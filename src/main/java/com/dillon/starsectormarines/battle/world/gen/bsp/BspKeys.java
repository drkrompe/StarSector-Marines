package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.world.gen.GenKey;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.Buildings;

import java.util.List;

/**
 * {@link GenKey} declarations for the optional / domain overlays the BSP city
 * generator threads through {@link com.dillon.starsectormarines.battle.world.gen.GenContext}.
 * Each field IS the key's identity — read with {@code ctx.get(BspKeys.BIOME_MAP)}.
 *
 * <p>These are specific to the BSP city pipeline (conquest + legacy district
 * modes). A different generator — station, ship interior — declares its own
 * key holder; the generic {@code GenContext} never needs to know about either.
 */
public final class BspKeys {

    private BspKeys() {}

    /** Conquest zoning overlay (beach → port → city → fortress bands). Null in legacy mode. */
    public static final GenKey<BiomeMap> BIOME_MAP = GenKey.of("biomeMap");

    /** Legacy uniform-scatter zoning overlay. Null in conquest mode. */
    public static final GenKey<DistrictMap> DISTRICT_MAP = GenKey.of("districtMap");

    /** Traversal axis for biome banding + attacker-facing orientation. Set only in conquest mode. */
    public static final GenKey<TraversalAxis> AXIS = GenKey.of("axis");

    /** The painted trunk + BSP-frame road mask; compound fillers read it to find bridged inter-leaf cells. */
    public static final GenKey<boolean[][]> ROAD_CELLS = GenKey.of("roadCells");

    /** Cell mask of every road-graph node/edge cell; stampers must skip these to keep centerlines drivable. */
    public static final GenKey<boolean[][]> ROAD_RESERVATION = GenKey.of("roadReservation");

    /** Vehicle-navigation skeleton extracted from the road mask. Flows into {@code MapResult}. */
    public static final GenKey<RoadGraph> ROAD_GRAPH = GenKey.of("roadGraph");

    /** Claimed multi-leaf compounds; perimeter stampers read it for exclusion masks. */
    public static final GenKey<List<Compound>> COMPOUNDS = GenKey.of("compounds");

    // --- pipeline intermediates (produced by one stage, consumed by later ones) ---

    /** Trunk skeleton (arterials + sub-rects + intersection). Produced by the trunk stage, read by BSP partition / zoning / pedestrian / road-graph stages. */
    public static final GenKey<TrunkPlan.Plan> TRUNK_PLAN = GenKey.of("trunkPlan");

    /** BSP leaf partition over the trunk sub-rects. Produced by the partition stage, read by label / seed / claim / fill / pedestrian stages. */
    public static final GenKey<Bsp.Partition> PARTITION = GenKey.of("partition");

    /** Linked tactical-node graph. Produced by the tactical-link stage, flows into {@code MapResult}. */
    public static final GenKey<TacticalMap> TACTICAL_MAP = GenKey.of("tacticalMap");

    /** Flood-filled building registry. Produced by the finalize stage, flows into {@code MapResult}. */
    public static final GenKey<Buildings> BUILDINGS = GenKey.of("buildings");

    /** Tactical-region segmentation (structural taxonomy). Produced after finalize; preview/analysis artifact, not yet in {@code MapResult}. */
    public static final GenKey<TacticalRegionMap> TACTICAL_REGIONS = GenKey.of("tacticalRegions");

    /** Marine spawn cell {@code [x, y]}. Produced by the spawn stage. */
    public static final GenKey<int[]> MARINE_SPAWN = GenKey.of("marineSpawn");

    /** Defender spawn cell {@code [x, y]}. Produced by the spawn stage. */
    public static final GenKey<int[]> DEFENDER_SPAWN = GenKey.of("defenderSpawn");
}
