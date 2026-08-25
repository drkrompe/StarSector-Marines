package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-shot JSON dump of a squad's GOAP state — blackboard predicates +
 * current goal + current plan steps with assignments + commander assignment
 * + per-member status. Written to
 * {@code saves/common/starsector_marines/debug/squad_<id>.json} via the
 * SettingsAPI common-folder write path (the only file I/O available to mod
 * code; {@link java.nio.file} and {@link java.io.File} are blocked by the
 * Starsector script sandbox).
 *
 * <p>Triggered manually from the {@link
 * com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel} when
 * the user wants to capture "why is this squad doing X right now" for
 * offline inspection. Overwrites on each click; copy the file out of
 * common/ if you want a history.
 */
@DebugOnly
public final class SquadStateDumper {

    private static final Logger LOG = Logger.getLogger(SquadStateDumper.class);

    private SquadStateDumper() {}

    /**
     * Writes the dump and returns the common-folder-relative path the file
     * lands at on success, or {@code null} on any error (the call site
     * shows a brief status line either way; details land in the game log).
     *
     * <p>{@code selectedUnitEntityId} is optional: when non-zero, the dump tags
     * the matching member with {@code "selected": true} and surfaces a
     * top-level {@code selectedMemberId} (the world entity id) so offline
     * inspection of "this specific mech is misbehaving while its squadmates
     * look fine" can jump straight to the right row.
     */
    public static String dump(Squad squad, BattleSimulation sim, WorldState worldState,
                              long selectedUnitEntityId) {
        if (squad == null || sim == null) return null;
        try {
            JSONObject root = new JSONObject();
            root.put("simTickIndex", sim.simTickIndex);
            root.put("selectedMemberId", selectedUnitEntityId != 0L ? selectedUnitEntityId : JSONObject.NULL);
            root.put("squad", buildSquadJson(squad, sim));
            root.put("members", buildMembersJson(squad, sim, selectedUnitEntityId));
            root.put("currentGoal", buildGoalJson(squad));
            root.put("currentPlan", buildPlanJson(squad, sim));
            root.put("worldState", buildPredicateJson(worldState));
            JSONObject clearZone = buildClearZoneReachabilityJson(squad, sim);
            if (clearZone != null) root.put("clearZoneReachability", clearZone);

            String path = pathFor(squad);
            Global.getSettings().writeJSONToCommon(path, root, true);
            // SettingsAPI.writeJSONToCommon appends ".data" to the path on disk
            // (sandbox quirk) — log the actual filename so the reader can find it.
            LOG.info("SquadStateDumper: wrote SQ-" + squad.id + " state to saves/common/" + path + ".data");
            return path;
        } catch (Exception ex) {
            LOG.warn("SquadStateDumper: dump failed for SQ-" + squad.id, ex);
            return null;
        }
    }

    private static String pathFor(Squad squad) {
        return StarsectorMarinesModPlugin.MOD_ID + "/debug/squad_" + squad.id + ".json";
    }

    static JSONObject buildSquadJson(Squad squad, BattleSimulation sim) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", squad.id);
        o.put("faction", squad.faction != null ? squad.faction.name() : null);
        o.put("alertLevel", squad.alertLevel != null ? squad.alertLevel.name() : null);
        o.put("aliveMembers", squad.aliveMembers);
        o.put("originalSize", squad.originalSize);
        o.put("centroidX", squad.centroidX);
        o.put("centroidY", squad.centroidY);
        o.put("currentZone", ZoneQueries.squadCurrentZone(squad, sim));
        o.put("morale", squad.morale);
        o.put("moraleBroken", squad.moraleBroken);
        o.put("timeSinceContact", squad.timeSinceContact);
        o.put("timeSinceReplan", squad.timeSinceReplan);
        JSONArray contacts = new JSONArray();
        for (BelievedContact contact : squad.believedContacts()) {
            JSONObject believed = new JSONObject();
            believed.put("unitId", contact.unitId());
            long liveContact = sim.resolveUnit(contact.unitId());
            believed.put("unitName", liveContact != 0L
                    ? sim.identity().name(liveContact) : null);
            believed.put("lastSeenCellX", contact.lastSeenCellX());
            believed.put("lastSeenCellY", contact.lastSeenCellY());
            believed.put("lastSeenTick", contact.lastSeenTick());
            believed.put("ageTicks", Math.max(0,
                    sim.simTickIndex - contact.lastSeenTick()));
            believed.put("confidence", contact.confidence());
            believed.put("source", contact.source().name());
            believed.put("observedThisTick",
                    contact.observedOnTick(sim.simTickIndex));
            contacts.put(believed);
        }
        o.put("believedContacts", contacts);
        AudibleBearing bearing = squad.audibleBearing();
        if (bearing == null) {
            o.put("audibleBearing", (Object) null);
        } else {
            JSONObject audible = new JSONObject();
            audible.put("cellX", bearing.cellX());
            audible.put("cellY", bearing.cellY());
            audible.put("heardTick", bearing.heardTick());
            audible.put("ageTicks", Math.max(0, sim.simTickIndex - bearing.heardTick()));
            audible.put("confidence", bearing.confidence());
            audible.put("sourceUnitId", bearing.sourceUnitId());
            long liveSource = sim.resolveUnit(bearing.sourceUnitId());
            audible.put("sourceName", liveSource != 0L
                    ? sim.identity().name(liveSource) : null);
            audible.put("kind", bearing.kind().name());
            o.put("audibleBearing", audible);
        }
        long leaderUnit = sim.resolveUnit(squad.leaderId);
        o.put("leaderId", leaderUnit != 0L ? sim.identity().name(leaderUnit) : null);
        o.put("assignedNode", squad.assignedNode != null ? squad.assignedNode.kind.name() : null);
        o.put("assignedNodeMustHold", squad.assignedNode != null && squad.assignedNode.mustHold);
        o.put("assignedObjective", squad.assignedObjective != null
                ? buildAssignmentJson(squad.assignedObjective) : JSONObject.NULL);
        ObjectiveAssignment executable = squad.assignmentForExecution();
        JSONObject execution = new JSONObject();
        execution.put("status", executable != null ? "READY" :
                squad.assignmentExecutionSuspension() != null
                        ? "SUSPENDED" : "UNASSIGNED");
        execution.put("suspensionReason",
                squad.assignmentExecutionSuspension() != null
                        ? squad.assignmentExecutionSuspension() : JSONObject.NULL);
        execution.put("assignment", executable != null
                ? buildAssignmentJson(executable) : JSONObject.NULL);
        o.put("assignmentExecution", execution);
        CommanderSnapshot<?> commander = sim.getCommanderSnapshot(squad.faction);
        CommandDirective commandDirective = sim.getSquadCommandDirective(squad.id);
        o.put("commandDirective", buildCommandDirectiveJson(
                squad, commandDirective));
        o.put("commander", buildCommanderJson(
                squad, commander, commandDirective, sim));
        o.put("conquestCommand", buildConquestCommandJson(squad, commander, sim));
        // Garrison-specific flags — load-bearing for "why won't this squad fire" diagnostics.
        o.put("holdsFireUntilKillZone", squad.holdsFireUntilKillZone);
        o.put("killZoneLosTicks", squad.killZoneLosTicks);
        o.put("chokePointPortalId", squad.chokePointPortalId);
        o.put("fallbackTriggered", squad.fallbackTriggered);
        o.put("fallbackInProgress", squad.fallbackInProgress);
        o.put("engagementDisciplineHold", squad.engagementDisciplineHold);
        long rejectedTarget = sim.resolveUnit(squad.engagementDisciplineTargetId);
        o.put("engagementDisciplineTargetId", rejectedTarget != 0L
                ? sim.identity().name(rejectedTarget) : null);
        o.put("engagementDisciplineThreatDensity",
                squad.engagementDisciplineThreatDensity);
        SquadContactPicture picture = squad.contactPicture;
        JSONObject contactPicture = new JSONObject();
        contactPicture.put("tick", picture.tick());
        contactPicture.put("ageTicks", Math.max(0,
                sim.simTickIndex - picture.tick()));
        contactPicture.put("posture", picture.posture().name());
        contactPicture.put("axisX", picture.axisX());
        contactPicture.put("axisY", picture.axisY());
        contactPicture.put("contactCount", picture.contactCount());
        contactPicture.put("directContactCount", picture.directContactCount());
        contactPicture.put("hostileStrength", picture.hostileStrength());
        contactPicture.put("friendlyStrength", picture.friendlyStrength());
        contactPicture.put("hostileToFriendlyRatio",
                picture.hostileStrength() / Math.max(1, picture.friendlyStrength()));
        contactPicture.put("forceBalance", picture.forceBalance().name());
        contactPicture.put("dominantSector", picture.dominantSector().name());
        contactPicture.put("primaryMotion", picture.primaryMotion().name());
        contactPicture.put("primaryContactId", picture.primaryContactId());
        long livePrimary = sim.resolveUnit(picture.primaryContactId());
        contactPicture.put("primaryContactName", livePrimary != 0L
                ? sim.identity().name(livePrimary) : null);
        contactPicture.put("primaryCellX", picture.primaryCellX());
        contactPicture.put("primaryCellY", picture.primaryCellY());
        contactPicture.put("primaryConfidence", picture.primaryConfidence());
        contactPicture.put("doctrine", picture.doctrine().name());
        contactPicture.put("contactInitiative", picture.contactInitiative().name());
        contactPicture.put("primaryEngageableMembers",
                picture.primaryEngageableMembers());
        contactPicture.put("liveMembers", picture.liveMembers());
        contactPicture.put("primaryEngageableFireTeams",
                picture.primaryEngageableFireTeams());
        contactPicture.put("liveFireTeams", picture.liveFireTeams());
        boolean holdReactionFresh = TacticalScoring.contactHoldIsFresh(
                squad, picture, sim.simTickIndex);
        contactPicture.put("holdReactionFresh", holdReactionFresh);
        contactPicture.put("holdAfterLosWindowTicks",
                TacticalScoring.HOLD_AFTER_LOS_TICKS);
        contactPicture.put("advanceHardHoldActive",
                TacticalScoring.shouldHardHoldAdvance(squad, picture,
                        sim.simTickIndex));
        contactPicture.put("doctrineChangedThisTick",
                squad._contactDoctrineChangedThisTick);
        contactPicture.put("primaryEvidence",
                buildPrimaryEvidenceJson(squad, picture, sim.simTickIndex));
        o.put("contactPicture", contactPicture);
        o.put("advanceEngageWeight", squad.advanceEngageWeight);
        o.put("advanceEngageCommitted", squad.advanceEngageCommitted);
        o.put("advanceEngageLeash", squad.advanceEngageLeash);
        long advanceThreat = sim.resolveUnit(squad.advanceThreatId);
        o.put("advanceThreatId", advanceThreat != 0L ? sim.identity().name(advanceThreat) : null);
        o.put("advanceThreatFoes", squad.advanceThreatFoes);
        o.put("advanceThreatFriends", squad.advanceThreatFriends);
        o.put("advanceThreatAnchorX", squad.advanceThreatAnchorX);
        o.put("advanceThreatAnchorY", squad.advanceThreatAnchorY);
        o.put("advanceThreatRetreating", squad.advanceThreatRetreating);
        o.put("advanceThreatTick", squad.advanceThreatTick);
        o.put("boundingActive", squad.boundingActive);
        o.put("boundingPhase", squad.boundingPhase);
        o.put("boundingTargetZoneId", squad.boundingTargetZoneId);
        o.put("boundingDestX", squad.boundingDestX);
        o.put("boundingDestY", squad.boundingDestY);
        long boundingThreat = sim.resolveUnit(squad.boundingThreatId);
        o.put("boundingThreatId", boundingThreat != 0L ? sim.identity().name(boundingThreat) : null);
        o.put("boundingStrideX", squad.boundingStrideX);
        o.put("boundingStrideY", squad.boundingStrideY);
        long[] boundingMembers = squad.boundingMemberIds;
        int[] boundingXs = squad.boundingTargetXs;
        int[] boundingYs = squad.boundingTargetYs;
        int boundingCount = Math.min(boundingMembers.length,
                Math.min(boundingXs.length, boundingYs.length));
        JSONArray boundingTargets = new JSONArray();
        for (int i = 0; i < boundingCount; i++) {
            JSONObject target = new JSONObject();
            long bounder = sim.resolveUnit(boundingMembers[i]);
            target.put("memberId", bounder != 0L ? sim.identity().name(bounder) : null);
            target.put("x", boundingXs[i]);
            target.put("y", boundingYs[i]);
            boundingTargets.put(target);
        }
        o.put("boundingTargets", boundingTargets);
        long screeningMech = sim.resolveUnit(squad.screeningMechId);
        o.put("screeningMechId", screeningMech != 0L
                ? sim.identity().name(screeningMech) : null);
        o.put("mechScreenMode", squad.mechScreenMode.name());
        long screenThreat = sim.resolveUnit(squad.mechScreenThreatId);
        o.put("mechScreenThreatId", screenThreat != 0L
                ? sim.identity().name(screenThreat) : null);
        JSONArray screenTargets = new JSONArray();
        long[] screenMembers = squad.mechScreenMemberIds;
        int[] screenXs = squad.mechScreenTargetXs;
        int[] screenYs = squad.mechScreenTargetYs;
        int screenCount = Math.min(screenMembers.length,
                Math.min(screenXs.length, screenYs.length));
        for (int i = 0; i < screenCount; i++) {
            JSONObject target = new JSONObject();
            long screenMember = sim.resolveUnit(screenMembers[i]);
            target.put("memberId", screenMember != 0L
                    ? sim.identity().name(screenMember) : null);
            target.put("x", screenXs[i]);
            target.put("y", screenYs[i]);
            screenTargets.put(target);
        }
        o.put("mechScreenTargets", screenTargets);
        return o;
    }

    /**
     * Describes only the belief sample that fed the published contact picture.
     * This intentionally does not resolve or serialize the hostile's live cell.
     */
    private static JSONObject buildPrimaryEvidenceJson(Squad squad,
                                                       SquadContactPicture picture,
                                                       int simTick) throws Exception {
        if (picture.primaryContactId() == 0L) return null;
        BelievedContact evidence = squad.believedContact(picture.primaryContactId());
        if (evidence == null) return null;

        JSONObject o = new JSONObject();
        o.put("source", evidence.source().name());
        o.put("lastSeenTick", evidence.lastSeenTick());
        o.put("ageTicks", Math.max(0, simTick - evidence.lastSeenTick()));
        o.put("observedThisTick", evidence.observedOnTick(simTick));
        o.put("freshMotionSample", evidence.hasFreshMotionSample(simTick));
        if (evidence.hasFreshMotionSample(simTick)) {
            o.put("previousDirectCellX", evidence.previousDirectCellX());
            o.put("previousDirectCellY", evidence.previousDirectCellY());
            o.put("previousDirectTick", evidence.previousDirectTick());
        }
        return o;
    }

    private static JSONObject buildAssignmentJson(ObjectiveAssignment a) throws Exception {
        if (a == null) return null;
        JSONObject o = new JSONObject();
        o.put("kind", a.kind().name());
        o.put("targetZoneId", a.targetZoneId());
        o.put("targetNode", a.targetNode() != null ? a.targetNode().kind.name() : null);
        o.put("objectiveId", a.objectiveId());
        o.put("targetCellX", a.targetCellX());
        o.put("targetCellY", a.targetCellY());
        return o;
    }

    private static Object buildCommanderJson(Squad squad,
                                              CommanderSnapshot<?> snapshot,
                                              CommandDirective ledgerDirective,
                                              BattleSimulation sim)
            throws Exception {
        if (snapshot == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("tick", snapshot.tick());
        out.put("ageTicks", snapshot.tick() >= 0
                ? Math.max(0, sim.simTickIndex - snapshot.tick()) : -1);
        out.put("influenceTick", snapshot.influenceTick());
        out.put("influenceAgeTicks", snapshot.influenceTick() >= 0
                ? Math.max(0, sim.simTickIndex - snapshot.influenceTick()) : -1);
        out.put("perspective", snapshot.perspective().name());
        out.put("strategy", snapshot.strategy());
        out.put("phase", snapshot.phase());
        out.put("commandPoolSize", snapshot.commandPoolSize());
        out.put("reserveCount", snapshot.reserveCount());
        JSONArray objectives = new JSONArray();
        for (String objective : snapshot.objectiveSummaries()) {
            objectives.put(objective);
        }
        out.put("objectives", objectives);

        CommandDirective directive = snapshot.directiveFor(squad.id);
        if (directive == null) directive = ledgerDirective;
        if (directive == null) {
            out.put("squadDirective", JSONObject.NULL);
        } else {
            out.put("squadDirective", buildCommandDirectiveJson(
                    squad, directive));
        }
        return out;
    }

    /** Ledger provenance remains available even without a commander snapshot. */
    private static Object buildCommandDirectiveJson(Squad squad,
                                                     CommandDirective directive)
            throws Exception {
        if (directive == null) return JSONObject.NULL;
        JSONObject row = new JSONObject();
        row.put("issuer", directive.issuer());
        row.put("authority", directive.authority().name());
        row.put("status", directive.status().name());
        row.put("reason", directive.reason());
        row.put("dispositionReason", directive.dispositionReason());
        row.put("issuedTick", directive.issuedTick());
        row.put("leaseUntilTick", directive.leaseUntilTick());
        ObjectiveAssignment effective = directive.status()
                == CommandDirective.Status.REJECTED
                ? squad.assignedObjective : directive.assignment();
        row.put("assignment", effective != null
                ? buildAssignmentJson(effective)
                : JSONObject.NULL);
        row.put("proposedAssignment", directive.status()
                == CommandDirective.Status.REJECTED
                && directive.assignment() != null
                ? buildAssignmentJson(directive.assignment())
                : JSONObject.NULL);
        return row;
    }

    private static Object buildConquestCommandJson(Squad squad,
                                                   CommanderSnapshot<?> commander,
                                                   BattleSimulation sim)
            throws Exception {
        ConquestFrontSnapshot snapshot = commander != null
                && commander.detail() instanceof ConquestFrontSnapshot conquest
                ? conquest : null;
        if (snapshot == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("tick", snapshot.tick());
        out.put("ageTicks", snapshot.tick() >= 0
                ? Math.max(0, sim.simTickIndex - snapshot.tick()) : -1);
        out.put("influenceTick", snapshot.influenceTick());
        out.put("influenceAgeTicks", snapshot.influenceTick() >= 0
                ? Math.max(0, sim.simTickIndex - snapshot.influenceTick()) : -1);
        out.put("axis", snapshot.axis().name());
        out.put("perspective", snapshot.perspective().name());
        out.put("phase", snapshot.phase().name());
        out.put("remainingCompounds", snapshot.remainingCompounds());
        out.put("keepZoneId", snapshot.keepZoneId());
        out.put("keepState", snapshot.keepState() != null
                ? snapshot.keepState().name() : JSONObject.NULL);

        ConquestFrontSnapshot.SquadDirective directive =
                snapshot.directiveFor(squad.id);
        if (directive == null) {
            out.put("squadDirective", JSONObject.NULL);
        } else {
            JSONObject row = new JSONObject();
            row.put("preferredTrack", directive.preferredTrack());
            row.put("effectiveTrack", directive.effectiveTrack());
            row.put("reason", directive.reason().name());
            row.put("assignmentKind", directive.assignmentKind() != null
                    ? directive.assignmentKind().name() : JSONObject.NULL);
            row.put("targetZoneId", directive.targetZoneId());
            row.put("targetCellX", directive.targetCellX());
            row.put("targetCellY", directive.targetCellY());
            out.put("squadDirective", row);
        }

        JSONArray tracks = new JSONArray();
        for (ConquestFrontSnapshot.TrackState track : snapshot.tracks()) {
            JSONObject row = new JSONObject();
            row.put("index", track.index());
            row.put("lateralStart", track.lateralStart());
            row.put("lateralEnd", track.lateralEnd());
            row.put("preferredSquads", track.preferredSquads());
            row.put("effectiveSquads", track.effectiveSquads());
            row.put("effectiveLiveMembers", track.effectiveLiveMembers());
            row.put("friendlyBodyProgress", track.friendlyBodyProgress());
            row.put("friendlyLeadProgress", track.friendlyLeadProgress());
            row.put("knownHostileFrontProgress",
                    track.knownHostileFrontProgress());
            row.put("knownHostileContacts", track.knownHostileContacts());
            row.put("friendlyPressure", track.friendlyPressure());
            row.put("knownHostilePressure", track.knownHostilePressure());
            row.put("targetZoneId", track.targetZoneId());
            tracks.put(row);
        }
        out.put("tracks", tracks);
        return out;
    }

    static JSONArray buildMembersJson(Squad squad, BattleSimulation sim,
                                      long selectedUnitEntityId) throws Exception {
        JSONArray arr = new JSONArray();
        // Live members only (dense registry) — a dead member has left the
        // registry, so the dump no longer lists corpses. The "why is this squad
        // stuck" diagnostic cares about the survivors; squad-level aliveMembers
        // carries attrition. The per-member "alive" field is now always true.
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(u) || sim.squad().squadId(u) != squad.id) continue;
            JSONObject o = new JSONObject();
            o.put("id", sim.identity().name(u));
            if (selectedUnitEntityId != 0L && selectedUnitEntityId == u) {
                o.put("selected", true);
            }
            o.put("alive", sim.world().isAlive(u));
            o.put("role", sim.role().role(u).name());
            MechVariant mechVariant = sim.identity().mechVariant(u);
            MechLoadoutComponent mechLoadout = sim.world().mechLoadout(u);
            o.put("mechVariant", mechVariant != null ? mechVariant.id : null);
            o.put("mechRole", mechLoadout != null ? mechLoadout.role.name() : null);
            o.put("overwatchCellX", mechLoadout != null ? mechLoadout.overwatchCellX : null);
            o.put("overwatchCellY", mechLoadout != null ? mechLoadout.overwatchCellY : null);
            o.put("overwatchLongRangeBand",
                    mechLoadout != null ? mechLoadout.overwatchLongRangeBand : null);
            long screen = mechLoadout != null
                    ? sim.resolveUnit(mechLoadout.overwatchScreenId) : 0L;
            o.put("overwatchScreenId", screen != 0L
                    ? sim.identity().name(screen) : null);
            if (mechLoadout != null) {
                long aimTarget = sim.resolveUnit(mechLoadout.torsoAimTargetId);
                o.put("mechHipFacingDegrees", sim.world().mechHipFacingDegrees(u));
                o.put("mechTorsoFacingDegrees", mechLoadout.torsoFacingDegrees);
                o.put("mechTorsoAimTargetId", aimTarget != 0L
                        ? sim.identity().name(aimTarget) : null);
                o.put("mechTorsoOnTarget", mechLoadout.torsoOnTarget);
                o.put("missileReplenisherId", mechLoadout.missileReplenisher().id());
                o.put("missileReplenisherName",
                        mechLoadout.missileReplenisher().displayName());
                o.put("srmReplenishmentSeconds",
                        mechLoadout.missileReplenisher().srmReplenishmentSeconds());
                o.put("lrmReplenishmentSeconds",
                        mechLoadout.missileReplenisher().lrmReplenishmentSeconds());
                o.put("mechMounts", buildMechMountsJson(mechLoadout, sim));
            }
            o.put("cellX", sim.world().cellX(u));
            o.put("cellY", sim.world().cellY(u));
            // homeCell{X,Y} = -1 sentinel for units without a post (marines,
            // patrols). Emit anyway so the dump distinguishes "no home" from
            // "home but drifted off" — key signal for diagnosing why a
            // garrison unit's findFiringPositionWithin returned null.
            boolean hasHome = sim.home().hasHome(u);
            o.put("homeCellX", hasHome ? sim.home().homeCellX(u) : -1);
            o.put("homeCellY", hasHome ? sim.home().homeCellY(u) : -1);
            o.put("currentZone", sim.getZoneGraph().zoneIdAt(sim.world().cellX(u), sim.world().cellY(u)));
            o.put("hp", sim.world().hp(u));
            o.put("maxHp", sim.world().maxHp(u));
            o.put("settled", sim.movement().settled(u));
            long dumpTarget = sim.targetOf(u);
            o.put("targetId", dumpTarget != 0L ? sim.identity().name(dumpTarget) : null);
            long reflexTarget = sim.resolveUnit(sim.combat().reflexTargetId(u));
            o.put("reflexTargetId", reflexTarget != 0L ? reflexTarget : JSONObject.NULL);
            o.put("reflexTargetName", reflexTarget != 0L
                    ? sim.identity().name(reflexTarget) : JSONObject.NULL);
            o.put("reflexTimer", sim.combat().reflexTimer(u));
            o.put("lastFireGate", sim.combat().lastFireGate(u).name());
            o.put("lastFireGateTick", sim.combat().lastFireGateTick(u));
            o.put("lastFireGateAgeTicks", Math.max(0,
                    sim.getSimTickIndex() - sim.combat().lastFireGateTick(u)));
            // Pathfinder reachability of the unit's current target. False
            // here means the squad is fixated on someone the pathfinder
            // can't route to from this member — e.g. an enemy behind walls
            // in the same flood-filled zone (see [[zone_graph_ignores_edges]]).
            // Future make-passage actions (breach door, blow wall) should
            // key off this flag. JSONObject.NULL when the unit has no target.
            o.put("targetReachable", computeTargetReachable(u, sim));
            o.put("cooldownTimer", sim.world().cooldownTimer(u));
            int[] path = sim.world().path(u);
            int pathLen = Paths.cellCount(path);
            int pathIndex = sim.world().pathIdx(u);
            o.put("pathLen", pathLen);
            o.put("pathIndex", pathIndex);
            o.put("pathRemaining", Math.max(0, pathLen - pathIndex));
            o.put("pathDestX", pathLen > 0 ? Paths.destX(path) : null);
            o.put("pathDestY", pathLen > 0 ? Paths.destY(path) : null);
            arr.put(o);
        }
        return arr;
    }

    private static JSONArray buildMechMountsJson(MechLoadoutComponent loadout,
                                                  BattleSimulation sim) throws Exception {
        JSONArray mounts = new JSONArray();
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null) continue;
            JSONObject o = new JSONObject();
            o.put("slot", mount.slot.name());
            o.put("component", mount.component.name());
            o.put("weapon", mount.weapon().name());
            o.put("ammo", mount.ammo);
            o.put("ammoCapacity", mount.component.ammoCapacity);
            o.put("hasAmmo", mount.hasAmmo());
            o.put("cooldown", mount.cooldown);
            o.put("burstRemaining", mount.burstRemaining);
            o.put("burstTimer", mount.burstTimer);
            o.put("replenishmentProgressSeconds",
                    mount.replenishmentProgressSeconds);
            long burstTarget = sim.resolveUnit(mount.burstTargetId);
            o.put("burstTargetId", burstTarget != 0L
                    ? sim.identity().name(burstTarget) : null);
            mounts.put(o);
        }
        return mounts;
    }

    private static Object computeTargetReachable(long self, BattleSimulation sim) {
        long target = sim.targetOf(self);
        if (target == 0L) return JSONObject.NULL;
        int[] path = GridPathfinder.findPath(sim.getGrid(),
                sim.world().cellX(self), sim.world().cellY(self),
                sim.world().cellX(target), sim.world().cellY(target));
        return path.length > 0;
    }

    /**
     * When the squad's current plan step is a {@link ClearZone}, scans alive
     * enemies inside the target zone and reports whether each can be reached
     * by any alive squadmate via {@link GridPathfinder#findPath}. Returns
     * {@code null} when the squad has no plan, the plan is complete, or the
     * current step isn't ClearZone — the field is then omitted from the dump.
     *
     * <p>Signal hook for future "make-passage" actions: if an enemy is in
     * the target zone but no squadmate can pathfind to it, the squad is
     * geometrically stuck and needs a door-breach / wall-demo action to
     * progress rather than another retry of the same plan. This remains a
     * diagnostic signal; no make-passage feature is currently contracted.
     */
    private static JSONObject buildClearZoneReachabilityJson(Squad squad, BattleSimulation sim) throws Exception {
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return null;
        SquadPlan.Step step = plan.currentStep();
        if (!(step.action instanceof ClearZone)) return null;
        int targetZoneId = ((ClearZone) step.action).targetZoneId();

        Faction enemyFaction = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;

        List<Long> squadmates = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.squad().hasSquad(u) && sim.squad().squadId(u) == squad.id) squadmates.add(u);
        }

        JSONArray enemies = new JSONArray();
        boolean anyUnreachable = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long e = sim.liveUnitAt(i);
            if (sim.identity().faction(e) != enemyFaction) continue;
            if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(e), sim.world().cellY(e)) != targetZoneId) continue;
            boolean reachable = false;
            for (long m : squadmates) {
                int[] path = GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(m), sim.world().cellY(m),
                        sim.world().cellX(e), sim.world().cellY(e));
                if (path.length > 0) { reachable = true; break; }
            }
            if (!reachable) anyUnreachable = true;
            JSONObject eo = new JSONObject();
            eo.put("id", sim.identity().name(e));
            eo.put("cellX", sim.world().cellX(e));
            eo.put("cellY", sim.world().cellY(e));
            eo.put("reachableFromAnyMember", reachable);
            enemies.put(eo);
        }

        JSONObject o = new JSONObject();
        o.put("targetZoneId", targetZoneId);
        o.put("enemies", enemies);
        // Convenience top-level bit so future make-passage triggers check
        // one field instead of walking the enemies array.
        o.put("anyEnemyUnreachable", anyUnreachable);
        return o;
    }

    private static JSONObject buildGoalJson(Squad squad) throws Exception {
        JSONObject o = new JSONObject();
        if (squad.currentGoal == null) {
            o.put("name", JSONObject.NULL);
            o.put("priority", JSONObject.NULL);
            return o;
        }
        o.put("name", squad.currentGoal.name());
        o.put("priority", squad.currentGoal.priority().name());
        return o;
    }

    private static JSONObject buildPlanJson(Squad squad, BattleSimulation sim) throws Exception {
        JSONObject o = new JSONObject();
        SquadPlan plan = squad.currentPlan;
        if (plan == null) {
            o.put("present", false);
            return o;
        }
        o.put("present", true);
        o.put("stepCount", plan.stepCount());
        o.put("currentIndex", plan.currentIndex());
        o.put("complete", plan.isComplete());
        JSONArray steps = new JSONArray();
        List<SquadPlan.Step> stepList = plan.steps();
        for (int i = 0; i < stepList.size(); i++) {
            SquadPlan.Step step = stepList.get(i);
            JSONObject so = new JSONObject();
            so.put("index", i);
            so.put("isCurrent", i == plan.currentIndex() && !plan.isComplete());
            so.put("action", step.action.name());
            JSONObject slotJson = new JSONObject();
            for (Map.Entry<String, List<Long>> e : step.assignments.entrySet()) {
                JSONArray ids = new JSONArray();
                for (long u : e.getValue()) ids.put(sim.identity().name(u));
                slotJson.put(e.getKey(), ids);
            }
            so.put("assignments", slotJson);
            steps.put(so);
        }
        o.put("steps", steps);
        return o;
    }

    private static JSONObject buildPredicateJson(WorldState state) throws Exception {
        JSONObject o = new JSONObject();
        if (state == null) return o;
        // Specified-vs-unspecified matters for diagnostics — a predicate that
        // isn't even in the world-state mask reads differently from one that's
        // explicitly false. Emit both columns so offline tools can tell.
        for (Predicate p : Predicate.values()) {
            JSONObject pj = new JSONObject();
            pj.put("specified", state.isSpecified(p));
            pj.put("value", state.get(p));
            o.put(p.name(), pj);
        }
        return o;
    }
}
