package org.example.md;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comprehensive tests for the modular decomposition algorithm.
 * Tests verify both correctness and structural properties of the decomposition tree.
 */
class ModularDecompositionTest {

    // ==================== Basic Graph Tests ====================

    @Test
    @DisplayName("Empty graph should return null")
    void testEmptyGraph() {
        Graph g = new Graph(0);
        MDNode tree = ModularDecomposition.compute(g);
        assertNull(tree, "Tree should be null for empty graph");
    }

    @Test
    @DisplayName("Single vertex should return NORMAL node")
    void testSingleVertex() {
        Graph g = new Graph(1);
        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.NORMAL, tree.getType(), "Root should be NORMAL");
        assertEquals(Integer.valueOf(0), tree.getVertex(), "Vertex should be 0");
        assertTrue(tree.isLeaf(), "Single vertex should be a leaf");
        assertTrue(tree.getChildren().isEmpty(), "Leaf should have no children");
    }

    @Test
    @DisplayName("Two connected vertices should return SERIES node")
    void testTwoConnectedVertices() {
        Graph g = new Graph(2);
        g.addEdge(0, 1);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.SERIES, tree.getType(), "Root should be SERIES for connected pair");
        assertEquals(2, tree.getChildren().size(), "Should have 2 children");

        // Verify children are leaves
        for (MDNode child : tree.getChildren()) {
            assertEquals(NodeType.NORMAL, child.getType(), "Children should be NORMAL");
            assertTrue(child.isLeaf(), "Children should be leaves");
        }

        // Verify all vertices are present
        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1), vertices, "Should contain vertices 0 and 1");
    }

    @Test
    @DisplayName("Two disconnected vertices should return PARALLEL node")
    void testTwoDisconnectedVertices() {
        Graph g = new Graph(2);
        // No edges

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.PARALLEL, tree.getType(), "Root should be PARALLEL for disconnected pair");
        assertEquals(2, tree.getChildren().size(), "Should have 2 children");
    }

    // ==================== Complete Graph Tests ====================

    @Test
    @DisplayName("Complete graph K3 should return SERIES with 3 children")
    void testCompleteGraphK3() {
        Graph g = new Graph(3);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(0, 2);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.SERIES, tree.getType(), "Root should be SERIES for complete graph");
        assertEquals(3, tree.getChildren().size(), "K3 should have 3 children");

        // All children should be leaves
        for (MDNode child : tree.getChildren()) {
            assertEquals(NodeType.NORMAL, child.getType(), "All children should be NORMAL nodes");
        }

        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1, 2), vertices, "Should contain all vertices");
    }

    @Test
    @DisplayName("Complete graph K4 should return SERIES with 4 children")
    void testCompleteGraphK4() {
        Graph g = new Graph(4);
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                g.addEdge(i, j);
            }
        }

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.SERIES, tree.getType(), "Root should be SERIES");
        assertEquals(4, tree.getChildren().size(), "K4 should have 4 children");
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20})
    @DisplayName("Complete graph Kn should return SERIES with n children")
    void testCompleteGraphKn(int n) {
        Graph g = new Graph(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(i, j);
            }
        }

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.SERIES, tree.getType(), "Root should be SERIES");
        assertEquals(n, tree.getChildren().size(), "Kn should have n children");
    }

    // ==================== Independent Set Tests ====================

    @Test
    @DisplayName("Independent set of 3 vertices should return PARALLEL with 3 children")
    void testIndependentSet3() {
        Graph g = new Graph(3);
        // No edges

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.PARALLEL, tree.getType(), "Root should be PARALLEL");
        assertEquals(3, tree.getChildren().size(), "Should have 3 children");

        for (MDNode child : tree.getChildren()) {
            assertEquals(NodeType.NORMAL, child.getType(), "All children should be NORMAL");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20})
    @DisplayName("Independent set of n vertices should return PARALLEL with n children")
    void testIndependentSetN(int n) {
        Graph g = new Graph(n);
        // No edges

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.PARALLEL, tree.getType(), "Root should be PARALLEL");
        assertEquals(n, tree.getChildren().size(), "Should have n children");
    }

    // ==================== Path Graph Tests ====================

    @Test
    @DisplayName("Path P3 (0-1-2) should have correct structure")
    void testPathP3() {
        Graph g = new Graph(3);
        g.addEdge(0, 1);
        g.addEdge(1, 2);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1, 2), vertices, "Should contain all vertices");

        // P3 is not prime - verify structure
        int leafCount = countLeaves(tree);
        assertEquals(3, leafCount, "Should have 3 leaves");
    }

    @Test
    @DisplayName("Path P4 (0-1-2-3) should contain PRIME node")
    void testPathP4ContainsPrime() {
        Graph g = new Graph(4);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");

        // P4 is the smallest prime graph - verify it contains a PRIME node
        boolean hasPrime = containsNodeType(tree, NodeType.PRIME);
        assertTrue(hasPrime, "P4 decomposition must contain a PRIME node");

        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1, 2, 3), vertices, "Should contain all vertices");
    }

    @Test
    @DisplayName("Path P5 should contain PRIME node")
    void testPathP5ContainsPrime() {
        Graph g = new Graph(5);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 4);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        boolean hasPrime = containsNodeType(tree, NodeType.PRIME);
        assertTrue(hasPrime, "P5 decomposition must contain a PRIME node");
    }

    // ==================== Star Graph Tests ====================

    @Test
    @DisplayName("Star graph with center and 3 leaves")
    void testStarGraph3Leaves() {
        Graph g = new Graph(4);
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(0, 3);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1, 2, 3), vertices, "Should contain all vertices");
        assertEquals(4, countLeaves(tree), "Should have 4 leaves");
    }

    @Test
    @DisplayName("Star graph with center and 5 leaves")
    void testStarGraph5Leaves() {
        Graph g = new Graph(6);
        for (int i = 1; i <= 5; i++) {
            g.addEdge(0, i);
        }

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(6, countLeaves(tree), "Should have 6 leaves");

        // Star graphs have a specific structure - leaves form a PARALLEL module
        boolean hasParallel = containsNodeType(tree, NodeType.PARALLEL);
        assertTrue(hasParallel, "Star graph should contain PARALLEL node for leaves");
    }

    // ==================== Cycle Graph Tests ====================

    @Test
    @DisplayName("Cycle C4 should have correct decomposition")
    void testCycleC4() {
        Graph g = new Graph(4);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 0);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1, 2, 3), vertices, "Should contain all vertices");
    }

    @Test
    @DisplayName("Cycle C5 should contain PRIME node")
    void testCycleC5ContainsPrime() {
        Graph g = new Graph(5);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(4, 0);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        // C5 is prime
        boolean hasPrime = containsNodeType(tree, NodeType.PRIME);
        assertTrue(hasPrime, "C5 decomposition must contain a PRIME node");
    }

    // ==================== Composite Graph Tests ====================

    @Test
    @DisplayName("Two disjoint triangles should form PARALLEL of two SERIES")
    void testTwoDisjointTriangles() {
        Graph g = new Graph(6);
        // First triangle: 0-1-2
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(0, 2);
        // Second triangle: 3-4-5
        g.addEdge(3, 4);
        g.addEdge(4, 5);
        g.addEdge(3, 5);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.PARALLEL, tree.getType(), "Root should be PARALLEL");
        assertEquals(2, tree.getChildren().size(), "Should have 2 children (two triangles)");

        for (MDNode child : tree.getChildren()) {
            assertEquals(NodeType.SERIES, child.getType(), "Each triangle should be SERIES");
            assertEquals(3, child.getChildren().size(), "Each triangle should have 3 vertices");
        }
    }

    @Test
    @DisplayName("Two triangles connected by single edge")
    void testTwoTrianglesConnected() {
        Graph g = new Graph(6);
        // First triangle: 0-1-2
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(0, 2);
        // Second triangle: 3-4-5
        g.addEdge(3, 4);
        g.addEdge(4, 5);
        g.addEdge(3, 5);
        // Bridge between triangles
        g.addEdge(2, 3);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        Set<Integer> vertices = collectLeafVertices(tree);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), vertices, "Should contain all vertices");
    }

    @Test
    @DisplayName("Complete bipartite graph K2,2 should have correct structure")
    void testCompleteBipartiteK22() {
        Graph g = new Graph(4);
        // K2,2: {0,1} connected to {2,3}
        g.addEdge(0, 2);
        g.addEdge(0, 3);
        g.addEdge(1, 2);
        g.addEdge(1, 3);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        // K2,2 has twin modules - both sides are modules
        boolean hasParallel = containsNodeType(tree, NodeType.PARALLEL);
        assertTrue(hasParallel, "K2,2 should have PARALLEL nodes for the two sides");
    }

    @Test
    @DisplayName("Complete bipartite graph K3,3")
    void testCompleteBipartiteK33() {
        Graph g = new Graph(6);
        // K3,3: {0,1,2} connected to {3,4,5}
        for (int i = 0; i < 3; i++) {
            for (int j = 3; j < 6; j++) {
                g.addEdge(i, j);
            }
        }

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(6, countLeaves(tree), "Should have 6 leaves");
    }

    // ==================== Petersen Graph Test ====================

    @Test
    @DisplayName("Petersen graph should be prime")
    void testPetersenGraph() {
        Graph g = new Graph(10);
        // Outer cycle
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(4, 0);
        // Inner star
        g.addEdge(5, 7);
        g.addEdge(7, 9);
        g.addEdge(9, 6);
        g.addEdge(6, 8);
        g.addEdge(8, 5);
        // Spokes
        g.addEdge(0, 5);
        g.addEdge(1, 6);
        g.addEdge(2, 7);
        g.addEdge(3, 8);
        g.addEdge(4, 9);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        // Petersen graph is prime
        assertEquals(NodeType.PRIME, tree.getType(), "Petersen graph root should be PRIME");
        assertEquals(10, tree.getChildren().size(), "PRIME node should have all 10 vertices as children");
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Self-loop should be ignored")
    void testSelfLoopIgnored() {
        Graph g = new Graph(2);
        g.addEdge(0, 0); // Self-loop - should be ignored
        g.addEdge(0, 1);

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.SERIES, tree.getType(), "Should be SERIES (self-loop ignored)");
    }

    @Test
    @DisplayName("Duplicate edges should not affect result")
    void testDuplicateEdges() {
        Graph g = new Graph(2);
        g.addEdge(0, 1);
        g.addEdge(0, 1); // Duplicate
        g.addEdge(1, 0); // Reverse duplicate

        MDNode tree = ModularDecomposition.compute(g);

        assertNotNull(tree, "Tree should not be null");
        assertEquals(NodeType.SERIES, tree.getType(), "Should be SERIES");
        assertEquals(2, tree.getChildren().size(), "Should have 2 children");
    }

    // ==================== Property-Based Tests ====================

    @Test
    @DisplayName("Decomposition should preserve all vertices")
    void testVertexPreservation() {
        for (int n = 1; n <= 10; n++) {
            Graph g = new Graph(n);
            // Add some random edges
            for (int i = 0; i < n - 1; i++) {
                g.addEdge(i, i + 1);
            }

            MDNode tree = ModularDecomposition.compute(g);
            assertNotNull(tree, "Tree should not be null for n=" + n);

            Set<Integer> vertices = collectLeafVertices(tree);
            assertEquals(n, vertices.size(), "Should preserve all " + n + " vertices");

            for (int i = 0; i < n; i++) {
                assertTrue(vertices.contains(i), "Should contain vertex " + i);
            }
        }
    }

    @Test
    @DisplayName("Leaf count should equal vertex count")
    void testLeafCountEqualsVertexCount() {
        int[] sizes = {1, 2, 3, 5, 10};
        for (int n : sizes) {
            Graph g = new Graph(n);
            // Create a path
            for (int i = 0; i < n - 1; i++) {
                g.addEdge(i, i + 1);
            }

            MDNode tree = ModularDecomposition.compute(g);
            assertNotNull(tree, "Tree should not be null");
            assertEquals(n, countLeaves(tree), "Leaf count should equal vertex count for n=" + n);
        }
    }

    // ==================== Graph Data Structure Tests ====================

    @Test
    @DisplayName("Graph should correctly report neighbors")
    void testGraphNeighbors() {
        Graph g = new Graph(3);
        g.addEdge(0, 1);
        g.addEdge(0, 2);

        assertEquals(Set.of(1, 2), g.neighbors(0), "Vertex 0 should have neighbors 1 and 2");
        assertEquals(Set.of(0), g.neighbors(1), "Vertex 1 should have neighbor 0");
        assertEquals(Set.of(0), g.neighbors(2), "Vertex 2 should have neighbor 0");
    }

    @Test
    @DisplayName("Graph should correctly report edges")
    void testGraphEdges() {
        Graph g = new Graph(3);
        g.addEdge(0, 1);
        g.addEdge(1, 2);

        List<int[]> edges = g.getEdges();
        assertEquals(2, edges.size(), "Should have 2 edges");
    }

    @Test
    @DisplayName("Graph hasEdge should work correctly")
    void testGraphHasEdge() {
        Graph g = new Graph(3);
        g.addEdge(0, 1);

        assertTrue(g.hasEdge(0, 1), "Should have edge 0-1");
        assertTrue(g.hasEdge(1, 0), "Should have edge 1-0 (undirected)");
        assertFalse(g.hasEdge(0, 2), "Should not have edge 0-2");
        assertFalse(g.hasEdge(1, 2), "Should not have edge 1-2");
    }

    @Test
    @DisplayName("Graph should handle invalid vertex indices")
    void testGraphInvalidIndices() {
        Graph g = new Graph(3);

        assertThrows(IllegalArgumentException.class, () -> g.addEdge(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 5));
        assertFalse(g.hasEdge(-1, 0), "hasEdge should return false for invalid index");
        assertTrue(g.neighbors(-1).isEmpty(), "neighbors should return empty for invalid index");
    }

    // ==================== LexBFS Tests ====================

    @Test
    @DisplayName("LexBFS should produce valid ordering")
    void testLexBFSOrdering() {
        Graph g = new Graph(4);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);

        LexBFS.Result result = LexBFS.compute(g);

        assertNotNull(result, "Result should not be null");
        assertEquals(4, result.sigma.length, "Sigma should have 4 elements");
        assertEquals(4, result.sigmaInv.length, "SigmaInv should have 4 elements");

        // Verify sigma and sigmaInv are inverses
        for (int i = 0; i < 4; i++) {
            assertEquals(i, result.sigmaInv[result.sigma[i]],
                "sigmaInv[sigma[i]] should equal i");
        }

        // Verify all vertices appear exactly once
        Set<Integer> seen = new HashSet<>();
        for (int v : result.sigma) {
            assertTrue(v >= 0 && v < 4, "Vertex should be in valid range");
            assertFalse(seen.contains(v), "Each vertex should appear once");
            seen.add(v);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Collect all vertex labels from leaf nodes in the tree
     */
    private Set<Integer> collectLeafVertices(MDNode node) {
        Set<Integer> vertices = new HashSet<>();
        collectLeafVerticesHelper(node, vertices);
        return vertices;
    }

    private void collectLeafVerticesHelper(MDNode node, Set<Integer> vertices) {
        if (node.isLeaf()) {
            if (node.getVertex() != null) {
                vertices.add(node.getVertex());
            }
        } else {
            for (MDNode child : node.getChildren()) {
                collectLeafVerticesHelper(child, vertices);
            }
        }
    }

    /**
     * Count the number of leaf nodes in the tree
     */
    private int countLeaves(MDNode node) {
        if (node.isLeaf()) {
            return 1;
        }
        int count = 0;
        for (MDNode child : node.getChildren()) {
            count += countLeaves(child);
        }
        return count;
    }

    /**
     * Check if the tree contains a node of the given type
     */
    private boolean containsNodeType(MDNode node, NodeType type) {
        if (node.getType() == type) {
            return true;
        }
        for (MDNode child : node.getChildren()) {
            if (containsNodeType(child, type)) {
                return true;
            }
        }
        return false;
    }
}
