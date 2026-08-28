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
            int distantCaptureDeferredSquadPulses,
            long reserveSquadTicks,
            List<Integer> publishedMobilizationLatenciesTicks,
            int unmobilizedThreatEpisodes,
            int peakPublishedTrackShareBasisPoints,
            PhysicalProgressMetrics physicalProgress,
            CommandInactivityMetrics commandInactivity) {

        public FactionMetrics {
            publishedMobilizationLatenciesTicks =
                    List.copyOf(publishedMobilizationLatenciesTicks);
            physicalProgress = physicalProgress != null
                    ? physicalProgress : PhysicalProgressMetrics.empty();
            commandInactivity = commandInactivity != null
                    ? commandInactivity : CommandInactivityMetrics.empty();
        }

        public FactionMetrics(int perspectiveSamples, int retargets,
                              int releases, int reissues,
                              int rejectedProposals, int stabilityHolds,
                              int unassignedSquadPulses,
                              int unassignedSquadTicks,
                              int unreachableSquadPulses,
                              int noActionableSquadPulses,
                              long reserveSquadTicks,
                              List<Integer> mobilizationLatencies,
                              int unmobilizedThreatEpisodes,
                              int peakTrackShare) {
            this(perspectiveSamples, retargets, releases, reissues,
                    rejectedProposals, stabilityHolds,
                    unassignedSquadPulses, unassignedSquadTicks,
                    unreachableSquadPulses, noActionableSquadPulses,
                    0, reserveSquadTicks, mobilizationLatencies,
                    unmobilizedThreatEpisodes, peakTrackShare,
                    PhysicalProgressMetrics.empty(),
                    CommandInactivityMetrics.empty());
        }

        public FactionMetrics(int perspectiveSamples, int retargets,
                              int releases, int reissues,
                              int rejectedProposals, int stabilityHolds,
                              int unassignedSquadPulses,
                              int unassignedSquadTicks,
                              int unreachableSquadPulses,
                              int noActionableSquadPulses,
                              int distantCaptureDeferredSquadPulses,
                              long reserveSquadTicks,
                              List<Integer> mobilizationLatencies,
                              int unmobilizedThreatEpisodes,
                              int peakTrackShare,
                              PhysicalProgressMetrics physicalProgress) {
            this(perspectiveSamples, retargets, releases, reissues,
                    rejectedProposals, stabilityHolds,
                    unassignedSquadPulses, unassignedSquadTicks,
                    unreachableSquadPulses, noActionableSquadPulses,
                    distantCaptureDeferredSquadPulses, reserveSquadTicks,
                    mobilizationLatencies, unmobilizedThreatEpisodes,
                    peakTrackShare, physicalProgress,
                    CommandInactivityMetrics.empty());
        }
    }

    /** Mutually-exclusive explanations for command-unassigned Conquest time. */
    public record CommandInactivityMetrics(
            int lifecycleSquadPulses,
            long lifecycleSquadTicks,
            int executionSuspendedSquadPulses,
            long executionSuspendedSquadTicks,
            int localContactSquadPulses,
            long localContactSquadTicks,
            int usefulMovementSquadPulses,
            long usefulMovementSquadTicks,
            int genuineIdleSquadPulses,
            long genuineIdleSquadTicks,
            int unclassifiedSquadPulses,
            long unclassifiedSquadTicks) {

        static CommandInactivityMetrics empty() {
            return new CommandInactivityMetrics(0, 0L, 0, 0L, 0, 0L,
                    0, 0L, 0, 0L, 0, 0L);
        }
    }

    /**
     * Completion and overlapping tactical context for secure-compound travel.
     * Lifecycle and context are deliberately separate: an episode can meet
     * contact, retain an active path, and later exit for any one reason.
     */
    public record SecureTravelMetrics(
            int episodesStarted,
            int targetEntryExits,
            int retargetExits,
            int retargetObjectiveChanged,
            int retargetMarkerChanged,
            int retargetAssignmentChanged,
            int retargetUnclassified,
            int releaseExits,
            int squadLossExits,
            int executionSuspensionExits,
            int observationGapExits,
            int timeoutExits,
            int terminalExits,
            int episodesWithLocalContact,
            int episodesWithActivePath,
            int episodesWithQuietTravel,
            int squadLossLocationsObserved,
            int squadLossLocationsUnknown,
            int squadLossAtLocalContact,
            int squadLossWithTrackBeliefOnly,
            int squadLossWithoutPublishedContact,
            int squadLossWithUnknownTrack,
            int squadLossTacticalObserved,
            int squadLossTacticalUnknown,
            int squadLossWithBreachAction,
            int squadLossWithMovingMembers,
            int squadLossExposedFromPrimary,
            int squadLossDoctrineAdvance,
            int squadLossDoctrineHold,
            int squadLossDoctrineDisengage,
            int squadLossInitiativeNone,
            int squadLossInitiativeReceive,
            int squadLossInitiativeProsecute,
            int squadLossWithEngageableMembers,
            int squadLossWithEngageableFireTeams,
            int squadLossUnderFireRecently,
            int squadLossMajorityCoveredFromPrimary,
            int squadLossCoolingDown,
            List<Integer> squadLossLastDistancesDecicells,
            List<Integer> squadLossApproachProgressBasisPoints) {

        public SecureTravelMetrics {
            squadLossLastDistancesDecicells =
                    sortedCopy(squadLossLastDistancesDecicells);
            squadLossApproachProgressBasisPoints =
                    sortedCopy(squadLossApproachProgressBasisPoints);
            if (retargetObjectiveChanged + retargetMarkerChanged
                    + retargetAssignmentChanged + retargetUnclassified
                    != retargetExits) {
                throw new IllegalArgumentException(
                        "retarget provenance must classify every retarget");
            }
            if (squadLossLocationsObserved + squadLossLocationsUnknown
                    != squadLossExits) {
                throw new IllegalArgumentException(
                        "location accounting must classify every squad loss");
            }
            if (squadLossAtLocalContact + squadLossWithTrackBeliefOnly
                    + squadLossWithoutPublishedContact
                    + squadLossWithUnknownTrack != squadLossExits) {
                throw new IllegalArgumentException(
                        "front context must classify every squad loss");
            }
            if (squadLossTacticalObserved + squadLossTacticalUnknown
                    != squadLossExits) {
                throw new IllegalArgumentException(
                        "tactical observation must classify every squad loss");
            }
        }

        public int episodesFinalized() {
            return targetEntryExits + retargetExits + releaseExits
                    + squadLossExits + executionSuspensionExits
                    + observationGapExits + timeoutExits + terminalExits;
        }

        public int episodesOpen() {
            return Math.max(0, episodesStarted - episodesFinalized());
        }

        static SecureTravelMetrics empty() {
            return new SecureTravelMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    List.of(), List.of());
        }
    }

    public record PhysicalProgressMetrics(
            int squadSamples,
            int maximumConcurrentAliveSquads,
            int maximumConcurrentAliveMembers,
            int movementEpisodes,
            int episodesWithMarkerClosure,
            int episodesObservedInTargetZone,
            int compoundAssaultThresholdCommitments,
            int secureCompoundEpisodes,
            int secureCompoundEpisodesObservedInTargetZone,
            long comparableTravelSquadTicks,
            long markerClosingSquadTicks,
            long nonClosingWithContactSquadTicks,
            long quietNonClosingSquadTicks,
            long targetZoneSquadTicks,
            long suspendedAssignmentSquadTicks,
            List<Integer> targetZoneEntryLatenciesTicks,
            SecureTravelMetrics secureTravel) {

        public PhysicalProgressMetrics {
            targetZoneEntryLatenciesTicks =
                    List.copyOf(targetZoneEntryLatenciesTicks);
            secureTravel = secureTravel != null
                    ? secureTravel : SecureTravelMetrics.empty();
        }

        static PhysicalProgressMetrics empty() {
            return new PhysicalProgressMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0L, 0L, 0L, 0L, 0L, 0L, List.of(),
                    SecureTravelMetrics.empty());
        }
    }

    public record CompoundPresenceMetrics(
            int observationEvents,
            int compoundsWithMarinePresence,
            long observedCompoundTicks,
            long marinePresentCompoundTicks,
            long marineOnlyCompoundTicks,
            long mixedCompoundTicks,
            long defenderOnlyCompoundTicks,
            long emptyCompoundTicks,
            long unresolvedCompoundTicks,
            int longestMarineOnlyPresenceRunTicks,
            int maximumMarineUnits,
            int maximumCaptureProgressBasisPoints) {

        static CompoundPresenceMetrics empty() {
            return new CompoundPresenceMetrics(0, 0, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0, 0, 0);
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
            boolean territorialProgressStalled,
            CompoundPresenceMetrics physicalPresence) {

        public ConquestMetrics {
            physicalPresence = physicalPresence != null
                    ? physicalPresence : CompoundPresenceMetrics.empty();
        }

        public ConquestMetrics(int compoundCount, int initialMarineHeld,
                               int finalMarineHeld, int maximumMarineHeld,
                               int captures, int losses, int keepCaptureTick,
                               int longestObservedCaptureGapTicks,
                               boolean territorialProgressStalled) {
            this(compoundCount, initialMarineHeld, finalMarineHeld,
                    maximumMarineHeld, captures, losses, keepCaptureTick,
                    longestObservedCaptureGapTicks, territorialProgressStalled,
                    CompoundPresenceMetrics.empty());
        }
    }

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
                numberField(out, "distantCaptureDeferredSquadPulses",
                        metrics.distantCaptureDeferredSquadPulses());
                CommandInactivityMetrics inactivity = metrics.commandInactivity();
                out.append(",\"commandInactivity\":{");
                rawNumberField(out, "lifecycleSquadPulses",
                        inactivity.lifecycleSquadPulses());
                longField(out, "lifecycleSquadTicks",
                        inactivity.lifecycleSquadTicks());
                numberField(out, "executionSuspendedSquadPulses",
                        inactivity.executionSuspendedSquadPulses());
                longField(out, "executionSuspendedSquadTicks",
                        inactivity.executionSuspendedSquadTicks());
                numberField(out, "localContactSquadPulses",
                        inactivity.localContactSquadPulses());
                longField(out, "localContactSquadTicks",
                        inactivity.localContactSquadTicks());
                numberField(out, "usefulMovementSquadPulses",
                        inactivity.usefulMovementSquadPulses());
                longField(out, "usefulMovementSquadTicks",
                        inactivity.usefulMovementSquadTicks());
                numberField(out, "genuineIdleSquadPulses",
                        inactivity.genuineIdleSquadPulses());
                longField(out, "genuineIdleSquadTicks",
                        inactivity.genuineIdleSquadTicks());
                numberField(out, "unclassifiedSquadPulses",
                        inactivity.unclassifiedSquadPulses());
                longField(out, "unclassifiedSquadTicks",
                        inactivity.unclassifiedSquadTicks());
                out.append('}');
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
                PhysicalProgressMetrics physical = metrics.physicalProgress();
                out.append(",\"physicalProgress\":{");
                rawNumberField(out, "squadSamples", physical.squadSamples());
                numberField(out, "maximumConcurrentAliveSquads",
                        physical.maximumConcurrentAliveSquads());
                numberField(out, "maximumConcurrentAliveMembers",
                        physical.maximumConcurrentAliveMembers());
                numberField(out, "movementEpisodes", physical.movementEpisodes());
                numberField(out, "episodesWithMarkerClosure",
                        physical.episodesWithMarkerClosure());
                numberField(out, "episodesObservedInTargetZone",
                        physical.episodesObservedInTargetZone());
                numberField(out, "compoundAssaultThresholdCommitments",
                        physical.compoundAssaultThresholdCommitments());
                numberField(out, "secureCompoundEpisodes",
                        physical.secureCompoundEpisodes());
                numberField(out, "secureCompoundEpisodesObservedInTargetZone",
                        physical.secureCompoundEpisodesObservedInTargetZone());
                longField(out, "comparableTravelSquadTicks",
                        physical.comparableTravelSquadTicks());
                longField(out, "markerClosingSquadTicks",
                        physical.markerClosingSquadTicks());
                longField(out, "nonClosingWithContactSquadTicks",
                        physical.nonClosingWithContactSquadTicks());
                longField(out, "quietNonClosingSquadTicks",
                        physical.quietNonClosingSquadTicks());
                longField(out, "targetZoneSquadTicks",
                        physical.targetZoneSquadTicks());
                longField(out, "suspendedAssignmentSquadTicks",
                        physical.suspendedAssignmentSquadTicks());
                out.append(",\"targetZoneEntryLatenciesTicks\":[");
                for (int i = 0; i < physical.targetZoneEntryLatenciesTicks().size(); i++) {
                    if (i > 0) out.append(',');
                    out.append(physical.targetZoneEntryLatenciesTicks().get(i));
                }
                out.append(']');
                SecureTravelMetrics secure = physical.secureTravel();
                out.append(",\"secureTravelEpisodes\":{");
                rawNumberField(out, "started", secure.episodesStarted());
                numberField(out, "finalized", secure.episodesFinalized());
                numberField(out, "open", secure.episodesOpen());
                out.append(",\"exits\":{");
                rawNumberField(out, "targetEntry", secure.targetEntryExits());
                numberField(out, "retarget", secure.retargetExits());
                numberField(out, "release", secure.releaseExits());
                numberField(out, "squadLoss", secure.squadLossExits());
                numberField(out, "executionSuspension",
                        secure.executionSuspensionExits());
                numberField(out, "observationGap",
                        secure.observationGapExits());
                numberField(out, "timeout", secure.timeoutExits());
                numberField(out, "terminalResult", secure.terminalExits());
                out.append('}');
                out.append(",\"retargetProvenance\":{");
                rawNumberField(out, "objectiveChanged",
                        secure.retargetObjectiveChanged());
                numberField(out, "markerChanged",
                        secure.retargetMarkerChanged());
                numberField(out, "assignmentChanged",
                        secure.retargetAssignmentChanged());
                numberField(out, "unclassified",
                        secure.retargetUnclassified());
                out.append('}');
                appendIntList(out, "squadLossLastDistancesDecicells",
                        secure.squadLossLastDistancesDecicells());
                appendIntList(out, "squadLossApproachProgressBasisPoints",
                        secure.squadLossApproachProgressBasisPoints());
                out.append(",\"squadLossFrontContext\":{");
                rawNumberField(out, "observed",
                        secure.squadLossLocationsObserved());
                numberField(out, "unknown",
                        secure.squadLossLocationsUnknown());
                numberField(out, "localContact",
                        secure.squadLossAtLocalContact());
                numberField(out, "trackBeliefOnly",
                        secure.squadLossWithTrackBeliefOnly());
                numberField(out, "noPublishedContact",
                        secure.squadLossWithoutPublishedContact());
                numberField(out, "unknownTrack",
                        secure.squadLossWithUnknownTrack());
                out.append('}');
                out.append(",\"squadLossTacticalContact\":{");
                rawNumberField(out, "observed", secure.squadLossTacticalObserved());
                numberField(out, "unknown", secure.squadLossTacticalUnknown());
                numberField(out, "breachAction",
                        secure.squadLossWithBreachAction());
                numberField(out, "moving", secure.squadLossWithMovingMembers());
                numberField(out, "exposedFromPrimary",
                        secure.squadLossExposedFromPrimary());
                out.append(",\"doctrine\":{");
                rawNumberField(out, "advance", secure.squadLossDoctrineAdvance());
                numberField(out, "hold", secure.squadLossDoctrineHold());
                numberField(out, "disengage",
                        secure.squadLossDoctrineDisengage());
                out.append('}');
                out.append(",\"initiative\":{");
                rawNumberField(out, "none", secure.squadLossInitiativeNone());
                numberField(out, "receive", secure.squadLossInitiativeReceive());
                numberField(out, "prosecute",
                        secure.squadLossInitiativeProsecute());
                out.append('}');
                numberField(out, "withEngageableMembers",
                        secure.squadLossWithEngageableMembers());
                numberField(out, "withEngageableFireTeams",
                        secure.squadLossWithEngageableFireTeams());
                numberField(out, "underFireRecently",
                        secure.squadLossUnderFireRecently());
                numberField(out, "majorityCoveredFromPrimary",
                        secure.squadLossMajorityCoveredFromPrimary());
                numberField(out, "coolingDown", secure.squadLossCoolingDown());
                out.append('}');
                numberField(out, "withLocalContact",
                        secure.episodesWithLocalContact());
                numberField(out, "withActivePath",
                        secure.episodesWithActivePath());
                numberField(out, "withQuietTravel",
                        secure.episodesWithQuietTravel());
                out.append("}}");
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
            CompoundPresenceMetrics presence = conquest.physicalPresence();
            out.append(",\"physicalPresence\":{");
            rawNumberField(out, "observationEvents", presence.observationEvents());
            numberField(out, "compoundsWithMarinePresence",
                    presence.compoundsWithMarinePresence());
            longField(out, "observedCompoundTicks",
                    presence.observedCompoundTicks());
            longField(out, "marinePresentCompoundTicks",
                    presence.marinePresentCompoundTicks());
            longField(out, "marineOnlyCompoundTicks",
                    presence.marineOnlyCompoundTicks());
            longField(out, "mixedCompoundTicks", presence.mixedCompoundTicks());
            longField(out, "defenderOnlyCompoundTicks",
                    presence.defenderOnlyCompoundTicks());
            longField(out, "emptyCompoundTicks", presence.emptyCompoundTicks());
            longField(out, "unresolvedCompoundTicks",
                    presence.unresolvedCompoundTicks());
            numberField(out, "longestMarineOnlyPresenceRunTicks",
                    presence.longestMarineOnlyPresenceRunTicks());
            numberField(out, "maximumMarineUnits",
                    presence.maximumMarineUnits());
            numberField(out, "maximumCaptureProgressBasisPoints",
                    presence.maximumCaptureProgressBasisPoints());
            out.append('}');
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
                        trace.termination, trace.finalWindow(),
                        trace.capturePausedAtEnd,
                        trace.schemaVersion));
            }
        }
        return new Analysis(trace.runMetrics(), factions,
                analyzeConquest(trace));
    }

    private static FactionMetrics analyzeFaction(
            Faction faction, List<PerspectiveSample> samples,
            Map<Integer, Integer> windowStarts,
            Map<Integer, Integer> windowEnds,
            Termination termination, int finalTraceWindow,
            boolean capturePausedAtEnd,
            int schemaVersion) throws Exception {
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
        int distantCaptureDeferredPulses = 0;
        InactivityAccumulator inactivity = new InactivityAccumulator();
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
            Map<Integer, JSONObject> squadStates = bySquad(
                    conquest.optJSONArray("squads"));
            Map<Integer, Boolean> respondingTracks = new HashMap<>();
            int unassignedNow = 0;
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                String reason = action.getString("reason");
                if (!baseline && action.optBoolean(
                        "distantCaptureDeferred", false)) {
                    distantCaptureDeferredPulses++;
                }
                if ("NO_REACHABLE_COMPOUND_TARGET".equals(reason)) {
                    if (!baseline) {
                        unreachablePulses++;
                        unassignedPulses++;
                    }
                    unassignedNow++;
                    inactivity.add(inactivityCause(schemaVersion,
                                    squadStates.get(action.getInt("squadId"))),
                            !baseline, intervalTicks);
                } else if ("NO_ACTIONABLE_TRACK_TARGET".equals(reason)) {
                    if (!baseline) {
                        noActionablePulses++;
                        unassignedPulses++;
                    }
                    unassignedNow++;
                    inactivity.add(inactivityCause(schemaVersion,
                                    squadStates.get(action.getInt("squadId"))),
                            !baseline, intervalTicks);
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

        PhysicalProgressMetrics physical = analyzePhysicalProgress(
                samples, windowStarts, windowEnds, termination,
                finalTraceWindow, capturePausedAtEnd,
                schemaVersion);
        return new FactionMetrics(samples.size(), retargets, releases,
                reissues, rejected, stabilityHolds, unassignedPulses,
                unassignedTicks, unreachablePulses, noActionablePulses,
                distantCaptureDeferredPulses, reserveTicks, latencies,
                unanswered, peakShare, physical, inactivity.metrics());
    }

    private static InactivityCause inactivityCause(int schemaVersion,
                                                    JSONObject state) {
        if (schemaVersion < 6) return InactivityCause.UNCLASSIFIED;
        if (state == null || state.optInt("aliveMembers", 0) <= 0) {
            return InactivityCause.LIFECYCLE;
        }
        if (!state.isNull("executionSuspension")) {
            return InactivityCause.EXECUTION_SUSPENDED;
        }
        if (state.optBoolean("localContact", false)) {
            return InactivityCause.LOCAL_CONTACT;
        }
        if (state.optInt("activePathMembers", 0) > 0) {
            return InactivityCause.USEFUL_MOVEMENT;
        }
        return InactivityCause.GENUINE_IDLE;
    }

    private enum InactivityCause {
        LIFECYCLE,
        EXECUTION_SUSPENDED,
        LOCAL_CONTACT,
        USEFUL_MOVEMENT,
        GENUINE_IDLE,
        UNCLASSIFIED
    }

    private static final class InactivityAccumulator {
        private final int[] pulses = new int[InactivityCause.values().length];
        private final long[] ticks = new long[InactivityCause.values().length];

        void add(InactivityCause cause, boolean countPulse, int intervalTicks) {
            int index = cause.ordinal();
            if (countPulse) pulses[index]++;
            ticks[index] += intervalTicks;
        }

        CommandInactivityMetrics metrics() {
            return new CommandInactivityMetrics(
                    pulses[0], ticks[0], pulses[1], ticks[1],
                    pulses[2], ticks[2], pulses[3], ticks[3],
                    pulses[4], ticks[4], pulses[5], ticks[5]);
        }
    }

    private static PhysicalProgressMetrics analyzePhysicalProgress(
            List<PerspectiveSample> samples,
            Map<Integer, Integer> windowStarts,
            Map<Integer, Integer> windowEnds,
            Termination termination,
            int finalTraceWindow,
            boolean capturePausedAtEnd,
            int schemaVersion) throws Exception {
        if (schemaVersion < 3) return PhysicalProgressMetrics.empty();
        Map<Integer, MovementEpisode> episodes = new HashMap<>();
        int squadSamples = 0;
        int maximumAliveSquads = 0;
        int maximumAliveMembers = 0;
        int movementEpisodes = 0;
        int episodesWithClosure = 0;
        int episodesInZone = 0;
        int thresholdCommitments = 0;
        int secureEpisodes = 0;
        int secureEpisodesInZone = 0;
        long comparableTicks = 0L;
        long closingTicks = 0L;
        long contactTicks = 0L;
        long quietTicks = 0L;
        long targetZoneTicks = 0L;
        long suspendedTicks = 0L;
        List<Integer> entryLatencies = new ArrayList<>();

        for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
            PerspectiveSample sample = samples.get(sampleIndex);
            boolean baseline = sample.observedTick
                    == windowStarts.getOrDefault(sample.window,
                    Integer.MIN_VALUE);
            JSONObject conquest = sample.row.optJSONObject("conquest");
            if (conquest == null) continue;
            int intervalEnd = intervalEnd(samples, sampleIndex, windowEnds);
            int intervalTicks = Math.max(0, intervalEnd - sample.observedTick);
            Map<Integer, JSONObject> states = bySquad(
                    conquest.optJSONArray("squads"));
            Map<Integer, JSONObject> effectiveDirectives = bySquad(
                    sample.row.optJSONArray("directives"));
            int aliveSquads = 0;
            int aliveMembers = 0;
            for (JSONObject state : states.values()) {
                int members = state.getInt("aliveMembers");
                if (members <= 0) continue;
                aliveSquads++;
                aliveMembers += members;
            }
            maximumAliveSquads = Math.max(maximumAliveSquads, aliveSquads);
            maximumAliveMembers = Math.max(maximumAliveMembers, aliveMembers);
            JSONArray actions = conquest.getJSONArray("actions");
            Map<Integer, Boolean> observed = new HashMap<>();
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                int squadId = action.getInt("squadId");
                JSONObject state = states.get(squadId);
                if (state == null) continue;
                squadSamples++;
                observed.put(squadId, true);
                JSONObject effectiveDirective = effectiveDirectives.get(squadId);
                if (!matchesEffectiveDirective(sample.row, action,
                        effectiveDirective)) {
                    episodes.remove(squadId);
                    continue;
                }
                String assignmentKind = nullableString(action, "assignmentKind");
                if (assignmentKind == null) {
                    episodes.remove(squadId);
                    continue;
                }
                int liveMembers = state.getInt("aliveMembers");
                if (liveMembers <= 0) {
                    episodes.remove(squadId);
                    continue;
                }
                if (!state.isNull("executionSuspension")) {
                    suspendedTicks += intervalTicks;
                    episodes.remove(squadId);
                    continue;
                }
                int markerX = action.getInt("markerCellX");
                int markerY = action.getInt("markerCellY");
                int targetZone = action.getInt("targetZoneId");
                if (markerX < 0 || markerY < 0) {
                    episodes.remove(squadId);
                    continue;
                }
                String key = directiveIdentity(effectiveDirective, assignmentKind,
                        targetZone, markerX, markerY);
                double dx = state.getDouble("centroidX") - (markerX + 0.5);
                double dy = state.getDouble("centroidY") - (markerY + 0.5);
                double distance = Math.sqrt(dx * dx + dy * dy);
                MovementEpisode episode = episodes.get(squadId);
                if (episode == null || !episode.key.equals(key)) {
                    if (episode != null && !baseline
                            && episode.previousTick < sample.observedTick) {
                        double oldDx = state.getDouble("centroidX")
                                - (episode.markerX + 0.5);
                        double oldDy = state.getDouble("centroidY")
                                - (episode.markerY + 0.5);
                        double oldDistance = Math.sqrt(
                                oldDx * oldDx + oldDy * oldDy);
                        int ticks = sample.observedTick - episode.previousTick;
                        if (!episode.previousInTargetZone) {
                            comparableTicks += ticks;
                            if (oldDistance
                                    < episode.previousDistance - 0.25) {
                                closingTicks += ticks;
                            } else if (episode.previousLocalContact
                                    || state.getBoolean("localContact")) {
                                contactTicks += ticks;
                            } else {
                                quietTicks += ticks;
                            }
                        }
                        episode.minimumDistance = Math.min(
                                episode.minimumDistance, oldDistance);
                        if (!episode.closedRange
                                && episode.initialDistance
                                - episode.minimumDistance >= 1.0) {
                            episode.closedRange = true;
                            episodesWithClosure++;
                        }
                        boolean reachedOldZone = episode.targetZone >= 0
                                && (state.getInt("currentZoneId")
                                == episode.targetZone
                                || (schemaVersion >= 7
                                && targetZone == episode.targetZone
                                && state.getInt("membersInTargetZone") > 0));
                        if (reachedOldZone && !episode.observedInTargetZone) {
                            episode.observedInTargetZone = true;
                            episodesInZone++;
                            if (episode.secureCompound) secureEpisodesInZone++;
                            entryLatencies.add(sample.observedTick
                                    - episode.startedTick);
                        }
                    }
                    episode = new MovementEpisode(key, sample.observedTick,
                            distance, "SECURE_COMPOUND".equals(assignmentKind),
                            markerX, markerY, targetZone);
                    episodes.put(squadId, episode);
                    movementEpisodes++;
                    if (episode.secureCompound) secureEpisodes++;
                } else if (!baseline
                        && episode.previousTick < sample.observedTick) {
                    int ticks = sample.observedTick - episode.previousTick;
                    boolean inZoneBefore = episode.previousInTargetZone;
                    if (!inZoneBefore) {
                        comparableTicks += ticks;
                        if (distance < episode.previousDistance - 0.25) {
                            closingTicks += ticks;
                        } else if (episode.previousLocalContact
                                || state.getBoolean("localContact")) {
                            contactTicks += ticks;
                        } else {
                            quietTicks += ticks;
                        }
                    }
                }
                if (baseline) {
                    episode.initialDistance = distance;
                    episode.minimumDistance = distance;
                }
                if (!episode.adjacentCommitted
                        && "COMPOUND_ASSAULT_ADJACENT".equals(
                        action.getString("reason"))
                        && (!baseline || sample.window == 0)) {
                    episode.adjacentCommitted = true;
                    thresholdCommitments++;
                }
                if (distance < episode.minimumDistance) {
                    episode.minimumDistance = distance;
                }
                if (!episode.closedRange
                        && episode.initialDistance - episode.minimumDistance >= 1.0) {
                    episode.closedRange = true;
                    episodesWithClosure++;
                }
                boolean inTargetZone = targetZone >= 0
                        && (schemaVersion >= 7
                        ? state.getInt("membersInTargetZone") > 0
                        : state.getInt("currentZoneId") == targetZone);
                if (inTargetZone) targetZoneTicks += intervalTicks;
                if (inTargetZone && !episode.observedInTargetZone) {
                    episode.observedInTargetZone = true;
                    episodesInZone++;
                    if (episode.secureCompound) secureEpisodesInZone++;
                    if (!baseline) {
                        entryLatencies.add(
                                sample.observedTick - episode.startedTick);
                    }
                }
                episode.previousTick = sample.observedTick;
                episode.previousDistance = distance;
                episode.previousInTargetZone = inTargetZone;
                episode.previousLocalContact = state.getBoolean("localContact");
            }
            episodes.keySet().removeIf(squadId -> !observed.containsKey(squadId));
        }
        SecureTravelMetrics secureTravel = analyzeSecureTravel(samples,
                termination, finalTraceWindow, capturePausedAtEnd,
                schemaVersion);
        return new PhysicalProgressMetrics(squadSamples, maximumAliveSquads,
                maximumAliveMembers, movementEpisodes,
                episodesWithClosure, episodesInZone, thresholdCommitments,
                secureEpisodes, secureEpisodesInZone, comparableTicks,
                closingTicks, contactTicks, quietTicks, targetZoneTicks,
                suspendedTicks, entryLatencies, secureTravel);
    }

    /**
     * Secure travel has a stricter lifecycle than the legacy movement totals:
     * every observable segment finishes exactly once, while a successful entry
     * remains tombstoned until the directive changes so holding the room cannot
     * manufacture another trip.
     */
    private static SecureTravelMetrics analyzeSecureTravel(
            List<PerspectiveSample> samples,
            Termination termination,
            int finalTraceWindow,
            boolean capturePausedAtEnd,
            int schemaVersion) throws Exception {
        Map<Integer, SecureTravelEpisode> active = new HashMap<>();
        Map<Integer, String> completedKeys = new HashMap<>();
        SecureTravelAccumulator metrics = new SecureTravelAccumulator();
        int currentWindow = -1;

        for (PerspectiveSample sample : samples) {
            boolean baseline = sample.window != currentWindow;
            if (baseline) {
                if (currentWindow >= 0) {
                    finishAll(active, metrics,
                            SecureTravelExit.OBSERVATION_GAP);
                    completedKeys.clear();
                }
                currentWindow = sample.window;
            }

            JSONObject conquest = sample.row.optJSONObject("conquest");
            if (conquest == null) continue;
            Map<Integer, JSONObject> states = bySquad(
                    conquest.optJSONArray("squads"));
            Map<Integer, JSONObject> directives = bySquad(
                    sample.row.optJSONArray("directives"));
            Map<Integer, JSONObject> actions = bySquad(
                    conquest.optJSONArray("actions"));

            for (Map.Entry<Integer, SecureTravelEpisode> entry
                    : new ArrayList<>(active.entrySet())) {
                int squadId = entry.getKey();
                SecureTravelEpisode episode = entry.getValue();
                JSONObject state = states.get(squadId);
                JSONObject directive = directives.get(squadId);
                JSONObject action = actions.get(squadId);

                if (state == null) {
                    metrics.finish(episode,
                            SecureTravelExit.OBSERVATION_GAP);
                    active.remove(squadId);
                    continue;
                }
                if (state.optInt("aliveMembers", 0) <= 0) {
                    metrics.finishLoss(episode);
                    active.remove(squadId);
                    completedKeys.remove(squadId);
                    continue;
                }

                metrics.observeContext(episode, state, action, sample.row,
                        schemaVersion);
                SecureTravelCandidate candidate = secureTravelCandidate(
                        sample.row, action, directive);
                boolean sameCandidate = candidate != null
                        && candidate.key.equals(episode.key);
                boolean enteredOldTarget = state.optInt("currentZoneId", -1)
                        == episode.targetZone
                        || (schemaVersion >= 7 && sameCandidate
                        && state.optInt("membersInTargetZone", 0) > 0);
                if (enteredOldTarget) {
                    metrics.finish(episode, SecureTravelExit.TARGET_ENTRY);
                    active.remove(squadId);
                    if (sameCandidate) {
                        completedKeys.put(squadId, episode.key);
                    }
                    continue;
                }
                if (!state.isNull("executionSuspension")) {
                    metrics.finish(episode,
                            SecureTravelExit.EXECUTION_SUSPENDED);
                    active.remove(squadId);
                    completedKeys.remove(squadId);
                    continue;
                }
                if (isRejected(directive)) {
                    // A rejected proposal does not revoke the incumbent that
                    // was governing the previous physical sample.
                    continue;
                }
                if (sameCandidate) continue;
                if (candidate != null
                        || assignmentRetargets(directive, sample.row,
                        episode.targetZone)) {
                    metrics.finishRetarget(episode,
                            retargetProvenance(episode, candidate, directive,
                                    sample.row));
                } else if (isExplicitRelease(directive)
                        || directive == null) {
                    metrics.finish(episode, SecureTravelExit.RELEASED);
                } else {
                    // A present own squad with no coherent action/directive
                    // pair is censored, not silently declared released.
                    metrics.finish(episode,
                            SecureTravelExit.OBSERVATION_GAP);
                }
                active.remove(squadId);
                completedKeys.remove(squadId);
            }

            for (Map.Entry<Integer, JSONObject> entry : actions.entrySet()) {
                int squadId = entry.getKey();
                JSONObject state = states.get(squadId);
                if (state == null || state.optInt("aliveMembers", 0) <= 0
                        || !state.isNull("executionSuspension")) continue;
                SecureTravelCandidate candidate = secureTravelCandidate(
                        sample.row, entry.getValue(), directives.get(squadId));
                if (candidate == null) {
                    completedKeys.remove(squadId);
                    continue;
                }
                SecureTravelEpisode incumbent = active.get(squadId);
                if (incumbent != null && incumbent.key.equals(candidate.key)) {
                    metrics.observeContext(incumbent, state, entry.getValue(),
                            sample.row, schemaVersion);
                    continue;
                }
                if (candidate.key.equals(completedKeys.get(squadId))) continue;
                completedKeys.remove(squadId);

                boolean inTargetZone = state.optInt("currentZoneId", -1)
                        == candidate.targetZone
                        || (schemaVersion >= 7
                        && state.optInt("membersInTargetZone", 0) > 0);
                if (baseline && inTargetZone) {
                    // The trip began outside the observation window. Suppress
                    // it until the assignment changes rather than inventing a
                    // zero-latency success at resume.
                    completedKeys.put(squadId, candidate.key);
                    continue;
                }

                SecureTravelEpisode episode = new SecureTravelEpisode(
                        candidate, state);
                active.put(squadId, episode);
                metrics.start();
                metrics.observeContext(episode, state, entry.getValue(),
                        sample.row, schemaVersion);
                if (inTargetZone) {
                    metrics.finish(episode, SecureTravelExit.TARGET_ENTRY);
                    active.remove(squadId);
                    completedKeys.put(squadId, candidate.key);
                }
            }
        }

        if (!active.isEmpty()) {
            if (capturePausedAtEnd || currentWindow < finalTraceWindow) {
                finishAll(active, metrics,
                        SecureTravelExit.OBSERVATION_GAP);
            } else if (termination == Termination.TIMEOUT) {
                finishAll(active, metrics, SecureTravelExit.TIMEOUT);
            } else if (termination == Termination.TERMINAL) {
                finishAll(active, metrics, SecureTravelExit.TERMINAL);
            }
        }
        return metrics.result();
    }

    private static SecureTravelCandidate secureTravelCandidate(
            JSONObject perspective, JSONObject action, JSONObject directive)
            throws Exception {
        if (action == null || directive == null
                || isRejected(directive)
                || !matchesEffectiveDirective(perspective, action, directive)
                || !"SECURE_COMPOUND".equals(
                nullableString(action, "assignmentKind"))) return null;
        int markerX = action.getInt("markerCellX");
        int markerY = action.getInt("markerCellY");
        int targetZone = action.getInt("targetZoneId");
        if (markerX < 0 || markerY < 0 || targetZone < 0) return null;
        String key = directive.getString("issuer") + "|SECURE_COMPOUND|"
                + targetZone + '|' + markerX + '|' + markerY;
        return new SecureTravelCandidate(key, targetZone, markerX, markerY);
    }

    private static SecureTravelRetarget retargetProvenance(
            SecureTravelEpisode episode, SecureTravelCandidate candidate,
            JSONObject directive, JSONObject perspective) throws Exception {
        if (candidate != null) {
            if (candidate.targetZone != episode.targetZone) {
                return SecureTravelRetarget.OBJECTIVE_CHANGED;
            }
            if (candidate.markerX != episode.markerX
                    || candidate.markerY != episode.markerY) {
                return SecureTravelRetarget.MARKER_CHANGED;
            }
            return SecureTravelRetarget.UNCLASSIFIED;
        }
        if (directive == null || isRejected(directive)
                || !perspective.getString("strategy").equals(
                directive.getString("issuer"))) {
            return SecureTravelRetarget.UNCLASSIFIED;
        }
        JSONObject assignment = directive.optJSONObject("assignment");
        if (assignment == null) return SecureTravelRetarget.UNCLASSIFIED;
        if (!"SECURE_COMPOUND".equals(assignment.getString("kind"))) {
            return SecureTravelRetarget.ASSIGNMENT_CHANGED;
        }
        return assignment.getInt("targetZoneId") != episode.targetZone
                ? SecureTravelRetarget.OBJECTIVE_CHANGED
                : SecureTravelRetarget.UNCLASSIFIED;
    }

    private static boolean assignmentRetargets(
            JSONObject directive, JSONObject perspective, int oldTargetZone)
            throws Exception {
        if (directive == null || isRejected(directive)
                || !perspective.getString("strategy").equals(
                directive.getString("issuer"))) return false;
        JSONObject assignment = directive.optJSONObject("assignment");
        if (assignment == null) return false;
        return !"SECURE_COMPOUND".equals(assignment.getString("kind"))
                || assignment.getInt("targetZoneId") != oldTargetZone;
    }

    private static boolean isExplicitRelease(JSONObject directive)
            throws Exception {
        if (directive == null) return false;
        String status = directive.getString("status");
        return "RELEASED".equals(status) || "UNASSIGNED".equals(status)
                || directive.optJSONObject("assignment") == null;
    }

    private static boolean isRejected(JSONObject directive) throws Exception {
        return directive != null
                && "REJECTED".equals(directive.getString("status"));
    }

    private static void finishAll(
            Map<Integer, SecureTravelEpisode> active,
            SecureTravelAccumulator metrics,
            SecureTravelExit exit) {
        for (SecureTravelEpisode episode : active.values()) {
            metrics.finish(episode, exit);
        }
        active.clear();
    }

    private static Map<Integer, JSONObject> bySquad(JSONArray rows)
            throws Exception {
        Map<Integer, JSONObject> bySquad = new HashMap<>();
        if (rows == null) return bySquad;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            bySquad.put(row.getInt("squadId"), row);
        }
        return bySquad;
    }

    private static boolean matchesEffectiveDirective(
            JSONObject perspective, JSONObject action, JSONObject directive)
            throws Exception {
        if (directive == null || "REJECTED".equals(
                directive.getString("status"))) return false;
        if (!perspective.getString("strategy").equals(
                directive.getString("issuer"))) return false;
        JSONObject assignment = directive.optJSONObject("assignment");
        String actionKind = nullableString(action, "assignmentKind");
        if (assignment == null || actionKind == null) {
            return assignment == null && actionKind == null;
        }
        return actionKind.equals(assignment.getString("kind"))
                && action.getInt("targetZoneId")
                == assignment.getInt("targetZoneId")
                && action.getInt("targetCellX")
                == assignment.getInt("targetCellX")
                && action.getInt("targetCellY")
                == assignment.getInt("targetCellY");
    }

    private static String directiveIdentity(
            JSONObject directive, String assignmentKind, int targetZone,
            int markerX, int markerY) throws Exception {
        return directive.getString("issuer") + '|'
                + directive.getInt("issuedTick") + '|'
                + assignmentKind + '|' + targetZone + '|'
                + markerX + '|' + markerY;
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
        CompoundPresenceMetrics physicalPresence =
                analyzeCompoundPresence(trace);
        return new ConquestMetrics(compoundCount, initialHeld, finalHeld,
                maxHeld, captures, losses, keepTick, longestGap,
                progressStalled, physicalPresence);
    }

    private static CompoundPresenceMetrics analyzeCompoundPresence(
            ParsedTrace trace) {
        int events = 0;
        long observedTicks = 0L;
        long marinePresentTicks = 0L;
        long marineOnlyTicks = 0L;
        long mixedTicks = 0L;
        long defenderOnlyTicks = 0L;
        long emptyTicks = 0L;
        long unresolvedTicks = 0L;
        int longestMarineOnly = 0;
        int maxMarineUnits = 0;
        int maxProgress = 0;
        Map<String, Boolean> compoundsWithMarines = new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, List<CompoundPresenceEvent>>>
                windowEntry : trace.compoundPresence.entrySet()) {
            int window = windowEntry.getKey();
            int lastTick = trace.windowStarts.getOrDefault(window, 0);
            Map<String, CompoundPresenceEvent> states = new HashMap<>();
            Map<String, Integer> marineOnlyStarts = new HashMap<>();
            for (Map.Entry<Integer, List<CompoundPresenceEvent>> tickEntry
                    : windowEntry.getValue().entrySet()) {
                int tick = tickEntry.getKey();
                int span = Math.max(0, tick - lastTick);
                for (CompoundPresenceEvent state : states.values()) {
                    observedTicks += span;
                    switch (state.occupancy) {
                        case "MARINE_ONLY" -> {
                            marinePresentTicks += span;
                            marineOnlyTicks += span;
                        }
                        case "MIXED" -> {
                            marinePresentTicks += span;
                            mixedTicks += span;
                        }
                        case "DEFENDER_ONLY" -> defenderOnlyTicks += span;
                        case "EMPTY" -> emptyTicks += span;
                        case "UNRESOLVED" -> unresolvedTicks += span;
                        default -> throw new IllegalArgumentException(
                                "Unknown compound occupancy: " + state.occupancy);
                    }
                }
                for (CompoundPresenceEvent event : tickEntry.getValue()) {
                    events++;
                    CompoundPresenceEvent prior = states.put(event.subject, event);
                    if (event.marineUnits > 0) {
                        compoundsWithMarines.put(event.subject, true);
                    }
                    maxMarineUnits = Math.max(maxMarineUnits, event.marineUnits);
                    maxProgress = Math.max(maxProgress,
                            event.captureProgressBasisPoints);
                    boolean wasMarineOnly = prior != null
                            && "MARINE_ONLY".equals(prior.occupancy);
                    boolean nowMarineOnly = "MARINE_ONLY".equals(event.occupancy);
                    if (!wasMarineOnly && nowMarineOnly) {
                        marineOnlyStarts.put(event.subject, tick);
                    } else if (wasMarineOnly && !nowMarineOnly) {
                        Integer start = marineOnlyStarts.remove(event.subject);
                        if (start != null) {
                            longestMarineOnly = Math.max(longestMarineOnly,
                                    tick - start);
                        }
                    }
                }
                lastTick = tick;
            }
            int windowEnd = trace.windowEnds.getOrDefault(window, lastTick);
            int span = Math.max(0, windowEnd - lastTick);
            for (CompoundPresenceEvent state : states.values()) {
                observedTicks += span;
                switch (state.occupancy) {
                    case "MARINE_ONLY" -> {
                        marinePresentTicks += span;
                        marineOnlyTicks += span;
                    }
                    case "MIXED" -> {
                        marinePresentTicks += span;
                        mixedTicks += span;
                    }
                    case "DEFENDER_ONLY" -> defenderOnlyTicks += span;
                    case "EMPTY" -> emptyTicks += span;
                    case "UNRESOLVED" -> unresolvedTicks += span;
                    default -> throw new IllegalArgumentException(
                            "Unknown compound occupancy: " + state.occupancy);
                }
            }
            for (Integer start : marineOnlyStarts.values()) {
                longestMarineOnly = Math.max(longestMarineOnly,
                        windowEnd - start);
            }
        }
        return new CompoundPresenceMetrics(events, compoundsWithMarines.size(),
                observedTicks, marinePresentTicks, marineOnlyTicks, mixedTicks,
                defenderOnlyTicks, emptyTicks, unresolvedTicks,
                longestMarineOnly, maxMarineUnits, maxProgress);
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
                    int schemaVersion = row.getInt("schemaVersion");
                    if (schemaVersion != 2 && schemaVersion != 3
                            && schemaVersion != 4 && schemaVersion != 5
                            && schemaVersion != 6 && schemaVersion != 7
                            && schemaVersion != 8) {
                        throw new IllegalArgumentException(
                                "Unsupported command trace schemaVersion: "
                                        + schemaVersion);
                    }
                    trace.schemaVersion = schemaVersion;
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
        trace.capturePausedAtEnd = paused;
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
            case "compound-presence" -> trace.compoundPresence
                    .computeIfAbsent(requireSchema3(trace, window),
                            ignored -> new TreeMap<>())
                    .computeIfAbsent(tick, ignored -> new ArrayList<>())
                    .add(new CompoundPresenceEvent(row.getString("subject"),
                            row.getString("occupancy"),
                            row.getInt("marineUnits"),
                            row.getInt("defenderUnits"),
                            row.getInt("captureProgressBasisPoints")));
            case "casualty" -> {
                if (row.getBoolean("combatant")) {
                    Faction faction = enumValue(Faction.class,
                            row.getString("faction"), "casualty faction");
                    trace.casualties.merge(faction, 1, Integer::sum);
                }
            }
            case "charge-site-state" -> {
                if (trace.schemaVersion < 4) {
                    throw new IllegalArgumentException(
                            "charge-site-state requires command trace schemaVersion 4");
                }
                row.getString("siteId");
                row.getInt("progressBasisPoints");
                row.getBoolean("planterOnSite");
                row.getBoolean("complete");
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

    private static int requireSchema3(ParsedTrace trace, int window) {
        if (trace.schemaVersion < 3) {
            throw new IllegalArgumentException(
                    "compound-presence requires command trace schemaVersion 3");
        }
        return window;
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

    private static List<Integer> sortedCopy(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return List.copyOf(sorted);
    }

    private static void appendIntList(StringBuilder out, String name,
                                      List<Integer> values) {
        out.append(',');
        string(out, name);
        out.append(':').append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(values.get(i));
        }
        out.append(']');
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

    private record CompoundPresenceEvent(
            String subject, String occupancy, int marineUnits,
            int defenderUnits, int captureProgressBasisPoints) { }

    private enum SecureTravelExit {
        TARGET_ENTRY,
        RETARGETED,
        RELEASED,
        SQUAD_LOST,
        EXECUTION_SUSPENDED,
        OBSERVATION_GAP,
        TIMEOUT,
        TERMINAL
    }

    private enum SecureTravelRetarget {
        OBJECTIVE_CHANGED,
        MARKER_CHANGED,
        ASSIGNMENT_CHANGED,
        UNCLASSIFIED
    }

    private enum SecureTravelLossContext {
        LOCAL_CONTACT,
        TRACK_BELIEF_ONLY,
        NO_PUBLISHED_CONTACT,
        UNKNOWN_TRACK
    }

    private record SecureTravelCandidate(
            String key, int targetZone, int markerX, int markerY) { }

    private static final class SecureTravelEpisode {
        final String key;
        final int targetZone;
        final int markerX;
        final int markerY;
        final double initialDistance;
        double lastDistance;
        boolean localContact;
        boolean activePath;
        boolean quietTravel;
        boolean finished;
        SecureTravelLossContext lastLossContext =
                SecureTravelLossContext.UNKNOWN_TRACK;
        boolean lastTacticalObserved;
        boolean lastBreachAction;
        boolean lastMoving;
        boolean lastExposedFromPrimary;
        String lastDoctrine;
        String lastInitiative;
        boolean lastWithEngageableMembers;
        boolean lastWithEngageableFireTeams;
        boolean lastUnderFireRecently;
        boolean lastMajorityCoveredFromPrimary;
        boolean lastCoolingDown;

        private SecureTravelEpisode(SecureTravelCandidate candidate,
                                    JSONObject state) {
            this.key = candidate.key;
            this.targetZone = candidate.targetZone;
            this.markerX = candidate.markerX;
            this.markerY = candidate.markerY;
            this.initialDistance = distanceToMarker(state, markerX, markerY);
            this.lastDistance = initialDistance;
        }
    }

    private static final class SecureTravelAccumulator {
        int started;
        int targetEntry;
        int retarget;
        int release;
        int squadLoss;
        int suspension;
        int observationGap;
        int timeout;
        int terminal;
        int withContact;
        int withActivePath;
        int withQuietTravel;
        int lossLocationsObserved;
        int lossLocationsUnknown;
        int lossAtLocalContact;
        int lossWithTrackBeliefOnly;
        int lossWithoutPublishedContact;
        int lossWithUnknownTrack;
        int lossTacticalObserved;
        int lossTacticalUnknown;
        int lossWithBreachAction;
        int lossWithMovingMembers;
        int lossExposedFromPrimary;
        int lossDoctrineAdvance;
        int lossDoctrineHold;
        int lossDoctrineDisengage;
        int lossInitiativeNone;
        int lossInitiativeReceive;
        int lossInitiativeProsecute;
        int lossWithEngageableMembers;
        int lossWithEngageableFireTeams;
        int lossUnderFireRecently;
        int lossMajorityCoveredFromPrimary;
        int lossCoolingDown;
        final List<Integer> lossDistances = new ArrayList<>();
        final List<Integer> lossProgress = new ArrayList<>();

        void start() {
            started++;
        }

        void observeContext(SecureTravelEpisode episode, JSONObject state,
                            JSONObject action, JSONObject perspective,
                            int schemaVersion) {
            double distance = distanceToMarker(state, episode.markerX,
                    episode.markerY);
            if (Double.isFinite(distance)) episode.lastDistance = distance;
            episode.lastLossContext = lossContext(state, action, perspective);
            if (schemaVersion >= 8) observeLossTactics(episode, state);
            if (!episode.localContact
                    && state.optBoolean("localContact", false)) {
                episode.localContact = true;
                withContact++;
            }
            if (schemaVersion >= 6 && !episode.activePath
                    && state.optInt("activePathMembers", 0) > 0) {
                episode.activePath = true;
                withActivePath++;
            }
            if (!episode.quietTravel
                    && !state.optBoolean("localContact", false)) {
                episode.quietTravel = true;
                withQuietTravel++;
            }
        }

        void finish(SecureTravelEpisode episode, SecureTravelExit exit) {
            if (episode.finished) return;
            episode.finished = true;
            switch (exit) {
                case TARGET_ENTRY -> targetEntry++;
                case RETARGETED -> retarget++;
                case RELEASED -> release++;
                case SQUAD_LOST -> squadLoss++;
                case EXECUTION_SUSPENDED -> suspension++;
                case OBSERVATION_GAP -> observationGap++;
                case TIMEOUT -> timeout++;
                case TERMINAL -> terminal++;
            }
        }

        void finishRetarget(SecureTravelEpisode episode,
                            SecureTravelRetarget provenance) {
            finish(episode, SecureTravelExit.RETARGETED);
            switch (provenance) {
                case OBJECTIVE_CHANGED -> retargetObjectiveChanged++;
                case MARKER_CHANGED -> retargetMarkerChanged++;
                case ASSIGNMENT_CHANGED -> retargetAssignmentChanged++;
                case UNCLASSIFIED -> retargetUnclassified++;
            }
        }

        int retargetObjectiveChanged;
        int retargetMarkerChanged;
        int retargetAssignmentChanged;
        int retargetUnclassified;

        void finishLoss(SecureTravelEpisode episode) {
            finish(episode, SecureTravelExit.SQUAD_LOST);
            if (Double.isFinite(episode.initialDistance)
                    && Double.isFinite(episode.lastDistance)) {
                lossLocationsObserved++;
                lossDistances.add((int) Math.round(
                        episode.lastDistance * 10d));
                int progress = episode.initialDistance <= 0.0001d
                        ? 10_000
                        : (int) Math.round(10_000d
                        * (episode.initialDistance - episode.lastDistance)
                        / episode.initialDistance);
                lossProgress.add(Math.max(0, Math.min(10_000, progress)));
            } else {
                lossLocationsUnknown++;
            }
            switch (episode.lastLossContext) {
                case LOCAL_CONTACT -> lossAtLocalContact++;
                case TRACK_BELIEF_ONLY -> lossWithTrackBeliefOnly++;
                case NO_PUBLISHED_CONTACT -> lossWithoutPublishedContact++;
                case UNKNOWN_TRACK -> lossWithUnknownTrack++;
            }
            if (episode.lastTacticalObserved) {
                lossTacticalObserved++;
                if (episode.lastBreachAction) lossWithBreachAction++;
                if (episode.lastMoving) lossWithMovingMembers++;
                if (episode.lastExposedFromPrimary) lossExposedFromPrimary++;
                switch (episode.lastDoctrine) {
                    case "ADVANCE" -> lossDoctrineAdvance++;
                    case "HOLD" -> lossDoctrineHold++;
                    case "DISENGAGE" -> lossDoctrineDisengage++;
                    default -> { }
                }
                switch (episode.lastInitiative) {
                    case "NONE" -> lossInitiativeNone++;
                    case "RECEIVE" -> lossInitiativeReceive++;
                    case "PROSECUTE" -> lossInitiativeProsecute++;
                    default -> { }
                }
                if (episode.lastWithEngageableMembers) lossWithEngageableMembers++;
                if (episode.lastWithEngageableFireTeams) lossWithEngageableFireTeams++;
            } else {
                lossTacticalUnknown++;
            }
            if (episode.lastUnderFireRecently) lossUnderFireRecently++;
            if (episode.lastMajorityCoveredFromPrimary) {
                lossMajorityCoveredFromPrimary++;
            }
            if (episode.lastCoolingDown) lossCoolingDown++;
        }

        SecureTravelMetrics result() {
            return new SecureTravelMetrics(started, targetEntry, retarget,
                    retargetObjectiveChanged, retargetMarkerChanged,
                    retargetAssignmentChanged, retargetUnclassified,
                    release, squadLoss, suspension, observationGap, timeout,
                    terminal, withContact, withActivePath, withQuietTravel,
                    lossLocationsObserved, lossLocationsUnknown,
                    lossAtLocalContact, lossWithTrackBeliefOnly,
                    lossWithoutPublishedContact, lossWithUnknownTrack,
                    lossTacticalObserved, lossTacticalUnknown,
                    lossWithBreachAction, lossWithMovingMembers,
                    lossExposedFromPrimary, lossDoctrineAdvance,
                    lossDoctrineHold, lossDoctrineDisengage,
                    lossInitiativeNone, lossInitiativeReceive,
                    lossInitiativeProsecute, lossWithEngageableMembers,
                    lossWithEngageableFireTeams, lossUnderFireRecently,
                    lossMajorityCoveredFromPrimary, lossCoolingDown,
                    lossDistances, lossProgress);
        }
    }

    private static void observeLossTactics(SecureTravelEpisode episode,
                                           JSONObject state) {
        int alive = state.optInt("aliveMembers", 0);
        int moving = state.optInt("movingMembers", 0);
        int covered = state.optInt("coveredFromPrimaryMembers", -1);
        int coolingDown = state.optInt("coolingDownMembers", 0);
        String action = state.optString("currentAction", "");
        episode.lastTacticalObserved = true;
        episode.lastBreachAction = action.startsWith("BreachAndAdvance");
        episode.lastMoving = moving > 0;
        episode.lastExposedFromPrimary = moving > 0 && covered == 0;
        episode.lastDoctrine = state.optString("contactDoctrine", "");
        episode.lastInitiative = state.optString("contactInitiative", "");
        episode.lastWithEngageableMembers =
                state.optInt("primaryEngageableMembers", 0) > 0;
        episode.lastWithEngageableFireTeams =
                state.optInt("primaryEngageableFireTeams", 0) > 0;
        episode.lastUnderFireRecently =
                state.optBoolean("underFireRecently", false);
        episode.lastMajorityCoveredFromPrimary =
                alive > 0 && covered >= 0 && covered * 2 >= alive;
        episode.lastCoolingDown = coolingDown > 0;
    }

    private static double distanceToMarker(JSONObject state, int markerX,
                                           int markerY) {
        double x = state.optDouble("centroidX", Double.NaN);
        double y = state.optDouble("centroidY", Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y)) return Double.NaN;
        double dx = x - (markerX + 0.5);
        double dy = y - (markerY + 0.5);
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static SecureTravelLossContext lossContext(
            JSONObject state, JSONObject action, JSONObject perspective) {
        if (state.optBoolean("localContact", false)) {
            return SecureTravelLossContext.LOCAL_CONTACT;
        }
        if (action == null) return SecureTravelLossContext.UNKNOWN_TRACK;
        int effectiveTrack = action.optInt("effectiveTrack", -1);
        JSONObject conquest = perspective.optJSONObject("conquest");
        JSONArray tracks = conquest != null
                ? conquest.optJSONArray("tracks") : null;
        if (effectiveTrack < 0 || tracks == null) {
            return SecureTravelLossContext.UNKNOWN_TRACK;
        }
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.optJSONObject(i);
            if (track == null || track.optInt("index", -1) != effectiveTrack) {
                continue;
            }
            boolean belief = track.optInt("knownHostileContacts", 0) > 0
                    || track.optDouble("knownHostileFrontProgress", -1d)
                    >= 0d;
            return belief ? SecureTravelLossContext.TRACK_BELIEF_ONLY
                    : SecureTravelLossContext.NO_PUBLISHED_CONTACT;
        }
        return SecureTravelLossContext.UNKNOWN_TRACK;
    }

    private static final class MovementEpisode {
        final String key;
        final int startedTick;
        double initialDistance;
        final boolean secureCompound;
        final int markerX;
        final int markerY;
        final int targetZone;
        double minimumDistance;
        double previousDistance;
        int previousTick;
        boolean previousInTargetZone;
        boolean previousLocalContact;
        boolean closedRange;
        boolean observedInTargetZone;
        boolean adjacentCommitted;

        private MovementEpisode(String key, int startedTick,
                                double initialDistance,
                                boolean secureCompound, int markerX,
                                int markerY, int targetZone) {
            this.key = key;
            this.startedTick = startedTick;
            this.initialDistance = initialDistance;
            this.secureCompound = secureCompound;
            this.markerX = markerX;
            this.markerY = markerY;
            this.targetZone = targetZone;
            this.minimumDistance = initialDistance;
            this.previousDistance = initialDistance;
            this.previousTick = startedTick;
        }
    }

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
        int schemaVersion;
        String fixtureKind;
        String schedulerMode;
        int startTick;
        int endTick;
        int maxObservedTick;
        int observationWindows;
        boolean capturePausedAtEnd;
        Termination termination = Termination.INCOMPLETE;
        String winner;
        final Map<Faction, Integer> casualties = new EnumMap<>(Faction.class);
        final Map<Faction, List<PerspectiveSample>> samples =
                new EnumMap<>(Faction.class);
        final Map<Integer, Map<Integer, List<CompoundEvent>>> compounds =
                new TreeMap<>();
        final Map<Integer, Map<Integer, List<CompoundPresenceEvent>>>
                compoundPresence = new TreeMap<>();
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
