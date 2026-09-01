package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scale on its own axis — `mission-tier-nouns.md`.
 *
 * <p>What these hold: a mission type can now span the whole ladder instead of
 * having its tier baked into its risk table, CONQUEST cannot be offered as a
 * beginner's job, and CONQUEST retains its authored mass independently of
 * what the campaign player can afford to bring.
 */
class OperationTierTest {

    @Test
    void conquestAtTheTopOfTheLadderOutnumbersFortyFullSquads() {
        DefenderRoster nominal = DefenderRoster.forMission(MissionType.CONQUEST,
                OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM, true);
        DefenderRoster high = DefenderRoster.forMission(MissionType.CONQUEST,
                OperationTier.FULL_STRENGTH, RiskLevel.HIGH, true);

        assertEquals(630, nominal.totalCount,
                "nominal Full Strength must exceed forty twelve-marine squads");
        assertEquals(725, high.totalCount,
                "risk may intensify the authored mass without reading attacker strength");
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
        // Conquest delivers half-squads: 168 six-seat sorties deploy 1,008
        // marines, balanced as 28 cycles on each of six lane shuttles.
        assertEquals(168, MissionGenerator.requiredDropsFor(
                MissionType.CONQUEST, OperationTier.FULL_STRENGTH));
        assertEquals(630, DefenderRoster.forMission(MissionType.CONQUEST,
                OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM, false).totalCount,
                "Conquest defender intensity must not inflate its lift demand");
        assertTrue(MissionGenerator.requiredDropsFor(
                MissionType.SABOTAGE, OperationTier.FIRST_CONTRACT) >= 2,
                "one drop is not an operation");
    }

    @Test
    void recommendationsUseTheSameCampaignCompanyLadder() {
        assertEquals(1, OperationTier.FIRST_CONTRACT.squadsDemanded);
        assertEquals(3, OperationTier.ESTABLISHED.squadsDemanded);
        assertEquals(6, OperationTier.VETERAN.squadsDemanded);
        assertEquals(17, OperationTier.REINFORCED.squadsDemanded);
        assertEquals(34, OperationTier.FULL_STRENGTH.squadsDemanded);
        assertEquals(42, MissionForceEnvelope.recommendedSquads(
                MissionType.CONQUEST, OperationTier.REINFORCED));
        assertEquals(84, MissionForceEnvelope.recommendedSquads(
                MissionType.CONQUEST, OperationTier.FULL_STRENGTH));
        assertEquals(4, MissionForceEnvelope.recommendedSquads(
                MissionType.SABOTAGE, OperationTier.FULL_STRENGTH));
        assertEquals(8, MissionForceEnvelope.recommendedSquads(
                MissionType.RAID, OperationTier.FULL_STRENGTH));
    }

    @Test
    void persistedEncodingReservesZeroForLegacyRows() {
        assertEquals(null, OperationTier.fromPersistedByte((byte) 0));
        for (OperationTier tier : OperationTier.values()) {
            assertSame(tier, OperationTier.fromPersistedByte(tier.toPersistedByte()));
        }
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
