package top.ilovemyhome.zora.rocksdb.graph.store;

/**
 * Tunable runtime options for {@link GraphStore}.
 *
 * <p>This is an immutable bag created via {@link Builder}; pass it to
 * {@link GraphStore#GraphStore(String, GraphStoreOptions)} to override the
 * defaults. Use {@link #defaults()} when defaults are fine.
 *
 * <h2>Default values</h2>
 * <ul>
 *   <li>{@code lockTimeoutMillis = 1000}  - matches RocksDB's built-in
 *       default for {@code TransactionDBOptions.setTransactionLockTimeout}.</li>
 *   <li>{@code deadlockDetect    = true}  - enables RocksDB's per-txn
 *       deadlock detector so cyclic waits abort one party with a Busy
 *       status instead of hanging until {@code lockTimeoutMillis} elapses.</li>
 *   <li>{@code syncWrites        = false} - writes are durable after a
 *       crash via RocksDB's WAL; {@code sync=true} additionally fsyncs on
 *       every commit (~order of magnitude slower).</li>
 *   <li>{@code indexPolicy       = IndexPolicy.none()} - no secondary
 *       indexing by default; callers must declare which
 *       {@code (typeId, propName)} pairs are queryable via
 *       {@link IndexPolicy#builder()}.</li>
 * </ul>
 */
public final class GraphStoreOptions {

    private static final GraphStoreOptions DEFAULTS = new Builder().build();

    private final long lockTimeoutMillis;
    private final boolean deadlockDetect;
    private final boolean syncWrites;
    private final IndexPolicy indexPolicy;

    private GraphStoreOptions(Builder b) {
        this.lockTimeoutMillis = b.lockTimeoutMillis;
        this.deadlockDetect    = b.deadlockDetect;
        this.syncWrites        = b.syncWrites;
        this.indexPolicy       = b.indexPolicy;
    }

    /** Convenience: a shared instance carrying the default settings. */
    public static GraphStoreOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long lockTimeoutMillis() { return lockTimeoutMillis; }
    public boolean deadlockDetect() { return deadlockDetect; }
    public boolean syncWrites()     { return syncWrites; }
    public IndexPolicy indexPolicy() { return indexPolicy; }

    public static final class Builder {
        private long lockTimeoutMillis = 1000;
        private boolean deadlockDetect = true;
        private boolean syncWrites     = false;
        private IndexPolicy indexPolicy = IndexPolicy.none();

        /**
         * Maximum time (in ms) a transaction will wait for a row lock before
         * RocksDB throws {@code Status.TimedOut}. Use {@code -1} for an
         * unbounded wait (combine with {@link #deadlockDetect(boolean)}!),
         * {@code 0} to fail-fast on contention.
         */
        public Builder lockTimeoutMillis(long ms) {
            this.lockTimeoutMillis = ms;
            return this;
        }

        /**
         * Turn the per-transaction deadlock detector on or off. With it on
         * (the default), a cycle of waiting transactions is broken by
         * aborting one with {@code Status.Busy}; with it off, the loser of
         * the cycle simply blocks until {@code lockTimeoutMillis} elapses.
         */
        public Builder deadlockDetect(boolean on) {
            this.deadlockDetect = on;
            return this;
        }

        /**
         * If {@code true}, every commit fsyncs the WAL before returning,
         * giving "durable on power loss" semantics at a large throughput
         * cost. The default ({@code false}) still writes to the WAL but
         * relies on the OS page cache being flushed within seconds.
         */
        public Builder syncWrites(boolean on) {
            this.syncWrites = on;
            return this;
        }

        /**
         * Sets which (typeId, propertyName) pairs the store should maintain
         * secondary indexes for. Default is {@link IndexPolicy#none()} -
         * nothing is indexed unless the caller explicitly opts in via
         * {@link IndexPolicy#builder()}. See {@link IndexPolicy} for the
         * implications on read / write paths.
         */
        public Builder indexPolicy(IndexPolicy policy) {
            this.indexPolicy = policy == null ? IndexPolicy.none() : policy;
            return this;
        }

        public GraphStoreOptions build() {
            return new GraphStoreOptions(this);
        }
    }
}
