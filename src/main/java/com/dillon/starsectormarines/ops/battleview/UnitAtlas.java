package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.ArrayList;
import java.util.List;

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
 * nobody adds to the seed: the only way out is over the cap, and that is
 * reported. An additive draw of a held image is a quad of this sheet too,
 * drained as its own run beside the normal one, so a shield screen's
 * replayed silhouettes join the batch rather than each binding its body.
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

    /**
     * Takes in every image the {@link DrawList} noted missing since the last
     * call, as {@link #extend} does; each is sized by the host that loaded it
     * ({@link BattleSprites#loadedImage}), because a sprite's own size is
     * whatever the last draw set it to. Call before the
     * collect. {@link Growth#RELAID} means the texture's size changed and its
     * batch must be built again.
     */
    public Growth grow() {
        List<SpriteAPI> images = takeMissed();
        if (images.isEmpty()) return Growth.NONE;
        List<Source> additions = new ArrayList<>();
        for (SpriteAPI image : images) {
            ModTextures.Loaded loaded = loadedImage(image);
            if (loaded == null) {
                exclude(Exclusion.NO_SIZE, "image its host cannot size");
                continue;
            }
            consider(additions, image, loaded.width(), loaded.height(), loaded.path());
        }
        return extend(additions);
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
        return plan(candidates, 4);
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

    /** {@link #offer}, recording whatever it turns away. */
    private void consider(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH, String name) {
        Exclusion refused = offer(into, sprite, recordedW, recordedH, name);
        if (refused != null) exclude(refused, (name == null ? "image" : name) + " " + recordedW + "x" + recordedH);
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
        if (sprite == null) return null;
        if (recordedW <= 0 || recordedH <= 0) return Exclusion.NO_SIZE;
        // A whole-image slot holds the texels the texture really has: a fitted image's slot is its
        // shipped size (texture-fit.md), so the cap and the atlas both see the art as it ships.
        int texW = ModTextures.shippedWidth(sprite, recordedW);
        int texH = ModTextures.shippedHeight(sprite, recordedH);
        if (texW > MAX_IMAGE_PX || texH > MAX_IMAGE_PX) return Exclusion.OVER_CAP;
        into.add(new Source(sprite, texW, texH, name));
        return null;
    }
}
