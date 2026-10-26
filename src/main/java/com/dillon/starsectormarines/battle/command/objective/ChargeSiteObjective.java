package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.fort.FortSensors;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.combat.BodyPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Marine objective: the charge at a site is its kit squad's job. The <b>kit</b> is carried by a live
 * {@link UnitRole#PLANTER} assigned to this objective; the carrier and every live infantry squad mate of a carrier is
 * <b>eligible</b>. Progress ticks up while any eligible body stands within {@link #PLANT_RADIUS_CELLS} of the site
 * cell with a structural line to it, settled or not, and resets to zero the moment none does. Mechs and vehicles never
 * plant.
 *
 * <p>No carrier, no plant: when the carrier dies the kit drops ({@code EquipmentDrop}), the squad no longer holds it,
 * and a squad mate already beside the site does not keep the channel; progress resets. Whoever retrieves the kit for
 * this site becomes its planter, and their squad can plant again. That gives contested plants a natural "the defender
 * just interrupted them" feel without bespoke damage-hookup logic.
 *
 * <p>One objective per charge site. A SABOTAGE mission registers one of these
 * per target structure; marine victory requires all of them complete.
 */
public final class ChargeSiteObjective implements Objective {

    /** How near the site cell an eligible body works the charge, in cells (Euclidean). UNTUNED first pass. */
    public static final float PLANT_RADIUS_CELLS = 3f;

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
    /**
     * Whether the charge is being worked this tick: an eligible body (the kit carrier or a squad mate) is within
     * {@link #PLANT_RADIUS_CELLS} of the site. The name predates the squad rule; command, the HUD and traces read it as
     * "being planted".
     */
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
        planterOnSiteThisTick = worked(sim);
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

    /** Whether an eligible body is in reach of the charge this tick: see the class rule. */
    private boolean worked(BattleView sim) {
        List<Long> carriers = new ArrayList<>(2);
        List<Integer> kitSquads = new ArrayList<>(2);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.role().role(u) != UnitRole.PLANTER || sim.task().assignedObjective(u) != this) continue;
            carriers.add(u);
            if (sim.squad().hasSquad(u)) kitSquads.add(sim.squad().squadId(u));
        }
        if (carriers.isEmpty()) return false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            boolean eligible = carriers.contains(u)
                    || sim.squad().hasSquad(u) && kitSquads.contains(sim.squad().squadId(u));
            if (eligible && inReach(sim, u)) return true;
        }
        return false;
    }

    /** An infantry body on the ground within the plant radius of the site with a structural line to it. */
    private boolean inReach(BattleView sim, long u) {
        if (sim.identity().type(u).bodyPlan() != BodyPlan.INFANTRY || !sim.world().hasPosition(u) || sim.isRiding(u))
            return false;
        float dx = sim.world().x(u) - (cellX + .5f), dy = sim.world().y(u) - (cellY + .5f);
        if (dx * dx + dy * dy > PLANT_RADIUS_CELLS * PLANT_RADIUS_CELLS) return false;
        return FortSensors.lineOpen(sim.getGrid(), sim.world().cellX(u), sim.world().cellY(u), cellX, cellY, null);
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return false; }

    @Override
    public String displayName() { return displayName; }
}
