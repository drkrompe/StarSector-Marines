package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import com.dillon.starsectormarines.campaign.ThroneClaimWriteback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThroneClaimResolutionSystemTest {

    @Test
    void appliedWritebackFinalizesLocalRankAndIdentityExactlyOnce() {
        Fixture fixture = new Fixture();
        FakeWriteback writeback = new FakeWriteback(ThroneClaimWriteback.Result.APPLIED);
        ThroneClaimResolutionSystem system = new ThroneClaimResolutionSystem(writeback);

        system.tick(fixture.state, 20);
        system.tick(fixture.state, 21);

        assertEquals(1, writeback.calls);
        assertEquals("source", writeback.sourceFactionId);
        assertEquals("claimants", writeback.resultFactionId);
        assertEquals("market", writeback.marketId);
        assertEquals(ThroneClaimState.APPLIED,
                ThroneClaimState.fromByte(fixture.state.throneClaimState[0]));
        assertEquals(20, fixture.state.throneClaimAppliedTick[0]);
        assertEquals(HouseRank.TIER_4,
                HouseRank.fromByte(fixture.state.houseRank[fixture.actorRow]));
        assertEquals(0, fixture.state.housePromotionProgress[fixture.actorRow]);
        assertEquals(fixture.resultFaction,
                fixture.state.houseFactionId[fixture.actorRow]);
        assertEquals(HouseAmbition.NONE,
                HouseAmbition.fromByte(fixture.state.houseAmbition[fixture.actorRow]));
        assertEquals(-1L, fixture.state.houseAmbitionTarget[fixture.actorRow]);
    }

    @Test
    void alreadyAppliedPostconditionRepairsPreparedLocalState() {
        Fixture fixture = new Fixture();

        new ThroneClaimResolutionSystem(new FakeWriteback(
                ThroneClaimWriteback.Result.ALREADY_APPLIED)).tick(fixture.state, 20);

        assertEquals(ThroneClaimState.APPLIED,
                ThroneClaimState.fromByte(fixture.state.throneClaimState[0]));
        assertEquals(HouseRank.TIER_4,
                HouseRank.fromByte(fixture.state.houseRank[fixture.actorRow]));
    }

    @Test
    void retryLeavesPreparedStateForLaterSuccess() {
        Fixture fixture = new Fixture();
        FakeWriteback writeback = new FakeWriteback(ThroneClaimWriteback.Result.RETRY);
        ThroneClaimResolutionSystem system = new ThroneClaimResolutionSystem(writeback);

        system.tick(fixture.state, 20);
        assertEquals(ThroneClaimState.PREPARED,
                ThroneClaimState.fromByte(fixture.state.throneClaimState[0]));
        assertEquals(-1, fixture.state.throneClaimAppliedTick[0]);
        writeback.result = ThroneClaimWriteback.Result.APPLIED;
        system.tick(fixture.state, 21);

        assertEquals(2, writeback.calls);
        assertEquals(ThroneClaimState.APPLIED,
                ThroneClaimState.fromByte(fixture.state.throneClaimState[0]));
        assertEquals(21, fixture.state.throneClaimAppliedTick[0]);
    }

    @Test
    void rejectedOrInvalidPreparedClaimFailsWithoutHouseMutation() {
        Fixture rejected = new Fixture();
        new ThroneClaimResolutionSystem(new FakeWriteback(
                ThroneClaimWriteback.Result.REJECTED)).tick(rejected.state, 20);
        Fixture invalid = new Fixture();
        invalid.state.chainState[invalid.chainRow] = ChainState.ACTIVE.toByte();
        FakeWriteback unused = new FakeWriteback(ThroneClaimWriteback.Result.APPLIED);
        new ThroneClaimResolutionSystem(unused).tick(invalid.state, 20);

        assertEquals(ThroneClaimState.FAILED,
                ThroneClaimState.fromByte(rejected.state.throneClaimState[0]));
        assertEquals(HouseRank.TIER_3,
                HouseRank.fromByte(rejected.state.houseRank[rejected.actorRow]));
        assertEquals(ThroneClaimState.FAILED,
                ThroneClaimState.fromByte(invalid.state.throneClaimState[0]));
        assertEquals(0, unused.calls);
    }

    @Test
    void adapterExceptionIsRetryable() {
        Fixture fixture = new Fixture();
        ThroneClaimWriteback throwing = (source, result, market) -> {
            throw new IllegalStateException("sector unavailable");
        };

        new ThroneClaimResolutionSystem(throwing).tick(fixture.state, 20);

        assertEquals(ThroneClaimState.PREPARED,
                ThroneClaimState.fromByte(fixture.state.throneClaimState[0]));
        assertEquals(HouseRank.TIER_3,
                HouseRank.fromByte(fixture.state.houseRank[fixture.actorRow]));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int sourceFaction = state.factionRegistry.intern("source");
        final int resultFaction = state.factionRegistry.intern("claimants");
        final int market = state.marketRegistry.intern("market");
        final long actor = state.addHouse(market, sourceFaction, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE, PatronArchetype.NEWCOMER,
                "Claimant");
        final long target = state.addHouse(market, sourceFaction, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED,
                "Coalition");
        final int actorRow = state.houseIndex(actor);
        final int chainRow;

        Fixture() {
            state.housePromotionProgress[actorRow] = 1000;
            state.houseAmbition[actorRow] = HouseAmbition.CLAIM_THRONE.toByte();
            state.houseAmbitionTarget[actorRow] = sourceFaction;
            long chain = state.addAutonomousChain(actor, target, market, -1,
                    HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                    (short) 180, (byte) 128, 1);
            chainRow = state.chainIndex(chain);
            state.chainState[chainRow] = ChainState.RESOLVED.toByte();
            state.chainResolvedTick[chainRow] = 10;
            state.prepareThroneClaim(chain, actor, sourceFaction, resultFaction,
                    market, 10);
        }
    }

    private static final class FakeWriteback implements ThroneClaimWriteback {
        Result result;
        int calls;
        String sourceFactionId;
        String resultFactionId;
        String marketId;

        FakeWriteback(Result result) {
            this.result = result;
        }

        @Override
        public Result apply(String sourceFactionId, String resultFactionId,
                            String marketId) {
            calls++;
            this.sourceFactionId = sourceFactionId;
            this.resultFactionId = resultFactionId;
            this.marketId = marketId;
            return result;
        }
    }
}
