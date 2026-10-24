package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every image a unit's body is composed from, composited into one GL texture so
 * a field of bodies is a handful of draws rather than one per layer.
 *
 * <p><b>Why.</b> {@code renderEvidence} on the canonical 560x336 Conquest, with
 * the ground already resident and atlased: the close and mid framings' largest
 * layer is {@code UNITS}, and its shape is 230 <em>whole sprites</em> leaving as
 * 232 draws across 230 texture binds. Nothing there can coalesce, because a
 * whole sprite is a foreign call that binds its own texture — and a marine is
 * not one sprite but seven, since the authored composition
 * ({@code unit-layer-layouts.appearance.json}) draws his feet, body, weapon,
 * head and muzzle flash from separate PNGs. Thirty marines is two hundred binds
 * for thirty bodies.
 *
 * <p><b>What goes in.</b> Every whole image drawn in {@code UNITS} or
 * {@code UNIT_SHADOWS} that is no larger than {@link #MAX_IMAGE_PX}, by
 * whichever path it is drawn: a normal or additive sprite, a part of an image,
 * a mesh, or a quad cut from a small sheet. The rule is the draw, not a list:
 * {@link #plan} seeds the first frame from what the host has loaded (the
 * modular infantry families and every mech livery, the map turrets and their
 * recoiling barrels, the drone hub, the airframes that stand on a hardstand),
 * and an image the seed does not name is noted by the {@link DrawList} the
 * first time it misses and taken in by {@link #grow} before the next collect,
 * into rows the plan reserved for it. That is what closed the turret collars
 * and the downed poses falling out of the batch, and it closes the next image
 * nobody adds to the seed. An additive draw of a held image is a quad of this sheet too,
 * drained as its own run beside the normal one, so a shield screen's
 * replayed silhouettes join the batch rather than each binding its body.
 *
 * <p><b>An image over the cap joins at the size it is drawn.</b> A downed pose
 * is 360x520 and a mother bore 736 across, but on a 560x336 map a downed body
 * is never more than 75 pixels tall on screen, even at the closest zoom: the
 * texels past that are never seen, and holding them whole is what the cap is
 * there to refuse. So an image larger than {@link #MAX_IMAGE_PX} that is drawn
 * in these layers takes a slot sized to the largest it can appear — the size
 * its draw gave it, taken to the camera's closest zoom — filtered down once
 * when it is uploaded ({@link SpriteAtlas.Source#imageW}), as long as that is
 * within {@link #MAX_COPY_PX}. Drawn at the closest zoom it is one texel to a
 * pixel, as close as the whole image got; drawn farther out it is minified
 * from a filtered copy rather than from texels a bilinear tap skips. The rule
 * is the draw again, not a list: whatever is too big to hold and small on
 * screen joins, and only what is big on screen as well stays on its own
 * texture, reported as over the cap.
 *
 * <p><b>The redirect is one place.</b> {@link DrawList#addSprite} resolves a
 * sprite to its slot and emits a rotated sheet quad instead, so no composer
 * knows the atlas exists and none of them can disagree with another about
 * whether to use it. Painter order is untouched: the same commands in the same
 * order, and the drain still breaks a run wherever a different sheet or a solid
 * fill interrupts it.
 *
 * <p>{@link SpriteAtlas} owns the mechanism. What is here is which images go in.
 */
public final class UnitAtlas extends SpriteAtlas {

    /**
     * Widths the layout may use, smallest first.
     *
     * <p>Unlike the terrain sheets, this set grows with the art: a mech livery
     * is sixteen more images and there is one per faction. Offering the packer a
     * ladder rather than one width means a project that adds a livery gets a
     * taller atlas rather than a silent fall-back to two hundred binds, and a
     * project that ships fewer does not pay for a texture it does not fill. Today
     * the whole modular set — every infantry family and all twelve liveries —
     * packs at 2048.
     */
    private static final int[] SIDES = {1024, 2048, 4096};

    /**
     * The longest side an image may have and still be worth a slot.
     *
     * <p>An atlas is worth its texels where <em>many</em> of its images are on
     * screen at once, and that is what a composed body is: a marine is seven
     * small PNGs and a field is thirty marines. An aircraft hull is neither —
     * it is a vanilla ship sprite, up to twelve hundred texels on a side, and a
     * field holds a handful of them parked. Left in, the hulls alone asked for
     * fifty-six million texels and the whole layout was refused, which took
     * every marine on the map back to being his own texture bind. Anything over
     * this keeps drawing as a whole sprite, which costs one bind for one body
     * rather than one bind for one layer of one.
     *
     * <p>Three hundred and twenty is comfortably above the largest thing that
     * does belong — a mech chassis at 208 — and below the smallest hull.
     */
    private static final int MAX_IMAGE_PX = 320;

    /**
     * The longest side a filtered copy of an image over {@link #MAX_IMAGE_PX} may take.
     *
     * <p>A copy holds only texels the screen can show, so what bounds it is not whether its texels earn their place
     * but how many such slots the reserved rows hold: at 512 a copy is at most a sixty-fourth of the 4096 atlas. It
     * admits every hive structure and the spitter's death sheet on a 400x240 map, where a mother bore reaches 442
     * pixels at the closest zoom, and a downed pose on any map at all.
     */
    static final int MAX_COPY_PX = 512;

    /**
     * Transparent texels kept around an image's art when it is held trimmed: one, the reach of a bilinear tap, so
     * every tap that can colour a pixel reads the image's own texels.
     */
    private static final int TRIM_BORDER_PX = 1;

    /**
     * Two texels between slots where {@link SpriteAtlas#GUTTER_PX} is eight. Nothing samples this texture further
     * than bilinear's one texel past a slot, since every quad drawn from it is a plain textured quad with no
     * mipmaps and no offset taps, and eight a side was about a ninth of the packed area.
     */
    private static final int GUTTER = 2;

    /** The host the plan was seeded from: what sizes an image the atlas only saw drawn. */
    private BattleSprites host;

    public UnitAtlas() {
        super("unit atlas", SIDES);
    }

    @Override
    public ModTextures.Loaded loadedImage(SpriteAPI image) {
        return host != null ? host.loadedImage(image) : ModTextures.loaded(image);
    }

    /** The layers whose whole images this atlas takes in on sight. */
    @Override
    public boolean gathers(RenderLayer layer) {
        return layer == RenderLayer.UNITS || layer == RenderLayer.UNIT_SHADOWS;
    }

    /**
     * A quarter of the side, kept free below the seeded layout for images
     * first drawn mid-battle: at 4096 that is a thousand rows, room for every
     * turret look and any small image the seed forgot several times over, and
     * each one placed there is a single upload rather than a rebuild of the
     * atlas on the frame it is first drawn. The texture is then rounded up to a
     * power-of-two height (see {@link SpriteAtlas#growthHeadroom}).
     */
    @Override
    protected int growthHeadroom(int side) {
        return side / 4;
    }

    @Override
    protected int gutterPx() {
        return GUTTER;
    }

    /**
     * Takes in every image the {@link DrawList} noted missing since the last
     * call, as {@link #extend} does; each is sized by the host that loaded it
     * ({@link BattleSprites#loadedImage}), because a sprite's own size is
     * whatever the last draw set it to. Call before the
     * collect. {@link Growth#RELAID} means the texture's size changed and its
     * batch must be built again.
     */
    public Growth grow() {
        List<Missed> images = takeMissed();
        if (images.isEmpty()) return Growth.NONE;
        List<Source> additions = new ArrayList<>();
        for (Missed missed : images) {
            ModTextures.Loaded loaded = loadedImage(missed.image());
            if (loaded == null) {
                exclude(Exclusion.NO_SIZE, "image its host cannot size");
                continue;
            }
            String name = loaded.path();
            Exclusion refused = offer(additions, missed.image(), loaded.width(), loaded.height(),
                    missed.closestW(), missed.closestH(), name);
            if (refused != null) {
                exclude(refused, (name == null ? "image" : name) + " " + loaded.width() + "x" + loaded.height()
                        + (refused == Exclusion.OVER_CAP && missed.closestW() > 0f
                                ? String.format(Locale.ROOT, " (drawn up to %.0fx%.0f)",
                                        missed.closestW(), missed.closestH())
                                : ""));
            }
        }
        return extend(trimmed(additions));
    }

    /**
     * Seeds the layout from the unit images currently loaded.
     *
     * <p>Called at the same lifecycle seam that registers every other sheet's
     * batch, after the host's {@code ensure*} loads. The seed only saves the
     * first frames a rebuild: an image it leaves out, or one loaded later, joins
     * through {@link #grow} the first time it is drawn.
     *
     * @return whether a layout exists at all
     */
    public boolean plan(BattleSprites sprites) {
        if (sprites == null) return isPlanned();
        host = sprites;
        List<Source> candidates = new ArrayList<>();
        for (LayeredUnitAssets family : sprites.layeredUnitSprites().values()) {
            if (family != null) addLayers(candidates, family.layers());
        }
        for (LayeredMechAssets livery : sprites.layeredMechLiveries().values()) {
            if (livery != null) addLayers(candidates, livery.layers());
        }
        RavRunnerAssets ravRunner = sprites.ravRunnerSprites();
        if (ravRunner != null) addLayers(candidates, ravRunner.layers());
        RavBruteAssets ravBrute = sprites.ravBruteSprites();
        if (ravBrute != null) addLayers(candidates, ravBrute.layers());
        RavMatriarchAssets ravMatriarch = sprites.ravMatriarchSprites();
        if (ravMatriarch != null) addLayers(candidates, ravMatriarch.layers());
        addLayers(candidates, sprites.bloodPools());
        LayeredMechAssets baseMech = sprites.layeredMechSprites();
        if (baseMech != null) addLayers(candidates, baseMech.layers());
        addHulls(candidates, sprites.turretSprites().values(), "turret");
        addHulls(candidates, sprites.turretRecoilSprites().values(), "turret recoil");
        addHull(candidates, sprites.droneHubSprite(), "drone hub");
        addHulls(candidates, sprites.airframeSprites().values(), "airframe");
        // The soft disc every body without its own shape casts on the ground:
        // UNIT_SHADOWS draws one per body, and out of the atlas each is a bind.
        consider(candidates, sprites.shadowBlobSprite(),
                sprites.shadowBlobPxW(), sprites.shadowBlobPxH(), "shadow blob");
        // Worn plate is drawn as cells of one sheet over every damaged mech part (combat-durability-nouns.md): in here,
        // a mark joins its part's run instead of breaking it twice. Over the cap, but one small sheet that every
        // damaged body samples is what the cap's own reasoning lets in.
        ShuttleSpriteCache scuffs = sprites.spriteByPath(VehicleScuffs.SHEET);
        if (scuffs != null && scuffs.pxW > 0 && scuffs.pxH > 0) {
            into(candidates, scuffs.sprite, scuffs.pxW, scuffs.pxH, "scuff sheet");
        }
        // Two images is already two binds and an atlas of them saves one; the
        // point of the layout is a field of bodies, so a host that loaded almost
        // no unit art stays on the sprite path rather than paying for a copy.
        return plan(trimmed(candidates), 4);
    }

    /**
     * Each source held only as far as its art reaches: its alpha bounds ({@link BattleSprites#alphaBounds}) taken
     * into slot texels, rounded outward, and one texel more on every side, so the transparent texels a bilinear tap
     * at the art's edge reads are the image's own. A body PNG is drawn on a canvas sized for every pose, and the
     * margins around a station arm or a shadow are most of its texels. A source the host cannot bound is held whole.
     *
     * <p><b>A side is trimmed only while the two sides across from it hold no art at the image's edge.</b> Trimming
     * a side moves the ends of the two edges that meet it, and an edge drawn between other ends is rasterized along
     * a line a fraction of a subpixel away: a pixel whose centre sits on that line can fall the other side of it.
     * Along an edge in a transparent border such a pixel reads nothing and the change cannot be seen; along an edge
     * the art reaches, it is a pixel the sprite path left out drawn faintly, or one it drew left out. So a rifle whose
     * barrel runs the image's full height keeps its side columns, and a station arm with margin all round loses all
     * four.
     */
    private List<Source> trimmed(List<Source> sources) {
        if (host == null) return sources;
        List<Source> out = new ArrayList<>(sources.size());
        for (Source source : sources) out.add(trimmed(source));
        return out;
    }

    private Source trimmed(Source source) {
        ModTextures.Loaded loaded = loadedImage(source.sheet());
        int[] bounds = host.alphaBounds(source.sheet());
        if (loaded == null || bounds == null || loaded.width() <= 0 || loaded.height() <= 0) return source;
        int[] held = trimRect(source.contentW(), source.contentH(), loaded.width(), loaded.height(), bounds);
        return source.trimmedTo(held[0], held[1], held[2], held[3]);
    }

    /**
     * The part {@code {x, y, w, h}} of a {@code w x h} slot to hold, for an image of {@code imageW x imageH} logical
     * pixels whose art is {@code bounds} ({@link BattleSprites#alphaBounds}); all of it when nothing can go.
     */
    static int[] trimRect(int w, int h, int imageW, int imageH, int[] bounds) {
        // The art's bounds in slot texels, rounded outward: a copy filtered down covers every texel a bound touches.
        int artX0 = (int) Math.floor(bounds[0] * (w / (double) imageW));
        int artY0 = (int) Math.floor(bounds[1] * (h / (double) imageH));
        int artX1 = Math.min(w, (int) Math.ceil(bounds[2] * (w / (double) imageW)));
        int artY1 = Math.min(h, (int) Math.ceil(bounds[3] * (h / (double) imageH)));
        boolean topAndBottomClear = artY0 > 0 && artY1 < h;
        boolean leftAndRightClear = artX0 > 0 && artX1 < w;
        int x0 = topAndBottomClear ? Math.max(0, artX0 - TRIM_BORDER_PX) : 0;
        int x1 = topAndBottomClear ? Math.min(w, artX1 + TRIM_BORDER_PX) : w;
        int y0 = leftAndRightClear ? Math.max(0, artY0 - TRIM_BORDER_PX) : 0;
        int y1 = leftAndRightClear ? Math.min(h, artY1 + TRIM_BORDER_PX) : h;
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    private void addLayers(List<Source> into, List<LayeredSpriteCache> layers) {
        if (layers == null) return;
        for (LayeredSpriteCache layer : layers) {
            if (layer == null) continue;
            consider(into, layer.sprite, layer.pxWidth, layer.pxHeight, layer.sourcePath);
        }
    }

    private void addHulls(List<Source> into, Iterable<ShuttleSpriteCache> caches, String kind) {
        for (ShuttleSpriteCache cache : caches) addHull(into, cache, kind);
    }

    private void addHull(List<Source> into, ShuttleSpriteCache cache, String kind) {
        if (cache == null) return;
        consider(into, cache.sprite, cache.pxW, cache.pxH, kind + " " + cache.pxW + "x" + cache.pxH);
    }

    /**
     * {@link #offer}, recording whatever it turns away, except an image over the cap: the seed cannot know how big
     * such an image is drawn, so it is judged when it is ({@link #grow}), and reported then if it is still too big.
     */
    private void consider(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH, String name) {
        Exclusion refused = offer(into, sprite, recordedW, recordedH, name);
        if (refused != null && refused != Exclusion.OVER_CAP) {
            exclude(refused, (name == null ? "image" : name) + " " + recordedW + "x" + recordedH);
        }
    }

    /**
     * Offers native image dimensions to the layout, unless the image is too large.
     *
     * <p>SpriteAPI's width and height are mutable display sizes: the first
     * whole-sprite frame resizes them, and the same caches survive battle
     * re-entry. Only the dimensions captured by the loader can size a texture
     * copy. An image without recorded dimensions stays on the sprite path.
     *
     * @return why the image was not offered, or null when it was (or there was none)
     */
    /** Offers a whole image the cap does not apply to, at its shipped size. */
    private static void into(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH, String name) {
        into.add(new Source(sprite, ModTextures.shippedWidth(sprite, recordedW),
                ModTextures.shippedHeight(sprite, recordedH), name));
    }

    static Exclusion offer(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH) {
        return offer(into, sprite, recordedW, recordedH, null);
    }

    static Exclusion offer(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH, String name) {
        return offer(into, sprite, recordedW, recordedH, Float.NaN, Float.NaN, name);
    }

    /**
     * As {@link #offer(List, SpriteAPI, int, int, String)}, for an image drawn at up to {@code closestW x closestH}
     * screen pixels at the closest zoom ({@code NaN} when unknown): one over {@link #MAX_IMAGE_PX} is offered as a
     * copy at that size, scaled evenly and never larger than the image, when the copy is within
     * {@link #MAX_COPY_PX}.
     */
    static Exclusion offer(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH,
                           float closestW, float closestH, String name) {
        if (sprite == null) return null;
        if (recordedW <= 0 || recordedH <= 0) return Exclusion.NO_SIZE;
        // A whole-image slot holds the texels the texture really has: a fitted image's slot is its
        // shipped size (texture-fit.md), so the cap and the atlas both see the art as it ships.
        int texW = ModTextures.shippedWidth(sprite, recordedW);
        int texH = ModTextures.shippedHeight(sprite, recordedH);
        if (texW <= MAX_IMAGE_PX && texH <= MAX_IMAGE_PX) {
            into.add(new Source(sprite, texW, texH, name));
            return null;
        }
        if (!(closestW > 0f) || !(closestH > 0f)) return Exclusion.OVER_CAP;
        // One scale for both axes, the larger the draw asks for, so the copy keeps the image's proportions and a
        // draw that stretches it is still met texel for pixel on its longer side.
        float scale = Math.min(1f, Math.max(closestW / texW, closestH / texH));
        int copyW = Math.max(1, (int) Math.ceil(texW * scale - 0.001f));
        int copyH = Math.max(1, (int) Math.ceil(texH * scale - 0.001f));
        if (copyW > MAX_COPY_PX || copyH > MAX_COPY_PX) return Exclusion.OVER_CAP;
        into.add(new Source(sprite, copyW, copyH, name, texW, texH));
        return null;
    }
}
