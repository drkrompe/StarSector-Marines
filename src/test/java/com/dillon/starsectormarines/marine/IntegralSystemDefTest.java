package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The standing rules from {@code integral-armor-systems.md}, pinned where an
 * author will actually trip over them: at parse time, loudly.
 */
class IntegralSystemDefTest {

    @Test
    void aBreacherAssistParsesItsAuthoredCapability() throws JSONException {
        IntegralSystemDef system = IntegralSystemDef.parse(breacherAssist(), "armor.test");

        assertEquals("system.test-assist", system.id());
        assertEquals(IntegralSystemEffect.BREACHER_ASSIST, system.effect());
        assertEquals(SpecialResourceMode.COOLDOWN, system.resourceMode());
        assertEquals(3f, system.durationSeconds(), 1e-6f);
        assertEquals(22f, system.cooldownSeconds(), 1e-6f);
        assertFalse(system.usesAmmunition(), "a cooldown system waits, it does not run out");

        BreacherAssistSpec spec = system.breacherAssist();
        assertNotNull(spec);
        assertEquals(1.45f, spec.moveSpeedMult(), 1e-6f);
        assertEquals(0.5f, spec.frontalResistance(), 1e-6f);
        assertEquals(120f, spec.shieldedArcDegrees(), 1e-6f);
    }

    /**
     * The rule the whole concept exists for. An author reaching for durability
     * gets told why rather than silently widening the arc's armour side.
     */
    @Test
    void durabilityIsRejectedByName() throws JSONException {
        for (String key : List.of("armorCapacity", "armorCapacityBonus", "armorRating",
                "bonusHp", "maxHp", "hpBonus", "extraArmor")) {
            JSONObject json = breacherAssist().put(key, 12.0);
            JSONException failure = assertThrows(JSONException.class,
                    () -> IntegralSystemDef.parse(json, "armor.test"),
                    "'" + key + "' should be refused");
            assertTrue(failure.getMessage().contains("behaviour"),
                    "the refusal should say what an integral system must be, got: "
                            + failure.getMessage());
        }
    }

    @Test
    void aCooldownThatNeverExpiresIsNotATemporaryEffect() throws JSONException {
        JSONObject permanent = breacherAssist()
                .put("durationSeconds", 30.0)
                .put("cooldownSeconds", 20.0);

        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(permanent, "armor.test"));
        assertTrue(failure.getMessage().contains("permanently available"),
                failure.getMessage());
    }

    @Test
    void aBreacherAssistMustSpeedTheSuitUpStayPenetrableAndLeaveAFlank() throws JSONException {
        assertThrows(JSONException.class, () -> IntegralSystemDef.parse(
                breacherAssist().put("moveSpeedMult", 0.9), "armor.test"),
                "a movement system that slows you down is not the capability");
        assertThrows(JSONException.class, () -> IntegralSystemDef.parse(
                breacherAssist().put("frontalResistance", 1.0), "armor.test"),
                "total immunity is an off-switch, not mitigation");
        assertThrows(JSONException.class, () -> IntegralSystemDef.parse(
                breacherAssist().put("shieldedArcDegrees", 360.0), "armor.test"),
                "an all-round screen removes the flank the squad is there to cover");
    }

    @Test
    void anUnknownEffectNamesWhatIsAvailable() throws JSONException {
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(
                        breacherAssist().put("effect", "invisibility"), "armor.test"));
        assertTrue(failure.getMessage().contains("breacher-assist"),
                "the refusal should list the effects that do exist: " + failure.getMessage());
    }

    /**
     * A system is a reason to want one particular suit. If the catalog ever
     * grows to where most patterns carry one, that reason is gone.
     */
    @Test
    void theCatalogKeepsIntegralSystemsRare() {
        MarineArmorCatalogRegistry registry = MarineArmorCatalogRegistry.installed();
        if (registry == null) return;

        List<String> carrying = new ArrayList<>();
        for (MarineArmorCatalogDef armor : registry.all()) {
            if (armor.hasIntegralSystem()) carrying.add(armor.id());
        }
        assertTrue(carrying.size() * 2 < registry.size(),
                "most patterns should carry no integral system, but " + carrying
                        + " of " + registry.size() + " do");
    }

    /**
     * The claim is the role, not the tier. Breaching is what an ASSAULT pattern
     * is <em>for</em> — the XIV's own catalog copy calls it a breach pattern —
     * so every assault suit expresses it and nothing else does. A future tier-IV
     * scout or line pattern would still carry nothing, which is what keeps this
     * a role marker rather than a tax on the top of the ladder
     * ({@code integral-system-slate.md}).
     */
    @Test
    void breachingIsTheAssaultRolesCapabilityAndNobodyElses() {
        MarineArmorCatalogRegistry registry = MarineArmorCatalogRegistry.installed();
        if (registry == null) return;

        for (MarineArmorCatalogDef armor : registry.all()) {
            boolean assault = "ASSAULT".equals(armor.unitClass());
            boolean breaches = armor.hasIntegralSystem()
                    && armor.integralSystem().effect() == IntegralSystemEffect.BREACHER_ASSIST;
            assertEquals(assault, breaches,
                    armor.id() + " is unitClass " + armor.unitClass()
                            + " and " + (breaches ? "does" : "does not") + " breach");
        }
    }

    /**
     * Six suits carrying the same effect are six suits only if they behave
     * differently. Identical numbers under different names is the palette-swap
     * failure {@code equipment-lore-catalog.md} exists to prevent, and it is the
     * easy mistake to make when adding the seventh.
     */
    @Test
    void everyFactionsTakeOnBreachingIsActuallyADifferentSuit() {
        MarineArmorCatalogRegistry registry = MarineArmorCatalogRegistry.installed();
        if (registry == null) return;

        List<String> ids = new ArrayList<>();
        List<String> shapes = new ArrayList<>();
        for (MarineArmorCatalogDef armor : registry.all()) {
            if (!armor.hasIntegralSystem()) continue;
            IntegralSystemDef system = armor.integralSystem();
            assertFalse(ids.contains(system.id()),
                    "two patterns share the system id " + system.id());
            ids.add(system.id());

            BreacherAssistSpec spec = system.breacherAssist();
            String shape = system.durationSeconds() + "/" + system.cooldownSeconds()
                    + "/" + spec.moveSpeedMult() + "/" + spec.frontalResistance()
                    + "/" + spec.shieldedArcDegrees();
            assertFalse(shapes.contains(shape),
                    armor.id() + " is a renamed copy of another pattern's system: " + shape);
            shapes.add(shape);
        }
        assertTrue(ids.size() > 1, "the family should have more than one member");
    }

    @Test
    void anArmourPatternWithoutOneSaysSoRatherThanFaking() throws JSONException {
        JSONObject battle = new JSONObject()
                .put("appearanceFamily", "CHARCOAL")
                .put("armorCapacity", 9.0)
                .put("armorRating", 8.0)
                .put("moveSpeedMult", 0.96)
                .put("incomingAccuracyMult", 0.96);
        JSONObject entry = new JSONObject()
                .put("id", "armor.plain")
                .put("catalog", new JSONObject()
                        .put("displayName", "Plain suit")
                        .put("unitClass", "LINE")
                        .put("description", "Nothing but numbers.")
                        .put("tier", 3)
                        .put("iconPath", "graphics/ui/armory/armor-tier-3-combat.png"))
                .put("battle", battle);

        MarineArmorCatalogDef def = MarineArmorCatalogDef.parse(entry);

        assertFalse(def.hasIntegralSystem());
        assertNull(def.integralSystem());
    }

    private static JSONObject breacherAssist() throws JSONException {
        return new JSONObject()
                .put("id", "system.test-assist")
                .put("displayName", "Breaching assist")
                .put("description", "Rams and a screen on one trigger.")
                .put("effect", "breacher-assist")
                .put("resource", "cooldown")
                .put("durationSeconds", 3.0)
                .put("cooldownSeconds", 22.0)
                .put("moveSpeedMult", 1.45)
                .put("frontalResistance", 0.5)
                .put("shieldedArcDegrees", 120.0);
    }
}
