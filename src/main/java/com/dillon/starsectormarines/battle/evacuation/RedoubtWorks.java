package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>A piece that can be broken is laid as a {@link NavigationGrid.CellTag#BARRICADE}: the swarm
 * comes on at it and tears it down where the way round is long, rather than treating it as a wall.
 *
 * <p>Planning is read-only and refuses rather than half-builds: a plan whose
 * gate cannot be cut, or whose building door no longer reaches the open ground
 * outside, is no plan, and the holdout keeps its plain door aprons.
 */
final class RedoubtWorks {

    /** Cells between the building and its barricade on a holdout: two yard rows, the firing step, the barricade. */
    static final int HOLDOUT_MARGIN = 4;
    /** The nearest a side may be drawn in toward the building: the firing step and the barricade. */
    static final int MIN_MARGIN = 2;
    /**
     * The least {@link #room} a holdout's block is chosen with while the colony
     * has enough such blocks: below it a side or two of the works are pulled in
     * to the kerb and the yard is gone.
     */
    static final float MIN_ROOM = 0.75f;
    /**
     * Carriageway cells in one unbroken run along a side's line that mean the
     * side lies along the street rather than crossing it. A crossing is as long
     * as the street is wide; a side lying along a street runs on past that.
     */
    static final int ALONG_RUN = 6;
    /** Cells the gate is wide: a marine's lane, and the cohort's. */
    static final int GATE_WIDTH = 2;
    /** Every this many barricade cells, a crate stack stands in the fence instead. */
    private static final int CRATE_EVERY = 5;
    /** Every this many barricade cells, a scrap pile. */
    private static final int SCRAP_EVERY = 11;
    static final String[] TRUCKS = {
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
     * @param corners   the corner gun pits' cells, in the step's corners, furthest from the gate first
     */
    record Plan(int left, int top, int right, int bottom,
                List<Piece> barricade, List<Bag> step, int gateX, int gateY,
                int gunX, int gunY, int leaks, List<int[]> corners) {

        /** Works with no corner guns: a cordon's or a staging post's, which are laid and never manned as a redoubt. */
        Plan(int left, int top, int right, int bottom, List<Piece> barricade, List<Bag> step, int gateX, int gateY,
             int gunX, int gunY, int leaks) {
            this(left, top, right, bottom, barricade, step, gateX, gateY, gunX, gunY, leaks, List.of());
        }

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

    /** Whether a cell is a street's carriageway: its asphalt. */
    interface Roadway {
        boolean at(int x, int y);
    }

    /** Whether a piece of the works could stand on a cell: open ground, off a street's asphalt. */
    interface Ground {
        boolean open(int x, int y);
    }

    /**
     * The share of a building's barricade line ({@code margin} cells out) and
     * firing-step line (one cell in from it) that is open ground off the
     * streets: what room the block has for its works to stand where they are
     * meant to. A building fronting a street has its sides drawn in to the kerb
     * and scores low; one in a yard scores near one.
     */
    static float room(Ground ground, int left, int top, int right, int bottom, int margin) {
        int open = 0, total = 0;
        for (int m = margin; m >= margin - 1 && m >= 1; m--) {
            int l = left - m, t = top - m, r = right + m, b = bottom + m;
            for (int y = t; y <= b; y++) {
                for (int x = l; x <= r; x++) {
                    if (x != l && x != r && y != t && y != b) continue;
                    total++;
                    if (ground.open(x, y)) open++;
                }
            }
        }
        return total == 0 ? 0f : open / (float) total;
    }

    /** Ground the works could stand on in this battle: {@link #open} with no keep, and off the asphalt. */
    static Ground ground(BattleSimulation sim) {
        Roadway road = roadway(sim);
        return (x, y) -> open(sim, Set.of(), (kx, ky) -> true, x, y) && !road.at(x, y);
    }

    /**
     * How far out each side of the works stands from the building, as
     * {@code {west, north, east, south}} (decreasing x, decreasing y, increasing
     * x, increasing y): {@code margin}, drawn in a cell at a time to
     * {@link #MIN_MARGIN} while the side's barricade or step would lie along a
     * street. The works close a street they cross with a truck; laid along one
     * they ate its lanes and left a sliver of road beside the fence.
     */
    static int[] margins(Roadway road, int left, int top, int right, int bottom, int margin) {
        int[] out = new int[4];
        for (int side = 0; side < 4; side++) {
            int m = margin;
            while (m > MIN_MARGIN && (alongRoad(road, left, top, right, bottom, side, m)
                    || alongRoad(road, left, top, right, bottom, side, m - 1))) m--;
            // A road up to the step's line: the barricade stands at the kerb and the
            // side goes without a step, closed rather than left open along the road.
            if (m == MIN_MARGIN && alongRoad(road, left, top, right, bottom, side, m)
                    && !alongRoad(road, left, top, right, bottom, side, m - 1)) m = 1;
            out[side] = m;
        }
        return out;
    }

    /** Whether the line {@code m} cells out on one side, beside the building, holds a run along a street. */
    private static boolean alongRoad(Roadway road, int left, int top, int right, int bottom, int side, int m) {
        boolean vertical = side == 0 || side == 2;
        int fixed = switch (side) { case 0 -> left - m; case 1 -> top - m; case 2 -> right + m; default -> bottom + m; };
        int from = vertical ? top : left, to = vertical ? bottom : right, run = 0;
        for (int along = from; along <= to; along++) {
            run = road.at(vertical ? fixed : along, vertical ? along : fixed) ? run + 1 : 0;
            if (run > ALONG_RUN) return true;
        }
        return false;
    }

    /** Cells of the ring and step lines that lie in a run along a street: left open so the street keeps its lanes. */
    private static Set<Long> alongCells(Roadway road, int l, int t, int r, int b) {
        Set<Long> out = new HashSet<>();
        for (int inset = 0; inset <= 1; inset++) {
            int x0 = l + inset, y0 = t + inset, x1 = r - inset, y1 = b - inset;
            int[][] lines = {{x0, y0, 1, 0, x1 - x0 + 1}, {x0, y1, 1, 0, x1 - x0 + 1},
                    {x0, y0, 0, 1, y1 - y0 + 1}, {x1, y0, 0, 1, y1 - y0 + 1}};
            for (int[] line : lines) {
                int start = 0;
                for (int i = 0; i <= line[4]; i++) {
                    boolean on = i < line[4] && road.at(line[0] + line[2] * i, line[1] + line[3] * i);
                    if (on) continue;
                    if (i - start > ALONG_RUN)
                        for (int k = start; k < i; k++) out.add(key(line[0] + line[2] * k, line[1] + line[3] * k));
                    start = i + 1;
                }
            }
        }
        return out;
    }

    /**
     * Plans the works round one building, gate toward {@code (towardX, towardY)}.
     *
     * @param doorX      the building's door, which must still reach the open ground through the gate
     * @param cornerGuns gun pits wanted in the step's corners, furthest from the gate first; fewer stand where a
     *                   corner has no open ground
     * @return the plan, or {@code null} when no gate can be cut or the door would be shut in
     */
    static Plan plan(BattleSimulation sim, PointOfInterest place, int doorX, int doorY,
                     int towardX, int towardY, int margin, Keep keep, long seed, int cornerGuns) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad("doodad.industrial-fence-straight-h") == null
                || registry.doodad("doodad.fort-sandbag-n") == null) return null;
        NavigationGrid grid = sim.getGrid();
        Roadway road = roadway(sim);
        int[] m = margins(road, place.left, place.top, place.right, place.bottom, margin);
        int l = place.left - m[0], t = place.top - m[1], r = place.right + m[2], b = place.bottom + m[3];
        if (l < 1 || t < 1 || r >= grid.getWidth() - 1 || b >= grid.getHeight() - 1) return null;
        Set<Long> dressed = dressedCells(sim, l - 2, t - 2, r + 2, b + 2);
        // What still lies along a street at the nearest the side can stand is left open.
        dressed.addAll(alongCells(road, l, t, r, b));

        int dx = towardX - (place.left + place.right) / 2, dy = towardY - (place.top + place.bottom) / 2;
        int[] toward = Math.abs(dx) >= Math.abs(dy) ? new int[]{Integer.signum(dx), 0} : new int[]{0, Integer.signum(dy)};
        if (toward[0] == 0 && toward[1] == 0) toward[0] = 1;
        int[][] sides = {toward, {toward[1], toward[0]}, {-toward[1], -toward[0]}, {-toward[0], -toward[1]}};
        for (int[] side : sides) {
            int[] gate = cutGate(sim, dressed, keep, l, t, r, b, side, doorX, doorY);
            if (gate == null) continue;
            Plan plan = build(sim, registry, dressed, keep, road, l, t, r, b, side, gate, seed, cornerGuns);
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

    private static Plan build(BattleSimulation sim, TileRegistry registry, Set<Long> dressed, Keep keep, Roadway road,
                              int l, int t, int r, int b, int[] side, int[] gate, long seed, int cornerGuns) {
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
            Piece truck = truckAcross(sim, registry, taken, keep, road, gateLane, l, t, r, b, edge, seed);
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

        // Corner gun pits: a gun in the step's corner, behind the barricade's, in place of its bag.
        List<int[]> corners = new ArrayList<>();
        for (int[] c : cornersFromGate(sl, st, sr, sb, gate[0], gate[1])) {
            if (corners.size() >= cornerGuns) break;
            long k = key(c[0], c[1]);
            if (gateLane.contains(k)) continue;
            boolean bagged = step.removeIf(bag -> bag.x() == c[0] && bag.y() == c[1]);
            if (!bagged && (taken.contains(k) || !open(sim, dressed, keep, c[0], c[1]))) continue;
            taken.add(k);
            corners.add(c);
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
        return new Plan(l, t, r, b, barricade, step, gate[0], gate[1], gunX, gunY, leaks, List.copyOf(corners));
    }

    /**
     * The four corners of the step's ring ({@code l..r} by {@code t..b}), the one diagonal from the gate first, then
     * the other corner on the far side: a light holdout's one corner gun covers the side its gate gun cannot see, a
     * heavy one's second covers the far flank.
     */
    static List<int[]> cornersFromGate(int l, int t, int r, int b, int gateX, int gateY) {
        List<int[]> corners = new ArrayList<>(List.of(new int[]{l, t}, new int[]{r, t}, new int[]{l, b}, new int[]{r, b}));
        corners.sort(Comparator.comparingInt((int[] c) -> -(Math.abs(c[0] - gateX) + Math.abs(c[1] - gateY)))
                .thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        return corners;
    }

    /**
     * A truck across the barricade where it crosses a street on one edge:
     * three ring cells of the street's carriageway (paved plaza is not a street
     * to close, and a truck on it stood out into the road beyond), and the row
     * outside them, all open. The
     * truck stands half out from the ring, nose to the street.
     */
    private static Piece truckAcross(BattleSimulation sim, TileRegistry registry, Set<Long> taken, Keep keep,
                                     Roadway road, Set<Long> gateLane, int l, int t, int r, int b, int[] edge, long seed) {
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
                if (topology.roadSurface() == null ? topology.isStreet(x, y) : road.at(x, y)) streets++;
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
        for (int[] c : plan.corners()) blocked.add(key(c[0], c[1]));
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
            // A piece that can be broken is a barricade the swarm assaults rather than walks round.
            for (int y = p.y(); y < p.y() + p.height(); y++)
                for (int x = p.x(); x < p.x() + p.width(); x++)
                    if (grid.getWallHp(x, y) > 0) grid.setTag(x, y, NavigationGrid.CellTag.BARRICADE, true);
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

    /** The street asphalt the road finish laid, or none on a map without one. */
    static Roadway roadway(BattleSimulation sim) {
        RoadSurface surface = sim.getTopology().roadSurface();
        if (surface == null) return (x, y) -> false;
        int asphalt = Material.ASPHALT.ordinal();
        return (x, y) -> {
            if (!sim.getGrid().inBounds(x, y)) return false;
            RoadSurface.Patch patch = surface.patch(x, y, asphalt);
            return patch != null && patch.area() > .5;
        };
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
