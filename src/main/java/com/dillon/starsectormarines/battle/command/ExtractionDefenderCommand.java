package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Bounded source security and belief-driven interdiction for Extraction. */
public final class ExtractionDefenderCommand implements AutonomousMissionCommand<
        ExtractionDefenderCommandFrame, ExtractionDefenseSnapshot> {

    static final int ALARM_RESPONSE_LIMIT = 3;
    private static final int[][] RALLY_OFFSETS = {
            {0, -4}, {4, 0}, {0, 4}, {-4, 0},
            {3, -3}, {3, 3}, {-3, 3}, {-3, -3}, {0, 0}
    };

    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private volatile ExtractionDefenseSnapshot defenseSnapshot;

    public ExtractionDefenderCommand(Set<Integer> mobileSquadIds) {
        if (mobileSquadIds != null) initialMobileSquads.addAll(mobileSquadIds);
    }

    public ExtractionDefenseSnapshot defenseSnapshot() {
        return defenseSnapshot;
    }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public String strategyId() { return "extraction-defender"; }

    @Override
    public CommandPlan<ExtractionDefenseSnapshot> plan(
            ExtractionDefenderCommandFrame frame) {
        ExtractionObjectiveFacts payload = frame.facts().payload();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> initialMobileSquads.contains(squad.squadId())
                        || strategyOwned(squad.directive()))
                .filter(squad -> squad.directive() == null
                        || strategyOwned(squad.directive())
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .sorted(Comparator
                        .comparingInt((CommandSquadState squad) ->
                                frame.topology().routeLength(
                                        squad.anchorCellX(), squad.anchorCellY(),
                                        payload.sourceCellX(),
                                        payload.sourceCellY()))
                        .thenComparingInt(CommandSquadState::squadId))
                .toList();
        boolean terminal = payload.complete() || payload.failed();
        int assigned = terminal ? 0 : payload.alarmActive()
                ? Math.min(ALARM_RESPONSE_LIMIT, pool.size())
                : Math.min(1, pool.size());
        List<CommanderContact> contacts = contacts(frame);
        int freshestContactTick = contacts.stream()
                .mapToInt(CommanderContact::observedTick).max().orElse(-1);

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            int index = pool.indexOf(squad);
            if (index < 0) {
                Role role = squad.role() == UnitRole.GARRISON
                        ? Role.AUTHORED_POST : Role.EXTERNAL;
                intents.add(new SquadIntent(squad.squadId(), role,
                        role == Role.AUTHORED_POST
                                ? "AUTHORED_POST_PRESERVED"
                                : "EXTERNAL_OWNERSHIP_PRESERVED",
                        squad.assignment() != null
                                ? squad.assignment().kind() : null,
                        squad.assignment() != null
                                ? squad.assignment().targetCellX() : -1,
                        squad.assignment() != null
                                ? squad.assignment().targetCellY() : -1,
                        squad.localContact()));
                continue;
            }
            if (terminal) {
                proposals.add(strategyOwned(squad.directive())
                        ? CommandProposal.release(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "OBJECTIVE_TERMINAL",
                        CommandStabilityBreak.OBJECTIVE_COMPLETED)
                        : CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "OBJECTIVE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED,
                        "OBJECTIVE_TERMINAL", null, -1, -1,
                        squad.localContact()));
                continue;
            }
            if (index >= assigned) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "MOBILE_RESERVE_HELD",
                        CommandStabilityBreak.CONTEXT_INVALIDATED));
                intents.add(new SquadIntent(squad.squadId(), Role.RESERVE,
                        "MOBILE_RESERVE_HELD", null, -1, -1,
                        squad.localContact()));
                continue;
            }

            CommanderContact contact = index > 0 && !contacts.isEmpty()
                    ? contacts.get((index - 1) % contacts.size()) : null;
            int centerX = contact != null ? contact.cellX()
                    : payload.sourceCellX();
            int centerY = contact != null ? contact.cellY()
                    : payload.sourceCellY();
            int[] rally = rally(squad, index, centerX, centerY, frame);
            if (rally == null && contact != null) {
                rally = rally(squad, index, payload.sourceCellX(),
                        payload.sourceCellY(), frame);
            }
            if (rally == null) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "INTERDICTION_TARGET_UNREACHABLE",
                        CommandStabilityBreak.TARGET_UNREACHABLE));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        "INTERDICTION_TARGET_UNREACHABLE", null, -1, -1,
                        squad.localContact()));
                continue;
            }

            Role role = index == 0 ? Role.SOURCE_GUARD
                    : contact != null ? Role.INTERDICTION
                    : Role.ALARM_RESPONDER;
            String reason = role == Role.SOURCE_GUARD
                    ? payload.alarmActive() ? "SOURCE_ALARM_SECURITY"
                    : "ROUTINE_SOURCE_SECURITY"
                    : role == Role.INTERDICTION
                    ? "BELIEVED_CONTACT_INTERDICTION"
                    : "SOURCE_ALARM_RESPONSE";
            ObjectiveAssignment assignment = ObjectiveAssignment.defendSite(
                    squad.squadId(), rally[0], rally[1]);
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), rally[0], rally[1],
                    squad.localContact()));
        }

        Phase phase = terminal ? Phase.TERMINAL
                : payload.alarmActive() ? Phase.ALARM_INTERDICTION
                : Phase.ROUTINE_SECURITY;
        ExtractionDefenseSnapshot detail = new ExtractionDefenseSnapshot(
                frame.tick(), faction(), phase, payload.payloadId(),
                payload.payloadName(), payload.sourceCellX(),
                payload.sourceCellY(), payload.alarmActive(),
                payload.alarmRaisedTick(), payload.complete(), payload.failed(),
                payload.failure(), contacts.size(), freshestContactTick,
                pool.size(), Math.max(0, pool.size() - assigned), intents);
        return new CommandPlan<>(faction(), strategyId(), phase.name(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                pool.size(), Math.max(0, pool.size() - assigned),
                List.of(payload.payloadName() + "="
                        + phase.name().toLowerCase()), proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<ExtractionDefenseSnapshot> snapshot) {
        defenseSnapshot = snapshot.detail();
    }

    private static List<CommanderContact> contacts(
            ExtractionDefenderCommandFrame frame) {
        if (frame.influence() == null) return List.of();
        return frame.influence().contacts().stream()
                .sorted(Comparator
                        .comparingInt(CommanderContact::observedTick).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                CommanderContact::confidence).reversed())
                        .thenComparingInt(CommanderContact::cellY)
                        .thenComparingInt(CommanderContact::cellX))
                .toList();
    }

    private static int[] rally(CommandSquadState squad, int slot,
                               int centerX, int centerY,
                               ExtractionDefenderCommandFrame frame) {
        for (int i = 0; i < RALLY_OFFSETS.length; i++) {
            int[] offset = RALLY_OFFSETS[(slot + i) % RALLY_OFFSETS.length];
            int x = centerX + offset[0];
            int y = centerY + offset[1];
            if (frame.topology().isWalkable(x, y)
                    && frame.topology().reachable(squad.anchorCellX(),
                    squad.anchorCellY(), x, y)) return new int[]{x, y};
        }
        return null;
    }

    private static boolean strategyOwned(CommandDirective directive) {
        return directive != null
                && "extraction-defender".equals(directive.issuer());
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null || Objects.equals(
                incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }
}
