package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fallen hull's roof drawn as the ship it is (`fallen-hull-nouns.md`; art in {@code art-source/fallen-hull},
 * {@code hull_roof.py}): one painting of the whole survey mothership, burnt and broken, in the hull's plan space
 * ({@link HullPlan}), {@link HullPlan#LENGTH} cells along from the stern, {@link HullPlan#BREADTH} across, laid over the
 * hull's roof in place of {@link RoofStyle#HULL}'s repeating field. A roofed cell of the hull draws its field pieces cut
 * out of the painting where the cell falls on the plan, turned to the hull's heading; a broken cell still draws the
 * roof sheet's damage, and the painting's own rim stands in for the eave trims. Presentation only.
 *
 * <p>The hull is found in the roofs themselves, so nothing about the ground need reach the renderer. The hull broke
 * up on impact, so it may lie as several {@link Section sections} along its trench: every connected patch of
 * {@link RoofStyle#HULL} roof big enough to be one, on a common axis, the broadest exactly the plan's breadth, their
 * lengths together no more than the plan's. Taken in order along the axis from the stern, each is the next run of the
 * plan; whatever length the breaks took away is shared out between them. The bow is the end whose corners are open,
 * where the plan tapers. A hull lying whole is one section the plan's length.
 *
 * <p>The hull is entered compartment by compartment: each is its own building, so each roof lifts on its own when the
 * compartment is boarded, and the rest keeps the ship's painting. A compartment whose roof takes no hull style is still
 * the ship's when its roof lies wholly inside a section's bounds.
 */
final class HullRoof {

    /** The painting, plan space: x from stern to bow, y across with plan {@code v = 0} at the bottom. */
    static final String TEXTURE = "graphics/battle/fallen-hull/hull-roof.png";
    /** Cells a patch of hull roof must hold to be a section of the hull rather than a lump of debris. UNTUNED. */
    static final int MIN_SECTION_CELLS = 8 * HullPlan.BREADTH / 2;

    /** One piece of the broken hull: a run of the plan, from {@link #u0} along. */
    static final class Section {
        /** Its cells' bounds on the map, and the bow's direction. */
        final int minX, minY, maxX, maxY, headingX, headingY;
        /** The plan {@code u} of its sternmost edge. */
        final float u0;
        /** Its middle across the heading, cell units: the plan's centre line. */
        private final float centre;

        Section(int minX, int minY, int maxX, int maxY, int headingX, int headingY, float u0) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.headingX = headingX;
            this.headingY = headingY;
            this.u0 = u0;
            this.centre = headingX != 0 ? (minY + maxY + 1) / 2f : (minX + maxX + 1) / 2f;
        }

        /** Whether every one of {@code cells} (cell indices, {@code w} wide) lies within the section's bounds. */
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
            if (headingX > 0) return u0 + x - minX;
            if (headingX < 0) return u0 + maxX + 1 - x;
            if (headingY > 0) return u0 + y - minY;
            return u0 + maxY + 1 - y;
        }

        /** Plan {@code v}, cells across from the hull's right-hand side, of the map point {@code (x, y)}. */
        float planV(float x, float y) {
            float half = HullPlan.BREADTH / 2f;
            if (headingX > 0) return y - (centre - half);
            if (headingX < 0) return centre + half - y;
            if (headingY > 0) return centre + half - x;
            return x - (centre - half);
        }
    }

    private final List<Section> sections;
    private final Map<Building, Section> of = new HashMap<>();

    private HullRoof(List<Section> sections) {
        this.sections = sections;
    }

    /** The hull's sections, stern first. */
    List<Section> sections() {
        return sections;
    }

    /** The section {@code b}'s roof is part of, or null when it is not the ship's. */
    Section of(Building b) {
        return of.get(b);
    }

    /** The ship among this battle's roofs, or null when no hull roof fits the plan. */
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
        List<BitSet> patches = patches(hull, w, h);
        HullRoof ship = fit(patches, w);
        if (ship == null) return null;
        for (int i = 0; i < patches.size(); i++) {
            BitSet patch = patches.get(i);
            Section s = ship.sectionOf(patch, w);
            for (int at = patch.nextSetBit(0); at >= 0; at = patch.nextSetBit(at + 1)) {
                if (owner[at] > 0) ship.of.putIfAbsent(byIndex[owner[at]], s);
            }
        }
        for (Building b : buildings.all()) {
            if (ship.of.containsKey(b)) continue;
            RoofPlans.Plan plan = plans.of(topology, buildings, b);
            if (plan == null || plan.cells.length == 0) continue;
            for (Section s : ship.sections) {
                if (s.inside(plan.cells, w)) {
                    ship.of.put(b, s);
                    break;
                }
            }
        }
        return ship;
    }

    private Section sectionOf(BitSet patch, int w) {
        int at = patch.nextSetBit(0);
        int x = at % w, y = at / w;
        for (Section s : sections) {
            if (x >= s.minX && x <= s.maxX && y >= s.minY && y <= s.maxY) return s;
        }
        return null;
    }

    /**
     * The ship laid over {@code patches} (each a section's cells as indices, {@code w} wide), or null when they are no
     * hull of the plan: not on one axis, none the plan's breadth, or longer together than the plan.
     */
    static HullRoof fit(List<BitSet> patches, int w) {
        if (patches.isEmpty()) return null;
        List<int[]> bounds = new ArrayList<>();
        int uMinX = Integer.MAX_VALUE, uMinY = Integer.MAX_VALUE, uMaxX = Integer.MIN_VALUE, uMaxY = Integer.MIN_VALUE;
        for (BitSet patch : patches) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int at = patch.nextSetBit(0); at >= 0; at = patch.nextSetBit(at + 1)) {
                int x = at % w, y = at / w;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
            bounds.add(new int[]{minX, minY, maxX, maxY});
            uMinX = Math.min(uMinX, minX);
            uMinY = Math.min(uMinY, minY);
            uMaxX = Math.max(uMaxX, maxX);
            uMaxY = Math.max(uMaxY, maxY);
        }
        boolean alongX = uMaxX - uMinX >= uMaxY - uMinY;
        int total = 0, broadest = 0;
        for (int[] b : bounds) {
            total += alongX ? b[2] - b[0] + 1 : b[3] - b[1] + 1;
            broadest = Math.max(broadest, alongX ? b[3] - b[1] + 1 : b[2] - b[0] + 1);
        }
        if (broadest != HullPlan.BREADTH || total > HullPlan.LENGTH || total * 2 < HullPlan.LENGTH) return null;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bounds.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> alongX ? bounds.get(i)[0] : bounds.get(i)[1]));
        int first = order.get(0), last = order.get(order.size() - 1);
        boolean bowHigh = openEnd(patches.get(last), bounds.get(last), w, alongX, true)
                || !openEnd(patches.get(first), bounds.get(first), w, alongX, false);
        if (!bowHigh) Collections.reverse(order);
        float gap = order.size() > 1 ? (HullPlan.LENGTH - total) / (float) (order.size() - 1) : 0f;
        int hx = alongX ? (bowHigh ? 1 : -1) : 0, hy = alongX ? 0 : (bowHigh ? 1 : -1);
        List<Section> sections = new ArrayList<>();
        float u = 0f;
        for (int i : order) {
            int[] b = bounds.get(i);
            sections.add(new Section(b[0], b[1], b[2], b[3], hx, hy, u));
            u += (alongX ? b[2] - b[0] + 1 : b[3] - b[1] + 1) + gap;
        }
        return new HullRoof(sections);
    }

    /** Whether both corners of a patch's end along the axis are open: the bow's taper. */
    private static boolean openEnd(BitSet patch, int[] b, int w, boolean alongX, boolean high) {
        if (alongX) {
            int x = high ? b[2] : b[0];
            return !patch.get(b[1] * w + x) && !patch.get(b[3] * w + x);
        }
        int y = high ? b[3] : b[1];
        return !patch.get(y * w + b[0]) && !patch.get(y * w + b[2]);
    }

    /** Every connected patch of {@code cells} big enough to be a section. */
    private static List<BitSet> patches(BitSet cells, int w, int h) {
        BitSet seen = new BitSet(w * h);
        List<BitSet> out = new ArrayList<>();
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
            if (count >= MIN_SECTION_CELLS) out.add(patch);
        }
        return out;
    }

    private static void visit(BitSet cells, BitSet seen, ArrayDeque<Integer> open, int at) {
        if (!cells.get(at) || seen.get(at)) return;
        seen.set(at);
        open.add(at);
    }
}
