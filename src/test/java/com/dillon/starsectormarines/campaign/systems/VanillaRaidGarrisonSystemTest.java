package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VanillaRaidGarrisonSystemTest {

    @Test
    void explicitRaidThreatArmsMatchingMarketOnce() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON, ContractState.ACTIVE,
                10, 100, -1, (byte) 0, -1, 7, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        VanillaRaidGarrisonSystem system = new VanillaRaidGarrisonSystem(s ->
                Collections.singletonList(new VanillaRaidGarrisonSystem.RaidThreat(44L, 7, 9)));

        system.tick(state, 30);
        assertEquals(ContractState.IN_PROGRESS,
                ContractState.fromByte(state.contractState[0]));

        state.contractState[0] = ContractState.ACTIVE.toByte();
        system.tick(state, 31);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void eventKeysAreStableAndDifferentAcrossTargets() {
        long first = VanillaRaidGarrisonSystem.eventKey(100L, "pirates", "jangala", "raid-1");
        long repeat = VanillaRaidGarrisonSystem.eventKey(100L, "pirates", "jangala", "raid-1");
        long other = VanillaRaidGarrisonSystem.eventKey(100L, "pirates", "asharu", "raid-1");

        assertEquals(first, repeat);
        assertNotEquals(first, other);
        assertNotEquals(0L, first);
    }
}
