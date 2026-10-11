package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Eight separate overhead components; source cell 96px, slot pitch 512x256. */
public final class ArmoryWorkshopTemplate {
    private ArmoryWorkshopTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < 8; slot++) {
            Graphics2D g = image.createGraphics();
            g.translate(64 + slot % 2 * 512, 32 + slot / 2 * 256);
            switch (slot) {
                case 0 -> { // issue counter 2x1
                    rect(g, 0, 4, 192, 88, 0x404746);
                    rect(g, 0, 4, 192, 68, 0x9A9B90);
                    rect(g, 6, 75, 180, 12, 0x303634);
                    rect(g, 124, 12, 48, 40, 0x394542);
                }
                case 1 -> { // recessed weapon bed 3x1
                    rect(g, 4, 4, 280, 88, 0x343B39);
                    rect(g, 12, 12, 264, 70, 0x646A60);
                    for (int x : new int[]{96, 192}) rect(g, x, 12, 4, 70, 0x414840);
                }
                case 2 -> { // rifle layer 3x1, transparent between weapons
                    for (int x : new int[]{48, 140, 232}) {
                        rect(g, x, 14, 6, 29, 0x929B97);
                        rect(g, x-5, 36, 18, 29, 0x353D39);
                        rect(g, x+11, 43, 9, 15, 0x535D54);
                        rect(g, x-3, 63, 15, 17, 0x6B705C);
                    }
                }
                case 3 -> { // retaining frame 3x1, same coordinates as bed
                    rect(g, 4, 4, 280, 8, 0x7C827A);
                    rect(g, 4, 84, 280, 8, 0x7C827A);
                    rect(g, 4, 4, 8, 88, 0x505A55);
                    rect(g, 276, 4, 8, 88, 0x505A55);
                    for (int x : new int[]{96, 192}) rect(g, x, 4, 4, 88, 0x525D55);
                }
                case 4 -> { // workbench 3x2
                    rect(g, 4, 4, 280, 184, 0x3C4641);
                    rect(g, 8, 8, 272, 160, 0x91978B);
                    rect(g, 26, 30, 208, 104, 0x3D4941);
                    for (int x : new int[]{16, 108, 200}) rect(g, x, 174, 72, 7, 0x92988B);
                }
                case 5 -> { // tools and stripped rifle on transparent bench overlay
                    rect(g, 34, 65, 133, 13, 0x778076);
                    rect(g, 32, 62, 36, 25, 0x363D38);
                    rect(g, 77, 74, 14, 17, 0x4E5850);
                    rect(g, 139, 91, 51, 9, 0x7D897F);
                    rect(g, 241, 24, 27, 45, 0x4E5B56);
                    rect(g, 232, 38, 45, 12, 0xA4ACA0);
                    rect(g, 34, 141, 82, 18, 0x434D44);
                }
                case 6 -> { // ready-use locker bank 3x2
                    rect(g, 4, 4, 280, 184, 0x3D4741);
                    for (int x : new int[]{12, 104, 196}) {
                        rect(g, x, 12, 80, 168, 0x727B6A);
                        rect(g, x+60, 83, 7, 25, 0xB0B5A5);
                        rect(g, x+15, 25, 48, 8, 0x424D42);
                    }
                }
                case 7 -> { // sealed ammunition cabinet 2x2
                    rect(g, 4, 4, 184, 184, 0x3A423A);
                    for (int y : new int[]{12, 100}) {
                        rect(g, 12, y, 168, 80, 0x787C66);
                        rect(g, 78, y+34, 36, 8, 0xABB09C);
                        rect(g, 22, y+13, 26, 12, 0x9B9473);
                    }
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
