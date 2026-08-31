package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.util.Set;

/**
 * One weapon, parsed from a {@code *.weapon.json} entry. Immutable and
 * id-addressed; the authoring surface is the JSON, not this class.
 *
 * <p>The field set is the shared baseline populated by handheld, mech, and
 * turret weapons (see {@code moddable-weapons-nouns.md}). Carrier-specific
 * durability, magazines and mount art remain outside this definition.
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
    /** Compact player-facing battlefield role, such as LINE or SUPPORT. */
    public final String catalogRole;
    /** In-universe catalog copy authored in data for inspection surfaces. */
    public final String catalogDescription;

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
    /** Number of actor bodies a resolved direct-fire round may pass through before the next contact stops it. */
    public final int bodyPenetrations;
    /** Rounds per fire decision. 1 = single shot. */
    public final int burstCount;
    /** Sim-seconds between burst rounds. Ignored when {@link #burstCount} is 1. */
    public final float burstSpacing;
    /** Simultaneous projectiles released by each burst round. */
    public final int projectilesPerShot;
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
    /**
     * Whether a point-defence emplacement may engage this round in flight.
     * Distinct from {@link #interceptableProjectile}, which says only <em>how
     * the round is modelled</em> (a travelling entity rather than a resolved
     * shot event). This says the round is ordnance a defensive gun is allowed
     * to shoot down. A weapon opts in here; nothing derives the answer from an
     * id list, so a new warhead is engageable the moment its data says so.
     */
    public final boolean pointDefenseTarget;
    /** Whether an interceptable projectile accelerates through the shared boost-then-cruise motion curve. */
    public final boolean boostRamp;
    /** Whether acquisition and firing may continue without direct line of sight. */
    public final boolean indirectFire;
    /** Accuracy multiplier for an indirect shot fired without line of sight. */
    public final float noLosAccuracyMult;
    /** Coarse hearing-model magnitude; simulation data, never inferred from visual effects. */
    public final float noiseMagnitude;

    // ---- render ----
    /** Modular actor sprite family used while a marine carries this primary. */
    public final LayeredWeaponFamily heldSpriteFamily;
    /** Traveling-body and tracer-tail tint, so the player can identify fire at a glance. */
    public final Color tracerColor;
    /** Optional layered hitscan-beam treatment; defaults preserve the ordinary two-pixel tracer. */
    public final BeamStyle beamStyle;
    /** Optional short line trailing a projectile sprite, in cells; zero disables it. */
    public final float tracerTailCells;
    /** Optional projectile sprite; null means the shared tinted bolt. */
    public final String projectileSpritePath;
    /** Projectile visual size in cells (long axis). Ignored when {@link #projectileSpritePath} is null. */
    public final float projectileVisualCells;
    /** Optional persistent projectile-ribbon profile, resolved by render consumers. */
    public final ContrailProfile contrailProfile;
    /** Whether the authored FX declares a launch composition at the mount center. */
    public final boolean launchBackblast;
    /** Authored particle composition shared by runtime and deterministic previews. */
    public final WeaponFxDef fx;

    // ---- audio ----
    /** Vanilla fire sound id; mono, pre-registered by the core install. */
    public final String fireSoundId;
    /** Optional impact sound for projectile or detonation presentation. */
    public final String impactSoundId;

    private WeaponDef(String id, MountClass mount, String displayName, String modelName,
                      String designation, boolean designationTiered,
                      String catalogRole, String catalogDescription,
                      float range, float damage, float accuracy, float cooldown,
                      float penetration, float contactDamage, float contactPenetration,
                      int bodyPenetrations,
                      int burstCount, float burstSpacing, int projectilesPerShot,
                      float accuracyFalloff, float hitSpread, float roundVelocity,
                      float minRange, float aoeRadius, int wallDamage,
                      float wallDamageRadius, float aimDuration, float flightSec,
                      float arcHeight, boolean interceptableProjectile,
                      boolean pointDefenseTarget,
                      boolean boostRamp, boolean indirectFire, float noLosAccuracyMult,
                      float noiseMagnitude,
                      LayeredWeaponFamily heldSpriteFamily,
                      Color tracerColor,
                      BeamStyle beamStyle,
                      float tracerTailCells,
                      String projectileSpritePath, float projectileVisualCells,
                      ContrailProfile contrailProfile,
                      WeaponFxDef fx,
                      String fireSoundId, String impactSoundId) {
        this.id = id;
        this.mount = mount;
        this.displayName = displayName;
        this.modelName = modelName;
        this.designation = designation;
        this.designationTiered = designationTiered;
        this.catalogRole = catalogRole;
        this.catalogDescription = catalogDescription;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.penetration = penetration;
        this.contactDamage = contactDamage;
        this.contactPenetration = contactPenetration;
        this.bodyPenetrations = bodyPenetrations;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.projectilesPerShot = projectilesPerShot;
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
        this.pointDefenseTarget = pointDefenseTarget;
        this.boostRamp = boostRamp;
        this.indirectFire = indirectFire;
        this.noLosAccuracyMult = noLosAccuracyMult;
        this.noiseMagnitude = noiseMagnitude;
        this.heldSpriteFamily = heldSpriteFamily;
        this.tracerColor = tracerColor;
        this.beamStyle = beamStyle;
        this.tracerTailCells = tracerTailCells;
        this.projectileSpritePath = projectileSpritePath;
        this.projectileVisualCells = projectileVisualCells;
        this.contrailProfile = contrailProfile;
        this.launchBackblast = fx != null && !fx.layers(FxSlot.LAUNCH).isEmpty();
        this.fx = fx;
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

    public String designation(EquipmentGrade grade) {
        return designation(grade != null ? grade.tier : EquipmentGrade.SERVICE.tier);
    }

    public String catalogName(EquipmentGrade grade) {
        return catalogName(grade != null ? grade.tier : EquipmentGrade.SERVICE.tier);
    }

    // Record-style accessors keep definition consumers terse without reintroducing
    // carrier-specific enum handles.
    public String displayName() { return displayName; }
    public String modelName() { return modelName; }
    public float range() { return range; }
    public float damage() { return damage; }
    public float accuracy() { return accuracy; }
    public float cooldown() { return cooldown; }
    public float penetration() { return penetration; }
    public int burstCount() { return burstCount; }
    public int projectilesPerShot() { return projectilesPerShot; }
    public float burstSpacing() { return burstSpacing; }
    public float accuracyFalloff() { return accuracyFalloff; }
    public float hitSpread() { return hitSpread; }
    public float roundVelocity() { return roundVelocity; }
    public Color tracerColor() { return tracerColor; }
    public BeamStyle beamStyle() { return beamStyle; }
    public float tracerTailCells() { return tracerTailCells; }
    public String projectileSpritePath() { return projectileSpritePath; }
    public float projectileVisualCells() { return projectileVisualCells; }
    public String fireSoundId() { return fireSoundId; }

    /** Speed only for an interceptable projectile entity; resolved rounds return zero. */
    public float projectileCellsPerSec() {
        return interceptableProjectile ? roundVelocity : 0f;
    }

    /** Modeled direct-fire speed; nearer impacts arrive sooner. */
    public float directRoundVelocity() {
        return flightSec > 0f ? range / flightSec : 60f;
    }

    /** Strongest penetration payload this weapon can deliver to a selected target. */
    public float targetAffinityPenetration() {
        return Math.max(penetration, contactPenetration);
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
        if (!json.has("fx") || json.isNull("fx")) {
            throw new JSONException("Weapon '" + id + "' must declare authored fx");
        }
        rejectLegacyFxFields(render, id);
        WeaponFxDef fx = WeaponFxDef.parse(id, json.getJSONObject("fx"));
        WeaponDef def = new WeaponDef(
                id,
                mount,
                requireText(catalog, "displayName"),
                requireText(catalog, "modelName"),
                requireText(catalog, "designation"),
                catalog.optBoolean("designationTiered", true),
                emptyToNull(catalog.optString("role", null)),
                emptyToNull(catalog.optString("description", null)),
                (float) sim.getDouble("range"),
                (float) sim.getDouble("damage"),
                (float) sim.getDouble("accuracy"),
                (float) sim.getDouble("cooldown"),
                (float) sim.getDouble("penetration"),
                contact != null ? (float) contact.getDouble("damage") : 0f,
                contact != null ? (float) contact.getDouble("penetration") : 0f,
                sim.optInt("bodyPenetrations", 0),
                sim.optInt("burstCount", 1),
                (float) sim.optDouble("burstSpacing", 0.0),
                sim.optInt("projectilesPerShot", 1),
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
                sim.optBoolean("pointDefenseTarget", false),
                sim.optBoolean("boostRamp", false),
                sim.optBoolean("indirectFire", false),
                (float) sim.optDouble("noLosAccuracyMult", 1.0),
                (float) sim.optDouble("noiseMagnitude", 1.0),
                parseHeldSpriteFamily(render, mount, id),
                render != null ? parseColor(render.optString("tracerColor", null), id) : Color.WHITE,
                parseBeamStyle(render, id),
                render != null ? (float) render.optDouble("tracerTailCells", 0.0) : 0f,
                render != null ? emptyToNull(render.optString("projectileSprite", null)) : null,
                render != null ? (float) render.optDouble("projectileVisualCells", 0.0) : 0f,
                ContrailProfile.fromKey(
                        render != null ? render.optString("contrail", null) : null, id),
                fx,
                audio != null ? emptyToNull(audio.optString("fireSound", null)) : null,
                audio != null ? emptyToNull(audio.optString("impactSound", null)) : null);
        if (render != null && render.has("beam") && !render.isNull("beam")
                && def.projectileSpritePath != null) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares render.beam for a projectile-sprite weapon");
        }
        validateMountFields(def);
        return def;
    }

    private static BeamStyle parseBeamStyle(JSONObject render, String weaponId)
            throws JSONException {
        if (render == null || !render.has("beam") || render.isNull("beam")) {
            return BeamStyle.DEFAULT;
        }
        Object value = render.get("beam");
        if (!(value instanceof JSONObject beam)) {
            throw new JSONException("Weapon '" + weaponId + "' render.beam must be an object");
        }
        float coreWidth = (float) beam.optDouble("coreWidthPx", BeamStyle.DEFAULT.coreWidthPx());
        Color glowColor = beam.has("glowColor") && !beam.isNull("glowColor")
                ? parseColor(beam.getString("glowColor"), weaponId + ".render.beam") : null;
        float glowWidth = (float) beam.optDouble("glowWidthPx", 0.0);
        float pulseCycles = (float) beam.optDouble("pulseCycles", 0.0);
        var keys = beam.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!Set.of("coreWidthPx", "glowColor", "glowWidthPx", "pulseCycles").contains(key)) {
                throw new JSONException("Weapon '" + weaponId
                        + "' render.beam has unknown field '" + key + "'");
            }
        }
        try {
            return new BeamStyle(coreWidth, glowColor, glowWidth, pulseCycles);
        } catch (IllegalArgumentException e) {
            throw new JSONException("Invalid beam style for weapon '" + weaponId + "': " + e.getMessage());
        }
    }

    private static LayeredWeaponFamily parseHeldSpriteFamily(
            JSONObject render, MountClass mount, String weaponId) throws JSONException {
        if (mount != MountClass.MARINE_PRIMARY) {
            if (render != null && render.has("heldSpriteFamily")
                    && !render.isNull("heldSpriteFamily")) {
                throw new JSONException("Weapon '" + weaponId
                        + "' declares heldSpriteFamily outside the marine-primary mount class");
            }
            return null;
        }
        if (render == null) {
            throw new JSONException("Marine primary '" + weaponId
                    + "' requires render.heldSpriteFamily");
        }
        return LayeredWeaponFamily.fromKey(
                requireText(render, "heldSpriteFamily"), weaponId);
    }

    private static void validateMountFields(WeaponDef def) throws JSONException {
        if (def.mount == MountClass.MARINE_PRIMARY
                && (def.catalogRole == null || def.catalogDescription == null)) {
            throw new JSONException("Marine primary '" + def.id
                    + "' requires catalog.role and catalog.description");
        }
        if (def.burstCount < 1 || def.projectilesPerShot < 1) {
            throw new JSONException("Weapon '" + def.id
                    + "' must emit at least one burst round and one projectile per shot");
        }
        if (def.burstCount == 1 && def.burstSpacing != 0f) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares burstSpacing without a multi-round burst");
        }
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
        if (def.bodyPenetrations < 0) {
            throw new JSONException("Weapon '" + def.id + "' bodyPenetrations cannot be negative");
        }
        if (def.bodyPenetrations > 0
                && (def.mount != MountClass.MECH_MOUNT || def.indirectFire
                || def.interceptableProjectile || def.contactDamage <= 0f
                || !(def.roundVelocity > 0f))) {
            throw new JSONException("Weapon '" + def.id
                    + "' bodyPenetrations requires a direct resolved mech round with a contact payload");
        }
        if (def.interceptableProjectile && !(def.roundVelocity > 0f)) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares an interceptable projectile without positive roundVelocity");
        }
        if (def.pointDefenseTarget && !def.interceptableProjectile) {
            throw new JSONException("Weapon '" + def.id
                    + "' is a point-defence target without an in-flight projectile to intercept");
        }
        if (def.pointDefenseTarget && !(def.aoeRadius > 0f)) {
            throw new JSONException("Weapon '" + def.id
                    + "' is a point-defence target without a warhead; emplacements engage"
                    + " ordnance, not bullets");
        }
        if (def.boostRamp && !def.interceptableProjectile) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares boostRamp for a non-interceptable round");
        }
        if (!def.indirectFire && def.noLosAccuracyMult != 1f) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares noLosAccuracyMult without indirectFire");
        }
        if (!(def.noiseMagnitude > 0f) || !Float.isFinite(def.noiseMagnitude)) {
            throw new JSONException("Weapon '" + def.id
                    + "' noiseMagnitude must be finite and positive");
        }
        if (!Float.isFinite(def.tracerTailCells) || def.tracerTailCells < 0f) {
            throw new JSONException("Weapon '" + def.id
                    + "' tracerTailCells must be finite and non-negative");
        }
        if (def.tracerTailCells > 0f && def.projectileSpritePath == null) {
            throw new JSONException("Weapon '" + def.id
                    + "' declares tracerTailCells without a projectile sprite");
        }
        requireFxSlot(def, FxSlot.IMPACT);
        if (def.mount == MountClass.TURRET_MOUNT) {
            requireFxSlot(def, FxSlot.MUZZLE);
            if (def.aoeRadius >= 1f) requireFxSlot(def, FxSlot.AFTERMATH);
            if (def.interceptableProjectile) requireFxSlot(def, FxSlot.TRAIL);
        }
    }

    /**
     * Presentation-only style for a full-path hitscan beam. Widths are screen pixels so the beam
     * remains readable at every camera zoom; pulse cycles are counted over the shot's visual life.
     */
    public record BeamStyle(float coreWidthPx, Color glowColor,
                            float glowWidthPx, float pulseCycles) {
        public static final BeamStyle DEFAULT = new BeamStyle(2f, null, 0f, 0f);

        public BeamStyle {
            if (!(coreWidthPx > 0f) || !Float.isFinite(coreWidthPx)) {
                throw new IllegalArgumentException("coreWidthPx must be finite and positive");
            }
            if (!Float.isFinite(glowWidthPx) || glowWidthPx < 0f
                    || !Float.isFinite(pulseCycles) || pulseCycles < 0f) {
                throw new IllegalArgumentException("glowWidthPx and pulseCycles must be finite and non-negative");
            }
            if ((glowColor == null) != (glowWidthPx == 0f)) {
                throw new IllegalArgumentException("glowColor and positive glowWidthPx must be declared together");
            }
            if (glowColor != null && glowWidthPx <= coreWidthPx) {
                throw new IllegalArgumentException("glowWidthPx must exceed coreWidthPx");
            }
        }
    }

    private static void requireFxSlot(WeaponDef def, FxSlot slot) throws JSONException {
        if (def.fx.layers(slot).isEmpty()) {
            throw new JSONException("Weapon '" + def.id
                    + "' must declare fx slot '" + slot.key + "'");
        }
    }

    private static void rejectLegacyFxFields(JSONObject render, String weaponId)
            throws JSONException {
        if (render == null) return;
        for (String field : new String[] {"impact", "smokeTrail", "engineTrail"}) {
            if (render.has(field)) {
                throw new JSONException("Weapon '" + weaponId + "' uses retired render."
                        + field + "; author the corresponding fx slot instead");
            }
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

}
