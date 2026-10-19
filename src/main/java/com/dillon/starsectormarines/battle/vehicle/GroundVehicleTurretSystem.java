package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
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

    /** Who calls targets for an indirect-fire launcher that cannot see them itself. */
    public void setSpotting(SideSpotting spotting) {
        this.spotting = spotting != null ? spotting : SideSpotting.NONE;
    }

    public void cancelQueuedFire(long id) {
        if (!convoy.isVehicle(id)) return;
        GroundTurret turret = convoy.turret(id);
        if (turret != null) turret.clearQueuedFire();
    }

    public void tick(float dt, long controlledId, PointFireAim point, boolean trigger) {
        if (!Float.isFinite(dt) || dt <= 0f) return;
        for (long id : convoy.entityIds()) {
            if (!convoy.isTargetable(id)) continue;
            VehicleType type = convoy.vehicleType(id);
            if (!type.hasTurretWeapon()) continue;
            GroundTurret turret = convoy.turret(id);
            if (turret.destroyed()) {
                // A shot-away gun neither fires nor traverses; it stays where it last pointed.
                turret.clearQueuedFire();
                continue;
            }
            GroundBody body = convoy.body(id);
            StructureDef structure = type.turretStructure();
            float radians = (float) Math.toRadians(body.facingDegrees);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            float mountX = body.x + type.turretMountX * cos - type.turretMountY * sin;
            float mountY = body.y + type.turretMountX * sin + type.turretMountY * cos;
            if (id == controlledId) {
                tickManual(id, turret, body, structure, mountX, mountY, dt, point, trigger);
            } else {
                if (turret.burstPointAim != null) turret.clearQueuedFire();
                tickAutonomous(id, turret, body, structure, mountX, mountY, dt);
            }
        }
    }

    private void tickManual(long id, GroundTurret turret, GroundBody body, StructureDef structure,
                            float mountX, float mountY, float dt, PointFireAim cursor, boolean trigger) {
        if (turret.burstRemaining > 0 && turret.burstPointAim == null) turret.clearQueuedFire();
        if (turret.ammo <= 0) turret.clearQueuedFire();
        turret.targetId = 0L;
        TurretMountDef mount = structure.mount;
        WeaponDef weapon = mount.weapon;
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
                finishBurstRound(turret, weapon);
            }
            return;
        }
        if (turret.cooldownTimer > 0f) turret.cooldownTimer -= dt;
        if (!trigger || turret.cooldownTimer > 0f
                || !firePoint(id, turret, body, structure, mountX, mountY, aim, 0)) return;
        turret.ammo--;
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
            turret.facingDeg = idleFacing(turret.facingDeg, body.facingDegrees, mount.turnRateDegPerSec, dt);
            return;
        }
        WeaponDef weapon = mount.weapon;
        long burstTarget = roster.isAliveById(turret.burstTargetId) ? turret.burstTargetId : 0L;
        // Keep autonomous APC cadence: no cooldown or traverse tick during a committed burst.
        if (turret.burstRemaining > 0) {
            turret.burstTimer -= dt;
            int release = TurretMountGeometry.releaseIndex(weapon.burstCount, turret.burstRemaining);
            if (turret.burstTimer <= 0f && burstTarget != 0L && world.isAlive(burstTarget)
                    && barrelClear(body, TurretMountGeometry.muzzle(
                    mountX, mountY, turret.facingDeg, mount, release))) {
                fireSink.fire(id, mountX, mountY, convoy.faction(id), structure,
                        burstTarget, false, true, turret.facingDeg, release);
                turret.ammo--;
                finishBurstRound(turret, weapon);
            }
            if (burstTarget == 0L || !world.isAlive(burstTarget)) turret.clearQueuedFire();
            return;
        }

        if (mount.hardenedTargetsOnly || weapon.indirectFire) {
            tickLauncher(id, turret, body, structure, mountX, mountY, dt);
            return;
        }

        TurretAim.State aim = new TurretAim.State();
        aim.originCellX = (int) Math.floor(mountX);
        aim.originCellY = (int) Math.floor(mountY);
        aim.originX = mountX;
        aim.originY = mountY;
        aim.faction = convoy.faction(id);
        aim.facingDegrees = turret.facingDeg;
        aim.turnRateDegPerSec = mount.turnRateDegPerSec;
        aim.attackRange = weapon.range;
        aim.minRange = weapon.minRange;
        aim.cooldownTimer = turret.cooldownTimer;
        aim.attackCooldown = weapon.cooldown;
        aim.target = roster.isAliveById(turret.targetId) ? turret.targetId : 0L;
        float agedCooldown = turret.cooldownTimer > 0f ? turret.cooldownTimer - dt : turret.cooldownTimer;
        TurretAim.tick(aim, scoring, grid, world, roster.vision(), dt);
        turret.facingDeg = aim.facingDegrees;
        turret.cooldownTimer = aim.cooldownTimer;
        turret.targetId = aim.target;
        if (aim.target == 0L) {
            turret.facingDeg = idleFacing(turret.facingDeg, body.facingDegrees, mount.turnRateDegPerSec, dt);
            return;
        }
        if (aim.fireThisTick && aim.target != 0L) {
            if (!barrelClear(body, TurretMountGeometry.muzzle(mountX, mountY, turret.facingDeg, mount, 0))) {
                turret.cooldownTimer = agedCooldown;
                return;
            }
            fireSink.fire(id, mountX, mountY, convoy.faction(id), structure, aim.target,
                    false, aim.lastFireHadLos, turret.facingDeg, 0);
            turret.ammo--;
            if (turret.ammo > 0 && weapon.burstCount > 1 && world.isAlive(aim.target)) {
                turret.burstRemaining = weapon.burstCount - 1;
                turret.burstTimer = weapon.burstSpacing;
                turret.burstTargetId = aim.target;
            }
        }
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
            turret.facingDeg = idleFacing(turret.facingDeg, body.facingDegrees, mount.turnRateDegPerSec, dt);
            return;
        }
        float bearing = TurretAim.bearingTo(mountX, mountY, world.x(target), world.y(target));
        turret.facingDeg = TurretAim.slewToward(turret.facingDeg, bearing, mount.turnRateDegPerSec * dt);
        if (turret.cooldownTimer > 0f
                || Math.abs(TurretAim.shortestAngleDelta(turret.facingDeg, bearing)) > TurretAim.FIRE_ARC_DEG
                || !barrelClear(body, TurretMountGeometry.muzzle(mountX, mountY, turret.facingDeg, mount, 0))) {
            return;
        }
        // A spotted round is an observed round: the spotter's eyes stand in for the hull's, as a mortar's do.
        fireSink.fire(id, mountX, mountY, side, structure, target, false, true, turret.facingDeg, 0);
        turret.ammo--;
        turret.cooldownTimer = weapon.cooldown;
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
        }
    }
}
