package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AssaultMuster.Body;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Objective;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Order;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The assault plan of a stamped fort, and the executor that drives a committed company through it
 * (`phased-assault.md`, slice 3). The plan's data lives with the stamp ({@code FortPlan.approaches()}: gates, nests, the
 * wall); this class places it on the map through the fort's frame, judges what is believed about the nests covering a
 * way in, and walks a company through four steps: <b>base of fire</b>, <b>entry</b>, <b>objective</b>, <b>hold</b>.
 *
 * <p>The plan only assigns roles, places and timing. Every squad's part is an ordinary order the squad's own GOAP
 * executes (attack move to a firing position, to the entry cell, to the aspect), and nothing here names a hostile
 * identity or position: the nests are the fort's disclosed works, whether one is alive is read from the contacts the
 * side's own squads hold, and a nest is believed dead only when a squad of the company stood in sight of it and it is no
 * longer a contact. Symmetric: nothing knows an attacker from a defender.
 *
 * <h2>Steps</h2>
 * <ol>
 *   <li><b>POSITION.</b> The fire element (machine squads, and a third of the infantry when there are enough of them)
 *       goes to firing positions outside the wall with sight of the nests covering the chosen way in and clear of its
 *       approach lane; the assault element gathers at an assembly cell on the lane, outside the nests' reach.</li>
 *   <li><b>SUPPRESS.</b> The fire element engages the covering nests. The assault element waits.</li>
 *   <li><b>ENTRY.</b> Once the covering nests are believed dead, or the suppression has run its bounded time, the assault
 *       element takes the way in: the gate, or the breach. A fire element with no nest left to answer follows.</li>
 *   <li><b>OBJECTIVE.</b> The assault element goes at the aspect {@link DefenceStructureTactics#planMassed} ranks first,
 *       taking it or, with demolition and a wreck pick, wrecking it. When it falls the assault element holds it for a
 *       time ({@code HOLD}) and the next pick follows.</li>
 * </ol>
 * Every figure marked UNTUNED is a first pass; none was set to hit a target. Each wait is bounded, so a plan cannot
 * hold a company at a fort it cannot take forever.
 */
final class FortAssault {

    /** Weighted marines added to the believed need for each nest believed live that covers the chosen way in. UNTUNED. */
    static final float NEST_COVERING_STRENGTH = 8f;
    /** Weighted marines for each other nest believed live: it reaches the approach, if not the way in. UNTUNED. */
    static final float NEST_OTHER_STRENGTH = 2f;
    /** Weighted marines for a gatehouse and baffle to be walked through. UNTUNED. */
    static final float GATE_STRENGTH = 6f;
    /** Weighted marines for a wall section to be cut. UNTUNED. */
    static final float BREACH_STRENGTH = 4f;

    /** A contact this close to a nest's cell is that nest, alive. */
    static final int NEST_CONTACT_CELLS = 3;
    /** A squad this close to a nest is in a position to see whether it is still there. */
    static final int NEST_WITNESS_CELLS = 22;
    /** How far from a nest an infantry fire element stands. Marine rifles reach 22, the nests' guns 24. UNTUNED. */
    static final int FIRE_STANDOFF_INFANTRY = 20;
    /** How far from a nest a machine fire element stands: past the nests' reach, inside a mech's. UNTUNED. */
    static final int FIRE_STANDOFF_MACHINE = 27;
    /** The assault element gathers at least this far from every nest believed live. UNTUNED. */
    static final int ASSEMBLY_STANDOFF = 30;
    /** A firing position keeps this far from the approach lane, so the fire element is not in the way in. */
    static final int LANE_CLEARANCE = 7;
    /** A squad this close to its cell has arrived. */
    static final int ARRIVE_CELLS = 8;
    /** The share of the infantry that becomes the fire element, when there are at least {@link #MIN_INFANTRY_FOR_FIRE}. */
    static final float FIRE_SHARE = 0.34f;
    static final int MIN_INFANTRY_FOR_FIRE = 3;

    /** Ticks POSITION waits for the fire element before suppression begins regardless. UNTUNED. */
    static final int POSITION_TIMEOUT_TICKS = 1500;
    /** Ticks since commit after which the assault element is taken to have assembled, wherever it is. UNTUNED. */
    static final int ASSEMBLY_TIMEOUT_TICKS = 2700;
    /** Ticks of suppression after which the assault element goes in whatever is believed of the nests. UNTUNED. */
    static final int SUPPRESS_MAX_TICKS = 1200;
    /** Ticks ENTRY waits for the assault element to be through the way in. UNTUNED. */
    static final int ENTRY_TIMEOUT_TICKS = 1800;
    /** Ticks the assault element holds a fallen aspect before the next pick. UNTUNED. */
    static final int HOLD_TICKS = 450;
    static final int HOLD_RADIUS_CELLS = 8;

    private FortAssault() {}

    enum Step { POSITION, SUPPRESS, ENTRY, OBJECTIVE, HOLD }

    enum Role { FIRE, ASSAULT }

    /** A way in, on the map. */
    record Way(Variant variant, int gateIndex, int mouthX, int mouthY, int outX, int outY, int entryX, int entryY,
               List<Integer> covering) { }

    /** A fort's plan placed on the map by its frame. */
    static final class Works {
        final List<Way> ways = new ArrayList<>();
        final List<int[]> nests = new ArrayList<>();
        final int centreX;
        final int centreY;

        private Works(FortFrame frame) {
            for (AssaultApproach a : frame.plan().approaches()) {
                int[] mouth = frame.cell(a.mouthX(), a.mouthY());
                int[] beyond = frame.cell(a.mouthX() + a.outX(), a.mouthY() + a.outY());
                int[] entry = frame.cell(a.entryX(), a.entryY());
                ways.add(new Way(a.variant(), a.gateIndex(), mouth[0], mouth[1], beyond[0] - mouth[0],
                        beyond[1] - mouth[1], entry[0], entry[1], a.covering()));
            }
            for (int[] nest : frame.plan().nests()) nests.add(frame.cell(nest[0], nest[1]));
            int[] centre = frame.cell(frame.plan().centrepiece()[0], frame.plan().centrepiece()[1]);
            centreX = centre[0];
            centreY = centre[1];
        }

        static Works of(FortFrame frame) {
            return new Works(frame);
        }
    }

    /** What a side believes of a fort's nests, kept across pulses. */
    static final class NestBelief {
        private final boolean[] seenAlive;
        private final boolean[] dead;

        NestBelief(int nests) {
            seenAlive = new boolean[nests];
            dead = new boolean[nests];
        }

        /**
         * A nest with a contact on it is seen alive. A nest once seen alive, with none now, and with an own squad in
         * sight of it, is believed dead for good. A nest never seen is believed live: the works are disclosed, and a gun
         * out of sight is not a gun absent.
         */
        void update(Works works, List<int[]> hostiles, List<int[]> own) {
            for (int i = 0; i < works.nests.size(); i++) {
                if (dead[i]) continue;
                int[] nest = works.nests.get(i);
                boolean contact = false;
                for (int[] hostile : hostiles) {
                    if (Math.hypot(hostile[0] - nest[0], hostile[1] - nest[1]) <= NEST_CONTACT_CELLS) {
                        contact = true;
                        break;
                    }
                }
                if (contact) {
                    seenAlive[i] = true;
                    continue;
                }
                if (!seenAlive[i]) continue;
                for (int[] mine : own) {
                    if (Math.hypot(mine[0] - nest[0], mine[1] - nest[1]) <= NEST_WITNESS_CELLS) {
                        dead[i] = true;
                        break;
                    }
                }
            }
        }

        boolean live(int nest) {
            return !dead[nest];
        }

        int liveCount() {
            int n = 0;
            for (boolean d : dead) if (!d) n++;
            return n;
        }

        int liveCovering(Way way) {
            int n = 0;
            for (int i : way.covering()) if (!dead[i]) n++;
            return n;
        }

        int deadCount() {
            return seenAlive.length - liveCount();
        }
    }

    /** The believed defence in front of a company, and the way in it would take. */
    record Defence(Way way, int liveCovering, int liveOther, float strength) { }

    /**
     * The way in a company with this make-up would take, and what the nests believed live add to the strength it needs.
     * The least covered gate, nearest the company on a tie; a breach only when the company carries demolition and a
     * machine fire element and the breach is less covered than every gate. Null when the plan offers no way.
     */
    static Defence assess(Works works, NestBelief belief, int fromX, int fromY, boolean demolition, boolean machines) {
        Way best = null;
        int bestCover = Integer.MAX_VALUE;
        double bestGap = Double.MAX_VALUE;
        Way breach = null;
        for (Way way : works.ways) {
            if (way.variant() == Variant.BREACH) {
                breach = way;
                continue;
            }
            int cover = belief.liveCovering(way);
            double gap = Math.hypot(way.mouthX() - fromX, way.mouthY() - fromY);
            if (cover < bestCover || (cover == bestCover && gap < bestGap)) {
                best = way;
                bestCover = cover;
                bestGap = gap;
            }
        }
        if (breach != null && demolition && machines && (best == null || belief.liveCovering(breach) < bestCover)) {
            best = breach;
        }
        if (best == null) return null;
        int covering = belief.liveCovering(best);
        int other = Math.max(0, belief.liveCount() - covering);
        float strength = NEST_COVERING_STRENGTH * covering + NEST_OTHER_STRENGTH * other
                + (best.variant() == Variant.BREACH ? BREACH_STRENGTH : GATE_STRENGTH);
        return new Defence(best, covering, other, strength);
    }

    /** One company's run through the plan. */
    static final class Execution {
        private final Works works;
        private final NestBelief belief;
        private Way way;
        private Step step = Step.POSITION;
        private int stepTick;
        private final int commitTick;
        private final Map<Integer, Role> roles = new TreeMap<>();
        private final Map<Integer, int[]> firePositions = new TreeMap<>();
        private int[] assembly;
        private int[] heldAspect;
        private int[] fallen;
        private int liveCovering;

        Execution(Works works, NestBelief belief, Way way, int tick) {
            this.works = works;
            this.belief = belief;
            this.way = way;
            this.stepTick = tick;
            this.commitTick = tick;
        }

        Step step() {
            return step;
        }

        Way way() {
            return way;
        }

        int liveCovering() {
            return liveCovering;
        }

        int fireSquads() {
            int n = 0;
            for (Role r : roles.values()) if (r == Role.FIRE) n++;
            return n;
        }

        String describe() {
            return step.name() + " via " + way.variant().name().toLowerCase(Locale.ROOT) + ", "
                    + liveCovering + " covering nests believed live, " + fireSquads() + " fire / "
                    + (roles.size() - fireSquads()) + " assault squads";
        }

        /**
         * One pulse. Returns the orders by squad, or null when the aspect cannot be reached, which the caller treats as
         * the company having nothing left to take.
         */
        Map<Integer, Order> run(int tick, Faction perspective, Objective objective, Set<Integer> members,
                                Map<Integer, Body> byId, CommandTopology topology, List<String> notes) {
            assignRoles(members, byId);
            liveCovering = belief.liveCovering(way);
            List<Integer> fire = new ArrayList<>();
            List<Integer> assault = new ArrayList<>();
            for (Map.Entry<Integer, Role> e : roles.entrySet()) {
                (e.getValue() == Role.FIRE ? fire : assault).add(e.getKey());
            }
            if (assembly == null) assembly = assemblyCell(byId, members, topology);
            for (int id : fire) {
                if (!firePositions.containsKey(id)) {
                    int[] cell = firePosition(byId.get(id), fire.indexOf(id), topology);
                    if (cell == null) {
                        roles.put(id, Role.ASSAULT);
                        notes.add("squad " + id + " has no firing position with sight of a covering nest; it goes in with the assault");
                    } else {
                        firePositions.put(id, cell);
                    }
                }
            }
            fire.removeIf(id -> roles.get(id) != Role.FIRE);
            assault.clear();
            for (Map.Entry<Integer, Role> e : roles.entrySet()) if (e.getValue() == Role.ASSAULT) assault.add(e.getKey());

            advance(tick, fire, assault, byId, notes);

            Map<Integer, Order> orders = new TreeMap<>();
            switch (step) {
                case POSITION, SUPPRESS -> {
                    for (int id : fire) orders.put(id, fireOrder(id));
                    for (int id : assault) {
                        orders.put(id, new Order(ObjectiveAssignment.advanceTrack(id, assembly[0], assembly[1]),
                                AssignmentReason.ASSAULT_STAGE));
                    }
                }
                case ENTRY -> {
                    for (int id : assault) {
                        orders.put(id, new Order(ObjectiveAssignment.attackMove(id, way.entryX(), way.entryY()),
                                AssignmentReason.ASSAULT_ENTRY));
                    }
                    followOrFire(fire, orders);
                }
                case HOLD -> {
                    for (int id : assault) {
                        orders.put(id, new Order(ObjectiveAssignment.defendArea(id, fallen[0], fallen[1], HOLD_RADIUS_CELLS),
                                AssignmentReason.ASSAULT_HOLD));
                    }
                    followOrFire(fire, orders);
                }
                case OBJECTIVE -> {
                    List<DefenceStructureTactics.Candidate> candidates = new ArrayList<>();
                    for (int id : assault) candidates.add(candidate(byId.get(id)));
                    if (liveCovering == 0) for (int id : fire) candidates.add(candidate(byId.get(id)));
                    else for (int id : fire) orders.put(id, fireOrder(id));
                    List<DefenceStructureTactics.Task> tasks = DefenceStructureTactics.planMassed(
                            perspective, objective.aspects(), candidates, topology);
                    if (tasks.isEmpty()) return null;
                    int[] aspect = {tasks.get(0).structure().cellX(), tasks.get(0).structure().cellY()};
                    if (heldAspect != null && (heldAspect[0] != aspect[0] || heldAspect[1] != aspect[1])) {
                        fallen = heldAspect;
                        heldAspect = aspect;
                        go(Step.HOLD, tick, notes, "the aspect at (" + fallen[0] + "," + fallen[1] + ") is no longer work");
                        for (int id : assault) {
                            orders.put(id, new Order(ObjectiveAssignment.defendArea(id, fallen[0], fallen[1], HOLD_RADIUS_CELLS),
                                    AssignmentReason.ASSAULT_HOLD));
                        }
                        followOrFire(fire, orders);
                        return orders;
                    }
                    heldAspect = aspect;
                    for (DefenceStructureTactics.Task task : tasks) {
                        orders.put(task.squadId(), new Order(task.assignment(), AssignmentReason.ASSAULT_COMMIT));
                    }
                }
            }
            return orders;
        }

        private Order fireOrder(int id) {
            int[] cell = firePositions.get(id);
            return new Order(ObjectiveAssignment.attackMove(id, cell[0], cell[1]), AssignmentReason.ASSAULT_BASE_OF_FIRE);
        }

        /** The fire element once the way is made: it holds its place while a covering nest lives, else follows in. */
        private void followOrFire(List<Integer> fire, Map<Integer, Order> orders) {
            for (int id : fire) {
                if (liveCovering > 0) {
                    orders.put(id, fireOrder(id));
                } else {
                    orders.put(id, new Order(ObjectiveAssignment.attackMove(id, way.entryX(), way.entryY()),
                            AssignmentReason.ASSAULT_ENTRY));
                }
            }
        }

        private static DefenceStructureTactics.Candidate candidate(Body body) {
            return new DefenceStructureTactics.Candidate(body.squadId(), body.anchorX(), body.anchorY(), body.incumbent(),
                    body.demolition(), body.machine());
        }

        private void go(Step next, int tick, List<String> notes, String why) {
            notes.add("assault plan " + step + " -> " + next + " at tick " + tick + ": " + why);
            step = next;
            stepTick = tick;
        }

        private void advance(int tick, List<Integer> fire, List<Integer> assault, Map<Integer, Body> byId, List<String> notes) {
            int elapsed = tick - stepTick;
            switch (step) {
                case POSITION -> {
                    if (fire.isEmpty()) {
                        go(Step.SUPPRESS, tick, notes, "no fire element; the assault waits for the way to be assembled");
                    } else if (arrivedShare(fire, id -> firePositions.get(id), byId) >= 0.5f) {
                        go(Step.SUPPRESS, tick, notes, "the fire element is in position");
                    } else if (elapsed >= POSITION_TIMEOUT_TICKS) {
                        go(Step.SUPPRESS, tick, notes, "positioning timed out");
                    }
                }
                case SUPPRESS -> {
                    boolean assembled = assault.isEmpty()
                            || arrivedShare(assault, id -> assembly, byId) >= 0.5f
                            || tick - commitTick >= ASSEMBLY_TIMEOUT_TICKS;
                    boolean suppressed = liveCovering == 0 || fire.isEmpty() || elapsed >= SUPPRESS_MAX_TICKS;
                    if (assembled && suppressed) {
                        go(Step.ENTRY, tick, notes, liveCovering == 0 ? "the covering nests are believed dead"
                                : fire.isEmpty() ? "no fire element left"
                                : "suppression ran its " + SUPPRESS_MAX_TICKS + " ticks with " + liveCovering + " nests believed live");
                    }
                }
                case ENTRY -> {
                    if (assault.isEmpty() || arrivedShare(assault, id -> new int[] {way.entryX(), way.entryY()}, byId) >= 0.5f) {
                        go(Step.OBJECTIVE, tick, notes, "the assault element is through the way in");
                    } else if (elapsed >= ENTRY_TIMEOUT_TICKS) {
                        go(Step.OBJECTIVE, tick, notes, "entry timed out");
                    }
                }
                case HOLD -> {
                    if (elapsed >= HOLD_TICKS) go(Step.OBJECTIVE, tick, notes, "the hold is served");
                }
                case OBJECTIVE -> { }
            }
        }

        private interface Place {
            int[] of(int squadId);
        }

        private static float arrivedShare(List<Integer> ids, Place place, Map<Integer, Body> byId) {
            if (ids.isEmpty()) return 1f;
            int arrived = 0;
            for (int id : ids) {
                Body body = byId.get(id);
                int[] cell = place.of(id);
                if (body != null && cell != null
                        && Math.hypot(body.anchorX() - cell[0], body.anchorY() - cell[1]) <= ARRIVE_CELLS) arrived++;
            }
            return arrived / (float) ids.size();
        }

        /**
         * Sticky roles. Machine squads are the fire element; of the infantry, when there are enough, a third without
         * demolition (the assault keeps the satchels). A squad that joins later goes with the assault.
         */
        private void assignRoles(Set<Integer> members, Map<Integer, Body> byId) {
            roles.keySet().retainAll(members);
            firePositions.keySet().retainAll(members);
            boolean first = roles.isEmpty();
            List<Integer> infantry = new ArrayList<>();
            for (int id : members) {
                if (roles.containsKey(id) || !byId.containsKey(id)) continue;
                Body body = byId.get(id);
                if (body.machine()) {
                    roles.put(id, Role.FIRE);
                } else if (first) {
                    infantry.add(id);
                } else {
                    roles.put(id, Role.ASSAULT);
                }
            }
            if (!first) return;
            int fireCount = infantry.size() >= MIN_INFANTRY_FOR_FIRE ? Math.max(1, Math.round(infantry.size() * FIRE_SHARE)) : 0;
            List<Integer> candidates = new ArrayList<>(infantry);
            candidates.sort(Comparator.<Integer>comparingInt(id -> byId.get(id).demolition() ? 1 : 0).thenComparingInt(id -> id));
            for (int i = 0; i < candidates.size(); i++) roles.put(candidates.get(i), i < fireCount ? Role.FIRE : Role.ASSAULT);
        }

        /**
         * The cell on the way's lane, outward from its mouth, nearest the mouth that is walkable and reachable and keeps
         * {@link #ASSEMBLY_STANDOFF} from every nest believed live; the farthest tried when none does.
         */
        private int[] assemblyCell(Map<Integer, Body> byId, Set<Integer> members, CommandTopology topology) {
            Body from = null;
            for (int id : members) if (byId.containsKey(id)) { from = byId.get(id); break; }
            int[] last = {way.mouthX(), way.mouthY()};
            for (int k = 6; k <= 90; k += 2) {
                int x = way.mouthX() + way.outX() * k;
                int y = way.mouthY() + way.outY() * k;
                int[] cell = walkableNear(x, y, from, topology);
                if (cell == null) continue;
                last = cell;
                boolean clear = true;
                for (int i = 0; i < works.nests.size(); i++) {
                    if (belief.live(i) && Math.hypot(works.nests.get(i)[0] - cell[0], works.nests.get(i)[1] - cell[1]) < ASSEMBLY_STANDOFF) {
                        clear = false;
                        break;
                    }
                }
                if (clear) return cell;
            }
            return last;
        }

        private static int[] walkableNear(int x, int y, Body from, CommandTopology topology) {
            for (int ring = 0; ring <= 4; ring++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                        int cx = x + dx;
                        int cy = y + dy;
                        if (topology.inBounds(cx, cy) && topology.isWalkable(cx, cy)
                                && (from == null || topology.reachable(from.anchorX(), from.anchorY(), cx, cy))) {
                            return new int[] {cx, cy};
                        }
                    }
                }
            }
            return null;
        }

        /**
         * A firing position for a fire squad: a reachable cell at its standoff from a live nest covering the way, on the
         * nest's outer side, clear of the approach lane, with sight of the nest; the one nearest the squad. Squads are
         * spread over the covering nests in turn.
         */
        private int[] firePosition(Body body, int turn, CommandTopology topology) {
            List<Integer> targets = new ArrayList<>();
            for (int i : way.covering()) if (belief.live(i)) targets.add(i);
            if (targets.isEmpty()) return null;
            int[] nest = works.nests.get(targets.get(turn % targets.size()));
            int standoff = body.machine() ? FIRE_STANDOFF_MACHINE : FIRE_STANDOFF_INFANTRY;
            for (boolean needSight : new boolean[] {true, false}) {
                int[] best = null;
                double bestGap = Double.MAX_VALUE;
                double ox = nest[0] - works.centreX;
                double oy = nest[1] - works.centreY;
                for (int dy = -standoff - 2; dy <= standoff + 2; dy++) {
                    for (int dx = -standoff - 2; dx <= standoff + 2; dx++) {
                        double d = Math.hypot(dx, dy);
                        if (d < standoff - 2 || d > standoff + 2 || dx * ox + dy * oy <= 0) continue;
                        int x = nest[0] + dx;
                        int y = nest[1] + dy;
                        if (!topology.inBounds(x, y) || !topology.isWalkable(x, y)
                                || !topology.reachable(body.anchorX(), body.anchorY(), x, y)
                                || laneDistance(x, y) < LANE_CLEARANCE
                                || (needSight && !sight(topology, x, y, nest[0], nest[1]))) continue;
                        double gap = Math.hypot(x - body.anchorX(), y - body.anchorY());
                        if (gap < bestGap) {
                            bestGap = gap;
                            best = new int[] {x, y};
                        }
                    }
                }
                if (best != null) return best;
            }
            return null;
        }

        /** Distance from a cell to the approach lane: the ray outward from the way's mouth. */
        private double laneDistance(int x, int y) {
            double px = x - way.mouthX();
            double py = y - way.mouthY();
            double along = px * way.outX() + py * way.outY();
            if (along < 0) return Math.hypot(px, py);
            return Math.abs(px * -way.outY() + py * way.outX());
        }

        /** Whether the straight line from a cell to a nest crosses only walkable cells (the nest's own cell is not). */
        private static boolean sight(CommandTopology topology, int x0, int y0, int x1, int y1) {
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;
            int x = x0;
            int y = y0;
            while (x != x1 || y != y1) {
                int e2 = 2 * err;
                if (e2 > -dy) { err -= dy; x += sx; }
                if (e2 < dx) { err += dx; y += sy; }
                if ((x != x1 || y != y1) && !topology.isWalkable(x, y)) return false;
            }
            return true;
        }
    }
}
