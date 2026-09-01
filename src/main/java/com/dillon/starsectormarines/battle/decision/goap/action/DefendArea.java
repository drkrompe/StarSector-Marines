package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.infantry.ApproachBound;
import com.dillon.starsectormarines.battle.infantry.LaneSidestep;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;

import java.util.List;

/**
 * Holds a player-authored circle while reacting to the squad's own contact
 * picture. Members form a threat-facing line in quiet belief, improve into
 * directional cover when available, and engage only from firing positions
 * inside the ordered area.
 */
public final class DefendArea implements Action {

    private static final int PREPARED_POSITION_SEARCH_RADIUS = 5;
    private static final float PREPARED_LINE_REARWARD_OFFSET = 3f;
    private static final float PREPARED_LINE_SPACING = 2.5f;

    private final int centerX;
    private final int centerY;
    private final int radius;

    public DefendArea(int centerX, int centerY, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
    }

    @Override public String name() { return "DefendArea"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA
                || assignment.targetCellX() != centerX
                || assignment.targetCellY() != centerY
                || assignment.targetRadiusCells() != radius) {
            sim.clearPath(member);
            return ActionStatus.FAILURE;
        }

        long target = sim.targetOf(member);
        if (target == 0L
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }
        if (target != 0L) return engageInsideArea(member, target, sim);

        SquadContactPicture picture = squad.contactPicture;
        int[] destination = picture.hasContacts()
                && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0
                ? preparedPosition(member, picture.primaryCellX(),
                        picture.primaryCellY(), squad, sim)
                : quietPosition(member, squad, sim);
        moveToward(member, destination[0], destination[1], sim);
        return ActionStatus.RUNNING;
    }

    private ActionStatus engageInsideArea(long member, long target,
                                          BattleControl sim) {
        int[] firingPosition = sim.getTacticalScoring().findFiringPositionWithin(
                member, target, centerX, centerY, radius);
        if (firingPosition == null) {
            long alternative = sim.getTacticalScoring().findEngageableEnemyWithin(
                    member, centerX, centerY, radius);
            if (alternative != 0L && alternative != target) {
                sim.world().setTargetId(member, alternative);
                target = alternative;
                firingPosition = sim.getTacticalScoring().findFiringPositionWithin(
                        member, target, centerX, centerY, radius);
            }
        }
        if (firingPosition == null) {
            int[] prepared = preparedPosition(member,
                    sim.world().cellX(target), sim.world().cellY(target), null, sim);
            moveToward(member, prepared[0], prepared[1], sim);
            return ActionStatus.RUNNING;
        }

        float distance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        boolean canFireHere = inside(sim.world().cellX(member),
                sim.world().cellY(member))
                && distance <= sim.world().attackRange(member)
                && sim.getTacticalScoring().hasClearShot(member, target);

        if (canFireHere && sim.movement().atCell(member,
                firingPosition[0], firingPosition[1])) {
            // Planting is the ordinary answer, but a step-aside already under
            // way is walked instead of dropped: the marine is moving out of a
            // squadmate's lane, not off his firing position, and he shoots on
            // the same tick either way.
            if (LaneSidestep.isStepping(member, sim)) {
                sim.advanceMovement(member);
            } else {
                PatrolMotion.hold(member, sim);
            }
            sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
            return ActionStatus.RUNNING;
        }

        moveToward(member, firingPosition[0], firingPosition[1], sim, true);
        return ActionStatus.RUNNING;
    }

    private int[] preparedPosition(long member, int threatX, int threatY,
                                   Squad squad, BattleView sim) {
        int ordinal = memberOrdinal(member, squad, sim);
        int count = squad != null ? Math.max(1, sim.squadMemberCount(squad.id)) : 1;
        float axisX = threatX - centerX;
        float axisY = threatY - centerY;
        float length = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (length < 0.001f) return quietPosition(member, squad, sim);
        axisX /= length;
        axisY /= length;
        float lateral = (ordinal - (count - 1) * 0.5f) * PREPARED_LINE_SPACING;
        int desiredX = Math.round(centerX - axisX * PREPARED_LINE_REARWARD_OFFSET
                - axisY * lateral);
        int desiredY = Math.round(centerY - axisY * PREPARED_LINE_REARWARD_OFFSET
                + axisX * lateral);
        int[] covered = sim.getTacticalScoring().bestCoverCell(
                threatX, threatY, desiredX, desiredY,
                PREPARED_POSITION_SEARCH_RADIUS);
        if (covered != null && inside(covered[0], covered[1])) return covered;
        return nearestWalkableInside(desiredX, desiredY, sim);
    }

    private int[] quietPosition(long member, Squad squad, BattleView sim) {
        int ordinal = memberOrdinal(member, squad, sim);
        int count = squad != null ? Math.max(1, sim.squadMemberCount(squad.id)) : 1;
        int columns = Math.min(4, count);
        int x = centerX + ordinal % columns - (columns - 1) / 2;
        int y = centerY + ordinal / columns;
        return nearestWalkableInside(x, y, sim);
    }

    private int[] nearestWalkableInside(int wantedX, int wantedY, BattleView sim) {
        if (inside(wantedX, wantedY) && sim.getGrid().inBounds(wantedX, wantedY)
                && sim.getGrid().isWalkable(wantedX, wantedY)) {
            return new int[]{wantedX, wantedY};
        }
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (!inside(x, y) || !sim.getGrid().inBounds(x, y)
                        || !sim.getGrid().isWalkable(x, y)) continue;
                int dx = x - wantedX;
                int dy = y - wantedY;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        return best != null ? best : new int[]{centerX, centerY};
    }

    private void moveToward(long member, int x, int y, BattleControl sim) {
        moveToward(member, x, y, sim, false);
    }

    /**
     * {@code boundDetour} refuses a route out of proportion to the straight
     * line it stands in for — see {@link ApproachBound}. Only the firing
     * position passes {@code true}: it is a cell chosen as an improvement
     * inside the area, so walking round the outside of a building to reach it
     * spends the defence of the area on getting somewhere marginally better.
     * A prepared or quiet position is where the member belongs, and it goes
     * there whichever way the ground allows.
     */
    private void moveToward(long member, int x, int y, BattleControl sim,
                            boolean boundDetour) {
        // Same exception, on the branch that re-paths rather than plants: a
        // path whose destination is not this member's own firing position is
        // normally stale and cleared, and a step-aside's is exactly that.
        if (LaneSidestep.isStepping(member, sim)) {
            sim.advanceMovement(member);
            return;
        }
        if (sim.movement().atCell(member, x, y)) {
            PatrolMotion.hold(member, sim);
            return;
        }
        int[] path = sim.world().path(member);
        if (!Paths.isEmpty(path)
                && (Paths.destX(path) != x || Paths.destY(path) != y)) {
            sim.clearPath(member);
            path = sim.world().path(member);
        }
        if (sim.movement().mayRepath(member)
                && sim.world().pathIdx(member) >= Paths.cellCount(path)) {
            int fromX = sim.world().cellX(member);
            int fromY = sim.world().cellY(member);
            int[] found = GridPathfinder.findPath(sim.getGrid(), fromX, fromY,
                    x, y, sim.getOccupancyMap());
            if (!ApproachBound.worthWalkingTo(fromX, fromY, x, y, found, boundDetour)) {
                PatrolMotion.hold(member, sim);
                return;
            }
            sim.setPath(member, found);
        }
        sim.advanceMovement(member);
    }

    private boolean inside(int x, int y) {
        int dx = x - centerX;
        int dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static int memberOrdinal(long member, Squad squad, BattleView sim) {
        if (squad == null) return 0;
        for (int i = 0, count = sim.squadMemberCount(squad.id); i < count; i++) {
            if (sim.squadMemberAt(squad.id, i) == member) return i;
        }
        return 0;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{centerX, centerY});
    }

    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int radius() { return radius; }
}
