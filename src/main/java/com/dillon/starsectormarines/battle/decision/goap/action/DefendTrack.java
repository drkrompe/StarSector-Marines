package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.AsyncDefendTrackRoutes;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/** Moves a squad to its command-authored track rally and holds there. */
public final class DefendTrack implements Action {
    private final AssignmentKind assignmentKind;
    private final int targetX;
    private final int targetY;

    public DefendTrack(int targetX, int targetY) {
        this(AssignmentKind.DEFEND_TRACK, targetX, targetY);
    }

    public DefendTrack(AssignmentKind assignmentKind, int targetX, int targetY) {
        if (assignmentKind != AssignmentKind.DEFEND_TRACK
                && assignmentKind != AssignmentKind.ADVANCE_TRACK
                && assignmentKind != AssignmentKind.DEFEND_SITE
                && assignmentKind != AssignmentKind.DEFEND_AREA
                && assignmentKind != AssignmentKind.RUSH_OBJECTIVE
                && assignmentKind != AssignmentKind.WITHDRAW) {
            throw new IllegalArgumentException("exact-cell command kind required");
        }
        this.assignmentKind = assignmentKind;
        this.targetX = targetX;
        this.targetY = targetY;
    }

    @Override public String name() {
        return switch (assignmentKind) {
            case ADVANCE_TRACK -> "AdvanceTrack";
            case DEFEND_SITE -> "DefendSite";
            case DEFEND_AREA -> "DefendArea";
            case RUSH_OBJECTIVE -> "ServiceObjective";
            case WITHDRAW -> "Withdraw";
            default -> "DefendTrack";
        };
    }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        AsyncDefendTrackRoutes async = assignmentKind == AssignmentKind.DEFEND_TRACK
                ? sim.asyncDefendTrackRoutes() : null;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != assignmentKind
                || assignment.targetCellX() != targetX || assignment.targetCellY() != targetY) {
            if (async != null) async.cancel(member);
            sim.clearPath(member);
            return ActionStatus.FAILURE;
        }
        if (WorldStateBuilder.hasActionableContact(squad, sim)) {
            if (async != null) async.cancel(member);
            sim.clearPath(member);
            return ActionStatus.FAILURE;
        }
        int moveX = targetX;
        int moveY = targetY;
        AudibleBearing bearing = squad.audibleBearing();
        if (bearing != null) { moveX = bearing.cellX(); moveY = bearing.cellY(); }
        // A local sound bearing is a tactical interrupt, not slow strategic
        // rally travel; it keeps the existing immediate route behavior.
        if (bearing != null && async != null) async.cancel(member);
        int anchorX = moveX;
        int anchorY = moveY;
        int[] formationCell = formationCell(member, squad, moveX, moveY, sim);
        moveX = formationCell[0];
        moveY = formationCell[1];
        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        boolean anchorFallback = !Paths.isEmpty(path)
                && (moveX != anchorX || moveY != anchorY)
                && Paths.destX(path) == anchorX && Paths.destY(path) == anchorY
                && !sim.getGrid().arePathConnected(
                        sim.world().cellX(member), sim.world().cellY(member),
                        moveX, moveY, GridPathfinder.USE_CARDINAL_NAVIGATION);
        if (!Paths.isEmpty(path) && !anchorFallback
                && (Paths.destX(path) != moveX || Paths.destY(path) != moveY)) {
            sim.clearPath(member);
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        int destinationX = anchorFallback ? anchorX : moveX;
        int destinationY = anchorFallback ? anchorY : moveY;
        if (sim.movement().mayRepath(member) && pathIdx >= Paths.cellCount(path)
                && !sim.movement().atCell(member, destinationX, destinationY)) {
            int[] next;
            if (async != null && bearing == null) {
                AsyncDefendTrackRoutes.Request request =
                        new AsyncDefendTrackRoutes.Request(member, squad.id,
                                squad.routingEpoch, this, targetX, targetY,
                                sim.world().cellX(member), sim.world().cellY(member),
                                moveX, moveY, anchorX, anchorY,
                                GridPathfinder.USE_CARDINAL_NAVIGATION);
                AsyncDefendTrackRoutes.Result result = async.pollOrSubmit(request,
                        sim.getSimTickIndex(), sim.getGrid(), sim.getOccupancyMap());
                if (!result.ready()) {
                    // The mover's velocity was zeroed at tick start. An
                    // exhausted old path needs one clear, not a setPath write
                    // on every waiting tick.
                    if (!Paths.isEmpty(path)) sim.clearPath(member);
                    return ActionStatus.RUNNING;
                }
                next = result.path();
            } else {
                next = GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(member), sim.world().cellY(member),
                        moveX, moveY, sim.getOccupancyMap());
                if (Paths.isEmpty(next) && (moveX != anchorX || moveY != anchorY)) {
                    next = GridPathfinder.findPath(sim.getGrid(),
                            sim.world().cellX(member), sim.world().cellY(member),
                            anchorX, anchorY, sim.getOccupancyMap());
                }
            }
            sim.setPath(member, next);
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        } else if (async != null) {
            // An external path or an arrival made an outstanding search moot.
            async.cancel(member);
        }
        if (pathIdx < Paths.cellCount(path)) sim.advanceMovement(member);
        else PatrolMotion.hold(member, sim);
        return ActionStatus.RUNNING;
    }

    @Override public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{targetX, targetY});
    }
    public AssignmentKind assignmentKind() { return assignmentKind; }
    public int targetX() { return targetX; }
    public int targetY() { return targetY; }

    /** Small deterministic fireteam footprint around the coarse squad rally. */
    private static int[] formationCell(long member, Squad squad,
                                       int anchorX, int anchorY,
                                       BattleView sim) {
        int count = sim.squadMemberCount(squad.id);
        int ordinal = 0;
        for (int i = 0; i < count; i++) {
            if (sim.squadMemberAt(squad.id, i) == member) {
                ordinal = i;
                break;
            }
        }
        int teamCount = Math.max(1, (count + Squad.FIRE_TEAM_SIZE - 1)
                / Squad.FIRE_TEAM_SIZE);
        int team = ordinal / Squad.FIRE_TEAM_SIZE;
        int seat = ordinal % Squad.FIRE_TEAM_SIZE;
        int teamOffsetX = team * 4 - (teamCount - 1) * 2;
        int[] seatX = {0, -1, 1, 0};
        int[] seatY = {0, 1, 1, 2};
        int x = anchorX + teamOffsetX + seatX[seat];
        int y = anchorY + seatY[seat];
        if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) {
            return new int[]{anchorX, anchorY};
        }
        return new int[]{x, y};
    }
}
