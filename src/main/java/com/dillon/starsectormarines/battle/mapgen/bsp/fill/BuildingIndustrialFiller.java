package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.map.Doodad;
import com.dillon.starsectormarines.battle.map.PointOfInterest;
import com.dillon.starsectormarines.battle.map.TileManifest;
import com.dillon.starsectormarines.battle.map.BuildingKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

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
    public void fill(BlockLeaf leaf,
                     NavigationGrid grid,
                     CellTopology topology,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     Random rng) {
        PointOfInterest poi = BuildingShellCore.carve(leaf, grid, topology, doodads, rng, CONFIG);
        if (poi != null) pois.add(poi);
    }
}
