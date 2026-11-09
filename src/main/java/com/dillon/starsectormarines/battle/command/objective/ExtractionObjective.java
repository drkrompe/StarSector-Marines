package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleView;
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
    private final UncontestedHold source;
    private final EscortedRoute transit;
    private final UncontestedHold boarding;

    private boolean complete;
    private boolean failed;
    private Failure failure = Failure.NONE;
    private int alarmTick = -1;
    private int controllingSquadId = -1;
    private boolean escortPresent;
    private boolean contested;

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
        this.source = new UncontestedHold(secureDuration);
        this.transit = new EscortedRoute(route, secondsPerRouteCell, lossDuration, abandonmentDuration,
                ESCORT_RADIUS, INTERDICTION_RADIUS);
        this.boarding = new UncontestedHold(boardingDuration);
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        if (!source.held()) {
            tickSourceControl(sim);
            return;
        }
        if (!transit.arrived()) {
            tickTransit(sim);
            return;
        }
        tickBoarding(sim);
    }

    private void tickSourceControl(BattleView sim) {
        // The zone graph is rebuilt when topology changes (a wall or a furnishing destroyed), and
        // rebuilding renumbers zones, so the source's zone is read where it stands now. Comparing
        // against the id frozen at setup left every escort in "another" zone for the whole battle.
        int zone = sim.getZoneGraph().zoneIdAt(sourceCellX(), sourceCellY());
        source.tick(sim, sourceCellX(), sourceCellY(), SOURCE_RADIUS, zone >= 0 ? zone : sourceZoneId);
        contested = source.contested();
        escortPresent = source.escortPresent();
        controllingSquadId = source.controllingSquadId();
        if (escortPresent && alarmTick < 0) alarmTick = sim.getSimTickIndex();
    }

    private void tickTransit(BattleView sim) {
        Failure result = transit.tick(sim);
        contested = transit.contested();
        escortPresent = transit.escortPresent();
        controllingSquadId = transit.controllingSquadId();
        if (result != Failure.NONE) fail(result);
    }

    private void tickBoarding(BattleView sim) {
        boolean boarded = boarding.tick(sim, egressCellX(), egressCellY(), EGRESS_RADIUS, -1);
        contested = boarding.contested();
        escortPresent = boarding.escortPresent();
        controllingSquadId = boarding.controllingSquadId();
        if (boarded) {
            complete = true;
            escortPresent = false;
            controllingSquadId = -1;
        }
    }

    private void fail(Failure reason) {
        failed = true;
        failure = reason;
        escortPresent = false;
        controllingSquadId = -1;
    }

    public boolean contested() { return contested; }
    public float lossDuration() { return transit.lossDuration(); }
    public float abandonmentDuration() { return transit.abandonmentDuration(); }

    public float secureProgress() { return source.progress(); }
    public float secureDuration() { return source.duration(); }
    public float boardingProgress() { return boarding.progress(); }
    public float boardingDuration() { return boarding.duration(); }
    public float lossProgress() { return transit.lossProgress(); }
    public float abandonmentProgress() { return transit.abandonmentProgress(); }
    public int routeCellCount() { return transit.cellCount(); }
    public int routeIndex() { return transit.index(); }

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
    @Override public int payloadCellX() { return transit.cellX(); }
    @Override public int payloadCellY() { return transit.cellY(); }
    @Override public int corridorGuideCellX() {
        int guide = Math.min(routeIndex() + 6, routeCellCount() - 1);
        return Paths.cellX(route, guide);
    }
    @Override public int corridorGuideCellY() {
        int guide = Math.min(routeIndex() + 6, routeCellCount() - 1);
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
        if (!source.held()) return 0.15f * source.progress() / source.duration();
        int travelCells = Math.max(1, routeCellCount() - 1);
        if (!transit.arrived()) {
            return 0.15f + 0.75f * routeIndex() / travelCells;
        }
        return 0.9f + 0.1f * boarding.progress() / boarding.duration();
    }
    @Override public Phase extractionPhase() {
        if (complete) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        if (!source.held()) return source.progress() > 0f ? Phase.SECURING : Phase.AT_SOURCE;
        return !transit.arrived() ? Phase.IN_TRANSIT : Phase.BOARDING;
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
}
