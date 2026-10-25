package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The allies' own command over their machines ({@code allied-command.md}): a mission says where
 * each allied hull should stand and which squads it should carry, and this turns that into the
 * hull's orders and the squad's, every tick, without the player.
 *
 * <p>Mission-neutral. A hull here is <b>enlisted</b>: it takes orders through the same mailbox the
 * player's own hulls do ({@link VehicleMoveOrderService#enlist}), so it plans drivable routes the same
 * way, and between orders it fights as an autonomous hull does. A mission owns the decisions — which
 * redoubt calls, where the barrage line is — and this owns carrying them out.
 *
 * <p>Two tasks: <b>station</b> (drive to a place and fight from it, driven back if it strays far from it)
 * and <b>carry</b> (a squad walks to the hull and boards, the hull drives it to a place, sets it down
 * there, hands it back to the mission and then stations where it set it down). Advance is a station
 * the mission keeps moving.
 */
public final class AlliedCommand {

    /**
     * What the mission does with the riders a hull has brought: it sets that many down at the drop.
     * They travel as a count, as a delivery convoy's do, not as units aboard: a squad that boards
     * leaves the field and the mission seats its riders again where they get out.
     */
    public interface Delivery {
        void delivered(BattleSimulation sim, int riders, int x, int y);
    }

    /** Cells from its hull a squad's members must all stand to board it. */
    public static final float BOARDING_REACH = 5f;
    /** Cells a stationed hull may stray from its post, fighting, before it is driven back. */
    public static final float STATION_LEASH = 16f;
    /** Cells from its drop a carrier sets its riders down at, however its route ended. */
    public static final float DROP_REACH = 6f;
    /** Ticks a carrier waits for its squad to board before it goes without them. */
    public static final int BOARDING_PATIENCE_TICKS = 60 * 60;
    /**
     * Cells a refused goal is pulled back toward the hull before it is asked again, where no way
     * there is known at all. A goal in woods or a walled yard has no drivable route, and ground a
     * step nearer usually has one.
     */
    public static final int PULLBACK_STEP = 6;
    /** Cells from a waypoint a hull counts as there, to be sent on to its post. */
    public static final float VIA_REACH = 4f;
    /** Waypoints a hull is sent through on its way to one post before it stops asking. */
    public static final int VIA_LIMIT = 24;
    /** Times a refused goal is pulled back before the hull stays where it is, until {@link #STALL_RETRY_TICKS}. */
    public static final int PULLBACK_LIMIT = 12;
    /** Cells a new post must move before a stationed hull is sent again, so a moving line is not a new order every tick. */
    public static final int RESTATION = 3;
    /**
     * Ticks a stationed hull that stopped short of its post (its refusals or waypoints spent) waits before it is
     * sent to the post again with both counts fresh. The ground changes under it — a hull or a walker that stood
     * in its way moves off, a wall comes down — and without a retry it stood where it stalled all battle.
     */
    public static final int STALL_RETRY_TICKS = Math.round(20f / BattleSimulation.TICK_DT);

    private enum Phase { STATION, BOARDING, DRIVING }

    private static final class Task {
        Phase phase;
        int postX, postY;
        /** Where it was last sent: the post, or the post pulled back after a refusal. */
        int goalX, goalY;
        int pullbacks;
        int vias;
        /** Tick it was last sent anywhere. */
        int sentAt;
        int squad = -1;
        Delivery delivery;
        int boardingSince;
        int riders;
    }

    /** A mech's post, and the standable point it was sent to. */
    private static final class MechPost {
        int postX, postY;
    }

    private final String issuer;
    private final Map<Long, Task> tasks = new LinkedHashMap<>();
    private final Map<Long, MechPost> mechs = new LinkedHashMap<>();

    /** @param issuer who gives the orders, as the command log names it */
    public AlliedCommand(String issuer) {
        this.issuer = issuer;
    }

    /** Puts an allied hull standing where it is under this command, with no task yet. */
    public void enlist(BattleSimulation sim, long hull) {
        if (sim.convoy().faction(hull) != Faction.ALLY) {
            throw new IllegalArgumentException("only an allied hull can be enlisted: " + hull);
        }
        sim.getVehicleMoveOrderService().enlist(hull);
        tasks.putIfAbsent(hull, null);
    }

    /** Sets down a new allied hull at a cell, deployed and enlisted. */
    public long deploy(BattleSimulation sim, VehicleType type, int x, int y, float facingDegrees) {
        float cx = x + 0.5f, cy = y + 0.5f;
        long hull = sim.convoy().spawn(type, Faction.ALLY, VehicleMission.deployed(cx, cy));
        // Initial pose, before it has moved; later motion goes through the chassis controller.
        sim.convoy().body(hull).teleport(cx, cy, facingDegrees);
        enlist(sim, hull);
        return hull;
    }

    /** Sends a hull to a place to fight from. A new task replaces the old. */
    public void station(BattleSimulation sim, long hull, int x, int y) {
        Task held = tasks.get(hull);
        if (held != null && held.phase == Phase.STATION
                && Math.abs(held.postX - x) < RESTATION && Math.abs(held.postY - y) < RESTATION) return;
        Task task = new Task();
        task.phase = Phase.STATION;
        task.postX = task.goalX = x;
        task.postY = task.goalY = y;
        tasks.put(hull, task);
        drive(sim, hull, task);
    }

    /**
     * Has a squad board the hull and be carried to a place; the mission gets it back there
     * through {@code delivery}, and the hull then stations at the drop.
     */
    public void carry(BattleSimulation sim, long hull, int squad, int x, int y, Delivery delivery) {
        Task task = new Task();
        task.phase = Phase.BOARDING;
        task.postX = task.goalX = x;
        task.postY = task.goalY = y;
        task.squad = squad;
        task.delivery = delivery;
        task.boardingSince = sim.getSimTickIndex();
        tasks.put(hull, task);
        GroundBody body = sim.convoy().body(hull);
        sim.getVehicleMoveOrderService().forget(hull);
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, (int) body.x, (int) body.y),
                CommandAuthority.GARRISON, issuer, "board the carrier");
    }

    /**
     * Sends an allied mech to a place to fight from, as {@link #station} does a hull: its squad
     * attack-moves to standable ground at the post, the post pulled back toward the mech a step at a
     * time when the mech cannot stand there (woods, a wall). The same post again is not a new order.
     */
    public void stationMech(BattleSimulation sim, long mech, int x, int y) {
        MechPost held = mechs.get(mech);
        if (held != null && Math.abs(held.postX - x) < RESTATION && Math.abs(held.postY - y) < RESTATION) return;
        Squad squad = sim.squadOf(mech);
        if (squad == null) return;
        MechPost post = new MechPost();
        post.postX = x;
        post.postY = y;
        mechs.put(mech, post);
        MechSpawnPlacement.Point stand = standable(sim, mech, x, y);
        if (stand == null) return;
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id, stand.cellX(), stand.cellY()),
                CommandAuthority.GARRISON, issuer, "allied mech to its post");
    }

    /** Cells from the asked-for cell a new mech is looked for standable, unoccupied ground. */
    public static final int MECH_SET_DOWN_REACH = 10;

    /**
     * Sets down a new allied mech on standable ground clear of every other body, as near {@code (x, y)} as
     * there is some within {@link #MECH_SET_DOWN_REACH}, and puts it under this command. Returns 0 where no
     * ground near takes it. It stands where it was set down until it is stationed.
     */
    public long deployMech(BattleSimulation sim, MechVariant variant, int x, int y, String name) {
        MechSpawnPlacement.Point at = null;
        for (int r = 0; at == null && r <= MECH_SET_DOWN_REACH; r++)
            for (int dy = -r; at == null && dy <= r; dy++) for (int dx = -r; at == null && dx <= r; dx++)
                if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), variant.radius, x + dx, y + dy, ANYWHERE,
                            (px, py) -> MechSpawnPlacement.unoccupied(sim.getRoster(), px, py, variant.radius, 0L));
        if (at == null) return 0L;
        int squad = sim.mintSquad(Faction.ALLY, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec(name, Faction.ALLY, UnitType.HEAVY_MECH, at.cellX(), at.cellY())
                .atPosition(at.x(), at.y()).mechVariant(variant).squad(squad));
        sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(variant, variant.defaultRole, "independent"));
        Squad s = sim.getSquad(squad);
        s.leaderId = mech;
        s.originalSize = 1;
        enlistMech(sim, mech);
        return mech;
    }

    /**
     * Puts an allied mech standing on the field (one a shed has just finished) under this command: its squad
     * is claimed under garrison authority, so nothing but this command moves it. It is stationed after.
     */
    public void enlistMech(BattleSimulation sim, long mech) {
        Squad squad = sim.squadOf(mech);
        if (squad == null) return;
        sim.claimSquadCommand(squad.id, CommandAuthority.GARRISON, issuer, "allied mech");
    }

    /** Live mechs under this command, in the order they were first stationed. */
    public List<Long> mechs() {
        return new ArrayList<>(mechs.keySet());
    }

    /** Standable ground at the post, or pulled back toward the mech until there is some. */
    private static MechSpawnPlacement.Point standable(BattleSimulation sim, long mech, int x, int y) {
        float radius = sim.physicalRadius(mech);
        float fromX = sim.world().cellX(mech) + 0.5f, fromY = sim.world().cellY(mech) + 0.5f;
        float gx = x + 0.5f, gy = y + 0.5f;
        for (int step = 0; step <= PULLBACK_LIMIT; step++) {
            MechSpawnPlacement.Point point = MechSpawnPlacement.nearCell(sim.getGrid(), radius,
                    (int) Math.floor(gx), (int) Math.floor(gy), ANYWHERE, ANYWHERE);
            if (point != null) return point;
            float dx = fromX - gx, dy = fromY - gy;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= PULLBACK_STEP) return null;
            gx += dx / length * PULLBACK_STEP;
            gy += dy / length * PULLBACK_STEP;
        }
        return null;
    }

    private static final MechSpawnPlacement.Domain ANYWHERE = (px, py) -> true;
    /** Cells an end of a waypoint search is moved to find ground the footprint fits, as a move order's is. */
    private static final int SNAP_REACH = 6;

    /** Live hulls under this command, in the order they were enlisted. */
    public List<Long> hulls() {
        return new ArrayList<>(tasks.keySet());
    }

    /** Whether a hull is carrying or about to: a mission does not re-task it. */
    public boolean carrying(long hull) {
        Task task = tasks.get(hull);
        return task != null && task.phase != Phase.STATION;
    }

    /** Whether a hull has no task. */
    public boolean idle(long hull) {
        return tasks.containsKey(hull) && tasks.get(hull) == null;
    }

    /** One tick: boards, drives, unloads and keeps stationed hulls at their posts. */
    public void advance(BattleSimulation sim) {
        mechs.keySet().removeIf(mech -> sim.resolveUnit(mech) == 0L || !sim.world().isAlive(mech));
        ConvoyService convoy = sim.convoy();
        for (Map.Entry<Long, Task> entry : new ArrayList<>(tasks.entrySet())) {
            long hull = entry.getKey();
            if (!alive(convoy, hull)) {
                tasks.remove(hull);
                continue;
            }
            Task task = entry.getValue();
            if (task == null) continue;
            if (task.phase != Phase.BOARDING && (pullBack(sim, hull, task) || reachedVia(sim, hull, task))) continue;
            switch (task.phase) {
                case BOARDING -> {
                    boolean gone = sim.squadMemberCount(task.squad) == 0;
                    boolean aboard = !gone && board(sim, hull, task);
                    boolean waited = sim.getSimTickIndex() - task.boardingSince > BOARDING_PATIENCE_TICKS;
                    if (aboard || gone || waited) {
                        task.phase = aboard ? Phase.DRIVING : Phase.STATION;
                        drive(sim, hull, task);
                    }
                }
                case DRIVING -> {
                    if (within(convoy.body(hull), task.postX, task.postY, DROP_REACH) || stopped(sim, hull)) {
                        task.phase = Phase.STATION;
                        if (task.delivery != null && task.riders > 0) {
                            GroundBody body = convoy.body(hull);
                            task.delivery.delivered(sim, task.riders, (int) body.x, (int) body.y);
                        }
                        task.riders = 0;
                    }
                }
                case STATION -> {
                    if (!stopped(sim, hull)) break;
                    if (stalled(task, sim.getSimTickIndex())) {
                        task.pullbacks = 0;
                        task.vias = 0;
                        task.goalX = task.postX;
                        task.goalY = task.postY;
                        drive(sim, hull, task);
                    } else if (!within(convoy.body(hull), task.goalX, task.goalY, STATION_LEASH)) {
                        drive(sim, hull, task);
                    }
                }
            }
        }
    }

    /**
     * The squad boards when every member stands within reach of the hull and they fit: all of them
     * leave the field and ride as a count. A squad strung out on the road waits for its stragglers.
     */
    private static boolean board(BattleSimulation sim, long hull, Task task) {
        GroundBody body = sim.convoy().body(hull);
        VehicleType type = sim.convoy().vehicleType(hull);
        List<Long> near = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit)) continue;
            Squad squad = sim.squadOf(unit);
            if (squad == null || squad.id != task.squad) continue;
            if (body.distanceTo(sim.world().x(unit), sim.world().y(unit)) <= BOARDING_REACH) near.add(unit);
        }
        if (near.isEmpty() || near.size() < sim.squadMemberCount(task.squad)) return false;
        if (type == null || near.size() > type.capacity) return false;
        for (long unit : near) sim.takeOffTheField(unit);
        task.riders = near.size();
        return true;
    }

    private void drive(BattleSimulation sim, long hull, Task task) {
        task.sentAt = sim.getSimTickIndex();
        sim.getVehicleMoveOrderService().requestMove(hull, task.goalX, task.goalY);
    }

    /**
     * Whether a stopped, stationed hull has stalled short of its post: it stands at a goal pulled back from the
     * post, and has been sent nowhere for {@link #STALL_RETRY_TICKS}. A hull at its post itself is home.
     */
    private static boolean stalled(Task task, int tick) {
        return (task.goalX != task.postX || task.goalY != task.postY) && tick - task.sentAt >= STALL_RETRY_TICKS;
    }

    /**
     * A refused goal is pulled back toward the hull and asked again: to the corner halfway along the way
     * there, where one is known, so the hull goes a leg at a time; else a step back along the
     * straight line, never past the hull itself. After {@link #PULLBACK_LIMIT} refusals the hull
     * stays where it is, and is sent to its post afresh after {@link #STALL_RETRY_TICKS}.
     *
     * <p>A hull that steers wide (an APC turns on twice a car's circle) is refused a long route whose
     * one tight corner it cannot make in a single drive, though every leg of it is drivable alone:
     * standing at the corner, it turns there on its own and goes on.
     *
     * @return whether the hull was refused this tick
     */
    private boolean pullBack(BattleSimulation sim, long hull, Task task) {
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        VehicleMoveOrderService.RefusedOrder refused = orders.refusal(hull);
        if (refused == null || refused.requestedX() != task.goalX || refused.requestedY() != task.goalY
                || !stopped(sim, hull)) return false;
        // Out of steps: a station stays put until it is retried; a carrier sets its riders down where it stands.
        if (task.pullbacks >= PULLBACK_LIMIT) return task.phase == Phase.STATION && !stalled(task, sim.getSimTickIndex());
        GroundBody body = sim.convoy().body(hull);
        int[] corner = wayCorner(sim, hull, task.goalX, task.goalY);
        if (corner != null) {
            task.goalX = corner[0];
            task.goalY = corner[1];
        } else {
            float dx = body.x - (task.goalX + 0.5f), dy = body.y - (task.goalY + 0.5f);
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= PULLBACK_STEP) {
                task.pullbacks = PULLBACK_LIMIT;
                return task.phase == Phase.STATION;
            }
            task.goalX = Math.round(task.goalX + dx / length * PULLBACK_STEP);
            task.goalY = Math.round(task.goalY + dy / length * PULLBACK_STEP);
        }
        task.pullbacks++;
        drive(sim, hull, task);
        return true;
    }

    /**
     * The corner nearest halfway along the hull's way to the goal, footprint-clear but not yet proved
     * drivable, or {@code null} where the way is one straight leg or there is none. Halving the way at
     * each refusal reaches back to the drivable first leg of a long route within {@link #PULLBACK_LIMIT}
     * refusals; stepping back one corner at a time ran out of refusals deep in a town whose every corner
     * was refused, and left the hull standing at its start.
     */
    static int[] midCorner(float[] xs, float[] ys) {
        if (xs.length < 3) return null;
        float total = 0f;
        for (int i = 1; i < xs.length; i++) total += (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        float run = 0f;
        int best = 1;
        float bestGap = Float.MAX_VALUE;
        for (int i = 1; i < xs.length - 1; i++) {
            run += (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            float gap = Math.abs(run - total / 2f);
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return new int[]{(int) xs[best], (int) ys[best]};
    }

    /** The {@link #midCorner} of the hull's footprint-clear way to the goal, or {@code null} where there is none. */
    private static int[] wayCorner(BattleSimulation sim, long hull, int goalX, int goalY) {
        VehicleType type = sim.convoy().vehicleType(hull);
        if (type == null) return null;
        GroundBody body = sim.convoy().body(hull);
        VehicleClearance clearance = sim.convoy().clearances().of(sim.getGrid(), type);
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) body.x, (int) body.y, SNAP_REACH);
        int[] to = VehicleRoutePlanner.snapToMask(clearance, goalX, goalY, SNAP_REACH);
        if (from == null || to == null) return null;
        float[][] way = VehicleRoutePlanner.route(from[0], from[1], to[0], to[1], sim.getGrid(),
                TerrainCostField.from(sim.getTopology()), clearance);
        return way == null ? null : midCorner(way[0], way[1]);
    }

    /**
     * A hull standing at the waypoint it was pulled back to is sent on to its post. Each leg it
     * made is ground gained, so the refusals it took on the way are forgotten; the waypoints are not,
     * so a hull cannot be walked round a loop for ever.
     *
     * @return whether it was sent on this tick
     */
    private boolean reachedVia(BattleSimulation sim, long hull, Task task) {
        if (task.goalX == task.postX && task.goalY == task.postY) return false;
        if (task.vias >= VIA_LIMIT || !stopped(sim, hull)) return false;
        if (!within(sim.convoy().body(hull), task.goalX, task.goalY, VIA_REACH)) return false;
        task.vias++;
        task.pullbacks = 0;
        task.goalX = task.postX;
        task.goalY = task.postY;
        drive(sim, hull, task);
        return true;
    }

    /** No order in hand or in the queue: it arrived, or its route was refused or given up. */
    private static boolean stopped(BattleSimulation sim, long hull) {
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        return orders.activeOrder(hull) == null && !orders.pending(hull);
    }

    private static boolean within(GroundBody body, int x, int y, float reach) {
        return body.distanceTo(x + 0.5f, y + 0.5f) <= reach;
    }

    static boolean alive(ConvoyService convoy, long hull) {
        if (!convoy.isVehicle(hull) || convoy.structure(hull) <= 0f) return false;
        VehicleMission mission = convoy.mission(hull);
        return mission != null && mission.state == VehicleState.DEPLOYED;
    }
}
