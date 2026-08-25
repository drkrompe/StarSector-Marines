package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure offline analysis of canonical commander JSONL. */
public final class CommandTraceAnalyzer {

    private CommandTraceAnalyzer() {}

    public enum Termination {
        TERMINAL,
        TIMEOUT,
        INCOMPLETE
    }

    public record RunMetrics(
            String fixtureKind,
            String schedulerMode,
            int startTick,
            int endTick,
            int durationTicks,
            Termination termination,
            String winner,
            boolean fullBattle,
            int observationWindows,
            Map<Faction, Integer> combatantCasualties) {

        public RunMetrics {
            combatantCasualties = immutableFactionMap(combatantCasualties);
        }
    }

    public record FactionMetrics(
            int perspectiveSamples,
            int retargets,
            int releases,
            int reissues,
            int rejectedProposals,
            int stabilityHolds,
            int unassignedSquadPulses,
            int unassignedSquadTicks,
            int unreachableSquadPulses,
            int noActionableSquadPulses,
            long reserveSquadTicks,
            List<Integer> publishedMobilizationLatenciesTicks,
            int unmobilizedThreatEpisodes,
            int peakPublishedTrackShareBasisPoints) {

        public FactionMetrics {
            publishedMobilizationLatenciesTicks =
                    List.copyOf(publishedMobilizationLatenciesTicks);
        }
    }

    public record ConquestMetrics(
            int compoundCount,
            int initialMarineHeld,
            int finalMarineHeld,
            int maximumMarineHeld,
            int captures,
            int losses,
            int keepCaptureTick,
            int longestObservedCaptureGapTicks,
            boolean territorialProgressStalled) { }

    public record Analysis(
            RunMetrics run,
            Map<Faction, FactionMetrics> factions,
            ConquestMetrics conquest) {

        public Analysis {
            factions = immutableFactionMap(factions);
        }

        /** Stable, timestamp-free summary bytes for report comparison. */
        public String canonicalJson() {
            StringBuilder out = new StringBuilder(1_024);
            out.append('{');
            nullableStringField(out, "fixtureKind", run.fixtureKind(), false);
            stringField(out, "schedulerMode", run.schedulerMode(), true);
            numberField(out, "startTick", run.startTick());
            numberField(out, "endTick", run.endTick());
            numberField(out, "durationTicks", run.durationTicks());
            stringField(out, "termination", run.termination().name(), true);
            nullableStringField(out, "winner", run.winner());
            booleanField(out, "fullBattle", run.fullBattle());
            numberField(out, "observationWindows", run.observationWindows());
            out.append(",\"combatantCasualties\":{");
            appendFactionInts(out, run.combatantCasualties());
            out.append('}');
            out.append(",\"factions\":{");
            boolean firstFaction = true;
            for (Faction faction : Faction.values()) {
                FactionMetrics metrics = factions.get(faction);
                if (metrics == null) continue;
                if (!firstFaction) out.append(',');
                firstFaction = false;
                string(out, faction.name());
                out.append(":{");
                rawNumberField(out, "perspectiveSamples",
                        metrics.perspectiveSamples());
                numberField(out, "retargets", metrics.retargets());
                numberField(out, "releases", metrics.releases());
                numberField(out, "reissues", metrics.reissues());
                numberField(out, "rejectedProposals",
                        metrics.rejectedProposals());
                numberField(out, "stabilityHolds", metrics.stabilityHolds());
                numberField(out, "unassignedSquadPulses",
                        metrics.unassignedSquadPulses());
                numberField(out, "unassignedSquadTicks",
                        metrics.unassignedSquadTicks());
                numberField(out, "unreachableSquadPulses",
                        metrics.unreachableSquadPulses());
                numberField(out, "noActionableSquadPulses",
                        metrics.noActionableSquadPulses());
                longField(out, "reserveSquadTicks",
                        metrics.reserveSquadTicks());
                out.append(",\"publishedMobilizationLatenciesTicks\":[");
                for (int i = 0;
                     i < metrics.publishedMobilizationLatenciesTicks().size(); i++) {
                    if (i > 0) out.append(',');
                    out.append(metrics.publishedMobilizationLatenciesTicks().get(i));
                }
                out.append(']');
                numberField(out, "unmobilizedThreatEpisodes",
                        metrics.unmobilizedThreatEpisodes());
                numberField(out, "peakPublishedTrackShareBasisPoints",
                        metrics.peakPublishedTrackShareBasisPoints());
                out.append('}');
            }
            out.append('}');
            out.append(",\"conquest\":{");
            rawNumberField(out, "compoundCount", conquest.compoundCount());
            numberField(out, "initialMarineHeld",
                    conquest.initialMarineHeld());
            numberField(out, "finalMarineHeld", conquest.finalMarineHeld());
            numberField(out, "maximumMarineHeld",
                    conquest.maximumMarineHeld());
            numberField(out, "captures", conquest.captures());
            numberField(out, "losses", conquest.losses());
            numberField(out, "keepCaptureTick", conquest.keepCaptureTick());
            numberField(out, "longestObservedCaptureGapTicks",
                    conquest.longestObservedCaptureGapTicks());
            booleanField(out, "territorialProgressStalled",
                    conquest.territorialProgressStalled());
            return out.append("}}\n").toString();
        }
    }

    public static Analysis analyze(String jsonLines) throws Exception {
        ParsedTrace trace = parse(jsonLines);
        Map<Faction, FactionMetrics> factions = new EnumMap<>(Faction.class);
        for (Faction faction : Faction.values()) {
            List<PerspectiveSample> samples = trace.samples.get(faction);
            if (samples != null && !samples.isEmpty()) {
                factions.put(faction, analyzeFaction(faction, samples,
                        trace.windowStarts, trace.windowEnds,
                        trace.termination, trace.finalWindow()));
            }
        }
        return new Analysis(trace.runMetrics(), factions,
                analyzeConquest(trace));
    }

    private static FactionMetrics analyzeFaction(
            Faction faction, List<PerspectiveSample> samples,
            Map<Integer, Integer> windowStarts,
            Map<Integer, Integer> windowEnds,
            Termination termination, int finalTraceWindow) throws Exception {
        Map<Integer, DirectiveState> directiveStates = new HashMap<>();
        int directiveWindow = -1;
        int retargets = 0;
        int releases = 0;
        int reissues = 0;
        int rejected = 0;
        int stabilityHolds = 0;
        int unassignedPulses = 0;
        int unassignedTicks = 0;
        int unreachablePulses = 0;
        int noActionablePulses = 0;
        long reserveTicks = 0;
        int peakShare = 0;
        Map<Integer, ThreatState> threats = new HashMap<>();
        List<Integer> latencies = new ArrayList<>();
        int unanswered = 0;

        for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
            PerspectiveSample sample = samples.get(sampleIndex);
            JSONObject row = sample.row;
            boolean newWindow = sample.window != directiveWindow;
            boolean baseline = newWindow && sample.observedTick
                    == windowStarts.getOrDefault(sample.window, Integer.MIN_VALUE);
            if (newWindow) {
                directiveStates.clear();
                directiveWindow = sample.window;
            }
            int intervalEnd = intervalEnd(samples, sampleIndex, windowEnds);
            int intervalTicks = Math.max(0, intervalEnd - sample.observedTick);
            reserveTicks += (long) row.getInt("reserveCount") * intervalTicks;

            JSONArray directives = row.getJSONArray("directives");
            for (int i = 0; i < directives.length(); i++) {
                JSONObject directive = directives.getJSONObject(i);
                int squadId = directive.getInt("squadId");
                String status = directive.getString("status");
                if ("REJECTED".equals(status)) {
                    if (!baseline) rejected++;
                    continue;
                }
                if (!baseline && "RETAINED".equals(status)
                        && directive.optString("disposition", "")
                        .startsWith("stable through tick")) {
                    stabilityHolds++;
                }
                String key = assignmentKey(directive);
                int issuedTick = directive.getInt("issuedTick");
                DirectiveState prior = directiveStates.get(squadId);
                if ("RELEASED".equals(status) || "UNASSIGNED".equals(status)) {
                    if (!baseline && prior != null && prior.key != null) releases++;
                    directiveStates.put(squadId,
                            new DirectiveState(null, issuedTick));
                    continue;
                }
                if (!baseline && prior != null && prior.key != null) {
                    if (!prior.key.equals(key)) retargets++;
                    else if (prior.issuedTick != issuedTick) reissues++;
                }
                directiveStates.put(squadId,
                        new DirectiveState(key, issuedTick));
            }

            JSONObject conquest = row.optJSONObject("conquest");
            if (conquest == null) continue;
            JSONArray actions = conquest.getJSONArray("actions");
            Map<Integer, Boolean> respondingTracks = new HashMap<>();
            int unassignedNow = 0;
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                String reason = action.getString("reason");
                if ("NO_REACHABLE_COMPOUND_TARGET".equals(reason)) {
                    if (!baseline) {
                        unreachablePulses++;
                        unassignedPulses++;
                    }
                    unassignedNow++;
                } else if ("NO_ACTIONABLE_TRACK_TARGET".equals(reason)) {
                    if (!baseline) {
                        noActionablePulses++;
                        unassignedPulses++;
                    }
                    unassignedNow++;
                }
                if ("DEFENDER_TRACK_RESPONSE".equals(reason)
                        || "DEFENDER_ADJACENT_TRACK_RESPONSE".equals(reason)) {
                    respondingTracks.put(action.getInt("effectiveTrack"), true);
                }
            }
            unassignedTicks += unassignedNow * intervalTicks;

            JSONArray tracks = conquest.getJSONArray("tracks");
            long totalMembers = 0;
            int maxMembers = 0;
            for (int i = 0; i < tracks.length(); i++) {
                JSONObject track = tracks.getJSONObject(i);
                int members = Math.max(0,
                        track.getInt("effectiveLiveMembers"));
                totalMembers += members;
                maxMembers = Math.max(maxMembers, members);

                if (faction != Faction.DEFENDER) continue;
                int trackIndex = track.getInt("index");
                int contacts = track.getInt("knownHostileContacts");
                ThreatState state = threats.get(trackIndex);
                if (state == null) {
                    threats.put(trackIndex, new ThreatState(contacts,
                            contacts > 0 ? -1 : Integer.MIN_VALUE,
                            sample.window));
                    continue;
                }
                if (state.window != sample.window) {
                    state = new ThreatState(contacts,
                            contacts > 0 ? -1 : Integer.MIN_VALUE,
                            sample.window);
                    threats.put(trackIndex, state);
                } else if (state.contacts == 0 && contacts > 0) {
                    state.startedTick = sample.observedTick;
                } else if (state.contacts > 0 && contacts == 0) {
                    if (state.startedTick >= 0) unanswered++;
                    state.startedTick = Integer.MIN_VALUE;
                }
                state.contacts = contacts;
                if (contacts > 0 && state.startedTick >= 0
                        && respondingTracks.containsKey(trackIndex)) {
                    latencies.add(sample.observedTick - state.startedTick);
                    state.startedTick = -1;
                }
            }
            if (totalMembers > 0) {
                int share = (int) Math.round(
                        10_000.0 * maxMembers / totalMembers);
                peakShare = Math.max(peakShare, share);
            }
        }

        if (faction == Faction.DEFENDER
                && termination == Termination.TERMINAL) {
            for (ThreatState state : threats.values()) {
                if (state.window == finalTraceWindow && state.startedTick >= 0) {
                    unanswered++;
                }
            }
        }

        return new FactionMetrics(samples.size(), retargets, releases,
                reissues, rejected, stabilityHolds, unassignedPulses,
                unassignedTicks, unreachablePulses, noActionablePulses,
                reserveTicks, latencies, unanswered, peakShare);
    }

    private static int intervalEnd(List<PerspectiveSample> samples, int index,
                                   Map<Integer, Integer> windowEnds) {
        PerspectiveSample current = samples.get(index);
        if (index + 1 < samples.size()) {
            PerspectiveSample next = samples.get(index + 1);
            if (next.window == current.window) return next.observedTick;
        }
        return windowEnds.getOrDefault(current.window, current.observedTick);
    }

    private static String assignmentKey(JSONObject directive) throws Exception {
        JSONObject assignment = directive.optJSONObject("assignment");
        if (assignment == null) return null;
        return directive.getString("issuer") + '|'
                + directive.getString("authority") + '|'
                + assignment.getString("kind") + '|'
                + assignment.getInt("targetZoneId") + '|'
                + assignment.optString("targetNode", "") + '|'
                + assignment.getInt("objectiveId") + '|'
                + assignment.getInt("targetCellX") + '|'
                + assignment.getInt("targetCellY");
    }

    private static ConquestMetrics analyzeConquest(ParsedTrace trace) {
        int initialHeld = 0;
        int finalHeld = 0;
        int compoundCount = 0;
        int maxHeld = 0;
        int captures = 0;
        int losses = 0;
        int keepTick = -1;
        int longestGap = 0;
        boolean firstWindow = true;

        for (Map.Entry<Integer, Map<Integer, List<CompoundEvent>>> windowEntry
                : trace.compounds.entrySet()) {
            int window = windowEntry.getKey();
            Map<String, String> states = new HashMap<>();
            int lastCaptureTick = trace.windowStarts.get(window);
            boolean baselineGroup = true;
            for (Map.Entry<Integer, List<CompoundEvent>> entry
                    : windowEntry.getValue().entrySet()) {
                int tick = entry.getKey();
                for (CompoundEvent event : entry.getValue()) {
                    String prior = states.put(event.subject, event.state);
                    if (baselineGroup || prior == null) continue;
                    if (!"MARINE_HELD".equals(prior)
                            && "MARINE_HELD".equals(event.state)) {
                        captures++;
                        longestGap = Math.max(longestGap,
                                tick - lastCaptureTick);
                        lastCaptureTick = tick;
                        if ("COMMAND_POST".equals(event.kind)) keepTick = tick;
                    } else if ("MARINE_HELD".equals(prior)
                            && !"MARINE_HELD".equals(event.state)) {
                        losses++;
                    }
                }
                int held = marineHeld(states);
                if (firstWindow && baselineGroup) initialHeld = held;
                maxHeld = Math.max(maxHeld, held);
                baselineGroup = false;
            }
            int windowEnd = trace.windowEnds.getOrDefault(window,
                    lastCaptureTick);
            longestGap = Math.max(longestGap, windowEnd - lastCaptureTick);
            finalHeld = marineHeld(states);
            compoundCount = Math.max(compoundCount, states.size());
            firstWindow = false;
        }
        boolean progressStalled = trace.termination != Termination.INCOMPLETE
                && compoundCount > 0 && observedDuration(trace) > 0
                && captures == 0 && maxHeld <= initialHeld;
        return new ConquestMetrics(compoundCount, initialHeld, finalHeld,
                maxHeld, captures, losses, keepTick, longestGap,
                progressStalled);
    }

    private static int observedDuration(ParsedTrace trace) {
        int duration = 0;
        for (Map.Entry<Integer, Integer> entry : trace.windowStarts.entrySet()) {
            int end = trace.windowEnds.getOrDefault(entry.getKey(), entry.getValue());
            duration += Math.max(0, end - entry.getValue());
        }
        return duration;
    }

    private static int marineHeld(Map<String, String> states) {
        int held = 0;
        for (String state : states.values()) {
            if ("MARINE_HELD".equals(state)) held++;
        }
        return held;
    }

    private static ParsedTrace parse(String jsonLines) throws Exception {
        if (jsonLines == null || jsonLines.isBlank()) {
            throw new IllegalArgumentException("Command trace is empty");
        }
        ParsedTrace trace = new ParsedTrace();
        int currentWindow = 0;
        boolean paused = false;
        for (String line : jsonLines.split("\\R")) {
            if (line.isBlank()) continue;
            JSONObject row = new JSONObject(line);
            String stream = row.getString("stream");
            int tick = row.getInt("tick");
            trace.maxObservedTick = Math.max(trace.maxObservedTick, tick);
            switch (stream) {
                case "run" -> {
                    if (trace.headerSeen) {
                        throw new IllegalArgumentException("Duplicate run header");
                    }
                    trace.headerSeen = true;
                    if (row.getInt("schemaVersion") != 2) {
                        throw new IllegalArgumentException(
                                "Unsupported command trace schemaVersion: "
                                        + row.getInt("schemaVersion"));
                    }
                    trace.startTick = tick;
                    trace.fixtureKind = nullableString(row, "fixtureKind");
                    trace.schedulerMode = row.getString("schedulerMode");
                    trace.windowStarts.put(currentWindow, tick);
                }
                case "control" -> {
                    requireHeader(trace);
                    String event = row.getString("event");
                    if ("capture-paused".equals(event)) {
                        if (paused) throw new IllegalArgumentException(
                                "Trace capture is already paused");
                        trace.windowEnds.put(currentWindow, tick);
                        paused = true;
                    } else if ("capture-resumed".equals(event)) {
                        if (!paused) throw new IllegalArgumentException(
                                "Trace capture resumed without a pause");
                        currentWindow++;
                        trace.windowStarts.put(currentWindow, tick);
                        paused = false;
                    } else {
                        throw new IllegalArgumentException(
                                "Unknown control event: " + event);
                    }
                }
                case "perspective" -> {
                    requireHeader(trace);
                    if (paused) throw new IllegalArgumentException(
                            "Perspective event inside a capture gap");
                    Faction faction = enumValue(Faction.class,
                            row.getString("perspective"), "perspective");
                    int observedTick = row.getInt("observedTick");
                    trace.maxObservedTick = Math.max(
                            trace.maxObservedTick, observedTick);
                    trace.samples.computeIfAbsent(faction,
                                    ignored -> new ArrayList<>())
                            .add(new PerspectiveSample(row, observedTick,
                                    currentWindow));
                }
                case "referee" -> {
                    requireHeader(trace);
                    if (paused) throw new IllegalArgumentException(
                            "Referee event inside a capture gap");
                    parseReferee(trace, row, tick, currentWindow);
                }
                default -> throw new IllegalArgumentException(
                        "Unknown command trace stream: " + stream);
            }
        }
        requireHeader(trace);
        trace.endTick = trace.termination == Termination.INCOMPLETE
                ? trace.maxObservedTick : trace.endTick;
        if (!paused) trace.windowEnds.put(currentWindow, trace.endTick);
        trace.observationWindows = trace.windowStarts.size();
        return trace;
    }

    private static void parseReferee(ParsedTrace trace, JSONObject row,
                                     int tick, int window) throws Exception {
        String event = row.getString("event");
        switch (event) {
            case "compound-state" -> trace.compounds
                    .computeIfAbsent(window, ignored -> new TreeMap<>())
                    .computeIfAbsent(tick, ignored -> new ArrayList<>())
                    .add(new CompoundEvent(row.getString("subject"),
                            row.getString("compoundKind"),
                            row.getString("state")));
            case "casualty" -> {
                if (row.getBoolean("combatant")) {
                    Faction faction = enumValue(Faction.class,
                            row.getString("faction"), "casualty faction");
                    trace.casualties.merge(faction, 1, Integer::sum);
                }
            }
            case "terminal" -> {
                ensureNoTermination(trace);
                trace.termination = Termination.TERMINAL;
                trace.winner = nullableString(row, "winner");
                trace.endTick = tick;
            }
            case "timeout" -> {
                ensureNoTermination(trace);
                trace.termination = Termination.TIMEOUT;
                trace.endTick = tick;
            }
            default -> throw new IllegalArgumentException(
                    "Unknown referee event: " + event);
        }
    }

    private static void ensureNoTermination(ParsedTrace trace) {
        if (trace.termination != Termination.INCOMPLETE) {
            throw new IllegalArgumentException("Duplicate terminal trace event");
        }
    }

    private static void requireHeader(ParsedTrace trace) {
        if (!trace.headerSeen) {
            throw new IllegalArgumentException("Run header must be first");
        }
    }

    private static String nullableString(JSONObject row, String name)
            throws Exception {
        return row.isNull(name) ? null : row.getString(name);
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown " + field + ": " + value, ex);
        }
    }

    private static <V> Map<Faction, V> immutableFactionMap(
            Map<Faction, V> source) {
        EnumMap<Faction, V> ordered = new EnumMap<>(Faction.class);
        ordered.putAll(source);
        return Collections.unmodifiableMap(ordered);
    }

    private static void appendFactionInts(
            StringBuilder out, Map<Faction, Integer> values) {
        boolean first = true;
        for (Faction faction : Faction.values()) {
            if (!first) out.append(',');
            first = false;
            string(out, faction.name());
            out.append(':').append(values.getOrDefault(faction, 0));
        }
    }

    private static void stringField(StringBuilder out, String name,
                                    String value, boolean comma) {
        if (comma) out.append(',');
        string(out, name);
        out.append(':');
        string(out, value);
    }

    private static void nullableStringField(StringBuilder out, String name,
                                            String value) {
        nullableStringField(out, name, value, true);
    }

    private static void nullableStringField(StringBuilder out, String name,
                                            String value, boolean comma) {
        if (comma) out.append(',');
        string(out, name);
        out.append(':');
        if (value == null) out.append("null");
        else string(out, value);
    }

    private static void rawNumberField(StringBuilder out, String name,
                                       int value) {
        string(out, name);
        out.append(':').append(value);
    }

    private static void numberField(StringBuilder out, String name, int value) {
        out.append(',');
        rawNumberField(out, name, value);
    }

    private static void longField(StringBuilder out, String name, long value) {
        out.append(',');
        string(out, name);
        out.append(':').append(value);
    }

    private static void booleanField(StringBuilder out, String name,
                                     boolean value) {
        out.append(',');
        string(out, name);
        out.append(':').append(value);
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append('"');
    }

    private record DirectiveState(String key, int issuedTick) { }

    private record PerspectiveSample(
            JSONObject row, int observedTick, int window) { }

    private record CompoundEvent(String subject, String kind, String state) { }

    private static final class ThreatState {
        int contacts;
        int startedTick;
        final int window;

        private ThreatState(int contacts, int startedTick, int window) {
            this.contacts = contacts;
            this.startedTick = startedTick;
            this.window = window;
        }
    }

    private static final class ParsedTrace {
        boolean headerSeen;
        String fixtureKind;
        String schedulerMode;
        int startTick;
        int endTick;
        int maxObservedTick;
        int observationWindows;
        Termination termination = Termination.INCOMPLETE;
        String winner;
        final Map<Faction, Integer> casualties = new EnumMap<>(Faction.class);
        final Map<Faction, List<PerspectiveSample>> samples =
                new EnumMap<>(Faction.class);
        final Map<Integer, Map<Integer, List<CompoundEvent>>> compounds =
                new TreeMap<>();
        final Map<Integer, Integer> windowStarts = new LinkedHashMap<>();
        final Map<Integer, Integer> windowEnds = new LinkedHashMap<>();

        int finalWindow() {
            return windowStarts.isEmpty() ? 0 : windowStarts.size() - 1;
        }

        RunMetrics runMetrics() {
            return new RunMetrics(fixtureKind, schedulerMode, startTick, endTick,
                    Math.max(0, endTick - startTick), termination, winner,
                    startTick == 0 && termination == Termination.TERMINAL
                            && observationWindows == 1,
                    observationWindows, casualties);
        }
    }
}
