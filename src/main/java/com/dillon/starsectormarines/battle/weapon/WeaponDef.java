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
    /** Efficiency input against actor armor; does not amplify exposed-structure damage. */
    public final float penetration;
    /** Optional damage delivered only to the actor physically contacted by an explosive round. */
    public final float contactDamage;
    /** Armor penetration paired with {@link #contactDamage}; zero when no distinct contact payload exists. */
    public final float contactPenetration;
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
    /** Minimum legal firing range; zero for ordinary direct fire. */
    public final float minRange;
    /** Splash radius in cells; zero for precise rounds. */
    public final float aoeRadius;
    /** Structural damage applied by a detonation. */
    public final int wallDamage;
    /** Structural-damage reach around the impact point. */
    public final float wallDamageRadius;
    /** Carrier-owned pre-fire commitment window for handheld specials. */
    public final float aimDuration;
    /** Maximum-range travel time retained for projectile presentation. */
    public final float flightSec;
    /** Visual arc height for lobbed projectiles. */
    public final float arcHeight;
    /** Whether the round exists as an interceptable in-flight projectile rather than only a resolved shot event. */
    public final boolean interceptableProjectile;
    /** Whether an interceptable projectile accelerates through the shared boost-then-cruise motion curve. */
    public final boolean boostRamp;
    /** Whether acquisition and firing may continue without direct line of sight. */
    public final boolean indirectFire;
    /** Accuracy multiplier for an indirect shot fired without line of sight. */
    public final float noLosAccuracyMult;

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
    /** Whether the traveling body emits the compatibility smoke-puff trail. W2 replaces this with authored layers. */
    public final boolean smokeTrail;
    /** Whether firing emits the compatibility launcher backblast. W2 replaces this with authored layers. */
    public final boolean launchBackblast;

    // ---- audio ----
    /** Vanilla fire sound id; mono, pre-registered by the core install. */
    public final String fireSoundId;
    /** Optional impact sound for projectile or detonation presentation. */
    public final String impactSoundId;

    private WeaponDef(String id, MountClass mount, String displayName, String modelName,
                      String designation, boolean designationTiered,
                      float range, float damage, float accuracy, float cooldown,
                      float penetration, float contactDamage, float contactPenetration,
                      int burstCount, float burstSpacing,
                      float accuracyFalloff, float hitSpread, float roundVelocity,
                      float minRange, float aoeRadius, int wallDamage,
                      float wallDamageRadius, float aimDuration, float flightSec,
                      float arcHeight, boolean interceptableProjectile,
                      boolean boostRamp, boolean indirectFire, float noLosAccuracyMult,
                      Color tracerColor, ImpactProfile impactProfile,
                      String projectileSpritePath, float projectileVisualCells,
                      boolean smokeTrail, boolean launchBackblast,
                      String fireSoundId, String impactSoundId) {
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
        this.penetration = penetration;
        this.contactDamage = contactDamage;
        this.contactPenetration = contactPenetration;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.accuracyFalloff = accuracyFalloff;
        this.hitSpread = hitSpread;
        this.roundVelocity = roundVelocity;
        this.minRange = minRange;
        this.aoeRadius = aoeRadius;
        this.wallDamage = wallDamage;
        this.wallDamageRadius = wallDamageRadius;
        this.aimDuration = aimDuration;
        this.flightSec = flightSec;
        this.arcHeight = arcHeight;
        this.interceptableProjectile = interceptableProjectile;
        this.boostRamp = boostRamp;
        this.indirectFire = indirectFire;
        this.noLosAccuracyMult = noLosAccuracyMult;
        this.tracerColor = tracerColor;
        this.impactProfile = impactProfile;
        this.projectileSpritePath = projectileSpritePath;
        this.projectileVisualCells = projectileVisualCells;
        this.smokeTrail = smokeTrail;
        this.launchBackblast = launchBackblast;
        this.fireSoundId = fireSoundId;
        this.impactSoundId = impactSoundId;
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
        JSONObject contact = sim.optJSONObject("contact");
        JSONObject render = json.optJSONObject("render");
        JSONObject audio = json.optJSONObject("audio");
        WeaponDef def = new WeaponDef(
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
                (float) sim.getDouble("penetration"),
                contact != null ? (float) contact.getDouble("damage") : 0f,
                contact != null ? (float) contact.getDouble("penetration") : 0f,
                sim.optInt("burstCount", 1),
                (float) sim.optDouble("burstSpacing", 0.0),
                (float) sim.optDouble("accuracyFalloff", 0.0),
                (float) sim.optDouble("hitSpread", 0.0),
                (float) sim.optDouble("roundVelocity", 0.0),
                (float) sim.optDouble("minRange", 0.0),
                (float) sim.optDouble("aoeRadius", 0.0),
                sim.optInt("wallDamage", 0),
                (float) sim.optDouble("wallDamageRadius", 0.0),
                (float) sim.optDouble("aimDuration", 0.0),
                (float) sim.optDouble("flightSec", 0.0),
                (float) sim.optDouble("arcHeight", 0.0),
                sim.optBoolean("interceptableProjectile", false),
                sim.optBoolean("boostRamp", false),
                sim.optBoolean("indirectFire", false),
                (float) sim.optDouble("noLosAccuracyMult", 1.0),
                render != null ? parseColor(render.optString("tracerColor", null), id) : Color.WHITE,
                render != null ? parseImpact(render.optString("impact", null), id) : ImpactProfile.RIFLE,
                render != null ? emptyToNull(render.optString("projectileSprite", null)) : null,
                render != null ? (float) render.optDouble("projectileVisualCells", 0.0) : 0f,
                render != null && render.optBoolean("smokeTrail", false),
                render != null && render.optBoolean("launchBackblast", false),
                audio != null ? emptyToNull(audio.optString("fireSound", null)) : null,
                audio != null ? emptyToNull(audio.optString("impactSound", null)) : null);
        validateMountFields(def);
        return def;
    }

    private static void validateMountFields(WeaponDef def) throws JSONException {
        if (def.mount == MountClass.MARINE_PRIMARY
                && (def.aoeRadius != 0f || def.wallDamage != 0
                || def.wallDamageRadius != 0f || def.aimDuration != 0f
                || def.minRange != 0f || def.arcHeight != 0f)) {
            throw new JSONException("Marine primary '" + def.id
                    + "' declares special or mounted-weapon fields");
        }
        if (def.mount != MountClass.MARINE_SECONDARY && def.aimDuration != 0f) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares aimDuration outside the marine-secondary mount class");
        }
        if (def.wallDamageRadius > 0f && def.wallDamage <= 0) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares wallDamageRadius without wallDamage");
        }
        if ((def.contactDamage == 0f) != (def.contactPenetration == 0f)) {
            throw new JSONException("Weapon '" + def.id
                    + "' must declare both contact damage and contact penetration");
        }
        if (def.contactDamage < 0f || def.contactPenetration < 0f) {
            throw new JSONException("Weapon '" + def.id + "' contact payload cannot be negative");
        }
        if (def.interceptableProjectile && !(def.roundVelocity > 0f)) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares an interceptable projectile without positive roundVelocity");
        }
        if (def.boostRamp && !def.interceptableProjectile) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares boostRamp for a non-interceptable round");
        }
        if (!def.indirectFire && def.noLosAccuracyMult != 1f) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares noLosAccuracyMult without indirectFire");
        }
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
