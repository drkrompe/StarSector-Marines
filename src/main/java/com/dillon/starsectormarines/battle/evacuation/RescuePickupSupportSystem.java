package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Holds a capped local-militia line around the rescue pickup and dispatches a
 * visible replacement shuttle when casualties pull that line below strength.
 */
public final class RescuePickupSupportSystem {

    public static final int TARGET_GUARDS = 8;
    public static final int REINFORCEMENT_FLOOR = 5;
    public static final int WAVE_SIZE = 4;
    public static final float WAVE_INTERVAL_SECONDS = 12f;
    private static final int SQUAD_SIZE = 4;
    private static final int INNER_PERIMETER_RADIUS = 3;
    private static final int OUTER_PERIMETER_RADIUS = 6;
    private static final int MIN_GUARD_SPACING = 2;

    private final CivilianEvacuationTracker tracker;
    private CivilianEvacuationPlacement placement;
    private float reinforcementLzX;
    private float reinforcementLzY;
    private float entryX;
    private float entryY;
    private float exitX;
    private float exitY;
    private float accumulator = WAVE_INTERVAL_SECONDS;
    private boolean configured;

    public RescuePickupSupportSystem(CivilianEvacuationTracker tracker) {
        if (tracker == null) throw new IllegalArgumentException("tracker is required");
        this.tracker = tracker;
    }

    /** Installs the standing line and arms the casualty-driven shuttle reserve. */
    public boolean configure(CivilianEvacuationPlacement placement,
                             float reinforcementLzX, float reinforcementLzY,
                             float entryX, float entryY,
                             float exitX, float exitY,
                             BattleSimulation sim) {
        if (configured || placement == null || sim == null) return false;
        this.placement = placement;
        this.reinforcementLzX = reinforcementLzX;
        this.reinforcementLzY = reinforcementLzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        configured = true;
        installInitialLine(sim);
        return true;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (!configured || tracker.isSealed() || tracker.activeCount() == 0) return;
        accumulator = Math.min(WAVE_INTERVAL_SECONDS,
                accumulator + Math.max(0f, dt));
        int live = liveGuardCount(sim);
        int inbound = inboundGuardCount(sim);
        if (live + inbound >= REINFORCEMENT_FLOOR
                || accumulator < WAVE_INTERVAL_SECONDS) return;

        int requested = Math.min(WAVE_SIZE, TARGET_GUARDS - live - inbound);
        if (requested <= 0) return;
        dispatchShuttle(sim, requested);
        accumulator = 0f;
    }

    public boolean isConfigured() {
        return configured;
    }

    public int liveGuardCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(unit)) continue;
            Squad squad = sim.getSquad(sim.squad().squadId(unit));
            if (squad != null && squad.rescuePickupGuard) count++;
        }
        return count;
    }

    private int inboundGuardCount(BattleSimulation sim) {
        int count = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission == null || !mission.rescueMilitiaTransport
                    || mission.state == ShuttleState.GONE) continue;
            count += mission.marinesRemaining;
        }
        return count;
    }

    private void dispatchShuttle(BattleSimulation sim, int requested) {
        long shuttle = sim.spawnShuttle(
                ShuttleType.AEROSHUTTLE, Faction.MARINE,
                reinforcementLzX, reinforcementLzY,
                entryX, entryY, exitX, exitY, 0f);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.marinesRemaining = requested;
        mission.deboardUnitType = UnitType.MILITIA;
        mission.rescueMilitiaTransport = true;
        mission.rescueGuardX = placement.liftX;
        mission.rescueGuardY = placement.liftY;
    }

    private void installInitialLine(BattleSimulation sim) {
        List<Integer> cells = perimeterCells(sim);
        int installed = Math.min(TARGET_GUARDS, cells.size());
        int next = 0;
        while (next < installed) {
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.MILITIA);
            Squad squad = sim.getSquad(squadId);
            squad.rescuePickupGuard = true;
            squad.assignedObjective = ObjectiveAssignment.escort(
                    squad.id, placement.liftX, placement.liftY);
            int end = Math.min(installed, next + SQUAD_SIZE);
            for (; next < end; next++) {
                int cell = cells.get(next);
                int x = cell % sim.getGrid().getWidth();
                int y = cell / sim.getGrid().getWidth();
                long unit = sim.spawn(new EntitySpec(
                        "Local Militia " + (next + 1), Faction.MARINE,
                        UnitType.MILITIA, x, y).squad(squadId));
                squad.originalSize++;
                if (squad.leaderId == 0L) squad.leaderId = unit;
            }
        }
    }

    private List<Integer> perimeterCells(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        List<Integer> candidates = new ArrayList<>();
        for (int y = Math.max(0, placement.liftY - OUTER_PERIMETER_RADIUS);
             y <= Math.min(grid.getHeight() - 1,
                     placement.liftY + OUTER_PERIMETER_RADIUS); y++) {
            for (int x = Math.max(0, placement.liftX - OUTER_PERIMETER_RADIUS);
                 x <= Math.min(grid.getWidth() - 1,
                         placement.liftX + OUTER_PERIMETER_RADIUS); x++) {
                int radius = Math.max(Math.abs(x - placement.liftX),
                        Math.abs(y - placement.liftY));
                if (radius < INNER_PERIMETER_RADIUS
                        || radius > OUTER_PERIMETER_RADIUS
                        || !grid.isWalkable(x, y)
                        || occupied(x, y, sim)
                        || Paths.isEmpty(GridPathfinder.findPath(
                        grid, x, y, placement.liftX, placement.liftY))) {
                    continue;
                }
                candidates.add(grid.index(x, y));
            }
        }
        candidates.sort(Comparator
                .comparingInt((Integer cell) -> Math.abs(
                        Math.max(Math.abs(cell % grid.getWidth() - placement.liftX),
                                Math.abs(cell / grid.getWidth() - placement.liftY)) - 4))
                .thenComparingInt(Integer::intValue));

        List<Integer> selected = new ArrayList<>();
        for (int cell : candidates) {
            if (!spaced(cell, selected, grid.getWidth())) continue;
            selected.add(cell);
            if (selected.size() == TARGET_GUARDS) break;
        }
        return selected;
    }

    private static boolean occupied(int x, int y, BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.world().cellX(unit) == x && sim.world().cellY(unit) == y) {
                return true;
            }
        }
        return false;
    }

    private static boolean spaced(int cell, List<Integer> selected, int width) {
        int x = cell % width;
        int y = cell / width;
        for (int other : selected) {
            int dx = x - other % width;
            int dy = y - other / width;
            if (dx * dx + dy * dy
                    < MIN_GUARD_SPACING * MIN_GUARD_SPACING) return false;
        }
        return true;
    }
}
