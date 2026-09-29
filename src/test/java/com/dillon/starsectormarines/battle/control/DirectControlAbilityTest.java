package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.combat.MitigationSystem;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.infantry.SmokeTactics;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
import com.dillon.starsectormarines.marine.SpecialUsePose;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Exact actor, real equipment owners, and a narrow command proxy; no battle construction. */
class DirectControlAbilityTest {
    private static final SmokeGrenadeSpec SMOKE = new SmokeGrenadeSpec(8f, .8f, .65f, 1.8f, 2.25f, 12f);
    private static final SpecialEquipmentDef SMOKE_ITEM = new SpecialEquipmentDef(
            "special.test-smoke", "Smoke", "", "", null, SpecialActivation.UTILITY_SMOKE,
            null, SpecialResourceMode.AMMUNITION, 2, SpecialAiPolicy.SQUAD_SMOKE_SCREEN,
            new SpecialEquipmentPresentationDef("", null, SpecialUsePose.THROW,
                    null, null, null, null, null, null), SMOKE, null, null, null, null);
    private static final IntegralSystemDef SHIELD = new IntegralSystemDef(
            "system.test-screen", "Screen", EquipmentGrade.SERVICE, "",
            IntegralSystemEffect.BREACHER_ASSIST, SpecialResourceMode.COOLDOWN,
            3f, 22f, 0, new BreacherAssistSpec(1.45f, 20f, 120f),
            null, null, null, new ExposedUnderFireSpec(2f, 1f));

    private final NavigationGrid grid = new NavigationGrid(20, 12);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(20, 12), null);
    private final SmokeFieldService smokeFields = new SmokeFieldService(grid);
    private boolean unavailable;
    private int releases;
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "world" -> roster.world();
                case "combat" -> roster.combat();
                case "movement" -> roster.movement();
                case "squad" -> roster.squad();
                case "smokeFields" -> smokeFields;
                case "physicalRadius" -> UnitType.MARINE.radius;
                case "squadOf" -> roster.squad().hasSquad((long) args[0])
                        ? roster.getSquad(roster.squad().squadId((long) args[0])) : null;
                case "getShelvedSquadDirective" -> null;
                case "resolveUnit" -> roster.isAliveById((long) args[0]) ? args[0] : 0L;
                case "liveUnitCount" -> roster.liveCount();
                case "liveUnitAt" -> roster.denseArray()[(int) args[0]];
                case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                case "isRiding" -> false;
                case "clearPath" -> {
                    roster.movement().setPathRef((long) args[0], new int[0]);
                    roster.movement().setPathIdx((long) args[0], 0);
                    yield null;
                }
                case "throwSmoke" -> {
                    releases++;
                    SmokeTactics.release((long) args[0], (float) args[1], (float) args[2],
                            roster, smokeFields, grid);
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> unavailable, () -> false);

    DirectControlAbilityTest() {
        for (int y = 0; y < 12; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
    }

    @Test
    void smokeCommitsOnTickFreezesMoveAndPrimaryAndSpendsExactlyAtMidpoint() {
        long marine = spawn();
        assertTrue(session.enter(marine));
        session.submit(new ManualIntent(1, 0, 12, 5.5f, true, true));
        assertTrue(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(10, 5.5f)));
        assertFalse(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(4, 8)));
        assertEquals(0f, roster.world().secondaryActionTimer(marine));
        assertEquals(2, roster.world().secondaryAmmo(marine));
        roster.combat().beginPointBurst(marine, new PointFireAim(12, 5.5f));
        assertTrue(roster.combat().burstRemaining(marine) > 0);
        float startX = roster.world().renderX(marine);
        session.tick();
        assertEquals(new PointFireAim(10, 5.5f), roster.world().smokeThrowCommit(marine).aim());
        assertEquals(SMOKE.throwDuration() - BattleSimulation.TICK_DT,
                roster.world().secondaryActionTimer(marine), 1e-6f);
        assertFalse(session.canUseAbility(DirectControlAbility.SHIELD));
        assertFalse(session.canUseAbility(DirectControlAbility.SMOKE));
        session.submit(new ManualIntent(1, 0, 4, 9, true, true));
        int ticks = 0;
        while (roster.world().secondaryActionTimer(marine) > 0f) {
            assertTrue(ticks++ < 30);
            assertTrue(session.active(), "the player's own channel keeps ownership");
            assertEquals(startX, roster.world().renderX(marine));
            assertEquals(0f, roster.movement().velX(marine));
            assertEquals(1f, session.sprintStatus().staminaFraction(),
                    "the committed throw rests even under held Shift");
            assertNull(roster.combat().pointFireAim(marine));
            assertEquals(0, roster.combat().burstRemaining(marine));
            if (roster.world().secondaryActionTimer(marine) > SMOKE.throwDuration() * .5f) {
                assertEquals(2, roster.world().secondaryAmmo(marine));
                assertEquals(0, releases);
            }
            session.tick();
        }
        assertTrue(session.active());
        assertEquals(startX, roster.world().renderX(marine), "the last channel tick also holds");
        assertEquals(1, releases);
        assertEquals(1, roster.telemetry().secondaryUsed(marine));
        assertEquals(1, roster.world().secondaryAmmo(marine));
        assertEquals(0f, roster.world().secondaryCooldownTimer(marine), "smoke has no authored cooldown");
        assertNull(roster.world().smokeThrowCommit(marine));
        smokeFields.tick(SMOKE.flightSeconds());
        assertEquals(10f, smokeFields.activeFields().get(0).x(), "cursor movement did not redirect the throw");
        assertEquals(SMOKE.cloudDuration(), smokeFields.activeFields().get(0).remaining());
        session.submit(new ManualIntent(1, 0, 4, 9, true));
        session.tick();
        assertTrue(roster.world().renderX(marine) > startX);
        assertNotNull(roster.combat().pointFireAim(marine));
    }

    @Test
    void handbackPreservesAcceptedSmokePointAndOrdinaryPrepFinishesIt() {
        long marine = spawn();
        session.enter(marine);
        session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(9, 7));
        session.tick();
        session.exit();
        assertFalse(session.canEnter(marine), "a pre-existing channel cannot be taken over");
        assertNotNull(roster.world().smokeThrowCommit(marine));
        for (int i = 0; i < 30 && roster.world().secondaryActionTimer(marine) > 0f; i++) {
            assertTrue(InfantryUnitPrep.tickAimAndShortCircuit(marine, battle));
        }
        assertEquals(1, releases);
        assertEquals(1, roster.world().secondaryAmmo(marine));
        assertNull(roster.world().smokeThrowCommit(marine));
        smokeFields.tick(SMOKE.flightSeconds());
        assertEquals(9f, smokeFields.activeFields().get(0).x());
        assertEquals(7f, smokeFields.activeFields().get(0).y());
        assertTrue(session.canEnter(marine));
    }

    @Test
    void suspensionAndExitDiscardOnlyUncommittedRequests() {
        long marine = spawn();
        session.enter(marine);
        assertTrue(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(9, 7)));
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(10, 5)));
        session.suspendInput();
        session.tick();
        assertEquals(0f, roster.world().secondaryActionTimer(marine));
        assertFalse(roster.integralSystems().isActive(marine));
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(10, 5)));
        session.exit();
        session.enter(marine);
        session.tick();
        assertFalse(roster.integralSystems().isActive(marine));
        assertTrue(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(9, 7)));
        session.tick();
        float remaining = roster.world().secondaryActionTimer(marine);
        session.suspendInput();
        assertEquals(remaining, roster.world().secondaryActionTimer(marine));
        assertNotNull(roster.world().smokeThrowCommit(marine));
        session.tick();
        assertTrue(session.active());
        assertTrue(roster.world().secondaryActionTimer(marine) < remaining);
    }

    @Test
    void priorAutonomousChannelStillBlocksEntryAndUnavailableBodyStillReleases() {
        long marine = spawn();
        assertTrue(SmokeTactics.beginThrow(marine, new PointFireAim(9, 7), false, battle));
        assertFalse(session.canEnter(marine));
        for (int i = 0; i < 30; i++) InfantryUnitPrep.tickAimAndShortCircuit(marine, battle);
        assertTrue(session.enter(marine));
        session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(10, 5));
        session.tick();
        unavailable = true;
        session.validate();
        assertFalse(session.active(), "boarding/unavailability still releases ownership during the throw");
        assertNotNull(roster.world().smokeThrowCommit(marine));
        unavailable = false;
        assertFalse(session.enter(marine));
        roster.world().setHp(marine, 0f);
        assertFalse(session.canEnter(marine));
    }

    @Test
    void clampIsCommittedBeforeReleaseAndInvalidRequestsDoNothing() {
        long marine = spawn();
        session.enter(marine);
        assertFalse(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(Float.NaN, 0)));
        assertTrue(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(-100, 5.5f)));
        session.tick();
        assertEquals(new PointFireAim(.5f, 5.5f), roster.world().smokeThrowCommit(marine).aim());
        float remaining = roster.world().secondaryActionTimer(marine);
        assertFalse(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(12, 8)));
        assertEquals(remaining, roster.world().secondaryActionTimer(marine));
        assertEquals(2, roster.world().secondaryAmmo(marine));
    }

    @Test
    void unsupportedOrEmptyEquipmentCannotQueueOrMutateAChannel() {
        long plain = roster.spawn(new EntitySpec("Plain", Faction.MARINE, UnitType.MARINE, 3, 5)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID)));
        session.enter(plain);
        assertFalse(session.canUseAbility(DirectControlAbility.SHIELD));
        assertFalse(session.canUseAbility(DirectControlAbility.SMOKE));
        session.exit();
        long marine = spawn();
        roster.world().setSecondaryAmmo(marine, 0);
        session.enter(marine);
        assertFalse(session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(10, 5)));
        assertEquals(0f, roster.world().secondaryActionTimer(marine));
        assertNull(roster.world().smokeThrowCommit(marine));
    }

    @Test
    void shieldGrantUsesAuthoredResourcesAndMouseFacingWhileStrafing() {
        long marine = spawn();
        session.enter(marine);
        float baseSpeed = roster.movement().moveSpeed(marine);
        session.submit(new ManualIntent(0, 1, 12, 5.5f, false));
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(12, 5.5f)));
        assertFalse(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(2, 5)));
        assertFalse(roster.mitigations().isActive(marine));
        session.tick();
        assertEquals(SHIELD.durationSeconds(), roster.mitigations().remaining(marine));
        assertEquals(SHIELD.cooldownSeconds(), roster.integralSystems().cooldownRemaining(marine));
        assertEquals(20f, roster.mitigations().soakRemaining(marine));
        assertEquals(120f, roster.mitigations().arcDegrees(marine));
        assertEquals(baseSpeed * 1.45f, roster.movement().velY(marine), 1e-4f);
        float x = roster.world().renderX(marine);
        float y = roster.world().renderY(marine);
        assertEquals(20f, roster.mitigations().soakAgainst(marine, x, y, 12, 5.5f));
        assertEquals(0f, roster.mitigations().soakAgainst(marine, x, y, x, y + 5));
        assertFalse(session.canUseAbility(DirectControlAbility.SHIELD));
        session.submit(new ManualIntent(0, 1, 0, y, false));
        new MitigationSystem(roster).tick(BattleSimulation.TICK_DT, marine, session.pointAim());
        roster.integralSystems().tick(marine, BattleSimulation.TICK_DT);
        session.tick();
        assertEquals(20f, roster.mitigations().soakAgainst(marine, roster.world().renderX(marine),
                roster.world().renderY(marine), 0, y));
        assertEquals(3f - BattleSimulation.TICK_DT, roster.mitigations().remaining(marine), 1e-6f);
        assertEquals(22f - BattleSimulation.TICK_DT, roster.integralSystems().cooldownRemaining(marine), 1e-6f);
    }

    @Test
    void sprintScalesTheCurrentShieldSpeedAndKeepsItsMouseFacing() {
        long marine = spawn();
        assertTrue(session.enter(marine));
        float base = roster.movement().moveSpeed(marine);
        session.submit(new ManualIntent(0, 1, 12, 5.5f, true, true));
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(12, 5.5f)));
        session.tick();
        assertTrue(session.sprintStatus().sprinting());
        assertEquals(base * 1.45f, roster.movement().moveSpeed(marine), 1e-5f,
                "sprint does not rewrite the suit's authored live speed");
        assertEquals(base * 1.45f * MarineSprint.SPEED_SCALE,
                roster.movement().velY(marine), 1e-4f);
        float x = roster.world().renderX(marine);
        float y = roster.world().renderY(marine);
        assertEquals(20f, roster.mitigations().soakAgainst(marine, x, y, 12f, 5.5f),
                "the shield protects the cursor bearing while the Marine moves north");
        assertEquals(0f, roster.mitigations().soakAgainst(marine, x, y, x, y + 5f),
                "the strafe bearing is outside the directional screen");
        assertNull(roster.combat().pointFireAim(marine), "boosted translation lowers primary fire");
    }

    @Test
    void zeroDeltaShieldAimKeepsSimulationBearingAndNeedsNoPresentationRead() {
        long marine = spawn();
        session.enter(marine);
        roster.mitigations().face(marine, 73f);
        PointFireAim samePoint = new PointFireAim(roster.world().renderX(marine), roster.world().renderY(marine));
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, samePoint));
        session.tick();
        assertTrue(roster.mitigations().isActive(marine));
        assertEquals(73f, roster.mitigations().facingDegrees(marine));
    }

    @Test
    void committedThrowPoseFacesItsPointBeforeAndAfterHandback() {
        long marine = spawn();
        session.enter(marine);
        session.requestAbility(DirectControlAbility.SMOKE, new PointFireAim(9, 5.5f));
        session.tick();
        FacingSystem facing = new FacingSystem(roster.entityWorld(), roster.components(), roster);
        facing.tick(marine, 0, 5.5f);
        assertEquals(AirBody.facingToward(1, 0), layeredFacing(marine));
        assertEquals(LayeredAppearance.POSE_SMOKE_THROW, roster.entityWorld().getInt(marine,
                roster.components().LAYERED_ANIMATION, BattleComponents.LAYERED_WEAPON_POSE));
        session.exit();
        facing.tick();
        assertEquals(AirBody.facingToward(1, 0), layeredFacing(marine));
    }

    @Test
    void squadSmokeSelectorLeavesControlledCarrierAvailableOnlyToThePlayer() {
        int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = roster.getSquad(squadId);
        long manual = roster.spawn(marine().squad(squadId));
        long autonomous = roster.spawn(marine().squad(squadId));
        squad.aliveMembers = 2;
        squad.leaderId = manual;
        long threat = roster.spawn(new EntitySpec("hostile", Faction.DEFENDER, UnitType.MARINE, 12, 5));
        assertTrue(session.enter(manual));
        assertTrue(SmokeTactics.holdForAdvanceSmoke(squad, threat, 16, 5, battle));
        assertEquals(autonomous, squad.smokeCarrierId);
        assertEquals(0f, roster.world().secondaryActionTimer(manual));
        assertFalse(roster.world().smokeThrowCommit(autonomous).manual());
        assertTrue(session.canUseAbility(DirectControlAbility.SMOKE));
    }

    private float layeredFacing(long id) {
        return roster.entityWorld().getFloat(id, roster.components().LAYERED_ANIMATION,
                BattleComponents.LAYERED_FACING_DEGREES);
    }

    private long spawn() { return roster.spawn(marine()); }

    private static EntitySpec marine() {
        return new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID))
                .specialEquipment(SMOKE_ITEM, 2).integralSystem(SHIELD);
    }
}
