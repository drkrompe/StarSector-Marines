package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's real {@link ImpactFx}, drawn into a Java2D review frame.
 *
 * <p>The particle backend is owned by the screen and drawn by a GL custom pass,
 * so the headless drain skips it and a landing blast has never appeared in a
 * review frame. This runs the <em>shipping</em> recipe and the shipping draw
 * arithmetic rather than a copy of either: the sprites {@code ImpactFx} asks the
 * game for are recording stand-ins that remember each {@code renderAtCenter}
 * (size, angle, tint, blend, flipbook cell), and {@link #paint} replays those
 * draws against the same art off disk. What it shows is therefore what the
 * recipe asks the GPU to draw, at the size it asks.
 *
 * <p><b>Fed every tick.</b> The simulation's per-frame event lists describe one
 * tick, and a review samples every few, so {@link #feed} must be called after
 * each advance or two blasts in three never exist. It mirrors the drains
 * {@code BattleScreen} makes into the same object.
 */
public final class ReviewImpactFx {

    private final ImpactFx fx = new ImpactFx();
    private final Map<String, BufferedImage> art = new HashMap<>();
    private final List<Draw> draws = new ArrayList<>();
    private final SettingsAPI settings;
    private final List<Path> roots;

    public ReviewImpactFx(Path modRoot) {
        this.roots = HeadlessBattleSceneRenderer.resourceRoots(modRoot.toAbsolutePath().normalize());
        this.settings = (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSprite") && args != null && args.length == 1) {
                        return recordingSprite((String) args[0]);
                    }
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "ReviewImpactFxSettings";
                        default -> HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                    };
                });
        withSettings(fx::ensureSprites);
    }

    /** Takes in the tick the simulation just played and advances the particles by it. */
    public void feed(BattleSimulation sim) {
        for (float[] dust : sim.getWallDustsThisFrame()) fx.spawnWallCollapse(dust[0], dust[1]);
        for (float[] impact : sim.getHeavyImpactsThisFrame()) {
            fx.spawnHeavyImpact(impact[0], impact[1], impact[2],
                    impact.length >= 6 ? new Color(impact[3], impact[4], impact[5]) : null);
        }
        for (float[] puff : sim.getSmokePuffsThisFrame()) fx.spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        for (float[] burst : sim.getFireBurstsThisFrame()) fx.spawnAmbientFire(burst[0], burst[1], burst[2]);
        fx.advance(BattleSimulation.TICK_DT);
    }

    /** Paints the live particles over {@code image}, which is as tall as the camera's screen is flipped against. */
    public void paint(BufferedImage image, BattleCamera camera) {
        draws.clear();
        withSettings(() -> fx.render(camera, 1f));
        for (Draw draw : draws) {
            BufferedImage sheet = art(draw.path);
            if (sheet == null) continue;
            blend(image, sheet, draw, image.getHeight() - draw.centerY);
        }
        draws.clear();
    }

    private void withSettings(Runnable action) {
        SettingsAPI previous = Global.getSettings();
        Global.setSettings(settings);
        try {
            action.run();
        } finally {
            Global.setSettings(previous);
        }
    }

    private BufferedImage art(String path) {
        if (art.containsKey(path)) return art.get(path);
        BufferedImage image = null;
        for (Path root : roots) {
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) continue;
            try {
                image = ImageIO.read(file.toFile());
            } catch (IOException unreadable) {
                image = null;
            }
            if (image != null) break;
        }
        art.put(path, image);
        return image;
    }

    private static void blend(BufferedImage target, BufferedImage sheet, Draw draw, float centerY) {
        int sheetW = sheet.getWidth();
        int sheetH = sheet.getHeight();
        int sx = Math.max(0, Math.min(sheetW - 1, Math.round(draw.texX * sheetW)));
        int sy = Math.max(0, Math.min(sheetH - 1, Math.round(draw.texY * sheetH)));
        int sw = Math.max(1, Math.min(sheetW - sx, Math.round(draw.texW * sheetW)));
        int sh = Math.max(1, Math.min(sheetH - sy, Math.round(draw.texH * sheetH)));
        BufferedImage cell = sheet.getSubimage(sx, sy, sw, sh);
        int span = (int) Math.ceil(Math.hypot(draw.width, draw.height)) + 2;
        if (span <= 2 || span > 4096) return;
        BufferedImage scratch = new BufferedImage(span, span, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate(span / 2.0, span / 2.0);
        g.rotate(-Math.toRadians(draw.angle));
        g.drawImage(cell, Math.round(-draw.width / 2f), Math.round(-draw.height / 2f),
                Math.round(draw.width), Math.round(draw.height), null);
        g.dispose();
        int originX = Math.round(draw.centerX - span / 2f);
        int originY = Math.round(centerY - span / 2f);
        float tintR = draw.color.getRed() / 255f;
        float tintG = draw.color.getGreen() / 255f;
        float tintB = draw.color.getBlue() / 255f;
        float tintA = draw.color.getAlpha() / 255f * draw.alpha;
        for (int y = 0; y < span; y++) {
            int ty = originY + y;
            if (ty < 0 || ty >= target.getHeight()) continue;
            for (int x = 0; x < span; x++) {
                int tx = originX + x;
                if (tx < 0 || tx >= target.getWidth()) continue;
                int s = scratch.getRGB(x, y);
                float a = ((s >>> 24) & 0xFF) / 255f * tintA;
                if (a <= 0f) continue;
                float r = ((s >> 16) & 0xFF) * tintR;
                float gr = ((s >> 8) & 0xFF) * tintG;
                float b = (s & 0xFF) * tintB;
                int d = target.getRGB(tx, ty);
                float dr = (d >> 16) & 0xFF;
                float dg = (d >> 8) & 0xFF;
                float db = d & 0xFF;
                int outR;
                int outG;
                int outB;
                if (draw.additive) {
                    outR = Math.min(255, Math.round(dr + r * a));
                    outG = Math.min(255, Math.round(dg + gr * a));
                    outB = Math.min(255, Math.round(db + b * a));
                } else {
                    outR = Math.round(dr * (1f - a) + r * a);
                    outG = Math.round(dg * (1f - a) + gr * a);
                    outB = Math.round(db * (1f - a) + b * a);
                }
                target.setRGB(tx, ty, 0xFF000000 | (outR << 16) | (outG << 8) | outB);
            }
        }
    }

    private SpriteAPI recordingSprite(String path) {
        State state = new State(path);
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setSize" -> { state.width = (Float) args[0]; state.height = (Float) args[1]; return null; }
                        case "setAngle" -> { state.angle = (Float) args[0]; return null; }
                        case "setAlphaMult" -> { state.alpha = (Float) args[0]; return null; }
                        case "setColor" -> { state.color = (Color) args[0]; return null; }
                        case "setAdditiveBlend" -> { state.additive = true; return null; }
                        case "setNormalBlend" -> { state.additive = false; return null; }
                        case "setTexX" -> { state.texX = (Float) args[0]; return null; }
                        case "setTexY" -> { state.texY = (Float) args[0]; return null; }
                        case "setTexWidth" -> { state.texW = (Float) args[0]; return null; }
                        case "setTexHeight" -> { state.texH = (Float) args[0]; return null; }
                        case "getTextureWidth", "getTextureHeight" -> { return 1f; }
                        case "renderAtCenter" -> {
                            draws.add(new Draw(state, (Float) args[0], (Float) args[1]));
                            return null;
                        }
                        case "hashCode" -> { return System.identityHashCode(proxy); }
                        case "equals" -> { return proxy == args[0]; }
                        default -> { return HeadlessBattleSprites.primitiveDefault(method.getReturnType()); }
                    }
                });
    }

    /** The mutable state of one stand-in sprite, as ImpactFx sets it before each draw. */
    private static final class State {
        final String path;
        float width, height, angle, alpha = 1f;
        Color color = Color.WHITE;
        boolean additive;
        float texX, texY, texW = 1f, texH = 1f;

        State(String path) { this.path = path; }
    }

    /** One recorded draw, frozen at the moment it was made. */
    private static final class Draw {
        final String path;
        final float centerX, centerY, width, height, angle, alpha, texX, texY, texW, texH;
        final Color color;
        final boolean additive;

        Draw(State s, float centerX, float centerY) {
            this.path = s.path;
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = s.width;
            this.height = s.height;
            this.angle = s.angle;
            this.alpha = s.alpha;
            this.color = s.color;
            this.additive = s.additive;
            this.texX = s.texX;
            this.texY = s.texY;
            this.texW = s.texW;
            this.texH = s.texH;
        }
    }
}
