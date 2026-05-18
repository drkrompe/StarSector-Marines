package com.dillon.starsectormarines.battle.ui.picking;

/**
 * Mutable, shared selection state for the battle HUD. One instance lives on
 * {@link com.dillon.starsectormarines.battle.ui.BattleUiContext}; panels read
 * it to decide what to display, and click handlers (HUD rows today, a world
 * picker tomorrow) write it.
 *
 * <p>Squad ids match {@link com.dillon.starsectormarines.battle.Squad#id}.
 * {@link #NONE} (-1) is the sentinel for "nothing selected" — same convention
 * the sim uses for {@code Unit.NO_SQUAD}.
 */
public final class Selection {

    public static final int NONE = -1;

    private int selectedSquadId = NONE;

    public int getSelectedSquadId() {
        return selectedSquadId;
    }

    public void selectSquad(int squadId) {
        this.selectedSquadId = squadId;
    }

    public void clear() {
        this.selectedSquadId = NONE;
    }

    public boolean hasSquadSelection() {
        return selectedSquadId != NONE;
    }
}
