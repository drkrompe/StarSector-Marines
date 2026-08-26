package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
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
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), 1));

        sim.fireSecondary(shooter, target(sim));

        ShotEvent shot = onlyShot(sim);
        assertSame(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), shot.specialEquipmentDef);
        assertEquals(BallisticResolver.StopKind.WALL, shot.stopKind);
        assertEquals(WALL_X, shot.toX, EPS);
        assertEquals(1, sim.getActiveProjectiles().size());
        Projectile projectile = sim.getActiveProjectiles().get(0);
        assertEquals(WALL_X, projectile.onArrival.endpointX, EPS);
        assertTrue(projectile.totalFlightTime < SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID).flightSec(),
                "a nearer wall arrives sooner than the old fixed maximum-range timing");
    }

    @Test
    void antiMaterielRoundStopsAtWallWithoutProjectileOrDetonation() {
        BattleSimulation sim = arena(true);
        long shooter = sim.spawn(new EntitySpec("heavy marksman", Faction.MARINE,
                UnitType.MARINE, 2, ROW)
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), 1));

        sim.fireSecondary(shooter, target(sim));

        ShotEvent shot = onlyShot(sim);
        assertSame(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), shot.specialEquipmentDef);
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
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), 1));
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
        WeaponDef chaingunDef = WeaponRegistry.require(WeaponRegistry.MECH_CHAINGUN_ID);
        BattleSimulation chaingunSim = arena(true);
        long chaingunMech = chaingunSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        long chaingunTarget = target(chaingunSim);

        chaingunSim.fireMechWeapon(chaingunMech, chaingunTarget, chaingunDef);
        ShotEvent chaingun = onlyShot(chaingunSim);
        assertSame(chaingunDef, chaingun.mechWeaponDef);
        assertEquals(BallisticResolver.StopKind.WALL, chaingun.stopKind);
        assertEquals(1, chaingunSim.getInflightDetonations().size());
        assertEquals(WALL_X,
                chaingunSim.getInflightDetonations().get(0).endpointX, EPS);

        BattleSimulation srmSim = arena(true);
        long srmMech = srmSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        WeaponDef srmDef = WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID);
        srmSim.fireMechWeapon(srmMech, target(srmSim), srmDef);
        ShotEvent srm = onlyShot(srmSim);
        assertSame(srmDef, srm.mechWeaponDef);
        assertEquals(BallisticResolver.StopKind.WALL, srm.stopKind);
        assertEquals(WALL_X,
                srmSim.getActiveProjectiles().get(0).onArrival.endpointX, EPS);

        BattleSimulation lrmSim = arena(true);
        long lrmMech = lrmSim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        WeaponDef lrmDef = WeaponRegistry.require(WeaponRegistry.MECH_LRM_ARTILLERY_ID);
        lrmSim.fireMechWeapon(lrmMech, target(lrmSim), lrmDef);
        ShotEvent lrm = onlyShot(lrmSim);
        assertSame(lrmDef, lrm.mechWeaponDef);
        assertNull(lrm.stopKind, "indirect artillery retains its scatter/projectile path");
        assertTrue(lrmSim.getActiveProjectiles().get(0).onArrival.aerialDelivery);
    }

    @Test
    void gunLaunchedHeavyHeUsesVisibleBallisticRoundsAndTimedSplash() {
        WeaponDef heavyCannon = WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID);
        BattleSimulation mechSim = arena(true);
        long mech = mechSim.spawn(new EntitySpec("sirocco", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, ROW));
        mechSim.fireMechWeapon(mech, target(mechSim), heavyCannon);

        ShotEvent cannon = onlyShot(mechSim);
        assertSame(heavyCannon, cannon.mechWeaponDef);
        assertTrue(cannon.weaponDef().fx.hasHeavyImpact());
        assertEquals(BallisticResolver.StopKind.WALL, cannon.stopKind);
        assertEquals(WALL_X, cannon.toX, EPS);
        assertTrue(mechSim.getActiveProjectiles().isEmpty(),
                "a gun shell is a ballistic shot, not a boost-ramping missile entity");
        assertEquals(1, mechSim.getInflightDetonations().size());
        PendingDetonation cannonBlast = mechSim.getInflightDetonations().get(0);
        assertEquals(heavyCannon.aoeRadius, cannonBlast.aoeRadius, EPS);
        assertEquals(heavyCannon.wallDamage, cannonBlast.wallDamage);

        BattleSimulation turretSim = arena(true);
        StructureDef mortarDef = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID);
        long mortar = turretSim.spawn(MapTurret.create(
                "heavy-mortar", Faction.MARINE, mortarDef.id, 2, ROW));
        turretSim.fireShotFrom(mortar,
                turretSim.world().x(mortar), turretSim.world().y(mortar),
                Faction.MARINE, mortarDef, target(turretSim),
                /*aerialShooter*/ false, /*hasLos*/ true);

        ShotEvent mortarShot = onlyShot(turretSim);
        assertTrue(mortarShot.weaponDef().fx.hasHeavyImpact());
        assertEquals(BallisticResolver.StopKind.WALL, mortarShot.stopKind);
        assertEquals(1, turretSim.getInflightDetonations().size());
        PendingDetonation mortarBlast = turretSim.getInflightDetonations().get(0);
        assertEquals(mortarDef.mount.weapon.aoeRadius, mortarBlast.aoeRadius, EPS);
        assertEquals(mortarDef.mount.weapon.wallDamage, mortarBlast.wallDamage);
    }

    @Test
    void hephaestusDirectHitCarriesContactAndAreaPayloadsThroughTheTurretFirePath() {
        BattleSimulation sim = arena(false, 12345L);
        StructureDef hephaestus = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID);
        long cannon = sim.spawn(MapTurret.create(
                "hephaestus", Faction.MARINE, hephaestus.id, 2, ROW));
        long victim = target(sim);

        sim.fireShotFrom(cannon,
                sim.world().x(cannon), sim.world().y(cannon),
                Faction.MARINE, hephaestus, victim,
                /*aerialShooter*/ false, /*hasLos*/ true);

        ShotEvent shot = onlyShot(sim);
        assertSame(hephaestus, shot.turretStructureDef);
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
        StructureDef vulcan = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID);
        long turret = sim.spawn(MapTurret.create(
                "vulcan", Faction.MARINE, vulcan.id, 2, ROW));
        long target = target(sim);

        sim.fireShotFrom(turret, sim.world().x(turret), sim.world().y(turret),
                Faction.MARINE, vulcan, target,
                /*aerialShooter*/ false, /*hasLos*/ true);
        ShotEvent ground = onlyShot(sim);
        assertSame(vulcan, ground.turretStructureDef);
        assertEquals(BallisticResolver.StopKind.WALL, ground.stopKind);
        assertEquals(WALL_X,
                sim.getInflightDetonations().get(0).endpointX, EPS);

        BattleSimulation aerialSim = arena(true);
        long aerialTurret = aerialSim.spawn(MapTurret.create(
                "vulcan", Faction.MARINE, vulcan.id, 2, ROW));
        long aerialTarget = target(aerialSim);
        aerialSim.fireShotFrom(aerialSim.world().x(aerialTurret),
                aerialSim.world().y(aerialTurret),
                Faction.MARINE, vulcan, aerialTarget,
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
