package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

/** Common identity shared by legacy commands and frame-only strategies. */
public interface CommandStrategy {

    /** The faction whose squads this strategy may command. */
    Faction faction();
}
