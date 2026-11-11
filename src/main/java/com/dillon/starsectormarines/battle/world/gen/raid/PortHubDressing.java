package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.door.WallGate;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.SplittableRandom;

/**
 * Lays a {@link PortHub} on a map ({@code raid-site-templates.md}, spaceport): the paving over the hub's bands, its roads
 * as street for the city's road finish, the rail line's tracks, the dock trucks at the warehouses' doors, the container
 * stacks under their gantry cranes, the fuel farm in its bund and its main past every pad, the pads with the scorch of
 * every lift on them and the blast walls between them, and the traffic standing on its pads: a freighter
 * mid-turnaround (a tanker at its hydrant, its cargo coming off), others loaded and waiting, couriers with their
 * shuttle pods. The traffic is the planet's and the defender's, never the company's: its berths are
 * {@link LandingPad.Purpose#PORT_LIGHTER} stands, which no lift lands on.
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
        pave(c, hub, hub.paved(), CONCRETE);
        pave(c, hub, railBand(hub), BALLAST);
        roads(c, hub);
        rails(c, registry, hub);
        LandingPad.Approach out = hub.outerNorth() ? LandingPad.Approach.NORTH : LandingPad.Approach.SOUTH;
        int n = hub.pads().size();
        int turnaround = rng.nextInt(n);
        int stands = 0;
        for (int i = 0; i < n; i++) {
            PortHub.Rect pad = hub.pads().get(i);
            // One freighter mid-turnaround, and about every other pad holding traffic, loaded and waiting: a freighter,
            // or a courier with the shuttle pods that run its passengers.
            boolean occupied = i == turnaround || rng.nextInt(OCCUPIED_ONE_IN) == 0;
            padMarks(c, registry, pad, occupied, rng);
            if (occupied) stands += i == turnaround || rng.nextBoolean() ? heavy(c, pad, out) : light(c, pad, out);
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

    /** Cells a heavy berth keeps inside its pad's edge on each side: the landing ring's rim. */
    static final int BERTH_RIM = 2;
    /** A light pad's courier berth's half side, and each shuttle pod's: a Hound fits the one, a pod the other. */
    static final int COURIER_HALF = 3, POD_HALF = 1;

    /**
     * A freighter's stand at its pad's middle, its berth the pad inside its rim, so setup stands the largest ship the
     * pad holds on it, its nose toward the outer edge it lifts over.
     *
     * @return the stands laid
     */
    static int heavy(GenContext c, PortHub.Rect pad, LandingPad.Approach out) {
        c.landingPads.add(new LandingPad(pad.centerX(), pad.centerY(), pad.width() / 2 - BERTH_RIM,
                pad.height() / 2 - BERTH_RIM, out, LandingPad.Purpose.PORT_LIGHTER));
        return 1;
    }

    /**
     * A light pad: a courier's berth at the middle, and a shuttle pod's berth either side of it toward the service
     * lane, the passengers' boats.
     *
     * @return the stands laid
     */
    static int light(GenContext c, PortHub.Rect pad, LandingPad.Approach out) {
        int cx = pad.centerX(), cy = pad.centerY();
        c.landingPads.add(new LandingPad(cx, cy, COURIER_HALF, COURIER_HALF, out, LandingPad.Purpose.PORT_LIGHTER));
        int inward = out == LandingPad.Approach.NORTH ? -1 : 1, podY = cy + inward * (pad.height() / 4 + 1);
        for (int side = -1; side <= 1; side += 2) {
            c.landingPads.add(new LandingPad(cx + side * (pad.width() / 4 + 1), podY, POD_HALF, POD_HALF, out,
                    LandingPad.Purpose.PORT_LIGHTER));
        }
        return 3;
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

    /**
     * The hub's roads, and the highway, as the town's streets are: bare street ground no pass claims, so the city's
     * road finish ({@code RoadSurfaceStage}) lays them in the same asphalt, sidewalks, centre line and square ends as
     * every other road on the map, and joins them to the town's. Nothing is painted here.
     */
    static void roads(GenContext c, PortHub hub) {
        street(c, hub.highway());
        for (PortHub.Rect r : hub.roads()) street(c, r);
    }

    /** The open cells of {@code r} as street ground, its surface the city's own. */
    private static void street(GenContext c, PortHub.Rect r) {
        c.doodads.removeIf(d -> r.contains(d.cellX, d.cellY) && free(c, d.cellX, d.cellY));
        for (int y = r.y0(); y <= r.y1(); y++) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                if (!free(c, x, y)) continue;
                c.topology.setGroundKind(x, y, GroundKind.STREET);
                c.topology.setSurface(x, y, 0);
                c.topology.setNatureOverlayIndex(x, y, -1);
            }
        }
    }

    /** Whether the cell is on the highway or one of the hub's roads: street the city finish lays, never made ground. */
    static boolean onRoad(PortHub hub, int x, int y) {
        if (hub.highway().contains(x, y)) return true;
        for (PortHub.Rect r : hub.roads()) if (r.contains(x, y)) return true;
        return false;
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
        for (int ry : rankY) rank(c, registry, park.x0() + 1, park.x1() - 1, ry, keep, null, -1, rng);
    }

    /**
     * One rank of truck stalls along x from {@code x0} to {@code x1}, three rows from {@code ry}: a stall line in
     * keep-clear paint between stalls, about three stalls in four holding an upright truck. No stall is laid on the
     * terminal's own ground ({@code claim} index {@code works}; null for none).
     */
    private static void rank(GenContext c, TileRegistry registry, int x0, int x1, int ry, int keep, int[][] claim,
                             int works, SplittableRandom rng) {
        for (int x = x0; x + 1 <= x1; x += STALL) {
            if (!outside(claim, works, x, ry) || !outside(claim, works, x + 2, ry + 2)) continue;
            for (int y = ry; y <= ry + 2; y++) if (free(c, x + 2, y)) c.topology.setSurface(x + 2, y, keep);
            if (rng.nextInt(4) == 0 || !freeRect(c, x, ry, x + 1, ry + 2)) continue;
            DoodadDef def = registry.doodad(HAULER_ART[rng.nextInt(HAULER_ART.length)]);
            if (def == null) continue;
            fixtures(c, x, ry, x + 1, ry + 2, false);
            c.doodads.add(new Doodad(x, ry, def, rng.nextBoolean() ? 1 : 3));
        }
    }

    /** Rows from one rank of the forecourt's truck park to the next: three of stalls, five of drive. */
    static final int PARK_PITCH = 8;

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

    /** The rail line's bed. */
    static final String BALLAST = "floors.stone";

    /**
     * Paves {@code paved} in {@code surface}, nothing under a building, door or road changed, and the hub's roads left
     * to the city's finish.
     */
    static void pave(GenContext c, PortHub hub, PortHub.Rect paved, String surface) {
        int concrete = c.topology.surfaceIndex(surface);
        // The country's scatter goes with the ground it lay on: rubble and stones the fill left are swept off.
        c.doodads.removeIf(d -> paved.contains(d.cellX, d.cellY) && free(c, d.cellX, d.cellY));
        for (int y = paved.y0(); y <= paved.y1(); y++) {
            for (int x = paved.x0(); x <= paved.x1(); x++) {
                if (!free(c, x, y) || onRoad(hub, x, y)) continue;
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
        tankFarm(c, registry, hub);
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
     * The fuel farm at the pad row's terminal end, laid as a refinery's is ({@link TankFarm}): its tanks in bunds walled
     * with barriers, the loading lane along the main's side where the tankers draw up, and the main leaving it for the
     * pads. Drawn in the refinery's own tank art.
     */
    static void tankFarm(GenContext c, TileRegistry registry, PortHub hub) {
        PortHub.Rect farm = hub.fuelFarm();
        TankFarm.Plan plan = TankFarm.plan(c.width, farm.x0(), farm.y0(), farm.x1(), farm.y1(),
                i -> free(c, i % c.width, i / c.width), hub.fuelMain().y0(), hub.terminalX());
        if (plan == null) return;
        int keep = c.topology.surfaceIndex(KEEP_CLEAR), plate = c.topology.surfaceIndex(PAD_PLATE);
        int[] lane = plan.lane();
        for (int y = lane[1]; y <= lane[3]; y++) {
            for (int x = lane[0]; x <= lane[2]; x++) {
                boolean rim = y == lane[1] || y == lane[3];
                if (rim && free(c, x, y)) c.topology.setSurface(x, y, keep);
            }
        }
        DoodadDef tanker = registry.doodad(TANKER);
        for (TankFarm.Bund bund : plan.bunds()) {
            // Each bund's floor a sealed plate, so a burst tank's fuel stays in it, its wall's line marked under the
            // barriers so the bund reads from above.
            for (int y = bund.y0(); y <= bund.y1(); y++) {
                for (int x = bund.x0(); x <= bund.x1(); x++) {
                    if (!free(c, x, y)) continue;
                    boolean rim = x == bund.x0() || x == bund.x1() || y == bund.y0() || y == bund.y1();
                    c.topology.setSurface(x, y, rim ? keep : plate);
                }
            }
            for (TankFarm.Tank tank : bund.tanks()) tank(c, registry, tank);
            for (TankFarm.Wall wall : bund.walls()) {
                StreetDressing.jerseyBarrier(c.grid, c.topology, c.doodads, wall.x(), wall.y(), wall.alongX());
            }
        }
        // The farm's line to the main: a pipe bridge from the lane's far end over the service lane, where the main
        // leaves along the hub for the pads.
        int pipeX = plan.bay()[0] == lane[0] ? lane[2] - 1 : lane[0] + 1;
        int mainY = hub.fuelMain().y0(), laneY = Math.abs(lane[1] - mainY) < Math.abs(lane[3] - mainY) ? lane[1] : lane[3];
        if (free(c, pipeX, laneY) && free(c, pipeX, mainY + Integer.signum(laneY - mainY))) {
            StreetDressing.pipeRun(c.grid, c.topology, c.doodads, pipeX, laneY, pipeX,
                    mainY + Integer.signum(laneY - mainY), 0);
        }
        // Two tankers drawn up in the lane's bay, filling.
        if (tanker == null) return;
        int[] bay = plan.bay();
        int ty = (bay[1] + bay[3]) / 2 - 1;
        for (int k = 0, x = bay[0] + 2; k < 2 && x + 2 <= bay[2]; k++, x += 6) {
            if (!freeRect(c, x, ty, x + 2, ty + 1)) continue;
            fixtures(c, x, ty, x + 2, ty + 1, true);
            c.doodads.add(new Doodad(x, ty, tanker));
        }
    }

    /** A tank: its disc solid and blind, a big one a floating-roof tank, a small one a sphere, in the refinery's art. */
    static void tank(GenContext c, TileRegistry registry, TankFarm.Tank tank) {
        int size = tank.size();
        float r = size / 2f, cx = tank.x() + r, cy = tank.y() + r;
        for (int y = tank.y(); y < tank.y() + size; y++) {
            for (int x = tank.x(); x < tank.x() + size; x++) {
                float dx = x + .5f - cx, dy = y + .5f - cy;
                if (dx * dx + dy * dy <= r * r + .25f && free(c, x, y)) fixtures(c, x, y, x, y, false);
            }
        }
        boolean big = size >= TankFarm.BIG;
        DoodadDef art = registry.doodad(big ? RefineryGround.TANK_ART : RefineryGround.SPHERE_ART);
        float span = big ? RefineryGround.TANK_SPAN : RefineryGround.SPHERE_SPAN;
        float at = big ? RefineryGround.TANK_AT : RefineryGround.SPHERE_AT;
        int[] ox = RefineryGround.over(size, span, at, tank.x()), oy = RefineryGround.over(size, span, at, tank.y());
        if (art == null || ox == null || oy == null) return;
        Doodad d = scaled(art, ox[0], ox[1], oy[1]);
        if (d != null) c.doodads.add(d);
    }

    /**
     * {@code def} drawn {@code size} cells square from {@code (x, y)}: its sheet read at a cell so much finer that its
     * frame spans the size, as the refinery draws its vessels. Null when the frame does not divide into it.
     */
    static Doodad scaled(DoodadDef def, int size, int x, int y) {
        int cell0 = def.sourceCellPx > 0 ? def.sourceCellPx : TileManifest.TILE_SIZE;
        int px = cell0 * def.footprintCellsX;
        if (size <= 0 || px % size != 0) return null;
        int cell = px / size;
        if ((def.col * cell0) % cell != 0 || (def.row * cell0) % cell != 0) return null;
        return new Doodad(x, y, new TileManifest.TileFrame(def.col * cell0 / cell, def.row * cell0 / cell),
                def.sheetPath, def.cover.level(), def.ballisticHalfHeight, size, size, null, cell);
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
    static final int REAR_REACH = 48;

    /** Rows either side of the yard road's middle the rear gate may be opened on, where a hall stands on it. */
    static final int REAR_SHIFT = 10;

    /**
     * The terminal's rear gate: the way the bonded cargo goes out to the hub. From the hub's edge on the terminal's
     * side, beside the truck gate's road, a lane is laid across the forecourt as street and opened through the
     * terminal's perimeter wall into its yard, on the yard road's middle row or the nearest row within
     * {@link #REAR_SHIFT} where no hall stands behind the wall. Nothing is opened where no such row finds a wall within
     * {@link #REAR_REACH}.
     *
     * @return the lane laid, or null where no gate was opened
     */
    public static PortHub.Rect rearGate(GenContext c, PortHub hub) {
        int step = hub.deepEast() ? -1 : 1;
        int startX = hub.terminalX() + step;
        PortHub.Rect apron = hub.apron();
        int midY = hub.outerNorth() ? apron.y0() + PortHub.YARD_ROAD / 2 : apron.y1() - PortHub.YARD_ROAD / 2;
        for (int shift = 0; shift <= REAR_SHIFT; shift++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                if (shift == 0 && sign < 0) continue;
                int row = midY + sign * shift;
                int length = gateRun(c, startX, step, row);
                if (length < 0) continue;
                lane(c, startX, step, row, length);
                int endX = startX + step * (length - 1);
                return PortHub.Rect.of(startX, row - REAR_GATE / 2, endX, row + REAR_GATE / 2);
            }
        }
        return null;
    }

    /**
     * Cells from {@code startX} stepping {@code step} along {@code row} to just past the terminal's wall, or -1 where
     * a hall or door comes first, the row leaves the map, or no wall is met within {@link #REAR_REACH}.
     */
    private static int gateRun(GenContext c, int startX, int step, int row) {
        if (row - REAR_GATE / 2 < 1 || row + REAR_GATE / 2 >= c.height - 1) return -1;
        boolean walled = false;
        for (int k = 0; k < REAR_REACH; k++) {
            int x = startX + step * k;
            if (x < 1 || x >= c.width - 1) return -1;
            for (int y = row - REAR_GATE / 2; y <= row + REAR_GATE / 2; y++) {
                if (c.topology.getBuildingId(x, y) != 0 || c.grid.isDoorway(x, y)) return -1;
            }
            boolean wall = c.topology.isWall(x, row);
            if (wall) walled = true;
            if (walled && !wall && c.grid.isWalkable(x, row)) return k;
        }
        return -1;
    }

    /** The rear lane, {@link #REAR_GATE} rows about {@code row}, as street, the wall opened where it crosses. */
    private static void lane(GenContext c, int startX, int step, int row, int length) {
        for (int k = 0; k < length; k++) {
            int x = startX + step * k;
            for (int y = row - REAR_GATE / 2; y <= row + REAR_GATE / 2; y++) {
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
                // Street for the city's finish: the road from the truck gate on through the gate, not a slab.
                c.topology.setSurface(x, y, 0);
                c.topology.setNatureOverlayIndex(x, y, -1);
            }
        }
    }

    /**
     * The forecourt between the terminal and the hub ({@code PortWorks.FORECOURT}): paved as the hub is round the rear
     * {@code lane} ({@link #rearGate}, null for none), which stays street, so the one road from the truck gate runs across it into the
     * works; on the rest, ranks of haulers waiting their call. The terminal's own ground ({@code claim} index
     * {@code works}) is its own and is not touched.
     */
    public static void forecourt(GenContext c, PortHub.Rect court, PortHub.Rect lane, int[][] claim, int works,
                                 long seed) {
        int concrete = c.topology.surfaceIndex(CONCRETE);
        c.doodads.removeIf(d -> court.contains(d.cellX, d.cellY) && outside(claim, works, d.cellX, d.cellY)
                && free(c, d.cellX, d.cellY) && (lane == null || !lane.contains(d.cellX, d.cellY)));
        for (int y = court.y0(); y <= court.y1(); y++) {
            for (int x = court.x0(); x <= court.x1(); x++) {
                if (!outside(claim, works, x, y) || !free(c, x, y) || lane != null && lane.contains(x, y)) continue;
                c.topology.setGroundKind(x, y, GroundKind.STREET);
                c.topology.setNatureOverlayIndex(x, y, -1);
                c.topology.setSurface(x, y, concrete);
                c.markMadeGround(x, y);
            }
        }
        // The haulers cleared through customs wait here for the rear gate, in ranks clear of the lane.
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        SplittableRandom rng = new SplittableRandom(seed);
        int keep = c.topology.surfaceIndex(KEEP_CLEAR);
        for (int ry = court.y0() + 2; ry + 2 <= court.y1() - 2; ry += PARK_PITCH) {
            if (lane != null && ry + 2 >= lane.y0() - 2 && ry <= lane.y1() + 2) continue;
            rank(c, registry, court.x0() + 2, court.x1() - 2, ry, keep, claim, works, rng);
        }
    }

    /** Whether the cell lies off the terminal's own ground. */
    private static boolean outside(int[][] claim, int works, int x, int y) {
        return claim == null || x < 0 || y < 0 || x >= claim.length || y >= claim[x].length || claim[x][y] != works;
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
