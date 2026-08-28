package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.AuthoredDuty;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot.Role;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Frame-only Marine commander for Civilian Rescue. Authored shelter and pickup
 * guards remain external while the mobile force relieves the shelter and then
 * occupies stable, separated cohort-screen roles along the public route.
 */
public final class RescueEscortCommand implements AutonomousMissionCommand<
        RescueCommandFrame, RescueCommandSnapshot> {

    public static final int ADVANCE_SCREEN_CELLS = 5;
    /** Compatibility name for the legacy two-cell pressured bound. */
    public static final int ENGAGED_BOUND_CELLS = 2;
    public static final int ENGAGED_BOUND_TICKS = 150;
    static final int LATERAL_SLOT_SPACING = 5;
    static final int ECHELON_DEPTH_SPACING = 3;
    static final int MIN_SLOT_SEPARATION = 4;
    private static final int SLOT_SEARCH_RADIUS = 5;
    private static final List<Role> SCREEN_ROLES = List.of(
            Role.LEAD_SCREEN, Role.LEFT_SCREEN,
            Role.RIGHT_SCREEN, Role.REAR_SCREEN);

    private final CivilianEvacuationPlacement placement;
    private final Map<Integer, Role> roles = new HashMap<>();
    private final Map<Integer, Integer> nextPressureBoundTick = new HashMap<>();
    private volatile RescueCommandSnapshot rescueSnapshot;

    public RescueEscortCommand(CivilianEvacuationPlacement placement) {
        this.placement = Objects.requireNonNull(placement, "placement");
    }

    public RescueCommandSnapshot rescueSnapshot() { return rescueSnapshot; }

    /** Direct-test seam; production registration uses the paired command service. */
    public void tick(BattleView sim) {
        CommanderService.runSingle(this, RescueCommandDisclosure.INSTANCE, sim);
    }

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return "rescue-corridor"; }

    @Override
    public CommandPlan<RescueCommandSnapshot> plan(RescueCommandFrame frame) {
        ExtractionObjectiveFacts cohort = frame.facts().cohort();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> frame.facts().authoredDuty(squad.squadId()) == null)
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        Set<Integer> poolIds = new HashSet<>();
        for (CommandSquadState squad : pool) poolIds.add(squad.squadId());
        roles.keySet().retainAll(poolIds);
        nextPressureBoundTick.keySet().retainAll(poolIds);
        if (!terminal(cohort)) assignStableRoles(pool);

        boolean atShelter = "AT_SOURCE".equals(cohort.phase());
        int anchorX = atShelter ? placement.shelterApproachX
                : validCell(cohort.payloadCellX(), placement.shelterX);
        int anchorY = atShelter ? placement.shelterApproachY
                : validCell(cohort.payloadCellY(), placement.shelterY);
        int[] route = frame.topology().route(anchorX, anchorY,
                placement.liftX, placement.liftY);
        int guideIndex = Paths.isEmpty(route) ? -1
                : Math.min(ADVANCE_SCREEN_CELLS,
                Paths.cellCount(route) - 1);
        int guideX = guideIndex >= 0 ? Paths.cellX(route, guideIndex) : anchorX;
        int guideY = guideIndex >= 0 ? Paths.cellY(route, guideIndex) : anchorY;
        int[] forward = direction(anchorX, anchorY, guideX, guideY);

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        List<int[]> claimed = new ArrayList<>();
        int knownPressure = frame.influence() != null
                ? frame.influence().contacts().size() : 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!poolIds.contains(squad.squadId())) {
                intents.add(externalIntent(squad,
                        frame.facts().authoredDuty(squad.squadId())));
                continue;
            }
            if (terminal(cohort)) {
                proposals.add(releaseOrRetain(squad, "RESCUE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED,
                        "RESCUE_TERMINAL", null, -1, -1,
                        squad.localContact(), false));
                continue;
            }

            Role role = roles.get(squad.squadId());
            int roleOrdinal = roleOrdinal(squad, pool, role);
            int[] ideal = atShelter
                    ? formationCell(role, roleOrdinal,
                    anchorX, anchorY, forward)
                    : corridorCell(role, anchorX, anchorY, guideX, guideY,
                    forward, roleOrdinal);
            int[] target = nearestReachable(squad, ideal[0], ideal[1],
                    anchorX, anchorY, frame.topology(), claimed);
            if (target == null) {
                proposals.add(releaseOrRetain(squad,
                        "RESCUE_TARGET_UNREACHABLE"));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        "RESCUE_TARGET_UNREACHABLE", null, -1, -1,
                        squad.localContact(), false));
                continue;
            }

            boolean slowed = locallySlow(squad, frame.tick(), target);
            if (slowed) {
                ObjectiveAssignment incumbent = squad.directive().assignment();
                target = new int[]{incumbent.targetCellX(),
                        incumbent.targetCellY()};
            }
            claimed.add(target);
            ObjectiveAssignment assignment = ObjectiveAssignment.escort(
                    squad.squadId(), target[0], target[1]);
            String reason = reason(role, atShelter, slowed);
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), target[0], target[1],
                    squad.localContact(), slowed));
        }

        RescueCommandSnapshot detail = new RescueCommandSnapshot(
                frame.tick(), faction(), cohort.phase(), cohort.payloadId(),
                cohort.payloadName(), placement.shelterX, placement.shelterY,
                cohort.payloadCellX(), cohort.payloadCellY(), guideX, guideY,
                placement.liftX, placement.liftY, cohort.initialElements(),
                cohort.activeElements(), cohort.boardedElements(),
                cohort.lostElements(), cohort.progress(),
                cohort.escortPresent(), cohort.controllingSquadId(),
                knownPressure, cohort.complete(), cohort.failed(),
                cohort.failure(), intents);
        return new CommandPlan<>(faction(), strategyId(), cohort.phase(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                pool.size(), 0, List.of(summary(cohort)), proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<RescueCommandSnapshot> snapshot) {
        rescueSnapshot = snapshot.detail();
    }

    private void assignStableRoles(List<CommandSquadState> pool) {
        if (!roles.containsValue(Role.COHORT_ESCORT)) {
            pool.stream().filter(squad -> !roles.containsKey(squad.squadId()))
                    .min(Comparator.comparingInt(CommandSquadState::squadId))
                    .ifPresent(squad -> roles.put(squad.squadId(),
                            Role.COHORT_ESCORT));
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

    private boolean locallySlow(CommandSquadState squad, int tick,
                                int[] desired) {
        if (!squad.localContact() || squad.directive() == null
                || !strategyId().equals(squad.directive().issuer())
                || squad.directive().assignment() == null) {
            nextPressureBoundTick.remove(squad.squadId());
            return false;
        }
        int next = nextPressureBoundTick.computeIfAbsent(squad.squadId(),
                ignored -> tick + ENGAGED_BOUND_TICKS);
        if (tick < next) return true;
        nextPressureBoundTick.put(squad.squadId(),
                tick + ENGAGED_BOUND_TICKS);
        ObjectiveAssignment current = squad.directive().assignment();
        return current.targetCellX() == desired[0]
                && current.targetCellY() == desired[1];
    }

    private static int[] corridorCell(Role role, int cohortX, int cohortY,
                                      int guideX, int guideY, int[] forward,
                                      int roleOrdinal) {
        if (role == Role.COHORT_ESCORT) return new int[]{cohortX, cohortY};
        return formationCell(role, roleOrdinal, guideX, guideY, forward);
    }

    private static int[] formationCell(Role role, int roleOrdinal,
                                       int anchorX, int anchorY,
                                       int[] forward) {
        int leftX = -forward[1];
        int leftY = forward[0];
        return switch (role) {
            case COHORT_ESCORT -> new int[]{anchorX, anchorY};
            case LEAD_SCREEN -> new int[]{
                    anchorX - forward[0] * roleOrdinal
                            * ECHELON_DEPTH_SPACING,
                    anchorY - forward[1] * roleOrdinal
                            * ECHELON_DEPTH_SPACING};
            case LEFT_SCREEN -> new int[]{
                    anchorX + leftX * LATERAL_SLOT_SPACING
                            - forward[0] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING,
                    anchorY + leftY * LATERAL_SLOT_SPACING
                            - forward[1] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING};
            case RIGHT_SCREEN -> new int[]{
                    anchorX - leftX * LATERAL_SLOT_SPACING
                            - forward[0] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING,
                    anchorY - leftY * LATERAL_SLOT_SPACING
                            - forward[1] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING};
            case REAR_SCREEN -> new int[]{
                    anchorX - forward[0] * ECHELON_DEPTH_SPACING
                            * (roleOrdinal + 2),
                    anchorY - forward[1] * ECHELON_DEPTH_SPACING
                            * (roleOrdinal + 2)};
            default -> new int[]{anchorX, anchorY};
        };
    }

    private int roleOrdinal(CommandSquadState squad,
                            List<CommandSquadState> pool, Role role) {
        int ordinal = 0;
        for (CommandSquadState candidate : pool) {
            if (candidate.squadId() >= squad.squadId()) break;
            if (roles.get(candidate.squadId()) == role) ordinal++;
        }
        return ordinal;
    }

    private static int[] direction(int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (Math.abs(dx) >= Math.abs(dy) && dx != 0) {
            return new int[]{Integer.signum(dx), 0};
        }
        if (dy != 0) return new int[]{0, Integer.signum(dy)};
        return new int[]{1, 0};
    }

    private static int[] nearestReachable(
            CommandSquadState squad, int desiredX, int desiredY,
            int fallbackX, int fallbackY, CommandTopology topology,
            List<int[]> claimed) {
        int[] result = search(squad, desiredX, desiredY, topology, claimed);
        if (result != null) return result;
        result = search(squad, fallbackX, fallbackY, topology, claimed);
        if (result != null) return result;
        return search(squad, fallbackX, fallbackY, topology, List.of());
    }

    private static int[] search(CommandSquadState squad, int centerX,
                                int centerY, CommandTopology topology,
                                List<int[]> claimed) {
        for (int distance = 0; distance <= SLOT_SEARCH_RADIUS; distance++) {
            for (int y = centerY - distance; y <= centerY + distance; y++) {
                for (int x = centerX - distance; x <= centerX + distance; x++) {
                    if (Math.abs(x - centerX) + Math.abs(y - centerY)
                            != distance || !topology.isWalkable(x, y)
                            || !farEnough(x, y, claimed)) continue;
                    if (topology.reachable(squad.anchorCellX(),
                            squad.anchorCellY(), x, y)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean farEnough(int x, int y, List<int[]> claimed) {
        int minimum = MIN_SLOT_SEPARATION * MIN_SLOT_SEPARATION;
        for (int[] cell : claimed) {
            int dx = x - cell[0];
            int dy = y - cell[1];
            if (dx * dx + dy * dy < minimum) return false;
        }
        return true;
    }

    private static SquadIntent externalIntent(CommandSquadState squad,
                                               AuthoredDuty duty) {
        Role role = duty == AuthoredDuty.SHELTER_GUARD
                ? Role.SHELTER_GUARD : duty == AuthoredDuty.PICKUP_GUARD
                ? Role.PICKUP_GUARD : Role.EXTERNAL;
        String reason = duty == AuthoredDuty.SHELTER_GUARD
                ? "AUTHORED_SHELTER_GUARD" : duty == AuthoredDuty.PICKUP_GUARD
                ? "AUTHORED_PICKUP_GUARD" : "EXTERNAL_OWNERSHIP_PRESERVED";
        ObjectiveAssignment assignment = squad.directive() != null
                ? squad.directive().assignment() : squad.assignment();
        return new SquadIntent(squad.squadId(), role, reason,
                assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetCellX() : -1,
                assignment != null ? assignment.targetCellY() : -1,
                squad.localContact(), false);
    }

    private static String reason(Role role, boolean atShelter,
                                 boolean slowed) {
        String base = atShelter ? "SHELTER_RELIEF"
                : role == Role.COHORT_ESCORT ? "COHORT_ESCORT"
                : "MOVING_" + role.name();
        return slowed ? base + "_LOCAL_PRESSURE_HOLD" : base;
    }

    private static boolean terminal(ExtractionObjectiveFacts cohort) {
        return cohort.complete() || cohort.failed();
    }

    private static int validCell(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private static CommandProposal releaseOrRetain(CommandSquadState squad,
                                                    String reason) {
        return squad.directive() != null
                && "rescue-corridor".equals(squad.directive().issuer())
                ? CommandProposal.release(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason,
                CommandStabilityBreak.OBJECTIVE_COMPLETED)
                : CommandProposal.retain(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason);
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null
                || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private static String summary(ExtractionObjectiveFacts cohort) {
        return cohort.payloadName() + "=" + cohort.phase().toLowerCase()
                + " active=" + cohort.activeElements()
                + " boarded=" + cohort.boardedElements()
                + " lost=" + cohort.lostElements();
    }
}
