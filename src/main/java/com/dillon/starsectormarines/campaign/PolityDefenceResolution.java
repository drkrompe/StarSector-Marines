package com.dillon.starsectormarines.campaign;

/**
 * Settles one polity defence: the company met a vanilla raid on the ground at one of the
 * player's own colonies, and this writes down how it went.
 *
 * <p>Pure over {@link CampaignState}, so the rule can be tested without a sector. It owns
 * only the record; sending the raid home is the caller's, because that is the one write
 * to vanilla and it belongs where the raid can still be found.
 *
 * <p>Exactly once per (raid, market). A settled pair is reported
 * {@link Result#ALREADY_SETTLED} and nothing is written, which is what makes a replayed
 * resolution — a Results screen reopened, an outcome applied twice — harmless.
 */
public final class PolityDefenceResolution {

    public enum Result {
        /** The ground was held; the caller should now send the raid home. */
        RESOLVED_WON,
        /** The landing stood; vanilla's own raid resolution runs untouched. */
        RESOLVED_LOST,
        /** This raid was already fought at this market. Nothing was written. */
        ALREADY_SETTLED,
        /** The arguments cannot name a defence. Nothing was written. */
        REFUSED
    }

    private PolityDefenceResolution() {}

    /**
     * @param key     the mission's own key, which carries the market slot and the raid
     * @param victory whether the company held the ground
     * @param day     campaign day the defence settled
     */
    public static Result apply(CampaignState state, PolityDefenceMissionKey key,
                               boolean victory, int day) {
        if (state == null || key == null) return Result.REFUSED;
        if (state.polityDefenceRow(key.eventKey, key.marketSlot) >= 0) {
            return Result.ALREADY_SETTLED;
        }
        int row = state.recordPolityDefence(key.eventKey, key.marketSlot, victory, day);
        if (row < 0) return Result.REFUSED;
        return victory ? Result.RESOLVED_WON : Result.RESOLVED_LOST;
    }
}
