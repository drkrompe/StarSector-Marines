package com.dillon.starsectormarines.battle.assault;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveHoldPlaceOrder;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The machine guard (`garrisonables.md`, Decided direction): a mech or an armed vehicle, which takes no posts, stands
 * beside a trench, a held place or a compound building its own side mans as the garrison's heavy support. It stands
 * on a cell outside the place, off the garrison's lines of fire, with a view along the believed threat axis; it faces
 * that axis while nothing is in its sights, engages what comes with its own doctrine or turret, never enters, and
 * stands down once the garrison has gone. This class owns the questions the order systems, the cursor, the
 * commanders and the scene all ask, so the verb the cursor promises is the one the click gives.
 *
 * <p><b>Own-force facts and belief.</b> Whether the side mans a place is its own knowledge of its own people: somebody
 * of the side standing in it, or a squad of the side ordered to it. The threat axis is the side's belief: contacts its
 * squads hold near the place. Nothing here reads a hostile the side has not reported.
 */
public final class MachineGuards {

    /** Ticks (five seconds) with nobody of the side manning the place before the guard stands down. */
    public static final int RELEASE_TICKS = 150;
    /** Ticks between a guard's looks at the threat axis. */
    public static final int REVIEW_TICKS = 90;
    /** A threat axis that has turned further than this (cosine of 45 degrees) moves the guard. */
    static final float REAXIS_COS = 0.7071f;
    /** Nearest a guard cell is drawn, in cells outside the footprint; the assault's own margin. */
    public static final int GUARD_NEAR_CELLS = MachineAssaults.STANDOFF_MARGIN_CELLS;
    /** Furthest a guard cell is drawn, in cells outside the footprint: beside the place, not off on its own. UNTUNED. */
    public static final int GUARD_FAR_CELLS = 7;
    /** Cells either side of the garrison's own frontage kept clear as its lanes of fire. UNTUNED. */
    public static final int LANE_MARGIN_CELLS = 2;
    /** How far past the place's front the guard wants to see along the axis. UNTUNED. */
    static final int WATCH_CELLS = 12;
    /** Believed contacts this far from the footprint (Chebyshev cells) set the threat axis. UNTUNED. */
    static final int THREAT_RADIUS_CELLS = 48;
    /** How far along a run, either way from the click, the stretch a guard screens reaches. UNTUNED. */
    public static final int STRETCH_REACH_CELLS = 12;
    /** Members of one lance keep their guard cells this far apart. */
    static final int GUARD_SPACING_CELLS = MachineAssaults.STANDOFF_SPACING_CELLS;
    /** How far from a chassis's own cell its ground is looked for, when that cell is not walkable ground itself. */
    static final int STANDING_SEARCH_CELLS = 3;

    private MachineGuards() { }

    // ----- what the click means -------------------------------------------------------------

    /**
     * The garrisonable a click by a machine of {@code side} guards, or null: plain ground, a garrisonable the side
     * believes holds an enemy (that is an assault), or one the side does not man.
     */
    public static AssaultTarget targetUnderClick(BattleSimulation sim, Faction side, int cellX, int cellY) {
        AssaultTarget target = AssaultTarget.underClick(sim.fightingPositions(), DigInSystem.isEnabled(),
                sim.getTacticalMap(), cellX, cellY);
        if (target == null) target = envelopeUnderClick(sim.garrisonables(), cellX, cellY);
        if (target == null || seenHeld(sim, side, target)) return null;
        // A run is guarded by the stretch under the click: the garrison there, not the whole belt.
        if (target.kind() == AssaultTarget.Kind.FIGHTING_POSITION) {
            target = AssaultTarget.stretch(sim.fightingPositions(), target.runId(), cellX, cellY, STRETCH_REACH_CELLS);
        }
        return manned(sim, side, target) ? target : null;
    }

    /**
     * Whether some squad of {@code side} has seen a hostile in {@code target}, the enemy holding a place its own side
     * mans. A report localised only by sound does not count: its cell is scattered round the shooter by design, so the
     * shots of an attack standing off a belt land reports on the belt, and a commander's guard for the garrison there
     * was refused every pulse for as long as the attack kept firing. A player's click on such a place is still an
     * assault, which is asked first ({@link MachineAssaults#targetUnderClick}).
     */
    static boolean seenHeld(BattleView sim, Faction side, AssaultTarget target) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != side) continue;
            for (BelievedContact contact : squad.believedContacts()) {
                if (contact.source() == BeliefSource.DIRECT
                        && target.contains(contact.lastSeenCellX(), contact.lastSeenCellY())) return true;
            }
        }
        return false;
    }

    /**
     * The building whose envelope the cell lies in, as a place to guard, or null: a compound's command post, barracks
     * or armory is a garrisonable its side mans as much as a held place is (`garrisonables.md`, building envelopes),
     * though no infantry click names it as one. Asked only after the run and held-place lookups have found nothing.
     */
    static AssaultTarget envelopeUnderClick(GarrisonableRegistry places, int cellX, int cellY) {
        if (places == null) return null;
        int place = places.idAt(cellX, cellY);
        return place >= 0 && places.isEnvelope(place) ? AssaultTarget.place(places.building(place)) : null;
    }

    // ----- own-force facts ------------------------------------------------------------------

    /** Whether {@code side} mans {@code target}: somebody of the side in it, or a squad of the side sent to it. */
    public static boolean manned(BattleSimulation sim, Faction side, AssaultTarget target) {
        return friendlyInside(sim, side, target) || sentTo(sim, side, target);
    }

    /** Whether a living unit of {@code side} that is not a machine stands in {@code target}. */
    public static boolean friendlyInside(BattleView sim, Faction side, AssaultTarget target) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != side || sim.world().hasMechLoadout(unit)) continue;
            int x = (int) Math.floor(sim.world().x(unit));
            int y = (int) Math.floor(sim.world().y(unit));
            if (target.gap(x, y) > 1) continue;
            if (target.kind() == AssaultTarget.Kind.HELD_PLACE ? target.gap(x, y) == 0 : target.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an infantry squad of {@code side} has been sent to {@code target}: a player's occupy or hold order on it,
     * or a commander's order to man it (a run-scoped area defence whose cell is on the run, or a hold of the building).
     */
    public static boolean sentTo(BattleSimulation sim, Faction side, AssaultTarget target) {
        SquadMoveOrderService orders = sim.getSquadMoveOrderService();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != side || squad.isMechSquad() || squad.isDroneSquad() || squad.aliveMembers <= 0) continue;
            SquadMoveOrderService.ActiveOrder order = orders.activeOrder(squad.id);
            if (target.kind() == AssaultTarget.Kind.HELD_PLACE) {
                if (order instanceof ActiveHoldPlaceOrder hold
                        && GarrisonableRegistry.sameBuilding(hold.targetNode(), target.heldPlace())) return true;
            } else if (order instanceof ActiveDefendAreaOrder defend && defend.isOccupy()
                    && defend.occupyRunId() == target.runId()
                    && target.gap(defend.destinationX(), defend.destinationY()) <= STRETCH_REACH_CELLS) {
                return true;
            }
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (assignment == null) continue;
            if (target.kind() == AssaultTarget.Kind.HELD_PLACE) {
                if (assignment.kind() == AssignmentKind.HOLD_NODE
                        && GarrisonableRegistry.sameBuilding(assignment.targetNode(), target.heldPlace())) {
                    return true;
                }
            } else if (assignment.isGarrison() && target.contains(assignment.targetCellX(), assignment.targetCellY())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the guard still stands this tick: refreshes when the side was last believed to man the place, and
     * answers false once nobody has manned it for {@link #RELEASE_TICKS}.
     */
    public static boolean stillStanding(MachineGuard guard, BattleSimulation sim, Faction side) {
        int tick = sim.getSimTickIndex();
        if (manned(sim, side, guard.target())) {
            guard.lastMannedTick = tick;
            return true;
        }
        return tick - guard.lastMannedTick < RELEASE_TICKS;
    }

    /** Whether {@link #REVIEW_TICKS} have passed since the guard last looked at its axis; restarts the clock when so. */
    public static boolean dueForReview(MachineGuard guard, int tick) {
        if (tick - guard.lastReviewTick < REVIEW_TICKS) return false;
        guard.lastReviewTick = tick;
        return true;
    }

    /** Whether {@code (ax, ay)} has turned far enough from the guard's own axis for its cell to be chosen again. */
    public static boolean axisTurned(MachineGuard guard, float ax, float ay) {
        return guard.axisX() * ax + guard.axisY() * ay < REAXIS_COS;
    }

    // ----- the threat axis ------------------------------------------------------------------

    /**
     * The unit threat axis of {@code target} for {@code side}: from the footprint's centre toward the side's believed
     * contacts within {@link #THREAT_RADIUS_CELLS} of it (one per unit, the freshest report, nearer ones weighing
     * more), not counting any believed inside it. With none, the way the machine at ({@code fromX}, {@code fromY})
     * was going: it came up from its own side's ground, so the threat is believed beyond the place.
     */
    public static float[] threatAxis(BattleView sim, Faction side, AssaultTarget target, float fromX, float fromY) {
        float[] believed = believedAxis(sim, side, target);
        if (believed != null) return believed;
        float cx = centreX(target), cy = centreY(target);
        double fx = cx - fromX, fy = cy - fromY;
        double from = Math.hypot(fx, fy);
        if (from > 1e-3) return new float[] {(float) (fx / from), (float) (fy / from)};
        return new float[] {1f, 0f};
    }

    /**
     * The threat axis read from the side's believed contacts alone (see {@link #threatAxis}), or null when it believes
     * nobody near the place: a guard already standing keeps the axis it had rather than one read off its own cell.
     */
    public static float[] believedAxis(BattleView sim, Faction side, AssaultTarget target) {
        float cx = centreX(target), cy = centreY(target);
        Map<Long, BelievedContact> byUnit = new HashMap<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != side) continue;
            for (BelievedContact contact : squad.believedContacts()) {
                int x = contact.lastSeenCellX(), y = contact.lastSeenCellY();
                int gap = target.gap(x, y);
                if (gap == 0 || gap > THREAT_RADIUS_CELLS || target.contains(x, y)) continue;
                BelievedContact held = byUnit.get(contact.unitId());
                if (held == null || contact.lastSeenTick() > held.lastSeenTick()) byUnit.put(contact.unitId(), contact);
            }
        }
        double sx = 0, sy = 0;
        for (BelievedContact contact : byUnit.values()) {
            float dx = contact.lastSeenCellX() + .5f - cx, dy = contact.lastSeenCellY() + .5f - cy;
            double length = Math.hypot(dx, dy);
            if (length < 1e-3) continue;
            double weight = 1.0 / (1 + target.gap(contact.lastSeenCellX(), contact.lastSeenCellY()));
            sx += dx / length * weight;
            sy += dy / length * weight;
        }
        double length = Math.hypot(sx, sy);
        return length > 1e-6 ? new float[] {(float) (sx / length), (float) (sy / length)} : null;
    }

    // ----- where to stand -------------------------------------------------------------------

    /**
     * A guard cell for a machine at ({@code fromX}, {@code fromY}) with reach {@code range} over {@code target}, for
     * the threat axis ({@code ax}, {@code ay}): a walkable cell connected to it, between {@link #GUARD_NEAR_CELLS} and
     * {@link #GUARD_FAR_CELLS} outside the footprint (and inside its reach), never in the garrison's lanes of fire —
     * the band in front of the place along the axis, as wide as the place plus {@link #LANE_MARGIN_CELLS} — and
     * {@link #GUARD_SPACING_CELLS} from {@code taken}, and never on prepared ground, whose posts are infantry's. A cell with a clear line of fire to the watch point (the axis
     * {@link #WATCH_CELLS} past the place's front) ranks first; then one abreast of the place's front edge, so it sees
     * past the place rather than over it; then a nearer ring; then nearer the machine; then cell order.
     *
     * @return the cell, or null when no cell of the machine's ground qualifies
     */
    public static int[] chooseGuardCell(BattleView sim, AssaultTarget target, int fromX, int fromY, float range,
                                        float ax, float ay, List<int[]> taken) {
        NavigationGrid grid = sim.getGrid();
        int[] components = GridPathfinder.labelConnectedComponents(grid);
        int component = componentNear(grid, components, fromX, fromY);
        if (component < 0) return null;
        float cx = centreX(target), cy = centreY(target);
        float halfW = (target.right - target.left + 1) / 2f, halfH = (target.bottom - target.top + 1) / 2f;
        float halfDepth = Math.abs(ax) * halfW + Math.abs(ay) * halfH;
        float halfWidth = Math.abs(ay) * halfW + Math.abs(ax) * halfH;
        float watchX = cx + ax * (halfDepth + WATCH_CELLS), watchY = cy + ay * (halfDepth + WATCH_CELLS);
        int far = Math.max(GUARD_NEAR_CELLS, Math.min(GUARD_FAR_CELLS, (int) Math.floor(range) - 2));

        int[] best = null;
        long bestScore = Long.MIN_VALUE;
        long bestGap = Long.MAX_VALUE;
        for (int ring = GUARD_NEAR_CELLS; ring <= far; ring++) {
            for (int y = target.top - ring; y <= target.bottom + ring; y++) {
                for (int x = target.left - ring; x <= target.right + ring; x++) {
                    if (target.gap(x, y) != ring || !grid.inBounds(x, y) || !grid.isWalkable(x, y)
                            || components[grid.index(x, y)] != component || crowded(x, y, taken)
                            || prepared(sim, x, y)) continue;
                    float rx = x + .5f - cx, ry = y + .5f - cy;
                    float along = rx * ax + ry * ay;
                    float lateral = Math.abs(rx * ay - ry * ax);
                    if (inLane(along, lateral, halfWidth)) continue;
                    boolean sees = grid.hasLineOfFire(x + .5f, y + .5f, watchX, watchY);
                    long score = (sees ? 1_000_000L : 0L)
                            - Math.round(Math.abs(along - halfDepth) * 100f)
                            - ring * 10L;
                    long dx = x - fromX, dy = y - fromY;
                    long gap = dx * dx + dy * dy;
                    if (score > bestScore || (score == bestScore && gap < bestGap)) {
                        best = new int[] {x, y};
                        bestScore = score;
                        bestGap = gap;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Whether a cell at {@code along} cells forward of the place's centre on the threat axis and {@code lateral} cells
     * off it stands in the garrison's lanes of fire: in front of the place and no further aside than its own frontage
     * plus {@link #LANE_MARGIN_CELLS}.
     */
    public static boolean inLane(float along, float lateral, float halfWidth) {
        return along > 0f && lateral < halfWidth + LANE_MARGIN_CELLS;
    }

    /**
     * The ground a machine at the cell stands on: the cell's own component, or, for a chassis whose centre stands on a
     * cell an infantry grid does not call walkable (a mech beside a parapet), the nearest walkable cell's within
     * {@link #STANDING_SEARCH_CELLS}. Negative when there is none.
     */
    private static int componentNear(NavigationGrid grid, int[] components, int x, int y) {
        for (int r = 0; r <= STANDING_SEARCH_CELLS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (!grid.inBounds(cx, cy)) continue;
                    int component = components[grid.index(cx, cy)];
                    if (component >= 0) return component;
                }
            }
        }
        return -1;
    }

    /** Whether the cell is prepared ground: a post is infantry's, so a guard never stands on one. */
    private static boolean prepared(BattleView sim, int x, int y) {
        return sim.fightingPositions() != null && sim.fightingPositions().isFightingPositionCell(x, y);
    }

        private static boolean crowded(int x, int y, List<int[]> taken) {
        if (taken == null) return false;
        for (int[] cell : taken) {
            if (Math.max(Math.abs(cell[0] - x), Math.abs(cell[1] - y)) < GUARD_SPACING_CELLS) return true;
        }
        return false;
    }

    /** The footprint's continuous centre. */
    public static float centreX(AssaultTarget target) { return (target.left + target.right + 1) / 2f; }

    public static float centreY(AssaultTarget target) { return (target.top + target.bottom + 1) / 2f; }

    // ----- vehicles -------------------------------------------------------------------------

    /**
     * Plans an armed vehicle's guard over the garrisonable under a click, or null when the click is not a guard. The
     * vehicle's own route proof then decides whether it can drive there.
     */
    public static MachineGuard planVehicle(BattleSimulation sim, long id, int cellX, int cellY) {
        if (!MachineAssaults.vehicleArmed(sim, id)) return null;
        Faction side = sim.convoy().faction(id);
        AssaultTarget target = targetUnderClick(sim, side, cellX, cellY);
        if (target == null) return null;
        GroundBody body = sim.convoy().body(id);
        WeaponDef weapon = sim.convoy().turretStructure(id).mount.weapon;
        float[] axis = threatAxis(sim, side, target, body.x, body.y);
        int[] cell = chooseGuardCell(sim, target, (int) Math.floor(body.x), (int) Math.floor(body.y),
                weapon.range, axis[0], axis[1], null);
        return cell == null ? null
                : new MachineGuard(target, cell[0], cell[1], axis[0], axis[1], sim.getSimTickIndex());
    }

    /**
     * The serial pass for vehicles: ends a guard whose hull lost its gun or whose garrison has gone, and keeps each
     * standing guard's axis current for the turret's idle facing. A hull keeps the cell it drove to: a wheeled or
     * tracked body cannot shuffle the way a mech steps, so it turns its gun rather than its ground. Runs before the
     * ground tick.
     */
    public static void tickVehicles(BattleSimulation sim) {
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        if (!orders.hasGuards()) return;
        for (Map.Entry<Long, MachineGuard> entry : orders.guardEntries()) {
            long id = entry.getKey();
            MachineGuard guard = entry.getValue();
            Faction side = sim.convoy().faction(id);
            if (!MachineAssaults.vehicleArmed(sim, id) || !stillStanding(guard, sim, side)) {
                orders.endGuard(id);
                continue;
            }
            if (!dueForReview(guard, sim.getSimTickIndex())) continue;
            float[] axis = believedAxis(sim, side, guard.target());
            if (axis != null) guard.moveGuard(guard.guardX(), guard.guardY(), axis[0], axis[1]);
        }
    }
}
