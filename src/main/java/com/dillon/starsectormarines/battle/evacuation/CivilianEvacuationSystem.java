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
    /** Each civilian owns this leash independently; one protected VIP cannot move all eight. */
    static final int ESCORT_LEASH_RADIUS = 5;
    /** Maximum progress a civilian may hold ahead of its nearest escort toward the lift. */
    static final float MAX_FORWARD_LEAD = 2f;
    /** Nearby enemies make civilians hold a screened cell behind their escort. */
    static final int THREAT_SCREEN_RADIUS = 12;
    private static final int SCREEN_OFFSET = 2;
    private static final long NO_CELL = Long.MIN_VALUE;

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
            long escort = evacuationTriggered ? nearestMarine(id, sim) : 0L;
            if (escort == 0L || !withinLeash(id, escort, sim)) {
                sim.clearPath(id);
                continue;
            }

            long threat = nearestThreat(id, sim);
            long destination;
            if (threat != 0L) {
                destination = screenedDestination(id, escort, threat, sim);
            } else if (forwardLead(id, escort, sim) > MAX_FORWARD_LEAD) {
                destination = NO_CELL;
            } else {
                destination = packCell(liftX, liftY);
            }
            if (destination == NO_CELL) {
                sim.clearPath(id);
                continue;
            }
            int destinationX = unpackX(destination);
            int destinationY = unpackY(destination);
            if (sim.world().cellX(id) == destinationX
                    && sim.world().cellY(id) == destinationY) {
                sim.clearPath(id);
                continue;
            }

            int[] path = sim.movement().path(id);
            boolean destinationChanged = Paths.destX(path) != destinationX
                    || Paths.destY(path) != destinationY;
            boolean pathExhausted = sim.movement().pathIdx(id) >= Paths.cellCount(path);
            if (destinationChanged
                    || (pathExhausted && sim.movement().mayRepath(id))) {
                sim.setPath(id, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(id), sim.world().cellY(id),
                        destinationX, destinationY, sim.getOccupancyMap()));
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

    private long nearestMarine(long civilian, BattleSimulation sim) {
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            float distance = distanceSquared(civilian, unit, sim);
            if (distance < bestDistance
                    || (distance == bestDistance && (best == 0L || unit < best))) {
                best = unit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private long nearestThreat(long civilian, BattleSimulation sim) {
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            if (!sim.getGrid().hasLineOfSightWithin(
                    sim.world().cellX(civilian), sim.world().cellY(civilian),
                    sim.world().cellX(unit), sim.world().cellY(unit),
                    THREAT_SCREEN_RADIUS)) continue;
            float distance = distanceSquared(civilian, unit, sim);
            if (distance < bestDistance
                    || (distance == bestDistance && (best == 0L || unit < best))) {
                best = unit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean withinLeash(long civilian, long escort, BattleSimulation sim) {
        return distanceSquared(civilian, escort, sim)
                <= ESCORT_LEASH_RADIUS * ESCORT_LEASH_RADIUS;
    }

    private float forwardLead(long civilian, long escort, BattleSimulation sim) {
        float civilianDx = sim.world().x(civilian) - (liftX + 0.5f);
        float civilianDy = sim.world().y(civilian) - (liftY + 0.5f);
        float escortDx = sim.world().x(escort) - (liftX + 0.5f);
        float escortDy = sim.world().y(escort) - (liftY + 0.5f);
        float civilianDistance = (float) Math.sqrt(
                civilianDx * civilianDx + civilianDy * civilianDy);
        float escortDistance = (float) Math.sqrt(
                escortDx * escortDx + escortDy * escortDy);
        return escortDistance - civilianDistance;
    }

    private long screenedDestination(long civilian, long escort, long threat,
                                     BattleSimulation sim) {
        int marineX = sim.world().cellX(escort);
        int marineY = sim.world().cellY(escort);
        int threatX = sim.world().cellX(threat);
        int threatY = sim.world().cellY(threat);
        int awayX = Integer.compare(marineX, threatX);
        int awayY = Integer.compare(marineY, threatY);
        if (awayX == 0 && awayY == 0) return NO_CELL;
        int desiredX = marineX + awayX * SCREEN_OFFSET;
        int desiredY = marineY + awayY * SCREEN_OFFSET;
        int civilianX = sim.world().cellX(civilian);
        int civilianY = sim.world().cellY(civilian);
        byte[] occupancy = sim.getOccupancyMap();
        long best = NO_CELL;
        int bestScore = Integer.MAX_VALUE;
        int bestCell = Integer.MAX_VALUE;
        for (int y = desiredY - 2; y <= desiredY + 2; y++) {
            for (int x = desiredX - 2; x <= desiredX + 2; x++) {
                if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) {
                    continue;
                }
                int fromMarineX = x - marineX;
                int fromMarineY = y - marineY;
                int marineDistance = fromMarineX * fromMarineX
                        + fromMarineY * fromMarineY;
                if (marineDistance == 0 || marineDistance > 16
                        || fromMarineX * awayX + fromMarineY * awayY <= 0) {
                    continue;
                }
                int cell = sim.getGrid().index(x, y);
                if ((occupancy[cell] & 0xFF) != 0
                        && (x != civilianX || y != civilianY)) continue;
                int desiredDx = x - desiredX;
                int desiredDy = y - desiredY;
                int liftDistance = Math.abs(x - liftX) + Math.abs(y - liftY);
                int score = (desiredDx * desiredDx + desiredDy * desiredDy) * 100
                        + liftDistance;
                if (score < bestScore || (score == bestScore && cell < bestCell)) {
                    best = packCell(x, y);
                    bestScore = score;
                    bestCell = cell;
                }
            }
        }
        return best;
    }

    private static float distanceSquared(long first, long second,
                                         BattleSimulation sim) {
        float dx = sim.world().x(first) - sim.world().x(second);
        float dy = sim.world().y(first) - sim.world().y(second);
        return dx * dx + dy * dy;
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

    private static long packCell(int x, int y) {
        return ((long) y << 32) | (x & 0xFFFFFFFFL);
    }

    private static int unpackX(long cell) {
        return (int) cell;
    }

    private static int unpackY(long cell) {
        return (int) (cell >>> 32);
    }
}
