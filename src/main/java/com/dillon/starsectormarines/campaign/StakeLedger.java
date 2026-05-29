package com.dillon.starsectormarines.campaign;

/**
 * Higher-level operations on the {@code stakes[]} table — the "transfer N stake
 * from A to B" layer that {@link CampaignState}'s low-level mutators deliberately
 * leave out (see that class's header and {@code architecture.md} §2). Stateless;
 * every method operates on a passed-in {@link CampaignState}.
 *
 * <p>This is the single primitive every stake-moving caller routes through —
 * the player path ({@code MissionResolver}'s contract bridge today) and the
 * autonomous drift / chain loops to come ({@code living-world/overview.md}
 * Slices C–D). Centralising it keeps the conservation + ceiling invariants in
 * one place.
 *
 * <h2>Tombstone-at-zero (stake soft-delete)</h2>
 * Stakes have no {@code status} column, so a depleted stake is represented by
 * {@code share == 0}, not row removal — physical removal would compact the array
 * and break {@code stakeIndexById} (architecture.md §1, "compaction is
 * forbidden"). A zeroed row is inert: it contributes nothing to claimed totals
 * and is revived in place if the same house re-acquires the industry.
 *
 * <h2>Invariant this layer maintains</h2>
 * At most one row per {@code (houseId, marketId, industryId)} triple — seeding
 * creates each once, and {@link #seizeShare} reuses the existing (or tombstoned)
 * row rather than appending a duplicate. {@link #findStake} relies on it.
 */
public final class StakeLedger {

    /** Stake shares live in {@code 0..255} (a byte's worth, stored in a {@code short}). */
    public static final int SHARE_CEILING = 255;

    private StakeLedger() {}

    /**
     * Row index of {@code houseId}'s stake on {@code (marketIdx, industryIdx)},
     * or {@code -1} if it holds none. Matches regardless of share so a tombstoned
     * (zeroed) row is found and revived rather than duplicated.
     *
     * <p>Linear scan — acceptable per architecture.md §5 because stake moves are
     * once-per-mission (player) or once-per-house-per-week (drift), never an
     * inner-loop hot path. If the drift loop later proves this hot, a composite
     * {@code (house,market,industry) → row} index is the escalation.
     */
    public static int findStake(CampaignState state, long houseId, int marketIdx, int industryIdx) {
        for (int i = 0; i < state.stakeCount; i++) {
            if (state.stakeHouseId[i] != houseId) continue;
            if (state.stakeMarketId[i] != marketIdx) continue;
            if (state.stakeIndustryId[i] != industryIdx) continue;
            return i;
        }
        return -1;
    }

    /** {@code houseId}'s current share of {@code (marketIdx, industryIdx)} in {@code 0..255}. */
    public static int shareOf(CampaignState state, long houseId, int marketIdx, int industryIdx) {
        int row = findStake(state, houseId, marketIdx, industryIdx);
        return row < 0 ? 0 : state.stakeShare[row];
    }

    /** Sum of every house's share on {@code (marketIdx, industryIdx)} — the claimed slice of the pie. */
    public static int totalClaimedShare(CampaignState state, int marketIdx, int industryIdx) {
        int total = 0;
        for (int i = 0; i < state.stakeCount; i++) {
            if (state.stakeMarketId[i] != marketIdx) continue;
            if (state.stakeIndustryId[i] != industryIdx) continue;
            total += state.stakeShare[i];
        }
        return total;
    }

    /**
     * Open (faction-baseline) share on {@code (marketIdx, industryIdx)} — the
     * slice no house holds, available for a winner to expand into.
     */
    public static int unclaimedShare(CampaignState state, int marketIdx, int industryIdx) {
        return Math.max(0, SHARE_CEILING - totalClaimedShare(state, marketIdx, industryIdx));
    }

    /**
     * Moves up to {@code amount} of share to {@code toHouseId} on
     * {@code (marketIdx, industryIdx)}, sourced first from {@code fromHouseId}'s
     * holding and then — if the loser can't cover it — from the unclaimed
     * remainder. Returns the share actually granted to the winner (0 if nothing
     * was available to move).
     *
     * <p>Rationale for the two-source model: the contract layer can target a
     * rival who doesn't actually hold the struck industry, so a pure zero-sum
     * transfer would no-op and leave no mark. Topping up from the open pool means
     * a successful strike always plants the patron a foothold — they expand into
     * the rival's turf even when there was little of the rival's there to take.
     *
     * <p>Conservation: the winner's gain is bounded by {@code loserHeld +
     * unclaimedBefore}, so the claimed total on the industry never exceeds
     * {@link #SHARE_CEILING}. The loser is reduced to a floor of zero (tombstoned,
     * never removed). No-ops on {@code amount <= 0} or a self-transfer.
     */
    public static int seizeShare(CampaignState state, long fromHouseId, long toHouseId,
                                 int marketIdx, int industryIdx, int amount) {
        if (amount <= 0 || fromHouseId == toHouseId) return 0;

        // Snapshot the open pool before touching the loser — freeing the loser's
        // share must not feed back into the unclaimed draw (would double-count).
        int unclaimedBefore = unclaimedShare(state, marketIdx, industryIdx);

        int taken = 0;
        int fromRow = findStake(state, fromHouseId, marketIdx, industryIdx);
        if (fromRow >= 0) {
            int held = state.stakeShare[fromRow];
            taken = Math.min(amount, held);
            state.stakeShare[fromRow] = (short) (held - taken); // tombstones at 0
        }

        int fromUnclaimed = Math.min(amount - taken, unclaimedBefore);
        int gain = taken + fromUnclaimed;
        if (gain <= 0) return 0;

        int toRow = findStake(state, toHouseId, marketIdx, industryIdx);
        if (toRow < 0) {
            state.addStake(toHouseId, marketIdx, industryIdx, (short) gain);
        } else {
            int cur = state.stakeShare[toRow];
            state.stakeShare[toRow] = (short) Math.min(SHARE_CEILING, cur + gain);
        }
        return gain;
    }
}
