package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.ClearanceComponents;
import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.function.BiPredicate;

/**
 * Where a hull should stand to serve a post ({@code allied-command.md}, posts and stands). The post is the mission's
 * strategic intent: a slot in a line, at a depth behind it. The stand is allied command's tactical answer, a cell
 * near the post that serves the same purpose and that the hull can both reach and leave again.
 *
 * <p>A stand is in the hull's own clearance component, so a route can exist, and is ground the hull could set out
 * from whichever way its next order leaves ({@link TurnAwareCorridor#canSetOutFrom}), so arriving there does not
 * strand it: the hulls a lane commander kept re-asking all battle were ones it had parked on cramped ground. Where
 * the post faces a way, a stand keeps the hull on its line: sliding along it costs least, standing deeper behind it
 * more, and a cell ahead of the post toward the enemy is never a stand. Cells a hull was already refused are not
 * offered again.
 */
final class PostStand {

    /** Cells from the post a stand may be, either way along the line or back from it. */
    static final int REACH = 4;
    /** How much more a cell of depth behind the line costs than a cell along it. */
    static final float DEPTH_COST = 4f;
    /** Cells ahead of the post a stand may be before it counts as stepping toward the enemy. */
    private static final float AHEAD_SLACK = .5f;

    private PostStand() {}

    /**
     * The best stand for the post at ({@code postX}, {@code postY}) for a hull standing in cell
     * ({@code fromX}, {@code fromY}), or {@code null} where no cell within {@link #REACH} serves.
     *
     * @param aheadX the post's facing toward the enemy, x; with {@code aheadY} zero for a post that faces no way
     * @param refused cells not to offer, because the hull was refused them
     */
    static int[] choose(NavigationGrid grid, VehicleType type, VehicleClearance clearance,
                        ClearanceComponents components, int fromX, int fromY, int postX, int postY,
                        float aheadX, float aheadY, BiPredicate<Integer, Integer> refused) {
        int own = components == null ? ClearanceComponents.NONE : components.labelAt(fromX, fromY);
        float length = (float) Math.hypot(aheadX, aheadY);
        boolean faces = length > 1e-4f;
        float ax = faces ? aheadX / length : 0f, ay = faces ? aheadY / length : 0f;
        int[] best = null;
        float bestCost = Float.MAX_VALUE;
        for (int dy = -REACH; dy <= REACH; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                float cost;
                if (faces) {
                    float ahead = dx * ax + dy * ay;
                    if (ahead > AHEAD_SLACK) continue;
                    float along = dx * -ay + dy * ax;
                    cost = along * along + DEPTH_COST * ahead * ahead;
                } else {
                    cost = dx * dx + dy * dy;
                }
                if (cost >= bestCost) continue;
                int x = postX + dx, y = postY + dy;
                if (!clearance.isPassable(x, y)) continue;
                if (own != ClearanceComponents.NONE && components.labelAt(x, y) != own) continue;
                if (refused.test(x, y)) continue;
                if (!TurnAwareCorridor.canSetOutFrom(x + .5f, y + .5f, type, grid)) continue;
                best = new int[]{x, y};
                bestCost = cost;
            }
        }
        return best;
    }
}
