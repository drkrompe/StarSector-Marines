package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Doodad layout strategies for {@link BuildingShellCore} carves. Replaces the
 * old "random scatter, capped at 3 props" pass with size- and recipe-driven
 * arrangements:
 *
 * <ul>
 *   <li><b>TINY</b> (interior {@code <} {@link #TINY_INTERIOR_DIM} on either
 *       axis — i.e., a 5×5 or smaller building) — sparse scatter from the
 *       per-type pool, 0-1 props. Reads as a shed / utility shack.</li>
 *   <li><b>LARGE</b> (everything else) — composes layout primitives
 *       ({@link #wallLine}, {@link #counterAtDoorway}) into per-type recipes.
 *       Density scales with the building's interior size; the result reads
 *       as a coherent room (warehouse with crate rows, shop with shelves +
 *       counter, etc.) rather than empty space with a stray chair.</li>
 * </ul>
 *
 * <p>Doodads are visual + cover hints; they don't block navigation, so dense
 * rows of crates won't seal off a corridor. Walkability is preserved by
 * skipping cells that are themselves doorways and the cell directly in front
 * of each doorway (so a marine entering doesn't materialize on top of a
 * shelf).
 */
final class BuildingLayouts {

    /** Below this interior dimension (on either axis), apply the TINY/shed fallback regardless of recipe. */
    static final int TINY_INTERIOR_DIM = 4;

    /** Chance a TINY building gets at least one prop placed. Reads as "occasionally lived-in shed." */
    private static final float TINY_PROP_CHANCE = 0.6f;

    /** Cells of clearance maintained in front of each doorway. Props within this distance of a doorway are skipped so entering is never blocked visually. */
    private static final int DOORWAY_CLEARANCE = 1;

    /** Cell spacing between props on a wall-line. 1 = stamp every cell; 2 = every other cell; etc. */
    private static final int WALL_LINE_SPACING = 1;

    private BuildingLayouts() {}

    /** Side of a building's perimeter, math y-up — N is the high-y wall, S is the low-y wall. */
    enum WallSide { N, S, E, W }

    /**
     * Per-type layout strategy. Each value composes primitives to give the
     * building a distinctive read. Pool-based recipes draw their props from
     * the per-type doodad pool; literal-frame recipes reach into
     * {@link TileManifest} directly for specific cells (shelves, desks).
     */
    enum LayoutRecipe {
        /** TINY/fallback. Sparse scatter from the per-type pool. */
        SHED,
        /** Residential. Short bench wall-line on one long wall + a 2-prop cluster from the pool. */
        HOME,
        /** Commercial shop. Shelves on both long walls + a counter-style desk just inside a doorway. */
        SHOP,
        /** Industrial warehouse. Crates on both long walls + a desk at one doorway. Reads as cargo bay. */
        WAREHOUSE,
    }

    // ---- Public API ----

    /**
     * Top-level dispatcher. Routes TINY buildings to {@link #sparseScatter}
     * regardless of recipe, and LARGE buildings through the per-recipe path.
     * The leaf's perimeter walls have already been stamped; this only touches
     * walkable interior cells.
     */
    static void applyLayout(NavigationGrid grid,
                            int bl, int bt, int br, int bb,
                            TileManifest.TileFrame[] pool,
                            LayoutRecipe recipe,
                            List<Doodad> doodads,
                            Random rng) {
        int interiorW = br - bl - 1;
        int interiorH = bb - bt - 1;
        if (interiorW < TINY_INTERIOR_DIM || interiorH < TINY_INTERIOR_DIM) {
            sparseScatter(grid, bl, bt, br, bb, pool, doodads, rng, /*tiny*/ true);
            return;
        }
        switch (recipe) {
            case HOME:      applyHome(grid, bl, bt, br, bb, pool, doodads, rng); break;
            case SHOP:      applyShop(grid, bl, bt, br, bb, doodads, rng); break;
            case WAREHOUSE: applyWarehouse(grid, bl, bt, br, bb, doodads, rng); break;
            case SHED:
            default:        sparseScatter(grid, bl, bt, br, bb, pool, doodads, rng, /*tiny*/ false); break;
        }
    }

    // ---- Recipes ----

    /**
     * Residential. Picks one long wall and runs a short bench wall-line
     * along it (every other cell), then drops a 2-prop cluster from the
     * pool near a corner.
     */
    private static void applyHome(NavigationGrid grid,
                                  int bl, int bt, int br, int bb,
                                  TileManifest.TileFrame[] pool,
                                  List<Doodad> doodads,
                                  Random rng) {
        TileManifest.TileFrame bench = new TileManifest.TileFrame(6, 7); // RESIDENTIAL_DOODADS[0]
        // Pick the longer pair of walls and run a bench line on one of them.
        boolean wallsAreHorizontal = (br - bl) >= (bb - bt);
        WallSide side = wallsAreHorizontal
                ? (rng.nextBoolean() ? WallSide.N : WallSide.S)
                : (rng.nextBoolean() ? WallSide.E : WallSide.W);
        wallLine(grid, bl, bt, br, bb, side, bench, /*spacing*/ 2, doodads);

        // 1-2 extra cluster props from the per-type pool, free-placed in the
        // opposite half of the building.
        if (pool != null && pool.length > 0) {
            int clusterPicks = 1 + rng.nextInt(2);
            for (int i = 0; i < clusterPicks; i++) {
                int[] cell = pickFreeInteriorCell(grid, bl, bt, br, bb, doodads, rng);
                if (cell == null) break;
                TileManifest.TileFrame f = pool[rng.nextInt(pool.length)];
                doodads.add(new Doodad(cell[0], cell[1], f));
            }
        }
    }

    /** Commercial shop. Shelves line both long walls; a desk sits one cell inside the (first found) doorway, facing the room. */
    private static void applyShop(NavigationGrid grid,
                                  int bl, int bt, int br, int bb,
                                  List<Doodad> doodads,
                                  Random rng) {
        // Shelves on both long walls — pick the prop deterministically so the
        // line reads as one continuous fixture rather than randomly mixed shelves.
        TileManifest.TileFrame shelf = new TileManifest.TileFrame(6 + rng.nextInt(3), 3); // shelf-1..3
        boolean wallsAreHorizontal = (br - bl) >= (bb - bt);
        if (wallsAreHorizontal) {
            wallLine(grid, bl, bt, br, bb, WallSide.N, shelf, WALL_LINE_SPACING, doodads);
            wallLine(grid, bl, bt, br, bb, WallSide.S, shelf, WALL_LINE_SPACING, doodads);
        } else {
            wallLine(grid, bl, bt, br, bb, WallSide.W, shelf, WALL_LINE_SPACING, doodads);
            wallLine(grid, bl, bt, br, bb, WallSide.E, shelf, WALL_LINE_SPACING, doodads);
        }

        TileManifest.TileFrame desk = new TileManifest.TileFrame(9, 2); // desk-1
        counterAtDoorway(grid, bl, bt, br, bb, desk, doodads);
    }

    /** Industrial warehouse. Crates line both long walls; a desk at one doorway reads as supervisor / parts counter. */
    private static void applyWarehouse(NavigationGrid grid,
                                       int bl, int bt, int br, int bb,
                                       List<Doodad> doodads,
                                       Random rng) {
        // Mix crate variants per cell for warehouse noise — repeating "all
        // identical crates" reads too sterile; alternating between (8, 1) and
        // (9, 1) gives a hand-stacked feel.
        boolean wallsAreHorizontal = (br - bl) >= (bb - bt);
        if (wallsAreHorizontal) {
            crateLine(grid, bl, bt, br, bb, WallSide.N, doodads, rng);
            crateLine(grid, bl, bt, br, bb, WallSide.S, doodads, rng);
        } else {
            crateLine(grid, bl, bt, br, bb, WallSide.W, doodads, rng);
            crateLine(grid, bl, bt, br, bb, WallSide.E, doodads, rng);
        }

        TileManifest.TileFrame desk = new TileManifest.TileFrame(9, 3); // desk-2
        counterAtDoorway(grid, bl, bt, br, bb, desk, doodads);
    }

    // ---- Primitives ----

    /** Stamps a single prop along the inside-of-{@code side} cells of the building, every {@code spacing} cells, skipping doorway clearance zones and existing doodad cells. */
    private static void wallLine(NavigationGrid grid,
                                 int bl, int bt, int br, int bb,
                                 WallSide side, TileManifest.TileFrame prop, int spacing,
                                 List<Doodad> doodads) {
        switch (side) {
            case N: { // inside of north wall — cells at y = bb - 1
                int y = bb - 1;
                for (int x = bl + 1; x <= br - 1; x += spacing) {
                    tryStamp(grid, x, y, prop, doodads);
                }
                break;
            }
            case S: { // inside of south wall — cells at y = bt + 1
                int y = bt + 1;
                for (int x = bl + 1; x <= br - 1; x += spacing) {
                    tryStamp(grid, x, y, prop, doodads);
                }
                break;
            }
            case E: { // inside of east wall — cells at x = br - 1
                int x = br - 1;
                for (int y = bt + 1; y <= bb - 1; y += spacing) {
                    tryStamp(grid, x, y, prop, doodads);
                }
                break;
            }
            case W: { // inside of west wall — cells at x = bl + 1
                int x = bl + 1;
                for (int y = bt + 1; y <= bb - 1; y += spacing) {
                    tryStamp(grid, x, y, prop, doodads);
                }
                break;
            }
        }
    }

    /** Wall-line variant that picks between (8, 1) and (9, 1) crate art per cell for noise. Same skip rules as {@link #wallLine}. */
    private static void crateLine(NavigationGrid grid,
                                  int bl, int bt, int br, int bb,
                                  WallSide side, List<Doodad> doodads, Random rng) {
        TileManifest.TileFrame[] crates = {
                new TileManifest.TileFrame(8, 1),
                new TileManifest.TileFrame(9, 1),
        };
        switch (side) {
            case N: {
                int y = bb - 1;
                for (int x = bl + 1; x <= br - 1; x += WALL_LINE_SPACING) {
                    tryStamp(grid, x, y, crates[rng.nextInt(crates.length)], doodads);
                }
                break;
            }
            case S: {
                int y = bt + 1;
                for (int x = bl + 1; x <= br - 1; x += WALL_LINE_SPACING) {
                    tryStamp(grid, x, y, crates[rng.nextInt(crates.length)], doodads);
                }
                break;
            }
            case E: {
                int x = br - 1;
                for (int y = bt + 1; y <= bb - 1; y += WALL_LINE_SPACING) {
                    tryStamp(grid, x, y, crates[rng.nextInt(crates.length)], doodads);
                }
                break;
            }
            case W: {
                int x = bl + 1;
                for (int y = bt + 1; y <= bb - 1; y += WALL_LINE_SPACING) {
                    tryStamp(grid, x, y, crates[rng.nextInt(crates.length)], doodads);
                }
                break;
            }
        }
    }

    /**
     * Finds the first doorway on the building perimeter and stamps {@code prop}
     * one cell inside, on the interior side. The doorway-clearance rule in
     * {@link #tryStamp} ensures the prop itself doesn't sit on the doorway
     * cell. Returns silently if no doorway is found (e.g., a sealed cell
     * shouldn't happen for a real building, but the guard keeps the contract
     * safe).
     */
    private static void counterAtDoorway(NavigationGrid grid,
                                         int bl, int bt, int br, int bb,
                                         TileManifest.TileFrame prop, List<Doodad> doodads) {
        // Scan the perimeter for the first doorway cell, then place the prop
        // 2 cells inward (1 cell of clearance + the cell to stand on for the
        // doorway, then the prop). Two cells in keeps the entry sightline
        // open while still reading as "right by the door."
        // North row
        for (int x = bl + 1; x <= br - 1; x++) {
            if (grid.isDoorway(x, bb)) { tryStampDirect(bb - 2, x, prop, doodads, grid, /*onY*/ true); return; }
        }
        // South row
        for (int x = bl + 1; x <= br - 1; x++) {
            if (grid.isDoorway(x, bt)) { tryStampDirect(bt + 2, x, prop, doodads, grid, /*onY*/ true); return; }
        }
        // East column
        for (int y = bt + 1; y <= bb - 1; y++) {
            if (grid.isDoorway(br, y)) { tryStampDirect(br - 2, y, prop, doodads, grid, /*onY*/ false); return; }
        }
        // West column
        for (int y = bt + 1; y <= bb - 1; y++) {
            if (grid.isDoorway(bl, y)) { tryStampDirect(bl + 2, y, prop, doodads, grid, /*onY*/ false); return; }
        }
    }

    /**
     * Places {@code prop} at the given fixed-axis coord. For {@code onY=true},
     * {@code fixed} is the y coord and {@code along} is the x coord. For
     * {@code onY=false}, {@code fixed} is the x coord and {@code along} is
     * the y coord. Skip rules from {@link #tryStamp} still apply.
     */
    private static void tryStampDirect(int fixed, int along, TileManifest.TileFrame prop,
                                       List<Doodad> doodads, NavigationGrid grid, boolean onY) {
        int x = onY ? along : fixed;
        int y = onY ? fixed : along;
        tryStamp(grid, x, y, prop, doodads);
    }

    /** Stamps {@code prop} at {@code (x, y)} if the cell is walkable, not a doorway, not too close to one, and not already occupied. */
    private static void tryStamp(NavigationGrid grid, int x, int y,
                                 TileManifest.TileFrame prop, List<Doodad> doodads) {
        if (!grid.inBounds(x, y)) return;
        if (!grid.isWalkable(x, y)) return;
        if (grid.isDoorway(x, y)) return;
        if (isNearDoorway(grid, x, y)) return;
        if (isOccupied(x, y, doodads)) return;
        doodads.add(new Doodad(x, y, prop));
    }

    /** True if {@code (x, y)} is within {@link #DOORWAY_CLEARANCE} cells of any doorway. Used to keep a clear approach path through each entrance. */
    private static boolean isNearDoorway(NavigationGrid grid, int x, int y) {
        for (int dy = -DOORWAY_CLEARANCE; dy <= DOORWAY_CLEARANCE; dy++) {
            for (int dx = -DOORWAY_CLEARANCE; dx <= DOORWAY_CLEARANCE; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (!grid.inBounds(nx, ny)) continue;
                if (grid.isDoorway(nx, ny)) return true;
            }
        }
        return false;
    }

    /** True if a doodad already exists at {@code (x, y)} — avoids stamping a counter on top of an earlier wall-line prop. */
    private static boolean isOccupied(int x, int y, List<Doodad> doodads) {
        for (Doodad d : doodads) {
            if (d.cellX == x && d.cellY == y) return true;
        }
        return false;
    }

    /** Returns a random walkable, non-doorway, non-occupied interior cell, or {@code null} if no candidate exists. */
    private static int[] pickFreeInteriorCell(NavigationGrid grid,
                                              int bl, int bt, int br, int bb,
                                              List<Doodad> doodads, Random rng) {
        List<int[]> free = new ArrayList<>();
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (grid.isDoorway(x, y)) continue;
                if (isNearDoorway(grid, x, y)) continue;
                if (isOccupied(x, y, doodads)) continue;
                free.add(new int[]{x, y});
            }
        }
        if (free.isEmpty()) return null;
        return free.get(rng.nextInt(free.size()));
    }

    /** SHED / fallback scatter — picks 0-1 (TINY) or 1-2 (LARGE fallback) props from the per-type pool at random interior cells. */
    private static void sparseScatter(NavigationGrid grid,
                                      int bl, int bt, int br, int bb,
                                      TileManifest.TileFrame[] pool,
                                      List<Doodad> doodads, Random rng,
                                      boolean tiny) {
        if (pool == null || pool.length == 0) return;
        if (tiny && rng.nextFloat() >= TINY_PROP_CHANCE) return;
        int picks = tiny ? 1 : (1 + rng.nextInt(2));
        for (int i = 0; i < picks; i++) {
            int[] cell = pickFreeInteriorCell(grid, bl, bt, br, bb, doodads, rng);
            if (cell == null) break;
            doodads.add(new Doodad(cell[0], cell[1], pool[rng.nextInt(pool.length)]));
        }
    }
}
