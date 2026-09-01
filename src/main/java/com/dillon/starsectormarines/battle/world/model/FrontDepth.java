package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;

/**
 * How far a cell is from the thing the battle is about.
 *
 * <p>A front is a <b>depth</b>, not a biome. The defender reinforcement layer
 * has to answer "which ground is my rear, and which is nearly lost" every
 * second of a Conquest battle, and until now it asked a
 * {@link BiomeKind}: the beach was the marine end, the fortress district was
 * the defender end, and the two bands between them were the progression. That
 * works exactly once — on the stock crossroad recipe, whose biomes are painted
 * as percentile strips along a traversal axis and therefore <em>are</em> a
 * depth, by construction. A map made of places has no such strips. Its
 * garrison may sit in the north-west with a town wrapped round it and open
 * country on three sides, and there is no band anywhere on it that could be
 * called the port. Asking a biome map for the front on such a map returns
 * nothing at all, which is why the whole reinforcement layer silently
 * declined to install on one.
 *
 * <p>So the front is stated as what it always meant: a small number of
 * <b>bands</b>, band {@code 0} at the objective and rising outward toward the
 * attacker, each with a display name the comms officer can say out loud. Both
 * recipes build one, from whatever they happen to know — the stock recipe from
 * its biome strips, a precinct map from the objective's own claim — so the two
 * cannot disagree about what a front is. {@link BiomeKind} stays what it is
 * for theming and terrain; it stops being the front.
 *
 * <p>Tier-neutral data. It is derived once at generation and read; nothing
 * mutates it, and it knows nothing about squads, missions, or factions.
 */
public final class FrontDepth {

    /** How many bands both factories produce. Four is what the stock biome progression already had. */
    private static final int BANDS = 4;

    /** Rings the non-objective ground is cut into by {@link #fromObjective}. One fewer than {@link #BANDS}: band 0 is the claim itself. */
    private static final int RINGS = BANDS - 1;

    private final int width;
    private final int height;

    /** Band index per cell, {@code [x][y]}. Values in {@code [0, bands())}. */
    private final byte[][] cellBands;

    private final String[] names;

    /** Centroid of band 0, precomputed — {@link #rearward} runs on the reinforcement cadence and the answer never changes. */
    private final int centreX;
    private final int centreY;

    private FrontDepth(int width, int height, byte[][] cellBands, String[] names,
                       int centreX, int centreY) {
        this.width = width;
        this.height = height;
        this.cellBands = cellBands;
        this.names = names;
        this.centreX = centreX;
        this.centreY = centreY;
    }

    public int width()  { return width; }
    public int height() { return height; }

    /** How many bands this front has. Both factories say four. */
    public int bands() { return names.length; }

    /**
     * The band {@code (x, y)} falls in, clamped into the map the way
     * {@link BiomeMap#biomeAt} is — a caller shifting an anchor outward is
     * asking about the nearest real ground, not about an error.
     */
    public int bandAt(int x, int y) {
        int cx = Math.max(0, Math.min(width - 1, x));
        int cy = Math.max(0, Math.min(height - 1, y));
        return cellBands[cx][cy];
    }

    /** What the comms officer calls this band. */
    public String bandName(int band) {
        if (band < 0 || band >= names.length) {
            throw new IllegalArgumentException("no band " + band + " on a front of " + names.length);
        }
        return names[band];
    }

    /** Centroid of band 0 as {@code {x, y}} — the middle of the ground the battle is about. */
    public int[] objectiveCentre() {
        return new int[]{centreX, centreY};
    }

    /**
     * The cell {@code cells} steps from {@code (x, y)} toward
     * {@link #objectiveCentre}, clamped to the map.
     *
     * <p><b>Toward the objective, not along an axis.</b> The rally hint this
     * serves is "somewhere solidly behind the line", and the defender's rear is
     * wherever the thing they are defending is. Shifting along a traversal axis
     * says the same thing only on a map whose axis points at the objective,
     * which is a property of one recipe rather than of fronts; on a map with
     * the garrison in a corner it walks the rally off the map edge instead. The
     * objective is the fact both recipes have.
     *
     * <p>The step is taken on the <b>dominant axis</b> of the vector to the
     * centre — whichever of {@code |dx|}, {@code |dy|} is larger, ties toward
     * y — rather than diagonally, because the hint is a search seed for a
     * delivery means and a cardinal step keeps it on the kind of open ground a
     * road or a strand runs along. A point already at the centre does not move.
     */
    public int[] rearward(int x, int y, int cells) {
        int dx = centreX - x;
        int dy = centreY - y;
        int rx = x;
        int ry = y;
        if (Math.abs(dx) > Math.abs(dy)) {
            rx += Integer.signum(dx) * cells;
        } else {
            ry += Integer.signum(dy) * cells;
        }
        rx = Math.max(0, Math.min(width - 1, rx));
        ry = Math.max(0, Math.min(height - 1, ry));
        return new int[]{rx, ry};
    }

    /**
     * The stock crossroad recipe's front, read off its biome strips.
     *
     * <p>The mapping is the progression that was already there:
     * {@link BiomeKind#FORTRESS_DISTRICT} 0, {@link BiomeKind#CITY} 1,
     * {@link BiomeKind#PORT} 2, {@link BiomeKind#BEACH} 3. {@code OUTSKIRTS}
     * is the furthest band too — it is the sparse off-axis fallback kind and a
     * band layout never emits one, so folding it in costs nothing and keeps
     * both recipes at four bands, which is what lets one rear-to-front walk
     * serve both.
     *
     * <p>Names are the district names the comms officer was already using for
     * those biomes, minus their article: the presenter says "around the
     * {@code name}", so the article belongs to the sentence and not to the
     * band. Band 3 is called the beachhead rather than the outskirts because
     * on a real band layout that is what it is.
     */
    public static FrontDepth fromBiomes(BiomeMap biomes) {
        int w = biomes.width();
        int h = biomes.height();
        byte[][] cells = new byte[w][h];
        long sumX = 0;
        long sumY = 0;
        long count = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                byte band = bandOf(biomes.biomeAt(x, y));
                cells[x][y] = band;
                if (band == 0) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        int cx = count > 0 ? (int) (sumX / count) : w / 2;
        int cy = count > 0 ? (int) (sumY / count) : h / 2;
        return new FrontDepth(w, h, cells,
                new String[]{"fortress district", "city district", "port district", "beachhead"},
                cx, cy);
    }

    private static byte bandOf(BiomeKind biome) {
        switch (biome) {
            case FORTRESS_DISTRICT: return 0;
            case CITY:              return 1;
            case PORT:              return 2;
            case BEACH:             return 3;
            case OUTSKIRTS:         return 3;
        }
        throw new IllegalStateException("Unhandled biome " + biome);
    }

    /**
     * A precinct map's front, read off the objective precinct's claim.
     *
     * <p>Band 0 is exactly the claim — the place the mission is about, however
     * ragged the shape its growth left it. Everything else is banded by how far
     * it is from the nearest claim cell, cut into three equal-width rings out
     * to the map's furthest cell, so the count matches the stock recipe's four
     * and a map with the garrison in a corner still has an inside, a middle and
     * an outside rather than one enormous elsewhere.
     *
     * <p>Distance is <b>Chebyshev</b>, from a multi-source breadth-first sweep
     * over the eight-neighbourhood — a plain queue gives the exact
     * king's-move distance to the nearest source in one pass over the grid.
     * The alternative, measuring each cell against every claim cell, is
     * cells times claim: 188k by several thousand on a 560x336 Conquest map,
     * which is not a price a generator stage should pay for a banding. The
     * ring boundaries are the same shape either way; Chebyshev merely makes
     * them a little squarer than a true Euclidean sweep would, and a band
     * boundary is not a thing anybody measures.
     *
     * <p>Returns {@code null} when no cell carries {@code who} — a map whose
     * objective claimed nothing has no front, and a caller should bind nothing
     * rather than a front centred on an empty set.
     */
    public static FrontDepth fromObjective(int[][] claim, int who, int width, int height) {
        byte[][] cells = new byte[width][height];
        int[][] dist = new int[width][height];
        // Cells are queued packed as x * height + y: a frontier of a hundred
        // thousand two-element arrays is the sweep's whole allocation cost.
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        long sumX = 0;
        long sumY = 0;
        long count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] == who) {
                    dist[x][y] = 0;
                    queue[tail++] = x * height + y;
                    sumX += x;
                    sumY += y;
                    count++;
                } else {
                    dist[x][y] = -1;
                }
            }
        }
        if (count == 0) return null;

        int furthest = 0;
        while (head < tail) {
            int packed = queue[head++];
            int cx = packed / height;
            int cy = packed % height;
            int d = dist[cx][cy];
            if (d > furthest) furthest = d;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    if (dist[nx][ny] != -1) continue;
                    dist[nx][ny] = d + 1;
                    queue[tail++] = nx * height + ny;
                }
            }
        }

        int span = Math.max(1, furthest);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int d = dist[x][y];
                if (d <= 0) {
                    cells[x][y] = 0;
                    continue;
                }
                int ring = (int) ((long) (d - 1) * RINGS / span);
                if (ring >= RINGS) ring = RINGS - 1;
                cells[x][y] = (byte) (1 + ring);
            }
        }
        return new FrontDepth(width, height, cells,
                new String[]{"citadel", "inner districts", "outer districts", "approaches"},
                (int) (sumX / count), (int) (sumY / count));
    }
}
