package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The swarm director (`colony-breach-nouns.md`, Swarm director): maintains
 * rescue-mission swarm pressure.
 *
 * <p>Until a Colony Breach's tremor it restores its population floor with
 * deterministic perimeter waves, at a fraction of the floor while the colony
 * seeps. From the tremor on ({@link #beginBoreSourcing}) spawning has a
 * source: every body comes out of a living bore, as a timed surge sized by the
 * bore's tier and the biomass it was fed, released as a tight stream from its
 * mouth. The population target is then the sum of the living bores'
 * capacities, so a collapsed bore takes its share with it for good and a
 * battle whose bores are all collapsed spawns nothing more. Strains come from
 * {@link BoreStrainTable} by the surging bore's tier, every body through
 * {@link SwarmStrains}.
 */
public final class SwarmReinforcementSystem {

    public static final float WAVE_INTERVAL_SECONDS = 6f;
    public static final float POPULATION_FLOOR_FRACTION = 0.70f;
    public static final int PERIMETER_BAND_CELLS = 6;
    public static final int MIN_CIVILIAN_ENTRY_DISTANCE = 14;
    public static final int MIN_MARINE_ENTRY_DISTANCE = 8;
    /**
     * The share of the population floor the director restores during Colony
     * Breach's opening seepage: a thin trickle at the perimeter until the
     * tremor, then the full floor.
     */
    public static final float SEEPAGE_FLOOR_FRACTION = 0.40f;
    private static final int MIN_WAVE_SIZE = 4;
    private static final int MIN_RUNNER_SPACING = 2;
    /** Ticks between two bodies of one surge leaving the mouth: a stream, not a crowd. */
    public static final int SURGE_RELEASE_SPACING_TICKS = 2;
    /** A due surge waits until at least this share of it fits under the target. */
    public static final float SURGE_MIN_FIT = 0.5f;
    /** Ticks a held surge waits before it asks again. */
    private static final int SURGE_RECHECK_TICKS = 30;

    /** One surge on its way out of a bore's mouth. */
    private static final class Release {
        final SwarmBore bore;
        final BoreTier tier;
        final int surgeIndex;
        final int size;
        int released;
        int nextTick;

        Release(SwarmBore bore, int surgeIndex, int size, int tick) {
            this.bore = bore;
            this.tier = bore.tier();
            this.surgeIndex = surgeIndex;
            this.size = size;
            this.nextTick = tick;
        }
    }

    private final CivilianEvacuationTracker tracker;
    private CivilianEvacuationPlacement placement;
    private int targetPopulation;
    private int populationFloor;
    private int waveSize;
    private long seed;
    private int waveIndex;
    private float accumulator;
    private boolean configured;
    private boolean seepage;
    private int snapshotRevision;
    private String snapshotSignature = "";
    private final Map<Long, SwarmPressureSnapshot.Approach> approachByRunner =
            new HashMap<>();
    private List<SwarmPressureSnapshot.WaveIntent> ownedWave = List.of();
    private SwarmPressureSnapshot snapshot;
    private SwarmBores bores;
    private boolean boreSourcing;
    private int boreTarget;
    private final List<Release> releases = new ArrayList<>();
    private List<SwarmPressureSnapshot.WaveIntent> surgeWave = new ArrayList<>();

    public SwarmReinforcementSystem(CivilianEvacuationTracker tracker) {
        if (tracker == null) throw new IllegalArgumentException("tracker is required");
        this.tracker = tracker;
    }

    /** Configures one rescue battle; later calls fail closed. */
    public boolean configure(CivilianEvacuationPlacement placement,
                             int targetPopulation, long seed,
                             BattleSimulation sim) {
        if (sim == null || !configure(placement, targetPopulation, seed)) {
            return false;
        }
        ownedWave = captureOpeningWave(sim);
        refreshSnapshot(sim, "OPENING_PERIMETER");
        return true;
    }

    /** Test/setup compatibility overload; the first tick publishes its picture. */
    public boolean configure(CivilianEvacuationPlacement placement,
                             int targetPopulation, long seed) {
        if (configured || placement == null || targetPopulation <= 0) return false;
        this.placement = placement;
        this.targetPopulation = targetPopulation;
        this.populationFloor = Math.max(1,
                (int) Math.ceil(targetPopulation * POPULATION_FLOOR_FRACTION));
        this.waveSize = Math.max(MIN_WAVE_SIZE,
                (int) Math.ceil(targetPopulation * 0.25f));
        this.seed = seed;
        configured = true;
        return true;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (!configured) return;
        if (snapshot == null) {
            ownedWave = captureOpeningWave(sim);
            refreshSnapshot(sim, "OPENING_PERIMETER");
        }
        if (tracker.isSealed() || tracker.activeCount() == 0) {
            refreshSnapshot(sim, "OBJECTIVE_TERMINAL");
            return;
        }
        if (boreSourcing) {
            refreshSnapshot(sim, tickBores(sim));
            return;
        }
        accumulator = Math.min(WAVE_INTERVAL_SECONDS, accumulator + Math.max(0f, dt));
        int live = liveRunnerCount(sim);
        int floor = activeFloor();
        if (live >= floor || accumulator < WAVE_INTERVAL_SECONDS) {
            refreshSnapshot(sim, live < floor
                    ? "REASSEMBLING_WAVE"
                    : seepage ? "PERIMETER_SEEPAGE"
                    : sim.isCivilianShelterProtected()
                    ? "OPENING_SCREEN" : "PRESSURE_COHORT_SCREEN");
            return;
        }

        int ceiling = seepage ? floor : targetPopulation;
        int requested = Math.min(waveSize, ceiling - live);
        if (requested <= 0) {
            refreshSnapshot(sim, "PRESSURE_POPULATION_MET");
            return;
        }
        List<SwarmPressureSnapshot.WaveIntent> spawned = spawnWave(sim, requested);
        accumulator = 0f;
        if (!spawned.isEmpty()) {
            waveIndex++;
            ownedWave = List.copyOf(spawned);
        }
        refreshSnapshot(sim, spawned.isEmpty()
                ? "NO_LEGAL_PERIMETER_ENTRY" : "RESTORE_PRESSURE_FLOOR");
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Starts the opening perimeter seepage: the floor is restored only to a fraction of itself. */
    public void beginSeepage() {
        seepage = true;
    }

    /** Ends the seepage; the director restores its full floor from here on. Idempotent. */
    public void endSeepage() {
        seepage = false;
    }

    /** Whether the director is still in its opening seepage. */
    public boolean isSeeping() {
        return seepage;
    }

    /**
     * From here on every body comes out of a living bore in {@code bores}; the
     * perimeter is closed. Called at the tremor. Idempotent.
     */
    public void beginBoreSourcing(SwarmBores bores) {
        if (bores == null) throw new IllegalArgumentException("bore sourcing needs the battle's bores");
        this.bores = bores;
        boreSourcing = true;
        seepage = false;
    }

    /** Whether spawning comes only from bores. */
    public boolean isBoreSourcing() {
        return boreSourcing;
    }

    /** The population target the living bores hold the field at: their capacities summed. */
    public static int borePopulationTarget(List<SwarmBore> bores) {
        int target = 0;
        for (SwarmBore bore : bores) if (!bore.collapsed()) target += bore.tier().capacity;
        return target;
    }

    /**
     * One bore-sourced tick: streams the surges already leaving their mouths,
     * then starts a surge at each living bore whose clock is due and whose
     * pulse fits under the target. Returns the snapshot reason.
     */
    private String tickBores(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        List<SwarmBore> all = bores.bores();
        boreTarget = borePopulationTarget(all);
        boolean released = false;
        for (int i = 0; i < releases.size(); ) {
            Release release = releases.get(i);
            if (release.bore.collapsed()) {
                releases.remove(i);
                continue;
            }
            if (tick >= release.nextTick && releaseOne(sim, release, tick)) released = true;
            if (release.released >= release.size) releases.remove(i);
            else i++;
        }
        int live = liveRunnerCount(sim);
        int inFlight = 0;
        for (Release release : releases) inFlight += release.size - release.released;
        boolean surged = false;
        for (SwarmBore bore : all) {
            if (!bore.surgeDue(tick) || releasing(bore)) continue;
            int headroom = boreTarget - live - inFlight;
            int size = bore.surgeSize();
            if (headroom < Math.max(1, (int) Math.ceil(size * SURGE_MIN_FIT))) {
                bore.deferSurge(tick + SURGE_RECHECK_TICKS);
                continue;
            }
            int bodies = Math.min(size, headroom);
            if (!surged) surgeWave = new ArrayList<>();
            releases.add(new Release(bore, bore.surges(), bodies, tick));
            float jitter = 2f * SwarmBores.unit(SwarmBores.mix(
                    seed ^ (bore.number() * 0x9E3779B97F4A7C15L) ^ (bore.surges() * 0xC2B2AE3D27D4EB4FL))) - 1f;
            bore.surged(tick, jitter);
            bores.surged(bore, tick);
            inFlight += bodies;
            waveIndex++;
            surged = true;
        }
        if (surged || released) ownedWave = List.copyOf(surgeWave);
        if (surged || released) return "BORE_SURGE";
        if (all.isEmpty()) return "AWAITING_ERUPTION";
        if (bores.livingCount() == 0) return "NO_LIVING_BORE";
        if (live >= boreTarget) return "PRESSURE_POPULATION_MET";
        return releases.isEmpty() ? "BORES_QUIET" : "BORE_SURGE";
    }

    private boolean releasing(SwarmBore bore) {
        for (Release release : releases) if (release.bore == bore) return true;
        return false;
    }

    /** Puts the next body of a surge on the first free mouth cell, or waits a spacing for one. */
    private boolean releaseOne(BattleSimulation sim, Release release, int tick) {
        release.nextTick = tick + SURGE_RELEASE_SPACING_TICKS;
        NavigationGrid grid = sim.getGrid();
        byte[] occupancy = sim.getOccupancyMap();
        List<int[]> mouth = SwarmBores.mouthCells(grid, release.bore);
        int[] cell = null;
        for (int i = 0; i < mouth.size(); i++) {
            int[] candidate = mouth.get((release.released + i) % mouth.size());
            if ((occupancy[grid.index(candidate[0], candidate[1])] & 0xFF) == 0) {
                cell = candidate;
                break;
            }
        }
        if (cell == null) return false;
        long roll = SwarmBores.mix(seed ^ (release.bore.number() * 0x9E3779B97F4A7C15L)
                ^ ((long) release.surgeIndex << 20) ^ release.released);
        SwarmBore bore = release.bore;
        boolean owed = BoreStrainTable.owesMatriarch(bore.mother(), release.tier, bore.matriarchReleased());
        BoreStrainTable.Slot slot = BoreStrainTable.pick(release.tier, roll, owed);
        String strain = slot.name().charAt(0) + slot.name().substring(1).toLowerCase();
        long body = SwarmStrains.spawn(sim, "Bore " + release.bore.beatKey() + " " + strain + " "
                + (release.bore.bodiesSpawned() + 1), slot.unitType, cell[0], cell[1]);
        release.released++;
        release.bore.released();
        if (owed) bores.matriarchEmerged(bore, tick);
        SwarmPressureSnapshot.Approach approach = approach(cell[0], cell[1], grid.getWidth(), grid.getHeight());
        approachByRunner.put(body, approach);
        surgeWave.add(new SwarmPressureSnapshot.WaveIntent(body, approach, cell[0], cell[1],
                SwarmPressureSnapshot.TargetContext.ROAMING, "BORE_SURGE_" + release.bore.beatKey()));
        return true;
    }

    private int activeFloor() {
        return seepage
                ? Math.max(1, (int) Math.ceil(populationFloor * SEEPAGE_FLOOR_FRACTION))
                : populationFloor;
    }

    public int targetPopulation() {
        return targetPopulation;
    }

    public SwarmPressureSnapshot snapshot() { return snapshot; }

    private List<SwarmPressureSnapshot.WaveIntent> spawnWave(
            BattleSimulation sim, int requested) {
        NavigationGrid grid = sim.getGrid();
        boolean[] reachable = SwarmDefenseRoster.reachableFromShelter(grid, placement);
        byte[] occupancy = sim.getOccupancyMap();
        List<Integer> candidates = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int edgeDistance = Math.min(Math.min(x, grid.getWidth() - 1 - x),
                        Math.min(y, grid.getHeight() - 1 - y));
                int cell = grid.index(x, y);
                if (edgeDistance > PERIMETER_BAND_CELLS
                        || !reachable[cell]
                        || (occupancy[cell] & 0xFF) != 0
                        || SwarmDefenseRoster.insideShelterZone(x, y, placement)
                        || SwarmDefenseRoster.insideLiftZone(x, y, placement)
                        || tooCloseToProtectedUnit(x, y, sim)) {
                    continue;
                }
                candidates.add(cell);
            }
        }
        long waveSeed = seed ^ ((long) waveIndex * 0x9E3779B97F4A7C15L);
        candidates.sort(Comparator
                .comparingLong((Integer cell) -> priority(waveSeed, cell))
                .thenComparingInt(Integer::intValue));

        List<Integer> selected = selectAcrossApproaches(candidates, requested,
                grid.getWidth(), grid.getHeight());
        List<SwarmPressureSnapshot.WaveIntent> intents = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            int cell = selected.get(i);
            int x = cell % grid.getWidth();
            int y = cell / grid.getWidth();
            long runner = sim.spawn(new EntitySpec(
                    "Roving Swarm Runner " + (waveIndex * waveSize + i + 1),
                    Faction.DEFENDER, UnitType.SWARM_RUNNER, x, y)
                    .role(UnitRole.SWARM_PRESSURE));
            intents.add(new SwarmPressureSnapshot.WaveIntent(runner,
                    approach(x, y, grid.getWidth(), grid.getHeight()), x, y,
                    sim.isCivilianShelterProtected()
                            ? SwarmPressureSnapshot.TargetContext.ROAMING
                            : SwarmPressureSnapshot.TargetContext.MARINE_SCREEN,
                    "RESTORE_PRESSURE_FLOOR"));
            approachByRunner.put(runner,
                    approach(x, y, grid.getWidth(), grid.getHeight()));
        }
        return List.copyOf(intents);
    }

    private List<Integer> selectAcrossApproaches(
            List<Integer> candidates, int requested, int width, int height) {
        Map<SwarmPressureSnapshot.Approach, List<Integer>> byApproach =
                new EnumMap<>(SwarmPressureSnapshot.Approach.class);
        for (SwarmPressureSnapshot.Approach approach
                : SwarmPressureSnapshot.Approach.values()) {
            byApproach.put(approach, new ArrayList<>());
        }
        for (int cell : candidates) {
            byApproach.get(approach(cell % width, cell / width, width, height))
                    .add(cell);
        }
        int[] cursors = new int[SwarmPressureSnapshot.Approach.values().length];
        List<Integer> selected = new ArrayList<>();
        int start = Math.floorMod(waveIndex,
                SwarmPressureSnapshot.Approach.values().length);
        boolean progressed = true;
        while (selected.size() < requested && progressed) {
            progressed = false;
            for (int offset = 0;
                 offset < SwarmPressureSnapshot.Approach.values().length
                         && selected.size() < requested; offset++) {
                SwarmPressureSnapshot.Approach side =
                        SwarmPressureSnapshot.Approach.values()[
                                (start + offset)
                                        % SwarmPressureSnapshot.Approach.values().length];
                List<Integer> cells = byApproach.get(side);
                int cursor = cursors[side.ordinal()];
                while (cursor < cells.size()
                        && !spacedFromSelected(cells.get(cursor), selected,
                        width)) cursor++;
                cursors[side.ordinal()] = cursor + 1;
                if (cursor >= cells.size()) continue;
                selected.add(cells.get(cursor));
                progressed = true;
            }
        }
        return selected;
    }

    private List<SwarmPressureSnapshot.WaveIntent> captureOpeningWave(
            BattleSimulation sim) {
        List<SwarmPressureSnapshot.WaveIntent> result = new ArrayList<>();
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long runner = sim.liveUnitAt(i);
            if (!isOwnedRunner(runner, sim)) continue;
            int x = sim.world().cellX(runner);
            int y = sim.world().cellY(runner);
            result.add(new SwarmPressureSnapshot.WaveIntent(runner,
                    approach(x, y, width, height), x, y,
                    SwarmPressureSnapshot.TargetContext.ROAMING,
                    "OPENING_PERIMETER"));
            approachByRunner.put(runner, approach(x, y, width, height));
        }
        result.sort(Comparator.comparingLong(
                SwarmPressureSnapshot.WaveIntent::runnerId));
        return List.copyOf(result);
    }

    private void refreshSnapshot(BattleSimulation sim, String reason) {
        SwarmPressureSnapshot.Phase phase = tracker.isSealed()
                || tracker.activeCount() == 0
                ? SwarmPressureSnapshot.Phase.TERMINAL
                : sim.isCivilianShelterProtected()
                ? SwarmPressureSnapshot.Phase.OPENING
                : SwarmPressureSnapshot.Phase.COHORT_RELEASED;
        Map<SwarmPressureSnapshot.Approach, Integer> approaches =
                new EnumMap<>(SwarmPressureSnapshot.Approach.class);
        Map<SwarmPressureSnapshot.TargetContext, Integer> targets =
                new EnumMap<>(SwarmPressureSnapshot.TargetContext.class);
        int live = 0;
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long runner = sim.liveUnitAt(i);
            if (!isOwnedRunner(runner, sim)) continue;
            live++;
            SwarmPressureSnapshot.Approach assigned =
                    approachByRunner.computeIfAbsent(runner,
                            ignored -> approach(sim.world().cellX(runner),
                                    sim.world().cellY(runner), width, height));
            approaches.merge(assigned, 1, Integer::sum);
            targets.merge(targetContext(runner, sim), 1, Integer::sum);
        }
        List<SwarmPressureSnapshot.ApproachState> approachStates =
                new ArrayList<>();
        for (SwarmPressureSnapshot.Approach side
                : SwarmPressureSnapshot.Approach.values()) {
            int waveRunners = 0;
            for (SwarmPressureSnapshot.WaveIntent intent : ownedWave) {
                if (intent.approach() == side
                        && sim.resolveUnit(intent.runnerId()) != 0L) {
                    waveRunners++;
                }
            }
            approachStates.add(new SwarmPressureSnapshot.ApproachState(side,
                    approaches.getOrDefault(side, 0), waveRunners));
        }
        List<SwarmPressureSnapshot.TargetState> targetStates = new ArrayList<>();
        for (SwarmPressureSnapshot.TargetContext context
                : SwarmPressureSnapshot.TargetContext.values()) {
            targetStates.add(new SwarmPressureSnapshot.TargetState(context,
                    targets.getOrDefault(context, 0)));
        }
        List<SwarmPressureSnapshot.BoreState> boreStates = boreStates(sim);
        StringBuilder boreSignature = new StringBuilder();
        for (SwarmPressureSnapshot.BoreState bore : boreStates) {
            boreSignature.append(bore.key()).append(':').append(bore.tier()).append(':')
                    .append(bore.alive()).append(':').append(bore.surges()).append(':')
                    .append(bore.feeds()).append(':').append((int) (bore.integrity() * 10f)).append(':')
                    .append(bore.sporeColonies()).append(';');
        }
        String signature = phase + "|" + reason + "|" + live + "|"
                + waveIndex + "|" + approachStates + "|" + targetStates
                + "|" + ownedWave + "|" + boreSignature;
        if (!signature.equals(snapshotSignature)) {
            snapshotSignature = signature;
            snapshotRevision++;
        }
        snapshot = new SwarmPressureSnapshot(snapshotRevision,
                sim.getSimTickIndex(), phase, reason,
                boreSourcing ? boreTarget : targetPopulation,
                populationFloor, live, waveIndex, approachStates,
                targetStates, ownedWave, boreSourcing, boreStates);
    }

    private List<SwarmPressureSnapshot.BoreState> boreStates(BattleSimulation sim) {
        if (bores == null) return List.of();
        List<SwarmPressureSnapshot.BoreState> states = new ArrayList<>();
        for (SwarmBore bore : bores.bores()) {
            long id = bore.entityId();
            boolean alive = !bore.collapsed() && sim.resolveUnit(id) != 0L;
            float integrity = 0f;
            if (alive) {
                StructureDef structure = sim.turretState().structure(id);
                integrity = (sim.world().hp(id) + sim.world().armor(id))
                        / (structure.maxStructure + structure.armorCapacity);
            }
            states.add(new SwarmPressureSnapshot.BoreState(bore.beatKey(), bore.mother(), id,
                    bore.x(), bore.y(), bore.tier(), alive, integrity, bore.growth(),
                    bore.biomassFed(), bore.feeds(), bore.surges(), bore.bodiesSpawned(),
                    SwarmBores.livingColonies(sim, bore), bore.nextSurgeTick(), bore.eruptedTick(), bore.collapsedTick()));
        }
        return states;
    }

    private SwarmPressureSnapshot.TargetContext targetContext(
            long runner, BattleSimulation sim) {
        long target = sim.resolveUnit(sim.combat().targetId(runner));
        if (target == 0L) return SwarmPressureSnapshot.TargetContext.ROAMING;
        if (tracker.state(target) == CivilianEvacuationTracker.State.ACTIVE) {
            return SwarmPressureSnapshot.TargetContext.COHORT_CONTACT;
        }
        return SwarmPressureSnapshot.TargetContext.MARINE_SCREEN;
    }

    private static boolean isOwnedRunner(long unit, BattleSimulation sim) {
        return sim.identity().faction(unit) == Faction.DEFENDER
                && sim.identity().type(unit).isSwarm()
                && sim.role().role(unit) == UnitRole.SWARM_PRESSURE;
    }

    private static SwarmPressureSnapshot.Approach approach(
            int x, int y, int width, int height) {
        int north = y;
        int east = width - 1 - x;
        int south = height - 1 - y;
        int west = x;
        int minimum = Math.min(Math.min(north, east), Math.min(south, west));
        if (north == minimum) return SwarmPressureSnapshot.Approach.NORTH;
        if (east == minimum) return SwarmPressureSnapshot.Approach.EAST;
        if (south == minimum) return SwarmPressureSnapshot.Approach.SOUTH;
        return SwarmPressureSnapshot.Approach.WEST;
    }

    private boolean tooCloseToProtectedUnit(int x, int y, BattleSimulation sim) {
        int civilianDistanceSquared = MIN_CIVILIAN_ENTRY_DISTANCE
                * MIN_CIVILIAN_ENTRY_DISTANCE;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long civilian = tracker.entityIdAt(i);
            if (tracker.state(civilian) != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(civilian) == 0L) continue;
            if (distanceSquared(x, y, civilian, sim) < civilianDistanceSquared) return true;
        }
        int marineDistanceSquared = MIN_MARINE_ENTRY_DISTANCE
                * MIN_MARINE_ENTRY_DISTANCE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            if (distanceSquared(x, y, unit, sim) < marineDistanceSquared) return true;
        }
        return false;
    }

    private static int liveRunnerCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.DEFENDER
                    && sim.identity().type(unit).isSwarm()) count++;
        }
        return count;
    }

    private static float distanceSquared(int x, int y, long unit,
                                         BattleSimulation sim) {
        float dx = x + 0.5f - sim.world().x(unit);
        float dy = y + 0.5f - sim.world().y(unit);
        return dx * dx + dy * dy;
    }

    private static boolean spacedFromSelected(int cell, List<Integer> selected,
                                              int width) {
        int x = cell % width;
        int y = cell / width;
        for (int other : selected) {
            int dx = x - other % width;
            int dy = y - other / width;
            if (dx * dx + dy * dy < MIN_RUNNER_SPACING * MIN_RUNNER_SPACING) {
                return false;
            }
        }
        return true;
    }

    private static long priority(long seed, int cell) {
        long value = seed ^ (cell * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
