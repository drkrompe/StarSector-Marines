package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.util.Map;

/**
 * How a missile flies and what it carries, read from its own weapon-table row and
 * projectile spec so a modded missile arrives with a sensible behaviour and the mod
 * authors no per-weapon numbers.
 *
 * <p>Distances and speeds are the game's (px, px/s, seconds, degrees per second);
 * {@link WarshipScale} turns them into cells where the ground battle needs them.
 * The turn rate here is the <i>missile's</i> ({@code engineSpec.turnRate}); the
 * weapon table's {@code turn rate} column is the mount's traverse and lives on
 * {@link VanillaWeapon#turnRateDegPerSec()}.
 *
 * <p>The {@link Guidance} class is derived, never authored: {@code missileType ROCKET}
 * or a missile that cannot turn is unguided; a {@code MIRV} behaviour is two-stage; a
 * {@code CUSTOM} behaviour (the dragon, the hydra's platforms) is custom; the
 * {@code GUIDED_POOR} hint is poorly guided; the rest home. Two-stage and custom
 * missiles fly as their carrier along this flight.
 */
public record MissileFlightSpec(
        Guidance guidance,
        float launchSpeedPx,
        float maxSpeedPx,
        float accelerationPx,
        float decelerationPx,
        float turnRateDegPerSec,
        float burnSeconds,
        float armingSeconds,
        float hitpoints,
        Color engineColor,
        Color contrailColor,
        float contrailSeconds,
        float contrailWidthMult,
        boolean glowTrail,
        float flameLengthPx,
        float flameWidthPx,
        Mirv mirv) {

    /** What steers the missile, derived from its spec. */
    public enum Guidance { UNGUIDED, HOMING, POORLY_GUIDED, TWO_STAGE, CUSTOM }

    /**
     * The warheads a multi-warhead missile releases, as the carrier's
     * {@code behaviorSpec} and the submunition's own spec declare them. The payload
     * of the missile is {@code count} blasts of {@code damage}, not the one row the
     * weapon table has for the carrier.
     */
    public record Mirv(
            int count,
            float splitRangePx,
            float minTimeToSplit,
            boolean canSplitEarly,
            float damage,
            String damageType,
            float warheadSpeedPx,
            float damageRadiusPx,
            float visualRadiusPx,
            Color explosionColor,
            String sprite,
            float arcDegrees,
            String splitSound,
            Color smokeColor,
            float warheadHitpoints) {

        /**
         * A payload read without the fan, the split cue, the smoke or the warheads' toughness: a
         * 180-degree fan, no sound, no smoke colour, and the default warhead toughness.
         */
        public Mirv(int count, float splitRangePx, float minTimeToSplit, boolean canSplitEarly, float damage,
                    String damageType, float warheadSpeedPx, float damageRadiusPx, float visualRadiusPx,
                    Color explosionColor, String sprite) {
            this(count, splitRangePx, minTimeToSplit, canSplitEarly, damage, damageType, warheadSpeedPx,
                    damageRadiusPx, visualRadiusPx, explosionColor, sprite, 180f, null, null,
                    DEFAULT_WARHEAD_HITPOINTS);
        }

        /** The damage the whole payload carries. */
        public float totalDamage() {
            return count * damage;
        }
    }

    /** The toughness of a warhead whose spec, and its carrier's, name none. */
    static final float DEFAULT_WARHEAD_HITPOINTS = 60f;
    /** No warhead is softer than this, however many share the carrier's own hit points. */
    static final float MIN_WARHEAD_HITPOINTS = 30f;

    private static final Color DEFAULT_ENGINE = new Color(255, 140, 40);
    private static final Color DEFAULT_CONTRAIL = new Color(120, 120, 120, 150);

    /** The warhead blast a submunition that declares no footprint of its own still has: a penetrator's pit, in px. */
    static final float MIN_WARHEAD_RADIUS_PX = 22f;

    /**
     * Reads a missile's flight from its weapon-table row and projectile spec, plus
     * the spec of the submunition it releases (or {@code null}). Returns {@code null}
     * for a projectile that is not a missile.
     */
    public static MissileFlightSpec read(Map<String, String> row, JSONObject proj, JSONObject warheadProj) {
        if (proj == null || !"missile".equals(proj.optString("specClass"))) return null;
        JSONObject engine = proj.optJSONObject("engineSpec");
        JSONObject behaviour = proj.optJSONObject("behaviorSpec");
        String behaviourKind = behaviour == null ? "" : behaviour.optString("behavior", "");
        float turn = engine == null ? 0f : (float) engine.optDouble("turnRate", 0.0);
        float maxSpeed = VanillaWeapon.number(row, "proj speed", 600f);
        float acc = engine == null ? 0f : (float) engine.optDouble("acc", 0.0);
        float dec = engine == null ? 0f : (float) engine.optDouble("dec", 0.0);
        String hints = row.getOrDefault("hints", "");

        Guidance guidance;
        if ("MIRV".equals(behaviourKind)) guidance = Guidance.TWO_STAGE;
        else if ("CUSTOM".equals(behaviourKind)) guidance = Guidance.CUSTOM;
        else if ("ROCKET".equals(proj.optString("missileType")) || turn <= 0f) guidance = Guidance.UNGUIDED;
        else if (hints.contains("GUIDED_POOR")) guidance = Guidance.POORLY_GUIDED;
        else guidance = Guidance.HOMING;

        JSONObject slot = null;
        JSONArray slots = proj.optJSONArray("engineSlots");
        if (slots != null && slots.length() > 0) slot = slots.optJSONObject(0);
        JSONObject style = slot == null ? null : slot.optJSONObject("styleSpec");
        Color engineColor = style == null ? null : VanillaWeapon.color(style, "engineColor");
        Color contrail = style == null ? null : VanillaWeapon.color(style, "contrailColor");

        return new MissileFlightSpec(guidance,
                VanillaWeapon.number(row, "launch speed", 0f),
                maxSpeed, acc, dec, turn,
                VanillaWeapon.number(row, "flight time", 0f),
                (float) proj.optDouble("armingTime", 0.0),
                VanillaWeapon.number(row, "proj hitpoints", 0f),
                engineColor != null ? engineColor : DEFAULT_ENGINE,
                contrail != null ? contrail : DEFAULT_CONTRAIL,
                style == null ? 1f : (float) style.optDouble("contrailDuration", 1.0),
                style == null ? 1f : (float) style.optDouble("contrailWidthMult", 1.0),
                style != null && "GLOW".equals(style.optString("type")),
                slot == null ? 20f : (float) slot.optDouble("length", 20.0),
                slot == null ? 4f : (float) slot.optDouble("width", 4.0),
                "MIRV".equals(behaviourKind) ? mirv(behaviour, warheadProj, row, maxSpeed) : null);
    }

    private static Mirv mirv(JSONObject behaviour, JSONObject warheadProj, Map<String, String> row, float carrierSpeed) {
        int count = Math.max(1, behaviour.optInt("numShots", 1));
        JSONObject warheadEngine = warheadProj == null ? null : warheadProj.optJSONObject("engineSpec");
        float warheadSpeed = warheadEngine == null ? 0f : (float) warheadEngine.optDouble("maxSpeed", 0.0);
        if (warheadSpeed <= 0f) warheadSpeed = (float) behaviour.optDouble("spreadSpeed", 0.0);
        if (warheadSpeed <= 0f) warheadSpeed = carrierSpeed;
        float visual = warheadProj == null ? 0f : (float) warheadProj.optDouble("explosionRadius", 0.0);
        float hitGlow = warheadProj == null ? 0f : (float) warheadProj.optDouble("hitGlowRadius", 0.0);
        float damageRadius = VanillaWeapon.damageRadius(warheadProj, visual > 0f ? visual : hitGlow);
        JSONObject smoke = behaviour.optJSONObject("smokeSpec");
        // A warhead has no hit points of its own in the data; the carrier's are shared among them.
        double declared = warheadProj == null ? 0.0 : warheadProj.optDouble("hitpoints", 0.0);
        double carrier = behaviour.optDouble("hitpoints", 0.0);
        float warheadHp = declared > 0.0 ? (float) declared
                : carrier > 0.0 ? Math.max(MIN_WARHEAD_HITPOINTS, (float) carrier / count) : DEFAULT_WARHEAD_HITPOINTS;
        String splitSound = behaviour.optString("splitSound", null);
        return new Mirv(count,
                (float) behaviour.optDouble("splitRange", 0.0),
                (float) behaviour.optDouble("minTimeToSplit", 0.0),
                behaviour.optBoolean("canSplitEarly", false),
                (float) behaviour.optDouble("damage", VanillaWeapon.number(row, "damage/shot", 0f)),
                behaviour.optString("damageType", row.getOrDefault("type", "")),
                warheadSpeed,
                Math.max(MIN_WARHEAD_RADIUS_PX, damageRadius),
                Math.max(visual, hitGlow),
                warheadProj == null ? null : VanillaWeapon.color(warheadProj, "explosionColor"),
                warheadProj == null ? null : warheadSprite(warheadProj),
                Math.max(1f, Math.min(360f, (float) behaviour.optDouble("arc", 180.0))),
                splitSound == null || splitSound.isBlank() ? null : splitSound,
                smoke == null ? null : VanillaWeapon.color(smoke, "particleColor"),
                warheadHp);
    }

    private static String warheadSprite(JSONObject warheadProj) {
        String bullet = warheadProj.optString("bulletSprite", null);
        return bullet != null ? bullet : warheadProj.optString("sprite", null);
    }
}
