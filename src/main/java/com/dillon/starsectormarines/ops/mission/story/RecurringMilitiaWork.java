package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.WaylineArc;

/** Ordinary postings unlocked by Wayline; never reopen its completed chapters. */
public final class RecurringMilitiaWork implements StoryMissionDef {
    private final OpeningOperationKind kind;

    public RecurringMilitiaWork(OpeningOperationKind kind) {
        if (kind == null) throw new IllegalArgumentException("opening operation kind is required");
        this.kind = kind;
    }

    @Override public String id() { return kind.recurringId; }

    @Override public boolean isEligible(StoryEligibilityContext ctx) {
        return OpeningOperationStory.eligibleBroker(ctx) && WaylineArc.stage(ctx.roster) == WaylineArc.Stage.COMPLETE;
    }

    @Override public Mission build(StoryEligibilityContext ctx) {
        return OpeningOperationStory.buildOperation(ctx, kind, true);
    }
}
