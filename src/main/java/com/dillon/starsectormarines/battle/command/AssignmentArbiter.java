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
     * What a leased squad goes back to. Keyed by squad id and populated only
     * while a lease stands over that squad: a lease does not discard the
     * directive it covers, it holds it here and puts it back on handback.
     */
    private final Map<Integer, CommandDirective> shelved = new HashMap<>();

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
                shelved.remove(squad.id);
                clearAssignment(squad);
                continue;
            }
            CommandDirective current = active.get(squad.id);
            if (isLease(current)) {
                adoptWriteUnderLease(squad, current,
                        missionIssuers.get(squad.faction), sim.getSimTickIndex());
                continue;
            }
            // Nothing is leased, so nothing is being held back for a handback.
            // A shelved directive that outlived its lease — superseded by a
            // higher authority, say — would otherwise be restored on top of
            // whoever took the squad next.
            shelved.remove(squad.id);
            if (current != null && Objects.equals(current.assignment(),
                    squad.assignedObjective)) {
                squad.assignedAuthority = assignmentAuthority(current);
                continue;
            }
            if (current != null) {
                // Registered provenance wins over legacy direct writes. Every
                // migrated writer must use this arbiter or an explicit handoff.
                applyAssignment(squad, current);
                continue;
            }
            if (squad.assignedObjective == null) {
                squad.assignedAuthority = null;
                continue;
            }
            String missionIssuer = missionIssuers.get(squad.faction);
            adoptCompatibilityAssignment(squad, squad.assignedObjective,
                    missionIssuer, sim.getSimTickIndex());
        }
        active.keySet().removeIf(id -> !liveSquads.contains(id));
        shelved.keySet().removeIf(id -> !liveSquads.contains(id));
    }

    /**
     * A commander's direct write during a lease is shelved rather than lost.
     *
     * <p>The sweep above already re-asserts registered provenance over a legacy
     * direct write; this is the same rule with the write kept instead of
     * discarded. The lease still owns what the squad executes — so the lease's
     * assignment is re-asserted on the field — but the squad hands back to what
     * the commander wants <em>now</em>, not to the intent that happened to be
     * standing at the instant the player clicked.
     */
    private void adoptWriteUnderLease(Squad squad, CommandDirective lease,
                                      String missionIssuer, int tick) {
        if (squad.assignedObjective != null
                && !Objects.equals(lease.assignment(), squad.assignedObjective)) {
            shelved.put(squad.id, compatibilityDirective(squad,
                    squad.assignedObjective, missionIssuer, tick));
        }
        applyAssignment(squad, lease);
    }

    private void adoptCompatibilityAssignment(Squad squad,
                                              ObjectiveAssignment assignment,
                                              String missionIssuer,
                                              int tick) {
        CommandDirective adopted = compatibilityDirective(squad, assignment,
                missionIssuer, tick);
        active.put(squad.id, adopted);
        squad.assignedAuthority = assignmentAuthority(adopted);
    }

    /** The directive an assignment written without provenance stands as. */
    private static CommandDirective compatibilityDirective(
            Squad squad, ObjectiveAssignment assignment,
            String missionIssuer, int tick) {
        CommandAuthority authority = externalAuthority(assignment,
                missionIssuer != null);
        String issuer = authority == CommandAuthority.MISSION_COMMAND
                ? missionIssuer : EXTERNAL_ISSUER;
        return new CommandDirective(squad.id, squad.faction,
                issuer, authority, "compatibility assignment adopted",
                assignment, tick, -1, CommandDirective.Status.ACTIVE, "");
    }

    /**
     * Who owns an assignment that arrived without provenance. A few kinds are
     * only ever written by one authority — nobody but a garrison holds a node,
     * nobody but a payload is escorted — and {@link OrderCatalog} is where that
     * is recorded. A kind with no such owner falls to the mission commander if
     * the faction has one and to a script if it does not.
     */
    private static CommandAuthority externalAuthority(ObjectiveAssignment assignment,
                                                      boolean hasMissionIssuer) {
        CommandAuthority presumed =
                OrderCatalog.row(assignment.kind()).externalAuthority();
        if (presumed != null) return presumed;
        return hasMissionIssuer
                ? CommandAuthority.MISSION_COMMAND : CommandAuthority.SCRIPTED;
    }

    public CommandAssignmentSnapshot snapshot() {
        return new CommandAssignmentSnapshot(active);
    }

    public CommandDirective activeDirective(int squadId) {
        return active.get(squadId);
    }

    /**
     * The directive a leased squad will go back to, or {@code null} when
     * nothing is being held for it. A readout that wants to name the mission a
     * squad is still under while it carries out somebody else's order asks
     * this; {@link #activeDirective} names the order it is carrying out.
     */
    public CommandDirective shelvedDirective(int squadId) {
        return shelved.get(squadId);
    }

    /**
     * Takes a squad on a bounded external authority, shelving whatever owned it.
     *
     * <p>This is the door the player's tactical orders come through, and the
     * reason they are not a side field on the squad: a lease is registered
     * provenance like any other directive, so the ledger can say who pointed a
     * squad somewhere and what it will go back to. The incumbent is not
     * displaced but <em>held</em> — {@link #endLease} puts it back — which is
     * what lets an order end without leaving the squad unowned for a tick.
     *
     * <p><b>A second lease keeps the first one's shelf.</b> Re-clicking is one
     * player changing their mind, not the player's own previous order becoming
     * the mission underneath; the commander's directive stays where the first
     * lease put it however many times the order is reissued.
     *
     * <p>An assignment written without provenance is adopted onto the shelf the
     * same way {@link #synchronizeCompatibilityAssignments} adopts one into the
     * ledger, so a squad whose mission arrived as a direct write still has one
     * to resume.
     *
     * @param leaseUntilTick last tick the lease may stand, or -1 for no bound
     */
    public CommandDirective lease(Squad squad, ObjectiveAssignment assignment,
                                  String issuer, String reason, int tick,
                                  int leaseUntilTick) {
        Objects.requireNonNull(squad, "squad");
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(issuer, "issuer");
        if (assignment.squadId() != squad.id) {
            throw new IllegalArgumentException("assignment squad does not match target");
        }
        CommandDirective incumbent = active.get(squad.id);
        String disposition;
        if (isLease(incumbent)) {
            disposition = "leased over " + incumbent.issuer();
        } else if (incumbent != null) {
            shelved.put(squad.id, incumbent);
            disposition = "leased over " + incumbent.issuer();
        } else {
            if (squad.assignedObjective != null) {
                shelved.put(squad.id, compatibilityDirective(squad,
                        squad.assignedObjective, null, tick));
            } else {
                shelved.remove(squad.id);
            }
            disposition = "leased an unowned squad";
        }
        CommandDirective leased = new CommandDirective(squad.id, squad.faction,
                issuer, CommandAuthority.PLAYER_INTERVENTION, reason, assignment,
                tick, -1, leaseUntilTick, CommandDirective.Status.ACTIVE,
                disposition);
        active.put(squad.id, leased);
        applyAssignment(squad, leased);
        return leased;
    }

    /**
     * Hands a leased squad back to what it was doing, if {@code issuer} is the
     * one holding the lease.
     *
     * <p>The shelved directive is restored in the same call that drops the
     * lease, so there is no tick on which the squad owns no assignment at all —
     * which is the whole difference between a handback and a release.
     *
     * @return false when no lease of {@code issuer}'s stands on this squad
     */
    public boolean endLease(Squad squad, String issuer, String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective standing = active.get(squad.id);
        if (!isLease(standing) || !standing.issuer().equals(issuer)) return false;
        CommandDirective resumed = resumptionFor(squad, standing, tick);
        if (resumed == null) {
            active.remove(squad.id);
            clearAssignment(squad);
            return true;
        }

        CommandDirective restored = new CommandDirective(resumed.squadId(),
                resumed.perspective(), resumed.issuer(), resumed.authority(),
                resumed.reason(), resumed.assignment(), resumed.issuedTick(),
                resumed.stableUntilTick(), resumed.leaseUntilTick(),
                CommandDirective.Status.ACTIVE, "resumed after lease: " + reason);
        install(squad, restored);
        return true;
    }

    /**
     * What a squad resumes when its lease ends: the shelf, unless a direct
     * write landed on the field since.
     *
     * <p>The compatibility sweep already moves such a write onto the shelf, but
     * only at command-pulse cadence, and an order can end between two pulses.
     * Restoring the shelf over the write would then discard it — which is how a
     * hard withdrawal written straight onto a leased squad disappeared, leaving
     * the squad resuming the task it had before anybody asked it to leave.
     */
    private CommandDirective resumptionFor(Squad squad, CommandDirective lease,
                                           int tick) {
        ObjectiveAssignment written = squad.assignedObjective;
        if (written != null && !Objects.equals(lease.assignment(), written)) {
            shelved.remove(squad.id);
            return compatibilityDirective(squad, written, null, tick);
        }
        return shelved.remove(squad.id);
    }

    /**
     * Ends a lease whose bound has passed. A lease with no bound (-1) never
     * expires here; it ends when its holder says so.
     *
     * @return true when this call ended a lease
     */
    public boolean expireLease(Squad squad, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective standing = active.get(squad.id);
        if (!isLease(standing)) return false;
        if (standing.leaseUntilTick() < 0
                || standing.leaseUntilTick() >= tick) return false;
        return endLease(squad, standing.issuer(), "lease expired", tick);
    }

    /**
     * Whether a directive is one {@link #lease} installed.
     * {@link CommandAuthority#PLAYER_INTERVENTION} is that method's alone —
     * no other writer issues it — so the authority is the marker rather than a
     * second flag on {@link CommandDirective} for the ledger to keep in step.
     */
    private static boolean isLease(CommandDirective directive) {
        return directive != null
                && directive.authority() == CommandAuthority.PLAYER_INTERVENTION;
    }

    /**
     * Makes {@code directive} the squad's active one and writes the field.
     *
     * <p>Anything but a lease landing here has taken the squad away from one,
     * so the shelf goes with it: a directive held for a handback that will
     * never come would otherwise be restored on top of whoever owns the squad
     * now.
     */
    private void install(Squad squad, CommandDirective directive) {
        active.put(squad.id, directive);
        if (!isLease(directive)) shelved.remove(squad.id);
        applyAssignment(squad, directive);
    }

    /** Writes a directive's assignment and the authority mirror beside it. */
    private static void applyAssignment(Squad squad, CommandDirective directive) {
        squad.assignedObjective = directive.assignment();
        squad.assignedAuthority = assignmentAuthority(directive);
    }

    private static void clearAssignment(Squad squad) {
        squad.assignedObjective = null;
        squad.assignedAuthority = null;
    }

    private static CommandAuthority assignmentAuthority(CommandDirective directive) {
        return directive.assignment() == null ? null : directive.authority();
    }

    /** Scoped mutation facade used by legacy mission planners during their serial pulse. */
    SquadDirectiveControl control(BattleView sim) {
        Objects.requireNonNull(sim, "sim");
        return new SquadDirectiveControl() {
            @Override
            public void claimSquadCommand(int squadId, CommandAuthority authority,
                                          String issuer, String reason) {
                claimExternal(requireSquad(sim, squadId), authority, issuer,
                        reason, sim.getSimTickIndex());
            }

            @Override
            public void assignSquadCommand(ObjectiveAssignment assignment,
                                           CommandAuthority authority,
                                           String issuer, String reason) {
                assignExternal(requireSquad(sim, assignment.squadId()), assignment,
                        authority, issuer, reason, sim.getSimTickIndex());
            }

            @Override
            public boolean handoffSquadCommand(int squadId, String currentIssuer,
                                               CommandAuthority nextAuthority,
                                               String nextIssuer,
                                               ObjectiveAssignment nextAssignment,
                                               String reason) {
                return handoff(requireSquad(sim, squadId), currentIssuer,
                        nextAuthority, nextIssuer, nextAssignment, reason,
                        sim.getSimTickIndex());
            }

            @Override
            public boolean releaseSquadCommand(int squadId, String issuer,
                                               String reason) {
                return releaseExternal(requireSquad(sim, squadId), issuer,
                        reason, sim.getSimTickIndex());
            }
        };
    }

    private static Squad requireSquad(BattleView sim, int squadId) {
        Squad squad = sim.getSquad(squadId);
        if (squad == null) {
            throw new IllegalArgumentException("unknown squad " + squadId);
        }
        return squad;
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
        install(squad, directive);
    }

    /** Claims command-pool ownership without imposing a tactical assignment. */
    public void claimExternal(Squad squad, CommandAuthority authority,
                              String issuer, String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective incumbent = active.get(squad.id);
        if (!mayReplace(incumbent, authority, issuer, tick)) return;
        if (incumbent != null && incumbent.issuer().equals(issuer)
                && incumbent.authority() == authority) return;
        install(squad, new CommandDirective(squad.id, squad.faction,
                issuer, authority, reason, null, tick, -1, -1,
                CommandDirective.Status.ACTIVE,
                supersessionDisposition(incumbent, authority, issuer, tick)));
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
        install(squad, next);
        return true;
    }

    /** Releases only a directive owned by {@code issuer}. */
    public boolean releaseExternal(Squad squad, String issuer,
                                   String reason, int tick) {
        Objects.requireNonNull(squad, "squad");
        CommandDirective incumbent = active.get(squad.id);
        if (incumbent == null || !incumbent.issuer().equals(issuer)) return false;
        active.remove(squad.id);
        shelved.remove(squad.id);
        clearAssignment(squad);
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
                    && ownershipBlocks(incumbent, proposal, tick)) {
                return rejected(plan, proposal, ownershipRejection(incumbent));
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
            install(squad, claimed);
            return claimed;
        }

        if (incumbent != null && !incumbent.issuer().equals(plan.strategy())
                && ownershipBlocks(incumbent, proposal, tick)) {
            return rejected(plan, proposal, ownershipRejection(incumbent));
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
            shelved.remove(squad.id);
            clearAssignment(squad);
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
        install(squad, committed);
        return committed;
    }

    /**
     * Why a proposal cannot have this squad. A lease says so in its own terms
     * — who holds it and how long it may stand — because "owned by player"
     * reads like a permanent loss of the squad rather than a bounded one, and
     * the tick the commander may have it back is the fact a reader wants.
     */
    private static String ownershipRejection(CommandDirective incumbent) {
        if (!isLease(incumbent)) {
            return "owned by " + incumbent.issuer()
                    + " (" + incumbent.authority() + ")";
        }
        return "leased by " + incumbent.issuer() + " until "
                + (incumbent.leaseUntilTick() < 0
                        ? "completion" : "tick " + incumbent.leaseUntilTick());
    }

    /**
     * Whether {@code incumbent} keeps this proposal off the squad.
     *
     * <p>A lease does not survive a hard withdrawal. Everything else the player
     * may order is a place to be, and a bounded interval of the commander not
     * getting its way about that is the whole point of a lease; a withdrawal is
     * the mission saying this squad is leaving, and a squad that walks where it
     * was pointed for another two minutes first is not withdrawing. The
     * exemption is deliberately narrow — an assignment of that one kind, never a
     * bare claim — so it cannot become the general escape hatch from a lease.
     */
    private static boolean ownershipBlocks(CommandDirective incumbent,
                                           CommandProposal proposal,
                                           int tick) {
        if (isLease(incumbent) && proposal.assignment() != null
                && proposal.assignment().kind() == AssignmentKind.WITHDRAW) {
            return false;
        }
        return ownershipBlocks(incumbent, proposal.authority(), tick);
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

    /**
     * Why a proposal is malformed, or null when it is not. Which slots a kind
     * owes is {@link OrderCatalog}'s to say; the topology checks below are this
     * arbiter's, since only it holds the frozen frame to ask.
     */
    private static String validateShape(ObjectiveAssignment assignment,
                                        CommandTopology topology) {
        if (assignment.kind() == null) return "assignment kind is required";
        switch (OrderCatalog.row(assignment.kind()).shape()) {
            case ZONE -> {
                if (assignment.targetZoneId() < 0) {
                    return "assignment kind requires a target zone";
                }
            }
            case CELL -> {
                if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) {
                    return "assignment kind requires a complete target cell";
                }
            }
            case NODE -> {
                if (assignment.targetNode() == null) {
                    return "assignment kind requires a target node";
                }
            }
            case OBJECTIVE_AT_CELL -> {
                if (assignment.objectiveId() < 0) {
                    return "assignment kind requires a target objective";
                }
                if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) {
                    return "assignment kind requires a complete target cell";
                }
            }
            case NONE -> { }
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
