package com.dillon.starsectormarines.battle.nav;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Battle-owned asynchronous route searches for settled DEFEND_TRACK members.
 * Workers see only immutable navigation inputs and never mutate the battle.
 */
public final class AsyncDefendTrackRoutes implements AutoCloseable {
    public static final String ENABLED_PROPERTY =
            "battle.pathfinding.asyncDefendTrack";
    public static final String QUEUE_CAPACITY_PROPERTY =
            "battle.pathfinding.asyncDefendTrack.queueCapacity";
    public static final String WORKERS_PROPERTY =
            "battle.pathfinding.asyncDefendTrack.workers";
    private static final int MAX_QUEUED = 512;
    private static final int REQUEST_TTL_TICKS = 120;
    private static final int NO_PATH_RETRY_TICKS = 30;

    public record Request(long member, int squadId, long routingEpoch,
                          Object actionToken, int assignmentX, int assignmentY,
                          int startX, int startY, int goalX, int goalY,
                          int anchorX, int anchorY, boolean cardinalOnly) { }

    public record Result(boolean ready, int[] path) {
        private static final Result WAITING = new Result(false,
                GridPathfinder.EMPTY_PATH);
    }

    public record Metrics(long submitted, long completed, long searchNanos,
                          long searchCpuNanos, long maxSearchNanos,
                          long canceled, long rejected, long noPath,
                          int queueDepth, int pendingMembers, int maxQueueDepth,
                          int maxRunning,
                          int queueWaitP95Ticks, int queueWaitMaxTicks,
                          int deliveryP95Ticks, int deliveryMaxTicks,
                          int finishedWaitP95Ticks, int finishedWaitMaxTicks,
                          long snapshotNanos, long maxSnapshotNanos,
                          long topologyCopies, long occupancyCopies) { }

    @FunctionalInterface
    interface Search {
        int[] find(NavigationGrid grid, byte[] occupancy, Request request);
    }

    private record Key(Request request, long topologyRevision) { }

    private static final class Entry {
        final Key key;
        final FutureTask<int[]> task;
        final int submittedTick;
        final AtomicInteger finishedTick;
        int retryAtTick;

        Entry(Key key, FutureTask<int[]> task, int submittedTick,
              AtomicInteger finishedTick) {
            this.key = key;
            this.task = task;
            this.submittedTick = submittedTick;
            this.finishedTick = finishedTick;
        }
    }

    private final ThreadPoolExecutor executor;
    private final Search search;
    private final Map<Long, Entry> byMember = new HashMap<>();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong searchNanos = new AtomicLong();
    private final AtomicLong searchCpuNanos = new AtomicLong();
    private final AtomicLong maxSearchNanos = new AtomicLong();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong canceled = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong noPath = new AtomicLong();
    private final AtomicInteger currentTick = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    private final AtomicLong queueStarts = new AtomicLong();
    private final AtomicLongArray queueWaitHistogram =
            new AtomicLongArray(REQUEST_TTL_TICKS + 2);
    private final AtomicInteger maxQueueWaitAge = new AtomicInteger();
    private final long[] waitAgeHistogram = new long[REQUEST_TTL_TICKS + 2];
    private final long[] finishedWaitHistogram = new long[REQUEST_TTL_TICKS + 2];
    private long delivered;
    private int maxWaitAge;
    private int maxFinishedWaitAge;
    private int maxQueueDepth;
    private long snapshotNanos;
    private long maxSnapshotNanos;
    private long topologyCopies;
    private long occupancyCopies;
    private NavigationGrid topologySnapshot;
    private long topologyRevision = Long.MIN_VALUE;
    private byte[] occupancySnapshot;
    private int occupancyTick = Integer.MIN_VALUE;
    private boolean closed;

    public AsyncDefendTrackRoutes() {
        this(Math.max(1, Integer.getInteger(WORKERS_PROPERTY, 2)),
                Math.max(1, Integer.getInteger(QUEUE_CAPACITY_PROPERTY,
                MAX_QUEUED)), (grid, occupancy, request) -> {
            int[] path = GridPathfinder.findPathAsyncUnprofiled(grid,
                    request.startX(), request.startY(), request.goalX(),
                    request.goalY(), request.cardinalOnly(), occupancy);
            if (Paths.isEmpty(path) && (request.goalX() != request.anchorX()
                    || request.goalY() != request.anchorY())) {
                path = GridPathfinder.findPathAsyncUnprofiled(grid,
                        request.startX(), request.startY(),
                        request.anchorX(), request.anchorY(),
                        request.cardinalOnly(), occupancy);
            }
            return path;
        });
    }

    AsyncDefendTrackRoutes(int workers, int queueCapacity, Search search) {
        this.search = search;
        executor = new ThreadPoolExecutor(workers, workers, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
                task -> {
                    Thread thread = new Thread(task, "BattleSim-AsyncDefendTrack");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Retires jobs for dead or re-planned members that never poll again. */
    public synchronized void beginTick(int tick) {
        if (closed) return;
        currentTick.set(tick);
        List<Long> stale = new ArrayList<>();
        for (Map.Entry<Long, Entry> row : byMember.entrySet()) {
            if (tick - row.getValue().submittedTick > REQUEST_TTL_TICKS) {
                stale.add(row.getKey());
            }
        }
        for (long member : stale) cancel(member);
        if (occupancyTick != tick) occupancySnapshot = null;
    }

    /** Called only from a unit's own update, so the returned route can be installed there. */
    public synchronized Result pollOrSubmit(Request request, int tick,
                                             NavigationGrid liveGrid,
                                             byte[] liveOccupancy) {
        if (closed) return Result.WAITING;
        Key key = new Key(request, liveGrid.topologyRevision());
        Entry entry = byMember.get(request.member());
        if (entry != null && (!entry.key.equals(key)
                || tick - entry.submittedTick > REQUEST_TTL_TICKS)) {
            remove(entry);
            entry = null;
        }
        if (entry != null) {
            if (!entry.task.isDone()) return Result.WAITING;
            if (entry.retryAtTick > tick) return Result.WAITING;
            if (entry.retryAtTick != 0) {
                remove(entry);
                entry = null;
            } else {
                try {
                    int[] path = entry.task.get();
                    if (!Paths.isEmpty(path)) {
                        byMember.remove(request.member());
                        int age = Math.max(0, tick - entry.submittedTick);
                        waitAgeHistogram[Math.min(age, REQUEST_TTL_TICKS + 1)]++;
                        int finishedAge = Math.max(0,
                                tick - entry.finishedTick.get());
                        finishedWaitHistogram[Math.min(finishedAge,
                                REQUEST_TTL_TICKS + 1)]++;
                        delivered++;
                        maxWaitAge = Math.max(maxWaitAge, age);
                        maxFinishedWaitAge = Math.max(maxFinishedWaitAge,
                                finishedAge);
                        return new Result(true, path);
                    }
                } catch (Exception ignored) {
                    // A failed worker search has the same bounded retry as an
                    // unreachable result; it must not hot-loop on every tick.
                }
                noPath.incrementAndGet();
                entry.retryAtTick = tick + NO_PATH_RETRY_TICKS;
                return Result.WAITING;
            }
        }
        if (executor.getQueue().remainingCapacity() == 0
                && executor.getActiveCount() >= executor.getMaximumPoolSize()) {
            rejected.incrementAndGet();
            return Result.WAITING;
        }
        long snapshotStart = System.nanoTime();
        if (topologySnapshot == null || topologyRevision != key.topologyRevision()) {
            topologySnapshot = liveGrid.copyNavigationTopology();
            topologyRevision = key.topologyRevision();
            topologyCopies++;
        }
        if (occupancySnapshot == null || occupancyTick != tick) {
            occupancySnapshot = Arrays.copyOf(liveOccupancy, liveOccupancy.length);
            occupancyTick = tick;
            occupancyCopies++;
        }
        long snapshotCost = System.nanoTime() - snapshotStart;
        snapshotNanos += snapshotCost;
        maxSnapshotNanos = Math.max(maxSnapshotNanos, snapshotCost);
        NavigationGrid frozenGrid = topologySnapshot;
        byte[] frozenOccupancy = occupancySnapshot;
        AtomicInteger finishedTick = new AtomicInteger(tick);
        FutureTask<int[]> task = new FutureTask<>(() -> {
            int active = running.incrementAndGet();
            maxRunning.accumulateAndGet(active, Math::max);
            int queueAge = Math.max(0, currentTick.get() - tick);
            queueWaitHistogram.incrementAndGet(Math.min(queueAge,
                    REQUEST_TTL_TICKS + 1));
            queueStarts.incrementAndGet();
            maxQueueWaitAge.accumulateAndGet(queueAge, Math::max);
            long started = System.nanoTime();
            long cpuStarted = threadBean.isCurrentThreadCpuTimeSupported()
                    ? threadBean.getCurrentThreadCpuTime() : -1L;
            try {
                return search.find(frozenGrid, frozenOccupancy, request);
            } finally {
                long duration = System.nanoTime() - started;
                searchNanos.addAndGet(duration);
                maxSearchNanos.accumulateAndGet(duration, Math::max);
                if (cpuStarted >= 0L) {
                    searchCpuNanos.addAndGet(Math.max(0L,
                            threadBean.getCurrentThreadCpuTime() - cpuStarted));
                }
                completed.incrementAndGet();
                finishedTick.set(currentTick.get());
                running.decrementAndGet();
            }
        });
        try {
            executor.execute(task);
        } catch (RejectedExecutionException full) {
            rejected.incrementAndGet();
            return Result.WAITING;
        }
        byMember.put(request.member(), new Entry(key, task, tick, finishedTick));
        submitted.incrementAndGet();
        maxQueueDepth = Math.max(maxQueueDepth, executor.getQueue().size());
        return Result.WAITING;
    }

    public synchronized void cancel(long member) {
        Entry entry = byMember.get(member);
        if (entry != null) remove(entry);
    }

    private void remove(Entry entry) {
        byMember.remove(entry.key.request().member());
        if (!entry.task.isDone()) {
            entry.task.cancel(true);
            executor.remove(entry.task);
            canceled.incrementAndGet();
        }
    }

    public synchronized Metrics metrics() {
        int deliveryP95 = 0;
        long count = 0L;
        long threshold = (long) Math.ceil(delivered * 0.95);
        for (int age = 0; age < waitAgeHistogram.length; age++) {
            count += waitAgeHistogram[age];
            if (count >= threshold) {
                deliveryP95 = age;
                break;
            }
        }
        int finishedP95 = 0;
        count = 0L;
        for (int age = 0; age < finishedWaitHistogram.length; age++) {
            count += finishedWaitHistogram[age];
            if (count >= threshold) {
                finishedP95 = age;
                break;
            }
        }
        int queueP95 = 0;
        count = 0L;
        threshold = (long) Math.ceil(queueStarts.get() * 0.95);
        for (int age = 0; age < queueWaitHistogram.length(); age++) {
            count += queueWaitHistogram.get(age);
            if (count >= threshold) {
                queueP95 = age;
                break;
            }
        }
        return new Metrics(submitted.get(), completed.get(), searchNanos.get(),
                searchCpuNanos.get(), maxSearchNanos.get(),
                canceled.get(), rejected.get(), noPath.get(),
                executor.getQueue().size(), byMember.size(), maxQueueDepth,
                maxRunning.get(),
                queueP95, maxQueueWaitAge.get(), deliveryP95, maxWaitAge,
                finishedP95, maxFinishedWaitAge,
                snapshotNanos, maxSnapshotNanos,
                topologyCopies, occupancyCopies);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (Entry entry : byMember.values()) entry.task.cancel(true);
        byMember.clear();
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
