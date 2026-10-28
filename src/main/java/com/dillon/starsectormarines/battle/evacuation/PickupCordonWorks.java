package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The evac cordon's works ({@code colony-breach-nouns.md}, Evac cordon): an octagon of field works thrown up round
 * the pickup, so the allied line the company brings the survivors back to fights from a trench rather than the open.
 *
 * <p>From the lift out: a <b>trench</b> ({@link #TRENCH} rings out), the run each militia team mans; a sandbag
 * <b>parapet</b> on its lip, bags facing out, closed to movement and open to sight; and hard against the bags a
 * <b>wall</b> of hedgehogs with a crate
 * stack or a scrap pile here and there, closed to movement and open to sight, laid as barricade so the swarm tears it
 * down where the way round is long. No open ground is left outside the bags, so the line stands in the trench rather
 * than on the bags' far side (owner, 2026-10-28: "defense forces should be in the trenches"). Rings are measured on an
 * octagon ({@link #ring}): four straight sides joined by staircase chamfers, so every ring is a closed loop of
 * edge-joined cells and the trench art joins all the way round.
 *
 * <p>Two <b>gates</b> are left, each {@code 2 * GATE_HALF + 1} cells wide through all three rings, where the
 * cohort's walk from the shelter and the company's way from its pads cross the works: the company comes in by them
 * with the survivors, its mechs and vehicles included. Nothing is set down on a landing pad's ground, a door, a
 * building or a prop already there; such cells are left open and counted as leaks.
 *
 * <p>Planning ({@link #layout}) is pure, over the predicates the battle answers, so it is tested without a battle.
 */
public final class PickupCordonWorks {

    /** Ring the trench is dug on: outside the lift's protected square ({@code formationRadius + 2}), round the posts. */
    static final int TRENCH = CivilianEvacuationPlacement.PICKUP_FORMATION_RADIUS + 3;
    /** Ring the parapet's bags stand on, on the trench's outer lip. */
    static final int BAGS = TRENCH + 1;
    /**
     * Ring the wall stands on, against the bags: a walkable cell beside a parapet is part of its fighting position
     * ({@code FightingPositionIndex}), so a walkway there would put the line outside its own bags.
     */
    static final int WALL = BAGS + 1;
    /** How far the corners are cut: each straight side runs {@code 2 * (TRENCH - CHAMFER) + 1} cells. */
    static final int CHAMFER = TRENCH / 2;
    /** Cells either side of a lane's line that a gate keeps open: five wide, a mech's way. */
    static final int GATE_HALF = 2;
    /** Cells of trench either way of a post that its team mans. */
    static final int POST_RADIUS = 5;
    /** Every this many wall cells, a crate stack stands in place of a hedgehog. */
    private static final int CRATE_EVERY = 4;
    /** Every this many wall cells, a scrap pile. */
    private static final int SCRAP_EVERY = 13;
    static final String HEDGEHOG = ColonyRoadblocks.HEDGEHOG;
    static final String CRATES = "doodad.industrial-crate-stack";
    static final String SCRAP = "doodad.industrial-scrap-pile";

    private PickupCordonWorks() {}

    /**
     * Whether the evac cordon is fortified. {@code -Dbattle.colonyBreach.cordonWorks=false} is the control: the
     * line holds the open ground round its posts, as it did. Temporary: remove with the open-ground path once the
     * owner accepts the works from {@code evac-cordon-look}.
     */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.cordonWorks", "true"));
    }

    /** Whether a piece of the works may stand on a cell. */
    interface Open {
        boolean at(int x, int y);
    }

    /**
     * The works as planned round one lift.
     *
     * @param trench cells dug, each {x, y}
     * @param bags   the parapet, as the redoubt works' bags
     * @param wall   the wall, as the redoubt works' barricade pieces
     * @param posts  for each formation point, the trench cell its team mans ({x, y} pairs; -1 where none)
     * @param gated  cells of the works left open for a gate
     * @param leaks  wall cells left open off the gates: ground no piece could stand on
     */
    record Layout(List<int[]> trench, List<RedoubtWorks.Bag> bags, List<RedoubtWorks.Piece> wall,
                  int[] posts, Set<Long> gated, int leaks) {
    }

    /**
     * Which ring of the octagon round the lift a cell offset lies on: the least {@code r} with the cell inside the
     * square of half-side {@code r} with its corners cut along {@code |dx| + |dy| = 2r - CHAMFER}.
     */
    static int ring(int dx, int dy) {
        int ax = Math.abs(dx), ay = Math.abs(dy);
        return Math.max(Math.max(ax, ay), (ax + ay + CHAMFER + 1) / 2);
    }

    /**
     * Plans the works round {@code (liftX, liftY)}.
     *
     * @param map       whether a cell is on the map: a trench or bag whose wall would stand off it is not dug, since
     *                  the map's edge closes that side and a line facing it faces nothing
     * @param lanes     the ways the gates are cut on (the cohort's walk, the company's way in), each a list of
     *                  {x, y} cells from the lift outward; each ring is gated once a lane, where it first crosses it
     * @param formation the pickup formation's points as {x, y} pairs, each given a post ({@link #posts})
     * @param threatX   where the swarm is expected from, the posts weighted toward it ({@link #posts})
     */
    static Layout layout(int liftX, int liftY, Open open, Open map, List<List<int[]>> lanes, int[] formation,
                         int threatX, int threatY, long seed) {
        Set<Long> gated = new HashSet<>();
        for (List<int[]> lane : lanes) {
            for (int r : new int[]{TRENCH, BAGS, WALL}) {
                for (int[] c : lane) {
                    if (ring(c[0] - liftX, c[1] - liftY) < r) continue;
                    for (int y = c[1] - GATE_HALF; y <= c[1] + GATE_HALF; y++) {
                        for (int x = c[0] - GATE_HALF; x <= c[0] + GATE_HALF; x++) {
                            if (ring(x - liftX, y - liftY) == r) gated.add(key(x, y));
                        }
                    }
                    break;
                }
            }
        }
        List<int[]> trench = new ArrayList<>();
        List<RedoubtWorks.Bag> bags = new ArrayList<>();
        List<RedoubtWorks.Piece> wall = new ArrayList<>();
        int leaks = 0;
        int counter = (int) Math.floorMod(seed, CRATE_EVERY);
        // Walked by ring and then round it, so the wall's crates and scrap fall at even steps along it.
        for (int[] c : loop(liftX, liftY, WALL)) {
            if (gated.contains(key(c[0], c[1])) || !map.at(c[0], c[1])) continue;
            if (!open.at(c[0], c[1])) {
                leaks++;
                continue;
            }
            counter++;
            String id = counter % SCRAP_EVERY == 0 ? SCRAP : counter % CRATE_EVERY == 0 ? CRATES : HEDGEHOG;
            wall.add(new RedoubtWorks.Piece(c[0], c[1], id, 0, 1, 1));
        }
        for (int[] c : loop(liftX, liftY, BAGS)) {
            if (gated.contains(key(c[0], c[1])) || !open.at(c[0], c[1]) || !walled(liftX, liftY, c, BAGS, map)) continue;
            bags.add(new RedoubtWorks.Bag(c[0], c[1], "doodad.fort-sandbag-" + facing(c[0] - liftX, c[1] - liftY)));
        }
        for (int[] c : loop(liftX, liftY, TRENCH)) {
            if (gated.contains(key(c[0], c[1])) || !open.at(c[0], c[1]) || !walled(liftX, liftY, c, TRENCH, map)) continue;
            trench.add(c);
        }
        return new Layout(trench, bags, wall, posts(trench, formation, liftX, liftY, threatX, threatY), gated, leaks);
    }

    /**
     * Each formation point's post, weighted toward where the swarm comes from (owner, 2026-10-28: "mainly manning
     * positions in directions where the ravs are likely to come from"). The dug trench is split at the bearing square
     * to the threat: all teams but one spread evenly over the half facing it, each in the middle of an even stretch
     * of its cells in bearing order, and the last watches the rear, at the dug cell most nearly opposite the threat.
     * Where one half has no trench (a map edge, a pad or a gate took it) every team spreads over the other. Even by
     * what was dug, not by the ring, so two teams never share the corner nearest both; and only on a stretch long
     * enough to be a fighting position ({@link FightingPositionIndex#MIN_RUN_CELLS}), never a stub cut off between
     * two gates. Each formation point is then given the nearest post not yet taken.
     */
    static int[] posts(List<int[]> dug, int[] formation, int liftX, int liftY, int threatX, int threatY) {
        int points = formation.length / 2;
        int[] posts = new int[formation.length];
        Arrays.fill(posts, -1);
        List<int[]> trench = manned(dug);
        if (trench.isEmpty()) return posts;
        double toward = Math.atan2(threatY - liftY, threatX - liftX);
        List<int[]> front = new ArrayList<>();
        List<int[]> rear = new ArrayList<>();
        for (int[] c : trench) (Math.abs(off(c, liftX, liftY, toward)) <= Math.PI / 2 ? front : rear).add(c);
        // In bearing order from one flank of the front round to the other.
        front.sort((a, b) -> Double.compare(off(a, liftX, liftY, toward), off(b, liftX, liftY, toward)));
        List<int[]> slots = new ArrayList<>();
        if (front.isEmpty() || rear.isEmpty()) {
            List<int[]> half = front.isEmpty() ? rear : front;
            if (front.isEmpty()) half.sort((a, b) -> Double.compare(off(a, liftX, liftY, toward + Math.PI),
                    off(b, liftX, liftY, toward + Math.PI)));
            for (int k = 0; k < points; k++) slots.add(half.get((2 * k + 1) * half.size() / (2 * points)));
        } else {
            int forward = points - 1;
            for (int k = 0; k < forward; k++) slots.add(front.get((2 * k + 1) * front.size() / (2 * forward)));
            int[] back = rear.get(0);
            for (int[] c : rear) {
                if (Math.abs(off(c, liftX, liftY, toward)) > Math.abs(off(back, liftX, liftY, toward))) back = c;
            }
            slots.add(back);
        }
        for (int p = 0; p < points; p++) {
            int best = -1;
            int bestD = Integer.MAX_VALUE;
            for (int s = 0; s < slots.size(); s++) {
                int dx = slots.get(s)[0] - formation[p * 2], dy = slots.get(s)[1] - formation[p * 2 + 1];
                int d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
            posts[p * 2] = slots.get(best)[0];
            posts[p * 2 + 1] = slots.get(best)[1];
            slots.remove(best);
        }
        return posts;
    }

    /**
     * The trench cells on an edge-joined stretch of at least {@link FightingPositionIndex#MIN_RUN_CELLS}, in their
     * order; every cell where no stretch is that long.
     */
    private static List<int[]> manned(List<int[]> trench) {
        Set<Long> cells = new HashSet<>();
        for (int[] c : trench) cells.add(key(c[0], c[1]));
        Set<Long> kept = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        for (int[] start : trench) {
            if (!seen.add(key(start[0], start[1]))) continue;
            List<Long> run = new ArrayList<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                run.add(key(c[0], c[1]));
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = c[0] + d[0], y = c[1] + d[1];
                    if (cells.contains(key(x, y)) && seen.add(key(x, y))) queue.add(new int[]{x, y});
                }
            }
            if (run.size() >= FightingPositionIndex.MIN_RUN_CELLS) kept.addAll(run);
        }
        if (kept.isEmpty()) return trench;
        List<int[]> out = new ArrayList<>();
        for (int[] c : trench) if (kept.contains(key(c[0], c[1]))) out.add(c);
        return out;
    }

    /** A cell's bearing from the lift off {@code toward}, in {@code (-PI, PI]}. */
    private static double off(int[] c, int liftX, int liftY, double toward) {
        double d = Math.atan2(c[1] - liftY, c[0] - liftX) - toward;
        while (d <= -Math.PI) d += 2 * Math.PI;
        while (d > Math.PI) d -= 2 * Math.PI;
        return d;
    }

    /** Whether the wall straight out from a cell of ring {@code r} stands on the map. */
    private static boolean walled(int liftX, int liftY, int[] c, int r, Open map) {
        int dx = c[0] - liftX, dy = c[1] - liftY;
        return map.at(liftX + Math.round(dx * (float) WALL / r), liftY + Math.round(dy * (float) WALL / r));
    }

    /** The cells of one ring, in the order of their bearing round the lift. */
    static List<int[]> loop(int liftX, int liftY, int r) {
        List<int[]> cells = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (ring(dx, dy) == r) cells.add(new int[]{liftX + dx, liftY + dy});
            }
        }
        cells.sort((a, b) -> Double.compare(Math.atan2(a[1] - liftY, a[0] - liftX), Math.atan2(b[1] - liftY, b[0] - liftX)));
        return cells;
    }

    /**
     * The sandbag art for a parapet bag at this offset, facing out: a straight course where one side of it is open
     * ground beyond the parapet, a corner where two are, or where it sits in the inner step of a chamfer.
     */
    static String facing(int dx, int dy) {
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        int ox = sx != 0 && ring(dx + sx, dy) > BAGS ? sx : 0;
        int oy = sy != 0 && ring(dx, dy + sy) > BAGS ? sy : 0;
        if (ox == 0 && oy == 0) {
            ox = sx;
            oy = sy;
        }
        if (ox != 0 && oy != 0) return "corner-" + letter(0, oy) + letter(ox, 0);
        return String.valueOf(letter(ox, oy));
    }

    /**
     * Plans and lays the works round the pickup on the live map before the first tick, gates cut along the cohort's
     * walk and the company's way in from its nearest pad, and the line's posts weighted toward the town: the shelter
     * stands at its heart, where the bores bud toward and the swarm comes out of. Returns the trench post of each
     * formation point ({x, y} pairs, -1 where none), or {@code null} when the art is missing and nothing was laid.
     */
    public static int[] raise(BattleSimulation sim, CivilianEvacuationPlacement placement, long seed) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad(HEDGEHOG) == null || registry.doodad("doodad.fort-trench-0") == null
                || registry.doodad("doodad.fort-sandbag-n") == null) return null;
        NavigationGrid grid = sim.getGrid();
        List<LandingPad> pads = sim.getMarineLandingPads();
        List<List<int[]>> lanes = new ArrayList<>();
        addPath(lanes, grid, placement.liftX, placement.liftY, placement.shelterApproachX, placement.shelterApproachY);
        LandingPad nearest = null;
        int best = Integer.MAX_VALUE;
        for (LandingPad pad : pads) {
            int d = Math.abs(pad.centerX - placement.liftX) + Math.abs(pad.centerY - placement.liftY);
            if (d < best) {
                best = d;
                nearest = pad;
            }
        }
        if (nearest != null) addPath(lanes, grid, placement.liftX, placement.liftY, nearest.centerX, nearest.centerY);
        Set<Long> dressed = new HashSet<>();
        for (Doodad d : sim.getDoodads())
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++)
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) dressed.add(key(x, y));
        CellTopology topology = sim.getTopology();
        Open open = (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && topology.getBuildingId(x, y) == 0 && !topology.isFixture(x, y) && !topology.isWall(x, y)
                && !dressed.contains(key(x, y)) && !onPad(pads, x, y);
        Layout layout = layout(placement.liftX, placement.liftY, open, grid::inBounds, lanes,
                placement.formationCells(), placement.shelterX, placement.shelterY, seed);
        lay(sim, registry, layout);
        return layout.posts();
    }

    /** The walked way from the lift out to {@code (toX, toY)}, as a lane. */
    private static void addPath(List<List<int[]>> lanes, NavigationGrid grid, int liftX, int liftY, int toX, int toY) {
        int[] path = GridPathfinder.findPath(grid, liftX, liftY, toX, toY);
        if (Paths.isEmpty(path)) return;
        List<int[]> lane = new ArrayList<>();
        for (int i = 0, n = Paths.cellCount(path); i < n; i++) lane.add(new int[]{Paths.cellX(path, i), Paths.cellY(path, i)});
        lanes.add(lane);
    }

    /** A pad's ground and a cell round it, where a boat sets down. */
    private static boolean onPad(List<LandingPad> pads, int x, int y) {
        for (LandingPad pad : pads) {
            if (Math.abs(x - pad.centerX) <= pad.halfWidth + 1 && Math.abs(y - pad.centerY) <= pad.halfHeight + 1) return true;
        }
        return false;
    }

    /**
     * Digs the trench (an earth floor and a section joined to its trench neighbours, which the fighting-position
     * index reads as a run) and sets the parapet and wall down as the redoubt works' own pieces.
     */
    private static void lay(BattleSimulation sim, TileRegistry registry, Layout layout) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        Set<Long> dug = new HashSet<>();
        for (int[] c : layout.trench()) dug.add(key(c[0], c[1]));
        int dirt = topology.surfaceIndex("floors.dirt");
        for (int[] c : layout.trench()) {
            int x = c[0], y = c[1], mask = 0;
            if (dug.contains(key(x, y + 1))) mask |= 1;
            if (dug.contains(key(x + 1, y))) mask |= 2;
            if (dug.contains(key(x, y - 1))) mask |= 4;
            if (dug.contains(key(x - 1, y))) mask |= 8;
            DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
            if (def == null) continue;
            topology.setGroundKind(x, y, GroundKind.DIRT);
            topology.setSurface(x, y, dirt);
            topology.setNatureOverlayIndex(x, y, -1);
            grid.setSeeThrough(x, y, true);
            topology.setFixture(x, y, true);
            sim.addDoodad(new Doodad(x, y, def));
        }
        List<RedoubtWorks.Bag> bags = new ArrayList<>();
        for (RedoubtWorks.Bag bag : layout.bags()) if (registry.doodad(bag.doodad()) != null) bags.add(bag);
        List<RedoubtWorks.Piece> wall = new ArrayList<>();
        for (RedoubtWorks.Piece piece : layout.wall()) if (registry.doodad(piece.doodad()) != null) wall.add(piece);
        RedoubtWorks.lay(sim, new RedoubtWorks.Plan(0, 0, 0, 0, wall, bags, -1, -1, -1, -1, layout.leaks()));
        // The parapet is fought over, not stood on: closed to movement like the wall, so the only ground the line's
        // run holds is the trench behind it, and the way in is by the gates.
        for (RedoubtWorks.Bag bag : bags) grid.setWalkable(bag.x(), bag.y(), false);
        for (RedoubtWorks.Bag bag : bags)
            for (int y = bag.y() - 1; y <= bag.y() + 1; y++)
                for (int x = bag.x() - 1; x <= bag.x() + 1; x++)
                    if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
    }

    /** The sandbag art's facing letter for a world direction, as the prepared-ground courses name it. */
    private static char letter(int dx, int dy) {
        if (dy > 0) return 'n';
        if (dy < 0) return 's';
        return dx > 0 ? 'e' : 'w';
    }

    static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
