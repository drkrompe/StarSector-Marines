package com.dillon.starsectormarines.battle.air.engine;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the {@code style} string on an {@link EngineSlotData} to a flame
 * color, mirroring vanilla's {@code data/config/engine_styles.json}.
 * Values transcribed from the vanilla source so a Buffalo (LOW_TECH) reads
 * orange, a Hermes (MIDLINE) reads warm amber, and a hypothetical HIGH_TECH
 * shuttle reads cyan-blue. Unknown styles fall back to {@link #DEFAULT_FLAME}
 * (MIDLINE amber) — graceful degradation for modded styles we don't know
 * about yet.
 *
 * <p>The glow color is the same hue as the flame today; the flame sprite is
 * a tighter column and the glow sprite is a soft halo, so per-style hue
 * difference matters less than the sprite shapes themselves. If a future
 * style wants a distinct glow tint (vanilla's {@code glowAlternateColor})
 * we'll extend the entry then.
 */
public final class EngineStylePalette {

    /** Vanilla MIDLINE engine color — warm amber, the default if a style is missing or unrecognized. */
    public static final Color DEFAULT_FLAME = new Color(255, 145, 75, 255);

    private static final Map<String, Color> FLAME_BY_STYLE = new HashMap<>();
    static {
        // Values pulled straight out of starsector-core/data/config/engine_styles.json
        // for accuracy. Alpha is left at 255 — per-frame alpha multiplication
        // (intensity, distance fade) happens at draw time.
        FLAME_BY_STYLE.put("MIDLINE",       new Color(255, 145,  75, 255));
        FLAME_BY_STYLE.put("LOW_TECH",      new Color(255, 125,  25, 255));
        FLAME_BY_STYLE.put("HIGH_TECH",     new Color(100, 165, 255, 255));
        FLAME_BY_STYLE.put("OMEGA",         new Color(150,  50, 255, 255));
        FLAME_BY_STYLE.put("COBRA_BOMBER",  new Color(255, 100, 100, 255));
        FLAME_BY_STYLE.put("ATTACK_SWARM",  new Color(130, 155, 145, 255));
        // Flare-style burns (used on some missiles) — included for completeness
        // since a future hull's slot could reference one.
        FLAME_BY_STYLE.put("midlineFlare",  new Color(255, 125,  75, 255));
    }

    private EngineStylePalette() {}

    /** Returns the flame color for the given vanilla style string, or {@link #DEFAULT_FLAME} when unknown. */
    public static Color flameColor(String style) {
        if (style == null) return DEFAULT_FLAME;
        Color c = FLAME_BY_STYLE.get(style);
        return c != null ? c : DEFAULT_FLAME;
    }
}
