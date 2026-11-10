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
 * Lays a {@link PortApron} on a map ({@code raid-site-templates.md}, spaceport): the pavement, the pads with the scorch
 * of every lift on them, the blast walls between them, the fuel main on its rack from the farm past every pad, the
 * container stacks waiting in staging under their crane, and the lighters standing on the pads: one mid-turnaround
 * (a tanker at its hydrant, its cargo coming off), the rest of the pads empty and freshly burnt, their lighters in
 * orbit. A lighter is the planet's traffic and the defender's, never the company's: its pad is a
 * {@link LandingPad.Purpose#PORT_LIGHTER} stand, which no lift lands on.
 *
 * <p>Nothing is laid on a cell a building, a doorway or a road holds; the apron's ground is held clear of them before
 * the places grow, so in practice nothing is refused.
 */
public final class PortApronDressing {

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

    private PortApronDressing() { }

    /**
     * Lays {@code apron} on {@code c}. {@code seed} draws which pad is mid-turnaround and the scorch's breaks.
     *
     * @return the stands the lighters standing on the apron occupy, already added to {@code c.landingPads}
     */
    public static int lay(GenContext c, PortApron apron, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        TileRegistry registry = TileRegistry.installed();
        pave(c, apron.paved());
        int plate = c.topology.surfaceIndex(PAD_PLATE), rim = c.topology.surfaceIndex(KEEP_CLEAR);
        DoodadDef burn = registry == null ? null : registry.doodad(BURN), mark = registry == null ? null
                : registry.doodad(MARK);
        DoodadDef line = registry == null ? null : registry.doodad(BERTH_EDGE);
        boolean deepEast = apron.deepX() >= apron.paved().x1();
        LandingPad.Approach out = deepEast ? LandingPad.Approach.EAST : LandingPad.Approach.WEST;
        int turnaround = rng.nextInt(apron.pads().size());
        // A second lighter, loaded and waiting on its slot, wherever the apron has three pads or more.
        int waiting = apron.pads().size() >= 3 ? (turnaround + 1 + rng.nextInt(apron.pads().size() - 1))
                % apron.pads().size() : -1;
        int stands = 0;
        for (int i = 0; i < apron.pads().size(); i++) {
            PortApron.Rect pad = apron.pads().get(i);
            // The stand's edge painted as an airfield berth's is, the keep-clear hatching inside it, and the plate
            // the lighter stands on in the middle.
            for (int y = pad.y0(); y <= pad.y1(); y++) {
                for (int x = pad.x0(); x <= pad.x1(); x++) {
                    if (!free(c, x, y)) continue;
                    boolean edge = x == pad.x0() || x == pad.x1() || y == pad.y0() || y == pad.y1();
                    boolean hatched = !edge && (x == pad.x0() + 1 || x == pad.x1() - 1
                            || y == pad.y0() + 1 || y == pad.y1() - 1);
                    if (edge && line != null) c.doodads.add(new Doodad(x, y, line));
                    else if (hatched) c.topology.setSurface(x, y, rim);
                    else c.topology.setSurface(x, y, plate);
                }
            }
            // The burn: the scorch every lift leaves where its exhaust meets the plate, and the lesser marks of the
            // landings round it, some worn off.
            int cx = pad.centerX(), cy = pad.centerY();
            // A pad with its lighter standing on it shows the hull, not the burn under it.
            boolean occupied = i == turnaround || i == waiting;
            if (burn != null && !occupied) c.doodads.add(new Doodad(cx - 2, cy - 2, burn, rng.nextInt(4) % 2 == 0 ? 0 : 2));
            if (mark != null && !occupied) {
                int[][] ring = {{-4, -4}, {2, -4}, {-4, 2}, {2, 2}, {-1, -5}, {-1, 3}};
                for (int[] at : ring) {
                    if (rng.nextDouble() < SCORCH_SHARE) c.doodads.add(new Doodad(cx + at[0], cy + at[1], mark));
                }
            }
            if (i == turnaround || i == waiting) {
                // The freighter's hull reaches past its stand toward its tail: stood a cell in from the pad's
                // middle, toward the terminal, it stays on its pad.
                c.landingPads.add(new LandingPad(cx - out.dx, cy, 1, 1, out, LandingPad.Purpose.PORT_LIGHTER));
                stands++;
            }
        }
        for (PortApron.Rect wall : apron.blastWalls()) blastWall(c, wall);
        fuel(c, registry, apron);
        turnaround(c, registry, apron, apron.pads().get(turnaround), apron.hydrants().get(turnaround), deepEast);
        staging(c, registry, apron, rng);
        apronControl(c, registry, apron, deepEast);
        return stands;
    }

    /** Paves the apron: asphalt over its ground, nothing under a building, door or road changed. */
    static void pave(GenContext c, PortApron.Rect paved) {
        int concrete = c.topology.surfaceIndex(CONCRETE);
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
    static void blastWall(GenContext c, PortApron.Rect wall) {
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
     * The fuel farm's tanks, and the main on its rack from the farm past every pad, a hydrant skid where it serves
     * each pad.
     */
    static void fuel(GenContext c, TileRegistry registry, PortApron apron) {
        PortApron.Rect farm = apron.fuelFarm();
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
        PortApron.Rect main = apron.fuelMain();
        // The main runs along one column, overhead on its rack, from beside the farm to past the last pad.
        int mx = main.x0();
        int from = main.y0(), to = main.y1();
        if (!free(c, mx, from) || !free(c, mx, to)) return;
        StreetDressing.pipeRun(c.grid, c.topology, c.doodads, mx, from, mx, to, RACK_BAY);
        DoodadDef skid = registry.doodad(SKID);
        if (skid == null) return;
        for (int[] at : apron.hydrants()) {
            // The skid lies across the service lane from the main toward its pad, 4 cells by 2, turned along u.
            int x = apron.deepX() > mx ? at[0] : at[0] - 3;
            if (!freeRect(c, x, at[1], x + 3, at[1] + 1)) continue;
            fixtures(c, x, at[1], x + 3, at[1] + 1, true);
            c.doodads.add(new Doodad(x, at[1], skid));
        }
    }

    /**
     * The lighter mid-turnaround: a tanker drawn up at its hydrant, carts beside its hold, and the cargo coming off
     * it stacked on the pad's near rim.
     */
    static void turnaround(GenContext c, TileRegistry registry, PortApron apron, PortApron.Rect pad, int[] hydrant,
                           boolean deepEast) {
        DoodadDef tanker = registry.doodad(TANKER), crates = registry.doodad(CRATES),
                pallets = registry.doodad(PALLETS), cart = registry.doodad(CART);
        // The tanker stands in the service lane beside the skid, along the lane (v), its 3x2 turned upright.
        int laneX = deepEast ? pad.x0() - 3 : pad.x1() + 2;
        int ty = hydrant[1] + 3;
        if (tanker != null && freeRect(c, laneX, ty, laneX + 1, ty + 2)) {
            fixtures(c, laneX, ty, laneX + 1, ty + 2, false);
            c.doodads.add(new Doodad(laneX, ty, tanker, 1));
        }
        // Cargo off the hold, on the pad's near rim either side of the stand's middle row.
        int rimX = deepEast ? pad.x0() + 1 : pad.x1() - 1;
        int[][] stacks = {{rimX, pad.y0() + 2}, {rimX, pad.y0() + 3}, {rimX, pad.y1() - 2}, {rimX, pad.y1() - 3}};
        DoodadDef[] kinds = {crates, pallets, pallets, crates};
        for (int i = 0; i < stacks.length; i++) {
            if (kinds[i] == null || !free(c, stacks[i][0], stacks[i][1])) continue;
            fixtures(c, stacks[i][0], stacks[i][1], stacks[i][0], stacks[i][1], true);
            c.doodads.add(new Doodad(stacks[i][0], stacks[i][1], kinds[i]));
        }
        if (cart != null) {
            int cx = deepEast ? pad.x0() + 2 : pad.x1() - 2, cy = pad.centerY() + 3;
            if (freeRect(c, cx, cy, cx, cy + 1)) {
                fixtures(c, cx, cy, cx, cy + 1, true);
                c.doodads.add(new Doodad(cx, cy, cart));
            }
        }
    }

    /** The apron control post's radar, and the flood lamps on the blast walls. */
    static final String RADAR = "doodad.military-radar-dish";
    static final String FLOOD = "doodad.street-lamp-cap-industrial";

    /**
     * Apron control: the approach radar at the near end of the fuel main, where the terminal's tower looks down the
     * pad line, and a flood lamp at each blast wall's ends, lighting the stands either side.
     */
    static void apronControl(GenContext c, TileRegistry registry, PortApron apron, boolean deepEast) {
        DoodadDef radar = registry.doodad(RADAR), flood = registry.doodad(FLOOD);
        PortApron.Rect main = apron.fuelMain();
        PortApron.Rect first = apron.pads().get(0);
        boolean farmHigh = apron.fuelFarm().y0() > first.y1();
        int ry = farmHigh ? main.y0() - 2 : main.y1() + 2;
        int rx = main.x0() + (deepEast ? 2 : -2);
        if (radar != null && free(c, rx, ry)) {
            fixtures(c, rx, ry, rx, ry, true);
            c.doodads.add(new Doodad(rx, ry, radar));
        }
        if (flood == null) return;
        for (PortApron.Rect wall : apron.blastWalls()) {
            for (int x : new int[]{wall.x0() - 1, wall.x1() + 1}) {
                if (!free(c, x, wall.y0())) continue;
                fixtures(c, x, wall.y0(), x, wall.y0(), true);
                c.doodads.add(new Doodad(x, wall.y0(), flood));
            }
        }
    }

    /** The staging rows' containers, and the gantry crane over the first block. */
    static void staging(GenContext c, TileRegistry registry, PortApron apron, SplittableRandom rng) {
        PortApron.Rect crane = apron.crane();
        for (PortApron.Rect box : apron.containers()) {
            // A gap in a row now and then: a box already gone to its lighter.
            if (rng.nextInt(6) == 0) continue;
            DoodadDef def = registry.doodad(BOXES[rng.nextInt(BOXES.length)]);
            if (def == null || !freeRect(c, box.x0(), box.y0(), box.x1(), box.y1())) continue;
            fixtures(c, box.x0(), box.y0(), box.x1(), box.y1(), false);
            // A container is 4 by 2 drawn along x; the staging rows lie along y.
            c.doodads.add(new Doodad(box.x0(), box.y0(), def, box.height() > box.width() ? 1 : 0));
        }
        if (crane == null) return;
        DoodadDef span = registry.doodad(CRANE_SPAN), leg = registry.doodad(CRANE_LEG);
        if (span == null || leg == null) return;
        for (int x : new int[]{crane.x0(), crane.x1()}) {
            if (!freeRect(c, x, crane.y0(), x, crane.y1())) return;
        }
        for (int x : new int[]{crane.x0(), crane.x1()}) {
            fixtures(c, x, crane.y0(), x, crane.y1(), false);
            c.doodads.add(new Doodad(x, crane.y0(), leg));
        }
        c.doodads.add(new Doodad(crane.x0(), crane.y0(), span));
    }

    /** Width of the terminal's rear gate onto the apron. */
    static final int REAR_GATE = 3;
    /** Furthest the rear lane walks from the apron toward the terminal before it gives up. */
    static final int REAR_REACH = 24;

    /**
     * The terminal's rear gate: the way the bonded cargo goes out to staging. From the apron's edge on the terminal's
     * side, along the rows level with the staging's middle, a lane is paved across the cleared ground and opened
     * through the terminal's perimeter wall into its yard. Nothing is opened where the lane would meet a building
     * first, or where it finds no wall within {@link #REAR_REACH}.
     *
     * @return whether the gate was opened
     */
    public static boolean rearGate(GenContext c, PortApron apron) {
        boolean deepEast = apron.deepX() >= apron.paved().x1();
        int step = deepEast ? -1 : 1;
        int startX = deepEast ? apron.paved().x0() - 1 : apron.paved().x1() + 1;
        int midY = apron.crane() != null ? apron.crane().centerY() : apron.paved().centerY();
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
     * @param apronX a cell column on the apron's side of the terminal: the gate facing it holds no queue
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
