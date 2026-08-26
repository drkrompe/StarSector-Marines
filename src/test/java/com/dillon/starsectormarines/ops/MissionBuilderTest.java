package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The builder's defaults are the API now — every caller that omits a field is
 * relying on them, so they get pinned here rather than left to whatever the
 * last deleted constructor happened to pass.
 */
class MissionBuilderTest {

    private static Mission.Builder minimal() {
        return Mission.builder()
                .id("id")
                .name("name")
                .type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED)
                .risk(RiskLevel.LOW);
    }

    @Test
    void anOmittedFieldMeansNothingToReport() {
        Mission mission = minimal().build();

        assertEquals(-1L, mission.contractId);
        assertEquals(-1L, mission.campaignEventId);
        assertEquals(-1, mission.campaignEventMarketId);
        assertEquals(-1L, mission.campaignEventThreatSeed);
        assertEquals(0, mission.civiliansAtRisk);

        assertEquals(0, mission.salvageBaseline);
        assertEquals(0, mission.salvageNegotiated);
        assertEquals(0, mission.contractSalvageBaseline);
        assertEquals(0, mission.contractSalvageNegotiated);
        assertEquals(100, mission.cashMultiplier & 0xFF,
                "an un-negotiated mission pays its baseline cash");

        assertSame(FlybyRoster.EMPTY, mission.clientFighterSupport);
        assertSame(FlybyRoster.EMPTY, mission.enemyFighterSupport);
        assertTrue(mission.employerPowerIds.isEmpty());
        assertEquals("", mission.requirements);
        assertEquals("", mission.flavor);
        assertNull(mission.targetPlanetName);
        assertNull(mission.targetIndustryId);
        assertNull(mission.targetFactionId);
        assertEquals(0, mission.payout);
        assertEquals(0, mission.requiredDrops);
        assertEquals(0, mission.employerShuttles);
        assertEquals(ConquestArrivalConfig.LEGACY,
                mission.conquestArrivalConfig());
    }

    /**
     * The reason {@code builder(Mission)} exists. A hand-written copy has to be
     * revisited every time this class gains a field; if this ever fails,
     * something was added to {@code Mission} and not to {@code Builder(Mission)},
     * and every copy-with-changes silently drops it.
     */
    @Test
    void copyingAMissionCarriesEveryFieldAcross() {
        Mission original = Mission.builder()
                .id("contract:4:phase:1:attempt:0")
                .name("Recover the Archive")
                .type(MissionType.EXTRACTION)
                .source(MissionSource.CAMPAIGN_EVENT)
                .payout(31_500)
                .risk(RiskLevel.HIGH)
                .requirements("Funded blind expedition")
                .flavor("The distress burst has gone quiet.")
                .mapPosition(0.25f, 0.75f)
                .clientFighterSupport(FlybyRoster.EMPTY)
                .enemyFighterSupport(FlybyRoster.EMPTY)
                .employerPowerIds(List.of("orbital_strike", "sensor_sweep"))
                .requiredDrops(6)
                .employerShuttles(2)
                .conquestArrivalConfig(new ConquestArrivalConfig(4, 2, 1.5f))
                .targetPlanetName("Eidolon")
                .targetIndustryId("refining")
                .targetFactionId("independent")
                .contractId(4L)
                .campaignEventId(7L)
                .campaignEventMarketId(3)
                .civiliansAtRisk(240)
                .campaignEventThreatSeed(55L)
                .salvageBaseline(30)
                .salvageNegotiated(12)
                .cashMultiplier(109)
                .contractSalvageBaseline(80)
                .contractSalvageNegotiated(44)
                .build();

        Mission copy = Mission.builder(original).build();

        assertEquals(original.id, copy.id);
        assertEquals(original.name, copy.name);
        assertEquals(original.type, copy.type);
        assertEquals(original.source, copy.source);
        assertEquals(original.payout, copy.payout);
        assertEquals(original.risk, copy.risk);
        assertEquals(original.requirements, copy.requirements);
        assertEquals(original.flavor, copy.flavor);
        assertEquals(original.normalizedX, copy.normalizedX);
        assertEquals(original.normalizedY, copy.normalizedY);
        assertSame(original.clientFighterSupport, copy.clientFighterSupport);
        assertSame(original.enemyFighterSupport, copy.enemyFighterSupport);
        assertEquals(original.employerPowerIds, copy.employerPowerIds);
        assertEquals(original.requiredDrops, copy.requiredDrops);
        assertEquals(original.employerShuttles, copy.employerShuttles);
        assertEquals(original.conquestArrivalConfig(),
                copy.conquestArrivalConfig());
        assertEquals(original.targetPlanetName, copy.targetPlanetName);
        assertEquals(original.targetIndustryId, copy.targetIndustryId);
        assertEquals(original.targetFactionId, copy.targetFactionId);
        assertEquals(original.contractId, copy.contractId);
        assertEquals(original.campaignEventId, copy.campaignEventId);
        assertEquals(original.campaignEventMarketId, copy.campaignEventMarketId);
        assertEquals(original.civiliansAtRisk, copy.civiliansAtRisk);
        assertEquals(original.campaignEventThreatSeed, copy.campaignEventThreatSeed);
        assertEquals(original.salvageBaseline, copy.salvageBaseline);
        assertEquals(original.salvageNegotiated, copy.salvageNegotiated);
        assertEquals(original.cashMultiplier, copy.cashMultiplier);
        assertEquals(original.contractSalvageBaseline, copy.contractSalvageBaseline);
        assertEquals(original.contractSalvageNegotiated, copy.contractSalvageNegotiated);
    }

    /**
     * The specific thing the old hand-written copy in {@code BriefingScreen} got
     * wrong: it used a constructor with no campaign-event parameters, so
     * re-negotiating salvage on an event mission would have blanked the lineage.
     * Unreachable in practice — negotiation is gated on a non-zero contract
     * salvage baseline, which no event mission has — but it cannot happen now.
     */
    @Test
    void renegotiatingSalvageDoesNotBlankTheEventLineage() {
        Mission original = Mission.builder()
                .id("silent-colony:7")
                .name("Silent Colony")
                .type(MissionType.EXTRACTION)
                .source(MissionSource.CAMPAIGN_EVENT)
                .risk(RiskLevel.HIGH)
                .requiredDrops(4)
                .campaignEventId(7L)
                .campaignEventMarketId(3)
                .civiliansAtRisk(180)
                .campaignEventThreatSeed(55L)
                .contractSalvageBaseline(60)
                .contractSalvageNegotiated(60)
                .build();

        Mission renegotiated = Mission.builder(original)
                .contractSalvageNegotiated(40)
                .cashMultiplier(110)
                .build();

        assertEquals(40, renegotiated.contractSalvageNegotiated);
        assertEquals(110, renegotiated.cashMultiplier & 0xFF);
        assertEquals(7L, renegotiated.campaignEventId);
        assertEquals(3, renegotiated.campaignEventMarketId);
        assertEquals(180, renegotiated.civiliansAtRisk);
        assertEquals(55L, renegotiated.campaignEventThreatSeed);
    }

    /**
     * The salvage setters take {@code int} so call sites stop writing
     * {@code (byte) 100}. Percentages run to 255, so the top of the range has to
     * survive the narrowing.
     */
    @Test
    void salvagePercentagesAboveOneTwentySevenSurviveTheNarrowing() {
        Mission mission = minimal()
                .salvageBaseline(200)
                .salvageNegotiated(255)
                .cashMultiplier(180)
                .contractSalvageBaseline(200)
                .contractSalvageNegotiated(128)
                .build();

        assertEquals(200, mission.salvageBaseline & 0xFF);
        assertEquals(255, mission.salvageNegotiated & 0xFF);
        assertEquals(180, mission.cashMultiplier & 0xFF);
        assertEquals(200, mission.contractSalvageBaseline & 0xFF);
        assertEquals(128, mission.contractSalvageNegotiated & 0xFF);
    }

    @Test
    void normalizationStillRunsOnTheBuiltMission() {
        Mission clamped = minimal()
                .requiredDrops(2)
                .employerShuttles(9)
                .build();
        assertEquals(2, clamped.employerShuttles,
                "the employer cannot cover more drops than the mission needs");

        Mission noSource = Mission.builder().id("id").build();
        assertEquals(MissionSource.GENERATED, noSource.source);

        Mission notARescue = minimal().civiliansAtRisk(500).build();
        assertEquals(0, notARescue.civiliansAtRisk,
                "civilian stakes only exist on a rescue");

        Mission noEvent = minimal().campaignEventMarketId(3)
                .campaignEventThreatSeed(55L).build();
        assertEquals(-1, noEvent.campaignEventMarketId,
                "event fields collapse without an event id");
        assertEquals(-1L, noEvent.campaignEventThreatSeed);
    }

}
