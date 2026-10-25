package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.MatriarchActions.Action;
import com.dillon.starsectormarines.battle.evacuation.MatriarchActions.Kind;
import com.dillon.starsectormarines.battle.evacuation.SwarmBruteBehavior.BreachPoint;
import com.dillon.starsectormarines.battle.evacuation.SwarmBruteBehavior.Breaker;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.LongBucket;

import java.util.Arrays;

/**
 * The Matriarch: the mother bore's heavy strain, slow and plated, and the swarm's answer to everything a
 * Brute cannot break.
 *
 * <p>It comes out roaring, then hunts as a Brute does: a sensed emplacement first, otherwise the swarm's
 * prey, pressing toward it on the swarm's strategic move. What it does on reaching it depends on what it
 * reached. Infantry is reaped: one scythe sweeps across its front, right then left on the next, and cuts
 * everyone in the arc, a handful at a time, throwing whoever lives through it down and across. Anything
 * else, an emplacement, a hull or a mech, takes the crown: it rocks back onto its haunches and drives the plated crown into it. Prey inside a building, or
 * behind a barricade, is reached through the wall: it breaks walls a Brute could not (a fort's buildings,
 * not its curtain until something has worn that down), from further off, and never walks round to a door.
 *
 * <p>Every blow is an action that holds the body ({@link MatriarchActions}): it begins, and lands part-way
 * through, so a reap is seen coming before it cuts. A Matriarch is not staggered and does not charge.
 *
 * <p>It walks, and it runs ({@link MatriarchRunRules}): infantry that has let it come within the band on open
 * ground draws a tell, crown-down and bellowing, then a run at it that closes right in before the reap, so the
 * cut lands on a body that kept walking. A run ends on the strike, on its time, or when the quarry changes, and
 * its cooldown leaves room to walk away again.
 */
final class SwarmMatriarchBehavior {

    static final float TICK_SECONDS = BattleSimulation.TICK_DT;
    /** From its centre to its prey's surface: its crown is nearly three cells ahead of its centre. */
    static final float REACH = 2.9f;
    /** A reap cuts what stands within this of its centre, in front of it. */
    static final float REAP_RADIUS = 3.6f;
    /** ...and no more than this far off the line to its prey: the cosine of eighty degrees. */
    static final float REAP_ARC_COS = 0.17f;
    static final int REAP_MAX_BODIES = 5;
    /** A scythe goes through infantry plate as a Brute's blow does, and further. */
    static final float REAP_PENETRATION = 12f;
    /** A reaped body is thrown this much forward for each cell across the front. */
    static final float TOSS_FORWARD = 0.75f;
    /** The crown: a mech's armour is not proof against it. */
    static final float CROWN_DAMAGE = 90f;
    static final float CROWN_PENETRATION = 22f;
    /** Wall damage per crown blow; a colony wall falls to one, a fort building's to three. */
    static final int WALL_BLOW = 100;
    static final Breaker BREAKER = new Breaker(320, 3.4f, 0f);

    private static final ThreadLocal<LongBucket> CANDIDATES = ThreadLocal.withInitial(LongBucket::new);

    private SwarmMatriarchBehavior() {}

    static void update(long matriarch, BattleSimulation sim) {
        SwarmPressureBehavior.tickCooldown(matriarch, sim);
        int now = sim.getSimTickIndex();
        Action action = sim.matriarchActions().of(matriarch);
        if (!action.roared) {
            action.roared = true;
            begin(action, Kind.ROAR, now, 0L, 0f, 0f);
            sim.swarmCues().post(SwarmCues.Kind.ROAR, matriarch, sim);
        }
        if (action.kind != Kind.NONE) {
            float age = MatriarchActions.age(action, now);
            if (age < action.kind.seconds) {
                sim.clearPath(matriarch);
                if (!action.landed && action.kind.hitSeconds >= 0f && age >= action.kind.hitSeconds) {
                    action.landed = true;
                    land(matriarch, action, sim);
                }
                return;
            }
            if (action.kind == Kind.RUN_TELL) {
                action.running = true;
                action.runStartTick = now;
                action.runQuarry = action.target;
            }
            action.kind = Kind.NONE;
        }

        long previousTarget = sim.combat().targetId(matriarch);
        long target = SwarmBruteBehavior.sensedEmplacement(matriarch, sim);
        if (target == 0L) target = SwarmPressureBehavior.selectTarget(matriarch, sim);
        sim.combat().setTargetId(matriarch, target);
        if (target == 0L) {
            endRun(action, now);
            SwarmPressureBehavior.updateRoaming(matriarch, previousTarget, sim);
            return;
        }
        if (target != previousTarget && !sim.swarmPacks().followsLeader(matriarch, target, sim)) {
            sim.swarmCues().post(SwarmCues.Kind.ALERT, matriarch, sim);
        }
        boolean ready = sim.combat().cooldownTimer(matriarch) <= 0f;
        float gap = SwarmBruteBehavior.gap(matriarch, target, sim);
        if (action.running && MatriarchRunRules.runOver(
                (now - action.runStartTick) * TICK_SECONDS, target == action.runQuarry, gap)) {
            endRun(action, now);
        }

        if (gap <= MatriarchRunRules.strikeGap(action.running, REACH)) {
            endRun(action, now);
            sim.clearPath(matriarch);
            if (ready) {
                float dx = sim.world().x(target) - sim.world().x(matriarch);
                float dy = sim.world().y(target) - sim.world().y(matriarch);
                float d = (float) Math.hypot(dx, dy);
                Kind kind = Kind.CROWN;
                if (BruteChargeSystem.kind(target, sim) == BruteChargeRules.Kind.INFANTRY) {
                    kind = action.nextLeft ? Kind.REAP_LEFT : Kind.REAP_RIGHT;
                    action.nextLeft = !action.nextLeft;
                    sim.swarmCues().post(SwarmCues.Kind.REAP, matriarch, sim);
                }
                begin(action, kind, now, target, d > 1e-4f ? dx / d : 0f, d > 1e-4f ? dy / d : 1f);
                sim.combat().setCooldownTimer(matriarch, SwarmPressureBehavior.strikeCooldown(matriarch, sim));
            }
            return;
        }

        BreachPoint breach = SwarmBruteBehavior.breachPointToward(matriarch, target, BREAKER, sim);
        if (breach == null) breach = SwarmBruteBehavior.barricadeToward(matriarch, target, BREAKER, sim);
        if (breach != null) {
            endRun(action, now);
            if (breach.inReach) {
                sim.clearPath(matriarch);
                if (ready) {
                    begin(action, Kind.CROWN, now, 0L, 0f, 0f);
                    action.breach = breach;
                    sim.combat().setCooldownTimer(matriarch, SwarmPressureBehavior.strikeCooldown(matriarch, sim));
                }
                return;
            }
            SwarmPressureBehavior.pressToward(matriarch, breach.approachX, breach.approachY,
                    target != previousTarget, sim);
            return;
        }
        if (sim.identity().type(target).isStatic()) {
            int[] stand = SwarmBruteBehavior.standBeside(matriarch, target, REACH, sim);
            if (stand != null) {
                SwarmPressureBehavior.pressToward(matriarch, stand[0], stand[1], target != previousTarget, sim);
                return;
            }
        }
        if (!action.running && tryRun(matriarch, action, target, gap, now, sim)) return;
        SwarmPressureBehavior.pressToward(matriarch, target, target != previousTarget, sim);
    }

    /** Begins the tell before a run at {@code target} when the rules allow one; true when it did. */
    private static boolean tryRun(long matriarch, Action action, long target, float gap, int now,
                                  BattleSimulation sim) {
        boolean onFoot = BruteChargeSystem.kind(target, sim) == BruteChargeRules.Kind.INFANTRY;
        // The line is walked only for quarry in the band.
        boolean open = gap <= MatriarchRunRules.BAND && SwarmBruteBehavior.lineOpen(sim,
                sim.world().cellX(matriarch), sim.world().cellY(matriarch),
                sim.world().cellX(target), sim.world().cellY(target));
        if (!MatriarchRunRules.shouldRun(gap, REACH, onFoot, open, now, action.runReadyTick)) return false;
        float dx = sim.world().x(target) - sim.world().x(matriarch);
        float dy = sim.world().y(target) - sim.world().y(matriarch);
        float d = (float) Math.hypot(dx, dy);
        begin(action, Kind.RUN_TELL, now, target, d > 1e-4f ? dx / d : 0f, d > 1e-4f ? dy / d : 1f);
        sim.clearPath(matriarch);
        sim.swarmCues().post(SwarmCues.Kind.CHARGE_TELL, matriarch, sim);
        return true;
    }

    /** Ends a run, if she is on one, and starts its cooldown. */
    private static void endRun(Action action, int now) {
        if (!action.running) return;
        action.running = false;
        action.runReadyTick = now + Math.round(MatriarchRunRules.COOLDOWN / TICK_SECONDS);
    }

    private static void begin(Action action, Kind kind, int now, long target, float axisX, float axisY) {
        action.kind = kind;
        action.startTick = now;
        action.landed = false;
        action.target = target;
        action.axisX = axisX;
        action.axisY = axisY;
        action.breach = null;
    }

    private static void land(long matriarch, Action action, BattleSimulation sim) {
        float morale = sim.identity().type(matriarch).moraleImpact;
        if (action.kind == Kind.CROWN) {
            BreachPoint wall = action.breach;
            if (wall != null) {
                // The wall's own blow or break is heard, as a Brute's is.
                sim.swarmStrikes().postBlow(matriarch, wall.cellX, wall.cellY, wall.edge, WALL_BLOW);
                return;
            } else {
                long target = action.target;
                // It may have moved off in the wind-up: the crown lands only on what is still in front of it.
                if (!sim.getRoster().isAliveById(target)
                        || SwarmBruteBehavior.gap(matriarch, target, sim) > REACH + 0.5f) return;
                sim.applyDamage(target, matriarch, CROWN_DAMAGE, CROWN_PENETRATION, morale);
                sim.bruteCharges().blow(matriarch, target);
            }
            sim.swarmCues().post(SwarmCues.Kind.STRIKE, matriarch, sim);
            return;
        }
        long[] struck = reaped(matriarch, action.axisX, action.axisY, sim);
        float damage = sim.combat().attackDamage(matriarch);
        // The sweep carries what it cuts with it: forward and across to the side the blade swings toward.
        float across = action.kind == Kind.REAP_RIGHT ? -1f : 1f;
        float rightX = action.axisY, rightY = -action.axisX;
        float tx = action.axisX * TOSS_FORWARD + rightX * across, ty = action.axisY * TOSS_FORWARD + rightY * across;
        float tl = (float) Math.hypot(tx, ty);
        for (long body : struck) {
            sim.applyDamage(body, matriarch, damage, REAP_PENETRATION, morale);
            sim.bruteCharges().toss(matriarch, body, tx / tl, ty / tl);
        }
    }

    /**
     * The infantry a reap along {@code (axisX, axisY)} cuts: hostile bodies within {@link #REAP_RADIUS} of
     * the centre and inside the arc, nearest first, at most {@link #REAP_MAX_BODIES}.
     */
    static long[] reaped(long matriarch, float axisX, float axisY, BattleSimulation sim) {
        float x = sim.world().x(matriarch), y = sim.world().y(matriarch);
        LongBucket nearby = CANDIDATES.get();
        nearby.clear();
        sim.getUnitIndex().gatherHostileCombatants(x, y, REAP_RADIUS + 1f, sim.identity().faction(matriarch),
                nearby);
        long[] ids = new long[nearby.size];
        float[] distance = new float[nearby.size];
        int n = 0;
        for (int i = 0; i < nearby.size; i++) {
            long id = nearby.ids[i];
            if (!sim.getRoster().isAliveById(id)
                    || BruteChargeSystem.kind(id, sim) != BruteChargeRules.Kind.INFANTRY) continue;
            float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
            float d = (float) Math.hypot(dx, dy);
            if (d - sim.getRoster().radius(id) > REAP_RADIUS) continue;
            if (d > 1e-4f && (dx * axisX + dy * axisY) / d < REAP_ARC_COS) continue;
            ids[n] = id;
            distance[n] = d;
            n++;
        }
        return nearest(ids, distance, n, REAP_MAX_BODIES);
    }

    /** The first {@code limit} of {@code n} ids by distance, ties by id: the same bodies every replay. */
    static long[] nearest(long[] ids, float[] distance, int n, int limit) {
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> distance[a] != distance[b]
                ? Float.compare(distance[a], distance[b]) : Long.compare(ids[a], ids[b]));
        long[] out = new long[Math.min(n, limit)];
        for (int i = 0; i < out.length; i++) out[i] = ids[order[i]];
        return out;
    }
}
