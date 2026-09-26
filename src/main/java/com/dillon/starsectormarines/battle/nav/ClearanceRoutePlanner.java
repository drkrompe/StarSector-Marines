package com.dillon.starsectormarines.battle.nav;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded A* over half-cell points for circular ground bodies. Nodes and every
 * straight link use the same terrain authority as direct movement. The exact
 * start is retained and attached by legal segments, never snapped. This is a
 * discrete route graph, not a proof of completeness over continuous space.
 *
 * <p>Destination tolerance is explicit: a caller requesting an objective must
 * not silently inherit tactical click snapping. Candidates inside that radius
 * are tried by distance from the requested point, then stable lattice index.
 * A nearer candidate whose search exhausts the shared budget stops the query;
 * it is never silently skipped in favor of a farther destination.
 *
 * <p>One reusable workspace per worker retains primitive storage, not topology
 * facts. Legality is memoized only within one request against the current raw
 * grid. As with other pathfinders, callers must not mutate the grid concurrently.
 * Endpoints are lattice points even when the requested point is not: zero
 * tolerance requires a requested half-cell point. Endpoint enumeration is
 * independently capped by {@link #MAX_ENDPOINT_DISTANCE}; it is not a map-wide
 * nearest-reachable fallback. No grid reference survives the query.
 */
public final class ClearanceRoutePlanner {
    /** Caps endpoint enumeration independently of the expansion budget. */
    public static final float MAX_ENDPOINT_DISTANCE = 8f;
    private static final float ATTACHMENT_DISTANCE = 1f;
    private final ThreadLocal<Workspace> workspaces = ThreadLocal.withInitial(Workspace::new);

    public enum Status {
        FOUND, INVALID_START, NO_START_ATTACHMENT, NO_LEGAL_DESTINATION,
        UNREACHABLE, SEARCH_LIMIT
    }

    public record Point(float x, float y) {}

    /** Failed queries have no resolved endpoint and no waypoints. */
    public record Result(Status status, Point requested, Point resolved,
                         List<Point> waypoints, long topologyRevision,
                         int expandedNodes, int clearanceChecks) {
        public Result { waypoints = List.copyOf(waypoints); }
    }

    public Result findRoute(NavigationGrid grid, float startX, float startY,
                            float requestedX, float requestedY, float radius,
                            float maxEndpointDistance, int maxExpandedNodes) {
        if (!Float.isFinite(startX) || !Float.isFinite(startY)
                || !Float.isFinite(requestedX) || !Float.isFinite(requestedY)
                || !Float.isFinite(radius) || radius <= 0f
                || !Float.isFinite(maxEndpointDistance) || maxEndpointDistance < 0f
                || maxEndpointDistance > MAX_ENDPOINT_DISTANCE
                || maxExpandedNodes < 1) {
            throw new IllegalArgumentException("Finite coordinates, positive radius/budget and endpoint tolerance in [0, 8] required");
        }
        long width = 2L * grid.getWidth() + 1L;
        long height = 2L * grid.getHeight() + 1L;
        if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE
                || width * height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Half-cell lattice exceeds indexed capacity");
        }
        Workspace w = workspaces.get();
        w.begin(grid, (int) width, (int) height, radius);
        try {
            return find(w, startX, startY, requestedX, requestedY,
                    maxEndpointDistance, maxExpandedNodes);
        } finally {
            w.grid = null;
        }
    }

    private Result find(Workspace w, float startX, float startY,
                        float requestedX, float requestedY, float maxEndpointDistance,
                        int maxExpandedNodes) {
        NavigationGrid grid = w.grid;
        Point requested = new Point(requestedX, requestedY);
        Point start = new Point(startX, startY);
        long revision = grid.topologyRevision();
        w.checks++;
        if (!ManualTerrainMotion.canStand(grid, startX, startY, w.radius)) {
            return result(Status.INVALID_START, requested, null, List.of(), revision, w);
        }
        List<Integer> goals = pointsWithin(w, requestedX, requestedY, maxEndpointDistance);
        goals.sort(Comparator.<Integer>comparingDouble(id -> distanceSquared(
                w.x(id), w.y(id), requestedX, requestedY)).thenComparingInt(id -> id));
        if (goals.isEmpty()) return result(Status.NO_LEGAL_DESTINATION, requested, null, List.of(), revision, w);
        List<Integer> attachments = null;
        for (int goal : goals) {
            Point endpoint = new Point(w.x(goal), w.y(goal));
            // An exact straight proof needs neither lattice attachment nor A*.
            // Try it in candidate order so it cannot bypass a nearer endpoint
            // whose reachability has not yet been decided within the budget.
            if (w.link(startX, startY, endpoint.x(), endpoint.y())) {
                List<Point> route = start.equals(endpoint) ? List.of(start) : List.of(start, endpoint);
                return result(Status.FOUND, requested, endpoint, route, revision, w);
            }
            if (attachments == null) {
                attachments = pointsWithin(w, startX, startY, ATTACHMENT_DISTANCE);
                attachments.removeIf(id -> !w.link(startX, startY, w.x(id), w.y(id)));
                if (attachments.isEmpty()) {
                    return result(Status.NO_START_ATTACHMENT, requested, null, List.of(), revision, w);
                }
            }
            w.resetSearch(goal);
            for (int node : attachments) {
                w.offer(node, (float) Math.hypot(w.x(node) - startX, w.y(node) - startY), -1);
            }
            while (w.heapSize > 0) {
                int slot = w.pop();
                int node = w.ids[slot];
                if (node == goal) {
                    List<Point> route = new ArrayList<>();
                    for (int cursor = slot; cursor >= 0; cursor = w.parents[cursor]) {
                        route.add(new Point(w.x(w.ids[cursor]), w.y(w.ids[cursor])));
                    }
                    Collections.reverse(route);
                    if (!route.get(0).equals(start)) route.add(0, start);
                    return result(Status.FOUND, requested, new Point(w.x(goal), w.y(goal)), route, revision, w);
                }
                if (w.expanded == maxExpandedNodes) {
                    return result(Status.SEARCH_LIMIT, requested, null, List.of(), revision, w);
                }
                w.expanded++;
                int nx = node % w.width;
                int ny = node / w.width;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int xx = nx + dx, yy = ny + dy;
                        if (xx < 0 || yy < 0 || xx >= w.width || yy >= w.height) continue;
                        int next = yy * w.width + xx;
                        if (!w.legal(next) || !w.link(w.x(node), w.y(node), w.x(next), w.y(next))) continue;
                        float cost = w.costs[slot] + (dx == 0 || dy == 0 ? 0.5f : (float) Math.sqrt(0.5d));
                        w.offer(next, cost, slot);
                    }
                }
            }
        }
        return result(Status.UNREACHABLE, requested, null, List.of(), revision, w);
    }

    private static Result result(Status status, Point requested, Point resolved,
                                 List<Point> route, long revision, Workspace w) {
        return new Result(status, requested, resolved, route, revision, w.expanded, w.checks);
    }

    private static List<Integer> pointsWithin(Workspace w, float x, float y, float distance) {
        List<Integer> points = new ArrayList<>();
        int minX = (int) Math.max(0d, Math.ceil(((double) x - distance) * 2d));
        int maxX = (int) Math.min(w.width - 1d, Math.floor(((double) x + distance) * 2d));
        int minY = (int) Math.max(0d, Math.ceil(((double) y - distance) * 2d));
        int maxY = (int) Math.min(w.height - 1d, Math.floor(((double) y + distance) * 2d));
        double squared = (double) distance * distance;
        for (int yy = minY; yy <= maxY; yy++) {
            for (int xx = minX; xx <= maxX; xx++) {
                int node = yy * w.width + xx;
                if (distanceSquared(xx * 0.5f, yy * 0.5f, x, y) <= squared && w.legal(node)) points.add(node);
            }
        }
        return points;
    }

    private static double distanceSquared(float x, float y, float targetX, float targetY) {
        double dx = (double) x - targetX, dy = (double) y - targetY;
        return dx * dx + dy * dy;
    }

    private static final class Workspace {
        final Int2IntOpenHashMap legality = new Int2IntOpenHashMap();
        final Int2IntOpenHashMap slots = new Int2IntOpenHashMap();
        int[] ids = new int[64], parents = new int[64], heap = new int[64], heapPositions = new int[64];
        float[] costs = new float[64], priorities = new float[64];
        NavigationGrid grid;
        int width, height, goal, size, heapSize, expanded, checks;
        float radius;

        Workspace() { slots.defaultReturnValue(-1); }

        void begin(NavigationGrid nextGrid, int nextWidth, int nextHeight, float nextRadius) {
            grid = nextGrid; width = nextWidth; height = nextHeight; radius = nextRadius;
            legality.clear(); slots.clear(); expanded = 0; checks = 0;
        }

        float x(int id) { return (id % width) * 0.5f; }
        float y(int id) { return (id / width) * 0.5f; }

        boolean legal(int id) {
            int cached = legality.get(id);
            if (cached != 0) return cached == 1;
            checks++;
            boolean fits = ManualTerrainMotion.canStand(grid, x(id), y(id), radius);
            legality.put(id, fits ? 1 : 2);
            return fits;
        }

        boolean link(float fromX, float fromY, float toX, float toY) {
            checks++;
            return ManualTerrainMotion.canSweepStraight(grid, fromX, fromY, toX - fromX, toY - fromY, radius);
        }

        void resetSearch(int target) { slots.clear(); size = 0; heapSize = 0; goal = target; }

        void offer(int id, float cost, int parent) {
            int slot = slots.get(id);
            if (slot >= 0 && cost >= costs[slot]) return;
            if (slot < 0) {
                slot = size++;
                ensure(size);
                slots.put(id, slot); ids[slot] = id; heapPositions[slot] = -1;
            }
            costs[slot] = cost; parents[slot] = parent;
            float dx = Math.abs(x(id) - x(goal)), dy = Math.abs(y(id) - y(goal));
            priorities[slot] = cost + Math.max(dx, dy)
                    + ((float) Math.sqrt(2d) - 1f) * Math.min(dx, dy);
            int position = heapPositions[slot];
            if (position < 0) { position = heapSize++; heap[position] = slot; heapPositions[slot] = position; }
            while (position > 0) {
                int parentPosition = (position - 1) / 2;
                if (!before(heap[position], heap[parentPosition])) break;
                swap(position, parentPosition); position = parentPosition;
            }
        }

        int pop() {
            int result = heap[0];
            heapSize--;
            if (heapSize > 0) {
                heap[0] = heap[heapSize]; heapPositions[heap[0]] = 0;
                int pos = 0;
                while (pos * 2 + 1 < heapSize) {
                    int child = pos * 2 + 1;
                    if (child + 1 < heapSize && before(heap[child + 1], heap[child])) child++;
                    if (!before(heap[child], heap[pos])) break;
                    swap(pos, child); pos = child;
                }
            }
            heapPositions[result] = -1;
            return result;
        }

        boolean before(int a, int b) {
            int byCost = Float.compare(priorities[a], priorities[b]);
            if (byCost != 0) return byCost < 0;
            int byProgress = Float.compare(costs[b], costs[a]);
            return byProgress < 0 || (byProgress == 0 && ids[a] < ids[b]);
        }

        void swap(int a, int b) {
            int value = heap[a]; heap[a] = heap[b]; heap[b] = value;
            heapPositions[heap[a]] = a; heapPositions[heap[b]] = b;
        }

        void ensure(int needed) {
            if (needed <= ids.length) return;
            int capacity = Math.max(needed, ids.length + ids.length / 2);
            ids = Arrays.copyOf(ids, capacity); parents = Arrays.copyOf(parents, capacity);
            heap = Arrays.copyOf(heap, capacity); heapPositions = Arrays.copyOf(heapPositions, capacity);
            costs = Arrays.copyOf(costs, capacity); priorities = Arrays.copyOf(priorities, capacity);
        }
    }
}
