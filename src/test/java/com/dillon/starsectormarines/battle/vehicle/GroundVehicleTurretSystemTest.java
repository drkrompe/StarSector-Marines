package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretFireSystem;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Bare vehicle and ballistic fixtures; no battle host or route planner is needed to ask about a gun. */
class GroundVehicleTurretSystemTest {
    private static final float DT = BattleSimulation.TICK_DT;

    @Test
    void manualBurstFreezesAimAlternatesBarrelsAndOwnsOneClockAndMagazine() {
        Fixture f = new Fixture();
        PointFireAim north = f.north();
        long enemy = f.enemy(40, 20);
        f.turret.targetId = enemy;
        f.tick(north, true);
        assertEquals(1, f.shots.getActiveShots().size(), "manual trigger must not also trigger AI");
        assertEquals(f.mount.mount.ammoCapacity - 1, f.turret.ammo);
        assertEquals(0L, f.turret.targetId);
        assertEquals(0L, f.turret.burstTargetId);
        assertEquals(north, f.turret.burstPointAim);
        assertEquals(f.weapon.cooldown, f.turret.cooldownTimer);

        int remainingTicks = 120;
        PointFireAim east = new PointFireAim(60f, 20.5f);
        while (f.turret.burstRemaining > 0 && remainingTicks-- > 0) f.tick(east, true);
        assertTrue(remainingTicks > 0);
        assertEquals(f.weapon.burstCount, f.shots.getActiveShots().size());
        assertEquals(f.mount.mount.ammoCapacity - f.weapon.burstCount, f.turret.ammo);
        assertEquals(f.weapon.cooldown, f.turret.cooldownTimer, 0.00001f,
                "APC cooldown recovery starts after the burst, not during it");
        assertNull(f.turret.burstPointAim);
        List<ShotEvent> shots = f.shots.getActiveShots();
        assertTrue(shots.get(0).fromX < shots.get(1).fromX);
        for (ShotEvent shot : shots) assertTrue(shot.toY > shot.fromY + 20f);
        f.tick(north, false);
        assertEquals(f.weapon.cooldown - DT, f.turret.cooldownTimer, 0.00001f);
    }

    @Test
    void traverseAndMinimumRangeGateBeforeSpendingResources() {
        Fixture f = new Fixture();
        f.tick(new PointFireAim(Float.NaN, 50f), true);
        f.tick(new PointFireAim(f.mountX(), f.mountY()), true);
        f.tick(new PointFireAim(f.mountX(), f.mountY() + 2.99f), true);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(f.mount.mount.ammoCapacity, f.turret.ammo);
        assertEquals(0f, f.turret.cooldownTimer);

        PointFireAim east = new PointFireAim(60f, f.mountY());
        f.tick(east, true);
        assertEquals(-f.mount.mount.turnRateDegPerSec * DT, f.turret.facingDeg, 0.00001f);
        assertTrue(f.shots.getActiveShots().isEmpty());
        int ticks = 60;
        while (f.shots.getActiveShots().isEmpty() && ticks-- > 0) f.tick(east, true);
        assertTrue(ticks > 0);
        assertEquals(1, f.shots.getActiveShots().size());
        assertTrue(Math.abs(TurretAim.shortestAngleDelta(f.turret.facingDeg, -90f)) <= TurretAim.FIRE_ARC_DEG);
    }

    @Test
    void suspensionCancelsQueuedRoundsWithoutRefundOrResetAndAmmunitionIsFinite() {
        Fixture f = new Fixture();
        f.tick(f.north(), true);
        int ammo = f.turret.ammo;
        float cooldown = f.turret.cooldownTimer;
        float facing = f.turret.facingDeg;
        f.system.cancelQueuedFire(f.id);
        assertEquals(ammo, f.turret.ammo);
        assertEquals(cooldown, f.turret.cooldownTimer);
        assertEquals(facing, f.turret.facingDeg);
        assertEquals(0, f.turret.burstRemaining);
        assertNull(f.turret.burstPointAim);
        for (int i = 0; i < 20; i++) f.tick(f.north(), false);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(cooldown - 20f * DT, f.turret.cooldownTimer, 0.00001f);

        Fixture lastRound = new Fixture();
        lastRound.turret.ammo = 1;
        lastRound.tick(lastRound.north(), true);
        for (int i = 0; i < 100; i++) lastRound.tick(lastRound.north(), true);
        assertEquals(1, lastRound.shots.getActiveShots().size());
        assertEquals(0, lastRound.turret.ammo);
        assertEquals(0, lastRound.turret.burstRemaining);
        assertNull(lastRound.turret.burstPointAim);
    }

    @Test
    void pausedOrInvalidTickDoesNothingAndIndirectMountCannotPointFire() {
        Fixture f = new Fixture();
        f.turret.cooldownTimer = 0.5f;
        for (float dt : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            f.system.tick(dt, f.id, new PointFireAim(60f, f.mountY()), true);
        }
        assertEquals(0.5f, f.turret.cooldownTimer);
        assertEquals(0f, f.turret.facingDeg);
        assertEquals(f.mount.mount.ammoCapacity, f.turret.ammo);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertFalse(f.fire.firePoint(f.id, f.mountX(), f.mountY(), Faction.MARINE,
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.LOCUST_STRUCTURE_ID),
                f.north(), 0f, 0));
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertTrue(f.detonations.isEmpty());
    }

    @Test
    void pointShotUsesPosedMuzzleAndPhysicalWallArrivalButEmptyGroundMakesNoBlast() {
        Fixture f = new Fixture();
        for (int x = 0; x < 96; x++) f.grid.setWalkable(x, 35, false);
        f.tick(f.north(), true);
        ShotEvent shot = f.shots.getActiveShots().get(0);
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                f.mountX(), f.mountY(), f.turret.facingDeg, f.mount.mount, 0);
        assertEquals(muzzle.x(), shot.fromX);
        assertEquals(muzzle.y(), shot.fromY);
        assertEquals(BallisticResolver.StopKind.WALL, shot.stopKind);
        assertEquals(35f, shot.toY, 0.0001f);
        assertEquals(1, f.detonations.size());
        PendingDetonation blast = f.detonations.get(0);
        assertEquals(shot.toX, blast.endpointX);
        assertEquals(shot.toY, blast.endpointY);
        assertEquals(shot.lifetime, blast.remainingTime, 0.00001f);
        assertEquals((float) Math.hypot(shot.toX - shot.fromX, shot.toY - shot.fromY)
                / f.weapon.directRoundVelocity(), shot.lifetime, 0.00001f);

        Fixture open = new Fixture();
        open.tick(new PointFireAim(open.mountX(), open.mountY() + 5f), true);
        ShotEvent miss = open.shots.getActiveShots().get(0);
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, miss.stopKind);
        assertTrue(miss.toY > open.mountY() + open.weapon.range);
        assertTrue(open.detonations.isEmpty(), "cursor ground is not an explosion request");
    }

    @Test
    void burstReslewsFromMovedChassisAndDoesNotEmitWhileOutsideLiveAlignment() {
        Fixture f = new Fixture();
        PointFireAim aim = f.north();
        f.tick(aim, true);
        f.body.x += 30f;
        f.tick(new PointFireAim(60f, 20f), false);
        f.tick(new PointFireAim(60f, 20f), false);
        f.tick(new PointFireAim(60f, 20f), false);
        assertEquals(1, f.shots.getActiveShots().size(), "ready burst round waits for the actual pose");
        assertSame(aim, f.turret.burstPointAim);
        int ticks = 60;
        while (f.shots.getActiveShots().size() == 1 && ticks-- > 0) f.tick(null, false);
        assertTrue(ticks > 0);
        assertEquals(2, f.shots.getActiveShots().size());
        assertEquals(f.weapon.cooldown, f.turret.cooldownTimer);
    }

    @Test
    void structuralBarrelGateProtectsManualAndAiContinuationButWalkableCoverDoesNot() {
        Fixture f = new Fixture();
        f.body.x = 20.71f;
        f.turret.facingDeg = 90f;
        for (int y = 0; y < 96; y++) f.grid.setWalkable(19, y, false);
        assertTrue(VehicleFootprint.isPoseFeasible(f.body.x, f.body.y, 0f,
                VehicleType.HEAVY_APC.visualLengthCells, VehicleType.HEAVY_APC.visualWidthCells, f.grid));
        PointFireAim west = new PointFireAim(5f, f.mountY());
        f.tick(west, true);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(f.mount.mount.ammoCapacity, f.turret.ammo);
        assertEquals(0f, f.turret.cooldownTimer);

        f.turret.burstTargetId = f.enemy(5, 20);
        f.turret.burstRemaining = 1;
        f.system.tick(DT, 0L, null, false);
        assertEquals(1, f.turret.burstRemaining);
        assertTrue(f.shots.getActiveShots().isEmpty());
        for (int y = 0; y < 96; y++) f.grid.setWalkableFloor(19, y);
        f.grid.setCoverAtFacing(19, 20, NavigationGrid.FACING_E, 3);
        f.system.tick(DT, 0L, null, false);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(f.mount.mount.ammoCapacity - 1, f.turret.ammo);
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(96, 96);
        final UnitSpatialIndex index = new UnitSpatialIndex(96, 96);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final ShotService shots = new ShotService();
        final List<PendingDetonation> detonations = new ArrayList<>();
        final StructureDef mount = VehicleType.HEAVY_APC.turretStructure();
        final WeaponDef weapon = mount.mount.weapon;
        final long id;
        final GroundTurret turret;
        final GroundBody body;
        final GroundVehicleTurretSystem system;
        final TurretFireSystem fire;

        Fixture() {
            for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) grid.setWalkableFloor(x, y);
            id = roster.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE,
                    VehicleMission.deployed(20.5f, 20.5f));
            body = roster.convoy().body(id);
            body.facingDegrees = 0f;
            turret = roster.convoy().turret(id);
            turret.facingDeg = 0f;
            fire = new TurretFireSystem(new Random() {
                @Override public float nextFloat() { return 0f; }
            }, new CellTopology(96, 96), shots, null, detonations::add, null, roster.world(),
                    new BallisticResolver(grid, new DoodadService(grid), index, roster), roster.telemetry());
            system = new GroundVehicleTurretSystem(roster.convoy(), roster, null, grid, roster.world(), fire);
        }

        float mountX() { return body.x + VehicleType.HEAVY_APC.turretMountX; }
        float mountY() { return body.y + VehicleType.HEAVY_APC.turretMountY; }
        PointFireAim north() { return new PointFireAim(mountX(), 60f); }
        void tick(PointFireAim aim, boolean trigger) { system.tick(DT, id, aim, trigger); }
        long enemy(int x, int y) {
            return roster.spawn(new EntitySpec("turret target", Faction.DEFENDER, UnitType.MARINE, x, y));
        }
    }
}
