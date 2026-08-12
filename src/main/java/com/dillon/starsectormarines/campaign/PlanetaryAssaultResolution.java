package com.dillon.starsectormarines.campaign;

/** Pure contract-state transitions for one Planetary Assault phase outcome. */
public final class PlanetaryAssaultResolution {

    public enum Result {
        PHASE_ADVANCED,
        PHASE_REROLLED,
        CONTRACT_COMPLETED,
        CONTRACT_FAILED
    }

    private PlanetaryAssaultResolution() {}

    public static Result apply(CampaignState state, int row, boolean victory) {
        if (state == null || row < 0 || row >= state.contractCount
                || ContractType.fromByte(state.contractType[row])
                != ContractType.PLANETARY_ASSAULT) {
            return null;
        }
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState != ContractState.OFFERED
                && contractState != ContractState.ACTIVE
                && contractState != ContractState.IN_PROGRESS) {
            return null;
        }
        int done = state.contractPhasesDone[row] & 0xFF;
        int total = state.contractPhasesTotal[row] & 0xFF;
        if (total < 3 || total > 5 || done >= total) return null;

        if (victory) {
            done++;
            state.contractPhasesDone[row] = (byte) done;
            state.contractPhaseAttempts[row] = 0;
            if (done >= total) {
                state.contractState[row] = ContractState.COMPLETED.toByte();
                return Result.CONTRACT_COMPLETED;
            }
            state.contractState[row] = ContractState.IN_PROGRESS.toByte();
            return Result.PHASE_ADVANCED;
        }

        state.contractPhaseAttempts[row] = saturatingIncrement(
                state.contractPhaseAttempts[row]);
        if (done == total - 1) {
            state.contractState[row] = ContractState.FAILED.toByte();
            return Result.CONTRACT_FAILED;
        }
        state.contractState[row] = ContractState.IN_PROGRESS.toByte();
        return Result.PHASE_REROLLED;
    }

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, value) + 1;
    }
}
