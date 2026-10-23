package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullContact;
import com.dillon.starsectormarines.battle.vehicle.HullShove;
import com.dillon.starsectormarines.battle.vehicle.HullRam;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hulls do not drive through each other ({@link HullContact}). Every pair of visible, unwrecked hulls whose
 * footprints come within {@link HullContact#SKIN} is resolved once a tick, after the ground pass has driven
 * them: the closing speed is taken off whoever must yield, and what overlap remains is shoved apart by mass
 * through the same terrain guard a hull's own slide uses, so a shove never puts a hull in a wall. Hostile hulls
 * that meet ram each other ({@link HullRam}) on the tick they first touch. Faction does not change the shove.
 *
 * <p>A pair that has stayed in contact for {@link #JAM_SECONDS} without both hulls getting {@link #JAM_PROGRESS}
 * is jammed: two hulls nose to nose in a lane too narrow to pass, a column backed into the trucks coming out, or
 * a truck inching a parked scout car along a road it cannot leave. A column on the move and two hulls pulling
 * past each other both get somewhere. Neither planner reverses out of that, so the pair is let through each other, as hulls drove
 * before there was contact, until they are clear. That is the backstop, not the rule: a meet that can be
 * passed is passed long before it.
 *
 * <p>A wreck is not a hull here: it is left where it died, and a live hull passing it is not stopped by it.
 * Serial; the convoy is a handful of hulls, so the pair walk is all of them against all of them.
 */
public final class HullContactSystem {

    /** Most a shove moves a hull in one tick, cells: two hulls set down inside each other part over a few ticks. */
    static final float MAX_STEP = .25f;
    /** How long a pair may stay in contact without either hull getting anywhere before it is jammed, seconds. */
    static final float JAM_SECONDS = 6f;
    /** How far both hulls must get, cells, for a pair in contact to count as moving. */
    static final float JAM_PROGRESS = .5f;
    private static final int JAM_TICKS = Math.round(JAM_SECONDS / BattleSimulation.TICK_DT);

    private final ConvoyService convoy;
    private final UnitRosterService roster;
    private final NavigationGrid grid;
    private final float[] contact = new float[3];
    private final float[] side = new float[2];
    private final float[] unmet = new float[2];
    private Set<Long> touching = new HashSet<>(), touched = new HashSet<>();
    /** Per pair in contact: ticks without progress, then where each hull stood when that count began. */
    private Map<Long, float[]> stuck = new HashMap<>(), stuckNext = new HashMap<>();
    private long contacts, rams, jams;
    private float worstOverlap;

    public HullContactSystem(UnitRosterService roster, NavigationGrid grid) {
        this.roster = roster;
        this.convoy = roster.convoy();
        this.grid = grid;
    }

    /** Hull-pair contacts resolved since the battle began; evidence. */
    public long contacts() { return contacts; }

    /** Rams between hostile hulls since the battle began; evidence. */
    public long rams() { return rams; }

    /** Hull pairs let through each other because they were jammed, since the battle began; evidence. */
    public long jams() { return jams; }

    /** The deepest two bare footprints were found inside each other before a resolve, cells; evidence. */
    public float worstOverlap() { return worstOverlap; }

    public void tick() {
        if (convoy.vehicleCount() < 2) return;
        long[] ids = convoy.entityIds();
        for (int i = 0; i < ids.length; i++) {
            if (!live(ids[i])) continue;
            for (int j = i + 1; j < ids.length; j++) {
                if (live(ids[j])) resolve(ids[i], ids[j]);
            }
        }
        Set<Long> swap = touched;
        touched = touching;
        touching = swap;
        touching.clear();
        Map<Long, float[]> swapStuck = stuck;
        stuck = stuckNext;
        stuckNext = swapStuck;
        stuckNext.clear();
    }

    private boolean live(long id) {
        VehicleMission mission = convoy.mission(id);
        return mission != null && convoy.body(id) != null && convoy.vehicleType(id) != null && mission.isVisible()
                && mission.state != VehicleState.WRECKED && mission.state != VehicleState.GONE;
    }

    private void resolve(long a, long b) {
        GroundBody ba = convoy.body(a), bb = convoy.body(b);
        VehicleType ta = convoy.vehicleType(a), tb = convoy.vehicleType(b);
        if (!HullContact.overlap(ba.x, ba.y, ba.facingDegrees, ta.visualLengthCells * .5f, ta.visualWidthCells * .5f,
                bb.x, bb.y, bb.facingDegrees, tb.visualLengthCells * .5f, tb.visualWidthCells * .5f,
                HullContact.SKIN, contact)) return;
        long pair = a * 0x9E3779B97F4A7C15L ^ b;
        if (jammed(pair, ba, bb)) return;
        float nx = contact[0], ny = contact[1];
        float depth = contact[2] - HullContact.SKIN;
        float ma = ta.massTonnes(), mb = tb.massTonnes();
        float aInto = forward(ba, nx, ny), bInto = -forward(bb, nx, ny);   // each one's forward toward the other
        float aClosing = ba.speed * aInto, bClosing = bb.speed * bInto;

        Faction fa = convoy.faction(a), fb = convoy.faction(b);
        boolean hostile = fa != null && fa.hostileTo(fb);
        // Nobody brakes for the enemy: hostile hulls close at full speed and ram, then can go no further.
        if (hostile && depth <= 0f) return;
        if (depth > 0f) {
            contacts++;
            worstOverlap = Math.max(worstOverlap, depth);
            if (hostile) {
                touching.add(pair);
                if (!touched.contains(pair)) ram(a, ba, ma, aInto, b, bb, mb, bInto, aClosing + bClosing);
                aClosing = ba.speed * aInto;
                bClosing = bb.speed * bInto;
            }
        }

        // Yield: a follower takes the pace of the hull ahead; head-on, one waits and the other crawls.
        boolean headOn = aClosing > 0f && bClosing > 0f;
        if (headOn) {
            boolean aYields = HullContact.yields(ma, a, mb, b);
            float onCrawl = depth > 0f ? HullContact.CRAWL : Float.MAX_VALUE;
            ba.speed = HullContact.yieldSpeed(ba.speed, aInto, aYields ? 0f : onCrawl);
            bb.speed = HullContact.yieldSpeed(bb.speed, bInto, aYields ? onCrawl : 0f);
        } else {
            ba.speed = HullContact.yieldSpeed(ba.speed, aInto, -bClosing);
            bb.speed = HullContact.yieldSpeed(bb.speed, bInto, -aClosing);
        }

        // Along the contact the lighter hull is shoved most of the way: that is physics.
        float shareB = ma / (ma + mb);
        float push = Math.max(0f, depth);
        float bx = nx * push * shareB, by = ny * push * shareB;
        float ax = -nx * push * (1f - shareB), ay = -ny * push * (1f - shareB);
        // Passing is steering, not physics: two hulls nose to nose each pull to their own side at the same pace,
        // whatever they weigh, from the moment they are within the skin.
        if (facingAgainst(ba, bb)) {
            HullContact.lateral(ba.x, ba.y, ba.facingDegrees, bb.x, bb.y, nx, ny, side);
            float pull = HullContact.PASS_SPEED * BattleSimulation.TICK_DT;
            bx += side[0] * pull;
            by += side[1] * pull;
            ax -= side[0] * pull;
            ay -= side[1] * pull;
        }
        if (ax == 0f && ay == 0f && bx == 0f && by == 0f) return;
        part(a, ba, ax, ay, b, bb, bx, by);
    }

    /**
     * Carries the pair's jam count into the next tick and says whether it has run out: for {@link #JAM_SECONDS} one
     * of the two has not got {@link #JAM_PROGRESS} from where it stood when the count began. A jammed pair stays
     * jammed until its footprints are clear, which drops it from the count.
     */
    private boolean jammed(long pair, GroundBody ba, GroundBody bb) {
        float[] s = stuck.get(pair);
        if (s != null && s[0] >= JAM_TICKS) {
            // Jammed stays jammed until the two are clear of each other, or they would be shoved apart half through.
            s[0]++;
        } else if (s == null || Math.hypot(ba.x - s[1], ba.y - s[2]) > JAM_PROGRESS
                && Math.hypot(bb.x - s[3], bb.y - s[4]) > JAM_PROGRESS) {
            s = new float[] {0f, ba.x, ba.y, bb.x, bb.y};
        } else {
            s[0]++;
        }
        stuckNext.put(pair, s);
        if (s[0] < JAM_TICKS) return false;
        if (s[0] == JAM_TICKS) jams++;
        return true;
    }

    /**
     * Moves {@code a} and {@code b} by their shares, keeping both out of the route planner's clearance where it can:
     * what one cannot take against a wall the other takes, and only what neither can take that way is resolved on
     * the bare footprints.
     */
    private void part(long a, GroundBody ba, float ax, float ay, long b, GroundBody bb, float bx, float by) {
        shove(b, bb, bx, by, true);
        float ux = unmet[0], uy = unmet[1];
        shove(a, ba, ax - ux, ay - uy, true);
        ux = unmet[0];
        uy = unmet[1];
        if (ux == 0f && uy == 0f) return;
        shove(b, bb, -ux, -uy, true);
        ux = unmet[0];
        uy = unmet[1];
        if (ux == 0f && uy == 0f) return;
        shove(b, bb, ux, uy, false);
        shove(a, ba, -unmet[0], -unmet[1], false);
    }

    /**
     * Moves the hull by up to {@code (dx, dy)}, at most {@link #MAX_STEP}, through its footprint sweep; leaves what
     * it could not take in {@link #unmet}.
     */
    private void shove(long id, GroundBody body, float dx, float dy, boolean margin) {
        float want = (float) Math.hypot(dx, dy);
        if (want < 1e-5f) { unmet[0] = unmet[1] = 0f; return; }
        float scale = want > MAX_STEP ? MAX_STEP / want : 1f;
        HullShove.move(body, convoy.vehicleType(id), grid, dx * scale, dy * scale, margin, unmet);
    }

    /**
     * Two hostile hulls first touching: the impulse of their combined closing speed, damage through the shared
     * armour law, each slowed by the velocity change it took and stalled if that was hard.
     */
    private void ram(long a, GroundBody ba, float ma, float aInto, long b, GroundBody bb, float mb, float bInto,
                     float closing) {
        if (!(closing > 0f)) return;
        float j = HullRam.impulse(closing, ma, mb);
        rams++;
        hit(a, ba, aInto, j / ma, HullRam.damage(j, ma), b);
        hit(b, bb, bInto, j / mb, HullRam.damage(j, mb), a);
    }

    private void hit(long id, GroundBody body, float into, float dv, float damage, long by) {
        if (into > 0f) body.speed -= Math.signum(body.speed) * Math.min(Math.abs(body.speed), dv * into);
        if (damage > 0f) roster.damageService().applyDamage(id, by, damage, HullRam.PENETRATION, 0f);
        if (dv >= HullRam.STALL_DV) {
            roster.stagger().stagger(id, Math.max(1, Math.round(HullRam.STALL_SECONDS / BattleSimulation.TICK_DT)),
                    Cause.STALLED);
        }
    }

    /** The hull's forward against the unit normal ({@code cos}). */
    private static float forward(GroundBody body, float nx, float ny) {
        double rad = Math.toRadians(body.facingDegrees);
        return (float) -Math.sin(rad) * nx + (float) Math.cos(rad) * ny;
    }

    private static boolean facingAgainst(GroundBody a, GroundBody b) {
        double ar = Math.toRadians(a.facingDegrees), br = Math.toRadians(b.facingDegrees);
        float dot = (float) (Math.sin(ar) * Math.sin(br) + Math.cos(ar) * Math.cos(br));
        return dot < HullContact.HEAD_ON;
    }
}
