package com.dillon.starsectormarines.catalog;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.ShotFx;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmodEquipmentContributionTest {

    private static final CatalogSource CORE =
            new CatalogSource("starsector_marines", "core-fixture");
    private static final CatalogSource OC = new CatalogSource("example_oc", "oc-fixture");

    @Test
    void externalFactionCanAddPrimaryArmorRosterAndCollectibleTemplates() throws Exception {
        WeaponRegistry oldWeapons = WeaponRegistry.installed();
        MarineArmorCatalogRegistry oldArmor = MarineArmorCatalogRegistry.installed();
        SpecialEquipmentRegistry oldSpecials = SpecialEquipmentRegistry.installed();
        GroundRosterRegistry oldRosters = GroundRosterRegistry.installed();
        EquipmentTemplateCatalog oldTemplates = EquipmentTemplateCatalog.installed();
        try {
            WeaponRegistry weapons = new WeaponRegistry();
            ingestFiles(weapons, "marine-weapons.weapon.json", "turret-weapons.weapon.json");
            weapons.ingest(externalWeapon(), OC);
            WeaponRegistry.install(weapons);

            SpecialEquipmentRegistry specials = new SpecialEquipmentRegistry();
            specials.ingest(read("marine-special-equipment.equipment.json"), CORE);
            specials.ingest(externalSpecial(), OC);
            specials.validateReferences();
            SpecialEquipmentRegistry.install(specials);

            MarineArmorCatalogRegistry armor = new MarineArmorCatalogRegistry();
            armor.ingest(read("marine-armor-catalog.armor.json"), CORE);
            armor.ingest(externalArmor(), OC);
            armor.validateCompleteness();
            MarineArmorCatalogRegistry.install(armor);

            EquipmentTemplateCatalog templates = new EquipmentTemplateCatalog();
            templates.ingest(read("equipment-templates.template.json"), CORE);
            templates.ingest(externalTemplates(), OC);
            EquipmentTemplateCatalog.install(templates);

            GroundRosterRegistry rosters = new GroundRosterRegistry();
            rosters.ingest(read("faction-ground-rosters.roster.json"), CORE);
            rosters.ingest(externalRoster(), OC);
            rosters.validateCompleteness();
            GroundRosterRegistry.install(rosters);

            GroundRosterProfile profile = GroundRosterRegistry.resolve("example_oc_faction");
            MarineLoadout loadout = InfantryLoadoutRolls.defenderLoadout(profile,
                    GroundRosterProfile.ForceTier.BULK, RiskLevel.LOW, new ZeroRandom());

            assertEquals("roster.example-oc", profile.id());
            assertEquals("example.weapon-needle-rifle", loadout.primaryDef.id);
            assertNull(loadout.primary, "external definitions must not require an enum constant");
            assertEquals("ARMY_GREEN", loadout.armorFamily.name());
            assertTrue(EquipmentTemplateCatalog.contains(
                    "equipment-template:example.weapon-needle-rifle:service"));
            assertTrue(EquipmentTemplateCatalog.contains(
                    "equipment-template:example.special-signal-smoke"));
            assertEquals("example_oc", EquipmentTemplateCatalog.installed().sourceOf(
                    "equipment-template:example.weapon-needle-rifle:service").modId());

            EntitySpec entity = new EntitySpec("OC marine", Faction.DEFENDER,
                    UnitType.MARINE, 1, 1);
            loadout.seedInto(entity);
            assertEquals("example.weapon-needle-rifle", entity.primaryWeaponDef.id);
            assertEquals(loadout.primaryDef.range, entity.attackRange);

            ShotEvent shot = ShotEvent.primary(0f, 0f, 0f, 4f, 0f, 0f,
                    true, Faction.DEFENDER, 0.2f, loadout.primaryDef,
                    1f, true, BallisticResolver.StopKind.UNIT_HIT, 42L);
            assertNull(shot.marineWeapon);
            assertEquals(loadout.primaryDef, shot.primaryWeaponDef);
            assertTrue(ShotFx.of(shot).body() instanceof ShotFx.Sprite);
        } finally {
            WeaponRegistry.install(oldWeapons);
            MarineArmorCatalogRegistry.install(oldArmor);
            SpecialEquipmentRegistry.install(oldSpecials);
            GroundRosterRegistry.install(oldRosters);
            EquipmentTemplateCatalog.install(oldTemplates);
        }
    }

    @Test
    void duplicateIdsNameBothProviders() throws Exception {
        WeaponRegistry registry = new WeaponRegistry();
        registry.ingest(externalWeapon(), OC);
        JSONException failure = assertThrows(JSONException.class,
                () -> registry.ingest(externalWeapon(),
                        new CatalogSource("second_oc", "second.weapon.json")));
        assertTrue(failure.getMessage().contains("example_oc"));
        assertTrue(failure.getMessage().contains("second_oc"));
    }

    private static JSONObject externalWeapon() throws Exception {
        JSONObject source = read("marine-weapons.weapon.json");
        JSONObject weapon = new JSONObject(source.getJSONArray("weapons")
                .getJSONObject(0).toString());
        weapon.put("id", "example.weapon-needle-rifle");
        weapon.getJSONObject("catalog").put("displayName", "Needle Rifle")
                .put("modelName", "Sting").put("designation", "OCN");
        return new JSONObject().put("weapons", new JSONArray().put(weapon));
    }

    private static JSONObject externalArmor() throws Exception {
        JSONObject source = read("marine-armor-catalog.armor.json");
        JSONObject armor = new JSONObject(source.getJSONArray("armor")
                .getJSONObject(5).toString());
        armor.put("id", "example.armor-ceramic");
        armor.getJSONObject("catalog").put("displayName", "Example ceramic armor");
        return new JSONObject().put("armor", new JSONArray().put(armor));
    }

    private static JSONObject externalSpecial() throws Exception {
        JSONObject source = read("marine-special-equipment.equipment.json");
        JSONObject special = new JSONObject(source.getJSONArray("equipment")
                .getJSONObject(2).toString());
        special.put("id", "example.special-signal-smoke");
        special.getJSONObject("catalog").put("displayName", "Signal Smoke");
        return new JSONObject().put("equipment", new JSONArray().put(special));
    }

    private static JSONObject externalTemplates() throws Exception {
        JSONObject grades = new JSONObject().put("service",
                new JSONObject().put("supplies", 4).put("heavyArmaments", 1));
        JSONObject primary = new JSONObject()
                .put("equipmentId", "example.weapon-needle-rifle")
                .put("grades", grades);
        JSONObject armor = new JSONObject()
                .put("equipmentId", "example.armor-ceramic")
                .put("issueCost", new JSONObject().put("supplies", 3));
        JSONObject special = new JSONObject()
                .put("equipmentId", "example.special-signal-smoke")
                .put("issueCost", new JSONObject().put("supplies", 1));
        return new JSONObject()
                .put("primaries", new JSONArray().put(primary))
                .put("armor", new JSONArray().put(armor))
                .put("specialEquipment", new JSONArray().put(special));
    }

    private static JSONObject externalRoster() throws Exception {
        JSONObject issue = new JSONObject()
                .put("unitType", "MARINE")
                .put("primaries", weighted("example.weapon-needle-rifle"))
                .put("gradesByRisk", risks("service"))
                .put("armorByRisk", risks("example.armor-ceramic"))
                .put("specialsByRisk", risks("none"));
        JSONObject profile = new JSONObject()
                .put("id", "roster.example-oc")
                .put("factionIds", new JSONArray().put("example_oc_faction"))
                .put("bulk", issue)
                .put("elite", new JSONObject(issue.toString()))
                .put("heavySupport", new JSONArray());
        return new JSONObject().put("profiles", new JSONArray().put(profile));
    }

    private static JSONObject risks(String id) throws Exception {
        return new JSONObject()
                .put("low", weighted(id))
                .put("medium", weighted(id))
                .put("high", weighted(id));
    }

    private static JSONArray weighted(String id) throws Exception {
        return new JSONArray().put(new JSONObject().put("id", id).put("weight", 1));
    }

    private static JSONObject read(String filename) throws Exception {
        return new JSONObject(Files.readString(Path.of("mod", "data", "marines", filename)));
    }

    private static void ingestFiles(WeaponRegistry registry, String... filenames)
            throws Exception {
        for (String filename : filenames) registry.ingest(read(filename), CORE);
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
