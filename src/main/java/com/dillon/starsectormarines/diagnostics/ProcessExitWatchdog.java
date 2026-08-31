package com.dillon.starsectormarines.diagnostics;

import org.apache.log4j.Logger;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Makes a silent process death legible after the fact.
 *
 * <p>A crash that leaves no stack trace in {@code starsector.log} has already
 * defeated every {@code catch} we could add, because there was nothing to
 * catch: the log simply stops mid-run. This watchdog answers the questions a
 * truncated log cannot, using three independent mechanisms that fail in
 * different ways on purpose.
 *
 * <ol>
 *   <li><b>Shutdown hook</b> — runs whenever the JVM shuts down in an orderly
 *       fashion, which includes {@code System.exit} from anywhere, the window
 *       close button, and a {@code taskkill} without {@code /F}. It reports the
 *       <em>reason</em>: the thread that requested the exit is blocked inside
 *       {@link Runtime#exit} while the hook runs, so its stack names the caller
 *       outright. When no such thread exists the exit came from outside the
 *       process and the banner says so.</li>
 *   <li><b>Default uncaught-exception handler</b> — catches an exception that
 *       killed a thread nobody was watching. It chains to whatever handler was
 *       already installed rather than replacing it.</li>
 *   <li><b>Liveness heartbeat</b> — one line per interval carrying uptime,
 *       heap, direct-buffer and thread counts, and where the game thread
 *       currently is. This is the mechanism that survives the cases the other
 *       two cannot see at all: a native crash in the graphics or audio driver,
 *       a display-driver reset, or a hard kill. None of those run a shutdown
 *       hook, so the <em>absence</em> of the banner after a heartbeat is itself
 *       the finding — it rules out every Java-level cause and dates the death
 *       to within one interval.</li>
 * </ol>
 *
 * <p>Read the tail of a crashed log as: heartbeat then banner means the process
 * chose to exit and the banner names who; heartbeat then nothing means the
 * process was destroyed underneath the JVM, and the last heartbeat's memory
 * figures and game-thread frame are the only surviving evidence.
 *
 * <p><strong>Check both channels before concluding the hook never ran.</strong>
 * The banner goes to the log and to {@code System.err}, which the launcher
 * captures to {@code build/starsector-run/console.log}. Absent from the log but
 * present there means logging died first, not that the process was killed --
 * a distinction the whole diagnosis turns on. Read it alongside the exit status
 * in {@code run-summary.txt}: a status of zero means something asked the process
 * to stop, so zero with no banner on either channel is a kill from outside the
 * JVM, while a crash arrives as an NTSTATUS instead.
 */
public final class ProcessExitWatchdog {

    private static final Logger LOG = Logger.getLogger(ProcessExitWatchdog.class);

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final long MEGABYTE = 1024L * 1024L;

    /** Deep enough to cross the game's frame dispatch into whatever called exit. */
    private static final int TRIGGER_STACK_DEPTH = 48;
    /** A heartbeat wants the area of code, not a full trace, on one line. */
    private static final int GAME_THREAD_STACK_DEPTH = 4;

    /** Frames that mean this thread is the one that asked the JVM to stop. */
    private static final String[] EXIT_TRIGGER_FRAMES = {
            "java.lang.Shutdown.exit",
            "java.lang.Shutdown.halt",
            "java.lang.Runtime.exit",
            "java.lang.Runtime.halt",
            "java.lang.System.exit",
    };

    private ProcessExitWatchdog() {}

    /**
     * Installs all three mechanisms. Call once, from the game thread, as early
     * in application load as possible — the calling thread is remembered as the
     * game thread that heartbeats report on.
     *
     * @param heartbeatSeconds interval between liveness lines; {@code <= 0}
     *                         installs the shutdown hook and exception handler
     *                         without the heartbeat thread
     */
    public static void install(long heartbeatSeconds) {
        if (!INSTALLED.compareAndSet(false, true)) return;

        Thread gameThread = Thread.currentThread();
        long startedNanos = System.nanoTime();

        installUncaughtExceptionHandler();
        installShutdownHook(startedNanos);
        if (heartbeatSeconds > 0L) {
            startHeartbeat(gameThread, startedNanos, heartbeatSeconds * 1000L);
        }

        LOG.info("ProcessExitWatchdog: armed on \"" + gameThread.getName()
                + "\"; heartbeat every " + heartbeatSeconds + "s");
    }

    private static void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                LOG.error("ProcessExitWatchdog: uncaught "
                        + error.getClass().getName() + " killed thread \""
                        + thread.getName() + "\"", error);
            } catch (Throwable loggingFailure) {
                // An OutOfMemoryError can take the logger down with it; stderr
                // is the only channel left that allocates almost nothing.
                System.err.println("ProcessExitWatchdog: uncaught " + error
                        + " on \"" + thread.getName() + "\"");
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static void installShutdownHook(long startedNanos) {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> reportShutdown(startedNanos), "MarinesProcessExitWatchdog"));
    }

    private static void reportShutdown(long startedNanos) {
        long uptimeMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        try {
            ThreadInfo[] threads = ManagementFactory.getThreadMXBean()
                    .dumpAllThreads(false, false, TRIGGER_STACK_DEPTH);
            // INFO, not ERROR: every ordinary quit runs this hook too, and a
            // banner that cries wolf on each of them stops being read. What
            // makes it evidence is its position at the tail of a truncated log.
            String banner = describeShutdown(
                    uptimeMillis, memorySummary(), snapshot(threads));
            LOG.info(banner);
            announce(banner);
        } catch (Throwable failure) {
            System.err.println("ProcessExitWatchdog: JVM shutting down after "
                    + uptimeMillis + "ms; could not describe it: " + failure);
        }
    }

    /**
     * Repeats the banner on {@code System.err}, which is a separate channel from
     * the log on purpose.
     *
     * <p>The whole diagnosis rests on one inference: banner absent means the hook
     * never ran, so the process was destroyed rather than asked to stop. That
     * only holds if a hook which <em>did</em> run is certain to leave a mark, and
     * a banner written solely through log4j is not — a closed appender, a
     * repository already shut down, or an exit racing the appender's own cleanup
     * all swallow it silently and look exactly like a hook that never ran.
     * {@code System.err} is owned by the JVM rather than by the logging
     * framework, and the launcher captures it, so a hook that runs says so
     * through a channel the log cannot lose.
     *
     * <p>Deliberately not a file: mod code has no filesystem access outside
     * Starsector's own settings API, and a shutdown hook is the worst possible
     * place to discover that.
     */
    private static void announce(String banner) {
        try {
            System.err.println(banner);
            System.err.flush();
        } catch (Throwable ignored) {
            // Nothing left to report through; the log line above is the fallback.
        }
    }

    /**
     * One thread as the banner needs it. {@link ThreadInfo} cannot be built
     * outside its own package, so the search and the formatting work over this
     * instead and stay exercisable without stopping a JVM.
     */
    record ThreadSnapshot(String name, long id, StackTraceElement[] frames) {}

    static ThreadSnapshot[] snapshot(ThreadInfo[] threads) {
        if (threads == null) return new ThreadSnapshot[0];
        List<ThreadSnapshot> out = new ArrayList<>(threads.length);
        for (ThreadInfo info : threads) {
            if (info == null) continue;
            out.add(new ThreadSnapshot(info.getThreadName(),
                    info.getThreadId(), info.getStackTrace()));
        }
        return out.toArray(new ThreadSnapshot[0]);
    }

    /** Formats the shutdown banner. */
    static String describeShutdown(long uptimeMillis, String memory,
                                   ThreadSnapshot[] threads) {
        ThreadSnapshot trigger = findExitTrigger(threads);
        StringBuilder out = new StringBuilder(4096);
        out.append("ProcessExitWatchdog: JVM shutdown after ")
                .append(uptimeMillis).append("ms; ").append(memory).append('\n');
        if (trigger == null) {
            out.append("  exitRequestedBy=<external> - no thread is inside ")
                    .append("Runtime.exit, so the shutdown came from outside ")
                    .append("the process (window close, SIGTERM, or the last ")
                    .append("non-daemon thread returning).\n");
            return out.toString();
        }
        out.append("  exitRequestedBy=\"").append(trigger.name())
                .append("\" id=").append(trigger.id()).append('\n');
        for (StackTraceElement frame : trigger.frames()) {
            out.append("    at ").append(frame).append('\n');
        }
        return out.toString();
    }

    /** The thread blocked inside {@code Runtime.exit}, or {@code null}. */
    static ThreadSnapshot findExitTrigger(ThreadSnapshot[] threads) {
        if (threads == null) return null;
        for (ThreadSnapshot info : threads) {
            if (info == null) continue;
            for (StackTraceElement frame : info.frames()) {
                String signature =
                        frame.getClassName() + "." + frame.getMethodName();
                for (String trigger : EXIT_TRIGGER_FRAMES) {
                    if (trigger.equals(signature)) return info;
                }
            }
        }
        return null;
    }

    private static void startHeartbeat(Thread gameThread, long startedNanos,
                                       long intervalMillis) {
        Thread beat = new Thread(
                () -> beatLoop(gameThread, startedNanos, intervalMillis),
                "MarinesProcessHeartbeat");
        beat.setDaemon(true);
        beat.start();
    }

    private static void beatLoop(Thread gameThread, long startedNanos,
                                 long intervalMillis) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        while (true) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException interrupted) {
                return;
            }
            try {
                long uptimeMillis =
                        (System.nanoTime() - startedNanos) / 1_000_000L;
                ThreadInfo game = threadBean.getThreadInfo(
                        gameThread.getId(), GAME_THREAD_STACK_DEPTH);
                StackTraceElement[] frames =
                        game == null ? null : game.getStackTrace();
                LOG.info(describeHeartbeat(uptimeMillis, memorySummary(),
                        threadBean.getThreadCount(), gameThread.getName(),
                        game == null ? null : game.getThreadState(),
                        frames == null || frames.length == 0 ? null : frames[0]));
            } catch (Throwable failure) {
                // A heartbeat that throws must not end the heartbeat.
                LOG.warn("ProcessExitWatchdog: heartbeat failed", failure);
            }
        }
    }

    /**
     * Formats one liveness line. Package-visible and pure: the whole value of a
     * heartbeat is that its last occurrence is readable, so the format is worth
     * pinning in a test.
     */
    static String describeHeartbeat(long uptimeMillis, String memory,
                                    int threadCount, String gameThreadName,
                                    Thread.State gameThreadState,
                                    StackTraceElement gameThreadFrame) {
        StringBuilder out = new StringBuilder(256);
        out.append("ProcessExitWatchdog: alive uptime=")
                .append(uptimeMillis / 1000L).append("s ")
                .append(memory)
                .append(" threads=").append(threadCount)
                .append(" gameThread=\"").append(gameThreadName).append('"');
        if (gameThreadState == null) {
            out.append(" state=<gone>");
            return out.toString();
        }
        out.append(" state=").append(gameThreadState);
        if (gameThreadFrame != null) out.append(" at ").append(gameThreadFrame);
        return out.toString();
    }

    /**
     * Heap, direct-buffer, and metaspace footprint on one line.
     *
     * <p>Direct buffers are called out because they are the one allocation the
     * heap figures cannot see: a leak there exhausts native memory while the
     * heap still looks healthy, and the resulting death need not surface as a
     * Java {@code OutOfMemoryError} at all.
     */
    static String memorySummary() {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        StringBuilder out = new StringBuilder(96);
        out.append("heap=").append(heapUsed / MEGABYTE).append('/')
                .append(runtime.maxMemory() / MEGABYTE).append("MB");
        appendDirectBuffers(out);
        try {
            MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean()
                    .getNonHeapMemoryUsage();
            out.append(" nonHeap=").append(nonHeap.getUsed() / MEGABYTE)
                    .append("MB");
        } catch (Throwable unavailable) {
            out.append(" nonHeap=?");
        }
        return out.toString();
    }

    private static void appendDirectBuffers(StringBuilder out) {
        try {
            List<BufferPoolMXBean> pools =
                    ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
            for (BufferPoolMXBean pool : pools) {
                if (!"direct".equals(pool.getName())) continue;
                out.append(" direct=").append(pool.getMemoryUsed() / MEGABYTE)
                        .append("MB/").append(pool.getCount()).append("buf");
                return;
            }
            out.append(" direct=<none>");
        } catch (Throwable unavailable) {
            out.append(" direct=?");
        }
    }
}
