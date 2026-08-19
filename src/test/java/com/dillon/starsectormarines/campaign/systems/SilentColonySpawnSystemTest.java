package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.dillon.starsectormarines.campaign.SilentColonyEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentColonySpawnSystemTest {

    @Test
    void firstEpochInternsOnlySelectedSiteAndFreezesTierTerms() {
        CampaignState state = new CampaignState();
        TestSites sites = new TestSites(
                site("scattered", 1), site("vast", 4));
        SilentColonySpawnSystem system = new SilentColonySpawnSystem(sites);

        system.tick(state, 89);
        assertEquals(0, state.eventCount);

        SilentColonySpawnSystem.Site expected =
                SilentColonySpawnSystem.selectSite(
                        state, sites.eligibleSites(), 0);
        system.tick(state, 90);

        assertEquals(1, state.eventCount);
        assertEquals(1, state.marketRegistry.size());
        assertEquals(CampaignEventType.SILENT_COLONY,
                CampaignEventType.fromByte(state.eventType[0]));
        assertEquals(expected.marketId,
                state.marketRegistry.get(state.eventMarketId[0]));
        assertEquals(20 + 10 * expected.ruinTier,
                state.eventSuppliesRequired[0]);
        assertEquals(10 + 5 * expected.ruinTier,
                state.eventFuelRequired[0]);
        assertEquals(4 + 2 * expected.ruinTier,
                state.eventCiviliansAtRisk[0]);
        assertEquals(SilentColonySpawnSystem.siteKey(expected.marketId),
                state.eventTriggerKey[0]);
        assertEquals(SilentColonySpawnSystem.threatSeed(
                        state.eventTriggerKey[0]),
                state.eventColonyThreatSeed[0]);
    }

    @Test
    void candidateOrderCannotChangeSelectedSiteIdentity() {
        List<SilentColonySpawnSystem.Site> forward = Arrays.asList(
                site("alpha", 1), site("beta", 2), site("gamma", 4));
        List<SilentColonySpawnSystem.Site> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        SilentColonySpawnSystem.Site first = SilentColonySpawnSystem.selectSite(
                new CampaignState(), forward, 3);
        SilentColonySpawnSystem.Site second = SilentColonySpawnSystem.selectSite(
                new CampaignState(), reverse, 3);

        assertEquals(first.marketId, second.marketId);
        assertEquals(first.ruinTier, second.ruinTier);
    }

    @Test
    void epochAndSiteIdentityPreventSameEpochAndSourceReplay() {
        CampaignState state = new CampaignState();
        SilentColonySpawnSystem system = new SilentColonySpawnSystem(
                new TestSites(site("alpha", 1), site("beta", 2)));

        system.tick(state, 90);
        String firstSite = selectedMarket(state, 0);
        SilentColonyEvent.refuse(state, state.eventId[0], 90);

        system.tick(state, 91);
        system.tick(state, 179);
        assertEquals(1, state.eventCount);

        system.tick(state, 180);
        assertEquals(2, state.eventCount);
        assertNotEquals(firstSite, selectedMarket(state, 1));
    }

    @Test
    void exhaustedOneShotSiteDoesNotRecurInLaterEpoch() {
        CampaignState state = new CampaignState();
        SilentColonySpawnSystem system = new SilentColonySpawnSystem(
                new TestSites(site("alpha", 3)));
        system.tick(state, 90);
        SilentColonyEvent.refuse(state, state.eventId[0], 90);

        system.tick(state, 180);
        system.tick(state, 400);

        assertEquals(1, state.eventCount);
    }

    @Test
    void commonOpenEventBlocksSiteWithoutInterningIt() {
        CampaignState state = new CampaignState();
        int rescueMarket = state.marketRegistry.intern("rescue");
        long rescue = CivilianRescueEvent.prepare(state, 4L, rescueMarket,
                80, 100, 25, 15, 100);
        assertTrue(rescue > 0L);
        int registrySize = state.marketRegistry.size();

        new SilentColonySpawnSystem(new TestSites(site("dead-site", 4)))
                .tick(state, 90);

        assertEquals(1, state.eventCount);
        assertEquals(registrySize, state.marketRegistry.size());
        assertEquals(CampaignEventState.PENDING_CHOICE,
                CampaignEventState.fromByte(state.eventState[0]));
    }

    @Test
    void lateFirstTickCreatesOnlyCurrentEpochAndStableKeys() {
        CampaignState state = new CampaignState();
        SilentColonySpawnSystem system = new SilentColonySpawnSystem(
                new TestSites(site("alpha", 2), site("beta", 3)));

        system.tick(state, 400);
        system.tick(state, 400);

        assertEquals(1, state.eventCount);
        String marketId = selectedMarket(state, 0);
        assertEquals(SilentColonySpawnSystem.siteKey(marketId),
                state.eventTriggerKey[0]);
        assertEquals(SilentColonySpawnSystem.selectionScore(3, marketId),
                SilentColonySpawnSystem.selectionScore(3, marketId));
        assertNotEquals(SilentColonySpawnSystem.selectionScore(2, marketId),
                SilentColonySpawnSystem.selectionScore(3, marketId));
    }

    @Test
    void malformedCandidatesAndTermsFailClosed() {
        CampaignState state = new CampaignState();
        SilentColonySpawnSystem system = new SilentColonySpawnSystem(
                new TestSites(null, site(null, 2), site("", 2),
                        site("low", 0), site("high", 5)));

        system.tick(state, 90);

        assertEquals(0, state.eventCount);
        assertEquals(0, state.marketRegistry.size());
        assertEquals(-1L, SilentColonySpawnSystem.prepareEvent(
                state, -1, 2, 90));
    }

    @Test
    void sourcePolicyRequiresADeadSiteRatherThanRuinsOnALiveColony() {
        assertEquals(4, SilentColonySpawnSystem.eligibleTier(
                true, true, true, false, false, 4));
        assertEquals(2, SilentColonySpawnSystem.eligibleTier(
                false, true, true, true, false, 0));
        assertEquals(2, SilentColonySpawnSystem.eligibleTier(
                false, true, true, false, true, 0));
        assertEquals(0, SilentColonySpawnSystem.eligibleTier(
                false, true, true, false, false, 4));
        assertEquals(0, SilentColonySpawnSystem.eligibleTier(
                true, false, true, false, false, 4));
        assertEquals(0, SilentColonySpawnSystem.eligibleTier(
                true, true, false, false, false, 4));
    }

    private static SilentColonySpawnSystem.Site site(String id, int tier) {
        return new SilentColonySpawnSystem.Site(id, tier);
    }

    private static String selectedMarket(CampaignState state, int eventRow) {
        return state.marketRegistry.get(state.eventMarketId[eventRow]);
    }

    private static final class TestSites
            implements SilentColonySpawnSystem.SiteSource {
        private final List<SilentColonySpawnSystem.Site> sites;

        TestSites(SilentColonySpawnSystem.Site... sites) {
            this.sites = Arrays.asList(sites);
        }

        @Override
        public List<SilentColonySpawnSystem.Site> eligibleSites() {
            return sites;
        }
    }
}
