package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.vehicle.MapVehicle;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.gen.UrbanMapGenerator;
import com.dillon.starsectormarines.battle.vehicle.VehicleKind;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapons.MarineLoadout;
import com.dillon.starsectormarines.battle.weapons.MarineSecondary;
import com.dillon.starsectormarines.battle.weapons.MarineWeapon;
import com.dillon.starsectormarines.battle.weapons.MechLoadoutState;
import com.dillon.starsectormarines.battle.weapons.MechRole;

import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.TurretMount;
import com.dillon.starsectormarines.battle.command.AssaultCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.compound.CompoundGarrisonSystem;
import com.dillon.starsectormarines.battle.command.SabotageCommand;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.vehicle.ConvoyPlanner;
import com.dillon.starsectormarines.battle.vehicle.HybridAStarPlanner;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.reinforcement.ConvoyMeans;
import com.dillon.starsectormarines.battle.reinforcement.GarrisonDepletedTrigger;
import com.dillon.starsectormarines.battle.reinforcement.ObjectiveLostTrigger;
import com.dillon.starsectormarines.battle.reinforcement.ReinforcementService;
import com.dillon.starsectormarines.battle.reinforcement.ShuttleMeans;
import com.dillon.starsectormarines.battle.reinforcement.WalkInMeans;
import com.dillon.starsectormarines.battle.ui.debug.ConvoySpawnDumper;
import org.apache.log4j.Logger;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.MapGenerator;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.objective.ConquestObjective;
import com.dillon.starsectormarines.battle.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Builds a battle scenario for the auto-battler. v3: marines arrive via
 * scheduled shuttle drops rather than pre-spawning. {@link UrbanMapGenerator}
 * carves the city; we pick spread-out landing zones in the marine quadrant,
 * stagger the drops, and let {@link BattleSimulation} run the state machine.
 * Defenders still pre-spawn (lore-correct: they're already on the ground).
 */
public final class BattleSetup {

    private static final Logger LOG = Logger.getLogger(BattleSetup.class);

    /** Default battle grid size (cells) — matches {@link MapScale#MEDIUM}. Used as the {@link com.dillon.starsectormarines.ops.BattleScreen} fallback when no simulation is active yet. The actual generated map dimensions come from {@link MapScale#forRisk}. */
    public static final int GRID_W = MapScale.MEDIUM.width;
    public static final int GRID_H = MapScale.MEDIUM.height;

    /** Three drops × 4 marines/shuttle keeps total marine count at 12 — matches pre-shuttle balance. */
    private static final int SHUTTLE_COUNT = 3;
    /** Sim-seconds between successive shuttle launches. Spaces out drops so the LZs aren't all active at once. */
    private static final float SHUTTLE_DROP_STAGGER_SEC = 1.5f;
    /** Minimum cell-distance between landing zones — avoids stacking all shuttles on the spawn anchor. */
    private static final int LZ_MIN_SEPARATION = 8;
    /** Entry/exit Y offset above the grid (in cells). Long enough that shuttles are visible during their descent. */
    private static final float SHUTTLE_OFFMAP_Y = 8f;

    /** BFS radius around the defender anchor we scan for candidate spawn cells. Larger than the largest expected defender count so we have a pool to cherry-pick high-cover cells from. */
    private static final int DEFENDER_SPAWN_SCAN_RADIUS = 14;
    /** BFS radius around a tactical-node anchor for picking garrison spawn cells. Tight — defenders should appear inside or right next to their post. */
    private static final int GARRISON_SPAWN_RADIUS = 5;

    /** Total ambient civilians (mix of CIVILIAN/ENGINEER/SCIENTIST) scattered around residential POIs as map flavor. */
    private static final int AMBIENT_CIVILIAN_COUNT = 8;
    /** BFS radius around each residential POI when looking for civilian spawn cells. */
    private static final int CIVILIAN_SPAWN_RADIUS = 5;

    /** Min/max parked vehicles scattered on streets and courtyards. Trucks block pathing + LOS, so they act as movable map terrain. */
    private static final int VEHICLE_COUNT_MIN = 3;
    private static final int VEHICLE_COUNT_MAX = 6;

    /**
     * The active {@link MapGenerator}. {@link BspCityGenerator} produces
     * irregular block-mosaic maps with per-{@link com.dillon.starsectormarines.battle.world.gen.BlockKind}
     * fillers (parks, plazas, industrial yards, waterfronts, fortified posts,
     * landing zones, etc.) on top of a BSP partition. {@link UrbanMapGenerator}
     * is kept around as a legacy fallback — flip this line if the new gen
     * needs to be temporarily disabled.
     */
    private static final MapGenerator MAP_GEN = new BspCityGenerator();

    /** SABOTAGE: number of charge sites to plant. One per shuttle = one planter per drop. */
    private static final int SABOTAGE_CHARGE_SITES = 3;
    /** SABOTAGE: sim-seconds a planter must dwell on a charge site to complete the plant. */
    private static final float SABOTAGE_PLANT_DURATION = 5.0f;

    private BattleSetup() {}

    public static BattleSimulation createPlaceholder() {
        return createPlaceholder(System.currentTimeMillis(), defaultManifest(), false);
    }

    public static BattleSimulation createPlaceholder(long seed) {
        return createPlaceholder(seed, defaultManifest(), false);
    }

    public static BattleSimulation createSabotage() {
        return createSabotage(System.currentTimeMillis(), defaultManifest(), false);
    }

    public static BattleSimulation createSabotage(long seed) {
        return createSabotage(seed, defaultManifest(), false);
    }

    /** Back-compat overload — assumes no heavy armor on the defender side. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest) {
        return createSabotage(seed, manifest, false);
    }

    /** Back-compat overload — assumes no heavy armor on the defender side. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest) {
        return createPlaceholder(seed, manifest, false);
    }

    /** Risk-defaulted overloads — map size collapses to {@link MapScale#MEDIUM}. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor) {
        return createSabotage(seed, manifest, enemyHasHeavyArmor, RiskLevel.MEDIUM);
    }

    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, RiskLevel.MEDIUM);
    }

    /** Default manifest used by no-arg factories — three single-cycle Aeroshuttles, matching pre-cycling behavior. */
    private static List<ShuttleAssignment> defaultManifest() {
        List<ShuttleAssignment> out = new ArrayList<>(SHUTTLE_COUNT);
        for (int i = 0; i < SHUTTLE_COUNT; i++) {
            out.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
        }
        return out;
    }

    /**
     * SABOTAGE variant: marines must plant charges on {@link #SABOTAGE_CHARGE_SITES}
     * target structures while defenders try to keep them off the sites. One
     * marine per shuttle drops in with the PLANTER role pre-assigned; the rest
     * deboard as combatants and provide cover fire.
     *
     * <p>Marine win: every {@link ChargeSiteObjective} completes AND at least
     * one marine is alive when the last charge sets. Defender win: kill every
     * marine before the charges go off.
     */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor, RiskLevel risk) {
        MapScale scale = MapScale.forRisk(risk);
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed);
        Random rng = new Random(seed);
        // Vehicles stamp before sim construction so the BattleSimulation's
        // zone-graph rebuild sees the final walkability — trucks partition zones.
        List<MapVehicle> vehiclePlacements = stampVehicles(map.grid, map.topology, rng);
        // Defense posts stamp before sim construction for the same reason —
        // the embankment ring cells flip walkability, and the zone graph the
        // sim builds on construction needs to reflect that.
        List<DefensePost> defensePosts = new ArrayList<>();
        DefensePostStamper.stampNonConquest(map.grid, map.topology,
                RoadReservation.mask(map.roadGraph, map.grid.getWidth(), map.grid.getHeight()),
                map.pointsOfInterest, map.doodads, defensePosts, rng);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        sim.setTacticalMap(map.tacticalMap);
        sim.setBuildings(map.buildings);
        sim.setDefensePosts(defensePosts);
        for (MapVehicle v : vehiclePlacements) sim.addVehicle(v);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        spawnDefensePostTurrets(sim, defensePosts);

        // Pick charge sites: prefer high-value POIs (lab/comms/depot) in the
        // defender half of the map. Fall back to any POI if not enough qualify.
        List<PointOfInterest> sites = pickChargeSites(map.pointsOfInterest, scale.width / 2, SABOTAGE_CHARGE_SITES);
        List<ChargeSiteObjective> objectives = new ArrayList<>(sites.size());
        for (PointOfInterest poi : sites) {
            // Plant target is inside the building — the planter pathfinds in
            // through a doorway, plants, exits. POIs back the interior anchor
            // with a walkable INDOOR cell; legacy POIs (no carved interior)
            // mirror the exterior anchor so this still produces a valid cell.
            ChargeSiteObjective obj = new ChargeSiteObjective(
                    poi.interiorAnchorX, poi.interiorAnchorY,
                    SABOTAGE_PLANT_DURATION,
                    "Plant charge: " + poi.kind.name().toLowerCase());
            objectives.add(obj);
            sim.addObjective(obj);
        }
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        // Marines: one Shuttle per assignment, each flying assignment.cycles
        // sorties. Each sortie counts as one "drop"; the globalDropIdx threads
        // through assignments + cycles so per-cycle planter targeting hits a
        // different charge site each time (cycle 0 of shuttle 0 → site 0,
        // cycle 1 of shuttle 0 → site 1, etc.).
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        List<int[]> lzCells = pickLandingZones(map.grid, map.marineSpawnX, map.marineSpawnY, assignments.size());
        stampLzPads(sim, lzCells);
        int globalDropIdx = 0;
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, scale.width, scale.height, null);
            Shuttle shuttle = new Shuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC);
            shuttle.totalCycles = a.cycles;
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = buildSabotageLoadout(shuttle.type.capacity, objectives, globalDropIdx, rng);
                globalDropIdx++;
            }
            shuttle.cycleLoadouts = cycleLoadouts;
            shuttle.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(shuttle);
            sim.addShuttle(shuttle);
        }

        allocateDefenders(sim, map, DefenderRoster.forMission(MissionType.SABOTAGE, risk, enemyHasHeavyArmor), rng);
        spawnAmbientCivilians(sim, map, rng);
        // Marine commander: routes non-planter squads toward the closest
        // unfinished charge site so cover-fire teams (or squads whose
        // planter has died) spread across the multi-site map instead of
        // dogpiling the nearest fight.
        sim.setCommander(Faction.MARINE, new SabotageCommand());
        installReinforcementLayer(sim, map, null);
        return sim;
    }

    /** Falls back to the default manifest when callers pass null or an empty list. Keeps test entry points working without a manifest. */
    private static List<ShuttleAssignment> resolveManifest(List<ShuttleAssignment> manifest) {
        if (manifest == null || manifest.isEmpty()) return defaultManifest();
        return manifest;
    }

    /** Drops a yellow-striped landing-pad doodad under each LZ cell so the touchdown reads as a deliberate landing on a marked pad. Lives on the road sheet, drawn between floor and units. */
    private static void stampLzPads(BattleSimulation sim, List<int[]> lzCells) {
        for (int[] lz : lzCells) {
            sim.addDoodad(new Doodad(lz[0], lz[1], TileManifest.LZ_PAD, true));
        }
    }

    /**
     * Filters POIs to the defender half (x >= halfX), prefers lab/comms/depot
     * kinds over residential, and returns up to {@code count} of them spaced
     * apart by at least {@link #LZ_MIN_SEPARATION}. Falls back to any POI in
     * defender territory if not enough valuable ones exist.
     */
    private static List<PointOfInterest> pickChargeSites(List<PointOfInterest> all, int halfX, int count) {
        List<PointOfInterest> highValue = new ArrayList<>();
        List<PointOfInterest> anyDefender = new ArrayList<>();
        for (PointOfInterest poi : all) {
            if (poi.centerX() < halfX) continue;
            anyDefender.add(poi);
            if (poi.kind != PointOfInterest.Kind.RESIDENTIAL) highValue.add(poi);
        }
        List<PointOfInterest> picked = new ArrayList<>();
        int minSepSq = LZ_MIN_SEPARATION * LZ_MIN_SEPARATION;
        for (List<PointOfInterest> pool : List.of(highValue, anyDefender)) {
            for (PointOfInterest poi : pool) {
                if (picked.size() >= count) break;
                boolean farEnough = true;
                for (PointOfInterest prev : picked) {
                    int dx = prev.anchorCellX - poi.anchorCellX;
                    int dy = prev.anchorCellY - poi.anchorCellY;
                    if (dx * dx + dy * dy < minSepSq) { farEnough = false; break; }
                }
                if (farEnough && !picked.contains(poi)) picked.add(poi);
            }
            if (picked.size() >= count) break;
        }
        return picked;
    }

    /**
     * Slot 0 of each shuttle gets a PLANTER assigned to a charge site (paired
     * by shuttle index, wrapping around if shuttle count and site count differ).
     * Remaining slots are plain combatants.
     */
    /**
     * Builds one sortie's loadout — slot 0 is a PLANTER targeting the charge
     * site keyed by {@code dropIndex}, the rest are plain combatants. {@code
     * dropIndex} is the GLOBAL drop number across all shuttles + cycles (not
     * the shuttle index), so a single cycling shuttle hits each site in turn
     * on successive sorties.
     */
    private static MarineLoadout[] buildSabotageLoadout(int capacity, List<ChargeSiteObjective> sites, int dropIndex, Random rng) {
        MarineLoadout[] roster = buildBaseLoadouts(capacity, rng);
        if (!sites.isEmpty()) {
            // Slot 0 becomes the planter — keep their primary weapon assignment
            // (so they fire opportunistically en route), drop any secondary so
            // they're not lugging a rocket launcher to a charge plant.
            ChargeSiteObjective site = sites.get(dropIndex % sites.size());
            MarineWeapon planterPrimary = roster[0].primary;
            roster[0] = new MarineLoadout(UnitRole.PLANTER, site, planterPrimary, null, 0);
        }
        return roster;
    }

    /**
     * Builds a per-slot loadout for one shuttle. Every slot gets a rolled
     * primary (50% pulse rifle, 25% SMG, 25% DMR); the last slot also picks
     * up a rocket launcher with full ammo as the squad's anti-armor specialist.
     * Capacity-1 shuttles skip the secondary — a one-marine drop is a courier,
     * not a fireteam.
     */
    /**
     * Populates a freshly-constructed shuttle's turret mounts with the
     * default A2G kit for its type, when the type has hardpoints &gt; 0.
     * Wired in here so every spawn path picks up fire support automatically
     * — a follow-up briefing-UI slice will let the player override the role
     * per shuttle, which slots in via {@code shuttle.assignedRole} before
     * this helper runs.
     *
     * <p>Mounts start with their facing aligned to the shuttle's nose so
     * the first hover-station tick doesn't snap turrets through a 90° swing.
     */
    private static void equipDefaultTurrets(Shuttle shuttle) {
        if (shuttle.type.hardpoints <= 0) return;
        if (shuttle.assignedRole == null) shuttle.assignedRole = TurretRole.A2G;
        TurretMount[] mounts = ShuttleType.kitFor(shuttle.assignedRole, shuttle.type.hardpoints);
        MountedTurret[] turrets = new MountedTurret[mounts.length];
        for (int i = 0; i < mounts.length; i++) {
            turrets[i] = new MountedTurret(mounts[i]);
            turrets[i].facingDegrees = shuttle.body.facingDegrees;
        }
        shuttle.turrets = turrets;
    }

    private static MarineLoadout[] buildBaseLoadouts(int capacity, Random rng) {
        MarineLoadout[] roster = new MarineLoadout[capacity];
        int rocketSlot = (capacity > 1) ? capacity - 1 : -1;
        for (int i = 0; i < capacity; i++) {
            MarineWeapon primary = rollPrimary(rng);
            if (i == rocketSlot) {
                roster[i] = new MarineLoadout(UnitRole.COMBATANT, null, primary,
                        MarineSecondary.ROCKET_LAUNCHER,
                        MarineSecondary.ROCKET_LAUNCHER.startingAmmo);
            } else {
                roster[i] = new MarineLoadout(UnitRole.COMBATANT, null, primary, null, 0);
            }
        }
        return roster;
    }

    /** Weighted primary roll — pulse rifle is the workhorse, SMG + DMR split the specialist slots evenly. */
    private static MarineWeapon rollPrimary(Random rng) {
        int r = rng.nextInt(4);
        if (r == 0) return MarineWeapon.SMG;
        if (r == 1) return MarineWeapon.DMR;
        return MarineWeapon.PULSE_RIFLE;
    }

    /** Back-compat overload — assumes generic ASSAULT mission type. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor, RiskLevel risk) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, risk, MissionType.ASSAULT);
    }

    /**
     * Catch-all factory for mission types without a dedicated builder (ASSAULT,
     * RAID, EXTRACTION). The {@code type} parameter only affects defender roster
     * sizing/composition — objectives are the same "eliminate the other side"
     * pair for all three; per-type objective wiring lands when those mission
     * types get their own factories.
     */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor, RiskLevel risk,
                                                     MissionType type) {
        MapScale scale = MapScale.forRisk(risk);
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed);
        Random rng = new Random(seed);
        List<MapVehicle> vehiclePlacements = stampVehicles(map.grid, map.topology, rng);
        List<DefensePost> defensePosts = new ArrayList<>();
        DefensePostStamper.stampNonConquest(map.grid, map.topology,
                RoadReservation.mask(map.roadGraph, map.grid.getWidth(), map.grid.getHeight()),
                map.pointsOfInterest, map.doodads, defensePosts, rng);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        sim.setTacticalMap(map.tacticalMap);
        sim.setBuildings(map.buildings);
        sim.setDefensePosts(defensePosts);
        for (MapVehicle v : vehiclePlacements) sim.addVehicle(v);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        spawnDefensePostTurrets(sim, defensePosts);

        // Default ASSAULT objectives — eliminate the other side. Mission-specific
        // setups (sabotage, raid, extraction) will swap or add to this pair.
        sim.addObjective(new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        // Marines: one Shuttle per assignment, each flying assignment.cycles sorties.
        // ASSAULT has no per-cycle role distinction (everyone is a combatant), so no
        // cycleLoadouts setup is needed — Shuttle.totalCycles drives repeat behavior.
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        List<int[]> lzCells = pickLandingZones(map.grid, map.marineSpawnX, map.marineSpawnY, assignments.size());
        stampLzPads(sim, lzCells);
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, scale.width, scale.height, null);
            Shuttle shuttle = new Shuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC);
            shuttle.totalCycles = a.cycles;
            // Per-cycle weapon loadouts — re-rolled each sortie so a cycling
            // shuttle doesn't deboard the same exact fireteam composition twice.
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = buildBaseLoadouts(shuttle.type.capacity, rng);
            }
            shuttle.cycleLoadouts = cycleLoadouts;
            shuttle.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(shuttle);
            sim.addShuttle(shuttle);
        }

        // Defenders pre-spawn around tactical-node anchors (garrison squads
        // pegged to the highest-priority posts; leftovers form patrol squads).
        // Legacy maps with no tactical layer fall back to the single-cluster
        // spawn around the defender anchor.
        allocateDefenders(sim, map, DefenderRoster.forMission(type, risk, enemyHasHeavyArmor), rng);
        spawnAmbientCivilians(sim, map, rng);
        installReinforcementLayer(sim, map, null);
        if (type == MissionType.ASSAULT) {
            sim.setCommander(Faction.MARINE, new AssaultCommand());
        }
        return sim;
    }

    /**
     * CONQUEST variant: full beach→port→city→fortress biome push with the
     * super-wall stamper active. Map size scales with risk (same tiers as
     * Assault/Sabotage) and the traversal axis is rolled per-seed —
     * SOUTH_TO_NORTH or WEST_TO_EAST, so two conquest missions on the same
     * world play with a different attacker approach. Objectives match Assault
     * for v1 (eliminate the other side); a "capture the command post" variant
     * is a natural follow-up once the AI consumer of the tactical map lands.
     *
     * <p>Marines and defenders pin to their respective biome anchors instead
     * of the legacy left/right halves — marine LZ in BEACH, defender garrison
     * in FORTRESS_DISTRICT (with garrison squads at the wall's tactical nodes).
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor, RiskLevel risk) {
        MapScale scale = MapScale.forRisk(risk);
        Random rng = new Random(seed);
        TraversalAxis axis = rng.nextBoolean() ? TraversalAxis.SOUTH_TO_NORTH : TraversalAxis.WEST_TO_EAST;
        // Generator uses its own seeded RNG — pass the same seed so different
        // mission types from the same seed still produce comparable layouts;
        // axis flips deterministically off the first bit of our wrapper RNG.
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed, axis);

        List<MapVehicle> vehiclePlacements = stampVehicles(map.grid, map.topology, rng);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        sim.setTacticalMap(map.tacticalMap);
        sim.setBuildings(map.buildings);
        sim.setDefensePosts(map.defensePosts);
        for (MapVehicle v : vehiclePlacements) sim.addVehicle(v);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        // Conquest defense posts come pre-stamped by the biome-aware
        // DefensePostStamper inside BspCityGenerator (BEACH→PORT→kill-zone
        // tiers + rear ARTILLERY battery). Each post is paired with a manned
        // GUARDPOST squad via {@link #linkGuardpostSquads} below — that's the
        // difference from the non-conquest path, which stamps the same shapes
        // unmanned via {@code DefensePostStamper.stampNonConquest}.
        spawnDefensePostTurrets(sim, map.defensePosts);

        // Conquest win condition: marines dismantle defender supply
        // structures, not "kill every defender." Pre-slice-4 this was
        // EliminateFactionObjective on both sides, which never resolved
        // because reinforcement kept spawning fresh militia after every
        // hardpoint fell. ConquestObjective reads CompoundService and
        // latches when every defender compound is MARINE_HELD. Defender
        // side keeps the elimination shape so "every marine died" still
        // terminates the battle. See roadmap/conquest/central-keep.md
        // slice 4.
        sim.addObjective(new ConquestObjective(sim.getCompoundService()));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        // Conquest = beach landing — spread LZs along the attacker frontage
        // rather than clustered around a single anchor. Other mission types
        // use the BFS picker until they get their own tuned strategies.
        List<int[]> lzCells = pickConquestLandingZones(map.grid,
                map.marineSpawnX, map.marineSpawnY, assignments.size(), axis, rng);
        stampLzPads(sim, lzCells);
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, scale.width, scale.height, axis);
            Shuttle shuttle = new Shuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC);
            shuttle.totalCycles = a.cycles;
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = buildBaseLoadouts(shuttle.type.capacity, rng);
            }
            shuttle.cycleLoadouts = cycleLoadouts;
            shuttle.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(shuttle);
            sim.addShuttle(shuttle);
        }

        allocateDefenders(sim, map, DefenderRoster.forMission(MissionType.CONQUEST, risk, enemyHasHeavyArmor), rng);
        linkGuardpostSquads(sim, map.defensePosts);
        spawnAmbientCivilians(sim, map, rng);
        // Marine commander: lateral-strip partition perpendicular to the
        // traversal axis. Each shuttle squad gets sticky-assigned to one
        // strip on first observation; per slow-tick the commander writes
        // CLEAR_ZONE on each squad pointed at the forward-most defender-
        // occupied zone in its strip. Spreads marines across the frontage
        // instead of dogpiling the nearest defender contact.
        sim.setCommander(Faction.MARINE, new ConquestCommand(axis));
        sim.setGarrisonSystem(new CompoundGarrisonSystem(axis));
        installReinforcementLayer(sim, map, axis);
        return sim;
    }

    /**
     * Install the reinforcement layer on the sim. Triggers (run order =
     * insertion):
     * <ul>
     *   <li>{@link GarrisonDepletedTrigger} — defender compound strength
     *       drops below threshold.</li>
     *   <li>{@link ObjectiveLostTrigger} — a previously defender-held
     *       zone has been taken by marines.</li>
     * </ul>
     * Means (priority = insertion order; first {@code canFulfill = true}
     * wins):
     * <ul>
     *   <li>{@link ConvoyMeans} — readable truck delivery; needs a road
     *       graph and a reachable rally.</li>
     *   <li>{@link ShuttleMeans} — air-drop; needs a walkable LZ within
     *       8 cells of the rally. Reuses the existing {@code AirSystem}
     *       state machine.</li>
     *   <li>{@link WalkInMeans} — always-feasible floor; spawns infantry
     *       on the side-appropriate perimeter and pulls them toward the
     *       rally via {@code assignedNode}.</li>
     * </ul>
     * Non-Conquest maps register the same set and self-gate harmlessly
     * (no compounds → no garrison fires; no road graph → convoy yields to
     * shuttle; no LZ → shuttle yields to walk-in). Replaces the prior
     * {@link #maybeSpawnDebugConvoy} debug-spawn path.
     *
     * @param axis traversal axis for the map; nullable on non-Conquest paths
     *             where there's no defender/attacker rear edge — walk-in
     *             falls back to a stable default edge.
     */
    private static void installReinforcementLayer(BattleSimulation sim, MapResult map, TraversalAxis axis) {
        ReinforcementService rs = sim.getReinforcementService();
        rs.addTrigger(new GarrisonDepletedTrigger());
        rs.addTrigger(new ObjectiveLostTrigger());
        rs.addMeans(new ConvoyMeans(map.roadGraph, axis));
        rs.addMeans(new ShuttleMeans(axis));
        rs.addMeans(new WalkInMeans(axis));
    }

    /**
     * Dev-only flag — when true, the (legacy) {@link #maybeSpawnDebugConvoy}
     * path drops a single defender-side militia truck per battle. Retained
     * for emergency rollback while the {@link ReinforcementService} v1 cut
     * beds in; not called from any active code path.
     */
    public static boolean DEBUG_SPAWN_TEST_CONVOY = true;
    /** Sim-seconds before the test convoy emerges from off-map. Long enough that the player sees the battle start before reinforcements arrive. */
    private static final float DEBUG_CONVOY_PENDING_SEC = 6f;
    /** Cells the off-map staging waypoint sits beyond the perimeter — the truck's first INCOMING waypoint, so it drives onto the map rather than popping in at the edge. */
    private static final float DEBUG_CONVOY_OFFMAP_PAD = 6f;

    /**
     * V1 debug spawn — drops one {@link VehicleType#HEAVY_APC} into the
     * sim if {@link #DEBUG_SPAWN_TEST_CONVOY} is on and the map has a
     * non-empty {@link RoadGraph}. Picks the perimeter node closest to the
     * defender spawn as the entry, the highest-degree non-perimeter node
     * closest to map center as the dropoff, and routes between them with
     * {@link ConvoyPlanner#planPath}. Outbound is the inbound path
     * reversed — the APC retreats the way it arrived (unused for
     * non-departing variants, but kept for compatibility).
     *
     * <p>Verbose-logs every step so a "I don't see the APC" report can be
     * traced through the starsector.log without code changes.
     */
    private static void maybeSpawnDebugConvoy(BattleSimulation sim, MapResult map) {
        if (!DEBUG_SPAWN_TEST_CONVOY) return;
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();
        RoadGraph graph = map.roadGraph;
        if (graph == null || graph.nodes().isEmpty()) {
            LOG.warn("convoy: skip — roadGraph "
                    + (graph == null ? "null" : "empty"));
            ConvoySpawnDumper.dump("roadGraph " + (graph == null ? "null" : "empty"),
                    graph, null, null, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        List<RoadGraph.Node> perim = graph.perimeterNodes();
        if (perim.isEmpty()) {
            LOG.warn("convoy: skip — no perimeter nodes in graph "
                    + "(" + graph.nodes().size() + " nodes, " + graph.edges().size() + " edges)");
            ConvoySpawnDumper.dump("no perimeter nodes",
                    graph, null, null, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        // Iterate perimeter nodes in order of distance to the defender spawn.
        // For each candidate entry, restrict the destination search to the
        // graph component reachable from that entry — fixes the
        // disconnected-graph case where the closest-to-spawn perimeter node
        // sits in a tiny stub component and the actual interior junctions
        // live in a separate component. First entry that yields a usable
        // junction wins.
        List<RoadGraph.Node> perimByDist = sortedByDistance(perim, map.defenderSpawnX, map.defenderSpawnY);
        RoadGraph.Node entry = null;
        RoadGraph.Node dest = null;
        for (RoadGraph.Node candidate : perimByDist) {
            Set<RoadGraph.Node> reachable = reachableFrom(candidate);
            RoadGraph.Node candDest = bestInteriorJunctionWithin(reachable, gw / 2, gh / 2);
            if (candDest != null && candDest != candidate) {
                entry = candidate;
                dest = candDest;
                break;
            }
        }
        if (entry == null) {
            // Last perimeter we tried is the most useful one to dump for diagnostics.
            RoadGraph.Node lastEntry = perimByDist.isEmpty() ? null : perimByDist.get(perimByDist.size() - 1);
            LOG.warn("convoy: skip — no entry/dest pair in the same component "
                    + "(" + perimByDist.size() + " perimeter candidates tried)");
            ConvoySpawnDumper.dump("no entry/dest pair in any component",
                    graph, lastEntry, null, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        List<RoadGraph.Edge> path = ConvoyPlanner.planPath(graph, entry, dest);
        if (path == null || path.isEmpty()) {
            // Shouldn't happen now that entry/dest are in the same component,
            // but keep the dump in case the reachable-set scan and BFS ever
            // disagree (e.g. graph mutation between the two queries).
            LOG.warn("convoy: skip — planPath failed entry→dest "
                    + "(" + entry.cellX + "," + entry.cellY + ")→("
                    + dest.cellX + "," + dest.cellY + ")");
            ConvoySpawnDumper.dump("planPath failed despite component check",
                    graph, entry, dest, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        float[][] inboundCells = ConvoyPlanner.expandToWaypoints(path, entry);

        if (inboundCells[0].length >= 2) {
            int last = inboundCells[0].length - 1;
            float startFacing = AirBody.facingToward(
                    inboundCells[0][1] - inboundCells[0][0],
                    inboundCells[1][1] - inboundCells[1][0]);
            float goalFacing = AirBody.facingToward(
                    inboundCells[0][last] - inboundCells[0][last - 1],
                    inboundCells[1][last] - inboundCells[1][last - 1]);
            float[][] refined = HybridAStarPlanner.refine(
                    inboundCells[0], inboundCells[1], startFacing, goalFacing,
                    VehicleType.HEAVY_APC, sim.getGrid());
            if (refined != null) inboundCells = refined;
        }

        // Prepend an off-map staging waypoint perpendicular to the entry's
        // edge so the truck visibly drives onto the map rather than popping
        // in at the perimeter cell.
        float offX = entry.cellX + 0.5f;
        float offY = entry.cellY + 0.5f;
        if (entry.cellY == 0)            offY = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (entry.cellY == gh - 1)  offY = gh + DEBUG_CONVOY_OFFMAP_PAD;
        else if (entry.cellX == 0)       offX = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (entry.cellX == gw - 1)  offX = gw + DEBUG_CONVOY_OFFMAP_PAD;

        int len = inboundCells[0].length;
        float[] inX = new float[len + 1];
        float[] inY = new float[len + 1];
        inX[0] = offX;
        inY[0] = offY;
        System.arraycopy(inboundCells[0], 0, inX, 1, len);
        System.arraycopy(inboundCells[1], 0, inY, 1, len);
        float[] inH = null;
        if (inboundCells.length > 2) {
            inH = new float[len + 1];
            inH[0] = AirBody.facingToward(inX[1] - inX[0], inY[1] - inY[0]);
            System.arraycopy(inboundCells[2], 0, inH, 1, len);
        }

        RoadGraph.Node exitNode = ConvoyPlanner.pickExitNode(graph, dest, entry);
        List<RoadGraph.Edge> outPath = ConvoyPlanner.planPath(graph, dest, exitNode);
        float[][] outCells;
        if (outPath != null && !outPath.isEmpty()) {
            outCells = ConvoyPlanner.expandToWaypoints(outPath, dest);
        } else {
            outCells = new float[][]{ new float[]{dest.cellX + 0.5f}, new float[]{dest.cellY + 0.5f} };
        }

        int inLast = inboundCells[0].length - 1;
        float lzX = inboundCells[0][inLast];
        float lzY = inboundCells[1][inLast];
        float lzFacing = AirBody.facingToward(
                inboundCells[0][inLast] - inboundCells[0][inLast - 1],
                inboundCells[1][inLast] - inboundCells[1][inLast - 1]);
        float distLzToDest = (float) Math.sqrt(
                (lzX - outCells[0][0]) * (lzX - outCells[0][0])
              + (lzY - outCells[1][0]) * (lzY - outCells[1][0]));
        if (distLzToDest > 0.5f) {
            float[] pX = new float[outCells[0].length + 1];
            float[] pY = new float[outCells[1].length + 1];
            pX[0] = lzX;
            pY[0] = lzY;
            System.arraycopy(outCells[0], 0, pX, 1, outCells[0].length);
            System.arraycopy(outCells[1], 0, pY, 1, outCells[1].length);
            outCells = new float[][] { pX, pY };
        }

        float exitOffX = exitNode.cellX + 0.5f;
        float exitOffY = exitNode.cellY + 0.5f;
        if (exitNode.cellY == 0)            exitOffY = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (exitNode.cellY == gh - 1)  exitOffY = gh + DEBUG_CONVOY_OFFMAP_PAD;
        else if (exitNode.cellX == 0)       exitOffX = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (exitNode.cellX == gw - 1)  exitOffX = gw + DEBUG_CONVOY_OFFMAP_PAD;
        if (outCells[0].length >= 2) {
            int outLast = outCells[0].length - 1;
            float exitFacing = AirBody.facingToward(
                    exitOffX - outCells[0][outLast], exitOffY - outCells[1][outLast]);
            outCells = ConvoyPlanner.refineWithFallback(
                    outCells[0], outCells[1], lzFacing, exitFacing,
                    VehicleType.HEAVY_APC, sim.getGrid());
        }
        int outLen = outCells[0].length;
        float[] outX = new float[outLen + 1];
        float[] outY = new float[outLen + 1];
        System.arraycopy(outCells[0], 0, outX, 0, outLen);
        System.arraycopy(outCells[1], 0, outY, 0, outLen);
        outX[outLen] = exitOffX;
        outY[outLen] = exitOffY;
        float[] outH = new float[outLen + 1];
        if (outCells.length > 2) {
            System.arraycopy(outCells[2], 0, outH, 0, outLen);
        }
        outH[outLen] = AirBody.facingToward(exitOffX - outX[outLen - 1], exitOffY - outY[outLen - 1]);

        Vehicle truck = new Vehicle(
                VehicleType.HEAVY_APC, Faction.DEFENDER,
                inX, inY, outX, outY,
                DEBUG_CONVOY_PENDING_SEC);
        truck.inboundHeading = inH;
        truck.outboundHeading = outH;
        sim.addConvoyVehicle(truck);
        LOG.info("convoy: spawned HEAVY_APC entry=(" + entry.cellX + "," + entry.cellY
                + ") exit=(" + exitNode.cellX + "," + exitNode.cellY
                + ") dest=(" + dest.cellX + "," + dest.cellY
                + ") path=" + path.size() + "edges/" + inX.length + "wps");
    }

    /** Sort {@code nodes} by squared distance to ({@code x, y}), ascending. Defensive copy — input list is not mutated. */
    private static List<RoadGraph.Node> sortedByDistance(List<RoadGraph.Node> nodes, int x, int y) {
        List<RoadGraph.Node> out = new ArrayList<>(nodes);
        out.sort((a, b) -> {
            int adx = a.cellX - x, ady = a.cellY - y;
            int bdx = b.cellX - x, bdy = b.cellY - y;
            return Integer.compare(adx*adx + ady*ady, bdx*bdx + bdy*bdy);
        });
        return out;
    }

    /** BFS flood from {@code seed} over edges — returns the seed's connected component as a Set. */
    private static Set<RoadGraph.Node> reachableFrom(RoadGraph.Node seed) {
        Set<RoadGraph.Node> seen = new HashSet<>();
        Deque<RoadGraph.Node> q = new ArrayDeque<>();
        q.add(seed);
        seen.add(seed);
        while (!q.isEmpty()) {
            RoadGraph.Node n = q.poll();
            for (RoadGraph.Edge e : n.edges()) {
                RoadGraph.Node nxt = e.otherEnd(n);
                if (seen.add(nxt)) q.add(nxt);
            }
        }
        return seen;
    }

    /**
     * Best interior junction within a reachable set, near ({@code cx, cy}).
     * Walks degree thresholds from {@code 3} down to {@code 2} — a degree-2
     * interior node is a worse drop-off (no choice but to turn around at
     * arrival) but still better than a failed spawn, especially when a
     * stub component has only chain nodes.
     */
    private static RoadGraph.Node bestInteriorJunctionWithin(Set<RoadGraph.Node> reachable, int cx, int cy) {
        for (int minDegree = 3; minDegree >= 2; minDegree--) {
            RoadGraph.Node best = null;
            int bestD2 = Integer.MAX_VALUE;
            for (RoadGraph.Node n : reachable) {
                if (n.perimeter) continue;
                if (n.degree() < minDegree) continue;
                int dx = n.cellX - cx;
                int dy = n.cellY - cy;
                int d2 = dx * dx + dy * dy;
                if (d2 < bestD2) { bestD2 = d2; best = n; }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Post-hoc wiring of GUARDPOST defender squads to their {@link DefensePost}.
     * Done after {@link #allocateDefenders} rather than threading the post list
     * into the allocator: the allocator stays oblivious to the post tier (it
     * just sees a tactical node), and the GUARDPOST-specific tuning (patrol
     * radius pulled from {@link DefensePostKind#patrolRadius}, post linkage for
     * release-on-turrets-dead) lives in one localized pass here.
     *
     * <p>Match by anchor position: the stamper emits one GUARDPOST node per
     * post at the post's anchor cell, so anchor equality is a 1:1 lookup.
     */
    private static void linkGuardpostSquads(BattleSimulation sim, List<DefensePost> posts) {
        if (posts == null || posts.isEmpty()) return;
        for (Squad squad : sim.getSquads()) {
            TacticalNode node = squad.assignedNode;
            if (node == null || node.kind != TacticalNode.Kind.GUARDPOST) continue;
            for (DefensePost post : posts) {
                if (post.anchorX == node.anchorX && post.anchorY == node.anchorY) {
                    squad.defensePost = post;
                    squad.patrolRadius = post.tier.patrolRadius;
                    break;
                }
            }
        }
    }

    /**
     * Pick the entry/exit off-map points for a shuttle drop. Returns
     * {@code {entryX, entryY, exitX, exitY}}. In legacy mode ({@code axis}
     * null) the shuttle drops in from above the top of the grid; in conquest
     * mode the entry is off the attacker-facing edge derived from the axis
     * (south edge for SOUTH_TO_NORTH, west edge for WEST_TO_EAST). Exit sits
     * one extra step beyond entry so the departing shuttle has a moment of
     * visible climb before it disappears.
     */
    private static float[] shuttleEntryFor(float lzCenterX, float lzCenterY,
                                           int gridW, int gridH, TraversalAxis axis) {
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            // Attacker side = south = low y. Entry below the grid, exit further below.
            return new float[]{
                    lzCenterX, -SHUTTLE_OFFMAP_Y,
                    lzCenterX, -SHUTTLE_OFFMAP_Y - 4f };
        }
        if (axis == TraversalAxis.WEST_TO_EAST) {
            // Attacker side = west = low x. Entry left of the grid.
            return new float[]{
                    -SHUTTLE_OFFMAP_Y,        lzCenterY,
                    -SHUTTLE_OFFMAP_Y - 4f,   lzCenterY };
        }
        // Legacy: drop from above the top edge.
        return new float[]{
                lzCenterX, gridH + SHUTTLE_OFFMAP_Y,
                lzCenterX, gridH + SHUTTLE_OFFMAP_Y + 4f };
    }

    /**
     * Distributes defender units across the map. When the {@link TacticalMap}
     * carries DEFENDER-leaning nodes (towers, gates, command posts, etc.),
     * top-priority nodes get GARRISON squads of their declared
     * {@link TacticalNode#garrisonSize}, with stiffening regulars tucked into
     * the highest-priority posts and any HEAVY_MECH lance bundled at the very
     * top slot so a 3-mech lance lands as one coordinated garrison. Any
     * defenders remaining after garrisons are filled bundle into PATROL squads
     * anchored to spare nodes, so the city has foot traffic instead of all
     * defenders sitting on the wall.
     *
     * <p>Force size + composition come from the {@link DefenderRoster} —
     * derived from {@link com.dillon.starsectormarines.ops.MissionType} +
     * {@link com.dillon.starsectormarines.ops.RiskLevel}. HIGH CONQUEST can
     * land 200 defenders; LOW SABOTAGE bottoms out at 12. The roster also
     * carries {@link DefenderRoster#patrolSquadSize} so larger forces don't
     * fragment into dozens of three-member patrols.
     *
     * <p>Legacy maps with no tactical nodes fall back to the original single-
     * cluster spawn around {@code map.defenderSpawnX/Y}: a flat list of
     * defenders, biased to high-cover cells, all as plain COMBATANTs. This
     * preserves placeholder/legacy generator output until those gens grow a
     * tactical layer of their own.
     */
    private static void allocateDefenders(BattleSimulation sim, MapResult map,
                                          DefenderRoster roster, Random rng) {
        TacticalMap tactical = map.tacticalMap;
        List<TacticalNode> defenderNodes = (tactical != null)
                ? new ArrayList<>(tactical.forFaction(Faction.DEFENDER))
                : Collections.emptyList();
        if (defenderNodes.isEmpty()) {
            List<int[]> cells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, roster.totalCount);
            spawnLegacyDefenderCluster(sim, cells, roster);
            return;
        }
        // Highest priority first — these get garrisons; the rest become patrol anchors.
        defenderNodes.sort(Comparator.comparingInt((TacticalNode n) -> -n.priorityScore));

        // Two separate queues — mechs and infantry never share a squad.
        // The planner ships distinct goal/action libraries for each, and a
        // mixed-arms squad would force a unified planner with two different
        // kinematic profiles. Adjacency between squads (Story E,
        // mech-screened advance) is the integration point instead. Mechs
        // drain first into the highest-priority slots; once exhausted,
        // infantry fills the rest.
        FactionUnitRoster defRoster = FactionUnitRoster.forFaction(Faction.DEFENDER);
        UnitType mechType = defRoster.mech();
        UnitType eliteType = defRoster.elite();
        UnitType infantryType = defRoster.infantry();
        java.util.Deque<UnitType> mechQueue = new java.util.ArrayDeque<>();
        java.util.Deque<UnitType> infQueue = new java.util.ArrayDeque<>();
        for (int i = 0; i < roster.mechCount; i++) mechQueue.add(mechType);
        for (int i = 0; i < roster.eliteCount; i++) infQueue.add(eliteType);
        for (int i = 0; i < roster.militiaCount; i++) infQueue.add(infantryType);

        int defenderIdx = 0;
        // Battle-wide counter, shared by Pass 1 + Pass 2. Round-robins
        // LR/Armored across the whole defender mech roster so the doctrine
        // mix is balanced regardless of which pass each mech lands in.
        int mechSpawnIdx = 0;
        List<TacticalNode> patrolAnchors = new ArrayList<>();

        // Pass 1 — garrison the highest-priority nodes. Each garrison draws
        // from a single source queue (mechs first while available, then
        // infantry) so the resulting squad is homogeneous.
        for (TacticalNode node : defenderNodes) {
            java.util.Deque<UnitType> source = !mechQueue.isEmpty() ? mechQueue : infQueue;
            if (source.isEmpty()) { patrolAnchors.add(node); continue; }
            int want = Math.min(node.garrisonSize, source.size());
            List<int[]> cells = pickCellsNear(map.grid, sim.getZoneGraph(), node.anchorX, node.anchorY, GARRISON_SPAWN_RADIUS, want);
            if (cells.isEmpty()) { patrolAnchors.add(node); continue; }
            Squad squad = null;
            int spawned = 0;
            for (int[] cell : cells) {
                if (source.isEmpty()) break;
                UnitType type = source.poll();
                MechRole mechRole = (type == mechType) ? nextMechRole(mechSpawnIdx++) : null;
                Unit unit = makeDefender("d" + defenderIdx++, type, cell[0], cell[1], mechRole);
                unit.role = UnitRole.GARRISON;
                unit.homeCellX = cell[0];
                unit.homeCellY = cell[1];
                if (squad == null) {
                    int sid = sim.mintSquad(Faction.DEFENDER, unit);
                    squad = sim.getSquad(sid);
                    squad.assignedNode = node;
                    // Story A: infantry garrisons hold fire until the
                    // kill-zone gate trips. Mech garrisons skip the gate —
                    // overwatch discipline lives in their planner-side
                    // doctrine (LR Support withholds short-range weapons),
                    // not in a fire-suppression flag on the squad.
                    squad.holdsFireUntilKillZone = (unit.mech == null);
                }
                unit.squadId = squad.id;
                sim.addUnit(unit);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }

        // Pass 2 — leftover defenders form patrol squads. Each patrol takes
        // up to roster.patrolSquadSize members from a single source queue
        // (mechs first while available, then infantry), anchored at a spare
        // node (or a top-priority node if there are no spares). Cycles
        // through the anchor list when defenders exceed
        // anchors * patrolSquadSize.
        if (mechQueue.isEmpty() && infQueue.isEmpty()) return;
        List<TacticalNode> anchorPool = patrolAnchors.isEmpty() ? defenderNodes : patrolAnchors;
        int anchorIdx = 0;
        while (!mechQueue.isEmpty() || !infQueue.isEmpty()) {
            if (anchorPool.isEmpty()) break;
            java.util.Deque<UnitType> source = !mechQueue.isEmpty() ? mechQueue : infQueue;
            TacticalNode anchor = anchorPool.get(anchorIdx % anchorPool.size());
            anchorIdx++;
            int want = Math.min(roster.patrolSquadSize, source.size());
            List<int[]> cells = pickCellsNear(map.grid, sim.getZoneGraph(), anchor.anchorX, anchor.anchorY, GARRISON_SPAWN_RADIUS + 2, want);
            if (cells.isEmpty()) {
                // Couldn't spawn here — drop this anchor from the pool so we
                // don't get stuck cycling. If the pool empties, the remaining
                // queues are silently dropped (lore: "they didn't make it to
                // the city in time"). Better than infinite-looping the spawn.
                anchorPool.remove(anchor);
                if (anchorPool.isEmpty()) break;
                anchorIdx = 0;
                continue;
            }
            Squad squad = null;
            int spawned = 0;
            for (int[] cell : cells) {
                if (source.isEmpty()) break;
                UnitType type = source.poll();
                MechRole mechRole = (type == mechType) ? nextMechRole(mechSpawnIdx++) : null;
                Unit unit = makeDefender("d" + defenderIdx++, type, cell[0], cell[1], mechRole);
                unit.role = UnitRole.PATROL;
                if (squad == null) {
                    int sid = sim.mintSquad(Faction.DEFENDER, unit);
                    squad = sim.getSquad(sid);
                    squad.assignedNode = anchor;
                }
                unit.squadId = squad.id;
                sim.addUnit(unit);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }
    }

    /** Original cluster-spawn behavior — kept around for maps with no tactical layer (legacy UrbanMapGenerator, placeholder gens). Composition follows the roster: mechs first, then red marines, then militia. */
    /**
     * Fallback spawn used when the map has no defender tactical nodes (any
     * mission whose generator skips BspCityGenerator's tactical pass). Mints
     * {@link UnitRole#PATROL} squads of up to {@link DefenderRoster#patrolSquadSize}
     * each, with {@code assignedNode = null} — {@link com.dillon.starsectormarines.battle.ai.goap.actions.PatrolRoute}
     * seeds off {@link Squad#centroidX}/{@code centroidY} when the anchor is
     * null, so the squad wanders from its spawn cluster outward and engages
     * on enemy LOS like the tactical-node patrol path.
     *
     * <p>Type queue order (mechs → elites → militia) matches
     * {@link #allocateDefenders}'s Pass-1/2 ordering so the highest-rank
     * defenders cluster into the first squad rather than scattering.
     */
    private static void spawnLegacyDefenderCluster(BattleSimulation sim, List<int[]> cells, DefenderRoster roster) {
        // Same mech-vs-infantry split as allocateDefenders — each squad
        // drains from a single source queue so mechs and infantry never
        // share membership.
        FactionUnitRoster defRoster = FactionUnitRoster.forFaction(Faction.DEFENDER);
        UnitType mechType = defRoster.mech();
        java.util.Deque<UnitType> mechQueue = new java.util.ArrayDeque<>();
        java.util.Deque<UnitType> infQueue = new java.util.ArrayDeque<>();
        for (int i = 0; i < roster.mechCount; i++)    mechQueue.add(mechType);
        for (int i = 0; i < roster.eliteCount; i++)   infQueue.add(defRoster.elite());
        for (int i = 0; i < roster.militiaCount; i++) infQueue.add(defRoster.infantry());

        int defenderIdx = 0;
        int cellIdx = 0;
        int mechSpawnIdx = 0;
        while ((!mechQueue.isEmpty() || !infQueue.isEmpty()) && cellIdx < cells.size()) {
            java.util.Deque<UnitType> source = !mechQueue.isEmpty() ? mechQueue : infQueue;
            int squadSize = Math.min(roster.patrolSquadSize,
                    Math.min(source.size(), cells.size() - cellIdx));
            Squad squad = null;
            int spawned = 0;
            for (int s = 0; s < squadSize; s++) {
                int[] cell = cells.get(cellIdx++);
                UnitType type = source.poll();
                MechRole mechRole = (type == mechType) ? nextMechRole(mechSpawnIdx++) : null;
                Unit unit = makeDefender("d" + defenderIdx++, type, cell[0], cell[1], mechRole);
                unit.role = UnitRole.PATROL;
                if (squad == null) {
                    int sid = sim.mintSquad(Faction.DEFENDER, unit);
                    squad = sim.getSquad(sid);
                    // No assignedNode — PatrolRoute falls back to the
                    // squad centroid as its wander seed when this is null.
                }
                unit.squadId = squad.id;
                sim.addUnit(unit);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }
    }

    /**
     * Builds a defender Unit with the loadout-state attached for mech types.
     * {@code mechRole} is consumed only when {@code type == HEAVY_MECH} —
     * non-mech callers pass {@code null}.
     */
    private static Unit makeDefender(String id, UnitType type, int x, int y, MechRole mechRole) {
        Unit unit = new Unit(id, Faction.DEFENDER, type, x, y);
        if (type == FactionUnitRoster.forFaction(Faction.DEFENDER).mech()) {
            unit.mech = MechLoadoutState.defaultLoadout(mechRole);
        }
        return unit;
    }

    /**
     * Picks the next mech doctrine slot in spawn order. Round-robin so a
     * battle with N≥2 mechs always has at least one of each role; with a
     * single mech, that mech is LR Support (the more visually distinct
     * doctrine — sits back and lobs LRMs). The commander tier (future)
     * overwrites this stub via {@code ObjectiveAssignment}.
     */
    private static MechRole nextMechRole(int spawnIdx) {
        return (spawnIdx % 2 == 0) ? MechRole.LR_SUPPORT : MechRole.ARMORED_SUPPORT;
    }

    /**
     * Pick walkable cells around {@code (ax, ay)} for spawning a squad — the
     * top {@code count} cells within Manhattan {@code radius} sorted by cover
     * desc, distance asc. Used by the defender allocator at setup time and by
     * {@link com.dillon.starsectormarines.battle.squad.SquadFallbackSystem}
     * when a squad falls back to a sibling tactical node mid-battle.
     *
     * <p>Routes through the live {@link ZoneGraph} so the spawn pool respects
     * the same room-partition the AI tier already uses everywhere else. Three
     * seed cases to handle, all reading off the zone structure:
     *
     * <ol>
     *   <li><b>Indoor walkable seed</b> (compound interior anchor) — seed's
     *       zone is the room. Pool draws from that one zone, bounded to
     *       Manhattan radius. A multi-room building's partition doorway is
     *       its own zone (per {@link com.dillon.starsectormarines.battle.nav.zone.ZoneDetector}),
     *       so cells in the antechamber are in a separate zone and never
     *       leak into the throne-room garrison's pool.</li>
     *   <li><b>Walkable doorway seed</b> (a GATE anchor) — seed's zone is a
     *       1-cell doorway zone. We extend the pool to the doorway's adjacent
     *       zones via the portal graph, so gate defenders still spawn on
     *       both sides of the gap.</li>
     *   <li><b>Unwalkable seed</b> (3×3 wall-mount tower, turret pylon) —
     *       seed has no zone. Walk outward cell-by-cell with walls
     *       transparent until we accumulate every zone reachable within
     *       radius, then draw from all of them. Matches the historical
     *       wall-mounted-tower spawn behavior (cells on both sides of the
     *       wall ring); the cover-based sort handles the defender-side bias.
     *   </li>
     * </ol>
     */
    public static List<int[]> pickCellsNear(NavigationGrid grid, ZoneGraph zones,
                                            int ax, int ay, int radius, int count) {
        java.util.Set<Integer> spawnZones = resolveSpawnZones(grid, zones, ax, ay, radius);
        if (spawnZones.isEmpty()) return Collections.emptyList();

        // Sweep the (2r+1)² rectangle around the seed and pick cells whose
        // zone is in the spawn set. Bounded by the rectangle (not by zone
        // membership iteration) so outdoor anchors with huge zones don't
        // pay O(|zone|) per call — perimeter towers in a 5000-cell
        // courtyard zone now stay O(radius²) like the historical BFS.
        List<int[]> pool = new ArrayList<>();
        for (int y = ay - radius; y <= ay + radius; y++) {
            for (int x = ax - radius; x <= ax + radius; x++) {
                int dist = Math.abs(x - ax) + Math.abs(y - ay);
                if (dist > radius) continue;
                if (!grid.inBounds(x, y)) continue;
                int zid = zones.zoneIdAt(x, y);
                if (zid < 0 || !spawnZones.contains(zid)) continue;
                pool.add(new int[]{x, y, dist});
            }
        }

        pool.sort(Comparator
                .comparingInt((int[] p) -> -grid.getCoverAt(p[0], p[1]))
                .thenComparingInt(p -> p[2]));
        int take = Math.min(count, pool.size());
        List<int[]> out = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            int[] p = pool.get(i);
            out.add(new int[]{p[0], p[1]});
        }
        return out;
    }

    /**
     * Resolve the set of zones {@link #pickCellsNear} draws cells from for a
     * given seed. See that method's javadoc for the three-case rationale.
     */
    private static java.util.Set<Integer> resolveSpawnZones(NavigationGrid grid, ZoneGraph zones,
                                                            int ax, int ay, int radius) {
        java.util.Set<Integer> result = new java.util.LinkedHashSet<>();
        if (!grid.inBounds(ax, ay)) return result;

        int seedZoneId = zones.zoneIdAt(ax, ay);
        if (seedZoneId >= 0) {
            result.add(seedZoneId);
            // Doorway seeds: 1-cell doorway zone; gate-defender pattern needs
            // both adjacent rooms in the pool.
            if (grid.isDoorway(ax, ay)) {
                result.addAll(zones.adjacentZones(seedZoneId));
            }
            return result;
        }

        // Unwalkable seed (wall-mount). Walk outward through walls until
        // every zone reachable within radius is collected. The radius bound
        // keeps this O(radius²) — same envelope as the original
        // pickCellsNear BFS, just collecting zone ids instead of cells.
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{ax, ay, 0});
        seen.add(key(ax, ay));
        int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > radius) continue;
            int zid = zones.zoneIdAt(p[0], p[1]);
            if (zid >= 0) result.add(zid);
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return result;
    }

    /**
     * Scatters {@link #AMBIENT_CIVILIAN_COUNT} non-combatants near residential
     * POIs as map flavor — they panic and flee gunfire via
     * {@code FleeBehavior}. Civilians belong to {@link Faction#CIVILIAN}, which
     * means they don't count toward either side's elimination objective and
     * aren't targeted by combatants. If no residential POIs were carved (rare
     * — small or unusually industrial seeds), this is a no-op.
     */
    private static void spawnAmbientCivilians(BattleSimulation sim, MapResult map, Random rng) {
        List<PointOfInterest> residential = new ArrayList<>();
        for (PointOfInterest poi : map.pointsOfInterest) {
            if (poi.kind == PointOfInterest.Kind.RESIDENTIAL) residential.add(poi);
        }
        if (residential.isEmpty()) return;
        UnitType[] roles = { UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.SCIENTIST };
        Set<Long> claimed = new HashSet<>();
        int spawned = 0;
        int attempts = 0;
        while (spawned < AMBIENT_CIVILIAN_COUNT && attempts < AMBIENT_CIVILIAN_COUNT * 8) {
            attempts++;
            PointOfInterest poi = residential.get(rng.nextInt(residential.size()));
            int[] cell = findCivilianCell(map.grid, poi.anchorCellX, poi.anchorCellY, claimed, rng);
            if (cell == null) continue;
            UnitType type = roles[rng.nextInt(roles.length)];
            Unit u = new Unit("c" + spawned, Faction.CIVILIAN, type, cell[0], cell[1]);
            u.role = UnitRole.FLEE;
            sim.addUnit(u);
            claimed.add(key(cell[0], cell[1]));
            spawned++;
        }
    }

    /**
     * Random walkable cell within {@link #CIVILIAN_SPAWN_RADIUS} BFS-steps of
     * (cx, cy) that isn't already claimed by another civilian. Returns null if
     * the area is fully clogged. Picks via reservoir-style random selection
     * rather than first-found so multiple civilians on the same POI don't all
     * cluster on its anchor cell.
     */
    private static int[] findCivilianCell(NavigationGrid grid, int cx, int cy, Set<Long> claimed, Random rng) {
        List<int[]> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy, 0});
        seen.add(key(cx, cy));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > CIVILIAN_SPAWN_RADIUS) continue;
            if (grid.isWalkable(p[0], p[1]) && !claimed.contains(key(p[0], p[1]))) {
                candidates.add(new int[]{p[0], p[1]});
            }
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * BFS from the marine anchor; keeps the first {@code count} walkable cells
     * that are each at least {@link #LZ_MIN_SEPARATION} from every previously
     * picked LZ. Spreads drops across the marine quadrant instead of stacking
     * them on the anchor. Falls back to the anchor itself if not enough spread
     * cells exist (tight map) — better one stacked LZ than zero shuttles.
     */
    private static List<int[]> pickLandingZones(NavigationGrid grid, int anchorX, int anchorY, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{anchorX, anchorY});
        seen.add(key(anchorX, anchorY));
        int minSepSq = LZ_MIN_SEPARATION * LZ_MIN_SEPARATION;
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (grid.isWalkable(p[0], p[1])) {
                boolean farEnough = true;
                for (int[] prev : picked) {
                    int dx = prev[0] - p[0];
                    int dy = prev[1] - p[1];
                    if (dx * dx + dy * dy < minSepSq) {
                        farEnough = false;
                        break;
                    }
                }
                if (farEnough) picked.add(new int[]{p[0], p[1]});
            }
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        while (picked.size() < count) picked.add(new int[]{anchorX, anchorY});
        return picked;
    }

    /**
     * CONQUEST-specific LZ picker — drops along the beachhead line instead of
     * a tight cluster around the marine anchor. The line runs parallel to the
     * attacker edge at the marine anchor's perpendicular coordinate
     * ({@code anchorY} for SOUTH_TO_NORTH, {@code anchorX} for WEST_TO_EAST),
     * so LZs land inside the beach biome strip. Drops are evenly spaced
     * across the attacker frontage with small per-cell jitter for visual
     * variety; if a target cell is unwalkable (wall, water, dock structure)
     * we slide along the line to the nearest walkable cell. Even spacing
     * implicitly enforces separation — no min-distance gate needed when the
     * map is wide enough.
     */
    private static List<int[]> pickConquestLandingZones(NavigationGrid grid,
                                                        int anchorX, int anchorY,
                                                        int count, TraversalAxis axis,
                                                        Random rng) {
        boolean vertical = (axis == TraversalAxis.WEST_TO_EAST);
        int lineLength = vertical ? grid.getHeight() : grid.getWidth();
        // Leave the corners alone — the very edge of the beach reads as
        // off-map rather than "landing zone". Margin scales with map width.
        int margin = Math.max(6, lineLength / 12);
        int span = Math.max(1, lineLength - 2 * margin);

        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            float t = (count == 1) ? 0.5f : (float) i / (count - 1);
            int along = margin + Math.round(t * span);
            // ±3 cells of jitter so a 3-drop mission doesn't land on the
            // identical t=0/0.5/1 slots every seed. Keeps drops feeling
            // hand-placed rather than mathematically pinned.
            along += rng.nextInt(7) - 3;
            along = Math.max(margin, Math.min(margin + span, along));
            int x = vertical ? anchorX : along;
            int y = vertical ? along : anchorY;
            int[] cell = slideLzAlongLine(grid, x, y, vertical);
            if (cell == null) continue;
            if (!seen.add(key(cell[0], cell[1]))) continue;
            picked.add(cell);
        }
        // Tight maps / line of all-unwalkable can leave us short. Better
        // a stacked LZ on the anchor than zero shuttles.
        while (picked.size() < count) picked.add(new int[]{anchorX, anchorY});
        return picked;
    }

    /**
     * Find the nearest walkable cell on the LZ line, sliding outward from the
     * target along the parallel axis. Returns null only if the entire line is
     * unwalkable, which would be a degenerate map.
     */
    private static int[] slideLzAlongLine(NavigationGrid grid, int x, int y, boolean vertical) {
        if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
        int max = vertical ? grid.getHeight() : grid.getWidth();
        for (int d = 1; d < max; d++) {
            if (vertical) {
                if (grid.inBounds(x, y - d) && grid.isWalkable(x, y - d)) return new int[]{x, y - d};
                if (grid.inBounds(x, y + d) && grid.isWalkable(x, y + d)) return new int[]{x, y + d};
            } else {
                if (grid.inBounds(x - d, y) && grid.isWalkable(x - d, y)) return new int[]{x - d, y};
                if (grid.inBounds(x + d, y) && grid.isWalkable(x + d, y)) return new int[]{x + d, y};
            }
        }
        return null;
    }

    /**
     * Scans walkable cells within {@link #DEFENDER_SPAWN_SCAN_RADIUS} of the
     * anchor, then sorts the pool by cover descending (proximity to anchor
     * breaks ties). Keeps the top {@code count}. Defenders end up tucked into
     * wall edges and building corners — "they prepared the position" emerges
     * from picking which cells they camp, not from stat asymmetry.
     */
    private static List<int[]> pickDefensiveCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> pool = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy, 0});
        seen.add(key(cx, cy));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEFENDER_SPAWN_SCAN_RADIUS) continue;
            if (grid.isWalkable(p[0], p[1])) pool.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        pool.sort(Comparator
                .comparingInt((int[] p) -> -grid.getCoverAt(p[0], p[1])) // higher cover first
                .thenComparingInt(p -> p[2])); // closer to anchor first on cover ties
        List<int[]> picked = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            int[] p = pool.get(i);
            picked.add(new int[]{p[0], p[1]});
        }
        // Backfill if the scan didn't return enough — fall back to plain BFS from
        // the anchor so we never spawn fewer defenders than requested.
        if (picked.size() < count) {
            picked.addAll(pickSpawnCluster(grid, cx, cy, count - picked.size()));
        }
        return picked;
    }

    /** BFS from (cx, cy) over walkable cells, returning the first {@code count} cells in BFS order. */
    private static List<int[]> pickSpawnCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        seen.add(key(cx, cy));
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (!grid.isWalkable(p[0], p[1])) continue;
            picked.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        return picked;
    }

    /**
     * Picks 3-6 random vehicle placements on open outdoor pavement (streets or
     * super-block courtyards). Each placement flags its footprint cells
     * non-walkable on the grid, then recomputes cover for the surrounding ring
     * so cells adjacent to the truck inherit the cover bonus.
     *
     * <p>Vehicle anchors are required to sit on a street or courtyard cell,
     * never on an indoor floor — a truck parked in a living room would read
     * wrong. {@link PlacementGuards#touchesDoorway Doorway-adjacent} cells are
     * excluded so the vehicle doesn't seal a building's only egress (the
     * doorway's perpendicular through-cell is walkable and unflagged, but
     * blocking it traps the interior), and
     * {@link PlacementGuards#wouldPartitionWalkable connectivity} is checked
     * so the truck can't sever a thin walkable strip from the main graph.
     */
    private static List<MapVehicle> stampVehicles(NavigationGrid grid, CellTopology topology, Random rng) {
        int target = VEHICLE_COUNT_MIN + rng.nextInt(VEHICLE_COUNT_MAX - VEHICLE_COUNT_MIN + 1);
        VehicleKind[] kinds = VehicleKind.values();
        List<MapVehicle> placed = new ArrayList<>(target);
        int attempts = 0;
        int maxAttempts = target * 50;
        while (placed.size() < target && attempts < maxAttempts) {
            attempts++;
            VehicleKind kind = kinds[rng.nextInt(kinds.length)];
            int x = rng.nextInt(Math.max(1, grid.getWidth()  - kind.footprintCellsX));
            int y = rng.nextInt(Math.max(1, grid.getHeight() - kind.footprintCellsY));
            if (!canPlaceVehicle(grid, topology, x, y, kind)) continue;
            if (PlacementGuards.wouldPartitionWalkable(
                    grid, x, y, kind.footprintCellsX, kind.footprintCellsY)) continue;
            stampOneVehicle(grid, topology, x, y, kind);
            placed.add(new MapVehicle(kind, x, y));
        }
        return placed;
    }

    private static boolean canPlaceVehicle(NavigationGrid grid, CellTopology topology, int x, int y, VehicleKind kind) {
        for (int dy = 0; dy < kind.footprintCellsY; dy++) {
            for (int dx = 0; dx < kind.footprintCellsX; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!grid.inBounds(cx, cy)) return false;
                if (!grid.isWalkable(cx, cy)) return false;
                if (PlacementGuards.touchesDoorway(grid, cx, cy)) return false;
                if (!topology.isStreet(cx, cy) && !topology.isCourtyard(cx, cy)) return false;
            }
        }
        return true;
    }

    private static void stampOneVehicle(NavigationGrid grid, CellTopology topology, int x, int y, VehicleKind kind) {
        for (int dy = 0; dy < kind.footprintCellsY; dy++) {
            for (int dx = 0; dx < kind.footprintCellsX; dx++) {
                grid.setWalkable(x + dx, y + dy, false);
                topology.setVehicle(x + dx, y + dy, true);
            }
        }
        // Refresh cover for the 1-cell ring around the footprint so units who
        // stand next to a truck pick up the +1 cover bonus on their next
        // firing-position score.
        for (int dy = -1; dy <= kind.footprintCellsY; dy++) {
            for (int dx = -1; dx <= kind.footprintCellsX; dx++) {
                grid.recomputeCoverAt(x + dx, y + dy);
            }
        }
    }

    /**
     * Defense-post turret spawner. Each {@link DefensePost} carries 1-3 turret
     * specs (LIGHT/MEDIUM = 1, LARGE = 2) at cells already stamped by
     * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}
     * as walkable STONE pads — that walkability is a map-gen artifact required
     * by {@link com.dillon.starsectormarines.battle.nav.NavigationGrid#recomputeCoverAt}
     * (which skips non-walkable cells, so a fully-walled turret pad would bake
     * to cover 0). Once the bake is done and the turret unit is in place we
     * flip the cell non-walkable so marines can't path through (or onto) a
     * live emplacement. {@link BattleSimulation#demolishDeadTurrets} flips it
     * back to walkable + rubble on death, so destroyed turrets open up
     * traversal again.
     *
     * <p>Cover is recomputed on the cardinal neighbors so adjacent walkable
     * cells (corner cells around the ring, the middle pad on a LARGE post)
     * pick up the +1 facing cover from the turret now reading as a wall in
     * that direction. The cell itself isn't recomputed — non-walkable cells
     * don't carry valid cover values; the demolition path re-bakes on death.
     */
    private static void spawnDefensePostTurrets(BattleSimulation sim, List<DefensePost> posts) {
        int i = 0;
        int h = 0;
        for (DefensePost post : posts) {
            for (DefensePost.TurretSpec spec : post.turrets) {
                sim.addUnit(new MapTurret("t" + i++, Faction.DEFENDER, spec.kind, spec.cellX, spec.cellY));
                sim.getGrid().setWalkable(spec.cellX, spec.cellY, false);
                sim.getGrid().recomputeCoverAt(spec.cellX + 1, spec.cellY);
                sim.getGrid().recomputeCoverAt(spec.cellX - 1, spec.cellY);
                sim.getGrid().recomputeCoverAt(spec.cellX, spec.cellY + 1);
                sim.getGrid().recomputeCoverAt(spec.cellX, spec.cellY - 1);
            }
            // DRONE_HUB has no turrets — the hub structure occupies the sealed
            // center cell (already flipped non-walkable by the stamper's
            // sealInnerCell call). Spawning the DroneHubUnit here gives it HP
            // and a render target; the drones it'll launch come in a follow-up.
            if (post.tier == DefensePostKind.DRONE_HUB) {
                sim.addUnit(new DroneHubUnit("dh" + h++, Faction.DEFENDER, post.anchorX, post.anchorY));
            }
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
