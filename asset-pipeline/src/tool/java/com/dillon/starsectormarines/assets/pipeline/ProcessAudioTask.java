package com.dillon.starsectormarines.assets.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
            String recipe = ffmpeg.version() + "\n" + String.join("\n", options);
            try {
                AudioEncodeCache.Result result = cache.materialize(source, output, recipe, (input, temporary) -> {
                    List<String> command = new ArrayList<>(List.of(ffmpeg.path(), "-y", "-loglevel", "error",
                            "-i", input.toString()));
                    command.addAll(options);
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
        if (processing == AudioManifest.Processing.DIALOGUE) {
            // Match alternating speakers without modifying the retained clean generation takes.
            options.addAll(List.of("-af", "loudnorm=I=-20:TP=-2:LRA=7"));
        }
        return List.copyOf(options);
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
