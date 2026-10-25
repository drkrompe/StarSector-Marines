package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;

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
 * not reached ({@link #unguarded}), and a truck nobody escorts waits at the line. The townspeople are
 * a count the compound holds; they ride as a count, and a truck that is destroyed takes its
 * passengers with it.
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

    /** Trucks in a convoy. */
    public static final int TRUCKS = 2;
    /** Townspeople a truck carries out. */
    public static final int SEATS = 6;
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
    /** Ticks a convoy waits at the gate while its people board. */
    static final int LOADING_TICKS = 60 * 30;
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
    /** Ticks a truck may make no headway along an open route before it is given a way on again. */
    static final int STUCK_TICKS = 60 * 5;
    /**
     * Ticks past its loading a convoy waits at the gate for a truck still on its way there before it turns for home
     * without it: the straggler is given none of the people and drives back empty.
     */
    static final int STRAGGLER_TICKS = LOADING_TICKS;

    private enum Phase { WAITING, INBOUND, LOADING, OUTBOUND, OUT, LOST }

    private static final class Truck {
        long hull;
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
        /** Where the road ahead of a halted truck is blocked. */
        int blockX, blockY;
        /** The furthest corridor cell along its way it has reached, and the tick it last went further, was held or was given its route. */
        int bestAt = -1;
        int movedTick;
    }

    private static final class Convoy {
        final int number;
        final int departTick;
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
    private int town;
    private int peopleOut, peopleLost;
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
     * @param departTicks when each convoy sets out, in order
     * @param townspeople the people waiting in the compound
     */
    public CordonConvoys(List<int[]> corridor, int entry, int gapIndex, List<Integer> departTicks, int townspeople) {
        this.corridor = List.copyOf(corridor);
        this.entry = entry;
        this.gapIndex = gapIndex;
        for (int i = 0; i < departTicks.size(); i++) convoys.add(new Convoy(i + 1, departTicks.get(i)));
        this.town = townspeople;
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

    private void applyDetour() {
        if (detour == null) return;
        for (Convoy c : convoys) {
            if (c.phase == Phase.INBOUND || c.phase == Phase.LOADING || c.phase == Phase.OUTBOUND) return;
        }
        corridor = detour;
        gapIndex = detourGap;
        entry = detourEntry;
        detour = null;
        detourApplied = true;
        clearance = null;
        inward = outward = null;
    }
    /** Convoys back out with their people. */
    public int out() { return count(Phase.OUT); }
    /** Convoys whose every truck was destroyed. */
    public int lost() { return count(Phase.LOST); }
    /** Townspeople driven out so far. */
    public int peopleOut() { return peopleOut; }
    /** Townspeople killed aboard a destroyed truck. */
    public int peopleLost() { return peopleLost; }
    /** Townspeople still in the compound. */
    public int town() { return town; }

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
            int lead = -1;
            boolean blocked = false, waiting = false;
            float marine = Float.MAX_VALUE, guard = Float.MAX_VALUE;
            for (Truck t : c.trucks) {
                if (t.gone) continue;
                GroundBody body = sim.convoy().body(t.hull);
                int at = nearestIndex(body);
                lead = Math.max(lead, at);
                marine = Math.min(marine, nearest(body, company));
                guard = Math.min(guard, nearest(body, guards));
                blocked |= t.halted;
                waiting |= t.unescorted;
            }
            if (out.length() > 0) out.append("; ");
            out.append(c.number).append(' ').append(c.phase.name().toLowerCase()).append(' ')
                    .append(lead).append('/').append(corridor.size() - 1);
            if (blocked) out.append(" blocked");
            if (waiting) out.append(String.format(Locale.ROOT, " escort (marines %.0f, armour %.0f cells off)",
                    marine, guard));
        }
        return out.length() == 0 ? "-" : out.toString();
    }

    /** The corridor cell where the road crosses the line. */
    public int gapIndex() { return gapIndex; }

    /** Live trucks, for evidence. */
    public List<Long> trucks() {
        List<Long> out = new ArrayList<>();
        for (Convoy c : convoys) for (Truck t : c.trucks) if (!t.gone) out.add(t.hull);
        return out;
    }

    /** One tick: brings convoys on, drives them a leg at a time, loads them at the gate, takes them off. */
    public void advance(BattleSimulation sim, int tick, Consumer<String> beat) {
        applyDetour();
        if (corridor.size() < 2) return;
        boolean review = tick % REVIEW_TICKS == 0;
        List<float[]> swarm = review ? positions(sim, true) : List.of();
        company = review ? positions(sim, false) : company;
        guards = review ? guards(sim) : guards;
        for (Convoy c : convoys) {
            if (c.phase == Phase.OUT || c.phase == Phase.LOST) continue;
            if (c.phase == Phase.WAITING) {
                if (tick < c.departTick) continue;
                c.phase = Phase.INBOUND;
                beat.accept("convoy_entered_" + c.number);
            }
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
                    for (Truck t : c.trucks) {
                        if (t.gone) continue;
                        live++;
                        if (!t.atGate) comeToGate(sim, c, t, swarm);
                        if (t.atGate) atGate++;
                    }
                    if (!leaves(tick - c.loadingSince, LOADING_TICKS + c.extraLoading, live, atGate)) continue;
                    // Only a truck at the gate takes people aboard; one still on its way turns back empty.
                    for (Truck t : c.trucks) {
                        if (t.gone || !t.atGate) continue;
                        t.people = Math.min(SEATS, town);
                        town -= t.people;
                    }
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
    }

    /** Trucks come on one at a time at the rear end, each once the one ahead has made room. */
    private void comeOn(BattleSimulation sim, Convoy c) {
        if (c.phase != Phase.INBOUND || c.trucks.size() >= TRUCKS) return;
        if (!c.trucks.isEmpty()) {
            Truck ahead = c.trucks.get(c.trucks.size() - 1);
            if (!ahead.gone && nearestIndex(sim.convoy().body(ahead.hull)) < entry + SPACING) return;
        }
        int[] start = corridor.get(Math.min(corridor.size() - 1, entry));
        int[] toward = corridor.get(Math.min(corridor.size() - 1, entry + 3));
        float facing = (float) Math.toDegrees(Math.atan2(toward[1] - start[1], toward[0] - start[0])) - 90f;
        float x = start[0] + 0.5f, y = start[1] + 0.5f;
        long hull = sim.convoy().spawn(VehicleType.SUPPLY_TRUCK, Faction.ALLY, VehicleMission.deployed(x, y));
        // Initial pose, before it has moved; later motion goes through the chassis controller.
        sim.convoy().body(hull).teleport(x, y, facing);
        Truck t = new Truck();
        t.hull = hull;
        c.trucks.add(t);
        route(sim, t, +1);
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
            if (at >= end - AT_LEG) {
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
            beat.accept("convoy_at_town_" + c.number);
        }
    }

    /**
     * A truck of a loading convoy still on its way to the gate keeps driving there: it is not frozen where it stood
     * when the first truck arrived (town Road, verdant 7: the second truck stood paused 45 cells short of the gate for
     * the whole loading, and was then given six people who never boarded it).
     */
    private void comeToGate(BattleSimulation sim, Convoy c, Truck t, List<float[]> swarm) {
        int at = nearestIndex(sim.convoy().body(t.hull));
        if (at >= corridor.size() - 1 - AT_LEG) {
            t.atGate = true;
            t.halted = t.unescorted = false;
            sim.convoy().mission(t.hull).authoredRoutePaused = true;
            return;
        }
        drive(sim, c, t, at, +1, swarm);
    }

    /**
     * Whether a loading convoy turns for home: its loading time is up and every live truck is at the gate, or a
     * straggler has been waited for {@link #STRAGGLER_TICKS} past it.
     *
     * @param waited  ticks since the first truck reached the gate
     * @param loading ticks its people take to board
     */
    static boolean leaves(int waited, int loading, int live, int atGate) {
        if (live <= 0 || waited < loading) return false;
        return atGate >= live || waited >= loading + STRAGGLER_TICKS;
    }

    private void outbound(BattleSimulation sim, Convoy c, List<float[]> swarm, Consumer<String> beat) {
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            int at = nearestIndex(body);
            if (at <= entry + AT_LEG) {
                // Back where it set out, with its people.
                peopleOut += t.people;
                c.people += t.people;
                t.people = 0;
                t.gone = true;
                sim.convoy().despawn(t.hull);
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
        mission.authoredRoutePaused = t.halted || t.unescorted || crowded(sim, c, t, at, direction);
        int tick = sim.getSimTickIndex();
        // Stuck is no headway along the road, not standing still: a truck circling a point it cannot
        // turn onto is moving all the time.
        if (t.bestAt < 0 || t.direction != direction || (at - t.bestAt) * direction > 0) {
            t.bestAt = at;
            t.movedTick = tick;
        }
        if (mission.authoredRoutePaused) t.movedTick = tick;
        boolean stuck = tick - t.movedTick > STUCK_TICKS;
        if (stuck && unstick(sim, t, at, direction)) return;
        if (mission.authoredRouteX == null || t.direction != direction || stuck) route(sim, t, direction);
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
        inward = lay(sim, entry, corridor.size() - 1);
        outward = lay(sim, corridor.size() - 1, entry);
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
     * drivable route the planner proves for a truck; where it proves none, a way a truck's footprint
     * fits along; and the corridor's own cells where there is not even that.
     */
    private float[][] lay(BattleSimulation sim, int from, int to) {
        int direction = to >= from ? 1 : -1;
        List<float[]> points = new ArrayList<>();
        for (int i = from; i != to; ) {
            int next = direction > 0 ? Math.min(to, i + STRETCH) : Math.max(to, i - STRETCH);
            int[] a = corridor.get(i), b = corridor.get(next);
            int[] sa = VehicleRoutePlanner.snapToMask(clearance, a[0], a[1], SNAP);
            int[] sb = VehicleRoutePlanner.snapToMask(clearance, b[0], b[1], SNAP);
            float[][] way = prove(sim, sa, sb);
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
