package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot.Role;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The moving escort around one payload ({@code extraction-command.md}): a payload element on it, a close escort just
 * behind, and screens ahead, to the flanks and to the rear along the route's next guide cell. Extraction's corridor
 * and the Ruins haul both escort this way; this class is that shared rule, never copied.
 *
 * <p>Roles are stable: a squad keeps its role while it lives and stays in the pool, and the payload element is
 * reassigned only when it can no longer reach the payload.
 */
final class PayloadEscortFormation {

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

    /** Forgets squads that left the pool. */
    void retain(Set<Integer> poolIds) {
        roles.keySet().retainAll(poolIds);
    }

    /** Forgets every role, so the next {@link #assign} starts the formation afresh. */
    void clear() {
        roles.clear();
        payloadSquadId = -1;
    }

    Role role(int squadId) {
        return roles.get(squadId);
    }

    void assign(List<CommandSquadState> pool, int anchorX, int anchorY,
                CommandTopology topology) {
        CommandSquadState payloadSquad = pool.stream()
                .filter(squad -> squad.squadId() == payloadSquadId)
                .findFirst().orElse(null);
        if (payloadSquad == null || !topology.reachable(
                payloadSquad.anchorCellX(), payloadSquad.anchorCellY(),
                anchorX, anchorY)) {
            roles.entrySet().removeIf(entry ->
                    entry.getValue() == Role.PAYLOAD_ELEMENT);
            payloadSquad = pool.stream()
                    .filter(squad -> topology.reachable(
                            squad.anchorCellX(), squad.anchorCellY(),
                            anchorX, anchorY))
                    .min(Comparator
                            .comparingInt((CommandSquadState squad) ->
                                    topology.routeLength(
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

    /**
     * Where a role stands around the anchor, facing the guide cell (or the egress when there is none, or the
     * source-to-egress line when the anchor is on the guide).
     */
    static int[] desiredCell(Role role, int anchorX, int anchorY,
                             int guideCellX, int guideCellY,
                             int egressX, int egressY,
                             int sourceX, int sourceY) {
        int guideX = guideCellX >= 0 ? guideCellX : egressX;
        int guideY = guideCellY >= 0 ? guideCellY : egressY;
        int dx = Integer.compare(guideX, anchorX);
        int dy = Integer.compare(guideY, anchorY);
        if (dx == 0 && dy == 0) {
            dx = Integer.compare(egressX, sourceX);
            dy = Integer.compare(egressY, sourceY);
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

    /**
     * A walkable cell the squad can reach near the desired cell, else near the fallback, else the fallback's
     * neighbourhood ignoring reservations; {@code null} when none is reachable.
     */
    static int[] nearestReachable(
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

    static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFF_FFFFL);
    }

    static boolean escortRole(Role role) {
        return role == Role.PAYLOAD_ELEMENT || role == Role.CLOSE_ESCORT;
    }
}
