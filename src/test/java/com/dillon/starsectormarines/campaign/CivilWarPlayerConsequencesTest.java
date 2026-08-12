package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilWarPlayerConsequencesTest {

    @Test
    void claimantConsequencesScaleBySnapshottedContributionAndReplayOnce() {
        int[][] cases = {
                {15, 5, -8},
                {45, 10, -15},
                {60, 15, -25},
                {105, 15, -25}
        };
        for (int[] values : cases) {
            Fixture fixture = new Fixture();
            int claimRow = fixture.appliedClaim(values[0]);
            fixture.state.playerMrbRep = 77;

            assertEquals(CivilWarPlayerConsequences.Result.APPLIED,
                    CivilWarPlayerConsequences.applyClaimant(
                            fixture.state, claimRow, 120));
            assertEquals(values[1], fixture.rep(fixture.claimant));
            assertEquals(values[2], fixture.rep(fixture.incumbent));
            assertEquals(77, fixture.state.playerMrbRep);
            assertEquals(0, fixture.completed(fixture.claimant));
            assertEquals(0, fixture.failed(fixture.incumbent));
            assertEquals(CivilWarPlayerConsequenceState.APPLIED,
                    CivilWarPlayerConsequenceState.fromByte(
                            fixture.state.throneClaimPlayerConsequenceState[claimRow]));
            assertEquals(120,
                    fixture.state.throneClaimPlayerConsequenceAppliedTick[claimRow]);

            assertEquals(CivilWarPlayerConsequences.Result.ALREADY_HANDLED,
                    CivilWarPlayerConsequences.applyClaimant(
                            fixture.state, claimRow, 121));
            assertEquals(values[1], fixture.rep(fixture.claimant));
            assertEquals(values[2], fixture.rep(fixture.incumbent));
        }
    }

    @Test
    void decisiveIncumbentVictoryReversesSupportedAndOpposedHouses() {
        Fixture fixture = new Fixture();
        fixture.state.chainState[fixture.chainRow] = ChainState.FAILED.toByte();
        fixture.state.chainResolvedTick[fixture.chainRow] = 90;
        fixture.state.chainPlayerAllegiance[fixture.chainRow] =
                CivilWarAllegiance.INCUMBENT.toByte();
        fixture.state.chainPlayerContribution[fixture.chainRow] = 60;
        fixture.state.chainPlayerLastContributionTick[fixture.chainRow] = 90;

        assertEquals(CivilWarPlayerConsequences.Result.APPLIED,
                CivilWarPlayerConsequences.applyIncumbent(
                        fixture.state, fixture.chainRow, 90));
        assertEquals(15, fixture.rep(fixture.incumbent));
        assertEquals(-25, fixture.rep(fixture.claimant));
        assertEquals(CivilWarPlayerConsequenceState.APPLIED,
                CivilWarPlayerConsequenceState.fromByte(
                        fixture.state.chainPlayerConsequenceState[fixture.chainRow]));
        assertEquals(90,
                fixture.state.chainPlayerConsequenceAppliedTick[fixture.chainRow]);

        assertEquals(CivilWarPlayerConsequences.Result.ALREADY_HANDLED,
                CivilWarPlayerConsequences.applyIncumbent(
                        fixture.state, fixture.chainRow, 91));
        assertEquals(15, fixture.rep(fixture.incumbent));
        assertEquals(-25, fixture.rep(fixture.claimant));
    }

    @Test
    void preparedClaimWaitsAndAutonomousOrUnrelatedFailuresStayNeutral() {
        Fixture prepared = new Fixture();
        int preparedClaim = prepared.preparedClaim(60, CivilWarAllegiance.CLAIMANT);
        Fixture autonomous = new Fixture();
        int autonomousClaim = autonomous.preparedClaim(0, CivilWarAllegiance.NONE);
        autonomous.state.throneClaimState[autonomousClaim] =
                ThroneClaimState.APPLIED.toByte();
        Fixture staleFailure = new Fixture();
        staleFailure.state.chainState[staleFailure.chainRow] = ChainState.FAILED.toByte();
        staleFailure.state.chainResolvedTick[staleFailure.chainRow] = 100;
        staleFailure.state.chainPlayerAllegiance[staleFailure.chainRow] =
                CivilWarAllegiance.INCUMBENT.toByte();
        staleFailure.state.chainPlayerContribution[staleFailure.chainRow] = 30;
        staleFailure.state.chainPlayerLastContributionTick[staleFailure.chainRow] = 80;

        assertEquals(CivilWarPlayerConsequences.Result.NOT_READY,
                CivilWarPlayerConsequences.applyClaimant(
                        prepared.state, preparedClaim, 120));
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        prepared.state.throneClaimPlayerConsequenceState[preparedClaim]));
        assertEquals(CivilWarPlayerConsequences.Result.NOT_APPLICABLE,
                CivilWarPlayerConsequences.applyClaimant(
                        autonomous.state, autonomousClaim, 120));
        assertEquals(CivilWarPlayerConsequences.Result.NOT_APPLICABLE,
                CivilWarPlayerConsequences.applyIncumbent(
                        staleFailure.state, staleFailure.chainRow, 120));
        assertEquals(0, autonomous.state.repCount);
        assertEquals(0, staleFailure.state.repCount);
    }

    @Test
    void consequenceDeltasClampExistingReputation() {
        Fixture fixture = new Fixture();
        int claimRow = fixture.appliedClaim(60);
        int supported = fixture.state.ensureRepRow(fixture.claimant);
        int opposed = fixture.state.ensureRepRow(fixture.incumbent);
        fixture.state.repValue[supported] = 98;
        fixture.state.repValue[opposed] = -90;

        CivilWarPlayerConsequences.applyClaimant(fixture.state, claimRow, 120);

        assertEquals(100, fixture.state.repValue[supported]);
        assertEquals(-100, fixture.state.repValue[opposed]);
        assertEquals(0, fixture.state.repLastContractTick[supported]);
        assertEquals(0, fixture.state.repLastContractTick[opposed]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final long claimant = house(state, "Claimant");
        final long incumbent = house(state, "Incumbent");
        final long chain = state.addAutonomousChain(claimant, incumbent, 1, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        final int chainRow = state.chainIndex(chain);

        int appliedClaim(int contribution) {
            int row = preparedClaim(contribution, CivilWarAllegiance.CLAIMANT);
            state.throneClaimState[row] = ThroneClaimState.APPLIED.toByte();
            return row;
        }

        int preparedClaim(int contribution, CivilWarAllegiance allegiance) {
            state.chainState[chainRow] = ChainState.RESOLVED.toByte();
            state.chainResolvedTick[chainRow] = 100;
            state.chainPlayerAllegiance[chainRow] = allegiance.toByte();
            state.chainPlayerContribution[chainRow] = (short) contribution;
            state.chainPlayerLastContributionTick[chainRow] =
                    contribution > 0 ? 80 : -1;
            int sourceFaction = state.factionRegistry.intern("source");
            int resultFaction = state.factionRegistry.intern("result");
            long claim = state.prepareThroneClaim(chain, claimant,
                    sourceFaction, resultFaction, 1, 100);
            return state.throneClaimIndex(claim);
        }

        int rep(long houseId) {
            int row = state.repIndex(houseId);
            return row >= 0 ? state.repValue[row] : 0;
        }

        int completed(long houseId) {
            int row = state.repIndex(houseId);
            return row >= 0 ? state.repContractsCompleted[row] : 0;
        }

        int failed(long houseId) {
            int row = state.repIndex(houseId);
            return row >= 0 ? state.repContractsFailed[row] : 0;
        }

        private static long house(CampaignState state, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
