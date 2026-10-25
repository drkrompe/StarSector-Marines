package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBombardRig;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
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
 * The Bombard's look, large enough to judge: its skeleton ({@link RavBombardRig}) stepped every tick and drawn through
 * the production {@link RavBombardComposer}, so this is exactly what the battle draws, magnified. No battle is played.
 * Three panels: walking (the legs and the swaying tail), the lob as the battle stages it (walking in, planting, the
 * wind-up curling the tail over the back, the whip that throws the glob, the settle), and its death: standing, then
 * bursting over the Runner's blood. A sheet of the lob's beats and one of the death's go with the GIF. The sac's
 * additive glow is drawn by the battle's light pass and is not shown here. 1920x1080 at 100% scale, one frame per
 * simulation tick.
 */
public final class RavBombardLookSnapshotSuite implements SnapshotSuite {

    private static final float DT = 1f / 30f;
    private static final int TICKS = 240;
    /** Screen px per cell: about seven times the game's normal battle zoom. */
    private static final float PPC = 170f;
    /** The lob panel's script, seconds: walking in, planting ({@code SwarmBombards}' plant), winding up, the release. */
    private static final float PLANT_AT = 0.5f, PLANT_SECONDS = 3f, WINDUP_SECONDS = 3f;
    private static final float RELEASE_AT = PLANT_AT + PLANT_SECONDS + WINDUP_SECONDS;
    /** The tick the dying Bombard dies on, and the death sheet's beats after it, seconds. */
    private static final int DEATH_TICK = 20;
    private static final float[] DEATH_BEATS = {0.12f, 0.35f, 0.8f, 2.45f};
    private static final RavWear.Marks NO_MARKS = (part, slots, index, x, y, w, h, angle, alpha) -> { };
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;

    @Override public String id() { return "bombard-look"; }

    @Override public String label() { return "Rav Bombard: the walk, the plant, the wind-up and the lob, and its death, magnified"; }

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
        RavBombardAssets art = RavBombardAssets.load(loader);
        if (art == null) throw new IllegalStateException("Rav Bombard art missing under " + modRoot);
        LayeredSpriteCache[] splat = new LayeredSpriteCache[4];
        for (int i = 0; i < splat.length; i++) splat[i] = loader.apply(RavRunnerAssets.ROOT + "ichor-splat-" + i + ".png");
        LayeredSpriteCache drop = loader.apply(RavRunnerAssets.ROOT + "ichor-drop.png");

        RavBombardRig walker = new RavBombardRig(3L), lobber = new RavBombardRig(5L), dying = new RavBombardRig(6L);
        for (RavBombardRig rig : new RavBombardRig[]{walker, lobber, dying}) rig.setStage(SwarmStage.ADULT);
        float walkX = 0f, walkY = 0f, lobY = 0f, cooldown = 0f;
        List<BufferedImage> frames = new ArrayList<>();
        List<String> lobBeats = new ArrayList<>();
        List<BufferedImage> lobFrames = new ArrayList<>();
        List<BufferedImage> deathFrames = new ArrayList<>();
        int nextDeathBeat = 0;
        String lastLobBeat = null;
        for (int tick = 0; tick < TICKS; tick++) {
            float t = tick * DT;
            float facing = 30f * (float) Math.sin(2 * Math.PI * t / 4.0);
            double rad = Math.toRadians(facing);
            walkX += (float) -Math.sin(rad) * 1.1f * DT;
            walkY += (float) Math.cos(rad) * 1.1f * DT;
            walker.advance(walkX, walkY, facing, 0f, 0f, 0f, DT);

            if (t < PLANT_AT) lobY += 1.1f * DT;
            float planted = t < PLANT_AT ? 0f : Math.min(1f, (t - PLANT_AT) / PLANT_SECONDS);
            float windup = t < PLANT_AT + PLANT_SECONDS || t >= RELEASE_AT ? 0f
                    : (t - PLANT_AT - PLANT_SECONDS) / WINDUP_SECONDS;
            cooldown = Math.max(0f, cooldown - DT);
            if (t >= RELEASE_AT && t - DT < RELEASE_AT) cooldown = 8f;   // the battle resets the cooldown as it throws
            lobber.advance(0f, lobY, 0f, cooldown, windup, planted, DT);
            String beat = t < PLANT_AT ? "walking in" : planted < 1f ? "planting" : windup > 0f ? "winding up"
                    : t < RELEASE_AT + 0.25f ? "the whip: the glob thrown" : "settled, planted";

            if (tick < DEATH_TICK) dying.advance(0f, 0f, 20f, 0f, 0f, 1f, DT);
            else dying.advanceDead(0f, 0f, DT);

            BufferedImage output = canvas();
            Graphics2D g = graphics(output);
            text(g, "RAV BLOOM BOMBARD / LOOK ON ITS SKELETON", 40, 52, 28);
            text(g, "1920 × 1080 · 100%% UI scale · production RavBombardComposer · %.0f px per cell · tick %d"
                    .formatted(PPC, tick), 40, 86, 17);
            for (int p = 0; p < 3; p++) panel(g, 40 + p * 620, 600);
            text(g, "Walking: legs %+.0f°, tail %+.0f°".formatted(walker.legSwing(), walker.tailAngle(6)), 50, 1050, 15);
            text(g, "Lob: %s · plant %.2f, wind-up %.2f, curl %.2f".formatted(beat, planted, windup, lobber.curl()),
                    670, 1050, 15);
            text(g, dying.dead() ? "Dead: burst, %.1f s".formatted(dying.burst().sinceDeath()) : "About to die",
                    1290, 1050, 15);
            draw(g, art, walker, 340, 640);
            draw(g, art, lobber, 960, 640);
            if (dying.dead()) {
                RavBombardComposer.emitDead(emitter(g), art, splat, drop, dying, 1580, 1080f - 640, PPC, 1f);
            } else {
                draw(g, art, dying, 1580, 640);
            }
            g.dispose();
            frames.add(output);
            if (!beat.equals(lastLobBeat) || tick == (int) ((PLANT_AT + PLANT_SECONDS + WINDUP_SECONDS * 0.6f) / DT)) {
                lobBeats.add(beat + " (tick " + tick + ")");
                lobFrames.add(output);
                lastLobBeat = beat;
            }
            if (tick == DEATH_TICK - 1 || nextDeathBeat < DEATH_BEATS.length && dying.dead()
                    && dying.burst().sinceDeath() >= DEATH_BEATS[nextDeathBeat]) {
                if (dying.dead()) nextDeathBeat++;
                deathFrames.add(output);
            }
        }
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(SnapshotArtifact.colorAnimation("bombard-look.gif", frames, 33));
        artifacts.add(new SnapshotArtifact("bombard-lob-beats.png", sheet("THE LOB", lobFrames, lobBeats, 660)));
        artifacts.add(new SnapshotArtifact("bombard-death.png", sheet("DEATH", deathFrames,
                List.of("alive", "the burst", "pieces in the air", "landing", "settled"), 1280)));
        return artifacts;
    }

    /** One panel (its left edge at panelX) at each of its beats, side by side. */
    private static BufferedImage sheet(String title, List<BufferedImage> frames, List<String> names, int panelX) {
        BufferedImage output = canvas();
        Graphics2D g = graphics(output);
        text(g, "RAV BLOOM BOMBARD / " + title, 40, 46, 26);
        int n = Math.min(frames.size(), names.size());
        int w = 1920 / Math.max(1, n);
        int h = Math.min(900, w * 900 / 600);
        for (int i = 0; i < n; i++) {
            int x = i * w, y = 100;
            g.drawImage(frames.get(i), x, y, x + w, y + h, panelX, 120, panelX + 600, 1020, null);
            text(g, names.get(i), x + 16, y + h + 30, 16);
        }
        g.dispose();
        return output;
    }

    private void draw(Graphics2D g, RavBombardAssets art, RavBombardRig rig, float x, float y) {
        // the pose is in the body's own frame, so each panel keeps its Bombard centred as it walks
        RavBombardComposer.emit(emitter(g), art, rig, x, 1080f - y, PPC, 1f, NO_MARKS);
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
