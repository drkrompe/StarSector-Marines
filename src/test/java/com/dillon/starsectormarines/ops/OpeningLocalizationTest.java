package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
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

class OpeningLocalizationTest {
    @Test
    void openingCatalogReferencesExistAndFormattedPatternsParse() throws Exception {
        var source = Files.readString(Path.of("mod/data/strings/strings.json"));
        var catalog = new JSONObject(source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
        var reference = Pattern.compile("(?:\\\"@?|i18n\\.)((?:opening[A-Za-z0-9_]+|marines_management_[A-Za-z0-9_]+(?:Title|Text)))(?:\\\"|})");
        // Dynamic key prefixes are checked through their display authorities below.
        var prefixes = List.of("openingShipWord", "openingRecovery", "openingRecoveryCompact", "openingRefitPath", "openingRefitPathDescription", "openingGuide", "openingEquipmentGrade");
        for (String root : List.of("src/main/java/com/dillon/starsectormarines/ops", "src/main/java/com/dillon/starsectormarines/marine", "src/main/java/com/dillon/starsectormarines/campaign/start", "src/main/java/com/dillon/starsectormarines/intel", "src/main/java/com/fs/starfarer/api/impl/campaign/rulecmd", "mod/data/ui/components", "mod/data/marines/company_management_guide.json")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(Files::isRegularFile).toList()) {
                    var matcher = reference.matcher(Files.readString(file));
                    while (matcher.find()) {
                        String key = matcher.group(1);
                        if (!prefixes.contains(key)) assertTrue(catalog.has(key), file + " references missing " + key);
                    }
                }
            }
        }
        for (var keys = catalog.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            if (key.startsWith("opening") && catalog.getString(key).matches("(?s).*\\{[0-9]+.*")) {
                assertDoesNotThrow(() -> new MessageFormat(catalog.getString(key)), key);
            }
        }
        for (Enum<?>[] values : List.of(HullClass.values(), HullRole.values(), DeckSide.values(),
                DeckZone.values(), RoomPurpose.values())) {
            for (var value : values) assertTrue(catalog.has("openingShipWord" + value.name()), value.name());
        }
        for (var grade : EquipmentGrade.values()) assertTrue(catalog.has("openingEquipmentGrade" + grade.name()));
        for (var path : FirstRefit.Path.values()) {
            assertFalse(path.label().startsWith("opening"));
            assertFalse(path.description().startsWith("opening"));
        }
    }

    @Test
    void nestedMarkupReadsTranslatedCatalogWithoutPropPlumbing() throws Exception {
        var files = Map.of("outer-view.mlx", "<template><inner-view /></template>",
                "inner-view.mlx", "<template><button id=\"translated\">{i18n.openingRankLieutenant}</button></template>");
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> "Translated: " + text)) {
            var loader = new MarkupLoader(files::get, List.copyOf(files.keySet()));
            loader.reload();
            try (var markup = loader.build("outer-view", Map.of())) {
                assertEquals("Translated: Lieutenant", markup.requireElement("translated").text());
            }
        }
    }

    @Test
    void quantitiesApostrophesAndReorderedPlayerNamesSurviveFormatting() throws Exception {
        assertEquals("0 boats aboard", Strings.format("openingBoatCount", 0));
        assertEquals("1 boat aboard", Strings.format("openingBoatCount", 1));
        assertEquals("2 boats aboard", Strings.format("openingBoatCount", 2));
        String name = "Mira's {Second Wind}";
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> switch (text) {
            case "Reach {0}" -> "{0} — Castle''s destination";
            case "in {0}d {1}h" -> "{1} hours / {0} days remaining";
            case "{0}d {1}h" -> "{1} hours / {0} days";
            default -> text;
        })) {
            assertEquals(name + " — Castle's destination", Strings.format("openingCompanyPrologueReach", name));
            assertEquals("6 hours / 2 days remaining", FleetArmoryViewModel.formatRemaining(2.25, 0));
            assertEquals("6 hours / 2 days", FleetArmoryViewModel.formatRemainingCompact(2.25, 0));
        }
    }

    @Test
    void alreadyLoadedDefinitionsResolveTheCurrentTranslationWithoutChangingIds() throws Exception {
        CompanyManagementGuide.install(new JSONObject(Files.readString(Path.of("mod", CompanyManagementGuide.PATH))));
        var line = CompanyManagementGuide.lines().get(0);
        String rank = Rank.LIEUTENANT.displayName();
        String specialty = OfficerSpecialty.TACTICAL_COVER.label();
        String fitting = BoatFitting.STANDARD_PLATING.displayName();
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> "Translated: " + text)) {
            assertEquals("Translated: " + rank, Rank.LIEUTENANT.displayName());
            assertEquals("Translated: " + specialty, OfficerSpecialty.TACTICAL_COVER.label());
            assertEquals("Translated: " + fitting, BoatFitting.STANDARD_PLATING.displayName());
            assertEquals("standard_plating", BoatFitting.STANDARD_PLATING.id());
            assertEquals(line.id(), CompanyManagementGuide.lines().get(0).id());
            assertEquals("Translated: " + line.text(), CompanyManagementGuide.lines().get(0).text());
        }
    }
}
