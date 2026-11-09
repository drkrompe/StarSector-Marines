package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Places a few optional detours on already-generated, reachable ground. Never carves terrain. */
public final class SalvagePlacement {
    public record Cell(int x, int y) { }
    /** Mission-authored recovery area; its chosen cell still needs clear, reachable ground. */
    public record Site(SalvageResource resource, Cell preferred, int left, int bottom, int right, int top) {
        public Site {
            if (resource == null || preferred == null || left > right || bottom > top
                    || preferred.x < left || preferred.x > right || preferred.y < bottom || preferred.y > top)
                throw new IllegalArgumentException("Invalid authored salvage site");
        }
        boolean contains(Cell c) { return c.x >= left && c.x <= right && c.y >= bottom && c.y <= top; }
    }
    /**
     * A Ruins delve's finds ({@code ruins-expedition.md}): the grade's cache count instead of one per resource, most
     * of them on the far half of the vault route, so the deeper ground holds more of the field finds.
     *
     * @param caches how many finds the grade offers
     * @param route  the vault route as flat x,y pairs, from the vault to the landing
     */
    public record Delve(int caches, int[] route) {
        public Delve {
            if (caches < 0 || route == null || route.length < 2 || (route.length & 1) != 0)
                throw new IllegalArgumentException("A delve needs a cache count and a route from the vault");
            route = Arrays.copyOf(route, route.length);
        }

        /** The finds the far half must hold first: two in three, rounded up. */
        int farQuota() { return (caches * 2 + 2) / 3; }

        /** Whether a cell lies nearer the vault than the landing along the route, by its nearest route point. */
        boolean farHalf(Cell c) {
            int points = route.length / 2, nearest = 0, best = Integer.MAX_VALUE;
            for (int i = 0; i < points; i++) {
                int d = distance(c.x, c.y, route[i * 2], route[i * 2 + 1]);
                if (d < best) { best = d; nearest = i; }
            }
            // The route runs from the vault, so its first half is the far half from the landing.
            return nearest * 2 < points;
        }
    }

    /** Delve finds stand closer together than the one-per-resource scatter, so a small site still holds its grade's count. */
    static final int DELVE_SPACING = 20;

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
        // A held place nobody is posted to is a building, not a tactical centre to keep salvage clear of.
        var targets = sim.getTacticalMap() == null ? List.<Cell>of() : sim.getTacticalMap().all().stream()
                .filter(n -> !HeldPlaces.isUnposted(n))
                .map(n -> new Cell(n.anchorX, n.anchorY)).toList();
        if (sim.fieldCacheDelve() != null)
            return placeDelve(grid, sim.getMarineLandingPads(), candidates, targets, seed, sim.fieldCacheDelve());
        return place(grid, sim.getMarineLandingPads(), candidates, targets, seed, sim.fieldSalvageSites());
    }

    /**
     * Pure geometry seam for a delve: the far half's quota first, then the rest from anywhere legal, so a far half
     * with too few sites lends its share to the near half. Resources take turns; fewer finds when sites are scarce.
     */
    public static List<SalvageCache> placeDelve(NavigationGrid grid, List<LandingPad> pads,
                                               List<Cell> candidates, List<Cell> targets, long seed, Delve delve) {
        if (pads.isEmpty() || delve.caches() == 0) return List.of();
        var shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(seed ^ 0x44454c5645L));
        var legal = shuffled.stream().filter(c -> grid.isWalkable(c.x, c.y))
                .filter(c -> pads.stream().noneMatch(p -> distance(c.x, c.y, p.centerX, p.centerY) < 28 * 28))
                .filter(c -> targets.stream().noneMatch(t -> distance(c.x, c.y, t.x, t.y) < 16 * 16))
                .filter(c -> pads.stream().anyMatch(p -> grid.arePathConnected(p.centerX, p.centerY, c.x, c.y, false)))
                .toList();
        var chosen = new ArrayList<Cell>();
        pick(legal.stream().filter(delve::farHalf).toList(), chosen, delve.farQuota());
        pick(legal, chosen, delve.caches());
        var selected = new ArrayList<SalvageCache>();
        var resources = SalvageResource.values();
        for (int i = 0; i < chosen.size(); i++) {
            var resource = resources[i % resources.length];
            var cell = chosen.get(i);
            selected.add(new SalvageCache("field-cache-" + i, cell.x, cell.y, resource, resource.cacheQuantity));
        }
        return List.copyOf(selected);
    }

    private static void pick(List<Cell> from, List<Cell> chosen, int upTo) {
        for (var c : from) {
            if (chosen.size() >= upTo) return;
            if (chosen.contains(c)) continue;
            if (chosen.stream().anyMatch(s -> distance(c.x, c.y, s.x, s.y) < DELVE_SPACING * DELVE_SPACING)) continue;
            chosen.add(c);
        }
    }

    /** Pure geometry seam: supplied candidates are clear sites, targets are mandatory/tactical centers. */
    public static List<SalvageCache> place(NavigationGrid grid, List<LandingPad> pads,
                                          List<Cell> candidates, List<Cell> targets, long seed) {
        return place(grid, pads, candidates, targets, seed, List.of());
    }

    public static List<SalvageCache> place(NavigationGrid grid, List<LandingPad> pads,
                                          List<Cell> candidates, List<Cell> targets, long seed, List<Site> sites) {
        if (pads.isEmpty()) return List.of();
        var shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(seed ^ 0x53414c56414745L));
        var selected = new ArrayList<SalvageCache>();
        for (var site : sites) {
            if (selected.stream().anyMatch(s -> s.resource == site.resource))
                throw new IllegalArgumentException("Duplicate authored salvage resource: " + site.resource);
            var cell = candidates.stream().filter(site::contains).filter(c -> grid.isWalkable(c.x, c.y))
                    .filter(c -> pads.stream().anyMatch(p -> grid.arePathConnected(p.centerX, p.centerY, c.x, c.y, false)))
                    .min(Comparator.comparingInt((Cell c) -> distance(c.x, c.y, site.preferred.x, site.preferred.y))
                            .thenComparingInt(Cell::x).thenComparingInt(Cell::y))
                    .orElseThrow(() -> new IllegalStateException("No clear reachable cell in authored salvage site: " + site));
            selected.add(cache(site.resource, cell));
        }
        for (var c : shuffled) {
            if (selected.size() == SalvageResource.values().length) break;
            if (!grid.isWalkable(c.x, c.y)) continue;
            if (pads.stream().anyMatch(p -> distance(c.x, c.y, p.centerX, p.centerY) < 28 * 28)) continue;
            if (targets.stream().anyMatch(t -> distance(c.x, c.y, t.x, t.y) < 16 * 16)) continue;
            if (selected.stream().anyMatch(s -> distance(c.x, c.y, s.cellX, s.cellY) < 26 * 26)) continue;
            if (pads.stream().noneMatch(p -> grid.arePathConnected(p.centerX, p.centerY, c.x, c.y, false))) continue;
            var resource = Arrays.stream(SalvageResource.values())
                    .filter(r -> selected.stream().noneMatch(s -> s.resource == r)).findFirst().orElseThrow();
            selected.add(cache(resource, c));
            if (selected.size() == SalvageResource.values().length) break;
        }
        return List.copyOf(selected);
    }

    private static SalvageCache cache(SalvageResource resource, Cell cell) {
        return new SalvageCache("field-cache-" + resource.ordinal(), cell.x, cell.y, resource, resource.cacheQuantity);
    }

    private static int distance(int x, int y, int tx, int ty) {
        int dx = x - tx, dy = y - ty;
        return dx * dx + dy * dy;
    }
}
