package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;

import java.util.EnumSet;

/** Arms rare, deterministic Cadre incidents without inventing their mission payload. */
public final class StationingIncidentSystem implements CampaignSystem {

    static final int MIN_INTERVAL_DAYS = 24;
    static final int INTERVAL_VARIANTS = 13;

    @Override
    public String name() {
        return "StationingIncident";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (int row = 0; row < state.contractCount; row++) {
            if (ContractType.fromByte(state.contractType[row]) != ContractType.CADRE) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                state.contractNextIncidentTick[row] = -1;
                state.contractIncidentPending[row] = 0;
                continue;
            }
            if (state.contractIncidentPending[row] != 0) continue;

            int next = state.contractNextIncidentTick[row];
            if (next < 0) {
                int acceptedDay = state.contractAcceptedTick[row] >= 0
                        ? state.contractAcceptedTick[row] : day;
                next = nextIncidentDay(state.contractId[row], acceptedDay);
                state.contractNextIncidentTick[row] = next;
            }
            int expires = state.contractExpiresTick[row];
            if (expires >= 0 && next >= expires) {
                state.contractNextIncidentTick[row] = Integer.MAX_VALUE;
                continue;
            }
            if (day >= next) {
                state.contractIncidentPending[row] = 1;
                state.contractNextIncidentTick[row] = -1;
            }
        }
    }

    /** Stable 24–36 day cadence derived from persisted contract identity and anchor day. */
    static int nextIncidentDay(long contractId, int afterDay) {
        long mixed = contractId * 0x9E3779B97F4A7C15L
                ^ (long) afterDay * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 33;
        int interval = MIN_INTERVAL_DAYS
                + (int) Math.floorMod(mixed, (long) INTERVAL_VARIANTS);
        long next = (long) afterDay + interval;
        return next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
    }
}
