package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

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
            PointOfInterest.Kind.RESIDENTIAL);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_RESIDENTIAL; }

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
