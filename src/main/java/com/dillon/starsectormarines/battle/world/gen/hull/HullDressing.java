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
 * The fallen hull dressed as the wrecked survey ship it is ({@code fallen-hull-nouns.md}, The ground): the hull's own
 * fittings ({@code fallen-hull.tileset.json}, {@code art-source/fallen-hull}) over the ship-interior kits the company's
 * deck is built of. A conduit run and frame ribs along the spine; what is left of the cargo and the survey racks
 * against each hold's walls, with a crushed bulkhead here and there; the launch cradles in the bay; consoles and a
 * sensor drum on the crew deck; the forge hall's plant in its aft corners and its coolant, hoppers and switchgear on
 * its flanks, cables run toward the autoforge; torn plating and scatter in every breach; the deck's wall-foot trim
 * ({@link ShipDeckTrim}); and wear, grime and debris strewn over every compartment. A piece the tileset lacks is left
 * out. A fitting drawn both ways ({@code -h}, {@code -v}) is laid the way its run lies on the map.
 *
 * <p>Only what has cover stands in the way: each such piece is kept off the docks, the ways in and the autoforge's
 * ground, and well inside the halls the derelict line walks ({@code derelict-watch.md}, section 3); the hull's laws
 * are checked after ({@link HullSiteCheck}). Everything else is flat on the deck. Placement is by coordinate hash, so it
 * draws from no stream.
 */
final class HullDressing {

    private static final String HULL = "fallen-hull.";
    /** Cargo left on the holds' walls, one cell each. */
    static final String[] CARGO = {"ship-hold.crate", "ship-hold.drum", "ship-hold.ammo-pair", "ship-hold.cable-coil",
            "ship-hold.toolbox", HULL + "survey-console"};
    /** Two-cell pieces against a hold's walls where they lie along it: cargo, survey racks, crushed bulkheads. */
    static final String[] CARGO_LONG = {"ship-hold.crate-stack", "ship-hold.pallet", HULL + "survey-rack",
            "ship-hold.netted-cargo", HULL + "survey-rack", HULL + "crushed-bulkhead-a", "ship-hold.ammo-cans",
            HULL + "crushed-bulkhead-b"};
    /** The forge hall's plant, in its aft corners. */
    static final String[] PLANT = {"ship-engineering.generator", "ship-engineering.cooling"};
    /** The forge hall's flanks, between the plant and the autoforge. */
    static final String[] WORKS = {HULL + "forge-coolant", HULL + "forge-hopper", "ship-engineering.switchgear",
            HULL + "forge-coolant", "ship-engineering.control"};
    /** The crew deck's fittings. */
    static final String[] CREW = {HULL + "survey-console", HULL + "survey-console", HULL + "conduit-junction"};
    /** Flat dressing over any deck: wear, grime, spilt cable, the crash's debris. */
    static final String[] DECK = {"ship-dressing.grime", "ship-dressing.debris", "ship-dressing.cable-spill",
            "ship-dressing.buckled-plate", "ship-dressing.condensation", "ship-wear.scuffs", "ship-wear.scorch",
            "ship-wear.patch"};
    static final String CONDUIT = HULL + "conduit", RIB = HULL + "rib", CRADLE = HULL + "hangar-cradle",
            SENSOR_DRUM = HULL + "sensor-drum", FORGE_CABLES = HULL + "forge-cables",
            BREACH_PLATING = HULL + "breach-plating", BREACH_SCATTER = HULL + "breach-scatter",
            BREACH_EDGE = HULL + "breach-edge", BREACH_SPILL = HULL + "breach-spill", SHEAR_EDGE = HULL + "shear-edge";
    /** The hull art's own interior dressing goes in under this prefix; {@code -wall} pieces stand at a wall's foot. */
    static final String INTERIOR = HULL + "interior-";
    /** Quarter turns: image top toward +y, -x, -y, +x. */
    private static final int[][] TURNS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    /** One flat piece tried per this many deck cells. UNTUNED. */
    static final int DECK_EVERY = 45;
    /** A frame rib stands every this many cells along the spine. */
    static final int RIB_EVERY = 6;
    /** Pieces in the way stand this far from a dock or way in, and the autoforge's ground runs this far round it. */
    static final int DOCK_CLEAR = 3, FORGE_CLEAR = 5;

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
        /** Whether the hull's length runs along the map's x axis. */
        final boolean lengthAlongX;

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
            lengthAlongX = frame.dx(1, 0) != 0;
        }

        void dress() {
            spine();
            for (Hall hall : hull.halls()) {
                switch (hall.use()) {
                    case HOLD -> cargo(hall);
                    case BAY -> bay(hall);
                    case CREW -> crew(hall);
                    case FORGE -> {
                        plant(hall);
                        works(hall);
                    }
                }
            }
            breaches();
            deck();
        }

        /** A fitting by its base id, turned the way its run lies: {@code -h} along the map's x, {@code -v} along y. */
        DoodadDef fitting(String id, boolean alongX) {
            DoodadDef def = registry.doodad(id);
            if (def != null) return def;
            DoodadDef turned = registry.doodad(id + (alongX ? "-h" : "-v"));
            return turned != null ? turned : null;
        }

        /** The spine's conduit run along its port wall's foot and a frame rib on its starboard wall every few cells. */
        void spine() {
            HullPlan.Rect s = HullPlan.SPINE;
            DoodadDef conduit = fitting(CONDUIT, lengthAlongX), rib = fitting(RIB, !lengthAlongX);
            for (int u = s.u0(); u <= s.u1(); u++) {
                if (conduit != null) flatAt(conduit, u, s.v0());
                if (rib != null && Math.floorMod(u, RIB_EVERY) == 0) flatAt(rib, u, s.v1());
            }
        }

        /**
         * What is left of a hold's cargo: stacks, survey racks and crushed bulkheads along its outer wall and both its
         * ends, away from its dock and any breach; two-cell pieces where they lie along the wall, single ones otherwise.
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

        /** The launch bay's two drone cradles, one at each end of its deck, lying along the hull. */
        void bay(Hall hall) {
            HullPlan.Rect f = hall.floor();
            DoodadDef cradle = fitting(CRADLE, lengthAlongX);
            if (cradle == null) return;
            int mid = (f.v0() + f.v1()) / 2;
            for (int u : new int[]{f.u0() + 3, f.u1() - 3}) {
                int x = frame.wx(u, mid), y = frame.wy(u, mid);
                int fx = Math.max(1, cradle.footprintCellsX), fy = Math.max(1, cradle.footprintCellsY);
                stand(cradle, x - fx / 2, y - fy / 2, fx, fy);
            }
        }

        /** The crew deck's consoles along its outer wall and its sensor drum in the far corner. */
        void crew(Hall hall) {
            HullPlan.Rect f = hall.floor();
            int outer = f.v0() >= HullPlan.BREADTH / 2 ? f.v1() : f.v0();
            for (int u = f.u0(); u <= f.u1(); u += 2) alongU(u, outer, new String[0], CREW, 0xC5);
            DoodadDef drum = registry.doodad(SENSOR_DRUM);
            if (drum == null) return;
            int inner = outer == f.v0() ? f.v0() + 2 : f.v1() - 2;
            int x = frame.wx(f.u1() - 1, inner), y = frame.wy(f.u1() - 1, inner);
            stand(drum, x - drum.footprintCellsX / 2, y - drum.footprintCellsY / 2, drum.footprintCellsX,
                    drum.footprintCellsY);
        }

        /** The forge hall's flanks, between the plant and the autoforge, and its cables run toward the forge. */
        void works(Hall hall) {
            HullPlan.Rect f = hall.floor();
            for (int u = f.u0() + 8; u < HullPlan.FORGE_U - 3; u += 3) {
                alongU(u, f.v0(), WORKS, new String[0], 0xE1);
                alongU(u, f.v1(), WORKS, new String[0], 0xE2);
            }
            // The cables run end to end along the keel toward the autoforge, where they lie that way on the map.
            DoodadDef cables = registry.doodad(FORGE_CABLES);
            if (cables == null || (cables.footprintCellsX > cables.footprintCellsY) != lengthAlongX) return;
            int step = Math.max(cables.footprintCellsX, cables.footprintCellsY);
            for (int k = 1; k <= 4; k++) {
                int u = HullPlan.FORGE_U - FORGE_CLEAR - k * step;
                flatAt(cables, frame.dx(1, 0) + frame.dy(1, 0) > 0 ? u : u + 1 - step, HullPlan.FORGE_V);
            }
        }

        /** A piece against a wall running along the hull at plan {@code (u, v)}: a long one if it lies that way. */
        void alongU(int u, int v, String[] longer, String[] single, int salt) {
            place(u, v, lengthAlongX, longer, single, salt);
        }

        /** A piece against a wall running across the hull. */
        void alongV(int u, int v, String[] longer, String[] single, int salt) {
            place(u, v, !lengthAlongX, longer, single, salt);
        }

        void place(int u, int v, boolean runsAlongX, String[] longer, String[] single, int salt) {
            if (nearWay(u, v)) return;
            int x = frame.wx(u, v), y = frame.wy(u, v);
            int hash = CrashSiteStage.hash(ctx.seed, x, y, salt);
            if (Math.floorMod(hash, 5) == 0) return;
            if (longer.length > 0) {
                DoodadDef def = fitting(longer[Math.floorMod(hash >> 4, longer.length)], runsAlongX);
                if (def != null && def.footprintCellsX != def.footprintCellsY
                        && (def.footprintCellsX > def.footprintCellsY) == runsAlongX) {
                    int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
                    // Back the piece onto the wall: it reaches inward from the cell on the wall.
                    int ax = runsAlongX ? x : (isWall(x - 1, y) ? x : x - fx + 1);
                    int ay = runsAlongX ? (isWall(x, y - 1) ? y : y - fy + 1) : y;
                    if (stand(def, ax, ay, fx, fy)) return;
                } else if (def != null && def.footprintCellsX == def.footprintCellsY && def.footprintCellsX > 1) {
                    int n = def.footprintCellsX;
                    int ax = isWall(x - 1, y) ? x : x - n + 1, ay = isWall(x, y - 1) ? y : y - n + 1;
                    if (stand(def, ax, ay, n, n)) return;
                }
            }
            if (single.length == 0) return;
            DoodadDef def = registry.doodad(single[Math.floorMod(hash >> 8, single.length)]);
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

        /**
         * Every breach: torn edges against the shell it was torn through, torn plating, spill and scatter over its floor
         * and the ground just outside; and beyond each section's broken end, the sheared deck, rib stubs and cable it
         * dragged out over the ground, along the break.
         */
        void breaches() {
            DoodadDef plating = registry.doodad(BREACH_PLATING), scatter = registry.doodad(BREACH_SCATTER);
            DoodadDef edge = registry.doodad(BREACH_EDGE), spill = registry.doodad(BREACH_SPILL);
            DoodadDef shear = registry.doodad(SHEAR_EDGE);
            if (plating == null) plating = registry.doodad("ship-dressing.debris");
            for (Entrance e : hull.entrances()) {
                if (!e.torn()) continue;
                HullPlan.Section section = HullPlan.section(e.door().u0());
                Area area = frame.area(section, e.door());
                for (int y = area.bottom() - 2; y <= area.top() + 2; y++) {
                    for (int x = area.left() - 2; x <= area.right() + 2; x++) {
                        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                        if (edge != null && area.contains(x, y)) {
                            for (int turn = 0; turn < 4; turn++) {
                                int nx = x + TURNS[turn][0], ny = y + TURNS[turn][1];
                                if (grid.inBounds(nx, ny) && topology.isWall(nx, ny)) {
                                    ctx.doodads.add(0, new Doodad(x, y, edge, turn));
                                }
                            }
                        }
                        int hash = Math.floorMod(CrashSiteStage.hash(ctx.seed, x, y, 0xB5), 9);
                        if (hash == 0 && plating != null) flat(plating, x, y, false);
                        else if (hash == 1 && spill != null) flat(spill, x, y, false);
                        else if (hash <= 3 && scatter != null) flat(scatter, x, y, false);
                    }
                }
                if (!e.broken() || shear == null) continue;
                // The ground one cell beyond the cut face, the whole breadth of the section, the edge's top toward it.
                int face = e.du() > 0 ? section.u1() + 1 : section.u0() - 1;
                int sx = frame.dx(e.du(), 0), sy = frame.dy(e.du(), 0);
                int turn = 0;
                for (int t = 0; t < 4; t++) if (TURNS[t][0] == -sx && TURNS[t][1] == -sy) turn = t;
                for (int v = 0; v < HullPlan.BREADTH; v++) {
                    int x = frame.wx(section, face, v), y = frame.wy(section, face, v);
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || taken[y * w + x]) continue;
                    if (!topology.isWall(x - sx, y - sy) && hull.at(face - e.du(), v) == HullPlan.Kind.OUTSIDE) continue;
                    ctx.doodads.add(0, new Doodad(x, y, shear, turn));
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

        /** A flat piece at plan cell {@code (u, v)}, off doorways. */
        void flatAt(DoodadDef def, int u, int v) {
            flat(def, frame.wx(u, v), frame.wy(u, v), true);
        }

        /** A flat piece at {@code (x0, y0)} where its footprint is free deck (in one compartment if {@code same}). */
        void flat(DoodadDef def, int x0, int y0, boolean same) {
            int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
            int room = compartments[y0 * w + x0];
            for (int y = y0; y < y0 + fy; y++) {
                for (int x = x0; x < x0 + fx; x++) {
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || topology.isWall(x, y)
                            || grid.isDoorway(x, y) || taken[y * w + x]) return;
                    if (same && compartments[y * w + x] != room) return;
                }
            }
            for (int y = y0; y < y0 + fy; y++) for (int x = x0; x < x0 + fx; x++) taken[y * w + x] = true;
            int hash = CrashSiteStage.hash(ctx.seed, x0, y0, 0xF1);
            ctx.doodads.add(0, new Doodad(x0, y0, def, 0, (hash & 1) == 0 && def.footprintCellsX > 1));
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
