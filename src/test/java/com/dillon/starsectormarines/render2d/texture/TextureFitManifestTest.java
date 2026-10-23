package com.dillon.starsectormarines.render2d.texture;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TextureFitManifestTest {

    @Test
    void anUnlistedImageShipsAsItself() throws Exception {
        TextureFitManifest manifest = TextureFitManifest.parse(
                "{\"textures\": {\"graphics/a.png\": {\"w\": 3072, \"h\": 768, \"file\": \"graphics/a.png\"}}}");
        assertNull(manifest.entry("graphics/b.png"));
        assertEquals("graphics/b.png", manifest.file("graphics/b.png"));
        assertEquals(3072, manifest.entry("graphics/a.png").width());
    }

    @Test
    void aReencodedImageKeepsItsSourceNameAndLoadsTheShippedFile() throws Exception {
        TextureFitManifest manifest = TextureFitManifest.parse(
                "{\"textures\": {\"graphics/ui/plate.png\": {\"w\": 2048, \"h\": 413, \"file\": \"graphics/ui/plate.jpg\"}}}");
        assertEquals("graphics/ui/plate.jpg", manifest.file("graphics/ui/plate.png"));
    }

    @Test
    void aFittedImageDecodesAtItsLogicalSize() {
        BufferedImage shipped = new BufferedImage(48, 12, BufferedImage.TYPE_INT_ARGB);
        BufferedImage logical = ModTextures.toLogicalSize(shipped, 96, 24);
        assertEquals(96, logical.getWidth());
        assertEquals(24, logical.getHeight());
        assertSame(shipped, ModTextures.toLogicalSize(shipped, 48, 12));
    }
}
