package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RaidCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Defender Raid security: one routine guard and bounded alarm mobilization. */
public final class RaidDefenderCommand implements AutonomousMissionCommand<
        RaidDefenderCommandFrame, RaidCommandSnapshot> {

    static final int ALARM_RESPONSE_LIMIT = 3;
    private static final int[][] RALLY_OFFSETS = {
            {0, -4}, {4, 0}, {0, 4}, {-4, 0},
            {3, -3}, {3, 3}, {-3, 3}, {-3, -3}, {0, 0}
    };

    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private volatile RaidCommandSnapshot raidSnapshot;

    public RaidDefenderCommand(Set<Integer> mobileSquadIds) {
        if (mobileSquadIds != null) initialMobileSquads.addAll(mobileSquadIds);
    }

    public RaidCommandSnapshot raidSnapshot() { return raidSnapshot; }
    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public String strategyId() { return "raid-defender"; }

    @Override
    public CommandPlan<RaidCommandSnapshot> plan(RaidDefenderCommandFrame frame) {
        RaidDefenderCommandFacts facts = frame.facts();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> initialMobileSquads.contains(squad.squadId())
                        || squad.directive() != null
                        && strategyId().equals(squad.directive().issuer()))
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .sorted(Comparator.comparingInt(squad -> frame.topology().routeLength(
                        squad.anchorCellX(), squad.anchorCellY(),
                        facts.targetCellX(), facts.targetCellY())))
                .toList();
        int assigned = facts.alarmActive() || facts.targetSecured()
                ? Math.min(ALARM_RESPONSE_LIMIT, pool.size())
                : Math.min(1, pool.size());
        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            int index = pool.indexOf(squad);
            if (index < 0) {
                intents.add(new SquadIntent(squad.squadId(), "AUTHORED_POST",
                        "GARRISON_OR_EXTERNAL_PRESERVED", null, -1, -1));
                continue;
            }
            if (index >= assigned) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, "MOBILE_RESERVE_HELD",
                        CommandStabilityBreak.CONTEXT_INVALIDATED));
                intents.add(new SquadIntent(squad.squadId(), "RESERVE",
                        "MOBILE_RESERVE_HELD", null, -1, -1));
                continue;
            }
            int[] rally = rally(squad, index, facts, frame);
            if (rally == null) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, "TARGET_UNREACHABLE",
                        CommandStabilityBreak.TARGET_UNREACHABLE));
                intents.add(new SquadIntent(squad.squadId(), "RESERVE",
                        "TARGET_UNREACHABLE", null, -1, -1));
                continue;
            }
            String role = index == 0 ? "ROUTINE_GUARD" : "ALARM_RESPONDER";
            String reason = facts.targetSecured() ? "TARGET_LOSS_RESPONSE"
                    : facts.alarmActive() ? "TARGET_ALARM_RESPONSE"
                    : "ROUTINE_TARGET_SECURITY";
            ObjectiveAssignment assignment = ObjectiveAssignment.defendSite(
                    squad.squadId(), rally[0], rally[1]);
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), rally[0], rally[1]));
        }
        String phase = facts.targetSecured() ? "TARGET_LOST"
                : facts.alarmActive() ? "ALARM_RESPONSE" : "ROUTINE_SECURITY";
        RaidCommandSnapshot detail = new RaidCommandSnapshot(frame.tick(),
                faction(), phase, facts.targetId(), facts.targetName(),
                facts.targetCellX(), facts.targetCellY(), facts.targetZoneId(),
                -1, -1, 0f, 0f, facts.targetSecured(), facts.alarmActive(),
                facts.alarmRaisedTick(), intents);
        return new CommandPlan<>(faction(), strategyId(), phase, frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                pool.size(), pool.size() - assigned,
                List.of(facts.targetName() + "=" + phase.toLowerCase()),
                proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<RaidCommandSnapshot> snapshot) {
        raidSnapshot = snapshot.detail();
    }

    private static int[] rally(CommandSquadState squad, int index,
                               RaidDefenderCommandFacts facts,
                               RaidDefenderCommandFrame frame) {
        for (int i = 0; i < RALLY_OFFSETS.length; i++) {
            int[] offset = RALLY_OFFSETS[(index + i) % RALLY_OFFSETS.length];
            int x = facts.targetCellX() + offset[0];
            int y = facts.targetCellY() + offset[1];
            if (frame.topology().isWalkable(x, y)
                    && frame.topology().reachable(squad.anchorCellX(),
                    squad.anchorCellY(), x, y)) return new int[]{x, y};
        }
        return null;
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }
}
