package com.dillon.starsectormarines.battle.command.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a commander-evidence harness's replays side by side, and holds the
 * two decisions every harness has to make the same way: how many times a
 * fixture is replayed, and what a difference between two replays reads like.
 *
 * <p>The default repeat is one. Replaying every fixture twice to end in an
 * equals() was most of what a balance run cost and none of it was evidence
 * about the battle; {@code simDeterminism} is where two replicas meet, on one
 * fixture and a bounded tick budget.
 *
 * <p>The default parallelism is also one, for a reason the determinism check
 * itself turned up — see {@link #parallelism()}.
 */
final class EvidenceFanOut {

    static final String PARALLELISM_PROPERTY = "commander.evidence.parallelism";
    static final String REPEAT_PROPERTY = "commander.evidence.repeat";

    private EvidenceFanOut() { }

    /**
     * Runs every job on a bounded pool and returns their results in job order.
     *
     * <p>The first job that failed — first by position, so the report is the
     * same whichever thread lost — propagates as itself, and the rest are
     * cancelled.
     */
    static <R> List<R> run(List<Callable<R>> jobs) throws Exception {
        if (jobs.isEmpty()) return List.of();
        AtomicInteger names = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(jobs.size(), parallelism()), runnable -> {
                    Thread worker = new Thread(runnable,
                            "evidence-" + names.getAndIncrement());
                    worker.setDaemon(true);
                    return worker;
                });
        try {
            List<Future<R>> futures = new ArrayList<>(jobs.size());
            for (Callable<R> job : jobs) futures.add(pool.submit(job));
            List<R> results = new ArrayList<>(jobs.size());
            try {
                for (Future<R> future : futures) results.add(future.get());
            } catch (ExecutionException failure) {
                for (Future<R> future : futures) future.cancel(true);
                throw propagate(failure.getCause());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * How many threads replays may occupy; never fewer than one.
     *
     * <p>The default is one, and not because a replay is unsafe to hand its
     * own thread — the per-thread scratch the sim keeps is all
     * {@link ThreadLocal}. It is because {@code LosCache} is not per
     * simulation: its enable flag is one static volatile that every sim
     * raises at its own tick top and lowers at its own tick end, and
     * {@code clearAll} sweeps every thread's cache in the process rather than
     * the caller's own. Two simulations therefore turn each other's
     * line-of-sight cache off and empty it mid-tick, and the first run of
     * {@code simDeterminism} caught it: two replicas of the same Conquest
     * fixture agreed for three hundred ticks and then recorded the same
     * compound-presence change one tick apart. Raise it deliberately with
     * {@code -Pparallelism=N} once a cache belongs to the sim that owns it.
     */
    static int parallelism() {
        String configured = System.getProperty(PARALLELISM_PROPERTY, "").trim();
        int value = configured.isBlank() ? 1 : Integer.parseInt(configured);
        return Math.max(1, value);
    }

    /** How many times each fixture is replayed; one unless asked otherwise. */
    static int repeat() {
        String configured = System.getProperty(REPEAT_PROPERTY, "").trim();
        int value = configured.isBlank() ? 1 : Integer.parseInt(configured);
        if (value < 1) {
            throw new IllegalArgumentException(
                    REPEAT_PROPERTY + " must be at least 1");
        }
        return value;
    }

    /** How a summary says what it replayed, for one replay and for many. */
    static String replayWording(int repeat) {
        return repeat < 2 ? "replayed once"
                : "replayed " + repeat
                + " times with byte-identical traces";
    }

    /** Asserts every replica of one fixture produced the same command events. */
    static void assertReplaysByteStable(String runId, List<String> traces) {
        assertReplaysByteStable(runId, "command events", traces);
    }

    /**
     * Asserts every replica of one fixture produced the same {@code evidence},
     * naming the first line and character at which one of them diverged.
     */
    static void assertReplaysByteStable(String runId, String evidence,
                                        List<String> replicas) {
        if (replicas.size() < 2) return;
        String first = replicas.get(0);
        for (int replica = 1; replica < replicas.size(); replica++) {
            assertByteStable(first, replicas.get(replica), evidence,
                    runId, replica);
        }
    }

    private static void assertByteStable(String first, String other,
                                         String evidence, String runId,
                                         int replica) {
        if (first.equals(other)) return;
        String[] firstLines = first.split("\\R", -1);
        String[] otherLines = other.split("\\R", -1);
        int shared = Math.min(firstLines.length, otherLines.length);
        int line = 0;
        while (line < shared && firstLines[line].equals(otherLines[line])) line++;
        String firstValue = line < firstLines.length
                ? firstLines[line] : "<missing>";
        String otherValue = line < otherLines.length
                ? otherLines[line] : "<missing>";
        int character = firstDifferingCharacter(firstValue, otherValue);
        throw new AssertionError("same fixture must produce byte-stable "
                + evidence + ": " + runId + "; replay 0 and replay " + replica
                + " first differ at line " + (line + 1) + ", character "
                + (character + 1)
                + "\nfirst: " + excerpt(firstValue, character)
                + "\nreplay " + replica + ": " + excerpt(otherValue, character));
    }

    private static int firstDifferingCharacter(String first, String second) {
        int shared = Math.min(first.length(), second.length());
        int character = 0;
        while (character < shared
                && first.charAt(character) == second.charAt(character)) character++;
        return character;
    }

    private static String excerpt(String value, int difference) {
        int radius = 500;
        int start = Math.max(0, difference - radius);
        int end = Math.min(value.length(), difference + radius);
        return (start > 0 ? "..." : "") + value.substring(start, end)
                + (end < value.length() ? "..." : "")
                + " [" + value.length() + " chars]";
    }

    private static RuntimeException propagate(Throwable cause) throws Exception {
        if (cause instanceof Error error) throw error;
        if (cause instanceof Exception failure) throw failure;
        throw new IllegalStateException("evidence replay failed", cause);
    }
}
