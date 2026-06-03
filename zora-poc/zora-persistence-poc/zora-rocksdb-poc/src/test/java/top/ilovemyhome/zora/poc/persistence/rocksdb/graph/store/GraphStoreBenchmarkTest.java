package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for the GraphStore.
 * Measures throughput and latency of core operations under realistic workloads.
 *
 * <p>These are not strict JMH micro-benchmarks, but integration-level
 * performance tests that demonstrate the efficiency characteristics of
 * the RocksDB-based graph storage.
 */
class GraphStoreBenchmarkTest {

    private static final Logger LOG = LoggerFactory.getLogger(GraphStoreBenchmarkTest.class);

    private static final int PERSON_TYPE = 1;
    private static final int KNOWS_TYPE = 10;
    private static final int[] NEIGHBOR_COUNTS = {10, 50, 100, 500};
    private static final int WARMUP_VERTICES = 1000;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    // ========================== Vertex Write ==========================

    @Test
    void benchmarkVertexWriteThroughput() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());
        int count = 10_000;

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            Vertex v = new Vertex(store.nextVertexId(), PERSON_TYPE)
                .withProperty("name", "User" + i)
                .withProperty("age", i % 100);
            store.addVertex(v);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double throughput = count * 1000.0 / elapsedMs;
        LOG.info("Vertex Write: {} vertices in {} ms | Throughput: {:.1f} ops/sec",
            count, elapsedMs, throughput);

        assertThat(elapsedMs).isLessThan(30_000); // Should complete within 30s
        store.close();
    }

    // ========================== Edge Write ==========================

    @Test
    void benchmarkEdgeWriteThroughput() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        // Pre-create vertices
        long[] vertexIds = new long[WARMUP_VERTICES];
        for (int i = 0; i < WARMUP_VERTICES; i++) {
            vertexIds[i] = store.nextVertexId();
            store.addVertex(new Vertex(vertexIds[i], PERSON_TYPE));
        }

        int edgeCount = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < edgeCount; i++) {
            long src = vertexIds[i % WARMUP_VERTICES];
            long dst = vertexIds[(i + 1) % WARMUP_VERTICES];
            store.addEdge(new Edge(src, dst, KNOWS_TYPE));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double throughput = edgeCount * 1000.0 / elapsedMs;
        LOG.info("Edge Write: {} edges in {} ms | Throughput: {:.1f} ops/sec",
            edgeCount, elapsedMs, throughput);

        store.close();
    }

    // ========================== Neighbor Traversal ==========================

    @Test
    void benchmarkNeighborTraversalScaling() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        for (int neighborCount : NEIGHBOR_COUNTS) {
            // Setup: one hub vertex connected to N neighbors
            long hubId = store.nextVertexId();
            store.addVertex(new Vertex(hubId, PERSON_TYPE).withProperty("name", "Hub"));

            for (int i = 0; i < neighborCount; i++) {
                long neighborId = store.nextVertexId();
                store.addVertex(new Vertex(neighborId, PERSON_TYPE));
                store.addEdge(new Edge(hubId, neighborId, KNOWS_TYPE));
            }

            // Measure out-edge traversal
            long start = System.nanoTime();
            List<Edge> edges = store.getOutEdges(hubId, KNOWS_TYPE);
            long elapsedNs = System.nanoTime() - start;

            // Measure full neighbor resolution (edge + vertex lookup)
            start = System.nanoTime();
            List<Vertex> neighbors = store.getNeighbors(hubId, KNOWS_TYPE);
            long neighborElapsedNs = System.nanoTime() - start;

            LOG.info("Neighbor Traversal (N={}): outEdges={}μs, fullNeighbors={}μs, " +
                    "perEdge={:.2f}μs, perNeighbor={:.2f}μs",
                neighborCount,
                elapsedNs / 1000,
                neighborElapsedNs / 1000,
                (double) elapsedNs / neighborCount / 1000,
                (double) neighborElapsedNs / neighborCount / 1000);

            assertThat(edges).hasSize(neighborCount);
            assertThat(neighbors).hasSize(neighborCount);
        }

        store.close();
    }

    // ========================== In-Edge Traversal ==========================

    @Test
    void benchmarkInEdgeTraversal() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        // Setup: N vertices all point to one central vertex
        long centerId = store.nextVertexId();
        store.addVertex(new Vertex(centerId, PERSON_TYPE));

        int count = 1000;
        for (int i = 0; i < count; i++) {
            long fromId = store.nextVertexId();
            store.addVertex(new Vertex(fromId, PERSON_TYPE));
            store.addEdge(new Edge(fromId, centerId, KNOWS_TYPE));
        }

        long start = System.nanoTime();
        List<Edge> inEdges = store.getInEdges(centerId, KNOWS_TYPE);
        long elapsedNs = System.nanoTime() - start;

        LOG.info("In-Edge Traversal (N={}): {}μs total | {:.2f}μs per edge",
            count, elapsedNs / 1000, (double) elapsedNs / count / 1000);

        assertThat(inEdges).hasSize(count);
        store.close();
    }

    // ========================== Property Index Query ==========================

    @Test
    void benchmarkPropertyIndexQuery() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        // Create vertices with indexed properties
        int totalVertices = 5000;
        String targetName = "TargetUser";
        for (int i = 0; i < totalVertices; i++) {
            String name = (i == totalVertices / 2) ? targetName : "User" + i;
            Vertex v = new Vertex(store.nextVertexId(), PERSON_TYPE)
                .withProperty("name", name)
                .withProperty("age", i % 50);
            store.addVertex(v);
        }

        // Query by exact name match
        long start = System.nanoTime();
        List<Vertex> result = store.findVerticesByProperty(PERSON_TYPE, "name", targetName);
        long elapsedNs = System.nanoTime() - start;

        LOG.info("Property Index Query: scanned {} vertices, found {} matches in {}μs",
            totalVertices, result.size(), elapsedNs / 1000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperty("name")).isEqualTo(targetName);
        store.close();
    }

    // ========================== Mixed Workload ==========================

    @Test
    void benchmarkMixedWorkload() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        int vertexCount = 1000;
        int edgePerVertex = 10;

        // Phase 1: Write vertices
        long[] vertexIds = new long[vertexCount];
        long start = System.nanoTime();
        for (int i = 0; i < vertexCount; i++) {
            vertexIds[i] = store.nextVertexId();
            store.addVertex(new Vertex(vertexIds[i], PERSON_TYPE)
                .withProperty("name", "User" + i)
                .withProperty("age", i % 50));
        }
        long vertexWriteMs = (System.nanoTime() - start) / 1_000_000;

        // Phase 2: Write edges (each vertex connects to next 10)
        start = System.nanoTime();
        int edgeCount = 0;
        for (int i = 0; i < vertexCount; i++) {
            for (int j = 1; j <= edgePerVertex && i + j < vertexCount; j++) {
                store.addEdge(new Edge(vertexIds[i], vertexIds[i + j], KNOWS_TYPE));
                edgeCount++;
            }
        }
        long edgeWriteMs = (System.nanoTime() - start) / 1_000_000;

        // Phase 3: Random neighbor traversals
        start = System.nanoTime();
        int traversalCount = 100;
        int totalNeighbors = 0;
        for (int i = 0; i < traversalCount; i++) {
            long vid = vertexIds[i % vertexCount];
            List<Vertex> neighbors = store.getNeighbors(vid, KNOWS_TYPE);
            totalNeighbors += neighbors.size();
        }
        long traversalMs = (System.nanoTime() - start) / 1_000_000;

        // Phase 4: Property index queries
        start = System.nanoTime();
        for (int age = 0; age < 50; age++) {
            List<Vertex> matches = store.findVerticesByProperty(PERSON_TYPE, "age", age);
            assertThat(matches).hasSize(vertexCount / 50);
        }
        long indexQueryMs = (System.nanoTime() - start) / 1_000_000;

        LOG.info("Mixed Workload Summary:");
        LOG.info("  Vertices: {} written in {} ms ({:.1f} v/s)",
            vertexCount, vertexWriteMs, vertexCount * 1000.0 / vertexWriteMs);
        LOG.info("  Edges: {} written in {} ms ({:.1f} e/s)",
            edgeCount, edgeWriteMs, edgeCount * 1000.0 / edgeWriteMs);
        LOG.info("  Traversals: {} queries, {} total neighbors in {} ms ({:.1f} q/s)",
            traversalCount, totalNeighbors, traversalMs, traversalCount * 1000.0 / traversalMs);
        LOG.info("  Index Queries: 50 age-based queries in {} ms ({:.1f} q/s)",
            indexQueryMs, 50 * 1000.0 / indexQueryMs);

        store.close();
    }

    // ========================== Vertex Deletion ==========================

    @Test
    void benchmarkVertexDeletionWithEdges() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        long vertexId = store.nextVertexId();
        store.addVertex(new Vertex(vertexId, PERSON_TYPE));

        // Connect to N neighbors
        int neighborCount = 500;
        for (int i = 0; i < neighborCount; i++) {
            long neighborId = store.nextVertexId();
            store.addVertex(new Vertex(neighborId, PERSON_TYPE));
            store.addEdge(new Edge(vertexId, neighborId, KNOWS_TYPE));
            store.addEdge(new Edge(neighborId, vertexId, KNOWS_TYPE)); // bidirectional
        }

        long start = System.nanoTime();
        store.removeVertex(PERSON_TYPE, vertexId);
        long elapsedNs = System.nanoTime() - start;

        LOG.info("Vertex Deletion: removed 1 vertex + {} edges in {}μs ({:.2f}μs per edge)",
            neighborCount * 2, elapsedNs / 1000, (double) elapsedNs / (neighborCount * 2) / 1000);

        assertThat(store.getVertex(PERSON_TYPE, vertexId)).isNull();
        store.close();
    }
}
