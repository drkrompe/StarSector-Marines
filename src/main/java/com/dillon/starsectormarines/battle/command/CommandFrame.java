package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, perspective-specific input to one autonomous command plan. */
public class CommandFrame {

    private final int tick;
    private final Faction perspective;
    private final List<CommandSquadState> squads;
    private final CommanderInfluenceSnapshot influence;
    private final CommandTopology topology;
    private final CommandAssignmentSnapshot assignments;

    protected CommandFrame(int tick, Faction perspective,
                           List<CommandSquadState> squads,
                           CommanderInfluenceSnapshot influence,
                           CommandTopology topology,
                           CommandAssignmentSnapshot assignments) {
        this.tick = tick;
        this.perspective = Objects.requireNonNull(perspective, "perspective");
        this.squads = List.copyOf(squads);
        this.influence = influence;
        this.topology = Objects.requireNonNull(topology, "topology");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
    }

    public static CommandFrame freeze(BattleView sim, Faction perspective,
                                      CommandTopology topology,
                                      CommandAssignmentSnapshot assignments) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long stageStarted = profile != null ? System.nanoTime() : 0L;
        CommandAssignmentSnapshot ownAssignments =
                assignments.forPerspective(perspective);
        record(profile, TickInnerProfile.Bucket.COMMANDER_FRAME_ASSIGNMENTS, stageStarted);
        stageStarted = profile != null ? System.nanoTime() : 0L;
        boolean detail = profile != null && Boolean.getBoolean("battle.profile.commandFrameDetail");
        long rosterNanos = 0L, memberNanos = 0L, contactNanos = 0L, publicationNanos = 0L;
        int rowCount = 0, memberInputs = 0, beliefInputs = 0;
        List<CommandSquadState> rows = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != perspective) continue;
            long rowStageStarted = detail ? System.nanoTime() : 0L;
            int memberCount = sim.squadMemberCount(squad.id);
            long anchor = sim.resolveUnit(squad.leaderId);
            if (anchor == 0L && memberCount > 0) anchor = sim.squadMemberAt(squad.id, 0);
            int anchorX = anchor != 0L ? sim.world().cellX(anchor) : -1;
            int anchorY = anchor != 0L ? sim.world().cellY(anchor) : -1;
            UnitRole role = memberCount > 0
                    ? sim.role().role(sim.squadMemberAt(squad.id, 0)) : null;
            if (detail) {
                long now = System.nanoTime();
                rosterNanos += now - rowStageStarted;
                rowStageStarted = now;
                rowCount++;
                memberInputs += memberCount;
            }
            int activePathMembers = 0;
            int movingMembers = 0;
            SquadContactPicture contact = squad.contactPicture;
            boolean primaryKnown = contact.primaryCellX() >= 0
                    && contact.primaryCellY() >= 0;
            int coveredFromPrimaryMembers = primaryKnown ? 0 : -1;
            int coolingDownMembers = 0;
            int[] memberZoneIds = new int[memberCount];
            int[] memberCellXs = new int[memberCount];
            int[] memberCellYs = new int[memberCount];
            for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
                long member = sim.squadMemberAt(squad.id, memberIndex);
                int memberX = sim.world().cellX(member);
                int memberY = sim.world().cellY(member);
                memberCellXs[memberIndex] = memberX;
                memberCellYs[memberIndex] = memberY;
                memberZoneIds[memberIndex] = sim.getZoneGraph().zoneIdAt(memberX, memberY);
                if (sim.world().pathIdx(member)
                        < Paths.cellCount(sim.world().path(member))) {
                    activePathMembers++;
                }
                float velocityX = sim.movement().velX(member);
                float velocityY = sim.movement().velY(member);
                if (velocityX * velocityX + velocityY * velocityY > 0.0001f) {
                    movingMembers++;
                }
                if (primaryKnown) {
                    int fromDx = contact.primaryCellX() - memberX;
                    int fromDy = contact.primaryCellY() - memberY;
                    if (sim.getGrid().getCoverAt(memberX, memberY,
                            fromDx, fromDy) > 0
                            || sim.getDoodadCoverAt(memberX, memberY,
                            fromDx, fromDy) > 0) {
                        coveredFromPrimaryMembers++;
                    }
                }
                if (sim.combat().has(member)
                        && sim.combat().cooldownTimer(member) > 0f) {
                    coolingDownMembers++;
                }
            }
            if (detail) {
                long now = System.nanoTime();
                memberNanos += now - rowStageStarted;
                rowStageStarted = now;
            }
            SquadPlan.Step step = squad.currentPlan != null
                    ? squad.currentPlan.currentStep() : null;
            int currentZone = ZoneQueries.squadCurrentZone(squad, sim);
            boolean localContact = WorldStateBuilder.hasActionableContact(squad, sim);
            if (detail) {
                long now = System.nanoTime();
                contactNanos += now - rowStageStarted;
                rowStageStarted = now;
                // Input size, not the number examined by the early-exit contact query.
                beliefInputs += squad.believedContacts().size();
            }
            rows.add(new CommandSquadState(squad.id, squad.faction,
                    squad.aliveMembers, squad.centroidX, squad.centroidY,
                    anchorX, anchorY, currentZone, role, localContact,
                    squad.timeSinceUnderFire
                            < SquadMoraleSystem.MORALE_RECOVER_AFTER_FIRE_SECONDS,
                    squad.moraleBroken,
                    squad.currentGoal != null ? squad.currentGoal.name() : null,
                    step != null ? step.action.name() : null,
                    squad.assignmentExecutionSuspension(),
                    CommandFrameCopies.assignment(squad.assignedObjective),
                    ownAssignments.directiveFor(squad.id), activePathMembers,
                    movingMembers, coveredFromPrimaryMembers,
                    contact.primaryEngageableMembers(),
                    contact.primaryEngageableFireTeams(),
                    contact.posture().name(),
                    contact.doctrine().name(),
                    contact.contactInitiative().name(), coolingDownMembers,
                    memberZoneIds, memberCellXs, memberCellYs));
            if (detail) publicationNanos += System.nanoTime() - rowStageStarted;
        }
        long sortStarted = detail ? System.nanoTime() : 0L;
        rows.sort(Comparator.comparingInt(CommandSquadState::squadId));
        if (detail) {
            publicationNanos += System.nanoTime() - sortStarted;
            // One sample per perspective, not per row/member. These disjoint slices
            // exclude loop/filter and diagnostic overhead inside the outer envelope.
            profile.record(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_ROSTER, rosterNanos);
            profile.record(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_MEMBERS, memberNanos);
            profile.record(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_CONTACT, contactNanos);
            profile.record(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_PUBLICATION, publicationNanos);
            profile.recordCount(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_ROWS, rowCount);
            profile.recordCount(TickInnerProfile.Bucket.COMMANDER_FRAME_MEMBER_INPUTS, memberInputs);
            profile.recordCount(TickInnerProfile.Bucket.COMMANDER_FRAME_BELIEF_INPUTS, beliefInputs);
        }
        record(profile, TickInnerProfile.Bucket.COMMANDER_FRAME_SQUADS, stageStarted);
        stageStarted = profile != null ? System.nanoTime() : 0L;
        CommanderInfluenceSnapshot influence = sim.getCommanderInfluence(perspective);
        record(profile, TickInnerProfile.Bucket.COMMANDER_FRAME_INFLUENCE, stageStarted);
        return new CommandFrame(sim.getSimTickIndex(), perspective, rows, influence,
                topology, ownAssignments);
    }

    private static void record(TickInnerProfile profile, TickInnerProfile.Bucket bucket,
                               long started) {
        if (profile != null) profile.record(bucket, System.nanoTime() - started);
    }

    public int tick() { return tick; }
    public Faction perspective() { return perspective; }
    public List<CommandSquadState> squads() { return squads; }
    public CommanderInfluenceSnapshot influence() { return influence; }
    public CommandTopology topology() { return topology; }
    public CommandAssignmentSnapshot assignments() { return assignments; }

    public CommandSquadState squad(int squadId) {
        for (CommandSquadState squad : squads) {
            if (squad.squadId() == squadId) return squad;
        }
        return null;
    }
}
