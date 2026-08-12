package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChronicleBand;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryPropagationSystemTest {

    @Test
    void touchedHouseCreatesIntimateSnapshotExactlyOnce() {
        Fixture fixture = new Fixture(HouseRank.TIER_1);
        fixture.state.ensureRepRow(fixture.target);
        int chainRow = fixture.terminalChain(ChainState.RESOLVED);
        DiscoveryPropagationSystem system = new DiscoveryPropagationSystem();

        system.tick(fixture.state, 25);
        system.tick(fixture.state, 26);

        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(ChronicleBand.INTIMATE,
                ChronicleBand.fromByte(fixture.state.chronicleBand[0]));
        assertEquals(fixture.state.chainId[chainRow],
                fixture.state.chronicleSourceChainId[0]);
        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(fixture.state.chronicleChainOutcome[0]));
        assertEquals(fixture.actor, fixture.state.chronicleActorHouseId[0]);
        assertEquals(fixture.target, fixture.state.chronicleTargetHouseId[0]);
        assertEquals(20, fixture.state.chronicleHappenedTick[0]);
        assertEquals(25, fixture.state.chronicleLearnedTick[0]);
        assertEquals(25, fixture.state.chainDiscoveryProcessedTick[chainRow]);
    }

    @Test
    void untouchedTierThreeOutcomeCreatesEpicSnapshot() {
        Fixture fixture = new Fixture(HouseRank.TIER_3);
        int chainRow = fixture.terminalChain(ChainState.FAILED);

        new DiscoveryPropagationSystem().tick(fixture.state, 25);

        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(ChronicleBand.EPIC,
                ChronicleBand.fromByte(fixture.state.chronicleBand[0]));
        assertEquals(ChainState.FAILED,
                ChainState.fromByte(fixture.state.chronicleChainOutcome[0]));
        assertEquals(25, fixture.state.chainDiscoveryProcessedTick[chainRow]);
    }

    @Test
    void untouchedLowTierOutcomeIsProcessedButRemainsSilent() {
        Fixture fixture = new Fixture(HouseRank.TIER_2);
        int chainRow = fixture.terminalChain(ChainState.RESOLVED);

        new DiscoveryPropagationSystem().tick(fixture.state, 25);

        assertEquals(0, fixture.state.chronicleCount);
        assertEquals(25, fixture.state.chainDiscoveryProcessedTick[chainRow]);
    }

    @Test
    void civilWarOutcomeWaitsForPreparedHandoffToFinish() {
        Fixture fixture = new Fixture(HouseRank.TIER_3);
        long chainId = fixture.state.addAutonomousChain(fixture.actor, fixture.target,
                1, -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        int chainRow = fixture.state.chainIndex(chainId);
        fixture.state.chainState[chainRow] = ChainState.RESOLVED.toByte();
        fixture.state.chainResolvedTick[chainRow] = 20;
        int resultFaction = fixture.state.factionRegistry.intern("claimants");
        fixture.state.prepareThroneClaim(chainId, fixture.actor,
                fixture.state.houseFactionId[fixture.state.houseIndex(fixture.actor)],
                resultFaction, 1, 20);
        DiscoveryPropagationSystem system = new DiscoveryPropagationSystem();

        system.tick(fixture.state, 25);
        assertEquals(0, fixture.state.chronicleCount);
        assertEquals(-1, fixture.state.chainDiscoveryProcessedTick[chainRow]);

        fixture.state.throneClaimState[0] = ThroneClaimState.APPLIED.toByte();
        system.tick(fixture.state, 26);
        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(ChronicleBand.EPIC,
                ChronicleBand.fromByte(fixture.state.chronicleBand[0]));
        assertEquals(26, fixture.state.chainDiscoveryProcessedTick[chainRow]);
    }

    @Test
    void activeChainIsNeitherLearnedNorProcessed() {
        Fixture fixture = new Fixture(HouseRank.TIER_3);
        long chainId = fixture.state.addAutonomousChain(fixture.actor, fixture.target,
                1, 7, fixture.rank.toByte(), ChainArchetype.CONSOLIDATE_STAKE,
                (short) 10, (byte) 1, 1);
        int chainRow = fixture.state.chainIndex(chainId);

        new DiscoveryPropagationSystem().tick(fixture.state, 25);

        assertEquals(0, fixture.state.chronicleCount);
        assertEquals(-1, fixture.state.chainDiscoveryProcessedTick[chainRow]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final HouseRank rank;
        final long actor;
        final long target;

        Fixture(HouseRank rank) {
            this.rank = rank;
            this.actor = house(state, rank, "Actor");
            this.target = house(state, HouseRank.TIER_1, "Target");
        }

        int terminalChain(ChainState outcome) {
            long id = state.addAutonomousChain(actor, target, 1, 7, rank.toByte(),
                    ChainArchetype.CONSOLIDATE_STAKE, (short) 10, (byte) 1, 1);
            int row = state.chainIndex(id);
            state.chainState[row] = outcome.toByte();
            state.chainResolvedTick[row] = 20;
            return row;
        }

        private static long house(CampaignState state, HouseRank rank, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, rank, HouseStatus.ACTIVE,
                    PatronArchetype.NEWCOMER, name);
        }
    }
}
