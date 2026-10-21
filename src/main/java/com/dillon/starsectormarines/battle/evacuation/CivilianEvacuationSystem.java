package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RescueEscortCommand;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private CivilianEvacuationPlacement placement;
    private boolean configured;
    private boolean evacuationTriggered;
    private boolean respondingMarineObserved;
    private long pickupShuttleId;
    private boolean storyEscort;
    private int securedTicks;
    private int nextBoardTick;
    private int escortSquad = -1;
    public enum HoldReason { NONE, NO_ESCORT, REGROUPING, ROUTE_BLOCKED, PICKUP_WAITING }
    private int unescorted, blocked, awaitingPickup, screened;
    private boolean shelterThreat;
    /** Observed movement gates; no hostile positions or private wave schedule. */
    public record Progress(boolean storyEscort, int securedTicks, boolean released,
                           boolean shelterThreat, int unescorted, int blocked,
                           int awaitingPickup, int screened) { }
    public Progress progress() {
        return new Progress(storyEscort, securedTicks, evacuationTriggered, shelterThreat,
                unescorted, blocked, awaitingPickup, screened);
    }
    public HoldReason holdReason() {
        if (!storyEscort || !evacuationTriggered || tracker.activeCount() == 0) return HoldReason.NONE;
        if (blocked > 0) return HoldReason.ROUTE_BLOCKED;
        if (awaitingPickup > 0) return HoldReason.PICKUP_WAITING;
        if (unescorted >= tracker.activeCount()) return HoldReason.NO_ESCORT;
        return unescorted > 0 ? HoldReason.REGROUPING : HoldReason.NONE;
    }
    public void requireStoryEscort() { storyEscort = true; }
    private int assignedEscort(BattleSimulation sim) {
        if (!(sim.getCommander(Faction.MARINE) instanceof RescueEscortCommand command)
                || command.rescueSnapshot() == null) return -1;
        return command.rescueSnapshot().squadIntents().stream()
                .filter(s -> s.role() == RescueCommandSnapshot.Role.COHORT_ESCORT)
                .mapToInt(s -> s.squadId()).findFirst().orElse(-1);
    }
    /**
     * The squad sealed in the shelter with the cohort, or {@link Squad#NO_SQUAD}.
     *
     * <p>This used to be a boolean on {@link Squad} itself, which existed
     * because the guard was a marine squad that had to be kept out of the
     * player's command pool. It is an allied squad now and the faction does
     * that, so what is left is the one thing genuinely local to this mission:
     * <em>which</em> squad is the one in the shelter. That belongs beside the
     * evacuation state it is guarding rather than on every squad in the game.
     */
    private int shelterGuardSquadId = Squad.NO_SQUAD;

    /**
     * Where the cohort is holed up: the main redoubt (the shelter) first, then
     * any holdouts ({@code colony-breach-nouns.md}, Redoubt). With the main
     * redoubt alone the system plays exactly as a single-shelter rescue.
     */
    private final List<Redoubt> redoubts = new ArrayList<>();
    /** Holdout members by identity; a member not listed belongs to the main redoubt. */
    private final Map<Long, Integer> redoubtOf = new HashMap<>();
    /** A holdout falls once its garrison is dead and a swarm body stands this close to its walls. */
    static final int FALL_PERIMETER = 1;

    private static final class Redoubt {
        final int number, approachX, approachY, left, top, right, bottom;
        int garrisonSquadId;
        /** The team on the yard's firing step, or {@code -1}; it falls back to {@link #building} once the yard is entered. */
        int lineSquadId = -1;
        TacticalNode building;
        int lineBrokeTick = -1;
        int cohort;
        int releasedTick = -1;
        int fellTick = -1;
        int sealed, walking, walkingCellX = -1, walkingCellY = -1;

        Redoubt(int number, int approachX, int approachY, int left, int top, int right, int bottom,
                int garrisonSquadId) {
            this.number = number;
            this.approachX = approachX;
            this.approachY = approachY;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.garrisonSquadId = garrisonSquadId;
        }

        CivilianRedoubt status() {
            return new CivilianRedoubt(number, approachX, approachY, left, top, right, bottom,
                    garrisonSquadId, cohort, releasedTick, fellTick, sealed, walking,
                    walkingCellX, walkingCellY, lineBrokeTick);
        }
    }

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
        this.placement = placement;
        int r = CivilianEvacuationPlacement.SHELTER_ZONE_RADIUS;
        Redoubt main = new Redoubt(1, shelterApproachX, shelterApproachY,
                placement.shelterX - r, placement.shelterY - r,
                placement.shelterX + r, placement.shelterY + r, shelterGuardSquadId);
        main.cohort = tracker.registeredCount();
        redoubts.add(main);
        configured = true;
        return true;
    }

    /**
     * Adds a holdout: a building away from the shelter holding a few of the
     * cohort behind its own garrison. Its people join the cohort and stay put
     * until a Marine reaches {@code (approachX, approachY)}.
     *
     * @return the holdout's redoubt number, or {@code -1} when refused
     */
    public int addHoldout(int approachX, int approachY, int left, int top, int right, int bottom,
                          int garrisonSquadId, List<Long> members) {
        if (!configured || tracker.isSealed() || members.isEmpty()) return -1;
        Redoubt holdout = new Redoubt(redoubts.size() + 1, approachX, approachY,
                left, top, right, bottom, garrisonSquadId);
        for (long id : members) {
            if (!tracker.enlist(id)) throw new IllegalStateException("holdout member " + id + " refused");
            redoubtOf.put(id, redoubts.size());
            holdout.cohort++;
        }
        redoubts.add(holdout);
        return holdout.number;
    }

    /**
     * Gives a holdout its line: the team manning the firing step of the works
     * round its building ({@code colony-breach-nouns.md}, Redoubt). The line
     * counts as the holdout's garrison as the house team does, and falls back
     * into {@code building} once a swarm body is inside the barricade or half
     * the line is down, so a perimeter that breaks is seen to be pushed back.
     */
    public boolean setHoldoutLine(int number, int lineSquadId, TacticalNode building) {
        if (number < 2 || number > redoubts.size() || building == null) return false;
        Redoubt holdout = redoubts.get(number - 1);
        holdout.lineSquadId = lineSquadId;
        holdout.building = building;
        return true;
    }

    /** The redoubts in number order; empty before configuration. */
    public List<CivilianRedoubt> redoubts() {
        List<CivilianRedoubt> out = new ArrayList<>(redoubts.size());
        for (Redoubt redoubt : redoubts) out.add(redoubt.status());
        return out;
    }

    private boolean multipleRedoubts() {
        return redoubts.size() > 1;
    }

    private int redoubtIndex(long id) {
        Integer index = redoubtOf.get(id);
        return index != null ? index : 0;
    }

    private boolean released(long id) {
        return multipleRedoubts() ? redoubts.get(redoubtIndex(id)).releasedTick >= 0 : evacuationTriggered;
    }

    /**
     * Records the squad standing guard over the sealed cohort. One only; a
     * second registration is refused rather than silently replacing the first.
     */
    public boolean registerShelterGuard(int squadId) {
        if (squadId == Squad.NO_SQUAD
                || shelterGuardSquadId != Squad.NO_SQUAD) {
            return false;
        }
        shelterGuardSquadId = squadId;
        if (!redoubts.isEmpty()) redoubts.get(0).garrisonSquadId = squadId;
        return true;
    }

    /** Whether {@code squadId} is the squad sealed in the shelter. */
    public boolean isShelterGuard(int squadId) {
        return squadId != Squad.NO_SQUAD && squadId == shelterGuardSquadId;
    }

    /** Associates the configured cohort with its physical pickup craft. */
    public boolean attachPickupShuttle(long shuttleId) {
        if (!configured || pickupShuttleId != 0L || shuttleId == 0L) {
            return false;
        }
        pickupShuttleId = shuttleId;
        return true;
    }

    public void tick(BattleSimulation sim) {
        if (!configured || tracker.isSealed()) return;
        if (storyEscort) escortSquad = assignedEscort(sim);
        unescorted = 0; blocked = 0; awaitingPickup = 0; screened = 0; shelterThreat = false;
        if (multipleRedoubts()) {
            releaseRedoubts(sim);
        } else if (!evacuationTriggered) {
            boolean respondingMarineAlive = hasRespondingMarine(sim);
            respondingMarineObserved |= respondingMarineAlive;
            boolean reached = marineWithin(shelterApproachX, shelterApproachY, RELIEF_TRIGGER_RADIUS, sim);
            if (storyEscort) {
                boolean threat = false;
                for (int i = 0; i < tracker.registeredCount(); i++) {
                    long id = tracker.entityIdAt(i);
                    if (sim.resolveUnit(id) != 0L && nearestThreat(id, sim) != 0L) threat = true;
                }
                shelterThreat = threat;
                securedTicks = reached && !threat ? securedTicks + 1 : 0;
                evacuationTriggered = securedTicks >= 60;
            } else if (reached || (respondingMarineObserved && !respondingMarineAlive)) {
                evacuationTriggered = true;
            }
            if (evacuationTriggered && !redoubts.isEmpty()) redoubts.get(0).releasedTick = sim.getSimTickIndex();
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
                if (pickupReady(sim)) {
                    board(id, sim);
                } else {
                    awaitingPickup++;
                    sim.clearPath(id);
                }
                continue;
            }
            if (multipleRedoubts() && !released(id)) {
                // Behind its own redoubt's door until somebody reaches it.
                sim.clearPath(id);
                continue;
            }
            long escort = released(id) ? nearestMarine(id, sim) : 0L;
            if (escort == 0L || !withinLeash(id, escort, sim)) {
                unescorted++;
                sim.clearPath(id);
                continue;
            }

            long threat = nearestThreat(id, sim);
            long destination;
            if (threat != 0L) {
                screened++;
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
            if (Paths.isEmpty(sim.movement().path(id))) blocked++;
            sim.advanceMovement(id);
            if (insideLiftZone(sim.world().cellX(id),
                    sim.world().cellY(id))) {
                if (pickupReady(sim)) board(id, sim);
            }
        }
        countRedoubts(sim);
        releasePickupWhenResolved(sim);
    }

    /**
     * Releases each redoubt a Marine has reached and marks the holdouts that
     * have fallen. The main redoubt is sealed and hidden, so only a holdout
     * falls: when its garrison is dead and a swarm body is at its walls, or
     * when nobody behind its door is still alive. A fallen holdout's survivors
     * leave the cohort: overrun, they are the swarm's.
     */
    private void releaseRedoubts(BattleSimulation sim) {
        boolean respondingMarineAlive = hasRespondingMarine(sim);
        respondingMarineObserved |= respondingMarineAlive;
        boolean abandoned = respondingMarineObserved && !respondingMarineAlive;
        int tick = sim.getSimTickIndex();
        for (int r = 0; r < redoubts.size(); r++) {
            Redoubt redoubt = redoubts.get(r);
            if (redoubt.releasedTick >= 0 || redoubt.fellTick >= 0) continue;
            if (abandoned || marineWithin(redoubt.approachX, redoubt.approachY, RELIEF_TRIGGER_RADIUS, sim)) {
                redoubt.releasedTick = tick;
                if (r == 0) evacuationTriggered = true;
                continue;
            }
            if (r == 0) continue;
            int sealedAlive = 0;
            for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
                long id = tracker.entityIdAt(i);
                if (redoubtIndex(id) == r && tracker.state(id) == CivilianEvacuationTracker.State.ACTIVE
                        && sim.resolveUnit(id) != 0L) sealedAlive++;
            }
            Squad garrison = redoubt.garrisonSquadId >= 0 ? sim.getSquad(redoubt.garrisonSquadId) : null;
            Squad line = redoubt.lineSquadId >= 0 ? sim.getSquad(redoubt.lineSquadId) : null;
            boolean garrisonAlive = (garrison != null && garrison.aliveMembers > 0)
                    || (line != null && line.aliveMembers > 0);
            if (line != null && redoubt.lineBrokeTick < 0 && line.aliveMembers > 0
                    && (line.aliveMembers * 2 <= line.originalSize || swarmInYard(redoubt, sim))) {
                redoubt.lineBrokeTick = tick;
                sim.assignSquadCommand(ObjectiveAssignment.holdNode(line.id, redoubt.building),
                        CommandAuthority.GARRISON, ColonyMilitia.ISSUER, "fall back into the building");
            }
            if (sealedAlive == 0 || (!garrisonAlive && swarmAtWalls(redoubt, sim))) {
                redoubt.fellTick = tick;
                for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
                    long id = tracker.entityIdAt(i);
                    if (redoubtIndex(id) == r) tracker.markLost(id);
                }
            }
        }
    }

    /** Whether a swarm body stands inside a holdout's barricade: the perimeter has been broken or the gate rushed. */
    private static boolean swarmInYard(Redoubt redoubt, BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            if (x > redoubt.left && x < redoubt.right && y > redoubt.top && y < redoubt.bottom) return true;
        }
        return false;
    }

    private static boolean swarmAtWalls(Redoubt redoubt, BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            if (x >= redoubt.left - FALL_PERIMETER && x <= redoubt.right + FALL_PERIMETER
                    && y >= redoubt.top - FALL_PERIMETER && y <= redoubt.bottom + FALL_PERIMETER) return true;
        }
        return false;
    }

    /**
     * Each redoubt's people behind the door and walking out, and where the
     * walkers are: their mean cell, or the rearmost of them when it has fallen
     * more than five cells behind, the regroup law the cohort objective uses.
     */
    private void countRedoubts(BattleSimulation sim) {
        int n = redoubts.size();
        int[] sumX = new int[n], sumY = new int[n];
        int[] rearX = new int[n], rearY = new int[n], rearDistance = new int[n];
        for (Redoubt redoubt : redoubts) {
            redoubt.sealed = 0;
            redoubt.walking = 0;
        }
        Arrays.fill(rearDistance, -1);
        for (int i = 0, count = tracker.registeredCount(); i < count; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE || sim.resolveUnit(id) == 0L) continue;
            int r = redoubtIndex(id);
            Redoubt redoubt = redoubts.get(r);
            if (redoubt.releasedTick < 0) {
                redoubt.sealed++;
                continue;
            }
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            redoubt.walking++;
            sumX[r] += x;
            sumY[r] += y;
            int distance = Math.abs(x - liftX) + Math.abs(y - liftY);
            if (distance > rearDistance[r]) {
                rearDistance[r] = distance;
                rearX[r] = x;
                rearY[r] = y;
            }
        }
        for (int r = 0; r < n; r++) {
            Redoubt redoubt = redoubts.get(r);
            if (redoubt.walking == 0) {
                redoubt.walkingCellX = -1;
                redoubt.walkingCellY = -1;
                continue;
            }
            int cx = sumX[r] / redoubt.walking, cy = sumY[r] / redoubt.walking;
            if (Math.abs(rearX[r] - cx) + Math.abs(rearY[r] - cy) > 5) {
                cx = rearX[r];
                cy = rearY[r];
            }
            redoubt.walkingCellX = cx;
            redoubt.walkingCellY = cy;
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

    public boolean hasPickupShuttle() {
        return pickupShuttleId != 0L;
    }

    /** Opening footprint targetless runners must not wander into. */
    public boolean isInsideOpeningProtectedZone(int x, int y) {
        return configured && (SwarmDefenseRoster.insideShelterZone(
                x, y, placement)
                || SwarmDefenseRoster.insideLiftZone(x, y, placement));
    }

    private long nearestMarine(long civilian, BattleSimulation sim) {
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            if (isShelterGuard(unit, sim)) continue;
            if (storyEscort && (sim.squadOf(unit) == null || sim.squadOf(unit).id != escortSquad)) continue;
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

    private boolean marineWithin(int x, int y, int distance,
                                 BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!isRespondingMarine(unit, sim)) continue;
            if (storyEscort && (sim.squadOf(unit) == null || sim.squadOf(unit).id != escortSquad)) continue;
            int dx = sim.world().cellX(unit) - x;
            int dy = sim.world().cellY(unit) - y;
            if (dx * dx + dy * dy <= distance * distance) return true;
        }
        return false;
    }

    private boolean hasRespondingMarine(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (isRespondingMarine(sim.liveUnitAt(i), sim)) return true;
        }
        return false;
    }

    private boolean isRespondingMarine(long unit, BattleSimulation sim) {
        // The guard is allied rather than marine since the migration, so the
        // faction test alone already excludes it. The guard test is kept
        // because "somebody came to relieve the shelter" must never be
        // answered by the people already inside it, whatever side they are on.
        return sim.identity().faction(unit) == Faction.MARINE
                && !isShelterGuard(unit, sim);
    }

    private boolean isShelterGuard(long unit, BattleSimulation sim) {
        Squad squad = sim.squadOf(unit);
        return squad != null && isShelterGuard(squad.id);
    }

    private void board(long id, BattleSimulation sim) {
        if (storyEscort && sim.getSimTickIndex() < nextBoardTick) return;
        if (!tracker.markEvacuated(id)) return;
        nextBoardTick = sim.getSimTickIndex() + 24;
        ShuttleMission pickup = pickupMission(sim);
        if (pickup != null && pickup.evacueesAboard < pickup.evacueeCapacity) {
            pickup.evacueesAboard++;
        }
        sim.clearPath(id);
        // Boarding takes the civilian off the field entirely — out of live
        // iteration and out of the entity world. It is not a death and
        // therefore creates no corpse or loss event, but it does have to
        // remove the row the render pass walks, or a rescued civilian goes on
        // standing at the ramp for the rest of the battle.
        sim.takeOffTheField(id);
    }

    private boolean pickupReady(BattleSimulation sim) {
        if (pickupShuttleId == 0L) return true;
        ShuttleMission pickup = pickupMission(sim);
        return pickup != null && pickup.state == ShuttleState.LANDED
                && pickup.evacueesAboard < pickup.evacueeCapacity;
    }

    /** The attached pickup craft's mission, or {@code null} when none is attached or live. */
    public ShuttleMission pickupMission(BattleSimulation sim) {
        return pickupShuttleId == 0L
                ? null : sim.world().mission(pickupShuttleId);
    }

    private void releasePickupWhenResolved(BattleSimulation sim) {
        if (tracker.activeCount() != 0) return;
        ShuttleMission pickup = pickupMission(sim);
        if (pickup != null) pickup.awaitingEvacuees = false;
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
