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

    /**
     * Where the wand's nozzle is, in cells from the Marine's centre: ahead along the jet's bearing and out to the
     * right hip. Read off the flamer layout's firing clip (the wand's tip at 0.62 shoulder widths ahead and 0.19
     * right, a shoulder width being 0.6 of a cell, measured on the firing pose at 0.42 and 0.11), so the jet leaves the wand rather than the Marine's chest.
     */
    static final float NOZZLE_FORWARD_CELLS = 0.42f;
    static final float NOZZLE_RIGHT_CELLS = 0.11f;

    private FlameJet() {
    }

    /**
     * The wand's nozzle for a Marine at {@code (bodyX, bodyY)} sending a jet toward {@code (aimX, aimY)}: where
     * the launch, the trail and the first flame start. The body itself when the aim is on the Marine.
     */
    public static float[] nozzle(float bodyX, float bodyY, float aimX, float aimY) {
        float dx = aimX - bodyX;
        float dy = aimY - bodyY;
        float length = (float) Math.hypot(dx, dy);
        if (!(length > 1e-4f)) return new float[]{bodyX, bodyY};
        float ux = dx / length;
        float uy = dy / length;
        // Right of the bearing, the grid's y running north: (uy, -ux).
        return new float[]{bodyX + ux * NOZZLE_FORWARD_CELLS + uy * NOZZLE_RIGHT_CELLS,
                bodyY + uy * NOZZLE_FORWARD_CELLS - ux * NOZZLE_RIGHT_CELLS};
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
