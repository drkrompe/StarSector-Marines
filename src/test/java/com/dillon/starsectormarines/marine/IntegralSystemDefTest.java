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
