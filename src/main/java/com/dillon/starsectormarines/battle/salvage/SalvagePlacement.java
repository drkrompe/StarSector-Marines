package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Places a few optional detours on already-generated, reachable ground. Never carves terrain. */
public final class SalvagePlacement {
    public record Cell(int x, int y) { }
    private SalvagePlacement() { }

    public static List<SalvageCache> forBattle(BattleSimulation sim, long seed) {
        var grid = sim.getGrid();
        var topology = sim.getTopology();
        boolean[] occupied = new boolean[grid.getWidth() * grid.getHeight()];
        for (var prop : sim.getDoodads()) for (int y = prop.cellY; y < prop.cellY + prop.footprintCellsY; y++)
            for (int x = prop.cellX; x < prop.cellX + prop.footprintCellsX; x++)
                if (grid.inBounds(x, y)) occupied[grid.index(x, y)] = true;
        var candidates = new ArrayList<Cell>();
        // Coarse scan keeps setup bounded and leaves clearance around the small pickup bundle.
        for (int y = 6; y < grid.getHeight() - 6; y += 3) for (int x = 6; x < grid.getWidth() - 6; x += 3) {
            if (topology.isStreet(x, y) || topology.hasTag(x, y, CellTopology.Tag.VEHICLE)) continue;
            boolean clear = true;
            for (int dy = -1; dy <= 1 && clear; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (!grid.isWalkable(x + dx, y + dy) || occupied[grid.index(x + dx, y + dy)]
                        || grid.hasTag(x + dx, y + dy, NavigationGrid.CellTag.DOORWAY)) { clear = false; break; }
            }
            if (clear) candidates.add(new Cell(x, y));
        }
        var targets = sim.getTacticalMap() == null ? List.<Cell>of() : sim.getTacticalMap().all().stream()
                .map(n -> new Cell(n.anchorX, n.anchorY)).toList();
        return place(grid, sim.getMarineLandingPads(), candidates, targets, seed);
    }

    /** Pure geometry seam: supplied candidates are clear sites, targets are mandatory/tactical centers. */
    public static List<SalvageCache> place(NavigationGrid grid, List<LandingPad> pads,
                                          List<Cell> candidates, List<Cell> targets, long seed) {
        if (pads.isEmpty()) return List.of();
        var shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(seed ^ 0x53414c56414745L));
        var selected = new ArrayList<SalvageCache>();
        for (var c : shuffled) {
            if (!grid.isWalkable(c.x, c.y)) continue;
            if (pads.stream().anyMatch(p -> distance(c.x, c.y, p.centerX, p.centerY) < 28 * 28)) continue;
            if (targets.stream().anyMatch(t -> distance(c.x, c.y, t.x, t.y) < 16 * 16)) continue;
            if (selected.stream().anyMatch(s -> distance(c.x, c.y, s.cellX, s.cellY) < 26 * 26)) continue;
            if (pads.stream().noneMatch(p -> grid.arePathConnected(p.centerX, p.centerY, c.x, c.y, false))) continue;
            var resource = SalvageResource.values()[selected.size()];
            selected.add(new SalvageCache("field-cache-" + selected.size(), c.x, c.y, resource, resource.cacheQuantity));
            if (selected.size() == SalvageResource.values().length) break;
        }
        return List.copyOf(selected);
    }

    private static int distance(int x, int y, int tx, int ty) {
        int dx = x - tx, dy = y - ty;
        return dx * dx + dy * dy;
    }
}
