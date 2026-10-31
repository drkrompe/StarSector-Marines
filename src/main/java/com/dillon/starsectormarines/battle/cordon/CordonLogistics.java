package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.command.allied.AlliedDepot;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The allies' logistics base behind the line ({@code cordon-nouns.md}, Logistics base): reinforcement
 * squads walk in off the rear edge to its apron, its shed builds the allies' cars and carriers, and
 * its carriers take waiting squads up to the redoubt that needs them most, where they join the
 * garrison. Between carries a carrier is support: it stays behind the redoubt it delivered to, its gun on the
 * swarm, and is re-homed only when that redoubt falls or is given up ({@code cordon-allied-command.md}, rule 2:
 * sectors, never calls). When squads wait at the base,
 * as many carriers as there are squads come back to their slots, the nearest first; the rest stay
 * on the line.
 *
 * <p><b>Exits are controlled.</b> The apron is the ground outside the shed's machine door. A finished
 * hull waits on its berth until the one before it has driven clear of the door, so two never meet in
 * the lane; a carrier waits on its own slot, never in the doorway; and a car or technical leaves the
 * apron for its home behind the line at once.
 *
 * <p>It is the Cordon's {@link AlliedDepot}: a machine sent from it (a beat's truck or dozer) sets out
 * from the threshold outside the machine door, and nothing is sent while the swarm is on the apron.
 */
public final class CordonLogistics implements AlliedDepot {

    /** Cells round the apron the swarm must be clear of for the base to send. */
    static final int OVERRUN_REACH = 10;

    /** Cells from a slot a column counts as at the base, and a carrier as home. */
    static final int AT_BASE = 8;
    /** Cells out from the machine door the apron's slots stand: past the threshold, room to turn. */
    static final int APRON_DEPTH = 9;
    /** Cells between one staging slot and the next, across the door. */
    static final int SLOT_PITCH = 5;
    /** Staging slots on the apron. */
    static final int SLOTS = 3;
    /** Cells past the door a leaving hull must be before the next is let out. */
    static final int DOOR_CLEAR = 5;
    /** Ticks (45 seconds) a leaving hull has to get clear of the door before the next is let out anyway. */
    static final int EXIT_PATIENCE_TICKS = Math.round(45f / BattleSimulation.TICK_DT);
    /** Cells out of the door a car or technical drives to before it goes on to its home. */
    static final int THRESHOLD = 4;
    /** Cells from the threshold that count as through it. */
    static final int AT_THRESHOLD = 3;
    /** Cells between the places carriers set down behind one redoubt. */
    static final int DROP_SPACING = 5;
    /** Cells of lane that must be clear of other hulls before a finished one is drawn into it. */
    static final float LANE_CLEAR = 4f;
    /** Doorway cells in a row that make a machine door rather than a hand door. */
    static final int MACHINE_DOOR = 3;
    /** Cells round the berths a shed's walls can stand: its vestibule and its shop. */
    static final int SHED_MARGIN = 12;

    private final CordonArmour armour;
    /** What this density's allies field ({@link CordonAlliedTable}): whether carriers go forward, and where they set down. */
    private final CordonAlliedTable table;
    private final int musterX, musterY;
    private final int doorX, doorY;
    private final List<int[]> slots;
    private final List<int[]> armourHomes;
    private final List<Long> carriers = new ArrayList<>();
    private final Map<Long, int[]> carrierSlot = new HashMap<>();
    private final Deque<Long> leaving = new ArrayDeque<>();
    /** The redoubt each carrier last fed, which it stands behind between carries. */
    private final Map<Long, CordonRedoubt> supporting = new HashMap<>();
    /** The redoubt each carrier now carrying a squad is bound for: that redoubt has help coming. */
    private final Map<Long, CordonRedoubt> bound = new HashMap<>();
    /**
     * Hulls on their way out to the threshold, and where each goes on to: a car's or technical's home
     * behind the line, a carrier's staging slot.
     */
    private final Map<Long, int[]> outbound = new HashMap<>();
    private final int thresholdX, thresholdY;
    private final Deque<Integer> waiting = new ArrayDeque<>();
    /** The redoubt each waiting replacement squad was called for ({@code cordon-nouns.md}, Replacements), by squad. */
    private final Map<Integer, CordonRedoubt> waitingFor = new HashMap<>();
    /** The squad each carrier has aboard, while it carries. */
    private final Map<Long, Integer> aboard = new HashMap<>();
    private final RiskLevel risk;
    private final Random rng;
    private final List<String> landed = new ArrayList<>();
    private long exiting;
    private int exitingSince;
    private int carried, built, homed;
    /** The company's lifts ({@code cordon-allied-command.md}, rule 10): asked, granted, and set down. */
    private int liftsAsked, liftsGranted, liftsLanded;
    /** Cells round a redoubt a duty may be and still be set down behind it. */
    static final int LIFT_REACH = 6;
    /**
     * Ticks a squad a lift brought no nearer its duty walks before it may ask again (a minute): a carrier that cannot
     * reach the drop stops where the squad boarded and sets it down there, and asked again it would do the same.
     */
    static final int FAILED_LIFT_TICKS = 1800;
    /** The tick until which each squad a lift failed may not ask again, by squad. */
    private final Map<Integer, Integer> liftBarred = new HashMap<>();
    /** The tick of the last {@link #advance}, for the asks the company makes between them. */
    private int now;
    /** Lifts called off since the last review, by squad. */
    private final List<Integer> cancels = new ArrayList<>();
    /** Lifts asked since the last review: {hull, squad, x, y}. */
    private final List<long[]> asks = new ArrayList<>();
    /** The company squad each lifting carrier has aboard or is waiting for. */
    private final Map<Long, Integer> lifting = new HashMap<>();
    /** The duty each lifted squad asked to be taken to, {x, y}, by squad. */
    private final Map<Integer, int[]> liftDuty = new HashMap<>();

    private CordonLogistics(CordonArmour armour, CordonAlliedTable table, int[] door, List<int[]> slots,
                            List<int[]> armourHomes, RiskLevel risk, Random rng) {
        this.armour = armour;
        this.table = table;
        this.doorX = door[0];
        this.doorY = door[1];
        this.slots = List.copyOf(slots);
        // The threshold: open ground a few cells out of the door, between it and the slots.
        this.thresholdX = door[0] + door[2] * THRESHOLD;
        this.thresholdY = door[1] + door[3] * THRESHOLD;
        int[] middle = slots.get(slots.size() / 2);
        this.musterX = middle[0];
        this.musterY = middle[1];
        this.armourHomes = List.copyOf(armourHomes);
        this.risk = risk;
        this.rng = rng;
    }

    /**
     * The base whose shed holds these berths: its machine door found on the map, its apron of
     * staging slots out in front of it. Where no machine door is found (a shed placed with only
     * hand doors) the apron is the open ground nearest the fallback cell. {@code null} where there is
     * no open ground at all.
     *
     * @param redoubts the line; cars and technicals off the stocks are posted behind its redoubts in turn
     * @param table    what this density's allies field: whether carriers lift columns up, and where they set down
     */
    public static CordonLogistics at(BattleSimulation sim, CordonArmour armour, List<Gantry> berths,
                                     int fallbackX, int fallbackY, List<CordonRedoubt> redoubts,
                                     CordonAlliedTable table, RiskLevel risk, Random rng) {
        List<int[]> armourHomes = new ArrayList<>();
        for (CordonRedoubt r : redoubts) armourHomes.add(armour.behind(r));
        NavigationGrid grid = sim.getGrid();
        int[] door = machineDoor(grid, berths);
        List<int[]> slots = new ArrayList<>();
        if (door != null) {
            int outX = door[2], outY = door[3];
            // Ground a carrier's footprint fits on, not merely ground a man can stand on; clear of the threshold every
            // hull drives out to, and of the other slots.
            boolean[] ground = armour.mainGround(sim, VehicleType.HEAVY_APC);
            int width = grid.getWidth();
            List<int[]> keepOff = new ArrayList<>();
            keepOff.add(new int[]{door[0] + outX * THRESHOLD, door[1] + outY * THRESHOLD});
            for (int i = 0; i < SLOTS; i++) {
                int offset = (i - (SLOTS - 1) / 2) * SLOT_PITCH;
                int[] cell = slotCell((x, y) -> grid.inBounds(x, y) && ground[y * width + x],
                        door[0] + outX * APRON_DEPTH + outY * offset, door[1] + outY * APRON_DEPTH + outX * offset,
                        CordonArmour.SET_DOWN_REACH, keepOff, SLOT_APART);
                if (cell == null) continue;
                slots.add(cell);
                keepOff.add(cell);
            }
        }
        if (slots.isEmpty()) {
            int[] cell = CordonGround.nearestWalkable(grid, fallbackX, fallbackY);
            if (cell == null) return null;
            slots.add(cell);
            door = new int[]{cell[0], cell[1], 0, 0};
        }
        return new CordonLogistics(armour, table, door, slots, armourHomes, risk, rng);
    }

    /** Cells a staging slot keeps from the threshold and from every other slot. */
    static final int SLOT_APART = 4;

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    /**
     * The staging slot nearest {@code (x, y)} on {@code ground}, out to {@code reach}, at least {@code apart} cells
     * (the larger of the two axes) from every cell in {@code keepOff}, or {@code null}. Two slots wanted on ground no
     * carrier fits once snapped to the one cell near both that it did (rural Line verdant 22): the second carrier
     * waited on the threshold for its slot all battle, and every hull built after it was turned aside at the door into
     * a turn it could not make and stood there.
     */
    static int[] slotCell(CellTest ground, int x, int y, int reach, List<int[]> keepOff, int apart) {
        for (int r = 0; r <= reach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (!ground.test(cx, cy)) continue;
                    boolean clear = true;
                    for (int[] k : keepOff) clear &= Math.max(Math.abs(cx - k[0]), Math.abs(cy - k[1])) >= apart;
                    if (clear) return new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    /** Cells from a redoubt's middle the apron's carriers must be able to drive to. */
    static final int APRON_REACH = 20;

    /**
     * Whether a carrier set down on the apron outside the shed's machine door can drive to every
     * one of {@code places}: ground its footprint's clearance holds, joined to the apron's, within
     * {@link #APRON_REACH} cells of each and on its level ({@link #at}), stepping as the route planner steps. A base whose fence and lanes are too narrow for a carrier
     * kept every one it built standing on its apron, refused a route to the line, and the shed's
     * lane jammed behind them. With no machine door it is {@code false} for a {@code shed}: the
     * base read its muster for the door and built one machine all battle (town Line frozen 14). An
     * open-air depot's gantries have no door to find, and it is {@code true} there.
     */
    public static boolean apronReaches(NavigationGrid grid, List<Gantry> berths, List<int[]> places, boolean shed) {
        int[] door = machineDoor(grid, berths);
        if (door == null) return !shed;
        VehicleClearance mask = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(VehicleType.HEAVY_APC.visualWidthCells));
        int w = mask.getWidth(), h = mask.getHeight();
        int[] start = null;
        int ax = door[0] + door[2] * APRON_DEPTH, ay = door[1] + door[3] * APRON_DEPTH;
        for (int r = 0; r <= APRON_DEPTH && start == null; r++) {
            for (int dx = -r; dx <= r && start == null; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (mask.isPassable(ax + dx, ay + dy)) { start = new int[]{ax + dx, ay + dy}; break; }
                }
            }
        }
        if (start == null) return false;
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        seen[start[1] * w + start[0]] = true;
        open.add(start[1] * w + start[0]);
        boolean[] reached = new boolean[places.size()];
        int left = places.size();
        while (!open.isEmpty() && left > 0) {
            int idx = open.poll(), x = idx % w, y = idx / w;
            for (int i = 0; i < places.size(); i++) {
                if (!reached[i] && at(grid.elevation(), x, y, places.get(i))) {
                    reached[i] = true;
                    left--;
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx] || !mask.isPassable(nx, ny)) continue;
                    // The route planner's own step: no wall along the edge crossed, no corner cut.
                    if (!grid.canTraverseCellStep(x, y, nx, ny)) continue;
                    seen[ny * w + nx] = true;
                    open.add(ny * w + nx);
                }
            }
        }
        return left == 0;
    }

    /**
     * Whether a carrier at {@code (x, y)} has reached {@code place}: within {@link #APRON_REACH} cells of it and on
     * its level. A redoubt on a terrace is reached on its top, never from the low ground under its cliff, which is
     * within the same reach and from where nothing a carrier carries gets in.
     */
    static boolean at(ElevationMap elevation, int x, int y, int[] place) {
        if (Math.max(Math.abs(x - place[0]), Math.abs(y - place[1])) > APRON_REACH) return false;
        return elevation == null || elevation.tier(x, y) == elevation.tier(place[0], place[1]);
    }

    /**
     * The shed's machine door: the longest straight run of doorway cells on the ring round its
     * berths, and which way out of the shed it faces, {@code [x, y, outX, outY]}, or {@code null}.
     */
    static int[] machineDoor(NavigationGrid grid, List<Gantry> berths) {
        if (berths.isEmpty()) return null;
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (Gantry g : berths) {
            left = Math.min(left, g.left());
            right = Math.max(right, g.right());
            bottom = Math.min(bottom, g.bottom());
            top = Math.max(top, g.top());
        }
        float midX = (left + right) / 2f, midY = (bottom + top) / 2f;
        int[] best = null;
        int bestRun = MACHINE_DOOR - 1;
        for (int x = left - SHED_MARGIN; x <= right + SHED_MARGIN; x++) {
            for (int y = bottom - SHED_MARGIN; y <= top + SHED_MARGIN; y++) {
                if (!grid.inBounds(x, y) || !grid.isDoorway(x, y)) continue;
                for (int axis = 0; axis < 2; axis++) {
                    int ax = axis == 0 ? 1 : 0, ay = 1 - ax;
                    // Count each run once, from its first cell.
                    if (grid.inBounds(x - ax, y - ay) && grid.isDoorway(x - ax, y - ay)) continue;
                    int run = 0;
                    while (grid.inBounds(x + ax * run, y + ay * run) && grid.isDoorway(x + ax * run, y + ay * run)) run++;
                    if (run <= bestRun) continue;
                    bestRun = run;
                    float cx = x + ax * (run - 1) / 2f, cy = y + ay * (run - 1) / 2f;
                    // Out of the shed is across the run, away from the berths.
                    int outX = ay, outY = ax;
                    if ((cx - midX) * outX + (cy - midY) * outY < 0) {
                        outX = -outX;
                        outY = -outY;
                    }
                    best = new int[]{Math.round(cx), Math.round(cy), outX, outY};
                }
            }
        }
        return best;
    }

    @Override
    public int[] dispatchCell(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            if (Math.abs(sim.world().x(unit) - musterX) <= OVERRUN_REACH
                    && Math.abs(sim.world().y(unit) - musterY) <= OVERRUN_REACH) return null;
        }
        return new int[]{thresholdX, thresholdY};
    }

    public int musterX() { return musterX; }
    public int musterY() { return musterY; }
    /** The middle of the shed's machine door. */
    public int doorX() { return doorX; }
    public int doorY() { return doorY; }
    /** The apron's staging slots. */
    public List<int[]> slots() { return slots; }
    /** Squads carried up to the line so far. */
    public int carried() { return carried; }
    /** Machines the shed has finished so far. */
    public int built() { return built; }
    /** Squads standing at the base waiting for a carrier. */
    public int waiting() { return waiting.size(); }
    /** Finished machines still on their berth, waiting to be let out. */
    public int queued() { return leaving.size(); }
    /** Live carriers. */
    public List<Long> carriers() { return List.copyOf(carriers); }

    /** Whether a carrier with a squad is bound for this redoubt now (the radio's Help coming). */
    public boolean carryingTo(CordonRedoubt r) {
        for (Map.Entry<Long, CordonRedoubt> e : bound.entrySet()) {
            if (e.getValue() == r && armour.command().carrying(e.getKey())) return true;
        }
        return false;
    }

    /** Puts a carrier standing on the field to work from the base, on a staging slot of its own. */
    public void addCarrier(BattleSimulation sim, long hull) {
        armour.command().enlist(sim, hull);
        int[] slot = slots.get(carriers.size() % slots.size());
        carrierSlot.put(hull, slot);
        carriers.add(hull);
        armour.command().station(sim, hull, slot[0], slot[1]);
    }

    /**
     * A machine off the shed's stocks. A hull waits on its berth to be let out; a mech walks out on its own and
     * joins the mechs in the street mouths ({@code cordon-allied-forces.md}).
     */
    public void rolledOut(BattleSimulation sim, long machine) {
        built++;
        if (!sim.convoy().isVehicle(machine)) {
            armour.adoptMech(sim, machine);
            return;
        }
        leaving.add(machine);
    }

    /**
     * Whether columns on the timetable muster at the base for a carrier to lift them up; otherwise they walk the
     * whole way to the redoubt that needs them most ({@link CordonAlliedTable#carriersForward}).
     */
    public boolean musters() {
        return table.carriersForward();
    }

    /** The orders a column on the timetable marches under: to the base's apron, where it waits. */
    public AlliedColumns.Orders columnOrders() {
        return columnOrders(null);
    }

    /**
     * Whether a squad that came up to the base is still on its way to the line: waiting at the muster yard, or aboard
     * a carrier taking it up.
     */
    public boolean onTheWay(int squad) {
        return waiting.contains(squad) || aboard.containsValue(squad);
    }

    /**
     * The orders a column marches under to the base's apron, where it waits; {@code bound}, the redoubt it was called
     * for, is where a carrier takes it while that redoubt stands, and {@code null} sends it where it is needed most.
     */
    public AlliedColumns.Orders columnOrders(CordonRedoubt bound) {
        return new AlliedColumns.Orders() {
            @Override public int[] destination(BattleSimulation sim) {
                return new int[]{musterX, musterY};
            }

            @Override public boolean stillNeeded(BattleSimulation sim, int x, int y) {
                return true;
            }

            @Override public boolean arrived(int x, int y, int destX, int destY) {
                return Math.abs(x - destX) <= AT_BASE && Math.abs(y - destY) <= AT_BASE;
            }

            @Override public void joined(BattleSimulation sim, int number, List<Integer> squads, int destX, int destY) {
                waiting.addAll(squads);
                if (bound != null) for (int squad : squads) waitingFor.put(squad, bound);
            }
        };
    }

    /**
     * One tick: lets the next finished machine out once the door is clear, loads a waiting squad
     * into a carrier on its slot, and calls a carrier back to its slot when squads wait.
     */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts, Consumer<String> beat) {
        AlliedCommand command = armour.command();
        now = sim.getSimTickIndex();
        for (String told : landed) beat.accept(told);
        landed.clear();
        carriers.retainAll(command.hulls());
        outbound.keySet().retainAll(command.hulls());
        for (long hull : new ArrayList<>(outbound.keySet())) {
            if (near(sim.convoy().body(hull), new int[]{thresholdX, thresholdY}, AT_THRESHOLD)) onward(sim, hull);
        }
        letOut(sim);
        if (sim.getSimTickIndex() % CordonArmour.REVIEW_TICKS != 0) return;
        waiting.removeIf(squad -> sim.squadMemberCount(squad) == 0);
        waitingFor.keySet().retainAll(waiting);
        aboard.keySet().retainAll(carriers);
        aboard.keySet().removeIf(hull -> !command.carrying(hull));
        lifting.keySet().removeIf(hull -> !command.carrying(hull));
        liftDuty.keySet().retainAll(lifting.values());
        for (int squad : cancels) cancelLift(sim, squad);
        cancels.clear();
        grantLifts(sim, redoubts);
        supporting.keySet().retainAll(carriers);
        bound.keySet().retainAll(carriers);
        bound.keySet().removeIf(hull -> !command.carrying(hull));
        List<Long> recalled = recall(sim);
        for (long hull : carriers) {
            if (command.carrying(hull) || outbound.containsKey(hull)) continue;
            if (!recalled.contains(hull)) {
                support(sim, hull, redoubts);
                continue;
            }
            supporting.remove(hull);
            int[] slot = carrierSlot.get(hull);
            if (!near(sim.convoy().body(hull), slot, AT_BASE)) {
                command.station(sim, hull, slot[0], slot[1]);
                continue;
            }
            if (waiting.isEmpty()) continue;
            // A replacement goes to the redoubt that called for it while that stands; anyone else, and a replacement
            // whose redoubt fell or was given up on the way, to the redoubt that needs it most.
            CordonRedoubt calledFor = waitingFor.get(waiting.peek());
            CordonRedoubt to = calledFor != null && calledFor.manned() ? calledFor : duty(sim, redoubts);
            if (to == null) continue;
            supporting.put(hull, to);
            bound.put(hull, to);
            int squad = waiting.poll();
            waitingFor.remove(squad);
            aboard.put(hull, squad);
            int[] drop = dropAt(sim, to);
            // Each carrier its own place behind the wall, so two sent to one redoubt do not stack.
            int lane = carriers.indexOf(hull) - (slots.size() - 1) / 2;
            int dropX = drop[0] + lane * DROP_SPACING, dropY = drop[1];
            command.carry(sim, hull, squad, dropX, dropY, (s, riders, x, y) -> {
                List<int[]> cells = CordonGround.cluster(s.getGrid(), s.getOccupancyMap(), x, y, riders);
                // Ground too cramped for the whole team is treated as none: seating assumes a cell each.
                if (cells.size() < ColonyMilitia.POST_SIZE) return;
                carried++;
                int team = ColonyMilitia.seatTeam(s, cells, "Carried team " + carried, risk, rng);
                CordonSequence.join(s, to, List.of(team));
                // Told on the tick it lands, which is later than the tick it was ordered on.
                landed.add("carried_" + carried);
            });
        }
    }

    /** A carrier the company may ask for a lift: its hull, where it stands, and the seats it has. */
    public record Carrier(long hull, float x, float y, int seats) { }

    /**
     * The carriers free to lift a company squad (rule 4 of the allied command): none while an allied squad waits at
     * the base, and none carrying, loading or on its way out of the shed.
     */
    public List<Carrier> liftable(BattleSimulation sim) {
        List<Carrier> out = new ArrayList<>();
        if (!waiting.isEmpty()) return out;
        AlliedCommand command = armour.command();
        for (long hull : carriers) {
            if (command.carrying(hull) || outbound.containsKey(hull) || !command.hulls().contains(hull)) continue;
            GroundBody body = sim.convoy().body(hull);
            out.add(new Carrier(hull, body.x, body.y, sim.transport().seatsFree(hull)));
        }
        return out;
    }

    /**
     * Each lifted company squad's ride, by squad: {carrier x, carrier y, duty x, duty y}. The squad walks to the
     * carrier, then rides to the duty it asked for.
     */
    public Map<Integer, float[]> lifting(BattleSimulation sim) {
        Map<Integer, float[]> out = new HashMap<>();
        for (Map.Entry<Long, Integer> e : lifting.entrySet()) {
            if (!armour.command().hulls().contains(e.getKey())) continue;
            GroundBody body = sim.convoy().body(e.getKey());
            int[] duty = liftDuty.get(e.getValue());
            out.put(e.getValue(), new float[]{body.x, body.y, duty[0], duty[1]});
        }
        return out;
    }

    /**
     * The company no longer wants a squad's lift (its duty ended or moved): the carrier stops waiting, or sets the
     * squad down where it is.
     */
    public void cancelLift(BattleSimulation sim, int squad) {
        for (Map.Entry<Long, Integer> e : new ArrayList<>(lifting.entrySet())) {
            if (e.getValue() != squad) continue;
            armour.command().setDown(sim, e.getKey());
            lifting.remove(e.getKey());
            liftDuty.remove(squad);
        }
    }

    /** The company asks for a lift for a squad to a duty in a carrier {@link #liftable} named; granted at review. */
    public void ask(long hull, int squad, int x, int y) {
        for (long[] a : asks) if (a[1] == squad) return;
        Integer barred = liftBarred.get(squad);
        if (barred != null && now < barred) return;
        if (lifting.containsValue(squad)) return;
        asks.add(new long[]{hull, squad, x, y});
        liftsAsked++;
    }

    /** The company calls off a squad's lift; done at review ({@link #cancelLift}). */
    public void callOff(int squad) {
        if (!cancels.contains(squad)) cancels.add(squad);
    }

    /** The company's lifts for evidence: asked, granted and set down. */
    public String liftReadings() {
        return liftsAsked + " asked, " + liftsGranted + " granted, " + liftsLanded + " set down";
    }

    /**
     * Grants the lifts asked since the last review whose carrier is still free: an allied squad waiting comes first.
     * The squad is set down behind the redoubt its duty is at, as a carried team is, else at the duty itself.
     */
    private void grantLifts(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        AlliedCommand command = armour.command();
        for (long[] ask : asks) {
            long hull = ask[0];
            int squad = (int) ask[1], x = (int) ask[2], y = (int) ask[3];
            if (!waiting.isEmpty() || command.carrying(hull) || outbound.containsKey(hull)
                    || !carriers.contains(hull) || lifting.containsValue(squad) || sim.squadMemberCount(squad) == 0
                    || sim.squadMemberCount(squad) > sim.transport().seatsFree(hull)) {
                continue;
            }
            int[] drop = {x, y};
            for (CordonRedoubt r : redoubts) if (r.manned() && r.contains(x, y, LIFT_REACH)) drop = dropAt(sim, r);
            supporting.remove(hull);
            lifting.put(hull, squad);
            liftDuty.put(squad, new int[]{x, y});
            liftsGranted++;
            int[] to = drop;
            GroundBody from = sim.convoy().body(hull);
            float boardX = from.x, boardY = from.y;
            command.lift(sim, hull, squad, to[0], to[1], CommandAuthority.MISSION_COMMAND, CordonCommand.ISSUER,
                    (s, riders, dx, dy) -> {
                        liftsLanded++;
                        // Set down no nearer the drop than half the way: the carrier could not take it there.
                        if (Math.hypot(to[0] - dx, to[1] - dy) > Math.hypot(to[0] - boardX, to[1] - boardY) / 2) {
                            liftBarred.put(squad, s.getSimTickIndex() + FAILED_LIFT_TICKS);
                        }
                    });
        }
        asks.clear();
    }

    /**
     * The carriers that go back for the squads waiting: one a squad, the nearest the base first.
     * Those already loading are counted, so a second carrier is not called for a squad boarding.
     */
    private List<Long> recall(BattleSimulation sim) {
        AlliedCommand command = armour.command();
        List<Long> free = new ArrayList<>();
        for (long hull : carriers) if (!command.carrying(hull)) free.add(hull);
        free.sort((a, b) -> Float.compare(
                sim.convoy().body(a).distanceTo(musterX, musterY),
                sim.convoy().body(b).distanceTo(musterX, musterY)));
        return free.subList(0, Math.min(waiting.size(), free.size()));
    }

    /**
     * A carrier between carries keeps its sector ({@code cordon-allied-command.md}, rule 2): it stands where it set its
     * last squad down, behind the redoubt it fed, and does not chase calls. Only when that redoubt falls or is given up
     * is it re-homed, to the redoubt a waiting squad would go to ({@link CordonAlliedRules#carrierDuty}): behind its
     * rear wall, or roadbound outside its rear gate. A carrier that has fed nobody stays on its slot.
     */
    private void support(BattleSimulation sim, long hull, List<CordonRedoubt> redoubts) {
        CordonRedoubt fed = supporting.get(hull);
        if (fed == null || fed.manned()) return;
        CordonRedoubt to = duty(sim, redoubts);
        if (to == null) {
            supporting.remove(hull);
            return;
        }
        supporting.put(hull, to);
        int[] drop = dropAt(sim, to);
        int lane = carriers.indexOf(hull) - (slots.size() - 1) / 2;
        armour.command().station(sim, hull, drop[0] + lane * DROP_SPACING, drop[1]);
    }

    /** Where a carrier sets its squad down at a redoubt: behind its rear wall, or, roadbound, outside its rear gate. */
    private int[] dropAt(BattleSimulation sim, CordonRedoubt r) {
        return table.drop() == CordonAlliedTable.CarrierDrop.REAR_GATE ? armour.rearGate(sim, r) : armour.behind(r);
    }

    /** Sends a hull through the threshold on to where it was going: a carrier to its slot, any other to its home. */
    private void onward(BattleSimulation sim, long hull) {
        int[] to = outbound.remove(hull);
        if (to == null) return;
        if (carrierSlot.containsKey(hull)) armour.command().station(sim, hull, to[0], to[1]);
        else armour.adopt(sim, hull, to[0], to[1]);
    }

    /** One hull through the door at a time: the next goes once the last is clear of it, or gone. */
    private void letOut(BattleSimulation sim) {
        if (exiting != 0L) {
            boolean gone = !sim.convoy().isVehicle(exiting) || sim.convoy().structure(exiting) <= 0f;
            if (!gone && !near(sim.convoy().body(exiting), new int[]{doorX, doorY}, DOOR_CLEAR)
                    && pastDoor(sim.convoy().body(exiting))) {
                exiting = 0L;
            } else if (!gone && sim.getSimTickIndex() - exitingSince > EXIT_PATIENCE_TICKS
                    && !pastDoor(sim.convoy().body(exiting))) {
                // Still inside the door: the tug tows it out to the threshold, or waits to.
                if (!towOut(sim, exiting)) {
                    exitingSince = sim.getSimTickIndex() - EXIT_PATIENCE_TICKS + TOW_RETRY_TICKS;
                    return;
                }
                exiting = 0L;
            } else if (!gone && sim.getSimTickIndex() - exitingSince > EXIT_PATIENCE_TICKS) {
                // Out of the door but still standing in front of it: the next one let out stood on top of it, and
                // they piled up there (rural Line verdant 22: six hulls on one cell). The tug pulls it clear onto the
                // apron, and it goes on from there; the next waits until it has.
                if (!towOut(sim, exiting)) {
                    exitingSince = sim.getSimTickIndex() - EXIT_PATIENCE_TICKS + TOW_RETRY_TICKS;
                    return;
                }
                onward(sim, exiting);
                exiting = 0L;
            } else if (gone) {
                exiting = 0L;
            } else {
                return;
            }
        }
        while (!leaving.isEmpty()) {
            long hull = leaving.peek();
            // A machine lost on its berth frees its id, and the id may be on another side's hull by now.
            if (!sim.convoy().isVehicle(hull) || sim.convoy().structure(hull) <= 0f
                    || sim.convoy().faction(hull) != Faction.ALLY) {
                leaving.poll();
                continue;
            }
            if (!rollIntoLane(sim, hull)) return;
            leaving.poll();
            VehicleType type = sim.convoy().vehicleType(hull);
            // A carrier works from a slot of its own; one more than there are slots fights with the armour.
            if (type != null && type.capacity > 0 && carriers.size() < slots.size()) {
                addCarrier(sim, hull);
                // Straight out of the door like the rest, then over to its slot.
                armour.command().station(sim, hull, thresholdX, thresholdY);
                outbound.put(hull, carrierSlot.get(hull));
            } else {
                // Out of the shed first, a short drive to the threshold; its home behind the line after.
                int[] home = armourHomes.isEmpty() ? new int[]{musterX, musterY}
                        : armourHomes.get(homed++ % armourHomes.size());
                armour.command().enlist(sim, hull);
                armour.command().station(sim, hull, thresholdX, thresholdY);
                outbound.put(hull, home);
            }
            exiting = hull;
            exitingSince = sim.getSimTickIndex();
            return;
        }
    }

    /**
     * The shed's tug: a finished hull is drawn out of its berth into the service lane in front of
     * it, turned to face the machine door. A berth is fitted round a machine standing in it, its
     * mouth no wider than the hull, and a hull cannot turn out of it on its own; the lane is wide
     * enough to drive down. Only when that stretch of lane is clear of every other hull, so a
     * hull is never drawn out into another.
     *
     * @return whether it is in the lane now
     */
    private boolean rollIntoLane(BattleSimulation sim, long hull) {
        if (doorX == musterX && doorY == musterY) return true;
        GroundBody body = sim.convoy().body(hull);
        int outX = Integer.signum(musterX - doorX), outY = Integer.signum(musterY - doorY);
        // The lane runs out of the door, so its middle is the door's line across it.
        float x = outX != 0 ? body.x : doorX + 0.5f;
        float y = outX != 0 ? doorY + 0.5f : body.y;
        VehicleType type = sim.convoy().vehicleType(hull);
        int radius = VehicleClearance.radiusForWidth(type == null ? 1f : type.visualWidthCells);
        if (!VehicleClearance.fitsAt(sim.getGrid(), (int) x, (int) y, radius)) return true;
        // A mech the shed finished before it walks out down the same lane: the hull waits for it to pass.
        if (mechNear(sim, x, y, LANE_CLEAR)) return false;
        for (long other : sim.convoy().entityIds()) {
            if (other == hull || sim.convoy().structure(other) <= 0f) continue;
            GroundBody blocker = sim.convoy().body(other);
            if (blocker.distanceTo(x, y) >= LANE_CLEAR) continue;
            // A hull standing in the lane inside the door (a carrier pulled back into the doorway on
            // its way home, one never proved a way out) is towed out of the way; else wait.
            boolean stranded = !pastDoor(blocker) && Math.abs(blocker.speed) < STANDING
                    && sim.convoy().faction(other) == Faction.ALLY;
            if (!stranded || !towOut(sim, other)) return false;
        }
        float heading = (float) Math.toDegrees(Math.atan2(outY, outX)) - 90f;
        body.teleport(x, y, heading);
        return true;
    }

    /** Speed, cells a second, below which a hull in the lane is standing. */
    static final float STANDING = 0.1f;
    /** Ticks (five seconds) before the tug looks again at a threshold it could not tow a hull onto. */
    static final int TOW_RETRY_TICKS = Math.round(5f / BattleSimulation.TICK_DT);

    /**
     * The tug's second pull: a hull let out that is still inside the door when its patience is up
     * is towed through it onto the threshold, facing out, where that ground takes its footprint and
     * no other hull stands; it then goes on to where it was going. A carrier steers too wide to be
     * proved a route out of a service lane narrower than its footprint's clearance, and one that
     * stood there stopped the shed: the next machine is drawn out into that same stretch of lane.
     * So is a hull standing in the lane when the next is to be drawn out.
     *
     * @return whether it was towed
     */
    private boolean towOut(BattleSimulation sim, long hull) {
        GroundBody body = sim.convoy().body(hull);
        VehicleType type = sim.convoy().vehicleType(hull);
        if (type == null) return false;
        int outX = Integer.signum(musterX - doorX), outY = Integer.signum(musterY - doorY);
        float heading = (float) Math.toDegrees(Math.atan2(outY, outX)) - 90f;
        // The threshold, or a little further out where a long hull's tail would still be in the door.
        for (int step = 0; step <= TOW_REACH; step++) {
            float x = thresholdX + outX * step + 0.5f, y = thresholdY + outY * step + 0.5f;
            if (!VehicleFootprint.isPoseFeasible(x, y, heading, type.visualLengthCells, type.visualWidthCells,
                    sim.getGrid()) || crowdedAt(sim, hull, x, y)) continue;
            body.teleport(x, y, heading);
            armour.command().station(sim, hull, (int) x, (int) y);
            return true;
        }
        return false;
    }

    /** Cells past the threshold the tug may tow a hull to find ground its footprint fits. */
    static final int TOW_REACH = 6;

    private static boolean crowdedAt(BattleSimulation sim, long hull, float x, float y) {
        for (long other : sim.convoy().entityIds()) {
            if (other == hull || sim.convoy().structure(other) <= 0f) continue;
            if (sim.convoy().body(other).distanceTo(x, y) < LANE_CLEAR) return true;
        }
        return mechNear(sim, x, y, LANE_CLEAR);
    }

    /** Whether a living mech stands within {@code reach} cells of a point. */
    private static boolean mechNear(BattleSimulation sim, float x, float y, float reach) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.HEAVY_MECH || !sim.world().hasPosition(unit)) continue;
            if (Math.hypot(sim.world().x(unit) - x, sim.world().y(unit) - y) < reach) return true;
        }
        return false;
    }

    /** Whether a hull is on the apron's side of the door rather than still in the shed. */
    private boolean pastDoor(GroundBody body) {
        float outX = Integer.signum(musterX - doorX), outY = Integer.signum(musterY - doorY);
        return (body.x - doorX) * outX + (body.y - doorY) * outY > 0f;
    }

    private static boolean near(GroundBody body, int[] cell, int reach) {
        return Math.abs(body.x - cell[0]) <= reach && Math.abs(body.y - cell[1]) <= reach;
    }

    /** Where a carrier takes a waiting squad ({@link CordonAlliedRules#carrierDuty}): calling first, then fewest standing. */
    private static CordonRedoubt duty(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        List<CordonAlliedRules.Post> posts = new ArrayList<>();
        for (CordonRedoubt r : redoubts) {
            posts.add(new CordonAlliedRules.Post(r.number(), r.manned(), r.state() == CordonRedoubt.State.CALLING,
                    CordonSequence.standing(sim, r), 0, false));
        }
        int number = CordonAlliedRules.carrierDuty(posts);
        for (CordonRedoubt r : redoubts) if (r.number() == number) return r;
        return null;
    }
}
