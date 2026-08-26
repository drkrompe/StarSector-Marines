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
        return arena(wallColumn, BattleSimulation.DEFAULT_SEED);
    }

    private static BattleSimulation arena(boolean wallColumn, long seed) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        if (wallColumn) {
            for (int y = 0; y < H; y++) grid.setWalkable(WALL_X, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H), seed);
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
        assertEquals(WALL_X, shot.toX, EPS);
        assertEquals(1, sim.getActiveProjectiles().size());
        Projectile projectile = sim.getActiveProjectiles().get(0);
        assertEquals(WALL_X, projectile.onArrival.endpointX, EPS);
        assertTrue(projectile.totalFlightTime < MarineSecondary.ROCKET_LAUNCHER.flightSec(),
                "a nearer wall arrives sooner than the old fixed maximum-range timing");
    }

    @Test
    void antiMaterielRoundStopsAtWallWithoutProjectileOrDetonation() {
        BattleSimulation sim = arena(true);
        long shooter = sim.spawn(new EntitySpec("heavy marksman", Faction.MARINE,
                UnitType.MARINE, 2, ROW)
                .secondary(MarineSecondary.ANTI_MATERIEL_RIFLE, 1));

        sim.fireSecondary(shooter, target(sim));

        ShotEvent shot = onlyShot(sim);
        assertSame(MarineSecondary.ANTI_MATERIEL_RIFLE, shot.marineSecondary);
        assertEquals(BallisticResolver.StopKind.WALL, shot.stopKind);
        assertEquals(WALL_X, shot.toX, EPS);
        assertTrue(sim.getActiveProjectiles().isEmpty(),
                "a precision heavy round is not a missile entity");
        assertTrue(sim.getInflightDetonations().isEmpty(),
                "the AMR carries no splash or structural payload");
        assertEquals(0, sim.world().secondaryAmmo(shooter));
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
        assertEquals(WALL_X,
                chaingunSim.getInflightDetonations().get(0).endpointX, EPS);

        BattleSimulation srmSim = arena(true);
        long srmMech = srmSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        srmSim.fireMechWeapon(srmMech, target(srmSim), MechWeapon.SRM_POD);
        ShotEvent srm = onlyShot(srmSim);
        assertSame(MechWeapon.SRM_POD, srm.mechWeapon);
        assertEquals(BallisticResolver.StopKind.WALL, srm.stopKind);
        assertEquals(WALL_X,
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
    void gunLaunchedHeavyHeUsesVisibleBallisticRoundsAndTimedSplash() {
        BattleSimulation mechSim = arena(true);
        long mech = mechSim.spawn(new EntitySpec("sirocco", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        mechSim.fireMechWeapon(mech, target(mechSim), MechWeapon.HEAVY_CANNON);

        ShotEvent cannon = onlyShot(mechSim);
        assertSame(MechWeapon.HEAVY_CANNON, cannon.mechWeapon);
        assertTrue(cannon.weaponDef().fx.hasHeavyImpact());
        assertEquals(BallisticResolver.StopKind.WALL, cannon.stopKind);
        assertEquals(WALL_X, cannon.toX, EPS);
        assertTrue(mechSim.getActiveProjectiles().isEmpty(),
                "a gun shell is a ballistic shot, not a boost-ramping missile entity");
        assertEquals(1, mechSim.getInflightDetonations().size());
        PendingDetonation cannonBlast = mechSim.getInflightDetonations().get(0);
        assertEquals(MechWeapon.HEAVY_CANNON.aoeRadius(), cannonBlast.aoeRadius, EPS);
        assertEquals(MechWeapon.HEAVY_CANNON.wallDamage(), cannonBlast.wallDamage);

        BattleSimulation turretSim = arena(true);
        long mortar = turretSim.spawn(MapTurret.create(
                "heavy-mortar", Faction.MARINE, TurretKind.HEAVY_MORTAR, 2, ROW));
        turretSim.fireShotFrom(mortar,
                turretSim.world().x(mortar), turretSim.world().y(mortar),
                Faction.MARINE, TurretKind.HEAVY_MORTAR, target(turretSim),
                /*aerialShooter*/ false, /*hasLos*/ true);

        ShotEvent mortarShot = onlyShot(turretSim);
        assertTrue(mortarShot.weaponDef().fx.hasHeavyImpact());
        assertEquals(BallisticResolver.StopKind.WALL, mortarShot.stopKind);
        assertEquals(1, turretSim.getInflightDetonations().size());
        PendingDetonation mortarBlast = turretSim.getInflightDetonations().get(0);
        assertEquals(TurretKind.HEAVY_MORTAR.aoeRadius(), mortarBlast.aoeRadius, EPS);
        assertEquals(TurretKind.HEAVY_MORTAR.wallDamage(), mortarBlast.wallDamage);
    }

    @Test
    void hephaestusDirectHitCarriesContactAndAreaPayloadsThroughTheTurretFirePath() {
        BattleSimulation sim = arena(false, 12345L);
        long cannon = sim.spawn(MapTurret.create(
                "hephaestus", Faction.MARINE, TurretKind.HEPHAESTUS, 2, ROW));
        long victim = target(sim);

        sim.fireShotFrom(cannon,
                sim.world().x(cannon), sim.world().y(cannon),
                Faction.MARINE, TurretKind.HEPHAESTUS, victim,
                /*aerialShooter*/ false, /*hasLos*/ true);

        ShotEvent shot = onlyShot(sim);
        assertSame(TurretKind.HEPHAESTUS, shot.turretKind);
        assertTrue(shot.weaponDef().fx.hasHeavyImpact());
        assertEquals(BallisticResolver.StopKind.UNIT_HIT, shot.stopKind);
        assertTrue(sim.getActiveProjectiles().isEmpty(),
                "the cannon shell uses the modeled ground direct-fire path, not a missile entity");
        assertEquals(1, sim.getInflightDetonations().size());
        PendingDetonation blast = sim.getInflightDetonations().get(0);
        assertEquals(victim, blast.directTargetId);
        assertEquals(117f, blast.directDamage, EPS);
        assertEquals(24f, blast.directPenetration, EPS);
        assertEquals(45f, blast.damage, EPS);
        assertEquals(4f, blast.penetration, EPS);
        assertEquals(1.6f, blast.aoeRadius, EPS);
        assertEquals(30, blast.wallDamage);
        assertEquals(1.25f, blast.wallDamageRadius, EPS);
        assertTrue(blast.authoredAftermath,
                "the authored cannon FX owns the smoke-and-fire aftermath at impact");
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
        assertEquals(WALL_X,
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
