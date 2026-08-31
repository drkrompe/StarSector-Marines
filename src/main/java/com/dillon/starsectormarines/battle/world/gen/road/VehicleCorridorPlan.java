package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;

/**
 * Derives a map's {@link VehicleCorridor} from its trunk skeleton.
 *
 * <p>The corridor is not a new road. {@link TrunkPlan} already lays one
 * arterial along each axis before BSP partitions anything, and the one
 * parallel to the traversal axis already spans the map edge to edge — which
 * is why every generated map publishes exactly one road-graph exit on the
 * defender's rear edge. The guaranteed back-of-fortress entry has always been
 * there structurally. What was missing is that nothing downstream was obliged
 * to leave it drivable.
 *
 * <p>So this picks that trunk out and states its drivable band as an authored
 * fact the rest of the pipeline must honor, rather than letting each later
 * stamper rediscover the road from a centerline mask that does not describe
 * its width.
 */
public final class VehicleCorridorPlan {

    private VehicleCorridorPlan() {}

    /**
     * The corridor for {@code axis}, or null when the trunk plan carries no
     * arterial along it.
     *
     * <p>Null is a real outcome rather than a defect to throw on: the corridor
     * is a Conquest-recipe fact, and a map family with no traversal axis has
     * no defender rear edge for a convoy to arrive from. Callers bind nothing
     * and every consumer behaves as it did before the corridor existed.
     */
    public static VehicleCorridor forAxis(TrunkPlan.Plan plan, TraversalAxis axis) {
        if (plan == null || axis == null) return null;
        boolean wantHorizontal = axis == TraversalAxis.WEST_TO_EAST;
        TrunkPlan.TrunkSegment spine = null;
        for (TrunkPlan.TrunkSegment trunk : plan.trunks) {
            if (trunk.horizontal == wantHorizontal) spine = trunk;
        }
        if (spine == null) return null;

        // The band's short axis, and the centred VehicleCorridor.WIDTH cells of
        // it. A SECONDARY trunk is exactly that wide already; a PRIMARY is
        // seven, and the corridor gives back one sidewalk cell per side rather
        // than claiming ground it does not need — a turret on the kerb beside a
        // five-cell road is good dressing, and a turret in the road is a
        // severed route.
        int bandLo = wantHorizontal ? spine.top : spine.left;
        int bandHi = wantHorizontal ? spine.bottom : spine.right;
        int surplus = (bandHi - bandLo + 1) - VehicleCorridor.WIDTH;
        if (surplus > 0) {
            bandLo += surplus / 2;
            bandHi = bandLo + VehicleCorridor.WIDTH - 1;
        }

        boolean[][] band = new boolean[plan.width][plan.height];
        if (wantHorizontal) {
            for (int y = Math.max(0, bandLo); y <= Math.min(plan.height - 1, bandHi); y++) {
                for (int x = 0; x < plan.width; x++) band[x][y] = true;
            }
        } else {
            for (int x = Math.max(0, bandLo); x <= Math.min(plan.width - 1, bandHi); x++) {
                for (int y = 0; y < plan.height; y++) band[x][y] = true;
            }
        }

        // The entry sits on the rear border cell at the band's centre line, so
        // a vehicle staged inward from it is inside the corridor rather than
        // beside it.
        int centre = (bandLo + bandHi) / 2;
        int entryX = wantHorizontal ? plan.width - 1 : centre;
        int entryY = wantHorizontal ? centre : plan.height - 1;
        return new VehicleCorridor(band, axis, entryX, entryY);
    }
}
