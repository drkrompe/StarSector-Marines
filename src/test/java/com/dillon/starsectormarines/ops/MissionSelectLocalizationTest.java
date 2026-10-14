package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MissionSelectLocalizationTest {
    @Test void screenAndMarkupReferencesExistAndVariablePatternsParse() throws Exception {
        var source = Files.readString(Path.of("mod/data/strings/strings.json"));
        var catalog = new JSONObject(source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
        var reference = Pattern.compile("missionSelect[A-Za-z0-9_]+");
        for (String path : List.of("src/main/java/com/dillon/starsectormarines/ops/MissionSelectScreen.java",
                "mod/data/ui/components/missions/mission-select.mlx")) {
            var matches = reference.matcher(Files.readString(Path.of(path)));
            while (matches.find()) {
                String key = matches.group();
                if (!List.of("missionSelectTier", "missionSelectOpposition").contains(key))
                    assertTrue(catalog.has(key), key);
            }
        }
        for (var keys = catalog.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            if (key.startsWith("missionSelect") && catalog.getString(key).contains("{0"))
                assertDoesNotThrow(() -> new MessageFormat(catalog.getString(key)), key);
        }
        for (var tier : OperationTier.values()) assertTrue(catalog.has("missionSelectTier" + tier.name()));
        for (var risk : RiskLevel.values()) assertTrue(catalog.has("missionSelectOpposition" + risk.name()));
    }

    @Test void translatedRowsPreserveMissionIdentityAndFormatCompleteQuantityMessages() throws Exception {
        String name = "Captain's {New Work}";
        var mission = Mission.builder().id("contract-test").name(name).type(MissionType.ASSAULT)
                .risk(RiskLevel.MEDIUM).tier(OperationTier.FIRST_CONTRACT).payout(1000).build();
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> "Translated: " + text)) {
            var rows = MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { });
            assertEquals(name, rows.get(0).title());
            assertTrue(rows.get(0).meta().startsWith("Translated:"));
            assertTrue(rows.get(0).payout().startsWith("Translated:"));
            var props = new LinkedHashMap<String, Object>();
            MissionSelectScreen.putDetail(props, mission, () -> { }, () -> { });
            assertEquals(name, props.get("detailTitle"));
            assertTrue(((String) props.get("detailMeta")).startsWith("Translated:"));
            assertTrue(((String) props.get("briefLabel")).startsWith("Translated:"));
        }
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            assertEquals("1 sortie", Strings.format("missionSelectSorties", 1));
            assertEquals("2 sorties", Strings.format("missionSelectSorties", 2));
            assertEquals("First Contract  " + (char) 183 + "  1 squad recommended",
                    Strings.format("missionSelectRecommendation", "First Contract", 1));
        }
    }

    @Test void catalogFormattingSupportsReorderedArgumentsAndLiteralPlayerNames() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> text.equals("Meet {0}")
                ? "{0} / rendezvous" : text)) {
            assertEquals("Mira's {Second Wind} / rendezvous", Strings.format("missionSelectMeet", "Mira's {Second Wind}"));
        }
    }
}
