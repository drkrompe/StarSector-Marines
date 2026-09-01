package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FiringLane;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.LongBucket;

/**
 * A marine with one of his own standing between him and what he is shooting at
 * <b>moves a cell</b>.
 *
 * <p>This is the second verb tried against the same fault, in the second of
 * two shapes. Nothing on the
 * decision side had ever asked whether a friendly stood in a firing lane —
 * every line test bottoms out in the navigation grid, which holds terrain and
 * no units — while {@code BallisticResolver} had always answered from the
 * round's side, giving a friendly met before the target about a third of a
 * chance of catching it at half damage. So a marine with a squadmate directly
 * in front read his lane as clear and fired into their back indefinitely.
 *
 * <p>The first verb was to <em>switch target</em>, and it was measured twice
 * and lost captures both times; {@link TacticalScoring#FRIENDLY_LANE_COST}
 * carries those numbers. The reason is worth restating because it is the reason
 * this class exists: a blocked lane usually means a squadmate is between this
 * marine and the enemy the squad is already engaging, which is a firing line
 * working correctly, and moving somebody onto a different target breaks up the
 * concentrated fire that does the killing. Stepping aside keeps the squad's
 * fire where it is <em>and</em> stops shooting its own man, where retargeting
 * trades the second for the first.
 *
 * <p><b>What it will not trade.</b> A candidate cell must keep the target in
 * range and in line of fire, must not lower the marine's cover toward that
 * target, and must not put him into a squadmate's lane — the firing-line case
 * is everybody shooting at the same thing, so clearing your own lane by masking
 * the man behind you is the same fault moved one cell back. No candidate means
 * no move: the step runs exactly as it does today, fires, and the ballistic
 * model's discipline roll decides, which is the status quo rather than a
 * regression.
 *
 * <p><b>Where the man stands decides whether a step can help.</b> A lane
 * converges on its target, so two cells of movement at the muzzle is worth two
 * cells of clearance right in front of the marine and almost none out at the
 * far end. A squadmate three cells ahead of a fourteen-cell shot can be stepped
 * around; the same squadmate halfway down it cannot, not inside
 * {@link #SEARCH_RADIUS}, and the reflex declines rather than walking further
 * to make it possible. That is the intended bound: a sidestep is a step, and a
 * marine who leaves his place in the line to open an angle has been given the
 * mission-inventing behaviour a reflex is not allowed to have.
 *
 * <p><b>It answers something the marine is already in, and invents nothing.</b>
 * It fires only in the situation where the assigned step would author fire
 * intent this very tick — live hostile target, inside the weapon's range, grid
 * line of fire clear — and only for a marine standing to fire rather than one
 * in transit, because a marine walking under an attack move is moved by his
 * step and his lane changes every tick anyway.
 *
 * <p><b>It does what it says, in the small.</b> {@code FiringLineScene} puts
 * six marines in column in a corridor and records the control putting 5.9 HP
 * into its own men and the subject putting none, with the identical 100.0 HP
 * landed on the enemy and all six alive at the end of both. Five sidesteps
 * carry that difference, and every one of them covers ground.
 *
 * <p><b>Two shapes of it have been measured against Conquest.</b> The first
 * consumed the tick: the reflex moved the marine itself and no plan step ran
 * while it did. That cost {@code conquest-reinforced-south} ten of its
 * fourteen captures — 4 against 14, holding 2 against 9 — while killing fewer
 * defenders (308 against 334), which is not a squad that traded shooting for
 * safety but one that spent time it did not have.
 *
 * <p>The second shape is this one, and it is the same lane test with the tick
 * handed back. The reflex authors the path and declines; the assigned step
 * fires the marine's weapon and walks him along it, so he shoots while he
 * moves. That recovers almost all of the loss:
 *
 * <table><caption>Conquest, reflex on against the same tree with it off</caption>
 * <tr><th>fixture</th><th>captures</th><th>held</th><th>defenders</th>
 *     <th>marines lost</th><th>result</th></tr>
 * <tr><td>reinforced-south, on</td><td>12</td><td>11</td><td>390</td>
 *     <td>216</td><td>timeout at 18,000</td></tr>
 * <tr><td>reinforced-south, off</td><td>14</td><td>9</td><td>334</td>
 *     <td>237</td><td>timeout at 18,000</td></tr>
 * <tr><td>full-strength-west, on</td><td>3</td><td>3</td><td>311</td>
 *     <td>426</td><td>defeat at 16,445</td></tr>
 * <tr><td>full-strength-west, off</td><td>3</td><td>0</td><td>319</td>
 *     <td>425</td><td>defeat at 16,236</td></tr>
 * </table>
 *
 * <p>Twelve captures against four, eleven held against two, 56 more defenders
 * killed and 21 fewer marines lost than the tick-consuming shape — and against
 * the control it now holds <em>more</em> on both fixtures, 11 against 9 and 3
 * against 0, taking 12 compounds and keeping 11 where the control takes 14 and
 * keeps 9.
 *
 * <p><b>It ships on, and the reason is which column is the outcome.</b>
 * Compounds held at the end is what a Conquest is won or lost on; captures is
 * throughput, and a run that takes twelve and keeps eleven has done better than
 * one that takes fourteen and keeps nine. On that reading this wins both
 * fixtures — 11 held against 9, and 3 against 0 — while killing 56 more
 * defenders and losing 21 fewer marines on the first and no more on the second.
 * The bar it was first judged against forbade any fixture capturing fewer, and
 * reinforced-south captures twelve against fourteen; that bar was stricter than
 * the outcome it was guarding.
 *
 * <p>What the two shapes together establish is where the cost lived. It was
 * never the lane test, and never the step: it was the tick the marine spent
 * not shooting. Four measurements across three verbs now say the same thing
 * about this fault from three directions.
 *
 * @see FiringLane the geometry, shared with the target picker and the mech overwatch search
 * @see InfantryReflexes#LANE_SIDESTEP where it sits in the marine's reflex order
 */
public final class LaneSidestep {

    /**
     * Turns the step-aside off for a control run:
     * {@code -Dbattle.infantry.laneSidestep=false}. On by default — see this
     * class's own measurement for why.
     */
    public static final String PROPERTY = "battle.infantry.laneSidestep";

    /**
     * How far the marine will look for a cell to stand in, in cells, Chebyshev.
     * A sidestep is a step: two cells is enough to clear a lane a cell and a
     * half wide from either side, and a marine who walks further than that to
     * shoot has left his place in the line.
     */
    static final int SEARCH_RADIUS = 2;

    /** Longest route to a candidate. A cell two away round a corner is not a sidestep. */
    static final int MAX_PATH_CELLS = 3;

    /**
     * How long the reflex keeps a marine while he finishes the move, in
     * sim-seconds. Matched to {@link RepositionToCover#COOLDOWN_SECONDS}, which
     * is also the cooldown the move stamps: the marine may not start a second
     * sidestep until the first has had its time, so the two bounds are the same
     * number rather than two that have to be kept in step.
     */
    static final float HOLD_SECONDS = RepositionToCover.COOLDOWN_SECONDS;

    /**
     * Read once at class load and settable for evidence. Volatile because a
     * scene's control loop flips it between loops on one thread while the unit
     * dispatch reads it from several.
     */
    private static volatile boolean enabled =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * The gather buffers, per dispatch thread. The unit update runs in parallel
     * above a threshold, so a shared pair would be two marines writing one
     * bucket; a fresh pair per call would be garbage on every firing marine on
     * every tick.
     */
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private LaneSidestep() {}

    /** Whether the step-aside is in effect. */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Evidence seam: turns the step-aside on or off for the rest of this JVM.
     *
     * <p>Exists so one scene can play its subject and its control in a single
     * run — loops play sequentially on one thread, and a control that needed a
     * second JVM would be a second command nobody runs. Restore the previous
     * value in a {@code finally}; production never calls this, and the property
     * is what a Conquest control run switches.
     */
    public static void setEnabledForEvidence(boolean value) {
        enabled = value;
    }

    /**
     * Runs the step-aside for one marine.
     *
     * <p><b>Always declines.</b> It is a reflex by rank rather than by
     * interruption: what it does is author a path, and the assigned step is
     * what fires this marine's weapon and walks him along it. Consuming the
     * tick instead — the shape the third attempt shipped — bought a
     * cell-and-a-half of movement at the price of every shot the marine would
     * have taken during it, and the Conquest matrix charged for that.
     *
     * @return always {@code false}. Movement is advanced by the plan step and
     *         never here, which is what keeps exactly one caller moving the
     *         marine per tick; {@code FiringLineScene}'s
     *         {@code one-mover-per-tick} verdict is what proves it.
     */
    public static boolean stepOutOfLane(long unit, BattleControl sim) {
        World world = sim.world();
        if (!world.hasAiState(unit)) return false;
        if (world.sidestepTimer(unit) > 0f) return continueMove(unit, sim, world);
        if (!enabled) return false;
        if (stillWalking(unit, world)) return false;
        if (world.repositionCooldown(unit) > 0f) return false;
        // A body that cannot walk cannot step aside, and one that took the
        // path anyway would sit on it for the whole hold without moving a cell
        // — a fixed emplacement or a scene's planted defender losing a second
        // and a half of fire to a move it can never make.
        if (!(sim.movement().moveSpeed(unit) > 0f)) return false;

        long target = engagementTarget(unit, sim);
        if (target == 0L) return false;
        if (sim.identity().faction(target) == sim.identity().faction(unit)) return false;

        float fromX = world.x(unit);
        float fromY = world.y(unit);
        float toX = world.x(target);
        float toY = world.y(target);
        float range = world.attackRange(unit);
        // The firing system's own gate, asked here rather than restated: this
        // reflex is only interested in the tick on which the step would author
        // fire intent, and a second range rule would drift from that one.
        NavigationGrid grid = sim.getGrid();
        if (TacticalScoring.cellDistance(fromX, fromY, toX, toY) > range) return false;
        if (!grid.hasLineOfFire(fromX, fromY, toX, toY)) return false;

        Scratch scratch = SCRATCH.get();
        FiringLane.Friendlies allies = scratch.allies;
        sim.getTacticalScoring().gatherFriendlies(unit,
                TacticalScoring.cellDistance(fromX, fromY, toX, toY) + SEARCH_RADIUS,
                scratch.ids, allies);
        if (allies.size() == 0) return false;
        if (!FiringLane.blocked(allies, fromX, fromY, toX, toY)) return false;

        int[] path = chooseStep(unit, target, sim, world, grid, allies,
                fromX, fromY, toX, toY, range, scratch.self);
        if (path == null) return false;

        sim.setPath(unit, path);
        world.setRepositionCooldown(unit, RepositionToCover.COOLDOWN_SECONDS);
        world.setSidestepTimer(unit, HOLD_SECONDS);
        // Authored, not executed. Declining hands the tick to the assigned
        // step, which authors this marine's fire intent and walks him along
        // the path we just set — so he shoots while he moves, the way he does
        // under an attack move or after a post-fire reposition. Movement is
        // advanced by that step and never here; see #isStepping for the two
        // places that had to learn to advance it instead of throwing it away.
        return false;
    }

    /**
     * Bookkeeping for a step-aside already under way. Never consumes the tick.
     *
     * <p>All it does is close the timer on arrival, rather than leaving it to
     * run out, so the marine is free to consider another step the moment he is
     * standing where he meant to be. While the move is still going the reflex
     * declines <em>without re-deciding</em>: the timer is what stops it
     * choosing a second cell out from under the first, which is the other half
     * of why the field exists.
     */
    private static boolean continueMove(long unit, BattleControl sim, World world) {
        if (!stillWalking(unit, world)) world.setSidestepTimer(unit, 0f);
        return false;
    }

    /**
     * Who this marine is shooting at, which is two questions because there are
     * two ways to be shooting at somebody.
     *
     * <p>{@code COMBAT_TARGET_ID} is the pursuit target and is what
     * {@link EngagePosture} and the zone actions write. It is <b>not</b> what a
     * standing firing line runs on: {@code OverwatchPosture} deliberately sets
     * no pursuit target at all — it owns the positional intent and leaves
     * target selection to the dispatcher's opportunity pass — so a squad that
     * has planted itself and is trading fire holds no pursuit target from one
     * tick to the next. Gated on that alone this reflex could never fire in the
     * case it exists for, which is exactly what {@code FiringLineScene}
     * recorded the first time it was played: six marines in column, shooting
     * through one another, and not one sidestep.
     *
     * <p>The registered threat is the other answer and the persistent one. The
     * firing system will not let a round leave the barrel until it matches, so
     * it is the id this marine is about to shoot at whichever path chose it.
     */
    private static long engagementTarget(long unit, BattleControl sim) {
        long pursued = sim.targetOf(unit);
        if (pursued != 0L) return pursued;
        return sim.resolveUnit(sim.combat().reflexTargetId(unit));
    }

    /**
     * True while this marine is part-way through a step-aside — the marker a
     * plan step consults before it throws his path away.
     *
     * <p>Three steps plant a marine by clearing his path when he has a firing
     * solution from where he stands, which on the tick a sidestep is authored
     * kills the move before it has covered a cell.
     * {@code AbstractZoneAction} already had the answer for the post-fire
     * cover reposition — advance the short move rather than cancel it, while
     * its owner says it is live — and this is the same rule for this move.
     *
     * <p>It reads the sidestep timer rather than the reposition cooldown the
     * zone action uses, deliberately. The cooldown is shared with
     * {@link RepositionToCover}, so guarding on it would quietly change what a
     * planted squad does with <em>that</em> move as well, in every battle,
     * including the control runs this reflex is measured against. The timer is
     * set by nothing else, so under
     * {@code -Dbattle.infantry.laneSidestep=false} these guards cannot fire and
     * a control run is the tree without them — which is how the off column of
     * the table above was measured, and why it reproduces the earlier shape's
     * off run exactly.
     */
    public static boolean isStepping(long unit, BattleControl sim) {
        World world = sim.world();
        return world.hasAiState(unit)
                && world.sidestepTimer(unit) > 0f
                && stillWalking(unit, world);
    }

    /** In transit: a marine being moved by his step, whose lane changes anyway. */
    private static boolean stillWalking(long unit, World world) {
        return world.pathIdx(unit) < Paths.cellCount(world.path(unit));
    }

    /**
     * The cell to step to, or {@code null} when standing still is the better of
     * what is available.
     *
     * <p>Ranked shortest-displacement first, then least lateral offset from the
     * lane the marine is already shooting down, then lower cell coordinates —
     * a marine should end up beside where he was standing rather than behind
     * it, and two equally good cells must be decided the same way on every
     * replay of the same battle.
     */
    private static int[] chooseStep(long unit, long target, BattleControl sim, World world,
                                    NavigationGrid grid, FiringLane.Friendlies allies,
                                    float fromX, float fromY, float toX, float toY,
                                    float range, FiringLane.Friendlies self) {
        int cellX = world.cellX(unit);
        int cellY = world.cellY(unit);
        int targetCellX = world.cellX(target);
        int targetCellY = world.cellY(target);
        int currentCover = coverToward(sim, grid, cellX, cellY, targetCellX, targetCellY);
        byte[] occupancy = sim.getOccupancyMap();
        float bodyRadius = sim.physicalRadius(unit);

        int[] best = null;
        int bestX = 0;
        int bestY = 0;
        float bestDisplacement = Float.MAX_VALUE;
        float bestLateral = Float.MAX_VALUE;
        for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                if (dx == 0 && dy == 0) continue;
                int x = cellX + dx;
                int y = cellY + dy;
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                if ((occupancy[grid.index(x, y)] & 0xFF) != 0) continue;

                float px = x + 0.5f;
                float py = y + 0.5f;
                if (TacticalScoring.cellDistance(px, py, toX, toY) > range) continue;
                if (!grid.hasLineOfFire(px, py, toX, toY)) continue;
                if (FiringLane.blocked(allies, px, py, toX, toY)) continue;
                if (coverToward(sim, grid, x, y, targetCellX, targetCellY) < currentCover) continue;
                if (wouldMaskSomebody(allies, self, px, py, bodyRadius, toX, toY)) continue;

                float displacement = dx * dx + dy * dy;
                float lateral = lateralOffset(px, py, fromX, fromY, toX, toY);
                if (displacement > bestDisplacement) continue;
                if (displacement == bestDisplacement && lateral > bestLateral) continue;
                if (displacement == bestDisplacement && lateral == bestLateral
                        && best != null && (x > bestX || x == bestX && y > bestY)) {
                    continue;
                }
                int[] path = GridPathfinder.findPath(grid, cellX, cellY, x, y, occupancy);
                if (Paths.isEmpty(path) || Paths.cellCount(path) > MAX_PATH_CELLS) continue;
                best = path;
                bestX = x;
                bestY = y;
                bestDisplacement = displacement;
                bestLateral = lateral;
            }
        }
        return best;
    }

    /**
     * Whether standing at {@code (px, py)} would put this marine in one of his
     * own squadmates' lanes onto the same enemy.
     *
     * <p>The one-body {@link FiringLane.Friendlies} is reused rather than
     * allocated per candidate: it is the same geometry read the other way
     * round, with this marine as the obstruction.
     */
    private static boolean wouldMaskSomebody(FiringLane.Friendlies allies,
                                             FiringLane.Friendlies self,
                                             float px, float py, float radius,
                                             float toX, float toY) {
        self.clear();
        self.add(px, py, radius);
        for (int i = 0; i < allies.size(); i++) {
            if (FiringLane.blocked(self, allies.x(i), allies.y(i), toX, toY)) return true;
        }
        return false;
    }

    /** Wall plus doodad cover at a cell against fire from the target's bearing. */
    private static int coverToward(BattleControl sim, NavigationGrid grid,
                                   int x, int y, int targetCellX, int targetCellY) {
        int dx = targetCellX - x;
        int dy = targetCellY - y;
        return grid.getCoverAt(x, y, dx, dy) + sim.getDoodadCoverAt(x, y, dx, dy);
    }

    /** Perpendicular distance of a point from the lane the marine is shooting down. */
    private static float lateralOffset(float px, float py,
                                       float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-4f) return 0f;
        return Math.abs((px - fromX) * -dy + (py - fromY) * dx) / length;
    }

    /** One dispatch thread's reusable gather buffers. */
    private static final class Scratch {
        private final LongBucket ids = new LongBucket();
        private final FiringLane.Friendlies allies = new FiringLane.Friendlies();
        private final FiringLane.Friendlies self = new FiringLane.Friendlies();
    }
}
