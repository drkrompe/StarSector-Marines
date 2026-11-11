package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.LongSurveyProgress;
import com.dillon.starsectormarines.campaign.LongSurveyProgress.Chapter;
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

class LongSurveyCampaignTest {
    private HeadlessStrings strings;

    @BeforeEach void shippedCopy() throws Exception { strings = new HeadlessStrings(Path.of("mod")); }
    @AfterEach void restore() { strings.close(); }

    private static LongSurveyProgress settled(boolean victory) {
        var p = new LongSurveyProgress();
        String id = p.missionId(Chapter.THE_FALL, false);
        p.begin(id);
        p.settle(id, victory, victory ? LongSurveyProgress.fee(id) : 0);
        return p;
    }

    @Test void theBoardOffersTheCurrentChapterThenEveryWonChapterAsAReplay() {
        List<Mission> fresh = LongSurveyCampaign.offers(new LongSurveyProgress(), "World");
        assertEquals(1, fresh.size());
        assertEquals(Chapter.THE_FALL.fee, fresh.get(0).payout);
        assertEquals(MissionSource.STORY, fresh.get(0).source);

        // Stakes is current: it leads the board, The Fall's replay after it.
        List<Mission> afterFall = LongSurveyCampaign.offers(settled(true), "World");
        assertEquals(2, afterFall.size());
        assertEquals(Chapter.STAKES, LongSurveyProgress.chapterOf(afterFall.get(0).id));
        assertFalse(LongSurveyProgress.replay(afterFall.get(0).id));
        assertTrue(LongSurveyProgress.replay(afterFall.get(1).id));
        assertEquals(Chapter.THE_FALL.replayFee(), afterFall.get(1).payout);
    }

    @Test void everyAttemptRetryAndReplayPlaysTheChaptersOwnSeed() {
        long first = LongSurveyCampaign.offers(new LongSurveyProgress(), "World").get(0).battleSeed;
        long retry = LongSurveyCampaign.offers(settled(false), "World").get(0).battleSeed;
        long replay = LongSurveyCampaign.offers(settled(true), "World").get(1).battleSeed;
        assertEquals(first, retry);
        assertEquals(first, replay);
        assertEquals(first, LongSurveyCampaign.fixedSeed(Chapter.THE_FALL));
    }

    @Test void aRetryBriefsWithItsOwnLineAndAReplayWithTheReplayLine() {
        assertEquals(7, LongSurveyCampaign.briefing(new LongSurveyProgress(), Chapter.THE_FALL, false).size());
        var retry = LongSurveyCampaign.briefing(settled(false), Chapter.THE_FALL, false);
        assertEquals(List.of("@longSurveyTheFallRetry"), retry.stream().map(MissionDialogue.Turn::text).toList());
        var replay = LongSurveyCampaign.briefing(settled(true), Chapter.THE_FALL, true);
        assertEquals(List.of("@longSurveyReplay"), replay.stream().map(MissionDialogue.Turn::text).toList());
    }

    @Test void theCommanderSpeaksWithTheirOwnRole() {
        var turn = LongSurveyCampaign.briefing(new LongSurveyProgress(), Chapter.THE_FALL, false).get(0);
        assertNotEquals(Strings.get("briefingMissionContact"), MissionDialogue.speakerRole(turn.speaker()));
    }

    @Test void everyBriefingAndBoardLineResolvesInTheShippedCatalog() {
        for (Chapter chapter : Chapter.values()) {
            for (var p : List.of(new LongSurveyProgress(), settled(false), settled(true)))
                for (boolean replay : new boolean[]{false, true})
                    for (var turn : LongSurveyCampaign.briefing(p, chapter, replay)) {
                        assertFalse(Strings.resolve(turn.text()).isBlank(), turn.text());
                        assertFalse(Strings.resolve(turn.speaker()).isBlank(), turn.speaker());
                    }
            assertFalse(LongSurveyCampaign.offer(new LongSurveyProgress(), chapter, false, "World").name.isBlank());
        }
    }

    @Test void theDebugPlayerListsEveryChapterOnItsSeedWithTheChallengeForce() {
        for (var scenario : DebugMissionCatalog.Scenario.values()) {
            Chapter chapter = scenario.longSurvey();
            if (chapter == null) continue;
            assertEquals("LS" + chapter.number(), scenario.sequence());
            assertTrue(scenario.offered());
            Mission m = scenario.mission();
            assertEquals(LongSurveyCampaign.fixedSeed(chapter), m.battleSeed);
            assertEquals(LongSurveyCampaign.RECOMMENDED_MACHINES, DebugMissionCatalog.preset(m).mechs());
        }
    }
}
