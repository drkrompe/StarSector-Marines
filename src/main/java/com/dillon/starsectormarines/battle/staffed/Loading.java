package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.StaffedCrew;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.ClearanceComponents;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * One loading: a staffed building's crew carrying its stock out to a truck at its
 * door, and the truck driving off the map once the count is aboard
 * ({@code staffed-buildings.md}, The objective hook).
 *
 * <p>The truck is the settlement's own, an allied hauler: it can be lost, and
 * losing it before it is away loses the loading. The crew are the building's
 * ordinary staff, handed a loop that runs stock to tailgate, and they keep their
 * yield: a fight in reach of them stops the loading until it has gone, which is
 * the whole reason the company stands guard over the yard.
 *
 * <p>Everything is decided in the serial advance, from the map and the roster,
 * so a replay loads the same crates at the same ticks.
 */
public final class Loading {

    /** Where a loading stands. */
    public enum Phase {
        /** The crew are carrying. */
        LOADING,
        /** The count is aboard and the truck is on its way out. */
        DEPARTING,
        /** The truck has left the map with its load. */
        AWAY,
        /** The truck was lost, or nobody is left to load it. */
        LOST
    }

    /** How far from the building's door a truck may stand, in cells. */
    static final int PARK_REACH = 12;
    /** Stands near the door weighed for a way out, nearest first. */
    static final int PARK_CANDIDATES = 6;
    /** Stands weighed lie at least this far apart, in cells, so a bad corner is not tried six times. */
    static final int PARK_SPREAD = 3;
    /** How far from where the road ends the exit may be snapped onto ground a truck can use, in cells. */
    static final int EXIT_SNAP = 6;
    /** Exit candidates tried, nearest first, before the loading says no truck can leave from here. */
    static final int EXIT_TRIES = 12;
    /** A truck this near the map's edge, or the end of its road out, has left the map, in cells. */
    static final float AWAY_MARGIN = 3f;
    /** Places at the tailgate a crate can be set down at once. */
    static final int TAILGATE_PLACES = 2;
    /** Claim groups for the tailgate are scoped above every building's. */
    private static final int TAILGATE_SITE_BASE = 950_000;

    private final StaffedBuildings.Staffed building;
    private final long truck;
    private final float[][] exitRoute;
    private final String tailgateGroup;
    private final int target;
    private boolean atBay;
    private final Set<Long> atTailgate = new HashSet<>();
    private Phase phase = Phase.LOADING;
    private int delivered;
    private int departedTick = -1;

    private Loading(StaffedBuildings.Staffed building, long truck, float[][] exitRoute, String tailgateGroup,
                    int target) {
        this.building = building;
        this.truck = truck;
        this.exitRoute = exitRoute;
        this.tailgateGroup = tailgateGroup;
        this.target = target;
    }

    /**
     * Parks a truck at the building's door, proves it a way off the map, and sets the crew loading
     * it; null where no truck could stand at the door or none could leave from there.
     *
     * @param target crates aboard before the truck leaves
     */
    public static Loading install(BattleSimulation sim, StaffedBuildings.Staffed building, int target) {
        return install(sim, building, target, List.of());
    }

    /**
     * {@link #install(BattleSimulation, StaffedBuildings.Staffed, int)} at the building's own loading bay
     * where a set piece laid one ({@code set-piece-blocks.md}): the truck stands where the yard means it to
     * and nothing is searched. A building with no bay is parked as before.
     */
    public static Loading install(BattleSimulation sim, StaffedBuildings.Staffed building, int target,
                                  List<LoadingBay> bays) {
        if (building == null || building.crew().isEmpty() || target <= 0) return null;
        NavigationGrid grid = sim.getGrid();
        VehicleClearance clearance = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
        TerrainCostField cost = TerrainCostField.from(sim.getTopology());
        ClearanceComponents components = ClearanceComponents.of(grid, clearance);
        Set<Integer> leaving = edgeComponents(clearance, components);

        // The nearest stand a truck can leave from: the nearest of all may be tucked into a corner
        // no turning circle gets it out of.
        int[] door = null, park = null;
        float[][] exit = null;
        float standX = 0f, standY = 0f, facing = 0f;
        boolean bayed = false;
        LoadingBay bay = bayOf(building.building(), bays);
        if (bay != null) {
            int[] cell = {(int) Math.floor(bay.standX()), (int) Math.floor(bay.standY())};
            if (clearance.isPassable(cell[0], cell[1])) exit = exit(grid, clearance, components, cost, cell);
            if (exit != null) {
                bayed = true;
                door = new int[]{bay.doorX(), bay.doorY()};
                park = cell;
                standX = bay.standX();
                standY = bay.standY();
                facing = bay.headingDegrees();
            }
        }
        for (int[][] stand : parkings(grid, clearance, building.building(),
                (x, y) -> leaving.contains(components.labelAt(x, y))
                        && TurnAwareCorridor.canSetOutFrom(x + .5f, y + .5f, VehicleType.CARGO_CARRIER, grid) ? 1 : 0)) {
            if (exit != null) break;
            exit = exit(grid, clearance, components, cost, stand[1]);
            if (exit != null) {
                door = stand[0];
                park = stand[1];
                standX = park[0] + .5f;
                standY = park[1] + .5f;
                // Backed up to the door, so the tailgate faces it; the stand lets it set out whichever way
                // the road leaves.
                facing = (float) Math.toDegrees(Math.atan2(park[1] - door[1], park[0] - door[0]));
            }
        }
        if (exit == null) return null;

        VehicleMission mission = VehicleMission.deployed(standX, standY);
        long truck = sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.ALLY, mission);
        GroundBody body = sim.convoy().body(truck);
        // The facing worked in here is a maths angle; a hull's is sprite-north based.
        if (body != null) body.facingDegrees = AirBody.facingToward((float) Math.cos(Math.toRadians(facing)),
                (float) Math.sin(Math.toRadians(facing)));

        List<float[]> tailgate = tailgate(grid, park, door, facing);
        if (tailgate.isEmpty()) {
            sim.convoy().despawn(truck);
            return null;
        }
        String group = JobBoard.group(TAILGATE_SITE_BASE + building.site().id(), Affordance.STOW);
        for (int i = 0; i < tailgate.size(); i++) {
            float[] place = tailgate.get(i);
            sim.taskPoints().register(new TaskPoint(group + "#" + i, group, place[0], place[1],
                    park[0] + .5f, park[1] + .5f));
        }
        Loading loading = new Loading(building, truck, exit, group, target);
        loading.atBay = bayed;
        for (int member = 0; member < building.crew().size(); member++) {
            long actor = building.crew().get(member);
            if (!sim.getRoster().isLive(actor)) continue;
            AmbientTaskRoute route = StaffedCrew.loading(building.site(), building.kind(), building.work(), member,
                    tailgate, group, park[0] + .5f, park[1] + .5f);
            if (route != null) sim.ambientTasks().reroute(actor, route);
        }
        return loading;
    }

    /**
     * Counts what was set down since the last advance and moves the loading on.
     *
     * <p>A crate is aboard the moment a hand arrives at the tailgate and starts setting it down,
     * once per arrival: the loop alternates stock and tailgate, so every arrival there carried one.
     */
    public Phase advance(BattleSimulation sim, int tick) {
        if (phase == Phase.AWAY || phase == Phase.LOST) return phase;
        if (!truckStanding(sim)) return phase = Phase.LOST;
        if (phase == Phase.LOADING) {
            int working = 0;
            for (long actor : building.crew()) {
                if (!sim.getRoster().isLive(actor)) {
                    atTailgate.remove(actor);
                    continue;
                }
                working++;
                boolean there = tailgateGroup.equals(sim.ambientTasks().workingAt(actor));
                if (there && atTailgate.add(actor)) delivered++;
                else if (!there) atTailgate.remove(actor);
            }
            if (delivered >= target) depart(sim, tick);
            else if (working == 0) return phase = Phase.LOST;
            return phase;
        }
        GroundBody body = sim.convoy().body(truck);
        NavigationGrid grid = sim.getGrid();
        int last = exitRoute[0].length - 1;
        boolean roadEnd = Math.hypot(body.x - exitRoute[0][last], body.y - exitRoute[1][last]) <= AWAY_MARGIN;
        if (roadEnd || body.x < AWAY_MARGIN || body.y < AWAY_MARGIN
                || body.x > grid.getWidth() - AWAY_MARGIN || body.y > grid.getHeight() - AWAY_MARGIN) {
            sim.convoy().despawn(truck);
            phase = Phase.AWAY;
        }
        return phase;
    }

    /** The crew go back to the building's ordinary work and the truck takes its proven road out. */
    private void depart(BattleSimulation sim, int tick) {
        for (int member = 0; member < building.crew().size(); member++) {
            long actor = building.crew().get(member);
            if (!sim.getRoster().isLive(actor)) continue;
            AmbientTaskRoute route = StaffedCrew.member(building.site(), building.kind(), building.work(), member);
            if (route != null) sim.ambientTasks().reroute(actor, route);
        }
        GroundBody body = sim.convoy().body(truck);
        int n = exitRoute[0].length + 1;
        float[] xs = new float[n], ys = new float[n];
        xs[0] = body.x;
        ys[0] = body.y;
        System.arraycopy(exitRoute[0], 0, xs, 1, n - 1);
        System.arraycopy(exitRoute[1], 0, ys, 1, n - 1);
        VehicleMission mission = sim.convoy().mission(truck);
        mission.authoredRouteX = xs;
        mission.authoredRouteY = ys;
        mission.authoredLeg = VehicleLeg.MOVE_ORDER;
        departedTick = tick;
        phase = Phase.DEPARTING;
    }

    private boolean truckStanding(BattleSimulation sim) {
        if (!sim.convoy().isPresent(truck)) return false;
        VehicleMission mission = sim.convoy().mission(truck);
        return mission != null && mission.state != VehicleState.WRECKED && mission.state != VehicleState.GONE;
    }

    public Phase phase() { return phase; }

    /** Whether the truck stands at a set piece's loading bay rather than a stand found by search. */
    public boolean atBay() { return atBay; }

    /** The road the truck leaves by, from its stand to the map's edge: xs then ys. */
    public float[][] exitRoute() { return exitRoute; }

    /** Crates aboard so far. */
    public int delivered() { return delivered; }

    /** Crates aboard before the truck leaves. */
    public int target() { return target; }

    /** The truck, an allied hauler on the convoy. */
    public long truck() { return truck; }

    /** The tick the truck set off, or -1 before it has. */
    public int departedTick() { return departedTick; }

    /** The building being loaded from. */
    public StaffedBuildings.Staffed building() { return building; }

    /**
     * The door to load through and where a truck stands outside it: of the doors with ground a
     * truck's footprint fits on within {@link #PARK_REACH} steps' walk outside, the one where that
     * ground is nearest. Null where no door has any.
     *
     * <p>Walked, not measured: the nearest truck-sized ground as the crow flies may be the
     * building's own floor or a yard across a wall, and a truck there is one nobody can carry to.
     */
    static int[][] parking(NavigationGrid grid, VehicleClearance clearance, PointOfInterest b) {
        List<int[][]> stands = parkings(grid, clearance, b, (x, y) -> 1);
        return stands.isEmpty() ? null : stands.get(0);
    }

    /**
     * Every stand {@link #parking} would weigh, nearest walk first, at most {@link #PARK_CANDIDATES}
     * of them and no two within {@link #PARK_SPREAD} cells, counting only ground where {@code leaves}
     * answers 1: a truck-sized yard walled in on every side is ground a truck could stand on and
     * never drive out of.
     */
    static List<int[][]> parkings(NavigationGrid grid, VehicleClearance clearance, PointOfInterest b,
                                  IntBinaryOperator leaves) {
        List<int[]> found = new ArrayList<>();
        for (int x = b.left; x <= b.right; x++) {
            for (int y = b.top; y <= b.bottom; y++) {
                boolean edge = x == b.left || x == b.right || y == b.top || y == b.bottom;
                if (!edge || !grid.isDoorway(x, y)) continue;
                int ox = x + (x == b.left ? -1 : x == b.right ? 1 : 0);
                int oy = y + (y == b.top ? -1 : y == b.bottom ? 1 : 0);
                for (int[] at : walkToClearance(grid, clearance, b, ox, oy, leaves)) {
                    found.add(new int[]{ox, oy, at[0], at[1], at[2]});
                }
            }
        }
        found.sort(Comparator.comparingInt(f -> f[4]));
        List<int[][]> out = new ArrayList<>();
        for (int[] f : found) {
            if (out.size() >= PARK_CANDIDATES) break;
            boolean spread = true;
            for (int[][] o : out) {
                if (Math.abs(o[1][0] - f[2]) + Math.abs(o[1][1] - f[3]) < PARK_SPREAD) spread = false;
            }
            if (spread) out.add(new int[][]{{f[0], f[1]}, {f[2], f[3]}});
        }
        return out;
    }

    /** Every cell a truck fits on within {@link #PARK_REACH} steps' walk from {@code (x, y)} outside the building: {x, y, steps}. */
    private static List<int[]> walkToClearance(NavigationGrid grid, VehicleClearance clearance, PointOfInterest b,
                                               int x, int y, IntBinaryOperator leaves) {
        List<int[]> out = new ArrayList<>();
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return out;
        ArrayDeque<int[]> open = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        open.add(new int[]{x, y, 0});
        seen.add(((long) x << 32) | y);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] at = open.poll();
            if (clearance.isPassable(at[0], at[1]) && leaves.applyAsInt(at[0], at[1]) == 1) out.add(at);
            if (at[2] >= PARK_REACH) continue;
            for (int[] d : steps) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                if (nx >= b.left && nx <= b.right && ny >= b.top && ny <= b.bottom) continue;
                if (seen.add(((long) nx << 32) | ny)) open.add(new int[]{nx, ny, at[2] + 1});
            }
        }
        return out;
    }

    /** A drivable way from the parking to the nearest edge a truck can reach, or null where none is proved. */
    static float[][] exit(NavigationGrid grid, VehicleClearance clearance, ClearanceComponents components,
                          TerrainCostField cost, int[] park) {
        int w = grid.getWidth(), h = grid.getHeight();
        List<int[]> edges = new ArrayList<>();
        int inset = EXIT_SNAP / 2;
        for (int x = inset; x < w - inset; x += 4) {
            edges.add(new int[]{x, inset});
            edges.add(new int[]{x, h - 1 - inset});
        }
        for (int y = inset; y < h - inset; y += 4) {
            edges.add(new int[]{inset, y});
            edges.add(new int[]{w - 1 - inset, y});
        }
        edges.sort(Comparator.comparingLong(e -> (long) (e[0] - park[0]) * (e[0] - park[0])
                + (long) (e[1] - park[1]) * (e[1] - park[1])));
        int tried = 0;
        Set<Long> seen = new HashSet<>();
        for (int[] edge : edges) {
            if (tried >= EXIT_TRIES) break;
            int[] goal = roadEnd(grid, clearance, components, park, edge[0], edge[1]);
            if (goal == null || !seen.add(((long) goal[0] << 32) | goal[1])) continue;
            tried++;
            float[][] route = VehicleRoutePlanner.routeDrivable(park[0], park[1], goal[0], goal[1],
                    grid, cost, clearance, VehicleType.CARGO_CARRIER);
            if (route != null && route[0].length > 0) return route;
        }
        return null;
    }

    /**
     * Where a road out can end near {@code (x, y)}: the nearest cell within {@link #EXIT_SNAP} on the
     * parking's own ground where the truck can stand facing any way, so the route's last pose is
     * never refused for its tail in the trees.
     */
    private static int[] roadEnd(NavigationGrid grid, VehicleClearance clearance, ClearanceComponents components,
                                 int[] park, int x, int y) {
        int own = components.labelAt(park[0], park[1]);
        for (int r = 0; r <= EXIT_SNAP; r++) {
            int[] best = null;
            int bestD2 = Integer.MAX_VALUE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (!clearance.isPassable(nx, ny) || components.labelAt(nx, ny) != own) continue;
                    int d2 = dx * dx + dy * dy;
                    if (d2 >= bestD2) continue;
                    if (!TurnAwareCorridor.canSetOutFrom(nx + .5f, ny + .5f, VehicleType.CARGO_CARRIER, grid)) continue;
                    bestD2 = d2;
                    best = new int[]{nx, ny};
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** The bay whose loading door opens off {@code building}, or null. */
    public static LoadingBay bayOf(PointOfInterest building, List<LoadingBay> bays) {
        for (LoadingBay bay : bays) {
            if (bay.doorX() >= building.left - 2 && bay.doorX() <= building.right + 2
                    && bay.doorY() >= building.top - 2 && bay.doorY() <= building.bottom + 2) return bay;
        }
        return null;
    }

    /** The truck-sized ground components that touch the band a departing truck leaves the map through. */
    static Set<Integer> edgeComponents(VehicleClearance clearance, ClearanceComponents components) {
        int w = clearance.getWidth(), h = clearance.getHeight();
        int band = EXIT_SNAP;
        Set<Integer> out = new HashSet<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (x >= band && x < w - band && y >= band && y < h - band) {
                    y = h - band - 1;
                    continue;
                }
                if (!clearance.isPassable(x, y)) continue;
                int label = components.labelAt(x, y);
                if (label != ClearanceComponents.NONE) out.add(label);
            }
        }
        return out;
    }

    /**
     * Where a crate is set down: free floor just behind the truck, the side its tailgate faces,
     * nearest the door first.
     */
    static List<float[]> tailgate(NavigationGrid grid, int[] park, int[] door, float facingDegrees) {
        double rad = Math.toRadians(facingDegrees);
        float backX = (float) -Math.cos(rad), backY = (float) -Math.sin(rad);
        float behind = VehicleType.CARGO_CARRIER.visualLengthCells / 2f + 1f;
        float cx = park[0] + .5f + backX * behind, cy = park[1] + .5f + backY * behind;
        List<int[]> cells = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int x = (int) Math.floor(cx) + dx, y = (int) Math.floor(cy) + dy;
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                float px = x + .5f - (park[0] + .5f), py = y + .5f - (park[1] + .5f);
                // Behind the truck's middle, not beside its cab, and not under it.
                if (px * backX + py * backY < VehicleType.CARGO_CARRIER.visualLengthCells / 2f) continue;
                cells.add(new int[]{x, y});
            }
        }
        cells.sort(Comparator.comparingDouble((int[] c) -> Math.hypot(c[0] + .5f - cx, c[1] + .5f - cy))
                .thenComparingDouble(c -> Math.hypot(c[0] - door[0], c[1] - door[1])));
        List<float[]> places = new ArrayList<>();
        for (int[] c : cells) {
            if (places.size() >= TAILGATE_PLACES) break;
            boolean apart = true;
            for (float[] p : places) if (Math.abs(p[0] - (c[0] + .5f)) + Math.abs(p[1] - (c[1] + .5f)) < 1.5f) apart = false;
            if (apart) places.add(new float[]{c[0] + .5f, c[1] + .5f});
        }
        return places;
    }
}
