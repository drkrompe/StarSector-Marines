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
 * between the heart and the start line, every one already at maw. The Burn adds
 * <b>young bores</b> along the route: fissures, unfed, that grow on their own
 * clock into maws if the company leaves them, and raise a brood spire when they
 * do.
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

    /**
     * One bore: {@code number} counts mothers and satellites separately from 1; a {@code young} satellite opens a
     * fissure, unfed.
     */
    public record Site(int number, boolean mother, int x, int y, boolean young) {
        public Site(int number, boolean mother, int x, int y) { this(number, mother, x, y, false); }
    }

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
    /**
     * Cells from the supply road within which Broad Front's road satellite stands (`heart-story-beats.md` F6): near
     * enough that its surges cut the road, so taking it is what keeps the armour and ammunition coming.
     */
    static final int ROAD_REACH = 24;
    /** The road satellite stands no nearer the start than this depth: past the settlement's near gate, in the bloom. */
    static final float ROAD_SATELLITE_DEPTH = 0.42f;
    /** Depth band of Broad Front's inner row: past the outer row, short of the hollow round the workings. */
    static final float INNER_DEPTH_FROM = 0.72f, INNER_DEPTH_TO = 0.84f;
    /** Share of the front's width either side of a lane's line that its inner satellite may stand in. */
    static final float INNER_HALF_ACROSS = 0.06f;
    /** A young bore stands at least this far from every other bore: nearer than a grown one, it holds less ground. */
    static final int YOUNG_SPACING = 26;
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
     * @param road     the supply road's cells; on Broad Front the satellite of the band nearest its far end stands
     *                 within {@link #ROAD_REACH} of it ({@link #roadSatellite}), else where its band puts it
     */
    public static HeartBores plan(NavigationGrid grid, CellTopology topology, Form form,
                                  int[] workings, int[] start, List<int[]> road, long seed) {
        return plan(grid, topology, form, workings, null, start, road, seed);
    }

    /**
     * As {@link #plan(NavigationGrid, CellTopology, Form, int[], int[], List, long)}, the first mother opening on the
     * open ground nearest {@code first}, the origin's core building (`heart-origins.md`), within
     * {@link #FIRST_REACH}; null, or no such ground, leaves her round the workings with the rest.
     */
    public static HeartBores plan(NavigationGrid grid, CellTopology topology, Form form,
                                  int[] workings, int[] first, int[] start, List<int[]> road, long seed) {
        boolean[] reachable = reachableFrom(grid, start);
        if (reachable == null) return null;
        SplittableRandom rng = new SplittableRandom(seed ^ SALT);
        List<Site> sites = new ArrayList<>();
        int mothers = 2 + rng.nextInt(2);
        for (int m = 1; m <= mothers; m++) {
            int[] nearest = m == 1 && first != null ? nearest(grid, topology, reachable, first, FIRST_REACH) : null;
            int[] cell = nearest != null ? nearest : pick(grid, topology, reachable, sites, rng, MOTHER_SPACING,
                    workings[0] - MOTHER_RADIUS, workings[1] - MOTHER_RADIUS,
                    workings[0] + MOTHER_RADIUS, workings[1] + MOTHER_RADIUS, workings);
            if (cell == null) return null;
            sites.add(new Site(m, true, cell[0], cell[1]));
        }
        float[][] bands = satelliteBands(form);
        int roadBand = form == Form.BROAD_FRONT ? roadBand(form, bands, road) : -1;
        int[] beside = roadBand < 0 ? null : roadSatellite(grid, topology, reachable, sites, rng, form, road);
        if (beside != null) sites.add(new Site(roadBand + 1, false, beside[0], beside[1]));
        for (int s = 0; s < bands.length; s++) {
            if (s == roadBand && beside != null) continue;
            float[] band = bands[s];
            int[] a = form.cellAt(band[0], band[2]), b = form.cellAt(band[1], band[3]);
            int[] cell = pick(grid, topology, reachable, sites, rng, SATELLITE_SPACING,
                    Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), null);
            if (cell == null) return null;
            sites.add(new Site(s + 1, false, cell[0], cell[1]));
        }
        // The inner row is drawn after every required site, so a map's mothers and outer row stand where they did, and
        // a site it cannot seat is left out rather than re-rolling the map.
        float[][] inner = innerBands(form);
        for (int s = 0; s < inner.length; s++) {
            float[] band = inner[s];
            int[] a = form.cellAt(band[0], band[2]), b = form.cellAt(band[1], band[3]);
            int[] cell = pick(grid, topology, reachable, sites, rng, SATELLITE_SPACING,
                    Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), null);
            if (cell != null) sites.add(new Site(bands.length + s + 1, false, cell[0], cell[1]));
        }
        // The young bores come last, so the mothers and satellites stand where they stood before them; one the
        // map has no ground for is left out rather than re-rolling the map.
        float[][] young = youngBands(form);
        int number = bands.length + inner.length;
        for (float[] band : young) {
            int[] a = form.cellAt(band[0], band[2]), b = form.cellAt(band[1], band[3]);
            int[] cell = pick(grid, topology, reachable, sites, rng, YOUNG_SPACING,
                    Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), null);
            if (cell != null) sites.add(new Site(++number, false, cell[0], cell[1], true));
        }
        sites.sort((p, q) -> p.mother != q.mother ? (p.mother ? -1 : 1) : Integer.compare(p.number, q.number));
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

    /**
     * Where Broad Front's <b>inner satellites</b> may stand, as {@link #satelliteBands} gives them: a second row between
     * the outer one and the heart, one on each allied lane's line (a sixth, a third, two thirds and five sixths across),
     * so every lane meets a second bore on its way in. Optional: a band whose site cannot be seated has none. The Burn
     * has no inner row.
     */
    static float[][] innerBands(Form form) {
        if (form != Form.BROAD_FRONT) return new float[0][];
        float[] lines = {1f / 6f, 1f / 3f, 2f / 3f, 5f / 6f};
        float[][] bands = new float[lines.length][];
        for (int i = 0; i < lines.length; i++) {
            bands[i] = new float[]{INNER_DEPTH_FROM, INNER_DEPTH_TO, lines[i] - INNER_HALF_ACROSS, lines[i] + INNER_HALF_ACROSS};
        }
        return bands;
    }

    /**
     * Where each young bore may stand, as {depth from, depth to, across from, across to}: on The Burn, the south
     * flank, the middle and the north flank of the first quarter past the near ride and of the second, so the route
     * is a clearing of bores from the first march on, then one more in the north half of the second quarter and one
     * in the north half of the last, short of the heart's own ground (owner, 2026-10-31). The later ones come last,
     * so the first six stand where they stood without them. None on Broad Front.
     */
    static float[][] youngBands(Form form) {
        if (form != Form.THE_BURN) return new float[0][];
        float[][] depths = {{0.32f, 0.48f}, {0.50f, 0.72f}};
        float[][] acrosses = {{0.08f, 0.32f}, {0.36f, 0.64f}, {0.68f, 0.92f}};
        float[][] north = {{0.50f, 0.72f, 0.52f, 0.92f}, {0.76f, 0.90f, 0.55f, 0.92f}};
        float[][] bands = new float[depths.length * acrosses.length + north.length][];
        int i = 0;
        for (float[] depth : depths) {
            for (float[] across : acrosses) bands[i++] = new float[]{depth[0], depth[1], across[0], across[1]};
        }
        for (float[] band : north) bands[i++] = band;
        return bands;
    }

    /** The band whose across is nearest the road's far end, whose satellite is the road's; -1 with no road. */
    static int roadBand(Form form, float[][] bands, List<int[]> road) {
        int[] end = farEnd(form, road);
        if (end == null) return -1;
        float across = form.across(end[0], end[1]);
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (int s = 0; s < bands.length; s++) {
            float gap = Math.abs((bands[s][2] + bands[s][3]) / 2f - across);
            if (gap < bestGap) { bestGap = gap; best = s; }
        }
        return best;
    }

    /** The road cell deepest into the approach: the settlement's near gate; null with no road. */
    static int[] farEnd(Form form, List<int[]> road) {
        int[] end = null;
        for (int[] cell : road) if (end == null || form.depth(cell[0], cell[1]) > form.depth(end[0], end[1])) end = cell;
        return end;
    }

    /**
     * A satellite site beside the road: within {@link #ROAD_REACH} of one of its cells but off it, past
     * {@link #ROAD_SATELLITE_DEPTH}; null when no such ground can be seated, and the band places it instead.
     */
    private static int[] roadSatellite(NavigationGrid grid, CellTopology topology, boolean[] reachable,
                                       List<Site> taken, SplittableRandom rng, Form form, List<int[]> road) {
        int[] end = farEnd(form, road);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] cell : road) {
            x0 = Math.min(x0, cell[0] - ROAD_REACH);
            y0 = Math.min(y0, cell[1] - ROAD_REACH);
            x1 = Math.max(x1, cell[0] + ROAD_REACH);
            y1 = Math.max(y1, cell[1] + ROAD_REACH);
        }
        if (end == null) return null;
        int width = grid.getWidth(), height = grid.getHeight();
        List<int[]> fit = new ArrayList<>();
        for (int y = Math.max(EDGE_MARGIN, y0); y <= Math.min(height - 1 - EDGE_MARGIN, y1); y++) {
            for (int x = Math.max(EDGE_MARGIN, x0); x <= Math.min(width - 1 - EDGE_MARGIN, x1); x++) {
                if (form.depth(x, y) < ROAD_SATELLITE_DEPTH || !reachable[y * width + x]) continue;
                if (!spaced(taken, x, y, SATELLITE_SPACING) || !besideRoad(road, x, y)) continue;
                if (clearGround(grid, topology, x, y)) fit.add(new int[]{x, y});
            }
        }
        return fit.isEmpty() ? null : fit.get(rng.nextInt(fit.size()));
    }

    /** Within {@link #ROAD_REACH} of a road cell, and clear of the road by more than a bore's mouth. */
    static boolean besideRoad(List<int[]> road, int x, int y) {
        float nearest = Float.MAX_VALUE;
        for (int[] cell : road) nearest = Math.min(nearest, (float) Math.hypot(cell[0] - x, cell[1] - y));
        return nearest <= ROAD_REACH && nearest > SwarmBores.MOUTH_RADIUS + 2;
    }

    /**
     * Opens every site and feeds it to its starting growth: a young bore is left a fissure, to raise its spire if it
     * grows to maw. Call at setup, tick zero.
     */
    public void open(BattleSimulation sim) {
        SwarmBores bores = sim.swarmBores();
        for (Site site : sites) {
            SwarmBore bore = bores.open(sim, site.number, site.mother, site.x, site.y);
            if (bore == null) throw new IllegalStateException("heart site " + site + " is not clear ground at setup");
            if (site.young) sim.broodSpires().raiseAtMaw(bore);
            else bores.feed(sim, bore, site.mother ? MOTHER_FEED : SATELLITE_FEED);
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
            if (spaced(taken, x, y, spacing)) return new int[]{x, y};
        }
        return null;
    }

    /** Cells from the origin's core building the first mother may open. */
    static final int FIRST_REACH = 16;

    /** The reachable open ground a bore can open on nearest {@code to}, within {@code reach}; null where none is. */
    static int[] nearest(NavigationGrid grid, CellTopology topology, boolean[] reachable, int[] to, int reach) {
        int width = grid.getWidth(), height = grid.getHeight();
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int y = Math.max(EDGE_MARGIN, to[1] - reach); y <= Math.min(height - 1 - EDGE_MARGIN, to[1] + reach); y++) {
            for (int x = Math.max(EDGE_MARGIN, to[0] - reach); x <= Math.min(width - 1 - EDGE_MARGIN, to[0] + reach); x++) {
                long d = (long) (x - to[0]) * (x - to[0]) + (long) (y - to[1]) * (y - to[1]);
                if (d > (long) reach * reach || d >= bestD) continue;
                if (!reachable[y * width + x] || !clearGround(grid, topology, x, y)) continue;
                best = new int[]{x, y};
                bestD = d;
            }
        }
        return best;
    }

    /** At least {@code spacing} from every site so far. */
    private static boolean spaced(List<Site> taken, int x, int y, int spacing) {
        for (Site site : taken) {
            int dx = site.x - x, dy = site.y - y;
            if (dx * dx + dy * dy < spacing * spacing) return false;
        }
        return true;
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
