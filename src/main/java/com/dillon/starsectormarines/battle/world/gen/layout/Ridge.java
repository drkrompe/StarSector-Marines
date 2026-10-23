package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

/**
 * High ground the works stand on: a ridge raised in the works phase before any
 * line, block or site is laid, so a line resolved along it is dug on its crest
 * ({@code mission-layout.md}, Ridge). It is raised as the landscape's own
 * ridges are ({@link RidgePlanner}): broken into a pass wherever a road, a
 * place, a structure or other reserved ground crosses it, each unbroken run a
 * hill segment climbed by one ramp from the side the band names. A segment that
 * would not fit or would cut the ground apart is left out.
 */
public record Ridge(String name, RidgePlanner.Band band, boolean required) implements Piece {

    public Ridge {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a ridge needs a name");
        if (band == null) throw new IllegalArgumentException("a ridge needs a band: " + name);
    }
}
