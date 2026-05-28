package com.dillon.starsectormarines.battle.world.gen;

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
    OUTSKIRTS,

    /** Beach landing zone — open scrub / wasteland with sparse cover and bunkers; pairs with the BEACH biome's SAND ground overlay. */
    COASTAL_BEACH,

    /** Harbor / docks — warehouses, industrial yards, fortified harbor batteries. Used for the PORT biome inland of the beach. */
    HARBOR_PORT,

    /** Defender stronghold — heavy fortified posts, depots, motor pools, military bases. Used for the FORTRESS_DISTRICT biome at the far end of the traversal axis. */
    MILITARY_FORT;

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
                .add(BlockKind.BUILDING_RESIDENTIAL, 18)
                .add(BlockKind.BUILDING_COMMERCIAL,  13)
                .add(BlockKind.BUILDING_INDUSTRIAL,  10)
                .add(BlockKind.PLAZA,                10)
                .add(BlockKind.PARK,                  8)
                .add(BlockKind.DENSE_BLOCK,           8)  // occasional dense pocket
                .add(BlockKind.INDUSTRIAL_YARD,       7)
                .add(BlockKind.WASTELAND_RUBBLE,      6)
                .add(BlockKind.NATURE_GRASSLAND,      4)  // green pocket between blocks
                .add(BlockKind.FORTIFIED_POST,        4)
                .add(BlockKind.GATED_HOUSING,         2)  // walled subdivision in mixed zones
                .add(BlockKind.DENSE_QUARTER,         2)  // small downtown pocket
                .add(BlockKind.MILITARY_BASE,         2)  // rare base in mixed zones
                .add(BlockKind.LANDING_ZONE,          2)
                // No WATERFRONT / NATURE_WETLAND in MIXED — interior water looks wrong.
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
                .add(BlockKind.WASTELAND_RUBBLE,     20)
                .add(BlockKind.NATURE_GRASSLAND,     16)  // untamed meadow at the city edge
                .add(BlockKind.PARK,                 16)
                .add(BlockKind.BUILDING_RESIDENTIAL, 12)
                .add(BlockKind.INDUSTRIAL_YARD,      10)
                .add(BlockKind.NATURE_WETLAND,        7)  // marshy edge plots
                .add(BlockKind.BUILDING_INDUSTRIAL,   6)
                .add(BlockKind.FORTIFIED_POST,        5)
                .add(BlockKind.BUILDING_COMMERCIAL,   3)
                .add(BlockKind.PLAZA,                 3)
                .add(BlockKind.MILITARY_BASE,         2)  // outskirts garrison
                .build();

        // Beach biome — wide-open landing terrain. NATURE_BEACH dominates so
        // the beach reads as varied sand + scattered cover rocks rather than
        // the flat sand strip the v1 BLANKET_SAND override produced over
        // PARK/WASTELAND_RUBBLE leaves. FORTIFIED_POST scattered for beach
        // bunkers; WATERFRONT only fires when a leaf actually touches the
        // map edge (the labeler can't tell, so we accept occasional misfires).
        // PARK / WASTELAND_RUBBLE retained at low weight as variety; the
        // biome-wide SAND override still re-paints their ground if they
        // sneak in.
        COASTAL_BEACH.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.NATURE_BEACH,         50)
                .add(BlockKind.FORTIFIED_POST,       18)
                .add(BlockKind.WATERFRONT,           12)  // map-edge cells only — fallback if interior
                .add(BlockKind.WASTELAND_RUBBLE,      8)
                .add(BlockKind.PARK,                  4)
                .add(BlockKind.INDUSTRIAL_YARD,       3)
                .add(BlockKind.BUILDING_COMMERCIAL,   3)  // beach kiosk / lifeguard
                .add(BlockKind.BUILDING_RESIDENTIAL,  2)  // beach hut
                .build();

        // Port / harbor — warehouses, yards, commercial offices, the
        // occasional harbor battery. No WATERFRONT here in v1 (it expects to
        // sit on the map edge); commit-2 work will revisit interior water if
        // we want piers cutting into the port.
        HARBOR_PORT.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.BUILDING_INDUSTRIAL,  26)
                .add(BlockKind.INDUSTRIAL_YARD,      20)
                .add(BlockKind.BUILDING_COMMERCIAL,  13)
                .add(BlockKind.WASTELAND_RUBBLE,      9)
                .add(BlockKind.FORTIFIED_POST,        8)
                .add(BlockKind.NATURE_WETLAND,        6)  // brackish marsh between piers
                .add(BlockKind.BUILDING_RESIDENTIAL,  6)
                .add(BlockKind.PLAZA,                 5)
                .add(BlockKind.DENSE_BLOCK,           5)  // dockside tenements
                .add(BlockKind.NATURE_BEACH,          2)  // sandy spit beside the docks
                .build();

        // Military fortress district — heavy fortified posts, depots, motor
        // pools. MILITARY_BASE seeds the claim pass; LANDING_ZONE makes
        // airfields / heli pads (the airbase the user wants for defender
        // fighters re-arming). Almost no civilian buildings.
        MILITARY_FORT.table = WeightedTable.<BlockKind>builder()
                .add(BlockKind.FORTIFIED_POST,       32)
                .add(BlockKind.INDUSTRIAL_YARD,      18)
                .add(BlockKind.MILITARY_BASE,        14)  // claim-pass seed
                .add(BlockKind.BUILDING_INDUSTRIAL,  12)
                .add(BlockKind.WASTELAND_RUBBLE,      8)
                .add(BlockKind.LANDING_ZONE,          7)  // airfield / heli pad
                .add(BlockKind.BUILDING_RESIDENTIAL,  5)  // barracks
                .add(BlockKind.PLAZA,                 4)  // parade ground
                .build();
    }

    /** Sample a {@link BlockKind} for a leaf inside a district with this theme. */
    public BlockKind pickBlockKind(Random rng) {
        return table.pick(rng);
    }
}
