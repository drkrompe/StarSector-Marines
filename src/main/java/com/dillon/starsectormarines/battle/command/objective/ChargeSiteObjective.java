package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
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

    private final int cellX;
    private final int cellY;
    private final float plantDuration;
    private final String displayName;

    private float progress = 0f;
    private boolean complete = false;
    private boolean planterOnSiteThisTick = false;

    public ChargeSiteObjective(int cellX, int cellY, float plantDuration, String displayName) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.plantDuration = plantDuration;
        this.displayName = displayName;
    }

    public int cellX() { return cellX; }
    public int cellY() { return cellY; }
    public float progress() { return progress; }
    public float plantDuration() { return plantDuration; }
    public boolean planterOnSite() { return planterOnSiteThisTick; }

    @Override
    public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete) return;
        planterOnSiteThisTick = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (sim.role().role(u.entityId) != UnitRole.PLANTER) continue;
            if (u.assignedObjective != this) continue;
            if (sim.world().cellX(u.entityId) == cellX && sim.world().cellY(u.entityId) == cellY && sim.world().moveProgress(u.entityId) == 0f) {
                planterOnSiteThisTick = true;
                break;
            }
        }
        if (planterOnSiteThisTick) {
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
