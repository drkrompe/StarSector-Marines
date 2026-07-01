package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner + factory for convoy ground vehicles as world entities — the ground
 * twin of the air adoption path. Owns the birth / death of a vehicle's world
 * entity ({@link #spawn}/{@link #despawn}) and the by-id access to its
 * {@code GROUND_IDENTITY} / {@code GROUND_KINEMATICS} / {@code GROUND_TURRET} /
 * {@code VEHICLE_MISSION} columns.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/vehicle-into-world.md}): it owns the
 * ground-craft component data and the spawn seam; {@link com.dillon.starsectormarines.battle.vehicle.GroundSystem}
 * (the System) reaches it via {@code roster.convoy()} and reads by id — no
 * {@link World} hop (the World facade is deprecated for new migrated state,
 * [[feedback_world_facade_deprecated]]). It takes the {@link UnitRosterService}
 * because minting must go through the single shared {@code nextId} authority
 * ({@code roster.allocateVehicle}) — self-minting would reopen the dual-mint trap.
 *
 * <p><b>Component-native.</b> A vehicle's identity ({@link VehicleType}/{@link Faction}),
 * kinematics ({@link GroundBody}), and turret ({@link GroundTurret}) each live in
 * their own column; the {@link VehicleMission} bag carries only lifecycle / path
 * state and holds none of them (the air {@code ShuttleMission} shape). {@link #spawn}
 * is the factory: it builds the body + optional turret from the variant, seeds
 * every column, and returns the id — callers hold no vehicle object, only the id
 * and this service.
 *
 * <p>Vehicles are world-resident only — never in the dense ground roster, so grid
 * systems (occupancy / spatial index / fog) skip them for free. Serial-only (the
 * convoy tick runs in the serial GROUND_SYSTEM phase), so {@link #despawn}'s
 * {@code destroy} is safe at the tick barrier without a {@code CommandBuffer}.
 */
public final class ConvoyService {

    private final UnitRosterService roster;

    public ConvoyService(UnitRosterService roster) {
        this.roster = roster;
    }

    /**
     * Spawns one convoy vehicle: builds its {@link GroundBody} (teleported to the
     * inbound queue's first waypoint, facing the second) + optional {@link GroundTurret}
     * from {@code type}, mints a world entity from the shared id authority
     * ({@link UnitRosterService#allocateVehicle}), seeds the ground-craft columns
     * ({@code {GROUND_IDENTITY, GROUND_KINEMATICS, VEHICLE_MISSION}} + {@code GROUND_TURRET}
     * iff armed), and returns the entity id. The caller hands a freshly-built
     * {@code mission} (single-use — one mission, one spawn), the air-spawn shape.
     */
    public long spawn(VehicleType type, Faction faction, VehicleMission mission) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();

        // Build the kinematics + (optional) turret the mission is agnostic of.
        GroundBody body = type.createBody();
        float spawnX = mission.inboundX[0], spawnY = mission.inboundY[0];
        float nextX = mission.inboundX[1], nextY = mission.inboundY[1];
        body.teleport(spawnX, spawnY, AirBody.facingToward(nextX - spawnX, nextY - spawnY));
        GroundTurret turret = type.hasTurretWeapon() ? new GroundTurret(type.turretKind.startingAmmo) : null;

        // VEHICLE_MISSION (the mission bag) is universal; GROUND_TURRET is present only when armed.
        ComponentType[] archetype = (turret != null)
                ? new ComponentType[]{c.GROUND_IDENTITY, c.GROUND_KINEMATICS, c.VEHICLE_MISSION, c.GROUND_TURRET}
                : new ComponentType[]{c.GROUND_IDENTITY, c.GROUND_KINEMATICS, c.VEHICLE_MISSION};
        long id = roster.allocateVehicle(archetype);
        world.setObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE, type);
        world.setObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION, faction);
        world.setObject(id, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY, body);
        world.setObject(id, c.VEHICLE_MISSION, BattleComponents.VEHICLE_MISSION_STATE, mission);
        if (turret != null) {
            world.setObject(id, c.GROUND_TURRET, BattleComponents.GROUND_TURRET_STATE, turret);
        }
        return id;
    }

    /**
     * Destroys the vehicle's world entity — called at terminal {@link VehicleState#GONE}.
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

    /**
     * The {@link VehicleMission} bag for {@code id} (the {@code VEHICLE_MISSION} payload),
     * or {@code null} if {@code id} isn't a live ground craft (has-gated). The id→mission
     * resolution that lets {@code GroundSystem} keep a {@code List<Long>} backbone instead
     * of a side {@code List} of handles.
     */
    public VehicleMission mission(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.VEHICLE_MISSION)
                ? (VehicleMission) world.getObject(id, c.VEHICLE_MISSION, BattleComponents.VEHICLE_MISSION_STATE)
                : null;
    }

    /** The vehicle's live turret state, or {@code null} if unarmed / not a live ground craft (has-gated, presence == armed). */
    public GroundTurret turret(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.GROUND_TURRET)
                ? (GroundTurret) world.getObject(id, c.GROUND_TURRET, BattleComponents.GROUND_TURRET_STATE)
                : null;
    }
}
