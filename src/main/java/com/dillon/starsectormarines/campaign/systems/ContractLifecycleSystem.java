package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/**
 * Tick phase 4: drive contract state transitions that aren't triggered by
 * mission resolution. The mission resolver bridge (see
 * {@code MissionResolver#applyContractBridge}) handles per-phase advancement
 * and victory/defeat flips; this system handles the time-driven and
 * patron-driven transitions:
 *
 * <ul>
 *   <li>{@link ContractState#OFFERED OFFERED} past its
 *       {@code contractOfferExpiresTick} → {@link ContractState#EXPIRED EXPIRED}
 *       (tombstoned per the SoA soft-delete invariant; filters out of the
 *       offer list).</li>
 *   <li>Patron DEPOSED → contract DEFAULTED (spawns extraction mission downstream).</li>
 *   <li>Stationing contract past its {@code expiresTick} → COMPLETED if all phases
 *       cleared, FAILED otherwise.</li>
 *   <li>Random monthly default roll scaled by patron {@code housePower} — TODO when
 *       housePower is actually populated by other systems.</li>
 * </ul>
 *
 * <p>See <code>roadmap/campaign/contracts/overview.md</code> §"Default mechanics".
 */
public final class ContractLifecycleSystem implements CampaignSystem {

    @Override
    public String name() {
        return "ContractLifecycle";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.CONTRACTS);
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
            int patronRow = state.houseIndex(patronId);
            if (patronRow >= 0
                    && HouseStatus.fromByte(state.houseStatus[patronRow]) == HouseStatus.DEPOSED) {
                state.contractState[i] = ContractState.DEFAULTED.toByte();
                continue;
            }

            int expires = state.contractExpiresTick[i];
            if (expires != -1 && day >= expires) {
                int phasesDone  = state.contractPhasesDone[i] & 0xFF;
                int phasesTotal = state.contractPhasesTotal[i] & 0xFF;
                if (phasesDone >= phasesTotal) {
                    state.contractState[i] = ContractState.COMPLETED.toByte();
                    bumpRep(state, patronId, +1, day, true);
                } else {
                    state.contractState[i] = ContractState.FAILED.toByte();
                    bumpRep(state, patronId, -1, day, false);
                }
            }

            // Monthly random default roll lives here too once housePower is
            // populated — until then it's a no-op.
        }
    }

    private static void bumpRep(CampaignState state, long patronId, int repDelta, int day, boolean completed) {
        int repRow = state.ensureRepRow(patronId);
        state.repValue[repRow] = Math.max(-100, Math.min(100, state.repValue[repRow] + repDelta));
        state.repLastContractTick[repRow] = day;
        if (completed) {
            int n = (state.repContractsCompleted[repRow] & 0xFFFF) + 1;
            if (n > 65535) n = 65535;
            state.repContractsCompleted[repRow] = (short) n;
        } else {
            int n = (state.repContractsFailed[repRow] & 0xFFFF) + 1;
            if (n > 65535) n = 65535;
            state.repContractsFailed[repRow] = (short) n;
        }
    }
}
