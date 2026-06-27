package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
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
 *
 * <p>Pool-based recipes draw their props from the per-theme doodad pool resolved
 * via {@link GenMappingRegistry}; literal-frame recipes resolve specific-cell
 * doodads from the {@link TileRegistry} by id (shelves, desks).
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
     * the per-theme doodad pool via {@link GenMappingRegistry}; literal-frame
     * recipes resolve specific doodads from the {@link TileRegistry} by id
     * (shelves, desks).
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
                            String doodadPoolId,
                            LayoutRecipe recipe,
                            List<Doodad> doodads,
                            Random rng) {
        int interiorW = br - bl - 1;
        int interiorH = bb - bt - 1;
        if (interiorW < TINY_INTERIOR_DIM || interiorH < TINY_INTERIOR_DIM) {
            sparseScatter(grid, bl, bt, br, bb, doodadPoolId, doodads, rng, /*tiny*/ true);
            return;
        }
        switch (recipe) {
            case HOME:      applyHome(grid, bl, bt, br, bb, doodads, rng); break;
            case SHOP:      applyShop(grid, bl, bt, br, bb, doodads, rng); break;
            case WAREHOUSE: applyWarehouse(grid, bl, bt, br, bb, doodads, rng); break;
            case SHED:
            default:        sparseScatter(grid, bl, bt, br, bb, doodadPoolId, doodads, rng, /*tiny*/ false); break;
        }
    }

    // ---- Recipes ----

    /**
     * Residential. Picks one long wall and runs a chair wall-line along it
     * (every other cell, alternating yellow/green for noise) so it reads
     * as paired seating against the wall, then drops a 1-2 chest cluster
     * free-placed elsewhere — chests give the "storage / dresser" half of
     * a real room so it reads as seating + furniture, not waiting-room
     * overflow.
     */
    private static void applyHome(NavigationGrid grid,
                                  int bl, int bt, int br, int bb,
                                  List<Doodad> doodads,
                                  Random rng) {
        DoodadDef[] chairs = {
                TileRegistry.installed().doodad("doodad.chair-south-yellow"),
                TileRegistry.installed().doodad("doodad.chair-south-green"),
        };
        DoodadDef[] chests = {
                TileRegistry.installed().doodad("doodad.chest-1"),
                TileRegistry.installed().doodad("doodad.chest-2"),
        };
        // Pick the longer pair of walls and run a chair line on one of them.
        boolean wallsAreHorizontal = (br - bl) >= (bb - bt);
        WallSide side = wallsAreHorizontal
                ? (rng.nextBoolean() ? WallSide.N : WallSide.S)
                : (rng.nextBoolean() ? WallSide.E : WallSide.W);
        wallLineMix(grid, bl, bt, br, bb, side, chairs, /*spacing*/ 2, doodads, rng);

        // Chest cluster — 1-2 free-placed, deliberately not chairs so the
        // building has a non-seating prop type.
        int clusterPicks = 1 + rng.nextInt(2);
        for (int i = 0; i < clusterPicks; i++) {
            int[] cell = pickFreeInteriorCell(grid, bl, bt, br, bb, doodads, rng);
            if (cell == null) break;
            doodads.add(new Doodad(cell[0], cell[1], chests[rng.nextInt(chests.length)]));
        }
    }

    /** Commercial shop. Shelves line both long walls (per-cell variant mix across all 4 shelf types — empty/1/2/3 — so stock reads as varied); a desk sits one cell inside the (first found) doorway, facing the room. */
    private static void applyShop(NavigationGrid grid,
                                  int bl, int bt, int br, int bb,
                                  List<Doodad> doodads,
                                  Random rng) {
        DoodadDef[] shelves = {
                TileRegistry.installed().doodad("doodad.shelf-empty"),
                TileRegistry.installed().doodad("doodad.shelf-1"),
                TileRegistry.installed().doodad("doodad.shelf-2"),
                TileRegistry.installed().doodad("doodad.shelf-3"),
        };
        boolean wallsAreHorizontal = (br - bl) >= (bb - bt);
        if (wallsAreHorizontal) {
            wallLineMix(grid, bl, bt, br, bb, WallSide.N, shelves, WALL_LINE_SPACING, doodads, rng);
            wallLineMix(grid, bl, bt, br, bb, WallSide.S, shelves, WALL_LINE_SPACING, doodads, rng);
        } else {
            wallLineMix(grid, bl, bt, br, bb, WallSide.W, shelves, WALL_LINE_SPACING, doodads, rng);
            wallLineMix(grid, bl, bt, br, bb, WallSide.E, shelves, WALL_LINE_SPACING, doodads, rng);
        }

        DoodadDef desk = TileRegistry.installed().doodad("doodad.desk-1");
        counterAtDoorway(grid, bl, bt, br, bb, desk, doodads);
    }

    /** Industrial warehouse. Crates line both long walls (per-cell variant mix between the two crate frames for hand-stacked feel); a desk at one doorway reads as supervisor / parts counter. */
    private static void applyWarehouse(NavigationGrid grid,
                                       int bl, int bt, int br, int bb,
                                       List<Doodad> doodads,
                                       Random rng) {
        DoodadDef[] crates = {
                TileRegistry.installed().doodad("doodad.box"),
                TileRegistry.installed().doodad("doodad.crate"),
        };
        boolean wallsAreHorizontal = (br - bl) >= (bb - bt);
        if (wallsAreHorizontal) {
            wallLineMix(grid, bl, bt, br, bb, WallSide.N, crates, WALL_LINE_SPACING, doodads, rng);
            wallLineMix(grid, bl, bt, br, bb, WallSide.S, crates, WALL_LINE_SPACING, doodads, rng);
        } else {
            wallLineMix(grid, bl, bt, br, bb, WallSide.W, crates, WALL_LINE_SPACING, doodads, rng);
            wallLineMix(grid, bl, bt, br, bb, WallSide.E, crates, WALL_LINE_SPACING, doodads, rng);
        }

        DoodadDef desk = TileRegistry.installed().doodad("doodad.desk-2");
        counterAtDoorway(grid, bl, bt, br, bb, desk, doodads);
    }

    // ---- Primitives ----

    /** Stamps a single prop along the inside-of-{@code side} cells of the building, every {@code spacing} cells, skipping doorway clearance zones and existing doodad cells. */
    private static void wallLine(NavigationGrid grid,
                                 int bl, int bt, int br, int bb,
                                 WallSide side, DoodadDef prop, int spacing,
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

    /** Wall-line variant that picks a random prop from {@code variants} per cell. Same skip rules as {@link #wallLine}; gives a hand-stacked / varied-stock feel when the variants are visually distinct (crates, shelves). */
    private static void wallLineMix(NavigationGrid grid,
                                    int bl, int bt, int br, int bb,
                                    WallSide side, DoodadDef[] variants, int spacing,
                                    List<Doodad> doodads, Random rng) {
        switch (side) {
            case N: {
                int y = bb - 1;
                for (int x = bl + 1; x <= br - 1; x += spacing) {
                    tryStamp(grid, x, y, variants[rng.nextInt(variants.length)], doodads);
                }
                break;
            }
            case S: {
                int y = bt + 1;
                for (int x = bl + 1; x <= br - 1; x += spacing) {
                    tryStamp(grid, x, y, variants[rng.nextInt(variants.length)], doodads);
                }
                break;
            }
            case E: {
                int x = br - 1;
                for (int y = bt + 1; y <= bb - 1; y += spacing) {
                    tryStamp(grid, x, y, variants[rng.nextInt(variants.length)], doodads);
                }
                break;
            }
            case W: {
                int x = bl + 1;
                for (int y = bt + 1; y <= bb - 1; y += spacing) {
                    tryStamp(grid, x, y, variants[rng.nextInt(variants.length)], doodads);
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
                                         DoodadDef prop, List<Doodad> doodads) {
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
    private static void tryStampDirect(int fixed, int along, DoodadDef prop,
                                       List<Doodad> doodads, NavigationGrid grid, boolean onY) {
        int x = onY ? along : fixed;
        int y = onY ? fixed : along;
        tryStamp(grid, x, y, prop, doodads);
    }

    /** Stamps {@code prop} at {@code (x, y)} if the cell is walkable, not a doorway, not too close to one, and not already occupied. */
    private static void tryStamp(NavigationGrid grid, int x, int y,
                                 DoodadDef prop, List<Doodad> doodads) {
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

    /** SHED / fallback scatter — picks 0-1 (TINY) or 1-2 (LARGE fallback) props from the per-theme pool at random interior cells. */
    private static void sparseScatter(NavigationGrid grid,
                                      int bl, int bt, int br, int bb,
                                      String doodadPoolId,
                                      List<Doodad> doodads, Random rng,
                                      boolean tiny) {
        List<DoodadDef> pool = GenMappingRegistry.installed().doodadPool(doodadPoolId);
        if (pool.isEmpty()) return;
        if (tiny && rng.nextFloat() >= TINY_PROP_CHANCE) return;
        int picks = tiny ? 1 : (1 + rng.nextInt(2));
        for (int i = 0; i < picks; i++) {
            int[] cell = pickFreeInteriorCell(grid, bl, bt, br, bb, doodads, rng);
            if (cell == null) break;
            doodads.add(new Doodad(cell[0], cell[1], pool.get(rng.nextInt(pool.size()))));
        }
    }
}
