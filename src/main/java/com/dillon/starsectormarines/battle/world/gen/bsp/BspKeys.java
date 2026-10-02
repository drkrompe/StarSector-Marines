package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.world.gen.GenKey;
import com.dillon.starsectormarines.battle.world.gen.OpeningOperationMapPlan;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridor;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;

import java.util.List;
import java.util.Map;

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

    /**
     * How far each cell is from the thing the battle is about — the front as a
     * depth rather than as a biome. Bound by the closing front stage on the
     * recipes that have an objective to be at depth zero from; absent on a map
     * with no front at all.
     */
    public static final GenKey<FrontDepth> FRONT_DEPTH = GenKey.of("frontDepth");

    /** Legacy uniform-scatter zoning overlay. Null in conquest mode. */
    public static final GenKey<DistrictMap> DISTRICT_MAP = GenKey.of("districtMap");

    /** Traversal axis for biome banding + attacker-facing orientation. Set only in conquest mode. */
    public static final GenKey<TraversalAxis> AXIS = GenKey.of("axis");

    /** Campaign → battle read of the target world (planetary defenses, market size, …). Always bound; {@link TargetProfile#NEUTRAL} when no market backs the battle. */
    public static final GenKey<TargetProfile> MARKET_PROFILE = GenKey.of("marketProfile");

    /** The painted trunk + BSP-frame road mask; compound fillers read it to find bridged inter-leaf cells. */
    public static final GenKey<boolean[][]> ROAD_CELLS = GenKey.of("roadCells");

    /**
     * The one answer to "may I close this cell".
     *
     * <p>Seeded by the road graph with every node and edge cell, so centerlines
     * stay drivable — hence the name — but it has become the general
     * reservation. {@link #VEHICLE_CORRIDOR} widens it, and so does the ward's
     * airfield: a runway is no more closable than a road, and a stamper should
     * not have to know which kind of thing it is standing on.
     *
     * <p>A stage that widens it must run after every <em>filler</em> that reads
     * it, or it changes how the city was built rather than only what may be
     * stamped onto it.
     */
    public static final GenKey<boolean[][]> ROAD_RESERVATION = GenKey.of("roadReservation");

    /** Vehicle-navigation skeleton extracted from the road mask. Flows into {@code MapResult}. */
    public static final GenKey<RoadGraph> ROAD_GRAPH = GenKey.of("roadGraph");

    /**
     * The one road across the map a vehicle is guaranteed to be able to drive,
     * reserved before anything is built on it. Bound only on the conquest
     * recipe; its band is also unioned into {@link #ROAD_RESERVATION}, so a
     * stamper asking "may I close this cell" needs only that mask.
     */
    public static final GenKey<VehicleCorridor> VEHICLE_CORRIDOR = GenKey.of("vehicleCorridor");

    /** Claimed multi-leaf compounds; perimeter stampers read it for exclusion masks. */
    public static final GenKey<List<Compound>> COMPOUNDS = GenKey.of("compounds");

    /** Campaign-backed civilian spaceport campus reserved before generic compound claiming. Null for neutral and conquest maps. */
    public static final GenKey<Compound> SPACEPORT_DISTRICT = GenKey.of("spaceportDistrict");

    // --- pipeline intermediates (produced by one stage, consumed by later ones) ---

    /** Trunk skeleton (arterials + sub-rects + intersection). Produced by the trunk stage, read by BSP partition / zoning / pedestrian / road-graph stages. */
    public static final GenKey<TrunkPlan.Plan> TRUNK_PLAN = GenKey.of("trunkPlan");

    /** Open ground the road growth never reached, left unpartitioned. Absent when the stock trunk stage ran. */
    public static final GenKey<List<TrunkPlan.SubRect>> HINTERLAND = GenKey.of("hinterland");

    /** The places this map is made of, when it was grown from a precinct plan. */
    public static final GenKey<PrecinctPlan> PRECINCTS = GenKey.of("precincts");

    /** Per-call First Contract facility and staging reservations. */
    public static final GenKey<OpeningOperationMapPlan> OPENING_OPERATION_PLAN =
            GenKey.of("openingOperationPlan");

    /** The exact facility authored for a First Contract map. */
    public static final GenKey<PointOfInterest> OPENING_OPERATION_PLACE =
            GenKey.of("openingOperationPlace");

    /**
     * Per-cell precinct index, or {@code GrownTrunkPlan.UNOWNED} for open
     * country. What lets a later stage fill, wall or report on one place
     * without re-deriving where it is.
     */
    public static final GenKey<int[][]> PRECINCT_CLAIM = GenKey.of("precinctClaim");

    /**
     * Per-cell road ownership by precinct index. Kept beside the claim because
     * a place's circulation and its ground are different questions: the wall
     * goes round the claim, and the gates are where the road crosses it.
     */
    public static final GenKey<int[][]> PRECINCT_ROAD = GenKey.of("precinctRoad");

    /**
     * What each programmed precinct owed and could not fit, by precinct name.
     *
     * <p>Bound even when empty, so "nothing was short" and "nobody asked" are
     * different answers. A place that could not build its motor pool has to be
     * able to say so: an under-provisioned installation is otherwise
     * indistinguishable from a small one, which is the fault
     * {@code compound-programs.md} exists to remove.
     */
    public static final GenKey<Map<String, List<FortressBuilding>>> UNPLACED_PROGRAM =
            GenKey.of("unplacedProgram");

    /**
     * How many airfields each precinct owed and had no ground for, by name.
     *
     * <p>Separate from {@link #UNPLACED_PROGRAM} because an airfield is a lot
     * rather than a building, and counted rather than listed because they are
     * identical to each other — what a caller needs is how many are missing.
     */
    public static final GenKey<Map<String, Integer>> UNPLACED_AIRFIELDS =
            GenKey.of("unplacedAirfields");

    /**
     * How many emplacements of each kind a precinct's fortification asked for
     * and had no ground for, by name.
     *
     * <p>The same law as {@link #UNPLACED_PROGRAM}, applied to the dial that
     * actually decides how hard a place is to take. A citadel that found room
     * for two of its four heavy posts is a stronghold wearing a citadel's name,
     * and an emplacement that was never stamped leaves nothing on the map to
     * notice.
     */
    public static final GenKey<Map<String, Map<DefensePostKind, Integer>>> UNPLACED_DEFENCES =
            GenKey.of("unplacedDefences");

    /**
     * The lane places the plan could not seat, by name, bound even when empty.
     *
     * <p>The same law as {@link #UNPLACED_PROGRAM}, one level up again. A lane's
     * ladder is stated as a rung per front band; a map may have no room for one
     * of them, and a lane with two rungs on it is indistinguishable on a
     * finished map from a lane that was only ever asked for two. Named rather
     * than counted, because <em>which</em> rung is missing is the whole of the
     * question — a lane missing its deepest place is a track with nothing
     * standing in front of the fortress, and a lane missing its shallowest is a
     * force that lands unopposed.
     */
    public static final GenKey<List<String>> UNPLACED_LANE_PLACES =
            GenKey.of("unplacedLanePlaces");

    /**
     * Where each lane actually runs: its places in path order and the road
     * between them, read back off the finished map.
     *
     * <p>Bound by the lane-route stage on a map with lanes, and flows into
     * {@code MapResult.lanes}. Absent on every map that laid none, which is
     * every mission but Conquest.
     */
    public static final GenKey<List<LaneRoute>> LANES = GenKey.of("lanes");

    /** BSP leaf partition over the trunk sub-rects. Produced by the partition stage, read by label / seed / claim / fill / pedestrian stages. */
    public static final GenKey<Bsp.Partition> PARTITION = GenKey.of("partition");

    /** Station room/corridor connectivity graph. Produced by the station corridor stage; read by the station spawn stage + (future) placement passes. Null in city modes. */
    public static final GenKey<StationGraph> STATION_GRAPH = GenKey.of("stationGraph");

    /** Linked tactical-node graph. Produced by the tactical-link stage, flows into {@code MapResult}. */
    public static final GenKey<TacticalMap> TACTICAL_MAP = GenKey.of("tacticalMap");

    /** Flood-filled building registry. Produced by the finalize stage, flows into {@code MapResult}. */
    public static final GenKey<Buildings> BUILDINGS = GenKey.of("buildings");

    /** Tactical-region segmentation (structural taxonomy). Produced after finalize; preview/analysis artifact, not yet in {@code MapResult}. */
    public static final GenKey<TacticalRegionMap> TACTICAL_REGIONS = GenKey.of("tacticalRegions");

    /** Marine spawn cell {@code [x, y]}. Produced by the spawn stage. */
    /**
     * The packed fortress ward as {@code {left, top, right, bottom}}, published
     * by {@code FortressWardStage} for the wall that is drawn around it. Absent
     * on any map without a fortress band.
     */
    public static final GenKey<int[]> FORTRESS_WARD = GenKey.of("fortressWard");

    /**
     * How many paired arrival areas the landing stage seated, bound even when
     * that is zero.
     *
     * <p>Same law as {@link #UNPLACED_PROGRAM}: a region that seated none and a
     * map nobody asked to be landed on are different answers, and a beachhead
     * that was never authored leaves nothing on the finished map to notice
     * until the mission asks for its drop zones and throws.
     */
    public static final GenKey<Integer> LANDING_AREAS_AUTHORED =
            GenKey.of("landingAreasAuthored");

    /**
     * Whether the berths were seated on the landing precinct's own claim.
     *
     * <p>Bound only on a map whose plan states a landing place. A landing
     * precinct is seeded before growth, against an estimate of where the
     * objective's claim will end up, so it can in principle come out somewhere
     * the resolved approach region never reaches. The berths then fall back to
     * the region, which is a worse map rather than a broken one — and this is
     * what says so, on the same law as {@link #UNPLACED_PROGRAM}.
     */
    public static final GenKey<Boolean> LANDING_ON_ITS_PLACE =
            GenKey.of("landingOnItsPlace");

    /**
     * Cells of approach the attacker's resolved region actually got, along the
     * traversal axis, between its objective-facing side and the objective
     * claim's attacker-facing boundary.
     *
     * <p>Same law as {@link #UNPLACED_PROGRAM}. A mission states a
     * {@code Standoff} and a map may not be able to afford it — the band slides
     * as far as it can and this is how far that was — and a beachhead that
     * landed further out than the mission asked for is otherwise not
     * distinguishable from one that landed where it wanted.
     * {@link Integer#MAX_VALUE} where there was no claim to stand off from.
     */
    public static final GenKey<Integer> APPROACH_STANDOFF =
            GenKey.of("approachStandoff");

    public static final GenKey<int[]> MARINE_SPAWN = GenKey.of("marineSpawn");

    /** Defender spawn cell {@code [x, y]}. Produced by the spawn stage. */
    public static final GenKey<int[]> DEFENDER_SPAWN = GenKey.of("defenderSpawn");
}
