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
 *   our.x = -vy_px * METERS_PER_PX   // vanilla port (+Y) → our starboard-negative (-X)
 *   our.y =  vx_px * METERS_PER_PX   // vanilla forward (+X) → our forward (+Y)
 * </pre>
 *
 * <p>Positions are <b>relative to the hull {@code center}</b> (its centroid of
 * gravity) — exactly as vanilla authors them — <em>not</em> shifted to the
 * sprite pixel centre. The air stack anchors each entity at {@code center}
 * (the hull render offsets its pixel centre by
 * {@link HullPivotResolver#pivotOffset}), so a center-relative slot drawn at
 * {@code body + R(facing)·offset} lands on its painted hardpoint with no
 * per-slot compensation. {@link ShipSpecEngineParser} is in the same frame, so
 * engine slots ride the same anchor for free.
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
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            JSONArray loc = slot.optJSONArray("locations");
            if (loc == null || loc.length() < 2) continue;
            float vx = (float) loc.getDouble(0);
            float vy = (float) loc.getDouble(1);
            String type = slot.optString("type", "");
            float angle = (float) slot.optDouble("angle", 0.0);
            out.add(new WeaponSlot(-vy * k, vx * k, type, angle));
        }
        return out;
    }
}
