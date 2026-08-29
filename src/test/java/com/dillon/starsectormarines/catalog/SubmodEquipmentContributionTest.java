package com.dillon.starsectormarines.catalog;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.EquipmentDoctrineDesignerViewModel;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.ShotFx;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
        FactionEquipmentCatalog oldFactionEquipment = FactionEquipmentCatalog.installed();
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
            templates.validateCompleteness();
            EquipmentTemplateCatalog.install(templates);

            FactionEquipmentCatalog factionEquipment = new FactionEquipmentCatalog();
            factionEquipment.ingest(read("faction-equipment.faction-equipment.json"), CORE);
            factionEquipment.ingest(externalFactionEquipment(), OC);
            factionEquipment.validateCompleteness();
            factionEquipment.validateReachability(
                    new MarineArmory().ownedEquipmentTemplateIds());
            FactionEquipmentCatalog.install(factionEquipment);

            GroundRosterRegistry rosters = new GroundRosterRegistry();
            rosters.ingest(read("faction-ground-rosters.roster.json"), CORE);
            rosters.ingest(externalRoster(), OC);
            rosters.validateCompleteness();
            GroundRosterRegistry.install(rosters);

            GroundRosterProfile profile = GroundRosterRegistry.resolve("example_oc_faction");
            MarineLoadout loadout = InfantryLoadoutRolls.defenderLoadout(profile,
                    GroundRosterProfile.ForceTier.BULK, RiskLevel.LOW, new ZeroRandom());

            assertEquals("roster.example-oc", profile.id());
            assertEquals("example.weapon-needle-rifle", loadout.primaryDef().id);
            assertEquals(LayeredWeaponFamily.RIFLE,
                    loadout.primaryDef().heldSpriteFamily);
            assertEquals("example.special-signal-smoke", loadout.specialDef().id());
            assertEquals("ARMY_GREEN", loadout.armorFamily.name());
            assertTrue(EquipmentTemplateCatalog.contains(
                    "equipment-template:example.weapon-needle-rifle:service"));
            assertTrue(EquipmentTemplateCatalog.contains(
                    "equipment-template:example.special-signal-smoke"));
            assertEquals("example_oc", EquipmentTemplateCatalog.installed().sourceOf(
                    "equipment-template:example.weapon-needle-rifle:service").modId());
            assertTrue(FactionEquipmentCatalog.resolve("example_oc_faction").offers(
                    "equipment-template:example.weapon-needle-rifle:service",
                    FactionEquipmentSource.MARKET));
            assertTrue(FactionEquipmentCatalog.resolve("example_oc_faction").offers(
                    "equipment-template:example.armor-ceramic",
                    FactionEquipmentSource.LICENSE));

            EntitySpec entity = new EntitySpec("OC marine", Faction.DEFENDER,
                    UnitType.MARINE, 1, 1);
            loadout.seedInto(entity);
            assertEquals("example.weapon-needle-rifle", entity.primaryWeaponDef.id);
            assertEquals("example.special-signal-smoke", entity.specialEquipment.id());
            assertEquals(loadout.primaryDef().range, entity.attackRange);

            BattleSimulation sim = openArena(12, 8);
            long carrier = sim.spawn(entity);
            assertEquals("example.special-signal-smoke",
                    sim.world().specialEquipment(carrier).id());
            sim.throwSmoke(carrier, 4.5f, 1.5f);
            assertEquals(1, sim.world().secondaryAmmo(carrier));
            assertEquals(1, sim.smokeFields().throwsInFlight().size(),
                    "the contributed utility must execute through its typed activation");

            ShotEvent shot = ShotEvent.primary(0f, 0f, 0f, 4f, 0f, 0f,
                    true, Faction.DEFENDER, 0.2f, loadout.primaryDef(),
                    1f, true, BallisticResolver.StopKind.UNIT_HIT, 42L);
            assertEquals(loadout.primaryDef(), shot.primaryWeaponDef);
            assertTrue(ShotFx.of(shot).body() instanceof ShotFx.Sprite);

            MarineRoster playerRoster = playerCanLearnAuthorIssueAndDeployContributedKit();
            // Weapons are authored per billet, so the contributed rifle and
            // special land on billet one by construction. Armour is issued by
            // role, so which billet wears the contributed suit is the sheet's
            // decision and the test follows it rather than assuming.
            int ceramicBillet = billetWearing(playerRoster, "example.armor-ceramic");
            MarineRoster persisted = roundTrip(playerRoster);
            assertEquals("example.weapon-needle-rifle",
                    persisted.activeSoldiers().get(0).primaryId());
            assertEquals("example.armor-ceramic",
                    persisted.activeSoldiers().get(ceramicBillet).armorId());
            assertEquals("example.special-signal-smoke",
                    persisted.activeSoldiers().get(0).specialEquipmentId());

            byte[] providerSave = serialize(playerRoster);
            WeaponRegistry.install(oldWeapons);
            MarineArmorCatalogRegistry.install(oldArmor);
            SpecialEquipmentRegistry.install(oldSpecials);
            EquipmentTemplateCatalog.install(oldTemplates);
            FactionEquipmentCatalog.install(oldFactionEquipment);
            MarineRoster repaired = deserialize(providerSave);
            assertEquals("weapon.field-rifle", repaired.activeSoldiers().get(0).primaryId());
            assertEquals("armor.field-fatigues",
                    repaired.activeSoldiers().get(ceramicBillet).armorId());
            assertNull(repaired.activeSoldiers().get(0).specialEquipmentId());

            WeaponRegistry.install(weapons);
            MarineArmorCatalogRegistry.install(armor);
            SpecialEquipmentRegistry.install(specials);
            EquipmentTemplateCatalog.install(templates);
            FactionEquipmentCatalog.install(factionEquipment);
        } finally {
            WeaponRegistry.install(oldWeapons);
            MarineArmorCatalogRegistry.install(oldArmor);
            SpecialEquipmentRegistry.install(oldSpecials);
            GroundRosterRegistry.install(oldRosters);
            EquipmentTemplateCatalog.install(oldTemplates);
            FactionEquipmentCatalog.install(oldFactionEquipment);
        }
    }

    private static MarineRoster playerCanLearnAuthorIssueAndDeployContributedKit() {
        String primaryTemplate = "equipment-template:example.weapon-needle-rifle:service";
        String armorTemplate = "equipment-template:example.armor-ceramic";
        String specialTemplate = "equipment-template:example.special-signal-smoke";
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        assertTrue(roster.armory().acquireEquipmentTemplate(primaryTemplate));
        assertTrue(roster.armory().acquireEquipmentTemplate(armorTemplate));
        assertTrue(roster.armory().acquireEquipmentTemplate(specialTemplate));

        EquipmentDoctrineDesignerViewModel designer = new EquipmentDoctrineDesignerViewModel(
                new Reactor(), roster, null, null);
        for (int attempt = 0; attempt < 20
                && !"example.weapon-needle-rifle".equals(
                designer.viewerBilletAt(0).primaryId()); attempt++) {
            designer.billets().get().get(0).cyclePrimary().run();
        }
        assertEquals("example.weapon-needle-rifle", designer.viewerBilletAt(0).primaryId(),
                "a learned contributed primary must appear in the doctrine picker");
        for (int attempt = 0; attempt < 20
                && !"example.special-signal-smoke".equals(
                designer.viewerBilletAt(0).specialEquipmentId()); attempt++) {
            designer.billets().get().get(0).cycleSpecial().run();
        }
        assertEquals("example.special-signal-smoke",
                designer.viewerBilletAt(0).specialEquipmentId(),
                "a learned contributed special must appear in the doctrine picker");
        designer.newDraft().run();
        designer.editName().accept("OC Needle Issue");
        designer.saveAsNew().run();
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrines().stream()
                .filter(doctrine -> "OC Needle Issue".equals(doctrine.displayName()))
                .findFirst().orElseThrow();

        // Armour is issued by plan rather than authored a billet at a time,
        // so a contributed pattern proves itself by being reachable through a
        // sheet the company can field, not by appearing in a picker.
        SquadArmorDoctrine armor = roster.armory().armorDoctrines().stream()
                .filter(doctrine -> doctrine.issueIds().contains("example.armor-ceramic"))
                .findFirst().orElseThrow();
        MarineSquad squad = roster.squads().stream()
                .filter(candidate -> !candidate.reserve()).findFirst().orElseThrow();

        var issueCost = roster.previewSquadEquipment(
                squad.id(), weapons.id(), armor.id()).issueCost();
        assertEquals(1, issueCost.heavyArmaments(),
                "the contributed primary's authored heavy-armament cost must be charged");
        assertTrue(issueCost.supplies() >= 8,
                "the contributed primary, armor, and special supply costs must be included");
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), weapons.id(), armor.id()));
        MarineSoldier issued = roster.squadMembers(squad).get(0);
        assertEquals("example.weapon-needle-rifle", issued.primaryId());
        assertTrue(issued.primaryDef() != null);
        assertEquals("example.special-signal-smoke", issued.specialEquipmentId());
        assertTrue(issued.specialEquipmentDef() != null);

        // Which billet wears the contributed suit is the sheet's business: it
        // fills each role from the best owned pattern for that job, so the test
        // asks whether the pattern reached the field rather than whether it
        // reached billet one.
        MarineSoldier wearing = roster.squadMembers(squad).stream()
                .filter(member -> "example.armor-ceramic".equals(member.armorId()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no billet wears the contributed armour: "
                                + roster.squadMembers(squad).stream()
                                .map(MarineSoldier::armorId).toList()));
        assertNull(wearing.armor(), "custom player armor must not require an enum constant");

        MarineLoadout deployed = CampaignMarineDeployment.freeze(roster, 1).seat(0);
        assertEquals("example.weapon-needle-rifle", deployed.primaryDef().id);
        assertEquals("example.special-signal-smoke", deployed.specialDef().id());
        return roster;
    }

    /** Which active billet the sheet put the named pattern on. */
    private static int billetWearing(MarineRoster roster, String armorId) {
        List<MarineSoldier> active = roster.activeSoldiers();
        for (int index = 0; index < active.size(); index++) {
            if (armorId.equals(active.get(index).armorId())) return index;
        }
        throw new AssertionError("no active billet wears " + armorId + ": "
                + active.stream().map(MarineSoldier::armorId).toList());
    }

    private static MarineRoster roundTrip(MarineRoster roster) throws Exception {
        return deserialize(serialize(roster));
    }

    private static byte[] serialize(MarineRoster roster) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(roster);
        }
        return bytes.toByteArray();
    }

    private static MarineRoster deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (MarineRoster) input.readObject();
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
        JSONObject armor = null;
        for (int index = 0; index < source.getJSONArray("armor").length(); index++) {
            JSONObject candidate = source.getJSONArray("armor").getJSONObject(index);
            if ("armor.line".equals(candidate.getString("id"))) {
                armor = new JSONObject(candidate.toString());
                break;
            }
        }
        if (armor == null) throw new IllegalStateException("Missing core armor.line fixture");
        armor.put("id", "example.armor-ceramic");
        armor.getJSONObject("catalog").put("displayName", "Example ceramic armor");
        // Tri-Tachyon has no LINE pattern of its own, so a contributed one is
        // the only candidate for that role in a corporate tactic sheet. Cloning
        // a Hegemony line suit instead left the contribution permanently
        // shadowed by the core pattern it copied, which proves nothing about
        // whether a submod can add armour ({@code role-and-access.md}).
        armor.getJSONObject("catalog").put("tradition", "tritachyon");
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
        JSONObject grades = new JSONObject()
                .put("surplus", new JSONObject().put("accessTier", "common")
                        .put("supplies", 2))
                .put("service", new JSONObject().put("accessTier", "common")
                        .put("supplies", 4)
                        .put("heavyArmaments", 1))
                .put("milspec", new JSONObject().put("accessTier", "advanced")
                        .put("supplies", 5)
                        .put("heavyArmaments", 2))
                .put("masterwork", new JSONObject().put("accessTier", "prestige")
                        .put("supplies", 7)
                        .put("heavyArmaments", 3));
        JSONObject primary = new JSONObject()
                .put("equipmentId", "example.weapon-needle-rifle")
                .put("grades", grades);
        JSONObject armor = new JSONObject()
                .put("equipmentId", "example.armor-ceramic")
                .put("accessTier", "advanced")
                .put("issueCost", new JSONObject().put("supplies", 3));
        JSONObject special = new JSONObject()
                .put("equipmentId", "example.special-signal-smoke")
                .put("accessTier", "common")
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
                .put("specialsByRisk", risks("example.special-signal-smoke"));
        JSONObject profile = new JSONObject()
                .put("id", "roster.example-oc")
                .put("factionIds", new JSONArray().put("example_oc_faction"))
                .put("bulk", issue)
                .put("elite", new JSONObject(issue.toString()))
                .put("heavySupport", new JSONArray());
        return new JSONObject().put("profiles", new JSONArray().put(profile));
    }

    private static JSONObject externalFactionEquipment() throws Exception {
        JSONArray offers = new JSONArray()
                .put(factionOffer("equipment-template:example.weapon-needle-rifle:surplus",
                        "recovery", 5))
                .put(factionOffer("equipment-template:example.weapon-needle-rifle:service",
                        "market", 8))
                .put(factionOffer("equipment-template:example.weapon-needle-rifle:milspec",
                        "license", 6))
                .put(factionOffer("equipment-template:example.weapon-needle-rifle:masterwork",
                        "patron", 2))
                .put(factionOffer("equipment-template:example.armor-ceramic",
                        "license", 6))
                .put(factionOffer("equipment-template:example.special-signal-smoke",
                        "patron", 4));
        JSONObject faction = new JSONObject()
                .put("factionId", "example_oc_faction")
                .put("offers", offers);
        return new JSONObject().put("factions", new JSONArray().put(faction));
    }

    private static JSONObject factionOffer(
            String templateId, String source, int weight) throws Exception {
        return new JSONObject()
                .put("templateId", templateId)
                .put("sources", new JSONObject().put(source, weight));
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

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
