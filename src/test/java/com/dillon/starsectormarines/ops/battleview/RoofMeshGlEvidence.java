package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCensus;
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
 * What a real driver draws out of the resident roofs after the world has moved
 * under them.
 *
 * <p>{@link RoofMeshTest} pins the bookkeeping — which slot a cell holds, which
 * cells a change re-resolves, what a destroyed roof does to its slot, when a
 * fade is a patch and when it is nothing at all — and that is arithmetic a GPU
 * adds nothing to. Two questions are left over, and both are ones residency is
 * actually risky for.
 *
 * <p><b>Does the resident layer draw what the stream drew?</b> Every roof quad
 * has moved from a command carrying its own destination rectangle to a vertex
 * pair in cell coordinates with the camera on the modelview, and its tint and
 * fade from a per-command colour to a vertex colour array. A quad half a cell
 * out, or a tint on the wrong building, draws a perfectly plausible city.
 *
 * <p><b>Does a patched mesh still hold the world?</b> A roof caves in and a
 * building fades while the player's marines are inside it, and both arrive as a
 * patch of a buffer rather than as a rebuild. A patch that wrote the wrong slot
 * would be wrong for the rest of the battle, invisibly.
 *
 * <p><b>It carries a control against measuring nothing</b> in both halves: the
 * streamed frame is asserted to have submitted a roofful of commands and the
 * resident one none at all, and the patch is asserted to have re-resolved a
 * handful of cells rather than the roof.
 *
 * <p><b>Opt-in, and excluded from {@code test}</b>, for
 * {@code GroundShaderGlEvidence}'s reasons: it needs an accelerated driver, and
 * a suite that requires a GPU fails on the machine that has none. Run it with
 * {@code gradlew.bat shaderEvidence}; where no context can be made it reports
 * that it skipped rather than failing.
 */
@Tag("shader-evidence")
class RoofMeshGlEvidence {

    private static final int CELLS_W = 24;
    private static final int CELLS_H = 16;
    /**
     * Sixteen pixels a cell — whole, so a cell boundary lands on a pixel
     * boundary and the comparison is between two pictures rather than between
     * two roundings of one.
     */
    private static final int CELL_PX = 16;
    private static final int SURFACE_W = CELLS_W * CELL_PX;
    private static final int SURFACE_H = CELLS_H * CELL_PX;

    private static final EnumSet<RenderLayer> ROOFS_ONLY = EnumSet.of(RenderLayer.ROOFS);

    @Test
    void theResidentRoofsDrawWhatTheStreamDrawsAndSurviveBeingPatched() throws Exception {
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
                RoofMesh mesh = renderer.getRoofMesh();

                try (BattleSimulation sim = town()) {
                    CellTopology topology = sim.getTopology();
                    BattleCamera camera = camera();

                    // Frame one is the stream, and its own drain bakes the mesh.
                    DrawCensus streamed = new DrawCensus();
                    byte[] perCell = draw(target[0], renderer, camera, sim, streamed);
                    assertTrue(streamed.sheetQuads() > 40,
                            "the control has to actually submit a roof, or this comparison "
                                    + "is between two blank frames; got " + streamed.sheetQuads());
                    assertTrue(mesh.residentQuads() > 40,
                            "and the bake has to have taken it; " + mesh.residentQuads());

                    // Frame two is the mesh serving: the same picture out of
                    // buffers, with nothing submitted at all.
                    DrawCensus resident = new DrawCensus();
                    byte[] fromMesh = draw(target[0], renderer, camera, sim, resident);
                    System.out.println("[shader-evidence] roof commands: streamed "
                            + streamed.sheetQuads() + " quads, resident "
                            + resident.sheetQuads() + " quads in "
                            + mesh.residentQuads() + " buffered");
                    assertEquals(0, resident.sheetQuads(),
                            "a resident roof submits no quads at all");
                    assertArrayEquals(perCell, fromMesh,
                            "the resident roofs must draw what the per-cell stream draws");

                    // Now move the world under it: one building's roof caves in
                    // over four cells of its own footprint, and another fades as
                    // the player walks in.
                    for (int x = 3; x < 7; x++) topology.setRoofDestroyed(x, 7, true);
                    faded(sim).targetAlpha = 0.35f;
                    faded(sim).currentAlpha = 0.35f;
                    byte[] patched = draw(target[0], renderer, camera, sim, null);
                    assertTrue(mesh.lastResolvedCells() > 0
                                    && mesh.lastResolvedCells() < mesh.residentQuads(),
                            "this has to be a patch to be worth comparing, not a rebuild; "
                                    + "resolved " + mesh.lastResolvedCells() + " of "
                                    + mesh.residentQuads());

                    // And the same world arrived at the other way: dropped and
                    // baked from scratch.
                    mesh.dispose();
                    draw(target[0], renderer, camera, sim, null);
                    // Read straight off the bake frame: the frame after it is
                    // the mesh merely serving, and resolves nothing at all.
                    int bakedCells = mesh.lastResolvedCells();
                    byte[] fresh = draw(target[0], renderer, camera, sim, null);
                    assertEquals(mesh.residentQuads(), bakedCells,
                            "the control has to be a whole bake");

                    assertArrayEquals(fresh, patched,
                            "a patched roof must draw what a fresh bake of the same world "
                                    + "draws");
                }
            } finally {
                if (renderer != null) {
                    renderer.getRoofMesh().dispose();
                    renderer.getGroundAtlas().dispose();
                }
                if (target != null) {
                    glDeleteFramebuffers(target[0]);
                    glDeleteTextures(target[1]);
                }
                Global.setSettings(null);
            }
        }
    }

    /** The building whose roof is faded in the patch — the second one added. */
    private static Building faded(BattleSimulation sim) {
        return sim.getBuildings().get(2);
    }

    /** Draws the ROOFS layer over a known clear and reads the pixels back. */
    private static byte[] draw(int fbo, BattleRenderer renderer, BattleCamera camera,
                               BattleSimulation sim, DrawCensus into) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, SURFACE_W, SURFACE_H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        // Magenta, so anything the layer fails to paint is unmistakable rather
        // than a plausible dark roof.
        glClearColor(1f, 0f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        FrameCensus census = into == null ? null : new FrameCensus();
        renderer.renderWorld(new RenderContext(sim, camera, null, 1f, 0f, false,
                null, null, BattleRenderHostProfile.STANDALONE_BATTLE), ROOFS_ONLY, census);
        if (into != null) into.add(census.drain(RenderLayer.ROOFS));

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE_W * SURFACE_H * 4];
        pixels.get(read);
        return read;
    }

    // ---- the world -----------------------------------------------------------

    /**
     * Three roofed blocks with different tints, one of them already destroyed
     * in part.
     *
     * <p>Hand-built rather than generated: which buildings a generated city
     * happens to place is a property of its seed, and this needs to name the one
     * that fades and the cells that cave in. Different tints because a building's
     * colour is per-vertex data in the mesh and one baked against the wrong
     * building would be invisible against a uniform town.
     */
    private static BattleSimulation town() {
        NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
        CellTopology topology = new CellTopology(CELLS_W, CELLS_H);
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) {
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
                grid.setWalkableFloor(x, y);
            }
        }
        Buildings buildings = new Buildings();
        buildings.add(block(1, 2, 6, 0.9f, 0.55f, 0.45f));
        buildings.add(block(2, 10, 2, 0.5f, 0.75f, 0.9f));
        buildings.add(block(3, 16, 8, 0.6f, 0.9f, 0.55f));
        // One roof already gone before the bake, so the bake itself has to
        // handle a destroyed cell and not only a patch.
        topology.setRoofDestroyed(11, 3, true);

        BattleSimulation sim = new BattleSimulation(grid, topology, 20260903L);
        sim.setMissionCompletionEnabled(false);
        sim.setBuildings(buildings);
        // Both sides present, or the simulation decides the battle is over and
        // the renderer is handed a world that has stopped.
        sim.spawn(new EntitySpec("us", Faction.MARINE, UnitType.MARINE, 0, 0).moveSpeed(0f));
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA,
                CELLS_W - 1, CELLS_H - 1).moveSpeed(0f));
        return sim;
    }

    /** A 5x4 roofed block with its origin at {@code (x0, y0)}. */
    private static Building block(int id, int x0, int y0, float r, float g, float b) {
        int w = 5;
        int h = 4;
        int[] cellsX = new int[w * h];
        int[] cellsY = new int[w * h];
        int at = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                cellsX[at] = x0 + x;
                cellsY[at] = y0 + y;
                at++;
            }
        }
        return new Building(id, BuildingKind.RESIDENTIAL,
                x0, x0 + w - 1, y0, y0 + h - 1, cellsX, cellsY, r, g, b);
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
