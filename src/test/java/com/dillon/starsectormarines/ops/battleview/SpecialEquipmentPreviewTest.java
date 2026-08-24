package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data-driven equipment gallery: catalog doll on the left, composed sample
 * soldier on the right. Both read the same definition the live renderer uses.
 */
class SpecialEquipmentPreviewTest {

    private static final Path OUTPUT = Paths.get("build", "equipment-previews",
            "special-equipment-contact.png");
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 260;
    private static final float SAMPLE_SHOULDERS = 122f;

    @Test
    void writeDataDrivenEquipmentAndSampleSoldierGallery() throws Exception {
        int count = SpecialEquipmentRegistry.installed().size();
        BufferedImage contact = new BufferedImage(PANEL_WIDTH * 2,
                PANEL_HEIGHT * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = contact.createGraphics();
        configure(g);

        int index = 0;
        for (SpecialEquipmentDef def : SpecialEquipmentRegistry.installed().all()) {
            int x = index % 2 * PANEL_WIDTH;
            int y = index / 2 * PANEL_HEIGHT;
            renderPanel(g, def, x, y);
            index++;
        }
        g.dispose();

        Files.createDirectories(OUTPUT.getParent());
        ImageIO.write(contact, "PNG", OUTPUT.toFile());
        assertEquals(4, count);
        assertTrue(Files.size(OUTPUT) > 10_000L, "preview should contain rendered source art");
    }

    private static void renderPanel(Graphics2D g, SpecialEquipmentDef def,
                                    int x, int y) throws Exception {
        g.setColor(new Color(0x0D, 0x14, 0x1D));
        g.fillRect(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(new Color(0x28, 0x3A, 0x4A));
        g.drawRect(x, y, PANEL_WIDTH - 1, PANEL_HEIGHT - 1);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(new Color(0xE2, 0xEC, 0xF4));
        g.drawString(def.displayName(), x + 18, y + 25);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(new Color(0x76, 0xB9, 0xD4));
        g.drawString(def.id() + " · " + def.presentation().preview().state(),
                x + 18, y + 44);

        BufferedImage icon = load(def.presentation().armoryIconPath());
        drawContained(g, icon, x + 18, y + 58, 205, 182);

        float actorX = x + 382f;
        float actorY = y + 156f;
        BufferedImage body = load("graphics/battle/marine-modular-topdown/variants/armor/army-green/body.png");
        BufferedImage head = load("graphics/battle/marine-modular-topdown/variants/armor/army-green/head.png");
        EquipmentLayerDef layer = def.presentation().carrierLayer();
        boolean using = "using".equals(def.presentation().preview().state());
        EquipmentLayerComposer.Placement placement = layer != null
                ? EquipmentLayerComposer.resolve(layer, using,
                        def.presentation().preview().phase(), 0f, 0f,
                        SAMPLE_SHOULDERS, 0f)
                : null;
        BufferedImage equipment = layer != null ? load(layer.spritePath()) : null;

        if (placement != null
                && placement.occlusion() == EquipmentLayerDef.Occlusion.UNDER_BODY) {
            drawPlacement(g, equipment, placement, actorX, actorY);
        }
        float bodyScale = SAMPLE_SHOULDERS / 150f;
        drawImage(g, body, actorX, actorY + 0.12f * SAMPLE_SHOULDERS,
                body.getWidth() * bodyScale, body.getHeight() * bodyScale, 0f);
        if (placement != null
                && placement.occlusion() == EquipmentLayerDef.Occlusion.OVER_BODY) {
            drawPlacement(g, equipment, placement, actorX, actorY);
        }
        drawImage(g, head, actorX, actorY - 0.08f * SAMPLE_SHOULDERS,
                head.getWidth() * bodyScale, head.getHeight() * bodyScale, 0f);

        g.setColor(new Color(0x8E, 0xA5, 0xB5));
        g.drawString("EQUIPMENT DOLL", x + 18, y + 253);
        g.drawString("SAMPLE SOLDIER", x + 326, y + 253);
    }

    private static void drawPlacement(Graphics2D g, BufferedImage image,
                                      EquipmentLayerComposer.Placement placement,
                                      float actorX, float actorY) {
        drawImage(g, image, actorX + placement.centerX(), actorY - placement.centerY(),
                placement.width(), placement.height(), -placement.angleDegrees());
    }

    private static BufferedImage load(String relativePath) throws Exception {
        BufferedImage image = ImageIO.read(Paths.get("mod").resolve(relativePath).toFile());
        if (image == null) throw new IllegalStateException("Could not read " + relativePath);
        return image;
    }

    private static void drawContained(Graphics2D g, BufferedImage image,
                                      int x, int y, int width, int height) {
        float scale = Math.min(width / (float) image.getWidth(),
                height / (float) image.getHeight());
        drawImage(g, image, x + width * 0.5f, y + height * 0.5f,
                image.getWidth() * scale, image.getHeight() * scale, 0f);
    }

    private static void drawImage(Graphics2D g, BufferedImage image,
                                  float centerX, float centerY,
                                  float width, float height, float angleDegrees) {
        AffineTransform old = g.getTransform();
        g.translate(centerX, centerY);
        g.rotate(Math.toRadians(angleDegrees));
        g.scale(width / image.getWidth(), height / image.getHeight());
        g.drawImage(image, -image.getWidth() / 2, -image.getHeight() / 2, null);
        g.setTransform(old);
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }
}
