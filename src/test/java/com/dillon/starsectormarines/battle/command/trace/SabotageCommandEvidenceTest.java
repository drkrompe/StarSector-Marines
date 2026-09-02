package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.trace.SabotageCommandTraceAnalyzer.Analysis;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in production-shaped Sabotage command evidence; excluded from normal builds. */
@Tag("sabotage-command-evidence")
class SabotageCommandEvidenceTest {

    private static final int DEFAULT_MAX_TICKS = 18_000;
    private static final List<FixtureSpec> DEFAULT_MATRIX = List.of(
            new FixtureSpec("site-groups",
                    "/battle-fixtures/sabotage-site-groups-v2.json"));

    @Test
    void writesByteStableForcedSerialSabotageEvidence() throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits(),
                "canonical command evidence must use the serial scheduler");
        int maxTicks = Integer.getInteger(
                "sabotage.command.evidence.maxTicks", DEFAULT_MAX_TICKS);
        if (maxTicks < 1) {
            throw new IllegalArgumentException(
                    "sabotage.command.evidence.maxTicks must be positive");
        }
        Path output = Path.of(System.getProperty(
                        "sabotage.command.evidence.outputDir",
                        "build/reports/commander/sabotage"))
                .toAbsolutePath().normalize();
        Path staging = createStaging(output);
        Path traces = staging.resolve("traces");
        Files.createDirectories(traces);

        // The cleanup must never become the report: see EvidenceCleanup.
        Throwable primary = null;
        try {
            List<FixtureSpec> matrix = selectedMatrix();
            boolean canonical = maxTicks == DEFAULT_MAX_TICKS
                    && System.getProperty(
                    "sabotage.command.evidence.fixture.path", "").isBlank();
            int repeat = EvidenceFanOut.repeat();
            List<LoadedFixture> loadedMatrix = new ArrayList<>(matrix.size());
            List<String> runIds = new ArrayList<>(matrix.size());
            List<Callable<RunResult>> replays = new ArrayList<>();
            for (FixtureSpec spec : matrix) {
                LoadedFixture loaded = load(spec);
                String runId = reportId(spec.id, spec.external, loaded.sha256);
                loadedMatrix.add(loaded);
                runIds.add(runId);
                for (int replica = 0; replica < repeat; replica++) {
                    // Only the replay whose trace is published records frames.
                    Path visualRoot = replica == 0 ? staging : null;
                    replays.add(() -> run(loaded.fixture, maxTicks,
                            visualRoot, runId));
                }
            }
            List<RunResult> results = EvidenceFanOut.run(replays);

            List<ReportRow> rows = new ArrayList<>(matrix.size());
            for (int index = 0; index < matrix.size(); index++) {
                LoadedFixture loaded = loadedMatrix.get(index);
                String runId = runIds.get(index);
                List<RunResult> replicas = results.subList(
                        index * repeat, (index + 1) * repeat);
                RunResult first = replicas.get(0);
                EvidenceFanOut.assertReplaysByteStable(runId, "command events",
                        replicas.stream().map(RunResult::trace).toList());
                EvidenceFanOut.assertReplaysByteStable(runId, "metrics",
                        replicas.stream()
                                .map(replica -> replica.analysis()
                                        .canonicalJson()).toList());
                assertTrue(first.trace.contains("\"perspective\":\"MARINE\""));
                assertTrue(first.trace.contains("\"perspective\":\"DEFENDER\""));
                assertTrue(first.trace.contains("\"sabotageDefense\":{"));
                assertTrue(first.trace.contains("\"event\":\"charge-site-state\""));
                Files.writeString(traces.resolve(runId + ".jsonl"), first.trace,
                        StandardCharsets.UTF_8);
                rows.add(new ReportRow(runId, loaded.sha256,
                        loaded.construction, first.analysis));
                System.out.println("[sabotage-command-evidence] " + runId + " "
                        + first.analysis.run().termination() + " winner="
                        + first.analysis.run().winner() + " ticks="
                        + first.analysis.run().durationTicks());
            }
            Files.writeString(staging.resolve("summary.json"),
                    summaryJson(rows, maxTicks, canonical, repeat),
                    StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("summary.md"),
                    summaryMarkdown(rows, maxTicks, canonical, repeat),
                    StandardCharsets.UTF_8);
            publishReports(staging, output);
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            EvidenceCleanup.deleteTreeQuietlyAfter(primary, staging);
        }
        System.out.println("[sabotage-command-evidence] report "
                + output.resolve("summary.md").toAbsolutePath());
    }

    private static RunResult run(BattleFixture fixture, int maxTicks,
                                 Path visualRoot, String runId)
            throws Exception {
        try (BattleSimulation sim = fixture.build();
             CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(
                     visualRoot, runId, sim)) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                int before = sim.getSimTickIndex();
                sim.advance(BattleSimulation.TICK_DT);
                capture.afterAdvance();
                assertEquals(before + 1, sim.getSimTickIndex(),
                        "headless runner must advance exactly one fixed tick");
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            String trace = sim.getCommandTraceJsonLines();
            return new RunResult(trace,
                    SabotageCommandTraceAnalyzer.analyze(trace));
        }
    }

    private static List<FixtureSpec> selectedMatrix() {
        String selected = System.getProperty(
                "sabotage.command.evidence.fixture.path", "").trim();
        if (selected.isEmpty()) return DEFAULT_MATRIX;
        Path path = Path.of(selected).toAbsolutePath().normalize();
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        return List.of(new FixtureSpec(
                stem.replaceAll("[^A-Za-z0-9_-]", "-"),
                path.toString(), true));
    }

    private static LoadedFixture load(FixtureSpec spec) throws Exception {
        String json;
        if (spec.location.startsWith("/")) {
            try (InputStream stream = SabotageCommandEvidenceTest.class
                    .getResourceAsStream(spec.location)) {
                if (stream == null) {
                    throw new IllegalStateException(
                            "Missing Sabotage fixture: " + spec.location);
                }
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(spec.location));
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleFixture construction = fixture instanceof BattleLaunchFixture launch
                ? launch.construction() : fixture;
        if (!(construction instanceof SabotageBattleFixture sabotage)) {
            throw new IllegalArgumentException(
                    "Sabotage evidence requires a Sabotage fixture: "
                            + fixture.kind());
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(json.getBytes(StandardCharsets.UTF_8));
        return new LoadedFixture(fixture, sabotage,
                HexFormat.of().formatHex(digest));
    }

    static String summaryJson(List<ReportRow> rows, int maxTicks,
                              boolean canonical, int repeat) {
        StringBuilder out = new StringBuilder(2_048)
                .append("{\"schemaVersion\":1,\"schedulerMode\":")
                .append("\"SERIAL_DETERMINISTIC\",\"maxTicks\":")
                .append(maxTicks)
                .append(",\"repeatCount\":").append(repeat)
                .append(",\"canonicalMatrix\":")
                .append(canonical).append(",\"runs\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) out.append(',');
            ReportRow row = rows.get(i);
            out.append("{\"id\":\"").append(row.id)
                    .append("\",\"seed\":").append(row.fixture.seed())
                    .append(",\"fixtureSha256\":\"").append(row.sha256)
                    .append('"')
                    .append(",\"marineSeats\":").append(seats(row.fixture))
                    .append(",\"shuttles\":\"")
                    .append(shuttleShape(row.fixture)).append('"')
                    .append(",\"metrics\":")
                    .append(row.analysis.canonicalJson().trim())
                    .append('}');
        }
        return out.append("]}\n").toString();
    }

    static String summaryMarkdown(List<ReportRow> rows, int maxTicks,
                                  boolean canonical, int repeat) {
        StringBuilder out = new StringBuilder(2_048)
                .append("# Sabotage commander evidence\n\n")
                .append("Forced-serial, zero-input production construction ")
                .append("fixtures, ")
                .append(EvidenceFanOut.replayWording(repeat))
                .append(". A timeout is evidence, not a defender victory. The JSON ")
                .append("summary is the complete machine-readable record.\n\n")
                .append("- Evidence mode: ").append(canonical
                        ? "canonical default matrix" : "ad hoc override")
                .append("\n- Maximum ticks: ").append(maxTicks)
                .append("\n- Replays per fixture: ").append(repeat).append('\n')
                .append("- Scheduler: SERIAL_DETERMINISTIC\n\n")
                .append("| fixture | seed | shuttle cycles | marine seats | result | winner | ticks | ")
                .append("marine losses | defender losses | sites complete | retargets | reissues |\n")
                .append("|---|---:|---|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (ReportRow row : rows) {
            Analysis analysis = row.analysis;
            out.append('|').append(row.id)
                    .append('|').append(row.fixture.seed())
                    .append('|').append(shuttleShape(row.fixture))
                    .append('|').append(seats(row.fixture))
                    .append('|').append(analysis.run().termination())
                    .append('|').append(analysis.run().winner() == null
                            ? "—" : analysis.run().winner())
                    .append('|').append(analysis.run().durationTicks())
                    .append('|').append(analysis.run().combatantCasualties()
                            .getOrDefault(Faction.MARINE, 0))
                    .append('|').append(analysis.run().combatantCasualties()
                            .getOrDefault(Faction.DEFENDER, 0))
                    .append('|').append(analysis.sabotage().completedSites())
                    .append('|').append(analysis.marineCommand().retargets())
                    .append('|').append(analysis.marineCommand().reissues())
                    .append("|\n");
        }
        out.append("\n## Site-command diagnostics\n");
        for (ReportRow row : rows) {
            Analysis analysis = row.analysis;
            SabotageCommandTraceAnalyzer.CoverageMetrics coverage =
                    analysis.sabotage().coverage();
            SabotageCommandTraceAnalyzer.RoleTransitionMetrics transitions =
                    analysis.sabotage().roleTransitions();
            out.append("\n### ").append(row.id).append("\n\n")
                    .append("Fixture SHA-256: `").append(row.sha256).append("`\n\n")
                    .append("- Site coverage: planter/retriever sites ")
                    .append(coverage.minimumPlanterOrRetrieverSites()).append("–")
                    .append(coverage.maximumPlanterOrRetrieverSites())
                    .append(", security sites ")
                    .append(coverage.minimumSecuritySites()).append("–")
                    .append(coverage.maximumSecuritySites()).append(" across ")
                    .append(coverage.eligibleForceSamples())
                    .append(" eligible-force samples; all unfinished sites fully supported in ")
                    .append(coverage.samplesWithEveryUnfinishedSiteSupported())
                    .append(" samples.\n")
                    .append("- Planter/retriever transitions: planter entries ")
                    .append(transitions.planterEntries()).append(", exits ")
                    .append(transitions.planterExits()).append("; retriever entries ")
                    .append(transitions.retrieverEntries()).append(", exits ")
                    .append(transitions.retrieverExits()).append("; retriever→planter ")
                    .append(transitions.retrieverToPlanter())
                    .append(", planter→retriever ")
                    .append(transitions.planterToRetriever())
                    .append("; site planter losses ")
                    .append(transitions.sitePlanterLosses())
                    .append(", recovery starts ")
                    .append(transitions.siteRecoveryStarts())
                    .append(", recovery support changes ")
                    .append(transitions.siteRecoverySupportChanges())
                    .append(", recovery→planter ")
                    .append(transitions.siteRecoveryToPlanter()).append(".\n")
                    .append("- Directive churn: ")
                    .append(analysis.marineCommand().retargets()).append(" retargets, ")
                    .append(analysis.marineCommand().releases()).append(" releases, ")
                    .append(analysis.marineCommand().reissues()).append(" reissues, ")
                    .append(analysis.marineCommand().rejectedProposals())
                    .append(" rejected proposals, ")
                    .append(analysis.marineCommand().stabilityHolds())
                    .append(" stability holds.\n")
                    .append("- Site outcomes: ");
            boolean first = true;
            for (SabotageCommandTraceAnalyzer.SiteMetrics site
                    : analysis.sabotage().sites().values()) {
                if (!first) out.append("; ");
                first = false;
                out.append(site.siteId()).append(" progress ")
                        .append(site.maximumProgressBasisPoints()).append(" bp, completion tick ")
                        .append(site.completionTick() < 0 ? "—" : site.completionTick());
            }
            out.append(".\n");
        }
        return out.toString();
    }

    static String reportId(String fixtureId, boolean external, String sha256) {
        return external ? fixtureId + '-' + sha256.substring(0, 12) : fixtureId;
    }

    private static Path createStaging(Path output) throws Exception {
        Path parent = output.getParent();
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent,
                "." + output.getFileName() + "-staging-");
    }

    static void publishReports(Path staging, Path output) throws Exception {
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path parent = normalizedOutput.getParent();
        Files.createDirectories(parent);
        Path backupHolder = Files.createTempDirectory(parent,
                "." + normalizedOutput.getFileName() + "-backup-");
        Path backup = backupHolder.resolve("previous");
        boolean previousMoved = false;
        boolean published = false;
        Throwable primary = null;
        try {
            if (Files.exists(normalizedOutput)) {
                Files.move(normalizedOutput, backup);
                previousMoved = true;
            }
            Files.move(staging, normalizedOutput);
            published = true;
        } catch (Throwable failure) {
            if (previousMoved && !Files.exists(normalizedOutput)) {
                try {
                    Files.move(backup, normalizedOutput);
                } catch (Exception restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            primary = failure;
            throw failure;
        } finally {
            if (published || !Files.exists(backup)) {
                EvidenceCleanup.deleteTreeQuietlyAfter(primary, backupHolder);
            }
        }
    }

    private static String shuttleShape(SabotageBattleFixture fixture) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fixture.manifest().size(); i++) {
            if (i > 0) out.append('+');
            ShuttleAssignment shuttle = fixture.manifest().get(i);
            out.append(shuttle.type.name()).append('x').append(shuttle.cycles);
        }
        return out.toString();
    }

    private static int seats(SabotageBattleFixture fixture) {
        int seats = 0;
        for (ShuttleAssignment shuttle : fixture.manifest()) {
            seats += shuttle.seatsPerSortie * shuttle.cycles;
        }
        return seats;
    }

    private record FixtureSpec(String id, String location, boolean external) {
        private FixtureSpec(String id, String location) {
            this(id, location, false);
        }
    }

    private record LoadedFixture(
            BattleFixture fixture, SabotageBattleFixture construction,
            String sha256) { }

    private record RunResult(String trace, Analysis analysis) { }

    record ReportRow(
            String id, String sha256, SabotageBattleFixture fixture,
            Analysis analysis) { }
}
