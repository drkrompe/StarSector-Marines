package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.dillon.starsectormarines.marine.BoatFitting;
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

    @ParameterizedTest
    @EnumSource(value = MissionType.class, names = {"CONQUEST"}, mode = EnumSource.Mode.EXCLUDE)
    void ordinaryScenarioCaptureKeepsActualBoatIdentityAndFittings(MissionType type) {
        Mission mission = Mission.builder().id("player-boats").name("Player boats")
                .type(type).source(MissionSource.STORY).tier(OperationTier.FIRST_CONTRACT)
                .risk(RiskLevel.LOW).requiredDrops(3).employerShuttles(1).build();
        FittedBoat boat = new FittedBoat(ShuttleType.HERMES,
                BoatFitting.REINFORCED_PLATING, BoatFitting.TUNED_DRIVE, "company-boat-03");
        var plan = MissionLaunch.captureScenario(null, mission, List.of(boat), FlybyRoster.EMPTY,
                List.of(), List.of(), null, 42);
        assertEquals(1, plan.firstPlayerShuttle());
        var player = plan.detachment().shuttleManifest.get(1);
        assertSame(boat, player.airframe);
        assertEquals(ShuttleType.HERMES, player.type);
        assertEquals(2, player.cycles);
        assertEquals(boat.pattern().capacity, player.seatsPerSortie);
    }

    @Test
    void launchRejectsIncompleteLiftBeforeBuildingAnything() {
        Mission mission = Mission.builder().id("no-boats").name("No boats")
                .type(MissionType.ASSAULT).source(MissionSource.STORY)
                .requiredDrops(3).employerShuttles(1).build();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MissionLaunch.captureScenario(null, mission, List.of(), FlybyRoster.EMPTY,
                        List.of(), List.of(), null, 42));
        assertTrue(error.getMessage().contains("Company boats are required"));
    }

    private static MissionLaunch.ScenarioPlan capture(Mission mission, long seed, int boats) {
        List<FittedBoat> lift = boats == 1
                ? List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE))
                : List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE), FittedBoat.standard(ShuttleType.AEROSHUTTLE));
        return MissionLaunch.captureScenario(null, mission, lift, FlybyRoster.EMPTY,
                List.of(), List.of(), null, seed);
    }
}
