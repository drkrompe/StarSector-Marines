package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.PolityDefenceResolution;
import com.dillon.starsectormarines.campaign.systems.PolityRaidLookup;
import com.dillon.starsectormarines.campaign.systems.RaidEnder;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem.RaidThreat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The polity-defence writeback: what a won, lost, and replayed defence do to the ledger
 * and to vanilla's raid.
 *
 * <p>{@code MissionResolver.apply} reaches its campaign state through
 * {@code CampaignStateScript.getInstance()}, which needs a live sector, so the branch is
 * driven at {@code settlePolityDefence} — the seam that takes the state explicitly and
 * carries the whole decision, including the one write to vanilla.
 */
class MissionResolverPolityDefenceTest {

    private static final long EVENT_KEY = 88L;

    /** Records what was asked of vanilla, so "exactly once" is measurable. */
    private static final class RecordingEnder implements RaidEnder {
        final List<String> calls = new ArrayList<>();

        @Override
        public int endRaidsTargeting(String marketId, String factionId) {
            calls.add(marketId + "/" + factionId);
            return 1;
        }
    }

    private final RecordingEnder ender = new RecordingEnder();

    @AfterEach
    void restoreLiveSeams() {
        MissionResolver.setRaidEnder(null);
        MissionResolver.setPolityRaidLookup(null);
    }

    private CampaignState armed() {
        CampaignState state = new CampaignState();
        int marketSlot = state.marketRegistry.intern("jangala");
        int factionSlot = state.factionRegistry.intern("pirates");
        List<RaidThreat> live = List.of(new RaidThreat(EVENT_KEY, marketSlot, factionSlot, 60f));
        MissionResolver.setRaidEnder(ender);
        MissionResolver.setPolityRaidLookup(
                (s, eventKey) -> PolityRaidLookup.attackerIn(s, live, eventKey));
        return state;
    }

    private static MissionOutcome outcome(CampaignState state, boolean victory) {
        Mission mission = PolityDefenceMissionFactory.create(
                new RaidThreat(EVENT_KEY, state.marketRegistry.intern("jangala"),
                        state.factionRegistry.intern("pirates"), 60f),
                state.marketRegistry.intern("jangala"), "pirates", "Jangala", "player", 1f);
        return MissionOutcome.builder().mission(mission).victory(victory).build();
    }

    @Test
    void aWonDefenceSendsTheRaidHomeOnceAndRecordsIt() {
        CampaignState state = armed();

        assertEquals(PolityDefenceResolution.Result.RESOLVED_WON,
                MissionResolver.settlePolityDefence(state, outcome(state, true)));

        assertEquals(List.of("jangala/pirates"), ender.calls);
        assertTrue(state.hasPolityDefence(EVENT_KEY));
    }

    @Test
    void aLostDefenceWritesTheRecordAndNothingToVanilla() {
        CampaignState state = armed();

        assertEquals(PolityDefenceResolution.Result.RESOLVED_LOST,
                MissionResolver.settlePolityDefence(state, outcome(state, false)));

        assertEquals(List.of(), ender.calls);
        assertTrue(state.hasPolityDefence(EVENT_KEY));
        assertEquals(0, state.polityDefenceWon[0]);
    }

    @Test
    void aReplayedWinEndsNoFurtherRaids() {
        CampaignState state = armed();
        MissionResolver.settlePolityDefence(state, outcome(state, true));

        assertEquals(PolityDefenceResolution.Result.ALREADY_SETTLED,
                MissionResolver.settlePolityDefence(state, outcome(state, true)));

        assertEquals(1, ender.calls.size(), "the raid is sent home exactly once");
        assertEquals(1, state.polityDefenceCount);
    }

    /** The raid may have gone away between the battle and the debrief. */
    @Test
    void aWinAgainstARaidNoLongerLiveEndsNothingButStillRecords() {
        CampaignState state = new CampaignState();
        state.marketRegistry.intern("jangala");
        state.factionRegistry.intern("pirates");
        MissionResolver.setRaidEnder(ender);
        MissionResolver.setPolityRaidLookup(PolityRaidLookup.NONE);

        assertEquals(PolityDefenceResolution.Result.RESOLVED_WON,
                MissionResolver.settlePolityDefence(state, outcome(state, true)));

        assertEquals(List.of(), ender.calls);
        assertTrue(state.hasPolityDefence(EVENT_KEY));
    }

    @Test
    void aMissionWithNoReadableKeySettlesNothing() {
        CampaignState state = armed();
        MissionOutcome foreign = MissionOutcome.builder()
                .mission(Mission.builder()
                        .id("not-a-polity-key")
                        .name("Colony Defence")
                        .type(MissionType.ASSAULT)
                        .source(MissionSource.POLITY_DEFENCE)
                        .contractId(-1L)
                        .build())
                .victory(true)
                .build();

        assertNull(MissionResolver.settlePolityDefence(state, foreign));

        assertEquals(List.of(), ender.calls);
        assertEquals(0, state.polityDefenceCount);
    }

    /** The lookup's own decision: the right raid out of several, by key. */
    @Test
    void theAttackerIsTheFactionBehindThatRaidKey() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("jangala");
        int pirates = state.factionRegistry.intern("pirates");
        int pathers = state.factionRegistry.intern("luddic_path");
        List<RaidThreat> live = List.of(
                new RaidThreat(11L, market, pathers, 20f),
                new RaidThreat(EVENT_KEY, market, pirates, 60f));

        assertEquals("pirates", PolityRaidLookup.attackerIn(state, live, EVENT_KEY));
        assertEquals("luddic_path", PolityRaidLookup.attackerIn(state, live, 11L));
        assertNull(PolityRaidLookup.attackerIn(state, live, 12L));
        assertNull(PolityRaidLookup.attackerIn(state, live, 0L));
        assertNull(PolityRaidLookup.attackerIn(state, null, EVENT_KEY));
        assertNull(PolityRaidLookup.attackerIn(null, live, EVENT_KEY));
    }
}
