package com.dillon.starsectormarines.tools.snapshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A lossless record of an artifact's frames, and the comparison of two such records.
 *
 * <p>A GIF is palettised, so two runs compared through their GIFs can disagree where the pictures agree, and agree
 * where they differ by less than a palette step. A renderer change accepted "on pixels" has to be read before the
 * palette, which is what this keeps: every frame as ARGB, deflated, in {@code <artifact>.frames}. Written only when
 * {@code -Dsnapshot.rawFrames=<dir>} is set; {@link #main} compares two such directories.
 */
public final class RawFrameDump {

    /** System property naming the directory the raw frames go under. */
    public static final String PROPERTY = "snapshot.rawFrames";

    private static final int MAGIC = 0x52415746; // RAWF

    private RawFrameDump() { }

    /** Writes {@code frames} to {@code artifactPath + ".frames"}. */
    public static void write(Path artifactPath, List<BufferedImage> frames) throws IOException {
        Path target = Path.of(artifactPath + ".frames");
        Files.createDirectories(target.getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            out.writeInt(MAGIC);
            out.writeInt(frames.size());
            for (BufferedImage frame : frames) {
                int width = frame.getWidth(), height = frame.getHeight();
                int[] argb = FramePixels.argb(frame);
                ByteBuffer bytes = ByteBuffer.allocate(argb.length * 4);
                bytes.asIntBuffer().put(argb);
                Deflater deflater = new Deflater(Deflater.BEST_SPEED);
                deflater.setInput(bytes.array());
                deflater.finish();
                ByteArrayOutputStream packed = new ByteArrayOutputStream(argb.length / 4 + 64);
                byte[] chunk = new byte[1 << 16];
                while (!deflater.finished()) packed.write(chunk, 0, deflater.deflate(chunk));
                deflater.end();
                out.writeInt(width);
                out.writeInt(height);
                out.writeInt(packed.size());
                packed.writeTo(out);
            }
        }
    }

    /** One frame of a record, as packed ARGB. */
    public record Frame(int width, int height, int[] argb) { }

    public static List<Frame> read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("not a raw frame record: " + file);
            int count = in.readInt();
            List<Frame> frames = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int width = in.readInt(), height = in.readInt(), length = in.readInt();
                byte[] packed = in.readNBytes(length);
                byte[] raw = new byte[width * height * 4];
                Inflater inflater = new Inflater();
                inflater.setInput(packed);
                try {
                    int filled = 0;
                    while (filled < raw.length && !inflater.finished()) {
                        filled += inflater.inflate(raw, filled, raw.length - filled);
                    }
                } catch (DataFormatException failure) {
                    throw new IOException("corrupt frame " + i + " in " + file, failure);
                } finally {
                    inflater.end();
                }
                int[] argb = new int[width * height];
                ByteBuffer.wrap(raw).asIntBuffer().get(argb);
                frames.add(new Frame(width, height, argb));
            }
            return frames;
        }
    }

    /**
     * {@code before after [sideBySideOut]}: compares every record under {@code before} with its namesake under
     * {@code after} and prints, per artifact, the frames compared, the pixels that differ, and the maximum and mean
     * absolute channel difference (0-255, over every channel of every pixel). With a third argument, writes the
     * worst frame overall as before | after | difference (amplified eight times) to that PNG.
     */
    public static void main(String[] args) throws IOException {
        Path before = Path.of(args[0]), after = Path.of(args[1]);
        Path sideBySide = args.length > 2 ? Path.of(args[2]) : null;
        List<Path> records;
        try (Stream<Path> walk = Files.walk(before)) {
            records = walk.filter(p -> p.toString().endsWith(".frames")).sorted().toList();
        }
        int worstMax = -1; Frame worstBefore = null, worstAfter = null; String worstName = null;
        long totalChannels = 0, totalDiff = 0, totalPixels = 0, totalChanged = 0; int globalMax = 0;
        for (Path record : records) {
            Path relative = before.relativize(record);
            Path other = after.resolve(relative.toString());
            if (!Files.isRegularFile(other)) { System.out.println("MISSING " + relative); continue; }
            List<Frame> a = read(record), b = read(other);
            if (a.size() != b.size()) {
                System.out.printf(Locale.ROOT, "FRAME COUNT %s: %d vs %d%n", relative, a.size(), b.size());
            }
            int frames = Math.min(a.size(), b.size());
            long changed = 0, pixels = 0, diffSum = 0; int max = 0;
            for (int f = 0; f < frames; f++) {
                Frame x = a.get(f), y = b.get(f);
                if (x.width() != y.width() || x.height() != y.height()) {
                    System.out.println("SIZE MISMATCH " + relative + " frame " + f); continue;
                }
                int frameMax = 0;
                for (int i = 0; i < x.argb().length; i++) {
                    int p = x.argb()[i], q = y.argb()[i];
                    pixels++;
                    if (p == q) continue;
                    changed++;
                    for (int shift = 0; shift <= 24; shift += 8) {
                        int d = Math.abs((p >>> shift & 255) - (q >>> shift & 255));
                        diffSum += d;
                        if (d > frameMax) frameMax = d;
                    }
                }
                if (frameMax > max) max = frameMax;
                if (frameMax > worstMax) { worstMax = frameMax; worstBefore = x; worstAfter = y; worstName = relative + " frame " + f; }
            }
            totalChannels += pixels * 4; totalDiff += diffSum; totalPixels += pixels; totalChanged += changed;
            globalMax = Math.max(globalMax, max);
            System.out.printf(Locale.ROOT, "%-70s frames=%4d changedPx=%10d (%.4f%%) max=%3d mean=%.5f%n",
                    relative, frames, changed, pixels == 0 ? 0 : 100.0 * changed / pixels, max,
                    pixels == 0 ? 0 : diffSum / (pixels * 4.0));
        }
        System.out.printf(Locale.ROOT, "TOTAL records=%d pixels=%d changedPx=%d (%.4f%%) max=%d mean=%.6f worst=%s%n",
                records.size(), totalPixels, totalChanged, totalPixels == 0 ? 0 : 100.0 * totalChanged / totalPixels,
                globalMax, totalChannels == 0 ? 0 : totalDiff / (double) totalChannels, worstName);
        if (sideBySide != null && worstBefore != null) {
            int w = worstBefore.width(), h = worstBefore.height();
            BufferedImage out = new BufferedImage(w * 3, h, BufferedImage.TYPE_INT_RGB);
            out.setRGB(0, 0, w, h, worstBefore.argb(), 0, w);
            out.setRGB(w, 0, w, h, worstAfter.argb(), 0, w);
            for (int i = 0; i < worstBefore.argb().length; i++) {
                int p = worstBefore.argb()[i], q = worstAfter.argb()[i], rgb = 0;
                for (int shift = 0; shift <= 16; shift += 8) {
                    int d = Math.min(255, 8 * Math.abs((p >>> shift & 255) - (q >>> shift & 255)));
                    rgb |= d << shift;
                }
                out.setRGB(2 * w + i % w, i / w, rgb);
            }
            Files.createDirectories(sideBySide.toAbsolutePath().getParent());
            if (!ImageIO.write(out, "PNG", sideBySide.toFile())) throw new UncheckedIOException(new IOException("no PNG writer"));
        }
    }
}
