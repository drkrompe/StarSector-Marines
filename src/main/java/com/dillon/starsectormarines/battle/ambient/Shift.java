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
 * <p>How long the loop takes is not a fact a shift knows or should. It names the
 * jobs and where they are; how long somebody spends walking between two of them
 * is what their legs and the pathfinder settle on the day, and the dwell begins
 * when they arrive. A shift that budgeted the walk itself would be guessing at
 * a corridor it cannot see, and the guess was wrong in the expensive direction:
 * scheduled for an unhurried straight line across a whole ship, a crew that
 * actually walks several times faster spends its life standing at the fixture
 * waiting for the timetable to catch up.
 *
 * <p>Routes name claim <b>groups</b> rather than coordinates, so two people
 * never work the same bench and a third waits for one to come free. The
 * coordinates carried on each stop are the deterministic fallback a pose-only
 * host samples; they are chosen per member so that even a host which ignores
 * claims does not stack everybody on one cell.
 */
public final class Shift {

    /**
     * Cells a second an ambient walker is drawn covering by the pose sampler.
     *
     * <p>Not the pace of a live shift, which is the actor's own movement through
     * the ordinary pathfinder. This is what a host that samples a route for a
     * pose without ticking anything assumes about the walk between two stops.
     */
    public static final float WALK_SPEED = 0.72f;

    /** How near an armed stranger has to be before somebody stops working. */
    private static final float THREAT_RADIUS = 14f;

    /** How far apart in the loop two consecutive members start. */
    private static final float PHASE_STEP = 1.7f;

    /**
     * How many places a circuit job reaches for.
     *
     * <p>A bound rather than a target: a ship with three machinery spaces has a
     * three-stop repair round, and that is correct. What it stops is a patrol of
     * a hundred and nineteen compartments — a rotation nobody completes, whose
     * walker is permanently in a passage, which is the shape this whole model
     * exists to avoid, arrived at by being too thorough.
     */
    private static final int CIRCUIT_STOPS = 6;

    private final CrewRole role;
    private final JobSite base;
    private final List<JobSite> sites;
    private final AmbientThreatPolicy threatPolicy;
    private final Map<Affordance, List<Placed>> byJob;
    private final List<Affordance> order;
    private final boolean spans;

    /** One job, and the site whose claim group it belongs to. */
    private record Placed(FixtureTask task, JobSite site) {}

    /**
     * A circuit's legs: one stop per <em>site</em>, not per fixture.
     *
     * <p>A claim group is a site and an affordance together, so four defects in
     * one machinery space are four places in one group. Emitting a stop for each
     * of them puts four consecutive stops on the same group, and the claim
     * service hands back the claim already held for it — so the walker
     * &quot;moves&quot; to a destination they are standing on, arrives at once,
     * and works the same defect four times over. A round is a round of the
     * ship, and the compartment is the unit of it.
     *
     * <p>Which defect within a compartment is still the member's own, so two
     * technicians sent to the same space do not both make for the same machine.
     * The claim service would separate them anyway; starting them apart means it
     * does not have to.
     */
    private static List<Placed> oneLegPerSite(List<Placed> places, int index) {
        Map<Integer, List<Placed>> bySite = new LinkedHashMap<>();
        for (Placed placed : places) {
            bySite.computeIfAbsent(placed.site().id(), key -> new ArrayList<>()).add(placed);
        }
        List<Placed> legs = new ArrayList<>(bySite.size());
        for (List<Placed> atSite : bySite.values()) {
            legs.add(atSite.get(Math.floorMod(index, atSite.size())));
        }
        return legs;
    }

    /**
     * A round of the machines: one stop per berth, not per servicing cell.
     *
     * <p>Two flanks of one aircraft are two places to stand and one turnaround,
     * so emitting both would spend half a rotation walking round the same hull.
     * Which flank is the member's own, for the reason {@link #oneLegPerSite}
     * gives: two technicians sent to the same machine should not make for the
     * same side of it.
     */
    private static List<Placed> oneLegPerBerth(List<Placed> places, int index) {
        Map<Integer, List<Placed>> byBerth = new LinkedHashMap<>();
        for (Placed placed : places) {
            byBerth.computeIfAbsent(placed.task().berth(), key -> new ArrayList<>())
                    .add(placed);
        }
        List<Placed> legs = new ArrayList<>(byBerth.size());
        for (List<Placed> atBerth : byBerth.values()) {
            legs.add(atBerth.get(Math.floorMod(index, atBerth.size())));
        }
        return legs;
    }

    /** Whether this job is work on machines rather than at benches. */
    private static boolean onMachines(List<Placed> places) {
        for (Placed placed : places) {
            if (placed.task().berth() != FixtureTask.NO_BERTH) return true;
        }
        return false;
    }

    /** One authored job as a stop on somebody's route. */
    private static AmbientTaskRoute.Stop stopAt(Placed placed, Affordance job) {
        FixtureTask task = placed.task();
        return new AmbientTaskRoute.Stop(
                task.cellX() + 0.5f, task.cellY() + 0.5f,
                CrewRole.dwellFor(job), CrewRole.activityFor(job),
                task.fixtureX() + 0.5f, task.fixtureY() + 0.5f,
                JobBoard.group(placed.site().id(), job, task.berth()));
    }

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
                // A circuit reaches for several places whether or not the
                // posting itself offers the job: a round that stopped at the
                // door of the room it started in is not a round.
                int wanted = CrewRole.isCircuit(job) ? CIRCUIT_STOPS
                        : offers(posted, role, job, authored, berthed) ? 0 : 1;
                addNearest(sites, candidates, posted, role, job,
                        authored, berthed, wanted);
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
     * <p><b>A role is based where its trade is,</b> and only there — see
     * {@link CrewRole#trade}. Every place its rotation reaches for a secondary
     * job is somewhere it goes. Reading those as postings makes one room a
     * billet for every trade that could lend a hand in it: a spares cage
     * publishing stowage became a posting for the storekeeper, the technician,
     * the machinist, the medic, the armourer and the engine watch at once, and
     * a ship with nineteen such pockets carried a thousand engineers.
     *
     * <p><b>Quarters are a billet only for a role with no work.</b> A watch is
     * posted where its work is and berthed wherever there is a rack; reading a
     * bunkroom as a posting for everybody who sleeps in it counts the ship's
     * complement off her furniture instead of off her work, and counts it once
     * per role. A hull with seven bunkrooms and seven working trades came out
     * carrying five hundred hands - each trade filling every rack - on a ship
     * whose whole company is sixty. What each of those roles then does aboard is
     * bounded by what there is to do: one vehicle bay supports the technicians
     * one bay can occupy, and a ship with no sick berth carries no medic.
     *
     * <p>A role with no work aboard therefore has exactly one kind of billet,
     * its berthing, which is the right shape for a marine complement carried as
     * passengers on somebody else's ship. Sleeping and washing are still
     * <em>reached</em> either way: {@link #postedAt} sends every watch to the
     * nearest place offering its off-watch jobs, so the ratings' bunkrooms fill
     * up with ratings who are billeted at their work.
     *
     * <p>This bounds a <em>complement</em>, not a posting. {@link #postedAt}
     * will still build the shift a role would work anywhere it is deliberately
     * stationed - a range detail is a real thing to order - and a caller
     * deciding who lives aboard consults this first.
     */
    public static boolean basedAt(CrewRole role, JobSite posted,
                                  List<FixtureTask> authored, boolean[] berthed) {
        Affordance trade = role.trade();
        if (trade != null) return offers(posted, role, trade, authored, berthed);
        return posted.purpose() == role.quarters();
    }

    /**
     * Take on the nearest {@code wanted} sites offering this job that the shift
     * has not already got.
     *
     * <p>Ordered by distance from the <em>posting</em> rather than from the last
     * site taken, because the question is which places somebody stationed here
     * would actually reach. Chaining nearest-to-the-last would let a round walk
     * itself across the hull one compartment at a time and never come back.
     */
    private static void addNearest(List<JobSite> sites, List<? extends JobSite> candidates,
                                   JobSite posted, CrewRole role, Affordance job,
                                   List<FixtureTask> authored, boolean[] berthed,
                                   int wanted) {
        for (int taken = 0; taken < wanted; taken++) {
            JobSite nearest = null;
            long best = Long.MAX_VALUE;
            for (JobSite candidate : candidates) {
                if (candidate.id() == posted.id() || sites.contains(candidate)) continue;
                if (!offers(candidate, role, job, authored, berthed)) continue;
                long span = distanceSquared(posted, candidate);
                if (span < best) {
                    best = span;
                    nearest = candidate;
                }
            }
            if (nearest == null) return;
            sites.add(nearest);
        }
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
            // A circuit is contended by the whole ship and satisfied anywhere on
            // it, so counting the one rounds point in this room would cap the
            // compartment at a single hand.
            if (CrewRole.isCircuit(job)) continue;
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
            if (CrewRole.isCircuit(job) || onMachines(places)) {
                // Every place it reaches, in one turn of the rotation, offset
                // per member so two walkers on the same round are not in step.
                //
                // Work on machines takes the same treatment for a related but
                // distinct reason. It is not a circuit — it happens in one room
                // — but that room holds several machines, and each of them is
                // its own piece of work waiting its turn. Given one stop like
                // an ordinary bench job, a technician takes the nearest stand
                // and services it for the whole battle: the claim service
                // answers every later request with the claim already held, so
                // the walk back is to the machine they never left, and nothing
                // else in the room is ever touched.
                List<Placed> legs = CrewRole.isCircuit(job)
                        ? oneLegPerSite(places, index)
                        : oneLegPerBerth(places, index);
                for (int leg = 0; leg < legs.size(); leg++) {
                    stops.add(stopAt(legs.get(Math.floorMod(index + leg, legs.size())), job));
                }
            } else {
                stops.add(stopAt(places.get(Math.floorMod(index, places.size())), job));
            }
        }
        String id = role.name().toLowerCase(Locale.ROOT) + "-" + base.id() + "-" + index;
        return new AmbientTaskRoute(id, index * PHASE_STEP, WALK_SPEED,
                THREAT_RADIUS, threatPolicy, stops);
    }
}
