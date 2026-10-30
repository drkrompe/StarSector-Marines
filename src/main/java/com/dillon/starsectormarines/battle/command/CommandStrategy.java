package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

/** Common identity shared by legacy commands and frame-only strategies. */
public interface CommandStrategy {

    /** The faction whose squads this strategy may command. */
    Faction faction();

    /**
     * Whether this strategy directs its side's machines itself (calls lances down, commits them). One that does not
     * leaves them to {@link CompanyMachineAttachment}, which attaches each to one of its squads.
     */
    default boolean commandsMachines() { return false; }
}
