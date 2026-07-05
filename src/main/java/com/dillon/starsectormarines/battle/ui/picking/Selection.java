package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.squad.Squad;

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
 * <p>Squad ids match {@link com.dillon.starsectormarines.battle.squad.Squad#id}.
 * {@link #NONE} (-1) is the sentinel for "nothing selected" — same convention
 * the sim uses for {@code Squad.NO_SQUAD}. The pinned unit is the world entity id
 * (a {@code long}); {@code 0L} == none (the sim's "no entity" sentinel) — the
 * stable machine identity, replacing the old greppable-string {@code Entity.id}.
 */
public final class Selection {

    public static final int NONE = -1;

    private int selectedSquadId = NONE;
    private long selectedUnitEntityId = 0L;
    /** Selected convoy vehicle by world entity id; {@code 0L} == none. Id-keyed (not a list
     *  index) so it stays valid as the convoy list mutates — GONE vehicles are reaped. */
    private long selectedVehicleId = 0L;

    public int getSelectedSquadId() {
        return selectedSquadId;
    }

    public long getSelectedUnitEntityId() {
        return selectedUnitEntityId;
    }

    /** Squad-only selection (e.g. HUD row click). Clears any prior unit pin so the dumper doesn't carry a stale member id forward. */
    public void selectSquad(int squadId) {
        this.selectedSquadId = squadId;
        this.selectedUnitEntityId = 0L;
    }

    /** Selects a specific unit and its parent squad. Both ids must be in sync — pass the unit's own squadId, not a guessed one. {@code unitEntityId} is the world entity id ({@code 0L} = none). */
    public void selectUnit(int squadId, long unitEntityId) {
        this.selectedSquadId = squadId;
        this.selectedUnitEntityId = unitEntityId;
    }

    public long getSelectedVehicleId() { return selectedVehicleId; }

    public void selectVehicle(long vehicleId) {
        this.selectedVehicleId = vehicleId;
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
    }

    public void clear() {
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
        this.selectedVehicleId = 0L;
    }

    public boolean hasSquadSelection() {
        return selectedSquadId != NONE;
    }

    public boolean hasVehicleSelection() {
        return selectedVehicleId != 0L;
    }
}
