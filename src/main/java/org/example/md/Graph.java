package org.example.md;

import java.util.*;

/**
 * Simple undirected graph representation optimized for the modular decomposition algorithm.
 *
 * <p>This class provides an adjacency list representation of an undirected graph
 * with O(1) edge lookup using HashSets. The graph is designed to be immutable
 * after construction (edges can only be added, not removed).</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Vertices are numbered 0 to n-1</li>
 *   <li>Self-loops are silently ignored</li>
 *   <li>Duplicate edges are handled correctly (stored once)</li>
 *   <li>O(1) edge existence check via {@link #hasEdge(int, int)}</li>
 *   <li>O(1) neighbor access via {@link #neighbors(int)}</li>
 * </ul>
 *
 * <h2>Thread Safety:</h2>
 * <p>This class is NOT thread-safe. External synchronization is required
 * if the graph is accessed from multiple threads.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Graph g = new Graph(4);
 * g.addEdge(0, 1);
 * g.addEdge(1, 2);
 * g.addEdge(2, 3);
 *
 * // Check adjacency
 * boolean connected = g.hasEdge(0, 1); // true
 * boolean notConnected = g.hasEdge(0, 3); // false
 *
 * // Get neighbors
 * Set<Integer> neighbors = g.neighbors(1); // {0, 2}
 * }</pre>
 *
 * @see ModularDecomposition
 */
public class Graph {

    /** Number of vertices in the graph */
    private final int n;

    /** Adjacency list representation using HashSets for O(1) lookup */
    private final List<Set<Integer>> adj;

    /**
     * Constructs an empty graph with the specified number of vertices.
     *
     * @param n the number of vertices (must be non-negative)
     * @throws IllegalArgumentException if n is negative
     */
    public Graph(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Number of vertices cannot be negative: " + n);
        }
        this.n = n;
        this.adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new HashSet<>());
        }
    }

    /**
     * Adds an undirected edge between vertices u and v.
     *
     * <p>Self-loops (u == v) are silently ignored. Adding a duplicate edge
     * has no effect.</p>
     *
     * @param u the first vertex (0 <= u < n)
     * @param v the second vertex (0 <= v < n)
     * @throws IllegalArgumentException if u or v is out of bounds
     */
    public void addEdge(int u, int v) {
        if (u < 0 || u >= n || v < 0 || v >= n) {
            throw new IllegalArgumentException(
                    String.format("Invalid vertex index: u=%d, v=%d (valid range: 0-%d)", u, v, n - 1));
        }
        if (u == v) {
            return; 
        }
        adj.get(u).add(v);
        adj.get(v).add(u);
    }

    /**
     * Checks if there is an edge between vertices u and v.
     *
     * <p>Since the graph is undirected, hasEdge(u, v) == hasEdge(v, u).</p>
     *
     * @param u the first vertex
     * @param v the second vertex
     * @return true if there is an edge between u and v, false otherwise
     *         (including if either vertex is out of bounds)
     */
    public boolean hasEdge(int u, int v) {
        if (u < 0 || u >= n || v < 0 || v >= n) {
            return false;
        }
        return adj.get(u).contains(v);
    }

    /**
     * Returns the set of neighbors of vertex v.
     *
     * <p>The returned set is unmodifiable. Attempting to modify it will
     * throw an {@link UnsupportedOperationException}.</p>
     *
     * @param v the vertex
     * @return an unmodifiable set of neighboring vertices, or an empty set
     *         if v is out of bounds
     */
    public Set<Integer> neighbors(int v) {
        if (v < 0 || v >= n) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(adj.get(v));
    }

    /**
     * Returns the number of vertices in the graph.
     *
     * @return the number of vertices
     */
    public int size() {
        return n;
    }

    /**
     * Returns the degree (number of neighbors) of vertex v.
     *
     * @param v the vertex
     * @return the degree of v, or 0 if v is out of bounds
     */
    public int degree(int v) {
        if (v < 0 || v >= n) {
            return 0;
        }
        return adj.get(v).size();
    }

    /**
     * Returns all edges in the graph as a list of pairs.
     *
     * <p>Each edge is represented as an int array {u, v} where u < v.
     * This ensures each undirected edge appears exactly once in the result.</p>
     *
     * @return a list of edges, each as {u, v} with u < v
     */
    public List<int[]> getEdges() {
        List<int[]> edges = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (int v : adj.get(u)) {
                if (u < v) { // Avoid duplicates
                    edges.add(new int[]{u, v});
                }
            }
        }
        return edges;
    }

    /**
     * Returns the total number of edges in the graph.
     *
     * @return the number of edges
     */
    public int edgeCount() {
        int count = 0;
        for (int u = 0; u < n; u++) {
            count += adj.get(u).size();
        }
        return count / 2; // Each edge counted twice
    }

    /**
     * Returns a string representation of the graph for debugging.
     *
     * @return a string showing the adjacency list
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph(n=").append(n).append(", edges=").append(edgeCount()).append(")\n");
        for (int u = 0; u < n; u++) {
            sb.append("  ").append(u).append(" -> ").append(adj.get(u)).append("\n");
        }
        return sb.toString();
    }
}
