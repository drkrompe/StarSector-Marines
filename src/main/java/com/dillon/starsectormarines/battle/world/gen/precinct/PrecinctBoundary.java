package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The edge of a precinct, and the ways through it.
 *
 * <p>Both are read off what grew rather than chosen. The outline is wherever a
 * precinct's claim stops, so it follows the shape the growth actually took; a
 * gate is wherever one of its roads runs out through that outline, so it is a
 * road that already exists with a hole in the wall where it leaves rather than
 * a hole rolled somewhere and a road found for it afterwards.
 *
 * <p>That inversion is the point. The shipped wall stamper rolls one to three
 * gate positions along a rectangle's south side, then separately tries to make
 * one of them line up with the vehicle corridor so a convoy can commit — two
 * decisions about the same thing, which have to be kept in agreement. Here
 * there is one: the arm is the gate.
 */
public final class PrecinctBoundary {

    private PrecinctBoundary() {}

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Neighbourhood the cells of one opening are grouped by, diagonals included.
     *
     * <p>A claim grown by orthogonal steps has a diamond-ish boundary, so a road
     * crossing it usually crosses at an angle and its cells come out as a
     * staircase rather than a row. Grouped by orthogonal adjacency, a single
     * seven-cell crossing reads as seven one-cell gates — measured, one garrison
     * reported eighteen gates none of which was wide enough to drive through,
     * when it had three crossings of three, seven and seven.
     *
     * <p>The wall is one cell thick, so the hole a staircase leaves is as
     * passable as the hole a row leaves; how wide an opening is means how many
     * contiguous cells of wall are missing, and contiguous includes diagonally.
     */
    private static final int[][] ADJACENT = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * A way through the boundary: the contiguous run of outline cells one road
     * occupies where it leaves the precinct.
     *
     * @param cells    the outline cells this road covers, in no particular order
     * @param drivable whether the opening is wide enough for a vehicle
     */
    public record Gate(List<int[]> cells, boolean drivable) {

        public int width() {
            return cells.size();
        }
    }

    /**
     * Cells wide an opening must be before a vehicle can use it. Three lets a
     * squad and a vehicle pass abreast, and matches the shipped wall's own gate
     * width — a narrower way out is a footpath, and a precinct with only those
     * has no route for its armour.
     */
    public static final int DRIVABLE_GATE_WIDTH = 3;

    /**
     * The precinct's own cells that touch something else — another precinct,
     * open country, or the map edge.
     *
     * <p>This is where a wall goes if the precinct has one. It is claim cells
     * rather than the ring outside them, because the wall belongs to the place
     * it encloses: drawn on the ground outside, it would be built on whatever
     * the neighbour claimed and would move when the neighbour grew.
     */
    public static boolean[][] outline(int[][] claim, int who, int width, int height) {
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] != who) continue;
                for (int[] step : STEPS) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height
                            || claim[nx][ny] != who) {
                        out[x][y] = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * Where roads leave the precinct.
     *
     * <p>An outline cell carrying this precinct's road, whose outward neighbour
     * is also road, is a way through. Contiguous such cells are one gate rather
     * than several, because a width-five arm leaving the precinct is one road
     * and not five holes.
     *
     * @param owner per-cell road ownership from {@link GrownTrunkPlan.Grown}
     */
    public static List<Gate> gates(int[][] claim, int[][] owner, int who,
                                   int width, int height) {
        boolean[][] outline = outline(claim, who, width, height);
        boolean[][] crossing = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!outline[x][y] || owner[x][y] != who) continue;
                for (int[] step : STEPS) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    if (claim[nx][ny] == who) continue;
                    if (owner[nx][ny] == GrownTrunkPlan.UNOWNED) continue;
                    crossing[x][y] = true;
                    break;
                }
            }
        }

        List<Gate> gates = new ArrayList<>();
        boolean[][] seen = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!crossing[x][y] || seen[x][y]) continue;
                List<int[]> run = new ArrayList<>();
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{x, y});
                seen[x][y] = true;
                while (!stack.isEmpty()) {
                    int[] at = stack.pop();
                    run.add(at);
                    for (int[] step : ADJACENT) {
                        int nx = at[0] + step[0];
                        int ny = at[1] + step[1];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        if (!crossing[nx][ny] || seen[nx][ny]) continue;
                        seen[nx][ny] = true;
                        stack.push(new int[]{nx, ny});
                    }
                }
                gates.add(new Gate(List.copyOf(run), run.size() >= DRIVABLE_GATE_WIDTH));
            }
        }
        return gates;
    }

    /**
     * The cells a wall would stand on: the outline, less the ways through it.
     *
     * <p>A precinct with no drivable gate would be sealed to its own vehicles,
     * so this is where that becomes visible rather than being discovered in a
     * battle. It is not corrected here — a place whose roads all leave it as
     * footpaths is a fact about how it grew, and the caller decides whether to
     * widen one or to accept a walled compound nothing drives out of.
     */
    public static boolean[][] wall(int[][] claim, int[][] owner, int who,
                                   int width, int height) {
        return wall(claim, owner, who, width, height, Integer.MAX_VALUE);
    }

    /**
     * The same, keeping at most {@code maxGates} of the ways through.
     *
     * <p>Growth decides where roads cross the outline; this decides how many of
     * those crossings stay open, which is the manoeuvre half of how hard a place
     * is to take. The widest are kept, because a wide crossing is a main road
     * and a narrow one is where a track happened to touch the line — and because
     * keeping the widest is what makes at least one of them drivable.
     *
     * <p>A crossing that is sealed leaves its road dead-ending at the wall,
     * which is what a closed gate looks like from outside and wants no special
     * handling.
     *
     * <p>The cap cannot manufacture gates. A place whose roads all leave by one
     * route has one gate however many it is allowed, which is why this is stated
     * as a maximum rather than a count.
     */
    public static boolean[][] wall(int[][] claim, int[][] owner, int who,
                                   int width, int height, int maxGates) {
        boolean[][] wall = outline(claim, who, width, height);
        for (Gate gate : openGates(claim, owner, who, width, height, maxGates)) {
            for (int[] cell : gate.cells()) wall[cell[0]][cell[1]] = false;
        }
        return wall;
    }

    /**
     * The crossings that stay open, widest first.
     *
     * <p>At least one drivable crossing survives whatever the cap says, when the
     * place has one at all: a walled installation its own armour cannot leave is
     * a defect rather than a difficulty, and it is the same obligation
     * {@link PrecinctArtery} enforces one step earlier.
     */
    public static List<Gate> openGates(int[][] claim, int[][] owner, int who,
                                       int width, int height, int maxGates) {
        List<Gate> all = new ArrayList<>(gates(claim, owner, who, width, height));
        all.sort((a, b) -> Integer.compare(b.width(), a.width()));
        if (all.size() <= maxGates) return all;

        List<Gate> kept = new ArrayList<>(all.subList(0, Math.max(0, maxGates)));
        if (kept.stream().noneMatch(Gate::drivable)) {
            for (Gate gate : all) {
                if (!gate.drivable()) continue;
                if (!kept.isEmpty()) kept.remove(kept.size() - 1);
                kept.add(gate);
                break;
            }
        }
        return kept;
    }
}
