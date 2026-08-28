package com.dillon.starsectormarines.battle.nav;

/**
 * One physical feature occupying a canonical shared cardinal edge.
 *
 * <p>The identity is stored once, on either an east- or north-facing edge;
 * reciprocal lookups return this same object. Its {@link Kind} supplies every
 * cross-system property so movement, sight, direct fire, cover, durability,
 * and presentation cannot silently describe different features.
 *
 * <p>Most barriers are authored by generation before play. A kind that leaves
 * movement alone may additionally be placed at runtime — see
 * {@code MapEditor.placeDeployedBarrier}, which is the only construction seam
 * and refuses any kind that would close a transition.
 */
public final class SharedEdgeBarrier {

    /**
     * Barrier profiles. Add a kind only with all semantics defined — the point
     * of one profile is that a feature cannot describe itself differently to
     * movement, sight, ballistics, cover, and the renderer.
     */
    public enum Kind {
        /** Transparent firing window: impassable, shoot-through, low framed cover. */
        WINDOW(true, false, false, 1,
                NavigationGrid.DEFAULT_COVER_CATCH_HALF_HEIGHT, 40),
        /**
         * Chest-high field revetment: a barricade you can step over, shoot
         * over, and see over, which catches a round crossing it at body height.
         * It is the one profile that leaves the navigation transition open, and
         * therefore the one profile a runtime placement may use — nothing about
         * the grid becomes less permissive when it appears, so no path is
         * invalidated and no unit can be stranded behind it.
         */
        REVETMENT(false, false, false, 1,
                NavigationGrid.DEFAULT_COVER_CATCH_HALF_HEIGHT, 60);

        private final boolean blocksMovement;
        private final boolean blocksSight;
        private final boolean blocksProjectiles;
        private final int coverLevel;
        private final float coverCatchHalfHeight;
        private final int structure;

        Kind(boolean blocksMovement, boolean blocksSight, boolean blocksProjectiles,
             int coverLevel, float coverCatchHalfHeight, int structure) {
            this.blocksMovement = blocksMovement;
            this.blocksSight = blocksSight;
            this.blocksProjectiles = blocksProjectiles;
            this.coverLevel = coverLevel;
            this.coverCatchHalfHeight = coverCatchHalfHeight;
            this.structure = structure;
        }

        /** Whether the owned shared transition is closed while this feature stands. */
        public boolean blocksMovement() { return blocksMovement; }
        public boolean blocksSight() { return blocksSight; }
        public boolean blocksProjectiles() { return blocksProjectiles; }
        public int coverLevel() { return coverLevel; }
        public float coverCatchHalfHeight() { return coverCatchHalfHeight; }
        public int structure() { return structure; }
    }

    private final int cellX;
    private final int cellY;
    private final Direction direction;
    private final Kind kind;
    /** Adjacent authored cell whose structure owns this edge feature. */
    private final int structureCellX;
    private final int structureCellY;
    private int structure;

    SharedEdgeBarrier(int cellX, int cellY, Direction direction, Kind kind,
                      int structureCellX, int structureCellY) {
        if (direction != Direction.E && direction != Direction.N) {
            throw new IllegalArgumentException(
                    "canonical shared-edge barriers must face east or north");
        }
        this.cellX = cellX;
        this.cellY = cellY;
        this.direction = direction;
        this.kind = kind;
        this.structureCellX = structureCellX;
        this.structureCellY = structureCellY;
        this.structure = kind.structure();
    }

    public int cellX() { return cellX; }
    public int cellY() { return cellY; }
    public Direction direction() { return direction; }
    public Kind kind() { return kind; }
    public int structureCellX() { return structureCellX; }
    public int structureCellY() { return structureCellY; }
    public int structure() { return structure; }
    public int maxStructure() { return kind.structure(); }

    public float midpointX() {
        return direction == Direction.E ? cellX + 1f : cellX + 0.5f;
    }

    public float midpointY() {
        return direction == Direction.N ? cellY + 1f : cellY + 0.5f;
    }

    boolean damage(int amount) {
        if (amount <= 0 || structure <= 0) return false;
        structure = Math.max(0, structure - amount);
        return structure == 0;
    }
}
