package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only helpers for unit lifecycle that mirror production semantics
 * the test fixtures can't get for free.
 *
 * <p>Pre-existing tests killed units with a direct {@code u.hp = 0f} write.
 * Production death (in
 * {@link com.dillon.starsectormarines.battle.combat.DamageResolver} and the
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
     * to 0 and release the dense-registry entry (swap-and-pop). After the call
     * the unit is gone from the live registry and {@code sim.resolveUnit(
     * u.entityId)} returns {@code null}, exactly as after a real damage kill;
     * the corpse's post-death state lives in the component stores. A caller
     * still holding the {@code Unit} reference (e.g. from {@link #snapshot})
     * can read its frozen hp via the release snapshot.
     */
    public static void kill(BattleSimulation sim, Unit u) {
        u.setHp(0f);
        sim.releaseFromRegistry(u.entityId);
    }

    /**
     * Snapshot of the currently-live units in dense-registry order, as a fresh
     * {@code List<Unit>}. Replaces the old {@code sim.getUnits()} for tests that
     * index ({@code .get(i)}) or iterate the roster: capture it <em>once</em>
     * before any {@link #kill}, and the held references stay stable across
     * subsequent kills — mirroring how the legacy live+dead list kept dead
     * entries at their slots. (Re-snapshotting after a kill reflects swap-and-pop
     * reordering, so don't re-take it mid-sequence when stable indices matter.)
     */
    public static List<Unit> snapshot(BattleSimulation sim) {
        List<Unit> out = new ArrayList<>(sim.liveUnitCount());
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            out.add(sim.liveUnitAt(i));
        }
        return out;
    }
}
