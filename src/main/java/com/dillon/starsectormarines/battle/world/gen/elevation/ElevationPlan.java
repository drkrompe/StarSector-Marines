package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Authored raised ground in cell-center coordinates: plateaus on whole tiers
 * and the ramps between them. See {@code elevation-nouns.md}.
 *
 * <p>A plateau is a chain of capsules swept along its points, like a terrain
 * stroke, whose cells stand on {@code tier}. A ramp climbs exactly one tier
 * along the segment from its foot (on the lower tier) to its head (on the upper
 * one), {@code width} cells wide.
 *
 * <p>{@code southFace} widens the faces that look down the screen by up to that
 * many cells: the camera looks at a south face and over a north one, so a
 * south face drawn taller reads as height. The extra width is face in the
 * battle too, so what is drawn and what can be stood on agree.
 */
public record ElevationPlan(int width, int height, int faceWidth, int southFace, double ruggedness, long seed,
                            List<Plateau> plateaus, List<Ramp> ramps) {

    /** A plan whose faces are the same width all round. */
    public ElevationPlan(int width, int height, int faceWidth, double ruggedness, long seed,
                         List<Plateau> plateaus, List<Ramp> ramps) {
        this(width, height, faceWidth, 0, ruggedness, seed, plateaus, ramps);
    }

    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Finite elevation points required");
            }
        }
    }

    /**
     * A chain of capsules along {@code points}. With {@code radii} (one per point) the chain tapers between them, so
     * an outline can swell and narrow as natural ground does; without, every capsule is {@code radius}, which is
     * then the largest. A {@code graded} plateau is built ground, an engineered bank: no roughening, its two ends cut
     * square across the chain rather than rounded, and its faces drawn as a graded bank rather than an escarpment.
     */
    public record Plateau(int tier, double radius, List<Point> points, List<Double> radii, boolean graded) {
        public Plateau {
            points = List.copyOf(points);
            radii = radii == null ? List.of() : List.copyOf(radii);
            if (tier < 1 || tier > 3 || !Double.isFinite(radius) || radius < 2 || radius > 64
                    || points.isEmpty() || points.size() > 4096) {
                throw new IllegalArgumentException("Plateau tier 1-3, radius 2-64, 1-4096 points");
            }
            if (!radii.isEmpty() && radii.size() != points.size()) {
                throw new IllegalArgumentException("Plateau radii are one per point");
            }
            for (double r : radii) {
                if (!Double.isFinite(r) || r < 1 || r > radius) {
                    throw new IllegalArgumentException("Plateau point radius 1 to the plateau's radius");
                }
            }
        }

        /** A natural plateau of one radius. */
        public Plateau(int tier, double radius, List<Point> points) {
            this(tier, radius, points, List.of(), false);
        }

        /** The radius of the capsule at point {@code i}. */
        public double radiusAt(int i) {
            return radii.isEmpty() ? radius : radii.get(i);
        }
    }

    public record Ramp(Point foot, Point head, double width) {
        public Ramp {
            if (foot == null || head == null || !Double.isFinite(width) || width < 1 || width > 16
                    || Math.hypot(head.x() - foot.x(), head.y() - foot.y()) < 2) {
                throw new IllegalArgumentException("Ramp width 1-16 and at least two cells long");
            }
        }
    }

    public ElevationPlan {
        plateaus = List.copyOf(plateaus);
        ramps = List.copyOf(ramps);
        if (width < 8 || height < 8 || width > 1024 || height > 1024 || faceWidth < 1 || faceWidth > 2
                || southFace < 0 || southFace > 3
                || !Double.isFinite(ruggedness) || ruggedness < 0 || ruggedness > 1
                || plateaus.size() > 256 || ramps.size() > 256) {
            throw new IllegalArgumentException("Map 8-1024; face width 1-2; south face 0-3; ruggedness 0-1; at most 256 plateaus and ramps");
        }
        for (Plateau plateau : plateaus) for (Point p : plateau.points()) requireInside(p, width, height);
        for (Ramp ramp : ramps) {
            requireInside(ramp.foot(), width, height);
            requireInside(ramp.head(), width, height);
        }
    }

    private static void requireInside(Point p, int width, int height) {
        if (p.x() < 0 || p.y() < 0 || p.x() > width - 1 || p.y() > height - 1) {
            throw new IllegalArgumentException("Elevation point outside map: " + p);
        }
    }

    public static ElevationPlan read(JSONObject doc) throws JSONException {
        if (doc.getInt("version") != 1) throw new IllegalArgumentException("Unsupported elevation document version");
        List<Plateau> plateaus = new ArrayList<>();
        JSONArray all = doc.getJSONArray("plateaus");
        for (int i = 0; i < all.length(); i++) {
            JSONObject p = all.getJSONObject(i);
            List<Double> radii = new ArrayList<>();
            JSONArray rj = p.optJSONArray("radii");
            if (rj != null) for (int k = 0; k < rj.length(); k++) radii.add(rj.getDouble(k));
            plateaus.add(new Plateau(p.getInt("tier"), p.getDouble("radius"), points(p.getJSONArray("points")), radii,
                    p.optBoolean("graded", false)));
        }
        List<Ramp> ramps = new ArrayList<>();
        JSONArray rs = doc.getJSONArray("ramps");
        for (int i = 0; i < rs.length(); i++) {
            JSONObject r = rs.getJSONObject(i);
            ramps.add(new Ramp(point(r.getJSONArray("foot")), point(r.getJSONArray("head")), r.getDouble("width")));
        }
        return new ElevationPlan(doc.getInt("width"), doc.getInt("height"), doc.getInt("faceWidth"),
                doc.optInt("southFace", 0), doc.getDouble("ruggedness"), doc.getLong("seed"), plateaus, ramps);
    }

    public JSONObject json() throws JSONException {
        JSONArray all = new JSONArray();
        for (Plateau p : plateaus) {
            JSONArray points = new JSONArray();
            for (Point point : p.points()) points.put(json(point));
            JSONObject plateau = new JSONObject().put("tier", p.tier()).put("radius", p.radius()).put("points", points);
            if (!p.radii().isEmpty()) {
                JSONArray radii = new JSONArray();
                for (double r : p.radii()) radii.put(r);
                plateau.put("radii", radii);
            }
            if (p.graded()) plateau.put("graded", true);
            all.put(plateau);
        }
        JSONArray rs = new JSONArray();
        for (Ramp r : ramps) {
            rs.put(new JSONObject().put("foot", json(r.foot())).put("head", json(r.head())).put("width", r.width()));
        }
        return new JSONObject().put("version", 1).put("width", width).put("height", height)
                .put("faceWidth", faceWidth).put("southFace", southFace).put("ruggedness", ruggedness).put("seed", seed)
                .put("plateaus", all).put("ramps", rs);
    }

    private static List<Point> points(JSONArray array) throws JSONException {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) out.add(point(array.getJSONArray(i)));
        return out;
    }

    private static Point point(JSONArray xy) throws JSONException {
        return new Point(xy.getDouble(0), xy.getDouble(1));
    }

    private static JSONArray json(Point p) throws JSONException {
        return new JSONArray().put(p.x()).put(p.y());
    }
}
