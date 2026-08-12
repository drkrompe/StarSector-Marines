package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

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
            if (CampaignEventState.fromByte(state.eventState[row])
                    == CampaignEventState.PENDING_CHOICE
                    && day > state.eventDeadlineTick[row]) {
                state.eventState[row] = CampaignEventState.EXPIRED.toByte();
            }
        }
    }
}
