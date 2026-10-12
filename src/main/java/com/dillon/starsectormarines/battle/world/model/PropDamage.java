package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

/** One damage pool per placed prop, shared by every cell in its rotated footprint. */
public final class PropDamage {
    public static final Set<String> BARRELS = Set.of("doodad.industrial-drum-cluster", "main-stores.drums");
    /** Presentation only: these cues do not cause secondary damage or smoke concealment. */
    public enum Effect { NONE, DUST, SMOKE, FIRE }
    public record Profile(Effect damaged, Effect destroyed) {}
    /** Adding a family requires matching full-footprint damaged/destroyed catalog entries. */
    public static final Map<String, Profile> EQUIPMENT = Map.ofEntries(
            Map.entry("doodad.industrial-generator", new Profile(Effect.SMOKE, Effect.FIRE)),
            Map.entry("doodad.military-radar-dish", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.industrial-machine-tool", new Profile(Effect.SMOKE, Effect.DUST)),
            Map.entry("doodad.industrial-fluid-tank", new Profile(Effect.SMOKE, Effect.SMOKE)),
            Map.entry("doodad.industrial-control-console", new Profile(Effect.SMOKE, Effect.FIRE)),
            Map.entry("doodad.military-command-console", new Profile(Effect.SMOKE, Effect.FIRE)),
            Map.entry("doodad.military-tactical-table", new Profile(Effect.SMOKE, Effect.DUST)),
            Map.entry("doodad.office-workstation-bank", new Profile(Effect.SMOKE, Effect.FIRE)),
            Map.entry("doodad.industrial-cable-reel", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.industrial-pallet-stack", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.industrial-pipe-bundle", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.military-bunk", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.desk-2", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.desk-1", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.shelf-empty", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.shelf-1", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.shelf-2", new Profile(Effect.NONE, Effect.DUST)),
            Map.entry("doodad.shelf-3", new Profile(Effect.NONE, Effect.DUST)));
    private static final Profile SILENT = new Profile(Effect.NONE, Effect.NONE);
    @FunctionalInterface public interface EffectSink { void accept(float x, float y, Effect effect, float footprintSize); }
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
        boolean hasDamage;
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
    private EffectSink effectSink = (x, y, effect, size) -> {};
    public PropDamage(NavigationGrid grid, List<Doodad> props) { this.grid = grid; this.props = props; }

    public void setEffectSink(EffectSink sink) { effectSink = sink; }

    public boolean register(int index, Doodad prop, TileRegistry registry) {
        if (prop.definitionId() == null || (!BARRELS.contains(prop.definitionId())
                && !EQUIPMENT.containsKey(prop.definitionId()))) return false;
        DoodadDef damaged = registry == null ? null : registry.doodad(prop.definitionId() + "-damaged");
        DoodadDef destroyed = registry == null ? null : registry.doodad(prop.definitionId() + "-destroyed");
        if (damaged == null || destroyed == null
                || damaged.footprintCellsX != prop.sourceCellsX || damaged.footprintCellsY != prop.sourceCellsY
                || destroyed.footprintCellsX != prop.sourceCellsX || destroyed.footprintCellsY != prop.sourceCellsY)
            throw new IllegalStateException("Missing or mismatched prop damage pair for " + prop.definitionId());
        if (destroyed.cover.level() != 0 || destroyed.ballisticHalfHeight != 0f)
            throw new IllegalStateException("Prop debris must release cover: " + prop.definitionId());
        Entry entry = new Entry(index, prop, damaged, destroyed, prop.footprintCellsX * prop.footprintCellsY);
        for (int y = 0, i = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++, i++) {
            int gx = prop.cellX + x, gy = prop.cellY + y;
            if (!grid.inBounds(gx, gy)) throw new IllegalArgumentException("Prop footprint outside map");
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
            if (!entry.hasDamage || entry.hp == 0) {
                Profile profile = EQUIPMENT.getOrDefault(p.definitionId(), SILENT);
                Effect effect = entry.hp == 0 ? profile.destroyed() : profile.damaged();
                if (effect != Effect.NONE)
                    effectSink.accept(p.cellX + p.footprintCellsX * .5f,
                            p.cellY + p.footprintCellsY * .5f, effect,
                            Math.min(p.footprintCellsX, p.footprintCellsY));
                entry.hasDamage = true;
            }
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
                // A layered, unregistered rack or fixture keeps its own obstruction.
                if (!occupied) for (Doodad other : props)
                    if (other.cover > 0 && other.occupiesCell(cell % grid.getWidth(), cell / grid.getWidth())) {
                        occupied = true; break;
                    }
                if (!occupied) cleared.accept(cell % grid.getWidth(), cell / grid.getWidth());
            }
        }
        return result;
    }
}
