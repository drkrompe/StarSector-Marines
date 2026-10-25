package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Video through the {@code ffmpeg} on {@code PATH} (or named by {@code $FFMPEG}, as the audio pipeline finds it):
 * frames streamed in as raw BGR while they render and encoded to H.264, so a 30 s 1920x1080 recording at 30 fps is
 * tens of megabytes where a full-colour GIF of it was near a gigabyte; audio decoded to PCM; and a soundtrack muxed
 * beside the picture.
 *
 * <p>The encode is deterministic: x264 is bit-exact for the same input and settings at any thread count, and the
 * container is written without creation times or encoder tags, so the same frames give the same file.
 */
public final class FfmpegVideo implements AutoCloseable {

    /** {@code null} until probed; empty when no ffmpeg answers. */
    private static volatile String executable;

    private final Process process;
    private final OutputStream stdin;
    private final Path errors;
    private final Path output;
    private final int width;
    private final int height;
    private final byte[] row;
    private boolean finished;

    private FfmpegVideo(Process process, Path errors, Path output, int width, int height) {
        this.process = process;
        this.stdin = new BufferedOutputStream(process.getOutputStream(), 1 << 20);
        this.errors = errors;
        this.output = output;
        this.width = width;
        this.height = height;
        this.row = new byte[width * 3];
    }

    /** The ffmpeg to run, or {@code null} when neither {@code $FFMPEG} nor {@code PATH} has one. */
    public static String executable() {
        String found = executable;
        if (found == null) {
            String override = System.getenv("FFMPEG");
            found = override != null && !override.isBlank() && probe(override) ? override
                    : probe("ffmpeg") ? "ffmpeg" : "";
            executable = found;
        }
        return found.isEmpty() ? null : found;
    }

    private static boolean probe(String path) {
        try {
            Process process = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Starts an H.264 encode to {@code output} ({@code yuv420p}, {@code +faststart}, so any player opens it) of
     * {@code width} x {@code height} frames at {@code frameRate}, an ffmpeg rate such as {@code "30/1"}.
     */
    public static FfmpegVideo open(Path output, int width, int height, String frameRate) throws IOException {
        String ffmpeg = requireExecutable();
        Path errors = Files.createTempFile(output.toAbsolutePath().getParent(), "ffmpeg-", ".log");
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "rawvideo", "-pix_fmt", "bgr24", "-video_size", width + "x" + height,
                "-framerate", frameRate, "-i", "pipe:0", "-an",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p"));
        command.addAll(BIT_EXACT);
        command.add(output.toString());
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(errors.toFile())
                .start();
        return new FfmpegVideo(process, errors, output, width, height);
    }

    /** Container flags that keep the file a function of its streams alone. */
    private static final List<String> BIT_EXACT = List.of("-map_metadata", "-1", "-fflags", "+bitexact",
            "-flags:v", "+bitexact", "-flags:a", "+bitexact", "-movflags", "+faststart");

    /** Encodes the next frame; it must be {@code width} x {@code height}. */
    public void add(BufferedImage frame) throws IOException {
        if (frame.getWidth() != width || frame.getHeight() != height) {
            throw new IllegalArgumentException("frame is " + frame.getWidth() + "x" + frame.getHeight()
                    + ", video is " + width + "x" + height);
        }
        if (frame.getType() == BufferedImage.TYPE_3BYTE_BGR
                && frame.getRaster().getDataBuffer() instanceof DataBufferByte bytes
                && bytes.getData().length == width * height * 3) {
            stdin.write(bytes.getData());
            return;
        }
        int[] argb = FramePixels.argb(frame);
        for (int y = 0, i = 0; y < height; y++) {
            for (int x = 0, o = 0; x < width; x++, i++) {
                int p = argb[i];
                row[o++] = (byte) p;
                row[o++] = (byte) (p >> 8);
                row[o++] = (byte) (p >> 16);
            }
            stdin.write(row);
        }
    }

    /** Closes the stream and waits for the encode; throws with ffmpeg's own words if it failed. */
    public Path finish() throws IOException {
        finished = true;
        try {
            stdin.close();
        } catch (IOException closed) {
            // A dead encoder reports itself below.
        }
        await(process, errors, "encode " + output);
        return output;
    }

    @Override
    public void close() {
        if (!finished) process.destroyForcibly();
        try {
            Files.deleteIfExists(errors);
        } catch (IOException ignored) {
            // A log left in build/tmp is harmless.
        }
    }

    /**
     * Decodes {@code audio} to mono 32-bit float samples at {@code sampleRate}, as the game's positional playback
     * hears a clip.
     */
    public static float[] decodeMono(Path audio, int sampleRate) throws IOException {
        Process process = new ProcessBuilder(requireExecutable(), "-hide_banner", "-loglevel", "error",
                "-i", audio.toString(), "-f", "f32le", "-ac", "1", "-ar", Integer.toString(sampleRate), "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        byte[] raw = process.getInputStream().readAllBytes();
        try {
            if (process.waitFor() != 0) throw new IOException("ffmpeg could not decode " + audio);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted decoding " + audio, e);
        }
        float[] samples = new float[raw.length / 4];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples);
        return samples;
    }

    /** Writes {@code video}'s picture with {@code audio} as its soundtrack (AAC) to {@code output}. */
    public static void mux(Path video, Path audio, Path output) throws IOException {
        Path errors = Files.createTempFile(output.toAbsolutePath().getParent(), "ffmpeg-", ".log");
        try {
            List<String> command = new ArrayList<>(List.of(requireExecutable(), "-hide_banner", "-loglevel",
                    "error", "-y", "-i", video.toString(), "-i", audio.toString(), "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest"));
            command.addAll(BIT_EXACT);
            command.add(output.toString());
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(errors.toFile())
                    .start();
            process.getOutputStream().close();
            await(process, errors, "mux " + output);
        } finally {
            Files.deleteIfExists(errors);
        }
    }

    private static void await(Process process, Path errors, String what) throws IOException {
        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted: " + what, e);
        }
        if (exit != 0) {
            String said = Files.exists(errors) ? Files.readString(errors, StandardCharsets.UTF_8).strip() : "";
            throw new IOException("ffmpeg could not " + what + " (exit " + exit + ")"
                    + (said.isEmpty() ? "" : ": " + said));
        }
    }

    private static String requireExecutable() throws IOException {
        String ffmpeg = executable();
        if (ffmpeg == null) throw new IOException("ffmpeg not found on PATH or via FFMPEG");
        return ffmpeg;
    }
}
