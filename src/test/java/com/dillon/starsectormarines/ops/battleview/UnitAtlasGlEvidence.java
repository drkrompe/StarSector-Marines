package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
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
 * What a real driver draws out of the composited unit atlas, against the
 * whole-sprite path it replaces.
 *
 * <p>{@link SpriteAtlasLayoutTest} pins the layout — that slots do not overlap, that
 * each carries its gutter, that an image the atlas does not hold passes through
 * — and that is arithmetic a GPU adds nothing to. The question left over is the
 * one an atlas is actually risky for, and it is sharper here than it was for
 * the ground: a unit's layers are drawn <em>rotated</em>, so the redirect
 * exchanges the host's own whole-texture sprite call for a rotated quad out of
 * a shared texture, and every way that can go wrong draws a perfectly plausible
 * picture — a mirrored V, a rotation the other way, a slot copied a row out, a
 * bilinear tap that now reaches into the art next door.
 *
 * <p><b>Both pictures come out of the shipping renderer.</b> The atlas is built
 * inside the unit layer's own custom pass, so the first frame of a battle still
 * collects and drains whole sprites — that frame is the control, taken through
 * the same collect and the same drain as the frame that follows it rather than
 * through this file's idea of what a marine looks like.
 *
 * <p><b>It carries a control against measuring nothing.</b> The frame that draws
 * whole sprites is asserted to have taken many texture binds and the atlas frame
 * very few, so a comparison that passed because both frames drew an empty layer
 * would fail here first.
 *
 * <p><b>Opt-in, and excluded from {@code test}</b>, for
 * {@code GroundShaderGlEvidence}'s reasons: it needs an accelerated driver, and
 * a suite that requires a GPU fails on the machine that has none. Run it with
 * {@code gradlew.bat shaderEvidence}; where no context can be made it reports
 * that it skipped rather than failing.
 */
@Tag("shader-evidence")
class UnitAtlasGlEvidence {

    private static final int CELLS_W = 8;
    private static final int CELLS_H = 5;
    /**
     * Cells large enough that a body is drawn near the size of its own art.
     *
     * <p>A marine's shoulders are 0.6 of a cell and his body PNG is 150 texels
     * across, so at 180 pixels a cell he is drawn at about 108 — near enough to
     * 1:1 that the comparison is between two pictures of a marine rather than
     * between two different resamplings of one. Drawn small he is minified four
     * to one, and there a sub-texel difference in where a tap lands does not
     * shade a pixel slightly differently, it picks a different texel.
     */
    private static final int CELL_PX = 180;
    private static final int SURFACE_W = CELLS_W * CELL_PX;
    private static final int SURFACE_H = CELLS_H * CELL_PX;

    private static final EnumSet<RenderLayer> UNITS_ONLY = EnumSet.of(RenderLayer.UNITS);

    /** Bodies on the field. Enough that the whole-sprite path is plainly many binds. */
    private static final int MARINES = 8;

    @Test
    void theAtlasDrawsWhatTheSpritePathDraws() throws Exception {
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
                sprites.ensureUnitSheets();
                renderer = new BattleRenderer(sprites);
                renderer.onAttach();
                renderer.buildTileBatches();
                UnitAtlas atlas = renderer.getUnitAtlas();
                assertTrue(atlas.isPlanned(),
                        "the atlas has to have a layout, or there is nothing to compare: "
                                + atlas.planDiagnostic());
                assertTrue(atlas.sheetCount() >= 10,
                        "every layer a composed body draws from belongs in it; got "
                                + atlas.sheetCount());

                try (BattleSimulation sim = field()) {
                    BattleCamera camera = camera();

                    // Frame one: the atlas does not exist yet, so this is the
                    // whole-sprite path — and its own drain builds the atlas for
                    // the frame after it.
                    assertFalse(atlas.isServing(),
                            "the control frame has to be collected before the atlas serves");
                    DrawCensus spriteCounts = new DrawCensus();
                    byte[] perSprite = draw(target[0], renderer, camera, sim, spriteCounts);
                    assertTrue(spriteCounts.textureBinds() >= MARINES,
                            "the control has to actually bind a texture per layer, or this "
                                    + "comparison is between two blank frames; got "
                                    + spriteCounts.textureBinds());

                    assertTrue(atlas.isServing(),
                            "the control frame's drain has to have built the atlas");

                    // Frame two: the same collect and the same drain, with every
                    // body now addressing the atlas instead of its own image.
                    DrawCensus atlasCounts = new DrawCensus();
                    byte[] throughAtlas = draw(target[0], renderer, camera, sim, atlasCounts);
                    System.out.println("[shader-evidence] unit binds: whole-sprite "
                            + spriteCounts.textureBinds() + ", atlas "
                            + atlasCounts.textureBinds()
                            + "; draws " + spriteCounts.drawCalls()
                            + " -> " + atlasCounts.drawCalls());
                    assertTrue(atlasCounts.textureBinds() <= 3,
                            "a unit layer through the atlas binds once per interruption of "
                                    + "the run, not once per layer; got "
                                    + atlasCounts.textureBinds());
                    assertTrue(atlasCounts.drawCalls() < spriteCounts.drawCalls(),
                            "and takes fewer draws than the whole-sprite path did");
                    // Bodies, not commands: the control frame also carries the
                    // one custom pass that builds the atlas, and the frame after
                    // it deliberately does not.
                    assertEquals(spriteCounts.sprites() + spriteCounts.sheetQuads(),
                            atlasCounts.sprites() + atlasCounts.sheetQuads(),
                            "the same drawn pieces, in the same order — only their texture moved");
                    assertEquals(0, atlasCounts.customs(),
                            "a settled atlas costs the layer no custom pass at all");

                    Difference same = compare(perSprite, throughAtlas);
                    Difference displaced = compare(perSprite, shiftedOneColumn(perSprite));
                    System.out.println("[shader-evidence] unit pixels: "
                            + same.painted() + " painted, " + same.differingPixels()
                            + " differ against the sprite path, "
                            + displaced.differingPixels()
                            + " against the same frame moved one column");
                    assertTrue(same.painted() > 8_000,
                            "the comparison has to be between two frames with bodies in "
                                    + "them, not two magenta rectangles; painted "
                                    + same.painted() + " pixels");
                    // The control for the measurement itself: the same picture
                    // displaced by a single column, which is the smallest thing
                    // that could go wrong here and the largest thing this metric
                    // has to be able to see. Everything an atlas actually gets
                    // wrong — a mirrored V, a rotation the other way, a slot read
                    // a row out — moves at least that much.
                    assertTrue(displaced.differingPixels() > 20 * same.differingPixels(),
                            "a metric that cannot tell a displaced picture from a matching "
                                    + "one is measuring nothing: matching "
                                    + same.differingPixels() + ", displaced "
                                    + displaced.differingPixels());
                    assertTrue(same.differingPixels() < same.painted() / 50,
                            "and the atlas has to draw the same picture, not a similar one: "
                                    + same.differingPixels() + " of " + same.painted()
                                    + " painted pixels differ at all");
                }
            } finally {
                if (renderer != null) renderer.getUnitAtlas().dispose();
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
     * How far apart two frames are, and how much of them was drawn at all.
     *
     * <p><b>Why this is not byte equality.</b> The ground atlas can be compared
     * exactly, because a ground quad is axis-aligned and its cell lands on whole
     * texels. A body does not: it is drawn rotated, so every one of its pixels
     * is a bilinear tap between four texels, and the weights come from a texture
     * coordinate the driver interpolates in fixed point. The same tap addressed
     * at {@code u} of a 64-texel image and at {@code (x + u * 64) / 2048} of the
     * atlas is the same tap in exact arithmetic and rounds apart in the
     * hardware's, by a bit, on the pixels where the art has a steep edge.
     *
     * <p>So the bar is the last bit and no further, which is a bar every failure
     * this evidence exists for clears loudly: a mirrored V, a rotation the wrong
     * way, a slot read a row out or a bilinear tap reaching into the art next
     * door all move whole pixels, not their last bit.
     */
    private record Difference(int painted, int differingPixels) { }

    /** Compares two RGBA read-backs, counting what was drawn over the magenta clear. */
    private static Difference compare(byte[] control, byte[] subject) {
        int painted = 0;
        int differing = 0;
        for (int pixel = 0; pixel < control.length; pixel += 4) {
            int r = control[pixel] & 0xFF;
            int g = control[pixel + 1] & 0xFF;
            int b = control[pixel + 2] & 0xFF;
            if (!(r == 255 && g == 0 && b == 255)) painted++;
            for (int channel = 0; channel < 4; channel++) {
                if (control[pixel + channel] != subject[pixel + channel]) {
                    differing++;
                    break;
                }
            }
        }
        return new Difference(painted, differing);
    }

    /** The same frame moved one pixel to the right — the metric's own control. */
    private static byte[] shiftedOneColumn(byte[] frame) {
        byte[] moved = new byte[frame.length];
        for (int row = 0; row < SURFACE_H; row++) {
            int at = row * SURFACE_W * 4;
            System.arraycopy(frame, at, moved, at + 4, (SURFACE_W - 1) * 4);
            // The column that scrolls in takes the clear colour, so the edge is
            // not silently identical.
            moved[at] = (byte) 255;
            moved[at + 1] = 0;
            moved[at + 2] = (byte) 255;
            moved[at + 3] = (byte) 255;
        }
        return moved;
    }

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
        // than a plausible dark body.
        glClearColor(1f, 0f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        FrameCensus census = into == null ? null : new FrameCensus();
        renderer.renderWorld(new RenderContext(sim, camera, null, 1f, 0f, false,
                null, null, BattleRenderHostProfile.STANDALONE_BATTLE), UNITS_ONLY, census);
        if (into != null) into.add(census.drain(RenderLayer.UNITS));

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE_W * SURFACE_H * 4];
        pixels.get(read);
        return read;
    }

    // ---- the world -----------------------------------------------------------

    /**
     * A field of composed bodies, each facing a different way.
     *
     * <p>The facings are written straight into the animation component rather
     * than played for: a rotation the wrong way round is the failure this is
     * looking for, and eight bodies all facing south could not see it. The
     * poses are varied too, so the weapon, the muzzle flash and the walking
     * feet — separate images with separate pivots — are all in the picture.
     */
    private static BattleSimulation field() {
        NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
        CellTopology topology = new CellTopology(CELLS_W, CELLS_H);
        for (int y = 0; y < CELLS_H; y++) {
            for (int x = 0; x < CELLS_W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 20260903L);
        sim.setMissionCompletionEnabled(false);
        for (int i = 0; i < MARINES; i++) {
            sim.spawn(new EntitySpec("marine-" + i, Faction.MARINE, UnitType.MARINE,
                    1 + (i % 4) * 2, 1 + (i / 4) * 2).moveSpeed(0f));
        }
        // Both sides on the map, or the simulation decides the battle is over.
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA,
                CELLS_W - 2, CELLS_H - 2).moveSpeed(0f));
        // One visibility sweep, so the marines are VIS_VISIBLE without playing
        // a battle whose facings would then be whatever the pathing decided.
        sim.getFogOfWar().tick(3, sim.getRoster());
        pose(sim);
        return sim;
    }

    /** Writes a distinct facing, head-look and action pose onto every layered body. */
    private static void pose(BattleSimulation sim) {
        BattleComponents c = sim.getBattleComponents();
        int seat = 0;
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.liveSprites)) {
            if (!t.has(c.LAYERED_ANIMATION)) continue;
            float[] facing = t.floats(c.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FACING_DEGREES).array();
            float[] headLook = t.floats(c.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_HEAD_LOOK_DEGREES).array();
            float[] locomotion = t.floats(c.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_LOCOMOTION_PHASE).array();
            float[] weaponPhase = t.floats(c.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_WEAPON_PHASE).array();
            int[] pose = t.ints(c.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_WEAPON_POSE).array();
            int[] flags = t.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_FLAGS).array();
            for (int r = 0, n = t.rowCount(); r < n; r++, seat++) {
                facing[r] = seat * 47f;
                headLook[r] = (seat % 3 - 1) * 18f;
                locomotion[r] = (seat % 5) / 5f;
                weaponPhase[r] = (seat % 4) / 4f;
                pose[r] = seat % 2 == 0
                        ? LayeredAppearance.POSE_FIRING : LayeredAppearance.POSE_IDLE;
                flags[r] = seat % 2 == 0
                        ? LayeredAppearance.FLAG_MUZZLE_FLASH : LayeredAppearance.FLAG_MOVING;
            }
        }
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
