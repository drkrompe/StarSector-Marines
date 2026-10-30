package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
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
 * authored. The arrangement is computed, never stored. {@link #pack} places the largest parts first,
 * into the first grid that takes them, in reading order, so the same parts always land in the same
 * places and the doll draws what the rule found.</p>
 */
public final class MechCompartment {

    /** The compartment grids a chassis may author, in packing order. */
    public static final List<SocketId> GRIDS = List.of(SocketId.AUXILIARY, SocketId.AUXILIARY_2);

    private MechCompartment() {
    }

    /** One part at its packed place: which grid, its top-left cell and its footprint. */
    public record Placement(MechInternal part, SocketId grid, int column, int row, int columns, int rows) {
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
            case CAPACITOR_BANK, LOADER -> 1;
            case VENT_ARRAY -> Integer.MAX_VALUE;
            case ENGINE, PLATING -> 0;
        };
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
        return place(columns, rows, order, 0, taken, placed) ? placed : null;
    }

    /** The placements that landed in {@code grid}. */
    public static List<Placement> in(List<Placement> placements, SocketId grid) {
        List<Placement> out = new ArrayList<>();
        if (placements != null) for (Placement placement : placements) if (placement.grid() == grid) out.add(placement);
        return out;
    }

    private static boolean place(int[] columns, int[] rows, List<Integer> order, int next,
                                 boolean[][][] taken, int[][] placed) {
        if (next == order.size()) return true;
        int index = order.get(next);
        int width = columns[index], height = rows[index];
        for (int g = 0; g < taken.length; g++) {
            boolean[][] grid = taken[g];
            for (int row = 0; row < grid.length; row++) {
                for (int column = 0; column < grid[0].length; column++) {
                    if (!free(grid, column, row, width, height)) continue;
                    mark(grid, column, row, width, height, true);
                    placed[index] = new int[] {g, column, row};
                    if (place(columns, rows, order, next + 1, taken, placed)) return true;
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
