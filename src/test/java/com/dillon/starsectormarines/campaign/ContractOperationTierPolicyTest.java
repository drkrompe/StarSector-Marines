package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.OperationTier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractOperationTierPolicyTest {

    @Test
    void patronRankAuthorsOrdinaryMissionScale() {
        assertEquals(OperationTier.FIRST_CONTRACT,
                ContractOperationTierPolicy.select(HouseRank.TIER_1, ContractType.STRIKE));
        assertEquals(OperationTier.ESTABLISHED,
                ContractOperationTierPolicy.select(HouseRank.TIER_2, ContractType.ESCORT));
        assertEquals(OperationTier.VETERAN,
                ContractOperationTierPolicy.select(HouseRank.TIER_3, ContractType.STRIKE));
    }

    @Test
    void planetaryAssaultHonoursItsScaleFloorAndStationingHasNoMissionTier() {
        assertEquals(OperationTier.REINFORCED,
                ContractOperationTierPolicy.select(
                        HouseRank.TIER_3, ContractType.PLANETARY_ASSAULT));
        assertNull(ContractOperationTierPolicy.select(
                HouseRank.TIER_2, ContractType.GARRISON));
    }

    @Test
    void addingAContractPersistsTheSelectedTier() {
        CampaignState state = new CampaignState();
        long patron = state.addHouse(1, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Patron");

        long contract = state.addContract(patron, -1L, -1L,
                ContractType.ESCORT, ContractState.OFFERED,
                1, -1, 10, (byte) 1, -1, 1, -1,
                45_000, 0, (byte) 10, (byte) 10, (byte) 100);

        int row = state.contractIndex(contract);
        assertEquals(OperationTier.ESTABLISHED,
                OperationTier.fromPersistedByte(state.contractOperationTier[row]));
    }

    @Test
    void legacySaveBackfillsTierFromFrozenPatronRankRatherThanRisk() throws Exception {
        CampaignState state = new CampaignState();
        long patron = state.addHouse(1, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_3, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Patron");
        state.addContract(patron, -1L, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                1, -1, 10, (byte) 1, -1, 1, -1,
                75_000, 0, (byte) 60, (byte) 60, (byte) 100);
        state.contractOperationTier = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertEquals(OperationTier.VETERAN,
                OperationTier.fromPersistedByte(state.contractOperationTier[0]));
    }
}
