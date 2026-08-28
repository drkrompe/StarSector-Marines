package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot.Role;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Marine generic-Extraction command for pickup, moving escort, and screens. */
public final class ExtractionCommand implements AutonomousMissionCommand<
        ExtractionCommandFrame, ExtractionCommandSnapshot> {

    private static final List<Role> SCREEN_ROLES = List.of(
            Role.LEAD_SCREEN, Role.LEFT_SCREEN,
            Role.RIGHT_SCREEN, Role.REAR_SCREEN);
    private static final int CLOSE_OFFSET = 2;
    private static final int SCREEN_FORWARD = 6;
    private static final int SCREEN_LATERAL = 5;
    private static final int SCREEN_REAR = 5;
    private static final int SNAP_RADIUS = 5;

    private final Map<Integer, Role> roles = new HashMap<>();
    private int payloadSquadId = -1;
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
        roles.keySet().retainAll(poolIds);

        int anchorX = objectiveAnchorX(payload);
        int anchorY = objectiveAnchorY(payload);
        if (!terminal(payload)) assignStableRoles(pool, anchorX, anchorY, frame);

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

            Role role = roles.get(squad.squadId());
            int[] desired = desiredCell(role, anchorX, anchorY, payload);
            int[] target = nearestReachable(squad, desired[0], desired[1],
                    anchorX, anchorY, frame.topology(), reservedTargets);
            if (target == null) {
                proposals.add(releaseOrRetain(squad,
                        "CORRIDOR_TARGET_UNREACHABLE"));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        "CORRIDOR_TARGET_UNREACHABLE", null, -1, -1,
                        squad.localContact()));
                continue;
            }
            reservedTargets.add(cellKey(target[0], target[1]));

            ObjectiveAssignment assignment = escortRole(role)
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

    private void assignStableRoles(List<CommandSquadState> pool,
                                   int anchorX, int anchorY,
                                   ExtractionCommandFrame frame) {
        CommandSquadState payloadSquad = pool.stream()
                .filter(squad -> squad.squadId() == payloadSquadId)
                .findFirst().orElse(null);
        if (payloadSquad == null || !frame.topology().reachable(
                payloadSquad.anchorCellX(), payloadSquad.anchorCellY(),
                anchorX, anchorY)) {
            roles.entrySet().removeIf(entry ->
                    entry.getValue() == Role.PAYLOAD_ELEMENT);
            payloadSquad = pool.stream()
                    .filter(squad -> frame.topology().reachable(
                            squad.anchorCellX(), squad.anchorCellY(),
                            anchorX, anchorY))
                    .min(Comparator
                            .comparingInt((CommandSquadState squad) ->
                                    frame.topology().routeLength(
                                            squad.anchorCellX(),
                                            squad.anchorCellY(), anchorX,
                                            anchorY))
                            .thenComparingInt(CommandSquadState::squadId))
                    .orElse(null);
            payloadSquadId = payloadSquad != null
                    ? payloadSquad.squadId() : -1;
        }
        if (payloadSquad != null) {
            roles.put(payloadSquad.squadId(), Role.PAYLOAD_ELEMENT);
        }

        boolean hasClose = roles.containsValue(Role.CLOSE_ESCORT);
        if (!hasClose && pool.size() > 1) {
            pool.stream()
                    .filter(squad -> squad.squadId() != payloadSquadId)
                    .min(Comparator.comparingInt(CommandSquadState::squadId))
                    .ifPresent(squad -> roles.put(squad.squadId(),
                            Role.CLOSE_ESCORT));
        }

        Map<Role, Integer> loads = new EnumMap<>(Role.class);
        for (Role role : roles.values()) loads.merge(role, 1, Integer::sum);
        for (CommandSquadState squad : pool) {
            if (roles.containsKey(squad.squadId())) continue;
            Role selected = SCREEN_ROLES.stream()
                    .min(Comparator
                            .comparingInt((Role role) ->
                                    loads.getOrDefault(role, 0))
                            .thenComparingInt(Enum::ordinal))
                    .orElse(Role.LEAD_SCREEN);
            roles.put(squad.squadId(), selected);
            loads.merge(selected, 1, Integer::sum);
        }
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
        int guideX = payload.corridorGuideCellX() >= 0
                ? payload.corridorGuideCellX() : payload.egressCellX();
        int guideY = payload.corridorGuideCellY() >= 0
                ? payload.corridorGuideCellY() : payload.egressCellY();
        int dx = Integer.compare(guideX, anchorX);
        int dy = Integer.compare(guideY, anchorY);
        if (dx == 0 && dy == 0) {
            dx = Integer.compare(payload.egressCellX(), payload.sourceCellX());
            dy = Integer.compare(payload.egressCellY(), payload.sourceCellY());
        }
        if (dx == 0 && dy == 0) dx = 1;
        int leftX = -dy;
        int leftY = dx;
        return switch (role) {
            case PAYLOAD_ELEMENT -> new int[]{anchorX, anchorY};
            case CLOSE_ESCORT -> new int[]{anchorX - dx * CLOSE_OFFSET,
                    anchorY - dy * CLOSE_OFFSET};
            case LEAD_SCREEN -> new int[]{anchorX + dx * SCREEN_FORWARD,
                    anchorY + dy * SCREEN_FORWARD};
            case LEFT_SCREEN -> new int[]{anchorX + leftX * SCREEN_LATERAL,
                    anchorY + leftY * SCREEN_LATERAL};
            case RIGHT_SCREEN -> new int[]{anchorX - leftX * SCREEN_LATERAL,
                    anchorY - leftY * SCREEN_LATERAL};
            case REAR_SCREEN -> new int[]{anchorX - dx * SCREEN_REAR,
                    anchorY - dy * SCREEN_REAR};
            default -> new int[]{anchorX, anchorY};
        };
    }

    private static int[] nearestReachable(
            CommandSquadState squad, int desiredX, int desiredY,
            int fallbackX, int fallbackY, CommandTopology topology,
            Set<Long> reservedTargets) {
        int[] nearDesired = searchReachable(squad, desiredX, desiredY,
                SNAP_RADIUS, topology, reservedTargets);
        if (nearDesired != null) return nearDesired;
        int[] nearFallback = searchReachable(squad, fallbackX, fallbackY,
                SNAP_RADIUS, topology, reservedTargets);
        if (nearFallback != null) return nearFallback;
        return searchReachable(squad, fallbackX, fallbackY,
                SNAP_RADIUS, topology, Set.of());
    }

    private static int[] searchReachable(
            CommandSquadState squad, int centerX, int centerY,
            int radius, CommandTopology topology, Set<Long> reservedTargets) {
        for (int distance = 0; distance <= radius; distance++) {
            for (int y = centerY - distance; y <= centerY + distance; y++) {
                for (int x = centerX - distance; x <= centerX + distance; x++) {
                    if (Math.abs(x - centerX) + Math.abs(y - centerY)
                            != distance) continue;
                    if (reservedTargets.contains(cellKey(x, y))) continue;
                    if (!topology.isWalkable(x, y)) continue;
                    if (topology.reachable(squad.anchorCellX(),
                            squad.anchorCellY(), x, y)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFF_FFFFL);
    }

    private static boolean escortRole(Role role) {
        return role == Role.PAYLOAD_ELEMENT || role == Role.CLOSE_ESCORT;
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
