package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Test-only helpers for unit lifecycle that mirror production semantics
 * the test fixtures can't get for free.
 *
 * <p>Pre-existing tests killed units with a direct {@code u.hp = 0f} write.
 * Production death (in
 * {@link com.dillon.starsectormarines.battle.damage.DamageResolver} and the
 * drone-hub cascade) does two things: drives hp negative AND releases the
 * entity from the {@link UnitRegistry} via
 * {@link BattleSimulation#releaseFromRegistry}. Tests that only do the first
 * leave a dead-but-still-registered unit, which means readers using the long-id
 * resolve path ({@link BattleSimulation#targetOf}, {@link BattleSimulation#resolveUnit})
 * still surface the corpse — only the legacy {@code u.isAlive()} follow-up
 * filters it out. {@link #kill} closes that contract gap so test fixtures
 * match production: after the call, {@code sim.resolveUnit(u.entityId)}
 * returns {@code null} the same way it would after a real damage kill.
 */
public final class TestUnits {

    private TestUnits() {}

    /**
     * Kill {@code u} the same way the production damage path does: drive hp
     * to 0 and release the dense-registry entry. The legacy {@code units}
     * list still keeps the dead unit (matching prod's
     * {@code DamageResolver.resolve} path), so any test still iterating
     * {@code sim.getUnits()} sees the corpse — but registry-resolve readers
     * see {@code null}.
     */
    public static void kill(BattleSimulation sim, Unit u) {
        u.hp = 0f;
        sim.releaseFromRegistry(u.entityId);
    }
}
