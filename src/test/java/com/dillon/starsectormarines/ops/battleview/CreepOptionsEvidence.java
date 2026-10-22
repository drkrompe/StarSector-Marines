package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

/**
 * One look option of the creep, for the owner to choose between: the infested colony through the shipping
 * {@link BattleRenderer} in GL at play zoom (32 px a cell), seventy-five seconds into the outbreak, framed on a wall the creep has only just reached, with
 * open ground and the creep's edge beside it. The option is whatever {@code -Dbattle.render.creep.look.*} values the
 * run was given; {@code -Dbattle.render.creep.optionsVariant=<name>} names the folder it writes under
 * {@code build/reports/render/creep-options/}: {@code patch.png} at the first shot, and {@code wall-0..2.png} as the
 * same wall ages (the creep keeps growing between them). Runs under {@code renderEvidence}; a sheet is assembled
 * from several runs by {@code art-source/hive-creep/option_sheets.py}.
 */
@Tag("render-evidence")
class CreepOptionsEvidence {

    private static final int W = 1920, H = 1080;
    private static final float CELL_PX = 32f;
    /** Seconds of creep before the first shot: the front still moving and its walls newly reached. */
    private static final int START_SECONDS = 75;
    /** Seconds past the first shot each wall-stage shot is taken at; a wall fills in about a hundred. */
    private static final int[] WALL_SECONDS = {0, 45, 110};
    /** Coverage of the framed wall at the first shot: veins part-way across it, no crust yet. */
    private static final int EARLY_COVERAGE = 40;
    private static final EnumSet<RenderLayer> LAYERS = EnumSet.complementOf(EnumSet.of(RenderLayer.FOG));

    @Test
    void rendersOneLookOption() throws Exception {
        String variant = System.getProperty("battle.render.creep.optionsVariant");
        if (variant == null || !CreepLayer.enabled()) {
            System.out.println("[creep-options] SKIPPED: no -Dbattle.render.creep.optionsVariant given");
            return;
        }
        try (HeadlessGl gl = HeadlessGl.createOrNull(W, H)) {
            if (gl == null) {
                System.out.println("[creep-options] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            RenderBudgetEvidence.configureSurface();
            Global.setSettings(RenderBudgetEvidence.screenSettings());
            Path out = Path.of(System.getProperty("render.evidence.outputDir", "build/reports/render"))
                    .resolve("creep-options").resolve(variant).toAbsolutePath().normalize();
            Files.createDirectories(out);
            Path modRoot = Path.of("mod").toAbsolutePath().normalize();
            new BattleReviewFrameRenderer(modRoot, W, H);
            try (GlSpriteTokens tokens = new GlSpriteTokens();
                 BattleSimulation sim = RenderBudgetEvidence.MapSpec.colony(START_SECONDS)) {
                HeadlessBattleSprites sprites = new HeadlessBattleSprites(modRoot, tokens);
                sprites.ensureUnitSheets();
                BattleRenderer renderer = new BattleRenderer(sprites);
                renderer.onAttach();
                renderer.buildTileBatches();
                BattleLayout layout = new BattleLayout(RenderBudgetEvidence.position(),
                        sim.getGrid().getWidth(), sim.getGrid().getHeight());
                CreepField field = sim.creepField();
                int[] wall = freshWall(field, sim);
                int tick = Math.round(START_SECONDS / BattleSimulation.TICK_DT);
                int elapsed = 0;
                for (int i = 0; i < WALL_SECONDS.length; i++) {
                    int until = Math.round(WALL_SECONDS[i] / BattleSimulation.TICK_DT);
                    for (; elapsed < until; elapsed++) field.tick(tick + elapsed);
                    System.out.println("[creep-options] stage " + i + ": wall coverage " + field.coverageAt(wall[0], wall[1])
                            + ", field revision " + field.revision());
                    BufferedImage frame = shoot(renderer, sim, layout, wall[0] + 0.5f, wall[1] + 0.5f);
                    ImageIO.write(frame, "png", out.resolve("wall-" + i + ".png").toFile());
                    if (i == 0) ImageIO.write(frame, "png", out.resolve("patch.png").toFile());
                }
                System.out.println("[creep-options] " + variant + ": wall " + wall[0] + "," + wall[1] + " -> " + out);
            } finally {
                Global.setSettings(null);
            }
        }
    }

    private static BufferedImage shoot(BattleRenderer renderer, BattleSimulation sim, BattleLayout layout,
                                       float x, float y) {
        BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH, layout.cellSize);
        float zoom = CELL_PX / layout.cellSize;
        camera.zoomAt((float) (Math.log(zoom) / Math.log(1.2)), camera.vpX() + camera.vpW() * 0.5f,
                camera.vpY() + camera.vpH() * 0.5f);
        camera.centerOn(x, y);
        RenderContext rc = new RenderContext(sim, camera, layout, 1f, 0f, false, null, null,
                BattleRenderHostProfile.STANDALONE_BATTLE);
        FrameCensus census = new FrameCensus();
        for (int warm = 0; warm < 3; warm++) {
            glClear(GL_COLOR_BUFFER_BIT);
            renderer.renderWorld(rc, LAYERS, warm == 2 ? census : null);
            glFinish();
        }
        assertTrue(census.drain(RenderLayer.CREEP).customs() >= 1, "the creep drained as a custom pass");
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                int i = ((H - 1 - py) * W + px) * 4;
                image.setRGB(px, py, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | (pixels.get(i + 2) & 0xFF));
            }
        }
        return image;
    }

    /**
     * The wall whose fill is nearest {@link #EARLY_COVERAGE} that still has uncreeped walkable ground within four cells of it: a
     * wall at the front, so the frame has the wall, the edge and open ground, and the wall has its whole fill ahead.
     * Ties break on position, so every option frames the same wall.
     */
    static int[] freshWall(CreepField field, BattleSimulation sim) {
        int gw = sim.getGrid().getWidth(), gh = sim.getGrid().getHeight();
        int[] best = {gw / 2, gh / 2};
        int bestCov = Integer.MAX_VALUE;
        for (int y = 20; y < gh - 20; y++) {
            for (int x = 30; x < gw - 30; x++) {
                if (sim.getGrid().isWalkable(x, y) || !sim.getTopology().isWall(x, y)) continue;
                int cov = field.coverageAt(x, y);
                int off = Math.abs(cov - EARLY_COVERAGE);
                if (cov == 0 || off >= bestCov) continue;
                if (!openGroundNear(field, sim, x, y, 4)) continue;
                bestCov = off;
                best = new int[]{x, y};
            }
        }
        return best;
    }

    private static boolean openGroundNear(CreepField field, BattleSimulation sim, int cx, int cy, int r) {
        int open = 0;
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if (sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y) && !field.isCreeped(x, y)) open++;
            }
        }
        return open >= 6;
    }
}
