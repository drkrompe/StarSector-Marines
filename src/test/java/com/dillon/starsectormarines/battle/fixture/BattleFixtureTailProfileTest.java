package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.nav.AsyncDefendTrackRoutes;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadReplanSystem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in, production-scheduler Conquest tail evidence. One fresh battle runs
 * continuously through late commander pulses; no replay slices can silently
 * omit the expensive battle age. Sleep is outside the timed tick so async
 * route workers see approximately live 30 Hz pacing without charging idle
 * time to the simulation. Timings are diagnostic, never a cross-host gate.
 */
@Tag("battle-tail-profile")
class BattleFixtureTailProfileTest {
    private static final int DEFAULT_TOTAL_TICKS = 1_200;
    private static final int DEFAULT_WARMUP_TICKS = 300;
    private static final int DEFAULT_TOP_TICKS = 20;
    private static final long FRAME_BUDGET_NANOS =
            Math.round(BattleSimulation.TICK_DT * 1_000_000_000.0);

    private record TickSample(int tick, long totalNanos, long[] phases,
                              TickInnerProfile.Snapshot inner,
                              SquadReplanSystem.TickDiagnostics replans,
                              UnitUpdateSystem.TickDiagnostics unitUpdate,
                              GcCounters gcBefore, GcCounters gcAfter,
                              AsyncDefendTrackRoutes.Metrics routesBefore,
                              AsyncDefendTrackRoutes.Metrics routesAfter,
                              int units, int squads) { }

    private record GcCounters(long collections, long collectionMillis) { }

    @Test
    void capturesProductionConquestTailWithCodeLevelContext() throws Exception {
        String fixturePath = System.getProperty("battle.fixture.path", "").trim();
        assertFalse(fixturePath.isEmpty(), "profileConquestTail needs a selected fixture");
        byte[] fixtureBytes = Files.readAllBytes(Path.of(fixturePath));
        BattleFixture fixture = BattleFixtureTestSupport.loadSelectedFixture();
        assertEquals("CONQUEST", fixture.kind());

        int totalTicks = Integer.getInteger("battle.tail.totalTicks", DEFAULT_TOTAL_TICKS);
        int warmupTicks = Integer.getInteger("battle.tail.warmupTicks",
                DEFAULT_WARMUP_TICKS);
        int topLimit = Integer.getInteger("battle.tail.topTicks", DEFAULT_TOP_TICKS);
        int paceMillis = Integer.getInteger("battle.tail.paceMillis", 33);
        assertTrue(totalTicks > warmupTicks && warmupTicks >= 0);
        assertTrue(topLimit > 0 && paceMillis >= 0);
        assertEquals("true", System.getProperty(
                AsyncDefendTrackRoutes.ENABLED_PROPERTY),
                "tail evidence must use production async routing");
        assertTrue(UnitUpdateSystem.configuredPoolParallelism() > 1,
                "tail evidence requires production parallel workers");

        long[] durations = new long[totalTicks - warmupTicks];
        PriorityQueue<TickSample> worst = new PriorityQueue<>(Comparator
                .comparingLong(TickSample::totalNanos)
                .thenComparingInt(TickSample::tick));
        TickSample[] worstByPhase = new TickSample[TickProfile.Phase.VALUES.length];
        int commanderPulses = 0;
        int maximumUnits = 0;
        int minimumUnits = Integer.MAX_VALUE;
        long totalReplans = 0;
        AsyncDefendTrackRoutes.Metrics firstRoutes;
        AsyncDefendTrackRoutes.Metrics finalRoutes;
        long nextDeadline = System.nanoTime();
        long paceNanos = paceMillis * 1_000_000L;
        List<GarbageCollectorMXBean> garbageCollectors =
                ManagementFactory.getGarbageCollectorMXBeans();

        try (BattleSimulation sim = fixture.build()) {
            assertNotNull(sim.asyncDefendTrackRoutes());
            sim.getSquadReplanSystem().setDiagnosticsEnabled(true);
            sim.getUnitUpdateSystem().setDiagnosticsEnabled(true);
            assertTrue(UnitUpdateSystem.configuredMinimumParallelUnits()
                    <= sim.liveUnitCount(),
                    "selected fixture is too small for parallel unit updates");
            firstRoutes = sim.asyncDefendTrackRoutes().metrics();
            for (int attempt = 0; attempt < totalTicks; attempt++) {
                assertFalse(sim.isComplete(), "fixture ended before tick " + (attempt + 1));
                int beforeTick = sim.simTickIndex;
                AsyncDefendTrackRoutes.Metrics routesBefore =
                        sim.asyncDefendTrackRoutes().metrics();
                GcCounters gcBefore = gcCounters(garbageCollectors);
                long started = System.nanoTime();
                sim.advance(BattleSimulation.TICK_DT);
                long duration = System.nanoTime() - started;
                GcCounters gcAfter = gcCounters(garbageCollectors);
                assertEquals(beforeTick + 1, sim.simTickIndex,
                        "one advance must execute exactly one fixed tick");
                AsyncDefendTrackRoutes.Metrics routesAfter =
                        sim.asyncDefendTrackRoutes().metrics();

                if (sim.simTickIndex > warmupTicks) {
                    int sampleIndex = sim.simTickIndex - warmupTicks - 1;
                    durations[sampleIndex] = duration;
                    int units = sim.liveUnitCount();
                    maximumUnits = Math.max(maximumUnits, units);
                    minimumUnits = Math.min(minimumUnits, units);
                    TickInnerProfile inner = sim.getTickInnerProfile();
                    if (inner.countOf(TickInnerProfile.Bucket.COMMANDER_PULSE) > 0) {
                        commanderPulses++;
                    }
                    totalReplans += sim.getSquadReplanSystem()
                            .lastTickDiagnostics().replanCount();
                    long[] phases = new long[TickProfile.Phase.VALUES.length];
                    boolean phaseRecord = false;
                    for (TickProfile.Phase phase : TickProfile.Phase.VALUES) {
                        int index = phase.ordinal();
                        phases[index] = sim.getTickProfile().lastTickNanos(phase);
                        if (worstByPhase[index] == null
                                || phases[index] > worstByPhase[index].phases()[index]) {
                            phaseRecord = true;
                        }
                    }
                    if (phaseRecord || worst.size() < topLimit
                            || duration > worst.peek().totalNanos()) {
                        TickSample sample = new TickSample(sim.simTickIndex, duration,
                                phases, inner.snapshot(),
                                sim.getSquadReplanSystem().lastTickDiagnostics(),
                                sim.getUnitUpdateSystem().lastTickDiagnostics(),
                                gcBefore, gcAfter,
                                routesBefore, routesAfter, units,
                                sim.getSquads().size());
                        for (TickProfile.Phase phase : TickProfile.Phase.VALUES) {
                            int index = phase.ordinal();
                            if (worstByPhase[index] == null
                                    || phases[index] > worstByPhase[index].phases()[index]) {
                                worstByPhase[index] = sample;
                            }
                        }
                        if (worst.size() < topLimit
                                || duration > worst.peek().totalNanos()) worst.add(sample);
                        if (worst.size() > topLimit) worst.remove();
                    }
                }

                if (paceNanos > 0L) {
                    nextDeadline += paceNanos;
                    long remaining = nextDeadline - System.nanoTime();
                    if (remaining > 0L) LockSupport.parkNanos(remaining);
                    else nextDeadline = System.nanoTime();
                }
            }
            assertEquals(totalTicks, sim.simTickIndex);
            finalRoutes = sim.asyncDefendTrackRoutes().metrics();
        }

        assertTrue(commanderPulses > 0, "late-age run missed commander pulses");
        assertTrue(totalReplans > 0, "replan diagnostics captured no replans");
        assertTrue(maximumUnits >= UnitUpdateSystem.configuredMinimumParallelUnits());
        JSONObject report = report(fixturePath, fixtureBytes, totalTicks,
                warmupTicks, paceMillis, durations, worst, worstByPhase, commanderPulses,
                totalReplans, minimumUnits, maximumUnits, firstRoutes,
                finalRoutes);
        assertEquals(Math.min(topLimit, durations.length),
                report.getJSONArray("worstTicks").length());
        assertEquals(TickProfile.Phase.VALUES.length,
                report.getJSONArray("worstByPhase").length());
        Path outputDir = Path.of(System.getProperty("battle.tail.outputDir",
                "build/reports/performance/conquest-tail"));
        Files.createDirectories(outputDir);
        Path temporary = Files.createTempFile(outputDir, "tail-", ".tmp");
        Files.writeString(temporary, report.toString(2), StandardCharsets.UTF_8);
        Path destination = outputDir.resolve("summary.json");
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("Conquest tail: %d measured ticks, max %.2f ms, "
                        + "P99 %.2f ms, %d over 33.3 ms, %d commander pulses; %s%n",
                durations.length, report.getDouble("maxMs"),
                report.getDouble("p99Ms"), report.getInt("overBudgetTicks"),
                commanderPulses, destination.toAbsolutePath());
    }

    private static JSONObject report(String fixturePath, byte[] fixtureBytes,
                                     int totalTicks, int warmupTicks,
                                     int paceMillis, long[] durations,
                                     PriorityQueue<TickSample> worst,
                                     TickSample[] worstByPhase,
                                     int commanderPulses, long totalReplans,
                                     int minimumUnits, int maximumUnits,
                                     AsyncDefendTrackRoutes.Metrics firstRoutes,
                                     AsyncDefendTrackRoutes.Metrics finalRoutes)
            throws Exception {
        long[] sorted = durations.clone();
        Arrays.sort(sorted);
        List<TickSample> ordered = new ArrayList<>(worst);
        ordered.sort(Comparator.comparingLong(TickSample::totalNanos).reversed()
                .thenComparingInt(TickSample::tick));
        JSONArray spikes = new JSONArray();
        for (TickSample sample : ordered) spikes.put(tickJson(sample));
        JSONArray phaseSpikes = new JSONArray();
        for (TickProfile.Phase phase : TickProfile.Phase.VALUES) {
            TickSample sample = worstByPhase[phase.ordinal()];
            if (sample == null) continue;
            phaseSpikes.put(new JSONObject().put("phase", phase.name())
                    .put("phaseMs", millis(sample.phases()[phase.ordinal()]))
                    .put("tick", tickJson(sample)));
        }
        JSONObject report = new JSONObject();
        report.put("schemaVersion", 1);
        report.put("fixturePath", fixturePath);
        report.put("fixtureSha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(fixtureBytes)));
        report.put("totalTicks", totalTicks);
        report.put("firstMeasuredTick", warmupTicks + 1);
        report.put("measuredTicks", durations.length);
        report.put("paceMillis", paceMillis);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("javaVendor", System.getProperty("java.vendor"));
        report.put("osName", System.getProperty("os.name"));
        report.put("osArch", System.getProperty("os.arch"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("renderSink", "none");
        report.put("asyncDefendTrack", true);
        report.put("minimumParallelUnits",
                UnitUpdateSystem.configuredMinimumParallelUnits());
        report.put("unitUpdateParallelism",
                UnitUpdateSystem.configuredPoolParallelism());
        report.put("minimumUnits", minimumUnits);
        report.put("maximumUnits", maximumUnits);
        report.put("commanderPulses", commanderPulses);
        report.put("replannedSquads", totalReplans);
        report.put("medianMs", millis(percentile(sorted, 0.50)));
        report.put("p95Ms", millis(percentile(sorted, 0.95)));
        report.put("p99Ms", millis(percentile(sorted, 0.99)));
        report.put("maxMs", millis(sorted[sorted.length - 1]));
        report.put("overBudgetTicks", Arrays.stream(durations)
                .filter(ns -> ns >= FRAME_BUDGET_NANOS).count());
        report.put("over50MsTicks", Arrays.stream(durations)
                .filter(ns -> ns >= 50_000_000L).count());
        report.put("asyncRouteSubmitted", finalRoutes.submitted()
                - firstRoutes.submitted());
        report.put("asyncRouteCompleted", finalRoutes.completed()
                - firstRoutes.completed());
        report.put("asyncRouteRejected", finalRoutes.rejected()
                - firstRoutes.rejected());
        report.put("timingSemantics", "tick and phase values are wall time; "
                + "inner behavior/action and sampled unit values aggregate parallel worker time "
                + "and overlap enclosing phases; GC counters may include concurrent or "
                + "background collection; async route search runs off tick");
        report.put("worstTicks", spikes);
        report.put("worstByPhase", phaseSpikes);
        return report;
    }

    static long percentile(long[] sorted, double quantile) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * quantile) - 1)];
    }

    private static GcCounters gcCounters(List<GarbageCollectorMXBean> collectors) {
        long count = 0L;
        long millis = 0L;
        for (GarbageCollectorMXBean collector : collectors) {
            count += Math.max(0L, collector.getCollectionCount());
            millis += Math.max(0L, collector.getCollectionTime());
        }
        return new GcCounters(count, millis);
    }

    private static JSONObject tickJson(TickSample sample) throws Exception {
        JSONObject tick = new JSONObject();
        tick.put("tick", sample.tick());
        tick.put("totalMs", millis(sample.totalNanos()));
        tick.put("units", sample.units());
        tick.put("squads", sample.squads());
        tick.put("gcCollections", sample.gcAfter().collections()
                - sample.gcBefore().collections());
        tick.put("gcCollectionMs", sample.gcAfter().collectionMillis()
                - sample.gcBefore().collectionMillis());
        JSONArray phases = new JSONArray();
        long phaseSum = 0L;
        for (TickProfile.Phase phase : TickProfile.Phase.VALUES) {
            long nanos = sample.phases()[phase.ordinal()];
            phaseSum += nanos;
            if (nanos > 0L) phases.put(new JSONObject()
                    .put("name", phase.name()).put("ms", millis(nanos)));
        }
        tick.put("phases", phases);
        tick.put("outsidePhaseMs", millis(sample.totalNanos() - phaseSum));
        JSONArray inner = new JSONArray();
        for (TickInnerProfile.Bucket bucket : TickInnerProfile.Bucket.VALUES) {
            int count = sample.inner().countOf(bucket);
            if (count == 0) continue;
            inner.put(new JSONObject().put("name", bucket.name())
                    .put("sumMs", millis(sample.inner().nanosOf(bucket)))
                    .put("count", count));
        }
        tick.put("inner", inner);
        JSONArray actions = new JSONArray();
        List<Map.Entry<String, long[]>> sortedActions =
                new ArrayList<>(sample.inner().actions.entrySet());
        sortedActions.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, long[]> entry : sortedActions) {
            actions.put(new JSONObject().put("name", entry.getKey())
                    .put("sumMs", millis(entry.getValue()[0]))
                    .put("count", entry.getValue()[1]));
        }
        tick.put("actions", actions);
        tick.put("flatPathfindExpandedNodes",
                sample.inner().pathfindExpandedNodes);
        JSONArray paths = new JSONArray();
        for (TickInnerProfile.PathSearch search : sample.inner().slowPathSearches) {
            paths.put(new JSONObject().put("ms", millis(search.nanos()))
                    .put("startX", search.startX()).put("startY", search.startY())
                    .put("goalX", search.goalX()).put("goalY", search.goalY())
                    .put("expandedNodes", search.expandedNodes())
                    .put("usesOccupancy", search.usesOccupancy())
                    .put("found", search.found()));
        }
        tick.put("slowFlatPathSearches", paths);

        SquadReplanSystem.TickDiagnostics replans = sample.replans();
        JSONObject goap = new JSONObject().put("squadsVisited", replans.squadCount())
                .put("replanned", replans.replanCount());
        JSONArray slowSquads = new JSONArray();
        for (SquadReplanSystem.SquadSample squad : replans.slowestSquads()) {
            slowSquads.put(new JSONObject().put("squadId", squad.squadId())
                    .put("kind", squad.kind().name())
                    .put("ms", millis(squad.durationNanos()))
                    .put("replanned", squad.replanned())
                    .put("planMissing", squad.planMissing())
                    .put("planComplete", squad.planComplete())
                    .put("periodic", squad.periodic())
                    .put("memberChanged", squad.memberChanged())
                    .put("assignmentChanged", squad.assignmentChanged())
                    .put("contactChanged", squad.contactChanged())
                    .put("incomingFireStarted", squad.incomingFireStarted())
                    .put("moraleChanged", squad.moraleChanged()));
        }
        goap.put("slowestSquads", slowSquads);
        tick.put("squadReplan", goap);

        UnitUpdateSystem.TickDiagnostics units = sample.unitUpdate();
        JSONArray slowUnits = new JSONArray();
        for (UnitUpdateSystem.UnitSample unit : units.slowestUnits()) {
            slowUnits.put(new JSONObject().put("entityId", unit.entityId())
                    .put("role", unit.role().name())
                    .put("ms", millis(unit.durationNanos())));
        }
        tick.put("unitUpdate", new JSONObject()
                .put("parallel", units.parallel())
                .put("liveCount", units.liveCount())
                .put("poolParallelism", units.poolParallelism())
                .put("dispatchMs", millis(units.dispatchNanos()))
                .put("awaitWorkersMs", millis(units.awaitWorkersNanos()))
                .put("activeThreads", units.activeThreads())
                .put("sampledUnitCount", units.sampledUnitCount())
                .put("sampledUnitWorkerMs", millis(units.sampledUnitNanos()))
                .put("maxThreadUnitMs", millis(units.maxThreadUnitNanos()))
                .put("slowestUnits", slowUnits));

        AsyncDefendTrackRoutes.Metrics before = sample.routesBefore();
        AsyncDefendTrackRoutes.Metrics after = sample.routesAfter();
        tick.put("asyncRoutes", new JSONObject()
                .put("submitted", after.submitted() - before.submitted())
                .put("completed", after.completed() - before.completed())
                .put("canceled", after.canceled() - before.canceled())
                .put("rejected", after.rejected() - before.rejected())
                .put("searchWorkerMs", millis(after.searchNanos()
                        - before.searchNanos()))
                .put("queueDepth", after.queueDepth())
                .put("pendingMembers", after.pendingMembers())
                .put("maxQueueDepthSoFar", after.maxQueueDepth()));
        return tick;
    }

    private static double millis(long nanos) { return nanos / 1_000_000.0; }
}
