package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine objective for physically securing a sealed archive inside one map
 * zone. Recovery requires uninterrupted marine presence in that room.
 */
public final class ColonyArchiveObjective implements ExtractionPayloadObjective {

    public static final float RECOVERY_DURATION = 5f;

    private final int cellX;
    private final int cellY;
    private final int zoneId;
    private final float recoveryDuration;
    private float progress;
    private boolean recovered;
    private int alarmRaisedTick = -1;

    public ColonyArchiveObjective(int cellX, int cellY, int zoneId) {
        this(cellX, cellY, zoneId, RECOVERY_DURATION);
    }

    ColonyArchiveObjective(int cellX, int cellY, int zoneId,
                           float recoveryDuration) {
        if (zoneId < 0 || recoveryDuration <= 0f) {
            throw new IllegalArgumentException("valid archive zone required");
        }
        this.cellX = cellX;
        this.cellY = cellY;
        this.zoneId = zoneId;
        this.recoveryDuration = recoveryDuration;
    }

    @Override
    public Faction owningFaction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim) {
        if (recovered) return;
        boolean marinePresent = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            int unitZone = sim.getZoneGraph().zoneIdAt(
                    sim.world().cellX(unit), sim.world().cellY(unit));
            if (unitZone == zoneId) {
                marinePresent = true;
                break;
            }
        }
        if (!marinePresent) {
            progress = 0f;
            return;
        }
        if (alarmRaisedTick < 0) alarmRaisedTick = sim.getSimTickIndex();
        progress += BattleSimulation.TICK_DT;
        if (progress >= recoveryDuration) {
            progress = recoveryDuration;
            recovered = true;
        }
    }

    public int cellX() {
        return cellX;
    }

    public int cellY() {
        return cellY;
    }

    public int zoneId() {
        return zoneId;
    }

    public float progress() {
        return progress;
    }

    public boolean isRecovered() {
        return recovered;
    }

    @Override public String payloadId() { return "COLONY-ARCHIVE"; }
    @Override public String payloadName() { return "sealed colony archive"; }
    @Override public Kind payloadKind() { return Kind.ARCHIVE; }
    @Override public int sourceCellX() { return cellX; }
    @Override public int sourceCellY() { return cellY; }
    @Override public int egressCellX() { return -1; }
    @Override public int egressCellY() { return -1; }
    @Override public int payloadCellX() { return cellX; }
    @Override public int payloadCellY() { return cellY; }
    @Override public int initialElements() { return 1; }
    @Override public int activeElements() { return recovered ? 0 : 1; }
    @Override public int boardedElements() { return -1; }
    @Override public int lostElements() { return 0; }
    @Override public float normalizedProgress() {
        return Math.min(1f, progress / recoveryDuration);
    }
    @Override public Phase extractionPhase() {
        if (recovered) return Phase.COMPLETE;
        return progress > 0f ? Phase.SECURING : Phase.AT_SOURCE;
    }
    @Override public Failure failureReason() { return Failure.NONE; }
    @Override public boolean alarmActive() { return alarmRaisedTick >= 0; }
    @Override public int alarmRaisedTick() { return alarmRaisedTick; }
    @Override public int controllingSquadId() { return -1; }
    @Override public boolean escortPresent() { return progress > 0f; }

    @Override
    public boolean isComplete() {
        return recovered;
    }

    @Override
    public boolean isFailed() {
        return false;
    }

    @Override
    public String displayName() {
        return "Recover sealed colony archive";
    }
}
