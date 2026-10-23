package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Streaming, dependency-free animated GIF encoder for visual evidence.
 *
 * <p>Lives with the snapshot catalog rather than with any one consumer: the
 * snapshot runner exclusively owns suite output and so must be able to write an
 * animated artifact itself, and the commander-evidence capture writes its own
 * review loop. One encoder, in the module both can reach.
 *
 * <p><b>Frames encode independently.</b> A frame's patch depends only on its own pixels and its predecessor's, so
 * {@link #write} quantises and compresses a whole recording's frames side by side on
 * {@link SnapshotWorkers#encode()} and only concatenates them in order. Writing a recording was a single thread's
 * work per GIF and, once review frames were drawn on the GPU, cost more than drawing them. The bytes are the ones
 * the JDK's ImageIO GIF writer produced for the same frames and metadata, which this replaced: the same
 * stream layout (the first frame's colour table as the global table, interlaced image data) and the same LZW
 * (greedy longest match, codes from 258, the width grown when a code passes it and the table cleared once it
 * holds 4096), so a recording is byte-identical to one written before the change.
 */
public final class AnimatedGifWriter implements AutoCloseable {
    /** Compact battlefield evidence or faithful UI/art colour review. */
    public enum Palette { COMPACT, ADAPTIVE }

    private final Palette palette;
    private final OutputStream output;
    private final int delayCentiseconds;
    private boolean firstFrame = true;
    private byte[] globalColorTable;
    private int[] previousKeys;
    private int previousWidth;
    private int previousHeight;
    private boolean sized;
    private boolean closed;

    public AnimatedGifWriter(Path path, int frameDelayMillis) throws IOException {
        this(path, frameDelayMillis, Palette.COMPACT);
    }

    public AnimatedGifWriter(Path path, int frameDelayMillis, Palette palette) throws IOException {
        if (palette == null) throw new IllegalArgumentException("GIF palette is required");
        this.palette = palette;
        if (frameDelayMillis < 10) {
            throw new IllegalArgumentException(
                    "GIF frame delay must be at least 10 milliseconds");
        }
        Path target = path.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        // Truncate by deleting first, so re-recording a shorter animation over a
        // longer one can never leave the old tail in place (ImageIO, which wrote
        // these before, opened an existing file without truncating it).
        Files.deleteIfExists(target);
        output = new BufferedOutputStream(Files.newOutputStream(target), 1 << 16);
        delayCentiseconds = Math.max(1,
                Math.round(frameDelayMillis / 10f));
    }

    /**
     * Writes {@code frames} as one GIF. With more than one {@linkplain SnapshotWorkers#threads() worker} the frames
     * are keyed, quantised and compressed side by side, a window at a time so a long recording never holds more
     * than a window of decoded frames, and written in order; the file is the one appending them one by one writes.
     */
    public static void write(Path path, List<BufferedImage> frames, int frameDelayMillis, Palette palette)
            throws IOException {
        try (AnimatedGifWriter gif = new AnimatedGifWriter(path, frameDelayMillis, palette)) {
            if (SnapshotWorkers.threads() <= 1) {
                for (BufferedImage frame : frames) gif.append(frame);
                return;
            }
            int window = SnapshotWorkers.threads();
            for (int start = 0; start < frames.size(); start += window) {
                int end = Math.min(frames.size(), start + window);
                List<Callable<Keyed>> keying = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    int index = i;
                    keying.add(() -> {
                        BufferedImage frame = frames.get(index);
                        return new Keyed(keys(frame, palette), frame.getWidth(), frame.getHeight());
                    });
                }
                List<Keyed> keyed = invokeAll(keying);
                // Sizes are checked before anything is encoded against a predecessor of another size.
                for (Keyed frame : keyed) gif.checkSize(frame.width(), frame.height());
                int width = gif.previousWidth, height = gif.previousHeight;
                List<Callable<EncodedFrame>> encoding = new ArrayList<>(end - start);
                for (int i = 0; i < keyed.size(); i++) {
                    int[] keys = keyed.get(i).keys();
                    int[] previous = i == 0 ? gif.previousKeys : keyed.get(i - 1).keys();
                    encoding.add(() -> encode(keys, previous, width, height, palette));
                }
                for (EncodedFrame frame : invokeAll(encoding)) gif.emit(frame);
                gif.previousKeys = keyed.get(keyed.size() - 1).keys();
            }
        }
    }

    public void append(BufferedImage image) throws IOException {
        if (closed) throw new IllegalStateException("GIF writer is closed");
        checkSize(image.getWidth(), image.getHeight());
        int[] keys = keys(image, palette);
        emit(encode(keys, previousKeys, image.getWidth(), image.getHeight(), palette));
        previousKeys = keys;
    }

    private void checkSize(int width, int height) {
        if (closed) throw new IllegalStateException("GIF writer is closed");
        if (sized && (width != previousWidth || height != previousHeight)) {
            throw new IllegalArgumentException("GIF frames must keep one size: "
                    + previousWidth + "x" + previousHeight + " then " + width + "x" + height);
        }
        sized = true;
        previousWidth = width;
        previousHeight = height;
    }

    /**
     * A frame's pixel keys, which identify a pixel's colour for the change test: the cube index in COMPACT mode
     * (so sub-quantum noise never counts as change), the source colour itself in ADAPTIVE mode — where the array
     * doubles as the frame's colours, alpha dropped, since nothing reads the alpha.
     */
    private static int[] keys(BufferedImage image, Palette palette) {
        int[] rgb = FramePixels.argb(image);
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = palette == Palette.ADAPTIVE ? rgb[i] & 0xffffff : cubeIndex(rgb[i]);
        }
        return rgb;
    }

    private record Keyed(int[] keys, int width, int height) { }

    /** One frame's patch, encoded but not yet placed in the stream (its colour table may be the global one). */
    private record EncodedFrame(int left, int top, int width, int height, boolean delta, int transparent,
                                byte[] colorTable, byte[] imageData) { }

    private static EncodedFrame encode(int[] keys, int[] previousKeys, int width, int height, Palette palette) {
        // Bounding box of what changed since the previous frame. Everything
        // outside it is simply not written; inside it, unchanged pixels become
        // the transparent index so LZW sees runs.
        int minX = width, minY = height, maxX = -1, maxY = -1;
        if (previousKeys == null) {
            minX = 0;
            minY = 0;
            maxX = width - 1;
            maxY = height - 1;
        } else {
            for (int y = 0; y < height; y++) {
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    if (keys[row + x] != previousKeys[row + x]) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < 0) { // identical frame: a one-pixel transparent patch keeps the timing
                minX = 0;
                minY = 0;
                maxX = 0;
                maxY = 0;
            }
        }
        boolean delta = previousKeys != null;
        int boxWidth = maxX - minX + 1;
        int boxHeight = maxY - minY + 1;

        byte[] colorTable;
        int transparent;
        int[] lookup = null; // ADAPTIVE: 6-bit bin -> palette index
        if (palette == Palette.ADAPTIVE) {
            Scratch scratch = SCRATCH.get();
            lookup = scratch.lookup;
            int size = adaptivePalette(keys, previousKeys, width, minX, minY, boxWidth, boxHeight, delta,
                    scratch);
            colorTable = colorTable(scratch.reds, scratch.greens, scratch.blues, size);
            transparent = size - 1;
        } else {
            colorTable = CUBE_TABLE;
            transparent = 255;
        }
        byte[] indexed = new byte[boxWidth * boxHeight];
        for (int y = 0, out = 0; y < boxHeight; y++) {
            int row = (minY + y) * width + minX;
            for (int x = 0; x < boxWidth; x++) {
                int at = row + x;
                int sample;
                if (delta && keys[at] == previousKeys[at]) {
                    sample = transparent;
                } else if (palette == Palette.ADAPTIVE) {
                    sample = lookup[bin(keys[at])];
                } else {
                    sample = keys[at];
                }
                indexed[out++] = (byte) sample;
            }
        }
        return new EncodedFrame(minX, minY, boxWidth, boxHeight, delta, transparent, colorTable,
                Lzw.compressInterlaced(indexed, boxWidth, boxHeight));
    }

    /** Places an encoded frame in the stream, in the layout ImageIO's GIF writer gave a sequence. */
    private void emit(EncodedFrame frame) throws IOException {
        OutputStream out = output;
        if (firstFrame) {
            // Header and logical screen: the first frame is whole, so its size is the screen's, and its colour
            // table is the global one.
            globalColorTable = frame.colorTable();
            out.write(new byte[]{'G', 'I', 'F', '8', '9', 'a'});
            writeShort(out, frame.width());
            writeShort(out, frame.height());
            out.write(0x80 | (8 - 1) << 4 | (numBits(globalColorTable.length / 3) - 1));
            out.write(0); // background colour index
            out.write(0); // pixel aspect ratio
            out.write(globalColorTable);
        }
        // Graphic control extension: no disposal, no user input, the delay, and the transparent index
        // (always written, flagged only on a delta frame).
        out.write(0x21);
        out.write(0xf9);
        out.write(4);
        out.write(frame.delta() ? 1 : 0);
        writeShort(out, delayCentiseconds);
        out.write(frame.transparent());
        out.write(0);
        if (firstFrame) {
            // NETSCAPE2.0: loop forever.
            out.write(0x21);
            out.write(0xff);
            out.write(11);
            out.write(new byte[]{'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0'});
            out.write(3);
            out.write(new byte[]{1, 0, 0});
            out.write(0);
        }
        byte[] local = Arrays.equals(globalColorTable, frame.colorTable()) ? null : frame.colorTable();
        out.write(0x2c);
        writeShort(out, frame.left());
        writeShort(out, frame.top());
        writeShort(out, frame.width());
        writeShort(out, frame.height());
        int bits = numBits((local != null ? local : globalColorTable).length / 3);
        out.write((local != null ? 0x80 : 0) | 0x40 /* interlaced */ | (bits - 1));
        if (local != null) out.write(local);
        out.write(frame.imageData());
        firstFrame = false;
    }

    private static void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write(value >> 8 & 0xff);
    }

    private static int numBits(int entries) {
        return Integer.numberOfTrailingZeros(entries);
    }

    /** Interleaved RGB colour table of {@code size} entries padded with the first colour to a power of two. */
    private static byte[] colorTable(byte[] reds, byte[] greens, byte[] blues, int size) {
        int entries = size <= 2 ? 2 : Integer.highestOneBit(size - 1) << 1;
        byte[] table = new byte[3 * entries];
        for (int i = 0; i < entries; i++) {
            int from = i < size ? i : 0;
            table[3 * i] = reds[from];
            table[3 * i + 1] = greens[from];
            table[3 * i + 2] = blues[from];
        }
        return table;
    }

    private static <T> List<T> invokeAll(List<Callable<T>> tasks) throws IOException {
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) futures.add(SnapshotWorkers.encode().submit(task));
        List<T> results = new ArrayList<>(tasks.size());
        Throwable failure = null;
        for (Future<T> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException thrown) {
                if (failure == null) failure = thrown.getCause(); else failure.addSuppressed(thrown.getCause());
            } catch (InterruptedException thrown) {
                Thread.currentThread().interrupt();
                if (failure == null) failure = thrown;
            }
        }
        if (failure instanceof IOException io) throw io;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IOException("GIF frame encoding failed", failure);
        return results;
    }

    private static final int BINS = 1 << 18;

    private static int bin(int rgb) {
        return ((rgb >> 18 & 0x3f) << 12) | ((rgb >> 10 & 0x3f) << 6) | (rgb >> 2 & 0x3f);
    }

    private static int cubeIndex(int rgb) {
        return level((rgb >> 16) & 0xff) * LEVELS * LEVELS
                + level((rgb >> 8) & 0xff) * LEVELS + level(rgb & 0xff);
    }

    /** Per-thread working arrays of the adaptive palette: 7 MB a frame that need not be allocated a frame. */
    private static final class Scratch {
        final long[] sumR = new long[BINS];
        final long[] sumG = new long[BINS];
        final long[] sumB = new long[BINS];
        final int[] count = new int[BINS];
        final int[] lookup = new int[BINS];
        final byte[] reds = new byte[256];
        final byte[] greens = new byte[256];
        final byte[] blues = new byte[256];
        int[] used = new int[1024];
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * Median-cut palette (at most 255 colours plus a last transparent entry)
     * over the pixels this frame actually writes, at 6 bits per channel. A
     * frame that changed little gets a palette spent entirely on what changed,
     * and the pixels left transparent keep their earlier colour exactly.
     * Fills {@code scratch.lookup} with the palette index of every bin it saw and
     * {@code scratch.reds/greens/blues} with the palette, and returns its size.
     */
    private static int adaptivePalette(int[] rgb, int[] previousKeys, int width, int minX,
                                       int minY, int boxWidth, int boxHeight,
                                       boolean delta, Scratch scratch) {
        long[] sumR = scratch.sumR;
        long[] sumG = scratch.sumG;
        long[] sumB = scratch.sumB;
        int[] count = scratch.count;
        int[] used = scratch.used;
        int usedCount = 0;
        for (int y = 0; y < boxHeight; y++) {
            int row = (minY + y) * width + minX;
            for (int x = 0; x < boxWidth; x++) {
                int at = row + x;
                if (delta && rgb[at] == previousKeys[at]) continue;
                int c = rgb[at];
                int bin = bin(c);
                if (count[bin]++ == 0) {
                    if (usedCount == used.length) used = scratch.used = Arrays.copyOf(used, usedCount * 2);
                    used[usedCount++] = bin;
                }
                sumR[bin] += (c >> 16) & 0xff;
                sumG[bin] += (c >> 8) & 0xff;
                sumB[bin] += c & 0xff;
            }
        }
        // Median cut over the occupied bins, splitting the box whose widest
        // channel spread (weighted by nothing) is largest, at the weighted median.
        // Each box's widest channel is measured once, when it is made; ties go
        // to the earlier box and, within a box, to the earlier channel.
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(Arrays.copyOf(used, usedCount)));
        int[][] buckets = new int[64][];
        int[] bucketSizes = new int[64];
        while (boxes.size() < 255) {
            int pick = -1;
            int best = 0;
            for (int i = 0; i < boxes.size(); i++) {
                Box box = boxes.get(i);
                if (box.bins.length >= 2 && box.range > best) {
                    best = box.range;
                    pick = i;
                }
            }
            if (pick < 0) break;
            Box box = boxes.remove(pick);
            int channel = box.channel;
            // A stable counting sort on the channel's 64 values: the order a stable comparison sort gives.
            Arrays.fill(bucketSizes, 0);
            for (int bin : box.bins) {
                int v = channelOf(bin, channel) >> 2;
                int[] bucket = buckets[v];
                if (bucket == null) bucket = buckets[v] = new int[16];
                else if (bucketSizes[v] == bucket.length) bucket = buckets[v] = Arrays.copyOf(bucket, bucket.length * 2);
                bucket[bucketSizes[v]++] = bin;
            }
            int[] sorted = new int[box.bins.length];
            for (int v = 0, at = 0; v < 64; v++) {
                if (bucketSizes[v] > 0) System.arraycopy(buckets[v], 0, sorted, at, bucketSizes[v]);
                at += bucketSizes[v];
            }
            long total = 0;
            for (int bin : sorted) total += count[bin];
            long running = 0;
            int cut = 1;
            for (int i = 0; i < sorted.length - 1; i++) {
                running += count[sorted[i]];
                cut = i + 1;
                if (running * 2 >= total) break;
            }
            boxes.add(new Box(Arrays.copyOfRange(sorted, 0, cut)));
            boxes.add(new Box(Arrays.copyOfRange(sorted, cut, sorted.length)));
        }
        int size = boxes.size() + 1; // last entry is the transparent index
        byte[] reds = scratch.reds;
        byte[] greens = scratch.greens;
        byte[] blues = scratch.blues;
        Arrays.fill(reds, (byte) 0);
        Arrays.fill(greens, (byte) 0);
        Arrays.fill(blues, (byte) 0);
        int[] lookup = scratch.lookup;
        for (int i = 0; i < boxes.size(); i++) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int bin : boxes.get(i).bins) {
                r += sumR[bin];
                g += sumG[bin];
                b += sumB[bin];
                n += count[bin];
                lookup[bin] = i;
            }
            if (n == 0) continue; // identical frame: nothing to colour
            reds[i] = (byte) (r / n);
            greens[i] = (byte) (g / n);
            blues[i] = (byte) (b / n);
        }
        // Leave the sums clear for the next frame on this thread; the lookup is
        // only ever read for bins this frame filled in.
        for (int i = 0; i < usedCount; i++) {
            int bin = used[i];
            sumR[bin] = 0;
            sumG[bin] = 0;
            sumB[bin] = 0;
            count[bin] = 0;
        }
        return size;
    }

    /** A median-cut box: its bins, and its widest channel with that channel's spread. */
    private static final class Box {
        final int[] bins;
        final int channel;
        final int range;

        Box(int[] bins) {
            this.bins = bins;
            int bestChannel = 0, bestRange = 0;
            for (int channel = 0; channel < 3; channel++) {
                int lo = 255, hi = 0;
                for (int bin : bins) {
                    int v = channelOf(bin, channel);
                    if (v < lo) lo = v;
                    if (v > hi) hi = v;
                }
                if (hi - lo > bestRange) {
                    bestRange = hi - lo;
                    bestChannel = channel;
                }
            }
            this.channel = bestChannel;
            this.range = bestRange;
        }
    }

    /** One 6-bit channel of a bin, scaled back to 0..255. */
    private static int channelOf(int bin, int channel) {
        int v = channel == 0 ? bin >> 12 & 0x3f : channel == 1 ? bin >> 6 & 0x3f : bin & 0x3f;
        return v << 2;
    }

    /**
     * The nearest cube level. The palette's levels sit at {@code i * 255 / (LEVELS - 1)}, so a
     * channel rounds to the closest of them; splitting 0..255 into six equal bins instead put
     * the bin edges between the levels, sending 0..42 to black and tipping a neutral dark grey
     * whose green was a few points higher into pure green.
     */
    static int level(int channel) {
        return (channel * (LEVELS - 1) + 127) / 255;
    }

    /** Levels per channel in the shared colour cube. Six gives 216 entries and leaves room to spare in a 256-entry GIF palette. */
    private static final int LEVELS = 6;

    /** The cube, then spare black slots whose last one is the transparent index. */
    private static final byte[] CUBE_TABLE = buildCubeTable();

    private static byte[] buildCubeTable() {
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];
        for (int r = 0; r < LEVELS; r++) {
            for (int g = 0; g < LEVELS; g++) {
                for (int b = 0; b < LEVELS; b++) {
                    int index = r * LEVELS * LEVELS + g * LEVELS + b;
                    reds[index] = (byte) (r * 255 / (LEVELS - 1));
                    greens[index] = (byte) (g * 255 / (LEVELS - 1));
                    blues[index] = (byte) (b * 255 / (LEVELS - 1));
                }
            }
        }
        return colorTable(reds, greens, blues, 256);
    }

    /**
     * GIF LZW with 8-bit roots, over a patch's rows in interlaced order (every eighth row from 0, every eighth
     * from 4, every fourth from 2, every second from 1), packed least significant bit first into 255-byte
     * sub-blocks: the minimum code size byte, the sub-blocks, and the zero-length terminator.
     */
    static final class Lzw {
        private static final int CLEAR = 256;
        private static final int END = 257;
        private static final int MAX_CODES = 4096;

        /** {@code (prefix << 8 | byte) -> stamp << 12 | code}; a stale stamp is an empty slot, so clearing is free. */
        private static final ThreadLocal<int[]> TABLE = ThreadLocal.withInitial(() -> new int[MAX_CODES << 8]);
        private static final ThreadLocal<int[]> STAMP = ThreadLocal.withInitial(() -> new int[1]);

        private final int[] table = TABLE.get();
        private final int[] stamp = STAMP.get();
        private final ByteArrayOutputStream out;
        private final byte[] block = new byte[256];
        private int blockLength;
        private int bitBuffer;
        private int bitCount;
        private int codeSize;
        private int nextCode;
        private int prefix = -1;

        private Lzw(int capacity) {
            out = new ByteArrayOutputStream(capacity);
            out.write(8);
            reset();
            writeCode(CLEAR);
        }

        static byte[] compressInterlaced(byte[] indexed, int width, int height) {
            Lzw lzw = new Lzw(indexed.length / 4 + 64);
            for (int pass = 0; pass < 4; pass++) {
                int first = pass == 0 ? 0 : pass == 1 ? 4 : pass == 2 ? 2 : 1;
                int step = pass == 0 ? 8 : pass == 1 ? 8 : pass == 2 ? 4 : 2;
                for (int y = first; y < height; y += step) lzw.compress(indexed, y * width, width);
            }
            return lzw.finish();
        }

        private void reset() {
            if (++stamp[0] >= 1 << 19) {
                Arrays.fill(table, 0);
                stamp[0] = 1;
            }
            codeSize = 9;
            nextCode = 258;
        }

        private void compress(byte[] data, int offset, int length) {
            int[] table = this.table;
            int current = stamp[0] << 12;
            for (int i = offset, end = offset + length; i < end; i++) {
                int c = data[i] & 0xff;
                if (prefix < 0) {
                    prefix = c;
                    continue;
                }
                int key = prefix << 8 | c;
                int entry = table[key];
                if ((entry & ~0xfff) == current) {
                    prefix = entry & 0xfff;
                    continue;
                }
                writeCode(prefix);
                if (nextCode < MAX_CODES) {
                    table[key] = current | nextCode;
                    if (nextCode++ == (1 << codeSize) && codeSize < 12) codeSize++;
                    // Matches the JDK: the width grows once the code just assigned passes the current limit.
                } else {
                    writeCode(CLEAR);
                    reset();
                    current = stamp[0] << 12;
                }
                prefix = c;
            }
        }

        private void writeCode(int code) {
            bitBuffer |= code << bitCount;
            bitCount += codeSize;
            while (bitCount >= 8) {
                block[blockLength++] = (byte) bitBuffer;
                bitBuffer >>>= 8;
                bitCount -= 8;
                if (blockLength == 255) flushBlock();
            }
        }

        private void flushBlock() {
            out.write(blockLength);
            out.write(block, 0, blockLength);
            blockLength = 0;
        }

        private byte[] finish() {
            if (prefix >= 0) writeCode(prefix);
            writeCode(END);
            if (bitCount > 0) {
                block[blockLength++] = (byte) bitBuffer;
                bitBuffer = 0;
                bitCount = 0;
                if (blockLength == 255) flushBlock();
            }
            if (blockLength > 0) flushBlock();
            out.write(0);
            return out.toByteArray();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            output.write(0x3b);
        } finally {
            output.close();
        }
    }
}
