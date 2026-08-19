package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine objective for physically securing a sealed archive inside one map
 * zone. Recovery requires uninterrupted marine presence in that room.
 */
public final class ColonyArchiveObjective implements Objective {

    public static final float RECOVERY_DURATION = 5f;

    private final int cellX;
    private final int cellY;
    private final int zoneId;
    private final float recoveryDuration;
    private float progress;
    private boolean recovered;

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
