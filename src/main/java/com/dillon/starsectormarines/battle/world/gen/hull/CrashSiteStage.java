package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Entrance;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Hall;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan.Rect;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Apron;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Interior;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.LeafDoor;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.PlantHall;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.PlantStage;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Lays a fallen hull over the grown country ({@code fallen-hull-nouns.md}, Fallen hull): the trench it ploughed behind
 * its stern, the debris field of hull fragments it shed along it, and the hull itself, its {@link HullPlan} carved out
 * of solid hull in the ruins megastructure's materials, with an apron of open ground before each way in. Runs before
 * the tactical link and finalize, so the hull's shells are ordinary buildings and its fragments ordinary walls, and
 * the mission layout's structures are sited round it. {@link CrashSiteFinishStage} roofs it and lays the claim on the
 * finished ground.
 *
 * <p>What it overwrites is gone: props, places and tactical nodes on the hull, its trench and its aprons are taken off.
 * A hull laid over a road is reported (the road's graph would run through a wall), never hidden.
 *
 * <p>Its own stream is derived from the seed, so the country under it draws exactly what it would without a hull.
 */
public final class CrashSiteStage implements GenStage {

    /**
     * The hull's walls and deck: the installation wall in the hull's own plate ({@code art-source/fallen-hull/tileset.py}),
     * whose id roofs the hull in hull plating ({@code RoofStyle.HULL}), over the ruins megastructure's deck.
     */
    static final String HULL_WALL = "installation.dark-concrete-wall@fallen-hull";
    static final String HULL_FLOOR = "installation.concrete-floor";
    /** The forge hall's deck: plate, so the vault reads apart from the holds. */
    static final String FORGE_FLOOR = "sky-port.deck-plate";
    static final String RUBBLE_BED = "pavement.gravel.cracked";
    static final String TRODDEN = "pavement.dirt.worn";
    static final String SCRAP = "doodad.industrial-scrap-pile";
    /** The trench floor's ploughed earth ({@code art-source/fallen-hull/tileset.py}); trodden earth where it is missing. */
    static final String TRENCH = "fallen-hull.trench";
    /** The pieces the hull shed on its trench floor, by hash; the plain scrap pile where they are missing. */
    static final String[] SHED = {"fallen-hull.debris-plate-shard-a", "fallen-hull.debris-plate-shard-b",
            "fallen-hull.debris-scrap-heap", "fallen-hull.debris-girder", "fallen-hull.debris-pod",
            "fallen-hull.debris-bell"};
    /** The interior's building kind: the ruins megastructure's, so the ruins roof joins its mass ({@code RuinsRoofStage}). */
    static final BuildingKind HULL_KIND = BuildingKind.FORTIFIED;

    /** The trench's half width at the stern and at its far end, where the hull first touched down. UNTUNED. */
    static final int TRENCH_HALF_STERN = 12, TRENCH_HALF_END = 4;
    /** Cells of thrown-up spoil either side of the trench. */
    static final int LIP = 2;
    /** An apron: this deep outward from its door, this wide across it, this far from the hull's face. */
    static final int APRON_DEPTH = 13, APRON_HALF = 4, APRON_GAP = 1;
    /** Hull fragments tried and laid along the trench. UNTUNED. */
    static final int DEBRIS = 14, DEBRIS_TRIES = 240;

    private static final long SALT = 0x48_75_6C_6C_43_72_61L;

    /** What this stage laid, for {@link CrashSiteFinishStage}. */
    record Laid(HullSitePlan plan, Area footprint, BitSet hullCells, Area trench, BitSet trenchCells, BitSet aprons,
                Interior interior, Area forgeHall, Cell forge, Area bay, List<Area> debris, List<String> problems) { }

    @Override
    public void run(GenContext ctx) {
        HullSitePlan plan = ctx.get(HullKeys.PLAN);
        if (plan == null) return;
        ctx.put(HullKeys.LAID, new Layout(ctx, plan).lay());
    }

    /**
     * The worked fields the hull came down on are no fields: a plot the hull, its trench or an apron touches is taken
     * off the map's fields, so no planet skin grows a covered farm on the hull.
     */
    static void dropFields(GenContext ctx, BitSet... covers) {
        List<RuralLayout.Plot> fields = ctx.get(BspKeys.WORKED_FIELDS);
        if (fields == null || fields.isEmpty()) return;
        int w = ctx.width;
        List<RuralLayout.Plot> kept = new ArrayList<>();
        for (RuralLayout.Plot plot : fields) {
            boolean hit = false;
            for (int y = Math.max(0, plot.y0()); y <= Math.min(ctx.height - 1, plot.y1()) && !hit; y++) {
                for (int x = Math.max(0, plot.x0()); x <= Math.min(w - 1, plot.x1()) && !hit; x++) {
                    for (BitSet cover : covers) hit |= cover.get(y * w + x);
                }
            }
            if (!hit) kept.add(plot);
        }
        ctx.put(BspKeys.WORKED_FIELDS, kept);
    }

    /** A coordinate hash off the seed: draws nothing from any stream, so the ground under the hull is unmoved. */
    static int hash(long seed, int x, int y, int salt) {
        long z = seed ^ SALT ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) y * 0xC2B2AE3D27D4EB4FL) ^ salt;
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return (int) (z ^ (z >>> 33));
    }

    /** The trench floor at a cell: ploughed earth, broken here and there by rubble. */
    static GroundKind trenchGround(long seed, int x, int y) {
        return Math.floorMod(hash(seed, x, y, 0x5C), 5) == 0 ? GroundKind.RUBBLE : GroundKind.DIRT;
    }

    /**
     * The surface the trench floor is drawn in: its own ploughed earth, over its broken cells too (they stay rubble
     * underfoot), else as {@link #block}.
     */
    static String trenchBlock(GroundKind kind) {
        TileRegistry registry = TileRegistry.installed();
        boolean floor = kind == GroundKind.DIRT || kind == GroundKind.RUBBLE;
        return floor && registry != null && registry.hasBlock(TRENCH) ? TRENCH : block(kind);
    }

    /** The surface a hull ground kind is drawn in: trodden earth, a rubble bed, or the kind's own block. */
    static String block(GroundKind kind) {
        TileRegistry registry = TileRegistry.installed();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        return kind == GroundKind.DIRT && registry != null && registry.hasBlock(TRODDEN) ? TRODDEN
                : kind == GroundKind.RUBBLE ? RUBBLE_BED
                : mapping == null ? null : mapping.groundBlockId(kind);
    }

    /** Where plan cell {@code (u, v)} falls on the map: the hull's middle at {@code (x, y)}, its bow along the heading. */
    record Frame(int x, int y, Direction heading) {
        int wx(int u, int v) { return x + (u - HullPlan.LENGTH / 2) * heading.dx - (v - HullPlan.BREADTH / 2) * heading.dy; }
        int wy(int u, int v) { return y + (u - HullPlan.LENGTH / 2) * heading.dy + (v - HullPlan.BREADTH / 2) * heading.dx; }
        /** A plan rectangle's cells on the map. */
        Area area(Rect r) {
            int ax = wx(r.u0(), r.v0()), ay = wy(r.u0(), r.v0()), bx = wx(r.u1(), r.v1()), by = wy(r.u1(), r.v1());
            return new Area(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by));
        }
        /** The map step of a plan step. */
        int dx(int du, int dv) { return du * heading.dx - dv * heading.dy; }
        int dy(int du, int dv) { return du * heading.dy + dv * heading.dx; }
    }

    private static final class Layout {
        final GenContext ctx;
        final NavigationGrid grid;
        final CellTopology topology;
        final HullSitePlan plan;
        final Frame frame;
        final HullPlan hull = HullPlan.plan();
        final int w, h;
        final Random rng;
        final BitSet hullCells, trenchCells, lipCells, apronCells, drafted;
        final List<String> problems = new ArrayList<>();
        /** The landing's promised field of fire, kept clear of the hull's wreckage; null where the plan promises none. */
        final FieldOfFire field;

        Layout(GenContext ctx, HullSitePlan plan) {
            this.ctx = ctx;
            this.grid = ctx.grid;
            this.topology = ctx.topology;
            this.plan = plan;
            this.frame = new Frame(plan.x(), plan.y(), plan.heading());
            this.w = ctx.width;
            this.h = ctx.height;
            this.rng = new Random(ctx.seed ^ SALT);
            this.field = FieldOfFire.of(ctx.get(BspKeys.PRECINCTS));
            hullCells = new BitSet(w * h);
            trenchCells = new BitSet(w * h);
            lipCells = new BitSet(w * h);
            apronCells = new BitSet(w * h);
            drafted = new BitSet(w * h);
        }

        int i(int x, int y) { return y * w + x; }

        boolean in(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h; }

        Laid lay() {
            Area footprint = frame.area(new Rect(0, 0, HullPlan.LENGTH - 1, HullPlan.BREADTH - 1));
            if (footprint.left() < 2 || footprint.bottom() < 2 || footprint.right() >= w - 2 || footprint.top() >= h - 2) {
                throw new IllegalStateException("the hull at " + plan.x() + "," + plan.y() + " runs off the map");
            }
            for (int u = 0; u < HullPlan.LENGTH; u++) {
                for (int v = 0; v < HullPlan.BREADTH; v++) {
                    if (hull.at(u, v) != HullPlan.Kind.OUTSIDE) hullCells.set(i(frame.wx(u, v), frame.wy(u, v)));
                }
            }
            Area trench = trench();
            reportField();
            List<Apron> aprons = new ArrayList<>();
            List<Area> entrances = new ArrayList<>();
            for (int k = 0; k < hull.entrances().size(); k++) {
                Entrance e = hull.entrances().get(k);
                Apron apron = apron(k, e);
                aprons.add(apron);
                entrances.add(frame.area(e.door()));
            }
            boolean[][] roadsBefore = RoadReservation.mask(ctx.get(BspKeys.ROAD_GRAPH), w, h);
            cutRoads();
            clear(footprint);
            digTrench();
            raiseHull();
            for (Apron apron : aprons) openApron(apron.ground(), roadsBefore);
            List<LeafDoor> doors = doors();
            finishMasks();
            List<Area> debris = debris(trench, footprint);

            List<Area> chambers = new ArrayList<>();
            List<PlantHall> halls = new ArrayList<>();
            Area forgeHall = null, bay = null;
            for (Hall hall : hull.halls()) {
                Area floor = frame.area(hall.floor());
                chambers.add(floor);
                switch (hall.use()) {
                    case HOLD -> halls.add(new PlantHall(HullSite.HULL + "." + hall.name(), PlantStage.STORAGE, floor));
                    case BAY -> {
                        halls.add(new PlantHall(HullSite.BAY, PlantStage.INTAKE, floor));
                        bay = floor;
                    }
                    case FORGE -> {
                        halls.add(new PlantHall(HullSite.FORGE, PlantStage.WORKS, floor));
                        forgeHall = floor;
                    }
                    case CREW -> { }
                }
            }
            Interior interior = new Interior(HullSite.INTERIOR, footprint, 0, entrances, aprons, chambers, halls, doors);
            Cell forge = new Cell(frame.wx(HullPlan.FORGE_U, HullPlan.FORGE_V), frame.wy(HullPlan.FORGE_U, HullPlan.FORGE_V));
            return new Laid(plan, footprint, hullCells, trench, trenchCells, apronCells, interior, forgeHall, forge, bay,
                    debris, problems);
        }

        // ---- the trench ------------------------------------------------------------------------

        /** Marks the trench and its spoil lips behind the stern; their bounding box. */
        Area trench() {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            int mid = HullPlan.BREADTH / 2;
            for (int t = 1; t <= plan.trench(); t++) {
                int half = TRENCH_HALF_STERN + (TRENCH_HALF_END - TRENCH_HALF_STERN) * t / plan.trench();
                half += Math.floorMod(hash(t, 0, 0x7E), 3) - 1;
                for (int dv = -half - LIP; dv < half + LIP; dv++) {
                    int x = frame.wx(-t, mid + dv), y = frame.wy(-t, mid + dv);
                    if (!in(x, y) || x < 1 || y < 1 || x >= w - 1 || y >= h - 1) continue;
                    boolean lip = dv < -half || dv >= half;
                    (lip ? lipCells : trenchCells).set(i(x, y));
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            if (minX > maxX) throw new IllegalStateException("the hull's trench runs off the map");
            return new Area(minX, minY, maxX, maxY);
        }

        void digTrench() {
            for (int k = trenchCells.nextSetBit(0); k >= 0; k = trenchCells.nextSetBit(k + 1)) {
                int x = k % w, y = k / w;
                free(x, y);
                GroundKind kind = trenchGround(ctx.seed, x, y);
                ground(x, y, kind);
                String floor = trenchBlock(kind);
                if (floor != null) topology.setSurface(x, y, topology.surfaceIndex(floor));
            }
            for (int k = lipCells.nextSetBit(0); k >= 0; k = lipCells.nextSetBit(k + 1)) {
                int x = k % w, y = k / w;
                if (hullCells.get(k)) continue;
                free(x, y);
                ground(x, y, GroundKind.RUBBLE);
            }
            // Scrap the hull shed as it ploughed, loose on the trench floor.
            for (int k = trenchCells.nextSetBit(0); k >= 0; k = trenchCells.nextSetBit(k + 1)) {
                if (Math.floorMod(hash(k % w, k / w, 0x5D), 70) != 0 || inField(k % w, k / w, 3)) continue;
                String piece = SHED[Math.floorMod(hash(k % w, k / w, 0x5E), SHED.length)];
                TileRegistry registry = TileRegistry.installed();
                loose(registry != null && registry.doodad(piece) != null ? piece : SCRAP, k % w, k / w);
            }
        }

        // ---- the hull ---------------------------------------------------------------------------

        void raiseHull() {
            Hall forge = hull.hall("forge");
            for (int u = 0; u < HullPlan.LENGTH; u++) {
                for (int v = 0; v < HullPlan.BREADTH; v++) {
                    HullPlan.Kind kind = hull.at(u, v);
                    if (kind == HullPlan.Kind.OUTSIDE) continue;
                    int x = frame.wx(u, v), y = frame.wy(u, v);
                    switch (kind) {
                        case HULL -> wall(x, y);
                        case FLOOR -> deck(x, y, forge.floor().contains(u, v) ? FORGE_FLOOR : HULL_FLOOR);
                        case TORN -> deck(x, y, RUBBLE_BED);
                        default -> { }
                    }
                }
            }
        }

        void wall(int x, int y) {
            removeProps(x, y);
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setDoorway(x, y, false);
            topology.setWall(x, y, true);
            topology.setFixture(x, y, false);
            topology.setWallRemnant(x, y, null);
            topology.setGroundKind(x, y, GroundKind.STRIPED);
            topology.setBuildingKindHint(x, y, HULL_KIND);
            topology.setSurface(x, y, topology.surfaceIndex(HULL_WALL));
            ctx.markMadeGround(x, y);
            drafted.set(i(x, y));
        }

        void deck(int x, int y, String surface) {
            free(x, y);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            topology.setBuildingKindHint(x, y, HULL_KIND);
            topology.setSurface(x, y, topology.surfaceIndex(surface));
            drafted.set(i(x, y));
        }

        /**
         * The leaf doors on the hull's joins and ways in ({@code ruins-expedition.md}, The ruins' doors are leaf doors):
         * each across the door's cells next to the hall it opens, cargo doors four cells and wider. A door here is
         * powered or forced open, never dead: the hull is live, and its watch walks through its own doors.
         */
        List<LeafDoor> doors() {
            List<LeafDoor> out = new ArrayList<>();
            for (Hall hall : hull.halls()) out.add(door(hall.door(), hall.door().u0() == hall.door().u1()));
            for (Entrance e : hull.entrances()) out.add(door(e.door(), e.du() != 0));
            return out;
        }

        /** A door across {@code r}: one cell deep, at the end of the way nearest the hull's middle. */
        LeafDoor door(Rect r, boolean throughU) {
            Rect span = throughU ? new Rect(r.u1(), r.v0(), r.u1(), r.v1())
                    : r.v0() >= HullPlan.BREADTH / 2 ? new Rect(r.u0(), r.v0(), r.u1(), r.v0())
                    : new Rect(r.u0(), r.v1(), r.u1(), r.v1());
            Area area = frame.area(span);
            boolean alongX = area.width() > area.height();
            int width = HullPlan.doorWidth(r, throughU);
            RuinsDelve.DoorStance stance = Math.floorMod(hash(area.left(), area.bottom(), 0xD0A), 4) == 0
                    ? RuinsDelve.DoorStance.FORCED : RuinsDelve.DoorStance.ANSWERS;
            for (int y = area.bottom(); y <= area.top(); y++) {
                for (int x = area.left(); x <= area.right(); x++) grid.setDoorway(x, y, true);
            }
            return new LeafDoor(area, alongX, width >= 4, stance);
        }

        /** Wall faces and door jambs for every shell cell this stage stamped. */
        void finishMasks() {
            for (int k = drafted.nextSetBit(0); k >= 0; k = drafted.nextSetBit(k + 1)) {
                int x = k % w, y = k / w;
                int mask = 0;
                if (topology.isWall(x, y)) {
                    for (Direction side : Direction.CARDINALS) {
                        int nx = x + side.dx, ny = y + side.dy;
                        if (!grid.inBounds(nx, ny) || !topology.isWall(nx, ny)) mask |= bit(side);
                    }
                } else if (grid.isDoorway(x, y)) {
                    boolean upright = topology.isWall(x, y - 1) || topology.isWall(x, y + 1);
                    mask = bit(upright ? Direction.E : Direction.N);
                    for (Direction side : Direction.CARDINALS) {
                        int nx = x + side.dx, ny = y + side.dy;
                        if (grid.inBounds(nx, ny) && !topology.hasBuildingKindHint(nx, ny)) {
                            mask = bit(side);
                            break;
                        }
                    }
                }
                topology.setWallDirMask(x, y, mask);
            }
        }

        static int bit(Direction side) {
            return switch (side) {
                case N -> CellTopology.WALL_DIR_N;
                case E -> CellTopology.WALL_DIR_E;
                case S -> CellTopology.WALL_DIR_S;
                default -> CellTopology.WALL_DIR_W;
            };
        }

        // ---- aprons ----------------------------------------------------------------------------

        /** The apron before entrance {@code k}: open ground outward from its door, with two berths for the boat. */
        Apron apron(int k, Entrance e) {
            Rect d = e.door();
            int mu = (d.u0() + d.u1()) / 2, mv = (d.v0() + d.v1()) / 2;
            // The door's outer face, then out past the hull's face.
            int fu = e.du() < 0 ? d.u0() : e.du() > 0 ? d.u1() : mu;
            int fv = e.dv() < 0 ? d.v0() : e.dv() > 0 ? d.v1() : mv;
            int nu = fu + e.du() * (1 + APRON_GAP), nv = fv + e.dv() * (1 + APRON_GAP);
            int au = nu + e.du() * (APRON_DEPTH - 1), av = nv + e.dv() * (APRON_DEPTH - 1);
            Rect r = e.du() != 0
                    ? new Rect(Math.min(nu, au), mv - APRON_HALF, Math.max(nu, au), mv + APRON_HALF)
                    : new Rect(mu - APRON_HALF, Math.min(nv, av), mu + APRON_HALF, Math.max(nv, av));
            Area ground = frame.area(r);
            int sx = frame.dx(e.du(), e.dv()), sy = frame.dy(e.du(), e.dv());
            LandingPad.Approach approach = sx > 0 ? LandingPad.Approach.EAST : sx < 0 ? LandingPad.Approach.WEST
                    : sy > 0 ? LandingPad.Approach.NORTH : LandingPad.Approach.SOUTH;
            int nearX = frame.wx(nu + e.du() * 2, nv + e.dv() * 2), nearY = frame.wy(nu + e.du() * 2, nv + e.dv() * 2);
            int farX = frame.wx(au - e.du() * 2, av - e.dv() * 2), farY = frame.wy(au - e.du() * 2, av - e.dv() * 2);
            LandingArea lift = new LandingArea("hull-entrance-" + (k + 1),
                    LandingPad.civilian(nearX, nearY, approach), LandingPad.civilian(farX, farY, approach));
            for (int y = ground.bottom(); y <= ground.top(); y++) {
                for (int x = ground.left(); x <= ground.right(); x++) if (in(x, y)) apronCells.set(i(x, y));
            }
            return new Apron(Interior.apronName(k), frame.area(d), ground, lift);
        }

        /** Clears an apron to open ground; a road across it stays a road. */
        void openApron(Area ground, boolean[][] roads) {
            for (int y = Math.max(1, ground.bottom()); y <= Math.min(h - 2, ground.top()); y++) {
                for (int x = Math.max(1, ground.left()); x <= Math.min(w - 2, ground.right()); x++) {
                    if (hullCells.get(i(x, y)) || trenchCells.get(i(x, y))) continue;
                    if (roads[x][y]) {
                        removeProps(x, y);
                        continue;
                    }
                    free(x, y);
                    ground(x, y, GroundKind.DIRT);
                }
            }
        }

        // ---- debris ------------------------------------------------------------------------------

        /**
         * Hull fragments the hull shed as it came down, standing on open ground beside the trench and round the hull:
         * short runs of hull plate, cover and walls, never on a road, a building, the trench or an apron, and never
         * touching another wall, so no run can close a way.
         */
        List<Area> debris(Area trench, Area footprint) {
            boolean[][] roads = RoadReservation.mask(ctx.get(BspKeys.ROAD_GRAPH), w, h);
            List<Area> out = new ArrayList<>();
            int mid = HullPlan.BREADTH / 2;
            for (int tries = 0; tries < DEBRIS_TRIES && out.size() < DEBRIS; tries++) {
                int t = rng.nextInt(plan.trench() + HullPlan.LENGTH) - plan.trench();
                int side = rng.nextBoolean() ? 1 : -1;
                int off = (t < 0 ? TRENCH_HALF_STERN + LIP + 2 : HullPlan.BREADTH / 2 + 3) + rng.nextInt(16);
                int length = 3 + rng.nextInt(4);
                boolean alongU = rng.nextBoolean();
                int x0 = frame.wx(t, mid + side * off), y0 = frame.wy(t, mid + side * off);
                int sx = alongU ? frame.dx(1, 0) : frame.dx(0, 1), sy = alongU ? frame.dy(1, 0) : frame.dy(0, 1);
                boolean fits = true;
                for (int k = 0; k < length && fits; k++) fits = clearForDebris(x0 + sx * k, y0 + sy * k, roads);
                if (!fits) continue;
                int x1 = x0 + sx * (length - 1), y1 = y0 + sy * (length - 1);
                for (int k = 0; k < length; k++) {
                    int x = x0 + sx * k, y = y0 + sy * k;
                    plate(x, y);
                }
                // Rubble round the fragment's foot, where it dug in.
                for (int k = -1; k <= length; k++) {
                    for (int s = -1; s <= 1; s++) {
                        int x = x0 + sx * k - sy * s, y = y0 + sy * k + sx * s;
                        if (in(x, y) && grid.isWalkable(x, y)) ground(x, y, GroundKind.RUBBLE);
                    }
                }
                out.add(new Area(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1)));
            }
            return out;
        }

        boolean clearForDebris(int x, int y, boolean[][] roads) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 2 || ny < 2 || nx >= w - 2 || ny >= h - 2) return false;
                    if (inField(nx, ny, 0)) return false;
                    int k = i(nx, ny);
                    if (hullCells.get(k) || trenchCells.get(k) || apronCells.get(k) || roads[nx][ny]) return false;
                    if (!grid.isWalkable(nx, ny) || topology.isWall(nx, ny) || topology.hasBuildingKindHint(nx, ny)) {
                        return false;
                    }
                    GroundKind g = topology.getGroundKind(nx, ny);
                    if (g == GroundKind.INDOOR || g == GroundKind.WATER || g == GroundKind.STREET) return false;
                }
            }
            return true;
        }

        /** One cell of a fallen hull plate: a freestanding wall in the hull's material on a rubble bed. */
        void plate(int x, int y) {
            removeProps(x, y);
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setDoorway(x, y, false);
            topology.setFixture(x, y, false);
            topology.setWallRemnant(x, y, null);
            topology.setSurface(x, y, topology.surfaceIndex(HULL_WALL));
            topology.setWallGroundSurface(x, y, RUBBLE_BED);
            ctx.markMadeGround(x, y);
        }

        /** Whether the cell is within {@code margin} of the landing's field of fire. */
        boolean inField(int x, int y, int margin) {
            if (field == null) return false;
            long dx = x - field.centerX(), dy = y - field.centerY(), r = field.radius() + margin;
            return dx * dx + dy * dy <= r * r;
        }

        /** A hull or trench in the landing's promised field of fire is a hull put in the wrong place. */
        void reportField() {
            if (field == null) return;
            int hit = 0;
            for (BitSet cells : new BitSet[]{hullCells, trenchCells, lipCells}) {
                for (int k = cells.nextSetBit(0); k >= 0; k = cells.nextSetBit(k + 1)) if (inField(k % w, k / w, 2)) hit++;
            }
            if (hit > 0) problems.add("the hull and its trench lie on " + hit + " cells of the landing's field of fire (" + field.centerX() + "," + field.centerY() + " r" + field.radius() + ")");
        }

        // ---- clearing ----------------------------------------------------------------------------

        /** Places, tactical nodes and props on the hull, its trench and its aprons are taken off: the hull came down there. */
        void clear(Area footprint) {
            ctx.pois.removeIf(this::overwritten);
            ctx.tactical.removeIf(node -> covered(node.anchorX, node.anchorY));
            dropFields(ctx, hullCells, trenchCells, lipCells, apronCells);
        }

        boolean overwritten(PointOfInterest poi) {
            int x0 = Math.min(poi.left, poi.right), x1 = Math.max(poi.left, poi.right);
            int y0 = Math.min(poi.top, poi.bottom), y1 = Math.max(poi.top, poi.bottom);
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (covered(x, y)) return true;
            return false;
        }

        boolean covered(int x, int y) {
            if (!in(x, y)) return false;
            int k = i(x, y);
            return hullCells.get(k) || trenchCells.get(k) || lipCells.get(k) || apronCells.get(k);
        }

        /**
         * The roads the hull came down across are cut: every road-graph edge with a cell under the hull is taken out of
         * the graph (and a node left with no edge with it), so no convoy or parked vehicle is routed through its wall.
         * The trench cuts nothing: it is open ground a truck crosses.
         */
        void cutRoads() {
            RoadGraph graph = ctx.get(BspKeys.ROAD_GRAPH);
            if (graph == null || graph.edges().isEmpty()) return;
            Map<RoadGraph.Node, RoadGraph.Node> copies = new HashMap<>();
            List<RoadGraph.Edge> kept = new ArrayList<>();
            int cut = 0;
            for (RoadGraph.Edge e : graph.edges()) {
                boolean under = false;
                for (int k = 0; k < e.cellsX.length && !under; k++) {
                    under = in(e.cellsX[k], e.cellsY[k]) && hullCells.get(i(e.cellsX[k], e.cellsY[k]));
                }
                if (under) {
                    cut++;
                    continue;
                }
                kept.add(new RoadGraph.Edge(e.id, copy(copies, e.a), copy(copies, e.b), e.cellsX, e.cellsY));
            }
            if (cut == 0) return;
            List<RoadGraph.Node> nodes = new ArrayList<>();
            for (RoadGraph.Node n : graph.nodes()) {
                RoadGraph.Node c = copies.get(n);
                if (c != null || n.degree() == 0) nodes.add(c != null ? c : copy(copies, n));
            }
            ctx.put(BspKeys.ROAD_GRAPH, new RoadGraph(nodes, kept));
        }

        private static RoadGraph.Node copy(Map<RoadGraph.Node, RoadGraph.Node> copies, RoadGraph.Node n) {
            return copies.computeIfAbsent(n, k -> new RoadGraph.Node(k.id, k.cellX, k.cellY, k.perimeter));
        }

        // ---- cell primitives ---------------------------------------------------------------------

        void free(int x, int y) {
            removeProps(x, y);
            grid.setWalkableFloor(x, y);
            grid.setSeeThrough(x, y, true);
            grid.setDoorway(x, y, false);
            topology.setWall(x, y, false);
            topology.setFixture(x, y, false);
            topology.setWallRemnant(x, y, null);
            topology.setBuildingKindHint(x, y, null);
            ctx.markMadeGround(x, y);
        }

        void ground(int x, int y, GroundKind kind) {
            topology.setGroundKind(x, y, kind);
            String block = block(kind);
            if (block != null) topology.setSurface(x, y, topology.surfaceIndex(block));
            ctx.markMadeGround(x, y);
        }

        void removeProps(int x, int y) {
            for (int k = ctx.doodads.size() - 1; k >= 0; k--) {
                Doodad d = ctx.doodads.get(k);
                if (x < d.cellX || y < d.cellY || x >= d.cellX + Math.max(1, d.footprintCellsX)
                        || y >= d.cellY + Math.max(1, d.footprintCellsY)) continue;
                ctx.doodads.remove(k);
                for (int fy = d.cellY; fy < d.cellY + Math.max(1, d.footprintCellsY); fy++) {
                    for (int fx = d.cellX; fx < d.cellX + Math.max(1, d.footprintCellsX); fx++) {
                        if (!grid.inBounds(fx, fy) || !topology.isFixture(fx, fy)) continue;
                        topology.setFixture(fx, fy, false);
                        grid.setWalkableFloor(fx, fy);
                        grid.setSeeThrough(fx, fy, true);
                    }
                }
            }
        }

        void loose(String id, int x, int y) {
            TileRegistry registry = TileRegistry.installed();
            DoodadDef def = registry == null ? null : registry.doodad(id);
            if (def == null || !grid.inBounds(x, y) || !grid.isWalkable(x, y)) return;
            ctx.doodads.add(new Doodad(x, y, def));
        }

        int hash(int x, int y, int salt) { return CrashSiteStage.hash(ctx.seed, x, y, salt); }
    }
}
