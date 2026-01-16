package org.example.md;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a node in the modular decomposition tree.
 *
 * <p>The modular decomposition tree is a hierarchical representation of a graph's
 * modular structure. Each node in the tree represents a module (a set of vertices
 * with identical connectivity to vertices outside the set).</p>
 *
 * <h2>Node Types:</h2>
 * <ul>
 *   <li><b>NORMAL</b> - A leaf node representing a single vertex</li>
 *   <li><b>SERIES</b> - Children are pairwise adjacent (form a complete subgraph)</li>
 *   <li><b>PARALLEL</b> - Children are pairwise non-adjacent (form an independent set)</li>
 *   <li><b>PRIME</b> - Children cannot be further decomposed; represents an indivisible structure</li>
 * </ul>
 *
 * <h2>Tree Properties:</h2>
 * <ul>
 *   <li>Every leaf node is of type NORMAL and represents exactly one vertex</li>
 *   <li>The number of leaves equals the number of vertices in the original graph</li>
 *   <li>Internal nodes (SERIES, PARALLEL, PRIME) have at least 2 children</li>
 *   <li>Consecutive SERIES nodes are merged (same for PARALLEL)</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * // For a complete graph K3:
 * // Tree structure: SERIES(v0, v1, v2)
 * MDNode root = new MDNode(NodeType.SERIES);
 * root.addChild(new MDNode(NodeType.NORMAL, 0));
 * root.addChild(new MDNode(NodeType.NORMAL, 1));
 * root.addChild(new MDNode(NodeType.NORMAL, 2));
 *
 * // Get JSON representation
 * String json = root.toJson();
 * // {"type":"SERIES","children":[{"type":"NORMAL","vertex":0},{"type":"NORMAL","vertex":1},{"type":"NORMAL","vertex":2}]}
 * }</pre>
 *
 * @see NodeType
 * @see ModularDecomposition
 */
public class MDNode {

    /** The type of this node (NORMAL, SERIES, PARALLEL, or PRIME) */
    private final NodeType type;

    /** Child nodes (empty for leaf nodes) */
    private final List<MDNode> children;

    /** Vertex index (only set for NORMAL nodes, null otherwise) */
    private final Integer vertex;

    /**
     * Constructs a node with the specified type and vertex.
     *
     * <p>This constructor is typically used for creating leaf (NORMAL) nodes.</p>
     *
     * @param type the node type
     * @param vertex the vertex index (only meaningful for NORMAL nodes)
     */
    public MDNode(NodeType type, Integer vertex) {
        this.type = type;
        this.vertex = vertex;
        this.children = new ArrayList<>();
    }

    /**
     * Constructs an internal node with the specified type.
     *
     * <p>This constructor is typically used for creating SERIES, PARALLEL, or PRIME nodes.</p>
     *
     * @param type the node type
     */
    public MDNode(NodeType type) {
        this(type, null);
    }

    /**
     * Returns the type of this node.
     *
     * @return the node type (NORMAL, SERIES, PARALLEL, or PRIME)
     */
    public NodeType getType() {
        return type;
    }

    /**
     * Returns the list of child nodes.
     *
     * <p>For leaf (NORMAL) nodes, this list is empty. The returned list
     * is the actual internal list, so modifications will affect this node.</p>
     *
     * @return the list of children
     */
    public List<MDNode> getChildren() {
        return children;
    }

    /**
     * Returns an unmodifiable view of the children.
     *
     * @return an unmodifiable list of children
     */
    public List<MDNode> getChildrenUnmodifiable() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the vertex index for NORMAL nodes.
     *
     * @return the vertex index, or null for non-leaf nodes
     */
    public Integer getVertex() {
        return vertex;
    }

    /**
     * Adds a child node to this node.
     *
     * @param child the child node to add
     * @throws IllegalStateException if this node is a leaf (NORMAL) node
     */
    public void addChild(MDNode child) {
        if (isLeaf()) {
            throw new IllegalStateException("Cannot add children to a leaf (NORMAL) node");
        }
        children.add(child);
    }

    /**
     * Checks if this node is a leaf node (type NORMAL).
     *
     * @return true if this is a leaf node, false otherwise
     */
    public boolean isLeaf() {
        return type == NodeType.NORMAL;
    }

    /**
     * Checks if this node is a degenerate node (SERIES or PARALLEL).
     *
     * <p>Degenerate nodes represent complete connectivity (SERIES) or
     * no connectivity (PARALLEL) between their children.</p>
     *
     * @return true if this is a SERIES or PARALLEL node
     */
    public boolean isDegenerate() {
        return type == NodeType.SERIES || type == NodeType.PARALLEL;
    }

    /**
     * Returns the number of children of this node.
     *
     * @return the number of children (0 for leaf nodes)
     */
    public int childCount() {
        return children.size();
    }

    /**
     * Counts the total number of leaf nodes in this subtree.
     *
     * <p>This corresponds to the number of vertices in the module
     * represented by this node.</p>
     *
     * @return the number of leaves in this subtree
     */
    public int countLeaves() {
        if (isLeaf()) {
            return 1;
        }
        int count = 0;
        for (MDNode child : children) {
            count += child.countLeaves();
        }
        return count;
    }

    /**
     * Returns the height of this subtree.
     *
     * <p>A leaf node has height 0. The height of an internal node
     * is 1 + max(height of children).</p>
     *
     * @return the height of this subtree
     */
    public int height() {
        if (isLeaf()) {
            return 0;
        }
        int maxChildHeight = 0;
        for (MDNode child : children) {
            maxChildHeight = Math.max(maxChildHeight, child.height());
        }
        return 1 + maxChildHeight;
    }

    /**
     * Converts this tree to a JSON string representation.
     *
     * <p>The JSON format is:</p>
     * <pre>
     * {
     *   "type": "SERIES|PARALLEL|PRIME|NORMAL",
     *   "vertex": 0,  // only for NORMAL nodes
     *   "children": [ ... ]  // only for internal nodes
     * }
     * </pre>
     *
     * @return JSON string representation of this tree
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        toJsonHelper(sb);
        return sb.toString();
    }

    /**
     * Helper method for recursive JSON construction.
     */
    private void toJsonHelper(StringBuilder sb) {
        sb.append("{");
        sb.append("\"type\":\"").append(type.name()).append("\"");

        if (isLeaf() && vertex != null) {
            sb.append(",\"vertex\":").append(vertex);
        } else if (!children.isEmpty()) {
            sb.append(",\"children\":[");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                children.get(i).toJsonHelper(sb);
            }
            sb.append("]");
        }

        sb.append("}");
    }

    /**
     * Returns a human-readable string representation for debugging.
     *
     * @return a string representation of this node
     */
    @Override
    public String toString() {
        if (isLeaf()) {
            return "MDNode(NORMAL, vertex=" + vertex + ")";
        }
        return "MDNode(" + type + ", children=" + children.size() + ")";
    }

    /**
     * Returns a pretty-printed tree representation for debugging.
     *
     * @return a multi-line string showing the tree structure
     */
    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        toPrettyStringHelper(sb, "", true);
        return sb.toString();
    }

    /**
     * Helper method for recursive pretty-printing.
     */
    private void toPrettyStringHelper(StringBuilder sb, String prefix, boolean isLast) {
        sb.append(prefix);
        sb.append(isLast ? "+-- " : "|-- ");

        if (isLeaf()) {
            sb.append(type).append("(").append(vertex).append(")");
        } else {
            sb.append(type);
        }
        sb.append("\n");

        String childPrefix = prefix + (isLast ? "    " : "|   ");
        for (int i = 0; i < children.size(); i++) {
            children.get(i).toPrettyStringHelper(sb, childPrefix, i == children.size() - 1);
        }
    }
}
