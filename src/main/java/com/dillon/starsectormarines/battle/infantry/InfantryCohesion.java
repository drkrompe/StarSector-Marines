package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Squad cohesion math shared by the GOAP infantry postures
 * ({@code EngagePosture}, {@code ApproachPosture}, {@code RegroupPosture})
 * and read by {@code WorldStateBuilder} for the
 * {@code WITHIN_COHESION_RADIUS} predicate.
 *
 * <p>Cohesion has two layers: the historical recovery override pulls a member
 * back after it drifts outside the radius, while Story I clips generic
 * Approach/Engage pursuit paths before they can cross that radius. Explicit
 * objective actions retain their own movement and leash contracts.
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
    public static int[] cohesionOverride(long self, BattleView sim) {
        Squad squad = sim.squadOf(self);
        if (squad == null || squad.aliveMembers <= 1) return null;

        // Engagement override — committed to a fight, don't drift back
        // to formation just because the squad's spread out. The
        // engagement-overrides-regroup rule is per-member, not per-squad:
        // one marine peeking from far cover doesn't pull the rest into
        // their lane.
        long target = sim.targetOf(self);
        if (target != 0L) {
            float td = (float) Math.sqrt(
                    (float) (sim.world().x(target) - sim.world().x(self)) * (sim.world().x(target) - sim.world().x(self))
                  + (float) (sim.world().y(target) - sim.world().y(self)) * (sim.world().y(target) - sim.world().y(self)));
            if (td <= sim.world().attackRange(self)
                    && sim.getGrid().hasLineOfSight(sim.world().cellX(self), sim.world().cellY(self),
                            sim.world().cellX(target), sim.world().cellY(target))) {
                return null;
            }
        }

        long leader = sim.resolveUnit(squad.leaderId);
        if (leader != 0L && leader != self) {
            float dx = sim.world().x(leader) - sim.world().x(self);
            float dy = sim.world().y(leader) - sim.world().y(self);
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= COHESION_RADIUS) return null;
            return new int[]{sim.world().cellX(leader), sim.world().cellY(leader)};
        }

        // Leaderless fallback — others-centroid (legacy behavior).
        // squad.centroid is sum/count over all alive members including self.
        // Reconstruct the others-only centroid: (sum - self) / (count - 1).
        // squad.centroidX/Y are true-position (center-based), matching x()/y().
        int othersCount = squad.aliveMembers - 1;
        float sumX = squad.centroidX * squad.aliveMembers - sim.world().x(self);
        float sumY = squad.centroidY * squad.aliveMembers - sim.world().y(self);
        float cx = sumX / othersCount;
        float cy = sumY / othersCount;
        float dx = cx - sim.world().x(self);
        float dy = cy - sim.world().y(self);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= COHESION_RADIUS) return null;
        // The containing cell of a continuous position is its floor (round
        // would bias toward the next cell for center-based coordinates).
        return new int[]{(int) Math.floor(cx), (int) Math.floor(cy)};
    }

    /**
     * Clips a generic pursuit path at the last cell whose center is inside the
     * squad leash. The path includes its start cell, so returning a one-cell
     * prefix is a valid planted hold when the first step would cross the
     * boundary.
     */
    public static int[] clampPursuitPath(int[] path, Squad squad) {
        if (path.length == 0 || squad.aliveMembers <= 1) return path;
        float radiusSquared = COHESION_RADIUS * COHESION_RADIUS;
        int cells = path.length / 2;
        int permittedCells = cells;
        for (int i = 0; i < cells; i++) {
            float dx = path[i * 2] + 0.5f - squad.centroidX;
            float dy = path[i * 2 + 1] + 0.5f - squad.centroidY;
            if (dx * dx + dy * dy > radiusSquared) {
                permittedCells = Math.max(1, i);
                break;
            }
        }
        if (permittedCells == cells) return path;
        int[] clipped = new int[permittedCells * 2];
        System.arraycopy(path, 0, clipped, 0, clipped.length);
        return clipped;
    }
}
