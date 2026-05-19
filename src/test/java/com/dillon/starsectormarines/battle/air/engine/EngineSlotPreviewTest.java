package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dev preview for {@link ShipSpecEngineParser}. For every {@link ShuttleType}
 * with a matching vanilla hull, loads the {@code .ship} spec + sprite from
 * the local game install, parses engine slots in <em>our</em> shuttle-local
 * frame, then projects each slot back onto the sprite image as a yellow dot
 * (slot position) + red arrow (plume direction and length). Writes one PNG
 * per hull to {@code build/engine-previews/} for human eyeballing.
 *
 * <p>The "did we get the transform right?" check is visual: dots should land
 * at the painted engine glow on the sprite, and arrows should point straight
 * back along the thrust direction the artist drew. If a transform sign or
 * axis is wrong, dots scatter off the hull and arrows fire toward the nose —
 * fail loud and obvious.
 *
 * <p>Iterate via:
 * <pre>
 *   gradlew :test --tests "*EngineSlotPreviewTest*"
 *   open build/engine-previews/&lt;hull&gt;.png
 * </pre>
 *
 * <p>Requires the {@code starsectorDir} gradle property to be set so the
 * test can resolve {@code &lt;starsectorDir&gt;/starsector-core/data/hulls/&lt;id&gt;.ship}.
 * Skips gracefully when the property is missing — this is a dev iteration
 * tool, not a CI gate.
 */
public class EngineSlotPreviewTest {

    private static final Path OUT_DIR = Paths.get("build/engine-previews");

    /** Sprite upscale for the preview canvas — sprite pixels can be tiny; doubling makes the overlay readable. */
    private static final int DISPLAY_SCALE = 2;
    private static final int LABEL_PAD_TOP = 28;
    private static final int LABEL_PAD_BOTTOM = 60;

    private static final Color BG          = new Color(0x10, 0x14, 0x1C);
    private static final Color LABEL_FG    = new Color(0xE0, 0xE8, 0xF4);
    private static final Color SLOT_DOT    = new Color(0xFF, 0xE0, 0x40);
    private static final Color PLUME_ARROW = new Color(0xFF, 0x5A, 0x3A);
    private static final Color CENTER_MARK = new Color(0x60, 0xC0, 0xFF, 180);

    @Test
    void writePerHullPreviews() throws Exception {
        String starsectorDir = System.getProperty("starsectorDir");
        Assumptions.assumeTrue(
                starsectorDir != null && !starsectorDir.isBlank(),
                "starsectorDir system property not set — pass -PstarsectorDir=... or set it in gradle.properties");

        Path coreDir = Paths.get(starsectorDir, "starsector-core");
        Files.createDirectories(OUT_DIR);

        int totalSlots = 0;
        List<String> renderedHulls = new ArrayList<>();
        List<String> skippedHulls = new ArrayList<>();

        for (ShuttleType type : ShuttleType.values()) {
            if (type.matchingHullIds.isEmpty()) continue;
            // First id per type is the canonical one. (Kite has kite +
            // kite_original; we only need one preview per visual hull.)
            String hullId = type.matchingHullIds.get(0);

            Path shipPath = coreDir.resolve("data/hulls/" + hullId + ".ship");
            Path spritePath = coreDir.resolve(type.spritePath);
            if (!Files.exists(shipPath) || !Files.exists(spritePath)) {
                skippedHulls.add(hullId + " (missing files)");
                continue;
            }

            String shipJson = Files.readString(shipPath);
            EngineSlotData[] slots = ShipSpecEngineParser.parse(shipJson, type.visualLengthCells);
            BufferedImage sprite = ImageIO.read(Files.newInputStream(spritePath));
            if (sprite == null) {
                skippedHulls.add(hullId + " (sprite read failed)");
                continue;
            }

            BufferedImage preview = renderPreview(type, hullId, sprite, slots);
            Path outPath = OUT_DIR.resolve(hullId + ".png");
            ImageIO.write(preview, "PNG", outPath.toFile());
            System.out.println("  wrote " + outPath.toAbsolutePath()
                    + "  (" + slots.length + " engine slots)");

            renderedHulls.add(hullId);
            totalSlots += slots.length;
        }

        System.out.println("EngineSlotPreviewTest: " + renderedHulls.size()
                + " hulls rendered, " + totalSlots + " total slots."
                + (skippedHulls.isEmpty() ? "" : "  Skipped: " + skippedHulls));

        // Parser regression sentinel — vanilla hulls all have at least one
        // engine slot, so zero across the whole sweep means the parser broke
        // (or every spec moved). The visual correctness check is the PNG.
        assertTrue(totalSlots > 0,
                "no engine slots parsed across any shuttle hull — parser likely broken");
    }

    /**
     * Draws the sprite scaled-up with engine slot markers on top. Slot
     * positions and plume arrows are first converted back from our local
     * cell-frame into vanilla pixel-frame, then scaled to display coords.
     */
    private static BufferedImage renderPreview(ShuttleType type, String hullId,
                                               BufferedImage sprite, EngineSlotData[] slots) {
        int spriteW = sprite.getWidth();
        int spriteH = sprite.getHeight();
        int canvasW = spriteW * DISPLAY_SCALE + 80;
        int canvasH = spriteH * DISPLAY_SCALE + LABEL_PAD_TOP + LABEL_PAD_BOTTOM;

        BufferedImage img = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g.setColor(BG);
        g.fillRect(0, 0, canvasW, canvasH);

        // Header label.
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString(type.name() + "  /  hullId='" + hullId
                + "'  /  visualLengthCells=" + type.visualLengthCells
                + "  /  sprite=" + spriteW + "x" + spriteH
                + "  /  " + slots.length + " slots", 8, 18);

        // Sprite (nose-up, vanilla convention).
        int sx0 = 40;
        int sy0 = LABEL_PAD_TOP;
        g.drawImage(sprite, sx0, sy0,
                spriteW * DISPLAY_SCALE, spriteH * DISPLAY_SCALE, null);

        // Sprite pixel center, in display coords. Our cell-frame origin lives
        // here — that's the assumption the parser makes (slot positions are
        // relative to sprite pixel center, not the vanilla `center` property).
        float spriteCenterX = sx0 + (spriteW * DISPLAY_SCALE) * 0.5f;
        float spriteCenterY = sy0 + (spriteH * DISPLAY_SCALE) * 0.5f;

        // Reference crosshair at sprite pixel center — visual sanity check
        // that "the math thinks the ship's origin is HERE."
        g.setColor(CENTER_MARK);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Float(spriteCenterX - 8, spriteCenterY,
                spriteCenterX + 8, spriteCenterY));
        g.draw(new Line2D.Float(spriteCenterX, spriteCenterY - 8,
                spriteCenterX, spriteCenterY + 8));

        // pxPerCell in DISPLAY units — vanilla pxPerCell scaled by our 2x
        // upscale. The parser used the source-pixel ratio; we need the
        // display ratio to project back onto the displayed sprite.
        float displayPxPerCell = (spriteH / type.visualLengthCells) * DISPLAY_SCALE;

        // Per-slot dot + plume arrow.
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i < slots.length; i++) {
            EngineSlotData s = slots[i];
            // Cells (our frame) → display pixels (image frame). Our +X maps
            // to image +X (both go right); our +Y maps to image -Y (image
            // y-axis points down, our forward is up in a nose-up sprite).
            float dx = spriteCenterX + s.localX * displayPxPerCell;
            float dy = spriteCenterY - s.localY * displayPxPerCell;

            // Plume direction in our frame: 0° = +Y (forward), CCW-positive.
            //   our_dx = -sin(angle), our_dy = cos(angle)
            // Mapped to image: image_dx = our_dx, image_dy = -our_dy.
            double rad = Math.toRadians(s.angleDegrees);
            float plumeLen = s.lengthCells * displayPxPerCell;
            float ex = dx + (float) (-Math.sin(rad)) * plumeLen;
            float ey = dy - (float)  Math.cos(rad)   * plumeLen;

            g.setColor(PLUME_ARROW);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Line2D.Float(dx, dy, ex, ey));
            drawArrowHead(g, dx, dy, ex, ey);

            g.setColor(SLOT_DOT);
            float r = Math.max(3f, s.widthCells * displayPxPerCell * 0.5f);
            g.fillOval((int) (dx - r), (int) (dy - r), (int) (2 * r), (int) (2 * r));
            g.setColor(Color.BLACK);
            g.drawOval((int) (dx - r), (int) (dy - r), (int) (2 * r), (int) (2 * r));

            // Slot index label, offset opposite the plume so it doesn't sit
            // on top of the arrow.
            g.setColor(LABEL_FG);
            g.drawString("#" + i, dx + 6, dy - 4);
        }

        // Legend at the bottom.
        int legY = sy0 + spriteH * DISPLAY_SCALE + 18;
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("yellow dot = slot location (radius = vanilla 'width'/2)", 8, legY);
        g.drawString("red arrow = plume direction & length (vanilla 'angle' + 'length')", 8, legY + 14);
        g.drawString("blue crosshair = sprite pixel center (our cell-frame origin)", 8, legY + 28);

        g.dispose();
        return img;
    }

    /** Small filled triangle at (ex,ey) pointing along the (dx,dy)→(ex,ey) direction. */
    private static void drawArrowHead(Graphics2D g, float dx, float dy, float ex, float ey) {
        double vx = ex - dx, vy = ey - dy;
        double len = Math.hypot(vx, vy);
        if (len < 0.001) return;
        double ux = vx / len, uy = vy / len;
        double headLen = 8.0;
        double headHalfWidth = 4.0;
        // Two base corners of the arrowhead triangle.
        double bx = ex - ux * headLen;
        double by = ey - uy * headLen;
        double px = -uy, py = ux; // perpendicular
        int[] xs = {
                (int) ex,
                (int) (bx + px * headHalfWidth),
                (int) (bx - px * headHalfWidth)
        };
        int[] ys = {
                (int) ey,
                (int) (by + py * headHalfWidth),
                (int) (by - py * headHalfWidth)
        };
        g.fillPolygon(xs, ys, 3);
    }
}
