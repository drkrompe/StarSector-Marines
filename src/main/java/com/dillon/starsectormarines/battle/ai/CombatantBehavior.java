package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Default combat loop: acquire target, fire when in range with LOS, otherwise
 * path to a firing position. The behavior every unit had before the role
 * system landed — now with squad cohesion: if a squadmate has drifted more
 * than {@link #COHESION_RADIUS} cells from the squad center, they path
 * toward the centroid before resuming normal engagement, so fireteams move
 * as a group instead of scattering.
 *
 * <p>Cohesion is a soft leash: once within radius, normal targeting takes
 * over. A marine with a great firing position 10 cells out from the squad
 * will be pulled back; one with a position 5 cells out will hold.
 */
public final class CombatantBehavior implements UnitBehavior {

    public static final CombatantBehavior INSTANCE = new CombatantBehavior();

    /** Squadmate leash radius in cells. Outside this, the unit prioritizes rejoining the squad over picking a firing position. */
    public static final float COHESION_RADIUS = 12f;

    /** Probability that, after firing, a unit picks a different firing position. Models routine sidestepping between shots. */
    private static final float REPOSITION_CHANCE = 0.30f;

    private CombatantBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (u.target == null || !u.target.isAlive()) {
            u.target = TacticalScoring.findBestTarget(u, sim);
        }
        if (u.target == null) return; // nothing to do — usually a win-condition frame

        if (u.cooldownTimer > 0f) u.cooldownTimer -= BattleSimulation.TICK_DT;

        float dist = TacticalScoring.cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        boolean inRange = dist <= u.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        if (inRange && visible) {
            if (u.cooldownTimer <= 0f) {
                sim.fireShot(u, u.target);
                u.cooldownTimer = u.attackCooldown;
                if (sim.getRng().nextFloat() < REPOSITION_CHANCE) {
                    int[] firingPos = TacticalScoring.findFiringPosition(u, u.target, sim, u.cellX, u.cellY);
                    if (firingPos[0] != u.cellX || firingPos[1] != u.cellY) {
                        sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, firingPos[0], firingPos[1], sim.getOccupancyMap()));
                    }
                }
            }
            if (!u.path.isEmpty() && u.pathIdx < u.path.size()) {
                sim.advanceMovement(u);
            } else {
                u.moveProgress = 0f;
                u.renderX = u.cellX;
                u.renderY = u.cellY;
            }
        } else {
            // Out of range or no LOS. If we've drifted away from our squad,
            // path back toward the centroid first — the firing-position picker
            // doesn't know about cohesion, so it'd happily send us solo across
            // the map toward a distant target.
            if (u.moveProgress == 0f) {
                int[] dest = cohesionOverride(u, sim);
                if (dest == null) dest = TacticalScoring.findFiringPosition(u, u.target, sim);
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, dest[0], dest[1], sim.getOccupancyMap()));
            }
            sim.advanceMovement(u);
        }
    }

    /**
     * Returns the squad-centroid cell when the unit is more than
     * {@link #COHESION_RADIUS} cells from it; null otherwise (normal targeting
     * takes over). Solo units — no squad, or squad of one alive — always
     * return null. The centroid excludes self so a lone surviving squadmate
     * doesn't try to walk toward themselves.
     */
    private static int[] cohesionOverride(Unit self, BattleSimulation sim) {
        if (self.squadId == Unit.NO_SQUAD) return null;
        Squad squad = sim.getSquad(self.squadId);
        if (squad == null) return null;

        float sumX = 0f, sumY = 0f;
        int count = 0;
        for (Unit u : sim.getUnits()) {
            if (u == self || !u.isAlive()) continue;
            if (u.squadId != self.squadId) continue;
            sumX += u.cellX;
            sumY += u.cellY;
            count++;
        }
        if (count == 0) return null;

        float cx = sumX / count;
        float cy = sumY / count;
        float dx = cx - self.cellX;
        float dy = cy - self.cellY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= COHESION_RADIUS) return null;
        return new int[]{Math.round(cx), Math.round(cy)};
    }
}
