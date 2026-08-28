package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;

/**
 * Stateless per-tick sweep over the units whose armour pattern carries a
 * capability: drains their clocks, drops an expired effect, and decides when a
 * system is worth spending ({@code integral-armor-systems.md}).
 *
 * <p>A <b>System</b> (processor) — the live state lives on
 * {@link IntegralSystemService}, which this drives. It runs as its own sweep
 * rather than inside a behaviour's prep so a unit's clocks drain no matter what
 * that unit is doing; the cooldown-drain-during-a-long-approach lesson from
 * {@code InfantryUnitPrep.tickCooldowns} applies here for the same reason.
 *
 * <p><b>The use policy here is deliberately the crude one.</b> A breacher
 * spends its assist when it is actually moving and hostiles are close enough to
 * make that movement expensive — which is the doorway moment the capability
 * exists for, and nothing more clever. The authored AI-policy vocabulary that
 * special equipment uses is the intended home for this decision; until an
 * integral system declares one, this sweep is the whole policy and says so.
 */
public final class IntegralSystemSystem {

    /**
     * How close a hostile must be for crossing open ground to be worth a
     * charge. Deliberately wider than a marine's own reach — the assist is for
     * getting somewhere under fire, so the threat that justifies it is one that
     * can already shoot at the crossing.
     */
    static final float THREAT_RADIUS_CELLS = 12f;

    /** Below this, the unit is standing still and has nothing to charge through. */
    private static final float MOVING_EPSILON = 1e-3f;

    private final UnitRosterService rosterService;
    private final LongBucket nearbyHostiles = new LongBucket();

    public IntegralSystemSystem(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /** Drains every carrier's clocks, then offers the ready ones a reason to fire. */
    public void tick(float dt, BattleSimulation sim) {
        IntegralSystemService systems = rosterService.integralSystems();
        MovementService movement = rosterService.movement();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!systems.has(id)) continue;
            systems.tick(id, dt);
            if (!systems.canActivate(id)) continue;
            if (shouldActivate(id, systems, movement, sim)) systems.activate(id);
        }
    }

    private boolean shouldActivate(long id, IntegralSystemService systems,
                                   MovementService movement, BattleSimulation sim) {
        IntegralSystemDef def = systems.spec(id);
        if (def == null || def.effect() != IntegralSystemEffect.BREACHER_ASSIST) return false;
        if (!isMoving(id, movement)) return false;
        return hostileWithin(id, sim, THREAT_RADIUS_CELLS);
    }

    private static boolean isMoving(long id, MovementService movement) {
        float vx = movement.velX(id);
        float vy = movement.velY(id);
        return Math.abs(vx) > MOVING_EPSILON || Math.abs(vy) > MOVING_EPSILON;
    }

    private boolean hostileWithin(long id, BattleSimulation sim, float radius) {
        UnitSpatialIndex index = sim.getUnitIndex();
        if (index == null) return false;
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return false;
        nearbyHostiles.clear();
        index.gatherOtherFactionCombatants(
                sim.world().x(id), sim.world().y(id), radius, faction, nearbyHostiles);
        return nearbyHostiles.size > 0;
    }
}
