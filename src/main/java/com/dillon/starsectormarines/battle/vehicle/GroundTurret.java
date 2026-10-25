package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.PointFireAim;

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

    /** Barrel facing in world frame (0° = +Y, positive CCW). */
    public float facingDeg;
    /** Sim-seconds until the turret can fire again. */
    public float cooldownTimer;
    /** Entity id of the currently locked target, or {@code 0L} when idle. */
    public long targetId;
    /** Rounds remaining in the magazine. Seeded from the authored mount capacity. */
    public int ammo;
    /** The gun's flux ({@code TurretMountDef#flux}); zero and unread for a mount without flux. */
    public float flux;
    /** Whether the gun overloaded and is venting: its trigger is held until its flux is gone. */
    public boolean overloaded;
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

    public GroundTurret(int startingAmmo) {
        this(startingAmmo, null);
    }

    public GroundTurret(int startingAmmo, HullTurretSection section) {
        this.ammo = startingAmmo;
        this.section = section;
    }

    /**
     * Whether damage has destroyed the gun. A destroyed turret stays drawn where it last pointed, never fires,
     * never traverses, and the hull reads as unarmed to every threat and engagement question.
     */
    public boolean destroyed() {
        return section != null && section.destroyed();
    }

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
