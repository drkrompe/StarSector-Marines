package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.RoofStyle;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Picture cards for the fallen hull's looks and the Long Survey's art picks (`long-survey-arc.md`, stories: Hull art;
 * `fallen-hull-nouns.md`, picks {@code hull-lineage} and {@code stripper-hauler}). The hull, its stakes, forge and
 * salvage walkers are not yet places or units in a battle, so each card is a real battle's look-preset frame at
 * 1920x1080 with the cut art laid over it at its footprint in cells, a soft contact shadow under each piece:
 * <ul>
 *   <li>{@code hull-lineage/}: the whole hull in each lineage's paint lying at the end of its trench across the
 *       civilian-rescue fixture's town, debris along the trench;</li>
 *   <li>{@code hull-pieces/}: the survey hull close, its debris field and trench patches; the {@code HULL} roof style's
 *       plating as a roofed block with a holed and a collapsed cell;</li>
 *   <li>{@code autoforge/}: each forge design lit and put out in the vast derelict ruin's ground beside its real vault
 *       frame;</li>
 *   <li>{@code stake/}: each stake design's three stages in a row beside a real line frame;</li>
 *   <li>{@code stripper-hauler/}: two units (a stripper and a hauler) against one unit that cuts and carries, at a scrap
 *       heap beside a real line frame.</li>
 * </ul>
 * The art is read from {@code build/fallen-hull-art} ({@code art-source/fallen-hull/export.py}), or the directory
 * {@code -Dsnapshot.fallenHull.art} names; a missing piece is skipped. Opt-in. No outcome is judged.
 */
public final class FallenHullLookSuite implements SnapshotSuite {

    static final String RESCUE_FIXTURE = "src/test/resources/battle-fixtures/civilian-rescue-v1.json";
    static final String ART_PROPERTY = "snapshot.fallenHull.art";
    static final int W = 1920;
    static final int H = 1080;
    /** Shipped densities, px a cell, as export.py writes them. */
    static final float HULL_PX = 32f;
    static final float FORGE_PX = 96f;
    static final float STAKE_PX = 128f;
    static final float DEBRIS_PX = 64f;
    static final float TRENCH_PX = 64f;
    static final float ROOF_PX = 128f;
    /**
     * A walker's assembled master spans 1024 guide px at 220 px a guide cell; its torso is drawn {@code 0.85 x render
     * scale} cells wide in play (`DerelictWalkerComposer`), so a guide cell is that over the torso's guide width. The
     * stripper is drawn at the rounds walker's scale (1.2), the hauler and the one-unit rig at the heavy's (2.0).
     */
    static final float STRIPPER_CELLS = 1024f / 220f * (0.85f * 1.2f / 1.0f);
    static final float HAULER_CELLS = 1024f / 220f * (0.85f * 2.0f / 1.4f);
    static final float RIG_CELLS = 1024f / 220f * (0.85f * 2.0f / 1.3f);

    @Override public String id() { return "fallen-hull-look"; }

    @Override public String label() { return "The fallen hull's looks and the Long Survey's art picks, carded"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        Path art = Path.of(System.getProperty(ART_PROPERTY,
                context.projectRoot().resolve("build/fallen-hull-art").toString()))
                .resolve("graphics/battle/fallen-hull");
        Art a = new Art(art, context.modRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            hull(context, a, out);
            ruin(context, a, out);
        }
        return out;
    }

    // ---- the hull on the town

    private static void hull(SnapshotContext context, Art a, List<SnapshotArtifact> out) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(new JSONObject(Files.readString(
                context.projectRoot().resolve(RESCUE_FIXTURE), StandardCharsets.UTF_8)));
        DerelictWatchPicksSuite.play(sim, 20);
        float mw = sim.getGrid().getWidth();
        float mh = sim.getGrid().getHeight();
        // The hull rests a little east of the map's middle, bow east, its trench ploughed in from the west.
        float hx = mw * 0.55f;
        float hy = mh * 0.5f;
        log("rescue map %.0f x %.0f, hull at (%.1f, %.1f)", mw, mh, hx, hy);
        for (String lineage : new String[] {"survey", "carrier", "contingency"}) {
            String letter = switch (lineage) { case "survey" -> "a"; case "carrier" -> "b"; default -> "c"; };
            Frame f = frame(context, sim, hx - 14f, hy, 104f, "hull-" + lineage);
            trench(f, a, hx - 66f, hx - 24f, hy, 18f);
            debrisField(f, a, hx - 60f, hx - 22f, hy, 9f);
            f.piece(a.get("hull-" + lineage + ".png"), hx, hy, HULL_PX, 0f, 0.45f);
            out.add(new SnapshotArtifact("hull-lineage/" + letter + "-" + lineage + ".png", f.image));
        }
        Frame f = frame(context, sim, hx - 22f, hy - 4f, 44f, "hull-close");
        trench(f, a, hx - 66f, hx - 24f, hy, 18f);
        debrisField(f, a, hx - 60f, hx - 22f, hy, 9f);
        f.piece(a.get("hull-survey.png"), hx, hy, HULL_PX, 0f, 0.45f);
        out.add(new SnapshotArtifact("hull-pieces/a-hull-close.png", f.image));
        Frame r = frame(context, sim, hx, hy, 40f, "hull-roof");
        roofBlock(r, a, hx - 9f, hy - 5f, 18, 10);
        out.add(new SnapshotArtifact("hull-pieces/b-hull-roof.png", r.image));
    }

    /** The trench's floor from x0 to x1 about y, {@code depth} cells wide, its lips along both edges, patches on it. */
    private static void trench(Frame f, Art a, float x0, float x1, float y, float depth) {
        BufferedImage field = a.get("trench-field.png");
        BufferedImage lip = a.get("trench-lip.png");
        if (field == null) return;
        for (float x = x0; x < x1; x += 2f) {
            for (float yy = y - depth / 2f; yy < y + depth / 2f; yy += 2f) {
                f.tile(field, x, yy, 2f, 2f, 0f);
            }
        }
        if (lip != null) {
            for (float x = x0; x < x1; x += 1f) {
                f.tile(lip, x, y + depth / 2f, 1f, 0.5f, 0f);
                f.tile(lip, x, y - depth / 2f - 0.5f, 1f, 0.5f, (float) Math.PI);
            }
        }
        f.piece(a.get("trench-furrow.png"), x0 + 8f, y + 3f, TRENCH_PX, 0f, 0f);
        f.piece(a.get("trench-scorch.png"), x1 - 4f, y - 2f, TRENCH_PX, 0f, 0f);
        f.piece(a.get("trench-glassed.png"), (x0 + x1) / 2f, y + 1f, TRENCH_PX, 0f, 0f);
        f.piece(a.get("trench-scorch.png"), x0 + 18f, y - 5f, TRENCH_PX, 1.2f, 0f);
    }

    /** Debris strewn along the trench behind the hull. */
    private static void debrisField(Frame f, Art a, float x0, float x1, float y, float spread) {
        String[] names = {"plate-shard-a", "girder", "scrap-heap", "pod", "plate-shard-b", "bell", "plating-flat",
                "mast", "scrap-heap", "plate-shard-a", "plating-edge", "girder"};
        Random rnd = new Random(7);
        for (int i = 0; i < names.length; i++) {
            float x = x0 + (x1 - x0) * (i + 0.5f) / names.length;
            float yy = y + (rnd.nextFloat() - 0.5f) * spread * 2f;
            f.piece(a.get("debris-" + names[i] + ".png"), x, yy, DEBRIS_PX, rnd.nextFloat() * 6.28f, 0.4f);
        }
    }

    /** A block of HULL roof from roofs.png's hull row: field cells, eaves round it, a holed and a collapsed cell. */
    private static void roofBlock(Frame f, Art a, float x0, float y0, int cw, int ch) {
        BufferedImage sheet = a.roofs();
        if (sheet == null) return;
        int fpx = sheet.getHeight() / RoofStyle.COUNT;
        int row = RoofStyle.HULL.ordinal() * fpx;
        for (int i = 0; i < cw; i++) {
            for (int j = 0; j < ch; j++) {
                int col = (i & 1) + 2 * (j & 1);
                if (i == 5 && j == 6) col = 7;
                if (i == 12 && j == 3) col = 8;
                f.tile(sheet.getSubimage(col * fpx, row, fpx, fpx), x0 + i, y0 + j, 1f, 1f, 0f);
            }
        }
        BufferedImage eave = sheet.getSubimage(4 * fpx, row, fpx, fpx);
        for (int i = 0; i < cw; i++) {
            f.tile(eave, x0 + i, y0 + ch - 0.3f, 1f, 1f, 0f);
            f.tile(eave, x0 + i, y0 - 0.7f, 1f, 1f, (float) Math.PI);
        }
        for (int j = 0; j < ch; j++) {
            f.tile(eave, x0 + cw - 0.3f, y0 + j, 1f, 1f, (float) (-Math.PI / 2));
            f.tile(eave, x0 - 0.7f, y0 + j, 1f, 1f, (float) (Math.PI / 2));
        }
    }

    // ---- the ruin: forge, stakes, salvage walkers

    private static void ruin(SnapshotContext context, Art a, List<SnapshotArtifact> out) throws Exception {
        JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(
                DerelictWatchPicksSuite.FIXTURE), StandardCharsets.UTF_8));
        BattleSimulation sim = DerelictWatchPicksSuite.settled(fixture, DerelictWatchPicksSuite.SETTLE_TICKS);
        DerelictSecurityDirector director = sim.derelictSecurity();
        World world = sim.world();
        long vault = DerelictWatchPicksSuite.first(sim, director, DerelictWalkerFrame.VAULT);
        long line = DerelictWatchPicksSuite.first(sim, director, DerelictWalkerFrame.LINE);
        if (vault == 0L) vault = line;
        if (vault != 0L) {
            float vx = world.renderX(vault);
            float vy = world.renderY(vault);
            log("vault frame at (%.1f, %.1f)", vx, vy);
            String[] designs = {"crucible", "gantry", "ring"};
            for (int i = 0; i < designs.length; i++) {
                Frame f = frame(context, sim, vx + 5f, vy, 30f, "forge-" + designs[i]);
                f.piece(a.get("forge-" + designs[i] + "-lit.png"), vx + 5.5f, vy + 4f, FORGE_PX, 0f, 0.3f);
                f.piece(a.get("forge-" + designs[i] + "-out.png"), vx + 13.5f, vy + 4f, FORGE_PX, 0f, 0.3f);
                out.add(new SnapshotArtifact("autoforge/" + (char) ('a' + i) + "-" + designs[i] + ".png", f.image));
            }
        }
        if (line != 0L) {
            float lx = world.renderX(line);
            float ly = world.renderY(line);
            log("line frame at (%.1f, %.1f)", lx, ly);
            String[] designs = {"spire", "tripod"};
            for (int i = 0; i < designs.length; i++) {
                Frame f = frame(context, sim, lx + 8f, ly, 30f, "stake-" + designs[i]);
                f.piece(a.get("stake-" + designs[i] + "-planted.png"), lx + 5f, ly - 3f, STAKE_PX, 0f, 0.35f);
                f.piece(a.get("stake-" + designs[i] + "-raised.png"), lx + 10f, ly - 3f, STAKE_PX, 0f, 0.35f);
                f.piece(a.get("stake-" + designs[i] + "-store.png"), lx + 15f, ly - 3f, STAKE_PX, 0f, 0.35f);
                out.add(new SnapshotArtifact("stake/" + (char) ('a' + i) + "-" + designs[i] + ".png", f.image));
            }
            Frame two = frame(context, sim, lx + 7f, ly, 30f, "stripper-hauler-two");
            salvage(two, a, lx, ly);
            two.walker(a.get("walker-stripper/assembled.png"), lx + 6f, ly - 3.5f, STRIPPER_CELLS, 0.5f);
            two.walker(a.get("walker-hauler/assembled.png"), lx + 13f, ly - 4f, HAULER_CELLS, -0.4f);
            out.add(new SnapshotArtifact("stripper-hauler/a-two-units.png", two.image));
            Frame one = frame(context, sim, lx + 7f, ly, 30f, "stripper-hauler-one");
            salvage(one, a, lx, ly);
            one.walker(a.get("walker-rig/assembled.png"), lx + 6.5f, ly - 3.5f, RIG_CELLS, 0.5f);
            one.walker(a.get("walker-rig/assembled.png"), lx + 14f, ly - 4.5f, RIG_CELLS, -0.6f);
            out.add(new SnapshotArtifact("stripper-hauler/b-one-unit.png", one.image));
        }
    }

    /** A wreck being cut up: plating and a scrap heap the salvage walkers work at. */
    private static void salvage(Frame f, Art a, float lx, float ly) {
        f.piece(a.get("debris-plating-bay.png"), lx + 9f, ly - 0.5f, DEBRIS_PX, 0.2f, 0.4f);
        f.piece(a.get("debris-scrap-heap.png"), lx + 10f, ly - 4f, DEBRIS_PX, 0f, 0.3f);
        f.piece(a.get("debris-girder.png"), lx + 7.5f, ly + 2.5f, DEBRIS_PX, -0.3f, 0.4f);
    }

    // ---- composition

    private static Frame frame(SnapshotContext context, BattleSimulation sim, float x, float y, float cells,
                               String name) throws Exception {
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, x, y, cells)
                .withoutRoofs();
        BufferedImage shot = renderer.render(sim, name);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(shot, 0, 0, null);
        g.dispose();
        return new Frame(img, renderer.cameraOf(sim));
    }

    /** A look frame and its camera, onto which cut art is laid at cell positions. */
    static final class Frame {
        final BufferedImage image;
        final BattleCamera cam;

        Frame(BufferedImage image, BattleCamera cam) {
            this.image = image;
            this.cam = cam;
        }

        float sx(float cellX) { return cam.cellToScreenX(cellX); }

        float sy(float cellY) { return H - cam.cellToScreenY(cellY); }

        /** A piece authored at {@code pxPerCell}, centred on cell point (x, y), turned, with a contact shadow. */
        void piece(BufferedImage sprite, float x, float y, float pxPerCell, float angle, float shadow) {
            if (sprite == null) return;
            float k = cam.cellPxSize() / pxPerCell;
            draw(sprite, sx(x), sy(y), sprite.getWidth() * k, sprite.getHeight() * k, angle, shadow);
        }

        /** A walker's assembled sprite drawn {@code cells} across, centred on (x, y), facing {@code angle}. */
        void walker(BufferedImage sprite, float x, float y, float cells, float angle) {
            if (sprite == null) return;
            float side = cells * cam.cellPxSize();
            draw(sprite, sx(x), sy(y), side, side * sprite.getHeight() / sprite.getWidth(), angle, 0.45f);
        }

        /** A ground tile whose south-west corner is cell (x, y), {@code w x h} cells, turned about its centre. */
        void tile(BufferedImage sprite, float x, float y, float w, float h, float angle) {
            float pw = w * cam.cellPxSize();
            float ph = h * cam.cellPxSize();
            draw(sprite, sx(x + w / 2f), sy(y + h / 2f), pw + 0.6f, ph + 0.6f, angle, 0f);
        }

        private void draw(BufferedImage sprite, float cx, float cy, float w, float h, float angle, float shadow) {
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (shadow > 0f) {
                float off = cam.cellPxSize() * 0.22f;
                AffineTransform t = placed(cx + off, cy + off, w, h, angle, sprite);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, shadow));
                g.drawImage(silhouette(sprite), t, null);
                g.setComposite(AlphaComposite.SrcOver);
            }
            g.drawImage(sprite, placed(cx, cy, w, h, angle, sprite), null);
            g.dispose();
        }

        private static AffineTransform placed(float cx, float cy, float w, float h, float angle, BufferedImage s) {
            AffineTransform t = new AffineTransform();
            t.translate(cx, cy);
            // Screen y runs down, so a counter-clockwise turn on the map is a negative one here.
            t.rotate(-angle);
            t.scale(w / s.getWidth(), h / s.getHeight());
            t.translate(-s.getWidth() / 2.0, -s.getHeight() / 2.0);
            return t;
        }

        /** The sprite's alpha as a soft black shadow. */
        private static BufferedImage silhouette(BufferedImage s) {
            BufferedImage black = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < s.getHeight(); y++) {
                for (int x = 0; x < s.getWidth(); x++) black.setRGB(x, y, s.getRGB(x, y) & 0xff000000);
            }
            int r = Math.max(2, s.getWidth() / 60);
            int n = 2 * r + 1;
            float[] k = new float[n * n];
            Arrays.fill(k, 1f / (n * n));
            BufferedImage padded = new BufferedImage(s.getWidth() + 2 * n, s.getHeight() + 2 * n,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = padded.createGraphics();
            g.drawImage(black, n, n, null);
            g.dispose();
            BufferedImage blurred = new ConvolveOp(new Kernel(n, n, k), ConvolveOp.EDGE_NO_OP, null).filter(padded,
                    null);
            return blurred.getSubimage(n, n, s.getWidth(), s.getHeight());
        }
    }

    /** The cut art, read once each; null for a piece not cut. */
    static final class Art {
        private final Path root;
        private final Path modRoot;
        private final Map<String, BufferedImage> cache = new HashMap<>();

        Art(Path root, Path modRoot) {
            this.root = root;
            this.modRoot = modRoot;
        }

        BufferedImage get(String name) {
            return cache.computeIfAbsent(name, n -> read(root.resolve(n)));
        }

        BufferedImage roofs() {
            return cache.computeIfAbsent("@roofs", n -> read(modRoot.resolve("graphics/tilesets/roofs.png")));
        }

        private static BufferedImage read(Path p) {
            try {
                return Files.exists(p) ? ImageIO.read(p.toFile()) : null;
            } catch (Exception e) {
                throw new IllegalStateException(p.toString(), e);
            }
        }
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/fallen-hull] " + format + "%n", args);
    }
}
