package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Full-cell opening overlays and quiet deck samples; geometry for an ImageGen reskin. */
public final class ShipInteriorMaterialTemplate {
    private static final int PITCH = 256, INSET = 16, CELL = 224;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        ImageIO.write(render(), "png", output.toFile());
    }

    public static BufferedImage render() {
        BufferedImage out = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int facing = 0; facing < 4; facing++) {
            Graphics2D g = cell(out, facing % 2, facing / 2);
            g.rotate(facing * Math.PI / 2, CELL / 2.0, CELL / 2.0);
            // Observation slit lies against the edge, not in a new floor slab.
            rect(g, 0x444842, 0, 0, CELL, 68);
            rect(g, 0xCAC9BD, 0, 0, CELL, 16);
            rect(g, 0xCAC9BD, 0, 52, CELL, 16);
            rect(g, 0xCAC9BD, 0, 16, 20, 36);
            rect(g, 0xCAC9BD, 204, 16, 20, 36);
            rect(g, 0x657271, 24, 22, 176, 24);
            rect(g, 0x444842, 108, 16, 8, 36);
            g.dispose();

            g = cell(out, 2 + facing % 2, facing / 2);
            g.rotate(facing * Math.PI / 2, CELL / 2.0, CELL / 2.0);
            // Adjacent wall cells own the jambs. A full-width low sill also joins wide doors.
            rect(g, 0x55594F, 0, 99, CELL, 26);
            rect(g, 0xB6B7AD, 0, 102, CELL, 20);
            rect(g, 0x777A70, 0, 110, CELL, 4);
            g.dispose();
        }
        for (int variant = 0; variant < 4; variant++) {
            Graphics2D g = cell(out, variant % 2, 2 + variant / 2);
            rect(g, 0x92968B, 0, 0, CELL, CELL);
            g.dispose();
            g = cell(out, 2 + variant % 2, 2 + variant / 2);
            rect(g, 0xACA99D, 0, 0, CELL, CELL);
            g.dispose();
        }
        return out;
    }

    private static Graphics2D cell(BufferedImage image, int x, int y) {
        Graphics2D g = image.createGraphics();
        g.translate(x * PITCH + INSET, y * PITCH + INSET);
        return g;
    }

    private static void rect(Graphics2D g, int rgb, int x, int y, int w, int h) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }
}
