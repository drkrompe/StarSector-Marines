package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Frozen eastern-to-western sectors; only observed structural damage advances the sweep. */
public final class StructuralDemolitionPlan {
    private record Sector(WarshipOrder.FireOnArea order, List<int[]> walls) { }
    private final List<Sector> sectors = new ArrayList<>();
    private int index;

    public StructuralDemolitionPlan(WarshipOrder.FireOnArea envelope, List<int[]> walls) {
        if (walls.isEmpty()) throw new IllegalArgumentException("No installation walls");
        int minX = walls.stream().mapToInt(c -> c[0]).min().orElseThrow();
        int minY = walls.stream().mapToInt(c -> c[1]).min().orElseThrow();
        int maxX = walls.stream().mapToInt(c -> c[0]).max().orElseThrow() + 1;
        int maxY = walls.stream().mapToInt(c -> c[1]).max().orElseThrow() + 1;
        float midX = (minX + maxX) / 2f, midY = (minY + maxY) / 2f;
        for (int col = 1; col >= 0; col--) for (int row = 0; row < 2; row++) {
            var bounds = new WarshipOrder.Bounds(col == 0 ? minX : midX, row == 0 ? minY : midY,
                    col == 0 ? midX : maxX, row == 0 ? midY : maxY);
            var cells = walls.stream().filter(c -> bounds.contains(c[0] + .5f, c[1] + .5f))
                    .map(int[]::clone).toList();
            if (!cells.isEmpty()) sectors.add(new Sector(new WarshipOrder.FireOnArea(envelope.x(),
                    envelope.y(), envelope.radius(), false, true, bounds), cells));
        }
    }

    public int sectorNumber() { return Math.min(index + 1, sectors.size()); }
    public int sectorCount() { return sectors.size(); }
    public boolean complete() { return index == sectors.size(); }
    public WarshipOrder.FireOnArea advance(BiPredicate<Integer, Integer> standing) {
        while (index < sectors.size()) {
            Sector sector = sectors.get(index);
            long remaining = sector.walls().stream().filter(c -> standing.test(c[0], c[1])).count();
            if (remaining > sector.walls().size() / 4) return sector.order();
            index++;
        }
        return null;
    }
}
