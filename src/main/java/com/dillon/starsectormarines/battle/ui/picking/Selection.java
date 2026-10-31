package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.warship.Warship;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;

/**
 * Mutable, shared selection state for the battle HUD. One instance lives on
 * {@link com.dillon.starsectormarines.battle.ui.BattleUiContext}; panels read
 * it to decide what to display, and click handlers in both HUD rows and the
 * world picker write it.
 *
 * <p>Carries a deduplicated squad set for commands and two focus ids:
 * a primary squad id (drives the detail-panel
 * filter) and an optional unit id within that squad. World-clicks set both
 * (you hit a specific member); HUD-row clicks set only the squad and clear
 * the unit (you picked the squad as a whole). Exact-member controls such as
 * the selected-Mech doctrine plate consume the unit id, and the
 * {@code SquadStateDumper} records it so an offline dump retains which mech
 * the player was inspecting.
 * A click on a convoy vehicle selects it alone, so a new world click cannot leave
 * a stale vehicle command target behind; a box or a Shift click instead builds a
 * <b>group</b> of squads and vehicles together ({@link #selectGroup}). The vehicle
 * focus ({@link #getSelectedVehicleId}) is set only while no squad is the primary,
 * so the vehicle plate shows for a vehicle-only scope and the squad plate otherwise.
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
    private final Set<Integer> selectedSquads = new LinkedHashSet<>();
    private int hoveredSquadId = NONE;
    private long selectedUnitEntityId = 0L;
    /** Selected convoy vehicle by world entity id; {@code 0L} == none. Id-keyed (not a list
     *  index) so it stays valid as the convoy list mutates — GONE vehicles are reaped. */
    private long selectedVehicleId = 0L;
    /** Every vehicle in the command scope, the focused one included, in selection order. */
    private final Set<Long> selectedVehicles = new LinkedHashSet<>();
    /** Selected friendly warship; it has no entity id, so the hull itself is held. Exclusive with every other form. */
    private Warship selectedWarship;
    /**
     * A selected civilian of the rescue cohort, {@code 0L} == none: picked only by a plain click, never by a box, so
     * a right click on one of the company's forces rallies its group to it. Exclusive with every other form.
     */
    private long selectedCivilianId = 0L;

    public int getSelectedSquadId() {
        return selectedSquadId;
    }

    public long getSelectedUnitEntityId() {
        return selectedUnitEntityId;
    }

    /** Stable command scope; the primary squad separately drives the detail plate. */
    public List<Integer> getSelectedSquadIds() { return List.copyOf(selectedSquads); }
    public boolean isSquadSelected(int id) { return selectedSquads.contains(id); }
    public int squadCount() { return selectedSquads.size(); }
    public int getHoveredSquadId() { return hoveredSquadId; }
    public void hoverSquad(int id) { hoveredSquadId = id; }

    /** Replaces or extends the command scope, pinning the member nearest the gesture. */
    public void selectSquads(Collection<Integer> ids, int primary, long unit, boolean additive) {
        selectGroup(ids, List.of(), primary, unit, 0L, additive);
    }

    /**
     * Replaces or extends the command scope with squads and vehicles together, as a box or a Shift
     * click does. A squad among them takes the focus ({@code primary} and its {@code unit} when
     * given); with none, the vehicle {@code primaryVehicle} does, else the first vehicle. A warship
     * never joins a group.
     */
    public void selectGroup(Collection<Integer> squads, Collection<Long> vehicles,
                            int primary, long unit, long primaryVehicle, boolean additive) {
        if (!additive || selectedWarship != null || selectedCivilianId != 0L) clear();
        for (int id : squads) if (id != NONE) selectedSquads.add(id);
        for (long id : vehicles) if (id != 0L) selectedVehicles.add(id);
        if (!selectedSquads.isEmpty()) {
            selectedVehicleId = 0L;
            if (selectedSquads.contains(primary)) {
                selectedSquadId = primary;
                selectedUnitEntityId = unit;
            } else if (!selectedSquads.contains(selectedSquadId)) {
                selectedSquadId = selectedSquads.iterator().next();
                selectedUnitEntityId = 0L;
            }
        } else if (!selectedVehicles.isEmpty()) {
            selectedSquadId = NONE;
            selectedUnitEntityId = 0L;
            if (selectedVehicles.contains(primaryVehicle)) selectedVehicleId = primaryVehicle;
            else if (!selectedVehicles.contains(selectedVehicleId)) selectedVehicleId = selectedVehicles.iterator().next();
        }
    }

    /** Casualties remove only their own squad, preserving surviving group members. */
    public void retainSquads(IntPredicate keep) {
        selectedSquads.removeIf(id -> !keep.test(id));
        if (!selectedSquads.contains(selectedSquadId)) {
            selectedSquadId = selectedSquads.stream().findFirst().orElse(NONE);
            selectedUnitEntityId = 0L;
        }
        if (selectedSquadId == NONE && selectedVehicleId == 0L && !selectedVehicles.isEmpty()) {
            selectedVehicleId = selectedVehicles.iterator().next();
        }
    }

    /** A wrecked or departed vehicle leaves the scope; the rest of the group stays. */
    public void retainVehicles(LongPredicate keep) {
        selectedVehicles.removeIf(id -> !keep.test(id));
        if (selectedVehicleId != 0L && !selectedVehicles.contains(selectedVehicleId)) {
            selectedVehicleId = selectedSquadId == NONE && !selectedVehicles.isEmpty()
                    ? selectedVehicles.iterator().next() : 0L;
        }
    }

    /** Every vehicle in the command scope, in selection order. */
    public List<Long> getSelectedVehicleIds() { return List.copyOf(selectedVehicles); }
    public boolean isVehicleSelected(long id) { return selectedVehicles.contains(id); }
    public int vehicleCount() { return selectedVehicles.size(); }

    /** Squad-only selection (e.g. HUD row click). Clears any prior unit pin so the dumper doesn't carry a stale member id forward. */
    public void selectSquad(int squadId) {
        selectedSquads.clear();
        selectedVehicles.clear();
        if (squadId != NONE) selectedSquads.add(squadId);
        this.selectedSquadId = squadId;
        this.selectedUnitEntityId = 0L;
        this.selectedVehicleId = 0L;
        this.selectedWarship = null;
        this.selectedCivilianId = 0L;
    }

    /** Selects a specific unit and its parent squad. Both ids must be in sync — pass the unit's own squadId, not a guessed one. {@code unitEntityId} is the world entity id ({@code 0L} = none). */
    public void selectUnit(int squadId, long unitEntityId) {
        selectedSquads.clear();
        selectedVehicles.clear();
        if (squadId != NONE) selectedSquads.add(squadId);
        this.selectedSquadId = squadId;
        this.selectedUnitEntityId = unitEntityId;
        this.selectedVehicleId = 0L;
        this.selectedWarship = null;
        this.selectedCivilianId = 0L;
    }

    public long getSelectedVehicleId() { return selectedVehicleId; }

    public void selectVehicle(long vehicleId) {
        selectedSquads.clear();
        selectedVehicles.clear();
        if (vehicleId != 0L) selectedVehicles.add(vehicleId);
        this.selectedVehicleId = vehicleId;
        this.selectedWarship = null;
        this.selectedCivilianId = 0L;
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
    }

    public void clear() {
        selectedSquads.clear();
        selectedVehicles.clear();
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
        this.selectedVehicleId = 0L;
        this.selectedWarship = null;
        this.selectedCivilianId = 0L;
    }

    public boolean hasSquadSelection() {
        return selectedSquadId != NONE;
    }

    public boolean hasVehicleSelection() {
        return selectedVehicleId != 0L;
    }

    /** The selected civilian, or {@code 0L}. */
    public long getSelectedCivilianId() { return selectedCivilianId; }

    public boolean hasCivilianSelection() { return selectedCivilianId != 0L; }

    /** Selects a civilian of the rescue cohort; exclusive with every other form. */
    public void selectCivilian(long civilianId) {
        clear();
        this.selectedCivilianId = civilianId;
    }

    /** The selected friendly warship, or {@code null}. */
    public Warship getSelectedWarship() { return selectedWarship; }

    public boolean hasWarshipSelection() { return selectedWarship != null; }

    /** Selects a warship; like a vehicle, it is exclusive with every squad form. */
    public void selectWarship(Warship ship) {
        selectedSquads.clear();
        selectedVehicles.clear();
        this.selectedWarship = ship;
        this.selectedCivilianId = 0L;
        this.selectedSquadId = NONE;
        this.selectedUnitEntityId = 0L;
        this.selectedVehicleId = 0L;
    }
}
