package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The company ship's bay as it stood at launch: one entry per occupied bay space, mechs
 * in gantry order and then vehicles, frozen. Calling Mech Support picks from it; nothing
 * in battle edits a fit or adds an entry ({@code mechs-nouns.md}, Armor drop).
 */
public final class BayManifest {

    /** One bay space's machine. {@code index} is its stable place in the manifest. */
    public record Entry(int index, ArmorLoad load) {

        public boolean isVehicle() { return load.isVehicle(); }

        public LiftClass weightClass() { return load.liftClass(); }

        /** Chassis or hull name: {@code Hound}, {@code Heavy APC}. */
        public String typeName() {
            if (!isVehicle()) return load.mech().variant().displayName();
            StringBuilder name = new StringBuilder();
            for (String word : load.vehicle().type().name().toLowerCase(Locale.ROOT).split("_")) {
                if (name.length() > 0) name.append(' ');
                name.append(word.equals("apc") ? "APC"
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
            return name.toString();
        }

        /** The machine's campaign callsign, or {@code null} for an unnamed one. */
        public String callsign() {
            return isVehicle() ? load.vehicle().callsign() : load.mech().displayName();
        }
    }

    private final List<Entry> entries;

    private BayManifest(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    /** Freezes the bay: mechs first, in order, then vehicles. Nulls are skipped. */
    public static BayManifest of(List<MechDeploymentSpec> mechs,
                                 List<VehicleDeploymentSpec> vehicles) {
        List<Entry> entries = new ArrayList<>();
        if (mechs != null) {
            for (MechDeploymentSpec mech : mechs) {
                if (mech != null) entries.add(new Entry(entries.size(), ArmorLoad.of(mech)));
            }
        }
        if (vehicles != null) {
            for (VehicleDeploymentSpec vehicle : vehicles) {
                if (vehicle != null) entries.add(new Entry(entries.size(), ArmorLoad.of(vehicle)));
            }
        }
        return new BayManifest(entries);
    }

    public List<Entry> entries() { return entries; }

    public int size() { return entries.size(); }

    public Entry get(int index) { return entries.get(index); }
}
