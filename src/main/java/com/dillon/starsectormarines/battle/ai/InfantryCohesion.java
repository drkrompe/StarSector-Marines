package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;

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
     * Returns the squad-centroid cell when the unit is more than
     * {@link #COHESION_RADIUS} cells from it; null otherwise (normal targeting
     * takes over). Solo units — no squad, or squad of one alive — always
     * return null. Reads the cached {@link Squad#centroidX} / {@link Squad#centroidY}
     * filled once per tick by the sim's alert-update pass; excludes self by
     * subtracting our contribution before averaging the remaining members.
     */
    public static int[] cohesionOverride(Unit self, BattleSimulation sim) {
        if (self.squadId == Unit.NO_SQUAD) return null;
        Squad squad = sim.getSquad(self.squadId);
        if (squad == null || squad.aliveMembers <= 1) return null;

        // squad.centroid is sum/count over all alive members including self.
        // Reconstruct the others-only centroid: (sum - self) / (count - 1).
        int othersCount = squad.aliveMembers - 1;
        float sumX = squad.centroidX * squad.aliveMembers - self.cellX;
        float sumY = squad.centroidY * squad.aliveMembers - self.cellY;
        float cx = sumX / othersCount;
        float cy = sumY / othersCount;
        float dx = cx - self.cellX;
        float dy = cy - self.cellY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= COHESION_RADIUS) return null;
        return new int[]{Math.round(cx), Math.round(cy)};
    }
}
