package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView.KindRow;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView.Mission;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.SquadMuster;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleUiLocalizationTest {
    private static final String SRC = "src/main/java/com/dillon/starsectormarines/";
    private static final List<String> JAVA = List.of(
            "ops/BattleHudOverlayModel.java", "ops/BattleSquadOverlayModel.java", "ops/BattleAirOverlayModel.java",
            "ops/battleview/SquadMuster.java", "ops/battleview/FortMarkers.java",
            "ops/battleview/FortressBatteryMarkers.java", "ops/battleview/HostileShipMarkers.java",
            "ops/battleview/ColdAccountMarkers.java", "ops/battleview/BattlefieldMarkerPresentation.java",
            "battle/ui/comms/WaylineHoundCommsPresenter.java", "battle/ui/comms/CounterattackCommsPresenter.java",
            "battle/ui/comms/FieldSalvageCommsPresenter.java", "battle/ui/comms/HostileShipCommsPresenter.java",
            "battle/ui/panel/BattleCommsPanel.java",
            "battle/ui/panel/CommandPowerTargetingPanel.java", "battle/ui/panel/OrderIntentCursorPanel.java",
            "battle/ui/panel/SquadOrderTargetingPanel.java");
    private static final Path MLX_DIR = Path.of("mod/data/ui/components/battle");

    private static JSONObject catalog() throws Exception {
        String source = Files.readString(Path.of("mod/data/strings/strings.json"));
        return new JSONObject(source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
    }

    @Test void markupAndJavaReferencesExistAndPatternsParse() throws Exception {
        JSONObject catalog = catalog();
        Pattern markup = Pattern.compile("\\{i18n\\.([A-Za-z0-9_]+)}");
        try (Stream<Path> files = Files.list(MLX_DIR)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".mlx")).toList()) {
                Matcher matches = markup.matcher(Files.readString(file));
                while (matches.find()) assertTrue(catalog.has(matches.group(1)), file + " " + matches.group(1));
            }
        }
        Pattern call = Pattern.compile("Strings\\.(?:get|format|join)\\(\"([A-Za-z0-9_]+)\"");
        for (String path : JAVA) {
            Matcher matches = call.matcher(Files.readString(Path.of(SRC + path)));
            while (matches.find()) assertTrue(catalog.has(matches.group(1)), path + " " + matches.group(1));
        }
        for (FortAspect aspect : FortAspect.values()) assertTrue(catalog.has("battleFortAspect" + aspect.name()));
        for (Mission mission : Mission.values()) assertTrue(catalog.has("battleAirMission" + mission.name()));
        for (var keys = catalog.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            if (key.startsWith("battleSquad") || key.startsWith("battleComms") || key.startsWith("battleAir")
                    || key.startsWith("battleFort") || key.startsWith("battleMarker") || key.startsWith("battleMuster"))
                assertDoesNotThrow(() -> new MessageFormat(catalog.getString(key)), key);
        }
    }

    @Test void quantityPatternsFormatSingularAndPlural() throws Exception {
        try (HeadlessStrings strings = new HeadlessStrings(Path.of("mod"))) {
            assertEquals("1 squad / current assignments", Strings.format("battleHudSquadsSummary", 1));
            assertEquals("2 squads / current assignments", Strings.format("battleHudSquadsSummary", 2));
            assertTrue(Strings.format("battleSquadTipRecord", 3, 5, 1, 40, "HOLDING").contains("1 kill ·"));
            assertTrue(Strings.format("battleSquadTipRecord", 3, 5, 2, 40, "HOLDING").contains("2 kills"));
            assertTrue(Strings.format("battleSquadTipRecord", 0, 0, 0, 0, "HOLDING").contains("0 kills"));
        }
    }

    @Test void translationReachesHudOutputWhileNamesPassThrough() throws Exception {
        try (HeadlessStrings strings = new HeadlessStrings(Path.of("mod"), text -> "«" + text + "»")) {
            AirPoolView view = new AirPoolView(
                    List.of(new KindRow(AirPoolKind.FIGHTER, 2, 0, 2, 0, -1f)), List.of(), 0, 0);
            assertEquals("«READY»", BattleAirOverlayModel.project(view).status());
            assertEquals("«LOST 1»", BattleAirOverlayModel.lossLine(1, 0));
            var billet = new SquadMuster.Billet(0, "m1", "Rook {Sarge}", "SGT", SquadMuster.State.READY, -1f, true, false);
            String hover = billet.hoverText();
            assertTrue(hover.contains("Rook {Sarge}"), hover);
            assertTrue(hover.contains("«Ready»") && hover.contains("«Squad NCO»"), hover);
        }
    }
}
