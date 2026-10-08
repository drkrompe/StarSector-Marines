package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Construction-input identity only: these checks deliberately never build a world. */
class MissionScenarioPlanTest {
    @Test void equalInputsReuseSurveyButConstructionChangesInvalidateIt() {
        Mission mission = Mission.builder().id("survey").name("Survey")
                .type(MissionType.SABOTAGE).source(MissionSource.DEBUG)
                .tier(OperationTier.FIRST_CONTRACT).risk(RiskLevel.MEDIUM).requiredDrops(2).build();
        var initial = capture(mission, 42, 1);
        assertInstanceOf(SabotageBattleFixture.class, initial.fixture());
        assertEquals(initial.key(), capture(mission, 42, 1).key());
        assertNotEquals(initial.key(), capture(mission, 43, 1).key());
        assertNotEquals(initial.key(), capture(mission, 42, 2).key());
        assertNotEquals(initial.key(), capture(Mission.builder(mission).risk(RiskLevel.HIGH).build(), 42, 1).key());
        // Negotiating payment must not move the objective while the player reads the map.
        assertEquals(initial.key(), capture(Mission.builder(mission).payout(20000).cashMultiplier(120).build(), 42, 1).key());
    }

    private static MissionLaunch.ScenarioPlan capture(Mission mission, long seed, int boats) {
        List<FittedBoat> lift = boats == 1
                ? List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE))
                : List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE), FittedBoat.standard(ShuttleType.AEROSHUTTLE));
        return MissionLaunch.captureScenario(null, mission, lift, FlybyRoster.EMPTY,
                List.of(), List.of(), null, seed);
    }
}
