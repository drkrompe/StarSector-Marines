package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;

/**
 * {@link BlockFiller} for {@link BlockKind#BUILDING_COMMERCIAL} leaves. Carves
 * rotated authored shops or procedural shells, with warm masonry or ceramic
 * materials and shop-style props (shelves, desks, chests and crates).
 *
 * <p>No dedicated {@code SHOP} POI kind exists yet, so the building is tagged
 * as {@link PointOfInterest.Kind#RESIDENTIAL} for mission-anchor purposes —
 * the materials and shop furnishings differentiate them from residential.
 */
public final class BuildingCommercialFiller implements BlockFiller {

    private static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE,
            "COMMERCIAL",
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.SHOP,
            BuildingKind.COMMERCIAL,
            new RoomPurpose[]{RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM},
            CommercialPartitionStrategy.DEFAULT);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_COMMERCIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        if (BuildingStamp.tryPlace(leaf, ctx, BuildingKind.COMMERCIAL)) return;
        PointOfInterest poi = BuildingShellCore.carve(leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, CONFIG);
        if (poi != null) ctx.pois.add(poi);
    }
}
