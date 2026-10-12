package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.warship.Warship;

/**
 * Mutable, shared selection state for the battle HUD. One instance lives on
 * {@link com.dillon.starsectormarines.battle.ui.BattleUiContext}; panels read
 * it to decide what to display, and click handlers in both HUD rows and the
 * world picker write it.
 *
 * <p>Carries two related ids: a squad id (drives the whole detail-panel
 * filter) and an optional unit id within that squad. World-clicks set both
 * (you hit a specific member); HUD-row clicks set only the squad and clear
 * the unit (you picked the squad as a whole). Exact-member controls such as
 * the selected-Mech doctrine plate consume the unit id, and the
 * {@code SquadStateDumper} records it so an offline dump retains which mech
 * the player was inspecting.
 * Convoy-vehicle selection is mutually exclusive with both squad forms so a
 * new world click cannot leave a stale vehicle command target behind.
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
    /** Selected friendly warship; it has no entity id, so the hull itself is held. Exclusive with every other form. */
    private Warship selectedWarship;

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
        this.selectedVehicleId = 0L;
        this.selectedWarship = null;
    }

    /** Selects a specific unit and its parent squad. Both ids must be in sync — pass the unit's own squadId, not a guessed one. {@code unitEntityId} is the world entity id ({@code 0L} = none). */
    public void selectUnit(int squadId, long unitEntityId) {
        this.selectedSquadId = squadId;
        this.selectedUnitEntityId = unitEntityId;
        this.selectedVehicleId = 0L;
        this.selectedWarship = null;
    }

    public long getSelectedVehicleId() { return selectedVehicleId; }

    public void selectVehicle(long vehicleId) {
        this.selectedVehicleId = vehicleId;
        this.selectedWarship = null;
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
    }

    public void clear() {
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
        this.selectedVehicleId = 0L;
        this.selectedWarship = null;
    }

    public boolean hasSquadSelection() {
        return selectedSquadId != NONE;
    }

    public boolean hasVehicleSelection() {
        return selectedVehicleId != 0L;
    }

    /** The selected friendly warship, or {@code null}. */
    public Warship getSelectedWarship() { return selectedWarship; }

    public boolean hasWarshipSelection() { return selectedWarship != null; }

    /** Selects a warship; like a vehicle, it is exclusive with every squad form. */
    public void selectWarship(Warship ship) {
        this.selectedWarship = ship;
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
        this.selectedVehicleId = 0L;
    }
}
