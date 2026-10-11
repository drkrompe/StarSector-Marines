package com.dillon.starsectormarines.battle.world.gen.road;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;

/** Editable road intent; coordinates are cell centers in world space. Wear is visual only. */
public record RoadPlan(int width, int height, List<Route> routes) {
    public enum Material { ASPHALT, CONCRETE;
        public String block(int age) { return "pavement." + name().toLowerCase(Locale.ROOT)
                + "." + new String[]{"new", "worn", "cracked"}[age]; }
    }
    public enum EndCap { ROUND, BUTT;
        @Override public String toString() { return this == ROUND ? "Round" : "Flush"; }
    }
    public record Point(double x, double y) {
        public Point { if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Finite anchors required"); }
    }
    public record Route(String id, List<Point> points, double width, double shoulder, double wear, long seed, Material material, EndCap endCap, boolean autoEdge) {
        public Route(String id, List<Point> points, double width, double shoulder, double wear, long seed, Material material, EndCap endCap) {
            this(id,points,width,shoulder,wear,seed,material,endCap,true);
        }
        public Route(String id, List<Point> points, double width, double shoulder, double wear, long seed, Material material) {
            this(id, points, width, shoulder, wear, seed, material, EndCap.ROUND);
        }
        public Route {
            points = List.copyOf(points);
            if (id == null || id.isBlank() || material == null || endCap == null || points.size() < 2 || points.size() > 64)
                throw new IllegalArgumentException("A road needs an id, material and 2–64 anchors");
            if (!Double.isFinite(width) || width < 3 || width > 32 || !Double.isFinite(shoulder) || shoulder < 0 || shoulder > 8
                    || !Double.isFinite(wear) || wear < 0 || wear > 1)
                throw new IllegalArgumentException("Width 3–32, shoulder 0–8, wear 0–1 required");
            for (int i=1;i<points.size();i++) if (points.get(i).equals(points.get(i-1)))
                throw new IllegalArgumentException("Consecutive anchors must differ");
        }
        public Route withWear(double value) { return new Route(id,points,width,shoulder,value,seed,material,endCap,autoEdge); }
    }
    public RoadPlan {
        routes = List.copyOf(routes);
        if (width < 8 || height < 8 || width > 1024 || height > 1024 || routes.isEmpty() || routes.size() > 32)
            throw new IllegalArgumentException("Map 8–1024 cells; 1–32 roads required");
        var ids = new HashSet<String>();
        for (Route road : routes) {
            if (!ids.add(road.id())) throw new IllegalArgumentException("Duplicate road id: " + road.id());
            for (Point p : road.points()) if (p.x()<0 || p.y()<0 || p.x()>width-1 || p.y()>height-1)
                throw new IllegalArgumentException("Anchor outside map: " + road.id());
        }
    }
    public static RoadPlan read(JSONObject doc) throws JSONException {
        if (doc.getInt("version") != 1) throw new IllegalArgumentException("Unsupported road document version");
        List<Route> roads = new ArrayList<>();
        JSONArray all=doc.getJSONArray("roads");
        for(int i=0;i<all.length();i++) {
            JSONObject r=all.getJSONObject(i); List<Point> points=new ArrayList<>();
            JSONArray p=r.getJSONArray("points");
            for(int j=0;j<p.length();j++) points.add(new Point(p.getJSONArray(j).getDouble(0),p.getJSONArray(j).getDouble(1)));
            roads.add(new Route(r.getString("id"),points,r.getDouble("width"),r.getDouble("shoulder"),r.getDouble("wear"),
                    r.getLong("seed"),Material.valueOf(r.getString("material")),EndCap.valueOf(r.optString("endCap", "ROUND")),r.optBoolean("autoEdge",true)));
        }
        return new RoadPlan(doc.getInt("width"),doc.getInt("height"),roads);
    }
    public JSONObject json() throws JSONException {
        JSONArray all=new JSONArray();
        for(Route r:routes) {
            JSONArray points=new JSONArray(); for(Point p:r.points()) points.put(new JSONArray().put(p.x()).put(p.y()));
            all.put(new JSONObject().put("id",r.id()).put("points",points).put("width",r.width()).put("shoulder",r.shoulder())
                    .put("wear",r.wear()).put("seed",r.seed()).put("material",r.material().name()).put("endCap",r.endCap().name()).put("autoEdge",r.autoEdge()));
        }
        return new JSONObject().put("version",1).put("width",width).put("height",height).put("roads",all);
    }
}
