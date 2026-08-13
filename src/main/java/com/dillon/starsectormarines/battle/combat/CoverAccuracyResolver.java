package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

/**
 * Converts the battle's existing directional wall + doodad cover into a
 * direct-fire accuracy modifier.
 *
 * <p>The target reads only the facing toward the shooter. Wall and doodad
 * levels add, then cap at heavy cover so overlapping props cannot drive hit
 * chance toward zero. Tuning is deliberately multiplicative: light cover
 * retains 85% accuracy, medium 70%, and heavy 55%. This keeps weapon identity
 * and stance/range penalties intact while making a correctly faced fighting
 * position materially safer.
 *
 * <p>Indirect and aerial-fire eligibility is decided by the weapon system;
 * this resolver models cardinal ground-level protection only.
 */
public final class CoverAccuracyResolver {

    public static final float LIGHT_ACCURACY_MULT = 0.85f;
    public static final float MEDIUM_ACCURACY_MULT = 0.70f;
    public static final float HEAVY_ACCURACY_MULT = 0.55f;

    private final NavigationGrid grid;
    private final DoodadService doodads;

    public CoverAccuracyResolver(NavigationGrid grid, DoodadService doodads) {
        this.grid = grid;
        this.doodads = doodads;
    }

    /**
     * Applies cover at {@code target} facing the threat at {@code shooter}.
     * Positions are cells, not render coordinates.
     */
    public float apply(float baseAccuracy,
                       int targetX, int targetY,
                       int shooterX, int shooterY) {
        int fromDx = shooterX - targetX;
        int fromDy = shooterY - targetY;
        if (fromDx == 0 && fromDy == 0) return baseAccuracy;

        int level = grid.getCoverAt(targetX, targetY, fromDx, fromDy)
                + doodads.getDoodadCoverAt(targetX, targetY, fromDx, fromDy);
        return baseAccuracy * accuracyMultiplier(level);
    }

    /** Pure tuning surface used by tests and future combat UI previews. */
    public static float accuracyMultiplier(int combinedCoverLevel) {
        if (combinedCoverLevel <= 0) return 1f;
        if (combinedCoverLevel == 1) return LIGHT_ACCURACY_MULT;
        if (combinedCoverLevel == 2) return MEDIUM_ACCURACY_MULT;
        return HEAVY_ACCURACY_MULT;
    }
}
