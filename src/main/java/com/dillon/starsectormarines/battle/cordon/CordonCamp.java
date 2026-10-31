package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The Road's safe point ({@code cordon-nouns.md}, Camp): a small allied encampment on the corridor behind the line,
 * where the convoys set out from and come back to. A homecoming convoy stops on the road through it, its townspeople
 * climb down and walk in to wait there for the rest of the battle (they are out the moment they step off), and its
 * trucks pull back down the road to wait for the next trip.
 *
 * <p>The camp is a square {@code 2 * HALF + 1} cells across round a corridor cell, cleared at setup of its trees and
 * loose props, the road through it kept clear: a sandbag ring on its edge, whole round with its corner pieces and broken
 * only where the road crosses it; the line's heavy machine gun on each inner corner (the redoubts' corner gun); an
 * aid station, a field HQ and a supply dump on its rear half either side of the road; and two allied teams, one along
 * its side toward the front and one along its rear. It stands beside the logistics base, on its west side first,
 * with an {@link #APRON} of open ground between them where vehicles are set down, built and drive out, and its own
 * road out of its middle across the apron to the corridor ({@link #beside}); where the base has no room beside it or
 * no way to the corridor, on the corridor {@link #BEHIND_GAP} cells behind the line ({@link #plan}, pure). Either way
 * its square is mostly ground it can clear and stands clear of the landing field, the base's berths and muster and
 * the air base.
 *
 * <p>It is meant to hold against the swarm's ordinary pressure, which the line stops well short of it, and to fall
 * to a tide that breaks through: four guns and twelve rifles behind bags, no wall (UNTUNED: {@link #GUARDS} and the
 * gun are the levers). The people waiting in it are ordinary civilians the swarm can reach and kill.
 */
public final class CordonCamp {

    /** Cells from the camp's middle to its edge. */
    public static final int HALF = 12;
    /** Cells either side of the road through the camp kept clear for the trucks. */
    static final int ROAD_KEEP = 3;
    /** Cells the camp's square keeps from each point kept off (a landing pad, a base berth, an air base stand). */
    static final int KEEP_OFF = 12;
    /** Corridor cells either way of where the camp is wanted that it is looked for. */
    static final int SEARCH = 60;
    /**
     * Corridor cells behind the line's crossing the camp's middle is wanted (owner, 2026-10-31: off the rear edge,
     * nearer the line, to shorten the drive's safe leg): a buffer the swarm crosses only once it is through the line,
     * well behind the company's fall-back ground. UNTUNED.
     */
    static final int BEHIND_GAP = 70;
    /** Corridor cells short of the gap the camp keeps at the nearest: it stands behind the line. */
    static final int SHORT_OF_GAP = 50;
    /** The least share of the square that must be open ground. */
    static final float MIN_OPEN = 0.5f;
    /** Allied riflemen in each of the camp's two teams. UNTUNED. */
    static final int GUARDS = 6;
    /** The gun on each of the camp's inner corners: the redoubts' corner gun. UNTUNED. */
    static final String CORNER_GUN = ColonyMilitia.EMPLACEMENT_STRUCTURE;
    /** Cells the camp's square keeps from the map's edge. */
    static final int EDGE = 4;
    /**
     * Cells of ground between the camp's square and the logistics base's buildings: the apron trucks are set down on,
     * vehicles are built out onto and drive away across (owner, 2026-10-31: to the left of the logistics base, with a
     * gap for vehicles to be created at). UNTUNED.
     */
    static final int APRON = 10;
    /** Cells from the base's seed its buildings are looked for, to take its footprint. */
    static final int BASE_REACH = 26;
    /** Cells the camp's middle may slide along the base's side from level with the base's middle. */
    static final int BESIDE_SLIDE = 24;
    /** Squares beside the base whose way to the corridor is searched before giving up on the base. */
    static final int BESIDE_TRIES = 6;
    /** Corridor cells short of the gap the camp's road may join the corridor, at the nearest. */
    static final int JOIN_SHORT = 10;

    /** A prop of a station: its art and its cell offset and footprint from the station's anchor. */
    record Piece(String doodad, int dx, int dy, int width, int height) { }

    /** A prop as set down. */
    public record Prop(String doodad, int x, int y, int width, int height) { }

    /** The aid station: cots and treatment tables where the people brought out are seen to. */
    static final List<Piece> AID = List.of(
            new Piece("doodad.medical-ward-bed-head-n", 0, 0, 1, 2),
            new Piece("doodad.medical-ward-bed-head-n", 2, 0, 1, 2),
            new Piece("doodad.medical-ward-bed-head-n", 4, 0, 1, 2),
            new Piece("doodad.medical-treatment-table", 0, 3, 2, 1),
            new Piece("doodad.medical-supply-trolley", 3, 3, 1, 1));
    /** The field HQ: a command console, a generator and a fluid tank. */
    static final List<Piece> HQ = List.of(
            new Piece("doodad.military-command-console", 0, 0, 1, 1),
            new Piece("doodad.industrial-generator", 2, 0, 1, 1),
            new Piece("doodad.industrial-fluid-tank", 3, 0, 1, 1));
    /** The supply dump: crates, pallets and drums. */
    static final List<Piece> DUMP = List.of(
            new Piece("doodad.industrial-crate-stack", 0, 0, 1, 1),
            new Piece("doodad.industrial-crate-stack", 1, 0, 1, 1),
            new Piece("doodad.industrial-pallet-stack", 2, 0, 1, 1),
            new Piece("doodad.industrial-drum-cluster", 0, 1, 1, 1),
            new Piece("doodad.industrial-drum-cluster", 1, 1, 1, 1),
            new Piece("doodad.industrial-crate-stack", 2, 1, 1, 1));

    /** A test on a cell: open ground the camp may stand on, road under a truck, a building. */
    public interface Open {
        boolean at(int x, int y);
    }

    /**
     * A sandbag on the ring: its cell and its art after {@code doodad.fort-sandbag-}: the way it faces out on a side
     * ({@code n}, {@code s}, ...) or {@code corner-ne} and so on at a corner.
     */
    public record Bag(int x, int y, String art) { }

    /**
     * Where the camp stands: the corridor cell the trucks stop at (its middle, or its apron beside the base), its open cells, the ring's bags, where
     * the people wait (nearest the middle first), the crate stacks' cells and the guards' cells.
     */
    public record Plan(int dropIndex, int cx, int cy, List<int[]> cells, List<Bag> bags, List<int[]> waiting,
                List<int[]> guns, List<int[]> guards, List<int[]> rearGuards, List<Prop> props) { }

    /** A camp beside the base: its plan, and the convoys' corridor from its middle (its road, then the corridor's). */
    public record Beside(Plan plan, List<int[]> corridor) { }

    private final Plan plan;
    private int nextHome;

    private CordonCamp(Plan plan) {
        this.plan = plan;
    }

    /** The corridor cell the trucks stop at in the camp. */
    public int dropIndex() { return plan.dropIndex(); }
    /** The camp's middle, on the road. */
    public int centreX() { return plan.cx(); }
    public int centreY() { return plan.cy(); }
    /** The camp's open cells. */
    public List<int[]> cells() { return plan.cells(); }
    /** Where the people brought out wait, nearest the middle first. */
    public List<int[]> waiting() { return plan.waiting(); }

    /** Whether a cell lies inside the camp's square. */
    public boolean contains(int x, int y) {
        return Math.abs(x - plan.cx()) <= HALF && Math.abs(y - plan.cy()) <= HALF;
    }

    /** The next place a townsperson stepping off waits: the waiting cells in turn, round again once all are given. */
    int[] nextHome() {
        if (plan.waiting().isEmpty()) return new int[]{plan.cx(), plan.cy()};
        return plan.waiting().get(nextHome++ % plan.waiting().size());
    }

    /**
     * Finds the camp {@link #BEHIND_GAP} corridor cells behind the line's crossing, or beside the base where the
     * convoys would set out ({@code entry}) when that is nearer the gap: on the corridor cell nearest there (the rear
     * way first on a tie), never nearer the gap than {@link #SHORT_OF_GAP}, whose square lies on the map, keeps
     * {@link #KEEP_OFF} from every point in {@code keepOff} and is at least {@link #MIN_OPEN} ground it can clear.
     * {@code null} where none is.
     *
     * @param front +1 or -1, the front's way in y
     */
    public static Plan plan(List<int[]> corridor, int entry, int gapIndex, int front, int width, int height, Open open,
                     List<int[]> keepOff) {
        int wanted = wanted(entry, gapIndex);
        int hi = Math.min(corridor.size() - 1, Math.min(wanted + SEARCH, gapIndex - SHORT_OF_GAP));
        int lo = Math.max(1, wanted - SEARCH);
        List<Integer> order = new ArrayList<>();
        for (int i = lo; i <= hi; i++) order.add(i);
        order.sort(Comparator.<Integer>comparingInt(i -> Math.abs(i - wanted)).thenComparingInt(i -> i));
        for (int index : order) {
            int[] c = corridor.get(index);
            if (c[0] - HALF < EDGE || c[1] - HALF < EDGE || c[0] + HALF >= width - EDGE
                    || c[1] + HALF >= height - EDGE) continue;
            if (crowded(c[0], c[1], keepOff)) continue;
            List<int[]> cells = new ArrayList<>();
            for (int y = c[1] - HALF; y <= c[1] + HALF; y++)
                for (int x = c[0] - HALF; x <= c[0] + HALF; x++) if (open.at(x, y)) cells.add(new int[]{x, y});
            int side = 2 * HALF + 1;
            if (cells.size() < MIN_OPEN * side * side) continue;
            return lay(index, c[0], c[1], front, corridor, cells);
        }
        return null;
    }

    /**
     * The logistics base's footprint {x0, y0, x1, y1}: the bounds of the buildings within {@link #BASE_REACH} of its
     * seed, or {@code null} where there are none.
     */
    public static int[] footprint(Open building, int seedX, int seedY) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int y = seedY - BASE_REACH; y <= seedY + BASE_REACH; y++) {
            for (int x = seedX - BASE_REACH; x <= seedX + BASE_REACH; x++) {
                if (!building.at(x, y)) continue;
                b[0] = Math.min(b[0], x);
                b[1] = Math.min(b[1], y);
                b[2] = Math.max(b[2], x);
                b[3] = Math.max(b[3], y);
            }
        }
        return b[2] < b[0] ? null : b;
    }

    /**
     * Finds the camp beside the logistics base {@code base} (its {@link #footprint}): on its west side first, then
     * its east, an {@link #APRON} clear of its buildings, level with its middle or the nearest to that within
     * {@link #BESIDE_SLIDE} (the rear way first on a tie), on the map, {@link #KEEP_OFF} from every point in
     * {@code keepOff}, at least {@link #MIN_OPEN} ground it can clear, with open ground from its middle to its side
     * facing the base. Its road runs from its middle straight out to the apron's middle, where the trucks stop, then
     * the cheapest way a truck can drive ({@link CordonCorridor#search}) to the corridor at least {@link #JOIN_SHORT}
     * short of the gap, and on along the corridor to the town. {@code null} where no square has room and a way.
     *
     * @param road  road under a truck, which the way keeps to where it can
     * @param front +1 or -1, the front's way in y
     */
    public static Beside beside(NavigationGrid grid, Open road, List<int[]> corridor, int gapIndex, int front,
                                int[] base, Open open, List<int[]> keepOff) {
        if (base == null || corridor.isEmpty()) return null;
        Map<Long, Integer> joins = new HashMap<>();
        for (int i = 1; i <= Math.min(corridor.size() - 1, gapIndex - JOIN_SHORT); i++) {
            joins.putIfAbsent(key(corridor.get(i)[0], corridor.get(i)[1]), i);
        }
        if (joins.isEmpty()) return null;
        int width = grid.getWidth(), height = grid.getHeight(), midY = (base[1] + base[3]) / 2, tries = 0;
        List<Integer> slide = new ArrayList<>();
        for (int d = -BESIDE_SLIDE; d <= BESIDE_SLIDE; d++) slide.add(d);
        slide.sort(Comparator.<Integer>comparingInt(Math::abs).thenComparingInt(d -> d * front));
        for (int side : new int[]{-1, 1}) {
            int cx = side < 0 ? base[0] - APRON - 1 - HALF : base[2] + APRON + 1 + HALF, out = -side;
            for (int d : slide) {
                int cy = midY + d;
                if (cx - HALF < EDGE || cy - HALF < EDGE || cx + HALF >= width - EDGE || cy + HALF >= height - EDGE) {
                    continue;
                }
                if (crowded(cx, cy, keepOff)) continue;
                List<int[]> cells = new ArrayList<>();
                for (int y = cy - HALF; y <= cy + HALF; y++)
                    for (int x = cx - HALF; x <= cx + HALF; x++) if (open.at(x, y)) cells.add(new int[]{x, y});
                int across = 2 * HALF + 1;
                if (cells.size() < MIN_OPEN * across * across) continue;
                // Straight out of the middle to the apron's middle, where the trucks stop, clear of the ring either side.
                int laneEnd = HALF + APRON / 2;
                boolean lane = true;
                for (int k = 0; k <= HALF && lane; k++) lane = open.at(cx + out * k, cy);
                for (int k = HALF + 1; k <= laneEnd && lane; k++) lane = CordonCorridor.drivable(grid, cx + out * k, cy);
                if (!lane) continue;
                if (tries++ >= BESIDE_TRIES) return null;
                List<int[]> way = CordonCorridor.search(grid, road::at,
                        List.of(new int[]{cx + out * (laneEnd + 1), cy}), (x, y) -> joins.containsKey(key(x, y)));
                if (way == null) continue;
                int[] join = way.get(way.size() - 1);
                List<int[]> route = new ArrayList<>();
                for (int k = 0; k <= laneEnd; k++) route.add(new int[]{cx + out * k, cy});
                route.addAll(way);
                route.addAll(corridor.subList(joins.get(key(join[0], join[1])) + 1, corridor.size()));
                return new Beside(lay(laneEnd, cx, cy, front, route, cells), List.copyOf(route));
            }
        }
        return null;
    }

    /** Road under a truck on {@code topology}, for {@link #beside}. */
    public static Open road(CellTopology topology) {
        return (x, y) -> CordonCorridor.road(topology, x, y);
    }

    /** The corridor cell the camp is wanted on: {@link #BEHIND_GAP} behind the gap, or the entry where it is nearer. */
    static int wanted(int entry, int gapIndex) {
        return Math.max(1, Math.max(entry, gapIndex - BEHIND_GAP));
    }

    private static boolean crowded(int cx, int cy, List<int[]> keepOff) {
        for (int[] k : keepOff) {
            if (Math.abs(k[0] - cx) <= HALF + KEEP_OFF && Math.abs(k[1] - cy) <= HALF + KEEP_OFF) return true;
        }
        return false;
    }

    /** The camp's pieces on a square found open enough. */
    private static Plan lay(int index, int cx, int cy, int front, List<int[]> corridor, List<int[]> cells) {
        Set<Long> road = new HashSet<>(), near = new HashSet<>();
        for (int[] r : corridor) {
            if (Math.abs(r[0] - cx) > HALF + ROAD_KEEP + 1 || Math.abs(r[1] - cy) > HALF + ROAD_KEEP + 1) continue;
            for (int dy = -ROAD_KEEP - 1; dy <= ROAD_KEEP + 1; dy++) {
                for (int dx = -ROAD_KEEP - 1; dx <= ROAD_KEEP + 1; dx++) {
                    long k = key(r[0] + dx, r[1] + dy);
                    near.add(k);
                    if (Math.abs(dx) <= ROAD_KEEP && Math.abs(dy) <= ROAD_KEEP) road.add(k);
                }
            }
        }
        List<Bag> bags = new ArrayList<>();
        List<int[]> inner = new ArrayList<>(), guns = new ArrayList<>();
        for (int[] c : cells) {
            int dx = c[0] - cx, dy = c[1] - cy;
            int ring = Math.max(Math.abs(dx), Math.abs(dy));
            long k = key(c[0], c[1]);
            if (ring == HALF) {
                if (!road.contains(k)) bags.add(new Bag(c[0], c[1], bagArt(dx, dy)));
            } else if (ring == HALF - 1 && Math.abs(dx) == HALF - 1 && Math.abs(dy) == HALF - 1) {
                if (!near.contains(k)) guns.add(c);
            } else if (ring <= HALF - 2 && !near.contains(k)) {
                inner.add(c);
            }
        }
        // One team just inside the bags on the side toward the front, one on the rear, each spread along its side.
        List<int[]> guards = row(cells, cx, cy + front * (HALF - 1), road);
        List<int[]> rearGuards = row(cells, cx, cy - front * (HALF - 1), road);
        // The stations stand on the rear half, either side of the road; the people wait round them.
        List<Prop> props = new ArrayList<>();
        Set<Long> free = new HashSet<>();
        for (int[] c : inner) free.add(key(c[0], c[1]));
        int rear = cy - front * (HALF - 3);
        station(AID, cx - HALF / 2 - 2, rear, free, props);
        station(DUMP, cx + HALF / 2, rear, free, props);
        station(HQ, cx - HALF / 2 - 2, cy, free, props);
        inner.removeIf(c -> !free.contains(key(c[0], c[1])));
        // The people wait toward the rear of the camp first, away from the front.
        int restY = cy - front * (HALF / 2);
        inner.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[0] - cx) + Math.abs(c[1] - restY))
                .thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        return new Plan(index, cx, cy, List.copyOf(cells), bags, inner, guns, guards, rearGuards, props);
    }

    /** {@link #GUARDS} cells spread along the row {@code y} just inside the bags, off the road and the corner guns. */
    private static List<int[]> row(List<int[]> cells, int cx, int y, Set<Long> road) {
        List<int[]> along = new ArrayList<>();
        for (int[] c : cells) {
            if (c[1] == y && Math.abs(c[0] - cx) < HALF - 1 && !road.contains(key(c[0], c[1]))) along.add(c);
        }
        along.sort(Comparator.comparingInt(c -> c[0]));
        List<int[]> out = new ArrayList<>();
        for (int g = 0; g < GUARDS && !along.isEmpty(); g++) out.add(along.get((int) ((g + .5f) * along.size() / GUARDS)));
        return out;
    }

    /** A ring cell's bag art: its corner piece on a corner, else the way it faces out. */
    static String bagArt(int dx, int dy) {
        if (Math.abs(dx) == Math.abs(dy) && dx != 0) return "corner-" + (dy > 0 ? 'n' : 's') + (dx > 0 ? 'e' : 'w');
        return String.valueOf(facing(dx, dy));
    }

    /**
     * Sets a station down whole at the free anchor nearest {@code (x, y)}, its pieces and a cell round them taken
     * from {@code free}; left out where it fits nowhere.
     */
    private static void station(List<Piece> pieces, int x, int y, Set<Long> free, List<Prop> props) {
        List<int[]> anchors = new ArrayList<>();
        for (long k : free) anchors.add(new int[]{(int) (k >> 32), (int) k});
        anchors.sort(Comparator.<int[]>comparingInt(a -> Math.abs(a[0] - x) + Math.abs(a[1] - y))
                .thenComparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
        for (int[] a : anchors) {
            boolean fits = true;
            for (Piece p : pieces) {
                for (int py = a[1] + p.dy(); py < a[1] + p.dy() + p.height() && fits; py++)
                    for (int px = a[0] + p.dx(); px < a[0] + p.dx() + p.width() && fits; px++)
                        fits = free.contains(key(px, py));
            }
            if (!fits) continue;
            for (Piece p : pieces) {
                props.add(new Prop(p.doodad(), a[0] + p.dx(), a[1] + p.dy(), p.width(), p.height()));
                for (int py = a[1] + p.dy() - 1; py <= a[1] + p.dy() + p.height(); py++)
                    for (int px = a[0] + p.dx() - 1; px <= a[0] + p.dx() + p.width(); px++) free.remove(key(px, py));
            }
            return;
        }
    }

    /** The bag art's facing for a ring cell, out from the middle: the art's letters as the redoubts' works use them. */
    static char facing(int dx, int dy) {
        if (Math.abs(dy) >= Math.abs(dx)) return dy > 0 ? 'n' : 's';
        return dx > 0 ? 'e' : 'w';
    }

    /**
     * Raises the camp on the live map before the first tick: its square cleared ({@link #clear}), the bags (walkable,
     * open to sight), the stations' props (closed to movement, open to sight), a gun on each inner corner and the two
     * teams, allied under garrison authority. {@code null} where {@link #plan} finds no ground.
     */
    public static CordonCamp raise(BattleSimulation sim, Plan plan, RiskLevel risk, Random rng) {
        if (plan == null) return null;
        TileRegistry registry = TileRegistry.installed();
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        clear(sim, plan.cells());
        if (registry != null) {
            for (Bag bag : plan.bags()) {
                DoodadDef def = registry.doodad("doodad.fort-sandbag-" + bag.art());
                if (def == null || !grid.isWalkable(bag.x(), bag.y()) || topology.isFixture(bag.x(), bag.y())) continue;
                grid.setSeeThrough(bag.x(), bag.y(), true);
                topology.setFixture(bag.x(), bag.y(), true);
                sim.addDoodad(new Doodad(bag.x(), bag.y(), def));
            }
            for (Prop p : plan.props()) {
                DoodadDef def = registry.doodad(p.doodad());
                if (def == null || !standable(grid, topology, p)) continue;
                for (int y = p.y(); y < p.y() + p.height(); y++) {
                    for (int x = p.x(); x < p.x() + p.width(); x++) {
                        grid.setWalkable(x, y, false);
                        grid.setSeeThrough(x, y, true);
                        topology.setWall(x, y, false);
                        topology.setFixture(x, y, true);
                    }
                }
                sim.addDoodad(new Doodad(p.x(), p.y(), def, 0));
                for (int y = p.y() - 1; y <= p.y() + p.height(); y++)
                    for (int x = p.x() - 1; x <= p.x() + p.width(); x++) if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
            }
        }
        int gun = 0;
        for (int[] c : plan.guns()) {
            if (!grid.isWalkable(c[0], c[1]) || topology.isFixture(c[0], c[1])) continue;
            ColonyMilitia.seatEmplacement(sim, c, "Camp gun " + (++gun), CORNER_GUN);
        }
        seat(sim, plan.guards(), "Camp guard", risk, rng);
        seat(sim, plan.rearGuards(), "Camp rear guard", risk, rng);
        return new CordonCamp(plan);
    }

    private static void seat(BattleSimulation sim, List<int[]> cells, String name, RiskLevel risk, Random rng) {
        List<int[]> open = new ArrayList<>();
        for (int[] c : cells) if (sim.getGrid().isWalkable(c[0], c[1])) open.add(c);
        if (!open.isEmpty()) ColonyMilitia.seatTeam(sim, open, name, risk, rng);
    }

    private static boolean standable(NavigationGrid grid, CellTopology topology, Prop p) {
        for (int y = p.y(); y < p.y() + p.height(); y++)
            for (int x = p.x(); x < p.x() + p.width(); x++)
                if (!grid.isWalkable(x, y) || topology.isFixture(x, y)) return false;
        return true;
    }

    /** Hits a fallen tree takes: enough to fell any the country grows in one. */
    private static final int FELL = 1_000_000;

    /**
     * Clears the camp's square before anything is set on it: the ground cover, every loose prop, and each tree felled
     * through the ordinary destruction path, as a heavy blade would take it, so its cells open to walking.
     */
    static void clear(BattleSimulation sim, List<int[]> cells) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (int[] c : cells) {
            int x = c[0], y = c[1];
            sim.clearNatureOverlay(x, y);
            if (sim.clearLooseProps(x, y)) topology.setFixture(x, y, false);
            for (int hit = 0; hit < 3 && !grid.isWalkable(x, y) && natural(sim, x, y); hit++) {
                sim.mapEditor().damageWall(x, y, FELL);
            }
            if (sim.clearLooseProps(x, y) && grid.isWalkable(x, y)) topology.setFixture(x, y, false);
        }
    }

    /** Whether a prop is the country's own: a tree or undergrowth, ground scatter, a rock formation. */
    static boolean natural(String id) {
        return id != null && (id.startsWith("forest.") || id.startsWith("nature.") || id.startsWith("formation."));
    }

    /** Whether a tree or other natural prop stands on the cell. */
    private static boolean natural(BattleSimulation sim, int x, int y) {
        for (Doodad d : sim.getDoodads()) {
            String id = d.definitionId();
            if (natural(d.definitionId()) && d.occupiesCell(x, y)) return true;
        }
        return false;
    }

    /**
     * Ground the camp may stand on and clear: outside every building, no wall, no door, and either open ground or a
     * cell only a tree or loose scatter holds.
     */
    public static Open open(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        Set<Long> natural = new HashSet<>();
        for (Doodad d : sim.getDoodads()) {
            if (!natural(d.definitionId())) continue;
            for (int dy = 0; dy < d.footprintCellsY; dy++)
                for (int dx = 0; dx < d.footprintCellsX; dx++) natural.add(key(d.cellX + dx, d.cellY + dy));
        }
        return (x, y) -> grid.inBounds(x, y) && !grid.isDoorway(x, y) && topology.getBuildingId(x, y) == 0
                && !topology.isWall(x, y)
                && (natural.contains(key(x, y)) || grid.isWalkable(x, y) && !topology.isFixture(x, y));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
