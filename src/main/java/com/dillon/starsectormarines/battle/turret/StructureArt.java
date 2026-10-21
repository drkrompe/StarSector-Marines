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
 *
 * <p>{@code groundPad: false} drops the flat footprint pad under a structure that has no base: a body that is grown
 * rather than built (a swarm bore, a spore colony) stands on the ground itself, not on a square of hardstanding.
 *
 * <p>{@code variants: N} gives a grown body several paintings so a map of them does not look stamped. A layer path
 * containing {@value #VARIANT_TOKEN} is resolved per structure to one of {@code 1..N}, the same number for every layer
 * of that structure (a body and its glow stay a pair), chosen from the structure's entity id. The lighting is baked
 * into each painting, so a variant is never rotated or mirrored to make another.
 *
 * <p>A grown body shows its damage. {@code woundedBelow} (0 to 1) is the share of its durability (structure plus
 * armour) below which it is drawn <b>wounded</b>: a layer path containing {@value #STATE_TOKEN} then resolves to the
 * path with {@value #WOUNDED_SUFFIX} in its place, and to nothing in it otherwise, so a body and its glow change
 * painting together; a layer without the token looks the same either way. {@code remains} names the painting left on
 * the ground once the structure dies, drawn {@code remainsCells} across (the widest layer when not given), under the
 * living; it may carry {@value #VARIANT_TOKEN}, so the remains are of the same painting.
 */
public final class StructureArt {

    /**
     * One overlay layer. {@code cells} is the sprite's edge length in cells (the sprite is square and pivots on its
     * centre). {@code spinDegPerSec} turns it; {@code pulseDepth} (0 to 1) swings its brightness at {@code pulseHz}.
     * A {@code tintByHolder} layer is multiplied by the colour of the side that holds the structure's dome, so one
     * neutral sprite serves both sides. A {@code dome} layer follows the dome's state: it stops spinning and dims
     * while the dome is down, and flares when the dome takes a round. {@code fireFlash} (0 to 1) makes the layer
     * answer the structure's own fire: it snaps to full brightness and swells by up to a quarter of its size at that
     * strength, settling over {@link #FIRE_FLASH_SECONDS} (a spore sac flaring as it spits a burst).
     */
    public record Layer(String spritePath, float cells, float spinDegPerSec,
                        float pulseHz, float pulseDepth, boolean tintByHolder, boolean dome, float fireFlash) {

        /** This layer's sprite for one painting, {@code variant} counted from 1, unhurt. */
        public String spritePath(int variant) {
            return spritePath(variant, false);
        }

        /** This layer's sprite for one painting, {@code variant} counted from 1, wounded or not. */
        public String spritePath(int variant, boolean wounded) {
            return spritePath.replace(VARIANT_TOKEN, Integer.toString(variant))
                    .replace(STATE_TOKEN, wounded ? WOUNDED_SUFFIX : "");
        }
    }

    /** How long a {@link Layer#fireFlash} layer takes to settle after a shot. */
    public static final float FIRE_FLASH_SECONDS = 0.4f;
    /** How much a {@code fireFlash: 1} layer swells at the moment of the shot. */
    public static final float FIRE_FLASH_SWELL = 0.25f;

    /** Placeholder in a layer path that a variant number replaces. */
    public static final String VARIANT_TOKEN = "{variant}";
    /** Placeholder in a layer path that the damage state replaces: nothing, or {@link #WOUNDED_SUFFIX}. */
    public static final String STATE_TOKEN = "{state}";
    /** What {@link #STATE_TOKEN} becomes while the structure is wounded. */
    public static final String WOUNDED_SUFFIX = "-wounded";
    /** The most paintings one structure may carry. */
    static final int MAX_VARIANTS = 9;

    /** Static base sprite path, or null to keep the flat footprint pad. */
    public final String basePath;
    /** Whether a structure without a base still stands on the flat footprint pad. */
    public final boolean groundPad;
    /** How many paintings the layers carry; 1 for an ordinary structure. */
    public final int variants;
    /** Durability share below which the body is drawn wounded; 0 when it never is. */
    public final float woundedBelow;
    /** The painting left on the ground once the structure dies, or null when it leaves none. */
    public final String remainsPath;
    /** Edge of the remains painting, in cells. */
    public final float remainsCells;
    private final List<Layer> layers;

    private StructureArt(String basePath, boolean groundPad, int variants, float woundedBelow,
                         String remainsPath, float remainsCells, List<Layer> layers) {
        this.basePath = basePath;
        this.groundPad = groundPad;
        this.variants = variants;
        this.woundedBelow = woundedBelow;
        this.remainsPath = remainsPath;
        this.remainsCells = remainsCells;
        this.layers = Collections.unmodifiableList(layers);
    }

    /** Whether a structure with {@code durability} (0 to 1) of its structure and armour left is drawn wounded. */
    public boolean woundedAt(float durability) {
        return durability < woundedBelow;
    }

    /** The remains painting for one painting, {@code variant} counted from 1, or null when it leaves none. */
    public String remainsPath(int variant) {
        return remainsPath == null ? null : remainsPath.replace(VARIANT_TOKEN, Integer.toString(variant));
    }

    /** The painting, from 1 to {@link #variants}, that the structure with this entity id wears. */
    public int variantFor(long entityId) {
        if (variants <= 1) return 1;
        long z = entityId + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return 1 + (int) Math.floorMod(z, (long) variants);
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
            float fireFlash = (float) l.optDouble("fireFlash", 0.0);
            TurretMountDef.requirePositiveFinite(cells, structureId, "art layer cells");
            if (!Float.isFinite(spin) || !Float.isFinite(pulseHz) || pulseHz < 0f
                    || !Float.isFinite(pulseDepth) || pulseDepth < 0f || pulseDepth > 1f
                    || !Float.isFinite(fireFlash) || fireFlash < 0f || fireFlash > 1f) {
                throw new JSONException("Structure '" + structureId + "' art layer " + i
                        + " has a non-finite or out-of-range spin, pulse or fire flash");
            }
            layers.add(new Layer(sprite, cells, spin, pulseHz, pulseDepth,
                    l.optBoolean("tintByHolder", false), l.optBoolean("dome", false), fireFlash));
        }
        int variants = json.optInt("variants", 1);
        if (variants < 1 || variants > MAX_VARIANTS) {
            throw new JSONException("Structure '" + structureId + "' art declares " + variants
                    + " variants; 1 to " + MAX_VARIANTS + " are allowed");
        }
        if (base == null && layers.isEmpty()) {
            throw new JSONException("Structure '" + structureId + "' declares an art block with no base and no layers");
        }
        float woundedBelow = (float) json.optDouble("woundedBelow", 0.0);
        if (!Float.isFinite(woundedBelow) || woundedBelow < 0f || woundedBelow >= 1f) {
            throw new JSONException("Structure '" + structureId + "' art woundedBelow must be at least 0 and under 1");
        }
        float widest = 0f;
        for (Layer layer : layers) widest = Math.max(widest, layer.cells());
        String remains = text(json, "remains");
        float remainsCells = (float) json.optDouble("remainsCells", widest);
        if (remains != null) TurretMountDef.requirePositiveFinite(remainsCells, structureId, "art remainsCells");
        return new StructureArt(base, json.optBoolean("groundPad", true), variants, woundedBelow, remains,
                remainsCells, layers);
    }

    private static String text(JSONObject json, String key) {
        String value = json.optString(key, null);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
    }
}
