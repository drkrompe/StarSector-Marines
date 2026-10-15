package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.CompanyState;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The muster and assault phases of a phased assault (`phased-assault.md`): when the next objective is one single
 * squads cannot take, the commander forms an <em>assault company</em> at a rally point, judges its readiness against
 * what it believes is in front of it, commits it as one body, and keeps it together on the approach.
 *
 * <p>Pure planning over frozen inputs, and symmetric: nothing here knows which side it plans for, so a defender's
 * counter-assault could use the same machinery. The first case is a disclosed fort; the objective and the believed
 * picture of it are the {@link Objective}, so another strong believed position is another objective, not another
 * mechanism.
 *
 * <h2>Phases, per objective</h2>
 * <ul>
 *   <li><b>Foothold.</b> While any company exists, one squad holds the drop zone. Probes elsewhere go on as they
 *       always did; this class never touches a squad it was not given.</li>
 *   <li><b>Muster.</b> Squads are recruited nearest-first to a rally point and ordered to it. The rally point is the
 *       drop zone when the objective is close, else a staging cell short of the objective on the approach, pulled back
 *       toward the drop zone while any believed hostile is near it. The company is {@link #judge ready} when the
 *       strength gathered at the rally point reaches what the objective is believed to take, or when a timeout
 *       passes, so a muster can never wait forever.</li>
 *   <li><b>Assault.</b> The whole company is ordered at the one aspect of the fort that {@link FortTactics} ranks
 *       first ({@link DefenceStructureTactics#planMassed}). A squad that runs well ahead of the company holds until
 *       it comes up, with the same bounds as a command group's rally, so the company reaches the fort as one body.
 *       A company reduced below {@link #BROKEN_FRACTION} of the strength it committed is broken: the attempt is
 *       counted, and the next muster believes the objective takes more.</li>
 * </ul>
 *
 * <h2>Laws</h2>
 * <ul>
 *   <li>Belief only. Readiness reads the objective's disclosed aspects and the hostile contacts the side holds near
 *       it, never the garrison's true size.</li>
 *   <li>Every figure here marked UNTUNED is a first pass to be set from play; none was chosen to hit a target.</li>
 * </ul>
 */
final class AssaultMuster {

    /** Weighted marines a fort is believed to take before any hostile is sighted near it. UNTUNED. */
    static final int FORT_BASE_STRENGTH = 24;
    /** Weighted marines added for each hostile contact believed near the objective. UNTUNED. */
    static final int PER_CONTACT_STRENGTH = 2;
    /** A contact within this many cells of the objective counts as in front of it. UNTUNED. */
    static final int CONTACT_RADIUS_CELLS = 40;
    /** One living member of a machine squad (a mech) counts as this many marines. UNTUNED. */
    static final float MACHINE_MEMBER_WEIGHT = 3f;
    /** Each broken attempt on an objective raises the strength it is believed to take by this fraction. UNTUNED. */
    static final float FAILURE_GROWTH = 0.25f;
    /** Ticks a muster waits from forming before it commits with what it has. UNTUNED. */
    static final int MUSTER_TIMEOUT_TICKS = 4500;
    /**
     * A muster that times out with less than this share of the strength it believes the objective takes stands down
     * rather than commit: three marines at a fort are a gift to its nests. UNTUNED.
     */
    static final float MIN_COMMIT_SHARE = 0.5f;
    /** Ticks a fort is left to the lanes after a company stood down against it. UNTUNED. */
    static final int STAND_DOWN_TICKS = 3000;
    /** Stand-downs per fort before a timed-out muster commits with what it has, so it never waits forever. UNTUNED. */
    static final int STAND_DOWN_LIMIT = 2;
    /** Ticks allowed per cell of walk for a recruit to reach the rally point. UNTUNED. */
    static final int TICKS_PER_CELL = 24;
    /** Extra ticks a recruit is allowed on top of its walk. UNTUNED. */
    static final int ARRIVAL_SLACK_TICKS = 600;
    /**
     * Once every squad the company can get has arrived and it is still short, it waits this many more ticks for
     * anything yet to come before committing with what it has, rather than standing out the whole timeout. UNTUNED.
     */
    static final int STRAGGLER_GRACE_TICKS = 900;
    /**
     * A recruit that has not closed on the rally point by at least {@link #STALL_CLOSE_CELLS} in this many ticks is not
     * coming (held in a fight, lost, on a path that does not close) and is released rather than waited for; a squad walks
     * about 0.04 cells a tick, so this is some fifty cells of walking. UNTUNED.
     */
    static final int STALL_TICKS = 1200;
    /** The ground a recruit must gain on the rally point within {@link #STALL_TICKS} to count as still coming. UNTUNED. */
    static final float STALL_CLOSE_CELLS = 3f;
    /** Ticks a released recruit is left to the lanes before any company may take it again. UNTUNED. */
    static final int RELEASE_BAR_TICKS = 3000;
    /** A squad within this many cells of the rally point has arrived. UNTUNED. */
    static final int RALLY_RADIUS_CELLS = 10;
    /**
     * How far short of the objective the staging cell lies when the objective is far from the drop zone. A fort's
     * outer nests stand about forty cells out from its centre and reach some thirty more, so a staging cell nearer
     * than this is under the works' guns while the company is still only gathering. UNTUNED.
     */
    static final int STAGING_STANDOFF_CELLS = 90;
    /** A staging cell with a believed hostile this close is unsafe and is pulled back. UNTUNED. */
    static final int SAFE_CONTACT_CELLS = 24;
    /** How far a staging cell may slide off its line to find walkable, reachable ground. */
    static final int RALLY_SNAP_CELLS = 12;
    /** Largest share of the side's free squads that companies may hold at once. UNTUNED. */
    static final float COMPANY_SHARE = 0.75f;
    /**
     * The share of each lane's infantry squads that no company recruits: the lanes keep probing and capturing while a
     * company musters, which is the work the first muster pulled squads away from. A flat floor per lane was tried first
     * and shut a small force out of mustering at all, so the floor is a share of what the lane has. UNTUNED.
     */
    static final float LANE_KEEP_SHARE = 0.5f;
    /**
     * The share of what an objective is believed to take that is enough to commit once the side is being flown at and the
     * objective's radar is disclosed: a muster bombed where it stands is losing strength by waiting, and the radar is the
     * thing that stops it. Equal to the share below which a timed-out muster stands down. UNTUNED.
     */
    static final float AIR_RAID_COMMIT_SHARE = 0.5f;
    /** A company left with less than this fraction of the strength it committed is broken. UNTUNED. */
    static final float BROKEN_FRACTION = 0.25f;
    /** Inside this many cells of the objective the company is in the fight and no longer waits for its stragglers. */
    static final int ENGAGE_RING_CELLS = 32;
    /** How far from the drop zone the foothold element may be chosen. UNTUNED. */
    static final int FOOTHOLD_RECRUIT_CELLS = 60;

    /** What a muster's gathered strength says about committing. */
    enum Readiness { GATHERING, READY, TIMED_OUT }

    private enum Stage { MUSTER, ASSAULT }

    /**
     * A strong believed position: a fort the side has been disclosed, with the hostile contacts it believes stand
     * near it.
     *
     * @param cellX            centre of the disclosed aspects
     * @param believedContacts hostile contacts the side's own belief places within {@link #CONTACT_RADIUS_CELLS}
     */
    record Objective(int fortId, Family family, int cellX, int cellY, List<DefenceStructureFact> aspects,
                     int believedContacts, FortFrame works) {
        /** An objective whose works are not known, which is assaulted without a plan. */
        Objective(int fortId, Family family, int cellX, int cellY, List<DefenceStructureFact> aspects,
                  int believedContacts) {
            this(fortId, family, cellX, cellY, aspects, believedContacts, null);
        }
    }

    /**
     * A squad the commander may put in a company.
     *
     * @param inContact engaged: under fire, broken, or with a member able to engage a contact. Not recruited, and not held back on the approach. A belief in a distant hostile is not contact
     * @param track     the lane the squad belongs to, or {@code -1} when it has none (a machine squad, a test): each lane keeps a floor of squads a company may not recruit below
     * @param capabilities what the squad's living members can do to a position, from the kit they carry now
     */
    record Body(int squadId, int anchorX, int anchorY, int alive, boolean machine, boolean demolition,
                boolean inContact, ObjectiveAssignment incumbent, int track, CapabilityProfile capabilities) {
        /** A body whose kit is not itemised: its capabilities are those assumed for a squad of its make-up. */
        Body(int squadId, int anchorX, int anchorY, int alive, boolean machine, boolean demolition,
             boolean inContact, ObjectiveAssignment incumbent, int track) {
            this(squadId, anchorX, anchorY, alive, machine, demolition, inContact, incumbent, track,
                    CapabilityProfile.assumed(machine, demolition, alive));
        }

        Body(int squadId, int anchorX, int anchorY, int alive, boolean machine, boolean demolition,
             boolean inContact, ObjectiveAssignment incumbent) {
            this(squadId, anchorX, anchorY, alive, machine, demolition, inContact, incumbent, -1);
        }
    }

    record Order(ObjectiveAssignment assignment, AssignmentReason reason) { }

    /**
     * A strike a company's plan wants called, for the commander to ask of the air after the plan is committed.
     *
     * @param fortId the objective the company is assaulting
     */
    record StrikeRequest(int fortId, FortAssault.StrikeWish wish) { }

    /**
     * @param orders  by squad id; only squads this class claims appear.
     * @param strikes the strikes the plans wish asked for this pulse
     */
    record Result(Map<Integer, Order> orders, List<CompanyState> companies, List<String> notes, List<StrikeRequest> strikes) {
        Result(Map<Integer, Order> orders, List<CompanyState> companies, List<String> notes) {
            this(orders, companies, notes, List.of());
        }
    }

    /** How much further than the nearest squad an observer may be and still be the one a company takes. */
    private static final double OBSERVER_DETOUR_CELLS = 40.0;

    private static final class Company {
        final int fortId;
        Stage stage = Stage.MUSTER;
        final int formedTick;
        int stageTick;
        int timeoutTick;
        final Set<Integer> members = new TreeSet<>();
        int rallyX;
        int rallyY;
        float strengthAtCommit;
        String reason = "";
        int waitStarted = -1;
        boolean boundReleased;
        /** The tick the company first had everything it can get at the rally and still fell short, or -1. */
        int fullSince = -1;
        /** Reinforcement (a lance called down) is due by this tick: no grace cut-short and no timeout before it. */
        int expectUntil = -1;
        /** The stamp's assault plan being executed, once committed against a fort whose works are known. */
        FortAssault.Execution execution;
        /** Per recruit still walking in: its closest approach to the rally so far and the tick it got there. */
        final Map<Integer, double[]> progress = new HashMap<>();

        int graceFrom(int tick) {
            if (fullSince < 0) fullSince = tick;
            return fullSince;
        }

        Company(int fortId, int tick) {
            this.fortId = fortId;
            this.formedTick = tick;
            this.stageTick = tick;
            this.timeoutTick = tick + MUSTER_TIMEOUT_TICKS;
        }
    }

    private final Map<Integer, Company> companies = new TreeMap<>();
    /** Broken attempts per objective: the side's memory that the objective took more than it believed. */
    private final Map<Integer, Integer> failures = new HashMap<>();
    private int footholdSquad = -1;
    /** Squads released from a muster for not coming up, and the tick they may be recruited again. */
    private final Map<Integer, Integer> barred = new HashMap<>();
    /** The tick of the pulse being planned, for the recruit bar. */
    private int clock;
    /** Times a company for each fort stood down for want of strength, and until when the fort is left to the lanes. */
    private final Map<Integer, Integer> standDowns = new HashMap<>();
    private final Map<Integer, Integer> standDownUntil = new HashMap<>();
    /** What is believed of each fort's nests, kept for as long as the fort is an objective. */
    private final Map<Integer, FortAssault.NestBelief> beliefs = new HashMap<>();
    private final Map<Integer, FortAssault.Works> placed = new HashMap<>();
    private final Map<Integer, FortFrame> placedFrames = new HashMap<>();
    /** What the side's air can do for the plan this pulse, and the strikes the plans have asked for in it. */
    private AirSupportFacts air = AirSupportFacts.NONE;
    private final List<StrikeRequest> strikeRequests = new ArrayList<>();
    /** What each fort's company did with its held close breachers, kept past the company for evidence. */
    private final Map<Integer, FortAssault.SatchelRecord> satchelRecords = new TreeMap<>();

    /** The satchel records by fort, for evidence; the plan never reads them. */
    Map<Integer, FortAssault.SatchelRecord> satchelRecords() {
        return satchelRecords;
    }

    /** The commander's report of what the air said to a strike the plan asked for. */
    void strikeAnswered(int fortId, int nest, boolean calling, int tick) {
        Company company = companies.get(fortId);
        if (company != null && company.execution != null) company.execution.strikeAnswered(nest, calling, tick);
    }

    /**
     * Whether the side believes the nest of fort {@code fortId} standing at {@code (x, y)} destroyed, so that no fire mission
     * is given against it. False for a fort or cell not tracked.
     */
    boolean nestBelievedDead(int fortId, int x, int y) {
        FortAssault.Works works = placed.get(fortId);
        FortAssault.NestBelief belief = beliefs.get(fortId);
        if (works == null || belief == null) return false;
        for (int i = 0; i < works.nests.size(); i++) {
            int[] nest = works.nests.get(i);
            if (nest[0] == x && nest[1] == y) return !belief.live(i);
        }
        return false;
    }

    /** Forgets every company and the foothold element; each squad returns to ordinary command next pulse. */
    void dissolveAll() {
        companies.clear();
        footholdSquad = -1;
    }

    private FortAssault.Works worksOf(Objective objective) {
        if (objective.works() == null) return null;
        if (placedFrames.get(objective.fortId()) != objective.works()) {
            placedFrames.put(objective.fortId(), objective.works());
            FortAssault.Works works = FortAssault.Works.of(objective.works());
            placed.put(objective.fortId(), works);
            beliefs.put(objective.fortId(), new FortAssault.NestBelief(works.nests.size()));
        }
        return placed.get(objective.fortId());
    }

    /**
     * The extra strength the defence in front of a company is believed to add to a fort's baseline: the nests believed
     * live (those covering the way in the company would take at full weight), and the gate or wall to be passed.
     */
    private float defenceStrength(Objective objective, Company company, Map<Integer, Body> byId, int[] from) {
        FortAssault.Defence defence = defenceOf(objective, company == null ? Set.of() : company.members, byId, from);
        return defence == null ? 0f : defence.strength();
    }

    /**
     * The way in a company of these squads would take, judged by what they can do ({@link CapabilityProfile}), with the
     * plan's needs matched against them. Null when the objective's works are not known.
     */
    private FortAssault.Defence defenceOf(Objective objective, Set<Integer> squads, Map<Integer, Body> byId, int[] from) {
        FortAssault.Works works = worksOf(objective);
        if (works == null) return null;
        Map<Integer, CapabilityProfile> profiles = new TreeMap<>();
        for (int id : squads) {
            Body body = byId.get(id);
            if (body != null) profiles.put(id, body.capabilities());
        }
        boolean operator = false;
        for (CapabilityProfile profile : profiles.values()) operator |= profile.has(AssaultCapability.AIR_DIRECTION);
        return FortAssault.assess(works, beliefs.get(objective.fortId()), from[0], from[1], profiles,
                new FortAssault.Strikes(air, operator, clock));
    }

    /** Whether a company that is short of what its plan needs asks for machines: a lance would fill a base of fire or a breach. */
    static boolean machinesWouldHelp(ConquestFrontSnapshot.CompanyState company) {
        for (String gap : company.shortfall()) {
            if (gap.startsWith("base_of_fire") || gap.startsWith("breach")) return true;
        }
        return false;
    }

    /**
     * The commander has called reinforcement for this objective (a mech lance); the muster waits for it until
     * {@code untilTick} rather than cutting its grace short or timing out.
     */
    void expectReinforcement(int fortId, int untilTick) {
        Company company = companies.get(fortId);
        if (company == null) return;
        company.expectUntil = Math.max(company.expectUntil, untilTick);
        company.timeoutTick = Math.max(company.timeoutTick, untilTick);
    }

    boolean active() {
        return !companies.isEmpty();
    }

    /** The strength an objective is believed to take, in marine-equivalents. */
    static int required(Family family, int believedContacts, int failures) {
        return required(family, believedContacts, failures, 0f);
    }

    /** As above, plus the strength the defence in front of the company is believed to add before growth for failures. */
    static int required(Family family, int believedContacts, int failures, float defence) {
        int base = switch (family) {
            case BATTERY_FORT, SHIELD_FORT -> FORT_BASE_STRENGTH;
            case BARE -> FORT_BASE_STRENGTH / 4;
        };
        float believed = base + defence + PER_CONTACT_STRENGTH * Math.max(0, believedContacts);
        return (int) Math.ceil(believed * (1f + FAILURE_GROWTH * Math.max(0, failures)));
    }

    /** What this objective is believed to take now, counting the nests and gates believed in front of the company. */
    private int requiredNow(Objective objective, Company company, Map<Integer, Body> byId, int[] from) {
        return required(objective.family(), objective.believedContacts(), failures.getOrDefault(objective.fortId(), 0),
                defenceStrength(objective, company, byId, from));
    }

    static float weight(int alive, boolean machine) {
        return Math.max(0, alive) * (machine ? MACHINE_MEMBER_WEIGHT : 1f);
    }

    /**
     * Whether the company is ready to commit: the strength at the rally point reaches the strength believed needed,
     * or the muster has run out of time.
     */
    static Readiness judge(float arrivedStrength, int required, int tick, int timeoutTick) {
        if (arrivedStrength > 0f && arrivedStrength >= required) return Readiness.READY;
        if (tick >= timeoutTick) return Readiness.TIMED_OUT;
        return Readiness.GATHERING;
    }

    /**
     * As {@link #judge(float, int, int, int)}, for a side that may be flown at: a muster that is being bombed and has a
     * hostile radar to go for commits at {@link #AIR_RAID_COMMIT_SHARE} of what it believes the objective takes, rather than
     * sitting at the rally accumulating strength under the aircraft the radar is calling.
     */
    static Readiness judge(float arrivedStrength, int required, int tick, int timeoutTick, boolean airRaid) {
        if (airRaid && arrivedStrength > 0f && arrivedStrength >= AIR_RAID_COMMIT_SHARE * required) return Readiness.READY;
        return judge(arrivedStrength, required, tick, timeoutTick);
    }

    /** Whether a cell is walkable and reachable from the drop zone. */
    @FunctionalInterface
    interface Ground {
        boolean ok(int x, int y);
    }

    /**
     * The cell a company gathers at: the drop zone when the objective is near it, otherwise a staging cell
     * {@link #STAGING_STANDOFF_CELLS} short of the objective on the line from the drop zone, slid to ground that is
     * usable and with no believed hostile within {@link #SAFE_CONTACT_CELLS}; if the line is unsafe all the way back
     * the drop zone itself is the answer.
     */
    static int[] rallyCell(int dropX, int dropY, int objectiveX, int objectiveY, Ground ground, List<int[]> hostiles) {
        double dx = objectiveX - dropX;
        double dy = objectiveY - dropY;
        double distance = Math.hypot(dx, dy);
        if (distance <= STAGING_STANDOFF_CELLS + RALLY_RADIUS_CELLS) return new int[] {dropX, dropY};
        for (double back = distance - STAGING_STANDOFF_CELLS; back > RALLY_RADIUS_CELLS; back -= 16.0) {
            int x = (int) Math.round(dropX + dx / distance * back);
            int y = (int) Math.round(dropY + dy / distance * back);
            int[] cell = snap(x, y, ground);
            if (cell != null && safe(cell[0], cell[1], hostiles)) return cell;
        }
        return new int[] {dropX, dropY};
    }

    static boolean safe(int x, int y, List<int[]> hostiles) {
        for (int[] hostile : hostiles) {
            if (Math.hypot(hostile[0] - x, hostile[1] - y) < SAFE_CONTACT_CELLS) return false;
        }
        return true;
    }

    private static int[] snap(int x, int y, Ground ground) {
        if (ground.ok(x, y)) return new int[] {x, y};
        for (int ring = 1; ring <= RALLY_SNAP_CELLS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    if (ground.ok(x + dx, y + dy)) return new int[] {x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /**
     * One pulse of company planning.
     *
     * @param objectives the strong positions this side is disclosed, whatever their state
     * @param bodies     the squads that may be claimed, in or out of a company already
     * @param drop       the drop zone cell
     * @param hostiles   believed hostile contact cells
     */
    Result plan(int tick, Faction perspective, List<Objective> objectives, List<Body> bodies, int[] drop,
                List<int[]> hostiles, CommandTopology topology) {
        return plan(tick, perspective, objectives, bodies, drop, hostiles, topology, AirSupportFacts.NONE);
    }

    /** As above, with the side's air picture: strikes on offer, and what a pass over each nest is believed to cost. */
    Result plan(int tick, Faction perspective, List<Objective> objectives, List<Body> bodies, int[] drop,
                List<int[]> hostiles, CommandTopology topology, AirSupportFacts airSupport) {
        return plan(tick, perspective, objectives, bodies, drop, hostiles, null, topology, airSupport);
    }

    /**
     * As above, with the cells of the believed hostile <em>emplacements</em> kept apart from the believed men beside them.
     * A nest is seen alive only by an emplacement standing on it, and a nest never seen is judged by the sight of an own
     * squad (see {@link FortAssault.NestBelief}); with {@code structures == null} every hostile contact counts for a nest
     * and a nest never seen stays believed live.
     */
    Result plan(int tick, Faction perspective, List<Objective> objectives, List<Body> bodies, int[] drop,
                List<int[]> hostiles, List<int[]> structures, CommandTopology topology, AirSupportFacts airSupport) {
        clock = tick;
        air = airSupport;
        strikeRequests.clear();
        Map<Integer, Order> orders = new TreeMap<>();
        List<String> notes = new ArrayList<>();
        Map<Integer, Body> byId = new TreeMap<>();
        for (Body body : bodies) if (body.alive() > 0) byId.put(body.squadId(), body);

        Set<Integer> live = new TreeSet<>();
        for (Objective objective : objectives) live.add(objective.fortId());
        List<Integer> gone = new ArrayList<>();
        for (Company company : companies.values()) {
            company.members.removeIf(id -> !byId.containsKey(id));
            if (!live.contains(company.fortId)) {
                gone.add(company.fortId);
                notes.add("company for fort " + company.fortId + " dissolved: nothing left of it to take");
                continue;
            }
            if (company.stage == Stage.ASSAULT) {
                float strength = strengthOf(company, byId);
                if (company.members.isEmpty() || strength < BROKEN_FRACTION * company.strengthAtCommit) {
                    gone.add(company.fortId);
                    failures.merge(company.fortId, 1, Integer::sum);
                    notes.add("company for fort " + company.fortId + " broken at strength " + Math.round(strength)
                            + " of " + Math.round(company.strengthAtCommit) + " committed; the next muster believes the fort takes more");
                }
            }
        }
        for (Integer id : gone) companies.remove(id);

        if (objectives.isEmpty() || byId.isEmpty()) {
            footholdSquad = -1;
            return new Result(orders, List.of(), notes);
        }

        List<CompanyState> picture = new ArrayList<>();
        chooseFoothold(byId, drop);
        int claimedByCompanies = 0;
        for (Company company : companies.values()) claimedByCompanies += company.members.size();
        int cap = Math.max(1, (int) Math.ceil(COMPANY_SHARE * byId.size()));

        List<Objective> ordered = new ArrayList<>(objectives);
        ordered.sort(Comparator.<Objective>comparingDouble(o -> Math.hypot(o.cellX() - drop[0], o.cellY() - drop[1]))
                .thenComparingInt(Objective::fortId));
        Ground ground = (x, y) -> topology.inBounds(x, y) && topology.isWalkable(x, y)
                && topology.reachable(drop[0], drop[1], x, y);
        List<int[]> own = new ArrayList<>();
        for (Body body : byId.values()) own.add(new int[] {body.anchorX(), body.anchorY()});
        for (Objective objective : ordered) {
            FortAssault.Works works = worksOf(objective);
            if (works == null) continue;
            if (structures == null) beliefs.get(objective.fortId()).update(works, hostiles, own);
            else beliefs.get(objective.fortId()).update(works, structures, own, topology, tick);
        }

        for (Objective objective : ordered) {
            Company company = companies.get(objective.fortId());
            if (company == null) {
                boolean flown = air.skyNeed().flownAt() && FortTactics.hostileRadar(perspective, objective.aspects());
                // A stand-down leaves the fort to the lanes; it does not outlast the radar being flown at the side.
                if (!flown && tick < standDownUntil.getOrDefault(objective.fortId(), 0)) continue;
                // Free is not the same as recruitable: a lone squad on its lane, or one off the drop zone's ground, is free and
                // will not be taken, and a company formed around nobody stands at its rally until the timeout.
                if (recruitable(byId, ground).isEmpty()) continue;
                company = new Company(objective.fortId(), tick);
                int[] rally = rallyCell(drop[0], drop[1], objective.cellX(), objective.cellY(), ground, hostiles);
                company.rallyX = rally[0];
                company.rallyY = rally[1];
                companies.put(objective.fortId(), company);
                notes.add("company formed for fort " + objective.fortId() + " "
                        + objective.family().name().toLowerCase(Locale.ROOT) + ": rally at (" + rally[0] + "," + rally[1]
                        + "), believes it takes " + requiredNow(objective, company, byId, rally)
                        + ", commits by tick " + company.timeoutTick);
            }
            if (company.stage == Stage.MUSTER && company.members.isEmpty() && recruitable(byId, ground).isEmpty()) {
                // Everything it recruited is gone (dead, in a fight it was not sent to, or taken by a higher authority) and there is
                // no one to replace them: an empty company holds a fort out of the lanes' work and a rally with nobody at it.
                companies.remove(company.fortId);
                notes.add("company for fort " + company.fortId + " dissolved: no squad left to recruit");
                continue;
            }
            if (company.stage == Stage.MUSTER) {
                claimedByCompanies += muster(tick, perspective, objective, company, byId, drop, hostiles, ground, cap - claimedByCompanies, notes);
            }
            if (company.stage == Stage.ASSAULT) {
                assault(tick, perspective, objective, company, byId, topology, orders, notes);
            } else {
                gather(company, byId, orders);
            }
            if (companies.containsKey(objective.fortId())) {
                picture.add(stateOf(tick, objective, company, byId));
            }
        }
        // The beachhead is held while a company musters or assaults; with none (stood down, nobody to recruit) the squad
        // stays on the lanes' work instead of standing on the drop zone for a company that does not exist.
        if (!companies.isEmpty() && footholdSquad >= 0 && byId.containsKey(footholdSquad)) {
            orders.put(footholdSquad, new Order(
                    ObjectiveAssignment.advanceTrack(footholdSquad, drop[0], drop[1]), AssignmentReason.MUSTER_FOOTHOLD));
            picture.add(new CompanyState(-1, "FOOTHOLD", List.of(footholdSquad), 1,
                    Math.round(weight(byId.get(footholdSquad).alive(), byId.get(footholdSquad).machine())), 0,
                    drop[0], drop[1], drop[0], drop[1], 0, -1, "holds the drop zone while a company musters", ""));
        }
        return new Result(orders, picture, notes, List.copyOf(strikeRequests));
    }

    /** The foothold element: one squad that holds the drop zone for as long as any company exists. Sticky. */
    private void chooseFoothold(Map<Integer, Body> byId, int[] drop) {
        if (footholdSquad >= 0 && byId.containsKey(footholdSquad) && !inAnyCompany(footholdSquad)) return;
        footholdSquad = -1;
        Body best = null;
        double bestGap = Double.MAX_VALUE;
        for (Body body : byId.values()) {
            if (body.machine() || body.inContact() || inAnyCompany(body.squadId())) continue;
            double gap = Math.hypot(body.anchorX() - drop[0], body.anchorY() - drop[1]);
            if (gap > FOOTHOLD_RECRUIT_CELLS) continue;
            if (gap < bestGap || (gap == bestGap && best != null && body.squadId() < best.squadId())) {
                best = body;
                bestGap = gap;
            }
        }
        if (best != null) footholdSquad = best.squadId();
    }

    private boolean inAnyCompany(int squadId) {
        for (Company company : companies.values()) if (company.members.contains(squadId)) return true;
        return false;
    }

    private List<Body> freeBodies(Map<Integer, Body> byId) {
        List<Body> free = new ArrayList<>();
        for (Body body : byId.values()) {
            if (body.squadId() == footholdSquad || body.inContact() || inAnyCompany(body.squadId())) continue;
            free.add(body);
        }
        return free;
    }

    /**
     * The free squads a company may take now: on ground shared with the drop zone (a squad that cannot be shown to share
     * it is left to the lanes), and not one of the {@link #LANE_KEEP_SHARE} of its lane the lane keeps.
     */
    private List<Body> recruitable(Map<Integer, Body> byId, Ground ground) {
        List<Body> out = new ArrayList<>();
        for (Body body : freeBodies(byId)) {
            if (!ground.ok(body.anchorX(), body.anchorY())) continue;
            if (clock < barred.getOrDefault(body.squadId(), 0)) continue;
            if (body.track() >= 0 && !body.machine() && laneFree(byId, body.track()) - 1 < Math.ceil(LANE_KEEP_SHARE * laneTotal(byId, body.track()))) continue;
            out.add(body);
        }
        return out;
    }

    private static boolean hasObserver(Company company, Map<Integer, Body> byId) {
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body != null && body.capabilities().has(AssaultCapability.AIR_DIRECTION)) return true;
        }
        return false;
    }

    /** Infantry squads on a lane, whoever has them. */
    private static int laneTotal(Map<Integer, Body> byId, int track) {
        int n = 0;
        for (Body body : byId.values()) if (body.track() == track && !body.machine()) n++;
        return n;
    }

    /** Infantry squads on a lane that are in no company and are not the foothold, in contact or not. */
    private int laneFree(Map<Integer, Body> byId, int track) {
        int n = 0;
        for (Body body : byId.values()) {
            if (body.track() == track && !body.machine() && body.squadId() != footholdSquad && !inAnyCompany(body.squadId())) n++;
        }
        return n;
    }

    private static float strengthOf(Company company, Map<Integer, Body> byId) {
        float sum = 0f;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body != null) sum += weight(body.alive(), body.machine());
        }
        return sum;
    }

    private static boolean arrived(Company company, Body body) {
        return Math.hypot(body.anchorX() - company.rallyX, body.anchorY() - company.rallyY) <= RALLY_RADIUS_CELLS;
    }

    /** Recruits, re-checks the rally point, judges readiness. Returns how many squads were newly recruited. */
    private int muster(int tick, Faction perspective, Objective objective, Company company, Map<Integer, Body> byId, int[] drop,
                       List<int[]> hostiles, Ground ground, int roomLeft, List<String> notes) {
        int required = requiredNow(objective, company, byId, new int[] {company.rallyX, company.rallyY});
        if (!safe(company.rallyX, company.rallyY, hostiles)) {
            int[] rally = rallyCell(drop[0], drop[1], objective.cellX(), objective.cellY(), ground, hostiles);
            company.rallyX = rally[0];
            company.rallyY = rally[1];
            notes.add("company for fort " + company.fortId + " rally moved to (" + rally[0] + "," + rally[1]
                    + "): a hostile is believed near the old one");
        }
        releaseStalled(tick, company, byId, notes);
        int recruited = 0;
        while (recruited < roomLeft) {
            int[] rallyAt = {company.rallyX, company.rallyY};
            FortAssault.Defence defence = defenceOf(objective, company.members, byId, rallyAt);
            required = requiredNow(objective, company, byId, rallyAt);
            boolean massShort = strengthOf(company, byId) < required;
            boolean unstaffed = defence != null && !defence.staffed();
            if (!massShort && !unstaffed) break;
            // A side with bombers on offer prefers a squad with an observer to direct them, while the company has none.
            boolean wantsObserver = air.any() && !hasObserver(company, byId);
            Body observer = null;
            double observerGap = Double.MAX_VALUE;
            Body pick = null;
            double bestGap = Double.MAX_VALUE;
            // A plan that cannot be staffed wants the squad that fills what it lacks before it wants more of what it has.
            for (int pass = unstaffed ? 0 : 1; pass < 2 && pick == null; pass++) {
                for (Body body : recruitable(byId, ground)) {
                    if (pass == 0 && !fillsAGap(defence, body)) continue;
                    if (pass == 1 && !massShort) continue;
                    double gap = Math.hypot(body.anchorX() - company.rallyX, body.anchorY() - company.rallyY);
                    if (wantsObserver && body.capabilities().has(AssaultCapability.AIR_DIRECTION)
                            && (gap < observerGap || (gap == observerGap && observer != null && body.squadId() < observer.squadId()))) {
                        observer = body;
                        observerGap = gap;
                    }
                    if (gap < bestGap || (gap == bestGap && pick != null && body.squadId() < pick.squadId())) {
                        pick = body;
                        bestGap = gap;
                    }
                }
            }
            if (pick == null) break;
            // An observer is taken ahead of the nearest squad when it is not much further, never at the price of a long wait.
            if (observer != null && observer != pick && observerGap <= bestGap + OBSERVER_DETOUR_CELLS) {
                pick = observer;
                bestGap = observerGap;
            }
            company.members.add(pick.squadId());
            company.progress.put(pick.squadId(), new double[] {bestGap, tick});
            recruited++;
            company.timeoutTick = Math.max(company.timeoutTick,
                    tick + (int) (TICKS_PER_CELL * bestGap) + ARRIVAL_SLACK_TICKS);
        }
        required = requiredNow(objective, company, byId, new int[] {company.rallyX, company.rallyY});
        float arrivedStrength = 0f;
        int arrivedSquads = 0;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body != null && arrived(company, body)) {
                arrivedStrength += weight(body.alive(), body.machine());
                arrivedSquads++;
            }
        }
        // Everything this company can get is at the rally and it is still short: wait a grace for anything yet to
        // come, not the whole timeout.
        boolean exhausted = strengthOf(company, byId) < required
                && (recruitable(byId, ground).isEmpty() || roomLeft - recruited <= 0);
        if (exhausted && !company.members.isEmpty() && arrivedSquads == company.members.size() && tick >= company.expectUntil) {
            company.timeoutTick = Math.min(company.timeoutTick, company.graceFrom(tick) + STRAGGLER_GRACE_TICKS);
        } else {
            company.fullSince = -1;
        }
        boolean airRaid = air.skyNeed().flownAt() && FortTactics.hostileRadar(perspective, objective.aspects());
        Readiness readiness = judge(arrivedStrength, required, tick, company.timeoutTick, airRaid);
        // A plan that cannot be fully staffed is gone into as it is when the muster is being bombed: waiting for the filler is the cost.
        if (readiness == Readiness.READY && !airRaid && !staffedAtRally(objective, company, byId, tick)) readiness = Readiness.GATHERING;
        if (readiness == Readiness.TIMED_OUT && arrivedStrength < MIN_COMMIT_SHARE * required
                && standDowns.getOrDefault(company.fortId, 0) < STAND_DOWN_LIMIT && tick >= company.expectUntil) {
            // Too little to be worth the lives: the squads go back to the lanes and the muster tries again later. Bounded, so a
            // force that never grows still commits with what it has the third time.
            standDowns.merge(company.fortId, 1, Integer::sum);
            standDownUntil.put(company.fortId, tick + STAND_DOWN_TICKS);
            companies.remove(company.fortId);
            notes.add("company for fort " + company.fortId + " stands down: " + Math.round(arrivedStrength) + " at the rally of "
                    + required + " believed needed is under " + Math.round(MIN_COMMIT_SHARE * 100) + " per cent; its squads return to the lanes until tick "
                    + (tick + STAND_DOWN_TICKS));
            return recruited;
        }
        if (readiness != Readiness.GATHERING && !company.members.isEmpty()) {
            company.stage = Stage.ASSAULT;
            company.stageTick = tick;
            company.strengthAtCommit = strengthOf(company, byId);
            company.reason = (readiness == Readiness.READY ? (airRaid && arrivedStrength < required ? "flown at, radar disclosed: " : "ready: ") : "timed out: ") + Math.round(arrivedStrength)
                    + " at the rally of " + required + " believed needed, committing "
                    + Math.round(company.strengthAtCommit) + " in " + company.members.size() + " squads";
            notes.add("company for fort " + company.fortId + " commits (" + company.reason + ")");
        } else if (readiness != Readiness.GATHERING) {
            // Nobody to commit: let the muster form again from whatever arrives, with a fresh clock.
            companies.remove(company.fortId);
        }
        return recruited;
    }

    /**
     * Lets go of recruits that are not closing on the rally point. Orders to the rally are advisory to a squad in a fight
     * or on a path that does not close, and a company waiting on such a squad stands at its rally with nobody at it until the
     * timeout; the squad is better back on the lanes, which it is already fighting for, and is not taken again at once.
     */
    private void releaseStalled(int tick, Company company, Map<Integer, Body> byId, List<String> notes) {
        company.progress.keySet().removeIf(id -> !company.members.contains(id));
        List<Integer> released = new ArrayList<>();
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body == null || arrived(company, body)) {
                company.progress.remove(id);
                continue;
            }
            double gap = Math.hypot(body.anchorX() - company.rallyX, body.anchorY() - company.rallyY);
            double[] seen = company.progress.get(id);
            if (seen == null || gap < seen[0] - STALL_CLOSE_CELLS) {
                company.progress.put(id, new double[] {gap, tick});
            } else if (tick - seen[1] >= STALL_TICKS) {
                released.add(id);
            }
        }
        for (int id : released) {
            company.members.remove(id);
            company.progress.remove(id);
            barred.put(id, tick + RELEASE_BAR_TICKS);
            notes.add("company for fort " + company.fortId + " released squad " + id + ": no closer to the rally in "
                    + STALL_TICKS + " ticks; it returns to the lanes until tick " + (tick + RELEASE_BAR_TICKS));
        }
    }

    private static boolean fillsAGap(FortAssault.Defence defence, Body body) {
        if (defence == null || defence.staffing() == null) return false;
        for (AssaultRoles.Gap gap : defence.staffing().gaps()) {
            if (gap.need().rateOf(body.capabilities()) > 0f) return true;
        }
        return false;
    }

    /**
     * Whether the squads at the rally can staff the plan, or none that would is still on its way: a mass that is there but
     * cannot do what the plan needs (no gun that hurts a nest, nothing to cut a wall) waits for the squad that can, for as
     * long as one is coming (a member not yet at the rally, a lance called down), and no longer. A plan nobody is coming
     * to staff is gone into as it is, and the shortfall is reported.
     */
    private boolean staffedAtRally(Objective objective, Company company, Map<Integer, Body> byId, int tick) {
        Set<Integer> arrivedSquads = new TreeSet<>();
        boolean fillerComing = tick < company.expectUntil;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body == null) continue;
            if (arrived(company, body)) arrivedSquads.add(id);
        }
        FortAssault.Defence defence = defenceOf(objective, arrivedSquads, byId, new int[] {company.rallyX, company.rallyY});
        if (defence == null || defence.staffed()) return true;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body != null && !arrivedSquads.contains(id) && fillsAGap(defence, body)) fillerComing = true;
        }
        return !fillerComing;
    }

    private void gather(Company company, Map<Integer, Body> byId, Map<Integer, Order> orders) {
        if (!companies.containsKey(company.fortId)) return;
        for (int id : company.members) {
            if (!byId.containsKey(id)) continue;
            orders.put(id, new Order(ObjectiveAssignment.advanceTrack(id, company.rallyX, company.rallyY),
                    AssignmentReason.MUSTER_GATHER));
        }
    }

    private void assault(int tick, Faction perspective, Objective objective, Company company, Map<Integer, Body> byId,
                         CommandTopology topology, Map<Integer, Order> orders, List<String> notes) {
        FortAssault.Works works = worksOf(objective);
        if (works != null) {
            if (company.execution == null) {
                Body lead = null;
                for (int id : company.members) {
                    Body body = byId.get(id);
                    if (body != null) {
                        lead = body;
                        break;
                    }
                }
                FortAssault.NestBelief belief = beliefs.get(objective.fortId());
                FortAssault.Defence defence = lead == null ? null
                        : defenceOf(objective, company.members, byId, new int[] {lead.anchorX(), lead.anchorY()});
                if (defence != null) {
                    company.execution = new FortAssault.Execution(works, belief, defence, tick);
                    satchelRecords.put(company.fortId, company.execution.satchelRecord());
                    notes.add("company for fort " + company.fortId + " takes the " + defence.variant() + ": "
                            + defence.liveCovering() + " nests believed to cover it"
                            + (defence.staffed() ? "" : "; it cannot staff it (" + String.join("; ", defence.staffing().describeGaps()) + ")"));
                }
            }
            if (company.execution != null) {
                company.execution.skyNeed(air.skyNeed());
                company.execution.strikes(new FortAssault.Strikes(air, true, clock));
                Map<Integer, Order> planned = company.execution.run(tick, perspective, objective, company.members, byId,
                        topology, notes);
                if (planned == null) {
                    notes.add("company for fort " + company.fortId + " dissolved: no aspect of it can be reached");
                    companies.remove(company.fortId);
                    return;
                }
                orders.putAll(planned);
                for (FortAssault.StrikeWish wish : company.execution.takeWishes()) {
                    strikeRequests.add(new StrikeRequest(company.fortId, wish));
                    notes.add("company for fort " + company.fortId + " asks for a strike on the nest at (" + wish.cellX() + ","
                            + wish.cellY() + ") through squads " + wish.operatorSquads());
                }
                return;
            }
        }
        List<DefenceStructureTactics.Candidate> candidates = new ArrayList<>();
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body == null) continue;
            candidates.add(new DefenceStructureTactics.Candidate(id, body.anchorX(), body.anchorY(), body.incumbent(),
                    body.demolition(), body.machine()));
        }
        List<DefenceStructureTactics.Task> tasks = DefenceStructureTactics.planMassed(
                perspective, objective.aspects(), candidates, topology, air.skyNeed());
        if (tasks.isEmpty()) {
            notes.add("company for fort " + company.fortId + " dissolved: no aspect of it can be reached");
            companies.remove(company.fortId);
            return;
        }
        Map<Integer, DefenceStructureTactics.Task> byTask = new TreeMap<>();
        for (DefenceStructureTactics.Task task : tasks) byTask.put(task.squadId(), task);
        company.members.removeIf(id -> !byTask.containsKey(id));

        Map<Integer, Double> remaining = new TreeMap<>();
        for (DefenceStructureTactics.Task task : tasks) {
            Body body = byId.get(task.squadId());
            remaining.put(task.squadId(), Math.hypot(body.anchorX() - task.structure().cellX(),
                    body.anchorY() - task.structure().cellY()));
        }
        Map<Integer, Boolean> holds = cohesion(tick, company, byId, remaining);
        for (DefenceStructureTactics.Task task : tasks) {
            Body body = byId.get(task.squadId());
            if (holds.getOrDefault(task.squadId(), false)) {
                orders.put(task.squadId(), new Order(
                        ObjectiveAssignment.advanceTrack(task.squadId(), body.anchorX(), body.anchorY()),
                        AssignmentReason.ASSAULT_COMPANY_RALLY));
            } else {
                orders.put(task.squadId(), new Order(task.assignment(), AssignmentReason.ASSAULT_COMMIT));
            }
        }
    }

    /**
     * Which company squads wait for the rest on the approach. The same bounds as a command group's rally
     * ({@link ConquestGroupCommand#BOUND_CELLS}, {@link ConquestGroupCommand#LATE_ARRIVAL_CELLS},
     * {@link ConquestGroupCommand#WAIT_TICKS}), measured as progress toward the objective rather than along a
     * lane: a squad more than the bound ahead of the company's rearward median waits, once per genuine regroup, for
     * at most the wait; a straggler more than the late-arrival distance behind the lead is not waited for; and a
     * squad in contact, or inside the engage ring, is never held.
     */
    private Map<Integer, Boolean> cohesion(int tick, Company company, Map<Integer, Body> byId,
                                           Map<Integer, Double> remaining) {
        Map<Integer, Boolean> holds = new TreeMap<>();
        List<Integer> quiet = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : remaining.entrySet()) {
            Body body = byId.get(entry.getKey());
            if (body.inContact() || entry.getValue() <= ENGAGE_RING_CELLS) continue;
            quiet.add(entry.getKey());
        }
        if (quiet.size() < 2) { company.waitStarted = -1; return holds; }
        double lead = quiet.stream().mapToDouble(id -> -remaining.get(id)).max().orElse(0);
        List<Integer> body = quiet.stream().filter(id -> lead - (-remaining.get(id))
                <= ConquestGroupCommand.LATE_ARRIVAL_CELLS).toList();
        if (body.size() < 2) { company.waitStarted = -1; return holds; }
        double[] positions = body.stream().mapToDouble(id -> -remaining.get(id)).sorted().toArray();
        double supported = positions[(positions.length - 1) / 2];
        if (lead - supported <= ConquestGroupCommand.BOUND_CELLS) {
            company.waitStarted = -1;
            company.boundReleased = false;
            return holds;
        }
        if (company.boundReleased) return holds;
        if (company.waitStarted < 0) company.waitStarted = tick;
        if (tick - company.waitStarted >= ConquestGroupCommand.WAIT_TICKS) {
            company.boundReleased = true;
            return holds;
        }
        for (int id : body) {
            if (-remaining.get(id) - supported > ConquestGroupCommand.BOUND_CELLS) holds.put(id, true);
        }
        return holds;
    }

    private CompanyState stateOf(int tick, Objective objective, Company company, Map<Integer, Body> byId) {
        int arrivedSquads = 0;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body == null) continue;
            if (company.stage == Stage.ASSAULT || arrived(company, body)) arrivedSquads++;
        }
        int required = requiredNow(objective, company, byId, new int[] {company.rallyX, company.rallyY});
        FortAssault.Defence defence = company.execution == null
                ? defenceOf(objective, company.members, byId, new int[] {company.rallyX, company.rallyY}) : null;
        String variant = company.execution != null ? company.execution.variant() : defence == null ? "" : defence.variant();
        List<String> shortfall = company.execution != null ? company.execution.shortfall()
                : defence == null || defence.staffing() == null ? List.of() : defence.staffing().describeGaps();
        String reason = company.stage == Stage.MUSTER
                ? "gathering: " + arrivedSquads + " of " + company.members.size() + " squads at the rally, believes it takes "
                + required + ", commits by tick " + company.timeoutTick
                : company.reason;
        return new CompanyState(objective.fortId(), company.stage.name(), new ArrayList<>(company.members), arrivedSquads,
                Math.round(strengthOf(company, byId)), required, company.rallyX, company.rallyY,
                objective.cellX(), objective.cellY(), tick - company.stageTick,
                company.stage == Stage.MUSTER ? company.timeoutTick : -1,
                company.execution == null ? reason : reason + " | plan: " + company.execution.describe(),
                company.execution == null ? "" : company.execution.step().name(), variant, shortfall);
    }
}
