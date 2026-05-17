package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Narrow contract the weapon subsystems ({@link InfantryWeapons}, and the
 * heavy-weapons system that'll land in a follow-up pass) need from the
 * surrounding simulation. Kept small on purpose: every method here is a real
 * call site, not a "the weapon system might want this someday" surface.
 * Adding to it should require a real need.
 *
 * <p>{@link com.dillon.starsectormarines.battle.BattleSimulation} implements
 * this directly. Weapon-system unit tests can supply a stub that records calls
 * without dragging in the full sim.
 *
 * <p>Parallels {@link com.dillon.starsectormarines.battle.air.AirSimContext}
 * — same pattern: the sim owns subsystem instances and exposes a curated set
 * of primitives the subsystems are allowed to call back into.
 */
public interface WeaponSimContext {

    /** The navigation grid — used for cover lookups, LOS checks, in-bounds tests. */
    NavigationGrid getGrid();

    /** Shared RNG — threaded through every probabilistic decision so deterministic-seed runs reproduce. */
    Random getRng();

    /** All units in the simulation. Reads only — mutation goes through more specific methods. */
    List<Unit> getUnits();

    /**
     * Applies {@code damage} (pre-cover) to {@code target}. The implementation
     * looks up cover at the target's cell, applies the cover damage reduction
     * curve, multiplies by {@code vsTurretMult} when the target is a turret,
     * subtracts from HP, and records a death (idempotently) if the hit was
     * the killing blow.
     */
    void applyDamage(Unit target, float damage, float vsTurretMult);

    /**
     * Adds a {@link ShotEvent} to the simulation's active + this-frame lists.
     * Active drives the renderer's projectile lerp + impact-on-expire path;
     * this-frame drives one-shot audio (so the fire SFX plays exactly once
     * per round even though the event lives for its full flight time).
     */
    void postShot(ShotEvent shot);

    /**
     * Queues a {@link PendingDetonation} on the AoE pipeline. The sim ticks it
     * down each frame and applies splash + wall damage on arrival. Paired with
     * a {@link #postShot}-emitted projectile so visuals + damage land together.
     */
    void queueDetonation(PendingDetonation det);

    /**
     * Rolls fall-back on a unit that just took a hit. No-op if the target is
     * dead, a turret (those fight to the last HP), already breaking contact,
     * or if the RNG fails the chance gate. Sets the fall-back fields and
     * clears the unit's path so the next behavior tick re-paths to the
     * fall-back cell.
     */
    void rollFallbackOnHit(Unit target);
}
