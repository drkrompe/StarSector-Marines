package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marine commander for civilian rescue: rally the mobile force on the bunker
 * entrance, then continuously retarget it to the moving cohort. Mobile squads
 * occupy distinct line-and-depth slots around that moving screen instead of
 * converging on one shared stop circle. Each squad slows independently while
 * locally pressured; pickup guards retain their authored perimeter posts.
 */
public final class RescueEscortCommand implements MissionCommand {

    /** Keeps the firing line ahead of the cohort instead of holding behind it. */
    public static final int ADVANCE_SCREEN_CELLS = 5;
    /** Locally pressured squads ratchet their screen ahead by this many route cells. */
    public static final int ENGAGED_BOUND_CELLS = 2;
    /** Sim ticks between forced bounds: five seconds at the fixed 30 Hz rate. */
    public static final int ENGAGED_BOUND_TICKS = 150;
    /** An engaged alert only slows its squad while a live attacker is locally relevant. */
    public static final int ENGAGED_SLOW_RADIUS = 12;
    /** Four support squads fit across each echelon without occupying the lead lane. */
    static final int SQUADS_PER_ECHELON = 4;
    /** Separation between support squad rally anchors across the line. */
    static final int LATERAL_SLOT_SPACING = 5;
    /** Each additional echelon sits this far behind the lead screen. */
    static final int ECHELON_DEPTH_SPACING = 3;
    /** Terrain-repaired anchors must retain useful squad-to-squad separation. */
    static final int MIN_SLOT_SEPARATION = 4;
    /** Local terrain repair around an ideal formation anchor. */
    private static final int SLOT_SEARCH_RADIUS = 4;

    private final CivilianEvacuationPlacement placement;
    private int[] evacuationRoute = GridPathfinder.EMPTY_PATH;
    private int cohortRouteCell;
    private final Map<Integer, SquadAdvanceState> squadAdvance =
            new HashMap<>();

    private static final class SquadAdvanceState {
        private int screenRouteCell = -1;
        private int nextEngagedBoundTick = -1;
    }

    private record EscortScreen(int[] target, int routeCell) {}

    public RescueEscortCommand(CivilianEvacuationPlacement placement) {
        if (placement == null) {
            throw new IllegalArgumentException("placement is required");
        }
        this.placement = placement;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim) {
        List<Squad> mobile = mobileSquads(sim);
        discardMissingSquadState(mobile);
        boolean evacuationTriggered = sim.isCivilianEvacuationTriggered();
        EscortScreen screen = evacuationTriggered
                ? forwardEscortScreen(sim)
                : new EscortScreen(new int[]{placement.shelterApproachX,
                placement.shelterApproachY}, -1);
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;
            if (squad.rescuePickupGuard) {
                if (squad.assignedObjective == null) {
                    assignEscort(squad, placement.liftX, placement.liftY);
                }
            }
        }
        if (screen == null) {
            for (Squad squad : mobile) squad.assignedObjective = null;
            return;
        }

        int[] forward = escortDirection(screen.target(), screen.routeCell(),
                evacuationTriggered, mobile);
        List<int[]> claimed = new ArrayList<>();
        for (int slot = 0; slot < mobile.size(); slot++) {
            Squad squad = mobile.get(slot);
            int[] squadTarget = screen.routeCell() >= 0
                    ? squadEscortTarget(squad, sim) : screen.target();
            int[] rally = formationRally(squadTarget, forward, slot,
                    squad, claimed, sim);
            assignEscort(squad, rally[0], rally[1]);
        }
    }

    private static List<Squad> mobileSquads(BattleView sim) {
        List<Squad> result = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.MARINE && squad.aliveMembers > 0
                    && !squad.rescuePickupGuard) {
                result.add(squad);
            }
        }
        result.sort(Comparator.comparingInt(squad -> squad.id));
        return result;
    }

    private void discardMissingSquadState(List<Squad> mobile) {
        Set<Integer> active = new HashSet<>();
        for (Squad squad : mobile) active.add(squad.id);
        squadAdvance.keySet().removeIf(id -> !active.contains(id));
    }

    private int[] escortDirection(int[] target, int targetRouteCell,
                                   boolean evacuationTriggered,
                                   List<Squad> mobile) {
        if (evacuationTriggered && !Paths.isEmpty(evacuationRoute)) {
            int from = Math.max(0, targetRouteCell - 1);
            int dx = target[0] - Paths.cellX(evacuationRoute, from);
            int dy = target[1] - Paths.cellY(evacuationRoute, from);
            if (dx != 0 || dy != 0) return cardinalDirection(dx, dy);
        }
        float centerX = 0f;
        float centerY = 0f;
        for (Squad squad : mobile) {
            centerX += squad.centroidX;
            centerY += squad.centroidY;
        }
        if (!mobile.isEmpty()) {
            centerX /= mobile.size();
            centerY /= mobile.size();
        }
        return cardinalDirection(
                target[0] - Math.round(centerX),
                target[1] - Math.round(centerY));
    }

    private static int[] cardinalDirection(int dx, int dy) {
        if (Math.abs(dx) >= Math.abs(dy) && dx != 0) {
            return new int[]{Integer.signum(dx), 0};
        }
        if (dy != 0) return new int[]{0, Integer.signum(dy)};
        return new int[]{1, 0};
    }

    private static int[] formationRally(int[] target, int[] forward,
                                         int slot, Squad squad,
                                         List<int[]> claimed, BattleView sim) {
        int idealX = target[0];
        int idealY = target[1];
        if (slot > 0) {
            int supportSlot = slot - 1;
            int echelon = supportSlot / SQUADS_PER_ECHELON + 1;
            int lateralPosition = supportSlot % SQUADS_PER_ECHELON;
            int lateralSteps = switch (lateralPosition) {
                case 0 -> -1;
                case 1 -> 1;
                case 2 -> -2;
                default -> 2;
            };
            int perpendicularX = -forward[1];
            int perpendicularY = forward[0];
            idealX += perpendicularX * lateralSteps
                    * LATERAL_SLOT_SPACING
                    - forward[0] * echelon * ECHELON_DEPTH_SPACING;
            idealY += perpendicularY * lateralSteps
                    * LATERAL_SLOT_SPACING
                    - forward[1] * echelon * ECHELON_DEPTH_SPACING;
        }

        long leader = sim.resolveUnit(squad.leaderId);
        int startX = leader != 0L
                ? sim.world().cellX(leader) : Math.round(squad.centroidX);
        int startY = leader != 0L
                ? sim.world().cellY(leader) : Math.round(squad.centroidY);
        int bestX = -1;
        int bestY = -1;
        List<int[]> candidates = new ArrayList<>();
        for (int y = idealY - SLOT_SEARCH_RADIUS;
             y <= idealY + SLOT_SEARCH_RADIUS; y++) {
            for (int x = idealX - SLOT_SEARCH_RADIUS;
                 x <= idealX + SLOT_SEARCH_RADIUS; x++) {
                if (!sim.getGrid().inBounds(x, y)
                        || !sim.getGrid().isWalkable(x, y)
                        || !farEnoughFromClaimed(x, y, claimed)) continue;
                int dx = x - idealX;
                int dy = y - idealY;
                int distance = dx * dx + dy * dy;
                candidates.add(new int[]{x, y, distance});
            }
        }
        candidates.sort(Comparator
                .comparingInt((int[] cell) -> cell[2])
                .thenComparingInt(cell -> cell[1])
                .thenComparingInt(cell -> cell[0]));
        for (int[] candidate : candidates) {
            int x = candidate[0];
            int y = candidate[1];
            if ((x == startX && y == startY)
                    || !Paths.isEmpty(GridPathfinder.findPath(
                    sim.getGrid(), startX, startY, x, y))) {
                bestX = x;
                bestY = y;
                break;
            }
        }
        if (bestX < 0) {
            bestX = target[0];
            bestY = target[1];
        }
        claimed.add(new int[]{bestX, bestY});
        return new int[]{bestX, bestY};
    }

    private static boolean farEnoughFromClaimed(int x, int y,
                                                 List<int[]> claimed) {
        int minimumDistanceSquared = MIN_SLOT_SEPARATION
                * MIN_SLOT_SEPARATION;
        for (int[] cell : claimed) {
            int dx = x - cell[0];
            int dy = y - cell[1];
            if (dx * dx + dy * dy < minimumDistanceSquared) return false;
        }
        return true;
    }

    /**
     * Projects the cohort's central representative forward along its actual
     * route. Squad-specific slots then distribute the force around that screen
     * without allowing every squad to settle behind civilians whose own
     * forward leash is deliberately much shorter.
     */
    private EscortScreen forwardEscortScreen(BattleView sim) {
        int[] center = activeCohortCenter(sim);
        if (center == null) return null;
        if (Paths.isEmpty(evacuationRoute)) {
            evacuationRoute = GridPathfinder.findPath(sim.getGrid(),
                    center[0], center[1], placement.liftX, placement.liftY);
            if (Paths.isEmpty(evacuationRoute)) {
                return new EscortScreen(center, -1);
            }
        }

        cohortRouteCell = Math.max(cohortRouteCell,
                nearestRouteCell(center[0], center[1]));
        int normalScreenRouteCell = Math.min(
                cohortRouteCell + ADVANCE_SCREEN_CELLS,
                Paths.cellCount(evacuationRoute) - 1);
        return new EscortScreen(new int[]{
                Paths.cellX(evacuationRoute, normalScreenRouteCell),
                Paths.cellY(evacuationRoute, normalScreenRouteCell)},
                normalScreenRouteCell);
    }

    private int[] squadEscortTarget(Squad squad, BattleView sim) {
        SquadAdvanceState state = squadAdvance.computeIfAbsent(squad.id,
                ignored -> new SquadAdvanceState());
        boolean engaged = squadUnderPressure(squad, sim);
        if (state.screenRouteCell < 0) {
            state.screenRouteCell = cohortRouteCell + (engaged
                    ? ENGAGED_BOUND_CELLS : ADVANCE_SCREEN_CELLS);
            if (engaged) {
                state.nextEngagedBoundTick = sim.getSimTickIndex()
                        + ENGAGED_BOUND_TICKS;
            }
        } else if (engaged) {
            if (state.nextEngagedBoundTick < 0) {
                state.nextEngagedBoundTick = sim.getSimTickIndex()
                        + ENGAGED_BOUND_TICKS;
            }
            while (sim.getSimTickIndex() >= state.nextEngagedBoundTick) {
                state.screenRouteCell += ENGAGED_BOUND_CELLS;
                state.nextEngagedBoundTick += ENGAGED_BOUND_TICKS;
            }
        } else {
            state.screenRouteCell = Math.max(state.screenRouteCell,
                    cohortRouteCell + ADVANCE_SCREEN_CELLS);
            state.nextEngagedBoundTick = -1;
        }
        state.screenRouteCell = Math.min(state.screenRouteCell,
                Paths.cellCount(evacuationRoute) - 1);
        return new int[]{Paths.cellX(evacuationRoute, state.screenRouteCell),
                Paths.cellY(evacuationRoute, state.screenRouteCell)};
    }

    private int nearestRouteCell(int x, int y) {
        int best = cohortRouteCell;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = cohortRouteCell, n = Paths.cellCount(evacuationRoute);
             i < n; i++) {
            int dx = Paths.cellX(evacuationRoute, i) - x;
            int dy = Paths.cellY(evacuationRoute, i) - y;
            int distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean squadUnderPressure(Squad squad, BattleView sim) {
        if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0
                || squad.rescuePickupGuard
                || squad.alertLevel != SquadAlertLevel.ENGAGED) return false;
        LongBucket nearby = new LongBucket();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long member = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(member)
                    || sim.squad().squadId(member) != squad.id) {
                continue;
            }
            sim.getUnitIndex().gather(sim.world().x(member),
                    sim.world().y(member), ENGAGED_SLOW_RADIUS, nearby);
            for (int k = 0, count = nearby.size; k < count; k++) {
                if (sim.identity().faction(nearby.ids[k])
                        == Faction.DEFENDER) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assignEscort(Squad squad, int x, int y) {
        ObjectiveAssignment current = squad.assignedObjective;
        if (current == null || current.kind() != AssignmentKind.ESCORT
                || current.targetCellX() != x
                || current.targetCellY() != y) {
            squad.assignedObjective = ObjectiveAssignment.escort(
                    squad.id, x, y);
        }
    }

    /** Picks the active representative nearest the cohort centroid. */
    private static int[] activeCohortCenter(BattleView sim) {
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        int sumX = 0;
        int sumY = 0;
        int count = 0;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(id) == 0L) continue;
            sumX += sim.world().cellX(id);
            sumY += sim.world().cellY(id);
            count++;
        }
        if (count == 0) return null;
        float centerX = (float) sumX / count;
        float centerY = (float) sumY / count;
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(id) == 0L) continue;
            float dx = sim.world().cellX(id) - centerX;
            float dy = sim.world().cellY(id) - centerY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && id < best)) {
                best = id;
                bestDistance = distance;
            }
        }
        return new int[]{sim.world().cellX(best), sim.world().cellY(best)};
    }
}
