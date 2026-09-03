package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.GlErrors;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glGetTexImage;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH;

/**
 * Every sheet the {@code GROUND} layer can draw from, composited into one GL
 * texture so a whole layer is one texture bind.
 *
 * <p><b>Why.</b> {@code renderEvidence} on the canonical 560x336 Conquest, with
 * the ground mesh, the relief fields and the fog all resident: the whole-map
 * {@code GROUND} layer is still 22,335 commands leaving as <em>504 draws across
 * 501 texture binds</em>. Nothing in that stream is expensive to build — 1.7 ms
 * of collection against 3.9 of submission — and nothing in it can coalesce,
 * because the sheet a piece of decoration draws from changes from one piece to
 * the next and the batcher must flush every time it does. Merging is what the
 * vocabulary points at, and the way to merge quads from six sheets is to make
 * them one sheet.
 *
 * <p><b>Painter order is untouched.</b> This changes which texture a quad
 * addresses and where in it, and nothing else: the same quads are submitted in
 * the same order with the same destination rectangles. What changes is that the
 * drain no longer has to break a run when the source sheet changes, so five
 * hundred flushes become a handful.
 *
 * <p><b>Planned on the CPU, built on the GPU.</b> The layout is a shelf pack of
 * the sheets' own content sizes and is pure arithmetic, so it is settled before
 * any GL runs — which is what lets {@link BattleRenderer} build the atlas's
 * {@link com.dillon.starsectormarines.render2d.QuadBatch} at the same lifecycle
 * seam as every other sheet's. The pixels are copied at drain time inside the
 * ground layer's own custom pass: each sheet is read back from its own texture
 * and written into its slot, so the atlas holds the sheets' exact texels rather
 * than a resample of them.
 *
 * <p><b>Slots carry a gutter</b> of {@link #GUTTER_PX} transparent texels.
 * Bilinear filtering reaches one texel past the rectangle it samples, and inside
 * a sheet that neighbour is the same art it has always been; only at a sheet's
 * outer boundary does the atlas change what is next door, and the gutter is
 * what keeps that from being another sheet.
 *
 * <p><b>It fails soft.</b> No texture object, a layout that will not fit the
 * driver's maximum, a read-back that errors — {@link #isServing()} answers false
 * and every ground quad addresses its own sheet exactly as before, with the same
 * picture. Turn it off for a control run with
 * {@code -Dbattle.render.groundAtlas=false}.
 */
public final class GroundAtlas {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.groundAtlas";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * Transparent texels between one sheet's content and the next.
     *
     * <p>Bilinear reaches one texel; eight is that with room for a future
     * filter that reaches further, and it costs a few thousand texels on an
     * atlas whose content is a couple of megapixels.
     */
    static final int GUTTER_PX = 8;

    /**
     * The widest atlas this will plan, before the driver's own maximum is
     * consulted.
     *
     * <p>Stated rather than measured because the layout is planned with no GL
     * context to ask. Every ground sheet this mod ships is under two thousand
     * texels wide, and a cap the driver then refuses is a fail-soft rather than
     * a crash.
     */
    static final int MAX_SIDE_PX = 2048;

    /** Whether the atlas is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** One source sheet and where its content sits in the atlas. */
    private record Slot(SpriteAPI sheet, int contentW, int contentH, int x, int y) { }

    private final List<Slot> slots = new ArrayList<>();
    private final Map<SpriteAPI, Integer> originBySheet = new IdentityHashMap<>();
    private final AtlasSprite sprite = new AtlasSprite();

    private boolean planned;
    private boolean broken;
    private boolean built;
    private int width;
    private int height;
    private int texture;

    /**
     * Settles the layout from the sheets currently loaded. GL-free, idempotent,
     * and the only thing that has to happen before a batch can be built for the
     * atlas.
     *
     * @return whether a layout exists at all; false leaves every caller on the
     *         per-sheet path
     */
    public boolean plan(BattleSprites sprites) {
        if (planned || broken || !ENABLED || sprites == null) return planned;
        planned = true;
        List<Slot> candidates = new ArrayList<>();
        add(candidates, sprites.tileSheet(), sprites.tileSheetPxW(), sprites.tileSheetPxH());
        add(candidates, sprites.roadSheet(), sprites.roadSheetPxW(), sprites.roadSheetPxH());
        add(candidates, sprites.floorsSheet(), sprites.floorsSheetPxW(), sprites.floorsSheetPxH());
        add(candidates, sprites.waterSheet(), sprites.waterSheetPxW(), sprites.waterSheetPxH());
        add(candidates, sprites.urbanTile3Sheet(),
                sprites.urbanTile3SheetPxW(), sprites.urbanTile3SheetPxH());
        add(candidates, sprites.natureSheet(), sprites.natureSheetPxW(), sprites.natureSheetPxH());
        if (candidates.size() < 2) {
            // One sheet is already one bind; an atlas of it would be a copy for
            // nothing, and none at all is a host with no terrain art.
            broken = true;
            return false;
        }
        if (!layOut(candidates)) {
            broken = true;
            return false;
        }
        return true;
    }

    private static void add(List<Slot> into, SpriteAPI sheet, int contentW, int contentH) {
        if (sheet == null || contentW <= 0 || contentH <= 0) return;
        for (Slot existing : into) if (existing.sheet() == sheet) return;
        into.add(new Slot(sheet, contentW, contentH, 0, 0));
    }

    private boolean layOut(List<Slot> candidates) {
        int[] contentW = new int[candidates.size()];
        int[] contentH = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            contentW[i] = candidates.get(i).contentW();
            contentH[i] = candidates.get(i).contentH();
        }
        int[] packed = shelfPack(contentW, contentH, MAX_SIDE_PX);
        if (packed == null) return false;
        for (int i = 0; i < candidates.size(); i++) {
            Slot planned = candidates.get(i);
            int x = packed[i * 2];
            int y = packed[i * 2 + 1];
            slots.add(new Slot(planned.sheet(), planned.contentW(), planned.contentH(), x, y));
            originBySheet.put(planned.sheet(), (x << 16) | y);
        }
        width = MAX_SIDE_PX;
        height = packed[packed.length - 1];
        return true;
    }

    /**
     * Shelves the rectangles tallest first, which wastes the least on a set of
     * long thin strips and short wide plates.
     *
     * <p>Deliberately not a general rectangle packer: six sheets whose sizes are
     * known and stable do not need one, and a packer whose output depended on
     * iteration order would move every UV in the atlas the day one sheet is
     * re-exported a pixel wider. Every rectangle is placed inside a
     * {@link #GUTTER_PX} margin on all four sides — including against the atlas
     * edge, since a texel at {@code u = 0} has a bilinear tap outside the
     * texture and {@code GL_CLAMP_TO_EDGE} would answer it with the slot's own
     * first column.
     *
     * @return each rectangle's origin as {@code x, y} in input order, then the
     *         atlas height; null when it will not fit
     */
    static int[] shelfPack(int[] contentW, int[] contentH, int side) {
        int count = contentW.length;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            int byHeight = Integer.compare(contentH[b], contentH[a]);
            return byHeight != 0 ? byHeight : Integer.compare(contentW[b], contentW[a]);
        });
        int[] out = new int[count * 2 + 1];
        int penX = GUTTER_PX;
        int penY = GUTTER_PX;
        int shelfH = 0;
        for (int slot : order) {
            if (contentW[slot] + 2 * GUTTER_PX > side) return null;
            if (penX + contentW[slot] + GUTTER_PX > side) {
                penX = GUTTER_PX;
                penY += shelfH + GUTTER_PX;
                shelfH = 0;
            }
            out[slot * 2] = penX;
            out[slot * 2 + 1] = penY;
            penX += contentW[slot] + GUTTER_PX;
            shelfH = Math.max(shelfH, contentH[slot]);
        }
        int height = penY + shelfH + GUTTER_PX;
        if (height > side) return null;
        out[count * 2] = height;
        return out;
    }

    /**
     * Whether ground quads may address the atlas.
     *
     * <p>GL-free, because a collector is ({@code battle-render-nouns.md}, law
     * 2). It answers false until the frame after the one whose drain built the
     * texture, and false forever once anything has failed.
     */
    public boolean isServing() {
        return ENABLED && built && !broken;
    }

    /**
     * Whether a layout exists.
     *
     * <p>Distinct from {@link #isServing()} because the two gate different
     * things: a plan is what a batch is built from, and only the host that
     * builds that batch may call {@link #plan}. A collector asks this before
     * asking for the texture to be built, so an atlas nothing can draw is never
     * put in front of quads that would then draw nothing at all.
     */
    public boolean isPlanned() {
        return planned && !broken && !slots.isEmpty();
    }

    /** The sheet every ground quad addresses while the atlas is serving. */
    public SpriteAPI sheet() {
        return sprite;
    }

    /** Atlas width in texels; settled by {@link #plan}, zero before it. */
    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Where {@code sheet}'s content sits, as {@code (x << 16) | y}, or
     * {@code -1} for a sheet the atlas does not hold.
     *
     * <p>Packed into one int so a caller can cache the six origins it needs per
     * collect and add them with a shift rather than probing a map per quad.
     */
    public int origin(SpriteAPI sheet) {
        Integer packed = originBySheet.get(sheet);
        return packed == null ? -1 : packed;
    }

    /** Sheets the atlas holds — for evidence that wants to name them. */
    public int sheetCount() {
        return slots.size();
    }

    /**
     * Builds the texture if it is not built yet. Owns its complete GL
     * lifecycle, as a custom pass must ({@code battle-render-nouns.md}, law 3).
     *
     * @return whether the atlas can be addressed from the next frame
     */
    public boolean sync() {
        if (!ENABLED || broken || built) return isServing();
        if (!planned || slots.isEmpty()) return false;
        try {
            build();
        } catch (RuntimeException failure) {
            broken = true;
        }
        return isServing();
    }

    private void build() {
        int driverMax = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (driverMax > 0 && (width > driverMax || height > driverMax)) {
            broken = true;
            return;
        }
        texture = glGenTextures();
        if (texture == 0) {
            broken = true;
            return;
        }
        ByteBuffer scratch = null;
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glBindTexture(GL_TEXTURE_2D, texture);
            GlErrors.clear();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, transparent());
            if (GlErrors.check("glTexImage2D (ground atlas)")) {
                broken = true;
                return;
            }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            for (Slot slot : slots) {
                scratch = copy(slot, scratch);
                if (broken) return;
            }
            built = true;
        } finally {
            glBindTexture(GL_TEXTURE_2D, 0);
            glPopClientAttrib();
        }
    }

    /**
     * Copies one sheet's content into its slot.
     *
     * <p>Through the driver rather than through a blit into a render target:
     * the sheet is already a texture and reading it back is the one way to be
     * sure the atlas holds <em>its texels</em> and not a resample of them, which
     * is exactly what the pixel-equality evidence asks of this.
     *
     * <p>The read-back is of the whole GL texture, padding included — that is
     * all {@code glGetTexImage} will give — so the sub-image is unpacked with a
     * row length of the padded width, and reads the content rows off the bottom
     * of it. A sprite's content sits at the texture's origin with
     * {@code getTextureWidth}/{@code getTextureHeight} of it used, which is the
     * same convention every UV in this renderer already assumes.
     */
    private ByteBuffer copy(Slot slot, ByteBuffer scratch) {
        SpriteAPI source = slot.sheet();
        float usedU = source.getTextureWidth();
        float usedV = source.getTextureHeight();
        if (usedU <= 0f || usedV <= 0f) {
            broken = true;
            return scratch;
        }
        int paddedW = Math.max(slot.contentW(), Math.round(slot.contentW() / usedU));
        int paddedH = Math.max(slot.contentH(), Math.round(slot.contentH() / usedV));
        int needed = paddedW * paddedH * 4;
        ByteBuffer buffer = scratch;
        if (buffer == null || buffer.capacity() < needed) {
            buffer = BufferUtils.createByteBuffer(needed);
        }
        buffer.clear();
        buffer.limit(needed);

        source.bindTexture();
        GlErrors.clear();
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        if (GlErrors.check("glGetTexImage (ground atlas source)")) {
            broken = true;
            return buffer;
        }

        glBindTexture(GL_TEXTURE_2D, texture);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, paddedW);
        GlErrors.clear();
        // A slot's y is stated the way a sheet's source rectangle is — down
        // from the top — because that is the coordinate every caller adds its
        // own srcY to. GL counts rows up from the bottom, so this is the one
        // place the two meet. Getting it wrong is not subtle in the arithmetic
        // and is entirely invisible in the code: every tile still draws a real
        // tile, just somebody else's.
        glTexSubImage2D(GL_TEXTURE_2D, 0, slot.x(), height - slot.y() - slot.contentH(),
                slot.contentW(), slot.contentH(), GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        boolean failed = GlErrors.check("glTexSubImage2D (ground atlas slot)");
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        if (failed) broken = true;
        return buffer;
    }

    /** A cleared atlas, so a slot nobody fills is transparent rather than driver noise. */
    private ByteBuffer transparent() {
        ByteBuffer zeroed = BufferUtils.createByteBuffer(width * height * 4);
        zeroed.limit(zeroed.capacity());
        return zeroed;
    }

    /**
     * Releases the texture and forgets the layout. The context that made it
     * must still be current.
     *
     * <p>The layout goes with it, not only the texture: a host that reattaches
     * has reloaded its sheets, and origins keyed on the previous session's
     * sprite handles would answer "not in the atlas" for every one of them —
     * silently, and for the rest of the run.
     */
    public void dispose() {
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
        built = false;
        planned = false;
        broken = false;
        width = 0;
        height = 0;
        slots.clear();
        originBySheet.clear();
    }

    /**
     * The atlas as a sheet the rest of the renderer can address.
     *
     * <p>A {@link SpriteAPI} because that is the currency of a sheet quad and
     * of a {@link com.dillon.starsectormarines.render2d.QuadBatch}: the atlas
     * has to be resolvable through the same {@code batchBySheet} map as any
     * other sheet, or every consumer would need a second code path for it. It
     * answers only what a sheet quad asks — its pixel size, its used fraction
     * (all of it: this texture is ours and has no padding), and how to bind it.
     * Everything else is the identity of a handle nobody draws through directly.
     */
    private final class AtlasSprite implements SpriteAPI {

        private float alphaMult = 1f;
        private Color color = Color.WHITE;
        private float angle;
        private float centerX;
        private float centerY;
        private float sizeW;
        private float sizeH;
        private int blendSrc;
        private int blendDest;
        private float texX;
        private float texY;
        private float texW = 1f;
        private float texH = 1f;

        @Override public void bindTexture() {
            glBindTexture(GL_TEXTURE_2D, texture);
        }

        @Override public int getTextureId() { return texture; }
        @Override public float getWidth() { return width; }
        @Override public float getHeight() { return height; }
        @Override public float getTextureWidth() { return 1f; }
        @Override public float getTextureHeight() { return 1f; }

        @Override public void setBlendFunc(int src, int dest) { blendSrc = src; blendDest = dest; }
        @Override public void setNormalBlend() { }
        @Override public void setAdditiveBlend() { }
        @Override public int getBlendSrc() { return blendSrc; }
        @Override public int getBlendDest() { return blendDest; }

        @Override public void setCenter(float x, float y) { centerX = x; centerY = y; }
        @Override public void setCenterX(float cx) { centerX = cx; }
        @Override public void setCenterY(float cy) { centerY = cy; }
        @Override public float getCenterX() { return centerX; }
        @Override public float getCenterY() { return centerY; }

        @Override public void setSize(float w, float h) { sizeW = w; sizeH = h; }
        @Override public void setWidth(float w) { sizeW = w; }
        @Override public void setHeight(float h) { sizeH = h; }

        @Override public float getAngle() { return angle; }
        @Override public void setAngle(float a) { angle = a; }

        @Override public Color getColor() { return color; }
        @Override public void setColor(Color c) { color = c == null ? Color.WHITE : c; }
        @Override public Color getAverageColor() { return Color.WHITE; }
        @Override public Color getAverageBrightColor() { return Color.WHITE; }

        @Override public float getAlphaMult() { return alphaMult; }
        @Override public void setAlphaMult(float mult) { alphaMult = mult; }

        @Override public float getTexX() { return texX; }
        @Override public float getTexY() { return texY; }
        @Override public float getTexWidth() { return texW; }
        @Override public float getTexHeight() { return texH; }
        @Override public void setTexX(float x) { texX = x; }
        @Override public void setTexY(float y) { texY = y; }
        @Override public void setTexWidth(float w) { texW = w; }
        @Override public void setTexHeight(float h) { texH = h; }

        // A batched sheet is never drawn through the host's own sprite calls;
        // the drain appends it into a QuadBatch and flushes that. Answering
        // these would be a second, unbatched way to paint the atlas.
        @Override public void renderAtCenter(float x, float y) { }
        @Override public void render(float x, float y) { }
        @Override public void renderRegionAtCenter(float x, float y, float tx, float ty,
                                                   float tw, float th) { }
        @Override public void renderRegion(float x, float y, float tx, float ty,
                                           float tw, float th) { }
        @Override public void renderNoBind(float x, float y) { }
        @Override public void renderAtCenterNoBind(float x, float y) { }
        @Override public void renderWithCorners(float blX, float blY, float tlX, float tlY,
                                                float trX, float trY, float brX, float brY) { }
    }
}
