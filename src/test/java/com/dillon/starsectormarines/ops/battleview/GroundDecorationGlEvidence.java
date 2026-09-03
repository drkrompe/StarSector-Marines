package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

/**
 * What a real driver draws out of the resident decoration sub-layers after the
 * world has moved under them.
 *
 * <p>{@link GroundMeshTest} pins the bookkeeping — which cells a change
 * re-resolves, which sub-layer a slot belongs to, what a released slot does —
 * and that is arithmetic a GPU adds nothing to. The question left over is the
 * one residency is actually risky for: whether a mesh that has been
 * <em>patched</em> still holds the world. Scatter and door decals are the first
 * things in this mesh that come and go during a battle — an overlay cleared, a
 * doorway buried in rubble — and a sub-layer that drifted would be wrong however
 * fast it is, invisibly, because every assertion about the CPU mirror is about
 * the arrays rather than about what the driver sampled.
 *
 * <p><b>The comparison is a patch against a fresh bake of the same world.</b>
 * Not against the command stream: a streamed frame also swaps a resident base
 * terrain for a per-cell one, and a difference could then be either. Both
 * pictures here come from the resident path through the shipping renderer, and
 * the only thing that differs is whether the mesh arrived at this world by being
 * patched or by being built.
 *
 * <p><b>It carries a control against measuring nothing.</b> The patch is
 * asserted to have re-resolved a handful of cells rather than the grid, so a
 * comparison that passed because the "patch" quietly rebuilt everything would
 * fail here first.
 *
 * <p><b>Opt-in, and excluded from {@code test}</b>, for
 * {@code GroundShaderGlEvidence}'s reasons: it needs an accelerated driver, and
 * a suite that requires a GPU fails on the machine that has none. Run it with
 * {@code gradlew.bat shaderEvidence}; where no context can be made it reports
 * that it skipped rather than failing.
 */
@Tag("shader-evidence")
class GroundDecorationGlEvidence {

    private static final int CELLS_W = 24;
    private static final int CELLS_H = 16;
    private static final int CELL_PX = 16;
    private static final int SURFACE_W = CELLS_W * CELL_PX;
    private static final int SURFACE_H = CELLS_H * CELL_PX;

    private static final EnumSet<RenderLayer> GROUND_ONLY = EnumSet.of(RenderLayer.GROUND);

    @Test
    void aPatchedDecorationDrawsWhatAFreshBakeOfTheSameWorldDraws() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE_W, SURFACE_H)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());
            Global.setSettings(screenSettings());
            int[] target = null;
            BattleRenderer renderer = null;
            try (GlSpriteTokens tokens = new GlSpriteTokens()) {
                target = target();
                HeadlessBattleSprites sprites = new HeadlessBattleSprites(
                        Path.of("mod").toAbsolutePath().normalize(), tokens);
                renderer = new BattleRenderer(sprites);
                renderer.onAttach();
                renderer.buildTileBatches();
                GroundMesh mesh = renderer.getGroundMesh();

                try (BattleSimulation sim = terrain()) {
                    CellTopology topology = sim.getTopology();
                    BattleCamera camera = camera();

                    // Frame one builds the atlas, frame two bakes the mesh
                    // against it, frame three is the one the mesh is serving.
                    for (int warmup = 0; warmup < 3; warmup++) draw(target[0], renderer, camera, sim);
                    assertEquals(3, mesh.sublayerCount(),
                            "the base terrain, the scatter and the door decals");
                    int baked = mesh.residentQuads();
                    assertTrue(baked > CELLS_W * CELLS_H,
                            "there has to be decoration on top of the terrain to compare; "
                                    + baked + " quads over " + (CELLS_W * CELLS_H) + " cells");

                    // Now move the world under it. (3,3) carries both a scatter
                    // tile and a doorway decal, and loses both — the overlay
                    // cleared outright, the decal because the cell is rubble
                    // now — while (12,9) gains scatter where there was none.
                    // Both directions in one patch, on both sub-layers.
                    topology.setNatureOverlayIndex(3, 3, -1);
                    topology.setNatureOverlayIndex(12, 9, 2);
                    topology.setGroundKind(3, 3, CellTopology.GroundKind.RUBBLE);
                    byte[] patched = draw(target[0], renderer, camera, sim);
                    assertTrue(mesh.lastResolvedCells() > 0
                                    && mesh.lastResolvedCells() < CELLS_W * CELLS_H,
                            "this has to be a patch to be worth comparing, not a rebuild; "
                                    + "resolved " + mesh.lastResolvedCells() + " of "
                                    + (CELLS_W * CELLS_H));

                    // And the same world arrived at the other way. The first
                    // frame after the drop draws the stream and bakes; the
                    // second is the fresh mesh serving.
                    mesh.dispose();
                    draw(target[0], renderer, camera, sim);
                    draw(target[0], renderer, camera, sim);
                    assertEquals(CELLS_W * CELLS_H, mesh.lastResolvedCells(),
                            "the control has to be a whole bake");
                    byte[] fresh = draw(target[0], renderer, camera, sim);

                    assertArrayEquals(fresh, patched,
                            "a patched decoration must draw what a fresh bake of the same "
                                    + "world draws");
                }
            } finally {
                if (renderer != null) {
                    renderer.getGroundAtlas().dispose();
                    renderer.getGroundMesh().dispose();
                }
                if (target != null) {
                    glDeleteFramebuffers(target[0]);
                    glDeleteTextures(target[1]);
                }
                Global.setSettings(null);
            }
        }
    }

    /** Draws the GROUND layer over a known clear and reads the pixels back. */
    private static byte[] draw(int fbo, BattleRenderer renderer, BattleCamera camera,
                               BattleSimulation sim) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, SURFACE_W, SURFACE_H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glClearColor(1f, 0f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        renderer.renderWorld(new RenderContext(sim, camera, null, 1f, 0f, false,
                null, null, BattleRenderHostProfile.STANDALONE_BATTLE), GROUND_ONLY, null);

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE_W * SURFACE_H * 4];
        pixels.get(read);
        return read;
    }

    // ---- the world -----------------------------------------------------------

    /** Open ground with scatter over much of it and a scattering of doorways. */
    private static BattleSimulation terrain() {
        NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
        CellTopology topology = new CellTopology(CELLS_W, CELLS_H);
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) {
                topology.setGroundKind(x, y, (x + y) % 3 == 0
                        ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.DIRT);
                grid.setWalkableFloor(x, y);
                if ((x * 5 + y * 3) % 7 == 0) topology.setNatureOverlayIndex(x, y, (x + y) % 4);
                if (x % 6 == 3 && y % 5 == 3) grid.setDoorway(x, y, true);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 20260903L);
        sim.setMissionCompletionEnabled(false);
        sim.spawn(new EntitySpec("us", Faction.MARINE, UnitType.MARINE, 1, 1).moveSpeed(0f));
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA,
                CELLS_W - 3, CELLS_H - 3).moveSpeed(0f));
        return sim;
    }

    // ---- GL scaffolding ------------------------------------------------------

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(CELLS_W, CELLS_H);
        camera.setViewport(0f, 0f, SURFACE_W, SURFACE_H, CELL_PX);
        camera.centerOn(CELLS_W * 0.5f, CELLS_H * 0.5f);
        return camera;
    }

    /** {@code {fbo, texture}} at the surface size. */
    private static int[] target() {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SURFACE_W, SURFACE_H, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);

        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return new int[]{fbo, texture};
    }

    /** Just enough {@link SettingsAPI} that renderer construction has a game to ask. */
    private static SettingsAPI screenSettings() {
        return (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getScreenWidth" -> (float) SURFACE_W;
                    case "getScreenHeight" -> (float) SURFACE_H;
                    case "getScreenScaleMult" -> 1f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "HeadlessSettings";
                    default -> HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                });
    }
}
