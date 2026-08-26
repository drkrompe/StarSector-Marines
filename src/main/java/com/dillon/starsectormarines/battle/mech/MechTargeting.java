package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Traverse-aware target retention shared by every mech combat posture. */
public final class MechTargeting {

    /**
     * Visible enemies inside this radius interrupt a farther engagement.
     * Ten cells is well inside every direct mech weapon band, so this reads as
     * self-defense rather than a general preference for whichever target is
     * marginally closer.
     */
    public static final float CLOSE_THREAT_AGGRO_RANGE = 10f;

    private MechTargeting() {}

    /**
     * Keeps a usable target stable, but gives immediate priority to a close
     * visible threat and replaces a target outside the planted hips' traverse
     * whenever another currently shootable enemy is inside it.
     */
    public static long refreshTarget(long mech, BattleView sim) {
        float hipFacing = sim.world().mechHipFacingDegrees(mech);
        long current = sim.targetOf(mech);

        if (isDirectlyEngageable(mech, current, hipFacing,
                CLOSE_THREAT_AGGRO_RANGE, sim)) {
            return current;
        }

        long closeThreat = closestEngageableInArc(mech, hipFacing,
                CLOSE_THREAT_AGGRO_RANGE, sim);
        if (closeThreat != 0L) return closeThreat;

        float attackRange = sim.world().attackRange(mech);
        if (isDirectlyEngageable(mech, current, hipFacing, attackRange, sim)) {
            return current;
        }

        long preferred = sim.getTacticalScoring()
                .refreshTargetIfNotShootable(mech);
        if (isDirectlyEngageable(mech, preferred, hipFacing,
                attackRange, sim)) {
            return preferred;
        }

        long traversable = closestEngageableInArc(mech, hipFacing,
                attackRange, sim);
        return traversable != 0L ? traversable : preferred;
    }

    private static long closestEngageableInArc(long mech, float hipFacing,
                                                float range, BattleView sim) {
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        Faction ownFaction = sim.identity().faction(mech);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            if (candidate == mech
                    || sim.identity().faction(candidate) == ownFaction
                    || !sim.identity().type(candidate).combatant) {
                continue;
            }
            float distance = TacticalScoring.cellDistance(
                    sim.world().x(mech), sim.world().y(mech),
                    sim.world().x(candidate), sim.world().y(candidate));
            if (distance >= bestDistance
                    || !isDirectlyEngageable(mech, candidate, hipFacing,
                    range, sim)) {
                continue;
            }
            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    private static boolean isDirectlyEngageable(long mech, long target,
                                                 float hipFacing, float range,
                                                 BattleView sim) {
        if (target == 0L || sim.resolveUnit(target) == 0L) return false;
        if (sim.identity().faction(target) == sim.identity().faction(mech)
                || !sim.identity().type(target).combatant) {
            return false;
        }
        float dx = sim.world().x(target) - sim.world().x(mech);
        float dy = sim.world().y(target) - sim.world().y(mech);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > range || !withinTraverse(hipFacing, dx, dy)) return false;
        return sim.getTacticalScoring().hasClearShot(mech, target);
    }

    static boolean withinTraverse(float hipFacing, float dx, float dy) {
        if (dx == 0f && dy == 0f) return true;
        float targetFacing = LayeredAppearance.wrapDegrees(
                AirBody.facingToward(dx, dy));
        float twist = LayeredAppearance.wrapDegrees(targetFacing - hipFacing);
        return Math.abs(twist)
                <= LayeredMechAppearance.MAX_TORSO_TWIST_DEGREES;
    }
}
