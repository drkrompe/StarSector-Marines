package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.RotaryFeed;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.LoaderPace;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable per-vehicle turret state — the ground twin of the air
 * {@link com.dillon.starsectormarines.battle.air.AirTurrets} bag. Held in the
 * {@code GROUND_TURRET} OBJECT component (presence == "armed"): only a vehicle whose
 * {@link VehicleType#hasTurretWeapon()} carries one; an unarmed truck has no
 * {@code GROUND_TURRET}. The immutable weapon config (turn rate, range, cooldown,
 * burst) stays on the structure definition resolved by
 * {@link VehicleType#turretStructureId}; this bag is the live aim/fire state
 * {@link GroundVehicleTurretSystem} drives each tick.
 *
 * <p>Extracted from {@code Vehicle}'s former inline {@code turret*} fields in the
 * convoy-{@code Vehicle}-into-world epic
 * ({@code ecs-nouns.md}). During the aliasing
 * phase the same instance is held by both the {@code Vehicle} handle and the
 * {@code GROUND_TURRET} column.
 */
public final class GroundTurret {

    /**
     * The structure this hull's gun stands in when a weapon other than its own is fitted ({@link VehicleGun}); null for
     * the hull's own gun. Read through {@link #structureFor}.
     */
    public StructureDef structure;
    /** The mech weapon in the hull's gun card that {@link #structure} was derived from; null for the hull's own gun. */
    public MechWeaponComponent weapon;
    /**
     * Whether this gun stands up to fire as an arcing rack does ({@link #elevation}): a fitted weapon asks for it by
     * being a missile pod, the hull's own gun by its type.
     */
    public boolean raises(VehicleType type) {
        return weapon != null ? weapon.hardpointType == MechWeaponComponent.HardpointType.MISSILE
                : type.turretRaiseFold() < 1f;
    }

    /**
     * The barrels and belt of a fitted rotary gun ({@link RotaryFeed}), which fires while its trigger is held and
     * spends its belt rather than a magazine; null for any other gun.
     */
    public RotaryFeed rotary;
    /**
     * Whether the gun draws on no magazine: a mount authored with no capacity ({@code TurretMountDef#ammoCapacity}
     * zero) never runs dry, and only its flux (or, for a rotary gun, its belt) limits how long it fires.
     */
    public boolean unlimited;

    /** Whether the gun has a round to fire: any, for a gun with no magazine. */
    public boolean loaded() {
        return unlimited || ammo > 0;
    }

    /** One round leaves the gun; a gun with no magazine has none to spend. */
    public void spend() {
        if (!unlimited) ammo--;
    }

    /** Barrel facing in world frame (0° = +Y, positive CCW). */
    public float facingDeg;
    /** Sim-seconds until the turret can fire again. */
    public float cooldownTimer;
    /** Entity id of the currently locked target, or {@code 0L} when idle. */
    public long targetId;
    /** Rounds remaining in the magazine. Seeded from the authored mount capacity. */
    public int ammo;
    /** Rounds the magazine holds full: what it was set down with, the mount's capacity and a ready rack's share. */
    public final int capacity;
    /**
     * Seconds the hull's loader takes to put one trigger pack back ({@code TurretMountDef#loaderSeconds}, quicker
     * with an accelerated loader), or infinite for a gun nothing reloads. Capacity bounds the ready rounds, not how
     * many packs come back in a battle.
     */
    public float loaderSeconds = Float.POSITIVE_INFINITY;
    /** Loader work toward the next pack, in seconds. */
    public float loaderProgress;
    /**
     * The hull's one flux reservoir ({@link HullReservoir}), the same instance on the main gun and every secondary
     * mount, so every round any of them fires draws on it; null on the per-mount control path and for a gun built
     * outside a hull. While it is set, {@link #flux} and {@link #overloaded} stay unused.
     */
    public MechFlux reservoir;
    /** The gun's own flux on the per-mount control path ({@code TurretMountDef#flux}); zero and unread otherwise. */
    public float flux;
    /** Whether the gun overloaded and is venting: its trigger is held until its flux is gone. Per-mount path only. */
    public boolean overloaded;

    /** The flux the gun reads against: the hull's reservoir, or its own bank on the per-mount path. */
    public float fluxValue() {
        return reservoir != null ? reservoir.value() : flux;
    }

    /** Whether the gun's flux holds its trigger: the reservoir venting, or the gun overloaded on the per-mount path. */
    public boolean fluxVenting() {
        return reservoir != null ? reservoir.venting() : overloaded;
    }
    /** Rounds left in the current burst (excluding the trigger-pull round). */
    public int burstRemaining;
    /** Sim-seconds until the next burst round fires. */
    public float burstTimer;
    /** The aircraft the gun is on, or {@code 0L}; set only for a mount that engages air. */
    public long airTargetId;
    /** Whether the committed burst is going into the air at {@link #airTargetId} rather than at a ground target. */
    public boolean burstAir;
    /** Entity id of the target locked when the current burst started. */
    public long burstTargetId;
    /** Sim-seconds until a missile launcher next looks for a new target. */
    public float relookTimer;
    /**
     * A launcher's fire mission, or {@code 0L}: the body it is told to shell. It takes it whenever it may, on its own
     * sight or a spotter's, before any other, and picks its own target only while nobody on its side sees it.
     */
    public long missionTargetId;
    /**
     * How far an arcing launcher stands raised on its trunnion, 0 lying flat to 1 raised to fire. A look only:
     * the renderer foreshortens the rack by it, and nothing in the sim decides anything from it.
     */
    public float elevation;
    /**
     * Cells to what a raised launcher last aimed at, NaN before it has aimed at anything. A look only, beside
     * {@link #elevation}: the renderer pitches the rack to the angle its rounds' drawn arc leaves at, which is
     * steeper the nearer the target.
     */
    public float raiseRangeCells = Float.NaN;
    /** World point committed by a manual trigger; null for autonomous bursts. */
    public PointFireAim burstPointAim;
    /**
     * Further salvos the launcher's trigger has committed to without waiting out its cooldown
     * ({@code SalvoChain}): the rest of the crew's chosen count, or unbounded while a driver holds the button.
     */
    public int chainSalvos;
    /** The body the crew's chain was committed against. */
    public long chainTargetId;
    /** Seconds until a chained salvo may follow the one that has just gone. */
    public float chainGap;
    /** Rounds a driver's current hold has fired, for the HUD; zero when the button is up. */
    public int holdRounds;

    /** The turret as a section of the hull, or null when the hull authors none (its gun cannot be shot away). */
    public final HullTurretSection section;

    /**
     * The hull's mounts beside this main turret, in {@link VehicleType#secondaryMounts()} order: seeded at spawn,
     * and fitted by a scene that stands up a hull with a second weapon. Empty on most hulls.
     */
    public final List<MountedGun> secondaries = new ArrayList<>();

    /** Who has been hitting the hull, and from where: whether its crew is caught in crossfire. */
    public final Crossfire crossfire = new Crossfire();

    /** The structure this turret stands in: the fitted gun's, or {@code type}'s own. */
    public StructureDef structureFor(VehicleType type) {
        return structure != null ? structure : type.turretStructure();
    }

    public GroundTurret(int startingAmmo) {
        this(startingAmmo, null);
    }

    public GroundTurret(int startingAmmo, HullTurretSection section) {
        this.ammo = startingAmmo;
        this.capacity = startingAmmo;
        this.section = section;
    }

    /**
     * A full gun on {@code mount}, its loader as the hull's kits make it, with {@code rounds} in a
     * magazine of that size.
     */
    public static GroundTurret fitted(TurretMountDef mount, int rounds, VehicleKit.Effect kits,
                                      HullTurretSection section) {
        GroundTurret turret = new GroundTurret(rounds, section);
        turret.unlimited = mount.ammoCapacity == 0;
        turret.loaderSeconds = LoaderPace.secondsPerPack(mount.loaderSeconds, kits.loaderFactor());
        return turret;
    }

    /**
     * The flux this gun reads against: the hull reservoir's capacity, or on the per-mount path its mount's own.
     * Zero for a mount that builds no flux.
     */
    public float fluxCapacity(TurretMountDef mount) {
        if (mount.flux == null) return 0f;
        return reservoir != null ? reservoir.capacity() : mount.flux.control().capacity();
    }

    /**
     * Runs the loader for {@code dt}: a pack of {@code packRounds} comes back each {@link #loaderSeconds}, firing
     * or not, until the magazine is full again, the way a mech's replenisher restores its racks.
     */
    public void advanceLoader(float dt, int packRounds) {
        if (ammo >= capacity || !Float.isFinite(loaderSeconds)) {
            loaderProgress = 0f;
            return;
        }
        loaderProgress += Math.max(0f, dt);
        while (loaderProgress >= loaderSeconds && ammo < capacity) {
            ammo = Math.min(capacity, ammo + Math.max(1, packRounds));
            loaderProgress -= loaderSeconds;
        }
        if (ammo >= capacity) loaderProgress = 0f;
    }

    /**
     * Whether damage has destroyed the gun. A destroyed turret stays drawn where it last pointed, never fires,
     * never traverses, and the hull reads as unarmed to every threat and engagement question.
     */
    public boolean destroyed() {
        return removed || (section != null && section.destroyed());
    }

    /**
     * Whether no head is fitted: a modular hull's gun card is empty ({@link VehicleType#hasModularTurret}), so the ring
     * stands bare. It reads as a shot-away gun to the rest of the simulation, and is drawn as the bare ring.
     */
    public boolean removed;

    /** Releases queued work without refunding ammunition or resetting the fire clock. */
    public void clearQueuedFire() {
        targetId = 0L;
        airTargetId = 0L;
        burstAir = false;
        burstTargetId = 0L;
        burstPointAim = null;
        burstRemaining = 0;
        burstTimer = 0f;
        chainSalvos = 0;
        holdRounds = 0;
    }

    /** Whether the next trigger would be a chained salvo: its cooldown is running, but a chain is pending. */
    public boolean chainedTrigger() {
        return cooldownTimer > 0f && chainSalvos > 0;
    }

    /** Whether the cadence admits a trigger: the cooldown is done, or a chained salvo's predecessor has gone. */
    public boolean clockReady() {
        return cooldownTimer <= 0f || chainedTrigger() && chainGap <= 0f;
    }

    /** Books a trigger: a chained one spends a salvo of the chain. */
    public void commitChained() {
        if (chainedTrigger() && chainSalvos != Integer.MAX_VALUE) chainSalvos--;
    }
}
