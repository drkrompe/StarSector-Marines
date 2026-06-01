package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-hull <b>pivot offset</b>: the vector from the hull's authored
 * {@code center} (its centroid of gravity) to the sprite's geometric pixel
 * centre, in our cell frame. Cached by hull id; mirrors
 * {@link HullFootprintResolver} / {@link EngineSlotResolver}.
 *
 * <h2>Why</h2>
 * <p>Vanilla authors all geometry — {@code weaponSlots}, {@code engineSlots},
 * {@code bounds} — relative to {@code center}, and a ship pivots around
 * {@code center}. But {@code center} is <em>not</em> the sprite's pixel centre
 * (it sits toward the tail). The renderer anchors the hull at {@code body} and
 * we want {@code body} to mean the centre of gravity, so the hull is drawn with
 * its pixel centre offset from {@code body} by this vector (rotated by facing) —
 * which keeps {@code center} fixed at {@code body} and makes every
 * center-relative slot land on its painted hardpoint with no per-slot fix.
 *
 * <p>{@code center} is {@code [xFromLeft, yFromBottom]} in sprite pixels. With
 * sprite size {@code (W, H)} and pixel centre {@code (W/2, H/2)}:
 * <pre>
 *   offset.x = (W/2 - cx) * METERS_PER_PX     // vanilla port (+Y → our +X)
 *   offset.y = (H/2 - cy) * METERS_PER_PX     // vanilla forward (+X → our +Y)
 * </pre>
 * i.e. the pixel centre relative to {@code center}, in our frame. Most hulls are
 * laterally centred ({@code cx == W/2}) so {@code offset.x == 0}; the forward
 * term is the real one (e.g. Valkyrie ~+0.85 cells, Kite ~+0.23).
 */
public final class HullPivotResolver {

    private static final Logger LOG = Global.getLogger(HullPivotResolver.class);
    private static final float[] ZERO = {0f, 0f};

    /** Key: hull id. Value: {@code {offsetX, offsetY}} pixel-centre-minus-center, in cells. */
    private static final Map<String, float[]> CACHE_BY_HULL = new HashMap<>();

    private HullPivotResolver() {}

    /**
     * Returns {@code {offsetX, offsetY}} — the sprite pixel centre relative to the
     * hull {@code center}, in our cell frame. Lazy-loaded; cached. Never null;
     * a null/empty id or any failure yields {@code {0, 0}} (i.e. fall back to
     * pixel-centre anchoring, the pre-CoG behaviour).
     */
    public static float[] pivotOffset(String hullId) {
        if (hullId == null || hullId.isEmpty()) return ZERO;
        float[] cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;
        float[] resolved = doResolve(hullId);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    private static float[] doResolve(String hullId) {
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            float w = (float) spec.optDouble("width", 0.0);
            float h = (float) spec.optDouble("height", 0.0);
            float[] c = WeaponSlotParser.parseCenter(spec);
            if (c == null || w <= 0f || h <= 0f) return ZERO;
            float k = AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
            return new float[]{ (w / 2f - c[0]) * k, (h / 2f - c[1]) * k };
        } catch (Exception e) {
            LOG.warn("HullPivotResolver: " + hullId + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ZERO;
        }
    }
}
