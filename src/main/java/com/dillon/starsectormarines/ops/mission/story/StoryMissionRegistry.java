package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Static catalog of hand-authored story missions. Walks every registered def at
 * generation time; eligible defs produce a {@link Mission} that the generator
 * prepends to the planet's regular list.
 *
 * <p>No registration ceremony — defs are inline {@code DEFS} entries. Convert to a
 * service-loader / external-registration pattern when we have 10+ defs and editing
 * this file becomes a merge hotspot.
 */
public final class StoryMissionRegistry {

    private static final List<StoryMissionDef> DEFS = Arrays.<StoryMissionDef>asList(
            new VeteransJobStory()
    );

    private StoryMissionRegistry() {}

    public static List<Mission> eligibleFor(StoryEligibilityContext ctx) {
        if (ctx == null) return Collections.emptyList();
        List<Mission> out = new ArrayList<>();
        for (StoryMissionDef def : DEFS) {
            try {
                if (def.isEligible(ctx)) out.add(def.build(ctx));
            } catch (Exception e) {
                // A broken def shouldn't take the whole list down — generator gets
                // the survivors and the player just doesn't see the broken one.
                com.fs.starfarer.api.Global.getLogger(StoryMissionRegistry.class)
                        .error("Story def '" + def.id() + "' threw during eligibility/build", e);
            }
        }
        return out;
    }
}
