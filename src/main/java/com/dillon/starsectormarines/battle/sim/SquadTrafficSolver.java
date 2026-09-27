package com.dillon.starsectormarines.battle.sim;

import java.util.Arrays;

/**
 * Local, soft traffic preferences for already-authored squad travel. This is not
 * collision, route selection, or a reservation system. The host owns one stable
 * offset and accumulated yield age per squad and publishes the result before
 * dispatching members. All distances are continuous cell-space distances.
 *
 * <p>The hash grid has fixed bucket and traversal bounds. Even a completely
 * stacked population visits at most {@link #MAX_CANDIDATE_VISITS} candidates per
 * squad; crowded buckets are sampled in a rotating insertion order. Overflow is
 * deliberately approximate, never a reason to fall back to an all-pairs scan.
 */
public final class SquadTrafficSolver {
    public static final float MIN_SPEED_SCALE = 0.85f;
    public static final float MAX_LATERAL_OFFSET = 3f;
    public static final int MAX_CANDIDATE_VISITS = 72;
    private static final int BUCKET_COUNT = 1024;
    private static final int VISITS_PER_BUCKET = 8;
    private static final float BUCKET_WIDTH = 16f;
    private static final float MAX_RADIUS = 5f;
    private static final float SPREAD_RATE = 1.2f;
    private static final float PRIORITY_AGE_SECONDS = 1.5f;

    /** Rejects pairs separated by terrain; called only for nearby friendly pairs. */
    @FunctionalInterface
    public interface PairVisibility {
        boolean visible(int first, int second);
    }

    private final int[] heads = new int[BUCKET_COUNT];
    private int[] next = new int[0];
    private float[] previousAge = new float[0];
    private float[] previousOffset = new float[0];
    private int rotation;
    private int candidateVisits;
    private int pairChecks;
    private int activeHints;

    public int candidateVisits() { return candidateVisits; }
    public int pairChecks() { return pairChecks; }
    public int activeHints() { return activeHints; }

    /**
     * Solves into caller-owned arrays. Headings must be normalized or zero;
     * friendlyGroups is an adapter-resolved coordination group, not faction
     * enum ordinals (allies may share a group). Openness lies in [0,1] and must
     * include terrain ahead, not only the current point. The adapter must still
     * validate every applied displacement against current terrain and taper
     * the offset at corners, route changes and arrival. No result authorizes a
     * new path or movement for a member that was otherwise holding.
     *
     * <p>Yield age advances only while locally yielding, and is retained while
     * contested. Quantized age gives a losing squad priority after a sustained
     * wait without exchanging precedence each tick. Age resets after traffic
     * clears. Speed is always positive: this layer cannot prohibit progress.
     * Arrays must be distinct, cover count, and contain finite values. Reuses
     * scratch after capacity growth; the callback itself should not allocate.
     */
    public void solve(int count, int[] ids, int[] friendlyGroups,
                      float[] x, float[] y, float[] headingX, float[] headingY,
                      float[] radius, float[] openness, float[] lateralOffset,
                      float[] yieldAge, float[] speedScale, float dt,
                      PairVisibility visibility) {
        if (count < 0 || !Float.isFinite(dt) || dt < 0f) {
            throw new IllegalArgumentException("Invalid traffic count or step");
        }
        ensureCapacity(count);
        Arrays.fill(heads, -1);
        System.arraycopy(yieldAge, 0, previousAge, 0, count);
        System.arraycopy(lateralOffset, 0, previousOffset, 0, count);
        candidateVisits = pairChecks = activeHints = 0;
        if (count == 0) return;
        // Hash collisions only reduce sample quality, never increase work.
        int start = Math.floorMod(rotation++, count);
        for (int n = 0; n < count; n++) {
            int i = (start + n) % count;
            int bucket = bucket(cell(x[i]), cell(y[i]));
            next[i] = heads[bucket];
            heads[bucket] = i;
        }
        float step = Math.min(dt, 0.25f);
        for (int i = 0; i < count; i++) {
            speedScale[i] = 1f;
            float open = clamp(openness[i], 0f, 1f);
            float hx = headingX[i], hy = headingY[i];
            if (hx * hx + hy * hy < 0.5f) {
                lateralOffset[i] = 0f;
                yieldAge[i] = 0f;
                continue;
            }
            float pressure = 0f;
            boolean contested = false;
            boolean spreading = false;
            int cx = cell(x[i]), cy = cell(y[i]);
            for (int by = cy - 1; by <= cy + 1; by++) {
                for (int bx = cx - 1; bx <= cx + 1; bx++) {
                    int visited = 0;
                    for (int j = heads[bucket(bx, by)]; j >= 0 && visited < VISITS_PER_BUCKET;
                         j = next[j], visited++) {
                        candidateVisits++;
                        // A bucket may alias another cell; don't count a pair twice.
                        if (i == j || cell(x[j]) != bx || cell(y[j]) != by
                                || friendlyGroups[i] != friendlyGroups[j]) continue;
                        float dx = x[j] - x[i], dy = y[j] - y[i];
                        float spacing = clamp(radius[i], 0.5f, MAX_RADIUS)
                                + clamp(radius[j], 0.5f, MAX_RADIUS);
                        float reach = Math.min(BUCKET_WIDTH, spacing * 1.5f);
                        if (dx * dx + dy * dy >= reach * reach) continue;
                        float otherLength = headingX[j] * headingX[j] + headingY[j] * headingY[j];
                        if (otherLength < 0.5f) continue;
                        pairChecks++;
                        if (visibility != null && !visibility.visible(i, j)) continue;
                        float alignment = hx * headingX[j] + hy * headingY[j];
                        float along = dx * hx + dy * hy;
                        float across = -dx * hy + dy * hx;
                        float pairOpen = Math.min(open, clamp(openness[j], 0f, 1f));
                        if (alignment > 0.5f && Math.abs(across) < spacing) {
                            spreading |= pairOpen > 0f;
                            float side;
                            if (Math.abs(across) > 0.25f) side = across > 0f ? -1f : 1f;
                            else if (Math.abs(previousOffset[i] - previousOffset[j]) > 0.25f) {
                                side = previousOffset[i] > previousOffset[j] ? 1f : -1f;
                            } else side = ids[i] < ids[j] ? -1f : 1f;
                            float closeness = 1f - Math.abs(across) / spacing;
                            pressure += side * closeness * (1f - Math.abs(along) / reach) * pairOpen;
                        }
                        // Open parallel lanes spread instead of paying a speed tax.
                        boolean crossing = alignment < 0.5f;
                        boolean interfering = Math.abs(across) < spacing * (crossing ? 0.8f : 0.5f)
                                && Math.abs(along) < spacing;
                        if (!interfering || (!crossing && pairOpen > 0.5f)) continue;
                        contested = true;
                        float myPriority = (float) Math.floor(previousAge[i] / PRIORITY_AGE_SECONDS);
                        float otherPriority = (float) Math.floor(previousAge[j] / PRIORITY_AGE_SECONDS);
                        // Existing progress wins initially; accumulated yielding can override it.
                        if (!crossing && along > 0.5f) otherPriority += 1f;
                        else if (!crossing && along < -0.5f) myPriority += 1f;
                        if (otherPriority > myPriority
                                || (otherPriority == myPriority && ids[j] < ids[i])) {
                            speedScale[i] = MIN_SPEED_SCALE;
                        }
                    }
                }
            }
            float offset = previousOffset[i] + clamp(pressure, -1f, 1f) * SPREAD_RATE * step;
            if (!spreading) offset = approachZero(offset, 0.2f * step);
            // Constraint release is immediate; re-expansion is gradual. This is
            // advisory geometry, so keeping an unsafe old lane is never required.
            lateralOffset[i] = open == 0f ? 0f
                    : clamp(offset, -MAX_LATERAL_OFFSET * open, MAX_LATERAL_OFFSET * open);
            yieldAge[i] = !contested ? 0f : previousAge[i]
                    + (speedScale[i] < 1f ? step : 0f);
            if (Math.abs(lateralOffset[i]) > 0.001f || speedScale[i] < 1f) activeHints++;
        }
    }

    private void ensureCapacity(int count) {
        if (next.length >= count) return;
        int capacity = Math.max(count, Math.max(16, next.length * 2));
        next = new int[capacity];
        previousAge = new float[capacity];
        previousOffset = new float[capacity];
    }

    private static int cell(float position) { return (int) Math.floor(position / BUCKET_WIDTH); }

    private static int bucket(int x, int y) {
        return (x * 73856093 ^ y * 19349663) & (BUCKET_COUNT - 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float approachZero(float value, float distance) {
        return value > 0f ? Math.max(0f, value - distance) : Math.min(0f, value + distance);
    }
}
