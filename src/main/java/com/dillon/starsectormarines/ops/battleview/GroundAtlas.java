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
 * slots, the gutter, the fail-soft. What is here is which sheets go in, and the
 * switch that takes them back out for a control run:
 * {@code -Dbattle.render.groundAtlas=false}.
 */
public final class GroundAtlas extends SpriteAtlas {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.groundAtlas";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Transparent texels between one sheet's content and the next. */
    static final int GUTTER_PX = SpriteAtlas.GUTTER_PX;

    /**
     * The widest atlas this will plan, before the driver's own maximum is
     * consulted.
     *
     * <p>Every ground sheet this mod ships is under two thousand texels wide,
     * and a cap the driver then refuses is a fail-soft rather than a crash.
     */
    static final int MAX_SIDE_PX = 2048;

    public GroundAtlas() {
        super("ground atlas", ENABLED, MAX_SIDE_PX);
    }

    /** Whether the atlas is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
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
        candidates.add(new Source(sprites.tileSheet(),
                sprites.tileSheetPxW(), sprites.tileSheetPxH()));
        candidates.add(new Source(sprites.roadSheet(),
                sprites.roadSheetPxW(), sprites.roadSheetPxH()));
        candidates.add(new Source(sprites.floorsSheet(),
                sprites.floorsSheetPxW(), sprites.floorsSheetPxH()));
        candidates.add(new Source(sprites.waterSheet(),
                sprites.waterSheetPxW(), sprites.waterSheetPxH()));
        candidates.add(new Source(sprites.urbanTile3Sheet(),
                sprites.urbanTile3SheetPxW(), sprites.urbanTile3SheetPxH()));
        candidates.add(new Source(sprites.natureSheet(),
                sprites.natureSheetPxW(), sprites.natureSheetPxH()));
        return plan(candidates, 2);
    }
}
