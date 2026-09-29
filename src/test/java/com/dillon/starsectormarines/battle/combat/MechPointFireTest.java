package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechCombatantBehavior;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechTurretSystem;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Mount authority and physical point rounds on a bare roster, without a battle host. */
class MechPointFireTest {
    private static final PointFireAim NORTH = new PointFireAim(20.5f, 60.5f);
    private static final PointFireAim EAST = new PointFireAim(60.5f, 20.5f);

    @Test
    void directMountsKeepIndependentResourcesAndIndirectMountNeverTriggers() {
        Fixture f = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS,
                MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        MechWeaponMount srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        MechWeaponMount lrm = f.loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        f.fire(NORTH);
        assertEquals(2, f.shots.getActiveShots().size());
        assertEquals(arms.weaponDef().cooldown - BattleSimulation.TICK_DT, arms.cooldown, 0.00001f);
        assertEquals(srm.weaponDef().cooldown - BattleSimulation.TICK_DT, srm.cooldown, 0.00001f);
        assertEquals(srm.component.ammoCapacity - 1, srm.ammo);
        assertEquals(lrm.component.ammoCapacity, lrm.ammo);
        assertEquals(0f, lrm.cooldown);
        assertEquals(0, lrm.burstRemaining);
        assertEquals(11, arms.burstRemaining);
        assertEquals(1, srm.burstRemaining, "the installed small rack owns packet size");
        assertEquals(NORTH, arms.burstPointAim);
        assertEquals(0L, arms.burstTargetId);
    }

    @Test
    void selectedHardpointIsTheOnlyManualTriggerAndOtherMountClocksStillAdvanceOnce() {
        Fixture f = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS,
                MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5);
        var arms = f.loadout.mount(MechMountSlot.ARMS);
        var srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        var lrm = f.loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        arms.cooldown = 1f;
        int ammo = srm.ammo;
        f.weapons.tick(f.shooter, NORTH, true, 2);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(0, arms.burstRemaining);
        assertEquals(1f - BattleSimulation.TICK_DT, arms.cooldown, 0.00001f);
        assertEquals(ammo - 1, srm.ammo);
        assertEquals(1, srm.burstRemaining);
        assertEquals(0f, lrm.cooldown);
        assertEquals(lrm.component.ammoCapacity, lrm.ammo);
        assertFalse(HeavyWeapons.supportsPointFire(lrm));
        for (int i = 0; i < 12; i++) f.weapons.tick(f.shooter, NORTH, false, 2);
        assertEquals(srm.component.projectilesPerTrigger, f.shots.getActiveShots().size());
        assertEquals(ammo - 1, srm.ammo, "selection does not split or reprice the authored trigger pack");

        Fixture invalid = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS, null, null);
        invalid.weapons.tick(invalid.shooter, NORTH, true, 4);
        assertTrue(invalid.shots.getActiveShots().isEmpty());
    }

    @Test
    void burstTracksLivePointAlternatesBarrelsAndDoesNotRestartWhileHeld() {
        Fixture f = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS, null, null);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        f.fire(NORTH);
        f.loadout.torsoFacingDegrees = -90f;
        int ticks = 1;
        while (arms.burstRemaining > 0 && ticks < 100) {
            f.weapons.tick(f.shooter, EAST, true);
            ticks++;
        }
        assertEquals(12, f.shots.getActiveShots().size());
        assertEquals(0, arms.burstRemaining);
        assertNull(arms.burstPointAim);
        assertTrue(f.shots.getActiveShots().get(0).toY > f.shots.getActiveShots().get(0).fromY);
        assertTrue(f.shots.getActiveShots().subList(1, 12).stream().allMatch(s -> s.toX > s.fromX));
        assertNotEquals(f.shots.getActiveShots().get(1).fromY, f.shots.getActiveShots().get(2).fromY,
                "paired barrels still alternate after changing aim");
        assertEquals(arms.weaponDef().cooldown - ticks * BattleSimulation.TICK_DT, arms.cooldown, 0.00001f);
    }

    @Test
    void invalidRearAndBehindMuzzlePointsSpendNoResourcesDespiteStaleAimCache() {
        Fixture f = new Fixture(MechWeaponComponent.SINGLE_HEAVY_CANNON, null, null);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        f.loadout.torsoOnTarget = true;
        for (PointFireAim aim : List.of(new PointFireAim(Float.NaN, 0f),
                new PointFireAim(20.5f, 20.5f), new PointFireAim(20.5f, 20.6f), EAST)) {
            f.fire(aim);
        }
        f.loadout.torsoFacingDegrees = 180f;
        f.fire(new PointFireAim(20.5f, 2.5f));
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(0f, arms.cooldown);
        assertEquals(0, f.roster.telemetry().roundsFired(f.shooter));
    }

    @Test
    void refusedManualReleasesExpireWithoutRefundingOrFiringAStaleBacklog() {
        Fixture f = new Fixture(null, MechWeaponComponent.SRM_5, null);
        MechWeaponMount mount = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        f.fire(NORTH);
        f.loadout.torsoFacingDegrees = 90f;
        for (int i = 0; i < 12; i++) f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(0, mount.burstRemaining);
        assertNull(mount.burstPointAim);
        assertEquals(mount.component.ammoCapacity - 1, mount.ammo);
        assertEquals(mount.weaponDef().cooldown - 13 * BattleSimulation.TICK_DT, mount.cooldown, 0.00001f);
        assertTrue(mount.replenishmentProgressSeconds > 0f);
        f.loadout.torsoFacingDegrees = 0f;
        f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
    }

    @Test
    void blockedManualBarrelExpiresItsPacketAndClearPreservesResources() {
        Fixture f = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS, null, null);
        MechWeaponMount mount = f.loadout.mount(MechMountSlot.ARMS);
        f.fire(NORTH);
        f.roster.world().setPos(f.shooter, 20.5f, 20.35f);
        for (int x = 0; x < 128; x++) f.grid.setWalkable(x, 21, false);
        assertFalse(f.weapons.canFireMechMount(f.shooter, mount));
        for (int i = 0; i < 30; i++) f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(0, mount.burstRemaining);
        float cooldown = mount.cooldown;
        float replenishment = mount.replenishmentProgressSeconds;
        int ammo = mount.ammo;
        f.loadout.clearQueuedFire();
        assertEquals(cooldown, mount.cooldown);
        assertEquals(replenishment, mount.replenishmentProgressSeconds);
        assertEquals(ammo, mount.ammo);
        for (int x = 0; x < 128; x++) f.grid.setWalkableFloor(x, 21);
        f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
    }

    @Test
    void torsoFollowsCursorDuringCommittedBurstAndNeverBorrowsTheAiTarget() {
        Fixture f = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS, null, null);
        f.fire(NORTH);
        long enemy = f.roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 2, 20));
        f.roster.world().setTargetId(f.shooter, enemy);
        MechTurretSystem torso = new MechTurretSystem(f.roster.entityWorld(), f.roster.components(), f.roster);
        torso.tick(BattleSimulation.TICK_DT, f.shooter, EAST);
        assertTrue(f.loadout.torsoFacingDegrees < 0f, "live cursor wins while the north burst is pending");
        assertEquals(0L, f.loadout.torsoAimTargetId);
        assertFalse(f.loadout.torsoOnTarget);
        assertTrue(f.loadout.mount(MechMountSlot.ARMS).burstRemaining > 0);
        torso.tick(BattleSimulation.TICK_DT, f.shooter, EAST);
        assertTrue(f.loadout.torsoFacingDegrees < 0f, "the manual east bearing wins over the AI west target");
    }

    @Test
    void wallBlastUsesPhysicalContactAndAiFlightStartsAtTheVisibleMuzzle() {
        Fixture f = new Fixture(MechWeaponComponent.DUAL_CHAINGUNS, null, null);
        for (int x = 0; x < 128; x++) f.grid.setWalkable(x, 40, false);
        f.fire(NORTH);
        ShotEvent shot = f.shots.getActiveShots().get(0);
        assertEquals(BallisticResolver.StopKind.WALL, shot.stopKind);
        PendingDetonation blast = f.detonations.getPending().get(0);
        assertEquals(shot.toX, blast.endpointX);
        assertEquals(shot.toY, blast.endpointY);
        assertEquals(shot.lifetimeMax, blast.remainingTime);
        assertEquals(40f, blast.endpointY, 0.001f);
        long enemy = f.roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 20, 60));
        f.loadout.clearQueuedFire();
        MechWeaponMount mount = f.loadout.mount(MechMountSlot.ARMS);
        f.weapons.fireMechWeapon(f.shooter, enemy, mount, 1f);
        ShotEvent ai = f.shots.getActiveShots().get(1);
        float physicalTime = (float) Math.hypot(ai.toX - ai.fromX, ai.toY - ai.fromY) / mount.weaponDef().roundVelocity;
        assertEquals(physicalTime, ai.lifetimeMax, 0.00001f);
    }

    @Test
    void directMissileRemainsInterceptableAndOpenFlightHasNoPhantomBlast() {
        Fixture f = new Fixture(null, MechWeaponComponent.SRM_5, null);
        for (int x = 0; x < 128; x++) f.grid.setWalkable(x, 32, false);
        f.fire(NORTH);
        Projectile rocket = f.shots.getActiveProjectiles().get(0);
        assertNotNull(rocket.onArrival);
        List<PendingDetonation> arrivals = new ArrayList<>();
        f.shots.tickProjectiles(rocket.remainingTime * 0.5f, arrivals::add);
        assertTrue(arrivals.isEmpty());
        rocket.intercepted = true;
        f.shots.tickProjectiles(100f, arrivals::add);
        assertTrue(arrivals.isEmpty());
        assertTrue(f.detonations.getPending().isEmpty());

        Fixture open = new Fixture(null, MechWeaponComponent.PIONEER_ROCKET_CRADLE, null);
        open.fire(new PointFireAim(20.5f, 35.5f));
        Projectile overshoot = open.shots.getActiveProjectiles().get(0);
        assertNull(overshoot.onArrival);
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, open.shots.getActiveShots().get(0).stopKind);
        assertTrue(open.shots.getActiveShots().get(0).toY > 35.5f);
        open.shots.tickProjectiles(100f, arrivals::add);
        assertTrue(arrivals.isEmpty());
    }

    @Test
    void directEnergyContactArrivesAfterFlightWithoutAnIntendedTarget() {
        Fixture f = new Fixture(null, MechWeaponComponent.THERMAL_LANCE, null);
        long victim = f.roster.spawn(new EntitySpec("incidental body", Faction.DEFENDER,
                UnitType.MARINE, 20, 40));
        f.fire(new PointFireAim(20.5f, 40.5f));
        ShotEvent shot = f.shots.getActiveShots().get(0);
        assertFalse(shot.hit);
        assertTrue(shot.struckUnit);
        assertEquals(0L, f.roster.world().targetId(f.shooter));
        List<ShotService.PendingImpact> impacts = new ArrayList<>();
        f.shots.tickImpacts(shot.lifetimeMax * 0.5f, impacts::add);
        assertTrue(impacts.isEmpty());
        f.shots.tickImpacts(shot.lifetimeMax * 0.5f + 0.00001f, impacts::add);
        assertEquals(1, impacts.size());
        assertEquals(victim, impacts.get(0).victimId);
        assertEquals(f.loadout.mount(MechMountSlot.LEFT_SHOULDER).weaponDef().contactDamage,
                impacts.get(0).damage);
        assertEquals(1, f.detonations.getPending().size());
        assertTrue(f.detonations.getPending().get(0).excludesAreaTarget(victim));
    }

    @Test
    void barrelCannotCrossWallForManualAiTriggerOrContinuationButCoverDoesNotBlockIt() {
        Fixture f = new Fixture(MechWeaponComponent.SINGLE_HEAVY_CANNON, null, null);
        MechWeaponMount mount = f.loadout.mount(MechMountSlot.ARMS);
        f.roster.world().setPos(f.shooter, 20.5f, 20.35f);
        for (int x = 0; x < 128; x++) f.grid.setWalkable(x, 21, false);
        assertTrue(ManualTerrainMotion.canStand(f.grid, 20.5f, 20.35f, MechVariant.BULWARK.radius));
        assertFalse(f.weapons.canFireMechMount(f.shooter, mount));
        f.fire(NORTH);
        assertEquals(0f, mount.cooldown);
        assertTrue(f.shots.getActiveShots().isEmpty());

        long victim = f.roster.spawn(new EntitySpec("beyond wall", Faction.DEFENDER, UnitType.MARINE, 20, 60));
        f.loadout.torsoOnTarget = true;
        f.loadout.torsoAimTargetId = victim;
        BattleControl battle = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                new Class<?>[]{BattleControl.class}, (proxy, method, args) -> {
                    if (method.getName().equals("canFireMechMount")) {
                        return f.weapons.canFireMechMount((long) args[0], (MechWeaponMount) args[1]);
                    }
                    throw new AssertionError("Blocked trigger reached " + method.getName());
                });
        MechCombatantBehavior.tryFireArms(f.shooter, f.loadout, victim, 20f, battle, true);
        assertEquals(0f, mount.cooldown);
        f.weapons.fireMechWeapon(f.shooter, victim, mount, 1f);
        assertTrue(f.shots.getActiveShots().isEmpty());
        mount.burstRemaining = 1;
        mount.burstTargetId = victim;
        f.weapons.tick();
        assertEquals(1, mount.burstRemaining);
        assertTrue(f.shots.getActiveShots().isEmpty());

        for (int x = 0; x < 128; x++) f.grid.setWalkableFloor(x, 21);
        f.grid.setCoverAtFacing(20, 21, NavigationGrid.FACING_N, 3);
        assertTrue(f.weapons.canFireMechMount(f.shooter, mount));
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(128, 128);
        final UnitSpatialIndex index = new UnitSpatialIndex(128, 128);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final ShotService shots = new ShotService();
        final MechLoadoutComponent loadout;
        final Detonations detonations;
        final HeavyWeapons weapons;
        final long shooter;

        Fixture(MechWeaponComponent arms, MechWeaponComponent left, MechWeaponComponent right) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) grid.setWalkableFloor(x, y);
            shooter = roster.spawn(new EntitySpec("manual mech", Faction.MARINE, UnitType.HEAVY_MECH, 20, 20));
            loadout = new MechLoadoutComponent(MechVariant.BULWARK, arms, left, right, MechRole.ARMORED_SUPPORT);
            loadout.torsoFacingDegrees = 0f;
            roster.world().attachMechLoadout(shooter, loadout);
            roster.entityWorld().setFloat(shooter, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
            detonations = new Detonations(roster, grid, new CellTopology(128, 128), null, null, null, null, null);
            weapons = new HeavyWeapons(roster, grid,
                    new BallisticResolver(grid, new DoodadService(grid), index, roster), shots,
                    detonations, new Random() { @Override public float nextFloat() { return 0f; } });
        }

        void fire(PointFireAim aim) { weapons.tick(shooter, aim, true); }
    }
}
