package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.weapon.MountClass;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog-shape cover for the close-contact activation. The two authored
 * families are read back from the registry; nothing here restates their
 * authored magnitudes.
 */
class CloseContactEquipmentTest {

    @Test
    void bothFamiliesShareOneActivationAndDifferByPolicy() {
        SpecialEquipmentDef cutter = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.BREACHING_CUTTER_ID);
        SpecialEquipmentDef blade = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.VIBRO_BLADE_ID);

        assertSame(SpecialActivation.CLOSE_CONTACT, cutter.activation());
        assertSame(SpecialActivation.CLOSE_CONTACT, blade.activation());
        assertSame(SpecialAiPolicy.CONTACT_BREACH_CHANNEL, cutter.aiPolicy());
        assertSame(SpecialAiPolicy.CONTACT_REACTION_STRIKE, blade.aiPolicy());
        assertTrue(cutter.isCloseContactWeapon());
        assertTrue(blade.isCloseContactWeapon());
    }

    @Test
    void thePayloadBelongsToTheReferencedWeaponDefinition() {
        for (SpecialEquipmentDef tool : new SpecialEquipmentDef[]{
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.BREACHING_CUTTER_ID),
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.VIBRO_BLADE_ID)}) {
            assertNotNull(tool.weaponDef(), tool.id());
            assertSame(MountClass.MARINE_SECONDARY, tool.weaponDef().mount, tool.id());
            assertEquals(tool.weaponDef().damage, tool.damage(), 0f, tool.id());
            assertEquals(tool.weaponDef().penetration, tool.penetration(), 0f, tool.id());
            assertEquals(0f, tool.aoeRadius(), 0f, tool.id() + " has no area blast");
            assertSame(SpecialResourceMode.COOLDOWN, tool.resourceMode(), tool.id());
            assertEquals(0, tool.startingAmmo(), tool.id());
            assertEquals(tool.closeContactSpec().channelSeconds(), tool.aimDuration(), 0f,
                    tool.id() + " channels for its authored commitment, not a weapon aim time");
        }
    }

    @Test
    void onlyTheBreachingChannelCarriesWallDamage() {
        assertTrue(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.BREACHING_CUTTER_ID)
                .wallDamage() > 0);
        assertEquals(0, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.VIBRO_BLADE_ID)
                .wallDamage(), "a blade is anti-personnel equipment, not a wall opener");
    }

    @Test
    void closeContactRejectsAPolicyFromAnotherActivation() throws Exception {
        JSONObject blade = builtInEntry(SpecialEquipmentRegistry.VIBRO_BLADE_ID);
        blade.getJSONObject("ai").put("policy", "contact-demolition");
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(blade));
    }

    @Test
    void closeContactRejectsAmmunitionAndAMissingWeaponReference() throws Exception {
        JSONObject ammo = builtInEntry(SpecialEquipmentRegistry.BREACHING_CUTTER_ID);
        ammo.getJSONObject("resource").put("mode", "ammunition").put("startingAmmo", 3);
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(ammo));

        JSONObject unarmed = builtInEntry(SpecialEquipmentRegistry.BREACHING_CUTTER_ID);
        unarmed.getJSONObject("activation").remove("weaponId");
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(unarmed));
    }

    @Test
    void closeContactRequiresPositiveReachAndChannel() throws Exception {
        for (String key : new String[]{"contactRange", "channelSeconds"}) {
            JSONObject entry = builtInEntry(SpecialEquipmentRegistry.VIBRO_BLADE_ID);
            entry.getJSONObject("activation").put(key, 0.0);
            assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(entry), key);
        }
    }

    @Test
    void everyOtherActivationLeavesTheCloseContactSpecUnset() {
        for (SpecialEquipmentDef def : SpecialEquipmentRegistry.installed().all()) {
            if (def.activation() == SpecialActivation.CLOSE_CONTACT) {
                assertNotNull(def.closeContactSpec(), def.id());
            } else {
                assertNull(def.closeContactSpec(), def.id());
            }
        }
    }

    @Test
    void bothFamiliesAreCollectibleAndReachable() {
        for (String id : new String[]{SpecialEquipmentRegistry.BREACHING_CUTTER_ID,
                SpecialEquipmentRegistry.VIBRO_BLADE_ID}) {
            EquipmentTemplateCard card = EquipmentTemplateCatalog.special(id);
            assertNotNull(card, id);
            assertNotNull(card.accessTier(), id);
            assertTrue(FactionEquipmentCatalog.installed().entries().stream()
                            .flatMap(pool -> pool.offers().stream())
                            .anyMatch(offer -> offer.template().id().equals(card.id())),
                    id + " must be offered through at least one faction source");
        }
    }

    private static JSONObject builtInEntry(String id) throws Exception {
        JSONObject root = new JSONObject(Files.readString(
                Path.of("mod", SpecialEquipmentRegistry.BUILTIN_CATALOGS.get(0))));
        JSONArray equipment = root.getJSONArray("equipment");
        for (int index = 0; index < equipment.length(); index++) {
            JSONObject entry = equipment.getJSONObject(index);
            if (id.equals(entry.getString("id"))) return entry;
        }
        throw new IllegalStateException("No built-in special-equipment entry '" + id + "'");
    }
}
