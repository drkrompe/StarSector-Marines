package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.assault.MachineGuard;
import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.SalvoChain;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.mech.MechTargeting;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.turret.TurretFireSink;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.function.LongPredicate;

/** One aim, magazine, and cadence authority for autonomous and controlled vehicle turrets. */
public final class GroundVehicleTurretSystem {
    /**
     * A turret with nothing to shoot comes back to the hull's heading at this share of its traverse rate, so it
     * reads as settling rather than snapping. Facing is a world bearing, so without it a turret kept whatever
     * bearing its last target left it on while the hull turned underneath.
     */
    static final float IDLE_RETURN_RATE_SHARE = 0.5f;

    private final ConvoyService convoy;
    private final UnitRosterService roster;
    private final TacticalScoring scoring;
    private final NavigationGrid grid;
    private final World world;
    private final TurretFireSink fireSink;
    private SideSpotting spotting = SideSpotting.NONE;
    private VehicleAirFire airFire = VehicleAirFire.NONE;
    /** Standing assaults whose fire directives this pass serves; null when none are given. */
    private VehicleMoveOrderService assaults;

    /** Whether point defence screens a target, for a launcher choosing how many salvos to chain at it. */
    private LongPredicate screened = target -> false;

    /** Seconds between a launcher's looks for a new target ({@link #tickLauncher}). */
    static final float LAUNCHER_RELOOK_SECONDS = 0.5f;

    public GroundVehicleTurretSystem(ConvoyService convoy, UnitRosterService roster,
                                     TacticalScoring scoring, NavigationGrid grid,
                                     World world, TurretFireSink fireSink) {
        this.convoy = convoy;
        this.roster = roster;
        this.scoring = scoring;
        this.grid = grid;
        this.world = world;
        this.fireSink = fireSink;
    }

    /** Whether point defence screens a body; a screened target draws a launcher's whole chain. */
    public void setScreening(LongPredicate screened) {
        this.screened = screened != null ? screened : target -> false;
    }

    /** Who calls targets for an indirect-fire launcher that cannot see them itself. */
    public void setSpotting(SideSpotting spotting) {
        this.spotting = spotting != null ? spotting : SideSpotting.NONE;
    }

    /** Where the craft are, for a mount that engages air ({@link TurretMountDef#engagesAir}). */
    public void setAirFire(VehicleAirFire airFire) {
        this.airFire = airFire != null ? airFire : VehicleAirFire.NONE;
    }

    /** The order mailbox whose standing assaults direct autonomous turrets ({@code MachineAssaults}). */
    public void setAssaults(VehicleMoveOrderService orders) {
        this.assaults = orders;
    }

    public void cancelQueuedFire(long id) {
        if (!convoy.isVehicle(id)) return;
        GroundTurret turret = convoy.turret(id);
        if (turret == null) return;
        turret.clearQueuedFire();
        for (MountedGun gun : turret.secondaries) gun.turret().clearQueuedFire();
    }

    /** One tick with the controlled hull's left button only, on LINKED. */
    public void tick(float dt, long controlledId, PointFireAim point, boolean trigger) {
        tick(dt, controlledId, point, trigger, false, TriggerPicks.LINKED);
    }

    /**
     * One tick of every armed hull's guns. On the controlled hull the left button fires what its pick names in the
     * left group and the right button what its pick names in the right ({@link VehicleFireGroups}); a mount neither
     * names holds fire, and a crewed mount keeps its own gunner.
     */
    public void tick(float dt, long controlledId, PointFireAim point, boolean primary, boolean secondary,
                     TriggerPicks picks) {
        if (!Float.isFinite(dt) || dt <= 0f) return;
        for (long id : convoy.entityIds()) {
            if (!convoy.isTargetable(id)) continue;
            VehicleType type = convoy.vehicleType(id);
            if (!type.hasTurretWeapon()) continue;
            GroundTurret turret = convoy.turret(id);
            turret.crossfire.advance(dt);
            GroundBody body = convoy.body(id);
            int held = id == controlledId
                    ? picks.heldMask(VehicleFireGroups.mounts(type, turret), primary, secondary) : 0;
            tickSecondaries(id, turret, body, dt, id == controlledId, point, held);
            if (turret.destroyed()) {
                // A shot-away gun neither fires nor traverses; it stays where it last pointed.
                turret.clearQueuedFire();
                continue;
            }
            StructureDef structure = type.turretStructure();
            TurretMountGeometry.Point mount = mountPoint(type, body);
            float mountX = mount.x();
            float mountY = mount.y();
            dissipate(turret, structure.mount, dt);
            if (turret.chainGap > 0f) turret.chainGap -= dt;
            if (turret.overloaded && turret.burstRemaining > 0) turret.clearQueuedFire();
            if (id == controlledId) {
                tickManual(id, turret, body, structure, mountX, mountY, dt, point,
                        FireGroups.contains(held, VehicleFireGroups.MAIN));
            } else {
                if (turret.burstPointAim != null) turret.clearQueuedFire();
                tickAutonomous(id, turret, body, structure, mountX, mountY, dt);
            }
            if (type.turretRaiseFold() < 1f) {
                raise(turret, structure.mount.weapon, dt);
                aimRange(turret, mountX, mountY);
            }
        }
    }

    /**
     * The hull's secondary mounts ({@link GroundTurret#secondaries}), each a turret of its own on the same aim,
     * magazine and cadence rules as the main gun. On the controlled hull a mount is the driver's unless it is
     * crewed, and fires while {@code held} names it ({@code index + 1}).
     */
    private void tickSecondaries(long id, GroundTurret main, GroundBody body, float dt, boolean controlled,
                                 PointFireAim point, int held) {
        for (int i = 0; i < main.secondaries.size(); i++) {
            MountedGun gun = main.secondaries.get(i);
            GroundTurret turret = gun.turret();
            StructureDef structure = gun.mount().structure();
            TurretMountGeometry.Point at = mountPoint(body, gun.mount().mountX(), gun.mount().mountY());
            dissipate(turret, structure.mount, dt);
            if (turret.chainGap > 0f) turret.chainGap -= dt;
            if (turret.overloaded && turret.burstRemaining > 0) turret.clearQueuedFire();
            if (controlled && !gun.mount().crewed()) {
                tickManual(id, turret, body, structure, at.x(), at.y(), dt, point,
                        FireGroups.contains(held, i + 1));
            } else {
                if (turret.burstPointAim != null) turret.clearQueuedFire();
                tickAutonomous(id, turret, body, structure, at.x(), at.y(), dt);
            }
        }
    }

    /**
     * Where a hull's turret ring stands in the world: the type's mount offset turned by the hull's heading. A
     * player's point fire is measured from here, so the HUD's minimum-range reading reads the same point.
     */
    public static TurretMountGeometry.Point mountPoint(VehicleType type, GroundBody body) {
        return mountPoint(body, type.turretMountX, type.turretMountY);
    }

    /** Where a mount {@code localX, localY} cells from the hull's centre stands in the world. */
    public static TurretMountGeometry.Point mountPoint(GroundBody body, float localX, float localY) {
        float radians = (float) Math.toRadians(body.facingDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new TurretMountGeometry.Point(body.x + localX * cos - localY * sin,
                body.y + localX * sin + localY * cos);
    }

    /** Remembers how far off a launcher's aim is ({@link GroundTurret#raiseRangeCells}); kept when it has none. */
    private void aimRange(GroundTurret turret, float mountX, float mountY) {
        long target = world.isAlive(turret.burstTargetId) ? turret.burstTargetId
                : world.isAlive(turret.targetId) ? turret.targetId : 0L;
        if (target != 0L) {
            turret.raiseRangeCells = (float) Math.hypot(world.x(target) - mountX, world.y(target) - mountY);
        } else if (turret.burstPointAim != null) {
            turret.raiseRangeCells = (float) Math.hypot(turret.burstPointAim.x() - mountX,
                    turret.burstPointAim.y() - mountY);
        }
    }

    /** Seconds a launcher takes to stand up on its trunnion, or to lie back down. */
    static final float RAISE_SECONDS = 0.6f;
    /** Seconds a launcher stays raised after its last salvo before it lies back down without a target. */
    static final float RAISED_AFTER_SALVO_SECONDS = 2.0f;

    /**
     * Stands a launcher up while it has something to fire at, is firing, or has just fired, and lays it back down
     * otherwise ({@link GroundTurret#elevation}). Nothing in the sim reads it: it is how an arcing rack looks.
     */
    static void raise(GroundTurret turret, WeaponDef weapon, float dt) {
        boolean raised = turret.targetId != 0L || turret.burstRemaining > 0 || turret.burstPointAim != null
                || turret.cooldownTimer > weapon.cooldown - RAISED_AFTER_SALVO_SECONDS;
        float step = dt / RAISE_SECONDS;
        turret.elevation = raised ? Math.min(1f, turret.elevation + step) : Math.max(0f, turret.elevation - step);
    }

    private void tickManual(long id, GroundTurret turret, GroundBody body, StructureDef structure,
                            float mountX, float mountY, float dt, PointFireAim cursor, boolean trigger) {
        if (turret.burstRemaining > 0 && turret.burstPointAim == null) turret.clearQueuedFire();
        if (turret.ammo <= 0) turret.clearQueuedFire();
        turret.targetId = 0L;
        TurretMountDef mount = structure.mount;
        WeaponDef weapon = mount.weapon;
        // A held launcher chains its salvos (SalvoChain); letting go starts its cooldown from the last one.
        boolean holding = trigger && SalvoChain.chains(weapon);
        if (holding) {
            // Only a hold that has already fired chains: a fresh press waits out the cooldown.
            if (turret.holdRounds > 0) turret.chainSalvos = Integer.MAX_VALUE;
        } else {
            turret.chainSalvos = 0;
            turret.holdRounds = 0;
        }

        PointFireAim aim = turret.burstPointAim != null ? turret.burstPointAim : cursor;
        if (aim != null && aim.validFrom(mountX, mountY)) {
            turret.facingDeg = TurretAim.slewToward(turret.facingDeg,
                    TurretAim.bearingTo(mountX, mountY, aim.x(), aim.y()),
                    mount.turnRateDegPerSec * dt);
        }
        if (turret.ammo <= 0) return;
        // APC cadence starts its cooldown recovery after the committed burst finishes.
        if (turret.burstRemaining > 0) {
            turret.burstTimer -= dt;
            int release = TurretMountGeometry.releaseIndex(weapon.burstCount, turret.burstRemaining);
            if (turret.burstTimer <= 0f
                    && firePoint(id, turret, body, structure, mountX, mountY, aim, release)) {
                turret.ammo--;
                if (holding) turret.holdRounds++;
                addFlux(turret, structure.mount);
                finishBurstRound(turret, weapon);
            }
            return;
        }
        if (turret.cooldownTimer > 0f) turret.cooldownTimer -= dt;
        if (!trigger || !turret.clockReady() || !ready(turret, mount)
                || !firePoint(id, turret, body, structure, mountX, mountY, aim, 0)) return;
        turret.commitChained();
        turret.ammo--;
        if (holding) turret.holdRounds++;
        addFlux(turret, structure.mount);
        turret.cooldownTimer = weapon.cooldown;
        turret.burstRemaining = turret.ammo > 0 ? Math.max(0, weapon.burstCount - 1) : 0;
        turret.burstTimer = turret.burstRemaining > 0 ? weapon.burstSpacing : 0f;
        turret.burstPointAim = turret.burstRemaining > 0 ? aim : null;
        turret.burstTargetId = 0L;
    }

    private boolean firePoint(long id, GroundTurret turret, GroundBody body, StructureDef structure,
                              float mountX, float mountY, PointFireAim aim, int release) {
        if (aim == null || !aim.validFrom(mountX, mountY)
                || !aligned(turret.facingDeg, mountX, mountY, aim)) return false;
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                mountX, mountY, turret.facingDeg, structure.mount, release);
        if (!aim.validFrom(muzzle.x(), muzzle.y())
                || !aligned(turret.facingDeg, muzzle.x(), muzzle.y(), aim)
                || !barrelClear(body, muzzle)) return false;
        return fireSink.firePoint(id, mountX, mountY, convoy.faction(id), structure,
                aim, turret.facingDeg, release);
    }

    private static boolean aligned(float facing, float x, float y, PointFireAim aim) {
        return Math.abs(TurretAim.shortestAngleDelta(facing,
                TurretAim.bearingTo(x, y, aim.x(), aim.y()))) <= TurretAim.FIRE_ARC_DEG;
    }

    private boolean barrelClear(GroundBody body, TurretMountGeometry.Point muzzle) {
        long wall = grid.firstWallOnLine(body.x, body.y, muzzle.x(), muzzle.y());
        return (int) wall == -1 && (int) (wall >>> 32) == -1
                && grid.firstProjectileBlockingEdgeBarrierOnLine(
                body.x, body.y, muzzle.x(), muzzle.y()) == null;
    }

    private void tickAutonomous(long id, GroundTurret turret, GroundBody body, StructureDef structure,
                                float mountX, float mountY, float dt) {
        TurretMountDef mount = structure.mount;
        if (turret.ammo <= 0) {
            turret.clearQueuedFire();
            turret.targetId = 0L;
            turret.facingDeg = idleFacing(turret.facingDeg, idleHeading(id, body), mount.turnRateDegPerSec, dt);
            return;
        }
        WeaponDef weapon = mount.weapon;
        if (turret.burstAir) {
            tickAirBurst(id, turret, structure, mountX, mountY, dt);
            return;
        }
        long burstTarget = roster.isAliveById(turret.burstTargetId) ? turret.burstTargetId : 0L;
        // Keep autonomous APC cadence: no cooldown or traverse tick during a committed burst. A beam is the
        // exception: its lance is held, so the turret tracks its target through it and the beam drags with it. So
        // is a missile rack, which opens its salvo inside the fire arc and keeps coming round onto the target while
        // it ripples, so the launcher points where its missiles go.
        if (turret.burstRemaining > 0) {
            boolean tracks = weapon.hitscanBeam() || mount.hardenedTargetsOnly || weapon.indirectFire;
            if (tracks && burstTarget != 0L && world.isAlive(burstTarget)) {
                turret.facingDeg = TurretAim.slewToward(turret.facingDeg,
                        TurretAim.bearingTo(mountX, mountY, world.x(burstTarget), world.y(burstTarget)),
                        mount.turnRateDegPerSec * dt);
            }
            turret.burstTimer -= dt;
            int release = TurretMountGeometry.releaseIndex(weapon.burstCount, turret.burstRemaining);
            if (turret.burstTimer <= 0f && burstTarget != 0L && world.isAlive(burstTarget)
                    && barrelClear(body, TurretMountGeometry.muzzle(
                    mountX, mountY, turret.facingDeg, mount, release))) {
                if (fireSink.fire(id, mountX, mountY, convoy.faction(id), structure,
                        burstTarget, false, true, turret.facingDeg, release)) {
                    turret.ammo--;
                    addFlux(turret, structure.mount);
                    finishBurstRound(turret, weapon);
                } else {
                    // The crew held a round that would have hit their own side: the rest of the burst stays in
                    // the gun, and the next pull is the aim loop's.
                    turret.clearQueuedFire();
                }
            }
            if (burstTarget == 0L || !world.isAlive(burstTarget)) turret.clearQueuedFire();
            return;
        }

        if (mount.hardenedTargetsOnly || weapon.indirectFire) {
            tickLauncher(id, turret, body, structure, mountX, mountY, dt);
            return;
        }

        long held = roster.isAliveById(turret.targetId) ? turret.targetId : 0L;
        if (mount.engagesAir && !closeThreat(held, mountX, mountY)
                && tickAir(id, turret, structure, mountX, mountY, dt)) {
            return;
        }
        MachineAssault assault = assaults != null ? assaults.assault(id) : null;
        // An assault directs the gun ahead of the ordinary pick, except that a visible enemy
        // close enough to be self-defence keeps the gun: the believed occupant first, then the
        // wall that shelters one when this gun can bring it down.
        if (assault != null && !closeThreat(held, mountX, mountY)) {
            long occupant = assault.occupantId();
            if (occupant != 0L && roster.isAliveById(occupant)) {
                held = occupant;
            } else if (assault.hasWall() && weapon.wallDamage > 0) {
                tickAssaultWall(id, turret, body, structure, mountX, mountY, dt, assault);
                return;
            }
        }

        TurretAim.State aim = new TurretAim.State();
        aim.originCellX = (int) Math.floor(mountX);
        aim.originCellY = (int) Math.floor(mountY);
        aim.originX = mountX;
        aim.originY = mountY;
        aim.faction = convoy.faction(id);
        aim.facingDegrees = turret.facingDeg;
        aim.turnRateDegPerSec = mount.turnRateDegPerSec;
        if (weapon.hitscanBeam()) aim.fireArcDeg = TurretAim.BEAM_OPEN_ARC_DEG;
        aim.attackRange = weapon.range;
        aim.minRange = weapon.minRange;
        // A venting gun holds the trigger the way a cooldown does, until its flux is gone.
        aim.cooldownTimer = Math.max(turret.cooldownTimer, fluxWait(turret, mount));
        aim.attackCooldown = weapon.cooldown;
        aim.tracksFliers = weapon.tracksFliers();
        aim.target = held;
        float agedCooldown = turret.cooldownTimer > 0f ? turret.cooldownTimer - dt : turret.cooldownTimer;
        TurretAim.tick(aim, scoring, grid, world, roster.vision(), dt);
        turret.facingDeg = aim.facingDegrees;
        turret.cooldownTimer = aim.cooldownTimer;
        turret.targetId = aim.target;
        if (aim.target == 0L) {
            turret.facingDeg = idleFacing(turret.facingDeg, idleHeading(id, body), mount.turnRateDegPerSec, dt);
            return;
        }
        if (aim.fireThisTick && aim.target != 0L) {
            if (!barrelClear(body, TurretMountGeometry.muzzle(mountX, mountY, turret.facingDeg, mount, 0))) {
                turret.cooldownTimer = agedCooldown;
                return;
            }
            if (!fireSink.fire(id, mountX, mountY, convoy.faction(id), structure, aim.target,
                    false, aim.lastFireHadLos, turret.facingDeg, 0)) return;
            turret.ammo--;
            addFlux(turret, structure.mount);
            if (turret.ammo > 0 && weapon.burstCount > 1 && world.isAlive(aim.target)) {
                turret.burstRemaining = weapon.burstCount - 1;
                turret.burstTimer = weapon.burstSpacing;
                turret.burstTargetId = aim.target;
            }
        }
    }

    /**
     * The air half of a gun that reaches it. A hostile craft in reach takes the gun ahead of the ground pick, since
     * it is overhead for seconds and the ground will still be there; a man close enough to be self-defence keeps it
     * (the caller asks that first). The gun slews onto the craft and fires its burst on its own cadence, magazine and
     * bank. Returns false when the sky in reach is empty, and the ground pick runs as before.
     */
    private boolean tickAir(long id, GroundTurret turret, StructureDef structure, float mountX, float mountY,
                            float dt) {
        TurretMountDef mount = structure.mount;
        WeaponDef weapon = mount.weapon;
        Faction side = convoy.faction(id);
        long craft = airFire.pick(side, mountX, mountY, weapon.range, turret.airTargetId);
        turret.airTargetId = craft;
        if (craft == 0L) return false;
        turret.targetId = 0L;
        float bearing = TurretAim.bearingTo(mountX, mountY, world.x(craft), world.y(craft));
        turret.facingDeg = TurretAim.slewToward(turret.facingDeg, bearing, mount.turnRateDegPerSec * dt);
        if (turret.cooldownTimer > 0f) turret.cooldownTimer -= dt;
        if (turret.cooldownTimer > 0f || !ready(turret, mount)
                || Math.abs(TurretAim.shortestAngleDelta(turret.facingDeg, bearing)) > TurretAim.FIRE_ARC_DEG) {
            return true;
        }
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(mountX, mountY, turret.facingDeg, mount, 0);
        airFire.fire(id, side, structure, craft, muzzle.x(), muzzle.y());
        turret.ammo--;
        addFlux(turret, structure.mount);
        turret.cooldownTimer = weapon.cooldown;
        if (turret.ammo > 0 && weapon.burstCount > 1) {
            turret.burstRemaining = weapon.burstCount - 1;
            turret.burstTimer = weapon.burstSpacing;
            turret.burstAir = true;
        }
        return true;
    }

    /** The rest of a burst committed into the air: it follows its craft, and ends when the craft leaves the gun. */
    private void tickAirBurst(long id, GroundTurret turret, StructureDef structure, float mountX, float mountY,
                              float dt) {
        TurretMountDef mount = structure.mount;
        Faction side = convoy.faction(id);
        long craft = turret.airTargetId;
        if (!airFire.valid(side, mountX, mountY, mount.weapon.range, craft)) {
            turret.clearQueuedFire();
            return;
        }
        turret.facingDeg = TurretAim.slewToward(turret.facingDeg,
                TurretAim.bearingTo(mountX, mountY, world.x(craft), world.y(craft)), mount.turnRateDegPerSec * dt);
        turret.burstTimer -= dt;
        if (turret.burstTimer > 0f) return;
        int release = TurretMountGeometry.releaseIndex(mount.weapon.burstCount, turret.burstRemaining);
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(mountX, mountY, turret.facingDeg, mount, release);
        airFire.fire(id, side, structure, craft, muzzle.x(), muzzle.y());
        turret.ammo--;
        addFlux(turret, structure.mount);
        finishBurstRound(turret, mount.weapon);
        if (turret.burstRemaining == 0) turret.burstAir = false;
    }

    /**
     * Where an idle gun rests: down the threat axis of a guard the hull stands ({@code MachineGuards}), so the
     * heavy support beside a garrison watches the way the attack is believed to come; otherwise over the hull's nose.
     */
    private float idleHeading(long id, GroundBody body) {
        MachineGuard guard = assaults != null ? assaults.guard(id) : null;
        if (guard == null) return body.facingDegrees;
        return TurretAim.bearingTo(0f, 0f, guard.axisX(), guard.axisY());
    }

    private boolean closeThreat(long target, float mountX, float mountY) {
        return target != 0L && Math.hypot(world.x(target) - mountX, world.y(target) - mountY)
                <= MechTargeting.CLOSE_THREAT_AGGRO_RANGE;
    }

    /**
     * One autonomous round at the wall an assault names: the gun slews onto the cell and fires a single round
     * through the point-fire path a controlled turret uses, on the weapon's own cadence and magazine. No burst
     * is queued; the next round is the next directive's.
     */
    private void tickAssaultWall(long id, GroundTurret turret, GroundBody body, StructureDef structure,
                                 float mountX, float mountY, float dt, MachineAssault assault) {
        WeaponDef weapon = structure.mount.weapon;
        turret.targetId = 0L;
        PointFireAim aim = new PointFireAim(assault.wallX() + .5f, assault.wallY() + .5f);
        if (aim.validFrom(mountX, mountY)) {
            turret.facingDeg = TurretAim.slewToward(turret.facingDeg,
                    TurretAim.bearingTo(mountX, mountY, aim.x(), aim.y()),
                    structure.mount.turnRateDegPerSec * dt);
        }
        if (turret.cooldownTimer > 0f) turret.cooldownTimer -= dt;
        if (turret.cooldownTimer > 0f || !ready(turret, structure.mount)
                || !firePoint(id, turret, body, structure, mountX, mountY, aim, 0)) return;
        turret.ammo--;
        addFlux(turret, structure.mount);
        turret.cooldownTimer = weapon.cooldown;
    }

    /**
     * A missile launcher's autonomous tick (the missile carriers, {@code convoy-nouns.md}). A launcher whose mount is
     * {@link TurretMountDef#hardenedTargetsOnly} spends its contact warheads only on turrets, hubs, vehicles and heavy
     * mechs, as a mech's anti-armor rack does, and holds fire at everything else. An indirect-fire launcher also takes
     * a target it cannot see when somebody else on its side is looking at it ({@link SideSpotting}): the round arcs
     * over whatever is between, at the weapon's observed accuracy. A target nobody is looking at is not fired on.
     * Its own sight is preferred, then the nearest.
     */
    private void tickLauncher(long id, GroundTurret turret, GroundBody body, StructureDef structure,
                              float mountX, float mountY, float dt) {
        TurretMountDef mount = structure.mount;
        WeaponDef weapon = mount.weapon;
        Faction side = convoy.faction(id);
        if (turret.cooldownTimer > 0f) turret.cooldownTimer -= dt;
        turret.relookTimer -= dt;
        long target = turret.targetId;
        if (target != 0L && launcherSight(side, target, mountX, mountY, mount) == NO_SIGHT) target = 0L;
        if (target == 0L && turret.relookTimer <= 0f) {
            turret.relookTimer = LAUNCHER_RELOOK_SECONDS;
            target = pickLauncherTarget(id, side, mountX, mountY, mount);
        }
        turret.targetId = target;
        if (target == 0L) {
            turret.facingDeg = idleFacing(turret.facingDeg, idleHeading(id, body), mount.turnRateDegPerSec, dt);
            return;
        }
        float bearing = TurretAim.bearingTo(mountX, mountY, world.x(target), world.y(target));
        turret.facingDeg = TurretAim.slewToward(turret.facingDeg, bearing, mount.turnRateDegPerSec * dt);
        // A chained salvo goes to the body its chain was committed against, and to no other.
        if (turret.chainedTrigger() && target != turret.chainTargetId) turret.chainSalvos = 0;
        if (!turret.clockReady() || !ready(turret, mount)
                || Math.abs(TurretAim.shortestAngleDelta(turret.facingDeg, bearing)) > TurretAim.FIRE_ARC_DEG
                || !barrelClear(body, TurretMountGeometry.muzzle(mountX, mountY, turret.facingDeg, mount, 0))) {
            return;
        }
        // A spotted round is an observed round: the spotter's eyes stand in for the hull's, as a mortar's do.
        if (!fireSink.fire(id, mountX, mountY, side, structure, target, false, true, turret.facingDeg, 0)) return;
        boolean chained = turret.chainedTrigger();
        int loadedSalvos = turret.ammo / Math.max(1, weapon.burstCount);
        turret.commitChained();
        turret.ammo--;
        addFlux(turret, structure.mount);
        turret.cooldownTimer = weapon.cooldown;
        if (!chained && SalvoChain.chains(weapon)) {
            // A fresh trigger chooses how many salvos it chains: more at a mech or behind point defence.
            turret.chainSalvos = SalvoChain.autonomousSalvos(loadedSalvos, SalvoChain.VEHICLE_AUTONOMOUS_SALVOS,
                    hardened(target), world.hasMechLoadout(target), screened.test(target)) - 1;
            turret.chainTargetId = target;
        }
        if (turret.ammo > 0 && weapon.burstCount > 1) {
            turret.burstRemaining = weapon.burstCount - 1;
            turret.burstTimer = weapon.burstSpacing;
            turret.burstTargetId = target;
        }
    }

    static final int NO_SIGHT = 0;
    static final int SPOTTED = 1;
    static final int OWN_SIGHT = 2;

    /** How launcher {@code mount} at the point may engage {@code target}: not at all, on a spotter's call, or itself. */
    private int launcherSight(Faction side, long target, float mountX, float mountY, TurretMountDef mount) {
        if (!world.isAlive(target)) return NO_SIGHT;
        boolean vehicle = convoy.isVehicle(target);
        if (vehicle && (!convoy.isTargetable(target) || convoy.structure(target) <= 0f)) return NO_SIGHT;
        Faction other = vehicle ? convoy.faction(target) : roster.identity().faction(target);
        if (other == null || !side.hostileTo(other)) return NO_SIGHT;
        if (mount.hardenedTargetsOnly && !hardened(target)) return NO_SIGHT;
        WeaponDef weapon = mount.weapon;
        float tx = world.x(target);
        float ty = world.y(target);
        float distance = (float) Math.hypot(tx - mountX, ty - mountY);
        if (distance > weapon.range || distance < weapon.minRange) return NO_SIGHT;
        if (grid.hasLineOfSight((int) Math.floor(mountX), (int) Math.floor(mountY),
                (int) Math.floor(tx), (int) Math.floor(ty))) return OWN_SIGHT;
        return weapon.indirectFire && spotting.spotted(side, target) ? SPOTTED : NO_SIGHT;
    }

    private long pickLauncherTarget(long self, Faction side, float mountX, float mountY, TurretMountDef mount) {
        long best = 0L;
        int bestSight = NO_SIGHT;
        float bestDistance = Float.MAX_VALUE;
        for (long candidate : convoy.entityIds()) {
            if (candidate == self) continue;
            int sight = launcherSight(side, candidate, mountX, mountY, mount);
            if (sight == NO_SIGHT) continue;
            float d = (float) Math.hypot(world.x(candidate) - mountX, world.y(candidate) - mountY);
            if (better(sight, d, candidate, bestSight, bestDistance, best)) {
                best = candidate;
                bestSight = sight;
                bestDistance = d;
            }
        }
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long candidate = roster.get(i);
            if (roster.isRiding(candidate)) continue;
            if (mount.hardenedTargetsOnly && !hardened(candidate)) continue;
            int sight = launcherSight(side, candidate, mountX, mountY, mount);
            if (sight == NO_SIGHT) continue;
            float d = (float) Math.hypot(world.x(candidate) - mountX, world.y(candidate) - mountY);
            if (better(sight, d, candidate, bestSight, bestDistance, best)) {
                best = candidate;
                bestSight = sight;
                bestDistance = d;
            }
        }
        return best;
    }

    /** A turret, hub, vehicle or heavy mech: what an anti-armor warhead is spent on. */
    private boolean hardened(long target) {
        return convoy.isVehicle(target) || TacticalScoring.isHardened(roster.identity().type(target));
    }

    /** Own sight before a spotter's call, then the nearer, then the lower id so the choice repeats. */
    static boolean better(int sight, float distance, long id, int bestSight, float bestDistance, long best) {
        if (best == 0L) return true;
        if (sight != bestSight) return sight > bestSight;
        if (distance != bestDistance) return distance < bestDistance;
        return id < best;
    }

    /** One idle tick: the turret slews back toward the hull's heading at {@link #IDLE_RETURN_RATE_SHARE} of its rate. */
    static float idleFacing(float turretFacing, float hullFacing, float turnRateDegPerSec, float dt) {
        return TurretAim.slewToward(turretFacing, hullFacing, turnRateDegPerSec * IDLE_RETURN_RATE_SHARE * dt);
    }

    /**
     * Dissipates the gun's flux ({@link TurretMountDef#flux}), at the vent rate while it is overloaded; an overloaded
     * gun is back in action once it is empty. Nothing for a mount without flux.
     */
    static void dissipate(GroundTurret turret, TurretMountDef mount, float dt) {
        TurretMountDef.Flux flux = mount.flux;
        if (flux == null) return;
        float rate = turret.overloaded ? flux.ventPerSec() : flux.dissipationPerSec();
        turret.flux = Math.max(0f, turret.flux - rate * dt);
        if (turret.overloaded && turret.flux <= 0f) turret.overloaded = false;
    }

    /** Whether the gun may pull its trigger: always, unless it overloaded and is venting. */
    static boolean ready(GroundTurret turret, TurretMountDef mount) {
        return mount.flux == null || !turret.overloaded;
    }

    /** Seconds until a venting gun is empty and ready; zero when it is ready. */
    static float fluxWait(GroundTurret turret, TurretMountDef mount) {
        return ready(turret, mount) ? 0f : turret.flux / mount.flux.ventPerSec();
    }

    /**
     * One round's flux. A round that fills the gun overloads it: its trigger is held until it has vented, and the
     * rest of its burst is broken off on the next tick, before another round.
     */
    static void addFlux(GroundTurret turret, TurretMountDef mount) {
        TurretMountDef.Flux flux = mount.flux;
        if (flux == null) return;
        turret.flux = Math.min(flux.capacity(), turret.flux + flux.perRound());
        if (turret.flux >= flux.capacity()) turret.overloaded = true;
    }

    private static void finishBurstRound(GroundTurret turret, WeaponDef weapon) {
        if (turret.ammo <= 0) {
            turret.clearQueuedFire();
            return;
        }
        turret.burstRemaining--;
        turret.burstTimer = weapon.burstSpacing;
        if (turret.burstRemaining == 0) {
            turret.burstTargetId = 0L;
            turret.burstPointAim = null;
            turret.chainGap = weapon.burstSpacing;
        }
    }
}
