package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Stateless per-tick sweep over the actors carrying a screen: points it, drains
 * its clock, and drops it the moment the clock runs out — the other way a
 * screen ends, its soak pool running dry, belongs to the damage path
 * ({@code combat-durability-nouns.md}).
 *
 * <p>A <b>System</b> (processor) — the live state lives on
 * {@link MitigationService}, which this drives. It is deliberately separate from
 * whatever raised the screen: mitigation is a durability noun, and the next
 * source of one (a deployable shield, a vehicle's spaced plate) must get the
 * arc, the clock, and the expiry for free rather than reimplementing them.
 *
 * <p><b>The facing is simulation state.</b> The screen points where the wearer
 * is pushing while they are actually moving, and at what they are shooting when
 * they are not. That ordering is what makes the standing rule bite: a breacher
 * who turns to deal with something behind them loses the front they were
 * covering, in the same tick they turn.
 */
public final class MitigationSystem {

    private final UnitRosterService rosterService;

    public MitigationSystem(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Runs before the systems that can raise a screen, so a grant made this
     * tick spends its whole authored duration rather than losing its first
     * tick to this drain.
     */
    public void tick(float dt) {
        MitigationService screens = rosterService.mitigations();
        MovementService movement = rosterService.movement();
        CombatService combat = rosterService.combat();
        World world = rosterService.world();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            // Every carrier, not only the ones holding a screen: the mark left
            // by a screen that broke has to drain too, and it outlives the
            // screen by construction.
            if (!screens.has(id)) continue;
            if (screens.isActive(id)) aim(id, screens, movement, combat, world);
            screens.tick(id, dt);
        }
    }

    /**
     * Points the screen from the wearer's own simulation state. Never reads a
     * presentation facing: the render-tier torso angle is authored at the tail
     * of the tick from the same inputs and is free to smooth or lag, and a
     * screen that resolved against a smoothed angle would refuse damage the
     * wearer had already turned away from.
     */
    private void aim(long id, MitigationService screens, MovementService movement,
                     CombatService combat, World world) {
        if (movement.has(id)) {
            float vx = movement.velX(id);
            float vy = movement.velY(id);
            if (vx * vx + vy * vy >= FacingSystem.MIN_TRAVEL_SPEED * FacingSystem.MIN_TRAVEL_SPEED) {
                screens.face(id, AirBody.facingToward(vx, vy));
                return;
            }
        }
        if (!combat.has(id)) return;
        long target = combat.targetId(id);
        if (target == 0L || !rosterService.isAliveById(target)) return;
        float dx = world.x(target) - world.x(id);
        float dy = world.y(target) - world.y(id);
        if (dx == 0f && dy == 0f) return;
        screens.face(id, AirBody.facingToward(dx, dy));
    }
}
