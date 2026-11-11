package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.model.NightLighting;

import java.util.List;
import java.util.Set;

/**
 * Whether a town battle is fought at night, and the night it is fought in: about one in {@link #SHARE} town battles,
 * by the battle's seed, so the same battle always falls the same way. A story map takes part only if it says so
 * ({@link StoryMap#nightFalls}). The night is a modest one ({@link #DARKNESS}), lit by the town itself
 * ({@link StreetLights#light}), so the streets, and the people on them, still read.
 */
public final class TownNight {

    /** The share of town battles fought at night. UNTUNED. */
    static final float SHARE = .2f;
    /** How dark a town's night lies on its open ground. UNTUNED. */
    static final float DARKNESS = .7f;

    private TownNight() { }

    /**
     * Whether the town battle of {@code seed} is fought at night: never on a story map that keeps to the day, otherwise
     * about {@link #SHARE} of them by the seed.
     */
    public static boolean falls(long seed, StoryMap map) {
        if (map != null && !map.nightFalls()) return false;
        long h = (seed ^ 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        return ((h >>> 40) & 0xFFFF) / 65536f < SHARE;
    }

    /** A dark night with the town's street circuit powered and no lamps yet: the town lays its own. */
    public static NightLighting dark() {
        return new NightLighting(DARKNESS, List.of(), Set.of(StreetLights.CIRCUIT));
    }

    /** Lays the night on {@code map} and lights it with the town as built. */
    public static void fall(MapResult map) {
        map.topology.setNightLighting(StreetLights.lit(dark(), StreetLights.light(map)));
    }
}
