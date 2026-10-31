package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCap;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.marine.CommsKitSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Roving forward observers (`air-nouns.md`, Forward observer): now and then the military sends a two-man observer team
 * (an observer with a radio, and his escort) up from behind its line. The team walks to the best vantage the mission
 * names, watches from it, and moves on: to the next vantage when it has watched a while, and at once when the swarm is
 * on it or the fire on it is heavy enough to stop the radio. From wherever it stands its radio is an operator like any
 * other, so the side's {@link AirStrikeDirector} strikes what it sees.
 *
 * <p><b>Rare.</b> A team comes up only while the map holds fewer than {@link #CAP} observers of the side, fixed posts
 * and roving teams counted together, and fewer than {@link #MOST_TEAMS} teams rove at once; the first comes
 * {@link #FIRST_TEAM_SECONDS} in and the next no sooner than {@link #TEAM_EVERY_SECONDS} after. A team whose observer
 * dies is gone; its escort holds where it stood.
 *
 * <p><b>Fair knowledge.</b> The mission ranks its vantages from what the side knows (a redoubt's call, a lane's squads
 * in contact), never from where the swarm is. A team takes the best vantage no other observer of the side stands within
 * {@link #SPREAD_CELLS} of.
 *
 * <p>UNTUNED: every figure here is a first-pass choice.
 */
public final class RovingObservers {

    /** The issuer of a team's orders. */
    public static final String ISSUER = "roving-observers";
    /** Most observers of the side on the map at once, fixed and roving together (owner: five to ten at most). */
    static final int CAP = 8;
    /** Most roving teams at once. */
    static final int MOST_TEAMS = 3;
    static final float FIRST_TEAM_SECONDS = 90f;
    static final float TEAM_EVERY_SECONDS = 150f;
    /** Seconds a team watches from one vantage before it moves on. */
    static final float DWELL_SECONDS = 75f;
    /** Seconds a team stays on its way to, or at, a vantage before anything moves it on. */
    static final float MIN_STAY_SECONDS = 25f;
    /** Cells within which another observer already watches a vantage. */
    static final float SPREAD_CELLS = 25f;
    /** Cells within which a team counts itself at, or still at, a vantage. */
    static final float AT_CELLS = 4f;
    /** Hostile combatants this close to the observer, and the team moves on. */
    static final float CLOSE_CELLS = 7f;
    /** Cells round its vantage a team holds. */
    static final int HOLD_RADIUS = 3;
    /** Soldiers in a team: the observer and his escort. */
    static final int TEAM_SIZE = 2;
    static final int PERIOD_TICKS = 30;

    /** The places a team may watch from, best first, as the mission ranks them now. */
    @FunctionalInterface
    public interface Vantages {
        List<int[]> ranked(BattleSimulation sim);
    }

    /** One team on the map: its squad, its observer, where it watches and since when. */
    private static final class Team {
        final int squad;
        final long observer;
        int[] vantage;
        int since;

        Team(int squad, long observer) {
            this.squad = squad;
            this.observer = observer;
        }
    }

    private final Faction side;
    private final float homeX, homeY;
    private final Vantages vantages;
    private final Random rng;
    private final List<Team> teams = new ArrayList<>();
    private int nextTeamTick;
    private int sent;
    private int moves;

    public RovingObservers(Faction side, float homeX, float homeY, Vantages vantages, long seed) {
        this(side, homeX, homeY, vantages, seed, Math.round(FIRST_TEAM_SECONDS / BattleSimulation.TICK_DT));
    }

    /** As above with the first team due at {@code firstTeamTick}: a unit test's way to skip the wait. */
    RovingObservers(Faction side, float homeX, float homeY, Vantages vantages, long seed, int firstTeamTick) {
        this.nextTeamTick = firstTeamTick;
        this.side = side;
        this.homeX = homeX;
        this.homeY = homeY;
        this.vantages = vantages;
        this.rng = new Random(seed);
    }

    public Faction side() {
        return side;
    }

    /** Teams sent up this battle. */
    public int sent() {
        return sent;
    }

    /** Times a team has set out for a vantage, its first included. */
    public int moves() {
        return moves;
    }

    /** The observers of the teams still roving, for evidence and look shots. */
    public List<Long> observers() {
        List<Long> out = new ArrayList<>();
        for (Team team : teams) out.add(team.observer);
        return out;
    }

    void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % PERIOD_TICKS != 0) return;
        World world = sim.world();
        teams.removeIf(team -> !world.isAlive(team.observer) || !CommsKit.carried(world, team.observer));
        List<int[]> ranked = vantages.ranked(sim);
        if (ranked.isEmpty()) return;
        if (mayRove(tick, nextTeamTick, teams.size(), observersOnMap(sim, side))) {
            Team team = send(sim);
            // Held by the allied cap, the team stays due and goes as soon as there is room.
            if (!heldByCap) nextTeamTick = tick + Math.round(TEAM_EVERY_SECONDS / BattleSimulation.TICK_DT);
            if (team != null) teams.add(team);
        }
        for (Team team : teams) {
            if (team.vantage != null && !movesOn(tick - team.since, arrived(sim, team), pressed(sim, team.observer))) continue;
            int[] next = pick(ranked, watchers(sim, team.observer), team.vantage, SPREAD_CELLS);
            if (next == null) continue;
            team.vantage = next;
            team.since = tick;
            moves++;
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(team.squad, next[0], next[1], HOLD_RADIUS),
                    CommandAuthority.GARRISON, ISSUER, "observer team moves to a new vantage");
        }
    }

    /**
     * Whether another team comes up now: its time has come, fewer than {@link #MOST_TEAMS} rove, and the map holds fewer
     * than {@link #CAP} of the side's observers, fixed and roving together.
     */
    static boolean mayRove(int tick, int nextTeamTick, int teams, int observersOnMap) {
        return tick >= nextTeamTick && teams < MOST_TEAMS && observersOnMap < CAP;
    }

    /**
     * Whether a team that set out for its vantage {@code ticksSince} ago moves on now: once it has watched its dwell
     * there, or once it has reached it and is pressed there; never sooner than {@link #MIN_STAY_SECONDS} after it set
     * out, so a team in a running fight is not forever on the move.
     */
    static boolean movesOn(int ticksSince, boolean arrived, boolean pressed) {
        if (ticksSince < Math.round(MIN_STAY_SECONDS / BattleSimulation.TICK_DT)) return false;
        return ticksSince >= Math.round(DWELL_SECONDS / BattleSimulation.TICK_DT) || arrived && pressed;
    }

    /** Whether the team's observer stands at its vantage. */
    private static boolean arrived(BattleSimulation sim, Team team) {
        return Math.hypot(sim.world().x(team.observer) - team.vantage[0], sim.world().y(team.observer) - team.vantage[1])
                <= AT_CELLS + HOLD_RADIUS;
    }

    /** Seats a team on the ground nearest home and gives its second man the radio; {@code null} when there is no ground. */
    /** Whether the last team due was held back by the allied cap. */
    private boolean heldByCap;

    private Team send(BattleSimulation sim) {
        // The allied cap: a team due while the side is at its infantry cap waits (the next look asks again).
        heldByCap = !sim.alliedCap().mayGo(sim, this, AlliedCap.Pool.INFANTRY, TEAM_SIZE);
        if (heldByCap) return null;
        List<int[]> cells = openCells(sim.getGrid(), Math.round(homeX), Math.round(homeY), TEAM_SIZE);
        if (cells.size() < TEAM_SIZE) return null;
        int squad = ColonyMilitia.seatTeam(sim, cells, "Observer team " + (sent + 1), RiskLevel.HIGH, rng);
        for (int i = 0, n = sim.squadMemberCount(squad); i < n; i++) {
            sim.alliedCap().enlist(sim, AlliedCap.Pool.INFANTRY, sim.squadMemberAt(squad, i));
        }
        long observer = ForwardObservers.issue(sim, side, List.of(squad));
        if (observer == 0L) return null;
        sent++;
        return new Team(squad, observer);
    }

    /**
     * The best of {@code ranked} that no other observer at {@code watched} stands within {@code spread} of and that is not
     * the vantage {@code current} the team already holds; {@code null} when every one is taken.
     */
    static int[] pick(List<int[]> ranked, List<float[]> watched, int[] current, float spread) {
        for (int[] v : ranked) {
            if (current != null && Math.hypot(v[0] - current[0], v[1] - current[1]) <= AT_CELLS) continue;
            boolean taken = false;
            for (float[] w : watched) {
                if (Math.hypot(v[0] - w[0], v[1] - w[1]) <= spread) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return v;
        }
        return null;
    }

    /** Where every other observer of the side stands now. */
    private List<float[]> watchers(BattleSimulation sim, long self) {
        List<float[]> out = new ArrayList<>();
        World world = sim.world();
        UnitRosterService roster = sim.getRoster();
        long[] units = roster.factionDenseArray(side);
        for (int i = 0, n = roster.factionLiveCount(side); i < n; i++) {
            long u = units[i];
            if (u == self || !world.isAlive(u) || !CommsKit.carried(world, u)) continue;
            out.add(new float[]{world.x(u), world.y(u)});
        }
        return out;
    }

    /** Whether the swarm is on the observer, or the fire on him is heavy enough to stop the radio. */
    private static boolean pressed(BattleSimulation sim, long observer) {
        CommsKitSpec spec = CommsKit.specOf(sim.world(), observer);
        if (spec != null && CommsOperators.suppressed(sim, observer, spec)) return true;
        return sim.getUnitIndex().countHostileCombatants(sim.world().x(observer), sim.world().y(observer),
                CLOSE_CELLS, sim.identity().faction(observer), observer) > 0;
    }

    /** Living soldiers of {@code side} carrying a radio. */
    static int observersOnMap(BattleSimulation sim, Faction side) {
        World world = sim.world();
        UnitRosterService roster = sim.getRoster();
        long[] units = roster.factionDenseArray(side);
        int n = 0;
        for (int i = 0, count = roster.factionLiveCount(side); i < count; i++) {
            if (world.isAlive(units[i]) && CommsKit.carried(world, units[i])) n++;
        }
        return n;
    }

    /** The {@code count} walkable cells nearest {@code (x, y)}, in rings outward; fewer when the map has none near. */
    static List<int[]> openCells(NavigationGrid grid, int x, int y, int count) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r <= 40 && out.size() < count; r++) {
            for (int dx = -r; dx <= r && out.size() < count; dx++) {
                for (int dy = -r; dy <= r && out.size() < count; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) out.add(new int[]{cx, cy});
                }
            }
        }
        return out;
    }
}
