package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
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
    void fluxOverloadsAtCapacityHoldsTheTriggerWhileVentingAndLeavesTheMagazine() {
        TurretMountDef laser = VehicleType.LASER_TANK.turretStructure().mount;
        TurretMountDef.Flux flux = laser.flux;
        GroundTurret turret = new GroundTurret(laser.ammoCapacity);
        int rounds = 0;
        while (!turret.overloaded) {
            assertTrue(GroundVehicleTurretSystem.ready(turret, laser));
            GroundVehicleTurretSystem.addFlux(turret, laser);
            rounds++;
        }
        assertEquals((int) Math.ceil(flux.capacity() / flux.perRound()), rounds);
        assertFalse(GroundVehicleTurretSystem.ready(turret, laser));
        assertEquals(flux.capacity() / flux.ventPerSec(), GroundVehicleTurretSystem.fluxWait(turret, laser), 1e-4f);
        assertEquals(laser.ammoCapacity, turret.ammo, "an overloaded gun leaves the hull armed");
        GroundVehicleTurretSystem.dissipate(turret, laser, flux.capacity() / flux.ventPerSec() * 0.5f);
        assertFalse(GroundVehicleTurretSystem.ready(turret, laser), "a venting gun holds until it is empty");
        GroundVehicleTurretSystem.dissipate(turret, laser, flux.capacity() / flux.ventPerSec());
        assertTrue(GroundVehicleTurretSystem.ready(turret, laser));
        assertEquals(0f, turret.flux);

        GroundVehicleTurretSystem.addFlux(turret, laser);
        GroundVehicleTurretSystem.dissipate(turret, laser, 1f);
        assertEquals(Math.max(0f, flux.perRound() - flux.dissipationPerSec()), turret.flux, 1e-5f,
                "below capacity flux bleeds at the dissipation rate");

        // An emplacement's mount: hull guns all build flux, so only a mount no hull carries is without it.
        TurretMountDef magazine = TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.VULCAN_STRUCTURE_ID).mount;
        GroundTurret gun = new GroundTurret(magazine.ammoCapacity);
        GroundVehicleTurretSystem.addFlux(gun, magazine);
        assertTrue(GroundVehicleTurretSystem.ready(gun, magazine), "a mount without flux is never held");
        assertEquals(0f, GroundVehicleTurretSystem.fluxWait(gun, magazine));
    }

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
    void anIdleTurretSettlesBackOntoTheHullHeadingAtHalfItsTraverse() {
        float rate = 60f;
        // Half rate: 30 degrees a second, along the short way round, and it stops on the heading.
        assertEquals(87f, GroundVehicleTurretSystem.idleFacing(90f, 0f, rate, 0.1f), 0.0001f);
        assertEquals(-177.5f, GroundVehicleTurretSystem.idleFacing(-170f, 170f, rate, 0.25f), 0.0001f);
        assertEquals(0f, GroundVehicleTurretSystem.idleFacing(1f, 0f, rate, 1f), 0.0001f);

        // An autonomous turret with nothing left to fire takes the same path back toward the hull.
        Fixture f = new Fixture();
        f.turret.ammo = 0;
        f.turret.facingDeg = 90f;
        f.body.facingDegrees = 0f;
        f.system.tick(DT, 0L, null, false);
        assertEquals(90f - f.mount.mount.turnRateDegPerSec * GroundVehicleTurretSystem.IDLE_RETURN_RATE_SHARE * DT,
                f.turret.facingDeg, 0.0001f);
    }

    @Test
    void pausedOrInvalidTickDoesNothing() {
        Fixture f = new Fixture();
        f.turret.cooldownTimer = 0.5f;
        for (float dt : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            f.system.tick(dt, f.id, new PointFireAim(60f, f.mountY()), true);
        }
        assertEquals(0.5f, f.turret.cooldownTimer);
        assertEquals(0f, f.turret.facingDeg);
        assertEquals(f.mount.mount.ammoCapacity, f.turret.ammo);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertTrue(f.detonations.isEmpty());
    }

    @Test
    void anArcingLauncherDropsItsRoundOnTheCursorOverAWallAndRefusesInsideItsMinimum() {
        Fixture f = new Fixture(VehicleType.LRM_CARRIER);
        StructureDef launcher = f.mount;
        float tooNear = ManualPointFire.minRangeCells(f.weapon) - 0.5f;
        assertFalse(f.fire.firePoint(f.id, f.mountX(), f.mountY(), Faction.MARINE, launcher,
                new PointFireAim(f.mountX(), f.mountY() + tooNear), 0f, 0));
        assertTrue(f.shots.getActiveProjectiles().isEmpty());

        for (int x = 10; x < 32; x++) f.grid.setWalkable(x, 30, false);
        PointFireAim cursor = new PointFireAim(f.mountX(), f.mountY() + 20f);
        assertTrue(f.fire.firePoint(f.id, f.mountX(), f.mountY(), Faction.MARINE, launcher, cursor, 0f, 0));
        Projectile round = f.shots.getActiveProjectiles().get(0);
        // The fixture's draws are all zero, so the scatter disk collapses onto the thrown point.
        assertEquals(cursor.x(), round.toX, 1e-4f);
        assertEquals(cursor.y(), round.toY, 1e-4f, "the wall at y=30 does not stop an arcing round");
        assertEquals(f.weapon.arcHeight, round.arcHeight);
        assertEquals(cursor.x(), round.onArrival.endpointX, 1e-4f);
        assertEquals(cursor.y(), round.onArrival.endpointY, 1e-4f);
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
        // A long barrel can cross a wall while its chassis stays clear. The
        // centered APC MG now remains inside the hull's collision footprint.
        Fixture f = new Fixture(VehicleType.BREAKTHROUGH_TANK);
        f.body.x = 20f + f.type.visualWidthCells * .5f + .01f;
        f.turret.facingDeg = 90f;
        for (int y = 0; y < 96; y++) f.grid.setWalkable(19, y, false);
        assertTrue(VehicleFootprint.isPoseFeasible(f.body.x, f.body.y, 0f,
                f.type.visualLengthCells, f.type.visualWidthCells, f.grid));
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

    @Test
    void tankBurstUsesSingleCenteredMuzzleAndStopsWhenItsFiniteMagazineIsEmpty() {
        Fixture f = new Fixture(VehicleType.BREAKTHROUGH_TANK);
        f.grid.setWalkable(20, 45, false);
        f.turret.ammo = 2;
        PointFireAim aim = f.north();
        for (int i = 0; i < 240; i++) f.tick(aim, true);
        assertEquals(2, f.shots.getActiveShots().size());
        assertEquals(0, f.turret.ammo);
        assertEquals(0, f.turret.burstRemaining);
        assertEquals(2, f.detonations.size(), "each shell has one shared blast, not a cosmetic duplicate");
        var first = f.shots.getActiveShots().get(0);
        var second = f.shots.getActiveShots().get(1);
        assertEquals(f.mountX(), first.fromX, .001f);
        assertEquals(first.fromX, second.fromX, .001f);
        assertEquals(first.fromY, second.fromY, .001f);
        assertEquals(f.mountY() + f.mount.mount.muzzleOffsetCells, first.fromY, .001f);
        assertTrue(f.weapon.contactDamage > f.weapon.damage);
        assertTrue(f.weapon.contactPenetration > f.weapon.penetration);
    }

    @Test
    void anAntiArmorLauncherHoldsFireAtMenAndFiresItsShapedChargeIntoArmor() {
        Fixture f = new Fixture(VehicleType.SRM_TECHNICAL);
        f.turret.facingDeg = 0f;
        f.enemy(20, 30);
        for (int i = 0; i < 120; i++) f.auto();
        assertTrue(f.shots.getActiveShots().isEmpty(), "a rifleman is not worth a contact warhead");
        assertEquals(f.mount.mount.ammoCapacity, f.turret.ammo);

        long tank = f.hostileHull(VehicleType.BREAKTHROUGH_TANK, 20.5f, 32.5f);
        for (int i = 0; i < 120 && f.shots.getActiveShots().isEmpty(); i++) f.auto();
        assertEquals(tank, f.turret.targetId);
        assertFalse(f.shots.getActiveShots().isEmpty());
        Projectile rocket = f.shots.getActiveProjectiles().get(0);
        assertEquals(tank, rocket.onArrival.directTargetId, "the contact warhead rides the rocket onto the hull");
        assertEquals(f.weapon.contactDamage, rocket.onArrival.directDamage, 0.001f);
    }

    @Test
    void anIndirectLauncherFiresOverAWallOnlyWhileSomebodyElseIsLooking() {
        Fixture f = new Fixture(VehicleType.LRM_CARRIER);
        for (int x = 10; x < 32; x++) f.grid.setWalkable(x, 30, false);
        long tank = f.hostileHull(VehicleType.BREAKTHROUGH_TANK, 20.5f, 45.5f);
        assertFalse(f.grid.hasLineOfSight(20, 20, 20, 45), "the wall hides the tank from the carrier");
        for (int i = 0; i < 120; i++) f.auto();
        assertTrue(f.shots.getActiveShots().isEmpty(), "nobody is looking, so the launcher holds");

        boolean[] looking = {true};
        f.system.setSpotting((side, target) -> looking[0] && side == Faction.MARINE && target == tank);
        for (int i = 0; i < 120 && f.shots.getActiveShots().isEmpty(); i++) f.auto();
        assertEquals(1, f.shots.getActiveShots().size());
        assertTrue(f.weapon.indirectFire && f.weapon.arcHeight > 0f, "the round arcs over the wall");
        while (f.turret.burstRemaining > 0) f.auto();
        int fired = f.shots.getActiveShots().size();
        assertEquals(f.weapon.burstCount, fired);

        looking[0] = false;
        for (int i = 0; i < (int) (2f * f.weapon.cooldown / DT); i++) f.auto();
        assertEquals(fired, f.shots.getActiveShots().size(), "the spotter looked away: no salvo at a memory");
        assertEquals(0L, f.turret.targetId);
    }

    @Test
    void aHeldLauncherChainsItsSalvosUntilReleaseWhileAClickIsOne() {
        Fixture click = new Fixture(VehicleType.LRM_CARRIER);
        click.tick(click.north(), true);
        for (int i = 0; i < 300; i++) click.tick(click.north(), false);
        assertEquals(click.weapon.burstCount, click.shots.getActiveShots().size(), "a click is one salvo");

        Fixture held = new Fixture(VehicleType.LRM_CARRIER);
        int salvos = 3;
        float salvoSeconds = held.weapon.burstCount * held.weapon.burstSpacing;
        int guard = 2000;
        while (held.shots.getActiveShots().size() < (salvos - 1) * held.weapon.burstCount + 1 && guard-- > 0) {
            held.tick(held.north(), true);
        }
        assertEquals((salvos - 1) * held.weapon.burstCount + 1, held.turret.holdRounds);
        for (int i = 0; i < 300; i++) held.tick(held.north(), false);
        assertEquals(salvos * held.weapon.burstCount, held.shots.getActiveShots().size(),
                "release ends the chain after the salvo in flight");
        assertEquals(0, held.turret.holdRounds);
        assertTrue(salvos * salvoSeconds < held.weapon.cooldown, "three salvos inside one cooldown");
    }

    @Test
    void clicksInsideTheCooldownFireTheRacksNextGroupsAndTheLoaderBringsThemBack() {
        Fixture f = new Fixture(VehicleType.LRM_CARRIER);
        int groups = f.mount.mount.ammoCapacity / f.weapon.burstCount;
        int ticks = 0;
        for (int click = 0; click < groups; click++) {
            f.tick(f.north(), true);
            for (int i = 0; i < 30; i++, ticks++) f.tick(f.north(), false);
        }
        assertTrue(ticks * DT < f.weapon.cooldown, "every click lands inside the first group's cooldown");
        assertEquals(groups * f.weapon.burstCount, f.shots.getActiveShots().size(), "each click is the next group");
        assertEquals(0, f.turret.ammo);
        assertFalse(f.turret.overloaded, "the whole load fits inside the gun's flux");

        for (int i = 0; i < (int) Math.ceil(f.turret.loaderSeconds / DT) + 1; i++) f.tick(f.north(), false);
        assertEquals(f.weapon.burstCount, f.turret.ammo, "the loader brings one group back per cadence");
    }

    @Test
    void anAutonomousLauncherChainsTwoSalvosIntoATank() {
        Fixture f = new Fixture(VehicleType.LRM_CARRIER);
        long tank = f.hostileHull(VehicleType.BREAKTHROUGH_TANK, 20.5f, 45.5f);
        f.system.setSpotting((side, target) -> side == Faction.MARINE && target == tank);
        for (int i = 0; i < (int) (f.weapon.cooldown * 0.9f / DT); i++) f.auto();
        assertEquals(2 * f.weapon.burstCount, f.shots.getActiveShots().size(),
                "half a capped load at armor that is not a mech");
    }

    @Test
    void ownSightBeatsASpottersCallThenTheNearerThenTheLowerId() {
        int own = GroundVehicleTurretSystem.OWN_SIGHT;
        int spotted = GroundVehicleTurretSystem.SPOTTED;
        assertTrue(GroundVehicleTurretSystem.better(spotted, 5f, 9L, 0, Float.MAX_VALUE, 0L));
        assertTrue(GroundVehicleTurretSystem.better(own, 30f, 9L, spotted, 5f, 4L));
        assertFalse(GroundVehicleTurretSystem.better(spotted, 5f, 9L, own, 30f, 4L));
        assertTrue(GroundVehicleTurretSystem.better(own, 5f, 9L, own, 30f, 4L));
        assertTrue(GroundVehicleTurretSystem.better(own, 5f, 3L, own, 5f, 4L));
        assertFalse(GroundVehicleTurretSystem.better(own, 5f, 5L, own, 5f, 4L));
    }

    @Test
    void aShotAwayGunNeitherFiresNorTraversesAndTheHullReadsUnarmed() {
        Fixture f = new Fixture(VehicleType.BREAKTHROUGH_TANK);
        if (f.turret.section == null) return;
        DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
        HullTurretSection.Hit hit = new HullTurretSection.Hit();
        f.turret.section.strike(10000f, 100f, f.type.armorRating, 1f, scratch, hit);

        assertTrue(f.turret.destroyed());
        assertFalse(f.roster.convoy().isArmed(f.id));
        assertEquals(0f, f.roster.convoy().weaponRange(f.id));
        f.turret.facingDeg = 35f;
        long enemy = f.enemy(20, 40);
        for (int i = 0; i < 120; i++) f.tick(f.north(), true);
        f.turret.targetId = enemy;
        for (int i = 0; i < 120; i++) f.tick(null, false);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(35f, f.turret.facingDeg);
        assertEquals(0L, f.turret.targetId);
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(96, 96);
        final UnitSpatialIndex index = new UnitSpatialIndex(96, 96);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final ShotService shots = new ShotService();
        final List<PendingDetonation> detonations = new ArrayList<>();
        final VehicleType type;
        final StructureDef mount;
        final WeaponDef weapon;
        final long id;
        final GroundTurret turret;
        final GroundBody body;
        final GroundVehicleTurretSystem system;
        final TurretFireSystem fire;

        Fixture() { this(VehicleType.HEAVY_APC); }
        Fixture(VehicleType type) {
            this.type = type;
            mount = type.turretStructure();
            weapon = mount.mount.weapon;
            for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) grid.setWalkableFloor(x, y);
            id = roster.convoy().spawn(type, Faction.MARINE,
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

        float mountX() { return body.x + type.turretMountX; }
        float mountY() { return body.y + type.turretMountY; }
        PointFireAim north() { return new PointFireAim(mountX(), 60f); }
        void tick(PointFireAim aim, boolean trigger) { system.tick(DT, id, aim, trigger); }
        void auto() { system.tick(DT, 0L, null, false); }
        long hostileHull(VehicleType hull, float x, float y) {
            long other = roster.convoy().spawn(hull, Faction.DEFENDER, VehicleMission.deployed(x, y));
            roster.convoy().body(other).teleport(x, y, 180f);
            // Its own gun stays silent: these fixtures ask about the launcher, and the bare fixture has no scorer.
            roster.convoy().turret(other).ammo = 0;
            roster.convoy().turret(other).loaderSeconds = Float.POSITIVE_INFINITY;
            return other;
        }
        long enemy(int x, int y) {
            return roster.spawn(new EntitySpec("turret target", Faction.DEFENDER, UnitType.MARINE, x, y));
        }
    }

    @Test
    void aSecondMountFiresFromTheRightButtonOnItsOwnMagazineAndHoldsOtherwise() {
        Fixture f = new Fixture();
        f.turret.secondaries.add(MountedGun.of(new VehicleMount(f.mount.id, .3f, -.3f, false)));
        GroundTurret coax = f.turret.secondaries.get(0).turret();
        int coaxRounds = coax.ammo;
        f.system.tick(DT, f.id, f.north(), true, false, TriggerPicks.LINKED);
        assertEquals(1, f.shots.getActiveShots().size(), "the left button fires the main gun alone");
        assertEquals(coaxRounds, coax.ammo);
        int mainRounds = f.turret.ammo;
        f.system.tick(DT, f.id, f.north(), false, true, TriggerPicks.LINKED);
        assertEquals(coaxRounds - 1, coax.ammo, "the right button fires the second mount");
        assertEquals(mainRounds, f.turret.ammo);
    }

    @Test
    void aLauncherStandsUpWhileItHasSomethingToFireAtAndLiesBackDownAfter() {
        Fixture f = new Fixture(VehicleType.LRM_CARRIER);
        GroundTurret t = new GroundTurret(10);
        float tenth = GroundVehicleTurretSystem.RAISE_SECONDS / 10f;
        t.targetId = 7L;
        for (int i = 0; i < 10; i++) GroundVehicleTurretSystem.raise(t, f.weapon, tenth);
        assertEquals(1f, t.elevation, 1e-4f);
        t.targetId = 0L;
        for (int i = 0; i < 5; i++) GroundVehicleTurretSystem.raise(t, f.weapon, tenth);
        assertEquals(.5f, t.elevation, 1e-4f, "no target and no salvo: it lies back down");
        t.cooldownTimer = f.weapon.cooldown;
        for (int i = 0; i < 5; i++) GroundVehicleTurretSystem.raise(t, f.weapon, tenth);
        assertEquals(1f, t.elevation, 1e-4f, "it stays up just after a salvo");
    }

    @Test
    void aFireMissionComesFirstWhileSeenAndTheLauncherPicksItsOwnOnlyWhileNobodySeesIt() {
        long bore = 7L, runner = 9L;
        int spotted = GroundVehicleTurretSystem.SPOTTED, own = GroundVehicleTurretSystem.OWN_SIGHT;
        int none = GroundVehicleTurretSystem.NO_SIGHT;
        assertEquals(bore, GroundVehicleTurretSystem.choose(bore, spotted, runner, own));
        assertEquals(runner, GroundVehicleTurretSystem.choose(bore, none, runner, own));
        assertEquals(0L, GroundVehicleTurretSystem.choose(bore, none, runner, none));
        assertEquals(runner, GroundVehicleTurretSystem.choose(0L, none, runner, spotted));
    }
}
