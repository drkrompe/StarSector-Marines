package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.EnumMap;

/**
 * Render-side flyweight: the <em>static, type-shared</em> appearance + capability
 * tags for a {@link UnitType}, resolved once at class-load from the sim type. One
 * descriptor is shared by every entity of a type — entities never store render
 * fields, and {@code Entity}/{@code UnitType} never gain {@code SpriteAPI} (the
 * render tier's hard boundary). This is the same flyweight relationship
 * {@link BattleSprites} already has with {@code UnitType}, promoted to carry the
 * capability tags and sprite kind that the ordered unit strata consume instead of an
 * {@code instanceof}/{@code combatant}/{@code deathPoseIdx} ladder.
 *
 * <p><b>Scope.</b> This carries only what is genuinely type-flyweight. Dynamic
 * inputs (hp, renderX/Y, facing, recoil) live in the SoA registry / on the
 * subclass; per-<em>kind</em> geometry that varies within a type (a map turret's
 * {@code visualCells} plus weapon sprite, keyed by stable structure id) is
 * resolved at sweep time from the instance, not stored here. The footprint pad
 * color (ROAD_FILL) is identical for every footprint-drawer, so it lives with the
 * footprint emit helper rather than per-descriptor.
 *
 * <p>{@link UnitRenderService} consumes these tags through its ordered unit
 * strata. See {@code battle-render-nouns.md}.
 */
public final class RenderAppearance {

    /**
     * How an entity's body is drawn — selects which sweep claims it and which
     * draw command it emits.
     */
    public enum SpriteKind {
        /** Frame-sheet sub-rect (live infantry + dead poses) → facing-indexed {@code SHEET_QUAD}. */
        SHEET,
        /** Whole-texture rotated sprite (map turrets + drone hubs) → {@code SPRITE}. */
        WHOLE_SPRITE,
        /** Not drawn by the UNITS system — drones live in the DRONES layer ({@link DroneRenderSystem}). */
        NONE
    }

    /** Selects the body sweep + command kind. */
    public final SpriteKind spriteKind;
    /** Emits a ground footprint pad under the body (map turrets + drone hubs). */
    public final boolean drawsFootprint;
    /** Gets a durability bar in the last (layer-wide top) sweep — combatants, excluding drones (they bar themselves in the DRONES layer). */
    public final boolean drawsDurabilityBar;
    /** Withholds that bar until the entity has actually taken fire, so an untouched emplacement reads as scenery. */
    public final boolean barsOnlyWhenUnderFire;
    /** Has a corpse sheet, so a {@code deathPoseIdx >= 0} entity is drawn in the dead-sprite sweep. */
    public final boolean hasDeathPose;
    /** Facing→frame convention for the sprite sweep. Meaningful only when {@link #spriteKind} is {@link SpriteKind#SHEET}. */
    public final UnitType.FrameLayout frameLayout;
    /** Multiplier on the per-cell sprite size (mirrors {@link UnitType#renderScale}). */
    public final float renderScale;

    private RenderAppearance(SpriteKind spriteKind, boolean drawsFootprint, boolean drawsDurabilityBar,
                             boolean barsOnlyWhenUnderFire, boolean hasDeathPose,
                             UnitType.FrameLayout frameLayout, float renderScale) {
        this.spriteKind = spriteKind;
        this.drawsFootprint = drawsFootprint;
        this.drawsDurabilityBar = drawsDurabilityBar;
        this.barsOnlyWhenUnderFire = barsOnlyWhenUnderFire;
        this.hasDeathPose = hasDeathPose;
        this.frameLayout = frameLayout;
        this.renderScale = renderScale;
    }

    private static final EnumMap<UnitType, RenderAppearance> TABLE = build();

    /** The shared descriptor for a unit type. Never null — every {@link UnitType} has an entry. */
    public static RenderAppearance of(UnitType type) {
        return TABLE.get(type);
    }

    private static EnumMap<UnitType, RenderAppearance> build() {
        EnumMap<UnitType, RenderAppearance> m = new EnumMap<>(UnitType.class);
        for (UnitType t : UnitType.values()) {
            m.put(t, derive(t));
        }
        return m;
    }

    /**
     * Derives the tags from the sim type. The sheet-drawn classification now
     * lives on the sim {@link UnitType#drawnAsSheet()} (so the sim tier can
     * gate {@code SPRITE} archetype membership without importing this
     * render-tier class) — this method just defers to it. {@code
     * TURRET}/{@code DRONE_HUB_STRUCTURE} are the whole-sprite footprint-drawers;
     * {@code DRONE} renders in its own layer. Sheet-drawn is asked first, so a
     * body that stands still and does name a sheet — a machine part-built in a
     * gantry — draws as one rather than falling into the footprint case.
     * Durability bars follow {@code combatant} (drones excluded), and a corpse sweep
     * needs only a dead sheet to exist.
     */
    private static RenderAppearance derive(UnitType t) {
        SpriteKind kind;
        boolean footprint;
        if (t.drawnAsSheet()) {
            kind = SpriteKind.SHEET;
            footprint = false;
        } else if (t == UnitType.RANGE_TARGET) {
            kind = SpriteKind.NONE;
            footprint = false;
        } else if (t.isStatic()) {
            kind = SpriteKind.WHOLE_SPRITE;
            footprint = true;
        } else {
            // The remaining cases are DRONE and GROUND_VEHICLE, each drawn by
            // its own domain pass rather than by the unit sprite pipeline.
            kind = SpriteKind.NONE;
            footprint = false;
        }
        boolean hpBar = t.combatant && t != UnitType.DRONE
                && t != UnitType.RANGE_TARGET;
        boolean deathPose = t.deadSpritePath != null;
        return new RenderAppearance(kind, footprint, hpBar, t.isTurret(), deathPose,
                t.frameLayout, t.renderScale);
    }
}
