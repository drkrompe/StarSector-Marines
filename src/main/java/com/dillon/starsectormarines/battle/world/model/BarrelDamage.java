package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One damage pool per drum group; stock overlays never own their underlying rack's obstruction. */
public final class BarrelDamage {
    public static final Set<String> IDS = Set.of("doodad.industrial-drum-cluster", "main-stores.drums");
    public static final int STARTING_HP = 100;
    @FunctionalInterface public interface ClearedCell { void accept(int x, int y); }
    private static final class Entry {
        final int index;
        final Doodad original;
        final DoodadDef damaged, destroyed;
        final int[] cells;
        final boolean[] physical, blocking;
        int hp = STARTING_HP;
        long lastAttack = Long.MIN_VALUE;
        Entry(int index, Doodad original, DoodadDef damaged, DoodadDef destroyed, int count) {
            this.index = index; this.original = original; this.damaged = damaged; this.destroyed = destroyed;
            cells = new int[count]; physical = new boolean[count]; blocking = new boolean[count];
        }
        boolean blocks(int cell) {
            for (int i = 0; i < cells.length; i++) if (cells[i] == cell) return blocking[i];
            return false;
        }
    }
    private final NavigationGrid grid;
    private final List<Doodad> props;
    private final Int2ObjectOpenHashMap<List<Entry>> owners = new Int2ObjectOpenHashMap<>();
    public BarrelDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    public boolean register(int index, Doodad prop, TileRegistry registry) {
        if (prop.definitionId() == null || !IDS.contains(prop.definitionId())) return false;
        DoodadDef damaged = registry == null ? null : registry.doodad(prop.definitionId() + "-damaged");
        DoodadDef destroyed = registry == null ? null : registry.doodad(prop.definitionId() + "-destroyed");
        if (damaged == null || destroyed == null
                || damaged.footprintCellsX != prop.sourceCellsX || damaged.footprintCellsY != prop.sourceCellsY
                || destroyed.footprintCellsX != prop.sourceCellsX || destroyed.footprintCellsY != prop.sourceCellsY)
            throw new IllegalStateException("Missing or mismatched barrel damage pair for " + prop.definitionId());
        Entry entry = new Entry(index, prop, damaged, destroyed, prop.footprintCellsX * prop.footprintCellsY);
        for (int y = 0, i = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++, i++) {
            int gx = prop.cellX + x, gy = prop.cellY + y;
            if (!grid.inBounds(gx, gy)) throw new IllegalArgumentException("Barrel footprint outside map");
            entry.cells[i] = grid.index(gx, gy);
            entry.physical[i] = grid.isWalkable(gx, gy);
            entry.blocking[i] = !entry.physical[i] && prop.cover > 0
                    && !grid.hasTag(gx, gy, NavigationGrid.CellTag.NATURAL_SOLID);
        }
        if (entry.cells.length == 1 && entry.blocking[0] && grid.getWallHp(prop.cellX, prop.cellY) > 0)
            entry.hp = grid.getWallHp(prop.cellX, prop.cellY);
        for (int cell : entry.cells) owners.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(entry);
        return true;
    }

    /** Own-cell physical cover only; tactical neighbor contributions are combined by DoodadService. */
    public int physicalCoverAt(int x, int y, float roundZ) {
        if (!grid.inBounds(x, y)) return 0;
        int cell = grid.index(x, y), level = 0;
        List<Entry> at = owners.get(cell);
        if (at == null) return 0;
        for (int n = 0; n < at.size(); n++) {
            Entry entry = at.get(n);
            if (entry.hp <= 0 || entry.original.ballisticHalfHeight <= 0
                    || !(Math.abs(roundZ) <= entry.original.ballisticHalfHeight)) continue;
            for (int i = 0; i < entry.cells.length; i++)
                if (entry.cells[i] == cell && entry.physical[i]) level = Math.max(level, entry.original.cover);
        }
        return level;
    }

    public CoverPropDamage.Result damage(int x, int y, int amount, long attack, ClearedCell cleared) {
        if (!grid.inBounds(x, y)) return CoverPropDamage.Result.NOT_COVER_PROP;
        List<Entry> at = owners.get(grid.index(x, y));
        if (at == null) return CoverPropDamage.Result.NOT_COVER_PROP;
        CoverPropDamage.Result result = CoverPropDamage.Result.UNCHANGED;
        for (Entry entry : at) {
            if (amount <= 0 || entry.hp <= 0 || entry.lastAttack == attack) continue;
            entry.lastAttack = attack; entry.hp = Math.max(0, entry.hp - amount);
            Doodad p = entry.original;
            props.set(entry.index, new Doodad(p.cellX, p.cellY, entry.hp == 0 ? entry.destroyed : entry.damaged,
                    p.quarterTurns, p.mirrored));
            if (entry.hp > 0) {
                if (result != CoverPropDamage.Result.DESTROYED) result = CoverPropDamage.Result.DAMAGED;
                continue;
            }
            result = CoverPropDamage.Result.DESTROYED;
            for (int i = 0; i < entry.cells.length; i++) {
                if (!entry.blocking[i]) continue;
                int cell = entry.cells[i];
                boolean occupied = false;
                for (Entry other : owners.get(cell)) if (other.hp > 0 && other.blocks(cell)) { occupied = true; break; }
                if (!occupied) cleared.accept(cell % grid.getWidth(), cell / grid.getWidth());
            }
        }
        return result;
    }
}
