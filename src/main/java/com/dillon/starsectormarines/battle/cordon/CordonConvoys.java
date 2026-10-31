package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * The Road's relief convoys ({@code cordon-nouns.md}, Convoy): columns of allied trucks that set out
 * from the logistics base where the corridor passes it (from the corridor's rear end on a map without
 * one) on the timetable, drive it through the gap to the cut-off town's compound, wait at its gate
 * while their share of the townspeople board, and drive back to where they set out.
 *
 * <p>A convoy does not fight its way through. A truck halts while the swarm is on the road a few
 * cells ahead of it and goes on when the road is clear, so a convoy whose road nobody clears is eaten
 * where it stands. Past the gap, in the swarm's country, it rolls only with an escort beside it: the
 * allied armour drives ahead of it ({@link #escorts}), the company walks ahead of one the armour has
 * not reached ({@link #unguarded}), and a truck nobody escorts waits at the line. The townspeople stand
 * in the compound ({@link CordonTownspeople}); while a convoy loads they walk out to its trucks at the
 * gate and board one at a time, and it turns for home once its trucks are full or the town is empty, or
 * at the loading's cap. A truck that is destroyed takes its passengers with it.
 *
 * <p>A truck drives a route laid along the corridor once, with ordinary steering and recovery: each
 * stretch of the road a drivable route the planner proves for a truck, and where it proves none (a
 * country lane's corner tighter than it will prove a truck's turn through) a way its footprint fits
 * along, or the road's own cells. A
 * truck that makes no headway on an open route is proved a fresh way from where it stands to the road a
 * stretch further on (a raw stretch can pin it on a wall's corner), and given the rest of the route
 * from there; where none is proved, the rest of it again from where it stands.
 */
public final class CordonConvoys {

    /** Trucks in a convoy: eighteen seats a trip, so the town's forty-eight take three trips (owner, 2026-10-31). */
    public static final int TRUCKS = 3;
    /**
     * Convoys in the fleet: two, staggered, so one loads while the other drives. The fleet's {@code FLEET} by
     * {@link #TRUCKS} trucks are all the trucks the road ever has: they cycle, trip after trip ({@link CordonFleet}).
     */
    public static final int FLEET = 2;
    /** Townspeople a truck carries out. */
    public static final int SEATS = 6;
    /** The most round trips the fleet drives to the town: five. */
    public static final int MAX_CONVOYS = 5;
    /**
     * How long after a truck is destroyed its replacement is put on the road at the camp's parking line, to join the
     * fleet's next trip: a lost truck is made good once, and the fleet never grows past its size. Untuned.
     */
    static final int REPLACE_TICKS = Math.round(45f / BattleSimulation.TICK_DT);
    /**
     * The people trapped in the town: four full trips of {@link #TRUCKS} by {@link #SEATS}, so getting everyone out
     * takes more than three convoys, and the fifth is there for the seats lost to a truck or a slow loading.
     */
    public static final int TOWNSPEOPLE = 48;
    /** Corridor cells from either end of the road a truck counts as there. */
    static final int AT_LEG = 5;
    /** Corridor cells ahead of a truck the road is looked at for the swarm. */
    static final int LOOK_AHEAD = 12;
    /** Cells from the road a swarm body blocks it. */
    static final int BLOCK_REACH = 6;
    /** Cells from a truck a swarm body has reached it. */
    static final int AMBUSH_REACH = 3;
    /** Corridor cells the truck ahead must be along before the next comes on behind it. */
    static final int SPACING = 8;
    /** Corridor cells from the road's end a loading convoy's truck behind the first is at the gate: drawn up behind it. */
    static final int GATE_QUEUE = AT_LEG + SPACING + 2;
    /** Corridor cells between one truck's place in a queue and the next's behind it. */
    static final int QUEUE_STEP = SPACING + 2;
    /** Corridor cells short of the camp's middle the last truck of a convoy draws up behind the others to unload. */
    static final int CAMP_QUEUE = GATE_QUEUE + (TRUCKS - 2) * QUEUE_STEP;
    /**
     * Cells short of the yard's stop a truck driving into the town's truck yard is at it: it stands there to load,
     * turned round the yard and facing out (where {@link #AT_LEG} would stop it mid-turn).
     */
    static final int YARD_STOP = 1;
    /** Where the truck behind draws up in the yard: its spacing back round the yard's loop from the one at the stop. */
    static final int YARD_QUEUE = YARD_STOP + SPACING + 2;
    /** Corridor cells short of the camp's middle a truck coming home stops at. */
    static final int CAMP_STOP = 2;
    /** Cells between two trucks waiting in the camp's parking line, and from the drop-off stop to the line's first. */
    static final int PARK_STEP = 6, PARK_CLEAR = 8;
    /** Places in the camp's parking line: the whole fleet. */
    static final int PARK_PLACES = FLEET * TRUCKS;
    /** Cells from its place a truck pulling into the parking line is parked. */
    static final int PARKED_WITHIN = 2;
    /** Corridor cells short of the yard's gate its tight stretch begins: the junction and the line-up into the gate. */
    static final int YARD_APPROACH = 10;
    /** Cells across the road under which two trucks cannot pass: a narrow stretch, one way at a time. */
    static final int PASSING = 6;
    /** Cells out from the road's middle its width is measured, either way. */
    static final int WIDTH_REACH = 6;
    /** Corridor cells past the entry a truck may be set down at, for ground it can set out from. */
    static final int SET_DOWN_REACH = 8;
    /**
     * The most ticks a convoy waits at the gate (past a beat's extra, R3) before it turns for home with whoever has
     * boarded: a minute and a half, for three trucks of people walking out from the rooms (two trucks took 52 s of a
     * 60 s cap in play). It also bounds the wait for a truck still on its way there, which turns back with whoever
     * reached it, or empty.
     */
    static final int LOADING_CAP_TICKS = Math.round(90f / BattleSimulation.TICK_DT);
    /** Ticks between two townspeople climbing aboard one truck: two seconds. */
    static final int BOARD_TICKS = Math.round(2f / BattleSimulation.TICK_DT);
    /**
     * Corridor cells in from the rear edge a truck comes on at, and leaves from, with no base on the
     * road: a hull set down on the edge row has no route out of it.
     */
    public static final int ENTRY = 6;
    /** Ticks between looks at the road. */
    static final int REVIEW_TICKS = 15;
    /** Cells from a truck a company marine must be for it to roll past the gap. */
    static final int ESCORT_REACH = 24;
    /**
     * Cells from a truck an armed allied hull must be for it to roll past the gap: further than a
     * marine, since a hull's post beside the road is short of where it was asked to stand wherever
     * the ground beside the road is broken, and its gun reaches further.
     */
    static final int GUARD_REACH = 32;
    /** Cells off the road's middle an armoured escort drives, clear of the trucks' way. */
    static final int GUARD_SIDE = 5;
    /** Corridor cells further ahead a third armoured escort drives. */
    static final int GUARD_POINT = 8;
    /** Corridor cells short of the gap where the escort is first wanted. */
    static final int ESCORT_FROM = 12;
    /** Corridor cells ahead of a convoy's lead truck its escort walks. */
    static final int ESCORT_LEAD = 14;
    /** Corridor cells in each stretch the road's route is proved over. */
    static final int STRETCH = 30;
    /** Ticks (five seconds) a truck may make no headway along an open route before it is given a way on again. */
    static final int STUCK_TICKS = Math.round(5f / BattleSimulation.TICK_DT);
    public enum Phase { WAITING, INBOUND, LOADING, OUTBOUND, OUT, LOST }

    /** What holds a convoy on the road: nothing, the want of an escort past the gap, or the swarm on the road ahead. */
    public enum Hold { NONE, ESCORT, BLOCKED }

    /**
     * One convoy as the mission picture reads it: its phase, what holds it, the tick it sets out on, where its
     * truck furthest along its way stands ({@code x} and {@code y} are -1 while none is on the road), and how many
     * townspeople are aboard its live trucks out of their seats.
     */
    public record Reading(int number, Phase phase, Hold hold, int departTick, int x, int y, int aboard, int seats) {
        public Reading(int number, Phase phase, Hold hold, int departTick, int x, int y) {
            this(number, phase, hold, departTick, x, y, 0, 0);
        }
    }

    private static final class Truck implements CordonTownspeople.Seat {
        /** The convoy it drives in; {@code null} while it waits at the camp for the next trip. */
        Convoy convoy;
        long hull;
        /** The tick it takes the next townsperson aboard from. */
        int nextBoardTick;

        Truck(Convoy convoy) {
            this.convoy = convoy;
        }

        @Override
        public float[] at(BattleSimulation sim) {
            GroundBody body = sim.convoy().body(hull);
            return new float[]{body.x, body.y};
        }

        @Override
        public int free() { return SEATS - people; }

        @Override
        public boolean opens(int tick) { return tick >= nextBoardTick; }

        @Override
        public void board(int tick) {
            people++;
            nextBoardTick = tick + boardTicks(convoy.extraLoading);
        }

        /** Which way along the corridor its route runs: +1 to the town, -1 out. */
        int direction;
        int people;
        boolean gone;
        /** Taken off the road by a beat (R2) with its people walking on: gone, but not destroyed. */
        boolean stranded;
        boolean halted;
        /** Held past the gap for want of an escort. */
        boolean unescorted;
        /** At the compound's gate, where its people board. */
        boolean atGate;
        /** Stopped in the camp while its people climb down, and the tick the next one does. */
        boolean unloading, unloaded;
        int nextUnloadTick;
        /** Waiting at the camp for the next trip: in its place in the parking line, and stopped there. */
        boolean parked;
        /** Its place in the camp's parking line, or -1. */
        int place = -1;
        /** Where the road ahead of a halted truck is blocked. */
        int blockX, blockY;
        /** The furthest corridor cell along its way it has reached, and the tick it last went further, was held or was given its route. */
        int bestAt = -1;
        int movedTick;
    }

    private static final class Convoy {
        final int number;
        /** The tick it set out on; -1 while it has not been called. */
        int departTick;
        Phase phase = Phase.WAITING;
        final List<Truck> trucks = new ArrayList<>();
        int loadingSince;
        /** Ticks added to its loading by a beat (R3). */
        int extraLoading;
        int people;
        boolean throughGap, ambushed;
        /** Where a swarm body stands on one of its trucks at the last look, or {@code null}. */
        int[] reachedAt;

        Convoy(int number, int departTick) {
            this.number = number;
            this.departTick = departTick;
        }
    }

    private List<int[]> corridor;
    /** The town's truck yard the convoys drive into, or {@code null}: they then stop at the road's end. */
    private CordonGateYard yard;
    /** The corridor's index of the yard's gate, where its loop begins; -1 with no yard. */
    private int yardFrom = -1;
    /** The corridor cell trucks set out from and come back to. */
    private int entry;
    /** A detour waiting for the road to be empty (beat R5): its cells, gap and entry, or {@code null}. */
    private List<int[]> detour;
    private int detourGap, detourEntry;
    /** The town militia's squads, for the beat that rides them out (R4). */
    private List<Integer> townMilitia = List.of();
    /** The route to the town and the route out, laid along the corridor on first use. */
    private float[][] inward, outward;
    /** Where a truck's footprint fits, and what ground costs it; made on first use. */
    private VehicleClearance clearance;
    private TerrainCostField cost;
    private int gapIndex;
    private final List<Convoy> convoys = new ArrayList<>();
    /** The trucks: a fixed fleet that cycles, a lost one made good once. */
    private final CordonFleet fleet = new CordonFleet(FLEET * TRUCKS, REPLACE_TICKS);
    /** The road's tight stretches and who holds them; made on first use. */
    private CordonTraffic traffic;
    /** By convoy number, the corridor cell its trucks hold short of for traffic, from the last look. */
    private Map<Integer, Integer> trafficHolds = Map.of();
    /** The camp's parking line: where its waiting trucks stand, nearest the drop-off first; made on first use. */
    private List<int[]> places;
    /** The townspeople still in the town. */
    private CordonTownspeople town;
    /** The town's people at the start. */
    private final int peopleTotal;
    private int peopleOut, peopleLost;
    /** The tick the first convoy sets out on; the rest are called while people are still trapped. */
    private final int firstDepartTick;
    /** Whether the town has been given up ({@link #abandonTown}). */
    private boolean abandoned;
    /** Ticks a beat adds to the last convoy's loading (R3), given to it when it reaches the gate. */
    private int lastExtra;
    /** The convoy known to be the last, from when it reached the gate; 0 until then. */
    private int lastConvoy;
    /** Beats published from outside {@link #advance}, sent with the next. */
    private final List<String> pending = new ArrayList<>();
    /** The trucks at the gate with seats free, made each tick. */
    private final List<CordonTownspeople.Seat> seats = new ArrayList<>();
    /** The safe camp the convoys bring the people to, or {@code null} on a map with none (they count out at the entry). */
    private CordonCamp camp;
    /** The people brought out, waiting in the camp. */
    private final CordonTownspeople sheltered = CordonTownspeople.counted(0);
    /** Trucks back from a trip and unloaded, waiting at the camp for the next. */
    private final List<Truck> pool = new ArrayList<>();
    /** The company's marines at the last look. */
    private List<float[]> company = List.of();
    /** The allies' armed hulls at the last look. */
    private List<float[]> guards = List.of();
    /** The armour's escort for the convoy whose posts came {@code i}th at its last look; {@code null} unwired. */
    private IntFunction<int[]> armourEscort;
    /** The convoys whose posts {@link #guardPosts} last gave the armour, by number, in that order. */
    private final List<Integer> guardConvoys = new ArrayList<>();

    /**
     * @param corridor the relief road's cells in driving order, rear edge first
     * @param entry the corridor cell trucks set out from and come back to: where it passes the base
     * @param gapIndex the corridor cell where it crosses the line
     * @param firstDepartTick when the first convoy sets out
     * @param maxConvoys the most convoys that may come ({@link #MAX_CONVOYS})
     * @param townspeople the people trapped in the town
     */
    public CordonConvoys(List<int[]> corridor, int entry, int gapIndex, int firstDepartTick, int maxConvoys,
                         int townspeople) {
        this.corridor = List.copyOf(corridor);
        this.entry = entry;
        this.gapIndex = gapIndex;
        this.firstDepartTick = firstDepartTick;
        for (int i = 0; i < maxConvoys; i++) convoys.add(new Convoy(i + 1, -1));
        this.peopleTotal = Math.max(0, townspeople);
        this.town = CordonTownspeople.counted(townspeople);
    }

    /**
     * Whether the next convoy, number {@code number}, sets out now: never past {@code max}, to a town given up, or
     * while the convoys already on their way have seats for everyone still trapped; the first on its tick, each
     * after it once the one before has passed the gap toward the town (so two are never queued at the line, and one
     * lost on the road is followed).
     *
     * @param previousOn  whether the convoy before it has passed the gap inbound, or is further on (loading, home or lost)
     * @param trapped     the people still trapped in the town
     * @param seatsComing the free seats on the convoys on their way to the town or loading at its gate
     */
    static boolean setsOut(int number, int max, int tick, int firstTick, boolean previousOn, int trapped,
                           int seatsComing, boolean abandoned) {
        if (abandoned || number > max || trapped <= seatsComing) return false;
        return number == 1 ? tick >= firstTick : previousOn;
    }

    /**
     * Whether a convoy reaching the gate is the last that will come: the last there can be, or one with seats for
     * everyone still trapped.
     */
    static boolean last(int number, int max, int trapped, int seats) {
        return number >= max || trapped <= seats;
    }

    /**
     * Stands the compound's people up inside it, round {@link CordonTownspeople#DEPTH} cells past the gate toward the
     * front ({@code frontward} +1 or -1 in y), behind the militia; at setup, after the militia are seated.
     */
    public void standTownspeople(BattleSimulation sim, int frontward) {
        if (corridor.isEmpty()) return;
        int[] gate = corridor.get(corridor.size() - 1);
        standTownspeople(sim, CordonGround.clusterNear(sim, gate[0], gate[1] + frontward * CordonTownspeople.DEPTH,
                town.left()));
    }

    /**
     * Stands the town's people up on {@code cells}, the staging area they are trapped in, nearest its middle first;
     * any there is no open cell for stay a count. At setup, after the militia are seated.
     */
    public void standTownspeople(BattleSimulation sim, List<int[]> cells) {
        town = CordonTownspeople.stand(sim, cells, town.left());
    }

    /**
     * Stands the compound's people in groups: {@code counts[i]} in {@code groups.get(i)}, the staging area by the
     * yard and the rooms round the compound; at setup, after the defenders are seated.
     */
    public void standTownspeople(BattleSimulation sim, List<List<int[]>> groups, int[] counts) {
        town = CordonTownspeople.stand(sim, groups, counts, town.left());
    }

    /**
     * Each convoy's boarding as {aboard, seats} on its live trucks, in departure order, read-only: for a convoy
     * status such as "boarding 5/12" ({@link Reading#aboard()} and {@link Reading#seats()} carry the same).
     */
    public List<int[]> boarding(BattleSimulation sim) {
        List<int[]> out = new ArrayList<>();
        for (Reading r : readings(sim)) out.add(new int[]{r.aboard(), r.seats()});
        return out;
    }

    public int convoys() { return convoys.size(); }

    /** The corridor cell trucks set out from and come back to. */
    public int entry() { return entry; }

    /** The corridor the convoys drive now, rear edge first. */
    public List<int[]> corridor() { return corridor; }

    /**
     * Has the company read whether the armour escorts a convoy from the escort the armour gave it
     * ({@link CordonAlliedRules#convoyEscorted}) rather than from any armed hull standing near a truck.
     */
    public void escortedBy(IntFunction<int[]> armourEscort) {
        this.armourEscort = armourEscort;
    }

    /** The town militia's squads, set at setup. */
    public List<Integer> townMilitia() { return townMilitia; }

    public void setTownMilitia(List<Integer> squads) {
        townMilitia = List.copyOf(squads);
    }

    /** Convoy {@code number}'s phase in a word ({@code waiting}, {@code inbound}, ...), or {@code null}. */
    public String phase(int number) {
        if (number < 1 || number > convoys.size()) return null;
        return convoys.get(number - 1).phase.name().toLowerCase(Locale.ROOT);
    }

    /** Convoy {@code number} waits this many more ticks at the gate while its people board (beat R3). */
    public void extendLoading(int number, int ticks) {
        if (number >= 1 && number <= convoys.size()) convoys.get(number - 1).extraLoading += Math.max(0, ticks);
    }

    /**
     * The last convoy waits this many more ticks at the gate (beat R3): given to the convoy found to be the last when
     * it reaches the gate ({@link #lastConvoy}), or at once to one already loading there as the last.
     */
    public void extendLastLoading(int ticks) {
        lastExtra += Math.max(0, ticks);
        if (lastConvoy > 0) extendLoading(lastConvoy, Math.max(0, ticks));
    }

    /** The number of the convoy known to be the last, from when it reached the gate; 0 until one has. */
    public int lastConvoy() { return lastConvoy; }

    /**
     * The town is given up (its staging area overrun, or the company pulled out): every townsperson still trapped
     * dies, taken off the field and counted lost; no convoy sets out again; one on its way to the town or loading at
     * its gate turns for home with whoever is aboard. The sequence publishes {@code town_abandoned} as it calls this
     * ({@link CordonSequence#onAbandonTown}). Idempotent.
     */
    public void abandonTown(BattleSimulation sim) {
        if (abandoned) return;
        abandoned = true;
        town.abandon(sim);
        for (Convoy c : convoys) {
            if (c.phase != Phase.INBOUND && c.phase != Phase.LOADING) continue;
            if (c.phase == Phase.LOADING) pending.add("convoy_loaded_" + c.number);
            c.phase = Phase.OUTBOUND;
            for (Truck t : c.trucks) {
                if (t.gone || !sim.convoy().isVehicle(t.hull)) continue;
                t.atGate = false;
                route(sim, t, -1);
            }
        }
        prune();
    }

    /** Whether the town has been given up. */
    public boolean townAbandoned() { return abandoned; }

    /**
     * The convoys set out from and come back to {@code camp}: their entry is the corridor cell it stands on, the people
     * step off there, and the trucks wait there between trips. At setup, before the first tick.
     */
    public void shelterAt(CordonCamp camp) {
        this.camp = camp;
        if (camp != null) entry = camp.dropIndex();
    }

    /** The safe camp, or {@code null}: read-only, for a beacon. */
    public CordonCamp camp() { return camp; }

    /** The people brought out waiting in the camp, standing on the field. */
    public int sheltered() { return sheltered.onField(); }

    /**
     * A townsperson who walked to the camp (R2's passengers) arrives: out, and waiting in the camp with the others.
     * Taken off the field where there is no camp.
     */
    public void arrived(BattleSimulation sim, long id) {
        peopleOut++;
        if (camp == null) {
            sim.clearPath(id);
            sim.takeOffTheField(id);
            return;
        }
        int[] home = camp.nextHome();
        sim.clearPath(id);
        sheltered.admit(id, home[0], home[1]);
    }

    /**
     * The convoys that will never set out come off the end of the list: every one still waiting once the town is
     * empty or given up, and one turned for home before any truck of it came on.
     */
    private void prune() {
        boolean done = abandoned || town.left() == 0;
        for (int i = convoys.size() - 1; i >= 0; i--) {
            Convoy c = convoys.get(i);
            boolean never = c.phase == Phase.WAITING && done || c.phase == Phase.OUTBOUND && c.trucks.isEmpty();
            if (!never) break;
            convoys.remove(i);
        }
    }

    /** Calls the next convoy when {@link #setsOut} says it goes. */
    private void callNext(int tick, Consumer<String> beat) {
        int trips = MAX_CONVOYS;
        for (int i = 0; i < convoys.size(); i++) {
            Convoy c = convoys.get(i);
            if (c.phase != Phase.WAITING) continue;
            Convoy before = i == 0 ? null : convoys.get(i - 1);
            boolean previousOn = before == null || before.throughGap || before.phase != Phase.WAITING
                    && before.phase != Phase.INBOUND;
            int seatsComing = 0;
            for (Convoy o : convoys) seatsComing += seatsComing(o);
            // The fleet's trucks: a trip sets out with trucks waiting at the camp, or the fleet's still to come on.
            if (fleet.ready(pool.size(), tick) < TRUCKS) return;
            if (setsOut(c.number, trips, tick, firstDepartTick, previousOn, town.left(), seatsComing, abandoned)) {
                c.phase = Phase.INBOUND;
                c.departTick = tick;
                beat.accept("convoy_entered_" + c.number);
            }
            return;
        }
    }

    /** The free seats a convoy brings to the town: on its way there or loading at its gate. */
    private static int seatsComing(Convoy c) {
        if (c.phase != Phase.INBOUND && c.phase != Phase.LOADING) return 0;
        int seats = 0;
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            seats += SEATS - t.people;
        }
        // Trucks still to come on at the rear end.
        if (c.phase == Phase.INBOUND) seats += Math.max(0, TRUCKS - c.trucks.size()) * SEATS;
        return seats;
    }

    /**
     * Where truck {@code index} (from 0) of convoy {@code number} is: {corridor cell, people aboard, x, y}, or
     * {@code null} when it has not come on or is gone.
     */
    public int[] truck(BattleSimulation sim, int number, int index) {
        if (number < 1 || number > convoys.size()) return null;
        Convoy c = convoys.get(number - 1);
        if (index < 0 || index >= c.trucks.size()) return null;
        Truck t = c.trucks.get(index);
        if (t.gone || !sim.convoy().isVehicle(t.hull) || sim.convoy().structure(t.hull) <= 0f) return null;
        GroundBody body = sim.convoy().body(t.hull);
        return new int[]{nearestIndex(body), t.people, (int) body.x, (int) body.y};
    }

    /**
     * A beat takes truck {@code index} of convoy {@code number} off the road (R2): it is gone without being
     * destroyed, so its people are not lost with it, and they are handed back to walk. Returns how many were
     * aboard, or -1 when there is no such truck on the road. The hull is taken off, not left as a wreck, so the
     * corridor stays open behind it.
     */
    public int strand(BattleSimulation sim, int number, int index) {
        if (number < 1 || number > convoys.size()) return -1;
        Convoy c = convoys.get(number - 1);
        if (index < 0 || index >= c.trucks.size()) return -1;
        Truck t = c.trucks.get(index);
        if (t.gone) return -1;
        int people = t.people;
        t.people = 0;
        t.gone = true;
        t.stranded = true;
        sim.convoy().despawn(t.hull);
        fleet.lose(sim.getSimTickIndex());
        return people;
    }

    /** Townspeople who walked out from a stranded truck (R2) are out. */
    public void walkedOut(int people) {
        peopleOut += Math.max(0, people);
    }

    /** Townspeople who died walking from a stranded truck (R2) are lost. */
    public void walkedLost(int people) {
        peopleLost += Math.max(0, people);
    }

    /**
     * The road has changed under the trucks (a wreck pushed clear, beat R1): every route is laid again from the
     * live map, and each truck on the road is handed its rest from where it stands.
     */
    public void relay(BattleSimulation sim) {
        if (clearance == null) return;
        clearance = null;
        ground(sim);
        for (Convoy c : convoys) {
            for (Truck t : c.trucks) {
                if (t.gone || !sim.convoy().isVehicle(t.hull)) continue;
                route(sim, t, t.direction == 0 ? 1 : t.direction);
            }
        }
    }

    /**
     * The convoys drive on into the town's truck yard ({@code cordon-nouns.md}, Cut-off town): in through its gate,
     * round the yard and to its stop beside the staging area, where they load facing out, then forward out of the gate.
     * The corridor is carried on into the yard ({@link CordonGateYard#extend}); where no truck can reach the gate from
     * its end it is left as it is and the convoys stop at the road's end. At setup, before the first tick.
     */
    public void driveInto(CordonGateYard yard, NavigationGrid grid) {
        this.yard = yard;
        intoYard(grid);
    }

    /** Carries the corridor on into the yard, where there is one and a truck can reach it. */
    private void intoYard(NavigationGrid grid) {
        yardFrom = -1;
        if (yard == null) return;
        CordonGateYard.Extended extended = yard.extend(corridor, (x, y) -> CordonCorridor.drivable(grid, x, y));
        if (extended == null) return;
        corridor = List.copyOf(extended.cells());
        yardFrom = extended.gateIndex();
        clearance = null;
        inward = outward = null;
        traffic = null;
    }

    /** Cells short of the corridor's end a truck is at the gate, to load: at the yard's stop where it drives in. */
    private int arrival() {
        return yardFrom >= 0 ? YARD_STOP : AT_LEG;
    }

    /** Cells short of the corridor's end the truck behind the one at the gate draws up. */
    private int queue() {
        return yardFrom >= 0 ? YARD_QUEUE : GATE_QUEUE;
    }

    /**
     * The convoys take another road (beat R5's detour) from the first moment none is on the road: one on it
     * keeps the road it is on, and the next to set out drives the detour.
     */
    public void detour(List<int[]> road, int gap, int from) {
        detour = List.copyOf(road);
        detourGap = gap;
        detourEntry = from;
    }

    /** Whether the convoys drive a detour now. */
    public boolean detoured() { return detourApplied; }

    private boolean detourApplied;

    private void applyDetour(BattleSimulation sim) {
        if (detour == null) return;
        for (Convoy c : convoys) {
            if (c.phase == Phase.INBOUND || c.phase == Phase.LOADING || c.phase == Phase.OUTBOUND) return;
        }
        corridor = detour;
        gapIndex = detourGap;
        entry = camp != null ? nearestCell(camp.centreX(), camp.centreY()) : detourEntry;
        detour = null;
        detourApplied = true;
        clearance = null;
        inward = outward = null;
        traffic = null;
        places = null;
        intoYard(sim.getGrid());
    }
    /** Every convoy, in departure order, as the mission picture reads it; read-only. */
    public List<Reading> readings(BattleSimulation sim) {
        List<Reading> out = new ArrayList<>();
        for (Convoy c : convoys) {
            Hold hold = Hold.NONE;
            int x = -1, y = -1, best = Integer.MIN_VALUE, aboard = 0, seated = 0;
            for (Truck t : c.trucks) {
                if (t.gone || !sim.convoy().isVehicle(t.hull)) continue;
                aboard += t.people;
                seated += SEATS;
                if (t.halted) hold = Hold.BLOCKED;
                else if (t.unescorted && hold == Hold.NONE) hold = Hold.ESCORT;
                GroundBody body = sim.convoy().body(t.hull);
                // Furthest along its way: toward the town inbound, toward home outbound.
                int along = c.phase == Phase.OUTBOUND ? -nearestIndex(body) : nearestIndex(body);
                if (along > best) {
                    best = along;
                    x = (int) body.x;
                    y = (int) body.y;
                }
            }
            out.add(new Reading(c.number, c.phase, hold, c.departTick, x, y, aboard, seated));
        }
        return out;
    }

    /** Convoys back out with their people. */
    public int out() { return count(Phase.OUT); }
    /** Convoys whose every truck was destroyed. */
    public int lost() { return count(Phase.LOST); }
    /** The town's people at the start: everyone there was to bring out. */
    public int peopleTotal() { return peopleTotal; }
    /**
     * Townspeople brought out so far: stepped off at the camp (or, with none, driven back to the entry). One killed at
     * the camp afterwards still counts: the company's job was to get them there ({@link #peopleOut(int, int)}).
     */
    public int peopleOut() { return peopleOut(peopleOut, sheltered.lost()); }

    /**
     * The people out: every one who stepped off at the drop-off, whatever befell them there since (owner: once they
     * reach the drop-off point they do not count against the company). Pure.
     */
    static int peopleOut(int steppedOff, int killedAtCamp) {
        return steppedOff;
    }

    /** The people lost: killed before they reached the drop-off, on the road or in the town; never at the camp. Pure. */
    static int peopleLost(int onTheRoad, int inTheTown, int killedAtCamp) {
        return onTheRoad + inTheTown;
    }
    /** Townspeople killed: aboard a destroyed truck, walking from a stranded one, or in the town before they boarded. */
    public int peopleLost() { return peopleLost(peopleLost, town.lost(), sheltered.lost()); }
    /** Townspeople still trapped in the town, alive and not boarded: in the staging area or walking out to a truck. */
    public int town() { return town.left(); }
    /** Townspeople standing on the field in the town, for evidence. */
    public int townOnField() { return town.onField(); }

    private int count(Phase phase) {
        int n = 0;
        for (Convoy c : convoys) if (c.phase == phase) n++;
        return n;
    }

    /**
     * Each convoy on the road in a word, for evidence: its number, phase, how far along the road its
     * lead truck is, and why it is held ({@code blocked} by the swarm, waiting for an {@code escort}).
     */
    public String describe(BattleSimulation sim) {
        StringBuilder out = new StringBuilder();
        for (Convoy c : convoys) {
            if (c.phase == Phase.WAITING || c.phase == Phase.OUT || c.phase == Phase.LOST) continue;
            int lead = -1, aboard = 0;
            boolean blocked = false, waiting = false;
            float marine = Float.MAX_VALUE, guard = Float.MAX_VALUE;
            for (Truck t : c.trucks) {
                if (t.gone) continue;
                GroundBody body = sim.convoy().body(t.hull);
                int at = nearestIndex(body);
                lead = Math.max(lead, at);
                aboard += t.people;
                marine = Math.min(marine, nearest(body, company));
                guard = Math.min(guard, nearest(body, guards));
                blocked |= t.halted;
                waiting |= t.unescorted;
            }
            if (out.length() > 0) out.append("; ");
            out.append(c.number).append(' ').append(c.phase.name().toLowerCase()).append(' ')
                    .append(lead).append('/').append(corridor.size() - 1);
            if (c.phase != Phase.INBOUND) out.append(" aboard ").append(aboard);
            if (blocked) out.append(" blocked");
            if (trafficHolds.containsKey(c.number)) out.append(" holding for traffic at ").append(trafficHolds.get(c.number));
            if (waiting) out.append(String.format(Locale.ROOT, " escort (marines %.0f, armour %.0f cells off)",
                    marine, guard));
        }
        if (out.length() == 0) out.append('-');
        out.append("; fleet ").append(fleet.live()).append('/').append(fleet.size()).append(" (")
                .append(fleet.spawned()).append(" ever, ").append(pool.size()).append(" waiting at the camp)");
        return out.append("; town ").append(town.left()).append(" (").append(town.onField()).append(" stood, ")
                .append(town.lost()).append(" lost)").toString();
    }

    /** The corridor cell where the road crosses the line. */
    public int gapIndex() { return gapIndex; }

    /** Live trucks, for evidence. */
    public List<Long> trucks() {
        List<Long> out = new ArrayList<>();
        for (Convoy c : convoys) for (Truck t : c.trucks) if (!t.gone) out.add(t.hull);
        for (Truck t : pool) out.add(t.hull);
        return out;
    }

    /** One tick: brings convoys on, drives them a leg at a time, loads them at the gate, takes them off. */
    public void advance(BattleSimulation sim, int tick, Consumer<String> beat) {
        applyDetour(sim);
        if (corridor.size() < 2) return;
        boolean review = tick % REVIEW_TICKS == 0;
        List<float[]> swarm = review ? positions(sim, true) : List.of();
        company = review ? positions(sim, false) : company;
        guards = review ? guards(sim) : guards;
        for (String b : pending) beat.accept(b);
        pending.clear();
        prune();
        callNext(tick, beat);
        if (review) look(sim);
        for (Convoy c : convoys) {
            if (c.phase == Phase.OUT || c.phase == Phase.LOST || c.phase == Phase.WAITING) continue;
            comeOn(sim, c);
            bury(sim, c);
            if (c.trucks.isEmpty() || allGone(c) && !anyStranded(c)) {
                if (c.trucks.size() >= TRUCKS || c.phase != Phase.INBOUND) {
                    c.phase = Phase.LOST;
                    beat.accept("convoy_lost_" + c.number);
                }
                continue;
            }
            if (!review) continue;
            c.reachedAt = reachedAt(sim, c, swarm);
            switch (c.phase) {
                case INBOUND -> inbound(sim, tick, c, swarm, beat);
                case LOADING -> {
                    int live = 0, atGate = 0;
                    boolean full = true;
                    for (Truck t : c.trucks) {
                        if (t.gone) continue;
                        live++;
                        if (!t.atGate) comeToGate(sim, c, t, swarm);
                        if (!t.atGate) continue;
                        atGate++;
                        full &= t.people >= SEATS;
                    }
                    // Only a truck at the gate takes people aboard; one still on its way turns back with whoever
                    // reached it.
                    if (!leaves(tick - c.loadingSince, c.extraLoading, live, atGate, atGate > 0 && full,
                            town.left() == 0)) continue;
                    c.phase = Phase.OUTBOUND;
                    beat.accept("convoy_loaded_" + c.number);
                }
                case OUTBOUND -> outbound(sim, c, swarm, beat);
                default -> { }
            }
            if (!c.ambushed && reached(sim, c, swarm)) {
                c.ambushed = true;
                beat.accept("convoy_ambushed_" + c.number);
            }
        }
        // The townspeople walk out to the trucks at the gate with seats free, and board them.
        seats.clear();
        for (Convoy c : convoys) {
            if (c.phase != Phase.LOADING) continue;
            for (Truck t : c.trucks) if (!t.gone && t.atGate && t.people < SEATS) seats.add(t);
        }
        town.step(sim, tick, seats);
        sheltered.step(sim, tick, List.of());
        if (review) park(sim);
    }

    /** Trucks come on one at a time at the rear end, each once the one ahead has made room. */
    private void comeOn(BattleSimulation sim, Convoy c) {
        if (c.phase != Phase.INBOUND || c.trucks.size() >= TRUCKS) return;
        // Nothing sets out through the camp while a truck is stopped on its road letting people down.
        if (unloading()) return;
        Truck waiting = fromPool(sim);
        if (waiting != null) {
            if (!c.trucks.isEmpty()) {
                Truck ahead = c.trucks.get(c.trucks.size() - 1);
                int from = nearestIndex(sim.convoy().body(waiting.hull));
                if (!ahead.gone && nearestIndex(sim.convoy().body(ahead.hull)) < from + SPACING) return;
            }
            pool.remove(waiting);
            waiting.convoy = c;
            waiting.parked = false;
            waiting.place = -1;
            waiting.bestAt = -1;
            c.trucks.add(waiting);
            route(sim, waiting, +1);
            return;
        }
        // No truck waiting: one of the fleet still to come on, or a lost one's replacement once it is due.
        int tick = sim.getSimTickIndex();
        if (fleet.spawnable(tick) <= 0) return;
        int at = setDown(corridor.size(), entry, i -> TurnAwareCorridor.canSetOutFrom(corridor.get(i)[0] + .5f,
                corridor.get(i)[1] + .5f, VehicleType.SUPPLY_TRUCK, sim.getGrid()));
        if (!c.trucks.isEmpty()) {
            Truck ahead = c.trucks.get(c.trucks.size() - 1);
            if (!ahead.gone && nearestIndex(sim.convoy().body(ahead.hull)) < at + SPACING) return;
        }
        // Put on in the camp's parking line where there is one, as a waiting truck would stand; else on the road.
        int place = freePlace(sim);
        int[] start = place >= 0 ? places.get(place) : corridor.get(at);
        int[] toward = corridor.get(Math.min(corridor.size() - 1, at + 3));
        float facing = (float) Math.toDegrees(Math.atan2(toward[1] - start[1], toward[0] - start[0])) - 90f;
        float x = start[0] + 0.5f, y = start[1] + 0.5f;
        long hull = sim.convoy().spawn(VehicleType.SUPPLY_TRUCK, Faction.ALLY, VehicleMission.deployed(x, y));
        // Initial pose, before it has moved; later motion goes through the chassis controller.
        sim.convoy().body(hull).teleport(x, y, facing);
        fleet.spawn(tick);
        Truck t = new Truck(c);
        t.hull = hull;
        c.trucks.add(t);
        route(sim, t, +1);
    }

    /**
     * The corridor cell a truck is set down at: the entry, or the first within {@link #SET_DOWN_REACH} past it a truck
     * can set out from. Set down on urban Road seed 1's entry, a corner a fence's width off its turn, a truck's every
     * route was refused at its first pose and it stood there all battle; the entry itself where none can.
     */
    static int setDown(int size, int entry, IntPredicate canSetOut) {
        int first = Math.min(size - 1, entry);
        for (int i = first; i <= Math.min(size - 1, entry + SET_DOWN_REACH); i++) if (canSetOut.test(i)) return i;
        return first;
    }

    private void inbound(BattleSimulation sim, int tick, Convoy c, List<float[]> swarm, Consumer<String> beat) {
        int end = corridor.size() - 1;
        boolean atGate = false;
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            int at = nearestIndex(body);
            if (!c.throughGap && at >= gapIndex) {
                c.throughGap = true;
                beat.accept("convoy_through_gap_" + c.number);
            }
            if (at >= end - arrival()) {
                atGate = true;
                t.atGate = true;
                t.halted = t.unescorted = false;
                sim.convoy().mission(t.hull).authoredRoutePaused = true;
                continue;
            }
            drive(sim, c, t, at, +1, swarm);
        }
        if (atGate && c.trucks.size() >= TRUCKS) {
            c.phase = Phase.LOADING;
            c.loadingSince = tick;
            int seats = 0;
            for (Truck t : c.trucks) if (!t.gone) seats += SEATS - t.people;
            if (lastConvoy == 0 && last(c.number, Math.min(MAX_CONVOYS, convoys.size()),
                    town.left(), seats)) {
                lastConvoy = c.number;
                c.extraLoading += lastExtra;
            }
            beat.accept("convoy_at_town_" + c.number);
        }
    }

    /**
     * A truck of a loading convoy still on its way to the gate keeps driving there: it is not frozen where it stood
     * when the first truck arrived (town Road, verdant 7: the second truck stood paused 45 cells short of the gate for
     * the whole loading, and was then given six people who never boarded it). It is at the gate once it has drawn
     * up behind the truck there ({@link #GATE_QUEUE}): held at its spacing behind the first, it could never come within
     * {@link #AT_LEG} of the end, and turned for home empty (town Road, arid 8: every convoy brought out six).
     */
    private void comeToGate(BattleSimulation sim, Convoy c, Truck t, List<float[]> swarm) {
        int at = nearestIndex(sim.convoy().body(t.hull));
        // Each further back in the convoy draws up a place further back behind the one before it.
        int order = 0;
        for (Truck o : c.trucks) {
            if (o == t) break;
            if (!o.gone) order++;
        }
        if (at >= corridor.size() - 1 - queue() - Math.max(0, order - 1) * QUEUE_STEP) {
            t.atGate = true;
            t.halted = t.unescorted = false;
            sim.convoy().mission(t.hull).authoredRoutePaused = true;
            return;
        }
        drive(sim, c, t, at, +1, swarm);
    }

    /**
     * Whether a loading convoy turns for home: at once when the town has nobody left to board; otherwise not before
     * a beat's extra wait (R3), and then when every live truck is at the gate and full, or at
     * {@link #LOADING_CAP_TICKS} past that extra with whoever has boarded (a truck still on its way included).
     *
     * @param waited    ticks since the first truck reached the gate
     * @param extra     ticks a beat added to its loading
     * @param gateFull  whether the trucks at the gate (one at least) are full
     * @param townEmpty whether the town has nobody left to board
     */
    static boolean leaves(int waited, int extra, int live, int atGate, boolean gateFull, boolean townEmpty) {
        if (live <= 0) return false;
        if (townEmpty) return true;
        if (waited < extra) return false;
        return atGate >= live && gateFull || waited >= extra + LOADING_CAP_TICKS;
    }

    /**
     * Ticks between two townspeople climbing aboard one truck: {@link #BOARD_TICKS}, and slower by a beat's extra
     * wait spread over a truck's seats (R3: the town's last are its wounded, and board slowly).
     */
    static int boardTicks(int extra) {
        return BOARD_TICKS + Math.max(0, extra) / SEATS;
    }

    private void outbound(BattleSimulation sim, Convoy c, List<float[]> swarm, Consumer<String> beat) {
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            int at = nearestIndex(body);
            if (camp != null) {
                if (t.unloading || stopsAtCamp(at, entry, aheadStopped(c, t))) unload(sim, c, t, sim.getSimTickIndex());
                else drive(sim, c, t, at, -1, swarm);
                continue;
            }
            if (at <= entry + AT_LEG) {
                // Back where it set out, with its people.
                peopleOut += t.people;
                c.people += t.people;
                t.people = 0;
                t.gone = true;
                sim.convoy().despawn(t.hull);
                fleet.lose(sim.getSimTickIndex() - REPLACE_TICKS);
                continue;
            }
            drive(sim, c, t, at, -1, swarm);
        }
        if (allGone(c)) {
            c.phase = Phase.OUT;
            beat.accept("convoy_out_" + c.number);
        }
    }

    /**
     * A truck home at the camp lets its people down one at a time ({@link #BOARD_TICKS} apart, as they climbed
     * aboard): each steps off beside it as a civilian, is out from that moment, and walks into the camp to wait. Once
     * it is empty it leaves the convoy for the camp's waiting trucks, and pulls back down the road ({@link #park}).
     */
    private void unload(BattleSimulation sim, Convoy c, Truck t, int tick) {
        VehicleMission mission = sim.convoy().mission(t.hull);
        mission.authoredRoutePaused = true;
        t.halted = t.unescorted = false;
        if (!t.unloading) {
            t.unloading = true;
            t.nextUnloadTick = tick;
        }
        if (t.people > 0) {
            if (tick < t.nextUnloadTick) return;
            GroundBody body = sim.convoy().body(t.hull);
            List<int[]> cell = CordonGround.clusterNear(sim, (int) body.x, (int) body.y, 1);
            if (cell.isEmpty()) return;
            t.people--;
            c.people++;
            peopleOut++;
            int[] home = camp.nextHome();
            long id = sim.spawn(new EntitySpec("Evacuee " + peopleOut, Faction.CIVILIAN, UnitType.CIVILIAN,
                    cell.get(0)[0], cell.get(0)[1]).role(UnitRole.VIP));
            sheltered.admit(id, home[0], home[1]);
            t.nextUnloadTick = tick + BOARD_TICKS;
            return;
        }
        // Empty: it waits at the camp for the next trip, in its place in the parking line.
        t.unloading = false;
        t.unloaded = true;
        t.gone = true;
        Truck waiting = new Truck(null);
        waiting.hull = t.hull;
        pool.add(waiting);
        toPlace(sim, waiting);
    }

    /**
     * Whether a truck coming home stops to let its people down: at the camp's middle, or drawn up behind a truck of its
     * convoy already stopped there ({@link #CAMP_QUEUE}: the convoy's trucks queued as at the gate).
     */
    static boolean stopsAtCamp(int at, int middle, boolean aheadStopped) {
        return at <= middle + CAMP_STOP || aheadStopped && at <= middle + CAMP_QUEUE;
    }

    /** Whether another truck of the convoy is stopped in the camp or already through with it. */
    private static boolean aheadStopped(Convoy c, Truck t) {
        for (Truck o : c.trucks) if (o != t && (o.unloading || o.unloaded)) return true;
        return false;
    }

    /** Whether a truck is stopped in the camp letting its people down. */
    public boolean unloading() {
        for (Convoy c : convoys) for (Truck t : c.trucks) if (!t.gone && t.unloading) return true;
        return false;
    }

    /** The camp's waiting truck nearest the drop-off (the first to set out), dropping any destroyed; or {@code null}. */
    private Truck fromPool(BattleSimulation sim) {
        prunePool(sim);
        Truck best = null;
        int bestAt = Integer.MIN_VALUE;
        for (Truck t : pool) {
            int at = t.parked && t.place >= 0 ? -t.place : nearestIndex(sim.convoy().body(t.hull)) - PARK_PLACES;
            if (at > bestAt) {
                bestAt = at;
                best = t;
            }
        }
        return best;
    }

    /** Drops the camp's waiting trucks that were destroyed: the fleet makes them good. */
    private void prunePool(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        pool.removeIf(t -> {
            boolean dead = !sim.convoy().isVehicle(t.hull) || sim.convoy().structure(t.hull) <= 0f;
            if (dead) fleet.lose(tick);
            return dead;
        });
    }

    /**
     * The camp's waiting trucks drive to their places in its parking line and stop there, in a row clear of the
     * drop-off stop and of the camp's ground ({@link #places}).
     */
    private void park(BattleSimulation sim) {
        if (pool.isEmpty()) return;
        prunePool(sim);
        for (Truck t : pool) {
            if (t.parked) continue;
            if (t.place < 0) {
                toPlace(sim, t);
                continue;
            }
            int[] place = places.get(t.place);
            GroundBody body = sim.convoy().body(t.hull);
            VehicleMission mission = sim.convoy().mission(t.hull);
            if (body.distanceTo(place[0] + .5f, place[1] + .5f) <= PARKED_WITHIN || mission.authoredRouteX == null) {
                t.parked = true;
                mission.authoredRoutePaused = true;
            }
        }
    }

    /** Sends a waiting truck to a free place in the parking line; with none it waits where it stopped. */
    private void toPlace(BattleSimulation sim, Truck t) {
        VehicleMission mission = sim.convoy().mission(t.hull);
        int place = freePlace(sim);
        if (place < 0) {
            // No place free (or no line): it waits where it stopped, off the road through the camp's middle.
            mission.authoredRoutePaused = true;
            t.parked = true;
            return;
        }
        t.place = place;
        ground(sim);
        GroundBody body = sim.convoy().body(t.hull);
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) body.x, (int) body.y, SNAP);
        float[][] way = prove(sim, from, places.get(place));
        if (way == null) way = new float[][]{{body.x, places.get(place)[0] + .5f}, {body.y, places.get(place)[1] + .5f}};
        mission.authoredRouteX = way[0];
        mission.authoredRouteY = way[1];
        mission.authoredLeg = VehicleLeg.MOVE_ORDER;
        mission.authoredRoutePaused = false;
        t.direction = -1;
    }

    /** The first place in the parking line no waiting truck holds, or -1 with no line or none free. */
    private int freePlace(BattleSimulation sim) {
        if (places == null) places = places(sim);
        boolean[] held = new boolean[places.size()];
        for (Truck t : pool) if (t.place >= 0 && t.place < held.length) held[t.place] = true;
        for (int i = 0; i < held.length; i++) if (!held[i]) return i;
        return -1;
    }

    /**
     * The camp's parking line: {@link #PARK_PLACES} places {@link #PARK_STEP} apart in a row across the drop-off
     * stop's lane, starting {@link #PARK_CLEAR} cells from the stop on the side away from where the road leaves it, on
     * ground a truck fits outside the camp's ring (its crops and its people), road first. Empty with no camp.
     */
    private List<int[]> places(BattleSimulation sim) {
        if (camp == null || entry <= 0 || entry >= corridor.size() - 6) return List.of();
        int[] stop = corridor.get(entry), back = corridor.get(entry - 1), on = corridor.get(Math.min(corridor.size() - 1, entry + 6));
        int[] lane = {Integer.signum(stop[0] - back[0]), Integer.signum(stop[1] - back[1])};
        if (lane[0] != 0 && lane[1] != 0) lane[1] = 0;
        int[] across = {-lane[1], lane[0]};
        int leaves = Integer.signum((on[0] - stop[0]) * across[0] + (on[1] - stop[1]) * across[1]);
        int side = leaves == 0 ? 1 : -leaves;
        List<int[]> line = new ArrayList<>();
        for (int sweep : new int[]{side, -side}) {
            for (int k = 0; line.size() < PARK_PLACES && k < PARK_PLACES * 2; k++) {
                int d = PARK_CLEAR + k * PARK_STEP;
                int x = stop[0] + across[0] * d * sweep, y = stop[1] + across[1] * d * sweep;
                if (!parkable(sim, x, y)) break;
                line.add(new int[]{x, y});
            }
            if (line.size() >= PARK_PLACES) break;
        }
        return List.copyOf(line);
    }

    /** Whether a truck may wait at a cell: it fits there, outside the camp's ring. */
    private boolean parkable(BattleSimulation sim, int x, int y) {
        if (!CordonCorridor.drivable(sim.getGrid(), x, y)) return false;
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) if (camp.contains(x + dx, y + dy)) return false;
        return true;
    }

    /** The corridor cell nearest a cell. */
    private int nearestCell(int x, int y) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < corridor.size(); i++) {
            long dx = corridor.get(i)[0] - x, dy = corridor.get(i)[1] - y;
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = i;
            }
        }
        return best;
    }

    /**
     * Keeps a truck rolling along the road in its direction: held while the swarm is on the road
     * ahead of it or the truck ahead of it is too close, and given the rest of the route again from
     * where it stands when it has run out short of the end or stood still on it.
     */
    private void drive(BattleSimulation sim, Convoy c, Truck t, int at, int direction, List<float[]> swarm) {
        VehicleMission mission = sim.convoy().mission(t.hull);
        int[] block = blockedAhead(at, direction, swarm);
        if (block != null) {
            t.blockX = block[0];
            t.blockY = block[1];
        }
        t.halted = block != null;
        // In the swarm's country a convoy waits for an escort beside it; one escort is the convoy's.
        t.unescorted = at >= gapIndex - ESCORT_FROM && !escorted(sim, c);
        // Held short of a tight stretch another convoy holds (CordonTraffic).
        Integer holdAt = trafficHolds.get(c.number);
        boolean waiting = holdAt != null && (at - holdAt) * direction >= 0;
        mission.authoredRoutePaused = t.halted || t.unescorted || waiting || crowded(sim, c, t, at, direction);
        int tick = sim.getSimTickIndex();
        // Stuck is no headway along the road, not standing still: a truck circling a point it cannot
        // turn onto is moving all the time.
        // Round the yard's loop the way out runs on up the corridor to the stop before the gate: any step is headway.
        boolean inYard = yardFrom >= 0 && at > yardFrom && at != t.bestAt;
        if (t.bestAt < 0 || t.direction != direction || (at - t.bestAt) * direction > 0 || inYard) {
            t.bestAt = at;
            t.movedTick = tick;
        }
        if (mission.authoredRoutePaused) t.movedTick = tick;
        boolean stuck = tick - t.movedTick > STUCK_TICKS;
        if (stuck && unstick(sim, t, at, direction)) return;
        if (mission.authoredRouteX == null || t.direction != direction || stuck) route(sim, t, direction);
    }


    /** One look at the road's traffic: who holds which tight stretch, and who waits short of one. */
    private void look(BattleSimulation sim) {
        if (traffic == null) traffic = new CordonTraffic(stretches(sim));
        List<CordonTraffic.Mover> movers = new ArrayList<>();
        for (Convoy c : convoys) {
            if (c.phase != Phase.INBOUND && c.phase != Phase.LOADING && c.phase != Phase.OUTBOUND) continue;
            List<Integer> at = new ArrayList<>();
            for (Truck t : c.trucks) {
                if (t.gone || !sim.convoy().isVehicle(t.hull)) continue;
                at.add(nearestIndex(sim.convoy().body(t.hull)));
            }
            if (at.isEmpty()) continue;
            int[] cells = new int[at.size()];
            for (int i = 0; i < cells.length; i++) cells[i] = at.get(i);
            movers.add(new CordonTraffic.Mover(c.number, c.phase == Phase.OUTBOUND ? -1 : 1, cells));
        }
        trafficHolds = traffic.update(movers);
    }

    /**
     * The road's tight stretches: the yard and its mouth, one convoy at a time, and every run past the camp where the
     * road is narrower than {@link #PASSING} cells; merged where closer than a convoy's length and two hold-backs.
     */
    private List<CordonTraffic.Stretch> stretches(BattleSimulation sim) {
        int end = corridor.size() - 1;
        int[] widths = new int[corridor.size()];
        int from = Math.min(end, entry + PARK_CLEAR), to = yardFrom >= 0 ? Math.max(from, yardFrom - YARD_APPROACH) : end;
        for (int i = 0; i < widths.length; i++) widths[i] = i < from || i > to ? Integer.MAX_VALUE : width(sim, i);
        List<CordonTraffic.Stretch> out = new ArrayList<>(CordonTraffic.narrows(widths, PASSING));
        if (yardFrom >= 0) out.add(new CordonTraffic.Stretch(Math.max(0, yardFrom - YARD_APPROACH), end, true));
        return CordonTraffic.merged(out, 2 * CordonTraffic.HOLD_BACK + TRUCKS * QUEUE_STEP);
    }

    /** The road's open width across corridor cell {@code index}: walkable cells along its normal, out to {@link #WIDTH_REACH}. */
    private int width(BattleSimulation sim, int index) {
        int[] c = corridor.get(index);
        int width = 1;
        for (int side : new int[]{1, -1}) {
            for (int k = 1; k <= WIDTH_REACH; k++) {
                int[] b = beside(index, side * k);
                if (!sim.getGrid().inBounds(b[0], b[1]) || !sim.getGrid().isWalkable(b[0], b[1])) break;
                width++;
            }
        }
        return c == null ? 0 : width;
    }

    /** Whether a company marine or an armed allied hull stands within {@link #ESCORT_REACH} of one of a convoy's trucks. */
    private boolean escorted(BattleSimulation sim, Convoy c) {
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            if (near(body, company, ESCORT_REACH) || near(body, guards, GUARD_REACH)) return true;
        }
        return false;
    }

    private static float nearest(GroundBody body, List<float[]> at) {
        float best = Float.MAX_VALUE;
        for (float[] m : at) best = Math.min(best, body.distanceTo(m[0], m[1]));
        return best;
    }

    private static boolean near(GroundBody body, List<float[]> at, float reach) {
        for (float[] m : at) if (body.distanceTo(m[0], m[1]) <= reach) return true;
        return false;
    }

    /**
     * The compound's gate while a convoy is on its way to it or loading at it, else nothing: where the company holds
     * for it ahead of time (rule 2). Walked out only once the trucks loaded, the escort came from the line a hundred
     * cells off and the swarm's brutes beat both trucks at the gate first (town Road, arid 8).
     */
    public List<int[]> gates() {
        if (corridor.size() < 2) return List.of();
        for (Convoy c : convoys) {
            if (c.phase == Phase.INBOUND || c.phase == Phase.LOADING) return List.of(corridor.get(corridor.size() - 1));
        }
        return List.of();
    }

    /**
     * Where each convoy on the road the armour does not escort wants its escort: a few cells ahead
     * of its lead truck along the road in the way it is going, or at the gate while it loads. What
     * the company is asked to walk.
     * The armour escorts first; the company is the backstop for a convoy whose armour went short, has
     * not come, or has died ({@link CordonAlliedRules#convoyEscorted}).
     */
    public List<int[]> unguarded(BattleSimulation sim) {
        return escorts(sim, true, false);
    }

    /**
     * Where each convoy on the road wants its armoured escort, from the base and back: beside the road either side, a few
     * cells ahead of its lead truck, and a third further ahead on the left. Never on the road itself, where a
     * parked car is in the trucks' way.
     */
    public List<int[][]> guardPosts(BattleSimulation sim) {
        List<int[][]> out = new ArrayList<>();
        guardConvoys.clear();
        for (int[] place : escorts(sim, false, true)) {
            guardConvoys.add(place[4]);
            int at = place[2], direction = place[3];
            int ahead = Math.max(0, Math.min(corridor.size() - 1, at + direction * GUARD_POINT));
            out.add(new int[][]{beside(at, GUARD_SIDE), beside(at, -GUARD_SIDE), beside(ahead, GUARD_SIDE)});
        }
        return out;
    }

    /** The cell {@code side} cells to the left of the road at a corridor cell (to the right when negative). */
    private int[] beside(int index, int side) {
        int[] a = corridor.get(Math.max(0, index - 3)), b = corridor.get(Math.min(corridor.size() - 1, index + 3));
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float length = (float) Math.max(1e-3, Math.sqrt(dx * dx + dy * dy));
        int[] c = corridor.get(index);
        return new int[]{Math.round(c[0] - dy / length * side), Math.round(c[1] + dx / length * side)};
    }


    /**
     * Each convoy's escort place as {x, y, corridor index, direction, convoy number}: on the whole road, or only
     * near and past the gap; and of every convoy, or only of those the armour does not escort.
     */
    private List<int[]> escorts(BattleSimulation sim, boolean unguardedOnly, boolean wholeRoad) {
        List<int[]> out = new ArrayList<>();
        for (Convoy c : convoys) {
            if (c.phase != Phase.INBOUND && c.phase != Phase.LOADING && c.phase != Phase.OUTBOUND) continue;
            int direction = c.phase == Phase.OUTBOUND ? -1 : 1;
            int lead = -1;
            boolean guarded = false;
            for (Truck t : c.trucks) {
                if (t.gone) continue;
                GroundBody body = sim.convoy().body(t.hull);
                guarded |= near(body, guards, GUARD_REACH);
                int at = nearestIndex(body);
                if (lead < 0 || (at - lead) * direction > 0) lead = at;
            }
            if (unguardedOnly && escortedByArmour(c.number, guarded)) continue;
            if (lead < 0) continue;
            if (!wholeRoad && direction > 0 && lead < gapIndex - 2 * ESCORT_FROM) continue;
            if (!wholeRoad && direction < 0 && lead < gapIndex - ESCORT_FROM) continue;
            int ahead = Math.max(0, Math.min(corridor.size() - 1, lead + direction * ESCORT_LEAD));
            int[] cell = corridor.get(ahead);
            out.add(new int[]{cell[0], cell[1], ahead, direction, c.number});
        }
        return out;
    }

    /**
     * Whether the armour escorts convoy {@code number}: the escort it was given is whole and one of it is alongside.
     * Unwired, any armed hull alongside a truck.
     */
    private boolean escortedByArmour(int number, boolean alongside) {
        if (armourEscort == null) return alongside;
        int index = guardConvoys.indexOf(number);
        int[] escort = index < 0 ? null : armourEscort.apply(index);
        return escort != null && CordonAlliedRules.convoyEscorted(escort[0], escort[1], alongside);
    }

    /** Whether another truck of the convoy is on the road just ahead of this one. */
    private boolean crowded(BattleSimulation sim, Convoy c, Truck t, int at, int direction) {
        for (Truck other : c.trucks) {
            if (other == t || other.gone) continue;
            int gap = (nearestIndex(sim.convoy().body(other.hull)) - at) * direction;
            if (gap > 0 && gap < SPACING) return true;
        }
        return false;
    }

    /** Corridor cells ahead a stuck truck is proved a fresh way to, nearest first. */
    private static final int[] UNSTICK_AHEAD = {12, 24, 36};

    /**
     * Proves a stuck truck a way from where it stands to the road a stretch further on, and hands it
     * that and the rest of the route beyond.
     *
     * @return whether a way was proved
     */
    private boolean unstick(BattleSimulation sim, Truck t, int at, int direction) {
        ground(sim);
        GroundBody body = sim.convoy().body(t.hull);
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) body.x, (int) body.y, SNAP);
        if (from == null) return false;
        float[][] path = direction > 0 ? inward : outward;
        for (int ahead : UNSTICK_AHEAD) {
            int index = at + direction * ahead;
            if (index < 0 || index >= corridor.size()) continue;
            int[] goal = VehicleRoutePlanner.snapToMask(clearance, corridor.get(index)[0], corridor.get(index)[1], SNAP);
            if (goal == null) continue;
            float[][] proved = VehicleRoutePlanner.routeDrivable(from[0], from[1], goal[0], goal[1],
                    sim.getGrid(), cost, clearance, VehicleType.SUPPLY_TRUCK);
            if (proved == null || proved[0].length == 0) continue;
            int join = nearestPoint(path, goal[0] + 0.5f, goal[1] + 0.5f);
            int rest = path[0].length - join - 1;
            float[] xs = new float[proved[0].length + rest], ys = new float[proved[0].length + rest];
            System.arraycopy(proved[0], 0, xs, 0, proved[0].length);
            System.arraycopy(proved[1], 0, ys, 0, proved[0].length);
            System.arraycopy(path[0], join + 1, xs, proved[0].length, rest);
            System.arraycopy(path[1], join + 1, ys, proved[0].length, rest);
            VehicleMission mission = sim.convoy().mission(t.hull);
            mission.authoredRouteX = xs;
            mission.authoredRouteY = ys;
            mission.authoredLeg = VehicleLeg.MOVE_ORDER;
            t.direction = direction;
            t.movedTick = sim.getSimTickIndex();
            return true;
        }
        return false;
    }

    private static int nearestPoint(float[][] path, float x, float y) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int k = 0; k < path[0].length; k++) {
            float dx = path[0][k] - x, dy = path[1][k] - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    private void ground(BattleSimulation sim) {
        if (clearance != null) return;
        clearance = VehicleClearance.erode(sim.getGrid(),
                VehicleClearance.radiusForWidth(VehicleType.SUPPLY_TRUCK.visualWidthCells));
        cost = TerrainCostField.from(sim.getTopology());
        if (yardFrom < 0 || yardFrom <= entry) {
            inward = lay(sim, entry, corridor.size() - 1);
            outward = lay(sim, corridor.size() - 1, entry);
            return;
        }
        // Into the yard: the road to its gate, then round the yard's loop to the stop. Out of it: on round the loop
        // from wherever a truck stands in it (one at the stop is at the loop's end), forward out of the gate, and down
        // the road.
        List<int[]> loop = corridor.subList(yardFrom, corridor.size());
        inward = join(lay(sim, entry, yardFrom), cells(loop, 1));
        outward = join(join(cells(loop, 0), cells(yard.wayOut(), 1)), lay(sim, yardFrom, entry));
    }

    /** The middles of {@code cells} from index {@code from} on, as a path. */
    private static float[][] cells(List<int[]> cells, int from) {
        int n = Math.max(0, cells.size() - from);
        float[][] out = new float[2][n];
        for (int k = 0; k < n; k++) {
            out[0][k] = cells.get(from + k)[0] + 0.5f;
            out[1][k] = cells.get(from + k)[1] + 0.5f;
        }
        return out;
    }

    /** One path then another. */
    private static float[][] join(float[][] a, float[][] b) {
        float[][] out = new float[2][a[0].length + b[0].length];
        for (int i = 0; i < 2; i++) {
            System.arraycopy(a[i], 0, out[i], 0, a[0].length);
            System.arraycopy(b[i], 0, out[i], a[0].length, b[0].length);
        }
        return out;
    }

    /** Hands a truck the rest of the road's route in its direction, from the point of it nearest the truck. */
    private void route(BattleSimulation sim, Truck t, int direction) {
        ground(sim);
        float[][] path = direction > 0 ? inward : outward;
        GroundBody body = sim.convoy().body(t.hull);
        int from = nearestPoint(path, body.x, body.y);
        int n = Math.max(2, path[0].length - from);
        float[] xs = new float[n], ys = new float[n];
        xs[0] = body.x;
        ys[0] = body.y;
        for (int k = 1; k < n; k++) {
            int src = Math.min(path[0].length - 1, from + k);
            xs[k] = path[0][src];
            ys[k] = path[1][src];
        }
        VehicleMission mission = sim.convoy().mission(t.hull);
        mission.authoredRouteX = xs;
        mission.authoredRouteY = ys;
        mission.authoredLeg = VehicleLeg.MOVE_ORDER;
        t.direction = direction;
        t.movedTick = sim.getSimTickIndex();
    }

    /**
     * The road's route from one corridor cell to another: each stretch of {@link #STRETCH} cells a
     * drivable route the planner proves for a truck; where it proves none, or meets the stretch before
     * in a sharp turn, one driven from halfway along the stretch before, so the turn is proved with them;
     * where not even that, a way a truck's footprint fits along; and the corridor's own cells where there
     * is not even that. Proved apart, two stretches can meet in a turn no truck makes: on town verdant 8
     * one ran east to the corner of the square and the next set off south-west from it, and every
     * convoy's lead truck stuck there.
     */
    private float[][] lay(BattleSimulation sim, int from, int to) {
        int direction = to >= from ? 1 : -1;
        List<float[]> points = new ArrayList<>();
        int[] before = null;
        int beforeMark = 0;
        float[] beforeHeading = null, endHeading = null;
        for (int i = from; i != to; ) {
            int next = direction > 0 ? Math.min(to, i + STRETCH) : Math.max(to, i - STRETCH);
            int[] a = corridor.get(i), b = corridor.get(next);
            int[] sa = VehicleRoutePlanner.snapToMask(clearance, a[0], a[1], SNAP);
            int[] sb = VehicleRoutePlanner.snapToMask(clearance, b[0], b[1], SNAP);
            float[][] way = prove(sim, sa, sb);
            int mark = points.size();
            if (before != null && (way == null || dot(endHeading, heading(way, true)) < SHARP)) {
                // Driven from halfway along the stretch before, so the turn where they meet is proved too; only
                // a way setting off as the stretch before was going, never one turning back down another street.
                float[][] back = prove(sim, before, sb);
                if (back != null && dot(beforeHeading, heading(back, true)) >= SHARP) {
                    points.subList(beforeMark, points.size()).clear();
                    mark = beforeMark;
                    way = back;
                }
            }
            before = null;
            if (way != null) {
                int[] half = halfway(way);
                int h = Math.min(half[0] + 1, way[0].length - 1);
                before = new int[]{half[1], half[2]};
                beforeMark = mark + half[0] + 1;
                beforeHeading = unit(way[0][h] - way[0][half[0]], way[1][h] - way[1][half[0]]);
                endHeading = heading(way, false);
            }
            // Where no turn is proved, a way the footprint fits along, clear of what was set on the
            // map after the corridor was found; the road's own cells only where there is not even that.
            if (way == null && sa != null && sb != null) {
                way = VehicleRoutePlanner.route(sa[0], sa[1], sb[0], sb[1], sim.getGrid(), cost, clearance);
            }
            if (way != null && way[0].length > 0) {
                for (int k = 0; k < way[0].length; k++) points.add(new float[]{way[0][k], way[1][k]});
            } else {
                for (int k = i; k != next + direction; k += direction) {
                    points.add(new float[]{corridor.get(k)[0] + 0.5f, corridor.get(k)[1] + 0.5f});
                }
            }
            i = next;
        }
        float[][] path = new float[2][points.size()];
        for (int k = 0; k < points.size(); k++) {
            path[0][k] = points.get(k)[0];
            path[1][k] = points.get(k)[1];
        }
        return path;
    }

    /** The least cosine between two stretches' headings where they meet that is not a sharp turn. */
    private static final float SHARP = 0.5f;

    /** The way's heading as it sets off ({@code start}) or as it arrives, over its first or last few cells. */
    private static float[] heading(float[][] way, boolean start) {
        int n = way[0].length, at = start ? 0 : n - 1, step = start ? 1 : -1;
        int k = at + step;
        while (k + step >= 0 && k + step < n && Math.hypot(way[0][k] - way[0][at], way[1][k] - way[1][at]) < 3f) k += step;
        if (k < 0 || k >= n) return null;
        return start ? unit(way[0][k] - way[0][at], way[1][k] - way[1][at]) : unit(way[0][at] - way[0][k], way[1][at] - way[1][k]);
    }

    private static float[] unit(float dx, float dy) {
        float d = (float) Math.hypot(dx, dy);
        return d <= 0f ? null : new float[]{dx / d, dy / d};
    }

    /** The cosine between two headings; 1 where either is unknown. */
    private static float dot(float[] a, float[] b) {
        return a == null || b == null ? 1f : a[0] * b[0] + a[1] * b[1];
    }

    /** The cell halfway along a way, and the last of its points short of it: {@code {point, x, y}}. */
    private static int[] halfway(float[][] way) {
        int n = way[0].length;
        float[] run = new float[n];
        for (int k = 1; k < n; k++) run[k] = run[k - 1] + (float) Math.hypot(way[0][k] - way[0][k - 1], way[1][k] - way[1][k - 1]);
        float half = run[n - 1] / 2f;
        int k = 0;
        while (k + 2 < n && run[k + 1] < half) k++;
        int m = Math.min(k + 1, n - 1);
        float t = run[m] <= run[k] ? 0f : (half - run[k]) / (run[m] - run[k]);
        return new int[]{k, (int) Math.floor(way[0][k] + t * (way[0][m] - way[0][k])),
                (int) Math.floor(way[1][k] + t * (way[1][m] - way[1][k]))};
    }

    /** A drivable route the planner proves for a truck between two cells, or {@code null}. */
    private float[][] prove(BattleSimulation sim, int[] from, int[] to) {
        if (from == null || to == null) return null;
        float[][] proved = VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1],
                sim.getGrid(), cost, clearance, VehicleType.SUPPLY_TRUCK);
        return proved == null || proved[0].length == 0 ? null : proved;
    }

    /** Cells a stretch's end is moved to find ground a truck's footprint fits. */
    private static final int SNAP = 4;

    /** Whether a swarm body is on the road within {@link #LOOK_AHEAD} cells ahead. */
    private int[] blockedAhead(int at, int direction, List<float[]> swarm) {
        if (swarm.isEmpty()) return null;
        for (int step = 1; step <= LOOK_AHEAD; step++) {
            int i = at + direction * step;
            if (i < 0 || i >= corridor.size()) break;
            int[] cell = corridor.get(i);
            for (float[] s : swarm) {
                if (Math.abs(s[0] - cell[0]) <= BLOCK_REACH && Math.abs(s[1] - cell[1]) <= BLOCK_REACH) return cell;
            }
        }
        return null;
    }

    /**
     * Where the company is asked to clear the road, one place a convoy ({@code cordon-nouns.md}, Flow, the Road):
     * ahead of a convoy halted with the road ahead blocked, or else where the swarm is on one of its trucks
     * ({@link #roadCall}).
     */
    public List<int[]> roadBlocks() {
        List<int[]> out = new ArrayList<>();
        for (Convoy c : convoys) {
            if (c.phase != Phase.INBOUND && c.phase != Phase.LOADING && c.phase != Phase.OUTBOUND) continue;
            int[] block = null;
            for (Truck t : c.trucks) {
                if (t.gone || !t.halted) continue;
                block = new int[]{t.blockX, t.blockY};
                break;
            }
            int[] call = roadCall(block, c.reachedAt);
            if (call != null) out.add(call);
        }
        return out;
    }

    /**
     * Where a convoy calls the company to clear its road: the road ahead where the swarm blocks it, or, with the road
     * ahead open, where the swarm has reached one of its trucks; {@code null} for neither. Only the road ahead used to
     * call, so a truck the swarm caught where nothing lay ahead of it called nobody: at the compound's gate while it
     * loaded, where it no longer looks ahead (town Road, arid 7: two brutes beat a loading truck to death in six
     * seconds while the company stood at the hinge), or from the side or behind on the road.
     */
    static int[] roadCall(int[] blockedAhead, int[] reachedAt) {
        return blockedAhead != null ? blockedAhead : reachedAt;
    }

    /**
     * Where the swarm body nearest any of a convoy's trucks stands, if one is within {@link #BLOCK_REACH} of it:
     * the swarm on the convoy itself.
     */
    static int[] swarmOn(List<float[]> trucks, List<float[]> swarm) {
        float best = BLOCK_REACH;
        int[] out = null;
        for (float[] t : trucks) {
            for (float[] s : swarm) {
                float d = (float) Math.hypot(s[0] - t[0], s[1] - t[1]);
                if (d <= best) {
                    best = d;
                    out = new int[]{(int) s[0], (int) s[1]};
                }
            }
        }
        return out;
    }

    private int[] reachedAt(BattleSimulation sim, Convoy c, List<float[]> swarm) {
        List<float[]> trucks = new ArrayList<>();
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            trucks.add(new float[]{body.x, body.y});
        }
        return swarmOn(trucks, swarm);
    }

    private boolean reached(BattleSimulation sim, Convoy c, List<float[]> swarm) {
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            for (float[] s : swarm) if (body.distanceTo(s[0], s[1]) <= AMBUSH_REACH) return true;
        }
        return false;
    }

    /** A destroyed truck is gone, and its passengers with it. */
    private void bury(BattleSimulation sim, Convoy c) {
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            if (sim.convoy().isVehicle(t.hull) && sim.convoy().structure(t.hull) > 0f) continue;
            t.gone = true;
            peopleLost += t.people;
            t.people = 0;
            fleet.lose(sim.getSimTickIndex());
        }
    }

    private static boolean anyStranded(Convoy c) {
        for (Truck t : c.trucks) if (t.stranded) return true;
        return false;
    }

    private static boolean allGone(Convoy c) {
        for (Truck t : c.trucks) if (!t.gone) return false;
        return true;
    }

    /** The corridor cell nearest a body: where along the road it is. */
    private int nearestIndex(GroundBody body) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < corridor.size(); i++) {
            int[] c = corridor.get(i);
            float dx = c[0] + 0.5f - body.x, dy = c[1] + 0.5f - body.y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Where the allies' armed hulls stand. */
    private static List<float[]> guards(BattleSimulation sim) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0, n = sim.convoy().vehicleCount(); i < n; i++) {
            long hull = sim.convoy().vehicleAt(i);
            if (sim.convoy().faction(hull) != Faction.ALLY || !sim.convoy().isArmed(hull)
                    || sim.convoy().structure(hull) <= 0f) continue;
            GroundBody body = sim.convoy().body(hull);
            out.add(new float[]{body.x, body.y});
        }
        return out;
    }

    /** Where the swarm's bodies stand, or the company's marines. */
    private static List<float[]> positions(BattleSimulation sim, boolean swarm) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            Faction faction = sim.identity().faction(unit);
            boolean wanted = swarm ? faction == Faction.DEFENDER && sim.identity().type(unit).isSwarm()
                    && !sim.identity().type(unit).isSwarmWorker()
                    : faction == Faction.MARINE;
            if (wanted) out.add(new float[]{sim.world().x(unit), sim.world().y(unit)});
        }
        return out;
    }
}
