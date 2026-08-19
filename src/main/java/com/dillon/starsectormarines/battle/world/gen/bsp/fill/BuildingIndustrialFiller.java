package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;

/**
 * {@link BlockFiller} for {@link BlockKind#BUILDING_INDUSTRIAL} leaves. Carves
 * a yellow safety-striped industrial shell. Lots at least 15x12 cells become
 * frontage-aware factories with production and support rooms; smaller lots
 * retain the compact warehouse treatment.
 *
 * <p>Tagged as {@link PointOfInterest.Kind#DEPOT} so mission setups that look
 * for cargo objectives (loot crates, supply runs) anchor here naturally.
 */
public final class BuildingIndustrialFiller implements BlockFiller {

    static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.STRIPED,
            "WAREHOUSE",
            PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.INDUSTRIAL_FACILITY,
            BuildingKind.INDUSTRIAL,
            null,
            IndustrialPartitionStrategy.DEFAULT);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_INDUSTRIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        BuildingPlacement placement = new BuildingPlacement(frontage(leaf, ctx), true);
        PointOfInterest poi = BuildingShellCore.carve(
                leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, CONFIG, placement);
        if (poi != null) ctx.pois.add(poi);
    }

    private static BuildingPlacement.Side frontage(BlockLeaf leaf, GenContext ctx) {
        if (leaf.height() >= leaf.width()) {
            return leaf.centerY() < ctx.height / 2
                    ? BuildingPlacement.Side.BOTTOM : BuildingPlacement.Side.TOP;
        }
        return leaf.centerX() < ctx.width / 2
                ? BuildingPlacement.Side.RIGHT : BuildingPlacement.Side.LEFT;
    }
}
