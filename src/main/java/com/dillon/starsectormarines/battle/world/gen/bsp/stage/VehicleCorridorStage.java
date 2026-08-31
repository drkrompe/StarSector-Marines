package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridor;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridorPlan;

/**
 * Step 2c' (conquest only) â€” reserve the one road a vehicle is guaranteed to
 * be able to drive, before anything is built on top of it.
 *
 * <p>Runs immediately after the road graph so it can state the corridor in
 * terms the graph has already published, and before the fill dispatch so that
 * every stamp in the rest of the pipeline â€” fillers, the fortress ward, the
 * wall, defense posts, perimeter defenders, overwatch towers â€” is downstream
 * of the reservation rather than racing it.
 *
 * <p><b>It widens {@link BspKeys#ROAD_RESERVATION} rather than living beside
 * it.</b> That mask already means "cells a later stamp may not close", and
 * several stampers already consult it; the defect was never that they ignored
 * the reservation but that the reservation was the road graph's centerline â€”
 * one cell â€” so honoring it perfectly still left the city's main street
 * crossing the fortress wall as a footpath. Unioning the band in fixes every
 * existing consumer at once and leaves one meaning for one mask. The corridor
 * is also bound under {@link BspKeys#VEHICLE_CORRIDOR} for the consumers that
 * need its identity rather than its cells: the wall derives a gate where it
 * crosses, and validation asserts a vehicle can drive it end to end.
 */
public final class VehicleCorridorStage implements GenStage {

    /**
     * {@code -Dbattle.mapgen.vehicleCorridor=false} reserves nothing, leaving
     * every later stamp exactly where it was before this stage existed. It is
     * here because the only honest control for a map-gen change is the same
     * seed generated with the change absent, and reaching that by checking out
     * an older commit measures every other difference between the two trees at
     * the same time — which is how a Conquest swing was once credited to a
     * squad behaviour and turned out to be a sibling session's work arriving on
     * a merge.
     */
    public static final String CORRIDOR_PROPERTY = "battle.mapgen.vehicleCorridor";

    private static final boolean CORRIDOR_ENABLED = Boolean.parseBoolean(
            System.getProperty(CORRIDOR_PROPERTY, "true"));

    @Override
    public void run(GenContext ctx) {
        if (!CORRIDOR_ENABLED) return;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        TrunkPlan.Plan plan = ctx.get(BspKeys.TRUNK_PLAN);
        VehicleCorridor corridor = VehicleCorridorPlan.forAxis(plan, axis);
        if (corridor == null) return;

        boolean[][] reservation = ctx.get(BspKeys.ROAD_RESERVATION);
        if (reservation != null) {
            boolean[][] band = corridor.band();
            for (int x = 0; x < ctx.width && x < band.length; x++) {
                for (int y = 0; y < ctx.height && y < band[x].length; y++) {
                    if (band[x][y]) reservation[x][y] = true;
                }
            }
        }
        ctx.put(BspKeys.VEHICLE_CORRIDOR, corridor);
    }
}
