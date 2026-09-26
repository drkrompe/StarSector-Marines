package com.dillon.starsectormarines.battle.nav;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Frozen-terrain route jobs, owned by one battle. Workers only produce proofs;
 * the owning unit's update validates and installs them. A repeated pending
 * intent keeps its original start, allowing harmless crowd drift while the
 * proof runs; installation must prove the attachment from the current body.
 */
public final class AsyncClearanceRoutes implements AutoCloseable {
    public static final String ENABLED_PROPERTY = "battle.pathfinding.asyncClearance";
    private static final int QUANTUM = 256;
    private static final int RETAIN_TICKS = 300;
    // Every admitted job can retain a frontier between quanta. Worker count
    // alone does not bound that memory. Other owners retain their intent and
    // poll PENDING until a workspace is available.
    private static final int MAX_PENDING_SEARCHES = 8;

    public record Request(float startX, float startY, int goalX, int goalY, float radius) {
        private boolean sameIntent(Request other) {
            return goalX == other.goalX && goalY == other.goalY && radius == other.radius;
        }
    }

    public record Reply(PathRequestStatus status, ClearanceRoutePlanner.Result proof,
                        long topologyRevision) {}

    private static final class Entry {
        final Request request;
        final long revision;
        final Job task;
        int lastPollTick;

        Entry(Request request, long revision, Job task, int tick) {
            this.request = request;
            this.revision = revision;
            this.task = task;
            lastPollTick = tick;
        }
    }

    /** One quantum rejoins the queue tail so a long route cannot monopolize a worker. */
    private final class Job implements Runnable {
        final CompletableFuture<ClearanceRoutePlanner.Result> future = new CompletableFuture<>();
        private NavigationGrid frozen;
        private final Request request;
        private ClearanceRoutePlanner.Search search;

        Job(NavigationGrid frozen, Request request) { this.frozen = frozen; this.request = request; }

        @Override public void run() {
            boolean reschedule = false;
            try {
                if (future.isDone()) return;
                if (search == null) {
                    long nodes = (2L * frozen.getWidth() + 1) * (2L * frozen.getHeight() + 1);
                    int budget = (int) Math.min(8_000_000L, Math.max(4096L, nodes * 9L));
                    search = new ClearanceRoutePlanner().begin(frozen,
                            request.startX, request.startY, request.goalX + .5f, request.goalY + .5f,
                            request.radius, .75f, budget);
                    frozen = null;
                }
                do {
                    if (future.isCancelled() || Thread.currentThread().isInterrupted()) {
                        future.cancel(false);
                        return;
                    }
                    var result = search.step(QUANTUM);
                    if (result.status() != ClearanceRoutePlanner.Status.PENDING) {
                        future.complete(result);
                        return;
                    }
                    if (executor != null) {
                        reschedule = true;
                        break;
                    }
                } while (true);
            } catch (Throwable failed) {
                future.completeExceptionally(failed);
            } finally {
                if (future.isDone()) {
                    if (search != null) search.close();
                    search = null;
                    frozen = null;
                }
            }
            // Publish only after this invocation has stopped touching the
            // frontier; another worker may start the next quantum immediately.
            if (reschedule && !future.isDone()) {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException shutdown) {
                    future.cancel(false);
                    if (search != null) search.close();
                    search = null;
                    frozen = null;
                }
            }
        }
    }

    private final ThreadPoolExecutor executor;
    private final Map<Long, Entry> entries = new HashMap<>();
    private NavigationGrid snapshot;
    private long snapshotRevision = Long.MIN_VALUE;
    private int tick;
    private boolean closed;

    public AsyncClearanceRoutes(boolean asynchronous) {
        executor = asynchronous ? new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_SEARCHES * 2), task -> {
                    Thread thread = new Thread(task, "BattleSim-ClearanceRoute");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy()) : null;
    }

    public synchronized void beginTick(int nextTick) {
        tick = nextTick;
        entries.entrySet().removeIf(row -> {
            if (tick - row.getValue().lastPollTick <= RETAIN_TICKS) return false;
            cancel(row.getValue());
            return true;
        });
    }

    public synchronized Reply pollOrSubmit(long owner, Request request, NavigationGrid grid) {
        long revision = grid.topologyRevision();
        if (closed) return new Reply(PathRequestStatus.FAILED, null, revision);
        Entry entry = entries.get(owner);
        // Failed proofs can be reconsidered after the body moved substantially.
        var completed = entry != null && entry.task.future.isDone()
                && !entry.task.future.isCompletedExceptionally() ? entry.task.future.getNow(null) : null;
        boolean moved = completed != null && completed.status() != ClearanceRoutePlanner.Status.FOUND
                && Math.hypot(request.startX - entry.request.startX,
                request.startY - entry.request.startY) > .5d;
        if (entry == null || entry.revision != revision || moved || !entry.request.sameIntent(request)) {
            if (entry != null) cancel(entry);
            entries.remove(owner);
            long pending = entries.values().stream().filter(row -> !row.task.future.isDone()).count();
            if (pending >= MAX_PENDING_SEARCHES) return new Reply(PathRequestStatus.PENDING, null, revision);
            if (snapshot == null || snapshotRevision != revision) {
                snapshot = grid.copyVehicleRoutingTopology();
                snapshotRevision = revision;
            }
            NavigationGrid frozen = snapshot;
            Job task = new Job(frozen, request);
            entry = new Entry(request, revision, task, tick);
            entries.put(owner, entry);
            if (executor == null) task.run();
            else {
                try {
                    executor.execute(task);
                } catch (RejectedExecutionException full) {
                    entries.remove(owner);
                    task.future.cancel(false);
                    return new Reply(PathRequestStatus.PENDING, null, revision);
                }
            }
        }
        entry.lastPollTick = tick;
        if (!entry.task.future.isDone()) return new Reply(PathRequestStatus.PENDING, null, revision);
        if (entry.task.future.isCancelled()) {
            entries.remove(owner);
            return new Reply(PathRequestStatus.PENDING, null, revision);
        }
        try {
            var proof = entry.task.future.get();
            return new Reply(proof.status() == ClearanceRoutePlanner.Status.FOUND
                    ? PathRequestStatus.READY : PathRequestStatus.FAILED, proof, revision);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Reply(PathRequestStatus.PENDING, null, revision);
        } catch (CancellationException cancelled) {
            entries.remove(owner);
            return new Reply(PathRequestStatus.PENDING, null, revision);
        } catch (Exception failed) {
            throw new IllegalStateException("Clearance route worker failed", failed);
        }
    }

    public synchronized void forget(long owner) {
        Entry entry = entries.remove(owner);
        if (entry != null) cancel(entry);
    }

    private void cancel(Entry entry) {
        entry.task.future.cancel(false);
        if (executor != null) executor.remove(entry.task);
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (Entry entry : entries.values()) cancel(entry);
        entries.clear();
        snapshot = null;
        if (executor != null) executor.shutdownNow();
    }
}
