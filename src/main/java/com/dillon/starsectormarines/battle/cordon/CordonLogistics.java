package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
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
 * garrison. Between carries a carrier is support: it stays behind the redoubt it delivered to and
 * answers the nearest call as the armour does, its gun on the swarm. When squads wait at the base,
 * as many carriers as there are squads come back to their slots, the nearest first; the rest stay
 * on the line.
 *
 * <p><b>Exits are controlled.</b> The apron is the ground outside the shed's machine door. A finished
 * hull waits on its berth until the one before it has driven clear of the door, so two never meet in
 * the lane; a carrier waits on its own slot, never in the doorway; and a car or technical leaves the
 * apron for its home behind the line at once.
 */
public final class CordonLogistics {

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
    /** Ticks a leaving hull has to get clear of the door before the next is let out anyway. */
    static final int EXIT_PATIENCE_TICKS = 60 * 45;
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
    private final int musterX, musterY;
    private final int doorX, doorY;
    private final List<int[]> slots;
    private final List<int[]> armourHomes;
    private final List<Long> carriers = new ArrayList<>();
    private final Map<Long, int[]> carrierSlot = new HashMap<>();
    private final Deque<Long> leaving = new ArrayDeque<>();
    /** The redoubt each carrier between carries is supporting, so a call is answered once. */
    private final Map<Long, CordonRedoubt> supporting = new HashMap<>();
    /**
     * Hulls on their way out to the threshold, and where each goes on to: a car's or technical's home
     * behind the line, a carrier's staging slot.
     */
    private final Map<Long, int[]> outbound = new HashMap<>();
    private final int thresholdX, thresholdY;
    private final Deque<Integer> waiting = new ArrayDeque<>();
    private final RiskLevel risk;
    private final Random rng;
    private final List<String> landed = new ArrayList<>();
    private long exiting;
    private int exitingSince;
    private int carried, built, homed;

    private CordonLogistics(CordonArmour armour, int[] door, List<int[]> slots, List<int[]> armourHomes,
                            RiskLevel risk, Random rng) {
        this.armour = armour;
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
     */
    public static CordonLogistics at(BattleSimulation sim, CordonArmour armour, List<Gantry> berths,
                                     int fallbackX, int fallbackY, List<CordonRedoubt> redoubts,
                                     RiskLevel risk, Random rng) {
        List<int[]> armourHomes = new ArrayList<>();
        for (CordonRedoubt r : redoubts) armourHomes.add(armour.behind(r));
        NavigationGrid grid = sim.getGrid();
        int[] door = machineDoor(grid, berths);
        List<int[]> slots = new ArrayList<>();
        if (door != null) {
            int outX = door[2], outY = door[3];
            for (int i = 0; i < SLOTS; i++) {
                int offset = (i - (SLOTS - 1) / 2) * SLOT_PITCH;
                // Ground a carrier's footprint fits on, not merely ground a man can stand on.
                int[] cell = CordonArmour.drivable(sim, VehicleType.HEAVY_APC,
                        door[0] + outX * APRON_DEPTH + outY * offset, door[1] + outY * APRON_DEPTH + outX * offset);
                if (cell != null) slots.add(cell);
            }
        }
        if (slots.isEmpty()) {
            int[] cell = CordonGround.nearestWalkable(grid, fallbackX, fallbackY);
            if (cell == null) return null;
            slots.add(cell);
            door = new int[]{cell[0], cell[1], 0, 0};
        }
        return new CordonLogistics(armour, door, slots, armourHomes, risk, rng);
    }

    /** Cells from a redoubt's middle the apron's carriers must be able to drive to. */
    static final int APRON_REACH = 20;

    /**
     * Whether a carrier set down on the apron outside the shed's machine door can drive to every
     * one of {@code places}: ground its footprint's clearance holds, joined to the apron's, within
     * {@link #APRON_REACH} cells of each, stepping as the route planner steps. A base whose fence and lanes are too narrow for a carrier
     * kept every one it built standing on its apron, refused a route to the line, and the shed's
     * lane jammed behind them. Off a map without a machine door it is {@code true}: such a base
     * builds nothing.
     */
    public static boolean apronReaches(NavigationGrid grid, List<Gantry> berths, List<int[]> places) {
        int[] door = machineDoor(grid, berths);
        if (door == null) return true;
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
                if (!reached[i] && Math.max(Math.abs(x - places.get(i)[0]), Math.abs(y - places.get(i)[1])) <= APRON_REACH) {
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

    /** Puts a carrier standing on the field to work from the base, on a staging slot of its own. */
    public void addCarrier(BattleSimulation sim, long hull) {
        armour.command().enlist(sim, hull);
        int[] slot = slots.get(carriers.size() % slots.size());
        carrierSlot.put(hull, slot);
        carriers.add(hull);
        armour.command().station(sim, hull, slot[0], slot[1]);
    }

    /** A machine off the shed's stocks: it waits on its berth to be let out. */
    public void rolledOut(BattleSimulation sim, long hull) {
        built++;
        leaving.add(hull);
    }

    /** The orders a column on the timetable marches under: to the base's apron, where it waits. */
    public AlliedColumns.Orders columnOrders() {
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
            }
        };
    }

    /**
     * One tick: lets the next finished machine out once the door is clear, loads a waiting squad
     * into a carrier on its slot, and calls a carrier back to its slot when squads wait.
     */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts, Consumer<String> beat) {
        AlliedCommand command = armour.command();
        for (String told : landed) beat.accept(told);
        landed.clear();
        carriers.retainAll(command.hulls());
        outbound.keySet().retainAll(command.hulls());
        for (Map.Entry<Long, int[]> leg : new ArrayList<>(outbound.entrySet())) {
            if (!near(sim.convoy().body(leg.getKey()), new int[]{thresholdX, thresholdY}, AT_THRESHOLD)) continue;
            outbound.remove(leg.getKey());
            if (carrierSlot.containsKey(leg.getKey())) {
                command.station(sim, leg.getKey(), leg.getValue()[0], leg.getValue()[1]);
            } else {
                armour.adopt(sim, leg.getKey(), leg.getValue()[0], leg.getValue()[1]);
            }
        }
        letOut(sim);
        if (sim.getSimTickIndex() % CordonArmour.REVIEW_TICKS != 0) return;
        waiting.removeIf(squad -> sim.squadMemberCount(squad) == 0);
        supporting.keySet().retainAll(carriers);
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
            CordonRedoubt to = neediest(sim, redoubts);
            if (to == null || waiting.isEmpty()) continue;
            int squad = waiting.poll();
            int[] drop = armour.behind(to);
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
     * A carrier between carries: to a flank of the nearest calling redoubt, behind the armour's
     * places there; with no call it stays where it last stood, behind the redoubt it fed.
     */
    private void support(BattleSimulation sim, long hull, List<CordonRedoubt> redoubts) {
        CordonRedoubt call = CordonArmour.nearestCalling(sim.convoy().body(hull), redoubts);
        if (call == null || call == supporting.get(hull)) return;
        supporting.put(hull, call);
        // Behind the first two armour places on each side, by its own rank among the carriers.
        int[] post = armour.flank(call, 4 + carriers.indexOf(hull));
        armour.command().station(sim, hull, post[0], post[1]);
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
            } else if (gone || sim.getSimTickIndex() - exitingSince > EXIT_PATIENCE_TICKS) {
                exiting = 0L;
            } else {
                return;
            }
        }
        while (!leaving.isEmpty()) {
            long hull = leaving.peek();
            if (!sim.convoy().isVehicle(hull) || sim.convoy().structure(hull) <= 0f) {
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
    /** Ticks before the tug looks again at a threshold it could not tow a hull onto. */
    static final int TOW_RETRY_TICKS = 60 * 5;

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

    /** A calling redoubt first, then the one with the fewest standing; never a fallen one. */
    private static CordonRedoubt neediest(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        CordonRedoubt best = null;
        int bestScore = Integer.MAX_VALUE;
        for (CordonRedoubt r : redoubts) {
            if (r.state() == CordonRedoubt.State.FALLEN) continue;
            int score = CordonSequence.standing(sim, r) - (r.state() == CordonRedoubt.State.CALLING ? 1000 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }
}
