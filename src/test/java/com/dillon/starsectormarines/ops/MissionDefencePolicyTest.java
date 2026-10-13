package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The mission, not the market, decides which defences a battle carries. */
class MissionDefencePolicyTest {

    private static final TargetProfile FORTRESS_MARKET = TargetProfile.NEUTRAL.withDefences(true, true, true);

    private static Mission of(MissionType type) {
        return Mission.builder().type(type).build();
    }

    @Test
    void aMissionTypeWithNoPolicyCarriesNoneWhateverTheMarketHolds() {
        assertEquals(GroundDefences.NONE, MissionDefencePolicy.declare(of(MissionType.SABOTAGE), FORTRESS_MARKET));
    }

    @Test
    void conquestReadsTheMarketSignalsAsInput() {
        assertEquals(GroundDefences.NONE,
                MissionDefencePolicy.declare(of(MissionType.CONQUEST), TargetProfile.NEUTRAL));
        GroundDefences declared = MissionDefencePolicy.declare(of(MissionType.CONQUEST), FORTRESS_MARKET);
        assertEquals(MissionDefencePolicy.CONQUEST_BATTERIES_WHEN_MARKET_HAS_THEM, declared.batteries());
        assertEquals(MissionDefencePolicy.CONQUEST_SHIELD_WHEN_MARKET_HAS_ONE, declared.shieldGenerator());
    }

    @Test
    void anExplicitStatementBeatsThePolicyBothWays() {
        Mission stripped = Mission.builder().type(MissionType.CONQUEST).defences(DebugDefences.NONE).build();
        assertEquals(GroundDefences.NONE, MissionDefencePolicy.declare(stripped, FORTRESS_MARKET));

        Mission fortress = Mission.builder().type(MissionType.SABOTAGE).defences(DebugDefences.FORTRESS).build();
        assertEquals(DebugDefences.FORTRESS.declaration(), MissionDefencePolicy.declare(fortress, TargetProfile.NEUTRAL));
    }
}
