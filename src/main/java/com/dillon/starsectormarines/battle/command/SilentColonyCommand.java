package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot.Reason;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot.Role;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Frame-only Marine commander that keeps stable archive and survivor branches
 * through the blind Silent Colony expedition.
 */
public final class SilentColonyCommand implements AutonomousMissionCommand<
        SilentColonyCommandFrame, SilentColonyCommandSnapshot> {

    public static final String ISSUER = "silent-colony";
    private static final int STRENGTH_ROUTE_CREDIT = 2;
    private static final int TARGET_SEARCH_RADIUS = 5;
    private static final int[][] SURVIVOR_OFFSETS = {
            {0, 0}, {0, -4}, {4, 0}, {0, 4}, {-4, 0},
            {3, -3}, {3, 3}, {-3, 3}, {-3, -3}
    };

    private final Map<Integer, Role> branches = new HashMap<>();
    private final Map<Integer, Reason> membershipReasons = new HashMap<>();
    private volatile SilentColonyCommandSnapshot expeditionSnapshot;

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return ISSUER; }

    public SilentColonyCommandSnapshot expeditionSnapshot() {
        return expeditionSnapshot;
    }

    @Override
    public CommandPlan<SilentColonyCommandSnapshot> plan(
            SilentColonyCommandFrame frame) {
        SilentColonyCommandFacts facts = frame.facts();
        boolean archiveNeeded = !facts.archive().complete();
        boolean survivorsNeeded = survivorsNeedCommand(facts.survivors());
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> ownedByStrategy(squad, frame.tick()))
                .toList();
        Set<Integer> poolIds = new HashSet<>();
        for (CommandSquadState squad : pool) poolIds.add(squad.squadId());
        branches.keySet().retainAll(poolIds);
        membershipReasons.keySet().retainAll(poolIds);
        assignBranches(pool, frame, archiveNeeded, survivorsNeeded);

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        int archiveBranchSquads = 0;
        int survivorBranchSquads = 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!poolIds.contains(squad.squadId())) {
                CommandDirective incumbent = squad.directive();
                ObjectiveAssignment assignment = incumbent != null
                        ? incumbent.assignment() : squad.assignment();
                proposals.add(CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        Reason.EXTERNAL_OWNERSHIP_PRESERVED.name()));
                intents.add(new SquadIntent(squad.squadId(), Role.EXTERNAL,
                        Reason.EXTERNAL_OWNERSHIP_PRESERVED,
                        Reason.EXTERNAL_OWNERSHIP_PRESERVED,
                        assignment != null ? assignment.kind() : null,
                        assignment != null ? assignment.targetCellX() : -1,
                        assignment != null ? assignment.targetCellY() : -1,
                        squad.localContact()));
                continue;
            }
            if (!archiveNeeded && !survivorsNeeded) {
                CommandDirective incumbent = squad.directive();
                boolean strategyOwned = incumbent != null
                        && ISSUER.equals(incumbent.issuer());
                proposals.add(releaseOrRetain(squad,
                        Reason.EXPEDITION_OBJECTIVES_COMPLETE));
                ObjectiveAssignment assignment = incumbent != null
                        ? incumbent.assignment() : squad.assignment();
                intents.add(new SquadIntent(squad.squadId(),
                        strategyOwned ? Role.RELEASED : Role.EXTERNAL,
                        strategyOwned ? membershipReason(squad)
                                : Reason.EXTERNAL_OWNERSHIP_PRESERVED,
                        strategyOwned
                                ? Reason.EXPEDITION_OBJECTIVES_COMPLETE
                                : Reason.EXTERNAL_OWNERSHIP_PRESERVED,
                        assignment != null ? assignment.kind() : null,
                        assignment != null ? assignment.targetCellX() : -1,
                        assignment != null ? assignment.targetCellY() : -1,
                        squad.localContact()));
                continue;
            }

            Role branch = branches.get(squad.squadId());
            if (branch == Role.ARCHIVE_RECOVERY) {
                archiveBranchSquads++;
                assignArchive(squad, frame, proposals, intents);
            } else if (branch == Role.SURVIVOR_ESCORT) {
                survivorBranchSquads++;
                assignSurvivors(squad, frame, proposals, intents);
            } else {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        Reason.TARGET_UNREACHABLE.name(),
                        CommandStabilityBreak.TARGET_UNREACHABLE));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        membershipReason(squad), Reason.TARGET_UNREACHABLE,
                        null, -1, -1, squad.localContact()));
            }
        }

        Phase phase = phase(archiveNeeded, survivorsNeeded);
        int influenceTick = frame.influence() != null
                ? frame.influence().updatedTick() : -1;
        int knownPressure = frame.influence() != null
                ? frame.influence().contacts().size() : 0;
        SilentColonyCommandSnapshot detail = new SilentColonyCommandSnapshot(
                frame.tick(), influenceTick, faction(), phase,
                facts.survivors(), facts.archive(), frame.archiveZoneId(),
                knownPressure, archiveBranchSquads, survivorBranchSquads,
                intents);
        List<String> summaries = List.of(
                summary(facts.archive()), summary(facts.survivors()));
        return new CommandPlan<>(faction(), strategyId(), phase.name(),
                frame.tick(), influenceTick, pool.size(), 0,
                summaries, proposals, detail);
    }

    @Override
    public void publish(
            CommanderSnapshot<SilentColonyCommandSnapshot> snapshot) {
        expeditionSnapshot = snapshot.detail();
    }

    private void assignBranches(
            List<CommandSquadState> pool, SilentColonyCommandFrame frame,
            boolean archiveNeeded, boolean survivorsNeeded) {
        if (pool.isEmpty() || !archiveNeeded && !survivorsNeeded) return;
        if (!archiveNeeded) {
            for (CommandSquadState squad : pool) {
                if (canServeSurvivors(squad, frame)) {
                    setBranch(squad, Role.SURVIVOR_ESCORT,
                            branches.get(squad.squadId()) == Role.ARCHIVE_RECOVERY
                                    ? Reason.ARCHIVE_COMPLETE_REJOIN
                                    : Reason.INITIAL_ROUTE_AND_STRENGTH);
                } else {
                    removeBranch(squad);
                }
            }
            return;
        }
        if (!survivorsNeeded) {
            for (CommandSquadState squad : pool) {
                if (canServeArchive(squad, frame)) {
                    setBranch(squad, Role.ARCHIVE_RECOVERY,
                            branches.get(squad.squadId()) == Role.SURVIVOR_ESCORT
                                    ? Reason.SURVIVORS_GONE_REINFORCE_ARCHIVE
                                    : Reason.INITIAL_ROUTE_AND_STRENGTH);
                } else {
                    removeBranch(squad);
                }
            }
            return;
        }

        boolean hadBranches = !branches.isEmpty();
        for (CommandSquadState squad : pool) {
            Role branch = branches.get(squad.squadId());
            if ((branch == Role.ARCHIVE_RECOVERY
                    && !canServeArchive(squad, frame))
                    || (branch == Role.SURVIVOR_ESCORT
                    && !canServeSurvivors(squad, frame))) {
                removeBranch(squad);
            }
        }

        List<CommandSquadState> unassigned = pool.stream()
                .filter(squad -> !branches.containsKey(squad.squadId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int archiveCount = branchCount(Role.ARCHIVE_RECOVERY);
        int survivorCount = branchCount(Role.SURVIVOR_ESCORT);

        if (pool.size() == 1 && archiveCount + survivorCount == 0) {
            CommandSquadState squad = pool.get(0);
            boolean archiveReachable = canServeArchive(squad, frame);
            boolean survivorsReachable = canServeSurvivors(squad, frame);
            if (archiveReachable || survivorsReachable) {
                Role branch = archiveReachable && (!survivorsReachable
                        || archiveAffinity(squad, frame) <= 0)
                        ? Role.ARCHIVE_RECOVERY : Role.SURVIVOR_ESCORT;
                setBranch(squad, branch, Reason.INITIAL_ROUTE_AND_STRENGTH);
            }
            return;
        }

        if (archiveCount == 0) {
            CommandSquadState candidate = bestArchive(unassigned, frame);
            if (candidate == null && survivorCount > 1) {
                candidate = bestArchive(pool, frame);
            }
            if (candidate != null) {
                Role previous = branches.get(candidate.squadId());
                setBranch(candidate, Role.ARCHIVE_RECOVERY,
                        hadBranches
                                ? Reason.ARCHIVE_BRANCH_LOSS_REBALANCE
                                : Reason.INITIAL_ROUTE_AND_STRENGTH);
                unassigned.remove(candidate);
                archiveCount++;
                if (previous == Role.SURVIVOR_ESCORT) survivorCount--;
            }
        }
        if (survivorCount == 0) {
            CommandSquadState candidate = bestSurvivor(unassigned, frame);
            if (candidate == null && archiveCount > 1) {
                candidate = bestSurvivor(pool, frame);
            }
            if (candidate != null) {
                Role previous = branches.get(candidate.squadId());
                setBranch(candidate, Role.SURVIVOR_ESCORT,
                        hadBranches
                                ? Reason.SURVIVOR_BRANCH_LOSS_REBALANCE
                                : Reason.INITIAL_ROUTE_AND_STRENGTH);
                unassigned.remove(candidate);
                survivorCount++;
                if (previous == Role.ARCHIVE_RECOVERY) archiveCount--;
            }
        }

        int desiredArchive = Math.max(1, pool.size() / 3);
        unassigned.sort(Comparator
                .comparingLong((CommandSquadState squad) ->
                        archiveAffinity(squad, frame))
                .thenComparing(Comparator.comparingInt(
                        CommandSquadState::aliveMembers).reversed())
                .thenComparingInt(CommandSquadState::squadId));
        for (CommandSquadState squad : unassigned) {
            if (archiveCount < desiredArchive
                    && canServeArchive(squad, frame)) {
                setBranch(squad, Role.ARCHIVE_RECOVERY,
                        Reason.INITIAL_ROUTE_AND_STRENGTH);
                archiveCount++;
            } else if (canServeSurvivors(squad, frame)) {
                setBranch(squad, Role.SURVIVOR_ESCORT,
                        Reason.INITIAL_ROUTE_AND_STRENGTH);
                survivorCount++;
            } else if (canServeArchive(squad, frame)) {
                setBranch(squad, Role.ARCHIVE_RECOVERY,
                        Reason.INITIAL_ROUTE_AND_STRENGTH);
                archiveCount++;
            }
        }
    }

    private void assignArchive(
            CommandSquadState squad, SilentColonyCommandFrame frame,
            List<CommandProposal> proposals, List<SquadIntent> intents) {
        ExtractionObjectiveFacts archive = frame.facts().archive();
        boolean reachable = canServeArchive(squad, frame);
        if (!reachable) {
            proposals.add(CommandProposal.claim(squad.squadId(),
                    CommandAuthority.MISSION_COMMAND,
                    Reason.TARGET_UNREACHABLE.name(),
                    CommandStabilityBreak.TARGET_UNREACHABLE));
            intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                    membershipReason(squad), Reason.TARGET_UNREACHABLE,
                    null, -1, -1, squad.localContact()));
            return;
        }
        ObjectiveAssignment assignment = ObjectiveAssignment.sweepSector(
                squad.squadId(), archive.sourceCellX(), archive.sourceCellY());
        proposals.add(CommandProposal.assign(assignment,
                CommandAuthority.MISSION_COMMAND,
                Reason.RECOVER_SEALED_ARCHIVE.name(),
                stabilityBreak(squad, assignment)));
        intents.add(new SquadIntent(squad.squadId(), Role.ARCHIVE_RECOVERY,
                membershipReason(squad), Reason.RECOVER_SEALED_ARCHIVE,
                assignment.kind(), archive.sourceCellX(),
                archive.sourceCellY(), squad.localContact()));
    }

    private void assignSurvivors(
            CommandSquadState squad, SilentColonyCommandFrame frame,
            List<CommandProposal> proposals, List<SquadIntent> intents) {
        ExtractionObjectiveFacts survivors = frame.facts().survivors();
        boolean atSource = "AT_SOURCE".equals(survivors.phase());
        int centerX = atSource
                ? frame.facts().shelterApproachCellX()
                : validCell(survivors.payloadCellX(), survivors.sourceCellX());
        int centerY = atSource
                ? frame.facts().shelterApproachCellY()
                : validCell(survivors.payloadCellY(), survivors.sourceCellY());
        int[] target = survivorTarget(squad, centerX, centerY,
                frame.topology());
        if (target == null) {
            proposals.add(CommandProposal.claim(squad.squadId(),
                    CommandAuthority.MISSION_COMMAND,
                    Reason.TARGET_UNREACHABLE.name(),
                    CommandStabilityBreak.TARGET_UNREACHABLE));
            intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                    membershipReason(squad), Reason.TARGET_UNREACHABLE,
                    null, -1, -1, squad.localContact()));
            return;
        }
        Reason reason = atSource ? Reason.REACH_COLONY_SURVIVORS
                : Reason.ESCORT_COLONY_SURVIVORS;
        ObjectiveAssignment assignment = ObjectiveAssignment.escort(
                squad.squadId(), target[0], target[1]);
        proposals.add(CommandProposal.assign(assignment,
                CommandAuthority.MISSION_COMMAND, reason.name(),
                stabilityBreak(squad, assignment)));
        intents.add(new SquadIntent(squad.squadId(), Role.SURVIVOR_ESCORT,
                membershipReason(squad), reason, assignment.kind(),
                target[0], target[1], squad.localContact()));
    }

    private static int[] survivorTarget(
            CommandSquadState squad, int centerX, int centerY,
            CommandTopology topology) {
        int start = Math.floorMod(squad.squadId(), SURVIVOR_OFFSETS.length);
        for (int radius = 0; radius <= TARGET_SEARCH_RADIUS; radius++) {
            for (int i = 0; i < SURVIVOR_OFFSETS.length; i++) {
                int[] offset = SURVIVOR_OFFSETS[
                        (start + i) % SURVIVOR_OFFSETS.length];
                int x = centerX + offset[0];
                int y = centerY + offset[1];
                if (Math.abs(offset[0]) + Math.abs(offset[1]) > radius * 2
                        || !topology.isWalkable(x, y)) continue;
                if (topology.reachable(squad.anchorCellX(),
                        squad.anchorCellY(), x, y)) return new int[]{x, y};
            }
        }
        return null;
    }

    private static boolean canServeArchive(
            CommandSquadState squad, SilentColonyCommandFrame frame) {
        ExtractionObjectiveFacts archive = frame.facts().archive();
        return frame.topology().reachable(squad.anchorCellX(),
                squad.anchorCellY(), archive.sourceCellX(),
                archive.sourceCellY());
    }

    private static boolean canServeSurvivors(
            CommandSquadState squad, SilentColonyCommandFrame frame) {
        ExtractionObjectiveFacts survivors = frame.facts().survivors();
        boolean atSource = "AT_SOURCE".equals(survivors.phase());
        int centerX = atSource
                ? frame.facts().shelterApproachCellX()
                : validCell(survivors.payloadCellX(), survivors.sourceCellX());
        int centerY = atSource
                ? frame.facts().shelterApproachCellY()
                : validCell(survivors.payloadCellY(), survivors.sourceCellY());
        return survivorTarget(squad, centerX, centerY,
                frame.topology()) != null;
    }

    private static boolean survivorsNeedCommand(
            ExtractionObjectiveFacts survivors) {
        return !survivors.complete() && !survivors.failed()
                && survivors.activeElements() > 0;
    }

    private boolean ownedByStrategy(CommandSquadState squad, int tick) {
        CommandDirective incumbent = squad.directive();
        if (incumbent == null) return false;
        if (ISSUER.equals(incumbent.issuer())
                && incumbent.authority() == CommandAuthority.MISSION_COMMAND) {
            return true;
        }
        return incumbent.authority() == CommandAuthority.PLAYER_INTERVENTION
                && incumbent.leaseUntilTick() >= 0
                && incumbent.leaseUntilTick() < tick;
    }

    private static Phase phase(boolean archiveNeeded,
                               boolean survivorsNeeded) {
        if (archiveNeeded && survivorsNeeded) {
            return Phase.DIVIDED_EXPEDITION;
        }
        if (archiveNeeded) return Phase.ARCHIVE_RECOVERY_ONLY;
        if (survivorsNeeded) return Phase.SURVIVOR_ESCORT_ONLY;
        return Phase.EXPEDITION_COMPLETE;
    }

    private static int validCell(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private int branchCount(Role role) {
        int count = 0;
        for (Role branch : branches.values()) {
            if (branch == role) count++;
        }
        return count;
    }

    private void setBranch(CommandSquadState squad, Role branch,
                           Reason reason) {
        Role previous = branches.put(squad.squadId(), branch);
        if (previous != branch) {
            membershipReasons.put(squad.squadId(), reason);
        } else {
            membershipReasons.putIfAbsent(squad.squadId(), reason);
        }
    }

    private void removeBranch(CommandSquadState squad) {
        branches.remove(squad.squadId());
        membershipReasons.remove(squad.squadId());
    }

    private Reason membershipReason(CommandSquadState squad) {
        return membershipReasons.getOrDefault(squad.squadId(),
                Reason.INITIAL_ROUTE_AND_STRENGTH);
    }

    private static CommandSquadState bestArchive(
            List<CommandSquadState> candidates,
            SilentColonyCommandFrame frame) {
        return candidates.stream()
                .filter(squad -> canServeArchive(squad, frame))
                .min(Comparator.comparingLong((CommandSquadState squad) ->
                        archiveAffinity(squad, frame))
                .thenComparing(Comparator.comparingInt(
                        CommandSquadState::aliveMembers).reversed())
                .thenComparingInt(CommandSquadState::squadId))
                .orElse(null);
    }

    private static CommandSquadState bestSurvivor(
            List<CommandSquadState> candidates,
            SilentColonyCommandFrame frame) {
        return candidates.stream()
                .filter(squad -> canServeSurvivors(squad, frame))
                .max(Comparator.comparingLong((CommandSquadState squad) ->
                        archiveAffinity(squad, frame))
                .thenComparingInt(CommandSquadState::aliveMembers)
                .thenComparing(Comparator.comparingInt(
                        CommandSquadState::squadId).reversed()))
                .orElse(null);
    }

    private static long archiveAffinity(
            CommandSquadState squad, SilentColonyCommandFrame frame) {
        ExtractionObjectiveFacts archive = frame.facts().archive();
        int archiveCost = routeCost(frame, squad, archive.sourceCellX(),
                archive.sourceCellY());
        int survivorCost = routeCost(frame, squad,
                frame.facts().shelterApproachCellX(),
                frame.facts().shelterApproachCellY());
        return (long) archiveCost - survivorCost
                - (long) squad.aliveMembers() * STRENGTH_ROUTE_CREDIT;
    }

    private static int routeCost(
            SilentColonyCommandFrame frame, CommandSquadState squad,
            int targetX, int targetY) {
        int cost = frame.topology().routeLength(squad.anchorCellX(),
                squad.anchorCellY(), targetX, targetY);
        return cost == Integer.MAX_VALUE ? 1_000_000 : cost;
    }

    private static CommandProposal releaseOrRetain(
            CommandSquadState squad, Reason reason) {
        return squad.directive() != null
                && ISSUER.equals(squad.directive().issuer())
                ? CommandProposal.release(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason.name(),
                CommandStabilityBreak.OBJECTIVE_COMPLETED)
                : CommandProposal.retain(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason.name());
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null
                || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private static String summary(ExtractionObjectiveFacts objective) {
        return objective.payloadName() + "=" + objective.phase().toLowerCase()
                + " progress=" + objective.progress();
    }
}
