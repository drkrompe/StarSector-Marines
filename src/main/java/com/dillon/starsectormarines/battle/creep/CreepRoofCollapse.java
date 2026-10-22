package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.Arrays;

/**
 * Creep brings roofs down under its weight (`colony-breach-nouns.md`, Creep):
 * a building the growth has stood in for a while sags open, cell by cell.
 *
 * <ul>
 *   <li><b>Hold.</b> A roofed building cell counts how long living creep has
 *   stood on it ({@link CreepField#isCreeped}); the count resets when the creep
 *   withers. Once it reaches {@link #HOLD_SECONDS} plus a seeded share of
 *   {@link #HOLD_JITTER_SECONDS} the cell may fall.</li>
 *   <li><b>Start.</b> A held cell falls first beside a wall the creep has
 *   crusted (coverage {@link #CRUST}, living), so a cave-in opens from the
 *   building's edge.</li>
 *   <li><b>Spread.</b> A held cell beside a roof that has fallen (to creep or
 *   to war damage) falls {@link #SPREAD_SECONDS} plus a seeded share of
 *   {@link #SPREAD_JITTER_SECONDS} after it, so the hole runs inward a cell
 *   every few seconds and no two cells keep step.</li>
 * </ul>
 *
 * A fall goes through {@link MapEditor#destroyRoof}, the same cave-in war
 * damage makes: rubble, and the roof pass, line of sight and cover follow it.
 * Fallen roofs stay fallen. Reads the field and keeps its own per-cell hold, so
 * the field knows nothing of roofs. Runs once per simulation tick after the
 * creep has spread, looking every {@link #CHECK_TICKS}; deterministic in the
 * seed. {@code -Dbattle.swarm.creepRoofCollapse=false} is the control.
 */
public final class CreepRoofCollapse {

    /** Control-run switch; roofs fall unless it reads {@code false}. */
    public static final String PROPERTY = "battle.swarm.creepRoofCollapse";

    /** Ticks between looks: half a second. */
    public static final int CHECK_TICKS = 15;
    /** Seconds of living creep a roofed cell must hold, at least, before it may fall. */
    public static final float HOLD_SECONDS = 60f;
    /** Seeded extra hold per cell, up to this many seconds. */
    public static final float HOLD_JITTER_SECONDS = 30f;
    /** Wall coverage at which the creep has crusted it (about four fifths of the way to full, some 80 s of climbing). */
    public static final int CRUST = 200;
    /** Seconds, at least, between a roof falling and a held neighbour following it. */
    public static final float SPREAD_SECONDS = 2f;
    /** Seeded extra spread delay per cell, up to this many seconds. */
    public static final float SPREAD_JITTER_SECONDS = 4f;

    static final int TICKS_PER_SECOND = 30;

    /** What the rule reads of the map and the creep. Coordinates are always in bounds. */
    public interface Cells {
        int width();

        int height();

        /** A building's interior cell (its roof may stand or be gone). */
        boolean building(int x, int y);

        boolean roofGone(int x, int y);

        /** Living creep stands on the cell. */
        boolean creeped(int x, int y);

        /** A wall the creep has crusted over and still lives on. */
        boolean crustedWall(int x, int y);
    }

    /** Brings one roof down. */
    public interface Collapse {
        void fall(int x, int y);
    }

    private final long seed;
    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private int[] candidates;
    private int[] held;
    private int[] fellAt;

    public CreepRoofCollapse(long seed) {
        this.seed = seed;
    }

    /** Ticks of hold the cell at {@code idx} needs: {@link #HOLD_SECONDS} plus its seeded share of the jitter. */
    public int holdTicks(int idx) {
        return Math.round((HOLD_SECONDS + HOLD_JITTER_SECONDS * unit(idx, 0x4F1DL)) * TICKS_PER_SECOND);
    }

    /** Ticks after a neighbour fell before the cell at {@code idx} follows it. */
    public int spreadTicks(int idx) {
        return Math.round((SPREAD_SECONDS + SPREAD_JITTER_SECONDS * unit(idx, 0x5B2EAL)) * TICKS_PER_SECOND);
    }

    /** Once per simulation tick, after the creep has spread. No-op while there is no creep. */
    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % CHECK_TICKS != 0) return;
        look(sim, tick);
    }

    /**
     * Brings down at once every roof standing over living creep, for creep that
     * has been matured at setup ({@link BattleSimulation#matureCreep}): weeks
     * of creep is far past any cell's hold, so a settlement the bloom has held
     * stands open to the sky when the battle starts rather than caving in during
     * its first minute. Returns the roof cells brought down. The battle's own
     * rule then counts them as fallen on its first look, as it does a roof a
     * shell took.
     */
    public static int matureOn(BattleSimulation sim) {
        if ("false".equalsIgnoreCase(System.getProperty(PROPERTY))) return 0;
        CreepField field = sim.creepFieldIfPresent();
        if (field == null || field.isEmpty()) return 0;
        CellTopology topology = sim.getTopology();
        MapEditor editor = sim.mapEditor();
        int width = topology.getWidth(), height = topology.getHeight(), fell = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (topology.getBuildingId(x, y) == 0 || topology.isRoofDestroyed(x, y) || !field.isCreeped(x, y)) continue;
                editor.destroyRoof(x, y);
                fell++;
            }
        }
        return fell;
    }

    /**
     * One look at the battle's creep at {@code tick}, whatever the battle's own
     * clock: for a review that advances the creep field alone. No-op while there
     * is no creep.
     */
    public void look(BattleSimulation sim, int tick) {
        if (!enabled) return;
        CreepField field = sim.creepFieldIfPresent();
        if (field == null || field.isEmpty()) return;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        MapEditor editor = sim.mapEditor();
        check(tick, new Cells() {
            @Override public int width() { return grid.getWidth(); }

            @Override public int height() { return grid.getHeight(); }

            @Override public boolean building(int x, int y) { return topology.getBuildingId(x, y) != 0; }

            @Override public boolean roofGone(int x, int y) { return topology.isRoofDestroyed(x, y); }

            @Override public boolean creeped(int x, int y) { return field.isCreeped(x, y); }

            @Override public boolean crustedWall(int x, int y) {
                if (!topology.isWall(x, y)) return false;
                int idx = y * grid.getWidth() + x;
                return field.coverageAt(idx) >= CRUST && field.vitalityAt(idx) >= CreepField.CREEPED;
            }
        }, editor::destroyRoof);
    }

    /**
     * One look: advances every roofed building cell's hold and brings down the
     * ones the rule lets fall. The building cells are taken from {@code cells}
     * on the first look; buildings gain no cells in a battle.
     */
    public void check(int tick, Cells cells, Collapse collapse) {
        int width = cells.width(), height = cells.height();
        if (candidates == null) index(cells, width, height);
        for (int i = 0; i < candidates.length; i++) {
            int idx = candidates[i];
            if (fellAt[idx] >= 0) continue;
            int x = idx % width, y = idx / width;
            if (cells.roofGone(x, y)) {
                // Fallen to something else (a shell, a breach): the creep spreads from it all the same.
                fellAt[idx] = tick;
                continue;
            }
            if (!cells.creeped(x, y)) {
                held[i] = 0;
                continue;
            }
            held[i] += CHECK_TICKS;
            if (held[i] < holdTicks(idx)) continue;
            if (besideCrust(cells, x, y, width, height) || sinceNeighbourFell(tick, x, y, width, height) >= spreadTicks(idx)) {
                collapse.fall(x, y);
                fellAt[idx] = tick;
            }
        }
    }

    /** Ticks the cell at {@code idx} has held living creep, as of the last look; 0 for a cell that is no building's. */
    public int heldTicks(int idx) {
        if (candidates == null) return 0;
        int i = Arrays.binarySearch(candidates, idx);
        return i < 0 ? 0 : held[i];
    }

    private void index(Cells cells, int width, int height) {
        int count = 0;
        int[] found = new int[64];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!cells.building(x, y)) continue;
                if (count == found.length) found = Arrays.copyOf(found, count * 2);
                found[count++] = y * width + x;
            }
        }
        candidates = Arrays.copyOf(found, count);
        held = new int[count];
        fellAt = new int[width * height];
        Arrays.fill(fellAt, -1);
    }

    private static boolean besideCrust(Cells cells, int x, int y, int width, int height) {
        return (x > 0 && cells.crustedWall(x - 1, y))
                || (x + 1 < width && cells.crustedWall(x + 1, y))
                || (y > 0 && cells.crustedWall(x, y - 1))
                || (y + 1 < height && cells.crustedWall(x, y + 1));
    }

    /** Ticks since the longest-fallen of the four neighbours fell, or -1 when none has. */
    private int sinceNeighbourFell(int tick, int x, int y, int width, int height) {
        int earliest = Integer.MAX_VALUE;
        if (x > 0) earliest = earliestOf(earliest, fellAt[y * width + x - 1]);
        if (x + 1 < width) earliest = earliestOf(earliest, fellAt[y * width + x + 1]);
        if (y > 0) earliest = earliestOf(earliest, fellAt[(y - 1) * width + x]);
        if (y + 1 < height) earliest = earliestOf(earliest, fellAt[(y + 1) * width + x]);
        return earliest == Integer.MAX_VALUE ? -1 : tick - earliest;
    }

    private static int earliestOf(int earliest, int fell) {
        return fell >= 0 ? Math.min(earliest, fell) : earliest;
    }

    /** A seeded share in [0, 1) for one cell and one use. */
    private float unit(int idx, long salt) {
        long h = seed ^ (idx * 0x9E3779B97F4A7C15L) ^ salt;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
