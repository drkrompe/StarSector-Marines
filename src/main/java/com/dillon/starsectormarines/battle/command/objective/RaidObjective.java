package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine Raid contract: seize one authored target, then return the recovered
 * package to the authored egress. The package is abstract rather than bound to
 * one carrier; after seizure, any surviving Marine combatant can complete the
 * extraction. This prevents carrier-death bookkeeping from becoming a hidden
 * second objective while retaining the required strike-and-egress shape.
 */
public final class RaidObjective implements Objective {

    public static final float DEFAULT_SERVICE_DURATION = 6f;
    public static final int TARGET_RADIUS = 2;
    public static final int EGRESS_RADIUS = 3;

    public enum Phase { APPROACH, SERVICE, EGRESS, COMPLETE }

    /** Identity-free target alarm available to the installation defender. */
    public record TargetAlarm(boolean active, int raisedTick) {
        public static TargetAlarm quiet() { return new TargetAlarm(false, -1); }
    }

    private final String targetId;
    private final String targetName;
    private final int targetCellX;
    private final int targetCellY;
    private final int targetZoneId;
    private final int egressCellX;
    private final int egressCellY;
    private final float serviceDuration;

    private float serviceProgress;
    private boolean marineServicing;
    private boolean targetSecured;
    private boolean complete;
    private int alarmRaisedTick = -1;

    public RaidObjective(String targetId, String targetName,
                         int targetCellX, int targetCellY, int targetZoneId,
                         int egressCellX, int egressCellY) {
        this(targetId, targetName, targetCellX, targetCellY, targetZoneId,
                egressCellX, egressCellY, DEFAULT_SERVICE_DURATION);
    }

    RaidObjective(String targetId, String targetName,
                  int targetCellX, int targetCellY, int targetZoneId,
                  int egressCellX, int egressCellY, float serviceDuration) {
        if (targetId == null || targetId.isBlank()
                || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("stable Raid target identity required");
        }
        if (targetZoneId < 0 || serviceDuration <= 0f) {
            throw new IllegalArgumentException("valid Raid target zone required");
        }
        this.targetId = targetId;
        this.targetName = targetName;
        this.targetCellX = targetCellX;
        this.targetCellY = targetCellY;
        this.targetZoneId = targetZoneId;
        this.egressCellX = egressCellX;
        this.egressCellY = egressCellY;
        this.serviceDuration = serviceDuration;
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete) return;
        if (!targetSecured) {
            boolean marinePresent = combatantPresent(sim, Faction.MARINE,
                    targetCellX, targetCellY, TARGET_RADIUS, targetZoneId, true);
            boolean defenderPresent = combatantPresent(sim, Faction.DEFENDER,
                    targetCellX, targetCellY, TARGET_RADIUS, targetZoneId, false);
            marineServicing = marinePresent && !defenderPresent;
            if (!marineServicing) {
                serviceProgress = 0f;
                return;
            }
            if (alarmRaisedTick < 0) alarmRaisedTick = sim.getSimTickIndex();
            serviceProgress += BattleSimulation.TICK_DT;
            if (serviceProgress >= serviceDuration) {
                serviceProgress = serviceDuration;
                targetSecured = true;
                marineServicing = false;
            }
            return;
        }
        complete = combatantPresent(sim, Faction.MARINE,
                egressCellX, egressCellY, EGRESS_RADIUS, -1, true);
    }

    private static boolean combatantPresent(BattleView sim, Faction faction,
                                             int centerX, int centerY, int radius,
                                             int requiredZone,
                                             boolean mustBeSettled) {
        int radiusSquared = radius * radius;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != faction
                    || !sim.identity().type(unit).combatant) continue;
            int dx = sim.world().cellX(unit) - centerX;
            int dy = sim.world().cellY(unit) - centerY;
            if (dx * dx + dy * dy > radiusSquared) continue;
            if (requiredZone >= 0 && sim.getZoneGraph().zoneIdAt(
                    sim.world().cellX(unit), sim.world().cellY(unit))
                    != requiredZone) continue;
            if (!mustBeSettled || sim.movement().settled(unit)) return true;
        }
        return false;
    }

    public String targetId() { return targetId; }
    public String targetName() { return targetName; }
    public int targetCellX() { return targetCellX; }
    public int targetCellY() { return targetCellY; }
    public int targetZoneId() { return targetZoneId; }
    public int egressCellX() { return egressCellX; }
    public int egressCellY() { return egressCellY; }
    public float serviceProgress() { return serviceProgress; }
    public float serviceDuration() { return serviceDuration; }
    public boolean marineServicing() { return marineServicing; }
    public boolean targetSecured() { return targetSecured; }
    public Phase phase() {
        if (complete) return Phase.COMPLETE;
        if (targetSecured) return Phase.EGRESS;
        return marineServicing || serviceProgress > 0f ? Phase.SERVICE : Phase.APPROACH;
    }
    public TargetAlarm defenderAlarm() {
        return alarmRaisedTick >= 0 ? new TargetAlarm(true, alarmRaisedTick)
                : TargetAlarm.quiet();
    }

    @Override public boolean isComplete() { return complete; }
    @Override public boolean isFailed() { return false; }
    @Override public String displayName() {
        return "Seize " + targetName + " and extract";
    }
}
