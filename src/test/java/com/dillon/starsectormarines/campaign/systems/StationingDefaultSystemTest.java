package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationingDefaultSystemTest {

    @Test
    void deposedPatronDefaultsOnlyStationingContracts() {
        CampaignState state = state(HouseStatus.DEPOSED);
        addStationing(state, ContractState.ACTIVE, 1, 100);
        state.addContract(state.houseId[0], 2L, -1L, ContractType.STRIKE,
                ContractState.ACTIVE, 1, -1, -1, (byte) 1, -1, 0, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);

        new StationingDefaultSystem((id, checkpoint, chance) -> false).tick(state, 2);

        assertEquals(ContractState.DEFAULTED, ContractState.fromByte(state.contractState[0]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[1]));
    }

    @Test
    void monthlyRollDefaultsAtFirstDueCheckpoint() {
        CampaignState state = state(HouseStatus.ACTIVE);
        addStationing(state, ContractState.ACTIVE, 10, 100);
        StationingDefaultSystem system = new StationingDefaultSystem(
                (id, checkpoint, chance) -> true);

        system.tick(state, 39);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));

        system.tick(state, 40);
        assertEquals(ContractState.DEFAULTED, ContractState.fromByte(state.contractState[0]));
        assertEquals(40, state.contractLastDefaultCheckTick[0]);
    }

    @Test
    void missedTicksCatchUpEachWholeMonthExactlyOnce() {
        CampaignState state = state(HouseStatus.ACTIVE);
        addStationing(state, ContractState.ACTIVE, 10, 100);
        CountingRoll roll = new CountingRoll();
        StationingDefaultSystem system = new StationingDefaultSystem(roll);

        system.tick(state, 100);
        system.tick(state, 101);

        assertEquals(3, roll.calls);
        assertEquals(100, state.contractLastDefaultCheckTick[0]);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void legacyClockBaselinesWithoutRetroactiveRolls() {
        CampaignState state = state(HouseStatus.ACTIVE);
        addStationing(state, ContractState.ACTIVE, 10, 200);
        state.contractLastDefaultCheckTick[0] = -1;
        CountingRoll roll = new CountingRoll();

        new StationingDefaultSystem(roll).tick(state, 150);

        assertEquals(0, roll.calls);
        assertEquals(150, state.contractLastDefaultCheckTick[0]);
    }

    @Test
    void powerReducesRiskToOnePercentFloor() {
        assertEquals(8, StationingDefaultSystem.defaultChancePercent(0));
        assertEquals(5, StationingDefaultSystem.defaultChancePercent(350));
        assertEquals(1, StationingDefaultSystem.defaultChancePercent(700));
        assertEquals(1, StationingDefaultSystem.defaultChancePercent(10_000));
    }

    private static CampaignState state(HouseStatus status) {
        CampaignState state = new CampaignState();
        state.addHouse(0, 0, HouseFlavor.CORPORATE, HouseRank.TIER_2,
                status, PatronArchetype.ESTABLISHED, "Patron");
        return state;
    }

    private static void addStationing(CampaignState state, ContractState contractState,
                                      int acceptedDay, int expiresDay) {
        state.addContract(state.houseId[0], -1L, -1L, ContractType.GARRISON,
                contractState, acceptedDay, expiresDay, -1, (byte) 0, 1, 0, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
    }

    private static final class CountingRoll implements StationingDefaultSystem.DefaultRoll {
        int calls;

        @Override
        public boolean defaults(long contractId, int checkpointDay, int chancePercent) {
            calls++;
            return false;
        }
    }
}
