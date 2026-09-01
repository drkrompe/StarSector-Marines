package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;

/**
 * Whether one of your own is standing in the lane between a muzzle and what it
 * is pointed at.
 *
 * <p>{@link BallisticResolver} has always modelled this from the round's side:
 * it walks every body the corridor crosses in time order, and a friendly met
 * before the intended target catches the round with
 * {@link BallisticResolver#FRIENDLY_INCIDENTAL_HIT_CHANCE} scaled by muzzle
 * distance, stopping it there at half damage. Nothing on the decision side ever
 * asked. Line-of-fire tests bottom out in the navigation grid, which holds
 * terrain and edge barriers and no units at all, so a marine with a squadmate
 * directly in front reads its lane as clear, keeps its target and fires into
 * their back.
 *
 * <p>The geometry here is the one {@code OverwatchKillZone} already uses to
 * reject an overwatch position whose firing lane a friendly occupies, and the
 * constants are deliberately shared with it and with the ballistic model rather
 * than restated: a body inside
 * {@link BallisticResolver#PROXIMITY_CATCH_ZERO_DISTANCE} of the muzzle has no
 * catch chance, so it does not block, and a lane is as wide as the body in it.
 *
 * <p>This answers only <em>is somebody in the way</em>. What that is worth is
 * the caller's judgement: the target picker treats it as a cost rather than a
 * veto, because a squad in column all declining to fire at once is a worse
 * answer than the shot they would otherwise take.
 */
public final class FiringLane {

    /** Minimum lane half-width in cells, for a body smaller than the round needs. */
    public static final float CLEARANCE_CELLS = 1.5f;
    /** Added to a body's own radius when that is the wider of the two. */
    public static final float RADIUS_MARGIN_CELLS = 0.5f;

    private FiringLane() {}

    /**
     * A gathered set of friendly bodies, reusable across calls so the hot
     * target scan pays one spatial query rather than one per candidate.
     */
    public static final class Friendlies {
        private float[] xs = new float[16];
        private float[] ys = new float[16];
        private float[] radii = new float[16];
        private int size;

        public int size() { return size; }

        /** Point x of the {@code i}th gathered body, {@code 0 <= i < size()}. */
        public float x(int i) { return xs[i]; }

        /** Point y of the {@code i}th gathered body. */
        public float y(int i) { return ys[i]; }

        /** Body radius of the {@code i}th gathered body. */
        public float radius(int i) { return radii[i]; }

        public void clear() { size = 0; }

        public void add(float x, float y, float radius) {
            if (size == xs.length) {
                int grown = size * 2;
                xs = java.util.Arrays.copyOf(xs, grown);
                ys = java.util.Arrays.copyOf(ys, grown);
                radii = java.util.Arrays.copyOf(radii, grown);
            }
            xs[size] = x;
            ys[size] = y;
            radii[size] = radius;
            size++;
        }
    }

    /**
     * Fills {@code out} with the shooter's living faction-mates within
     * {@code radius} of it, excluding the shooter itself.
     *
     * <p>{@code scratch} is the caller's reusable id bucket; nothing is
     * retained from it.
     */
    public static void gather(UnitSpatialIndex index, UnitRosterService roster,
                              long shooter, float shooterX, float shooterY,
                              float radius, Faction faction,
                              LongBucket scratch, Friendlies out) {
        out.clear();
        if (!(radius > 0f) || !Float.isFinite(radius)) return;
        index.gatherFaction(shooterX, shooterY, radius, faction, scratch);
        for (int i = 0; i < scratch.size; i++) {
            long ally = scratch.ids[i];
            if (ally == shooter) continue;
            if (!roster.isAliveById(ally)) continue;
            out.add(roster.world().x(ally), roster.world().y(ally),
                    roster.radius(ally));
        }
    }

    /**
     * True when one of {@code allies} stands in the lane from
     * {@code (fromX, fromY)} to {@code (toX, toY)}.
     *
     * <p>Deliberately excludes a body at either end: one closer to the muzzle
     * than the ballistic model's zero-catch distance cannot take the round, and
     * one at or past the target is not between anything.
     */
    public static boolean blocked(Friendlies allies,
                                  float fromX, float fromY,
                                  float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1e-4f) return false;
        float length = (float) Math.sqrt(lengthSq);
        for (int i = 0; i < allies.size; i++) {
            float relX = allies.xs[i] - fromX;
            float relY = allies.ys[i] - fromY;
            float progress = (relX * dx + relY * dy) / length;
            if (progress <= BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE) continue;
            if (progress >= length) continue;
            float lateral = Math.abs(relX * -dy + relY * dx) / length;
            float clearance = Math.max(CLEARANCE_CELLS,
                    allies.radii[i] + RADIUS_MARGIN_CELLS);
            if (lateral < clearance) return true;
        }
        return false;
    }
}
