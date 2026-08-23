package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractReputation;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseResolution;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingLapseSystemTest {

    private static final int ARMED_DAY = 40;

    @Test
    void garrisonSurvivesUntilItsWindowExpiresThenFails() {
        CampaignState state = pendingDefense(ARMED_DAY, 500);
        StationingLapseSystem system = new StationingLapseSystem(() -> null);

        system.tick(state, ARMED_DAY);
        assertEquals(ContractState.IN_PROGRESS, ContractState.fromByte(state.contractState[0]));
        assertEquals(ARMED_DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS,
                state.contractResponseDeadlineTick[0]);

        system.tick(state, ARMED_DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS - 1);
        assertEquals(ContractState.IN_PROGRESS, ContractState.fromByte(state.contractState[0]));

        system.tick(state, ARMED_DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS);
        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void cadreIncidentGetsItsOwnLongerWindow() {
        CampaignState state = pendingIncident(ARMED_DAY, 500);
        StationingLapseSystem system = new StationingLapseSystem(() -> null);

        system.tick(state, ARMED_DAY);
        assertEquals(ARMED_DAY + StationingLapseSystem.CADRE_RESPONSE_DAYS,
                state.contractResponseDeadlineTick[0]);

        system.tick(state, ARMED_DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));

        system.tick(state, ARMED_DAY + StationingLapseSystem.CADRE_RESPONSE_DAYS);
        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void deadlineIsClampedToTheRemainingTerm() {
        int expires = ARMED_DAY + 3;
        CampaignState state = pendingDefense(ARMED_DAY, expires);

        new StationingLapseSystem(() -> null).tick(state, ARMED_DAY);

        assertEquals(expires, state.contractResponseDeadlineTick[0]);
    }

    @Test
    void eventArmedBeforeThisLayerExistedGetsAFullWindowFromFirstObservation() {
        CampaignState state = pendingDefense(10, 500);
        StationingLapseSystem system = new StationingLapseSystem(() -> null);

        system.tick(state, 100);

        assertEquals(ContractState.IN_PROGRESS, ContractState.fromByte(state.contractState[0]));
        assertEquals(100 + StationingLapseSystem.GARRISON_RESPONSE_DAYS,
                state.contractResponseDeadlineTick[0]);
    }

    @Test
    void lapseCostsMoreThanLosingTheFightAndIsAppliedExactlyOnce() {
        CampaignState state = pendingDefense(ARMED_DAY, 500);
        StationingLapseSystem system = new StationingLapseSystem(() -> null);
        int deadline = ARMED_DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS;

        system.tick(state, ARMED_DAY);
        system.tick(state, deadline);

        int repRow = state.repIndex(1L);
        assertEquals(ContractReputation.LAPSED_HOUSE_DELTA, state.repValue[repRow]);
        assertEquals(ContractReputation.LAPSED_MRB_DELTA, state.playerMrbRep);
        assertEquals(1, state.repContractsFailed[repRow]);
        assertTrue(ContractReputation.LAPSED_HOUSE_DELTA
                < ContractReputation.ABANDONED_HOUSE_DELTA);

        system.tick(state, deadline + 1);
        system.tick(state, deadline + 30);

        assertEquals(ContractReputation.LAPSED_HOUSE_DELTA, state.repValue[repRow]);
        assertEquals(ContractReputation.LAPSED_MRB_DELTA, state.playerMrbRep);
        assertEquals(1, state.repContractsFailed[repRow]);
        assertEquals(-1, state.contractResponseDeadlineTick[0]);
    }

    @Test
    void answeringBeforeTheDeadlineDisarmsTheWindow() {
        CampaignState state = pendingDefense(ARMED_DAY, 500);
        StationingLapseSystem system = new StationingLapseSystem(() -> null);
        system.tick(state, ARMED_DAY);

        assertEquals(GarrisonDefenseResolution.Result.DEFENSE_WON,
                GarrisonDefenseResolution.apply(state, state.contractId[0], 77L,
                        3, false, true));
        system.tick(state, ARMED_DAY + 1);

        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
        assertEquals(-1, state.contractResponseDeadlineTick[0]);

        system.tick(state, ARMED_DAY + 90);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
        assertEquals(0, state.playerMrbRep);
    }

    @Test
    void namedDetachmentIsReleasedAndTheCaptainComesHome() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 500, -1, (byte) 0,
                captainSlot, 12, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 6;
        armDefense(state, ARMED_DAY);
        assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
        captain.setStatus(Status.GARRISONED);

        StationingLapseSystem system = new StationingLapseSystem(() -> roster);
        system.tick(state, ARMED_DAY);
        system.tick(state, ARMED_DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS);

        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
        assertEquals(0, state.contractMarinesCommitted[0]);
        assertEquals(-1, state.contractCaptainId[0]);
        assertFalse(squad.stationed());
        assertEquals(Status.ACTIVE, captain.status());
    }

    @Test
    void nonStationingContractsAreUntouched() {
        CampaignState state = new CampaignState();
        state.addContract(1L, 2L, -1L, ContractType.STRIKE,
                ContractState.IN_PROGRESS, 10, 500, -1, (byte) 1,
                -1, 12, -1, 25_000, 0, (byte) 60, (byte) 60, (byte) 100);

        new StationingLapseSystem(() -> null).tick(state, 900);

        assertEquals(ContractState.IN_PROGRESS, ContractState.fromByte(state.contractState[0]));
        assertEquals(-1, state.contractResponseDeadlineTick[0]);
        assertEquals(0, state.playerMrbRep);
    }

    private static CampaignState pendingDefense(int armedDay, int expiresTick) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, expiresTick, -1, (byte) 0,
                -1, 12, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        armDefense(state, armedDay);
        return state;
    }

    private static void armDefense(CampaignState state, int armedDay) {
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggeredTick[0] = armedDay;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerHouseId[0] = 2L;
        state.contractDefenseAttackerFactionId[0] = 5;
    }

    private static CampaignState pendingIncident(int dueDay, int expiresTick) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, expiresTick, -1, (byte) 0,
                -1, 12, -1, 0, 400,
                (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = 6;
        state.contractNextIncidentTick[0] = dueDay;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
        return state;
    }
}
