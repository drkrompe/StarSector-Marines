package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectFireUnificationTest {

    private static final int W = 20;
    private static final int H = 12;
    private static final int ROW = 5;
    private static final int WALL_X = 6;
    private static final float EPS = 1e-4f;

    private static BattleSimulation arena(boolean wallColumn) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        if (wallColumn) {
            for (int y = 0; y < H; y++) grid.setWalkable(WALL_X, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static long target(BattleSimulation sim) {
        return sim.spawn(new EntitySpec("target", Faction.DEFENDER,
                UnitType.MARINE, 10, ROW));
    }

    private static ShotEvent onlyShot(BattleSimulation sim) {
        assertEquals(1, sim.getActiveShots().size());
        return sim.getActiveShots().get(0);
    }

    @Test
    void handheldRocketDetonatesAtTheResolvedWallWithRealFlightTime() {
        BattleSimulation sim = arena(true);
        long shooter = sim.spawn(new EntitySpec("rocketeer", Faction.MARINE,
                UnitType.MARINE, 2, ROW)
                .secondary(MarineSecondary.ROCKET_LAUNCHER, 1));

        sim.fireSecondary(shooter, target(sim));

        ShotEvent shot = onlyShot(sim);
        assertSame(MarineSecondary.ROCKET_LAUNCHER, shot.marineSecondary);
        assertEquals(BallisticResolver.StopKind.WALL, shot.stopKind);
        assertEquals(WALL_X + 0.5f, shot.toX, EPS);
        assertEquals(1, sim.getActiveProjectiles().size());
        Projectile projectile = sim.getActiveProjectiles().get(0);
        assertEquals(WALL_X + 0.5f, projectile.onArrival.endpointX, EPS);
        assertTrue(projectile.totalFlightTime < MarineSecondary.ROCKET_LAUNCHER.flightSec,
                "a nearer wall arrives sooner than the old fixed maximum-range timing");
    }

    @Test
    void directRocketOvershootKeepsTheProjectileButCarriesNoDetonation() {
        BattleSimulation sim = arena(false);
        long shooter = sim.spawn(new EntitySpec("rocketeer", Faction.MARINE,
                UnitType.MARINE, 2, ROW)
                .secondary(MarineSecondary.ROCKET_LAUNCHER, 1));
        long evasive = sim.spawn(new EntitySpec("evasive", Faction.DEFENDER,
                UnitType.MARINE, 10, ROW).armor(0f, 0f, 1f, 0f));

        sim.fireSecondary(shooter, evasive);

        ShotEvent shot = onlyShot(sim);
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, shot.stopKind);
        assertFalse(shot.impacts());
        assertEquals(1, sim.getActiveProjectiles().size());
        assertNull(sim.getActiveProjectiles().get(0).onArrival,
                "a free-flight miss must not create a phantom ground explosion");
    }

    @Test
    void mechChaingunAndSrmUseResolvedStopsButLrmStaysIndirect() {
        BattleSimulation chaingunSim = arena(true);
        long chaingunMech = chaingunSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        long chaingunTarget = target(chaingunSim);

        chaingunSim.fireMechWeapon(chaingunMech, chaingunTarget, MechWeapon.CHAINGUN);
        ShotEvent chaingun = onlyShot(chaingunSim);
        assertSame(MechWeapon.CHAINGUN, chaingun.mechWeapon);
        assertEquals(BallisticResolver.StopKind.WALL, chaingun.stopKind);
        assertEquals(1, chaingunSim.getInflightDetonations().size());
        assertEquals(WALL_X + 0.5f,
                chaingunSim.getInflightDetonations().get(0).endpointX, EPS);

        BattleSimulation srmSim = arena(true);
        long srmMech = srmSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        srmSim.fireMechWeapon(srmMech, target(srmSim), MechWeapon.SRM_POD);
        ShotEvent srm = onlyShot(srmSim);
        assertSame(MechWeapon.SRM_POD, srm.mechWeapon);
        assertEquals(BallisticResolver.StopKind.WALL, srm.stopKind);
        assertEquals(WALL_X + 0.5f,
                srmSim.getActiveProjectiles().get(0).onArrival.endpointX, EPS);

        BattleSimulation lrmSim = arena(true);
        long lrmMech = lrmSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        lrmSim.fireMechWeapon(lrmMech, target(lrmSim), MechWeapon.LRM_ARTILLERY);
        ShotEvent lrm = onlyShot(lrmSim);
        assertSame(MechWeapon.LRM_ARTILLERY, lrm.mechWeapon);
        assertNull(lrm.stopKind, "indirect artillery retains its scatter/projectile path");
        assertTrue(lrmSim.getActiveProjectiles().get(0).onArrival.aerialDelivery);
    }

    @Test
    void groundBurstTurretUsesResolverWhileAerialMountStaysLegacy() {
        BattleSimulation sim = arena(true);
        long turret = sim.spawn(MapTurret.create(
                "vulcan", Faction.MARINE, TurretKind.VULCAN, 2, ROW));
        long target = target(sim);

        sim.fireShotFrom(turret, sim.world().x(turret), sim.world().y(turret),
                Faction.MARINE, TurretKind.VULCAN, target,
                /*aerialShooter*/ false, /*hasLos*/ true);
        ShotEvent ground = onlyShot(sim);
        assertSame(TurretKind.VULCAN, ground.turretKind);
        assertEquals(BallisticResolver.StopKind.WALL, ground.stopKind);
        assertEquals(WALL_X + 0.5f,
                sim.getInflightDetonations().get(0).endpointX, EPS);

        BattleSimulation aerialSim = arena(true);
        long aerialTurret = aerialSim.spawn(MapTurret.create(
                "vulcan", Faction.MARINE, TurretKind.VULCAN, 2, ROW));
        long aerialTarget = target(aerialSim);
        aerialSim.fireShotFrom(aerialSim.world().x(aerialTurret),
                aerialSim.world().y(aerialTurret),
                Faction.MARINE, TurretKind.VULCAN, aerialTarget,
                /*aerialShooter*/ true, /*hasLos*/ true);
        assertNull(onlyShot(aerialSim).stopKind,
                "aerial mounts wait for the explicit airborne collision policy");
    }

    @Test
    void payloadlessProjectileExpiresWithoutDetonationOrArrivalFx() {
        ShotService shots = new ShotService();
        shots.queueProjectile(new Projectile(
                1f, 1f, 3f, 1f, false, 0f,
                Faction.MARINE, false, 0.1f, null));
        AtomicInteger detonations = new AtomicInteger();

        shots.tickProjectiles(0.2f, det -> detonations.incrementAndGet());

        assertEquals(0, detonations.get());
        assertTrue(shots.getActiveProjectiles().isEmpty());
        assertTrue(shots.getProjectilesArrivedThisFrame().isEmpty());
    }
}
