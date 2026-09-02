package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VanillaRaidGarrisonSystemTest {

    @Test
    void explicitRaidThreatArmsMatchingMarketOnce() {
        CampaignState state = new CampaignState();
        garrisonAt(state, 7);
        VanillaRaidGarrisonSystem system = new VanillaRaidGarrisonSystem(s ->
                Collections.singletonList(
                        new VanillaRaidGarrisonSystem.RaidThreat(44L, 7, 9, 0f)));

        system.tick(state, 30);
        assertEquals(ContractState.IN_PROGRESS,
                ContractState.fromByte(state.contractState[0]));

        state.contractState[0] = ContractState.ACTIVE.toByte();
        system.tick(state, 31);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
    }

    /**
     * A pirate-base raid reaches the trigger through the same seam as a fleet group, and
     * the strength and attacker it carries have to survive onto the payload the battle
     * reads.
     */
    @Test
    void raidIntelThreatPersistsStrengthAndAttackerFaction() {
        CampaignState state = new CampaignState();
        long contractId = garrisonAt(state, 7);
        int pirates = state.factionRegistry.intern("pirates");
        VanillaRaidGarrisonSystem system = new VanillaRaidGarrisonSystem(s ->
                Collections.singletonList(
                        new VanillaRaidGarrisonSystem.RaidThreat(51L, 7, pirates, 312.5f)));

        system.tick(state, 12);

        GarrisonDefensePayload payload = GarrisonDefensePayload.from(state, contractId);
        assertEquals(51L, payload.eventKey);
        assertEquals(312.5f, payload.attackerStrength, 0.001f);
        assertEquals("pirates", payload.attackerFactionKey);
    }

    @Test
    void threatOnAMarketWithNoGarrisonArmsNothing() {
        CampaignState state = new CampaignState();
        long contractId = garrisonAt(state, 7);
        VanillaRaidGarrisonSystem system = new VanillaRaidGarrisonSystem(s ->
                Collections.singletonList(
                        new VanillaRaidGarrisonSystem.RaidThreat(51L, 8, 9, 100f)));

        system.tick(state, 12);

        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
        assertNull(GarrisonDefensePayload.from(state, contractId));
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

    private static long garrisonAt(CampaignState state, int marketId) {
        return state.addContract(1L, -1L, -1L, ContractType.GARRISON, ContractState.ACTIVE,
                10, 100, -1, (byte) 0, -1, marketId, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
    }
}
