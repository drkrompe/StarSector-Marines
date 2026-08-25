package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Battle-owned assignment ledger and the sole commit point for framed command plans. */
public final class AssignmentArbiter {

    private static final String EXTERNAL_ISSUER = "external";

    private final Map<Integer, CommandDirective> active = new HashMap<>();

    /**
     * Adopts assignments written by not-yet-migrated systems before frames are
     * frozen. This compatibility boundary disappears when all writers submit
     * explicit ownership through this service.
     */
    public void synchronizeCompatibilityAssignments(
            BattleView sim, Map<Faction, String> missionIssuers) {
        Set<Integer> liveSquads = new HashSet<>();
        for (Squad squad : sim.getSquads()) {
            liveSquads.add(squad.id);
            CommandDirective current = active.get(squad.id);
            if (current != null && Objects.equals(current.assignment(),
                    squad.assignedObjective)) continue;
            if (squad.assignedObjective == null) {
                active.remove(squad.id);
                continue;
            }
            String missionIssuer = missionIssuers.get(squad.faction);
            CommandAuthority authority = externalAuthority(squad.assignedObjective,
                    missionIssuer != null);
            String issuer = authority == CommandAuthority.MISSION_COMMAND
                    ? missionIssuer : EXTERNAL_ISSUER;
            active.put(squad.id, new CommandDirective(squad.id, squad.faction,
                    issuer, authority,
                    "compatibility assignment adopted",
                    squad.assignedObjective, sim.getSimTickIndex(), -1,
                    CommandDirective.Status.ACTIVE, ""));
        }
        active.keySet().removeIf(id -> !liveSquads.contains(id));
    }

    private static CommandAuthority externalAuthority(ObjectiveAssignment assignment,
                                                      boolean hasMissionIssuer) {
        if (assignment.kind() == AssignmentKind.HOLD_NODE) {
            return CommandAuthority.GARRISON;
        }
        if (assignment.kind() == AssignmentKind.ESCORT) {
            return CommandAuthority.PAYLOAD;
        }
        return hasMissionIssuer
                ? CommandAuthority.MISSION_COMMAND : CommandAuthority.SCRIPTED;
    }

    public CommandAssignmentSnapshot snapshot() {
        return new CommandAssignmentSnapshot(active);
    }

    public CommandDirective activeDirective(int squadId) {
        return active.get(squadId);
    }

    public void assignExternal(Squad squad, ObjectiveAssignment assignment,
                               CommandAuthority authority, String issuer,
                               String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        Objects.requireNonNull(assignment, "assignment");
        if (assignment.squadId() != squad.id) {
            throw new IllegalArgumentException("assignment squad does not match target");
        }
        CommandDirective directive = new CommandDirective(squad.id, squad.faction,
                issuer, authority, reason, assignment, tick, -1,
                CommandDirective.Status.ACTIVE, "");
        active.put(squad.id, directive);
        squad.assignedObjective = assignment;
    }

    public <D> CommanderSnapshot<D> commit(CommandPlan<D> plan,
                                            BattleView sim,
                                            CommandTopology topology) {
        List<CommandProposal> proposals = new ArrayList<>(plan.proposals());
        proposals.sort(Comparator.comparingInt(CommandProposal::squadId));
        List<CommandDirective> results = new ArrayList<>(proposals.size());
        for (CommandProposal proposal : proposals) {
            results.add(commitOne(plan, proposal, sim, topology));
        }
        return new CommanderSnapshot<>(plan.perspective(), plan.strategy(),
                plan.phase(), plan.tick(), plan.influenceTick(),
                plan.commandPoolSize(), plan.reserveCount(),
                plan.objectiveSummaries(), results, plan.detail());
    }

    private CommandDirective commitOne(CommandPlan<?> plan,
                                       CommandProposal proposal,
                                       BattleView sim,
                                       CommandTopology topology) {
        Squad squad = sim.getSquad(proposal.squadId());
        if (squad == null || squad.aliveMembers <= 0) {
            return rejected(plan, proposal, "squad is not live");
        }
        if (squad.faction != plan.perspective()) {
            return rejected(plan, proposal, "squad belongs to another perspective");
        }
        CommandDirective incumbent = active.get(squad.id);
        if (proposal.action() == CommandProposal.Action.RETAIN) {
            if (incumbent != null) {
                return new CommandDirective(squad.id, incumbent.perspective(),
                        incumbent.issuer(), incumbent.authority(), proposal.reason(),
                        incumbent.assignment(), incumbent.issuedTick(),
                        incumbent.leaseUntilTick(), CommandDirective.Status.RETAINED,
                        "retained incumbent assignment");
            }
            if (squad.assignedObjective == null) {
                return new CommandDirective(squad.id, plan.perspective(),
                        plan.strategy(), proposal.authority(), proposal.reason(),
                        null, plan.tick(), proposal.leaseUntilTick(),
                        CommandDirective.Status.UNASSIGNED,
                        "no incumbent assignment to retain");
            }
            return rejected(plan, proposal, "unregistered assignment is externally owned");
        }

        if (incumbent != null && !incumbent.issuer().equals(plan.strategy())
                && incumbent.authority().priority() >= proposal.authority().priority()) {
            return rejected(plan, proposal,
                    "owned by " + incumbent.issuer() + " (" + incumbent.authority() + ")");
        }
        if (incumbent != null && incumbent.leaseUntilTick() >= plan.tick()
                && !incumbent.issuer().equals(plan.strategy())) {
            return rejected(plan, proposal, "incumbent lease remains active");
        }

        if (proposal.action() == CommandProposal.Action.RELEASE) {
            active.remove(squad.id);
            squad.assignedObjective = null;
            return new CommandDirective(squad.id, plan.perspective(),
                    plan.strategy(), proposal.authority(), proposal.reason(),
                    null, plan.tick(), proposal.leaseUntilTick(),
                    CommandDirective.Status.RELEASED, "assignment released");
        }

        ObjectiveAssignment applied = resolveAppliedAssignment(
                proposal.assignment(), sim);
        String invalid = validate(applied, topology);
        if (invalid != null) return rejected(plan, proposal, invalid);
        int issuedTick = incumbent != null
                && incumbent.issuer().equals(plan.strategy())
                && Objects.equals(incumbent.assignment(), applied)
                ? incumbent.issuedTick() : plan.tick();
        CommandDirective committed = new CommandDirective(squad.id,
                plan.perspective(), plan.strategy(), proposal.authority(),
                proposal.reason(), applied, issuedTick,
                proposal.leaseUntilTick(), CommandDirective.Status.ACTIVE, "");
        active.put(squad.id, committed);
        squad.assignedObjective = applied;
        return committed;
    }

    private static ObjectiveAssignment resolveAppliedAssignment(
            ObjectiveAssignment assignment, BattleView sim) {
        if (assignment.kind() != AssignmentKind.SECURE_COMPOUND
                || assignment.targetZoneId() < 0) return assignment;
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            int zoneId = sim.getZoneGraph().zoneIdAt(
                    record.node.anchorX, record.node.anchorY);
            if (zoneId == assignment.targetZoneId()) {
                return ObjectiveAssignment.secureCompound(assignment.squadId(),
                        zoneId, record.node);
            }
        }
        return assignment;
    }

    private static String validate(ObjectiveAssignment assignment,
                                   CommandTopology topology) {
        if (assignment.targetZoneId() >= 0
                && topology.zone(assignment.targetZoneId()) == null) {
            return "target zone does not exist in the frozen topology";
        }
        if (assignment.targetCellX() >= 0 || assignment.targetCellY() >= 0) {
            if (!topology.inBounds(assignment.targetCellX(), assignment.targetCellY())) {
                return "target cell is outside the frozen topology";
            }
            if (!topology.isWalkable(assignment.targetCellX(), assignment.targetCellY())) {
                return "target cell is not walkable in the frozen topology";
            }
        }
        return null;
    }

    private static CommandDirective rejected(CommandPlan<?> plan,
                                             CommandProposal proposal,
                                             String reason) {
        return new CommandDirective(proposal.squadId(), plan.perspective(),
                plan.strategy(), proposal.authority(), proposal.reason(),
                proposal.assignment(), plan.tick(), proposal.leaseUntilTick(),
                CommandDirective.Status.REJECTED, reason);
    }
}
