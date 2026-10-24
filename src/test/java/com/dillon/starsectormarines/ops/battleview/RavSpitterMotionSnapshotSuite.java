package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavSpitterRig;
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
import java.util.function.Function;

/**
 * The Spitter's motion, large enough to judge: its skeleton ({@link RavSpitterRig}) stepped every tick and drawn
 * through the production {@link RavSpitterComposer}, so this is exactly what the battle draws, magnified. No battle
 * is played. Four panels: gliding north (the wave down the tail), weaving (the tail lagging each turn), standing and
 * lobbing as the battle's cooldown cycles (the hood popping, the rear, the lunge, the fold), and its corpse in its
 * pool. A still of the lob's beats goes with the GIF.
 * 1920x1080 at 100% scale, one frame per simulation tick.
 */
public final class RavSpitterMotionSnapshotSuite implements SnapshotSuite {

    private static final float DT = 1f / 30f;
    private static final int TICKS = 150;
    /** Screen px per cell: about eight times the game's normal battle zoom. */
    private static final float PPC = 200f;
    /** The lob panel's cooldown: the battle's Spitter strike cooldown, near enough. */
    private static final float COOLDOWN = 2.0f;
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;

    @Override public String id() { return "rav-spitter-motion"; }

    @Override public String label() { return "Rav Spitter: the glide, the tail through turns, the lob, and its corpse, magnified"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        modRoot = context.modRoot();
        images.clear();
        Function<String, LayeredSpriteCache> loader = path -> {
            try {
                BufferedImage image = image(path);
                return LayeredSpriteCache.headless(path, image.getWidth(), image.getHeight());
            } catch (Exception e) {
                return null;
            }
        };
        RavSpitterAssets art = RavSpitterAssets.load(loader);
        if (art == null) throw new IllegalStateException("Rav Spitter art missing under " + modRoot);

        RavSpitterRig walker = new RavSpitterRig(3L), weaver = new RavSpitterRig(4L), lobber = new RavSpitterRig(5L);
        float walkY = 0f, weaveX = 0f, weaveY = 0f, cooldown = 0.9f;
        List<BufferedImage> frames = new ArrayList<>();
        List<float[]> beats = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            walkY += 1.4f * DT;
            walker.advance(0f, walkY, 0f, 1f, false, DT);
            float facing = 45f * (float) Math.sin(2 * Math.PI * tick * DT / 3.0);
            double rad = Math.toRadians(facing);
            weaveX += (float) -Math.sin(rad) * 1.4f * DT;
            weaveY += (float) Math.cos(rad) * 1.4f * DT;
            weaver.advance(weaveX, weaveY, facing, 1f, false, DT);
            cooldown -= DT;
            if (cooldown <= 0f) cooldown = COOLDOWN;     // the lob: the battle resets the cooldown as it fires
            lobber.advance(0f, 0f, 0f, cooldown, true, DT);
            beats.add(new float[]{lobber.brace(), lobber.hood()});

            BufferedImage output = canvas();
            Graphics2D g = graphics(output);
            text(g, "RAV BLOOM SPITTER / MOTION ON ITS SKELETON", 40, 52, 28);
            text(g, "1920 × 1080 · 100%% UI scale · production RavSpitterComposer · %.0f px per cell · tick %d"
                    .formatted(PPC, tick), 40, 86, 17);
            for (int p = 0; p < 4; p++) panel(g, 40 + p * 465, 445);
            text(g, "Gliding north", 50, 1050, 15);
            text(g, "Weaving: the tail lags the turn %+.0f°".formatted(weaver.tailAngle(2)), 515, 1050, 15);
            text(g, "Lobbing: brace %.2f, hood %.2f".formatted(lobber.brace(), lobber.hood()), 980, 1050, 15);
            text(g, "Dead: dulled in its acid", 1445, 1050, 15);
            draw(g, art, walker, 262, 620);
            draw(g, art, weaver, 727, 620);
            draw(g, art, lobber, 1192, 620);
            drawDead(g, art, 1657, 620);
            g.dispose();
            frames.add(output);
        }
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(SnapshotArtifact.colorAnimation("rav-spitter-motion.gif", frames, 33));
        artifacts.add(new SnapshotArtifact("rav-spitter-lob.png", lobSheet(frames, beats)));
        return artifacts;
    }

    /** The lob panel at its beats: at rest, braced with its hood popped, as the glob leaves, and folded again. */
    private static BufferedImage lobSheet(List<BufferedImage> frames, List<float[]> beats) {
        int braced = 0;
        for (int i = 0; i < beats.size(); i++) if (beats.get(i)[0] > beats.get(braced)[0]) braced = i;
        int lob = braced;
        while (lob < beats.size() - 1 && beats.get(lob)[0] > 0.05f) lob++;
        int folded = lob;
        while (folded < beats.size() - 1 && beats.get(folded)[1] > 0.05f) folded++;
        int[] at = {0, braced, Math.min(frames.size() - 1, lob + 1), folded};
        String[] names = {"at rest, hood folded", "braced: hood popped, chest reared", "the lob: the head lunges",
                "hood folded again"};
        BufferedImage output = canvas();
        Graphics2D g = graphics(output);
        text(g, "RAV BLOOM SPITTER / THE LOB", 40, 46, 26);
        for (int i = 0; i < 4; i++) {
            int x = i * 480, y = 100;
            g.drawImage(frames.get(at[i]), x, y, x + 480, y + 900, 970, 120, 1415, 1020, null);
            text(g, names[i] + " (tick " + at[i] + ")", x + 16, y + 930, 16);
        }
        g.dispose();
        return output;
    }

    private void draw(Graphics2D g, RavSpitterAssets art, RavSpitterRig rig, float x, float y) {
        // the pose is in the body's own frame, so each panel keeps its Spitter centred as it walks
        RavSpitterComposer.emit(emitter(g), art, rig, x, 1080f - y, PPC, 1f);
    }

    private void drawDead(Graphics2D g, RavSpitterAssets art, float x, float y) {
        RavSpitterComposer.emitDead(emitter(g), art, x, 1080f - y, 20f, PPC, 1f);
    }

    private LayeredUnitComposer.SpriteEmitter emitter(Graphics2D g) {
        return (sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        };
    }

    private static BufferedImage canvas() {
        return new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D graphics(BufferedImage output) {
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        return g;
    }

    private static void panel(Graphics2D g, int x, int width) {
        g.setColor(new Color(0x3E4A36));
        g.fillRect(x, 120, width, 900);
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
