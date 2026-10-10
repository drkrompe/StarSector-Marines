package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;

/**
 * Hand-authored mission with predicate-based eligibility. The registry walks all
 * defs at generation time; for each one whose {@link #isEligible} returns true,
 * {@link #build} produces the actual {@link Mission} prepended to the planet's
 * generated list.
 *
 * <p>STORY victories record this definition's id in the roster's completed set.
 * RECURRING jobs have distinct ids and never write chapter completion. They may
 * read story victories to unlock; roster growth never removes recurring work.
 */
public interface StoryMissionDef {

    /** Stable offer id (also the completion key for STORY) — keep unique across all defs. */
    String id();

    boolean isEligible(StoryEligibilityContext ctx);

    Mission build(StoryEligibilityContext ctx);
}
