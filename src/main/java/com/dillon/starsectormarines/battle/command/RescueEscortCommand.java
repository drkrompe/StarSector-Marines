package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine commander for civilian rescue: rally every live squad on the bunker
 * entrance, then continuously retarget the assignment to the moving cohort.
 */
public final class RescueEscortCommand implements MissionCommand {

    /** Keeps the firing line ahead of the cohort instead of holding behind it. */
    public static final int ADVANCE_SCREEN_CELLS = 5;
    /** Engaged columns ratchet their screen ahead by this many route cells. */
    public static final int ENGAGED_BOUND_CELLS = 2;
    /** Sim ticks between forced bounds: five seconds at the fixed 30 Hz rate. */
    public static final int ENGAGED_BOUND_TICKS = 150;

    private final CivilianEvacuationPlacement placement;
    private int[] evacuationRoute = GridPathfinder.EMPTY_PATH;
    private int cohortRouteCell;
    private int screenRouteCell = -1;
    private int nextEngagedBoundTick = -1;

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
        int[] target = sim.isCivilianEvacuationTriggered()
                ? forwardEscortTarget(sim)
                : new int[]{placement.shelterApproachX,
                placement.shelterApproachY};
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;
            if (squad.rescuePickupGuard) {
                assignEscort(squad, placement.liftX, placement.liftY);
                continue;
            }
            if (target == null) {
                squad.assignedObjective = null;
                continue;
            }
            assignEscort(squad, target[0], target[1]);
        }
    }

    /**
     * Projects the cohort's central representative forward along its actual
     * route. The escort action's broad rally radius can then absorb a large
     * force without allowing every squad to settle behind civilians whose own
     * forward leash is deliberately much shorter.
     */
    private int[] forwardEscortTarget(BattleView sim) {
        int[] center = activeCohortCenter(sim);
        if (center == null) return null;
        if (Paths.isEmpty(evacuationRoute)) {
            evacuationRoute = GridPathfinder.findPath(sim.getGrid(),
                    center[0], center[1], placement.liftX, placement.liftY);
            if (Paths.isEmpty(evacuationRoute)) return center;
        }

        cohortRouteCell = Math.max(cohortRouteCell,
                nearestRouteCell(center[0], center[1]));
        boolean engaged = mobileEscortEngaged(sim);
        if (screenRouteCell < 0) {
            screenRouteCell = cohortRouteCell + (engaged
                    ? ENGAGED_BOUND_CELLS : ADVANCE_SCREEN_CELLS);
            if (engaged) {
                nextEngagedBoundTick = sim.getSimTickIndex()
                        + ENGAGED_BOUND_TICKS;
            }
        } else if (engaged) {
            if (nextEngagedBoundTick < 0) {
                nextEngagedBoundTick = sim.getSimTickIndex()
                        + ENGAGED_BOUND_TICKS;
            }
            while (sim.getSimTickIndex() >= nextEngagedBoundTick) {
                screenRouteCell += ENGAGED_BOUND_CELLS;
                nextEngagedBoundTick += ENGAGED_BOUND_TICKS;
            }
        } else {
            screenRouteCell = Math.max(screenRouteCell,
                    cohortRouteCell + ADVANCE_SCREEN_CELLS);
            nextEngagedBoundTick = -1;
        }
        screenRouteCell = Math.min(screenRouteCell,
                Paths.cellCount(evacuationRoute) - 1);
        return new int[]{Paths.cellX(evacuationRoute, screenRouteCell),
                Paths.cellY(evacuationRoute, screenRouteCell)};
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

    private static boolean mobileEscortEngaged(BattleView sim) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.MARINE && squad.aliveMembers > 0
                    && !squad.rescuePickupGuard
                    && squad.alertLevel == SquadAlertLevel.ENGAGED) {
                return true;
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
