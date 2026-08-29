package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.combat.MitigationService;
import com.dillon.starsectormarines.battle.combat.MitigationSystem;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.SightedStandoffSpec;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The presentation half of an integral system: while one runs the wearer looks
 * different, the difference says which way the screen faces and how much of the
 * window is left, and it ends when the effect does.
 */
class SystemFxSystemTest {

    private static final float DURATION = 3f;
    private static final float COOLDOWN = 22f;
    private static final float BOOST = 1.45f;
    private static final float SOAK = 20f;
    private static final float ARC = 120f;
    private static final float TICK = 0.1f;

    @Test
    void aSuitThatCarriesNoSystemCarriesNoTreatment() {
        UnitRosterService roster = roster();
        long plain = roster.spawn(marine());
        SystemFxSystem presentation = new SystemFxSystem(roster);

        presentation.tick();

        assertFalse(roster.systemFx().has(plain), "no capability, no appearance column");
        assertFalse(roster.systemFx().isRunning(plain));
        assertTrue(presentation.activationsThisFrame().isEmpty());
    }

    @Test
    void spendingTheSystemWritesTheTreatmentTheSameTick() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        SystemFxService fx = roster.systemFx();

        assertTrue(fx.has(breacher), "the suit can show a system running");
        presentation.tick();
        assertFalse(fx.isRunning(breacher), "but nothing is running before it is spent");

        assertTrue(roster.integralSystems().activate(breacher));
        roster.mitigations().face(breacher, 42f);
        presentation.tick();

        assertTrue(fx.isRunning(breacher));
        assertEquals(1f, fx.intensity(breacher), 1e-4f, "a freshly opened window is wide open");
        assertEquals(42f, fx.arcFacingDegrees(breacher), 1e-4f);
        assertEquals(1f, fx.soakFraction(breacher), 1e-4f, "and an unspent pool is a full one");
    }

    /**
     * The reason the treatment exists at all. A screen that protects 120
     * degrees must be drawn covering 120 degrees, because the arc is a rule the
     * player has to play around and a wider drawing would teach the wrong one.
     */
    @Test
    void theDrawnArcIsTheAuthoredArc() {
        UnitRosterService roster = roster();
        long narrow = roster.spawn(marine().integralSystem(breacherAssist(90f)));
        long wide = roster.spawn(marine().integralSystem(breacherAssist(200f)));
        SystemFxSystem presentation = new SystemFxSystem(roster);

        roster.integralSystems().activate(narrow);
        roster.integralSystems().activate(wide);
        presentation.tick();

        assertEquals(90f, roster.systemFx().arcDegrees(narrow), 1e-4f);
        assertEquals(200f, roster.systemFx().arcDegrees(wide), 1e-4f);
    }

    /** And it is the arc the damage path resolves against, not a second copy of it. */
    @Test
    void theDrawnArcIsTheOneTheDamagePathResolvesAgainst() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        MitigationService screens = roster.mitigations();

        roster.integralSystems().activate(breacher);
        screens.face(breacher, -30f);
        presentation.tick();

        SystemFxService fx = roster.systemFx();
        assertEquals(screens.facingDegrees(breacher), fx.arcFacingDegrees(breacher), 1e-4f);
        assertEquals(screens.arcDegrees(breacher), fx.arcDegrees(breacher), 1e-4f);
        assertEquals(screens.soakFraction(breacher), fx.soakFraction(breacher), 1e-4f);
    }

    /** The window closing is the second thing a viewer has to be able to read. */
    @Test
    void theWindowIsDrawnClosing() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        SystemFxService fx = roster.systemFx();

        roster.integralSystems().activate(breacher);
        presentation.tick();
        float previous = fx.intensity(breacher);

        for (int i = 0; i < 10; i++) {
            roster.integralSystems().tick(breacher, TICK);
            presentation.tick();
            float now = fx.intensity(breacher);
            assertTrue(now < previous, "the window only ever closes");
            previous = now;
        }
        assertTrue(previous > 0f, "and is still open a second into a three-second run");
    }

    @Test
    void theTreatmentIsGoneOnTheTickTheEffectExpires() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        SystemFxService fx = roster.systemFx();

        roster.integralSystems().activate(breacher);
        presentation.tick();
        assertTrue(fx.isRunning(breacher));

        drain(roster, presentation, breacher, DURATION);

        assertFalse(fx.isRunning(breacher), "and nothing lingers");
        assertEquals(0f, fx.intensity(breacher), 1e-6f);
        assertEquals(0f, fx.arcDegrees(breacher), 1e-6f);
        assertEquals(0f, fx.soakFraction(breacher), 1e-6f);
        assertEquals(0f, fx.breakFlash(breacher), 1e-6f,
                "a window that simply ran out did not shatter");
    }

    /**
     * A capability, not a carrier. A running system that raises no screen still
     * shows that it is running, and simply reports no arc for the consumer to
     * draw one from.
     */
    @Test
    void aRunningSystemThatRaisesNoScreenReportsNoArc() {
        UnitRosterService roster = roster();
        long gunner = roster.spawn(marine().integralSystem(missilePod()));
        SystemFxSystem presentation = new SystemFxSystem(roster);

        assertTrue(roster.integralSystems().activate(gunner));
        presentation.tick();

        assertTrue(roster.systemFx().isRunning(gunner));
        assertEquals(0f, roster.systemFx().arcDegrees(gunner), 1e-6f);
    }

    @Test
    void oneActivationIsReportedOncePerSpend() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);

        presentation.beginFrame();
        roster.integralSystems().activate(breacher);
        presentation.tick();
        presentation.tick();
        presentation.tick();

        assertEquals(1, presentation.activationsThisFrame().size(),
                "an activation is an edge, not a state");
        assertEquals(breacher, presentation.activationsThisFrame().getLong(0));

        presentation.beginFrame();
        presentation.tick();
        assertTrue(presentation.activationsThisFrame().isEmpty(),
                "and belongs to the frame it happened in");
    }

    /**
     * The standing rule the whole placement of this data exists to enforce.
     * Corrupting every appearance column must leave the simulation exactly
     * where it was: the suit still moves at its boosted speed and the screen
     * still refuses damage over the arc it was granted.
     */
    @Test
    void theSimulationReadsNoneOfIt() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        BattleComponents components = roster.components();

        roster.integralSystems().activate(breacher);
        roster.mitigations().face(breacher, 0f);
        presentation.tick();
        float boosted = roster.movement().moveSpeed(breacher);
        // Straight ahead is inside the arc; the flank is outside it.
        float ahead = roster.mitigations().soakAgainst(breacher, 0f, 0f, 0f, 5f);
        float flank = roster.mitigations().soakAgainst(breacher, 0f, 0f, 5f, 0f);
        assertEquals(SOAK, ahead, 1e-6f, "fixture assumption: the front is covered");
        assertEquals(0f, flank, 1e-6f, "fixture assumption: the flank is not");

        roster.entityWorld().setFloat(breacher, components.SYSTEM_FX,
                BattleComponents.SYSTEM_FX_INTENSITY, 0f);
        roster.entityWorld().setFloat(breacher, components.SYSTEM_FX,
                BattleComponents.SYSTEM_FX_ARC_DEGREES, 359f);
        roster.entityWorld().setFloat(breacher, components.SYSTEM_FX,
                BattleComponents.SYSTEM_FX_ARC_FACING_DEGREES, 180f);
        roster.entityWorld().setFloat(breacher, components.SYSTEM_FX,
                BattleComponents.SYSTEM_FX_SOAK_FRACTION, 0.99f);
        roster.entityWorld().setFloat(breacher, components.SYSTEM_FX,
                BattleComponents.SYSTEM_FX_BREAK_FLASH, 1f);

        assertEquals(boosted, roster.movement().moveSpeed(breacher), 1e-6f);
        assertEquals(ahead, roster.mitigations().soakAgainst(breacher, 0f, 0f, 0f, 5f), 1e-6f);
        assertEquals(flank, roster.mitigations().soakAgainst(breacher, 0f, 0f, 5f, 0f), 1e-6f);
        assertTrue(roster.integralSystems().isActive(breacher));
    }

    /**
     * The pool is the thing the treatment now reads off, so spending it has to
     * show. A screen with a sliver left must be drawn as one.
     */
    @Test
    void theDrawnScreenFadesWithWhatIsLeftInThePool() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        SystemFxService fx = roster.systemFx();
        MitigationService screens = roster.mitigations();

        roster.integralSystems().activate(breacher);
        presentation.tick();
        assertEquals(1f, fx.soakFraction(breacher), 1e-4f);

        screens.absorb(breacher, SOAK * 0.75f);
        presentation.tick();

        assertEquals(0.25f, fx.soakFraction(breacher), 1e-3f);
        assertTrue(fx.isRunning(breacher), "a nearly-spent screen is still a screen");
    }

    /**
     * A screen ends two ways and they must not look alike. A window running out
     * is a treatment simply stopping; a pool beaten to nothing is an event, and
     * it is the outcome the soak pool exists to produce.
     */
    @Test
    void breakingIsMarkedApartFromTheWindowRunningOut() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        SystemFxService fx = roster.systemFx();

        roster.integralSystems().activate(breacher);
        presentation.tick();
        assertEquals(0f, fx.breakFlash(breacher), 1e-6f);

        roster.mitigations().absorb(breacher, SOAK);
        presentation.tick();

        assertEquals(1f, fx.breakFlash(breacher), 1e-4f, "the shatter is marked the same tick");
        assertEquals(0f, fx.arcDegrees(breacher), 1e-6f, "and the screen it marks is gone");
        assertTrue(roster.integralSystems().isActive(breacher),
                "fixture assumption: the window itself has not run out");
    }

    /** The shatter is drawn after the screen is gone, which is the only way it can be seen at all. */
    @Test
    void theShatterOutlivesTheScreenAndThenDrains() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        SystemFxSystem presentation = new SystemFxSystem(roster);
        SystemFxService fx = roster.systemFx();
        MitigationSystem sweep = new MitigationSystem(roster);

        roster.integralSystems().activate(breacher);
        roster.mitigations().absorb(breacher, SOAK);
        presentation.tick();
        float first = fx.breakFlash(breacher);

        sweep.tick(TICK);
        presentation.tick();
        float second = fx.breakFlash(breacher);
        assertTrue(second > 0f && second < first, "the mark fades rather than blinking off");

        for (int i = 0; i < 20; i++) {
            sweep.tick(TICK);
            presentation.tick();
        }
        assertEquals(0f, fx.breakFlash(breacher), 1e-6f);
    }

    private static void drain(UnitRosterService roster, SystemFxSystem presentation,
                              long id, float seconds) {
        int ticks = Math.round(seconds / TICK) + 1;
        for (int i = 0; i < ticks; i++) {
            roster.integralSystems().tick(id, TICK);
            presentation.tick();
        }
    }

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static EntitySpec marine() {
        return new EntitySpec("breacher", Faction.MARINE, UnitType.MARINE, 5, 5);
    }

    private static IntegralSystemDef breacherAssist() {
        return breacherAssist(ARC);
    }

    private static IntegralSystemDef breacherAssist(float arcDegrees) {
        return new IntegralSystemDef(
                "system.test-assist", "Breaching assist", EquipmentGrade.SERVICE, "Rams and a screen.",
                IntegralSystemEffect.BREACHER_ASSIST, SpecialResourceMode.COOLDOWN,
                DURATION, COOLDOWN, 0,
                new BreacherAssistSpec(BOOST, SOAK, arcDegrees), null, null, null,
                new ExposedUnderFireSpec(2f, 1f));
    }

    private static IntegralSystemDef missilePod() {
        return new IntegralSystemDef(
                "system.test-pod", "Predictive volley", EquipmentGrade.SERVICE, "A brace of missiles.",
                IntegralSystemEffect.MISSILE_POD, SpecialResourceMode.AMMUNITION,
                1f, 0f, 2,
                null, new MissilePodSpec("weapon.micro-missile"), null, null,
                new SightedStandoffSpec(5f));
    }
}
