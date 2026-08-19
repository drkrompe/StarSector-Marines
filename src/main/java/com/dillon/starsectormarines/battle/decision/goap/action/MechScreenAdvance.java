package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.MechScreenMode;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit-anchored movement doctrine used by {@link EnterZone}. Infantry follow
 * behind a compatible friendly assault mech, then spread into a short,
 * two-sided firing fan when that mech has a visible contact.
 */
final class MechScreenAdvance {

    static final float MATCHED_MAX_DISTANCE = 18f;
    static final float FALLBACK_MAX_DISTANCE = 12f;
    static final float MATCHED_AXIS_HALF_WIDTH = 6f;
    static final float FALLBACK_AXIS_HALF_WIDTH = 4f;
    static final float SCREEN_LEASH = 5f;
    private static final float FOLLOW_BASE_DEPTH = 2f;
    private static final float FAN_DEPTH = 0.75f;
    private static final float FAN_BASE_WIDTH = 1.75f;
    private static final int CELL_SEARCH_RADIUS = 3;

    private MechScreenAdvance() {}

    static boolean execute(long member, Squad squad, int targetZoneId,
                           int destX, int destY, BattleControl sim) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step == null || !(step.action instanceof EnterZone)) {
            clear(squad);
            return false;
        }

        synchronized (squad.lock) {
            if (squad.mechScreenTick != sim.getSimTickIndex()) {
                rebuild(squad, step.allAssignedMembers(), targetZoneId, destX, destY, sim);
            }
        }

        long mech = sim.resolveUnit(squad.screeningMechId);
        int memberIndex = memberIndex(squad.mechScreenMemberIds, member);
        if (mech == 0L || memberIndex < 0
                || memberIndex >= squad.mechScreenTargetXs.length
                || memberIndex >= squad.mechScreenTargetYs.length) {
            clear(squad);
            return false;
        }

        int x = squad.mechScreenTargetXs[memberIndex];
        int y = squad.mechScreenTargetYs[memberIndex];
        if (sim.movement().atCell(member, x, y)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            fireFromFan(member, squad, sim);
            return true;
        }

        int[] path = sim.world().path(member);
        boolean destinationShifted = Paths.isEmpty(path)
                || Paths.destX(path) != x
                || Paths.destY(path) != y;
        if (sim.movement().mayRepath(member) && destinationShifted) {
            int[] replacement = GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    x, y, sim.getOccupancyMap());
            if (Paths.isEmpty(replacement)) sim.clearPath(member);
            else sim.setPath(member, replacement);
        }
        if (sim.world().pathIdx(member) < Paths.cellCount(sim.world().path(member))) {
            sim.advanceMovement(member);
        }
        return true;
    }

    static long selectScreeningMech(Squad squad, int targetZoneId,
                                    int destX, int destY, BattleView sim) {
        long best = 0L;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            if (sim.identity().faction(candidate) != squad.faction) continue;
            MechLoadoutComponent loadout = sim.world().mechLoadout(candidate);
            if (loadout == null || loadout.role != MechRole.ASSAULT) continue;
            Squad mechSquad = sim.squadOf(candidate);
            if (mechSquad == null || !mechSquad.isMechSquad()
                    || mechSquad.rescuePickupMech) continue;

            float dx = sim.world().x(candidate) - squad.centroidX;
            float dy = sim.world().y(candidate) - squad.centroidY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            boolean assignmentMatch = sameTargetZone(mechSquad.assignedObjective, targetZoneId);
            if (assignmentMatch) {
                if (distance > MATCHED_MAX_DISTANCE
                        || !onObjectiveAxis(squad, candidate, destX, destY,
                        MATCHED_MAX_DISTANCE, MATCHED_AXIS_HALF_WIDTH, sim)) continue;
            } else if (distance > FALLBACK_MAX_DISTANCE
                    || !onObjectiveAxis(squad, candidate, destX, destY,
                    FALLBACK_MAX_DISTANCE, FALLBACK_AXIS_HALF_WIDTH, sim)) {
                continue;
            }

            float score = distance + (assignmentMatch ? 0f : MATCHED_MAX_DISTANCE);
            if (score < bestScore || score == bestScore && candidate < best) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static void rebuild(Squad squad, List<Long> assignedMembers,
                                int targetZoneId, int destX, int destY,
                                BattleView sim) {
        squad.mechScreenTick = sim.getSimTickIndex();
        long mech = selectScreeningMech(squad, targetZoneId, destX, destY, sim);
        if (mech == 0L) {
            clearInsideLock(squad);
            squad.mechScreenTick = sim.getSimTickIndex();
            return;
        }

        List<Long> members = new ArrayList<>();
        for (long assigned : assignedMembers) {
            if (sim.resolveUnit(assigned) != 0L) members.add(assigned);
        }
        members.sort(Comparator.naturalOrder());
        if (members.isEmpty()) {
            clearInsideLock(squad);
            squad.mechScreenTick = sim.getSimTickIndex();
            return;
        }

        long mechTarget = liveHostileTarget(mech, squad, sim);
        boolean fan = mechTarget != 0L && canMechEngage(mech, mechTarget, sim);
        long axisTarget = mechTarget != 0L ? mechTarget : sim.resolveUnit(squad.advanceThreatId);
        float threatX;
        float threatY;
        if (axisTarget != 0L && sim.identity().faction(axisTarget) != squad.faction) {
            threatX = sim.world().x(axisTarget);
            threatY = sim.world().y(axisTarget);
        } else if (squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            threatX = squad.lastSeenEnemyX + 0.5f;
            threatY = squad.lastSeenEnemyY + 0.5f;
            axisTarget = 0L;
        } else {
            threatX = destX + 0.5f;
            threatY = destY + 0.5f;
            axisTarget = 0L;
        }

        float mechX = sim.world().x(mech);
        float mechY = sim.world().y(mech);
        float dirX = threatX - mechX;
        float dirY = threatY - mechY;
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (length < 1e-4f) {
            dirX = destX + 0.5f - mechX;
            dirY = destY + 0.5f - mechY;
            length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        }
        if (length < 1e-4f) {
            clearInsideLock(squad);
            squad.mechScreenTick = sim.getSimTickIndex();
            return;
        }
        dirX /= length;
        dirY /= length;
        float perpX = -dirY;
        float perpY = dirX;

        long[] memberIds = new long[members.size()];
        int[] targetXs = new int[members.size()];
        int[] targetYs = new int[members.size()];
        Set<Integer> reserved = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            long infantry = members.get(i);
            float depth;
            float lateral;
            int side = 0;
            if (fan) {
                side = (i & 1) == 0 ? -1 : 1;
                depth = FAN_DEPTH;
                lateral = side * (FAN_BASE_WIDTH + (i / 2) * 0.75f);
            } else {
                depth = FOLLOW_BASE_DEPTH + i / 3;
                lateral = followLateral(i);
            }
            float idealX = mechX - dirX * depth + perpX * lateral;
            float idealY = mechY - dirY * depth + perpY * lateral;
            int[] cell = findFormationCell(infantry, mechX, mechY,
                    dirX, dirY, perpX, perpY, idealX, idealY,
                    fan, side, reserved, sim);
            if (cell == null) {
                clearInsideLock(squad);
                squad.mechScreenTick = sim.getSimTickIndex();
                return;
            }
            memberIds[i] = infantry;
            targetXs[i] = cell[0];
            targetYs[i] = cell[1];
            reserved.add(sim.getGrid().index(cell[0], cell[1]));
        }

        squad.screeningMechId = mech;
        squad.mechScreenMode = fan ? MechScreenMode.FAN : MechScreenMode.FOLLOW;
        squad.mechScreenThreatId = axisTarget;
        squad.mechScreenMemberIds = memberIds;
        squad.mechScreenTargetXs = targetXs;
        squad.mechScreenTargetYs = targetYs;
    }

    private static int[] findFormationCell(long member, float mechX, float mechY,
                                           float dirX, float dirY,
                                           float perpX, float perpY,
                                           float idealX, float idealY,
                                           boolean fan, int side,
                                           Set<Integer> reserved, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int centerX = (int) Math.floor(idealX);
        int centerY = (int) Math.floor(idealY);
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int oy = -CELL_SEARCH_RADIUS; oy <= CELL_SEARCH_RADIUS; oy++) {
            for (int ox = -CELL_SEARCH_RADIUS; ox <= CELL_SEARCH_RADIUS; ox++) {
                int x = centerX + ox;
                int y = centerY + oy;
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)
                        || reserved.contains(grid.index(x, y))) continue;
                float relX = x + 0.5f - mechX;
                float relY = y + 0.5f - mechY;
                float forward = relX * dirX + relY * dirY;
                float lateral = relX * perpX + relY * perpY;
                float mechDistance = (float) Math.sqrt(relX * relX + relY * relY);
                if (mechDistance > SCREEN_LEASH) continue;
                if (!fan && forward > -0.25f) continue;
                if (fan && (forward > 0.75f || side * lateral < 0.5f)) continue;
                int[] path = GridPathfinder.findPath(grid,
                        sim.world().cellX(member), sim.world().cellY(member), x, y);
                if (Paths.isEmpty(path)) continue;
                float dx = x + 0.5f - idealX;
                float dy = y + 0.5f - idealY;
                float score = dx * dx + dy * dy;
                if (score < bestScore) {
                    best = new int[]{x, y};
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static void fireFromFan(long member, Squad squad, BattleControl sim) {
        if (squad.mechScreenMode != MechScreenMode.FAN) return;
        long target = sim.resolveUnit(squad.mechScreenThreatId);
        if (target == 0L || sim.identity().faction(target) == squad.faction) return;
        float dx = sim.world().x(target) - sim.world().x(member);
        float dy = sim.world().y(target) - sim.world().y(member);
        if (dx * dx + dy * dy > sim.world().attackRange(member)
                * sim.world().attackRange(member)) return;
        if (!sim.getGrid().hasLineOfSight(
                sim.world().cellX(member), sim.world().cellY(member),
                sim.world().cellX(target), sim.world().cellY(target))) return;
        sim.world().setTargetId(member, target);
        sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
    }

    private static long liveHostileTarget(long mech, Squad squad, BattleView sim) {
        long target = sim.resolveUnit(sim.targetOf(mech));
        return target != 0L && sim.identity().faction(target) != squad.faction
                ? target : 0L;
    }

    private static boolean sameTargetZone(ObjectiveAssignment assignment, int targetZoneId) {
        return assignment != null && targetZoneId >= 0
                && assignment.targetZoneId() == targetZoneId;
    }

    private static boolean onObjectiveAxis(Squad squad, long mech,
                                           int destX, int destY,
                                           float maxProgress, float halfWidth,
                                           BattleView sim) {
        float axisX = destX + 0.5f - squad.centroidX;
        float axisY = destY + 0.5f - squad.centroidY;
        float length = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (length < 1e-4f) return false;
        axisX /= length;
        axisY /= length;
        float relX = sim.world().x(mech) - squad.centroidX;
        float relY = sim.world().y(mech) - squad.centroidY;
        float progress = relX * axisX + relY * axisY;
        float lateral = Math.abs(relX * -axisY + relY * axisX);
        return progress >= -1.5f && progress <= maxProgress
                && lateral <= halfWidth;
    }

    private static boolean canMechEngage(long mech, long target, BattleView sim) {
        float dx = sim.world().x(target) - sim.world().x(mech);
        float dy = sim.world().y(target) - sim.world().y(mech);
        float range = sim.world().attackRange(mech);
        return dx * dx + dy * dy <= range * range
                && sim.getGrid().hasLineOfSight(
                sim.world().cellX(mech), sim.world().cellY(mech),
                sim.world().cellX(target), sim.world().cellY(target));
    }

    private static float followLateral(int index) {
        if (index == 0) return 0f;
        int magnitude = (index + 1) / 2;
        return (index & 1) == 1 ? magnitude : -magnitude;
    }

    private static int memberIndex(long[] members, long member) {
        for (int i = 0; i < members.length; i++) {
            if (members[i] == member) return i;
        }
        return -1;
    }

    private static void clear(Squad squad) {
        if (squad.screeningMechId == 0L && squad.mechScreenMode == MechScreenMode.NONE) return;
        squad.clearMechScreen();
    }

    private static void clearInsideLock(Squad squad) {
        squad.screeningMechId = 0L;
        squad.mechScreenMode = MechScreenMode.NONE;
        squad.mechScreenThreatId = 0L;
        squad.mechScreenMemberIds = new long[0];
        squad.mechScreenTargetXs = new int[0];
        squad.mechScreenTargetYs = new int[0];
    }
}
