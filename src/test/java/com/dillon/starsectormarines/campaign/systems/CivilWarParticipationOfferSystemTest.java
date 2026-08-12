package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilWarParticipationOfferSystemTest {

    @Test
    void discoveredCoalitionBandCreatesOneOfferPerSideExactlyOnce() {
        Fixture fixture = new Fixture(20);
        CivilWarParticipationOfferSystem system = new CivilWarParticipationOfferSystem();

        system.tick(fixture.state, 20);
        system.tick(fixture.state, 21);

        assertEquals(2, fixture.state.contractCount);
        assertOffer(fixture, 0, CivilWarAllegiance.CLAIMANT,
                ContractType.ESCORT, CivilWarBand.COALITION_BUILDING, 27);
        assertOffer(fixture, 1, CivilWarAllegiance.INCUMBENT,
                ContractType.STRIKE, CivilWarBand.COALITION_BUILDING, 27);
        assertEquals(-1, fixture.state.chainIndustryId[fixture.chainRow]);
    }

    @Test
    void bandTransitionExpiresOldPairAndCreatesMappedNewPair() {
        Fixture fixture = new Fixture(20);
        CivilWarParticipationOfferSystem system = new CivilWarParticipationOfferSystem();
        system.tick(fixture.state, 20);
        fixture.state.chainProgress[fixture.chainRow] = 70;

        system.tick(fixture.state, 21);

        assertEquals(4, fixture.state.contractCount);
        assertEquals(ContractState.EXPIRED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(ContractState.EXPIRED,
                ContractState.fromByte(fixture.state.contractState[1]));
        assertOffer(fixture, 2, CivilWarAllegiance.CLAIMANT,
                ContractType.CADRE, CivilWarBand.MOBILIZATION, 28);
        assertOffer(fixture, 3, CivilWarAllegiance.INCUMBENT,
                ContractType.GARRISON, CivilWarBand.MOBILIZATION, 28);
    }

    @Test
    void lockedAllegianceAndTerminalStateWithdrawOppositionAndStopGeneration() {
        Fixture fixture = new Fixture(70);
        CivilWarParticipationOfferSystem system = new CivilWarParticipationOfferSystem();
        system.tick(fixture.state, 20);
        fixture.state.chainPlayerAllegiance[fixture.chainRow] =
                CivilWarAllegiance.CLAIMANT.toByte();

        system.tick(fixture.state, 21);
        assertEquals(ContractState.OFFERED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(ContractState.EXPIRED,
                ContractState.fromByte(fixture.state.contractState[1]));

        fixture.state.chainState[fixture.chainRow] = ChainState.FAILED.toByte();
        system.tick(fixture.state, 22);
        assertEquals(ContractState.EXPIRED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(2, fixture.state.contractCount);
    }

    @Test
    void noKnownLocalObjectiveOrUndiscoveredChainCreatesNothing() {
        Fixture noObjective = new Fixture(20);
        noObjective.state.stakeCount = 0;
        Fixture unknown = new Fixture(20);
        unknown.state.chainDiscoveredTick[unknown.chainRow] = -1;
        CivilWarParticipationOfferSystem system = new CivilWarParticipationOfferSystem();

        system.tick(noObjective.state, 20);
        system.tick(unknown.state, 20);

        assertEquals(0, noObjective.state.contractCount);
        assertEquals(0, unknown.state.contractCount);
    }

    private static void assertOffer(Fixture fixture, int row,
                                    CivilWarAllegiance side, ContractType type,
                                    CivilWarBand band, int expires) {
        long patron = side == CivilWarAllegiance.CLAIMANT
                ? fixture.claimant : fixture.incumbent;
        long target = side == CivilWarAllegiance.CLAIMANT
                ? fixture.incumbent : fixture.claimant;
        assertEquals(patron, fixture.state.contractPatronHouseId[row]);
        assertEquals(target, fixture.state.contractTargetHouseId[row]);
        assertEquals(type, ContractType.fromByte(fixture.state.contractType[row]));
        assertEquals(band, CivilWarBand.fromByte(
                fixture.state.contractCivilWarBand[row]));
        assertEquals(fixture.industry, fixture.state.contractIndustryId[row]);
        assertEquals(ContractState.OFFERED,
                ContractState.fromByte(fixture.state.contractState[row]));
        assertEquals(expires, fixture.state.contractOfferExpiresTick[row]);
        if (side == CivilWarAllegiance.CLAIMANT) {
            assertEquals(fixture.chainId, fixture.state.contractChainId[row]);
            assertEquals(-1L, fixture.state.contractOpposedChainId[row]);
        } else {
            assertEquals(-1L, fixture.state.contractChainId[row]);
            assertEquals(fixture.chainId, fixture.state.contractOpposedChainId[row]);
        }
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final int industry = state.industryRegistry.intern("heavyindustry");
        final long claimant = house(state, market, "Claimant");
        final long incumbent = house(state, market, "Incumbent");
        final long chainId = state.addAutonomousChain(claimant, incumbent, market,
                -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        final int chainRow = state.chainIndex(chainId);

        Fixture(int progress) {
            state.playerMrbRep = 20;
            state.chainProgress[chainRow] = (short) progress;
            state.chainDiscoveredTick[chainRow] = 10;
            state.addStake(claimant, market, industry, (short) 100);
            state.addStake(incumbent, market, industry, (short) 90);
        }

        private static long house(CampaignState state, int market, String name) {
            return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
