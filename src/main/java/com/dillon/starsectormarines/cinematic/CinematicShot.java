package com.dillon.starsectormarines.cinematic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One shot's timeline as {@code cinematic_export.py} writes it: a video, and the captions,
 * overlays and sound cues the player draws and schedules against it (runtime-contract.md).
 * All times are seconds from the shot's first frame.
 */
public record CinematicShot(String id, String video, int fps, int width, int height, int frames, double duration,
                            Map<String, Speaker> speakers, List<Caption> captions, List<LockOverlay> locks,
                            List<Cue> cues) {

    /** Colours are named, not numeric, so the player picks them from the game's own palette. */
    public record Speaker(String label, String color) { }

    public record Caption(double in, double out, String speaker, String text) { }

    public record Cue(double t, String sound, float volume, double until, float fadeIn, float fadeOut) {
        public boolean looped() { return !Double.isNaN(until); }
    }

    public record Detail(String text, String color) { }

    /** A sensor lock on something in the picture, tracked by its centre and radius in source pixels. */
    public record LockOverlay(double in, double out, double closeSeconds, double outSeconds, double arcDegrees,
                              double leader, String title, List<Detail> detail, double[][] track) { }

    public static CinematicShot parse(JSONObject json) throws JSONException {
        Map<String, Speaker> speakers = new LinkedHashMap<>();
        JSONObject speakerJson = json.optJSONObject("speakers");
        if (speakerJson != null)
            for (String key : keys(speakerJson)) {
                JSONObject s = speakerJson.getJSONObject(key);
                speakers.put(key, new Speaker(s.getString("label"), s.optString("color", "player")));
            }
        List<Caption> captions = new ArrayList<>();
        JSONArray captionJson = json.optJSONArray("captions");
        for (int i = 0; captionJson != null && i < captionJson.length(); i++) {
            JSONObject c = captionJson.getJSONObject(i);
            captions.add(new Caption(c.getDouble("t_in"), c.getDouble("t_out"), c.getString("speaker"), c.getString("text")));
        }
        List<LockOverlay> locks = new ArrayList<>();
        JSONArray overlayJson = json.optJSONArray("overlays");
        for (int i = 0; overlayJson != null && i < overlayJson.length(); i++) {
            JSONObject o = overlayJson.getJSONObject(i);
            if (!"lock".equals(o.optString("kind"))) continue;     // unknown kinds are skipped, not fatal
            List<Detail> detail = new ArrayList<>();
            JSONArray detailJson = o.optJSONArray("detail");
            for (int d = 0; detailJson != null && d < detailJson.length(); d++)
                detail.add(new Detail(detailJson.getJSONObject(d).getString("text"),
                        detailJson.getJSONObject(d).optString("color", "player")));
            JSONArray trackJson = o.getJSONArray("track");
            double[][] track = new double[trackJson.length()][4];
            for (int p = 0; p < track.length; p++)
                for (int k = 0; k < 4; k++) track[p][k] = trackJson.getJSONArray(p).getDouble(k);
            Arrays.sort(track, Comparator.comparingDouble(p -> p[0]));
            locks.add(new LockOverlay(o.getDouble("t_in"), o.getDouble("t_out"), o.optDouble("close_seconds", 0.7),
                    o.optDouble("out_seconds", 0.9), o.optDouble("arc_degrees", 16), o.optDouble("leader", 46),
                    o.optString("title", ""), detail, track));
        }
        List<Cue> cues = new ArrayList<>();
        JSONArray cueJson = json.optJSONArray("cues");
        for (int i = 0; cueJson != null && i < cueJson.length(); i++) {
            JSONObject c = cueJson.getJSONObject(i);
            cues.add(new Cue(c.getDouble("t"), c.getString("sound"), (float) c.optDouble("volume", 1.0),
                    c.has("until") ? c.getDouble("until") : Double.NaN,
                    (float) c.optDouble("fade_in", 0), (float) c.optDouble("fade_out", 0)));
        }
        cues.sort(Comparator.comparingDouble(Cue::t));
        return new CinematicShot(json.getString("id"), json.getString("video"), json.optInt("fps", 24),
                json.optInt("width", 1920), json.optInt("height", 1080), json.getInt("frames"),
                json.getDouble("duration"), speakers, captions, locks, cues);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<String> keys(JSONObject json) {
        List<String> out = new ArrayList<>();
        json.keys().forEachRemaining(k -> out.add((String) k));
        return out;
    }
}
