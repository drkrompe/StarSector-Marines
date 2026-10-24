package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;

/**
 * A fortification lot's works drawn in its guns' style (`fort-works.md`). A generator marks the cells of each lot it
 * builds (a defence post's layout, a fort's whole footprint) with the lot's {@link EmplacementStyle} on the
 * {@link CellTopology}; the battle then dresses every wall, parapet and barricade on those cells in that style's art.
 *
 * <p>Presentation only. A styled piece keeps its catalog id, cover, catch band, footprint and blocking, and a wall keeps
 * its surface id, so every rule reads a lot the same in any style: only the picture comes from the style's look. A
 * look is a catalog entry named {@code <base id>@<style key>} ({@link #lookId}): a doodad def for a piece, a grid
 * block for a wall surface. A piece with no look in a style draws its own art.
 */
public final class FortWorks {

    private FortWorks() {}

    /** The catalog id of {@code baseId}'s look in {@code style}. */
    public static String lookId(String baseId, EmplacementStyle style) {
        return baseId + "@" + style.key;
    }

    /** The style the works on the cell were built in, or null for a cell that is no lot's. */
    public static EmplacementStyle styleAt(CellTopology topology, int x, int y) {
        int value = topology.getWorksStyle(x, y);
        return value == 0 ? null : EmplacementStyle.values()[value - 1];
    }

    /** Marks the cell as part of a lot built in {@code style}. */
    public static void mark(CellTopology topology, int x, int y, EmplacementStyle style) {
        topology.setWorksStyle(x, y, style == null ? 0 : style.ordinal() + 1);
    }

    /**
     * Brings each lot into its posts' style. A lot's style is its guns' style, so a post restyled after the map was
     * generated ({@code EmplacementStylePolicy.restyle}, a mission theming old works) takes the whole lot it stands in
     * with it: every cell joined to the post's guns through cells of the lot's old style. A post standing in no lot
     * marks its own layout.
     */
    public static void settle(CellTopology topology, List<DefensePost> posts) {
        for (DefensePost post : posts) {
            int target = post.style.ordinal() + 1;
            for (DefensePost.TurretSpec turret : post.turrets) {
                int from = topology.getWorksStyle(turret.cellX, turret.cellY);
                if (from == target) continue;
                if (from == 0) markLayout(topology, post);
                else flood(topology, turret.cellX, turret.cellY, from, target);
            }
        }
    }

    private static void markLayout(CellTopology topology, DefensePost post) {
        DefensePostLayoutRegistry registry = DefensePostLayoutRegistry.installed();
        DefensePostLayoutDef layout = registry == null || post.layoutId == null ? null : registry.get(post.layoutId);
        if (layout == null) {
            for (DefensePost.TurretSpec turret : post.turrets) mark(topology, turret.cellX, turret.cellY, post.style);
            return;
        }
        for (DefensePostLayoutDef.Cell cell : layout.cells)
            mark(topology, post.anchorX + cell.offset().x(), post.anchorY + cell.offset().y(), post.style);
    }

    private static void flood(CellTopology topology, int x0, int y0, int from, int to) {
        int width = topology.getWidth();
        IntArrayList open = new IntArrayList();
        open.add(y0 * width + x0);
        topology.setWorksStyle(x0, y0, to);
        while (!open.isEmpty()) {
            int index = open.removeInt(open.size() - 1);
            int x = index % width, y = index / width;
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (topology.getWorksStyle(nx, ny) != from) continue;
                topology.setWorksStyle(nx, ny, to);
                open.add(ny * width + nx);
            }
        }
    }

    /**
     * {@code placed} in the style of the lot its anchor cell belongs to, or {@code placed} itself where the cell is no
     * lot's, the piece is not a catalog piece, or its style has no look for it.
     */
    public static Doodad dress(Doodad placed, CellTopology topology, TileRegistry registry) {
        if (registry == null || placed.definitionId() == null) return placed;
        EmplacementStyle style = styleAt(topology, placed.cellX, placed.cellY);
        return style == null ? placed : dress(placed, placed.definitionId(), style.key, registry);
    }

    /**
     * {@code placed} wearing the look of {@code shownId} in the style named {@code look}: how a damage state keeps the
     * style of the piece it replaced. Unchanged when the style has no look for it, or a look whose source span is not
     * the piece's (a look never changes a footprint).
     */
    public static Doodad dress(Doodad placed, String shownId, String look, TileRegistry registry) {
        if (look == null || registry == null) return placed;
        DoodadDef art = registry.doodad(shownId + "@" + look);
        if (art == null || art.footprintCellsX != placed.sourceCellsX || art.footprintCellsY != placed.sourceCellsY)
            return placed;
        return placed.dressedAs(art, look);
    }
}
