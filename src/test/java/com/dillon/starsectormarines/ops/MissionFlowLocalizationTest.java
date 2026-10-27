package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveFamilies;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Briefing, muster, stationing, results, loot and the objective picture read their copy from the catalog. */
class MissionFlowLocalizationTest {
    private static final String JAVA = "src/main/java/com/dillon/starsectormarines/";
    private static final String MLX = "mod/data/ui/components/missions/";
    private static final List<String> SOURCES = List.of(
            JAVA + "ops/BriefingScreen.java", JAVA + "ops/BriefingViewModel.java",
            JAVA + "ops/SquadDeploymentScreen.java", JAVA + "ops/StationingScreen.java",
            JAVA + "ops/ResultsScreen.java", JAVA + "ops/LootScreen.java",
            JAVA + "ops/OfficerMissionResult.java", JAVA + "ops/FieldPresencePolicy.java",
            JAVA + "ops/detachment/MissionForceEnvelope.java", JAVA + "ops/detachment/TaskForce.java",
            JAVA + "ops/detachment/AgreusConstructionSupport.java",
            JAVA + "battle/command/objective/MissionObjectiveFamilies.java",
            JAVA + "battle/command/objective/MissionObjectiveProjection.java",
            MLX + "mission-briefing.mlx", MLX + "squad-deployment.mlx", MLX + "stationing-screen.mlx",
            MLX + "mission-results.mlx", MLX + "mission-loot.mlx", MLX + "polity-doctrine-screen.mlx");
    /** Key literals on this surface, whether looked up directly, chosen by a ternary or named in markup. */
    private static final Pattern KEY = Pattern.compile(
            "(?:(?<!props\\.put\\(|props\\.get\\(|props\\.getOrDefault\\()\"|\\{i18n\\.)((?:objective|briefing|deploy|stationingScreen|resultsScreen|lootScreen|polityDoctrine)[A-Z][A-Za-z0-9_]*)(?:\"(?!\\s*\\+)|\\})");
    /** Prefixes completed by an enum constant; each is checked against its enum below. */
    private static final Set<String> PREFIXES = Set.of("objectivePhase", "objectiveState", "objectiveSiteKind",
            "objectiveAssignment", "briefingExperienceTier", "deployAptitude", "resultsScreenOfficerStatus",
            "briefingOpposition", "resultsScreenMemberCode");

    private static JSONObject catalog() throws Exception {
        String source = Files.readString(Path.of("mod/data/strings/strings.json"));
        return new JSONObject(source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
    }

    @Test void everyKeyTheMissionFlowNamesIsInTheCatalogAndEveryPatternParses() throws Exception {
        JSONObject catalog = catalog();
        for (String path : SOURCES) {
            var matches = KEY.matcher(Files.readString(Path.of(path)));
            while (matches.find()) {
                String key = matches.group(1);
                if (PREFIXES.contains(key)) continue;
                assertTrue(catalog.has(key), path + ": " + key);
                String text = catalog.getString(key);
                if (text.contains("{")) assertDoesNotThrow(() -> new MessageFormat(text), key);
            }
        }
        for (RiskLevel risk : RiskLevel.values()) assertTrue(catalog.has("briefingOpposition" + risk.name()));
        for (ExperienceTier tier : ExperienceTier.values()) assertTrue(catalog.has("briefingExperienceTier" + tier.name()));
        for (SoldierAptitude aptitude : SoldierAptitude.values()) assertTrue(catalog.has("deployAptitude" + aptitude.name()));
        for (Status status : Status.values()) assertTrue(catalog.has("resultsScreenOfficerStatus" + status.name()));
        for (State state : State.values()) assertTrue(catalog.has("objectiveState" + state.name()));
        for (AssignmentKind kind : AssignmentKind.values()) assertTrue(catalog.has("objectiveAssignment" + kind.name()));
        for (var phase : OpeningOperationCommandPicture.Phase.values()) assertTrue(catalog.has("objectivePhase" + phase.name()));
    }

    @Test void quantityPatternsFormatOneAndTwo() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            assertEquals("1 squad active · remaining commitment held in reserve", FieldPresencePolicy.INFILTRATION.briefingText());
            assertEquals("2 squads active · remaining commitment held in reserve", FieldPresencePolicy.CAPTURE_TEAM.briefingText());
            assertEquals("1 officer · 1 squad · 1 marine", Strings.format("deployForceStrength", 1, 1, 1));
            assertEquals("2 officers · 2 squads · 24 marines", Strings.format("deployForceStrength", 2, 2, 24));
            assertEquals("0 officers · 1 squad · 12 marines", Strings.format("deployForceStrength", 0, 1, 12));
            assertEquals("1 acting NCO", Strings.format("deployForceActingNco", 1));
            assertEquals("2 acting NCOs", Strings.format("deployForceActingNco", 2));
            assertEquals("1 needs close escort", Strings.format("objectivePayloadNeedEscort", 1));
            assertEquals("2 need close escort", Strings.format("objectivePayloadNeedEscort", 2));
            assertEquals("Planting / 1.5s of 4.0s", Strings.format("objectiveTimed", "Planting",
                    Strings.format("objectiveSeconds", 1.5f), Strings.format("objectiveSeconds", 4f)));
        }
    }

    @Test void translationReachesTheMissionFlowWhileNamesPassThrough() throws Exception {
        String officerName = "Morgan's {Second} Hale";
        String placeName = "Morrow's {Yard}";
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> "«" + text + "»")) {
            assertTrue(FieldPresencePolicy.UNRESTRICTED.briefingText().startsWith("«"));
            assertTrue(MissionForceEnvelope.oppositionExpectation(RiskLevel.MEDIUM).startsWith("«"));
            var item = MissionObjectiveFamilies.at("site", placeName, State.FAILED, "phase", "steps",
                    "instruction", "measure", -1, "", 0, 0);
            assertEquals(placeName, item.name());
            assertTrue(item.blocker().startsWith("«"));
            var officer = OfficerCommandProfile.capture(new MarineCaptain(officerName, null, Rank.LIEUTENANT, 0f));
            String summary = new OfficerMissionResult(officer, Set.of(), 40, Status.ACTIVE, 0f, null).summary();
            assertTrue(summary.startsWith("«"));
            assertTrue(summary.contains(officerName));
        }
    }
}
