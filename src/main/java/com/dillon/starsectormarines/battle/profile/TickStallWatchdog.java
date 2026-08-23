package com.dillon.starsectormarines.battle.profile;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Out-of-band watchdog for a {@code BattleSimulation.tick()} that never
 * returns. The ordinary {@link TickProfile} can describe only completed
 * ticks; this watcher runs on its own daemon thread and captures every JVM
 * thread while the stalled tick is still in place.
 *
 * <p>The production instance is process-wide rather than per battle. A game
 * session can create many battle simulations, and each per-battle watchdog
 * would otherwise strand a daemon thread after its battle ended. Active ticks
 * are keyed independently, so headless tests or tools may advance more than
 * one simulation concurrently without one disarming another.
 *
 * <p>At two seconds, a tick is already roughly sixty times over its 30 Hz
 * budget and Windows is close to declaring the game unresponsive. Each tick
 * dumps at most once. The text is written directly to the Starsector common
 * folder rather than waiting for the game thread or the completed-tick spike
 * dumper, neither of which can make progress during a deadlock.
 */
public final class TickStallWatchdog implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(TickStallWatchdog.class);

    public static final long STALL_THRESHOLD_MILLIS = 2_000L;
    private static final long POLL_INTERVAL_MILLIS = 100L;
    private static final int MAX_STACK_DEPTH = Integer.MAX_VALUE;

    private final long thresholdNanos;
    private final DumpSink dumpSink;
    private final ConcurrentHashMap<Long, ActiveTick> activeTicks =
            new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private final Thread watcherThread;
    private volatile boolean running = true;

    private TickStallWatchdog(long thresholdMillis, long pollIntervalMillis,
                              DumpSink dumpSink, String threadName) {
        if (thresholdMillis <= 0L) {
            throw new IllegalArgumentException("thresholdMillis must be positive");
        }
        if (pollIntervalMillis <= 0L) {
            throw new IllegalArgumentException("pollIntervalMillis must be positive");
        }
        if (dumpSink == null) throw new IllegalArgumentException("dumpSink is required");
        this.thresholdNanos = thresholdMillis * 1_000_000L;
        this.dumpSink = dumpSink;
        this.watcherThread = new Thread(
                () -> watchLoop(pollIntervalMillis), threadName);
        this.watcherThread.setDaemon(true);
        this.watcherThread.start();
    }

    /** Arms the process-wide watcher for one battle tick. Close on tick exit. */
    public static TickGuard watchTick(int tickIndex) {
        return Holder.INSTANCE.watch(tickIndex);
    }

    private TickGuard watch(int tickIndex) {
        long id = nextId.incrementAndGet();
        Thread owner = Thread.currentThread();
        ActiveTick active = new ActiveTick(id, tickIndex, System.nanoTime(),
                owner.getId(), owner.getName());
        activeTicks.put(id, active);
        return new TickGuard(this, active);
    }

    private void disarm(ActiveTick active) {
        activeTicks.remove(active.id, active);
    }

    private void watchLoop(long pollIntervalMillis) {
        while (running) {
            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException ignored) {
                if (!running) return;
            }
            long now = System.nanoTime();
            for (ActiveTick active : activeTicks.values()) {
                long elapsedNanos = now - active.startedNanos;
                if (elapsedNanos < thresholdNanos
                        || !active.dumped.compareAndSet(false, true)) {
                    continue;
                }
                String dump = captureThreadDump(active, elapsedNanos);
                String path = pathFor(active);
                try {
                    dumpSink.write(path, dump);
                } catch (Throwable failure) {
                    logFallback(path, dump, failure);
                }
            }
        }
    }

    static String captureThreadDump(ActiveTick active, long elapsedNanos) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] deadlockedIds = findDeadlockedThreads(threads);
        Set<Long> deadlocked = new HashSet<>();
        if (deadlockedIds != null) {
            for (long id : deadlockedIds) deadlocked.add(id);
        }

        boolean monitors = threads.isObjectMonitorUsageSupported();
        boolean synchronizers = threads.isSynchronizerUsageSupported();
        ThreadInfo[] snapshot = threads.dumpAllThreads(
                monitors, synchronizers, MAX_STACK_DEPTH);
        List<ThreadInfo> sorted = new ArrayList<>();
        for (ThreadInfo info : snapshot) {
            if (info != null) sorted.add(info);
        }
        sorted.sort(Comparator.comparing(ThreadInfo::getThreadName)
                .thenComparingLong(ThreadInfo::getThreadId));

        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("Starsector Marines battle-tick stall thread dump\n")
                .append("capturedAt=").append(Instant.now()).append('\n')
                .append("tickIndex=").append(active.tickIndex).append('\n')
                .append("elapsedMillis=")
                .append(elapsedNanos / 1_000_000L).append('\n')
                .append("tickOwner=\"").append(active.ownerName).append("\" id=")
                .append(active.ownerThreadId).append('\n');
        if (deadlocked.isEmpty()) {
            out.append("jvmDeadlockDetected=false\n");
        } else {
            out.append("jvmDeadlockDetected=true threadIds=")
                    .append(deadlocked).append('\n');
        }
        out.append('\n');

        for (ThreadInfo info : sorted) {
            appendThread(out, info, deadlocked.contains(info.getThreadId()));
        }
        return out.toString();
    }

    private static long[] findDeadlockedThreads(ThreadMXBean threads) {
        try {
            return threads.findDeadlockedThreads();
        } catch (UnsupportedOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static void appendThread(StringBuilder out, ThreadInfo info,
                                     boolean deadlocked) {
        out.append('"').append(info.getThreadName()).append('"')
                .append(" id=").append(info.getThreadId())
                .append(" state=").append(info.getThreadState());
        if (deadlocked) out.append(" DEADLOCKED");
        if (info.getLockName() != null) {
            out.append(" on ").append(info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            out.append(" owned by \"").append(info.getLockOwnerName())
                    .append("\" id=").append(info.getLockOwnerId());
        }
        if (info.isSuspended()) out.append(" (suspended)");
        if (info.isInNative()) out.append(" (in native)");
        out.append('\n');

        StackTraceElement[] stack = info.getStackTrace();
        MonitorInfo[] monitors = info.getLockedMonitors();
        for (int depth = 0; depth < stack.length; depth++) {
            out.append("    at ").append(stack[depth]).append('\n');
            for (MonitorInfo monitor : monitors) {
                if (monitor.getLockedStackDepth() == depth) {
                    out.append("      - locked ").append(monitor).append('\n');
                }
            }
        }
        LockInfo[] synchronizers = info.getLockedSynchronizers();
        if (synchronizers.length > 0) {
            out.append("    Locked ownable synchronizers:\n");
            for (LockInfo synchronizer : synchronizers) {
                out.append("      - ").append(synchronizer).append('\n');
            }
        }
        out.append('\n');
    }

    private static String pathFor(ActiveTick active) {
        return StarsectorMarinesModPlugin.MOD_ID
                + "/debug/thread_dump_stall_" + System.currentTimeMillis()
                + "_tick_" + active.tickIndex + ".txt";
    }

    private static void writeToCommon(String path, String dump) throws Exception {
        Global.getSettings().writeTextFileToCommon(path, dump);
        LOG.error("TickStallWatchdog: battle tick stalled; wrote JVM thread dump to "
                + "saves/common/" + path);
    }

    private static void logFallback(String path, String dump, Throwable failure) {
        try {
            LOG.error("TickStallWatchdog: could not write saves/common/" + path
                    + "; emitting JVM thread dump to the game log\n" + dump,
                    failure);
        } catch (Throwable loggingFailure) {
            System.err.println("TickStallWatchdog: dump persistence failed: "
                    + failure);
            System.err.println(dump);
        }
    }

    @Override
    public void close() {
        running = false;
        watcherThread.interrupt();
        activeTicks.clear();
    }

    /** Auto-closeable arm token; identity removal prevents one tick clearing another. */
    public static final class TickGuard implements AutoCloseable {
        private final TickStallWatchdog watchdog;
        private final ActiveTick active;
        private final AtomicBoolean closed = new AtomicBoolean();

        private TickGuard(TickStallWatchdog watchdog, ActiveTick active) {
            this.watchdog = watchdog;
            this.active = active;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) watchdog.disarm(active);
        }
    }

    static final class ActiveTick {
        final long id;
        final int tickIndex;
        final long startedNanos;
        final long ownerThreadId;
        final String ownerName;
        final AtomicBoolean dumped = new AtomicBoolean();

        ActiveTick(long id, int tickIndex, long startedNanos,
                   long ownerThreadId, String ownerName) {
            this.id = id;
            this.tickIndex = tickIndex;
            this.startedNanos = startedNanos;
            this.ownerThreadId = ownerThreadId;
            this.ownerName = ownerName;
        }
    }

    @FunctionalInterface
    interface DumpSink {
        void write(String path, String dump) throws Exception;
    }

    /** Test seam: short thresholds and an in-memory sink, with an owned daemon. */
    static TickStallWatchdog createForTest(long thresholdMillis,
                                           long pollIntervalMillis,
                                           DumpSink dumpSink) {
        return new TickStallWatchdog(thresholdMillis, pollIntervalMillis,
                dumpSink, "BattleSim-StallWatchdog-Test");
    }

    TickGuard watchForTest(int tickIndex) {
        return watch(tickIndex);
    }

    int activeTickCountForTest() {
        return activeTicks.size();
    }

    private static final class Holder {
        private static final TickStallWatchdog INSTANCE =
                new TickStallWatchdog(STALL_THRESHOLD_MILLIS,
                        POLL_INTERVAL_MILLIS,
                        TickStallWatchdog::writeToCommon,
                        "BattleSim-StallWatchdog");
    }
}
