package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef.WallSide;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;

import java.util.ArrayDeque;
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
 * <p>Most doodads remain visual + cover hints. Tactical shops are the
 * deliberate exception: shelf runs stamp {@link CellTopology.Tag#FIXTURE}
 * footprints that block movement but remain see-through and render as props.
 * Their two-cell aisles are sized around the 0.3-cell infantry radius.
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

    /**
     * Width of tactical-store aisles. Two cells comfortably admit two
     * 0.3-radius infantry bodies abreast (1.2 cells combined diameter), while
     * a one-cell passage remains a deliberate single-file choke.
     */
    static final int TACTICAL_AISLE_WIDTH = 2;

    /** Cell spacing between props on a wall-line. 1 = stamp every cell; 2 = every other cell; etc. */
    private static final int WALL_LINE_SPACING = 1;

    /** Cell spacing along the industrial production line — one machine, then a {@link #TACTICAL_AISLE_WIDTH} cross gap. */
    private static final int MACHINERY_SPACING = TACTICAL_AISLE_WIDTH + 1;

    private BuildingLayouts() {}

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
        /** Courtyard-facing apartment block with a lobby, common hall, living rooms, and bedrooms. */
        APARTMENT_BLOCK,
        /** Commercial shop. Shelves on both long walls + a counter-style desk just inside a doorway. */
        SHOP,
        /** Industrial warehouse. Crates on both long walls + a desk at one doorway. Reads as cargo bay. */
        WAREHOUSE,
        /** Large factory with loading, production, control, parts, and service-spine zones. */
        INDUSTRIAL_FACILITY,
        /** Military command post. C2 consoles flank a central tactical planning table. */
        COMMAND_CENTER,
        /** Military barracks. Paired bunk rows leave a broad central fire lane. */
        BARRACKS,
        /** Military armory. Alternating weapon racks and heavy supply stacks form cover lanes. */
        ARMORY,
        /** Military vehicle bay. Service equipment hugs one wall and leaves the bay center open. */
        VEHICLE_BAY,
        /** Civic headquarters. Purpose-labeled offices flank a public-to-service circulation spine. */
        CIVIC_HEADQUARTERS,
        /** Medical clinic. Reception and clinical/support rooms flank a clear circulation spine. */
        MEDICAL_CLINIC,
        /** Narrow dense-block tenement row with a common room and private bedroom. */
        DENSE_TENEMENT,
        /** Narrow dense-block market row with a shop floor and rear stockroom. */
        DENSE_MARKET,
    }

    // ---- Public API ----

    /**
     * Top-level dispatcher. Routes TINY buildings to {@link #sparseScatter}
     * regardless of recipe, and LARGE buildings through the per-recipe path.
     * The leaf's perimeter walls have already been stamped; this only touches
     * walkable interior cells.
     */
    static void applyLayout(NavigationGrid grid,
                            CellTopology topology,
                            PartitionLayout partition,
                            int bl, int bt, int br, int bb,
                            String doodadPoolId,
                            LayoutRecipe recipe,
                            List<Doodad> doodads,
                            Random rng) {
        int interiorW = br - bl - 1;
        int interiorH = bb - bt - 1;
        boolean denseRowRecipe = recipe == LayoutRecipe.DENSE_TENEMENT
                || recipe == LayoutRecipe.DENSE_MARKET;
        if (!denseRowRecipe && (interiorW < TINY_INTERIOR_DIM || interiorH < TINY_INTERIOR_DIM)) {
            sparseScatter(grid, bl, bt, br, bb, doodadPoolId, doodads, rng, /*tiny*/ true);
            return;
        }
        switch (recipe) {
            case HOME:      applyHome(grid, bl, bt, br, bb, doodads, rng); break;
            case APARTMENT_BLOCK: applyApartmentBlock(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
            case SHOP:      applyShop(grid, topology, partition, bl, bt, br, bb, doodads, rng); break;
            case WAREHOUSE: applyWarehouse(grid, bl, bt, br, bb, doodads, rng); break;
            case INDUSTRIAL_FACILITY: applyIndustrialFacility(
                    grid, topology, partition, bl, bt, br, bb, doodads, rng); break;
            case COMMAND_CENTER: applyCommandCenter(grid, topology, bl, bt, br, bb, doodads, rng); break;
            case BARRACKS:       applyBarracks(grid, topology, bl, bt, br, bb, doodads); break;
            case ARMORY:         applyArmory(grid, topology, bl, bt, br, bb, doodads, rng); break;
            case VEHICLE_BAY:    applyVehicleBay(grid, topology, bl, bt, br, bb, doodads, rng); break;
            case CIVIC_HEADQUARTERS: applyCivicHeadquarters(
                    grid, topology, partition, bl, bt, br, bb, doodads, rng); break;
            case MEDICAL_CLINIC: applyMedicalClinic(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
            case DENSE_TENEMENT: applyDenseTenement(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
            case DENSE_MARKET: applyDenseMarket(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
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
            int[] cell = pickFreeInteriorCell(
                    grid, bl, bt, br, bb, chests[0], doodads, rng);
            if (cell == null) break;
            doodads.add(new Doodad(cell[0], cell[1], chests[rng.nextInt(chests.length)]));
        }
    }

    /**
     * Furnishes each purpose-labeled apartment room with one tactical fixture.
     * The common two-cell hall remains completely clear; beds and sofas are low
     * enough to preserve sightlines while still shaping movement and cover.
     */
    private static void applyApartmentBlock(NavigationGrid grid,
                                            CellTopology topology,
                                            int bl, int bt, int br, int bb,
                                            List<Doodad> doodads,
                                            Random rng) {
        if (purposeBounds(topology, bl, bt, br, bb,
                RoomPurpose.RESIDENTIAL_HALL) == null) {
            applyHome(grid, bl, bt, br, bb, doodads, rng);
            return;
        }

        DoodadDef[] beds = {
                TileRegistry.installed().doodad("doodad.residential-bed-head-n"),
                TileRegistry.installed().doodad("doodad.residential-bed-v"),
                TileRegistry.installed().doodad("doodad.residential-bed-head-e"),
                TileRegistry.installed().doodad("doodad.residential-bed-h"),
        };
        DoodadDef[] sofas = {
                TileRegistry.installed().doodad("doodad.residential-sofa-h"),
                TileRegistry.installed().doodad("doodad.residential-sofa-back-s"),
                TileRegistry.installed().doodad("doodad.residential-sofa-back-e"),
                TileRegistry.installed().doodad("doodad.residential-sofa-v"),
        };
        DoodadDef lobbyDesk = TileRegistry.installed().doodad("doodad.desk-1");

        stampOneOrientedFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.BEDROOM, beds, doodads, rng, true);
        stampOneOrientedFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.APARTMENT_LIVING, sofas, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.APARTMENT_LOBBY, lobbyDesk, doodads, rng, true);
    }

    /**
     * Commercial shop. Purpose-built plans get real shelf footprints,
     * two-cell longitudinal lanes, a two-cell cross aisle, and a separate
     * stockroom. Smaller stores retain the lighter perimeter treatment.
     */
    private static void applyShop(NavigationGrid grid,
                                  CellTopology topology,
                                  PartitionLayout partition,
                                  int bl, int bt, int br, int bb,
                                  List<Doodad> doodads,
                                  Random rng) {
        DoodadDef[] shelves = {
                TileRegistry.installed().doodad("doodad.shelf-empty"),
                TileRegistry.installed().doodad("doodad.shelf-1"),
                TileRegistry.installed().doodad("doodad.shelf-2"),
                TileRegistry.installed().doodad("doodad.shelf-3"),
        };
        if (partition.tacticalCommercial) {
            applyTacticalShop(grid, topology, partition, bl, bt, br, bb,
                    shelves, doodads, rng);
            return;
        }

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

    /** Furnishes the labeled sales floor as navigational aisles and the stockroom as loose cover. */
    private static void applyTacticalShop(NavigationGrid grid,
                                          CellTopology topology,
                                          PartitionLayout partition,
                                          int bl, int bt, int br, int bb,
                                          DoodadDef[] shelves,
                                          List<Doodad> doodads,
                                          Random rng) {
        int[] sales = purposeBounds(topology, bl, bt, br, bb, RoomPurpose.SHOP_FLOOR);
        if (sales == null) return;

        // The partition is perpendicular to the storefront/service entrances.
        // Run racks front-to-back so their neighboring aisles preserve long
        // firing lanes; break every run across the middle for a flank route.
        boolean runsAlongX = partition.orient == PartitionLayout.Orient.VERTICAL;
        int alongMin  = runsAlongX ? sales[0] : sales[1];
        int alongMax  = runsAlongX ? sales[2] : sales[3];
        int acrossMin = runsAlongX ? sales[1] : sales[0];
        int acrossMax = runsAlongX ? sales[3] : sales[2];
        int crossA = (alongMin + alongMax - 1) / 2;
        int crossB = Math.min(alongMax, crossA + 1);
        int laneCenterLo = (acrossMin + acrossMax - 1) / 2;
        int laneCenterHi = Math.min(acrossMax, laneCenterLo + 1);

        int fixturesBefore = countFixtures(topology, sales);
        for (int across = acrossMin + TACTICAL_AISLE_WIDTH;
             across <= acrossMax - TACTICAL_AISLE_WIDTH;
             across += TACTICAL_AISLE_WIDTH + 1) {
            // Reserve a central 2-cell maneuver/fire lane. Other rack rows are
            // spaced by two open cells (diameter 0.6 infantry can pass abreast).
            if (across >= laneCenterLo && across <= laneCenterHi) continue;
            for (int along = alongMin + TACTICAL_AISLE_WIDTH;
                 along <= alongMax - TACTICAL_AISLE_WIDTH;
                 along++) {
                if (along == crossA || along == crossB) continue;
                int x = runsAlongX ? along : across;
                int y = runsAlongX ? across : along;
                stampShelfFixture(grid, topology, x, y,
                        shelves[rng.nextInt(shelves.length)], doodads);
            }
        }
        if (countFixtures(topology, sales) - fixturesBefore < 3) {
            stampShelfIsland(grid, topology, partition, sales, shelves, doodads, rng);
        }

        DoodadDef desk = TileRegistry.installed().doodad("doodad.desk-1");
        counterAtShopEntrance(grid, topology, bl, bt, br, bb, desk, doodads);

        DoodadDef[] crates = {
                TileRegistry.installed().doodad("doodad.box"),
                TileRegistry.installed().doodad("doodad.crate"),
        };
        int stockProps = 2 + rng.nextInt(3);
        for (int i = 0; i < stockProps; i++) {
            int[] cell = pickFreePurposeCell(grid, topology, bl, bt, br, bb,
                    RoomPurpose.STOCKROOM, crates[0], doodads, rng);
            if (cell == null) break;
            doodads.add(new Doodad(cell[0], cell[1], crates[rng.nextInt(crates.length)]));
        }
    }

    private static int[] purposeBounds(CellTopology topology,
                                       int bl, int bt, int br, int bb,
                                       RoomPurpose purpose) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                if (topology.getRoomPurpose(x, y) != purpose) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return minX == Integer.MAX_VALUE ? null : new int[]{minX, minY, maxX, maxY};
    }

    private static int countFixtures(CellTopology topology, int[] bounds) {
        int count = 0;
        for (int y = bounds[1]; y <= bounds[3]; y++) {
            for (int x = bounds[0]; x <= bounds[2]; x++) {
                if (topology.isFixture(x, y)) count++;
            }
        }
        return count;
    }

    /**
     * Medium-store fallback: a three-cell display island centered in the room,
     * perpendicular to the storefront-to-stockroom axis. The minimum 5x7 sales
     * floor leaves two cells around all four sides, producing two flanks and
     * cross routes even when the lot cannot support repeated shelf runs.
     */
    private static void stampShelfIsland(NavigationGrid grid, CellTopology topology,
                                         PartitionLayout partition, int[] sales,
                                         DoodadDef[] shelves, List<Doodad> doodads,
                                         Random rng) {
        int cx = (sales[0] + sales[2]) / 2;
        int cy = (sales[1] + sales[3]) / 2;
        boolean frontBackAlongX = partition.orient == PartitionLayout.Orient.VERTICAL;
        for (int offset = -1; offset <= 1; offset++) {
            int x = frontBackAlongX ? cx : cx + offset;
            int y = frontBackAlongX ? cy + offset : cy;
            stampShelfFixture(grid, topology, x, y,
                    shelves[rng.nextInt(shelves.length)], doodads);
        }
    }

    private static void stampShelfFixture(NavigationGrid grid, CellTopology topology,
                                          int x, int y, DoodadDef shelf,
                                          List<Doodad> doodads) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return;
        if (grid.isDoorway(x, y) || isNearDoorway(grid, x, y)) return;
        if (isOccupied(x, y, doodads)) return;
        if (PlacementGuards.wouldStrandGround(grid, x, y)) return;
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
        topology.setWall(x, y, false);
        topology.setFixture(x, y, true);
        doodads.add(new Doodad(x, y, shelf));
    }

    /** Place a checkout beside the exterior doorway whose inward cell is the sales floor. */
    private static void counterAtShopEntrance(NavigationGrid grid, CellTopology topology,
                                              int bl, int bt, int br, int bb,
                                              DoodadDef desk, List<Doodad> doodads) {
        for (int x = bl + 1; x <= br - 1; x++) {
            if (grid.isDoorway(x, bt)
                    && tryCounterBesideEntrance(grid, topology, x, bt, 0, 1, desk, doodads)) return;
            if (grid.isDoorway(x, bb)
                    && tryCounterBesideEntrance(grid, topology, x, bb, 0, -1, desk, doodads)) return;
        }
        for (int y = bt + 1; y <= bb - 1; y++) {
            if (grid.isDoorway(bl, y)
                    && tryCounterBesideEntrance(grid, topology, bl, y, 1, 0, desk, doodads)) return;
            if (grid.isDoorway(br, y)
                    && tryCounterBesideEntrance(grid, topology, br, y, -1, 0, desk, doodads)) return;
        }
    }

    private static boolean tryCounterBesideEntrance(NavigationGrid grid, CellTopology topology,
                                                     int doorX, int doorY, int inX, int inY,
                                                     DoodadDef desk, List<Doodad> doodads) {
        int insideX = doorX + inX;
        int insideY = doorY + inY;
        if (topology.getRoomPurpose(insideX, insideY) != RoomPurpose.SHOP_FLOOR) return false;
        int baseX = doorX + 2 * inX;
        int baseY = doorY + 2 * inY;
        int sideX = inY;
        int sideY = -inX;
        int before = doodads.size();
        tryStamp(grid, baseX + sideX, baseY + sideY, desk, doodads);
        if (doodads.size() == before) {
            tryStamp(grid, baseX - sideX, baseY - sideY, desk, doodads);
        }
        return true;
    }

    private static int[] pickFreePurposeCell(NavigationGrid grid, CellTopology topology,
                                             int bl, int bt, int br, int bb,
                                             RoomPurpose purpose,
                                             DoodadDef prop,
                                             List<Doodad> doodads, Random rng) {
        List<int[]> free = new ArrayList<>();
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                if (!footprintHasPurpose(topology, x, y, prop, purpose)) continue;
                if (!canPlaceDoodad(grid, x, y, prop, doodads)) continue;
                if (footprintTouchesWindow(grid, topology, x, y, prop)) continue;
                free.add(new int[]{x, y});
            }
        }
        return free.isEmpty() ? null : free.get(rng.nextInt(free.size()));
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

    /**
     * Furnishes a qualifying factory without obstructing its service spine.
     * Opaque machinery hugs the production-side exterior wall at three-cell
     * intervals, creating a broad longitudinal lane plus two-cell cross gaps.
     * Support-room and loading props remain low enough to shoot across.
     */
    private static void applyIndustrialFacility(NavigationGrid grid, CellTopology topology,
                                                 PartitionLayout partition,
                                                 int bl, int bt, int br, int bb,
                                                 List<Doodad> doodads, Random rng) {
        if (!partition.tacticalIndustrial) {
            applyWarehouse(grid, bl, bt, br, bb, doodads, rng);
            return;
        }

        DoodadDef machine = TileRegistry.installed().doodad("doodad.industrial-machine-tool");
        DoodadDef tank = TileRegistry.installed().doodad("doodad.industrial-fluid-tank");
        DoodadDef console = TileRegistry.installed().doodad("doodad.industrial-control-console");
        DoodadDef parts = TileRegistry.installed().doodad("doodad.industrial-crate-stack");
        DoodadDef pallet = TileRegistry.installed().doodad("doodad.industrial-pallet-stack");

        stampIndustrialProductionLine(grid, topology, partition,
                bl, bt, br, bb, new DoodadDef[]{machine, tank}, doodads);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.CONTROL_ROOM, console, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.PARTS_CAGE, parts, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.PARTS_CAGE, parts, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.LOADING_BAY, pallet, doodads, rng, true);
    }

    private static void stampIndustrialProductionLine(NavigationGrid grid,
                                                        CellTopology topology,
                                                        PartitionLayout partition,
                                                        int bl, int bt, int br, int bb,
                                                        DoodadDef[] machinery,
                                                        List<Doodad> doodads) {
        int minX = br;
        int minY = bb;
        int maxX = bl;
        int maxY = bt;
        for (int y = bt + 1; y < bb; y++) {
            for (int x = bl + 1; x < br; x++) {
                if (topology.getRoomPurpose(x, y) != RoomPurpose.PRODUCTION_FLOOR) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) return;

        if (partition.orient == PartitionLayout.Orient.VERTICAL) {
            stampMachineryRun(grid, topology, machinery, doodads, true,
                    maxX < partition.preferredPerimeterDoorAlong ? minX : maxX, minY, maxY);
        } else {
            stampMachineryRun(grid, topology, machinery, doodads, false,
                    maxY < partition.preferredPerimeterDoorAlong ? minY : maxY, minX, maxX);
        }
    }

    /**
     * Lays the machinery along one edge of the production floor at
     * {@link #MACHINERY_SPACING}, which is what leaves the lane its two-cell
     * cross gaps. A slot the no-island guard refuses steps one cell along and
     * the spacing resumes from where the machine actually landed, so a firing
     * aperture in that wall costs the run a cell of position rather than a
     * whole machine.
     */
    private static void stampMachineryRun(NavigationGrid grid, CellTopology topology,
                                          DoodadDef[] machinery, List<Doodad> doodads,
                                          boolean runsVertically, int fixed,
                                          int from, int to) {
        int placed = 0;
        int along = from;
        while (along <= to && placed < 5) {
            DoodadDef prop = machinery[placed % machinery.length];
            int slot = along;
            while (slot <= to && refusedOnlyByNoIslandGuard(grid,
                    runsVertically ? fixed : slot, runsVertically ? slot : fixed,
                    prop, doodads)) {
                slot++;
            }
            if (slot > to) return;
            int before = doodads.size();
            stampFixture(grid, topology,
                    runsVertically ? fixed : slot, runsVertically ? slot : fixed,
                    prop, doodads, false);
            boolean landed = doodads.size() > before;
            if (landed) placed++;
            along = (landed ? slot : along) + MACHINERY_SPACING;
        }
    }

    /**
     * Builds a recognizable command workspace: a central planning table,
     * console banks around the command chamber, a separate operations bank in
     * the inner chamber, and workstation banks in a qualifying entry chamber.
     * Repeated stations hug real walls and leave each room's service aisle
     * connected rather than reading as isolated symbolic boxes.
     */
    private static void applyCommandCenter(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           List<Doodad> doodads, Random rng) {
        DoodadDef console = TileRegistry.installed().doodad("doodad.military-command-console");
        DoodadDef table = TileRegistry.installed().doodad("doodad.military-tactical-table");
        DoodadDef workstation = TileRegistry.installed().doodad("doodad.office-workstation-bank");

        stampPurposeCenterFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.KEEP_THRONE, table, doodads, rng, true);
        stampPurposeFixtureRows(grid, topology, bl, bt, br, bb,
                RoomPurpose.KEEP_THRONE, console, doodads,
                purposeFixtureBudget(topology, bl, bt, br, bb,
                        RoomPurpose.KEEP_THRONE, 2, 4, 5), 2, true, false);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.KEEP_THRONE, rng);
        stampPurposeFixtureRows(grid, topology, bl, bt, br, bb,
                RoomPurpose.KEEP_INNER, console, doodads,
                purposeFixtureBudget(topology, bl, bt, br, bb,
                        RoomPurpose.KEEP_INNER, 2, 5, 4), 2, true, false);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.KEEP_INNER, rng);
        stampPurposeFixtureRows(grid, topology, bl, bt, br, bb,
                RoomPurpose.KEEP_ENTRY, workstation, doodads,
                purposeFixtureBudget(topology, bl, bt, br, bb,
                        RoomPurpose.KEEP_ENTRY, 2, 4, 5), 2, true, false);
    }

    /** Two vertical bunk rows leave at least a two-cell central lane for 0.3-cell-radius marines. */
    private static void applyBarracks(NavigationGrid grid, CellTopology topology,
                                      int bl, int bt, int br, int bb,
                                      List<Doodad> doodads) {
        DoodadDef bunk = TileRegistry.installed().doodad("doodad.military-bunk");
        fixtureWallLine(grid, topology, bl, bt, br, bb, WallSide.W, bunk, 2, doodads);
        fixtureWallLine(grid, topology, bl, bt, br, bb, WallSide.E, bunk, 2, doodads);
    }

    /** Alternating rack/crate fixtures create firing lanes and meaningful heavy cover in the armory. */
    private static void applyArmory(NavigationGrid grid, CellTopology topology,
                                    int bl, int bt, int br, int bb,
                                    List<Doodad> doodads, Random rng) {
        DoodadDef[] stores = {
                TileRegistry.installed().doodad("doodad.shelf-2"),
                TileRegistry.installed().doodad("doodad.industrial-crate-stack"),
        };
        boolean horizontal = (br - bl) >= (bb - bt);
        WallSide first = horizontal ? WallSide.N : WallSide.W;
        WallSide second = horizontal ? WallSide.S : WallSide.E;
        fixtureWallLineMix(grid, topology, bl, bt, br, bb, first, stores, 2, doodads, rng);
        fixtureWallLineMix(grid, topology, bl, bt, br, bb, second, stores, 2, doodads, rng);
        counterAtDoorway(grid, bl, bt, br, bb,
                TileRegistry.installed().doodad("doodad.desk-2"), doodads);
    }

    /** Keeps the vehicle-bay center empty and pushes repair/service cover to the longer wall pair. */
    private static void applyVehicleBay(NavigationGrid grid, CellTopology topology,
                                        int bl, int bt, int br, int bb,
                                        List<Doodad> doodads, Random rng) {
        DoodadDef generator = TileRegistry.installed().doodad("doodad.industrial-generator");
        DoodadDef[] utility = {
                TileRegistry.installed().doodad("doodad.industrial-cable-reel"),
                TileRegistry.installed().doodad("doodad.industrial-pallet-stack"),
        };
        boolean horizontal = (br - bl) >= (bb - bt);
        WallSide serviceWall = horizontal ? WallSide.N : WallSide.W;
        WallSide utilityWall = horizontal ? WallSide.S : WallSide.E;
        fixtureWallLine(grid, topology, bl, bt, br, bb,
                serviceWall, generator, 3, doodads);
        wallLineMix(grid, bl, bt, br, bb, utilityWall, utility, 4, doodads, rng);
    }

    /**
     * Fits the civic plan around its authored public-to-service spine. Twin
     * reception counters and paired office work groups hug room corners away
     * from their fixed doors, the conference table owns the center of its
     * room, and server racks form an opaque wall row around a service aisle.
     * The corridor itself remains entirely clear.
     */
    private static void applyCivicHeadquarters(NavigationGrid grid, CellTopology topology,
                                                PartitionLayout partition,
                                                int bl, int bt, int br, int bb,
                                                List<Doodad> doodads, Random rng) {
        if (!partition.tacticalCivic) return;
        DoodadDef workstation = TileRegistry.installed().doodad("doodad.civic-workstation-bank");
        DoodadDef records = TileRegistry.installed().doodad("doodad.office-records-bank");
        DoodadDef reception = TileRegistry.installed().doodad("doodad.office-reception-counter");
        DoodadDef conference = TileRegistry.installed().doodad("doodad.civic-conference-table");
        DoodadDef serverRack = TileRegistry.installed().doodad("doodad.civic-server-rack");

        stampOneAuthoredFixtureGroupPerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.CIVIC_OFFICE, workstation, records, doodads, true);
        preservePlacementDrawsForPurposeRooms(
                grid, topology, bl, bt, br, bb, RoomPurpose.CIVIC_OFFICE, rng);

        stampOneAuthoredFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.CIVIC_RECEPTION, reception, doodads, true);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.CIVIC_RECEPTION, rng);

        stampRotatablePurposeCenterFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.CONFERENCE_ROOM, conference, doodads, rng, true);
        stampRotatablePurposeFixtureRows(grid, topology, bl, bt, br, bb,
                RoomPurpose.SERVER_ROOM, serverRack, doodads,
                purposeFixtureBudget(topology, bl, bt, br, bb,
                        RoomPurpose.SERVER_ROOM, 3, 6, 4), 2, false, true);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.SERVER_ROOM, rng);
    }

    /**
     * Furnishes the room plan as a clinic while keeping the two-cell medical
     * corridor empty. The ward reuses the cardinal two-cell bed variants so
     * each bed sits against a supporting wall and faces into its room.
     */
    private static void applyMedicalClinic(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           List<Doodad> doodads, Random rng) {
        if (purposeBounds(topology, bl, bt, br, bb,
                RoomPurpose.MEDICAL_CORRIDOR) == null) return;

        DoodadDef receptionDesk = TileRegistry.installed().doodad("doodad.desk-1");
        DoodadDef triageStation = TileRegistry.installed().doodad("doodad.office-workstation-bank");
        DoodadDef treatmentConsole = TileRegistry.installed().doodad("doodad.military-command-console");
        DoodadDef pharmacyShelf = TileRegistry.installed().doodad("doodad.shelf-2");
        DoodadDef[] beds = {
                TileRegistry.installed().doodad("doodad.residential-bed-head-n"),
                TileRegistry.installed().doodad("doodad.residential-bed-v"),
                TileRegistry.installed().doodad("doodad.residential-bed-head-e"),
                TileRegistry.installed().doodad("doodad.residential-bed-h"),
        };

        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.MEDICAL_RECEPTION, receptionDesk, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.TRIAGE, triageStation, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.TREATMENT_ROOM, treatmentConsole, doodads, rng, false);
        stampOneOrientedFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.PATIENT_WARD, beds, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.PHARMACY, pharmacyShelf, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.PHARMACY, pharmacyShelf, doodads, rng, true);
    }

    /** Furnishes both rooms of a narrow tenement without obstructing the shared alley doors. */
    private static void applyDenseTenement(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           List<Doodad> doodads, Random rng) {
        DoodadDef[] beds = {
                TileRegistry.installed().doodad("doodad.residential-bed-head-n"),
                TileRegistry.installed().doodad("doodad.residential-bed-v"),
                TileRegistry.installed().doodad("doodad.residential-bed-head-e"),
                TileRegistry.installed().doodad("doodad.residential-bed-h"),
        };
        DoodadDef[] sofas = {
                TileRegistry.installed().doodad("doodad.residential-sofa-h"),
                TileRegistry.installed().doodad("doodad.residential-sofa-back-s"),
                TileRegistry.installed().doodad("doodad.residential-sofa-back-e"),
                TileRegistry.installed().doodad("doodad.residential-sofa-v"),
        };
        stampOneOrientedFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.BEDROOM, beds, doodads, rng, true);
        stampOneOrientedFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                RoomPurpose.APARTMENT_LIVING, sofas, doodads, rng, true);
        if (!purposeHasFixture(topology, bl, bt, br, bb, RoomPurpose.BEDROOM)) {
            stampPurposeFixture(grid, topology, bl, bt, br, bb,
                    RoomPurpose.BEDROOM,
                    TileRegistry.installed().doodad("doodad.chest-1"),
                    doodads, rng, true);
        }
        if (!purposeHasFixture(topology, bl, bt, br, bb, RoomPurpose.APARTMENT_LIVING)) {
            stampPurposeFixture(grid, topology, bl, bt, br, bb,
                    RoomPurpose.APARTMENT_LIVING,
                    TileRegistry.installed().doodad("doodad.chair-south-yellow"),
                    doodads, rng, true);
        }
    }

    /** Gives the compact alley market one blocking rack and loose stockroom cover. */
    private static void applyDenseMarket(NavigationGrid grid, CellTopology topology,
                                         int bl, int bt, int br, int bb,
                                         List<Doodad> doodads, Random rng) {
        DoodadDef shelf = TileRegistry.installed().doodad("doodad.shelf-2");
        DoodadDef crate = TileRegistry.installed().doodad("doodad.crate");
        DoodadDef counter = TileRegistry.installed().doodad("doodad.desk-1");
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.SHOP_FLOOR, shelf, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.SHOP_FLOOR, counter, doodads, rng, true);
        stampPurposeFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.STOCKROOM, crate, doodads, rng, true);
    }

    private static boolean purposeHasFixture(CellTopology topology,
                                             int bl, int bt, int br, int bb,
                                             RoomPurpose purpose) {
        for (int y = bt + 1; y < bb; y++) {
            for (int x = bl + 1; x < br; x++) {
                if (topology.getRoomPurpose(x, y) == purpose
                        && topology.isFixture(x, y)) return true;
            }
        }
        return false;
    }

    /** Stamps one fixture into each disconnected region carrying {@code purpose}. */
    private static void stampOneFixturePerPurposeRoom(NavigationGrid grid, CellTopology topology,
                                                       int bl, int bt, int br, int bb,
                                                       RoomPurpose purpose, DoodadDef prop,
                                                       List<Doodad> doodads, Random rng,
                                                       boolean seeThrough) {
        stampOneFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                purpose, new DoodadDef[]{prop}, false, doodads, rng, seeThrough);
    }

    private static void stampOneOrientedFixturePerPurposeRoom(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef[] props,
            List<Doodad> doodads, Random rng,
            boolean seeThrough) {
        stampOneFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                purpose, props, true, doodads, rng, seeThrough);
    }

    private static void stampOneFixturePerPurposeRoom(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef[] props,
            boolean requirePreferredWall,
            List<Doodad> doodads, Random rng,
        boolean seeThrough) {
        for (List<int[]> roomCells : purposeRoomComponents(
                grid, topology, bl, bt, br, bb, purpose)) {
            List<FixtureCandidate> candidates = new ArrayList<>();
            for (DoodadDef prop : props) {
                if (requirePreferredWall && prop.preferredWallSide == null) continue;
                for (int[] cell : roomCells) {
                    if (!grid.isWalkable(cell[0], cell[1])
                            || grid.isDoorway(cell[0], cell[1])) continue;
                    if (!footprintHasPurpose(
                            topology, cell[0], cell[1], prop, purpose)) continue;
                    if (!canPlaceDoodad(grid, cell[0], cell[1], prop, doodads)) continue;
                    if (footprintTouchesWindow(
                            grid, topology, cell[0], cell[1], prop)) continue;
                    if (requirePreferredWall
                            && (!hasWallSupport(grid, topology, cell[0], cell[1], prop)
                            || !hasOpenFront(grid, topology, purpose,
                            cell[0], cell[1], prop, doodads))) continue;
                    if (!preservesRoomConnectivity(
                            grid, topology, purpose, roomCells,
                            cell[0], cell[1], prop)) continue;
                    candidates.add(new FixtureCandidate(cell[0], cell[1], prop));
                }
            }
            if (!candidates.isEmpty()) {
                FixtureCandidate candidate = candidates.get(rng.nextInt(candidates.size()));
                stampFixture(grid, topology, candidate.x, candidate.y,
                        candidate.prop, doodads, seeThrough);
            }
        }
    }

    /**
     * Places one room-defining fixture in each disconnected room carrying the
     * purpose. Candidate scoring prefers real wall contact and distance from
     * doors, so the room's circulation is a premise rather than the gap left
     * after a random placement.
     */
    private static void stampOneAuthoredFixturePerPurposeRoom(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef prop,
            List<Doodad> doodads, boolean seeThrough) {
        for (List<int[]> roomCells : purposeRoomComponents(
                grid, topology, bl, bt, br, bb, purpose)) {
            FixtureCandidate best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int[] cell : roomCells) {
                int x = cell[0];
                int y = cell[1];
                for (int turns = 0; turns < 4; turns++) {
                    FixtureCandidate candidate = new FixtureCandidate(x, y, prop, turns);
                    if (!isPurposeFixtureCandidate(
                            grid, topology, purpose, candidate, doodads)) continue;
                    if (!preservesRoomConnectivity(
                            grid, topology, purpose, roomCells, candidate)) continue;
                    int score = orientedFixtureScore(grid, topology, purpose,
                            bl, bt, br, bb, candidate, doodads);
                    if (score > bestScore || (score == bestScore
                            && earlierCandidate(candidate, best))) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                stampFixture(grid, topology, best, doodads, seeThrough);
            }
        }
    }

    /**
     * Selects an anchor and satellite as one workspace before committing either
     * footprint. This prevents one plausible prop placement from consuming the
     * only cell arrangement in which the complete room can exist.
     */
    private static void stampOneAuthoredFixtureGroupPerPurposeRoom(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef anchor, DoodadDef satellite,
            List<Doodad> doodads, boolean seeThrough) {
        for (List<int[]> roomCells : purposeRoomComponents(
                grid, topology, bl, bt, br, bb, purpose)) {
            FixtureCandidate bestAnchor = null;
            FixtureCandidate bestSatellite = null;
            int bestScore = Integer.MIN_VALUE;
            for (int[] anchorCell : roomCells) {
                int anchorX = anchorCell[0];
                int anchorY = anchorCell[1];
                for (int anchorTurns = 0; anchorTurns < 4; anchorTurns++) {
                    FixtureCandidate anchorCandidate =
                            new FixtureCandidate(anchorX, anchorY, anchor, anchorTurns);
                    if (!isPurposeFixtureCandidate(grid, topology, purpose,
                            anchorCandidate, doodads)) continue;
                    for (int[] satelliteCell : roomCells) {
                        int satelliteX = satelliteCell[0];
                        int satelliteY = satelliteCell[1];
                        for (int satelliteTurns = 0; satelliteTurns < 4; satelliteTurns++) {
                            FixtureCandidate satelliteCandidate = new FixtureCandidate(
                                    satelliteX, satelliteY, satellite, satelliteTurns);
                            if (!isPurposeFixtureCandidate(grid, topology, purpose,
                                    satelliteCandidate, doodads)) continue;
                            if (fixturesOverlap(anchorCandidate, satelliteCandidate)) continue;
                            if (!preservesRoomConnectivity(grid, topology, purpose, roomCells,
                                    anchorCandidate, satelliteCandidate)) continue;
                            int separation = fixtureSeparation(anchorCandidate, satelliteCandidate);
                            int score = orientedFixtureScore(grid, topology, purpose,
                                    bl, bt, br, bb, anchorCandidate, doodads)
                                    + orientedFixtureScore(grid, topology, purpose,
                                    bl, bt, br, bb, satelliteCandidate, doodads)
                                    - separation * 10;
                            if (score > bestScore || (score == bestScore
                                    && earlierPair(anchorCandidate, satelliteCandidate,
                                    bestAnchor, bestSatellite))) {
                                bestScore = score;
                                bestAnchor = anchorCandidate;
                                bestSatellite = satelliteCandidate;
                            }
                        }
                    }
                }
            }
            if (bestAnchor != null && bestSatellite != null) {
                stampFixture(grid, topology, bestAnchor, doodads, seeThrough);
                stampFixture(grid, topology, bestSatellite, doodads, seeThrough);
            }
        }
    }

    private static boolean isPurposeFixtureCandidate(
            NavigationGrid grid, CellTopology topology, RoomPurpose purpose,
            FixtureCandidate candidate, List<Doodad> doodads) {
        return footprintHasPurpose(topology, candidate, purpose)
                && canPlaceDoodad(grid, candidate, doodads)
                && !footprintTouchesWindow(grid, topology, candidate);
    }

    private static boolean fixturesOverlap(FixtureCandidate first,
                                           FixtureCandidate second) {
        return first.x < second.x + second.width
                && first.x + first.width > second.x
                && first.y < second.y + second.height
                && first.y + first.height > second.y;
    }

    private static boolean earlierCandidate(FixtureCandidate candidate,
                                            FixtureCandidate incumbent) {
        return incumbent == null || candidate.y < incumbent.y
                || (candidate.y == incumbent.y && candidate.x < incumbent.x)
                || (candidate.y == incumbent.y && candidate.x == incumbent.x
                && candidate.quarterTurns < incumbent.quarterTurns);
    }

    private static boolean earlierPair(FixtureCandidate anchor, FixtureCandidate satellite,
                                       FixtureCandidate incumbentAnchor,
                                       FixtureCandidate incumbentSatellite) {
        if (earlierCandidate(anchor, incumbentAnchor)) return true;
        if (incumbentAnchor == null || anchor.x != incumbentAnchor.x
                || anchor.y != incumbentAnchor.y
                || anchor.quarterTurns != incumbentAnchor.quarterTurns) return false;
        return earlierCandidate(satellite, incumbentSatellite);
    }

    private static int fixtureSeparation(FixtureCandidate first,
                                         FixtureCandidate second) {
        int firstCenterX = first.x * 2 + first.width;
        int firstCenterY = first.y * 2 + first.height;
        int secondCenterX = second.x * 2 + second.width;
        int secondCenterY = second.y * 2 + second.height;
        return (Math.abs(firstCenterX - secondCenterX)
                + Math.abs(firstCenterY - secondCenterY)) / 2;
    }

    private static int orientedFixtureScore(
            NavigationGrid grid, CellTopology topology, RoomPurpose purpose,
            int bl, int bt, int br, int bb,
            FixtureCandidate candidate, List<Doodad> doodads) {
        WallSide back = rotateSide(WallSide.N, candidate.quarterTurns);
        WallSide front = back.opposite();
        return fixtureWallContact(grid, topology, candidate) * 100
                + (hasWallSupport(grid, topology, candidate, back) ? 400 : 0)
                + (hasOpenFront(grid, topology, purpose, candidate, front, doodads) ? 250 : 0)
                + nearestDoorwayDistance(grid, bl, bt, br, bb, candidate);
    }

    private static int fixtureWallContact(NavigationGrid grid, CellTopology topology,
                                          FixtureCandidate candidate) {
        int contact = 0;
        for (WallSide side : WallSide.values()) {
            for (int offset = 0; offset < edgeLength(candidate, side); offset++) {
                int[] neighbor = edgeNeighbor(candidate, side, offset);
                if (!grid.inBounds(neighbor[0], neighbor[1])
                        || (!grid.isWalkable(neighbor[0], neighbor[1])
                        && !topology.isFixture(neighbor[0], neighbor[1]))) contact++;
            }
        }
        return contact;
    }

    private static int nearestDoorwayDistance(NavigationGrid grid,
                                              int bl, int bt, int br, int bb,
                                              FixtureCandidate candidate) {
        int nearest = (br - bl) + (bb - bt);
        for (int doorY = bt; doorY <= bb; doorY++) {
            for (int doorX = bl; doorX <= br; doorX++) {
                if (!grid.isDoorway(doorX, doorY)) continue;
                for (int dy = 0; dy < candidate.height; dy++) {
                    for (int dx = 0; dx < candidate.width; dx++) {
                        nearest = Math.min(nearest,
                                Math.abs(candidate.x + dx - doorX)
                                        + Math.abs(candidate.y + dy - doorY));
                    }
                }
            }
        }
        return nearest;
    }

    private static int fixtureWallContact(NavigationGrid grid, CellTopology topology,
                                          int x, int y, DoodadDef prop) {
        int contact = 0;
        for (WallSide side : WallSide.values()) {
            for (int offset = 0; offset < edgeLength(prop, side); offset++) {
                int[] neighbor = edgeNeighbor(x, y, prop, side, offset);
                if (!grid.inBounds(neighbor[0], neighbor[1])
                        || (!grid.isWalkable(neighbor[0], neighbor[1])
                        && !topology.isFixture(neighbor[0], neighbor[1]))) contact++;
            }
        }
        return contact;
    }

    private static int nearestDoorwayDistance(NavigationGrid grid,
                                              int bl, int bt, int br, int bb,
                                              int x, int y, DoodadDef prop) {
        int nearest = (br - bl) + (bb - bt);
        for (int doorY = bt; doorY <= bb; doorY++) {
            for (int doorX = bl; doorX <= br; doorX++) {
                if (!grid.isDoorway(doorX, doorY)) continue;
                for (int dy = 0; dy < prop.footprintCellsY; dy++) {
                    for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                        nearest = Math.min(nearest,
                                Math.abs(x + dx - doorX) + Math.abs(y + dy - doorY));
                    }
                }
            }
        }
        return nearest;
    }

    private static List<List<int[]>> purposeRoomComponents(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb, RoomPurpose purpose) {
        List<List<int[]>> rooms = new ArrayList<>();
        boolean[] visited = new boolean[grid.getWidth() * grid.getHeight()];
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                int startIndex = y * grid.getWidth() + x;
                if (visited[startIndex] || topology.getRoomPurpose(x, y) != purpose) continue;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                List<int[]> roomCells = new ArrayList<>();
                queue.add(new int[]{x, y});
                visited[startIndex] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    roomCells.add(cell);
                    for (int[] direction : directions) {
                        int nx = cell[0] + direction[0];
                        int ny = cell[1] + direction[1];
                        if (nx <= bl || nx >= br || ny <= bt || ny >= bb) continue;
                        int index = ny * grid.getWidth() + nx;
                        if (visited[index]
                                || topology.getRoomPurpose(nx, ny) != purpose) continue;
                        visited[index] = true;
                        queue.addLast(new int[]{nx, ny});
                    }
                }
                rooms.add(roomCells);
            }
        }
        return rooms;
    }

    private static boolean hasWallSupport(NavigationGrid grid, CellTopology topology,
                                          int x, int y, DoodadDef prop) {
        WallSide side = prop.preferredWallSide;
        int length = edgeLength(prop, side);
        for (int i = 0; i < length; i++) {
            int[] neighbor = edgeNeighbor(x, y, prop, side, i);
            if (!grid.inBounds(neighbor[0], neighbor[1])
                    || grid.isWalkable(neighbor[0], neighbor[1])
                    || topology.isFixture(neighbor[0], neighbor[1])) return false;
        }
        return true;
    }

    private static boolean hasWallSupport(NavigationGrid grid, CellTopology topology,
                                          FixtureCandidate candidate, WallSide side) {
        for (int offset = 0; offset < edgeLength(candidate, side); offset++) {
            int[] neighbor = edgeNeighbor(candidate, side, offset);
            if (!grid.inBounds(neighbor[0], neighbor[1])
                    || grid.isWalkable(neighbor[0], neighbor[1])
                    || topology.isFixture(neighbor[0], neighbor[1])) return false;
        }
        return true;
    }

    private static boolean hasOpenFront(NavigationGrid grid, CellTopology topology,
                                        RoomPurpose purpose, int x, int y,
                                        DoodadDef prop, List<Doodad> doodads) {
        WallSide front = prop.preferredWallSide.opposite();
        int length = edgeLength(prop, front);
        for (int i = 0; i < length; i++) {
            int[] neighbor = edgeNeighbor(x, y, prop, front, i);
            int cellX = neighbor[0];
            int cellY = neighbor[1];
            if (!grid.inBounds(cellX, cellY) || !grid.isWalkable(cellX, cellY)) return false;
            if (grid.isDoorway(cellX, cellY) || isOccupied(cellX, cellY, doodads)) return false;
            if (topology.getRoomPurpose(cellX, cellY) != purpose) return false;
        }
        return true;
    }

    private static boolean hasOpenFront(NavigationGrid grid, CellTopology topology,
                                        RoomPurpose purpose, FixtureCandidate candidate,
                                        WallSide front, List<Doodad> doodads) {
        for (int offset = 0; offset < edgeLength(candidate, front); offset++) {
            int[] neighbor = edgeNeighbor(candidate, front, offset);
            int cellX = neighbor[0];
            int cellY = neighbor[1];
            if (!grid.inBounds(cellX, cellY) || !grid.isWalkable(cellX, cellY)) return false;
            if (grid.isDoorway(cellX, cellY) || isOccupied(cellX, cellY, doodads)) return false;
            if (topology.getRoomPurpose(cellX, cellY) != purpose) return false;
        }
        return true;
    }

    private static int edgeLength(DoodadDef prop, WallSide side) {
        return side == WallSide.N || side == WallSide.S
                ? prop.footprintCellsX : prop.footprintCellsY;
    }

    private static int[] edgeNeighbor(int x, int y, DoodadDef prop,
                                      WallSide side, int offset) {
        switch (side) {
            case N: return new int[]{x + offset, y + prop.footprintCellsY};
            case S: return new int[]{x + offset, y - 1};
            case E: return new int[]{x + prop.footprintCellsX, y + offset};
            case W: return new int[]{x - 1, y + offset};
            default: throw new IllegalStateException();
        }
    }

    private static int edgeLength(FixtureCandidate candidate, WallSide side) {
        return side == WallSide.N || side == WallSide.S
                ? candidate.width : candidate.height;
    }

    private static int[] edgeNeighbor(FixtureCandidate candidate,
                                      WallSide side, int offset) {
        switch (side) {
            case N: return new int[]{candidate.x + offset, candidate.y + candidate.height};
            case S: return new int[]{candidate.x + offset, candidate.y - 1};
            case E: return new int[]{candidate.x + candidate.width, candidate.y + offset};
            case W: return new int[]{candidate.x - 1, candidate.y + offset};
            default: throw new IllegalStateException();
        }
    }

    private static WallSide rotateSide(WallSide side, int quarterTurns) {
        WallSide rotated = side;
        for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
            switch (rotated) {
                case N: rotated = WallSide.W; break;
                case W: rotated = WallSide.S; break;
                case S: rotated = WallSide.E; break;
                case E: rotated = WallSide.N; break;
                default: throw new IllegalStateException();
            }
        }
        return rotated;
    }

    private static final class FixtureCandidate {
        final int x;
        final int y;
        final DoodadDef prop;
        final int quarterTurns;
        final int width;
        final int height;

        FixtureCandidate(int x, int y, DoodadDef prop) {
            this(x, y, prop, 0);
        }

        FixtureCandidate(int x, int y, DoodadDef prop, int quarterTurns) {
            this.x = x;
            this.y = y;
            this.prop = prop;
            this.quarterTurns = Math.floorMod(quarterTurns, 4);
            boolean swapsAxes = (this.quarterTurns & 1) != 0;
            this.width = swapsAxes ? prop.footprintCellsY : prop.footprintCellsX;
            this.height = swapsAxes ? prop.footprintCellsX : prop.footprintCellsY;
        }
    }

    /** A multi-cell fixture cannot create a sealed pocket inside its owning room. */
    private static boolean preservesRoomConnectivity(NavigationGrid grid,
                                                     CellTopology topology,
                                                     RoomPurpose purpose,
                                                     List<int[]> roomCells,
                                                     int fixtureX, int fixtureY,
                                                     DoodadDef prop) {
        return preservesRoomConnectivity(grid, topology, purpose, roomCells,
                new FixtureCandidate(fixtureX, fixtureY, prop));
    }

    private static boolean preservesRoomConnectivity(NavigationGrid grid,
                                                     CellTopology topology,
                                                     RoomPurpose purpose,
                                                     List<int[]> roomCells,
                                                     FixtureCandidate... fixtures) {
        int remaining = 0;
        int[] start = null;
        for (int[] cell : roomCells) {
            if (!grid.isWalkable(cell[0], cell[1])) continue;
            if (insideAnyFootprint(cell[0], cell[1], fixtures)) continue;
            remaining++;
            if (start == null) start = cell;
        }
        if (remaining == 0 || start == null) return false;

        boolean[] seen = new boolean[grid.getWidth() * grid.getHeight()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start[1] * grid.getWidth() + start[0]] = true;
        int reached = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            reached++;
            for (int[] direction : directions) {
                int nx = cell[0] + direction[0];
                int ny = cell[1] + direction[1];
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                if (topology.getRoomPurpose(nx, ny) != purpose) continue;
                if (insideAnyFootprint(nx, ny, fixtures)) continue;
                int index = ny * grid.getWidth() + nx;
                if (seen[index]) continue;
                seen[index] = true;
                queue.addLast(new int[]{nx, ny});
            }
        }
        return reached == remaining;
    }

    private static boolean insideAnyFootprint(int x, int y,
                                              FixtureCandidate... fixtures) {
        for (FixtureCandidate fixture : fixtures) {
            if (x >= fixture.x && x < fixture.x + fixture.width
                    && y >= fixture.y && y < fixture.y + fixture.height) return true;
        }
        return false;
    }

    private static boolean insideFootprint(int x, int y, int fixtureX, int fixtureY,
                                           DoodadDef prop) {
        return x >= fixtureX && x < fixtureX + prop.footprintCellsX
                && y >= fixtureY && y < fixtureY + prop.footprintCellsY;
    }

    private static void stampPurposeFixture(NavigationGrid grid, CellTopology topology,
                                            int bl, int bt, int br, int bb,
                                            RoomPurpose purpose, DoodadDef prop,
                                            List<Doodad> doodads, Random rng) {
        int[] cell = pickFreePurposeCell(
                grid, topology, bl, bt, br, bb, purpose, prop, doodads, rng);
        if (cell != null) stampFixture(grid, topology, cell[0], cell[1], prop, doodads);
    }

    private static void stampPurposeFixture(NavigationGrid grid, CellTopology topology,
                                            int bl, int bt, int br, int bb,
                                            RoomPurpose purpose, DoodadDef prop,
                                            List<Doodad> doodads, Random rng,
                                            boolean seeThrough) {
        int[] cell = pickFreePurposeCell(
                grid, topology, bl, bt, br, bb, purpose, prop, doodads, rng);
        if (cell != null) {
            stampFixture(grid, topology, cell[0], cell[1], prop, doodads, seeThrough);
        }
    }

    private static int purposeFixtureBudget(CellTopology topology,
                                            int bl, int bt, int br, int bb,
                                            RoomPurpose purpose, int minimum,
                                            int maximum, int cellsPerFixture) {
        int cells = 0;
        for (int y = bt + 1; y < bb; y++) {
            for (int x = bl + 1; x < br; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) cells++;
            }
        }
        if (cells == 0) return 0;
        return Math.max(minimum, Math.min(maximum,
                (cells + cellsPerFixture - 1) / cellsPerFixture));
    }

    /**
     * Repeated authored rows replace one historical random placement without
     * shifting the request-owned RNG stream seen by later fills and stages.
     */
    private static void preservePlacementDraw(CellTopology topology,
                                              int bl, int bt, int br, int bb,
                                              RoomPurpose purpose, Random rng) {
        if (purposeBounds(topology, bl, bt, br, bb, purpose) != null) rng.nextInt();
    }

    private static void preservePlacementDrawsForPurposeRooms(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, Random rng) {
        for (List<int[]> ignored : purposeRoomComponents(
                grid, topology, bl, bt, br, bb, purpose)) {
            rng.nextInt();
        }
    }

    /**
     * Stamps a purpose-specific fixture bank along the room's real wall edges.
     * Rows are tried from the most-supported edge outward, with spacing between
     * stations and a connectivity check after every placement. This is the
     * common grammar for server racks and command-console banks.
     */
    private static void stampPurposeFixtureRows(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef prop,
            List<Doodad> doodads, int budget, int spacing,
            boolean seeThrough, boolean blocking) {
        if (budget <= 0) return;
        int[] bounds = purposeBounds(topology, bl, bt, br, bb, purpose);
        if (bounds == null) return;
        List<int[]> roomCells = purposeCells(topology, bl, bt, br, bb, purpose);
        WallSide[] sides = WallSide.values();
        boolean[] used = new boolean[sides.length];
        int placed = 0;

        for (int row = 0; row < sides.length && placed < budget; row++) {
            int bestIndex = -1;
            int bestScore = 0;
            for (int i = 0; i < sides.length; i++) {
                if (used[i]) continue;
                int score = purposeWallRow(grid, topology, bounds, purpose,
                        prop, doodads, sides[i]).size();
                if (score > bestScore) {
                    bestIndex = i;
                    bestScore = score;
                }
            }
            if (bestIndex < 0) break;
            used[bestIndex] = true;
            List<int[]> cells = purposeWallRow(grid, topology, bounds, purpose,
                    prop, doodads, sides[bestIndex]);
            for (int i = 0; i < cells.size() && placed < budget; i += spacing) {
                int[] cell = cells.get(i);
                if (!canPlaceDoodad(grid, cell[0], cell[1], prop, doodads)) continue;
                if (blocking && !preservesRoomConnectivity(grid, topology, purpose, roomCells,
                        cell[0], cell[1], prop)) continue;
                int before = doodads.size();
                if (blocking) {
                    stampFixture(grid, topology, cell[0], cell[1], prop, doodads, seeThrough);
                } else {
                    doodads.add(new Doodad(cell[0], cell[1], prop));
                }
                if (doodads.size() > before) placed++;
            }
        }
    }

    private static List<int[]> purposeWallRow(
            NavigationGrid grid, CellTopology topology, int[] bounds,
            RoomPurpose purpose, DoodadDef prop, List<Doodad> doodads,
            WallSide side) {
        List<int[]> cells = new ArrayList<>();
        int start = side == WallSide.N || side == WallSide.S ? bounds[0] : bounds[1];
        int end = side == WallSide.N || side == WallSide.S ? bounds[2] : bounds[3];
        for (int along = start; along <= end; along++) {
            int x = side == WallSide.W ? bounds[0]
                    : side == WallSide.E ? bounds[2] : along;
            int y = side == WallSide.S ? bounds[1]
                    : side == WallSide.N ? bounds[3] : along;
            int behindX = x + (side == WallSide.E ? 1 : side == WallSide.W ? -1 : 0);
            int behindY = y + (side == WallSide.N ? 1 : side == WallSide.S ? -1 : 0);
            if (!grid.inBounds(behindX, behindY) || grid.isWalkable(behindX, behindY)
                    || topology.isFixture(behindX, behindY)) continue;
            if (!footprintHasPurpose(topology, x, y, prop, purpose)) continue;
            if (!canPlaceDoodad(grid, x, y, prop, doodads)) continue;
            if (footprintTouchesWindow(grid, topology, x, y, prop)) continue;
            cells.add(new int[]{x, y});
        }
        return cells;
    }

    /** Civic equipment rows rotate one canonical rack toward whichever wall supports the row. */
    private static void stampRotatablePurposeFixtureRows(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef prop,
            List<Doodad> doodads, int budget, int spacing,
            boolean seeThrough, boolean blocking) {
        if (budget <= 0) return;
        int[] bounds = purposeBounds(topology, bl, bt, br, bb, purpose);
        if (bounds == null) return;
        List<int[]> roomCells = purposeCells(topology, bl, bt, br, bb, purpose);
        WallSide[] sides = WallSide.values();
        boolean[] used = new boolean[sides.length];
        int placed = 0;

        for (int row = 0; row < sides.length && placed < budget; row++) {
            int bestIndex = -1;
            int bestScore = 0;
            for (int i = 0; i < sides.length; i++) {
                if (used[i]) continue;
                int score = rotatablePurposeWallRow(grid, topology, bounds, purpose,
                        prop, doodads, sides[i]).size();
                if (score > bestScore) {
                    bestIndex = i;
                    bestScore = score;
                }
            }
            if (bestIndex < 0) break;
            used[bestIndex] = true;
            List<FixtureCandidate> cells = rotatablePurposeWallRow(
                    grid, topology, bounds, purpose, prop, doodads, sides[bestIndex]);
            for (int i = 0; i < cells.size() && placed < budget; i += spacing) {
                FixtureCandidate candidate = cells.get(i);
                if (!canPlaceDoodad(grid, candidate, doodads)) continue;
                if (blocking && !preservesRoomConnectivity(
                        grid, topology, purpose, roomCells, candidate)) continue;
                int before = doodads.size();
                if (blocking) {
                    stampFixture(grid, topology, candidate, doodads, seeThrough);
                } else {
                    doodads.add(new Doodad(candidate.x, candidate.y,
                            candidate.prop, candidate.quarterTurns));
                }
                if (doodads.size() > before) placed++;
            }
        }
    }

    private static List<FixtureCandidate> rotatablePurposeWallRow(
            NavigationGrid grid, CellTopology topology, int[] bounds,
            RoomPurpose purpose, DoodadDef prop, List<Doodad> doodads,
            WallSide side) {
        int turns = quarterTurnsFromNorth(side);
        FixtureCandidate dimensions = new FixtureCandidate(0, 0, prop, turns);
        int start = side == WallSide.N || side == WallSide.S ? bounds[0] : bounds[1];
        int end = side == WallSide.N || side == WallSide.S
                ? bounds[2] - dimensions.width + 1
                : bounds[3] - dimensions.height + 1;
        List<FixtureCandidate> cells = new ArrayList<>();
        for (int along = start; along <= end; along++) {
            int x = side == WallSide.W ? bounds[0]
                    : side == WallSide.E ? bounds[2] - dimensions.width + 1 : along;
            int y = side == WallSide.S ? bounds[1]
                    : side == WallSide.N ? bounds[3] - dimensions.height + 1 : along;
            FixtureCandidate candidate = new FixtureCandidate(x, y, prop, turns);
            if (!isPurposeFixtureCandidate(grid, topology, purpose, candidate, doodads)) continue;
            if (!hasWallSupport(grid, topology, candidate, side)) continue;
            if (!hasOpenFront(grid, topology, purpose, candidate,
                    side.opposite(), doodads)) continue;
            cells.add(candidate);
        }
        return cells;
    }

    private static int quarterTurnsFromNorth(WallSide side) {
        switch (side) {
            case N: return 0;
            case W: return 1;
            case S: return 2;
            case E: return 3;
            default: throw new IllegalStateException();
        }
    }

    private static void stampPurposeCenterFixture(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef prop,
            List<Doodad> doodads, Random rng, boolean seeThrough) {
        int[] bounds = purposeBounds(topology, bl, bt, br, bb, purpose);
        if (bounds == null) return;
        List<int[]> roomCells = purposeCells(topology, bl, bt, br, bb, purpose);
        List<int[]> best = new ArrayList<>();
        int bestDistance = Integer.MAX_VALUE;
        for (int[] cell : roomCells) {
            int x = cell[0], y = cell[1];
            if (!footprintHasPurpose(topology, x, y, prop, purpose)
                    || !canPlaceDoodad(grid, x, y, prop, doodads)
                    || footprintTouchesWindow(grid, topology, x, y, prop)) continue;
            if (!preservesRoomConnectivity(grid, topology, purpose, roomCells,
                    x, y, prop)) continue;
            int distance = Math.abs(2 * x - bounds[0] - bounds[2])
                    + Math.abs(2 * y - bounds[1] - bounds[3]);
            if (distance < bestDistance) {
                best.clear();
                bestDistance = distance;
            }
            if (distance == bestDistance) best.add(cell);
        }
        if (best.isEmpty()) return;
        int[] cell = best.get(rng.nextInt(best.size()));
        stampFixture(grid, topology, cell[0], cell[1], prop, doodads, seeThrough);
    }

    /** Centers the full footprint and turns its long axis with the room. */
    private static void stampRotatablePurposeCenterFixture(
            NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb,
            RoomPurpose purpose, DoodadDef prop,
            List<Doodad> doodads, Random rng, boolean seeThrough) {
        int[] bounds = purposeBounds(topology, bl, bt, br, bb, purpose);
        if (bounds == null) return;
        List<int[]> roomCells = purposeCells(topology, bl, bt, br, bb, purpose);
        List<FixtureCandidate> best = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        int roomCenterX = bounds[0] + bounds[2] + 1;
        int roomCenterY = bounds[1] + bounds[3] + 1;
        boolean roomIsWide = bounds[2] - bounds[0] >= bounds[3] - bounds[1];
        for (int[] cell : roomCells) {
            for (int turns = 0; turns < 4; turns++) {
                FixtureCandidate candidate =
                        new FixtureCandidate(cell[0], cell[1], prop, turns);
                if (!isPurposeFixtureCandidate(
                        grid, topology, purpose, candidate, doodads)) continue;
                if (!preservesRoomConnectivity(
                        grid, topology, purpose, roomCells, candidate)) continue;
                int centerDistance = Math.abs(2 * candidate.x + candidate.width - roomCenterX)
                        + Math.abs(2 * candidate.y + candidate.height - roomCenterY);
                boolean fixtureIsWide = candidate.width >= candidate.height;
                int score = centerDistance * 10 + (fixtureIsWide == roomIsWide ? 0 : 1);
                if (score < bestScore) {
                    best.clear();
                    bestScore = score;
                }
                if (score == bestScore) best.add(candidate);
            }
        }
        if (best.isEmpty()) return;
        stampFixture(grid, topology, best.get(rng.nextInt(best.size())), doodads, seeThrough);
    }

    private static List<int[]> purposeCells(CellTopology topology,
                                            int bl, int bt, int br, int bb,
                                            RoomPurpose purpose) {
        List<int[]> cells = new ArrayList<>();
        for (int y = bt + 1; y < bb; y++) {
            for (int x = bl + 1; x < br; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) {
                    cells.add(new int[]{x, y});
                }
            }
        }
        return cells;
    }

    private static void fixtureWallLine(NavigationGrid grid, CellTopology topology,
                                        int bl, int bt, int br, int bb,
                                        WallSide side, DoodadDef prop, int spacing,
                                        List<Doodad> doodads) {
        fixtureWallLineMix(grid, topology, bl, bt, br, bb, side,
                new DoodadDef[]{prop}, spacing, doodads, null);
    }

    private static void fixtureWallLineMix(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           WallSide side, DoodadDef[] props, int spacing,
                                           List<Doodad> doodads, Random rng) {
        int start = (side == WallSide.N || side == WallSide.S) ? bl + 1 : bt + 1;
        int end = (side == WallSide.N || side == WallSide.S) ? br - 1 : bb - 1;
        for (int along = start; along <= end; along += spacing) {
            DoodadDef prop = props.length == 1 ? props[0] : props[rng.nextInt(props.length)];
            // A cell the no-island guard refuses takes the prop one further
            // along the wall instead of losing it. The run's own cadence is
            // untouched, so shifting one prop cannot eat the tail of the row.
            int slot = along;
            while (slot <= end && refusedOnlyByNoIslandGuard(grid,
                    alongX(side, bl, br, slot), alongY(side, bt, bb, slot), prop, doodads)) {
                slot++;
            }
            if (slot > end) continue;
            stampFixture(grid, topology,
                    alongX(side, bl, br, slot), alongY(side, bt, bb, slot), prop, doodads);
        }
    }

    private static int alongX(WallSide side, int bl, int br, int along) {
        return side == WallSide.W ? bl + 1 : side == WallSide.E ? br - 1 : along;
    }

    private static int alongY(WallSide side, int bt, int bb, int along) {
        return side == WallSide.S ? bt + 1 : side == WallSide.N ? bb - 1 : along;
    }

    /**
     * True when the only thing wrong with the cell is that blocking it would
     * strand walkable ground. The case that actually arises is the cell in
     * front of a firing aperture: {@link BuildingShellCore} turns that
     * perimeter cell into a walkable recess reachable only from inside, so a
     * prop on the inside cell seals it. A run of props that hits one should
     * step along its wall rather than lose the prop — the row's density is what
     * it is for — while a cell refused for any other reason (a doorway, an
     * earlier doodad) is left alone as it always was.
     */
    private static boolean refusedOnlyByNoIslandGuard(NavigationGrid grid, int x, int y,
                                                      DoodadDef prop, List<Doodad> doodads) {
        return canPlaceDoodad(grid, x, y, prop, doodads)
                && PlacementGuards.wouldStrandGround(grid, x, y,
                        prop.footprintCellsX, prop.footprintCellsY);
    }

    /** Fixture props block bodies, default to transparent to fire, and never masquerade as structure. */
    private static void stampFixture(NavigationGrid grid, CellTopology topology,
                                     int x, int y, DoodadDef prop, List<Doodad> doodads) {
        stampFixture(grid, topology, x, y, prop, doodads, true);
    }

    private static void stampFixture(NavigationGrid grid, CellTopology topology,
                                     int x, int y, DoodadDef prop, List<Doodad> doodads,
                                     boolean seeThrough) {
        if (!canPlaceDoodad(grid, x, y, prop, doodads)) return;
        if (PlacementGuards.wouldStrandGround(grid, x, y,
                prop.footprintCellsX, prop.footprintCellsY)) return;
        for (int dy = 0; dy < prop.footprintCellsY; dy++) {
            for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                int cellX = x + dx;
                int cellY = y + dy;
                grid.setWalkable(cellX, cellY, false);
                grid.setSeeThrough(cellX, cellY, seeThrough);
                topology.setWall(cellX, cellY, false);
                topology.setFixture(cellX, cellY, true);
            }
        }
        doodads.add(new Doodad(x, y, prop));
    }

    private static void stampFixture(NavigationGrid grid, CellTopology topology,
                                     FixtureCandidate candidate, List<Doodad> doodads,
                                     boolean seeThrough) {
        if (!canPlaceDoodad(grid, candidate, doodads)) return;
        if (PlacementGuards.wouldStrandGround(grid, candidate.x, candidate.y,
                candidate.width, candidate.height)) return;
        for (int dy = 0; dy < candidate.height; dy++) {
            for (int dx = 0; dx < candidate.width; dx++) {
                int cellX = candidate.x + dx;
                int cellY = candidate.y + dy;
                grid.setWalkable(cellX, cellY, false);
                grid.setSeeThrough(cellX, cellY, seeThrough);
                topology.setWall(cellX, cellY, false);
                topology.setFixture(cellX, cellY, true);
            }
        }
        doodads.add(new Doodad(candidate.x, candidate.y,
                candidate.prop, candidate.quarterTurns));
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
        if (!canPlaceDoodad(grid, x, y, prop, doodads)) return;
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
            if (d.occupiesCell(x, y)) return true;
        }
        return false;
    }

    private static boolean canPlaceDoodad(NavigationGrid grid, int x, int y,
                                          DoodadDef prop, List<Doodad> doodads) {
        for (int dy = 0; dy < prop.footprintCellsY; dy++) {
            for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                int cellX = x + dx;
                int cellY = y + dy;
                if (!grid.inBounds(cellX, cellY) || !grid.isWalkable(cellX, cellY)) return false;
                if (grid.isDoorway(cellX, cellY) || isNearDoorway(grid, cellX, cellY)) return false;
                if (isOccupied(cellX, cellY, doodads)) return false;
            }
        }
        return true;
    }

    private static boolean canPlaceDoodad(NavigationGrid grid,
                                          FixtureCandidate candidate,
                                          List<Doodad> doodads) {
        for (int dy = 0; dy < candidate.height; dy++) {
            for (int dx = 0; dx < candidate.width; dx++) {
                int cellX = candidate.x + dx;
                int cellY = candidate.y + dy;
                if (!grid.inBounds(cellX, cellY) || !grid.isWalkable(cellX, cellY)) return false;
                if (grid.isDoorway(cellX, cellY) || isNearDoorway(grid, cellX, cellY)) return false;
                if (isOccupied(cellX, cellY, doodads)) return false;
            }
        }
        return true;
    }

    private static boolean footprintHasPurpose(CellTopology topology, int x, int y,
                                               DoodadDef prop, RoomPurpose purpose) {
        for (int dy = 0; dy < prop.footprintCellsY; dy++) {
            for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                int cellX = x + dx;
                int cellY = y + dy;
                if (!topology.inBounds(cellX, cellY)
                        || topology.getRoomPurpose(cellX, cellY) != purpose) return false;
            }
        }
        return true;
    }

    private static boolean footprintHasPurpose(CellTopology topology,
                                               FixtureCandidate candidate,
                                               RoomPurpose purpose) {
        for (int dy = 0; dy < candidate.height; dy++) {
            for (int dx = 0; dx < candidate.width; dx++) {
                int cellX = candidate.x + dx;
                int cellY = candidate.y + dy;
                if (!topology.inBounds(cellX, cellY)
                        || topology.getRoomPurpose(cellX, cellY) != purpose) return false;
            }
        }
        return true;
    }

    /** Keeps the standable cell immediately inside every firing window clear. */
    private static boolean footprintTouchesWindow(NavigationGrid grid,
                                                  CellTopology topology, int x, int y,
                                                  DoodadDef prop) {
        for (int dy = 0; dy < prop.footprintCellsY; dy++) {
            for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                int cellX = x + dx;
                int cellY = y + dy;
                for (Direction direction : Direction.CARDINALS) {
                    int neighborX = cellX + direction.dx;
                    int neighborY = cellY + direction.dy;
                    if (topology.isWindow(neighborX, neighborY)
                            || ownsWindowEdge(grid, neighborX, neighborY)) return true;
                }
            }
        }
        return false;
    }

    /** True when this cell is the structure side of any thin window edge. */
    private static boolean ownsWindowEdge(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        for (Direction direction : Direction.CARDINALS) {
            SharedEdgeBarrier barrier = grid.getEdgeBarrier(x, y, direction);
            if (barrier != null
                    && barrier.kind() == SharedEdgeBarrier.Kind.WINDOW
                    && barrier.structureCellX() == x
                    && barrier.structureCellY() == y) return true;
        }
        return false;
    }

    private static boolean footprintTouchesWindow(NavigationGrid grid,
                                                  CellTopology topology,
                                                  FixtureCandidate candidate) {
        for (int dy = 0; dy < candidate.height; dy++) {
            for (int dx = 0; dx < candidate.width; dx++) {
                int cellX = candidate.x + dx;
                int cellY = candidate.y + dy;
                for (Direction direction : Direction.CARDINALS) {
                    int neighborX = cellX + direction.dx;
                    int neighborY = cellY + direction.dy;
                    if (topology.isWindow(neighborX, neighborY)
                            || ownsWindowEdge(grid, neighborX, neighborY)) return true;
                }
            }
        }
        return false;
    }

    /** Returns a random walkable, non-doorway, non-occupied interior cell, or {@code null} if no candidate exists. */
    private static int[] pickFreeInteriorCell(NavigationGrid grid,
                                              int bl, int bt, int br, int bb,
                                              DoodadDef prop,
                                              List<Doodad> doodads, Random rng) {
        List<int[]> free = new ArrayList<>();
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                if (!canPlaceDoodad(grid, x, y, prop, doodads)) continue;
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
            int[] cell = pickFreeInteriorCell(
                    grid, bl, bt, br, bb, pool.get(0), doodads, rng);
            if (cell == null) break;
            doodads.add(new Doodad(cell[0], cell[1], pool.get(rng.nextInt(pool.size()))));
        }
    }
}
