package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The pictures a block is chosen by, and the moment they stop being true.
 *
 * <p>Both the decoded atlas and the thumbnails cut from it are cached, because
 * a list cell renderer asks for them on every repaint and decoding a sheet per
 * row per paint would stall the window. That cache is right until the atlas is
 * re-exported underneath it.
 *
 * <p>Getting it wrong is worse than showing an old picture. The catalog is
 * re-read after an export, so the coordinates are new while the cached image is
 * old — and the room preview, which is drawn from coordinates rather than
 * cached whole, then reads the new cells out of the previous atlas and shows
 * art from somewhere else on the sheet.
 */
public class BlockPreviewTest {

    private static final String SHEET = "graphics/tilesets/probe.png";

    /** A one-cell sheet of a single flat colour, written where a block would find it. */
    private static void writeSheet(Path root, Color colour) throws IOException {
        Path file = root.resolve("mod");
        for (String part : SHEET.split("/")) file = file.resolve(part);
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        ImageIO.write(image, "PNG", file.toFile());
    }

    private static GridBlockDef block() {
        return new GridBlockDef("probe.block", SHEET, 16, 0, 0, GridLayout.SINGLE, null);
    }

    private static int centre(BufferedImage image) {
        return image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
    }

    @Test
    void aRepackedAtlasIsRedrawnOnceThePreviewIsToldToForget(@TempDir Path root) throws Exception {
        writeSheet(root, Color.RED);
        BlockPreview previews = new BlockPreview(root);

        BufferedImage before = previews.patch(block(), 48);
        assertNotNull(before);
        assertEquals(Color.RED.getRGB(), centre(before));

        // What an export does: the same path, different pixels.
        writeSheet(root, Color.BLUE);
        assertEquals(Color.RED.getRGB(), centre(previews.patch(block(), 48)),
                "the cache is doing its job until it is told the sheet changed");

        previews.forget();
        assertEquals(Color.BLUE.getRGB(), centre(previews.patch(block(), 48)),
                "after forgetting, the picture is the sheet as it now is");
    }

    /** The room view is drawn from the atlas too, so it goes stale the same way. */
    @Test
    void theRoomViewIsRedrawnToo(@TempDir Path root) throws Exception {
        writeSheet(root, Color.RED);
        BlockPreview previews = new BlockPreview(root);

        BufferedImage before = previews.room(block(), 24);
        assertNotNull(before, "a single-cell block should still draw as a room");

        writeSheet(root, Color.BLUE);
        previews.forget();

        BufferedImage after = previews.room(block(), 24);
        assertNotNull(after);
        assertNotEquals(centre(before), centre(after),
                "the room preview reads the atlas by coordinate, so a repack must be re-read "
                        + "or it draws new cells out of the old sheet");
    }
}
