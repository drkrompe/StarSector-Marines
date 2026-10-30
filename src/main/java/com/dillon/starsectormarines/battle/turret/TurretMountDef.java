package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, id-addressed turret carriage definition. The mount owns the
 * hardware around a gun: its magazine, traverse and the layered body art.
 * Ballistics and shot presentation remain on {@link #weapon}.
 */
public final class TurretMountDef {

    public final String id;
    public final String weaponId;
    public final WeaponDef weapon;
    /** Rounds carried by a mobile installation; zero means an unlimited static feed. */
    public final int ammoCapacity;
    public final float turnRateDegPerSec;
    /** Long-axis render extent, or zero when the carrier owns sheet-frame appearance. */
    public final float visualCells;
    /** Distance from mount center to the muzzle along the current bearing, in cells. */
    public final float muzzleOffsetCells;
    /**
     * Half-separation of an alternating paired barrel, perpendicular to the
     * current bearing. Zero means the mount has one launch line.
     */
    public final float muzzleLateralOffsetCells;
    /** Authored launch mouths in cells, ordered by release; empty retains legacy paired geometry. */
    public final List<Muzzle> muzzles;

    public record Muzzle(float lateral, float forward) {}

    /**
     * Whether the mount fires only at hardened targets (turrets, hubs, vehicles, heavy mechs), as a mech's
     * anti-armor rack does: a contact warhead spent on a rifleman is a warhead the next tank does not get.
     */
    public final boolean hardenedTargetsOnly;

    /**
     * What a round from the mount costs in flux, or null for a mount whose cadence is its cooldown alone. Every mount
     * a hull carries authors it (`convoy-nouns.md`, hull flux); an emplacement never reads it. The cost is drawn on
     * the hull's one reservoir, which every mount of the hull shares under the mech's admission rule
     * ({@code HullReservoir}). Flux sets what the gun can sustain, not what it carries: a gun held by flux leaves the
     * magazine untouched, so the hull still reads as armed while it vents.
     */
    public final Flux flux;

    /**
     * Seconds a hull's stock loader takes to put one trigger pack (the weapon's burst) back in this mount's
     * magazine, for the whole battle and never past its capacity (`convoy-nouns.md`, the loader); infinite when
     * nothing reloads it. Read by the vehicle turret pass only: an emplacement's magazine is its own.
     */
    public final float loaderSeconds;

    /**
     * Whether a vehicle carrying this mount also puts its gun on hostile aircraft: fighters, gunships and
     * shuttles in the air within its reach, with a clear line up. The gun is the same gun, on the same magazine,
     * cadence and flux, so a craft it takes is ground it leaves. Read by the vehicle turret pass only; an
     * emplacement's air role is its structure's {@code targetRole}.
     */
    public final boolean engagesAir;

    /**
     * How far below level the gun can point, in degrees. It matters only on a raised mount
     * ({@link StructureDef#plinthTiers}): a gun standing that high cannot reach the ground at its own foot,
     * so the plinth's height over the tangent of this angle is a range it cannot engage inside. Ninety, the
     * default, is a gun that can point straight down.
     */
    public final float maxDepressionDeg;

    /** Optional whole-texture mount body. Null lets a carrier own sheet-frame art. */
    public final String spritePath;
    /** Optional recoil/barrel layer paired with {@link #spritePath}. */
    public final String recoilSpritePath;
    /** How the barrel layer kicks back on a shot ({@code render.recoil}); {@link Recoil#DEFAULT} when not authored. */
    public final Recoil recoil;
    /** Whether the gun throws spent brass beside itself as it fires ({@code render.casings}). Presentation only. */
    public final boolean ejectsCasings;
    /**
     * Length in cells of the soot fan a round lays on the ground ahead of the muzzle ({@code render.scorch}); zero
     * for a gun that leaves none. Presentation only.
     */
    public final float scorchCells;

    /**
     * A barrel's kick: it jumps back {@code distanceFrac} of the mount's visual size when a round leaves and eases
     * home over {@code seconds}. Presentation only: no rule reads it.
     */
    public record Recoil(float distanceFrac, float seconds) {
        /** The kick every mount had before it could author its own. */
        public static final Recoil DEFAULT = new Recoil(0.10f, 0.12f);
    }

    private TurretMountDef(String id, String weaponId, WeaponDef weapon,
                           int ammoCapacity, float turnRateDegPerSec,
                           float visualCells, float muzzleOffsetCells,
                           float muzzleLateralOffsetCells, List<Muzzle> muzzles,
                           boolean hardenedTargetsOnly, Flux flux, float loaderSeconds,
                           boolean engagesAir, float maxDepressionDeg,
                           String spritePath, String recoilSpritePath, Recoil recoil,
                           boolean ejectsCasings, float scorchCells) {
        this.id = id;
        this.loaderSeconds = loaderSeconds;
        this.scorchCells = scorchCells;
        this.recoil = recoil;
        this.ejectsCasings = ejectsCasings;
        this.maxDepressionDeg = maxDepressionDeg;
        this.engagesAir = engagesAir;
        this.flux = flux;
        this.hardenedTargetsOnly = hardenedTargetsOnly;
        this.weaponId = weaponId;
        this.weapon = weapon;
        this.ammoCapacity = ammoCapacity;
        this.turnRateDegPerSec = turnRateDegPerSec;
        this.visualCells = visualCells;
        this.muzzleOffsetCells = muzzleOffsetCells;
        this.muzzleLateralOffsetCells = muzzleLateralOffsetCells;
        this.muzzles = List.copyOf(muzzles);
        this.spritePath = spritePath;
        this.recoilSpritePath = recoilSpritePath;
    }

    static TurretMountDef parse(JSONObject json, WeaponDef weapon) throws JSONException {
        String id = requireText(json, "id");
        String weaponId = requireText(json, "weapon");
        if (!weaponId.equals(weapon.id)) {
            throw new JSONException("Turret mount '" + id + "' resolved the wrong weapon '"
                    + weapon.id + "' for reference '" + weaponId + "'");
        }
        if (weapon.mount != MountClass.TURRET_MOUNT) {
            throw new JSONException("Turret mount '" + id + "' references non-turret weapon '"
                    + weaponId + "' (" + weapon.mount.key + ")");
        }
        JSONObject sim = json.getJSONObject("sim");
        JSONObject render = json.optJSONObject("render");
        int ammoCapacity = sim.getInt("ammoCapacity");
        float turnRate = (float) sim.getDouble("turnRateDegPerSec");
        float maxDepressionDeg = (float) sim.optDouble("maxDepressionDeg", 90.0);
        if (!(maxDepressionDeg > 0f && maxDepressionDeg <= 90f)) {
            throw new JSONException("Turret mount '" + id + "' maxDepressionDeg must be in (0, 90]");
        }
        Flux flux = null;
        JSONObject fluxJson = sim.optJSONObject("flux");
        if (fluxJson != null) {
            float perRound = (float) fluxJson.getDouble("perRound");
            requirePositiveFinite(perRound, id, "flux.perRound");
            flux = new Flux(perRound);
        }
        float loaderSeconds = sim.has("loaderSeconds")
                ? (float) sim.getDouble("loaderSeconds") : Float.POSITIVE_INFINITY;
        if (sim.has("loaderSeconds")) requirePositiveFinite(loaderSeconds, id, "loaderSeconds");
        float visualCells = render != null
                ? (float) render.optDouble("visualCells", 0.0) : 0f;
        float muzzleOffsetCells = render != null
                ? (float) render.optDouble("muzzleOffsetCells", 0.0) : 0f;
        float muzzleLateralOffsetCells = render != null
                ? (float) render.optDouble("muzzleLateralOffsetCells", 0.0) : 0f;
        if (ammoCapacity < 0) {
            throw new JSONException("Turret mount '" + id + "' ammoCapacity cannot be negative");
        }
        requirePositiveFinite(turnRate, id, "turnRateDegPerSec");
        if (!Float.isFinite(visualCells) || visualCells < 0f) {
            throw new JSONException("Turret definition '" + id
                    + "' field 'visualCells' must be finite and non-negative");
        }
        if (!Float.isFinite(muzzleOffsetCells) || muzzleOffsetCells < 0f) {
            throw new JSONException("Turret definition '" + id
                    + "' field 'muzzleOffsetCells' must be finite and non-negative");
        }
        if (!Float.isFinite(muzzleLateralOffsetCells) || muzzleLateralOffsetCells < 0f) {
            throw new JSONException("Turret definition '" + id
                    + "' field 'muzzleLateralOffsetCells' must be finite and non-negative");
        }
        List<Muzzle> muzzles = new ArrayList<>();
        JSONArray mouths = render != null ? render.optJSONArray("muzzles") : null;
        if (render != null && render.has("muzzles") && (mouths == null || mouths.length() == 0)) {
            throw new JSONException("Turret mount '" + id + "' muzzles must be a non-empty array");
        }
        for (int i = 0; mouths != null && i < mouths.length(); i++) {
            JSONArray point = mouths.getJSONArray(i);
            if (point.length() != 2) throw new JSONException("Muzzle must contain [lateral, forward]");
            float lateral = (float) point.getDouble(0);
            float forward = (float) point.getDouble(1);
            if (!Float.isFinite(lateral) || !Float.isFinite(forward)) {
                throw new JSONException("Turret mount '" + id + "' muzzle coordinates must be finite");
            }
            muzzles.add(new Muzzle(lateral, forward));
        }
        String sprite = render != null ? optionalText(render, "sprite") : null;
        String recoilSprite = render != null ? optionalText(render, "recoilSprite") : null;
        if ((sprite != null || recoilSprite != null) && visualCells == 0f) {
            throw new JSONException("Turret mount '" + id
                    + "' declares sprite appearance without positive visualCells");
        }
        if ((sprite != null || recoilSprite != null) && muzzleOffsetCells == 0f) {
            throw new JSONException("Turret mount '" + id
                    + "' declares sprite appearance without positive muzzleOffsetCells");
        }
        Recoil recoil = Recoil.DEFAULT;
        JSONObject kick = render != null ? render.optJSONObject("recoil") : null;
        if (kick != null) {
            float distance = (float) kick.optDouble("distanceFrac", Recoil.DEFAULT.distanceFrac());
            float seconds = (float) kick.optDouble("seconds", Recoil.DEFAULT.seconds());
            if (!Float.isFinite(distance) || distance < 0f || distance > 1f
                    || !Float.isFinite(seconds) || seconds <= 0f) {
                throw new JSONException("Turret mount '" + id
                        + "' recoil needs distanceFrac in [0, 1] and positive seconds");
            }
            recoil = new Recoil(distance, seconds);
        }
        float scorch = render != null ? (float) render.optDouble("scorch", 0.0) : 0f;
        if (!Float.isFinite(scorch) || scorch < 0f) {
            throw new JSONException("Turret mount '" + id + "' scorch must be a finite, non-negative length in cells");
        }
        return new TurretMountDef(
                id, weaponId, weapon, ammoCapacity, turnRate, visualCells, muzzleOffsetCells,
                muzzleLateralOffsetCells, muzzles, json.optBoolean("hardenedTargetsOnly", false),
                flux, loaderSeconds, json.optBoolean("engagesAir", false), maxDepressionDeg, sprite, recoilSprite, recoil,
                render != null && render.optBoolean("casings", false), scorch);
    }

    /**
     * This carriage carrying another gun: the same traverse, loader and body, with {@code weapon} in it, a
     * magazine of {@code ammoCapacity} pulls, the given flux and hardened-target rule, and the weapon's own single muzzle
     * {@code muzzleOffsetCells} out from the mount with its own casing and scorch effects. A vehicle's fitted weapon is
     * the stock mount with the weapon's own ballistics ({@code VehicleGun}).
     */
    public TurretMountDef withGun(WeaponDef weapon, int ammoCapacity, Flux flux, boolean hardenedTargetsOnly,
                                  float muzzleOffsetCells, boolean ejectsCasings, float scorchCells) {
        return new TurretMountDef(id, weapon.id, weapon, ammoCapacity, turnRateDegPerSec, visualCells,
                muzzleOffsetCells, 0f, List.of(), hardenedTargetsOnly, flux, loaderSeconds,
                engagesAir, maxDepressionDeg, spritePath, recoilSpritePath, recoil, ejectsCasings, scorchCells);
    }

    /**
     * What a round from this mount costs in flux ({@link TurretMountDef#flux}). The flux it draws on is the hull's
     * reservoir, never the mount's (`convoy-nouns.md`, hull flux).
     */
    public record Flux(float perRound) { }

    static String requireText(JSONObject json, String key) throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException("Turret definition is missing required text field '" + key + "'");
        }
        return value.trim();
    }

    static String optionalText(JSONObject json, String key) {
        String value = json.optString(key, null);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
    }

    static void requirePositiveFinite(float value, String id, String field) throws JSONException {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new JSONException("Turret definition '" + id + "' field '" + field
                    + "' must be finite and positive");
        }
    }
}
