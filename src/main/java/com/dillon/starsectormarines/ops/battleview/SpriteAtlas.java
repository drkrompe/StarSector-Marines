package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.GlErrors;
import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS;
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
 * ({@link GroundAtlas}), the several dozen separate PNGs a marine's body,
 * head, feet and weapon are drawn from ({@link UnitAtlas}), or the props a map
 * places from a dozen catalog sheets ({@link DecorationAtlas}) — which is why
 * the mechanism lives here and only the question "which images?" is a
 * subclass's.
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
 * a resample of them — except where a subclass asks for a slot smaller than its
 * image ({@link Source#imageW}), which holds a copy filtered down to that size.
 *
 * <p><b>Whole images or regions of them.</b> A slot holds either a whole image
 * ({@link Source}) or one rectangle cut from a sheet ({@link Region}). A region
 * carries an <em>apron</em> of the sheet's own neighbouring texels, clamped at
 * the sheet's edge the way {@code GL_CLAMP_TO_EDGE} answers there, so a bilinear
 * tap that reaches past the rectangle reads what it read on the sheet rather
 * than the gutter's transparency.
 *
 * <p><b>Slots carry a gutter</b> of {@link #gutterPx()} transparent texels.
 * Bilinear filtering reaches one texel past the rectangle it samples, and inside
 * one image that neighbour is the art it has always been; only at an image's
 * outer boundary does the atlas change what is next door, and the gutter is what
 * keeps that from being somebody else's art.
 *
 * <p><b>A whole image may be held trimmed</b> to the part of it that is not
 * transparent ({@link Source#trimW}): its alpha bounds and one texel of the
 * image's own transparent texels around them. Every texel left out is
 * transparent and at least a texel from anything that is not, so no bilinear
 * tap that can colour a pixel reads one of them; a draw of the whole image is
 * then a draw of the held rectangle over the matching part of its destination
 * ({@link Placement#trimX}), which paints the same pixels.
 *
 * <p><b>It fails soft, and says so.</b> No texture object, a layout that will
 * not fit the driver's maximum, a read-back that errors — {@link #isServing()}
 * answers false and every quad addresses its own image exactly as before, with
 * the same picture. Because that fallback cannot be seen in the picture, every
 * image left out — over a subclass's size cap, refused by the layout, or lost
 * with a refused texture — is recorded as an {@link Exclusion}, and the whole
 * account is logged once per plan ({@link #report()}).
 *
 * <p><b>It can grow from what it is asked to draw.</b> A subclass that
 * {@linkplain #gathers gathers} a layer is told about every image drawn there
 * that it does not hold ({@link #noteMissed}), and {@link #extend} takes them in
 * at the next frame boundary. A planned atlas reserves rows below its layout
 * for that ({@link #growthHeadroom}), so an image first drawn in the middle of
 * a battle is one upload into the texture that already exists rather than a
 * rebuild of every image in it; only when the headroom is spent is the whole
 * layout settled again. This is what keeps an atlas from depending on a list of
 * its images being kept up to date: the list seeds the first frame, and
 * anything it forgot joins the first time it is drawn.
 */
public abstract class SpriteAtlas {

    /**
     * Transparent texels between one image's content and the next, unless a subclass says otherwise
     * ({@link #gutterPx()}).
     *
     * <p>Bilinear reaches one texel; eight is that with room for a future
     * filter that reaches further, and it costs a few thousand texels on an
     * atlas whose content is a couple of megapixels.
     */
    static final int GUTTER_PX = 8;

    /**
     * One source image and where its content sits in the atlas.
     *
     * <p>{@code x, y, width, height} are the texels the atlas holds. For an image held trimmed they are a part of
     * it: the image is {@code fullW x fullH} slot texels, and what is held starts {@code trimX, trimY} into it
     * ({@code y} down from the top). An untrimmed image's held rectangle is all of it.
     */
    public record Placement(int x, int y, int width, int height, int trimX, int trimY, int fullW, int fullH) {
        public Placement(int x, int y, int width, int height) {
            this(x, y, width, height, 0, 0, width, height);
        }

        /** Whether only part of the image is held. */
        public boolean trimmed() {
            return trimX != 0 || trimY != 0 || width != fullW || height != fullH;
        }
    }

    /**
     * One image offered to the layout, with the content size its slot holds.
     *
     * <p>{@code imageW x imageH} are the texels the image's own texture has; zero, the usual case, means the slot
     * holds exactly those ({@code contentW x contentH}). A slot smaller than its image holds a copy filtered down to
     * the slot's size once, at upload: the form an image too large to hold whole takes when it is never drawn larger
     * than that ({@link UnitAtlas}). Callers address it in the image's own pixels as they always did, since every
     * redirect already scales those into the slot.
     *
     * <p>{@code trimX, trimY, trimW x trimH} is the part of the {@code contentW x contentH} slot the atlas holds, in
     * slot texels with {@code y} down from the top; a {@code trimW} of zero, the usual case, holds all of it. Every
     * texel outside it must be transparent and at least one texel from one that is not.
     */
    public record Source(SpriteAPI sheet, int contentW, int contentH, String name, int imageW, int imageH,
                         int trimX, int trimY, int trimW, int trimH) {
        public Source(SpriteAPI sheet, int contentW, int contentH) {
            this(sheet, contentW, contentH, null);
        }

        public Source(SpriteAPI sheet, int contentW, int contentH, String name) {
            this(sheet, contentW, contentH, name, 0, 0);
        }

        public Source(SpriteAPI sheet, int contentW, int contentH, String name, int imageW, int imageH) {
            this(sheet, contentW, contentH, name, imageW, imageH, 0, 0, 0, 0);
        }

        /** This source holding only {@code x, y, w x h} of its slot; itself when that is all of it or out of it. */
        public Source trimmedTo(int x, int y, int w, int h) {
            if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > contentW || y + h > contentH) return this;
            if (x == 0 && y == 0 && w == contentW && h == contentH) return this;
            return new Source(sheet, contentW, contentH, name, imageW, imageH, x, y, w, h);
        }
    }

    /**
     * An image drawn in a gathered layer that the atlas does not hold, and the largest size it was drawn at in the
     * frames before it was taken, scaled to the camera's closest zoom; {@code NaN} when no draw could say.
     */
    public record Missed(SpriteAPI image, float closestW, float closestH) { }

    /**
     * One rectangle of a sheet offered to the layout.
     *
     * <p>{@code sheetW}/{@code sheetH} are the sheet's content size in texels —
     * the numbers the sheet's own batch divides by — and the rectangle is
     * stated the way a sheet quad states its source, {@code y} down from the top.
     */
    public record Region(SpriteAPI sheet, int sheetW, int sheetH,
                         int srcX, int srcY, int width, int height, String name) { }

    /** What {@link #extend} did with the images it was given. */
    public enum Growth {
        /** Nothing new to place. */
        NONE,
        /** Placed in the reserved rows; uploaded at the next {@link #sync()}, the texture and its size unchanged. */
        APPENDED,
        /** The whole layout settled again at a new size: the texture is rebuilt and its batch must be too. */
        RELAID
    }

    /** Why an image is not in the atlas. */
    public enum Exclusion {
        /** Larger than the subclass will give a slot. */
        OVER_CAP,
        /** No recorded size to copy it by. */
        NO_SIZE,
        /** The layout did not fit any of the atlas's sides. */
        NO_ROOM,
        /** The driver refused the texture or a copy into it. */
        REFUSED
    }

    private static final Logger LOG = Global.getLogger(SpriteAtlas.class);

    /** How many names a report lists per reason before it only counts. */
    private static final int NAMES_PER_REASON = 6;

    private final String label;
    /** Off only for {@link DecorationAtlas} under its {@code battle.render.doodadAtlas} control. */
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
    /** Each region slot's sheet, numbered for {@link RegionTable#key}. */
    private final Map<SpriteAPI, Integer> sheetIndex = new IdentityHashMap<>();
    private final RegionTable regions = new RegionTable();
    private final Map<Exclusion, List<String>> excluded = new EnumMap<>(Exclusion.class);
    private final AtlasSprite sprite = new AtlasSprite();

    /** Why the last plan gave up, or null; see {@link #planDiagnostic()}. */
    private String diagnostic;
    /** What the driver refused, or null. */
    private String refusal;
    private boolean reported;

    private boolean planned;
    private boolean broken;
    private boolean built;
    private int width;
    /** Rows of the texture: the layout's own and the headroom reserved below it. */
    private int height;
    /** Rows the layout fills, gutter included; where an appended image's shelf starts. */
    private int usedHeight;
    private int texture;
    /** Textures a relayout replaced, deleted at the next GL seam. */
    private final List<Integer> retired = new ArrayList<>();

    /** Images placed in the headroom whose texels are not uploaded yet, and where they will sit. */
    private final List<Slot> pendingUploads = new ArrayList<>();
    private final Map<SpriteAPI, Placement> pendingPlacements = new IdentityHashMap<>();
    /** Replaced whenever a placement is added or moved, so a caller that cached addresses re-resolves them. */
    private Object layoutToken = new Object();

    /** Images drawn in a gathered layer that the atlas did not hold, in the order they were first drawn. */
    private final List<SpriteAPI> missed = new ArrayList<>();
    /** Each image in {@link #missed}: the largest whole-image size it was drawn at, at the closest zoom. */
    private final Map<SpriteAPI, float[]> missedSize = new IdentityHashMap<>();
    /** Every image already noted, offered or not: an image is judged once, not once per frame it is drawn. */
    private final Set<SpriteAPI> judged = Collections.newSetFromMap(new IdentityHashMap<>());

    protected SpriteAtlas(String label, int... candidateSides) {
        this(label, true, candidateSides);
    }

    protected SpriteAtlas(String label, boolean enabled, int... candidateSides) {
        this.label = label;
        this.enabled = enabled;
        this.candidateSides = candidateSides.clone();
    }

    /**
     * One slot: the rectangle {@code srcX, srcY, w, h} of {@code sheet}, whose
     * content is {@code sheetW x sheetH}, copied with {@code apron} texels of its
     * surroundings and its content placed at {@code x, y} in the atlas. A whole
     * image is the rectangle that is all of it, with no apron, or, held trimmed, the part of it that is not
     * transparent; {@code image} says the slot is addressed by its image rather than as a rectangle of a sheet.
     * {@code imageW x imageH} is the image texture's own texel size when the slot holds a smaller copy of it, else
     * zero.
     */
    private record Slot(SpriteAPI sheet, int sheetW, int sheetH, int srcX, int srcY,
                        int w, int h, int apron, int x, int y, String name, int imageW, int imageH, boolean image) {
        Slot at(int px, int py) {
            return new Slot(sheet, sheetW, sheetH, srcX, srcY, w, h, apron, px, py, name, imageW, imageH, image);
        }

        boolean wholeImage() {
            return image || (srcX == 0 && srcY == 0 && w == sheetW && h == sheetH && apron == 0);
        }

        Placement placement() {
            return new Placement(x, y, w, h, srcX, srcY, sheetW, sheetH);
        }
    }

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
        List<Slot> distinct = new ArrayList<>();
        for (Source source : candidates) add(distinct, source);
        return settle(distinct, minimumSheets);
    }

    /**
     * As {@link #plan(List, int)}, for rectangles cut from sheets: each slot is
     * {@code apronPx} larger on every side than its rectangle, filled with the
     * sheet's own texels around it.
     */
    protected final boolean planRegions(List<Region> candidates, int apronPx, int minimumRegions) {
        if (planned || broken || !enabled || candidates == null) return planned;
        List<Slot> distinct = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Map<SpriteAPI, Integer> indices = new IdentityHashMap<>();
        for (Region region : candidates) {
            if (region == null || region.sheet() == null) continue;
            if (region.width() <= 0 || region.height() <= 0
                    || region.sheetW() <= 0 || region.sheetH() <= 0) {
                exclude(Exclusion.NO_SIZE, region.name());
                continue;
            }
            Integer index = indices.get(region.sheet());
            if (index == null) {
                index = indices.size();
                indices.put(region.sheet(), index);
            }
            if (!seen.add(RegionTable.key(index, region.srcX(), region.srcY(),
                    region.width(), region.height()))) continue;
            distinct.add(new Slot(region.sheet(), region.sheetW(), region.sheetH(),
                    region.srcX(), region.srcY(), region.width(), region.height(),
                    Math.max(0, apronPx), 0, 0, region.name(), 0, 0, false));
        }
        return settle(distinct, minimumRegions);
    }

    private boolean settle(List<Slot> distinct, int minimum) {
        planned = true;
        if (distinct.size() < minimum) {
            broken = true;
            diagnostic = distinct.size() + " images offered, fewer than the " + minimum
                    + " an atlas is worth";
            return false;
        }
        if (!layOut(distinct)) {
            broken = true;
            diagnostic = describeOverflow(distinct);
            List<String> names = excluded.computeIfAbsent(Exclusion.NO_ROOM, reason -> new ArrayList<>());
            for (Slot slot : distinct) names.add(slotName(slot));
            logOnce();
            return false;
        }
        return true;
    }

    /**
     * Records an image a subclass did not offer, and why.
     *
     * <p>Called while gathering candidates. The fallback for it is the image's
     * own texture, which draws the same picture, so this record is the only
     * place the difference shows.
     */
    protected final void exclude(Exclusion reason, String what) {
        List<String> names = excluded.computeIfAbsent(reason, r -> new ArrayList<>());
        String name = what == null ? "(unnamed)" : what;
        // One image is often offered by several families; it is one image left out.
        if (!names.contains(name)) names.add(name);
    }

    /** Every image left out, by reason; empty when the atlas holds everything it was offered. */
    public final Map<Exclusion, List<String>> exclusions() {
        return Collections.unmodifiableMap(excluded);
    }

    /**
     * One line on what this atlas is doing — off, serving, waiting to be built,
     * or not serving — how many images in how large a texture, and each reason
     * something was left out with its count and the first few names.
     */
    public final String report() {
        StringBuilder out = new StringBuilder(label).append(": ");
        if (!enabled) out.append("off");
        else if (isServing()) out.append("serving");
        else if (broken) out.append("not serving");
        else if (isPlanned()) out.append("planned, not built");
        else out.append("not planned");
        if (enabled && !slots.isEmpty()) {
            long content = 0;
            int trimmed = 0;
            long trimmedHeld = 0;
            long trimmedWhole = 0;
            for (Slot slot : slots) {
                content += (long) (slot.w() + 2 * slot.apron()) * (slot.h() + 2 * slot.apron());
                if (slot.image() && slot.placement().trimmed()) {
                    trimmed++;
                    trimmedHeld += (long) slot.w() * slot.h();
                    trimmedWhole += (long) slot.sheetW() * slot.sheetH();
                }
            }
            out.append(", ").append(slots.size()).append(" images in ")
                    .append(width).append('x').append(height)
                    .append(String.format(Locale.ROOT, " (%.0f%% filled, %.1f MB, %d rows laid out)",
                            100.0 * content / Math.max(1L, (long) width * height),
                            width * (double) height * 4 / (1 << 20), usedHeight));
            if (trimmed > 0) {
                out.append(String.format(Locale.ROOT, ", %d trimmed to their art (%d of %d texels held)",
                        trimmed, trimmedHeld, trimmedWhole));
            }
        }
        String detail = detail();
        if (detail != null) out.append(", ").append(detail);
        if (diagnostic != null) out.append(" (").append(diagnostic).append(')');
        for (Map.Entry<Exclusion, List<String>> entry : excluded.entrySet()) {
            List<String> names = entry.getValue();
            out.append("; ").append(entry.getKey()).append(' ').append(names.size()).append(": ");
            for (int i = 0; i < Math.min(NAMES_PER_REASON, names.size()); i++) {
                if (i > 0) out.append(", ");
                out.append(names.get(i));
            }
            if (names.size() > NAMES_PER_REASON) out.append(", ...");
        }
        return out.toString();
    }

    /** A subclass's own clause for {@link #report()}, such as where its images came from; null for none. */
    protected String detail() {
        return null;
    }

    /**
     * Logs {@link #report()} once per plan, when the atlas has reached the
     * state it will keep: refused at plan, or built or refused at its first sync.
     */
    private void logOnce() {
        if (reported) return;
        reported = true;
        if (excluded.isEmpty() && !broken) LOG.info(report());
        else LOG.warn(report());
    }

    private static String slotName(Slot slot) {
        return slot.name() != null ? slot.name() : slot.w() + "x" + slot.h() + " image";
    }

    /**
     * Why the last plan gave up, or null when it did not.
     *
     * <p>A layout that will not fit is silent by design — every quad simply goes
     * back to its own image — so an atlas that quietly stopped serving looks
     * exactly like an atlas that was never built. This says which it was, in the
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
            int w = slot.w() + 2 * slot.apron();
            int h = slot.h() + 2 * slot.apron();
            area += (long) (w + gutterPx()) * (h + gutterPx());
            widest = Math.max(widest, w);
            tallest = Math.max(tallest, h);
        }
        return label + ": " + candidates.size() + " images, " + area
                + " texels with gutters, widest " + widest + ", tallest " + tallest
                + ", would not fit " + Arrays.toString(candidateSides);
    }

    private void add(List<Slot> into, Source source) {
        if (source == null || source.sheet() == null) return;
        if (source.contentW() <= 0 || source.contentH() <= 0) {
            exclude(Exclusion.NO_SIZE, source.name());
            return;
        }
        for (Slot existing : into) if (existing.sheet() == source.sheet()) return;
        boolean trimmed = source.trimW() > 0 && source.trimH() > 0;
        into.add(new Slot(source.sheet(), source.contentW(), source.contentH(),
                trimmed ? source.trimX() : 0, trimmed ? source.trimY() : 0,
                trimmed ? source.trimW() : source.contentW(), trimmed ? source.trimH() : source.contentH(),
                0, 0, 0, source.name(),
                Math.max(0, source.imageW()), Math.max(0, source.imageH()), true));
    }

    private boolean layOut(List<Slot> candidates) {
        int[] packW = new int[candidates.size()];
        int[] packH = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            Slot slot = candidates.get(i);
            packW[i] = slot.w() + 2 * slot.apron();
            packH[i] = slot.h() + 2 * slot.apron();
        }
        for (int side : candidateSides) {
            int[] packed = shelfPack(packW, packH, side, gutterPx());
            if (packed == null) continue;
            int packedHeight = packed[packed.length - 1];
            if (packedHeight > side) continue;
            regions.reset(candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                Slot proposed = candidates.get(i);
                int x = packed[i * 2] + proposed.apron();
                int y = packed[i * 2 + 1] + proposed.apron();
                slots.add(proposed.at(x, y));
                if (proposed.wholeImage()) {
                    placementBySheet.put(proposed.sheet(), proposed.at(x, y).placement());
                } else {
                    Integer index = sheetIndex.get(proposed.sheet());
                    if (index == null) {
                        index = sheetIndex.size();
                        sheetIndex.put(proposed.sheet(), index);
                    }
                    regions.put(RegionTable.key(index, proposed.srcX(), proposed.srcY(),
                            proposed.w(), proposed.h()), (x << 16) | y);
                }
            }
            width = side;
            usedHeight = packedHeight;
            int headroom = growthHeadroom(side);
            height = headroom <= 0 ? packedHeight
                    : Math.min(side, Integer.highestOneBit(packedHeight + headroom - 1) << 1);
            return true;
        }
        return false;
    }

    /** Whether {@code candidates} would lay out at any of the atlas's sides; GL-free and changes nothing. */
    private boolean fitsAnySide(List<Slot> candidates) {
        int[] packW = new int[candidates.size()];
        int[] packH = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            packW[i] = candidates.get(i).w() + 2 * candidates.get(i).apron();
            packH[i] = candidates.get(i).h() + 2 * candidates.get(i).apron();
        }
        for (int side : candidateSides) {
            int[] packed = shelfPack(packW, packH, side, gutterPx());
            if (packed != null && packed[packed.length - 1] <= side) return true;
        }
        return false;
    }

    /**
     * Rows a planned layout keeps free below itself for images first drawn
     * after it was planned, given the side it settled at; zero, the default,
     * for an atlas whose images are all known when it is planned.
     *
     * <p>An atlas that reserves any is given a power-of-two height that holds
     * the layout and the headroom, up to its side. A power of two states every
     * texel row's coordinate exactly where any other height rounds it, and the
     * unit atlas's pixel evidence measured the difference: 194 pixels against
     * the sprite path at 4096 rows, 976 at the 2652 the layout alone filled,
     * 1393 at 3676.
     */
    protected int growthHeadroom(int side) {
        return 0;
    }

    /**
     * Transparent texels around every slot: {@link #GUTTER_PX} unless a subclass knows nothing samples it further
     * than bilinear's one texel past a slot. Asked at every layout, so it must not change over an atlas's life.
     */
    protected int gutterPx() {
        return GUTTER_PX;
    }

    /**
     * Whether whole images drawn in {@code layer} that the atlas does not hold
     * should be offered to it. False by default: an atlas planned from a
     * complete list has nothing to learn from its draws.
     */
    public boolean gathers(RenderLayer layer) {
        return false;
    }

    /**
     * Records that {@code image} was drawn in a layer this atlas
     * {@linkplain #gathers gathers} and is not in it.
     *
     * <p>Called from the collect, so it is GL-free and costs one identity
     * lookup once an image has been seen: an image the atlas has already
     * judged (taken in, or turned away and recorded as an {@link Exclusion}) is
     * not noted again. An atlas's own texture, or another atlas's, is not art
     * and is never noted.
     */
    public final void noteMissed(SpriteAPI image) {
        noteMissed(image, Float.NaN, Float.NaN);
    }

    /**
     * As {@link #noteMissed(SpriteAPI)}, with the size the whole image was drawn at by this draw, scaled to the
     * camera's closest zoom ({@code NaN} when unknown). Until {@link #takeMissed} hands it on, the largest such size
     * is kept, so an image is judged by the biggest anyone drew it in the frame that first drew it.
     */
    public final void noteMissed(SpriteAPI image, float closestW, float closestH) {
        if (image == null || image instanceof AtlasSprite || !planned || broken) return;
        if (judged.add(image)) {
            missed.add(image);
            missedSize.put(image, new float[]{closestW, closestH});
            return;
        }
        if (!(closestW > 0f) || missedSize.isEmpty()) return;
        float[] size = missedSize.get(image);
        if (size == null) return;
        if (!(size[0] >= closestW)) size[0] = closestW;
        if (!(size[1] >= closestH)) size[1] = closestH;
    }

    /**
     * Whether a draw of {@code image} still has something to tell the atlas: it has not been judged yet, or it has
     * been noted and not taken. False for an image held or already turned away, so a caller can skip working out a
     * size nobody will read.
     */
    public final boolean awaitsJudgement(SpriteAPI image) {
        if (image == null || image instanceof AtlasSprite || !planned || broken) return false;
        return !judged.contains(image) || missedSize.containsKey(image);
    }

    /**
     * Where {@code image} came from and its logical size, for an image the atlas was told about only by being drawn;
     * null when nothing can say, the default for an atlas that takes nothing in on sight.
     */
    public ModTextures.Loaded loadedImage(SpriteAPI image) {
        return null;
    }

    /** The images noted since the last call, oldest first; each is noted once for the atlas's life. */
    protected final List<Missed> takeMissed() {
        if (missed.isEmpty()) return Collections.emptyList();
        List<Missed> out = new ArrayList<>(missed.size());
        for (SpriteAPI image : missed) {
            float[] size = missedSize.get(image);
            out.add(size == null ? new Missed(image, Float.NaN, Float.NaN) : new Missed(image, size[0], size[1]));
        }
        missed.clear();
        missedSize.clear();
        return out;
    }

    /**
     * Takes {@code additions} into a planned atlas. GL-free: call it between
     * frames, before the collect, never between a collect and its drain.
     *
     * <p>Once the texture is built, additions that fit the reserved rows are
     * placed there and uploaded by the next {@link #sync()}; until then they
     * are not addressable, so nothing samples texels that are not there yet.
     * Additions that do not fit settle the whole layout again, which the host
     * answers by rebuilding the atlas's batch, because the texture's size
     * changes. A set that fits no side at all is turned away as
     * {@link Exclusion#NO_ROOM} and the atlas keeps serving what it has.
     */
    protected final Growth extend(List<Source> additions) {
        if (!isPlanned() || additions == null) return Growth.NONE;
        List<Slot> fresh = new ArrayList<>();
        for (Source source : additions) {
            if (source == null || source.sheet() == null) continue;
            if (placementBySheet.containsKey(source.sheet()) || pendingPlacements.containsKey(source.sheet())) continue;
            add(fresh, source);
        }
        if (fresh.isEmpty()) return Growth.NONE;
        if (built && appendIntoHeadroom(fresh)) return Growth.APPENDED;

        List<Slot> all = new ArrayList<>(slots.size() + pendingUploads.size() + fresh.size());
        for (Slot slot : slots) all.add(slot.at(0, 0));
        for (Slot slot : pendingUploads) all.add(slot.at(0, 0));
        all.addAll(fresh);
        if (!fitsAnySide(all)) {
            List<String> names = excluded.computeIfAbsent(Exclusion.NO_ROOM, reason -> new ArrayList<>());
            for (Slot slot : fresh) names.add(slotName(slot));
            return Growth.NONE;
        }
        slots.clear();
        placementBySheet.clear();
        sheetIndex.clear();
        pendingUploads.clear();
        pendingPlacements.clear();
        layOut(all);
        if (texture != 0) retired.add(texture);
        texture = 0;
        built = false;
        reported = false;
        layoutToken = new Object();
        return Growth.RELAID;
    }

    /** Shelves {@code fresh} in the rows below the layout, if they fit in what the texture already has. */
    private boolean appendIntoHeadroom(List<Slot> fresh) {
        int[] packW = new int[fresh.size()];
        int[] packH = new int[fresh.size()];
        for (int i = 0; i < fresh.size(); i++) {
            packW[i] = fresh.get(i).w() + 2 * fresh.get(i).apron();
            packH[i] = fresh.get(i).h() + 2 * fresh.get(i).apron();
        }
        int[] packed = shelfPack(packW, packH, width, gutterPx());
        if (packed == null) return false;
        // The layout's last gutter is the first gutter of the appended shelf.
        int top = usedHeight - gutterPx();
        int packedHeight = packed[packed.length - 1];
        if (top + packedHeight > height) return false;
        for (int i = 0; i < fresh.size(); i++) {
            Slot proposed = fresh.get(i);
            Slot placed = proposed.at(packed[i * 2] + proposed.apron(), top + packed[i * 2 + 1] + proposed.apron());
            pendingUploads.add(placed);
            pendingPlacements.put(placed.sheet(), placed.placement());
        }
        usedHeight = top + packedHeight;
        return true;
    }

    /**
     * An object that changes whenever a placement is added or moved; a caller
     * that resolves addresses once and keeps them keys them on it.
     */
    public final Object layoutToken() {
        return layoutToken;
    }

    /**
     * Whether the atlas has GL work waiting: a texture to build, or images
     * placed in its headroom to upload. A host puts {@link #sync()} in front of
     * the layer it serves while this holds, and only then.
     */
    public final boolean needsSync() {
        return isPlanned() && (!built || !pendingUploads.isEmpty());
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
        return shelfPack(contentW, contentH, side, GUTTER_PX);
    }

    /** {@link #shelfPack(int[], int[], int)} with a {@code gutter} other than {@link #GUTTER_PX}. */
    static int[] shelfPack(int[] contentW, int[] contentH, int side, int gutter) {
        int count = contentW.length;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            int byHeight = Integer.compare(contentH[b], contentH[a]);
            return byHeight != 0 ? byHeight : Integer.compare(contentW[b], contentW[a]);
        });
        int[] out = new int[count * 2 + 1];
        int penX = gutter;
        int penY = gutter;
        int shelfH = 0;
        for (int slot : order) {
            if (contentW[slot] + 2 * gutter > side) return null;
            if (penX + contentW[slot] + gutter > side) {
                penX = gutter;
                penY += shelfH + gutter;
                shelfH = 0;
            }
            out[slot * 2] = penX;
            out[slot * 2 + 1] = penY;
            penX += contentW[slot] + gutter;
            shelfH = Math.max(shelfH, contentH[slot]);
        }
        int height = penY + shelfH + gutter;
        if (height > side) return null;
        out[count * 2] = height;
        return out;
    }

    /**
     * Whether quads may address the atlas.
     *
     * <p>GL-free, because a collector is ({@code battle-render-nouns.md}, law
     * 2). It answers false until the frame after the one whose drain built the
     * texture, and false forever once anything has failed.
     */
    public final boolean isServing() {
        return built && !broken;
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

    /**
     * Where the rectangle {@code srcX, srcY, srcW, srcH} of {@code sheet} sits,
     * as {@code (x << 16) | y}, or {@code -1} when the atlas holds no slot for
     * exactly that rectangle.
     *
     * <p>Exact rather than containing: a quad that addresses some other cut of
     * a sheet the atlas also holds keeps its own sheet, which is the same
     * picture through one more bind. Allocation-free, because it is asked once
     * per quad in a collect.
     */
    public final int regionOrigin(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH) {
        Integer index = sheetIndex.get(sheet);
        if (index == null) return -1;
        return regions.get(RegionTable.key(index, srcX, srcY, srcW, srcH));
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
        if (broken) return false;
        if (built) {
            if (!pendingUploads.isEmpty()) uploadPending();
            return isServing();
        }
        if (!planned || slots.isEmpty()) return false;
        try {
            build();
        } catch (RuntimeException failure) {
            broken = true;
            if (refusal == null) refusal = failure.toString();
        }
        if (broken) {
            exclude(Exclusion.REFUSED, slots.size() + " images ("
                    + (refusal == null ? "unknown" : refusal) + ")");
        }
        logOnce();
        return isServing();
    }

    /** Uploads the images {@link #extend} placed in the headroom, and only then lets quads address them. */
    private void uploadPending() {
        List<Slot> uploading = new ArrayList<>(pendingUploads);
        ByteBuffer scratch = null;
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            for (List<Slot> group : bySheet(uploading)) {
                scratch = copy(group, scratch);
                if (broken) break;
            }
        } catch (RuntimeException failure) {
            broken = true;
            if (refusal == null) refusal = failure.toString();
        } finally {
            glBindTexture(GL_TEXTURE_2D, 0);
            glPopClientAttrib();
        }
        if (broken) {
            exclude(Exclusion.REFUSED, slots.size() + uploading.size() + " images ("
                    + (refusal == null ? "unknown" : refusal) + ")");
            logOnce();
            return;
        }
        slots.addAll(uploading);
        placementBySheet.putAll(pendingPlacements);
        pendingUploads.clear();
        pendingPlacements.clear();
        layoutToken = new Object();
    }

    /** {@code group}'s slots by the image they are cut from, in first-seen order: each is read back once. */
    private static List<List<Slot>> bySheet(List<Slot> group) {
        Map<SpriteAPI, List<Slot>> bySheet = new IdentityHashMap<>();
        List<List<Slot>> ordered = new ArrayList<>();
        for (Slot slot : group) {
            List<Slot> into = bySheet.get(slot.sheet());
            if (into == null) {
                into = new ArrayList<>();
                bySheet.put(slot.sheet(), into);
                ordered.add(into);
            }
            into.add(slot);
        }
        return ordered;
    }

    private void deleteRetired() {
        for (int stale : retired) glDeleteTextures(stale);
        retired.clear();
    }

    private void build() {
        deleteRetired();
        int driverMax = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (driverMax > 0 && (width > driverMax || height > driverMax)) {
            broken = true;
            refusal = width + "x" + height + " is over the driver's " + driverMax;
            return;
        }
        texture = glGenTextures();
        if (texture == 0) {
            broken = true;
            refusal = "no texture object";
            return;
        }
        ByteBuffer scratch = null;
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glBindTexture(GL_TEXTURE_2D, texture);
            GlErrors.clear();
            ByteBuffer zeroed = transparent();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, zeroed);
            // LWJGL 2 hands the driver the buffer's address and keeps no reference to it, so a compiled caller may
            // let the collector free tens of megabytes of direct memory while the driver is still reading them: an
            // access violation inside the driver, under heap pressure, seen headless and possible in the game.
            Reference.reachabilityFence(zeroed);
            if (GlErrors.check("glTexImage2D (" + label + ")")) {
                broken = true;
                refusal = "glTexImage2D " + width + "x" + height;
                return;
            }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // Each sheet is read back once however many slots it fills: a
            // decoration atlas cuts dozens of regions from one sheet.
            Map<SpriteAPI, List<Slot>> bySheet = new IdentityHashMap<>();
            List<SpriteAPI> order = new ArrayList<>();
            for (Slot slot : slots) {
                List<Slot> group = bySheet.get(slot.sheet());
                if (group == null) {
                    group = new ArrayList<>();
                    bySheet.put(slot.sheet(), group);
                    order.add(slot.sheet());
                }
                group.add(slot);
            }
            for (SpriteAPI sheet : order) {
                scratch = copy(bySheet.get(sheet), scratch);
                if (broken) return;
            }
            built = true;
        } finally {
            glBindTexture(GL_TEXTURE_2D, 0);
            glPopClientAttrib();
        }
    }

    /**
     * Copies every slot cut from one image into the atlas.
     *
     * <p>Through the driver rather than through a blit into a render target:
     * the image is already a texture and reading it back is the one way to be
     * sure the atlas holds <em>its texels</em> and not a resample of them, which
     * is exactly what the pixel-equality evidence asks of this.
     *
     * <p>The read-back is of the whole GL texture, padding included — that is
     * all {@code glGetTexImage} will give — so a slot is unpacked with a row
     * length of the padded width, and reads the content rows off the bottom of
     * it. A sprite's content sits at the texture's origin with
     * {@code getTextureWidth}/{@code getTextureHeight} of it used, which is the
     * same convention every UV in this renderer already assumes.
     */
    private ByteBuffer copy(List<Slot> group, ByteBuffer scratch) {
        Slot first = group.get(0);
        SpriteAPI source = first.sheet();
        float usedU = source.getTextureWidth();
        float usedV = source.getTextureHeight();
        if (usedU <= 0f || usedV <= 0f) {
            broken = true;
            refusal = slotName(first) + " reports no used texture extent";
            return scratch;
        }
        // The texture's own texel size: smaller than the sheet's logical size when the build fitted
        // the image (texture-fit.md), and then the padding must be worked out from it.
        int texW = first.imageW() > 0 ? first.imageW() : ModTextures.shippedWidth(source, first.sheetW());
        int texH = first.imageH() > 0 ? first.imageH() : ModTextures.shippedHeight(source, first.sheetH());
        int paddedW = Math.max(texW, Math.round(texW / usedU));
        int paddedH = Math.max(texH, Math.round(texH / usedV));
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
            refusal = "glGetTexImage of " + slotName(first);
            return buffer;
        }

        if (first.imageW() > 0 && first.sheetW() <= texW && first.sheetH() <= texH) {
            // A slot smaller than its image: a copy filtered down once, every source texel weighed by
            // the area it covers, as a mipmap would hold it at the size it is drawn.
            buffer = downsample(buffer, paddedW, texW, texH, first.sheetW(), first.sheetH());
            paddedW = first.sheetW();
        } else if (texW != first.sheetW() || texH != first.sheetH()) {
            // Slots addressed in source pixels over a fitted texture: the sheet is resampled up to
            // its logical size once, so every rectangle and apron reads where it always did.
            buffer = resample(buffer, paddedW, texW, texH, first.sheetW(), first.sheetH());
            paddedW = first.sheetW();
        }
        glBindTexture(GL_TEXTURE_2D, texture);
        for (Slot slot : group) {
            if (slot.image() && slot.placement().trimmed()) checkTrim(slot, buffer, paddedW);
            if (slot.apron() == 0) uploadDirect(slot, buffer, paddedW);
            else uploadWithApron(slot, buffer, paddedW);
            if (GlErrors.check("glTexSubImage2D (" + label + " slot)")) {
                broken = true;
                refusal = "glTexSubImage2D of " + slotName(slot);
                break;
            }
        }
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        return buffer;
    }

    /**
     * Bilinear resample of the {@code w x h} image at the start of {@code src} (rows
     * {@code stride} texels apart) into a tightly packed {@code outW x outH} buffer, rows in the
     * same order. Premultiplied while filtering, so transparent texels lend no colour to an edge.
     */
    static ByteBuffer resample(ByteBuffer src, int stride, int w, int h, int outW, int outH) {
        ByteBuffer out = BufferUtils.createByteBuffer(outW * outH * 4);
        float sx = (float) w / outW;
        float sy = (float) h / outH;
        for (int y = 0; y < outH; y++) {
            float fy = Math.max(0f, Math.min(h - 1f, (y + 0.5f) * sy - 0.5f));
            int y0 = (int) fy;
            int y1 = Math.min(h - 1, y0 + 1);
            float ty = fy - y0;
            for (int x = 0; x < outW; x++) {
                float fx = Math.max(0f, Math.min(w - 1f, (x + 0.5f) * sx - 0.5f));
                int x0 = (int) fx;
                int x1 = Math.min(w - 1, x0 + 1);
                float tx = fx - x0;
                float r = 0f, g = 0f, b = 0f, a = 0f;
                for (int k = 0; k < 4; k++) {
                    int px = (k & 1) == 0 ? x0 : x1;
                    int py = (k & 2) == 0 ? y0 : y1;
                    float weight = ((k & 1) == 0 ? 1f - tx : tx) * ((k & 2) == 0 ? 1f - ty : ty);
                    int at = (py * stride + px) * 4;
                    float alpha = (src.get(at + 3) & 0xFF) * weight;
                    r += (src.get(at) & 0xFF) * alpha;
                    g += (src.get(at + 1) & 0xFF) * alpha;
                    b += (src.get(at + 2) & 0xFF) * alpha;
                    a += alpha;
                }
                int o = (y * outW + x) * 4;
                out.put(o, (byte) (a > 0f ? Math.round(r / a) : 0));
                out.put(o + 1, (byte) (a > 0f ? Math.round(g / a) : 0));
                out.put(o + 2, (byte) (a > 0f ? Math.round(b / a) : 0));
                out.put(o + 3, (byte) Math.round(a));
            }
        }
        out.limit(outW * outH * 4);
        return out;
    }

    /**
     * Box-filtered reduction of the {@code w x h} image at the start of {@code src} (rows {@code stride} texels apart)
     * into a tightly packed {@code outW x outH} buffer, rows in the same order, for {@code outW <= w} and
     * {@code outH <= h}: each output texel is the area-weighted mean of the source texels its footprint covers.
     * Premultiplied while filtering, as {@link #resample} is, so transparent texels lend no colour to an edge.
     *
     * <p>Not {@link #resample}'s four taps: a bilinear tap reduced four to one reads four texels of sixteen, the
     * sparkle a mipmap exists to remove, and a copy filtered once carries that removal for free.
     */
    static ByteBuffer downsample(ByteBuffer src, int stride, int w, int h, int outW, int outH) {
        ByteBuffer out = BufferUtils.createByteBuffer(outW * outH * 4);
        double sx = (double) w / outW;
        double sy = (double) h / outH;
        for (int y = 0; y < outH; y++) {
            double y0 = y * sy;
            double y1 = Math.min(h, y0 + sy);
            for (int x = 0; x < outW; x++) {
                double x0 = x * sx;
                double x1 = Math.min(w, x0 + sx);
                double r = 0, g = 0, b = 0, a = 0, area = 0;
                for (int py = (int) y0; py < y1; py++) {
                    double wy = Math.min(y1, py + 1) - Math.max(y0, py);
                    if (wy <= 0) continue;
                    for (int px = (int) x0; px < x1; px++) {
                        double weight = wy * (Math.min(x1, px + 1) - Math.max(x0, px));
                        if (weight <= 0) continue;
                        int at = (py * stride + px) * 4;
                        double alpha = (src.get(at + 3) & 0xFF) * weight;
                        r += (src.get(at) & 0xFF) * alpha;
                        g += (src.get(at + 1) & 0xFF) * alpha;
                        b += (src.get(at + 2) & 0xFF) * alpha;
                        a += alpha;
                        area += weight;
                    }
                }
                int o = (y * outW + x) * 4;
                out.put(o, (byte) (a > 0 ? Math.round(r / a) : 0));
                out.put(o + 1, (byte) (a > 0 ? Math.round(g / a) : 0));
                out.put(o + 2, (byte) (a > 0 ? Math.round(b / a) : 0));
                out.put(o + 3, (byte) (area > 0 ? Math.round(a / area) : 0));
            }
        }
        out.limit(outW * outH * 4);
        return out;
    }

    /**
     * Warns when a trimmed slot left out a texel that is not transparent: the trim was planned from the image's
     * file before any GL ran, and this is the first time the texels the texture really holds can be asked. A miss
     * is a piece of art the atlas would not draw, so it is said, never silent; the slot is uploaded as planned.
     */
    private void checkTrim(Slot slot, ByteBuffer sheet, int paddedW) {
        int x1 = slot.srcX() + slot.w();
        int y1 = slot.srcY() + slot.h();
        for (int topDown = 0; topDown < slot.sheetH(); topDown++) {
            boolean insideRows = topDown >= slot.srcY() && topDown < y1;
            int rowBase = (slot.sheetH() - 1 - topDown) * paddedW * 4;
            for (int col = 0; col < slot.sheetW(); col++) {
                if (insideRows && col >= slot.srcX() && col < x1) continue;
                if (sheet.get(rowBase + col * 4 + 3) != 0) {
                    LOG.warn(label + ": " + slotName(slot) + " has art at " + col + "," + topDown
                            + " outside its trim " + slot.srcX() + "," + slot.srcY() + " " + slot.w() + "x"
                            + slot.h() + " of " + slot.sheetW() + "x" + slot.sheetH());
                    return;
                }
            }
        }
    }

    /**
     * A slot straight out of the read-back, by unpack state alone.
     *
     * <p>The read-back's rows run up from the content's bottom row, so the
     * sheet's top row is row {@code sheetH - 1} and a rectangle {@code y} down
     * from the top starts {@code sheetH - y - h} rows in.
     */
    private void uploadDirect(Slot slot, ByteBuffer sheet, int paddedW) {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, paddedW);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, slot.srcX());
        glPixelStorei(GL_UNPACK_SKIP_ROWS, slot.sheetH() - slot.srcY() - slot.h());
        GlErrors.clear();
        // A slot's y is stated the way a sheet's source rectangle is — down
        // from the top — because that is the coordinate every caller adds its
        // own srcY to. GL counts rows up from the bottom, so this is the one
        // place the two meet. Getting it wrong is not subtle in the arithmetic
        // and is entirely invisible in the code: every quad still draws real
        // art, just somebody else's.
        glTexSubImage2D(GL_TEXTURE_2D, 0, slot.x(), height - slot.y() - slot.h(),
                slot.w(), slot.h(), GL_RGBA, GL_UNSIGNED_BYTE, sheet);
    }

    /**
     * A slot with its apron, assembled on the CPU: every texel of the enlarged
     * rectangle is read from the sheet at that position clamped into the
     * sheet's content — what {@code GL_CLAMP_TO_EDGE} answers for a tap past
     * the sheet's edge, and what the sheet itself holds everywhere else.
     */
    private void uploadWithApron(Slot slot, ByteBuffer sheet, int paddedW) {
        int a = slot.apron();
        int w = slot.w() + 2 * a;
        int h = slot.h() + 2 * a;
        ByteBuffer out = BufferUtils.createByteBuffer(w * h * 4);
        int maxX = slot.sheetW() - 1;
        int maxRow = slot.sheetH() - 1;
        for (int row = 0; row < h; row++) {
            // Row 0 of the upload is the slot's bottom row, top-down row h - 1.
            int topDown = slot.srcY() - a + (h - 1 - row);
            int sourceRow = maxRow - Math.max(0, Math.min(maxRow, topDown));
            int rowBase = sourceRow * paddedW * 4;
            for (int col = 0; col < w; col++) {
                int at = rowBase + Math.max(0, Math.min(maxX, slot.srcX() - a + col)) * 4;
                out.put(sheet.get(at)).put(sheet.get(at + 1))
                        .put(sheet.get(at + 2)).put(sheet.get(at + 3));
            }
        }
        out.flip();
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        GlErrors.clear();
        glTexSubImage2D(GL_TEXTURE_2D, 0, slot.x() - a, height - (slot.y() - a) - h,
                w, h, GL_RGBA, GL_UNSIGNED_BYTE, out);
        Reference.reachabilityFence(out);   // LWJGL 2 keeps only the address
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
        deleteRetired();
        texture = 0;
        usedHeight = 0;
        pendingUploads.clear();
        pendingPlacements.clear();
        missed.clear();
        missedSize.clear();
        judged.clear();
        layoutToken = new Object();
        built = false;
        planned = false;
        broken = false;
        width = 0;
        height = 0;
        slots.clear();
        placementBySheet.clear();
        sheetIndex.clear();
        regions.reset(0);
        excluded.clear();
        diagnostic = null;
        refusal = null;
        reported = false;
    }

    /**
     * Region origins by packed rectangle: open addressing over {@code long}
     * keys, so the lookup a collect makes per quad allocates nothing.
     */
    private static final class RegionTable {
        private long[] keys = new long[0];
        private int[] values = new int[0];
        private boolean[] used = new boolean[0];

        /** Sheet number in 8 bits, then x, y, width and height in 14 each. */
        static long key(int sheet, int x, int y, int w, int h) {
            return ((long) (sheet & 0xFF) << 56) | ((long) (x & 0x3FFF) << 42)
                    | ((long) (y & 0x3FFF) << 28) | ((long) (w & 0x3FFF) << 14) | (h & 0x3FFF);
        }

        void reset(int expected) {
            int capacity = Integer.highestOneBit(Math.max(4, expected * 2) - 1) << 1;
            keys = new long[capacity];
            values = new int[capacity];
            used = new boolean[capacity];
        }

        void put(long key, int value) {
            int mask = keys.length - 1;
            int at = hash(key) & mask;
            while (used[at] && keys[at] != key) at = (at + 1) & mask;
            used[at] = true;
            keys[at] = key;
            values[at] = value;
        }

        int get(long key) {
            if (keys.length == 0) return -1;
            int mask = keys.length - 1;
            int at = hash(key) & mask;
            while (used[at]) {
                if (keys[at] == key) return values[at];
                at = (at + 1) & mask;
            }
            return -1;
        }

        private static int hash(long key) {
            long mixed = key * 0x9E3779B97F4A7C15L;
            return (int) (mixed ^ (mixed >>> 32));
        }
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
