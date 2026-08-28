package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertTrue(failure.getMessage().contains("breacher-assist")
                        && failure.getMessage().contains("missile-pod"),
                "the refusal should list the effects that do exist: " + failure.getMessage());
    }

    @Test
    void aMissilePodParsesItsAuthoredCapability() throws JSONException {
        IntegralSystemDef system = IntegralSystemDef.parse(missilePod(), "armor.test");

        assertEquals("system.test-pod", system.id());
        assertEquals(IntegralSystemEffect.MISSILE_POD, system.effect());
        assertEquals(SpecialResourceMode.AMMUNITION, system.resourceMode());
        assertTrue(system.usesAmmunition(), "a missile pod runs out, it does not wait");
        assertEquals(2, system.startingAmmo());
        assertNull(system.breacherAssist(), "a pod is not also a breacher");

        MissilePodSpec pod = system.missilePod();
        assertNotNull(pod);
        assertEquals("weapon.micro-missile", pod.weaponId());
        assertSame(WeaponRegistry.require("weapon.micro-missile"), pod.weaponDef(),
                "the pod delegates to the one authoritative weapon definition"
                        + " rather than duplicating its numbers");
    }

    /**
     * The validation the breacher assist gets against cooldown
     * ({@link #aBreacherAssistParsesItsAuthoredCapability}) mirrored for the
     * pod's own resource mode.
     */
    @Test
    void aMissilePodMustBeAmmunitionGated() throws JSONException {
        JSONObject cooldownGated = missilePod().put("resource", "cooldown")
                .put("cooldownSeconds", 20.0);
        cooldownGated.remove("startingAmmo");

        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(cooldownGated, "armor.test"));
        assertTrue(failure.getMessage().contains("ammunition-gated"), failure.getMessage());
    }

    @Test
    void aMissilePodMustNameItsWeapon() throws JSONException {
        JSONObject noWeapon = missilePod();
        noWeapon.remove("weaponId");

        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(noWeapon, "armor.test"));
        assertTrue(failure.getMessage().contains("weaponId"), failure.getMessage());
    }

    @Test
    void aMissilePodRefusesAnUnknownWeapon() throws JSONException {
        JSONObject json = missilePod().put("weaponId", "weapon.does-not-exist");

        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains("weapon.does-not-exist"), failure.getMessage());
    }

    /**
     * The rule this story is most at risk of breaking, pinned at parse time:
     * a shoulder pod is infantry ordnance, never a mech-mount weapon.
     */
    @Test
    void aMissilePodRefusesAMechMountWeapon() throws JSONException {
        JSONObject json = missilePod().put("weaponId", WeaponRegistry.MECH_LRM_ARTILLERY_ID);

        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains("marine-secondary"), failure.getMessage());
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

    private static JSONObject missilePod() throws JSONException {
        return new JSONObject()
                .put("id", "system.test-pod")
                .put("displayName", "Predictive volley")
                .put("description", "A brace of smart micro-missiles.")
                .put("effect", "missile-pod")
                .put("resource", "ammunition")
                .put("durationSeconds", 1.0)
                .put("startingAmmo", 2)
                .put("weaponId", "weapon.micro-missile");
    }
}
