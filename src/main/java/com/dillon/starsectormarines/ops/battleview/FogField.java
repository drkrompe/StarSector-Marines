package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.vision.RevealChangeLog;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_ALPHA;
import static org.lwjgl.opengl.GL11.GL_ALPHA8;
import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_MODULATE;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_ENV;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_ENV_MODE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexEnvi;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * The player's fog, resident on the GPU as one map-sized alpha texture.
 *
 * <p><b>Why this and not fewer commands.</b> {@code renderEvidence} measured the
 * whole-map frame on the canonical 560x336 Conquest with the ground and the
 * relief fields already resident: FOG was 5.7 ms of the 12.5 that were ours,
 * and it was collection-bound in the sharpest form the vocabulary has —
 * 168,339 commands, one per cell the player cannot fully see, draining in a
 * single call. Nothing was wrong with the drain. The cost was walking every
 * visible cell every frame and emitting a quad for each, and it scaled with the
 * map exactly as the ground did before it became resident.
 *
 * <p><b>Fog is a scalar per cell, and a scalar per cell is a texture.</b> One
 * texel per cell holds the alpha that cell's shadow draws at, and the whole map
 * is one quad sampling it. Colour comes from {@code glColor4f} and the frame's
 * alpha multiplies the texel under {@code GL_MODULATE}, which is exactly the
 * arithmetic the per-cell path did with {@code fogAlpha * alphaMult} — so this
 * needs no shader, and the picture is the same picture rather than a second
 * one.
 *
 * <p><b>One resolver, two sinks.</b> What a cell's shadow looks like is
 * {@link BattleRenderer#fogAlphaForCell}, and both paths call it. The field
 * stores that answer as the byte a fixed-point alpha channel would hold anyway;
 * the per-cell stream passes the float straight through. They cannot disagree
 * about the picture because there is only one of them.
 *
 * <p><b>The upload is the delta.</b> {@link RevealChangeLog} says where the
 * player's reveal moved since this field last looked, and the patch is a
 * sub-image of that rectangle grown by one cell — grown because a revealed
 * cell's alpha is feathered from its four neighbours, so a cell that goes dark
 * changes the picture of everything it touches. A whole-map rewrite is 188 KB
 * and stays as the fallback for a reader that has fallen off the end of the
 * log.
 *
 * <p><b>What fog gates is untouched.</b> The unit-visibility gate, the building
 * pass and every consumer that asks whether the player can see a cell keep
 * reading {@link FogOfWarService}. Only the picture changes how it is drawn.
 *
 * <p><b>It fails soft.</b> No texture, a failed allocation, any GL error at all
 * — {@link #sync} answers false and the collector emits the ordinary per-cell
 * stream, with the same paint order and the same picture. Turn it off for a
 * control run with {@code -Dbattle.render.fogField=false}.
 */
public final class FogField {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.fogField";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Whether the field is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    // ---- the picture ---------------------------------------------------------

    /** A cell nobody can see. */
    private static final float UNSEEN_ALPHA = 0.85f;

    /**
     * A cell only a cloud is hiding: half the ordinary shadow, so the terrain
     * silhouette and the smoke above it explain the lost sight together.
     */
    private static final float SMOKE_ALPHA = UNSEEN_ALPHA * 0.5f;

    /** Feathering on a revealed cell, per dark neighbour. */
    private static final float FEATHER_STEP = 0.15f;

    private static final byte UNSEEN_LEVEL = quantise(UNSEEN_ALPHA);
    private static final byte SMOKE_LEVEL = quantise(SMOKE_ALPHA);
    private static final byte[] FEATHER_LEVEL = {
            quantise(0f), quantise(FEATHER_STEP), quantise(2 * FEATHER_STEP),
            quantise(3 * FEATHER_STEP), quantise(4 * FEATHER_STEP),
    };

    /**
     * The shadow one cell shows, as the byte an alpha channel holds.
     *
     * <p><b>Quantised on purpose.</b> Fog is drawn into an eight-bit alpha
     * channel whichever path draws it, so the scale has always had 256 steps;
     * naming them is what lets a texel and a vertex colour land on the identical
     * value instead of on two floats that round apart under the driver's own
     * conversion. Without it a level whose product sits on a rounding boundary —
     * two dark neighbours over a mid-toned floor is one — comes out a byte
     * different between the resident field and the per-cell stream, which is a
     * seam the player would eventually see.
     *
     * <p>A cell hidden only by smoke remains unrevealed; the half-strength
     * shadow says so without revealing it. Naturally unseen cells and
     * revealed-edge feathering are unchanged by a cloud.
     */
    static byte levelFor(boolean revealed, int darkNeighbors, boolean clearAirRevealed) {
        if (!revealed) return clearAirRevealed ? SMOKE_LEVEL : UNSEEN_LEVEL;
        int dark = Math.max(0, Math.min(FEATHER_LEVEL.length - 1, darkNeighbors));
        return FEATHER_LEVEL[dark];
    }

    /** The alpha a level draws at — the exact value the texel also carries. */
    static float alphaForLevel(byte level) {
        return (level & 0xFF) / 255f;
    }

    private static byte quantise(float alpha) {
        return (byte) Math.round(alpha * 255f);
    }

    private FogOfWarService bound;
    private int gridW;
    private int gridH;
    private long caughtUpTo;
    private boolean broken;

    /** One alpha byte per cell: the CPU mirror of the texture. */
    private byte[] level;

    private int texture;
    private boolean wholeDirty;

    /** The pending upload rectangle, inclusive; {@code maxX < minX} means none. */
    private int dirtyMinX = Integer.MAX_VALUE;
    private int dirtyMinY = Integer.MAX_VALUE;
    private int dirtyMaxX = -1;
    private int dirtyMaxY = -1;

    private ByteBuffer scratch;
    private final int[] union = new int[4];

    /** What the last catch-up re-derived; an instrument, read only by tests. */
    private final int[] lastDerived = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};

    /**
     * Whether this field already holds {@code fog}'s picture.
     *
     * <p>GL-free, because a collector is ({@code battle-render-nouns.md}, law
     * 2). It answers false for the frame in which a battle's fog is first baked,
     * so that frame collects the ordinary per-cell stream and the bake happens
     * in its drain; from the next frame the field serves. It answers false
     * forever once anything has failed, which is what makes the fallback
     * automatic rather than something a host has to know about.
     */
    public boolean isServing(FogOfWarService fog) {
        return ENABLED && !broken && fog != null && fog.isInitialized() && fog == bound;
    }

    /**
     * Catches the field up to {@code fog} and uploads whatever moved.
     *
     * @return whether the field can draw this frame; false means the caller owns
     *         the fog and must emit it cell by cell
     */
    public boolean sync(FogOfWarService fog) {
        if (!catchUp(fog)) return false;
        try {
            upload();
            return !broken;
        } catch (RuntimeException failure) {
            broken = true;
            return false;
        }
    }

    /**
     * The half of {@link #sync} that touches no GL: decide whether this is a
     * bake or a patch, and re-derive the cells that need it.
     *
     * <p>Separate from the upload so it can be exercised without a context. Every
     * rule worth pinning here — which cells a change re-derives, when the log has
     * been outrun, what a cell's level comes out as — is arithmetic, and a test
     * that needed a GPU to ask about arithmetic would not be run.
     */
    boolean catchUp(FogOfWarService fog) {
        if (!ENABLED || broken || fog == null || !fog.isInitialized()) return false;
        try {
            if (fog != bound) {
                build(fog);
                return true;
            }
            RevealChangeLog log = fog.revealChanges();
            long now = log.changeCount();
            noteDerived(Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1);
            if (now == caughtUpTo) return true;
            if (!log.unionSince(caughtUpTo, union)) {
                build(fog);
                return true;
            }
            caughtUpTo = now;
            patch(fog, union[0], union[1], union[2], union[3]);
            return true;
        } catch (RuntimeException failure) {
            broken = true;
            return false;
        }
    }

    /**
     * Draws the fog.
     *
     * <p>Owns its complete GL lifecycle, as a custom pass must
     * ({@code battle-render-nouns.md}, law 3). One quad over the map rectangle,
     * in the camera's own screen coordinates rather than through a modelview
     * transform — four vertices are not worth a matrix push, and the per-cell
     * stream this replaces put its quads in exactly these coordinates.
     *
     * <p>Sampling is {@code GL_NEAREST}: a texel is a cell and a cell has one
     * shadow, so the field draws the flat squares the command stream drew. A
     * softened edge would be a different picture and is not one this is
     * entitled to invent.
     */
    public void draw(BattleCamera camera, float alphaMult) {
        if (broken || texture == 0 || camera == null) return;
        try (GlStateBracket bracket = GlStateBracket.textured2D()) {
            glColor4f(0f, 0f, 0f, alphaMult);
            glBindTexture(GL_TEXTURE_2D, texture);
            // An alpha texture under MODULATE leaves the primary colour alone and
            // multiplies the alphas, which is fog colour times fog level times the
            // frame's fade. The mode is per texture unit and a foreign draw may
            // have moved it; GL_TEXTURE_BIT in the bracket carries it back.
            glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

            float x0 = camera.cellToScreenX(0f);
            float y0 = camera.cellToScreenY(0f);
            float x1 = camera.cellToScreenX(gridW);
            float y1 = camera.cellToScreenY(gridH);
            glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex2f(x0, y0);
            glTexCoord2f(1f, 0f); glVertex2f(x1, y0);
            glTexCoord2f(1f, 1f); glVertex2f(x1, y1);
            glTexCoord2f(0f, 1f); glVertex2f(x0, y1);
            glEnd();
        } catch (RuntimeException failure) {
            broken = true;
        }
    }

    /** Releases the texture. The context that made it must still be current. */
    public void dispose() {
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
        bound = null;
        caughtUpTo = 0L;
        wholeDirty = true;
        clearDirty();
    }

    /** Visible for tests: the alpha byte this field holds for a cell. */
    byte levelAt(int x, int y) {
        if (level == null || x < 0 || y < 0 || x >= gridW || y >= gridH) return 0;
        return level[y * gridW + x];
    }

    /**
     * Visible for tests: the rectangle the last catch-up re-derived, as
     * {@code {minX, minY, maxX, maxY}}; {@code maxX < minX} when it did nothing.
     *
     * <p>This is the reading the extent exists for. Whether a bake or a patch
     * happened is not observable from the picture — both leave the field holding
     * what the bitmap says — so the thing worth pinning is how much ground the
     * field was sent over to get there.
     */
    int[] lastDerivedRect() {
        return lastDerived.clone();
    }

    // ---- build and patch -----------------------------------------------------

    private void build(FogOfWarService fog) {
        bound = fog;
        gridW = fog.gridWidth();
        gridH = fog.gridHeight();
        level = new byte[gridW * gridH];
        derive(fog, 0, 0, gridW - 1, gridH - 1);
        noteDerived(0, 0, gridW - 1, gridH - 1);
        wholeDirty = true;
        clearDirty();
        caughtUpTo = fog.revealChanges().changeCount();
    }

    /**
     * Re-derives the changed rectangle and the ring of cells around it.
     *
     * <p>The ring is not superstition: a revealed cell's alpha is feathered by
     * how many of its four neighbours are dark, so a cell going dark changes the
     * picture of the four cells it touches without changing their own reveal
     * state.
     */
    private void patch(FogOfWarService fog, int minX, int minY, int maxX, int maxY) {
        if (maxX < minX || maxY < minY) return;
        int x0 = Math.max(0, minX - 1);
        int y0 = Math.max(0, minY - 1);
        int x1 = Math.min(gridW - 1, maxX + 1);
        int y1 = Math.min(gridH - 1, maxY + 1);
        derive(fog, x0, y0, x1, y1);
        noteDerived(x0, y0, x1, y1);
        if (x0 < dirtyMinX) dirtyMinX = x0;
        if (y0 < dirtyMinY) dirtyMinY = y0;
        if (x1 > dirtyMaxX) dirtyMaxX = x1;
        if (y1 > dirtyMaxY) dirtyMaxY = y1;
    }

    private void noteDerived(int minX, int minY, int maxX, int maxY) {
        lastDerived[0] = minX;
        lastDerived[1] = minY;
        lastDerived[2] = maxX;
        lastDerived[3] = maxY;
    }

    private void derive(FogOfWarService fog, int x0, int y0, int x1, int y1) {
        boolean[] revealed = fog.cellRevealedArray();
        for (int y = y0; y <= y1; y++) {
            int rowBase = y * gridW;
            for (int x = x0; x <= x1; x++) {
                int idx = rowBase + x;
                int darkNeighbors = 0;
                if (revealed[idx]) {
                    if (y > 0          && !revealed[idx - gridW]) darkNeighbors++;
                    if (y < gridH - 1  && !revealed[idx + gridW]) darkNeighbors++;
                    if (x > 0          && !revealed[idx - 1])     darkNeighbors++;
                    if (x < gridW - 1  && !revealed[idx + 1])     darkNeighbors++;
                }
                level[idx] = levelFor(revealed[idx], darkNeighbors,
                        fog.wouldBeRevealedWithoutTransientOpacity(x, y));
            }
        }
    }

    // ---- upload --------------------------------------------------------------

    private void upload() {
        if (level == null || gridW <= 0 || gridH <= 0) return;
        if (texture == 0) {
            texture = glGenTextures();
            if (texture == 0) {
                broken = true;
                return;
            }
            wholeDirty = true;
        }
        if (!wholeDirty && dirtyMaxX < dirtyMinX) return;

        glBindTexture(GL_TEXTURE_2D, texture);
        // One byte per texel over an arbitrary map width: the default four-byte
        // unpack alignment would shear every row that is not a multiple of four.
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            if (wholeDirty) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, gridW, gridH, 0,
                        GL_ALPHA, GL_UNSIGNED_BYTE, packed(0, 0, gridW, gridH));
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                wholeDirty = false;
            } else {
                int w = dirtyMaxX - dirtyMinX + 1;
                int h = dirtyMaxY - dirtyMinY + 1;
                glTexSubImage2D(GL_TEXTURE_2D, 0, dirtyMinX, dirtyMinY, w, h,
                        GL_ALPHA, GL_UNSIGNED_BYTE, packed(dirtyMinX, dirtyMinY, w, h));
            }
        } finally {
            glPopClientAttrib();
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        clearDirty();
    }

    /** The rectangle's bytes, row by row, in one buffer the driver can read. */
    private ByteBuffer packed(int x, int y, int w, int h) {
        int needed = w * h;
        if (scratch == null || scratch.capacity() < needed) {
            scratch = BufferUtils.createByteBuffer(Math.max(needed, 4096));
        }
        scratch.clear();
        for (int row = 0; row < h; row++) {
            scratch.put(level, (y + row) * gridW + x, w);
        }
        scratch.flip();
        return scratch;
    }

    private void clearDirty() {
        dirtyMinX = Integer.MAX_VALUE;
        dirtyMinY = Integer.MAX_VALUE;
        dirtyMaxX = -1;
        dirtyMaxY = -1;
    }
}
