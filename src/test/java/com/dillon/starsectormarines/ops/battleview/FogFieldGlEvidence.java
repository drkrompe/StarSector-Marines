package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
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
 * What a real driver draws out of the resident fog field, against the per-cell
 * stream it replaces.
 *
 * <p>{@link FogFieldTest} pins the bookkeeping — which cells a change
 * re-derives, when the log has been outrun — and that is arithmetic a GPU adds
 * nothing to. The question left over is the one residency is actually risky
 * for: whether one textured quad paints the same pixels as a hundred and
 * ninety-two little ones, and whether it still does after the field has been
 * <em>patched</em> rather than baked. A field that has drifted is wrong however
 * fast it is, and the drift would be invisible to any assertion about the CPU
 * mirror, because those are about the array rather than about the texture the
 * driver samples.
 *
 * <p><b>Both pictures come out of the shipping renderer.</b> The only thing that
 * differs between the two draws is the host profile: a standalone battle elects
 * residency and an embedded scene declines it, and that is the gate
 * {@code collectFogOverlay} reads. The comparison is therefore the real collect
 * and the real drain either way, rather than this file's idea of what fog looks
 * like.
 *
 * <p><b>Opt-in, and excluded from {@code test}</b>, for
 * {@code GroundShaderGlEvidence}'s reasons: it needs an accelerated driver, and
 * a suite that requires a GPU fails on the machine that has none. Run it with
 * {@code gradlew.bat shaderEvidence}; where no context can be made it reports
 * that it skipped rather than failing.
 */
@Tag("shader-evidence")
class FogFieldGlEvidence {

    private static final int CELLS_W = 16;
    private static final int CELLS_H = 12;
    /** Eight pixels a cell, so a texel landing one cell out lands visibly out. */
    private static final int CELL_PX = 8;
    private static final int SURFACE_W = CELLS_W * CELL_PX;
    private static final int SURFACE_H = CELLS_H * CELL_PX;

    /** A wall column, so half the map is dark and the join carries the feathering. */
    private static final int WALL_X = 9;

    /** Three sim ticks per vision tick; six comfortably outruns the cadence. */
    private static final int SETTLE_TICKS = 6;

    private static final EnumSet<RenderLayer> FOG_ONLY = EnumSet.of(RenderLayer.FOG);

    @Test
    void theFieldDrawsWhatThePerCellStreamDraws() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE_W, SURFACE_H)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());
            Global.setSettings(screenSettings());
            try {
                int[] target = target();
                BattleRenderer renderer = new BattleRenderer(new BattleSprites());
                BattleCamera camera = camera();

                // Nobody can see: every cell carries the full shadow.
                try (BattleSimulation dark = world(false)) {
                    assertBlind(dark);
                    assertSamePicture("a map nobody can see", target[0], renderer, camera, dark);
                }

                // Open ground and one pair of eyes in the middle of it: every cell
                // is revealed, so the field is asked for the other extreme.
                try (BattleSimulation lit = openWorld()) {
                    assertSighted(lit);
                    assertSamePicture("a map entirely in sight", target[0], renderer, camera, lit);
                }

                // A wall down the middle: seen one side, unseen the other, and the
                // feathered join between them.
                try (BattleSimulation mixed = world(true)) {
                    FogOfWarService fog = mixed.getFogOfWar();
                    assertTrue(fog.isCellRevealed(2, CELLS_H / 2), "the near side has to be lit");
                    assertFalse(fog.isCellRevealed(CELLS_W - 1, CELLS_H / 2),
                            "and the far side dark, or the mixed case is not mixed");
                    assertSamePicture("a map seen on one side of a wall",
                            target[0], renderer, camera, mixed);

                    // And now the case the delta path exists for: vision moves, the
                    // field patches rather than rebuilds, and the picture still
                    // agrees with the stream.
                    fog.addEphemeralSource(CELLS_W - 3, CELLS_H - 3, 4, 0f);
                    settle(mixed);
                    assertTrue(fog.isCellRevealed(CELLS_W - 3, CELLS_H - 3),
                            "the source has to open ground the wall was hiding");

                    FogField resident = renderer.getFogField();
                    byte[] field = draw(target[0], renderer, camera, mixed,
                            BattleRenderHostProfile.STANDALONE_BATTLE);
                    int[] derived = resident.lastDerivedRect();
                    long area = (long) (derived[2] - derived[0] + 1) * (derived[3] - derived[1] + 1);
                    assertTrue(area < (long) CELLS_W * CELLS_H,
                            "this has to be a patch to be worth comparing, not a rebuild; "
                                    + "derived " + area + " of " + (CELLS_W * CELLS_H));

                    byte[] perCell = draw(target[0], renderer, camera, mixed,
                            BattleRenderHostProfile.EMBEDDED_SCENE);
                    assertArrayEquals(perCell, field,
                            "a patched field must draw exactly what the per-cell stream draws");
                }

                glDeleteFramebuffers(target[0]);
                glDeleteTextures(target[1]);
                renderer.getFogField().dispose();
            } finally {
                Global.setSettings(null);
            }
        }
    }

    // ---- the comparison ------------------------------------------------------

    private static void assertSamePicture(String what, int fbo, BattleRenderer renderer,
                                          BattleCamera camera, BattleSimulation sim) {
        byte[] perCell = draw(fbo, renderer, camera, sim, BattleRenderHostProfile.EMBEDDED_SCENE);
        // The first standalone frame bakes the field and still draws the stream,
        // by design; the second is the one the field is serving.
        draw(fbo, renderer, camera, sim, BattleRenderHostProfile.STANDALONE_BATTLE);
        assertTrue(renderer.getFogField().isServing(sim.getFogOfWar()),
                what + ": the field has to be serving before it can be compared");
        byte[] field = draw(fbo, renderer, camera, sim, BattleRenderHostProfile.STANDALONE_BATTLE);
        assertArrayEquals(perCell, field,
                what + ": the field must draw exactly what the per-cell stream draws");
    }

    /** Draws the FOG layer over a known clear under one host profile, and reads it back. */
    private static byte[] draw(int fbo, BattleRenderer renderer, BattleCamera camera,
                               BattleSimulation sim, BattleRenderHostProfile profile) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, SURFACE_W, SURFACE_H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        // A mid grey, so a shadow that is one level out of step is a byte out of
        // step: over black every alpha paints the same nothing.
        glClearColor(0.75f, 0.6f, 0.45f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        renderer.renderWorld(new RenderContext(sim, camera, null, 1f, 0f, false,
                null, null, profile), FOG_ONLY, null);

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE_W * SURFACE_H * 4];
        pixels.get(read);
        return read;
    }

    // ---- the worlds ----------------------------------------------------------

    /**
     * Open ground with no player-side eyes on it at all, or the same ground with
     * a wall down the middle and one marine on the near side.
     */
    private static BattleSimulation world(boolean withEyes) {
        NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) {
                if (withEyes && x == WALL_X) continue; // a fresh grid is solid
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(CELLS_W, CELLS_H), 20260902L);
        sim.setMissionCompletionEnabled(false);
        if (withEyes) {
            sim.spawn(new EntitySpec("eyes", Faction.MARINE, UnitType.MARINE, 2, CELLS_H / 2)
                    .moveSpeed(0f));
        }
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA,
                CELLS_W - 2, CELLS_H - 2).moveSpeed(0f));
        settle(sim);
        return sim;
    }

    /** Open ground with one pair of eyes in the middle: nothing is dark. */
    private static BattleSimulation openWorld() {
        NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(CELLS_W, CELLS_H), 20260902L);
        sim.setMissionCompletionEnabled(false);
        sim.spawn(new EntitySpec("eyes", Faction.MARINE, UnitType.MARINE,
                CELLS_W / 2, CELLS_H / 2).moveSpeed(0f));
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA, 1, 1).moveSpeed(0f));
        settle(sim);
        return sim;
    }

    private static void assertBlind(BattleSimulation sim) {
        FogOfWarService fog = sim.getFogOfWar();
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) {
                assertFalse(fog.isCellRevealed(x, y),
                        "a battle with no contributor must be entirely dark at " + x + "," + y);
            }
        }
    }

    private static void assertSighted(BattleSimulation sim) {
        FogOfWarService fog = sim.getFogOfWar();
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) {
                assertTrue(fog.isCellRevealed(x, y),
                        "open ground inside one marine's sight must be lit at " + x + "," + y);
            }
        }
    }

    private static void settle(BattleSimulation sim) {
        for (int i = 0; i < SETTLE_TICKS; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    // ---- GL scaffolding ------------------------------------------------------

    /**
     * A camera whose cells land on exact pixel boundaries.
     *
     * <p>Not fastidiousness: the per-cell stream places each quad at
     * {@code cellToScreenX(cx)} and {@code + cellPx}, and the field places one
     * quad across the whole map, so the two agree on a seam only where the
     * arithmetic is exact. A framing that put a cell boundary halfway through a
     * pixel would be measuring float rounding rather than the field.
     */
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
