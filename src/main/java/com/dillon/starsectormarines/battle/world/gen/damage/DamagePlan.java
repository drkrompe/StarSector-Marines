package com.dillon.starsectormarines.battle.world.gen.damage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Non-destructive authoring intent. Rebuild the base map to remove or disable a layer. */
public record DamagePlan(int width, int height, long seed, List<Layer> layers) {
    public enum Mode { ERASE, SCAR, BATTERED, RUIN;
        @Override public String toString() { return switch (this) {
            case ERASE -> "Erase from layer"; case SCAR -> "Scorch / scar ground";
            case BATTERED -> "Damage structures and equipment"; case RUIN -> "Collapse / wreck";
        }; }
    }
    public record Point(double x, double y) {
        public Point { if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Finite brush points required"); }
    }
    public record Stroke(Mode mode, double radius, List<Point> points) {
        public Stroke {
            points = List.copyOf(points);
            if (mode == null || !Double.isFinite(radius) || radius < .5 || radius > 32 || points.isEmpty() || points.size() > 4096)
                throw new IllegalArgumentException("Brush radius 0.5–32; 1–4096 points required");
        }
    }
    public record Layer(String name, boolean enabled, List<Stroke> strokes) {
        public Layer {
            strokes = List.copyOf(strokes);
            if (name == null || name.isBlank() || name.length() > 80 || strokes.size() > 512)
                throw new IllegalArgumentException("Named layer required; at most 512 strokes");
        }
        public Layer append(Stroke stroke) {
            List<Stroke> next = new ArrayList<>(strokes); next.add(stroke); return new Layer(name, enabled, next);
        }
    }
    public DamagePlan {
        layers = List.copyOf(layers);
        if (width < 8 || height < 8 || width > 1024 || height > 1024 || layers.size() > 16)
            throw new IllegalArgumentException("Map extent 8–1024; at most 16 layers");
        var names = new HashSet<String>(); int count = 0;
        for (Layer layer : layers) {
            if (!names.add(layer.name())) throw new IllegalArgumentException("Duplicate layer name: " + layer.name());
            for (Stroke stroke : layer.strokes()) for (Point point : stroke.points()) {
                if (point.x() < 0 || point.y() < 0 || point.x() > width - 1 || point.y() > height - 1)
                    throw new IllegalArgumentException("Brush point outside map");
                if (++count > 32768) throw new IllegalArgumentException("Too many brush points");
            }
        }
    }
    public DamagePlan replace(int index, Layer layer) {
        List<Layer> next = new ArrayList<>(layers); next.set(index, layer); return new DamagePlan(width, height, seed, next);
    }
    public static DamagePlan read(JSONObject doc) throws JSONException {
        if (doc.getInt("version") != 1) throw new IllegalArgumentException("Unsupported damage document version");
        List<Layer> layers = new ArrayList<>(); JSONArray all = doc.getJSONArray("layers");
        for (int i = 0; i < all.length(); i++) {
            JSONObject l = all.getJSONObject(i); List<Stroke> strokes = new ArrayList<>(); JSONArray ss = l.getJSONArray("strokes");
            for (int j = 0; j < ss.length(); j++) {
                JSONObject s = ss.getJSONObject(j); List<Point> points = new ArrayList<>(); JSONArray ps = s.getJSONArray("points");
                for (int k = 0; k < ps.length(); k++) points.add(new Point(ps.getJSONArray(k).getDouble(0), ps.getJSONArray(k).getDouble(1)));
                strokes.add(new Stroke(Mode.valueOf(s.getString("mode")), s.getDouble("radius"), points));
            }
            layers.add(new Layer(l.getString("name"), l.getBoolean("enabled"), strokes));
        }
        return new DamagePlan(doc.getInt("width"), doc.getInt("height"), doc.getLong("seed"), layers);
    }
    public JSONObject json() throws JSONException {
        JSONArray all = new JSONArray();
        for (Layer layer : layers) {
            JSONArray strokes = new JSONArray();
            for (Stroke stroke : layer.strokes()) {
                JSONArray points = new JSONArray(); for (Point point : stroke.points()) points.put(new JSONArray().put(point.x()).put(point.y()));
                strokes.put(new JSONObject().put("mode", stroke.mode().name()).put("radius", stroke.radius()).put("points", points));
            }
            all.put(new JSONObject().put("name", layer.name()).put("enabled", layer.enabled()).put("strokes", strokes));
        }
        return new JSONObject().put("version", 1).put("width", width).put("height", height).put("seed", seed).put("layers", all);
    }
}
