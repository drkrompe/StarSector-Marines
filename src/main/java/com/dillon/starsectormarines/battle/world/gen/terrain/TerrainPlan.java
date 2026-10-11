package com.dillon.starsectormarines.battle.world.gen.terrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/** Ordered brush intent in cell-center coordinates. A raised mass is wholly inaccessible. */
public record TerrainPlan(int width, int height, Finish finish, double faceDepth,
                          double ruggedness, long seed, List<Stroke> strokes) {
    public enum Finish { ROCK, GRASS_TOP, ICE;
        public String materialPrefix() { return this==ICE?"terrain.ice.":"terrain."; }
        @Override public String toString() { return switch(this) {
            case ROCK -> "Rock escarpment"; case GRASS_TOP -> "Grass-topped cliff"; case ICE -> "Glacial ice ridge";
        }; }
    }
    public enum Mode { RAISE, ERASE;
        @Override public String toString() { return this==RAISE?"Raise terrain":"Erase / cut a pass"; }
    }
    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Finite brush points required");
        }
    }
    public record Stroke(Mode mode, double radius, List<Point> points) {
        public Stroke {
            points = List.copyOf(points);
            if (mode == null || !Double.isFinite(radius) || radius < 1 || radius > 64
                    || points.isEmpty() || points.size() > 4096)
                throw new IllegalArgumentException("Brush radius 1-64; 1-4096 points required");
        }
    }
    public TerrainPlan {
        strokes = List.copyOf(strokes);
        if (width < 8 || height < 8 || width > 1024 || height > 1024 || finish == null
                || !Double.isFinite(faceDepth) || faceDepth < 1 || faceDepth > 6
                || !Double.isFinite(ruggedness) || ruggedness < 0 || ruggedness > 1 || strokes.size() > 512)
            throw new IllegalArgumentException("Map 8-1024; face depth 1-6; ruggedness 0-1; at most 512 strokes");
        int points = 0;
        for (Stroke stroke : strokes) for (Point p : stroke.points()) {
            if (p.x() < 0 || p.y() < 0 || p.x() > width-1 || p.y() > height-1)
                throw new IllegalArgumentException("Brush point outside map");
            if (++points > 32768) throw new IllegalArgumentException("Too many brush points");
        }
    }
    public TerrainPlan append(Stroke stroke) {
        List<Stroke> next = new ArrayList<>(strokes); next.add(stroke);
        return new TerrainPlan(width,height,finish,faceDepth,ruggedness,seed,next);
    }
    public static TerrainPlan read(JSONObject doc) throws JSONException {
        if (doc.getInt("version") != 1) throw new IllegalArgumentException("Unsupported terrain document version");
        List<Stroke> strokes = new ArrayList<>(); JSONArray all = doc.getJSONArray("strokes");
        for (int i=0;i<all.length();i++) {
            JSONObject s=all.getJSONObject(i); List<Point> points=new ArrayList<>(); JSONArray ps=s.getJSONArray("points");
            for(int j=0;j<ps.length();j++) points.add(new Point(ps.getJSONArray(j).getDouble(0),ps.getJSONArray(j).getDouble(1)));
            strokes.add(new Stroke(Mode.valueOf(s.getString("mode")),s.getDouble("radius"),points));
        }
        return new TerrainPlan(doc.getInt("width"),doc.getInt("height"),Finish.valueOf(doc.getString("finish")),
                doc.getDouble("faceDepth"),doc.getDouble("ruggedness"),doc.getLong("seed"),strokes);
    }
    public JSONObject json() throws JSONException {
        JSONArray all=new JSONArray();
        for (Stroke s:strokes) {
            JSONArray points=new JSONArray(); for(Point p:s.points()) points.put(new JSONArray().put(p.x()).put(p.y()));
            all.put(new JSONObject().put("mode",s.mode().name()).put("radius",s.radius()).put("points",points));
        }
        return new JSONObject().put("version",1).put("width",width).put("height",height).put("finish",finish.name())
                .put("faceDepth",faceDepth).put("ruggedness",ruggedness).put("seed",seed).put("strokes",all);
    }
}
