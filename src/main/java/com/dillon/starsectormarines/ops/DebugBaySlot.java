package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;

/** One of the debug briefing's player bay slots: empty, a mech chassis or a vehicle. */
public final class DebugBaySlot {
    public static final int SLOTS = 4;
    public static final DebugBaySlot EMPTY = new DebugBaySlot(null, null);

    private static final List<DebugBaySlot> OPTIONS = options();

    public final MechVariant mech;
    public final VehicleType vehicle;

    private DebugBaySlot(MechVariant mech, VehicleType vehicle) {
        this.mech = mech; this.vehicle = vehicle;
    }

    public static DebugBaySlot of(MechVariant mech) { return new DebugBaySlot(mech, null); }
    public static DebugBaySlot of(VehicleType vehicle) { return new DebugBaySlot(null, vehicle); }

    /** Empty first, then every mech chassis, then every vehicle. */
    public static List<DebugBaySlot> options() {
        if (OPTIONS != null) return OPTIONS;
        List<DebugBaySlot> all = new ArrayList<>();
        all.add(EMPTY);
        for (MechVariant mech : MechVariant.values()) all.add(of(mech));
        for (VehicleType vehicle : VehicleType.values()) all.add(of(vehicle));
        return List.copyOf(all);
    }

    /** The option {@code step} places from {@code current}, wrapping. */
    public static DebugBaySlot cycle(DebugBaySlot current, int step) {
        List<DebugBaySlot> all = options();
        int index = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).sameAs(current)) index = i;
        return all.get(Math.floorMod(index + step, all.size()));
    }

    public boolean sameAs(DebugBaySlot other) {
        return other != null && mech == other.mech && vehicle == other.vehicle;
    }
}
