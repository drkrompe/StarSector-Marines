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
    private static final String LIGHT_CIRCUIT = "civil-defence";

    enum Kind { WALL, SLIT, GUN }

    /**
     * The dressing planned for one compound.
     *
     * @param cells what the dressing makes of each cell, keyed by packed cell
     * @param guns  the gun towers' centre cells
     * @param gateX the first cell of the gate nearest the lift
     * @param gateY the first cell of the gate nearest the lift
     */
    record Plan(int left, int top, int right, int bottom, Map<Long, Kind> cells, List<int[]> guns,
                List<RedoubtWorks.Bag> posts, List<NightLighting.Lamp> lamps, int gateX, int gateY) {}

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
        int[] inside = throughTheDoor(grid, best);
        if (inside == null) return null;
        PointOfInterest hall = new PointOfInterest(best.kind, best.left, best.top, best.right,
                best.bottom, best.anchorCellX, best.anchorCellY, inside[0], inside[1]);
        // Replaced where it stands, so the militia and the colony's people find the same hall.
        points.set(points.indexOf(best), hall);
        return List.of(hall);
    }

    /**
     * The hall's floor cell nearest its middle that the door reaches. The hall
     * is furnished with bunks, and a stand cell chosen on the bare plan can end
     * up in a corner the bunks wall off; the colony's people wait in the aisle.
     */
    private static int[] throughTheDoor(NavigationGrid grid, PointOfInterest hall) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{hall.anchorCellX, hall.anchorCellY});
        seen.add(key(hall.anchorCellX, hall.anchorCellY));
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            boolean in = c[0] > hall.left && c[0] < hall.right && c[1] > hall.top && c[1] < hall.bottom;
            int distance = Math.abs(c[0] - hall.centerX()) + Math.abs(c[1] - hall.centerY());
            if (in && !grid.isDoorway(c[0], c[1]) && distance < bestDistance) {
                best = c;
                bestDistance = distance;
            }
            for (int[] d : STEPS) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                // The hall's bounds are its floor; its walls and door stand one cell out.
                boolean inside = nx >= hall.left - 1 && nx <= hall.right + 1 && ny >= hall.top - 1 && ny <= hall.bottom + 1;
                if (!inside || !grid.isWalkable(nx, ny) || !seen.add(key(nx, ny))) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return best;
    }

    /** Plans the dressing of the wall round {@code shelter}; null when the shelter stands in no closed wall. */
    static Plan plan(BattleSimulation sim, PointOfInterest shelter, CivilianEvacuationPlacement placement) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        Set<Long> wall = new HashSet<>();
        int sx = shelter.centerX(), sy = shelter.centerY();
        for (int y = Math.max(0, sy - WALL_REACH); y <= Math.min(grid.getHeight() - 1, sy + WALL_REACH); y++) {
            for (int x = Math.max(0, sx - WALL_REACH); x <= Math.min(grid.getWidth() - 1, sx + WALL_REACH); x++) {
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
                if (spans(wall, grid, x, y, 1, 0) || spans(wall, grid, x, y, 0, 1)) gate.add(key(x, y));
            }
        }
        // Inside: what the shelter door reaches without crossing the wall or a gate.
        Set<Long> inside = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{placement.shelterApproachX, placement.shelterApproachY});
        inside.add(key(placement.shelterApproachX, placement.shelterApproachY));
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
        Set<Long> dressed = dressedCells(sim, l - 2, t - 2, r + 2, b + 2);
        Open open = (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && topology.getBuildingId(x, y) == 0 && !topology.isFixture(x, y) && !topology.isWall(x, y)
                && !dressed.contains(key(x, y)) && !gate.contains(key(x, y));

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
            int distance = Math.abs(first[0] - placement.liftX) + Math.abs(first[1] - placement.liftY);
            if (distance < mainDistance) {
                main = first;
                mainDistance = distance;
            }
            for (int[] cell : crossing) {
                for (int depth = -3; depth <= 3; depth++) keepOpen.add(key(cell[0] + in[0] * depth, cell[1] + in[1] * depth));
            }
            gunTower(crossing, in, wall, inside, open, cells, guns, lamps);
        }
        if (main == null) return null;

        // A squat tower on each outer corner of the wall, the one furthest out each way.
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
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int x = cx - 1; x <= cx + 1; x++) {
                    long k = key(x, y);
                    if (inside.contains(k) || wall.contains(k) || cells.containsKey(k) || !open.at(x, y)) continue;
                    cells.put(k, Kind.WALL);
                }
            }
            int lx = cx - diagonal[0] * 3, ly = cy - diagonal[1] * 3;
            if (open.at(lx, ly) && inside.contains(key(lx, ly))) lamps.add(floodlight(lx, ly, diagonal[0], diagonal[1]));
        }

        // Firing slits along the straight runs, a panel in three.
        for (long k : wall) {
            int x = x(k), y = y(k);
            if (cells.containsKey(k) || near(cells, x, y, 2) || near(gate, x, y, 3)) continue;
            boolean horizontal = wall.contains(key(x - 1, y)) && wall.contains(key(x + 1, y));
            boolean vertical = wall.contains(key(x, y - 1)) && wall.contains(key(x, y + 1));
            if (horizontal == vertical || (horizontal ? x : y) % 3 != 1) continue;
            int px = horizontal ? 0 : 1, py = horizontal ? 1 : 0;
            boolean faces = inside.contains(key(x + px, y + py)) && grid.isWalkable(x - px, y - py)
                    || inside.contains(key(x - px, y - py)) && grid.isWalkable(x + px, y + py);
            if (faces) cells.put(k, Kind.SLIT);
        }

        // Wall posts: the yard cell behind each slit. A wall is its own cover, so nothing is
        // piled against it; the wall team stands at the slits and shoots through them.
        List<RedoubtWorks.Bag> posts = new ArrayList<>();
        for (Map.Entry<Long, Kind> e : cells.entrySet()) {
            if (e.getValue() != Kind.SLIT) continue;
            for (int[] d : STEPS) {
                int x = x(e.getKey()) + d[0], y = y(e.getKey()) + d[1];
                long k = key(x, y);
                if (!inside.contains(k) || keepOpen.contains(k) || !open.at(x, y)) continue;
                if (posts.stream().noneMatch(p -> p.x() == x && p.y() == y)) posts.add(new RedoubtWorks.Bag(x, y, null));
            }
        }
        if (posts.isEmpty()) return null;
        return new Plan(l, t, r, b, cells, guns, posts, lamps, main[0], main[1]);
    }

    /** A gun tower on one flank of a gate: its gun on the line of the wall behind slits, a floodlight inside the other flank. */
    private static void gunTower(List<int[]> crossing, int[] in, Set<Long> wall, Set<Long> inside, Open open,
                                 Map<Long, Kind> cells, List<int[]> guns, List<NightLighting.Lamp> lamps) {
        int ax = in[1] != 0 ? 1 : 0, ay = in[0] != 0 ? 1 : 0;
        int[] first = crossing.get(0), last = crossing.get(crossing.size() - 1);
        int[][] flanks = {{first[0] - ax * 2, first[1] - ay * 2}, {last[0] + ax * 2, last[1] + ay * 2}};
        for (int f = 0; f < flanks.length; f++) {
            int gx = flanks[f][0], gy = flanks[f][1];
            if (!wall.contains(key(gx, gy))) continue;
            // The line of the wall and the row inside it must be free; the row outside is built
            // only where the ground is open, since a neighbour may stand against the wall.
            boolean fits = true;
            for (int dy = -1; dy <= 1 && fits; dy++) {
                for (int dx = -1; dx <= 1 && fits; dx++) {
                    if (dx * in[0] + dy * in[1] < 0) continue;
                    long k = key(gx + dx, gy + dy);
                    fits = !cells.containsKey(k) && (wall.contains(k) || open.at(gx + dx, gy + dy));
                }
            }
            if (!fits) continue;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    long k = key(gx + dx, gy + dy);
                    boolean outer = dx * in[0] + dy * in[1] < 0;
                    if (outer && (cells.containsKey(k) || !(wall.contains(k) || open.at(gx + dx, gy + dy)))) continue;
                    cells.put(k, dx == 0 && dy == 0 ? Kind.GUN : Kind.SLIT);
                }
            }
            guns.add(new int[]{gx, gy});
            int[] other = flanks[1 - f];
            int lx = other[0] + in[0] * 2, ly = other[1] + in[1] * 2;
            if (open.at(lx, ly) && inside.contains(key(lx, ly))) {
                lamps.add(floodlight(lx, ly, -in[0], -in[1]));
            }
            return;
        }
    }

    private static NightLighting.Lamp floodlight(int x, int y, int outX, int outY) {
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
        NightLighting night = topology.nightLighting();
        List<NightLighting.Lamp> lamps = new ArrayList<>(night == null ? List.of() : night.lamps());
        lamps.addAll(plan.lamps());
        Set<String> powered = new HashSet<>(night == null ? Set.of() : night.poweredCircuits());
        powered.add(LIGHT_CIRCUIT);
        topology.setNightLighting(new NightLighting(night == null ? 0f : night.darkness(), lamps, powered));
    }

    /** Whether an open cell lies across a gap in the wall along one axis, the gap no wider than a gate. */
    private static boolean spans(Set<Long> wall, NavigationGrid grid, int x, int y, int dx, int dy) {
        if (wall.contains(key(x + dy, y + dx)) || wall.contains(key(x - dy, y - dx))) return false;
        int width = 1;
        int a = 1;
        while (!wall.contains(key(x - dx * a, y - dy * a))) {
            if (!grid.inBounds(x - dx * a, y - dy * a) || !grid.isWalkable(x - dx * a, y - dy * a) || ++width > MAX_GATE) return false;
            a++;
        }
        int b = 1;
        while (!wall.contains(key(x + dx * b, y + dy * b))) {
            if (!grid.inBounds(x + dx * b, y + dy * b) || !grid.isWalkable(x + dx * b, y + dy * b) || ++width > MAX_GATE) return false;
            b++;
        }
        return true;
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

    private static Set<Long> dressedCells(BattleSimulation sim, int l, int t, int r, int b) {
        Set<Long> cells = new HashSet<>();
        for (Doodad d : sim.getDoodads()) {
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
