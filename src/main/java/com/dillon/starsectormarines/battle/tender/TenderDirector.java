package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.MissionStance;
import com.dillon.starsectormarines.battle.warship.ObjectiveBias;

import java.util.Arrays;
import java.util.List;

/**
 * What an unpiloted tender builds, and where it waits, from its hull's {@link MissionStance}.
 *
 * <p>It is a director over {@link Tender#lay}, never a second order model: it lays the same
 * site a pilot or the debug panel would, and the build then runs, stands down and fails exactly
 * as theirs do. A tender whose stance is {@link MissionStance#HOLD} (the default) is never
 * directed, so a scripted or debug tender keeps its station until told otherwise.
 *
 * <ul>
 *   <li><b>ATTACK</b> builds forward positions on cleared ground behind the advance: for the
 *       objective its own side has pushed closest to, the most forward cell on the line from the
 *       leading friendly back towards the rear that no hostile is within the build's threat radius
 *       of. Idle, it hovers a little behind that leading friendly.</li>
 *   <li><b>DEFEND</b> builds on the approaches to the held ground: from the held point that the
 *       nearest hostile is closest to, a few cells out along the bearing to that hostile. With no
 *       hostile on the field there is no approach to read, and it builds on the held point itself.
 *       Idle, it hovers on the far side of the most threatened held point.</li>
 * </ul>
 *
 * <p>Both refuse ground a build would immediately stand down on (a hostile inside
 * {@link TenderSystem#THREAT_CELLS}), ground already covered by {@link #COVERED_COUNT} of its
 * side's turrets, and cells too close to another turret or live site. That is what bounds how many
 * turrets a free tender raises: it is the ground, not a count.
 *
 * <p>The decision ({@link #plan}) is a pure function of a frozen {@link Picture}, so it is tested
 * without a battle; {@link Picture#read} is the only part that looks at the simulation. Like the
 * build's own stand-down check, it reads hostile positions directly rather than through the side's
 * knowledge.
 */
public final class TenderDirector {

    /** Seconds between decisions; a tender does not need to re-think every tick. */
    static final float DECIDE_SECONDS = 2f;
    /** How far ahead of the leading friendly, in cells, an attacking tender will look for ground. */
    static final float AHEAD_CELLS = 6f;
    /** How far back from the leading friendly, in cells, an attacking tender will look for cleared ground. */
    static final float BEHIND_CELLS = 30f;
    /** How far out along the approach, in cells, a defending tender prefers to build. */
    static final float APPROACH_CELLS = 8f;
    /** How far behind the advance, or behind the held ground, an idle tender hovers. */
    static final float IDLE_BACK_CELLS = 10f;
    /** An idle station is moved only when the wanted one is this far from the current. */
    static final float RESTATION_CELLS = 4f;
    /** No new site closer than this to a turret of its side or another live site. */
    static final float SPACING_CELLS = 6f;
    /** Ground with this many of its side's turrets within {@link #COVERED_RADIUS_CELLS} is covered. */
    static final int COVERED_COUNT = 2;
    static final float COVERED_RADIUS_CELLS = 14f;
    private static final float STEP_CELLS = 2f;
    private static final float[] LATERALS = {0f, 3f, -3f};

    private TenderDirector() {}

    /**
     * The battle as the director sees it, in cells. Positions are flat {@code x, y} pairs.
     *
     * @param stance     the hull's stance
     * @param focus      the stance's objective ground ({@link ObjectiveBias#focus})
     * @param friendlies the side's ground people and vehicles
     * @param hostiles   every hostile body on the field
     * @param turrets    the side's standing turrets
     * @param sites      the tender's own live sites
     */
    public record Picture(MissionStance stance, List<float[]> focus, float[] friendlies, float[] hostiles,
                          float[] turrets, float[] sites) {

        /** Reads {@code tender}'s picture off the battle. */
        public static Picture read(BattleSimulation sim, Tender tender) {
            Faction side = tender.faction();
            UnitRosterService roster = sim.getRoster();
            World world = sim.world();
            long[] dense = roster.denseArray();
            Points friendlies = new Points();
            Points hostiles = new Points();
            Points turrets = new Points();
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = dense[i];
                Faction f = roster.identity().faction(u);
                if (f == null) continue;
                float x = world.x(u);
                float y = world.y(u);
                if (side.hostileTo(f)) {
                    hostiles.add(x, y);
                    continue;
                }
                if (!side.friendlyTo(f)) continue;
                UnitType type = roster.identity().type(u);
                if (type == UnitType.TURRET) turrets.add(x, y);
                else if (type != null && !type.isStatic() && !type.isDrone() && world.kinematics(u) == null) {
                    friendlies.add(x, y);
                }
            }
            Points sites = new Points();
            for (ConstructionOrder o : tender.orders()) {
                if (!o.phase().terminal()) sites.add(o.siteX(), o.siteY());
            }
            return new Picture(tender.ship().stance(), ObjectiveBias.focus(sim, tender.ship()),
                    friendlies.array(), hostiles.array(), turrets.array(), sites.array());
        }
    }

    /**
     * What to do: lay a site at {@code (siteX, siteY)}, or (with no site) hover at
     * {@code (stationX, stationY)}. {@code hasSite} and {@code hasStation} say which halves are set.
     */
    public record Plan(boolean hasSite, float siteX, float siteY, boolean hasStation, float stationX, float stationY) {
        static final Plan NONE = new Plan(false, 0f, 0f, false, 0f, 0f);

        static Plan site(float x, float y) {
            return new Plan(true, x, y, false, 0f, 0f);
        }

        Plan withStation(float x, float y) {
            return new Plan(hasSite, siteX, siteY, true, x, y);
        }
    }

    /** Decides where from {@code p}. Never lays anything itself. */
    public static Plan plan(Picture p, NavigationGrid grid) {
        if (p.stance() == MissionStance.ATTACK) return attack(p, grid);
        if (p.stance() == MissionStance.DEFEND) return defend(p, grid);
        return Plan.NONE;
    }

    private static Plan attack(Picture p, NavigationGrid grid) {
        // The objective the side has pushed closest to, and the friendly leading on it.
        float bestGap = Float.MAX_VALUE;
        float[] objective = null;
        int lead = -1;
        for (float[] f : p.focus()) {
            int i = nearest(p.friendlies(), f[0], f[1]);
            if (i < 0) continue;
            float gap = dist(p.friendlies()[i], p.friendlies()[i + 1], f[0], f[1]);
            if (gap < bestGap) {
                bestGap = gap;
                objective = f;
                lead = i;
            }
        }
        if (objective == null) return Plan.NONE;
        float lx = p.friendlies()[lead];
        float ly = p.friendlies()[lead + 1];
        float[] u = unit(objective[0] - lx, objective[1] - ly);
        Plan plan = Plan.NONE;
        // Most forward first: a little ahead of the lead, then back along the line towards the rear.
        for (float s = Math.min(AHEAD_CELLS, bestGap); s >= -BEHIND_CELLS && !plan.hasSite(); s -= STEP_CELLS) {
            plan = firstOpen(p, grid, lx + u[0] * s, ly + u[1] * s, u);
        }
        return plan.withStation(lx - u[0] * IDLE_BACK_CELLS, ly - u[1] * IDLE_BACK_CELLS);
    }

    private static Plan defend(Picture p, NavigationGrid grid) {
        // The held point the nearest hostile is closest to.
        float bestGap = Float.MAX_VALUE;
        float[] held = null;
        float[] u = null;
        for (float[] f : p.focus()) {
            int h = nearest(p.hostiles(), f[0], f[1]);
            float gap = h < 0 ? Float.MAX_VALUE : dist(p.hostiles()[h], p.hostiles()[h + 1], f[0], f[1]);
            if (held == null || gap < bestGap) {
                bestGap = gap;
                held = f;
                u = h < 0 ? null : unit(p.hostiles()[h] - f[0], p.hostiles()[h + 1] - f[1]);
            }
        }
        if (held == null) return Plan.NONE;
        Plan plan = Plan.NONE;
        if (u == null) {
            plan = firstOpen(p, grid, held[0], held[1], new float[]{1f, 0f});
            return plan.withStation(held[0], held[1]);
        }
        // Out along the approach first, then nearer the held ground, then a little farther.
        float[] reach = {APPROACH_CELLS, APPROACH_CELLS - 2f, APPROACH_CELLS + 2f, APPROACH_CELLS - 4f,
                APPROACH_CELLS + 4f, 2f, 0f};
        for (int i = 0; i < reach.length && !plan.hasSite(); i++) {
            plan = firstOpen(p, grid, held[0] + u[0] * reach[i], held[1] + u[1] * reach[i], u);
        }
        return plan.withStation(held[0] - u[0] * IDLE_BACK_CELLS, held[1] - u[1] * IDLE_BACK_CELLS);
    }

    /** The first acceptable cell at {@code (x, y)} or a few cells to either side of the axis {@code u}. */
    private static Plan firstOpen(Picture p, NavigationGrid grid, float x, float y, float[] u) {
        for (float side : LATERALS) {
            float cx = x - u[1] * side;
            float cy = y + u[0] * side;
            if (acceptable(p, grid, cx, cy)) {
                return Plan.site((float) Math.floor(cx) + 0.5f, (float) Math.floor(cy) + 0.5f);
            }
        }
        return Plan.NONE;
    }

    /** Whether a site at the cell holding {@code (x, y)} would be laid, worked and worth having. */
    static boolean acceptable(Picture p, NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return false;
        float sx = cx + 0.5f;
        float sy = cy + 0.5f;
        if (within(p.hostiles(), sx, sy, TenderSystem.THREAT_CELLS) > 0) return false;
        if (within(p.turrets(), sx, sy, SPACING_CELLS) > 0 || within(p.sites(), sx, sy, SPACING_CELLS) > 0) return false;
        return within(p.turrets(), sx, sy, COVERED_RADIUS_CELLS) < COVERED_COUNT;
    }

    private static int within(float[] pts, float x, float y, float r) {
        int n = 0;
        for (int i = 0; i < pts.length; i += 2) {
            if (dist(pts[i], pts[i + 1], x, y) <= r) n++;
        }
        return n;
    }

    private static int nearest(float[] pts, float x, float y) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < pts.length; i += 2) {
            float d = dist(pts[i], pts[i + 1], x, y);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        return (float) Math.hypot(ax - bx, ay - by);
    }

    private static float[] unit(float dx, float dy) {
        float len = (float) Math.hypot(dx, dy);
        return len < 0.01f ? new float[]{1f, 0f} : new float[]{dx / len, dy / len};
    }

    /** A growable flat list of points. */
    static final class Points {
        private float[] xy = new float[16];
        private int n;

        void add(float x, float y) {
            if (n + 2 > xy.length) xy = Arrays.copyOf(xy, xy.length * 2);
            xy[n++] = x;
            xy[n++] = y;
        }

        float[] array() {
            return Arrays.copyOf(xy, n);
        }
    }

    /**
     * What an unpiloted tender builds: an attacking tender the quickest option, because the advance
     * moves on and a forward position is worth having soon; a defending tender the heaviest it can
     * pay for, because held ground gives the builders time. Null when a metered stock can pay for
     * nothing.
     */
    static BuildOption option(MissionStance stance, Tender t) {
        BuildOption best = null;
        for (BuildOption o : BuildOption.ALL) {
            if (t.metered() && o.cost() > t.stock()) continue;
            boolean better = best == null || (stance == MissionStance.DEFEND
                    ? o.buildSeconds() > best.buildSeconds()
                    : o.buildSeconds() < best.buildSeconds());
            if (better) best = o;
        }
        return best;
    }

    /**
     * Runs the director for one tender this tick: when the tender is unpiloted, has a stance other
     * than HOLD, and has nothing on its line, it lays the planned site ({@link #option}) or
     * moves to its idle station. Returns the plan acted on, for evidence.
     */
    static Plan direct(Tender t, BattleSimulation sim, float dt) {
        if (t.piloted() || t.ship().stance() == MissionStance.HOLD) return Plan.NONE;
        t.directorClock += dt;
        if (t.directorClock < DECIDE_SECONDS) return Plan.NONE;
        t.directorClock = 0f;
        if (t.current() != null) return Plan.NONE;
        Plan plan = plan(Picture.read(sim, t), sim.getGrid());
        BuildOption option = option(t.ship().stance(), t);
        if (plan.hasSite() && option != null && t.lay(option, plan.siteX(), plan.siteY(), sim.getGrid()) != null) {
            return plan;
        }
        if (plan.hasStation()) {
            float sx = t.ship().hasStation() ? t.ship().stationX() : t.ship().x;
            float sy = t.ship().hasStation() ? t.ship().stationY() : t.ship().y;
            if (dist(sx, sy, plan.stationX(), plan.stationY()) > RESTATION_CELLS) {
                t.ship().flyRoute(null, false);
                t.ship().stationTo(plan.stationX(), plan.stationY());
            }
        }
        return plan;
    }
}
