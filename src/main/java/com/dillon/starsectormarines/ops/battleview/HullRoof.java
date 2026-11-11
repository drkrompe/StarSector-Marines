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
 * plan. When there are as many patches as the plan has {@link HullPlan#SECTIONS}, each is laid where the plan's own
 * section fits it best, both ways round: its deck roofed, its torn deck and the outside not, so solid hull the roofs
 * leave out (the bow's taper, armour no compartment's roof reaches) does not throw the painting off. That solid hull is
 * painted too ({@link Section#solid}), over the cells no roof covers. Otherwise whatever length the breaks took away is
 * shared out between the patches, and the bow is the end whose corners are open, where the plan tapers. A hull lying
 * whole is one section the plan's length.
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

    /** One piece of the broken hull: a run of the plan laid on the map, its bow along a heading. */
    static final class Section {
        /** Its roof's bounds on the map, and the bow's direction. */
        final int minX, minY, maxX, maxY, headingX, headingY;
        /** The map cell of plan cell {@code (0, 0)} as this section lies (off the map for all but the stern). */
        private final float baseX, baseY;
        /** The plan's section it was matched to, or null when laid by its bounds alone. */
        final HullPlan.Section plan;
        /** Solid hull of this section no roof covers, cell indices: painted as the roof is. */
        int[] solid = new int[0];
        /** The compartments whose roofs are this section's. */
        final List<Building> buildings = new ArrayList<>();

        Section(int minX, int minY, int maxX, int maxY, int headingX, int headingY, float baseX, float baseY,
                HullPlan.Section plan) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.headingX = headingX;
            this.headingY = headingY;
            this.baseX = baseX;
            this.baseY = baseY;
            this.plan = plan;
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
            return headingX * (x - baseX) + headingY * (y - baseY) + (headingX < 0 || headingY < 0 ? 1 : 0);
        }

        /** Plan {@code v}, cells across from the hull's right-hand side, of the map point {@code (x, y)}. */
        float planV(float x, float y) {
            // Across runs a quarter turn counter-clockwise of the heading, as the ground lays it.
            return -headingY * (x - baseX) + headingX * (y - baseY) + (headingY > 0 || headingX < 0 ? 1 : 0);
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

    /**
     * The section whose bounds hold cell {@code (x, y)}, or null: a compartment's roof may run over more than one
     * section (armour carried across a break), and each cell takes the painting as its own section lies.
     */
    Section at(int x, int y) {
        for (Section s : sections) {
            if (x >= s.minX && x <= s.maxX && y >= s.minY && y <= s.maxY) return s;
        }
        return null;
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
        BitSet roofed = new BitSet(w * h);
        for (Building b : buildings.all()) {
            RoofPlans.Plan plan = plans.of(topology, buildings, b);
            if (plan == null || plan.cells.length == 0) continue;
            for (int at : plan.cells) roofed.set(at);
            if (ship.of.containsKey(b)) continue;
            for (Section s : ship.sections) {
                if (s.inside(plan.cells, w)) {
                    ship.of.put(b, s);
                    break;
                }
            }
        }
        for (Map.Entry<Building, Section> e : ship.of.entrySet()) e.getValue().buildings.add(e.getKey());
        for (Section s : ship.sections) s.solid = solid(s, roofed, w, h);
        return ship;
    }

    /**
     * {@code s}'s solid hull no roof covers, as cell indices: armour the compartments' roofs leave out, the bow's
     * taper. Armour beside a torn deck stays open to the sky, as the ground leaves it.
     */
    static int[] solid(Section s, BitSet roofed, int w, int h) {
        if (s.plan == null) return new int[0];
        HullPlan hull = HullPlan.plan();
        List<Integer> out = new ArrayList<>();
        int bx = Math.round(s.baseX), by = Math.round(s.baseY);
        for (int u = s.plan.u0(); u <= s.plan.u1(); u++) {
            for (int v = 0; v < HullPlan.BREADTH; v++) {
                if (hull.at(u, v) != HullPlan.Kind.HULL || besideTorn(hull, u, v)) continue;
                int x = bx + u * s.headingX - v * s.headingY;
                int y = by + u * s.headingY + v * s.headingX;
                if (x < 0 || y < 0 || x >= w || y >= h || roofed.get(y * w + x)) continue;
                out.add(y * w + x);
            }
        }
        int[] cells = new int[out.size()];
        for (int i = 0; i < cells.length; i++) cells[i] = out.get(i);
        return cells;
    }

    private static boolean besideTorn(HullPlan hull, int u, int v) {
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                if (hull.near(u, v, du, dv) == HullPlan.Kind.TORN) return true;
            }
        }
        return false;
    }

    private Section sectionOf(BitSet patch, int w) {
        int at = patch.nextSetBit(0);
        return at(at % w, at / w);
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
        if (patches.size() == HullPlan.SECTIONS.size()) return matched(patches, bounds, order, w, alongX);
        int first = order.get(0), last = order.get(order.size() - 1);
        boolean bowHigh = openEnd(patches.get(last), bounds.get(last), w, alongX, true)
                || !openEnd(patches.get(first), bounds.get(first), w, alongX, false);
        if (!bowHigh) Collections.reverse(order);
        float gap = order.size() > 1 ? (HullPlan.LENGTH - total) / (float) (order.size() - 1) : 0f;
        int hx = alongX ? (bowHigh ? 1 : -1) : 0, hy = alongX ? 0 : (bowHigh ? 1 : -1);
        List<Section> sections = new ArrayList<>();
        float u = 0f;
        float half = HullPlan.BREADTH / 2f;
        for (int i : order) {
            int[] b = bounds.get(i);
            float centre = alongX ? (b[1] + b[3] + 1) / 2f : (b[0] + b[2] + 1) / 2f;
            float baseX, baseY;
            if (hx > 0) {
                baseX = b[0] - u;
                baseY = centre - half;
            } else if (hx < 0) {
                baseX = u + b[2];
                baseY = centre + half - 1;
            } else if (hy > 0) {
                baseX = centre + half - 1;
                baseY = b[1] - u;
            } else {
                baseX = centre - half;
                baseY = u + b[3];
            }
            sections.add(new Section(b[0], b[1], b[2], b[3], hx, hy, baseX, baseY, null));
            u += (alongX ? b[2] - b[0] + 1 : b[3] - b[1] + 1) + gap;
        }
        return new HullRoof(sections);
    }

    /** How far from its first guess a section's fit is sought, cells along and across. UNTUNED. */
    static final int SEEK_ALONG = 14, SEEK_ACROSS = 5;

    /**
     * The patches laid as the plan's sections, in order along the axis from whichever end fits the plan better: each
     * where its plan section fits it best.
     */
    private static HullRoof matched(List<BitSet> patches, List<int[]> bounds, List<Integer> order, int w,
                                    boolean alongX) {
        List<Section> best = null;
        long bestScore = Long.MIN_VALUE;
        for (int dir = 1; dir >= -1; dir -= 2) {
            int hx = alongX ? dir : 0, hy = alongX ? 0 : dir;
            List<Section> sections = new ArrayList<>();
            long score = 0;
            for (int k = 0; k < order.size(); k++) {
                int i = order.get(dir > 0 ? k : order.size() - 1 - k);
                HullPlan.Section plan = HullPlan.SECTIONS.get(k);
                long[] fit = place(patches.get(i), bounds.get(i), plan, k > 0, w, hx, hy);
                // Bounded by the whole of its plan section as it lies, roofed or not.
                int ax = (int) fit[1] + plan.u0() * hx, ay = (int) fit[2] + plan.u0() * hy;
                int bx = (int) fit[1] + plan.u1() * hx - (HullPlan.BREADTH - 1) * hy;
                int by = (int) fit[2] + plan.u1() * hy + (HullPlan.BREADTH - 1) * hx;
                sections.add(new Section(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by), hx, hy,
                        fit[1], fit[2], plan));
                score += fit[0];
            }
            if (score > bestScore) {
                bestScore = score;
                best = sections;
            }
        }
        return new HullRoof(best);
    }

    /**
     * Where {@code section} of the plan lies best over {@code patch} (its bounds {@code b}), heading {@code (hx, hy)}:
     * {@code {score, baseX, baseY}}. First guessed from the patch's end facing a break ({@code aftBroken}: its stern
     * end, else its bow end) and the middle of its breadth, then sought round that. A deck cell scores roofed and
     * loses unroofed; a roofed torn deck, outside or cell off the section loses twice.
     */
    static long[] place(BitSet patch, int[] b, HullPlan.Section section, boolean aftBroken, int w, int hx, int hy) {
        int end = aftBroken ? -1 : 1;
        int ax, ay;
        if (hx != 0) {
            ax = hx * end > 0 ? b[2] : b[0];
            ay = (b[1] + b[3]) / 2;
        } else {
            ax = (b[0] + b[2]) / 2;
            ay = hy * end > 0 ? b[3] : b[1];
        }
        int ua = aftBroken ? section.u0() : section.u1(), va = HullPlan.BREADTH / 2;
        int gx = ax - ua * hx + va * hy, gy = ay - ua * hy - va * hx;
        int cells = patch.cardinality();
        HullPlan hull = HullPlan.plan();
        long[] best = {Long.MIN_VALUE, gx, gy};
        for (int along = -SEEK_ALONG; along <= SEEK_ALONG; along++) {
            for (int across = -SEEK_ACROSS; across <= SEEK_ACROSS; across++) {
                int bx = gx + along * hx - across * hy, by = gy + along * hy + across * hx;
                long score = 0;
                int hit = 0;
                for (int u = section.u0(); u <= section.u1(); u++) {
                    for (int v = 0; v < HullPlan.BREADTH; v++) {
                        int x = bx + u * hx - v * hy, y = by + u * hy + v * hx;
                        boolean in = x >= 0 && y >= 0 && x < w && patch.get(y * w + x);
                        if (in) hit++;
                        switch (hull.at(u, v)) {
                            case FLOOR -> score += in ? 1 : -1;
                            case TORN, OUTSIDE -> score -= in ? 2 : 0;
                            default -> { }
                        }
                    }
                }
                score -= 2L * (cells - hit);
                if (score > best[0]) best = new long[]{score, bx, by};
            }
        }
        return best;
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
