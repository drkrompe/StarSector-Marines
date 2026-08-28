package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;

import java.util.List;

/**
 * Names for the cells of an autotile block, so a piece can be assigned to one.
 *
 * <p>A {@link GridLayout} resolves a cell from a four-neighbour mask in which
 * each flag means <b>"the exterior is on this side"</b> — not "the neighbour is
 * a wall". The slot names here follow that reading: {@code n} is the piece drawn
 * for a wall whose exposed face points north, and {@code center} is the enclosed
 * case that a hollow layout leaves to its fill colour.
 *
 * <p>Getting that backwards produces a sheet whose walls are inside out, and
 * nothing downstream can detect it — every id resolves, every cell is opaque,
 * and the room is simply wrong. It is the one part of this pipeline where the
 * wording of a label is load-bearing.
 */
public final class BlockSlots {

    private BlockSlots() {}

    /** The single slot of a {@link GridLayout#SINGLE} block. */
    public static final String ONLY = "only";

    /** Row-major, which is both reading order and the order a split plate arrives in. */
    private static final List<String> NINE = List.of(
            "nw", "n", "ne",
            "w", "center", "e",
            "sw", "s", "se");

    /** Every slot of a layout, in the order a split plate's pieces arrive. */
    public static List<String> of(GridLayout layout) {
        return layout.span() == 1 ? List.of(ONLY) : NINE;
    }

    /** The {@code {col, row}} offset of a named slot within its block. */
    public static int[] offset(String slot) {
        String name = slot == null ? "" : slot.trim().toLowerCase();
        if (name.equals(ONLY) || name.isEmpty()) return new int[]{0, 0};
        int index = NINE.indexOf(name);
        if (index < 0) throw new IllegalArgumentException("unknown block slot '" + slot + "'");
        return new int[]{index % 3, index / 3};
    }

    /** The name of the slot at a {@code (col, row)} offset within a block. */
    public static String name(int col, int row) {
        if (col < 0 || row < 0 || col > 2 || row > 2) {
            throw new IllegalArgumentException("no slot at " + col + "," + row);
        }
        return NINE.get(row * 3 + col);
    }

    /** Whether a slot exists in a layout — a 1x1 block has no {@code ne}. */
    public static boolean fits(GridLayout layout, String slot) {
        try {
            int[] offset = offset(slot);
            return offset[0] < layout.span() && offset[1] < layout.span();
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    /** What the mask flag behind a slot means, for a label the operator can check against the art. */
    public static String describe(String slot) {
        int[] offset = offset(slot);
        if (slot == null || slot.trim().isEmpty() || slot.trim().equalsIgnoreCase(ONLY)) {
            return "the block's only cell";
        }
        String vertical = offset[1] == 0 ? "north" : offset[1] == 2 ? "south" : "";
        String horizontal = offset[0] == 0 ? "west" : offset[0] == 2 ? "east" : "";
        if (vertical.isEmpty() && horizontal.isEmpty()) return "enclosed — no exterior on any side";
        if (vertical.isEmpty()) return "exterior on the " + horizontal;
        if (horizontal.isEmpty()) return "exterior on the " + vertical;
        return "exterior on the " + vertical + " and " + horizontal;
    }
}
