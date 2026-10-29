package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.HullTurretSection;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearances;
import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.RotaryFeed;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleStance;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleMount;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** The drones a hull's kits add to its bay, by entity id; only hulls that carry such a kit are here. */
    private final Map<Long, List<DroneRole>> kitBays = new LinkedHashMap<>();

    /** Setup-time hook run once for every hull set down, so the battle can enrol it as a fog contributor. */
    private LongConsumer spawnSink;

    /** Entity id to the mech-bay vehicle it realizes; only company vehicles are here. */
    private final Map<Long, String> companyVehicles = new LinkedHashMap<>();

    /** Held footprint masks, so a route question does not erode the whole map again. */
    private final VehicleClearances clearances = new VehicleClearances();

    public ConvoyService(UnitRosterService roster) {
        this.roster = roster;
    }

    /** The battle's held vehicle clearance masks, for any route question asked during play. */
    public VehicleClearances clearances() { return clearances; }

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
        return spawn(type, List.of(), faction, mission);
    }

    /** As above, set down with what the kits on its rack make of it ({@link VehicleKit}). */
    public long spawn(VehicleType type, List<VehicleKit> kits, Faction faction, VehicleMission mission) {
        return spawn(type, kits, VehiclePlating.FACTORY, faction, mission);
    }

    /** As above, in the plating its armor point holds ({@link VehiclePlating}). */
    public long spawn(VehicleType type, List<VehicleKit> kits, VehiclePlating plating, Faction faction,
                      VehicleMission mission) {
        return spawn(type, kits, plating, VehicleEngine.STOCK, faction, mission);
    }

    /** As above, running the engine its engine core holds ({@link VehicleEngine}). */
    public long spawn(VehicleType type, List<VehicleKit> kits, VehiclePlating plating, VehicleEngine engine,
                      Faction faction, VehicleMission mission) {
        return spawn(type, kits, plating, engine, type.standardPart(), faction, mission);
    }

    /**
     * As above, with {@code weapon} in the gun card instead of the hull's own gun ({@link VehicleGun}); null for its own,
     * or for a modular hull ({@link VehicleType#hasModularTurret}) for the bare ring, which has no gun and reads as a
     * shot-away turret ({@link GroundTurret#removed}).
     */
    public long spawn(VehicleType type, List<VehicleKit> kits, VehiclePlating plating, VehicleEngine engine,
                      MechWeaponComponent weapon, Faction faction, VehicleMission mission) {
        VehicleKit.Effect fitted = VehicleKit.Effect.of(kits, plating, type);
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();

        // Build the kinematics + (optional) turret the mission is agnostic of.
        GroundBody body = type.createBody();
        body.speedFactor = engine.speedFactor();
        float spawnX = mission.inboundX[0], spawnY = mission.inboundY[0];
        float nextX = mission.inboundX[1], nextY = mission.inboundY[1];
        body.teleport(spawnX, spawnY, AirBody.facingToward(nextX - spawnX, nextY - spawnY));
        StructureDef fittedStructure = type.hasTurretWeapon() ? VehicleGun.fit(type, weapon) : null;
        TurretMountDef gun = fittedStructure != null ? fittedStructure.mount : null;
        GroundTurret turret = gun != null
                ? GroundTurret.fitted(gun, Math.round(gun.ammoCapacity * fitted.ammoFactor()), fitted,
                        HullTurretSection.forHull(type, fitted)) : null;
        if (turret != null && weapon == null && type.hasModularTurret()) turret.removed = true;
        if (turret != null && weapon != null) {
            turret.structure = fittedStructure;
            turret.weapon = weapon;
            if (weapon.rotary() != null) turret.rotary = new RotaryFeed(weapon.rotary());
        }
        HullLocations locations = HullLocations.of(type, turret != null ? turret.section : null, fitted);
        if (turret != null) {
            for (VehicleMount mount : type.secondaryMounts()) turret.secondaries.add(MountedGun.of(mount, fitted));
        }

        // VEHICLE_MISSION (mission bag) + VEHICLE_CONTROL (motion-control bag) are universal;
        // GROUND_TURRET is present only when armed; HULL_LOCATIONS on every hull.
        List<ComponentType> components = new ArrayList<>(List.of(c.IDENTITY, c.GROUND_IDENTITY,
                c.GROUND_KINEMATICS, c.VEHICLE_MISSION, c.VEHICLE_CONTROL, c.HEALTH, c.ARMOR, c.HULL_LOCATIONS));
        if (turret != null) components.add(c.GROUND_TURRET);
        long id = roster.allocateVehicle(components.toArray(new ComponentType[0]));
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
        // Located damage holds the drive and the side and rear plates on top of the hull's own pools.
        float structure = type.maxStructure * fitted.structureFactor() + locations.maxDrive();
        float armor = locations.maxTotalArmor();
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_HP, structure);
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_MAX_HP, structure);
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_DAMAGE_TAKEN_MULT, 1f);
        world.setFloat(id, c.HEALTH, BattleComponents.HEALTH_INCOMING_ACCURACY_MULT,
                type.incomingAccuracyMult);
        world.setFloat(id, c.ARMOR, BattleComponents.ARMOR_CURRENT, armor);
        world.setFloat(id, c.ARMOR, BattleComponents.ARMOR_MAX, armor);
        world.setFloat(id, c.ARMOR, BattleComponents.ARMOR_RATING, type.armorRating + fitted.armorRatingBonus());
        if (turret != null) {
            world.setObject(id, c.GROUND_TURRET, BattleComponents.GROUND_TURRET_STATE, turret);
        }
        world.setObject(id, c.HULL_LOCATIONS, BattleComponents.HULL_LOCATIONS_STATE, locations);
        entityIds.add(id);
        List<DroneRole> kitBay = new ArrayList<>();
        for (VehicleKit kit : kits) kitBay.addAll(kit.droneBay());
        if (!kitBay.isEmpty()) kitBays.put(id, List.copyOf(kitBay));
        roster.bodies().admit(id);
        // A chassis that spawns already on the map is a body somebody could be
        // looking at this tick; one still off-map joins at the next rebuild.
        if (isTargetable(id)) roster.indexVehicle(id);
        if (spawnSink != null) spawnSink.accept(id);
        return id;
    }

    /**
     * Destroys the vehicle's world entity — called at terminal {@link VehicleState#GONE}.
     * One {@code destroy} drops all its columns. No-op on {@code 0L} (never adopted) or an
     * already-destroyed id.
     */
    /** Marks {@code id} as the company's own vehicle {@code campaignId}, so its loss writes back. */
    public void tagCompanyVehicle(long id, String campaignId) {
        if (id != 0L && campaignId != null) companyVehicles.put(id, campaignId);
    }

    /**
     * Campaign ids of company vehicles wrecked on this field, in deployment order. A
     * vehicle that never landed (its carrier still aloft or downed) is not here.
     */
    public List<String> lostCompanyVehicleIds() {
        List<String> lost = new ArrayList<>();
        for (Map.Entry<Long, String> entry : companyVehicles.entrySet()) {
            VehicleMission mission = mission(entry.getKey());
            if (mission != null && mission.state == VehicleState.WRECKED) lost.add(entry.getValue());
        }
        return lost;
    }

    public void despawn(long id) {
        if (id == 0L) return;
        roster.unindexVehicle(id);
        roster.entityWorld().destroy(id);
        entityIds.remove(id);
        kitBays.remove(id);
    }

    /** The drones this hull launches, in launch order: its type's bay, then what its kits add. */
    public List<DroneRole> droneBay(long id) {
        VehicleType type = vehicleType(id);
        List<DroneRole> added = kitBays.get(id);
        if (type == null) return added == null ? List.of() : added;
        if (added == null) return type.droneBay();
        List<DroneRole> bay = new ArrayList<>(type.droneBay());
        bay.addAll(added);
        return bay;
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

    /** Installs the hook {@link #spawn} runs on each new hull once its columns are seeded. */
    public void setSpawnSink(LongConsumer sink) {
        this.spawnSink = sink;
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
        if (!isArmed(id)) return 0f;
        StructureDef structure = turretStructure(id);
        return structure != null ? structure.mount.weapon.range : 0f;
    }

    /**
     * How {@code id} fights: the stance its fitted weapon asks for ({@link VehicleStance}), or its type's own for the
     * hull's own gun.
     */
    public VehicleType.Stance engagementStance(long id) {
        VehicleType type = vehicleType(id);
        if (type == null) return VehicleType.Stance.DUEL;
        GroundTurret turret = type.hasTurretWeapon() ? turret(id) : null;
        return turret != null && turret.structure != null ? VehicleStance.of(turret.structure.mount)
                : type.engagementStance();
    }

    /**
     * The structure {@code id}'s gun stands in: the weapon fitted to its gun card, or its hull's own gun; null for an
     * unarmed hull. Every read of a live hull's gun goes through here, never through its type alone.
     */
    public StructureDef turretStructure(long id) {
        VehicleType type = vehicleType(id);
        if (type == null || !type.hasTurretWeapon()) return null;
        GroundTurret turret = turret(id);
        return turret != null ? turret.structureFor(type) : type.turretStructure();
    }

    /** Circular contact radius used by ballistic and blast broad phases. */
    @Override
    public float targetRadius(long id) {
        VehicleType type = vehicleType(id);
        return type != null ? Math.max(type.visualLengthCells, type.visualWidthCells) * 0.5f : 0f;
    }

    /** The hull's footprint, as {@code HullImpact} meets it: its drawn length and width. */
    @Override
    public float hullHalfLength(long id) {
        VehicleType type = vehicleType(id);
        return type != null ? type.visualLengthCells * 0.5f : 0f;
    }

    @Override
    public float hullHalfWidth(long id) {
        VehicleType type = vehicleType(id);
        return type != null ? type.visualWidthCells * 0.5f : 0f;
    }

    @Override
    public float hullFacingDegrees(long id) {
        GroundBody body = body(id);
        return body != null ? body.facingDegrees : 0f;
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

    /**
     * Whether the hull at {@code id} has a gun it can still fire: a turret weapon whose turret damage has not
     * destroyed it ({@link GroundTurret#destroyed()}). An empty magazine is still armed; it is a different fact.
     */
    public boolean isArmed(long id) {
        VehicleType type = vehicleType(id);
        if (type == null || !type.hasTurretWeapon()) return false;
        GroundTurret turret = turret(id);
        return turret == null || !turret.destroyed();
    }

    /** The vehicle's live turret state, or {@code null} if unarmed / not a live ground craft (has-gated, presence == armed). */
    public GroundTurret turret(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.GROUND_TURRET)
                ? (GroundTurret) world.getObject(id, c.GROUND_TURRET, BattleComponents.GROUND_TURRET_STATE)
                : null;
    }

    /** The vehicle's located damage ({@link HullLocations}), or {@code null} when {@code id} isn't a live ground craft. */
    public HullLocations locations(long id) {
        BattleComponents c = roster.components();
        EntityWorld world = roster.entityWorld();
        return world.has(id, c.HULL_LOCATIONS)
                ? (HullLocations) world.getObject(id, c.HULL_LOCATIONS, BattleComponents.HULL_LOCATIONS_STATE)
                : null;
    }
}
