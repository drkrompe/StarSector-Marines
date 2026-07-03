package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.EntitySpec;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Spawns a single {@link Drone} for a drone-hub {@link Entity}: spirals out
 * from the hub anchor to find the first walkable cell beyond the embankment
 * ring, places the drone there, and registers it with the sim. No-op if the
 * hub is dead or every nearby cell is occupied — the hub's per-tick behavior
 * just re-tries on the next interval.
 */
public final class DroneSpawner {

    /** Inner radius of the spiral search — skips the hub center and its 3×3 embankment ring. */
    private static final int SEARCH_MIN_RADIUS = 2;
    /** Outer radius of the spiral search — cap the scan so a hub boxed in by walls + posts gives up cleanly. */
    private static final int SEARCH_MAX_RADIUS = 5;

    private DroneSpawner() {}

    /**
     * Tries to spawn one drone for {@code hub} (a live drone-hub entity id,
     * {@code sim.identity().type(hub).isDroneHub()}). Returns the spawned drone
     * on success, or {@code null} if no eligible cell was found within the
     * search radius. Mints the hub's {@code HUB_STATE} squad id (via
     * {@code sim.hubState().setDroneSquadId}) lazily on the first successful
     * launch so every drone from this hub coordinates through the same
     * {@link Squad} (encircle bearings, sector patrols). Subsequent launches
     * join the existing squad; if its leader is dead, the new drone takes
     * over.
     */
    public static Entity tryLaunch(long hub, BattleSimulation sim) {
        if (!sim.world().isAlive(hub)) return null;
        NavigationGrid grid = sim.getGrid();
        World world = sim.world();
        int hubX = world.cellX(hub);
        int hubY = world.cellY(hub);
        int[] cell = findFreeCell(grid, sim, hubX, hubY);
        if (cell == null) return null;
        String id = "drone-" + sim.identity().name(hub) + "-" + sim.hubState().incrementDronesLaunched(hub);
        EntitySpec droneSpec = Drone.create(id, sim.identity().faction(hub), cell[0], cell[1], hub);

        // Resolve the hub's drone squad (mint one on first launch) BEFORE the spawn
        // so the squad id can be stamped on the spec — allocate then seeds the SQUAD
        // component for both the serial (inline) and parallel (deferred flush) paths,
        // replacing the old post-spawn assignSquad/seedSquadId split.
        boolean newSquad = !sim.hubState().hasDroneSquad(hub);
        int squadId;
        if (newSquad) {
            squadId = sim.mintSquad(sim.identity().faction(hub), droneSpec.type);
            Squad squad = sim.getSquad(squadId);
            squad.droneHubId = hub;
            sim.hubState().setDroneSquadId(hub, squadId);
        } else {
            squadId = sim.hubState().droneSquadId(hub);
        }
        droneSpec.squad(squadId);

        // queueSpawn instead of inline addUnit — DroneHubBehavior runs inside
        // UPDATE_UNITS, which Phase B will fork-join. APPLY_SPAWNS drains the
        // queue before the next phase reads units.
        Entity drone = sim.queueSpawn(droneSpec);

        // Designate the drone as squad leader on first launch; on later launches take
        // over only if the current leader is dead/gone. In the parallel path the drone
        // is queued (entityId still 0 here), so leaderId stays 0 until a serial spawn
        // assigns a real id — matching the pre-spec behavior.
        Squad squad = sim.getSquad(squadId);
        if (newSquad || sim.resolveUnit(squad.leaderId) == 0L) {
            squad.leaderId = drone.entityId;
        }
        return drone;
    }

    /**
     * First walkable + unoccupied cell at distance &gt;= {@code SEARCH_MIN_RADIUS}
     * from the hub anchor. Box-spiral order (Chebyshev rings) — the spiral keeps
     * drones from clumping on the same neighbor cell when several hubs sit close
     * together.
     */
    private static int[] findFreeCell(NavigationGrid grid, BattleView sim, int hubX, int hubY) {
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
    private static boolean isCellOccupied(BattleView sim, int x, int y) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (sim.world().cellX(u.entityId) == x && sim.world().cellY(u.entityId) == y) return true;
        }
        return false;
    }
}
