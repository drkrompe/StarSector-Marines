package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridor;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.turret.MapTurret;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;

import java.util.Collections;
import java.util.List;

/**
 * The product of a {@link MapGenerator}. Every implementation — legacy
 * striped-grid urban, BSP-based city, future wilderness/spacehulk gens —
 * returns this exact shape so {@link com.dillon.starsectormarines.battle.setup.BattleSetup}
 * can swap implementations behind a single {@link MapGenerator} reference.
 *
 * <p>Invariants the generator must establish before returning (audited by
 * downstream consumers like the renderer, AI, zone graph):
 * <ul>
 *   <li>Spawn anchor cells are walkable on the grid.</li>
 *   <li>Doorway cells are walkable AND flagged on the grid; both perpendicular
 *       cells across the doorway are also walkable (so a portal connects two
 *       zones).</li>
 *   <li>Each {@link PointOfInterest} rect lies fully in the grid, the
 *       perimeter is non-walkable (closed building shell), at least one
 *       interior cell is walkable when {@code hollow}, the anchor cell is
 *       walkable AND outside the rect.</li>
 *   <li>Cover is baked on every walkable cell (cardinal-wall count) and any
 *       walls left after generation are flagged {@link CellTopology.Tag#WALL}.</li>
 *   <li>Authored shared-edge barriers join two walkable cells, own a closed
 *       reciprocal cardinal transition, and publish cover from their profile.</li>
 *   <li>Doodads sit on walkable cells, never on doorways.</li>
 * </ul>
 */
public final class MapResult {

    /** Applied once after map props register, before forces spawn; null means an intact base. */
    public final DamagePlan initialDamage;
    public final NavigationGrid grid;
    public final CellTopology topology;
    public final int marineSpawnX;
    public final int marineSpawnY;
    public final int defenderSpawnX;
    public final int defenderSpawnY;
    public final List<PointOfInterest> pointsOfInterest;
    public final List<Doodad> doodads;
    /** Authored shuttle berths, ordered deterministically by filler dispatch. */
    public final List<LandingPad> landingPads;
    /** Authored pair-capable arrival areas, ordered deterministically by map generation. */
    public final List<LandingArea> landingAreas;
    /**
     * Authored machine berths inside vehicle bays, ordered deterministically by
     * the fitting that cut them. Empty for generators with no vehicle bay.
     *
     * <p>Generation authors the berth; the host decides what stands in it, so
     * these cells are left clear rather than stamped. See {@link Gantry}.
     */
    public final List<Gantry> gantries;
    /**
     * Authored work points inside fitted compartments, ordered deterministically
     * by the fittings that emitted them. Empty for generators whose rooms are
     * not fitted out.
     *
     * <p>Read by ambient life to decide where idle crew have business, and by a
     * facility to count what it can still do — a task point is the capacity, so
     * the room and the number cannot disagree. See {@link FixtureTask}.
     */
    public final List<FixtureTask> fixtureTasks;
    /**
     * Authored airstrips. Empty for a map with nothing that rolls — most of
     * them — and never null.
     *
     * <p>Published by the lot that laid the strip rather than recovered from
     * the painted ground, so the centreline a sortie rolls along is the middle
     * of the surface that was made. See {@link Runway}.
     */
    public final List<Runway> runways;
    /**
     * Aircraft shelters — the hangar bay an aircraft is kept and worked on in,
     * and taxis out of under its own power. Never null; empty on a map with no
     * airbase. Kept apart from {@link #gantries}, which are vehicle-bay berths.
     */
    public final List<Gantry> shelters;
    /**
     * Authored tactical hint graph the battle AI uses for squad allocation and
     * fallback routing. Never null — generators with no tactical layer return
     * an empty {@link TacticalMap}. See {@link TacticalMap} for the queries
     * available to {@link com.dillon.starsectormarines.battle.setup.BattleSetup}.
     */
    public final TacticalMap tacticalMap;
    /**
     * Building registry — closed INDOOR/TILE regions found by the flood-fill
     * pass after stamping. Drives the roof-render and fog-of-war visibility
     * systems. Never null; generators that don't run the flood-fill (or that
     * gen maps without buildings) return {@link Buildings#EMPTY}.
     */
    public final Buildings buildings;
    /**
     * Manned turret emplacements placed by
     * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}.
     * {@link com.dillon.starsectormarines.battle.setup.BattleSetup} reads this list to
     * spawn {@link MapTurret} units at each
     * post's turret cells and to wire the {@code GUARDPOST_PATROL} squads'
     * turret-dead release condition. Empty for non-conquest generators and for
     * legacy callers that don't go through the conquest pipeline.
     */
    public final List<DefensePost> defensePosts;
    /**
     * Vehicle-navigation skeleton extracted from the BSP / trunk road mask.
     * Convoys path edge-to-edge along this graph; tests and dev-overlays
     * read it to visualize street centerlines. Generators with no road
     * network (wilderness / spacehulk gens) return {@link RoadGraph#EMPTY}.
     */
    public final RoadGraph roadGraph;
    /**
     * The one road across this map a vehicle is guaranteed to be able to
     * drive, reserved before anything was built on it and honored by every
     * stamp that followed. Where {@link #roadGraph} is a centerline skeleton
     * for choosing among places, this is a width contract: the band a ground
     * reinforcement can actually get a hull down, from the defender's rear map
     * edge to the city. Null for every family that has no traversal axis, and
     * on a conquest map generated with the corridor switched off.
     */
    public final VehicleCorridor vehicleCorridor;
    /**
     * Conquest biome-band overlay ({@code reinforcement-nouns.md}) —
     * the slice a cell falls in (BEACH/PORT/CITY/FORTRESS_DISTRICT/OUTSKIRTS),
     * read by {@link com.dillon.starsectormarines.battle.command.reinforcement.RecaptureTargetService}
     * to bucket recapture targets. Null for generators with no biome layer
     * (legacy district mode, non-conquest missions).
     */
    public final BiomeMap biomeMap;
    /**
     * How far each cell is from the thing the battle is about — the front the
     * defender reinforcement layer walks rear-to-front, read by
     * {@link com.dillon.starsectormarines.battle.command.reinforcement.RecaptureTargetService}
     * to bucket recapture targets and by
     * {@link com.dillon.starsectormarines.battle.command.reinforcement.FrontLineReinforcementTrigger}
     * to place a rally behind the line. Stated by both the stock conquest
     * recipe and the precinct recipe; null for a map with no objective to be at
     * depth zero from, which is the gate on installing that layer at all.
     */
    public final FrontDepth frontDepth;
    /**
     * Where each lane of resistance runs — its places from the beachhead to the
     * objective, and the road route through them. Empty on every map that laid
     * no lanes, which is every mission but Conquest, and never null.
     *
     * <p>The seam a commander's lane chain reads. Ownership along the chain is
     * what a Conquest is measured in, and staging on the road to the next link
     * needs a road somebody wrote down; re-deriving one at battle time would be
     * a second answer to a question generation had already settled. See
     * {@code precincts.md}.
     */
    public final List<LaneRoute> lanes;
    /**
     * The ground an open-field landing promised to keep clear, or {@code null}
     * on every map whose landing promised none. Carried so the promise can be
     * checked against the finished map, and kept by whatever stamps it after
     * generation (parked vehicles stay off it).
     */
    public final FieldOfFire fieldOfFire;
    /**
     * What the plan's mission layout laid, by piece name ({@code mission-layout.md});
     * {@link LaidLayout#EMPTY} on every map whose plan stated none.
     */
    public final LaidLayout layout;
    /**
     * Where set pieces mean a hauler to stand while it is loaded ({@code set-piece-blocks.md}); empty on
     * a map with none, and never null.
     */
    public final List<LoadingBay> loadingBays;
    /**
     * The service lanes factory rows laid ({@code set-piece-blocks.md}): each lane's two gates and centre
     * line; empty on a map with none, and never null.
     */
    public final List<ServiceLane> serviceLanes;

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, new TacticalMap(Collections.emptyList()),
                Buildings.EMPTY, Collections.emptyList(), RoadGraph.EMPTY, Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, Buildings.EMPTY, Collections.emptyList(), RoadGraph.EMPTY,
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, Collections.emptyList(), RoadGraph.EMPTY,
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, RoadGraph.EMPTY,
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, null);
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     BiomeMap biomeMap) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, Collections.emptyList(), biomeMap);
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, landingAreas, biomeMap, Collections.emptyList(),
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap,
                     List<Gantry> gantries,
                     List<FixtureTask> fixtureTasks) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, landingAreas, biomeMap, gantries, fixtureTasks,
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap,
                     List<Gantry> gantries,
                     List<FixtureTask> fixtureTasks,
                     List<Runway> runways) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, landingAreas, biomeMap, gantries, fixtureTasks, runways,
                Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap,
                     List<Gantry> gantries,
                     List<FixtureTask> fixtureTasks,
                     List<Runway> runways,
                     List<Gantry> shelters) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, landingAreas, biomeMap, gantries, fixtureTasks, runways,
                shelters, null);
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap,
                     List<Gantry> gantries,
                     List<FixtureTask> fixtureTasks,
                     List<Runway> runways,
                     List<Gantry> shelters,
                     VehicleCorridor vehicleCorridor) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, landingAreas, biomeMap, gantries, fixtureTasks, runways,
                shelters, vehicleCorridor, null);
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap,
                     List<Gantry> gantries,
                     List<FixtureTask> fixtureTasks,
                     List<Runway> runways,
                     List<Gantry> shelters,
                     VehicleCorridor vehicleCorridor,
                     FrontDepth frontDepth) {
        this(grid, topology, marineSpawnX, marineSpawnY, defenderSpawnX, defenderSpawnY,
                pointsOfInterest, doodads, tacticalMap, buildings, defensePosts, roadGraph,
                landingPads, landingAreas, biomeMap, gantries, fixtureTasks, runways,
                shelters, vehicleCorridor, frontDepth, Collections.emptyList());
    }

    public MapResult(NavigationGrid grid, CellTopology topology,
                     int marineSpawnX, int marineSpawnY,
                     int defenderSpawnX, int defenderSpawnY,
                     List<PointOfInterest> pointsOfInterest,
                     List<Doodad> doodads,
                     TacticalMap tacticalMap,
                     Buildings buildings,
                     List<DefensePost> defensePosts,
                     RoadGraph roadGraph,
                     List<LandingPad> landingPads,
                     List<LandingArea> landingAreas,
                     BiomeMap biomeMap,
                     List<Gantry> gantries,
                     List<FixtureTask> fixtureTasks,
                     List<Runway> runways,
                     List<Gantry> shelters,
                     VehicleCorridor vehicleCorridor,
                     FrontDepth frontDepth,
                     List<LaneRoute> lanes) {
        this.initialDamage = null;
        this.grid = grid;
        this.topology = topology;
        this.marineSpawnX = marineSpawnX;
        this.marineSpawnY = marineSpawnY;
        this.defenderSpawnX = defenderSpawnX;
        this.defenderSpawnY = defenderSpawnY;
        this.pointsOfInterest = pointsOfInterest;
        this.doodads = doodads;
        this.landingPads = landingPads == null ? Collections.emptyList() : landingPads;
        this.landingAreas = landingAreas == null
                ? Collections.emptyList() : List.copyOf(landingAreas);
        this.tacticalMap = tacticalMap;
        this.buildings = buildings;
        this.defensePosts = defensePosts;
        this.roadGraph = roadGraph;
        this.vehicleCorridor = vehicleCorridor;
        this.biomeMap = biomeMap;
        this.frontDepth = frontDepth;
        this.gantries = gantries == null ? Collections.emptyList() : List.copyOf(gantries);
        this.fixtureTasks = fixtureTasks == null
                ? Collections.emptyList() : List.copyOf(fixtureTasks);
        this.runways = runways == null ? Collections.emptyList() : List.copyOf(runways);
        this.shelters = shelters == null ? Collections.emptyList() : List.copyOf(shelters);
        this.lanes = lanes == null ? Collections.emptyList() : List.copyOf(lanes);
        this.fieldOfFire = null;
        this.layout = LaidLayout.EMPTY;
        this.loadingBays = List.of();
        this.serviceLanes = List.of();
    }

    /** Keeps recipe output intact until the shared map-to-battle assembly consumes the layer. */
    public MapResult withInitialDamage(DamagePlan damage) {
        return new MapResult(this, damage, fieldOfFire, layout, loadingBays, serviceLanes);
    }

    /** The same map, carrying the field of fire its landing promised. */
    public MapResult withFieldOfFire(FieldOfFire field) {
        return new MapResult(this, initialDamage, field, layout, loadingBays, serviceLanes);
    }

    /** The same map, carrying what its mission layout laid. */
    public MapResult withLayout(LaidLayout laid) {
        return new MapResult(this, initialDamage, fieldOfFire, laid == null ? LaidLayout.EMPTY : laid, loadingBays,
                serviceLanes);
    }

    /** The same map, carrying the loading bays its set pieces laid. */
    public MapResult withLoadingBays(List<LoadingBay> bays) {
        return new MapResult(this, initialDamage, fieldOfFire, layout, bays == null ? List.of() : List.copyOf(bays),
                serviceLanes);
    }

    /** The same map, carrying the service lanes its factory rows laid. */
    public MapResult withServiceLanes(List<ServiceLane> laid) {
        return new MapResult(this, initialDamage, fieldOfFire, layout, loadingBays,
                laid == null ? List.of() : List.copyOf(laid));
    }

    private MapResult(MapResult base, DamagePlan damage, FieldOfFire field, LaidLayout laid,
                      List<LoadingBay> bays, List<ServiceLane> laidLanes) {
        if (damage != null && (damage.width() > base.grid.getWidth() || damage.height() > base.grid.getHeight()))
            throw new IllegalArgumentException("Damage extent exceeds map");
        initialDamage = damage;
        grid = base.grid;
        topology = base.topology;
        marineSpawnX = base.marineSpawnX;
        marineSpawnY = base.marineSpawnY;
        defenderSpawnX = base.defenderSpawnX;
        defenderSpawnY = base.defenderSpawnY;
        pointsOfInterest = base.pointsOfInterest;
        doodads = base.doodads;
        landingPads = base.landingPads;
        landingAreas = base.landingAreas;
        gantries = base.gantries;
        fixtureTasks = base.fixtureTasks;
        runways = base.runways;
        shelters = base.shelters;
        tacticalMap = base.tacticalMap;
        buildings = base.buildings;
        defensePosts = base.defensePosts;
        roadGraph = base.roadGraph;
        vehicleCorridor = base.vehicleCorridor;
        biomeMap = base.biomeMap;
        frontDepth = base.frontDepth;
        lanes = base.lanes;
        fieldOfFire = field;
        layout = laid;
        loadingBays = bays;
        serviceLanes = laidLanes;
    }
}
