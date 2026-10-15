package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.Direction;

/**
 * The piece of the environment a round stopped in: a cell — a crossed prop, a
 * prop or wall lending edge cover, a structural wall — or, when {@code edge} is
 * non-null, the shared edge of that cell holding a feature such as a window.
 *
 * <p>Recorded by {@link BallisticResolver} at fire time and worn at arrival by
 * {@link EnvironmentWear}. It names a place, not a prop: whatever stands there
 * when the round arrives is what it strikes.
 */
public record StruckFeature(int cellX, int cellY, Direction edge) {

    /** A whole cell. */
    public static StruckFeature cell(int cellX, int cellY) {
        return new StruckFeature(cellX, cellY, null);
    }

    /** The feature on one cardinal edge of a cell. */
    public static StruckFeature edge(int cellX, int cellY, Direction edge) {
        return new StruckFeature(cellX, cellY, edge);
    }
}
