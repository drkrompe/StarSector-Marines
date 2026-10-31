package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCap;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Scripted allied columns ({@code cordon-nouns.md}, Allied column): a few teams
 * of allied militia that come on at a map edge on the timetable and march,
 * under garrison authority, to the place that needs them most when they set
 * out. Mission-neutral: the mission says where the column enters, how it
 * picks where to go, and what joining means once it gets there.
 *
 * <p>Not the full allied column system (an allied commander choosing and
 * re-routing columns, `marine-side-reinforcement.md` applied to {@code ALLY}),
 * which is a later story; a column here knows only its destination, and is
 * sent on to the next one if its destination is lost before it arrives.
 */
public final class AlliedColumns {

    /** What a mission tells the columns. */
    public interface Orders {
        /** Where a column setting out now goes, {@code [x, y]}, or {@code null} when nowhere needs it. */
        int[] destination(BattleSimulation sim);
        /** Whether a destination is still worth marching to. */
        boolean stillNeeded(BattleSimulation sim, int x, int y);
        /** Whether a column member at {@code (x, y)} has arrived at the destination. */
        boolean arrived(int x, int y, int destX, int destY);
        /**
         * Whether a column that has gained no ground on its destination for {@link #STALL_SECONDS} has reached the
         * fight for it, and so joins there: an attack-moving column halts to shoot whatever comes into range, and a
         * destination that spawns its own defenders (a bore) is one it can only otherwise reach by winning. None does
         * by default.
         */
        default boolean holdsFront(BattleSimulation sim, List<Integer> squads, int destX, int destY) { return false; }
        /** The column has stepped onto the map: its squads are seated, before it is routed. */
        default void seated(BattleSimulation sim, int number, List<Integer> squads) { }
        /** The column has arrived: its squads are the mission's now. */
        void joined(BattleSimulation sim, int number, List<Integer> squads, int destX, int destY);
    }

    /** One column on the timetable: when it comes and where it comes on. */
    public record Departure(int seconds, int entryX, int entryY) { }

    private static final class Column {
        final int number;
        final Departure departure;
        final List<Integer> squads = new ArrayList<>();
        int destX = -1, destY = -1;
        boolean entered, done;
        final Progress progress = new Progress();

        Column(int number, Departure departure) {
            this.number = number;
            this.departure = departure;
        }
    }

    /** Seconds a marching column may gain no ground on its destination before it is asked whether it holds the front. */
    public static final float STALL_SECONDS = 30f;
    private static final int STALL_TICKS = Math.round(STALL_SECONDS / BattleSimulation.TICK_DT);

    /**
     * How a marching column is getting on: the nearest any member has come to its destination, and the tick it last
     * came a cell nearer. A column that is not gaining ground has stalled, whatever stopped it.
     */
    static final class Progress {
        private float closest = Float.MAX_VALUE;
        private int closerSince;

        /** A new destination: the column starts gaining ground on it afresh. */
        void reset(int tick) {
            closest = Float.MAX_VALUE;
            closerSince = tick;
        }

        /** The nearest member is {@code nearest} cells from the destination at {@code tick}. */
        void observe(int tick, float nearest) {
            if (nearest > closest - 1f) return;
            closest = nearest;
            closerSince = tick;
        }

        /** Whether the column has come no cell nearer for {@code ticks}. */
        boolean stalled(int tick, int ticks) {
            return tick - closerSince >= ticks;
        }
    }

    private final List<Column> columns = new ArrayList<>();
    private final int teams;
    private final String issuer;
    private final RiskLevel risk;
    private final Random rng;

    /**
     * @param teams  militia teams of {@link ColonyMilitia#POST_SIZE} in each column
     * @param issuer who orders the columns, as the command log names it
     */
    public AlliedColumns(List<Departure> departures, int teams, String issuer, RiskLevel risk, Random rng) {
        for (int i = 0; i < departures.size(); i++) columns.add(new Column(i + 1, departures.get(i)));
        this.teams = teams;
        this.issuer = issuer;
        this.risk = risk;
        this.rng = rng;
    }

    /** Columns that have joined their destination. */
    public int joined() {
        int n = 0;
        for (Column c : columns) if (c.done && !c.squads.isEmpty()) n++;
        return n;
    }

    /** Columns still to come or still marching: not joined, not wiped out. */
    public int remaining() {
        int n = 0;
        for (Column c : columns) if (!c.done) n++;
        return n;
    }

    /** Where each column on the march is bound, {x, y}: entered, not yet joined, with someone left in it. */
    public List<int[]> marching() {
        List<int[]> out = new ArrayList<>();
        for (Column c : columns) if (c.entered && !c.done && !c.squads.isEmpty()) out.add(new int[]{c.destX, c.destY});
        return out;
    }

    /**
     * Each column on the march, one line each, for a probe: its number, where it is bound, its squads and the
     * centre and nearest member to its destination, in cells.
     */
    public List<String> describe(BattleSimulation sim) {
        List<String> out = new ArrayList<>();
        for (Column c : columns) {
            if (!c.entered || c.done || c.squads.isEmpty()) continue;
            float sx = 0f, sy = 0f, nearest = Float.MAX_VALUE;
            int n = 0;
            for (int squad : c.squads) {
                for (int i = 0, m = sim.squadMemberCount(squad); i < m; i++) {
                    long member = sim.squadMemberAt(squad, i);
                    float x = sim.world().x(member), y = sim.world().y(member);
                    sx += x;
                    sy += y;
                    n++;
                    nearest = Math.min(nearest, (float) Math.hypot(x - c.destX, y - c.destY));
                }
            }
            out.add("column " + c.number + " to " + c.destX + "," + c.destY + " squads " + c.squads
                    + (n == 0 ? "" : String.format(" at %.0f,%.0f nearest %.0f", sx / n, sy / n, nearest)));
        }
        return out;
    }

    /** One tick: due columns come on, marching columns are checked for arrival and re-routed if needed. */
    public void advance(BattleSimulation sim, int tick, Orders orders, BeatSink beat) {
        for (Column column : columns) {
            if (column.done) continue;
            if (!column.entered) {
                if (tick < CordonSequence.ticks(column.departure.seconds())) continue;
                // The allied cap: a column due while the side is at its infantry cap waits its turn for room.
                if (!sim.alliedCap().mayGo(sim, column, AlliedCap.Pool.INFANTRY, teams * ColonyMilitia.POST_SIZE)) continue;
                int[] dest = orders.destination(sim);
                if (dest == null) continue;
                enter(sim, column, dest, orders);
                beat.emit("column_entered_" + column.number, true);
                continue;
            }
            column.squads.removeIf(squad -> sim.squadMemberCount(squad) == 0);
            if (column.squads.isEmpty()) {
                column.done = true;
                continue;
            }
            if (!orders.stillNeeded(sim, column.destX, column.destY)) {
                int[] dest = orders.destination(sim);
                if (dest == null) continue;
                route(sim, column, dest);
            }
            column.progress.observe(tick, nearest(sim, column));
            if (anyArrived(sim, column, orders) || column.progress.stalled(tick, STALL_TICKS)
                    && orders.holdsFront(sim, List.copyOf(column.squads), column.destX, column.destY)) {
                column.done = true;
                orders.joined(sim, column.number, List.copyOf(column.squads), column.destX, column.destY);
                beat.emit("column_arrived_" + column.number, false);
            }
        }
    }

    /** Where a column's beats go: the mission's story log, with whether the beat is for the observer only. */
    public interface BeatSink {
        void emit(String beat, boolean observerOnly);
    }

    private void enter(BattleSimulation sim, Column column, int[] dest, Orders orders) {
        column.entered = true;
        List<int[]> cells = CordonGround.cluster(sim.getGrid(), sim.getOccupancyMap(),
                column.departure.entryX(), column.departure.entryY(), teams * ColonyMilitia.POST_SIZE);
        for (int t = 0; t + ColonyMilitia.POST_SIZE <= cells.size() && t / ColonyMilitia.POST_SIZE < teams;
             t += ColonyMilitia.POST_SIZE) {
            column.squads.add(ColonyMilitia.seatTeam(sim, cells.subList(t, t + ColonyMilitia.POST_SIZE),
                    "Column " + column.number + " team " + (t / ColonyMilitia.POST_SIZE + 1), risk, rng));
        }
        for (int squad : column.squads) {
            for (int i = 0, n = sim.squadMemberCount(squad); i < n; i++) {
                sim.alliedCap().enlist(sim, AlliedCap.Pool.INFANTRY, sim.squadMemberAt(squad, i));
            }
        }
        orders.seated(sim, column.number, List.copyOf(column.squads));
        route(sim, column, dest);
    }

    private void route(BattleSimulation sim, Column column, int[] dest) {
        if (dest[0] != column.destX || dest[1] != column.destY) column.progress.reset(sim.getSimTickIndex());
        column.destX = dest[0];
        column.destY = dest[1];
        for (int squad : column.squads) {
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, dest[0], dest[1]),
                    CommandAuthority.GARRISON, issuer, "allied column " + column.number + " marches up");
        }
    }

    /** Cells from the column's destination to its nearest member. */
    private static float nearest(BattleSimulation sim, Column column) {
        float best = Float.MAX_VALUE;
        for (int squad : column.squads) {
            for (int i = 0, m = sim.squadMemberCount(squad); i < m; i++) {
                long member = sim.squadMemberAt(squad, i);
                best = Math.min(best, (float) Math.hypot(sim.world().x(member) - column.destX, sim.world().y(member) - column.destY));
            }
        }
        return best;
    }

    private static boolean anyArrived(BattleSimulation sim, Column column, Orders orders) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            // Turrets and bores carry no squad: ask for one rather than reading its id.
            Squad squad = sim.squadOf(unit);
            if (squad == null || !column.squads.contains(squad.id)) continue;
            if (orders.arrived(sim.world().cellX(unit), sim.world().cellY(unit), column.destX, column.destY)) return true;
        }
        return false;
    }
}
