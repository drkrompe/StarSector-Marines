package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.OreLine;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * The yard of light industry's fabrication works ({@link LightIndustryWorks}), dressed for the work it does once its
 * halls stand: what tells a raider, from the yard, what this place is and which way the work runs.
 *
 * <ul>
 *   <li><b>The parts line.</b> An overhead conveyor links the halls in the order the work passes: receiving dock to
 *   parts stores, stores to each fabrication shop, each shop to the assembly hall, assembly to finishing, finishing
 *   to each dispatch shed. Its trestle legs stand in the yard as cover.</li>
 *   <li><b>The feeders' trucks.</b> The feeding shops' trucks are queued at the receiving dock, backed in to
 *   unload ({@link #MAX_TRUCKS}, alongside where there is no room to back in), with the stock they brought staged
 *   beside them: bar stock, cable reels, drums, pallets.</li>
 *   <li><b>The work, outside every door.</b> Open parts racks at the stores; a lathe and a mill working under each
 *   shop's eaves with its part bins, bar stock and offcuts; a roller line of totes and the assembly and inspection
 *   benches at assembly; paint drums and the coolant plant at finishing; cased pallets, crates, a loader and a van at
 *   each dispatch shed. A pallet carries its load: cartons coming in, cases going out.</li>
 *   <li><b>The stock yards.</b> The open works ground between the halls stacked with pallets in rows, cartons
 *   by receiving, totes among the shops, cases by dispatch: a planet's throughput, not a town's.</li>
 *   <li><b>The shifts.</b> Mess tables and a bin outside the canteen, and walkways from its door to each shop's,
 *   kept clear of stock.</li>
 * </ul>
 *
 * Each hall's kit is set by its doors first, then its stock again along what is left of its faces ({@link #FILL}).
 * Everything stands against the wall of the hall it belongs to, off its doors and off the roads, with a lane at least
 * {@link #LANE} cells wide left open beyond it, so the dressing never closes a way through the works. Laid by
 * {@link LightIndustryWorks#dressWorks} once the halls stand; its draws come from the map's own seed.
 */
public final class FabricationYard {

    /** The dressing's name: the key its draws are seeded from. */
    public static final String NAME = "fabrication-yard";

    /**
     * Trucks queued at the receiving dock: the hub is fed by more shops than its dock takes at once
     * ({@link com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate#feedersFor} is never
     * under three), so its face is full. UNTUNED.
     */
    static final int MAX_TRUCKS = 4;
    /** Cells of open ground left beyond anything set against a wall. UNTUNED. */
    static final int LANE = 3;
    /** Cells kept clear round a doorway's outside cell. UNTUNED. */
    static final int DOOR_CLEAR = 2;
    /** Stock yard blocks laid at most. UNTUNED. */
    static final int STOCK_YARDS = 3;
    /** Stock yard blocks tried, largest first, in cells: rows of pallets with their aisles. UNTUNED. */
    static final int[][] YARD_SIZES = {{14, 10}, {11, 6}, {8, 6}};
    /** Bays in the assembly hall at most. UNTUNED. */
    static final int BAYS = 4;
    /** Cells a loading yard reaches out from its hall toward the road it is served from, at most. UNTUNED. */
    static final int YARD_REACH = 16;
    /** How far above the ground a hull on its station stands, in cells: taller than a man, so it blocks sight. */
    static final float HULL_HEIGHT = 1.5f;
    /** Times a hall's own stock is set again along what is left of its faces, at most. UNTUNED. */
    static final int FILL = 2;
    /** Cells a link of the parts line may run beyond twice its straight distance. UNTUNED. */
    static final int LINK_SLACK = 12;

    /** The parts line's art, by shape; the ore conveyor's stands in until the parts line's own is drawn. */
    static final String LINE_ART = "parts-line.";
    static final String LINE_STAND_IN = "ore-conveyor.";

    /** The ground the yard is dressed on, as the planner reads it. */
    public interface Ground {
        int width();

        int height();

        /** Whether a prop may stand on the cell: walkable, and no doorway, fixture or wall. */
        boolean open(int x, int y);

        boolean road(int x, int y);

        /** Whether the cell is any building's, its walls included, or anything else no one walks through. */
        boolean building(int x, int y);

        /** Whether the cell is the works' own ground. */
        boolean inWorks(int x, int y);
    }

    /**
     * One hall of the works: its step in the process, its inclusive cell bounds walls and all, and the doorsteps: the
     * outside cells in front of its doors.
     */
    public record Hall(RoomPurpose step, int x0, int y0, int x1, int y1, List<int[]> doors) {
        public Hall {
            doors = List.copyOf(doors);
        }

        float cx() { return (x0 + x1) / 2f; }

        float cy() { return (y0 + y1) / 2f; }
    }

    /** One thing set against a hall's wall: a doodad, and whether it stands across the wall (a truck backed in). */
    record Item(String doodad, boolean across, String overlay) {
        static Item along(String id) { return new Item(id, false, null); }

        /** A base with its load on it: a pallet and the cartons stacked on it, a roller line and its totes. */
        static Item loaded(String id, String load) { return new Item(id, false, load); }

        static Item across(String id) { return new Item(id, true, null); }
    }

    /**
     * The yard as planned: the assembly hall's bays (null where there are none), the parts line's pieces and legs,
     * the props, and the trodden paths' cells.
     */
    public record Dressing(AssemblyBays.Plan bays, List<Props.Placement> line, List<Props.Placement> legs,
                           List<Props.Placement> props, List<Props.Placement> loads, List<int[]> paths) { }

    private FabricationYard() { }

    /**
     * Dresses the works named {@code worksName} on the built map: plans the yard from its halls and lays the parts
     * line, its legs and the props, each through the ordinary prop placement, which skips one whose ground is no
     * longer open.
     */
    public static void dress(GenContext c, String worksName) {
        LayoutGround ground = new LayoutGround(c);
        int[] works = ground.claimBounds(worksName);
        if (works == null) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        String line = registry.doodad(LINE_ART + "straight") != null ? LINE_ART : LINE_STAND_IN;
        Function<String, int[]> footprint = id -> {
            DoodadDef def = registry.doodad(id);
            return def == null ? null : new int[]{def.footprintCellsX, def.footprintCellsY};
        };
        long seed = ground.seed(NAME);
        AssemblyBays.Model model = AssemblyBays.model(new SplittableRandom(seed ^ 0x5DEECE66DL));
        if (registry.doodad(model.hull()) == null) model = null;
        Ground g = adapt(c, ground, works);
        List<Hall> halls = halls(ground, works);
        Dressing d = plan(halls, g, seed, line, footprint, model);
        // The assembly hall's bays, inside it: the craft being put together from their sections.
        if (d.bays() != null) bays(c, registry, d.bays());
        // The loading yards: dispatch's and receiving's ground paved out to the road they are served from.
        for (Hall hall : halls) {
            if (hall.step() == LightIndustryWorks.DISPATCH || hall.step() == LightIndustryWorks.RECEIVING) {
                int[] yard = loadingYard(hall, g, YARD_REACH);
                if (yard != null) pave(c, g, yard);
            }
        }
        // The shifts' walkways are kept clear of stock but not painted: trodden earth read as dirt roads across the
        // yard (owner pick raid-light-industry-paths, recommended). Paint them with PreparedGround.workedGround.
        // The line runs overhead and takes no ground; its legs and the props stand only where the works' shared rule
        // ({@link WorksProps#open}) lets a prop stand: off streets, doorways, ramps and landing pads.
        for (Props.Placement p : d.line()) {
            if (!WorksProps.open(c, p.x(), p.y(), 1, 1)) continue;
            PreparedGround.prop(c, p.x(), p.y(), registry.doodad(p.doodad()), p.quarterTurns());
        }
        Set<Long> stood = new HashSet<>();
        for (List<Props.Placement> props : List.of(d.legs(), d.props())) {
            for (Props.Placement p : props) {
                DoodadDef def = registry.doodad(p.doodad());
                if (def == null) continue;
                Doodad turned = new Doodad(p.x(), p.y(), def, p.quarterTurns());
                if (!WorksProps.open(c, p.x(), p.y(), turned.footprintCellsX, turned.footprintCellsY)) continue;
                if (PreparedGround.prop(c, p.x(), p.y(), def, p.quarterTurns())) {
                    stood.add(PreparedGround.key(p.x(), p.y()));
                }
            }
        }
        // A load goes on its base only where the base stood.
        for (Props.Placement p : d.loads()) {
            if (stood.contains(PreparedGround.key(p.x(), p.y()))) {
                PreparedGround.prop(c, p.x(), p.y(), registry.doodad(p.doodad()), p.quarterTurns());
            }
        }
    }

    /**
     * Lays the assembly bays in their hall: the band's furnishing cleared to bare floor, then each craft's pieces, a
     * gantry over each joint being made and over the hull being fitted out, and the fit-out in its work strip. A
     * piece closes the cells its hull lies over to movement and sight.
     */
    private static void bays(GenContext c, TileRegistry registry, AssemblyBays.Plan plan) {
        int[] band = plan.band();
        clear(c, band);
        for (AssemblyBays.Piece p : plan.pieces()) {
            DoodadDef def = registry.doodad(p.art());
            if (def == null) continue;
            c.doodads.add(new Doodad(p.x(), p.y(), def, p.turns(), DoodadCover.HEAVY.level(), HULL_HEIGHT));
            for (int y = p.y(); y < p.y() + p.fy(); y++) {
                for (int x = p.x(); x < p.x() + p.fx(); x++) {
                    if (!AssemblyBays.underHull(p, x, y)) continue;
                    c.grid.setWalkable(x, y, false);
                    c.grid.setSeeThrough(x, y, false);
                    c.topology.setFixture(x, y, true);
                }
            }
        }
        for (AssemblyBays.Piece p : plan.pieces()) {
            if (p.gantry() >= 0) gantry(c, registry, p);
            if (p.stage() == AssemblyBays.Stage.FITTING) feed(c, registry, p, band);
        }
    }

    /**
     * Clears a band of a hall's floor to bare floor: every prop whose footprint touches it is lifted, and the cells it
     * held inside the building opened again. Walls and doorways are left as they are.
     */
    private static void clear(GenContext c, int[] band) {
        List<Doodad> lifted = new ArrayList<>();
        for (Doodad d : c.doodads) {
            if (d.cellX + d.footprintCellsX - 1 < band[0] || d.cellX > band[2]
                    || d.cellY + d.footprintCellsY - 1 < band[1] || d.cellY > band[3]) continue;
            lifted.add(d);
        }
        c.doodads.removeAll(lifted);
        for (Doodad d : lifted) {
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++) {
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) open(c, x, y);
            }
        }
        for (int y = band[1]; y <= band[3]; y++) for (int x = band[0]; x <= band[2]; x++) open(c, x, y);
    }

    /** Opens a building floor cell: walkable, seen through, no fixture. Walls and doorways are left alone. */
    private static void open(GenContext c, int x, int y) {
        if (!c.grid.inBounds(x, y) || c.topology.getBuildingId(x, y) == 0 || c.topology.isWall(x, y)
                || c.grid.isDoorway(x, y)) return;
        c.grid.setWalkable(x, y, true);
        c.grid.setSeeThrough(x, y, true);
        c.topology.setFixture(x, y, false);
    }

    /**
     * A gantry crane across a bay: its legs either side of the craft, its span over it at the piece's gantry cell. A
     * craft along x has its span along y.
     */
    private static void gantry(GenContext c, TileRegistry registry, AssemblyBays.Piece p) {
        DoodadDef span = registry.doodad(PortHubDressing.CRANE_SPAN), leg = registry.doodad(PortHubDressing.CRANE_LEG);
        if (span == null || leg == null) return;
        boolean alongX = p.alongX();
        int turns = alongX ? 1 : 0;
        int spanLong = Math.max(span.footprintCellsX, span.footprintCellsY);
        int from = (alongX ? p.y() : p.x()) - 1, to = (alongX ? p.y() + p.fy() : p.x() + p.fx());
        int at = p.gantry();
        int[][] legs = alongX ? new int[][]{{at, from}, {at, to}} : new int[][]{{from, at}, {to, at}};
        for (int[] l : legs) {
            if (!PreparedGround.prop(c, l[0], l[1], leg, turns)) return;
        }
        for (int k = from; k <= to; k += spanLong) {
            int a = Math.min(k, to - spanLong + 1);
            c.doodads.add(alongX ? new Doodad(at, a, span, turns) : new Doodad(a, at, span, turns));
        }
    }

    /** A fitting bay's fit-out down its work strip, the band's cells beside the hull, a cell between pieces. */
    private static void feed(GenContext c, TileRegistry registry, AssemblyBays.Piece p, int[] band) {
        boolean alongX = p.alongX();
        // The strip: the band's side the hull does not take.
        int across = alongX ? (p.y() == band[1] ? p.y() + p.fy() : band[1])
                : (p.x() == band[0] ? p.x() + p.fx() : band[0]);
        int at = (alongX ? p.x() : p.y()) + 1, end = (alongX ? p.x() + p.fx() : p.y() + p.fy()) - 1;
        for (String id : AssemblyBays.feed()) {
            DoodadDef def = registry.doodad(id);
            if (def == null) continue;
            int along = Math.max(def.footprintCellsX, def.footprintCellsY);
            int deep = Math.min(def.footprintCellsX, def.footprintCellsY);
            if (deep > AssemblyBays.STRIP || at + along - 1 > end) break;
            int turns = (def.footprintCellsX >= def.footprintCellsY) == alongX ? 0 : 1;
            if (alongX) PreparedGround.prop(c, at, across, def, turns);
            else PreparedGround.prop(c, across, at, def, turns);
            at += along + 1;
        }
    }

    /**
     * A hall's loading yard: the ground in front of its face nearest a road, the face's width, out to the road and
     * at most {@code reach} cells, as {@code {x0, y0, x1, y1}}; null when no road is within reach of any face.
     */
    static int[] loadingYard(Hall hall, Ground g, int reach) {
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int side = 0; side < 4; side++) {
            int dx = side == 2 ? -1 : side == 3 ? 1 : 0, dy = side == 0 ? -1 : side == 1 ? 1 : 0;
            boolean horizontal = side < 2;
            int lo = horizontal ? hall.x0() : hall.y0(), hi = horizontal ? hall.x1() : hall.y1();
            // How far out the road is, measured down the face's middle.
            int mid = (lo + hi) / 2;
            int ox = horizontal ? mid : dx < 0 ? hall.x0() - 1 : hall.x1() + 1;
            int oy = horizontal ? (dy < 0 ? hall.y0() - 1 : hall.y1() + 1) : mid;
            int d = -1;
            for (int k = 0; k <= reach; k++) {
                int x = ox + dx * k, y = oy + dy * k;
                if (x < 0 || y < 0 || x >= g.width() || y >= g.height() || g.building(x, y)) break;
                if (g.road(x, y)) {
                    d = k;
                    break;
                }
            }
            if (d <= 0 || d >= bestD) continue;
            bestD = d;
            int x0, y0, x1, y1;
            if (horizontal) {
                x0 = lo;
                x1 = hi;
                y0 = dy < 0 ? oy - d + 1 : oy;
                y1 = dy < 0 ? oy : oy + d - 1;
            } else {
                y0 = lo;
                y1 = hi;
                x0 = dx < 0 ? ox - d + 1 : ox;
                x1 = dx < 0 ? ox : ox + d - 1;
            }
            best = new int[]{x0, y0, x1, y1};
        }
        return best;
    }

    /** Paves a yard as concrete: open ground only, off buildings and roads; the coverless scatter on it swept off. */
    private static void pave(GenContext c, Ground g, int[] yard) {
        int concrete = c.topology.surfaceIndex(PortHubDressing.CONCRETE);
        if (concrete < 0) return;
        boolean[] paved = new boolean[c.width * c.height];
        for (int y = yard[1]; y <= yard[3]; y++) {
            for (int x = yard[0]; x <= yard[2]; x++) {
                if (!PreparedGround.open(c, x, y) || g.building(x, y) || g.road(x, y)) continue;
                c.topology.setSurface(x, y, concrete);
                c.topology.setNatureOverlayIndex(x, y, -1);
                c.markMadeGround(x, y);
                paved[y * c.width + x] = true;
            }
        }
        c.doodads.removeIf(d -> d.cellX >= 0 && d.cellY >= 0 && d.cellX < c.width && d.cellY < c.height
                && paved[d.cellY * c.width + d.cellX] && d.cover == 0 && !c.topology.isFixture(d.cellX, d.cellY));
    }

    private static Ground adapt(GenContext c, LayoutGround g, int[] works) {
        CellTopology topology = g.topology();
        // The works' own roads count as road too: the place's roads inside its claim, and any street.
        int[][] roadOwner = c.get(BspKeys.PRECINCT_ROAD);
        return new Ground() {
            @Override public int width() { return g.width(); }

            @Override public int height() { return g.height(); }

            @Override public boolean open(int x, int y) { return g.open(x, y); }

            @Override public boolean road(int x, int y) {
                if (x < 0 || y < 0 || x >= g.width() || y >= g.height()) return false;
                return g.isWay(x, y) || topology.getGroundKind(x, y) == CellTopology.GroundKind.STREET
                        || roadOwner != null && roadOwner[x][y] != GrownTrunkPlan.UNOWNED;
            }

            @Override public boolean building(int x, int y) {
                return x >= 0 && y >= 0 && x < g.width() && y < g.height()
                        && (topology.getBuildingId(x, y) != 0 || !g.walkable(x, y));
            }

            @Override public boolean inWorks(int x, int y) {
                return x >= works[0] && y >= works[1] && x <= works[2] && y <= works[3];
            }
        };
    }

    /** The works' halls on the grown ground: each building inside its claim whose rooms are a step of the work. */
    static List<Hall> halls(LayoutGround g, int[] works) {
        CellTopology topology = g.topology();
        NavigationGrid grid = g.grid();
        List<Hall> out = new ArrayList<>();
        for (Building b : g.buildings()) {
            float cx = (b.minX + b.maxX) / 2f, cy = (b.minY + b.maxY) / 2f;
            if (cx < works[0] || cx > works[2] || cy < works[1] || cy > works[3]) continue;
            Map<RoomPurpose, Integer> votes = new EnumMap<>(RoomPurpose.class);
            for (int i = 0; i < b.cellsX.length; i++) {
                RoomPurpose p = topology.getRoomPurpose(b.cellsX[i], b.cellsY[i]);
                if (p != null) votes.merge(p, 1, Integer::sum);
            }
            RoomPurpose step = null;
            int best = 0;
            for (Map.Entry<RoomPurpose, Integer> e : votes.entrySet()) {
                if (e.getValue() > best && isStep(e.getKey())) {
                    best = e.getValue();
                    step = e.getKey();
                }
            }
            if (step == null) continue;
            // The hall's walls stand a cell outside its floor. Its doors are in them, or, for a wide vehicle door,
            // on the floor's own edge; the doorstep is the first walkable cell beyond the wall.
            int x0 = b.minX - 1, y0 = b.minY - 1, x1 = b.maxX + 1, y1 = b.maxY + 1;
            List<int[]> doors = new ArrayList<>();
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    boolean wall = x == x0 || x == x1 || y == y0 || y == y1;
                    boolean edge = x == b.minX || x == b.maxX || y == b.minY || y == b.maxY;
                    if (!(wall || edge) || !grid.inBounds(x, y) || !grid.isDoorway(x, y)) continue;
                    int dx = x <= b.minX ? -1 : x >= b.maxX ? 1 : 0, dy = y <= b.minY ? -1 : y >= b.maxY ? 1 : 0;
                    if (dx != 0 && dy != 0) continue;
                    int ox = dx < 0 ? x0 - 1 : dx > 0 ? x1 + 1 : x, oy = dy < 0 ? y0 - 1 : dy > 0 ? y1 + 1 : y;
                    if (!grid.inBounds(ox, oy) || !grid.isWalkable(ox, oy) || grid.isDoorway(ox, oy)) continue;
                    boolean seen = false;
                    for (int[] d : doors) seen |= d[0] == ox && d[1] == oy;
                    if (!seen) doors.add(new int[]{ox, oy});
                }
            }
            out.add(new Hall(step, x0, y0, x1, y1, doors));
        }
        return out;
    }

    static boolean isStep(RoomPurpose p) {
        return LightIndustryWorks.PROCESS.contains(p) || p == LightIndustryWorks.CANTEEN;
    }

    /**
     * Plans the yard: the parts line first, then the shifts' paths, then the props round each hall, each kept off
     * what was planned before it.
     *
     * @param line      the parts line's art prefix, its shapes named after {@link OreLine.Shape}
     * @param footprint a doodad's footprint unturned, {@code {x, y}}, or {@code null} when it is not installed
     */
    public static Dressing plan(List<Hall> halls, Ground g, long seed, String line,
                                Function<String, int[]> footprint) {
        return plan(halls, g, seed, line, footprint, null);
    }

    /**
     * Plans the yard, with the assembly hall's bays building {@code model} ({@link AssemblyBays}), or none when it is
     * null.
     */
    public static Dressing plan(List<Hall> halls, Ground g, long seed, String line,
                                Function<String, int[]> footprint, AssemblyBays.Model model) {
        int w = g.width(), h = g.height();
        boolean[] taken = new boolean[w * h];
        Map<RoomPurpose, List<Hall>> byStep = new EnumMap<>(RoomPurpose.class);
        for (Hall hall : halls) byStep.computeIfAbsent(hall.step(), k -> new ArrayList<>()).add(hall);
        List<int[]> doors = new ArrayList<>();
        for (Hall hall : halls) doors.addAll(hall.doors());

        // The bays inside the assembly hall, along its wall toward dispatch.
        AssemblyBays.Plan bays = null;
        Hall assembly = first(byStep, LightIndustryWorks.ASSEMBLY);
        if (model != null && assembly != null) {
            Hall out = first(byStep, LightIndustryWorks.DISPATCH);
            if (out == null) out = first(byStep, LightIndustryWorks.FINISHING);
            if (out == null) out = assembly;
            bays = AssemblyBays.plan(assembly.x0() + 1, assembly.y0() + 1, assembly.x1() - 1, assembly.y1() - 1,
                    out.cx(), out.cy(), model, BAYS);
        }

        // The parts line, step to step in the order the work passes.
        List<Props.Placement> pieces = new ArrayList<>(), legs = new ArrayList<>();
        IntPredicate passable = i -> {
            int x = i % w, y = i / w;
            return x > 0 && y > 0 && x < w - 1 && y < h - 1 && !taken[i] && g.inWorks(x, y) && !g.building(x, y)
                    && !g.road(x, y);
        };
        IntPredicate canStand = i -> {
            int x = i % w, y = i / w;
            return g.open(x, y) && !g.road(x, y) && !nearDoor(doors, x, y, DOOR_CLEAR) && laneAround(g, x, y);
        };
        List<RoomPurpose> steps = new ArrayList<>();
        for (RoomPurpose step : LightIndustryWorks.PROCESS) if (byStep.containsKey(step)) steps.add(step);
        for (int k = 0; k + 1 < steps.size(); k++) {
            for (Hall from : byStep.get(steps.get(k))) {
                for (Hall to : byStep.get(steps.get(k + 1))) {
                    link(from, to, g, taken, passable, canStand, line, pieces, legs);
                }
            }
        }

        // The shifts' paths: from the canteen's door to the doors of the halls they work in.
        List<int[]> paths = new ArrayList<>();
        boolean[] trodden = new boolean[w * h];
        for (Hall canteen : byStep.getOrDefault(LightIndustryWorks.CANTEEN, List.of())) {
            if (canteen.doors().isEmpty()) continue;
            int[] start = canteen.doors().get(0);
            for (Hall hall : halls) {
                if (hall.step() != LightIndustryWorks.SHOP) continue;
                if (hall.doors().isEmpty()) continue;
                int[] end = nearest(hall.doors(), start);
                IntPredicate walk = i -> {
                    int x = i % w, y = i / w;
                    return x > 0 && y > 0 && x < w - 1 && y < h - 1 && g.inWorks(x, y) && !g.building(x, y)
                            && !legAt(legs, x, y);
                };
                List<int[]> course = OreLine.route(w, h, start[0], start[1], walk, i -> i == end[1] * w + end[0]);
                for (int[] c : course) {
                    int i = c[1] * w + c[0];
                    if (trodden[i] || !g.open(c[0], c[1]) || g.road(c[0], c[1])) continue;
                    trodden[i] = true;
                    paths.add(c);
                }
            }
        }
        for (int i = 0; i < trodden.length; i++) if (trodden[i]) taken[i] = true;

        // The props, hall by hall in the order of the work.
        SplittableRandom rng = new SplittableRandom(seed);
        List<Props.Placement> props = new ArrayList<>(), loads = new ArrayList<>();
        List<RoomPurpose> order = new ArrayList<>(LightIndustryWorks.PROCESS);
        order.add(LightIndustryWorks.CANTEEN);
        for (RoomPurpose step : order) {
            for (Hall hall : byStep.getOrDefault(step, List.of())) {
                set(hall, kit(step, rng), g, taken, doors, footprint, props, loads);
            }
        }
        stockYards(halls, g, taken, doors, props, loads);
        return new Dressing(bays, pieces, legs, props, loads, paths);
    }

    /**
     * The stock yards: the open works ground left between the halls, stacked with pallets in rows with aisles between
     * them, as a works that ships a planet's goods keeps its stock. Each block takes the load of the hall nearest it:
     * bought-in cartons by receiving and the stores, finished cases by finishing and dispatch, work in progress (totes)
     * among the shops and assembly. A block keeps {@link #LANE} cells of open ground round it and stays off doors,
     * roads and anything already planned; at most {@link #STOCK_YARDS} blocks, largest first.
     */
    static void stockYards(List<Hall> halls, Ground g, boolean[] taken, List<int[]> doors,
                           List<Props.Placement> props, List<Props.Placement> loads) {
        int w = g.width(), h = g.height();
        int laid = 0;
        for (int[] size : YARD_SIZES) {
            int bw = size[0], bh = size[1];
            for (int y = LANE + 1; y + bh + LANE < h - 1 && laid < STOCK_YARDS; y++) {
                for (int x = LANE + 1; x + bw + LANE < w - 1 && laid < STOCK_YARDS; x++) {
                    if (!clearBlock(g, taken, doors, x, y, bw, bh)) continue;
                    String load = loadFor(halls, x + bw / 2f, y + bh / 2f);
                    // Pallets two by two, in rows two deep with a two-cell aisle between rows and a cell between
                    // stacks along a row.
                    for (int py = y; py + 1 < y + bh; py += 4) {
                        for (int px = x; px + 1 < x + bw; px += 3) {
                            props.add(new Props.Placement("main-stores.pallet", px, py, 0));
                            loads.add(new Props.Placement(load, px, py, 0));
                        }
                    }
                    for (int yy = y - LANE; yy < y + bh + LANE; yy++) {
                        for (int xx = x - LANE; xx < x + bw + LANE; xx++) taken[yy * w + xx] = true;
                    }
                    laid++;
                }
            }
        }
    }

    /** Whether a stock block at {@code (x, y)} has its ground and a lane round it free. */
    private static boolean clearBlock(Ground g, boolean[] taken, List<int[]> doors, int x, int y, int bw, int bh) {
        int w = g.width();
        for (int yy = y - LANE; yy < y + bh + LANE; yy++) {
            for (int xx = x - LANE; xx < x + bw + LANE; xx++) {
                if (!g.inWorks(xx, yy) || g.building(xx, yy) || g.road(xx, yy)) return false;
                boolean inside = xx >= x && xx < x + bw && yy >= y && yy < y + bh;
                if (inside && (taken[yy * w + xx] || !g.open(xx, yy) || nearDoor(doors, xx, yy, DOOR_CLEAR))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The load a stock block holds: what the hall nearest it takes in or sends out. */
    static String loadFor(List<Hall> halls, float x, float y) {
        Hall nearest = null;
        float best = Float.MAX_VALUE;
        for (Hall hall : halls) {
            float dx = Math.max(0, Math.max(hall.x0() - x, x - hall.x1()));
            float dy = Math.max(0, Math.max(hall.y0() - y, y - hall.y1()));
            if (dx + dy < best) {
                best = dx + dy;
                nearest = hall;
            }
        }
        RoomPurpose step = nearest == null ? null : nearest.step();
        if (step == LightIndustryWorks.RECEIVING || step == LightIndustryWorks.STORES) return "main-stores.cartons";
        if (step == LightIndustryWorks.FINISHING || step == LightIndustryWorks.DISPATCH) return "main-stores.cases";
        return "loading-bay.insulated-tote";
    }

    /** What stands against a hall's wall, by its step, in the order it is set down. */
    static List<Item> kit(RoomPurpose step, SplittableRandom rng) {
        List<Item> out = new ArrayList<>();
        if (step == LightIndustryWorks.RECEIVING) {
            // The feeders' trucks backed in, and what they brought: cartons of bought-in parts, bar stock, cable.
            String[] trucks = {"doodad.parked-cargo-truck", "doodad.parked-flatbed-truck", "doodad.parked-box-van"};
            for (int i = 0; i < MAX_TRUCKS; i++) out.add(Item.across(trucks[rng.nextInt(trucks.length)]));
            out.add(Item.loaded("main-stores.pallet", "main-stores.cartons"));
            out.add(Item.along("machine-shop.bar-stock"));
            out.add(Item.loaded("main-stores.pallet", "main-stores.cartons"));
            out.add(Item.along("doodad.industrial-cable-reel"));
            out.add(Item.along("doodad.industrial-drum-cluster"));
        } else if (step == LightIndustryWorks.STORES) {
            // Open racks of parts along the stores' face, bins, and the tug that runs them out to the shops.
            out.add(Item.loaded("main-stores.rack", "main-stores.cases"));
            out.add(Item.along("doodad.stores-bin-rack"));
            out.add(Item.along("loading-bay.pallet-tug"));
            out.add(Item.along("doodad.stores-bin-rack"));
        } else if (step == LightIndustryWorks.SHOP) {
            // The line spills out of the door: a lathe and a mill working under the eaves, the part bins beside them,
            // the bar stock they cut and the offcuts.
            out.add(Item.along(rng.nextBoolean() ? "machine-shop.lathe" : "machine-shop.mill"));
            out.add(Item.along("machine-shop.bearings"));
            out.add(Item.along("doodad.stores-bin-rack"));
            out.add(Item.along(rng.nextBoolean() ? "machine-shop.mill" : "machine-shop.lathe"));
            out.add(Item.along(rng.nextBoolean() ? "machine-shop.electrical" : "machine-shop.bar-stock"));
            out.add(Item.along("doodad.industrial-scrap-pile"));
        } else if (step == LightIndustryWorks.ASSEMBLY) {
            // The line's end: a roller run of totes coming off it, the assembly and inspection benches.
            out.add(Item.loaded("loading-bay.conveyor", "loading-bay.insulated-tote"));
            out.add(Item.along("machine-shop.assembly-bench"));
            out.add(Item.along("machine-shop.inspection-bench"));
            out.add(Item.along("doodad.stores-cargo-loader"));
            out.add(Item.loaded("main-stores.pallet", "main-stores.cases"));
        } else if (step == LightIndustryWorks.FINISHING) {
            // Paint and cure: drums, the coolant plant, the tank, and packed cases waiting for dispatch.
            out.add(Item.along("doodad.industrial-drum-cluster"));
            out.add(Item.along("factory-machines.coolant"));
            out.add(Item.along("doodad.industrial-fluid-tank"));
            out.add(Item.loaded("main-stores.pallet", "main-stores.cases"));
            out.add(Item.along("doodad.industrial-drum-cluster"));
        } else if (step == LightIndustryWorks.DISPATCH) {
            // Finished goods: a van backed in, cased pallets and crates staged for it, the loader.
            out.add(Item.across("doodad.parked-box-van"));
            out.add(Item.loaded("main-stores.pallet", "main-stores.cases"));
            out.add(Item.along("doodad.industrial-crate-stack"));
            out.add(Item.loaded("main-stores.pallet", "main-stores.cases"));
            out.add(Item.along("doodad.stores-cargo-loader"));
            out.add(Item.along("doodad.industrial-crate-stack"));
        } else if (step == LightIndustryWorks.CANTEEN) {
            out.add(Item.along("doodad.mess-table-long"));
            out.add(Item.along("doodad.mess-table-long"));
            out.add(Item.along("doodad.industrial-dumpster"));
        }
        return out;
    }

    /**
     * Sets a hall's kit against its walls: the faces with a door first (where the work goes in and out), each walked
     * from one end, an item standing at the first spot it fits with a cell's gap to the last; then its stock again,
     * up to {@link #FILL} times over, while there is room.
     */
    private static void set(Hall hall, List<Item> kit, Ground g, boolean[] taken, List<int[]> doors,
                            Function<String, int[]> footprint, List<Props.Placement> out,
                            List<Props.Placement> loads) {
        int w = g.width();
        List<int[]> faces = new ArrayList<>(); // {side, hasDoor}; side 0 south, 1 north, 2 west, 3 east
        for (int side = 0; side < 4; side++) {
            boolean door = false;
            for (int[] d : hall.doors()) door |= onFace(hall, side, d);
            faces.add(new int[]{side, door ? 1 : 0});
        }
        faces.sort((a, b) -> b[1] - a[1]);
        Map<Integer, Integer> cursor = new HashMap<>();
        List<Item> fill = new ArrayList<>();
        for (Item item : kit) {
            // A truck with no room to back in stands alongside instead.
            boolean placed = place(hall, item.doodad(), item.across(), faces, cursor, g, taken, doors, footprint, out)
                    || item.across() && place(hall, item.doodad(), false, faces, cursor, g, taken, doors, footprint, out);
            if (placed) load(item, out, loads);
            if (placed && !item.across()) fill.add(item);
        }
        // Then the hall's own stock again along what is left of its faces: a works this size keeps more than one of
        // each by its doors.
        for (int i = 0; i < FILL * fill.size(); i++) {
            Item item = fill.get(i % fill.size());
            if (!place(hall, item.doodad(), false, faces, cursor, g, taken, doors, footprint, out)) break;
            load(item, out, loads);
        }
    }

    /** The item's load, if it carries one, on the base just set down: same corner, same turn. */
    private static void load(Item item, List<Props.Placement> out, List<Props.Placement> loads) {
        if (item.overlay() == null) return;
        Props.Placement base = out.get(out.size() - 1);
        loads.add(new Props.Placement(item.overlay(), base.x(), base.y(), base.quarterTurns()));
    }

    /** Sets one item at the first spot it fits on the hall's faces in order, from each face's cursor on. */
    private static boolean place(Hall hall, String doodad, boolean across, List<int[]> faces,
                                 Map<Integer, Integer> cursor, Ground g, boolean[] taken, List<int[]> doors,
                                 Function<String, int[]> footprint, List<Props.Placement> out) {
        int[] fp = footprint.apply(doodad);
        if (fp == null) return false;
        int w = g.width();
        for (int[] face : faces) {
            int side = face[0];
            boolean horizontal = side < 2;
            // Along the wall the long side lies along it; across, it stands out from it.
            boolean longAlongX = fp[0] >= fp[1];
            int turns = horizontal == (across != longAlongX) ? 0 : 1;
            int fx = turns == 0 ? fp[0] : fp[1], fy = turns == 0 ? fp[1] : fp[0];
            int from = horizontal ? hall.x0() : hall.y0(), to = horizontal ? hall.x1() : hall.y1();
            int extent = horizontal ? fx : fy;
            for (int s = cursor.getOrDefault(side, from); s + extent - 1 <= to; s++) {
                int ax, ay;
                switch (side) {
                    case 0 -> { ax = s; ay = hall.y0() - fy; }
                    case 1 -> { ax = s; ay = hall.y1() + 1; }
                    case 2 -> { ax = hall.x0() - fx; ay = s; }
                    default -> { ax = hall.x1() + 1; ay = s; }
                }
                if (!fits(g, taken, doors, ax, ay, fx, fy, side)) continue;
                for (int y = ay; y < ay + fy; y++) for (int x = ax; x < ax + fx; x++) taken[y * w + x] = true;
                out.add(new Props.Placement(doodad, ax, ay, turns));
                cursor.put(side, s + extent + 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a footprint at {@code (ax, ay)} of {@code fx} by {@code fy} cells fits against the wall on
     * {@code side}: open works ground off roads, other plans and doors, with {@link #LANE} cells of ground no building
     * stands on beyond it.
     */
    static boolean fits(Ground g, boolean[] taken, List<int[]> doors, int ax, int ay, int fx, int fy, int side) {
        int w = g.width(), h = g.height();
        for (int y = ay; y < ay + fy; y++) {
            for (int x = ax; x < ax + fx; x++) {
                if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) return false;
                if (taken[y * w + x] || !g.open(x, y) || g.road(x, y) || !g.inWorks(x, y)) return false;
                if (nearDoor(doors, x, y, DOOR_CLEAR)) return false;
            }
        }
        int dx = side == 2 ? -1 : side == 3 ? 1 : 0, dy = side == 0 ? -1 : side == 1 ? 1 : 0;
        // The lane beyond: every cell from the footprint's outer edge out LANE cells, along its whole width.
        for (int y = ay; y < ay + fy; y++) {
            for (int x = ax; x < ax + fx; x++) {
                for (int k = 1; k <= LANE; k++) {
                    int ox = x + dx * k, oy = y + dy * k;
                    if (ox >= ax && ox < ax + fx && oy >= ay && oy < ay + fy) continue;
                    if (ox < 0 || oy < 0 || ox >= w || oy >= h || g.building(ox, oy)) return false;
                }
            }
        }
        return true;
    }

    /** Lays one link of the parts line from {@code from}'s wall to {@code to}'s; false when no short course reaches. */
    private static boolean link(Hall from, Hall to, Ground g, boolean[] taken, IntPredicate passable,
                                IntPredicate canStand, String art, List<Props.Placement> pieces,
                                List<Props.Placement> legs) {
        int w = g.width(), h = g.height();
        int[] start = beside(from, to, passable, w, h);
        if (start == null) return false;
        IntPredicate inTo = i -> i % w >= to.x0() && i % w <= to.x1() && i / w >= to.y0() && i / w <= to.y1();
        IntPredicate goal = i -> passable.test(i) && ((i % w > 0 && inTo.test(i - 1))
                || (i % w < w - 1 && inTo.test(i + 1)) || (i >= w && inTo.test(i - w))
                || (i + w < w * h && inTo.test(i + w)));
        List<int[]> path = OreLine.route(w, h, start[0], start[1], passable, goal);
        if (path.isEmpty()) return false;
        int gx = Math.max(to.x0() - start[0], Math.max(0, start[0] - to.x1()));
        int gy = Math.max(to.y0() - start[1], Math.max(0, start[1] - to.y1()));
        if (path.size() > 2 * (gx + gy) + LINK_SLACK) return false;
        int[] last = path.get(path.size() - 1);
        int intoX = last[0], intoY = last[1];
        for (int d = 0; d < 4; d++) {
            int nx = last[0] + (d == 0 ? 1 : d == 2 ? -1 : 0), ny = last[1] + (d == 1 ? 1 : d == 3 ? -1 : 0);
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && inTo.test(ny * w + nx)) {
                intoX = nx;
                intoY = ny;
                break;
            }
        }
        List<OreLine.Piece> course = OreLine.pieces(path, start[2], start[3], intoX, intoY, true);
        for (OreLine.Piece p : course) {
            pieces.add(new Props.Placement(art + p.shape().name().toLowerCase(Locale.ROOT), p.x(), p.y(),
                    p.quarterTurns()));
            taken[p.y() * w + p.x()] = true;
        }
        for (int[] at : OreLine.legs(course, canStand, w)) {
            int turns = 0;
            for (OreLine.Piece p : course) if (p.x() == at[0] && p.y() == at[1]) turns = p.quarterTurns() % 2;
            legs.add(new Props.Placement(art + "leg", at[0], at[1], turns));
        }
        return true;
    }

    /**
     * Where a link from {@code from} to {@code to} starts: the open cell against {@code from}'s wall (not at a
     * corner) nearest {@code to}, with the wall cell behind it, as {@code {x, y, wallX, wallY}}.
     */
    static int[] beside(Hall from, Hall to, IntPredicate passable, int w, int h) {
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = from.y0() - 1; y <= from.y1() + 1; y++) {
            for (int x = from.x0() - 1; x <= from.x1() + 1; x++) {
                boolean side = x == from.x0() - 1 || x == from.x1() + 1;
                boolean end = y == from.y0() - 1 || y == from.y1() + 1;
                if (side == end || x < 0 || y < 0 || x >= w || y >= h || !passable.test(y * w + x)) continue;
                float d = Math.abs(x - to.cx()) + Math.abs(y - to.cy());
                if (d >= bestD) continue;
                bestD = d;
                int fx = x < from.x0() ? x + 1 : x > from.x1() ? x - 1 : x;
                int fy = y < from.y0() ? y + 1 : y > from.y1() ? y - 1 : y;
                best = new int[]{x, y, fx, fy};
            }
        }
        return best;
    }

    private static Hall first(Map<RoomPurpose, List<Hall>> byStep, RoomPurpose step) {
        List<Hall> of = byStep.get(step);
        return of == null || of.isEmpty() ? null : of.get(0);
    }

    private static boolean onFace(Hall hall, int side, int[] d) {
        return switch (side) {
            case 0 -> d[1] == hall.y0() - 1;
            case 1 -> d[1] == hall.y1() + 1;
            case 2 -> d[0] == hall.x0() - 1;
            default -> d[0] == hall.x1() + 1;
        };
    }

    static boolean nearDoor(List<int[]> doors, int x, int y, int reach) {
        for (int[] d : doors) if (Math.abs(d[0] - x) <= reach && Math.abs(d[1] - y) <= reach) return true;
        return false;
    }

    /** Whether a leg at the cell leaves its four neighbours walkable ground, so it never closes a gap. */
    private static boolean laneAround(Ground g, int x, int y) {
        int open = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!g.building(x + d[0], y + d[1])) open++;
        }
        return open == 4;
    }

    private static boolean legAt(List<Props.Placement> legs, int x, int y) {
        for (Props.Placement p : legs) if (p.x() == x && p.y() == y) return true;
        return false;
    }

    private static int[] nearest(List<int[]> cells, int[] to) {
        int[] best = cells.get(0);
        int bestD = Integer.MAX_VALUE;
        for (int[] c : cells) {
            int d = Math.abs(c[0] - to[0]) + Math.abs(c[1] - to[1]);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
