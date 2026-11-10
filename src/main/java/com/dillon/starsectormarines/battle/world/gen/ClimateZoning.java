package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.Climate.Hazard;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The selection-layer policy that turns a planet's vanilla market condition ids into its {@link Climate}: the
 * climate counterpart to {@link SurfaceZoning}, and the only place condition ids are named on the battle side of the
 * bridge. Takes plain strings so it stays testable without a sector.
 *
 * <p>Vanilla has no dust-storm condition, so storms are read from the ground as well as the sky: a desert with any
 * air at all is a dust world, and a frozen world in extreme cold is a blizzard world. Extreme weather is a storm
 * world wherever it is. A world with no atmosphere has no weather, whatever else it says.
 */
public final class ClimateZoning {

    private ClimateZoning() { }

    /**
     * The climate a world reads as.
     *
     * @param conditionIds vanilla market condition ids ({@code extreme_weather}, {@code no_atmosphere}, ...);
     *                     null reads as none
     * @param surface      what the world's ground is made of, from {@link SurfaceZoning}
     */
    public static Climate forConditions(Collection<String> conditionIds, SurfacePalette surface) {
        Set<String> ids = conditionIds == null ? Set.of() : Set.copyOf(conditionIds);
        EnumSet<Hazard> hazards = EnumSet.noneOf(Hazard.class);
        boolean airless = ids.contains("no_atmosphere");
        if (airless) hazards.add(Hazard.AIRLESS);
        if (ids.contains("toxic_atmosphere")) hazards.add(Hazard.TOXIC);
        if (ids.contains("irradiated")) hazards.add(Hazard.RADIATION);
        if (ids.contains("tectonic_activity") || ids.contains("extreme_tectonic_activity")) hazards.add(Hazard.QUAKES);
        boolean storms = ids.contains("extreme_weather")
                || surface == SurfacePalette.ARID
                || surface == SurfacePalette.FROZEN && ids.contains("very_cold");
        if (storms && !airless) hazards.add(Hazard.STORMS);
        return new Climate(hazards);
    }
}
