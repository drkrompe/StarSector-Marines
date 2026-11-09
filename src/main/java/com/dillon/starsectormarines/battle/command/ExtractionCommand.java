package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot.Role;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Marine generic-Extraction command for pickup, moving escort, and screens. */
public final class ExtractionCommand implements AutonomousMissionCommand<
        ExtractionCommandFrame, ExtractionCommandSnapshot> {

    private final EscortFormation formation = new EscortFormation();
    private volatile ExtractionCommandSnapshot extractionSnapshot;

    public ExtractionCommandSnapshot extractionSnapshot() {
        return extractionSnapshot;
    }

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return "extraction-attacker"; }

    @Override
    public CommandPlan<ExtractionCommandSnapshot> plan(
            ExtractionCommandFrame frame) {
        ExtractionObjectiveFacts payload = frame.facts().payload();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        Set<Integer> poolIds = new HashSet<>();
        for (CommandSquadState squad : pool) poolIds.add(squad.squadId());
        formation.retain(poolIds);

        int anchorX = objectiveAnchorX(payload);
        int anchorY = objectiveAnchorY(payload);
        if (!terminal(payload)) formation.assign(pool, anchorX, anchorY, frame.topology());

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        Set<Long> reservedTargets = new HashSet<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!poolIds.contains(squad.squadId())) {
                intents.add(new SquadIntent(squad.squadId(), Role.EXTERNAL,
                        "EXTERNAL_OWNERSHIP_PRESERVED", null, -1, -1,
                        squad.localContact()));
                continue;
            }
            if (terminal(payload)) {
                proposals.add(releaseOrRetain(squad, "OBJECTIVE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED,
                        "OBJECTIVE_TERMINAL", null, -1, -1,
                        squad.localContact()));
                continue;
            }

            Role role = formation.role(squad.squadId());
            int[] desired = desiredCell(role, anchorX, anchorY, payload);
            int[] target = EscortFormation.nearestReachable(squad, desired[0], desired[1],
                    anchorX, anchorY, frame.topology(), reservedTargets);
            if (target == null) {
                proposals.add(releaseOrRetain(squad,
                        "CORRIDOR_TARGET_UNREACHABLE"));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        "CORRIDOR_TARGET_UNREACHABLE", null, -1, -1,
                        squad.localContact()));
                continue;
            }
            reservedTargets.add(EscortFormation.cellKey(target[0], target[1]));

            ObjectiveAssignment assignment = EscortFormation.escortRole(role)
                    ? ObjectiveAssignment.escort(squad.squadId(),
                    target[0], target[1])
                    : ObjectiveAssignment.defendSite(squad.squadId(),
                    target[0], target[1]);
            String reason = reason(role, payload.phase());
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), target[0], target[1],
                    squad.localContact()));
        }

        ExtractionCommandSnapshot detail = new ExtractionCommandSnapshot(
                frame.tick(), faction(), payload.phase(), payload.payloadId(),
                payload.payloadName(), payload.sourceCellX(),
                payload.sourceCellY(), payload.payloadCellX(),
                payload.payloadCellY(), payload.corridorGuideCellX(),
                payload.corridorGuideCellY(), payload.egressCellX(),
                payload.egressCellY(), payload.progress(),
                payload.escortPresent(), payload.controllingSquadId(),
                payload.complete(), payload.failed(), payload.failure(), intents);
        return new CommandPlan<>(faction(), strategyId(), payload.phase(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                pool.size(), 0, List.of(summary(payload)), proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<ExtractionCommandSnapshot> snapshot) {
        extractionSnapshot = snapshot.detail();
    }

    private static int objectiveAnchorX(ExtractionObjectiveFacts payload) {
        return switch (payload.phase()) {
            case "AT_SOURCE", "SECURING" -> payload.sourceCellX();
            case "BOARDING" -> payload.egressCellX();
            default -> payload.payloadCellX();
        };
    }

    private static int objectiveAnchorY(ExtractionObjectiveFacts payload) {
        return switch (payload.phase()) {
            case "AT_SOURCE", "SECURING" -> payload.sourceCellY();
            case "BOARDING" -> payload.egressCellY();
            default -> payload.payloadCellY();
        };
    }

    private static boolean terminal(ExtractionObjectiveFacts payload) {
        return payload.complete() || payload.failed();
    }

    private static int[] desiredCell(Role role, int anchorX, int anchorY,
                                     ExtractionObjectiveFacts payload) {
        return EscortFormation.desiredCell(role, anchorX, anchorY,
                payload.corridorGuideCellX(), payload.corridorGuideCellY(),
                payload.egressCellX(), payload.egressCellY(),
                payload.sourceCellX(), payload.sourceCellY());
    }

    private static String reason(Role role, String phase) {
        if (role == Role.PAYLOAD_ELEMENT) {
            return switch (phase) {
                case "AT_SOURCE", "SECURING" -> "PACKAGE_PICKUP";
                case "BOARDING" -> "PACKAGE_BOARDING";
                default -> "PACKAGE_ESCORT";
            };
        }
        if (role == Role.CLOSE_ESCORT) {
            return "CLOSE_ESCORT_" + phase;
        }
        return "CORRIDOR_" + role.name() + "_" + phase;
    }

    private static CommandProposal releaseOrRetain(CommandSquadState squad,
                                                    String reason) {
        return squad.directive() != null
                && strategyOwned(squad.directive())
                ? CommandProposal.release(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason,
                CommandStabilityBreak.OBJECTIVE_COMPLETED)
                : CommandProposal.retain(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason);
    }

    private static boolean strategyOwned(CommandDirective directive) {
        return directive != null
                && "extraction-attacker".equals(directive.issuer());
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null
                || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private static String summary(ExtractionObjectiveFacts payload) {
        return payload.payloadName() + "=" + payload.phase().toLowerCase()
                + " " + Math.round(payload.progress() * 100f) + "%";
    }
}
