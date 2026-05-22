package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.ContractLifecycleSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the offer-expiry data flow:
 *   addContract(OFFERED, offerExpiresTick=D) → tick(day=D-1) → still OFFERED
 *   → tick(day=D)   → flips to EXPIRED (soft-delete, row + id->index preserved)
 *
 * Plus {@link CampaignState#contractDaysLeft} semantics, which the dossier UI
 * binds to for the days-left bar.
 */
public class ContractOfferExpiryTest {

    @Test
    public void offeredContractFlipsToExpiredOnDayDayOfExpiry() {
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.TIME_RUSHED, "Test Patron");
        long targetId = state.addHouse(2, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Test Target");

        int spawnDay = 100;
        int offerExpiresTick = spawnDay + 3; // lapses on day 103
        long contractId = state.addContract(
                patronId, targetId, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                spawnDay, -1, offerExpiresTick,
                (byte) 1, -1, 0, -1, 25_000, 0,
                (byte) 60, (byte) 60, (byte) 100);

        ContractLifecycleSystem sys = new ContractLifecycleSystem();

        // Day 102: still inside the window → still OFFERED.
        sys.tick(state, 102);
        int row = state.contractIndex(contractId);
        assertTrue(row >= 0, "id->index lookup must still resolve");
        assertEquals(ContractState.OFFERED, ContractState.fromByte(state.contractState[row]));

        // Day 103: day == offerExpiresTick → flips to EXPIRED.
        sys.tick(state, 103);
        int rowAfter = state.contractIndex(contractId);
        assertEquals(row, rowAfter, "id->index lookup must survive soft-delete (architecture invariant)");
        assertEquals(ContractState.EXPIRED, ContractState.fromByte(state.contractState[rowAfter]),
                "OFFERED contract past its window must become EXPIRED");
        assertTrue(ContractState.EXPIRED.isTerminal(),
                "EXPIRED must be terminal so it filters out of OFFERED scans and clearTerminalContracts sweeps it");
    }

    @Test
    public void contractWithoutOfferExpiryNeverLapses() {
        // Debug-spawned offers pass offerExpiresTick = -1 (never lapse). Lifecycle
        // must respect that and leave them OFFERED indefinitely.
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.TIME_RUSHED, "P");
        long contractId = state.addContract(
                patronId, -1L, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                10, -1, -1,           // offerExpiresTick = -1 → never lapses
                (byte) 1, -1, 0, -1, 25_000, 0,
                (byte) 60, (byte) 60, (byte) 100);

        ContractLifecycleSystem sys = new ContractLifecycleSystem();
        for (int day = 11; day < 200; day++) sys.tick(state, day);
        int row = state.contractIndex(contractId);
        assertEquals(ContractState.OFFERED, ContractState.fromByte(state.contractState[row]),
                "offerExpiresTick == -1 must mean indefinite OFFERED");
    }

    @Test
    public void daysLeftMatchesExpiryMinusCurrentDay() {
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.TIME_RUSHED, "P");
        long contractId = state.addContract(
                patronId, -1L, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                100, -1, 105,
                (byte) 1, -1, 0, -1, 25_000, 0,
                (byte) 60, (byte) 60, (byte) 100);
        int row = state.contractIndex(contractId);

        assertEquals(5, state.contractDaysLeft(row, 100));
        assertEquals(1, state.contractDaysLeft(row, 104));
        assertEquals(0, state.contractDaysLeft(row, 105), "expiry day reads as zero, not negative");
        assertEquals(0, state.contractDaysLeft(row, 106), "past-expiry clamped to zero");
    }

    @Test
    public void daysLeftReturnsMinusOneForNonOfferedRows() {
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.TIME_RUSHED, "P");
        long contractId = state.addContract(
                patronId, -1L, -1L,
                ContractType.STRIKE, ContractState.ACTIVE,   // not OFFERED
                100, -1, 105,
                (byte) 1, -1, 0, -1, 25_000, 0,
                (byte) 60, (byte) 60, (byte) 100);
        int row = state.contractIndex(contractId);
        assertEquals(-1, state.contractDaysLeft(row, 100),
                "Non-OFFERED rows have no offer window — UI must treat as N/A");
    }

    @Test
    public void daysLeftReturnsMinusOneForBadRowIndex() {
        CampaignState state = new CampaignState();
        assertEquals(-1, state.contractDaysLeft(-1, 0));
        assertEquals(-1, state.contractDaysLeft(0, 0), "empty contracts table → row 0 invalid");
        assertEquals(-1, state.contractDaysLeft(99, 0));
    }

    @Test
    public void offerExpiryDoesNotInterfereWithStationingTerm() {
        // Stationing-mode contracts use contractExpiresTick (term end), not
        // contractOfferExpiresTick. Make sure the lifecycle's OFFERED-aging branch
        // doesn't accidentally fire on ACTIVE rows.
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "P");
        long contractId = state.addContract(
                patronId, -1L, -1L,
                ContractType.STRIKE, ContractState.ACTIVE,
                50, -1, 999,           // offerExpiresTick=999 but state is ACTIVE
                (byte) 1, -1, 0, -1, 25_000, 0,
                (byte) 60, (byte) 60, (byte) 100);

        ContractLifecycleSystem sys = new ContractLifecycleSystem();
        sys.tick(state, 1500); // long past the stray offerExpiresTick
        int row = state.contractIndex(contractId);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[row]),
                "ACTIVE contract must not be touched by the OFFERED-aging branch");
    }
}
