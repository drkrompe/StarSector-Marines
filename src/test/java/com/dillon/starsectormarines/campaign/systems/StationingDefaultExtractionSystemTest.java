package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StrandedDetachment;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingDefaultExtractionSystemTest {

    @Test
    void defaultedStationingSpawnsOneLinkedRecoveryOffer() {
        CampaignState state = stationing(ContractState.DEFAULTED);
        StationingDefaultExtractionSystem system = new StationingDefaultExtractionSystem();

        system.tick(state, 40);
        system.tick(state, 41);

        assertEquals(2, state.contractCount);
        int row = 1;
        assertEquals(ContractType.EXTRACTION, ContractType.fromByte(state.contractType[row]));
        assertEquals(ContractState.OFFERED, ContractState.fromByte(state.contractState[row]));
        assertEquals(state.contractId[0], state.contractSourceContractId[row]);
        assertEquals(-1L, state.contractTargetHouseId[row]);
        assertEquals(1, state.contractPhasesTotal[row] & 0xFF);
        assertEquals(7, state.contractMarketId[row]);
        assertEquals(2_200, state.contractBasePayout[row]);
        assertEquals(25, state.contractSalvageBaseline[row] & 0xFF);
        assertEquals(40, state.contractAcceptedTick[row]);
    }

    @Test
    void ignoresNonDefaultedAndEmptyAssignments() {
        CampaignState active = stationing(ContractState.ACTIVE);
        CampaignState empty = stationing(ContractState.DEFAULTED);
        empty.contractMarinesCommitted[0] = 0;
        empty.contractCaptainId[0] = -1;

        StationingDefaultExtractionSystem system = new StationingDefaultExtractionSystem();
        system.tick(active, 40);
        system.tick(empty, 40);

        assertEquals(1, active.contractCount);
        assertEquals(1, empty.contractCount);
    }

    /** A breach where the company may land freely waits for a pickup, not a fight. */
    @Test
    void aBreachOnAFriendlyMarketWaitsForAPickup() {
        Named named = new Named();
        StationingDefaultExtractionSystem system =
                new StationingDefaultExtractionSystem(() -> named.roster, id -> false);

        system.tick(named.state, 40);
        system.tick(named.state, 41);

        assertEquals(1, named.state.contractCount);
        assertTrue(StrandedDetachment.isStranded(named.state, 0));
        assertEquals(40 + StrandedDetachment.RECOVERY_DAYS,
                named.state.contractStrandedUntilTick[0]);
        assertTrue(named.squad.stationed());
    }

    @Test
    void aBreachOnAHostileMarketIsFoughtOut() {
        Named named = new Named();

        new StationingDefaultExtractionSystem(() -> named.roster, id -> true)
                .tick(named.state, 40);

        assertEquals(2, named.state.contractCount);
        assertEquals(ContractType.EXTRACTION, ContractType.fromByte(named.state.contractType[1]));
        assertFalse(StrandedDetachment.isStranded(named.state, 0));
    }

    /** Unreadable hostility keeps the old, fought recovery rather than guessing. */
    @Test
    void unreadableHostilityKeepsTheFoughtExtraction() {
        Named named = new Named();

        new StationingDefaultExtractionSystem(() -> named.roster, id -> null)
                .tick(named.state, 40);

        assertEquals(2, named.state.contractCount);
    }

    /** The breached employer still answers for the breach once the pickup is made. */
    @Test
    void collectingABreachedDetachmentSettlesTheBreach() {
        Named named = new Named();
        new StationingDefaultExtractionSystem(() -> named.roster, id -> false)
                .tick(named.state, 40);

        assertEquals(6, StrandedDetachment.pickup(named.state, named.state.contractId[0],
                named.roster, 45, "Jangala"));

        assertFalse(named.squad.stationed());
        assertEquals(Status.ACTIVE, named.captain.status());
        assertEquals(1, named.state.repCount);
    }

    private static final class Named {
        final CampaignState state = new CampaignState();
        final MarineRoster roster = new MarineRoster();
        final MarineCaptain captain =
                new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        final MarineSquad squad;

        Named() {
            roster.add(captain);
            roster.ensureActiveSoldiers(6);
            squad = roster.squads().get(0);
            long contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                    ContractState.DEFAULTED, 10, 100, -1, (byte) 0,
                    state.captainRegistry.intern(captain.id()),
                    state.marketRegistry.intern("jangala"), -1, 0, 2_200,
                    (byte) 25, (byte) 25, (byte) 100);
            state.contractMarinesCommitted[0] = 6;
            assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
            captain.setStatus(Status.GARRISONED);
        }
    }

    private static CampaignState stationing(ContractState contractState) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON, contractState,
                10, 100, -1, (byte) 0, 3, 7, -1,
                0, 2_200, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        state.contractCaptainId[0] = 3;
        return state;
    }
}
