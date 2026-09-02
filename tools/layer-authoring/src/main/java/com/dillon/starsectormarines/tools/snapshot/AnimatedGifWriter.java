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
import java.util.Iterator;

/**
 * Streaming, dependency-free animated GIF encoder for visual evidence.
 *
 * <p>Lives with the snapshot catalog rather than with any one consumer: the
 * snapshot runner exclusively owns suite output and so must be able to write an
 * animated artifact itself, and the commander-evidence capture writes its own
 * review loop. One encoder, in the module both can reach.
 */
public final class AnimatedGifWriter implements AutoCloseable {
    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final ImageOutputStream output;
    private final int delayCentiseconds;
    private boolean firstFrame = true;
    private boolean closed;

    public AnimatedGifWriter(Path path, int frameDelayMillis) throws IOException {
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
        BufferedImage indexed = quantize(image);
        ImageTypeSpecifier type = ImageTypeSpecifier
                .createFromRenderedImage(indexed);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);
        configure(metadata, firstFrame);
        writer.writeToSequence(new IIOImage(indexed, null, metadata), params);
        firstFrame = false;
    }

    /**
     * Map a frame onto one fixed palette by nearest colour, with no dithering.
     *
     * <p>Handing truecolour frames straight to the GIF writer costs far more
     * than it looks: the writer quantises each frame to its own palette and
     * error-diffuses, which turns a flat expanse of ground into per-pixel noise
     * and leaves LZW nothing to compress. A battle review recorded that way ran
     * about two bytes per pixel — larger than the raw indexed frames. Sharing
     * one palette across every frame and refusing to dither keeps flat regions
     * flat, which is exactly the run structure LZW needs. Nearest-colour
     * banding is an acceptable trade in an offline review artifact.
     */
    private static BufferedImage quantize(BufferedImage source) {
        BufferedImage indexed = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_BYTE_INDEXED, PALETTE);
        WritableRaster raster = indexed.getRaster();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                raster.setSample(x, y, 0,
                        level(r) * LEVELS * LEVELS + level(g) * LEVELS + level(b));
            }
        }
        return indexed;
    }

    private static int level(int channel) {
        return Math.min(LEVELS - 1, channel * LEVELS / 256);
    }

    /** Levels per channel in the shared colour cube. Six gives 216 entries and leaves room to spare in a 256-entry GIF palette. */
    private static final int LEVELS = 6;

    private static final IndexColorModel PALETTE = buildPalette();

    private static IndexColorModel buildPalette() {
        int size = LEVELS * LEVELS * LEVELS;
        byte[] reds = new byte[size];
        byte[] greens = new byte[size];
        byte[] blues = new byte[size];
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
        return new IndexColorModel(8, size, reds, greens, blues);
    }

    private void configure(IIOMetadata metadata, boolean loop) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(delayCentiseconds));
        control.setAttribute("transparentColorIndex", "0");
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
