package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RaidCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Marine Raid strike command: service one target, then withdraw the force. */
public final class RaidCommand implements AutonomousMissionCommand<
        RaidCommandFrame, RaidCommandSnapshot> {

    private static final int[][] CORDON_OFFSETS = {
            {0, -4}, {4, 0}, {0, 4}, {-4, 0},
            {3, -3}, {3, 3}, {-3, 3}, {-3, -3}
    };
    private static final int[][] EGRESS_OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };

    private volatile RaidCommandSnapshot raidSnapshot;

    public RaidCommandSnapshot raidSnapshot() { return raidSnapshot; }
    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return "raid-attacker"; }

    @Override
    public CommandPlan<RaidCommandSnapshot> plan(RaidCommandFrame frame) {
        RaidCommandFacts facts = frame.facts();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        CommandSquadState service = facts.targetSecured() ? null : pool.stream()
                .filter(squad -> frame.topology().reachable(squad.anchorCellX(),
                        squad.anchorCellY(), facts.targetCellX(),
                        facts.targetCellY()))
                .min(Comparator.comparingInt(squad -> frame.topology().routeLength(
                        squad.anchorCellX(), squad.anchorCellY(),
                        facts.targetCellX(), facts.targetCellY())))
                .orElse(null);

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        int cordonIndex = 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!pool.contains(squad)) {
                intents.add(new SquadIntent(squad.squadId(), "EXTERNAL",
                        "EXTERNAL_OWNERSHIP_PRESERVED", null, -1, -1));
                continue;
            }
            ObjectiveAssignment assignment;
            String role;
            String reason;
            if (facts.targetSecured()) {
                int[] rally = reachableOffset(squad, facts.egressCellX(),
                        facts.egressCellY(), cordonIndex++, EGRESS_OFFSETS,
                        frame);
                if (rally == null) {
                    proposals.add(releaseOrRetain(squad, "EGRESS_UNREACHABLE"));
                    intents.add(new SquadIntent(squad.squadId(), "STRANDED",
                            "EGRESS_UNREACHABLE", null, -1, -1));
                    continue;
                }
                assignment = ObjectiveAssignment.withdraw(squad.squadId(),
                        rally[0], rally[1]);
                role = "WITHDRAWAL";
                reason = "TARGET_SECURED_WITHDRAW";
            } else if (squad == service) {
                assignment = ObjectiveAssignment.rushObjective(squad.squadId(),
                        0, facts.targetZoneId(), facts.targetCellX(),
                        facts.targetCellY());
                role = "SERVICE_ELEMENT";
                reason = "PRIMARY_TARGET_SERVICE";
            } else {
                int[] rally = reachableOffset(squad, facts.targetCellX(),
                        facts.targetCellY(), cordonIndex++, CORDON_OFFSETS,
                        frame);
                if (rally == null) {
                    proposals.add(releaseOrRetain(squad, "TARGET_UNREACHABLE"));
                    intents.add(new SquadIntent(squad.squadId(), "UNASSIGNED",
                            "TARGET_UNREACHABLE", null, -1, -1));
                    continue;
                }
                assignment = ObjectiveAssignment.defendSite(squad.squadId(),
                        rally[0], rally[1]);
                role = "SECURITY_ELEMENT";
                reason = "TARGET_CORDON";
            }
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), assignment.targetCellX(),
                    assignment.targetCellY()));
        }

        RaidCommandSnapshot detail = new RaidCommandSnapshot(frame.tick(),
                faction(), facts.phase().name(), facts.targetId(),
                facts.targetName(), facts.targetCellX(), facts.targetCellY(),
                facts.targetZoneId(), facts.egressCellX(), facts.egressCellY(),
                facts.serviceProgress(), facts.serviceDuration(),
                facts.targetSecured(), false, -1, intents);
        return new CommandPlan<>(faction(), strategyId(), facts.phase().name(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                pool.size(), 0, List.of(summary(facts)), proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<RaidCommandSnapshot> snapshot) {
        raidSnapshot = snapshot.detail();
    }

    private static String summary(RaidCommandFacts facts) {
        return facts.targetName() + "=" + facts.phase().name().toLowerCase()
                + " " + Math.round(facts.serviceProgress()) + "/"
                + Math.round(facts.serviceDuration());
    }

    private static CommandProposal releaseOrRetain(CommandSquadState squad,
                                                    String reason) {
        return squad.directive() != null
                ? CommandProposal.release(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason,
                CommandStabilityBreak.TARGET_UNREACHABLE)
                : CommandProposal.retain(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason);
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private static int[] reachableOffset(CommandSquadState squad, int centerX,
                                         int centerY, int index,
                                         int[][] offsets,
                                         RaidCommandFrame frame) {
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[(index + i) % offsets.length];
            int x = centerX + offset[0];
            int y = centerY + offset[1];
            if (frame.topology().isWalkable(x, y)
                    && frame.topology().reachable(squad.anchorCellX(),
                    squad.anchorCellY(), x, y)) return new int[]{x, y};
        }
        return frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(),
                centerX, centerY) ? new int[]{centerX, centerY} : null;
    }
}
