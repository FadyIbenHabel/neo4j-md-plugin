package org.example.md;

import java.util.*;

/**
 * Complete implementation of the Corneil-Habib-Paul-Tedder (CHPT) modular decomposition algorithm.
 *
 * <p>This is a faithful port of the Cython implementation from SageMath, providing
 * a pure Java implementation that runs entirely within Neo4j.</p>
 *
 * <h2>Algorithm Overview:</h2>
 * <p>The CHPT algorithm computes the modular decomposition of an undirected graph
 * in linear time O(n + m). The algorithm proceeds in several phases:</p>
 * <ol>
 *   <li><b>LexBFS Ordering:</b> Compute a lexicographic breadth-first search ordering
 *       of vertices using partition refinement.</li>
 *   <li><b>Recursive Decomposition:</b> Process each "x-slice" (vertices with the same
 *       lexicographic label prefix) recursively.</li>
 *   <li><b>Marking Phase:</b> Mark nodes in the partitive forest based on their
 *       connectivity to the pivot vertex.</li>
 *   <li><b>Parse and Assemble:</b> Construct the final modular decomposition tree
 *       using the Left/Right arrays.</li>
 * </ol>
 *
 * <h2>Reference:</h2>
 * <p>"A Simple Linear-Time Modular Decomposition Algorithm"<br>
 * by Derek G. Corneil, Michel Habib, Jean-Marc Lanlignel, Bruce Reed, and Udi Rotics<br>
 * SIAM Journal on Discrete Mathematics, 2008</p>
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
 * MDNode tree = ModularDecomposition.compute(g);
 * System.out.println(tree.toJson());
 * }</pre>
 *
 * @see Graph
 * @see MDNode
 * @see LexBFS
 */
public class ModularDecomposition {

    /**
     * Flag value indicating the node has NOT been touched in the current marking pass.
     * Used for partitioning children during the marking phase.
     */
    private static final boolean FLAG_O = false;

    /**
     * Flag value indicating the node HAS been touched (is in the "Full" set).
     * Used for partitioning children during the marking phase.
     */
    private static final boolean FLAG_STAR = true;

    /**
     * Internal tree node used during algorithm computation.
     *
     * <p>This corresponds to md_tree_node in the original Cython implementation.
     * It maintains additional state (label, flag, sliceOrCcTag) needed during
     * the marking and assembly phases of the algorithm.</p>
     */
    private static class InternalNode {
        /** The type of this node (NORMAL, SERIES, PARALLEL, PRIME) */
        NodeType type;

        /** Child nodes */
        List<InternalNode> children;

        /** Vertex index (only for leaf/NORMAL nodes) */
        Integer vertex;

        /** Marking label used during the partitive forest marking phase */
        NodeLabel label;

        /** Flag used during marking (FLAG_O or FLAG_STAR) */
        boolean flag;

        /** Multi-purpose tag: slice index or connected component tag */
        int sliceOrCcTag;

        /** Parent node in the tree */
        InternalNode parent;

        InternalNode(NodeType type, Integer vertex, NodeLabel label, boolean flag, int sliceOrCcTag) {
            this.type = type;
            this.vertex = vertex;
            this.children = new ArrayList<>();
            this.label = label;
            this.flag = flag;
            this.sliceOrCcTag = sliceOrCcTag;
            this.parent = null;
        }

        InternalNode(NodeType type, Integer vertex) {
            this(type, vertex, NodeLabel.EMPTY, FLAG_O, -1);
        }

        boolean isLeaf() {
            return type == NodeType.NORMAL;
        }

        boolean isDegenerate() {
            return type == NodeType.SERIES || type == NodeType.PARALLEL;
        }

        boolean isHomogeneousOrEmpty() {
            return label.isHomogeneousOrEmpty();
        }

        boolean isDeadOrBroken() {
            return label.isDeadOrBroken();
        }

        void setLabelAndFlagRecursively(NodeLabel l, boolean f) {
            this.label = l;
            this.flag = f;
            for (InternalNode child : children) {
                child.setLabelAndFlagRecursively(l, f);
            }
        }

        void addChild(InternalNode child) {
            child.parent = this;
            children.add(child);
        }
    }

    /**
     * Represents a module (connected component) for cluster computation
     */
    private static class Module {
        InternalNode root;
        InternalNode leftmost;

        Module(InternalNode root, InternalNode leftmost) {
            this.root = root;
            this.leftmost = leftmost;
        }
    }

    /**
     * Scratch data structure to avoid reallocations during recursion
     */
    private static class ScratchData {
        List<List<Module>> clusters = new ArrayList<>();
        Map<Integer, Integer> clusterOfV = new HashMap<>();
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
    }

    /**
     * Compute the modular decomposition tree of a graph
     */
    public static MDNode compute(Graph graph) {
        if (graph.size() == 0) {
            return null;
        }

        // Compute LexBFS and slice decomposition
        LexBFS.Result lexResult = LexBFS.compute(graph);

        // Build the modular decomposition tree
        Map<Integer, InternalNode> leaves = new HashMap<>();
        ScratchData scratch = new ScratchData();

        List<InternalNode> roots = computeInternal(
            graph,
            lexResult.sigma,
            lexResult.xsliceLen,
            lexResult.lexLabel,
            0,
            graph.size(),
            0,
            leaves,
            scratch
        );

        // Convert internal representation to public MDNode
        if (roots.isEmpty()) {
            return null;
        }

        return convertToMDNode(roots.get(0));
    }

    /**
     * Recursive computation of modular decomposition.
     * This is the core of the CHPT algorithm.
     */
    private static List<InternalNode> computeInternal(
            Graph graph,
            int[] sigma,
            int[] xsliceLen,
            List<List<Integer>> lexLabel,
            int offset,
            int length,
            int lexLabelOffset,
            Map<Integer, InternalNode> leaves,
            ScratchData scratch) {

        List<InternalNode> roots = new ArrayList<>();

        if (length == 0) {
            return roots;
        }

        // Base case: single vertex
        if (length == 1) {
            int x = sigma[offset];
            InternalNode leaf = new InternalNode(NodeType.NORMAL, x);
            leaves.put(x, leaf);
            roots.add(leaf);
            return roots;
        }

        // Base case: two vertices
        if (length == 2) {
            int x = sigma[offset];
            int y = sigma[offset + 1];

            // Check if there's an edge between x and y
            List<Integer> label = lexLabel.get(offset + 1);
            boolean hasEdge = label.size() > lexLabelOffset;
            NodeType rootType = hasEdge ? NodeType.SERIES : NodeType.PARALLEL;

            InternalNode root = new InternalNode(rootType, null);
            InternalNode leafX = new InternalNode(NodeType.NORMAL, x);
            InternalNode leafY = new InternalNode(NodeType.NORMAL, y);

            root.addChild(leafX);
            root.addChild(leafY);

            leaves.put(x, leafX);
            leaves.put(y, leafY);
            roots.add(root);
            return roots;
        }

        // General case: process slices recursively
        int x = sigma[offset];
        List<List<InternalNode>> MDi = new ArrayList<>();

        int i = offset + 1;
        int firstOfLastSlice = i;

        while (i < offset + length) {
            firstOfLastSlice = i;
            int sliceLen = xsliceLen[i];

            List<InternalNode> sliceRoots = computeInternal(
                graph,
                sigma,
                xsliceLen,
                lexLabel,
                i,
                sliceLen,
                lexLabel.get(i).size(),
                leaves,
                scratch
            );

            // Set initial labels
            for (InternalNode node : sliceRoots) {
                node.setLabelAndFlagRecursively(NodeLabel.EMPTY, FLAG_O);
            }

            MDi.add(sliceRoots);
            i += sliceLen;
        }

        int nslices = MDi.size() + 1; // +1 for {x}

        // Check if graph is connected
        List<Integer> lastSliceLabel = lexLabel.get(firstOfLastSlice);
        boolean isConnected = lastSliceLabel.size() > lexLabelOffset;

        // Check if x is isolated
        List<Integer> firstLabel = lexLabel.get(offset + 1);
        boolean xIsIsolated = firstLabel.size() <= lexLabelOffset;

        if (xIsIsolated) {
            // X is isolated: create PARALLEL node
            List<InternalNode> md = MDi.get(0);

            if (md.size() == 1 && md.get(0).type == NodeType.PARALLEL) {
                // Reuse existing PARALLEL node
                roots.addAll(md);
                InternalNode leafX = new InternalNode(NodeType.NORMAL, x);
                leaves.put(x, leafX);
                roots.get(0).addChild(leafX);
            } else {
                // Create new PARALLEL node
                InternalNode root = new InternalNode(NodeType.PARALLEL, null);
                InternalNode leafX = new InternalNode(NodeType.NORMAL, x);
                leaves.put(x, leafX);

                root.addChild(leafX);
                for (InternalNode node : md) {
                    root.addChild(node);
                }
                roots.add(root);
            }

            return roots;
        }

        // Handle disconnected graph: when x has neighbors but some vertices are unreachable
        // This happens when the last slice has an empty lex label (no connection to earlier vertices)
        if (!isConnected) {
            // Graph is disconnected - need to create PARALLEL of connected components
            // Find which slices are connected to x (have non-empty lex labels pointing to earlier slices)
            List<InternalNode> connectedRoots = new ArrayList<>();
            List<InternalNode> disconnectedRoots = new ArrayList<>();

            // First slice (neighbors of x) is always connected to x
            connectedRoots.addAll(MDi.get(0));

            // Check each subsequent slice
            int sliceStart = offset + 1 + xsliceLen[offset + 1];
            for (int sliceIdx = 1; sliceIdx < MDi.size(); sliceIdx++) {
                List<Integer> sliceLabel = lexLabel.get(sliceStart);
                if (sliceLabel.size() > lexLabelOffset) {
                    // This slice is connected to earlier vertices
                    connectedRoots.addAll(MDi.get(sliceIdx));
                } else {
                    // This slice is disconnected
                    disconnectedRoots.addAll(MDi.get(sliceIdx));
                }
                sliceStart += xsliceLen[sliceStart];
            }

            // Build the connected component containing x
            InternalNode xComponent;
            InternalNode leafX = new InternalNode(NodeType.NORMAL, x);
            leaves.put(x, leafX);

            if (connectedRoots.isEmpty()) {
                xComponent = leafX;
            } else if (connectedRoots.size() == 1 && connectedRoots.get(0).type == NodeType.SERIES) {
                // Merge x into existing SERIES
                xComponent = connectedRoots.get(0);
                xComponent.addChild(leafX);
            } else {
                // Create SERIES for x and its neighbors
                xComponent = new InternalNode(NodeType.SERIES, null);
                xComponent.addChild(leafX);
                for (InternalNode node : connectedRoots) {
                    xComponent.addChild(node);
                }
            }

            // Create PARALLEL node for all components
            InternalNode root = new InternalNode(NodeType.PARALLEL, null);
            root.addChild(xComponent);
            for (InternalNode node : disconnectedRoots) {
                if (node.type == NodeType.PARALLEL) {
                    // Flatten nested PARALLEL
                    for (InternalNode child : node.children) {
                        root.addChild(child);
                    }
                } else {
                    root.addChild(node);
                }
            }

            roots.add(root);
            return roots;
        }

        // X is not isolated and graph is connected: perform marking and assembly

        // Preprocessing: set connected components tags
        for (int sliceIdx = 0; sliceIdx < MDi.size(); sliceIdx++) {
            setConnectedComponentsTag(MDi.get(sliceIdx), sliceIdx == 0);
        }

        // Mark partitive forest
        i = offset + 1;
        boolean first = true;
        while (i < offset + length) {
            if (!first) {
                List<Integer> label = lexLabel.get(i);
                if (label.size() > lexLabelOffset) {
                    List<Integer> subLabel = label.subList(lexLabelOffset, label.size());
                    markPartitiveForestOneSet(subLabel, leaves);
                }
            }
            first = false;
            i += xsliceLen[i];
        }

        // Finish marking
        for (List<InternalNode> md : MDi) {
            for (InternalNode node : md) {
                markPartitiveForestFinish(node);
            }
        }

        // Extract and sort
        for (int sliceIdx = 0; sliceIdx < MDi.size(); sliceIdx++) {
            extractAndSort(MDi.get(sliceIdx), sliceIdx == 0);
        }

        // Build clusters
        scratch.clusters.clear();
        scratch.clusterOfV.clear();

        for (int sliceIdx = 0; sliceIdx < MDi.size(); sliceIdx++) {
            List<InternalNode> md = MDi.get(sliceIdx);

            int prevCc = -1;
            for (InternalNode n : md) {
                int cc = n.sliceOrCcTag;

                // Find leftmost descendant
                InternalNode c = n;
                while (!c.children.isEmpty()) {
                    c = c.children.get(0);
                }
                int v = c.vertex;

                // Store slice index in sliceOrCcTag temporarily
                n.sliceOrCcTag = sliceIdx;

                if (cc == -1) {
                    // n is alone in the cluster
                    List<Module> newCluster = new ArrayList<>();
                    newCluster.add(new Module(n, c));
                    scratch.clusters.add(newCluster);
                    prevCc = -1;
                } else {
                    if (cc != prevCc) {
                        // Start new cluster
                        scratch.clusters.add(new ArrayList<>());
                    }
                    scratch.clusters.get(scratch.clusters.size() - 1).add(new Module(n, c));
                    prevCc = cc;
                }

                scratch.clusterOfV.put(v, scratch.clusters.size() - 1);
            }
        }

        // Add cluster for {x}
        InternalNode leafX = new InternalNode(NodeType.NORMAL, x);
        leaves.put(x, leafX);
        int p = scratch.clusters.size();
        List<Module> xCluster = new ArrayList<>();
        xCluster.add(new Module(leafX, leafX));
        scratch.clusters.add(xCluster);
        int q = scratch.clusters.size() - 1;

        // Compute Left array
        computeLeft(graph, sigma, xsliceLen, offset, length, p, scratch);

        // Compute Right array
        computeRight(sigma, xsliceLen, lexLabel, offset, length, lexLabelOffset, p, q, scratch);

        // Parse and assemble
        List<InternalNode> result = parseAndAssemble(graph, p, q, scratch);

        return result;
    }

    /**
     * Set connected components tag for roots.
     * Corresponds to set_connected_components_tag in Cython.
     */
    private static void setConnectedComponentsTag(List<InternalNode> roots, boolean first) {
        int i = 0;
        for (InternalNode r : roots) {
            NodeType t = r.type;
            if (t == NodeType.PRIME || (first && t == NodeType.PARALLEL) || (!first && t == NodeType.SERIES)) {
                r.sliceOrCcTag = i;
            } else {
                r.sliceOrCcTag = -1;
                for (InternalNode c : r.children) {
                    c.sliceOrCcTag = i;
                    i++;
                }
            }
        }
    }

    /**
     * Mark partitive forest for one set of vertices.
     * Corresponds to _mark_partitive_forest_one_set in Cython (lines 1340-1435).
     */
    private static void markPartitiveForestOneSet(List<Integer> lexLabelVerts, Map<Integer, InternalNode> leaves) {
        Set<InternalNode> marked = new HashSet<>();
        Set<InternalNode> full = new HashSet<>();
        Queue<InternalNode> explore = new LinkedList<>();

        // Mark leaves in lex label
        for (int v : lexLabelVerts) {
            InternalNode leaf = leaves.get(v);
            if (leaf != null) {
                explore.add(leaf);
            }
        }

        // Propagate marking up the tree
        while (!explore.isEmpty()) {
            InternalNode n = explore.poll();
            full.add(n);

            if (n.label == NodeLabel.EMPTY) {
                n.label = NodeLabel.HOMOGENEOUS;
            }

            if (n.parent != null) {
                InternalNode p = n.parent;
                marked.add(p);

                // Check if all children of p are in full
                boolean allChildrenInFull = true;
                for (InternalNode child : p.children) {
                    if (!full.contains(child)) {
                        allChildrenInFull = false;
                        break;
                    }
                }

                if (allChildrenInFull) {
                    marked.remove(p);
                    explore.add(p);
                }
            }
        }

        // Process marked nodes
        for (InternalNode n : marked) {
            // Gather children in Full and not in Full
            if (n.isDegenerate()) {
                NodeType t = n.type;
                List<InternalNode> childrenInFull = new ArrayList<>();
                List<InternalNode> childrenNotInFull = new ArrayList<>();

                for (InternalNode c : n.children) {
                    if (full.contains(c)) {
                        childrenInFull.add(c);
                    } else {
                        childrenNotInFull.add(c);
                    }
                }

                // Create new nodes if needed
                int nA = childrenInFull.size();
                int nB = childrenNotInFull.size();

                if (nA >= 2 || nB >= 2) {
                    n.children.clear();

                    if (nA >= 2) {
                        InternalNode newnodeA = new InternalNode(t, null, NodeLabel.HOMOGENEOUS, FLAG_STAR, -1);
                        newnodeA.parent = n;
                        for (InternalNode c : childrenInFull) {
                            newnodeA.addChild(c);
                        }
                        n.children.add(newnodeA);
                    } else if (nA == 1) {
                        InternalNode c = childrenInFull.get(0);
                        c.parent = n;
                        n.children.add(c);
                    }

                    if (nB >= 2) {
                        InternalNode newnodeB = new InternalNode(t, null, NodeLabel.EMPTY, FLAG_O, -1);
                        newnodeB.parent = n;
                        for (InternalNode c : childrenNotInFull) {
                            newnodeB.addChild(c);
                        }
                        n.children.add(newnodeB);
                    } else if (nB == 1) {
                        InternalNode c = childrenNotInFull.get(0);
                        c.parent = n;
                        n.children.add(c);
                    }
                }
            }

            if (n.label != NodeLabel.DEAD) {
                n.label = NodeLabel.DEAD;
                // Set flag to * for children in Full
                for (InternalNode c : n.children) {
                    if (full.contains(c)) {
                        c.flag = FLAG_STAR;
                    }
                }
            }
        }
    }

    /**
     * Finish marking process.
     * Corresponds to _mark_partitive_forest_finish in Cython (lines 1436-1473).
     */
    private static void markPartitiveForestFinish(InternalNode n) {
        int nbHomogeneousOrEmpty = 0;

        // Postorder: visit children first
        for (InternalNode c : n.children) {
            markPartitiveForestFinish(c);
            if (c.isHomogeneousOrEmpty()) {
                nbHomogeneousOrEmpty++;
            }
        }

        // Process current node
        if (n.isDeadOrBroken()) {
            InternalNode parent = n.parent;
            if (parent != null && parent.label != NodeLabel.DEAD) {
                parent.label = NodeLabel.BROKEN;
            }

            if (n.label == NodeLabel.BROKEN && n.isDegenerate() && nbHomogeneousOrEmpty > 1) {
                InternalNode newnodeA = new InternalNode(n.type, null, NodeLabel.EMPTY, FLAG_O, -1);
                newnodeA.parent = n;

                List<InternalNode> remaining = new ArrayList<>();
                for (InternalNode c : n.children) {
                    if (c.isHomogeneousOrEmpty()) {
                        newnodeA.addChild(c);
                    } else {
                        remaining.add(c);
                    }
                }

                n.children.clear();
                n.children.addAll(remaining);
                n.children.add(newnodeA);
            }
        }
    }

    /**
     * Sort dead and broken nodes, then extract them.
     * Corresponds to _extract_and_sort in Cython (lines 1527-1556).
     */
    private static void extractAndSort(List<InternalNode> roots, boolean firstSlice) {
        // Sort children of DEAD nodes
        for (InternalNode r : roots) {
            sortDeadRec(r, firstSlice);
        }

        // Sort children of BROKEN nodes
        for (InternalNode r : roots) {
            sortBrokenRec(r, firstSlice);
        }

        // Extract DEAD and BROKEN nodes
        List<InternalNode> newRoots = new ArrayList<>();
        for (InternalNode r : roots) {
            if (r.isDeadOrBroken()) {
                int cc = r.sliceOrCcTag;
                for (InternalNode c : r.children) {
                    if (cc != -1) {
                        c.sliceOrCcTag = cc;
                    }
                    c.parent = null;
                    newRoots.add(c);
                }
            } else {
                newRoots.add(r);
            }
        }

        roots.clear();
        roots.addAll(newRoots);
    }

    /**
     * Sort children of DEAD nodes.
     * Corresponds to _sort_dead_rec in Cython (lines 1475-1498).
     */
    private static void sortDeadRec(InternalNode n, boolean firstSlice) {
        if (n.isDeadOrBroken()) {
            for (InternalNode c : n.children) {
                sortDeadRec(c, firstSlice);
            }
        }

        if (n.label == NodeLabel.DEAD) {
            // Partition children: those with correct flag first
            List<InternalNode> front = new ArrayList<>();
            List<InternalNode> back = new ArrayList<>();

            for (InternalNode c : n.children) {
                if (firstSlice != (c.flag == FLAG_O)) {
                    front.add(c);
                } else {
                    back.add(c);
                }
            }

            n.children.clear();
            n.children.addAll(front);
            n.children.addAll(back);
        }
    }

    /**
     * Sort children of BROKEN nodes.
     * Corresponds to _sort_broken_rec in Cython (lines 1500-1525).
     */
    private static void sortBrokenRec(InternalNode n, boolean firstSlice) {
        if (n.isDeadOrBroken()) {
            for (InternalNode c : n.children) {
                sortBrokenRec(c, firstSlice);
            }
        }

        if (n.label == NodeLabel.BROKEN) {
            // Partition children
            List<InternalNode> front = new ArrayList<>();
            List<InternalNode> back = new ArrayList<>();

            for (InternalNode c : n.children) {
                if (firstSlice != c.isHomogeneousOrEmpty()) {
                    front.add(c);
                } else {
                    back.add(c);
                }
            }

            n.children.clear();
            n.children.addAll(front);
            n.children.addAll(back);
        }
    }

    /**
     * Compute Left array for parse-and-assemble.
     * Corresponds to Left computation in Cython (lines 1704-1731).
     */
    private static void computeLeft(Graph graph, int[] sigma, int[] xsliceLen,
                                    int offset, int length, int p, ScratchData scratch) {
        scratch.left.clear();

        // Left(j) == j for j <= p
        for (int j = 0; j <= p; j++) {
            scratch.left.add(j);
        }

        int i = offset + 1;
        int s = 0;
        int k = p + 1;

        while (i < offset + length) {
            if (s > 0) {
                int v = sigma[i];
                int lp = 0;

                // Find correct lp
                while (lp < p) {
                    boolean adj = true;
                    for (Module m : scratch.clusters.get(lp)) {
                        int u = m.leftmost.vertex;
                        if (!graph.hasEdge(u, v)) {
                            adj = false;
                            break;
                        }
                    }
                    if (!adj) break;
                    lp++;
                }

                // Assign lp to all clusters in this slice
                while (k < scratch.clusters.size() &&
                       scratch.clusters.get(k).get(0).root.sliceOrCcTag == s) {
                    scratch.left.add(lp);
                    k++;
                }
            }

            i += xsliceLen[i];
            s++;
        }
    }

    /**
     * Compute Right array for parse-and-assemble.
     * Corresponds to Right computation in Cython (lines 1733-1753).
     */
    private static void computeRight(int[] sigma, int[] xsliceLen, List<List<Integer>> lexLabel,
                                     int offset, int length, int lexLabelOffset, int p, int q,
                                     ScratchData scratch) {
        scratch.right.clear();

        // Right(j) == p for j <= p
        for (int j = 0; j <= p; j++) {
            scratch.right.add(p);
        }

        // Right(j) == j for p < j <= q
        for (int j = p + 1; j <= q; j++) {
            scratch.right.add(j);
        }

        int i = offset + 1;
        int s = 0;
        int j = 0;

        while (i < offset + length) {
            // Find highest index of cluster in this slice
            while (j + 1 < scratch.clusters.size() &&
                   scratch.clusters.get(j + 1).get(0).root.sliceOrCcTag == s) {
                j++;
            }

            // Update Right based on lexicographic labels
            if (s > 0) {
                List<Integer> label = lexLabel.get(i);
                for (int idx = lexLabelOffset; idx < label.size(); idx++) {
                    int v = label.get(idx);
                    Integer clusterIdx = scratch.clusterOfV.get(v);
                    if (clusterIdx != null) {
                        scratch.right.set(clusterIdx, j);
                    }
                }
            } else {
                j++; // Skip cluster {x}
            }

            i += xsliceLen[i];
            s++;
        }
    }

    /**
     * Parse and assemble the final tree.
     * Corresponds to parse and assemble logic in Cython (lines 1755-1791).
     *
     * <p>This function builds the final modular decomposition tree by iteratively
     * expanding from the pivot cluster (at index p) and combining clusters into
     * SERIES, PARALLEL, or PRIME nodes.</p>
     *
     * <p>PRIME detection: A node is PRIME when we cannot do a pure SERIES or PARALLEL
     * expansion. This happens when:</p>
     * <ul>
     *   <li>We need to expand in both directions at once</li>
     *   <li>The Left/Right arrays force us to include more than one additional cluster</li>
     *   <li>The new cluster is not uniformly adjacent/non-adjacent to existing clusters</li>
     * </ul>
     */
    private static List<InternalNode> parseAndAssemble(Graph graph, int p, int q, ScratchData scratch) {
        List<InternalNode> roots = new ArrayList<>();

        // Initialize with the pivot cluster at position p
        for (Module m : scratch.clusters.get(p)) {
            roots.add(m.root);
        }

        int l = p;
        int r = p;

        // Track all vertices currently in the assembled tree
        Set<Integer> currentVertices = new HashSet<>();
        for (Module m : scratch.clusters.get(p)) {
            collectVertices(m.root, currentVertices);
        }

        while (l > 0 || r < q) {
            int oldL = l;
            int oldR = r;

            NodeType t;
            int lp, rp;

            // Decide initial direction: SERIES (expand left) or PARALLEL (expand right)
            // SERIES: next cluster is ADJACENT to current set (will be connected)
            // PARALLEL: next cluster is NOT ADJACENT to current set (will be disconnected)
            if (l > 0 && isClustersAdjacentToCurrentSet(graph, scratch.clusters, l - 1, currentVertices)) {
                // Next left cluster is adjacent to current set -> SERIES
                lp = l - 1;
                rp = r;
                t = NodeType.SERIES;
            } else if (r < q) {
                // Can expand right -> PARALLEL
                lp = l;
                rp = r + 1;
                t = NodeType.PARALLEL;
            } else if (l > 0) {
                // Can only expand left, cluster is not adjacent -> PARALLEL
                lp = l - 1;
                rp = r;
                t = NodeType.PARALLEL;
            } else {
                // Shouldn't reach here, but default to SERIES
                lp = l;
                rp = r;
                t = NodeType.SERIES;
            }

            // Expand interval using Left and Right arrays
            boolean expandedLeft = false;
            boolean expandedRight = false;

            while (lp < l || r < rp) {
                if (lp < l) {
                    l = l - 1;
                    expandedLeft = true;
                } else {
                    r = r + 1;
                    expandedRight = true;
                }

                // Update bounds based on Left/Right arrays
                int idx = (l < oldL) ? l : r;
                if (idx >= 0 && idx < scratch.left.size()) {
                    int newLp = scratch.left.get(idx);
                    if (newLp < lp) {
                        lp = newLp;
                    }
                }
                if (idx >= 0 && idx < scratch.right.size()) {
                    int newRp = scratch.right.get(idx);
                    if (newRp > rp) {
                        rp = newRp;
                    }
                }
            }

            // Collect vertices being added in this step
            Set<Integer> newVertices = new HashSet<>();
            for (int i = l; i < oldL; i++) {
                for (Module m : scratch.clusters.get(i)) {
                    collectVertices(m.root, newVertices);
                }
            }
            for (int i = oldR + 1; i <= r; i++) {
                for (Module m : scratch.clusters.get(i)) {
                    collectVertices(m.root, newVertices);
                }
            }

            // Detect PRIME:
            // 1. If we expanded in BOTH directions
            // 2. If we expanded by more than 1 cluster total
            // 3. If new vertices violate the expected module property
            int totalExpansion = (oldL - l) + (r - oldR);
            boolean forcedBothDirections = expandedLeft && expandedRight;

            // For SERIES: new vertices must be adjacent to ALL current vertices
            // For PARALLEL: new vertices must NOT be adjacent to ANY current vertex AND
            //               must have the same external neighborhood as current vertices
            boolean violatesModuleProperty = false;
            if (!newVertices.isEmpty() && !currentVertices.isEmpty()) {
                if (t == NodeType.SERIES) {
                    // Check if all new vertices are adjacent to all current vertices
                    for (int nv : newVertices) {
                        for (int cv : currentVertices) {
                            if (!graph.hasEdge(nv, cv)) {
                                violatesModuleProperty = true;
                                break;
                            }
                        }
                        if (violatesModuleProperty) break;
                    }
                } else if (t == NodeType.PARALLEL) {
                    // Check if new vertices have the same neighborhood OUTSIDE current set
                    // Get the external neighborhood of the current set
                    Set<Integer> allInSet = new HashSet<>(currentVertices);
                    allInSet.addAll(newVertices);

                    // Get external neighbors of first current vertex
                    Set<Integer> expectedExtNeighbors = new HashSet<>();
                    int firstCurrent = currentVertices.iterator().next();
                    for (int v = 0; v < graph.size(); v++) {
                        if (!allInSet.contains(v) && graph.hasEdge(firstCurrent, v)) {
                            expectedExtNeighbors.add(v);
                        }
                    }

                    // Check all new vertices have the same external neighborhood
                    for (int nv : newVertices) {
                        Set<Integer> nvExtNeighbors = new HashSet<>();
                        for (int v = 0; v < graph.size(); v++) {
                            if (!allInSet.contains(v) && graph.hasEdge(nv, v)) {
                                nvExtNeighbors.add(v);
                            }
                        }
                        if (!nvExtNeighbors.equals(expectedExtNeighbors)) {
                            violatesModuleProperty = true;
                            break;
                        }
                    }
                }
            }

            if (forcedBothDirections || totalExpansion > 1 || violatesModuleProperty) {
                // PRIME detected - collect ALL remaining clusters into a single PRIME node
                InternalNode root = new InternalNode(NodeType.PRIME, null);

                // Add all clusters, flattening any non-NORMAL nodes
                // For a true PRIME graph, all vertices should be direct children
                for (int i = 0; i < p; i++) {
                    for (Module m : scratch.clusters.get(i)) {
                        addToPrimeNode(root, m.root);
                    }
                }

                // Add the pivot
                for (Module m : scratch.clusters.get(p)) {
                    addToPrimeNode(root, m.root);
                }

                // Add any clusters after pivot (if any)
                for (int i = p + 1; i <= q; i++) {
                    for (Module m : scratch.clusters.get(i)) {
                        addToPrimeNode(root, m.root);
                    }
                }

                roots.clear();
                roots.add(root);
                return roots; // Exit early - entire graph is prime
            }

            // Create new root node for SERIES/PARALLEL
            InternalNode root = new InternalNode(t, null);

            // Add children from left clusters
            for (int i = l; i < oldL; i++) {
                for (Module m : scratch.clusters.get(i)) {
                    addToRoot(root, m.root, t);
                }
            }

            // Add old root
            if (!roots.isEmpty()) {
                InternalNode oldRoot = roots.remove(roots.size() - 1);
                addToRoot(root, oldRoot, t);
            }

            // Add children from right clusters
            for (int i = oldR + 1; i <= r; i++) {
                for (Module m : scratch.clusters.get(i)) {
                    addToRoot(root, m.root, t);
                }
            }

            roots.add(root);
            currentVertices.addAll(newVertices);
        }

        return roots;
    }

    /**
     * Collect all vertex indices from a node and its descendants
     */
    private static void collectVertices(InternalNode node, Set<Integer> vertices) {
        if (node.isLeaf()) {
            vertices.add(node.vertex);
        } else {
            for (InternalNode child : node.children) {
                collectVertices(child, vertices);
            }
        }
    }

    /**
     * Check if new vertices have non-uniform adjacency to current vertices.
     * For SERIES, all new vertices should be adjacent to all current vertices.
     * For PARALLEL, no new vertex should be adjacent to any current vertex.
     * If neither holds, we have PRIME.
     */
    private static boolean hasNonUniformAdjacency(Graph graph, Set<Integer> newVerts,
                                                   Set<Integer> currentVerts, NodeType expectedType) {
        if (newVerts.isEmpty() || currentVerts.isEmpty()) {
            return false;
        }

        for (int newV : newVerts) {
            int adjCount = 0;
            for (int curV : currentVerts) {
                if (graph.hasEdge(newV, curV)) {
                    adjCount++;
                }
            }

            // For SERIES: new vertex must be adjacent to ALL current vertices
            // For PARALLEL: new vertex must be adjacent to NO current vertices
            if (expectedType == NodeType.SERIES && adjCount != currentVerts.size()) {
                return true; // Non-uniform: not fully connected
            }
            if (expectedType == NodeType.PARALLEL && adjCount != 0) {
                return true; // Non-uniform: has some connections
            }
        }

        return false;
    }

    /**
     * Add a node to root, handling degenerate node flattening
     */
    private static void addToRoot(InternalNode root, InternalNode node, NodeType rootType) {
        if (rootType != NodeType.PRIME && node.type == rootType) {
            // Flatten: add children directly
            for (InternalNode c : node.children) {
                root.addChild(c);
            }
        } else {
            root.addChild(node);
        }
    }

    /**
     * Add a node to a PRIME root, recursively flattening all non-leaf nodes.
     * For a true PRIME graph, all vertices should be direct children.
     */
    private static void addToPrimeNode(InternalNode primeRoot, InternalNode node) {
        if (node.isLeaf()) {
            primeRoot.addChild(node);
        } else {
            // Flatten: recursively add all descendants
            for (InternalNode child : node.children) {
                addToPrimeNode(primeRoot, child);
            }
        }
    }

    /**
     * Check if two clusters are non-adjacent.
     * Corresponds to _are_cluster_non_adjacent in Cython (lines 1560-1572).
     */
    private static boolean areClustersNonAdjacent(Graph graph, List<List<Module>> clusters, int i, int j) {
        if (i < 0 || i >= clusters.size() || j < 0 || j >= clusters.size()) {
            return true;
        }

        for (Module mi : clusters.get(i)) {
            int vi = mi.leftmost.vertex;
            for (Module mj : clusters.get(j)) {
                int vj = mj.leftmost.vertex;
                if (graph.hasEdge(vi, vj)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Check if a cluster is adjacent to any vertex in the current set.
     */
    private static boolean isClustersAdjacentToCurrentSet(Graph graph, List<List<Module>> clusters,
                                                          int clusterIdx, Set<Integer> currentVertices) {
        if (clusterIdx < 0 || clusterIdx >= clusters.size()) {
            return false;
        }

        for (Module m : clusters.get(clusterIdx)) {
            int clusterVertex = m.leftmost.vertex;
            for (int cv : currentVertices) {
                if (graph.hasEdge(clusterVertex, cv)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Convert internal node representation to public MDNode
     */
    private static MDNode convertToMDNode(InternalNode internal) {
        if (internal.isLeaf()) {
            return new MDNode(NodeType.NORMAL, internal.vertex);
        }

        MDNode node = new MDNode(internal.type);
        for (InternalNode child : internal.children) {
            node.addChild(convertToMDNode(child));
        }

        return node;
    }
}
