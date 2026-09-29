package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LiveAppearance;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Exact Marine stamina and the real swept mover on an open, bare roster. */
class MarineSprintTest {
    private static final float TICK = BattleSimulation.TICK_DT;
    private static final float AUTHORED_SPEED = 2.137f;
    private final NavigationGrid grid = new NavigationGrid(500, 20);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(500, 20), null);
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "physicalRadius" -> UnitType.MARINE.radius;
                case "squadOf", "getShelvedSquadDirective" -> null;
                case "clearPath" -> {
                    roster.movement().setPathRef((long) args[0], new int[0]);
                    roster.movement().setPathIdx((long) args[0], 0);
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> false, () -> false);

    MarineSprintTest() {
        for (int y = 0; y < 20; y++) for (int x = 0; x < 500; x++) grid.setWalkableFloor(x, y);
    }

    @Test
    void authoredSpeedAndDiagonalNormalizationSurviveTheTransientBoost() {
        long marine = spawn(10, 5);
        session.enter(marine);
        float authored = roster.movement().moveSpeed(marine);
        session.submit(intent(1, 0, false));
        session.tick();
        float normal = roster.movement().velX(marine);
        session.submit(intent(1, 0, true));
        session.tick();
        assertEquals(normal * MarineSprint.SPEED_SCALE, roster.movement().velX(marine), 1e-4f);
        assertEquals(authored, roster.movement().moveSpeed(marine), 1e-6f);
        assertTrue(session.sprintStatus().sprinting());
        assertTrue(session.sprintStatus().staminaFraction() < 1f);
        session.submit(intent(1, 1, true));
        session.tick();
        assertEquals(roster.movement().velX(marine), roster.movement().velY(marine), 1e-5f);
        assertEquals(authored * MarineSprint.SPEED_SCALE,
                Math.hypot(roster.movement().velX(marine), roster.movement().velY(marine)), 1e-4);
    }

    @Test
    void blockedMotionStopsDrainingAndStationaryHoldKeepsTheWeaponAvailable() {
        long marine = spawn(10, 5);
        for (int y = 0; y < 20; y++) grid.setWalkable(11, y, false);
        session.enter(marine);
        session.submit(intent(1, 0, true));
        for (int i = 0; i < 20; i++) session.tick();
        float atWall = roster.world().renderX(marine);
        float reserve = session.sprintStatus().staminaFraction();
        for (int i = 0; i < 50; i++) session.tick();
        assertEquals(atWall, roster.world().renderX(marine), 1e-5f);
        assertTrue(session.sprintStatus().staminaFraction() >= reserve);
        assertFalse(session.sprintStatus().sprinting());
        assertNotNull(roster.combat().pointFireAim(marine),
                "a blocked Shift hold can still fire its normal primary");
        session.submit(intent(0, 0, true));
        float stationary = session.sprintStatus().staminaFraction();
        session.tick();
        assertFalse(session.sprintStatus().sprinting());
        assertTrue(session.sprintStatus().staminaFraction() >= stationary);
    }

    @Test
    void sprintCancelsAQueuedBurstAndLowersBothSheetAndLayeredWeaponPose() {
        long marine = spawn(10, 5);
        session.enter(marine);
        roster.combat().beginPointBurst(marine, new PointFireAim(40, 5.5f));
        assertTrue(roster.combat().burstRemaining(marine) > 0);
        session.submit(new ManualIntent(1, 0, 10.5f, 15f, true, true));
        session.tick();
        assertTrue(session.sprintStatus().sprinting());
        assertEquals(0, roster.combat().burstRemaining(marine));
        assertNull(roster.combat().pointFireAim(marine));
        FacingSystem facing = new FacingSystem(roster.entityWorld(), roster.components(), roster);
        facing.tick(marine, 10.5f, 15f, session.sprintStatus().sprinting());
        int sprintPose = roster.entityWorld().getInt(marine, roster.components().LAYERED_ANIMATION,
                BattleComponents.LAYERED_WEAPON_POSE);
        assertEquals(LayeredAppearance.POSE_IDLE, sprintPose);
        assertEquals(LiveAppearance.pickFrame(LiveAppearance.Facing.NORTH, false),
                roster.entityWorld().getInt(marine, roster.components().SPRITE,
                        BattleComponents.SPRITE_INDEX));
        assertEquals(AirBody.facingToward(10.5f - roster.world().renderX(marine),
                        15f - roster.world().renderY(marine)),
                roster.entityWorld().getFloat(marine, roster.components().LAYERED_ANIMATION,
                        BattleComponents.LAYERED_FACING_DEGREES), 1e-4f);
        session.submit(intent(0, 0, true));
        session.tick();
        assertFalse(session.sprintStatus().sprinting());
        assertNotNull(roster.combat().pointFireAim(marine));
        facing.tick(marine, 40, 5.5f, false);
        int resumedPose = roster.entityWorld().getInt(marine, roster.components().LAYERED_ANIMATION,
                BattleComponents.LAYERED_WEAPON_POSE);
        assertNotEquals(LayeredAppearance.POSE_IDLE, resumedPose);
    }

    @Test
    void continuousMotionAtLargeCoordinatesExhaustsDespiteFloatRounding() {
        long marine = spawn(400, 5);
        session.enter(marine);
        session.submit(intent(1, 0, true));
        for (int i = 0; i < 190; i++) session.tick();
        assertEquals(0f, session.sprintStatus().staminaFraction(), 1e-6f);
        assertTrue(session.sprintStatus().exhausted());
        float x = roster.world().renderX(marine);
        session.tick();
        assertEquals(AUTHORED_SPEED * TICK,
                roster.world().renderX(marine) - x, 1e-4f,
                "a held exhausted Shift does not grant another fast tick");
        assertFalse(session.sprintStatus().sprinting());
    }

    @Test
    void fullRefillWhileHeldStillNeedsAReleaseToUnlock() {
        long marine = spawn(400, 5);
        session.enter(marine);
        session.submit(intent(1, 0, true));
        for (int i = 0; i < 190; i++) session.tick();
        for (int i = 0; i < 180; i++) session.tick();
        assertEquals(1f, session.sprintStatus().staminaFraction(), 1e-5f);
        assertTrue(session.sprintStatus().exhausted());
        assertFalse(session.sprintStatus().sprinting());
        session.observeSprintRelease();
        session.submit(intent(1, 0, false));
        session.tick();
        assertFalse(session.sprintStatus().exhausted());
        session.submit(intent(1, 0, true));
        session.tick();
        assertTrue(session.sprintStatus().sprinting());
    }

    @Test
    void releaseBeforeQuarterCapacityWaitsForRecoveryThenAllowsAnotherHold() {
        long marine = spawn(400, 5);
        session.enter(marine);
        session.submit(intent(1, 0, true));
        for (int i = 0; i < 190; i++) session.tick();
        session.observeSprintRelease();
        session.submit(intent(1, 0, false));
        session.tick();
        session.submit(intent(1, 0, true));
        for (int i = 0; i < 45; i++) {
            session.tick();
            assertFalse(session.sprintStatus().sprinting());
        }
        boolean restarted = false;
        for (int i = 0; i < 30; i++) {
            session.tick();
            if (session.sprintStatus().sprinting()) { restarted = true; break; }
        }
        assertTrue(restarted);
    }

    @Test
    void handbackAndBodySwitchPreserveExactMarineReserveAndRecoverInactiveBody() {
        long first = spawn(400, 5);
        long second = spawn(400, 8);
        session.enter(first);
        session.submit(intent(1, 0, true));
        for (int i = 0; i < 30; i++) session.tick();
        float spent = session.sprintStatus().staminaFraction();
        session.exit();
        assertEquals(MarineSprint.EMPTY, session.sprintStatus());
        session.enter(second);
        assertEquals(1f, session.sprintStatus().staminaFraction());
        for (int i = 0; i < 75; i++) session.tick();
        session.exit();
        session.enter(first);
        assertTrue(session.sprintStatus().staminaFraction() > spent);
        assertFalse(session.sprintStatus().sprinting());
        session.tick();
        assertTrue(session.sprintStatus().staminaFraction() <= 1f);
    }

    @Test
    void pausedInputChangesNoStaminaUntilAnotherSimulationTickRuns() {
        long marine = spawn(400, 5);
        session.enter(marine);
        session.submit(intent(1, 0, true));
        session.tick();
        float reserve = session.sprintStatus().staminaFraction();
        session.suspendInput();
        assertEquals(reserve, session.sprintStatus().staminaFraction());
        assertFalse(session.sprintStatus().sprinting());
        assertFalse(session.intent().sprint());
        session.tick();
        assertEquals(reserve, session.sprintStatus().staminaFraction(), 1e-6f,
                "the first resting tick spends the authored delay, not recovery");
    }

    @Test
    void deadBodyStateRetiresAndLegacyIntentHasNoSprint() {
        MarineSprint sprint = new MarineSprint();
        assertEquals(1f, sprint.speedScale(5L, false, TICK));
        assertEquals(MarineSprint.EMPTY, sprint.status(5L));
        assertFalse(new ManualIntent(1, 0, 10, 5, true).sprint());
        assertFalse(new ManualIntent(1, 0, 10, 5, true, true).neutralized().sprint());
        assertTrue(sprint.finishStep(5L, true, .1f, AUTHORED_SPEED, TICK));
        sprint.tickInactive(0L, TICK, id -> false);
        assertEquals(MarineSprint.EMPTY, sprint.status(5L));
    }

    private long spawn(int x, int y) {
        return roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, x, y)
                .moveSpeed(AUTHORED_SPEED)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID)));
    }

    private static ManualIntent intent(float x, float y, boolean sprint) {
        return new ManualIntent(x, y, 40, 5.5f, true, sprint);
    }
}
