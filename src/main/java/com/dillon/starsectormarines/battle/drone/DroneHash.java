package com.dillon.starsectormarines.battle.drone;

/**
 * Deterministic per-drone variation: a number in [0, 1) that depends only on the
 * drone's id and a salt. It stands in for a random roll wherever a drone's look
 * or timing wants to differ from its neighbour's, so the shared simulation
 * stream is never drawn from and a replay of the same battle flies the same
 * wings.
 */
public final class DroneHash {

    private DroneHash() {}

    /** A stable value in [0, 1) for ({@code id}, {@code salt}). */
    public static float unit(long id, int salt) {
        long z = id * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L + 0x632BE59BD9B4E019L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1 << 24);
    }
}
