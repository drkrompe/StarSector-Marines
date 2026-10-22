package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Where the heart's bores stand and how grown they are when the battle opens
 * (`bloom-mission-arc.md`, the heart): two or three <b>mother bores</b> on one
 * reservoir round the old workings at the far edge, and <b>satellite bores</b>
 * between the heart and the start line, every one already at maw.
 *
 * <p>Sites are chosen once, at setup, on ground that can be walked to from the
 * start line: the plan is the map's <b>reachability proof</b>. Trees are
 * impassable crowns on the grid, so a site the company could reach only by
 * burning or felling its way in is never chosen, and fire is never counted on to
 * open the way to a mother.
 *
 * <p>Growth is bought through the ordinary feed seam ({@link SwarmBores#feed}),
 * as if the bodies of the dark country had gone into them: that takes each to
 * maw, sprouts the spore colonies of every tier it crossed, shifts its surges'
 * age mix older, and sets each mother's Matriarch at the stage her feeding
 * earns. Nothing about a bore is scripted beyond where it opened and what it
 * was fed.
 *
 * @param sites mothers first, then satellites, in the order they open
 */
public record HeartBores(List<Site> sites) {

    /** One bore: {@code number} counts mothers and satellites separately from 1. */
    public record Site(int number, boolean mother, int x, int y) { }

    /** Bodies fed into each mother before the battle: maw, and an elder Matriarch. */
    public static final float MOTHER_FEED = 16f;
    /** Bodies fed into each satellite: maw. */
    public static final float SATELLITE_FEED = 9f;
    /** Cells round the workings' seed a mother may stand in. */
    static final int MOTHER_RADIUS = 42;
    /** Two mothers stand at least this far apart: one reservoir, several mouths. */
    static final int MOTHER_SPACING = 22;
    /** Any satellite stands at least this far from every other bore. */
    static final int SATELLITE_SPACING = 34;
    /** No bore nearer the map edge than this. */
    static final int EDGE_MARGIN = 10;
    /** Random picks tried per site before the plan gives up on it. */
    private static final int ATTEMPTS = 400;

    private static final long SALT = 0x4845_4152_5442_4F52L;

    /** The mothers. */
    public List<Site> mothers() {
        List<Site> out = new ArrayList<>();
        for (Site site : sites) if (site.mother) out.add(site);
        return out;
    }

    /** The satellites. */
    public List<Site> satellites() {
        List<Site> out = new ArrayList<>();
        for (Site site : sites) if (!site.mother) out.add(site);
        return out;
    }

    /**
     * Chooses the heart's sites on a generated country, or returns {@code null}
     * when the map cannot seat every mother and every satellite on ground
     * reachable from {@code start}: the caller re-rolls the seed.
     *
     * @param workings the old workings' seed cell, round which the mothers stand
     * @param start    a cell on the start line
     */
    public static HeartBores plan(NavigationGrid grid, CellTopology topology, Form form,
                                  int[] workings, int[] start, long seed) {
        boolean[] reachable = reachableFrom(grid, start);
        if (reachable == null) return null;
        SplittableRandom rng = new SplittableRandom(seed ^ SALT);
        int width = grid.getWidth(), height = grid.getHeight();
        List<Site> sites = new ArrayList<>();
        int mothers = 2 + rng.nextInt(2);
        for (int m = 1; m <= mothers; m++) {
            int[] cell = pick(grid, topology, reachable, sites, rng, MOTHER_SPACING,
                    workings[0] - MOTHER_RADIUS, workings[1] - MOTHER_RADIUS,
                    workings[0] + MOTHER_RADIUS, workings[1] + MOTHER_RADIUS, workings);
            if (cell == null) return null;
            sites.add(new Site(m, true, cell[0], cell[1]));
        }
        float[][] bands = satelliteBands(form);
        for (int s = 0; s < bands.length; s++) {
            float[] band = bands[s];
            int[] a = form.cellAt(band[0], band[2]), b = form.cellAt(band[1], band[3]);
            int[] cell = pick(grid, topology, reachable, sites, rng, SATELLITE_SPACING,
                    Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), null);
            if (cell == null) return null;
            sites.add(new Site(s + 1, false, cell[0], cell[1]));
        }
        return new HeartBores(List.copyOf(sites));
    }

    /**
     * Where each satellite may stand, as {depth from, depth to, across from,
     * across to}: Broad Front spreads them across the width, one to each fifth,
     * so every lane meets one; The Burn strings them along its one approach.
     */
    static float[][] satelliteBands(Form form) {
        if (form == Form.BROAD_FRONT) {
            float[][] bands = new float[5][];
            for (int i = 0; i < 5; i++) {
                bands[i] = new float[]{0.52f, 0.72f, 0.04f + i * 0.19f, 0.04f + i * 0.19f + 0.15f};
            }
            return bands;
        }
        return new float[][]{
                {0.44f, 0.54f, 0.20f, 0.80f},
                {0.52f, 0.64f, 0.20f, 0.80f},
                {0.64f, 0.74f, 0.20f, 0.80f}};
    }

    /** Opens every site and feeds it to its starting growth. Call at setup, tick zero. */
    public void open(BattleSimulation sim) {
        SwarmBores bores = sim.swarmBores();
        for (Site site : sites) {
            SwarmBore bore = bores.open(sim, site.number, site.mother, site.x, site.y);
            if (bore == null) throw new IllegalStateException("heart site " + site + " is not clear ground at setup");
            bores.feed(sim, bore, site.mother ? MOTHER_FEED : SATELLITE_FEED);
        }
    }

    /**
     * A random clear cell in the rect, reachable, spaced from every site so far;
     * with {@code near} set, the cell must also lie within {@link #MOTHER_RADIUS}
     * of it.
     */
    private static int[] pick(NavigationGrid grid, CellTopology topology, boolean[] reachable, List<Site> taken,
                              SplittableRandom rng, int spacing, int x0, int y0, int x1, int y1, int[] near) {
        int width = grid.getWidth(), height = grid.getHeight();
        x0 = Math.max(EDGE_MARGIN, x0);
        y0 = Math.max(EDGE_MARGIN, y0);
        x1 = Math.min(width - 1 - EDGE_MARGIN, x1);
        y1 = Math.min(height - 1 - EDGE_MARGIN, y1);
        if (x0 > x1 || y0 > y1) return null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1), y = y0 + rng.nextInt(y1 - y0 + 1);
            if (near != null) {
                int dx = x - near[0], dy = y - near[1];
                if (dx * dx + dy * dy > MOTHER_RADIUS * MOTHER_RADIUS) continue;
            }
            if (!reachable[y * width + x] || !clearGround(grid, topology, x, y)) continue;
            boolean spaced = true;
            for (Site site : taken) {
                int dx = site.x - x, dy = site.y - y;
                if (dx * dx + dy * dy < spacing * spacing) { spaced = false; break; }
            }
            if (spaced) return new int[]{x, y};
        }
        return null;
    }

    /**
     * Open ground a bore can open on and pour out of: walkable outdoors, no
     * door, no water, with its mouth's ring walkable round it.
     */
    static boolean clearGround(NavigationGrid grid, CellTopology topology, int x, int y) {
        for (int dy = -SwarmBores.MOUTH_RADIUS; dy <= SwarmBores.MOUTH_RADIUS; dy++) {
            for (int dx = -SwarmBores.MOUTH_RADIUS; dx <= SwarmBores.MOUTH_RADIUS; dx++) {
                int cx = x + dx, cy = y + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || grid.isDoorway(cx, cy)) return false;
                if (topology.getBuildingId(cx, cy) != 0 || topology.getGroundKind(cx, cy) == GroundKind.WATER) return false;
            }
        }
        return true;
    }

    /**
     * Every cell reachable on foot from the walkable cell nearest {@code start},
     * four-connected over walkable ground; {@code null} when there is no walkable
     * ground near the start at all.
     */
    static boolean[] reachableFrom(NavigationGrid grid, int[] start) {
        int width = grid.getWidth(), height = grid.getHeight();
        int origin = -1;
        for (int r = 0; r < 24 && origin < 0; r++) {
            for (int dy = -r; dy <= r && origin < 0; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = start[0] + dx, y = start[1] + dy;
                    if (grid.inBounds(x, y) && grid.isWalkable(x, y)) { origin = y * width + x; break; }
                }
            }
        }
        if (origin < 0) return null;
        boolean[] seen = new boolean[width * height];
        int[] queue = new int[width * height];
        int head = 0, tail = 0;
        queue[tail++] = origin;
        seen[origin] = true;
        while (head < tail) {
            int idx = queue[head++];
            int x = idx % width, y = idx / width;
            if (x > 0) tail = visit(grid, seen, queue, tail, x - 1, y, width);
            if (x + 1 < width) tail = visit(grid, seen, queue, tail, x + 1, y, width);
            if (y > 0) tail = visit(grid, seen, queue, tail, x, y - 1, width);
            if (y + 1 < height) tail = visit(grid, seen, queue, tail, x, y + 1, width);
        }
        return seen;
    }

    private static int visit(NavigationGrid grid, boolean[] seen, int[] queue, int tail, int x, int y, int width) {
        int idx = y * width + x;
        if (seen[idx] || !grid.isWalkable(x, y)) return tail;
        seen[idx] = true;
        queue[tail] = idx;
        return tail + 1;
    }
}
