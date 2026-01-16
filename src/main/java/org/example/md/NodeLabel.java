package org.example.md;

/**
 * Labels used during the modular decomposition algorithm to track node states.
 *
 * <p>These labels are used in the marking phase of the CHPT algorithm to
 * identify how nodes in the partitive forest should be processed.</p>
 *
 * <p>The labels form a progression: EMPTY -> HOMOGENEOUS -> BROKEN -> DEAD,
 * where each subsequent state indicates more "damage" to the node's module
 * structure.</p>
 *
 * @see ModularDecomposition
 */
public enum NodeLabel {
    /**
     * Initial state - node has not been touched by any marking operation.
     */
    EMPTY(0),

    /**
     * Node's subtree is homogeneously connected to the pivot set.
     * All vertices in the subtree have identical adjacency to the pivot.
     */
    HOMOGENEOUS(1),

    /**
     * Node's subtree has mixed connectivity - some children are
     * connected to the pivot and some are not.
     */
    BROKEN(2),

    /**
     * Node has been fully processed and should be removed/replaced
     * in the final tree structure.
     */
    DEAD(3);

    private final int value;

    NodeLabel(int value) {
        this.value = value;
    }

    /**
     * Get the numeric value of this label.
     * Used for comparison operations (e.g., isHomogeneousOrEmpty checks value <= 1).
     *
     * @return the numeric value
     */
    public int getValue() {
        return value;
    }

    /**
     * Check if this label indicates the node is still "healthy" (EMPTY or HOMOGENEOUS).
     *
     * @return true if EMPTY or HOMOGENEOUS
     */
    public boolean isHomogeneousOrEmpty() {
        return this == EMPTY || this == HOMOGENEOUS;
    }

    /**
     * Check if this label indicates the node is "damaged" (BROKEN or DEAD).
     *
     * @return true if BROKEN or DEAD
     */
    public boolean isDeadOrBroken() {
        return this == BROKEN || this == DEAD;
    }
}
