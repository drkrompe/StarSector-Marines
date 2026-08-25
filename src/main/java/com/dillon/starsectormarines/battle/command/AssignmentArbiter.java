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
            if (squad.aliveMembers <= 0 && sim.squadMemberCount(squad.id) <= 0) {
                active.remove(squad.id);
                squad.assignedObjective = null;
                continue;
            }
            CommandDirective current = active.get(squad.id);
            if (current != null && Objects.equals(current.assignment(),
                    squad.assignedObjective)) continue;
            if (squad.assignedObjective == null) {
                active.remove(squad.id);
                continue;
            }
            String missionIssuer = missionIssuers.get(squad.faction);
            adoptCompatibilityAssignment(squad, squad.assignedObjective,
                    missionIssuer, sim.getSimTickIndex());
        }
        active.keySet().removeIf(id -> !liveSquads.contains(id));
    }

    private void adoptCompatibilityAssignment(Squad squad,
                                              ObjectiveAssignment assignment,
                                              String missionIssuer,
                                              int tick) {
        CommandAuthority authority = externalAuthority(assignment,
                missionIssuer != null);
        String issuer = authority == CommandAuthority.MISSION_COMMAND
                ? missionIssuer : EXTERNAL_ISSUER;
        active.put(squad.id, new CommandDirective(squad.id, squad.faction,
                issuer, authority, "compatibility assignment adopted",
                assignment, tick, -1, CommandDirective.Status.ACTIVE, ""));
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
        CommandDirective incumbent = active.get(squad.id);
        if (!mayReplace(incumbent, authority, issuer)) return;
        if (incumbent != null && incumbent.issuer().equals(issuer)
                && incumbent.authority() == authority
                && Objects.equals(incumbent.assignment(), assignment)) return;
        CommandDirective directive = new CommandDirective(squad.id, squad.faction,
                issuer, authority, reason, assignment, tick, -1,
                CommandDirective.Status.ACTIVE, "");
        active.put(squad.id, directive);
        squad.assignedObjective = assignment;
    }

    /** Claims command-pool ownership without imposing a tactical assignment. */
    public void claimExternal(Squad squad, CommandAuthority authority,
                              String issuer, String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective incumbent = active.get(squad.id);
        if (!mayReplace(incumbent, authority, issuer)) return;
        if (incumbent != null && incumbent.issuer().equals(issuer)
                && incumbent.authority() == authority) return;
        active.put(squad.id, new CommandDirective(squad.id, squad.faction,
                issuer, authority, reason, null, tick, -1,
                CommandDirective.Status.ACTIVE, ""));
        squad.assignedObjective = null;
    }

    private static boolean mayReplace(CommandDirective incumbent,
                                      CommandAuthority authority,
                                      String issuer) {
        return incumbent == null || incumbent.issuer().equals(issuer)
                || authority.priority() > incumbent.authority().priority();
    }

    /** Atomically transfers ownership when the named incumbent still owns the squad. */
    public boolean handoff(Squad squad, String currentIssuer,
                           CommandAuthority nextAuthority, String nextIssuer,
                           ObjectiveAssignment nextAssignment,
                           String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective incumbent = active.get(squad.id);
        if (incumbent == null || !incumbent.issuer().equals(currentIssuer)) {
            return false;
        }
        if (nextAssignment != null && nextAssignment.squadId() != squad.id) {
            throw new IllegalArgumentException("assignment squad does not match target");
        }
        CommandDirective next = new CommandDirective(squad.id, squad.faction,
                nextIssuer, nextAuthority, reason, nextAssignment, tick, -1,
                CommandDirective.Status.ACTIVE,
                "handed off from " + currentIssuer);
        active.put(squad.id, next);
        squad.assignedObjective = nextAssignment;
        return true;
    }

    /** Releases only a directive owned by {@code issuer}. */
    public boolean releaseExternal(Squad squad, String issuer,
                                   String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective incumbent = active.get(squad.id);
        if (incumbent == null || !incumbent.issuer().equals(issuer)) return false;
        active.remove(squad.id);
        squad.assignedObjective = null;
        return true;
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

        String invalid = validateShape(proposal.assignment(), topology);
        if (invalid != null) return rejected(plan, proposal, invalid);
        ObjectiveAssignment applied = resolveAppliedAssignment(
                proposal.assignment(), sim);
        if (applied == null) {
            return rejected(plan, proposal,
                    "target zone is not a current compound");
        }
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
        if (assignment.kind() != AssignmentKind.SECURE_COMPOUND) {
            return assignment;
        }
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            int zoneId = sim.getZoneGraph().zoneIdAt(
                    record.node.anchorX, record.node.anchorY);
            if (zoneId == assignment.targetZoneId()) {
                return ObjectiveAssignment.secureCompound(assignment.squadId(),
                        zoneId, record.node);
            }
        }
        return null;
    }

    private static String validateShape(ObjectiveAssignment assignment,
                                        CommandTopology topology) {
        if (assignment.kind() == null) return "assignment kind is required";
        switch (assignment.kind()) {
            case CLEAR_ZONE, SECURE_COMPOUND -> {
                if (assignment.targetZoneId() < 0) {
                    return "assignment kind requires a target zone";
                }
            }
            case DEFEND_TRACK, SWEEP_SECTOR, ESCORT -> {
                if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) {
                    return "assignment kind requires a complete target cell";
                }
            }
            case HOLD_NODE -> {
                if (assignment.targetNode() == null) {
                    return "assignment kind requires a target node";
                }
            }
            case RUSH_OBJECTIVE -> {
                if (assignment.objectiveId() < 0) {
                    return "assignment kind requires a target objective";
                }
            }
            case SUPPORT -> { }
        }
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
