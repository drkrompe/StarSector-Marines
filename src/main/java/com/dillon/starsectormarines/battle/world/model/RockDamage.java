package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Nature rocks retain ground and cover identity when their overlay becomes a damaged prop. */
public final class RockDamage {
    public enum Result { NOT_ROCK, UNCHANGED, DAMAGED, DESTROYED }
    public static final Set<String> IDS = Set.of("nature.rock-small-1", "nature.rock-small-2",
            "nature.rock-medium-1", "nature.rock-medium-2", "nature.rock-large-1",
            "nature.rock-large-2", "nature.rock-large-3");
    private static final class Entry {
        final int index;
        final DoodadDef damaged, destroyed;
        int hp;
        Entry(int index, DoodadDef damaged, DoodadDef destroyed, int hp) {
            this.index = index; this.damaged = damaged; this.destroyed = destroyed; this.hp = hp;
        }
    }
    private final NavigationGrid grid;
    private final List<Doodad> props;
    private final Map<Integer, Entry> entries = new HashMap<>();
    public RockDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    public Result damage(int x, int y, int amount, CellTopology topology, TileRegistry registry) {
        if (!grid.inBounds(x, y)) return Result.NOT_ROCK;
        int key = grid.index(x, y);
        Entry entry = entries.get(key);
        if (entry == null) {
            int overlay = topology.getNatureOverlayIndex(x, y);
            if (registry == null || overlay < 0) return Result.NOT_ROCK;
            TileDef original = registry.byIndex(overlay);
            if (!IDS.contains(original.id) || topology.isWall(x, y)
                    || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return Result.NOT_ROCK;
            if (amount <= 0) return Result.UNCHANGED;
            DoodadDef damaged = registry.doodad(original.id + "-damaged");
            DoodadDef destroyed = registry.doodad(original.id + "-destroyed");
            if (damaged == null || destroyed == null)
                throw new IllegalStateException("Missing rock damage pair for " + original.id);
            int hp = original.id.contains("-large-") ? 200 : original.id.contains("-medium-") ? 100 : 50;
            if (!grid.isWalkable(x, y) && grid.getWallHp(x, y) > 0) hp = grid.getWallHp(x, y);
            entry = new Entry(props.size(), damaged, destroyed, hp);
            entries.put(key, entry);
            // Cover stays in the nature channel; do not publish a second crossing profile.
            props.add(new Doodad(x, y, damaged));
            topology.setNatureOverlayIndex(x, y, -1);
        }
        if (amount <= 0 || entry.hp == 0) return Result.UNCHANGED;
        entry.hp = Math.max(0, entry.hp - amount);
        props.set(entry.index, new Doodad(x, y, entry.hp == 0 ? entry.destroyed : entry.damaged));
        return entry.hp == 0 ? Result.DESTROYED : Result.DAMAGED;
    }
}
