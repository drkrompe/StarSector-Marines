package com.dillon.starsectormarines.battle.turret;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The painted body of a multi-cell structure, authored on the structure rather than on its mount: a static base
 * drawn over the whole footprint, then any number of centre-pivoted overlay layers that may spin or pulse. A
 * structure that names none is drawn the way it always was, as a flat pad with its mount's sprite on top.
 *
 * <p>The base replaces the flat footprint pad and is never rotated. A rotating gun is not a layer here: it stays the
 * mount's own {@code sprite} / {@code recoilSprite} pair, which already turn and recoil with the structure's facing.
 * Layers are for what moves without aiming: an emitter ring turning, a core pulsing.
 */
public final class StructureArt {

    /**
     * One overlay layer. {@code cells} is the sprite's edge length in cells (the sprite is square and pivots on its
     * centre). {@code spinDegPerSec} turns it; {@code pulseDepth} (0 to 1) swings its brightness at {@code pulseHz}.
     * A {@code tintByHolder} layer is multiplied by the colour of the side that holds the structure's dome, so one
     * neutral sprite serves both sides. A {@code dome} layer follows the dome's state: it stops spinning and dims
     * while the dome is down, and flares when the dome takes a round.
     */
    public record Layer(String spritePath, float cells, float spinDegPerSec,
                        float pulseHz, float pulseDepth, boolean tintByHolder, boolean dome) {}

    /** Static base sprite path, or null to keep the flat footprint pad. */
    public final String basePath;
    private final List<Layer> layers;

    private StructureArt(String basePath, List<Layer> layers) {
        this.basePath = basePath;
        this.layers = Collections.unmodifiableList(layers);
    }

    public List<Layer> layers() {
        return layers;
    }

    static StructureArt parse(String structureId, JSONObject json) throws JSONException {
        String base = text(json, "base");
        List<Layer> layers = new ArrayList<>();
        JSONArray array = json.optJSONArray("layers");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject l = array.getJSONObject(i);
            String sprite = TurretMountDef.requireText(l, "sprite");
            float cells = (float) l.getDouble("cells");
            float spin = (float) l.optDouble("spinDegPerSec", 0.0);
            float pulseHz = (float) l.optDouble("pulseHz", 0.0);
            float pulseDepth = (float) l.optDouble("pulseDepth", 0.0);
            TurretMountDef.requirePositiveFinite(cells, structureId, "art layer cells");
            if (!Float.isFinite(spin) || !Float.isFinite(pulseHz) || pulseHz < 0f
                    || !Float.isFinite(pulseDepth) || pulseDepth < 0f || pulseDepth > 1f) {
                throw new JSONException("Structure '" + structureId + "' art layer " + i
                        + " has a non-finite or out-of-range spin or pulse");
            }
            layers.add(new Layer(sprite, cells, spin, pulseHz, pulseDepth,
                    l.optBoolean("tintByHolder", false), l.optBoolean("dome", false)));
        }
        if (base == null && layers.isEmpty()) {
            throw new JSONException("Structure '" + structureId + "' declares an art block with no base and no layers");
        }
        return new StructureArt(base, layers);
    }

    private static String text(JSONObject json, String key) {
        String value = json.optString(key, null);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
    }
}
