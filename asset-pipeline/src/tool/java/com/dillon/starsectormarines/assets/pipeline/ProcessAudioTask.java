package com.dillon.starsectormarines.assets.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transcodes manifest clips to game-ready Ogg Vorbis, reusing content-addressed encodes.
 * Usage: {@code ProcessAudioTask <inputDir> <outputDir> [cacheDir]}.
 * The input directory contains {@code audio.json}; output normally points at {@code mod/sounds}.
 * The shared cache belongs outside the shipped mod. Its key includes source bytes, ffmpeg's
 * version/build description, and effective output arguments, never manifest or file timestamps.
 * Requires ffmpeg on PATH or via {@code FFMPEG}, with librubberband for AIR_COMMS.
 */
public final class ProcessAudioTask {
    private record Ffmpeg(String path, String version) { }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: ProcessAudioTask <inputDir> <outputDir> [cacheDir]");
        }
        Path inputDir = Path.of(args[0]).toAbsolutePath();
        Path outputDir = Path.of(args[1]).toAbsolutePath();
        Path cacheDir = args.length == 3 ? Path.of(args[2])
                : Path.of(System.getProperty("user.home"), ".gradle", "caches", "starsector-marines", "audio-v1");
        AudioManifest manifest = AudioManifest.load(inputDir.resolve("audio.json"));
        Ffmpeg ffmpeg = resolveFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("ffmpeg not found on PATH or via FFMPEG. Install it and re-run.");
        }
        AudioEncodeCache cache = new AudioEncodeCache(cacheDir);
        int encoded = 0, failed = 0, restored = 0, skipped = 0;
        System.out.printf("Found %d clip(s) in audio.json%n", manifest.clips().size());
        for (AudioManifest.AudioClip clip : manifest.clips()) {
            Path source = inputDir.resolve(clip.source());
            Path output = outputDir.resolve(clip.out() == null ? "" : clip.out()).resolve(clip.id() + ".ogg");
            List<String> options = outputArguments(clip.kind(), clip.processing());
            Double target = loudnessTarget(clip);
            // The gain is measured inside the encode, so the key names the target rather than the gain.
            String recipe = ffmpeg.version() + "\n" + String.join("\n", options)
                    + (target == null ? "" : "\nlevel " + target + " LUFS");
            try {
                AudioEncodeCache.Result result = cache.materialize(source, output, recipe, (input, temporary) -> {
                    List<String> command = new ArrayList<>(List.of(ffmpeg.path(), "-y", "-loglevel", "error",
                            "-i", input.toString()));
                    command.addAll(target == null ? options
                            : levelled(options, target - measureLoudness(ffmpeg, input, options)));
                    command.add(temporary.toString());
                    System.out.printf("  [%s] encoding %s%n", clip.id(), clip.source());
                    Process process = new ProcessBuilder(command).inheritIO().start();
                    int exit;
                    try {
                        exit = process.waitFor();
                    } catch (InterruptedException e) {
                        process.destroyForcibly().waitFor();
                        throw e;
                    }
                    if (exit != 0) throw new IOException("ffmpeg exited with code " + exit);
                });
                switch (result) {
                    case ENCODED -> encoded++;
                    case RESTORED -> restored++;
                    case UP_TO_DATE -> skipped++;
                }
            } catch (IOException e) {
                System.err.printf("  [%s] FAILED: %s%n", clip.id(), e.getMessage());
                failed++;
            }
        }
        System.out.printf("%nDone! %d encoded, %d failed, %d restored from cache, %d up-to-date%n",
                encoded, failed, restored, skipped);
        if (failed > 0) throw new IOException(failed + " audio clip(s) failed");
    }

    static List<String> outputArguments(AudioManifest.Kind kind) {
        return outputArguments(kind, null);
    }

    static List<String> outputArguments(AudioManifest.Kind kind, AudioManifest.Processing processing) {
        String channels = switch (kind) {
            case POSITIONAL -> "1";
            case UI, MUSIC -> "2";
        };
        // Strip artwork and metadata; Vorbis quality 5 matches the existing game mix.
        List<String> options = new ArrayList<>(List.of("-vn", "-map_metadata", "-1", "-ac", channels,
                "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5"));
        if (processing == AudioManifest.Processing.COMMS) {
            // One reusable radio recipe, separate from clean source takes. No pitch/time changes.
            options.addAll(List.of("-af", "highpass=f=280:p=2,lowpass=f=3600:p=2,"
                    + "acompressor=threshold=0.125:ratio=3:attack=5:release=90:makeup=1.5,"
                    + "asoftclip=type=tanh:threshold=0.65:oversample=2,volume=2.5,"
                    + "alimiter=limit=0.89:level=false:latency=true"));
        }
        if (processing == AudioManifest.Processing.AIR_COMMS) {
            // Lower the pilot one semitone (2^(-1/12)) without slowing his phrase timing.
            // Narrow cockpit radio, with a driven microphone.
            // Filter again after saturation so its harmonics do not restore the clean high end.
            options.addAll(List.of("-af", "rubberband=tempo=1:pitch=0.943874313:channels=together,"
                    + "highpass=f=420:p=2,lowpass=f=2500:p=2,"
                    + "acompressor=threshold=0.06:ratio=6:attack=3:release=70:makeup=3,"
                    + "asoftclip=type=tanh:threshold=0.38:oversample=4,volume=3,"
                    + "lowpass=f=2500:p=2,alimiter=limit=0.85:level=false:latency=true"));
        }
        if (processing == AudioManifest.Processing.ORBITAL_COMMS) {
            // A station across orbit: narrower than the cockpit band, harder driven, with digital
            // grit and a faint constant noise floor (turned down twice at the owner's ear). Gain makes up what the narrow band removes; band-limit
            // again so the grit and floor stay inside it.
            // No pitch change; the cast voice stays the one that was cast. The noise is written per
            // channel on a fixed stereo layout: aeval's single shared expression (c=same) with
            // random() crashes FFmpeg 8.1 on real takes.
            options.addAll(List.of("-af", "aformat=channel_layouts=stereo,highpass=f=500:p=2,lowpass=f=2200:p=2,"
                    + "acompressor=threshold=0.05:ratio=8:attack=2:release=60:makeup=3,"
                    + "asoftclip=type=tanh:threshold=0.3:oversample=4,acrusher=bits=12:mode=log:aa=1,"
                    + "aeval='val(0)+0.001*(random(0)*2-1)|val(1)+0.001*(random(1)*2-1)':c=stereo,volume=3,"
                    + "highpass=f=500:p=2,lowpass=f=2200:p=2,alimiter=limit=0.85:level=false:latency=true"));
        }
        if (processing == AudioManifest.Processing.SHIP_PA) {
            // A ship's loudspeaker heard from outside the hull: narrower than the intercom, driven,
            // with two short slap echoes, loudness-matched to the comms takes (about -19 LUFS).
            options.addAll(List.of("-af", "highpass=f=450:p=2,lowpass=f=2600:p=2,equalizer=f=1600:t=q:w=1:g=6,"
                    + "acompressor=threshold=0.06:ratio=6:attack=3:release=70:makeup=3,"
                    + "asoftclip=type=tanh:threshold=0.35:oversample=4,lowpass=f=2800:p=2,"
                    + "aecho=0.8:0.5:31|67:0.3|0.18,loudnorm=I=-19:TP=-1.5:LRA=7"));
        }
        if (processing == AudioManifest.Processing.DIALOGUE) {
            // Match alternating speakers without modifying the retained clean generation takes.
            options.addAll(List.of("-af", "loudnorm=I=-20:TP=-2:LRA=7"));
        }
        return List.copyOf(options);
    }

    /** Story dialogue, every speaker and treatment, played at full volume. */
    static final double STORY_VOICE_LUFS = -19;
    /** Operation net and officer, played at 0.8 in sounds.json, so heard at about -21: under the story. */
    static final double MISSION_VOICE_LUFS = -19;
    /** Music, played at 0.525 in sounds.json, so heard at about -23.6: a bed under every voice. */
    static final double MUSIC_LUFS = -18;
    /** Ceiling after the leveling gain, about -1 dBFS. */
    private static final String LEVEL_LIMITER = "alimiter=limit=0.891:level=false:latency=true";

    /**
     * The integrated loudness a clip is encoded to, after its treatment, or null to keep the
     * source's level. Barks are leveled in sounds.json by their own tool; effects keep their
     * authored levels, which the code playing them is tuned against.
     */
    static Double loudnessTarget(AudioManifest.AudioClip clip) {
        if (clip.mix() != null) return switch (clip.mix()) {
            case STORY_VOICE -> STORY_VOICE_LUFS;
            case MISSION_VOICE -> MISSION_VOICE_LUFS;
        };
        return clip.kind() == AudioManifest.Kind.MUSIC ? MUSIC_LUFS : null;
    }

    /** {@code options} with a gain of {@code gainDb} and a peak ceiling after any treatment. */
    static List<String> levelled(List<String> options, double gainDb) {
        String level = String.format(Locale.ROOT, "volume=%.2fdB,%s", gainDb, LEVEL_LIMITER);
        List<String> result = new ArrayList<>(options);
        int filter = result.indexOf("-af");
        if (filter < 0) {
            result.addAll(List.of("-af", level));
        } else {
            result.set(filter + 1, result.get(filter + 1) + "," + level);
        }
        return List.copyOf(result);
    }

    /** The integrated loudness from an ebur128 log: the last {@code I:} line, which is the summary. */
    static double integratedLoudness(String log) throws IOException {
        Matcher matcher = INTEGRATED.matcher(log);
        Double found = null;
        while (matcher.find()) found = Double.parseDouble(matcher.group(1));
        // ebur128 reports silence as -70, the gate; leveling it would only amplify noise.
        if (found == null || found <= -70) throw new IOException("No integrated loudness in ffmpeg output");
        return found;
    }

    private static final Pattern INTEGRATED = Pattern.compile("I:\\s+(-?[0-9.]+) LUFS");

    /** Measures the clip as it would be encoded: same channels, rate and treatment. */
    private static double measureLoudness(Ffmpeg ffmpeg, Path input, List<String> options)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(ffmpeg.path(), "-nostats", "-i", input.toString()));
        String chain = "ebur128";
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            if (option.equals("-ac") || option.equals("-ar")) command.addAll(options.subList(i, i + 2));
            if (option.equals("-af")) chain = options.get(i + 1) + ",ebur128";
        }
        command.addAll(List.of("-af", chain, "-f", "null", "-"));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException("ffmpeg loudness measurement failed");
        return integratedLoudness(log);
    }

    private static Ffmpeg resolveFfmpeg() throws InterruptedException {
        String override = System.getenv("FFMPEG");
        if (override != null && !override.isBlank()) {
            Ffmpeg found = probe(override);
            if (found != null) return found;
        }
        return probe("ffmpeg");
    }

    private static Ffmpeg probe(String path) throws InterruptedException {
        try {
            Process process = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? new Ffmpeg(path, version) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
