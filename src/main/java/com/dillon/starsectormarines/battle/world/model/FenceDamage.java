package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Destructible cell-blocking fence props; the ordinary grid wall-HP pool owns durability. */
public final class FenceDamage {
    public enum Result { NOT_FENCE, UNCHANGED, DAMAGED, DESTROYED }
    public static final int STARTING_HP = 100;
    private static final String PREFIX = "doodad.industrial-fence-";
    private static final Set<String> SHAPES = Set.of("straight-h", "straight-v",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private record Entry(int index, Doodad original, DoodadDef damaged, DoodadDef destroyed) {}
    private final Map<Integer, Entry> entries = new HashMap<>();
    private final NavigationGrid grid;
    private final List<Doodad> props;

    public FenceDamage(NavigationGrid grid, List<Doodad> props) {
        this.grid = grid;
        this.props = props;
    }

    /** Decorative walkable placements do not acquire a new movement or cover contract. */
    public void register(int index, Doodad prop, TileRegistry registry) {
        String id = prop.definitionId();
        if (id == null || !id.startsWith(PREFIX) || !SHAPES.contains(id.substring(PREFIX.length()))
                || !grid.inBounds(prop.cellX, prop.cellY) || grid.isWalkable(prop.cellX, prop.cellY)) return;
        String shape = id.substring(PREFIX.length());
        DoodadDef damaged = registry == null ? null : registry.doodad(PREFIX + "damaged-" + shape);
        DoodadDef destroyed = registry == null ? null : registry.doodad(PREFIX + "destroyed-" + shape);
        if (damaged == null || destroyed == null)
            throw new IllegalStateException("Missing fence damage pair for " + id);
        int key = grid.index(prop.cellX, prop.cellY);
        if (entries.containsKey(key)) throw new IllegalStateException("Overlapping destructible fences");
        entries.put(key, new Entry(index, prop, damaged, destroyed));
        if (grid.getWallHp(prop.cellX, prop.cellY) <= 0)
            grid.setWallHp(prop.cellX, prop.cellY, STARTING_HP);
    }

    public Result damage(int x, int y, int amount) {
        if (!grid.inBounds(x, y)) return Result.NOT_FENCE;
        Entry entry = entries.get(grid.index(x, y));
        if (entry == null) return Result.NOT_FENCE;
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
