package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;

import java.util.List;
import java.util.Locale;

/**
 * Static config for each ground-vehicle variant — sprite, capacity, handling
 * tunables, visual footprint. Parallels {@link com.dillon.starsectormarines.battle.air.ShuttleType}
 * for ground craft. The sim ticks each ground vehicle's {@link GroundBody}
 * directly — different variants can supply different kinematic models via
 * {@link #createBody()}, so a future {@code TANK} entry can return a
 * differential-drive {@code TrackedBody} instead of a {@link BicycleBody}
 * without changing {@link GroundSystem} at all.
 *
 * <p>Each constant overrides {@link #createBody()} with its own kinematic model
 * and constructor args. The sprite path resolves against the mod's atlas.
 */
public enum VehicleType {

    /**
     * The APC: the one wheeled troop carrier chassis, six marines behind a roof machine gun (the light
     * machine gun every machine-gun hull carries, {@link #ownGun}). Slower and
     * heavier than the militia truck. The gun is what it defends itself with on the drive in and the drive out;
     * it does not buy the squad a parked fire-support platform on the drop point. A tougher or faster APC is this
     * hull with plating and an engine fitted, never a hull of its own ({@link VehicleStockFit}); only a turret
     * that changes the gun card is a variant of the chassis ({@link #FIRE_SUPPORT_APC}, {@link #LRM_CARRIER}),
     * which shares every number here but the turret and the troop bay.
     * Bearing registration comes from art-source/vehicles/apc: a 1.42-cell gun frame to match the ballistic muzzles.
     */
    HEAVY_APC(
            "graphics/battle/vehicles/army-apc.png", /*spriteFrame*/ 0, /*frameCount*/ 4,
            /*spriteFacingOffsetDeg*/ -90f,
            /*capacity*/ 6, Apc.DRAWN_LENGTH, Apc.VISUAL_LENGTH, Apc.VISUAL_WIDTH,
            Apc.MAX_SPEED, Apc.ACCEL, Apc.BRAKING,
            Apc.DEBOARD_INTERVAL, Apc.LOOK_AHEAD,
            /*turretFrame*/ 1, /*turretMountX*/ 0f, Apc.TURRET_MOUNT_Y,
            /*turretPivotX*/ 0f, /*turretPivotY*/ 0f, /*turretVisualCells*/ 1.42f * Apc.SCALE, /*turretSpriteFacingOffsetDeg*/ -90f,
            /*turretStructureId*/ TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID,
            Apc.STRUCTURE, Apc.ARMOR, Apc.ARMOR_RATING, Apc.INCOMING_ACCURACY, Apc.HIT_HALF_HEIGHT) {
        @Override
        public GroundBody createBody() { return Apc.body(this); }
        @Override public float noseInsetCells() { return LIFTED_NOSE_INSET; }
    },
    /**
     * The mortar carrier: the APC chassis with the troop bay given over to a 120 mm mortar on a roof turntable, a
     * ballistic gun card two rows tall. It is the LRM carrier's steadier, weaker sibling. Like the carrier it fights
     * from beyond its target's reach ({@link Stance#STANDOFF}) and drops its rounds over cover onto anything somebody
     * else on its side is looking at ({@link SideObservers}); unlike the carrier it fires on men as readily as on
     * machines. One bomb every few seconds from a deep rack hits far softer than the LRM's ten-missile salvo, but it
     * keeps falling after the launcher has emptied its magazine, so the mortar wins a long fight the LRM wins a short
     * one. It cannot carry a squad. Its sheet is the APC's hulls with an ImageGen turret at the hull's scale:
     * an open turntable, its tube raised with the bore toward the eye, ready bombs racked round the rear
     * (art-source/vehicles/mortar-carrier).
     */
    FIRE_SUPPORT_APC("graphics/battle/vehicles/mortar-carrier.png", 0, 4, -90f,
            0, Apc.DRAWN_LENGTH, Apc.VISUAL_LENGTH, Apc.VISUAL_WIDTH, Apc.MAX_SPEED, Apc.ACCEL, Apc.BRAKING,
            Apc.DEBOARD_INTERVAL, Apc.LOOK_AHEAD,
            1, 0f, Apc.TURRET_MOUNT_Y, 0f, 0f, Apc.DRAWN_LENGTH, -90f,
            TurretCatalogRegistry.VEHICLE_MORTAR_STRUCTURE_ID,
            Apc.STRUCTURE, Apc.ARMOR, Apc.ARMOR_RATING, Apc.INCOMING_ACCURACY, Apc.HIT_HALF_HEIGHT) {
        @Override public GroundBody createBody() { return Apc.body(this); }
        @Override public Stance engagementStance() { return Stance.STANDOFF; }
        @Override public float noseInsetCells() { return LIFTED_NOSE_INSET; }
    },
    /**
     * The Onager, the Hegemony's heavy repulsor tank and the convoy's heavy spearhead (hegemony-armor-line.md): an
     * ultra-heavy hull on a repulsor skirt, broad and short, that floats over water and rubble as a skirt does and
     * crushes ordinary walls and barricades in its path by its own weight ({@link WallCrush}). Its weapon is drawn
     * under the shared Hegemony head ({@link TurretHead}). Turns on conservative rolling arcs, and on the spot where no
     * arc fits ({@link NeutralPivot}). Its active protection, where it carries one, is a rack kit
     * ({@code MechInternal.ACTIVE_PROTECTION}); the stock fit with it is the Parapet ({@link VehicleStockFit#PARAPET}).
     * Its sheet is the owner's concept turned to point left (art-source/vehicles/heavy-repulsor).
     */
    BREAKTHROUGH_TANK("graphics/battle/vehicles/heg-heavy-repulsor.png", 0, 4, -90f,
            0, Onager.DRAWN_LENGTH, Onager.VISUAL_LENGTH, Onager.VISUAL_WIDTH, 2.9f, .8f, 2.4f, .8f, 3.0f,
            1, 0f, Onager.TURRET_MOUNT_Y, 0f, 0f, Onager.DRAWN_LENGTH, -90f,
            TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID, 750f, 550f, 18f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            // Bounded curvature is deliberately shared with route proof and collision checks. A skirt slews its
            // heading faster than a tracked hull steers, but eighty tonnes on it swings slowly: a first figure for
            // the playtest.
            return new BicycleBody(2.4f, 34f, 110f, accel, brakingAccel, maxSpeed, TANK_REVERSE_FRACTION);
        }
        @Override public float turretRecoilCells() { return .18f; }
        @Override public float turretHitShare() { return TANK_TURRET_HIT_SHARE; }
        @Override public float turretPoolShare() { return TANK_TURRET_POOL_SHARE; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(TANK_ARCS); }
        @Override public float turretRingShareOfWidth() { return Onager.RING_SHARE_OF_WIDTH; }
        @Override public float noseInsetCells() { return LIFTED_NOSE_INSET; }
    },
    /**
     * The Scorpion, the Hegemony's medium tank (hegemony-armor-line.md): a lighter tracked hull below the Onager,
     * faster, thinner in the plate and cheaper, at the bottom of the heavy weight class but carried by a Medium lift.
     * Its gun card is a two-row ballistic one, so it takes the HC-10, the autocannons, the demolition cannons and
     * the chaingun but never the Onager's HC-20; the weapon is drawn under the shared Hegemony head
     * ({@link TurretHead}). Its sheet is the owner's concept turned to point left (art-source/vehicles/medium-tank).
     */
    MEDIUM_TANK("graphics/battle/vehicles/heg-medium-tank.png", 0, 4, -90f,
            0, MediumTank.DRAWN_LENGTH, MediumTank.VISUAL_LENGTH, 1.75f, 3.6f, 1.4f, 3.2f, .8f, 2.8f,
            1, 0f, MediumTank.TURRET_MOUNT_Y, 0f, 0f, MediumTank.DRAWN_LENGTH, -90f,
            TurretCatalogRegistry.TANK_MEDIUM_CANNON_STRUCTURE_ID, 520f, 360f, 15f, 1.35f, 1.0f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.3f, 34f, 105f, accel, brakingAccel, maxSpeed, TANK_REVERSE_FRACTION);
        }
        @Override public float turretRecoilCells() { return .12f; }
        @Override public float turretHitShare() { return TANK_TURRET_HIT_SHARE; }
        @Override public float turretPoolShare() { return TANK_TURRET_POOL_SHARE; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(TANK_ARCS); }
        @Override public float turretRingShareOfWidth() { return MediumTank.RING_SHARE_OF_WIDTH; }
        @Override public float noseInsetCells() { return LIFTED_NOSE_INSET; }
    },
    /**
     * Breakthrough hull with a ship's burst laser where the twin cannons were. A ship mounts this emitter as
     * point defence because it barely marks proper plate; at ground scale almost nothing is proper plate. It
     * answers each target in a short burst a second, hits nearly where it aims and turns fast, so it clears
     * infantry and drones far quicker than a cannon tank. It carries no blast and no wall damage, so it cannot open a
     * building, and its low penetration leaves it chipping at armor. The power plant takes the shell stowage's
     * room and weight, so the hull runs a little faster. Its sheet is Tri-Tachyon's: the breakthrough hull in the
     * company's white livery with a capacitor pack over the rear deck, and for a turret the vanilla PD laser's shape,
     * up-res'd and in the company's white, graphite and cyan, on a ship's turret mount, its shutter opening on the
     * lens while it fires ({@link #turretFiringFrame}; art-source/vehicles/tank-laser). Its lance is the burst PD
     * laser's blue.
     */
    LASER_TANK("graphics/battle/vehicles/army-tank-laser.png", 0, 8, -90f,
            0, 4.3f, 3.85f, 2.25f, 3.3f, 1.05f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 1.6f, -90f,
            TurretCatalogRegistry.TANK_PL_3_STRUCTURE_ID, 750f, 500f, 18f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.6f, 32f, 95f, accel, brakingAccel, maxSpeed, TANK_REVERSE_FRACTION);
        }
        @Override public float turretHitShare() { return TANK_TURRET_HIT_SHARE; }
        @Override public float turretPoolShare() { return TANK_TURRET_POOL_SHARE; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(TANK_ARCS); }
        @Override public int turretFiringFrame() { return 4; }
    },
    /**
     * Workshop pickup with a pintle MG and welded plate, faction-neutral for authored local forces.
     * Its plate turns rifle fire, so it can stand in a road and draw it; crude rockets still get through.
     */
    MILITIA_TECHNICAL("graphics/battle/vehicles/militia-technical.png", 0, 4, -90f,
            0, 3.0f, 2.9f, 1.4f, 4.0f, 2.4f, 4.0f, .8f, 2.4f,
            1, 0f, -.9875f, 0f, 0f, 1.508943f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 480f, 180f, 9f, 1.3f, .65f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.65f, 30f, 180f, accel, brakingAccel, maxSpeed);
        }
    },
    /**
     * The supply pickup, a field rig: the technical's pickup with its pintle gone and its bed full of ammunition
     * crates — what a company welds together, not anybody's issue hull. It carries a finite stock of
     * resupply and hands it out, one round at a time through the battle's ordinary {@code ResupplySystem}, to friendly
     * squads standing near it — but only while it is halted: crates come off a parked truck. It pays for that with
     * everything else: no gun, no seats, and none of the technical's welded plate (art-source/vehicles/light-roles).
     */
    SUPPLY_TRUCK("graphics/battle/vehicles/supply-truck.png", 0, 4, -90f,
            0, 3.0f, 2.9f, 1.4f, 3.4f, 2.0f, 3.6f, .8f, 2.4f,
            -1, 0, 0, 0, 0, 0, 0, null, 360f, 60f, 4f, 1.3f, .65f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.65f, 30f, 170f, accel, brakingAccel, maxSpeed);
        }
        @Override public int supplyStock() { return 24; }
    },
    /**
     * The spotter pickup, a field rig: the pickup cut short on a shorter wheelbase, painted dust grey, with a
     * salvaged sensor mast where the gun stood. Its job
     * is information: it sees farther than a marine and reports what it sees to its side's fog and its commander
     * ({@link HullSightings}). The fastest hull in the bay, and the thinnest: no gun, no seats, almost no plate, and its
     * sight is ordinary line of sight, so it has to be driven somewhere it can look.
     */
    SCOUT_CAR("graphics/battle/vehicles/scout-car.png", 0, 4, -90f,
            0, 2.5f, 2.4f, 1.4f, 5.2f, 3.0f, 4.5f, .8f, 2.2f,
            -1, 0, 0, 0, 0, 0, 0, null, 200f, 30f, 3f, 1.15f, .6f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.35f, 32f, 200f, accel, brakingAccel, maxSpeed);
        }
        @Override public float sightCells() { return 48f; }
    },
    /**
     * Tri-Tachyon's hover scout, a purpose-built corporate hull and not a field rig: a lift-fan skirt under a
     * sleek composite shell, a sensor cluster that reports to its side as the spotter pickup's mast does
     * ({@link HullSightings}), and a light twin pulse emitter of the Wisp family for screening. Hover is a real
     * trade ({@link HoverSkirt}): it crosses water and rubble no wheel can, and it cannot shove a body aside.
     * It pays in plate — thinner than the technical — and it is slower than the stripped pickup. Its sheet draws the
     * emitter at the hull's own scale, so the turret frame spans the hull frame (art-source/vehicles/tt-hover).
     */
    HOVER_SCOUT("graphics/battle/vehicles/tt-hover-scout.png", 0, 4, -90f,
            0, 2.625f, 2.55f, 1.5f, 4.6f, 3.2f, 4.2f, .8f, 2.2f,
            1, 0f, -.15f, 0f, 0f, 2.625f, -90f,
            TurretCatalogRegistry.HOVER_PULSE_STRUCTURE_ID, 240f, 50f, 6f, 1.15f, .55f) {
        @Override public GroundBody createBody() {
            // A skirt slews faster than a steered axle; bounded curvature stays the planners' shared contract.
            return new BicycleBody(1.3f, 38f, 260f, accel, brakingAccel, maxSpeed);
        }
        @Override public float sightCells() { return 40f; }
        @Override public List<DroneRole> droneBay() { return List.of(DroneRole.SENSOR, DroneRole.SENSOR); }
    },
    /**
     * The hover scout's hull stretched over a drone bay: a Tri-Tachyon carrier whose drones are its weapon. It
     * carries no gun of its own; it launches a tethered flight, crewed remote control rather than free autonomy:
     * two escorts, two point-defence halo drones and a strike drone the bay rebuilds. They fall when the carrier
     * is wrecked, and other drones lost stay lost.
     * Its sheet has no turret frames (art-source/vehicles/tt-hover).
     */
    DRONE_SKIMMER("graphics/battle/vehicles/tt-drone-skimmer.png", 0, 4, -90f,
            0, 3.25f, 3.15f, 1.9f, 3.6f, 2.4f, 3.8f, .8f, 2.6f,
            -1, 0, 0, 0, 0, 0, 0, null, 360f, 110f, 8f, 1.25f, .6f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.7f, 34f, 220f, accel, brakingAccel, maxSpeed);
        }
        @Override public List<DroneRole> droneBay() {
            return List.of(DroneRole.ESCORT, DroneRole.ESCORT, DroneRole.HALO, DroneRole.HALO, DroneRole.STRIKE);
        }
    },
    /**
     * The APC chassis with the troop bay given over to a launcher, a missile gun card two rows tall: one mech LRM-15
     * pod on a turntable where the twin MG stood, which stands up on it to fire. It is the long arm against armor,
     * where the mortar carrier ({@link #FIRE_SUPPORT_APC}) is the steady one against anything: its rounds arc over
     * cover onto a machine somebody else on its side is looking at ({@link SideObservers}), it holds fire at anything
     * soft, and it fights from beyond its target's reach ({@link Stance#STANDOFF}). It carries no squad. Its sheet is
     * the APC's hulls with the mech LRM pod drawn at the hull's scale (art-source/vehicles/missile-carriers).
     */
    LRM_CARRIER("graphics/battle/vehicles/lrm-carrier.png", 0, 4, -90f,
            0, Apc.DRAWN_LENGTH, Apc.VISUAL_LENGTH, Apc.VISUAL_WIDTH, Apc.MAX_SPEED, Apc.ACCEL, Apc.BRAKING,
            Apc.DEBOARD_INTERVAL, Apc.LOOK_AHEAD,
            1, 0f, Apc.TURRET_MOUNT_Y, 0f, 0f, Apc.DRAWN_LENGTH, -90f,
            TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID,
            Apc.STRUCTURE, Apc.ARMOR, Apc.ARMOR_RATING, Apc.INCOMING_ACCURACY, Apc.HIT_HALF_HEIGHT) {
        @Override public GroundBody createBody() { return Apc.body(this); }
        @Override public Stance engagementStance() { return Stance.STANDOFF; }
        @Override public float noseInsetCells() { return LIFTED_NOSE_INSET; }
        // The pod is 0.9 cells long and stays centred on the turntable however it is raised.
        @Override public float turretRaiseFold() { return .78f; }
    },
    /**
     * The rocket pickup, a field rig: the technical's pickup with a mech SRM pod bolted to its pintle ring and the
     * welded plate left off to make the weight. Its anti-armor rockets reach no farther than a rack on a mech's
     * shoulder, so it never drives at a tank: it waits where it was set down, behind cover, and fires on the armor
     * that comes into reach ({@link Stance#AMBUSH}). It holds fire at men. Cheap and fragile, a Light lift
     * (art-source/vehicles/missile-carriers).
     */
    SRM_TECHNICAL("graphics/battle/vehicles/srm-technical.png", 0, 4, -90f,
            0, 3.0f, 2.9f, 1.4f, 4.0f, 2.4f, 4.0f, .8f, 2.4f,
            1, 0f, -.9875f, 0f, 0f, 3.0f, -90f,
            TurretCatalogRegistry.VEHICLE_SRM_STRUCTURE_ID, 260f, 60f, 5f, 1.3f, .65f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.65f, 30f, 180f, accel, brakingAccel, maxSpeed);
        }
        @Override public Stance engagementStance() { return Stance.AMBUSH; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(FIELD_RIG_ARCS); }
    },
    /** Long-wheelbase industrial container hauler; no passengers or roof weapon. */
    CARGO_CARRIER("graphics/battle/vehicles/wayline-hauler.png", 0, 2, -90f,
            0, 4.8f, 4.5f, 1.3f, 2.4f, 1.2f, 3.0f, .8f, 3.2f,
            -1, 0, 0, 0, 0, 0, 0, null, 400f, 120f, 10f, 1.4f, .9f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.8f, 26f, 130f, accel, brakingAccel, maxSpeed);
        }
    };

    /**
     * Rounds of resupply this hull carries into battle and hands out through the battle's resupply authority, or 0
     * for every hull that carries none.
     */
    public int supplyStock() { return 0; }

    /** The roles of the drones this hull launches and tethers to itself, in launch order; empty for most hulls. */
    public List<DroneRole> droneBay() { return List.of(); }

    /** Drones this hull launches and tethers to itself, or 0 for every hull that carries none. */
    public int droneComplement() { return droneBay().size(); }

    /** How far a hull's crew sees out of it, in cells: a marine's own sight. */
    public static final float CREW_SIGHT_CELLS = UnitType.MARINE.visionRange;

    /** How far a sensor mast sees past the crew, in cells; 0 for a hull with no mast. */
    public float sightCells() { return 0f; }

    /**
     * How far this hull looks and reports what it sees to its side, in cells: its mast where it has one, the crew's
     * sight otherwise. Every hull is an observer ({@link HullSightings}, {@link SideObservers}).
     */
    public final float observeCells() { return Math.max(sightCells(), CREW_SIGHT_CELLS); }

    /**
     * How a deployed hull fights armor on its own ({@link VehicleEngagement}). A dueller takes the duel band its gun
     * and the target's weapons choose. A stand-off hull holds beyond the target's reach where its gun out-reaches it
     * and at the edge of its own otherwise, and never drives in to see what a spotter already sees. An ambusher
     * never drives at armor at all: it holds where it was set down and fires on what comes into its reach.
     */
    public enum Stance { DUEL, STANDOFF, AMBUSH }

    public Stance engagementStance() { return Stance.DUEL; }

    /**
     * The hull's weapon mounts beside its main turret ({@link VehicleMount}), in the order a driver's right button
     * lists them; empty for every hull with one gun.
     */
    public List<VehicleMount> secondaryMounts() { return List.of(); }

    /** Visual recoil from actual recent shots; does not change the ballistic hardpoint. */
    public float turretRecoilCells() { return 0f; }

    /**
     * Under 1 for an arcing launcher whose turret stands up to fire ({@link GroundTurret#elevation}): the share of
     * its flat reach its tube tips keep at a typical raise, which is where the mount's muzzles are authored, since a
     * launcher only fires raised. 1 for a turret that never stands up. How it is drawn raised is the renderer's,
     * pitched to its rounds' arc.
     */
    public float turretRaiseFold() { return 1f; }

    /**
     * The share of this hull's hits that land on its turret ({@link HullTurretSection}). Zero, with
     * {@link #turretPoolShare()}, for a hull whose gun cannot be shot away.
     */
    public float turretHitShare() { return 0f; }

    /**
     * The share of this hull's armor capacity and structure its turret holds; zero means the hull authors no
     * turret section and stays one body.
     */
    public float turretPoolShare() { return 0f; }

    /**
     * Each plate's armor as a multiple of the hull's armor outside its turret ({@link HullLocations}): front, left,
     * right, rear. The front holds the whole of it, so a hull shot head-on lasts as long as a hull with one pool did;
     * the sides and rear are thinner, which is what a flank is worth.
     */
    public float[] plateArmor() { return PLATE_ARMOR; }

    /**
     * The drive's structure as a share of the hull's ({@link HullLocations}), held on top of it: losing the drive
     * stops the hull, never brings its death nearer.
     */
    public float driveStructureShare() { return DRIVE_STRUCTURE_SHARE; }

    /** The share of hull hits from the front, a side and the rear that land on the drive ({@link HullLocations}). */
    public float[] driveHitShares() { return DRIVE_HIT_SHARES; }

    /**
     * What this hull's plate is worth on each arc a hit can arrive on ({@link ArmorArcs}): one shared profile, the
     * front strongest and the rear weakest, with the tank line on a steeper one of its own. The turret
     * of an armed hull meets the same profile on its own facing. {@code -Dbattle.vehicle.armorArcs=false} is the
     * control: every arc the hull's one plate.
     */
    public ArmorArcs.Profile armorArcs() { return arcs(HULL_ARCS); }

    /** {@code false} is the control: a hull's plate is the same on every arc. */
    public static final String ARMOR_ARCS_PROPERTY = "battle.vehicle.armorArcs";

    /**
     * Every hull's speed band, as a mech chassis has one (`MechFrameBudget`): from its own top speed and acceleration
     * to this multiple of them, an engine putting it {@code speedFraction} of the way along (the uprated engine half
     * way, ×1.10; the military engine all of it, ×1.20). One rule for every machine; only the band is the chassis's.
     */
    public static final float REFIT_SPEED_FACTOR = 1.2f;

    /**
     * Every hull's armor band: from its own plate to this multiple of it, plating putting it {@code armorFraction} of
     * the way along (composite half way, ×1.20; heavy all of it, ×1.40), the mech's plating rule on the hull's plate.
     */
    public static final float REFIT_ARMOR_FACTOR = 1.4f;

    private static final boolean ARMOR_ARCS =
            Boolean.parseBoolean(System.getProperty(ARMOR_ARCS_PROPERTY, "true"));

    /** Every hull's arcs unless it authors its own: a modest front, thinner sides, a thin rear. */
    static final ArmorArcs.Profile HULL_ARCS = new ArmorArcs.Profile(1f, 1.1f, .9f, .9f, .75f, .7f);

    /**
     * The tank line's: a thick sloped glacis, flanks a little thinner than the hull's nominal plate, and a thin
     * engine deck. The flank is deliberately mild: a tank caught broadside by a low-penetration gun is in a long, close
     * fight, and at 0.85 plate a heavily plated machine-gun hull out-traded the line's cannon tanks across a street.
     */
    static final ArmorArcs.Profile TANK_ARCS = new ArmorArcs.Profile(1f, 1.15f, 1f, .95f, .6f, .6f);

    /**
     * The rocket pickup's: welded sheet with nothing sloped about it, so no front bonus. With one, the cheapest rig
     * in the bay took a third of its fights from the fire-support APC.
     */
    static final ArmorArcs.Profile FIELD_RIG_ARCS = new ArmorArcs.Profile(1f, 1f, .9f, .9f, .75f, .7f);

    private static ArmorArcs.Profile arcs(ArmorArcs.Profile authored) {
        return ARMOR_ARCS ? authored : ArmorArcs.Profile.FLAT;
    }

    private static final float[] NO_TINT = {1f, 1f, 1f};

    /**
     * The tank hulls' reverse speed as a fraction of forward top speed. A tracked hull backs up
     * slower than a Bulwark walks, so a mech that closes on a tank can catch it instead of being
     * kited forever; every other hull reverses at half speed. {@code -Dbattle.vehicle.tankReverseFraction=.5}
     * is the control.
     */
    /**
     * A tank turret's section ({@link HullTurretSection}): a fifth of the hull's hits land on the turret, which
     * holds a quarter of its pools. The pool share sits above the hit share, so the gun goes late in a long fight
     * and by a run of hits in a short one, about one fight in ten against a Bulwark. A quarter of the hits against
     * 0.18 of the pools lost the gun in most fights and gave the open ground to the mech
     * ({@code heavy-equipment-progression.md}, a tank that loses its gun).
     */
    /** Front, left, right, rear: the whole plate on the nose, three fifths on a side, under half behind. */
    private static final float[] PLATE_ARMOR = {1f, .45f, .45f, .3f};
    /** Running gear worth a seventh of the hull; it shows most from the sides, least from ahead. */
    private static final float DRIVE_STRUCTURE_SHARE = .1f;
    private static final float[] DRIVE_HIT_SHARES = {.05f, .15f, .12f};

    static final float TANK_TURRET_HIT_SHARE = .2f;
    static final float TANK_TURRET_POOL_SHARE = .25f;

    static final float TANK_REVERSE_FRACTION =
            Float.parseFloat(System.getProperty("battle.vehicle.tankReverseFraction", ".3"));

    /** The APC chassis: the troop carrier and the two typed-turret variants built on it. */
    public boolean isApc() {
        return this == HEAVY_APC || this == FIRE_SUPPORT_APC || this == LRM_CARRIER;
    }

    /**
     * The APC chassis's numbers, shared by every hull built on it: footprint, handling, durability. Its variants
     * differ only in the turret and the troop bay.
     */
    private static final class Apc {
        /**
         * The APC's size, 1.6 times the olive hull's 2.4 / 2.25 / 0.9, as long as the medium tank (owner, 2026-11-05:
         * the Hegemony carrier read as a car beside the tanks). Its sheet is unchanged, so everything registered in its frame pixels
         * (roof bearing, tyre bands, running gear) grows with it; the width keeps a radius-1 route clearance.
         */
        static final float SCALE = 1.6f;
        /** How far the painted nose runs ahead of the hull's centre, cells (army-apc.png, 1.85 measured). */
        static final float PAINTED_NOSE = 1.85f;
        /** The footprint, its nose lifted; its rear edge falls at the tail plate, the tow hooks left out. */
        static final float DRAWN_LENGTH = 2.4f * SCALE, VISUAL_LENGTH = 2f * (PAINTED_NOSE - LIFTED_NOSE_INSET),
                VISUAL_WIDTH = .9f * SCALE;
        static final float MAX_SPEED = 2.8f, ACCEL = 1.8f, BRAKING = 3.5f;
        static final float DEBOARD_INTERVAL = .8f, LOOK_AHEAD = 2.2f;
        static final float TURRET_MOUNT_Y = .15208333f * SCALE;
        static final float STRUCTURE = 220f, ARMOR = 160f, ARMOR_RATING = 18f;
        static final float INCOMING_ACCURACY = 1.25f, HIT_HALF_HEIGHT = .75f;
        /** The Hegemony deck's engine grilles behind the turret ring, shared by the carriers built on this hull. */
        static final FluxVents VENTS = new FluxVents(178f, 70f);

        static GroundBody body(VehicleType type) {
            // The wheelbase keeps the olive hull's 1.6, so the larger hull turns where the old one did.
            return new BicycleBody(/*wheelbaseCells*/ 1.6f, /*maxSteeringDeg*/ 22f, /*steeringSlewDegPerSec*/ 150f,
                    type.accel, type.brakingAccel, type.maxSpeed);
        }
    }

    /**
     * How far a Hegemony hull's footprint stops behind its painted nose, cells ({@link #noseInsetCells}): the nose
     * overhangs the ground it touches, as though lifted a little off it (owner, 2026-11-05). A footprint is centred on
     * its hull, so its rear edge stands as far aft of the centre as the front stands ahead.
     */
    static final float LIFTED_NOSE_INSET = .25f;

    /** The medium tank's sheet registration (art-source/vehicles/medium-tank/pack.py). */
    private static final class MediumTank {
        static final float DRAWN_LENGTH = 3.84f;
        /** How far the painted nose runs ahead of the hull's centre, cells (heg-medium-tank.png, 1.70 measured). */
        static final float PAINTED_NOSE = 1.70f;
        /** The footprint's length, its nose lifted; its rear edge falls a little inside the tail. */
        static final float VISUAL_LENGTH = 2f * (PAINTED_NOSE - LIFTED_NOSE_INSET);
        /** The concept's turret ring, 37.7 px of the 768 px frame ahead of the hull's centre. */
        static final float TURRET_MOUNT_Y = .188545f;
        /** The ring's collar spans this share of the hull's width, measured off the concept. */
        static final float RING_SHARE_OF_WIDTH = .62f;
        /** The engine deck's twin grilles behind the turret ring. */
        static final FluxVents VENTS = new FluxVents(179f, 70f);
    }

    /** The Onager's sheet registration (art-source/vehicles/heavy-repulsor/pack.py). */
    private static final class Onager {
        /**
         * The Onager's size against its first 4.0-cell draw, as long as the laser tank (owner, 2026-11-05: it read no
         * larger than the medium tank). Its footprint grows with it; its drive keeps the first figures.
         */
        static final float SCALE = 4.3f / 4f;
        static final float DRAWN_LENGTH = 4f * SCALE;
        /** How far the painted wedge nose runs ahead of the hull's centre, cells (heg-heavy-repulsor.png, 1.94 measured). */
        static final float PAINTED_NOSE = 1.94f;
        /** The hull's painted width through its flanks, cells, measured. */
        static final float PAINTED_WIDTH = 2.37f;
        /**
         * How far each side of the footprint stands inside the painted flank, cells: the skirt's flanks overhang the
         * ground a little, as the nose does (owner, 2026-11-05).
         */
        static final float SIDE_INSET = .08f;
        /** The footprint, its nose lifted, its rear edge a little inside the tail, its sides inside the flanks. */
        static final float VISUAL_LENGTH = 2f * (PAINTED_NOSE - LIFTED_NOSE_INSET),
                VISUAL_WIDTH = PAINTED_WIDTH - 2f * SIDE_INSET;
        /** The concept's turret ring, 21.3 px of the 768 px frame ahead of the hull's centre. */
        static final float TURRET_MOUNT_Y = .110841f * SCALE;
        /** The ring's collar spans this share of the hull's width, measured off the concept. */
        static final float RING_SHARE_OF_WIDTH = .5345f;
        /** The pair of radiator grilles flanking the spine behind the turret ring. */
        static final FluxVents VENTS = new FluxVents(164f, 110f);
    }

    /**
     * The share of the hull's width its turret ring spans ({@link VehicleGun#mountCells}), so a closed head sits on the
     * ring its art paints. Most hulls' rings span seven tenths of it.
     */
    public float turretRingShareOfWidth() { return .7f; }

    /**
     * The technical's pickup and the light roles built on it. Every pickup is a <em>field rig</em> — jerry-rigged
     * from a civilian chassis, never a military faction's issue — which is how pattern channels weight it.
     */
    public boolean isPickup() {
        return this == MILITIA_TECHNICAL || this == SUPPLY_TRUCK || this == SCOUT_CAR || this == SRM_TECHNICAL;
    }

    /**
     * Whether this hull rides a hover skirt. Hover decides only the ground a hull crosses: a skirt floats over water
     * and rubble a wheel or a track cannot cross ({@link HoverSkirt}). What it shoves, rams and crushes, and what it
     * weighs, go by its {@link #weight()}, so a heavy repulsor shoves and crushes as a tracked hull of its weight
     * does: Tri-Tachyon's light skirts are light, the Onager's repulsor ultra-heavy.
     */
    public boolean isHover() { return this == HOVER_SCOUT || this == DRONE_SKIMMER || this == BREAKTHROUGH_TANK; }

    /**
     * Whether this hull shoves bodies out of its way, by contact and by bow wave: by its weight
     * ({@link Weight#shoves}), so a light hover skirt cannot clear a road by driving into people and every other hull,
     * a heavy repulsor among them, does.
     */
    public boolean repelsBodies() { return weight().shoves(isHover()); }

    /** A hover hull of light weight: a skirt that barely touches the ground, with no purchase and almost no mass. */
    private boolean lightSkirt() { return isHover() && weight() == Weight.LIGHT; }

    /** The gun tanks: the Onager on its repulsor, the laser tank and the medium tank on tracks. */
    public boolean isTank() {
        return this == BREAKTHROUGH_TANK || this == LASER_TANK || this == MEDIUM_TANK;
    }

    /**
     * Whether this hull opens ordinary walls and barricades in its path ({@link WallCrush}): every heavy and
     * ultra-heavy hull, by its own weight on its nose ({@link Weight#crushesWalls}). Its route proof and rolling
     * planner read the breach view instead of the grid it moves over.
     */
    public boolean crushesWalls() { return weight().crushesWalls(); }

    /**
     * Whether this hull flattens undergrowth and lone boulders it drives into ({@link HeavyTracks}), by its weight
     * ({@link Weight#crushesUndergrowth}), on tracks, wheels or a skirt: the APC lines, the rocket technical, the
     * gun tanks. A pickup, a scout car, a hauler or a light hover hull is stopped. Its planning and its
     * motion read the crush view.
     */
    public boolean crushesUndergrowth() { return weight().crushesUndergrowth(); }

    /**
     * Whether this hull drives through the light barricades its way covers ({@link BarricadeRam}): every hull that
     * crushes undergrowth but those that crush walls, which open barricades as they open walls; so the medium hulls.
     */
    public boolean ramsBarricades() { return crushesUndergrowth() && !crushesWalls(); }

    /**
     * How heavy a hull is, and so what it does to what it meets: a Brute's charge reads it
     * ({@code colony-breach-nouns.md}, Brute), and it alone decides the shove, the crush, the ram and the mass. Hover
     * decides only the ground a hull crosses, except that a light skirt has no purchase to shove with.
     */
    public enum Weight {
        LIGHT, MEDIUM, HEAVY, ULTRA_HEAVY;

        /** Whether this class is {@code other} or heavier. */
        public boolean atLeast(Weight other) { return ordinal() >= other.ordinal(); }

        /** Whether a hull of this weight shoves bodies: every one but a light skirt ({@code hover}), which has no purchase. */
        public boolean shoves(boolean hover) { return !hover || this != LIGHT; }

        /** Whether a hull of this weight flattens undergrowth: medium and heavier, whatever it runs on. */
        public boolean crushesUndergrowth() { return this != LIGHT; }

        /**
         * Whether a hull of this weight crushes ordinary walls and barricades with its own nose: heavy and ultra-heavy
         * (owner: heavy vehicles break ordinary walls, not fortified ones). A medium hull rams barricades
         * ({@link BarricadeRam}) and stops at walls.
         */
        public boolean crushesWalls() { return atLeast(HEAVY); }

        /**
         * Mass in tonnes: a pickup about a Brute's weight and a light skirt ({@code hover}) less, an APC several
         * times it, a gun tank many, an ultra-heavy hull nearly twice a gun tank.
         */
        public float massTonnes(boolean hover) {
            return switch (this) {
                case LIGHT -> hover ? 1.5f : 2.5f;
                case MEDIUM -> 12f;
                case HEAVY -> 45f;
                case ULTRA_HEAVY -> 80f;
            };
        }
    }

    /**
     * The hull's weight class: the pickups, Tri-Tachyon's hover hulls and haulers are light, the APC lines and the
     * missile carriers medium, the laser and medium tanks heavy, and the Onager ultra-heavy.
     */
    public Weight weight() {
        if (this == BREAKTHROUGH_TANK) return Weight.ULTRA_HEAVY;
        if (this == LASER_TANK || this == MEDIUM_TANK) return Weight.HEAVY;
        if (isApc() || this == SRM_TECHNICAL) return Weight.MEDIUM;
        return Weight.LIGHT;
    }

    /**
     * Mass in tonnes by weight class ({@link Weight#massTonnes}), for a body running into the hull
     * ({@link HullImpact}), the shove and the momentum a hull spends on what it breaks.
     */
    public float massTonnes() { return weight().massTonnes(isHover()); }

    /**
     * How hard the ground holds the hull against a slide, as decelerations in cells a second squared: along
     * the hull, where a wheel rolls and a track does not, and across it, where both scrub. A light hover skirt
     * barely touches the ground either way; a heavier skirt is pressed down by its weight and holds across as a
     * wheel nearly does, a first figure for the playtest to tune. Along the hull it holds no harder than a wheel
     * rolls, so the Onager's eighty tonnes coast on after the throttle is off, and across it slides more than a
     * tracked hull would before the skirt bites: the repulsor's drift.
     */
    public float gripAlong() {
        if (lightSkirt()) return .8f;
        if (isHover()) return 1.5f;
        return hasAnimatedTreads() ? 5f : 1.5f;
    }

    public float gripAcross() {
        if (lightSkirt()) return .8f;
        if (isHover()) return 4f;
        return hasAnimatedTreads() ? 7f : 6f;
    }

    /** Only exposed tire/track tops scroll; covered technical/hauler wheels stay hidden, and a skirt has none. */
    public boolean hasAnimatedTreads() { return (isApc() || isTank()) && !isHover(); }

    /** Multiplier on the hull sprite's colour; white for every hull drawn as painted. */
    public float[] hullTint() { return NO_TINT; }

    /**
     * Whether this variant's hull and turret art exist. A variant still waiting on art keeps
     * its stats and pattern but is never offered as a card, as a loadout card whose
     * presentation is pending is not.
     */
    public boolean presentationDeclared() { return true; }

    public int wreckHullFrame() { return this == CARGO_CARRIER ? 1 : 2; }
    public int wreckTurretFrame() { return turretFrame < 0 ? -1 : 3; }

    /**
     * The sheet frame the live turret draws while its gun is engaged (a target on the ground or in the air, or a
     * burst under way), or -1 for a turret drawn the same whether or not it fires.
     */
    /** The laser hull's radiator fins across its tail, on the breakthrough master (art-source/vehicles/tank-laser). */
    private static final FluxVents LASER_VENTS = new FluxVents(321.5f, 98f);

    public int turretFiringFrame() { return -1; }

    /**
     * Whether this hull's turret is a modular head on an exposed turret ring ({@code vehicle-card-sizes.md}): a hull
     * with no part in its gun card has the bare ring and no gun. The laser tank alone; a hull with a closed head
     * ({@link #turretHead}) shows the head with nothing in its slot, and every other hull still carries the gun its
     * sheet was drawn with.
     */
    public boolean hasModularTurret() { return this == LASER_TANK; }

    /** The finish of the turret mount ring this hull's gun stands on: the laser tank white, the hovers silver, the rest army green. */
    /**
     * The head this hull's weapon turns in ({@link TurretHead}), or null for a hull whose weapon stands bare on its
     * ring. The APC, the medium tank and the Onager wear the closed Hegemony head (hegemony-armor-line.md). The mortar
     * and LRM carriers share the APC's hull but their weapons stand up to fire, which a closed head would hide, so they
     * carry them in the open Hegemony barbette.
     */
    public TurretHead turretHead() {
        if (this == FIRE_SUPPORT_APC || this == LRM_CARRIER) return TurretHead.HEGEMONY_BARBETTE;
        return this == HEAVY_APC || this == MEDIUM_TANK || this == BREAKTHROUGH_TANK ? TurretHead.HEGEMONY : null;
    }

    /**
     * The head a weapon of {@code point}'s colour turns in on this hull: a missile rack in a Hegemony closed head's place
     * stands in the open barbette instead, as the carriers' do, so a green Scorpion or Onager raises its rack to fire;
     * the hull's own head for any other colour, and for null.
     */
    public TurretHead turretHead(HardpointType point) {
        TurretHead own = turretHead();
        return own == TurretHead.HEGEMONY && point == HardpointType.MISSILE ? TurretHead.HEGEMONY_BARBETTE : own;
    }

    public TurretMountStyle mountStyle() {
        return switch (this) {
            case LASER_TANK -> TurretMountStyle.WHITE;
            case HOVER_SCOUT, DRONE_SKIMMER -> TurretMountStyle.SILVER;
            default -> TurretMountStyle.GREEN;
        };
    }

    /**
     * The part a spawn that names none fits, for the scenes, missions and fixtures that field a hull as a stock unit: the
     * laser tank's PL-3, the Onager's heavy cannon; a fixed hull's own gun. A spawn that names its card as
     * empty is not this one, and fields the bare ring.
     */
    public MechWeaponComponent standardPart() {
        return switch (this) {
            case LASER_TANK -> MechWeaponComponent.PL_3;
            case BREAKTHROUGH_TANK -> MechWeaponComponent.HC_20;
            case MEDIUM_TANK -> MechWeaponComponent.HC_10;
            default -> ownGun();
        };
    }

    /**
     * The part a stock spawn of a side that prefers {@code point}'s colour fits ({@link FactionVehiclePoints}): in the
     * gun tanks' omni cards the Scorpion's SRM-5 or PL-2 and the Onager's SRM-15 or PL-3; the hull's own standard
     * part for ballistic, for null, and on every hull whose card is not omni.
     */
    public MechWeaponComponent standardPart(HardpointType point) {
        VehicleGunCard card = VehicleGunCard.of(this);
        if (point == null || card == null || card.hardpoint != null) return standardPart();
        return switch (this) {
            case MEDIUM_TANK -> switch (point) {
                case MISSILE -> MechWeaponComponent.SRM_5;
                case ENERGY -> MechWeaponComponent.PL_2;
                case BALLISTIC -> standardPart();
            };
            case BREAKTHROUGH_TANK -> switch (point) {
                case MISSILE -> MechWeaponComponent.SRM_15;
                case ENERGY -> MechWeaponComponent.PL_3;
                case BALLISTIC -> standardPart();
            };
            default -> standardPart();
        };
    }

    /**
     * The mech weapon this hull's own turret is, for a hull whose stock gun is a shared mech part rather than a turret
     * of its own: every machine-gun hull carries the light machine gun, so the game has one machine gun. Its turret
     * structure stays the carriage the weapon sits in ({@link VehicleGun#fit}). Null for a modular hull, whose empty
     * card is a bare ring, and for a hull whose own gun is its turret structure's weapon.
     */
    public MechWeaponComponent ownGun() {
        return TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID.equals(turretStructureId) && turretFrame >= 0
                ? MechWeaponComponent.LIGHT_MACHINE_GUN : null;
    }

    /**
     * Where one turret head is on a hull's sheet: its live, wreck and firing frames (firing -1 for a head drawn the same
     * whether or not it fires). A hull's heads are painted at one registration, so they share the turret's drawn size,
     * pivot and muzzle.
     */
    public record HeadFrames(int live, int wreck, int firing) {}

    private static final HeadFrames LASER_WHITE_HEAD = new HeadFrames(1, 3, 4);
    private static final HeadFrames LASER_RED_HEAD = new HeadFrames(5, 6, 7);

    /**
     * The frames of the turret head this hull's sheet paints for {@code head}, or null for a head the hull does not
     * carry ({@link MechWeaponComponent#tankHead}): each hull declares its own heads. The laser tank alone carries
     * heads on its sheet, the white and red laser heads.
     */
    public HeadFrames headFrames(MechWeaponComponent.TankHead head) {
        if (head == null || this != LASER_TANK) return null;
        return switch (head) {
            case WHITE -> LASER_WHITE_HEAD;
            case RED -> LASER_RED_HEAD;
            default -> null;
        };
    }

    /**
     * Whether {@code weapon} fitted to this hull is drawn as one of the hull's own turret heads ({@link #headFrames})
     * rather than as its own drawing on the mount ring.
     */
    public boolean wearsHeadOf(MechWeaponComponent weapon) {
        return weapon != null && headFrames(weapon.tankHead()) != null;
    }

    /**
     * The sheet frame a turret draws: {@code stockFrame} (the live, wreck or firing frame) for a weapon that wears no
     * head, or the same state of the head {@code weapon} wears. A head with no firing frame draws its live frame.
     */
    public int headFrame(MechWeaponComponent weapon, int stockFrame) {
        HeadFrames head = weapon == null ? null : headFrames(weapon.tankHead());
        if (head == null) return stockFrame;
        if (stockFrame == wreckTurretFrame()) return head.wreck();
        if (stockFrame >= 0 && stockFrame == turretFiringFrame()) return head.firing() >= 0 ? head.firing() : head.live();
        return head.live();
    }

    /**
     * The vents this hull's gun's flux haze rises from ({@code TurretMountDef#flux}), as its art paints them, or null
     * for a hull whose art authors none. Every hull gun builds flux; the haze shows only off painted vents, so a hull
     * joins by registering its sheet's grilles here, in that sheet's frame.
     */
    private FluxVents fluxVents() {
        return switch (this) {
            case HEAVY_APC, FIRE_SUPPORT_APC, LRM_CARRIER -> Apc.VENTS;
            case MEDIUM_TANK -> MediumTank.VENTS;
            case BREAKTHROUGH_TANK -> Onager.VENTS;
            case LASER_TANK -> LASER_VENTS;
            default -> null;
        };
    }

    /**
     * How far aft of the hull's centre, in cells, the vents its gun's flux haze rises from ({@link #fluxVents}), or
     * zero for a hull that draws none. They span {@link #fluxVentHalfSpanCells} either side of the centre line.
     */
    public float fluxVentAftCells() {
        FluxVents vents = fluxVents();
        return vents == null ? 0f : vents.aftPx() / FluxVents.FRAME_PX * drawnLengthCells;
    }

    /** Half the width of the flux vents across the hull, in cells ({@link #fluxVentAftCells}). */
    public float fluxVentHalfSpanCells() {
        FluxVents vents = fluxVents();
        return vents == null ? 0f : vents.halfSpanPx() / FluxVents.FRAME_PX * drawnLengthCells;
    }

    /**
     * A hull sheet's flux vents in its frame's pixels: the vents' centre aft of the frame's centre, and half their
     * spread across the hull. The frame's long side is the hull's drawn length, so the vents grow with the hull.
     */
    private record FluxVents(float aftPx, float halfSpanPx) {
        /** The long side of a hull sheet's frame, in pixels. */
        static final float FRAME_PX = 768f;
    }

    /**
     * How far the footprint's front edge ({@link #visualLengthCells}) sits behind the painted nose, cells, so the nose
     * overhangs it as though lifted a little off the ground: the Hegemony line's ({@link #LIFTED_NOSE_INSET}). Zero for a hull whose
     * footprint runs to its nose.
     */
    public float noseInsetCells() { return 0f; }

    /** Where this hull's tracks or wheels meet the ground, as its sprite paints them ({@link RunningGear}). */
    public RunningGear runningGear() { return RunningGear.of(this); }

    /** Keep authored vehicle bearings and live/wreck registration for every chassis. */
    public SpriteSheetFrames registeredSpriteFrames(SpriteSheetFrames detected) {
        if (detected.sheetWidth % frameCount != 0) {
            throw new IllegalArgumentException("Registered vehicle sheet must contain equal horizontal frames");
        }
        int width = detected.sheetWidth / frameCount;
        SpriteSheetFrames.Frame[] frames = new SpriteSheetFrames.Frame[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = new SpriteSheetFrames.Frame(i * width, 0, width, detected.sheetHeight);
        }
        return new SpriteSheetFrames(detected.sheetWidth, detected.sheetHeight, frames);
    }

    public final String spritePath;
    /** Which frame in the horizontal sprite-sheet to draw. 0 = leftmost, increments rightward. */
    public final int spriteFrame;
    /** Authored horizontal frame count; registered sheets retain these full slots. */
    public final int frameCount;
    /**
     * Degrees to add to the chassis {@link GroundBody}'s {@code facingDegrees} when
     * rendering, to align the texture's natural "forward" with the sim's
     * "facing=0 is +Y" convention. Vanilla Starsector ship sprites point
     * north (offset 0); the ground-vehicle frames point left and use -90°
     * to turn their noses toward the sim's north when the body faces north.
     */
    public final float spriteFacingOffsetDeg;
    public final int capacity;
    /**
     * The sprite frame's long side (nose to tail) as drawn, cells: the scale the hull is painted at. Drawing only;
     * the frame runs past the hull to its wheels and transparent margin.
     */
    public final float drawnLengthCells;
    /**
     * The footprint's length, nose to tail, cells: what the simulation collides, routes, crushes and is struck by.
     * The hull body's painted length.
     */
    public final float visualLengthCells;
    /**
     * The footprint's width, side to side, cells: the hull body's painted width, leaving out wheels that jut past
     * it. Tracks that run flush along the hull are the hull's own sides.
     */
    public final float visualWidthCells;
    /** Cruise / max forward velocity, cells/sec. The path follower clamps target speed to this. */
    public final float maxSpeed;
    /** Forward thrust cap, cells/sec². Applied when speed is below the path-follower's target. */
    public final float accel;
    /** Forward braking cap, cells/sec². Sizes the brake taper into the final waypoint. */
    public final float brakingAccel;
    /** Sim-seconds between marine drop-offs after the truck reaches its LZ. */
    public final float deboardInterval;
    /**
     * Pure-pursuit look-ahead distance, cells — how far ahead along the
     * polyline the carrot sits. Larger = smoother corners but wider corner
     * cuts; smaller = tighter tracking but jitter on tight turns. ~1 vehicle
     * length is a reasonable starting point.
     */
    public final float lookAheadCells;

    /** Sheet frame index for the turret sprite, or {@code -1} if this variant has no turret. */
    public final int turretFrame;
    /** Turret mount point on the chassis, cells from chassis center. +X right, +Y forward in chassis local frame. */
    public final float turretMountX;
    public final float turretMountY;
    /** Rotation center within the turret sprite, cells from turret sprite center. +X right, +Y forward in turret local frame. */
    public final float turretPivotX;
    public final float turretPivotY;
    /** Visual size of the turret sprite (longest axis), cells. Same semantics as {@link #drawnLengthCells}. */
    public final float turretVisualCells;
    /** Facing offset for the turret sprite, degrees. Separate from {@link #spriteFacingOffsetDeg} because chassis and turret frames may face different directions in the sheet. */
    public final float turretSpriteFacingOffsetDeg;
    /** Stable structure id for the vehicle-mounted turret, or {@code null} if unarmed. */
    public final String turretStructureId;
    /** Component-native durability profile seeded into HEALTH/ARMOR at spawn. */
    public final float maxStructure;
    public final float maxArmor;
    public final float armorRating;
    /** Large silhouettes are easier to hit than an infantry body. */
    public final float incomingAccuracyMult;
    /** Vertical half-extent used by the ballistic target-plane/contact model. */
    public final float hitHalfHeight;

    public boolean hasTurretWeapon() {
        return turretStructureId != null && turretFrame >= 0;
    }

    public StructureDef turretStructure() {
        return hasTurretWeapon()
                ? TurretCatalogRegistry.requireStructure(turretStructureId) : null;
    }

    VehicleType(String spritePath, int spriteFrame, int frameCount,
                float spriteFacingOffsetDeg,
                int capacity, float drawnLengthCells, float visualLengthCells, float visualWidthCells,
                float maxSpeed, float accel, float brakingAccel,
                float deboardInterval, float lookAheadCells,
                int turretFrame, float turretMountX, float turretMountY,
                float turretPivotX, float turretPivotY, float turretVisualCells,
                float turretSpriteFacingOffsetDeg,
                String turretStructureId,
                float maxStructure, float maxArmor, float armorRating,
                float incomingAccuracyMult, float hitHalfHeight) {
        this.spritePath = spritePath;
        this.spriteFrame = spriteFrame;
        this.frameCount = frameCount;
        this.spriteFacingOffsetDeg = spriteFacingOffsetDeg;
        this.capacity = capacity;
        this.drawnLengthCells = drawnLengthCells;
        this.visualLengthCells = visualLengthCells;
        this.visualWidthCells = visualWidthCells;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.brakingAccel = brakingAccel;
        this.deboardInterval = deboardInterval;
        this.lookAheadCells = lookAheadCells;
        this.turretFrame = turretFrame;
        this.turretMountX = turretMountX;
        this.turretMountY = turretMountY;
        this.turretPivotX = turretPivotX;
        this.turretPivotY = turretPivotY;
        this.turretVisualCells = turretVisualCells;
        this.turretSpriteFacingOffsetDeg = turretSpriteFacingOffsetDeg;
        this.turretStructureId = turretStructureId;
        this.maxStructure = maxStructure;
        this.maxArmor = maxArmor;
        this.armorRating = armorRating;
        this.incomingAccuracyMult = incomingAccuracyMult;
        this.hitHalfHeight = hitHalfHeight;
    }

    /**
     * Factory for this variant's kinematic body. Each enum constant returns
     * the model appropriate for its handling — trucks/cars get
     * {@link BicycleBody}, tanks would get a future {@code TrackedBody},
     * mechs a holonomic body, etc. This is the only place per-variant
     * kinematic params live; {@link GroundSystem} sees only the abstract
     * {@link GroundBody}.
     */
    public abstract GroundBody createBody();

    /**
     * What this vehicle asks of the aircraft that drops it, or {@code null}
     * for one that is never air-delivered (the convoy's hauler).
     */
    public LiftClass liftClass() {
        return switch (this) {
            case HEAVY_APC, SUPPLY_TRUCK, SCOUT_CAR, HOVER_SCOUT, SRM_TECHNICAL -> LiftClass.LIGHT;
            case MILITIA_TECHNICAL, FIRE_SUPPORT_APC, DRONE_SKIMMER, LRM_CARRIER, MEDIUM_TANK -> LiftClass.MEDIUM;
            case BREAKTHROUGH_TANK, LASER_TANK -> LiftClass.HEAVY;
            case CARGO_CARRIER -> null;
        };
    }

    /**
     * The hull a saved name stands for, matched without case: a hull's own name, or for a retired hull the hull it was
     * carried to (the dozer breacher to the Onager, whose weight now opens the walls its blade did); null for a name
     * that is neither. Saves, pattern ids and fixtures read hulls through it.
     */
    public static VehicleType fromSavedName(String name) {
        if (name == null) return null;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        if (RETIRED_DOZER.equals(upper)) return BREAKTHROUGH_TANK;
        for (VehicleType type : values()) if (type.name().equals(upper)) return type;
        return null;
    }

    /** The retired dozer breacher's saved name: migration input only ({@link #fromSavedName}). */
    public static final String RETIRED_DOZER = "DOZER_BREACHER";
}
