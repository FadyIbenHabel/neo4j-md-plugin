package org.example.md;

import java.util.*;

/**
 * Implementation of extended Lexicographic Breadth-First Search (LexBFS) algorithm.
 *
 * <p>This implementation is based on the O(n+m) algorithm from:</p>
 * <blockquote>
 * Habib, McConnell, Paul, and Viennot (2000)<br>
 * "Lex-BFS and partition refinement, with applications to transitive orientation,
 * interval graph recognition and consecutive ones testing"
 * </blockquote>
 *
 * <h2>Algorithm Overview:</h2>
 * <p>LexBFS produces an ordering of vertices with special properties useful for
 * recognizing graph classes and computing modular decomposition. The algorithm
 * uses partition refinement to achieve linear O(n+m) time complexity.</p>
 *
 * <h2>Key Outputs:</h2>
 * <ul>
 *   <li><b>sigma:</b> The LexBFS ordering of vertices</li>
 *   <li><b>sigmaInv:</b> The inverse mapping (position of each vertex in sigma)</li>
 *   <li><b>xsliceLen:</b> Length of each x-slice (vertices with same lex label prefix)</li>
 *   <li><b>lexLabel:</b> The lexicographic label for each position</li>
 * </ul>
 *
 * <h2>Properties of LexBFS Ordering:</h2>
 * <p>If a < b < c in the ordering and ac is an edge but bc is not, then
 * there exists a vertex d with d < a such that db is an edge but dc is not.
 * This property is crucial for the modular decomposition algorithm.</p>
 *
 * <h2>Time Complexity:</h2>
 * <p>O(n + m) where n is the number of vertices and m is the number of edges.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * Graph g = new Graph(4);
 * g.addEdge(0, 1);
 * g.addEdge(1, 2);
 * g.addEdge(2, 3);
 *
 * LexBFS.Result result = LexBFS.compute(g);
 * // result.sigma contains the vertex ordering
 * // result.sigmaInv[v] gives the position of vertex v
 * }</pre>
 *
 * @see ModularDecomposition
 */
public class LexBFS {

    /**
     * Result of extended LexBFS computation.
     *
     * <p>Contains all the data structures needed by the modular decomposition
     * algorithm to process the graph.</p>
     */
    public static class Result {
        /**
         * The LexBFS ordering of vertices.
         * sigma[i] is the vertex at position i in the ordering.
         */
        public final int[] sigma;

        /**
         * Inverse of sigma.
         * sigmaInv[v] is the position of vertex v in the ordering.
         */
        public final int[] sigmaInv;

        /**
         * Length of each x-slice.
         * xsliceLen[i] is the number of vertices in the slice starting at position i.
         */
        public final int[] xsliceLen;

        /**
         * Lexicographic labels for each position.
         * lexLabel.get(i) contains the vertices that contributed to the label of position i.
         */
        public final List<List<Integer>> lexLabel;

        /**
         * Constructs a Result with the given data.
         *
         * @param sigma the LexBFS ordering
         * @param sigmaInv the inverse ordering
         * @param xsliceLen the slice lengths
         * @param lexLabel the lexicographic labels
         */
        public Result(int[] sigma, int[] sigmaInv, int[] xsliceLen, List<List<Integer>> lexLabel) {
            this.sigma = sigma;
            this.sigmaInv = sigmaInv;
            this.xsliceLen = xsliceLen;
            this.lexLabel = lexLabel;
        }
    }

    /**
     * Perform extended LexBFS on the graph starting from an arbitrary vertex.
     *
     * @param graph the input graph
     * @return the LexBFS result containing ordering and labels
     */
    public static Result compute(Graph graph) {
        return compute(graph, -1);
    }

    /**
     * Perform extended LexBFS on the graph starting from a specified vertex.
     *
     * <p>The algorithm maintains a partition of unprocessed vertices and refines
     * it based on adjacency to processed vertices. This produces an ordering
     * where vertices with larger lexicographic labels appear earlier.</p>
     *
     * @param graph the input graph
     * @param initialVertex the vertex to start from (-1 for arbitrary)
     * @return the LexBFS result containing ordering and labels
     */
    public static Result compute(Graph graph, int initialVertex) {
        int n = graph.size();

        // Initialize sigma (ordering) and sigmaInv (inverse ordering)
        int[] sigma = new int[n];
        int[] sigmaInv = new int[n];

        int idx = 0;
        if (initialVertex >= 0 && initialVertex < n) {
            sigma[0] = initialVertex;
            sigmaInv[initialVertex] = 0;
            idx = 1;
        }

        // Fill remaining vertices in default order
        for (int v = 0; v < n; v++) {
            if (v != initialVertex) {
                sigma[idx] = v;
                sigmaInv[v] = idx;
                idx++;
            }
        }

        // Initialize partition refinement data structures
        // Maximum number of parts equals number of edges + 1
        int maxParts = graph.getEdges().size() + 1;
        int[] partOf = new int[n];      // Which part each position belongs to
        Arrays.fill(partOf, 0);

        int[] partHead = new int[maxParts];  // Start index of each part
        int[] subpart = new int[maxParts];   // Subpart created during refinement
        int[] partLen = new int[maxParts];   // Length of each part

        Arrays.fill(partHead, 0);
        Arrays.fill(subpart, 0);
        partLen[0] = n;

        int nparts = 1;

        // Initialize xsliceLen and lexLabel
        int[] xsliceLen = new int[n];
        List<List<Integer>> lexLabel = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lexLabel.add(new ArrayList<>());
        }

        // Main LexBFS loop - process vertices in order
        for (int i = 0; i < n; i++) {
            int oldNparts = nparts;
            int partOfI = partOf[i];

            // Remove position i from its part
            partHead[partOfI]++;
            xsliceLen[i] = partLen[partOfI];
            partLen[partOfI]--;

            int vInt = sigma[i];

            // Refine partitions based on neighbors of v
            for (int uInt : graph.neighbors(vInt)) {
                int j = sigmaInv[uInt]; // Position of neighbor u
                if (j <= i) continue;   // Already processed

                // Add v to lexicographic label of position j
                lexLabel.get(j).add(vInt);

                int p = partOf[j];       // Part of u
                int l = partHead[p];     // Head of the part

                // If not last element and next belongs to same part, swap to front
                if (l < n - 1 && partOf[l + 1] == p) {
                    if (l != j) { // Not already first element
                        // Swap u with head of part
                        int tInt = sigma[l];
                        sigmaInv[tInt] = j;
                        sigmaInv[uInt] = l;
                        sigma[j] = tInt;
                        sigma[l] = uInt;

                        // Swap lex labels too
                        List<Integer> temp = lexLabel.get(j);
                        lexLabel.set(j, lexLabel.get(l));
                        lexLabel.set(l, temp);

                        j = l;
                    }
                    partHead[p]++;
                }

                // Create new subpart if needed
                if (subpart[p] < oldNparts) {
                    subpart[p] = nparts;
                    partHead[nparts] = j;
                    partLen[nparts] = 0;
                    subpart[nparts] = 0;
                    nparts++;
                }

                // Move position to subpart
                partOf[j] = subpart[p];
                partLen[p]--;
                partLen[subpart[p]]++;
            }
        }

        return new Result(sigma, sigmaInv, xsliceLen, lexLabel);
    }
}
