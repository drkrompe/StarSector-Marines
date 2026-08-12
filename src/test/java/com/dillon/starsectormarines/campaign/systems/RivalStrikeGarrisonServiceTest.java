package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RivalStrikeGarrisonServiceTest {

    @Test
    void launchedStrikeArmsGarrisonAtTargetHouseMarket() {
        Fixture fixture = fixture();

        int armed = RivalStrikeGarrisonService.armForContractLaunch(
                fixture.state, fixture.strikeId, 42);

        assertEquals(1, armed);
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(
                fixture.state, fixture.garrisonId);
        assertEquals(GarrisonDefenseTriggerType.RIVAL_STRIKE, payload.triggerType);
        assertEquals(fixture.attackerId, payload.attackerHouseId);
        assertEquals(11, payload.attackerFactionId);
        assertEquals(7, payload.marketId);
        assertEquals(42, payload.triggeredDay);
        assertEquals(RivalStrikeGarrisonService.eventKey(fixture.strikeId), payload.eventKey);
        assertNotEquals(0L, payload.eventKey);
    }

    @Test
    void consumedStrikeCannotRearmSameGarrison() {
        Fixture fixture = fixture();
        assertEquals(1, RivalStrikeGarrisonService.armForContractLaunch(
                fixture.state, fixture.strikeId, 42));
        int row = fixture.state.contractIndex(fixture.garrisonId);
        fixture.state.contractState[row] = ContractState.ACTIVE.toByte();
        fixture.state.contractDefenseTriggerType[row] = GarrisonDefenseTriggerType.NONE.toByte();

        assertEquals(0, RivalStrikeGarrisonService.armForContractLaunch(
                fixture.state, fixture.strikeId, 43));
        assertNull(GarrisonDefensePayload.from(fixture.state, fixture.garrisonId));
    }

    @Test
    void unrelatedOrTerminalContractsDoNotArmDefense() {
        Fixture fixture = fixture();
        int strikeRow = fixture.state.contractIndex(fixture.strikeId);
        fixture.state.contractType[strikeRow] = ContractType.ESCORT.toByte();
        assertEquals(0, RivalStrikeGarrisonService.armForContractLaunch(
                fixture.state, fixture.strikeId, 42));

        fixture.state.contractType[strikeRow] = ContractType.STRIKE.toByte();
        fixture.state.contractState[strikeRow] = ContractState.EXPIRED.toByte();
        assertEquals(0, RivalStrikeGarrisonService.armForContractLaunch(
                fixture.state, fixture.strikeId, 42));

        int garrisonRow = fixture.state.contractIndex(fixture.garrisonId);
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[garrisonRow]));
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        long attacker = state.addHouse(1, 11, HouseFlavor.CORPORATE,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.TIME_RUSHED, "Attacker");
        long defender = state.addHouse(7, 22, HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Defender");
        long strike = state.addContract(attacker, defender, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                10, -1, 50, (byte) 1, -1, 1, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);
        long garrison = state.addContract(defender, -1L, -1L,
                ContractType.GARRISON, ContractState.ACTIVE,
                5, 100, -1, (byte) 0, -1, 7, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[state.contractIndex(garrison)] = 80;
        return new Fixture(state, attacker, strike, garrison);
    }

    private static final class Fixture {
        final CampaignState state;
        final long attackerId;
        final long strikeId;
        final long garrisonId;

        Fixture(CampaignState state, long attackerId, long strikeId, long garrisonId) {
            this.state = state;
            this.attackerId = attackerId;
            this.strikeId = strikeId;
            this.garrisonId = garrisonId;
        }
    }
}
