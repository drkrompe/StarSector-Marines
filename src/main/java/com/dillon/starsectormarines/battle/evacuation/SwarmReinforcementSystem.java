package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    public SwarmReinforcementSystem(CivilianEvacuationTracker tracker) {
        if (tracker == null) throw new IllegalArgumentException("tracker is required");
        this.tracker = tracker;
    }

    /** Configures one rescue battle; later calls fail closed. */
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
        if (!configured || tracker.isSealed() || tracker.activeCount() == 0) return;
        accumulator = Math.min(WAVE_INTERVAL_SECONDS, accumulator + Math.max(0f, dt));
        int live = liveRunnerCount(sim);
        if (live >= populationFloor || accumulator < WAVE_INTERVAL_SECONDS) return;

        int requested = Math.min(waveSize, targetPopulation - live);
        if (requested <= 0) return;
        int spawned = spawnWave(sim, requested);
        accumulator = 0f;
        if (spawned > 0) waveIndex++;
    }

    public boolean isConfigured() {
        return configured;
    }

    public int targetPopulation() {
        return targetPopulation;
    }

    private int spawnWave(BattleSimulation sim, int requested) {
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

        List<Integer> selected = new ArrayList<>();
        for (int cell : candidates) {
            if (!spacedFromSelected(cell, selected, grid.getWidth())) continue;
            selected.add(cell);
            if (selected.size() == requested) break;
        }
        for (int i = 0; i < selected.size(); i++) {
            int cell = selected.get(i);
            int x = cell % grid.getWidth();
            int y = cell / grid.getWidth();
            sim.spawn(new EntitySpec(
                    "Roving Swarm Runner " + (waveIndex * waveSize + i + 1),
                    Faction.DEFENDER, UnitType.SWARM_RUNNER, x, y)
                    .role(UnitRole.SWARM_PRESSURE));
        }
        return selected.size();
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
