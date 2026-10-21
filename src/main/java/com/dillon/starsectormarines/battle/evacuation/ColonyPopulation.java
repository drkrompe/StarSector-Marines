package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The colonists still in the colony outside the shelter: the people the swarm
 * feeds on ({@code colony-breach-nouns.md}, Harvest).
 *
 * <p>They are ordinary ambient civilians — fleeing, not part of the cohort,
 * not counted by the evacuation — spread through the colony's buildings a few
 * to a house, so that whichever precinct a bore comes up in there are bodies at
 * stake from the first eruption. How many is drawn from the seed, inside a
 * band, and the breach snapshot records both how many were placed and how many
 * are still alive.
 */
public final class ColonyPopulation {

    /** Fewest colonists a colony carries. */
    public static final int MIN_COLONISTS = 48;
    /** Most colonists a colony carries. */
    public static final int MAX_COLONISTS = 64;
    /** Most colonists placed in one building, so the population is spread rather than pooled. */
    public static final int PER_BUILDING = 3;
    private static final long SEED_SALT = 0x434F4C4F4E495354L;
    private static final UnitType[] TRADES = {
            UnitType.CIVILIAN, UnitType.CIVILIAN, UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.SCIENTIST};

    private ColonyPopulation() {}

    /** How many colonists this seed's colony carries. */
    public static int countFor(long seed) {
        return MIN_COLONISTS + new Random(seed ^ SEED_SALT).nextInt(MAX_COLONISTS - MIN_COLONISTS + 1);
    }

    /**
     * Spawns the colony's population and returns the ids, in spawn order. A
     * colony with fewer open houses than its draw places what it can.
     */
    public static List<Long> install(BattleSimulation sim, List<PointOfInterest> places,
                                     CivilianEvacuationPlacement placement, long seed) {
        int wanted = countFor(seed);
        Random rng = new Random(seed ^ SEED_SALT ^ 0x7472616465L);
        NavigationGrid grid = sim.getGrid();
        int clearance = ColonyEruptionPlan.shelterClearance(grid.getWidth(), grid.getHeight());

        List<PointOfInterest> houses = new ArrayList<>();
        for (PointOfInterest place : places) {
            if (place == null || place.kind == PointOfInterest.Kind.LANDING_SITE) continue;
            if (Math.abs(place.interiorAnchorX - placement.shelterX)
                    + Math.abs(place.interiorAnchorY - placement.shelterY) < clearance) continue;
            if (sim.isInsideRescueOpeningProtectedZone(place.interiorAnchorX, place.interiorAnchorY)) continue;
            if (insideRedoubt(sim, place)) continue;
            houses.add(place);
        }
        houses.sort(Comparator
                .comparingLong((PointOfInterest p) -> priority(seed, grid.index(p.interiorAnchorX, p.interiorAnchorY)))
                .thenComparingInt(p -> grid.index(p.interiorAnchorX, p.interiorAnchorY)));

        List<List<int[]>> rooms = new ArrayList<>();
        for (PointOfInterest house : houses) rooms.add(openCells(sim, house, seed));
        List<Long> ids = new ArrayList<>();
        // Round-robin, one per house per pass, so every house gets someone before any gets a second.
        for (int pass = 0; pass < PER_BUILDING && ids.size() < wanted; pass++) {
            for (int i = 0; i < houses.size() && ids.size() < wanted; i++) {
                List<int[]> cells = rooms.get(i);
                if (pass >= cells.size()) continue;
                int[] cell = cells.get(pass);
                UnitType trade = TRADES[rng.nextInt(TRADES.length)];
                ids.add(sim.spawn(new EntitySpec("Colonist " + (ids.size() + 1), Faction.CIVILIAN, trade,
                        cell[0], cell[1]).role(UnitRole.FLEE)));
            }
        }
        return List.copyOf(ids);
    }

    /** Whether a place is one of the redoubts, whose people are the cohort's rather than the town's. */
    private static boolean insideRedoubt(BattleSimulation sim, PointOfInterest place) {
        for (CivilianRedoubt redoubt : sim.getCivilianRedoubts()) {
            if (redoubt.contains(place.interiorAnchorX, place.interiorAnchorY, 0)) return true;
        }
        return false;
    }

    /** A house's walkable interior cells in seeded order, one per occupant. */
    private static List<int[]> openCells(BattleSimulation sim, PointOfInterest house, long seed) {
        NavigationGrid grid = sim.getGrid();
        int building = sim.getTopology().getBuildingId(house.interiorAnchorX, house.interiorAnchorY);
        List<int[]> cells = new ArrayList<>();
        for (int y = house.top + 1; y < house.bottom; y++) {
            for (int x = house.left + 1; x < house.right; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                if (building > 0 && sim.getTopology().getBuildingId(x, y) != building) continue;
                if ((sim.getOccupancyMap()[grid.index(x, y)] & 0xFF) != 0) continue;
                cells.add(new int[]{x, y});
            }
        }
        cells.sort(Comparator
                .comparingLong((int[] c) -> priority(seed ^ 0x5A5AL, grid.index(c[0], c[1])))
                .thenComparingInt(c -> grid.index(c[0], c[1])));
        return cells;
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
