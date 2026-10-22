package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.nav.SwarmFlowFields;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;

/**
 * How a swarm body moves: the one movement call every swarm strain shares.
 *
 * <p>Strategic movement — toward a goal the body is not yet close to — walks
 * downhill on the goal's shared flow field ({@link SwarmFlowFields}), so a
 * swarm of any size costs one integration per goal refresh plus a few
 * neighbour reads per body. Local pursuit — a target within
 * {@link #LOCAL_PURSUIT_RANGE} — keeps the body's own short path, because
 * close in a body is chasing one particular victim rather than the nearest.
 *
 * <p>A pack member moves with its pack first ({@link SwarmPackMovement}):
 * the pack runs its anchor's descent as a fan rather than every body
 * descending into one file.
 *
 * <p>{@code -Dbattle.swarm.flowField=false} (or
 * {@link BattleSimulation#setSwarmFlowFieldEnabled}) is the control: every
 * move paths body by body, as the swarm did before fields.
 */
public final class SwarmMovement {

    /** Within this many cells of its target a body pursues it with its own short path. */
    public static final float LOCAL_PURSUIT_RANGE = 12f;
    /** Cells a body takes off its field per descent; it re-descends on its repath clock. */
    static final int SEGMENT_CELLS = 10;
    /** Re-descend early when this few cells of the current segment remain. */
    private static final int SEGMENT_REFILL = 3;

    private SwarmMovement() {}

    /**
     * Moves {@code body} one tick toward {@code target}: directly when the
     * target is close or is not prey the swarm may know at range (a civilian
     * it has found), otherwise down the {@link SwarmGoal#PREY} field.
     *
     * @param retargeted whether the body chose {@code target} this tick; a
     *                   new target is pathed to at once rather than on the
     *                   repath clock
     */
    public static void pursue(long body, long target, boolean retargeted,
                              BattleSimulation sim) {
        if (SwarmPackMovement.steer(body, target, sim)) return;
        if (sim.isSwarmFlowFieldEnabled()
                && !isLocal(body, target, sim)
                && Faction.DEFENDER.hostileTo(sim.identity().faction(target))
                && moveToward(body, SwarmGoal.PREY, sim)) {
            return;
        }
        pathDirect(body, sim.world().cellX(target), sim.world().cellY(target),
                retargeted, sim);
    }

    /**
     * Moves {@code body} one tick down {@code goal}'s flow field. Returns
     * false, having moved nothing, when fields are switched off, this goal's
     * is not built yet (asking here builds it before the next tick), or it
     * does not reach the body; the caller then moves the body some other way.
     */
    public static boolean moveToward(long body, SwarmGoal goal, BattleSimulation sim) {
        if (!sim.isSwarmFlowFieldEnabled()) return false;
        SwarmFlowFields fields = sim.getGrid().swarmFlowFields();
        fields.request(goal.ordinal(), sim.getSimTickIndex());
        SwarmFlowFields.Field field = fields.field(goal.ordinal());
        if (field == null) return false;
        int x = sim.world().cellX(body);
        int y = sim.world().cellY(body);
        int distance = field.distanceAt(x, y);
        if (distance == SwarmFlowFields.UNREACHABLE) return false;
        if (distance == 0) {
            sim.clearPath(body);
            return true;
        }
        int remaining = Paths.cellCount(sim.movement().path(body))
                - sim.movement().pathIdx(body);
        if (remaining <= SEGMENT_REFILL || sim.movement().mayRepath(body)) {
            int[] segment = field.descend(sim.getGrid(), x, y, SEGMENT_CELLS);
            if (Paths.isEmpty(segment)) return false;
            sim.setPath(body, segment);
        }
        sim.advanceMovement(body);
        return true;
    }

    /**
     * Serial, before the unit update: brings every goal a body asked for up to
     * date. Prey sources are gathered here, before any body moves, so a
     * field never depends on the order workers moved bodies in.
     */
    public static void prepare(BattleSimulation sim) {
        SwarmFlowFields fields = sim.getGrid().swarmFlowFields();
        int tick = sim.getSimTickIndex();
        for (SwarmGoal goal : SwarmGoal.values()) {
            if (!fields.isWanted(goal.ordinal(), tick)) continue;
            int[] sources = goal == SwarmGoal.PREY
                    ? preyCells(sim) : fields.fixedSources(goal.ordinal());
            if (sources != null) fields.refresh(goal.ordinal(), sources, sources.length, tick);
        }
        fields.retireUnused(tick);
    }

    /** Declares the cells of a disclosed goal (shelter, lift); {@code null} withdraws them. */
    public static void declareGoalCells(BattleSimulation sim, SwarmGoal goal, int[] cells) {
        if (goal == SwarmGoal.PREY) {
            throw new IllegalArgumentException("prey is gathered, not declared");
        }
        sim.getGrid().swarmFlowFields().setFixedSources(goal.ordinal(), cells);
    }

    private static int[] preyCells(BattleSimulation sim) {
        boolean shelterProtected = sim.isCivilianShelterProtected();
        int[] cells = new int[sim.liveUnitCount()];
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!Faction.DEFENDER.hostileTo(sim.identity().faction(unit))) continue;
            if (sim.isRiding(unit) || !sim.world().hasPosition(unit)) continue;
            if (!SwarmPressureBehavior.eligiblePreyKnownFaction(
                    unit, shelterProtected, sim)) continue;
            int x = sim.world().cellX(unit);
            int y = sim.world().cellY(unit);
            if (!sim.getGrid().inBounds(x, y)) continue;
            cells[count++] = sim.getGrid().index(x, y);
        }
        return Arrays.copyOf(cells, count);
    }

    private static boolean isLocal(long body, long target, BattleSimulation sim) {
        float dx = sim.world().x(target) - sim.world().x(body);
        float dy = sim.world().y(target) - sim.world().y(body);
        return dx * dx + dy * dy <= LOCAL_PURSUIT_RANGE * LOCAL_PURSUIT_RANGE;
    }

    /** The body's own path to a cell, repathed on the movement clock or at once when retargeted. */
    static void pathDirect(long body, int targetX, int targetY, boolean retargeted,
                           BattleSimulation sim) {
        ensurePath(body, targetX, targetY, retargeted, sim);
        sim.advanceMovement(body);
    }

    /** Gives the body its own path to a cell, on the movement clock or at once when retargeted; moves nothing. */
    static void ensurePath(long body, int targetX, int targetY, boolean retargeted,
                           BattleSimulation sim) {
        if ((retargeted || sim.movement().mayRepath(body))
                && needsPath(body, targetX, targetY, sim)) {
            int bodyX = sim.world().cellX(body);
            int bodyY = sim.world().cellY(body);
            int[] path = SharedGoalPolicy.usesSharedGoalFields(sim.liveUnitCount())
                    ? sim.findSharedPathToGoal(bodyX, bodyY, targetX, targetY)
                    : GridPathfinder.findPath(sim.getGrid(),
                            bodyX, bodyY, targetX, targetY,
                            sim.getOccupancyMap());
            sim.setPath(body, path);
        }
    }

    private static boolean needsPath(long body, int targetX, int targetY,
                                     BattleSimulation sim) {
        int[] path = sim.movement().path(body);
        return sim.movement().pathIdx(body) >= Paths.cellCount(path)
                || Paths.destX(path) != targetX
                || Paths.destY(path) != targetY;
    }
}
