package com.dillon.starsectormarines.battle.combat.fx;

import java.util.HashMap;
import java.util.Map;

/**
 * Where rounds have struck the walls, so a wall's face shows its hits where they landed rather than marks drawn from
 * its hit points. Each struck wall cell keeps its last {@link #PER_CELL} hits: the point the round reached the wall,
 * how high on it, and the mark it leaves ({@link Mark}). Cosmetic, like the ground decals beside it: never read by
 * the simulation, never saved.
 */
public final class WallScars {

    /** What a hit leaves on a wall face. */
    public enum Mark {
        /** A small-arms round: a chipped pock. */
        CHIP,
        /** A heavy round, a pulse or a beam: a deep gouge. */
        GOUGE,
        /** A rocket's or a shell's burst: a scorched blast scar round a punched hole. */
        BLAST
    }

    /** The hits a wall cell keeps; a later hit replaces its oldest. */
    public static final int PER_CELL = 8;
    /** Floats per kept hit: {@code x, y} (cells, where it reached the wall), metres up, size (cells), mark. */
    public static final int STRIDE = 5;

    private final Map<Integer, float[]> byCell = new HashMap<>();
    private final Map<Integer, int[]> counts = new HashMap<>();

    /** The mark an impact mark leaves on a wall, or null where it leaves none there. */
    public static Mark of(ImpactMark mark) {
        return switch (mark) {
            case NONE, FLAME, ACID, UPHEAVAL -> null;
            case SMALL_ARMS, FLAK -> Mark.CHIP;
            case HEAVY_ROUND, PULSE, BEAM -> Mark.GOUGE;
            case ROCKET, SHELL, HEAVY_SHELL, FRAG, MELT, INCENDIARY -> Mark.BLAST;
        };
    }

    /**
     * Keeps a round that flew from {@code (fromX, fromY)} and stopped against a wall at {@code (x, y)}, {@code z}
     * metres up, on a map {@code gridW} cells wide: the wall cell is the one the round was entering.
     */
    public void strike(int gridW, float fromX, float fromY, float x, float y, float z, Mark mark, float size) {
        if (mark == null) return;
        float dx = x - fromX, dy = y - fromY, len = (float) Math.hypot(dx, dy);
        float ix = len > 1e-4f ? x + dx / len * .05f : x, iy = len > 1e-4f ? y + dy / len * .05f : y;
        int cell = (int) Math.floor(ix) + (int) Math.floor(iy) * gridW;
        float[] kept = byCell.computeIfAbsent(cell, c -> new float[PER_CELL * STRIDE]);
        int[] count = counts.computeIfAbsent(cell, c -> new int[1]);
        int o = count[0] % PER_CELL * STRIDE;
        kept[o] = x;
        kept[o + 1] = y;
        kept[o + 2] = z;
        kept[o + 3] = size;
        kept[o + 4] = mark.ordinal();
        count[0]++;
    }

    /** How many hits wall cell {@code cell} keeps, read from {@link #of(int)}. */
    public int count(int cell) {
        int[] count = counts.get(cell);
        return count == null ? 0 : Math.min(PER_CELL, count[0]);
    }

    /** Wall cell {@code cell}'s kept hits, {@link #STRIDE} floats each, or null where it has none. */
    public float[] of(int cell) {
        return byCell.get(cell);
    }

    /** How many hits wall cells have taken in all, so a reader can tell when another lands. */
    public long total() {
        long n = 0;
        for (int[] c : counts.values()) n += c[0];
        return n;
    }

    public void clear() {
        byCell.clear();
        counts.clear();
    }
}
