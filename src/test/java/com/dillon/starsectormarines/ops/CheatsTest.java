package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheatsTest {

    private static Mission story(String id) {
        return Mission.builder().id(id).name("x").source(MissionSource.STORY).type(MissionType.ASSAULT).build();
    }

    @Test
    void winSkipsChaptersThatSettleOnABareVictory() {
        assertTrue(Cheats.canWin(story(OpeningOperationKind.RELIEF.missionId)));
        assertTrue(Cheats.canWin(Mission.builder(story("contract")).source(MissionSource.GENERATED).build()));
    }

    @Test
    void winIsWithheldWhereTheArcChecksWhatTheBattleRecorded() {
        assertFalse(Cheats.canWin(story(AgreusArcProgress.PREFIX + "0")));
        assertFalse(Cheats.canWin(story(ShepherdRecoveryProgress.PREFIX + "0")));
        assertFalse(Cheats.canWin(null));
    }
}
