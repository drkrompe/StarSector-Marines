package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The moving parts of one prop: what is drawn over its static base, how each part moves, and what has to be true for
 * it to run. Authored against the base doodad's id in a motion catalog rather than on the doodad itself, because a
 * tileset's own document is rewritten by its exporter and a motion written there would not survive the next export.
 *
 * <p>A part is another doodad on a sheet, drawn at a point in the base's own source frame and turned and mirrored with
 * the placement, so one authored motion serves a prop in every room pose. Three motions:
 * <ul>
 *   <li>{@link Kind#SPIN} turns the part about its centre (a fan, a radar, a lathe chuck);</li>
 *   <li>{@link Kind#SLIDE} carries it along a track of stops, dwelling at each, out and back (a gantry crane, a cargo
 *       lift, a rowing seat);</li>
 *   <li>{@link Kind#SCROLL} lays the part's frame as a seamless tile end to end across a window and runs it along one
 *       axis (a belt conveyor, a treadmill).</li>
 * </ul>
 * {@link Kind#FIXED} parts do not move; they exist to put structure in the overhead layer with the parts that do.
 *
 * <p>Every motion is a pure function of a <b>phase</b>: the seconds the prop has actually run. A stopped prop keeps
 * its phase, so a crane that loses its operator halts where it is rather than snapping home. Presentation only: a part
 * never moves the prop's footprint, cover or blocking, and is never drawn outside what its base already owns.
 */
public final class PropMotion {

    /** What a prop needs in order to run. */
    public enum Runs {
        /** Always running. */
        ALWAYS,
        /** Running while the ship's power is up; at a deficit it runs at {@link #deficitRate}. */
        POWER,
        /** Running while somebody is at work on it. */
        USE,
        /** Running while somebody is at work on it, at the power deficit's rate. */
        USE_AND_POWER;

        public boolean needsUse() { return this == USE || this == USE_AND_POWER; }
        public boolean needsPower() { return this == POWER || this == USE_AND_POWER; }

        static Runs fromJson(String value) {
            return switch (value == null ? "always" : value) {
                case "always" -> ALWAYS;
                case "power" -> POWER;
                case "use" -> USE;
                case "use+power" -> USE_AND_POWER;
                default -> throw new IllegalArgumentException("unknown runs '" + value + "'");
            };
        }
    }

    public enum Kind { FIXED, SPIN, SLIDE, SCROLL }

    /** Which pass draws the part: with the deck's furniture, or above the people standing under it. */
    public enum Layer { DECK, OVERHEAD }

    /**
     * One moving part. Coordinates are in the base's source cells, measured from the top-left of its frame, which is
     * the frame the art was drawn in.
     *
     * @param x,y          where the part's centre sits (SPIN, FIXED, and SLIDE when it has no stops)
     * @param stops        SLIDE: the centre's successive stops, as {x, y} pairs
     * @param window       SCROLL: {x, y, width, height} of the area the belt fills
     * @param alongY       SCROLL: the belt runs top to bottom of the frame rather than left to right
     * @param period       SCROLL: the length of one tile along the run, in cells
     * @param speed        SCROLL: cells per second; SPIN: degrees per second
     */
    public record Part(String doodadId, Kind kind, Layer layer, float x, float y,
                       float[] stops, float travelSeconds, float dwellSeconds,
                       float[] window, boolean alongY, float period, float speed) {

        /** SPIN: the part's turn in degrees after {@code phase} running seconds. */
        public float spinDegrees(float phase) {
            return kind == Kind.SPIN ? (float) ((double) speed * phase % 360.0) : 0f;
        }

        /** SCROLL: how far the belt has run into its current tile, in cells, in [0, period). */
        public float scrollOffset(float phase) {
            if (kind != Kind.SCROLL || period <= 0f) return 0f;
            double run = (double) speed * phase % period;
            return (float) (run < 0 ? run + period : run);
        }

        /**
         * SLIDE: the part's centre after {@code phase} running seconds, as {x, y}. The track runs out through every stop
         * and back again; each leg dwells at the stop it leaves and then eases to the next.
         */
        public float[] slideAt(float phase) {
            int count = stops == null ? 0 : stops.length / 2;
            if (kind != Kind.SLIDE || count < 2) return count == 1 ? new float[]{stops[0], stops[1]} : new float[]{x, y};
            int legs = 2 * (count - 1);
            double leg = dwellSeconds + travelSeconds;
            double cycle = legs * leg;
            double t = phase % cycle;
            if (t < 0) t += cycle;
            int index = (int) Math.min(legs - 1, Math.floor(t / leg));
            double within = t - index * leg;
            int from = stopOf(index, count), to = stopOf(index + 1, count);
            float u = within <= dwellSeconds || travelSeconds <= 0f ? 0f
                    : smooth((float) ((within - dwellSeconds) / travelSeconds));
            return new float[]{
                    stops[2 * from] + (stops[2 * to] - stops[2 * from]) * u,
                    stops[2 * from + 1] + (stops[2 * to + 1] - stops[2 * from + 1]) * u};
        }

        /** The stop a ping-pong walk is at after {@code step} legs. */
        private static int stopOf(int step, int count) {
            int span = count - 1;
            int k = Math.floorMod(step, 2 * span);
            return k <= span ? k : 2 * span - k;
        }

        private static float smooth(float u) {
            float c = Math.max(0f, Math.min(1f, u));
            return c * c * (3f - 2f * c);
        }
    }

    public final String baseId;
    public final Runs runs;
    /** The share of full speed a power-gated prop keeps when the ship's power is at a deficit. */
    public final float deficitRate;
    /** Seconds to come up to speed from rest, and to run down again; zero responds at once. */
    public final float spinUpSeconds;
    private final List<Part> parts;

    /**
     * The share of full speed the prop keeps once damaged, drawn over its {@code -damaged} variant; zero (the default)
     * means a damaged prop shows no moving parts at all, its damaged art standing for the machine. A destroyed prop
     * never runs.
     */
    public final float damagedRate;

    public PropMotion(String baseId, Runs runs, float deficitRate, float spinUpSeconds, List<Part> parts) {
        this(baseId, runs, deficitRate, 0f, spinUpSeconds, parts);
    }

    public PropMotion(String baseId, Runs runs, float deficitRate, float damagedRate, float spinUpSeconds,
                      List<Part> parts) {
        this.baseId = baseId;
        this.runs = runs;
        this.deficitRate = deficitRate;
        this.damagedRate = damagedRate;
        this.spinUpSeconds = spinUpSeconds;
        this.parts = List.copyOf(parts);
    }

    public List<Part> parts() { return parts; }

    /**
     * Whether the prop's own art is a fixed overhead part (an awning, a pipe rack): it then draws only in the roof
     * pass, above the people under it, and never as a ground doodad below them. Like a roof it is part of the street,
     * so it draws whether or not the fog has reached it.
     */
    public boolean liftsItself() {
        for (Part part : parts) {
            if (part.layer == Layer.OVERHEAD && part.kind == Kind.FIXED && part.doodadId.equals(baseId)) return true;
        }
        return false;
    }

    /** Whether any part draws in {@code layer}. */
    public boolean draws(Layer layer) {
        for (Part part : parts) if (part.layer == layer) return true;
        return false;
    }

    /**
     * The speed a prop runs at, as a share of full: zero when a use-gated prop stands unworked, and scaled down by
     * {@code powerDeficit} (0 with the ship's power up, 1 with it at a deficit) when the prop runs on power.
     */
    public float targetRate(boolean inUse, float powerDeficit) {
        return targetRate(inUse, powerDeficit, false);
    }

    /** As {@link #targetRate(boolean, float)}, for a prop standing damaged: scaled again by {@link #damagedRate}. */
    public float targetRate(boolean inUse, float powerDeficit, boolean damaged) {
        if (runs.needsUse() && !inUse) return 0f;
        float rate = damaged ? damagedRate : 1f;
        if (!runs.needsPower()) return rate;
        float deficit = Math.max(0f, Math.min(1f, powerDeficit));
        return rate * (1f + (deficitRate - 1f) * deficit);
    }

    /** One entry of a catalog's {@code motions} array. */
    static PropMotion parse(JSONObject json) throws JSONException {
        String base = json.getString("doodad");
        Runs runs;
        try {
            runs = Runs.fromJson(json.optString("runs", "always"));
        } catch (IllegalArgumentException e) {
            throw new JSONException("motion for '" + base + "': " + e.getMessage());
        }
        float deficitRate = finite(base, "deficitRate", (float) json.optDouble("deficitRate", 0.0));
        float damagedRate = finite(base, "damagedRate", (float) json.optDouble("damagedRate", 0.0));
        float spinUp = finite(base, "spinUpSeconds", (float) json.optDouble("spinUpSeconds", 0.0));
        if (deficitRate < 0f || deficitRate > 1f || damagedRate < 0f || damagedRate > 1f || spinUp < 0f)
            throw new JSONException("motion for '" + base + "' has a rate outside [0,1] or a negative spinUpSeconds");
        JSONArray array = json.getJSONArray("parts");
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) parts.add(parsePart(base, array.getJSONObject(i)));
        if (parts.isEmpty()) throw new JSONException("motion for '" + base + "' has no parts");
        return new PropMotion(base, runs, deficitRate, damagedRate, spinUp, parts);
    }

    private static Part parsePart(String base, JSONObject p) throws JSONException {
        String id = p.getString("part");
        Kind kind = Kind.valueOf(p.optString("kind", "fixed").toUpperCase(Locale.ROOT));
        Layer layer = Layer.valueOf(p.optString("layer", "deck").toUpperCase(Locale.ROOT));
        float[] at = floats(base, p.optJSONArray("at"), 2);
        float[] stops = null, window = null;
        float travel = 0f, dwell = 0f, period = 0f, speed = 0f;
        boolean alongY = false;
        switch (kind) {
            case SPIN -> speed = finite(base, "degPerSec", (float) p.getDouble("degPerSec"));
            case SLIDE -> {
                JSONArray track = p.getJSONArray("stops");
                stops = new float[track.length() * 2];
                for (int s = 0; s < track.length(); s++) {
                    float[] stop = floats(base, track.getJSONArray(s), 2);
                    stops[2 * s] = stop[0];
                    stops[2 * s + 1] = stop[1];
                }
                travel = finite(base, "travelSeconds", (float) p.getDouble("travelSeconds"));
                dwell = finite(base, "dwellSeconds", (float) p.optDouble("dwellSeconds", 0.0));
                if (track.length() < 2 || travel <= 0f || dwell < 0f)
                    throw new JSONException("slide on '" + base + "' needs two stops and a positive travelSeconds");
            }
            case SCROLL -> {
                window = floats(base, p.getJSONArray("window"), 4);
                alongY = "y".equals(p.optString("axis", "x"));
                period = finite(base, "period", (float) p.getDouble("period"));
                speed = finite(base, "speed", (float) p.getDouble("speed"));
                if (period <= 0f || window[2] <= 0f || window[3] <= 0f)
                    throw new JSONException("scroll on '" + base + "' needs a positive period and window");
            }
            case FIXED -> { }
        }
        if (at == null && kind != Kind.SLIDE && kind != Kind.SCROLL)
            throw new JSONException("part '" + id + "' on '" + base + "' needs an 'at' centre");
        return new Part(id, kind, layer, at == null ? 0f : at[0], at == null ? 0f : at[1],
                stops, travel, dwell, window, alongY, period, speed);
    }

    private static float[] floats(String base, JSONArray array, int n) throws JSONException {
        if (array == null) return null;
        if (array.length() != n) throw new JSONException("motion for '" + base + "' expects " + n + " numbers");
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = finite(base, "coordinate", (float) array.getDouble(i));
        return out;
    }

    private static float finite(String base, String field, float value) throws JSONException {
        if (!Float.isFinite(value)) throw new JSONException("motion for '" + base + "' has a non-finite " + field);
        return value;
    }
}
