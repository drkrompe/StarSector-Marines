package com.dillon.starsectormarines.battle.nav;

/**
 * Cardinal and diagonal directions on the 2D navigation grid.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.Direction}.
 * Differences vs the original: dropped the 3D rotation helper, renamed {@code dz}
 * to {@code dy} since this grid is top-down 2D (X/Y plane) rather than the
 * engine's 3D X/Z floor plane.
 *
 * <p>Bit index maps to the edge-passability byte in {@link NavigationGrid}.
 */
public enum Direction {
    N(0, 0, 1),
    E(1, 1, 0),
    S(2, 0, -1),
    W(3, -1, 0),
    NE(4, 1, 1),
    SE(5, 1, -1),
    SW(6, -1, -1),
    NW(7, -1, 1);

    private final int bit;
    public final int dx;
    public final int dy;

    Direction(int bit, int dx, int dy) {
        this.bit = bit;
        this.dx = dx;
        this.dy = dy;
    }

    /** Bit index within the edge-passability byte. */
    public int bit() {
        return bit;
    }

    /** The opposite direction (N↔S, NE↔SW, etc.). */
    public Direction opposite() {
        switch (this) {
            case N:  return S;
            case S:  return N;
            case E:  return W;
            case W:  return E;
            case NE: return SW;
            case SW: return NE;
            case NW: return SE;
            case SE: return NW;
            default: throw new IllegalStateException();
        }
    }

    public boolean isDiagonal() {
        return bit >= 4;
    }

    public static final Direction[] CARDINALS = {N, E, S, W};
    public static final Direction[] DIAGONALS = {NE, SE, SW, NW};
    public static final Direction[] ALL = values();
}
