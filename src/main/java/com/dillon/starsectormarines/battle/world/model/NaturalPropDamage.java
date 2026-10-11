package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One structure pool per natural object; overlapping silhouettes retain obstruction. */
public final class NaturalPropDamage {
    public enum Result { NOT_NATURAL_PROP, UNCHANGED, DAMAGED, DESTROYED }
    @FunctionalInterface public interface ClearedCell { void accept(int x, int y); }
    private static final class Entry {
        final int index;
        final Doodad original;
        final DoodadDef damaged, destroyed;
        final int[] cells;
        int hp;
        long lastAttack = Long.MIN_VALUE;
        Entry(int index, Doodad original, DoodadDef damaged, DoodadDef destroyed, int[] cells, int hp) {
            this.index = index; this.original = original; this.damaged = damaged;
            this.destroyed = destroyed; this.cells = cells;
            this.hp = hp;
        }
    }
    private final NavigationGrid grid;
    private final List<Doodad> props;
    private final Map<Integer, List<Entry>> owners = new HashMap<>();
    public NaturalPropDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    public void register(int index, Doodad prop, TileRegistry registry) {
        NaturalPropShape shape = NaturalPropShape.find(prop.definitionId());
        if (shape == null) return;
        List<Integer> cells = new ArrayList<>();
        for (int y = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++) {
            // Rendering mirrors the source horizontally before rotating it.
            boolean oddTurn = (prop.quarterTurns & 1) != 0;
            int maskX = prop.mirrored && !oddTurn ? prop.footprintCellsX - 1 - x : x;
            int maskY = prop.mirrored && oddTurn ? prop.footprintCellsY - 1 - y : y;
            if (!shape.blocks(maskX, maskY, prop.quarterTurns)) continue;
            int gx = prop.cellX + x, gy = prop.cellY + y;
            // A visual-only catalog placement does not acquire natural obstruction.
            if (!grid.inBounds(gx, gy) || grid.isWalkable(gx, gy)
                    || !grid.hasTag(gx, gy, NavigationGrid.CellTag.NATURAL_SOLID)) return;
            cells.add(grid.index(gx, gy));
        }
        DoodadDef damaged = registry.doodad(shape.id() + "-damaged");
        DoodadDef destroyed = registry.doodad(shape.id() + "-destroyed");
        if (damaged == null || destroyed == null
                || damaged.footprintCellsX != shape.width() || damaged.footprintCellsY != shape.height()
                || destroyed.footprintCellsX != shape.width() || destroyed.footprintCellsY != shape.height())
            throw new IllegalStateException("Missing or mismatched natural prop damage pair for " + shape.id());
        Entry entry = new Entry(index, prop, damaged, destroyed, cells.stream().mapToInt(Integer::intValue).toArray(), shape.hitPoints());
        for (int cell : entry.cells) owners.computeIfAbsent(cell, unused -> new ArrayList<>()).add(entry);
    }

    /** Attack identity deduplicates a blast visiting several cells of the same object. */
    public Result damage(int x, int y, int amount, long attack, ClearedCell cleared) {
        if (!grid.inBounds(x, y)) return Result.NOT_NATURAL_PROP;
        List<Entry> at = owners.get(grid.index(x, y));
        if (at == null) return Result.NOT_NATURAL_PROP;
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
