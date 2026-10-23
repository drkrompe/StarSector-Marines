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
import java.util.function.Consumer;

/**
 * The Road's relief convoys ({@code cordon-nouns.md}, Convoy): columns of allied trucks that come on
 * at the corridor's rear end on the timetable, drive it through the gap to the cut-off town's
 * compound, wait at its gate while their share of the townspeople board, and drive back out.
 *
 * <p>A convoy does not fight its way through. A truck halts while the swarm is on the road a few
 * cells ahead of it and goes on when the road is clear, so a convoy whose road nobody clears is eaten
 * where it stands. Past the gap, in the swarm's country, it rolls only with the company beside it:
 * the company walks ahead of it ({@link #escorts}), and a truck nobody escorts waits at the line. The townspeople are a count the compound holds; they ride as a count, and a
 * truck that is destroyed takes its passengers with it.
 *
 * <p>A truck drives a route laid along the corridor once, with ordinary steering and recovery: each
 * stretch of the road a drivable route the planner proves for a truck, and where it proves none (a
 * country lane's corner tighter than it will prove a truck's turn through) the road's own cells. A
 * truck that stands still on an open route is given the rest of it again from where it stands.
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
     * Corridor cells in from the rear edge a truck comes on at, and leaves from: a hull set down on
     * the edge row has no route out of it.
     */
    static final int ENTRY = 6;
    /** Ticks between looks at the road. */
    static final int REVIEW_TICKS = 15;
    /** Cells from a truck a company marine must be for it to roll past the gap. */
    static final int ESCORT_REACH = 20;
    /** Corridor cells short of the gap where the escort is first wanted. */
    static final int ESCORT_FROM = 12;
    /** Corridor cells ahead of a convoy's lead truck its escort walks. */
    static final int ESCORT_LEAD = 14;
    /** Corridor cells in each stretch the road's route is proved over. */
    static final int STRETCH = 30;
    /** Ticks a truck may stand still on an open route before it is given the route again. */
    static final int STUCK_TICKS = 60 * 5;

    private enum Phase { WAITING, INBOUND, LOADING, OUTBOUND, OUT, LOST }

    private static final class Truck {
        long hull;
        /** Which way along the corridor its route runs: +1 to the town, -1 out. */
        int direction;
        int people;
        boolean gone;
        boolean halted;
        /** Where the road ahead of a halted truck is blocked. */
        int blockX, blockY;
        /** The tick it was last seen moving, or given its route. */
        int movedTick;
    }

    private static final class Convoy {
        final int number;
        final int departTick;
        Phase phase = Phase.WAITING;
        final List<Truck> trucks = new ArrayList<>();
        int loadingSince;
        int people;
        boolean throughGap, ambushed;

        Convoy(int number, int departTick) {
            this.number = number;
            this.departTick = departTick;
        }
    }

    private final List<int[]> corridor;
    /** The route to the town and the route out, laid along the corridor on first use. */
    private float[][] inward, outward;
    private final int gapIndex;
    private final List<Convoy> convoys = new ArrayList<>();
    private int town;
    private int peopleOut, peopleLost;
    /** The company's marines at the last look. */
    private List<float[]> company = List.of();

    /**
     * @param corridor the relief road's cells in driving order, rear edge first
     * @param gapIndex the corridor cell where it crosses the line
     * @param departTicks when each convoy comes on, in order
     * @param townspeople the people waiting in the compound
     */
    public CordonConvoys(List<int[]> corridor, int gapIndex, List<Integer> departTicks, int townspeople) {
        this.corridor = List.copyOf(corridor);
        this.gapIndex = gapIndex;
        for (int i = 0; i < departTicks.size(); i++) convoys.add(new Convoy(i + 1, departTicks.get(i)));
        this.town = townspeople;
    }

    public int convoys() { return convoys.size(); }
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

    /** Live trucks, for evidence. */
    public List<Long> trucks() {
        List<Long> out = new ArrayList<>();
        for (Convoy c : convoys) for (Truck t : c.trucks) if (!t.gone) out.add(t.hull);
        return out;
    }

    /** One tick: brings convoys on, drives them a leg at a time, loads them at the gate, takes them off. */
    public void advance(BattleSimulation sim, int tick, Consumer<String> beat) {
        if (corridor.size() < 2) return;
        boolean review = tick % REVIEW_TICKS == 0;
        List<float[]> swarm = review ? positions(sim, true) : List.of();
        company = review ? positions(sim, false) : company;
        for (Convoy c : convoys) {
            if (c.phase == Phase.OUT || c.phase == Phase.LOST) continue;
            if (c.phase == Phase.WAITING) {
                if (tick < c.departTick) continue;
                c.phase = Phase.INBOUND;
                beat.accept("convoy_entered_" + c.number);
            }
            comeOn(sim, c);
            bury(sim, c);
            if (c.trucks.isEmpty() || allGone(c)) {
                if (c.trucks.size() >= TRUCKS || c.phase != Phase.INBOUND) {
                    c.phase = Phase.LOST;
                    beat.accept("convoy_lost_" + c.number);
                }
                continue;
            }
            if (!review) continue;
            switch (c.phase) {
                case INBOUND -> inbound(sim, tick, c, swarm, beat);
                case LOADING -> {
                    if (tick - c.loadingSince < LOADING_TICKS) continue;
                    for (Truck t : c.trucks) {
                        if (t.gone) continue;
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
            if (!ahead.gone && nearestIndex(sim.convoy().body(ahead.hull)) < ENTRY + SPACING) return;
        }
        int[] start = corridor.get(Math.min(corridor.size() - 1, ENTRY));
        int[] toward = corridor.get(Math.min(corridor.size() - 1, ENTRY + 3));
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

    private void outbound(BattleSimulation sim, Convoy c, List<float[]> swarm, Consumer<String> beat) {
        for (Truck t : c.trucks) {
            if (t.gone) continue;
            GroundBody body = sim.convoy().body(t.hull);
            int at = nearestIndex(body);
            if (at <= ENTRY + AT_LEG) {
                // Off the map at the rear edge with its people.
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
        // In the swarm's country a truck waits for the company beside it.
        boolean unescorted = at >= gapIndex - ESCORT_FROM && !escorted(sim.convoy().body(t.hull));
        mission.authoredRoutePaused = t.halted || unescorted || crowded(sim, c, t, at, direction);
        int tick = sim.getSimTickIndex();
        if (mission.authoredRoutePaused || Math.abs(sim.convoy().body(t.hull).speed) > STILL) t.movedTick = tick;
        boolean stuck = tick - t.movedTick > STUCK_TICKS;
        if (mission.authoredRouteX == null || t.direction != direction || stuck) route(sim, t, direction);
    }

    /** Speed, cells a second, below which a truck is standing still. */
    private static final float STILL = 0.1f;

    /** Whether a company marine stands within {@link #ESCORT_REACH} of a truck. */
    private boolean escorted(GroundBody body) {
        for (float[] m : company) if (body.distanceTo(m[0], m[1]) <= ESCORT_REACH) return true;
        return false;
    }

    /**
     * Where each convoy on the road wants its escort: a few cells ahead of its lead truck along the
     * road in the way it is going, or at the gate while it loads. What the company is asked to walk.
     */
    public List<int[]> escorts(BattleSimulation sim) {
        List<int[]> out = new ArrayList<>();
        for (Convoy c : convoys) {
            if (c.phase != Phase.INBOUND && c.phase != Phase.LOADING && c.phase != Phase.OUTBOUND) continue;
            int direction = c.phase == Phase.OUTBOUND ? -1 : 1;
            int lead = -1;
            for (Truck t : c.trucks) {
                if (t.gone) continue;
                int at = nearestIndex(sim.convoy().body(t.hull));
                if (lead < 0 || (at - lead) * direction > 0) lead = at;
            }
            if (lead < 0 || lead < gapIndex - 2 * ESCORT_FROM && direction > 0) continue;
            if (direction < 0 && lead < gapIndex - ESCORT_FROM) continue;
            int ahead = Math.max(0, Math.min(corridor.size() - 1, lead + direction * ESCORT_LEAD));
            out.add(corridor.get(ahead).clone());
        }
        return out;
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

    /** Hands a truck the rest of the road's route in its direction, from the point of it nearest the truck. */
    private void route(BattleSimulation sim, Truck t, int direction) {
        if (inward == null) {
            inward = lay(sim, ENTRY, corridor.size() - 1);
            outward = lay(sim, corridor.size() - 1, ENTRY);
        }
        float[][] path = direction > 0 ? inward : outward;
        GroundBody body = sim.convoy().body(t.hull);
        int from = 0;
        float best = Float.MAX_VALUE;
        for (int k = 0; k < path[0].length; k++) {
            float d = body.distanceTo(path[0][k], path[1][k]);
            if (d < best) {
                best = d;
                from = k;
            }
        }
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
     * drivable route the planner proves for a truck, or the corridor's own cells where it proves none.
     */
    private float[][] lay(BattleSimulation sim, int from, int to) {
        int direction = to >= from ? 1 : -1;
        VehicleClearance clearance = VehicleClearance.erode(sim.getGrid(),
                VehicleClearance.radiusForWidth(VehicleType.SUPPLY_TRUCK.visualWidthCells));
        TerrainCostField cost = TerrainCostField.from(sim.getTopology());
        List<float[]> points = new ArrayList<>();
        for (int i = from; i != to; ) {
            int next = direction > 0 ? Math.min(to, i + STRETCH) : Math.max(to, i - STRETCH);
            int[] a = corridor.get(i), b = corridor.get(next);
            int[] sa = VehicleRoutePlanner.snapToMask(clearance, a[0], a[1], SNAP);
            int[] sb = VehicleRoutePlanner.snapToMask(clearance, b[0], b[1], SNAP);
            float[][] proved = sa == null || sb == null ? null : VehicleRoutePlanner.routeDrivable(
                    sa[0], sa[1], sb[0], sb[1], sim.getGrid(), cost, clearance, VehicleType.SUPPLY_TRUCK);
            if (proved != null && proved[0].length > 0) {
                for (int k = 0; k < proved[0].length; k++) points.add(new float[]{proved[0][k], proved[1][k]});
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
     * Where convoys stand halted with the road ahead blocked, one place a convoy: what the company
     * is asked to clear ({@code cordon-nouns.md}, Flow, the Road).
     */
    public List<int[]> roadBlocks() {
        List<int[]> out = new ArrayList<>();
        for (Convoy c : convoys) {
            for (Truck t : c.trucks) {
                if (t.gone || !t.halted) continue;
                out.add(new int[]{t.blockX, t.blockY});
                break;
            }
        }
        return out;
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

    /** Where the swarm's bodies stand, or the company's marines. */
    private static List<float[]> positions(BattleSimulation sim, boolean swarm) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            Faction faction = sim.identity().faction(unit);
            boolean wanted = swarm ? faction == Faction.DEFENDER && sim.identity().type(unit).isSwarm()
                    : faction == Faction.MARINE;
            if (wanted) out.add(new float[]{sim.world().x(unit), sim.world().y(unit)});
        }
        return out;
    }
}
