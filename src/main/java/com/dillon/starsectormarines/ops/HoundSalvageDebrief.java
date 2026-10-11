package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;

import java.util.List;
import java.util.Objects;

/** Correspondence about an already-settled award; playback cannot grant or change it. */
final class HoundSalvageDebrief {
    private HoundSalvageDebrief() { }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (!WaylineHoundAward.eligible(outcome)) return List.of();
        return List.of("request", "terms", "accept", outcome.waylineHound.disabled() ? "repaired" : "transfer")
                .stream().map(beat -> catalog.find(outcome.missionId, "debrief.hound." + beat))
                .filter(Objects::nonNull).toList();
    }
}
