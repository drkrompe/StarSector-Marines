package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.ClearanceComponents;
import com.dillon.starsectormarines.battle.vehicle.ConvoyPlanner;
import com.dillon.starsectormarines.battle.vehicle.DrivableRouteSearch;
import com.dillon.starsectormarines.battle.vehicle.RouteSearchBudget;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleController;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One convoy dispatch's route proof, carried across ticks.
 *
 * <p>The proof is an enumeration — perimeter entries against interior drop
 * junctions against exits — and every pair that survives the cheap filters costs
 * a cost-field A* over the whole grid, several of them where the turn refinement
 * rejects a bend. {@link #SEARCH_BUDGET} bounds how many of those one dispatch
 * may spend; this class bounds how many of them land in one <em>tick</em>.
 *
 * <p><b>Those are different bounds and the second one is what a player feels.</b>
 * Bounded only in total, the whole proof still ran inside the tick that asked
 * for it: on the production 560x336 Conquest fixture that was 118 ms of one
 * game-thread frame, which is a visible hitch on hardware weaker than the one it
 * was measured on. Nothing about the question requires an answer this frame —
 * the delivery it authorises takes six seconds to appear and a minute to arrive.
 * So the enumeration is a resumable object: {@link #step} does at most
 * {@link ConvoyMeans#SEARCHES_PER_TICK} searches and puts it down, and the
 * dispatch that started it reads a finished result some ticks later.
 *
 * <p><b>Every cursor here is state a restart would destroy.</b> The mask a
 * {@link DrivableRouteSearch} accumulates is the obvious one, but so is which
 * entry is being tried and how many drops beneath it have already been refused:
 * beginning again each tick would spend the budget on the same first candidate
 * forever. Progress is why the object exists.
 *
 * <p>Bound to the grid revision it was built against. A world that closes ground
 * under a half-proved route has invalidated the mask, the labels and every
 * partial search at once, and the honest response is to start over rather than
 * to finish a proof about a map that no longer exists.
 */
final class RouteProofJob {

    /** How the proof stands. */
    enum State {
        /** Still enumerating; call {@link #step} again. */
        RUNNING,
        /** A complete journey exists; read it from {@link #plan}. */
        PROVED,
        /** The enumeration or the budget ran out with nothing drivable. Terminal. */
        NO_ROUTE
    }

    /**
     * Grid searches one dispatch may spend proving its journey.
     *
     * <p>The proof is an enumeration — entries against junctions against exits
     * — and every pair of endpoints costs a cost-field A* over a 188,160-cell
     * grid, up to eight of them where the turn refinement rejects a bend. That
     * product is unbounded in the map rather than in the question, and on a
     * production Conquest fixture it reached a thousand searches: three to five
     * seconds of one tick, nine watchdog dumps deep, while the rest of the tick
     * came to twenty milliseconds.
     *
     * <p><b>One budget for the whole enumeration replaces eight tries per
     * pair, and that is a deliberate redistribution rather than only a
     * ceiling.</b> On the fixture this was measured from, the best-ranked drop
     * routed its inbound leg on the first search and then needed <em>eighteen
     * more</em> before its outbound leg was drivable. Under a flat eight the
     * dispatch abandoned it, and the next ninety-odd ranked drops in turn, at
     * eight searches each — 745 of them — before settling on a drop three times
     * further from the ground the request actually asked for. Ranking says the
     * first candidate is the best one; spending the budget on it rather than
     * rationing every candidate alike is both cheaper and a better delivery.
     *
     * <p>Thirty-two is that measured nineteen with room. A delivery that cannot
     * be proven in that many searches, on a map whose reachability has already
     * been settled without searching at all, is the bugged map the dispatcher's
     * own diagnostic already names — and the request falls through to another
     * means rather than being lost.
     *
     * <p><b>This is the only bound on searching, deliberately.</b> Drops and
     * exits were capped by count as well, at six and three, which reads as
     * generous and is a bar set in the dark: the canonical 240x160 rear-entry
     * map proves its route at about the seventh ranked drop, and the count cap
     * refused a delivery the budget would have paid for. Everything that
     * reaches the router costs at least one search, so the budget already
     * bounds how many candidates can be tried — and it bounds them in the
     * currency the stall was measured in.
     */
    static final int SEARCH_BUDGET = 32;

    /**
     * Perimeter entries a dispatch will actually route from, nearest the drop
     * first.
     *
     * <p>The one bound that is not counted in searches, because what an entry
     * costs before any search is a road-graph flood and a sort of every
     * junction it reaches. Ranking is what makes it safe: the fifth-nearest
     * gate is a worse delivery than the first, not a different one.
     *
     * <p>Nothing else is capped by count. Drops and exits are bounded by
     * {@link #SEARCH_BUDGET} alone, since every one of them that gets as far as
     * the router spends at least one search — and a cap by count is a bar set in
     * the dark. Six drops per entry looked generous and refused a delivery the
     * canonical 240x160 rear-entry map proves at its seventh.
     */
    private static final int MAX_ENTRIES_TRIED = 4;

    /** Max Chebyshev rings used to resolve an interior junction onto the vehicle-clearance mask. */
    private static final int SNAP_RADIUS = 8;

    /** Minimum cell separation between a fresh dispatch's destination junction and any already-active convoy truck's LZ. Soft preference — route candidates degrade to overlap only after separated peers. */
    private static final int MIN_DEST_SEPARATION = 4;

    private final RoadGraph graph;
    private final TraversalAxis axis;
    private final DeliveryDeployment deployment;
    private final int hintX;
    private final int hintY;
    private final NavigationGrid grid;
    private final int width;
    private final int height;
    private final TerrainCostField cost;
    private final VehicleClearance clearance;
    private final ClearanceComponents components;
    private final LandingZoneScorer scorer;
    private final List<int[]> reserved;
    private final List<RoadGraph.Node> perimeter;
    private final List<RoadGraph.Node> entries;
    private final RouteSearchBudget budget;
    /** The grid passability this proof was built against; a different one invalidates every cursor below. */
    private final long gridRevision;

    private State state = State.RUNNING;
    private RoutePlan plan;

    private int entryCursor;
    private int entriesTried;
    private RoadGraph.Node entry;
    private int[] entryCell;

    private List<RoadGraph.Node> destinations;
    private int destinationCursor;
    private RoadGraph.Node destination;
    private int[] destinationCell;

    private DrivableRouteSearch inboundSearch;
    private float[][] inbound;

    private List<RoadGraph.Node> exits;
    private int exitCursor;
    private RoadGraph.Node exit;
    private int[] exitCell;
    private DrivableRouteSearch outboundSearch;

    /**
     * A proof for this delivery, or {@code null} when the map offers no eligible
     * perimeter at all — which is a refusal rather than a job, and one the
     * feasibility probe has usually already made.
     */
    static RouteProofJob start(RoadGraph graph, TraversalAxis axis,
                               DeliveryDeployment deployment,
                               List<RoadGraph.Node> perimeter,
                               NavigationGrid grid, long gridRevision,
                               TerrainCostField cost, VehicleClearance clearance,
                               ClearanceComponents components,
                               LandingZoneScorer scorer, List<int[]> reserved) {
        if (perimeter.isEmpty()) return null;
        return new RouteProofJob(graph, axis, deployment, grid, gridRevision, cost,
                clearance, components, scorer, reserved, perimeter);
    }

    private RouteProofJob(RoadGraph graph, TraversalAxis axis,
                          DeliveryDeployment deployment,
                          NavigationGrid grid, long gridRevision,
                          TerrainCostField cost, VehicleClearance clearance,
                          ClearanceComponents components,
                          LandingZoneScorer scorer, List<int[]> reserved,
                          List<RoadGraph.Node> perimeter) {
        this.graph = graph;
        this.axis = axis;
        this.deployment = deployment;
        this.hintX = deployment.hintX();
        this.hintY = deployment.hintY();
        this.grid = grid;
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        this.gridRevision = gridRevision;
        this.cost = cost;
        this.clearance = clearance;
        this.components = components;
        this.scorer = scorer;
        this.reserved = reserved;
        this.perimeter = perimeter;
        this.entries = ConvoyMeans.sortedByDistance(perimeter, hintX, hintY);
        this.budget = new RouteSearchBudget(SEARCH_BUDGET);
    }

    State state() { return state; }

    RoutePlan plan() { return plan; }

    long gridRevision() { return gridRevision; }

    RouteSearchBudget budget() { return budget; }

    /**
     * Advances the enumeration by at most {@code maxSearches} grid searches.
     *
     * <p>Work that costs no search — ranking an entry's reachable junctions,
     * snapping a candidate onto the mask, refusing a pair the component labels
     * already separate — runs freely inside a step, because that is the work the
     * previous slice made cheap and none of it is what a stall is made of. The
     * allowance rations searching, which is.
     */
    State step(int maxSearches) {
        if (state != State.RUNNING) return state;
        int allowance = Math.max(1, maxSearches);
        int spentAtStart = budget.spent();
        while (state == State.RUNNING) {
            if (budget.isExhausted()) {
                state = State.NO_ROUTE;
                break;
            }
            int used = budget.spent() - spentAtStart;
            if (used >= allowance) break;
            pump(allowance - used);
        }
        return state;
    }

    /** One unit of progress, innermost cursor first so a started search is finished before another is begun. */
    private void pump(int allowance) {
        if (outboundSearch != null) {
            pumpOutbound(allowance);
        } else if (exits != null) {
            advanceExit();
        } else if (inboundSearch != null) {
            pumpInbound(allowance);
        } else if (destinations != null) {
            advanceDestination();
        } else {
            advanceEntry();
        }
    }

    private void advanceEntry() {
        while (entryCursor < entries.size() && entriesTried < MAX_ENTRIES_TRIED) {
            RoadGraph.Node candidate = entries.get(entryCursor++);
            int[] cell = ConvoyMeans.perimeterRouteCell(axis, clearance, candidate,
                    width, height);
            if (cell == null) continue;
            entriesTried++;
            entry = candidate;
            entryCell = cell;
            destinations = rankedDrops(reachableFrom(candidate));
            destinationCursor = 0;
            return;
        }
        state = State.NO_ROUTE;
    }

    private void advanceDestination() {
        while (destinationCursor < destinations.size()) {
            RoadGraph.Node candidate = destinations.get(destinationCursor++);
            int[] cell = VehicleRoutePlanner.snapToMask(clearance,
                    candidate.cellX, candidate.cellY, SNAP_RADIUS);
            if (cell == null
                    || entryCell[0] == cell[0] && entryCell[1] == cell[1]
                    || !components.connected(entryCell[0], entryCell[1], cell[0], cell[1])
                    || !scorer.isViable(cell[0], cell[1])
                    || !ConvoyMeans.behindMinimum(axis, cell[0], cell[1],
                    deployment.minimumDefenderForward())) {
                continue;
            }
            destination = candidate;
            destinationCell = cell;
            inboundSearch = DrivableRouteSearch.over(entryCell[0], entryCell[1],
                    cell[0], cell[1], grid, cost, clearance, VehicleType.HEAVY_APC);
            return;
        }
        destinations = null;
    }

    private void pumpInbound(int allowance) {
        DrivableRouteSearch.Status status = inboundSearch.advance(budget, allowance);
        if (status == DrivableRouteSearch.Status.PENDING) return;
        if (status == DrivableRouteSearch.Status.ROUTED) {
            inbound = inboundSearch.route();
            inboundSearch = null;
            exits = deployment.strictDefenderRearEntry()
                    ? ConvoyMeans.sortedByDistance(perimeter,
                    destination.cellX, destination.cellY)
                    : List.of(ConvoyPlanner.pickExitNode(graph, destination, entry));
            exitCursor = 0;
            return;
        }
        inboundSearch = null;
    }

    private void advanceExit() {
        while (exitCursor < exits.size()) {
            RoadGraph.Node candidate = exits.get(exitCursor++);
            int[] cell = ConvoyMeans.perimeterRouteCell(axis, clearance, candidate,
                    width, height);
            if (cell == null) continue;
            if (!components.connected(destinationCell[0], destinationCell[1],
                    cell[0], cell[1])) {
                continue;
            }
            exit = candidate;
            exitCell = cell;
            outboundSearch = DrivableRouteSearch.over(
                    destinationCell[0], destinationCell[1], cell[0], cell[1],
                    grid, cost, clearance, VehicleType.HEAVY_APC);
            return;
        }
        exits = null;
        inbound = null;
    }

    private void pumpOutbound(int allowance) {
        DrivableRouteSearch.Status status = outboundSearch.advance(budget, allowance);
        if (status == DrivableRouteSearch.Status.PENDING) return;
        if (status == DrivableRouteSearch.Status.ROUTED) {
            float[][] outbound = outboundSearch.route();
            outboundSearch = null;
            // The one bend on neither polyline: the turn from the way the truck
            // arrives to the way it must leave. An LZ whose entry and exit
            // disagree by more than the chassis can turn in the room available
            // delivers its marines and then strands the vehicle for the rest of
            // the battle.
            if (canLeaveTheWayItArrived(inbound, outbound)) {
                plan = new RoutePlan(entry, destination, exit, inbound, outbound,
                        cost, clearance);
                state = State.PROVED;
            }
            return;
        }
        outboundSearch = null;
    }

    /**
     * Whether a truck arriving down {@code inbound} can point itself along
     * {@code outbound} at the drop point. Both are {@code [xs][ys]} polylines
     * meeting at the LZ.
     */
    private boolean canLeaveTheWayItArrived(float[][] inbound, float[][] outbound) {
        int in = inbound[0].length;
        if (in < 2 || outbound[0].length < 2) return true;
        float lzX = inbound[0][in - 1];
        float lzY = inbound[1][in - 1];
        float approach = AirBody.facingToward(lzX - inbound[0][in - 2],
                lzY - inbound[1][in - 2]);
        // Ask the question with no run-up in it. Docking may well rescue this
        // pairing, but it is an attempt rather than a guarantee, and a truck
        // that arrives without docking is left standing on the drop point
        // itself — which can be a much tighter piece of road than the one the
        // docking maneuver would have been evaluated in.
        return VehicleController.canTurnOntoRouteAt(grid, VehicleType.HEAVY_APC,
                lzX, lzY, approach, outbound[0], outbound[1]);
    }

    /** BFS flood from {@code seed} over edges — returns the seed's connected component as a Set. */
    private static Set<RoadGraph.Node> reachableFrom(RoadGraph.Node seed) {
        Set<RoadGraph.Node> seen = new HashSet<>();
        Deque<RoadGraph.Node> queue = new ArrayDeque<>();
        queue.add(seed);
        seen.add(seed);
        while (!queue.isEmpty()) {
            RoadGraph.Node node = queue.poll();
            for (RoadGraph.Edge edge : node.edges()) {
                RoadGraph.Node next = edge.otherEnd(node);
                if (seen.add(next)) queue.add(next);
            }
        }
        return seen;
    }

    /** Ranked viable drop junctions; route proof, not graph proximity, makes the commitment. */
    private List<RoadGraph.Node> rankedDrops(Set<RoadGraph.Node> reachable) {
        int minimumForward = deployment.minimumDefenderForward();
        List<RoadGraph.Node> candidates = new ArrayList<>();
        for (RoadGraph.Node node : reachable) {
            if (node.perimeter || node.degree() < 2
                    || !scorer.isViable(node.cellX, node.cellY)
                    || !ConvoyMeans.behindMinimum(axis, node.cellX, node.cellY,
                    minimumForward)) {
                continue;
            }
            candidates.add(node);
        }
        candidates.sort(Comparator
                .comparingInt((RoadGraph.Node node) -> node.degree() >= 3 ? 0 : 1)
                .thenComparingInt(node -> nearAnyReserved(
                        node.cellX, node.cellY, reserved) ? 1 : 0)
                .thenComparingInt(node -> distanceSquared(
                        node.cellX, node.cellY, hintX, hintY))
                .thenComparingInt(node -> node.cellX)
                .thenComparingInt(node -> node.cellY));
        return candidates;
    }

    private static int distanceSquared(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** True iff {@code (x, y)} is within {@link #MIN_DEST_SEPARATION} cells of any reserved point. Squared-distance comparison so no sqrt. */
    private static boolean nearAnyReserved(int x, int y, List<int[]> reserved) {
        if (reserved.isEmpty()) return false;
        int separationSq = MIN_DEST_SEPARATION * MIN_DEST_SEPARATION;
        for (int[] point : reserved) {
            int dx = x - point[0];
            int dy = y - point[1];
            if (dx * dx + dy * dy < separationSq) return true;
        }
        return false;
    }
}
