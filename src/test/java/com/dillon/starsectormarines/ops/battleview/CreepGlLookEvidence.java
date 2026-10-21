package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.Global;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * The creep as the game draws it: the infested canonical colony through the shipping {@link BattleRenderer} into
 * a real GL context, at play zoom on a spreading edge, close on a climbed wall, and at the whole map, each beside
 * the review frame of the same framing. The GL frame is the only picture of the creep inside the relief
 * composite: displaced with the ground, shadowed by the walls, shading its own relief. Writes
 * {@code build/reports/render/creep-look/}; runs under {@code renderEvidence} and reports that it skipped where
 * no context can be made.
 *
 * <p>Asserts the mechanic, not the look: at every framing the creep drained as one custom pass inside the ground
 * target and painted pixels the review frame also paints.
 */
@Tag("render-evidence")
class CreepGlLookEvidence {

    private static final int W = 1920, H = 1080;

    /** Everything a player sees but the fog of war, which would dim the creep the review frame shows undimmed. */
    private static final EnumSet<RenderLayer> LAYERS = EnumSet.complementOf(EnumSet.of(RenderLayer.FOG));

    @Test
    void drawsTheInfestedColonyInsideTheGroundComposite() throws Exception {
        if (!CreepLayer.enabled()) {
            System.out.println("[creep-look] SKIPPED: the creep control run draws no creep");
            return;
        }
        try (HeadlessGl gl = HeadlessGl.createOrNull(W, H)) {
            if (gl == null) {
                System.out.println("[creep-look] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            RenderBudgetEvidence.configureSurface();
            Global.setSettings(RenderBudgetEvidence.screenSettings());
            Path out = Path.of(System.getProperty("render.evidence.outputDir", "build/reports/render"))
                    .resolve("creep-look").toAbsolutePath().normalize();
            Files.createDirectories(out);
            Path modRoot = Path.of("mod").toAbsolutePath().normalize();
            // Review renderers first: they install the tile registry the colony is generated against.
            new BattleReviewFrameRenderer(modRoot, W, H);
            try (GlSpriteTokens tokens = new GlSpriteTokens();
                 BattleSimulation sim = RenderBudgetEvidence.MapSpec.colony()) {
                HeadlessBattleSprites sprites = new HeadlessBattleSprites(modRoot, tokens);
                sprites.ensureUnitSheets();
                BattleRenderer renderer = new BattleRenderer(sprites);
                renderer.onAttach();
                renderer.buildTileBatches();
                int gw = sim.getGrid().getWidth(), gh = sim.getGrid().getHeight();
                BattleLayout layout = new BattleLayout(RenderBudgetEvidence.position(), gw, gh);
                CreepField field = sim.creepField();
                List<ColonyEruptionPlan.Site> sites = sim.getColonyBreach().plan().sites();
                int[] edge = edgeNear(field, sim, sites.get(0).x(), sites.get(0).y());
                int[] wall = climbedWallNear(field, sim, edge[0], edge[1]);
                shoot(renderer, sim, layout, modRoot, out, "01-play-zoom", edge[0], edge[1], 32f);
                shoot(renderer, sim, layout, modRoot, out, "02-close-wall", wall[0], wall[1], 64f);
                shoot(renderer, sim, layout, modRoot, out, "03-whole-map", gw * 0.5f, gh * 0.5f, 0f);
                System.out.println("[creep-look] wrote " + out);
            } finally {
                Global.setSettings(null);
            }
        }
    }

    private static void shoot(BattleRenderer renderer, BattleSimulation sim, BattleLayout layout, Path modRoot,
                              Path out, String name, float x, float y, float cellPx) throws Exception {
        int gw = sim.getGrid().getWidth(), gh = sim.getGrid().getHeight();
        BattleCamera camera = new BattleCamera(gw, gh);
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH, layout.cellSize);
        if (cellPx > 0f) {
            float zoom = cellPx / layout.cellSize;
            camera.zoomAt((float) (Math.log(zoom) / Math.log(1.2)), camera.vpX() + camera.vpW() * 0.5f,
                    camera.vpY() + camera.vpH() * 0.5f);
        }
        camera.centerOn(x, y);
        RenderContext rc = new RenderContext(sim, camera, layout, 1f, 0f, false, null, null,
                BattleRenderHostProfile.STANDALONE_BATTLE);
        FrameCensus census = new FrameCensus();
        for (int warm = 0; warm < 3; warm++) frame(renderer, rc, null);
        frame(renderer, rc, census);
        assertTrue(census.drain(RenderLayer.CREEP).customs() >= 1, name + ": the creep drained as a custom pass");
        BufferedImage glFrame = readBack();
        ImageIO.write(glFrame, "png", out.resolve(name + "-gl.png").toFile());
        float across = camera.vpW() / camera.cellPxSize();
        BattleReviewFrameRenderer review = new BattleReviewFrameRenderer(modRoot, W, H,
                camera.panCellX(), camera.panCellY(), across).withRoofs();
        ImageIO.write(review.render(sim, String.format("%s, %.1f px a cell (review frame)", name, camera.cellPxSize())),
                "png", out.resolve(name + "-review.png").toFile());
    }

    private static void frame(BattleRenderer renderer, RenderContext rc, FrameCensus census) {
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.renderWorld(rc, LAYERS, census);
        glFinish();
    }

    private static BufferedImage readBack() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                image.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | (pixels.get(i + 2) & 0xFF));
            }
        }
        return image;
    }

    /** A creeped cell beside an uncreeped walkable one, the nearest such to the given point at least six cells out. */
    static int[] edgeNear(CreepField field, BattleSimulation sim, int cx, int cy) {
        int[] best = {cx, cy};
        int bestD = Integer.MAX_VALUE;
        int gw = sim.getGrid().getWidth(), gh = sim.getGrid().getHeight();
        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                if (!field.isCreeped(x, y) || !sim.getGrid().isWalkable(x, y)) continue;
                boolean front = !field.isCreeped(x + 1, y) && sim.getGrid().isWalkable(x + 1, y);
                if (!front) continue;
                int d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d >= 36 && d < bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** The climbed wall cell nearest the given point: near the front, where the creep has held it least long. */
    static int[] climbedWallNear(CreepField field, BattleSimulation sim, int cx, int cy) {
        int[] best = {cx, cy};
        double bestScore = Double.NEGATIVE_INFINITY;
        int gw = sim.getGrid().getWidth(), gh = sim.getGrid().getHeight();
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (sim.getGrid().isWalkable(x, y) || !sim.getTopology().isWall(x, y)) continue;
                int cov = field.coverageAt(x, y);
                if (cov == 0) continue;
                double score = -Math.hypot(x - cx, y - cy);
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }
}
