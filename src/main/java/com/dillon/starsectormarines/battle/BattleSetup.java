package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.TurretMount;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.mapgen.MapGenerator;
import com.dillon.starsectormarines.battle.mapgen.MapResult;
import com.dillon.starsectormarines.battle.mapgen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.objective.EliminateFactionObjective;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /** Default battle grid size (cells). Was 24x16 during the MVP loop; widened for urban combat. */
    public static final int GRID_W = 96;
    public static final int GRID_H = 48;

    private static final int DEFENDER_COUNT = 12;

    /** Three drops × 4 marines/shuttle keeps total marine count at 12 — matches pre-shuttle balance. */
    private static final int SHUTTLE_COUNT = 3;
    /** Sim-seconds between successive shuttle launches. Spaces out drops so the LZs aren't all active at once. */
    private static final float SHUTTLE_DROP_STAGGER_SEC = 1.5f;
    /** Minimum cell-distance between landing zones — avoids stacking all shuttles on the spawn anchor. */
    private static final int LZ_MIN_SEPARATION = 8;
    /** Entry/exit Y offset above the grid (in cells). Long enough that shuttles are visible during their descent. */
    private static final float SHUTTLE_OFFMAP_Y = 8f;

    /** BFS radius around the defender anchor we scan for candidate spawn cells. Larger than {@link #DEFENDER_COUNT} so we have a pool to cherry-pick high-cover cells from. */
    private static final int DEFENDER_SPAWN_SCAN_RADIUS = 14;

    /** How many of the {@link #DEFENDER_COUNT} defenders are stiffening regulars (red marines) instead of militia. The rest are militia. */
    private static final int DEFENDER_ELITE_COUNT = 3;
    /** Total ambient civilians (mix of CIVILIAN/ENGINEER/SCIENTIST) scattered around residential POIs as map flavor. */
    private static final int AMBIENT_CIVILIAN_COUNT = 8;
    /** BFS radius around each residential POI when looking for civilian spawn cells. */
    private static final int CIVILIAN_SPAWN_RADIUS = 5;

    /** Min/max parked vehicles scattered on streets and courtyards. Trucks block pathing + LOS, so they act as movable map terrain. */
    private static final int VEHICLE_COUNT_MIN = 3;
    private static final int VEHICLE_COUNT_MAX = 6;

    /** Min/max defender-side static turrets stamped on streets/courtyards. Vanilla weapon sprites pulled in via {@link com.dillon.starsectormarines.battle.TurretKind}. */
    private static final int TURRET_COUNT_MIN = 2;
    private static final int TURRET_COUNT_MAX = 4;
    /** Min cell-distance between any two turret placements. Stops them from clustering on one block — defenders typically spread emplacements across the line of approach. */
    private static final int TURRET_MIN_SEPARATION = 8;

    /**
     * The active {@link MapGenerator}. {@link BspCityGenerator} produces
     * irregular block-mosaic maps with per-{@link com.dillon.starsectormarines.battle.mapgen.BlockKind}
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
                                                  boolean enemyHasHeavyArmor) {
        MapResult map = MAP_GEN.generate(GRID_W, GRID_H, seed);
        Random rng = new Random(seed);
        // Vehicles stamp before sim construction so the BattleSimulation's
        // zone-graph rebuild sees the final walkability — trucks partition zones.
        List<MapVehicle> vehiclePlacements = stampVehicles(map.grid, map.topology, rng);
        List<TurretPlacement> turretPlacements = stampTurrets(map.grid, map.topology, rng);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        for (MapVehicle v : vehiclePlacements) sim.addVehicle(v);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        spawnTurrets(sim, turretPlacements);

        // Pick charge sites: prefer high-value POIs (lab/comms/depot) in the
        // defender half of the map. Fall back to any POI if not enough qualify.
        List<PointOfInterest> sites = pickChargeSites(map.pointsOfInterest, GRID_W / 2, SABOTAGE_CHARGE_SITES);
        List<ChargeSiteObjective> objectives = new ArrayList<>(sites.size());
        for (PointOfInterest poi : sites) {
            ChargeSiteObjective obj = new ChargeSiteObjective(
                    poi.anchorCellX, poi.anchorCellY,
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
        float topEdgeY = GRID_H;
        int globalDropIdx = 0;
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            float entryX = lzCenterX;
            float entryY = topEdgeY + SHUTTLE_OFFMAP_Y;
            float exitX  = lzCenterX;
            float exitY  = topEdgeY + SHUTTLE_OFFMAP_Y + 4f;
            Shuttle shuttle = new Shuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entryX, entryY,
                    exitX, exitY,
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

        List<int[]> defenderCells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, DEFENDER_COUNT);
        spawnDefenderMix(sim, defenderCells, enemyHasHeavyArmor);
        spawnAmbientCivilians(sim, map, rng);
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

    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor) {
        MapResult map = MAP_GEN.generate(GRID_W, GRID_H, seed);
        Random rng = new Random(seed);
        List<MapVehicle> vehiclePlacements = stampVehicles(map.grid, map.topology, rng);
        List<TurretPlacement> turretPlacements = stampTurrets(map.grid, map.topology, rng);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        for (MapVehicle v : vehiclePlacements) sim.addVehicle(v);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        spawnTurrets(sim, turretPlacements);

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
        float topEdgeY = GRID_H;
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            // Entry directly above the LZ, off the top of the grid. Exit a bit further off to give
            // the departing shuttle a moment of visible climb before it disappears.
            float entryX = lzCenterX;
            float entryY = topEdgeY + SHUTTLE_OFFMAP_Y;
            float exitX  = lzCenterX;
            float exitY  = topEdgeY + SHUTTLE_OFFMAP_Y + 4f;
            Shuttle shuttle = new Shuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entryX, entryY,
                    exitX, exitY,
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

        // Defenders pre-spawn around their anchor, biased to high-cover cells —
        // they prepared the position. Falls back to plain BFS order if the
        // local area is open (e.g., a plaza right at the anchor).
        List<int[]> defenderCells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, DEFENDER_COUNT);
        spawnDefenderMix(sim, defenderCells, enemyHasHeavyArmor);
        spawnAmbientCivilians(sim, map, rng);
        return sim;
    }

    /**
     * Spawns defenders from a pre-chosen cell list, mixing in
     * {@link #DEFENDER_ELITE_COUNT} red-marine elites among the militia. Elites
     * take the first slots (which are the highest-cover positions per
     * {@link #pickDefensiveCluster}'s sort), so the stiffening regulars are
     * tucked into the best firing posts and the militia fills the perimeter.
     *
     * <p>When {@code enemyHasHeavyArmor} is set — meaning the target planet's
     * market produces or demands heavy armaments — the highest-cover slot is
     * swapped for a single {@link UnitType#HEAVY_MECH}. One mech per battle
     * keeps the threat readable; scaling that up is a balance question for
     * later. The remaining elite slots stay red-marines.
     */
    private static void spawnDefenderMix(BattleSimulation sim, List<int[]> cells,
                                         boolean enemyHasHeavyArmor) {
        for (int i = 0; i < cells.size(); i++) {
            int[] p = cells.get(i);
            UnitType type;
            if (enemyHasHeavyArmor && i == 0) {
                type = UnitType.HEAVY_MECH;
            } else if (i < DEFENDER_ELITE_COUNT) {
                type = UnitType.MARINE_RED;
            } else {
                type = UnitType.MILITIA;
            }
            Unit unit = new Unit("d" + i, Faction.DEFENDER, type, p[0], p[1]);
            if (type == UnitType.HEAVY_MECH) {
                unit.mech = MechLoadoutState.defaultLoadout();
            }
            sim.addUnit(unit);
        }
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
     * wrong. Doorway cells are also excluded so we don't accidentally seal
     * a building's only entrance.
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
                if (grid.isDoorway(cx, cy)) return false;
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
     * Picks 2-4 turret placements on outdoor pavement in the defender half of
     * the map ({@code x >= GRID_W/2}), spaced at least {@link #TURRET_MIN_SEPARATION}
     * cells apart. Each placement flags its single mount cell non-walkable, then
     * recomputes cover for the surrounding ring — same pattern as
     * {@link #stampVehicles} on a 1×1 footprint. Returns the kind/cell pairs for
     * the caller to spawn as {@link MapTurret} units after sim construction.
     *
     * <p>Defender-half-only because that's where they'd be staged in lore — a
     * planetary garrison places turrets between their command post and the
     * marine approach axis. Spreading them out matters more than tight cover
     * adjacency: a clustered pair gets flanked by one charge, where two on
     * opposite blocks force the marines to split fire.
     */
    private static List<TurretPlacement> stampTurrets(NavigationGrid grid, CellTopology topology, Random rng) {
        int target = TURRET_COUNT_MIN + rng.nextInt(TURRET_COUNT_MAX - TURRET_COUNT_MIN + 1);
        TurretKind[] kinds = TurretKind.values();
        List<TurretPlacement> placed = new ArrayList<>(target);
        int halfX = grid.getWidth() / 2;
        int minSepSq = TURRET_MIN_SEPARATION * TURRET_MIN_SEPARATION;
        int attempts = 0;
        int maxAttempts = target * 100;
        while (placed.size() < target && attempts < maxAttempts) {
            attempts++;
            int x = halfX + rng.nextInt(Math.max(1, grid.getWidth() - halfX));
            int y = rng.nextInt(grid.getHeight());
            if (!canPlaceTurret(grid, topology, x, y)) continue;
            boolean farEnough = true;
            for (TurretPlacement prev : placed) {
                int dx = prev.cellX - x;
                int dy = prev.cellY - y;
                if (dx * dx + dy * dy < minSepSq) { farEnough = false; break; }
            }
            if (!farEnough) continue;
            TurretKind kind = kinds[rng.nextInt(kinds.length)];
            stampOneTurret(grid, x, y);
            placed.add(new TurretPlacement(kind, x, y));
        }
        return placed;
    }

    private static boolean canPlaceTurret(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        if (!grid.isWalkable(x, y)) return false;
        if (grid.isDoorway(x, y)) return false;
        if (!topology.isStreet(x, y) && !topology.isCourtyard(x, y)) return false;
        return true;
    }

    private static void stampOneTurret(NavigationGrid grid, int x, int y) {
        grid.setWalkable(x, y, false);
        // Don't tag CellTopology.WALL — the wall pass would draw building art
        // here. Don't tag VEHICLE either — that'd trigger the vehicle render
        // path. The renderer's turret pass paints the platform fill explicitly.
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                grid.recomputeCoverAt(x + dx, y + dy);
            }
        }
    }

    private static void spawnTurrets(BattleSimulation sim, List<TurretPlacement> placements) {
        int i = 0;
        for (TurretPlacement p : placements) {
            sim.addUnit(new MapTurret("t" + i++, Faction.DEFENDER, p.kind, p.cellX, p.cellY));
        }
    }

    /** Immutable pair of (kind, anchor cell) emitted by {@link #stampTurrets} and consumed by {@link #spawnTurrets}. Lives here to keep the BattleSetup-internal flow self-contained. */
    private static final class TurretPlacement {
        final TurretKind kind;
        final int cellX;
        final int cellY;

        TurretPlacement(TurretKind kind, int cellX, int cellY) {
            this.kind = kind;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
