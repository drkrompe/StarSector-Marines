package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One structure pool per crown, indexed by its actual cells; overlapping crowns retain obstruction. */
public final class TreeDamage {
    public static final int STARTING_HP = 200;
    public enum Result { NOT_TREE, UNCHANGED, DAMAGED, DESTROYED }
    @FunctionalInterface public interface ClearedCell { void accept(int x, int y); }
    private static final class Entry {
        final int index;
        final Doodad original;
        final DoodadDef damaged, destroyed;
        final int[] cells;
        int hp = STARTING_HP;
        long lastAttack = Long.MIN_VALUE;
        Entry(int index, Doodad original, DoodadDef damaged, DoodadDef destroyed, int[] cells) {
            this.index = index; this.original = original; this.damaged = damaged;
            this.destroyed = destroyed; this.cells = cells;
        }
    }
    private final NavigationGrid grid;
    private final List<Doodad> props;
    private final Map<Integer, List<Entry>> owners = new HashMap<>();
    public TreeDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    public void register(int index, Doodad prop, TileRegistry registry) {
        TreeCanopy canopy = null;
        for (TreeCanopy candidate : TreeCanopy.values())
            if (candidate.id.equals(prop.definitionId())) { canopy = candidate; break; }
        if (canopy == null) return;
        List<Integer> cells = new ArrayList<>();
        for (int y = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++) {
            // Rendering mirrors the source horizontally before rotating it.
            boolean oddTurn = (prop.quarterTurns & 1) != 0;
            int maskX = prop.mirrored && !oddTurn ? prop.footprintCellsX - 1 - x : x;
            int maskY = prop.mirrored && oddTurn ? prop.footprintCellsY - 1 - y : y;
            if (!canopy.blocks(maskX, maskY, prop.quarterTurns)) continue;
            int gx = prop.cellX + x, gy = prop.cellY + y;
            // A visual-only catalog placement does not acquire forest collision.
            if (!grid.inBounds(gx, gy) || grid.isWalkable(gx, gy)
                    || !grid.hasTag(gx, gy, NavigationGrid.CellTag.NATURAL_SOLID)) return;
            cells.add(grid.index(gx, gy));
        }
        DoodadDef damaged = registry.doodad(canopy.id + "-damaged");
        DoodadDef destroyed = registry.doodad(canopy.id + "-destroyed");
        if (damaged == null || destroyed == null
                || damaged.footprintCellsX != canopy.width || damaged.footprintCellsY != canopy.height
                || destroyed.footprintCellsX != canopy.width || destroyed.footprintCellsY != canopy.height)
            throw new IllegalStateException("Missing or mismatched tree damage pair for " + canopy.id);
        Entry entry = new Entry(index, prop, damaged, destroyed, cells.stream().mapToInt(Integer::intValue).toArray());
        for (int cell : entry.cells) owners.computeIfAbsent(cell, unused -> new ArrayList<>()).add(entry);
    }

    /** Attack identity deduplicates a blast visiting several cells of the same tree. */
    public Result damage(int x, int y, int amount, long attack, ClearedCell cleared) {
        if (!grid.inBounds(x, y)) return Result.NOT_TREE;
        List<Entry> at = owners.get(grid.index(x, y));
        if (at == null) return Result.NOT_TREE;
        Result result = Result.UNCHANGED;
        for (Entry entry : at) {
            if (amount <= 0 || entry.hp <= 0 || entry.lastAttack == attack) continue;
            entry.lastAttack = attack;
            entry.hp = Math.max(0, entry.hp - amount);
            Doodad p = entry.original;
            props.set(entry.index, new Doodad(p.cellX, p.cellY,
                    entry.hp == 0 ? entry.destroyed : entry.damaged, p.quarterTurns, p.mirrored));
            if (entry.hp > 0) {
                if (result != Result.DESTROYED) result = Result.DAMAGED;
                continue;
            }
            result = Result.DESTROYED;
            for (int cell : entry.cells) {
                boolean occupied = false;
                for (Entry other : owners.get(cell)) if (other.hp > 0) { occupied = true; break; }
                if (!occupied) cleared.accept(cell % grid.getWidth(), cell / grid.getWidth());
            }
        }
        return result;
    }
}
