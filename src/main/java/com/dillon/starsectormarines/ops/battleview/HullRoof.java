package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A fallen hull's roof drawn as the ship it is (`fallen-hull-nouns.md`; art in {@code art-source/fallen-hull},
 * {@code hull_roof.py}): one painting of the whole survey mothership in the hull's plan space ({@link HullPlan}),
 * {@link HullPlan#LENGTH} cells along from the stern, {@link HullPlan#BREADTH} across, laid over the hull's roof in
 * place of {@link RoofStyle#HULL}'s repeating field. A roofed cell of the hull draws its field pieces cut out of the
 * painting where the cell falls on the plan, turned to the hull's heading; a broken cell still draws the roof sheet's
 * damage, and the painting's own rim stands in for the eave trims. Presentation only.
 *
 * <p>The hull is found in the roofs themselves, so nothing about the ground need reach the renderer: the largest
 * connected patch of {@link RoofStyle#HULL} roof whose bounds are the plan's, along either axis. Its bow is the end
 * whose corners are open, where the plan tapers. Any other hull roof (a debris lump) keeps the plain style.
 *
 * <p>The hull is entered compartment by compartment: each is its own building, so each roof lifts on its own when the
 * compartment is boarded, and the rest keeps the ship's painting. A compartment walled only by the ship's interior
 * bulkheads takes no hull style from its walls, so the ship also holds every roof lying wholly inside its bounds.
 */
final class HullRoof {

    /** The painting, plan space: x from stern to bow, y across with plan {@code v = 0} at the bottom. */
    static final String TEXTURE = "graphics/battle/fallen-hull/hull-roof.png";

    /** Plan point (0, 0)'s cell corner on the map, and the bow's direction. */
    final int minX, minY, maxX, maxY, headingX, headingY;
    private final Set<Building> buildings;

    private HullRoof(int minX, int minY, int maxX, int maxY, int headingX, int headingY, Set<Building> buildings) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.headingX = headingX;
        this.headingY = headingY;
        this.buildings = buildings;
    }

    /** Whether {@code b}'s roof is part of the ship. */
    boolean holds(Building b) {
        return buildings.contains(b);
    }

    /** Whether every one of {@code cells} (cell indices, {@code w} wide) lies within the hull's bounds. */
    boolean inside(int[] cells, int w) {
        for (int at : cells) {
            int x = at % w, y = at / w;
            if (x < minX || x > maxX || y < minY || y > maxY) return false;
        }
        return true;
    }

    /** Degrees counter-clockwise the painting is turned to lie along the heading. */
    float angleDeg() {
        if (headingX > 0) return 0f;
        if (headingY > 0) return 90f;
        if (headingX < 0) return 180f;
        return 270f;
    }

    /** Plan {@code u}, cells from the stern, of the map point {@code (x, y)} (cell units, continuous). */
    float planU(float x, float y) {
        if (headingX > 0) return x - minX;
        if (headingX < 0) return maxX + 1 - x;
        if (headingY > 0) return y - minY;
        return maxY + 1 - y;
    }

    /** Plan {@code v}, cells across from the hull's right-hand side, of the map point {@code (x, y)}. */
    float planV(float x, float y) {
        if (headingX > 0) return y - minY;
        if (headingX < 0) return maxY + 1 - y;
        if (headingY > 0) return maxX + 1 - x;
        return x - minX;
    }

    /** The ship among this battle's roofs, or null when no hull roof has the plan's bounds. */
    static HullRoof find(CellTopology topology, Buildings buildings, RoofPlans plans) {
        if (buildings == null || buildings.isEmpty()) return null;
        int w = topology.getWidth();
        int h = topology.getHeight();
        BitSet hull = new BitSet(w * h);
        int[] owner = new int[w * h];
        Building[] byIndex = new Building[buildings.all().size() + 1];
        int k = 0;
        for (Building b : buildings.all()) {
            RoofPlans.Plan plan = plans.of(topology, buildings, b);
            if (plan == null || plan.style != RoofStyle.HULL) continue;
            byIndex[++k] = b;
            for (int at : plan.cells) {
                hull.set(at);
                owner[at] = k;
            }
        }
        if (hull.isEmpty()) return null;
        BitSet patch = largestPatch(hull, w, h);
        HullRoof ship = fit(patch, w);
        if (ship == null) return null;
        for (int at = patch.nextSetBit(0); at >= 0; at = patch.nextSetBit(at + 1)) {
            if (owner[at] > 0) ship.buildings.add(byIndex[owner[at]]);
        }
        for (Building b : buildings.all()) {
            RoofPlans.Plan plan = plans.of(topology, buildings, b);
            if (plan != null && plan.cells.length > 0 && ship.inside(plan.cells, w)) ship.buildings.add(b);
        }
        return ship;
    }

    /**
     * The ship laid over {@code patch} (cell indices, {@code w} wide), or null when its bounds are not the plan's. The
     * bow is the end of the long axis whose two corners are open.
     */
    static HullRoof fit(BitSet patch, int w) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int at = patch.nextSetBit(0); at >= 0; at = patch.nextSetBit(at + 1)) {
            int x = at % w, y = at / w;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        int spanX = maxX - minX + 1, spanY = maxY - minY + 1;
        Set<Building> none = new HashSet<>();
        if (spanX == HullPlan.LENGTH && spanY == HullPlan.BREADTH) {
            boolean bowEast = !patch.get(minY * w + maxX) && !patch.get(maxY * w + maxX);
            return new HullRoof(minX, minY, maxX, maxY, bowEast ? 1 : -1, 0, none);
        }
        if (spanY == HullPlan.LENGTH && spanX == HullPlan.BREADTH) {
            boolean bowNorth = !patch.get(maxY * w + minX) && !patch.get(maxY * w + maxX);
            return new HullRoof(minX, minY, maxX, maxY, 0, bowNorth ? 1 : -1, none);
        }
        return null;
    }

    private static BitSet largestPatch(BitSet cells, int w, int h) {
        BitSet seen = new BitSet(w * h);
        BitSet best = new BitSet();
        int bestCount = 0;
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int start = cells.nextSetBit(0); start >= 0; start = cells.nextSetBit(start + 1)) {
            if (seen.get(start)) continue;
            BitSet patch = new BitSet(w * h);
            int count = 0;
            seen.set(start);
            open.add(start);
            while (!open.isEmpty()) {
                int at = open.poll();
                patch.set(at);
                count++;
                int x = at % w, y = at / w;
                if (x > 0) visit(cells, seen, open, at - 1);
                if (x < w - 1) visit(cells, seen, open, at + 1);
                if (y > 0) visit(cells, seen, open, at - w);
                if (y < h - 1) visit(cells, seen, open, at + w);
            }
            if (count > bestCount) {
                bestCount = count;
                best = patch;
            }
        }
        return best;
    }

    private static void visit(BitSet cells, BitSet seen, ArrayDeque<Integer> open, int at) {
        if (!cells.get(at) || seen.get(at)) return;
        seen.set(at);
        open.add(at);
    }
}
