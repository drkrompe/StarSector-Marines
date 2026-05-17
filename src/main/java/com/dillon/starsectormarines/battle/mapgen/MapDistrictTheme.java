package com.dillon.starsectormarines.battle.mapgen;

import java.util.Random;

/**
 * Coarse-grained map district label. Each district covers a chunk of the map
 * (~20×20 nav cells) and carries one theme that biases the BSP leaf labeler
 * toward certain {@link BlockKind}s. Switching from a global weight table
 * (the v1 BspCityGenerator behavior) to a per-district table is what makes
 * the output read as <em>"residential neighborhood next to an industrial
 * belt next to a waterfront"</em> rather than <em>"hodgepodge of stamped
 * rectangles"</em>.
 *
 * <p>Themes are mutually exclusive at the district level. Adjacency
 * constraints — e.g., WATERFRONT only on map-edge districts — are enforced
 * by the {@code DistrictMap} that assigns themes, not by the theme itself.
 *
 * <p>Each theme's weight table is set in the static initializer below so
 * the {@link WeightedTable.Builder} can reference fully-constructed
 * {@link BlockKind} values. Weights are integer "shares" — only their
 * proportions matter.
 */
public enum MapDistrictTheme {

    /** Dense housing — parks and small civic spots. Few industrial fixtures. */
    RESIDENTIAL,

    /** Factories, yards, fortified posts. Heavy outdoor cover and few softscape elements. */
    INDUSTRIAL,

    /** High-traffic urban core — shops, plazas, landing zones, parks. Light cover. */
    CIVIC,

    /** Balanced — older legacy-style mix. Used when no other theme fits cleanly. */
    MIXED,

    /** Coastal — water dominates, with sand + commercial/industrial shore. Only assigned to map-edge districts. */
    WATERFRONT,

    /** Sparse city edges — wasteland, parks, scattered houses, occasional fortified position. */
    OUTSKIRTS;

    private WeightedTable<BlockKind> table;

    static {
        RESIDENTIAL.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.BUILDING_RESIDENTIAL, 42)
                .add(BlockKind.PARK,                 18)
                .add(BlockKind.DENSE_BLOCK,          12)  // tenements / row houses
                .add(BlockKind.PLAZA,                 8)
                .add(BlockKind.BUILDING_COMMERCIAL,   7)
                .add(BlockKind.WASTELAND_RUBBLE,      4)
                .add(BlockKind.GATED_HOUSING,         3)  // claim-pass seed: walled subdivision
                .add(BlockKind.BUILDING_INDUSTRIAL,   2)
                .add(BlockKind.INDUSTRIAL_YARD,       2)
                .add(BlockKind.FORTIFIED_POST,        2)
                .build();

        INDUSTRIAL.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.BUILDING_INDUSTRIAL,  38)
                .add(BlockKind.INDUSTRIAL_YARD,      28)
                .add(BlockKind.WASTELAND_RUBBLE,     10)
                .add(BlockKind.FORTIFIED_POST,        8)
                .add(BlockKind.BUILDING_COMMERCIAL,   5)
                .add(BlockKind.BUILDING_RESIDENTIAL,  5)
                .add(BlockKind.MILITARY_BASE,         4)  // seed roll; claim pass grows it
                .add(BlockKind.PLAZA,                 2)
                .add(BlockKind.PARK,                  2)
                .build();

        CIVIC.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.BUILDING_COMMERCIAL,  22)
                .add(BlockKind.PLAZA,                20)
                .add(BlockKind.PARK,                 14)
                .add(BlockKind.BUILDING_RESIDENTIAL, 13)
                .add(BlockKind.DENSE_BLOCK,          10)  // downtown bazaar / shop alleys
                .add(BlockKind.LANDING_ZONE,          7)
                .add(BlockKind.DENSE_QUARTER,         5)  // downtown skyscraper district
                .add(BlockKind.FORTIFIED_POST,        4)
                .add(BlockKind.BUILDING_INDUSTRIAL,   2)
                .add(BlockKind.WASTELAND_RUBBLE,      3)
                .build();

        MIXED.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.BUILDING_RESIDENTIAL, 20)
                .add(BlockKind.BUILDING_COMMERCIAL,  14)
                .add(BlockKind.BUILDING_INDUSTRIAL,  10)
                .add(BlockKind.PLAZA,                10)
                .add(BlockKind.PARK,                  9)
                .add(BlockKind.DENSE_BLOCK,           8)  // occasional dense pocket
                .add(BlockKind.INDUSTRIAL_YARD,       8)
                .add(BlockKind.WASTELAND_RUBBLE,      7)
                .add(BlockKind.FORTIFIED_POST,        4)
                .add(BlockKind.GATED_HOUSING,         2)  // walled subdivision in mixed zones
                .add(BlockKind.DENSE_QUARTER,         2)  // small downtown pocket
                .add(BlockKind.MILITARY_BASE,         2)  // rare base in mixed zones
                .add(BlockKind.LANDING_ZONE,          2)
                // No WATERFRONT in MIXED — water in the middle of the map looks wrong.
                .build();

        WATERFRONT.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.WATERFRONT,           45)
                .add(BlockKind.PARK,                 12)
                .add(BlockKind.BUILDING_COMMERCIAL,  12)
                .add(BlockKind.INDUSTRIAL_YARD,      10)
                .add(BlockKind.BUILDING_RESIDENTIAL,  8)
                .add(BlockKind.PLAZA,                 6)
                .add(BlockKind.BUILDING_INDUSTRIAL,   4)
                .add(BlockKind.WASTELAND_RUBBLE,      3)
                .build();

        OUTSKIRTS.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.WASTELAND_RUBBLE,     25)
                .add(BlockKind.PARK,                 22)
                .add(BlockKind.BUILDING_RESIDENTIAL, 15)
                .add(BlockKind.INDUSTRIAL_YARD,      12)
                .add(BlockKind.BUILDING_INDUSTRIAL,   8)
                .add(BlockKind.FORTIFIED_POST,        7)
                .add(BlockKind.BUILDING_COMMERCIAL,   5)
                .add(BlockKind.PLAZA,                 5)
                .add(BlockKind.MILITARY_BASE,         3)  // outskirts garrison
                .build();
    }

    /** Sample a {@link BlockKind} for a leaf inside a district with this theme. */
    public BlockKind pickBlockKind(Random rng) {
        return table.pick(rng);
    }
}
