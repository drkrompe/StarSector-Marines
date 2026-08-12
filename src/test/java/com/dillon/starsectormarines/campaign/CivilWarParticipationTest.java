package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilWarParticipationTest {

    @Test
    void firstClaimantSuccessLocksAllegianceAndAdvancesExactlyOnce() {
        Fixture fixture = new Fixture(40);
        long contract = fixture.contract(CivilWarAllegiance.CLAIMANT,
                CivilWarBand.COALITION_BUILDING, ContractType.ESCORT);

        assertEquals(CivilWarParticipation.Result.APPLIED,
                CivilWarParticipation.applyCompleted(fixture.state, contract, 50));
        assertEquals(CivilWarParticipation.Result.ALREADY_APPLIED,
                CivilWarParticipation.applyCompleted(fixture.state, contract, 51));

        assertEquals(CivilWarAllegiance.CLAIMANT,
                CivilWarAllegiance.fromByte(
                        fixture.state.chainPlayerAllegiance[fixture.chainRow]));
        assertEquals(55, fixture.state.chainProgress[fixture.chainRow]);
        assertEquals(15, fixture.state.chainPlayerContribution[fixture.chainRow]);
        assertEquals(50, fixture.state.chainPlayerLastContributionTick[fixture.chainRow]);
        assertEquals(50, fixture.state.contractCivilWarContributionAppliedTick[0]);
    }

    @Test
    void incumbentMobilizationSuccessSuppressesProgressWithoutGoingNegative() {
        Fixture fixture = new Fixture(20);
        long contract = fixture.contract(CivilWarAllegiance.INCUMBENT,
                CivilWarBand.MOBILIZATION, ContractType.GARRISON);

        assertEquals(CivilWarParticipation.Result.APPLIED,
                CivilWarParticipation.applyCompleted(fixture.state, contract, 50));

        assertEquals(0, fixture.state.chainProgress[fixture.chainRow]);
        assertEquals(CivilWarAllegiance.INCUMBENT,
                CivilWarAllegiance.fromByte(
                        fixture.state.chainPlayerAllegiance[fixture.chainRow]));
        assertEquals(30, fixture.state.chainPlayerContribution[fixture.chainRow]);
    }

    @Test
    void lockedAllegianceRejectsOppositeSuccessWithoutConsumingIt() {
        Fixture fixture = new Fixture(80);
        fixture.state.chainPlayerAllegiance[fixture.chainRow] =
                CivilWarAllegiance.CLAIMANT.toByte();
        long contract = fixture.contract(CivilWarAllegiance.INCUMBENT,
                CivilWarBand.MOBILIZATION, ContractType.GARRISON);

        assertEquals(CivilWarParticipation.Result.ALLEGIANCE_CONFLICT,
                CivilWarParticipation.applyCompleted(fixture.state, contract, 50));
        assertEquals(80, fixture.state.chainProgress[fixture.chainRow]);
        assertEquals(-1, fixture.state.contractCivilWarContributionAppliedTick[0]);
    }

    @Test
    void openConflictClaimantArmsResolutionAndIncumbentEndsChain() {
        Fixture claimant = new Fixture(140);
        long claimantContract = claimant.contract(CivilWarAllegiance.CLAIMANT,
                CivilWarBand.OPEN_CONFLICT, ContractType.PLANETARY_ASSAULT);
        Fixture incumbent = new Fixture(140);
        long incumbentContract = incumbent.contract(CivilWarAllegiance.INCUMBENT,
                CivilWarBand.OPEN_CONFLICT, ContractType.PLANETARY_ASSAULT);

        assertEquals(CivilWarParticipation.Result.APPLIED,
                CivilWarParticipation.applyCompleted(
                        claimant.state, claimantContract, 50));
        assertEquals(180, claimant.state.chainProgress[claimant.chainRow]);
        assertEquals(ChainState.ACTIVE,
                ChainState.fromByte(claimant.state.chainState[claimant.chainRow]));

        assertEquals(CivilWarParticipation.Result.APPLIED,
                CivilWarParticipation.applyCompleted(
                        incumbent.state, incumbentContract, 50));
        assertEquals(ChainState.FAILED,
                ChainState.fromByte(incumbent.state.chainState[incumbent.chainRow]));
        assertEquals(50, incumbent.state.chainResolvedTick[incumbent.chainRow]);
    }

    @Test
    void malformedLineagePartiesBandOrStateCannotMutateChain() {
        Fixture dual = new Fixture(40);
        long dualContract = dual.contract(CivilWarAllegiance.CLAIMANT,
                CivilWarBand.COALITION_BUILDING, ContractType.ESCORT);
        dual.state.contractOpposedChainId[0] = dual.chainId;
        Fixture wrongType = new Fixture(40);
        long wrongTypeContract = wrongType.contract(CivilWarAllegiance.CLAIMANT,
                CivilWarBand.COALITION_BUILDING, ContractType.STRIKE);
        Fixture incomplete = new Fixture(40);
        long incompleteContract = incomplete.contract(CivilWarAllegiance.CLAIMANT,
                CivilWarBand.COALITION_BUILDING, ContractType.ESCORT);
        incomplete.state.contractState[0] = ContractState.ACTIVE.toByte();

        assertEquals(CivilWarParticipation.Result.INVALID,
                CivilWarParticipation.applyCompleted(dual.state, dualContract, 50));
        assertEquals(CivilWarParticipation.Result.INVALID,
                CivilWarParticipation.applyCompleted(
                        wrongType.state, wrongTypeContract, 50));
        assertEquals(CivilWarParticipation.Result.NOT_READY,
                CivilWarParticipation.applyCompleted(
                        incomplete.state, incompleteContract, 50));
        assertEquals(40, dual.state.chainProgress[dual.chainRow]);
        assertEquals(40, wrongType.state.chainProgress[wrongType.chainRow]);
        assertEquals(40, incomplete.state.chainProgress[incomplete.chainRow]);
    }

    @Test
    void terminalChainCannotReceiveLateContribution() {
        Fixture fixture = new Fixture(40);
        long contract = fixture.contract(CivilWarAllegiance.CLAIMANT,
                CivilWarBand.COALITION_BUILDING, ContractType.ESCORT);
        fixture.state.chainState[fixture.chainRow] = ChainState.RESOLVED.toByte();

        assertEquals(CivilWarParticipation.Result.CHAIN_TERMINAL,
                CivilWarParticipation.applyCompleted(fixture.state, contract, 50));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long claimant = state.addHouse(market, 1, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE, PatronArchetype.NEWCOMER,
                "Claimant");
        final long incumbent = state.addHouse(market, 1, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED,
                "Incumbent");
        final long chainId = state.addAutonomousChain(claimant, incumbent, market,
                -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        final int chainRow = state.chainIndex(chainId);

        Fixture(int progress) {
            state.chainProgress[chainRow] = (short) progress;
        }

        long contract(CivilWarAllegiance side, CivilWarBand band,
                      ContractType type) {
            long patron = side == CivilWarAllegiance.CLAIMANT ? claimant : incumbent;
            long target = side == CivilWarAllegiance.CLAIMANT ? incumbent : claimant;
            long parent = side == CivilWarAllegiance.CLAIMANT ? chainId : -1L;
            long id = state.addContract(patron, target, parent, type,
                    ContractState.COMPLETED, 40, -1, -1, (byte) 1, -1,
                    market, 2, 1_000, 0, (byte) 25, (byte) 25, (byte) 100);
            int row = state.contractIndex(id);
            if (side == CivilWarAllegiance.INCUMBENT) {
                state.contractOpposedChainId[row] = chainId;
            }
            state.contractCivilWarBand[row] = band.toByte();
            return id;
        }
    }
}
