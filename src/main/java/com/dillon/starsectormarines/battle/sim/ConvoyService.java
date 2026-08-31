package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * Data owner + factory for convoy ground vehicles as world entities — the ground
 * twin of the air adoption path. Owns the birth / death of a vehicle's world
 * entity ({@link #spawn}/{@link #despawn}) and the by-id access to its
 * {@code GROUND_IDENTITY} / {@code GROUND_KINEMATICS} / {@code GROUND_TURRET} /
 * {@code VEHICLE_MISSION} / {@code HEALTH} / {@code ARMOR} columns and its
 * live-vehicle/persistent-wreck id backbone.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code ecs-nouns.md}): it owns the
 * ground-craft component data and the spawn seam; {@link com.dillon.starsectormarines.battle.vehicle.GroundSystem}
 * (the System) reaches it via {@code roster.convoy()} and reads by id — no
 * {@link World} hop (the World facade is deprecated for new migrated state,
 * [[feedback_world_facade_deprecated]]). It takes the {@link UnitRosterService}
 * because minting must go through the single shared {@code nextId} authority
 * ({@code roster.allocateVehicle}) — self-minting would reopen the dual-mint trap.
 *
 * <p><b>Component-native.</b> A vehicle's identity ({@link VehicleType}/{@link Faction}),
 * kinematics ({@link GroundBody}), durability, and turret ({@link GroundTurret})
 * each live in their own column; the {@link VehicleMission} bag carries only
 * lifecycle / path state and holds none of them (the air {@code ShuttleMission}
 * shape). {@link #spawn}
 * is the factory: it builds the body + optional turret from the variant, seeds
 * every column, and returns the id — callers hold no vehicle object, only the id
 * and this service.
 *
 * <p>Vehicles are world-resident only — never in the dense ground roster, so grid
 * systems (occupancy / spatial index / fog) skip them for free. Serial-only (the
 * convoy tick runs in the serial GROUND_SYSTEM phase), so {@link #despawn}'s
 * {@code destroy} is safe at the tick barrier without a {@code CommandBuffer}.
 */
public final class ConvoyService implements BodyCarrier {

    private final UnitRosterService roster;
    /** World-resident live vehicles and persistent wrecks; N is normally 1-4. */
    private final List<Long> entityIds = new ArrayList<>();

    /** Monotonic suffix for the greppable {@code IDENTITY} name; never recycled, so two chassis never share one. */
    private int spawnSequence;

    /** Setup-time cycle break: {@code GroundSystem} is constructed after the damage service. */
    private LongConsumer destructionSink;

    public ConvoyService(UnitRosterService roster) {
        this.roster = roster;
    }

    /**
     * Spawns one convoy vehicle: builds its {@link GroundBody} (teleported to the
     * inbound queue's first waypoint, facing the second) + optional {@link GroundTurret}
     * from {@code type}, mints a world entity from the shared id authority
     * ({@link UnitRosterService#allocateVehicle}), seeds the ground-craft columns
     * ({@code {GROUND_IDENTITY, GROUND_KINEMATICS, VEHICLE_MISSION,
     * VEHICLE_CONTROL, HEALTH, ARMOR}} + {@code GROUND_TURRET}
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
        GroundTurret turret = type.hasTurretWeapon()
                ? new GroundTurret(type.turretStructure().mount.ammoCapacity) : null;

        // VEHICLE_MISSION (mission bag) + VEHICLE_CONTROL (motion-control bag) are universal;
        // GROUND_TURRET is present only when armed.
        ComponentType[] archetype = (turret != null)
                ? new ComponentType[]{c.IDENTITY, c.GROUND_IDENTITY, c.GROUND_KINEMATICS,
                    c.VEHICLE_MISSION, c.VEHICLE_CONTROL, c.GROUND_TURRET, c.HEALTH, c.ARMOR}
                : new ComponentType[]{c.IDENTITY, c.GROUND_IDENTITY, c.GROUND_KINEMATICS,
                    c.VEHICLE_MISSION, c.VEHICLE_CONTROL, c.HEALTH, c.ARMOR};
        long id = roster.allocateVehicle(archetype);
        // IDENTITY makes the chassis a body the ordinary grid walks can read:
        // faction, archetype, and a greppable name, exactly as a turret or a
        // parked airframe carries them. It deliberately brings no POSITION,
        // so the occupancy and separation queries keyed on that still skip it.
        world.setObject(id, c.IDENTITY, BattleComponents.IDENTITY_TYPE, UnitType.GROUND_VEHICLE);
        world.setObject(id, c.IDENTITY, BattleComponents.IDENTITY_FACTION, faction);
        world.setObject(id, c.IDENTITY, BattleComponents.IDENTITY_NAME,
                type.name().toLowerCase(Locale.ROOT) + "-" + (++spawnSequence));
        world.setObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE, type);
        world.setObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION, faction);
        world.setObject(id, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY, body);
        world.setObject(id, c.VEHICLE_MISSION, BattleComponents.VEHICLE_MISSION_STATE, mission);
        world.setObject(id, c.VEHICLE_CONTROL, BattleComponents.VEHICLE_CONTROL_STATE, new VehicleControlComponent());
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_HP, type.maxStructure);
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_MAX_HP, type.maxStructure);
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_DAMAGE_TAKEN_MULT, 1f);
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_INCOMING_ACCURACY_MULT,
                type.incomingAccuracyMult);
        world.setFloat(id, c.ARMOR, BattleComponents.ARMOR_CURRENT, type.maxArmor);
        world.setFloat(id, c.ARMOR, BattleComponents.ARMOR_MAX, type.maxArmor);
        world.setFloat(id, c.ARMOR, BattleComponents.ARMOR_RATING, type.armorRating);
        if (turret != null) {
            world.setObject(id, c.GROUND_TURRET, BattleComponents.GROUND_TURRET_STATE, turret);
        }
        entityIds.add(id);
        roster.bodies().admit(id);
        // A chassis that spawns already on the map is a body somebody could be
        // looking at this tick; one still off-map joins at the next rebuild.
        if (isTargetable(id)) roster.indexVehicle(id);
        return id;
    }

    /**
     * Destroys the vehicle's world entity — called at terminal {@link VehicleState#GONE}.
     * One {@code destroy} drops all its columns. No-op on {@code 0L} (never adopted) or an
     * already-destroyed id.
     */
    public void despawn(long id) {
        if (id == 0L) return;
        roster.unindexVehicle(id);
        roster.entityWorld().destroy(id);
        entityIds.remove(id);
    }

    /**
     * How many convoy entities exist, wrecks included. Paired with
     * {@link #vehicleAt} for the per-unit target scans, which run for every
     * combatant every tick and cannot afford {@link #entityIds}'s copy.
     */
    public int vehicleCount() {
        return entityIds.size();
    }

    /** The convoy entity at {@code index} in {@code [0, vehicleCount())}. */
    public long vehicleAt(int index) {
        return entityIds.get(index);
    }

    /** Snapshot of every convoy entity, including persistent wrecks. */
    public long[] entityIds() {
        long[] ids = new long[entityIds.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = entityIds.get(i);
        return ids;
    }

    public boolean isVehicle(long id) {
        return roster.entityWorld().has(id, roster.components().GROUND_IDENTITY);
    }

    @Override
    public boolean owns(long id) {
        return isVehicle(id);
    }

    /** Visits every convoy entity, live hulls and persistent wrecks alike. */
    @Override
    public void forEachBody(LongConsumer visitor) {
        for (int i = 0, n = entityIds.size(); i < n; i++) visitor.accept(entityIds.get(i));
    }

    /** Setup-time cycle break: {@code GroundSystem} is constructed after the damage service. */
    public void setDestructionSink(LongConsumer sink) {
        this.destructionSink = sink;
    }

    /** A hull out of structure stops and becomes scenery; {@code GroundSystem} owns the transition. */
    @Override
    public void destroy(long id) {
        if (destructionSink != null) destructionSink.accept(id);
    }

    /** A chassis is in the battle while it is on the map, whole, and alive. */
    @Override
    public boolean isPresent(long id) {
        VehicleMission mission = mission(id);
        return mission != null && mission.isVisible() && mission.state != VehicleState.WRECKED
                && roster.isAliveById(id);
    }

    /** Never: a chassis drives. Nothing about a truck is a question of altitude. */
    @Override
    public boolean isAirborne(long id) {
        return false;
    }

    /**
     * How far this chassis can shoot, in cells — its turret weapon's range, or
     * {@code 0} for an unarmed hull. A vehicle carries no {@code COMBAT}
     * component (its turret runs its own aim loop rather than the infantry
     * fire path), so the threat scans read reach through here instead of the
     * fail-loud {@code World.attackRange}. An unarmed truck answering zero is
     * the right answer, not a missing one: it is a thing to shoot at, not a
     * thing to take cover from.
     */
    @Override
    public float weaponRange(long id) {
        VehicleType type = vehicleType(id);
        if (type == null || !type.hasTurretWeapon()) return 0f;
        StructureDef structure = type.turretStructure();
        return structure != null ? structure.mount.weapon.range : 0f;
    }

    /** Circular contact radius used by ballistic and blast broad phases. */
    @Override
    public float targetRadius(long id) {
        VehicleType type = vehicleType(id);
        return type != null ? Math.max(type.visualLengthCells, type.visualWidthCells) * 0.5f : 0f;
    }

    @Override
    public float hitHalfHeight(long id) {
        VehicleType type = vehicleType(id);
        return type != null ? type.hitHalfHeight : 0f;
    }

    public float structure(long id) { return roster.world().hp(id); }
    public float maxStructure(long id) { return roster.world().maxHp(id); }
    public float armor(long id) { return roster.world().armor(id); }
    public float maxArmor(long id) { return roster.world().maxArmor(id); }
    public float armorRating(long id) { return roster.world().armorRating(id); }

    @Override
    public float velocityX(long id) {
        GroundBody body = body(id);
        if (body == null) return 0f;
        return -(float) Math.sin(Math.toRadians(body.facingDegrees)) * body.speed;
    }

    @Override
    public float velocityY(long id) {
        GroundBody body = body(id);
        if (body == null) return 0f;
        return (float) Math.cos(Math.toRadians(body.facingDegrees)) * body.speed;
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
    @Override
    public Faction faction(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.GROUND_IDENTITY)
                ? (Faction) world.getObject(id, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION)
                : null;
    }

    /**
     * The {@link VehicleMission} bag for {@code id} (the {@code VEHICLE_MISSION} payload),
     * or {@code null} if {@code id} isn't a live ground craft (has-gated). The
     * id→mission resolution lets {@code GroundSystem} consume this service's
     * id backbone instead of maintaining a second list of handles.
     */
    public VehicleMission mission(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.VEHICLE_MISSION)
                ? (VehicleMission) world.getObject(id, c.VEHICLE_MISSION, BattleComponents.VEHICLE_MISSION_STATE)
                : null;
    }

    /** The vehicle's motion-control state ({@code VEHICLE_CONTROL} payload), or {@code null} if {@code id} isn't a live ground craft (has-gated). */
    public VehicleControlComponent control(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.VEHICLE_CONTROL)
                ? (VehicleControlComponent) world.getObject(id, c.VEHICLE_CONTROL, BattleComponents.VEHICLE_CONTROL_STATE)
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
