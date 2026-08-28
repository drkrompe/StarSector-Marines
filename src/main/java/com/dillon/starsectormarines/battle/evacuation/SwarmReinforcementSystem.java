package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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

/** Maintains rescue-mission swarm pressure with deterministic perimeter waves. */
public final class SwarmReinforcementSystem {

    public static final float WAVE_INTERVAL_SECONDS = 6f;
    public static final float POPULATION_FLOOR_FRACTION = 0.70f;
    public static final int PERIMETER_BAND_CELLS = 6;
    public static final int MIN_CIVILIAN_ENTRY_DISTANCE = 14;
    public static final int MIN_MARINE_ENTRY_DISTANCE = 8;
    private static final int MIN_WAVE_SIZE = 4;
    private static final int MIN_RUNNER_SPACING = 2;

    private final CivilianEvacuationTracker tracker;
    private CivilianEvacuationPlacement placement;
    private int targetPopulation;
    private int populationFloor;
    private int waveSize;
    private long seed;
    private int waveIndex;
    private float accumulator;
    private boolean configured;
    private int snapshotRevision;
    private String snapshotSignature = "";
    private final Map<Long, SwarmPressureSnapshot.Approach> approachByRunner =
            new HashMap<>();
    private List<SwarmPressureSnapshot.WaveIntent> ownedWave = List.of();
    private SwarmPressureSnapshot snapshot;

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
        accumulator = Math.min(WAVE_INTERVAL_SECONDS, accumulator + Math.max(0f, dt));
        int live = liveRunnerCount(sim);
        if (live >= populationFloor || accumulator < WAVE_INTERVAL_SECONDS) {
            refreshSnapshot(sim, live < populationFloor
                    ? "REASSEMBLING_WAVE"
                    : sim.isCivilianShelterProtected()
                    ? "OPENING_SCREEN" : "PRESSURE_COHORT_SCREEN");
            return;
        }

        int requested = Math.min(waveSize, targetPopulation - live);
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
        String signature = phase + "|" + reason + "|" + live + "|"
                + waveIndex + "|" + approachStates + "|" + targetStates
                + "|" + ownedWave;
        if (!signature.equals(snapshotSignature)) {
            snapshotSignature = signature;
            snapshotRevision++;
        }
        snapshot = new SwarmPressureSnapshot(snapshotRevision,
                sim.getSimTickIndex(), phase, reason, targetPopulation,
                populationFloor, live, waveIndex, approachStates,
                targetStates, ownedWave);
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
                && sim.identity().type(unit) == UnitType.SWARM_RUNNER
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
                    && sim.identity().type(unit) == UnitType.SWARM_RUNNER) count++;
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
