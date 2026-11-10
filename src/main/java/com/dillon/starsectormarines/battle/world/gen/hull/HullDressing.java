package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Entrance;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Hall;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.HallUse;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckTrim;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The fallen hull dressed as the wrecked ship it is ({@code fallen-hull-nouns.md}, The ground), from the ship-interior
 * kits the company's own deck is built of: what is left of the cargo racked against each hold's outer wall, the forge
 * hall's engineering plant in its two aft corners, the deck's wall-foot trim and bulkhead ribs ({@link ShipDeckTrim}),
 * and wear, grime and the crash's debris strewn over every compartment and breach. Art the tileset lacks is left out.
 *
 * <p>Only the cargo and the plant stand in the way: each is kept off the docks, the ways in and the autoforge's
 * ground, and well inside the halls the derelict line walks ({@code derelict-watch.md}, section 3). Everything else is
 * flat on the deck. Placement is by coordinate hash, so it draws from no stream.
 */
final class HullDressing {

    /** Cargo left on the holds' outer walls, one cell each. */
    static final String[] CARGO = {"ship-hold.crate", "ship-hold.drum", "ship-hold.ammo-pair", "ship-hold.cable-coil",
            "ship-hold.toolbox"};
    /** Cargo two cells long, laid along a hold's wall where the hull lies so it fits. */
    static final String[] CARGO_LONG = {"ship-hold.crate-stack", "ship-hold.pallet", "ship-hold.netted-cargo",
            "ship-hold.ammo-cans"};
    /** The forge hall's plant, in its aft corners. */
    static final String[] PLANT = {"ship-engineering.generator", "ship-engineering.cooling"};
    /** The forge hall's switchgear and benches, along its flanks forward of the plant. */
    static final String[] WORKS = {"ship-engineering.switchgear", "ship-engineering.control", "ship-engineering.bench",
            "ship-engineering.spares"};
    /** Flat dressing over any deck: wear, grime, spilt cable, the crash's debris. */
    static final String[] DECK = {"ship-dressing.grime", "ship-dressing.debris", "ship-dressing.cable-spill",
            "ship-dressing.buckled-plate", "ship-dressing.condensation", "ship-wear.scuffs", "ship-wear.scorch",
            "ship-wear.patch"};
    /** The breaches' own ({@code fallen-hull.tileset.json}); the deck's debris where they are missing. */
    static final String BREACH_EDGE = "fallen-hull.breach-edge", BREACH_SPILL = "fallen-hull.breach-spill";
    /** Interior dressing the hull's art adds goes in under this prefix. */
    static final String INTERIOR = "fallen-hull.interior-";
    /** One flat piece tried per this many deck cells. UNTUNED. */
    static final int DECK_EVERY = 45;
    /** Cargo stands this far from a dock or way in, and the autoforge's ground runs this far round it. */
    static final int DOCK_CLEAR = 3, FORGE_CLEAR = 5;

    private static final int[][] DIRECTIONS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    private HullDressing() { }

    static void dress(GenContext ctx, HullPlan hull, CrashSiteStage.Frame frame, int[] compartments) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        new Dresser(ctx, hull, frame, compartments, registry).dress();
        ShipDeckTrim.apply(ctx);
    }

    private static final class Dresser {
        final GenContext ctx;
        final NavigationGrid grid;
        final CellTopology topology;
        final HullPlan hull;
        final CrashSiteStage.Frame frame;
        final int[] compartments;
        final TileRegistry registry;
        final int w;
        final boolean[] taken;
        final int forgeX, forgeY;

        Dresser(GenContext ctx, HullPlan hull, CrashSiteStage.Frame frame, int[] compartments, TileRegistry registry) {
            this.ctx = ctx;
            this.grid = ctx.grid;
            this.topology = ctx.topology;
            this.hull = hull;
            this.frame = frame;
            this.compartments = compartments;
            this.registry = registry;
            this.w = ctx.width;
            this.taken = new boolean[ctx.width * ctx.height];
            forgeX = frame.wx(HullPlan.FORGE_U, HullPlan.FORGE_V);
            forgeY = frame.wy(HullPlan.FORGE_U, HullPlan.FORGE_V);
        }

        void dress() {
            for (Hall hall : hull.halls()) {
                if (hall.use() == HallUse.HOLD) cargo(hall);
                if (hall.use() == HallUse.FORGE) {
                    plant(hall);
                    works(hall);
                }
            }
            breaches();
            deck();
        }

        /**
         * What is left of a hold's cargo: stacks along its outer wall and both its ends, away from its dock and any
         * breach; two-cell cargo where the hull lies so it runs along the wall, single pieces otherwise.
         */
        void cargo(Hall hall) {
            HullPlan.Rect f = hall.floor();
            int outer = f.v0() >= HullPlan.BREADTH / 2 ? f.v1() : f.v0();
            for (int u = f.u0() + 1; u < f.u1(); u += 3) alongU(u, outer, CARGO_LONG, CARGO, 0xCA);
            for (int v = f.v0() + 1; v < f.v1(); v += 3) {
                alongV(f.u0(), v, CARGO_LONG, CARGO, 0xCB);
                alongV(f.u1(), v, CARGO_LONG, CARGO, 0xCC);
            }
        }

        /** The forge hall's switchgear and benches, along both flanks between the plant and the autoforge. */
        void works(Hall hall) {
            HullPlan.Rect f = hall.floor();
            for (int u = f.u0() + 8; u < HullPlan.FORGE_U - 3; u += 4) {
                alongU(u, f.v0(), WORKS, new String[0], 0xE1);
                alongU(u, f.v1(), WORKS, new String[0], 0xE2);
            }
        }

        /** A piece against a wall running along the hull at plan {@code (u, v)}: a long one if it lies that way. */
        void alongU(int u, int v, String[] longer, String[] single, int salt) {
            place(u, v, frame.dx(1, 0) != 0, longer, single, salt);
        }

        /** A piece against a wall running across the hull. */
        void alongV(int u, int v, String[] longer, String[] single, int salt) {
            place(u, v, frame.dx(0, 1) != 0, longer, single, salt);
        }

        void place(int u, int v, boolean runsAlongX, String[] longer, String[] single, int salt) {
            if (nearWay(u, v)) return;
            int x = frame.wx(u, v), y = frame.wy(u, v);
            int hash = CrashSiteStage.hash(ctx.seed, x, y, salt);
            if (Math.floorMod(hash, 5) == 0) return;
            DoodadDef def = registry.doodad(longer[Math.floorMod(hash >> 4, longer.length)]);
            if (def != null && (def.footprintCellsX > def.footprintCellsY) == runsAlongX) {
                int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
                // Back the piece onto the wall: it reaches inward from the cell on the wall.
                int ax = runsAlongX ? x : (isWall(x - 1, y) ? x : x - fx + 1);
                int ay = runsAlongX ? (isWall(x, y - 1) ? y : y - fy + 1) : y;
                if (stand(def, ax, ay, fx, fy)) return;
            }
            if (single.length == 0) return;
            def = registry.doodad(single[Math.floorMod(hash >> 8, single.length)]);
            if (def != null && def.footprintCellsX <= 1 && def.footprintCellsY <= 1) stand(def, x, y, 1, 1);
        }

        boolean isWall(int x, int y) {
            return grid.inBounds(x, y) && topology.isWall(x, y);
        }

        /** Whether a plan cell is within {@link #DOCK_CLEAR} of a hall's dock or a way in. */
        boolean nearWay(int u, int v) {
            for (Hall hall : hull.halls()) if (near(hall.door(), u, v)) return true;
            for (Entrance e : hull.entrances()) if (near(e.door(), u, v)) return true;
            return false;
        }

        static boolean near(HullPlan.Rect r, int u, int v) {
            return u >= r.u0() - DOCK_CLEAR && u <= r.u1() + DOCK_CLEAR && v >= r.v0() - DOCK_CLEAR
                    && v <= r.v1() + DOCK_CLEAR;
        }

        /** The forge hall's plant, one machine in each aft corner, inset a cell from the walls. */
        void plant(Hall hall) {
            Area floor = frame.area(hall.floor());
            int[][] corners = {{hall.floor().u0(), hall.floor().v0()}, {hall.floor().u0(), hall.floor().v1()}};
            for (int k = 0; k < corners.length; k++) {
                DoodadDef def = registry.doodad(PLANT[k % PLANT.length]);
                if (def == null) continue;
                int cx = frame.wx(corners[k][0], corners[k][1]), cy = frame.wy(corners[k][0], corners[k][1]);
                int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
                int x = cx <= floor.centreX() ? floor.left() + 1 : floor.right() - fx;
                int y = cy <= floor.centreY() ? floor.bottom() + 1 : floor.top() - fy;
                stand(def, x, y, fx, fy);
            }
        }

        /** Stands a piece that is in the way, where every cell of it is open deck clear of the ways and the forge. */
        boolean stand(DoodadDef def, int x0, int y0, int fx, int fy) {
            for (int y = y0; y < y0 + fy; y++) {
                for (int x = x0; x < x0 + fx; x++) {
                    if (!deck(x, y) || grid.isDoorway(x, y) || nearForge(x, y)) return false;
                }
            }
            for (int y = y0; y < y0 + fy; y++) {
                for (int x = x0; x < x0 + fx; x++) {
                    grid.setWalkable(x, y, false);
                    grid.setSeeThrough(x, y, true);
                    topology.setFixture(x, y, true);
                    taken[y * w + x] = true;
                }
            }
            ctx.doodads.add(new Doodad(x0, y0, def));
            return true;
        }

        /** The breaches' torn edges against the shell, and the plate they spilt over their floor and out of them. */
        void breaches() {
            DoodadDef edge = registry.doodad(BREACH_EDGE);
            DoodadDef spill = registry.doodad(BREACH_SPILL);
            if (spill == null) spill = registry.doodad("ship-dressing.debris");
            for (Entrance e : hull.entrances()) {
                if (!e.torn()) continue;
                Area area = frame.area(e.door());
                for (int y = area.bottom() - 1; y <= area.top() + 1; y++) {
                    for (int x = area.left() - 1; x <= area.right() + 1; x++) {
                        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                        if (edge != null) {
                            for (int turn = 0; turn < 4; turn++) {
                                int nx = x + DIRECTIONS[turn][0], ny = y + DIRECTIONS[turn][1];
                                if (grid.inBounds(nx, ny) && topology.isWall(nx, ny)) {
                                    ctx.doodads.add(0, new Doodad(x, y, edge, turn));
                                }
                            }
                        }
                        if (spill != null && Math.floorMod(CrashSiteStage.hash(ctx.seed, x, y, 0xB5), 6) == 0) {
                            flat(spill, x, y, false);
                        }
                    }
                }
            }
        }

        /** Flat dressing over the compartments' decks, by hash; each piece kept inside its own compartment. */
        void deck() {
            List<DoodadDef> pool = new ArrayList<>();
            for (String id : DECK) {
                DoodadDef def = registry.doodad(id);
                if (def != null) pool.add(def);
            }
            for (DoodadDef def : registry.doodads()) {
                if (def.id.startsWith(INTERIOR) && !def.id.endsWith("-wall")) pool.add(def);
            }
            if (pool.isEmpty()) return;
            for (int k = 0; k < compartments.length; k++) {
                if (compartments[k] < 0) continue;
                int x = k % w, y = k / w;
                int hash = CrashSiteStage.hash(ctx.seed, x, y, 0xDE);
                if (Math.floorMod(hash, DECK_EVERY) != 0) continue;
                flat(pool.get(Math.floorMod(hash >> 8, pool.size())), x, y, true);
            }
        }

        /** A flat piece at {@code (x0, y0)} where its footprint is free deck (in one compartment if {@code same}). */
        void flat(DoodadDef def, int x0, int y0, boolean same) {
            int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
            int room = compartments[y0 * w + x0];
            for (int y = y0; y < y0 + fy; y++) {
                for (int x = x0; x < x0 + fx; x++) {
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || topology.isWall(x, y)
                            || grid.isDoorway(x, y) || taken[y * w + x] || nearForge(x, y)) return;
                    if (same && compartments[y * w + x] != room) return;
                }
            }
            for (int y = y0; y < y0 + fy; y++) for (int x = x0; x < x0 + fx; x++) taken[y * w + x] = true;
            int hash = CrashSiteStage.hash(ctx.seed, x0, y0, 0xF1);
            ctx.doodads.add(0, new Doodad(x0, y0, def, 0, (hash & 1) == 0));
        }

        boolean deck(int x, int y) {
            return grid.inBounds(x, y) && grid.isWalkable(x, y) && !topology.isWall(x, y) && !taken[y * w + x]
                    && compartments[y * w + x] >= 0;
        }

        boolean nearForge(int x, int y) {
            return Math.abs(x - forgeX) <= FORGE_CLEAR && Math.abs(y - forgeY) <= FORGE_CLEAR;
        }
    }
}
