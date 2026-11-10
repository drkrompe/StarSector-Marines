package com.dillon.starsectormarines.battle.world.gen;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What the world's weather and air do to a settlement, distilled from the planet's market conditions so
 * {@code battle.world.gen} never sees a game API: the climate counterpart to {@link SurfacePalette}, which says what
 * the ground is made of. {@link ClimateZoning} owns what each vanilla condition means.
 *
 * <p>A settlement adapts to what it lives under ({@code mapgen-nouns.md}, Climate). {@link Hazard#STORMS} banks the
 * world's ground against the windward face of every wall ({@link StormDrift}, wind from {@link StormWind});
 * {@link Hazard#RADIATION}, {@link Hazard#TOXIC} and {@link Hazard#AIRLESS} decide how its town is built
 * ({@link HabitatZoning}). {@link Hazard#QUAKES} is carried so the next adaptation reads the same fact rather than a
 * second distillation.
 *
 * <p>{@code wind} is the side the prevailing wind blows from, one fact for every adaptation that faces it (a drift on
 * a wall's windward face, a storm bank on the town's windward edge). It is the planet's, drawn once from the place
 * ({@link ClimateZoning}), so every battle on a world faces the same way. Never null.
 *
 * <p>{@link #NONE} is the climate of every battle with no campaign market behind it (fixtures, story maps, previews),
 * so a map that never declared weather is the map it was.
 */
public record Climate(Set<Hazard> hazards, StormWind.Side wind) {

    /** One thing the world does to the people living on it. */
    public enum Hazard {
        /** Wind-borne weather the town shelters from: dust storms, blizzards, extreme weather. Needs an atmosphere. */
        STORMS,
        /** No air: nothing blows, and nothing is lived in unsealed. */
        AIRLESS,
        /** Air that kills. */
        TOXIC,
        /** Hard radiation at the surface. */
        RADIATION,
        /** A ground that moves. */
        QUAKES
    }

    /** No weather the generator answers to. */
    public static final Climate NONE = new Climate(EnumSet.noneOf(Hazard.class));

    /** A climate whose wind blows from the west. */
    public Climate(Set<Hazard> hazards) {
        this(hazards, StormWind.Side.WEST);
    }

    public Climate {
        if (wind == null) wind = StormWind.Side.WEST;
        hazards = hazards == null || hazards.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(Hazard.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(hazards));
    }

    /** Whether the world does this. */
    public boolean has(Hazard hazard) {
        return hazards.contains(hazard);
    }
}
