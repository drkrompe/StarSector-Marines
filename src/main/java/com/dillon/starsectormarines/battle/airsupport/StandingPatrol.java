package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;
import java.util.function.Predicate;

/**
 * A wing's standing patrol (`air-nouns.md`, Standing patrol): fighters the wing puts up over its own formations on its
 * own initiative, without anybody on the ground calling for them, while the air is contested. Each is an ordinary cover
 * sortie ({@link AirPoolService#patrol}): it holds a ring over the ground it was sent to, chases the hostile aircraft
 * that come near it ({@code CoverTasking}) and goes home, so the air war over the formations happens on its own.
 *
 * <p>The patrol spends the same stock a strike does and keeps the director's reserve ({@link
 * AirStrikeDirector#COVER_RESERVE}) for the gun runs the radios ask for; it never puts up more than {@code maxUp}
 * at once, never sends a second fighter over ground one already covers, and goes round its stations in turn so
 * every formation gets its share of the sky.
 *
 * <p>The patrol is not a strike: it is the wing's own tasking over its own ground, so it needs no observer. What its
 * fighters do to the ground under them is cover's law, which strikes only ground the side has sighted.
 */
public final class StandingPatrol {

    /** Ticks between looks at whether another fighter should go up. */
    static final int PERIOD_TICKS = 90;

    /** One piece of ground the patrol covers: where a ring over it centres now, or null while there is nothing there. */
    @FunctionalInterface
    public interface Station {
        float[] point(BattleSimulation sim);
    }

    private final Faction side;
    private final List<? extends Station> stations;
    private final int maxUp;
    private final Predicate<BattleSimulation> contested;
    private int last = -1;
    private int launched;

    /**
     * @param contested whether the air over the battle is contested now; the patrol flies only while it is
     */
    public StandingPatrol(Faction side, List<? extends Station> stations, int maxUp,
                          Predicate<BattleSimulation> contested) {
        this.side = side;
        this.stations = List.copyOf(stations);
        this.maxUp = maxUp;
        this.contested = contested;
    }

    public Faction side() {
        return side;
    }

    /** Patrol fighters this patrol has put up, for evidence. */
    public int launched() {
        return launched;
    }

    /** One look: puts a fighter up over the next station that wants one, when the wing has one to spare. */
    void tick(BattleSimulation sim, AirPoolService pool) {
        if (stations.isEmpty() || sim.getSimTickIndex() % PERIOD_TICKS != 0) return;
        if (pool.coversOut(side) >= maxUp) return;
        if (pool.available(side, AirPoolKind.FIGHTER) <= AirStrikeDirector.COVER_RESERVE) return;
        if (!contested.test(sim)) return;
        float[][] points = new float[stations.size()][];
        boolean[] wanted = new boolean[stations.size()];
        for (int i = 0; i < points.length; i++) {
            points[i] = stations.get(i).point(sim);
            wanted[i] = points[i] != null
                    && !pool.covers(side, points[i][0], points[i][1], AirStrikeDirector.COVER_DEDUP_CELLS);
        }
        int next = next(wanted, last);
        if (next < 0) return;
        if (pool.patrol(sim, side, points[next][0], points[next][1]) == AirPoolService.Outcome.CALLING) {
            last = next;
            launched++;
        }
    }

    /** The first wanted station after {@code last}, going round; -1 when none is wanted. */
    static int next(boolean[] wanted, int last) {
        for (int k = 1; k <= wanted.length; k++) {
            int i = Math.floorMod(last + k, wanted.length);
            if (wanted[i]) return i;
        }
        return -1;
    }

    /**
     * A station over a lane: where the foremost of {@code side}'s squads in the band stands, a squad whose leader is
     * within {@code halfWidth} of {@code across} (along x when the front advances along y) and furthest toward the
     * enemy ({@code sign} the way the front advances), since that is where the lane's fight and the enemy's fliers are.
     */
    public static Station lane(Faction side, boolean advancesAlongY, int across, int halfWidth, int sign) {
        return sim -> {
            float[] best = null;
            float bestAlong = -Float.MAX_VALUE;
            for (Squad squad : sim.getSquads()) {
                if (squad.faction != side || !sim.world().isAlive(squad.leaderId)) continue;
                float x = sim.world().x(squad.leaderId), y = sim.world().y(squad.leaderId);
                float at = advancesAlongY ? x : y;
                if (Math.abs(at - across) > halfWidth) continue;
                float along = (advancesAlongY ? y : x) * sign;
                if (along > bestAlong) {
                    bestAlong = along;
                    best = new float[]{x, y};
                }
            }
            return best;
        };
    }
}
