package com.dillon.starsectormarines.campaign;

/** One-way delivery policy for sealed kingmaker testimony snapshots. */
public final class KingmakerTestament {

    public enum Result {
        REVEALED,
        ALREADY_REVEALED,
        INVALID
    }

    private KingmakerTestament() {}

    public static Result reveal(CampaignState state, long testamentId) {
        if (state == null || testamentId <= 0L) return Result.INVALID;
        int row = state.kingmakerTestamentIndex(testamentId);
        if (row < 0) return Result.INVALID;
        KingmakerTestamentState status = KingmakerTestamentState.fromByte(
                state.kingmakerTestamentState[row]);
        if (status == KingmakerTestamentState.REVEALED) {
            return Result.ALREADY_REVEALED;
        }
        if (status != KingmakerTestamentState.SEALED) return Result.INVALID;
        state.kingmakerTestamentState[row] =
                KingmakerTestamentState.REVEALED.toByte();
        return Result.REVEALED;
    }
}
