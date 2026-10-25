package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * {@link BlockFiller} for {@link BlockKind#BUILDING_RESIDENTIAL} leaves. Carves
 * domestic room plans on ordinary lots. Medium and large homes separate sleep,
 * cooking/dining and living space; fitting large lots may keep a medium home
 * inside a fenced garden instead. An ordinary lot is always a home, however
 * large: the apartment plan with its common hall belongs to
 * {@link GatedHousingFiller}'s compounds alone.
 *
 * <p>In a terraced or mixed town an ordinary lot is a {@link FrontageRow} of homes along its street instead.
 * Fitting lots also draw from {@link BuildingStamp}'s rotated authored plans.
 * The procedural alternatives are carved by {@link BuildingShellCore}.
 */
public final class BuildingResidentialFiller implements BlockFiller {

    static final BuildingShellCore.BuildingConfig HOME_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR,
            "RESIDENTIAL",
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME,
            BuildingKind.RESIDENTIAL);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_RESIDENTIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        BuildingPlacement.Side front=streetFrontage(leaf,ctx);
        if (leaf.kind == BlockKind.BUILDING_RESIDENTIAL && FrontageRow.place(leaf, ctx, front,
                FrontageRow.forHomes(theme(leaf, ctx)), HOME_CONFIG, streetBehind(leaf, ctx, front))) return;
        if (LotYardTemplate.tryPlace(leaf,ctx,HOME_CONFIG,front)) return;
        if (BuildingStamp.tryPlace(leaf, ctx, BuildingKind.RESIDENTIAL)) return;
        BuildingPlacement placement = new BuildingPlacement(front, true);
        PointOfInterest poi = BuildingShellCore.carve(
                leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, HOME_CONFIG, placement);
        if (poi != null) ctx.pois.add(poi);
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

    /**
     * The zoning theme an ordinary lot stands in, or null where the map is zoned by biome bands (Conquest),
     * whose lots keep the buildings they had.
     */
    static MapDistrictTheme theme(BlockLeaf leaf, GenContext ctx) {
        DistrictMap districts = ctx.get(BspKeys.DISTRICT_MAP);
        if (districts == null || ctx.get(BspKeys.BIOME_MAP) != null) return null;
        return districts.themeAt(leaf.centerX(), leaf.centerY());
    }

    /** Whether at least half of the lot's back edge, opposite {@code front}, is street a rear row can face. */
    static boolean streetBehind(BlockLeaf leaf, GenContext ctx, BuildingPlacement.Side front) {
        boolean[][] roads = ctx.get(BspKeys.ROAD_CELLS);
        if (roads == null) return false;
        BuildingPlacement.Side back = front.opposite();
        int length = back == BuildingPlacement.Side.TOP || back == BuildingPlacement.Side.BOTTOM
                ? leaf.width() - 2 : leaf.height() - 2;
        return 2 * roadExposure(leaf, back, roads) >= length;
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
