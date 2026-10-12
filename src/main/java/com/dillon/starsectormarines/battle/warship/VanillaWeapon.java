package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;

import java.awt.Color;
import java.util.Map;

/**
 * A vanilla gun, launcher or cannon as the ground battle needs to know it: what
 * one round does, how far and how fast it goes, how it fires, and what it looks
 * like — all read from the game's weapon table, weapon spec and projectile spec.
 *
 * <p>Nothing here is authored by the mod. A ground-scale effect is derived from
 * these numbers by one conversion ({@link WarshipScale}) rather than being
 * retyped per weapon, so a re-balanced or modded weapon arrives with its own
 * figures.
 *
 * <p>{@code spreadDegrees} is the weapon's tightest cone, its {@code min spread}:
 * a turret holding its aim fires at that, and the wider figure is only what
 * sustained fire builds up. Reading the wide one scattered a Devastator's rounds
 * across a quarter of the map.
 *
 * <p>Distances and speeds are the game's own units (px, px/s); converting them
 * to cells is {@link WarshipScale}'s business.
 */
public record VanillaWeapon(
        String id,
        float damagePerShot,
        String damageType,
        float rangePx,
        int burstSize,
        float burstDelaySeconds,
        float cooldownSeconds,
        float projectileSpeedPx,
        float spreadDegrees,
        float turnRateDegPerSec,
        boolean beam,
        float damagePerSecond,
        float beamWidthPx,
        boolean missile,
        boolean pointDefenceOnly,
        float explosionRadiusPx,
        float hitGlowRadiusPx,
        Color explosionColor,
        Color fringe,
        Color core,
        String projectileSprite,
        float projectileLengthPx,
        float projectileWidthPx,
        Color muzzleColor,
        float muzzleLengthPx,
        String turretSprite,
        String turretGunSprite,
        String hardpointSprite,
        String hardpointGunSprite,
        String fireSoundOne,
        String fireSoundTwo,
        String impactSoundId,
        float fluxPerShot,
        float fluxPerSecond) {

    /** A weapon with no flux cost: every figure but the two that read the table's {@code energy} columns. */
    public VanillaWeapon(String id, float damagePerShot, String damageType, float rangePx, int burstSize,
                         float burstDelaySeconds, float cooldownSeconds, float projectileSpeedPx,
                         float spreadDegrees, float turnRateDegPerSec, boolean beam, float damagePerSecond,
                         float beamWidthPx, boolean missile, boolean pointDefenceOnly, float explosionRadiusPx,
                         float hitGlowRadiusPx, Color explosionColor, Color fringe, Color core,
                         String projectileSprite, float projectileLengthPx, float projectileWidthPx,
                         Color muzzleColor, float muzzleLengthPx, String turretSprite, String turretGunSprite,
                         String hardpointSprite, String hardpointGunSprite, String fireSoundOne,
                         String fireSoundTwo, String impactSoundId) {
        this(id, damagePerShot, damageType, rangePx, burstSize, burstDelaySeconds, cooldownSeconds,
                projectileSpeedPx, spreadDegrees, turnRateDegPerSec, beam, damagePerSecond, beamWidthPx,
                missile, pointDefenceOnly, explosionRadiusPx, hitGlowRadiusPx, explosionColor, fringe, core,
                projectileSprite, projectileLengthPx, projectileWidthPx, muzzleColor, muzzleLengthPx,
                turretSprite, turretGunSprite, hardpointSprite, hardpointGunSprite, fireSoundOne,
                fireSoundTwo, impactSoundId, 0f, 0f);
    }

    /**
     * The sound this weapon is heard by: its {@code fireSoundOne}, else its
     * {@code fireSoundTwo} (many vanilla guns, and the lances, name only the
     * second), else {@code null}.
     */
    public String fireSoundId() {
        if (fireSoundOne != null && !fireSoundOne.isBlank()) return fireSoundOne;
        if (fireSoundTwo != null && !fireSoundTwo.isBlank()) return fireSoundTwo;
        return null;
    }

    /**
     * The sound held while a beam burns: its {@code fireSoundTwo}, but only when
     * {@code fireSoundOne} is also named. A weapon naming only the second has no
     * separate start sound, so that one is its single report and not a loop.
     */
    public String loopSoundId() {
        if (fireSoundOne == null || fireSoundOne.isBlank()) return null;
        return fireSoundTwo == null || fireSoundTwo.isBlank() ? null : fireSoundTwo;
    }

    /**
     * Builds a weapon from its table row, its {@code .wpn} spec and its projectile
     * spec. Any of the specs may be {@code null}: the table row alone says what a
     * round does, and a missing sprite only costs the look.
     */
    public static VanillaWeapon of(String id, Map<String, String> row, JSONObject wpn, JSONObject proj) {
        String hints = row.getOrDefault("hints", "");
        boolean pd = hints.contains("PD") && !hints.contains("PD_ALSO");
        boolean beam = wpn != null && "beam".equals(wpn.optString("specClass"));
        boolean missile = proj != null && "missile".equals(proj.optString("specClass"));
        float explosion = explosionRadius(proj);
        return new VanillaWeapon(id,
                number(row, "damage/shot", 0f),
                row.getOrDefault("type", ""),
                number(row, "range", 0f),
                Math.max(1, (int) number(row, "burst size", 1f)),
                number(row, "burst delay", 0f),
                number(row, "chargedown", 1f),
                number(row, "proj speed", 600f),
                number(row, "min spread", 0f),
                number(row, "turn rate", 0f),
                beam,
                number(row, "damage/second", 0f),
                wpn == null ? 0f : (float) wpn.optDouble("width", 0.0),
                missile, pd, explosion,
                proj == null ? 0f : (float) proj.optDouble("hitGlowRadius", 0.0),
                proj == null ? null : color(proj, "explosionColor"),
                proj == null ? null : color(proj, "fringeColor"),
                proj == null ? null : color(proj, "coreColor"),
                proj == null ? null : sprite(proj),
                proj == null ? 0f : (float) proj.optDouble("length", 0.0),
                proj == null ? 0f : (float) proj.optDouble("width", 0.0),
                muzzle(wpn, "particleColor"),
                muzzleLength(wpn),
                wpn == null ? null : wpn.optString("turretSprite", null),
                wpn == null ? null : wpn.optString("turretGunSprite", null),
                wpn == null ? null : wpn.optString("hardpointSprite", null),
                wpn == null ? null : wpn.optString("hardpointGunSprite", null),
                wpn == null ? null : wpn.optString("fireSoundOne", null),
                wpn == null ? null : wpn.optString("fireSoundTwo", null),
                impactSound(proj),
                number(row, "energy/shot", 0f),
                number(row, "energy/second", 0f));
    }

    /** The sound the round's blast names, on the projectile or inside its proximity-fuse explosion; else null. */
    private static String impactSound(JSONObject proj) {
        if (proj == null) return null;
        JSONObject blast = proj.optJSONObject("explosionSpec");
        if (blast == null) {
            JSONObject behaviour = proj.optJSONObject("behaviorSpec");
            blast = behaviour == null ? null : behaviour.optJSONObject("explosionSpec");
        }
        String sound = blast == null ? null : blast.optString("sound", null);
        return sound == null || sound.isBlank() ? null : sound;
    }

    /** A missile's explosion radius, or a projectile's proximity-fuse blast; zero for a plain slug. */
    private static float explosionRadius(JSONObject proj) {
        if (proj == null) return 0f;
        double direct = proj.optDouble("explosionRadius", 0.0);
        if (direct > 0.0) return (float) direct;
        JSONObject behaviour = proj.optJSONObject("behaviorSpec");
        JSONObject blast = behaviour == null ? null : behaviour.optJSONObject("explosionSpec");
        return blast == null ? 0f : (float) blast.optDouble("radius", 0.0);
    }

    private static Color muzzle(JSONObject wpn, String key) {
        JSONObject flash = wpn == null ? null : wpn.optJSONObject("muzzleFlashSpec");
        return flash == null ? null : color(flash, key);
    }

    private static float muzzleLength(JSONObject wpn) {
        JSONObject flash = wpn == null ? null : wpn.optJSONObject("muzzleFlashSpec");
        return flash == null ? 0f : (float) flash.optDouble("length", 0.0);
    }

    private static String sprite(JSONObject proj) {
        String bullet = proj.optString("bulletSprite", null);
        return bullet != null ? bullet : proj.optString("sprite", null);
    }

    private static Color color(JSONObject o, String key) {
        org.json.JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() < 3) return null;
        return new Color(a.optInt(0), a.optInt(1), a.optInt(2), a.length() > 3 ? a.optInt(3, 255) : 255);
    }

    private static float number(Map<String, String> row, String key, float fallback) {
        String v = row.get(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
