package org.example.md;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

/**
 * Neo4j procedures for computing modular decomposition of graphs.
 *
 * <p>This implementation uses a pure Java version of the Corneil-Habib-Paul-Tedder
 * algorithm, ensuring all computation happens within Neo4j without external dependencies.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * CALL md.compute('NodeLabel', 'RELATIONSHIP_TYPE')
 * YIELD treeJson, nodeCount, edgeCount, nodeMapping
 * </pre>
 *
 * <h2>Configuration:</h2>
 * <ul>
 *   <li>maxNodes - Maximum number of nodes to process (default: 100,000)</li>
 *   <li>timeoutMs - Timeout in milliseconds (default: 300,000 = 5 minutes)</li>
 * </ul>
 *
 * @see ModularDecomposition
 */
public class ModularDecompositionProcedures {

    private static final Logger log = LoggerFactory.getLogger(ModularDecompositionProcedures.class);

    /** Default maximum number of nodes to process */
    public static final int DEFAULT_MAX_NODES = 100_000;

    /** Default timeout in milliseconds (5 minutes) */
    public static final long DEFAULT_TIMEOUT_MS = 300_000;

    /** Jackson ObjectMapper for JSON serialization */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Context
    public Transaction tx;

    /**
     * Result class for the modular decomposition procedure.
     *
     * <p>Contains the decomposition tree as JSON, node/edge counts, and a mapping
     * from internal indices back to original Neo4j node IDs.</p>
     */
    public static class MdResult {
        /** JSON representation of the complete result including the MD tree */
        public String treeJson;

        /** Number of nodes in the processed subgraph */
        public long nodeCount;

        /** Number of edges in the processed subgraph */
        public long edgeCount;

        /** JSON mapping from internal vertex indices to Neo4j element IDs */
        public String nodeMapping;

        /** Execution time in milliseconds */
        public long executionTimeMs;

        public MdResult(String treeJson, long nodeCount, long edgeCount,
                       String nodeMapping, long executionTimeMs) {
            this.treeJson = treeJson;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.nodeMapping = nodeMapping;
            this.executionTimeMs = executionTimeMs;
        }
    }

    /**
     * Compute modular decomposition of a subgraph with default settings.
     *
     * @param label The node label to filter nodes (required, non-empty)
     * @param relType The relationship type to consider (required, non-empty)
     * @return Stream of MdResult containing the decomposition tree as JSON
     * @throws IllegalArgumentException if label or relType is null or empty
     * @throws RuntimeException if graph exceeds size limit or computation times out
     *
     * @see #computeWithConfig(String, String, Long, Long)
     */
    @Procedure(name = "md.compute", mode = Mode.READ)
    @Description("CALL md.compute(label, relType) YIELD treeJson, nodeCount, edgeCount, nodeMapping, executionTimeMs - " +
                 "Computes the modular decomposition tree of the specified subgraph")
    public Stream<MdResult> compute(
            @Name("label") String label,
            @Name("relType") String relType
    ) {
        return computeWithConfig(label, relType, null, null);
    }

    /**
     * Compute modular decomposition with custom configuration.
     *
     * @param label The node label to filter nodes (required, non-empty)
     * @param relType The relationship type to consider (required, non-empty)
     * @param maxNodes Maximum number of nodes to process (null for default)
     * @param timeoutMs Timeout in milliseconds (null for default)
     * @return Stream of MdResult containing the decomposition tree as JSON
     */
    @Procedure(name = "md.computeWithConfig", mode = Mode.READ)
    @Description("CALL md.computeWithConfig(label, relType, maxNodes, timeoutMs) - " +
                 "Computes modular decomposition with custom size limit and timeout")
    public Stream<MdResult> computeWithConfig(
            @Name("label") String label,
            @Name("relType") String relType,
            @Name(value = "maxNodes", defaultValue = "null") Long maxNodes,
            @Name(value = "timeoutMs", defaultValue = "null") Long timeoutMs
    ) {
        long startTime = System.currentTimeMillis();

        // Input validation
        validateInput(label, "label");
        validateInput(relType, "relType");

        int effectiveMaxNodes = maxNodes != null ? maxNodes.intValue() : DEFAULT_MAX_NODES;
        long effectiveTimeoutMs = timeoutMs != null ? timeoutMs : DEFAULT_TIMEOUT_MS;

        log.info("Starting md.compute for label='{}', relType='{}', maxNodes={}, timeoutMs={}",
                label, relType, effectiveMaxNodes, effectiveTimeoutMs);

        try {
            // Extract the subgraph from Neo4j
            GraphExtract extract = extractUndirectedSubgraph(label, relType, effectiveMaxNodes);

            log.debug("Extracted {} nodes and {} edges", extract.n, extract.edges.size());

            // Check timeout before computation
            checkTimeout(startTime, effectiveTimeoutMs, "graph extraction");

            // Build the graph data structure
            Graph graph = new Graph(extract.n);
            for (int[] edge : extract.edges) {
                graph.addEdge(edge[0], edge[1]);
            }

            // Check timeout before main computation
            checkTimeout(startTime, effectiveTimeoutMs, "graph building");

            // Compute modular decomposition
            MDNode tree = ModularDecomposition.compute(graph);

            // Check timeout after computation
            checkTimeout(startTime, effectiveTimeoutMs, "decomposition computation");

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Completed md.compute in {}ms for {} nodes, {} edges",
                    executionTime, extract.n, extract.edges.size());

            // Build result JSON
            String treeJson = buildResultJson(tree, extract.n, extract.edges.size());
            String mappingJson = buildMappingJson(extract.idMapping);

            return Stream.of(new MdResult(
                    treeJson,
                    extract.n,
                    extract.edges.size(),
                    mappingJson,
                    executionTime
            ));

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error in md.compute: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error in md.compute: {}", e.getMessage(), e);
            throw new RuntimeException("md.compute failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate that a required string parameter is not null or empty.
     */
    private void validateInput(String value, String paramName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Parameter '%s' is required and cannot be null or empty", paramName));
        }
    }

    /**
     * Check if the operation has exceeded the timeout.
     */
    private void checkTimeout(long startTime, long timeoutMs, String phase) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > timeoutMs) {
            throw new IllegalStateException(String.format(
                    "Operation timed out after %dms during %s (limit: %dms)",
                    elapsed, phase, timeoutMs));
        }
    }

    /**
     * Internal class to hold extracted graph data with ID mapping.
     */
    private static class GraphExtract {
        /** Number of vertices */
        final int n;

        /** List of edges, each as {u, v} where u < v */
        final List<int[]> edges;

        /** Mapping from internal index to Neo4j element ID */
        final Map<Integer, String> idMapping;

        GraphExtract(int n, List<int[]> edges, Map<Integer, String> idMapping) {
            this.n = n;
            this.edges = edges;
            this.idMapping = idMapping;
        }
    }

    /**
     * Extract an undirected subgraph from Neo4j based on label and relationship type.
     *
     * @param label Node label to filter
     * @param relType Relationship type to consider
     * @param maxNodes Maximum number of nodes allowed
     * @return GraphExtract containing the graph structure and ID mappings
     * @throws IllegalStateException if node count exceeds maxNodes
     */
    private GraphExtract extractUndirectedSubgraph(String label, String relType, int maxNodes) {
        Label L = Label.label(label);
        RelationshipType R = RelationshipType.withName(relType);

        // Collect nodes with that label
        List<Node> nodes = new ArrayList<>();
        try (ResourceIterator<Node> it = tx.findNodes(L)) {
            while (it.hasNext()) {
                if (nodes.size() >= maxNodes) {
                    throw new IllegalStateException(String.format(
                            "Graph exceeds maximum node limit of %d. " +
                            "Use md.computeWithConfig() with a higher maxNodes parameter or filter your data.",
                            maxNodes));
                }
                nodes.add(it.next());
            }
        }

        // Map Neo4j element ID -> index 0..n-1
        // Also build reverse mapping for result
        Map<String, Integer> elementIdToIdx = new HashMap<>(nodes.size() * 2);
        Map<Integer, String> idxToElementId = new HashMap<>(nodes.size() * 2);

        for (int i = 0; i < nodes.size(); i++) {
            String elementId = nodes.get(i).getElementId();
            elementIdToIdx.put(elementId, i);
            idxToElementId.put(i, elementId);
        }

        // Collect undirected edges among selected nodes
        // Use a long key (minIdx, maxIdx) to deduplicate
        Set<Long> edgeKeys = new HashSet<>();
        for (Node u : nodes) {
            int ui = elementIdToIdx.get(u.getElementId());
            for (Relationship rel : u.getRelationships(Direction.BOTH, R)) {
                Node v = rel.getOtherNode(u);
                Integer viObj = elementIdToIdx.get(v.getElementId());
                if (viObj == null) continue; // Node doesn't have the required label

                int vi = viObj;
                if (ui == vi) continue; // Ignore self-loops

                int a = Math.min(ui, vi);
                int b = Math.max(ui, vi);
                long key = (((long) a) << 32) | (b & 0xffffffffL);
                edgeKeys.add(key);
            }
        }

        List<int[]> edges = new ArrayList<>(edgeKeys.size());
        for (long key : edgeKeys) {
            int a = (int) (key >>> 32);
            int b = (int) (key & 0xffffffffL);
            edges.add(new int[]{a, b});
        }

        return new GraphExtract(nodes.size(), edges, idxToElementId);
    }

    /**
     * Build the result JSON using Jackson.
     */
    private String buildResultJson(MDNode tree, int nodeCount, int edgeCount) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("nodeCount", nodeCount);
            result.put("edgeCount", edgeCount);

            if (tree != null) {
                result.set("mdTree", buildTreeJson(tree));
            } else {
                result.putNull("mdTree");
            }

            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize result to JSON", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Recursively build JSON representation of the MD tree.
     */
    private ObjectNode buildTreeJson(MDNode node) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("type", node.getType().name());

        if (node.isLeaf() && node.getVertex() != null) {
            json.put("vertex", node.getVertex());
        } else if (!node.getChildren().isEmpty()) {
            ArrayNode children = objectMapper.createArrayNode();
            for (MDNode child : node.getChildren()) {
                children.add(buildTreeJson(child));
            }
            json.set("children", children);
        }

        return json;
    }

    /**
     * Build JSON mapping from internal indices to Neo4j element IDs.
     */
    private String buildMappingJson(Map<Integer, String> idMapping) {
        try {
            ObjectNode mapping = objectMapper.createObjectNode();
            for (Map.Entry<Integer, String> entry : idMapping.entrySet()) {
                mapping.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return objectMapper.writeValueAsString(mapping);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize mapping to JSON", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Get statistics about the procedure configuration.
     *
     * @return Stream containing configuration information
     */
    @Procedure(name = "md.info", mode = Mode.READ)
    @Description("Returns information about the md procedures configuration")
    public Stream<InfoResult> info() {
        return Stream.of(new InfoResult(
                "0.2.0",
                DEFAULT_MAX_NODES,
                DEFAULT_TIMEOUT_MS
        ));
    }

    /**
     * Result class for md.info procedure.
     */
    public static class InfoResult {
        public String version;
        public long defaultMaxNodes;
        public long defaultTimeoutMs;

        public InfoResult(String version, long defaultMaxNodes, long defaultTimeoutMs) {
            this.version = version;
            this.defaultMaxNodes = defaultMaxNodes;
            this.defaultTimeoutMs = defaultTimeoutMs;
        }
    }
}
