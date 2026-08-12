package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractRetainerSystemTest {

    @Test
    void catchesUpWholeMonthsOnceAndCapsAtExpiry() {
        CampaignState state = stateWithContract(ContractType.GARRISON,
                ContractState.ACTIVE, 0, 60, 1_000);
        List<Integer> payments = new ArrayList<>();
        ContractRetainerSystem system = new ContractRetainerSystem(credits -> {
            payments.add(credits);
            return true;
        });

        system.tick(state, 29);
        system.tick(state, 30);
        system.tick(state, 30);
        system.tick(state, 90);

        assertEquals(List.of(1_000, 1_000), payments);
        assertEquals(60, state.contractLastRetainerTick[0]);
    }

    @Test
    void missedTicksBatchWholeMonths() {
        CampaignState state = stateWithContract(ContractType.CADRE,
                ContractState.ACTIVE, 10, 100, 800);
        List<Integer> payments = new ArrayList<>();

        new ContractRetainerSystem(credits -> {
            payments.add(credits);
            return true;
        }).tick(state, 100);

        assertEquals(List.of(2_400), payments);
        assertEquals(100, state.contractLastRetainerTick[0]);
    }

    @Test
    void failedDeliveryDoesNotAdvanceClock() {
        CampaignState state = stateWithContract(ContractType.GARRISON,
                ContractState.ACTIVE, 0, 60, 1_000);

        new ContractRetainerSystem(credits -> false).tick(state, 30);

        assertEquals(0, state.contractLastRetainerTick[0]);
    }

    @Test
    void ignoresMissionModeAndTerminalContracts() {
        CampaignState mission = stateWithContract(ContractType.STRIKE,
                ContractState.ACTIVE, 0, -1, 1_000);
        CampaignState terminal = stateWithContract(ContractType.GARRISON,
                ContractState.COMPLETED, 0, 30, 1_000);
        List<Integer> payments = new ArrayList<>();
        ContractRetainerSystem system = new ContractRetainerSystem(credits -> {
            payments.add(credits);
            return true;
        });

        system.tick(mission, 60);
        system.tick(terminal, 60);

        assertEquals(List.of(), payments);
    }

    private static CampaignState stateWithContract(ContractType type, ContractState contractState,
                                                    int acceptedDay, int expiresDay,
                                                    int monthlyRetainer) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, type, contractState,
                acceptedDay, expiresDay, -1, (byte) 0, -1, 0, -1,
                0, monthlyRetainer, (byte) 0, (byte) 0, (byte) 100);
        return state;
    }
}
