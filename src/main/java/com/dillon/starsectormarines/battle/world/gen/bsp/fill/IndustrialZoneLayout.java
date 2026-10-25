package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.SpineClaim;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The industrial zone layout ({@code set-piece-districts.md}) over a planned {@link SpineClaim.Spine}: every
 * member walled off from its neighbours and opening onto the one service street, so the street is the working
 * way through and each yard a short, walled sightline off it.
 *
 * <ul>
 *   <li>A <b>freight yard</b> member lays the {@link FreightYard} on its leaf, a <b>factory row</b> member the
 *       {@link FactoryRow}; either falls back to a warehouse where its leaf holds no pose.</li>
 *   <li>A <b>warehouse</b> stands behind a striped dock apron on the street, walled at both ends, its loading
 *       doors on the street alone ({@link BuildingPlacement#frontOnly}) and a hatched keep-clear before each;
 *       a deep leaf leaves a walled stock yard behind the hall. A hauler is parked along the apron's kerb away
 *       from the docks, and pipe runs on stands line what is left of the kerb.</li>
 *   <li>A <b>tank farm</b> or <b>vehicle depot</b> is a walled yard with one vehicle gate on the street: blocks
 *       of fluid tanks beside a clear drive, or haulers parked either side of it.</li>
 *   <li>A <b>guard post</b> keeps its hardpoint inside a wall with a narrow gate on the street.</li>
 * </ul>
 *
 * Every member but a yard or a row gives its row on the street to the street's sidewalk, so the carriageway
 * meets pavement rather than a wall and keeps its asphalt. The street itself is paved city street, and freight
 * gantries span it where both legs land on open apron.
 * Draws only from the map's random stream, inside the shell carves.
 */
final class IndustrialZoneLayout {

    /** Rows of sidewalk between the street and a member's walls. */
    static final int PAVEMENT = 1;
    /** Rows of striped dock apron between the street and a warehouse's front wall; fewer on a shallow member. */
    static final int APRON = 3, APRON_SHALLOW = 2;
    /** The least depth of a hall: a hollow shell with standable rooms behind its loading door. */
    static final int HALL_MIN = 6;
    /** The deepest hall; a member deeper than a hall and a stock yard keeps the yard. */
    static final int HALL_MAX = 12;
    /** The least depth of the stock yard behind a hall. */
    static final int STOCK_MIN = 4;
    /** The least member depth and frontage that takes a warehouse rather than a depot. */
    static final int WAREHOUSE_MIN_DEPTH = PAVEMENT + APRON_SHALLOW + HALL_MIN, WAREHOUSE_MIN_FRONT = 8;
    /** The least depth of any member: a walled yard behind its pavement. */
    static final int MEMBER_MIN_DEPTH = 8;
    /** The least frontage of a walled yard: its gate and the wall either side. */
    static final int YARD_MIN_FRONT = FreightYard.GATE + 4;
    /** Rows of keep-clear before a loading door, across its width and a cell either side. */
    static final int DOCK_ROWS = 2;
    /** Width of a loading door, and the hall length from which it has two. */
    static final int DOCK_DOOR = 3, TWO_DOCKS = 16;
    /** Rows inside a loading door kept clear of furniture. */
    static final int DOCK_INSIDE = 2;
    /** The least front of a walled yard whose gate is centred; a narrower one has it at one end. */
    static final int CENTRED_GATE = 14;
    /** Length of a parked hauler, and the least kerb run that takes one. */
    static final int HAULER = 3;
    /** Cells along the street from one gantry to the next, and kept clear of the street's ends. */
    static final int GANTRY_EVERY = 14, GANTRY_END = 4;
    /** The gantry's span across the street, and its depth along it. */
    static final int SPAN = 7, SPAN_DEPTH = 3;
    /** Width of a guard post's gate: people, not haulers. */
    static final int GATE_POST = 2;
    private static final int WALL_HP = 80;
    private static final FortifiedPostFiller POST = new FortifiedPostFiller();
    /** A warehouse hall: one open store of racks, as the freight yard's. */
    private static final BuildingShellCore.BuildingConfig HALL = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.INDUSTRIAL);
    private static final Direction[] CARDINALS = {Direction.N, Direction.E, Direction.S, Direction.W};

    private static final String KEEP_CLEAR = "apron.keep-clear";
    private static final String CRATES = "doodad.industrial-crate-stack";
    private static final String PALLETS = "doodad.industrial-pallet-stack";
    private static final String DRUMS = "doodad.industrial-drum-cluster";
    private static final String TANK = "doodad.industrial-fluid-tank";
    private static final String PIPE_RUN = "origin-workings.pipe-run-clean";
    private static final String GANTRY_SPAN = "origin-freight.gantry-span-clean";
    private static final String GANTRY_LEG = "origin-freight.gantry-leg-clean";
    private static final String TANKER = "doodad.parked-tanker-truck";
    /** The haulers parked along the street and in the depots, in turn. */
    private static final String[] HAULERS = {"doodad.parked-cargo-truck", "doodad.parked-flatbed-truck",
            "doodad.parked-utility-truck", "doodad.parked-tanker-truck", "doodad.parked-box-van"};

    private IndustrialZoneLayout() { }

    /** Lays the zone over {@code district}. Publishes its mark. */
    static IndustrialZone place(GenContext c, District district) {
        SpineClaim.Spine s = (SpineClaim.Spine) district.claim;
        TileRegistry registry = TileRegistry.installed();
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        List<LoadingBay> bays = new ArrayList<>();
        List<ServiceLane> lanes = new ArrayList<>();
        List<IndustrialZone.Dock> docks = new ArrayList<>();
        int hauler = 0;
        int depot = 0;
        for (BlockLeaf leaf : s.members()) {
            District.Role role = district.roles.get(leaf);
            if (role == District.Role.FREIGHT_YARD) {
                FreightYard.Pose pose = FreightYard.choose(c, leaf.left, leaf.top, leaf.right, leaf.bottom,
                        leaf::contains);
                if (pose != null) {
                    IndustrialYardFiller.paintOpenDirt(leaf, c.grid, c.topology);
                    bays.addAll(FreightYard.place(c, pose));
                    continue;
                }
            } else if (role == District.Role.FACTORY_ROW) {
                FactoryRow.Pose pose = FactoryRow.choose(c, leaf.left, leaf.top, leaf.right, leaf.bottom,
                        frame(c, leaf, road));
                if (pose != null) {
                    IndustrialYardFiller.paintOpenDirt(leaf, c.grid, c.topology);
                    lanes.add(FactoryRow.place(c, pose));
                    continue;
                }
            } else if (role == District.Role.TANK_FARM || role == District.Role.VEHICLE_DEPOT) {
                if (s.alongHi(leaf) - s.alongLo(leaf) + 1 >= YARD_MIN_FRONT) {
                    walledYard(c, s, leaf, role == District.Role.TANK_FARM, registry, depot++);
                    continue;
                }
            } else if (role == District.Role.SECURITY_POST) {
                securityPost(c, s, leaf);
                continue;
            }
            warehouse(c, s, leaf, registry, docks, hauler++);
        }
        pavedCrossStreets(c, s, road);
        carriageway(c, s, road);
        gantries(c, s, road, registry);

        int mid = (s.lo + s.hi + 1) / 2;
        IndustrialZone zone = new IndustrialZone(s.x(s.from, mid), s.y(s.from, mid), s.x(s.to + 1, mid),
                s.y(s.to + 1, mid), s.streetWidth(), bays, lanes, docks);
        c.industrialZones.add(zone);
        return zone;
    }

    /** The ground a factory row may stand on: the member, and the street frame round it nobody owns. */
    private static BiPredicate<Integer, Integer> frame(GenContext c, BlockLeaf leaf,
                                                      boolean[][] road) {
        Bsp.Partition partition = c.get(BspKeys.PARTITION);
        return (x, y) -> {
            if (leaf.contains(x, y)) return true;
            if (road == null || road[x][y] || partition == null) return false;
            for (BlockLeaf other : partition.leaves) if (other.contains(x, y)) return false;
            return true;
        };
    }

    /** Which way a member's front faces: onto the street. */
    static BuildingPlacement.Side side(SpineClaim.Spine s, BlockLeaf leaf) {
        boolean near = s.onNearSide(leaf);
        if (s.horizontal) return near ? BuildingPlacement.Side.BOTTOM : BuildingPlacement.Side.TOP;
        return near ? BuildingPlacement.Side.RIGHT : BuildingPlacement.Side.LEFT;
    }

    /** The dock apron's rows for a member {@code depth} deep: the full apron where a full hall stands behind it. */
    static int apron(int depth) {
        return depth >= APRON + HALL_MIN + 1 ? APRON : APRON_SHALLOW;
    }

    /**
     * The hall's depth for a member {@code depth} deep: everything behind the apron, unless that leaves room
     * for the deepest hall and a stock yard behind it.
     */
    static int hallDepth(int depth) {
        int rest = depth - apron(depth);
        return rest >= HALL_MAX + STOCK_MIN ? HALL_MAX : rest;
    }

    /**
     * A warehouse behind its dock apron, walled at the apron's ends, its loading doors on the street alone,
     * with a stock yard behind it where the member is deep enough. Adds a dock per loading door.
     */
    private static void warehouse(GenContext c, SpineClaim.Spine s, BlockLeaf leaf, TileRegistry registry,
                                  List<IndustrialZone.Dock> docks, int turn) {
        Frontage f = frontage(c, s, leaf);
        int aLo = f.aLo, aHi = f.aHi, kerb = f.kerb, step = f.step, depth = f.depth;
        int apron = apron(depth), hall = hallDepth(depth);
        for (int a = aLo; a <= aHi; a++) {
            for (int k = 0; k < apron; k++) ground(c, s.x(a, kerb + step * k), s.y(a, kerb + step * k), GroundKind.STRIPED);
        }
        for (int k = 0; k < apron; k++) {
            wall(c, s.x(aLo, kerb + step * k), s.y(aLo, kerb + step * k));
            wall(c, s.x(aHi, kerb + step * k), s.y(aHi, kerb + step * k));
        }
        int wallRow = kerb + step * apron, backRow = kerb + step * (apron + hall - 1);
        int bLo = Math.min(wallRow, backRow), bHi = Math.max(wallRow, backRow);
        BlockLeaf shell = s.horizontal ? new BlockLeaf(aLo, bLo, aHi, bHi, false)
                : new BlockLeaf(bLo, aLo, bHi, aHi, false);
        PointOfInterest poi = BuildingShellCore.carve(shell, c.grid, c.topology, c.doodads, c.rng,
                HALL, BuildingPlacement.frontOnly(side(s, leaf)));
        if (poi != null) c.pois.add(poi);
        loadingDoors(c, s, aLo, aHi, wallRow, step);
        if (depth - apron - hall >= STOCK_MIN) stockYard(c, s, f, apron + hall, registry);

        // The docks: a keep-clear before each loading door, nothing parked or stacked before any door.
        boolean[] clear = new boolean[aHi - aLo + 1];
        clear[0] = clear[1] = clear[clear.length - 1] = clear[clear.length - 2] = true;
        int keepClear = c.topology.surfaceIndex(KEEP_CLEAR);
        int run = -1;
        for (int a = aLo; a <= aHi + 1; a++) {
            boolean door = a <= aHi && c.grid.isDoorway(s.x(a, wallRow), s.y(a, wallRow));
            if (door && run < 0) run = a;
            if (door || run < 0) continue;
            int middle = (run + a - 1) / 2;
            boolean dock = a - run >= DOCK_DOOR;
            if (dock) {
                docks.add(new IndustrialZone.Dock(s.x(middle, wallRow), s.y(middle, wallRow),
                        s.x(middle, f.street), s.y(middle, f.street)));
            }
            for (int d = run - 1; d <= a; d++) {
                if (d <= aLo || d >= aHi) continue;
                clear[d - aLo] = true;
                for (int k = apron - DOCK_ROWS; k < apron && dock; k++) {
                    c.topology.setSurface(s.x(d, kerb + step * k), s.y(d, kerb + step * k), keepClear);
                }
            }
            run = -1;
        }
        // A hauler along the apron, away from the docks; pipe runs on stands along the rest, with a way through.
        int spot = kerbRun(clear, HAULER, aLo, (aLo + aHi) / 2);
        if (spot >= 0) {
            vehicle(c, s, spot, kerb, step, registry.doodad(HAULERS[turn % HAULERS.length]));
            for (int a = spot - 1; a <= spot + HAULER; a++) if (a >= aLo && a <= aHi) clear[a - aLo] = true;
        }
        DoodadDef pipe = registry.doodad(PIPE_RUN);
        for (int a = aLo; a + 2 < aHi && pipe != null; a++) {
            if (clear[a - aLo] || clear[a + 1 - aLo] || clear[a + 2 - aLo]) continue;
            int x0 = Math.min(s.x(a, kerb), s.x(a + 2, kerb)), y0 = Math.min(s.y(a, kerb), s.y(a + 2, kerb));
            for (int i = 0; i < 3; i++) fixture(c, s.x(a + i, kerb), s.y(a + i, kerb));
            c.doodads.add(new Doodad(x0, y0, pipe, s.horizontal ? 0 : 1));
            for (int i = 0; i < 5 && a + i <= aHi; i++) clear[a + i - aLo] = true;
        }
        // Freight stacked at the apron's ends against the end walls.
        int inner = kerb + step * (apron - 1);
        for (int a : new int[]{aLo + 1, aHi - 1}) {
            int x = s.x(a, inner), y = s.y(a, inner);
            if (!c.grid.isWalkable(x, y) || c.topology.getSurface(x, y) == keepClear) continue;
            solid(c, x, y, registry.doodad(CRATES));
        }
    }

    /**
     * Loading doors {@link #DOCK_DOOR} wide in a hall's front wall on row {@code wallRow}: one, or two on a hall
     * long enough, each nearest its share of the frontage where the {@link #DOCK_INSIDE} rows inside it are floor
     * or furniture, never a room's wall. Furniture standing inside a door is taken out, so the door opens onto a
     * clear bay.
     */
    static void loadingDoors(GenContext c, SpineClaim.Spine s, int aLo, int aHi, int wallRow, int step) {
        int length = aHi - aLo + 1, count = length >= TWO_DOCKS ? 2 : 1;
        for (int i = 0; i < count; i++) {
            int want = aLo + length * (2 * i + 1) / (2 * count) - DOCK_DOOR / 2;
            int best = -1;
            for (int a0 = aLo + 1; a0 + DOCK_DOOR - 1 <= aHi - 1; a0++) {
                boolean open = true;
                for (int a = a0; a < a0 + DOCK_DOOR && open; a++) {
                    for (int k = 1; k <= DOCK_INSIDE && open; k++) {
                        int x = s.x(a, wallRow + step * k), y = s.y(a, wallRow + step * k);
                        open = (c.grid.isWalkable(x, y) || c.topology.isFixture(x, y)) && !c.topology.isWall(x, y)
                                && !c.grid.isDoorway(x, y);
                    }
                    open &= c.grid.isWalkable(s.x(a, wallRow - step), s.y(a, wallRow - step))
                            && !c.grid.isDoorway(s.x(a, wallRow), s.y(a, wallRow));
                }
                if (open && (best < 0 || Math.abs(a0 - want) < Math.abs(best - want))) best = a0;
            }
            if (best < 0) continue;
            for (int a = best; a < best + DOCK_DOOR; a++) {
                for (int k = 1; k <= DOCK_INSIDE; k++) unfurnish(c, s.x(a, wallRow + step * k), s.y(a, wallRow + step * k));
                int x = s.x(a, wallRow), y = s.y(a, wallRow);
                // A window the facade put here gives way to the door.
                for (Direction d : CARDINALS) c.grid.removeEdgeBarrier(x, y, d);
                c.grid.setWalkableFloor(x, y);
                c.grid.openAllEdges(x, y);
                c.grid.setDoorway(x, y, true);
                c.topology.setWindow(x, y, false);
                c.topology.setWall(x, y, false);
                c.topology.setWallDirMask(x, y, 0);
                c.topology.setGroundKind(x, y, HALL.interiorGround);
                c.topology.setBuildingKindHint(x, y, HALL.buildingKind);
            }
        }
    }

    /** Takes out whatever furniture stands on {@code (x, y)}, freeing every cell it stood on. */
    private static void unfurnish(GenContext c, int x, int y) {
        if (!c.topology.isFixture(x, y)) return;
        c.doodads.removeIf(d -> {
            if (x < d.cellX || y < d.cellY || x >= d.cellX + d.footprintCellsX || y >= d.cellY + d.footprintCellsY) {
                return false;
            }
            for (int fy = d.cellY; fy < d.cellY + d.footprintCellsY; fy++) {
                for (int fx = d.cellX; fx < d.cellX + d.footprintCellsX; fx++) {
                    c.grid.setWalkable(fx, fy, true);
                    c.topology.setFixture(fx, fy, false);
                }
            }
            return true;
        });
        c.grid.setWalkable(x, y, true);
        c.topology.setFixture(x, y, false);
    }

    /**
     * The walled stock yard behind a hall, from row {@code from} to the back of the member: tanks and freight
     * along its back wall, and a gap in one end wall onto whichever cross street it backs onto.
     */
    private static void stockYard(GenContext c, SpineClaim.Spine s, Frontage f, int from, TileRegistry registry) {
        int aLo = f.aLo, aHi = f.aHi, kerb = f.kerb, step = f.step, depth = f.depth;
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        int gapAt = -1;
        for (int a : new int[]{aLo, aHi}) {
            int out = a == aLo ? aLo - 1 : aHi + 1;
            int mid = kerb + step * ((from + depth - 1) / 2);
            int x = s.x(out, mid), y = s.y(out, mid);
            if (gapAt < 0 && road != null && c.grid.inBounds(x, y) && road[x][y]) gapAt = a;
        }
        for (int k = from; k < depth; k++) {
            for (int a = aLo; a <= aHi; a++) {
                int x = s.x(a, kerb + step * k), y = s.y(a, kerb + step * k);
                boolean ring = a == aLo || a == aHi || k == depth - 1;
                boolean gap = a == gapAt && (k == from + 1 || k == from + 2);
                if (ring && !gap) wall(c, x, y);
                else ground(c, x, y, GroundKind.DIRT);
            }
        }
        int back = depth - 2;
        for (int a = aLo + 2; a <= aHi - 2; a++) {
            String id = (a - aLo) % 4 == 2 ? TANK : (a - aLo) % 4 == 0 ? PALLETS : null;
            if (id != null) solid(c, s.x(a, kerb + step * back), s.y(a, kerb + step * back), registry.doodad(id));
        }
    }

    /**
     * A walled yard with one vehicle gate on its street front: a tank farm (blocks of four fluid tanks on bunded
     * earth beside a clear drive, a tanker parked at the drive's end) or a vehicle depot (haulers parked nose to
     * the drive on striped hardstanding). The gate is centred on a wide front and at one end of a narrow one,
     * so the tanks or haulers fill the rest.
     */
    private static void walledYard(GenContext c, SpineClaim.Spine s, BlockLeaf leaf, boolean tanks,
                                   TileRegistry registry, int turn) {
        Frontage f = frontage(c, s, leaf);
        int aLo = f.aLo, aHi = f.aHi, kerb = f.kerb, step = f.step, depth = f.depth;
        int front = aHi - aLo + 1;
        int gateLo = front >= CENTRED_GATE ? (aLo + aHi + 1) / 2 - FreightYard.GATE / 2
                : (turn % 2 == 0 ? aLo + 1 : aHi - FreightYard.GATE);
        int gateHi = gateLo + FreightYard.GATE - 1;
        for (int k = 0; k < depth; k++) {
            for (int a = aLo; a <= aHi; a++) {
                int x = s.x(a, kerb + step * k), y = s.y(a, kerb + step * k);
                boolean ring = a == aLo || a == aHi || k == 0 || k == depth - 1;
                boolean drive = a >= gateLo && a <= gateHi;
                if (ring && !(k == 0 && drive)) wall(c, x, y);
                else ground(c, x, y, drive || !tanks ? GroundKind.STRIPED : GroundKind.DIRT);
            }
        }
        if (tanks) {
            DoodadDef tank = registry.doodad(TANK);
            for (int k = 2; k < depth - 1; k++) {
                for (int a = aLo + 1; a < aHi; a++) {
                    if (a >= gateLo - 1 && a <= gateHi + 1) continue;
                    int inA = a < gateLo ? gateLo - 2 - a : a - gateHi - 2, inK = k - 2;
                    if (inA % 3 == 2 || inK % 3 == 2) continue;
                    solid(c, s.x(a, kerb + step * k), s.y(a, kerb + step * k), tank);
                }
            }
            int end = depth - 1 - HAULER;
            if (end >= 3) vehicleAcross(c, s, gateLo + 1, kerb + step * end, step, registry.doodad(TANKER));
            return;
        }
        // Fuel drums along the back wall, then haulers either side of the drive, a free row between each pair.
        int back = kerb + step * (depth - 2);
        for (int a = aLo + 1; a < aHi; a += 3) {
            if (a < gateLo - 1 || a > gateHi + 1) solid(c, s.x(a, back), s.y(a, back), registry.doodad(DRUMS));
        }
        int n = turn;
        for (int k = 2; k + 1 < depth - 2; k += 3) {
            for (int side = 0; side < 2; side++) {
                int a0 = side == 0 ? gateLo - 1 - HAULER : gateHi + 2;
                if (a0 <= aLo || a0 + HAULER - 1 >= aHi) continue;
                int row = kerb + step * (step < 0 ? k + 1 : k);
                vehicle(c, s, a0, row, 1, registry.doodad(HAULERS[n++ % HAULERS.length]));
            }
        }
    }

    /**
     * A guard post kept as it would have stood, inside a wall round its leaf with a gate onto the street: the
     * post's own hardpoint and turret nest, on striped ground.
     */
    private static void securityPost(GenContext c, SpineClaim.Spine s, BlockLeaf leaf) {
        Frontage f = frontage(c, s, leaf);
        int gateLo = (f.aLo + f.aHi + 1) / 2 - GATE_POST / 2, gateHi = gateLo + GATE_POST - 1;
        for (int k = 0; k < f.depth; k++) {
            for (int a = f.aLo; a <= f.aHi; a++) {
                int x = s.x(a, f.kerb + f.step * k), y = s.y(a, f.kerb + f.step * k);
                boolean ring = a == f.aLo || a == f.aHi || k == 0 || k == f.depth - 1;
                if (ring && !(k == 0 && a >= gateLo && a <= gateHi)) wall(c, x, y);
                else ground(c, x, y, GroundKind.STRIPED);
            }
        }
        int c0 = f.kerb + f.step, c1 = f.kerb + f.step * (f.depth - 2);
        int x0 = Math.min(s.x(f.aLo + 1, c0), s.x(f.aHi - 1, c1)), y0 = Math.min(s.y(f.aLo + 1, c0), s.y(f.aHi - 1, c1));
        int x1 = Math.max(s.x(f.aLo + 1, c0), s.x(f.aHi - 1, c1)), y1 = Math.max(s.y(f.aLo + 1, c0), s.y(f.aHi - 1, c1));
        POST.fill(new BlockLeaf(x0, y0, x1, y1, false), c);
    }

    /**
     * A member seen from the street: cleared, its row on the street paved as the street's sidewalk (so the
     * carriageway keeps its asphalt rather than meeting a wall), and the rows behind it counted from
     * {@code kerb} by {@code step}.
     */
    private record Frontage(int aLo, int aHi, int street, int kerb, int step, int depth) {
    }

    private static Frontage frontage(GenContext c, SpineClaim.Spine s, BlockLeaf leaf) {
        clear(c, leaf);
        boolean near = s.onNearSide(leaf);
        int aLo = s.alongLo(leaf), aHi = s.alongHi(leaf), step = near ? -1 : 1;
        int edge = near ? s.acrossHi(leaf) : s.acrossLo(leaf);
        for (int a = aLo; a <= aHi; a++) ground(c, s.x(a, edge), s.y(a, edge), GroundKind.SIDEWALK);
        int depth = s.acrossHi(leaf) - s.acrossLo(leaf) + 1 - PAVEMENT;
        return new Frontage(aLo, aHi, edge - step, edge + step * PAVEMENT, step, depth);
    }

    /** The cells the rewrite took from the cross streets: apron, the member past them walled behind it. */
    private static void pavedCrossStreets(GenContext c, SpineClaim.Spine s, boolean[][] road) {
        for (SpineClaim.Gap gap : s.gaps) {
            for (int a = gap.from(); a <= gap.to(); a++) {
                for (int cc = gap.lo(); cc <= gap.hi(); cc++) {
                    int x = s.x(a, cc), y = s.y(a, cc);
                    if (road[x][y]) continue;
                    ground(c, x, y, GroundKind.SIDEWALK);
                }
            }
        }
    }

    /**
     * The service street and the stubs of its cross streets are paved city street, wherever the settlement would
     * have left a lane of its own ground: the street finish then lays its asphalt over them like any other.
     */
    private static void carriageway(GenContext c, SpineClaim.Spine s, boolean[][] road) {
        for (int a = s.from; a <= s.to; a++) {
            for (int cc = s.lo; cc <= s.hi; cc++) street(c, road, s.x(a, cc), s.y(a, cc));
        }
        for (SpineClaim.Gap gap : s.gaps) {
            for (int a = gap.from(); a <= gap.to(); a++) {
                for (int cc = gap.lo(); cc <= gap.hi(); cc++) street(c, road, s.x(a, cc), s.y(a, cc));
            }
        }
    }

    /**
     * Freight gantries over the street every {@link #GANTRY_EVERY} cells: the span centred across the
     * carriageway, coverless, and a leg at each end on open ground of the members either side. A gantry whose
     * legs would stand on a wall, a door, a keep-clear or a cross street is left out.
     */
    private static void gantries(GenContext c, SpineClaim.Spine s, boolean[][] road, TileRegistry registry) {
        DoodadDef span = registry.doodad(GANTRY_SPAN), leg = registry.doodad(GANTRY_LEG);
        if (span == null || leg == null) return;
        int keepClear = c.topology.surfaceIndex(KEEP_CLEAR);
        int c0 = s.lo - (SPAN - s.streetWidth()) / 2, legLo = c0 - 1, legHi = c0 + SPAN;
        int rotation = s.horizontal ? 1 : 0;
        for (int a0 = s.from + GANTRY_END; a0 + SPAN_DEPTH - 1 <= s.to - GANTRY_END; ) {
            boolean ok = true;
            for (int a = a0; a < a0 + SPAN_DEPTH && ok; a++) {
                for (int cc = c0; cc < c0 + SPAN && ok; cc++) {
                    int x = s.x(a, cc), y = s.y(a, cc);
                    ok = c.grid.isWalkable(x, y) && !c.topology.isFixture(x, y);
                }
                for (int cc : new int[]{legLo, legHi}) {
                    int x = s.x(a, cc), y = s.y(a, cc);
                    ok &= c.grid.inBounds(x, y) && !road[x][y] && c.grid.isWalkable(x, y)
                            && !c.grid.isDoorway(x, y) && !c.topology.isFixture(x, y)
                            && c.topology.getSurface(x, y) != keepClear && owned(s, x, y);
                }
            }
            if (!ok) {
                a0++;
                continue;
            }
            int sx = Math.min(s.x(a0, c0), s.x(a0 + SPAN_DEPTH - 1, c0 + SPAN - 1));
            int sy = Math.min(s.y(a0, c0), s.y(a0 + SPAN_DEPTH - 1, c0 + SPAN - 1));
            c.doodads.add(new Doodad(sx, sy, span, rotation));
            for (int cc : new int[]{legLo, legHi}) {
                for (int a = a0; a < a0 + SPAN_DEPTH; a++) fixture(c, s.x(a, cc), s.y(a, cc));
                c.doodads.add(new Doodad(Math.min(s.x(a0, cc), s.x(a0 + SPAN_DEPTH - 1, cc)),
                        Math.min(s.y(a0, cc), s.y(a0 + SPAN_DEPTH - 1, cc)), leg, rotation));
            }
            a0 += GANTRY_EVERY;
        }
    }

    /** Whether {@code (x, y)} is on a member of the spine. */
    private static boolean owned(SpineClaim.Spine s, int x, int y) {
        for (BlockLeaf leaf : s.members()) if (leaf.contains(x, y)) return true;
        return false;
    }

    /** The first run of {@code length} unclaimed kerb cells, nearest the middle of the frontage, or -1. */
    static int kerbRun(boolean[] clear, int length, int aLo, int centre) {
        int best = -1;
        for (int i = 0; i + length <= clear.length; i++) {
            boolean free = true;
            for (int k = 0; k < length && free; k++) free = !clear[i + k];
            if (!free) continue;
            int a = aLo + i;
            if (best < 0 || Math.abs(a + length / 2 - centre) < Math.abs(best + length / 2 - centre)) best = a;
        }
        return best;
    }

    /** A hauler parked along the street from {@code a0}, on row {@code row} and the next one in. */
    private static void vehicle(GenContext c, SpineClaim.Spine s, int a0, int row, int step, DoodadDef def) {
        if (def == null) return;
        int a1 = a0 + HAULER - 1, r1 = row + step;
        int minX = Math.min(s.x(a0, row), s.x(a1, r1)), minY = Math.min(s.y(a0, row), s.y(a1, r1));
        Doodad prop = new Doodad(minX, minY, def, s.horizontal ? 0 : 1);
        for (int y = minY; y < minY + prop.footprintCellsY; y++) {
            for (int x = minX; x < minX + prop.footprintCellsX; x++) fixture(c, x, y);
        }
        c.doodads.add(prop);
    }

    /** A hauler parked across the street's direction, two cells wide from {@code a0}, from row {@code row} in. */
    private static void vehicleAcross(GenContext c, SpineClaim.Spine s, int a0, int row, int step, DoodadDef def) {
        if (def == null) return;
        int r1 = row + step * (HAULER - 1);
        int minX = Math.min(s.x(a0, row), s.x(a0 + 1, r1)), minY = Math.min(s.y(a0, row), s.y(a0 + 1, r1));
        Doodad prop = new Doodad(minX, minY, def, s.horizontal ? 1 : 0);
        for (int y = minY; y < minY + prop.footprintCellsY; y++) {
            for (int x = minX; x < minX + prop.footprintCellsX; x++) fixture(c, x, y);
        }
        c.doodads.add(prop);
    }

    /** Clears a member to open floor before its role is laid on it. */
    private static void clear(GenContext c, BlockLeaf leaf) {
        c.doodads.removeIf(d -> d.cellX <= leaf.right && d.cellX + d.footprintCellsX - 1 >= leaf.left
                && d.cellY <= leaf.bottom && d.cellY + d.footprintCellsY - 1 >= leaf.top);
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                c.grid.setWalkableFloor(x, y);
                c.grid.openAllEdges(x, y);
                c.topology.setFixture(x, y, false);
                c.topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
    }

    private static void ground(GenContext c, int x, int y, GroundKind kind) {
        c.grid.setWalkableFloor(x, y);
        c.grid.openAllEdges(x, y);
        c.topology.setFixture(x, y, false);
        c.topology.setGroundKind(x, y, kind);
        if (kind != GroundKind.SIDEWALK) c.markMadeGround(x, y);
    }

    private static void wall(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.grid.setWallHp(x, y, WALL_HP);
        c.topology.setGroundKind(x, y, GroundKind.INDOOR);
        c.markMadeGround(x, y);
    }

    private static void street(GenContext c, boolean[][] road, int x, int y) {
        if (!road[x][y]) return;
        c.topology.setGroundKind(x, y, GroundKind.STREET);
        c.topology.setSurface(x, y, 0);
        c.topology.setNatureOverlayIndex(x, y, -1);
    }

    private static void fixture(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, true);
        c.topology.setFixture(x, y, true);
    }

    private static void solid(GenContext c, int x, int y, DoodadDef def) {
        if (def == null) return;
        fixture(c, x, y);
        c.doodads.add(new Doodad(x, y, def));
    }
}
