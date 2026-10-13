package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * The planetary shield dome's look, over a real generated city, at 1920x1080 and
 * game scale (no frame is resized after it is drawn).
 *
 * <p><b>This shades through {@link ShieldDomeShading}, a CPU model of the dome
 * shader, not the shader.</b> The Java2D snapshot suites cannot run GLSL. The
 * model and the shader were compared on a real driver by
 * {@code gradlew.bat shaderEvidence} ({@code ShieldDomeShaderGlEvidence}), which
 * is what makes these frames fair evidence of the look; they are not evidence
 * that a particular machine renders it, and the colour and motion want one live
 * look in a battle.
 *
 * <p>Every frame is the same ground under a different dome state, so what varies
 * is the dome: steady state for a friendly and a hostile dome, the fill ladder,
 * an impact sequence (six rounds of different weight, one still sheet and one
 * animation), and the collapse and recovery.
 */
public final class ShieldDomeLookSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int MAP_W = 150;
    private static final int MAP_H = 90;
    private static final long SEED = 11L;
    private static final TraversalAxis AXIS = TraversalAxis.SOUTH_TO_NORTH;
    private static final float DOME_RADIUS_CELLS = 16f;
    /** A shield fort declares the ground it covers, so its dome is several times the bare generator's. */
    private static final float FORT_DOME_RADIUS_CELLS = 60f;
    private static final float CAPACITY = 1500f;

    @Override
    public String id() {
        return "shield-dome-look";
    }

    @Override
    public String label() {
        return "Planetary shield dome: the shader's look (CPU model) in steady state, fill, impacts and collapse";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, SEED, AXIS);
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());

        List<SnapshotArtifact> out = new ArrayList<>();
        out.add(new SnapshotArtifact("steady-state.png", steady(renderer.render(map, SEED, 20))));
        out.add(new SnapshotArtifact("fill-ladder.png", ladder(renderer.render(map, SEED, 14))));
        // A fort-sized dome is wider than the small map the other sheets use, so it gets a map large enough to fill the frame.
        MapResult wide = new BspCityGenerator().generate(280, 170, SEED, AXIS);
        out.add(new SnapshotArtifact("fort-scale.png", fortScale(renderer.render(wide, SEED, 7))));
        BufferedImage large = renderer.render(map, SEED, 28);
        out.add(new SnapshotArtifact("impact-stills.png", impactStills(large)));
        out.add(SnapshotArtifact.colorAnimation("impacts.gif", impactFrames(large), 100));
        out.add(new SnapshotArtifact("collapse-recovery.png", collapse(renderer.render(map, SEED, 10))));
        return out;
    }

    // ---- sheets --------------------------------------------------------------

    private static BufferedImage steady(BufferedImage ground) {
        BufferedImage frame = crop(ground, WIDTH, HEIGHT);
        composite(frame, dome(WIDTH * 0.27f, HEIGHT * 0.5f, 20f, Faction.MARINE, 0.18f), 2.4f);
        composite(frame, dome(WIDTH * 0.73f, HEIGHT * 0.5f, 20f, Faction.DEFENDER, 0.80f), 2.4f);
        caption(frame, "Steady state at game scale (20 px a cell). Friendly dome, pool at 18%; hostile dome, pool at 80%.");
        return frame;
    }

    private static BufferedImage fortScale(BufferedImage ground) {
        BufferedImage frame = crop(ground, WIDTH, HEIGHT);
        composite(frame, dome(WIDTH * 0.25f, HEIGHT * 0.5f, 7f, FORT_DOME_RADIUS_CELLS, Faction.MARINE, 0.18f), 2.4f);
        ShieldDomeDraw struck = dome(WIDTH * 0.75f, HEIGHT * 0.5f, 7f, FORT_DOME_RADIUS_CELLS, Faction.DEFENDER, 0.5f);
        struck.impactCount = 2;
        struck.impacts[0] = -0.4f;
        struck.impacts[1] = 0.3f;
        struck.impacts[2] = 0.3f;
        struck.impacts[3] = 0.9f;
        struck.impacts[4] = 0.55f;
        struck.impacts[5] = -0.2f;
        struck.impacts[6] = 0.9f;
        struck.impacts[7] = 0.6f;
        composite(frame, struck, 2.7f);
        caption(frame, "A shield fort's dome at 7 px a cell, 60 cells of reach (a bare generator's is 16): the same rim, hexes and ripples, scaled with the radius.");
        return frame;
    }

    private static BufferedImage ladder(BufferedImage ground) {
        BufferedImage frame = crop(ground, WIDTH, HEIGHT);
        float[] levels = {0f, 0.5f, 0.95f};
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                Faction holder = row == 0 ? Faction.MARINE : Faction.DEFENDER;
                float cx = WIDTH * (col * 2 + 1) / 6f;
                float cy = HEIGHT * (row * 2 + 1) / 4f;
                composite(frame, dome(cx, cy, 14f, holder, levels[col]), 2.4f);
            }
        }
        caption(frame, "Fill ladder, 14 px a cell: pool empty, half, nearly full. More cells alight and the colour warms toward amber as the pool fills.");
        return frame;
    }

    private static BufferedImage impactStills(BufferedImage ground) {
        BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        float[] times = {0.05f, 0.45f, 0.95f, 1.30f};
        String[] words = {"t = 0.05 s: the first round lands", "t = 0.45 s: its ripple crossing, two more rounds",
                "t = 0.95 s: a heavy round", "t = 1.30 s: rounds at the rim"};
        for (int i = 0; i < times.length; i++) {
            BufferedImage world = crop(ground, WIDTH, HEIGHT);
            ShieldDomeDraw d = sequenceDome(WIDTH / 2f, HEIGHT / 2f, 28f, times[i]);
            composite(world, d, 2.4f + times[i]);
            int px = (i % 2) * (WIDTH / 2);
            int py = (i / 2) * (HEIGHT / 2);
            BufferedImage panel = world.getSubimage(WIDTH / 4, HEIGHT / 4, WIDTH / 2, HEIGHT / 2);
            Graphics2D g = frame.createGraphics();
            g.drawImage(panel, px, py, null);
            g.dispose();
            caption(frame, px, py + HEIGHT / 2 - 34, WIDTH / 2, words[i]);
        }
        return frame;
    }

    private static List<BufferedImage> impactFrames(BufferedImage ground) {
        List<BufferedImage> frames = new ArrayList<>();
        BufferedImage base = crop(ground, WIDTH, HEIGHT);
        float step = 0.1f;
        for (int i = 0; i < 25; i++) {
            float t = i * step;
            BufferedImage frame = copy(base);
            composite(frame, sequenceDome(WIDTH / 2f, HEIGHT / 2f, 28f, t), 2.4f + t);
            caption(frame, String.format(Locale.ROOT, "Six rounds of different weight, t = %.2f s", t));
            frames.add(frame);
        }
        return frames;
    }

    private static BufferedImage collapse(BufferedImage ground) {
        BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        String[] words = {"pool nearly full", "overload: the instant", "0.2 s: cells going out",
                "0.35 s: collapse spent", "down, 10% recovered", "down, 40% recovered",
                "down, 70% recovered", "down, 92% recovered", "back up, pool empty"};
        for (int i = 0; i < 9; i++) {
            BufferedImage world = crop(ground, WIDTH / 3, HEIGHT / 3);
            ShieldDomeDraw d = dome(WIDTH / 6f, HEIGHT / 6f, 10f, Faction.MARINE, 0f);
            switch (i) {
                case 0 -> d.level = 0.93f;
                case 1 -> down(d, 1f, 1f);
                case 2 -> down(d, 1f, 0.55f);
                case 3 -> down(d, 1f, 0.08f);
                case 4 -> down(d, 0.9f, 0f);
                case 5 -> down(d, 0.6f, 0f);
                case 6 -> down(d, 0.3f, 0f);
                case 7 -> down(d, 0.08f, 0f);
                default -> d.level = 0f;
            }
            composite(world, d, 3.1f);
            int px = (i % 3) * (WIDTH / 3);
            int py = (i / 3) * (HEIGHT / 3);
            Graphics2D g = frame.createGraphics();
            g.drawImage(world, px, py, null);
            g.dispose();
            caption(frame, px, py + HEIGHT / 3 - 30, WIDTH / 3, words[i]);
        }
        return frame;
    }

    // ---- the scripted sequence ----------------------------------------------

    private record Round(float at, float x, float y, float cost) {}

    private static final Round[] ROUNDS = {
            new Round(0.00f, -0.35f, 0.40f, 300f),
            new Round(0.27f, 0.30f, 0.15f, 120f),
            new Round(0.47f, 0.45f, -0.35f, 60f),
            new Round(0.80f, -0.15f, -0.55f, 380f),
            new Round(1.07f, 0.85f, 0.30f, 200f),
            new Round(1.13f, 0.80f, 0.42f, 180f)};

    /** One dome at time {@code t} into the scripted rounds, with its pool, flash and live impacts. */
    private static ShieldDomeDraw sequenceDome(float cx, float cy, float cellPx, float t) {
        ShieldDomeDraw d = dome(cx, cy, cellPx, Faction.MARINE, 0f);
        float flux = 0f;
        float flash = 0f;
        for (Round r : ROUNDS) {
            if (r.at > t) continue;
            float age = t - r.at;
            flux += r.cost;
            flash = Math.max(flash, Math.max(0f, 1f - age / ShieldDomeService.FLASH_SECONDS));
            if (age >= ShieldDomeService.IMPACT_SECONDS || d.impactCount >= ShieldDomeDraw.MAX_IMPACTS) continue;
            int at = d.impactCount * 4;
            d.impacts[at] = r.x;
            d.impacts[at + 1] = r.y;
            d.impacts[at + 2] = age;
            d.impacts[at + 3] = ShieldImpactField.intensity(r.cost, CAPACITY);
            d.impactCount++;
        }
        flux = Math.max(0f, flux - 60f * t);
        d.level = Math.min(1f, flux / CAPACITY);
        d.hitFlash = flash;
        return d;
    }

    private static void down(ShieldDomeDraw d, float recovery, float dropFlash) {
        d.overloaded = true;
        d.recovery = recovery;
        d.dropFlash = dropFlash;
    }

    private static ShieldDomeDraw dome(float cx, float cy, float cellPx, Faction holder, float level) {
        return dome(cx, cy, cellPx, DOME_RADIUS_CELLS, holder, level);
    }

    private static ShieldDomeDraw dome(float cx, float cy, float cellPx, float radiusCells, Faction holder, float level) {
        ShieldDomeDraw d = new ShieldDomeDraw();
        d.sx = cx;
        d.sy = cy;
        d.radiusCells = radiusCells;
        d.radiusPx = radiusCells * cellPx;
        float[] tint = ShieldDomeRenderSystem.tintFor(holder);
        System.arraycopy(tint, 0, d.tint, 0, 3);
        d.level = level;
        return d;
    }

    // ---- raster --------------------------------------------------------------

    /** Composites one dome through the CPU model onto {@code image} with the shader's blend: src + dst * (1 - a). */
    static void composite(BufferedImage image, ShieldDomeDraw d, float time) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int w = image.getWidth();
        int h = image.getHeight();
        float extent = ShieldDomeShading.EXTENT * d.radiusPx;
        int x0 = Math.max(0, (int) Math.floor(d.sx - extent));
        int x1 = Math.min(w - 1, (int) Math.ceil(d.sx + extent));
        int y0 = Math.max(0, (int) Math.floor(d.sy - extent));
        int y1 = Math.min(h - 1, (int) Math.ceil(d.sy + extent));
        IntStream.rangeClosed(y0, y1).parallel().forEach(y -> {
            float[] rgba = new float[4];
            for (int x = x0; x <= x1; x++) {
                float lx = (x + 0.5f - d.sx) / d.radiusPx;
                float ly = (d.sy - (y + 0.5f)) / d.radiusPx;
                if (lx * lx + ly * ly > ShieldDomeShading.EXTENT * ShieldDomeShading.EXTENT) continue;
                ShieldDomeShading.shade(d, lx, ly, time, rgba);
                if (rgba[3] <= 0f && rgba[0] + rgba[1] + rgba[2] <= 0f) continue;
                int dst = pixels[y * w + x];
                int r = channel(rgba[0], (dst >> 16) & 0xFF, rgba[3]);
                int g = channel(rgba[1], (dst >> 8) & 0xFF, rgba[3]);
                int b = channel(rgba[2], dst & 0xFF, rgba[3]);
                pixels[y * w + x] = (r << 16) | (g << 8) | b;
            }
        });
    }

    private static int channel(float src, int dst, float a) {
        float v = src + (dst / 255f) * (1f - a);
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    /** A {@code w x h} RGB crop from the middle of the ground, as its own image so it can be drawn on. */
    private static BufferedImage crop(BufferedImage ground, int w, int h) {
        int x = Math.max(0, (ground.getWidth() - w) / 2);
        int y = Math.max(0, (ground.getHeight() - h) / 2);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(ground.getSubimage(x, y, Math.min(w, ground.getWidth()), Math.min(h, ground.getHeight())), 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void caption(BufferedImage image, String text) {
        caption(image, 0, image.getHeight() - 34, image.getWidth(), text);
    }

    private static void caption(BufferedImage image, int x, int y, int width, String text) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x, y, width, 30);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString(text, x + 10, y + 21);
        g.dispose();
    }
}
