package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live half of an integral system: it runs for its authored duration, the
 * suit goes back to what it was, and it cannot be spent again until the
 * cooldown drains ({@code integral-armor-systems.md}).
 */
class IntegralSystemActivationTest {

    private static final float DURATION = 3f;
    private static final float COOLDOWN = 22f;
    private static final float BOOST = 1.45f;
    private static final float TICK = 0.1f;

    @Test
    void aSuitWithoutASystemCarriesNoStateAndAnswersNoToEverything() {
        UnitRosterService roster = roster();
        long plain = roster.spawn(marine());
        IntegralSystemService systems = roster.integralSystems();

        assertFalse(systems.has(plain));
        assertFalse(systems.canActivate(plain));
        assertFalse(systems.activate(plain), "there is nothing to spend");
        assertEquals(1f, systems.moveSpeedMultiplier(plain), 1e-6f);
        systems.tick(plain, TICK);
    }

    @Test
    void activatingSpeedsTheSuitUpForItsDurationAndThenPutsItBack() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        IntegralSystemService systems = roster.integralSystems();
        float issued = roster.movement().moveSpeed(breacher);

        assertTrue(systems.canActivate(breacher));
        assertTrue(systems.activate(breacher));

        assertTrue(systems.isActive(breacher));
        assertEquals(issued * BOOST, roster.movement().moveSpeed(breacher), 1e-4f);
        assertEquals(BOOST, systems.moveSpeedMultiplier(breacher), 1e-6f);

        drain(systems, breacher, DURATION);

        assertFalse(systems.isActive(breacher));
        assertEquals(issued, roster.movement().moveSpeed(breacher), 1e-4f,
                "the suit is exactly the suit it was");
        assertEquals(1f, systems.moveSpeedMultiplier(breacher), 1e-6f);
    }

    @Test
    void theSystemCannotBeSpentAgainUntilItsCooldownDrains() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        IntegralSystemService systems = roster.integralSystems();

        assertTrue(systems.activate(breacher));
        assertFalse(systems.activate(breacher), "not while it is already running");

        drain(systems, breacher, DURATION);
        assertFalse(systems.canActivate(breacher), "the clock outlasts the effect");

        drain(systems, breacher, COOLDOWN - DURATION);
        assertTrue(systems.canActivate(breacher));
    }

    /**
     * Speed is recomputed from an untouched base rather than scaled in place,
     * so a second run starts from the issued speed instead of compounding the
     * first one.
     */
    @Test
    void repeatedRunsDoNotCompound() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        IntegralSystemService systems = roster.integralSystems();
        float issued = roster.movement().moveSpeed(breacher);

        for (int run = 0; run < 4; run++) {
            assertTrue(systems.activate(breacher), "run " + run);
            assertEquals(issued * BOOST, roster.movement().moveSpeed(breacher), 1e-4f,
                    "run " + run + " should start from the issued speed");
            drain(systems, breacher, COOLDOWN);
            assertEquals(issued, roster.movement().moveSpeed(breacher), 1e-4f,
                    "run " + run + " should end back at the issued speed");
        }
    }

    /** Death drops the live state with every other live-only component. */
    @Test
    void theCorpseKeepsNoSystemState() {
        UnitRosterService roster = roster();
        long breacher = roster.spawn(marine().integralSystem(breacherAssist()));
        IntegralSystemService systems = roster.integralSystems();
        systems.activate(breacher);

        roster.entityWorld().removeComponent(breacher,
                roster.components().INTEGRAL_SYSTEM);

        assertFalse(systems.has(breacher));
        assertEquals(0f, systems.activeRemaining(breacher), 1e-6f);
        assertEquals(0f, systems.cooldownRemaining(breacher), 1e-6f);
    }

    /**
     * The other half of the model from the breacher: spending a delivered
     * payload is not a stat effect, so activating it must leave movement
     * completely alone while still spending exactly one use
     * ({@code integral-armor-systems.md}).
     */
    @Test
    void aMissilePodActivationSpendsAmmoWithoutTouchingMovement() {
        UnitRosterService roster = roster();
        long gunner = roster.spawn(marine().integralSystem(missilePod()));
        IntegralSystemService systems = roster.integralSystems();
        float issued = roster.movement().moveSpeed(gunner);

        assertEquals(2, systems.ammo(gunner));
        assertTrue(systems.activate(gunner));

        assertEquals(issued, roster.movement().moveSpeed(gunner), 1e-6f,
                "a delivered payload is not a movement effect");
        assertEquals(1f, systems.moveSpeedMultiplier(gunner), 1e-6f);
        assertEquals(1, systems.ammo(gunner), "one salvo spent");
    }

    @Test
    void theBoostIsMeasuredAgainstTheIssuedSuitNotABareMarine() {
        UnitRosterService roster = roster();
        // The foundry-breaker is the slowest thing in the catalog; the assist
        // buys speed relative to that, which is the whole point of it.
        long heavy = roster.spawn(marine()
                .armor(27f, 6f, 0.80f, 1.00f)
                .integralSystem(breacherAssist()));
        IntegralSystemService systems = roster.integralSystems();
        float bare = UnitType.MARINE.moveSpeed;
        float issued = roster.movement().moveSpeed(heavy);

        assertTrue(issued < bare, "the rig is slower than an unarmoured marine");

        systems.activate(heavy);

        assertEquals(issued * BOOST, roster.movement().moveSpeed(heavy), 1e-4f);
        assertTrue(roster.movement().moveSpeed(heavy) > bare,
                "and for three seconds it outruns one");
    }

    private static void drain(IntegralSystemService systems, long id, float seconds) {
        int ticks = Math.round(seconds / TICK) + 1;
        for (int i = 0; i < ticks; i++) systems.tick(id, TICK);
    }

    /**
     * Catalog to component in one go: the authored suit's system survives the
     * loadout, the spec, and the spawn. Guards the seam easiest to break by
     * accident — a loadout field that silently stops being threaded.
     */
    @Test
    void theAuthoredFoundryBreakerDeploysCarryingItsSystem() {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        assumeTrue(catalog != null, "armour catalog is not installed in this context");
        MarineArmorCatalogDef rig = catalog.get("armor.foundry-breaker");
        assumeTrue(rig != null, "fixture assumption: the foundry-breaker is catalogued");
        assertTrue(rig.hasIntegralSystem(),
                "fixture assumption: the foundry-breaker is the pattern that carries one");

        MarineLoadout issued = MarineLoadout.fromCatalog(
                UnitRole.COMBATANT, null, null, EquipmentGrade.SERVICE,
                SoldierProfile.REGULAR, null, "marine-a", rig.appearanceFamily(),
                rig.armorCapacity(), rig.armorRating(), rig.moveSpeedMult(),
                rig.incomingAccuracyMult(), null, rig.integralSystem());
        assertNotNull(issued.integralSystem, "the issue carried the suit's capability");

        UnitRosterService roster = roster();
        EntitySpec spec = marine();
        issued.seedInto(spec);
        long id = roster.spawn(spec);
        IntegralSystemService systems = roster.integralSystems();

        assertTrue(systems.has(id));
        assertEquals(rig.integralSystem().id(), systems.spec(id).id());
        assertTrue(systems.canActivate(id), "it arrives ready");
    }

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static EntitySpec marine() {
        return new EntitySpec("breacher", Faction.MARINE, UnitType.MARINE, 5, 5);
    }

    private static IntegralSystemDef breacherAssist() {
        return new IntegralSystemDef(
                "system.test-assist", "Breaching assist", "Rams and a screen.",
                IntegralSystemEffect.BREACHER_ASSIST, SpecialResourceMode.COOLDOWN,
                DURATION, COOLDOWN, 0,
                new BreacherAssistSpec(BOOST, 0.5f, 120f), null);
    }

    private static IntegralSystemDef missilePod() {
        return new IntegralSystemDef(
                "system.test-pod", "Predictive volley", "A brace of missiles.",
                IntegralSystemEffect.MISSILE_POD, SpecialResourceMode.AMMUNITION,
                1f, 0f, 2,
                null, new MissilePodSpec("weapon.micro-missile"));
    }
}
