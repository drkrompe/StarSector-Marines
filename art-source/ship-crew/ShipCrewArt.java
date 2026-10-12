import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Retained silhouette guide and mechanical layer cuts. Run from the repository root. */
public class ShipCrewArt {
    static final String[] ROLES = {"command", "engineering", "medical", "service"};
    // Body and head match the compositor's 150px shoulder reference. Foot pivot is (19,26).
    static final int[][] CUTS = {{54, 28, 300, 240, 150, 120},
            {496, 60, 144, 176, 72, 88}, {786, 96, 76, 104, 38, 52}};
    static final String[] PARTS = {"body", "head", "foot"};

    public static void main(String[] args) throws Exception {
        Path source = Path.of("art-source/ship-crew");
        Files.createDirectories(source);
        BufferedImage guide = new BufferedImage(1024, 1152, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = guide.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int row = 0; row < 4; row++) {
            g.translate(0, row * 288);
            g.setColor(new Color(163, 163, 163));
            Area body = new Area(new RoundRectangle2D.Double(91, 35, 226, 226, 76, 80));
            body.add(new Area(new RoundRectangle2D.Double(56, 68, 78, 163, 60, 60)));
            body.add(new Area(new RoundRectangle2D.Double(274, 68, 78, 163, 60, 60)));
            body.subtract(new Area(new Ellipse2D.Double(148, 29, 112, 108)));
            g.fill(body);
            g.fillOval(502, 62, 132, 170);
            g.fillOval(496, 131, 18, 38);
            g.fillOval(622, 131, 18, 38);
            g.fillRoundRect(788, 98, 72, 100, 38, 40);
            g.translate(0, -row * 288);
        }
        g.dispose();
        Path guidePath = Path.of("art-source/templates/ship-crew-silhouettes.png");
        ImageIO.write(guide, "png", guidePath.toFile());
        if (args.length == 0) return;
        BufferedImage raw = ImageIO.read(source.resolve("ship-crew.raw.png").toFile());
        for (int row = 0; row < 4; row++) {
            Path target = Path.of("mod/graphics/battle/ship-crew-modular-topdown", ROLES[row]);
            Files.createDirectories(target);
            for (int part = 0; part < CUTS.length; part++) {
                int[] cut = CUTS[part];
                BufferedImage layer = new BufferedImage(cut[4], cut[5], BufferedImage.TYPE_INT_ARGB);
                Graphics2D out = layer.createGraphics();
                out.setComposite(AlphaComposite.Src);
                out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                // Independent layers normalize their content to the compositor's
                // anatomical sizes; no alpha keying or painted corrections.
                int[] columns = {0, 400, 700, 1024};
                int left = columns[part] * raw.getWidth() / 1024;
                int top = row * raw.getHeight() / 4;
                int endX = columns[part + 1] * raw.getWidth() / 1024;
                int endY = (row + 1) * raw.getHeight() / 4;
                int x = endX, y = endY, right = left, bottom = top;
                for (int py = top; py < endY; py++) for (int px = left; px < endX; px++) {
                    if ((raw.getRGB(px, py) >>> 24) <= 8) continue;
                    x = Math.min(x, px); y = Math.min(y, py);
                    right = Math.max(right, px + 1); bottom = Math.max(bottom, py + 1);
                }
                if (right <= x || bottom <= y) throw new IllegalStateException("Missing layer");
                out.drawImage(raw, 0, 0, cut[4], cut[5], x, y, right, bottom, null);
                out.dispose();
                ImageIO.write(layer, "png", target.resolve(PARTS[part] + ".png").toFile());
            }
        }
    }
}
