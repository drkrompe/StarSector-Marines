package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

/**
 * Large civic/office headquarters. The frontage faces the map interior, while
 * an aligned rear service door and purpose-built partition create a readable
 * public-to-secure circulation axis through the building.
 */
public final class BuildingCivicFiller implements BlockFiller {

    static final BuildingShellCore.BuildingConfig CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.TILE,
                    "COMMERCIAL",
                    PointOfInterest.Kind.ADMINISTRATIVE,
                    BuildingLayouts.LayoutRecipe.CIVIC_HEADQUARTERS,
                    BuildingKind.CIVIC,
                    null,
                    CivicPartitionStrategy.DEFAULT);

    @Override
    public BlockKind kind() {
        return BlockKind.BUILDING_CIVIC;
    }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        BuildingPlacement placement = new BuildingPlacement(frontage(leaf, ctx), true);
        PointOfInterest poi = BuildingShellCore.carve(
                leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, CONFIG, placement);
        if (poi != null) ctx.pois.add(poi);
    }

    static BuildingPlacement.Side frontage(BlockLeaf leaf, GenContext ctx) {
        if (leaf.height() >= leaf.width()) {
            return leaf.centerY() < ctx.height / 2
                    ? BuildingPlacement.Side.BOTTOM : BuildingPlacement.Side.TOP;
        }
        return leaf.centerX() < ctx.width / 2
                ? BuildingPlacement.Side.RIGHT : BuildingPlacement.Side.LEFT;
    }
}
