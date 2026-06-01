package com.dillon.starsectormarines.battle.world.gen.taxonomy;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * The tactical category of a {@link TacticalRegion} — a coarse grouping of the
 * fine-grained {@link GroundKind} the fillers paint, chosen so two cells share
 * a region only when they read as the same kind of <em>space</em> to a unit
 * standing in it. The fillers already make the distinction (a plaza is BRICK, a
 * park is GRASS); this enum just names it as a first-class structural fact the
 * generator publishes.
 *
 * <p>Deliberately coarse: {@code BRICK}, {@code STONE}, {@code STRIPED}, and
 * {@code LZ_MARKER} all read as "open paved ground" tactically, so they collapse
 * to {@link #PLAZA}; the texture differences that matter (cover, exposure,
 * enclosure) are captured as <em>attributes</em> on the region, not as separate
 * kinds. Width-based reads like "alley vs. street" are likewise attributes
 * ({@code exposure} / {@code coverDensity}), not kinds.
 */
public enum RegionKind {
    /** Public outdoor pavement — roads + curb-side sidewalks. Transit space; exposed, low cover. */
    STREET,
    /** Open paved/hardscape ground — plaza centers, monuments, factory aprons, LZ pads. Exposed gathering space. */
    PLAZA,
    /** Private interior pavement inside a super-block — semi-enclosed by definition. */
    COURTYARD,
    /** Natural open ground — grass, dirt, sand, snow. Parks, lawns, unpaved yards, shore. */
    OPEN_GROUND,
    /** Wasteland rubble — knocked-down walls + damaged floor. Cover-dense, broken terrain. */
    RUBBLE,
    /** Carved building interior — indoor floors reachable through a doorway. The city's closest thing to a station "room". */
    BUILDING_INTERIOR,
    /** Anything not otherwise mapped — a safety net so a new {@link GroundKind} never silently vanishes from the segmentation. */
    OTHER;

    /**
     * Map a per-cell {@link GroundKind} to its tactical region category.
     * {@link GroundKind#WATER} has no mapping — water is never walkable, so it
     * is excluded from segmentation upstream and never reaches this method on a
     * walkable cell.
     */
    public static RegionKind fromGround(GroundKind g) {
        switch (g) {
            case STREET:
            case SIDEWALK:
                return STREET;
            case BRICK:
            case STONE:
            case STRIPED:
            case LZ_MARKER:
                return PLAZA;
            case COURTYARD:
                return COURTYARD;
            case GRASS:
            case DIRT:
            case SAND:
            case SNOW:
                return OPEN_GROUND;
            case RUBBLE:
                return RUBBLE;
            case INDOOR:
            case TILE:
                return BUILDING_INTERIOR;
            default:
                return OTHER;
        }
    }
}
