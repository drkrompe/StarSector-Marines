package com.dillon.starsectormarines.tools.snapshot;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Streaming, dependency-free animated GIF encoder for visual evidence.
 *
 * <p>Lives with the snapshot catalog rather than with any one consumer: the
 * snapshot runner exclusively owns suite output and so must be able to write an
 * animated artifact itself, and the commander-evidence capture writes its own
 * review loop. One encoder, in the module both can reach.
 */
public final class AnimatedGifWriter implements AutoCloseable {
    /** Compact battlefield evidence or faithful UI/art colour review. */
    public enum Palette { COMPACT, ADAPTIVE }

    private final Palette palette;
    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final ImageOutputStream output;
    private final int delayCentiseconds;
    private boolean firstFrame = true;
    private int[] previousKeys;
    private int previousWidth;
    private int previousHeight;
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
        // Truncate by deleting first. ImageIO opens an existing file for
        // random access without truncating it, so re-recording a shorter
        // animation over a longer one leaves the old tail in place: the file
        // keeps the byte length of the largest run ever written, and every
        // attempt to measure the effect of fewer or smaller frames reports the
        // stale size instead.
        Files.deleteIfExists(target);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No GIF ImageIO writer is installed");
        }
        writer = writers.next();
        params = writer.getDefaultWriteParam();
        output = ImageIO.createImageOutputStream(target.toFile());
        if (output == null) {
            writer.dispose();
            throw new IOException("Could not open GIF output " + target);
        }
        delayCentiseconds = Math.max(1,
                Math.round(frameDelayMillis / 10f));
        writer.setOutput(output);
        writer.prepareWriteSequence(null);
    }

    public void append(BufferedImage image) throws IOException {
        if (closed) throw new IllegalStateException("GIF writer is closed");
        int width = image.getWidth();
        int height = image.getHeight();
        if (previousKeys != null && (width != previousWidth || height != previousHeight)) {
            throw new IllegalArgumentException("GIF frames must keep one size: "
                    + previousWidth + "x" + previousHeight + " then " + width + "x" + height);
        }
        int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
        // A key identifies a pixel's colour for the change test: the cube index in
        // COMPACT mode (so sub-quantum noise never counts as change), the source
        // colour itself in ADAPTIVE mode.
        int[] keys = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            keys[i] = palette == Palette.ADAPTIVE ? rgb[i] & 0xffffff : cubeIndex(rgb[i]);
        }
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

        IndexColorModel model;
        int[] lookup = null; // ADAPTIVE: 6-bit bin -> palette index
        if (palette == Palette.ADAPTIVE) {
            lookup = new int[BINS];
            model = adaptivePalette(rgb, keys, width, minX, minY, boxWidth, boxHeight, delta, lookup);
        } else {
            model = PALETTE;
        }
        int transparent = model.getMapSize() - 1;
        BufferedImage indexed = new BufferedImage(boxWidth, boxHeight,
                BufferedImage.TYPE_BYTE_INDEXED, model);
        WritableRaster raster = indexed.getRaster();
        for (int y = 0; y < boxHeight; y++) {
            int row = (minY + y) * width + minX;
            for (int x = 0; x < boxWidth; x++) {
                int at = row + x;
                int sample;
                if (delta && keys[at] == previousKeys[at]) {
                    sample = transparent;
                } else if (palette == Palette.ADAPTIVE) {
                    sample = lookup[bin(rgb[at])];
                } else {
                    sample = keys[at];
                }
                raster.setSample(x, y, 0, sample);
            }
        }
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(indexed);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);
        configure(metadata, firstFrame, delta, transparent, minX, minY);
        writer.writeToSequence(new IIOImage(indexed, null, metadata), params);
        firstFrame = false;
        previousKeys = keys;
        previousWidth = width;
        previousHeight = height;
    }

    private static final int BINS = 1 << 18;

    private static int bin(int rgb) {
        return ((rgb >> 18 & 0x3f) << 12) | ((rgb >> 10 & 0x3f) << 6) | (rgb >> 2 & 0x3f);
    }

    private static int cubeIndex(int rgb) {
        return level((rgb >> 16) & 0xff) * LEVELS * LEVELS
                + level((rgb >> 8) & 0xff) * LEVELS + level(rgb & 0xff);
    }

    /**
     * Median-cut palette (at most 255 colours plus a last transparent entry)
     * over the pixels this frame actually writes, at 6 bits per channel. A
     * frame that changed little gets a palette spent entirely on what changed,
     * and the pixels left transparent keep their earlier colour exactly.
     * Fills {@code lookup} with the palette index of every bin it saw.
     */
    private IndexColorModel adaptivePalette(int[] rgb, int[] keys, int width, int minX,
                                            int minY, int boxWidth, int boxHeight,
                                            boolean delta, int[] lookup) {
        long[] sumR = new long[BINS];
        long[] sumG = new long[BINS];
        long[] sumB = new long[BINS];
        int[] count = new int[BINS];
        int[] used = new int[1024];
        int usedCount = 0;
        for (int y = 0; y < boxHeight; y++) {
            int row = (minY + y) * width + minX;
            for (int x = 0; x < boxWidth; x++) {
                int at = row + x;
                if (delta && keys[at] == previousKeys[at]) continue;
                int c = rgb[at];
                int bin = bin(c);
                if (count[bin]++ == 0) {
                    if (usedCount == used.length) used = Arrays.copyOf(used, usedCount * 2);
                    used[usedCount++] = bin;
                }
                sumR[bin] += (c >> 16) & 0xff;
                sumG[bin] += (c >> 8) & 0xff;
                sumB[bin] += c & 0xff;
            }
        }
        // Median cut over the occupied bins, splitting the box whose widest
        // channel spread (weighted by nothing) is largest, at the weighted median.
        List<int[]> boxes = new ArrayList<>();
        boxes.add(Arrays.copyOf(used, usedCount));
        while (boxes.size() < 255) {
            int pick = -1;
            int best = 0;
            int pickChannel = 0;
            for (int i = 0; i < boxes.size(); i++) {
                int[] box = boxes.get(i);
                if (box.length < 2) continue;
                for (int channel = 0; channel < 3; channel++) {
                    int lo = 255, hi = 0;
                    for (int bin : box) {
                        int v = channelOf(bin, channel);
                        if (v < lo) lo = v;
                        if (v > hi) hi = v;
                    }
                    if (hi - lo > best) {
                        best = hi - lo;
                        pick = i;
                        pickChannel = channel;
                    }
                }
            }
            if (pick < 0) break;
            int[] box = boxes.remove(pick);
            final int channel = pickChannel;
            int[] sorted = Arrays.stream(box).boxed()
                    .sorted((p, q) -> Integer.compare(channelOf(p, channel), channelOf(q, channel)))
                    .mapToInt(Integer::intValue).toArray();
            long total = 0;
            for (int bin : sorted) total += count[bin];
            long running = 0;
            int cut = 1;
            for (int i = 0; i < sorted.length - 1; i++) {
                running += count[sorted[i]];
                cut = i + 1;
                if (running * 2 >= total) break;
            }
            boxes.add(Arrays.copyOfRange(sorted, 0, cut));
            boxes.add(Arrays.copyOfRange(sorted, cut, sorted.length));
        }
        int size = boxes.size() + 1; // last entry is the transparent index
        byte[] reds = new byte[size];
        byte[] greens = new byte[size];
        byte[] blues = new byte[size];
        for (int i = 0; i < boxes.size(); i++) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int bin : boxes.get(i)) {
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
        return new IndexColorModel(8, size, reds, greens, blues);
    }

    /** One 6-bit channel of a bin, scaled back to 0..255. */
    private static int channelOf(int bin, int channel) {
        int v = channel == 0 ? bin >> 12 & 0x3f : channel == 1 ? bin >> 6 & 0x3f : bin & 0x3f;
        return v << 2;
    }

    private static int level(int channel) {
        return Math.min(LEVELS - 1, channel * LEVELS / 256);
    }

    /** Levels per channel in the shared colour cube. Six gives 216 entries and leaves room to spare in a 256-entry GIF palette. */
    private static final int LEVELS = 6;

    private static final IndexColorModel PALETTE = buildPalette();

    private static IndexColorModel buildPalette() {
        int size = LEVELS * LEVELS * LEVELS;
        // 256 entries: the cube, then spare slots whose last one is the transparent index.
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
        return new IndexColorModel(8, 256, reds, greens, blues);
    }

    private void configure(IIOMetadata metadata, boolean loop, boolean delta, int transparent,
                           int left, int top) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", delta ? "TRUE" : "FALSE");
        control.setAttribute("delayTime", Integer.toString(delayCentiseconds));
        control.setAttribute("transparentColorIndex", Integer.toString(transparent));
        IIOMetadataNode descriptor = child(root, "ImageDescriptor");
        descriptor.setAttribute("imageLeftPosition", Integer.toString(left));
        descriptor.setAttribute("imageTopPosition", Integer.toString(top));
        if (loop) {
            IIOMetadataNode extensions = child(root, "ApplicationExtensions");
            IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
            extension.setAttribute("applicationID", "NETSCAPE");
            extension.setAttribute("authenticationCode", "2.0");
            extension.setUserObject(new byte[]{1, 0, 0});
            extensions.appendChild(extension);
        }
        metadata.setFromTree(format, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int index = 0; index < root.getLength(); index++) {
            Node node = root.item(index);
            if (name.equals(node.getNodeName())) {
                return (IIOMetadataNode) node;
            }
        }
        IIOMetadataNode result = new IIOMetadataNode(name);
        root.appendChild(result);
        return result;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try {
            // Whatever the trailer does, the stream holds the file: on Windows
            // an unclosed one is a lock, and the caller's own cleanup then
            // fails with "the process cannot access the file" over whatever
            // actually went wrong. So the close is owed even to an unchecked
            // failure out of the encoder.
            try {
                writer.endWriteSequence();
            } catch (IOException ex) {
                failure = ex;
            }
        } finally {
            try {
                output.close();
            } catch (IOException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            } finally {
                writer.dispose();
            }
        }
        if (failure != null) throw failure;
    }
}
