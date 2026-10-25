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
import com.dillon.starsectormarines.battle.vehicle.ClearanceComponents;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.HashMap;
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
    /**
     * Ticks a hull's refused goals are remembered while it stands where it was refused. Within that, asking again
     * is asking the same proof the same question: the goal is pulled back past without an order. After it, the
     * ground may have changed (a fire through the undergrowth, a wreck towed off) and the goals are asked afresh.
     */
    public static final int REFUSAL_MEMORY_TICKS = Math.round(60f / BattleSimulation.TICK_DT);
    /** Cells a hull must move from where it was refused before what it was refused stops applying. */
    public static final float REFUSAL_MOVED_CELLS = 1.5f;
    /** Doublings of {@link #STALL_RETRY_TICKS} a hull that keeps stalling where it stands waits through, at most. */
    public static final int STALL_BACKOFF_LIMIT = 3;
    /** Stands tried for one post after refusals before the goal is pulled back toward the hull instead. */
    public static final int STAND_LIMIT = 4;
    /**
     * Whether the commander remembers its hulls' refused goals and backs off its stall retries ({@link RefusedGoals}).
     * {@code -Dbattle.allied.refusalMemory=false} is the control, every pull-back and retry asked again: remove it
     * with the losing path once The Burn's vehicle-order evidence has been read against it.
     */
    static final boolean REFUSAL_MEMORY =
            Boolean.parseBoolean(System.getProperty("battle.allied.refusalMemory", "true"));

    private enum Phase { STATION, BOARDING, DRIVING }

    private static final class Task {
        Phase phase;
        int postX, postY;
        /** The way the post faces the enemy, or zero for a post that faces no way ({@link PostStand}). */
        float aheadX, aheadY;
        /** Where it was last sent: a stand for the post, or the goal pulled back after a refusal. */
        int goalX, goalY;
        /** Whether the goal is a stand chosen for the post, not a waypoint pulled back toward the hull. */
        boolean onStand;
        /** Stands tried for the post since it was last placed afresh. */
        int stands;
        /** The post's stands and pull-backs are spent: allied command cannot serve it from here. */
        boolean unservable;
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
    private final Map<Long, RefusedGoals> refusals = new HashMap<>();

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
        station(sim, hull, x, y, 0f, 0f);
    }

    /**
     * {@link #station} at a post that faces the enemy along ({@code aheadX}, {@code aheadY}), a slot in a line: the
     * hull stands on the line, sliding along it or back from it to ground it can reach and leave, never forward
     * ({@link PostStand}).
     */
    public void station(BattleSimulation sim, long hull, int x, int y, float aheadX, float aheadY) {
        Task held = tasks.get(hull);
        if (held != null && held.phase == Phase.STATION
                && Math.abs(held.postX - x) < RESTATION && Math.abs(held.postY - y) < RESTATION) return;
        Task task = new Task();
        task.phase = Phase.STATION;
        task.postX = task.goalX = x;
        task.postY = task.goalY = y;
        task.aheadX = aheadX;
        task.aheadY = aheadY;
        tasks.put(hull, task);
        placeOnPost(sim, hull, task);
        drive(sim, hull, task);
    }

    /**
     * Whether the hull's post cannot be served from where it stands: every stand near it and every pull-back toward
     * the hull was refused. The mission that chose the post decides what the hull is for instead; until then it
     * holds, and its stall retry asks again.
     */
    public boolean postUnservable(long hull) {
        Task task = tasks.get(hull);
        return task != null && task.phase == Phase.STATION && task.unservable;
    }

    /**
     * Sets the goal to the best stand for the post ({@link PostStand}), passing over what the hull was already
     * refused; where none serves, the post itself, as before stands.
     */
    private void placeOnPost(BattleSimulation sim, long hull, Task task) {
        int[] stand = standFor(sim, hull, task);
        task.goalX = stand == null ? task.postX : stand[0];
        task.goalY = stand == null ? task.postY : stand[1];
        task.onStand = stand != null;
    }

    private int[] standFor(BattleSimulation sim, long hull, Task task) {
        VehicleType type = sim.convoy().vehicleType(hull);
        if (type == null) return null;
        GroundBody body = sim.convoy().body(hull);
        VehicleClearance clearance = sim.convoy().clearances().of(sim.getGrid(), type);
        ClearanceComponents components = sim.convoy().clearances().components(sim.getGrid(), type);
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) body.x, (int) body.y, SNAP_REACH);
        if (from == null) return null;
        RefusedGoals held = refusals(sim, hull);
        return PostStand.choose(sim.getGrid(), type, clearance, components, from[0], from[1],
                task.postX, task.postY, task.aheadX, task.aheadY,
                (x, y) -> held != null && held.refused(x, y));
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
                refusals.remove(hull);
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
                    if (stalled(task, sim.getSimTickIndex(), refusals(sim, hull))) {
                        RefusedGoals held = refusals(sim, hull);
                        if (held != null) held.stallRetried();
                        task.pullbacks = 0;
                        task.vias = 0;
                        task.stands = 0;
                        task.unservable = false;
                        placeOnPost(sim, hull, task);
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

    /**
     * Sends the hull to its goal, pulling the goal back first past anything it has already been refused where it
     * stands ({@link #REFUSAL_MEMORY_TICKS}). Out of steps, it is not sent at all and waits for its stall retry.
     */
    private void drive(BattleSimulation sim, long hull, Task task) {
        task.sentAt = sim.getSimTickIndex();
        RefusedGoals held = refusals(sim, hull);
        while (held != null && held.refused(task.goalX, task.goalY)) {
            if (!stepBack(sim, hull, task)) return;
        }
        sim.getVehicleMoveOrderService().requestMove(hull, task.goalX, task.goalY);
    }

    /** What the hull has been refused where it stands now, or {@code null}; a move or {@link #REFUSAL_MEMORY_TICKS} forgets it. */
    private RefusedGoals refusals(BattleSimulation sim, long hull) {
        RefusedGoals held = refusals.get(hull);
        if (held == null) return null;
        GroundBody body = sim.convoy().body(hull);
        if (held.movedAway(body.x, body.y)) {
            refusals.remove(hull);
            return null;
        }
        held.age(sim.getSimTickIndex());
        return held;
    }

    private void rememberRefused(BattleSimulation sim, long hull, int x, int y) {
        if (!REFUSAL_MEMORY) return;
        RefusedGoals held = refusals(sim, hull);
        if (held == null) {
            GroundBody body = sim.convoy().body(hull);
            held = new RefusedGoals(body.x, body.y, sim.getSimTickIndex(), REFUSAL_MOVED_CELLS, REFUSAL_MEMORY_TICKS);
            refusals.put(hull, held);
        }
        held.remember(x, y);
    }

    /**
     * Whether a stopped, stationed hull has stalled short of its post: it stands at a goal pulled back from the
     * post, and has been sent nowhere for {@link #STALL_RETRY_TICKS}, doubled for each retry in a row that found it
     * where it was refused, up to {@link #STALL_BACKOFF_LIMIT} doublings. A hull at its post itself is home.
     */
    private static boolean stalled(Task task, int tick, RefusedGoals held) {
        int wait = held == null ? STALL_RETRY_TICKS : held.stallWait(STALL_RETRY_TICKS, STALL_BACKOFF_LIMIT);
        return !task.onStand && (task.goalX != task.postX || task.goalY != task.postY) && tick - task.sentAt >= wait;
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
        rememberRefused(sim, hull, task.goalX, task.goalY);
        // A refused stand is first answered by another stand on the same post: the post is what the hull is for,
        // and ground toward the hull is only where it already was.
        if (task.phase == Phase.STATION && task.onStand && task.stands < STAND_LIMIT) {
            task.stands++;
            int[] stand = standFor(sim, hull, task);
            if (stand != null && (stand[0] != task.goalX || stand[1] != task.goalY)) {
                task.goalX = stand[0];
                task.goalY = stand[1];
                drive(sim, hull, task);
                return true;
            }
        }
        task.onStand = false;
        // Out of steps: a station stays put until it is retried; a carrier sets its riders down where it stands.
        if (task.pullbacks >= PULLBACK_LIMIT) {
            task.unservable = true;
            return task.phase == Phase.STATION && !stalled(task, sim.getSimTickIndex(), refusals(sim, hull));
        }
        if (!stepBack(sim, hull, task)) {
            task.unservable = true;
            return task.phase == Phase.STATION;
        }
        drive(sim, hull, task);
        return true;
    }

    /**
     * Moves the goal one pull-back nearer the hull, as {@link #pullBack} describes.
     *
     * @return {@code false} when the pull-backs are spent or the goal is already beside the hull
     */
    private boolean stepBack(BattleSimulation sim, long hull, Task task) {
        if (task.pullbacks >= PULLBACK_LIMIT) return false;
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
                return false;
            }
            task.goalX = Math.round(task.goalX + dx / length * PULLBACK_STEP);
            task.goalY = Math.round(task.goalY + dy / length * PULLBACK_STEP);
        }
        task.pullbacks++;
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
        if (task.onStand || task.goalX == task.postX && task.goalY == task.postY) return false;
        if (task.vias >= VIA_LIMIT || !stopped(sim, hull)) return false;
        if (!within(sim.convoy().body(hull), task.goalX, task.goalY, VIA_REACH)) return false;
        task.vias++;
        task.pullbacks = 0;
        task.stands = 0;
        placeOnPost(sim, hull, task);
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
