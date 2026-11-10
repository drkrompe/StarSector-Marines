package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Fills a {@link BlockKind#PARK} leaf as a green, open block:
 * <ul>
 *   <li>Every cell walkable — no walls, no doorways.</li>
 *   <li>Whole interior reads as {@link GroundKind#GRASS}.</li>
 *   <li>Optionally a single 1-cell-wide stone path cuts the leaf either
 *       horizontally or vertically (RNG-picked) so squads can flow through
 *       without trampling the grass. Skipped on leaves too small to host a
 *       meaningful path.</li>
 *   <li>At most 1-2 bench doodads (urban-1 (6, 7)) — parks are low-cover
 *       open ground, not seating areas.</li>
 *   <li>No POI emitted.</li>
 * </ul>
 *
 * <p>A park lays lawn without asking the planet; the closing outdoor pass
 * ({@link com.dillon.starsectormarines.battle.world.gen.CultivatedGround})
 * turns it to the world's own cultivated ground where nothing grows.
 *
 * Deterministic — all randomness sourced from the orchestrator-seeded
 * {@link Random} passed in.
 *
 * <p>Where nobody lives in the open ({@code Habitat#enclosed}) the park is laid as a paved service court instead, its
 * edge holding oxygen spheres rather than benches, on the same draws.
 */
public final class ParkFiller implements BlockFiller {

    /** What an enclosed habitat's court is paved with. */
    static final String COURT_PAVING = "colony.concrete";

    /** What stands on an enclosed habitat's court: the oxygen kept outside, where a leak vents to nothing. */
    static final String COURT_PLANT = "doodad.oxygen-sphere";

    /** Minimum width/height to bother cutting a stone path through the park. */
    private static final int MIN_PATH_SIDE = 5;

    @Override
    public BlockKind kind() { return BlockKind.PARK; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        List<Doodad> doodads = ctx.doodads;
        Random rng = ctx.rng;

        // Where nobody lives in the open the park is a paved service court, and what stands on its edge is the life
        // support kept outside in case of a leak rather than benches. The draws are the park's either way, so the
        // town around it is the same town.
        boolean court = HabitatZoning.forProfile(ctx.get(BspKeys.MARKET_PROFILE)).enclosed();
        DoodadDef bench = TileRegistry.installed().doodad(court ? COURT_PLANT : "mess-galley.stool");
        int paving = court ? topology.surfaceIndex(COURT_PAVING) : 0;

        // Carve the leaf as walkable grass, or pave it.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, court ? GroundKind.STONE : GroundKind.GRASS);
                if (court) topology.setSurface(x, y, paving);
            }
        }

        // Optional stone path — single-cell-wide line, orientation RNG'd.
        // Path is purely cosmetic; the cells stay walkable (already are).
        if (leaf.width() >= MIN_PATH_SIDE && leaf.height() >= MIN_PATH_SIDE) {
            if (rng.nextBoolean()) {
                // Horizontal path along an interior row.
                int innerSpan = leaf.height() - 2;
                int pathY = leaf.top + 1 + rng.nextInt(innerSpan);
                for (int x = leaf.left; x <= leaf.right; x++) {
                    topology.setGroundKind(x, pathY, GroundKind.STONE);
                }
            } else {
                // Vertical path along an interior column.
                int innerSpan = leaf.width() - 2;
                int pathX = leaf.left + 1 + rng.nextInt(innerSpan);
                for (int y = leaf.top; y <= leaf.bottom; y++) {
                    topology.setGroundKind(pathX, y, GroundKind.STONE);
                }
            }
        }

        // 1-2 benches, placed on edge cells so the middle of the park stays open.
        int benchCount = 1 + rng.nextInt(2);

        // Tiny leaves: skip doodads entirely rather than crowd them.
        if (leaf.width() < 3 || leaf.height() < 3) return;

        for (int i = 0; i < benchCount; i++) {
            // Pick a perimeter cell of the leaf.
            // Side: 0=top, 1=bottom, 2=left, 3=right.
            int side = rng.nextInt(4);
            int x, y;
            switch (side) {
                case 0: // top
                    x = leaf.left + rng.nextInt(leaf.width());
                    y = leaf.top;
                    break;
                case 1: // bottom
                    x = leaf.left + rng.nextInt(leaf.width());
                    y = leaf.bottom;
                    break;
                case 2: // left
                    x = leaf.left;
                    y = leaf.top + rng.nextInt(leaf.height());
                    break;
                default: // right
                    x = leaf.right;
                    y = leaf.top + rng.nextInt(leaf.height());
                    break;
            }
            if (!court) {
                doodads.add(new Doodad(x, y, bench));
            } else if (bench != null) {
                standPlant(grid, topology, doodads, leaf, x, y, bench);
            }
        }
    }

    /**
     * A court's plant on the edge cell the bench would have taken, pulled inside the leaf to fit its footprint, and
     * only where nothing else already stands: a real body with cover, so it occupies its cells.
     */
    private static void standPlant(NavigationGrid grid, CellTopology topology, List<Doodad> doodads,
                                   BlockLeaf leaf, int x, int y, DoodadDef plant) {
        Doodad prop = new Doodad(x, y, plant);
        int px = Math.min(x, leaf.right - prop.footprintCellsX + 1);
        int py = Math.min(y, leaf.bottom - prop.footprintCellsY + 1);
        if (px < leaf.left || py < leaf.top) return;
        for (int yy = py; yy < py + prop.footprintCellsY; yy++)
            for (int xx = px; xx < px + prop.footprintCellsX; xx++)
                if (!grid.isWalkable(xx, yy) || grid.isDoorway(xx, yy) || topology.isFixture(xx, yy)) return;
        Doodad placed = new Doodad(px, py, plant);
        for (int yy = py; yy < py + placed.footprintCellsY; yy++)
            for (int xx = px; xx < px + placed.footprintCellsX; xx++) {
                grid.setWalkable(xx, yy, false);
                grid.setSeeThrough(xx, yy, true);
                topology.setFixture(xx, yy, true);
            }
        doodads.add(placed);
    }
}
