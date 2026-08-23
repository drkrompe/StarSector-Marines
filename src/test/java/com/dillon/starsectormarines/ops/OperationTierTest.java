package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scale on its own axis — `mission-tiers/overview.md`.
 *
 * <p>What these hold: a mission type can now span the whole ladder instead of
 * having its tier baked into its risk table, CONQUEST cannot be offered as a
 * beginner's job, and the one number play has actually measured —
 * CONQUEST at the top of the ladder — survives the migration.
 */
class OperationTierTest {

    @Test
    void conquestAtTheTopOfTheLadderKeepsTheForceThatWasMeasured() {
        // The pre-split table put CONQUEST/HIGH at 320 defenders, and play
        // established that it wants hundreds of marines. The tier curve has to
        // land on the same fight or the measurement is invalidated.
        DefenderRoster roster = DefenderRoster.forMission(MissionType.CONQUEST,
                OperationTier.FULL_STRENGTH, RiskLevel.HIGH, true);

        assertEquals(322, roster.totalCount,
                "within rounding of the 320 the old CONQUEST/HIGH table produced");
    }

    @Test
    void conquestIsNeverOfferedAsABeginnersJob() {
        assertSame(OperationTier.REINFORCED, MissionType.CONQUEST.tierFloor);

        // Through the builder, whatever risk it was authored at.
        Mission mission = Mission.builder()
                .id("t").name("t").type(MissionType.CONQUEST)
                .source(MissionSource.DEBUG).risk(RiskLevel.LOW)
                .build();

        assertTrue(mission.tier.atLeast(OperationTier.REINFORCED),
                "a low-risk conquest still fields a late-game force");
    }

    @Test
    void everyOtherTypeCanReachTheTopOfTheLadder() {
        // The point of the split: before, ASSAULT topped out at its HIGH-risk
        // row and could never be a late-game battle at all.
        int assaultLate = DefenderRoster.forMission(MissionType.ASSAULT,
                OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM, false).totalCount;
        int assaultEarly = DefenderRoster.forMission(MissionType.ASSAULT,
                OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, false).totalCount;

        assertTrue(assaultLate > 200, "a late-game assault is a real fight: " + assaultLate);
        assertTrue(assaultEarly < 20, "a beginner's assault stays small: " + assaultEarly);
    }

    @Test
    void riskColoursATierRatherThanReplacingIt() {
        int low = DefenderRoster.forMission(MissionType.RAID,
                OperationTier.VETERAN, RiskLevel.LOW, false).totalCount;
        int high = DefenderRoster.forMission(MissionType.RAID,
                OperationTier.VETERAN, RiskLevel.HIGH, false).totalCount;
        int nextTier = DefenderRoster.forMission(MissionType.RAID,
                OperationTier.REINFORCED, RiskLevel.LOW, false).totalCount;

        assertTrue(high > low, "risk still means something");
        assertTrue(high < nextTier,
                "but the riskiest job at one tier is smaller than the safest at the next — "
                        + high + " vs " + nextTier);
    }

    @Test
    void mapSizeFollowsTheTier() {
        assertSame(MapScale.SMALL, MapScale.forTier(OperationTier.FIRST_CONTRACT));
        assertSame(MapScale.LARGE, MapScale.forTier(OperationTier.FULL_STRENGTH));
        assertSame(MapScale.MEDIUM, MapScale.forTier(null), "no signal reads as the middle");
    }

    @Test
    void liftTracksTheForceTheTierExpects() {
        // 40 drops at a Valkyrie's twelve seats is the 480 that a full-strength
        // conquest is written for.
        assertEquals(40, MissionGenerator.requiredDropsFor(
                MissionType.CONQUEST, OperationTier.FULL_STRENGTH));
        assertTrue(MissionGenerator.requiredDropsFor(
                MissionType.SABOTAGE, OperationTier.FIRST_CONTRACT) >= 2,
                "one drop is not an operation");
    }

    @Test
    void aMissionWithNoTierAuthoredStillGetsACoherentOne() {
        Mission mission = Mission.builder()
                .id("t").name("t").type(MissionType.RAID)
                .source(MissionSource.DEBUG).risk(RiskLevel.MEDIUM)
                .build();

        assertSame(OperationTier.ESTABLISHED, mission.tier);
    }

    @Test
    void theCompatibilityBridgeStillHonoursTheTypeFloor() {
        // The legacy (type, risk) entry point is reachable from headless
        // previews. It must not hand CONQUEST a beginner's garrison.
        int viaBridge = DefenderRoster.forMission(
                MissionType.CONQUEST, RiskLevel.LOW, false).totalCount;

        assertTrue(viaBridge > 100,
                "a conquest is a conquest however it was built: " + viaBridge);
    }
}
