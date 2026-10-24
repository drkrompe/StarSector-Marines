package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.EscortPace;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.DozerBlade;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * R1, Clear the road ({@code cordon-nouns.md}, Optional objectives): a wreck lies across the corridor in
 * the suburb, past the gap, and the base's dozer has to push it off before the first convoy needs the
 * road. The wreck is laid only when this beat is rolled, at setup, on the ground the layout held for it
 * (crate stacks, as {@code BlockKind.WRECK} is laid: there is no wreck art yet); with no dozer to clear it
 * a laid wreck stopped every convoy. The base's dozer starts moved up behind the gap and drives the corridor
 * to the far side of the wreck, its blade opening the way, with an escort out of the allied armour's surplus that it
 * keeps to once under way ({@link EscortPace}), and the company asked only when no hull was free ({@code cordon-allied-command.md}, rule 3). Succeeds
 * when the road through the wreck is open before the first convoy's lead truck reaches it; fails when the
 * dozer dies first, or the convoy reaches a wreck still across the road (the dozer keeps at it for the
 * convoys after).
 */
final class ClearRoadBeat extends CordonBeat {

    /** Cells either side of the road's middle that must be open for a truck. */
    static final int ROAD_HALF = 1;
    /** Corridor cells past the wreck the dozer pulls aside at once the road is open. */
    static final int PAST = 8;
    /** Cells off the road's middle the dozer pulls aside to once the road is open, nearest tried first. */
    static final int[] ASIDE = {6, 9, 12, 16};
    /** Corridor cells past the wreck tried in turn for the place the dozer pulls aside beside. */
    static final int[] ALONG = {PAST, PAST + 6, PAST + 12, PAST / 2};
    /**
     * Cells an aside place keeps from every corridor cell on either axis: off the road, not on its edge. The dozer's
     * half-width and a truck's beside it, and a little more: four left the dozer on the inside of a bend the trucks cut.
     */
    static final int OFF_ROAD = 6;
    /** Corridor cells either side of a place its heading is read across. */
    static final int HEADING_SPAN = 3;
    /** Corridor cells short of the gap the dozer starts. */
    static final int FORWARD = 12;
    /** Cells from a shut road cell the dozer's blade works it. */
    static final float BLADE_REACH = 6.5f;
    /** Corridor cells short of the wreck a convoy's truck needs the road open. */
    static final int NEEDED = 12;
    private static final String CRATES = "doodad.industrial-crate-stack";

    private final List<int[]> wreck = new ArrayList<>();
    /** The wreck's cells across the road's middle a truck needs open. */
    private final List<int[]> road = new ArrayList<>();
    private int wreckIndex = -1;
    private long dozer;
    private boolean sent, through, cleared;
    /** The shut road cell the dozer was last sent to. */
    private int[] target;
    /** Blade push not yet spent as whole structure points. */
    private float push;
    /** How the dozer keeps to its escort once under way: it holds when the escort falls behind. */
    private EscortPace pace;
    /** The cell the dozer was sent aside to once the road was open, or {@code null}; and every cell tried. */
    private int[] aside;
    private final List<int[]> asideTried = new ArrayList<>();
    /** The nearest the dozer has come to its aside place, and the tick it last came nearer. */
    private float asideNearest;
    private int asideSince;

    ClearRoadBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: open before the first convoy needs it; a dead dozer or a convoy at a shut road fails. */
    static Status rule(boolean cleared, boolean dozerAlive, boolean convoyAtWreck) {
        if (cleared) return Status.SUCCEEDED;
        if (!dozerAlive || convoyAtWreck) return Status.FAILED;
        return Status.ACTIVE;
    }

    /**
     * Lays the wreck on its held ground, at setup: every open cell of the site closed to movement and open to
     * sight, a crate stack on it the blade can push. Returns the cells laid.
     */
    static List<int[]> lay(BattleSimulation sim, int[] site) {
        List<int[]> laid = new ArrayList<>();
        TileRegistry registry = TileRegistry.installed();
        DoodadDef def = registry == null ? null : registry.doodad(CRATES);
        if (site == null || def == null) return laid;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (int y = site[1]; y <= site[3]; y++) {
            for (int x = site[0]; x <= site[2]; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isFixture(x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
                topology.setWall(x, y, false);
                topology.setFixture(x, y, true);
                sim.addDoodad(new Doodad(x, y, def));
                laid.add(new int[]{x, y});
            }
        }
        for (int[] c : laid) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (grid.inBounds(c[0] + dx, c[1] + dy)) grid.recomputeCoverAt(c[0] + dx, c[1] + dy);
                }
            }
        }
        return laid;
    }

    /** Lays the wreck and sets the dozer down at the base end of the road: at setup. */
    void setUp(BattleSimulation sim) {
        wreck.addAll(lay(sim, beats.bounds(CordonPlan.CORRIDOR_WRECK)));
        List<int[]> corridor = beats.corridor();
        if (wreck.isEmpty() || corridor == null) return;
        int[] mid = beats.centre(CordonPlan.CORRIDOR_WRECK);
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < corridor.size(); i++) {
            long dx = corridor.get(i)[0] - mid[0], dy = corridor.get(i)[1] - mid[1];
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                wreckIndex = i;
            }
        }
        int[] at = corridor.get(wreckIndex);
        for (int[] c : wreck) {
            if (Math.abs(c[0] - at[0]) <= ROAD_HALF && Math.abs(c[1] - at[1]) <= ROAD_HALF) road.add(c);
        }
    }

    /** The wreck's cells as laid, for evidence. */
    List<int[]> wreck() { return wreck; }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (wreckIndex < 0 || road.isEmpty()) return Status.LAPSED;
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null) return Status.LAPSED;
        List<int[]> corridor = beats.corridor();
        if (!sent) {
            sent = true;
            // Moved up behind the gap before the battle: from the base it lost the race to the first convoy on
            // every long corridor (a dozer is a third of a truck's pace).
            dozer = setDown(sim, convoys);
            if (dozer == 0L) return Status.LAPSED;
            // It sets out at once and keeps to its escort only once under way: staged forward to beat the first convoy
            // to the wreck, it lost that race in town, where no hull is ever free to escort it, by waiting the two
            // seconds a truck at the base gives the armour to offer one.
            pace = new EscortPace(tick);
            pace.release(sim, dozer);
            setOut(sim);
        }
        if (!cleared && CordonBeats.alive(sim, dozer)) pace.step(sim, dozer, beats.escortOf(dozer));
        int[] lead = convoys.truck(sim, 1, 0);
        boolean atWreck = lead != null && !convoys.detoured() && lead[0] >= wreckIndex - NEEDED;
        return rule(cleared, CordonBeats.alive(sim, dozer), atWreck);
    }

    /**
     * Sets the dozer down behind the gap, at the first corridor cell {@link #startIndex} offers that it can drive to
     * the wreck from, over the ground its blade opens, and facing along that route's first leg
     * ({@link SupplyDelivery#facing}); where none can, where it always was, facing the front. Set down at the corridor
     * cell {@link #FORWARD} short of the gap whatever lay there, it stood in town where its footprint could not turn
     * onto the street (every route refused at its first bend), or snapped into a yard walled all round, and never
     * reached the wreck.
     */
    private long setDown(BattleSimulation sim, CordonConvoys convoys) {
        List<int[]> corridor = beats.corridor();
        int preferred = Math.max(convoys.entry(), convoys.gapIndex() - FORWARD);
        VehicleType type = VehicleType.DOZER_BREACHER;
        int radius = VehicleClearance.radiusForWidth(type.visualWidthCells);
        VehicleClearance stand = VehicleClearance.erode(sim.getGrid(), radius);
        // The move order proves a bladed hull's route over the blade's view of the map; so does this.
        NavigationGrid view = sim.blade() != null ? sim.blade().planningGrid() : sim.getGrid();
        VehicleClearance mask = VehicleClearance.erode(view, radius);
        TerrainCostField cost = TerrainCostField.from(sim.getTopology());
        int[] mid = corridor.get(wreckIndex);
        int[] goal = VehicleRoutePlanner.snapToMask(mask, mid[0], mid[1], ORDER_SNAP);
        int[][] at = new int[1][];
        float[][][] way = new float[1][][];
        int index = goal == null ? -1 : startIndex(preferred, convoys.entry(), convoys.gapIndex() - 1, i -> {
            int[] cell = VehicleRoutePlanner.snapToMask(stand, corridor.get(i)[0], corridor.get(i)[1], CordonBeats.STAND_REACH);
            if (cell == null || !mask.isPassable(cell[0], cell[1])) return false;
            float[][] route = VehicleRoutePlanner.routeDrivable(cell[0], cell[1], goal[0], goal[1], view, cost, mask, type);
            if (route == null) return false;
            at[0] = cell;
            way[0] = route;
            return true;
        });
        if (index < 0) {
            int[] from = corridor.get(preferred);
            return beats.deploy(sim, type, from[0], from[1]);
        }
        float facing = SupplyDelivery.facing(way[0], at[0][0] + .5f, at[0][1] + .5f, goal[0] + .5f, goal[1] + .5f);
        return beats.command().deploy(sim, type, at[0][0], at[0][1], facing);
    }

    /** Cells a move order looks round its goal for ground the hull fits, as {@code VehicleMoveOrderSystem} does. */
    private static final int ORDER_SNAP = 6;

    /** Corridor cells either side of the preferred start tried in turn for the dozer's, nearest first, back before on. */
    static final int[] START_STEPS = {0, -3, 3, -6, 6, -9, 9, -12, -15, -18, -21, -24};

    /**
     * The corridor cell the dozer is set down at: the first of {@link #START_STEPS} from {@code preferred}, within
     * {@code lo..hi}, that {@code drives} to the wreck; {@code -1} where none does.
     */
    static int startIndex(int preferred, int lo, int hi, IntPredicate drives) {
        for (int step : START_STEPS) {
            int i = preferred + step;
            if (i < lo || i > hi) continue;
            if (drives.test(i)) return i;
        }
        return -1;
    }

    /**
     * Sends the dozer onto the wreck's own middle, which only the blade's view of the ground opens: sent past it, the
     * dozer drove round the wreck through the fields and pushed nothing.
     */
    private void setOut(BattleSimulation sim) {
        int[] at = beats.corridor().get(wreckIndex);
        beats.command().station(sim, dozer, at[0], at[1]);
    }

    /** The road through the wreck opened: the convoys' routes are laid again through it, even after a failure. */
    @Override
    void watch(BattleSimulation sim, int tick) {
        if (pace != null && !active() && CordonBeats.alive(sim, dozer)) {
            // Its escort ends with the beat; the dozer keeps at the wreck for the convoys after, on its own.
            pace.release(sim, dozer);
        }
        if (cleared) {
            // A pull-aside the move order gave up on leaves the dozer on the road: the next place, until one is
            // reached or none is left.
            // So does one it is wedged on: a dozer pinned against a fence beside the road neither moves nor is refused.
            if (aside == null || !CordonBeats.alive(sim, dozer)) return;
            VehicleMoveOrderService.RefusedOrder refused = sim.getVehicleMoveOrderService().refusal(dozer);
            GroundBody body = sim.convoy().body(dozer);
            float d = body.distanceTo(aside[0] + .5f, aside[1] + .5f);
            if (d < asideNearest - ASIDE_PROGRESS) {
                asideNearest = d;
                asideSince = tick;
            }
            boolean gaveUp = refused != null && refused.requestedX() == aside[0] && refused.requestedY() == aside[1];
            VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
            VehicleMoveOrderService.ActiveOrder order = orders.activeOrder(dozer);
            boolean underWay = orders.pending(dozer)
                    || order != null && order.requestedX() == aside[0] && order.requestedY() == aside[1];
            if (asideTried.size() < ASIDE_TRIES && (gaveUp || asideStalled(d, tick - asideSince, underWay))) {
                pullAside(sim);
            }
            return;
        }
        if (road.isEmpty() || beats.sequence().convoys() == null) return;
        int[] closed = null;
        for (int[] c : road) {
            if (!sim.getGrid().isWalkable(c[0], c[1])) {
                closed = c;
                break;
            }
        }
        if (closed == null) {
            cleared = true;
            if (pace != null) pace.release(sim, dozer);
            beats.sequence().convoys().relay(sim);
            pullAside(sim);
            return;
        }
        if (sent && pace != null && pace.setOut() && CordonBeats.alive(sim, dozer)) drive(sim, closed);
    }

    /**
     * Sends the dozer off the road past the wreck, so it is not in the trucks' way: the first place {@link #aside}
     * offers that its footprint stands on and it can drive to from where it is, and that was not tried before.
     */
    private void pullAside(BattleSimulation sim) {
        if (!CordonBeats.alive(sim, dozer)) return;
        GroundBody body = sim.convoy().body(dozer);
        VehicleType type = sim.convoy().vehicleType(dozer);
        VehicleClearance mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(type.visualWidthCells));
        TerrainCostField cost = TerrainCostField.from(sim.getTopology());
        int[] from = VehicleRoutePlanner.snapToMask(mask, (int) body.x, (int) body.y, BLADE_SNAP);
        // Its move order proves a bladed hull's route over the ground the blade opens; that view is asked only where
        // no place is reached over open ground, so the dozer pushes through a yard wall only when it must.
        NavigationGrid view = sim.blade() != null ? sim.blade().planningGrid() : sim.getGrid();
        VehicleClearance bladeMask = VehicleClearance.erode(view, VehicleClearance.radiusForWidth(type.visualWidthCells));
        int[] bladeFrom = VehicleRoutePlanner.snapToMask(bladeMask, (int) body.x, (int) body.y, BLADE_SNAP);
        aside = aside(beats.corridor(), wreckIndex, (x, y) -> {
            if (tried(x, y)) return false;
            return from != null && mask.isPassable(x, y) && VehicleRoutePlanner.routeDrivable(from[0], from[1], x, y,
                    sim.getGrid(), cost, mask, type) != null;
        }, (x, y) -> {
            if (tried(x, y)) return false;
            return bladeFrom != null && mask.isPassable(x, y) && VehicleRoutePlanner.routeDrivable(bladeFrom[0],
                    bladeFrom[1], x, y, view, cost, bladeMask, type) != null;
        });
        if (aside == null) return;
        asideTried.add(aside);
        asideNearest = body.distanceTo(aside[0] + .5f, aside[1] + .5f);
        asideSince = sim.getSimTickIndex();
        beats.command().station(sim, dozer, aside[0], aside[1]);
    }

    private boolean tried(int x, int y) {
        for (int[] t : asideTried) if (t[0] == x && t[1] == y) return true;
        return false;
    }

    /**
     * Where the dozer pulls aside: the first place {@link #aside(List, int, Ground)} offers over {@code open} ground,
     * and only where none is, the first it offers over {@code bladed} ground, which its blade opens on the way. In town
     * no place beside the road could be driven to round the blocks, the dozer stood on a street one hull wide past the
     * wreck, and the outbound convoys halted at it all battle.
     */
    static int[] aside(List<int[]> corridor, int wreckIndex, Ground open, Ground bladed) {
        int[] place = aside(corridor, wreckIndex, open);
        return place != null ? place : aside(corridor, wreckIndex, bladed);
    }

    /** Cells nearer its aside place the dozer must come to count as moving toward it. */
    static final float ASIDE_PROGRESS = 1f;
    /** Cells from its aside place the dozer has pulled aside. */
    static final float ASIDE_REACHED = 3f;
    /** Ticks the dozer may go without coming nearer its aside place before the next is tried. */
    static final int ASIDE_STALL_TICKS = Math.round(8f / BattleSimulation.TICK_DT);

    /** Aside places the dozer is sent to at most: after that it stays where the last one left it. */
    static final int ASIDE_TRIES = 4;

    /**
     * Whether the dozer is wedged short of its aside place: still under way to it, not there, and no nearer for
     * {@link #ASIDE_STALL_TICKS}. A move order pinned against a fence neither progresses nor is given up on, and the
     * dozer stood on the road. One whose order is done has arrived as near as it stops, and is not sent on: re-sent
     * every few seconds, it wandered beside the road among the trucks.
     */
    static boolean asideStalled(float distance, int ticksSinceProgress, boolean underWay) {
        return underWay && distance > ASIDE_REACHED && ticksSinceProgress >= ASIDE_STALL_TICKS;
    }

    /** Cells round the dozer its footprint is looked for when it plans its pull-aside. */
    static final int BLADE_SNAP = 4;

    /** Whether the dozer can stand on a cell and drive there. */
    interface Ground {
        boolean takes(int x, int y);
    }

    /**
     * Where the dozer pulls aside once the road is open: beside the corridor past the wreck, square to the road's
     * heading there, on either side and at each of {@link #ASIDE} cells out, nearest first and the right before the
     * left, at each of the {@link #ALONG} places past the wreck in turn; only cells {@link #OFF_ROAD} clear of every
     * corridor cell, and the first {@code ground} takes. {@code null} where none is. A fixed step south-east of the
     * road put it in a wall or back on the road, the move order gave up, and it stood in the convoys' way.
     */
    static int[] aside(List<int[]> corridor, int wreckIndex, Ground ground) {
        if (corridor == null || corridor.isEmpty() || wreckIndex < 0) return null;
        int last = corridor.size() - 1;
        for (int along : ALONG) {
            int i = Math.min(last, wreckIndex + along);
            int[] a = corridor.get(Math.max(0, i - HEADING_SPAN)), b = corridor.get(Math.min(last, i + HEADING_SPAN));
            double hx = b[0] - a[0], hy = b[1] - a[1], len = Math.hypot(hx, hy);
            if (len == 0) continue;
            // The road's right, square to its heading; then its left.
            double rx = -hy / len, ry = hx / len;
            int[] at = corridor.get(i);
            for (int out : ASIDE) {
                for (int side = 1; side >= -1; side -= 2) {
                    int x = (int) Math.round(at[0] + side * rx * out), y = (int) Math.round(at[1] + side * ry * out);
                    if (offRoad(corridor, x, y) && ground.takes(x, y)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean offRoad(List<int[]> corridor, int x, int y) {
        for (int[] c : corridor) {
            if (Math.abs(c[0] - x) < OFF_ROAD && Math.abs(c[1] - y) < OFF_ROAD) return false;
        }
        return true;
    }

    /**
     * Keeps the dozer at the wreck until the road through it is open. The move order stops a hull short of a
     * goal it cannot stand on, so the blade often never bore on the wreck; a dozer standing within
     * {@link #BLADE_REACH} of a shut road cell pushes it at the blade's own rate ({@link DozerBlade#PUSH_PER_SECOND})
     * through the ordinary wall-damage path. Once it has worked at the wreck it is sent on past it.
     */
    private void drive(BattleSimulation sim, int[] closed) {
        GroundBody body = sim.convoy().body(dozer);
        if (Math.abs(body.x - closed[0]) <= BLADE_REACH && Math.abs(body.y - closed[1]) <= BLADE_REACH) {
            through = true;
            push += DozerBlade.PUSH_PER_SECOND * BattleSimulation.TICK_DT;
            int spend = (int) push;
            push -= spend;
            if (spend > 0) sim.mapEditor().damageWall(closed[0], closed[1], spend);
        } else if (target != closed) {
            target = closed;
            beats.command().station(sim, dozer, closed[0], closed[1]);
        }
    }

    @Override
    String describe(BattleSimulation sim) {
        int open = 0;
        for (int[] c : road) if (sim.getGrid().isWalkable(c[0], c[1])) open++;
        String hull = !CordonBeats.alive(sim, dozer) ? "dozer gone" : String.format(Locale.ROOT,
                "dozer at %.0f,%.0f structure %.0f", sim.convoy().body(dozer).x, sim.convoy().body(dozer).y,
                sim.convoy().structure(dozer));
        VehicleMoveOrderService.RefusedOrder refused = CordonBeats.alive(sim, dozer)
                ? sim.getVehicleMoveOrderService().refusal(dozer) : null;
        int[] at = beats.corridor().get(wreckIndex);
        return "wreck " + wreck.size() + " cells at corridor cell " + wreckIndex + " (" + at[0] + "," + at[1] + "), "
                + open + "/" + road.size() + " road cells open; " + hull + (through ? ", through" : "")
                + (cleared ? aside == null ? ", no aside place" : ", aside " + aside[0] + "," + aside[1] + " (try "
                + asideTried.size() + ")" : "")
                + (refused == null ? "" : ", refused " + refused.reason() + " to " + refused.requestedX() + ","
                + refused.requestedY());
    }

    @Override
    long escortHull(BattleSimulation sim) {
        return !cleared && CordonBeats.alive(sim, dozer) && (pace == null || !pace.alone()) ? dozer : 0L;
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (!CordonBeats.alive(sim, dozer) || beats.covered(dozer)) return null;
        GroundBody body = sim.convoy().body(dozer);
        return new int[]{(int) body.x, (int) body.y, 2};
    }
}
