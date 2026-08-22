package com.dillon.starsectormarines.ops.event;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.PlayerEventInbox;
import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.dillon.starsectormarines.campaign.systems.StationingLapseSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEventPresenterTest {

    private static final int ARMED = 40;
    private static final int DEADLINE =
            ARMED + StationingLapseSystem.GARRISON_RESPONSE_DAYS;

    @Test
    void anAcceptedOfferIsAcknowledged() {
        CampaignState state = pendingDefense();
        List<PlayerEventNotice> shown = new ArrayList<>();

        PlayerEventNotice presented = PlayerEventPresenter.presentNext(
                state, null, ARMED, accepting(shown));

        assertNotNull(presented);
        assertEquals(1, shown.size());
        assertTrue(PlayerEventInbox.isAcknowledged(state, presented));
        assertNull(PlayerEventPresenter.presentNext(state, null, ARMED, accepting(shown)));
        assertEquals(1, shown.size());
    }

    @Test
    void aRefusedOfferIsNotAcknowledgedAndComesBack() {
        CampaignState state = pendingDefense();
        List<PlayerEventNotice> shown = new ArrayList<>();

        assertNull(PlayerEventPresenter.presentNext(state, null, ARMED,
                (notice, day) -> false));
        assertEquals(0, state.contractNoticeAckStage[0]);

        // Still queued on a later frame — the domain state is the queue.
        assertNotNull(PlayerEventPresenter.presentNext(state, null, ARMED, accepting(shown)));
        assertEquals(1, shown.size());
    }

    @Test
    void anEmptyInboxOffersNothing() {
        CampaignState state = new CampaignState();
        List<PlayerEventNotice> shown = new ArrayList<>();

        assertNull(PlayerEventPresenter.presentNext(state, null, ARMED, accepting(shown)));
        assertTrue(shown.isEmpty());
    }

    @Test
    void theOfferCarriesTheDayTheCardWillCountDownFrom() {
        CampaignState state = pendingDefense();
        int[] offeredDay = {-1};

        PlayerEventPresenter.presentNext(state, null, ARMED + 2, (notice, day) -> {
            offeredDay[0] = day;
            return true;
        });

        assertEquals(ARMED + 2, offeredDay[0]);
    }

    @Test
    void nothingIsOfferedWhileTheUiIsBusy() {
        assertTrue(PlayerEventPresenter.isQuiet(false, false, false));
        assertFalse(PlayerEventPresenter.isQuiet(true, false, false));
        assertFalse(PlayerEventPresenter.isQuiet(false, true, false));
        assertFalse(PlayerEventPresenter.isQuiet(false, false, true));
    }

    private static PlayerEventPresenter.Offer accepting(List<PlayerEventNotice> shown) {
        return (notice, day) -> {
            shown.add(notice);
            return true;
        };
    }

    private static CampaignState pendingDefense() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 500, -1, (byte) 0,
                -1, 12, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 40;
        state.contractDefenseEventKey[0] = 900L;
        state.contractDefenseTriggeredTick[0] = ARMED;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractResponseDeadlineTick[0] = DEADLINE;
        return state;
    }
}
