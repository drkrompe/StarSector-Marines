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

    private static BufferedImage frame(Color color) {
        BufferedImage image = new BufferedImage(
                8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }
}
