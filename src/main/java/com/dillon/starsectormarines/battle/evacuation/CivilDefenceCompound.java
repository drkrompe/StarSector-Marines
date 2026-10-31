package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The colony's civil-defence compound: the main redoubt
 * ({@code colony-breach-nouns.md}, Redoubt).
 *
 * <p>A frontier colony that always knew something might come out of the
 * wilderness built it when the colony was laid out, so it is a place in the
 * colony's plan rather than works thrown up round a house: a walled precinct
 * ({@link #PLACE}) with a shelter hall, a clinic and a store round a muster
 * yard, its wall light concrete at {@link #WALL_HP} and its gates where the
 * colony's roads meet it. Roads, gates and neighbours fit it by construction.
 *
 * <p>What the generator leaves is a wall. This class is what the militia make
 * of it when the battle is set up, and it is civil rather than military: a
 * squat <b>tower</b> on each outer corner, a heavy machine gun in a slitted
 * <b>gun tower</b> beside each gate (the compound's whole armament: no layers,
 * no bunkers, no anti-air), <b>firing slits</b> along the straight runs, a
 * the <b>wall posts</b> behind the slits a wall team stands at, and
 * <b>floodlights</b> on tripods in the corners and at the gates, facing out.
 */
public final class CivilDefenceCompound {

    /** The precinct's name in the colony plan. */
    public static final String PLACE = "civil-defence";
    /** Wall left in a civil panel: a colony wall's, inside what a Brute can break. */
    public static final int WALL_HP = 110;
    /** Ways in: a colony keeps a main gate and a back gate. */
    public static final int GATES = 2;

    /** How far from its plan seed the compound's shelter hall may be found. */
    private static final int SHELTER_REACH = 45;
    /** How far from the shelter the compound's wall is looked for. */
    private static final int WALL_REACH = 60;
    /** The widest crossing read as a gate rather than as a gap in the reading. */
    private static final int MAX_GATE = 10;
    /** How far past the wall's extent the yard may run before the wall is read as open. */
    private static final int LEAK = 6;
    /** How far outside the hall its doorstep is looked for: through the building it stands in, to open ground. */
    private static final int DOORSTEP_REACH = 12;
    /** Cells along the wall from a gate's edge a gun tower's centre may stand: beside it, or past what abuts it. */
    private static final int TOWER_REACH = 4;
    private static final String LIGHT_CIRCUIT = "civil-defence";

    enum Kind { WALL, SLIT, GUN }

    /**
     * The dressing planned for one compound.
     *
     * @param cells what the dressing makes of each cell, keyed by packed cell
     * @param guns    the gun towers' centre cells
     * @param corners the corner towers' gun cells, for a front-facing dressing ({@link #dress(BattleSimulation, int,
     *                int, int, int, int, int, int, int)}); empty otherwise
     * @param gateX   the first cell of the gate nearest the lift
     * @param gateY   the first cell of the gate nearest the lift
     */
    record Plan(int left, int top, int right, int bottom, Map<Long, Kind> cells, List<int[]> guns,
                List<RedoubtWorks.Bag> posts, List<NightLighting.Lamp> lamps, int gateX, int gateY,
                List<int[]> corners) {}

    private CivilDefenceCompound() {}

    /** Whether the colony has a civil-defence compound. {@code -Dbattle.colonyBreach.civilDefence=false} is the control. */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.civilDefence", "true"));
    }

    /**
     * The places the colony's shelter may be: on a plan with a compound, its
     * shelter hall alone, re-anchored in {@code points} where its people stand,
     * or {@code null} when the map did not build one (the caller re-rolls).
     * Every point of interest on any other map.
     */
    public static List<PointOfInterest> shelters(NavigationGrid grid, List<PointOfInterest> points,
                                                 PrecinctPlan plan) {
        Precinct compound = null;
        if (plan != null) {
            for (Precinct precinct : plan.precincts()) if (precinct.name().equals(PLACE)) compound = precinct;
        }
        if (compound == null) return points;
        PointOfInterest best = null;
        int bestDistance = SHELTER_REACH * SHELTER_REACH + 1;
        for (PointOfInterest poi : points) {
            if (poi == null || poi.kind != PointOfInterest.Kind.RESIDENTIAL) continue;
            int dx = poi.centerX() - compound.seedX(), dy = poi.centerY() - compound.seedY();
            if (dx * dx + dy * dy < bestDistance) {
                best = poi;
                bestDistance = dx * dx + dy * dy;
            }
        }
        if (best == null) return null;
        int[] anchors = throughTheDoor(grid, best);
        if (anchors == null) return null;
        PointOfInterest hall = new PointOfInterest(best.kind, best.left, best.top, best.right,
                best.bottom, anchors[2], anchors[3], anchors[0], anchors[1]);
        // Replaced where it stands, so the militia and the colony's people find the same hall.
        points.set(points.indexOf(best), hall);
        return List.of(hall);
    }

    /**
     * The hall's floor cell nearest its middle that the door reaches, and the
     * nearest open ground that door leads out to: {@code {insideX, insideY,
     * doorstepX, doorstepY}}. The hall is furnished with bunks after it was
     * planned, and either anchor chosen on the bare plan can end up under a bunk,
     * in a corner the bunks wall off, or in a one-cell passage between the bunks
     * and the building's shell. The colony's people wait in the aisle, and the
     * relief that opens the shelter has to be able to stand a squad on its doorstep.
     */
    private static int[] throughTheDoor(NavigationGrid grid, PointOfInterest hall) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{hall.interiorAnchorX, hall.interiorAnchorY});
        seen.add(key(hall.interiorAnchorX, hall.interiorAnchorY));
        if (seen.add(key(hall.anchorCellX, hall.anchorCellY))) queue.add(new int[]{hall.anchorCellX, hall.anchorCellY});
        int[] best = null;
        int[] doorstep = null;
        int bestDistance = Integer.MAX_VALUE;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            boolean in = c[0] > hall.left && c[0] < hall.right && c[1] > hall.top && c[1] < hall.bottom;
            boolean out = c[0] < hall.left - 1 || c[0] > hall.right + 1 || c[1] < hall.top - 1 || c[1] > hall.bottom + 1;
            boolean stand = grid.isWalkable(c[0], c[1]) && !grid.isDoorway(c[0], c[1]);
            int distance = Math.abs(c[0] - hall.centerX()) + Math.abs(c[1] - hall.centerY());
            if (in && stand && distance < bestDistance) {
                best = c;
                bestDistance = distance;
            }
            // Searched outward, so the first open outside cell is the nearest one through a door.
            if (out && stand && doorstep == null && roomToStand(grid, c[0], c[1])) doorstep = c;
            for (int[] d : STEPS) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                // The hall's bounds are its floor; its walls and door stand one cell out.
                boolean near = nx >= hall.left - DOORSTEP_REACH && nx <= hall.right + DOORSTEP_REACH
                        && ny >= hall.top - DOORSTEP_REACH && ny <= hall.bottom + DOORSTEP_REACH;
                if (!near || !grid.isWalkable(nx, ny) || !seen.add(key(nx, ny))) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        if (best == null || doorstep == null) return null;
        return new int[]{best[0], best[1], doorstep[0], doorstep[1]};
    }

    /** Whether {@code (x, y)} and every cell round it can be walked: room for a squad to stand. */
    private static boolean roomToStand(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!grid.inBounds(x + dx, y + dy) || !grid.isWalkable(x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    /** Plans the dressing of the wall round {@code shelter}; null when the shelter stands in no closed wall. */
    static Plan plan(BattleSimulation sim, PointOfInterest shelter, CivilianEvacuationPlacement placement) {
        return plan(sim, shelter.centerX(), shelter.centerY(), placement.shelterApproachX, placement.shelterApproachY,
                placement.liftX, placement.liftY, WALL_REACH);
    }

    /**
     * Plans the dressing of the perimeter wall within {@code wallReach} of
     * {@code (sx, sy)}: the yard is what {@code (insideX, insideY)} reaches
     * without crossing it, and the main gate is the one nearest
     * {@code (towardX, towardY)}. Null when no closed wall stands there.
     */
    static Plan plan(BattleSimulation sim, int sx, int sy, int insideX, int insideY, int towardX, int towardY,
                     int wallReach) {
        return plan(sim.getGrid(), sim.getTopology(), sim.getDoodads(), sx, sy, insideX, insideY, towardX, towardY,
                wallReach, 0);
    }

    /**
     * Where the gun towers of the perimeter wall within {@code reach} of {@code (sx, sy)} will stand when the wall is
     * dressed, read off a generated map before any battle stands on it: the same plan {@link #dress} lays. Empty
     * when no closed wall stands there.
     */
    public static List<int[]> gunTowers(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, int sx,
                                        int sy, int insideX, int insideY, int towardX, int towardY, int reach) {
        Plan plan = plan(grid, topology, doodads, sx, sy, insideX, insideY, towardX, towardY, reach, 0);
        return plan == null ? List.of() : List.copyOf(plan.guns());
    }

    /**
     * @param front 0 for the colony's dressing; +1 (north, high y) or -1 (south) for a wall that faces a front: a gun
     *              on each corner tower behind slits, and the run facing the front slit every other panel
     */
    private static Plan plan(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, int sx, int sy,
                             int insideX, int insideY, int towardX, int towardY, int wallReach, int front) {
        Set<Long> wall = new HashSet<>();
        for (int y = Math.max(0, sy - wallReach); y <= Math.min(grid.getHeight() - 1, sy + wallReach); y++) {
            for (int x = Math.max(0, sx - wallReach); x <= Math.min(grid.getWidth() - 1, sx + wallReach); x++) {
                if (topology.isWall(x, y) && InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y))) {
                    wall.add(key(x, y));
                }
            }
        }
        if (wall.isEmpty()) return null;
        int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = -1, b = -1;
        for (long k : wall) {
            int x = x(k), y = y(k);
            l = Math.min(l, x); t = Math.min(t, y); r = Math.max(r, x); b = Math.max(b, y);
        }

        // Gates: open cells on the line of the wall, with wall either side along it.
        Set<Long> gate = new HashSet<>();
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                if (wall.contains(key(x, y)) || !grid.isWalkable(x, y)) continue;
                if (spans(wall, grid, topology, x, y, 1, 0) || spans(wall, grid, topology, x, y, 0, 1)) gate.add(key(x, y));
            }
        }
        // Inside: what the shelter door reaches without crossing the wall or a gate.
        Set<Long> inside = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{insideX, insideY});
        inside.add(key(insideX, insideY));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            // Past the wall's own extent is out of the compound: a wall with a hole in it.
            if (c[0] < l - LEAK || c[0] > r + LEAK || c[1] < t - LEAK || c[1] > b + LEAK) return null;
            for (int[] d : STEPS) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                long k = key(nx, ny);
                if (!grid.isWalkable(nx, ny) || wall.contains(k) || gate.contains(k) || !inside.add(k)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        // A neighbour's building may close a side, so the ground is what the wall and the yard cover.
        for (long k : inside) {
            l = Math.min(l, x(k) - 1); t = Math.min(t, y(k) - 1); r = Math.max(r, x(k) + 1); b = Math.max(b, y(k) + 1);
        }
        List<List<int[]>> gates = groups(gate, inside);
        if (gates.isEmpty()) return null;
        Set<Long> dressed = dressedCells(doodads, l - 2, t - 2, r + 2, b + 2);
        Open open = (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && topology.getBuildingId(x, y) == 0 && !topology.isFixture(x, y) && !topology.isWall(x, y)
                && !dressed.contains(key(x, y)) && !gate.contains(key(x, y));

        // A building against the inside of the wall: its walls and floor, not its doors, and not a gate.
        Open standing = (x, y) -> grid.inBounds(x, y) && !grid.isDoorway(x, y) && !gate.contains(key(x, y))
                && (topology.getBuildingId(x, y) != 0 || wallAt(topology, x, y));
        Map<Long, Kind> cells = new LinkedHashMap<>();
        Set<Long> keepOpen = new HashSet<>();
        List<int[]> guns = new ArrayList<>();
        List<NightLighting.Lamp> lamps = new ArrayList<>();
        int[] main = null;
        int mainDistance = Integer.MAX_VALUE;
        for (List<int[]> crossing : gates) {
            int[] first = crossing.get(0);
            int[] in = inward(first, inside);
            if (in == null) continue;
            int distance = Math.abs(first[0] - towardX) + Math.abs(first[1] - towardY);
            if (distance < mainDistance) {
                main = first;
                mainDistance = distance;
            }
            for (int[] cell : crossing) {
                for (int depth = -3; depth <= 3; depth++) keepOpen.add(key(cell[0] + in[0] * depth, cell[1] + in[1] * depth));
            }
            gunTower(crossing, in, wall, inside, open, standing, cells, guns, lamps);
        }
        if (main == null) return null;
        // A compound with fewer gates than it keeps, or a gate with a building against one flank,
        // still mounts its guns: a second tower on a gate's other flank, the gatehouse a single way in has.
        for (List<int[]> crossing : gates) {
            if (guns.size() >= GATES) break;
            int[] in = inward(crossing.get(0), inside);
            if (in != null) gunTower(crossing, in, wall, inside, open, standing, cells, guns, lamps);
        }

        // A squat tower on each outer corner of the wall, the one furthest out each way; facing a front, a gun on it.
        List<int[]> corners = new ArrayList<>();
        for (int[] diagonal : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            long best = -1;
            int bestReach = Integer.MIN_VALUE;
            for (long k : wall) {
                int x = x(k), y = y(k);
                if (!wall.contains(key(x - diagonal[0], y)) || !wall.contains(key(x, y - diagonal[1]))) continue;
                // The corner's inner diagonal is the yard, or a building standing in the corner.
                int ix = x - diagonal[0], iy = y - diagonal[1];
                if (!inside.contains(key(ix, iy)) && (grid.isWalkable(ix, iy) || wall.contains(key(ix, iy)))) continue;
                int reach = x * diagonal[0] + y * diagonal[1];
                if (reach > bestReach) {
                    bestReach = reach;
                    best = k;
                }
            }
            if (best < 0) continue;
            int cx = x(best), cy = y(best);
            boolean armed = front != 0 && !cells.containsKey(best);
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int x = cx - 1; x <= cx + 1; x++) {
                    long k = key(x, y);
                    if (inside.contains(k) || wall.contains(k) || cells.containsKey(k) || !open.at(x, y)) continue;
                    // An armed tower is slit all round, so its gun sees out over the ground at the corner.
                    cells.put(k, armed ? Kind.SLIT : Kind.WALL);
                }
            }
            if (armed) {
                cells.put(best, Kind.GUN);
                corners.add(new int[]{cx, cy});
            }
            int lx = cx - diagonal[0] * 3, ly = cy - diagonal[1] * 3;
            if (open.at(lx, ly) && inside.contains(key(lx, ly))) lamps.add(floodlight(lx, ly, diagonal[0], diagonal[1]));
        }

        // Firing slits along the straight runs, a panel in three.
        Set<Long> frontSlits = new HashSet<>();
        for (long k : wall) {
            int x = x(k), y = y(k);
            if (cells.containsKey(k) || near(gate, x, y, 3)) continue;
            boolean horizontal = wall.contains(key(x - 1, y)) && wall.contains(key(x + 1, y));
            boolean vertical = wall.contains(key(x, y - 1)) && wall.contains(key(x, y + 1));
            if (horizontal == vertical) continue;
            // The run facing the front is slit every other panel (the yard behind it, open ground before it): it is
            // where the swarm comes at the wall, and a panel in three seated a wall team's worth of rifles there.
            boolean facesFront = horizontal && front != 0 && inside.contains(key(x, y - front))
                    && !inside.contains(key(x, y + front));
            if (near(cells, x, y, 2, facesFront ? frontSlits : Set.of())) continue;
            if ((horizontal ? x : y) % (facesFront ? 2 : 3) != 1) continue;
            int px = horizontal ? 0 : 1, py = horizontal ? 1 : 0;
            boolean faces = inside.contains(key(x + px, y + py)) && grid.isWalkable(x - px, y - py)
                    || inside.contains(key(x - px, y - py)) && grid.isWalkable(x + px, y + py);
            if (faces) {
                cells.put(k, Kind.SLIT);
                if (facesFront) frontSlits.add(k);
            }
        }

        // Wall posts: the yard cell behind each slit. A wall is its own cover, so nothing is
        // piled against it; the wall team stands at the slits and shoots through them. A gun tower's
        // inner row is slit too and is still open ground until laid: a team seated there stood in
        // the wall with no way out (urban Line frozen 6, the switch line's garrison).
        List<RedoubtWorks.Bag> posts = new ArrayList<>();
        for (Map.Entry<Long, Kind> e : cells.entrySet()) {
            if (e.getValue() != Kind.SLIT) continue;
            for (int[] d : STEPS) {
                int x = x(e.getKey()) + d[0], y = y(e.getKey()) + d[1];
                long k = key(x, y);
                // A front slit may open into a room against the wall: its floor behind the slit is a post too.
                boolean post = open.at(x, y) || frontSlits.contains(e.getKey()) && grid.isWalkable(x, y)
                        && !grid.isDoorway(x, y) && !topology.isFixture(x, y) && !topology.isWall(x, y)
                        && !dressed.contains(k) && !gate.contains(k);
                if (!inside.contains(k) || keepOpen.contains(k) || cells.containsKey(k) || !post) continue;
                if (posts.stream().noneMatch(p -> p.x() == x && p.y() == y)) posts.add(new RedoubtWorks.Bag(x, y, null));
            }
        }
        if (posts.isEmpty()) return null;
        return new Plan(l, t, r, b, cells, guns, posts, lamps, main[0], main[1], List.copyOf(corners));
    }

    /**
     * A walled place dressed as the compound's wall is: its ground, its gun
     * towers' centres (seated by the caller), the wall posts behind its slits
     * and its main gate.
     */
    public record Dressed(int left, int top, int right, int bottom, List<int[]> guns, List<int[]> posts,
                          int gateX, int gateY, List<int[]> corners) { }

    /**
     * Dresses the perimeter wall round {@code (sx, sy)} on the live map, the
     * way the militia dress the compound's: towers, a gun tower beside each
     * gate, slits along the runs and floodlights ({@code cordon-nouns.md},
     * Field redoubt). Null, having laid nothing, when no closed wall stands
     * within {@code reach}.
     */
    public static Dressed dress(BattleSimulation sim, int sx, int sy, int insideX, int insideY,
                                int towardX, int towardY, int reach) {
        return dress(sim, sx, sy, insideX, insideY, towardX, towardY, reach, 0);
    }

    /**
     * As {@link #dress(BattleSimulation, int, int, int, int, int, int, int)}, for a wall that faces a front
     * ({@code cordon-nouns.md}, Field redoubt): a gun cell on each corner tower, slit all round
     * ({@link Dressed#corners()}, seated by the caller as the gun towers are), and the run facing {@code front} slit
     * every other panel rather than one in three. The gun towers are the same cells either way, so
     * {@link #gunTowers} reads them off the map without the front.
     *
     * @param front +1 where the front is north (high y), -1 where it is south, 0 for the colony's plain dressing
     */
    public static Dressed dress(BattleSimulation sim, int sx, int sy, int insideX, int insideY,
                                int towardX, int towardY, int reach, int front) {
        Plan plan = plan(sim.getGrid(), sim.getTopology(), sim.getDoodads(), sx, sy, insideX, insideY, towardX,
                towardY, reach, front);
        if (plan == null) return null;
        lay(sim, plan);
        List<int[]> posts = new ArrayList<>();
        for (RedoubtWorks.Bag post : plan.posts()) posts.add(new int[]{post.x(), post.y()});
        return new Dressed(plan.left(), plan.top(), plan.right(), plan.bottom(), List.copyOf(plan.guns()),
                List.copyOf(posts), plan.gateX(), plan.gateY(), plan.corners());
    }

    /**
     * A gun tower on one flank of a gate: its gun on the line of the wall behind slits, a floodlight
     * inside the other flank. Beside the gate where it can be, else a step or two further along the
     * wall, past a building that stands against it; none when neither flank has room within
     * {@link #TOWER_REACH}. A flank already holding a tower has none to spare, so a second call puts
     * the gate's second tower on its other flank.
     */
    private static void gunTower(List<int[]> crossing, int[] in, Set<Long> wall, Set<Long> inside, Open open,
                                 Open standing, Map<Long, Kind> cells, List<int[]> guns,
                                 List<NightLighting.Lamp> lamps) {
        int ax = in[1] != 0 ? 1 : 0, ay = in[0] != 0 ? 1 : 0;
        int[] first = crossing.get(0), last = crossing.get(crossing.size() - 1);
        int[][] flanks = new int[2 * (TOWER_REACH - 1)][];
        for (int d = 2, f = 0; d <= TOWER_REACH; d++) {
            flanks[f++] = new int[]{first[0] - ax * d, first[1] - ay * d};
            flanks[f++] = new int[]{last[0] + ax * d, last[1] + ay * d};
        }
        // Free ground inside the wall first; then past a building standing against it, which keeps its walls.
        for (int pass = 0; pass < 2 * flanks.length; pass++) {
            int f = pass % flanks.length;
            boolean against = pass >= flanks.length;
            int gx = flanks[f][0], gy = flanks[f][1];
            if (!wall.contains(key(gx, gy))) continue;
            // The line of the wall and the row inside it must be free; the row outside is built
            // only where the ground is open, since a neighbour may stand against the wall.
            boolean fits = true;
            for (int dy = -1; dy <= 1 && fits; dy++) {
                for (int dx = -1; dx <= 1 && fits; dx++) {
                    if (dx * in[0] + dy * in[1] < 0) continue;
                    long k = key(gx + dx, gy + dy);
                    boolean line = dx * in[0] + dy * in[1] == 0;
                    fits = !cells.containsKey(k) && (wall.contains(k) || open.at(gx + dx, gy + dy)
                            || against && !line && standing.at(gx + dx, gy + dy));
                }
            }
            if (!fits) continue;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    long k = key(gx + dx, gy + dy);
                    boolean outer = dx * in[0] + dy * in[1] < 0;
                    boolean inner = dx * in[0] + dy * in[1] > 0;
                    if ((outer || inner) && (cells.containsKey(k) || !(wall.contains(k) || open.at(gx + dx, gy + dy)))) continue;
                    cells.put(k, dx == 0 && dy == 0 ? Kind.GUN : Kind.SLIT);
                }
            }
            guns.add(new int[]{gx, gy});
            int[] other = flanks[(f & 1) ^ 1];
            int lx = other[0] + in[0] * 2, ly = other[1] + in[1] * 2;
            boolean lit = lamps.stream().anyMatch(l -> (int) l.x() == lx && (int) l.y() == ly);
            if (!lit && open.at(lx, ly) && inside.contains(key(lx, ly))) {
                lamps.add(floodlight(lx, ly, -in[0], -in[1]));
            }
            return;
        }
    }

    static NightLighting.Lamp floodlight(int x, int y, int outX, int outY) {
        float bearing = (float) Math.toDegrees(Math.atan2(outY, outX));
        return new NightLighting.Lamp(x + 0.5f, y + 0.5f, 9f, 1f, 0.93f, 0.78f, LIGHT_CIRCUIT,
                NightLighting.Fixture.TRIPOD_FLOODLIGHT, bearing);
    }

    /**
     * Dresses the wall on the live map before the first tick: towers are the
     * wall's own panels, slits are panels that can be seen and shot through, a
     * gun tower's centre is opened for its gun. Guns are seated by the caller
     * on {@link Plan#guns()}.
     */
    static void lay(BattleSimulation sim, Plan plan) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int surface = topology.surfaceIndex(InstallationWalls.PERIMETER);
        FortifiedWorks.mark(grid, plan.left(), plan.top(), plan.right(), plan.bottom());
        for (Map.Entry<Long, Kind> e : plan.cells().entrySet()) {
            int x = x(e.getKey()), y = y(e.getKey());
            if (e.getValue() == Kind.GUN) {
                grid.setWalkable(x, y, true);
                grid.setSeeThrough(x, y, true);
                grid.setWallHp(x, y, 0);
                topology.setWall(x, y, false);
                topology.setWindow(x, y, false);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
                continue;
            }
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, e.getValue() == Kind.SLIT);
            grid.setDoorway(x, y, false);
            grid.setWallHp(x, y, WALL_HP);
            topology.setWall(x, y, true);
            topology.setWindow(x, y, e.getValue() == Kind.SLIT);
            topology.setSurface(x, y, surface);
            topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
        }
        for (long k : plan.cells().keySet()) {
            int cx = x(k), cy = y(k);
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int x = cx - 1; x <= cx + 1; x++) {
                    if (!grid.inBounds(x, y)) continue;
                    if (topology.isWall(x, y)) {
                        int mask = 0;
                        if (!wallAt(topology, x, y + 1)) mask |= CellTopology.WALL_DIR_N;
                        if (!wallAt(topology, x, y - 1)) mask |= CellTopology.WALL_DIR_S;
                        if (!wallAt(topology, x + 1, y)) mask |= CellTopology.WALL_DIR_E;
                        if (!wallAt(topology, x - 1, y)) mask |= CellTopology.WALL_DIR_W;
                        topology.setWallDirMask(x, y, mask);
                    }
                    grid.recomputeCoverAt(x, y);
                }
            }
        }
        addLamps(sim, plan.lamps());
    }

    /** Adds floodlights to the map's night lighting, on the civil-defence circuit, which is powered. */
    static void addLamps(BattleSimulation sim, List<NightLighting.Lamp> added) {
        CellTopology topology = sim.getTopology();
        NightLighting night = topology.nightLighting();
        List<NightLighting.Lamp> lamps = new ArrayList<>(night == null ? List.of() : night.lamps());
        lamps.addAll(added);
        Set<String> powered = new HashSet<>(night == null ? Set.of() : night.poweredCircuits());
        powered.add(LIGHT_CIRCUIT);
        topology.setNightLighting(new NightLighting(night == null ? 0f : night.darkness(), lamps, powered));
    }

    /**
     * Whether an open cell lies across a gap in the wall along one axis, the gap no wider than a gate.
     * Each side of the gap is the wall, or a building standing on the line of the wall whose own wall
     * runs on along it to the wall: a building packed against the outline closes the gate's side.
     */
    private static boolean spans(Set<Long> wall, NavigationGrid grid, CellTopology topology, int x, int y,
                                 int dx, int dy) {
        if (wall.contains(key(x + dy, y + dx)) || wall.contains(key(x - dy, y - dx))) return false;
        int width = 1;
        int a = 1;
        while (grid.inBounds(x - dx * a, y - dy * a) && grid.isWalkable(x - dx * a, y - dy * a)) {
            if (++width > MAX_GATE) return false;
            a++;
        }
        int b = 1;
        while (grid.inBounds(x + dx * b, y + dy * b) && grid.isWalkable(x + dx * b, y + dy * b)) {
            if (++width > MAX_GATE) return false;
            b++;
        }
        return closes(wall, topology, x - dx * a, y - dy * a, -dx, -dy)
                && closes(wall, topology, x + dx * b, y + dy * b, dx, dy);
    }

    /**
     * Whether {@code (x, y)} is the wall, or standing wall from it runs along {@code (dx, dy)} into
     * the wall where the wall runs on the same way: a building on the line of the wall, not one
     * standing beside the wall along it.
     */
    private static boolean closes(Set<Long> wall, CellTopology topology, int x, int y, int dx, int dy) {
        for (int k = 0; k <= MAX_GATE; k++) {
            int cx = x + dx * k, cy = y + dy * k;
            if (wall.contains(key(cx, cy))) return k == 0 || wall.contains(key(cx + dx, cy + dy));
            if (!wallAt(topology, cx, cy)) return false;
        }
        return false;
    }

    /** Gate cells grouped into crossings that touch the inside, each in order along the wall. */
    private static List<List<int[]>> groups(Set<Long> gate, Set<Long> inside) {
        List<List<int[]>> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : gate) {
            if (!seen.add(start)) continue;
            List<int[]> group = new ArrayList<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                long k = queue.poll();
                group.add(new int[]{x(k), y(k)});
                for (int[] d : STEPS) {
                    long n = key(x(k) + d[0], y(k) + d[1]);
                    if (gate.contains(n) && seen.add(n)) queue.add(n);
                }
            }
            if (group.stream().noneMatch(c -> inward(c, inside) != null)) continue;
            group.sort((p, q) -> p[0] != q[0] ? Integer.compare(p[0], q[0]) : Integer.compare(p[1], q[1]));
            out.add(group);
        }
        out.sort((p, q) -> p.get(0)[0] != q.get(0)[0] ? Integer.compare(p.get(0)[0], q.get(0)[0])
                : Integer.compare(p.get(0)[1], q.get(0)[1]));
        return out;
    }

    /** The step from a gate cell into the compound, or null. */
    private static int[] inward(int[] cell, Set<Long> inside) {
        for (int[] d : STEPS) if (inside.contains(key(cell[0] + d[0], cell[1] + d[1]))) return d;
        return null;
    }

    /** As {@link #near(Map, int, int, int)}, not counting the cells in {@code ignored}. */
    private static boolean near(Map<Long, Kind> cells, int x, int y, int reach, Set<Long> ignored) {
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                long k = key(x + dx, y + dy);
                if (cells.containsKey(k) && !ignored.contains(k)) return true;
            }
        }
        return false;
    }

    private static boolean near(Map<Long, Kind> cells, int x, int y, int reach) {
        for (int dy = -reach; dy <= reach; dy++)
            for (int dx = -reach; dx <= reach; dx++) if (cells.containsKey(key(x + dx, y + dy))) return true;
        return false;
    }

    private static boolean near(Set<Long> cells, int x, int y, int reach) {
        for (int dy = -reach; dy <= reach; dy++)
            for (int dx = -reach; dx <= reach; dx++) if (cells.contains(key(x + dx, y + dy))) return true;
        return false;
    }

    private interface Open {
        boolean at(int x, int y);
    }

    private static boolean wallAt(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    private static Set<Long> dressedCells(List<Doodad> doodads, int l, int t, int r, int b) {
        Set<Long> cells = new HashSet<>();
        for (Doodad d : doodads) {
            if (d.cellX > r || d.cellY > b || d.cellX + d.footprintCellsX <= l || d.cellY + d.footprintCellsY <= t) continue;
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++)
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) cells.add(key(x, y));
        }
        return cells;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int x(long k) {
        return (int) (k >> 32);
    }

    private static int y(long k) {
        return (int) k;
    }
}
