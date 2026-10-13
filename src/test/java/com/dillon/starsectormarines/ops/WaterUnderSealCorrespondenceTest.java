package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaterUnderSealCorrespondenceTest {
    @Test void victoryNamesActualFeeAndLossesAndIntroducesTheLimitedWarrant() {
        var mission = AgreusArcCampaign.offer(new AgreusArcProgress(), "Agreus");
        var result = MissionOutcome.builder().mission(mission).victory(true).payoutEarned(26000).marinesLost(3)
                .waterUnderSeal(new WaterUnderSealProgress.Report(true, true, true, true, true, true, 1, 2, false)).build();
        var lines = MissionDebriefConversation.lines(result, new WaylineNarration(List.of()));
        assertEquals(4, lines.size()); assertTrue(lines.stream().noneMatch(WaylineNarration.Line::voiced));
        assertEquals("NORTH WEIR / CASTLE & RUSK", MissionDebriefConversation.title(result));
        assertTrue(lines.get(1).text().contains("26,000 credits"));
        assertTrue(lines.get(2).text().contains("Company casualties: 3"));
        assertTrue(lines.get(2).text().contains("Constables lost: 2"));
        assertTrue(lines.get(2).text().contains("Technicians lost: 1"));
        assertTrue(lines.get(3).text().contains("Limited Warrant"));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("Specter")));
        assertTrue(MissionDebriefConversation.recordings(result, new WaylineNarration(List.of())).isEmpty());
    }
    @Test void withdrawalReportsObservedWorkAndReplacementMobilizationWithoutPayment() {
        var mission = AgreusArcCampaign.offer(new AgreusArcProgress(), "Agreus");
        var result = MissionOutcome.builder().mission(mission).victory(false).payoutEarned(0)
                .waterUnderSeal(new WaterUnderSealProgress.Report(true, true, false, false, false, true, 2, 4, true)).build();
        var lines = MissionDebriefConversation.lines(result, new WaylineNarration(List.of()));
        assertTrue(lines.get(0).text().contains("north room was reconnected"));
        assertTrue(lines.get(0).text().contains("south room was not reconnected"));
        assertTrue(lines.get(0).text().contains("repair load was destroyed"));
        assertTrue(lines.get(1).text().contains("fee is unpaid"));
        assertTrue(lines.get(1).text().contains("fresh repair cargo and a replacement technician party"));
        assertTrue(lines.get(2).text().contains("Technicians lost: 2"));
        assertTrue(lines.get(3).text().contains("contract can wait"));
    }
    @Test void publicPhaseBeatsHaveTextAndHiddenRoadArrivalsStaySilent() throws Exception {
        var catalog = WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
        var mission = AgreusArcCampaign.offer(new AgreusArcProgress(), "Agreus");
        String story = WaylineNarration.storyMissionId(mission);
        assertEquals("story_agreus_water_under_seal", story);
        assertFalse(catalog.find(story, "briefing").voiced());
        for (String beat : List.of("mobilization", "north_room", "south_room", "construction", "attack", "complete", "failed")) {
            var line = catalog.find(story, "water_under_seal." + beat);
            assertNotNull(line, beat); assertFalse(line.voiced());
            assertEquals(beat.equals("complete") || beat.equals("failed"), line.terminal());
        }
        assertNull(catalog.find(story, "water_under_seal.road_element_0"));
        assertNull(catalog.find(story, "water_under_seal.road_element_1"));
        assertNull(WaylineNarration.storyMissionId(Mission.builder(mission).source(MissionSource.RECURRING).build()));
    }
}
