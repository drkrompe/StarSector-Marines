package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * {@link BlockFiller} for {@link BlockKind#BUILDING_RESIDENTIAL} leaves. Carves
 * a compact home on ordinary lots. Lots large enough for infantry-scale
 * circulation instead reuse the apartment plan: street-facing lobby, clear
 * two-cell hall, four private rooms, opposed rear exit, and firing windows.
 *
 * <p>All the actual carving lives in {@link BuildingShellCore} — this class
 * only wires the per-kind configuration (interior ground, doodad pool, POI
 * kind) and returns the carved POI if any.
 */
public final class BuildingResidentialFiller implements BlockFiller {

    private static final BuildingShellCore.BuildingConfig HOME_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR,
            "RESIDENTIAL",
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME,
            BuildingKind.RESIDENTIAL);

    static final BuildingShellCore.BuildingConfig APARTMENT_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR,
                    "RESIDENTIAL",
                    PointOfInterest.Kind.RESIDENTIAL,
                    BuildingLayouts.LayoutRecipe.APARTMENT_BLOCK,
                    BuildingKind.RESIDENTIAL,
                    null,
                    ResidentialPartitionStrategy.DEFAULT);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_RESIDENTIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        boolean apartment = qualifiesForApartment(leaf);
        BuildingShellCore.BuildingConfig config = apartment ? APARTMENT_CONFIG : HOME_CONFIG;
        BuildingPlacement placement = apartment
                ? new BuildingPlacement(streetFrontage(leaf, ctx), true)
                : BuildingPlacement.DEFAULT;
        PointOfInterest poi = BuildingShellCore.carve(
                leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, config, placement);
        if (poi != null) ctx.pois.add(poi);
    }

    static boolean qualifiesForApartment(BlockLeaf leaf) {
        return Math.max(leaf.width(), leaf.height()) >= ResidentialPartitionStrategy.MIN_LONG_DIM
                && Math.min(leaf.width(), leaf.height()) >= ResidentialPartitionStrategy.MIN_SHORT_DIM;
    }

    /** Selects the facade with the strongest direct exposure to the BSP road mask. */
    static BuildingPlacement.Side streetFrontage(BlockLeaf leaf, GenContext ctx) {
        boolean[][] roads = ctx.get(BspKeys.ROAD_CELLS);
        BuildingPlacement.Side fallback = inwardFrontage(leaf, ctx);
        if (roads == null) return fallback;

        BuildingPlacement.Side best = fallback;
        int bestScore = -1;
        for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
            int score = roadExposure(leaf, side, roads);
            if (score > bestScore || (score == bestScore && side == fallback)) {
                best = side;
                bestScore = score;
            }
        }
        return best;
    }

    private static int roadExposure(BlockLeaf leaf, BuildingPlacement.Side side,
                                    boolean[][] roads) {
        boolean horizontal = side == BuildingPlacement.Side.TOP
                || side == BuildingPlacement.Side.BOTTOM;
        int min = horizontal ? leaf.left + 1 : leaf.top + 1;
        int max = horizontal ? leaf.right - 1 : leaf.bottom - 1;
        int score = 0;
        for (int along = min; along <= max; along++) {
            int x = side == BuildingPlacement.Side.LEFT ? leaf.left - 1
                    : side == BuildingPlacement.Side.RIGHT ? leaf.right + 1 : along;
            int y = side == BuildingPlacement.Side.TOP ? leaf.top - 1
                    : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom + 1 : along;
            if (x >= 0 && x < roads.length && y >= 0 && y < roads[0].length
                    && roads[x][y]) score++;
        }
        return score;
    }

    /** Tie/fallback faces away from the nearest map edge and toward the city interior. */
    private static BuildingPlacement.Side inwardFrontage(BlockLeaf leaf, GenContext ctx) {
        if (leaf.height() >= leaf.width()) {
            return leaf.centerY() < ctx.height / 2
                    ? BuildingPlacement.Side.BOTTOM : BuildingPlacement.Side.TOP;
        }
        return leaf.centerX() < ctx.width / 2
                ? BuildingPlacement.Side.RIGHT : BuildingPlacement.Side.LEFT;
    }
}
