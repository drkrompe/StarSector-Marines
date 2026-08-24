package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractReputation;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;

import java.util.EnumSet;

/**
 * Drive offer expiry and successful term completion that aren't triggered by
 * mission resolution. The mission resolver bridge (see
 * {@code MissionResolver#applyContractBridge}) handles per-phase advancement
 * and victory/defeat flips; stationing defaults are evaluated earlier by
 * {@link StationingDefaultSystem} so a missed payment cannot be delivered
 * before the contract defaults.
 *
 * <ul>
 *   <li>{@link ContractState#OFFERED OFFERED} past its
 *       {@code contractOfferExpiresTick} → {@link ContractState#EXPIRED EXPIRED}
 *       (filters out of the offer list; the owning maintenance path may
 *       compact terminal rows while preserving contract IDs).</li>
 *   <li>Stationing contract past its {@code expiresTick} → COMPLETED if all phases
 *       cleared, FAILED otherwise.</li>
 * </ul>
 *
 * <p>See <code>contracts-nouns.md</code>.
 */
public final class ContractLifecycleSystem implements CampaignSystem {

    @Override
    public String name() {
        return "ContractLifecycle";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS, CampaignTable.PLAYER_REP,
                CampaignTable.PATRON_MEMORY);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (int i = 0; i < state.contractCount; i++) {
            ContractState s = ContractState.fromByte(state.contractState[i]);
            if (s.isTerminal()) continue;

            // OFFERED lapse — transition to EXPIRED. Callers retain the stable
            // contract ID; row storage is an owner-controlled maintenance detail.
            if (s == ContractState.OFFERED) {
                int offerExpires = state.contractOfferExpiresTick[i];
                if (offerExpires >= 0 && day >= offerExpires) {
                    state.contractState[i] = ContractState.EXPIRED.toByte();
                    continue;
                }
                // OFFERED rows have no patron-deposed or term-expiry semantics;
                // they're just sitting on the table. Skip the rest of the loop.
                continue;
            }

            int expires = state.contractExpiresTick[i];
            if (expires != -1 && day >= expires) {
                int phasesDone  = state.contractPhasesDone[i] & 0xFF;
                int phasesTotal = state.contractPhasesTotal[i] & 0xFF;
                if (phasesDone >= phasesTotal && !hasPendingResponse(state, i)) {
                    state.contractState[i] = ContractState.COMPLETED.toByte();
                    ContractReputation.completedForContract(
                            state, state.contractId[i], +1, day);
                } else {
                    state.contractState[i] = ContractState.FAILED.toByte();
                    ContractReputation.failedForContract(
                            state, state.contractId[i], -1, day);
                }
            }
        }
    }

    /**
     * Backstop for the G31 invariant: a stationing term can never be laundered into a
     * successful completion while the player still owes a response.
     * {@link StationingLapseSystem} normally resolves the payload first, but this holds
     * even if the deadline layer is bypassed or races the term boundary.
     */
    private static boolean hasPendingResponse(CampaignState state, int row) {
        if (!ContractType.fromByte(state.contractType[row]).isStationing()) return false;
        long contractId = state.contractId[row];
        return GarrisonDefensePayload.from(state, contractId) != null
                || StationingIncidentPayload.from(state, contractId) != null;
    }

}
