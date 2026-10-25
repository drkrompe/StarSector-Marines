package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

/**
 * What another attempt at a story operation costs from inside its battle.
 *
 * <p>One retry is free per visit to mission select; every further retry spends
 * one Starsector story point. A retry erases the attempt rather than settling
 * it, so the allowance is the only thing it changes in the campaign.
 */
final class StoryRetryAllowance {

    enum Cost { FREE, STORY_POINT, UNAVAILABLE }

    /** The player's story-point purse; a seam so the rule is testable without a sector. */
    interface StoryPoints {
        int available();

        void spend(int points, String logText);

        StoryPoints LIVE = new StoryPoints() {
            @Override public int available() {
                MutableCharacterStatsAPI stats = stats();
                return stats == null ? 0 : stats.getStoryPoints();
            }

            @Override public void spend(int points, String logText) {
                MutableCharacterStatsAPI stats = stats();
                // Undoing an outcome earns no bonus experience, as with vanilla's own undo-style uses.
                if (stats != null) stats.spendStoryPoints(points, true, null, true, 0f, logText);
            }

            private MutableCharacterStatsAPI stats() {
                return Global.getSector() == null ? null : Global.getSector().getPlayerStats();
            }
        };
    }

    private final StoryPoints points;
    private boolean freeRetryUsed;

    StoryRetryAllowance(StoryPoints points) {
        this.points = points;
    }

    Cost cost() {
        if (!freeRetryUsed) return Cost.FREE;
        return points.available() > 0 ? Cost.STORY_POINT : Cost.UNAVAILABLE;
    }

    /** Pays for one retry; false when nothing could pay and the retry must not happen. */
    boolean charge(String logText) {
        switch (cost()) {
            case FREE -> freeRetryUsed = true;
            case STORY_POINT -> points.spend(1, logText);
            default -> { return false; }
        }
        return true;
    }

    /** Called on reaching mission select: the free retry returns. */
    void reset() {
        freeRetryUsed = false;
    }
}
