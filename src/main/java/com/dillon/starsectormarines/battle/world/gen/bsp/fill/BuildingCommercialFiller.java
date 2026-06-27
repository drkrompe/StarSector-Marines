package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;

/**
 * {@link BlockFiller} for {@link BlockKind#BUILDING_COMMERCIAL} leaves. Carves
 * a hollow shell with the polished {@link GroundKind#TILE} floor across the
 * whole interior (fl-2 on the road sheet — same uniform-fill model as
 * residential's INDOOR `fl` blanket) and shop-style props (shelves, desks,
 * chests, crates) drawn from the {@code "COMMERCIAL"} doodad pool.
 *
 * <p>No dedicated {@code SHOP} POI kind exists yet, so the building is tagged
 * as {@link PointOfInterest.Kind#RESIDENTIAL} for mission-anchor purposes —
 * the visual identity (TILE floor + shop pool) differentiates from residential.
 */
public final class BuildingCommercialFiller implements BlockFiller {

    private static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE,
            "COMMERCIAL",
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.SHOP,
            BuildingKind.COMMERCIAL);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_COMMERCIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        PointOfInterest poi = BuildingShellCore.carve(leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, CONFIG);
        if (poi != null) ctx.pois.add(poi);
    }
}
