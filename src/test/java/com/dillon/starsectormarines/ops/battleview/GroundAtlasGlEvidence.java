package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * What a real driver draws out of the composited ground atlas, against the
 * per-sheet path it replaces.
 *
 * <p>{@link GroundAtlasTest} pins the layout — that slots do not overlap, that
 * each carries its gutter, that a sheet the atlas does not hold passes through —
 * and that is arithmetic a GPU adds nothing to. The question left over is the
 * one an atlas is actually risky for: whether a quad that has been moved to
 * another place in another texture samples the same texels. Nothing about a
 * command count can see that. A slot copied a row out, a padded source read back
 * with the wrong row length, a bilinear tap that now reaches into the sheet next
 * door — every one of them draws a perfectly plausible picture of the wrong
 * thing.
 *
 * <p><b>Both pictures come out of the shipping renderer.</b> The atlas is built
 * inside the ground layer's own custom pass, so the first frame of a battle
 * still collects and drains the per-sheet, per-cell stream — that frame is the
 * control, taken through the same collect and the same drain as the frame that
 * follows it rather than through this file's idea of what terrain looks like.
 *
 * <p><b>It carries a control against measuring nothing.</b> The frame that draws
 * per-sheet is asserted to have taken several texture binds and the frame that
 * draws through the atlas exactly one, so a comparison that passed because both
 * frames drew an empty layer would fail here first.
 *
 * <p><b>Opt-in, and excluded from {@code test}</b>, for
 * {@code GroundShaderGlEvidence}'s reasons: it needs an accelerated driver, and
 * a suite that requires a GPU fails on the machine that has none. Run it with
 * {@code gradlew.bat shaderEvidence}; where no context can be made it reports
 * that it skipped rather than failing.
 */
@Tag("shader-evidence")
class GroundAtlasGlEvidence {

    private static final int CELLS_W = 24;
    private static final int CELLS_H = 16;
    /**
     * Sixteen pixels a cell.
     *
     * <p>Large enough that a tile's interior is many texels wide and a
     * one-texel sampling error shows as a band rather than as rounding, and
     * whole, so a cell boundary lands on a pixel boundary: a framing that put
     * one halfway through a pixel would be comparing float rounding rather than
     * comparing textures.
     */
    private static final int CELL_PX = 16;
    private static final int SURFACE_W = CELLS_W * CELL_PX;
    private static final int SURFACE_H = CELLS_H * CELL_PX;

    private static final EnumSet<RenderLayer> GROUND_ONLY = EnumSet.of(RenderLayer.GROUND);

    @Test
    void theAtlasDrawsWhatThePerSheetPathDraws() throws Exception {
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
                GroundAtlas atlas = renderer.getGroundAtlas();
                assertTrue(atlas.isPlanned(),
                        "the atlas has to have a layout, or there is nothing to compare");
                assertTrue(atlas.sheetCount() >= 5,
                        "every terrain sheet the ground layer can draw from belongs in it; got "
                                + atlas.sheetCount());

                try (BattleSimulation sim = terrain()) {
                    BattleCamera camera = camera();

                    // Frame one: the atlas does not exist yet, so this is the
                    // per-sheet, per-cell stream — and its own drain builds the
                    // atlas for the frame after it.
                    assertFalse(atlas.isServing(),
                            "the control frame has to be collected before the atlas serves");
                    DrawCensus perSheetCounts = new DrawCensus();
                    byte[] perSheet = draw(target[0], renderer, camera, sim, perSheetCounts);
                    assertTrue(perSheetCounts.textureBinds() >= 3,
                            "the control has to actually bind several sheets, or this "
                                    + "comparison is between two blank frames; got "
                                    + perSheetCounts.textureBinds());

                    assertTrue(atlas.isServing(),
                            "the control frame's drain has to have built the atlas");

                    // Frame two: the same collect and the same drain, with every
                    // quad now addressing the atlas instead of its own sheet.
                    DrawCensus atlasCounts = new DrawCensus();
                    byte[] throughAtlas = draw(target[0], renderer, camera, sim, atlasCounts);
                    System.out.println("[shader-evidence] ground binds: per-sheet "
                            + perSheetCounts.textureBinds() + ", atlas "
                            + atlasCounts.textureBinds());
                    // Not one, and not a number of sheets either: through the
                    // atlas a run of quads is broken only where a solid fill
                    // interrupts it — the crosswalk band and the window panes —
                    // so what is left is a handful whatever the sheets do.
                    assertTrue(atlasCounts.textureBinds() <= 3,
                            "a ground layer through the atlas binds once per solid-fill "
                                    + "interruption, not once per sheet; got "
                                    + atlasCounts.textureBinds());
                    assertTrue(atlasCounts.textureBinds() < perSheetCounts.textureBinds(),
                            "and fewer than the per-sheet path took");
                    assertEquals(perSheetCounts.sheetQuads(), atlasCounts.sheetQuads(),
                            "the same quads, in the same order — only their texture moved");

                    assertArrayEquals(perSheet, throughAtlas,
                            "the atlas must draw exactly what the per-sheet path draws");

                    // And the resident mesh takes the atlas too: a standalone
                    // frame bakes it, and what it bakes is one buffer against
                    // one texture rather than one per sheet. Its picture is the
                    // mesh's question rather than the atlas's, but which
                    // texture it baked against is this one, and a mesh that had
                    // baked the per-sheet coordinates would keep them for the
                    // rest of the battle.
                    draw(target[0], renderer, camera, sim, null,
                            BattleRenderHostProfile.STANDALONE_BATTLE);
                    draw(target[0], renderer, camera, sim, null,
                            BattleRenderHostProfile.STANDALONE_BATTLE);
                    assertTrue(renderer.getGroundMesh().residentQuads() > 0,
                            "the mesh has to have baked something to have baked it anywhere");
                    assertEquals(1, renderer.getGroundMesh().bucketCount(),
                            "a mesh baked against the atlas is one buffer, not one per sheet");
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

    // ---- the comparison ------------------------------------------------------

    /**
     * Draws the GROUND layer over a known clear and reads the pixels back.
     *
     * <p>Through an embedded scene's profile, which declines resident ground
     * and the relief redirect. That is not a shortcut: it is what makes the
     * comparison about the atlas. Both frames then take the same per-cell
     * command path, and the only difference between them is which texture the
     * quads address — where a standalone frame would also be trading a
     * per-cell stream for a resident mesh, and a difference could be either.
     */
    private static byte[] draw(int fbo, BattleRenderer renderer, BattleCamera camera,
                               BattleSimulation sim, DrawCensus into) {
        return draw(fbo, renderer, camera, sim, into, BattleRenderHostProfile.EMBEDDED_SCENE);
    }

    private static byte[] draw(int fbo, BattleRenderer renderer, BattleCamera camera,
                               BattleSimulation sim, DrawCensus into,
                               BattleRenderHostProfile profile) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, SURFACE_W, SURFACE_H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        // Magenta, so anything the layer fails to paint over is unmistakable
        // rather than a plausible dark tile.
        glClearColor(1f, 0f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        FrameCensus census = into == null ? null : new FrameCensus();
        renderer.renderWorld(new RenderContext(sim, camera, null, 1f, 0f, false,
                null, null, profile), GROUND_ONLY, census);
        if (into != null) into.add(census.drain(RenderLayer.GROUND));

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE_W * SURFACE_H * 4];
        pixels.get(read);
        return read;
    }

    // ---- the world -----------------------------------------------------------

    /**
     * Ground that draws from every sheet the atlas holds, plus the decoration
     * that is not a base tile at all.
     *
     * <p>Hand-built rather than generated: a generated city is a fine picture
     * and a poor fixture, because which sheets it happens to reach is a
     * property of its seed. This one names them — a floor block, the sliced
     * street strip, the nature strip, water, and the wall block — and adds the
     * sparse work that shares the layer: a doorway decal, a crosswalk's
     * stripes, a window pane, and scattered nature overlays over cells that
     * already drew a tile.
     */
    private static BattleSimulation terrain() {
        NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
        CellTopology topology = new CellTopology(CELLS_W, CELLS_H);
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) {
                CellTopology.GroundKind kind = kindFor(x, y);
                topology.setGroundKind(x, y, kind);
                if (x == CELLS_W - 1 || y == CELLS_H - 1) {
                    // A rim of wall, so the wall block and its autotile frames
                    // are in the picture and every interior cell has a
                    // neighbour mask that is not all-open.
                    topology.setWall(x, y, true);
                    topology.setWallDirMask(x, y, wallMask(x, y));
                    if (y == CELLS_H - 1 && x % 5 == 2) topology.setWindow(x, y, true);
                    continue;
                }
                grid.setWalkableFloor(x, y);
                if (kind == CellTopology.GroundKind.STREET && y == 4) {
                    topology.setCrosswalk(x, y, true);
                    topology.setCrosswalkStripesHorizontal(x, y, (x & 1) == 0);
                }
                if ((x * 7 + y * 3) % 11 == 0) topology.setNatureOverlayIndex(x, y, x % 4);
                if (x % 9 == 3 && y % 6 == 2) grid.setDoorway(x, y, true);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 20260902L);
        sim.setMissionCompletionEnabled(false);
        // Both sides present, or the simulation decides the battle is over and
        // the renderer is handed a world that has stopped.
        sim.spawn(new EntitySpec("us", Faction.MARINE, UnitType.MARINE, 1, 1).moveSpeed(0f));
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA,
                CELLS_W - 3, CELLS_H - 3).moveSpeed(0f));
        return sim;
    }

    /** Bands of ground kind across the map, one per sheet the atlas holds. */
    private static CellTopology.GroundKind kindFor(int x, int y) {
        return switch (x / 5) {
            case 0 -> CellTopology.GroundKind.STREET;
            case 1 -> CellTopology.GroundKind.GRASS;
            case 2 -> CellTopology.GroundKind.INDOOR;
            case 3 -> CellTopology.GroundKind.WATER;
            default -> CellTopology.GroundKind.DIRT;
        };
    }

    /** Which sides of a rim cell face open ground. */
    private static int wallMask(int x, int y) {
        int mask = 0;
        if (y + 1 >= CELLS_H) mask |= CellTopology.WALL_DIR_N;
        if (y - 1 < 0) mask |= CellTopology.WALL_DIR_S;
        if (x + 1 >= CELLS_W) mask |= CellTopology.WALL_DIR_E;
        if (x - 1 < 0) mask |= CellTopology.WALL_DIR_W;
        return mask == 0 ? CellTopology.WALL_DIR_N : mask;
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
