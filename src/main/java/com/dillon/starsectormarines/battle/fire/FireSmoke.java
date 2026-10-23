package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Arrays;

/**
 * Smoke over a fire (`fire-nouns.md`, Smoke). A burning cell whose fuel is
 * brush or a crown holds smoke over itself and, in a wind, over the cell
 * downwind of it, for as long as it burns: the grid's transient opacity, the
 * same that a smoke grenade's cloud holds, so sight, cover and fog read it
 * with no new path. A grass or creep fire burns too low and fast to screen: it
 * gives off a thin haze that is drawn and screens nothing ({@link #hazes}).
 *
 * <p>Each burning cell's downwind cell is fixed when it catches, so what it
 * holds is exactly what it gives back when it burns out, however the wind
 * changes in between.
 */
public final class FireSmoke {

    public static final String PROPERTY = "battle.fire.smoke";

    /** Wind strength under which smoke stands over its own cell only. */
    public static final float DRIFT_WIND = 0.25f;
    private static final int NONE = -1;

    private final NavigationGrid grid;
    private final int width, height;
    /** Per cell: the downwind cell it holds smoke over, itself only ({@code idx}), or {@link #NONE}. */
    private final int[] held;
    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    public FireSmoke(NavigationGrid grid) {
        this.grid = grid;
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        this.held = new int[width * height];
        Arrays.fill(held, NONE);
    }

    /** Whether a fire burning that fuel smokes, and so screens. */
    public static boolean smokes(int fuelMask) {
        return FireFuel.BRUSH.in(fuelMask) || FireFuel.CROWN.in(fuelMask);
    }

    /** Whether a fire burning that fuel gives off only a haze: drawn, and screening nothing. */
    public static boolean hazes(int fuelMask) {
        return !smokes(fuelMask) && low(fuelMask);
    }

    /** Grass or creep, living or dead: the fuels that burn too low and fast to raise a screen. */
    private static boolean low(int fuelMask) {
        return FireFuel.GRASS.in(fuelMask) || FireFuel.CREEP.in(fuelMask) || FireFuel.DEAD_CREEP.in(fuelMask);
    }

    public void ignited(int idx, int fuelMask, float windX, float windY, float windStrength) {
        if (!enabled || !smokes(fuelMask) || held[idx] != NONE) return;
        grid.addTransientOpacityAt(idx);
        int down = idx;
        if (windStrength >= DRIFT_WIND) {
            int x = idx % width + Math.round(windX), y = idx / width + Math.round(windY);
            if (x >= 0 && y >= 0 && x < width && y < height) down = y * width + x;
            if (down != idx) grid.addTransientOpacityAt(down);
        }
        held[idx] = down;
    }

    public void burntOut(int idx) {
        int down = held[idx];
        if (down == NONE) return;
        grid.removeTransientOpacityAt(idx);
        if (down != idx) grid.removeTransientOpacityAt(down);
        held[idx] = NONE;
    }

    /** Whether the fire at this cell is holding smoke. */
    public boolean holds(int idx) { return held[idx] != NONE; }
}
