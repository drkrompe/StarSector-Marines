package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractTableCompactor;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.Posting;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The post/release pair: stationing with the commercial half removed. */
class PostingServiceTest {

    private static final String MARKET = "jangala";
    private static final int DAY = 40;

    @Test
    void postBindsTheDetachmentAndWritesARowWithNoEmployer() {
        Fixture fixture = fixture();

        long contractId = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);

        assertTrue(contractId >= 0L);
        CampaignState state = fixture.state;
        int row = state.contractIndex(contractId);
        assertTrue(Posting.isPosting(state, row));
        assertEquals(ContractType.GARRISON, ContractType.fromByte(state.contractType[row]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[row]));
        assertEquals(Posting.NO_PATRON, state.contractPatronHouseId[row]);
        assertEquals(-1, state.contractExpiresTick[row], "a posting has no term");
        assertEquals(-1, state.contractOfferExpiresTick[row], "it was never offered");
        assertEquals(0, state.contractRetainerPerMonth[row]);
        assertEquals(0, state.contractBasePayout[row]);
        assertEquals(0, state.contractPhasesTotal[row]);
        assertEquals(0, state.contractSalvageBaseline[row] & 0xFF);
        assertEquals(0, state.contractSalvageNegotiated[row] & 0xFF);
        assertEquals(DAY, state.contractAcceptedTick[row]);
        assertEquals(-1, state.contractNextIncidentTick[row]);
        assertEquals(StationingIncidentType.NONE,
                StationingIncidentType.fromByte(state.contractIncidentType[row]));
        assertEquals(GarrisonDefenseTriggerType.NONE, GarrisonDefenseTriggerType.fromByte(
                state.contractDefenseTriggerType[row]));
        assertEquals(state.marketRegistry.intern(MARKET), state.contractMarketId[row]);
        assertEquals(fixture.captain.id(),
                state.captainRegistry.get(state.contractCaptainId[row]));
        assertEquals(2 * MarineSquad.CAPACITY, state.contractMarinesCommitted[row]);
        assertEquals(fixture.squadIds.size(),
                fixture.roster.squadsStationedOn(contractId).size());
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertTrue(fixture.captain.commendations().stream()
                        .anyMatch(line -> line.contains("Posted to " + MARKET)),
                fixture.captain.commendations().toString());
    }

    @Test
    void aSecondPostingAtTheSameMarketIsRefused() {
        Fixture fixture = fixture();
        long first = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);
        MarineCaptain second = new MarineCaptain("Second", null, Rank.CAPTAIN, 0f);
        fixture.roster.add(second);
        MarineSquad spare = fixture.roster.squads().get(2);

        long refused = PostingService.post(fixture.state, MARKET, fixture.roster,
                second, List.of(spare.id()), DAY + 1);

        assertTrue(first >= 0L);
        assertEquals(-1L, refused);
        assertEquals(1, fixture.state.contractCount, "no second row was appended");
        assertFalse(spare.stationed());
        assertEquals(Status.ACTIVE, second.status());
    }

    @Test
    void aPostingAtAnotherMarketIsAllowedAlongsideIt() {
        Fixture fixture = fixture();
        PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);
        MarineCaptain second = new MarineCaptain("Second", null, Rank.CAPTAIN, 0f);
        fixture.roster.add(second);
        MarineSquad spare = fixture.roster.squads().get(2);

        long elsewhere = PostingService.post(fixture.state, "culann", fixture.roster,
                second, List.of(spare.id()), DAY);

        assertTrue(elsewhere >= 0L);
        assertTrue(spare.stationed());
    }

    @Test
    void aRefusedBindLeavesNoLiveRowBehind() {
        Fixture fixture = fixture();

        // The same squad twice is exactly what bindStationing refuses.
        MarineSquad squad = fixture.roster.squads().get(0);
        long refused = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, List.of(squad.id(), squad.id()), DAY);

        assertEquals(-1L, refused);
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertFalse(squad.stationed());
        assertEquals(-1, Posting.activeRowAt(fixture.state,
                fixture.state.marketRegistry.intern(MARKET)));
        assertEquals(1, ContractTableCompactor.removeTerminal(fixture.state),
                "the retired row is compactable");
    }

    @Test
    void releaseReturnsThePersonnelAndSettlesTheRowCompleted() {
        Fixture fixture = fixture();
        long contractId = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);

        assertTrue(PostingService.release(fixture.state, contractId,
                fixture.roster, DAY + 12));

        int row = fixture.state.contractIndex(contractId);
        assertEquals(ContractState.COMPLETED,
                ContractState.fromByte(fixture.state.contractState[row]),
                "COMPLETED, not ABANDONED: nobody was walked out on");
        assertEquals(0, fixture.state.contractMarinesCommitted[row]);
        assertEquals(-1, fixture.state.contractCaptainId[row]);
        assertTrue(fixture.roster.squadsStationedOn(contractId).isEmpty());
        for (String squadId : fixture.squadIds) {
            assertFalse(fixture.roster.squadById(squadId).stationed());
        }
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertTrue(fixture.captain.commendations().stream()
                        .anyMatch(line -> line.contains("Released from the posting at "
                                + MARKET)),
                fixture.captain.commendations().toString());
    }

    @Test
    void releaseWritesNoReputationFactAtAll() {
        Fixture fixture = fixture();
        long contractId = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);
        int mrbBefore = fixture.state.playerMrbRep;

        assertTrue(PostingService.release(fixture.state, contractId,
                fixture.roster, DAY + 12));

        assertEquals(mrbBefore, fixture.state.playerMrbRep);
        assertEquals(0, fixture.state.repCount,
                "a posting has no employer to hold an opinion");
    }

    @Test
    void releaseIsRefusedWhileADefenceIsArmed() {
        Fixture fixture = fixture();
        long contractId = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);
        int marketSlot = fixture.state.marketRegistry.intern(MARKET);

        assertEquals(1, GarrisonDefenseTrigger.arm(fixture.state, 88L, marketSlot,
                GarrisonDefenseTriggerType.VANILLA_RAID, -1L,
                fixture.state.factionRegistry.intern("pirates"), 120f, DAY + 3));
        assertNotNull(GarrisonDefensePayload.from(fixture.state, contractId,
                fixture.roster), "the defence is really armed");

        assertFalse(PostingService.release(fixture.state, contractId,
                fixture.roster, DAY + 4),
                "an armed defence is real unpaid work (contracts-nouns.md, law 5)");
        assertEquals(fixture.squadIds.size(),
                fixture.roster.squadsStationedOn(contractId).size());
        assertEquals(Status.GARRISONED, fixture.captain.status());
    }

    @Test
    void releaseIsRefusedTwiceAndForARowThatIsNotAPosting() {
        Fixture fixture = fixture();
        long contractId = PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY);
        assertTrue(PostingService.release(fixture.state, contractId, fixture.roster, DAY + 1));
        assertFalse(PostingService.release(fixture.state, contractId, fixture.roster, DAY + 2));

        long employed = fixture.state.addContract(7L, -1L, -1L, ContractType.GARRISON,
                ContractState.ACTIVE, DAY, DAY + 60, -1, (byte) 0, -1,
                fixture.state.marketRegistry.intern(MARKET), -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        assertFalse(PostingService.release(fixture.state, employed, fixture.roster, DAY + 2),
                "a patron's Garrison is withdrawn from, not released");
    }

    @Test
    void postRefusesTheArgumentsItCannotHonour() {
        Fixture fixture = fixture();
        assertEquals(-1L, PostingService.post(null, MARKET, fixture.roster,
                fixture.captain, fixture.squadIds, DAY));
        assertEquals(-1L, PostingService.post(fixture.state, null, fixture.roster,
                fixture.captain, fixture.squadIds, DAY));
        assertEquals(-1L, PostingService.post(fixture.state, "  ", fixture.roster,
                fixture.captain, fixture.squadIds, DAY));
        assertEquals(-1L, PostingService.post(fixture.state, MARKET, null,
                fixture.captain, fixture.squadIds, DAY));
        assertEquals(-1L, PostingService.post(fixture.state, MARKET, fixture.roster,
                null, fixture.squadIds, DAY));
        assertEquals(-1L, PostingService.post(fixture.state, MARKET, fixture.roster,
                fixture.captain, null, DAY));
        assertEquals(-1L, PostingService.post(fixture.state, MARKET, fixture.roster,
                new MarineCaptain("Stranger", null, Rank.CAPTAIN, 0f),
                fixture.squadIds, DAY), "a captain the roster never heard of");
        assertEquals(0, fixture.state.contractCount);
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Hale", null, Rank.CAPTAIN, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(3 * MarineSquad.CAPACITY);
        List<String> squadIds = List.of(roster.squads().get(0).id(),
                roster.squads().get(1).id());
        return new Fixture(state, roster, captain, squadIds);
    }

    private record Fixture(CampaignState state, MarineRoster roster,
                           MarineCaptain captain, List<String> squadIds) {}
}
