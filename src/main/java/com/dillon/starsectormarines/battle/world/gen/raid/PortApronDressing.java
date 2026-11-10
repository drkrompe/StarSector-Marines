package com.dillon.starsectormarines.battle.world.gen.raid;

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
    static final String KEEP_CLEAR = "apron.keep-clear";
    static final String SCORCH = "terrain.blast-paving";
    static final String TANK = "doodad.industrial-fluid-tank";
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
    static final float SCORCH_SHARE = .7f;

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
        int scorch = c.topology.surfaceIndex(SCORCH);
        boolean deepEast = apron.deepX() >= apron.paved().x1();
        LandingPad.Approach out = deepEast ? LandingPad.Approach.EAST : LandingPad.Approach.WEST;
        int turnaround = rng.nextInt(apron.pads().size());
        int stands = 0;
        for (int i = 0; i < apron.pads().size(); i++) {
            PortApron.Rect pad = apron.pads().get(i);
            for (int y = pad.y0(); y <= pad.y1(); y++) {
                for (int x = pad.x0(); x <= pad.x1(); x++) {
                    if (!free(c, x, y)) continue;
                    boolean edge = x == pad.x0() || x == pad.x1() || y == pad.y0() || y == pad.y1();
                    c.topology.setSurface(x, y, edge ? rim : plate);
                }
            }
            // The burn: a broken ring where the exhaust meets the plate, round the stand's middle.
            int cx = pad.centerX(), cy = pad.centerY();
            for (int y = pad.y0() + 1; y < pad.y1(); y++) {
                for (int x = pad.x0() + 1; x < pad.x1(); x++) {
                    double d = Math.hypot(x - cx, y - cy);
                    if (d < 1.5 || d > 4.6 || !free(c, x, y)) continue;
                    if (rng.nextDouble() < SCORCH_SHARE) c.topology.setSurface(x, y, scorch);
                }
            }
            if (i == turnaround) {
                c.landingPads.add(new LandingPad(cx, cy, 1, 1, out, LandingPad.Purpose.PORT_LIGHTER));
                stands++;
            }
        }
        for (PortApron.Rect wall : apron.blastWalls()) blastWall(c, wall);
        fuel(c, registry, apron);
        turnaround(c, registry, apron, apron.pads().get(turnaround), apron.hydrants().get(turnaround), deepEast);
        staging(c, registry, apron, rng);
        return stands;
    }

    /** Paves the apron: asphalt over its ground, nothing under a building, door or road changed. */
    static void pave(GenContext c, PortApron.Rect paved) {
        for (int y = paved.y0(); y <= paved.y1(); y++) {
            for (int x = paved.x0(); x <= paved.x1(); x++) {
                if (!free(c, x, y)) continue;
                c.topology.setGroundKind(x, y, GroundKind.STREET);
                c.topology.setSurface(x, y, 0);
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
        if (tank != null) {
            int keep = c.topology.surfaceIndex(KEEP_CLEAR);
            for (int y = farm.y0(); y <= farm.y1(); y++) {
                for (int x = farm.x0(); x <= farm.x1(); x++) {
                    if (!free(c, x, y)) continue;
                    boolean edge = x == farm.x0() || x == farm.x1() || y == farm.y0() || y == farm.y1();
                    if (edge) {
                        c.topology.setSurface(x, y, keep);
                    } else if ((x - farm.x0()) % 2 == 1 && (y - farm.y0()) % 2 == 1) {
                        c.doodads.add(new Doodad(x, y, tank));
                        c.grid.setWalkable(x, y, false);
                        c.grid.setSeeThrough(x, y, false);
                        c.topology.setFixture(x, y, true);
                    }
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
