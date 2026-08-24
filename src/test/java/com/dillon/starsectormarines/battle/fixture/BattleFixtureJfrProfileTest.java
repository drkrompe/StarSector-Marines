package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.evacuation.SwarmPressureBehavior;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import jdk.jfr.Category;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in profiler harness; run with {@code gradlew profileBattleFixture}.
 * A captured fixture can be selected with
 * {@code -Dbattle.fixture.path=path/to/tick_profile.json}; tune preparation
 * and measurement with {@code -Dbattle.profile.warmupTicks=600},
 * {@code -Dbattle.profile.prerollTicks=300},
 * {@code -Dbattle.profile.sliceTicks=300}, and
 * {@code -Dbattle.profile.minimumMillis=5000}.
 */
@Tag("battle-profile")
class BattleFixtureJfrProfileTest {

    private static final int DEFAULT_WARMUP_TICKS = 300;
    private static final int DEFAULT_PREROLL_TICKS = 300;
    private static final int DEFAULT_SLICE_TICKS = 300;
    private static final long DEFAULT_MINIMUM_MILLIS = 5_000L;
    private static final Duration SAMPLE_PERIOD = Duration.ofMillis(10L);
    private static final String RUN_EVENT_NAME =
            "com.dillon.starsectormarines.BattleFixtureProfileRun";
    private static final String PRODUCTION_BATTLE_PREFIX =
            "com.dillon.starsectormarines.battle.";
    private static final String FIXTURE_TEST_PREFIX =
            "com.dillon.starsectormarines.battle.fixture.";

    @Test
    void recordsSelectedFixtureThroughTheRealSimulationLoop() throws Exception {
        BattleFixture fixture = BattleFixtureTestSupport.loadSelectedFixture();
        int warmupTicks = Integer.getInteger(
                "battle.profile.warmupTicks", DEFAULT_WARMUP_TICKS);
        int prerollTicks = Integer.getInteger(
                "battle.profile.prerollTicks", DEFAULT_PREROLL_TICKS);
        int sliceTicks = Integer.getInteger(
                "battle.profile.sliceTicks", DEFAULT_SLICE_TICKS);
        long minimumMillis = Long.getLong(
                "battle.profile.minimumMillis", DEFAULT_MINIMUM_MILLIS);
        if (warmupTicks < 0) {
            throw new IllegalArgumentException(
                    "battle.profile.warmupTicks must be non-negative");
        }
        if (minimumMillis < 1L) {
            throw new IllegalArgumentException(
                    "battle.profile.minimumMillis must be positive");
        }
        if (prerollTicks < 0) {
            throw new IllegalArgumentException(
                    "battle.profile.prerollTicks must be non-negative");
        }
        if (sliceTicks < 1) {
            throw new IllegalArgumentException(
                    "battle.profile.sliceTicks must be positive");
        }

        // Warm classloading/JIT on sacrificial reconstructions. A short scene
        // is rebuilt as needed rather than silently turning remaining advances
        // into no-ops after completion.
        runTicks(fixture, warmupTicks);

        Path outputDir = Path.of(System.getProperty(
                "battle.profile.outputDir", "build/profiles"));
        Files.createDirectories(outputDir);
        Path recordingPath = outputDir.resolve(
                "battle-fixture-" + System.currentTimeMillis() + ".jfr")
                .toAbsolutePath();

        ProfileRunEvent boundary = new ProfileRunEvent();
        boundary.fixtureKind = fixture.kind();
        boundary.fixtureJson = BattleFixtureJson.toJson(fixture).toString();
        boundary.fixtureSource = System.getProperty(
                "battle.fixture.path", "checked-in-default");
        boundary.warmupTicks = warmupTicks;
        boundary.prerollTicks = prerollTicks;
        boundary.sliceTicks = sliceTicks;
        boundary.minimumMillis = minimumMillis;
        boundary.minimumParallelUnits =
                UnitUpdateSystem.configuredMinimumParallelUnits();
        boundary.unitUpdateParallelism =
                UnitUpdateSystem.configuredPoolParallelism();
        boundary.minimumSharedGoalUnits =
                SwarmPressureBehavior.configuredMinimumSharedGoalUnits();

        RunStats measured;
        // Construct the ordinary measured sim before recording so map/scenario
        // setup cannot masquerade as a tick-loop hotspot.
        try (BattleSimulation firstSimulation = prepareSimulation(fixture, prerollTicks);
             Recording recording = new Recording(
                Configuration.getConfiguration("profile"))) {
            recording.setName("battle-fixture");
            recording.enable(ProfileRunEvent.class);
            recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
            recording.start();
            boundary.begin();
            measured = runFixedSlices(
                    fixture, firstSimulation, recording,
                    prerollTicks, sliceTicks, minimumMillis);
            boundary.measuredTicks = measured.ticks();
            boundary.replayCount = measured.replays();
            boundary.activeTickNanos = measured.activeTickNanos();
            boundary.firstSliceLiveUnits = measured.firstSliceLiveUnits();
            boundary.minimumSliceStartLiveUnits = measured.minimumSliceStartLiveUnits();
            boundary.maximumSliceStartLiveUnits = measured.maximumSliceStartLiveUnits();
            boundary.pathfindCalls = measured.pathfindCalls();
            boundary.pathfindNanos = measured.pathfindNanos();
            boundary.swarmPathfindCalls = measured.swarmPathfindCalls();
            boundary.swarmPathfindNanos = measured.swarmPathfindNanos();
            boundary.sharedFieldBuilds = measured.sharedFieldBuilds();
            boundary.sharedFieldBuildNanos = measured.sharedFieldBuildNanos();
            boundary.sharedFieldExtractions = measured.sharedFieldExtractions();
            boundary.sharedFieldExtractionNanos =
                    measured.sharedFieldExtractionNanos();
            boundary.occupancyPathfindCalls = measured.occupancyPathfindCalls();
            boundary.uniquePathfindGoals = measured.uniquePathfindGoals();
            boundary.uniquePathfindRequests = measured.uniquePathfindRequests();
            boundary.maximumGoalFanIn = measured.maximumGoalFanIn();
            boundary.end();
            boundary.commit();
            recording.stop();
            recording.dump(recordingPath);
        }

        assertTrue(measured.ticks() > 0,
                "the recording must include real simulation ticks");
        assertTrue(Files.size(recordingPath) > 0L);
        verifyRecording(recordingPath, fixture.kind(), minimumMillis);
        System.out.println("Battle fixture JFR: " + recordingPath
                + " (" + measured.ticks() + " ticks across "
                + measured.replays() + " fixed slices; "
                + Math.round(measured.ticksPerSecond()) + " active ticks/s; "
                + measured.firstSliceLiveUnits() + " live units at slice start; "
                + measured.pathfindCallsPerTick() + " pathfinds/tick; "
                + measured.uniquePathfindGoalsPerTick() + " unique goals/tick; "
                + measured.sharedFieldBuildsPerTick() + " shared fields/tick; "
                + measured.maximumGoalFanIn() + " max same-goal fan-in)");
    }

    private static void verifyRecording(
            Path recordingPath, String expectedKind, long minimumMillis) throws Exception {
        RecordedEvent boundary = null;
        boolean hasProductionSample = false;
        try (RecordingFile recording = new RecordingFile(recordingPath)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (RUN_EVENT_NAME.equals(event.getEventType().getName())) {
                    boundary = event;
                } else if ("jdk.ExecutionSample".equals(
                        event.getEventType().getName())
                        && hasProductionBattleFrame(event.getStackTrace())) {
                    hasProductionSample = true;
                }
            }
        }
        assertNotNull(boundary,
                "JFR should contain the measured-region boundary");
        assertEquals(expectedKind, boundary.getString("fixtureKind"));
        assertTrue(boundary.getLong("measuredTicks") > 0L);
        assertTrue(boundary.getLong("activeTickNanos")
                        >= minimumMillis * 1_000_000L,
                "active tick time should satisfy the requested minimum");
        assertTrue(boundary.getDuration().toMillis() >= minimumMillis);
        assertTrue(hasProductionSample,
                "JFR should sample production battle code inside the measured run");
    }

    private static boolean hasProductionBattleFrame(RecordedStackTrace trace) {
        if (trace == null) return false;
        for (RecordedFrame frame : trace.getFrames()) {
            String className = frame.getMethod().getType().getName();
            if (className.startsWith(PRODUCTION_BATTLE_PREFIX)
                    && !className.startsWith(FIXTURE_TEST_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static void runTicks(BattleFixture fixture, int requestedTicks) {
        int completedTicks = 0;
        while (completedTicks < requestedTicks) {
            int beforeReplay = completedTicks;
            try (BattleSimulation sim = fixture.build()) {
                while (completedTicks < requestedTicks && !sim.isComplete()) {
                    advanceOneTick(sim);
                    completedTicks++;
                }
            }
            if (completedTicks == beforeReplay) {
                throw new IllegalStateException(
                        "fixture completes before its first tick");
            }
        }
    }

    private static BattleSimulation prepareSimulation(
            BattleFixture fixture, int prerollTicks) {
        BattleSimulation sim = fixture.build();
        try {
            for (int tick = 0; tick < prerollTicks; tick++) {
                if (sim.isComplete()) {
                    throw new IllegalStateException(
                            "fixture completed during profile pre-roll at tick " + tick);
                }
                advanceOneTick(sim);
            }
            if (sim.isComplete()) {
                throw new IllegalStateException(
                        "fixture completes before the measured profile slice");
            }
            return sim;
        } catch (RuntimeException | Error failure) {
            sim.close();
            throw failure;
        }
    }

    private static RunStats runFixedSlices(
            BattleFixture fixture, BattleSimulation firstSimulation,
            Recording recording, int prerollTicks, int sliceTicks,
            long minimumMillis) {
        long minimumNanos = minimumMillis * 1_000_000L;
        long activeTickNanos = 0L;
        long ticks = 0L;
        int replays = 1;
        int firstSliceLiveUnits = firstSimulation.liveUnitCount();
        int minimumSliceStartLiveUnits = firstSliceLiveUnits;
        int maximumSliceStartLiveUnits = firstSliceLiveUnits;
        long pathfindCalls = 0L;
        long pathfindNanos = 0L;
        long swarmPathfindCalls = 0L;
        long swarmPathfindNanos = 0L;
        long sharedFieldBuilds = 0L;
        long sharedFieldBuildNanos = 0L;
        long sharedFieldExtractions = 0L;
        long sharedFieldExtractionNanos = 0L;
        long occupancyPathfindCalls = 0L;
        long uniquePathfindGoals = 0L;
        long uniquePathfindRequests = 0L;
        int maximumGoalFanIn = 0;
        BattleSimulation sim = firstSimulation;
        try {
            while (activeTickNanos < minimumNanos) {
                int liveUnits = sim.liveUnitCount();
                minimumSliceStartLiveUnits = Math.min(
                        minimumSliceStartLiveUnits, liveUnits);
                maximumSliceStartLiveUnits = Math.max(
                        maximumSliceStartLiveUnits, liveUnits);
                for (int tick = 0; tick < sliceTicks; tick++) {
                    if (sim.isComplete()) {
                        throw new IllegalStateException(
                                "fixture completed inside measured profile slice at tick "
                                        + (prerollTicks + tick));
                    }
                    long tickStart = System.nanoTime();
                    advanceOneTick(sim);
                    activeTickNanos += System.nanoTime() - tickStart;
                    TickInnerProfile innerProfile = sim.getTickInnerProfile();
                    pathfindCalls += innerProfile.countOf(
                            TickInnerProfile.Bucket.PATHFIND);
                    pathfindNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.PATHFIND);
                    swarmPathfindCalls += innerProfile.countOf(
                            TickInnerProfile.Bucket.SWARM_PATHFIND);
                    swarmPathfindNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.SWARM_PATHFIND);
                    sharedFieldBuilds += innerProfile.countOf(
                            TickInnerProfile.Bucket.SHARED_PATH_FIELD_BUILD);
                    sharedFieldBuildNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.SHARED_PATH_FIELD_BUILD);
                    sharedFieldExtractions += innerProfile.countOf(
                            TickInnerProfile.Bucket.SHARED_PATH_FIELD_EXTRACT);
                    sharedFieldExtractionNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.SHARED_PATH_FIELD_EXTRACT);
                    occupancyPathfindCalls +=
                            innerProfile.occupancyPathfindRequestCount();
                    uniquePathfindGoals +=
                            innerProfile.uniquePathfindGoalCount();
                    uniquePathfindRequests +=
                            innerProfile.uniquePathfindRequestCount();
                    maximumGoalFanIn = Math.max(maximumGoalFanIn,
                            innerProfile.maximumPathfindGoalFanIn());
                    ticks++;
                }

                if (activeTickNanos < minimumNanos) {
                    // Every repetition profiles the same logical battle age.
                    // Pause sampling while reconstructing and pre-rolling so
                    // lifecycle work cannot masquerade as tick-loop cost.
                    recording.disable("jdk.ExecutionSample");
                    sim.close();
                    sim = prepareSimulation(fixture, prerollTicks);
                    replays++;
                    recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
                }
            }
        } finally {
            if (sim != firstSimulation) {
                recording.disable("jdk.ExecutionSample");
                sim.close();
            }
        }
        return new RunStats(ticks, replays, activeTickNanos,
                firstSliceLiveUnits, minimumSliceStartLiveUnits,
                maximumSliceStartLiveUnits, pathfindCalls, pathfindNanos,
                swarmPathfindCalls, swarmPathfindNanos,
                sharedFieldBuilds, sharedFieldBuildNanos,
                sharedFieldExtractions, sharedFieldExtractionNanos,
                occupancyPathfindCalls, uniquePathfindGoals,
                uniquePathfindRequests, maximumGoalFanIn);
    }

    private static void advanceOneTick(BattleSimulation sim) {
        int before = sim.simTickIndex;
        sim.advance(BattleSimulation.TICK_DT);
        if (sim.simTickIndex != before + 1) {
            throw new IllegalStateException(
                    "fixture did not advance exactly one requested tick");
        }
    }

    private record RunStats(
            long ticks, int replays, long activeTickNanos,
            int firstSliceLiveUnits, int minimumSliceStartLiveUnits,
            int maximumSliceStartLiveUnits,
            long pathfindCalls, long pathfindNanos,
            long swarmPathfindCalls, long swarmPathfindNanos,
            long sharedFieldBuilds, long sharedFieldBuildNanos,
            long sharedFieldExtractions, long sharedFieldExtractionNanos,
            long occupancyPathfindCalls, long uniquePathfindGoals,
            long uniquePathfindRequests, int maximumGoalFanIn) {
        double ticksPerSecond() {
            return ticks * 1_000_000_000.0 / activeTickNanos;
        }

        double pathfindCallsPerTick() {
            return Math.round(pathfindCalls * 100.0 / ticks) / 100.0;
        }

        double uniquePathfindGoalsPerTick() {
            return Math.round(uniquePathfindGoals * 100.0 / ticks) / 100.0;
        }

        double sharedFieldBuildsPerTick() {
            return Math.round(sharedFieldBuilds * 100.0 / ticks) / 100.0;
        }
    }

    @Name(RUN_EVENT_NAME)
    @Label("Battle fixture profile run")
    @Category("Starsector Marines")
    static final class ProfileRunEvent extends Event {
        @Label("Fixture kind")
        String fixtureKind;
        @Label("Fixture JSON")
        String fixtureJson;
        @Label("Fixture source")
        String fixtureSource;
        @Label("Warmup ticks")
        int warmupTicks;
        @Label("Pre-roll ticks per slice")
        int prerollTicks;
        @Label("Measured ticks per slice")
        int sliceTicks;
        @Label("Requested minimum milliseconds")
        long minimumMillis;
        @Label("Measured ticks")
        long measuredTicks;
        @Label("Replay count")
        int replayCount;
        @Label("Active tick nanoseconds")
        long activeTickNanos;
        @Label("Minimum units for parallel dispatch")
        int minimumParallelUnits;
        @Label("Unit-update pool parallelism")
        int unitUpdateParallelism;
        @Label("Minimum units for shared swarm goal fields")
        int minimumSharedGoalUnits;
        @Label("First slice live units")
        int firstSliceLiveUnits;
        @Label("Minimum live units at slice start")
        int minimumSliceStartLiveUnits;
        @Label("Maximum live units at slice start")
        int maximumSliceStartLiveUnits;
        @Label("Pathfind calls")
        long pathfindCalls;
        @Label("Accumulated pathfind nanoseconds")
        long pathfindNanos;
        @Label("Swarm pathfind calls")
        long swarmPathfindCalls;
        @Label("Accumulated swarm pathfind nanoseconds")
        long swarmPathfindNanos;
        @Label("Shared reverse-field builds")
        long sharedFieldBuilds;
        @Label("Shared reverse-field build nanoseconds")
        long sharedFieldBuildNanos;
        @Label("Shared reverse-field extractions")
        long sharedFieldExtractions;
        @Label("Shared reverse-field extraction nanoseconds")
        long sharedFieldExtractionNanos;
        @Label("Occupancy-aware pathfind calls")
        long occupancyPathfindCalls;
        @Label("Sum of per-tick unique pathfind goals")
        long uniquePathfindGoals;
        @Label("Sum of per-tick unique start-goal requests")
        long uniquePathfindRequests;
        @Label("Maximum same-goal fan-in in one tick")
        int maximumGoalFanIn;
    }
}
