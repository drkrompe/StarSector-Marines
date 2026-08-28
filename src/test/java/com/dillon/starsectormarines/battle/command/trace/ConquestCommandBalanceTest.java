package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Analysis;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in production-shaped Conquest command evidence; excluded from normal builds. */
@Tag("commander-balance")
class ConquestCommandBalanceTest {

    private static final int DEFAULT_MAX_TICKS = 18_000;
    private static final List<FixtureSpec> DEFAULT_MATRIX = List.of(
            new FixtureSpec("reinforced-south",
                    "/battle-fixtures/conquest-reinforced-south-v3.json",
                    204, 17),
            new FixtureSpec("full-strength-west",
                    "/battle-fixtures/conquest-full-strength-west-v3.json",
                    408, 34));

    @Test
    void writesByteStableForcedSerialConquestEvidence() throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits(),
                "canonical command evidence must use the serial scheduler");
        int maxTicks = Integer.getInteger(
                "commander.balance.maxTicks", DEFAULT_MAX_TICKS);
        if (maxTicks < 1) {
            throw new IllegalArgumentException(
                    "commander.balance.maxTicks must be positive");
        }
        Path output = Path.of(System.getProperty(
                "commander.balance.outputDir",
                "build/reports/commander/conquest"))
                .toAbsolutePath().normalize();
        Path staging = createStaging(output);
        Path traces = staging.resolve("traces");
        Files.createDirectories(traces);

        try {
            List<FixtureSpec> matrix = selectedMatrix();
            boolean canonical = maxTicks == DEFAULT_MAX_TICKS
                    && System.getProperty(
                    "commander.balance.fixture.path", "").isBlank();
            List<ReportRow> rows = new ArrayList<>(matrix.size());
            for (FixtureSpec spec : matrix) {
                LoadedFixture loaded = load(spec);
                BattleFixture fixture = loaded.fixture;
                String runId = reportId(spec.id, spec.external, loaded.sha256);
                RunResult first = run(fixture, maxTicks, staging, runId);
                RunResult second = run(fixture, maxTicks, null, runId);
                assertByteStable(first.trace, second.trace,
                        "command events", runId);
                assertByteStable(first.analysis.canonicalJson(),
                        second.analysis.canonicalJson(), "metrics", runId);
                assertTrue(first.trace.contains("\"perspective\":\"MARINE\""));
                assertTrue(first.trace.contains("\"perspective\":\"DEFENDER\""));
                Files.writeString(traces.resolve(runId + ".jsonl"), first.trace,
                        StandardCharsets.UTF_8);
                rows.add(new ReportRow(runId, loaded.sha256,
                        loaded.construction, loaded.launchSeats,
                        loaded.launchSquads, first.analysis));
                System.out.println("[commander-balance] " + runId + " "
                        + first.analysis.run().termination() + " winner="
                        + first.analysis.run().winner() + " ticks="
                        + first.analysis.run().durationTicks());
            }
            Files.writeString(staging.resolve("summary.json"),
                    summaryJson(rows, maxTicks, canonical),
                    StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("summary.md"),
                    summaryMarkdown(rows, maxTicks, canonical),
                    StandardCharsets.UTF_8);
            publishReports(staging, output);
        } finally {
            deleteTree(staging);
        }
        System.out.println("[commander-balance] report "
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
            return new RunResult(trace, CommandTraceAnalyzer.analyze(trace));
        }
    }

    private static List<FixtureSpec> selectedMatrix() {
        String selected = System.getProperty(
                "commander.balance.fixture.path", "").trim();
        if (selected.isEmpty()) return DEFAULT_MATRIX;
        Path path = Path.of(selected).toAbsolutePath().normalize();
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String id = stem.replaceAll("[^A-Za-z0-9_-]", "-");
        return List.of(new FixtureSpec(id, path.toString(), -1, -1, true));
    }

    private static LoadedFixture load(FixtureSpec spec) throws Exception {
        String json;
        if (spec.location.startsWith("/")) {
            try (InputStream stream = ConquestCommandBalanceTest.class
                    .getResourceAsStream(spec.location)) {
                if (stream == null) {
                    throw new IllegalStateException(
                            "Missing Conquest fixture: " + spec.location);
                }
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(spec.location));
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleFixture construction = fixture instanceof BattleLaunchFixture launch
                ? launch.construction() : fixture;
        if (!(construction instanceof ConquestBattleFixture conquest)) {
            throw new IllegalArgumentException(
                    "Commander balance requires a Conquest fixture: " + fixture.kind());
        }
        int launchSeats = 0;
        int launchSquads = 0;
        if (fixture instanceof BattleLaunchFixture launch) {
            launchSeats = launch.launch().marineSeats().size();
            launchSquads = (int) launch.launch().marineSeats().stream()
                    .map(MarineSeatCommitment::campaignSquadId)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct().count();
        }
        if (!spec.external) {
            validateCanonicalLaunch(spec, fixture, conquest,
                    launchSeats, launchSquads);
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(json.getBytes(StandardCharsets.UTF_8));
        return new LoadedFixture(fixture, conquest, launchSeats, launchSquads,
                HexFormat.of().formatHex(digest));
    }

    private static void assertByteStable(
            String first, String second, String evidence, String runId) {
        if (first.equals(second)) return;
        String[] firstLines = first.split("\\R", -1);
        String[] secondLines = second.split("\\R", -1);
        int shared = Math.min(firstLines.length, secondLines.length);
        int line = 0;
        while (line < shared && firstLines[line].equals(secondLines[line])) line++;
        String firstValue = line < firstLines.length
                ? firstLines[line] : "<missing>";
        String secondValue = line < secondLines.length
                ? secondLines[line] : "<missing>";
        int character = firstDifferingCharacter(firstValue, secondValue);
        throw new AssertionError("same fixture must produce byte-stable "
                + evidence + ": " + runId + "; first difference at line "
                + (line + 1) + ", character " + (character + 1)
                + "\nfirst: " + excerpt(firstValue, character)
                + "\nsecond: " + excerpt(secondValue, character));
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

    private static void validateCanonicalLaunch(
            FixtureSpec spec, BattleFixture fixture,
            ConquestBattleFixture construction,
            int launchSeats, int launchSquads) {
        if (!(fixture instanceof BattleLaunchFixture)) {
            throw new IllegalArgumentException(
                    "Canonical Conquest evidence requires a V3 launch fixture: "
                            + spec.id);
        }
        if (construction.arrivalPlan().policy()
                != MarineArrivalPolicy.PAIRED_HALF_SQUAD) {
            throw new IllegalArgumentException(
                    "Canonical Conquest evidence requires paired arrivals: "
                            + spec.id);
        }
        if (construction.arrivalPlan().firstPlayerShuttle() != 0
                || construction.arrivalPlan().arrivalConfig().dropZoneCount() != 3
                || construction.arrivalPlan().arrivalConfig()
                .shuttlePairsPerZone() != 1
                || construction.manifest().size() != 6
                || construction.manifest().stream().anyMatch(shuttle ->
                shuttle.type != ShuttleType.AEROSHUTTLE
                        || shuttle.seatsPerSortie != 6)) {
            throw new IllegalArgumentException(
                    "Canonical Conquest evidence requires the default three paired "
                            + "landing areas and six-seat descent shuttles: "
                            + spec.id);
        }
        if (launchSeats != spec.expectedLaunchSeats
                || launchSquads != spec.expectedLaunchSquads
                || transportSeats(construction) != launchSeats) {
            throw new IllegalArgumentException(
                    "Canonical Conquest launch shape mismatch for " + spec.id
                            + ": expected " + spec.expectedLaunchSeats
                            + " marines / " + spec.expectedLaunchSquads
                            + " squads, got " + launchSeats + " / " + launchSquads);
        }
    }

    static String summaryJson(List<ReportRow> rows, int maxTicks,
                              boolean canonical) {
        StringBuilder out = new StringBuilder(2_048)
                .append("{\"schemaVersion\":6,\"schedulerMode\":")
                .append("\"SERIAL_DETERMINISTIC\",\"maxTicks\":")
                .append(maxTicks)
                .append(",\"repeatCount\":2,\"canonicalMatrix\":")
                .append(canonical).append(",\"runs\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) out.append(',');
            ReportRow row = rows.get(i);
            out.append("{\"id\":\"").append(row.id)
                    .append("\",\"seed\":").append(row.fixture.seed())
                    .append(",\"fixtureSha256\":\"").append(row.sha256)
                    .append('"')
                    .append(",\"transportSeatCapacity\":")
                    .append(transportSeats(row.fixture))
                    .append(",\"marineCommitments\":")
                    .append(row.launchSeats)
                    .append(",\"marineSquads\":")
                    .append(row.launchSquads)
                    .append(",\"shuttles\":\"").append(shuttleShape(row.fixture))
                    .append('"')
                    .append(",\"metrics\":")
                    .append(row.analysis.canonicalJson().trim())
                    .append('}');
        }
        return out.append("]}\n").toString();
    }

    static String summaryMarkdown(List<ReportRow> rows, int maxTicks,
                                  boolean canonical) {
        StringBuilder out = new StringBuilder(2_048)
                .append("# Conquest commander evidence\n\n")
                .append("Forced-serial, zero-input production launch fixtures. ")
                .append("A timeout is evidence, not a defender victory. The JSON ")
                .append("summary is the complete machine-readable record.\n\n")
                .append("- Evidence mode: ").append(canonical
                        ? "canonical default matrix" : "ad hoc override")
                .append("\n- Maximum ticks: ").append(maxTicks)
                .append("\n- Replays per fixture: 2\n")
                .append("- Scheduler: SERIAL_DETERMINISTIC\n\n")
                .append("| fixture | seed | shuttle cycles | transport seats | committed marines | squads | result | winner | ticks | ")
                .append("marine losses | defender losses | captures | final held | ")
                .append("marine retargets | defender mobilization samples |\n")
                .append("|---|---:|---|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (ReportRow row : rows) {
            Analysis analysis = row.analysis;
            out.append('|').append(row.id)
                    .append('|').append(row.fixture.seed())
                    .append('|').append(shuttleShape(row.fixture))
                    .append('|').append(transportSeats(row.fixture))
                    .append('|').append(row.launchSeats)
                    .append('|').append(row.launchSquads)
                    .append('|').append(analysis.run().termination())
                    .append('|').append(analysis.run().winner() == null
                            ? "—" : analysis.run().winner())
                    .append('|').append(analysis.run().durationTicks())
                    .append('|').append(analysis.run().combatantCasualties()
                            .getOrDefault(Faction.MARINE, 0))
                    .append('|').append(analysis.run().combatantCasualties()
                            .getOrDefault(Faction.DEFENDER, 0))
                    .append('|').append(analysis.conquest().captures())
                    .append('|').append(analysis.conquest().finalMarineHeld())
                    .append('|').append(analysis.factions().get(Faction.MARINE)
                            .retargets())
                    .append('|').append(analysis.factions().get(Faction.DEFENDER)
                            .publishedMobilizationLatenciesTicks().size())
                    .append("|\n");
        }
        out.append("\n## Command diagnostics\n");
        for (ReportRow row : rows) {
            Analysis analysis = row.analysis;
            CommandTraceAnalyzer.FactionMetrics marine =
                    analysis.factions().get(Faction.MARINE);
            CommandTraceAnalyzer.FactionMetrics defender =
                    analysis.factions().get(Faction.DEFENDER);
            CommandTraceAnalyzer.PhysicalProgressMetrics movement =
                    marine.physicalProgress();
            CommandTraceAnalyzer.CommandInactivityMetrics inactivity =
                    marine.commandInactivity();
            CommandTraceAnalyzer.SecureTravelMetrics secureTravel =
                    movement.secureTravel();
            CommandTraceAnalyzer.CompoundPresenceMetrics presence =
                    analysis.conquest().physicalPresence();
            out.append("\n### ").append(row.id).append("\n\n")
                    .append("Fixture SHA-256: `").append(row.sha256).append("`\n\n")
                    .append("- Marine command-unassigned: ")
                    .append(marine.unassignedSquadPulses()).append(" squad-pulses / ")
                    .append(marine.unassignedSquadTicks()).append(" squad-ticks; unreachable ")
                    .append(marine.unreachableSquadPulses()).append(", no-actionable ")
                    .append(marine.noActionableSquadPulses()).append(".\n")
                    .append("- Marine command-unassigned causes: lifecycle ")
                    .append(inactivity.lifecycleSquadPulses()).append(" / ")
                    .append(inactivity.lifecycleSquadTicks())
                    .append("; execution-suspended ")
                    .append(inactivity.executionSuspendedSquadPulses()).append(" / ")
                    .append(inactivity.executionSuspendedSquadTicks())
                    .append("; local-contact ")
                    .append(inactivity.localContactSquadPulses()).append(" / ")
                    .append(inactivity.localContactSquadTicks())
                    .append("; useful active-path movement ")
                    .append(inactivity.usefulMovementSquadPulses()).append(" / ")
                    .append(inactivity.usefulMovementSquadTicks())
                    .append("; genuine idle ")
                    .append(inactivity.genuineIdleSquadPulses()).append(" / ")
                    .append(inactivity.genuineIdleSquadTicks())
                    .append("; legacy/unclassified ")
                    .append(inactivity.unclassifiedSquadPulses()).append(" / ")
                    .append(inactivity.unclassifiedSquadTicks())
                    .append(" (squad-pulses / squad-ticks).\n")
                    .append("- Marine distant captures deferred for front resistance: ")
                    .append(marine.distantCaptureDeferredSquadPulses())
                    .append(" squad-pulses.\n")
                    .append("- Marine stability holds: ").append(marine.stabilityHolds())
                    .append("; peak published track share: ")
                    .append(marine.peakPublishedTrackShareBasisPoints())
                    .append(" bp.\n")
                    .append("- Marine physical progress: ")
                    .append(movement.maximumConcurrentAliveMembers())
                    .append(" peak live members in ")
                    .append(movement.maximumConcurrentAliveSquads())
                    .append(movement.maximumConcurrentAliveSquads() == 1
                            ? " squad; " : " squads; ")
                    .append(movement.episodesWithMarkerClosure()).append('/')
                    .append(movement.movementEpisodes())
                    .append(" assignment episodes closed marker range; ")
                    .append(movement.secureCompoundEpisodesObservedInTargetZone())
                    .append('/').append(movement.secureCompoundEpisodes())
                    .append(" secure-compound episodes were observed in their capture zone; ")
                    .append(movement.compoundAssaultThresholdCommitments())
                    .append(" adjacent assault commitments.\n")
                    .append("- Marine command-pulse movement intervals: ")
                    .append(movement.markerClosingSquadTicks())
                    .append(" closing squad-ticks; ")
                    .append(movement.nonClosingWithContactSquadTicks())
                    .append(" non-closing with contact; ")
                    .append(movement.quietNonClosingSquadTicks())
                    .append(" quiet non-closing; ")
                    .append(movement.suspendedAssignmentSquadTicks())
                    .append(" execution-suspended; target-zone latencies: ")
                    .append(movement.targetZoneEntryLatenciesTicks())
                    .append(".\n")
                    .append("- Marine secure-travel episodes: ")
                    .append(secureTravel.episodesFinalized()).append('/')
                    .append(secureTravel.episodesStarted())
                    .append(" finalized, ")
                    .append(secureTravel.episodesOpen())
                    .append(" open; exits: target entry ")
                    .append(secureTravel.targetEntryExits())
                    .append(", retarget ")
                    .append(secureTravel.retargetExits())
                    .append(", release ")
                    .append(secureTravel.releaseExits())
                    .append(", squad loss ")
                    .append(secureTravel.squadLossExits())
                    .append(", execution suspension ")
                    .append(secureTravel.executionSuspensionExits())
                    .append(", observation gap ")
                    .append(secureTravel.observationGapExits())
                    .append(", timeout ")
                    .append(secureTravel.timeoutExits())
                    .append(", terminal result ")
                    .append(secureTravel.terminalExits())
                    .append(". Retarget provenance: objective changed ")
                    .append(secureTravel.retargetObjectiveChanged())
                    .append(", marker changed ")
                    .append(secureTravel.retargetMarkerChanged())
                    .append(", assignment changed ")
                    .append(secureTravel.retargetAssignmentChanged())
                    .append(", unclassified ")
                    .append(secureTravel.retargetUnclassified())
                    .append(". Squad-loss last distances (0.1 cells): ")
                    .append(secureTravel.squadLossLastDistancesDecicells())
                    .append("; approach progress (bp): ")
                    .append(secureTravel.squadLossApproachProgressBasisPoints())
                    .append(". Last-alive front context: observed ")
                    .append(secureTravel.squadLossLocationsObserved())
                    .append(", unknown location ")
                    .append(secureTravel.squadLossLocationsUnknown())
                    .append(", local contact ")
                    .append(secureTravel.squadLossAtLocalContact())
                    .append(", track belief only ")
                    .append(secureTravel.squadLossWithTrackBeliefOnly())
                    .append(", no published contact ")
                    .append(secureTravel.squadLossWithoutPublishedContact())
                    .append(", unknown track ")
                    .append(secureTravel.squadLossWithUnknownTrack())
                    .append(". Last-alive tactical contact: observed ")
                    .append(secureTravel.squadLossTacticalObserved())
                    .append(", unknown ")
                    .append(secureTravel.squadLossTacticalUnknown())
                    .append(", breach action ")
                    .append(secureTravel.squadLossWithBreachAction())
                    .append(", moving/exposed from primary ")
                    .append(secureTravel.squadLossWithMovingMembers()).append('/')
                    .append(secureTravel.squadLossExposedFromPrimary())
                    .append(", doctrine advance/hold/disengage ")
                    .append(secureTravel.squadLossDoctrineAdvance()).append('/')
                    .append(secureTravel.squadLossDoctrineHold()).append('/')
                    .append(secureTravel.squadLossDoctrineDisengage())
                    .append(", engageable members/fireteams ")
                    .append(secureTravel.squadLossWithEngageableMembers()).append('/')
                    .append(secureTravel.squadLossWithEngageableFireTeams())
                    .append(", under fire ")
                    .append(secureTravel.squadLossUnderFireRecently())
                    .append(", majority covered from primary ")
                    .append(secureTravel.squadLossMajorityCoveredFromPrimary())
                    .append(", cooling down ")
                    .append(secureTravel.squadLossCoolingDown())
                    .append(". Context may overlap: local contact ")
                    .append(secureTravel.episodesWithLocalContact())
                    .append(", active path ")
                    .append(secureTravel.episodesWithActivePath())
                    .append(", quiet travel ")
                    .append(secureTravel.episodesWithQuietTravel())
                    .append(".\n")
                    .append("- Capture-zone presence: ")
                    .append(presence.compoundsWithMarinePresence())
                    .append(" compounds observed with marine presence; ")
                    .append(presence.marineOnlyCompoundTicks())
                    .append(" marine-only compound-ticks, ")
                    .append(presence.mixedCompoundTicks())
                    .append(" mixed; longest marine-only run ")
                    .append(presence.longestMarineOnlyPresenceRunTicks())
                    .append(" ticks; peak capture progress ")
                    .append(presence.maximumCaptureProgressBasisPoints())
                    .append(" bp.\n")
                    .append("- Defender reserve: ").append(defender.reserveSquadTicks())
                    .append(" squad-ticks; mobilization latencies: ")
                    .append(defender.publishedMobilizationLatenciesTicks())
                    .append("; unmobilized episodes: ")
                    .append(defender.unmobilizedThreatEpisodes()).append(".\n")
                    .append("- Defender peak published track share: ")
                    .append(defender.peakPublishedTrackShareBasisPoints())
                    .append(" bp; longest observed capture gap: ")
                    .append(analysis.conquest().longestObservedCaptureGapTicks())
                    .append(" ticks; territorial progress: ")
                    .append(progressLabel(analysis))
                    .append(".\n");
        }
        return out.toString();
    }

    static String reportId(String fixtureId, boolean external, String sha256) {
        return external ? fixtureId + '-' + sha256.substring(0, 12) : fixtureId;
    }

    private static String progressLabel(Analysis analysis) {
        if (analysis.conquest().territorialProgressStalled()) return "STALLED";
        if (analysis.conquest().captures() > 0
                || analysis.conquest().maximumMarineHeld()
                > analysis.conquest().initialMarineHeld()) return "OBSERVED";
        return "NO_STALL_EVIDENCE";
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
        boolean hadPrevious = Files.exists(normalizedOutput);
        boolean previousMoved = false;
        boolean published = false;
        try {
            if (hadPrevious) {
                Files.move(normalizedOutput, backup);
                previousMoved = true;
            }
            Files.move(staging, normalizedOutput);
            published = true;
        } catch (Exception failure) {
            if (previousMoved && !Files.exists(normalizedOutput)) {
                try {
                    Files.move(backup, normalizedOutput);
                } catch (Exception restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        } finally {
            if (published || !Files.exists(backup)) deleteTree(backupHolder);
        }
    }

    static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String shuttleShape(ConquestBattleFixture fixture) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fixture.manifest().size(); i++) {
            if (i > 0) out.append('+');
            ShuttleAssignment shuttle = fixture.manifest().get(i);
            out.append(shuttle.type.name()).append('x').append(shuttle.cycles);
        }
        return out.toString();
    }

    private static int transportSeats(ConquestBattleFixture fixture) {
        int seats = 0;
        for (ShuttleAssignment shuttle : fixture.manifest()) {
            seats += shuttle.seatsPerSortie * shuttle.cycles;
        }
        return seats;
    }

    private record FixtureSpec(
            String id, String location,
            int expectedLaunchSeats, int expectedLaunchSquads,
            boolean external) {
        private FixtureSpec(String id, String location,
                            int expectedLaunchSeats, int expectedLaunchSquads) {
            this(id, location, expectedLaunchSeats, expectedLaunchSquads, false);
        }
    }

    private record LoadedFixture(
            BattleFixture fixture, ConquestBattleFixture construction,
            int launchSeats, int launchSquads, String sha256) { }

    private record RunResult(String trace, Analysis analysis) { }

    record ReportRow(
            String id, String sha256, ConquestBattleFixture fixture,
            int launchSeats, int launchSquads, Analysis analysis) { }
}
