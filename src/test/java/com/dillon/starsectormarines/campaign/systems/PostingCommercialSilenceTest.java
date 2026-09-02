package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractReputation;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.Posting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A posting produces no credibility, no relationship, and no payout fact — ever
 * ({@code meta-progression.md}: the polity is never a client).
 *
 * <p>Each case here is a shipped mechanism already believed to be patron-blind. That
 * belief is exactly what wants pinning: the guards are one-line sentinel checks in
 * three different files, and a posting is the first row that exercises any of them.
 */
class PostingCommercialSilenceTest {

    private static final String MARKET = "player_colony";

    @Test
    void ninetyDaysOfRetainerTicksPayNothing() {
        CampaignState state = posting();
        List<Integer> payments = new ArrayList<>();
        ContractRetainerSystem system = new ContractRetainerSystem(credits -> {
            payments.add(credits);
            return true;
        });

        for (int day = 0; day <= 90; day++) system.tick(state, day);

        assertEquals(List.of(), payments, "there is no retainer to catch up on");
    }

    /** The control: the identical row with an employer and a retainer does pay. */
    @Test
    void theSameRowWithAnEmployerPays() {
        CampaignState state = new CampaignState();
        state.addContract(7L, -1L, -1L, ContractType.GARRISON, ContractState.ACTIVE,
                0, -1, -1, (byte) 0, -1, state.marketRegistry.intern(MARKET), -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        List<Integer> payments = new ArrayList<>();
        ContractRetainerSystem system = new ContractRetainerSystem(credits -> {
            payments.add(credits);
            return true;
        });

        for (int day = 0; day <= 90; day++) system.tick(state, day);

        assertEquals(List.of(1_000, 1_000, 1_000), payments);
    }

    @Test
    void everySettlementConsequenceOnAPostingIsSilent() {
        CampaignState state = posting();
        long contractId = state.contractId[0];
        state.playerMrbRep = 55;

        ContractReputation.failedForContract(state, contractId, -1, 10);
        ContractReputation.lapsedForContract(state, contractId, 11);
        ContractReputation.abandonedForContract(state, contractId, 12);
        ContractReputation.completedForContract(state, contractId, +1, 13);
        ContractReputation.employerBreachedForContract(state, contractId, 14);

        assertEquals(55, state.playerMrbRep, "credibility is what other people think");
        assertEquals(0, state.repCount, "no relationship row was opened for nobody");
    }

    /** The control: the same five calls against an employer do move both columns. */
    @Test
    void theSameCallsAgainstAnEmployerAreHeard() {
        CampaignState state = new CampaignState();
        long contractId = state.addContract(7L, -1L, -1L, ContractType.GARRISON,
                ContractState.ACTIVE, 0, -1, -1, (byte) 0, -1,
                state.marketRegistry.intern(MARKET), -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.playerMrbRep = 55;

        ContractReputation.failedForContract(state, contractId, -1, 10);

        assertEquals(1, state.repCount);
        assertEquals(55 + ContractReputation.FAILED_MRB_DELTA, state.playerMrbRep);
    }

    @Test
    void theMonthlyDefaultRollNeverReachesAPosting() {
        CampaignState state = posting();
        // A roll that defaults everything it is asked about, so a reached row must fall.
        StationingDefaultSystem system = new StationingDefaultSystem(
                (contractId, checkpointDay, chancePercent) -> true);

        for (int day = 0; day <= 365; day++) system.tick(state, day);

        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]),
                "nobody can breach a contract nobody made");
        assertTrue(Posting.isPosting(state, 0));
    }

    /** The control: the identical row with an employer falls on the first checkpoint. */
    @Test
    void theSameRowWithAnEmployerDefaults() {
        CampaignState state = new CampaignState();
        state.addContract(7L, -1L, -1L, ContractType.GARRISON, ContractState.ACTIVE,
                0, -1, -1, (byte) 0, -1, state.marketRegistry.intern(MARKET), -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        StationingDefaultSystem system = new StationingDefaultSystem(
                (contractId, checkpointDay, chancePercent) -> true);

        for (int day = 0; day <= 365; day++) system.tick(state, day);

        assertEquals(ContractState.DEFAULTED,
                ContractState.fromByte(state.contractState[0]));
    }

    private static CampaignState posting() {
        CampaignState state = new CampaignState();
        state.addContract(Posting.NO_PATRON, -1L, -1L, ContractType.GARRISON,
                ContractState.ACTIVE, 0, -1, -1, (byte) 0, -1,
                state.marketRegistry.intern(MARKET), -1, 0, 0,
                (byte) 0, (byte) 0, (byte) 100);
        state.contractMarinesCommitted[0] = 24;
        state.contractLastRetainerTick[0] = 0;
        state.contractLastDefaultCheckTick[0] = 0;
        return state;
    }
}
