package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Semantic ordering of cells on {@code graphics/tilesets/nature-tiles.png} —
 * a single-row sheet with variable-width art separated by transparent gutters.
 * The PNG is fed through {@link SpriteSheetSlicer}; the resulting frame array
 * lines up index-for-index with this enum's declaration order.
 *
 * <p>Each tile renders inside one nav cell (1×1). Tiles fall into two layers:
 * <ul>
 *   <li>{@link Kind#GROUND} — the base surface drawn under everything (grass,
 *       dirt, sand, water).</li>
 *   <li>{@link Kind#PLANT_OVERLAY} / {@link Kind#ROCK_OVERLAY} — doodad-style
 *       props drawn on top of a ground tile (closer in spirit to
 *       {@link com.dillon.starsectormarines.battle.world.model.Doodad} than to a base
 *       autotile). Plants are only valid on grass; rocks are valid on any
 *       non-water surface. The placement layer reads
 *       {@link #canOverlay(NatureTile)} to decide where each overlay may go.</li>
 * </ul>
 *
 * <p>Variant pairs (e.g. {@link #GRASS_1} / {@link #GRASS_2}) exist so the
 * map filler can break up uniform tiling by picking from per-kind pools.
 */
public enum NatureTile {
    GRASS_1        (Kind.GROUND,         "grass",                       Cover.NONE,  true),
    GRASS_2        (Kind.GROUND,         "grass alt",                   Cover.NONE,  true),
    DIRT_1         (Kind.GROUND,         "dirt",                        Cover.NONE,  true),
    DIRT_2         (Kind.GROUND,         "dirt alt",                    Cover.NONE,  true),
    SAND           (Kind.GROUND,         "sand-ish",                    Cover.NONE,  true),
    WATER_1        (Kind.GROUND,         "water",                       Cover.NONE,  true),
    WATER_2        (Kind.GROUND,         "water alt",                   Cover.NONE,  true),
    SHRUB_1        (Kind.PLANT_OVERLAY,  "shrub",                       Cover.NONE,  true),
    SHRUB_2        (Kind.PLANT_OVERLAY,  "shrub alt",                   Cover.NONE,  true),
    GRASS_TUFT_1   (Kind.PLANT_OVERLAY,  "tuft of grass",               Cover.NONE,  true),
    GRASS_TUFT_2   (Kind.PLANT_OVERLAY,  "tuft of grass alt",           Cover.NONE,  true),
    SHRUB_3        (Kind.PLANT_OVERLAY,  "shrub variant",               Cover.NONE,  true),
    ROCKS_SMALL_1  (Kind.ROCK_OVERLAY,   "small rocks",                 Cover.NONE,  true),
    ROCKS_SMALL_2  (Kind.ROCK_OVERLAY,   "small rocks alt",             Cover.NONE,  true),
    ROCKS_SMALL_3  (Kind.ROCK_OVERLAY,   "small rocks alt",             Cover.NONE,  true),
    ROCK_MEDIUM_1  (Kind.ROCK_OVERLAY,   "medium rock (light cover)",   Cover.LIGHT, true),
    ROCK_MEDIUM_2  (Kind.ROCK_OVERLAY,   "medium rock (light cover)",   Cover.LIGHT, true),
    ROCK_LARGE_1   (Kind.ROCK_OVERLAY,   "large rock (impassable)",     Cover.NONE,  false),
    ROCK_LARGE_2   (Kind.ROCK_OVERLAY,   "large rock (impassable)",     Cover.NONE,  false),
    ROCK_LARGE_3   (Kind.ROCK_OVERLAY,   "large rock (impassable)",     Cover.NONE,  false);

    /** Render-layer + placement-rule bucket. Determines whether a tile is a base surface or a sprite drawn on top of one. */
    public enum Kind { GROUND, PLANT_OVERLAY, ROCK_OVERLAY }

    /** Gameplay cover bucket. {@link #LIGHT} reads as partial — fire-through with accuracy penalty later; {@link #NONE} is fully open. */
    public enum Cover { NONE, LIGHT }

    public final Kind kind;
    public final String label;
    public final Cover cover;
    public final boolean passable;

    NatureTile(Kind kind, String label, Cover cover, boolean passable) {
        this.kind = kind;
        this.label = label;
        this.cover = cover;
        this.passable = passable;
    }

    /** Frame index on the auto-sliced sheet. Position in the enum IS the position on the source PNG. */
    public int frameIndex() { return ordinal(); }

    public boolean isGround()        { return kind == Kind.GROUND; }
    public boolean isPlantOverlay()  { return kind == Kind.PLANT_OVERLAY; }
    public boolean isRockOverlay()   { return kind == Kind.ROCK_OVERLAY; }

    /**
     * Whether this overlay tile can be drawn on top of {@code base}. Ground
     * tiles return {@code false} — they're the base layer, not overlays.
     * <ul>
     *   <li>{@link Kind#PLANT_OVERLAY} → grass tiles only.</li>
     *   <li>{@link Kind#ROCK_OVERLAY} → any non-water ground.</li>
     * </ul>
     */
    public boolean canOverlay(NatureTile base) {
        if (base == null || base.kind != Kind.GROUND) return false;
        switch (kind) {
            case PLANT_OVERLAY: return base == GRASS_1 || base == GRASS_2;
            case ROCK_OVERLAY:  return base != WATER_1 && base != WATER_2;
            default:            return false; // GROUND can't be an overlay
        }
    }

    /** Indexed lookup by slicer frame index. Returns {@code null} for indices outside the declared range. */
    public static NatureTile byFrame(int index) {
        NatureTile[] all = values();
        if (index < 0 || index >= all.length) return null;
        return all[index];
    }
}
