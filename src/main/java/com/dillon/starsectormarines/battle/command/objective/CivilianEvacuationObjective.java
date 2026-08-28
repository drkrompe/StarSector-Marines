package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine objective that resolves the registered rescue cohort. Production
 * missions let {@code CivilianEvacuationSystem} board civilians only after the
 * physical pickup has landed; objective-only fixtures retain zone-crossing
 * accounting. Ambient civilians are invisible because both paths iterate
 * tracker identities, never faction or unit type.
 *
 * <p>A registered identity that is no longer live becomes lost. When every
 * member is terminal, the tracker seals: at least one evacuation completes
 * the objective, while a measured zero fails it.
 */
public final class CivilianEvacuationObjective
        implements ExtractionPayloadObjective {

    public static final String DEFAULT_PAYLOAD_ID = "CIVILIAN-COHORT";

    private final CivilianEvacuationTracker tracker;
    private final String payloadId;
    private final String payloadName;
    private final int sourceCellX;
    private final int sourceCellY;
    private final int cellX;
    private final int cellY;
    private final int radius;
    private final boolean requireAnyEvacuated;
    private boolean complete;
    private boolean failed;
    private int alarmRaisedTick = -1;
    private int payloadCellX = -1;
    private int payloadCellY = -1;
    private int controllingSquadId = -1;
    private boolean escortPresent;

    public CivilianEvacuationObjective(CivilianEvacuationTracker tracker,
                                       int cellX, int cellY, int radius) {
        this(tracker, DEFAULT_PAYLOAD_ID, "civilian cohort",
                cellX, cellY, cellX, cellY, radius, true);
    }

    public CivilianEvacuationObjective(CivilianEvacuationTracker tracker,
                                       int cellX, int cellY, int radius,
                                       boolean requireAnyEvacuated) {
        this(tracker, DEFAULT_PAYLOAD_ID, "civilian cohort",
                cellX, cellY, cellX, cellY, radius, requireAnyEvacuated);
    }

    public CivilianEvacuationObjective(
            CivilianEvacuationTracker tracker, String payloadId,
            String payloadName, int sourceCellX, int sourceCellY,
            int cellX, int cellY, int radius,
            boolean requireAnyEvacuated) {
        if (tracker == null) {
            throw new IllegalArgumentException("tracker is required");
        }
        if (payloadId == null || payloadId.isBlank()
                || payloadName == null || payloadName.isBlank()) {
            throw new IllegalArgumentException("stable evacuation payload identity required");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative");
        }
        this.tracker = tracker;
        this.payloadId = payloadId;
        this.payloadName = payloadName;
        this.sourceCellX = sourceCellX;
        this.sourceCellY = sourceCellY;
        this.cellX = cellX;
        this.cellY = cellY;
        this.radius = radius;
        this.requireAnyEvacuated = requireAnyEvacuated;
    }

    @Override
    public Faction owningFaction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        int activeX = 0;
        int activeY = 0;
        int active = 0;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE) {
                continue;
            }
            if (sim.resolveUnit(id) == 0L) {
                tracker.markLost(id);
                continue;
            }
            activeX += sim.world().cellX(id);
            activeY += sim.world().cellY(id);
            active++;
            // Legacy/objective-only fixtures still treat crossing the zone as
            // success. Production rescue missions attach a physical pickup;
            // their evacuation system owns boarding and waits for LANDED.
            if (!sim.hasCivilianPickupShuttle()) {
                int dx = Math.abs(sim.world().cellX(id) - cellX);
                int dy = Math.abs(sim.world().cellY(id) - cellY);
                if (dx <= radius && dy <= radius) {
                    tracker.markEvacuated(id);
                }
            }
        }
        payloadCellX = active > 0 ? activeX / active : cellX;
        payloadCellY = active > 0 ? activeY / active : cellY;
        if (alarmRaisedTick < 0 && (sim.isCivilianEvacuationTriggered()
                || tracker.evacuatedCount() > 0)) {
            alarmRaisedTick = sim.getSimTickIndex();
        }
        updateEscort(sim);
        if (tracker.activeCount() != 0 || !tracker.seal()) return;
        if (tracker.evacuatedCount() > 0 || !requireAnyEvacuated) {
            complete = true;
        } else {
            failed = true;
        }
    }

    public int cellX() {
        return cellX;
    }

    public int cellY() {
        return cellY;
    }

    public int radius() {
        return radius;
    }

    private void updateEscort(BattleView sim) {
        escortPresent = false;
        controllingSquadId = -1;
        int selected = Integer.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long marine = sim.liveUnitAt(i);
            if (sim.identity().faction(marine) != Faction.MARINE
                    || !sim.identity().type(marine).combatant) continue;
            for (int j = 0, m = tracker.registeredCount(); j < m; j++) {
                long payload = tracker.entityIdAt(j);
                if (tracker.state(payload) != CivilianEvacuationTracker.State.ACTIVE
                        || sim.resolveUnit(payload) == 0L) continue;
                int dx = sim.world().cellX(marine) - sim.world().cellX(payload);
                int dy = sim.world().cellY(marine) - sim.world().cellY(payload);
                if (dx * dx + dy * dy > 25) continue;
                escortPresent = true;
                Squad squad = sim.squadOf(marine);
                int squadId = squad != null ? squad.id : -1;
                if (squadId >= 0 && squadId < selected) selected = squadId;
                break;
            }
        }
        if (selected != Integer.MAX_VALUE) controllingSquadId = selected;
    }

    @Override public String payloadId() { return payloadId; }
    @Override public String payloadName() { return payloadName; }
    @Override public Kind payloadKind() { return Kind.COHORT; }
    @Override public int sourceCellX() { return sourceCellX; }
    @Override public int sourceCellY() { return sourceCellY; }
    @Override public int egressCellX() { return cellX; }
    @Override public int egressCellY() { return cellY; }
    @Override public int payloadCellX() { return payloadCellX; }
    @Override public int payloadCellY() { return payloadCellY; }
    @Override public int initialElements() { return tracker.expectedCount(); }
    @Override public int activeElements() { return tracker.activeCount(); }
    @Override public int boardedElements() { return tracker.evacuatedCount(); }
    @Override public int lostElements() { return tracker.lostCount(); }
    @Override public float normalizedProgress() {
        return tracker.expectedCount() > 0
                ? (float) tracker.evacuatedCount() / tracker.expectedCount() : 0f;
    }
    @Override public Phase extractionPhase() {
        if (complete) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        if (!alarmActive()) return Phase.AT_SOURCE;
        return tracker.evacuatedCount() > 0 ? Phase.BOARDING : Phase.IN_TRANSIT;
    }
    @Override public Failure failureReason() {
        return failed ? Failure.LOST : Failure.NONE;
    }
    @Override public boolean alarmActive() { return alarmRaisedTick >= 0; }
    @Override public int alarmRaisedTick() { return alarmRaisedTick; }
    @Override public int controllingSquadId() { return controllingSquadId; }
    @Override public boolean escortPresent() { return escortPresent; }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public boolean isFailed() {
        return failed;
    }

    @Override
    public String displayName() {
        return "Evacuate " + payloadName;
    }
}
