package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.TileManifest.TileFrame;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Filler for {@link BlockKind#LANDING_ZONE} leaves. Open touchdown pad: no
 * perimeter wall, no interior carving — the whole leaf is walkable striped
 * floor with a single LZ_MARKER cell at the center and a sparse ring of
 * inward-pointing arrow decals around the perimeter.
 *
 * <p>Reads as a deliberate clearing inside the city: the striped pad signals
 * "shuttle apron" against the surrounding gray road, and the arrows orient
 * pilots toward the touchdown cell. Spawn-anchor selection in
 * {@code BspCityGenerator} will later prefer LZ_MARKER cells for marine
 * starting positions.
 *
 * <p>No POI emitted — landing zones aren't landmark buildings, they're a
 * spatial role consumed by spawn-anchor logic instead.
 */
public final class LandingZoneFiller implements BlockFiller {

    /**
     * Arrow doodads on the urban-2 sheet. Each cardinal direction's arrow
     * points back toward the leaf center. The catalog names tell us the
     * direction the art points:
     * <ul>
     *   <li>{@code fl-arrow-se} (9, 1) — points SE → place at NW edge so it points inward.</li>
     *   <li>{@code fl-arrow-sw} (10, 1) — points SW → place at NE edge.</li>
     *   <li>{@code fl-arrow-ne} (9, 2) — points NE → place at SW edge.</li>
     *   <li>{@code fl-arrow-nw} (10, 2) — points NW → place at SE edge.</li>
     * </ul>
     * Diagonal arrows are what the sheet provides (no cardinal-only variants),
     * so we place them at corner cells of the inset ring rather than the
     * middle of each edge — they read as runway-corner markers pointing
     * toward the center decal.
     */
    private static final TileFrame ARROW_FROM_NW = new TileFrame(9,  1); // points SE (toward center from NW corner)
    private static final TileFrame ARROW_FROM_NE = new TileFrame(10, 1); // points SW (toward center from NE corner)
    private static final TileFrame ARROW_FROM_SW = new TileFrame(9,  2); // points NE (toward center from SW corner)
    private static final TileFrame ARROW_FROM_SE = new TileFrame(10, 2); // points NW (toward center from SE corner)

    /** Inset from the leaf rect for the arrow corner ring — keeps arrows off the leaf border. */
    private static final int ARROW_INSET = 1;

    @Override
    public BlockKind kind() { return BlockKind.LANDING_ZONE; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        List<PointOfInterest> pois = ctx.pois;
        List<Doodad> doodads = ctx.doodads;
        Random rng = ctx.rng;
        // Whole leaf is a walkable striped pad. Walkability was set true by
        // the orchestrator initial pass; we leave it that way (no perimeter
        // wall) and overwrite the ground kind so the renderer paints striped
        // floor instead of road.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }

        // Center cell: LZ_MARKER. Touchdown decal — spawn-anchor preference
        // will key on this kind later.
        int cx = leaf.centerX();
        int cy = leaf.centerY();
        if (grid.inBounds(cx, cy)) {
            topology.setGroundKind(cx, cy, GroundKind.LZ_MARKER);
        }

        // Corner arrows pointing inward. Skip when the leaf is too small to
        // place a 1-cell inset corner without colliding with the marker — a
        // 3x3 leaf still has 4 distinct inset corners (the 4 non-center cells
        // of the inset ring), so 3 is the practical minimum.
        if (leaf.width() < 3 || leaf.height() < 3) return;

        int nwX = leaf.left  + ARROW_INSET;
        int nwY = leaf.top   + ARROW_INSET;
        int neX = leaf.right - ARROW_INSET;
        int neY = leaf.top   + ARROW_INSET;
        int swX = leaf.left  + ARROW_INSET;
        int swY = leaf.bottom - ARROW_INSET;
        int seX = leaf.right - ARROW_INSET;
        int seY = leaf.bottom - ARROW_INSET;

        placeArrow(grid, doodads, nwX, nwY, cx, cy, ARROW_FROM_NW);
        placeArrow(grid, doodads, neX, neY, cx, cy, ARROW_FROM_NE);
        placeArrow(grid, doodads, swX, swY, cx, cy, ARROW_FROM_SW);
        placeArrow(grid, doodads, seX, seY, cx, cy, ARROW_FROM_SE);
    }

    /**
     * Stamps one arrow doodad if the target cell is walkable, in bounds, and
     * not the center marker. Arrows live on the road sheet (urban-tileset-2),
     * so {@code fromRoadSheet=true}.
     */
    private static void placeArrow(NavigationGrid grid, List<Doodad> doodads,
                                   int x, int y, int centerX, int centerY,
                                   TileFrame tile) {
        if (!grid.inBounds(x, y)) return;
        if (!grid.isWalkable(x, y)) return;
        if (x == centerX && y == centerY) return; // don't overlay the LZ marker
        doodads.add(new Doodad(x, y, tile, true));
    }
}
