package com.dillon.starsectormarines.battle.fire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Burning as simulation state the battle owns (`fire-nouns.md`, Fire field).
 *
 * <p>Per cell: the heat it has soaked up, whether it is unburnt, burning or
 * burnt, and while burning its fuel left and intensity. Heat is shared out by
 * burning cells every {@link #STEP_TICKS} ticks to the rings of cells round
 * them their fuel reaches, weighted by ring, wind and a seeded jitter; a cell
 * whose heat passes the ignition of the fuel standing on it catches. Heat a
 * cell does not use cools away. A cell that has spent its fuel is burnt: it
 * never burns again, except for creep that has grown back over it once its
 * refusal is over.
 *
 * <p>Deterministic in the seed: every random choice is a hash of the seed, the
 * cell and the step, and heat is summed in integers before any ignition is
 * decided, so the order burning cells are visited in cannot change a result.
 * Costs scale with cells burning or warm, never with the map.
 *
 * <p>{@code -Dbattle.fire=false} is the control: nothing ever catches.
 */
public final class FireField {

    /** Control-run switch; fire burns unless it reads {@code false}. */
    public static final String PROPERTY = "battle.fire";

    /** Simulation ticks per fire step (5 Hz at 30 ticks a second). */
    public static final int STEP_TICKS = 6;
    /** Seconds per fire step. */
    public static final float STEP_SECONDS = STEP_TICKS / 30f;
    /** Ticks a burnt cell refuses creep after it burns out: three minutes. */
    public static final int CREEP_REFUSAL_TICKS = 30 * 180;
    /**
     * Steps a caught cell takes to reach its peak: six seconds. It sets the front's pace, well under a marine's walk,
     * without changing peak heat or reach, so breaks hold.
     */
    static final int RAMP_STEPS = 30;
    /** The share of a cell's load left when its fire starts to die down. */
    static final int TAIL_DIVISOR = 4;
    /** Heat a cell keeps each step, in 1/8: the rest cools away. */
    static final int KEEP_EIGHTHS = 7;
    /** Heat given to a ring, in 1/256 of the burning cell's intensity: ring 1, 2, 3, 4. */
    static final int[] RING_WEIGHT = {0, 64, 24, 16, 8};
    /**
     * Temporary A/B switch: a multiplier on every limited fuel's {@link FireFuel#spread}, and {@code 0} for the
     * old unbounded spread. Remove it with its losing arm once the owner picks how far a grass fire runs
     * (`fire-nouns.md`, Spread), folding the pick into {@link FireFuel}.
     */
    public static final String SPREAD_SCALE_PROPERTY = "battle.fire.spreadScale";
    /** How many times its spread a fire carries in dry country ({@link #setTinder}). */
    static final int TINDER_SPREAD = 3;
    /** Wind strength at or above which a fire reaches a ring further downwind. */
    static final float STRONG_WIND = 0.6f;
    /**
     * Distances from a fire's ignition are counted in quarter cells, straight-line (Euclidean) per hop, so a fire's
     * carry limit is a disc round where it was lit rather than a box.
     */
    static final int QUARTERS = 4;
    /** A distance past any fuel's carry: what a cell of unlimited fuel never reaches. */
    private static final int FAR = Short.MAX_VALUE / 2;
    /**
     * How much a cell's own carry limit and ignition vary, seeded per cell: its carry is 65% to 115% of its fuel's,
     * its ignition 85% to 120%, half from the cell and half from the three-cell block round it, so a burn's edge is
     * ragged and clumped rather than ruled.
     */
    static final float CARRY_LOW = 0.65f, CARRY_SPAN = 0.5f, CATCH_LOW = 0.85f, CATCH_SPAN = 0.35f;
    /** Cells round a burning cell, in a square, that its {@link #massAt mass} counts. */
    static final int MASS_RADIUS = 3;
    /** The most a cell's mass can be: every other cell of its square burning. */
    public static final int MASS_MAX = (2 * MASS_RADIUS + 1) * (2 * MASS_RADIUS + 1) - 1;
    /** Side of a change-tracking tile, in cells. */
    public static final int TILE = 32;

    public static final byte UNBURNT = 0, BURNING = 1, BURNT = 2;

    /** What happens to the world as cells catch and burn out; the battle wires it to creep, trees and bodies. */
    public interface Listener {
        /** A cell caught, with the fuel standing on it. */
        default void ignited(int idx, int fuelMask) { }
        /** A cell spent its fuel. */
        default void burntOut(int idx, int fuelMask) { }
    }

    private final int width, height, cells;
    private final long seed;
    private final FireTerrain terrain;
    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private final float spreadScale = spreadScale(System.getProperty(SPREAD_SCALE_PROPERTY));
    private Listener listener = new Listener() { };

    private final int[] heat;
    private final byte[] state;
    private final short[] fuel, load;
    private final byte[] peak, age, intensity, mask, reach;
    /** Per burning cell, as of the last step: how many cells round it are burning too ({@link #massAt}). */
    private final byte[] mass;
    /**
     * A burning cell's distance, in quarter cells ({@link #QUARTERS}), from where its fire was lit ({@code 0} where it was lit and on fuel of
     * {@link FireFuel#UNLIMITED} spread); for a warm cell, the nearest of the fires heating it, plus the way.
     */
    private final short[] distance, heatDistance;
    private final int[] burntTick;
    /** Cells that stop heat passing over them, as of {@link #barrierRevision}; see {@link FireTerrain#blocksHeat}. */
    private final boolean[] barrier;
    private long barrierRevision = Long.MIN_VALUE;
    private boolean anyBarrier;
    /** Cells holding heat, unburning; flagged in {@link #warm}. */
    private int[] warmList = new int[256];
    private int warmSize;
    private final boolean[] warm;
    private int[] burning = new int[256];
    private int burningSize;
    private final List<Igniter> igniters = new ArrayList<>();

    private float windX, windY, windStrength;
    /** Offsets and weights a burning cell heats, per reach 1..4 (4 only downwind in a strong wind). */
    private int[][] offsets = new int[5][];
    private int[][] weights = new int[5][];
    /** Per reach, each offset's straight-line length in quarter cells. */
    private int[][] hops = new int[5][];

    private final int tilesX, tilesY;
    private final long[] tileRevision;
    private volatile long revision;
    private boolean dirtyThisStep;
    private int now;
    private int stepIndex;
    private long everBurnt;

    public FireField(int width, int height, long seed, FireTerrain terrain) {
        this.width = width;
        this.height = height;
        this.cells = width * height;
        this.seed = seed;
        this.terrain = terrain;
        this.heat = new int[cells];
        this.state = new byte[cells];
        this.fuel = new short[cells];
        this.load = new short[cells];
        this.peak = new byte[cells];
        this.age = new byte[cells];
        this.intensity = new byte[cells];
        this.mask = new byte[cells];
        this.reach = new byte[cells];
        this.mass = new byte[cells];
        this.distance = new short[cells];
        this.heatDistance = new short[cells];
        this.burntTick = new int[cells];
        this.barrier = new boolean[cells];
        this.warm = new boolean[cells];
        this.tilesX = (width + TILE - 1) / TILE;
        this.tilesY = (height + TILE - 1) / TILE;
        this.tileRevision = new long[tilesX * tilesY];
        setWind(0f, 0f, 0f);
    }

    private boolean tinder;

    /**
     * Declares the battle's country dry: ordinary heavy blasts and burning
     * wrecks light what they land on ({@link FireIgnition}). Off unless a mission says so.
     */
    public void setTinder(boolean tinder) { this.tinder = tinder; }

    public boolean tinder() { return tinder; }

    /** Unit vector the wind blows toward, and its strength 0..1. */
    public float windX() { return windX; }

    public float windY() { return windY; }

    public float windStrength() { return windStrength; }

    public void setListener(Listener listener) { this.listener = listener == null ? new Listener() { } : listener; }

    /**
     * Sets the battle's wind: the direction it blows toward and a strength
     * 0..1. Downwind cells get up to twice the heat, upwind ones as little as
     * none; at {@link #STRONG_WIND} or more a fire reaches a ring further downwind.
     */
    public void setWind(float towardX, float towardY, float strength) {
        float len = (float) Math.hypot(towardX, towardY);
        windStrength = len == 0f ? 0f : Math.max(0f, Math.min(1f, strength));
        windX = len == 0f ? 0f : towardX / len;
        windY = len == 0f ? 0f : towardY / len;
        for (int r = 1; r <= 4; r++) buildRing(r);
    }

    private void buildRing(int maxReach) {
        List<int[]> out = new ArrayList<>();
        int span = maxReach;
        for (int dy = -span; dy <= span; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                if (dx == 0 && dy == 0) continue;
                double d = Math.hypot(dx, dy);
                int ring = (int) Math.round(d);
                if (ring < 1 || ring > maxReach) continue;
                double cos = (dx * windX + dy * windY) / d;
                if (maxReach == 4 && ring == 4 && (windStrength < STRONG_WIND || cos < 0.7)) continue;
                double factor = 1.0 + windStrength * cos;
                int w = (int) Math.round(RING_WEIGHT[ring] * factor);
                if (w <= 0) continue;
                out.add(new int[]{dx, dy, w, (int) Math.round(QUARTERS * d)});
            }
        }
        int[] off = new int[out.size() * 2];
        int[] wt = new int[out.size()];
        int[] hop = new int[out.size()];
        for (int i = 0; i < out.size(); i++) {
            hop[i] = out.get(i)[3];
            off[i * 2] = out.get(i)[0];
            off[i * 2 + 1] = out.get(i)[1];
            wt[i] = out.get(i)[2];
        }
        offsets[maxReach] = off;
        weights[maxReach] = wt;
        hops[maxReach] = hop;
    }

    // ---- Igniting ------------------------------------------------------

    /** Puts heat into one cell: an incendiary's touch, a shell's blast on it. */
    public void addHeat(int x, int y, int amount) {
        if (!enabled || amount <= 0 || x < 0 || y < 0 || x >= width || y >= height) return;
        warmUp(y * width + x, amount, 0);
    }

    /** Puts heat into every cell whose centre lies within a radius, full at the centre and half at the edge. */
    public void addHeat(float cx, float cy, float radius, int amount) {
        if (!enabled || amount <= 0) return;
        int x0 = Math.max(0, (int) Math.floor(cx - radius)), x1 = Math.min(width - 1, (int) Math.floor(cx + radius));
        int y0 = Math.max(0, (int) Math.floor(cy - radius)), y1 = Math.min(height - 1, (int) Math.floor(cy + radius));
        float r2 = radius * radius;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x + .5f - cx, dy = y + .5f - cy;
                float d2 = dx * dx + dy * dy;
                if (d2 > r2) continue;
                float falloff = r2 == 0f ? 1f : 1f - 0.5f * d2 / r2;
                warmUp(y * width + x, Math.round(amount * falloff), 0);
            }
        }
    }

    /**
     * A standing igniter: a burning wreck, which puts heat into its own cell and
     * the ring round it every step until a tick. Replaces one with the same id.
     */
    public void putIgniter(long id, int x, int y, int heatPerStep, int untilTick) {
        if (!enabled) return;
        igniters.removeIf(i -> i.id == id);
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        igniters.add(new Igniter(id, y * width + x, heatPerStep, untilTick));
    }

    /** Heat into a cell from a fire {@code from} quarter cells from where it was lit; an igniter's own heat is {@code 0}. */
    private void warmUp(int idx, int amount, int from) {
        byte s = state[idx];
        if (s == BURNING) return;
        if (s == BURNT && !refusalOver(idx)) return;
        heat[idx] = (int) Math.min(Integer.MAX_VALUE / 2, (long) heat[idx] + amount);
        int d = Math.min(FAR, from);
        if (!warm[idx] || d < heatDistance[idx]) heatDistance[idx] = (short) d;
        if (!warm[idx]) {
            warm[idx] = true;
            if (warmSize == warmList.length) warmList = Arrays.copyOf(warmList, warmSize * 2);
            warmList[warmSize++] = idx;
        }
    }

    // ---- Tick ----------------------------------------------------------

    /** Advances the field to a simulation tick; steps on every {@link #STEP_TICKS}th. */
    public void tick(int tick) {
        now = tick;
        if (Math.floorMod(tick, STEP_TICKS) != 0) return;
        if (burningSize == 0 && warmSize == 0 && igniters.isEmpty()) return;
        step();
    }

    private void step() {
        stepIndex++;
        refreshBarriers();
        for (int i = igniters.size() - 1; i >= 0; i--) {
            Igniter ig = igniters.get(i);
            if (now >= ig.untilTick) { igniters.remove(i); continue; }
            emit(ig.idx, ig.heat, 1, false, 0);
            warmUp(ig.idx, ig.heat, 0);
        }
        // Heat goes out from every burning cell before anything is decided.
        for (int i = 0; i < burningSize; i++) {
            int idx = burning[i];
            emit(idx, intensity[idx] & 0xFF, reach[idx], true, distance[idx]);
        }
        burnDown();
        coolAndCatch();
        weighMass();
        if (dirtyThisStep) {
            dirtyThisStep = false;
            revision = revision + 1;
        }
    }

    /** Counts, for every burning cell, the burning cells in the square round it: a small fire's or a big one's. */
    private void weighMass() {
        for (int i = 0; i < burningSize; i++) {
            int idx = burning[i];
            int x = idx % width, y = idx / width, n = 0;
            for (int ny = Math.max(0, y - MASS_RADIUS); ny <= Math.min(height - 1, y + MASS_RADIUS); ny++) {
                int row = ny * width;
                for (int nx = Math.max(0, x - MASS_RADIUS); nx <= Math.min(width - 1, x + MASS_RADIUS); nx++) {
                    if (state[row + nx] == BURNING) n++;
                }
            }
            mass[idx] = (byte) (n - 1);
        }
    }

    private void emit(int idx, int power, int maxReach, boolean jitter, int from) {
        if (power <= 0) return;
        int r = Math.max(1, Math.min(3, maxReach));
        if (r == 3 && windStrength >= STRONG_WIND) r = 4;
        int[] off = offsets[r], wt = weights[r], hop = hops[r];
        int x = idx % width, y = idx / width;
        for (int k = 0; k < wt.length; k++) {
            int dx = off[k * 2], dy = off[k * 2 + 1];
            int nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
            int n = ny * width + nx;
            if (anyBarrier && shielded(x, y, dx, dy)) continue;
            int amount = power * wt[k];
            if (jitter) amount = (int) ((long) amount * (128 + jitter(n)) >> 8);
            amount >>= 8;
            if (amount > 0) warmUp(n, amount, from + hop[k]);
        }
    }

    /**
     * Whether a standing wall that is not itself burning lies between a cell and
     * one {@code (dx, dy)} from it: heat does not pass over walls. Adjacent cells
     * have nothing between them.
     */
    private boolean shielded(int x, int y, int dx, int dy) {
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        for (int t = 1; t < steps; t++) {
            int cx = x + Math.round(dx * t / (float) steps), cy = y + Math.round(dy * t / (float) steps);
            int c = cy * width + cx;
            if (barrier[c] && state[c] != BURNING) return true;
        }
        return false;
    }

    /**
     * Rereads which cells stop heat when the terrain says its walls have changed
     * (a breach, a wall burnt through): a whole-map pass, on those steps only.
     */
    private void refreshBarriers() {
        long rev = terrain.barrierRevision();
        if (rev == barrierRevision) return;
        barrierRevision = rev;
        boolean any = false;
        for (int i = 0; i < cells; i++) {
            boolean b = terrain.blocksHeat(i);
            barrier[i] = b;
            any |= b;
        }
        anyBarrier = any;
    }

    /** 0..255, a hash of the cell and the step: the ragged edge of a front. */
    private int jitter(int idx) {
        long z = seed ^ (idx * 0x9E3779B97F4A7C15L) ^ (stepIndex * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return (int) ((z ^ (z >>> 31)) & 0xFF);
    }

    private void burnDown() {
        int kept = 0;
        for (int i = 0; i < burningSize; i++) {
            int idx = burning[i];
            int left = fuel[idx] - 1;
            int a = Math.min(255, (age[idx] & 0xFF) + 1);
            age[idx] = (byte) a;
            if (left <= 0) {
                fuel[idx] = 0;
                intensity[idx] = 0;
                state[idx] = BURNT;
                burntTick[idx] = now;
                everBurnt++;
                markDirty(idx);
                listener.burntOut(idx, mask[idx] & 0xFF);
                continue;
            }
            fuel[idx] = (short) left;
            int before = intensity[idx] & 0xFF;
            int after = intensityFor(peak[idx] & 0xFF, load[idx], left, a);
            if (after != before) {
                intensity[idx] = (byte) after;
                markDirty(idx);
            }
            burning[kept++] = idx;
        }
        burningSize = kept;
    }

    static int intensityFor(int peak, int load, int left, int ageSteps) {
        int ramp = Math.min(RAMP_STEPS, ageSteps + 1);
        int value = peak * ramp / RAMP_STEPS;
        int tail = Math.max(1, load / TAIL_DIVISOR);
        if (left < tail) value = Math.min(value, peak * left / tail);
        return Math.max(1, Math.min(255, value));
    }

    private void coolAndCatch() {
        int kept = 0;
        for (int i = 0; i < warmSize; i++) {
            int idx = warmList[i];
            byte s = state[idx];
            if (s == BURNING) { heat[idx] = 0; warm[idx] = false; continue; }
            int h = heat[idx];
            if (h >= FireFuel.MIN_IGNITION) {
                int m = terrain.fuelMask(idx);
                if (s == BURNT) m &= FireFuel.CREEP.bit | FireFuel.DEAD_CREEP.bit;
                if (m != 0 && h >= ignitionAt(idx, m) && heatDistance[idx] <= spreadLimitAt(idx, m)) {
                    catchFire(idx, m);
                    heat[idx] = 0;
                    warm[idx] = false;
                    continue;
                }
            }
            h = h * KEEP_EIGHTHS >> 3;
            if (h < 8) { heat[idx] = 0; warm[idx] = false; continue; }
            heat[idx] = h;
            warmList[kept++] = idx;
        }
        warmSize = kept;
    }

    private void catchFire(int idx, int m) {
        int l = Math.max(1, FireFuel.load(m));
        state[idx] = BURNING;
        mask[idx] = (byte) m;
        load[idx] = (short) l;
        fuel[idx] = (short) l;
        peak[idx] = (byte) FireFuel.peak(m);
        reach[idx] = (byte) FireFuel.reach(m);
        distance[idx] = spreadLimitAt(idx, m) == Integer.MAX_VALUE ? 0 : heatDistance[idx];
        age[idx] = 0;
        intensity[idx] = (byte) intensityFor(FireFuel.peak(m), l, l, 0);
        if (burningSize == burning.length) burning = Arrays.copyOf(burning, burningSize * 2);
        burning[burningSize++] = idx;
        markDirty(idx);
        listener.ignited(idx, m);
    }

    /**
     * How far, in quarter cells, from its fire's ignition this cell may be and still catch with this fuel standing on
     * it: its fuel's carry ({@link #spreadLimit}) times the cell's own seeded share; {@link Integer#MAX_VALUE} for fuel
     * that carries a fire as far as it stands.
     */
    int spreadLimitAt(int idx, int m) {
        int cells = spreadLimit(m);
        if (cells >= FireFuel.UNLIMITED) return Integer.MAX_VALUE;
        return Math.round(cells * QUARTERS * (CARRY_LOW + CARRY_SPAN * texture(idx, 0x5EED_C0FFEEL)));
    }

    /** The heat this cell needs to catch with this fuel: the fuel's ignition times the cell's own seeded share. */
    int ignitionAt(int idx, int m) {
        return Math.round(FireFuel.ignition(m) * (CATCH_LOW + CATCH_SPAN * texture(idx, 0x1617_7105L)));
    }

    /** 0..1, fixed per cell for the battle: half the cell's own hash, half its three-cell block's. */
    private float texture(int idx, long salt) {
        int x = idx % width, y = idx / width;
        int block = (y / 3) * ((width + 2) / 3) + x / 3;
        return (hash01(idx, salt) + hash01(block, salt ^ 0xB10CL)) * 0.5f;
    }

    private float hash01(int key, long salt) {
        long z = seed ^ salt ^ (key * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return ((z ^ (z >>> 31)) & 0xFFFF) / 65535f;
    }

    /** How far from its ignition a fire may be and still catch a cell of this fuel, in whole cells, before a cell's own share. */
    int spreadLimit(int m) {
        int spread = FireFuel.spread(m);
        if (spread >= FireFuel.UNLIMITED || spreadScale <= 0f) return FireFuel.UNLIMITED;
        float limit = spread * spreadScale * (tinder ? TINDER_SPREAD : 1);
        return Math.min(FireFuel.UNLIMITED, Math.round(limit));
    }

    static float spreadScale(String value) {
        if (value == null || value.isBlank()) return 1f;
        try {
            return Math.max(0f, Float.parseFloat(value.trim()));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    /** A burning cell's distance from where its fire was lit, in quarter cells ({@link #QUARTERS}); see {@link FireFuel#spread}. */
    public int distanceAt(int idx) { return distance[idx]; }

    private boolean refusalOver(int idx) { return now - burntTick[idx] >= CREEP_REFUSAL_TICKS; }

    private void markDirty(int idx) {
        int t = (idx / width / TILE) * tilesX + (idx % width) / TILE;
        tileRevision[t] = revision + 1;
        dirtyThisStep = true;
    }

    // ---- Reads ---------------------------------------------------------

    public int width() { return width; }
    public int height() { return height; }
    public boolean enabled() { return enabled; }

    public byte stateAt(int idx) { return state[idx]; }
    public boolean isBurning(int x, int y) { return inBounds(x, y) && state[y * width + x] == BURNING; }
    public boolean isBurnt(int x, int y) { return inBounds(x, y) && state[y * width + x] == BURNT; }

    /** The fuel standing on a cell, burnt or not; {@code 0} off the field. */
    int fuelMaskAt(int x, int y) { return inBounds(x, y) ? terrain.fuelMask(y * width + x) : 0; }

    /** Whether a cell could still catch: unburnt, with fuel standing on it. */
    public boolean canBurn(int x, int y) { return inBounds(x, y) && state[y * width + x] == UNBURNT && terrain.fuelMask(y * width + x) != 0; }
    /** 0..255 while burning, else 0. */
    public int intensityAt(int idx) { return intensity[idx] & 0xFF; }
    /**
     * 0..{@link #MASS_MAX} while burning: how many of the cells within {@link #MASS_RADIUS} burn with it, as of the
     * last step. A flamer's patch is a few; the heart of a big fire is most of them.
     */
    public int massAt(int idx) { return state[idx] == BURNING ? mass[idx] : 0; }
    public int intensityAt(int x, int y) { return inBounds(x, y) ? intensity[y * width + x] & 0xFF : 0; }
    /** The fuel mask a burning or burnt cell caught with; 0 for an unburnt one. */
    public int burnedMaskAt(int idx) { return mask[idx] & 0xFF; }

    /** How far a cell has burnt, 0..255: 0 unburnt, the share of its fuel spent while it burns, 255 once burnt out. */
    public int burnProgress(int idx) {
        byte st = state[idx];
        if (st == BURNT) return 255;
        if (st != BURNING || load[idx] <= 0) return 0;
        return Math.max(1, 255 - 255 * fuel[idx] / load[idx]);
    }
    /** Ticks since a burnt cell burnt out; -1 if it is not burnt. */
    public int burntAge(int idx) { return state[idx] == BURNT ? now - burntTick[idx] : -1; }

    /** Whether creep here is held at nothing: the cell is burning, or burnt and still refusing. */
    public boolean holdsCreepBack(int idx) { return state[idx] == BURNING || refusesCreep(idx); }

    /** Whether creep may not claim this cell: it burnt out less than {@link #CREEP_REFUSAL_TICKS} ago. */
    public boolean refusesCreep(int idx) { return state[idx] == BURNT && !refusalOver(idx); }

    /** The heat a cell holds now, for evidence. */
    public int heatAt(int idx) { return heat[idx]; }

    public int burningCount() { return burningSize; }
    /** Burning cell indices, in no promised order; valid until the next tick. */
    public int[] burningCells() { return Arrays.copyOf(burning, burningSize); }
    /** The raw burning list and its size, for per-frame readers that must not allocate. */
    public int burningCell(int i) { return burning[i]; }
    /** Cells that have ever burnt out. */
    public long burntCount() { return everBurnt; }
    /** True while anything burns, warms, or ignites; false on a field nothing has lit. */
    public boolean isActive() { return burningSize > 0 || warmSize > 0 || !igniters.isEmpty(); }

    /** Bumped at the end of every fire step that changed any cell. */
    public long revision() { return revision; }
    public int tilesX() { return tilesX; }
    public int tilesY() { return tilesY; }
    public long tileRevision(int tx, int ty) { return tileRevision[ty * tilesX + tx]; }

    private boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    private record Igniter(long id, int idx, int heat, int untilTick) { }
}
