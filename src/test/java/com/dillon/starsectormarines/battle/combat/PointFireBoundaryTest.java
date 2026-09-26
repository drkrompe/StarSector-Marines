package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The serial firing boundary, with a real roster and a recording shot sink. */
class PointFireBoundaryTest {
    private final NavigationGrid grid = new NavigationGrid(32, 16);
    private final UnitRosterService roster =
            new UnitRosterService(new UnitSpatialIndex(32, 16), null);
    private final CombatService combat = roster.combat();
    private final List<Emission> emitted = new ArrayList<>();
    private final FiringSystem firing = new FiringSystem(grid, roster);
    private final BattleControl control = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getSimTickIndex" -> 23;
                case "firePointShot" -> {
                    emitted.add(new Emission((long) args[0],
                            (PointFireAim) args[1], (FireStance) args[2]));
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call: " + method);
            });
    private final long shooter;

    PointFireBoundaryTest() throws Exception {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
        }
        shooter = roster.spawn(new EntitySpec("shooter", Faction.MARINE,
                UnitType.MARINE, 2, 5).primaryWeapon(PointFireFixtures.rifle(3)));
    }

    @Test
    void emptyPointBeyondRangeAndBehindWallFiresWithoutRegisteredEntity() {
        PointFireAim aim = new PointFireAim(100f, 5.5f);
        grid.setWalkable(6, 5, false);
        combat.setReflexTimer(shooter, 10f);
        combat.setPointFireIntent(shooter, aim, FireStance.MOVING);

        firing.tick(control);
        firing.tick(control);

        assertEquals(List.of(new Emission(shooter, aim, FireStance.MOVING)), emitted);
        assertNull(combat.pointFireAim(shooter), "point intent is consumed exactly once");
        assertEquals(0L, combat.fireTargetId(shooter));
        assertEquals(0L, combat.reflexTargetId(shooter));
        assertEquals(combat.attackCooldown(shooter), combat.cooldownTimer(shooter));
        assertEquals(aim, combat.burstPointAim(shooter));
        assertEquals(2, combat.burstRemaining(shooter));
        assertEquals(0L, combat.burstTargetId(shooter));
        assertEquals(FireGate.FIRED, combat.lastFireGate(shooter));
        assertEquals(23, combat.lastFireGateTick(shooter));
    }

    @Test
    void cooldownConsumesIntentWithoutReplayingItWhenReady() {
        combat.setCooldownTimer(shooter, 0.5f);
        combat.setPointFireIntent(shooter, new PointFireAim(12f, 5.5f), FireStance.STANCED);
        firing.tick(control);
        assertEquals(0.5f, combat.cooldownTimer(shooter));
        assertNull(combat.pointFireAim(shooter));
        combat.setCooldownTimer(shooter, 0f);
        firing.tick(control);
        assertTrue(emitted.isEmpty());
    }

    @Test
    void invalidAndCoincidentPointsCannotEmitOrStartBurst() {
        for (PointFireAim aim : List.of(new PointFireAim(Float.NaN, 5f),
                new PointFireAim(5f, Float.POSITIVE_INFINITY),
                new PointFireAim(roster.world().renderX(shooter),
                        roster.world().renderY(shooter)))) {
            combat.setPointFireIntent(shooter, aim, FireStance.STANCED);
            firing.tick(control);
            assertNull(combat.pointFireAim(shooter));
            assertEquals(0, combat.burstRemaining(shooter));
            assertEquals(0f, combat.cooldownTimer(shooter));
        }
        assertTrue(emitted.isEmpty());
    }

    @Test
    void unequippedUnsupportedAndZeroRangeShotsCannotEmit() {
        combat.setPrimaryWeapon(shooter, null);
        requestAndTick();
        combat.setPrimaryWeapon(shooter,
                WeaponRegistry.require(WeaponRegistry.MECH_LRM_ARTILLERY_ID));
        requestAndTick();
        combat.setPrimaryWeapon(shooter,
                WeaponRegistry.require(WeaponRegistry.MECH_SRM_POD_ID));
        requestAndTick();
        combat.setPrimaryWeapon(shooter,
                WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID));
        combat.setAttackRange(shooter, 0f);
        requestAndTick();
        assertTrue(emitted.isEmpty());
        assertEquals(0, combat.burstRemaining(shooter));
    }

    @Test
    void aNewCursorPointCannotReplaceACommittedBurst() {
        PointFireAim committed = new PointFireAim(12f, 5.5f);
        combat.beginPointBurst(shooter, committed);
        combat.setCooldownTimer(shooter, 0f);
        requestAndTick();
        assertTrue(emitted.isEmpty());
        assertEquals(committed, combat.burstPointAim(shooter));
        assertEquals(2, combat.burstRemaining(shooter));
    }

    @Test
    void entityAndPointIntentsReplaceEachOtherAndReleaseClearsBothBurstForms() {
        long target = roster.spawn(new EntitySpec("target", Faction.DEFENDER,
                UnitType.MARINE, 10, 5));
        PointFireAim aim = new PointFireAim(14f, 5.5f);
        combat.setFireIntent(shooter, target, FireStance.STANCED, false);
        combat.setPointFireIntent(shooter, aim, FireStance.STANCED);
        assertEquals(0L, combat.fireTargetId(shooter));
        combat.setFireIntent(shooter, target, FireStance.STANCED, false);
        assertNull(combat.pointFireAim(shooter));
        combat.beginPointBurst(shooter, aim);
        combat.setCooldownTimer(shooter, 0.75f);
        combat.clearPrimaryFire(shooter);
        assertNull(combat.pointFireAim(shooter));
        assertNull(combat.burstPointAim(shooter));
        assertEquals(0L, combat.fireTargetId(shooter));
        assertEquals(0L, combat.burstTargetId(shooter));
        assertEquals(0, combat.burstRemaining(shooter));
        assertEquals(0.75f, combat.cooldownTimer(shooter), "release grants no free cooldown");
    }

    @Test
    void deadShooterCannotEmitAStalePointIntent() {
        combat.setPointFireIntent(shooter, new PointFireAim(12f, 5.5f), FireStance.STANCED);
        roster.world().setHp(shooter, 0f);
        firing.tick(control);
        assertTrue(emitted.isEmpty());
        assertNull(combat.pointFireAim(shooter));
    }

    private void requestAndTick() {
        combat.setPointFireIntent(shooter, new PointFireAim(2.5f, 14f), FireStance.STANCED);
        firing.tick(control);
        assertNull(combat.pointFireAim(shooter));
    }

    private record Emission(long shooter, PointFireAim aim, FireStance stance) { }
}
