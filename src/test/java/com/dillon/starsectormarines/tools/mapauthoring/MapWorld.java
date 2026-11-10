package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.EnumSet;
import java.util.Locale;

/**
 * One world the Maps page draws a map as: a vanilla planet type and what its air does, the same ten the owned-map look
 * suite shows. The map is built for it through the campaign's own reading of a world ({@link BiomeSkin}), so the page
 * draws what that world would get: its ground, its drop style, its rise and how each settlement adapts.
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

    /** The drop style this world gives a drop of {@code role}, in the editor's words. */
    public String dropStyle(OwnedMap.DropRole role) {
        NaturalVoidZoning.Drop drop = skin().drop(role);
        return drop.style().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + (drop == NaturalVoidZoning.LAVA_RIVER ? " river" : "");
    }

    /** An adaptation in the editor's words. */
    public static String words(BiomeSkin.Adaptation a) {
        return switch (a) {
            case OPEN -> "open";
            case INSULATED -> "insulated";
            case SEALED_PERIMETER -> "sealed perimeter";
            case CREVASSE_TERRACE -> "crevasse town";
        };
    }
}
