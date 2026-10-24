package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * A trained shooter who cannot fire on his target for a friend in the lane, and
 * is not stepping out of it, <b>shoots somebody he can</b>.
 *
 * <p>The firing gate holds a trained shooter's trigger while one of his own
 * stands in the lane ({@code FiringSystem}, {@code FRIENDLY_IN_LANE}), and
 * {@link LaneSidestep} answers it by moving a cell. That answers a squadmate a
 * few cells in front of the muzzle. It cannot answer a friend further down the
 * lane, or one in melee with the target: no cell within a step clears that lane,
 * and a militia crowd is made of exactly those. Before this the crowd held its
 * fire for seconds at a time until somebody moved.
 *
 * <p>Switching target on a blocked lane was measured once against Conquest and
 * removed, because it broke up the concentrated fire that does the killing. The
 * alternative then was firing through the friend; since the gate it is not
 * firing at all, so the trade is now a shot elsewhere against no shot. The
 * sidestep keeps first claim: a marine part-way through a step-aside keeps his
 * target, so a step that will clear the lane is not traded for another enemy.
 *
 * <p><b>A switch costs a registration, so it is made only when it pays.</b> A
 * new target has to be registered before a round can go at it
 * ({@code REGISTERING}), which takes the shooter's own reflex delay. Two rules
 * follow, and the first version without them fired fewer rounds than holding
 * did, because a lane that flickers clear and blocked flipped the shooter
 * between two targets and restarted his registration every time:
 * <ul>
 *   <li>he switches only once the lane has been blocked as long as the switch
 *       would cost him, since a lane that clears sooner was cheaper to wait
 *       out;</li>
 *   <li>once switched he finishes the alternative, keeping it for as long as
 *       it stays shootable with a clear lane, rather than going back the
 *       moment his first target's lane clears.</li>
 * </ul>
 * With no shootable enemy that has a clear lane, the original target stands
 * and the gate holds, as before.
 *
 * <p>The record of who is holding on what is kept per shooter and touched only
 * by that shooter's own update, so a parallel dispatch reads and writes
 * disjoint entries and a replay makes the same choices.
 */
public final class LaneRetarget implements CombatService.FireRedirect {

    private final FiringSystem.FriendInLane friendInLane;
    private final TacticalScoring scoring;
    private final CombatService combat;
    private final World world;
    private final IntSupplier tick;
    private final Map<Long, Hold> holds = new ConcurrentHashMap<>();

    public LaneRetarget(FiringSystem.FriendInLane friendInLane, TacticalScoring scoring,
                        CombatService combat, World world, IntSupplier tick) {
        this.friendInLane = friendInLane;
        this.scoring = scoring;
        this.combat = combat;
        this.world = world;
        this.tick = tick;
    }

    @Override
    public long redirect(long shooter, long target) {
        Hold hold = holds.get(shooter);
        long kept = hold != null ? stillShootable(shooter, target, hold.alternative) : 0L;
        if (kept != 0L) return kept;
        if (!friendInLane.test(shooter, target)) {
            if (hold != null) holds.remove(shooter);
            return target;
        }
        if (LaneSidestep.isStepping(shooter, world)) return target;

        int now = tick.getAsInt();
        if (hold == null || hold.target != target) {
            holds.put(shooter, new Hold(target, now));
            return target;
        }
        float switchCost = combat.soldierProfile(shooter).experienceTier().reflexDelaySeconds;
        if ((now - hold.since) * BattleSimulation.TICK_DT < switchCost) return target;

        long alternative = scoring.closestEnemyInAttackRange(shooter,
                combat.reflexTargetId(shooter), Float.MAX_VALUE,
                other -> other != target && !friendInLane.test(shooter, other));
        if (alternative == 0L) return target;
        hold.alternative = alternative;
        return alternative;
    }

    /**
     * {@code alternative} while it is still in range and line of fire with a
     * clear lane, else {@code 0L}. Asked through the same search the switch
     * used, preferring the alternative absolutely, so it is the same test.
     */
    private long stillShootable(long shooter, long target, long alternative) {
        if (alternative == 0L || alternative == target) return 0L;
        long found = scoring.closestEnemyInAttackRange(shooter, alternative, Float.MAX_VALUE,
                other -> other == alternative && !friendInLane.test(shooter, other));
        return found == alternative ? alternative : 0L;
    }

    /** The target a shooter's lane has been blocked on since a tick, and the enemy he switched to, if any. */
    private static final class Hold {
        final long target;
        final int since;
        long alternative;

        Hold(long target, int since) {
            this.target = target;
            this.since = since;
        }
    }
}
