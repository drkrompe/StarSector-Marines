package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code HUB_STATE} component — typed by-id access to a
 * drone hub's live spawn-cadence state in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it. A consumer reaches it
 * via {@code sim.hubState()} / {@code roster.hubState()} and calls
 * {@code hubState.spawnCooldown(id)} / {@code hubState.incrementDronesLaunched(id)}
 * directly — no {@link World} hop.
 *
 * <p>{@code HUB_STATE} is OPTIONAL — <b>presence IS "is a live drone hub"</b>:
 * {@link #isHub} is the presence check. Unlike {@code HOME}, the field readers here
 * are not fail-loud gated behind a separate doc note because every caller already
 * holds a known drone-hub id (the type-tag {@code UnitType.isDroneHub()} check
 * happens upstream); still, a read on a unit with no {@code HUB_STATE} throws,
 * same as every other optional-component data owner.
 *
 * <p><b>Single-writer per hub.</b> The hub's spawn cadence ({@code DroneHubBehavior})
 * and launch bookkeeping ({@code DroneSpawner}) run inside the <em>parallel</em>
 * {@code UPDATE_UNITS} dispatch (whence {@code DroneSpawner} routes its drone
 * through {@code queueSpawn}), but a hub only ever writes its <em>own</em>
 * {@code HUB_STATE} — each unit is processed by exactly one worker — so these
 * unsynchronized reads/writes are safe exactly as the old per-instance field
 * writes on the dissolved hub subclass were.
 */
public final class HubStateService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public HubStateService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} is a live drone hub (carries HUB_STATE). Gate the other reads on this. */
    public boolean isHub(long id) { return entityWorld.has(id, components.HUB_STATE); }

    /** Sim-seconds until {@code id}'s next drone-launch attempt. Fail-loud on a non-hub; gate on {@link #isHub}. */
    public float spawnCooldown(long id) { return entityWorld.getFloat(id, components.HUB_STATE, BattleComponents.HUB_STATE_SPAWN_COOLDOWN); }

    /** Sets {@code id}'s sim-seconds until the next drone-launch attempt. */
    public void setSpawnCooldown(long id, float v) { entityWorld.setFloat(id, components.HUB_STATE, BattleComponents.HUB_STATE_SPAWN_COOLDOWN, v); }

    /** Lifetime count of drones {@code id} has launched. */
    public int dronesLaunched(long id) { return entityWorld.getInt(id, components.HUB_STATE, BattleComponents.HUB_STATE_DRONES_LAUNCHED); }

    /**
     * Bumps {@code id}'s lifetime launch count by one and returns the new value —
     * folded into each launched drone's greppable name ({@code "drone-" + hubName + "-" + n},
     * where {@code hubName} is {@code IdentityService.name(hubId)}).
     */
    public int incrementDronesLaunched(long id) {
        int n = dronesLaunched(id) + 1;
        entityWorld.setInt(id, components.HUB_STATE, BattleComponents.HUB_STATE_DRONES_LAUNCHED, n);
        return n;
    }

    /** The squad id {@code id}'s drones join, or {@link Squad#NO_SQUAD} (-1) if none minted yet. */
    public int droneSquadId(long id) { return entityWorld.getInt(id, components.HUB_STATE, BattleComponents.HUB_STATE_DRONE_SQUAD_ID); }

    /** Sets the squad id {@code id}'s drones join — minted lazily on the hub's first successful launch. */
    public void setDroneSquadId(long id, int v) { entityWorld.setInt(id, components.HUB_STATE, BattleComponents.HUB_STATE_DRONE_SQUAD_ID, v); }

    /** True iff {@code id} has already minted a drone squad ({@link #droneSquadId} is not {@link Squad#NO_SQUAD}). */
    public boolean hasDroneSquad(long id) { return droneSquadId(id) != Squad.NO_SQUAD; }
}
