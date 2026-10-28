package com.dillon.starsectormarines.battle.assault;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The machine assault on a garrisonable (`garrisonables.md`, Decided direction): a mech or an
 * armed vehicle ordered onto a trench or a held building the side believes is enemy-held stands
 * off and fires on it rather than walking in. This class owns the questions the order systems,
 * the cursor and the scene all ask, so the verb the cursor promises is the one the click gives:
 * whether a click is an assault, where the machine fights from, what it fires at, and when the
 * order is over.
 *
 * <p><b>Belief only.</b> Occupants are the side's own reports: contacts some squad of the side
 * holds, last placed in the garrisonable. A machine reads the side's picture rather than its own
 * squad's because it is supporting fire for whoever found the garrison, and a vehicle has no squad
 * of its own. Every shot still needs the machine's own line of fire; nothing here reads where a
 * hostile is that the side has not reported, beyond the live position every direct shot is aimed at.
 */
public final class MachineAssaults {

    /** Quiet ticks (five seconds) with nobody believed inside before the order is over. */
    public static final int RELEASE_TICKS = 150;
    /** Ticks a mech stands at its stand-off with nothing to fire at before it chooses again. */
    public static final int RECHOOSE_TICKS = 90;
    /** How far outside the footprint, at the nearest, a stand-off may be. UNTUNED. */
    public static final int STANDOFF_MARGIN_CELLS = 3;
    /** The nearest a stand-off ring is drawn, in cells from the footprint. UNTUNED. */
    static final int MIN_STANDOFF_CELLS = 6;
    /** The share of reach a stand-off ring is drawn at first. UNTUNED. */
    static final float PREFERRED_REACH_SHARE = 0.7f;
    /** Members of one lance keep their stand-offs this far apart. */
    static final int STANDOFF_SPACING_CELLS = 3;
    /** No wall is fired on while a friendly stands this close to it. UNTUNED. */
    public static final float DANGER_CLOSE_CELLS = 4f;
    /** Occupants a stand-off is judged against, nearest the footprint's centre first. */
    private static final int STANDOFF_AIMS = 4;

    private MachineAssaults() { }

    // ----- what the click means -------------------------------------------------------------

    /**
     * The garrisonable a click by a machine of {@code side} assaults, or null when the click is an
     * ordinary move: plain ground, or a garrisonable nobody on the side believes holds an enemy.
     */
    public static AssaultTarget targetUnderClick(BattleView sim, Faction side, int cellX, int cellY) {
        AssaultTarget target = AssaultTarget.underClick(sim.fightingPositions(), DigInSystem.isEnabled(),
                sim.getTacticalMap(), cellX, cellY);
        return target != null && believedHeld(sim, side, target) ? target : null;
    }

    /** Whether vehicle {@code id} has a working gun to assault with; an unarmed hull only moves. */
    public static boolean vehicleArmed(BattleSimulation sim, long id) {
        if (!sim.convoy().isVehicle(id)) return false;
        VehicleType type = sim.convoy().vehicleType(id);
        if (type == null || !type.hasTurretWeapon()) return false;
        GroundTurret turret = sim.convoy().turret(id);
        return turret != null && !turret.destroyed();
    }

    // ----- belief ---------------------------------------------------------------------------

    /** Whether some squad of {@code side} believes a hostile is in {@code target}. */
    public static boolean believedHeld(BattleView sim, Faction side, AssaultTarget target) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != side) continue;
            for (BelievedContact contact : squad.believedContacts()) {
                if (target.contains(contact.lastSeenCellX(), contact.lastSeenCellY())) return true;
            }
        }
        return false;
    }

    /**
     * The side's believed occupants of {@code target}, one per unit (the freshest report wins),
     * in unit-id order so every reader sees them in the same order.
     */
    public static List<BelievedContact> occupants(BattleView sim, Faction side, AssaultTarget target) {
        Map<Long, BelievedContact> byUnit = new LinkedHashMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != side) continue;
            for (BelievedContact contact : squad.believedContacts()) {
                if (!target.contains(contact.lastSeenCellX(), contact.lastSeenCellY())) continue;
                BelievedContact held = byUnit.get(contact.unitId());
                if (held == null || contact.lastSeenTick() > held.lastSeenTick()) {
                    byUnit.put(contact.unitId(), contact);
                }
            }
        }
        List<BelievedContact> out = new ArrayList<>(byUnit.values());
        out.sort(Comparator.comparingLong(BelievedContact::unitId));
        return out;
    }

    /**
     * Whether the order still stands this tick: refreshes when the side last believed somebody
     * inside, and answers false once it has believed nobody for {@link #RELEASE_TICKS}.
     */
    public static boolean stillStanding(MachineAssault assault, BattleView sim, Faction side) {
        int tick = sim.getSimTickIndex();
        if (believedHeld(sim, side, assault.target())) {
            assault.lastBelievedTick = tick;
            return true;
        }
        return tick - assault.lastBelievedTick < RELEASE_TICKS;
    }

    /**
     * Whether a machine that is {@code idle} this tick — at its stand-off with nothing to fire at —
     * has been so for {@link #RECHOOSE_TICKS} and should choose its stand-off again. Restarts the
     * clock whenever it answers yes or the machine has work.
     */
    public static boolean dueToChooseAgain(MachineAssault assault, boolean idle, int tick) {
        if (!idle) {
            assault.idleSinceTick = -1;
            return false;
        }
        if (assault.idleSinceTick < 0) {
            assault.idleSinceTick = tick;
            return false;
        }
        if (tick - assault.idleSinceTick < RECHOOSE_TICKS) return false;
        assault.idleSinceTick = -1;
        return true;
    }

    // ----- what to fire at ------------------------------------------------------------------

    /**
     * Sets this tick's fire directive for a machine at ({@code x}, {@code y}): the nearest believed
     * occupant it has a clear line of fire to inside its band, else — when it carries wall damage —
     * the first wall on its line to the nearest believed occupant whose line stops on a wall of the
     * target, unless a friendly stands within {@link #DANGER_CLOSE_CELLS} of that wall. Clears both
     * when neither is there.
     *
     * @param shooter the machine's entity id, asked {@link BattleView#canEngage} of each occupant
     */
    public static void aim(MachineAssault assault, BattleView sim, long shooter, Faction side,
                           float x, float y, float range, float minRange, boolean wallDamage) {
        assault.occupantId = 0L;
        assault.wallX = MachineAssault.NO_WALL;
        assault.wallY = MachineAssault.NO_WALL;
        List<BelievedContact> occupants = occupants(sim, side, assault.target());
        if (occupants.isEmpty()) return;
        occupants.sort(Comparator.comparingDouble(
                (BelievedContact c) -> squared(c.lastSeenCellX() + .5f - x, c.lastSeenCellY() + .5f - y))
                .thenComparingLong(BelievedContact::unitId));
        NavigationGrid grid = sim.getGrid();
        for (BelievedContact occupant : occupants) {
            long id = occupant.unitId();
            if (!sim.world().isAlive(id) || !sim.canEngage(shooter, id)
                    || !side.hostileTo(sim.identity().faction(id))) continue;
            float tx = sim.world().x(id), ty = sim.world().y(id);
            float distance = (float) Math.hypot(tx - x, ty - y);
            if (distance > range || distance < minRange || !grid.hasLineOfFire(x, y, tx, ty)) continue;
            assault.occupantId = id;
            return;
        }
        if (!wallDamage) return;
        for (BelievedContact occupant : occupants) {
            int[] wall = shelteringWall(grid, assault.target(), x, y,
                    occupant.lastSeenCellX(), occupant.lastSeenCellY());
            if (wall == null) continue;
            float distance = (float) Math.hypot(wall[0] + .5f - x, wall[1] + .5f - y);
            if (distance > range || distance < minRange || dangerClose(sim, side, wall[0], wall[1])) continue;
            assault.wallX = wall[0];
            assault.wallY = wall[1];
            return;
        }
    }

    /**
     * The wall sheltering a hostile at cell ({@code toX}, {@code toY}) from a shooter at
     * ({@code fromX}, {@code fromY}): the first wall on the line between them, when it is a wall
     * of {@code target} that can be knocked down. Null when the line is open or stops elsewhere.
     */
    public static int[] shelteringWall(NavigationGrid grid, AssaultTarget target,
                                       float fromX, float fromY, int toX, int toY) {
        long packed = grid.firstWallOnLine(fromX, fromY, toX + .5f, toY + .5f);
        int hitX = (int) packed;
        int hitY = (int) (packed >>> 32);
        if (hitX == -1 && hitY == -1) return null;
        if (!grid.inBounds(hitX, hitY) || grid.isWalkable(hitX, hitY)
                || grid.hasTag(hitX, hitY, NavigationGrid.CellTag.NATURAL_SOLID)
                || !target.ownsWall(hitX, hitY)) return null;
        return new int[] {hitX, hitY};
    }

    /** Whether a living unit not hostile to {@code side} stands within {@link #DANGER_CLOSE_CELLS} of the wall. */
    static boolean dangerClose(BattleView sim, Faction side, int wallX, int wallY) {
        float cx = wallX + .5f, cy = wallY + .5f;
        float limit = DANGER_CLOSE_CELLS * DANGER_CLOSE_CELLS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (side.hostileTo(sim.identity().faction(unit))) continue;
            if (squared(sim.world().x(unit) - cx, sim.world().y(unit) - cy) <= limit) return true;
        }
        return false;
    }

    // ----- where to fight from --------------------------------------------------------------

    /**
     * A stand-off cell for a machine at ({@code fromX}, {@code fromY}) with reach {@code range}:
     * a walkable cell connected to it, on a ring at least {@link #STANDOFF_MARGIN_CELLS} outside
     * the footprint, drawn first at {@link #PREFERRED_REACH_SHARE} of reach and then nearer, then
     * further out short of full reach. Cells with a clear line of fire to a believed occupant rank
     * first, then cells whose line to one stops on a wall of the target, then any; nearer the
     * machine breaks ties, then cell order. Keeps {@link #STANDOFF_SPACING_CELLS} from {@code taken}.
     *
     * @return the cell, or null when no cell of the machine's ground lies on any ring
     */
    public static int[] chooseStandoff(BattleView sim, AssaultTarget target, Faction side,
                                       int fromX, int fromY, float range, List<int[]> taken) {
        NavigationGrid grid = sim.getGrid();
        if (!grid.inBounds(fromX, fromY)) return null;
        int[] components = GridPathfinder.labelConnectedComponents(grid);
        int component = components[grid.index(fromX, fromY)];
        if (component < 0) return null;
        List<int[]> aims = new ArrayList<>();
        List<BelievedContact> occupants = occupants(sim, side, target);
        occupants.sort(Comparator.comparingInt((BelievedContact c) ->
                target.gap(c.lastSeenCellX(), c.lastSeenCellY()) == 0
                        ? Math.abs(c.lastSeenCellX() - target.centreX()) + Math.abs(c.lastSeenCellY() - target.centreY())
                        : 0).thenComparingLong(BelievedContact::unitId));
        for (int i = 0; i < Math.min(STANDOFF_AIMS, occupants.size()); i++) {
            aims.add(new int[] {occupants.get(i).lastSeenCellX(), occupants.get(i).lastSeenCellY()});
        }
        if (aims.isEmpty()) aims.add(new int[] {target.centreX(), target.centreY()});

        int furthest = Math.max(STANDOFF_MARGIN_CELLS, (int) Math.floor(range) - 2);
        int nearest = Math.min(furthest, Math.max(STANDOFF_MARGIN_CELLS, MIN_STANDOFF_CELLS));
        int preferred = Math.max(nearest, Math.min(furthest, Math.round(range * PREFERRED_REACH_SHARE)));
        List<Integer> rings = new ArrayList<>();
        for (int ring = preferred; ring >= nearest; ring--) rings.add(ring);
        for (int ring = preferred + 1; ring <= furthest; ring++) rings.add(ring);

        int[] best = null;
        int bestScore = -1;
        long bestGap = Long.MAX_VALUE;
        for (int ring : rings) {
            int[] ringBest = null;
            int ringScore = -1;
            long ringGap = Long.MAX_VALUE;
            for (int y = target.top - ring; y <= target.bottom + ring; y++) {
                for (int x = target.left - ring; x <= target.right + ring; x++) {
                    if (target.gap(x, y) != ring || !grid.inBounds(x, y) || !grid.isWalkable(x, y)
                            || components[grid.index(x, y)] != component || crowded(x, y, taken)) continue;
                    long dx = x - fromX, dy = y - fromY;
                    long gap = dx * dx + dy * dy;
                    int score = score(grid, target, x, y, aims);
                    if (score > ringScore || (score == ringScore && gap < ringGap)) {
                        ringBest = new int[] {x, y};
                        ringScore = score;
                        ringGap = gap;
                    }
                }
            }
            if (ringBest == null) continue;
            if (ringScore == 2) return ringBest;
            if (ringScore > bestScore || (ringScore == bestScore && ringGap < bestGap)) {
                best = ringBest;
                bestScore = ringScore;
                bestGap = ringGap;
            }
        }
        return best;
    }

    /** 2 for a clear line to an aim, 1 for a line stopping on a wall of the target, 0 otherwise. */
    private static int score(NavigationGrid grid, AssaultTarget target, int x, int y, List<int[]> aims) {
        float fx = x + .5f, fy = y + .5f;
        int score = 0;
        for (int[] aim : aims) {
            if (grid.hasLineOfFire(fx, fy, aim[0] + .5f, aim[1] + .5f)) return 2;
            if (shelteringWall(grid, target, fx, fy, aim[0], aim[1]) != null) score = 1;
        }
        return score;
    }

    private static boolean crowded(int x, int y, List<int[]> taken) {
        if (taken == null) return false;
        for (int[] cell : taken) {
            if (Math.max(Math.abs(cell[0] - x), Math.abs(cell[1] - y)) < STANDOFF_SPACING_CELLS) return true;
        }
        return false;
    }

    // ----- vehicles -------------------------------------------------------------------------

    /**
     * Plans an armed vehicle's assault on the garrisonable under a click, or null when the click is
     * an ordinary move. The vehicle's own route proof then decides whether it can drive there.
     */
    public static MachineAssault planVehicle(BattleSimulation sim, long id, int cellX, int cellY) {
        if (!vehicleArmed(sim, id)) return null;
        Faction side = sim.convoy().faction(id);
        AssaultTarget target = targetUnderClick(sim, side, cellX, cellY);
        if (target == null) return null;
        GroundBody body = sim.convoy().body(id);
        WeaponDef weapon = sim.convoy().turretStructure(id).mount.weapon;
        int[] cell = chooseStandoff(sim, target, side, (int) Math.floor(body.x), (int) Math.floor(body.y),
                weapon.range, null);
        return cell == null ? null : new MachineAssault(target, cell[0], cell[1], sim.getSimTickIndex());
    }

    /**
     * The serial pass for vehicles: ends an assault the side no longer believes in and sets each
     * standing one's fire directive for the turret pass that follows. Runs before the ground tick.
     */
    public static void tickVehicles(BattleSimulation sim) {
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        if (!orders.hasAssaults()) return;
        for (Map.Entry<Long, MachineAssault> entry : orders.assaultEntries()) {
            long id = entry.getKey();
            MachineAssault assault = entry.getValue();
            if (!vehicleArmed(sim, id)) {
                orders.endAssault(id);
                continue;
            }
            Faction side = sim.convoy().faction(id);
            if (!stillStanding(assault, sim, side)) {
                orders.endAssault(id);
                continue;
            }
            GroundBody body = sim.convoy().body(id);
            WeaponDef weapon = sim.convoy().turretStructure(id).mount.weapon;
            aim(assault, sim, id, side, body.x, body.y, weapon.range, weapon.minRange,
                    weapon.wallDamage > 0 && !weapon.indirectFire);
        }
    }

    private static float squared(float dx, float dy) {
        return dx * dx + dy * dy;
    }
}
