package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickProfile;
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
                SharedGoalPolicy.configuredMinimumSharedGoalUnits();

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
            boundary.squadFieldBuilds = measured.squadFieldBuilds();
            boundary.squadFieldBuildNanos = measured.squadFieldBuildNanos();
            boundary.squadFieldExtractions = measured.squadFieldExtractions();
            boundary.squadFieldExtractionNanos =
                    measured.squadFieldExtractionNanos();
            boundary.squadFieldFallbacks = measured.squadFieldFallbacks();
            boundary.squadFieldFallbackNanos = measured.squadFieldFallbackNanos();
            boundary.squadFieldCorridorCells = measured.squadFieldCorridorCells();
            boundary.squadFieldSettledCells = measured.squadFieldSettledCells();
            boundary.occupancyPathfindCalls = measured.occupancyPathfindCalls();
            boundary.uniquePathfindGoals = measured.uniquePathfindGoals();
            boundary.uniquePathfindRequests = measured.uniquePathfindRequests();
            boundary.maximumGoalFanIn = measured.maximumGoalFanIn();
            boundary.commanderPulseCount = measured.commanderPulseCount();
            boundary.commanderNanos = measured.commanderNanos();
            boundary.maximumCommanderNanos = measured.maximumCommanderNanos();
            boundary.goapReplanNanos = measured.goapReplanNanos();
            boundary.maximumGoapReplanNanos = measured.maximumGoapReplanNanos();
            boundary.goapSquadReplanNanos = measured.goapSquadReplanNanos();
            boundary.maximumGoapSquadReplanNanos = measured.maximumGoapSquadReplanNanos();
            boundary.goapRouteCollectionNanos = measured.goapRouteCollectionNanos();
            boundary.maximumGoapRouteCollectionNanos = measured.maximumGoapRouteCollectionNanos();
            boundary.goapRoutePreparationNanos = measured.goapRoutePreparationNanos();
            boundary.maximumGoapRoutePreparationNanos = measured.maximumGoapRoutePreparationNanos();
            boundary.commanderSyncNanos = measured.commanderSyncNanos();
            boundary.commanderTopologyLookupNanos = measured.commanderTopologyLookupNanos();
            boundary.commanderTopologyRebuildCount =
                    measured.commanderTopologyRebuildCount();
            boundary.commanderTopologyRebuildNanos =
                    measured.commanderTopologyRebuildNanos();
            boundary.commanderFrameNanos = measured.commanderFrameNanos();
            boundary.commanderPlanNanos = measured.commanderPlanNanos();
            boundary.commanderCommitNanos = measured.commanderCommitNanos();
            boundary.influenceRefreshCount = measured.influenceRefreshCount();
            boundary.influenceTopologyLookupNanos =
                    measured.influenceTopologyLookupNanos();
            boundary.influenceTopologyRebuildCount =
                    measured.influenceTopologyRebuildCount();
            boundary.influenceTopologyRebuildNanos =
                    measured.influenceTopologyRebuildNanos();
            boundary.influencePerspectiveBuildCount =
                    measured.influencePerspectiveBuildCount();
            boundary.influenceSourceNanos = measured.influenceSourceNanos();
            boundary.influencePropagationNanos = measured.influencePropagationNanos();
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
                + measured.squadFieldBuildsPerTick() + " squad fields/tick; "
                + measured.squadFieldFallbacksPerTick() + " squad fallbacks/tick; "
                + measured.averageSquadSettledCells() + " settled cells/squad field; "
                + measured.maximumGoalFanIn() + " max same-goal fan-in; "
                + measured.commanderPulseCount() + " commander pulses; "
                + measured.commanderAverageMillis() + " ms avg / "
                + measured.commanderMaximumMillis() + " ms max commander; "
                + measured.influenceAverageMillis() + " ms/influence refresh; "
                + measured.goapMaximumMillis() + " ms max GOAP; "
                + "independent GOAP-stage maxima: "
                + measured.goapSquadReplanMaximumMillis() + " ms replan, "
                + measured.goapRouteCollectionMaximumMillis() + " ms collect, "
                + measured.goapRoutePreparationMaximumMillis() + " ms prepare)");
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
        long pulseCount = boundary.getLong("commanderPulseCount");
        if (pulseCount > 0L) {
            long stageNanos = boundary.getLong("commanderSyncNanos")
                    + boundary.getLong("commanderTopologyLookupNanos")
                    + boundary.getLong("commanderFrameNanos")
                    + boundary.getLong("commanderPlanNanos")
                    + boundary.getLong("commanderCommitNanos");
            assertTrue(stageNanos > 0L);
            assertTrue(stageNanos <= boundary.getLong("commanderNanos"),
                    "commander stages must fit inside pulse-only commander wall time");
            assertTrue(boundary.getLong("influenceRefreshCount") > 0L,
                    "commander fixtures should retain normalized influence evidence");
        }
        assertTrue(boundary.getLong("goapSquadReplanNanos") > 0L);
        assertTrue(boundary.getLong("goapRouteCollectionNanos") > 0L);
        assertTrue(boundary.getLong("goapRoutePreparationNanos") > 0L);
        assertTrue(boundary.getLong("goapSquadReplanNanos")
                        + boundary.getLong("goapRouteCollectionNanos")
                        + boundary.getLong("goapRoutePreparationNanos")
                        <= boundary.getLong("goapReplanNanos"),
                "GOAP stages must fit inside GOAP phase wall time");
        // Event duration includes slice reconstruction and pre-roll orchestration;
        // activeTickNanos above is the measured-work denominator.
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
        long squadFieldBuilds = 0L;
        long squadFieldBuildNanos = 0L;
        long squadFieldExtractions = 0L;
        long squadFieldExtractionNanos = 0L;
        long squadFieldFallbacks = 0L;
        long squadFieldFallbackNanos = 0L;
        long squadFieldCorridorCells = 0L;
        long squadFieldSettledCells = 0L;
        long occupancyPathfindCalls = 0L;
        long uniquePathfindGoals = 0L;
        long uniquePathfindRequests = 0L;
        int maximumGoalFanIn = 0;
        long commanderPulseCount = 0L;
        long commanderNanos = 0L;
        long maximumCommanderNanos = 0L;
        long goapReplanNanos = 0L;
        long maximumGoapReplanNanos = 0L;
        long goapSquadReplanNanos = 0L;
        long maximumGoapSquadReplanNanos = 0L;
        long goapRouteCollectionNanos = 0L;
        long maximumGoapRouteCollectionNanos = 0L;
        long goapRoutePreparationNanos = 0L;
        long maximumGoapRoutePreparationNanos = 0L;
        long commanderSyncNanos = 0L;
        long commanderTopologyLookupNanos = 0L;
        long commanderTopologyRebuildCount = 0L;
        long commanderTopologyRebuildNanos = 0L;
        long commanderFrameNanos = 0L;
        long commanderPlanNanos = 0L;
        long commanderCommitNanos = 0L;
        long influenceRefreshCount = 0L;
        long influenceTopologyLookupNanos = 0L;
        long influenceTopologyRebuildCount = 0L;
        long influenceTopologyRebuildNanos = 0L;
        long influencePerspectiveBuildCount = 0L;
        long influenceSourceNanos = 0L;
        long influencePropagationNanos = 0L;
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
                    squadFieldBuilds += innerProfile.countOf(
                            TickInnerProfile.Bucket.SQUAD_PATH_FIELD_BUILD);
                    squadFieldBuildNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.SQUAD_PATH_FIELD_BUILD);
                    squadFieldExtractions += innerProfile.countOf(
                            TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT);
                    squadFieldExtractionNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT);
                    squadFieldFallbacks += innerProfile.countOf(
                            TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK);
                    squadFieldFallbackNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK);
                    squadFieldCorridorCells += innerProfile.squadRouteCorridorCells();
                    squadFieldSettledCells += innerProfile.squadRouteSettledCells();
                    occupancyPathfindCalls +=
                            innerProfile.occupancyPathfindRequestCount();
                    uniquePathfindGoals +=
                            innerProfile.uniquePathfindGoalCount();
                    uniquePathfindRequests +=
                            innerProfile.uniquePathfindRequestCount();
                    maximumGoalFanIn = Math.max(maximumGoalFanIn,
                            innerProfile.maximumPathfindGoalFanIn());
                    long commanderTickNanos = sim.getTickProfile().lastTickNanos(
                            TickProfile.Phase.COMMANDER);
                    boolean commanderPulse = innerProfile.countOf(
                            TickInnerProfile.Bucket.COMMANDER_PULSE) > 0;
                    if (commanderPulse) {
                        commanderPulseCount++;
                        commanderNanos += commanderTickNanos;
                        maximumCommanderNanos = Math.max(
                                maximumCommanderNanos, commanderTickNanos);
                    }
                    long goapTickNanos = sim.getTickProfile().lastTickNanos(
                            TickProfile.Phase.GOAP_REPLAN);
                    goapReplanNanos += goapTickNanos;
                    maximumGoapReplanNanos = Math.max(
                            maximumGoapReplanNanos, goapTickNanos);
                    long squadReplanTickNanos = innerProfile.nanosOf(
                            TickInnerProfile.Bucket.GOAP_SQUAD_REPLAN);
                    goapSquadReplanNanos += squadReplanTickNanos;
                    maximumGoapSquadReplanNanos = Math.max(
                            maximumGoapSquadReplanNanos, squadReplanTickNanos);
                    long routeCollectionTickNanos = innerProfile.nanosOf(
                            TickInnerProfile.Bucket.GOAP_ROUTE_COLLECTION);
                    goapRouteCollectionNanos += routeCollectionTickNanos;
                    maximumGoapRouteCollectionNanos = Math.max(
                            maximumGoapRouteCollectionNanos, routeCollectionTickNanos);
                    long routePreparationTickNanos = innerProfile.nanosOf(
                            TickInnerProfile.Bucket.GOAP_ROUTE_PREPARATION);
                    goapRoutePreparationNanos += routePreparationTickNanos;
                    maximumGoapRoutePreparationNanos = Math.max(
                            maximumGoapRoutePreparationNanos, routePreparationTickNanos);
                    commanderSyncNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.COMMANDER_SYNC);
                    commanderTopologyLookupNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_LOOKUP);
                    commanderTopologyRebuildCount += innerProfile.countOf(
                            TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_REBUILD);
                    commanderTopologyRebuildNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_REBUILD);
                    commanderFrameNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.COMMANDER_FRAME);
                    commanderPlanNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.COMMANDER_PLAN);
                    commanderCommitNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.COMMANDER_COMMIT);
                    influenceRefreshCount += innerProfile.countOf(
                            TickInnerProfile.Bucket.INFLUENCE_TOPOLOGY_LOOKUP);
                    influenceTopologyLookupNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.INFLUENCE_TOPOLOGY_LOOKUP);
                    influenceTopologyRebuildCount += innerProfile.countOf(
                            TickInnerProfile.Bucket.INFLUENCE_TOPOLOGY_REBUILD);
                    influenceTopologyRebuildNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.INFLUENCE_TOPOLOGY_REBUILD);
                    influencePerspectiveBuildCount += innerProfile.countOf(
                            TickInnerProfile.Bucket.INFLUENCE_SOURCES);
                    influenceSourceNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.INFLUENCE_SOURCES);
                    influencePropagationNanos += innerProfile.nanosOf(
                            TickInnerProfile.Bucket.INFLUENCE_PROPAGATE);
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
                squadFieldBuilds, squadFieldBuildNanos,
                squadFieldExtractions, squadFieldExtractionNanos,
                squadFieldFallbacks, squadFieldFallbackNanos,
                squadFieldCorridorCells, squadFieldSettledCells,
                occupancyPathfindCalls, uniquePathfindGoals,
                uniquePathfindRequests, maximumGoalFanIn,
                commanderPulseCount, commanderNanos, maximumCommanderNanos,
                goapReplanNanos, maximumGoapReplanNanos,
                goapSquadReplanNanos, maximumGoapSquadReplanNanos,
                goapRouteCollectionNanos, maximumGoapRouteCollectionNanos,
                goapRoutePreparationNanos, maximumGoapRoutePreparationNanos,
                commanderSyncNanos, commanderTopologyLookupNanos,
                commanderTopologyRebuildCount, commanderTopologyRebuildNanos,
                commanderFrameNanos, commanderPlanNanos,
                commanderCommitNanos, influenceRefreshCount,
                influenceTopologyLookupNanos, influenceTopologyRebuildCount,
                influenceTopologyRebuildNanos, influencePerspectiveBuildCount,
                influenceSourceNanos, influencePropagationNanos);
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
            long squadFieldBuilds, long squadFieldBuildNanos,
            long squadFieldExtractions, long squadFieldExtractionNanos,
            long squadFieldFallbacks, long squadFieldFallbackNanos,
            long squadFieldCorridorCells, long squadFieldSettledCells,
            long occupancyPathfindCalls, long uniquePathfindGoals,
            long uniquePathfindRequests, int maximumGoalFanIn,
            long commanderPulseCount, long commanderNanos,
            long maximumCommanderNanos, long goapReplanNanos,
            long maximumGoapReplanNanos,
            long goapSquadReplanNanos, long maximumGoapSquadReplanNanos,
            long goapRouteCollectionNanos, long maximumGoapRouteCollectionNanos,
            long goapRoutePreparationNanos, long maximumGoapRoutePreparationNanos,
            long commanderSyncNanos,
            long commanderTopologyLookupNanos,
            long commanderTopologyRebuildCount,
            long commanderTopologyRebuildNanos, long commanderFrameNanos,
            long commanderPlanNanos, long commanderCommitNanos,
            long influenceRefreshCount, long influenceTopologyLookupNanos,
            long influenceTopologyRebuildCount,
            long influenceTopologyRebuildNanos,
            long influencePerspectiveBuildCount, long influenceSourceNanos,
            long influencePropagationNanos) {
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

        double squadFieldBuildsPerTick() {
            return Math.round(squadFieldBuilds * 100.0 / ticks) / 100.0;
        }

        double squadFieldFallbacksPerTick() {
            return Math.round(squadFieldFallbacks * 100.0 / ticks) / 100.0;
        }

        long averageSquadSettledCells() {
            return squadFieldBuilds == 0L
                    ? 0L : Math.round((double) squadFieldSettledCells
                    / squadFieldBuilds);
        }

        double commanderAverageMillis() {
            return commanderPulseCount > 0
                    ? Math.round(commanderNanos / commanderPulseCount / 10_000.0) / 100.0
                    : 0.0;
        }

        double commanderMaximumMillis() {
            return Math.round(maximumCommanderNanos / 10_000.0) / 100.0;
        }

        double influenceAverageMillis() {
            if (influenceRefreshCount == 0L) return 0.0;
            long nanos = influenceTopologyLookupNanos
                    + influenceSourceNanos + influencePropagationNanos;
            return Math.round(nanos / influenceRefreshCount / 10_000.0) / 100.0;
        }

        double goapMaximumMillis() {
            return Math.round(maximumGoapReplanNanos / 10_000.0) / 100.0;
        }

        double goapSquadReplanMaximumMillis() {
            return Math.round(maximumGoapSquadReplanNanos / 10_000.0) / 100.0;
        }

        double goapRouteCollectionMaximumMillis() {
            return Math.round(maximumGoapRouteCollectionNanos / 10_000.0) / 100.0;
        }

        double goapRoutePreparationMaximumMillis() {
            return Math.round(maximumGoapRoutePreparationNanos / 10_000.0) / 100.0;
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
        @Label("Squad corridor-field builds")
        long squadFieldBuilds;
        @Label("Squad corridor-field build nanoseconds")
        long squadFieldBuildNanos;
        @Label("Squad corridor-field extractions")
        long squadFieldExtractions;
        @Label("Squad corridor-field extraction nanoseconds")
        long squadFieldExtractionNanos;
        @Label("Squad corridor-field fallbacks")
        long squadFieldFallbacks;
        @Label("Squad corridor-field fallback nanoseconds")
        long squadFieldFallbackNanos;
        @Label("Squad corridor cells considered")
        long squadFieldCorridorCells;
        @Label("Squad corridor cells settled")
        long squadFieldSettledCells;
        @Label("Occupancy-aware pathfind calls")
        long occupancyPathfindCalls;
        @Label("Sum of per-tick unique pathfind goals")
        long uniquePathfindGoals;
        @Label("Sum of per-tick unique start-goal requests")
        long uniquePathfindRequests;
        @Label("Maximum same-goal fan-in in one tick")
        int maximumGoalFanIn;
        @Label("Commander pulse count")
        long commanderPulseCount;
        @Label("Accumulated commander nanoseconds")
        long commanderNanos;
        @Label("Maximum commander tick nanoseconds")
        long maximumCommanderNanos;
        @Label("Accumulated GOAP replan nanoseconds")
        long goapReplanNanos;
        @Label("Maximum GOAP replan tick nanoseconds")
        long maximumGoapReplanNanos;
        @Label("Accumulated squad replan nanoseconds")
        long goapSquadReplanNanos;
        @Label("Maximum squad replan tick nanoseconds")
        long maximumGoapSquadReplanNanos;
        @Label("Accumulated squad route collection nanoseconds")
        long goapRouteCollectionNanos;
        @Label("Maximum squad route collection tick nanoseconds")
        long maximumGoapRouteCollectionNanos;
        @Label("Accumulated squad route preparation nanoseconds")
        long goapRoutePreparationNanos;
        @Label("Maximum squad route preparation tick nanoseconds")
        long maximumGoapRoutePreparationNanos;
        @Label("Commander assignment synchronization nanoseconds")
        long commanderSyncNanos;
        @Label("Commander topology lookup nanoseconds")
        long commanderTopologyLookupNanos;
        @Label("Commander topology rebuild count")
        long commanderTopologyRebuildCount;
        @Label("Commander topology rebuild nanoseconds")
        long commanderTopologyRebuildNanos;
        @Label("Commander frame freeze nanoseconds")
        long commanderFrameNanos;
        @Label("Commander planning nanoseconds")
        long commanderPlanNanos;
        @Label("Commander commit nanoseconds")
        long commanderCommitNanos;
        @Label("Influence refresh count")
        long influenceRefreshCount;
        @Label("Influence topology lookup nanoseconds")
        long influenceTopologyLookupNanos;
        @Label("Influence topology rebuild count")
        long influenceTopologyRebuildCount;
        @Label("Influence topology rebuild nanoseconds")
        long influenceTopologyRebuildNanos;
        @Label("Influence perspective build count")
        long influencePerspectiveBuildCount;
        @Label("Influence source aggregation nanoseconds")
        long influenceSourceNanos;
        @Label("Influence propagation nanoseconds")
        long influencePropagationNanos;
    }
}
