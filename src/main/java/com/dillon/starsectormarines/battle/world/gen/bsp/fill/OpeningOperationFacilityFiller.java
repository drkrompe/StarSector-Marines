package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

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
        BuildingShellCore.BuildingConfig config = switch (kind) {
            case COMMS -> COMMS_CONFIG;
            case DEPOT -> BuildingIndustrialFiller.CONFIG;
            default -> throw new IllegalArgumentException(
                    "opening-operation facility must be COMMS or DEPOT");
        };
        BuildingPlacement placement = new BuildingPlacement(
                toBuildingSide(frontage), false);
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
        PointOfInterest place = new PointOfInterest(carved.kind,
                carved.left, carved.top, carved.right, carved.bottom,
                exteriorAnchor[0], exteriorAnchor[1],
                carved.interiorAnchorX, carved.interiorAnchorY);
        ctx.pois.add(place);
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
