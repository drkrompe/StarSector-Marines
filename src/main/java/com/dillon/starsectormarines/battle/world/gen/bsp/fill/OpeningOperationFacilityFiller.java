package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * Authors the dedicated facility for a First Contract operation using the
 * ordinary building shell and a frontage selected by the map plan.
 */
public final class OpeningOperationFacilityFiller {

    public enum Frontage {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    private static final BuildingShellCore.BuildingConfig COMMS_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR,
                    "SKY_PORT",
                    PointOfInterest.Kind.COMMS,
                    BuildingLayouts.LayoutRecipe.COMMAND_CENTER,
                    BuildingKind.CIVIC,
                    new RoomPurpose[]{
                            RoomPurpose.KEEP_THRONE,
                            RoomPurpose.KEEP_INNER,
                            RoomPurpose.KEEP_ENTRY,
                    },
                    TernaryPartitionStrategy.DEFAULT);

    private OpeningOperationFacilityFiller() {}

    public static void fill(PointOfInterest.Kind kind, BlockLeaf footprint,
                            GenContext ctx, Frontage frontage) {
        fill(kind, footprint, ctx, frontage, false);
    }

    /** A story facility may owe both a direct entrance and a service entrance. */
    public static void fill(PointOfInterest.Kind kind, BlockLeaf footprint,
                            GenContext ctx, Frontage frontage, boolean opposedEntrances) {
        BuildingShellCore.BuildingConfig config = switch (kind) {
            case COMMS -> COMMS_CONFIG;
            case DEPOT -> BuildingIndustrialFiller.CONFIG;
            default -> throw new IllegalArgumentException(
                    "opening-operation facility must be COMMS or DEPOT");
        };
        BuildingPlacement placement = new BuildingPlacement(
                toBuildingSide(frontage), opposedEntrances);
        PointOfInterest carved = BuildingShellCore.carve(
                footprint, ctx.grid, ctx.topology, ctx.doodads, ctx.rng,
                config, placement);
        if (carved == null) {
            throw new IllegalStateException(
                    "opening-operation facility footprint is too small to enclose");
        }

        int[] exteriorAnchor = doorwayAnchor(carved, ctx, frontage);
        if (exteriorAnchor == null) {
            throw new IllegalStateException(
                    "opening-operation facility did not open its planned frontage");
        }
        int[] interior = usableInterior(carved, ctx, exteriorAnchor);
        PointOfInterest place = new PointOfInterest(carved.kind,
                carved.left, carved.top, carved.right, carved.bottom,
                exteriorAnchor[0], exteriorAnchor[1],
                interior[0], interior[1]);
        ctx.pois.add(place);
    }

    /** Furniture is applied after the shell chooses its anchor. Publish a
     * reachable floor cell in the finished room, never a desk or partition. */
    private static int[] usableInterior(PointOfInterest place, GenContext ctx, int[] entry) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = place.top + 1; y < place.bottom; y++) {
            for (int x = place.left + 1; x < place.right; x++) {
                int distance = Math.abs(x - place.interiorAnchorX) + Math.abs(y - place.interiorAnchorY);
                if (distance >= bestDistance || !ctx.grid.isWalkable(x, y)) continue;
                if (Paths.isEmpty(GridPathfinder.findPath(ctx.grid, entry[0], entry[1], x, y))) continue;
                best = new int[]{x, y};
                bestDistance = distance;
            }
        }
        if (best == null) throw new IllegalStateException("Opening-operation facility has no reachable interior");
        return best;
    }

    private static BuildingPlacement.Side toBuildingSide(Frontage frontage) {
        if (frontage == null) {
            throw new IllegalArgumentException("opening-operation frontage is required");
        }
        return switch (frontage) {
            case TOP -> BuildingPlacement.Side.TOP;
            case BOTTOM -> BuildingPlacement.Side.BOTTOM;
            case LEFT -> BuildingPlacement.Side.LEFT;
            case RIGHT -> BuildingPlacement.Side.RIGHT;
        };
    }

    private static int[] doorwayAnchor(PointOfInterest place, GenContext ctx,
                                       Frontage frontage) {
        switch (frontage) {
            case TOP:
                for (int x = place.left + 1; x < place.right; x++) {
                    if (ctx.grid.isDoorway(x, place.top)) return new int[]{x, place.top - 1};
                }
                break;
            case BOTTOM:
                for (int x = place.left + 1; x < place.right; x++) {
                    if (ctx.grid.isDoorway(x, place.bottom)) return new int[]{x, place.bottom + 1};
                }
                break;
            case LEFT:
                for (int y = place.top + 1; y < place.bottom; y++) {
                    if (ctx.grid.isDoorway(place.left, y)) return new int[]{place.left - 1, y};
                }
                break;
            case RIGHT:
                for (int y = place.top + 1; y < place.bottom; y++) {
                    if (ctx.grid.isDoorway(place.right, y)) return new int[]{place.right + 1, y};
                }
                break;
        }
        return null;
    }
}
