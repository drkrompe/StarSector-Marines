package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whole machines the company has set aside: boats, mechs and vehicles taken off their berths
 * and gantries with their identity, callsign, fit and repair clock intact. Stored, a machine
 * flies and fights nothing and so is never lost in a battle.
 *
 * <p>The hangar is the company's, like its boats and stores, so what is in it goes along when
 * the company changes ship. How many it holds is the home ship's reserve-hangar refit, asked
 * at each order ({@code capacity}); a ship without room still lets everything be recalled.
 *
 * <p>Every order is atomic: it checks room, the machine and the vacant place it goes to before
 * anything moves, so a refusal leaves the deck, the bay and the hangar as they were.
 */
public final class ReserveHangar implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<CampaignBoat> boats = new ArrayList<>();
    private List<CampaignMech> mechs = new ArrayList<>();
    private List<CampaignVehicle> vehicles = new ArrayList<>();

    public enum Status { STORED, RECALLED, HANGAR_FULL, UNKNOWN_MACHINE, NO_VACANT_PLACE }

    public List<CampaignBoat> boats() { return Collections.unmodifiableList(boats); }
    public List<CampaignMech> mechs() { return Collections.unmodifiableList(mechs); }
    public List<CampaignVehicle> vehicles() { return Collections.unmodifiableList(vehicles); }

    /** Every machine stored, of every kind. */
    public int stored() { return boats.size() + mechs.size() + vehicles.size(); }

    /** Takes a boat off her berth into the hangar; the berth stands vacant. */
    public Status storeBoat(BoatDeck deck, String boatId, int capacity) {
        if (stored() >= capacity) return Status.HANGAR_FULL;
        CampaignBoat boat = deck.vacate(boatId);
        if (boat == null) return Status.UNKNOWN_MACHINE;
        boats.add(boat);
        return Status.STORED;
    }

    /** Stands a stored boat in the deck's first vacant berth; her pattern must be the one its bays hold. */
    public Status recallBoat(BoatDeck deck, String boatId) {
        CampaignBoat boat = find(boats, boatId);
        if (boat == null) return Status.UNKNOWN_MACHINE;
        int berth = vacantBerthFor(deck, boat);
        if (berth >= 0 && deck.berth(berth, boat)) {
            boats.remove(boat);
            return Status.RECALLED;
        }
        return Status.NO_VACANT_PLACE;
    }

    /** The first vacant berth on this deck a stored boat can stand in, or -1; a founding wreck's berth is not vacant. */
    public static int vacantBerthFor(BoatDeck deck, CampaignBoat boat) {
        if (boat.pattern() != deck.pattern()) return -1;
        for (int berth : deck.vacantBerths()) if (!deck.wreckInBerth(berth)) return berth;
        return -1;
    }

    /** Takes a mech or a vehicle off its gantry into the hangar; the gantry stands vacant. */
    public Status storeMachine(MechBay bay, String machineId, int capacity) {
        if (stored() >= capacity) return Status.HANGAR_FULL;
        if (bay.mechById(machineId) != null) {
            mechs.add(bay.stowMech(machineId));
            return Status.STORED;
        }
        CampaignVehicle vehicle = bay.stowVehicle(machineId);
        if (vehicle == null) return Status.UNKNOWN_MACHINE;
        vehicles.add(vehicle);
        return Status.STORED;
    }

    /** Puts a stored mech or vehicle on a vacant gantry of {@code squadId}, within its working berths. */
    public Status recallMachine(MechBay bay, String machineId, String squadId, int workingBerths) {
        CampaignMech mech = find(mechs, machineId);
        CampaignVehicle vehicle = mech == null ? find(vehicles, machineId) : null;
        if (mech == null && vehicle == null) return Status.UNKNOWN_MACHINE;
        CampaignMechSquad squad = bay.squadById(squadId);
        if (squad == null || squad.occupied() >= workingBerths || !bay.canAddMech(squadId)) return Status.NO_VACANT_PLACE;
        if (mech != null) {
            if (!bay.addMech(squadId, mech)) return Status.NO_VACANT_PLACE;
            mechs.remove(mech);
        } else {
            if (!bay.berthVehicle(squadId, vehicle)) return Status.NO_VACANT_PLACE;
            vehicles.remove(vehicle);
        }
        return Status.RECALLED;
    }

    private static <T> T find(List<T> machines, String id) {
        for (T machine : machines) {
            String machineId = machine instanceof CampaignBoat boat ? boat.id()
                    : machine instanceof CampaignMech mech ? mech.id() : ((CampaignVehicle) machine).id();
            if (machineId.equals(id)) return machine;
        }
        return null;
    }

    private Object readResolve() {
        if (boats == null) boats = new ArrayList<>();
        if (mechs == null) mechs = new ArrayList<>();
        if (vehicles == null) vehicles = new ArrayList<>();
        return this;
    }
}
