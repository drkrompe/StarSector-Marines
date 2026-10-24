package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Where a player's flamer jet lands: toward the cursor, no further than the flamer's range and inside the map,
 * and short of the first wall on the way. The jet is flat, so it cannot be put down behind a wall; it stops on
 * the last open point along its bearing, by the structural rule direct fire follows (smoke does not stop it).
 */
public final class FlameJet {
    /** How finely the jet's bearing is walked out from the nozzle, in cells. */
    static final float STEP_CELLS = 0.25f;

    private FlameJet() {
    }

    /**
     * The landing point of a jet from {@code (fromX, fromY)} at the cursor {@code (aimX, aimY)}, or null when
     * nothing past the Marine's own cell can be reached (the cursor is on the Marine, or a wall is in the way at once).
     */
    public static float[] point(NavigationGrid grid, float fromX, float fromY, float aimX, float aimY, float range) {
        float[] clamped = FragGrenadeTactics.clampThrow(fromX, fromY, aimX, aimY, range,
                grid.getWidth(), grid.getHeight());
        if (clamped == null) return null;
        float dx = clamped[0] - fromX;
        float dy = clamped[1] - fromY;
        float length = (float) Math.hypot(dx, dy);
        if (length < STEP_CELLS) return null;
        int fromCellX = (int) Math.floor(fromX);
        int fromCellY = (int) Math.floor(fromY);
        // Walk out from the nozzle and stop on the last point before the first wall the jet would meet.
        float[] last = null;
        for (float reach = STEP_CELLS; ; reach = Math.min(length, reach + STEP_CELLS)) {
            float x = fromX + dx / length * reach;
            float y = fromY + dy / length * reach;
            int cellX = (int) Math.floor(x);
            int cellY = (int) Math.floor(y);
            boolean open = cellX == fromCellX && cellY == fromCellY
                    || !grid.blocksStructuralLineOfSight(cellX, cellY) && grid.hasLineOfFire(fromX, fromY, x, y);
            if (!open) return last;
            // A jet that would land in the Marine's own cell would burn the Marine; it needs ground ahead.
            if (cellX != fromCellX || cellY != fromCellY) last = new float[]{x, y};
            if (reach >= length) return last;
        }
    }
}
