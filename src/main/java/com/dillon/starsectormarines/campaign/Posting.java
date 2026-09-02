package com.dillon.starsectormarines.campaign;

/**
 * Recognition rules for a <b>posting</b>: stationing without a patron
 * ({@code contracts-nouns.md}).
 *
 * <p>The player's own detachment bound to one of the player's own markets, with no
 * retainer, no term, and no commercial consequence. It reuses the Garrison response
 * and settlement machinery as a {@link ContractType#GARRISON} row whose employer is
 * nobody, which is the only way the polity can hold a detachment without becoming a
 * client ({@code meta-progression.md}).
 *
 * <p>The employer slot is the discriminator: a posting carries
 * {@link CampaignState#contractPatronHouseId} {@code < 0}. That is a real sentinel
 * rather than an absent house id — {@code 0L} is a legal house id here — so every
 * reader that asks "is there a patron" must ask it the same way.
 */
public final class Posting {

    /** The employer slot of a row nobody employs. */
    public static final long NO_PATRON = -1L;

    /**
     * Stands in for "the player is about to post a detachment here" where a contract id
     * is expected, since the row does not exist until
     * {@code PostingService.post} creates it. Distinct from {@code -1L}, which the
     * stationing screen already uses for "nothing selected".
     */
    public static final long DRAFT_CONTRACT_ID = -2L;

    private Posting() {}

    /** True when contract {@code row} is a Garrison row with no employer. */
    public static boolean isPosting(CampaignState state, int row) {
        if (state == null || row < 0 || row >= state.contractCount) return false;
        return ContractType.fromByte(state.contractType[row]) == ContractType.GARRISON
                && state.contractPatronHouseId[row] < 0L;
    }

    /** True when {@code contractId} names a Garrison row with no employer. */
    public static boolean isPostingId(CampaignState state, long contractId) {
        return state != null && isPosting(state, state.contractIndex(contractId));
    }

    /**
     * The live posting row at {@code marketSlot}, or {@code -1}. At most one posting
     * stands at a market at a time; a second is refused at creation rather than
     * merged, so the first match is the answer.
     *
     * @param marketSlot the market's slot in {@link CampaignState#marketRegistry}
     */
    public static int activeRowAt(CampaignState state, int marketSlot) {
        if (state == null || marketSlot < 0) return -1;
        for (int row = 0; row < state.contractCount; row++) {
            if (state.contractMarketId[row] != marketSlot || !isPosting(state, row)) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            if (contractState == ContractState.ACTIVE
                    || contractState == ContractState.IN_PROGRESS) {
                return row;
            }
        }
        return -1;
    }
}
