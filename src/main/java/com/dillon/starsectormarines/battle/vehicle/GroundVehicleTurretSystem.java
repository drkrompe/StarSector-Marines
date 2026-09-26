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
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/** One aim, magazine, and cadence authority for autonomous and controlled vehicle turrets. */
public final class GroundVehicleTurretSystem {
    private final ConvoyService convoy;
    private final UnitRosterService roster;
    private final TacticalScoring scoring;
    private final NavigationGrid grid;
    private final World world;
    private final TurretFireSink fireSink;

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
        if (turret.ammo <= 0) {
            turret.clearQueuedFire();
            return;
        }
        TurretMountDef mount = structure.mount;
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
