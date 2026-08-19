package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequenceState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.KingmakerTestamentState;
import com.dillon.starsectormarines.campaign.MoralChoiceRecorder;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingmakerTestamentSystemTest {

    @Test
    void decisiveClaimantVictorySealsLedgerBoundedSnapshotOnce() {
        Fixture fixture = new Fixture(60, true);
        MoralChoiceRecorder.record(fixture.state,
                MoralChoiceSource.CIVILIAN_RESCUE_SAVED, 40L,
                15, 0, 20, 0, 20, 20);
        MoralChoiceRecorder.record(fixture.state,
                MoralChoiceSource.DEFECTOR_ASYLUM, 50L,
                0, -25, -10, 0, 40, 40);
        fixture.recordClaimantChoice();
        KingmakerTestamentSystem system = new KingmakerTestamentSystem();

        system.tick(fixture.state, 82);
        system.tick(fixture.state, 83);

        assertEquals(1, fixture.state.kingmakerTestamentCount);
        int row = fixture.state.kingmakerTestamentIndexForClaim(fixture.claim);
        assertEquals(fixture.chain,
                fixture.state.kingmakerTestamentSourceChainId[row]);
        assertEquals(fixture.claimant,
                fixture.state.kingmakerTestamentClaimantHouseId[row]);
        assertEquals(fixture.incumbent,
                fixture.state.kingmakerTestamentDeposedHouseId[row]);
        assertEquals(15, fixture.state.kingmakerTestamentMercy[row]);
        assertEquals(-25, fixture.state.kingmakerTestamentIntegrity[row]);
        assertEquals(10, fixture.state.kingmakerTestamentStewardship[row]);
        assertEquals(-20,
                fixture.state.kingmakerTestamentInstitutionalism[row]);
        assertEquals(3,
                fixture.state.kingmakerTestamentMoralChoiceCount[row]);
        assertEquals(82, fixture.state.kingmakerTestamentSealedTick[row]);
        assertEquals(KingmakerTestamentState.SEALED,
                KingmakerTestamentState.fromByte(
                        fixture.state.kingmakerTestamentState[row]));

        MoralChoiceRecorder.record(fixture.state,
                MoralChoiceSource.CIVILIAN_RESCUE_REFUSED, 90L,
                -5, 0, -10, 0, 84, 84);
        system.tick(fixture.state, 84);

        assertEquals(1, fixture.state.kingmakerTestamentCount);
        assertEquals(15, fixture.state.kingmakerTestamentMercy[row]);
        assertEquals(10, fixture.state.kingmakerTestamentStewardship[row]);
        assertEquals(3,
                fixture.state.kingmakerTestamentMoralChoiceCount[row]);
    }

    @Test
    void nonDecisiveOrUnrecordedClaimantOutcomesDoNotQualify() {
        Fixture nonDecisive = new Fixture(59, true);
        nonDecisive.recordClaimantChoice();
        Fixture unrecorded = new Fixture(60, true);
        Fixture mistimed = new Fixture(60, true);
        MoralChoiceRecorder.record(mistimed.state,
                MoralChoiceSource.CIVIL_WAR_CLAIMANT, mistimed.chain,
                0, 0, 0, -20, 80, 82);

        KingmakerTestamentSystem system = new KingmakerTestamentSystem();
        system.tick(nonDecisive.state, 82);
        system.tick(unrecorded.state, 82);
        system.tick(mistimed.state, 82);

        assertEquals(0, nonDecisive.state.kingmakerTestamentCount);
        assertEquals(0, unrecorded.state.kingmakerTestamentCount);
        assertEquals(0, mistimed.state.kingmakerTestamentCount);
    }

    @Test
    void autonomousPendingAndMalformedOutcomesFailClosed() {
        Fixture autonomous = new Fixture(60, false);
        autonomous.recordClaimantChoice();
        Fixture pending = new Fixture(60, true);
        pending.recordClaimantChoice();
        pending.state.throneClaimState[pending.claimRow] =
                ThroneClaimState.PREPARED.toByte();
        Fixture malformed = new Fixture(60, true);
        malformed.recordClaimantChoice();
        malformed.state.chainTarget[malformed.chainRow] = 999L;

        KingmakerTestamentSystem system = new KingmakerTestamentSystem();
        system.tick(autonomous.state, 82);
        system.tick(pending.state, 82);
        system.tick(malformed.state, 82);

        assertEquals(0, autonomous.state.kingmakerTestamentCount);
        assertEquals(0, pending.state.kingmakerTestamentCount);
        assertEquals(0, malformed.state.kingmakerTestamentCount);
    }

    @Test
    void diplomacyCompletionIsNotRequired() {
        Fixture fixture = new Fixture(60, true);
        fixture.recordClaimantChoice();

        new KingmakerTestamentSystem().tick(fixture.state, 82);

        assertEquals(1, fixture.state.kingmakerTestamentCount);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long claimant = house(state, market, "Claimant");
        final long incumbent = house(state, market, "Incumbent");
        final long chain;
        final int chainRow;
        final long claim;
        final int claimRow;

        Fixture(int contribution, boolean attributed) {
            chain = state.addAutonomousChain(claimant, incumbent, market, -1,
                    HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                    (short) 180, (byte) 128, 1);
            chainRow = state.chainIndex(chain);
            state.chainState[chainRow] = ChainState.RESOLVED.toByte();
            state.chainResolvedTick[chainRow] = 80;
            state.chainPlayerAllegiance[chainRow] = attributed
                    ? CivilWarAllegiance.CLAIMANT.toByte()
                    : CivilWarAllegiance.NONE.toByte();
            state.chainPlayerContribution[chainRow] = (short) contribution;
            state.chainPlayerLastContributionTick[chainRow] = attributed ? 70 : -1;
            claim = state.prepareThroneClaim(chain, claimant,
                    state.factionRegistry.intern("source"),
                    state.factionRegistry.intern("result"), market, 80);
            claimRow = state.throneClaimIndex(claim);
            state.throneClaimState[claimRow] = ThroneClaimState.APPLIED.toByte();
            state.throneClaimAppliedTick[claimRow] = 81;
            if (attributed) {
                state.throneClaimPlayerConsequenceState[claimRow] =
                        CivilWarPlayerConsequenceState.APPLIED.toByte();
                state.throneClaimPlayerConsequenceAppliedTick[claimRow] = 82;
            }
        }

        void recordClaimantChoice() {
            MoralChoiceRecorder.record(state,
                    MoralChoiceSource.CIVIL_WAR_CLAIMANT, chain,
                    0, 0, 0, -20, 81, 82);
        }

        private static long house(CampaignState state, int market, String name) {
            return state.addHouse(market, 1, HouseFlavor.FEUDAL,
                    HouseRank.TIER_3, HouseStatus.ACTIVE,
                    PatronArchetype.NEWCOMER, name);
        }
    }
}
