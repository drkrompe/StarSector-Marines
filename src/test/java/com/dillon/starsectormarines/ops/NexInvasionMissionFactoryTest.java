package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.campaign.nexerelin.NexInvasionMissionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NexInvasionMissionFactoryTest {

    @Test
    void anInvasionIsAConquestWithNoContractAndNoEmployer() {
        Mission mission = NexInvasionMissionFactory.create(5, "jangala", "Jangala", "hegemony",
                new MarketGarrison(400f, 0.3f, 0.1f, 1f));

        assertEquals(MissionSource.NEX_INVASION, mission.source);
        assertEquals(MissionType.CONQUEST, mission.type);
        assertEquals(new NexInvasionMissionKey(5, "jangala"), NexInvasionMissionKey.parse(mission.id));
        assertEquals("Jangala", mission.targetPlanetName);
        assertEquals("hegemony", mission.targetFactionId);
        assertEquals(-1L, mission.contractId);
        assertEquals(0, mission.employerShuttles);
        assertEquals(mission.tier.drops, mission.requiredDrops);
    }

    @Test
    void theTierIsReadFromTheWholeHeadcount() {
        MarketGarrison fortress = new MarketGarrison(2_000f, 0.5f, 0.3f, 1f);
        assertEquals(OperationTier.clampTo(OperationTierForStrength.forGroundStrength(
                        DefenderRoster.wholeGarrisonCount(fortress)), MissionType.CONQUEST.tierFloor),
                NexInvasionMissionFactory.tierFor(fortress));
        assertEquals(OperationTier.clampTo(OperationTier.FIRST_CONTRACT, MissionType.CONQUEST.tierFloor),
                NexInvasionMissionFactory.tierFor(MarketGarrison.NONE),
                "a weak garrison is a light defence on Conquest's floor, not a refused invasion");
    }

    @Test
    void nothingToInvadeBuildsNothing() {
        assertNull(NexInvasionMissionFactory.create(0, "jangala", "Jangala", "hegemony", MarketGarrison.NONE));
        assertNull(NexInvasionMissionFactory.create(1, null, "Jangala", "hegemony", MarketGarrison.NONE));
    }
}
