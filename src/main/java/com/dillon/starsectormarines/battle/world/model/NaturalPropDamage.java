package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.creep.CreepVegetation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * One structure pool per natural object; overlapping silhouettes retain obstruction.
 *
 * <p>An object is <em>undergrowth</em> when a heavy-lift hull may flatten it ({@code HeavyTracks}): a young tree from
 * the start ({@link NaturalPropShape#undergrowth}), any tree once the creep has killed it ({@link #wither}). A cell
 * carries {@link NavigationGrid.CellTag#CRUSHABLE} exactly while every standing object owning it is undergrowth, so a
 * dead crown overlapping a living mature one still stops the hull, and never on permanent ground (a summit).
 */
public final class NaturalPropDamage {
    public enum Result { NOT_NATURAL_PROP, UNCHANGED, DAMAGED, DESTROYED }
    @FunctionalInterface public interface ClearedCell { void accept(int x, int y); }
    private static final class Entry {
        final int index;
        final Doodad original;
        final DoodadDef damaged, destroyed;
        final int[] cells;
        boolean undergrowth;
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
    private final List<Entry> entries = new ArrayList<>();
    /** Cells whose obstruction is the ground itself, never crushable: a summit a tree stands on. */
    private IntPredicate permanent = cell -> false;
    public NaturalPropDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    /** Names the cells (map indices) whose obstruction outlives any prop on them. */
    public void setPermanent(IntPredicate permanent) { this.permanent = permanent; }

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
        // The placed id, not the shape's: a dry-country twin has its own damage art.
        DoodadDef damaged = registry.doodad(prop.definitionId() + "-damaged");
        DoodadDef destroyed = registry.doodad(prop.definitionId() + "-destroyed");
        if (damaged == null || destroyed == null
                || damaged.footprintCellsX != shape.width() || damaged.footprintCellsY != shape.height()
                || destroyed.footprintCellsX != shape.width() || destroyed.footprintCellsY != shape.height())
            throw new IllegalStateException("Missing or mismatched natural prop damage pair for " + prop.definitionId());
        Entry entry = new Entry(index, prop, damaged, destroyed, cells.stream().mapToInt(Integer::intValue).toArray(), shape.hitPoints());
        entry.undergrowth = shape.undergrowth();
        entries.add(entry);
        for (int cell : entry.cells) owners.computeIfAbsent(cell, unused -> new ArrayList<>()).add(entry);
        refreshCrushable(entry);
    }

    /**
     * The standing tree placed at this origin has died: it is undergrowth from now on. False when no standing object
     * was placed there.
     */
    public boolean wither(int originX, int originY) {
        boolean found = false;
        for (Entry entry : entries) {
            if (entry.hp <= 0 || entry.original.cellX != originX || entry.original.cellY != originY) continue;
            found = true;
            if (entry.undergrowth) continue;
            entry.undergrowth = true;
            refreshCrushable(entry);
        }
        return found;
    }

    private void refreshCrushable(Entry entry) {
        for (int cell : entry.cells) {
            int x = cell % grid.getWidth(), y = cell / grid.getWidth();
            grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, crushable(cell));
        }
    }

    /** Some object stands on the cell and every one that does is undergrowth. */
    private boolean crushable(int cell) {
        if (permanent.test(cell)) return false;
        boolean standing = false;
        for (Entry owner : owners.get(cell)) {
            if (owner.hp <= 0) continue;
            if (!owner.undergrowth) return false;
            standing = true;
        }
        return standing;
    }

    /**
     * Whether the cell is the outer crown of what stands on it: some natural object stands here, and the cell is the
     * trunk of none of them and not permanent ground. The trunk is the rule {@code CreepVegetation} keeps blocking a
     * dead tree by: the crown cells within {@link CreepVegetation#TRUNK_RADIUS} of the footprint's centre, and at
     * least the nearest one. A hull may brush an outer crown ({@code SoftBrush}); never a trunk.
     */
    public boolean outerCrownAt(int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        int cell = grid.index(x, y);
        List<Entry> at = owners.get(cell);
        if (at == null || permanent.test(cell)) return false;
        boolean standing = false;
        for (Entry entry : at) {
            if (entry.hp <= 0) continue;
            standing = true;
            if (trunk(entry, cell)) return false;
        }
        return standing;
    }

    private boolean trunk(Entry entry, int cell) {
        Doodad d = entry.original;
        float cx = d.cellX + d.footprintCellsX * 0.5f, cy = d.cellY + d.footprintCellsY * 0.5f;
        float nearest = Float.MAX_VALUE, here = Float.MAX_VALUE;
        for (int owned : entry.cells) {
            float dx = owned % grid.getWidth() + 0.5f - cx, dy = owned / grid.getWidth() + 0.5f - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            nearest = Math.min(nearest, dist);
            if (owned == cell) here = dist;
        }
        return here <= Math.max(CreepVegetation.TRUNK_RADIUS, nearest);
    }

    /** The definition id of a standing natural object owning this cell, or null. */
    public String standingIdAt(int x, int y) {
        if (!grid.inBounds(x, y)) return null;
        List<Entry> at = owners.get(grid.index(x, y));
        if (at == null) return null;
        for (Entry entry : at) if (entry.hp > 0) return entry.original.definitionId();
        return null;
    }

    /**
     * The footprint of the standing natural object owning this cell, {cellX, cellY, width, height} in cells, into
     * {@code out}; false, and {@code out} untouched, when none stands here.
     */
    public boolean standingFootprintAt(int x, int y, int[] out) {
        if (!grid.inBounds(x, y)) return false;
        List<Entry> at = owners.get(grid.index(x, y));
        if (at == null) return false;
        for (Entry entry : at) {
            if (entry.hp <= 0) continue;
            Doodad d = entry.original;
            out[0] = d.cellX; out[1] = d.cellY; out[2] = d.footprintCellsX; out[3] = d.footprintCellsY;
            return true;
        }
        return false;
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
            refreshCrushable(entry);
            for (int cell : entry.cells) {
                boolean occupied = false;
                for (Entry other : owners.get(cell)) if (other.hp > 0) { occupied = true; break; }
                if (!occupied) cleared.accept(cell % grid.getWidth(), cell / grid.getWidth());
            }
        }
        return result;
    }
}
