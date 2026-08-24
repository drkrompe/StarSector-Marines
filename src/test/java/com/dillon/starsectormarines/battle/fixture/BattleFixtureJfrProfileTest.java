package com.dillon.starsectormarines.battle.fixture;

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
 * and measurement with {@code -Dbattle.profile.warmupTicks=600} and
 * {@code -Dbattle.profile.minimumMillis=5000}.
 */
@Tag("battle-profile")
class BattleFixtureJfrProfileTest {

    private static final int DEFAULT_WARMUP_TICKS = 300;
    private static final long DEFAULT_MINIMUM_MILLIS = 3_000L;
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
        boundary.minimumMillis = minimumMillis;

        RunStats measured;
        // Construct the ordinary measured sim before recording so map/scenario
        // setup cannot masquerade as a tick-loop hotspot.
        try (BattleSimulation firstSimulation = fixture.build();
             Recording recording = new Recording(
                Configuration.getConfiguration("profile"))) {
            recording.setName("battle-fixture");
            recording.enable(ProfileRunEvent.class);
            recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
            recording.start();
            boundary.begin();
            measured = runForDuration(
                    fixture, firstSimulation, recording, minimumMillis);
            boundary.measuredTicks = measured.ticks();
            boundary.replayCount = measured.replays();
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
                + measured.replays() + " replays)");
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

    private static RunStats runForDuration(
            BattleFixture fixture, BattleSimulation firstSimulation,
            Recording recording, long minimumMillis) {
        long deadline = System.nanoTime() + minimumMillis * 1_000_000L;
        long ticks = 0L;
        int replays = 1;
        BattleSimulation sim = firstSimulation;
        try {
            while (System.nanoTime() < deadline) {
                if (sim.isComplete()) {
                    // Short fixtures may need reconstruction to fill the sample
                    // window. Pause execution sampling around setup/teardown so
                    // those frames do not pollute the tick-loop hot methods.
                    recording.disable("jdk.ExecutionSample");
                    sim.close();
                    sim = fixture.build();
                    replays++;
                    recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
                    if (sim.isComplete()) {
                        throw new IllegalStateException(
                                "fixture completes before its first tick");
                    }
                }
                advanceOneTick(sim);
                ticks++;
            }
        } finally {
            if (sim != firstSimulation) {
                recording.disable("jdk.ExecutionSample");
                sim.close();
            }
        }
        return new RunStats(ticks, replays);
    }

    private static void advanceOneTick(BattleSimulation sim) {
        int before = sim.simTickIndex;
        sim.advance(BattleSimulation.TICK_DT);
        if (sim.simTickIndex != before + 1) {
            throw new IllegalStateException(
                    "fixture did not advance exactly one requested tick");
        }
    }

    private record RunStats(long ticks, int replays) {}

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
        @Label("Requested minimum milliseconds")
        long minimumMillis;
        @Label("Measured ticks")
        long measuredTicks;
        @Label("Replay count")
        int replayCount;
    }
}
