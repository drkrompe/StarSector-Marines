package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.fx.FxCompositionContext;
import com.dillon.starsectormarines.battle.weapon.fx.FxLayerKind;
import com.dillon.starsectormarines.battle.weapon.fx.FxParticleCommand;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxComposer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.tools.snapshot.AnimatedGifWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regenerates the two-second design review of the Tri-Arch shoulder laser's one-second beam. */
class LaserFxFeelPreviewTest {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 320;
    private static final int FRAMES = 50;
    private static final int FRAME_MILLIS = 40;
    private static final float REVIEW_SECONDS = FRAMES * FRAME_MILLIS / 1000f;
    private static final float CELL_PX = 22f;
    private static final float MUZZLE_X = 5f;
    private static final float IMPACT_X = 38f;
    private static final float BEAM_Y = 7f;

    @Test
    void writeTwoSecondLaserFeelLoop() throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();
        Path mod = project.resolve("mod");
        Path core = HeadlessBattleSceneRenderer.installedGameResources();
        WeaponDef laser = WeaponRegistry.require(WeaponRegistry.MECH_SHOULDER_LASER_ID);
        ShotEvent shot = new ShotEvent(MUZZLE_X, BEAM_Y, 0f, IMPACT_X, BEAM_Y, 0f,
                true, Faction.MARINE, 0.10f,
                null, null, null, laser, 1f, true, BallisticResolver.StopKind.WALL);
        BeamFxService beams = new BeamFxService();
        beams.spawn(shot);
        BattleCamera camera = new BattleCamera(42, 14);
        camera.setViewport(0f, 0f, WIDTH, HEIGHT, CELL_PX);

        List<FxParticleCommand> particles = new ArrayList<>();
        particles.addAll(WeaponFxComposer.compose(laser.fx, FxSlot.MUZZLE,
                new FxCompositionContext(MUZZLE_X, BEAM_Y, 90f, false, 0.314f)));
        particles.addAll(WeaponFxComposer.compose(laser.fx, FxSlot.IMPACT,
                new FxCompositionContext(IMPACT_X, BEAM_Y, 90f, false, 0.314f)));
        Assets assets = new Assets(mod, core);
        Path output = project.resolve("roadmap/mechs/previews/laser-1s-beam-2s-review.gif");

        try (AnimatedGifWriter gif = new AnimatedGifWriter(output, FRAME_MILLIS)) {
            for (int frame = 0; frame < FRAMES; frame++) {
                float age = frame * FRAME_MILLIS / 1000f;
                gif.append(frame(beams, camera, particles, assets, age));
                beams.advance(FRAME_MILLIS / 1000f);
            }
        }
        assertTrue(Files.size(output) > 20_000L, "preview should contain a real animated scene");
        try (ImageInputStream input = ImageIO.createImageInputStream(output.toFile())) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input);
                assertEquals(FRAMES, reader.getNumImages(true));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage frame(BeamFxService beams, BattleCamera camera,
                                       List<FxParticleCommand> particles,
                                       Assets assets, float age) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        paintBackdrop(g, age);

        DrawList beamCommands = new DrawList();
        beams.collect(camera, beamCommands, 1f);
        for (int i = 0; i < beamCommands.count(RenderLayer.SHOTS); i++) {
            drawLine(g, beamCommands.buffer(RenderLayer.SHOTS)[i]);
        }
        for (FxParticleCommand particle : particles) drawParticle(g, camera, assets, particle, age);

        drawImage(g, assets.weapon, camera.cellToScreenX(MUZZLE_X),
                HEIGHT - camera.cellToScreenY(BEAM_Y), 112f, 90f, Color.WHITE);
        paintCaption(g, age);
        g.dispose();
        return image;
    }

    private static void paintBackdrop(Graphics2D g, float age) {
        g.setColor(new Color(0x10, 0x18, 0x26));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(0x25, 0x34, 0x46));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= WIDTH; x += Math.round(CELL_PX)) g.drawLine(x, 0, x, HEIGHT);
        for (int y = 0; y <= HEIGHT; y += Math.round(CELL_PX)) g.drawLine(0, y, WIDTH, y);
        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(0, 0, WIDTH, 42);
        g.fillRect(0, HEIGHT - 30, WIDTH, 30);
        int progress = Math.min(WIDTH, Math.round(age / REVIEW_SECONDS * WIDTH));
        g.setColor(new Color(0x38, 0xA8, 0xFF));
        g.fillRect(0, HEIGHT - 5, progress, 5);
    }

    private static void paintCaption(Graphics2D g, float age) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.setColor(new Color(0xD8, 0xF8, 0xFF));
        g.drawString("TRI-ARCH LANCE CANNON — 1.0 s BEAM / 2.0 s REVIEW", 18, 27);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.setColor(new Color(0x9C, 0xD8, 0xFF));
        g.drawString(String.format(Locale.ROOT, "T+%.2f s", age), WIDTH - 110, 27);
    }

    private static void drawLine(Graphics2D g, DrawCommand command) {
        g.setColor(new Color(channel(command.red()), channel(command.green()),
                channel(command.blue()), channel(command.alpha())));
        g.setStroke(new BasicStroke(command.angleDegrees(), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.drawLine(Math.round(command.centerX()), HEIGHT - Math.round(command.centerY()),
                Math.round(command.width()), HEIGHT - Math.round(command.height()));
    }

    private static void drawParticle(Graphics2D g, BattleCamera camera, Assets assets,
                                     FxParticleCommand command, float snapshotAge)
            throws IOException {
        float age = snapshotAge - command.delaySeconds();
        if (age < 0f || age >= command.lifetimeSeconds()) return;
        float life = 1f - age / command.lifetimeSeconds();
        float x = command.x() + command.velocityX() * age;
        float y = command.y() + command.velocityY() * age;
        float radius = command.radiusCells() + command.radiusGrowthPerSecond() * age;
        BufferedImage sprite = assets.sprite(command, age);
        Color tint = new Color(command.color().getRed(), command.color().getGreen(),
                command.color().getBlue(), Math.round(255f * life));
        drawImage(g, sprite, camera.cellToScreenX(x), HEIGHT - camera.cellToScreenY(y),
                Math.max(2f, radius * 2f * CELL_PX), command.angleDegrees(), tint);
    }

    private static void drawImage(Graphics2D g, BufferedImage source,
                                  float centerX, float centerY, float height,
                                  float angleDegrees, Color tint) {
        BufferedImage image = tint(source, tint);
        float width = height * image.getWidth() / image.getHeight();
        AffineTransform transform = new AffineTransform();
        transform.translate(centerX, centerY);
        transform.rotate(Math.toRadians(angleDegrees));
        transform.translate(-width * 0.5, -height * 0.5);
        transform.scale(width / image.getWidth(), height / image.getHeight());
        g.drawImage(image, transform, null);
    }

    private static BufferedImage tint(BufferedImage source, Color tint) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float alpha = tint.getAlpha() / 255f;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int a = Math.round(((argb >>> 24) & 0xff) * alpha);
                int r = ((argb >>> 16) & 0xff) * tint.getRed() / 255;
                int green = ((argb >>> 8) & 0xff) * tint.getGreen() / 255;
                int b = (argb & 0xff) * tint.getBlue() / 255;
                result.setRGB(x, y, a << 24 | r << 16 | green << 8 | b);
            }
        }
        return result;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    private static final class Assets {
        final Path mod;
        final Path core;
        final BufferedImage weapon;
        final BufferedImage particleSheet;
        final BufferedImage glow;
        final BufferedImage ring;
        final BufferedImage[] explosions = new BufferedImage[7];

        Assets(Path mod, Path core) throws IOException {
            this.mod = mod;
            this.core = core;
            weapon = read("graphics/battle/mech-modular-topdown/shoulder-laser-cannon.png");
            particleSheet = read("graphics/particle/smokeAndFire.png");
            glow = read("graphics/fx/particlealpha64linear.png");
            ring = read("graphics/fx/explosion_ring0.png");
            for (int i = 0; i < explosions.length; i++) {
                explosions[i] = read("graphics/fx/explosion" + i + ".png");
            }
        }

        BufferedImage sprite(FxParticleCommand command, float age) {
            return switch (command.kind()) {
                case GLOW, DUST -> glow;
                case RING -> ring;
                case EXPLOSION -> explosions[Math.floorMod(command.variantIndex(), explosions.length)];
                case FIRE, SMOKE -> particleFrame(command.kind(), age, command.lifetimeSeconds());
            };
        }

        private BufferedImage particleFrame(FxLayerKind kind, float age, float lifetime) {
            int frame = Math.min(7, (int) (age / Math.max(0.001f, lifetime) * 8f));
            int index = (kind == FxLayerKind.SMOKE ? 0 : 8) + frame;
            int width = particleSheet.getWidth() / 4;
            int height = particleSheet.getHeight() / 4;
            return particleSheet.getSubimage(index % 4 * width, index / 4 * height, width, height);
        }

        private BufferedImage read(String relative) throws IOException {
            Path modPath = mod.resolve(relative);
            Path source = Files.isRegularFile(modPath) ? modPath
                    : core != null ? core.resolve(relative) : modPath;
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) throw new IOException("Could not read preview asset " + source);
            return image;
        }
    }
}
