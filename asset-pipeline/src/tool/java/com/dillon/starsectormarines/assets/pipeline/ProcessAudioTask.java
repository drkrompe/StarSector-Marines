package com.dillon.starsectormarines.assets.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Build-time task that transcodes WAV / OGG source clips listed in {@code audio.json} into
 * Ogg Vorbis files Starsector can load. Mirrors {@link ProcessModelsTask}'s shape: a JavaExec
 * Gradle task feeds {@code (inputDir, outputDir)} and we walk the manifest.
 *
 * <p>Usage: {@code java ProcessAudioTask <inputDir> <outputDir>}
 *
 * <p>{@code inputDir} must contain {@code audio.json}. Source paths in the manifest are resolved
 * relative to that dir. {@code outputDir} is the root that {@link AudioManifest.AudioClip#out()}
 * is resolved against — point it at {@code mod/sounds/}.
 *
 * <p>ffmpeg is required on PATH (or via the {@code FFMPEG} env var); on Windows,
 * {@code winget install Gyan.FFmpeg} works. Per-clip channel count is driven by
 * {@link AudioManifest.Kind}: UI/music → stereo, positional → mono.
 *
 * <p>Incremental: a clip is skipped if its output exists and is newer than both the source file
 * and the manifest. Touch {@code audio.json} or the source to force a re-encode.
 */
public final class ProcessAudioTask {

    // Global args that go BEFORE -i (overwrite + quiet logging).
    private static final String[] FFMPEG_GLOBAL_ARGS = { "-y", "-loglevel", "error" };

    // Output args that go AFTER -i <source> but before the output path.
    // -vn strips any cover-art video stream; -map_metadata -1 drops tags so output is reproducible.
    private static final String[] FFMPEG_OUTPUT_BASE_ARGS = { "-vn", "-map_metadata", "-1" };

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.err.println("Usage: ProcessAudioTask <inputDir> <outputDir>");
            System.exit(1);
        }

        Path inputDir = Path.of(args[0]).toAbsolutePath();
        Path outputDir = Path.of(args[1]).toAbsolutePath();
        Path manifestPath = inputDir.resolve("audio.json");

        if (!Files.isRegularFile(manifestPath)) {
            System.err.println("Manifest not found: " + manifestPath);
            System.err.println("Create audio.json in " + inputDir + " — see AudioManifest javadoc for shape.");
            System.exit(1);
        }

        AudioManifest manifest = AudioManifest.load(manifestPath);
        long manifestMtime = Files.getLastModifiedTime(manifestPath).toMillis();

        Files.createDirectories(outputDir);

        int success = 0, failed = 0, skipped = 0;
        String ffmpeg = null;  // Lazily resolved — only fail on a missing ffmpeg if a clip actually needs encoding.

        System.out.printf("Found %d clip(s) in %s%n", manifest.clips().size(), manifestPath.getFileName());

        for (AudioManifest.AudioClip clip : manifest.clips()) {
            Path sourceFile = inputDir.resolve(clip.source());
            if (!Files.isRegularFile(sourceFile)) {
                System.err.printf("  [%s] FAILED: source not found: %s%n", clip.id(), sourceFile);
                failed++;
                continue;
            }

            String outSubdir = clip.out() == null ? "" : clip.out();
            Path outputFile = outputDir.resolve(outSubdir).resolve(clip.id() + ".ogg");

            long sourceMtime = Files.getLastModifiedTime(sourceFile).toMillis();
            if (Files.exists(outputFile)
                    && Files.getLastModifiedTime(outputFile).toMillis() >= Math.max(sourceMtime, manifestMtime)) {
                skipped++;
                continue;
            }

            if (ffmpeg == null) {
                ffmpeg = resolveFfmpeg();
                if (ffmpeg == null) {
                    System.err.println("ffmpeg not found on PATH or via $FFMPEG env var.");
                    System.err.println("Install it (e.g. `winget install Gyan.FFmpeg`) and re-run.");
                    System.exit(2);
                }
            }

            Files.createDirectories(outputFile.getParent());

            int channels = switch (clip.kind()) {
                case POSITIONAL -> 1;
                case UI, MUSIC  -> 2;
            };

            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpeg);
            for (String a : FFMPEG_GLOBAL_ARGS) cmd.add(a);
            cmd.add("-i"); cmd.add(sourceFile.toString());
            for (String a : FFMPEG_OUTPUT_BASE_ARGS) cmd.add(a);
            cmd.add("-ac"); cmd.add(Integer.toString(channels));
            cmd.add("-ar"); cmd.add("44100");
            cmd.add("-c:a"); cmd.add("libvorbis");
            cmd.add("-q:a"); cmd.add("5");          // ~160 kbps stereo / ~96 kbps mono — matches vanilla feel
            cmd.add(outputFile.toString());

            System.out.printf("  [%s] %s -> %s (%dch)%n",
                    clip.id(), clip.source(), outputDir.relativize(outputFile), channels);

            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).inheritIO().start();
            int exit = proc.waitFor();
            if (exit != 0) {
                System.err.printf("  [%s] ffmpeg exited with code %d%n", clip.id(), exit);
                failed++;
            } else {
                success++;
            }
        }

        System.out.printf("%nDone! %d encoded, %d failed, %d up-to-date%n", success, failed, skipped);
        if (failed > 0) System.exit(1);
    }

    /** Returns an ffmpeg path that works, or null if none is reachable. */
    private static String resolveFfmpeg() {
        String env = System.getenv("FFMPEG");
        if (env != null && !env.isBlank() && probe(env)) return env;
        if (probe("ffmpeg")) return "ffmpeg";
        return null;
    }

    private static boolean probe(String path) {
        try {
            Process p = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
