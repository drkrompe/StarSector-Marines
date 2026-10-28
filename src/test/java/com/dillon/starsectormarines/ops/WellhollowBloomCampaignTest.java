package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress.Chapter;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WellhollowBloomCampaignTest {
    private HeadlessStrings strings;

    @BeforeEach void shippedCopy() throws Exception { strings = new HeadlessStrings(Path.of("mod")); }
    @AfterEach void restore() { strings.close(); }

    private static WellhollowBloomProgress wonThrough(int chapters) {
        var p = new WellhollowBloomProgress();
        for (int i = 0; i < chapters; i++) {
            String id = p.missionId(p.current(), false);
            p.begin(id);
            p.settle(id, true, WellhollowBloomProgress.fee(id));
        }
        return p;
    }

    @Test void theBoardOffersTheCurrentChapterThenEveryWonChapterAsAReplay() {
        List<Mission> offers = WellhollowBloomCampaign.offers(wonThrough(2), "Asharu");
        assertEquals(3, offers.size());
        assertEquals(Chapter.CORDON_ROAD, WellhollowBloomProgress.chapterOf(offers.get(0).id));
        assertFalse(WellhollowBloomProgress.replay(offers.get(0).id));
        assertEquals(Chapter.CORDON_ROAD.fee, offers.get(0).payout);
        assertEquals(Chapter.CORDON_LINE, WellhollowBloomProgress.chapterOf(offers.get(1).id));
        assertTrue(WellhollowBloomProgress.replay(offers.get(1).id));
        assertEquals(Chapter.CORDON_LINE.replayFee(), offers.get(1).payout);
        for (Mission m : offers) assertEquals(MissionSource.STORY, m.source);
    }

    @Test void onlyTheColonyBreachChapterTakesTheRescuePath() {
        var p = new WellhollowBloomProgress();
        Mission breach = WellhollowBloomCampaign.offer(p, Chapter.COLONY_BREACH, false, "Asharu");
        Mission line = WellhollowBloomCampaign.offer(wonThrough(1), Chapter.CORDON_LINE, false, "Asharu");
        assertTrue(WellhollowBloomCampaign.isColonyBreach(breach));
        assertTrue(MissionLaunch.isCivilianRescueBattle(breach));
        assertEquals(RiskLevel.HIGH, breach.risk);
        assertFalse(MissionLaunch.isCivilianRescueBattle(line));
    }

    @Test void theWardenSpeaksWithTheirOwnRoleAndPortrait() {
        var turn = WellhollowBloomCampaign.briefing(new WellhollowBloomProgress(), Chapter.COLONY_BREACH, false).get(0);
        assertNotEquals(Strings.get("briefingMissionContact"), MissionDialogue.speakerRole(turn.speaker()));
        assertFalse(MissionDialogue.speakerPortrait(turn.speaker()).isEmpty());
    }

    @Test void everyBriefingAndDebriefLineResolvesInTheShippedCatalog() {
        var complete = wonThrough(Chapter.values().length);
        for (Chapter chapter : Chapter.values()) {
            for (boolean replay : new boolean[]{false, true}) {
                var p = replay ? complete : wonThrough(chapter.ordinal());
                for (var turn : WellhollowBloomCampaign.briefing(p, chapter, replay)) {
                    assertFalse(Strings.resolve(turn.text()).isBlank());
                    assertFalse(Strings.resolve(turn.speaker()).isBlank());
                }
                assertFalse(WellhollowBloomCampaign.offer(p, chapter, replay, "Asharu").requirements.isBlank());
            }
        }
    }
}
