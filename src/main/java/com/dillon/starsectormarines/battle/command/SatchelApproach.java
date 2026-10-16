package com.dillon.starsectormarines.battle.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a squad that must stand at a wall with a charge may walk up to it, and what the company can do to make it so
 * (`phased-assault.md`, "Satchel squads keep holding"). Pure over the facts it is handed: no unit, nest or weapon is
 * named, so the plan executor ({@link FortAssault.Execution}) supplies what its company believes and does what this
 * class chooses.
 *
 * <p>A satchel is set by hand at the foot of a wall, so the sapper spends its whole approach inside the reach of every
 * gun that can see the route, and it is the most fragile thing in the company. It is therefore let go only when the
 * approach is <em>survivable</em>: every nest believed live that reaches the route has been <b>answered</b>, one of
 * four ways, each a fact the company can hold from what it has seen and done:
 * <ul>
 *   <li>{@link Answer#DEAD}: the nest is believed destroyed;</li>
 *   <li>{@link Answer#SUPPRESSED}: a friendly element in position with sight of it has been engaging it for the
 *       settle time ({@link #SUPPRESSION_SETTLE_TICKS}), so it is not firing freely;</li>
 *   <li>{@link Answer#BLINDED}: smoke stands on the line from the nest to every part of the route it could otherwise
 *       see;</li>
 *   <li>{@link Answer#HIDDEN}: no cell of the route that lies in the nest's reach is in its sight (dead ground).</li>
 * </ul>
 * A nest that is none of these is open. For each open nest the planner picks the cheapest {@link Enabler} the company
 * can use now (engage with the base of fire, throw smoke, call a strike), waits on one already in motion, and
 * otherwise reports that nothing available serves it. The outcome is a {@link Plan}: GO when nothing is open, ENABLING
 * while an action is requested or in motion, HOLD when an open nest has nothing to answer it.
 *
 * <p>Effects feed back by being re-read: a nest the strike kills reads DEAD the next pulse, a squad that arrives
 * and settles makes its nest read SUPPRESSED, and a cloud that thins out makes the nest read open again.
 */
final class SatchelApproach {

    /**
     * Ticks a base of fire must have been engaging a nest from its place before the nest is taken as keeping its head
     * down: the time to find the range and bring a burst onto a gun behind armour (four seconds). UNTUNED.
     */
    static final int SUPPRESSION_SETTLE_TICKS = 120;
    /** Cells between sampled points of the route. */
    static final int ROUTE_STEP_CELLS = 2;
    /**
     * Ticks the company holds its sappers with no further nest answered before it changes approach: the longest any
     * enabling action takes to show, which is what the base of fire is owed (the suppression window) followed by the
     * flight of one strike. If neither has answered a nest in that time, nothing the company has is going to, and a
     * sapper held longer is only a sapper not used.
     */
    static final int PATIENCE_TICKS = FortAssault.SUPPRESS_MAX_TICKS + FortAssault.STRIKE_FLIGHT_TICKS;

    private SatchelApproach() {}

    /** How a live nest on the route is made harmless to a sapper. */
    enum Answer { DEAD, SUPPRESSED, BLINDED, HIDDEN }

    /** What the company can do about an open nest, cheapest first: the cost is what it spends of the company's means. */
    enum Enabler {
        /** A base-of-fire squad is put against that nest: it costs a squad's walk and nothing consumed. */
        ENGAGE(1),
        /** A grenade is thrown on the line: it costs a charge of smoke and puts a thrower near the route. */
        SMOKE(2),
        /** The side's air is asked: it costs an airframe's sortie and a long flight. */
        STRIKE(3);

        final int cost;

        Enabler(int cost) {
            this.cost = cost;
        }
    }

    /** Whether the straight line between two cells crosses only open ground. */
    interface Sight {
        boolean clear(int x0, int y0, int x1, int y1);
    }

    /** A cloud of smoke standing until a tick. */
    record Cloud(float x, float y, float radius, int untilTick) { }

    /** One nest and how it is answered, or {@code null} when it is open. */
    record Reading(int nest, Answer answer) {
        boolean open() {
            return answer == null;
        }
    }

    /** One action the plan asks for. */
    record Move(int nest, Enabler enabler) { }

    enum Verdict { GO, ENABLING, HOLD }

    /**
     * @param moves       actions to begin now, one per open nest that has an enabler and nothing in motion
     * @param pending     open nests that already have an enabler in motion
     * @param unservable  open nests nothing the company has can answer
     */
    record Plan(Verdict verdict, List<Move> moves, List<Integer> pending, List<Integer> unservable) {
        Plan {
            moves = List.copyOf(moves);
            pending = List.copyOf(pending);
            unservable = List.copyOf(unservable);
        }
    }

    /** What the company has, per nest: whether an enabler could be begun now, and whether one is already in motion. */
    interface Resources {
        boolean can(Enabler enabler, int nest);

        boolean pending(Enabler enabler, int nest);
    }

    /** Cells along the straight line from one cell to another, every {@link #ROUTE_STEP_CELLS}, the last always included. */
    static List<int[]> route(int fromX, int fromY, int toX, int toY) {
        List<int[]> cells = new ArrayList<>();
        double length = Math.hypot(toX - fromX, toY - fromY);
        int steps = Math.max(1, (int) Math.ceil(length / ROUTE_STEP_CELLS));
        for (int i = 1; i <= steps; i++) {
            int x = (int) Math.round(fromX + (toX - fromX) * (i / (double) steps));
            int y = (int) Math.round(fromY + (toY - fromY) * (i / (double) steps));
            cells.add(new int[] {x, y});
        }
        return cells;
    }

    /** Cells of a walked path (x,y pairs, as the pathfinder returns it), every {@link #ROUTE_STEP_CELLS}, the last always included. */
    static List<int[]> routeOf(int[] path) {
        List<int[]> cells = new ArrayList<>();
        int count = path.length / 2;
        for (int i = 0; i < count; i += ROUTE_STEP_CELLS) cells.add(new int[] {path[i * 2], path[i * 2 + 1]});
        if (count > 0 && (count - 1) % ROUTE_STEP_CELLS != 0) cells.add(new int[] {path[(count - 1) * 2], path[(count - 1) * 2 + 1]});
        return cells;
    }

    /** The cells of a route that a nest reaches and sees: where a sapper would be under its gun. */
    static List<int[]> exposed(int[] nest, List<int[]> route, double reach, Sight sight) {
        List<int[]> out = new ArrayList<>();
        for (int[] cell : route) {
            if (Math.hypot(cell[0] - nest[0], cell[1] - nest[1]) <= reach
                    && sight.clear(cell[0], cell[1], nest[0], nest[1])) out.add(cell);
        }
        return out;
    }

    /**
     * Whether every exposed cell has a standing cloud on the line from the nest to it: the cloud's centre within its
     * radius of the segment, and standing for at least {@code minLife} more ticks (the time the walk takes: a cloud that thins out
     * under the sappers screens nothing). False when there is nothing exposed (that is HIDDEN, not blinded) or no cloud.
     */
    static boolean blinded(int[] nest, List<int[]> exposed, List<Cloud> clouds, int tick, int minLife) {
        if (exposed.isEmpty()) return false;
        SmokePartition partition = new SmokePartition(clouds);
        for (int[] cell : exposed) {
            if (!partition.cuts(nest, cell, tick, minLife)) return false;
        }
        return true;
    }

    /** How far from the route, toward the nest, a cloud is put: close enough for a grenadier at the route to reach it. */
    static final double SCREEN_OFFSET_CELLS = 4;

    /**
     * Where to put clouds so that every exposed cell has one on its line to the nest: for each cell not yet screened, a point
     * {@link #SCREEN_OFFSET_CELLS} from it toward the nest (never past halfway), then every cell whose line that cloud covers is
     * done. Own-force ground between the nest and the route; nothing of the nest but its cell is read.
     */
    static List<int[]> screenPoints(int[] nest, List<int[]> exposed, double cloudRadius) {
        List<int[]> points = new ArrayList<>();
        boolean[] covered = new boolean[exposed.size()];
        for (int i = 0; i < exposed.size(); i++) {
            if (covered[i]) continue;
            int[] cell = exposed.get(i);
            double dx = nest[0] - cell[0];
            double dy = nest[1] - cell[1];
            double d = Math.hypot(dx, dy);
            double t = d == 0 ? 0 : Math.min(SCREEN_OFFSET_CELLS, d * 0.5) / d;
            int px = (int) Math.round(cell[0] + dx * t);
            int py = (int) Math.round(cell[1] + dy * t);
            points.add(new int[] {px, py});
            for (int j = i; j < exposed.size(); j++) {
                if (SmokePartition.distanceToSegment(px + 0.5, py + 0.5, nest[0] + 0.5, nest[1] + 0.5, exposed.get(j)[0] + 0.5, exposed.get(j)[1] + 0.5)
                        <= cloudRadius) covered[j] = true;
            }
        }
        return points;
    }

    /** What a screen would cost the company that lays it: the other edge of the partition. */
    enum Refusal {
        /** It would cut the sight between a base of fire and the nest it is suppressing, un-answering that nest. */
        BLINDS_BASE_OF_FIRE,
        /** It would leave the sappers alone beyond it, cut from every element that could fire for them, with a suppressed nest still seeing the wall. */
        STRANDS_SAPPERS
    }

    /** A friendly element whose fire an answered nest rests on: the nest it is suppressing, and where it fires from. */
    record Reliance(int nest, int[] nestCell, int[] from) { }

    /**
     * Whether a screen may be laid, weighing both edges of it (`ai-nouns.md`, "Smoke screens partition the field"). The
     * screen blinds the nest it is laid against, but smoke blocks sight for everyone, so it is refused when it would:
     * <ul>
     *   <li>cut the line between a base of fire and a <em>different</em> nest it is relied on to keep suppressed, which would
     *       open that nest again ({@link Refusal#BLINDS_BASE_OF_FIRE}); or</li>
     *   <li>leave the place a sapper must reach cut off from every element of the base of fire, while some nest the company
     *       is holding suppressed still reaches and sees that place ({@link Refusal#STRANDS_SAPPERS}).</li>
     * </ul>
     * Only cuts the proposal <em>makes</em> count: a line the standing clouds already cut is not the proposal's doing. Returns
     * {@code null} when the screen costs nothing the company depends on.
     *
     * @param forNest    the nest the screen is for (its own suppression, if any, is not relied on: the screen replaces it)
     * @param standing   the clouds already on the field
     * @param proposed   the clouds the screen would stand, with the life they will hold
     * @param reliances  the suppressions the company's answers rest on
     * @param support    where the base of fire stands
     * @param places     the places the sappers must reach
     * @param reach      how far a nest sees a place
     * @param minLife    the ticks the screen is wanted for; a cloud holding less is not counted as cutting anything
     */
    static Refusal weigh(int forNest, List<Cloud> standing, List<Cloud> proposed, List<Reliance> reliances,
                         List<int[]> support, List<int[]> places, double reach, Sight sight, int tick, int minLife) {
        SmokePartition before = new SmokePartition(standing);
        SmokePartition after = before.with(proposed);
        for (Reliance reliance : reliances) {
            if (reliance.nest() == forNest) continue;
            if (after.cuts(reliance.from(), reliance.nestCell(), tick, minLife)
                    && !before.cuts(reliance.from(), reliance.nestCell(), tick, minLife)) return Refusal.BLINDS_BASE_OF_FIRE;
        }
        for (int[] place : places) {
            if (!after.isolates(support, place, tick, minLife) || before.isolates(support, place, tick, minLife)) continue;
            for (Reliance reliance : reliances) {
                if (reliance.nest() == forNest) continue;
                int[] nest = reliance.nestCell();
                if (Math.hypot(place[0] - nest[0], place[1] - nest[1]) <= reach && sight.clear(place[0], place[1], nest[0], nest[1])) {
                    return Refusal.STRANDS_SAPPERS;
                }
            }
        }
        return null;
    }

    /**
     * How one nest stands against the route, in order: believed dead, out of sight of every part of the route it reaches,
     * screened by smoke from every such part, engaged by a settled base of fire, else open.
     *
     * @param live      whether the nest is believed alive
     * @param engaged   whether a friendly element in position against this nest has been engaging it for the settle time
     */
    static Reading read(int nest, int[] cell, boolean live, List<int[]> route, double reach, Sight sight, boolean engaged,
                        List<Cloud> clouds, int tick, int minLife) {
        if (!live) return new Reading(nest, Answer.DEAD);
        List<int[]> exposed = exposed(cell, route, reach, sight);
        if (exposed.isEmpty()) return new Reading(nest, Answer.HIDDEN);
        if (blinded(cell, exposed, clouds, tick, minLife)) return new Reading(nest, Answer.BLINDED);
        if (engaged) return new Reading(nest, Answer.SUPPRESSED);
        return new Reading(nest, null);
    }

    /** Nests answered in any way. */
    static int answered(List<Reading> readings) {
        int n = 0;
        for (Reading reading : readings) if (!reading.open()) n++;
        return n;
    }

    /**
     * The plan for a set of readings: nothing to do when none is open; otherwise, per open nest, wait on an enabler already
     * in motion, else begin the cheapest one available, else count the nest as one nothing answers.
     */
    static Plan plan(List<Reading> readings, Resources resources) {
        List<Move> moves = new ArrayList<>();
        List<Integer> pending = new ArrayList<>();
        List<Integer> unservable = new ArrayList<>();
        boolean open = false;
        for (Reading reading : readings) {
            if (!reading.open()) continue;
            open = true;
            boolean inMotion = false;
            for (Enabler enabler : Enabler.values()) inMotion |= resources.pending(enabler, reading.nest());
            if (inMotion) {
                pending.add(reading.nest());
                continue;
            }
            Enabler chosen = null;
            for (Enabler enabler : Enabler.values()) {
                if (resources.can(enabler, reading.nest()) && (chosen == null || enabler.cost < chosen.cost)) chosen = enabler;
            }
            if (chosen == null) unservable.add(reading.nest());
            else moves.add(new Move(reading.nest(), chosen));
        }
        Verdict verdict = !open ? Verdict.GO : moves.isEmpty() && pending.isEmpty() ? Verdict.HOLD : Verdict.ENABLING;
        return new Plan(verdict, moves, pending, unservable);
    }

    /**
     * Whether the company has waited out its patience: no further nest answered since {@code sinceTick}, the tick it began to
     * hold or last made progress.
     */
    static boolean patienceSpent(int sinceTick, int tick) {
        return sinceTick >= 0 && tick - sinceTick >= PATIENCE_TICKS;
    }
}
