package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fort.FortKind;
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
        // The first-pass policy stands its declared batteries as battery forts (CONQUEST_BATTERIES_AS_FORTS).
        assertEquals(0, declared.batteries());
        assertEquals(MissionDefencePolicy.CONQUEST_BATTERIES_WHEN_MARKET_HAS_THEM,
                declared.fortCount(FortKind.BATTERY_FORT));
        // ... and its declared shield generator as a shield fort (CONQUEST_SHIELD_AS_FORT), never both.
        assertEquals(MissionDefencePolicy.CONQUEST_SHIELD_WHEN_MARKET_HAS_ONE && !MissionDefencePolicy.CONQUEST_SHIELD_AS_FORT,
                declared.shieldGenerator());
        assertEquals(MissionDefencePolicy.CONQUEST_SHIELD_WHEN_MARKET_HAS_ONE && MissionDefencePolicy.CONQUEST_SHIELD_AS_FORT ? 1 : 0,
                declared.fortCount(FortKind.SHIELD_FORT));
        assertEquals(MissionDefencePolicy.CONQUEST_NESTS_WHEN_MARKET_HAS_THEM, declared.nests());

        GroundDefences nestsOnly = MissionDefencePolicy.declare(of(MissionType.CONQUEST),
                TargetProfile.NEUTRAL.withDefences(true, false, false));
        assertEquals(new GroundDefences(0, false, MissionDefencePolicy.CONQUEST_NESTS_WHEN_MARKET_HAS_THEM), nestsOnly);
    }

    @Test
    void theConversionToFortsIsOneSwitchAndAFortIsNotABareBattery() {
        GroundDefences declared = MissionDefencePolicy.conquest(FORTRESS_MARKET);
        assertEquals(MissionDefencePolicy.CONQUEST_BATTERIES_AS_FORTS ? 0 : 4, declared.batteries());
        GroundDefences marketWithoutBatteries = MissionDefencePolicy.conquest(
                TargetProfile.NEUTRAL.withDefences(true, false, true));
        assertEquals(0, marketWithoutBatteries.fortCount(FortKind.BATTERY_FORT));
        // The dial's fort option declares forts and nothing else, so a scene can ask for one on its own.
        GroundDefences dial = DebugDefences.BATTERY_FORTS.declaration();
        assertEquals(0, dial.batteries());
        assertEquals(false, dial.shieldGenerator());
        assertEquals(2, dial.fortCount(FortKind.BATTERY_FORT));
        assertEquals(false, dial.isNone());
        GroundDefences shieldDial = DebugDefences.SHIELD_FORT.declaration();
        assertEquals(1, shieldDial.fortCount(FortKind.SHIELD_FORT));
        assertEquals(false, shieldDial.shieldGenerator());
        assertEquals(0, shieldDial.fortCount(FortKind.BATTERY_FORT));
        assertEquals(false, GroundDefences.NONE.withForts(FortKind.BATTERY_FORT, 1).isNone());
        assertEquals(true, GroundDefences.NONE.withForts(FortKind.BATTERY_FORT, 0).isNone());
    }

    @Test
    void theDebugDialCanDeclareNestsAndFortressIncludesThem() {
        Mission nests = Mission.builder().type(MissionType.SABOTAGE).defences(DebugDefences.NESTS).build();
        GroundDefences declared = MissionDefencePolicy.declare(nests, TargetProfile.NEUTRAL);
        assertEquals(0, declared.batteries());
        assertEquals(false, declared.shieldGenerator());
        assertEquals(true, declared.nests() > 0);
        assertEquals(true, DebugDefences.FORTRESS.declaration().nests() > 0);
    }

    @Test
    void anExplicitStatementBeatsThePolicyBothWays() {
        Mission stripped = Mission.builder().type(MissionType.CONQUEST).defences(DebugDefences.NONE).build();
        assertEquals(GroundDefences.NONE, MissionDefencePolicy.declare(stripped, FORTRESS_MARKET));

        Mission fortress = Mission.builder().type(MissionType.SABOTAGE).defences(DebugDefences.FORTRESS).build();
        assertEquals(DebugDefences.FORTRESS.declaration(), MissionDefencePolicy.declare(fortress, TargetProfile.NEUTRAL));
    }
}
