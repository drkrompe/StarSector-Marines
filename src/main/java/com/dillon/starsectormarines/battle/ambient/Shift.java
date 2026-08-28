package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One role's work across one or more sites, and the loop each member of it
 * walks.
 *
 * <p>A shift is the join between a role and a map. The role says which jobs are
 * its own and in what order it comes round to them; the sites say where those
 * jobs actually are. Nothing here is authored per map: hand a shift the places
 * somebody is based and the work the generator published, and it produces a
 * route.
 *
 * <p><b>A shift is not a room.</b> That was the first shape and it was wrong in
 * a way that only showed once every room was fitted: a marine's four jobs live
 * in three different compartments, so a per-room shift gave each of them as a
 * separate posting — a marine who slept and checked their kit and never ate, or
 * one who stood on a firing line all watch. What somebody does is a fact about
 * them, and where they do it is a fact about the ship.
 *
 * <p>The order is the role's, and it is a rotation rather than a priority. A
 * route that always ran to the nearest free job would bunch every technician at
 * one end of a bay. Members start on different jobs and at different phases so a
 * watch coming on spreads out instead of queueing, and the shift takes on only
 * as many people as the scarcest job <em>where it is posted</em> can sustain —
 * a bay with eight berths and one terminal cannot occupy eight technicians on a
 * rotation that includes the terminal.
 *
 * <p>Routes name claim <b>groups</b> rather than coordinates, so two people
 * never work the same bench and a third waits for one to come free. The
 * coordinates carried on each stop are the deterministic fallback a pose-only
 * host samples; they are chosen per member so that even a host which ignores
 * claims does not stack everybody on one cell.
 */
public final class Shift {

    /** Cells a second an ambient walker covers. Unhurried: this is a shift, not a sortie. */
    public static final float WALK_SPEED = 0.72f;

    /** How near an armed stranger has to be before somebody stops working. */
    private static final float THREAT_RADIUS = 14f;

    /**
     * How much longer a walk between two sites is than the straight line between
     * them.
     *
     * <p>The route clock budgets travel from the distance between stops, and a
     * deck is a spine with rooms hung off it — so the actual walk from a berth
     * forward to a mess amidships is nothing like the line drawn between them.
     * Budget the straight line and the schedule moves somebody on to their next
     * job before they have arrived at this one, and a shift that spans the ship
     * never dwells anywhere at all. So a spanning shift is scheduled at a slower
     * assumed pace, which is the same thing as allowing for the corridor.
     */
    private static final float CORRIDOR_DETOUR = 1.7f;

    /** How far apart in the loop two consecutive members start. */
    private static final float PHASE_STEP = 1.7f;

    private final CrewRole role;
    private final JobSite base;
    private final List<JobSite> sites;
    private final AmbientThreatPolicy threatPolicy;
    private final Map<Affordance, List<Placed>> byJob;
    private final List<Affordance> order;
    private final boolean spans;

    /** One job, and the site whose claim group it belongs to. */
    private record Placed(FixtureTask task, JobSite site) {}

    private Shift(CrewRole role, List<JobSite> sites, AmbientThreatPolicy threatPolicy,
                  Map<Affordance, List<Placed>> byJob, List<Affordance> order, boolean spans) {
        this.role = role;
        this.base = sites.get(0);
        this.sites = sites;
        this.threatPolicy = threatPolicy;
        this.byJob = byJob;
        this.order = order;
        this.spans = spans;
    }

    /**
     * The shift somebody posted to one site would work, reaching wherever else
     * on the map their other jobs are.
     *
     * <p>Nearest rather than authored, and found by asking what each place
     * actually publishes rather than by a table of which purpose serves which
     * job. A table would be a second place to keep the generator's decisions,
     * and it would be wrong the first time a room started affording something
     * new.
     *
     * <p>This is the entry point a host or a mission wants. Deriving the sites
     * is not a fact about ships: a civilian posted to a house on a town map
     * eats and works and sleeps in the same spread of places, for the same
     * reason, and the selection would only have to be written a second time.
     *
     * @param posted where this role is stationed, which names the shift
     * @param candidates every site on the map, including {@code posted}
     */
    public static Shift postedAt(CrewRole role, JobSite posted,
                                 List<? extends JobSite> candidates,
                                 List<FixtureTask> authored, boolean[] berthed,
                                 AmbientThreatPolicy threatPolicy) {
        if (role == null) throw new IllegalArgumentException("a shift needs a role");
        if (posted == null) throw new IllegalArgumentException("a shift needs a posting");
        List<JobSite> sites = new ArrayList<>();
        sites.add(posted);
        if (candidates != null) {
            for (Affordance job : role.jobs()) {
                if (offers(posted, role, job, authored, berthed)) continue;
                JobSite nearest = null;
                long best = Long.MAX_VALUE;
                for (JobSite candidate : candidates) {
                    if (candidate.id() == posted.id()) continue;
                    if (!offers(candidate, role, job, authored, berthed)) continue;
                    long span = distanceSquared(posted, candidate);
                    if (span < best) {
                        best = span;
                        nearest = candidate;
                    }
                }
                if (nearest != null && !sites.contains(nearest)) sites.add(nearest);
            }
        }
        return of(role, sites, authored, berthed, threatPolicy);
    }

    /**
     * Whether this role's people live here, as opposed to merely having
     * business here.
     *
     * <p>Somewhere a role is based is its quarters or its work. Every other
     * place its rotation reaches - the mess it eats in, the lane it shoots on -
     * is somewhere it goes, and reading those as billets would invent a
     * population: a galley with eight tables would become quarters for eight
     * marines who sleep nowhere and are on no roster.
     *
     * <p>A role with no work aboard therefore has exactly one kind of billet,
     * its berthing, which is the right shape for a marine complement carried as
     * passengers on somebody else's ship.
     *
     * <p>This bounds a <em>complement</em>, not a posting. {@link #postedAt}
     * will still build the shift a role would work anywhere it is deliberately
     * stationed - a range detail is a real thing to order - and a caller
     * deciding who lives aboard consults this first.
     */
    public static boolean basedAt(CrewRole role, JobSite posted,
                                  List<FixtureTask> authored, boolean[] berthed) {
        if (posted.purpose() == role.quarters()) return true;
        for (Affordance job : role.onWatch()) {
            if (job.duty() && offers(posted, role, job, authored, berthed)) return true;
        }
        return false;
    }

    /** Whether this site has a live job of that kind, and it is this role's. */
    private static boolean offers(JobSite site, CrewRole role, Affordance job,
                                  List<FixtureTask> authored, boolean[] berthed) {
        if (!JobBoard.belongsTo(role, site, job)) return false;
        for (FixtureTask task : JobBoard.live(authored, site, berthed)) {
            if (task.affordance() == job) return true;
        }
        return false;
    }

    private static long distanceSquared(JobSite from, JobSite to) {
        long dx = (long) from.centreX() - to.centreX();
        long dy = (long) from.centreY() - to.centreY();
        return dx * dx + dy * dy;
    }

    /**
     * The shift a role can work across these sites.
     *
     * @param sites where this role is based and wherever else its jobs are; the
     *     first is what names the shift, so it should be the place somebody is
     *     posted to rather than one they merely pass through
     * @param authored every job the generator published on this map
     * @param berthed whether the berth at each index holds a machine
     */
    public static Shift of(CrewRole role, List<? extends JobSite> sites,
                           List<FixtureTask> authored, boolean[] berthed,
                           AmbientThreatPolicy threatPolicy) {
        if (role == null) throw new IllegalArgumentException("a shift needs a role");
        if (sites == null || sites.isEmpty()) {
            throw new IllegalArgumentException("a shift needs somewhere to work");
        }
        Map<Affordance, List<Placed>> byJob = new LinkedHashMap<>();
        for (JobSite site : sites) {
            for (FixtureTask task : JobBoard.live(authored, site, berthed)) {
                if (!JobBoard.belongsTo(role, site, task.affordance())) continue;
                byJob.computeIfAbsent(task.affordance(), key -> new ArrayList<>())
                        .add(new Placed(task, site));
            }
        }
        List<Affordance> order = new ArrayList<>();
        for (Affordance job : role.jobs()) {
            if (byJob.containsKey(job)) order.add(job);
        }
        return new Shift(role, List.copyOf(sites), threatPolicy, byJob, order, spansSites(byJob));
    }

    /** Whether more than one site contributes, which is what the clock has to allow for. */
    private static boolean spansSites(Map<Affordance, List<Placed>> byJob) {
        int first = Integer.MIN_VALUE;
        for (List<Placed> places : byJob.values()) {
            for (Placed placed : places) {
                if (first == Integer.MIN_VALUE) first = placed.site().id();
                else if (placed.site().id() != first) return true;
            }
        }
        return false;
    }

    /**
     * Where this shift is posted, which is the first site it was built from.
     *
     * <p>Not "wherever its first job happens to be". A marine's rotation begins
     * with the mess only because that is where the role's job list begins, and a
     * shift that took its identity from that would be a barracks watch named
     * after a room three compartments away.
     */
    public JobSite base() {
        return base;
    }

    /** Everywhere this shift works, the posting first. */
    public List<JobSite> sites() {
        return sites;
    }

    /** Whether this shift reaches more than one site. */
    public boolean spansSites() {
        return spans;
    }

    /** The jobs this shift comes round to, in order. */
    public List<Affordance> jobs() {
        return List.copyOf(order);
    }

    /**
     * How many people this shift holds at once.
     *
     * <p>Two different questions, decided by what the posting is. A berthing
     * holds the people who sleep in it, so its capacity is its <b>bunks</b>: a
     * bunkroom with eight racks and two lockers quarters eight marines who
     * sometimes wait for a locker, and reading it as quartering two would empty
     * a ship of three quarters of her complement to avoid a queue nobody would
     * ever notice. A workplace holds the people it can keep busy, so its
     * capacity is its <b>scarcest job</b>: a bay with eight berths and one
     * terminal cannot occupy eight technicians on a rotation that includes the
     * terminal, and pretending otherwise puts seven of them in a line.
     *
     * <p>Either way the bound is read <em>at the site posted to</em>, never the
     * scarcest anywhere the shift reaches. The ship has two firing lanes and
     * twenty-seven barracks, so counting practice against a berthing would cap
     * every one of them at two marines and then send all fifty-four at the same
     * two lanes. A shared job is contended by everyone who reaches it, the claim
     * service already arbitrates that, and a member who finds the range full
     * waits out that stop and comes round again.
     */
    public int capacity() {
        if (CrewRole.isBerthing(base.purpose())) return atBase(Affordance.REST);
        int fewest = Integer.MAX_VALUE;
        for (Affordance job : order) {
            int here = atBase(job);
            if (here > 0) fewest = Math.min(fewest, here);
        }
        return fewest == Integer.MAX_VALUE ? 0 : fewest;
    }

    /** How many places for this job stand in the compartment the shift is posted to. */
    private int atBase(Affordance job) {
        List<Placed> places = byJob.get(job);
        if (places == null) return 0;
        int here = 0;
        for (Placed placed : places) {
            if (placed.site().id() == base.id()) here++;
        }
        return here;
    }

    /**
     * One member's route, or null when there is no work here for this role.
     *
     * @param index which member of the shift this is, which sets both their
     *     phase and which job they start on
     */
    public AmbientTaskRoute member(int index) {
        if (order.isEmpty()) return null;
        int start = Math.floorMod(index, order.size());
        List<AmbientTaskRoute.Stop> stops = new ArrayList<>(order.size());
        for (int step = 0; step < order.size(); step++) {
            Affordance job = order.get((start + step) % order.size());
            List<Placed> places = byJob.get(job);
            Placed placed = places.get(Math.floorMod(index, places.size()));
            FixtureTask task = placed.task();
            stops.add(new AmbientTaskRoute.Stop(
                    task.cellX() + 0.5f, task.cellY() + 0.5f,
                    CrewRole.dwellFor(job), CrewRole.activityFor(job),
                    task.fixtureX() + 0.5f, task.fixtureY() + 0.5f,
                    JobBoard.group(placed.site().id(), job)));
        }
        String id = role.name().toLowerCase(Locale.ROOT) + "-" + base.id() + "-" + index;
        return new AmbientTaskRoute(id, index * PHASE_STEP,
                spans ? WALK_SPEED / CORRIDOR_DETOUR : WALK_SPEED,
                THREAT_RADIUS, threatPolicy, stops);
    }
}
