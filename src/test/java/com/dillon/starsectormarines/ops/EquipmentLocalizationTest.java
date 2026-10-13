package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.ui.panel.WeaponSymbols;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MechFabricationCatalog;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentLocalizationTest {
    @Test
    void translatedNamesRetainNonLatinHudAbbreviations() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> "无人机武器")) {
            assertEquals("无人机", WeaponSymbols.primaryAbbrev(WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID)));
        }
    }

    @Test
    void catalogReferencesExistAndWholeMessagesAcceptTheirArguments() throws Exception {
        String json = Files.readString(Path.of("mod/data/strings/strings.json"));
        var catalog = new JSONObject(json.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
        var references = Pattern.compile("\"@(gear[A-Za-z0-9]+)\"");
        for (String root : List.of("mod/data/marines", "mod/data/mechs", "src/main/java/com/dillon/starsectormarines")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(Files::isRegularFile).toList()) {
                    var matcher = references.matcher(Files.readString(file));
                    while (matcher.find()) assertTrue(catalog.has(matcher.group(1)), file + ": " + matcher.group(1));
                }
            }
        }
        for (var keys = catalog.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            if (key.startsWith("gear") && catalog.getString(key).matches("(?s).*\\{[0-9]+.*")) {
                assertDoesNotThrow(() -> new MessageFormat(catalog.getString(key)).format(new Object[]{2, 3, 4}), key);
            }
        }
        assertEquals("0 uses", Strings.format("gearUses", 0));
        assertEquals("1 use", Strings.format("gearUses", 1));
        assertEquals("2 uses", Strings.format("gearUses", 2));
        assertTrue(Strings.format("gearSystemBraceDetail", "15%", "40%").contains("wearer's"));
    }

    @Test
    void alreadyLoadedCatalogsAndRecipesResolveTheCurrentLanguageWithoutChangingStats() throws Exception {
        var weapon = WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID);
        var armor = MarineArmorCatalogRegistry.require("armor.foundry-breaker");
        var special = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
        var card = EquipmentTemplateCatalog.primary(weapon, EquipmentGrade.SERVICE);
        var recipe = MechFabricationCatalog.weapon(MechWeaponComponent.NOSE_CHAINGUN);
        String name = card.displayName(), description = armor.description(), recipeName = recipe.displayName();
        var before = SpecSheets.weapon(weapon, EquipmentGrade.SERVICE);
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> "Translated " + text)) {
            assertEquals("Translated " + description, armor.description());
            assertTrue(weapon.displayName().startsWith("Translated "));
            assertTrue(special.catalogDescription().startsWith("Translated "));
            assertNotEquals(name, card.displayName());
            assertEquals("Translated " + recipeName, recipe.displayName());
            assertTrue(recipe.provenance().startsWith("Translated "));
            assertTrue(IntegralSystemCopy.detail(armor).contains("Translated "));
            assertTrue(SpecSheets.mech(MechVariant.HOUND).notes().get(0).startsWith("Translated "));
            var after = SpecSheets.weapon(weapon, EquipmentGrade.SERVICE);
            assertEquals(before.stats().stream().map(SpecSheet.Stat::id).toList(), after.stats().stream().map(SpecSheet.Stat::id).toList());
            assertEquals(before.stats().stream().map(SpecSheet.Stat::value).toList(), after.stats().stream().map(SpecSheet.Stat::value).toList());
            assertEquals(before.stats().stream().map(SpecSheet.Stat::fill).toList(), after.stats().stream().map(SpecSheet.Stat::fill).toList());
            assertTrue(after.stats().stream().allMatch(stat -> stat.label().startsWith("Translated ")));
            var custom = new EquipmentTemplateCard("addon:test", "Dillon's {Rook}", card.kind(),
                    card.equipmentId(), card.grade(), card.accessTier(), card.issueCost());
            assertEquals("Dillon's {Rook}", custom.displayName());
        }
        assertEquals(name, card.displayName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void identicalTranslatedStatLabelsCannotMergeComparisonRowsOrChangeTheirIcons() throws Exception {
        var roster = new MarineRoster();
        var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
        var preview = roster.firstRefit().preview(roster, squad.id(), FirstRefit.Path.FORWARD_SCREEN);
        Map<String, Object> before = LoadoutInspection.project(roster, squad, preview, 0, ignored -> {});
        try (var strings = new HeadlessStrings(Path.of("mod"), text ->
                List.of("DMG", "RNG", "ACC", "DPS", "PEN", "ARMOR", "MOVE", "RESIST", "EVASION").contains(text) ? "Same translated label" : text)) {
            var after = LoadoutInspection.project(roster, squad, preview, 0, ignored -> {});
            var oldItems = (List<Map<String, Object>>) before.get("items");
            var newItems = (List<Map<String, Object>>) after.get("items");
            for (int i = 0; i < oldItems.size(); i++) assertEquals(oldItems.get(i).get("stats"), newItems.get(i).get("stats"));
            assertEquals(5, ((List<?>)newItems.get(0).get("stats")).size());
        }
    }
}
