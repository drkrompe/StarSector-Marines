package com.dillon.starsectormarines.ops.intel;

import com.dillon.starsectormarines.ops.MissionType;

/**
 * Pre-baked mission template — what kind of op, what it's called, what the briefing
 * paragraph says. Industry-specific so a Sabotage on Refining reads as
 * "Disrupt the Refinery" with refinery-flavored prose, not generic "Sabotage Listening Post."
 *
 * <p>The catalog maps an industry id to a small list of these; one is picked at generation
 * time. Pairs with {@link PlanetIntel#getIndustry(String)} to fill in the
 * {@code targetIndustryId} on the resulting {@code Mission}.
 */
public final class MissionArchetype {

    public final MissionType type;
    public final String      name;
    public final String      flavor;

    public MissionArchetype(MissionType type, String name, String flavor) {
        this.type   = type;
        this.name   = name;
        this.flavor = flavor;
    }
}
