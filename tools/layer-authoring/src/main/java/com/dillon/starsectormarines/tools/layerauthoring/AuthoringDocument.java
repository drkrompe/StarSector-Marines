package com.dillon.starsectormarines.tools.layerauthoring;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Mutable, validated authoring view of the unit-layer layout JSON authority. */
public final class AuthoringDocument {

    public static final Path RELATIVE_PATH =
            Path.of("mod/data/appearance/unit-layer-layouts.appearance.json");

    private final Path projectRoot;
    private final Path sourcePath;
    private final List<UnitComposition> units;

    private AuthoringDocument(Path projectRoot, Path sourcePath,
                              List<UnitComposition> units) {
        this.projectRoot = projectRoot;
        this.sourcePath = sourcePath;
        this.units = units;
    }

    public static AuthoringDocument load(Path projectRoot) throws IOException, JSONException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve(RELATIVE_PATH).normalize();
        return parse(normalizedRoot, Files.readString(source, StandardCharsets.UTF_8));
    }

    static AuthoringDocument parse(Path projectRoot, String sourceJson) throws JSONException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve(RELATIVE_PATH).normalize();
        JSONObject root = new JSONObject(sourceJson);
        if (root.getInt("schemaVersion") != 2) {
            throw new IllegalArgumentException("Unsupported unit-layer schema version");
        }
        JSONArray unitArray = root.getJSONArray("units");
        List<UnitComposition> units = new ArrayList<>();
        for (int index = 0; index < unitArray.length(); index++) {
            units.add(UnitComposition.parse(unitArray.getJSONObject(index)));
        }
        AuthoringDocument document = new AuthoringDocument(normalizedRoot, source, units);
        List<String> errors = document.validate();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
        }
        return document;
    }

    String snapshot() throws JSONException {
        return toJson().toString();
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public List<UnitComposition> units() {
        return units;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        Set<String> unitIds = new HashSet<>();
        Path modRoot = projectRoot.resolve("mod").normalize();
        for (UnitComposition unit : units) {
            if (!unitIds.add(unit.id())) errors.add("Duplicate unit id: " + unit.id());
            if (unit.referencePixels() <= 0.0) {
                errors.add(unit.id() + " referencePixels must be positive");
            }
            Set<String> variantIds = new HashSet<>();
            for (AppearanceVariant variant : unit.variants()) {
                if (!variantIds.add(variant.id())) {
                    errors.add(unit.id() + " has duplicate variant id: " + variant.id());
                }
                Set<String> animationIds = new HashSet<>();
                for (AnimationDefinition animation : variant.animations()) {
                    String animationPrefix = unit.id() + "/" + variant.id()
                            + "/" + animation.id();
                    if (!animationIds.add(animation.id())) {
                        errors.add(unit.id() + "/" + variant.id()
                                + " has duplicate animation id: " + animation.id());
                    }
                    if (animation.frames().isEmpty()) {
                        errors.add(animationPrefix + " must contain at least one keyframe");
                    }
                    Set<String> frameIds = new HashSet<>();
                    for (FrameDefinition frame : animation.frames()) {
                        if (!frameIds.add(frame.id())) {
                            errors.add(animationPrefix + " has duplicate keyframe id: "
                                    + frame.id());
                        }
                        if (frame.durationMs() <= 0) {
                            errors.add(animationPrefix + "/" + frame.id()
                                    + " durationMs must be positive");
                        }
                        Set<String> layerIds = new HashSet<>();
                        for (LayerDefinition layer : frame.layers()) {
                            String prefix = animationPrefix + "/" + frame.id()
                                    + "/" + layer.id();
                            if (!layerIds.add(layer.id())) {
                                errors.add(animationPrefix + "/" + frame.id()
                                        + " has duplicate layer id: " + layer.id());
                            }
                            if (layer.scaleX() <= 0.0 || layer.scaleY() <= 0.0) {
                                errors.add(prefix + " scale values must be positive");
                            }
                            if (!unitInterval(layer.pivotX()) || !unitInterval(layer.pivotY())) {
                                errors.add(prefix + " pivot values must be between 0 and 1");
                            }
                            Path sprite = modRoot.resolve(layer.spritePath()).normalize();
                            if (!sprite.startsWith(modRoot)) {
                                errors.add(prefix + " sprite escapes the mod directory");
                            } else if (!Files.isRegularFile(sprite)) {
                                errors.add(prefix + " sprite does not exist: "
                                        + layer.spritePath());
                            }
                        }
                    }
                }
            }
        }
        return errors;
    }

    public void save() throws IOException, JSONException {
        List<String> errors = validate();
        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));

        JSONObject root = toJson();

        Files.createDirectories(sourcePath.getParent());
        Path temporary = sourcePath.resolveSibling(sourcePath.getFileName()
                + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(temporary, root.toString(2) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        try {
            Files.move(temporary, sourcePath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, sourcePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", 2);
        root.put("coordinateSystem", new JSONObject()
                .put("origin", "actor-center")
                .put("x", "right")
                .put("y", "forward")
                .put("angleDegrees", "counter-clockwise")
                .put("pivot", "normalized-from-top-left"));
        JSONArray unitArray = new JSONArray();
        for (UnitComposition unit : units) unitArray.put(unit.toJson());
        root.put("units", unitArray);
        return root;
    }

    private static boolean unitInterval(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    public static final class UnitComposition {
        private final String id;
        private final String label;
        private final double referencePixels;
        private final List<AppearanceVariant> variants;

        private UnitComposition(String id, String label, double referencePixels,
                                List<AppearanceVariant> variants) {
            this.id = id;
            this.label = label;
            this.referencePixels = referencePixels;
            this.variants = variants;
        }

        static UnitComposition parse(JSONObject json) throws JSONException {
            JSONArray variantArray = json.getJSONArray("variants");
            List<AppearanceVariant> variants = new ArrayList<>();
            for (int index = 0; index < variantArray.length(); index++) {
                variants.add(AppearanceVariant.parse(variantArray.getJSONObject(index)));
            }
            return new UnitComposition(json.getString("id"), json.getString("label"),
                    json.getDouble("referencePixels"), variants);
        }

        JSONObject toJson() throws JSONException {
            JSONArray variantArray = new JSONArray();
            for (AppearanceVariant variant : variants) variantArray.put(variant.toJson());
            return new JSONObject().put("id", id).put("label", label)
                    .put("referencePixels", referencePixels).put("variants", variantArray);
        }

        public String id() { return id; }
        public String label() { return label; }
        public double referencePixels() { return referencePixels; }
        public List<AppearanceVariant> variants() { return variants; }
        @Override public String toString() { return label; }
    }

    public static final class AppearanceVariant {
        private final String id;
        private final String label;
        private final List<AnimationDefinition> animations;

        private AppearanceVariant(String id, String label,
                                  List<AnimationDefinition> animations) {
            this.id = id;
            this.label = label;
            this.animations = animations;
        }

        static AppearanceVariant parse(JSONObject json) throws JSONException {
            JSONArray animationArray = json.getJSONArray("animations");
            List<AnimationDefinition> animations = new ArrayList<>();
            for (int index = 0; index < animationArray.length(); index++) {
                animations.add(AnimationDefinition.parse(animationArray.getJSONObject(index)));
            }
            return new AppearanceVariant(json.getString("id"), json.getString("label"),
                    animations);
        }

        JSONObject toJson() throws JSONException {
            JSONArray animationArray = new JSONArray();
            for (AnimationDefinition animation : animations) {
                animationArray.put(animation.toJson());
            }
            return new JSONObject().put("id", id).put("label", label)
                    .put("animations", animationArray);
        }

        public String id() { return id; }
        public String label() { return label; }
        public List<AnimationDefinition> animations() { return animations; }
        @Override public String toString() { return label; }
    }

    public static final class AnimationDefinition {
        private final String id;
        private final String label;
        private boolean loop;
        private final List<FrameDefinition> frames;

        private AnimationDefinition(String id, String label, boolean loop,
                                    List<FrameDefinition> frames) {
            this.id = id;
            this.label = label;
            this.loop = loop;
            this.frames = frames;
        }

        static AnimationDefinition parse(JSONObject json) throws JSONException {
            JSONArray frameArray = json.getJSONArray("frames");
            List<FrameDefinition> frames = new ArrayList<>();
            for (int index = 0; index < frameArray.length(); index++) {
                frames.add(FrameDefinition.parse(frameArray.getJSONObject(index)));
            }
            return new AnimationDefinition(json.getString("id"), json.getString("label"),
                    json.optBoolean("loop", false), frames);
        }

        JSONObject toJson() throws JSONException {
            JSONArray frameArray = new JSONArray();
            for (FrameDefinition frame : frames) frameArray.put(frame.toJson());
            return new JSONObject().put("id", id).put("label", label)
                    .put("loop", loop).put("frames", frameArray);
        }

        public String id() { return id; }
        public String label() { return label; }
        public boolean loop() { return loop; }
        public void loop(boolean value) { loop = value; }
        public List<FrameDefinition> frames() { return frames; }
        public AnimationDefinition copy(String newId, String newLabel) {
            List<FrameDefinition> copies = new ArrayList<>();
            for (FrameDefinition frame : frames) {
                copies.add(frame.copy(frame.id(), frame.label()));
            }
            return new AnimationDefinition(newId, newLabel, loop, copies);
        }
        @Override public String toString() { return label; }
    }

    public static final class FrameDefinition {
        private final String id;
        private final String label;
        private int durationMs;
        private final List<LayerDefinition> layers;

        private FrameDefinition(String id, String label, int durationMs,
                                List<LayerDefinition> layers) {
            this.id = id;
            this.label = label;
            this.durationMs = durationMs;
            this.layers = layers;
        }

        static FrameDefinition parse(JSONObject json) throws JSONException {
            JSONArray layerArray = json.getJSONArray("layers");
            List<LayerDefinition> layers = new ArrayList<>();
            for (int index = 0; index < layerArray.length(); index++) {
                layers.add(LayerDefinition.parse(layerArray.getJSONObject(index)));
            }
            layers.sort(Comparator.comparingInt(LayerDefinition::z));
            return new FrameDefinition(json.getString("id"), json.getString("label"),
                    json.getInt("durationMs"), layers);
        }

        JSONObject toJson() throws JSONException {
            layers.sort(Comparator.comparingInt(LayerDefinition::z));
            JSONArray layerArray = new JSONArray();
            for (LayerDefinition layer : layers) layerArray.put(layer.toJson());
            return new JSONObject().put("id", id).put("label", label)
                    .put("durationMs", durationMs).put("layers", layerArray);
        }

        public String id() { return id; }
        public String label() { return label; }
        public int durationMs() { return durationMs; }
        public void durationMs(int value) { durationMs = value; }
        public List<LayerDefinition> layers() { return layers; }
        public FrameDefinition copy(String newId, String newLabel) {
            List<LayerDefinition> copies = new ArrayList<>();
            for (LayerDefinition layer : layers) copies.add(layer.copy());
            return new FrameDefinition(newId, newLabel, durationMs, copies);
        }
        static FrameDefinition preview(FrameDefinition source,
                                       List<LayerDefinition> layers) {
            return new FrameDefinition(source.id, source.label, source.durationMs, layers);
        }
        @Override public String toString() { return label; }
    }

    public static final class LayerDefinition {
        private final String id;
        private String spritePath;
        private double offsetX;
        private double offsetY;
        private double scaleX;
        private double scaleY;
        private double angleDegrees;
        private double pivotX;
        private double pivotY;
        private int z;
        private boolean visible;

        private LayerDefinition(String id, String spritePath, double offsetX,
                                double offsetY, double scaleX, double scaleY,
                                double angleDegrees, double pivotX, double pivotY,
                                int z, boolean visible) {
            this.id = id;
            this.spritePath = spritePath;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.angleDegrees = angleDegrees;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.z = z;
            this.visible = visible;
        }

        static LayerDefinition parse(JSONObject json) throws JSONException {
            JSONArray offset = json.getJSONArray("offset");
            JSONArray scale = json.getJSONArray("scale");
            JSONArray pivot = json.getJSONArray("pivot");
            return new LayerDefinition(json.getString("id"), json.getString("sprite"),
                    offset.getDouble(0), offset.getDouble(1),
                    scale.getDouble(0), scale.getDouble(1),
                    json.optDouble("angleDegrees", 0.0),
                    pivot.getDouble(0), pivot.getDouble(1),
                    json.optInt("z", 0), json.optBoolean("visible", true));
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("sprite", spritePath)
                    .put("offset", new JSONArray().put(offsetX).put(offsetY))
                    .put("scale", new JSONArray().put(scaleX).put(scaleY))
                    .put("angleDegrees", angleDegrees)
                    .put("pivot", new JSONArray().put(pivotX).put(pivotY))
                    .put("z", z).put("visible", visible);
        }

        public String id() { return id; }
        public String spritePath() { return spritePath; }
        public void spritePath(String value) { spritePath = value; }
        public double offsetX() { return offsetX; }
        public double offsetY() { return offsetY; }
        public void offset(double x, double y) { offsetX = x; offsetY = y; }
        public double scaleX() { return scaleX; }
        public double scaleY() { return scaleY; }
        public void scale(double x, double y) { scaleX = x; scaleY = y; }
        public double angleDegrees() { return angleDegrees; }
        public void angleDegrees(double value) { angleDegrees = value; }
        public double pivotX() { return pivotX; }
        public double pivotY() { return pivotY; }
        public void pivot(double x, double y) { pivotX = x; pivotY = y; }
        public int z() { return z; }
        public void z(int value) { z = value; }
        public boolean visible() { return visible; }
        public void visible(boolean value) { visible = value; }
        LayerDefinition copy() {
            return new LayerDefinition(id, spritePath, offsetX, offsetY,
                    scaleX, scaleY, angleDegrees, pivotX, pivotY, z, visible);
        }
        @Override public String toString() { return id; }
    }
}
