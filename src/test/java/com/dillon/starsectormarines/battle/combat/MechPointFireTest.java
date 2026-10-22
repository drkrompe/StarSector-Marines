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
    void everyMountKeepsIndependentResourcesAndTheArcingRackDropsOnThePoint() {
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        MechWeaponMount srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        MechWeaponMount lrm = f.loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        f.fire(NORTH);
        assertEquals(3, f.shots.getActiveShots().size());
        assertEquals(arms.component.cycleSeconds() - BattleSimulation.TICK_DT, arms.cycleRemaining, 0.00001f);
        assertEquals(srm.component.cycleSeconds() - BattleSimulation.TICK_DT, srm.cycleRemaining, 0.00001f);
        assertEquals(srm.component.ammoCapacity - 1, srm.ammo);
        assertEquals(lrm.component.ammoCapacity - 1, lrm.ammo);
        assertEquals(lrm.component.cycleSeconds() - BattleSimulation.TICK_DT, lrm.cycleRemaining, 0.00001f);
        Projectile salvo = f.shots.getActiveProjectiles().stream()
                .filter(p -> p.sourceWeaponId.equals(lrm.weaponDef().id)).findFirst().orElseThrow();
        assertNotNull(salvo.onArrival, "an LRM round always lands");
        assertEquals(NORTH.x(), salvo.onArrival.endpointX, 0.001f);
        assertEquals(NORTH.y(), salvo.onArrival.endpointY, 0.001f);
        assertEquals(arms.component.projectilesPerTrigger - 1, arms.burstRemaining);
        assertEquals(1, srm.burstRemaining, "the installed small rack owns packet size");
        assertEquals(NORTH, arms.burstPointAim);
        assertEquals(0L, arms.burstTargetId);
    }

    @Test
    void selectedHardpointIsTheOnlyManualTriggerAndOtherMountClocksStillAdvanceOnce() {
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5);
        var arms = f.loadout.mount(MechMountSlot.ARMS);
        var srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        var lrm = f.loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        arms.cycleRemaining = 1f;
        int ammo = srm.ammo;
        f.weapons.tick(f.shooter, NORTH, true, LEFT_SHOULDER_KEY);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(0, arms.burstRemaining);
        assertEquals(1f - BattleSimulation.TICK_DT, arms.cycleRemaining, 0.00001f);
        assertEquals(ammo - 1, srm.ammo);
        assertEquals(1, srm.burstRemaining);
        assertEquals(0f, lrm.cycleRemaining);
        assertEquals(lrm.component.ammoCapacity, lrm.ammo);
        assertTrue(HeavyWeapons.supportsPointFire(lrm));
        assertFalse(HeavyWeapons.firesFlat(lrm));
        for (int i = 0; i < 12; i++) f.weapons.tick(f.shooter, NORTH, false, 2);
        assertEquals(srm.component.projectilesPerTrigger, f.shots.getActiveShots().size());
        assertEquals(ammo - 1, srm.ammo, "selection does not split or reprice the authored trigger pack");

        Fixture invalid = new Fixture(MechWeaponComponent.CHAINGUN, null, null);
        invalid.weapons.tick(invalid.shooter, NORTH, true, 9);
        assertTrue(invalid.shots.getActiveShots().isEmpty());
    }

    @Test
    void eachArmGunTracksTheLivePointFromItsOwnArmAndDoesNotRestartWhileHeld() {
        Fixture f = Fixture.bulwark(MechWeaponComponent.CHAINGUN, null, null);
        MechWeaponMount left = f.loadout.mount(MechMountSlot.LEFT_ARM);
        MechWeaponMount right = f.loadout.mount(MechMountSlot.RIGHT_ARM);
        f.fire(NORTH);
        f.loadout.torsoFacingDegrees = -90f;
        int ticks = 1;
        while ((left.burstRemaining > 0 || right.burstRemaining > 0) && ticks < 100) {
            f.weapons.tick(f.shooter, EAST, true);
            ticks++;
        }
        int rounds = left.component.projectilesPerTrigger + right.component.projectilesPerTrigger;
        assertEquals(rounds, f.shots.getActiveShots().size());
        assertEquals(0, left.burstRemaining);
        assertEquals(0, right.burstRemaining);
        assertNull(left.burstPointAim);
        List<ShotEvent> shots = f.shots.getActiveShots();
        assertTrue(shots.subList(0, 2).stream().allMatch(s -> s.toY > s.fromY), "both arms open on the north point");
        assertTrue(shots.subList(2, rounds).stream().allMatch(s -> s.toX > s.fromX));
        assertNotEquals(shots.get(2).fromY, shots.get(3).fromY,
                "each arm keeps firing from its own muzzle after changing aim");
        assertEquals(left.component.cycleSeconds() - ticks * BattleSimulation.TICK_DT, left.cycleRemaining, 0.00001f);
    }

    @Test
    void invalidAndRearPointsSpendNoResourcesDespiteStaleAimCache() {
        Fixture f = new Fixture(MechWeaponComponent.SINGLE_HEAVY_CANNON, null, null);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        f.loadout.torsoOnTarget = true;
        for (PointFireAim aim : List.of(new PointFireAim(Float.NaN, 0f),
                new PointFireAim(20.5f, 20.5f), EAST)) {
            f.fire(aim);
        }
        f.loadout.torsoFacingDegrees = 180f;
        f.fire(new PointFireAim(20.5f, 2.5f));
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(0f, arms.cycleRemaining);
        assertEquals(0, f.roster.telemetry().roundsFired(f.shooter));
    }

    @Test
    void anLrmRackRefusesAPointInsideItsMinimumWithoutSpendingWhileRocketsStillFire() {
        Fixture f = new Fixture(null, MechWeaponComponent.SRM_5, MechWeaponComponent.LRM_5);
        MechWeaponMount srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        MechWeaponMount lrm = f.loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        float flux = f.loadout.flux.value();
        f.fire(new PointFireAim(20.5f, 20.5f + HeavyWeapons.MIN_MANUAL_ARC_RANGE_CELLS - 1f));
        assertEquals(lrm.component.ammoCapacity, lrm.ammo);
        assertEquals(0f, lrm.cycleRemaining);
        assertEquals(0, lrm.burstRemaining);
        assertEquals(srm.component.ammoCapacity - 1, srm.ammo, "the rocket rack on the same button still fires");
        assertTrue(f.loadout.flux.value() - flux <= srm.component.fluxPerTrigger() + 1e-4f,
                "the refused rack reserves no flux");
    }

    @Test
    void closePointsFireEveryBarrelConvergingAheadOfTheChassis() {
        // A point just ahead of the centre, and one a few cells off to the side, used to fail the
        // per-muzzle alignment check on the laterally offset barrels and drop half of every burst.
        for (PointFireAim aim : List.of(new PointFireAim(20.5f, 20.6f), new PointFireAim(21.0f, 25.5f))) {
            Fixture f = Fixture.bulwark(MechWeaponComponent.CHAINGUN, null, null);
            MechWeaponMount left = f.loadout.mount(MechMountSlot.LEFT_ARM);
            MechWeaponMount right = f.loadout.mount(MechMountSlot.RIGHT_ARM);
            f.loadout.torsoFacingDegrees = (float) Math.toDegrees(Math.atan2(aim.x() - 20.5f, aim.y() - 20.5f)) * -1f;
            f.roster.entityWorld().setFloat(f.shooter, f.roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, f.loadout.torsoFacingDegrees);
            f.fire(aim);
            for (int i = 0; i < 100 && (left.burstRemaining > 0 || right.burstRemaining > 0); i++) {
                f.weapons.tick(f.shooter, aim, true);
            }
            assertEquals(left.component.projectilesPerTrigger + right.component.projectilesPerTrigger,
                    f.shots.getActiveShots().size(), "every round of both arms' bursts leaves at " + aim);
            for (ShotEvent shot : f.shots.getActiveShots()) {
                assertTrue(shot.toY > shot.fromY, "rounds fly forward, never back under the chassis");
            }
        }
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
        assertEquals(mount.component.cycleSeconds() - 13 * BattleSimulation.TICK_DT, mount.cycleRemaining, 0.00001f);
        assertTrue(mount.replenishmentProgressSeconds > 0f);
        f.loadout.torsoFacingDegrees = 0f;
        f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
    }

    @Test
    void blockedManualBarrelExpiresItsPacketAndClearPreservesResources() {
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN, null, null);
        MechWeaponMount mount = f.loadout.mount(MechMountSlot.ARMS);
        f.fire(NORTH);
        f.roster.world().setPos(f.shooter, 20.5f, 20.35f);
        for (int x = 0; x < 128; x++) f.grid.setWalkable(x, 21, false);
        assertFalse(f.weapons.canFireMechMount(f.shooter, mount));
        for (int i = 0; i < 30; i++) f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(0, mount.burstRemaining);
        float cooldown = mount.cycleRemaining;
        float replenishment = mount.replenishmentProgressSeconds;
        int ammo = mount.ammo;
        f.loadout.clearQueuedFire();
        assertEquals(cooldown, mount.cycleRemaining);
        assertEquals(replenishment, mount.replenishmentProgressSeconds);
        assertEquals(ammo, mount.ammo);
        for (int x = 0; x < 128; x++) f.grid.setWalkableFloor(x, 21);
        f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(1, f.shots.getActiveShots().size());
    }

    @Test
    void torsoFollowsCursorDuringCommittedBurstAndNeverBorrowsTheAiTarget() {
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN, null, null);
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
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN, null, null);
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
    void directMissileRemainsInterceptableAndBurstsOnOpenGroundWhereAimed() {
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
        Projectile burst = open.shots.getActiveProjectiles().get(0);
        assertNotNull(burst.onArrival, "a rocket thrown at open ground bursts there");
        assertEquals(BallisticResolver.StopKind.GROUND_BURST, open.shots.getActiveShots().get(0).stopKind);
        assertEquals(35.5f, burst.onArrival.endpointY, 0.001f);
        open.shots.tickProjectiles(100f, arrivals::add);
        assertEquals(1, arrivals.size());
    }

    @Test
    void rocketAimedBeyondRangeStillFliesAndBurstsNoFurtherThanItsCeiling() {
        Fixture f = new Fixture(null, MechWeaponComponent.PIONEER_ROCKET_CRADLE, null);
        float range = f.loadout.mount(MechMountSlot.LEFT_SHOULDER).weaponDef().range;
        f.fire(new PointFireAim(20.5f, 120.5f));
        Projectile rocket = f.shots.getActiveProjectiles().get(0);
        assertNotNull(rocket.onArrival);
        float thrown = rocket.onArrival.endpointY - 20.5f;
        assertTrue(thrown > range, "a point past nominal range is not refused or cut short to it");
        assertTrue(thrown <= range * BallisticResolver.MAX_GROUND_BURST_RANGE_MULTIPLIER + 1f);
    }

    @Test
    void gunRoundsKeepTheBearingOnlyContractAtOpenGround() {
        Fixture f = new Fixture(MechWeaponComponent.SINGLE_HEAVY_CANNON, null, null);
        f.fire(new PointFireAim(20.5f, 35.5f));
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, f.shots.getActiveShots().get(0).stopKind);
        assertTrue(f.detonations.getPending().isEmpty());
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
        assertEquals(0f, mount.cycleRemaining);
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
        assertEquals(0f, mount.cycleRemaining);
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

    @Test
    void fluxAndMechanicalCycleIndependentlyGateFireWithoutSpendingAmmo() {
        Fixture f = new Fixture(MechWeaponComponent.NOSE_CHAINGUN, MechWeaponComponent.SRM_5, null);
        var arms = f.loadout.mount(MechMountSlot.ARMS);
        var srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        f.loadout.flux.commit(90f);
        f.fire(NORTH);
        assertEquals(0, f.roster.telemetry().roundsFired(f.shooter));
        assertEquals(6, srm.ammo);
        f.loadout.flux.advance(20f);
        arms.cycleRemaining = 2f;
        f.fire(NORTH);
        assertEquals(1, f.roster.telemetry().roundsFired(f.shooter), "ready SRM can fire beside cycling gun");
        assertEquals(28f - f.loadout.flux.dissipationPerSecond() * BattleSimulation.TICK_DT,
                f.loadout.flux.value(), .001f);
        assertEquals(5, srm.ammo);
        for (int i = 0; i < 10; i++) f.weapons.tick(f.shooter, NORTH, false);
        assertEquals(2, f.roster.telemetry().roundsFired(f.shooter));
        assertEquals(28f - f.loadout.flux.dissipationPerSecond() * 11 * BattleSimulation.TICK_DT,
                f.loadout.flux.value(), .001f,
                "continuation does not charge a second salvo's flux");
        f.loadout.clearQueuedFire();
        assertTrue(f.loadout.flux.value() > 0f);
    }

    @Test
    void unaimedTriggerDoesNotReserveFlux() {
        Fixture f = new Fixture(MechWeaponComponent.NOSE_CHAINGUN, null, null);
        f.fire(EAST);
        assertEquals(0f, f.loadout.flux.value());
        assertEquals(0, f.roster.telemetry().roundsFired(f.shooter));
    }

    @Test
    void leftButtonFiresThePrimaryGroupAndRightButtonTheSecondaryGroupIndependently() {
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_5);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        MechWeaponMount left = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        MechWeaponMount right = f.loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        f.weapons.tick(f.shooter, NORTH, true, false, 0);
        assertEquals(arms.component.cycleSeconds() - BattleSimulation.TICK_DT, arms.cycleRemaining, 0.00001f);
        assertEquals(0f, left.cycleRemaining, "the left button leaves the rockets alone");
        assertEquals(left.component.ammoCapacity, left.ammo);
        f.weapons.tick(f.shooter, NORTH, false, true, 0);
        assertEquals(left.component.ammoCapacity - 1, left.ammo);
        assertEquals(right.component.ammoCapacity - 1, right.ammo);
        assertEquals(arms.component.ammoCapacity, arms.ammo);
    }

    @Test
    void rightButtonAloneNeverFiresTheGunsAndBothButtonsTogetherFireEverything() {
        Fixture f = new Fixture(MechWeaponComponent.NOSE_CHAINGUN, MechWeaponComponent.SRM_5, null);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        MechWeaponMount srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        f.weapons.tick(f.shooter, NORTH, false, true, 0);
        assertEquals(0f, arms.cycleRemaining);
        assertEquals(srm.component.ammoCapacity - 1, srm.ammo);
        Fixture both = new Fixture(MechWeaponComponent.NOSE_CHAINGUN, MechWeaponComponent.SRM_5, null);
        both.weapons.tick(both.shooter, NORTH, true, true, 0);
        assertTrue(both.loadout.mount(MechMountSlot.ARMS).cycleRemaining > 0f);
        assertTrue(both.loadout.mount(MechMountSlot.LEFT_SHOULDER).cycleRemaining > 0f);
    }

    @Test
    void numberKeyRepointsTheLeftButtonOnlyAndRightStaysSecondary() {
        Fixture f = new Fixture(MechWeaponComponent.NOSE_CHAINGUN, MechWeaponComponent.SRM_5, null);
        MechWeaponMount arms = f.loadout.mount(MechMountSlot.ARMS);
        MechWeaponMount srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        f.weapons.tick(f.shooter, NORTH, true, false, LEFT_SHOULDER_KEY);
        assertEquals(0f, arms.cycleRemaining, "a number key moved the left button onto the left shoulder");
        assertEquals(srm.component.ammoCapacity - 1, srm.ammo);
    }

    @Test
    void aSingleGroupChassisLeavesTheRightButtonIdle() {
        Fixture f = new Fixture(MechWeaponComponent.NOSE_CHAINGUN, null, null);
        f.weapons.tick(f.shooter, NORTH, false, true, 0);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(0f, f.loadout.mount(MechMountSlot.ARMS).cycleRemaining);
    }

    @Test
    void releasingAGroupButtonLetsItsQueuedBurstFinishButAnotherSelectionDropsIt() {
        Fixture f = new Fixture(MechWeaponComponent.CHAINGUN, MechWeaponComponent.SRM_5, null);
        MechWeaponMount srm = f.loadout.mount(MechMountSlot.LEFT_SHOULDER);
        f.weapons.tick(f.shooter, NORTH, false, true, 0);
        assertEquals(1, srm.burstRemaining);
        f.weapons.tick(f.shooter, NORTH, false, false, 0);
        for (int i = 0; i < 12; i++) f.weapons.tick(f.shooter, NORTH, false, false, 0);
        assertEquals(srm.component.projectilesPerTrigger, f.shots.getActiveShots().size());
    }

    /** The number-key selection that points the left button at the left shoulder. */
    private static final int LEFT_SHOULDER_KEY = MechMountSlot.LEFT_SHOULDER.ordinal() + 1;

    /**
     * A bare chassis carrying {@code arms} on its centreline mount (a Sirocco frame), or, from
     * {@link #bulwark}, one gun on each arm.
     */
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
            this(MechVariant.SIROCCO, arms, left, right);
        }

        static Fixture bulwark(MechWeaponComponent armGun, MechWeaponComponent left, MechWeaponComponent right) {
            return new Fixture(MechVariant.BULWARK, armGun, left, right);
        }

        private Fixture(MechVariant variant, MechWeaponComponent arms, MechWeaponComponent left,
                        MechWeaponComponent right) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) grid.setWalkableFloor(x, y);
            shooter = roster.spawn(new EntitySpec("manual mech", Faction.MARINE, UnitType.HEAVY_MECH, 20, 20));
            loadout = new MechLoadoutComponent(variant, arms, left, right, MechRole.ARMORED_SUPPORT);
            loadout.torsoFacingDegrees = 0f;
            roster.world().attachMechLoadout(shooter, loadout);
            roster.entityWorld().setFloat(shooter, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
            detonations = new Detonations(roster, grid, new CellTopology(128, 128), null, null, null, null, null);
            weapons = new HeavyWeapons(roster, grid,
                    new BallisticResolver(grid, new DoodadService(grid), index, roster), shots,
                    detonations, new Random() { @Override public float nextFloat() { return 0f; } });
        }

        /** Both mouse buttons down: every point-fire mount is triggered. */
        void fire(PointFireAim aim) { weapons.tick(shooter, aim, true, true, 0); }
    }
}
