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
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout;
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
 *       axis — i.e., a 5×5 or smaller building) — a fitted utility, kitchen, retail or storage nook
 *       with working clearance.</li>
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
        /** Compact utility or service room, themed by the building pool. */
        SHED,
        /** Residential. Sleeping nook and kitchenette with working clearance. */
        HOME,
        /** Courtyard-facing apartment block with a lobby, common hall, living rooms, and bedrooms. */
        APARTMENT_BLOCK,
        /** Commercial shop. Shelves on both long walls + a counter-style desk just inside a doorway. */
        SHOP,
        /** Industrial warehouse. Stocked rack bays or compact case rows with a dispatch desk. */
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
        /** Medical supply annex: clean handling, consumable storage and dispatch. */
        MEDICAL_SUPPORT,
        /** Narrow dense-block tenement row with a common room and private bedroom. */
        DENSE_TENEMENT,
        /** Narrow dense-block market row with a shop floor and rear stockroom. */
        DENSE_MARKET,
    }

    // ---- Public API ----

    /**
     * Top-level dispatcher. Routes TINY buildings to compact themed groups
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
            CompactBuildingFurnishing.furnish(grid, topology, bl, bt, br, bb, doodadPoolId, recipe, doodads);
            return;
        }
        switch (recipe) {
            case HOME:      applyHome(grid, topology, bl, bt, br, bb, doodads, rng); break;
            case APARTMENT_BLOCK: applyApartmentBlock(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
            case SHOP:      applyShop(grid, topology, partition, bl, bt, br, bb, doodads, rng); break;
            case WAREHOUSE: applyWarehouse(grid, topology, bl, bt, br, bb, doodads, rng); break;
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
            case MEDICAL_SUPPORT: applyMedicalSupport(grid, topology, bl, bt, br, bb, doodads); break;
            case DENSE_TENEMENT: applyDenseTenement(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
            case DENSE_MARKET: applyDenseMarket(
                    grid, topology, bl, bt, br, bb, doodads, rng); break;
            case SHED:
            default:        CompactBuildingFurnishing.furnish(grid, topology, bl, bt, br, bb, doodadPoolId, recipe, doodads); break;
        }
    }

    // ---- Recipes ----

    /** Sleeping nook and kitchenette, each fitted with protected working space. */
    private static void applyHome(NavigationGrid grid, CellTopology topology,
                                  int bl, int bt, int br, int bb, List<Doodad> doodads, Random rng) {
        if (!NeighborhoodFurnishing.furnish(grid, topology, bl, bt, br, bb, doodads))
            CompactBuildingFurnishing.furnish(grid, topology, bl, bt, br, bb,
                    "RESIDENTIAL", LayoutRecipe.HOME, doodads);
    }

    /**
     * Furnishes each purpose-labeled apartment room with complete domestic activity groups.
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
            applyHome(grid, topology, bl, bt, br, bb, doodads, rng);
            return;
        }

        NeighborhoodFurnishing.furnish(grid, topology, bl, bt, br, bb, doodads);
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
                TileRegistry.installed().doodad("colony-furnishings.shelf"),
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

        DoodadDef desk = TileRegistry.installed().doodad("colony-furnishings.desk");
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

        DoodadDef desk = TileRegistry.installed().doodad("colony-furnishings.desk");
        counterAtShopEntrance(grid, topology, bl, bt, br, bb, desk, doodads);

        BuildingWorkGroups groups = new BuildingWorkGroups(grid, topology, doodads);
        groups.reserveDoorSurrounds(bl, bt, br, bb);
        if (groups.fit(bl, bt, br, bb, RoomPurpose.STOCKROOM,
                CompactBuildingFurnishing.pair("case", "case"), 2) == 0)
            groups.fit(bl, bt, br, bb, RoomPurpose.STOCKROOM, CompactBuildingFurnishing.single("case"), 1);
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

    /** Rack bays and dispatch desk retain handling aisles. */
    private static void applyWarehouse(NavigationGrid grid, CellTopology topology,
                                       int bl, int bt, int br, int bb, List<Doodad> doodads, Random rng) {
        BuildingWorkGroups groups = new BuildingWorkGroups(grid, topology, doodads);
        groups.reserveCargoApproaches(bl, bt, br, bb);
        for (RoomPurpose purpose : new RoomPurpose[]{null, RoomPurpose.GENERIC, RoomPurpose.STOCKROOM}) {
            groups.fit(bl, bt, br, bb, purpose, CompactBuildingFurnishing.single("desk"), 1);
            if (groups.fit(bl, bt, br, bb, purpose, BuildingWorkGroups.stockedRack("cases"), 2) == 0)
                groups.fit(bl, bt, br, bb, purpose, CompactBuildingFurnishing.pair("case", "case"), 2);
        }
    }

    /**
     * Furnishes a qualifying factory without obstructing its service spine.
     * Full-footprint opaque machines face inward from the production side,
     * retaining two-cell operating and cross aisles. Receiving and support
     * furniture share the same reserved-access fitter; compact floors retain
     * smaller machines or the legacy line when complete bays cannot fit.
     */
    private static void applyIndustrialFacility(NavigationGrid grid, CellTopology topology,
                                                 PartitionLayout partition,
                                                 int bl, int bt, int br, int bb,
                                                 List<Doodad> doodads, Random rng) {
        if (!partition.tacticalIndustrial) {
            if (!NeighborhoodFurnishing.furnish(grid, topology, bl, bt, br, bb, doodads))
                applyWarehouse(grid, topology, bl, bt, br, bb, doodads, rng);
            return;
        }

        DoodadDef machine = TileRegistry.installed().doodad("doodad.industrial-machine-tool");
        DoodadDef tank = TileRegistry.installed().doodad("doodad.industrial-fluid-tank");
        DoodadDef console = TileRegistry.installed().doodad("doodad.industrial-control-console");
        DoodadDef parts = TileRegistry.installed().doodad("doodad.industrial-crate-stack");
        DoodadDef pallet = TileRegistry.installed().doodad("doodad.industrial-pallet-stack");

        if (stampPurposeConsoleBanks(grid, topology, bl, bt, br, bb, RoomPurpose.CONTROL_ROOM, doodads) == 0)
            stampPurposeFixture(grid, topology, bl, bt, br, bb,
                    RoomPurpose.CONTROL_ROOM, console, doodads, rng, true);
        else preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.CONTROL_ROOM, rng);
        BuildingWorkGroups storage = new BuildingWorkGroups(grid, topology, doodads);
        storage.reserveCargoApproaches(bl, bt, br, bb);
        int[] floor = purposeBounds(topology, bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR);
        boolean vertical = partition.orient == PartitionLayout.Orient.VERTICAL;
        boolean lowSide = (vertical ? floor[2] : floor[3]) < partition.preferredPerimeterDoorAlong;
        int machineTurn = vertical ? (lowSide ? 1 : 3) : (lowSide ? 2 : 0);
        int machineCount = storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR,
                BuildingWorkGroups.productionLine(true), 1, machineTurn);
        machineCount += storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR,
                BuildingWorkGroups.productionLine(false),
                purposeFixtureBudget(topology, bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR, 1, 3, 120), machineTurn);
        if (machineCount == 0) {
            machineCount += storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR,
                    BuildingWorkGroups.productionLine(false), 1, (machineTurn + 1) % 4);
        }
        if (machineCount == 0) {
            machineCount += storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR,
                    BuildingWorkGroups.machine("factory-machines.fabricator"), 1, machineTurn);
            machineCount += storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR,
                    BuildingWorkGroups.machine("factory-machines.assembly"), 1, machineTurn);
        }
        machineCount += storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR,
                BuildingWorkGroups.machine("factory-machines.coolant"), 1, machineTurn);
        if (machineCount == 0) {
            stampIndustrialProductionLine(grid, topology, partition,
                    bl, bt, br, bb, new DoodadDef[]{machine, tank}, doodads);
            storage = new BuildingWorkGroups(grid, topology, doodads);
            storage.reserveCargoApproaches(bl, bt, br, bb);
        }
        int racks = storage.fit(bl, bt, br, bb, RoomPurpose.PARTS_CAGE,
                BuildingWorkGroups.stockedRack("cases"), 2);
        if (racks > 0) {
            storage.fit(bl, bt, br, bb, RoomPurpose.PARTS_CAGE, BuildingWorkGroups.cargoDesk(false), 1);
            preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.PARTS_CAGE, rng);
            preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.PARTS_CAGE, rng);
        } else {
            stampPurposeFixture(grid, topology, bl, bt, br, bb,
                    RoomPurpose.PARTS_CAGE, parts, doodads, rng, true);
            stampPurposeFixture(grid, topology, bl, bt, br, bb,
                    RoomPurpose.PARTS_CAGE, parts, doodads, rng, true);
        }
        int benches = storage.fit(bl, bt, br, bb, RoomPurpose.LOADING_BAY,
                BuildingWorkGroups.cargoDesk(true), 1);
        if (benches > 0) {
            storage.fit(bl, bt, br, bb, RoomPurpose.LOADING_BAY, BuildingWorkGroups.cargoDesk(false), 1);
            int banks = storage.fit(bl, bt, br, bb, RoomPurpose.LOADING_BAY,
                    BuildingWorkGroups.pallets(2, "cartons"), 2);
            if (banks == 0) storage.fit(bl, bt, br, bb, RoomPurpose.LOADING_BAY,
                    BuildingWorkGroups.pallets(1, "cartons"), 1);
            preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.LOADING_BAY, rng);
        } else {
            // Even the compact fallback must honor the cargo approach reservation.
            if (pallet != null) storage.fit(bl, bt, br, bb, RoomPurpose.LOADING_BAY,
                    new BuildingWorkGroups.Group(2, 3,
                            List.of(new BuildingWorkGroups.Part(pallet.id, 0, 0, true))), 1);
            preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.LOADING_BAY, rng);
        }
        storage.fit(bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR, BuildingWorkGroups.palletIsland(List.of(
                        new ConsoleBankLayout.Cell(0, 0), new ConsoleBankLayout.Cell(1, 0),
                        new ConsoleBankLayout.Cell(0, 1)), "cases"),
                purposeFixtureBudget(topology, bl, bt, br, bb, RoomPurpose.PRODUCTION_FLOOR, 0, 3, 90));
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
        int[] chamber = purposeBounds(topology, bl, bt, br, bb, RoomPurpose.KEEP_THRONE);
        boolean largePlot = chamber != null && chamber[2] - chamber[0] >= 6 && chamber[3] - chamber[1] >= 6;
        DoodadDef table = TileRegistry.installed().doodad(largePlot ? "ship-bridge.plot" : "doodad.military-tactical-table");
        DoodadDef workstation = TileRegistry.installed().doodad("doodad.office-workstation-bank");
        stampPurposeCenterFixture(grid, topology, bl, bt, br, bb,
                RoomPurpose.KEEP_THRONE, table, doodads, rng, true);
        stampPurposeConsoleBanks(grid, topology, bl, bt, br, bb, RoomPurpose.KEEP_THRONE, doodads);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.KEEP_THRONE, rng);
        stampPurposeConsoleBanks(grid, topology, bl, bt, br, bb, RoomPurpose.KEEP_INNER, doodads);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.KEEP_INNER, rng);
        stampPurposeFixtureRows(grid, topology, bl, bt, br, bb,
                RoomPurpose.KEEP_ENTRY, workstation, doodads,
                purposeFixtureBudget(topology, bl, bt, br, bb,
                        RoomPurpose.KEEP_ENTRY, 2, 4, 5), 2, true, false);
        BuildingWorkGroups storage = new BuildingWorkGroups(grid, topology, doodads);
        storage.fit(bl, bt, br, bb, RoomPurpose.KEEP_INNER, BuildingWorkGroups.stockedRack("cases"), 2);
        storage.fit(bl, bt, br, bb, RoomPurpose.KEEP_ENTRY, BuildingWorkGroups.pallets(1, "cartons"), 2);
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
                TileRegistry.installed().doodad("colony-furnishings.shelf"),
                TileRegistry.installed().doodad("doodad.industrial-crate-stack"),
        };
        boolean horizontal = (br - bl) >= (bb - bt);
        WallSide first = horizontal ? WallSide.N : WallSide.W;
        WallSide second = horizontal ? WallSide.S : WallSide.E;
        fixtureWallLineMix(grid, topology, bl, bt, br, bb, first, stores, 2, doodads, rng);
        fixtureWallLineMix(grid, topology, bl, bt, br, bb, second, stores, 2, doodads, rng);
        counterAtDoorway(grid, bl, bt, br, bb,
                TileRegistry.installed().doodad("colony-furnishings.desk"), doodads);
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

    /** Modern civic desks, seating and server cabinets share the room-based furniture fitter. */
    private static void applyCivicHeadquarters(NavigationGrid grid, CellTopology topology,
                                                PartitionLayout partition,
                                                int bl, int bt, int br, int bb,
                                                List<Doodad> doodads, Random rng) {
        if (!partition.tacticalCivic) {
            NeighborhoodFurnishing.furnish(grid, topology, bl, bt, br, bb, doodads);
            return;
        }
        NeighborhoodFurnishing.furnish(grid, topology, bl, bt, br, bb, doodads);
        // Keep the shell's style stream stable when replacing its old furniture program.
        preservePlacementDrawsForPurposeRooms(grid, topology, bl, bt, br, bb, RoomPurpose.CIVIC_OFFICE, rng);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.CIVIC_RECEPTION, rng);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.CONFERENCE_ROOM, rng);
        preservePlacementDraw(topology, bl, bt, br, bb, RoomPurpose.SERVER_ROOM, rng);
    }

    /**
     * Fits complete examination and monitored ward bays, reception/waiting and
     * pharmacy storage. Working aprons stay free; ward ranks scale to the room
     * and narrow accommodation retains the compact bed fallback.
     */
    private static void applyMedicalClinic(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           List<Doodad> doodads, Random rng) {
        if (purposeBounds(topology, bl, bt, br, bb,
                RoomPurpose.MEDICAL_CORRIDOR) == null) return;

        // Charting belongs to triage; treatment and recovery are fitted around patients.
        stampPurposeConsoleBanks(grid, topology, bl, bt, br, bb, RoomPurpose.TRIAGE, doodads);
        BuildingWorkGroups groups = new BuildingWorkGroups(grid, topology, doodads);
        groups.fit(bl, bt, br, bb, RoomPurpose.MEDICAL_RECEPTION, BuildingWorkGroups.nurseStation(), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.MEDICAL_RECEPTION, BuildingWorkGroups.waitingSeats(), 2);
        int exams = groups.fit(bl, bt, br, bb, RoomPurpose.TREATMENT_ROOM, BuildingWorkGroups.examBay(),
                purposeFixtureBudget(topology, bl, bt, br, bb, RoomPurpose.TREATMENT_ROOM, 1, 3, 28));
        if (exams == 0) groups.fit(bl, bt, br, bb, RoomPurpose.TREATMENT_ROOM, BuildingWorkGroups.compactExam(), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.TREATMENT_ROOM, BuildingWorkGroups.sink(), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.TRIAGE, BuildingWorkGroups.compactExam(), 1);
        int[] ward = purposeBounds(topology, bl, bt, br, bb, RoomPurpose.PATIENT_WARD);
        int[] ranks = ward != null && ward[2] - ward[0] < ward[3] - ward[1] ? new int[]{1, 3} : new int[]{0, 2};
        int wardBeds = groups.fit(bl, bt, br, bb, RoomPurpose.PATIENT_WARD, BuildingWorkGroups.bedBay(),
                purposeFixtureBudget(topology, bl, bt, br, bb, RoomPurpose.PATIENT_WARD, 1, 8, 12), ranks);
        groups.fit(bl, bt, br, bb, RoomPurpose.PATIENT_WARD, BuildingWorkGroups.nurseStation(), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.nurseStation(), 1);
        int cabinets = groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.cabinets(2), 3);
        if (cabinets == 0) groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.cabinets(1), 2);

        // Preserve the narrow clinic's one-bed accommodation when a complete ward bay cannot fit.
        DoodadDef[] beds = {
                TileRegistry.installed().doodad("doodad.residential-bed-head-n"),
                TileRegistry.installed().doodad("doodad.residential-bed-v"),
                TileRegistry.installed().doodad("doodad.residential-bed-head-e"),
                TileRegistry.installed().doodad("doodad.residential-bed-h"),
        };

        if (wardBeds == 0) stampOneOrientedFixturePerPurposeRoom(grid, topology, bl, bt, br, bb,
                    RoomPurpose.PATIENT_WARD, beds, doodads, rng, true);
        else preservePlacementDrawsForPurposeRooms(grid, topology, bl, bt, br, bb, RoomPurpose.PATIENT_WARD, rng);
        for (RoomPurpose purpose : new RoomPurpose[]{RoomPurpose.MEDICAL_RECEPTION, RoomPurpose.TRIAGE,
                RoomPurpose.TREATMENT_ROOM, RoomPurpose.PHARMACY, RoomPurpose.PHARMACY})
            preservePlacementDraw(topology, bl, bt, br, bb, purpose, rng);
    }

    private static void applyMedicalSupport(NavigationGrid grid, CellTopology topology,
                                            int bl, int bt, int br, int bb, List<Doodad> doodads) {
        BuildingWorkGroups groups = new BuildingWorkGroups(grid, topology, doodads);
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.nurseStation(), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.sink(), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.cabinets(2), 2);
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.stockedRack("cartons"),
                Math.max(1, (br-bl-1)*(bb-bt-1)/65));
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.cargoDesk(true), 1);
        groups.fit(bl, bt, br, bb, RoomPurpose.PHARMACY, BuildingWorkGroups.pallets(1, "cases"), 2);
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
                    TileRegistry.installed().doodad("colony-furnishings.locker"),
                    doodads, rng, true);
        }
        if (!purposeHasFixture(topology, bl, bt, br, bb, RoomPurpose.APARTMENT_LIVING)) {
            stampPurposeFixture(grid, topology, bl, bt, br, bb,
                    RoomPurpose.APARTMENT_LIVING,
                    TileRegistry.installed().doodad("mess-galley.stool"),
                    doodads, rng, true);
        }
    }

    /** Gives the compact alley market one blocking rack and loose stockroom cover. */
    private static void applyDenseMarket(NavigationGrid grid, CellTopology topology,
                                         int bl, int bt, int br, int bb,
                                         List<Doodad> doodads, Random rng) {
        DoodadDef shelf = TileRegistry.installed().doodad("colony-furnishings.shelf");
        DoodadDef crate = TileRegistry.installed().doodad("colony-furnishings.case");
        DoodadDef counter = TileRegistry.installed().doodad("colony-furnishings.desk");
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

    /** Short complete banks use actual supporting walls, leaving two clear operator rows. */
    private static int stampPurposeConsoleBanks(NavigationGrid grid, CellTopology topology,
            int bl, int bt, int br, int bb, RoomPurpose purpose, List<Doodad> doodads) {
        DoodadDef probe = TileRegistry.installed().doodad("console-bank.straight");
        int[] bounds = purposeBounds(topology, bl, bt, br, bb, purpose);
        if (probe == null || bounds == null) return 0;
        int placed = 0;
        List<ConsoleBankLayout.Cell> operatorCells = new ArrayList<>();
        for (WallSide side : WallSide.values()) {
            List<int[]> available = purposeWallRow(grid, topology, bounds, purpose, probe, doodads, side);
            List<ConsoleBankLayout.Cell> run = new ArrayList<>();
            int inwardX = side == WallSide.W ? 1 : side == WallSide.E ? -1 : 0;
            int inwardY = side == WallSide.S ? 1 : side == WallSide.N ? -1 : 0;
            for (int index = 0; index <= available.size(); index++) {
                int[] cell = index < available.size() ? available.get(index) : null;
                boolean clear = cell != null && !operatorCells.contains(new ConsoleBankLayout.Cell(cell[0], cell[1]));
                if (clear) for (int depth = 1; depth <= 2; depth++) {
                    int x = cell[0] + inwardX * depth, y = cell[1] + inwardY * depth;
                    if (!grid.inBounds(x, y) || topology.getRoomPurpose(x, y) != purpose
                            || !grid.isWalkable(x, y) || topology.isFixture(x, y)
                            || isOccupied(x, y, doodads)) clear = false;
                }
                boolean adjacent = run.isEmpty() || cell != null &&
                        Math.abs(run.get(run.size() - 1).x() - cell[0])
                        + Math.abs(run.get(run.size() - 1).y() - cell[1]) == 1;
                if (!clear || !adjacent || run.size() == 5) {
                    if (run.size() >= 2 && BuildingConsoleBanks.place(grid, topology, run,
                            side == WallSide.N || side == WallSide.W, doodads)) {
                        for (ConsoleBankLayout.Cell occupied : run) for (int depth = 1; depth <= 2; depth++)
                            operatorCells.add(new ConsoleBankLayout.Cell(occupied.x() + inwardX * depth,
                                    occupied.y() + inwardY * depth));
                        if (++placed == 2) return placed;
                        clear = false; // Leave a gap between separately capped banks.
                    }
                    run.clear();
                }
                if (clear) run.add(new ConsoleBankLayout.Cell(cell[0], cell[1]));
            }
        }
        return placed;
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

}
