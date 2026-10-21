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
import java.awt.image.RescaleOp;
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
 * A second recording kills four Runners mid-action (galloping, striking, turning, standing) and
 * lets them fall, dulled as the battle draws its dead.
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
        List<BufferedImage> deaths = deaths(art);
        artifacts.add(new SnapshotArtifact("rav-runner-death.png", deathSheet(deaths)));
        artifacts.add(SnapshotArtifact.colorAnimation("rav-runner-death.gif", deaths, 33));
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

    private static final int DEATH_TICK = 30;
    private static final int DEATH_TICKS = 80;
    private static final String[] DEATH_NAMES = {"galloping", "striking", "turning", "standing"};

    /** Four Runners, each shot mid-action at {@link #DEATH_TICK}, then lying where they fell. */
    private List<BufferedImage> deaths(RavRunnerAssets art) {
        RavRunnerRig[] rigs = {new RavRunnerRig(11L), new RavRunnerRig(12L), new RavRunnerRig(13L), new RavRunnerRig(14L)};
        float[][] at = new float[4][2];
        float cooldown = 0.45f, turnAngle = 0f;
        List<BufferedImage> frames = new ArrayList<>();
        for (int tick = 0; tick < DEATH_TICKS; tick++) {
            boolean dead = tick >= DEATH_TICK;
            if (!dead) {
                rigs[0].advance(0f, tick * 3.2f * DT, 0f, 1f, false, DT);
                at[0][1] = tick * 3.2f * DT;
                if (cooldown <= 0f) cooldown = 0.7f;
                rigs[1].advance(0f, 0f, 0f, cooldown, true, DT);
                cooldown = Math.max(0f, cooldown - DT);
                turnAngle += 3f / 1.4f * DT;
                at[2][0] = 1.4f * (float) Math.cos(turnAngle);
                at[2][1] = 1.4f * (float) Math.sin(turnAngle);
                rigs[2].advance(at[2][0], at[2][1], (float) Math.toDegrees(turnAngle), 1f, false, DT);
                rigs[3].advance(0f, 0f, 0f, 1f, false, DT);
            } else {
                for (int i = 0; i < 4; i++) rigs[i].advanceDead(at[i][0], at[i][1], DT);
            }
            BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = output.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x111920));
            g.fillRect(0, 0, 1920, 1080);
            text(g, "RAV BLOOM RUNNER / DEATH ON ITS SKELETON", 40, 52, 28);
            text(g, ("Four Runners killed mid-action at tick %d, then lying as corpses, dulled as the battle draws its"
                    + " dead · %.0f px per cell · tick %d%s").formatted(DEATH_TICK, PPC, tick, dead ? " · DEAD" : ""), 40, 86, 17);
            for (int i = 0; i < 4; i++) {
                int px = 40 + i * 465;
                g.setColor(new Color(0x3E4A36));
                g.fillRect(px, 120, 445, 900);
                text(g, "killed while " + DEATH_NAMES[i], px + 10, 1050, 16);
                draw(g, art, rigs[i], px + 222, 570);
            }
            g.dispose();
            frames.add(output);
        }
        return frames;
    }

    /** Top: the galloping Runner as it falls. Bottom: all four corpses where they came to rest. */
    private static BufferedImage deathSheet(List<BufferedImage> frames) {
        BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        text(g, "RAV BLOOM RUNNER / CORPSES", 40, 46, 26);
        text(g, "Top: a galloping Runner killed and falling onto its flank. Bottom: four corpses at rest, each"
                + " lying in the pose it died in. 280 px per cell.", 40, 76, 16);
        int[] fall = {DEATH_TICK - 1, DEATH_TICK + 4, DEATH_TICK + 8, DEATH_TICK + 14};
        String[] fallNames = {"alive, galloping", "hit: rolling over", "falling", "down, legs slack"};
        for (int i = 0; i < 4; i++) {
            int x = i * 480, y = 100;
            g.drawImage(frames.get(fall[i]), x, y, x + 480, y + 460, 142, 455, 382, 685, null);
            text(g, fallNames[i], x + 16, y + 482, 16);
        }
        BufferedImage last = frames.get(frames.size() - 1);
        for (int i = 0; i < 4; i++) {
            int x = i * 480, y = 590;
            int sx = 40 + i * 465 + 222;
            g.drawImage(last, x, y, x + 480, y + 460, sx - 120, 455, sx + 120, 685, null);
            text(g, "corpse: killed while " + DEATH_NAMES[i], x + 16, y + 482, 16);
        }
        g.dispose();
        return output;
    }

    /** One Runner centred on (x, y) of the sheet, y down. */
    private void draw(Graphics2D g, RavRunnerAssets art, RavRunnerRig rig, float x, float y) {
        RavRunnerComposer.emit((sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = shaded(sprite.sourcePath, red);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        }, art, rig, x, 1080f - y, PPC, rig.dead() ? RavRunnerComposer.DEAD_SHADE : 1f, 1f);
    }

    /** The sprite multiplied by a grey tint, as the game's vertex colour does. */
    private BufferedImage shaded(String path, float shade) {
        BufferedImage source = images.get(path);
        if (shade >= 0.999f) return source;
        String key = path + "@" + shade;
        BufferedImage result = images.get(key);
        if (result == null) {
            result = new RescaleOp(new float[]{shade, shade, shade, 1f}, new float[4], null).filter(source, null);
            images.put(key, result);
        }
        return result;
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
