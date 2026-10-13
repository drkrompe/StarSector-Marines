package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * The few live side effects a commander may make after its plan is committed, behind a narrow interface so a
 * strategy never holds the live battle. Pure planning stays pure: the plan wishes, and
 * {@link AutonomousMissionCommand#afterCommit} acts here, once, serially.
 */
public interface CommandEffects {

    /** No effects available: a battle that is not controllable, or a test. */
    CommandEffects NONE = new CommandEffects() {
        @Override public int tick() { return -1; }
        @Override public int mechLancesRemaining(Faction side) { return 0; }
        @Override public MechDropService.Outcome callMechLance(Faction side, SquadCommandClaim claim, int hintX, int hintY) {
            return MechDropService.Outcome.NO_ALLOWANCE;
        }
    };

    int tick();

    /** Lances {@code side} may still call down. */
    int mechLancesRemaining(Faction side);

    /** Calls a lance down at the drop zone near the hint, claimed by {@code claim}. */
    MechDropService.Outcome callMechLance(Faction side, SquadCommandClaim claim, int hintX, int hintY);

    /** The effects of a live battle, or {@link #NONE} where it cannot be controlled. */
    static CommandEffects over(BattleView sim) {
        if (!(sim instanceof BattleControl control)) return NONE;
        return new CommandEffects() {
            @Override public int tick() { return control.getSimTickIndex(); }
            @Override public int mechLancesRemaining(Faction side) { return control.mechDrops().lancesRemaining(side); }
            @Override public MechDropService.Outcome callMechLance(Faction side, SquadCommandClaim claim, int hintX, int hintY) {
                return control.callMechDrop(side, claim, hintX, hintY);
            }
        };
    }
}
