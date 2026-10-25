package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.ambient.StaffedCrew;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectivePressure;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stripping a workshop on a factory row ({@code set-piece-blocks.md}, The factory row): the row's hands carry the
 * workshop's gear out across the service lane and stack it against the far side for lifting, while the company
 * holds the lane. A fight comes down the lane from either gate.
 *
 * <p>A row's workshops are too small to be staffed buildings ({@code staffed-buildings.md}: their stock is a few
 * crates, not a store), so the hands are brought for the job: a few colonists, each walking a loop from a place
 * on the workshop floor to the stack and back with a load in their arms. They keep a staffed crew's yield
 * ({@link AmbientThreatPolicy#FIGHT_NEARBY}): a fight in reach stops the work until it has gone. Done when the
 * count is stacked; lost when nobody is left to carry. Counted as a loading counts its crates, once per arrival
 * at the stack.
 */
public final class WorkshopStrip implements PressuredObjective.Task {

    /** Hands brought to strip the workshop. */
    static final int HANDS = 3;
    /** Places on the workshop floor a load is lifted from. */
    static final int PICKUPS = 4;
    /** Places at the stack a load can be set down at once. */
    static final int STACK_PLACES = 2;
    /** Seconds a load takes to lift or set down. */
    static final float DWELL_SECONDS = 3.5f;
    /** Share of the count stacked when the pressure's final rush comes. */
    static final float FINAL_AT = .75f;
    /** A door this far along the lane from either gate is still the gate's, as a share of the lane. */
    static final float GATE_SHARE = .05f;
    /** Claim groups are scoped above every building's, every tailgate's and every stack's. */
    private static final int PICKUP_SITE = 970_000, STACK_SITE = 970_001;

    private final ServiceLane lane;
    private final PointOfInterest workshop;
    private final List<Long> crew;
    private final String stackGroup;
    private final int[] stack;
    private final int target;
    private final Set<Long> atStack = new HashSet<>();
    private int carried;
    private Status status = Status.ACTIVE;

    private WorkshopStrip(ServiceLane lane, PointOfInterest workshop, List<Long> crew, String stackGroup, int[] stack,
                          int target) {
        this.lane = lane;
        this.workshop = workshop;
        this.crew = crew;
        this.stackGroup = stackGroup;
        this.stack = stack;
        this.target = target;
    }

    /** Whether the map has a factory row with a workshop on its lane. */
    public static boolean offered(MapResult map, NavigationGrid grid) {
        return workshop(map, grid) != null;
    }

    /** The largest building whose door opens onto the map's first service lane, or null. */
    static PointOfInterest workshop(MapResult map, NavigationGrid grid) {
        if (map.serviceLanes.isEmpty()) return null;
        ServiceLane lane = map.serviceLanes.get(0);
        PointOfInterest best = null;
        int bestArea = -1;
        for (PointOfInterest b : map.pointsOfInterest) {
            if (laneDoor(b, lane, grid) == null) continue;
            int area = (b.right - b.left + 1) * (b.bottom - b.top + 1);
            if (area > bestArea) {
                best = b;
                bestArea = area;
            }
        }
        return best;
    }

    /**
     * Brings the hands to the row's largest workshop and sets them carrying its gear to a stack across the lane
     * from its door; null where the map has no row, or the workshop has no floor to lift from or the lane no room
     * for the stack.
     *
     * @param target loads stacked before the workshop is stripped
     */
    public static WorkshopStrip install(BattleSimulation sim, MapResult map, int target) {
        NavigationGrid grid = sim.getGrid();
        PointOfInterest workshop = workshop(map, grid);
        if (workshop == null || target <= 0) return null;
        ServiceLane lane = map.serviceLanes.get(0);
        int[] door = laneDoor(workshop, lane, grid);
        List<float[]> places = stack(grid, lane, door);
        List<int[]> floor = floor(grid, workshop, PICKUPS);
        if (places.isEmpty() || floor.size() < 2) return null;

        String pickupGroup = JobBoard.group(PICKUP_SITE, Affordance.STOW);
        String stackGroup = JobBoard.group(STACK_SITE, Affordance.STOW);
        float doorX = door[0] + .5f, doorY = door[1] + .5f;
        for (int i = 0; i < floor.size(); i++) {
            int[] at = floor.get(i);
            sim.taskPoints().register(new TaskPoint(pickupGroup + "#" + i, pickupGroup, at[0] + .5f, at[1] + .5f,
                    doorX, doorY));
        }
        for (int i = 0; i < places.size(); i++) {
            float[] place = places.get(i);
            sim.taskPoints().register(new TaskPoint(stackGroup + "#" + i, stackGroup, place[0], place[1], doorX, doorY));
        }
        List<Long> crew = new ArrayList<>();
        for (int member = 0; member < HANDS; member++) {
            List<AmbientTaskRoute.Stop> stops = new ArrayList<>();
            for (int i = 0; i < floor.size(); i++) {
                int[] at = floor.get(Math.floorMod(member + i, floor.size()));
                stops.add(new AmbientTaskRoute.Stop(at[0] + .5f, at[1] + .5f, DWELL_SECONDS,
                        CrewRole.activityFor(Affordance.STOW), doorX, doorY, pickupGroup, true));
                float[] place = places.get(Math.floorMod(member + i, places.size()));
                // Facing back toward the door they came out of, the way a stack is built.
                stops.add(new AmbientTaskRoute.Stop(place[0], place[1], DWELL_SECONDS,
                        CrewRole.activityFor(Affordance.STOW), doorX, doorY, stackGroup, true));
            }
            AmbientTaskRoute route = new AmbientTaskRoute("strip-" + member, member * 2.3f, Shift.WALK_SPEED,
                    StaffedCrew.FIGHT_RADIUS, AmbientThreatPolicy.FIGHT_NEARBY, stops);
            int[] start = floor.get(member % floor.size());
            long actor = sim.spawn(new EntitySpec("Row hand " + (member + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                    start[0], start[1]).role(UnitRole.FLEE));
            sim.ambientTasks().assign(actor, route);
            crew.add(actor);
        }
        sim.ambientTasks().settle();
        float[] first = places.get(0);
        return new WorkshopStrip(lane, workshop, crew, stackGroup,
                new int[]{(int) Math.floor(first[0]), (int) Math.floor(first[1])}, target);
    }

    @Override
    public Status advance(BattleSimulation sim, int tick) {
        if (status.isFinal()) return status;
        int working = 0;
        for (long actor : crew) {
            if (!sim.getRoster().isLive(actor)) {
                atStack.remove(actor);
                continue;
            }
            working++;
            boolean there = stackGroup.equals(sim.ambientTasks().workingAt(actor));
            if (there && atStack.add(actor)) carried++;
            else if (!there) atStack.remove(actor);
        }
        if (carried >= target) {
            // Stripped: the hands go their own ways.
            for (long actor : crew) if (sim.getRoster().isLive(actor)) sim.ambientTasks().release(actor);
            return status = Status.SUCCEEDED;
        }
        if (working == 0) return status = Status.FAILED;
        return status;
    }

    /** Half stacked is half way; the last quarter brings everything the pressure owns to the stack. */
    @Override
    public ObjectivePressure.Beat beat() {
        float progress = carried / (float) target;
        return progress >= FINAL_AT ? new ObjectivePressure.Beat(progress, true, stack)
                : ObjectivePressure.Beat.underWay(progress);
    }

    /** Loads stacked so far. */
    public int carried() { return carried; }

    /** Loads stacked before the workshop is stripped. */
    public int target() { return target; }

    /** Hands left alive. */
    public int hands(BattleSimulation sim) {
        int n = 0;
        for (long actor : crew) if (sim.getRoster().isLive(actor)) n++;
        return n;
    }

    /** The stack's first place, a cell in the lane. */
    public int[] stackCell() { return stack; }

    /** The lane the row's gates open off. */
    public ServiceLane lane() { return lane; }

    /** The workshop being stripped. */
    public PointOfInterest workshop() { return workshop; }

    /**
     * The lane cell outside one of {@code building}'s doorways, or null where none opens onto the lane between its
     * gates: a building beyond a gate whose door happens to face the gate is not on the row.
     */
    static int[] laneDoor(PointOfInterest b, ServiceLane lane, NavigationGrid grid) {
        for (int x = b.left; x <= b.right; x++) {
            for (int y = b.top; y <= b.bottom; y++) {
                boolean edge = x == b.left || x == b.right || y == b.top || y == b.bottom;
                if (!edge || !grid.isDoorway(x, y)) continue;
                int ox = x + (x == b.left ? -1 : x == b.right ? 1 : 0);
                int oy = y + (y == b.top ? -1 : y == b.bottom ? 1 : 0);
                float along = along(lane, ox + .5f, oy + .5f);
                if (along <= GATE_SHARE || along >= 1f - GATE_SHARE) continue;
                if (fromCentreLine(lane, ox + .5f, oy + .5f) <= lane.width() / 2f + .5f) return new int[]{ox, oy};
            }
        }
        return null;
    }

    /** How far along the lane from gate A to gate B {@code (x, y)} lies, as a share of its length. */
    static float along(ServiceLane lane, float x, float y) {
        float dx = lane.endX() - lane.startX(), dy = lane.endY() - lane.startY();
        float length2 = dx * dx + dy * dy;
        return length2 <= 0f ? 0f : ((x - lane.startX()) * dx + (y - lane.startY()) * dy) / length2;
    }

    /** How far {@code (x, y)} stands from the lane's centre line, gate to gate. */
    static float fromCentreLine(ServiceLane lane, float x, float y) {
        float t = Math.max(0f, Math.min(1f, along(lane, x, y)));
        float px = lane.startX() + t * (lane.endX() - lane.startX()), py = lane.startY() + t * (lane.endY() - lane.startY());
        return (float) Math.hypot(x - px, y - py);
    }

    /** Up to {@code count} places on the workshop's floor, never a doorway, no two touching, farthest from the lane last. */
    static List<int[]> floor(NavigationGrid grid, PointOfInterest b, int count) {
        List<int[]> places = new ArrayList<>();
        int w = grid.getWidth();
        boolean[] seen = new boolean[w * grid.getHeight()];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int x = b.left + 1; x < b.right; x++) {
            for (int y = b.top + 1; y < b.bottom; y++) {
                if (!grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                boolean byDoor = grid.isDoorway(x - 1, y) || grid.isDoorway(x + 1, y) || grid.isDoorway(x, y - 1)
                        || grid.isDoorway(x, y + 1);
                if (byDoor) {
                    open.add(new int[]{x, y});
                    seen[y * w + x] = true;
                }
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty() && places.size() < count) {
            int[] at = open.poll();
            boolean apart = true;
            for (int[] p : places) if (Math.max(Math.abs(p[0] - at[0]), Math.abs(p[1] - at[1])) < 2) apart = false;
            if (apart) places.add(at);
            for (int[] d : dirs) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (nx <= b.left || ny <= b.top || nx >= b.right || ny >= b.bottom || seen[ny * w + nx]) continue;
                seen[ny * w + nx] = true;
                if (grid.isWalkable(nx, ny) && !grid.isDoorway(nx, ny)) open.add(new int[]{nx, ny});
            }
        }
        return places;
    }

    /**
     * Where a load is set down: across the lane from the door, against the far side, on the door's line and beside
     * it, so the stack stands off the run from gate to gate.
     */
    static List<float[]> stack(NavigationGrid grid, ServiceLane lane, int[] door) {
        float dx = lane.endX() - lane.startX(), dy = lane.endY() - lane.startY();
        float length = (float) Math.max(1e-3, Math.hypot(dx, dy));
        float ux = dx / length, uy = dy / length;
        // Across the lane: the normal pointing from the door over the centre line.
        float nx = -uy, ny = ux;
        float side = (door[0] + .5f - lane.startX()) * nx + (door[1] + .5f - lane.startY()) * ny;
        if (side > 0) {
            nx = -nx;
            ny = -ny;
        }
        int across = lane.width() - 1;
        List<float[]> places = new ArrayList<>();
        int[] along = {0, 1, -1, 2, -2};
        for (int a : along) {
            if (places.size() >= STACK_PLACES) break;
            int x = Math.round(door[0] + nx * across + ux * a), y = Math.round(door[1] + ny * across + uy * a);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
            boolean apart = true;
            for (float[] p : places) if (Math.abs(p[0] - (x + .5f)) + Math.abs(p[1] - (y + .5f)) < 1.5f) apart = false;
            if (apart) places.add(new float[]{x + .5f, y + .5f});
        }
        return places;
    }
}
