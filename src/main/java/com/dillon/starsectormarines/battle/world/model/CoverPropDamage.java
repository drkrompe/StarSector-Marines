package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Destructible cell-blocking cover props; the ordinary grid wall-HP pool owns durability. */
public final class CoverPropDamage {
    public enum Result { NOT_COVER_PROP, UNCHANGED, DAMAGED, DESTROYED }
    public static final int STARTING_HP = 100;
    private static final String FENCE_PREFIX = "doodad.industrial-fence-";
    private static final String SANDBAG_PREFIX = "doodad.sandbag-";
    private static final Set<String> FENCE_SHAPES = Set.of("straight-h", "straight-v",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private static final Set<String> SANDBAG_SHAPES = Set.of("straight-n", "straight-e", "straight-s", "straight-w",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private record Entry(int index, Doodad original, DoodadDef damaged, DoodadDef destroyed) {}
    private final Map<Integer, Entry> entries = new HashMap<>();
    private final NavigationGrid grid;
    private final List<Doodad> props;

    public CoverPropDamage(NavigationGrid grid, List<Doodad> props) {
        this.grid = grid;
        this.props = props;
    }

    /** Decorative walkable placements do not acquire a new movement or cover contract. */
    public void register(int index, Doodad prop, TileRegistry registry) {
        String id = prop.definitionId();
        String prefix = familyPrefix(id);
        if (prefix == null
                || !grid.inBounds(prop.cellX, prop.cellY) || grid.isWalkable(prop.cellX, prop.cellY)) return;
        String shape = id.substring(prefix.length());
        DoodadDef damaged = registry == null ? null : registry.doodad(prefix + "damaged-" + shape);
        DoodadDef destroyed = registry == null ? null : registry.doodad(prefix + "destroyed-" + shape);
        if (damaged == null || destroyed == null)
            throw new IllegalStateException("Missing cover damage pair for " + id);
        int key = grid.index(prop.cellX, prop.cellY);
        if (entries.containsKey(key)) throw new IllegalStateException("Overlapping destructible cover props");
        entries.put(key, new Entry(index, prop, damaged, destroyed));
        if (grid.getWallHp(prop.cellX, prop.cellY) <= 0)
            grid.setWallHp(prop.cellX, prop.cellY, STARTING_HP);
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
        if (amount <= 0 || grid.isWalkable(x, y)) return Result.UNCHANGED;
        int before = grid.getWallHp(x, y);
        boolean broken = grid.damageCell(x, y, amount);
        if (!broken && grid.getWallHp(x, y) == before) return Result.UNCHANGED;
        Doodad original = entry.original();
        props.set(entry.index(), new Doodad(x, y, broken ? entry.destroyed() : entry.damaged(),
                original.quarterTurns, original.mirrored));
        return broken ? Result.DESTROYED : Result.DAMAGED;
    }
}
