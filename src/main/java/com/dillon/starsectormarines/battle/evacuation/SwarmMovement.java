package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.nav.SwarmFlowFields;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

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
 * <p>A militia barricade is not a wall to the swarm: the field crosses it at
 * a cost, so where the way round is long the swarm comes on at it, and a body
 * whose way on runs through one tears at it until it breaks
 * ({@code colony-breach-nouns.md}, Roadblock). Close in, a body the field
 * leads through a barricade follows the field too.
 *
 * <p>{@code -Dbattle.swarm.flowField=false} (or
 * {@link BattleSimulation#setSwarmFlowFieldEnabled}) is the control: every
 * move paths body by body, as the swarm did before fields.
 *
 * <p>A body's own path to a cell close by is a bounded search
 * ({@link #LOCAL_PATH_CELLS}, {@link #LOCAL_PATH_EXPANSIONS}): melee is where
 * every body re-paths to a victim that moves every tick, and a whole-map answer
 * per goal cell (a shared reverse field, or an A* that floods the map round a
 * goal it cannot reach) was 140-630 ms ticks once a large swarm made contact
 * ({@code swarm-movement-cost.md}). A pursuit the bounded search cannot close
 * goes back to the prey field; a farther cell gets {@link #FAR_PATH_EXPANSIONS}
 * before the whole-map answer. {@code -Dbattle.swarm.localPathBudget=false} is
 * the control: every own path is the unbounded search, as before.
 */
public final class SwarmMovement {

    /** Within this many cells of its target a body pursues it with its own short path. */
    public static final float LOCAL_PURSUIT_RANGE = 12f;
    /** Cells a body takes off its field per descent; it re-descends on its repath clock. */
    static final int SEGMENT_CELLS = 10;
    /** Re-descend early when this few cells of the current segment remain. */
    private static final int SEGMENT_REFILL = 3;
    /** Wall damage one Runner's blow does to a barricade piece; a piece has 100. */
    static final int RUNNER_BLOW = 8;
    /** How far from a barricade cell's centre a body can strike it. */
    private static final float BARRICADE_REACH = 1.6f;
    /** Cells down the field a body close to its target looks for a barricade it should tear through. */
    private static final int BARRICADE_LOOKAHEAD = (int) LOCAL_PURSUIT_RANGE + 2;
    /** A destination within this many cells (on each axis) is pathed to with the bounded search. */
    static final int LOCAL_PATH_CELLS = 16;
    /**
     * Pops the bounded search may spend: the whole {@link #LOCAL_PATH_CELLS} box
     * once over, so any route that stays near the body is found, crowd tolls and all.
     */
    static final int LOCAL_PATH_EXPANSIONS = (2 * LOCAL_PATH_CELLS + 1) * (2 * LOCAL_PATH_CELLS + 1);
    /**
     * Pops the bounded search may spend on a farther cell (a pack member's place
     * round a quarry it is still running in on) before it pays for the whole-map
     * answer: an open run of a hundred cells or so, and a fraction of one
     * whole-map field on 560x336.
     */
    static final int FAR_PATH_EXPANSIONS = 4096;
    private static final boolean LOCAL_PATH_BUDGET = Boolean.parseBoolean(
            System.getProperty("battle.swarm.localPathBudget", "true"));

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
        boolean field = sim.isSwarmFlowFieldEnabled()
                && Faction.DEFENDER.hostileTo(sim.identity().faction(target));
        boolean onField = field && (!isLocal(body, target, sim) || behindBarricade(body, sim));
        if (onField && assaultBarricade(body, sim)) return;
        if (onField && moveToward(body, SwarmGoal.PREY, sim)) return;
        // Close in, a victim the bounded search cannot reach is not close: the field goes round to it.
        if (!ensurePath(body, sim.world().cellX(target), sim.world().cellY(target), retargeted, !field, sim)
                && field && moveToward(body, SwarmGoal.PREY, sim)) return;
        sim.advanceMovement(body);
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
     * field never depends on the order workers moved bodies in. Steps pay the
     * fire's multipliers, so the swarm goes round a burning stand where it can.
     */
    public static void prepare(BattleSimulation sim) {
        SwarmFlowFields fields = sim.getGrid().swarmFlowFields();
        int tick = sim.getSimTickIndex();
        fields.setStepCost(sim.swarmStepCost());
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

    /**
     * Strikes the barricade the prey field leads this body through, when it
     * stands at it: the blow lands on the serial strike drain, on the body's
     * own strike cooldown. True when the body is at a barricade, struck or
     * waiting to; it holds there rather than going round.
     */
    static boolean assaultBarricade(long body, BattleSimulation sim) {
        if (!SwarmFlowFields.barricadeAssaultEnabled()) return false;
        SwarmFlowFields.Field field = sim.getGrid().swarmFlowFields().field(SwarmGoal.PREY.ordinal());
        if (field == null) return false;
        int ahead = field.barricadeAhead(sim.getGrid(), sim.world().cellX(body), sim.world().cellY(body));
        if (ahead < 0) return false;
        int width = sim.getGrid().getWidth();
        int bx = ahead % width, by = ahead / width;
        float dx = bx + 0.5f - sim.world().x(body), dy = by + 0.5f - sim.world().y(body);
        if (dx * dx + dy * dy > BARRICADE_REACH * BARRICADE_REACH) return false;
        sim.clearPath(body);
        if (sim.combat().cooldownTimer(body) <= 0f) {
            int blow = sim.identity().type(body) == UnitType.SWARM_BRUTE ? SwarmBruteBehavior.WALL_BLOW : RUNNER_BLOW;
            sim.swarmStrikes().postBlow(body, bx, by, null, blow);
            sim.combat().setCooldownTimer(body, SwarmPressureBehavior.strikeCooldown(body, sim));
        }
        return true;
    }

    /**
     * Whether the prey field leads this body through a barricade close by: a
     * target behind a militia line, where going round is the longer way. The
     * field decides, not the body's own path, so a body does not turn back and
     * forth between the two.
     */
    private static boolean behindBarricade(long body, BattleSimulation sim) {
        if (!SwarmFlowFields.barricadeAssaultEnabled()) return false;
        SwarmFlowFields.Field field = sim.getGrid().swarmFlowFields().field(SwarmGoal.PREY.ordinal());
        return field != null && field.leadsThroughBarricade(sim.getGrid(),
                sim.world().cellX(body), sim.world().cellY(body), BARRICADE_LOOKAHEAD);
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

    /**
     * Gives the body its own path to a cell, on the movement clock or at once
     * when retargeted; moves nothing. Returns whether the body holds a path it
     * can still walk.
     *
     * <p>Every cell is tried with the bounded search first; a failure takes the
     * unbounded search, so a far goal is still reached.
     */
    static boolean ensurePath(long body, int targetX, int targetY, boolean retargeted,
                              BattleSimulation sim) {
        return ensurePath(body, targetX, targetY, retargeted, true, sim);
    }

    /**
     * As {@link #ensurePath(long, int, int, boolean, BattleSimulation)}; with
     * {@code wholeMap} false a cell the bounded search cannot reach leaves the
     * body without a path rather than paying for the whole-map answer, for a
     * caller with a field to fall back on.
     */
    static boolean ensurePath(long body, int targetX, int targetY, boolean retargeted,
                              boolean wholeMap, BattleSimulation sim) {
        if ((retargeted || sim.movement().mayRepath(body))
                && needsPath(body, targetX, targetY, sim)) {
            int bodyX = sim.world().cellX(body);
            int bodyY = sim.world().cellY(body);
            int[] path = GridPathfinder.EMPTY_PATH;
            if (LOCAL_PATH_BUDGET) {
                boolean near = isNearCell(bodyX, bodyY, targetX, targetY);
                path = GridPathfinder.findPathWithinExpansionBudget(sim.getGrid(),
                        bodyX, bodyY, targetX, targetY, sim.getOccupancyMap(),
                        near ? LOCAL_PATH_EXPANSIONS : FAR_PATH_EXPANSIONS);
                if (Paths.isEmpty(path) && !wholeMap) {
                    sim.clearPath(body);
                    return false;
                }
            }
            if (Paths.isEmpty(path)) {
                path = SharedGoalPolicy.usesSharedGoalFields(sim.liveUnitCount())
                        ? sim.findSharedPathToGoal(bodyX, bodyY, targetX, targetY)
                        : GridPathfinder.findPath(sim.getGrid(),
                                bodyX, bodyY, targetX, targetY,
                                sim.getOccupancyMap());
            }
            sim.setPath(body, path);
        }
        return sim.movement().pathIdx(body) < Paths.cellCount(sim.movement().path(body));
    }

    static boolean isNearCell(int fromX, int fromY, int toX, int toY) {
        return Math.abs(toX - fromX) <= LOCAL_PATH_CELLS && Math.abs(toY - fromY) <= LOCAL_PATH_CELLS;
    }

    private static boolean needsPath(long body, int targetX, int targetY,
                                     BattleSimulation sim) {
        int[] path = sim.movement().path(body);
        return sim.movement().pathIdx(body) >= Paths.cellCount(path)
                || Paths.destX(path) != targetX
                || Paths.destY(path) != targetY;
    }
}
