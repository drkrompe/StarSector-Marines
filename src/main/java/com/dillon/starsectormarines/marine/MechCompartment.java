package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.mech.MachineSystems;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The rules of a chassis's auxiliary compartment, the shared space that holds capacitor banks,
 * vent arrays and missile loaders (mechs-nouns.md, the auxiliary compartment). A chassis has one
 * compartment grid or two (the Bulwark's flank bays), and the grids pool: a set of parts is legal
 * when every footprint lands whole in one of them, no part overlapping another, and the role limits
 * hold (one capacitor bank, at most one loader, any number of vent arrays).
 *
 * <p>Every part is two columns wide and so is every grid, so a part never turns: it stands as
 * authored. A fitted part keeps the place it was given ({@link CompartmentFit}, the same on a mech and a
 * vehicle): taking one out moves nothing else, and a part goes in the bay it was dropped on.
 * {@link #arrange} honours those places; {@link #pack}, for a compartment with none, places the largest
 * parts first, into the first grid that takes them, in reading order.</p>
 */
public final class MechCompartment {

    /** The compartment grids a chassis may author, in packing order. */
    public static final List<SocketId> GRIDS = List.of(SocketId.AUXILIARY, SocketId.AUXILIARY_2);

    private MechCompartment() {
    }

    /** One part at its packed place: which grid, its top-left cell and its footprint. */
    public record Placement(MechInternal part, SocketId grid, int column, int row, int columns, int rows) {
        /** Where this part sits, as a place to keep. */
        public Spot spot() { return new Spot(grid, column, row); }
    }

    /**
     * Where a part is wanted: a cell of one grid ({@code column} and {@code row} its top-left), or anywhere in
     * {@code grid} when {@code column} is negative.
     */
    public record Spot(SocketId grid, int column, int row) {
        /** Anywhere in {@code grid}: the first place there that takes the part. */
        public static Spot in(SocketId grid) { return grid != null ? new Spot(grid, -1, -1) : null; }

        boolean exact() { return column >= 0 && row >= 0; }
    }

    /** The compartment grids this chassis has, in packing order; empty for one authored without. */
    public static List<SocketDef> of(MechVariant variant) {
        MechFittingLayout layout = MechFittingLayout.forVariant(variant);
        List<SocketDef> grids = new ArrayList<>();
        for (SocketId id : GRIDS) {
            SocketDef grid = layout.socket(id);
            if (grid != null) grids.add(grid);
        }
        return grids;
    }

    /** Cells across every grid. */
    public static int capacity(List<SocketDef> grids) {
        int cells = 0;
        for (SocketDef grid : grids) cells += grid.capacity();
        return cells;
    }

    /** How many of {@code role} one compartment may hold. */
    public static int limit(MechInternal.Role role) {
        return switch (role) {
            case CAPACITOR_BANK, LOADER, LINER, READY_RACK, DRONE_BAY, ACTIVE_PROTECTION -> 1;
            case VENT_ARRAY -> Integer.MAX_VALUE;
            case ENGINE, PLATING -> 0;
        };
    }

    /**
     * The loader {@code parts} carry, as the battle reads it (mechs-nouns.md, the loader): its replenisher, or
     * {@link MissileReplenisherComponent#NONE} for a compartment without one. Empty means nothing, on every machine.
     */
    public static MissileReplenisherComponent loader(List<MechInternal> parts) {
        for (MechInternal part : parts) if (part.role == MechInternal.Role.LOADER) return part.replenisher();
        return MissileReplenisherComponent.NONE;
    }

    /**
     * What {@code parts} make of the machine at the drop beyond its frame and loader ({@link MachineSystems}): the
     * structure and rounds every part multiplies, the same on a mech as on a vehicle.
     */
    public static MachineSystems systems(List<MechInternal> parts) {
        float structure = 1f, ammo = 1f, speed = 1f;
        List<DroneRole> drones = new ArrayList<>();
        boolean activeProtection = false;
        for (MechInternal part : parts) {
            structure *= part.structureFactor();
            ammo *= part.ammoFactor();
            speed *= part.speedFactor();
            drones.addAll(part.droneBay());
            activeProtection |= part.role == MechInternal.Role.ACTIVE_PROTECTION;
        }
        MachineSystems systems = new MachineSystems(structure, ammo, speed, drones, activeProtection);
        return systems.equals(MachineSystems.NONE) ? MachineSystems.NONE : systems;
    }

    /** Whether {@code parts} obey the role limits and pack into {@code variant}'s compartment. */
    public static boolean fits(MechVariant variant, List<MechInternal> parts) {
        return fits(of(variant), parts);
    }

    public static boolean fits(List<SocketDef> grids, List<MechInternal> parts) {
        if (parts.isEmpty()) return true;
        for (MechInternal.Role role : MechInternal.Role.values()) {
            int count = 0;
            for (MechInternal part : parts) if (part.role == role) count++;
            if (count > limit(role)) return false;
        }
        return pack(grids, parts) != null;
    }

    /** Where each part sits, in the order given, or null when they do not all fit. */
    public static List<Placement> pack(List<SocketDef> grids, List<MechInternal> parts) {
        int[] columns = new int[parts.size()], rows = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            columns[i] = parts.get(i).footprintColumns;
            rows[i] = parts.get(i).footprintRows;
        }
        int[][] at = packFootprints(grids, columns, rows);
        if (at == null) return null;
        List<Placement> placed = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            placed.add(new Placement(parts.get(i), grids.get(at[i][0]).id(), at[i][1], at[i][2], columns[i], rows[i]));
        }
        return List.copyOf(placed);
    }

    /**
     * Where each part sits, in the order given, with each part where {@code wanted} puts it (an entry lined up with
     * {@code parts}: a cell, a grid, or null for anywhere). Where they will not all go that way, the last part is let go
     * anywhere (it is the one being fitted); where even that fails, they {@link #pack} afresh. Null when they do not fit
     * at all.
     */
    public static List<Placement> arrange(List<SocketDef> grids, List<MechInternal> parts, List<Spot> wanted) {
        if (wanted == null || wanted.size() != parts.size()) return pack(grids, parts);
        List<Placement> placed = placeAt(grids, parts, wanted);
        int last = parts.size() - 1;
        if (placed == null && last >= 0 && wanted.get(last) != null) {
            List<Spot> loose = new ArrayList<>(wanted);
            if (wanted.get(last).exact()) {
                loose.set(last, Spot.in(wanted.get(last).grid()));
                placed = placeAt(grids, parts, loose);
            }
            if (placed == null) {
                loose.set(last, null);
                placed = placeAt(grids, parts, loose);
            }
        }
        return placed != null ? placed : pack(grids, parts);
    }

    private static List<Placement> placeAt(List<SocketDef> grids, List<MechInternal> parts, List<Spot> wanted) {
        int count = parts.size();
        int[] columns = new int[count], rows = new int[count], pins = new int[count];
        boolean[][][] taken = new boolean[grids.size()][][];
        for (int g = 0; g < grids.size(); g++) taken[g] = new boolean[grids.get(g).gridRows()][grids.get(g).gridColumns()];
        int[][] at = new int[count][];
        List<Integer> loose = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            columns[i] = parts.get(i).footprintColumns;
            rows[i] = parts.get(i).footprintRows;
            Spot spot = wanted.get(i);
            pins[i] = spot != null ? gridIndex(grids, spot.grid()) : -1;
            if (spot != null && pins[i] < 0) return null;
            if (spot != null && spot.exact()) {
                if (!free(taken[pins[i]], spot.column(), spot.row(), columns[i], rows[i])) return null;
                mark(taken[pins[i]], spot.column(), spot.row(), columns[i], rows[i], true);
                at[i] = new int[] {pins[i], spot.column(), spot.row()};
            } else {
                loose.add(i);
            }
        }
        loose.sort(Comparator.comparingInt((Integer i) -> -columns[i] * rows[i]).thenComparingInt(i -> i));
        if (!place(columns, rows, pins, loose, 0, taken, at)) return null;
        List<Placement> placed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            placed.add(new Placement(parts.get(i), grids.get(at[i][0]).id(), at[i][1], at[i][2], columns[i], rows[i]));
        }
        return List.copyOf(placed);
    }

    private static int gridIndex(List<SocketDef> grids, SocketId id) {
        for (int g = 0; g < grids.size(); g++) if (grids.get(g).id() == id) return g;
        return -1;
    }

    /**
     * The packing rule on bare footprints, {@code columns[i]} by {@code rows[i]}: the largest first, each into the
     * first grid and the first place in reading order that takes it, backtracking when the rest will not go. Each
     * index's place as {grid index, column, row}, or null when they do not all fit. A vehicle's kit rack packs by it
     * too ({@link VehicleRack}).
     */
    static int[][] packFootprints(List<SocketDef> grids, int[] columns, int[] rows) {
        int count = columns.length;
        if (count == 0) return new int[0][];
        if (grids.isEmpty()) return null;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> -columns[i] * rows[i]).thenComparingInt(i -> i));
        boolean[][][] taken = new boolean[grids.size()][][];
        for (int g = 0; g < grids.size(); g++) taken[g] = new boolean[grids.get(g).gridRows()][grids.get(g).gridColumns()];
        int[][] placed = new int[count][];
        int[] pins = new int[count];
        Arrays.fill(pins, -1);
        return place(columns, rows, pins, order, 0, taken, placed) ? placed : null;
    }

    /** The placements that landed in {@code grid}. */
    public static List<Placement> in(List<Placement> placements, SocketId grid) {
        List<Placement> out = new ArrayList<>();
        if (placements != null) for (Placement placement : placements) if (placement.grid() == grid) out.add(placement);
        return out;
    }

    private static boolean place(int[] columns, int[] rows, int[] pins, List<Integer> order, int next,
                                 boolean[][][] taken, int[][] placed) {
        if (next == order.size()) return true;
        int index = order.get(next);
        int width = columns[index], height = rows[index];
        for (int g = 0; g < taken.length; g++) {
            if (pins[index] >= 0 && g != pins[index]) continue;
            boolean[][] grid = taken[g];
            for (int row = 0; row < grid.length; row++) {
                for (int column = 0; column < grid[0].length; column++) {
                    if (!free(grid, column, row, width, height)) continue;
                    mark(grid, column, row, width, height, true);
                    placed[index] = new int[] {g, column, row};
                    if (place(columns, rows, pins, order, next + 1, taken, placed)) return true;
                    mark(grid, column, row, width, height, false);
                }
            }
        }
        placed[index] = null;
        return false;
    }

    private static boolean free(boolean[][] taken, int column, int row, int width, int height) {
        if (width < 1 || height < 1 || row + height > taken.length || column + width > taken[0].length) return false;
        for (int r = row; r < row + height; r++) {
            for (int c = column; c < column + width; c++) if (taken[r][c]) return false;
        }
        return true;
    }

    private static void mark(boolean[][] taken, int column, int row, int width, int height, boolean value) {
        for (int r = row; r < row + height; r++) {
            for (int c = column; c < column + width; c++) taken[r][c] = value;
        }
    }
}
