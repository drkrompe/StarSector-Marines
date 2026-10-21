package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
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
 * The owner's art review of the swarm strain bodies: Runner, Brute and Spitter
 * through the production layered composer, magnified and at the game's normal
 * battle zoom, walking and striking, beside their corpse sheets. 1920x1080 at
 * 100% scale. The scene suites show the same bodies fighting; this sheet is
 * where they are large enough to judge.
 */
public final class SwarmStrainLayerSnapshotSuite implements SnapshotSuite {

    private static final String ALIEN = "graphics/battle/alien-modular-topdown/";
    private static final String BRUTE = "graphics/battle/swarm-brute-modular-topdown/";
    private static final String SPITTER = "graphics/battle/swarm-spitter-modular-topdown/";
    /** The game's cell size at its normal zoom on a 1920-wide screen. */
    private static final float CELL_PX = 17.142857f;
    private static final Color GROUND = new Color(0x4A4638);
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;

    @Override public String id() { return "swarm-strains"; }

    @Override public String label() { return "Swarm strain bodies: Runner, Brute, Spitter and their corpses"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        modRoot = context.modRoot();
        images.clear();
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < 12; i++) frames.add(review(i / 12f));
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("swarm-strains-sheet.png", frames.get(0)));
        artifacts.add(SnapshotArtifact.colorAnimation("swarm-strains-motion.gif", frames, 110));
        return artifacts;
    }

    private BufferedImage review(float phase) throws Exception {
        BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        text(g, "SWARM STRAINS / RUNNER, BRUTE, SPITTER", 40, 48, 28);
        text(g, "1920 × 1080 · 100% UI scale · production layered composition · corpses from each strain's sheet",
                40, 79, 17);

        LayeredUnitAssets runner = assets(ALIEN, ALIEN + "foot.png", ALIEN + "fore-claw.png");
        LayeredUnitAssets brute = assets(BRUTE, BRUTE + "foot.png", BRUTE + "fore-claw.png");
        LayeredUnitAssets spitter = assets(SPITTER, ALIEN + "foot.png", ALIEN + "fore-claw.png");
        LayeredUnitAssets[] strains = {runner, brute, spitter};
        UnitType[] types = {UnitType.SWARM_RUNNER, UnitType.SWARM_BRUTE, UnitType.SWARM_SPITTER};
        String[] names = {"Runner", "Brute", "Spitter"};

        // Magnified: each strain at its own render scale, so relative size reads true.
        text(g, "Magnified ×8 · relative sizes true · idle, walking, striking", 40, 125, 20);
        float magnify = 8f;
        for (int s = 0; s < 3; s++) {
            float sw = shoulder(types[s]) * magnify;
            int x0 = 130 + s * 620;
            actor(g, strains[s], x0, 300, sw, LayeredAppearance.POSE_IDLE, 0, phase, 0f);
            actor(g, strains[s], x0 + 200, 300, sw, LayeredAppearance.POSE_IDLE,
                    LayeredAppearance.FLAG_MOVING, phase, 0f);
            actor(g, strains[s], x0 + 400, 300, sw, LayeredAppearance.POSE_FIRING, 0, phase,
                    (phase * 3f) % 1f);
            text(g, names[s] + " · idle / walking / strike", x0 - 80, 445, 16);
        }
        divider(g, 470);

        // Corpse sheets, magnified.
        text(g, "Corpse sheets · all four poses, magnified ×2.5", 40, 510, 20);
        for (int s = 1; s < 3; s++) {
            BufferedImage sheet = image(types[s].deadSpritePath);
            SpriteSheetFrames frames = SpriteSheetSlicer.slice(sheet);
            float longSide = CELL_PX * types[s].renderScale * 2.5f * 2f;
            int x = 40 + (s - 1) * 940;
            for (SpriteSheetFrames.Frame f : frames.frames) {
                float scale = longSide / Math.max(f.w, f.h);
                int w = Math.round(f.w * scale);
                int h = Math.round(f.h * scale);
                g.drawImage(sheet, x, 640 - h / 2, x + w, 640 + h / 2,
                        f.x, f.y, f.x + f.w, f.y + f.h, null);
                x += w + 24;
            }
            text(g, names[s] + " corpses", 40 + (s - 1) * 940, 740, 16);
        }
        divider(g, 760);

        // Battle zoom on ground: what the player sees.
        text(g, "Battle zoom · %.1f px cells · a pack crossing, and the dead where they fell"
                .formatted(CELL_PX), 40, 795, 20);
        g.setColor(GROUND);
        g.fillRect(40, 810, 1840, 250);
        float[][] pack = {
                {0, 120, 860, 0}, {0, 180, 900, 15}, {0, 250, 850, -10}, {0, 320, 920, 5},
                {0, 400, 880, 20}, {0, 170, 1000, -5}, {0, 290, 980, 10}, {0, 380, 1020, 0},
                {1, 520, 900, 0}, {1, 560, 1000, 12},
                {2, 760, 880, -8}, {2, 820, 990, 6}, {2, 700, 1030, 0},
        };
        for (float[] p : pack) {
            int s = (int) p[0];
            actor(g, strains[s], p[1], p[2], shoulder(types[s]), s == 2 && p[3] == 0
                            ? LayeredAppearance.POSE_FIRING : LayeredAppearance.POSE_IDLE,
                    LayeredAppearance.FLAG_MOVING, phase + p[1] / 300f, 0.5f, -90f + p[3]);
        }
        int cx = 1100;
        for (int s = 0; s < 3; s++) {
            BufferedImage sheet = image(types[s].deadSpritePath);
            SpriteSheetFrames frames = SpriteSheetSlicer.slice(sheet);
            for (int i = 0; i < Math.min(4, frames.frames.length); i++) {
                SpriteSheetFrames.Frame f = frames.frames[i];
                float longSide = CELL_PX * types[s].renderScale;
                float scale = longSide / Math.max(f.w, f.h);
                int w = Math.round(f.w * scale);
                int h = Math.round(f.h * scale);
                int x = cx + i * 60;
                int y = 870 + s * 60;
                g.drawImage(sheet, x - w / 2, y - h / 2, x + w / 2, y + h / 2,
                        f.x, f.y, f.x + f.w, f.y + f.h, null);
            }
            text(g, names[s], cx + 260, 875 + s * 60, 14);
        }
        g.dispose();
        return output;
    }

    private static float shoulder(UnitType type) {
        return UnitRenderService.layeredInfantryShoulderWidth(CELL_PX, type.renderScale);
    }

    private void actor(Graphics2D g, LayeredUnitAssets assets, float x, float y, float sw,
                       int pose, int flags, float phase, float weaponPhase) {
        actor(g, assets, x, y, sw, pose, flags, phase, weaponPhase, 0f);
    }

    /** {@code facing} in the composer's degrees; 0 faces up the sheet, 90 to the left. */
    private void actor(Graphics2D g, LayeredUnitAssets assets, float x, float y, float sw,
                       int pose, int flags, float phase, float weaponPhase, float facing) {
        LayeredUnitComposer.emit((sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        }, assets, assets.head, null, false, null, EquipmentGrade.SERVICE,
                x, 1080f - y, sw, facing, 0f, phase, weaponPhase, pose, flags, 1f);
    }

    private LayeredUnitAssets assets(String root, String foot, String claw) throws Exception {
        return new LayeredUnitAssets(layer(root + "body.png"), layer(root + "head.png"),
                layer(foot), layer(claw), null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private LayeredSpriteCache layer(String path) throws Exception {
        BufferedImage image = image(path);
        return LayeredSpriteCache.headless(path, image.getWidth(), image.getHeight());
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

    private static void divider(Graphics2D g, int y) {
        g.setColor(new Color(0x2A3640));
        g.fillRect(40, y, 1840, 2);
    }

    private static void text(Graphics2D g, String value, int x, int y, int size) {
        g.setColor(new Color(0xD3DFE5));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }
}
