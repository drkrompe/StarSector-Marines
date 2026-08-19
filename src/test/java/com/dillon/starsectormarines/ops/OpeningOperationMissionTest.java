package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningOperationMissionTest {

    @Test
    void onlyStableStoryIdsRouteToOpeningBattle() {
        Mission relief = mission(OpeningOperationKind.RELIEF.missionId,
                MissionSource.STORY);
        Mission generatedLookalike = mission(
                OpeningOperationKind.RELIEF.missionId,
                MissionSource.GENERATED);
        Mission otherStory = mission("story_other", MissionSource.STORY);

        assertTrue(MissionLaunch.isOpeningOperationBattle(relief));
        assertFalse(MissionLaunch.isOpeningOperationBattle(generatedLookalike));
        assertFalse(MissionLaunch.isOpeningOperationBattle(otherStory));
    }

    private static Mission mission(String id, MissionSource source) {
        return new Mission(id, "mission", MissionType.ASSAULT, source,
                0, RiskLevel.LOW, "", "", 0.5f, 0.5f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                2, 1, null, null);
    }
}
