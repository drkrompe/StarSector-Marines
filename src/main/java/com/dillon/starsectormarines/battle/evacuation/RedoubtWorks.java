package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The works a redoubt's people have thrown up round the block they hold
 * ({@code colony-breach-nouns.md}, Redoubt): not a building with bags at the
 * door but a yard closed off round it.
 *
 * <p>Everything is a piece the battle already has. The <b>barricade</b> is a
 * ring of industrial fence a few cells out from the building, closed to
 * movement and open to sight, broken here and there by stacked crates and
 * scrap; where it crosses a street a parked truck is dragged across it. The
 * fence, the crates and the scrap are the ordinary destructible cover props,
 * so a charge, a shell or a Brute opens them as it opens any fence. Behind the
 * barricade runs a <b>firing step</b> of sandbag, one course all the way round,
 * which the fighting-position index reads as a run the militia man. One
 * <b>gate</b> is left, on the side toward the lift, the way relief comes in and
 * the people walk out, and the post's heavy gun stands just inside it. Where
 * another building or a wall already stands on the ring it closes that side
 * itself, the way a desperate fort uses its neighbours.
 *
 * <p>Planning is read-only and refuses rather than half-builds: a plan whose
 * gate cannot be cut, or whose building door no longer reaches the open ground
 * outside, is no plan, and the holdout keeps its plain door aprons.
 */
final class RedoubtWorks {

    /** Cells between the building and its barricade on a holdout: two yard rows, the firing step, the barricade. */
    static final int HOLDOUT_MARGIN = 4;
    /** Cells the gate is wide: a marine's lane, and the cohort's. */
    static final int GATE_WIDTH = 2;
    /** Every this many barricade cells, a crate stack stands in the fence instead. */
    private static final int CRATE_EVERY = 5;
    /** Every this many barricade cells, a scrap pile. */
    private static final int SCRAP_EVERY = 11;
    private static final String[] TRUCKS = {
            "doodad.parked-utility-truck", "doodad.parked-flatbed-truck",
            "doodad.parked-cargo-truck", "doodad.parked-box-van"};

    private RedoubtWorks() {}

    /** One piece of the barricade: what stands on {@code (x, y)}. */
    record Piece(int x, int y, String doodad, int quarterTurns, int width, int height) { }

    /** One sandbag of the firing step, bags facing out. */
    record Bag(int x, int y, String doodad) { }

    /**
     * A planned redoubt: its claimed ground (the barricade's rectangle), its
     * pieces, its firing step, its gate and where the gun goes.
     *
     * @param gateX     the outside cell of the gate's first lane
     * @param gateY     the outside cell of the gate's first lane
     * @param leaks     ring cells left open other than the gate: ground no piece could be set on
     */
    record Plan(int left, int top, int right, int bottom,
                List<Piece> barricade, List<Bag> step, int gateX, int gateY,
                int gunX, int gunY, int leaks) {

        boolean contains(int x, int y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        /** Whether a cell lies strictly inside the barricade: in the yard or the building. */
        boolean inside(int x, int y) {
            return x > left && x < right && y > top && y < bottom;
        }
    }

    /** What the planner may not build on: the cohort's walk, the pads and the protected zones. */
    interface Keep {
        boolean clear(int x, int y);
    }

    /**
     * Plans the works round one building, gate toward {@code (towardX, towardY)}.
     *
     * @param doorX the building's door, which must still reach the open ground through the gate
     * @return the plan, or {@code null} when no gate can be cut or the door would be shut in
     */
    static Plan plan(BattleSimulation sim, PointOfInterest place, int doorX, int doorY,
                     int towardX, int towardY, int margin, Keep keep, long seed) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad("doodad.industrial-fence-straight-h") == null
                || registry.doodad("doodad.fort-sandbag-n") == null) return null;
        NavigationGrid grid = sim.getGrid();
        int l = place.left - margin, t = place.top - margin, r = place.right + margin, b = place.bottom + margin;
        if (l < 1 || t < 1 || r >= grid.getWidth() - 1 || b >= grid.getHeight() - 1) return null;
        Set<Long> dressed = dressedCells(sim, l - 2, t - 2, r + 2, b + 2);

        int dx = towardX - (place.left + place.right) / 2, dy = towardY - (place.top + place.bottom) / 2;
        int[] toward = Math.abs(dx) >= Math.abs(dy) ? new int[]{Integer.signum(dx), 0} : new int[]{0, Integer.signum(dy)};
        if (toward[0] == 0 && toward[1] == 0) toward[0] = 1;
        int[][] sides = {toward, {toward[1], toward[0]}, {-toward[1], -toward[0]}, {-toward[0], -toward[1]}};
        for (int[] side : sides) {
            int[] gate = cutGate(sim, dressed, keep, l, t, r, b, side, doorX, doorY);
            if (gate == null) continue;
            Plan plan = build(sim, registry, dressed, keep, l, t, r, b, side, gate, seed);
            if (plan != null && reaches(grid, plan, doorX, doorY)) return plan;
        }
        return null;
    }

    /**
     * Where the gate goes on one side: the start of {@link #GATE_WIDTH} ring
     * cells nearest the door's line that are open ground, with open ground
     * outside them and in the yard behind them; or null.
     */
    private static int[] cutGate(BattleSimulation sim, Set<Long> dressed, Keep keep,
                                 int l, int t, int r, int b, int[] side, int doorX, int doorY) {
        boolean vertical = side[0] != 0;
        int fixed = vertical ? (side[0] > 0 ? r : l) : (side[1] > 0 ? b : t);
        int min = (vertical ? t : l) + 2, max = (vertical ? b : r) - 1 - GATE_WIDTH;
        int preferred = Math.max(min, Math.min(max, vertical ? doorY : doorX));
        for (int offset = 0; offset <= max - min; offset++) {
            for (int sign : new int[]{1, -1}) {
                int start = preferred + sign * offset;
                if (start < min || start > max) continue;
                boolean ok = true;
                for (int k = 0; k < GATE_WIDTH && ok; k++) {
                    int along = start + k;
                    int x = vertical ? fixed : along, y = vertical ? along : fixed;
                    ok = open(sim, dressed, keep, x, y)
                            && sim.getGrid().isWalkable(x + side[0], y + side[1])
                            && sim.getGrid().isWalkable(x - side[0], y - side[1]);
                }
                if (ok) return vertical ? new int[]{fixed, start} : new int[]{start, fixed};
            }
        }
        return null;
    }

    private static Plan build(BattleSimulation sim, TileRegistry registry, Set<Long> dressed, Keep keep,
                              int l, int t, int r, int b, int[] side, int[] gate, long seed) {
        boolean vertical = side[0] != 0;
        Set<Long> gateLane = new HashSet<>();
        for (int k = 0; k < GATE_WIDTH; k++) {
            int gx = vertical ? gate[0] : gate[0] + k, gy = vertical ? gate[1] + k : gate[1];
            // The gate and the lane straight in through the firing step stay open.
            for (int depth = 0; depth <= 2; depth++) gateLane.add(key(gx - side[0] * depth, gy - side[1] * depth));
        }
        Set<Long> taken = new HashSet<>(dressed);
        List<Piece> barricade = new ArrayList<>();
        int leaks = 0;

        // A truck dragged across each street the barricade crosses, one a side.
        for (int[] edge : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            Piece truck = truckAcross(sim, registry, taken, keep, gateLane, l, t, r, b, edge, seed);
            if (truck == null) continue;
            barricade.add(truck);
            for (int y = truck.y(); y < truck.y() + truck.height(); y++)
                for (int x = truck.x(); x < truck.x() + truck.width(); x++) taken.add(key(x, y));
        }

        int counter = (int) Math.floorMod(seed, CRATE_EVERY);
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                if (x != l && x != r && y != t && y != b) continue;
                if (gateLane.contains(key(x, y)) || taken.contains(key(x, y))) continue;
                if (!open(sim, dressed, keep, x, y)) {
                    if (sim.getGrid().isWalkable(x, y) && sim.getTopology().getBuildingId(x, y) == 0) leaks++;
                    continue;
                }
                boolean corner = (x == l || x == r) && (y == t || y == b);
                String id;
                counter++;
                if (corner) id = "doodad.industrial-fence-corner-" + (y == t ? "n" : "s") + (x == l ? "w" : "e");
                else if (counter % SCRAP_EVERY == 0) id = "doodad.industrial-scrap-pile";
                else if (counter % CRATE_EVERY == 0) id = "doodad.industrial-crate-stack";
                else id = "doodad.industrial-fence-straight-" + (y == t || y == b ? "h" : "v");
                DoodadDef def = registry.doodad(id);
                if (def == null || def.footprintCellsX != 1 || def.footprintCellsY != 1) {
                    id = "doodad.industrial-fence-straight-" + (y == t || y == b ? "h" : "v");
                }
                barricade.add(new Piece(x, y, id, 0, 1, 1));
                taken.add(key(x, y));
            }
        }

        // The firing step: one course of sandbag a cell inside the barricade, bags facing out.
        List<Bag> step = new ArrayList<>();
        int sl = l + 1, st = t + 1, sr = r - 1, sb = b - 1;
        for (int y = st; y <= sb; y++) {
            for (int x = sl; x <= sr; x++) {
                if (x != sl && x != sr && y != st && y != sb) continue;
                if (gateLane.contains(key(x, y)) || taken.contains(key(x, y)) || !open(sim, dressed, keep, x, y)) continue;
                int ox = x == sl ? -1 : x == sr ? 1 : 0;
                int oy = y == st ? -1 : y == sb ? 1 : 0;
                String name = ox != 0 && oy != 0
                        ? "corner-" + letter(0, oy) + letter(ox, 0)
                        : String.valueOf(letter(ox, oy));
                if (registry.doodad("doodad.fort-sandbag-" + name) == null) continue;
                step.add(new Bag(x, y, "doodad.fort-sandbag-" + name));
                taken.add(key(x, y));
            }
        }

        // The gun stands just inside the gate, beside the lane, behind the step.
        int gunX = -1, gunY = -1;
        for (int along : new int[]{-1, GATE_WIDTH}) {
            int x = (vertical ? gate[0] : gate[0] + along) - side[0] * 2;
            int y = (vertical ? gate[1] + along : gate[1]) - side[1] * 2;
            if (taken.contains(key(x, y)) || !open(sim, dressed, keep, x, y)) continue;
            gunX = x;
            gunY = y;
            break;
        }
        return new Plan(l, t, r, b, barricade, step, gate[0], gate[1], gunX, gunY, leaks);
    }

    /**
     * A truck across the barricade where it crosses a street on one edge:
     * three ring cells of street, and the row outside them, all open. The
     * truck stands half out from the ring, nose to the street.
     */
    private static Piece truckAcross(BattleSimulation sim, TileRegistry registry, Set<Long> taken, Keep keep,
                                     Set<Long> gateLane, int l, int t, int r, int b, int[] edge, long seed) {
        String id = TRUCKS[(int) Math.floorMod(seed + edge[0] * 3L + edge[1], TRUCKS.length)];
        DoodadDef def = registry.doodad(id);
        if (def == null) return null;
        boolean horizontal = edge[1] != 0;
        int turns = horizontal ? 0 : 1;
        int w = horizontal ? def.footprintCellsX : def.footprintCellsY;
        int h = horizontal ? def.footprintCellsY : def.footprintCellsX;
        int length = horizontal ? w : h, depth = horizontal ? h : w;
        if (depth < 1 || length < 1) return null;
        int ring = horizontal ? (edge[1] < 0 ? t : b) : (edge[0] < 0 ? l : r);
        int from = (horizontal ? l : t) + 2, to = (horizontal ? r : b) - 2 - (length - 1);
        CellTopology topology = sim.getTopology();
        for (int along = from; along <= to; along++) {
            int streets = 0;
            boolean ok = true;
            for (int i = 0; i < length && ok; i++) {
                int x = horizontal ? along + i : ring, y = horizontal ? ring : along + i;
                if (topology.isStreet(x, y)) streets++;
                for (int d = 0; d < depth && ok; d++) {
                    // The footprint runs outward from the ring, so the truck stands outside the step.
                    int fx = x + (horizontal ? 0 : edge[0] * d), fy = y + (horizontal ? edge[1] * d : 0);
                    ok = !gateLane.contains(key(fx, fy)) && !taken.contains(key(fx, fy))
                            && open(sim, Set.of(), keep, fx, fy);
                }
            }
            if (!ok || streets < length) continue;
            int minX = horizontal ? along : (edge[0] < 0 ? ring - (depth - 1) : ring);
            int minY = horizontal ? (edge[1] < 0 ? ring - (depth - 1) : ring) : along;
            return new Piece(minX, minY, id, turns, w, h);
        }
        return null;
    }

    /** Whether the door still reaches open ground outside the barricade once the works stand. */
    private static boolean reaches(NavigationGrid grid, Plan plan, int doorX, int doorY) {
        Set<Long> blocked = new HashSet<>();
        for (Piece p : plan.barricade())
            for (int y = p.y(); y < p.y() + p.height(); y++)
                for (int x = p.x(); x < p.x() + p.width(); x++) blocked.add(key(x, y));
        if (plan.gunX() >= 0) blocked.add(key(plan.gunX(), plan.gunY()));
        if (!grid.inBounds(doorX, doorY) || !grid.isWalkable(doorX, doorY)) return false;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{doorX, doorY});
        seen.add(key(doorX, doorY));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (!plan.contains(c[0], c[1])) return true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                long k = key(nx, ny);
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || blocked.contains(k) || !seen.add(k)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return false;
    }

    /**
     * Sets the works down on the live map before the first tick, as a
     * generated yard's fence and a held base's aprons are set down: barricade
     * pieces close their cells to movement and keep them open to sight, the
     * step's bags leave theirs walkable.
     */
    static void lay(BattleSimulation sim, Plan plan) {
        TileRegistry registry = TileRegistry.installed();
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (Piece p : plan.barricade()) {
            DoodadDef def = registry.doodad(p.doodad());
            for (int y = p.y(); y < p.y() + p.height(); y++) {
                for (int x = p.x(); x < p.x() + p.width(); x++) {
                    grid.setWalkable(x, y, false);
                    grid.setSeeThrough(x, y, true);
                    topology.setWall(x, y, false);
                    topology.setFixture(x, y, true);
                }
            }
            sim.addDoodad(new Doodad(p.x(), p.y(), def, p.quarterTurns()));
            for (int y = p.y() - 1; y <= p.y() + p.height(); y++)
                for (int x = p.x() - 1; x <= p.x() + p.width(); x++)
                    if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
        }
        for (Bag bag : plan.step()) {
            grid.setSeeThrough(bag.x(), bag.y(), true);
            topology.setFixture(bag.x(), bag.y(), true);
            sim.addDoodad(new Doodad(bag.x(), bag.y(), registry.doodad(bag.doodad())));
        }
    }

    /** Open ground a piece can stand on: walkable, outside every building, no door, no prop, nothing kept clear. */
    private static boolean open(BattleSimulation sim, Set<Long> dressed, Keep keep, int x, int y) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && topology.getBuildingId(x, y) == 0 && !topology.isFixture(x, y) && !topology.isWall(x, y)
                && !dressed.contains(key(x, y)) && keep.clear(x, y);
    }

    private static Set<Long> dressedCells(BattleSimulation sim, int l, int t, int r, int b) {
        Set<Long> cells = new HashSet<>();
        for (Doodad d : sim.getDoodads()) {
            if (d.cellX > r || d.cellY > b || d.cellX + d.footprintCellsX <= l || d.cellY + d.footprintCellsY <= t) continue;
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++)
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) cells.add(key(x, y));
        }
        return cells;
    }

    /** The sandbag art's facing letter for a world direction, as the prepared-ground courses name it. */
    private static char letter(int dx, int dy) {
        if (dy > 0) return 'n';
        if (dy < 0) return 's';
        return dx > 0 ? 'e' : 'w';
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
