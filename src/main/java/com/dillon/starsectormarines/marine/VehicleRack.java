package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.vehicle.HullReservoir;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The rules of a vehicle's kit rack (mechs-nouns.md, vehicle kit): a vehicle is a mech for parts, so the rack holds
 * the mech's own capacitor banks and vent arrays at their mech footprints beside the vehicle's kits, a slot (a
 * two-column row) a kit, and packs them by the mech compartment's rule ({@link MechCompartment}). The rack is one
 * 2x2 bay on a Light hull, one 2x3 bay on a Medium and two 2x2 bays on a Heavy.
 *
 * <p>An armed hull's flux reservoir is its rack's bank and vent arrays ({@link #frame}): one with no bank is
 * incomplete and stays off the drop manifest, as a mech is ({@link #complete}). Every armed hull is built with a
 * bank ({@link #stockBank}); an unarmed hull needs none and takes none.</p>
 */
public final class VehicleRack {

    private VehicleRack() { }

    /** One thing on the rack: a kit, or a capacitor bank or vent array. */
    public record Part(VehicleKit kit, MechInternal internal) {
        public Part {
            if ((kit == null) == (internal == null)) throw new IllegalArgumentException("A rack part is one thing");
        }

        public int columns() { return kit != null ? 2 : internal.footprintColumns; }

        public int rows() { return kit != null ? 1 : internal.footprintRows; }
    }

    /** One part at its packed place: which bay, its top-left cell and its footprint. */
    public record Placement(Part part, SocketId grid, int column, int row, int columns, int rows) { }

    /** Rack slots on a hull ({@link VehicleKit#slots}). */
    public static int slots(VehicleType type) {
        return VehicleKit.slots(type);
    }

    /** The rack's bays, in packing order: the card or cards the doll draws. */
    public static List<SocketDef> grids(VehicleType type) {
        if (type == null) return List.of();
        return switch (type.weight()) {
            case LIGHT -> List.of(bay(SocketId.AUXILIARY, 2));
            case MEDIUM -> List.of(bay(SocketId.AUXILIARY, 3));
            case HEAVY -> List.of(bay(SocketId.AUXILIARY, 2), bay(SocketId.AUXILIARY_2, 2));
        };
    }

    private static SocketDef bay(SocketId id, int rows) {
        return new SocketDef(id, SocketType.AUXILIARY, 2, rows, 0f, 0f, 0f, 1f, 1f, 1f, false, "@vehicleDollKitRack");
    }

    /** Whether the rack takes {@code internal} at all: a capacitor bank or a vent array, on an armed hull. */
    public static boolean takes(VehicleType type, MechInternal internal) {
        return type != null && type.hasTurretWeapon() && internal != null
                && (internal.role == MechInternal.Role.CAPACITOR_BANK || internal.role == MechInternal.Role.VENT_ARRAY);
    }

    /**
     * Whether {@code kits} and {@code internals} together fit {@code type}'s rack: each kit suiting the hull and none
     * twice, each internal one the rack takes, at most one bank, and all of it packing into the bays.
     */
    public static boolean fits(VehicleType type, List<VehicleKit> kits, List<MechInternal> internals) {
        Set<VehicleKit> seen = EnumSet.noneOf(VehicleKit.class);
        for (VehicleKit kit : kits) if (kit == null || !kit.suits(type) || !seen.add(kit)) return false;
        int banks = 0;
        for (MechInternal internal : internals) {
            if (!takes(type, internal)) return false;
            if (internal.role == MechInternal.Role.CAPACITOR_BANK) banks++;
        }
        return banks <= MechCompartment.limit(MechInternal.Role.CAPACITOR_BANK) && pack(type, kits, internals) != null;
    }

    /** Where each part sits, the internals first and then the kits, or null when they do not all fit. */
    public static List<Placement> pack(VehicleType type, List<VehicleKit> kits, List<MechInternal> internals) {
        List<Part> parts = new ArrayList<>();
        for (MechInternal internal : internals) parts.add(new Part(null, internal));
        for (VehicleKit kit : kits) parts.add(new Part(kit, null));
        List<SocketDef> grids = grids(type);
        int[] columns = new int[parts.size()], rows = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            columns[i] = parts.get(i).columns();
            rows[i] = parts.get(i).rows();
        }
        int[][] at = MechCompartment.packFootprints(grids, columns, rows);
        if (at == null) return null;
        List<Placement> placed = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            placed.add(new Placement(parts.get(i), grids.get(at[i][0]).id(), at[i][1], at[i][2], columns[i], rows[i]));
        }
        return List.copyOf(placed);
    }

    /** The bank an armed hull is built with: the military bank on the laser tank, the factory bank on the rest. */
    public static MechInternal stockBank(VehicleType type) {
        HullReservoir.Frame stock = HullReservoir.stockFrame(type);
        if (stock == null) return null;
        return stock == HullReservoir.MILITARY_BANK
                ? MechInternal.MILITARY_CAPACITOR_BANK : MechInternal.FACTORY_CAPACITOR_BANK;
    }

    /** Whether the hull may drop: an unarmed hull always, an armed one only with a capacitor bank on its rack. */
    public static boolean complete(VehicleType type, List<MechInternal> internals) {
        return type == null || !type.hasTurretWeapon() || bank(internals) != null;
    }

    /**
     * The flux frame the rack gives the hull at the drop: the bank's capacity and base dissipation, each vent array
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
