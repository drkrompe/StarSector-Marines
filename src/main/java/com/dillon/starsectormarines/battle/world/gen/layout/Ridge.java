package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner;

import java.util.List;

/**
 * High ground the works stand on: a ridge raised in the works phase before any
 * line, block or site is laid, so a line resolved along it is dug on its crest
 * ({@code mission-layout.md}, Ridge). It is raised as the landscape's own
 * ridges are ({@link RidgePlanner}): broken into a pass wherever a road, a
 * place, a structure or other reserved ground crosses it, each unbroken run a
 * hill segment climbed by one ramp from the side the band names. A segment that
 * would not fit or would cut the ground apart is left out.
 *
 * <p>{@code terraces} are built places the ridge carries on its crest rather
 * than breaking round ({@link TerracePlanner}): each stands on a flat top with a
 * ramp wherever a road crosses its cliff, and the ridge runs on into it. Each is
 * reported under its own name.
 */
public record Ridge(String name, RidgePlanner.Band band, boolean required, List<TerracePlanner.Terrace> terraces)
        implements Piece {

    public Ridge {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a ridge needs a name");
        if (band == null) throw new IllegalArgumentException("a ridge needs a band: " + name);
        terraces = terraces == null ? List.of() : List.copyOf(terraces);
    }

    /** A ridge that carries nothing on its crest: every place it meets is a pass. */
    public Ridge(String name, RidgePlanner.Band band, boolean required) {
        this(name, band, required, List.of());
    }
}
