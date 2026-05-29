package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;

/**
 * {@link BlockFiller} for {@link BlockKind#BUILDING_INDUSTRIAL} leaves. Carves
 * a hollow warehouse-style shell with the yellow safety-striped floor and
 * scatters cargo props from {@link TileManifest#WAREHOUSE_DOODADS}.
 *
 * <p>Tagged as {@link PointOfInterest.Kind#DEPOT} so mission setups that look
 * for cargo objectives (loot crates, supply runs) anchor here naturally.
 */
public final class BuildingIndustrialFiller implements BlockFiller {

    private static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.STRIPED,
            TileManifest.WAREHOUSE_DOODADS,
            PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE,
            BuildingKind.INDUSTRIAL);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_INDUSTRIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        PointOfInterest poi = BuildingShellCore.carve(leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, CONFIG);
        if (poi != null) ctx.pois.add(poi);
    }
}
