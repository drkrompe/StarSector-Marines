package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Drone;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.weapons.FireStance;

/**
 * Per-tick driver for a {@link Drone}: reuses {@link TurretAim} to acquire +
 * track + fire at the nearest visible enemy combatant within the drone's
 * weapon range. Fires through {@link BattleSimulation#fireShot}, which routes
 * to the existing {@code InfantryWeapons} pipeline since the drone has a
 * {@code primaryWeapon} assigned.
 *
 * <p>No patrol motion yet — the drone fires from wherever it spawned. The
 * matching kinematic + roof-aware LoS slice lands in a follow-up commit.
 */
public final class DroneBehavior implements UnitBehavior {

    public static final DroneBehavior INSTANCE = new DroneBehavior();

    private DroneBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (!(u instanceof Drone)) return;
        Drone d = (Drone) u;
        if (!d.isAlive()) return;

        TurretAim.State s = new TurretAim.State();
        s.originCellX = d.cellX;
        s.originCellY = d.cellY;
        s.originX = d.body.x;
        s.originY = d.body.y;
        s.faction = d.faction;
        s.squadId = d.squadId;
        s.excludeFromCrowding = d;
        s.facingDegrees = d.body.facingDegrees;
        s.turnRateDegPerSec = Drone.TURN_RATE_DEG_PER_SEC;
        s.attackRange = d.attackRange;
        s.minRange = 0f;
        s.cooldownTimer = d.cooldownTimer;
        s.attackCooldown = d.attackCooldown;
        s.target = d.target;

        TurretAim.tick(s, sim, BattleSimulation.TICK_DT);

        d.body.facingDegrees = s.facingDegrees;
        d.cooldownTimer = s.cooldownTimer;
        d.target = s.target;

        if (s.fireThisTick && s.target != null && s.target.isAlive()) {
            sim.fireShot(d, s.target, FireStance.STANCED);
            // Latch the burst — InfantryWeapons.tick pumps the remaining
            // rounds at the weapon's spacing. Drones don't move yet, so
            // STANCED is always the correct stance for the follow-ups.
            d.beginBurst(s.target);
        }
    }
}
