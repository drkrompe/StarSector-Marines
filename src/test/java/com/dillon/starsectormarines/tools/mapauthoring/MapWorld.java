package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.EnumSet;

/**
 * One world the Maps page dresses a story map as: a vanilla planet type and what its air does, read through the
 * campaign's own reading of a world ({@link BiomeSkin}): its ground palette, signature and habitat.
 */
public enum MapWorld {
    ROCK("Rock", "barren", Climate.Hazard.AIRLESS),
    ARID("Arid", "desert"),
    VERDANT("Verdant", "terran"),
    JUNGLE("Jungle", "jungle"),
    TUNDRA("Tundra", "tundra"),
    OCEAN("Ocean", "water"),
    FROZEN("Frozen", "frozen"),
    VOLCANIC("Volcanic", "lava"),
    IRRADIATED("Irradiated", "irradiated", Climate.Hazard.RADIATION),
    TOXIC("Toxic", "toxic", Climate.Hazard.TOXIC);

    private final String label, planetType;
    private final Climate climate;

    MapWorld(String label, String planetType, Climate.Hazard... hazards) {
        this.label = label;
        this.planetType = planetType;
        EnumSet<Climate.Hazard> set = EnumSet.noneOf(Climate.Hazard.class);
        for (Climate.Hazard h : hazards) set.add(h);
        this.climate = hazards.length == 0 ? Climate.NONE : new Climate(set);
    }

    public String label() { return label; }
    public String planetType() { return planetType; }

    /** The target the map is built for on this world. */
    public TargetProfile profile() {
        return new TargetProfile(4, 5, 1, 1, "independent", EnumSet.of(EconomicFunction.HABITATION),
                SurfaceZoning.forPlanetType(planetType), SettlementLink.ROAD)
                .withClimate(climate)
                .withSignature(SurfaceZoning.signatureFor(planetType))
                .withLivingWorld(SurfaceZoning.livingWorld(planetType));
    }

    public BiomeSkin skin() { return BiomeSkin.of(profile()); }
}
