package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;

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
 * <p>{@link SpriteAtlas} owns the mechanism — the shelf pack, the read-back into
 * slots, the gutter, the fail-soft. What is here is which sheets go in.
 */
public final class GroundAtlas extends SpriteAtlas {

    /** Transparent texels between one sheet's content and the next. */
    static final int GUTTER_PX = SpriteAtlas.GUTTER_PX;

    /**
     * The widest atlas this will plan, before the driver's own maximum is
     * consulted.
     *
     * <p>A sheet wider or taller than this is left out ({@link #fitsAlone}),
     * and a cap the driver then refuses is a fail-soft rather than a crash.
     * 4096 since the nature strip grew to 3071 texels (2026-10-25): the
     * texture is only as tall as its shelves, about 4096x300, and the unit
     * atlas already needs a 4096 side.
     */
    static final int MAX_SIDE_PX = 4096;

    /** Widths tried in turn: the narrowest the sheets fit. */
    private static final int[] SIDES = {2048, MAX_SIDE_PX};

    public GroundAtlas() {
        super("ground atlas", SIDES);
    }

    /** Visible for the layout test; see {@link SpriteAtlas#shelfPack}. */
    static int[] shelfPack(int[] contentW, int[] contentH, int side) {
        return SpriteAtlas.shelfPack(contentW, contentH, side);
    }

    /**
     * Settles the layout from the terrain sheets currently loaded.
     *
     * <p>One sheet is already one bind, so an atlas of it would be a copy for
     * nothing, and none at all is a host with no terrain art.
     *
     * @return whether a layout exists at all; false leaves every ground quad on
     *         the per-sheet path
     */
    public boolean plan(BattleSprites sprites) {
        if (sprites == null) return isPlanned();
        List<Source> candidates = new ArrayList<>();
        offer(candidates, new Source(sprites.tileSheet(),
                sprites.tileSheetPxW(), sprites.tileSheetPxH(), "tile sheet"));
        offer(candidates, new Source(sprites.roadSheet(),
                sprites.roadSheetPxW(), sprites.roadSheetPxH(), "road sheet"));
        offer(candidates, new Source(sprites.floorsSheet(),
                sprites.floorsSheetPxW(), sprites.floorsSheetPxH(), "floors sheet"));
        offer(candidates, new Source(sprites.waterSheet(),
                sprites.waterSheetPxW(), sprites.waterSheetPxH(), "water sheet"));
        offer(candidates, new Source(sprites.urbanTile3Sheet(),
                sprites.urbanTile3SheetPxW(), sprites.urbanTile3SheetPxH(), "urban tile 3 sheet"));
        offer(candidates, new Source(sprites.natureSheet(),
                sprites.natureSheetPxW(), sprites.natureSheetPxH(), "nature sheet"));
        return plan(candidates, 2);
    }

    /**
     * Offers a sheet, or leaves one too big for any atlas this plans out, so it draws from its own texture
     * and the rest still share one. Offered, it failed the whole layout: the nature strip grew to 3071 texels
     * wide (2026-10-25), the atlas stopped serving, and with it the resident ground (The Burn whole-map,
     * 63 ms of GROUND a frame).
     */
    private void offer(List<Source> into, Source source) {
        if (source.sheet() != null && !fitsAlone(source.contentW(), source.contentH())) {
            exclude(Exclusion.OVER_CAP, source.name() + " " + source.contentW() + "x" + source.contentH());
            return;
        }
        into.add(source);
    }

    /** Whether a sheet this size, with a gutter on every side, fits the widest atlas this plans. */
    static boolean fitsAlone(int w, int h) {
        return w + 2 * GUTTER_PX <= MAX_SIDE_PX && h + 2 * GUTTER_PX <= MAX_SIDE_PX;
    }
}
