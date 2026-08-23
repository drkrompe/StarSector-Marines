package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;

/**
 * One weapon, parsed from a {@code *.weapon.json} entry. Immutable and
 * id-addressed; the authoring surface is the JSON, not this class.
 *
 * <p>The current field set is the shared baseline populated by marine
 * primaries (see {@code moddable-weapons-nouns.md}). Later catalog stories
 * extend that shape for secondaries, mech mounts, and turret mounts, then
 * validate which fields each {@link #mount} class may declare.
 *
 * <p>Grouped by who reads it: the {@code sim} block feeds
 * {@link com.dillon.starsectormarines.battle.infantry.InfantryCombatStats}
 * and the firing pipeline, {@code render} / {@code audio} are presentation
 * only and are never read by the simulation, and {@code catalog} is
 * player-facing naming.
 */
public final class WeaponDef {

    // ---- identity ----
    /** Stable namespaced id, e.g. {@code weapon.pulse-rifle}. Save-persisted once W4 lands, so it is API. */
    public final String id;
    public final MountClass mount;

    // ---- catalog (player-facing naming) ----
    public final String displayName;
    /** In-universe model name — the "Lancer" in "PLS-2 Lancer". */
    public final String modelName;
    /** Fleet catalog prefix. Combined with the equipment tier when {@link #designationTiered}. */
    public final String designation;
    /** Whether {@link #designation} takes a {@code -<tier>} suffix. Recruit issue is always "FR-1"; a pulse rifle is "PLS-1" through "PLS-4". */
    public final boolean designationTiered;

    // ---- sim ----
    public final float range;
    public final float damage;
    public final float accuracy;
    /** Seconds between trigger pulls. */
    public final float cooldown;
    /**
     * Damage multiplier against hardened classes (turrets, drone hubs, heavy
     * mechs). Named honestly here; the Java field it feeds is still called
     * {@code vsTurretMult}, which {@code DamageResolver} already documents as
     * "misnamed history".
     */
    public final float vsHardenedMult;
    /** Rounds per fire decision. 1 = single shot. */
    public final int burstCount;
    /** Sim-seconds between burst rounds. Ignored when {@link #burstCount} is 1. */
    public final float burstSpacing;
    /** Fraction of base {@link #accuracy} lost at {@link #range} cells. */
    public final float accuracyFalloff;
    /** Lateral scatter radius in cells at {@link #range}. */
    public final float hitSpread;
    /** Round speed in cells/sec. */
    public final float roundVelocity;

    // ---- render ----
    /** Traveling-body tint, so the player can identify fire at a glance. */
    public final Color tracerColor;
    /**
     * Impact character. A named profile reference today; W2 replaces this
     * with an authored layer list, at which point this field goes away.
     */
    public final ImpactProfile impactProfile;
    /** Optional projectile sprite; null means the shared tinted bolt. */
    public final String projectileSpritePath;
    /** Projectile visual size in cells (long axis). Ignored when {@link #projectileSpritePath} is null. */
    public final float projectileVisualCells;

    // ---- audio ----
    /** Vanilla fire sound id; mono, pre-registered by the core install. */
    public final String fireSoundId;

    private WeaponDef(String id, MountClass mount, String displayName, String modelName,
                      String designation, boolean designationTiered,
                      float range, float damage, float accuracy, float cooldown,
                      float vsHardenedMult, int burstCount, float burstSpacing,
                      float accuracyFalloff, float hitSpread, float roundVelocity,
                      Color tracerColor, ImpactProfile impactProfile,
                      String projectileSpritePath, float projectileVisualCells,
                      String fireSoundId) {
        this.id = id;
        this.mount = mount;
        this.displayName = displayName;
        this.modelName = modelName;
        this.designation = designation;
        this.designationTiered = designationTiered;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.vsHardenedMult = vsHardenedMult;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.accuracyFalloff = accuracyFalloff;
        this.hitSpread = hitSpread;
        this.roundVelocity = roundVelocity;
        this.tracerColor = tracerColor;
        this.impactProfile = impactProfile;
        this.projectileSpritePath = projectileSpritePath;
        this.projectileVisualCells = projectileVisualCells;
        this.fireSoundId = fireSoundId;
    }

    /** Catalog designation for one equipment tier — "PLS-2", or "FR-1" for an untiered entry. */
    public String designation(int tier) {
        return designationTiered ? designation + "-" + tier : designation;
    }

    /** Catalog designation plus model name, e.g. "PLS-2 Lancer". */
    public String catalogName(int tier) {
        return designation(tier) + " " + modelName;
    }

    /**
     * Parses one entry. Throws rather than defaulting on anything a weapon
     * cannot function without — a typo in a damage figure should fail at
     * load, not produce a silently harmless gun.
     */
    public static WeaponDef parse(JSONObject json) throws JSONException {
        String id = requireText(json, "id");
        MountClass mount = MountClass.fromKey(requireText(json, "mount"), id);
        JSONObject catalog = json.getJSONObject("catalog");
        JSONObject sim = json.getJSONObject("sim");
        JSONObject render = json.optJSONObject("render");
        JSONObject audio = json.optJSONObject("audio");
        return new WeaponDef(
                id,
                mount,
                requireText(catalog, "displayName"),
                requireText(catalog, "modelName"),
                requireText(catalog, "designation"),
                catalog.optBoolean("designationTiered", true),
                (float) sim.getDouble("range"),
                (float) sim.getDouble("damage"),
                (float) sim.getDouble("accuracy"),
                (float) sim.getDouble("cooldown"),
                (float) sim.optDouble("vsHardenedMult", 1.0),
                sim.optInt("burstCount", 1),
                (float) sim.optDouble("burstSpacing", 0.0),
                (float) sim.optDouble("accuracyFalloff", 0.0),
                (float) sim.optDouble("hitSpread", 0.0),
                (float) sim.optDouble("roundVelocity", 0.0),
                render != null ? parseColor(render.optString("tracerColor", null), id) : Color.WHITE,
                render != null ? parseImpact(render.optString("impact", null), id) : ImpactProfile.RIFLE,
                render != null ? emptyToNull(render.optString("projectileSprite", null)) : null,
                render != null ? (float) render.optDouble("projectileVisualCells", 0.0) : 0f,
                audio != null ? emptyToNull(audio.optString("fireSound", null)) : null);
    }

    private static String requireText(JSONObject json, String key) throws JSONException {
        String value = emptyToNull(json.optString(key, null));
        if (value == null) throw new JSONException("Weapon entry is missing required text field '" + key + "'");
        return value;
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
    }

    /** {@code "80FF80"} or {@code "#80FF80"} → opaque RGB. */
    private static Color parseColor(String hex, String weaponId) throws JSONException {
        String value = emptyToNull(hex);
        if (value == null) return Color.WHITE;
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.length() != 6) {
            throw new JSONException("Weapon '" + weaponId + "' tracerColor must be 6 hex digits, got '" + hex + "'");
        }
        try {
            return new Color(Integer.parseInt(digits.substring(0, 2), 16),
                    Integer.parseInt(digits.substring(2, 4), 16),
                    Integer.parseInt(digits.substring(4, 6), 16));
        } catch (NumberFormatException e) {
            throw new JSONException("Weapon '" + weaponId + "' tracerColor is not hex: '" + hex + "'");
        }
    }

    private static ImpactProfile parseImpact(String key, String weaponId) throws JSONException {
        String value = emptyToNull(key);
        if (value == null) return ImpactProfile.RIFLE;
        for (ImpactProfile profile : ImpactProfile.values()) {
            if (profile.name().equalsIgnoreCase(value.replace('-', '_'))) return profile;
        }
        throw new JSONException("Weapon '" + weaponId + "' has unknown impact profile '" + key + "'");
    }
}
