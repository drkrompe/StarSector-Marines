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
 * {@link BlockFiller} for {@link BlockKind#BUILDING_COMMERCIAL} leaves. Carves
 * a hollow shell with the polished {@link GroundKind#TILE} floor across the
 * whole interior (fl-2 on the road sheet — same uniform-fill model as
 * residential's INDOOR `fl` blanket) and shop-style props (shelves, desks,
 * chests, crates) drawn from {@link #COMMERCIAL_DOODADS}.
 *
 * <p>No dedicated {@code SHOP} POI kind exists yet, so the building is tagged
 * as {@link PointOfInterest.Kind#RESIDENTIAL} for mission-anchor purposes —
 * the visual identity (TILE floor + shop pool) differentiates from residential.
 */
public final class BuildingCommercialFiller implements BlockFiller {

    /**
     * Shop fixtures from rows 2-3 of the urban-1 tileset: shelves, desks,
     * chests, plus a couple of cargo crates. Defined inline rather than in
     * {@link TileManifest} so this filler can iterate on its own pool without
     * touching shared art constants.
     */
    private static final TileManifest.TileFrame[] COMMERCIAL_DOODADS = {
            new TileManifest.TileFrame(5, 3),               // shelf-empty
            new TileManifest.TileFrame(6, 3),               // shelf-1
            new TileManifest.TileFrame(7, 3),               // shelf-2
            new TileManifest.TileFrame(8, 3),               // shelf-3
            new TileManifest.TileFrame(9, 2),               // desk-1
            new TileManifest.TileFrame(9, 3),               // desk-2
            new TileManifest.TileFrame(3, 3),               // chest-1
            new TileManifest.TileFrame(4, 3),               // chest-2
            new TileManifest.TileFrame(8, 1),               // box (crate)
            new TileManifest.TileFrame(9, 1),               // crate
    };

    private static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE,
            COMMERCIAL_DOODADS,
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
