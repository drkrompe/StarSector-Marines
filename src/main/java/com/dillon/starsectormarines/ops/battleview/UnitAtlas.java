package com.dillon.starsectormarines.ops.battleview;

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
 * <p><b>What goes in.</b> The images that compose a unit: the modular infantry
 * families and every mech livery, the map turrets and their recoiling barrels,
 * the drone hub, and the airframes that stand on a hardstand. What stays out is
 * anything already drawn as a sub-rectangle of a shared sheet — the sprite-sheet
 * infantry, the corpse poses, the aim sheets — because those already coalesce,
 * and anything an atlas cannot express: an additive draw carries its own blend
 * and a batched quad does not.
 *
 * <p><b>The redirect is one place.</b> {@link DrawList#addSprite} resolves a
 * sprite to its slot and emits a rotated sheet quad instead, so no composer
 * knows the atlas exists and none of them can disagree with another about
 * whether to use it. Painter order is untouched: the same commands in the same
 * order, and the drain still breaks a run wherever a different sheet or a solid
 * fill interrupts it.
 *
 * <p>{@link SpriteAtlas} owns the mechanism. What is here is which images go in,
 * and the switch that takes them back out for a control run:
 * {@code -Dbattle.render.unitAtlas=false}.
 */
public final class UnitAtlas extends SpriteAtlas {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.unitAtlas";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

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

    public UnitAtlas() {
        super("unit atlas", ENABLED, SIDES);
    }

    /** Whether the atlas is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Settles the layout from the unit images currently loaded.
     *
     * <p>Called at the same lifecycle seam that registers every other sheet's
     * batch, after the host's {@code ensure*} loads. An image loaded later is
     * simply not in the atlas and keeps drawing as a whole sprite, which is the
     * behaviour this replaces rather than a failure.
     *
     * @return whether a layout exists at all
     */
    public boolean plan(BattleSprites sprites) {
        if (sprites == null) return isPlanned();
        List<Source> candidates = new ArrayList<>();
        for (LayeredUnitAssets family : sprites.layeredUnitSprites().values()) {
            if (family != null) addLayers(candidates, family.layers());
        }
        for (LayeredMechAssets livery : sprites.layeredMechLiveries().values()) {
            if (livery != null) addLayers(candidates, livery.layers());
        }
        LayeredMechAssets baseMech = sprites.layeredMechSprites();
        if (baseMech != null) addLayers(candidates, baseMech.layers());
        addHulls(candidates, sprites.turretSprites().values());
        addHulls(candidates, sprites.turretRecoilSprites().values());
        addHull(candidates, sprites.droneHubSprite());
        addHulls(candidates, sprites.airframeSprites().values());
        // Two images is already two binds and an atlas of them saves one; the
        // point of the layout is a field of bodies, so a host that loaded almost
        // no unit art stays on the sprite path rather than paying for a copy.
        return plan(candidates, 4);
    }

    private static void addLayers(List<Source> into, List<LayeredSpriteCache> layers) {
        if (layers == null) return;
        for (LayeredSpriteCache layer : layers) {
            if (layer == null) continue;
            offer(into, layer.sprite, layer.pxWidth, layer.pxHeight);
        }
    }

    private static void addHulls(List<Source> into, Iterable<ShuttleSpriteCache> caches) {
        for (ShuttleSpriteCache cache : caches) addHull(into, cache);
    }

    private static void addHull(List<Source> into, ShuttleSpriteCache cache) {
        if (cache == null) return;
        offer(into, cache.sprite, cache.pxW, cache.pxH);
    }

    /** Offers one image to the layout, unless it is too large to be worth a slot. */
    private static void offer(List<Source> into, SpriteAPI sprite, int recordedW, int recordedH) {
        if (sprite == null) return;
        int w = contentWidth(sprite, recordedW);
        int h = contentHeight(sprite, recordedH);
        if (w <= 0 || h <= 0) return;
        if (w > MAX_IMAGE_PX || h > MAX_IMAGE_PX) return;
        into.add(new Source(sprite, w, h));
    }

    /**
     * The image's own pixel width, preferring what the sprite itself reports.
     *
     * <p>A cache's recorded size is what the composers scale against and is
     * normally the same number, but a cache that never recorded one is a real
     * case ({@code ShuttleSpriteCache.pxW} can be zero), and a slot sized from a
     * zero copies nothing at all.
     */
    private static int contentWidth(SpriteAPI sprite, int recorded) {
        int reported = Math.round(sprite.getWidth());
        return reported > 0 ? reported : recorded;
    }

    private static int contentHeight(SpriteAPI sprite, int recorded) {
        int reported = Math.round(sprite.getHeight());
        return reported > 0 ? reported : recorded;
    }
}
