package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Spawns a single {@link Drone} for a {@link DroneHubUnit}: spirals out from
 * the hub anchor to find the first walkable cell beyond the embankment ring,
 * places the drone there, and registers it with the sim. No-op if the hub is
 * dead or every nearby cell is occupied — the hub's per-tick behavior just
 * re-tries on the next interval.
 */
public final class DroneSpawner {

    /** Inner radius of the spiral search — skips the hub center and its 3×3 embankment ring. */
    private static final int SEARCH_MIN_RADIUS = 2;
    /** Outer radius of the spiral search — cap the scan so a hub boxed in by walls + posts gives up cleanly. */
    private static final int SEARCH_MAX_RADIUS = 5;

    private DroneSpawner() {}

    /**
     * Tries to spawn one drone for {@code hub}. Returns the spawned drone on
     * success, or {@code null} if no eligible cell was found within the
     * search radius. Mints {@link DroneHubUnit#droneSquad} lazily on the first
     * successful launch so every drone from this hub coordinates through the
     * same {@link Squad} (encircle bearings, sector patrols). Subsequent
     * launches join the existing squad; if its leader is dead, the new drone
     * takes over.
     */
    public static Drone tryLaunch(DroneHubUnit hub, BattleSimulation sim) {
        if (!hub.isAlive()) return null;
        NavigationGrid grid = sim.getGrid();
        int hubX = hub.cellX;
        int hubY = hub.cellY;
        int[] cell = findFreeCell(grid, sim, hubX, hubY);
        if (cell == null) return null;
        String id = "drone-" + hub.id + "-" + (++hub.dronesLaunched);
        Drone drone = new Drone(id, hub.faction, cell[0], cell[1], hub);
        // queueSpawn instead of inline addUnit — DroneHubBehavior runs inside
        // UPDATE_UNITS, which Phase B will fork-join. APPLY_SPAWNS drains the
        // queue before the next phase reads units.
        sim.queueSpawn(drone);

        if (hub.droneSquad == null) {
            int squadId = sim.mintSquad(hub.faction, drone);
            Squad squad = sim.getSquad(squadId);
            squad.droneHub = hub;
            hub.droneSquad = squad;
            drone.squadId = squadId;
        } else {
            drone.squadId = hub.droneSquad.id;
            if (hub.droneSquad.leader == null || !hub.droneSquad.leader.isAlive()) {
                hub.droneSquad.leader = drone;
            }
        }
        return drone;
    }

    /**
     * First walkable + unoccupied cell at distance &gt;= {@code SEARCH_MIN_RADIUS}
     * from the hub anchor. Box-spiral order (Chebyshev rings) — the spiral keeps
     * drones from clumping on the same neighbor cell when several hubs sit close
     * together.
     */
    private static int[] findFreeCell(NavigationGrid grid, BattleSimulation sim, int hubX, int hubY) {
        for (int r = SEARCH_MIN_RADIUS; r <= SEARCH_MAX_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = hubX + dx;
                    int ny = hubY + dy;
                    if (!grid.inBounds(nx, ny)) continue;
                    if (!grid.isWalkable(nx, ny)) continue;
                    if (isCellOccupied(sim, nx, ny)) continue;
                    return new int[]{nx, ny};
                }
            }
        }
        return null;
    }

    /** True if any alive unit currently logically occupies {@code (x, y)}. Cheap linear scan — defender + marine rosters cap small enough that this is fine inside an interval-gated tick. */
    private static boolean isCellOccupied(BattleSimulation sim, int x, int y) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive()) continue;
            if (u.cellX == x && u.cellY == y) return true;
        }
        return false;
    }
}
