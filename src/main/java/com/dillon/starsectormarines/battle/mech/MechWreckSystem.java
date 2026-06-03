package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.unit.DeathDispatcher;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;

/**
 * Death-event handler that drops a smoking wreck on the cell of any dead
 * chassis unit (a {@link MechLoadoutState}-carrying walker today; future
 * tanks/hovercraft the same way). Subscribes to the {@link DeathDispatcher};
 * fires once per mech death when the mailbox drains (the {@code DEMOLISH}
 * phase).
 *
 * <p>Replaces the former {@code HeavyWeapons.spawnMechWrecks} per-tick scan,
 * which walked the legacy {@code List<Entity>} every tick for just-died mechs
 * (gated by the {@link MechLoadoutState#wreckSpawned} latch). Every kill path —
 * primary fire, mech crossfire, marine rockets, flyby strafing — routes through
 * {@code DamageResolver.resolve}, which publishes a {@link DeathEvent}, so the
 * one death seam catches them all without the list scan. Same flip-to-wreck
 * shape as {@code TurretDemolitionSystem} / {@code HubDemolitionSystem}.
 *
 * <p>The {@code wreckSpawned} latch stays as a belt-and-suspenders double-fire
 * guard (a death publishes exactly once, so it can't fire twice via the
 * dispatcher) and as the idempotency marker for any other wreck path.
 *
 * <p>Sibling to other {@code *System} consumers — all dependencies
 * constructor-injected, no per-event state.
 */
public final class MechWreckSystem {

    private final EffectsService effects;
    private final ComponentStore<MechLoadoutState> mechLoadouts;

    public MechWreckSystem(EffectsService effects, ComponentStore<MechLoadoutState> mechLoadouts) {
        this.effects = effects;
        this.mechLoadouts = mechLoadouts;
    }

    /**
     * Death-event callback: a dead chassis unit leaves a smoking wreck at the
     * cell it fell on. Ignores non-mech deaths and a mech whose wreck already
     * spawned (the latter can't happen via the dispatcher — a death publishes
     * once — but the guard keeps the method safe if ever called twice).
     */
    public void onDeath(DeathEvent event) {
        Entity u = event.unit();
        // The loadout store survives registry release (keyed by id), so the
        // mech's component is still here even though the unit is gone. A
        // non-mech death has no entry → null, and we skip.
        MechLoadoutState m = mechLoadouts.get(u.entityId);
        if (m == null || m.wreckSpawned) return;
        // Read the death cell off the event snapshot: the unit is released by
        // the time this drains, so its Group-C cell accessors are fail-loud.
        effects.spawnSmokingWreck(event.cellX(), event.cellY());
        m.wreckSpawned = true;
        // Wreck spawned → the live-combat loadout is done. Detach it so the
        // mech-fire pass stops iterating a dead entity (the wreck decal is
        // owned by the effects layer now). Mirrors the Crashing lifecycle:
        // the component is removed once its terminal event fires.
        mechLoadouts.remove(u.entityId);
    }
}
