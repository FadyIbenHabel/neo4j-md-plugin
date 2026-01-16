package org.example.md;

/**
 * Enum representing the different types of nodes in a modular decomposition tree.
 *
 * <p>In modular decomposition, a graph is recursively decomposed into modules.
 * Each node in the resulting tree has one of four types that describe the
 * relationship between its children:</p>
 *
 * <h2>Node Type Semantics:</h2>
 * <ul>
 *   <li><b>SERIES</b> - Represents a "join" operation. All children are pairwise adjacent.
 *       The quotient graph (treating each child as a vertex) is a complete graph.</li>
 *   <li><b>PARALLEL</b> - Represents a "union" operation. No children are adjacent to each other.
 *       The quotient graph is an empty graph (no edges).</li>
 *   <li><b>PRIME</b> - The quotient graph is neither complete nor empty. This represents
 *       an indivisible structure that cannot be expressed as series or parallel composition.</li>
 *   <li><b>NORMAL</b> - A leaf node representing a single vertex of the original graph.</li>
 * </ul>
 *
 * <h2>Graph Reconstruction:</h2>
 * <p>The original graph can be reconstructed from the MD tree by:</p>
 * <ol>
 *   <li>For SERIES nodes: connect all pairs of vertices in different child modules</li>
 *   <li>For PARALLEL nodes: do not add any edges between child modules</li>
 *   <li>For PRIME nodes: use the quotient graph structure to determine connections</li>
 * </ol>
 *
 * <h2>Examples:</h2>
 * <ul>
 *   <li>Complete graph K_n: Single SERIES node with n NORMAL children</li>
 *   <li>Independent set: Single PARALLEL node with n NORMAL children</li>
 *   <li>Path P_4: Contains a PRIME node (P_4 is the smallest prime graph)</li>
 *   <li>Cycle C_5: Contains a PRIME node</li>
 * </ul>
 *
 * @see MDNode
 * @see ModularDecomposition
 */
public enum NodeType {
    /**
     * A prime node - cannot be further decomposed into series or parallel composition.
     *
     * <p>The quotient graph of a PRIME node is neither complete nor empty.
     * PRIME nodes represent the "irreducible" parts of the graph structure.
     * Examples of prime graphs include P_4 (path of 4 vertices) and C_5 (cycle of 5).</p>
     */
    PRIME,

    /**
     * A series node - all children are pairwise adjacent.
     *
     * <p>In a SERIES node, every vertex in one child module is adjacent to every
     * vertex in every other child module. The complete graph K_n is represented
     * by a single SERIES node with n NORMAL children.</p>
     *
     * <p>Mathematically, if modules M_1, M_2, ..., M_k are children of a SERIES node,
     * then for any i != j, and any v in M_i, w in M_j, there is an edge (v, w).</p>
     */
    SERIES,

    /**
     * A parallel node - no children are adjacent to each other.
     *
     * <p>In a PARALLEL node, no vertex in one child module is adjacent to any
     * vertex in any other child module. An independent set is represented by
     * a single PARALLEL node with n NORMAL children.</p>
     *
     * <p>Mathematically, if modules M_1, M_2, ..., M_k are children of a PARALLEL node,
     * then for any i != j, and any v in M_i, w in M_j, there is NO edge (v, w).</p>
     */
    PARALLEL,

    /**
     * A normal (leaf) node - represents a single vertex in the original graph.
     *
     * <p>NORMAL nodes are always leaves in the MD tree. Each vertex in the
     * original graph corresponds to exactly one NORMAL node, and the total
     * number of NORMAL nodes equals the number of vertices in the graph.</p>
     */
    NORMAL
}
