package com.dillon.starsectormarines.battle.command.trace;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How quickly the defender answers a track that has just become threatened: from the first command pulse in which the
 * track holds a believed contact (after it was quiet for {@link #QUIET_PULSES} pulses, or never seen) to the first pulse
 * in which a squad the commander sent in response (rally, staging, manning or standing) is on its post inside that
 * track. Read from the published defender trace only, so its resolution is one command pulse.
 */
final class RallyResponseEvidence {

    private static final int QUIET_PULSES = 4;
    /** A squad is at its post when its centroid is this close (Chebyshev, cells) to the post's cell. */
    private static final int AT_POST_CELLS = 12;
    private static final Set<String> RESPONSE_REASONS = Set.of("DEFENDER_TRACK_RESPONSE", "DEFENDER_ADJACENT_TRACK_RESPONSE",
            "DEFENDER_GARRISONABLE_STAGE", "DEFENDER_GARRISONABLE_MAN", "DEFENDER_GARRISONABLE_STAND");

    private static final class Episode {
        final int track;
        final int onset;
        int arrived = -1;
        boolean closed;

        Episode(int track, int onset) {
            this.track = track;
            this.onset = onset;
        }
    }

    private RallyResponseEvidence() { }

    static String describe(String trace) {
        try {
            return read(trace);
        } catch (JSONException e) {
            throw new IllegalStateException("unreadable defender trace", e);
        }
    }

    private static String read(String trace) throws JSONException {
        Map<Integer, Integer> quiet = new HashMap<>();
        List<Episode> episodes = new ArrayList<>();
        Map<Integer, Episode> open = new HashMap<>();
        for (String line : trace.split("\n")) {
            if (!line.contains("\"perspective\":\"DEFENDER\"") || !line.contains("\"conquest\"")) continue;
            JSONObject pulse = new JSONObject(line);
            int tick = pulse.getInt("tick");
            JSONObject conquest = pulse.getJSONObject("conquest");
            boolean lateralIsX = conquest.getString("axis").contains("NORTH");
            JSONArray tracks = conquest.getJSONArray("tracks");
            for (int i = 0; i < tracks.length(); i++) {
                JSONObject track = tracks.getJSONObject(i);
                int index = track.getInt("index");
                if (track.getInt("knownHostileContacts") > 0) {
                    if (quiet.getOrDefault(index, QUIET_PULSES) >= QUIET_PULSES) {
                        Episode episode = new Episode(index, tick);
                        episodes.add(episode);
                        open.put(index, episode);
                    }
                    quiet.put(index, 0);
                } else {
                    int q = quiet.getOrDefault(index, QUIET_PULSES) + 1;
                    quiet.put(index, q);
                    Episode episode = open.get(index);
                    if (episode != null && q >= QUIET_PULSES) {
                        episode.closed = true;
                        open.remove(index);
                    }
                }
            }
            if (open.isEmpty()) continue;
            Map<Integer, double[]> centroid = new HashMap<>();
            JSONArray squads = conquest.getJSONArray("squads");
            for (int i = 0; i < squads.length(); i++) {
                JSONObject squad = squads.getJSONObject(i);
                centroid.put(squad.getInt("squadId"), new double[]{squad.getDouble("centroidX"), squad.getDouble("centroidY")});
            }
            JSONArray actions = conquest.getJSONArray("actions");
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                if (!RESPONSE_REASONS.contains(action.optString("reason"))) continue;
                int cellX = action.optInt("targetCellX", -1);
                int cellY = action.optInt("targetCellY", -1);
                double[] at = centroid.get(action.getInt("squadId"));
                if (cellX < 0 || at == null) continue;
                if (Math.max(Math.abs(at[0] - cellX), Math.abs(at[1] - cellY)) > AT_POST_CELLS) continue;
                int lateral = lateralIsX ? cellX : cellY;
                for (int t = 0; t < tracks.length(); t++) {
                    JSONObject track = tracks.getJSONObject(t);
                    if (lateral < track.getInt("lateralStart") || lateral > track.getInt("lateralEnd")) continue;
                    Episode episode = open.get(track.getInt("index"));
                    if (episode != null && episode.arrived < 0) episode.arrived = tick;
                }
            }
        }
        if (episodes.isEmpty()) return "- defender response to newly threatened tracks: none observed\n";
        int answered = 0;
        long sum = 0;
        int max = 0;
        StringBuilder each = new StringBuilder();
        for (Episode episode : episodes) {
            if (episode.arrived >= 0) {
                answered++;
                int latency = episode.arrived - episode.onset;
                sum += latency;
                max = Math.max(max, latency);
                each.append(' ').append(latency);
            } else {
                each.append(" none");
            }
        }
        return String.format(Locale.ROOT,
                "- defender response to newly threatened tracks (first contact to a sent squad on post in the track, pulse resolution):"
                        + " %d onsets, %d answered, mean %.0f ticks, max %d; per onset:%s%n",
                episodes.size(), answered, answered == 0 ? 0.0 : sum / (double) answered, max, each);
    }
}
