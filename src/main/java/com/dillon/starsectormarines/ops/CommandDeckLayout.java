package com.dillon.starsectormarines.ops;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The packed command-deck banner: its plate, the view of space behind the plate's windows,
 * the figure sprites standing on it, the stations a player can use, and where the live
 * overlays sit.
 *
 * <p>Written by {@code art-source/hq-command-deck/pack.py} from the Blender layout and
 * the placed figure cards, so a repaint on the same guide only needs the pack re-run. All positions are in packed-plate pixels
 * except station rectangles, which are fractions of the plate so they survive a
 * different pack width.
 *
 * <p>The pack also carries the guide's {@link Camera} and the plotting table's footprint
 * in deck metres, so live overlays that stand in the room (the holo board) can be
 * projected into the painting rather than placed by eye. Both are optional; a layout
 * without them has no projectable volume and {@link #projects()} answers false.
 *
 * <p>The monitor faces ({@link Display}) and boards of buttons ({@link Panel}) come from
 * the model's outlines, nudged by how far the painter moved each one's key colour. A layout
 * without them simply has no live screens or buttons.
 */
record CommandDeckLayout(float width, float height, String plate, String space, List<Figure> figures,
                         List<Station> stations, float[] plot, float[] screens,
                         Camera camera, float[] table, List<Display> displays, List<Panel> panels) {

    static final String PATH = "data/ui/hq-command-deck.json";

    /** Height of the plotting table's top above the deck, in metres. */
    static final float TABLE_TOP = 0.97f;

    /**
     * The guide camera in packed-plate pixels: a pinhole at {@code height} metres over the
     * deck, looking along +z. A deck point (x right, y up from the deck, z away) lands at
     * {@code u = cx + focal * x / z}, {@code v = cy - focal * (y - height) / z}.
     */
    record Camera(float focal, float cx, float cy, float height) {
        float u(float x, float z) { return cx + focal * x / z; }

        float v(float y, float z) { return cy - focal * (y - height) / z; }
    }

    /** {@code behindPlot} figures stand beyond the table, so the hologram draws in front of them. */
    record Figure(String id, String sprite, float x, float y, float w, float h, boolean behindPlot) { }

    /**
     * A board of buttons drawn over the plate. {@code buttons} holds eight values per button,
     * its corners clockwise from top-left in packed-plate pixels; {@code role} names what the
     * board answers to ({@link DeckPanels}).
     */
    record Panel(String id, String role, float[] buttons) {
        Panel {
            if (buttons == null || buttons.length % 8 != 0) throw new IllegalArgumentException("a button needs four corners");
            buttons = buttons.clone();
        }

        @Override
        public float[] buttons() { return buttons.clone(); }

        int count() { return buttons.length / 8; }

        float x(int button, int corner) { return buttons[button * 8 + corner * 2]; }

        float y(int button, int corner) { return buttons[button * 8 + corner * 2 + 1]; }
    }

    /**
     * A monitor face behind a hole in the plate, which live readouts are drawn into.
     * {@code quad} holds its four corners clockwise from top-left in packed-plate pixels,
     * as {@code x0, y0, x1, y1, x2, y2, x3, y3}; {@code kind} names what it shows.
     */
    record Display(String id, String kind, float[] quad) {
        Display {
            if (quad == null || quad.length != 8) throw new IllegalArgumentException("display quad needs four corners");
            quad = quad.clone();
        }

        @Override
        public float[] quad() { return quad.clone(); }

        /** Corner {@code i} (0 top-left, clockwise) x. */
        float x(int corner) { return quad[corner * 2]; }

        /** Corner {@code i} (0 top-left, clockwise) y. */
        float y(int corner) { return quad[corner * 2 + 1]; }
    }

    /** A place on deck and the panel it points at; {@code label} names the i18n suffix. */
    record Station(String id, String label, List<String> figures, float fx, float fy, float fw, float fh) {
        boolean shows(String figure) { return figures.contains(figure); }
    }

    /**
     * Cover-fit of the plate into a box: the plate fills the box and is centred, and
     * whatever does not fit is cut, never stretched. A box wider than the plate's
     * aspect loses a strip of ceiling and deck; a narrower one loses the side banks.
     */
    record Fit(float scale, float offsetX, float offsetY) {
        static Fit cover(float boxWidth, float boxHeight, float imageWidth, float imageHeight) {
            float scale = Math.max(boxWidth / imageWidth, boxHeight / imageHeight);
            return new Fit(scale, (boxWidth - imageWidth * scale) * 0.5f, (boxHeight - imageHeight * scale) * 0.5f);
        }

        float x(float imageX) { return offsetX + imageX * scale; }

        float y(float imageY) { return offsetY + imageY * scale; }
    }

    static CommandDeckLayout parse(String text) {
        try {
            JSONObject root = new JSONObject(text);
            JSONArray size = root.getJSONArray("size");
            List<Figure> figures = new ArrayList<>();
            JSONArray figureArray = root.getJSONArray("figures");
            for (int i = 0; i < figureArray.length(); i++) {
                JSONObject f = figureArray.getJSONObject(i);
                JSONArray r = f.getJSONArray("rect");
                figures.add(new Figure(f.getString("id"), f.getString("sprite"),
                        (float) r.getDouble(0), (float) r.getDouble(1), (float) r.getDouble(2), (float) r.getDouble(3),
                        f.optBoolean("behindPlot", false)));
            }
            List<Station> stations = new ArrayList<>();
            JSONArray stationArray = root.getJSONArray("stations");
            for (int i = 0; i < stationArray.length(); i++) {
                JSONObject s = stationArray.getJSONObject(i);
                JSONArray r = s.getJSONArray("rect");
                List<String> people = new ArrayList<>();
                JSONArray who = s.getJSONArray("figures");
                for (int j = 0; j < who.length(); j++) people.add(who.getString(j));
                stations.add(new Station(s.getString("id"), s.getString("label"), List.copyOf(people),
                        (float) r.getDouble(0), (float) r.getDouble(1), (float) r.getDouble(2), (float) r.getDouble(3)));
            }
            JSONObject overlays = root.getJSONObject("overlays");
            return new CommandDeckLayout((float) size.getDouble(0), (float) size.getDouble(1), root.getString("plate"),
                    root.optString("space", null), List.copyOf(figures), List.copyOf(stations),
                    points(overlays.getJSONArray("plot")), points(overlays.getJSONArray("screens")),
                    camera(root.optJSONObject("camera")), table(root.optJSONArray("table")),
                    displays(root.optJSONArray("displays")), panels(root.optJSONArray("panels")));
        } catch (JSONException failure) {
            throw new IllegalArgumentException("Malformed command deck layout", failure);
        }
    }

    private static List<Display> displays(JSONArray array) throws JSONException {
        if (array == null) return List.of();
        List<Display> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject d = array.getJSONObject(i);
            JSONArray corners = d.getJSONArray("quad");
            if (corners.length() != 4) throw new JSONException("display " + d.optString("id") + " needs four corners");
            out.add(new Display(d.getString("id"), d.getString("kind"), points(corners)));
        }
        return List.copyOf(out);
    }

    private static List<Panel> panels(JSONArray array) throws JSONException {
        if (array == null) return List.of();
        List<Panel> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject p = array.getJSONObject(i);
            JSONArray buttons = p.getJSONArray("buttons");
            float[] values = new float[buttons.length() * 8];
            for (int b = 0; b < buttons.length(); b++) {
                JSONArray corners = buttons.getJSONArray(b);
                if (corners.length() != 8) throw new JSONException("panel " + p.optString("id") + " has a button without four corners");
                for (int k = 0; k < 8; k++) values[b * 8 + k] = (float) corners.getDouble(k);
            }
            out.add(new Panel(p.getString("id"), p.getString("role"), values));
        }
        return List.copyOf(out);
    }

    private static Camera camera(JSONObject camera) throws JSONException {
        if (camera == null) return null;
        return new Camera((float) camera.getDouble("focal"), (float) camera.getDouble("cx"),
                (float) camera.getDouble("cy"), (float) camera.getDouble("height"));
    }

    private static float[] table(JSONArray table) throws JSONException {
        if (table == null) return null;
        if (table.length() != 4) throw new JSONException("table needs [x0, x1, z0, z1]");
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) out[i] = (float) table.getDouble(i);
        return out;
    }

    /** True when the layout carries the camera and table a projected overlay needs. */
    boolean projects() { return camera != null && table != null; }

    /**
     * A deck point in metres projected to packed-plate pixels, as {@code {u, v}}.
     *
     * @throws IllegalStateException when the layout carries no camera
     * @throws IllegalArgumentException when the point is not in front of the camera
     */
    float[] project(float x, float y, float z) {
        if (camera == null) throw new IllegalStateException("Command deck layout has no camera");
        if (!(z > 0f)) throw new IllegalArgumentException("Deck point is behind the camera: z=" + z);
        return new float[]{camera.u(x, z), camera.v(y, z)};
    }

    /** Centre of the table top in deck metres, as {@code {x, y, z}}. */
    float[] tableCentre() {
        if (table == null) throw new IllegalStateException("Command deck layout has no table");
        return new float[]{(table[0] + table[1]) * 0.5f, TABLE_TOP, (table[2] + table[3]) * 0.5f};
    }

    private static float[] points(JSONArray array) throws JSONException {
        float[] out = new float[array.length() * 2];
        for (int i = 0; i < array.length(); i++) {
            JSONArray p = array.getJSONArray(i);
            out[i * 2] = (float) p.getDouble(0);
            out[i * 2 + 1] = (float) p.getDouble(1);
        }
        return out;
    }

    Station station(String id) {
        for (Station station : stations) if (station.id().equals(id)) return station;
        return null;
    }
}
