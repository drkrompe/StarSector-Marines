package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent player-facing mech squad; battle realizes it as one mech lance. Its gantries
 * are shared: each holds a mech or a vehicle, and {@link #CAPACITY} counts both.
 */
public final class CampaignMechSquad implements Serializable {

    public static final int CAPACITY = 4;

    private final String id;
    private String displayName;
    private List<CampaignMech> mechs = new ArrayList<>();
    /** Absent in older saves: every gantry there was a mech, and stays one. */
    private List<CampaignVehicle> vehicles = new ArrayList<>();

    public CampaignMechSquad(String id, String displayName) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Squad id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Squad name is required");
        }
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<CampaignMech> mechs() { return Collections.unmodifiableList(mechs); }
    public List<CampaignVehicle> vehicles() { return Collections.unmodifiableList(vehicles); }

    /** Gantries holding a mech or a vehicle. */
    public int occupied() { return mechs.size() + vehicles.size(); }

    public CampaignVehicle vehicleById(String vehicleId) {
        if (vehicleId == null) return null;
        for (CampaignVehicle vehicle : vehicles) {
            if (vehicleId.equals(vehicle.id())) return vehicle;
        }
        return null;
    }

    public CampaignMech mechById(String mechId) {
        if (mechId == null) return null;
        for (CampaignMech mech : mechs) {
            if (mechId.equals(mech.id())) return mech;
        }
        return null;
    }

    public boolean add(CampaignMech mech) {
        if (mech == null || occupied() >= CAPACITY || mechById(mech.id()) != null) {
            return false;
        }
        mechs.add(mech);
        return true;
    }

    public boolean add(CampaignVehicle vehicle) {
        if (vehicle == null || occupied() >= CAPACITY || vehicleById(vehicle.id()) != null) {
            return false;
        }
        vehicles.add(vehicle);
        return true;
    }

    /** The vehicle leaves its gantry; the gantry stands empty. */
    boolean remove(CampaignVehicle vehicle) {
        return vehicles.remove(vehicle);
    }

    private Object readResolve() {
        if (mechs == null) mechs = new ArrayList<>();
        mechs.removeIf(mech -> mech == null);
        if (mechs.size() > CAPACITY) {
            mechs = new ArrayList<>(mechs.subList(0, CAPACITY));
        }
        if (vehicles == null) vehicles = new ArrayList<>();
        vehicles.removeIf(vehicle -> vehicle == null);
        int vehicleRoom = CAPACITY - mechs.size();
        if (vehicles.size() > vehicleRoom) {
            vehicles = new ArrayList<>(vehicles.subList(0, vehicleRoom));
        }
        return this;
    }
}
