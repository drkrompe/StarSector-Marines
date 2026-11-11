package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.door.WallGate;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.SplittableRandom;

/**
 * Lays a {@link PortHub} on a map ({@code raid-site-templates.md}, spaceport): the paving over the hub's bands, the rail
 * line's tracks, the dock trucks at the warehouses' doors, the container stacks under their gantry cranes, the fuel
 * farm and its main past every pad, the pads with the scorch of every lift on them and the blast walls between them,
 * and the lighters standing on their pads: one mid-turnaround (a tanker at its hydrant, its cargo coming off), others
 * loaded and waiting, one on the heavy pad. A lighter is the planet's traffic and the defender's, never the
 * company's: its pad is a {@link LandingPad.Purpose#PORT_LIGHTER} stand, which no lift lands on.
 *
 * <p>Nothing is laid on a cell a building, a doorway or a road holds; the hub's ground is held clear of them before
 * the places grow, so in practice nothing but the warehouses is passed over.
 */
public final class PortHubDressing {

    /** The apron's pavement, and the pad's plate, its rim and the burn the lifts leave on it. */
    static final String PAD_PLATE = "sky-port.pad-plate";
    static final String CONCRETE = "installation.concrete-floor";
    static final String KEEP_CLEAR = "apron.keep-clear";
    /** A lift's burn on its pad, 5x4, and the lesser scorch marks round it, 2x2. */
    static final String BURN = "workshop-damage.scorch";
    static final String MARK = "ship-wear.scorch";
    /** The yellow line an airfield's berths are edged with. */
    static final String BERTH_EDGE = "doodad.fl-striped-yellow";
    /** A fuel tank in the farm: stand-in art, the listening post's pressure sphere (2x2). */
    static final String TANK = "doodad.oxygen-sphere";
    static final String TANKER = "doodad.parked-tanker-truck";
    static final String SKID = "shuttle-bay.fluid-skid";
    static final String CART = "shuttle-bay.service-cart";
    static final String CRATES = "doodad.industrial-crate-stack";
    static final String PALLETS = "doodad.industrial-pallet-stack";
    static final String[] BOXES = {"origin-freight.container-rust", "origin-freight.container-blue",
            "origin-freight.container-rust-clean", "origin-freight.container-blue-clean"};
    static final String CRANE_SPAN = "origin-freight.gantry-span";
    static final String CRANE_LEG = "origin-freight.gantry-leg";
    /** A blast wall's hit points: a deflector a lift's exhaust is turned by. UNTUNED. */
    static final int BLAST_WALL_HP = 160;
    /** Columns along the fuel main's rack, every this many cells. */
    static final int RACK_BAY = 5;
    /** Share of a pad's burn ring that is scorched: a ring, broken, not a painted circle. UNTUNED. */
    static final float SCORCH_SHARE = .45f;

    private PortHubDressing() { }

    /**
     * Lays {@code hub} on {@code c}. {@code seed} draws which pads hold freighters, the traffic and the scorch's breaks.
     *
     * @return the stands the freighters standing on the hub occupy, already added to {@code c.landingPads}
     */
    public static int lay(GenContext c, PortHub hub, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        TileRegistry registry = TileRegistry.installed();
        pave(c, hub.paved());
        pave(c, railBand(hub), BALLAST);
        highway(c, registry, hub);
        roads(c, registry, hub);
        rails(c, registry, hub);
        LandingPad.Approach out = hub.outerNorth() ? LandingPad.Approach.NORTH : LandingPad.Approach.SOUTH;
        int n = hub.pads().size();
        int turnaround = rng.nextInt(n);
        int stands = 0;
        for (int i = 0; i < n; i++) {
            PortHub.Rect pad = hub.pads().get(i);
            // One freighter mid-turnaround, and about every other pad holding another, loaded and waiting.
            boolean occupied = i == turnaround || rng.nextInt(OCCUPIED_ONE_IN) == 0;
            padMarks(c, registry, pad, occupied, rng);
            if (occupied) {
                stand(c, pad, out);
                stands++;
            }
        }
        for (PortHub.Rect wall : hub.blastWalls()) blastWall(c, wall);
        fuel(c, registry, hub);
        turnaround(c, registry, hub, hub.pads().get(turnaround), hub.hydrants().get(turnaround));
        staging(c, registry, hub, rng);
        apronControl(c, registry, hub);
        truckGate(c, registry, hub, rng);
        marshalling(c, registry, hub, rng);
        docks(c, registry, hub, rng);
        traffic(c, registry, hub, rng);
        return stands;
    }

    /** Pads in so many hold a freighter besides the one mid-turnaround. UNTUNED. */
    static final int OCCUPIED_ONE_IN = 2;

    /**
     * A pad's markings: its edge painted as an airfield berth's is, a landing ring inside it in keep-clear hatching
     * with the plate within, and on an empty pad the burns the lifts leave, some worn off. A pad with its freighter on
     * it shows the hull, not the burn.
     */
    static void padMarks(GenContext c, TileRegistry registry, PortHub.Rect pad, boolean occupied,
                         SplittableRandom rng) {
        int plate = c.topology.surfaceIndex(PAD_PLATE), keep = c.topology.surfaceIndex(KEEP_CLEAR);
        DoodadDef line = registry == null ? null : registry.doodad(BERTH_EDGE);
        float cx = (pad.x0() + pad.x1()) / 2f, cy = (pad.y0() + pad.y1()) / 2f;
        float r = Math.min(pad.width(), pad.height()) / 2f - 1.5f;
        for (int y = pad.y0(); y <= pad.y1(); y++) {
            for (int x = pad.x0(); x <= pad.x1(); x++) {
                if (!free(c, x, y)) continue;
                boolean edge = x == pad.x0() || x == pad.x1() || y == pad.y0() || y == pad.y1();
                double d = Math.hypot(x - cx, y - cy);
                if (edge && line != null) c.doodads.add(new Doodad(x, y, line));
                else if (d <= r && d > r - RING) c.topology.setSurface(x, y, keep);
                else if (d <= r - RING) c.topology.setSurface(x, y, plate);
            }
        }
        if (occupied || registry == null) return;
        DoodadDef burn = registry.doodad(BURN), mark = registry.doodad(MARK);
        int mx = Math.round(cx), my = Math.round(cy);
        if (burn != null) {
            int[][] burns = {{-2, -2}, {-5, -8}, {1, 5}};
            for (int[] at : burns) c.doodads.add(new Doodad(mx + at[0], my + at[1], burn, rng.nextInt(2) * 2));
        }
        if (mark != null) {
            int[][] ring = {{-6, -6}, {4, -6}, {-6, 4}, {4, 4}, {-1, -10}, {-1, 8}, {-8, 0}, {6, 0}};
            for (int[] at : ring) {
                if (rng.nextDouble() < SCORCH_SHARE) c.doodads.add(new Doodad(mx + at[0], my + at[1], mark));
            }
        }
    }

    /** Width of a pad's landing ring. */
    static final float RING = 2f;

    /** A freighter's stand at its pad's middle, its nose toward the outer edge it lifts over. */
    static void stand(GenContext c, PortHub.Rect pad, LandingPad.Approach out) {
        c.landingPads.add(new LandingPad(pad.centerX(), pad.centerY(), 1, 1, out, LandingPad.Purpose.PORT_LIGHTER));
    }

    /** The rail band: the hub's lines without the highway, which the layout paves as a street. */
    static PortHub.Rect railBand(PortHub hub) {
        PortHub.Rect l = hub.lines(), h = hub.highway();
        return hub.outerNorth() ? new PortHub.Rect(l.x0(), h.y1() + 1, l.x1(), l.y1())
                : new PortHub.Rect(l.x0(), l.y0(), l.x1(), h.y0() - 1);
    }

    /** The rail line's track art: a 2x2 piece drawn along y, turned to run along x. */
    static final String RAIL = "doodad.ruins-mag-rail";
    static final String RAIL_END = "doodad.ruins-mag-rail-end";

    /**
     * The tracks, from the deep edge (off the map, to the planet's other towns) back to the town end, where each
     * ends at its stop; broken where an arterial crosses them, a level crossing. Stand-in art: the ruins' mag-rail,
     * drawn along y, so turned a quarter to run along x.
     */
    static void rails(GenContext c, TileRegistry registry, PortHub hub) {
        if (registry == null) return;
        DoodadDef rail = registry.doodad(RAIL), end = registry.doodad(RAIL_END);
        if (rail == null) return;
        for (PortHub.Rect track : hub.tracks()) {
            int from = hub.deepEast() ? track.x0() : track.x1() - 1, step = hub.deepEast() ? 2 : -2;
            for (int x = from; x >= track.x0() && x + 1 <= track.x1(); x += step) {
                if (crossing(hub, x, track.y0()) || crossing(hub, x + 1, track.y1())) continue;
                if (!free(c, x, track.y0()) || !free(c, x + 1, track.y1())) continue;
                // The stop at the town end, its buffer toward the town.
                boolean stop = end != null && x == from;
                c.doodads.add(new Doodad(x, track.y0(), stop ? end : rail, hub.deepEast() ? 1 : 3));
            }
        }
    }

    /** Whether an arterial crosses the cell. */
    static boolean crossing(PortHub hub, int x, int y) {
        for (PortHub.Rect a : hub.arterials()) if (a.contains(x, y)) return true;
        return false;
    }

    /** Cells between two dashes' starts on an arterial's centre line. */
    static final int ARTERIAL_DASH = 4;

    /**
     * The hub's roads in asphalt, over the concrete and the rail bed: the arterials from the highway to the pads with
     * their dashed centre lines, and the roads along the hub joining them.
     */
    static void roads(GenContext c, TileRegistry registry, PortHub hub) {
        int road = c.topology.surfaceIndex(ROAD);
        DoodadDef paint = registry == null ? null : registry.doodad(BERTH_EDGE);
        for (PortHub.Rect r : hub.roads()) {
            for (int y = r.y0(); y <= r.y1(); y++) {
                for (int x = r.x0(); x <= r.x1(); x++) {
                    if (!free(c, x, y)) continue;
                    c.topology.setGroundKind(x, y, GroundKind.STREET);
                    c.topology.setSurface(x, y, road);
                    c.topology.setNatureOverlayIndex(x, y, -1);
                    c.markMadeGround(x, y);
                }
            }
        }
        if (paint == null) return;
        for (PortHub.Rect a : hub.arterials()) {
            int mx = a.centerX();
            for (int y = a.y0(); y <= a.y1(); y++) {
                if (Math.floorMod(y, ARTERIAL_DASH) < 2 && free(c, mx, y)) c.doodads.add(new Doodad(mx, y, paint));
            }
        }
    }

    /** The haulers' art: parked trucks, 3 by 2 along x. */
    static final String[] HAULER_ART = {"doodad.parked-cargo-truck", "doodad.parked-box-van",
            "doodad.parked-flatbed-truck"};

    /**
     * The truck gate: where the gate's road leaves the rail road into the warehouse band, a boom gate across each
     * lane, a jersey barrier down the middle between them, and the haulers off the highway queued before it.
     */
    static void truckGate(GenContext c, TileRegistry registry, PortHub hub, SplittableRandom rng) {
        if (registry == null) return;
        PortHub.Rect g = hub.gateRoad();
        boolean north = hub.outerNorth();
        // The gate line: inside the warehouse band, a truck's length and a gap past the rail road.
        int railEdge = north ? hub.railRoad().y1() : hub.railRoad().y0();
        int gy = railEdge + (north ? GATE_IN : -GATE_IN);
        int mid = g.centerX();
        if (free(c, g.x0(), gy)) StreetDressing.boomGate(c.grid, c.topology, c.doodads, g.x0(), gy, 1, 0);
        if (free(c, g.x1(), gy)) StreetDressing.boomGate(c.grid, c.topology, c.doodads, g.x1(), gy, -1, 0);
        if (free(c, mid, gy) && free(c, mid, gy + (north ? -1 : 1))) {
            StreetDressing.jerseyBarrier(c.grid, c.topology, c.doodads, mid, north ? gy - 1 : gy, false);
        }
        // Inbound haulers, nose to the gate, upright on the inbound lane before it.
        int lane = g.x0() + 1;
        for (int k = 0, y = gy - (north ? 4 : -2); k < GATE_QUEUE; k++, y += north ? -4 : 4) {
            if (!freeRect(c, lane, y, lane + 1, y + 2)) continue;
            DoodadDef def = registry.doodad(HAULER_ART[rng.nextInt(HAULER_ART.length)]);
            if (def == null) continue;
            fixtures(c, lane, y, lane + 1, y + 2, false);
            c.doodads.add(new Doodad(lane, y, def, north ? 1 : 3));
        }
    }

    /** Cells the truck gate stands inside the warehouse band, and the haulers queued before it. UNTUNED. */
    static final int GATE_IN = 7, GATE_QUEUE = 3;

    /**
     * The marshalling park: the near row's first bay given to the haulers off the highway waiting their call to a
     * warehouse or a pad, in angled ranks of upright trucks with a drive between ranks, some bays empty.
     */
    static void marshalling(GenContext c, TileRegistry registry, PortHub hub, SplittableRandom rng) {
        PortHub.Rect park = hub.marshalling();
        if (park == null || registry == null) return;
        int keep = c.topology.surfaceIndex(KEEP_CLEAR);
        // Two ranks of stalls along the park, a drive between them; stall lines in keep-clear paint.
        int[] rankY = hub.outerNorth() ? new int[]{park.y0() + 1, park.y1() - 3}
                : new int[]{park.y1() - 3, park.y0() + 1};
        for (int ry : rankY) {
            for (int x = park.x0() + 1; x + 1 <= park.x1() - 1; x += STALL) {
                for (int y = ry; y <= ry + 2; y++) if (free(c, x + 2, y)) c.topology.setSurface(x + 2, y, keep);
                if (rng.nextInt(4) == 0 || !freeRect(c, x, ry, x + 1, ry + 2)) continue;
                DoodadDef def = registry.doodad(HAULER_ART[rng.nextInt(HAULER_ART.length)]);
                if (def == null) continue;
                fixtures(c, x, ry, x + 1, ry + 2, false);
                c.doodads.add(new Doodad(x, ry, def, rng.nextBoolean() ? 1 : 3));
            }
        }
    }

    /** A marshalling stall's width: a truck's breadth and its stall line. */
    static final int STALL = 3;

    /** Trucks on each arterial, between the lines and the pads. UNTUNED. */
    static final int TRAFFIC = 2;

    /**
     * Haulers on the move along the arterials: a few to each, keeping to their lane, outbound on one side and inbound
     * on the other, so the roads read as worked.
     */
    static void traffic(GenContext c, TileRegistry registry, PortHub hub, SplittableRandom rng) {
        if (registry == null) return;
        for (int i = 1; i < hub.arterials().size(); i++) {
            PortHub.Rect a = hub.arterials().get(i);
            for (int k = 0; k < TRAFFIC; k++) {
                boolean outbound = rng.nextBoolean();
                int x = outbound ? a.x1() - 2 : a.x0() + 1;
                int y = a.y0() + 4 + rng.nextInt(Math.max(1, a.height() - 10));
                if (!freeRect(c, x, y, x + 1, y + 2)) continue;
                DoodadDef def = registry.doodad(HAULER_ART[rng.nextInt(HAULER_ART.length)]);
                if (def == null) continue;
                fixtures(c, x, y, x + 1, y + 2, false);
                // Outbound toward the pads, inbound toward the lines.
                c.doodads.add(new Doodad(x, y, def, outbound == hub.outerNorth() ? 1 : 3));
            }
        }
    }

    /** The trucks drawn up at the warehouses' doors, loading. */
    static final String[] DOCK_TRUCKS = {"doodad.parked-box-van", "doodad.parked-cargo-truck"};
    /** Warehouses in so many with a truck at the door. UNTUNED. */
    static final int DOCKED_ONE_IN = 2;

    /**
     * A truck at the door of about every other warehouse: beside the door, on the yard before it. A warehouse's door
     * is in the middle of its long side facing its road.
     */
    static void docks(GenContext c, TileRegistry registry, PortHub hub, SplittableRandom rng) {
        if (registry == null) return;
        for (PortHub.Shed shed : hub.sheds()) {
            if (rng.nextInt(DOCKED_ONE_IN) != 0) continue;
            DoodadDef def = registry.doodad(DOCK_TRUCKS[rng.nextInt(DOCK_TRUCKS.length)]);
            if (def == null) continue;
            PortHub.Rect at = shed.at();
            int x = at.centerX() + 3;
            // Upright, 2 by 3, from the yard's edge out over the road before the door's side.
            int y = shed.turns() == 0 ? at.y0() - 2 : at.y1();
            if (!freeRect(c, x, y, x + 1, y + 2)) continue;
            fixtures(c, x, y, x + 1, y + 2, false);
            c.doodads.add(new Doodad(x, y, def, 1));
        }
    }

    /** The highway's carriageway and its centre line; the rail line's bed. */
    static final String ROAD = "road.road";
    static final String BALLAST = "floors.stone";
    /** Cells between the starts of two dashes of the highway's centre line. */
    static final int DASH_PITCH = 4;

    /**
     * The highway: asphalt over the paving the layout laid along the lines, and its dashed centre line. Only street
     * the layout laid is surfaced, so where a building stood in its way the highway stops at it.
     */
    static void highway(GenContext c, TileRegistry registry, PortHub hub) {
        int road = c.topology.surfaceIndex(ROAD);
        PortHub.Rect h = hub.highway();
        DoodadDef paint = registry == null ? null : registry.doodad(BERTH_EDGE);
        for (int y = h.y0(); y <= h.y1(); y++) {
            for (int x = Math.max(1, h.x0()); x <= Math.min(c.width - 2, h.x1()); x++) {
                if (c.topology.getGroundKind(x, y) != GroundKind.STREET || c.topology.getBuildingId(x, y) != 0) continue;
                c.topology.setSurface(x, y, road);
                if (paint != null && y == h.centerY() && Math.floorMod(x, DASH_PITCH) < 2 && free(c, x, y)) {
                    c.doodads.add(new Doodad(x, y, paint));
                }
            }
        }
    }

    /** Paves the hub: concrete over its ground, nothing under a building, door or road changed. */
    static void pave(GenContext c, PortHub.Rect paved) {
        pave(c, paved, CONCRETE);
    }

    /** Paves {@code paved} in {@code surface}, nothing under a building, door or road changed. */
    static void pave(GenContext c, PortHub.Rect paved, String surface) {
        int concrete = c.topology.surfaceIndex(surface);
        // The country's scatter goes with the ground it lay on: rubble and stones the fill left are swept off.
        c.doodads.removeIf(d -> paved.contains(d.cellX, d.cellY) && free(c, d.cellX, d.cellY));
        for (int y = paved.y0(); y <= paved.y1(); y++) {
            for (int x = paved.x0(); x <= paved.x1(); x++) {
                if (!free(c, x, y)) continue;
                c.topology.setGroundKind(x, y, GroundKind.STREET);
                c.topology.setSurface(x, y, concrete);
                c.topology.setNatureOverlayIndex(x, y, -1);
                // Claimed against the landscape pass after it: no rock, scrub or drift is laid on made ground.
                c.markMadeGround(x, y);
            }
        }
    }

    /** One blast wall: standing concrete the height of a deflector, between two pads. */
    static void blastWall(GenContext c, PortHub.Rect wall) {
        int surface = c.topology.surfaceIndex(InstallationWalls.BUILDING);
        for (int y = wall.y0(); y <= wall.y1(); y++) {
            for (int x = wall.x0(); x <= wall.x1(); x++) {
                if (!free(c, x, y)) continue;
                c.grid.setWalkable(x, y, false);
                c.grid.setSeeThrough(x, y, false);
                c.grid.setWallHp(x, y, BLAST_WALL_HP);
                c.topology.setWall(x, y, true);
                c.topology.setFixture(x, y, false);
                c.topology.setSurface(x, y, surface);
                c.topology.setWallGroundSurface(x, y, "installation.concrete-floor");
            }
        }
    }

    /**
     * The fuel farm's tanks in their bund at the pad row's terminal end, and the main on its rack from the farm past
     * every pad, a hydrant skid where it serves each pad.
     */
    static void fuel(GenContext c, TileRegistry registry, PortHub hub) {
        if (registry == null) return;
        PortHub.Rect farm = hub.fuelFarm();
        DoodadDef tank = registry.doodad(TANK);
        int keep = c.topology.surfaceIndex(KEEP_CLEAR);
        for (int y = farm.y0(); y <= farm.y1(); y++) {
            for (int x = farm.x0(); x <= farm.x1(); x++) {
                boolean edge = x == farm.x0() || x == farm.x1() || y == farm.y0() || y == farm.y1();
                if (edge && free(c, x, y)) c.topology.setSurface(x, y, keep);
            }
        }
        // Pressure spheres, two by two, in the bund the keep-clear marks out. Stand-in art: a port's fuel tank.
        if (tank != null) {
            for (int ty = farm.y0() + 1; ty + 1 < farm.y1(); ty += 3) {
                for (int tx = farm.x0() + 1; tx + 1 < farm.x1(); tx += 3) {
                    if (!freeRect(c, tx, ty, tx + 1, ty + 1)) continue;
                    fixtures(c, tx, ty, tx + 1, ty + 1, false);
                    c.doodads.add(new Doodad(tx, ty, tank));
                }
            }
        }
        PortHub.Rect main = hub.fuelMain();
        // The main runs along one row, overhead on its rack, from beside the farm to past the last pad.
        int my = main.y0();
        if (!free(c, main.x0(), my) || !free(c, main.x1(), my)) return;
        StreetDressing.pipeRun(c.grid, c.topology, c.doodads, main.x0(), my, main.x1(), my, RACK_BAY);
        DoodadDef skid = registry.doodad(SKID);
        if (skid == null) return;
        for (int[] at : hub.hydrants()) {
            // The skid lies across the service lane from the main toward its pad, 2 cells by 4, turned along u.
            int y = hub.outerNorth() ? at[1] : at[1] - 3;
            if (!freeRect(c, at[0], y, at[0] + 1, y + 3)) continue;
            fixtures(c, at[0], y, at[0] + 1, y + 3, true);
            c.doodads.add(new Doodad(at[0], y, skid, 1));
        }
    }

    /**
     * The lighter mid-turnaround: a tanker drawn up at its hydrant, a cart beside its hold, and the cargo coming off
     * it stacked on the pad's near rim.
     */
    static void turnaround(GenContext c, TileRegistry registry, PortHub hub, PortHub.Rect pad, int[] hydrant) {
        if (registry == null) return;
        DoodadDef tanker = registry.doodad(TANKER), crates = registry.doodad(CRATES),
                pallets = registry.doodad(PALLETS), cart = registry.doodad(CART);
        boolean north = hub.outerNorth();
        // The tanker stands in the service lane beside the skid, along the lane (v): 3 by 2, unturned.
        int laneY = north ? pad.y0() - 3 : pad.y1() + 2;
        int tx = hydrant[0] + 3;
        if (tanker != null && freeRect(c, tx, laneY, tx + 2, laneY + 1)) {
            fixtures(c, tx, laneY, tx + 2, laneY + 1, false);
            c.doodads.add(new Doodad(tx, laneY, tanker));
        }
        // Cargo off the hold, on the pad's near rim either side of the stand's middle column.
        int rimY = north ? pad.y0() + 1 : pad.y1() - 1;
        int[][] stacks = {{pad.x0() + 2, rimY}, {pad.x0() + 3, rimY}, {pad.x1() - 2, rimY}, {pad.x1() - 3, rimY}};
        DoodadDef[] kinds = {crates, pallets, pallets, crates};
        for (int i = 0; i < stacks.length; i++) {
            if (kinds[i] == null || !free(c, stacks[i][0], stacks[i][1])) continue;
            fixtures(c, stacks[i][0], stacks[i][1], stacks[i][0], stacks[i][1], true);
            c.doodads.add(new Doodad(stacks[i][0], stacks[i][1], kinds[i]));
        }
        if (cart != null) {
            int cx = pad.centerX() + 3, cy = north ? pad.y0() + 2 : pad.y1() - 2;
            if (freeRect(c, cx, cy, cx + 1, cy)) {
                fixtures(c, cx, cy, cx + 1, cy, true);
                c.doodads.add(new Doodad(cx, cy, cart, 1));
            }
        }
    }

    /** The apron control post's radar, and the flood lamps on the blast walls. */
    static final String RADAR = "doodad.military-radar-dish";
    static final String FLOOD = "doodad.street-lamp-cap-industrial";

    /**
     * Apron control: the approach radar beside the fuel farm, at the pad row's terminal end, and a flood lamp at
     * each blast wall's ends, lighting the stands either side.
     */
    static void apronControl(GenContext c, TileRegistry registry, PortHub hub) {
        if (registry == null) return;
        DoodadDef radar = registry.doodad(RADAR), flood = registry.doodad(FLOOD);
        PortHub.Rect farm = hub.fuelFarm();
        int rx = hub.deepEast() ? farm.x0() + 1 : farm.x1() - 1;
        int ry = hub.outerNorth() ? farm.y1() + 2 : farm.y0() - 2;
        if (radar != null && free(c, rx, ry)) {
            fixtures(c, rx, ry, rx, ry, true);
            c.doodads.add(new Doodad(rx, ry, radar));
        }
        if (flood == null) return;
        for (PortHub.Rect wall : hub.blastWalls()) {
            for (int y : new int[]{wall.y0() - 1, wall.y1() + 1}) {
                if (!free(c, wall.x0(), y)) continue;
                fixtures(c, wall.x0(), y, wall.x0(), y, true);
                c.doodads.add(new Doodad(wall.x0(), y, flood));
            }
        }
    }

    /** The staging rows' containers, and the gantry cranes over them. */
    static void staging(GenContext c, TileRegistry registry, PortHub hub, SplittableRandom rng) {
        if (registry == null) return;
        for (PortHub.Rect box : hub.containers()) {
            // A gap in a row now and then: a box already gone to its lighter.
            if (rng.nextInt(6) == 0) continue;
            DoodadDef def = registry.doodad(BOXES[rng.nextInt(BOXES.length)]);
            if (def == null || !freeRect(c, box.x0(), box.y0(), box.x1(), box.y1())) continue;
            fixtures(c, box.x0(), box.y0(), box.x1(), box.y1(), false);
            // A container is 4 by 2 drawn along x, as the staging rows lie.
            c.doodads.add(new Doodad(box.x0(), box.y0(), def, box.height() > box.width() ? 1 : 0));
        }
        DoodadDef span = registry.doodad(CRANE_SPAN), leg = registry.doodad(CRANE_LEG);
        if (span == null || leg == null) return;
        for (PortHub.Rect crane : hub.cranes()) {
            // The crane straddles its block across the rows: a leg in the road before them and one in the lane
            // behind, each 3 cells along the rows; the span between, drawn along x, turned to run along y.
            if (!freeRect(c, crane.x0(), crane.y0(), crane.x1(), crane.y0())
                    || !freeRect(c, crane.x0(), crane.y1(), crane.x1(), crane.y1())) continue;
            for (int y : new int[]{crane.y0(), crane.y1()}) {
                fixtures(c, crane.x0(), y, crane.x1(), y, false);
                c.doodads.add(new Doodad(crane.x0(), y, leg, 1));
            }
            c.doodads.add(new Doodad(crane.x0(), crane.y0(), span, 1));
        }
    }

    /** Width of the terminal's rear gate onto the hub. */
    static final int REAR_GATE = 3;
    /** Furthest the rear lane walks from the hub toward the terminal before it gives up. */
    static final int REAR_REACH = 30;

    /**
     * The terminal's rear gate: the way the bonded cargo goes out to the yard road and staging. From the hub's edge on
     * the terminal's side, along the yard road's rows, a lane is paved across the cleared ground and opened through
     * the terminal's perimeter wall into its yard. Nothing is opened where the lane would meet a building first, or
     * where it finds no wall within {@link #REAR_REACH}.
     *
     * @return whether the gate was opened
     */
    public static boolean rearGate(GenContext c, PortHub hub) {
        int step = hub.deepEast() ? -1 : 1;
        int startX = hub.terminalX() + step;
        PortHub.Rect apron = hub.apron();
        int midY = hub.outerNorth() ? apron.y0() + PortHub.YARD_ROAD / 2 : apron.y1() - PortHub.YARD_ROAD / 2;
        int length = -1;
        // Measured on the middle row; the gate is that run, REAR_GATE rows wide.
        boolean walled = false;
        for (int k = 0; k < REAR_REACH; k++) {
            int x = startX + step * k;
            if (x < 1 || x >= c.width - 1) return false;
            for (int y = midY - REAR_GATE / 2; y <= midY + REAR_GATE / 2; y++) {
                if (c.topology.getBuildingId(x, y) != 0 || c.grid.isDoorway(x, y)) return false;
            }
            boolean wall = c.topology.isWall(x, midY);
            if (wall) walled = true;
            if (walled && !wall && c.grid.isWalkable(x, midY)) {
                length = k;
                break;
            }
        }
        if (length < 0) return false;
        int concrete = c.topology.surfaceIndex(CONCRETE);
        for (int k = 0; k < length; k++) {
            int x = startX + step * k;
            for (int y = midY - REAR_GATE / 2; y <= midY + REAR_GATE / 2; y++) {
                boolean wall = c.topology.isWall(x, y);
                if (!wall && !free(c, x, y)) continue;
                for (Direction d : new Direction[]{Direction.N, Direction.E, Direction.S, Direction.W}) {
                    c.grid.removeEdgeBarrier(x, y, d);
                }
                c.grid.setWalkable(x, y, true);
                c.grid.setSeeThrough(x, y, true);
                c.grid.setWallHp(x, y, 0);
                c.topology.setWall(x, y, false);
                c.topology.setWallDirMask(x, y, 0);
                c.topology.setFixture(x, y, false);
                c.topology.setGroundKind(x, y, GroundKind.STREET);
                c.topology.setSurface(x, y, concrete);
                c.topology.setNatureOverlayIndex(x, y, -1);
                c.markMadeGround(x, y);
            }
        }
        return true;
    }

    /** Haulers queued at the terminal's gates, nose to tail on the verge, waiting on customs. */
    static final String[] HAULERS = {"doodad.parked-cargo-truck", "doodad.parked-box-van", "doodad.parked-flatbed-truck"};
    /** Haulers queued at each gate the town roads come in by. UNTUNED. */
    static final int QUEUE = 3;
    /** Cells between one queued hauler's nose and the next's. */
    static final int QUEUE_PITCH = 5;

    /**
     * The intake queue ({@code raid-site-templates.md}, spaceport: the roads in from the planet's towns): at each of
     * the terminal's gates but the one onto the apron, haulers waiting on customs in a line along the verge of the
     * road out, from a few cells beyond the gate. A hauler stands only on free ground off the carriageway.
     *
     * @param claim  each cell's place index ({@code BspKeys.PRECINCT_CLAIM}), or null
     * @param works  the terminal's index in the plan
     * @param apronX a cell column on the hub's side of the terminal: the gate facing it holds no queue
     */
    public static void intakeQueue(GenContext c, int[][] claim, int works, int apronX, long seed) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || claim == null) return;
        SplittableRandom rng = new SplittableRandom(seed);
        for (WallGate gate : c.wallGates) {
            int gx = gate.spanX() ? (gate.min() + gate.max()) / 2 : gate.across();
            int gy = gate.spanX() ? gate.across() : (gate.min() + gate.max()) / 2;
            int ix = gx - gate.outX(), iy = gy - gate.outY();
            if (ix < 0 || iy < 0 || ix >= claim.length || iy >= claim[ix].length || claim[ix][iy] != works) continue;
            if (gate.outX() != 0 && Integer.signum(apronX - gx) == gate.outX()) continue;
            // The verge: beside the carriageway, on the side the queue keeps to.
            int side = gate.width() / 2 + 2;
            int placed = 0;
            for (int step = 4; step < 4 + QUEUE * QUEUE_PITCH * 2 && placed < QUEUE; step += QUEUE_PITCH) {
                DoodadDef def = registry.doodad(HAULERS[rng.nextInt(HAULERS.length)]);
                if (def == null) continue;
                int ax = gx + gate.outX() * step, ay = gy + gate.outY() * step;
                // A hauler is 3 by 2 along x; along a road out north or south it is turned upright.
                boolean upright = gate.outY() != 0;
                int x = upright ? ax + side : ax, y = upright ? ay : ay + side;
                int w = upright ? 2 : 3, h = upright ? 3 : 2;
                if (!freeRect(c, x, y, x + w - 1, y + h - 1)) continue;
                fixtures(c, x, y, x + w - 1, y + h - 1, false);
                c.doodads.add(new Doodad(x, y, def, upright ? 1 : 0));
                placed++;
            }
        }
    }

    private static void fixtures(GenContext c, int x0, int y0, int x1, int y1, boolean seeThrough) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                c.grid.setWalkable(x, y, false);
                c.grid.setSeeThrough(x, y, seeThrough);
                c.topology.setWall(x, y, false);
                c.topology.setFixture(x, y, true);
            }
        }
    }

    private static boolean freeRect(GenContext c, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (!free(c, x, y)) return false;
        return true;
    }

    /** Open ground the apron may lay on: walkable, inside the map, no building, door, fixture or road. */
    static boolean free(GenContext c, int x, int y) {
        if (x < 1 || y < 1 || x >= c.width - 1 || y >= c.height - 1) return false;
        if (!c.grid.isWalkable(x, y) || c.grid.isDoorway(x, y)) return false;
        if (c.topology.getBuildingId(x, y) != 0 || c.topology.isWall(x, y) || c.topology.isFixture(x, y)) return false;
        return c.topology.roadSurface() == null || c.topology.roadSurface().underlay(x, y) == null;
    }
}
