package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
    /** Inclusive floor: one full subsequent command pulse cannot churn the order. */
    public static final int MIN_STABILITY_TICKS = Math.max(1, Math.round(
            CommanderService.COMMANDER_TICK_PERIOD / BattleSimulation.TICK_DT));
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
            if (current != null) {
                // Registered provenance wins over legacy direct writes. Every
                // migrated writer must use this arbiter or an explicit handoff.
                squad.assignedObjective = current.assignment();
                continue;
            }
            if (squad.assignedObjective == null) {
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
        assignExternal(squad, assignment, authority, issuer, reason, tick, -1);
    }

    public void assignExternal(Squad squad, ObjectiveAssignment assignment,
                               CommandAuthority authority, String issuer,
                               String reason, int tick, int leaseUntilTick) {
        Objects.requireNonNull(squad, "squad");
        Objects.requireNonNull(assignment, "assignment");
        if (assignment.squadId() != squad.id) {
            throw new IllegalArgumentException("assignment squad does not match target");
        }
        CommandDirective incumbent = active.get(squad.id);
        if (!mayReplace(incumbent, authority, issuer, tick)) return;
        if (incumbent != null && incumbent.issuer().equals(issuer)
                && incumbent.authority() == authority
                && Objects.equals(incumbent.assignment(), assignment)) return;
        String disposition = supersessionDisposition(incumbent, authority,
                issuer, tick);
        CommandDirective directive = new CommandDirective(squad.id, squad.faction,
                issuer, authority, reason, assignment, tick,
                stabilityFloor(authority, tick), leaseUntilTick,
                CommandDirective.Status.ACTIVE, disposition);
        active.put(squad.id, directive);
        squad.assignedObjective = assignment;
    }

    /** Claims command-pool ownership without imposing a tactical assignment. */
    public void claimExternal(Squad squad, CommandAuthority authority,
                              String issuer, String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective incumbent = active.get(squad.id);
        if (!mayReplace(incumbent, authority, issuer, tick)) return;
        if (incumbent != null && incumbent.issuer().equals(issuer)
                && incumbent.authority() == authority) return;
        active.put(squad.id, new CommandDirective(squad.id, squad.faction,
                issuer, authority, reason, null, tick, -1, -1,
                CommandDirective.Status.ACTIVE,
                supersessionDisposition(incumbent, authority, issuer, tick)));
        squad.assignedObjective = null;
    }

    private static boolean mayReplace(CommandDirective incumbent,
                                      CommandAuthority authority,
                                      String issuer, int tick) {
        return incumbent == null || incumbent.issuer().equals(issuer)
                || authority.priority() > incumbent.authority().priority()
                || incumbent.leaseUntilTick() >= 0
                && incumbent.leaseUntilTick() < tick;
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
                nextIssuer, nextAuthority, reason, nextAssignment, tick,
                nextAssignment != null ? stabilityFloor(nextAuthority, tick) : -1,
                -1,
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
        int tick = sim.getSimTickIndex();
        if (plan.tick() != tick) {
            throw new IllegalStateException("command plan tick does not match battle tick");
        }
        List<CommandProposal> proposals = new ArrayList<>(plan.proposals());
        proposals.sort(Comparator.comparingInt(CommandProposal::squadId));
        Set<Integer> seen = new HashSet<>();
        for (CommandProposal proposal : proposals) {
            if (proposal.authority() != CommandAuthority.MISSION_COMMAND) {
                throw new IllegalStateException(
                        "autonomous command proposal must use MISSION_COMMAND");
            }
            if (!seen.add(proposal.squadId())) {
                throw new IllegalStateException(
                        "duplicate command proposal for squad " + proposal.squadId());
            }
        }
        List<CommandDirective> results = new ArrayList<>(proposals.size());
        for (CommandProposal proposal : proposals) {
            results.add(commitOne(plan, proposal, sim, topology, tick));
        }
        return new CommanderSnapshot<>(plan.perspective(), plan.strategy(),
                plan.phase(), plan.tick(), plan.influenceTick(),
                plan.commandPoolSize(), plan.reserveCount(),
                plan.objectiveSummaries(), results, plan.detail());
    }

    private CommandDirective commitOne(CommandPlan<?> plan,
                                       CommandProposal proposal,
                                       BattleView sim,
                                       CommandTopology topology,
                                       int tick) {
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
                        incumbent.issuer(), incumbent.authority(), incumbent.reason(),
                        incumbent.assignment(), incumbent.issuedTick(),
                        incumbent.stableUntilTick(), incumbent.leaseUntilTick(),
                        CommandDirective.Status.RETAINED,
                        "retained incumbent assignment: " + proposal.reason());
            }
            if (squad.assignedObjective == null) {
                return new CommandDirective(squad.id, plan.perspective(),
                        plan.strategy(), proposal.authority(), proposal.reason(),
                        null, tick, -1, proposal.leaseUntilTick(),
                        CommandDirective.Status.UNASSIGNED,
                        "no incumbent assignment to retain");
            }
            return rejected(plan, proposal, "unregistered assignment is externally owned");
        }

        if (proposal.action() == CommandProposal.Action.CLAIM) {
            if (incumbent != null && !incumbent.issuer().equals(plan.strategy())
                    && ownershipBlocks(incumbent, proposal.authority(), tick)) {
                return rejected(plan, proposal,
                        "owned by " + incumbent.issuer() + " ("
                                + incumbent.authority() + ")");
            }
            boolean unchanged = incumbent != null
                    && incumbent.issuer().equals(plan.strategy())
                    && incumbent.authority() == proposal.authority()
                    && incumbent.assignment() == null;
            if (!unchanged && holdsStableIncumbent(incumbent, proposal, tick)) {
                return stabilityRetained(incumbent, proposal, tick);
            }
            int issuedTick = unchanged ? incumbent.issuedTick() : tick;
            CommandDirective claimed = new CommandDirective(squad.id,
                    plan.perspective(), plan.strategy(), proposal.authority(),
                    proposal.reason(), null, issuedTick, -1,
                    proposal.leaseUntilTick(), CommandDirective.Status.ACTIVE,
                    unchanged ? incumbent.dispositionReason()
                            : supersessionDisposition(incumbent, proposal, tick));
            active.put(squad.id, claimed);
            squad.assignedObjective = null;
            return claimed;
        }

        if (incumbent != null && !incumbent.issuer().equals(plan.strategy())
                && ownershipBlocks(incumbent, proposal.authority(), tick)) {
            return rejected(plan, proposal,
                    "owned by " + incumbent.issuer() + " (" + incumbent.authority() + ")");
        }

        if (proposal.action() == CommandProposal.Action.RELEASE) {
            if (incumbent != null && !incumbent.issuer().equals(plan.strategy())) {
                return rejected(plan, proposal,
                        "release requires incumbent issuer ownership");
            }
            if (holdsStableIncumbent(incumbent, proposal, tick)) {
                return stabilityRetained(incumbent, proposal, tick);
            }
            active.remove(squad.id);
            squad.assignedObjective = null;
            return new CommandDirective(squad.id, plan.perspective(),
                    plan.strategy(), proposal.authority(), proposal.reason(),
                    null, tick, -1, proposal.leaseUntilTick(),
                    CommandDirective.Status.RELEASED,
                    releaseDisposition(incumbent, proposal));
        }

        String invalid = validateShape(proposal.assignment(), topology);
        if (invalid != null) return rejected(plan, proposal, invalid);
        ObjectiveAssignment applied = resolveAppliedAssignment(
                proposal.assignment(), sim);
        if (applied == null) {
            return rejected(plan, proposal,
                    "target zone is not a current compound");
        }
        boolean unchanged = incumbent != null
                && incumbent.issuer().equals(plan.strategy())
                && Objects.equals(incumbent.assignment(), applied);
        if (!unchanged && holdsStableIncumbent(incumbent, proposal, tick)) {
            return stabilityRetained(incumbent, proposal, tick);
        }
        int issuedTick = unchanged ? incumbent.issuedTick() : tick;
        int stableUntilTick = unchanged ? incumbent.stableUntilTick()
                : stabilityFloor(proposal.authority(), tick);
        int leaseUntilTick = unchanged ? incumbent.leaseUntilTick()
                : proposal.leaseUntilTick();
        CommandDirective committed = new CommandDirective(squad.id,
                plan.perspective(), plan.strategy(), proposal.authority(),
                proposal.reason(), applied, issuedTick,
                stableUntilTick, leaseUntilTick, CommandDirective.Status.ACTIVE,
                unchanged ? incumbent.dispositionReason()
                        : supersessionDisposition(incumbent, proposal, tick));
        active.put(squad.id, committed);
        squad.assignedObjective = applied;
        return committed;
    }

    private static boolean ownershipBlocks(CommandDirective incumbent,
                                           CommandAuthority proposed,
                                           int tick) {
        if (proposed.priority() > incumbent.authority().priority()) return false;
        return incumbent.leaseUntilTick() < 0
                || incumbent.leaseUntilTick() >= tick;
    }

    private static boolean holdsStableIncumbent(CommandDirective incumbent,
                                                CommandProposal proposal,
                                                int tick) {
        return incumbent != null
                && incumbent.issuer() != null
                && incumbent.isStableAt(tick)
                && !proposal.stabilityBreak().permitsEarlySupersession();
    }

    private static CommandDirective stabilityRetained(
            CommandDirective incumbent, CommandProposal proposal, int tick) {
        return new CommandDirective(incumbent.squadId(), incumbent.perspective(),
                incumbent.issuer(), incumbent.authority(), incumbent.reason(),
                incumbent.assignment(), incumbent.issuedTick(),
                incumbent.stableUntilTick(), incumbent.leaseUntilTick(),
                CommandDirective.Status.RETAINED,
                "stable through tick " + incumbent.stableUntilTick()
                        + "; held " + proposal.reason() + " at tick " + tick);
    }

    private static int stabilityFloor(CommandAuthority authority, int tick) {
        return authority == CommandAuthority.MISSION_COMMAND
                ? tick + MIN_STABILITY_TICKS : -1;
    }

    private static String releaseDisposition(CommandDirective incumbent,
                                             CommandProposal proposal) {
        if (incumbent == null) return "no assignment to release";
        if (proposal.stabilityBreak().permitsEarlySupersession()) {
            return "released early: " + proposal.stabilityBreak().description();
        }
        return "released after stability interval";
    }

    private static String supersessionDisposition(CommandDirective incumbent,
                                                  CommandProposal proposal,
                                                  int tick) {
        if (incumbent == null) return "";
        if (proposal.stabilityBreak().permitsEarlySupersession()) {
            return "superseded early: " + proposal.stabilityBreak().description();
        }
        if (incumbent.leaseUntilTick() >= 0
                && incumbent.leaseUntilTick() < tick) {
            return "superseded expired lease from " + incumbent.issuer();
        }
        if (incumbent.stableUntilTick() >= 0
                && incumbent.stableUntilTick() < tick) {
            return "superseded after stability interval";
        }
        return "superseded incumbent directive";
    }

    private static String supersessionDisposition(CommandDirective incumbent,
                                                  CommandAuthority authority,
                                                  String issuer, int tick) {
        if (incumbent == null) return "";
        if (authority.priority() > incumbent.authority().priority()) {
            return "superseded " + incumbent.issuer() + " by higher authority";
        }
        if (incumbent.leaseUntilTick() >= 0
                && incumbent.leaseUntilTick() < tick) {
            return "superseded expired lease from " + incumbent.issuer();
        }
        return "superseded " + incumbent.issuer() + " by " + issuer;
    }

    private static ObjectiveAssignment resolveAppliedAssignment(
            ObjectiveAssignment assignment, BattleView sim) {
        if (assignment.kind() != AssignmentKind.SECURE_COMPOUND) {
            return assignment;
        }
        if (assignment.targetNode() != null) {
            for (CompoundService.Record record
                    : sim.getCompoundService().getRecords()) {
                if (!CommandFrameCopies.sameNodeIdentity(
                        assignment.targetNode(), record.node)) continue;
                int zoneId = sim.getCompoundService()
                        .captureZoneId(record, sim);
                return zoneId >= 0
                        ? ObjectiveAssignment.secureCompound(
                        assignment.squadId(), zoneId, record.node)
                        : null;
            }
            // A typed compound assignment must never fall through to another
            // compound merely because both currently share a navigation zone.
            return null;
        }
        // Legacy zone-only assignments have no stable authored identity.
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            int zoneId = sim.getCompoundService()
                    .captureZoneId(record, sim);
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
            case DEFEND_TRACK, DEFEND_SITE, DEFEND_AREA, ADVANCE_TRACK,
                    SWEEP_SECTOR, ESCORT, WITHDRAW -> {
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
                if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) {
                    return "assignment kind requires a complete target cell";
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
                proposal.assignment(), plan.tick(), -1, proposal.leaseUntilTick(),
                CommandDirective.Status.REJECTED, reason);
    }
}
