package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;

/**
 * How a warship's round looks in flight, read from its projectile spec: which kind of thing the game draws, how long
 * and wide it is, its colours, the glow under it, and for a missile its nozzle, flame and contrail.
 *
 * <p>The game draws three different things and the spec says which. A {@code BALLISTIC} round with a
 * {@code bulletSprite} is that sprite stretched to the spec's {@code length} along its flight and {@code width} across
 * it (a Heavy Autocannon's ten-by-twelve-pixel green shell is drawn forty long and six and a half wide). A
 * {@code BALLISTIC_AS_BEAM} round is a <em>streak</em>: a tapered bolt of light {@code length} by {@code width} in
 * its {@code fringeColor} with a {@code coreColor} centre, fading at both ends, and it is a streak even when it also
 * names a {@code bulletSprite} (the Antimatter Blaster names one and the game never draws it). A missile is its
 * {@code sprite} at its {@code size}, held at its {@code center}, with an engine flame at its first engine slot and
 * a contrail laid behind it. A contrail's smoke keeps a share of the missile's speed as it is let go
 * ({@code contrailMaxSpeedMult}, its {@link #contrailDrift}), so a Harpoon's trail is half as long as a torpedo's for
 * the same duration.
 *
 * <p>Sizes are the game's pixels; {@link #cellsPerPx} turns them into cells. A round fired between hulls is drawn at
 * the hulls' own scale ({@link WarshipScale#HULL_PRESENCE}), so it stands to the ship it hits as it does in the base
 * game; a round fired at the ground or the air is drawn at the shared density, slimmed by {@link #GROUND_ROUND_SCALE},
 * because there it is measured against marines.
 */
public record VanillaRoundLook(
        Kind kind,
        String sprite,
        float lengthPx,
        float widthPx,
        float spriteAheadPx,
        Color fringe,
        Color core,
        Color glowColor,
        float glowRadiusPx,
        float nozzleBehindPx,
        float flameLengthPx,
        float flameWidthPx,
        Color engineColor,
        float engineGlowMult,
        Color contrailColor,
        float contrailSeconds,
        float contrailWidthPx,
        float contrailGrowthAtEnd,
        float contrailDrift,
        boolean contrailGlow) {

    /** What the game draws for a round. */
    public enum Kind {
        /** A {@code BALLISTIC} round: its bullet sprite stretched to the spec's length and width. */
        SHELL,
        /** A {@code BALLISTIC_AS_BEAM} round, or a projectile with no sprite: a tapered streak of light. */
        STREAK,
        /** A missile: its sprite at its size, an engine flame and a contrail. */
        MISSILE
    }

    /**
     * How much slimmer than the shared density a round fired at the ground or the air is drawn: at the shared density
     * the game's own proportions read as boulders beside marines.
     */
    public static final float GROUND_ROUND_SCALE = 0.6f;

    /** A streak the spec gives no size: the game's default bolt. */
    private static final float DEFAULT_STREAK_LENGTH = 30f, DEFAULT_STREAK_WIDTH = 6f;

    /**
     * Cells per game pixel at which a round is drawn: the hulls' own scale ({@link WarshipScale#hullCells}) when it
     * flies between hulls, else the shared density slimmed by {@link #GROUND_ROUND_SCALE}.
     */
    public static float cellsPerPx(boolean betweenHulls) {
        return betweenHulls ? WarshipScale.hullCells(1f) : WarshipScale.cells(1f) * GROUND_ROUND_SCALE;
    }

    /** The round's drawn length along its flight, in cells. */
    public float lengthCells(boolean betweenHulls) {
        return lengthPx * cellsPerPx(betweenHulls);
    }

    /** The round's drawn width across its flight, in cells. */
    public float widthCells(boolean betweenHulls) {
        return widthPx * cellsPerPx(betweenHulls);
    }

    /** The radius of the glow the spec puts under the round, in cells; zero when it names none. */
    public float glowRadiusCells(boolean betweenHulls) {
        return glowRadiusPx * cellsPerPx(betweenHulls);
    }

    /**
     * How long a missile's contrail hangs, in the battle's seconds. Between hulls everything runs on the hull fight's
     * clock ({@link WarshipScale#HULL_TEMPO}), the trail with it, so it lies behind the missile over the same share
     * of its flight as in the base game; at the ground it is the spec's.
     */
    public float contrailLifeSeconds(boolean betweenHulls) {
        return contrailSeconds * (betweenHulls ? WarshipScale.HULL_TEMPO : 1f);
    }

    /**
     * Reads a projectile spec. {@code null} for a missing spec; a spec the game would draw as a streak is a streak
     * whatever sprite it also names.
     */
    public static VanillaRoundLook fromSpec(JSONObject proj) {
        if (proj == null) return null;
        Color fringe = VanillaWeapon.color(proj, "fringeColor");
        Color core = VanillaWeapon.color(proj, "coreColor");
        Color glow = VanillaWeapon.color(proj, "glowColor");
        float glowRadius = (float) proj.optDouble("glowRadius", 0.0);
        if ("missile".equals(proj.optString("specClass"))) return missile(proj, fringe, core, glow, glowRadius);
        float length = (float) proj.optDouble("length", 0.0);
        float width = (float) proj.optDouble("width", 0.0);
        String bullet = nonBlank(proj.optString("bulletSprite", null));
        boolean streak = isStreak(proj) || bullet == null;
        if (streak) {
            length = length > 0f ? length : DEFAULT_STREAK_LENGTH;
            width = width > 0f ? width : DEFAULT_STREAK_WIDTH;
        }
        return new VanillaRoundLook(streak ? Kind.STREAK : Kind.SHELL, streak ? null : bullet, length, width, 0f,
                fringe, core, glow, glowRadius, 0f, 0f, 0f, null, 0f, null, 0f, 0f, 0f, 0f, false);
    }

    /** Whether the game draws this projectile as a streak of light rather than as its bullet sprite. */
    static boolean isStreak(JSONObject proj) {
        return proj != null && "BALLISTIC_AS_BEAM".equals(proj.optString("spawnType"));
    }

    private static VanillaRoundLook missile(JSONObject proj, Color fringe, Color core, Color glow, float glowRadius) {
        float[] size = pair(proj.optJSONArray("size"));
        float[] centre = pair(proj.optJSONArray("center"));
        float width = size != null ? size[0] : 6f;
        float length = size != null ? size[1] : 16f;
        // The round's position is the missile's own centre; the sprite's middle sits this far ahead of it.
        float ahead = size != null && centre != null ? length * 0.5f - centre[1] : 0f;
        JSONArray slots = proj.optJSONArray("engineSlots");
        JSONObject slot = slots != null && slots.length() > 0 ? slots.optJSONObject(0) : null;
        JSONObject style = slot == null ? null : slot.optJSONObject("styleSpec");
        float[] loc = slot == null ? null : pair(slot.optJSONArray("loc"));
        float slotWidth = slot == null ? 4f : (float) slot.optDouble("width", 4.0);
        float contrailMult = number(style, "contrailWidthMult", 1f);
        return new VanillaRoundLook(Kind.MISSILE, nonBlank(proj.optString("sprite", null)), length, width, ahead,
                fringe, core, glow, glowRadius,
                loc != null ? Math.max(0f, -loc[0]) : length * 0.5f,
                slot == null ? 20f : (float) slot.optDouble("length", 20.0),
                slotWidth,
                style == null ? null : VanillaWeapon.color(style, "engineColor"),
                number(style, "glowSizeMult", 1f),
                style == null ? null : VanillaWeapon.color(style, "contrailColor"),
                number(style, "contrailDuration", 0f),
                slotWidth * contrailMult,
                number(style, "contrailWidthAddedFractionAtEnd", 0f),
                Math.max(0f, Math.min(1f, number(style, "contrailMaxSpeedMult", 0f))),
                style != null && "GLOW".equals(style.optString("type")));
    }

    /**
     * A number from a spec, which the game's files sometimes write as a Java float literal ({@code 0.5f}) that reads
     * back as text; {@code fallback} when absent or unreadable.
     */
    static float number(JSONObject o, String key, float fallback) {
        Object v = o == null ? null : o.opt(key);
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String text) {
            String t = text.trim();
            if (t.endsWith("f") || t.endsWith("F")) t = t.substring(0, t.length() - 1);
            try {
                return Float.parseFloat(t);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static float[] pair(JSONArray a) {
        if (a == null || a.length() < 2) return null;
        return new float[]{(float) a.optDouble(0, 0.0), (float) a.optDouble(1, 0.0)};
    }

    private static String nonBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
