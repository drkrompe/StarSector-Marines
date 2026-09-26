package com.dillon.starsectormarines.battle.nav;

import java.util.HashMap;
import java.util.Map;

/**
 * Battle-owned queue for resumable circular route proofs. Parallel unit updates
 * may submit or poll; the battle advances the queue once at a serial topology
 * boundary. Service order is by owner identity with a retained round-robin
 * cursor, independent of submission order. Each owner has at most one request.
 *
 * <p>Both node expansions and visited requests have explicit per-advance caps.
 * These bound algorithmic work, not elapsed time: initial geometry checks,
 * allocation and GC still cost time. The caller supplies the scheduling policy
 * and must forget dead or superseded owners. Completed results remain available
 * until forgotten, replaced, or invalidated by a topology revision.
 */
public final class ClearanceRouteScheduler implements AutoCloseable {
    public record Request(float startX, float startY, float targetX, float targetY,
                          float radius, float endpointTolerance, int totalExpansionBudget) {}

    public record Work(int visitedRequests, int expandedNodes, int pendingRequests) {}

    private record Entry(Request request, long revision, ClearanceRoutePlanner.Search search) {}

    private final NavigationGrid grid;
    private final ClearanceRoutePlanner planner = new ClearanceRoutePlanner();
    private final Map<Long, Entry> requests = new HashMap<>();
    private long lastServed = Long.MIN_VALUE;
    private boolean closed;

    public ClearanceRouteScheduler(NavigationGrid grid) {
        this.grid = grid;
    }

    /** Queues work without searching. A changed exact request replaces its owner's old frontier. */
    public synchronized ClearanceRoutePlanner.Result pollOrSubmit(long owner, Request request) {
        if (closed) throw new IllegalStateException("Route scheduler is closed");
        Entry entry = requests.get(owner);
        long revision = grid.topologyRevision();
        if (entry == null || entry.revision != revision || !entry.request.equals(request)) {
            // Validate before canceling the old request; a malformed replacement
            // must not destroy work that its owner could still consume.
            var search = planner.begin(grid, request.startX, request.startY,
                    request.targetX, request.targetY, request.radius,
                    request.endpointTolerance, request.totalExpansionBudget);
            if (entry != null) entry.search.close();
            entry = new Entry(request, revision, search);
            requests.put(owner, entry);
        }
        return entry.search.result();
    }

    /**
     * Advances each selected owner at most once. Call where the grid cannot
     * mutate until this method returns. Node and request budgets are shared
     * across the queue, while quantum bounds one owner's share.
     */
    public synchronized Work advance(int maxExpandedNodes, int maxRequests, int quantum) {
        if (maxExpandedNodes < 1 || maxRequests < 1 || quantum < 1) {
            throw new IllegalArgumentException("Positive scheduling budgets are required");
        }
        if (closed) return new Work(0, 0, 0);
        long[] owners = requests.entrySet().stream()
                .filter(row -> row.getValue().search.result().status() == ClearanceRoutePlanner.Status.PENDING)
                .mapToLong(Map.Entry::getKey).sorted().toArray();
        if (owners.length == 0) return new Work(0, 0, 0);
        int first = 0;
        while (first < owners.length && owners[first] <= lastServed) first++;
        if (first == owners.length) first = 0;
        int visited = 0, expanded = 0;
        for (int i = 0; i < owners.length && visited < maxRequests && expanded < maxExpandedNodes; i++) {
            long owner = owners[(first + i) % owners.length];
            var search = requests.get(owner).search;
            int previous = search.result().expandedNodes();
            var result = search.step(Math.min(quantum, maxExpandedNodes - expanded));
            expanded += result.expandedNodes() - previous;
            visited++;
            lastServed = owner;
        }
        int pending = 0;
        for (Entry entry : requests.values()) {
            if (entry.search.result().status() == ClearanceRoutePlanner.Status.PENDING) pending++;
        }
        return new Work(visited, expanded, pending);
    }

    public synchronized void forget(long owner) {
        Entry entry = requests.remove(owner);
        if (entry != null) entry.search.close();
    }

    public synchronized int retainedRequests() { return requests.size(); }

    @Override
    public synchronized void close() {
        for (Entry entry : requests.values()) entry.search.close();
        requests.clear();
        closed = true;
    }
}
