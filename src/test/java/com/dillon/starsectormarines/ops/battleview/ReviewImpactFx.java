package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.FittedTextureOverlay;
import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactDecals;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxRuntime;
import com.dillon.starsectormarines.ops.BattleScreen;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.tools.snapshot.SnapshotWorkers;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

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

    /** Fixed, so the same battle drawn twice jitters its smoke and blasts the same way. */
    private static final long REVIEW_SEED = 0x5EEDFACEL;
    private final ImpactFx fx = new ImpactFx(REVIEW_SEED);
    private final Random decalRng = new Random(REVIEW_SEED);
    private final ContrailFxService contrails = new ContrailFxService();
    private final ProjectileTrailFxService trails = new ProjectileTrailFxService();
    private final BeamFxService beams = new BeamFxService();
    /** Muzzle, impact, blast and fire lights, fed as the screen feeds its renderer's; only a GL frame draws them. */
    private final GroundLightService lights = new GroundLightService();
    private final TurretFx turretFx = new TurretFx(REVIEW_SEED);
    private OrdnanceTraceFxService traces;
    private final DrawList overlay = new DrawList();
    private final Path modRoot;
    private final Map<String, BufferedImage> art = new HashMap<>();
    private final List<Draw> draws = new ArrayList<>();
    private int lastPainted;
    private final SettingsAPI settings;
    private final List<Path> roots;

    public ReviewImpactFx(Path modRoot) {
        this.modRoot = modRoot.toAbsolutePath().normalize();
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

    /**
     * Takes in the tick the simulation just played and advances the presentation by it, in the order
     * {@code BattleScreen.advance} drains the same lists: weapon muzzle and impact compositions, lingering beams,
     * wall dust, heavy blasts, ambient smoke and fire, delivered ordnance, projectile trails, then the clocks.
     */
    public void feed(BattleSimulation sim) {
        withSettings(() -> feedTick(sim));
    }

    private void feedTick(BattleSimulation sim) {
        float dt = BattleSimulation.TICK_DT;
        NavigationGrid grid = sim.getGrid();
        for (float[] dust : sim.getWallDustsThisFrame()) fx.spawnWallCollapse(dust[0], dust[1], dust.length > 2 ? dust[2] : 1f);
        beams.advance(dt);
        for (ShotEvent shot : sim.getShotsThisFrame()) {
            beams.spawn(shot);
            lights.spawnMuzzle(shot);
            WeaponFxRuntime.spawnMuzzle(fx, shot);
            turretFx.onShot(shot, sim, fx);
            // A line tracer lands its impact the instant it fires; a travelling body lands it when it expires.
            if (ShotFx.of(shot).travels() || !shot.impacts()) continue;
            WeaponFxRuntime.spawnImpactAndAftermath(fx, shot, isWallAt(grid, shot.toX, shot.toY));
            lights.spawnImpact(WeaponFxRuntime.definition(shot), shot.toX, shot.visualToY());
        }
        for (ShotEvent shot : sim.getShotsExpiredThisFrame()) {
            if (!ShotFx.of(shot).travels() || !shot.impacts()) continue;
            WeaponFxRuntime.spawnImpactAndAftermath(fx, shot, isWallAt(grid, shot.toX, shot.toY));
            lights.spawnImpact(WeaponFxRuntime.definition(shot), shot.toX, shot.visualToY());
            // A weapon's own authored ground mark (Spitter acid) as the screen drops it; the stock craters and
            // casings the screen also drops stay out of review frames, as they always have.
            if (shot.toZ <= 0f) ImpactDecals.spawnAuthored(sim, decalRng, shot.weaponDef(), shot.toX, shot.toY);
        }
        for (float[] point : sim.getShots().getInterceptPointsThisFrame()) {
            fx.spawnHeavyImpact(point[0], point[1], BattleScreen.INTERCEPT_BURST_CELLS);
            lights.spawnHeavyImpact(point[0], point[1], BattleScreen.INTERCEPT_BURST_CELLS);
        }
        for (float[] impact : sim.getHeavyImpactsThisFrame()) {
            Color tint = impact.length >= 6 ? new Color(impact[3], impact[4], impact[5]) : null;
            fx.spawnHeavyImpact(impact[0], impact[1], impact[2], tint);
            lights.spawnHeavyImpact(impact[0], impact[1], impact[2], tint);
        }
        for (var blast : sim.getRollingBlastsThisFrame()) {
            fx.spawnRollingBlast(blast);
            lights.spawnHeavyImpact(blast.x(), blast.y(), blast.radius(), null);
        }
        for (float[] puff : sim.getSmokePuffsThisFrame()) fx.spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        for (float[] dust : sim.getGroundDustThisFrame()) {
            fx.spawnGroundDust(dust[0], dust[1], dust[2], dust[3], dust[4], (int) dust[5]);
        }
        for (float[] s : sim.getClawStrikesThisFrame()) fx.spawnClawStrike(s[0], s[1], s[2], s[3], s[4]);
        for (float[] h : sim.getSwarmHitsThisFrame()) fx.spawnSwarmHit(h);
        for (float[] burst : sim.getFireBurstsThisFrame()) {
            fx.spawnAmbientFire(burst[0], burst[1], burst[2]);
            lights.spawnFire(burst[0], burst[1], burst[2]);
        }
        turretFx.advance(sim, fx, dt);
        for (OrdnanceRelease release : sim.getOrdnanceReleasesThisFrame()) {
            traces().spawn(release);
            OrdnanceFxRuntime.spawnRelease(fx, lights, release);
        }
        if (traces != null) {
            traces.advance(dt);
            for (OrdnanceRelease arrival : traces.arrivalsThisFrame()) OrdnanceFxRuntime.spawnArrival(fx, lights, arrival);
        }
        trails.tick(sim.getActiveShots(), fx);
        fx.advance(dt);
        lights.advance(dt);
        lights.syncBoltLights(sim.getActiveShots());
        lights.syncFireLights(sim.fireFieldIfPresent());
        contrails.tick(sim.getActiveShots(), dt);
    }

    private OrdnanceTraceFxService traces() {
        if (traces == null) {
            try {
                // Its falling rounds are drawn by whichever drain draws the frame, so they carry that drain's tokens.
                GlReviewDrain gpu = ReviewSceneBackend.gpu(modRoot);
                traces = new OrdnanceTraceFxService(gpu != null ? gpu.sprites() : HeadlessBattleSceneRenderer.spritesFor(modRoot));
            } catch (Exception failure) {
                throw new IllegalStateException("Could not prepare headless sprites for ordnance traces", failure);
            }
        }
        return traces;
    }

    /** A solid, non-walkable endpoint reads as a chip on material; open ground as a kick of dust. */
    private static boolean isWallAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        return grid.inBounds(cx, cy) && !grid.isWalkable(cx, cy);
    }

    /** The event lights this feed has been keeping, for a drain whose ground composite draws them. */
    GroundLightService lights() {
        return lights;
    }

    /**
     * The contrails, lingering beams and falling rounds as production draw commands for the SHOTS layer, which the
     * scene drain draws under the shot bodies — the order the screen collects them in.
     */
    DrawList overlay(BattleCamera camera) {
        overlay.clear();
        contrails.collect(overlay, 1f);
        if (traces != null) traces.collect(camera, overlay, 1f);
        beams.collect(camera, overlay, 1f);
        return overlay;
    }

    /**
     * How many particles the last {@link #paint} laid inside its frame: how much is going on in that view, which a
     * look recording reads to choose its stills.
     */
    public int lastPainted() {
        return lastPainted;
    }

    /** Paints the live particles over {@code image}, which is as tall as the camera's screen is flipped against. */
    public void paint(BufferedImage image, BattleCamera camera) {
        draws.clear();
        withSettings(() -> fx.render(camera, 1f));
        List<Draw> painted = new ArrayList<>(draws.size());
        List<BufferedImage> sheets = new ArrayList<>(draws.size());
        for (Draw draw : draws) {
            BufferedImage sheet = art(draw.path);
            if (sheet == null) continue;
            painted.add(draw);
            sheets.add(sheet);
        }
        draws.clear();
        lastPainted = 0;
        for (Draw draw : painted) {
            float y = image.getHeight() - draw.centerY;
            if (draw.centerX >= 0f && draw.centerX < image.getWidth() && y >= 0f && y < image.getHeight()) lastPainted++;
        }
        if (!fastTarget(image) || SnapshotWorkers.threads() <= 1) {
            for (int i = 0; i < painted.size(); i++) {
                blend(image, sheets.get(i), painted.get(i), image.getHeight() - painted.get(i).centerY);
            }
            return;
        }
        // Each particle's turned and scaled cell is its own small raster, so they are made side by side; then the
        // frame is blended in horizontal bands, every band taking every particle in the recorded order, so each pixel
        // sees exactly the blends it saw one particle at a time.
        Particle[] particles = new Particle[painted.size()];
        List<Callable<Void>> cells = new ArrayList<>(painted.size());
        for (int i = 0; i < painted.size(); i++) {
            int index = i;
            cells.add(() -> {
                particles[index] = particle(sheets.get(index), painted.get(index), image.getHeight() - painted.get(index).centerY);
                return null;
            });
        }
        SnapshotWorkers.runAll(cells);
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int bands = SnapshotWorkers.threads();
        List<Callable<Void>> rows = new ArrayList<>(bands);
        for (int band = 0; band < bands; band++) {
            int top = image.getHeight() * band / bands, bottom = image.getHeight() * (band + 1) / bands;
            rows.add(() -> {
                for (Particle particle : particles) {
                    if (particle != null) particle.blend(target, image.getWidth(), image.getHeight(), top, bottom);
                }
                return null;
            });
        }
        SnapshotWorkers.runAll(rows);
    }

    /** A packed, unshared 0xAARRGGBB frame whose pixels can be read and written as the ints {@code getRGB} returns. */
    private static boolean fastTarget(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_INT_ARGB && image.getRaster().getParent() == null
                && image.getRaster().getDataBuffer() instanceof DataBufferInt buffer && buffer.getNumBanks() == 1
                && buffer.getOffset() == 0 && image.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel model
                && model.getScanlineStride() == image.getWidth();
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
                image = FittedTextureOverlay.read(path, file);
            } catch (IOException unreadable) {
                image = null;
            }
            if (image != null) break;
        }
        art.put(path, image);
        return image;
    }

    private static void blend(BufferedImage target, BufferedImage sheet, Draw draw, float centerY) {
        Particle particle = particle(sheet, draw, centerY);
        if (particle == null) return;
        for (int y = 0; y < particle.span; y++) {
            int ty = particle.originY + y;
            if (ty < 0 || ty >= target.getHeight()) continue;
            for (int x = 0; x < particle.span; x++) {
                int tx = particle.originX + x;
                if (tx < 0 || tx >= target.getWidth()) continue;
                int out = particle.over(particle.pixels[y * particle.span + x], target.getRGB(tx, ty));
                if (out != NO_CHANGE) target.setRGB(tx, ty, out);
            }
        }
    }

    /** Returned by {@link Particle#over} for a source pixel that leaves the target as it was. */
    private static final int NO_CHANGE = 0x00FFFFFF;

    /** One recorded draw's cell, turned and scaled into a square of its own, with where it lands and how it tints. */
    private static Particle particle(BufferedImage sheet, Draw draw, float centerY) {
        int sheetW = sheet.getWidth();
        int sheetH = sheet.getHeight();
        int sx = Math.max(0, Math.min(sheetW - 1, Math.round(draw.texX * sheetW)));
        // SpriteAPI UVs start at the bottom; ImageIO source rows start at the top.
        int sy = Math.max(0, Math.min(sheetH - 1, Math.round((1f - draw.texY - draw.texH) * sheetH)));
        int sw = Math.max(1, Math.min(sheetW - sx, Math.round(draw.texW * sheetW)));
        int sh = Math.max(1, Math.min(sheetH - sy, Math.round(draw.texH * sheetH)));
        BufferedImage cell = sheet.getSubimage(sx, sy, sw, sh);
        int span = (int) Math.ceil(Math.hypot(draw.width, draw.height)) + 2;
        if (span <= 2 || span > 4096) return null;
        BufferedImage scratch = new BufferedImage(span, span, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate(span / 2.0, span / 2.0);
        g.rotate(-Math.toRadians(draw.angle));
        g.drawImage(cell, Math.round(-draw.width / 2f), Math.round(-draw.height / 2f),
                Math.round(draw.width), Math.round(draw.height), null);
        g.dispose();
        return new Particle(((DataBufferInt) scratch.getRaster().getDataBuffer()).getData(), span,
                Math.round(draw.centerX - span / 2f), Math.round(centerY - span / 2f),
                draw.color.getRed() / 255f, draw.color.getGreen() / 255f, draw.color.getBlue() / 255f,
                draw.color.getAlpha() / 255f * draw.alpha, draw.additive);
    }

    /** A particle ready to lay over a frame. {@link #over} is the one blend both paths use. */
    private record Particle(int[] pixels, int span, int originX, int originY,
                            float tintR, float tintG, float tintB, float tintA, boolean additive) {

        /** The blended pixel, or {@link #NO_CHANGE} when the source is fully transparent here. */
        int over(int s, int d) {
            float a = ((s >>> 24) & 0xFF) / 255f * tintA;
            if (a <= 0f) return NO_CHANGE;
            float r = ((s >> 16) & 0xFF) * tintR;
            float gr = ((s >> 8) & 0xFF) * tintG;
            float b = (s & 0xFF) * tintB;
            float dr = (d >> 16) & 0xFF;
            float dg = (d >> 8) & 0xFF;
            float db = d & 0xFF;
            int outR;
            int outG;
            int outB;
            if (additive) {
                outR = Math.min(255, Math.round(dr + r * a));
                outG = Math.min(255, Math.round(dg + gr * a));
                outB = Math.min(255, Math.round(db + b * a));
            } else {
                outR = Math.round(dr * (1f - a) + r * a);
                outG = Math.round(dg * (1f - a) + gr * a);
                outB = Math.round(db * (1f - a) + b * a);
            }
            return 0xFF000000 | (outR << 16) | (outG << 8) | outB;
        }

        /** {@link ReviewImpactFx#blend}, over rows {@code top} (inclusive) to {@code bottom} of a packed frame. */
        void blend(int[] target, int width, int height, int top, int bottom) {
            int from = Math.max(Math.max(0, top), originY), to = Math.min(Math.min(height, bottom), originY + span);
            for (int ty = from; ty < to; ty++) {
                int row = (ty - originY) * span;
                for (int x = 0; x < span; x++) {
                    int tx = originX + x;
                    if (tx < 0 || tx >= width) continue;
                    int out = over(pixels[row + x], target[ty * width + tx]);
                    if (out != NO_CHANGE) target[ty * width + tx] = out;
                }
            }
        }
    }

    private SpriteAPI recordingSprite(String path) {
        SpriteAPI sprite = recordingSpriteFor(path);
        BufferedImage image = art(path);
        if (image != null) TextureUsage.name(sprite, path, image.getWidth(), image.getHeight());
        return sprite;
    }

    private SpriteAPI recordingSpriteFor(String path) {
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
