package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Nature rocks retain ground and cover identity when their overlay becomes a damaged prop. */
public final class RockDamage {
    public enum Result { NOT_ROCK, UNCHANGED, DAMAGED, DESTROYED }
    /** The seven stone groups every set draws, smallest first. */
    public static final List<String> GROUPS = List.of("small-1", "small-2", "medium-1", "medium-2",
            "large-1", "large-2", "large-3");
    /** Every nature rock: each group in each biome's stone set ({@link #stone}). */
    public static final Set<String> IDS = Stream.of(Biome.values())
            .flatMap(b -> GROUPS.stream().map(b::stone)).collect(Collectors.toUnmodifiableSet());

    /**
     * The field-stone sets, one per kind of wild ground: mossed stone on a living world (the unprefixed ids), sun-
     * bleached sandstone on arid ones, dark basalt on bare rock and frost-capped stone on frozen worlds.
     */
    public enum Biome {
        MOSSED(""), SANDSTONE("sandstone-"), BASALT("basalt-"), FROST("frost-");

        private final String prefix;

        Biome(String prefix) { this.prefix = prefix; }

        /** This set's id for {@code group}, one of {@link #GROUPS}. */
        public String stone(String group) { return "nature.rock-" + prefix + group; }
    }
    private static final class Entry {
        final int index;
        final String id;
        final DoodadDef damaged, destroyed;
        int hp;
        Entry(int index, String id, DoodadDef damaged, DoodadDef destroyed, int hp) {
            this.index = index; this.id = id; this.damaged = damaged; this.destroyed = destroyed; this.hp = hp;
        }
    }
    private final NavigationGrid grid;
    private final List<Doodad> props;
    private final Map<Integer, Entry> entries = new HashMap<>();
    public RockDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    /** The id of a standing rock on this cell, or null: what a round that stopped here would wear. */
    public String standingIdAt(int x, int y, CellTopology topology, TileRegistry registry) {
        if (!grid.inBounds(x, y)) return null;
        Entry entry = entries.get(grid.index(x, y));
        if (entry != null) return entry.hp > 0 ? entry.id : null;
        int overlay = topology.getNatureOverlayIndex(x, y);
        if (registry == null || overlay < 0) return null;
        TileDef original = registry.byIndex(overlay);
        if (!IDS.contains(original.id) || topology.isWall(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return null;
        return original.id;
    }

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
            entry = new Entry(props.size(), original.id, damaged, destroyed, hp);
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
