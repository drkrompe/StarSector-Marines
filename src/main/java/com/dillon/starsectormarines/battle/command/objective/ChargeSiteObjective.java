package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

/**
 * Marine objective: a planter must stand on the site cell long enough to
 * complete the {@link #plantDuration} channel. Progress ticks up while any
 * assigned, alive {@link UnitRole#PLANTER} is standing on the site cell —
 * and resets to zero the moment no eligible planter is on-site (got pushed
 * off, fell back, died). That gives contested plants a natural "the defender
 * just interrupted them" feel without bespoke damage-hookup logic.
 *
 * <p>One objective per charge site. A SABOTAGE mission registers one of these
 * per target structure; marine victory requires all of them complete.
 */
public final class ChargeSiteObjective implements Objective {

    /** Coarse defender alarm hold after the last legally detected tamper. */
    public static final int ALARM_HOLD_TICKS = Math.round(15f / BattleSimulation.TICK_DT);

    public record SiteAlarm(boolean active, int raisedTick, int expiresTick) {
        public static SiteAlarm quiet() { return new SiteAlarm(false, -1, -1); }
    }

    private final int cellX;
    private final int cellY;
    private final float plantDuration;
    private final String siteId;
    private final String displayName;

    private float progress = 0f;
    private boolean complete = false;
    private boolean planterOnSiteThisTick = false;
    private int alarmRaisedTick = -1;
    private int alarmLastTamperTick = -1;

    public ChargeSiteObjective(int cellX, int cellY, float plantDuration, String displayName) {
        this(cellX, cellY, plantDuration, displayName, displayName);
    }

    public ChargeSiteObjective(int cellX, int cellY, float plantDuration,
                               String siteId, String displayName) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.plantDuration = plantDuration;
        this.siteId = siteId;
        this.displayName = displayName;
    }

    public int cellX() { return cellX; }
    public int cellY() { return cellY; }
    public float progress() { return progress; }
    public float plantDuration() { return plantDuration; }
    public String siteId() { return siteId; }
    public boolean planterOnSite() { return planterOnSiteThisTick; }

    /**
     * Identity-free installation alarm legally available to the owning
     * defender.  The authored site cell is already known; this reports no
     * attacker identity, cell, role, squad, or planting progress.
     */
    public SiteAlarm defenderAlarm(int tick) {
        if (complete || alarmLastTamperTick < 0
                || tick > alarmLastTamperTick + ALARM_HOLD_TICKS) {
            return SiteAlarm.quiet();
        }
        return new SiteAlarm(true, alarmRaisedTick,
                alarmLastTamperTick + ALARM_HOLD_TICKS);
    }

    @Override
    public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete) {
            planterOnSiteThisTick = false;
            return;
        }
        planterOnSiteThisTick = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.role().role(u) != UnitRole.PLANTER) continue;
            if (sim.task().assignedObjective(u) != this) continue;
            if (sim.movement().atCell(u, cellX, cellY) && sim.movement().settled(u)) {
                planterOnSiteThisTick = true;
                break;
            }
        }
        if (planterOnSiteThisTick) {
            int tick = sim.getSimTickIndex();
            if (alarmLastTamperTick < 0
                    || tick > alarmLastTamperTick + ALARM_HOLD_TICKS) {
                alarmRaisedTick = tick;
            }
            alarmLastTamperTick = tick;
            progress += BattleSimulation.TICK_DT;
            if (progress >= plantDuration) {
                progress = plantDuration;
                complete = true;
            }
        } else {
            // Interrupted — reset. Sabotage isn't a save-state action; the
            // planter has to dwell uninterrupted from start to finish.
            progress = 0f;
        }
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return false; }

    @Override
    public String displayName() { return displayName; }
}
