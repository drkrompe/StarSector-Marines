package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress.Chapter;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
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

    @Test void everyBriefingTurnNamesItsChaptersRecording() throws Exception {
        var catalog = WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod/data/marines/wellhollow_narration.json"))));
        for (Chapter chapter : Chapter.values()) {
            var lost = new WellhollowBloomProgress();
            for (Chapter before : Chapter.values()) {
                String id = lost.missionId(lost.current(), false);
                lost.begin(id);
                lost.settle(id, before != chapter, 0);
                if (before == chapter) break;
            }
            var recordings = catalog.briefing(WellhollowBloomCampaign.narrationMissionId(chapter));
            for (boolean replay : new boolean[]{false, true})
                for (var progress : List.of(new WellhollowBloomProgress(), lost))
                    for (var turn : WellhollowBloomCampaign.briefing(progress, chapter, replay)) {
                        var line = recordings.stream().filter(l -> l.id().equals(turn.narrationId())).findFirst();
                        assertTrue(line.isPresent(), chapter + " " + turn.narrationId());
                        assertEquals(Strings.resolve(turn.text()), line.get().text(), turn.narrationId());
                    }
        }
    }

    @Test void theKhettaraPlaysItsOwnSeedOnEveryAttemptAndReplay() {
        var lost = new WellhollowBloomProgress();
        String id = lost.missionId(lost.current(), false);
        lost.begin(id);
        lost.settle(id, false, 0);
        long first = WellhollowBloomCampaign.offers(new WellhollowBloomProgress(), "Asharu").get(0).battleSeed;
        long retry = WellhollowBloomCampaign.offers(lost, "Asharu").get(0).battleSeed;
        List<Mission> afterTwo = WellhollowBloomCampaign.offers(wonThrough(2), "Asharu");
        long replay = afterTwo.get(afterTwo.size() - 1).battleSeed;
        assertEquals(WellhollowBloomCampaign.COLONY_BREACH_SEED, first);
        assertEquals(first, retry);
        assertEquals(first, replay);
        assertEquals(first, WellhollowBloomCampaign.fixedSeed(Chapter.COLONY_BREACH));
    }

    @Test void ainTafrantPlaysItsOwnSeedOnEveryAttempt() {
        for (int attempt = 0; attempt < 4; attempt++)
            assertEquals(WellhollowBloomCampaign.AIN_TAFRANT_SEED, WellhollowBloomCampaign.seed(Chapter.CORDON_ROAD, attempt));
        assertEquals(WellhollowBloomCampaign.AIN_TAFRANT_SEED, WellhollowBloomCampaign.fixedSeed(Chapter.CORDON_ROAD));
    }

    @Test void theBoardOffersTheCurrentChapterThenEveryWonChapterAsAReplay() {
        List<Mission> offers = WellhollowBloomCampaign.offers(wonThrough(2), "Asharu");
        assertEquals(3, offers.size());
        assertEquals(Chapter.THE_BURN, WellhollowBloomProgress.chapterOf(offers.get(0).id));
        assertFalse(WellhollowBloomProgress.replay(offers.get(0).id));
        assertEquals(Chapter.THE_BURN.fee, offers.get(0).payout);
        assertEquals(Chapter.CORDON_ROAD, WellhollowBloomProgress.chapterOf(offers.get(1).id));
        assertTrue(WellhollowBloomProgress.replay(offers.get(1).id));
        assertEquals(Chapter.CORDON_ROAD.replayFee(), offers.get(1).payout);
        for (Mission m : offers) assertEquals(MissionSource.STORY, m.source);
    }

    @Test void onlyTheColonyBreachChapterTakesTheRescuePath() {
        var p = new WellhollowBloomProgress();
        Mission breach = WellhollowBloomCampaign.offer(p, Chapter.COLONY_BREACH, false, "Asharu");
        Mission road = WellhollowBloomCampaign.offer(wonThrough(1), Chapter.CORDON_ROAD, false, "Asharu");
        assertTrue(WellhollowBloomCampaign.isColonyBreach(breach));
        assertTrue(MissionLaunch.isCivilianRescueBattle(breach));
        assertEquals(RiskLevel.HIGH, breach.risk);
        assertFalse(MissionLaunch.isCivilianRescueBattle(road));
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
