package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONException;
import org.json.JSONObject;

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
    public final float visualCells;
    /** Optional whole-texture mount body. Null lets a carrier own sheet-frame art. */
    public final String spritePath;
    /** Optional recoil/barrel layer paired with {@link #spritePath}. */
    public final String recoilSpritePath;

    private TurretMountDef(String id, String weaponId, WeaponDef weapon,
                           int ammoCapacity, float turnRateDegPerSec,
                           float visualCells,
                           String spritePath, String recoilSpritePath) {
        this.id = id;
        this.weaponId = weaponId;
        this.weapon = weapon;
        this.ammoCapacity = ammoCapacity;
        this.turnRateDegPerSec = turnRateDegPerSec;
        this.visualCells = visualCells;
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
        JSONObject render = json.getJSONObject("render");
        int ammoCapacity = sim.getInt("ammoCapacity");
        float turnRate = (float) sim.getDouble("turnRateDegPerSec");
        float visualCells = (float) render.getDouble("visualCells");
        if (ammoCapacity < 0) {
            throw new JSONException("Turret mount '" + id + "' ammoCapacity cannot be negative");
        }
        requirePositiveFinite(turnRate, id, "turnRateDegPerSec");
        requirePositiveFinite(visualCells, id, "visualCells");
        return new TurretMountDef(
                id, weaponId, weapon, ammoCapacity, turnRate, visualCells,
                optionalText(render, "sprite"), optionalText(render, "recoilSprite"));
    }

    static String requireText(JSONObject json, String key) throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException("Turret definition is missing required text field '" + key + "'");
        }
        return value.trim();
    }

    private static String optionalText(JSONObject json, String key) {
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
