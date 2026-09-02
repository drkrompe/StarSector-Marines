package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
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

    /**
     * How far out the force lands is the battle's statement too, so the DEBUG
     * board may make it — and hand it back to the mission type's default.
     */
    @Test
    void theDebugBoardStatesHowFarOutItLands() {
        Mission mission = Mission.builder()
                .id("debug:CONQUEST:REINFORCED:0")
                .name("CONQUEST — Reinforced")
                .type(MissionType.CONQUEST)
                .source(MissionSource.DEBUG)
                .tier(OperationTier.REINFORCED)
                .risk(RiskLevel.MEDIUM)
                .build();

        Mission close = DebugMissionDifficulty.atStandoff(mission, Standoff.CLOSE);
        assertEquals(Standoff.CLOSE, close.standoff);
        assertEquals(OperationTier.REINFORCED, close.tier,
                "stating a standoff is not a statement about scale");

        assertNull(DebugMissionDifficulty.atStandoff(close, null).standoff,
                "clearing the statement hands the answer back to the mission type");
    }

    /**
     * How many lanes of resistance the map lays is the battle's statement too,
     * and zero is one of the positions — a Conquest with nothing on its tracks
     * is the control a lane balance run is read against.
     */
    @Test
    void theDebugBoardStatesHowManyLanesItLays() {
        Mission mission = Mission.builder()
                .id("debug:CONQUEST:REINFORCED:0")
                .name("CONQUEST — Reinforced")
                .type(MissionType.CONQUEST)
                .source(MissionSource.DEBUG)
                .tier(OperationTier.REINFORCED)
                .risk(RiskLevel.MEDIUM)
                .build();

        Mission oneLane = DebugMissionDifficulty.atLanes(mission, 1);
        assertEquals(1, oneLane.lanes);
        assertEquals(OperationTier.REINFORCED, oneLane.tier,
                "stating a lane count is not a statement about scale");
        assertEquals(0, DebugMissionDifficulty.atLanes(mission, 0).lanes,
                "zero lanes is a statement, not the absence of one");
        assertNull(DebugMissionDifficulty.atLanes(oneLane, null).lanes,
                "clearing the statement hands the answer back to the mission type");
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
