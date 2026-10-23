package com.dillon.starsectormarines.battle.nav;

import java.util.Locale;

/**
 * The battle's raised ground: which tier every cell stands on, which cells are
 * cliff faces, and which are ramps. Fixed when the map is generated and never
 * changed in a battle, so every copy of the grid shares the one instance and
 * nothing derived from it needs invalidating. See {@code elevation-nouns.md}.
 *
 * <p>Movement needs nothing from this class: a face is an unwalkable natural
 * solid cell and every shared edge between walkable cells of different tiers
 * outside a ramp is closed, so every route authority already refuses a cliff.
 * What this class answers is the asymmetric part, sight and direct fire: who
 * stands higher than whom, and which high cells are the rim a body on lower
 * ground can still see.
 *
 * <p>Height for sight is a walkable cell's tier, a face cell's upper tier and a
 * ramp cell's lower tier — a unit on a ramp sees as the low ground does until it
 * reaches the head.
 */
public final class ElevationMap {

    /** Tiers above the base the model supports. */
    public static final int MAX_TIER = 3;

    /**
     * Metres one tier stands above the one below: a storey. The relief composite raises its
     * ground by it, and a gun on a plinth of whole tiers fires from that high.
     */
    public static final float TIER_RISE_METERS = 4f;

    /** Ordinary ground or plateau top: walkable, on {@link #tier}. */
    public static final byte GROUND = 0;
    /** Cliff face: unwalkable, standing at the tier it holds up. */
    public static final byte FACE = 1;
    /** Ramp: walkable, between its lower tier and the one above. */
    public static final byte RAMP = 2;

    /**
     * How elevation limits sight upward. A switch, so a control run is one
     * property away ({@code -Dbattle.elevation.sight=flat}).
     */
    public enum SightRule {
        /** High ground sees down; low ground sees the face and the rim above it, nothing further back. */
        SKYLINE,
        /** High ground sees down; low ground sees the face and nothing on top (StarCraft). */
        STRICT,
        /** Elevation changes nothing about sight; faces do not block it. */
        FLAT;

        public static SightRule configured() {
            String value = System.getProperty("battle.elevation.sight", "skyline");
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "strict" -> STRICT;
                case "flat" -> FLAT;
                default -> SKYLINE;
            };
        }
    }

    private final int width;
    private final int height;
    private final byte[] sightHeight;
    private final byte[] kind;
    private final byte[] upperTier;
    private final boolean[] rim;
    /** How far up its ramp a ramp cell stands, 0 (foot) to 255 (head); for the look only. */
    private final byte[] rampRise;
    private final int highestTier;
    private final SightRule rule;

    private ElevationMap(int width, int height, byte[] sightHeight, byte[] kind,
                         byte[] upperTier, boolean[] rim, byte[] rampRise, int highestTier, SightRule rule) {
        this.width = width;
        this.height = height;
        this.sightHeight = sightHeight;
        this.kind = kind;
        this.upperTier = upperTier;
        this.rim = rim;
        this.rampRise = rampRise;
        this.highestTier = highestTier;
        this.rule = rule;
    }

    /**
     * Builds the map from per-cell tiers and kinds. {@code tier} is the cell's
     * own tier for ground, the tier it holds up for a face, and the lower tier
     * for a ramp.
     */
    public static ElevationMap of(int width, int height, byte[] tier, byte[] kind, SightRule rule) {
        return of(width, height, tier, kind, null, rule);
    }

    /**
     * As {@link #of(int, int, byte[], byte[], SightRule)}, with how far up its
     * ramp each ramp cell stands (0 at the foot, 1 at the head), so the ground
     * draws a ramp as a slope. Null stands every ramp cell halfway up.
     */
    public static ElevationMap of(int width, int height, byte[] tier, byte[] kind, double[] rampRise,
                                  SightRule rule) {
        int cells = width * height;
        if (tier.length != cells || kind.length != cells) {
            throw new IllegalArgumentException("Elevation arrays do not match the map");
        }
        byte[] heights = tier.clone();
        byte[] kinds = kind.clone();
        byte[] upper = new byte[cells];
        int highest = 0;
        for (int i = 0; i < cells; i++) {
            if (heights[i] < 0 || heights[i] > MAX_TIER) {
                throw new IllegalArgumentException("Tier out of range at cell " + i);
            }
            upper[i] = kinds[i] == RAMP ? (byte) (heights[i] + 1) : heights[i];
            highest = Math.max(highest, upper[i]);
        }
        boolean[] rim = new boolean[cells];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (kinds[i] != GROUND || heights[i] == 0) continue;
                for (int dy = -1; dy <= 1 && !rim[i]; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int n = ny * width + nx;
                        if (kinds[n] == FACE || heights[n] < heights[i]) {
                            rim[i] = true;
                            break;
                        }
                    }
                }
            }
        }
        byte[] rise = new byte[cells];
        for (int i = 0; i < cells; i++) {
            double r = rampRise == null || kinds[i] != RAMP || Double.isNaN(rampRise[i]) ? .5 : rampRise[i];
            rise[i] = (byte) Math.round(Math.max(0, Math.min(1, r)) * 255);
        }
        return new ElevationMap(width, height, heights, kinds, upper, rim, rise, highest, rule);
    }

    public int width() { return width; }
    public int height() { return height; }
    public SightRule sightRule() { return rule; }
    /** The highest tier any cell stands at, a ramp's head included. */
    public int highestTier() { return highestTier; }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    /** Height for sight: tier for ground, upper tier for a face, lower tier for a ramp. */
    public int sightHeightAt(int idx) { return sightHeight[idx]; }

    /** Tier a body standing in the cell sees from; out of bounds is the base. */
    public int eyeTier(int x, int y) {
        return inBounds(x, y) ? sightHeight[y * width + x] : 0;
    }

    /** The highest tier the cell reaches: a ramp's head tier, otherwise its own. */
    public int upperTierAt(int idx) { return upperTier[idx]; }

    public byte kindAt(int idx) { return kind[idx]; }
    public boolean isFaceAt(int idx) { return kind[idx] == FACE; }
    public boolean isRampAt(int idx) { return kind[idx] == RAMP; }
    /** How far up its ramp a ramp cell stands, 0 at the foot to 1 at the head. */
    public float rampRiseAt(int idx) { return (rampRise[idx] & 0xff) / 255f; }
    /** A plateau-top cell beside a face or lower ground: the lip seen from below. */
    public boolean isRimAt(int idx) { return rim[idx]; }

    public int tier(int x, int y) { return inBounds(x, y) ? sightHeight[y * width + x] : 0; }
    public boolean isFace(int x, int y) { return inBounds(x, y) && kind[y * width + x] == FACE; }
    public boolean isRamp(int x, int y) { return inBounds(x, y) && kind[y * width + x] == RAMP; }
    public boolean isRim(int x, int y) { return inBounds(x, y) && rim[y * width + x]; }

    /**
     * Whether a cell stands higher than an eye on {@code eyeTier}: the only
     * cells whose sight answer elevation changes.
     */
    public boolean above(int idx, int eyeTier) {
        return rule != SightRule.FLAT && sightHeight[idx] > eyeTier;
    }

    /**
     * Whether a cell on a ray from an eye on {@code eyeTier} stops sight there,
     * given whether the ray has already crossed onto higher ground. A face is
     * a skyline the eye looks past (strict: a wall); the first top cell past
     * it is the rim the eye sees; anything further on that higher ground is
     * hidden.
     */
    public boolean stopsSight(int idx, int eyeTier, boolean onHigherGround) {
        if (!above(idx, eyeTier)) return false;
        if (kind[idx] == FACE) return rule == SightRule.STRICT;
        return onHigherGround || !(rule == SightRule.SKYLINE && rim[idx]);
    }

    /**
     * Whether a body in this cell can be seen from an eye on {@code eyeTier},
     * leaving the line between them aside: anything at or below the eye, and
     * from below only the rim under the skyline rule.
     */
    public boolean visibleFrom(int idx, int eyeTier) {
        if (!above(idx, eyeTier)) return true;
        if (kind[idx] == FACE) return true;
        return rule == SightRule.SKYLINE && rim[idx];
    }

    /**
     * Whether stepping between two adjacent walkable cells crosses a tier
     * without a ramp — the shared edge a cliff closes.
     */
    public boolean separates(int a, int b) {
        boolean rampA = kind[a] == RAMP, rampB = kind[b] == RAMP;
        if (rampA && rampB) return false;
        if (rampA) return sightHeight[b] != sightHeight[a] && sightHeight[b] != upperTier[a];
        if (rampB) return sightHeight[a] != sightHeight[b] && sightHeight[a] != upperTier[b];
        return sightHeight[a] != sightHeight[b];
    }
}
