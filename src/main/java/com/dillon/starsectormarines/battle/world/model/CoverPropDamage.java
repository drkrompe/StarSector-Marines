package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.FortWorks;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/** Destructible cover props. Blockers use grid wall HP; walkable props own a removable physical profile. */
public final class CoverPropDamage {
    public enum Result { NOT_COVER_PROP, UNCHANGED, DAMAGED, DESTROYED }
    public static final int STARTING_HP = 100;
    private static final String FENCE_PREFIX = "doodad.industrial-fence-";
    private static final String SANDBAG_PREFIX = "doodad.sandbag-";
    private static final String FORT_SANDBAG_PREFIX = "doodad.fort-sandbag-";
    private static final String EMPLACEMENT_PREFIX = TileManifest.EMPLACEMENT_PIECE_PREFIX;
    private static final Set<String> FENCE_SHAPES = Set.of("straight-h", "straight-v",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private static final Set<String> SANDBAG_SHAPES = Set.of("straight-n", "straight-e", "straight-s", "straight-w",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private static final Set<String> FORT_SANDBAG_SHAPES = Set.of("n", "e", "s", "w",
            "corner-nw", "corner-ne", "corner-sw", "corner-se");
    private static final Set<String> EMPLACEMENT_SHAPES = Set.of("n", "e", "s", "w",
            "inner-nw", "inner-ne", "inner-sw", "inner-se");
    /** Single-cell props named {@code <id>-damaged} / {@code <id>-destroyed}; co-located ones keep separate pools. */
    public static final Set<String> CRATES = Set.of("doodad.box", "doodad.box-dam", "doodad.crate", "doodad.industrial-crate-stack");
    /** Militia hedgehogs: crate-style naming, but one per cell. */
    private static final Set<String> HEDGEHOGS = Set.of("doodad.fort-hedgehog");
    private static final class Entry {
        int index;
        final Doodad original;
        final DoodadDef damaged, destroyed;
        final boolean physical;
        Entry next;
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
        boolean crate = id != null && CRATES.contains(id);
        String[] states = damageStateIds(id);
        if (states == null || !grid.inBounds(prop.cellX, prop.cellY)) return false;
        boolean physical = grid.isWalkable(prop.cellX, prop.cellY);
        DoodadDef damaged = registry == null ? null : registry.doodad(states[0]);
        DoodadDef destroyed = registry == null ? null : registry.doodad(states[1]);
        if (damaged == null || destroyed == null)
            throw new IllegalStateException("Missing cover damage pair for " + id);
        int key = grid.index(prop.cellX, prop.cellY);
        Entry previous = entries.get(key);
        if (previous != null && (!crate || !CRATES.contains(previous.original.definitionId())))
            throw new IllegalStateException("Overlapping destructible cover props at (" + prop.cellX + ","
                    + prop.cellY + "): " + id + " laid over " + previous.original.definitionId());
        Entry entry = new Entry(index, prop, damaged, destroyed, physical, previous);
        entries.put(key, entry);
        if (!physical && grid.getWallHp(prop.cellX, prop.cellY) <= 0)
            grid.setWallHp(prop.cellX, prop.cellY, entry.hp);
        return physical;
    }

    /**
     * Whether a prop of this id is loose: a crate stack or a field sandbag, which a build's pad may clear away. Fort
     * sandbags, emplacement pieces, fences and hedgehogs are works, and stay.
     */
    public static boolean loose(String id) {
        return id != null && (CRATES.contains(id) || SANDBAG_PREFIX.equals(familyPrefix(id)));
    }

    /** Sets the list index of every prop whose cover this channel carries on walkable ground. */
    void markPhysical(BitSet out) {
        for (Entry head : entries.values())
            for (Entry entry = head; entry != null; entry = entry.next) if (entry.physical) out.set(entry.index);
    }

    /** Forgets the prop at {@code index} as it leaves the list; every later prop's index moves down one. */
    void removed(int index) {
        for (int key : entries.keySet().toIntArray()) {
            List<Entry> chain = new ArrayList<>();
            for (Entry entry = entries.get(key); entry != null; entry = entry.next) if (entry.index != index) chain.add(entry);
            Entry kept = null;
            for (int i = chain.size() - 1; i >= 0; i--) {
                Entry entry = chain.get(i);
                entry.next = kept;
                if (entry.index > index) entry.index--;
                kept = entry;
            }
            if (kept == null) entries.remove(key);
            else entries.put(key, kept);
        }
    }

    /** Strongest surviving walkable cover prop whose silhouette contains this ray. */
    public int physicalCoverAt(int x, int y, float roundZ) {
        if (!grid.inBounds(x, y)) return 0;
        int level = 0;
        for (Entry entry = entries.get(grid.index(x, y)); entry != null; entry = entry.next)
            if (entry.physical && entry.hp > 0 && entry.original.ballisticHalfHeight > 0
                    && Math.abs(roundZ) <= entry.original.ballisticHalfHeight)
                level = Math.max(level, entry.original.cover);
        return level;
    }

    /**
     * The definition id of a surviving destructible cover prop on this cell, or null: what a round that
     * stopped here would wear. A spent prop names nothing.
     */
    public String standingIdAt(int x, int y) {
        if (!grid.inBounds(x, y)) return null;
        Entry entry = entries.get(grid.index(x, y));
        if (entry == null) return null;
        if (!entry.physical) {
            return !grid.isWalkable(x, y) && grid.getWallHp(x, y) > 0 ? entry.original.definitionId() : null;
        }
        for (Entry current = entry; current != null; current = current.next)
            if (current.hp > 0) return current.original.definitionId();
        return null;
    }

    /**
     * The damaged and destroyed doodad ids a destructible cover prop swaps to, or null for a prop
     * this channel does not own: {@code <id>-damaged} for crates and hedgehogs, {@code
     * <family>damaged-<shape>} for fence, sandbag and emplacement runs.
     */
    public static String[] damageStateIds(String id) {
        if (id == null) return null;
        if (CRATES.contains(id) || HEDGEHOGS.contains(id)) return new String[]{id + "-damaged", id + "-destroyed"};
        String prefix = familyPrefix(id);
        if (prefix == null) return null;
        String shape = id.substring(prefix.length());
        return new String[]{prefix + "damaged-" + shape, prefix + "destroyed-" + shape};
    }

    private static String familyPrefix(String id) {
        if (id == null) return null;
        if (id.startsWith(FENCE_PREFIX) && FENCE_SHAPES.contains(id.substring(FENCE_PREFIX.length()))) return FENCE_PREFIX;
        if (id.startsWith(SANDBAG_PREFIX) && SANDBAG_SHAPES.contains(id.substring(SANDBAG_PREFIX.length()))) return SANDBAG_PREFIX;
        if (id.startsWith(FORT_SANDBAG_PREFIX) && FORT_SANDBAG_SHAPES.contains(id.substring(FORT_SANDBAG_PREFIX.length()))) return FORT_SANDBAG_PREFIX;
        if (id.startsWith(EMPLACEMENT_PREFIX) && EMPLACEMENT_SHAPES.contains(id.substring(EMPLACEMENT_PREFIX.length()))) return EMPLACEMENT_PREFIX;
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
        DoodadDef shown = broken ? entry.destroyed : entry.damaged;
        // Sustained small-arms fire lands many hits on a prop that already shows its damage.
        if (props.get(entry.index).definitionId() != null
                && props.get(entry.index).definitionId().equals(shown.id)) return;
        TileRegistry registry = TileRegistry.installed();
        Doodad state = new Doodad(original.cellX, original.cellY, shown, original.quarterTurns, original.mirrored);
        // A damage state keeps the style the piece was dressed in, where that style draws one.
        props.set(entry.index, FortWorks.dress(state, shown.id, original.look(), registry));
    }
}
