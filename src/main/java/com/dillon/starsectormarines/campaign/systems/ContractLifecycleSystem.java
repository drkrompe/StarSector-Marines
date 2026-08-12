package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractReputation;

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
 *       (tombstoned per the SoA soft-delete invariant; filters out of the
 *       offer list).</li>
 *   <li>Stationing contract past its {@code expiresTick} → COMPLETED if all phases
 *       cleared, FAILED otherwise.</li>
 * </ul>
 *
 * <p>See <code>roadmap/campaign/contracts/overview.md</code> §"Lifecycle".
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
        return EnumSet.of(CampaignTable.CONTRACTS, CampaignTable.PLAYER_REP);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (int i = 0; i < state.contractCount; i++) {
            ContractState s = ContractState.fromByte(state.contractState[i]);
            if (s.isTerminal()) continue;

            // OFFERED lapse — soft-delete to EXPIRED. Tombstones in place so
            // the id→index map stays valid (architecture.md §1).
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

            long patronId = state.contractPatronHouseId[i];
            int expires = state.contractExpiresTick[i];
            if (expires != -1 && day >= expires) {
                int phasesDone  = state.contractPhasesDone[i] & 0xFF;
                int phasesTotal = state.contractPhasesTotal[i] & 0xFF;
                if (phasesDone >= phasesTotal) {
                    state.contractState[i] = ContractState.COMPLETED.toByte();
                    ContractReputation.completed(state, patronId, +1, day);
                } else {
                    state.contractState[i] = ContractState.FAILED.toByte();
                    ContractReputation.failed(state, patronId, -1, day);
                }
            }
        }
    }

}
