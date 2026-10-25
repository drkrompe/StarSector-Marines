package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link BattleSoundLog} mixed into a soundtrack: every logged start at its tick, at its volume and pitch, panned by
 * where it is across the view, into a 16-bit stereo WAV at {@link #RATE}.
 *
 * <ul>
 *   <li>A sound id resolves through the mod's {@code data/config/sounds.json} (then the game's files, for an entry
 *       that points at a core clip), then the game's own {@code sounds.json}; its takes play in turn, the n-th start of an id on take {@code n % takes}, where
 *       the game would pick one at random. Each take is decoded once ({@link FfmpegVideo#decodeMono}).</li>
 *   <li>The level is the logged volume times the take's own {@code volume}; the pitch the logged pitch times the
 *       take's {@code pitch}, and as in the game it moves pitch and speed together, so the take is resampled.</li>
 *   <li>Pan is equal-power, hard over at the view's edge ({@link BattleReviewFrameRenderer#PLAY_ZOOM_CELLS} across).
 *       The engine's own distance roll-off is not modelled: a level is what the battle's rules gave it.</li>
 *   <li>A mix that would clip is scaled down as a whole, so the levels keep their proportions; the console says
 *       by how much.</li>
 * </ul>
 * The mix is a pure function of the log and the clips: same log, same WAV.
 */
public final class BattleSoundMix {

    public static final int RATE = 48_000;

    private BattleSoundMix() { }

    /** One take of a sound id: its file, and the level and pitch {@code sounds.json} gives it. */
    record Take(Path file, float volume, float pitch) { }

    /**
     * Mixes {@code log} into {@code wav}, {@code seconds} long; a sound starting past the end is dropped and one running
     * over it is cut. Returns how many starts were mixed.
     */
    public static int mix(Path modRoot, Path log, double seconds, Path wav) throws IOException {
        Map<String, List<Take>> takes = takes(modRoot);
        int length = (int) Math.round(seconds * RATE);
        float[] left = new float[length];
        float[] right = new float[length];
        Map<Path, float[]> decoded = new HashMap<>();
        Map<String, Integer> plays = new HashMap<>();
        Set<String> unknown = new LinkedHashSet<>();
        float halfWidth = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS / 2f;
        int mixed = 0;
        for (String line : Files.readAllLines(log)) {
            if (line.isBlank()) continue;
            JSONObject start;
            String id;
            try {
                start = new JSONObject(line);
                id = start.getString("id");
            } catch (JSONException e) {
                throw new IOException("bad sound log line in " + log + ": " + line, e);
            }
            List<Take> options = takes.get(id);
            if (options == null || options.isEmpty()) {
                unknown.add(id);
                continue;
            }
            int n = plays.merge(id, 1, Integer::sum) - 1;
            Take take = options.get(n % options.size());
            float[] clip = decoded.get(take.file());
            if (clip == null) {
                if (!Files.exists(take.file())) {
                    throw new IOException("sound " + id + " plays " + take.file()
                            + ", which is missing; run gradlew :asset-pipeline:processAudio");
                }
                clip = FfmpegVideo.decodeMono(take.file(), RATE);
                decoded.put(take.file(), clip);
            }
            int at = (int) Math.round(start.optInt("frame") * (double) BattleSimulation.TICK_DT * RATE);
            float gain = (float) start.optDouble("volume", 1.0) * take.volume();
            float pitch = (float) start.optDouble("pitch", 1.0) * take.pitch();
            float dx = (float) (start.optDouble("x", 0.0) - start.optDouble("listenerX", 0.0));
            float pan = Math.max(-1f, Math.min(1f, dx / halfWidth));
            double angle = (pan + 1.0) * Math.PI / 4.0;
            float gainLeft = gain * (float) Math.cos(angle);
            float gainRight = gain * (float) Math.sin(angle);
            if (add(clip, pitch, at, gainLeft, gainRight, left, right)) mixed++;
        }
        float peak = 0f;
        for (int i = 0; i < length; i++) peak = Math.max(peak, Math.max(Math.abs(left[i]), Math.abs(right[i])));
        float scale = peak > 1f ? 1f / peak : 1f;
        if (!unknown.isEmpty()) {
            System.out.println("    [sound] no clip for " + String.join(", ", unknown) + " in sounds.json; left out");
        }
        System.out.printf("    [sound] %s: %d starts from %d takes, peak %.2f%s%n", log.getFileName(), mixed,
                decoded.size(), peak, scale < 1f ? String.format(", scaled by %.2f to fit", scale) : "");
        writeWav(wav, left, right, scale);
        return mixed;
    }

    /**
     * Adds {@code clip} at {@code pitch} (read {@code pitch} source samples per output sample, linearly interpolated)
     * from output sample {@code at}. False when it starts past the end.
     */
    static boolean add(float[] clip, float pitch, int at, float gainLeft, float gainRight,
                       float[] left, float[] right) {
        if (at >= left.length || clip.length == 0 || pitch <= 0f) return false;
        double step = pitch;
        for (int i = Math.max(0, at); i < left.length; i++) {
            double source = (i - at) * step;
            int s = (int) source;
            if (s >= clip.length - 1) {
                if (s == clip.length - 1 && source == s) {
                    left[i] += clip[s] * gainLeft;
                    right[i] += clip[s] * gainRight;
                }
                break;
            }
            float frac = (float) (source - s);
            float v = clip[s] + (clip[s + 1] - clip[s]) * frac;
            left[i] += v * gainLeft;
            right[i] += v * gainRight;
        }
        return true;
    }

    /** Every sound id in the mod's {@code sounds.json}, groups such as {@code music} included, to its takes. */
    static Map<String, List<Take>> takes(Path modRoot) throws IOException {
        Path config = modRoot.resolve("data/config/sounds.json");
        JSONObject root;
        try {
            root = new JSONObject(Files.readString(config).replaceAll("(?m)#.*$", ""));
        } catch (JSONException e) {
            throw new IOException("could not read " + config, e);
        }
        Path core = coreRoot();
        Map<String, List<Take>> out = new HashMap<>();
        collect(root, modRoot, core, out);
        if (core != null) {
            // The game's own ids (a vanilla weapon's fire), from its loose files; packed music is left out.
            Path vanilla = core.resolve("data/config/sounds.json");
            try {
                Map<String, List<Take>> game = new HashMap<>();
                collect(new JSONObject(Files.readString(vanilla).replaceAll("(?m)#.*$", "")), core, null, game);
                game.forEach(out::putIfAbsent);
            } catch (JSONException e) {
                System.out.println("    [sound] could not read " + vanilla + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static void collect(JSONObject group, Path modRoot, Path core, Map<String, List<Take>> out) {
        for (Iterator<?> keys = group.keys(); keys.hasNext(); ) {
            String key = keys.next().toString();
            Object value = group.opt(key);
            if (value instanceof JSONObject nested) {
                collect(nested, modRoot, core, out);
            } else if (value instanceof JSONArray entries) {
                List<Take> list = new ArrayList<>();
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null || !entry.has("file") || entry.has("source")) continue;
                    String file = entry.optString("file");
                    Path path = modRoot.resolve(file);
                    if (!Files.exists(path) && core != null && Files.exists(core.resolve(file))) {
                        path = core.resolve(file);
                    }
                    list.add(new Take(path, (float) entry.optDouble("volume", 1.0),
                            (float) entry.optDouble("pitch", 1.0)));
                }
                out.put(key, list);
            }
        }
    }

    /** The game's {@code starsector-core}, as {@code createSnapshots} names the install; null when unknown. */
    private static Path coreRoot() {
        String dir = System.getProperty("starsectorDir");
        if (dir == null || dir.isBlank()) return null;
        Path core = Path.of(dir, "starsector-core");
        return Files.isDirectory(core) ? core : null;
    }

    private static void writeWav(Path wav, float[] left, float[] right, float scale) throws IOException {
        int frames = left.length;
        int dataBytes = frames * 4;
        try (OutputStream file = Files.newOutputStream(wav);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file, 1 << 16))) {
            out.writeBytes("RIFF");
            out.writeInt(Integer.reverseBytes(36 + dataBytes));
            out.writeBytes("WAVEfmt ");
            out.writeInt(Integer.reverseBytes(16));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeShort(Short.reverseBytes((short) 2));
            out.writeInt(Integer.reverseBytes(RATE));
            out.writeInt(Integer.reverseBytes(RATE * 4));
            out.writeShort(Short.reverseBytes((short) 4));
            out.writeShort(Short.reverseBytes((short) 16));
            out.writeBytes("data");
            out.writeInt(Integer.reverseBytes(dataBytes));
            for (int i = 0; i < frames; i++) {
                out.writeShort(Short.reverseBytes(pcm(left[i] * scale)));
                out.writeShort(Short.reverseBytes(pcm(right[i] * scale)));
            }
        }
    }

    private static short pcm(float v) {
        return (short) Math.round(Math.max(-1f, Math.min(1f, v)) * 32767f);
    }
}
