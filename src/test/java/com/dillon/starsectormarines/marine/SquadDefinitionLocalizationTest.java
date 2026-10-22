package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Built-in squad definitions, billet roles, saved defaults and service records read through the catalog. */
class SquadDefinitionLocalizationTest {

    private static final Path MOD = Path.of("mod");

    @Test
    void everyBuiltInReferenceAndDynamicKeyIsInTheCatalog() throws Exception {
        String source = Files.readString(MOD.resolve("data/strings/strings.json"));
        JSONObject catalog = new JSONObject(source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
        List<String> keys = new ArrayList<>(BilletRoles.keys());
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            keys.add(doctrine.savedDisplayName());
            keys.add(doctrine.savedDescription());
        }
        for (ContractType type : ContractType.values()) keys.add("commendationDuty" + type.name());
        for (Status status : Status.values()) keys.add("commendationStatus" + status.name());
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            String bare = key.startsWith("@") ? key.substring(1) : key;
            if (!catalog.has(bare)) missing.add(bare);
        }
        assertTrue(missing.isEmpty(), "missing catalog keys " + missing);
        // Built-in plans and templates resolve to catalog text rather than echoing their key.
        try (var strings = new HeadlessStrings(MOD)) {
            for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
                assertTrue(!plan.displayName().startsWith("squadDoctrine")
                        && !plan.displayName().startsWith("loadout"), plan.id());
            }
            for (FireTeamTemplateCard card : FireTeamTemplateCards.starterCards()) {
                assertTrue(!card.displayName().startsWith("fireTeamTemplate"), card.id());
            }
        }
    }

    @Test
    void countsAndDatedRecordsFormatWholeMessages() throws Exception {
        try (var strings = new HeadlessStrings(MOD)) {
            assertEquals("1 Rifleman", Strings.format("squadDoctrineCount", 1, "Rifleman"));
            assertEquals("2 Rifleman", Strings.format("squadDoctrineCount", 2, "Rifleman"));
            // A long campaign's day number is not grouped like a quantity.
            assertEquals("Day 1204: Returned to active duty.",
                    Commendations.display(Commendations.entry("commendationReturnedToDuty", 1204)));
        }
    }

    @Test
    void translationReachesBuiltInsWhileNamesAndIdsStayAsSaved() throws Exception {
        try (var strings = new HeadlessStrings(MOD, text -> "«" + text + "»")) {
            SquadWeaponDoctrine dockside = SquadEquipmentDoctrines.weaponById(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
            assertTrue(dockside.displayName().startsWith("«"), dockside.displayName());
            assertEquals(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, dockside.id());
            assertTrue(FireTeamTemplateCards.starterCards().get(0).displayName().startsWith("«"));

            // A default squad name is saved in English and translated while unchanged; a typed one is not.
            MarineSquad founded = new MarineSquad(MarineSquad.defaultName(3));
            assertEquals("Squad 03", founded.savedName());
            assertEquals("«Squad 03»", founded.name());
            assertEquals("Hammer Section", new MarineSquad("Hammer Section").name());

            assertEquals("«Team Leader»", BilletRoles.label(BilletRoles.TEAM_LEADER));
            assertEquals("Door Kicker", BilletRoles.label("Door Kicker"));

            List<SquadWeaponIssue> issues = dockside.issues();
            SquadWeaponDoctrine typed = new SquadWeaponDoctrine("custom:weapons:x", "My Line",
                    "Player-authored squad weapon definition.", issues);
            assertEquals("My Line", typed.displayName());
            assertTrue(typed.description().startsWith("«"), "legacy saved description is recognised");

            FirstRefit.Path path = FirstRefit.Path.STEADY_LINE;
            SquadWeaponDoctrine wayline = new SquadWeaponDoctrine(path.weapons,
                    FirstRefit.savedDoctrineName(path), "", issues);
            assertTrue(wayline.displayName().startsWith("«"), wayline.displayName());
            SquadWeaponDoctrine renamedPlan = new SquadWeaponDoctrine(path.weapons, "Our Rifles", "", issues);
            assertEquals("Our Rifles", renamedPlan.displayName());
            SquadWeaponDoctrine migrated = new SquadWeaponDoctrine("migrated:weapons:s1",
                    SquadWeaponDoctrine.migratedName("Hammer Section"), "", issues);
            assertTrue(migrated.displayName().contains("Hammer Section") && migrated.displayName().startsWith("«"));

            // Service records: a new entry is formatted when read, with saved names verbatim.
            String entry = Commendations.entry("commendationPostedTo", 40, "Port | Tse \\ North");
            assertTrue(!entry.contains("Posted to") && !entry.contains("«"), "the save holds a marker, not English");
            assertEquals("«Day 40: Posted to Port | Tse \\ North.»", Commendations.display(entry));
            String ranked = Commendations.entry("commendationCadrePromoted", 7,
                    Commendations.ref(Rank.values()[1].displayKey()));
            assertTrue(Commendations.display(ranked).contains(Strings.get(Rank.values()[1].displayKey())));
            // Prose written by older saves is shown exactly as saved.
            String legacy = "Day 12: Posted to jangala.";
            assertEquals(legacy, Commendations.display(legacy));
        }
    }
}
