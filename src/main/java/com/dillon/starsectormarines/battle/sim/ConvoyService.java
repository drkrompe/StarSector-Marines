package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for convoy ground vehicles as world entities — the ground twin of the
 * air adoption path. Owns the birth / death of a vehicle's world entity
 * ({@link #spawn}/{@link #despawn}) and the by-id access to its
 * {@code GROUND_IDENTITY} / {@code GROUND_KINEMATICS} columns.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/vehicle-into-world.md}): it owns the ground-craft
 * component data and the adoption seam; {@link GroundSystem}-analog processors reach it
 * via {@code roster.convoy()} and read by id — no {@link World} hop (the World facade is
 * deprecated for new migrated state, [[feedback_world_facade_deprecated]]). It takes the
 * {@link UnitRosterService} because minting must go through the single shared {@code nextId}
 * authority ({@code roster.allocateVehicle}) — self-minting would reopen the dual-mint trap.
 *
 * <p><b>Aliasing phase.</b> Today {@link #spawn} seeds the columns with the <em>same</em>
 * {@link VehicleType}/{@link Faction}/{@link GroundBody} instances the {@link Vehicle}
 * handle holds, so the handle stays authoritative for lifecycle / turret / deboard state
 * and every existing consumer compiles unchanged; the world entity carries identity +
 * live pose (the body is one shared instance the tick mutates). Later phases extract a
 * {@code VehicleMission} bag onto a {@code VEHICLE_MISSION} column and dissolve the handle.
 *
 * <p>Vehicles are world-resident only — never in the dense ground roster, so grid systems
 * (occupancy / spatial index / fog) skip them for free. Serial-only (the convoy tick runs
 * in the serial GROUND_SYSTEM phase), so {@link #despawn}'s {@code destroy} is safe at the
 * tick barrier without a {@code CommandBuffer}.
 */
public final class ConvoyService {

    private final UnitRosterService roster;

    public ConvoyService(UnitRosterService roster) {
        this.roster = roster;
    }

    /**
     * Adopts {@code v} into the entity world as a ground-craft entity
     * ({@code {GROUND_IDENTITY, GROUND_KINEMATICS}}), seeding the columns with the
     * handle's own type / faction / body instances (aliasing), and stamps
     * {@link Vehicle#entityId}. Returns the minted id (shared {@code nextId} authority).
     */
    public long spawn(Vehicle v) {
        // Fail loud on a double-adopt (parity with UnitRosterService.allocate): a second
        // spawn would mint a fresh id, overwrite entityId, and orphan the first world
        // entity (no longer reachable to despawn → leak). One handle == one adoption.
        if (v.entityId != 0L) {
            throw new IllegalStateException("vehicle already adopted (entityId=" + v.entityId + ") — double spawn");
        }
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        long id = roster.allocateVehicle(new ComponentType[]{c.GROUND_IDENTITY, c.GROUND_KINEMATICS});
        world.setObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE, v.type);
        world.setObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION, v.faction);
        world.setObject(id, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY, v.body);
        v.entityId = id;
        return id;
    }

    /**
     * Destroys the vehicle's world entity — called at terminal {@link Vehicle.State#GONE}.
     * One {@code destroy} drops all its columns. No-op on {@code 0L} (never adopted) or an
     * already-destroyed id.
     */
    public void despawn(long id) {
        if (id == 0L) return;
        roster.entityWorld().destroy(id);
    }

    /** The vehicle's kinematic body, or {@code null} if {@code id} isn't a live ground craft (has-gated). */
    public GroundBody body(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.GROUND_KINEMATICS)
                ? (GroundBody) world.getObject(id, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY)
                : null;
    }

    /** The vehicle's variant, or {@code null} if {@code id} isn't a live ground craft (has-gated). */
    public VehicleType vehicleType(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.GROUND_IDENTITY)
                ? (VehicleType) world.getObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE)
                : null;
    }

    /** The vehicle's faction, or {@code null} if {@code id} isn't a live ground craft (has-gated). */
    public Faction faction(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.GROUND_IDENTITY)
                ? (Faction) world.getObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION)
                : null;
    }
}
