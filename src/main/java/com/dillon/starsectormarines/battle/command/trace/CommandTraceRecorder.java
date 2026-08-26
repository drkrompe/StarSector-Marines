package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Battle-long diagnostic trace with strictly separated perspective and neutral
 * referee streams. Perspective rows serialize only published post-commit
 * commander snapshots; compound and terminal facts are labelled referee rows
 * and are never exposed back to command planning.
 */
public final class CommandTraceRecorder {

    private final Map<Faction, Integer> lastPerspectiveTick =
            new EnumMap<>(Faction.class);
    private final Map<String, String> lastCompoundState = new HashMap<>();
    private final Map<String, String> lastCompoundPresence = new HashMap<>();
    private final Map<String, String> lastChargeSiteState = new HashMap<>();
    private final StringBuilder canonical = new StringBuilder(16_384);
    private List<CompoundService.Record> compounds = List.of();
    private int compoundCount = -1;
    private int[] marineZoneCounts = new int[0];
    private int[] defenderZoneCounts = new int[0];
    private boolean terminalRecorded;
    private boolean sealed;
    private int eventCount;

    public CommandTraceRecorder(String fixtureKind, String schedulerMode,
                                int startTick) {
        StringBuilder header = begin("run", startTick);
        numberField(header, "schemaVersion", 4);
        nullableField(header, "fixtureKind", fixtureKind);
        field(header, "schedulerMode", schedulerMode);
        appendLine(end(header));
    }

    /** Poll after a completed simulation tick; unchanged snapshots are ignored. */
    public void sample(BattleSimulation sim) {
        if (sealed) return;
        for (Faction faction : Faction.values()) {
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(faction);
            recordPerspective(snapshot, sim.getSimTickIndex());
        }
        sampleCompounds(sim);
        sampleChargeSites(sim);
        if (sim.isComplete() && !terminalRecorded) {
            terminalRecorded = true;
            StringBuilder out = begin("referee", sim.getSimTickIndex());
            field(out, "event", "terminal");
            nullableField(out, "winner",
                    sim.getWinner() != null ? sim.getWinner().name() : null);
            appendLine(end(out));
            sealed = true;
        }
    }

    /** Records one newly published snapshot, deduplicated by side and tick. */
    void recordPerspective(CommanderSnapshot<?> snapshot) {
        recordPerspective(snapshot, snapshot != null ? snapshot.tick() : -1);
    }

    private void recordPerspective(CommanderSnapshot<?> snapshot,
                                   int observedTick) {
        if (sealed || snapshot == null) return;
        Integer priorTick = lastPerspectiveTick.get(snapshot.perspective());
        if (priorTick != null && priorTick == snapshot.tick()) return;
        lastPerspectiveTick.put(snapshot.perspective(), snapshot.tick());
        appendLine(encodePerspective(snapshot, observedTick));
    }

    /** Labels a gap created by disabling live capture without discarding it. */
    public void recordCapturePaused(int tick) {
        if (sealed) return;
        StringBuilder out = begin("control", tick);
        field(out, "event", "capture-paused");
        appendLine(end(out));
    }

    /** Labels the start of a new contiguous observation window. */
    public void recordCaptureResumed(int tick) {
        if (sealed) return;
        lastPerspectiveTick.clear();
        lastCompoundState.clear();
        lastCompoundPresence.clear();
        lastChargeSiteState.clear();
        StringBuilder out = begin("control", tick);
        field(out, "event", "capture-resumed");
        appendLine(end(out));
    }

    /** Records one neutral combat loss while trace capture is active. */
    public void recordCasualty(int tick, long unitId, Faction faction,
                               UnitType type, int cellX, int cellY) {
        if (sealed) return;
        StringBuilder out = begin("referee", tick);
        field(out, "event", "casualty");
        longField(out, "unitId", unitId);
        field(out, "faction", faction.name());
        field(out, "unitType", type.name());
        booleanField(out, "combatant", type.combatant);
        numberField(out, "cellX", cellX);
        numberField(out, "cellY", cellY);
        appendLine(end(out));
    }

    /** Records a bounded-run stop distinctly from a battle outcome. */
    public void recordTimeout(int tick, int maxTicks) {
        if (terminalRecorded || sealed) return;
        terminalRecorded = true;
        StringBuilder out = begin("referee", tick);
        field(out, "event", "timeout");
        numberField(out, "maxTicks", maxTicks);
        appendLine(end(out));
        sealed = true;
    }

    /** Canonical JSONL: fixed key order, fixed event order, and LF endings. */
    public String canonicalJsonLines() {
        return canonical.toString();
    }

    public int eventCount() {
        return eventCount;
    }

    /** A terminal or timeout row permanently closes this trace. */
    public boolean isSealed() {
        return sealed;
    }

    private void sampleCompounds(BattleSimulation sim) {
        int currentCount = sim.getCompoundService().getRecords().size();
        if (currentCount != compoundCount) {
            compoundCount = currentCount;
            List<CompoundService.Record> sorted = new ArrayList<>(
                    sim.getCompoundService().getRecords());
            sorted.sort(Comparator
                    .comparingInt((CompoundService.Record record) -> record.node.anchorY)
                    .thenComparingInt(record -> record.node.anchorX)
                    .thenComparing(record -> record.node.kind.name()));
            compounds = List.copyOf(sorted);
        }
        int zoneCount = sim.getZoneGraph().getZones().size();
        if (marineZoneCounts.length < zoneCount) {
            marineZoneCounts = new int[zoneCount];
            defenderZoneCounts = new int[zoneCount];
        } else {
            Arrays.fill(marineZoneCounts, 0, zoneCount, 0);
            Arrays.fill(defenderZoneCounts, 0, zoneCount, 0);
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            int zone = sim.getZoneGraph().zoneIdAt(
                    sim.world().cellX(unit), sim.world().cellY(unit));
            if (zone < 0 || zone >= zoneCount) continue;
            Faction faction = sim.identity().faction(unit);
            if (faction == Faction.MARINE) marineZoneCounts[zone]++;
            else if (faction == Faction.DEFENDER) defenderZoneCounts[zone]++;
        }
        for (CompoundService.Record record : compounds) {
            TacticalNode node = record.node;
            String subject = node.kind.name() + "@" + node.anchorX + "," + node.anchorY;
            String state = record.state.name();
            if (!state.equals(lastCompoundState.put(subject, state))) {
                StringBuilder out = begin("referee", sim.getSimTickIndex());
                field(out, "event", "compound-state");
                field(out, "subject", subject);
                field(out, "compoundKind", node.kind.name());
                numberField(out, "anchorX", node.anchorX);
                numberField(out, "anchorY", node.anchorY);
                field(out, "state", state);
                appendLine(end(out));
            }

            int zoneId = sim.getZoneGraph().zoneIdAt(node.anchorX, node.anchorY);
            int marines = zoneId >= 0 && zoneId < zoneCount
                    ? marineZoneCounts[zoneId] : -1;
            int defenders = zoneId >= 0 && zoneId < zoneCount
                    ? defenderZoneCounts[zoneId] : -1;
            String occupancy = occupancy(marines, defenders);
            int progress = Math.max(0, Math.min(10_000,
                    Math.round(record.captureProgress * 10_000f)));
            String signature = zoneId + "|" + occupancy + "|" + marines
                    + "|" + defenders + "|" + progress;
            if (signature.equals(lastCompoundPresence.put(subject, signature))) {
                continue;
            }
            StringBuilder presence = begin("referee", sim.getSimTickIndex());
            field(presence, "event", "compound-presence");
            field(presence, "subject", subject);
            field(presence, "compoundKind", node.kind.name());
            numberField(presence, "anchorX", node.anchorX);
            numberField(presence, "anchorY", node.anchorY);
            numberField(presence, "anchorZoneId", zoneId);
            field(presence, "occupancy", occupancy);
            numberField(presence, "marineUnits", marines);
            numberField(presence, "defenderUnits", defenders);
            numberField(presence, "captureProgressBasisPoints", progress);
            appendLine(end(presence));
        }
    }

    private static String occupancy(int marines, int defenders) {
        if (marines < 0 || defenders < 0) return "UNRESOLVED";
        if (marines > 0 && defenders > 0) return "MIXED";
        if (marines > 0) return "MARINE_ONLY";
        if (defenders > 0) return "DEFENDER_ONLY";
        return "EMPTY";
    }

    /** Authoritative objective progress is emitted only on the referee stream. */
    private void sampleChargeSites(BattleSimulation sim) {
        List<ChargeSiteObjective> sites = new ArrayList<>();
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof ChargeSiteObjective site) sites.add(site);
        }
        sites.sort(Comparator.comparing(ChargeSiteObjective::siteId));
        for (ChargeSiteObjective site : sites) {
            int progress = Math.max(0, Math.min(10_000, Math.round(
                    site.progress() / Math.max(site.plantDuration(), 0.0001f)
                            * 10_000f)));
            String signature = progress + "|" + site.planterOnSite()
                    + "|" + site.isComplete();
            if (signature.equals(lastChargeSiteState.put(site.siteId(), signature))) {
                continue;
            }
            StringBuilder out = begin("referee", sim.getSimTickIndex());
            field(out, "event", "charge-site-state");
            field(out, "siteId", site.siteId());
            numberField(out, "cellX", site.cellX());
            numberField(out, "cellY", site.cellY());
            numberField(out, "progressBasisPoints", progress);
            booleanField(out, "planterOnSite", site.planterOnSite());
            booleanField(out, "complete", site.isComplete());
            appendLine(end(out));
        }
    }

    private void appendLine(String line) {
        canonical.append(line).append('\n');
        eventCount++;
    }

    private static String encodePerspective(CommanderSnapshot<?> snapshot,
                                            int observedTick) {
        StringBuilder out = begin("perspective", snapshot.tick());
        numberField(out, "observedTick", observedTick);
        field(out, "perspective", snapshot.perspective().name());
        field(out, "strategy", snapshot.strategy());
        field(out, "phase", snapshot.phase());
        numberField(out, "influenceTick", snapshot.influenceTick());
        numberField(out, "commandPoolSize", snapshot.commandPoolSize());
        numberField(out, "reserveCount", snapshot.reserveCount());
        out.append(",\"objectives\":[");
        for (int i = 0; i < snapshot.objectiveSummaries().size(); i++) {
            if (i > 0) out.append(',');
            string(out, snapshot.objectiveSummaries().get(i));
        }
        out.append(']');

        List<CommandDirective> directives = new ArrayList<>(snapshot.directives());
        directives.sort(Comparator.comparingInt(CommandDirective::squadId));
        out.append(",\"directives\":[");
        for (int i = 0; i < directives.size(); i++) {
            if (i > 0) out.append(',');
            directive(out, directives.get(i));
        }
        out.append(']');
        if (snapshot.detail() instanceof ConquestFrontSnapshot conquest) {
            conquest(out, conquest);
        } else if (snapshot.detail() instanceof AssaultSearchSnapshot assault) {
            assault(out, assault);
        } else if (snapshot.detail() instanceof SabotageSiteSnapshot sabotage) {
            sabotage(out, sabotage);
        } else if (snapshot.detail() instanceof SabotageDefenseSnapshot defense) {
            sabotageDefense(out, defense);
        }
        return end(out);
    }

    private static void directive(StringBuilder out, CommandDirective directive) {
        out.append('{');
        rawNumberField(out, "squadId", directive.squadId());
        field(out, "issuer", directive.issuer());
        field(out, "authority", directive.authority().name());
        field(out, "status", directive.status().name());
        field(out, "reason", directive.reason());
        field(out, "disposition", directive.dispositionReason());
        numberField(out, "issuedTick", directive.issuedTick());
        numberField(out, "stableUntilTick", directive.stableUntilTick());
        numberField(out, "leaseUntilTick", directive.leaseUntilTick());
        out.append(",\"assignment\":");
        assignment(out, directive.assignment());
        out.append('}');
    }

    private static void assignment(StringBuilder out, ObjectiveAssignment assignment) {
        if (assignment == null) {
            out.append("null");
            return;
        }
        out.append('{');
        rawField(out, "kind", assignment.kind().name());
        numberField(out, "targetZoneId", assignment.targetZoneId());
        nullableField(out, "targetNode", assignment.targetNode() != null
                ? assignment.targetNode().kind.name() : null);
        numberField(out, "objectiveId", assignment.objectiveId());
        numberField(out, "targetCellX", assignment.targetCellX());
        numberField(out, "targetCellY", assignment.targetCellY());
        out.append('}');
    }

    private static void conquest(StringBuilder out, ConquestFrontSnapshot snapshot) {
        out.append(",\"conquest\":{");
        rawField(out, "axis", snapshot.axis().name());
        field(out, "phase", snapshot.phase().name());
        numberField(out, "remainingCompounds", snapshot.remainingCompounds());
        numberField(out, "keepZoneId", snapshot.keepZoneId());
        nullableField(out, "keepState", snapshot.keepState() != null
                ? snapshot.keepState().name() : null);

        List<ConquestFrontSnapshot.TrackState> tracks = new ArrayList<>(snapshot.tracks());
        tracks.sort(Comparator.comparingInt(ConquestFrontSnapshot.TrackState::index));
        out.append(",\"tracks\":[");
        for (int i = 0; i < tracks.size(); i++) {
            if (i > 0) out.append(',');
            track(out, tracks.get(i));
        }
        out.append(']');

        List<ConquestFrontSnapshot.SquadState> squads =
                new ArrayList<>(snapshot.squads());
        squads.sort(Comparator.comparingInt(
                ConquestFrontSnapshot.SquadState::squadId));
        out.append(",\"squads\":[");
        for (int i = 0; i < squads.size(); i++) {
            if (i > 0) out.append(',');
            squad(out, squads.get(i));
        }
        out.append(']');

        List<ConquestFrontSnapshot.SquadDirective> actions =
                new ArrayList<>(snapshot.directives());
        actions.sort(Comparator.comparingInt(
                ConquestFrontSnapshot.SquadDirective::squadId));
        out.append(",\"actions\":[");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) out.append(',');
            action(out, actions.get(i));
        }
        out.append("]}");
    }

    private static void squad(StringBuilder out,
                              ConquestFrontSnapshot.SquadState squad) {
        out.append('{');
        rawNumberField(out, "squadId", squad.squadId());
        numberField(out, "aliveMembers", squad.aliveMembers());
        floatField(out, "centroidX", squad.centroidX());
        floatField(out, "centroidY", squad.centroidY());
        numberField(out, "currentZoneId", squad.currentZoneId());
        nullableField(out, "executionSuspension", squad.executionSuspension());
        booleanField(out, "localContact", squad.localContact());
        out.append('}');
    }

    private static void track(StringBuilder out,
                              ConquestFrontSnapshot.TrackState track) {
        out.append('{');
        rawNumberField(out, "index", track.index());
        numberField(out, "lateralStart", track.lateralStart());
        numberField(out, "lateralEnd", track.lateralEnd());
        numberField(out, "preferredSquads", track.preferredSquads());
        numberField(out, "effectiveSquads", track.effectiveSquads());
        numberField(out, "effectiveLiveMembers", track.effectiveLiveMembers());
        floatField(out, "friendlyBodyProgress", track.friendlyBodyProgress());
        floatField(out, "friendlyLeadProgress", track.friendlyLeadProgress());
        floatField(out, "knownHostileFrontProgress",
                track.knownHostileFrontProgress());
        numberField(out, "knownHostileContacts", track.knownHostileContacts());
        floatField(out, "friendlyPressure", track.friendlyPressure());
        floatField(out, "knownHostilePressure", track.knownHostilePressure());
        numberField(out, "targetZoneId", track.targetZoneId());
        out.append('}');
    }

    private static void action(StringBuilder out,
                               ConquestFrontSnapshot.SquadDirective action) {
        out.append('{');
        rawNumberField(out, "squadId", action.squadId());
        numberField(out, "preferredTrack", action.preferredTrack());
        numberField(out, "effectiveTrack", action.effectiveTrack());
        field(out, "reason", action.reason().name());
        nullableField(out, "assignmentKind", action.assignmentKind() != null
                ? action.assignmentKind().name() : null);
        numberField(out, "targetZoneId", action.targetZoneId());
        numberField(out, "targetCellX", action.targetCellX());
        numberField(out, "targetCellY", action.targetCellY());
        numberField(out, "markerCellX", action.markerCellX());
        numberField(out, "markerCellY", action.markerCellY());
        booleanField(out, "distantCaptureDeferred",
                action.distantCaptureDeferred());
        out.append('}');
    }

    private static void sabotage(StringBuilder out,
                                 SabotageSiteSnapshot snapshot) {
        out.append(",\"sabotage\":{");
        rawField(out, "phase", snapshot.phase().name());
        List<SabotageSiteSnapshot.SiteState> sites =
                new ArrayList<>(snapshot.sites());
        sites.sort(Comparator.comparingInt(SabotageSiteSnapshot.SiteState::index));
        out.append(",\"sites\":[");
        for (int i = 0; i < sites.size(); i++) {
            if (i > 0) out.append(',');
            sabotageSite(out, sites.get(i));
        }
        out.append(']');
        List<SabotageSiteSnapshot.SquadDirective> actions =
                new ArrayList<>(snapshot.directives());
        actions.sort(Comparator.comparingInt(
                SabotageSiteSnapshot.SquadDirective::squadId));
        out.append(",\"actions\":[");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) out.append(',');
            sabotageAction(out, actions.get(i));
        }
        out.append("]}");
    }

    private static void assault(StringBuilder out,
                                AssaultSearchSnapshot snapshot) {
        out.append(",\"assault\":{");
        rawField(out, "phase", snapshot.phase().name());
        numberField(out, "searchPass", snapshot.searchPass());
        List<AssaultSearchSnapshot.SectorState> sectors =
                new ArrayList<>(snapshot.sectors());
        sectors.sort(Comparator.comparingInt(
                AssaultSearchSnapshot.SectorState::index));
        out.append(",\"sectors\":[");
        for (int i = 0; i < sectors.size(); i++) {
            if (i > 0) out.append(',');
            AssaultSearchSnapshot.SectorState sector = sectors.get(i);
            out.append('{');
            rawNumberField(out, "index", sector.index());
            numberField(out, "minCellX", sector.minCellX());
            numberField(out, "minCellY", sector.minCellY());
            numberField(out, "width", sector.width());
            numberField(out, "height", sector.height());
            field(out, "status", sector.status().name());
            numberField(out, "visitedLegs", sector.visitedLegs());
            numberField(out, "totalLegs", sector.totalLegs());
            numberField(out, "believedContacts", sector.believedContacts());
            numberField(out, "freshestContactTick", sector.freshestContactTick());
            numberField(out, "assignedSquads", sector.assignedSquads());
            numberField(out, "leadTargetCellX", sector.leadTargetCellX());
            numberField(out, "leadTargetCellY", sector.leadTargetCellY());
            out.append('}');
        }
        out.append(']');
        List<AssaultSearchSnapshot.SquadDirective> actions =
                new ArrayList<>(snapshot.directives());
        actions.sort(Comparator.comparingInt(
                AssaultSearchSnapshot.SquadDirective::squadId));
        out.append(",\"actions\":[");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) out.append(',');
            AssaultSearchSnapshot.SquadDirective action = actions.get(i);
            out.append('{');
            rawNumberField(out, "squadId", action.squadId());
            numberField(out, "sectorIndex", action.sectorIndex());
            field(out, "reason", action.reason().name());
            nullableField(out, "assignmentKind", action.assignmentKind() != null
                    ? action.assignmentKind().name() : null);
            numberField(out, "targetCellX", action.targetCellX());
            numberField(out, "targetCellY", action.targetCellY());
            out.append('}');
        }
        out.append("]}");
    }

    private static void sabotageSite(StringBuilder out,
                                     SabotageSiteSnapshot.SiteState site) {
        out.append('{');
        rawNumberField(out, "index", site.index());
        field(out, "id", site.id());
        field(out, "name", site.name());
        numberField(out, "cellX", site.cellX());
        numberField(out, "cellY", site.cellY());
        numberField(out, "zoneId", site.zoneId());
        floatField(out, "progress", site.progress());
        floatField(out, "plantDuration", site.plantDuration());
        booleanField(out, "planterOnSite", site.planterOnSite());
        booleanField(out, "complete", site.complete());
        numberField(out, "activeKitDrops", site.activeKitDrops());
        numberField(out, "unclaimedKitDrops", site.unclaimedKitDrops());
        field(out, "groupReason", site.groupReason().name());
        numberField(out, "planterSquads", site.planterSquads());
        numberField(out, "retrieverSquads", site.retrieverSquads());
        numberField(out, "securitySquads", site.securitySquads());
        numberField(out, "liveMembers", site.liveMembers());
        floatField(out, "friendlyPressure", site.friendlyPressure());
        floatField(out, "knownHostilePressure", site.knownHostilePressure());
        out.append('}');
    }

    private static void sabotageAction(StringBuilder out,
                                       SabotageSiteSnapshot.SquadDirective action) {
        out.append('{');
        rawNumberField(out, "squadId", action.squadId());
        numberField(out, "siteIndex", action.siteIndex());
        field(out, "groupRole", action.groupRole().name());
        field(out, "reason", action.reason().name());
        nullableField(out, "assignmentKind", action.assignmentKind() != null
                ? action.assignmentKind().name() : null);
        numberField(out, "targetZoneId", action.targetZoneId());
        numberField(out, "markerCellX", action.markerCellX());
        numberField(out, "markerCellY", action.markerCellY());
        out.append('}');
    }

    private static void sabotageDefense(StringBuilder out,
                                         SabotageDefenseSnapshot snapshot) {
        out.append(",\"sabotageDefense\":{");
        rawField(out, "phase", snapshot.phase().name());
        numberField(out, "mobilePool", snapshot.mobilePool());
        numberField(out, "reserveCount", snapshot.reserveCount());
        List<SabotageDefenseSnapshot.SiteState> sites =
                new ArrayList<>(snapshot.sites());
        sites.sort(Comparator.comparingInt(
                SabotageDefenseSnapshot.SiteState::index));
        out.append(",\"sites\":[");
        for (int i = 0; i < sites.size(); i++) {
            if (i > 0) out.append(',');
            SabotageDefenseSnapshot.SiteState site = sites.get(i);
            out.append('{');
            rawNumberField(out, "index", site.index());
            field(out, "id", site.id());
            field(out, "name", site.name());
            numberField(out, "cellX", site.cellX());
            numberField(out, "cellY", site.cellY());
            numberField(out, "zoneId", site.zoneId());
            booleanField(out, "complete", site.complete());
            booleanField(out, "alarmActive", site.alarmActive());
            numberField(out, "alarmRaisedTick", site.alarmRaisedTick());
            numberField(out, "alarmExpiresTick", site.alarmExpiresTick());
            numberField(out, "routineSquads", site.routineSquads());
            numberField(out, "respondingSquads", site.respondingSquads());
            numberField(out, "liveMembers", site.liveMembers());
            floatField(out, "friendlyPressure", site.friendlyPressure());
            floatField(out, "knownHostilePressure", site.knownHostilePressure());
            out.append('}');
        }
        out.append(']');
        List<SabotageDefenseSnapshot.SquadDirective> actions =
                new ArrayList<>(snapshot.directives());
        actions.sort(Comparator.comparingInt(
                SabotageDefenseSnapshot.SquadDirective::squadId));
        out.append(",\"actions\":[");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) out.append(',');
            SabotageDefenseSnapshot.SquadDirective action = actions.get(i);
            out.append('{');
            rawNumberField(out, "squadId", action.squadId());
            numberField(out, "siteIndex", action.siteIndex());
            field(out, "role", action.role().name());
            field(out, "reason", action.reason().name());
            nullableField(out, "assignmentKind", action.assignmentKind() != null
                    ? action.assignmentKind().name() : null);
            numberField(out, "markerCellX", action.markerCellX());
            numberField(out, "markerCellY", action.markerCellY());
            out.append('}');
        }
        out.append("]}");
    }

    private static StringBuilder begin(String stream, int tick) {
        StringBuilder out = new StringBuilder(512);
        out.append('{');
        rawField(out, "stream", stream);
        numberField(out, "tick", tick);
        return out;
    }

    private static String end(StringBuilder out) {
        return out.append('}').toString();
    }

    private static void rawField(StringBuilder out, String name, String value) {
        name(out, name);
        string(out, value);
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append(',');
        rawField(out, name, value);
    }

    private static void nullableField(StringBuilder out, String name,
                                      String value) {
        out.append(',');
        name(out, name);
        if (value == null) out.append("null");
        else string(out, value);
    }

    private static void rawNumberField(StringBuilder out, String name,
                                       int value) {
        name(out, name);
        out.append(value);
    }

    private static void numberField(StringBuilder out, String name, int value) {
        out.append(',');
        rawNumberField(out, name, value);
    }

    private static void longField(StringBuilder out, String name, long value) {
        out.append(',');
        name(out, name);
        out.append(value);
    }

    private static void booleanField(StringBuilder out, String name,
                                     boolean value) {
        out.append(',');
        name(out, name);
        out.append(value);
    }

    private static void floatField(StringBuilder out, String name, float value) {
        out.append(',');
        name(out, name);
        out.append(Float.toString(value));
    }

    private static void name(StringBuilder out, String name) {
        string(out, name);
        out.append(':');
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
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00");
                        out.append(Character.forDigit((c >>> 4) & 0xf, 16));
                        out.append(Character.forDigit(c & 0xf, 16));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
