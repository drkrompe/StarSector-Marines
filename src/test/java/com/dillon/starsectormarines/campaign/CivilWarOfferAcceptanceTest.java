package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilWarOfferAcceptanceTest {

    @Test
    void claimantAcceptanceActivatesChoiceAndWithdrawsIncumbent() {
        Fixture fixture = new Fixture(70);

        assertTrue(CivilWarOfferAcceptance.acceptMission(
                fixture.state, fixture.claimantOffer, 25));

        assertEquals(ContractState.ACTIVE, fixture.contractState(fixture.claimantOffer));
        assertEquals(25, fixture.state.contractAcceptedTick[
                fixture.state.contractIndex(fixture.claimantOffer)]);
        assertEquals(-1, fixture.state.contractOfferExpiresTick[
                fixture.state.contractIndex(fixture.claimantOffer)]);
        assertEquals(ContractState.EXPIRED, fixture.contractState(fixture.incumbentOffer));
        assertTrue(CivilWarOfferAcceptance.isParticipation(
                fixture.state, fixture.claimantOffer));
        assertFalse(CivilWarOfferAcceptance.isOfferedParticipation(
                fixture.state, fixture.claimantOffer));
    }

    @Test
    void staleBandAndTerminalChainRejectAcceptance() {
        Fixture stale = new Fixture(70);
        stale.state.chainProgress[stale.chainRow] = 120;
        Fixture terminal = new Fixture(70);
        terminal.state.chainState[terminal.chainRow] = ChainState.FAILED.toByte();

        assertFalse(CivilWarOfferAcceptance.canAccept(
                stale.state, stale.claimantOffer));
        assertFalse(CivilWarOfferAcceptance.canAccept(
                terminal.state, terminal.claimantOffer));
        assertEquals(ContractState.OFFERED, stale.contractState(stale.claimantOffer));
        assertEquals(ContractState.OFFERED, terminal.contractState(terminal.claimantOffer));
    }

    @Test
    void lockedAllegianceAndOpposingCommitmentRejectContradiction() {
        Fixture locked = new Fixture(70);
        locked.state.chainPlayerAllegiance[locked.chainRow] =
                CivilWarAllegiance.INCUMBENT.toByte();
        Fixture committed = new Fixture(70);
        committed.state.contractState[committed.state.contractIndex(
                committed.incumbentOffer)] = ContractState.ACTIVE.toByte();

        assertFalse(CivilWarOfferAcceptance.canAccept(
                locked.state, locked.claimantOffer));
        assertFalse(CivilWarOfferAcceptance.canAccept(
                committed.state, committed.claimantOffer));
        assertTrue(CivilWarOfferAcceptance.canAccept(
                locked.state, locked.incumbentOffer));
    }

    @Test
    void malformedLineageAndTypeAreNotAccepted() {
        Fixture lineage = new Fixture(70);
        int lineageRow = lineage.state.contractIndex(lineage.claimantOffer);
        lineage.state.contractOpposedChainId[lineageRow] = lineage.chainId;
        Fixture type = new Fixture(70);
        int typeRow = type.state.contractIndex(type.claimantOffer);
        type.state.contractType[typeRow] = ContractType.STRIKE.toByte();

        assertFalse(CivilWarOfferAcceptance.canAccept(
                lineage.state, lineage.claimantOffer));
        assertFalse(CivilWarOfferAcceptance.canAccept(
                type.state, type.claimantOffer));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long claimant = house(state, market, "Claimant");
        final long incumbent = house(state, market, "Incumbent");
        final long chainId = state.addAutonomousChain(claimant, incumbent, market,
                -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        final int chainRow = state.chainIndex(chainId);
        final long claimantOffer;
        final long incumbentOffer;

        Fixture(int progress) {
            state.chainProgress[chainRow] = (short) progress;
            CivilWarBand band = CivilWarBand.forProgress(progress);
            ContractType claimantType = band == CivilWarBand.MOBILIZATION
                    ? ContractType.CADRE : ContractType.PLANETARY_ASSAULT;
            ContractType incumbentType = band == CivilWarBand.MOBILIZATION
                    ? ContractType.GARRISON : ContractType.PLANETARY_ASSAULT;
            claimantOffer = offer(claimant, incumbent, chainId, -1L,
                    claimantType, band);
            incumbentOffer = offer(incumbent, claimant, -1L, chainId,
                    incumbentType, band);
        }

        ContractState contractState(long contractId) {
            return ContractState.fromByte(state.contractState[
                    state.contractIndex(contractId)]);
        }

        private long offer(long patron, long target, long parent, long opposed,
                           ContractType type, CivilWarBand band) {
            long id = state.addContract(patron, target, parent, type,
                    ContractState.OFFERED, 20, -1, 27, (byte) 1, -1,
                    market, -1, 100, 0, (byte) 20, (byte) 20, (byte) 100);
            int row = state.contractIndex(id);
            state.contractOpposedChainId[row] = opposed;
            state.contractCivilWarBand[row] = band.toByte();
            return id;
        }

        private static long house(CampaignState state, int market, String name) {
            return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
