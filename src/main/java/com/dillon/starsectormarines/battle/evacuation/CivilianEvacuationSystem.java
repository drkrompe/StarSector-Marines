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

    /** A marine must physically reach the bunker entrance to open it. */
    static final int RELIEF_TRIGGER_RADIUS = 2;
    /** Civilians stop when no living marine remains within this distance of the cohort. */
    static final int ESCORT_RADIUS = 6;

    private final CivilianEvacuationTracker tracker;
    private int liftX = -1;
    private int liftY = -1;
    private int shelterApproachX = -1;
    private int shelterApproachY = -1;
    private int radius;
    private boolean configured;
    private boolean evacuationTriggered;

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
        shelterApproachX = placement.shelterApproachX;
        shelterApproachY = placement.shelterApproachY;
        radius = CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
        configured = true;
        return true;
    }

    public void tick(BattleSimulation sim) {
        if (!configured || tracker.isSealed()) return;
        if (!evacuationTriggered && marineWithin(
                shelterApproachX, shelterApproachY,
                RELIEF_TRIGGER_RADIUS, sim)) {
            evacuationTriggered = true;
        }
        boolean escorted = evacuationTriggered && cohortHasEscort(sim);
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
            if (!escorted) {
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

    /** True while the cohort remains behind the sealed shelter barricade. */
    public boolean isShelterProtected() {
        return configured && !evacuationTriggered && !tracker.isSealed();
    }

    public boolean isEvacuationTriggered() {
        return evacuationTriggered;
    }

    private boolean cohortHasEscort(BattleSimulation sim) {
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long civilian = tracker.entityIdAt(i);
            if (tracker.state(civilian)
                    != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(civilian) == 0L) {
                continue;
            }
            if (marineWithin(sim.world().cellX(civilian),
                    sim.world().cellY(civilian), ESCORT_RADIUS, sim)) {
                return true;
            }
        }
        return false;
    }

    private static boolean marineWithin(int x, int y, int distance,
                                        BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            int dx = sim.world().cellX(unit) - x;
            int dy = sim.world().cellY(unit) - y;
            if (dx * dx + dy * dy <= distance * distance) return true;
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
