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
    /**
     * Which machine stands on each pad, by id; null where the gantry stands empty. A machine
     * keeps its pad when another leaves. Absent in older saves, which seat the mechs first.
     */
    private String[] pads = new String[CAPACITY];

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

    /** The mech standing on this pad, or null. */
    public CampaignMech mechOnPad(int pad) { return mechById(idOnPad(pad)); }

    /** The vehicle standing on this pad, or null. */
    public CampaignVehicle vehicleOnPad(int pad) { return vehicleById(idOnPad(pad)); }

    /** Whether a mech or a vehicle stands on this pad. */
    public boolean padHeld(int pad) { return idOnPad(pad) != null; }

    /** The pad this machine stands on, or -1. */
    public int padOf(String machineId) {
        for (int pad = 0; machineId != null && pad < CAPACITY; pad++) {
            if (machineId.equals(pads[pad])) return pad;
        }
        return -1;
    }

    /** Moves a machine of this lance onto a vacant pad; false when it has none here or the pad is taken. */
    public boolean standOn(String machineId, int pad) {
        int from = padOf(machineId);
        if (from < 0 || pad < 0 || pad >= CAPACITY || pads[pad] != null) return false;
        pads[from] = null;
        pads[pad] = machineId;
        return true;
    }

    /**
     * Brings machines standing past a smaller ship's working pads down onto vacant ones, so none is
     * left out of sight beside an empty pad; with no pad vacant it stays where it is.
     */
    public void fitPadsTo(int workingPads) {
        for (int pad = Math.max(0, workingPads); pad < CAPACITY; pad++) {
            if (pads[pad] == null) continue;
            for (int vacant = 0; vacant < workingPads; vacant++) {
                if (standOn(pads[pad], vacant)) break;
            }
        }
    }

    private String idOnPad(int pad) {
        return pad >= 0 && pad < CAPACITY ? pads[pad] : null;
    }

    /** Puts a machine on the first vacant pad. */
    private void seat(String machineId) {
        for (int pad = 0; pad < CAPACITY; pad++) {
            if (pads[pad] == null) {
                pads[pad] = machineId;
                return;
            }
        }
    }

    private void vacate(String machineId) {
        int pad = padOf(machineId);
        if (pad >= 0) pads[pad] = null;
    }

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
        seat(mech.id());
        return true;
    }

    public boolean add(CampaignVehicle vehicle) {
        if (vehicle == null || occupied() >= CAPACITY || vehicleById(vehicle.id()) != null) {
            return false;
        }
        vehicles.add(vehicle);
        seat(vehicle.id());
        return true;
    }

    /** The mech leaves its gantry; the gantry stands empty. */
    boolean remove(CampaignMech mech) {
        if (!mechs.remove(mech)) return false;
        vacate(mech.id());
        return true;
    }

    /** The vehicle leaves its gantry; the gantry stands empty. */
    boolean remove(CampaignVehicle vehicle) {
        if (!vehicles.remove(vehicle)) return false;
        vacate(vehicle.id());
        return true;
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
        if (pads == null || pads.length != CAPACITY) pads = new String[CAPACITY];
        for (int pad = 0; pad < CAPACITY; pad++) {
            if (mechById(pads[pad]) == null && vehicleById(pads[pad]) == null) pads[pad] = null;
        }
        for (CampaignMech mech : mechs) if (padOf(mech.id()) < 0) seat(mech.id());
        for (CampaignVehicle vehicle : vehicles) if (padOf(vehicle.id()) < 0) seat(vehicle.id());
        return this;
    }
}
