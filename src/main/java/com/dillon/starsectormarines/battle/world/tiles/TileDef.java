package com.dillon.starsectormarines.battle.world.tiles;

import java.util.List;

/**
 * One tile's authoritative definition, loaded from a {@code *.tileset.json} into
 * the {@link TileRegistry} and addressed by its stable string {@link #id}. This
 * is the data half of the moddable-tilesets split — it replaces the per-tile
 * semantics the former {@code NatureTile} / {@code UrbanTile3} enums hardcoded (layer,
 * cover, passability, overlay legality), so a submod can extend the catalog by
 * dropping JSON rather than recompiling.
 *
 * <p>See {@code roadmap/moddable-tilesets/overview.md} for the schema and the
 * data-vs-algorithm seam (tiles are pure data; gen carving stays in code).
 *
 * <p>Sliced sheets pin a {@link #frame} (the slicer's left-to-right ordinal —
 * what the former {@code NatureTile.frameIndex()} derived from enum order). Grid
 * autotile <em>blocks</em> (origin + named layout) arrive in Phase 1c and will
 * leave {@code frame == -1}.
 */
public final class TileDef {

    public final String id;
    public final String sheetPath;
    /**
     * Dense registry index assigned at ingest order — the opaque tile handle a
     * cell stores (as {@code index + 1}, with 0 reading as "none") once the
     * {@code NatureTile} ordinal it replaced is gone. Stable within one load;
     * never persisted ([[battle_transient_no_save_load]] — battles don't save,
     * so the index need not survive a reload).
     */
    public final int index;
    /** Slicer frame index for sliced sheets; {@code -1} for block-based grid tiles (Phase 1c). */
    public final int frame;
    public final TileLayer layer;
    public final TileCover cover;
    public final boolean passable;
    /**
     * Overlay-placement selectors (empty for ground/unconstrained tiles). Each
     * entry is one of: {@code "<id>"} (base tile id must equal it),
     * {@code "layer:ground"} (base tile's layer must match), or {@code "!<id>"}
     * (exclude that base tile id — exclusion wins). Evaluated by
     * {@link #canOverlayOn}.
     */
    public final List<String> validOn;

    public TileDef(String id, String sheetPath, int index, int frame, TileLayer layer,
                   TileCover cover, boolean passable, List<String> validOn) {
        this.id = id;
        this.sheetPath = sheetPath;
        this.index = index;
        this.frame = frame;
        this.layer = layer;
        this.cover = cover;
        this.passable = passable;
        this.validOn = validOn == null ? List.of() : List.copyOf(validOn);
    }

    public boolean isGround() { return layer.isGround(); }

    /**
     * Whether this overlay tile may be drawn on top of {@code base}. Faithful
     * port of the former {@code NatureTile.canOverlay}: valid when at least one
     * positive selector in {@link #validOn} matches {@code base} AND no
     * exclusion selector matches it. Ground tiles (empty {@code validOn}) never
     * overlay anything.
     */
    public boolean canOverlayOn(TileDef base) {
        if (base == null) return false;
        boolean positive = false;
        for (String sel : validOn) {
            if (sel.startsWith("!")) continue;
            if (sel.startsWith("layer:")) {
                if (base.layer.name().equalsIgnoreCase(sel.substring("layer:".length()))) positive = true;
            } else if (sel.equals(base.id)) {
                positive = true;
            }
        }
        if (!positive) return false;
        for (String sel : validOn) {
            if (sel.startsWith("!") && sel.substring(1).equals(base.id)) return false;
        }
        return true;
    }
}
