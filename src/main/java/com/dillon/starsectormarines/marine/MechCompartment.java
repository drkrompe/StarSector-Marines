package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The rules of a chassis's auxiliary compartment: the one shared well that holds capacitor banks,
 * vent arrays and missile loaders (mechs-nouns.md, the auxiliary compartment). A set of parts is
 * legal when every footprint packs whole onto the grid, no part overlapping another, and the role
 * limits hold: one capacitor bank, at most one loader, any number of vent arrays.
 *
 * <p>The arrangement is computed, never stored. {@link #pack} places the largest pieces first and
 * tries every cell in reading order upright before any turned, so the same parts always land in the
 * same places and the doll draws what the rule found.</p>
 */
public final class MechCompartment {

    private MechCompartment() {
    }

    /** One part at its packed place: top-left cell and its footprint as placed. */
    public record Placement(MechInternal part, int column, int row, int columns, int rows) {
    }

    /** The compartment this chassis has, or null for a chassis authored without one. */
    public static SocketDef of(MechVariant variant) {
        return MechFittingLayout.forVariant(variant).socket(SocketId.AUXILIARY);
    }

    /** How many of {@code role} one compartment may hold. */
    public static int limit(MechInternal.Role role) {
        return switch (role) {
            case CAPACITOR_BANK, LOADER -> 1;
            case VENT_ARRAY -> Integer.MAX_VALUE;
            case ENGINE, PLATING -> 0;
        };
    }

    /** Whether {@code parts} obey the role limits and pack onto {@code variant}'s compartment. */
    public static boolean fits(MechVariant variant, List<MechInternal> parts) {
        return fits(of(variant), parts);
    }

    public static boolean fits(SocketDef compartment, List<MechInternal> parts) {
        if (parts.isEmpty()) return true;
        for (MechInternal.Role role : MechInternal.Role.values()) {
            int count = 0;
            for (MechInternal part : parts) if (part.role == role) count++;
            if (count > limit(role)) return false;
        }
        return pack(compartment, parts) != null;
    }

    /**
     * Where each part sits, in the order given, or null when they do not all fit. An empty list
     * packs into anything, a missing compartment holds nothing.
     */
    public static List<Placement> pack(SocketDef compartment, List<MechInternal> parts) {
        if (parts.isEmpty()) return List.of();
        if (compartment == null) return null;
        int columns = compartment.gridColumns();
        int rows = compartment.gridRows();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> -area(parts.get(i))).thenComparingInt(i -> i));
        Placement[] placed = new Placement[parts.size()];
        boolean[][] taken = new boolean[rows][columns];
        if (!place(parts, order, 0, taken, placed)) return null;
        return List.of(placed);
    }

    private static boolean place(List<MechInternal> parts, List<Integer> order, int next,
                                 boolean[][] taken, Placement[] placed) {
        if (next == order.size()) return true;
        int index = order.get(next);
        MechInternal part = parts.get(index);
        int rows = taken.length, columns = taken[0].length;
        int[][] shapes = part.footprintColumns == part.footprintRows
                ? new int[][] {{part.footprintColumns, part.footprintRows}}
                : new int[][] {{part.footprintColumns, part.footprintRows}, {part.footprintRows, part.footprintColumns}};
        // Upright anywhere before turned anywhere, so a part only lies on its side when it must.
        for (int[] shape : shapes) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    if (!free(taken, column, row, shape[0], shape[1])) continue;
                    mark(taken, column, row, shape[0], shape[1], true);
                    placed[index] = new Placement(part, column, row, shape[0], shape[1]);
                    if (place(parts, order, next + 1, taken, placed)) return true;
                    mark(taken, column, row, shape[0], shape[1], false);
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

    private static int area(MechInternal part) {
        return part.footprintColumns * part.footprintRows;
    }
}
