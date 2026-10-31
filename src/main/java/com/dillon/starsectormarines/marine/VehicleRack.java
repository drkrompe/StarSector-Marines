package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.HullReservoir;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of a hull's auxiliary compartment (mechs-nouns.md, one machine, one part catalog): a vehicle is a mech for
 * parts, so its compartment holds the mech's own internals (capacitor banks, vent arrays, loaders, a spall liner, a
 * ready rack, sensor drones, active protection) at their mech footprints, each fitted by its requirements
 * ({@link MechInternal#unmet}) and packed by the mech compartment's rule ({@link MechCompartment}). The compartment
 * is one 2x2 bay on a Light hull, one 2x3 bay on a Medium and two 2x2 bays on a Heavy.
 *
 * <p>An armed hull's flux reservoir is its compartment's bank and vent arrays ({@link #frame}): one with no bank is
 * incomplete and stays off the drop manifest, as a mech is ({@link #complete}). Every armed hull is built with a
 * bank, and the laser tank with vent arrays too ({@link #stockFit}); an unarmed hull needs none and takes none.</p>
 */
public final class VehicleRack {

    private VehicleRack() { }

    /** One part at its packed place: which bay, its top-left cell and its footprint. */
    public record Placement(MechInternal internal, SocketId grid, int column, int row, int columns, int rows) { }

    /** Rows across a hull's bays, a row two columns: two on a Light hull, three on a Medium, four on a Heavy. */
    public static int slots(VehicleType type) {
        int rows = 0;
        for (SocketDef bay : grids(type)) rows += bay.gridRows();
        return rows;
    }

    /** The compartment's bays, in packing order: the card or cards the doll draws. */
    public static List<SocketDef> grids(VehicleType type) {
        if (type == null) return List.of();
        return switch (type.weight()) {
            case LIGHT -> List.of(bay(SocketId.AUXILIARY, 2));
            case MEDIUM -> List.of(bay(SocketId.AUXILIARY, 3));
            case HEAVY -> List.of(bay(SocketId.AUXILIARY, 2), bay(SocketId.AUXILIARY_2, 2));
        };
    }

    private static SocketDef bay(SocketId id, int rows) {
        return new SocketDef(id, SocketType.AUXILIARY, 2, rows, 0f, 0f, 0f, 1f, 1f, 1f, false, "@gearSocketAuxiliary");
    }

    /**
     * Whether the compartment takes {@code internal} at all on {@code type} with its own gun: a compartment part whose
     * requirements the hull meets ({@link MechInternal#unmet}), as on any machine.
     */
    public static boolean takes(VehicleType type, MechInternal internal) {
        return takes(type, null, internal);
    }

    /** As above, on a hull carrying {@code gun} in its gun card ({@link MachineCapabilities#of(VehicleType, MechWeaponComponent)}). */
    public static boolean takes(VehicleType type, MechWeaponComponent gun, MechInternal internal) {
        return type != null && internal != null && internal.role.compartment()
                && internal.unmet(MachineCapabilities.of(type, gun)) == null;
    }

    /**
     * Whether {@code internals} fit {@code type}'s compartment: each one the compartment takes, within its role limits
     * (one bank, one loader), and all of them packing into the bays.
     */
    public static boolean fits(VehicleType type, List<MechInternal> internals) {
        return fits(type, null, internals);
    }

    /** As above, on a hull carrying {@code gun} in its gun card. */
    public static boolean fits(VehicleType type, MechWeaponComponent gun, List<MechInternal> internals) {
        for (MechInternal internal : internals) if (!takes(type, gun, internal)) return false;
        for (MechInternal.Role role : MechInternal.Role.values()) {
            int count = 0;
            for (MechInternal internal : internals) if (internal.role == role) count++;
            if (count > MechCompartment.limit(role)) return false;
        }
        return pack(type, internals) != null;
    }

    /** Where each part sits, in order, or null when they do not all fit. */
    public static List<Placement> pack(VehicleType type, List<MechInternal> internals) {
        List<SocketDef> grids = grids(type);
        int[] columns = new int[internals.size()], rows = new int[internals.size()];
        for (int i = 0; i < internals.size(); i++) {
            columns[i] = internals.get(i).footprintColumns;
            rows[i] = internals.get(i).footprintRows;
        }
        int[][] at = MechCompartment.packFootprints(grids, columns, rows);
        if (at == null) return null;
        List<Placement> placed = new ArrayList<>();
        for (int i = 0; i < internals.size(); i++) {
            placed.add(new Placement(internals.get(i), grids.get(at[i][0]).id(), at[i][1], at[i][2], columns[i], rows[i]));
        }
        return List.copyOf(placed);
    }

    /** The bank an armed hull is built with ({@link HullReservoir#stockBank}): the laser tank's own, the factory bank on the rest. */
    public static MechInternal stockBank(VehicleType type) {
        HullReservoir.Frame bank = HullReservoir.stockBank(type);
        if (bank == null) return null;
        if (bank == HullReservoir.MILITARY_BANK) return MechInternal.MILITARY_CAPACITOR_BANK;
        if (bank == HullReservoir.DOUBLE_BANK) return MechInternal.DOUBLE_CAPACITOR_BANK;
        return MechInternal.FACTORY_CAPACITOR_BANK;
    }

    /**
     * Every compartment part an armed hull is built with, in packing order: its bank, its stock vent arrays (the laser
     * tank's), then the standard loader, as a mech's stock frame carries one (an empty compartment means nothing on any
     * machine, so a hull with no loader never puts a missile pack back). A Light hull is full with bank and loader, as a
     * Hound is. Its {@link #frame} is the hull's stock frame ({@link HullReservoir#stockFrame}); an unarmed hull is
     * built with nothing in it.
     */
    public static List<MechInternal> stockFit(VehicleType type) {
        MechInternal bank = stockBank(type);
        if (bank == null) return List.of();
        List<MechInternal> fit = new ArrayList<>();
        fit.add(bank);
        for (int i = 0; i < HullReservoir.stockVentArrays(type); i++) fit.add(MechInternal.AUXILIARY_VENT_ARRAY);
        fit.add(MechInternal.STANDARD_LOADER);
        return List.copyOf(fit);
    }

    /** The loader the compartment's parts give the hull at the drop ({@link MechCompartment#loader}). */
    public static MissileReplenisherComponent loader(List<MechInternal> internals) {
        return MechCompartment.loader(internals);
    }

    /** Whether the hull may drop: an unarmed hull always, an armed one only with a capacitor bank in its compartment. */
    public static boolean complete(VehicleType type, List<MechInternal> internals) {
        return type == null || !type.hasTurretWeapon() || bank(internals) != null;
    }

    /**
     * The flux frame the compartment gives the hull at the drop: the bank's capacity and base dissipation, each vent array
     * adding its own, as on a mech; null without a bank.
     */
    public static HullReservoir.Frame frame(List<MechInternal> internals) {
        MechInternal bank = bank(internals);
        if (bank == null) return null;
        float dissipation = 0f;
        for (MechInternal internal : internals) {
            if (internal.role == MechInternal.Role.CAPACITOR_BANK || internal.role == MechInternal.Role.VENT_ARRAY) {
                dissipation += internal.dissipation;
            }
        }
        return new HullReservoir.Frame(bank.fluxCapacity, dissipation);
    }

    private static MechInternal bank(List<MechInternal> internals) {
        for (MechInternal internal : internals) if (internal.role == MechInternal.Role.CAPACITOR_BANK) return internal;
        return null;
    }
}
