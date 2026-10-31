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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Road's safe point ({@code cordon-nouns.md}, Camp): a small allied encampment on the corridor behind the line,
 * where the convoys set out from and come back to. A homecoming convoy stops on the road through it, its townspeople
 * climb down and walk in to wait there for the rest of the battle (they are out the moment they step off), and its
 * trucks pull back down the road to wait for the next trip.
 *
 * <p>The camp is a square of open ground {@code 2 * HALF + 1} cells across round a corridor cell, the road through it
 * kept clear, a sandbag ring on its edge broken where the road crosses it, crate stacks in its corners, an aid
 * station, a field HQ and a supply dump on its rear half either side of the road, and a few allied guards on its side
 * toward the front. It is found at setup round where the convoys would set out
 * ({@link #plan}, pure), on the nearest corridor cell whose square is mostly open ground and stands clear of the
 * landing field, the logistics base's apron and the air base.
 */
public final class CordonCamp {

    /** Cells from the camp's middle to its edge. */
    public static final int HALF = 12;
    /** Cells either side of the road through the camp kept clear for the trucks. */
    static final int ROAD_KEEP = 3;
    /** Cells the camp's square keeps from each point kept off (a landing pad, a base berth, an air base stand). */
    static final int KEEP_OFF = 12;
    /** Corridor cells either way of the convoys' entry the camp is looked for. */
    static final int SEARCH = 40;
    /** Corridor cells short of the gap the camp keeps at the nearest: it stands behind the line. */
    static final int SHORT_OF_GAP = 60;
    /** The least share of the square that must be open ground. */
    static final float MIN_OPEN = 0.5f;
    /** Allied guards standing in the camp. UNTUNED. */
    static final int GUARDS = 4;
    /** Cells the camp's square keeps from the map's edge. */
    static final int EDGE = 4;

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

    /** Whether a cell is open ground the camp may stand on. */
    public interface Open {
        boolean at(int x, int y);
    }

    /** A sandbag on the ring: its cell and the way it faces out, as the art names it ({@code n}, {@code s}, ...). */
    public record Bag(int x, int y, char facing) { }

    /**
     * Where the camp stands: the corridor cell the trucks stop at (its middle), its open cells, the ring's bags, where
     * the people wait (nearest the middle first), the crate stacks' cells and the guards' cells.
     */
    public record Plan(int dropIndex, int cx, int cy, List<int[]> cells, List<Bag> bags, List<int[]> waiting,
                List<int[]> crates, List<int[]> guards, List<Prop> props) { }

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
     * Finds the camp round the convoys' entry: on the corridor cell nearest {@code entry} (the rear way first on a
     * tie), never nearer the gap than {@link #SHORT_OF_GAP}, whose square lies on the map, keeps {@link #KEEP_OFF}
     * from every point in {@code keepOff} and is at least {@link #MIN_OPEN} open. {@code null} where none is.
     *
     * @param front +1 or -1, the front's way in y
     */
    public static Plan plan(List<int[]> corridor, int entry, int gapIndex, int front, int width, int height, Open open,
                     List<int[]> keepOff) {
        int hi = Math.min(corridor.size() - 1, Math.min(entry + SEARCH, gapIndex - SHORT_OF_GAP));
        int lo = Math.max(1, entry - SEARCH);
        List<Integer> order = new ArrayList<>();
        for (int i = lo; i <= hi; i++) order.add(i);
        order.sort(Comparator.<Integer>comparingInt(i -> Math.abs(i - entry)).thenComparingInt(i -> i));
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
        List<int[]> inner = new ArrayList<>(), crates = new ArrayList<>(), guards = new ArrayList<>();
        for (int[] c : cells) {
            int dx = c[0] - cx, dy = c[1] - cy;
            int ring = Math.max(Math.abs(dx), Math.abs(dy));
            long k = key(c[0], c[1]);
            if (ring == HALF) {
                if (!road.contains(k)) bags.add(new Bag(c[0], c[1], facing(dx, dy)));
            } else if (ring == HALF - 1 && Math.abs(dx) == HALF - 1 && Math.abs(dy) == HALF - 1) {
                if (!near.contains(k)) crates.add(c);
            } else if (ring <= HALF - 2 && !near.contains(k)) {
                inner.add(c);
            }
        }
        // Guards on the side toward the front, just inside the bags, spread along it.
        List<int[]> frontRow = new ArrayList<>();
        for (int[] c : cells) {
            if (c[1] - cy == front * (HALF - 1) && Math.abs(c[0] - cx) < HALF - 1 && !road.contains(key(c[0], c[1]))) {
                frontRow.add(c);
            }
        }
        frontRow.sort(Comparator.comparingInt(c -> c[0]));
        for (int g = 0; g < GUARDS && !frontRow.isEmpty(); g++) {
            guards.add(frontRow.get((int) ((g + .5f) * frontRow.size() / GUARDS)));
        }
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
        return new Plan(index, cx, cy, List.copyOf(cells), bags, inner, crates, guards, props);
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
     * Raises the camp on the live map before the first tick: the bags (walkable, open to sight), the crate stacks
     * (closed to movement, open to sight) and the guards, an allied team under garrison authority. {@code null} where
     * {@link #plan} finds no ground.
     */
    public static CordonCamp raise(BattleSimulation sim, Plan plan, RiskLevel risk, Random rng) {
        if (plan == null) return null;
        TileRegistry registry = TileRegistry.installed();
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        if (registry != null) {
            for (Bag bag : plan.bags()) {
                DoodadDef def = registry.doodad("doodad.fort-sandbag-" + bag.facing());
                if (def == null) continue;
                grid.setSeeThrough(bag.x(), bag.y(), true);
                topology.setFixture(bag.x(), bag.y(), true);
                sim.addDoodad(new Doodad(bag.x(), bag.y(), def));
            }
            List<Prop> props = new ArrayList<>(plan.props());
            for (int[] c : plan.crates()) props.add(new Prop("doodad.industrial-crate-stack", c[0], c[1], 1, 1));
            for (Prop p : props) {
                DoodadDef def = registry.doodad(p.doodad());
                if (def == null) continue;
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
        if (!plan.guards().isEmpty()) ColonyMilitia.seatTeam(sim, plan.guards(), "Camp guard", risk, rng);
        return new CordonCamp(plan);
    }

    /** Open ground on the live map: walkable, outside every building, no door, no prop, no wall. */
    public static Open open(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        return (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && topology.getBuildingId(x, y) == 0 && !topology.isFixture(x, y) && !topology.isWall(x, y);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
