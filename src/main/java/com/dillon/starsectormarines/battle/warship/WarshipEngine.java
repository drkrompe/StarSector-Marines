package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One engine nozzle of a hull, in the hull's own frame and in cells: where the
 * flame starts, which way it burns and how big the hull's data makes it.
 *
 * <p>The frame is {@link WarshipSlot}'s, so a nozzle and a gun on the same hull
 * are placed by one rule: {@code localY} along the nose, {@code localX} across it,
 * origin at the centre of gravity. {@code angleDegrees} is the direction the
 * <em>flame</em> burns, in the same hull-relative convention as a weapon slot's
 * angle: 180 is straight aft, so the ship is pushed the other way.
 *
 * <p>The style is the hull spec's own ({@code LOW_TECH}, {@code HIGH_TECH}, ...);
 * the engine colour that goes with it is the game's, held by
 * {@code EngineStylePalette}.
 */
public record WarshipEngine(
        float localX,
        float localY,
        float angleDegrees,
        float lengthCells,
        float widthCells,
        String style) {

    private static final Map<String, List<WarshipEngine>> BY_HULL = new ConcurrentHashMap<>();

    /**
     * Reads every engine slot a hull spec lists, converting its pixel offsets and
     * sizes to cells. A spec without an {@code engineSlots} array has no nozzles.
     */
    public static List<WarshipEngine> parse(JSONObject hull) throws JSONException {
        List<WarshipEngine> out = new ArrayList<>();
        JSONArray slots = hull.optJSONArray("engineSlots");
        if (slots == null) return out;
        float k = WarshipScale.cells(1f);
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            JSONArray loc = slot.optJSONArray("location");
            if (loc == null) loc = slot.optJSONArray("locations");
            if (loc == null || loc.length() < 2) continue;
            float vx = (float) loc.getDouble(0);
            float vy = (float) loc.getDouble(1);
            out.add(new WarshipEngine(
                    -vy * k, vx * k,
                    (float) slot.optDouble("angle", 180.0),
                    (float) slot.optDouble("length", 8.0) * k,
                    (float) slot.optDouble("width", 4.0) * k,
                    slot.optString("style", "MIDLINE")));
        }
        return out;
    }

    /**
     * The nozzles of {@code hullId}, read once through {@link VanillaData} and
     * remembered. A hull that cannot be read has none, so its ship simply burns
     * no flame rather than failing to draw.
     */
    public static List<WarshipEngine> forHull(String hullId) {
        if (hullId == null || hullId.isEmpty()) return List.of();
        return BY_HULL.computeIfAbsent(hullId, id -> {
            try {
                return Collections.unmodifiableList(parse(VanillaData.json("data/hulls/" + id + ".ship")));
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    /** Forgets what was read, for a test that swaps {@link VanillaData}'s source. */
    static void clearCache() {
        BY_HULL.clear();
    }
}
