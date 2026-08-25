package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyNewsTest {

    @Test
    void latestShowsOnlyAlreadyLearnedFactsNewestFirst() {
        Fixture fixture = fixture();
        fixture.state.addChronicleChainOutcome(1L, ChainState.RESOLVED,
                ChronicleBand.INTIMATE, fixture.actor, fixture.target,
                fixture.market, -1, 10, 12);
        fixture.state.addChronicleChainRumor(2L, ChronicleBand.EPIC,
                fixture.actor, fixture.target, fixture.market, -1, 14, 18);
        fixture.state.addChronicleHouseDormancy(ChronicleBand.INTIMATE,
                fixture.actor, fixture.market, 19, 25);

        List<CompanyNews.Entry> entries = CompanyNews.latest(
                fixture.state, 20, 5, ignored -> "Jangala");

        assertEquals(2, entries.size());
        assertEquals(ChronicleEventType.ACTIVE_CHAIN_RUMOR, entries.get(0).type());
        assertEquals(ChronicleEventType.CHAIN_OUTCOME, entries.get(1).type());
        assertEquals("House Current", entries.get(0).actorName());
        assertEquals("Jangala", entries.get(0).marketName());
    }

    @Test
    void limitIsHonestAndMalformedSubjectsAreSkipped() {
        Fixture fixture = fixture();
        fixture.state.addChronicleHouseDormancy(ChronicleBand.INTIMATE,
                fixture.actor, fixture.market, 1, 1);
        fixture.state.addChronicleHouseDormancy(ChronicleBand.INTIMATE,
                fixture.target, fixture.market, 2, 2);
        fixture.state.addChronicleHouseDormancy(ChronicleBand.INTIMATE,
                999L, fixture.market, 3, 3);

        List<CompanyNews.Entry> entries = CompanyNews.latest(
                fixture.state, 5, 1, ignored -> null);

        assertEquals(1, entries.size());
        assertEquals("House Other", entries.get(0).actorName());
    }

    @Test
    void absentStateAndNonPositiveLimitAreEmpty() {
        assertTrue(CompanyNews.latest(null, 1, 5, ignored -> null).isEmpty());
        assertTrue(CompanyNews.latest(new CampaignState(), 1, 0,
                ignored -> null).isEmpty());
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("jangala");
        int faction = state.factionRegistry.intern("hegemony");
        long actor = state.addHouse(market, faction, HouseFlavor.CORPORATE,
                HouseRank.TIER_3, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Current");
        long target = state.addHouse(market, faction, HouseFlavor.FEUDAL,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.FALLEN_NOBLE, "House Other");
        return new Fixture(state, market, actor, target);
    }

    private record Fixture(CampaignState state, int market, long actor, long target) { }
}
