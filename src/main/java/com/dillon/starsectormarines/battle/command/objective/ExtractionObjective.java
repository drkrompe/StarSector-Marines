package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;

/**
 * Generic Extraction contract for one abstract recoverable package. Marines
 * first establish uncontested control at its source, then keep a combat squad
 * close enough to escort it along an authored route and board it at egress.
 * The package is mission state rather than a hidden carrier entity, so squad
 * casualties may change the escort without silently destroying the objective.
 */
public final class ExtractionObjective implements ExtractionPayloadObjective {

    public static final float DEFAULT_SECURE_DURATION = 4f;
    public static final float DEFAULT_SECONDS_PER_ROUTE_CELL = 0.35f;
    public static final float DEFAULT_BOARDING_DURATION = 3f;
    public static final float DEFAULT_LOSS_DURATION = 6f;
    public static final float DEFAULT_ABANDONMENT_DURATION = 30f;
    public static final int SOURCE_RADIUS = 2;
    public static final int ESCORT_RADIUS = 5;
    public static final int INTERDICTION_RADIUS = 3;
    public static final int EGRESS_RADIUS = 3;

    private final String payloadId;
    private final String payloadName;
    private final int sourceZoneId;
    private final int[] route;
    private final float secureDuration;
    private final float secondsPerRouteCell;
    private final float boardingDuration;
    private final float lossDuration;
    private final float abandonmentDuration;

    private int routeIndex;
    private float secureProgress;
    private float travelProgress;
    private float boardingProgress;
    private float lossProgress;
    private float abandonmentProgress;
    private boolean secured;
    private boolean complete;
    private boolean failed;
    private Failure failure = Failure.NONE;
    private int alarmTick = -1;
    private int controllingSquadId = -1;
    private boolean escortPresent;

    public ExtractionObjective(String payloadId, String payloadName,
                               int sourceZoneId, int[] route) {
        this(payloadId, payloadName, sourceZoneId, route,
                DEFAULT_SECURE_DURATION, DEFAULT_SECONDS_PER_ROUTE_CELL,
                DEFAULT_BOARDING_DURATION, DEFAULT_LOSS_DURATION,
                DEFAULT_ABANDONMENT_DURATION);
    }

    ExtractionObjective(String payloadId, String payloadName,
                        int sourceZoneId, int[] route,
                        float secureDuration, float secondsPerRouteCell,
                        float boardingDuration, float lossDuration,
                        float abandonmentDuration) {
        if (payloadId == null || payloadId.isBlank()
                || payloadName == null || payloadName.isBlank()) {
            throw new IllegalArgumentException("stable Extraction payload identity required");
        }
        if (sourceZoneId < 0 || route == null || route.length < 4
                || (route.length & 1) != 0 || secureDuration <= 0f
                || secondsPerRouteCell <= 0f || boardingDuration <= 0f
                || lossDuration <= 0f || abandonmentDuration <= 0f) {
            throw new IllegalArgumentException("valid Extraction route and durations required");
        }
        this.payloadId = payloadId;
        this.payloadName = payloadName;
        this.sourceZoneId = sourceZoneId;
        this.route = Arrays.copyOf(route, route.length);
        this.secureDuration = secureDuration;
        this.secondsPerRouteCell = secondsPerRouteCell;
        this.boardingDuration = boardingDuration;
        this.lossDuration = lossDuration;
        this.abandonmentDuration = abandonmentDuration;
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        if (!secured) {
            tickSourceControl(sim);
            return;
        }
        if (routeIndex < Paths.cellCount(route) - 1) {
            tickTransit(sim);
            return;
        }
        tickBoarding(sim);
    }

    private void tickSourceControl(BattleView sim) {
        Escort escort = nearestMarineEscort(sim, sourceCellX(), sourceCellY(),
                SOURCE_RADIUS, sourceZoneId);
        boolean defenderPresent = combatantPresent(sim, Faction.DEFENDER,
                sourceCellX(), sourceCellY(), SOURCE_RADIUS, sourceZoneId);
        escortPresent = escort.present() && !defenderPresent;
        controllingSquadId = escortPresent ? escort.squadId() : -1;
        if (!escortPresent) {
            secureProgress = 0f;
            return;
        }
        if (alarmTick < 0) alarmTick = sim.getSimTickIndex();
        secureProgress += BattleSimulation.TICK_DT;
        if (secureProgress >= secureDuration) {
            secureProgress = secureDuration;
            secured = true;
        }
    }

    private void tickTransit(BattleView sim) {
        int x = payloadCellX();
        int y = payloadCellY();
        Escort escort = nearestMarineEscort(sim, x, y, ESCORT_RADIUS, -1);
        boolean defenderPresent = combatantPresent(sim, Faction.DEFENDER,
                x, y, INTERDICTION_RADIUS, -1);
        escortPresent = escort.present();
        controllingSquadId = escortPresent ? escort.squadId() : -1;

        if (!escortPresent) {
            travelProgress = 0f;
            if (defenderPresent) {
                lossProgress += BattleSimulation.TICK_DT;
                abandonmentProgress = 0f;
                if (lossProgress >= lossDuration) fail(Failure.LOST);
            } else {
                abandonmentProgress += BattleSimulation.TICK_DT;
                lossProgress = 0f;
                if (abandonmentProgress >= abandonmentDuration) {
                    fail(Failure.ABANDONED);
                }
            }
            return;
        }
        lossProgress = 0f;
        abandonmentProgress = 0f;
        if (defenderPresent) {
            travelProgress = 0f;
            return;
        }
        travelProgress += BattleSimulation.TICK_DT;
        while (travelProgress >= secondsPerRouteCell
                && routeIndex < Paths.cellCount(route) - 1) {
            travelProgress -= secondsPerRouteCell;
            routeIndex++;
        }
    }

    private void tickBoarding(BattleView sim) {
        Escort escort = nearestMarineEscort(sim, egressCellX(), egressCellY(),
                EGRESS_RADIUS, -1);
        boolean defenderPresent = combatantPresent(sim, Faction.DEFENDER,
                egressCellX(), egressCellY(), EGRESS_RADIUS, -1);
        escortPresent = escort.present() && !defenderPresent;
        controllingSquadId = escortPresent ? escort.squadId() : -1;
        if (!escortPresent) {
            boardingProgress = 0f;
            return;
        }
        boardingProgress += BattleSimulation.TICK_DT;
        if (boardingProgress >= boardingDuration) {
            boardingProgress = boardingDuration;
            complete = true;
            escortPresent = false;
            controllingSquadId = -1;
        }
    }

    private Escort nearestMarineEscort(BattleView sim, int x, int y,
                                       int radius, int requiredZone) {
        int radiusSquared = radius * radius;
        int selectedSquad = Integer.MAX_VALUE;
        long selectedUnit = Long.MAX_VALUE;
        boolean found = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE
                    || !sim.identity().type(unit).combatant
                    || !sim.movement().settled(unit)) continue;
            int unitX = sim.world().cellX(unit);
            int unitY = sim.world().cellY(unit);
            int dx = unitX - x;
            int dy = unitY - y;
            if (dx * dx + dy * dy > radiusSquared) continue;
            if (requiredZone >= 0 && sim.getZoneGraph().zoneIdAt(unitX, unitY)
                    != requiredZone) continue;
            Squad squad = sim.squadOf(unit);
            int squadId = squad != null ? squad.id : -1;
            int sortSquad = squadId >= 0 ? squadId : Integer.MAX_VALUE;
            if (!found || sortSquad < selectedSquad
                    || (sortSquad == selectedSquad && unit < selectedUnit)) {
                found = true;
                selectedSquad = sortSquad;
                selectedUnit = unit;
            }
        }
        return found ? new Escort(true,
                selectedSquad != Integer.MAX_VALUE ? selectedSquad : -1)
                : Escort.NONE;
    }

    private static boolean combatantPresent(BattleView sim, Faction faction,
                                             int x, int y, int radius,
                                             int requiredZone) {
        int radiusSquared = radius * radius;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != faction
                    || !sim.identity().type(unit).combatant) continue;
            int unitX = sim.world().cellX(unit);
            int unitY = sim.world().cellY(unit);
            int dx = unitX - x;
            int dy = unitY - y;
            if (dx * dx + dy * dy > radiusSquared) continue;
            if (requiredZone < 0 || sim.getZoneGraph().zoneIdAt(unitX, unitY)
                    == requiredZone) return true;
        }
        return false;
    }

    private void fail(Failure reason) {
        failed = true;
        failure = reason;
        escortPresent = false;
        controllingSquadId = -1;
    }

    public float secureProgress() { return secureProgress; }
    public float secureDuration() { return secureDuration; }
    public float boardingProgress() { return boardingProgress; }
    public float boardingDuration() { return boardingDuration; }
    public float lossProgress() { return lossProgress; }
    public float abandonmentProgress() { return abandonmentProgress; }
    public int routeCellCount() { return Paths.cellCount(route); }
    public int routeIndex() { return routeIndex; }

    @Override public String payloadId() { return payloadId; }
    @Override public String payloadName() { return payloadName; }
    @Override public Kind payloadKind() { return Kind.PACKAGE; }
    @Override public int sourceCellX() { return Paths.cellX(route, 0); }
    @Override public int sourceCellY() { return Paths.cellY(route, 0); }
    @Override public int egressCellX() {
        return Paths.cellX(route, Paths.cellCount(route) - 1);
    }
    @Override public int egressCellY() {
        return Paths.cellY(route, Paths.cellCount(route) - 1);
    }
    @Override public int payloadCellX() { return Paths.cellX(route, routeIndex); }
    @Override public int payloadCellY() { return Paths.cellY(route, routeIndex); }
    @Override public int corridorGuideCellX() {
        int guide = Math.min(routeIndex + 6, routeCellCount() - 1);
        return Paths.cellX(route, guide);
    }
    @Override public int corridorGuideCellY() {
        int guide = Math.min(routeIndex + 6, routeCellCount() - 1);
        return Paths.cellY(route, guide);
    }
    @Override public int initialElements() { return 1; }
    @Override public int activeElements() {
        return complete || failure == Failure.LOST ? 0 : 1;
    }
    @Override public int boardedElements() { return complete ? 1 : 0; }
    @Override public int lostElements() { return failure == Failure.LOST ? 1 : 0; }
    @Override public float normalizedProgress() {
        if (complete) return 1f;
        if (!secured) return 0.15f * secureProgress / secureDuration;
        int travelCells = Math.max(1, routeCellCount() - 1);
        if (routeIndex < routeCellCount() - 1) {
            return 0.15f + 0.75f * routeIndex / travelCells;
        }
        return 0.9f + 0.1f * boardingProgress / boardingDuration;
    }
    @Override public Phase extractionPhase() {
        if (complete) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        if (!secured) return secureProgress > 0f ? Phase.SECURING : Phase.AT_SOURCE;
        return routeIndex < routeCellCount() - 1
                ? Phase.IN_TRANSIT : Phase.BOARDING;
    }
    @Override public Failure failureReason() { return failure; }
    @Override public boolean alarmActive() { return alarmTick >= 0; }
    @Override public int alarmRaisedTick() { return alarmTick; }
    @Override public int controllingSquadId() { return controllingSquadId; }
    @Override public boolean escortPresent() { return escortPresent; }
    @Override public boolean isComplete() { return complete; }
    @Override public boolean isFailed() { return failed; }
    @Override public String displayName() {
        return "Recover " + payloadName + " and extract";
    }

    private record Escort(boolean present, int squadId) {
        private static final Escort NONE = new Escort(false, -1);
    }
}
