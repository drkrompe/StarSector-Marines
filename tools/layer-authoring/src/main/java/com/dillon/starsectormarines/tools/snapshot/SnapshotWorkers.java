package com.dillon.starsectormarines.tools.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The worker threads snapshot evidence spends its pixel work on, so the simulation thread only simulates.
 *
 * <p>Two pools, because their work nests differently. {@link #raster()} takes the horizontal bands of one frame
 * being drawn; its caller waits on them before the simulation moves, so it must never be queued behind anything
 * slow. {@link #background()} takes work nobody waits on until the end — compressing a finished frame, encoding a
 * finished GIF. Neither pool's tasks submit to their own pool, so neither can deadlock on itself.
 *
 * <p>Sized by {@code -Dsnapshot.threads=N}, default half the machine's processors (the convention every
 * side-by-side evidence task here uses); {@code 1} is the serial control. Nothing here changes what is drawn: work
 * is split, never reordered — a frame's bands are disjoint and a list's frames are read back in submission order.
 */
public final class SnapshotWorkers {

    /** System property selecting the worker count. */
    public static final String PROPERTY = "snapshot.threads";

    private static final int THREADS = Math.max(1, Integer.getInteger(PROPERTY,
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));

    private static volatile ExecutorService raster;
    private static volatile ExecutorService background;

    private SnapshotWorkers() { }

    /** How many workers each pool has; 1 means evidence runs serially on its caller. */
    public static int threads() {
        return THREADS;
    }

    public static ExecutorService raster() {
        ExecutorService pool = raster;
        if (pool == null) {
            synchronized (SnapshotWorkers.class) {
                if (raster == null) raster = pool("snapshot-raster");
                pool = raster;
            }
        }
        return pool;
    }

    public static ExecutorService background() {
        ExecutorService pool = background;
        if (pool == null) {
            synchronized (SnapshotWorkers.class) {
                if (background == null) background = pool("snapshot-background");
                pool = background;
            }
        }
        return pool;
    }

    /**
     * Runs every task, the first on the caller and the rest on {@link #raster()}, and returns once all have finished.
     * A failure in any task is rethrown here, after the others have finished, so no task outlives the call.
     */
    public static void runAll(List<? extends Callable<?>> tasks) {
        if (tasks.isEmpty()) return;
        List<Future<?>> futures = new ArrayList<>(tasks.size() - 1);
        for (int i = 1; i < tasks.size(); i++) futures.add(raster().submit(tasks.get(i)));
        Throwable failure = null;
        try {
            tasks.get(0).call();
        } catch (Throwable thrown) {
            failure = thrown;
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException thrown) {
                if (failure == null) failure = thrown.getCause(); else failure.addSuppressed(thrown.getCause());
            } catch (InterruptedException thrown) {
                Thread.currentThread().interrupt();
                if (failure == null) failure = thrown;
            }
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
    }

    private static ExecutorService pool(String name) {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(THREADS, runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
