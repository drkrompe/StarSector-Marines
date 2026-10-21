package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The colony's civil-defence compound round the shelter: the main redoubt
 * ({@code colony-breach-nouns.md}, Redoubt). A frontier settlement that always
 * knew something might come out of the wilderness put it up when the colony
 * was laid out, so it is a proper work, but a civil one: thinner walls, fewer
 * guns, no layers.
 *
 * <ul>
 *   <li>A <b>curtain wall</b> of prefab concrete panels a few cells out from the
 *       shelter, with firing slits along its straight runs. It is ordinary wall:
 *       impassable, opaque except at its slits, and light enough at
 *       {@link #WALL_HP} that a Brute, a charge or a shell breaches it.</li>
 *   <li>A squat <b>tower</b> at each corner.</li>
 *   <li>A <b>gatehouse</b> on the side toward the lift: a gate two cells wide
 *       between two gun towers, each a heavy machine gun behind slitted walls.
 *       Those two guns are the compound's whole armament.</li>
 *   <li>A <b>firing step</b> of sandbag behind the wall, one course all the way
 *       round, which the militia's wall team mans.</li>
 *   <li><b>Floodlights</b> on tripods inside each corner and at the gate,
 *       facing out.</li>
 * </ul>
 *
 * <p>Where another building stands on the line of the wall it closes that
 * stretch itself. Planning is read-only and refuses rather than half-builds:
 * no gate, or a shelter door that no longer reaches the open ground, is no
 * compound.
 */
final class CivilDefenceCompound {

    /** Wall left in a civil panel: a colony wall's, inside what a Brute can break. */
    static final int WALL_HP = 110;
    static final int GATE_WIDTH = 2;
    /** Wall margins tried round the shelter, in order of preference. */
    private static final int[] MARGINS = {6, 7, 5, 8};
    private static final String WALL_SURFACE = "installation.dark-concrete-wall";
    private static final String LIGHT_CIRCUIT = "civil-defence";

    enum Kind { WALL, SLIT, GUN }

    /**
     * A planned compound.
     *
     * @param cells     what stands on each cell of the works, keyed by packed cell
     * @param guns      the gun towers' centre cells
     * @param gateX     the gate's first cell, on the line of the wall
     * @param gateY     the gate's first cell, on the line of the wall
     * @param inwardX   the step from the gate into the compound
     * @param inwardY   the step from the gate into the compound
     */
    record Plan(int left, int top, int right, int bottom, Map<Long, Kind> cells, List<int[]> guns,
                List<RedoubtWorks.Bag> step, List<NightLighting.Lamp> lamps,
                int gateX, int gateY, int inwardX, int inwardY) {

        boolean inside(int x, int y) {
            return x > left && x < right && y > top && y < bottom;
        }
    }

    private CivilDefenceCompound() {}

    /** Whether the shelter stands in a civil-defence compound. {@code -Dbattle.colonyBreach.civilDefence=false} is the control. */
    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.civilDefence", "true"));
    }

    /** Plans the compound round {@code shelter}, gate toward the lift; null when none fits. */
    static Plan plan(BattleSimulation sim, PointOfInterest shelter, CivilianEvacuationPlacement placement,
                     List<LandingPad> pads) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad("doodad.fort-sandbag-n") == null) return null;
        Plan best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int margin : MARGINS) {
            Plan plan = planAt(sim, registry, shelter, placement, pads, margin);
            if (plan == null) continue;
            // Prefer the line that cuts fewest other buildings: each one is a stretch the wall does not close.
            int cost = crossings(sim, plan, shelter);
            if (cost < bestCost) {
                best = plan;
                bestCost = cost;
            }
        }
        return best;
    }

    private static Plan planAt(BattleSimulation sim, TileRegistry registry, PointOfInterest shelter,
                               CivilianEvacuationPlacement placement, List<LandingPad> pads, int margin) {
        NavigationGrid grid = sim.getGrid();
        int l = shelter.left - margin, t = shelter.top - margin, r = shelter.right + margin, b = shelter.bottom + margin;
        if (l < 3 || t < 3 || r >= grid.getWidth() - 3 || b >= grid.getHeight() - 3) return null;
        int shelterBuilding = sim.getTopology().getBuildingId(shelter.interiorAnchorX, shelter.interiorAnchorY);
        Set<Long> dressed = dressedCells(sim, l - 2, t - 2, r + 2, b + 2);
        Open open = (x, y) -> openGround(sim, dressed, pads, placement, shelterBuilding, x, y);

        int dx = placement.liftX - shelter.centerX(), dy = placement.liftY - shelter.centerY();
        int[] toward = Math.abs(dx) >= Math.abs(dy) ? new int[]{Integer.signum(dx), 0} : new int[]{0, Integer.signum(dy)};
        if (toward[0] == 0 && toward[1] == 0) toward[0] = 1;
        int[][] sides = {toward, {toward[1], toward[0]}, {-toward[1], -toward[0]}, {-toward[0], -toward[1]}};
        for (int[] side : sides) {
            int[] gate = gateOn(grid, open, l, t, r, b, side, placement.shelterApproachX, placement.shelterApproachY);
            if (gate == null) continue;
            Plan plan = build(sim, registry, open, l, t, r, b, side, gate);
            if (reaches(grid, plan, placement.shelterApproachX, placement.shelterApproachY)) return plan;
        }
        return null;
    }

    /** A gate on one side: two open cells with open ground either side, and room for a gun tower either flank. */
    private static int[] gateOn(NavigationGrid grid, Open open, int l, int t, int r, int b, int[] side,
                                int doorX, int doorY) {
        boolean vertical = side[0] != 0;
        int fixed = vertical ? (side[0] > 0 ? r : l) : (side[1] > 0 ? b : t);
        int min = (vertical ? t : l) + 5, max = (vertical ? b : r) - 4 - GATE_WIDTH;
        if (max < min) return null;
        int preferred = Math.max(min, Math.min(max, vertical ? doorY : doorX));
        for (int offset = 0; offset <= max - min; offset++) {
            for (int sign : new int[]{1, -1}) {
                int start = preferred + sign * offset;
                if (start < min || start > max) continue;
                boolean ok = true;
                for (int along = start - 3; along <= start + GATE_WIDTH + 2 && ok; along++) {
                    for (int depth = -1; depth <= 1 && ok; depth++) {
                        int x = vertical ? fixed + side[0] * depth : along, y = vertical ? along : fixed + side[1] * depth;
                        ok = open.at(x, y);
                    }
                }
                for (int k = 0; k < GATE_WIDTH && ok; k++) {
                    int x = vertical ? fixed : start + k, y = vertical ? start + k : fixed;
                    ok = grid.isWalkable(x + side[0] * 2, y + side[1] * 2) && grid.isWalkable(x - side[0] * 2, y - side[1] * 2);
                }
                if (ok) return vertical ? new int[]{fixed, start} : new int[]{start, fixed};
            }
        }
        return null;
    }

    private static Plan build(BattleSimulation sim, TileRegistry registry, Open open,
                              int l, int t, int r, int b, int[] side, int[] gate) {
        boolean vertical = side[0] != 0;
        Map<Long, Kind> cells = new LinkedHashMap<>();
        Set<Long> keepOpen = new HashSet<>();
        for (int k = 0; k < GATE_WIDTH; k++) {
            int gx = vertical ? gate[0] : gate[0] + k, gy = vertical ? gate[1] + k : gate[1];
            for (int depth = -2; depth <= 3; depth++) keepOpen.add(key(gx - side[0] * depth, gy - side[1] * depth));
        }

        // The gatehouse: a gun tower either side of the gate, its gun behind slits on every face.
        List<int[]> guns = new ArrayList<>();
        for (int along : new int[]{-2, GATE_WIDTH + 1}) {
            int cx = vertical ? gate[0] : gate[0] + along, cy = vertical ? gate[1] + along : gate[1];
            guns.add(new int[]{cx, cy});
            for (int y = cy - 1; y <= cy + 1; y++)
                for (int x = cx - 1; x <= cx + 1; x++) cells.put(key(x, y), x == cx && y == cy ? Kind.GUN : Kind.SLIT);
        }
        // A squat tower at each corner, wherever its ground is open.
        for (int[] corner : new int[][]{{l, t}, {r, t}, {l, b}, {r, b}}) {
            for (int y = corner[1] - 1; y <= corner[1] + 1; y++)
                for (int x = corner[0] - 1; x <= corner[0] + 1; x++)
                    if (!cells.containsKey(key(x, y)) && open.at(x, y)) cells.put(key(x, y), Kind.WALL);
        }
        // The curtain, with a slit every third panel on its straight runs.
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                if (x != l && x != r && y != t && y != b) continue;
                long k = key(x, y);
                if (cells.containsKey(k) || keepOpen.contains(k) || !open.at(x, y)) continue;
                boolean nearCorner = (Math.abs(x - l) <= 2 || Math.abs(x - r) <= 2) && (Math.abs(y - t) <= 2 || Math.abs(y - b) <= 2);
                int[] in = inward(x, y, l, t, r, b);
                boolean slit = !nearCorner && (x == l || x == r ? y : x) % 3 == 1
                        && sim.getGrid().isWalkable(x + in[0], y + in[1]) && sim.getGrid().isWalkable(x - in[0], y - in[1]);
                cells.put(k, slit ? Kind.SLIT : Kind.WALL);
            }
        }

        // The firing step: a course of sandbag along the inside of the wall, bags facing out.
        List<RedoubtWorks.Bag> step = new ArrayList<>();
        int sl = l + 1, st = t + 1, sr = r - 1, sb = b - 1;
        for (int y = st; y <= sb; y++) {
            for (int x = sl; x <= sr; x++) {
                if (x != sl && x != sr && y != st && y != sb) continue;
                long k = key(x, y);
                if (cells.containsKey(k) || keepOpen.contains(k) || !open.at(x, y)) continue;
                int ox = x == sl ? -1 : x == sr ? 1 : 0;
                int oy = y == st ? -1 : y == sb ? 1 : 0;
                String name = ox != 0 && oy != 0 ? "corner-" + letter(0, oy) + letter(ox, 0) : String.valueOf(letter(ox, oy));
                if (registry.doodad("doodad.fort-sandbag-" + name) == null) continue;
                step.add(new RedoubtWorks.Bag(x, y, "doodad.fort-sandbag-" + name));
            }
        }

        // Floodlights on tripods inside each corner, and one either side of the gate, all facing out.
        List<NightLighting.Lamp> lamps = new ArrayList<>();
        for (int[] corner : new int[][]{{l + 3, t + 3, -1, -1}, {r - 3, t + 3, 1, -1}, {l + 3, b - 3, -1, 1}, {r - 3, b - 3, 1, 1}}) {
            lamps.add(floodlight(corner[0], corner[1], corner[2], corner[3]));
        }
        for (int[] gun : guns) {
            lamps.add(floodlight(gun[0] - side[0] * 3, gun[1] - side[1] * 3, side[0], side[1]));
        }
        return new Plan(l, t, r, b, cells, guns, step, lamps, gate[0], gate[1], -side[0], -side[1]);
    }

    private static NightLighting.Lamp floodlight(int x, int y, int outX, int outY) {
        float bearing = (float) Math.toDegrees(Math.atan2(outY, outX));
        return new NightLighting.Lamp(x + 0.5f, y + 0.5f, 9f, 1f, 0.93f, 0.78f, LIGHT_CIRCUIT,
                NightLighting.Fixture.TRIPOD_FLOODLIGHT, bearing);
    }

    /**
     * Raises the compound on the live map before the first tick, the way a
     * generator raises a walled bed: panels are impassable, opaque wall at
     * {@link #WALL_HP}, slits see-through, every face that meets open ground
     * shown. Guns are seated by the caller on {@link Plan#guns()}.
     */
    static void lay(BattleSimulation sim, Plan plan) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int surface = topology.surfaceIndex(WALL_SURFACE);
        for (Map.Entry<Long, Kind> e : plan.cells().entrySet()) {
            int x = (int) (e.getKey() >> 32), y = (int) (long) e.getKey();
            topology.setGroundKind(x, y, GroundKind.STONE);
            if (e.getValue() == Kind.GUN) continue;
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, e.getValue() == Kind.SLIT);
            grid.setDoorway(x, y, false);
            grid.setWallHp(x, y, WALL_HP);
            topology.setWall(x, y, true);
            topology.setWindow(x, y, e.getValue() == Kind.SLIT);
            topology.setSurface(x, y, surface);
        }
        for (long k : plan.cells().keySet()) {
            int x = (int) (k >> 32), y = (int) k;
            if (!topology.isWall(x, y)) continue;
            int mask = 0;
            if (!wallAt(topology, x, y + 1)) mask |= CellTopology.WALL_DIR_N;
            if (!wallAt(topology, x, y - 1)) mask |= CellTopology.WALL_DIR_S;
            if (!wallAt(topology, x + 1, y)) mask |= CellTopology.WALL_DIR_E;
            if (!wallAt(topology, x - 1, y)) mask |= CellTopology.WALL_DIR_W;
            topology.setWallDirMask(x, y, mask);
            for (int ny = y - 1; ny <= y + 1; ny++)
                for (int nx = x - 1; nx <= x + 1; nx++) if (grid.inBounds(nx, ny)) grid.recomputeCoverAt(nx, ny);
        }
        // The gate: a striped apron through the wall, marked as the way in.
        for (int k = 0; k < GATE_WIDTH; k++) {
            int gx = plan.gateX() + (plan.inwardY() != 0 ? k : 0), gy = plan.gateY() + (plan.inwardX() != 0 ? k : 0);
            grid.setDoorway(gx, gy, true);
            topology.setGroundKind(gx, gy, GroundKind.STRIPED);
        }
        TileRegistry registry = TileRegistry.installed();
        for (RedoubtWorks.Bag bag : plan.step()) {
            DoodadDef def = registry.doodad(bag.doodad());
            grid.setSeeThrough(bag.x(), bag.y(), true);
            topology.setFixture(bag.x(), bag.y(), true);
            sim.addDoodad(new Doodad(bag.x(), bag.y(), def));
        }
        NightLighting night = topology.nightLighting();
        List<NightLighting.Lamp> lamps = new ArrayList<>(night == null ? List.of() : night.lamps());
        lamps.addAll(plan.lamps());
        Set<String> powered = new HashSet<>(night == null ? Set.of() : night.poweredCircuits());
        powered.add(LIGHT_CIRCUIT);
        topology.setNightLighting(new NightLighting(night == null ? 0f : night.darkness(), lamps, powered));
    }

    /** How many cells of the wall's line other buildings stand on. */
    private static int crossings(BattleSimulation sim, Plan plan, PointOfInterest shelter) {
        CellTopology topology = sim.getTopology();
        int shelterBuilding = topology.getBuildingId(shelter.interiorAnchorX, shelter.interiorAnchorY);
        int count = 0;
        for (int y = plan.top(); y <= plan.bottom(); y++) {
            for (int x = plan.left(); x <= plan.right(); x++) {
                if (x != plan.left() && x != plan.right() && y != plan.top() && y != plan.bottom()) continue;
                if (plan.cells().containsKey(key(x, y))) continue;
                int building = topology.getBuildingId(x, y);
                if (building != 0 && building != shelterBuilding) count += 1;
                else if (!sim.getGrid().isWalkable(x, y) || topology.isWall(x, y)) count += 1;
                else count += 3;
            }
        }
        return count;
    }

    private static boolean reaches(NavigationGrid grid, Plan plan, int doorX, int doorY) {
        if (!grid.inBounds(doorX, doorY) || !grid.isWalkable(doorX, doorY)) return false;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{doorX, doorY});
        seen.add(key(doorX, doorY));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (!plan.inside(c[0], c[1])) return !plan.cells().containsKey(key(c[0], c[1]));
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                long k = key(nx, ny);
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || plan.cells().containsKey(k) || !seen.add(k)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return false;
    }

    private interface Open {
        boolean at(int x, int y);
    }

    /** Open ground the works can stand on: walkable, no other building, no door, no prop, off the pads and the lift. */
    private static boolean openGround(BattleSimulation sim, Set<Long> dressed, List<LandingPad> pads,
                                      CivilianEvacuationPlacement placement, int shelterBuilding, int x, int y) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
        int building = topology.getBuildingId(x, y);
        if (building != 0 || topology.isFixture(x, y) || topology.isWall(x, y) || dressed.contains(key(x, y))) return false;
        int lift = CivilianEvacuationPlacement.LIFT_ZONE_RADIUS + 3;
        if (Math.abs(x - placement.liftX) <= lift && Math.abs(y - placement.liftY) <= lift) return false;
        if (pads != null) {
            for (LandingPad pad : pads) {
                if (Math.abs(x - pad.centerX) <= pad.halfWidth + 2 && Math.abs(y - pad.centerY) <= pad.halfHeight + 2) return false;
            }
        }
        return true;
    }

    private static int[] inward(int x, int y, int l, int t, int r, int b) {
        if (x == l) return new int[]{1, 0};
        if (x == r) return new int[]{-1, 0};
        if (y == t) return new int[]{0, 1};
        return new int[]{0, -1};
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

    private static char letter(int dx, int dy) {
        if (dy > 0) return 'n';
        if (dy < 0) return 's';
        return dx > 0 ? 'e' : 'w';
    }

    static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
