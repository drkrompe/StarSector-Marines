package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Rav Bloom Runner's motion, large enough to judge: its skeleton ({@link RavRunnerRig})
 * stepped every tick and drawn through the production {@link RavRunnerComposer}, so this is
 * exactly what the battle draws, magnified. Three panels, each camera on its Runner:
 * <ul>
 *   <li><b>Strikes:</b> stood at a target with the attack cooldown cycling as the battle's does,
 *       one arm per blow, the body winding back and driving through.</li>
 *   <li><b>Turning:</b> galloping round a tight circle, the spine bowed into the turn.</li>
 *   <li><b>Hauling:</b> backing off with a body on its spike, jaws latched on.</li>
 * </ul>
 * 1920x1080 at 100% scale, one frame per simulation tick.
 */
public final class RavRunnerMotionSnapshotSuite implements SnapshotSuite {

    private static final float DT = 1f / 30f;
    private static final int TICKS = 150;
    /** Screen px per cell: about eight times the game's normal battle zoom. */
    private static final float PPC = 140f;
    private static final String LOAD = "graphics/battle/marine-modular-topdown/variants/armor/charcoal/downed-0.png";
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;

    @Override public String id() { return "rav-runner-motion"; }

    @Override public String label() { return "Rav Runner: strikes, a galloping turn and a haul, magnified"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        modRoot = context.modRoot();
        images.clear();
        RavRunnerAssets art = RavRunnerAssets.load(path -> {
            try {
                BufferedImage image = image(path);
                return LayeredSpriteCache.headless(path, image.getWidth(), image.getHeight());
            } catch (Exception e) {
                return null;
            }
        });
        if (art == null) throw new IllegalStateException("Rav Runner bones missing under " + modRoot);
        RavRunnerRig striker = new RavRunnerRig(3L);
        RavRunnerRig turner = new RavRunnerRig(5L);
        RavRunnerRig hauler = new RavRunnerRig(7L);
        float cooldown = 0.45f;
        float turnAngle = 0f;
        float haulX = 0f;
        List<BufferedImage> frames = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            // the striker: the cooldown counts down, and the blow lands as it runs out
            if (cooldown <= 0f) cooldown = 0.7f;
            striker.advance(0f, 0f, 0f, cooldown, true, DT);
            cooldown = Math.max(0f, cooldown - DT);
            // the turner: three cells a second round a circle of 1.4 cells, turning left
            float radius = 1.4f;
            turnAngle += 3f / radius * DT;
            float facing = (float) Math.toDegrees(turnAngle);
            turner.advance(radius * (float) Math.cos(turnAngle),
                    radius * (float) Math.sin(turnAngle), facing, 1f, false, DT);
            // the hauler: backing east toward a bore, the body trailing half a cell to the west
            hauler.advance(haulX, 0f, 90f, 1f, false, tick > 3, haulX - 0.55f, 0f, DT);
            haulX += 1.28f * DT;
            frames.add(frame(art, striker, turner, hauler, haulX - 0.55f, tick));
        }
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("rav-runner-motion-frame-40.png", frames.get(40)));
        artifacts.add(new SnapshotArtifact("rav-runner-strike-phases.png", strikePhases(frames)));
        artifacts.add(SnapshotArtifact.colorAnimation("rav-runner-motion.gif", frames, 33));
        return artifacts;
    }

    private BufferedImage frame(RavRunnerAssets art, RavRunnerRig striker, RavRunnerRig turner,
                                RavRunnerRig hauler, float loadX, int tick) throws Exception {
        BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        g.setColor(new Color(0x3E4A36));
        for (int p = 0; p < 3; p++) g.fillRect(40 + p * 620, 120, 600, 900);
        text(g, "RAV BLOOM RUNNER / MOTION ON ITS SKELETON", 40, 52, 28);
        text(g, "1920 × 1080 · 100%% UI scale · production RavRunnerComposer · %.0f px per cell · tick %d"
                .formatted(PPC, tick), 40, 86, 17);
        text(g, "Strikes: one arm per blow, the body winds back and drives through", 50, 1050, 15);
        text(g, "Turning: galloping round a 1.4-cell circle, spine bowed", 670, 1050, 15);
        text(g, "Hauling: backing toward the bore, spike in, jaws latched", 1290, 1050, 15);

        draw(g, art, striker, 340, 620);
        draw(g, art, turner, 960, 570);
        // the hauled body first, under the jaws, as the battle draws it
        BufferedImage load = image(LOAD);
        float shoulderPx = UnitRenderService.layeredInfantryShoulderWidth(PPC, 1f);
        float scale = shoulderPx * UnitRenderService.DOWNED_BODY_SCALE / LayeredUnitComposer.SOURCE_SHOULDER_PX;
        float haulCx = 1580, haulCy = 620;
        float lx = haulCx + (loadX - hauler.bodyX()) * PPC;
        AffineTransform t = new AffineTransform();
        t.translate(lx, haulCy);
        t.rotate(Math.toRadians(90));
        t.scale(scale, scale);
        t.translate(-load.getWidth() / 2.0, -load.getHeight() / 2.0);
        g.drawImage(load, t, null);
        draw(g, art, hauler, haulCx, haulCy);
        g.dispose();
        return output;
    }

    /** Ticks of one stab (rest, pull back, drive, hold) and one swipe (rest, wind, cut, follow through). */
    private static final int[] PHASE_TICKS = {8, 11, 14, 17, 29, 32, 35, 38};
    private static final String[] PHASE_NAMES = {"stab: at rest", "elbow out, body pulled back", "drives forward onto the point", "holds",
            "swipe: at rest", "winds out wide, shoulder back", "cuts across, trunk twists through", "follows through"};

    /** The striker at each phase of a stab and a swipe, two times its panel's scale. */
    private static BufferedImage strikePhases(List<BufferedImage> frames) {
        BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        text(g, "RAV BLOOM RUNNER / THE BODY IN THE STRIKE", 40, 46, 26);
        text(g, "Top: a stab pulls the body back, then drives it forward onto the point. Bottom: a swipe winds"
                + " the striking shoulder back and twists the trunk through the cut. 280 px per cell.", 40, 76, 16);
        for (int i = 0; i < PHASE_TICKS.length; i++) {
            int x = (i % 4) * 480, y = 100 + (i / 4) * 490;
            g.drawImage(frames.get(PHASE_TICKS[i]), x, y, x + 480, y + 460, 220, 440, 460, 670, null);
            text(g, "%s (tick %d)".formatted(PHASE_NAMES[i], PHASE_TICKS[i]), x + 16, y + 482, 16);
        }
        g.dispose();
        return output;
    }

    /** One Runner centred on (x, y) of the sheet, y down. */
    private void draw(Graphics2D g, RavRunnerAssets art, RavRunnerRig rig, float x, float y) {
        RavRunnerComposer.emit((sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        }, art, rig, x, 1080f - y, PPC, 1f, 1f);
    }

    private BufferedImage image(String path) throws Exception {
        BufferedImage result = images.get(path);
        if (result == null) {
            result = ImageIO.read(modRoot.resolve(path).toFile());
            if (result == null) throw new IllegalArgumentException("Unreadable sprite: " + path);
            images.put(path, result);
        }
        return result;
    }

    private static void text(Graphics2D g, String value, int x, int y, int size) {
        g.setColor(new Color(0xD3DFE5));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }
}
