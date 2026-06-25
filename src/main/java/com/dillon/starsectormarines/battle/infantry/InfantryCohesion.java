package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;

/**
 * Squad cohesion math shared by the GOAP infantry postures
 * ({@code EngagePosture}, {@code ApproachPosture}, {@code RegroupPosture})
 * and read by {@code WorldStateBuilder} for the
 * {@code WITHIN_COHESION_RADIUS} predicate.
 *
 * <p>Cohesion is a soft leash: when a member drifts more than
 * {@link #COHESION_RADIUS} cells from the rest of the squad, the postures
 * path them back toward the centroid before resuming normal engagement, so
 * fireteams move as a group instead of scattering. Once within radius,
 * normal targeting takes over.
 *
 * <p>Stage 2 will likely re-imagine this as a hard cohesion clamp during
 * ENGAGED posture (Story I — engagement discipline). For now the soft
 * leash matches Stage 1's playtested behavior.
 */
public final class InfantryCohesion {

    /** Squadmate leash radius in cells. Outside this, the unit prioritizes rejoining the squad over picking a firing position. */
    public static final float COHESION_RADIUS = 12f;

    private InfantryCohesion() {}

    /**
     * Returns a cohesion anchor cell when the unit is more than
     * {@link #COHESION_RADIUS} cells from the rest of the squad and isn't
     * actively engaging an enemy; null otherwise (normal targeting takes
     * over). Solo units — no squad, or squad of one alive — always return
     * null.
     *
     * <p>The anchor is the {@link Squad#leader} cell when a live leader
     * exists and isn't {@code self}: every follower aims at the same
     * point, so route choice converges on one side of an obstacle
     * instead of bifurcating around it. Falls back to the others-centroid
     * (the historical pull target) only when the squad has no live
     * leader — solo squads, freshly-spawned squads before leader
     * assignment, or a transient tick where the leader died and
     * promotion hasn't run yet.
     *
     * <p><b>Engagement override.</b> A member with a live target inside
     * its {@code world.attackRange(id)} and clear LoS ignores cohesion and
     * stays in the fight — splitting around a building during combat
     * is fine; the failure mode was units stuck navigating <em>to</em>
     * the battlefield. See {@code memory/squad_leader_cohesion.md}.
     */
    public static int[] cohesionOverride(Entity self, BattleView sim) {
        if (self.squadId == Entity.NO_SQUAD) return null;
        Squad squad = sim.getSquad(self.squadId);
        if (squad == null || squad.aliveMembers <= 1) return null;

        // Engagement override — committed to a fight, don't drift back
        // to formation just because the squad's spread out. The
        // engagement-overrides-regroup rule is per-member, not per-squad:
        // one marine peeking from far cover doesn't pull the rest into
        // their lane.
        Entity target = sim.targetOf(self);
        if (target != null) {
            float td = (float) Math.sqrt(
                    (float) (sim.world().cellX(target.entityId) - sim.world().cellX(self.entityId)) * (sim.world().cellX(target.entityId) - sim.world().cellX(self.entityId))
                  + (float) (sim.world().cellY(target.entityId) - sim.world().cellY(self.entityId)) * (sim.world().cellY(target.entityId) - sim.world().cellY(self.entityId)));
            if (td <= sim.world().attackRange(self.entityId)
                    && sim.getGrid().hasLineOfSight(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId),
                            sim.world().cellX(target.entityId), sim.world().cellY(target.entityId))) {
                return null;
            }
        }

        Entity leader = sim.resolveUnit(squad.leaderId);
        if (leader != null && leader != self) {
            float dx = sim.world().cellX(leader.entityId) - sim.world().cellX(self.entityId);
            float dy = sim.world().cellY(leader.entityId) - sim.world().cellY(self.entityId);
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= COHESION_RADIUS) return null;
            return new int[]{sim.world().cellX(leader.entityId), sim.world().cellY(leader.entityId)};
        }

        // Leaderless fallback — others-centroid (legacy behavior).
        // squad.centroid is sum/count over all alive members including self.
        // Reconstruct the others-only centroid: (sum - self) / (count - 1).
        int othersCount = squad.aliveMembers - 1;
        float sumX = squad.centroidX * squad.aliveMembers - sim.world().cellX(self.entityId);
        float sumY = squad.centroidY * squad.aliveMembers - sim.world().cellY(self.entityId);
        float cx = sumX / othersCount;
        float cy = sumY / othersCount;
        float dx = cx - sim.world().cellX(self.entityId);
        float dy = cy - sim.world().cellY(self.entityId);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= COHESION_RADIUS) return null;
        return new int[]{Math.round(cx), Math.round(cy)};
    }
}
