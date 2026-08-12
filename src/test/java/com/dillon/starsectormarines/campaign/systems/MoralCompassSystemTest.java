package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequenceState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoralCompassSystemTest {

    @Test
    void attributedClaimantHandoffRecordsAntiInstitutionalChoiceOnce() {
        Fixture fixture = new Fixture();
        fixture.state.chainState[fixture.chainRow] = ChainState.RESOLVED.toByte();
        fixture.state.chainResolvedTick[fixture.chainRow] = 80;
        fixture.state.chainPlayerAllegiance[fixture.chainRow] =
                CivilWarAllegiance.CLAIMANT.toByte();
        fixture.state.chainPlayerContribution[fixture.chainRow] = 60;
        fixture.state.chainPlayerLastContributionTick[fixture.chainRow] = 70;
        long claim = fixture.state.prepareThroneClaim(fixture.chain,
                fixture.claimant, fixture.state.factionRegistry.intern("source"),
                fixture.state.factionRegistry.intern("result"), fixture.market, 80);
        int claimRow = fixture.state.throneClaimIndex(claim);
        fixture.state.throneClaimState[claimRow] = ThroneClaimState.APPLIED.toByte();
        fixture.state.throneClaimAppliedTick[claimRow] = 81;
        fixture.state.throneClaimPlayerConsequenceState[claimRow] =
                CivilWarPlayerConsequenceState.APPLIED.toByte();
        MoralCompassSystem system = new MoralCompassSystem();

        system.tick(fixture.state, 82);
        system.tick(fixture.state, 83);

        assertEquals(-20, fixture.state.moralInstitutionalism);
        assertEquals(0, fixture.state.moralMercy);
        assertEquals(0, fixture.state.moralIntegrity);
        assertEquals(0, fixture.state.moralStewardship);
        assertEquals(1, fixture.state.moralChoiceCount);
        assertEquals(MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                MoralChoiceSource.fromByte(
                        fixture.state.moralChoiceSourceType[0]));
        assertEquals(fixture.chain, fixture.state.moralChoiceSourceId[0]);
        assertEquals(81, fixture.state.moralChoiceHappenedTick[0]);
        assertEquals(82, fixture.state.moralChoiceRecordedTick[0]);
    }

    @Test
    void decisiveIncumbentPipelineRecordsInstitutionalChoice() {
        Fixture fixture = new Fixture();
        fixture.state.chainProgress[fixture.chainRow] = 120;
        long contract = fixture.state.addContract(fixture.incumbent,
                fixture.claimant, -1L, ContractType.PLANETARY_ASSAULT,
                ContractState.COMPLETED, 80, -1, -1, (byte) 3, -1,
                fixture.market, 2, 1_000, 0,
                (byte) 50, (byte) 50, (byte) 100);
        int contractRow = fixture.state.contractIndex(contract);
        fixture.state.contractOpposedChainId[contractRow] = fixture.chain;
        fixture.state.contractCivilWarBand[contractRow] =
                CivilWarBand.OPEN_CONFLICT.toByte();

        new CivilWarParticipationSystem().tick(fixture.state, 90);
        new CivilWarPlayerConsequenceSystem().tick(fixture.state, 90);
        MoralCompassSystem system = new MoralCompassSystem();
        system.tick(fixture.state, 90);
        system.tick(fixture.state, 91);

        assertEquals(20, fixture.state.moralInstitutionalism);
        assertEquals(1, fixture.state.moralChoiceCount);
        assertEquals(MoralChoiceSource.CIVIL_WAR_INCUMBENT,
                MoralChoiceSource.fromByte(
                        fixture.state.moralChoiceSourceType[0]));
        assertEquals(90, fixture.state.moralChoiceHappenedTick[0]);
    }

    @Test
    void unappliedOrAutonomousOutcomesRemainSilent() {
        Fixture fixture = new Fixture();
        fixture.state.chainState[fixture.chainRow] = ChainState.RESOLVED.toByte();
        fixture.state.chainResolvedTick[fixture.chainRow] = 80;

        new MoralCompassSystem().tick(fixture.state, 90);

        assertEquals(0, fixture.state.moralChoiceCount);
        assertEquals(0, fixture.state.moralInstitutionalism);
    }

    @Test
    void contributionBandsHaveLockedNarrativeWeight() {
        assertEquals(0, MoralCompassSystem.magnitude(0));
        assertEquals(5, MoralCompassSystem.magnitude(15));
        assertEquals(10, MoralCompassSystem.magnitude(30));
        assertEquals(10, MoralCompassSystem.magnitude(59));
        assertEquals(20, MoralCompassSystem.magnitude(60));
        assertEquals(20, MoralCompassSystem.magnitude(105));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long claimant = house(state, market, "Claimant");
        final long incumbent = house(state, market, "Incumbent");
        final long chain = state.addAutonomousChain(claimant, incumbent, market, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        final int chainRow = state.chainIndex(chain);

        private static long house(CampaignState state, int market, String name) {
            return state.addHouse(market, 1, HouseFlavor.FEUDAL,
                    HouseRank.TIER_3, HouseStatus.ACTIVE,
                    PatronArchetype.NEWCOMER, name);
        }
    }
}
