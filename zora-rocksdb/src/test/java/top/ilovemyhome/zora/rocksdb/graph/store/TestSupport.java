package top.ilovemyhome.zora.rocksdb.graph.store;

import org.rocksdb.RocksDBException;

/**
 * Shared test helpers. Production code's default IndexPolicy is
 * {@link IndexPolicy#none()}, but most tests want to verify index-backed
 * behaviour, so the test factory opens stores with {@link IndexPolicy#all()}.
 */
final class TestSupport {
    private TestSupport() {}

    /** Opens a store with {@link IndexPolicy#all()} - tests want every property queryable. */
    static GraphStore openTestStore(String path) throws RocksDBException {
        return new GraphStore(path,
            GraphStoreOptions.builder().indexPolicy(IndexPolicy.all()).build());
    }
}
