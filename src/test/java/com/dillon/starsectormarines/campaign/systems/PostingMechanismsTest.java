package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.Posting;
import com.dillon.starsectormarines.campaign.StationedStrength;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The other half of the posting's bargain: it earns nothing, and it still does the
 * work. Both mechanisms below are the shipped Garrison ones, asked of a row with no
 * employer.
 */
class PostingMechanismsTest {

    private static final String MARKET = "player_colony";

    /** Records the write instead of touching a market that only exists in the game. */
    private static final class RecordingSink
            implements StationedStrengthSystem.GroundDefenceSink {

        final List<String> calls = new ArrayList<>();

        @Override
        public boolean apply(String marketId, String modifierId, float value,
                            String description) {
            calls.add("apply " + marketId + " " + modifierId + " " + value);
            return true;
        }

        @Override
        public boolean remove(String marketId, String modifierId) {
            calls.add("remove " + marketId + " " + modifierId);
            return true;
        }
    }

    @Test
    void aPostedDetachmentDefendsItsMarketExactlyAsAGarrisonDoes() {
        CampaignState state = posting(ContractState.ACTIVE);
        RecordingSink sink = new RecordingSink();

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertEquals(List.of("apply " + MARKET + " "
                        + StationedStrengthSystem.modifierId(state.contractId[0]) + " "
                        + 12 * StationedStrength.GREEN_SEAT_WORTH),
                sink.calls);
        assertEquals(1, state.stationedStrengthCount);
    }

    @Test
    void aVanillaRaidArmsAPostingTheSameWay() {
        CampaignState state = posting(ContractState.ACTIVE);
        int marketSlot = state.contractMarketId[0];

        int armed = GarrisonDefenseTrigger.arm(state, 91L, marketSlot,
                GarrisonDefenseTriggerType.VANILLA_RAID, -1L,
                state.factionRegistry.intern("pirates"), 140f, 12);

        assertEquals(1, armed);
        assertEquals(ContractState.IN_PROGRESS,
                ContractState.fromByte(state.contractState[0]));
        assertEquals(91L, state.contractDefenseEventKey[0]);
        assertEquals(GarrisonDefenseTriggerType.VANILLA_RAID,
                GarrisonDefenseTriggerType.fromByte(state.contractDefenseTriggerType[0]));
        assertEquals(140f, state.contractDefenseAttackerStrength[0]);
        assertEquals(12, state.contractDefenseTriggeredTick[0]);
    }

    /** And it keeps defending while the raid is in the air. */
    @Test
    void anArmedPostingStillContributesItsStrength() {
        CampaignState state = posting(ContractState.IN_PROGRESS);
        RecordingSink sink = new RecordingSink();

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertEquals(1, sink.calls.size());
        assertEquals(1, state.stationedStrengthCount);
    }

    private static CampaignState posting(ContractState contractState) {
        CampaignState state = new CampaignState();
        state.addContract(Posting.NO_PATRON, -1L, -1L, ContractType.GARRISON,
                contractState, 10, -1, -1, (byte) 0, -1,
                state.marketRegistry.intern(MARKET), -1, 0, 0,
                (byte) 0, (byte) 0, (byte) 100);
        state.contractMarinesCommitted[0] = 12;
        return state;
    }
}
