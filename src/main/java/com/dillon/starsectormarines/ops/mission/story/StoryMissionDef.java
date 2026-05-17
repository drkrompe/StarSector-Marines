package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;

/**
 * Hand-authored mission with predicate-based eligibility. The registry walks all
 * defs at generation time; for each one whose {@link #isEligible} returns true,
 * {@link #build} produces the actual {@link Mission} prepended to the planet's
 * generated list.
 *
 * <p>Completion is tracked by {@link #id} on the player's roster — see
 * {@code MarineRoster.completedStoryIds}. {@code isEligible} should typically check
 * {@code !ctx.roster.hasCompletedStory(id())} as its first clause for one-shot defs.
 */
public interface StoryMissionDef {

    /** Stable id used as the completion key — keep unique across all defs. */
    String id();

    boolean isEligible(StoryEligibilityContext ctx);

    Mission build(StoryEligibilityContext ctx);
}
