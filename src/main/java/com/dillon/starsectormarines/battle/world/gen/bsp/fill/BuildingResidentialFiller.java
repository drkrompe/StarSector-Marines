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
 * {@link BlockFiller} for {@link BlockKind#BUILDING_RESIDENTIAL} leaves. Carves
 * a hollow shell with the beige INDOOR floor and scatters homely props
 * (benches, chests, stools) from {@link TileManifest#RESIDENTIAL_DOODADS}.
 *
 * <p>All the actual carving lives in {@link BuildingShellCore} — this class
 * only wires the per-kind configuration (interior ground, doodad pool, POI
 * kind) and returns the carved POI if any.
 */
public final class BuildingResidentialFiller implements BlockFiller {

    private static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR,
            TileManifest.RESIDENTIAL_DOODADS,
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME,
            BuildingKind.RESIDENTIAL);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_RESIDENTIAL; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        PointOfInterest poi = BuildingShellCore.carve(leaf, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, CONFIG);
        if (poi != null) ctx.pois.add(poi);
    }
}
