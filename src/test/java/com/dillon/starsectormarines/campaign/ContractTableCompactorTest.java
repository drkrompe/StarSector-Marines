package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractTableCompactorTest {

    @Test
    void removingTerminalRowsKeepsEveryStationingColumnAligned() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.STRIKE, ContractState.COMPLETED,
                1, -1, -1, (byte) 1, -1, 10, 11,
                12, 0, (byte) 60, (byte) 50, (byte) 105);
        long keptId = state.addContract(2L, -1L, -1L, ContractType.CADRE, ContractState.ACTIVE,
                20, 200, -1, (byte) 0, 7, 21, 22,
                0, 2_300, (byte) 5, (byte) 4, (byte) 101);
        state.contractPhasesDone[1] = 3;
        state.contractMarinesCommitted[1] = 88;
        state.contractLastRetainerTick[1] = 50;
        state.contractLastTrainingTick[1] = 51;
        state.contractSourceContractId[1] = 99L;
        state.contractOpposedChainId[1] = 98L;
        state.contractCivilWarBand[1] = CivilWarBand.MOBILIZATION.toByte();
        state.contractCivilWarContributionAppliedTick[1] = 49;
        state.contractLastDefaultCheckTick[1] = 52;
        state.contractPhaseAttempts[1] = 4;
        state.contractNextPhaseReadyTick[1] = 55;
        state.contractNextIncidentTick[1] = 56;
        state.contractIncidentPending[1] = 1;
        state.contractIncidentType[1] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
        state.contractDefenseEventKey[1] = 57L;
        state.contractDefenseTriggeredTick[1] = 58;
        state.contractDefenseTriggerType[1] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerHouseId[1] = 59L;
        state.contractDefenseAttackerFactionId[1] = 60;

        assertEquals(1, ContractTableCompactor.removeTerminal(state));

        assertEquals(1, state.contractCount);
        assertEquals(0, state.contractIndex(keptId));
        assertEquals(keptId, state.contractId[0]);
        assertEquals(ContractType.CADRE, ContractType.fromByte(state.contractType[0]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
        assertEquals(20, state.contractAcceptedTick[0]);
        assertEquals(200, state.contractExpiresTick[0]);
        assertEquals(3, state.contractPhasesDone[0]);
        assertEquals(7, state.contractCaptainId[0]);
        assertEquals(21, state.contractMarketId[0]);
        assertEquals(22, state.contractIndustryId[0]);
        assertEquals(2_300, state.contractRetainerPerMonth[0]);
        assertEquals(88, state.contractMarinesCommitted[0]);
        assertEquals(50, state.contractLastRetainerTick[0]);
        assertEquals(51, state.contractLastTrainingTick[0]);
        assertEquals(99L, state.contractSourceContractId[0]);
        assertEquals(98L, state.contractOpposedChainId[0]);
        assertEquals(CivilWarBand.MOBILIZATION,
                CivilWarBand.fromByte(state.contractCivilWarBand[0]));
        assertEquals(49, state.contractCivilWarContributionAppliedTick[0]);
        assertEquals(52, state.contractLastDefaultCheckTick[0]);
        assertEquals(4, state.contractPhaseAttempts[0]);
        assertEquals(55, state.contractNextPhaseReadyTick[0]);
        assertEquals(56, state.contractNextIncidentTick[0]);
        assertEquals(1, state.contractIncidentPending[0]);
        assertEquals(StationingIncidentType.LIVE_FIRE_RAID,
                StationingIncidentType.fromByte(state.contractIncidentType[0]));
        assertEquals(57L, state.contractDefenseEventKey[0]);
        assertEquals(58, state.contractDefenseTriggeredTick[0]);
        assertEquals(GarrisonDefenseTriggerType.VANILLA_RAID,
                GarrisonDefenseTriggerType.fromByte(state.contractDefenseTriggerType[0]));
        assertEquals(59L, state.contractDefenseAttackerHouseId[0]);
        assertEquals(60, state.contractDefenseAttackerFactionId[0]);
        assertEquals(5, state.contractSalvageBaseline[0] & 0xFF);
        assertEquals(4, state.contractSalvageNegotiated[0] & 0xFF);
        assertEquals(101, state.contractCashMultiplier[0] & 0xFF);
    }

    @Test
    void defaultedStationingRowWithPersonnelCannotBeCompactedAway() {
        CampaignState state = new CampaignState();
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.DEFAULTED, 1, 30, -1, (byte) 0, 4, 5, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 50;

        assertEquals(0, ContractTableCompactor.removeTerminal(state));
        assertEquals(0, state.contractIndex(id));
        assertEquals(1, state.contractCount);
    }

    @Test
    void terminalExtractionIsRetainedUntilParentPersonnelSettles() {
        CampaignState state = new CampaignState();
        long parentId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.DEFAULTED, 1, 30, -1, (byte) 0, 4, 5, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 50;
        long extractionId = state.addContract(1L, -1L, -1L, ContractType.EXTRACTION,
                ContractState.COMPLETED, 2, -1, -1, (byte) 1, -1, 5, -1,
                1_000, 0, (byte) 25, (byte) 25, (byte) 100);
        state.contractSourceContractId[1] = parentId;

        assertEquals(0, ContractTableCompactor.removeTerminal(state));
        assertEquals(0, state.contractIndex(parentId));
        assertEquals(1, state.contractIndex(extractionId));
    }

    @Test
    void namedBindingRetainsTerminalRowEvenWhenScalarAuthorityIsMissing() {
        CampaignState state = new CampaignState();
        long completedId = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.COMPLETED, 1, 30, -1, (byte) 0, -1, 5, -1,
                0, 1_000, (byte) 5, (byte) 5, (byte) 100);
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Cadre Lead", null, Rank.PRIVATE, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        roster.bindStationing(completedId, captain.id(), List.of(squad.id()));

        assertEquals(0, ContractTableCompactor.removeTerminal(state, roster));
        assertEquals(0, state.contractIndex(completedId));

        assertEquals(1, roster.releaseStationing(completedId));
        assertEquals(1, ContractTableCompactor.removeTerminal(state, roster));
        assertEquals(-1, state.contractIndex(completedId));
    }

    @Test
    void completedLegacyPersonnelRowAlsoWaitsForSettlement() {
        CampaignState state = new CampaignState();
        long completedId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.COMPLETED, 1, 30, -1, (byte) 0, -1, 5, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 40;

        assertEquals(0, ContractTableCompactor.removeTerminal(state));
        assertEquals(0, state.contractIndex(completedId));

        state.contractMarinesCommitted[0] = 0;
        assertEquals(1, ContractTableCompactor.removeTerminal(state));
    }
}
