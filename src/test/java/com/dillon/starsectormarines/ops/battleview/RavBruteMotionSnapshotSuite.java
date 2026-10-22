package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBruteRig;
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
 * The Rav Bloom Brute's motion and death, large enough to judge: its skeleton ({@link RavBruteRig}) stepped
 * every tick and drawn through the production {@link RavBruteComposer}, so this is exactly what the battle
 * draws, magnified, each camera on its Brute. The motion recording has four panels: walking into a trot,
 * weaving left and right (the back bending into each turn), blows thrown with the whole body, and a charge
 * (the tell, the gallop, then stunned against something heavy). The death recording kills four Brutes mid-action and lets them burst over the Runner's ichor.
 * 1920x1080 at 100% scale, one frame per simulation tick.
 */
public final class RavBruteMotionSnapshotSuite implements SnapshotSuite {

    private static final float DT = 1f / 30f;
    private static final int TICKS = 150;
    /** Screen px per cell: about eight times the game's normal battle zoom. */
    private static final float PPC = 140f;
    private static final int DEATH_TICK = 30;
    private static final int DEATH_TICKS = 110;
    private static final String[] DEATH_NAMES = {"trotting", "striking", "charging", "stunned"};
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;

    @Override public String id() { return "rav-brute-motion"; }

    @Override public String label() { return "Rav Brute: gait, blows, a charge and its death, magnified"; }

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
        RavBruteAssets art = RavBruteAssets.load(loader);
        RavRunnerAssets ichor = RavRunnerAssets.load(loader);
        if (art == null || ichor == null) throw new IllegalStateException("Rav Brute or Runner art missing under " + modRoot);

        List<SnapshotArtifact> artifacts = new ArrayList<>();
        List<BufferedImage> motion = motion(art);
        artifacts.add(SnapshotArtifact.colorAnimation("rav-brute-motion.gif", motion, 33));
        List<BufferedImage> deaths = deaths(art, ichor);
        artifacts.add(new SnapshotArtifact("rav-brute-death.png", deathSheet(deaths)));
        artifacts.add(SnapshotArtifact.colorAnimation("rav-brute-death.gif", deaths, 33));
        return artifacts;
    }

    /** One tick of each scripted Brute: where it is, and what it is doing. */
    private static final class Actor {
        final RavBruteRig rig;
        float x, y, facing, cooldown = 1f;

        Actor(long seed) {
            rig = new RavBruteRig(seed);
        }

        /** Walks, then trots, north. */
        void trot(int tick) {
            float speed = tick < 40 ? 0.9f : 1.8f;
            y += speed * DT;
            rig.advance(x, y, 0f, 1f, RavBruteRig.IDLE, false, DT);
        }

        /** Trots a weave, turning one way then the other at up to about a hundred degrees a second. */
        void weave(int tick) {
            facing = 50f * (float) Math.sin(2 * Math.PI * tick * DT / 3.0);
            double rad = Math.toRadians(facing);
            x += (float) -Math.sin(rad) * 1.8f * DT;
            y += (float) Math.cos(rad) * 1.8f * DT;
            rig.advance(x, y, facing, 1f, RavBruteRig.IDLE, false, DT);
        }

        /** Stands and strikes as the battle's cooldown cycles: the blow lands as it runs out. */
        void strike() {
            if (cooldown <= 0f) cooldown = 1.2f;
            rig.advance(x, y, 0f, cooldown, RavBruteRig.IDLE, false, DT);
            cooldown = Math.max(0f, cooldown - DT);
        }

        /** The tell, the gallop, the hit that stuns it, then standing again. */
        void charge(int tick) {
            int t = tick % 100;
            int phase = t < 12 ? RavBruteRig.TELL : t < 50 ? RavBruteRig.RUN : t < 65 ? RavBruteRig.PULL_UP
                    : RavBruteRig.IDLE;
            boolean stunned = t >= 50 && t < 80;
            if (phase == RavBruteRig.RUN) y += 4f * DT;
            rig.advance(x, y, 0f, 1f, phase, stunned, DT);
        }
    }

    private List<BufferedImage> motion(RavBruteAssets art) {
        Actor walker = new Actor(3L), weaver = new Actor(4L), striker = new Actor(5L), charger = new Actor(7L);
        List<BufferedImage> frames = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            walker.trot(tick);
            weaver.weave(tick);
            striker.strike();
            charger.charge(tick);
            BufferedImage output = canvas();
            Graphics2D g = graphics(output);
            for (int p = 0; p < 4; p++) panel(g, 40 + p * 465, 445);
            text(g, "RAV BLOOM BRUTE / MOTION ON ITS SKELETON", 40, 52, 28);
            text(g, "1920 × 1080 · 100%% UI scale · production RavBruteComposer · %.0f px per cell · tick %d"
                    .formatted(PPC, tick), 40, 86, 17);
            text(g, "Walking, then trotting (from tick 40)", 50, 1050, 15);
            text(g, "Weaving: the back bends %+.0f°".formatted(weaver.rig.bendDegrees()), 515, 1050, 15);
            text(g, "Blows: weight back, forequarters up, drive", 980, 1050, 15);
            text(g, "Charge: tell, gallop, stunned, standing", 1445, 1050, 15);
            draw(g, art, null, walker.rig, 262, 620);
            draw(g, art, null, weaver.rig, 727, 620);
            draw(g, art, null, striker.rig, 1192, 620);
            draw(g, art, null, charger.rig, 1657, 620);
            g.dispose();
            frames.add(output);
        }
        return frames;
    }

    /** Four Brutes, each killed mid-action at {@link #DEATH_TICK}, then lying where they burst. */
    private List<BufferedImage> deaths(RavBruteAssets art, RavRunnerAssets ichor) {
        Actor[] actors = {new Actor(11L), new Actor(12L), new Actor(13L), new Actor(14L)};
        List<BufferedImage> frames = new ArrayList<>();
        for (int tick = 0; tick < DEATH_TICKS; tick++) {
            boolean dead = tick >= DEATH_TICK;
            if (!dead) {
                actors[0].trot(tick + 40);
                actors[1].strike();
                actors[2].charge(tick + 15);
                actors[3].rig.advance(0f, 0f, 0f, 1f, RavBruteRig.IDLE, true, DT);
            } else {
                for (Actor a : actors) a.rig.advanceDead(a.x, a.y, DT);
            }
            BufferedImage output = canvas();
            Graphics2D g = graphics(output);
            text(g, "RAV BLOOM BRUTE / DEATH ON ITS SKELETON", 40, 52, 28);
            text(g, ("Four Brutes killed mid-action at tick %d, then bursting into pieces and ichor"
                    + " · %.0f px per cell · tick %d%s").formatted(DEATH_TICK, PPC, tick, dead ? " · DEAD" : ""),
                    40, 86, 17);
            for (int i = 0; i < 4; i++) {
                int px = 40 + i * 465;
                g.setColor(new Color(0x3E4A36));
                g.fillRect(px, 120, 445, 900);
                text(g, "killed while " + DEATH_NAMES[i], px + 10, 1050, 16);
                draw(g, art, ichor, actors[i].rig, px + 222, 570);
            }
            g.dispose();
            frames.add(output);
        }
        return frames;
    }

    /** Top: the trotting Brute as it bursts. Bottom: all four corpses where they came to rest. */
    private static BufferedImage deathSheet(List<BufferedImage> frames) {
        BufferedImage output = canvas();
        Graphics2D g = graphics(output);
        text(g, "RAV BLOOM BRUTE / CORPSES", 40, 46, 26);
        text(g, "Top: a trotting Brute killed and bursting. Bottom: four Brutes at rest where they burst, each"
                + " killed in a different action. 140 px per cell.", 40, 76, 16);
        int[] fall = {DEATH_TICK - 1, DEATH_TICK + 3, DEATH_TICK + 7, DEATH_TICK + 16};
        String[] fallNames = {"alive, trotting", "bursts", "pieces flying, ichor spraying", "landed"};
        for (int i = 0; i < 4; i++) {
            int x = i * 480, y = 100;
            g.drawImage(frames.get(fall[i]), x, y, x + 480, y + 460, 40, 340, 485, 800, null);
            text(g, fallNames[i], x + 16, y + 482, 16);
        }
        BufferedImage last = frames.get(frames.size() - 1);
        for (int i = 0; i < 4; i++) {
            int x = i * 480, y = 590;
            int sx = 40 + i * 465 + 222;
            g.drawImage(last, x, y, x + 480, y + 460, sx - 222, 340, sx + 223, 800, null);
            text(g, "corpse: killed while " + DEATH_NAMES[i], x + 16, y + 482, 16);
        }
        g.dispose();
        return output;
    }

    /** One Brute centred on (x, y) of the sheet, y down; dead, it is drawn burst over {@code ichor}. */
    private void draw(Graphics2D g, RavBruteAssets art, RavRunnerAssets ichor, RavBruteRig rig, float x, float y) {
        LayeredUnitComposer.SpriteEmitter out = (sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        };
        if (rig.dead()) {
            RavBruteComposer.emitDead(out, art, ichor.splat, ichor.drop, rig, x, 1080f - y, PPC, 1f);
        } else {
            RavBruteComposer.emit(out, art, rig, x, 1080f - y, PPC, 1f);
        }
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
