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
 * <p>The geometry is the firing gate's own, {@link BallisticResolver#inLane},
 * asked per gathered body rather than restated. A reflex that steps out of a
 * blocked lane has to agree with the rule that holds the trigger, or it
 * declines on a lane the gate holds on (a friend just past the target, in
 * melee with him) and refuses cells the gate would fire from (a lane drawn
 * wider than the body in it walls off every cell beside a packed squad).
 *
 * <p>This answers only <em>is somebody in the way</em>. What that is worth is
 * the caller's judgement.
 */
public final class FiringLane {

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
     * Cached once: {@code values()} clones its array on every call and this
     * runs per firing body per tick.
     */
    private static final Faction[] FACTIONS = Faction.values();

    /**
     * Fills {@code out} with the living bodies {@code faction} is friendly to
     * within {@code radius} of the shooter, excluding the shooter itself.
     *
     * <p>Every friendly faction rather than only the shooter's own: an allied
     * militiaman standing in a marine's lane catches the round exactly as a
     * squadmate would, so he is somebody to step around. Each faction has its
     * own snapshot slice in the index, so this is one bucket walk per friendly
     * faction and the scratch buffer is reused between them.
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
        for (Faction candidate : FACTIONS) {
            if (!faction.friendlyTo(candidate)) continue;
            index.gatherFaction(shooterX, shooterY, radius, candidate, scratch);
            for (int i = 0; i < scratch.size; i++) {
                long ally = scratch.ids[i];
                if (ally == shooter) continue;
                if (!roster.isAliveById(ally)) continue;
                out.add(roster.world().x(ally), roster.world().y(ally),
                        roster.radius(ally));
            }
        }
    }

    /**
     * True when one of {@code allies} stands in the lane from
     * {@code (fromX, fromY)} to {@code (toX, toY)}, by the lane the firing gate
     * holds on ({@link BallisticResolver#inLane}): clear of the muzzle's
     * zero-catch distance, within a body's radius plus a margin of the line, and
     * as far as just past the target, where a missed round goes first.
     */
    public static boolean blocked(Friendlies allies,
                                  float fromX, float fromY,
                                  float toX, float toY) {
        for (int i = 0; i < allies.size; i++) {
            if (BallisticResolver.inLane(fromX, fromY, toX, toY,
                    allies.xs[i], allies.ys[i], allies.radii[i])) return true;
        }
        return false;
    }
}
