package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Repeatable two-material guides; existing wall, opening and remnant geometry stays authoritative. */
public final class ConcreteInstallationTemplate {
    private ConcreteInstallationTemplate() {}
    public static void main(String[] args) throws Exception {
        Path output = Path.of("art-source/templates");
        Files.createDirectories(output);
        int[] colors = {0x414448, 0x999A96};
        ImageIO.write(WallReskinTemplate.render(colors), "png", output.resolve("concrete-walls.png").toFile());
        ImageIO.write(OpeningReskinTemplate.render(colors), "png", output.resolve("concrete-openings.png").toFile());
        ImageIO.write(RubbleReskinTemplate.render(colors), "png", output.resolve("concrete-rubble.png").toFile());
        var floors = new BufferedImage(1536, 512, BufferedImage.TYPE_INT_RGB);
        var g = floors.createGraphics();
        g.setColor(new Color(0x17191C)); g.fillRect(0, 0, 1536, 512);
        int[] floorColors = {0x797B79, 0x8B8C88, 0x858075};
        for (int i = 0; i < floorColors.length; i++) {
            g.setColor(new Color(floorColors[i])); g.fillRect(i * 512 + 16, 16, 480, 480);
        }
        g.dispose();
        ImageIO.write(floors, "png", output.resolve("installation-floors.png").toFile());
    }
}
