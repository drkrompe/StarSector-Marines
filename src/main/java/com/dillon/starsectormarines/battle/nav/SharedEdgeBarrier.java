package com.dillon.starsectormarines.battle.nav;

/**
 * One authored physical feature occupying a canonical shared cardinal edge.
 *
 * <p>The identity is stored once, on either an east- or north-facing edge;
 * reciprocal lookups return this same object. Its {@link Kind} supplies every
 * cross-system property so movement, sight, direct fire, cover, durability,
 * and presentation cannot silently describe different features.
 */
public final class SharedEdgeBarrier {

    /** Authored barrier profiles. Add a kind only with all semantics defined. */
    public enum Kind {
        /** Transparent firing window: impassable, shoot-through, low framed cover. */
        WINDOW(false, false, 1,
                NavigationGrid.DEFAULT_COVER_CATCH_HALF_HEIGHT, 40);

        private final boolean blocksSight;
        private final boolean blocksProjectiles;
        private final int coverLevel;
        private final float coverCatchHalfHeight;
        private final int structure;

        Kind(boolean blocksSight, boolean blocksProjectiles,
             int coverLevel, float coverCatchHalfHeight, int structure) {
            this.blocksSight = blocksSight;
            this.blocksProjectiles = blocksProjectiles;
            this.coverLevel = coverLevel;
            this.coverCatchHalfHeight = coverCatchHalfHeight;
            this.structure = structure;
        }

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
    private int structure;

    SharedEdgeBarrier(int cellX, int cellY, Direction direction, Kind kind) {
        if (direction != Direction.E && direction != Direction.N) {
            throw new IllegalArgumentException(
                    "canonical shared-edge barriers must face east or north");
        }
        this.cellX = cellX;
        this.cellY = cellY;
        this.direction = direction;
        this.kind = kind;
        this.structure = kind.structure();
    }

    public int cellX() { return cellX; }
    public int cellY() { return cellY; }
    public Direction direction() { return direction; }
    public Kind kind() { return kind; }
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
