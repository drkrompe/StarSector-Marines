package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * The hold rule ({@code extraction-command.md}, Generic recovery law): a settled Marine combatant at a cell, with no
 * defender combatant there, for an uninterrupted time. Any tick without it starts the count again. A source's release
 * and an egress's boarding are both this rule at different cells, radii and durations.
 */
final class UncontestedHold {

    private float duration;
    private float progress;
    private boolean held;
    private boolean escortPresent;
    private boolean contested;
    private int controllingSquadId = -1;

    UncontestedHold(float duration) {
        setDuration(duration);
    }

    /**
     * One tick of the hold at a cell.
     *
     * @param zone the zone the escort and the defender must stand in, or negative for any
     * @return whether the hold completed on this tick
     */
    boolean tick(BattleView sim, int x, int y, int radius, int zone) {
        if (held) return false;
        EscortPresence.Escort escort = EscortPresence.nearestMarine(sim, x, y, radius, zone);
        contested = EscortPresence.combatantPresent(sim, Faction.DEFENDER, x, y, radius, zone);
        escortPresent = escort.present() && !contested;
        controllingSquadId = escortPresent ? escort.squadId() : -1;
        if (!escortPresent) {
            progress = 0f;
            return false;
        }
        progress += BattleSimulation.TICK_DT;
        if (progress < duration) return false;
        progress = duration;
        held = true;
        return true;
    }

    /** A shorter or longer hold from now on; progress already made counts toward it. */
    void setDuration(float duration) {
        if (duration <= 0f) throw new IllegalArgumentException("hold duration must be positive");
        this.duration = duration;
        if (!held && progress >= duration) progress = duration;
    }

    /** Clears who holds the cell once the hold no longer applies (the package left, or the mission ended). */
    void release() {
        escortPresent = false;
        controllingSquadId = -1;
    }

    float duration() { return duration; }
    float progress() { return progress; }
    boolean held() { return held; }
    boolean escortPresent() { return escortPresent; }
    boolean contested() { return contested; }
    int controllingSquadId() { return controllingSquadId; }
}
