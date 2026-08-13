package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;

import java.util.EnumSet;

/** Expires unseen black-swan choices without treating silence as refusal. */
public final class CampaignEventLifecycleSystem implements CampaignSystem {

    @Override
    public String name() {
        return "CampaignEventLifecycle";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.EVENTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.EVENTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int row = 0; row < state.eventCount; row++) {
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE
                    && day > state.eventDeadlineTick[row]) {
                state.eventState[row] = CampaignEventState.EXPIRED.toByte();
                continue;
            }
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.DEFECTOR_ASYLUM) {
                continue;
            }
            if (eventState == CampaignEventState.COMMITTED
                    && day >= state.eventFollowupTick[row]) {
                DefectorAsylumEvent.advanceToFollowup(
                        state, state.eventId[row], day);
            } else if (eventState == CampaignEventState.PENDING_FOLLOWUP
                    && day > state.eventFollowupDeadlineTick[row]) {
                DefectorAsylumEvent.protect(state, state.eventId[row], day);
            }
        }
    }
}
