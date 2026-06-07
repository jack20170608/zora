package top.ilovemyhome.zora.rocksdb.graph.store;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides which {@code (typeId, propertyName)} pairs deserve a secondary
 * index entry.
 *
 * <p>The graph store consults this policy at every write path
 * ({@code addVertex}, {@code addEdge}, {@code updateVertexProperty}, ...)
 * and at every read path ({@code findVerticesByProperty},
 * {@code findEdgesByPropertyRange}, ...). A property that the policy
 * refuses is:
 * <ul>
 *   <li>not written to {@code cf_index} / {@code cf_edge_index};</li>
 *   <li>not removed either - so any historical entries from a previous
 *       policy are left intact;</li>
 *   <li>silently treated as "no matches" on the read side (since the
 *       index simply doesn't exist for it).</li>
 * </ul>
 *
 * <p>The default in {@link GraphStoreOptions} is {@link #none()} - nothing
 * is indexed unless the caller opts in. Callers explicitly list the
 * {@code (typeId, propName)} pairs they want queryable via
 * {@link #builder()}.
 */
public interface IndexPolicy {

    /** Returns true iff the given vertex property should be index-maintained. */
    boolean shouldIndexVertexProperty(int typeId, String propName);

    /** Returns true iff the given edge property should be index-maintained. */
    boolean shouldIndexEdgeProperty(int edgeType, String propName);

    // ========================== Built-ins ==========================

    /**
     * Indexes nothing. Default for stores that don't need property lookups
     * - keeps writes cheap and the on-disk footprint minimal.
     */
    static IndexPolicy none() {
        return NONE;
    }

    /**
     * Indexes every property of every entity. Equivalent to the legacy
     * behaviour before IndexPolicy existed. Convenient for tests; avoid
     * in production unless you actually query every property.
     */
    static IndexPolicy all() {
        return ALL;
    }

    /** Builds a per-(type, prop) allow-list. */
    static Builder builder() {
        return new Builder();
    }

    // ========================== Implementations ==========================

    IndexPolicy NONE = new IndexPolicy() {
        @Override public boolean shouldIndexVertexProperty(int typeId, String propName) { return false; }
        @Override public boolean shouldIndexEdgeProperty (int edgeType, String propName) { return false; }
        @Override public String toString() { return "IndexPolicy.none()"; }
    };

    IndexPolicy ALL = new IndexPolicy() {
        @Override public boolean shouldIndexVertexProperty(int typeId, String propName) { return true; }
        @Override public boolean shouldIndexEdgeProperty (int edgeType, String propName) { return true; }
        @Override public String toString() { return "IndexPolicy.all()"; }
    };

    /**
     * Per-{@code (typeId, propName)} allow-list builder. Vertex types and
     * edge types live in separate namespaces (typeIds may overlap) so they
     * have separate setters.
     *
     * <pre>{@code
     *   IndexPolicy p = IndexPolicy.builder()
     *       .indexVertexProperty(PERSON_TYPE, "name")
     *       .indexVertexProperty(PERSON_TYPE, "age")
     *       .indexEdgeProperty  (KNOWS_TYPE, "since")
     *       .build();
     * }</pre>
     */
    final class Builder {
        private final Map<Integer, Set<String>> vertex = new HashMap<>();
        private final Map<Integer, Set<String>> edge   = new HashMap<>();

        public Builder indexVertexProperty(int typeId, String propName) {
            vertex.computeIfAbsent(typeId, k -> new HashSet<>()).add(propName);
            return this;
        }

        public Builder indexVertexProperties(int typeId, Set<String> propNames) {
            vertex.computeIfAbsent(typeId, k -> new HashSet<>()).addAll(propNames);
            return this;
        }

        public Builder indexEdgeProperty(int edgeType, String propName) {
            edge.computeIfAbsent(edgeType, k -> new HashSet<>()).add(propName);
            return this;
        }

        public Builder indexEdgeProperties(int edgeType, Set<String> propNames) {
            edge.computeIfAbsent(edgeType, k -> new HashSet<>()).addAll(propNames);
            return this;
        }

        public IndexPolicy build() {
            // Snapshot to make the resulting policy immutable.
            Map<Integer, Set<String>> v = new HashMap<>();
            vertex.forEach((k, set) -> v.put(k, Set.copyOf(set)));
            Map<Integer, Set<String>> e = new HashMap<>();
            edge.forEach((k, set) -> e.put(k, Set.copyOf(set)));
            return new AllowListPolicy(Collections.unmodifiableMap(v),
                                       Collections.unmodifiableMap(e));
        }
    }

    /** Internal allow-list implementation; instantiate via {@link Builder}. */
    final class AllowListPolicy implements IndexPolicy {
        private final Map<Integer, Set<String>> vertexAllow;
        private final Map<Integer, Set<String>> edgeAllow;

        AllowListPolicy(Map<Integer, Set<String>> vertexAllow,
                        Map<Integer, Set<String>> edgeAllow) {
            this.vertexAllow = vertexAllow;
            this.edgeAllow   = edgeAllow;
        }

        @Override
        public boolean shouldIndexVertexProperty(int typeId, String propName) {
            Set<String> allowed = vertexAllow.get(typeId);
            return allowed != null && allowed.contains(propName);
        }

        @Override
        public boolean shouldIndexEdgeProperty(int edgeType, String propName) {
            Set<String> allowed = edgeAllow.get(edgeType);
            return allowed != null && allowed.contains(propName);
        }

        @Override
        public String toString() {
            return "IndexPolicy.allowList(vertex=" + vertexAllow + ", edge=" + edgeAllow + ")";
        }
    }
}
