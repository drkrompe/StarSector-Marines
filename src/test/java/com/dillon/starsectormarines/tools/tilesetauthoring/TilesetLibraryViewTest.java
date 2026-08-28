package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thumbnails are read to pick a sheet by eye. They are read on a background
 * thread and from files a couple of megabytes each, so the decode has to stay
 * cheap and has to survive a file that is not really an image.
 */
class TilesetLibraryViewTest {

    private static Path sheet(Path dir, String name, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x40, 0x80, 0xC0));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillRect(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        Path path = dir.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    @Test
    void everySheetLandsInTheSameCellWhateverItsShape(@TempDir Path dir) throws Exception {
        // A square plate and a wide strip have to be comparable side by side.
        BufferedImage square = TilesetLibraryView.readThumbnail(sheet(dir, "square.png", 1254, 1254));
        BufferedImage strip = TilesetLibraryView.readThumbnail(sheet(dir, "strip.png", 2172, 724));

        assertNotNull(square);
        assertNotNull(strip);
        assertEquals(square.getWidth(), strip.getWidth(), "thumbnails share a cell width");
        assertEquals(square.getHeight(), strip.getHeight(), "thumbnails share a cell height");
        assertTrue(square.getWidth() <= 128 && square.getHeight() <= 128,
                "a thumbnail is a thumbnail: " + square.getWidth() + "x" + square.getHeight());
    }

    @Test
    void theArtIsActuallyInTheThumbnailNotJustTheLetterbox() throws Exception {
        Path dir = Files.createTempDirectory("thumb");
        BufferedImage thumb = TilesetLibraryView.readThumbnail(sheet(dir, "sheet.png", 512, 512));

        assertNotNull(thumb);
        int centre = thumb.getRGB(thumb.getWidth() / 2, thumb.getHeight() / 2);
        assertEquals(255, centre >>> 24, "the middle of the sheet should be drawn, not blank");
    }

    @Test
    void aSmallSheetIsNotSubsampledIntoNothing(@TempDir Path dir) throws Exception {
        // The subsampling step must never round to zero on a sheet smaller than
        // the thumbnail, which would make the reader throw rather than upscale.
        BufferedImage thumb = TilesetLibraryView.readThumbnail(sheet(dir, "tiny.png", 32, 16));

        assertNotNull(thumb);
        assertTrue(thumb.getWidth() > 0 && thumb.getHeight() > 0);
    }

    @Test
    void aFileThatIsNotAnImageIsSkippedRatherThanThrown(@TempDir Path dir) throws Exception {
        // The strip is built on a background thread; one bad file must not take
        // the rest of the library with it.
        Path notAnImage = dir.resolve("broken.png");
        Files.writeString(notAnImage, "this is not a png", StandardCharsets.UTF_8);

        assertNull(TilesetLibraryView.readThumbnail(notAnImage));
        assertNull(TilesetLibraryView.readThumbnail(dir.resolve("absent.png")));
    }
}
