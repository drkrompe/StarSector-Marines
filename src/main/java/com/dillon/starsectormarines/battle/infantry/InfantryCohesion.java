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
                    && sim.getTacticalScoring().hasClearShot(self, target)) {
                return null;
            }
        }

        return pullTarget(self, squad, sim);
    }

    /**
     * Whether {@code self} stands inside {@link #COHESION_RADIUS} of the
     * squadmate it would otherwise be pulled toward — the distance rule of
     * {@link #cohesionOverride} without its engagement escape.
     *
     * <p>Offered separately because that helper answers {@code null} to two
     * different questions: "close enough already" and "in a fight worth
     * staying in". A caller deciding whether a marine has <em>rejoined</em> its
     * squad has to tell those apart, and a marine trading fire thirty cells
     * from his squad has not rejoined it.
     *
     * <p>True when there is nobody to close on — no squad, no live squadmate,
     * or a squad of one.
     */
    public static boolean withinCohesion(long self, BattleView sim) {
        Squad squad = sim.squadOf(self);
        return squad == null || pullTarget(self, squad, sim) == null;
    }

    /**
     * The cell {@code self} is pulled toward, or null when it is already inside
     * {@link #COHESION_RADIUS} or has nobody to close on. The one distance rule
     * both public entry points read.
     *
     * <p>The anchor is the live {@link Squad#leaderId} cell when there is one
     * other than {@code self}, and the others-only centroid otherwise. The
     * centroid branch reconstructs itself out of the squad's cached aggregate,
     * which is refreshed once per tick and therefore does not yet count a
     * marine spawned later in the same tick — the landing case reads a slightly
     * overstated distance there, which errs toward treating an arrival as late.
     */
    private static int[] pullTarget(long self, Squad squad, BattleView sim) {
        long leader = sim.resolveUnit(squad.leaderId);
        if (leader != 0L && leader != self) {
            if (withinCohesion(sim.world().x(self), sim.world().y(self),
                    sim.world().x(leader), sim.world().y(leader))) {
                return null;
            }
            return new int[]{sim.world().cellX(leader), sim.world().cellY(leader)};
        }

        // Leaderless fallback — others-centroid (legacy behavior).
        // squad.centroid is sum/count over all alive members including self.
        // Reconstruct the others-only centroid: (sum - self) / (count - 1).
        // squad.centroidX/Y are true-position (center-based), matching x()/y().
        int othersCount = squad.aliveMembers - 1;
        if (othersCount <= 0) return null;
        float sumX = squad.centroidX * squad.aliveMembers - sim.world().x(self);
        float sumY = squad.centroidY * squad.aliveMembers - sim.world().y(self);
        float cx = sumX / othersCount;
        float cy = sumY / othersCount;
        if (withinCohesion(sim.world().x(self), sim.world().y(self), cx, cy)) return null;
        // The containing cell of a continuous position is its floor (round
        // would bias toward the next cell for center-based coordinates).
        return new int[]{(int) Math.floor(cx), (int) Math.floor(cy)};
    }

    /**
     * Whether a member standing at ({@code x}, {@code y}) is inside
     * {@link #COHESION_RADIUS} of the squadmate anchor at ({@code anchorX},
     * {@code anchorY}). The one distance comparison every cohesion caller
     * makes; what differs between them is only how the anchor was found.
     */
    public static boolean withinCohesion(float x, float y, float anchorX, float anchorY) {
        float dx = anchorX - x;
        float dy = anchorY - y;
        return dx * dx + dy * dy <= COHESION_RADIUS * COHESION_RADIUS;
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
