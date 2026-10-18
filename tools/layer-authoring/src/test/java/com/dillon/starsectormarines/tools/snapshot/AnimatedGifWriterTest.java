package com.dillon.starsectormarines.tools.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedGifWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAReadableMultiFrameLoop() throws Exception {
        Path gif = tempDir.resolve("review.gif");
        try (AnimatedGifWriter writer = new AnimatedGifWriter(gif, 120)) {
            writer.append(frame(Color.RED));
            writer.append(frame(Color.GREEN));
            writer.append(frame(Color.BLUE));
        }

        assertTrue(gif.toFile().length() > 0);
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(gif.toFile())) {
            reader.setInput(input);
            assertEquals(3, reader.getNumImages(true));
            assertEquals(Color.RED.getRGB(), reader.read(0).getRGB(4, 4));
            assertEquals(Color.GREEN.getRGB(), reader.read(1).getRGB(4, 4));
            assertEquals(Color.BLUE.getRGB(), reader.read(2).getRGB(4, 4));
        } finally {
            reader.dispose();
        }
    }

    @Test
    void rerecordingAShorterLoopReplacesTheLongerOne() throws Exception {
        Path gif = tempDir.resolve("review.gif");
        try (AnimatedGifWriter writer = new AnimatedGifWriter(gif, 120)) {
            for (int i = 0; i < 12; i++) writer.append(frame(Color.RED));
        }
        long longRun = gif.toFile().length();

        try (AnimatedGifWriter writer = new AnimatedGifWriter(gif, 120)) {
            writer.append(frame(Color.BLUE));
        }

        // ImageIO opens an existing file for random access without truncating
        // it, so without an explicit delete the short recording is written over
        // the head of the long one and the file keeps its old length. Every
        // attempt to measure a smaller or shorter animation then reports the
        // stale size.
        assertTrue(gif.toFile().length() < longRun,
                "re-recording must not leave the previous loop's tail behind");
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(gif.toFile())) {
            reader.setInput(input);
            assertEquals(1, reader.getNumImages(true));
            assertEquals(Color.BLUE.getRGB(), reader.read(0).getRGB(4, 4));
        } finally {
            reader.dispose();
        }
    }

    @Test
    void adaptiveReviewPreservesDarkNeutralGradients() throws Exception {
        BufferedImage gradient = new BufferedImage(128, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 8; y++) {
                gradient.setRGB(x, y, new Color(x, x + 3, x + 1).getRGB());
            }
        }
        Path gif = tempDir.resolve("shading.gif");
        try (AnimatedGifWriter writer = new AnimatedGifWriter(gif, 100,
                AnimatedGifWriter.Palette.ADAPTIVE)) {
            writer.append(gradient);
        }
        BufferedImage decoded = ImageIO.read(gif.toFile());
        for (int x = 0; x < 128; x++) {
            Color actual = new Color(decoded.getRGB(x, 4));
            assertTrue(Math.abs(actual.getRed() - x) <= 3, "red shading at " + x);
            assertTrue(Math.abs(actual.getGreen() - x - 3) <= 3, "green shading at " + x);
            assertTrue(Math.abs(actual.getBlue() - x - 1) <= 3, "blue shading at " + x);
        }
    }

    @Test
    void mostlyStaticSequenceEncodesAsSmallDeltasAndDecodesToTheSamePixels() throws Exception {
        for (AnimatedGifWriter.Palette palette : AnimatedGifWriter.Palette.values()) {
            int frames = 12;
            BufferedImage[] sequence = new BufferedImage[frames];
            for (int f = 0; f < frames; f++) {
                BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 256; y++) {
                    for (int x = 0; x < 256; x++) {
                        // busy but fixed background: LZW cannot flatten it
                        image.setRGB(x, y, new Color((x * 7) & 0xff, (y * 13) & 0xff,
                                (x * y) & 0xff).getRGB());
                    }
                }
                Graphics2D g = image.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(10 + f * 4, 20, 6, 6);
                g.dispose();
                sequence[f] = image;
            }
            Path gif = tempDir.resolve("delta-" + palette + ".gif");
            try (AnimatedGifWriter writer = new AnimatedGifWriter(gif, 100, palette)) {
                for (BufferedImage image : sequence) writer.append(image);
            }
            Path single = tempDir.resolve("single-" + palette + ".gif");
            try (AnimatedGifWriter writer = new AnimatedGifWriter(single, 100, palette)) {
                writer.append(sequence[0]);
            }
            assertTrue(gif.toFile().length() < single.toFile().length() * 3,
                    palette + ": twelve frames must cost far less than twelve whole frames, was "
                            + gif.toFile().length() + " vs one frame " + single.toFile().length());

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            ImageReader reader = readers.next();
            try (ImageInputStream input = ImageIO.createImageInputStream(gif.toFile())) {
                reader.setInput(input);
                assertEquals(frames, reader.getNumImages(true));
                for (int f = 0; f < frames; f++) {
                    BufferedImage decoded = reader.read(f);
                    // composite the delta over the previous canvas the way a viewer does
                    if (f == 0) canvas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = canvas.createGraphics();
                    org.w3c.dom.Node descriptor = ((javax.imageio.metadata.IIOMetadataNode)
                            reader.getImageMetadata(f).getAsTree("javax_imageio_gif_image_1.0"))
                            .getElementsByTagName("ImageDescriptor").item(0);
                    int left = Integer.parseInt(descriptor.getAttributes()
                            .getNamedItem("imageLeftPosition").getNodeValue());
                    int top = Integer.parseInt(descriptor.getAttributes()
                            .getNamedItem("imageTopPosition").getNodeValue());
                    g.drawImage(decoded, left, top, null);
                    g.dispose();
                    for (int y = 0; y < 256; y += 5) {
                        for (int x = 0; x < 256; x += 3) {
                            Color expected = new Color(sequence[f].getRGB(x, y));
                            Color actual = new Color(canvas.getRGB(x, y));
                            int tolerance = palette == AnimatedGifWriter.Palette.COMPACT ? 52 : 48;
                            assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                                            && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                                            && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance,
                                    palette + " frame " + f + " at " + x + "," + y);
                        }
                    }
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage canvas;

    private static BufferedImage frame(Color color) {
        BufferedImage image = new BufferedImage(
                8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    @Test
    void aCubeChannelRoundsToItsNearestLevelSoGreyStaysGrey() {
        // A neutral UI grey once split across the bin edge: red and blue to 0, green to 51.
        assertEquals(1, AnimatedGifWriter.level(42));
        assertEquals(1, AnimatedGifWriter.level(46));
        assertEquals(0, AnimatedGifWriter.level(25));
        assertEquals(5, AnimatedGifWriter.level(255));
        for (int level = 0; level <= 5; level++) {
            assertEquals(level, AnimatedGifWriter.level(level * 51), "a palette colour maps to itself");
        }
    }
}
