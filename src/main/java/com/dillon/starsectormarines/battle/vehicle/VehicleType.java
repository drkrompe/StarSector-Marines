package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;

import java.util.List;

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
     * Armored personnel carrier — four marines, roof-mounted heavy MG.
     * Slower and heavier than the militia truck. The gun is what it defends
     * itself with on the drive in and the drive out; it does not buy the
     * squad a parked fire-support platform on the drop point.
     * Bearing registration comes from art-source/vehicles/apc; both APC hulls
     * carry the same 1.42-cell gun frame to match the shared ballistic muzzles.
     */
    HEAVY_APC(
            "graphics/battle/vehicles/army-apc.png", /*spriteFrame*/ 0, /*frameCount*/ 4,
            /*spriteFacingOffsetDeg*/ -90f,
            /*capacity*/ 6, /*drawnLengthCells*/ 2.4f, /*visualLengthCells*/ 2.25f, /*visualWidthCells*/ .9f,
            /*maxSpeed*/ 2.8f, /*accel*/ 1.8f, /*brakingAccel*/ 3.5f,
            /*deboardInterval*/ 0.8f, /*lookAheadCells*/ 2.2f,
            /*turretFrame*/ 1, /*turretMountX*/ 0f, /*turretMountY*/ 0.15208333f,
            /*turretPivotX*/ 0f, /*turretPivotY*/ 0f, /*turretVisualCells*/ 1.42f, /*turretSpriteFacingOffsetDeg*/ -90f,
            /*turretStructureId*/ TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID,
            /*maxStructure*/ 220f, /*maxArmor*/ 160f, /*armorRating*/ 18f,
            /*incomingAccuracyMult*/ 1.25f, /*hitHalfHeight*/ 0.75f) {
        @Override
        public GroundBody createBody() {
            return new BicycleBody(
                    /*wheelbaseCells*/ 1.6f,
                    /*maxSteeringDeg*/ 22f, /*steeringSlewDegPerSec*/ 150f,
                    accel, brakingAccel, maxSpeed);
        }
    },
    /**
     * The heavy APC with welded appliqué plate: the same four seats and roof gun behind
     * twice the armour, paid for in speed and in lift — it needs a Medium cradle where the
     * troop carrier rides a Light one. Same hull art; the fresh plate reads as a darker hull.
     */
    UP_ARMORED_APC("graphics/battle/vehicles/army-apc.png", 0, 4, -90f,
            4, 2.5f, 2.35f, .95f, 2.3f, 1.4f, 3.2f, .9f, 2.3f,
            1, 0f, .15208333f, 0f, 0f, 1.42f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 300f, 320f, 24f, 1.3f, .78f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.65f, 22f, 140f, accel, brakingAccel, maxSpeed);
        }
        @Override public float[] hullTint() { return UP_ARMORED_TINT; }
    },
    /**
     * The mortar carrier: the heavy APC's hull with the troop bay given over to a 120 mm mortar on a roof turntable.
     * It is the LRM carrier's steadier, weaker sibling. Like the carrier it fights from beyond its target's reach
     * ({@link Stance#STANDOFF}) and drops its rounds over cover onto anything somebody else on its side is looking at
     * ({@link SideObservers}); unlike the carrier it fires on men as readily as on machines. One bomb every few seconds
     * from a deep rack hits far softer than the LRM's ten-missile salvo, but it keeps falling after the launcher has
     * emptied its magazine, so the mortar wins a long fight the LRM wins a short one. It cannot carry a squad and is no
     * tougher than the carrier it replaces. Its sheet is the APC's hulls with an ImageGen turret at the hull's scale:
     * an open turntable, its tube raised with the bore toward the eye, ready bombs racked round the rear
     * (art-source/vehicles/mortar-carrier).
     */
    FIRE_SUPPORT_APC("graphics/battle/vehicles/mortar-carrier.png", 0, 4, -90f,
            0, 2.4f, 2.25f, .9f, 2.6f, 1.6f, 3.5f, .8f, 2.2f,
            1, 0f, .15208333f, 0f, 0f, 2.4f, -90f,
            TurretCatalogRegistry.VEHICLE_MORTAR_STRUCTURE_ID, 220f, 160f, 18f, 1.25f, .75f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.6f, 22f, 150f, accel, brakingAccel, maxSpeed);
        }
        @Override public Stance engagementStance() { return Stance.STANDOFF; }
    },
    /** Reinforced local convoy escort. Ordinary reinforcement APCs keep their lighter profile. */
    ESCORT_APC("graphics/battle/vehicles/army-apc.png", 0, 4, -90f,
            0, 3.2f, 3.0f, 1.2f, 3.4f, 1.8f, 3.5f, .8f, 2.8f,
            1, 0f, .20277778f, 0f, 0f, 1.42f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 620f, 420f, 24f, 1.25f, .9f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.2f, 26f, 150f, accel, brakingAccel, maxSpeed);
        }
    },
    /** Heavy convoy spearhead. Turns on conservative rolling arcs, and on the spot where no arc fits ({@link NeutralPivot}). */
    BREAKTHROUGH_TANK("graphics/battle/vehicles/army-tank.png", 0, 4, -90f,
            0, 4.3f, 3.8f, 2.2f, 3.1f, 1.0f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 7.789194f, -90f,
            TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID, 750f, 550f, 18f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            // Bounded curvature is deliberately shared with route proof and collision checks.
            return new BicycleBody(2.6f, 32f, 90f, accel, brakingAccel, maxSpeed, TANK_REVERSE_FRACTION);
        }
        @Override public float turretRecoilCells() { return .18f; }
        @Override public float turretHitShare() { return TANK_TURRET_HIT_SHARE; }
        @Override public float turretPoolShare() { return TANK_TURRET_POOL_SHARE; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(TANK_ARCS); }
    },
    /**
     * The breakthrough tank with a hull interceptor system: the same hull, twin cannons and plate, plus active
     * protection that takes top-attack rounds coming down on it ({@link #activeProtectionStructureId}), paid for in
     * a little speed for the launcher boxes and their power. The tank
     * line's answer to a missile mech, bought as its own rung rather than fitted to a stock tank; the stock tanks
     * carry none. Its sheet is the breakthrough hull with an ImageGen turret carrying the
     * fit: an interceptor launcher box on each rear flank and a sensor panel on each cheek
     * (art-source/vehicles/tank-screened).
     */
    SCREENED_TANK("graphics/battle/vehicles/army-tank-screened.png", 0, 4, -90f,
            0, 4.3f, 3.8f, 2.2f, 2.9f, .95f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 7.789194f, -90f,
            TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID, 750f, 550f, 18f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.6f, 32f, 90f, accel, brakingAccel, maxSpeed, TANK_REVERSE_FRACTION);
        }
        @Override public float turretRecoilCells() { return .18f; }
        @Override public float turretHitShare() { return TANK_TURRET_HIT_SHARE; }
        @Override public float turretPoolShare() { return TANK_TURRET_POOL_SHARE; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(TANK_ARCS); }
        @Override public String activeProtectionStructureId() { return SCREENED_TANK_ACTIVE_PROTECTION; }
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
    LASER_TANK("graphics/battle/vehicles/army-tank-laser.png", 0, 5, -90f,
            0, 4.3f, 3.85f, 2.25f, 3.3f, 1.05f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 1.6f, -90f,
            TurretCatalogRegistry.TANK_BURST_LASER_STRUCTURE_ID, 750f, 500f, 18f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.6f, 32f, 95f, accel, brakingAccel, maxSpeed, TANK_REVERSE_FRACTION);
        }
        @Override public float turretHitShare() { return TANK_TURRET_HIT_SHARE; }
        @Override public float turretPoolShare() { return TANK_TURRET_POOL_SHARE; }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(TANK_ARCS); }
        @Override public int turretFiringFrame() { return 4; }
        @Override public float fluxVentAftCells() { return LASER_VENT_AFT_CELLS; }
        @Override public float fluxVentHalfSpanCells() { return LASER_VENT_HALF_SPAN_CELLS; }
    },
    /**
     * Breaching sidegrade on the breakthrough hull: a dozer blade and the APC's twin MG instead of
     * the cannons, the heaviest plate of the line and the slowest drive. Its sheet carries the
     * breakthrough hull drawn at this longer frame's scale with the blade ahead of the nose, and
     * the APC's twin-MG frames at their own 1.42-cell registration (art-source/vehicles/tank-dozer).
     * The blade is real: it pushes through barricades and ordinary walls in its path ({@link DozerBlade}).
     */
    DOZER_BREACHER("graphics/battle/vehicles/army-tank-dozer.png", 0, 4, -90f,
            0, 4.6f, 3.8f, 2.2f, 2.4f, .9f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 1.42f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 1400f, 1000f, 38f, 1.45f, 1.1f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.8f, 30f, 80f, accel, brakingAccel, maxSpeed);
        }
        @Override public ArmorArcs.Profile armorArcs() { return arcs(DOZER_ARCS); }
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
     * The heavy APC's hull with the troop bay given over to a launcher: one mech LRM-15 pod on a turntable where the
     * twin MG stood, which stands up on it to fire. It is the long arm against armor, where the mortar carrier
     * ({@link #FIRE_SUPPORT_APC}) is the steady one against anything: its rounds arc over cover onto a machine somebody
     * else on its side is looking at ({@link SideObservers}), it holds fire at anything soft, and it fights from
     * beyond its target's reach ({@link Stance#STANDOFF}). It pays with no seats and thinner plate than the carrier it replaces. Its sheet is
     * the APC's hulls with the mech LRM pod drawn at the hull's scale (art-source/vehicles/missile-carriers).
     */
    LRM_CARRIER("graphics/battle/vehicles/lrm-carrier.png", 0, 4, -90f,
            0, 2.4f, 2.25f, .9f, 2.6f, 1.6f, 3.5f, .8f, 2.2f,
            1, 0f, .15208333f, 0f, 0f, 2.4f, -90f,
            TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID, 200f, 100f, 14f, 1.25f, .75f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.6f, 22f, 150f, accel, brakingAccel, maxSpeed);
        }
        @Override public Stance engagementStance() { return Stance.STANDOFF; }
        // The pod is 0.72 cells long and stays centred on the turntable however it is raised.
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
     * front strongest and the rear weakest, with the tank line and the dozer on a steeper one of their own. The turret
     * of an armed hull meets the same profile on its own facing. {@code -Dbattle.vehicle.armorArcs=false} is the
     * control: every arc the hull's one plate.
     */
    public ArmorArcs.Profile armorArcs() { return arcs(HULL_ARCS); }

    /** {@code false} is the control: a hull's plate is the same on every arc. */
    public static final String ARMOR_ARCS_PROPERTY = "battle.vehicle.armorArcs";

    private static final boolean ARMOR_ARCS =
            Boolean.parseBoolean(System.getProperty(ARMOR_ARCS_PROPERTY, "true"));

    /** Every hull's arcs unless it authors its own: a modest front, thinner sides, a thin rear. */
    static final ArmorArcs.Profile HULL_ARCS = new ArmorArcs.Profile(1f, 1.1f, .9f, .9f, .75f, .7f);

    /**
     * The tank line's: a thick sloped glacis, flanks a little thinner than the hull's nominal plate, and a thin
     * engine deck. The flank is deliberately mild: a tank caught broadside by a low-penetration gun is in a long, close
     * fight, and at 0.85 plate a dozer's MG out-traded the line's cannon tanks across a street.
     */
    static final ArmorArcs.Profile TANK_ARCS = new ArmorArcs.Profile(1f, 1.15f, 1f, .95f, .6f, .6f);

    /**
     * The dozer's: the tank line's flanks and engine deck without the glacis bonus. Its blade and appliqué already
     * make its plate the heaviest of the line, the best armor per credit there is.
     */
    static final ArmorArcs.Profile DOZER_ARCS = new ArmorArcs.Profile(1f, 1f, 1f, .95f, .6f, .6f);

    /**
     * The rocket pickup's: welded sheet with nothing sloped about it, so no front bonus. With one, the cheapest rig
     * in the bay took a third of its fights from the fire-support APC.
     */
    static final ArmorArcs.Profile FIELD_RIG_ARCS = new ArmorArcs.Profile(1f, 1f, .9f, .9f, .75f, .7f);

    private static ArmorArcs.Profile arcs(ArmorArcs.Profile authored) {
        return ARMOR_ARCS ? authored : ArmorArcs.Profile.FLAT;
    }

    /**
     * The turret-catalog structure of this hull's active protection, or null for a hull that carries none. Its mount
     * is a hull-borne point-defence system ({@code PointDefenseService#mountHull}): it engages hostile arcing,
     * top-attack rounds coming down on the hull, inside its reach, at its rate, from a finite magazine. Flat
     * rockets, gun rounds and rounds falling on anybody else are left alone. Only the screened tank carries one;
     * it is a rung of the tank line, not a fitting. {@code -Dbattle.vehicle.activeProtection=false} is the control.
     */
    public String activeProtectionStructureId() { return null; }

    /** The screened tank's active-protection structure, or null under the control. */
    private static final String SCREENED_TANK_ACTIVE_PROTECTION =
            Boolean.parseBoolean(System.getProperty("battle.vehicle.activeProtection", "true"))
                    ? "structure.tank-aps" : null;

    private static final float[] NO_TINT = {1f, 1f, 1f};
    private static final float[] UP_ARMORED_TINT = {.78f, .80f, .76f};

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

    public boolean isApc() {
        return this == HEAVY_APC || this == ESCORT_APC || this == UP_ARMORED_APC || this == FIRE_SUPPORT_APC
                || this == LRM_CARRIER;
    }

    /**
     * The technical's pickup and the light roles built on it. Every pickup is a <em>field rig</em> — jerry-rigged
     * from a civilian chassis, never a military faction's issue — which is how pattern channels weight it.
     */
    public boolean isPickup() {
        return this == MILITIA_TECHNICAL || this == SUPPLY_TRUCK || this == SCOUT_CAR || this == SRM_TECHNICAL;
    }

    /**
     * Tri-Tachyon's hover hulls. A skirt floats over water and rubble a wheel or a track cannot cross, and it has
     * no purchase to shove a body with ({@link HoverSkirt}).
     */
    public boolean isHover() { return this == HOVER_SCOUT || this == DRONE_SKIMMER; }

    /**
     * Whether this hull shoves bodies out of its way, by contact and by bow wave. A hover skirt has nothing to shove
     * with, so a hover hull cannot clear a road by driving into people; every wheeled and tracked hull does.
     */
    public boolean repelsBodies() { return !isHover(); }

    /** The breakthrough hull and the variants built on it. */
    public boolean isTank() {
        return this == BREAKTHROUGH_TANK || this == SCREENED_TANK || this == LASER_TANK
                || this == DOZER_BREACHER;
    }

    /**
     * Whether this hull carries a dozer blade that opens ground ahead of it ({@link DozerBlade}). Only the dozer
     * breacher does; its route proof and its rolling planner read the blade's breach view instead of the live grid.
     */
    public boolean hasBlade() { return this == DOZER_BREACHER; }

    /**
     * Whether this hull flattens undergrowth and lone boulders it drives into ({@link HeavyTracks}): every hull of
     * medium weight or more that runs on the ground, which is the APC lines, the rocket technical, the dozer and the
     * gun tanks. A pickup, a scout car or a hauler is stopped, as is a hover hull. Its planning and its motion read
     * the crush view.
     */
    public boolean crushesUndergrowth() { return weight() != Weight.LIGHT && !isHover(); }

    /**
     * Whether this hull drives through the light barricades its way covers ({@link BarricadeRam}): every hull that
     * crushes undergrowth but the dozer, whose blade pushes them down.
     */
    public boolean ramsBarricades() { return crushesUndergrowth() && !hasBlade(); }

    /** How heavy a hull is to something that runs into it ({@code colony-breach-nouns.md}, Brute). */
    public enum Weight { LIGHT, MEDIUM, HEAVY }

    /**
     * The hull's weight class: the pickups, hover hulls and haulers are light, the APC lines, the missile
     * carriers and the dozer medium, the gun tanks heavy. Read by a Brute's charge and by what a hull crushes.
     */
    public Weight weight() {
        if (this == BREAKTHROUGH_TANK || this == SCREENED_TANK || this == LASER_TANK) {
            return Weight.HEAVY;
        }
        if (isApc() || this == SRM_TECHNICAL || this == DOZER_BREACHER) return Weight.MEDIUM;
        return Weight.LIGHT;
    }

    /**
     * Mass in tonnes, for a body running into the hull ({@link HullImpact}): a pickup or a hover hull is
     * about a Brute's weight, an APC several times it, a gun tank many.
     */
    public float massTonnes() {
        if (isHover()) return 1.5f;
        return switch (weight()) {
            case LIGHT -> 2.5f;
            case MEDIUM -> 12f;
            case HEAVY -> 45f;
        };
    }

    /**
     * How hard the ground holds the hull against a slide, as decelerations in cells a second squared: along
     * the hull, where a wheel rolls and a track does not, and across it, where both scrub. A hover hull
     * barely touches the ground either way.
     */
    public float gripAlong() {
        if (isHover()) return .8f;
        return hasAnimatedTreads() ? 5f : 1.5f;
    }

    public float gripAcross() {
        if (isHover()) return .8f;
        return hasAnimatedTreads() ? 7f : 6f;
    }

    /** Only exposed tire/track tops scroll; covered technical/hauler wheels stay hidden. */
    public boolean hasAnimatedTreads() { return isApc() || isTank(); }

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
    /** The laser hulls' radiator fins, aft of centre on the breakthrough master (art-source/vehicles/tank-laser). */
    static final float LASER_VENT_AFT_CELLS = 1.8f;
    static final float LASER_VENT_HALF_SPAN_CELLS = 0.55f;

    public int turretFiringFrame() { return -1; }

    /**
     * How far aft of the hull's centre, in cells, the vents its gun's flux haze rises from
     * ({@code TurretMountDef#flux}), or zero for a hull that draws none. They span {@link #fluxVentHalfSpanCells}
     * either side of the centre line.
     */
    public float fluxVentAftCells() { return 0f; }

    /** Half the width of the flux vents across the hull, in cells ({@link #fluxVentAftCells}). */
    public float fluxVentHalfSpanCells() { return 0f; }

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
     * the frame runs past the hull to its blade, wheels and transparent margin.
     */
    public final float drawnLengthCells;
    /**
     * The footprint's length, nose to tail, cells: what the simulation collides, routes, crushes and is struck by.
     * The hull body's painted length, leaving out a dozer's blade.
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
            case ESCORT_APC, MILITIA_TECHNICAL, UP_ARMORED_APC, FIRE_SUPPORT_APC, DRONE_SKIMMER,
                 LRM_CARRIER -> LiftClass.MEDIUM;
            case BREAKTHROUGH_TANK, SCREENED_TANK, LASER_TANK, DOZER_BREACHER -> LiftClass.HEAVY;
            case CARGO_CARRIER -> null;
        };
    }
}
