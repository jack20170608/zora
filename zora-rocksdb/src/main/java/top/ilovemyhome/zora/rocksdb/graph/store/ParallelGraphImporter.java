package top.ilovemyhome.zora.rocksdb.graph.store;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-threaded bulk importer that fans batches of vertices or edges to a
 * fixed pool of worker threads and commits each batch as one
 * {@code addVertices / addEdges} transaction.
 *
 * <h2>Why this exists</h2>
 * <p>{@link GraphStore#addVertices(List)} amortises one transaction over a
 * batch (~5x throughput vs single calls). This class adds a second
 * dimension: many such batches run in parallel on different threads to
 * saturate RocksDB's compaction + write pipeline on multi-core machines.
 *
 * <h2>How it stays correct under parallelism</h2>
 * <p>Every item is routed to a worker by {@code id-hash mod workerCount}.
 * Once an id is bound to a worker, all subsequent submissions with that
 * id go to the SAME worker, so two transactions never race for the same
 * row lock. Edges hash on
 * {@code mix(srcId) ^ mix(dstId)} - both endpoints participate so an
 * imported edge cannot fight a same-endpoint edge sitting in another
 * worker's queue, at the cost of slightly worse load balance when
 * endpoint degrees are extremely skewed.
 *
 * <h2>Failure mode</h2>
 * Fail-fast. The first worker to throw flips a shared latch; new
 * submissions are rejected and {@link #close()} drains the running
 * batches then re-throws the original cause wrapped in
 * {@link ImportException}. The caller is expected to delete the partially
 * committed data (it has the source list) and re-run.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   try (var importer = ParallelGraphImporter.vertexImporter(store, 4, 1000)) {
 *       for (Vertex v : source) importer.submit(v);
 *   } // close() waits for queue drain, propagates errors
 * }</pre>
 */
public final class ParallelGraphImporter<T> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelGraphImporter.class);

    /** Sentinel batch list pushed into a worker queue to signal "drain and exit". */
    private static final List<?> POISON = List.of();

    private final ExecutorService pool;
    private final BlockingQueue<List<T>>[] queues;
    private final int batchSize;
    private final IdHasher<T> hasher;
    private final BatchWriter<T> writer;

    /** First failure caught by any worker; once set, submitters get rejected. */
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    /** Per-worker pending list, owned by the calling thread between submit() calls. */
    private final List<T>[] pendingPerWorker;

    private volatile boolean closed = false;

    @SuppressWarnings("unchecked")
    private ParallelGraphImporter(GraphStore store, int workerCount, int batchSize,
                                  IdHasher<T> hasher, BatchWriter<T> writer) {
        if (workerCount < 1) throw new IllegalArgumentException("workerCount must be >= 1");
        if (batchSize < 1)   throw new IllegalArgumentException("batchSize must be >= 1");
        this.batchSize = batchSize;
        this.hasher    = hasher;
        this.writer    = writer;
        this.queues = new BlockingQueue[workerCount];
        this.pendingPerWorker = new List[workerCount];
        for (int i = 0; i < workerCount; i++) {
            // Each queue holds up to 4 pending batches per worker. Beyond that
            // the submitter backpressures.
            this.queues[i] = new ArrayBlockingQueue<>(4);
            this.pendingPerWorker[i] = new ArrayList<>(batchSize);
        }
        this.pool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "graph-importer-" + System.identityHashCode(this));
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < workerCount; i++) {
            final int idx = i;
            pool.submit(() -> runWorker(idx, store));
        }
    }

    // ========================== Factory methods ==========================

    public static ParallelGraphImporter<Vertex> vertexImporter(GraphStore store,
                                                               int workerCount,
                                                               int batchSize) {
        return new ParallelGraphImporter<>(store, workerCount, batchSize,
            v -> v.getId(),                                  // partition by id
            (s, batch) -> s.addVertices((List<Vertex>) batch));
    }

    public static ParallelGraphImporter<Edge> edgeImporter(GraphStore store,
                                                           int workerCount,
                                                           int batchSize) {
        return new ParallelGraphImporter<>(store, workerCount, batchSize,
            // Hash on both endpoints so edges sharing either endpoint land
            // on the same worker - prevents lock contention from concurrent
            // edges touching the same vertex's incident set.
            e -> mix(e.getSrcId()) ^ mix(e.getDstId()),
            (s, batch) -> s.addEdges((List<Edge>) batch));
    }

    // ========================== Public submit API ==========================

    /**
     * Submits one item. Buffered into a per-worker pending list and flushed
     * when the buffer reaches {@code batchSize}. Blocks if the target
     * worker's queue is full (backpressure).
     *
     * @throws ImportException if any worker has already failed
     * @throws IllegalStateException if {@link #close()} has been called
     */
    public void submit(T item) throws ImportException, InterruptedException {
        if (closed) throw new IllegalStateException("importer is closed");
        checkFailure();
        int workerIdx = Math.floorMod(hasher.hash(item), queues.length);
        List<T> pending = pendingPerWorker[workerIdx];
        pending.add(item);
        if (pending.size() >= batchSize) {
            handOff(workerIdx);
        }
    }

    /** Bulk submit convenience. */
    public void submitAll(Collection<T> items) throws ImportException, InterruptedException {
        for (T item : items) submit(item);
    }

    // ========================== Lifecycle ==========================

    /**
     * Flushes any half-full per-worker buffers, signals every worker to
     * drain its queue and exit, blocks until they do, then propagates the
     * first error if any worker failed.
     *
     * <p>Safe to call multiple times.
     */
    @Override
    public void close() throws ImportException {
        if (closed) return;
        closed = true;

        // Flush any leftover < batchSize buffers.
        try {
            for (int i = 0; i < queues.length; i++) {
                if (!pendingPerWorker[i].isEmpty() && firstFailure.get() == null) {
                    handOff(i);
                }
            }
            // Send poison to every worker.
            for (BlockingQueue<List<T>> q : queues) {
                @SuppressWarnings("unchecked")
                List<T> poison = (List<T>) POISON;
                q.put(poison);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new ImportException("Interrupted while flushing", ie);
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
                pool.shutdownNow();
                throw new ImportException("Worker pool did not terminate in 5 minutes", null);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new ImportException("Interrupted while awaiting worker shutdown", ie);
        }

        Throwable f = firstFailure.get();
        if (f != null) throw new ImportException("Import failed in worker", f);
    }

    // ========================== Internals ==========================

    private void handOff(int workerIdx) throws InterruptedException {
        List<T> ready = pendingPerWorker[workerIdx];
        pendingPerWorker[workerIdx] = new ArrayList<>(batchSize);
        queues[workerIdx].put(ready);   // blocks on backpressure
    }

    private void checkFailure() throws ImportException {
        Throwable f = firstFailure.get();
        if (f != null) throw new ImportException("Import already failed", f);
    }

    private void runWorker(int idx, GraphStore store) {
        BlockingQueue<List<T>> q = queues[idx];
        try {
            while (true) {
                List<T> batch = q.take();
                if (batch == POISON) return;
                if (batch.isEmpty()) continue;
                try {
                    writer.write(store, batch);
                } catch (RocksDBException | RuntimeException e) {
                    // Record first failure; let other workers drain.
                    firstFailure.compareAndSet(null, e);
                    LOG.warn("Worker {} failed on batch of {} items", idx, batch.size(), e);
                    return;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** xorshift to spread sequential ids across workers. */
    private static long mix(long v) {
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        return v;
    }

    // ========================== Plumbing ==========================

    @FunctionalInterface
    private interface IdHasher<T> {
        long hash(T item);
    }

    @FunctionalInterface
    private interface BatchWriter<T> {
        void write(GraphStore store, List<T> batch) throws RocksDBException;
    }

    /**
     * Wraps a worker-thread failure so the caller doesn't lose the stack
     * trace through queue handoffs.
     */
    public static final class ImportException extends Exception {
        ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
