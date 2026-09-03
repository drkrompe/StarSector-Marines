package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.GlErrors;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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
import static org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH;
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

/**
 * A set of loaded images composited into one GL texture, so a run of quads
 * drawn from them is one texture bind rather than one per image.
 *
 * <p><b>Why a layer wants this.</b> A run of quads coalesces exactly as far as
 * its texture stays the same, so several sheets in painter order is a flush at
 * every change and one sheet is one flush. That reasoning is the same whether
 * the images are the six terrain sheets a {@code GROUND} quad is cut from
 * ({@link GroundAtlas}) or the several dozen separate PNGs a marine's body,
 * head, feet and weapon are drawn from ({@link UnitAtlas}) — which is why the
 * mechanism lives here and only the question "which images?" is a subclass's.
 *
 * <p><b>Painter order is untouched.</b> This changes which texture a quad
 * addresses and where in it, and nothing else: the same quads in the same order
 * over the same destination rectangles.
 *
 * <p><b>Planned on the CPU, built on the GPU.</b> The layout is a shelf pack of
 * the images' own content sizes and is pure arithmetic, so it is settled before
 * any GL runs — which is what lets a host build the atlas's
 * {@link com.dillon.starsectormarines.render2d.QuadBatch} at the same lifecycle
 * seam as every other sheet's. The pixels are copied at drain time inside a
 * layer's own custom pass: each image is read back from its own texture and
 * written into its slot, so the atlas holds the images' exact texels rather than
 * a resample of them.
 *
 * <p><b>Slots carry a gutter</b> of {@link #GUTTER_PX} transparent texels.
 * Bilinear filtering reaches one texel past the rectangle it samples, and inside
 * one image that neighbour is the art it has always been; only at an image's
 * outer boundary does the atlas change what is next door, and the gutter is what
 * keeps that from being somebody else's art.
 *
 * <p><b>It fails soft.</b> No texture object, a layout that will not fit the
 * driver's maximum, a read-back that errors — {@link #isServing()} answers false
 * and every quad addresses its own image exactly as before, with the same
 * picture.
 */
public abstract class SpriteAtlas {

    /**
     * Transparent texels between one image's content and the next.
     *
     * <p>Bilinear reaches one texel; eight is that with room for a future
     * filter that reaches further, and it costs a few thousand texels on an
     * atlas whose content is a couple of megapixels.
     */
    static final int GUTTER_PX = 8;

    /** One source image and where its content sits in the atlas. */
    public record Placement(int x, int y, int width, int height) { }

    /** One image offered to the layout, with the content size it actually uses. */
    public record Source(SpriteAPI sheet, int contentW, int contentH) { }

    private final String label;
    private final boolean enabled;
    /**
     * Widths the layout may use, smallest first.
     *
     * <p>Tried in order and the first that holds everything at a height no
     * greater than its own width wins, so an atlas is only as large as its art
     * needs. Stated rather than measured because the layout is planned with no
     * GL context to ask; the driver's own maximum is consulted at build time and
     * a side it refuses is a fail-soft rather than a crash.
     */
    private final int[] candidateSides;

    private final List<Slot> slots = new ArrayList<>();
    private final Map<SpriteAPI, Placement> placementBySheet = new IdentityHashMap<>();
    private final AtlasSprite sprite = new AtlasSprite();

    /** Why the last plan gave up, or null; see {@link #planDiagnostic()}. */
    private String diagnostic;

    private boolean planned;
    private boolean broken;
    private boolean built;
    private int width;
    private int height;
    private int texture;

    protected SpriteAtlas(String label, boolean enabled, int... candidateSides) {
        this.label = label;
        this.enabled = enabled;
        this.candidateSides = candidateSides.clone();
    }

    private record Slot(SpriteAPI sheet, int contentW, int contentH, int x, int y) { }

    /**
     * Settles the layout from {@code candidates}. GL-free, idempotent, and the
     * only thing that has to happen before a batch can be built for the atlas.
     *
     * @param minimumSheets fewer distinct images than this and there is nothing
     *                      worth compositing — one image is already one bind
     * @return whether a layout exists at all; false leaves every caller on the
     *         per-image path
     */
    protected final boolean plan(List<Source> candidates, int minimumSheets) {
        if (planned || broken || !enabled || candidates == null) return planned;
        planned = true;
        List<Slot> distinct = new ArrayList<>();
        for (Source source : candidates) add(distinct, source);
        if (distinct.size() < minimumSheets) {
            broken = true;
            return false;
        }
        if (!layOut(distinct)) {
            broken = true;
            diagnostic = describeOverflow(distinct);
            return false;
        }
        return true;
    }

    /**
     * Why the last plan gave up, or null when it did not.
     *
     * <p>A layout that will not fit is silent by design — every quad simply goes
     * back to its own image — so an atlas that quietly stopped serving looks
     * exactly like an atlas nobody switched on. This says which it was, in the
     * one sentence that is worth having: how much art was offered and how wide
     * the widest piece is.
     */
    public final String planDiagnostic() {
        return diagnostic;
    }

    private String describeOverflow(List<Slot> candidates) {
        long area = 0;
        int widest = 0;
        int tallest = 0;
        for (Slot slot : candidates) {
            area += (long) (slot.contentW() + GUTTER_PX) * (slot.contentH() + GUTTER_PX);
            widest = Math.max(widest, slot.contentW());
            tallest = Math.max(tallest, slot.contentH());
        }
        return label + ": " + candidates.size() + " images, " + area
                + " texels with gutters, widest " + widest + ", tallest " + tallest
                + ", would not fit " + Arrays.toString(candidateSides);
    }

    private static void add(List<Slot> into, Source source) {
        if (source == null || source.sheet() == null) return;
        if (source.contentW() <= 0 || source.contentH() <= 0) return;
        for (Slot existing : into) if (existing.sheet() == source.sheet()) return;
        into.add(new Slot(source.sheet(), source.contentW(), source.contentH(), 0, 0));
    }

    private boolean layOut(List<Slot> candidates) {
        int[] contentW = new int[candidates.size()];
        int[] contentH = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            contentW[i] = candidates.get(i).contentW();
            contentH[i] = candidates.get(i).contentH();
        }
        for (int side : candidateSides) {
            int[] packed = shelfPack(contentW, contentH, side);
            if (packed == null) continue;
            int packedHeight = packed[packed.length - 1];
            if (packedHeight > side) continue;
            for (int i = 0; i < candidates.size(); i++) {
                Slot proposed = candidates.get(i);
                int x = packed[i * 2];
                int y = packed[i * 2 + 1];
                slots.add(new Slot(proposed.sheet(), proposed.contentW(), proposed.contentH(), x, y));
                placementBySheet.put(proposed.sheet(),
                        new Placement(x, y, proposed.contentW(), proposed.contentH()));
            }
            width = side;
            height = packedHeight;
            return true;
        }
        return false;
    }

    /**
     * Shelves the rectangles tallest first, which wastes the least on a set of
     * long thin strips and short wide plates.
     *
     * <p>Deliberately not a general rectangle packer: a set of images whose
     * sizes are known and stable does not need one, and a packer whose output
     * depended on iteration order would move every UV in the atlas the day one
     * image is re-exported a pixel wider. Every rectangle is placed inside a
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
        Arrays.sort(order, (a, b) -> {
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

    /** Whether the atlas is armed at all — for evidence that reports which run it was. */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether quads may address the atlas.
     *
     * <p>GL-free, because a collector is ({@code battle-render-nouns.md}, law
     * 2). It answers false until the frame after the one whose drain built the
     * texture, and false forever once anything has failed.
     */
    public final boolean isServing() {
        return enabled && built && !broken;
    }

    /**
     * Whether a layout exists.
     *
     * <p>Distinct from {@link #isServing()} because the two gate different
     * things: a plan is what a batch is built from, and only the host that
     * builds that batch may call plan. A collector asks this before asking for
     * the texture to be built, so an atlas nothing can draw is never put in
     * front of quads that would then draw nothing at all.
     */
    public final boolean isPlanned() {
        return planned && !broken && !slots.isEmpty();
    }

    /** The sheet every atlased quad addresses while the atlas is serving. */
    public final SpriteAPI sheet() {
        return sprite;
    }

    /** Atlas width in texels; settled by plan, zero before it. */
    public final int width() {
        return width;
    }

    public final int height() {
        return height;
    }

    /**
     * Where {@code sheet}'s content sits, as {@code (x << 16) | y}, or
     * {@code -1} for an image the atlas does not hold.
     *
     * <p>Packed into one int so a caller can cache the origins it needs per
     * collect and add them with a shift rather than probing a map per quad.
     */
    public final int origin(SpriteAPI sheet) {
        Placement placement = placementBySheet.get(sheet);
        return placement == null ? -1 : (placement.x() << 16) | placement.y();
    }

    /**
     * Where {@code sheet}'s content sits and how big it is, or null for an
     * image the atlas does not hold.
     *
     * <p>The size is what a whole-image draw needs and an addressed sub-rectangle
     * does not: a caller that used to hand the image to the host's own sprite
     * call never had to say how much of it to draw.
     */
    public final Placement placement(SpriteAPI sheet) {
        return placementBySheet.get(sheet);
    }

    /** Images the atlas holds — for evidence that wants to name them. */
    public final int sheetCount() {
        return slots.size();
    }

    /**
     * Builds the texture if it is not built yet. Owns its complete GL
     * lifecycle, as a custom pass must ({@code battle-render-nouns.md}, law 3).
     *
     * @return whether the atlas can be addressed from the next frame
     */
    public final boolean sync() {
        if (!enabled || broken || built) return isServing();
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
            if (GlErrors.check("glTexImage2D (" + label + ")")) {
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
     * Copies one image's content into its slot.
     *
     * <p>Through the driver rather than through a blit into a render target:
     * the image is already a texture and reading it back is the one way to be
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
        if (GlErrors.check("glGetTexImage (" + label + " source)")) {
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
        // and is entirely invisible in the code: every quad still draws real
        // art, just somebody else's.
        glTexSubImage2D(GL_TEXTURE_2D, 0, slot.x(), height - slot.y() - slot.contentH(),
                slot.contentW(), slot.contentH(), GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        boolean failed = GlErrors.check("glTexSubImage2D (" + label + " slot)");
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
     * has reloaded its images, and origins keyed on the previous session's
     * sprite handles would answer "not in the atlas" for every one of them —
     * silently, and for the rest of the run.
     */
    public final void dispose() {
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
        built = false;
        planned = false;
        broken = false;
        width = 0;
        height = 0;
        slots.clear();
        placementBySheet.clear();
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
