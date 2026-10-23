package com.dillon.starsectormarines.battle.world.gen.layout;

/** A world direction a place's side faces, y up: north is +y. */
public enum Side {
    NORTH(0, 1), SOUTH(0, -1), EAST(1, 0), WEST(-1, 0);

    public final int dx, dy;

    Side(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /** The side facing world direction {@code (dx, dy)} (its larger axis). */
    public static Side facing(int dx, int dy) {
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? EAST : WEST;
        return dy >= 0 ? NORTH : SOUTH;
    }

    public Side opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
