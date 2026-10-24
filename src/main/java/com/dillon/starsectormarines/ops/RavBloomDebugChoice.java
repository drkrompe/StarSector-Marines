package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import java.util.List;
import java.util.Random;

/**
 * What the debug launcher lets the player choose for a Rav Bloom mission: how built-up the country is, its biome and
 * whether the seed is a vetted one from the map's story-map pool or a fresh roll. One choice serves all four missions.
 */
final class RavBloomDebugChoice {
    static final List<SurfacePalette> BIOMES =
            List.of(SurfacePalette.VERDANT, SurfacePalette.ARID, SurfacePalette.ROCK, SurfacePalette.FROZEN);
    private static final String[] DENSITIES = {"Rural", "Town", "Urban"};

    private static int density, biome;
    private static boolean random;
    private static long rolled = new Random().nextLong() & 0xFFFFL;

    private RavBloomDebugChoice() { }

    static void cycleDensity() { density = (density + 1) % DENSITIES.length; }
    static void cycleBiome() { biome = (biome + 1) % BIOMES.size(); }
    static void cycleSeed() {
        random = !random;
        if (random) rolled = new Random().nextLong() & 0xFFFFL;
    }

    static SurfacePalette biome() { return BIOMES.get(biome); }
    static CordonPlan.Density cordonDensity() { return CordonPlan.Density.values()[density]; }
    static HeartCountry.Density heartDensity() { return HeartCountry.Density.values()[density]; }

    /** The seed for a map: the first vetted one for this biome, or the roll. */
    static long seed(StoryMap map) {
        if (random) return rolled;
        if (!StoryMapPools.installed()) return 1L;
        List<StoryMapPool.Entry> entries = StoryMapPools.pool(map).entries(biome());
        return entries == null || entries.isEmpty() ? 1L : entries.get(0).seed();
    }

    static String label() {
        return DENSITIES[density] + " / " + biome() + " / " + (random ? "random seed " + rolled : "pooled seed");
    }
    static String densityLabel() { return "DENSITY: " + DENSITIES[density].toUpperCase(); }
    static String biomeLabel() { return "BIOME: " + biome(); }
    static String seedLabel() { return random ? "SEED: RANDOM " + rolled + " (USE POOLED)" : "SEED: POOLED (ROLL RANDOM)"; }
}
