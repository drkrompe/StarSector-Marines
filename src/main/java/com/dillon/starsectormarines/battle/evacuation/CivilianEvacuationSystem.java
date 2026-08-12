package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Serial movement and boarding driver for the registered rescue cohort.
 * Squadless VIPs idle in ordinary unit dispatch; this system is their sole
 * path author and advances them toward the configured lift point.
 */
public final class CivilianEvacuationSystem {

    /** Civilians wait behind shelter walls for rescuers, but never deadlock a fixture or a mission whose first drop is lost. */
    static final float MAX_SHELTER_HOLD_SECONDS = 12f;

    private final CivilianEvacuationTracker tracker;
    private int liftX = -1;
    private int liftY = -1;
    private int radius;
    private boolean configured;
    private boolean released;
    private float shelterHoldElapsed;

    public CivilianEvacuationSystem(CivilianEvacuationTracker tracker) {
        if (tracker == null) {
            throw new IllegalArgumentException("tracker is required");
        }
        this.tracker = tracker;
    }

    /** One-time production configuration after a complete payload is installed. */
    public boolean configure(CivilianEvacuationPlacement placement) {
        if (configured || placement == null
                || tracker.registeredCount() != tracker.expectedCount()) {
            return false;
        }
        liftX = placement.liftX;
        liftY = placement.liftY;
        radius = CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
        configured = true;
        return true;
    }

    public void tick(BattleSimulation sim) {
        if (!configured || tracker.isSealed()) return;
        if (!released) {
            shelterHoldElapsed += BattleSimulation.TICK_DT;
            released = hasLiveMarine(sim)
                    || shelterHoldElapsed >= MAX_SHELTER_HOLD_SECONDS;
        }
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE) {
                continue;
            }
            if (sim.resolveUnit(id) == 0L) {
                tracker.markLost(id);
                continue;
            }
            if (insideLiftZone(sim.world().cellX(id),
                    sim.world().cellY(id))) {
                board(id, sim);
                continue;
            }
            if (!released) {
                sim.clearPath(id);
                continue;
            }

            int[] path = sim.movement().path(id);
            if (sim.movement().moveProgress(id) == 0f
                    && (sim.movement().pathIdx(id) >= Paths.cellCount(path)
                    || Paths.destX(path) != liftX
                    || Paths.destY(path) != liftY)) {
                sim.setPath(id, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(id), sim.world().cellY(id),
                        liftX, liftY, sim.getOccupancyMap()));
            }
            sim.advanceMovement(id);
            if (insideLiftZone(sim.world().cellX(id),
                    sim.world().cellY(id))) {
                board(id, sim);
            }
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    private static boolean hasLiveMarine(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.MARINE) {
                return true;
            }
        }
        return false;
    }

    private void board(long id, BattleSimulation sim) {
        if (!tracker.markEvacuated(id)) return;
        sim.clearPath(id);
        // Boarding removes the civilian from live iteration and rendering. It
        // is not a death and therefore creates no corpse or loss event.
        sim.releaseFromRegistry(id);
    }

    private boolean insideLiftZone(int x, int y) {
        return Math.abs(x - liftX) <= radius
                && Math.abs(y - liftY) <= radius;
    }
}
