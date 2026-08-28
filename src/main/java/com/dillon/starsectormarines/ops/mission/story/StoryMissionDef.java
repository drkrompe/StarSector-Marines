package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;

/**
 * Hand-authored mission with predicate-based eligibility. The registry walks all
 * defs at generation time; for each one whose {@link #isEligible} returns true,
 * {@link #build} produces the actual {@link Mission} prepended to the planet's
 * generated list.
 *
 * <p>Completion is tracked by {@link #id} on the player's roster — see
 * {@code MarineRoster.completedStoryIds}. A one-shot def checks
 * {@code !ctx.roster.hasCompletedStory(id())} as its first clause; a recurring
 * one deliberately does not, and may still read the set to order itself behind
 * another def. Recurring defs should gate on something other than roster
 * quality, or they become work the player loses by growing.
 */
public interface StoryMissionDef {

    /** Stable id used as the completion key — keep unique across all defs. */
    String id();

    boolean isEligible(StoryEligibilityContext ctx);

    Mission build(StoryEligibilityContext ctx);
}
