package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One weapon slot of a hull, in the hull's own frame and in cells.
 *
 * <p>The frame is the same one every air craft's mounts use: {@code localY} runs
 * along the nose and {@code localX} across it, with the origin at the hull's
 * centre of gravity, so a slot's simulated position and its drawn position are
 * one point. A <b>hardpoint</b> is fixed to the hull and fires where the nose
 * points; a <b>turret</b> traverses within its arc.
 */
public record WarshipSlot(
        String id,
        String type,
        String size,
        boolean hardpoint,
        float localX,
        float localY,
        float angleDegrees,
        float arcDegrees) {

    /** Reads every slot a hull spec lists, converting its pixel offsets to cells. */
    public static List<WarshipSlot> parse(JSONObject hull) throws JSONException {
        List<WarshipSlot> out = new ArrayList<>();
        JSONArray slots = hull.optJSONArray("weaponSlots");
        if (slots == null) return out;
        float k = AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            JSONArray loc = slot.optJSONArray("locations");
            if (loc == null || loc.length() < 2) continue;
            float vx = (float) loc.getDouble(0);
            float vy = (float) loc.getDouble(1);
            out.add(new WarshipSlot(
                    slot.optString("id"),
                    slot.optString("type"),
                    slot.optString("size"),
                    "HARDPOINT".equals(slot.optString("mount")),
                    -vy * k, vx * k,
                    (float) slot.optDouble("angle", 0.0),
                    (float) slot.optDouble("arc", 0.0)));
        }
        return out;
    }
}
