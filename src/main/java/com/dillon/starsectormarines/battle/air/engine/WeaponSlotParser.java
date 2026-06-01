package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code weaponSlots} array out of a vanilla {@code .ship} hull spec
 * and converts each slot's {@code locations} pixel pair into the shuttle-local
 * frame at the one global pixel density ({@link AirScale#METERS_PER_PX}) — the
 * same frame swap {@link ShipSpecEngineParser} uses for engine slots:
 *
 * <pre>
 *   our.x = (-vy_px + (cx - W/2)) * METERS_PER_PX   // vanilla port (+Y) → our -X
 *   our.y = ( vx_px + (cy - H/2)) * METERS_PER_PX   // vanilla forward (+X) → our +Y
 * </pre>
 *
 * <h2>Center compensation (the bug this fixes)</h2>
 * <p>Vanilla slot {@code locations} are authored relative to the hull's
 * {@code center} pivot ({@code [xFromLeft, yFromBottom]}), <em>not</em> the
 * sprite pixel centre. We render the hull pixel-centred (the sprite's image
 * centre sits at the entity position), so each slot must be shifted by
 * {@code (center - pixelCentre)} to land on the painted hardpoint. Skipping this
 * put turrets ~{@code (H/2 - cy) * METERS_PER_PX} cells off along the hull —
 * small on a Kite (~0.2 cells), very visible on a Valkyrie (~0.85). Confirmed
 * against the painted art by {@code TurretSlotPreviewTest}.
 *
 * <p>NOTE: {@link ShipSpecEngineParser} has the same latent offset (it anchors
 * engine slots at the pixel centre too); it's just less visible on fuzzy engine
 * glows. Apply the same compensation there if engines read off.
 *
 * <p>Returns <em>every</em> slot with a valid location (type unfiltered) so the
 * preview can show the full hardpoint set; {@link TurretSlotResolver} applies the
 * mountable-type filter. Pure (no {@code Global}) so the offline
 * {@code TurretSlotPreviewTest} exercises the exact in-game transform.
 */
public final class WeaponSlotParser {

    private WeaponSlotParser() {}

    /** Convenience overload taking the raw {@code .ship} JSON text. */
    public static List<WeaponSlot> parse(String shipJson) throws JSONException {
        return parse(new JSONObject(shipJson));
    }

    /**
     * The vanilla {@code center} (the hull's pivot), raw {@code [xFromLeft,
     * yFromBottom]} in sprite pixels — or {@code null} if absent. Slot
     * {@code locations} are authored relative to <em>this</em>, not the sprite
     * pixel centre; diagnostic for the preview / any center-anchor work.
     */
    public static float[] parseCenter(String shipJson) throws JSONException {
        return parseCenter(new JSONObject(shipJson));
    }

    public static float[] parseCenter(JSONObject root) throws JSONException {
        JSONArray c = root.optJSONArray("center");
        if (c == null || c.length() < 2) return null;
        return new float[]{ (float) c.getDouble(0), (float) c.getDouble(1) };
    }

    public static List<WeaponSlot> parse(JSONObject root) throws JSONException {
        List<WeaponSlot> out = new ArrayList<>();
        JSONArray slots = root.optJSONArray("weaponSlots");
        if (slots == null || slots.length() == 0) return out;
        // px → cell at the global density (METERS_PER_CELL == 1).
        float k = AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
        // Pivot offset from the sprite pixel centre (see class javadoc). center
        // is [xFromLeft, yFromBottom]; both axes are in the vanilla slot frame
        // (vx forward, vy port), so they add directly to the raw locations.
        float w = (float) root.optDouble("width", 0.0);
        float h = (float) root.optDouble("height", 0.0);
        float[] c = parseCenter(root);
        float pivotX = (c != null ? c[0] : w / 2f) - w / 2f;   // vanilla port (+y) axis
        float pivotY = (c != null ? c[1] : h / 2f) - h / 2f;   // vanilla forward (+x) axis
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            JSONArray loc = slot.optJSONArray("locations");
            if (loc == null || loc.length() < 2) continue;
            float vx = (float) loc.getDouble(0);
            float vy = (float) loc.getDouble(1);
            String type = slot.optString("type", "");
            float angle = (float) slot.optDouble("angle", 0.0);
            out.add(new WeaponSlot((-vy + pivotX) * k, (vx + pivotY) * k, type, angle));
        }
        return out;
    }
}
