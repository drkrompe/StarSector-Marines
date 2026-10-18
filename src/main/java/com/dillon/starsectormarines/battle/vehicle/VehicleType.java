package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;

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
            /*capacity*/ 4, /*visualLengthCells*/ 2.4f, /*visualWidthCells*/ 1.4f,
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
            4, 2.5f, 1.45f, 2.3f, 1.4f, 3.2f, .9f, 2.3f,
            1, 0f, .15208333f, 0f, 0f, 1.42f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 300f, 320f, 24f, 1.3f, .78f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.65f, 22f, 140f, accel, brakingAccel, maxSpeed);
        }
        @Override public float[] hullTint() { return UP_ARMORED_TINT; }
    },
    /**
     * The heavy APC's hull with the troop bay given over to a heavy mortar: no seats, and
     * the roof gun is the mortar mount's own catalog art rather than the sheet's MG turret.
     * It shells what it can see; it cannot carry a squad and it is no tougher than the
     * carrier it replaces.
     */
    FIRE_SUPPORT_APC("graphics/battle/vehicles/army-apc.png", 0, 4, -90f,
            0, 2.4f, 1.4f, 2.6f, 1.6f, 3.5f, .8f, 2.2f,
            -1, 0f, .15208333f, 0f, 0f, 0f, 0f,
            TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID, 220f, 160f, 18f, 1.25f, .75f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.6f, 22f, 150f, accel, brakingAccel, maxSpeed);
        }
        @Override public boolean turretArtFromMount() { return true; }
    },
    /** Reinforced local convoy escort. Ordinary reinforcement APCs keep their lighter profile. */
    ESCORT_APC("graphics/battle/vehicles/army-apc.png", 0, 4, -90f,
            0, 3.2f, 1.8f, 3.4f, 1.8f, 3.5f, .8f, 2.8f,
            1, 0f, .20277778f, 0f, 0f, 1.42f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 620f, 420f, 24f, 1.25f, .9f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.2f, 26f, 150f, accel, brakingAccel, maxSpeed);
        }
    },
    /** Heavy convoy spearhead. Uses conservative rolling turns; neutral track pivot is not modeled. */
    BREAKTHROUGH_TANK("graphics/battle/vehicles/army-tank.png", 0, 4, -90f,
            0, 4.3f, 3.9f, 3.1f, 1.0f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 7.789194f, -90f,
            TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID, 1100f, 850f, 36f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            // Bounded curvature is deliberately shared with route proof and collision checks.
            return new BicycleBody(2.6f, 32f, 90f, accel, brakingAccel, maxSpeed);
        }
        @Override public float turretRecoilCells() { return .18f; }
    },
    /**
     * Breakthrough hull carrying one long heavy gun instead of the twin breach cannons:
     * more reach and penetration per round, a slower cycle and a slower hull. The breakthrough
     * hull art with its own long-gun turret frames (art-source/vehicles/tank-heavy-gun).
     */
    HEAVY_GUN_TANK("graphics/battle/vehicles/army-tank-heavy-gun.png", 0, 4, -90f,
            0, 4.3f, 3.9f, 2.6f, .85f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 7.789194f, -90f,
            TurretCatalogRegistry.TANK_HEAVY_GUN_STRUCTURE_ID, 1050f, 900f, 40f, 1.4f, 1.1f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.6f, 30f, 80f, accel, brakingAccel, maxSpeed);
        }
        @Override public float turretRecoilCells() { return .24f; }
    },
    /**
     * Breaching sidegrade on the breakthrough hull: a dozer blade and the APC's twin MG instead of
     * the cannons, the heaviest plate of the line and the slowest drive. Its sheet carries the
     * breakthrough hull drawn at this longer frame's scale with the blade ahead of the nose, and
     * the APC's twin-MG frames at their own 1.42-cell registration (art-source/vehicles/tank-dozer).
     * The blade is real: it pushes through barricades and ordinary walls in its path ({@link DozerBlade}).
     */
    DOZER_BREACHER("graphics/battle/vehicles/army-tank-dozer.png", 0, 4, -90f,
            0, 4.6f, 3.9f, 2.4f, .9f, 3.0f, .8f, 3.0f,
            1, 0f, 0f, 0f, 0f, 1.42f, -90f,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 1400f, 1000f, 38f, 1.45f, 1.1f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(2.8f, 30f, 80f, accel, brakingAccel, maxSpeed);
        }
    },
    /**
     * Workshop pickup with a pintle MG and welded plate, faction-neutral for authored local forces.
     * Its plate turns rifle fire, so it can stand in a road and draw it; crude rockets still get through.
     */
    MILITIA_TECHNICAL("graphics/battle/vehicles/militia-technical.png", 0, 4, -90f,
            0, 3.0f, 1.5f, 4.0f, 2.4f, 4.0f, .8f, 2.4f,
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
            0, 3.0f, 1.5f, 3.4f, 2.0f, 3.6f, .8f, 2.4f,
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
     * ({@link ScoutSight}). The fastest hull in the bay, and the thinnest: no gun, no seats, almost no plate, and its
     * sight is ordinary line of sight, so it has to be driven somewhere it can look.
     */
    SCOUT_CAR("graphics/battle/vehicles/scout-car.png", 0, 4, -90f,
            0, 2.5f, 1.5f, 5.2f, 3.0f, 4.5f, .8f, 2.2f,
            -1, 0, 0, 0, 0, 0, 0, null, 200f, 30f, 3f, 1.15f, .6f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.35f, 32f, 200f, accel, brakingAccel, maxSpeed);
        }
        @Override public float sightCells() { return 48f; }
    },
    /**
     * Tri-Tachyon's hover scout, a purpose-built corporate hull and not a field rig: a lift-fan skirt under a
     * sleek composite shell, a sensor cluster that reports to its side as the spotter pickup's mast does
     * ({@link ScoutSight}), and a light twin pulse emitter of the Wisp family for screening. Hover is a real
     * trade ({@link HoverSkirt}): it crosses water and rubble no wheel can, and it cannot shove a body aside.
     * It pays in plate — thinner than the technical — and it is slower than the stripped pickup. Its sheet draws the
     * emitter at the hull's own scale, so the turret frame spans the hull frame (art-source/vehicles/tt-hover).
     */
    HOVER_SCOUT("graphics/battle/vehicles/tt-hover-scout.png", 0, 4, -90f,
            0, 2.625f, 1.45f, 4.6f, 3.2f, 4.2f, .8f, 2.2f,
            1, 0f, -.15f, 0f, 0f, 2.625f, -90f,
            TurretCatalogRegistry.HOVER_PULSE_STRUCTURE_ID, 240f, 50f, 6f, 1.15f, .55f) {
        @Override public GroundBody createBody() {
            // A skirt slews faster than a steered axle; bounded curvature stays the planners' shared contract.
            return new BicycleBody(1.3f, 38f, 260f, accel, brakingAccel, maxSpeed);
        }
        @Override public float sightCells() { return 40f; }
    },
    /**
     * The hover scout's hull stretched over a drone bay: a Tri-Tachyon carrier whose drones are its weapon. It
     * carries no gun of its own; it launches a tethered flight of drone-pulse drones that escort it, crewed
     * remote control rather than free autonomy, and they fall when the carrier is wrecked. Drones lost stay lost.
     * Its sheet has no turret frames (art-source/vehicles/tt-hover).
     */
    DRONE_SKIMMER("graphics/battle/vehicles/tt-drone-skimmer.png", 0, 4, -90f,
            0, 3.25f, 1.85f, 3.6f, 2.4f, 3.8f, .8f, 2.6f,
            -1, 0, 0, 0, 0, 0, 0, null, 360f, 110f, 8f, 1.25f, .6f) {
        @Override public GroundBody createBody() {
            return new BicycleBody(1.7f, 34f, 220f, accel, brakingAccel, maxSpeed);
        }
        @Override public int droneComplement() { return 4; }
    },
    /** Long-wheelbase industrial container hauler; no passengers or roof weapon. */
    CARGO_CARRIER("graphics/battle/vehicles/wayline-hauler.png", 0, 2, -90f,
            0, 4.8f, 1.65f, 2.4f, 1.2f, 3.0f, .8f, 3.2f,
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

    /** Drones this hull launches and tethers to itself, or 0 for every hull that carries none. */
    public int droneComplement() { return 0; }

    /** How far this hull reports what it sees to its side, in cells; 0 for a hull that is not a sight source. */
    public float sightCells() { return 0f; }

    /** Visual recoil from actual recent shots; does not change the ballistic hardpoint. */
    public float turretRecoilCells() { return 0f; }

    private static final float[] NO_TINT = {1f, 1f, 1f};
    private static final float[] UP_ARMORED_TINT = {.78f, .80f, .76f};

    public boolean isApc() {
        return this == HEAVY_APC || this == ESCORT_APC || this == UP_ARMORED_APC || this == FIRE_SUPPORT_APC;
    }

    /**
     * The technical's pickup and the light roles built on it. Every pickup is a <em>field rig</em> — jerry-rigged
     * from a civilian chassis, never a military faction's issue — which is how pattern channels weight it.
     */
    public boolean isPickup() {
        return this == MILITIA_TECHNICAL || this == SUPPLY_TRUCK || this == SCOUT_CAR;
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
        return this == BREAKTHROUGH_TANK || this == HEAVY_GUN_TANK || this == DOZER_BREACHER;
    }

    /**
     * Whether this hull carries a dozer blade that opens ground ahead of it ({@link DozerBlade}). Only the dozer
     * breacher does; its route proof and its rolling planner read the blade's breach view instead of the live grid.
     */
    public boolean hasBlade() { return this == DOZER_BREACHER; }

    /** Only exposed tire/track tops scroll; covered technical/hauler wheels stay hidden. */
    public boolean hasAnimatedTreads() { return isApc() || isTank(); }

    /** Multiplier on the hull sprite's colour; white for every hull drawn as painted. */
    public float[] hullTint() { return NO_TINT; }

    /**
     * Whether the turret is drawn from its catalog mount's own sprite (the way an aircraft's
     * hull turrets are) rather than from a frame of this hull's sheet.
     */
    public boolean turretArtFromMount() { return false; }

    /**
     * Whether this variant's hull and turret art exist. A variant still waiting on art keeps
     * its stats and pattern but is never offered as a card, as a loadout card whose
     * presentation is pending is not.
     */
    public boolean presentationDeclared() { return true; }

    public int wreckHullFrame() { return this == CARGO_CARRIER ? 1 : 2; }
    public int wreckTurretFrame() { return turretFrame < 0 ? -1 : 3; }

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
    /** Sprite long-axis (nose-to-tail) in cells at facing 0. */
    public final float visualLengthCells;
    /** Sprite short-axis (side-to-side) in cells at facing 0. */
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
    /** Visual size of the turret sprite (longest axis), cells. Same semantics as {@link #visualLengthCells}. */
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
        return turretStructureId != null && (turretFrame >= 0 || turretArtFromMount());
    }

    public StructureDef turretStructure() {
        return hasTurretWeapon()
                ? TurretCatalogRegistry.requireStructure(turretStructureId) : null;
    }

    VehicleType(String spritePath, int spriteFrame, int frameCount,
                float spriteFacingOffsetDeg,
                int capacity, float visualLengthCells, float visualWidthCells,
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
            case HEAVY_APC, SUPPLY_TRUCK, SCOUT_CAR, HOVER_SCOUT -> LiftClass.LIGHT;
            case ESCORT_APC, MILITIA_TECHNICAL, UP_ARMORED_APC, FIRE_SUPPORT_APC, DRONE_SKIMMER -> LiftClass.MEDIUM;
            case BREAKTHROUGH_TANK, HEAVY_GUN_TANK, DOZER_BREACHER -> LiftClass.HEAVY;
            case CARGO_CARRIER -> null;
        };
    }
}
