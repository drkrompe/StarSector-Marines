package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A holder that fires down from higher ground gives itself away: for
 * {@link #SECONDS} after the shot, the ground it fired on can see it back and
 * answer it. Under the skyline rule a holder back from the rim is hidden from
 * below; while exposed, sight and fire from lower ground to its cell are
 * judged from its own height, as if looking down the line it fired along
 * ({@link NavigationGrid#setExposedCells}). The exposure follows the holder if
 * it moves, and a new shot down restarts it.
 *
 * <p>Shots are noted from whatever thread fires them and applied at the top of
 * the next tick in id order, so the exposed cells, and every sight check that
 * reads them, are fixed for the whole tick and repeat on a replay.
 * See {@code elevation-nouns.md}, Sight.
 */
public final class FiringExposure {

    /** How long a shot down keeps the shooter exposed (owner, 2026-10-22). */
    public static final float SECONDS = 3f;

    /** Control-run switch; on by default. */
    public static final String PROPERTY = "battle.elevation.firingReveal";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    private final Map<Long, Boolean> firedDown = new ConcurrentHashMap<>();
    /** Seconds of exposure left, by shooter id; ordered so the published cells repeat. */
    private final TreeMap<Long, Float> remaining = new TreeMap<>();
    private int[] cells = new int[8];
    private int published;

    /**
     * Notes a shot from {@code (fromX, fromY)} at {@code (toX, toY)}; it exposes
     * the shooter only if it was fired down onto lower ground.
     */
    public void noteShot(NavigationGrid grid, long shooter, int fromX, int fromY, int toX, int toY) {
        if (!ENABLED) return;
        ElevationMap elevation = grid.elevation();
        if (elevation == null || !elevation.inBounds(fromX, fromY) || !elevation.inBounds(toX, toY)) return;
        if (elevation.eyeTier(fromX, fromY) > elevation.eyeTier(toX, toY)) firedDown.put(shooter, Boolean.TRUE);
    }

    /** Whether a unit is exposed now, for evidence and scenes. */
    public boolean isExposed(long unit) {
        return remaining.containsKey(unit);
    }

    /** Ages the exposures, takes in this tick's shots, and publishes the exposed cells to the grid. */
    public void tick(float dt, UnitRosterService roster, NavigationGrid grid) {
        if (remaining.isEmpty() && firedDown.isEmpty() && published == 0) return;
        remaining.replaceAll((id, left) -> left - dt);
        remaining.values().removeIf(left -> left <= 0f);
        for (Long id : firedDown.keySet()) remaining.put(id, SECONDS);
        firedDown.clear();
        remaining.keySet().removeIf(id -> !roster.isAliveById(id));
        World world = roster.world();
        int count = 0;
        for (Long id : remaining.keySet()) {
            int x = world.cellX(id), y = world.cellY(id);
            if (!grid.inBounds(x, y)) continue;
            if (count == cells.length) cells = Arrays.copyOf(cells, count * 2);
            cells[count++] = y * grid.getWidth() + x;
        }
        Arrays.sort(cells, 0, count);
        grid.setExposedCells(cells, count);
        published = count;
    }
}
