package com.dillon.starsectormarines.battle.ui.picking;

/**
 * Mutable, shared selection state for the battle HUD. One instance lives on
 * {@link com.dillon.starsectormarines.battle.ui.BattleUiContext}; panels read
 * it to decide what to display, and click handlers (HUD rows today, a world
 * picker tomorrow) write it.
 *
 * <p>Carries two related ids: a squad id (drives the whole detail-panel
 * filter) and an optional unit id within that squad. World-clicks set both
 * (you hit a specific member); HUD-row clicks set only the squad and clear
 * the unit (you picked the squad as a whole). The unit id is purely
 * diagnostic — current panels still filter on squad — but the
 * {@code SquadStateDumper} consumes it so a dump captures "which mech the
 * user was inspecting" for offline debugging of individual misbehavior.
 *
 * <p>Squad ids match {@link com.dillon.starsectormarines.battle.unit.Squad#id}.
 * {@link #NONE} (-1) is the sentinel for "nothing selected" — same convention
 * the sim uses for {@code Unit.NO_SQUAD}. Unit ids are {@link String} (matches
 * {@code Unit.id}), null when no unit is pinned.
 */
public final class Selection {

    public static final int NONE = -1;

    private int selectedSquadId = NONE;
    private String selectedUnitId;
    private int selectedVehicleIdx = NONE;

    public int getSelectedSquadId() {
        return selectedSquadId;
    }

    public String getSelectedUnitId() {
        return selectedUnitId;
    }

    /** Squad-only selection (e.g. HUD row click). Clears any prior unit pin so the dumper doesn't carry a stale member id forward. */
    public void selectSquad(int squadId) {
        this.selectedSquadId = squadId;
        this.selectedUnitId = null;
    }

    /** Selects a specific unit and its parent squad. Both ids must be in sync — pass the unit's own squadId, not a guessed one. */
    public void selectUnit(int squadId, String unitId) {
        this.selectedSquadId = squadId;
        this.selectedUnitId = unitId;
    }

    public int getSelectedVehicleIdx() { return selectedVehicleIdx; }

    public void selectVehicle(int vehicleIdx) {
        this.selectedVehicleIdx = vehicleIdx;
        this.selectedSquadId = NONE;
        this.selectedUnitId = null;
    }

    public void clear() {
        this.selectedSquadId = NONE;
        this.selectedUnitId = null;
        this.selectedVehicleIdx = NONE;
    }

    public boolean hasSquadSelection() {
        return selectedSquadId != NONE;
    }

    public boolean hasVehicleSelection() {
        return selectedVehicleIdx != NONE;
    }
}
