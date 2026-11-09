package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.campaign.raid.MarketRaidMissionKey;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketRaidMissionFactoryTest {

    @Test
    void aMarketRaidIsARaidWithNoContractAndNoEmployer() {
        Mission mission = MarketRaidMissionFactory.create(3, "jangala", "Jangala", "hegemony", "mining",
                new MarketGarrison(400f, 0.3f, 0.1f, 1f));

        assertEquals(MissionSource.MARKET_RAID, mission.source);
        assertEquals(MissionType.RAID, mission.type);
        assertEquals(new MarketRaidMissionKey(3, "jangala"), MarketRaidMissionKey.parse(mission.id));
        assertEquals("mining", mission.targetIndustryId);
        assertEquals("hegemony", mission.targetFactionId);
        assertEquals(-1L, mission.contractId);
        assertEquals(0, mission.employerShuttles);
        assertTrue(MissionForceEnvelope.allowsUnderstrength(mission), "the player's own raid may go understrength");
    }

    @Test
    void theTierIsReadFromTheWholeHeadcountAboveRaidsFloor() {
        MarketGarrison fortress = new MarketGarrison(2_000f, 0.5f, 0.3f, 1f);
        assertEquals(OperationTier.clampTo(OperationTierForStrength.forGroundStrength(
                        DefenderRoster.wholeGarrisonCount(fortress)), MissionType.RAID.tierFloor),
                MarketRaidMissionFactory.tierFor(fortress));
    }

    /** The industry's Raid site's line closes the briefing: what the target is to the whole planet. */
    @Test
    void theChokepointLineClosesTheBriefing() {
        Mission plain = MarketRaidMissionFactory.create(3, "jangala", "Jangala", "hegemony", "mining",
                MarketGarrison.NONE);
        Mission hub = MarketRaidMissionFactory.create(3, "jangala", "Jangala", "hegemony", "mining",
                MarketGarrison.NONE, "It handles the output of 7 digs.");
        assertEquals(plain.flavor + " It handles the output of 7 digs.", hub.flavor);
    }

    @Test
    void nothingToRaidBuildsNothing() {
        assertNull(MarketRaidMissionFactory.create(0, "jangala", "Jangala", "hegemony", null, MarketGarrison.NONE));
        assertNull(MarketRaidMissionFactory.create(1, null, "Jangala", "hegemony", null, MarketGarrison.NONE));
    }
}
