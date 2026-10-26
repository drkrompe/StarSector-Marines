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
import java.util.LinkedHashMap;
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
 *   <li>A held loop (a line with {@code loop}, its slot) is logged on every tick it sounds: each unbroken run of ticks
 *       of one id and slot is one take wrapped round under the run, faded in and out over {@link #LOOP_FADE_SECONDS},
 *       at each tick's level and pan, rather than a start a tick.</li>
 *   <li>A music line (one with {@code music}) starts that track at the centre at its {@code sounds.json} volume, faded
 *       in over its {@code fadeIn} seconds; the next music line fades it out over that line's {@code fadeOut} seconds
 *       while the next track fades in. A line with {@code loop} true wraps the track round until then. An empty
 *       {@code music} is silence.</li>
 *   <li>A mix that would clip is scaled down as a whole, so the levels keep their proportions; the console says
 *       by how much.</li>
 * </ul>
 * The mix is a pure function of the log and the clips: same log, same WAV.
 */
public final class BattleSoundMix {

    public static final int RATE = 48_000;
    /** How long a held loop takes to come up and to die away at the ends of its run. */
    static final float LOOP_FADE_SECONDS = 0.25f;

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
        Map<String, List<JSONObject>> held = new LinkedHashMap<>();
        List<JSONObject> music = new ArrayList<>();
        int mixed = 0;
        for (String line : Files.readAllLines(log)) {
            if (line.isBlank()) continue;
            JSONObject start;
            String id;
            try {
                start = new JSONObject(line);
                if (start.has("music")) {
                    music.add(start);
                    continue;
                }
                id = start.getString("id");
            } catch (JSONException e) {
                throw new IOException("bad sound log line in " + log + ": " + line, e);
            }
            List<Take> options = takes.get(id);
            if (options == null || options.isEmpty()) {
                unknown.add(id);
                continue;
            }
            if (start.has("loop")) {
                held.computeIfAbsent(id + "#" + start.optInt("loop"), ignored -> new ArrayList<>()).add(start);
                continue;
            }
            int n = plays.merge(id, 1, Integer::sum) - 1;
            Take take = options.get(n % options.size());
            float[] clip = clip(id, take, decoded);
            int at = (int) Math.round(start.optInt("frame") * (double) BattleSimulation.TICK_DT * RATE);
            float gain = (float) start.optDouble("volume", 1.0) * take.volume();
            float pitch = (float) start.optDouble("pitch", 1.0) * take.pitch();
            float[] pans = pan(start, gain, halfWidth);
            if (add(clip, pitch, at, pans[0], pans[1], left, right)) mixed++;
        }
        int samplesPerTick = (int) Math.round(BattleSimulation.TICK_DT * RATE);
        int fade = Math.round(LOOP_FADE_SECONDS * RATE);
        for (List<JSONObject> ticks : held.values()) {
            for (int from = 0, to; from < ticks.size(); from = to) {
                to = from + 1;
                while (to < ticks.size()
                        && ticks.get(to).optInt("frame") == ticks.get(to - 1).optInt("frame") + 1) to++;
                JSONObject first = ticks.get(from);
                String id = first.optString("id");
                List<Take> options = takes.get(id);
                Take take = options.get(first.optInt("loop") % options.size());
                float[] gainLeft = new float[to - from];
                float[] gainRight = new float[to - from];
                for (int i = from; i < to; i++) {
                    float[] pans = pan(ticks.get(i), (float) ticks.get(i).optDouble("volume", 1.0) * take.volume(),
                            halfWidth);
                    gainLeft[i - from] = pans[0];
                    gainRight[i - from] = pans[1];
                }
                int at = (int) Math.round(first.optInt("frame") * (double) BattleSimulation.TICK_DT * RATE);
                hold(clip(id, take, decoded), (float) first.optDouble("pitch", 1.0) * take.pitch(), at,
                        samplesPerTick, gainLeft, gainRight, fade, left, right);
                mixed++;
            }
        }
        for (int c = 0; c < music.size(); c++) {
            JSONObject cue = music.get(c);
            String id = cue.optString("music");
            if (id.isEmpty()) continue;
            List<Take> options = takes.get(id);
            if (options == null || options.isEmpty()) {
                unknown.add(id);
                continue;
            }
            Take take = options.get(0);
            int at = (int) Math.round(cue.optInt("frame") * (double) BattleSimulation.TICK_DT * RATE);
            int end = length;
            int fadeOut = 0;
            if (c + 1 < music.size()) {
                JSONObject next = music.get(c + 1);
                end = (int) Math.round(next.optInt("frame") * (double) BattleSimulation.TICK_DT * RATE);
                fadeOut = next.optInt("fadeOut") * RATE;
            }
            music(clip(id, take, decoded), take.volume(), at, cue.optInt("fadeIn") * RATE, end, fadeOut,
                    cue.optBoolean("loop"), left, right);
            mixed++;
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

    private static float[] clip(String id, Take take, Map<Path, float[]> decoded) throws IOException {
        float[] clip = decoded.get(take.file());
        if (clip == null) {
            if (!Files.exists(take.file())) {
                throw new IOException("sound " + id + " plays " + take.file()
                        + ", which is missing; run gradlew :asset-pipeline:processAudio");
            }
            clip = FfmpegVideo.decodeMono(take.file(), RATE);
            decoded.put(take.file(), clip);
        }
        return clip;
    }

    /** A logged sound's left and right gain at {@code gain}: equal-power, hard over at the view's edge. */
    private static float[] pan(JSONObject start, float gain, float halfWidth) {
        float dx = (float) (start.optDouble("x", 0.0) - start.optDouble("listenerX", 0.0));
        float pan = Math.max(-1f, Math.min(1f, dx / halfWidth));
        double angle = (pan + 1.0) * Math.PI / 4.0;
        return new float[] {gain * (float) Math.cos(angle), gain * (float) Math.sin(angle)};
    }

    /**
     * Adds a music track at the centre: {@code clip} at {@code gain} from output sample {@code at}, faded in over
     * {@code fadeIn} samples, and from {@code end} faded out over {@code fadeOut} samples; nothing sounds past that.
     * A looped track wraps round; otherwise it plays once.
     */
    static void music(float[] clip, float gain, int at, int fadeIn, int end, int fadeOut, boolean loop,
                      float[] left, float[] right) {
        if (clip.length == 0) return;
        float centre = gain * (float) Math.cos(Math.PI / 4.0);
        for (int k = 0; loop || k < clip.length; k++) {
            int i = at + k;
            if (i < 0) continue;
            if (i >= left.length || i >= end + fadeOut) break;
            float env = fadeIn <= 0 ? 1f : Math.min(1f, k / (float) fadeIn);
            if (i >= end) env *= fadeOut <= 0 ? 0f : 1f - (i - end) / (float) fadeOut;
            left[i] += clip[k % clip.length] * centre * env;
            right[i] += clip[k % clip.length] * centre * env;
        }
    }

    /**
     * Adds a held loop: {@code clip} at {@code pitch}, wrapped round, from output sample {@code at} for one tick of
     * {@code samplesPerTick} per gain, each tick at its own gains, faded in and out over {@code fade} samples at the
     * run's ends. Nothing sounds past the run.
     */
    static void hold(float[] clip, float pitch, int at, int samplesPerTick, float[] gainLeft, float[] gainRight,
                     int fade, float[] left, float[] right) {
        if (clip.length == 0 || pitch <= 0f) return;
        int n = gainLeft.length * samplesPerTick;
        for (int k = 0; k < n; k++) {
            int i = at + k;
            if (i < 0) continue;
            if (i >= left.length) break;
            double source = (k * (double) pitch) % clip.length;
            int s = (int) source;
            float frac = (float) (source - s);
            float v = clip[s] + (clip[(s + 1) % clip.length] - clip[s]) * frac;
            float env = fade <= 0 ? 1f : Math.min(1f, Math.min(k, n - 1 - k) / (float) fade);
            int tick = k / samplesPerTick;
            left[i] += v * gainLeft[tick] * env;
            right[i] += v * gainRight[tick] * env;
        }
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
                    if (entry == null || !entry.has("file")) continue;
                    String file = entry.optString("file");
                    String source = entry.optString("source", "");
                    // Music names its directory; packed music (a .bin) is left out.
                    if (source.endsWith(".bin")) continue;
                    Path path = modRoot.resolve(source).resolve(file);
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
