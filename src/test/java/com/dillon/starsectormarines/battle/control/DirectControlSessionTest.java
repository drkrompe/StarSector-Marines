package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One session on real component data, without battle construction or a UI host. */
class DirectControlSessionTest {
    private final NavigationGrid grid = new NavigationGrid(20, 12);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(20, 12), null);
    private boolean unavailable;
    private boolean complete;
    private int pathsCleared;
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "physicalRadius" -> UnitType.MARINE.radius;
                case "squadOf" -> null;
                case "clearPath" -> {
                    long id = (long) args[0];
                    roster.movement().setPathRef(id, new int[0]);
                    roster.movement().setPathIdx(id, 0);
                    pathsCleared++;
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> unavailable, () -> complete);

    DirectControlSessionTest() {
        for (int y = 0; y < 12; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
    }

    @Test
    void onlyOneExactLivePlayerMarineOwnsControl() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        long second = spawn(Faction.MARINE, UnitType.MARINE);
        assertFalse(session.enter(0L));
        assertFalse(session.enter(spawn(Faction.DEFENDER, UnitType.MARINE)));
        assertFalse(session.enter(spawn(Faction.MARINE, UnitType.CIVILIAN)));
        assertTrue(session.enter(marine));
        assertFalse(session.enter(second));
        assertEquals(marine, session.activeUnitId());
        assertEquals(1, pathsCleared);
        session.exit();
        assertEquals(0L, session.activeUnitId());
        assertEquals(2, pathsCleared);
        assertTrue(session.enter(second));
    }

    @Test
    void oneTickUsesActualMotionAndDecrementsCooldownOnce() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        assertTrue(session.enter(marine));
        roster.combat().setCooldownTimer(marine, 1f);
        session.submit(new ManualIntent(1, 1, 18, 5, true));
        session.tick();
        assertEquals(1f - BattleSimulation.TICK_DT, roster.combat().cooldownTimer(marine), 1e-6f);
        double speed = Math.hypot(roster.movement().velX(marine), roster.movement().velY(marine));
        assertEquals(roster.movement().moveSpeed(marine), speed, 1e-4);
        assertEquals(18f, roster.combat().pointFireAim(marine).x());
        assertEquals(0L, roster.combat().fireTargetId(marine));
    }

    @Test
    void wallStopsTheWholeStepAndInputDoesNotAccumulateBehindIt() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        for (int y = 0; y < 12; y++) grid.setWalkable(6, y, false);
        session.enter(marine);
        session.submit(new ManualIntent(1, 0, 18, 5, true));
        for (int tick = 0; tick < 100; tick++) session.tick();
        assertTrue(roster.world().renderX(marine) <= 6f - UnitType.MARINE.radius);
        assertEquals(0f, roster.movement().velX(marine), 1e-5f);
        float blockedX = roster.world().renderX(marine);
        grid.setWalkableFloor(6, 5);
        session.tick();
        assertTrue(roster.world().renderX(marine) > blockedX);
        assertTrue(roster.world().renderX(marine) - blockedX
                <= roster.movement().moveSpeed(marine) * BattleSimulation.TICK_DT + 1e-5f);
    }

    @Test
    void suspendedAndReleasedInputCancelsBurstWithoutResettingCooldown() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        session.enter(marine);
        roster.combat().setCooldownTimer(marine, 0.8f);
        session.submit(new ManualIntent(1, 0, 18, 5, true));
        session.tick();
        roster.combat().beginPointBurst(marine, roster.combat().pointFireAim(marine));
        assertTrue(roster.combat().burstRemaining(marine) > 0);
        float remainingCooldown = roster.combat().cooldownTimer(marine);
        session.suspendInput();
        assertNull(roster.combat().pointFireAim(marine));
        assertEquals(0, roster.combat().burstRemaining(marine));
        assertEquals(remainingCooldown, roster.combat().cooldownTimer(marine));
        assertEquals(0f, session.intent().moveX());
        assertFalse(session.intent().firing());
        session.exit();
        assertEquals(ManualIntent.NEUTRAL, session.intent());
    }

    @Test
    void lossOfEligibilityReleasesEvenWithoutAnUpdateTick() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        unavailable = true;
        assertFalse(session.enter(marine));
        unavailable = false;
        session.enter(marine);
        unavailable = true;
        session.validate();
        assertFalse(session.active());
        unavailable = false;
        session.enter(marine);
        complete = true;
        session.validate();
        assertFalse(session.active());
    }

    @Test
    void missionKitAndFallbackOwnershipCannotBeBypassed() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        roster.role().setRole(marine, UnitRole.PLANTER);
        assertFalse(session.enter(marine));
        roster.role().setRole(marine, UnitRole.KIT_RETRIEVER);
        assertFalse(session.enter(marine));
        roster.role().setRole(marine, UnitRole.COMBATANT);
        assertTrue(session.enter(marine));
        roster.world().setFallbackTimer(marine, 1f);
        session.validate();
        assertFalse(session.active());
    }

    @Test
    void unsafeTerrainPlacementCannotBeginAStuckSession() {
        long marine = spawn(Faction.MARINE, UnitType.MARINE);
        grid.setWalkable(6, 5, false);
        roster.world().setPos(marine, 5.95f, 5.5f);
        assertFalse(session.canEnter(marine));
        assertFalse(session.enter(marine));
    }

    private long spawn(Faction faction, UnitType type) {
        EntitySpec spec = new EntitySpec("Marine", faction, type, 3, 5);
        if (type == UnitType.MARINE) spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
        return roster.spawn(spec);
    }
}
