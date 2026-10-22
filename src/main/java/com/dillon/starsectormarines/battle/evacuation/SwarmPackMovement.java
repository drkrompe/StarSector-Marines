package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * How a pack member moves (`colony-breach-nouns.md`, Pack movement). The flow
 * field stays the strategic layer: the pack's lane is the prey field's descent
 * from its anchor, and every member runs that lane at its own place in a fan
 * (sideways by rank, swept back by rank) instead of descending from its own
 * cell into the same file as everyone else. Near its quarry the pack stalks,
 * feints and rushes on the plan {@link SwarmPackPlans#prepare} made.
 *
 * <p>Reads only the plan and the member's own state, so it is safe in the
 * parallel unit update.
 */
final class SwarmPackMovement {

    /** Anchor speed while travelling, so the fan can keep its places. */
    static final float ANCHOR_PACE = 0.92f;
    /** Slowest a member ahead of its place drops to. */
    static final float MIN_PACE = 0.55f;
    /** Pace lost per cell a member runs ahead of its place. */
    static final float PACE_PER_CELL = 0.35f;
    /** Depth of the stride-to-stride pace swing. */
    static final float GAIT_SWING = 0.08f;
    /** Weave about a member's place, as a share of the fan's spacing. */
    static final float SWAY = 0.35f;
    /** A member whose own target is this close commits and pursues it itself. */
    static final float COMMIT_RANGE = 3f;

    private SwarmPackMovement() {}

    /**
     * Moves {@code body} one tick as a member of its pack. Returns false,
     * having moved nothing, when the body is not moving as a pack this tick
     * (no pack, a broken one, off its leash, committed to the rush, or its
     * own victim right in front of it); the caller then moves it alone.
     */
    static boolean steer(long body, long target, BattleSimulation sim) {
        if (!sim.isSwarmPackEnabled() || !sim.isSwarmFlowFieldEnabled()) return false;
        int packId = sim.swarmPacks().packIdOf(body);
        if (packId == 0) return false;
        SwarmPackPlans.Plan pack = sim.swarmPackPlans().planFor(packId);
        if (pack == null || pack.anchor == 0L) return false;
        int i = pack.indexOf(body);
        if (i < 0) return false;
        float x = sim.world().x(body);
        float y = sim.world().y(body);
        float ax = x - pack.anchorX;
        float ay = y - pack.anchorY;
        if (body != pack.anchor && ax * ax + ay * ay > SwarmPackPlans.LEASH * SwarmPackPlans.LEASH) return false;
        if (target != 0L) {
            float tx = sim.world().x(target) - x;
            float ty = sim.world().y(target) - y;
            if (tx * tx + ty * ty <= COMMIT_RANGE * COMMIT_RANGE) return false;
        }
        int tick = sim.getSimTickIndex();
        switch (pack.phase) {
            case TRAVEL:
                return runLane(body, pack, i, ax, ay, tick, sim);
            case STALK: {
                float pace = pack.flanker[i] ? 1f : 0.5f;
                return toGoal(body, pack, i, pace, sim);
            }
            case FEINT: {
                boolean in = (tick - pack.phaseTick) % SwarmPackPlans.FEINT_TICKS < SwarmPackPlans.FEINT_IN_TICKS;
                float pace = pack.flanker[i] ? 0.5f : in ? 1f : 0.8f;
                return toGoal(body, pack, i, pace, sim);
            }
            case RUSH:
                if (tick >= pack.commitTick[i]) return false;
                return toGoal(body, pack, i, 0.5f, sim);
            default:
                return false;
        }
    }

    private static boolean runLane(long body, SwarmPackPlans.Plan pack, int i, float ax, float ay,
                                   int tick, BattleSimulation sim) {
        int[] lane = pack.lane;
        if (Paths.cellCount(lane) < 2) return false;
        if (sim.movement().path(body) != lane) sim.setPath(body, lane);
        int rank = pack.rank[i];
        // A slow seeded weave about the member's place, so the fan breathes rather than holding a drill line.
        double swayT = tick * BattleSimulation.TICK_DT / pack.swayPeriod[i] + pack.gaitOffset[i] * 0.37;
        float sway = rank == 0 ? 0f : SWAY * pack.spacing * (float) Math.sin(2 * Math.PI * swayT);
        float lateral = rank * pack.spacing + sway;
        float ox = -pack.headingY * lateral;
        float oy = pack.headingX * lateral;
        float pace;
        if (rank == 0 && body == pack.anchor) {
            pace = ANCHOR_PACE;
        } else {
            float along = ax * pack.headingX + ay * pack.headingY;
            float place = -(SwarmPackPlans.DEPTH_PER_RANK * Math.abs(rank) + 0.6f) * pack.depthShare[i] * 1.5f;
            float ahead = along - place;
            pace = ahead > 0f ? Math.max(MIN_PACE, 1f - PACE_PER_CELL * ahead) : 1f;
        }
        pace *= gait(pack, i, tick);
        sim.movement().advanceAlongPath(sim.world(), body, BattleSimulation.TICK_DT, ox, oy, pace);
        return true;
    }

    /** The member's own stride rhythm: a slow seeded swing in pace, so no two lope in step. */
    static float gait(SwarmPackPlans.Plan pack, int i, int tick) {
        double t = tick * BattleSimulation.TICK_DT / pack.gaitPeriod[i] + pack.gaitOffset[i];
        return 1f - GAIT_SWING * 0.5f * (1f + (float) Math.sin(2 * Math.PI * t));
    }

    private static boolean toGoal(long body, SwarmPackPlans.Plan pack, int i, float pace, BattleSimulation sim) {
        int gx = pack.goalX[i];
        int gy = pack.goalY[i];
        float dx = gx + 0.5f - sim.world().x(body);
        float dy = gy + 0.5f - sim.world().y(body);
        if (dx * dx + dy * dy <= 0.5f * 0.5f) {
            sim.clearPath(body);
            return true;
        }
        SwarmMovement.ensurePath(body, gx, gy, false, sim);
        sim.movement().advanceAlongPath(sim.world(), body, BattleSimulation.TICK_DT, 0f, 0f, pace);
        return true;
    }
}
