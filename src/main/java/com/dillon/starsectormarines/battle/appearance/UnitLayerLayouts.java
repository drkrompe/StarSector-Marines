package com.dillon.starsectormarines.battle.appearance;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime authority for phase-driven modular unit layer clips. */
public final class UnitLayerLayouts {

    public static final String CONTENT_PATH =
            "data/appearance/unit-layer-layouts.appearance.json";
    public static final int SCHEMA_VERSION = 3;

    private static UnitLayerLayouts active = new UnitLayerLayouts(Map.of());
    private static final String ARMOR_MASTER_VARIANT = "field-loadout";
    private static final String ARMOR_MASTER_ANIMATION = "idle";
    private static final float MASTER_BODY_OFFSET_X = 0f;
    private static final float MASTER_BODY_OFFSET_Y = -0.12f;
    private static final float MASTER_HEAD_OFFSET_X = 0f;
    private static final float MASTER_HEAD_OFFSET_Y = 0.08f;

    private final Map<String, UnitLayout> units;

    private UnitLayerLayouts(Map<String, UnitLayout> units) {
        this.units = units;
    }

    public static void loadBuiltins() throws IOException, JSONException {
        active = parse(Global.getSettings().loadJSON(CONTENT_PATH, true));
    }

    public static UnitLayerLayouts get() {
        return active;
    }

    public static UnitLayerLayouts parse(JSONObject root) throws JSONException {
        int version = root.getInt("schemaVersion");
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported unit-layer schema version: "
                    + version);
        }
        Map<String, UnitLayout> units = new LinkedHashMap<>();
        JSONArray array = root.getJSONArray("units");
        for (int index = 0; index < array.length(); index++) {
            UnitLayout unit = UnitLayout.parse(array.getJSONObject(index));
            if (units.put(unit.id(), unit) != null) {
                throw new IllegalArgumentException("Duplicate unit layout: " + unit.id());
            }
        }
        return new UnitLayerLayouts(Collections.unmodifiableMap(units));
    }

    public AnimationClip clip(String unitId, String variantId, String animationId) {
        UnitLayout unit = units.get(unitId);
        if (unit == null) return null;
        VariantLayout variant = unit.variants().get(variantId);
        return variant != null ? variant.animations().get(animationId) : null;
    }

    /**
     * Applies workbench-authored registration for independently selected armor
     * body and helmet families without replacing the active movement/action pose.
     * Master scale is multiplicative; offset and angle are deltas from the
     * neutral marine pose shown by the workbench.
     */
    public LayerPose applyArmorMastering(LayerPose pose,
                                         LayeredArmorFamily bodyFamily,
                                         LayeredArmorFamily headFamily) {
        if (pose == null) return null;
        LayerTransform body = armorMaster(bodyFamily, "body");
        LayerTransform head = armorMaster(headFamily, "head");
        if (body == null && head == null) return pose;
        return pose.withArmorMastering(body, head);
    }

    private LayerTransform armorMaster(LayeredArmorFamily family, String layerId) {
        String unitId = armorMasterUnitId(family);
        if (unitId == null) return null;
        AnimationClip clip = clip(unitId, ARMOR_MASTER_VARIANT, ARMOR_MASTER_ANIMATION);
        return clip != null ? clip.frames().get(0).pose().layer(layerId) : null;
    }

    private static String armorMasterUnitId(LayeredArmorFamily family) {
        if (family == null) return null;
        return switch (family) {
            case AEGIS_COMPOSITE -> "armor-master-aegis";
            case PALATINE -> "armor-master-palatine";
            case FURNACE_LINE -> "armor-master-furnace-line";
            case REAVER -> "armor-master-reaver";
            case SPECTER_HEAVY -> "armor-master-specter-heavy";
            case BULWARK_HEAVY -> "armor-master-bulwark-heavy";
            case RELIQUARY_HEAVY -> "armor-master-reliquary-heavy";
            case LIONS_MANTLE -> "armor-master-lions-mantle";
            case FOUNDRY_BREAKER -> "armor-master-foundry-breaker";
            default -> null;
        };
    }

    public enum AnimationDriver {
        TIME("time"),
        LOCOMOTION_PHASE("locomotionPhase"),
        ACTION_PHASE("actionPhase");

        private final String jsonName;

        AnimationDriver(String jsonName) {
            this.jsonName = jsonName;
        }

        public String jsonName() {
            return jsonName;
        }

        static AnimationDriver parse(String value) {
            for (AnimationDriver driver : values()) {
                if (driver.jsonName.equals(value)) return driver;
            }
            throw new IllegalArgumentException("Unknown animation driver: " + value);
        }
    }

    public record UnitLayout(String id, double referencePixels,
                             Map<String, VariantLayout> variants) {
        static UnitLayout parse(JSONObject json) throws JSONException {
            Map<String, VariantLayout> variants = new LinkedHashMap<>();
            JSONArray array = json.getJSONArray("variants");
            for (int index = 0; index < array.length(); index++) {
                VariantLayout variant = VariantLayout.parse(array.getJSONObject(index));
                if (variants.put(variant.id(), variant) != null) {
                    throw new IllegalArgumentException("Duplicate appearance variant: "
                            + json.getString("id") + "/" + variant.id());
                }
            }
            return new UnitLayout(json.getString("id"), json.getDouble("referencePixels"),
                    Collections.unmodifiableMap(variants));
        }
    }

    public record VariantLayout(String id, Map<String, AnimationClip> animations) {
        static VariantLayout parse(JSONObject json) throws JSONException {
            Map<String, AnimationClip> animations = new LinkedHashMap<>();
            JSONArray array = json.getJSONArray("animations");
            for (int index = 0; index < array.length(); index++) {
                AnimationClip clip = AnimationClip.parse(array.getJSONObject(index));
                if (animations.put(clip.id(), clip) != null) {
                    throw new IllegalArgumentException("Duplicate animation clip: "
                            + json.getString("id") + "/" + clip.id());
                }
            }
            return new VariantLayout(json.getString("id"),
                    Collections.unmodifiableMap(animations));
        }
    }

    public record AnimationClip(String id, AnimationDriver driver, boolean loop,
                                List<Keyframe> frames, int totalDurationMs) {
        static AnimationClip parse(JSONObject json) throws JSONException {
            List<Keyframe> frames = new ArrayList<>();
            int duration = 0;
            JSONArray array = json.getJSONArray("frames");
            for (int index = 0; index < array.length(); index++) {
                Keyframe frame = Keyframe.parse(array.getJSONObject(index));
                frames.add(frame);
                duration += frame.durationMs();
            }
            if (frames.isEmpty() || duration <= 0) {
                throw new IllegalArgumentException("Animation must have positive keyframes: "
                        + json.getString("id"));
            }
            return new AnimationClip(json.getString("id"),
                    AnimationDriver.parse(json.getString("driver")),
                    json.optBoolean("loop", false), List.copyOf(frames), duration);
        }

        /** Samples a normalized procedural phase; looping clips wrap at one. */
        public LayerPose sample(float phase) {
            if (frames.size() == 1) return frames.get(0).pose();
            float normalized = loop ? phase - (float) Math.floor(phase)
                    : Math.max(0f, Math.min(1f, phase));
            if (!loop && normalized >= 1f) return frames.get(frames.size() - 1).pose();
            float timeMs = normalized * totalDurationMs;
            int frameIndex = 0;
            int elapsedMs = 0;
            while (frameIndex + 1 < frames.size()
                    && timeMs >= elapsedMs + frames.get(frameIndex).durationMs()) {
                elapsedMs += frames.get(frameIndex).durationMs();
                frameIndex++;
            }
            Keyframe current = frames.get(frameIndex);
            int nextIndex = frameIndex + 1;
            if (nextIndex >= frames.size()) {
                if (!loop) return current.pose();
                nextIndex = 0;
            }
            float progress = (timeMs - elapsedMs) / current.durationMs();
            return LayerPose.interpolate(current.pose(), frames.get(nextIndex).pose(),
                    smoothstep(Math.max(0f, Math.min(1f, progress))));
        }
    }

    public record Keyframe(String id, int durationMs, LayerPose pose) {
        static Keyframe parse(JSONObject json) throws JSONException {
            int durationMs = json.getInt("durationMs");
            if (durationMs <= 0) {
                throw new IllegalArgumentException("Keyframe duration must be positive: "
                        + json.getString("id"));
            }
            Map<String, LayerTransform> layers = new LinkedHashMap<>();
            JSONArray array = json.getJSONArray("layers");
            for (int index = 0; index < array.length(); index++) {
                LayerTransform layer = LayerTransform.parse(array.getJSONObject(index));
                if (layers.put(layer.id(), layer) != null) {
                    throw new IllegalArgumentException("Duplicate layer: " + layer.id());
                }
            }
            return new Keyframe(json.getString("id"), durationMs,
                    new LayerPose(Collections.unmodifiableMap(layers)));
        }
    }

    public record LayerPose(Map<String, LayerTransform> layers) {
        public LayerTransform layer(String id) {
            return layers.get(id);
        }

        /**
         * Returns this pose with the named layers replaced by their transforms
         * from {@code source}. Missing source layers leave the base pose alone.
         * Used when two independent presentation drivers own disjoint parts of
         * one actor, such as locomotion feet under a primary-weapon upper-body
         * action.
         */
        public LayerPose withLayersFrom(LayerPose source, String... layerIds) {
            Map<String, LayerTransform> combined = new LinkedHashMap<>(layers);
            for (String layerId : layerIds) {
                LayerTransform replacement = source.layers.get(layerId);
                if (replacement != null) combined.put(layerId, replacement);
            }
            return new LayerPose(Collections.unmodifiableMap(combined));
        }

        private LayerPose withArmorMastering(LayerTransform bodyMaster,
                                             LayerTransform headMaster) {
            Map<String, LayerTransform> combined = new LinkedHashMap<>(layers);
            applyMaster(combined, "body", bodyMaster,
                    MASTER_BODY_OFFSET_X, MASTER_BODY_OFFSET_Y);
            applyMaster(combined, "head", headMaster,
                    MASTER_HEAD_OFFSET_X, MASTER_HEAD_OFFSET_Y);
            return new LayerPose(Collections.unmodifiableMap(combined));
        }

        private static void applyMaster(Map<String, LayerTransform> target,
                                        String layerId, LayerTransform master,
                                        float neutralOffsetX, float neutralOffsetY) {
            LayerTransform animated = target.get(layerId);
            if (animated == null || master == null) return;
            target.put(layerId, new LayerTransform(animated.id, animated.spritePath,
                    animated.offsetX + master.offsetX - neutralOffsetX,
                    animated.offsetY + master.offsetY - neutralOffsetY,
                    animated.scaleX * master.scaleX,
                    animated.scaleY * master.scaleY,
                    animated.angleDegrees + master.angleDegrees,
                    master.pivotX, master.pivotY,
                    animated.z, animated.visible && master.visible));
        }

        /**
         * Blends layers shared by both poses while retaining the destination's
         * equipment-specific layer set. This lets an action settle out of a
         * sampled locomotion pose without inventing equipment layers in the
         * locomotion clip.
         */
        public static LayerPose blendMatching(LayerPose from, LayerPose to,
                                              float progress) {
            float t = Math.max(0f, Math.min(1f, progress));
            t = smoothstep(t);
            Map<String, LayerTransform> sampled = new LinkedHashMap<>();
            for (Map.Entry<String, LayerTransform> entry : to.layers.entrySet()) {
                LayerTransform end = entry.getValue();
                LayerTransform start = from.layers.get(entry.getKey());
                sampled.put(entry.getKey(), start != null
                                && start.spritePath.equals(end.spritePath)
                        ? LayerTransform.interpolate(start, end, t) : end);
            }
            return new LayerPose(Collections.unmodifiableMap(sampled));
        }

        static LayerPose interpolate(LayerPose from, LayerPose to, float progress) {
            Map<String, LayerTransform> sampled = new LinkedHashMap<>();
            for (Map.Entry<String, LayerTransform> entry : from.layers.entrySet()) {
                LayerTransform start = entry.getValue();
                LayerTransform end = to.layers.get(entry.getKey());
                sampled.put(entry.getKey(), end != null && start.spritePath.equals(end.spritePath)
                        ? LayerTransform.interpolate(start, end, progress) : start);
            }
            return new LayerPose(Collections.unmodifiableMap(sampled));
        }
    }

    public record LayerTransform(String id, String spritePath,
                                 float offsetX, float offsetY,
                                 float scaleX, float scaleY,
                                 float angleDegrees, float pivotX, float pivotY,
                                 int z, boolean visible) {
        static LayerTransform parse(JSONObject json) throws JSONException {
            JSONArray offset = json.getJSONArray("offset");
            JSONArray scale = json.getJSONArray("scale");
            JSONArray pivot = json.getJSONArray("pivot");
            return new LayerTransform(json.getString("id"), json.getString("sprite"),
                    (float) offset.getDouble(0), (float) offset.getDouble(1),
                    (float) scale.getDouble(0), (float) scale.getDouble(1),
                    (float) json.optDouble("angleDegrees", 0.0),
                    (float) pivot.getDouble(0), (float) pivot.getDouble(1),
                    json.optInt("z", 0), json.optBoolean("visible", true));
        }

        static LayerTransform interpolate(LayerTransform from, LayerTransform to,
                                          float progress) {
            float angleDelta = LayeredAppearance.wrapDegrees(
                    to.angleDegrees - from.angleDegrees);
            return new LayerTransform(from.id, from.spritePath,
                    lerp(from.offsetX, to.offsetX, progress),
                    lerp(from.offsetY, to.offsetY, progress),
                    lerp(from.scaleX, to.scaleX, progress),
                    lerp(from.scaleY, to.scaleY, progress),
                    from.angleDegrees + angleDelta * progress,
                    lerp(from.pivotX, to.pivotX, progress),
                    lerp(from.pivotY, to.pivotY, progress),
                    progress < 0.5f ? from.z : to.z,
                    progress < 0.5f ? from.visible : to.visible);
        }
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float smoothstep(float progress) {
        return progress * progress * (3f - 2f * progress);
    }
}
