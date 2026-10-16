package com.dillon.starsectormarines.ops;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The packed command-deck banner: its plate, the figure sprites standing on it, the
 * stations a player can use, and where the live overlays sit.
 *
 * <p>Written by {@code art-source/hq-command-deck/pack.py} from the Blender layout and
 * the placed figure cards, never measured off the painting, so a repaint registered on
 * the same guide only needs the pack re-run. All positions are in packed-plate pixels
 * except station rectangles, which are fractions of the plate so they survive a
 * different pack width.
 */
record CommandDeckLayout(float width, float height, String plate, List<Figure> figures,
                         List<Station> stations, float[] plot, float[] screens) {

    static final String PATH = "data/ui/hq-command-deck.json";

    record Figure(String id, String sprite, float x, float y, float w, float h) { }

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
                        (float) r.getDouble(0), (float) r.getDouble(1), (float) r.getDouble(2), (float) r.getDouble(3)));
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
                    List.copyOf(figures), List.copyOf(stations),
                    points(overlays.getJSONArray("plot")), points(overlays.getJSONArray("screens")));
        } catch (JSONException failure) {
            throw new IllegalArgumentException("Malformed command deck layout", failure);
        }
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
