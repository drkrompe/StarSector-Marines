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
 * {@link BlockFiller} for {@link BlockKind#BUILDING_COMMERCIAL} leaves. Carves
 * a hollow shell that reads as a shop — interior cells mostly INDOOR floor
 * with a few TILE accents for polish, and shop-style props (shelves, desks,
 * chests, crates) drawn from {@link #COMMERCIAL_DOODADS}.
 *
 * <p>No dedicated {@code SHOP} POI kind exists yet, so the building is tagged
 * as {@link PointOfInterest.Kind#RESIDENTIAL} for mission-anchor purposes —
 * the visual identity (shop pool + tile accents) is enough to differentiate
 * from a true residential block in the renderer pass.
 *
 * <p>Tile accents are decorative only — they don't change walkability, cover,
 * or doodad placement. Doodads still scatter on walkable, non-doorway cells
 * regardless of the underlying ground kind.
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

    /** Chance an interior cell gets re-painted to {@link GroundKind#TILE} as a polished-floor accent. */
    private static final float TILE_ACCENT_CHANCE = 0.20f;

    private static final BuildingShellCore.BuildingConfig CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR,
            COMMERCIAL_DOODADS,
            PointOfInterest.Kind.RESIDENTIAL);

    @Override
    public BlockKind kind() { return BlockKind.BUILDING_COMMERCIAL; }

    @Override
    public void fill(BlockLeaf leaf,
                     NavigationGrid grid,
                     CellTopology topology,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     Random rng) {
        PointOfInterest poi = BuildingShellCore.carve(leaf, grid, topology, doodads, rng, CONFIG);
        if (poi == null) return;
        pois.add(poi);

        // Tile-accent pass — scatter TILE cells across the interior so the
        // shop reads as polished rather than the same beige as residential.
        // Only walkable interior cells get accents; doorways stay INDOOR so
        // the door overlay reads correctly against its neighboring rooms.
        for (int y = poi.top + 1; y <= poi.bottom - 1; y++) {
            for (int x = poi.left + 1; x <= poi.right - 1; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (grid.isDoorway(x, y))   continue;
                if (rng.nextFloat() < TILE_ACCENT_CHANCE) {
                    topology.setGroundKind(x, y, GroundKind.TILE);
                }
            }
        }
    }
}
