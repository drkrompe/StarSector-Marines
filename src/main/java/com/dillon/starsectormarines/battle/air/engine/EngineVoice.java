package com.dillon.starsectormarines.battle.air.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Flyweight descriptor for a vehicle's engine-loop audio — which vanilla
 * {@code sounds/sfx_engines/} clip a craft's engines play, derived from the
 * hull's engine {@code style} (tech tier) and {@code hullSize}.
 *
 * <h2>Why a flyweight</h2>
 * <p>The base game ships exactly one engine loop per {@code (tier, size)} pair
 * — five tiers (lotek / midtek / hitek / omega / dweller) × five sizes
 * (fighter … capital). The descriptor is immutable and there are at most ~24
 * of them, so every craft of the same tier+size shares one interned instance
 * ({@link #forSpec}). This mirrors {@link EngineSlotData}: per-hull-immutable
 * engine data resolved from a cache, never copied per entity. Per-entity state
 * (position, velocity, pitch jitter, intensity) stays on the craft; the shared
 * "what does this engine sound like" lives here.
 *
 * <h2>Where the clips come from</h2>
 * <p>The combat engine loads these clips by filename internally, so they are
 * <em>not</em> registered as named sound ids by the core install.
 * {@link #loopSoundId} names an id we register in
 * {@code mod/data/config/sounds.json} that points straight at the core file —
 * the same resolve-against-core trick the flyby weapon SFX use, no
 * redistribution. Every clip is mono, so it drives a positional
 * {@code playLoop} (not the stereo {@code playUILoop}).
 */
public final class EngineVoice {

    /** Vanilla engine-sound tech tiers — the {@code engine_NN_<tier>} filename prefixes under {@code sounds/sfx_engines/}. */
    public enum Tier {
        LOTEK("lotek"), MIDTEK("midtek"), HITEK("hitek"), OMEGA("omega"), DWELLER("dweller");

        public final String tag;
        Tier(String tag) { this.tag = tag; }

        /**
         * Maps a {@code .ship} engine-slot {@code style} to its sound tier. The
         * five core tiers map 1:1 from their engine-style names; faction / boss
         * styles with no dedicated engine clip (THREAT, ONSLAUGHT_MKI,
         * COBRA_BOMBER, ATTACK_SWARM, …) fall back to the nearest tier by feel.
         * Unknown / null styles default to {@link #MIDTEK} — the neutral middle.
         */
        public static Tier fromStyle(String style) {
            if (style == null) return MIDTEK;
            switch (style) {
                case "LOW_TECH":
                case "ONSLAUGHT_MKI":  return LOTEK;
                case "HIGH_TECH":
                case "THREAT":
                case "ATTACK_SWARM":   return HITEK;
                case "OMEGA":          return OMEGA;
                case "DWELLER":        return DWELLER;
                case "MIDLINE":
                case "COBRA_BOMBER":
                default:               return MIDTEK;
            }
        }
    }

    /** Hull size → the {@code _NN_<size>} filename suffix under {@code sounds/sfx_engines/}. */
    public enum Size {
        FIGHTER("fighter"), FRIGATE("frigate"), DESTROYER("destroyer"),
        CRUISER("cruiser"), CAPITAL("capital");

        public final String tag;
        Size(String tag) { this.tag = tag; }

        /** Maps a {@code .ship} {@code hullSize} value; unknown / null falls back to {@link #FRIGATE} (the civilian-transport baseline). */
        public static Size fromHullSize(String hullSize) {
            if (hullSize == null) return FRIGATE;
            switch (hullSize) {
                case "FIGHTER":      return FIGHTER;
                case "DESTROYER":    return DESTROYER;
                case "CRUISER":      return CRUISER;
                case "CAPITAL_SHIP": return CAPITAL;
                case "FRIGATE":
                default:             return FRIGATE;
            }
        }
    }

    /** Interned flyweights, keyed by {@code "<tier>_<size>"}. */
    private static final Map<String, EngineVoice> POOL = new HashMap<>();

    /** Fallback voice for hulls that can't be resolved — a neutral midline frigate engine. */
    public static final EngineVoice DEFAULT = forSpec("MIDLINE", "FRIGATE");

    public final Tier tier;
    public final Size size;
    /** Registered sound id (the ENGINE block in {@code sounds.json}) backing the vanilla clip. */
    public final String loopSoundId;

    private EngineVoice(Tier tier, Size size) {
        this.tier = tier;
        this.size = size;
        this.loopSoundId = "marines_engine_" + tier.tag + "_" + size.tag;
    }

    /**
     * The interned voice for a hull's engine {@code style} + {@code hullSize}.
     * Same args always return the same instance (flyweight identity). The one
     * missing core clip — dweller ships no fighter-size engine — clamps to
     * dweller frigate so the id always names a file that exists.
     */
    public static EngineVoice forSpec(String engineStyle, String hullSize) {
        Tier tier = Tier.fromStyle(engineStyle);
        Size size = Size.fromHullSize(hullSize);
        if (tier == Tier.DWELLER && size == Size.FIGHTER) size = Size.FRIGATE;
        String key = tier.tag + "_" + size.tag;
        EngineVoice cached = POOL.get(key);
        if (cached != null) return cached;
        EngineVoice v = new EngineVoice(tier, size);
        POOL.put(key, v);
        return v;
    }

    @Override public String toString() { return "EngineVoice[" + loopSoundId + "]"; }
}
