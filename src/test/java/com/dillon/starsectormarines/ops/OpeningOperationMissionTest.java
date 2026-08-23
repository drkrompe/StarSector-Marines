package com.dillon.starsectormarines.ops;

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
        return Mission.builder()
                .id(id)
                .name("mission")
                .type(MissionType.ASSAULT)
                .source(source)
                .risk(RiskLevel.LOW)
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(2)
                .employerShuttles(1)
                .build();
    }
}
