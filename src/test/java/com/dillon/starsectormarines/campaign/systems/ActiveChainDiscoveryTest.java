package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChronicleBand;
import com.dillon.starsectormarines.campaign.ChronicleConfidence;
import com.dillon.starsectormarines.campaign.ChronicleEventType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveChainDiscoveryTest {

    @Test
    void touchedAutonomousChainBecomesIntimateRumorAfterFirstWindow() {
        Fixture fixture = new Fixture(HouseRank.TIER_1, (byte) 0xFF);
        fixture.state.ensureRepRow(fixture.target);
        DiscoveryPropagationSystem system = new DiscoveryPropagationSystem();

        system.tick(fixture.state, 7);
        assertEquals(0, fixture.state.chronicleCount);
        system.tick(fixture.state, 8);
        system.tick(fixture.state, 9);

        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(8, fixture.state.chainDiscoveredTick[fixture.chainRow]);
        assertEquals(8, fixture.state.chainLastDiscoveryCheckTick[fixture.chainRow]);
        assertEquals(ChronicleEventType.ACTIVE_CHAIN_RUMOR,
                ChronicleEventType.fromByte(fixture.state.chronicleEventType[0]));
        assertEquals(ChronicleBand.INTIMATE,
                ChronicleBand.fromByte(fixture.state.chronicleBand[0]));
        assertEquals(ChronicleConfidence.RUMOR,
                ChronicleConfidence.fromByte(fixture.state.chronicleConfidence[0]));
        assertEquals(1, fixture.state.chronicleHappenedTick[0]);
        assertEquals(8, fixture.state.chronicleLearnedTick[0]);
    }

    @Test
    void untouchedTierThreeChainBecomesEpicRumor() {
        Fixture fixture = new Fixture(HouseRank.TIER_3, (byte) 0xFF);

        new DiscoveryPropagationSystem().tick(fixture.state, 8);

        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(ChronicleBand.EPIC,
                ChronicleBand.fromByte(fixture.state.chronicleBand[0]));
    }

    @Test
    void civilWarBecomesEpicMarketWideRumor() {
        CampaignState state = new CampaignState();
        long actor = Fixture.house(state, HouseRank.TIER_3, "Claimant");
        long target = Fixture.house(state, HouseRank.TIER_3, "Coalition");
        long chainId = state.addAutonomousChain(actor, target, 1, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 0xFF, 1);
        int chainRow = state.chainIndex(chainId);

        new DiscoveryPropagationSystem().tick(state, 8);

        assertEquals(1, state.chronicleCount);
        assertEquals(8, state.chainDiscoveredTick[chainRow]);
        assertEquals(ChronicleBand.EPIC,
                ChronicleBand.fromByte(state.chronicleBand[0]));
        assertEquals(-1, state.chronicleIndustryId[0]);
    }

    @Test
    void untouchedLowTierChainStaysOutsideRumorEvaluation() {
        Fixture fixture = new Fixture(HouseRank.TIER_2, (byte) 0xFF);

        new DiscoveryPropagationSystem().tick(fixture.state, 30);

        assertEquals(0, fixture.state.chronicleCount);
        assertEquals(-1, fixture.state.chainLastDiscoveryCheckTick[fixture.chainRow]);
        assertEquals(-1, fixture.state.chainDiscoveredTick[fixture.chainRow]);
    }

    @Test
    void failedRollEvaluatesOnlyOncePerRelativeWindow() {
        Fixture fixture = new Fixture(HouseRank.TIER_1, (byte) 0);
        fixture.state.ensureRepRow(fixture.actor);
        DiscoveryPropagationSystem system = new DiscoveryPropagationSystem();

        system.tick(fixture.state, 8);
        assertEquals(8, fixture.state.chainLastDiscoveryCheckTick[fixture.chainRow]);
        system.tick(fixture.state, 14);
        assertEquals(8, fixture.state.chainLastDiscoveryCheckTick[fixture.chainRow]);
        system.tick(fixture.state, 15);

        assertEquals(15, fixture.state.chainLastDiscoveryCheckTick[fixture.chainRow]);
        assertEquals(0, fixture.state.chronicleCount);
        assertEquals(-1, fixture.state.chainDiscoveredTick[fixture.chainRow]);
    }

    @Test
    void playerBackedChainNeverEntersAutonomousRumorPath() {
        Fixture fixture = new Fixture(HouseRank.TIER_3, (byte) 0xFF);
        fixture.state.chainPatron[fixture.chainRow] = fixture.actor;

        new DiscoveryPropagationSystem().tick(fixture.state, 30);

        assertEquals(0, fixture.state.chronicleCount);
        assertEquals(-1, fixture.state.chainLastDiscoveryCheckTick[fixture.chainRow]);
    }

    @Test
    void exposureRisesWithProgressAndRollIsStable() {
        Fixture fixture = new Fixture(HouseRank.TIER_3, (byte) 64);
        fixture.state.chainThreshold[fixture.chainRow] = 100;
        fixture.state.chainProgress[fixture.chainRow] = 50;

        assertEquals(96,
                DiscoveryPropagationSystem.effectiveDiscoveryRisk(
                        fixture.state, fixture.chainRow));
        int first = DiscoveryPropagationSystem.discoveryRoll(42L, 3);
        assertEquals(first, DiscoveryPropagationSystem.discoveryRoll(42L, 3));
        assertTrue(first >= 0 && first <= 255);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final long actor;
        final long target;
        final int chainRow;

        Fixture(HouseRank rank, byte risk) {
            actor = house(state, rank, "Actor");
            target = house(state, HouseRank.TIER_1, "Target");
            long chainId = state.addAutonomousChain(actor, target, 1, 7,
                    rank.toByte(), ChainArchetype.CONSOLIDATE_STAKE,
                    (short) 45, risk, 1);
            chainRow = state.chainIndex(chainId);
        }

        private static long house(CampaignState state, HouseRank rank, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, rank, HouseStatus.ACTIVE,
                    PatronArchetype.NEWCOMER, name);
        }
    }
}
