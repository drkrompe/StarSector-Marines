package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DebugMissionDifficultyTest {

    @Test
    void tierChoiceMovesTierOwnedLiftWithoutChangingRiskOrPresence() {
        Mission mission = Mission.builder()
                .id("debug:ASSAULT:FIRST_CONTRACT:0")
                .name("ASSAULT — First Contract")
                .type(MissionType.ASSAULT)
                .source(MissionSource.DEBUG)
                .tier(OperationTier.FIRST_CONTRACT)
                .risk(RiskLevel.HIGH)
                .requiredDrops(2)
                .employerShuttles(2)
                .fieldPresencePolicy(FieldPresencePolicy.CAPTURE_TEAM)
                .build();

        Mission adjusted = DebugMissionDifficulty.atTier(
                mission, OperationTier.VETERAN);

        assertEquals(OperationTier.VETERAN, adjusted.tier);
        assertEquals(MissionGenerator.requiredDropsFor(
                MissionType.ASSAULT, OperationTier.VETERAN),
                adjusted.requiredDrops);
        assertEquals(RiskLevel.HIGH, adjusted.risk);
        assertEquals(FieldPresencePolicy.CAPTURE_TEAM,
                adjusted.fieldPresencePolicy);
        assertEquals("ASSAULT — Veteran", adjusted.name);
    }

    @Test
    void missionTypeFloorRemainsRealForTheDebugSlider() {
        Mission conquest = Mission.builder()
                .id("debug:CONQUEST:REINFORCED:0")
                .name("CONQUEST — Reinforced")
                .type(MissionType.CONQUEST)
                .source(MissionSource.DEBUG)
                .tier(OperationTier.REINFORCED)
                .risk(RiskLevel.MEDIUM)
                .requiredDrops(84)
                .employerShuttles(84)
                .build();

        Mission adjusted = DebugMissionDifficulty.atTier(
                conquest, OperationTier.FIRST_CONTRACT);

        assertEquals(OperationTier.REINFORCED, adjusted.tier);
        assertEquals(MissionGenerator.requiredDropsFor(
                MissionType.CONQUEST, OperationTier.REINFORCED),
                adjusted.requiredDrops);
        assertEquals(adjusted.requiredDrops, adjusted.employerShuttles);
    }

    @Test
    void productionMissionIsNotADeveloperOverrideTarget() {
        Mission production = Mission.builder()
                .id("campaign:assault")
                .name("Assault")
                .type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED)
                .tier(OperationTier.FIRST_CONTRACT)
                .risk(RiskLevel.LOW)
                .build();

        assertSame(production, DebugMissionDifficulty.atTier(
                production, OperationTier.FULL_STRENGTH));
    }

    /**
     * How settled the map is is the battle's statement, so the DEBUG board may
     * make it — and hand it back, which is what a null request means.
     */
    @Test
    void theDebugBoardStatesHowSettledItsMapIs() {
        Mission mission = Mission.builder()
                .id("debug:ASSAULT:ESTABLISHED:0")
                .name("ASSAULT — Established")
                .type(MissionType.ASSAULT)
                .source(MissionSource.DEBUG)
                .tier(OperationTier.ESTABLISHED)
                .risk(RiskLevel.MEDIUM)
                .build();

        Mission dense = DebugMissionDifficulty.atSprawl(
                mission, PrecinctPlan.Sprawl.DENSE);
        assertEquals(PrecinctPlan.Sprawl.DENSE, dense.sprawl);
        assertEquals(OperationTier.ESTABLISHED, dense.tier,
                "stating a sprawl is not a statement about scale");

        assertNull(DebugMissionDifficulty.atSprawl(dense, null).sprawl,
                "clearing the statement hands the answer back to the market");
    }

    @Test
    void aProductionMissionsMapIsNotADeveloperOverrideTarget() {
        Mission production = Mission.builder()
                .id("campaign:raid")
                .name("Raid")
                .type(MissionType.RAID)
                .source(MissionSource.GENERATED)
                .tier(OperationTier.FIRST_CONTRACT)
                .risk(RiskLevel.LOW)
                .build();

        assertSame(production, DebugMissionDifficulty.atSprawl(
                production, PrecinctPlan.Sprawl.DENSE));
    }
}
