package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChronicleEventType;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequenceState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.KingmakerTestamentState;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.ThroneClaimConsequenceState;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugKingmakerTestamentSpawnerTest {

    @Test
    void spawnBuildsProductionShapedCapstoneAndReplaysExactlyOnce()
            throws Exception {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("jangala");
        int otherMarket = state.marketRegistry.intern("asharu");
        int faction = state.factionRegistry.intern("hegemony");
        house(state, otherMarket, faction, "Unpaired");
        long claimant = house(state, market, faction, "Aster");
        long deposed = house(state, market, faction, "Boreal");
        house(state, market, faction, "Cygnus");

        long testament = DebugKingmakerTestamentSpawner.spawn(state, 90);

        assertTrue(testament > 0L);
        assertEquals(1, state.chainCount);
        assertEquals(1, state.throneClaimCount);
        assertEquals(1, state.kingmakerTestamentCount);
        assertEquals(2, state.chronicleCount);
        assertEquals(claimant, state.chainActorHouseId[0]);
        assertEquals(deposed, state.chainTarget[0]);
        assertEquals(HouseRank.TIER_4,
                HouseRank.fromByte(state.houseRank[state.houseIndex(claimant)]));
        assertEquals(ThroneClaimState.APPLIED,
                ThroneClaimState.fromByte(state.throneClaimState[0]));
        assertEquals(CivilWarPlayerConsequenceState.APPLIED,
                CivilWarPlayerConsequenceState.fromByte(
                        state.throneClaimPlayerConsequenceState[0]));
        assertEquals(ThroneClaimConsequenceState.PENDING,
                ThroneClaimConsequenceState.fromByte(
                        state.throneClaimConsequenceState[0]));
        assertEquals(KingmakerTestamentState.SEALED,
                KingmakerTestamentState.fromByte(state.kingmakerTestamentState[0]));
        assertEquals(1, claimantMoralRows(state));
        assertEquals(1, chronicleRows(state,
                ChronicleEventType.KINGMAKER_TESTAMENT));
        assertEquals(1, chronicleRows(state,
                ChronicleEventType.THRONE_CLAIM_APPLIED));

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);
        long replay = DebugKingmakerTestamentSpawner.spawn(state, 120);

        assertEquals(testament, replay);
        assertEquals(1, state.chainCount);
        assertEquals(1, state.throneClaimCount);
        assertEquals(1, state.kingmakerTestamentCount);
        assertEquals(2, state.chronicleCount);
        assertEquals(1, claimantMoralRows(state));
    }

    @Test
    void noEligiblePairLeavesStateUntouched() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("jangala");
        int faction = state.factionRegistry.intern("hegemony");
        house(state, market, faction, "Alone");
        int factionCount = state.factionRegistry.size();

        assertEquals(-1L, DebugKingmakerTestamentSpawner.spawn(state, 90));
        assertEquals(0, state.chainCount);
        assertEquals(0, state.throneClaimCount);
        assertEquals(0, state.kingmakerTestamentCount);
        assertEquals(0, state.chronicleCount);
        assertEquals(factionCount, state.factionRegistry.size());
    }

    private static int claimantMoralRows(CampaignState state) {
        int count = 0;
        for (int row = 0; row < state.moralChoiceCount; row++) {
            if (MoralChoiceSource.fromByte(state.moralChoiceSourceType[row])
                    == MoralChoiceSource.CIVIL_WAR_CLAIMANT) {
                count++;
            }
        }
        return count;
    }

    private static int chronicleRows(CampaignState state,
                                     ChronicleEventType type) {
        int count = 0;
        for (int row = 0; row < state.chronicleCount; row++) {
            if (ChronicleEventType.fromByte(state.chronicleEventType[row]) == type) {
                count++;
            }
        }
        return count;
    }

    private static long house(CampaignState state, int market, int faction,
                              String name) {
        return state.addHouse(market, faction, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE,
                PatronArchetype.NEWCOMER, name);
    }
}
