package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Set;

/** Destructible cover props. Blockers use grid wall HP; walkable crates own a removable physical profile. */
public final class CoverPropDamage {
    public enum Result { NOT_COVER_PROP, UNCHANGED, DAMAGED, DESTROYED }
    public static final int STARTING_HP = 100;
    private static final String FENCE_PREFIX = "doodad.industrial-fence-";
    private static final String SANDBAG_PREFIX = "doodad.sandbag-";
    private static final Set<String> FENCE_SHAPES = Set.of("straight-h", "straight-v",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private static final Set<String> SANDBAG_SHAPES = Set.of("straight-n", "straight-e", "straight-s", "straight-w",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    public static final Set<String> CRATES = Set.of("doodad.box", "doodad.box-dam", "doodad.crate", "doodad.industrial-crate-stack");
    private static final class Entry {
        final int index;
        final Doodad original;
        final DoodadDef damaged, destroyed;
        final boolean physical;
        final Entry next;
        int hp;
        Entry(int index, Doodad original, DoodadDef damaged, DoodadDef destroyed,
              boolean physical, Entry next) {
            this.index = index; this.original = original; this.damaged = damaged; this.destroyed = destroyed;
            this.physical = physical; this.next = next;
            hp = "doodad.box-dam".equals(original.definitionId()) ? STARTING_HP / 2 : STARTING_HP;
        }
    }
    // Queried per ballistic crossing and tactical facing; primitive keys keep those reads allocation-free.
    private final Int2ObjectOpenHashMap<Entry> entries = new Int2ObjectOpenHashMap<>();
    private final NavigationGrid grid;
    private final List<Doodad> props;

    public CoverPropDamage(NavigationGrid grid, List<Doodad> props) {
        this.grid = grid;
        this.props = props;
    }

    /** Returns true when the prop's physical cover belongs to this removable channel. */
    public boolean register(int index, Doodad prop, TileRegistry registry) {
        String id = prop.definitionId();
        String prefix = familyPrefix(id);
        boolean crate = id != null && CRATES.contains(id);
        if ((!crate && prefix == null) || !grid.inBounds(prop.cellX, prop.cellY)) return false;
        boolean physical = grid.isWalkable(prop.cellX, prop.cellY);
        if (physical && !crate) return false;
        String shape = crate ? "" : id.substring(prefix.length());
        DoodadDef damaged = registry == null ? null : registry.doodad(crate ? id + "-damaged" : prefix + "damaged-" + shape);
        DoodadDef destroyed = registry == null ? null : registry.doodad(crate ? id + "-destroyed" : prefix + "destroyed-" + shape);
        if (damaged == null || destroyed == null)
            throw new IllegalStateException("Missing cover damage pair for " + id);
        int key = grid.index(prop.cellX, prop.cellY);
        Entry previous = entries.get(key);
        if (previous != null && (!crate || !CRATES.contains(previous.original.definitionId())))
            throw new IllegalStateException("Overlapping destructible cover props");
        Entry entry = new Entry(index, prop, damaged, destroyed, physical, previous);
        entries.put(key, entry);
        if (!physical && grid.getWallHp(prop.cellX, prop.cellY) <= 0)
            grid.setWallHp(prop.cellX, prop.cellY, entry.hp);
        return physical;
    }

    /** Strongest surviving walkable crate whose silhouette contains this ray. */
    public int physicalCoverAt(int x, int y, float roundZ) {
        if (!grid.inBounds(x, y)) return 0;
        int level = 0;
        for (Entry entry = entries.get(grid.index(x, y)); entry != null; entry = entry.next)
            if (entry.physical && entry.hp > 0 && entry.original.ballisticHalfHeight > 0
                    && Math.abs(roundZ) <= entry.original.ballisticHalfHeight)
                level = Math.max(level, entry.original.cover);
        return level;
    }

    private static String familyPrefix(String id) {
        if (id == null) return null;
        if (id.startsWith(FENCE_PREFIX) && FENCE_SHAPES.contains(id.substring(FENCE_PREFIX.length()))) return FENCE_PREFIX;
        if (id.startsWith(SANDBAG_PREFIX) && SANDBAG_SHAPES.contains(id.substring(SANDBAG_PREFIX.length()))) return SANDBAG_PREFIX;
        return null;
    }

    public Result damage(int x, int y, int amount) {
        if (!grid.inBounds(x, y)) return Result.NOT_COVER_PROP;
        Entry entry = entries.get(grid.index(x, y));
        if (entry == null) return Result.NOT_COVER_PROP;
        if (amount <= 0) return Result.UNCHANGED;
        if (entry.physical) {
            boolean changed = false, alive = false;
            for (Entry current = entry; current != null; current = current.next) {
                if (current.hp <= 0) continue;
                current.hp = Math.max(0, current.hp - amount);
                replace(current, current.hp == 0);
                changed = true; alive |= current.hp > 0;
            }
            return !changed ? Result.UNCHANGED : alive ? Result.DAMAGED : Result.DESTROYED;
        }
        if (grid.isWalkable(x, y)) return Result.UNCHANGED;
        int before = grid.getWallHp(x, y);
        boolean broken = grid.damageCell(x, y, amount);
        if (!broken && grid.getWallHp(x, y) == before) return Result.UNCHANGED;
        for (Entry current = entry; current != null; current = current.next) replace(current, broken);
        return broken ? Result.DESTROYED : Result.DAMAGED;
    }

    private void replace(Entry entry, boolean broken) {
        Doodad original = entry.original;
        props.set(entry.index, new Doodad(original.cellX, original.cellY, broken ? entry.destroyed : entry.damaged,
                original.quarterTurns, original.mirrored));
    }
}
