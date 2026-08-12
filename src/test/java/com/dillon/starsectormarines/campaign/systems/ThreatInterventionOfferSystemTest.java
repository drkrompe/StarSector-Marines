package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreatInterventionOfferSystemTest {

    @Test
    void discoveredThreatCreatesOneBoundStrikeOffer() {
        Fixture fixture = new Fixture();
        ThreatInterventionOfferSystem system = new ThreatInterventionOfferSystem();

        system.tick(fixture.state, 20);
        system.tick(fixture.state, 20);

        assertEquals(1, fixture.state.contractCount);
        assertEquals(fixture.target, fixture.state.contractPatronHouseId[0]);
        assertEquals(fixture.actor, fixture.state.contractTargetHouseId[0]);
        assertEquals(-1L, fixture.state.contractChainId[0]);
        assertEquals(fixture.chainId, fixture.state.contractOpposedChainId[0]);
        assertEquals(ContractType.STRIKE,
                ContractType.fromByte(fixture.state.contractType[0]));
        assertEquals(ContractState.OFFERED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(1, fixture.state.contractMarketId[0]);
        assertEquals(7, fixture.state.contractIndustryId[0]);
        assertEquals(25_000, fixture.state.contractBasePayout[0]);
        assertEquals(27, fixture.state.contractOfferExpiresTick[0]);
    }

    @Test
    void offerWindowShortensBeforeImminentResolution() {
        Fixture fixture = new Fixture();
        fixture.state.chainProgress[fixture.chainRow] = 43;

        new ThreatInterventionOfferSystem().tick(fixture.state, 20);

        assertEquals(21, fixture.state.contractOfferExpiresTick[0]);
    }

    @Test
    void terminalSourceWithdrawsOfferAndNeverRespawnsIt() {
        Fixture fixture = new Fixture();
        ThreatInterventionOfferSystem system = new ThreatInterventionOfferSystem();
        system.tick(fixture.state, 20);
        fixture.state.chainState[fixture.chainRow] = ChainState.RESOLVED.toByte();

        system.tick(fixture.state, 21);
        system.tick(fixture.state, 22);

        assertEquals(1, fixture.state.contractCount);
        assertEquals(ContractState.EXPIRED,
                ContractState.fromByte(fixture.state.contractState[0]));
    }

    @Test
    void unknownIneligibleAndPlayerBackedThreatsCreateNothing() {
        Fixture unknown = new Fixture();
        unknown.state.chainDiscoveredTick[unknown.chainRow] = -1;
        Fixture ineligible = new Fixture();
        ineligible.state.repValue[ineligible.state.ensureRepRow(ineligible.target)] = -100;
        Fixture playerBacked = new Fixture();
        playerBacked.state.chainPatron[playerBacked.chainRow] = playerBacked.actor;
        ThreatInterventionOfferSystem system = new ThreatInterventionOfferSystem();

        system.tick(unknown.state, 20);
        system.tick(ineligible.state, 20);
        system.tick(playerBacked.state, 20);

        assertEquals(0, unknown.state.contractCount);
        assertEquals(0, ineligible.state.contractCount);
        assertEquals(0, playerBacked.state.contractCount);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final long actor = house(state, "Actor");
        final long target = house(state, "Target");
        final long chainId;
        final int chainRow;

        Fixture() {
            chainId = state.addAutonomousChain(actor, target, 1, 7, (byte) 0,
                    ChainArchetype.CONSOLIDATE_STAKE, (short) 45, (byte) 32, 1);
            chainRow = state.chainIndex(chainId);
            state.chainDiscoveredTick[chainRow] = 10;
        }

        private static long house(CampaignState state, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
