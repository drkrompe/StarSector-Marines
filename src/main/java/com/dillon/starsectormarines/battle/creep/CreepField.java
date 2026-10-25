package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Hive creep: the per-cell biological growth living sources spread across a
 * battle map (`colony-breach-nouns.md` *Creep*).
 *
 * <p>Each source grows a reach along walkable ground at its {@link CreepGrowth}
 * rate, faster while it is fed, and claims every cell whose path cost from it
 * falls inside that reach. Path cost is weighted per cell by seeded noise (fine
 * grain plus a coarse lattice), so the front runs out in fingers rather than as
 * a circle, and pavement costs more than open ground. A claimed walkable cell
 * also claims the walls beside it: creep climbs a building's edge but does not
 * spread from the wall on, and a wall fills slowly ({@link #WALL_FILL_TICKS}),
 * so its coverage is how long the creep has held it: a renderer shows veins on
 * a young wall and a crust on an old one. Growth declared with {@link #growOver}
 * (a tree's crown) is not only climbed but crossed, dearer than ground, so a
 * grove is overgrown from its edge in. Nothing blocks creep except the walls
 * the ground itself respects.
 *
 * <p>Each cell carries three bytes: <em>coverage</em> (how much growth stands
 * there), <em>vitality</em> (living 255 down to dead 0) and <em>pulse</em> (its
 * path distance in cells from the source that reached it first, for a pulse
 * running toward the source). A claimed cell fills over a few seconds. When a
 * source is removed its cells that no living source also claims wither from the
 * outer edge back toward the hole, losing vitality first and then coverage, so
 * a collapse reads as a visible dying-back rather than a vanishing. A living
 * source reaching a withering cell revives it.
 *
 * <p>Owned by one simulation and ticked on its thread; deterministic in the
 * seed and the order sources were declared. A renderer reads the bytes and
 * patches the {@link #TILE}-cell tiles whose {@link #tileRevision} moved.
 */
public final class CreepField {

    /** Full coverage or vitality. */
    public static final int FULL = 255;
    /** Coverage and vitality at or above which a cell counts as creeped. */
    public static final int CREEPED = 128;
    /** Side of a change-tracking tile, in cells. */
    public static final int TILE = 32;

    /** Seconds per simulation tick; the field is ticked once per sim tick. */
    static final float TICK_SECONDS = 1f / 30f;
    /** Rate multiplier while a source is fed (owner pick, 2026-10-23: 1.5, was 3); {@code -Dbattle.creep.feedMultiplier} overrides it. */
    static final float FEED_MULTIPLIER = 1.5f;
    public static final String FEED_MULTIPLIER_PROPERTY = "battle.creep.feedMultiplier";
    /** Coverage a growing cell gains per tick (about three seconds to fill). */
    static final int FILL_PER_TICK = 3;
    /** Ticks per point of coverage on a climbed (unwalkable) cell: about a hundred seconds from first veins to full crust. */
    static final int WALL_FILL_TICKS = 12;
    /** Vitality a reviving cell regains per tick. */
    static final int REVIVE_PER_TICK = 8;
    /** Vitality a withering cell loses per tick (about three seconds to die). */
    static final int WITHER_PER_TICK = 3;
    /** Coverage a dead husk loses per tick (about eight seconds to clear). */
    static final int CLEAR_PER_TICK = 1;
    /** Ticks per cell of path distance the dying-back takes to run from the edge to the hole. */
    static final int RECEDE_TICKS_PER_CELL = 20;
    /** Seeded jitter, in ticks, on when each cell starts to wither. */
    static final int RECEDE_JITTER_TICKS = 24;
    /** Vitality and coverage a refused cell loses per tick: burning creep crisps and is gone in about two seconds. */
    static final int SCORCH_PER_TICK = 6;
    /** Cells a source settles per tick at most; bounds a large feed or a fresh maw. */
    static final int MAX_SETTLES_PER_TICK = 4000;
    /** Path cost of growing over a cell of natural growth, against the ground's. */
    static final int OVERGROW_COST = 3;
    /**
     * Multiplies every source's reach rate, fed or not: how fast the front walks
     * out, never how far it goes or how fast a claimed cell fills.
     * {@code -Dbattle.creep.spreadScale=1} restores the pre-2026-10-23 rate; the default, 0.5, is the owner's pick.
     */
    public static final String SPREAD_SCALE_PROPERTY = "battle.creep.spreadScale";
    /** The default of {@link #SPREAD_SCALE_PROPERTY}: half the presets' rate. */
    static final float SPREAD_SCALE = 0.5f;

    private static final int COST_SCALE = 10;
    private static final int RING = 64;
    private static final char UNVISITED = Character.MAX_VALUE;

    private static final byte NONE = 0, GROWING = 1, STEADY = 2, PENDING = 3, WITHERING = 4;

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final long seed;
    private final int width, height, cells;
    private final byte[] coverage, vitality, pulse, supporters, state;
    private final byte[] stepCost;
    private int[] active = new int[256];
    private int activeSize;
    private final ArrayList<WitherQueue> withers = new ArrayList<>();
    private final LinkedHashMap<Long, Source> sources = new LinkedHashMap<>();
    private final int tilesX, tilesY;
    private final long[] tileRevision;
    private volatile long revision;
    private boolean dirtyThisTick;
    /** Cells the creep grows over rather than climbs; null while none are declared. */
    private BitSet overgrown;
    private int now;
    private final float spreadScale = nonNegative(SPREAD_SCALE_PROPERTY, SPREAD_SCALE);
    private final float feedMultiplier = nonNegative(FEED_MULTIPLIER_PROPERTY, FEED_MULTIPLIER);
    /** Cells the creep is held off (`fire-nouns.md`, Firebreak); null while nothing refuses. */
    private IntPredicate refusal;

    public CreepField(NavigationGrid grid, CellTopology topology, long seed) {
        this.grid = grid;
        this.topology = topology;
        this.seed = seed;
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        this.cells = width * height;
        this.coverage = new byte[cells];
        this.vitality = new byte[cells];
        this.pulse = new byte[cells];
        this.supporters = new byte[cells];
        this.state = new byte[cells];
        this.stepCost = new byte[cells];
        this.tilesX = (width + TILE - 1) / TILE;
        this.tilesY = (height + TILE - 1) / TILE;
        this.tileRevision = new long[tilesX * tilesY];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) stepCost[y * width + x] = (byte) baseStepCost(x, y);
        }
    }

    // ---- Source API ----------------------------------------------------

    /**
     * Declares a source at a cell, or updates an existing one's growth (a bore
     * reaching a new tier). A source's position is fixed once declared; a
     * removed id cannot be declared again.
     */
    public void putSource(long id, int x, int y, CreepGrowth growth) {
        Source existing = sources.get(id);
        if (existing != null) {
            existing.growth = growth;
            return;
        }
        Source source = new Source(id, x, y, growth, rateVariance(id));
        sources.put(id, source);
        int start = nearestWalkable(x, y);
        if (start >= 0) source.push(start, 0);
    }

    /** Removes a source: what it alone held withers back toward its hole. No-op for an unknown id. */
    public void removeSource(long id) {
        Source source = sources.remove(id);
        if (source == null) return;
        int n = source.claimedSize;
        int[] queued = new int[n];
        int[] at = new int[n];
        int count = 0;
        int reachCells = source.farthestCost / COST_SCALE;
        for (int i = 0; i < n; i++) {
            int idx = source.claimed[i];
            int left = (supporters[idx] & 0xFF) - 1;
            supporters[idx] = (byte) left;
            if (left > 0) continue;
            int distCells = source.dist[idx] / COST_SCALE;
            int delay = Math.max(0, reachCells - distCells) * RECEDE_TICKS_PER_CELL
                    + (int) (hash01(idx, 0x5EEDL) * RECEDE_JITTER_TICKS);
            if (state[idx] == STEADY) state[idx] = PENDING;
            else if (state[idx] == GROWING) state[idx] = WITHERING;
            queued[count] = idx;
            at[count] = now + delay;
            count++;
        }
        if (count > 0) withers.add(new WitherQueue(queued, at, count));
    }

    /** Feeds a source: it spreads {@link #FEED_MULTIPLIER} times as fast for that many more seconds. */
    public void feed(long id, float seconds) {
        Source source = sources.get(id);
        if (source != null && seconds > 0f) source.boostSeconds += seconds;
    }

    // ---- Refusal ---------------------------------------------------------

    /**
     * Declares which cells the creep is held off: a refused cell's creep dies
     * and clears fast and does not grow back while the refusal stands, though
     * the sources that reach it keep their claim and fill it again once it ends
     * (`fire-nouns.md`, Firebreak). Asked only for cells already changing.
     */
    public void setRefusal(IntPredicate refusal) { this.refusal = refusal; }

    public IntPredicate refusal() { return refusal; }

    /**
     * Starts a cell's creep dying under the refusal now rather than waiting for
     * it to change: a fire caught on it. No-op on a cell with no creep.
     */
    public void scorch(int idx) {
        if (idx < 0 || idx >= cells) return;
        byte s = state[idx];
        if (s == STEADY) {
            state[idx] = GROWING;
            addActive(idx);
        } else if (s == PENDING) {
            state[idx] = WITHERING;
            addActive(idx);
        }
    }

    // ---- Tick ----------------------------------------------------------

    /** Advances the field to a simulation tick. */
    public void tick(int tick) {
        now = tick;
        for (Source source : sources.values()) grow(source);
        for (int q = withers.size() - 1; q >= 0; q--) {
            if (withers.get(q).release()) withers.remove(q);
        }
        stepActive();
        if (dirtyThisTick) {
            dirtyThisTick = false;
            revision = revision + 1;
        }
    }

    /**
     * Brings every source to its full reach at once and fills all it claims,
     * walls crusted over: creep that has stood for weeks
     * ({@code cordon-nouns.md}, The map). For setup, before the first tick;
     * what it grows is what the sources would have grown, only sooner.
     */
    public void mature() {
        for (Source source : sources.values()) {
            source.reach = Math.max(source.reach, source.growth.maxReachCells());
            int limit = (int) (source.reach * COST_SCALE);
            while (source.queued > 0) {
                int idx = source.popUpTo(limit);
                if (idx < 0) break;
                settle(source, idx, source.cur);
            }
        }
        int kept = 0;
        for (int i = 0; i < activeSize; i++) {
            int idx = active[i];
            if (state[idx] != GROWING) {
                active[kept++] = idx;
                continue;
            }
            coverage[idx] = (byte) FULL;
            vitality[idx] = (byte) FULL;
            state[idx] = STEADY;
            markDirty(idx);
        }
        activeSize = kept;
        if (dirtyThisTick) {
            dirtyThisTick = false;
            revision = revision + 1;
        }
    }

    private void grow(Source source) {
        float rate = source.growth.cellsPerSecond() * source.variance * spreadScale;
        if (source.boostSeconds > 0f) {
            rate *= feedMultiplier;
            source.boostSeconds = Math.max(0f, source.boostSeconds - TICK_SECONDS);
        }
        float max = source.growth.maxReachCells();
        source.reach = Math.min(Math.max(source.reach, 0f) + rate * TICK_SECONDS, Math.max(max, source.reach));
        int limit = (int) (source.reach * COST_SCALE);
        int settles = 0;
        while (source.queued > 0 && settles < MAX_SETTLES_PER_TICK) {
            int idx = source.popUpTo(limit);
            if (idx < 0) break;
            settles++;
            settle(source, idx, source.cur);
        }
    }

    private void settle(Source source, int idx, int cost) {
        source.markSettled(idx);
        source.farthestCost = Math.max(source.farthestCost, cost);
        claim(source, idx, cost);
        int x = idx % width, y = idx / width;
        boolean overgrowing = !grid.isWalkableAt(idx);
        for (Direction dir : Direction.CARDINALS) {
            int nx = x + dir.dx, ny = y + dir.dy;
            if (!grid.inBounds(nx, ny)) continue;
            int n = ny * width + nx;
            if (source.isSettled(n)) continue;
            if (grid.isWalkableAt(n)) {
                // Off a tree onto open ground again: the far side of a crown is reached over it.
                if (overgrowing) {
                    int next = cost + (stepCost[n] & 0xFF);
                    if (next < source.dist[n]) source.push(n, next);
                }
                continue;
            }
            if (!climbable(nx, ny)) continue;
            if (growsOver(nx, ny)) {
                int next = cost + (stepCost[n] & 0xFF) * OVERGROW_COST;
                if (next < source.dist[n]) source.push(n, next);
                continue;
            }
            if (overgrowing) continue;
            source.markSettled(n);
            claim(source, n, cost);
        }
        if (overgrowing) return;
        for (Direction dir : Direction.ALL) {
            if (!grid.canTraverseCellStep(x, y, dir)) continue;
            int n = (y + dir.dy) * width + (x + dir.dx);
            if (source.isSettled(n)) continue;
            int step = stepCost[n] & 0xFF;
            int next = cost + (dir.isDiagonal() ? (step * 14 + 5) / 10 : step);
            if (next < source.dist[n]) source.push(n, next);
        }
    }

    private void claim(Source source, int idx, int cost) {
        source.addClaimed(idx);
        int held = supporters[idx] & 0xFF;
        int distCells = Math.min(255, cost / COST_SCALE);
        if (held == 0 || distCells < (pulse[idx] & 0xFF)) pulse[idx] = (byte) distCells;
        supporters[idx] = (byte) Math.min(255, held + 1);
        byte s = state[idx];
        if (s == NONE || s == PENDING) {
            state[idx] = GROWING;
            addActive(idx);
        } else if (s == WITHERING) {
            state[idx] = GROWING;
        }
        markDirty(idx);
    }

    private void stepActive() {
        int kept = 0;
        for (int i = 0; i < activeSize; i++) {
            int idx = active[i];
            int cov = coverage[idx] & 0xFF, vit = vitality[idx] & 0xFF;
            if (refusal != null && refusal.test(idx)) {
                if (vit > 0 || cov > 0) {
                    vit = Math.max(0, vit - SCORCH_PER_TICK * 2);
                    if (vit == 0) cov = Math.max(0, cov - SCORCH_PER_TICK);
                    vitality[idx] = (byte) vit;
                    coverage[idx] = (byte) cov;
                    markDirty(idx);
                }
                // Claimed ground waits out the refusal to fill again; unclaimed ground is simply gone.
                if (cov == 0 && state[idx] != GROWING) {
                    state[idx] = NONE;
                    pulse[idx] = 0;
                    continue;
                }
                active[kept++] = idx;
                continue;
            }
            if (state[idx] == GROWING) {
                if (grid.isWalkableAt(idx)) cov = Math.min(FULL, cov + FILL_PER_TICK);
                else if (Math.floorMod(now + idx, WALL_FILL_TICKS) == 0) cov = Math.min(FULL, cov + 1);
                vit = Math.min(FULL, vit + REVIVE_PER_TICK);
                coverage[idx] = (byte) cov;
                vitality[idx] = (byte) vit;
                markDirty(idx);
                if (cov == FULL && vit == FULL) {
                    state[idx] = STEADY;
                    continue;
                }
            } else {
                if (vit > 0) vitality[idx] = (byte) Math.max(0, vit - WITHER_PER_TICK);
                else coverage[idx] = (byte) (cov = Math.max(0, cov - CLEAR_PER_TICK));
                markDirty(idx);
                if (cov == 0) {
                    state[idx] = NONE;
                    pulse[idx] = 0;
                    continue;
                }
            }
            active[kept++] = idx;
        }
        activeSize = kept;
    }

    private void addActive(int idx) {
        if (activeSize == active.length) active = Arrays.copyOf(active, active.length * 2);
        active[activeSize++] = idx;
    }

    private void markDirty(int idx) {
        int t = (idx / width / TILE) * tilesX + (idx % width) / TILE;
        tileRevision[t] = revision + 1;
        dirtyThisTick = true;
    }

    // ---- Reads ---------------------------------------------------------

    public int width() { return width; }
    public int height() { return height; }
    public int coverageAt(int idx) { return coverage[idx] & 0xFF; }
    public int vitalityAt(int idx) { return vitality[idx] & 0xFF; }
    public int pulseAt(int idx) { return pulse[idx] & 0xFF; }

    public int coverageAt(int x, int y) { return grid.inBounds(x, y) ? coverage[y * width + x] & 0xFF : 0; }

    /** Whether a living source claims the cell and is growing it: a burnt cell it waits on counts. */
    public boolean claimed(int idx) { return idx >= 0 && idx < cells && state[idx] == GROWING; }

    /** Living creep stands here: coverage and vitality both at least {@link #CREEPED}. */
    public boolean isCreeped(int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        int idx = y * width + x;
        return (coverage[idx] & 0xFF) >= CREEPED && (vitality[idx] & 0xFF) >= CREEPED;
    }

    /** Whether any cell holds creep or is still changing; false on a field nothing has spread. */
    public boolean isEmpty() { return sources.isEmpty() && activeSize == 0 && withers.isEmpty() && revision == 0; }

    /** Bumped at the end of every tick that changed any cell. */
    public long revision() { return revision; }

    /** The tick the field last advanced to. */
    public int now() { return now; }
    public int tilesX() { return tilesX; }
    public int tilesY() { return tilesY; }
    /** The {@link #revision} at which tile {@code (tx, ty)} last changed; may run one ahead mid-tick. */
    public long tileRevision(int tx, int ty) { return tileRevision[ty * tilesX + tx]; }

    /** Declared sources, living ones only, in declaration order. */
    public List<Long> sourceIds() { return new ArrayList<>(sources.keySet()); }

    /** Coverage per source and over the whole map, for the director snapshot and evidence. */
    public CreepSnapshot snapshot() {
        int covered = 0, dying = 0;
        for (int i = 0; i < cells; i++) {
            int cov = coverage[i] & 0xFF;
            if (cov < CREEPED) continue;
            if ((vitality[i] & 0xFF) >= CREEPED) covered++;
            else dying++;
        }
        List<CreepSnapshot.SourceView> views = new ArrayList<>(sources.size());
        for (Source s : sources.values()) {
            views.add(new CreepSnapshot.SourceView(s.id, s.x, s.y, s.reach, s.growth.maxReachCells(),
                    s.claimedSize, s.boostSeconds > 0f));
        }
        return new CreepSnapshot(now, covered, dying, views);
    }

    // ---- Ground ----------------------------------------------------------

    /** Seeded cost to step onto a cell, about {@link #COST_SCALE} on average; pavement costs half again. */
    private int baseStepCost(int x, int y) {
        double fine = hash01(y * width + x, 0xC4EE9L);
        double coarse = latticeNoise(x, y, 9);
        double factor = 0.3 + 0.3 * fine + 1.6 * coarse * coarse;
        if (topology != null && topology.inBounds(x, y) && topology.isStreet(x, y)) factor += 0.5;
        return (int) Math.max(3, Math.min(30, Math.round(factor * COST_SCALE)));
    }

    /**
     * Whether creep grows over a non-walkable cell rather than only climbing its edge: a living thing's growth
     * declared by {@link #growOver} (a tree's crown), which the creep crosses cell by cell at
     * {@link #OVERGROW_COST} times the ground's cost, so a grove is overgrown from its edge inward and its far side
     * is reached over it. Rock and hills are climbed as walls are.
     */
    private boolean growsOver(int x, int y) {
        return overgrown != null && overgrown.get(y * width + x) && grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID);
    }

    /** Declares a cell of growth the creep grows over rather than climbs (a tree's crown); before it spreads. */
    public void growOver(int idx) {
        if (idx < 0 || idx >= cells) return;
        if (overgrown == null) overgrown = new BitSet(cells);
        overgrown.set(idx);
    }

    /** A non-walkable cell creep may climb: walls, fixtures and growth, not water or open void. */
    private boolean climbable(int x, int y) {
        if (topology == null || topology.isWall(x, y)) return true;
        if (topology.isWater(x, y)) return false;
        return topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
    }

    private int nearestWalkable(int x, int y) {
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) return cy * width + cx;
                }
            }
        }
        return -1;
    }

    /** A rate switch, read when a field is made. */
    private static float nonNegative(String property, float fallback) {
        float value = Float.parseFloat(System.getProperty(property, Float.toString(fallback)));
        if (!(value >= 0f)) throw new IllegalArgumentException(property + " must be non-negative: " + value);
        return value;
    }

    private float rateVariance(long id) {
        return (float) (0.85 + 0.3 * hash01((int) (id ^ (id >>> 32)), 0xB04EL));
    }

    private double latticeNoise(int x, int y, int scale) {
        int gx = Math.floorDiv(x, scale), gy = Math.floorDiv(y, scale);
        double fx = (x - gx * scale) / (double) scale, fy = (y - gy * scale) / (double) scale;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = lattice(gx, gy), b = lattice(gx + 1, gy), c = lattice(gx, gy + 1), d = lattice(gx + 1, gy + 1);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private double lattice(int gx, int gy) { return hash01(gx * 73856093 ^ gy * 19349663, 0x1A77L); }

    private double hash01(int value, long salt) {
        long z = seed ^ (value * 0x9E3779B97F4A7C15L) ^ (salt * 0xBF58476D1CE4E5B9L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    // ---- Internals -----------------------------------------------------

    /** One declared source: its growth, its reach, and its own path-cost search out across the map. */
    private final class Source {
        final long id;
        final int x, y;
        final float variance;
        CreepGrowth growth;
        float reach;
        float boostSeconds;
        final char[] dist = new char[cells];
        final long[] settled = new long[(cells + 63) >>> 6];
        final int[][] ring = new int[RING][];
        final int[] ringHead = new int[RING], ringTail = new int[RING];
        int queued;
        int cur;
        int farthestCost;
        int[] claimed = new int[256];
        int claimedSize;

        Source(long id, int x, int y, CreepGrowth growth, float variance) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.growth = growth;
            this.variance = variance;
            Arrays.fill(dist, UNVISITED);
        }

        void push(int idx, int cost) {
            dist[idx] = (char) Math.min(cost, UNVISITED - 1);
            int b = cost & (RING - 1);
            int[] bucket = ring[b];
            if (bucket == null) bucket = ring[b] = new int[16];
            if (ringTail[b] == bucket.length) bucket = ring[b] = Arrays.copyOf(bucket, bucket.length * 2);
            bucket[ringTail[b]++] = idx;
            queued++;
        }

        /** Pops the next cell whose cost is within the limit, skipping stale entries; -1 when none. */
        int popUpTo(int limit) {
            while (queued > 0 && cur <= limit) {
                int b = cur & (RING - 1);
                if (ringHead[b] == ringTail[b]) {
                    ringHead[b] = ringTail[b] = 0;
                    cur++;
                    continue;
                }
                int idx = ring[b][ringHead[b]++];
                queued--;
                if (dist[idx] != cur || isSettled(idx)) continue;
                return idx;
            }
            return -1;
        }

        boolean isSettled(int idx) { return (settled[idx >>> 6] & (1L << idx)) != 0; }
        void markSettled(int idx) { settled[idx >>> 6] |= 1L << idx; }

        void addClaimed(int idx) {
            if (claimedSize == claimed.length) claimed = Arrays.copyOf(claimed, claimed.length * 2);
            claimed[claimedSize++] = idx;
        }
    }

    /** Cells a removed source left unsupported, released to wither in the order of their start ticks. */
    private final class WitherQueue {
        final int[] cellsByTick;
        final int[] atByTick;
        int cursor;

        WitherQueue(int[] queued, int[] at, int count) {
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> at[a] != at[b] ? Integer.compare(at[a], at[b]) : Integer.compare(queued[a], queued[b]));
            cellsByTick = new int[count];
            atByTick = new int[count];
            for (int i = 0; i < count; i++) {
                cellsByTick[i] = queued[order[i]];
                atByTick[i] = at[order[i]];
            }
        }

        /** Starts every cell now due; true once the queue is spent. */
        boolean release() {
            while (cursor < cellsByTick.length && atByTick[cursor] <= now) {
                int idx = cellsByTick[cursor++];
                if (supporters[idx] != 0) continue;
                if (state[idx] == PENDING) {
                    state[idx] = WITHERING;
                    addActive(idx);
                }
            }
            return cursor >= cellsByTick.length;
        }
    }
}
