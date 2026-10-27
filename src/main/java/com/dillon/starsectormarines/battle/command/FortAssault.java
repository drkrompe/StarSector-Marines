package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AssaultMuster.Body;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Objective;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Order;
import com.dillon.starsectormarines.battle.command.AssaultRoles.Need;
import com.dillon.starsectormarines.battle.command.AssaultRoles.Staffing;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
    /** How close an own squad must have a clear line to a nest never seen alive for the silence to say it is gone. UNTUNED. */
    static final int NEST_SIGHT_CELLS = 16;
    /** Ticks of that clear view, with no contact on the nest, before it is believed dead. UNTUNED. */
    static final int NEST_UNSEEN_TICKS = 90;
    /**
     * How far from a nest an infantry fire element stands, at the farthest, and the depth of the band inside that it may
     * stand in. A rifle reaches 22 cells and the nest's turret stands half a cell off its cell centre, so the band lies well
     * inside it. UNTUNED.
     */
    static final int FIRE_STANDOFF_INFANTRY = 20;
    static final int FIRE_BAND_INFANTRY = 5;
    /**
     * As above for a machine squad: just past the nests' 24-cell reach and inside the mech weapons' (30 and more), so it
     * answers the nests without being answered. UNTUNED.
     */
    static final int FIRE_STANDOFF_MACHINE = 28;
    static final int FIRE_BAND_MACHINE = 3;
    /** Nearest a fire element is put to a nest, whatever its weapons' reach: inside this it is in the nest's face. */
    static final int MIN_FIRE_STANDOFF = 12;
    /** Cells around a nest's cell a fire mission covers: the nest's turret stands within a cell of it. UNTUNED. */
    static final int FIRE_MISSION_RADIUS = 3;
    /** The assault element gathers at least this far from every nest believed live. UNTUNED. */
    static final int ASSEMBLY_STANDOFF = 30;
    /** A firing position keeps this far from the approach lane, so the fire element is not in the way in. */
    static final int LANE_CLEARANCE = 7;
    /** A squad this close to its cell has arrived. */
    static final int ARRIVE_CELLS = 8;
    /** The share of the infantry that becomes the fire element, when there are at least {@link #MIN_INFANTRY_FOR_FIRE}. */
    static final float FIRE_SHARE = 0.34f;
    static final int MIN_INFANTRY_FOR_FIRE = 3;

    /**
     * Anti-structure damage per second the base of fire needs for each nest believed live that covers the way in: a nest
     * has about 80 structure behind 60 to 140 armour, and the catalog's shells, rockets and missile racks do tens of damage
     * a second each. UNTUNED.
     */
    static final float STRUCTURE_FIRE_PER_NEST = 20f;
    /** Wall damage per second a distance breach asks for: slow enough to be a siege, fast enough to be a plan. UNTUNED. */
    static final float BREACH_RATE = 20f;
    /**
     * Wall damage a breach must be able to deliver in all. A fort wall section is believed to be about two layers of a few
     * hundred hit points each, and shells miss; the figure is that estimate with a margin, not a read of the wall. UNTUNED.
     */
    static final float BREACH_RESERVE = 1200f;
    /** Wall damage per second a close breach (charges) asks for: one charge's worth. UNTUNED. */
    static final float CLOSE_BREACH_RATE = 10f;
    /** Bodies able to enter a building the way in must have to be taken. UNTUNED. */
    static final float ENTRY_BODIES = 4f;
    /** A distance breacher stands this far inside its weapon's reach from the wall, in a band this deep. */
    static final int BREACH_STANDOFF_MARGIN = 2;
    static final int BREACH_BAND = 3;
    /** Cells from a live nest that a distance breacher prefers to keep to: the nests' reach. */
    static final int NEST_REACH_CELLS = 24;
    /** Ticks the plan waits for a wall to come down before the assault goes in regardless. UNTUNED. */
    static final int BREACH_TIMEOUT_TICKS = 2400;
    /** Ticks the plan allows a let-go breacher for each cell of straight line to its place, a walk round a fort being longer than that and a machine slower. UNTUNED. */
    static final int BREACH_WALK_TICKS_PER_CELL = 60;
    /** Ticks added to a breacher's walk allowance: the walk is bounded, the wall is then given its {@link #BREACH_TIMEOUT_TICKS}. UNTUNED. */
    static final int BREACH_WALK_SLACK_TICKS = 600;

    /** Ticks a requested strike is taken to be on its way: the call, the corridor in from the map edge, the pass. UNTUNED. */
    static final int STRIKE_FLIGHT_TICKS = 1200;
    /** Ticks before a nest whose strike was refused is asked for again. UNTUNED. */
    static final int STRIKE_RETRY_TICKS = 1500;
    /** Strikes asked for on one nest before the plan stops counting on the air for it. UNTUNED. */
    static final int STRIKE_ATTEMPTS = 3;

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

    /** Ticks a smoke screen job is given to stand its clouds: the walk of a grenadier from the staging cell, the throw and the flight. UNTUNED. */
    static final int SMOKE_JOB_TICKS = 600;
    /** Ticks a squad takes between one cloud of a screen and the next: it throws, walks to the next place and throws again. UNTUNED. */
    static final int SMOKE_THROW_CYCLE_TICKS = 300;
    /** Screens tried on one nest before the plan stops counting on smoke for it. UNTUNED. */
    static final int SMOKE_ATTEMPTS = 2;
    /** Most clouds one nest's screen may ask: a route needing more is not screened, it is answered another way. UNTUNED. */
    static final int MAX_SCREEN_CLOUDS = 4;
    /** Radius of the cloud a grenade stands (the catalog's smoke grenade). */
    static final double CLOUD_RADIUS_CELLS = 2.25;
    /** Ticks a squad on attack move takes for a cell of its route, measured at 45 cells in 960 ticks: the life a cloud must have left to screen the walk. */
    static final int WALK_TICKS_PER_CELL = 20;
    /** Sim ticks to a second. */
    static final int TICKS_PER_SECOND = 30;
    /** Ticks a sapper stands at the wall setting its charge, over which a screen must also hold. UNTUNED. */
    static final int PLANT_DWELL_TICKS = 150;
    /** How far from a grenadier's place to its cloud the order puts it: inside the throw. */
    static final int THROW_STANDOFF_CELLS = 5;
    /** A cloud this close to a screen's point is that screen standing. */
    static final double SMOKE_STANDS_CELLS = 1.5;

    private FortAssault() {}

    enum Step { POSITION, SUPPRESS, ENTRY, OBJECTIVE, HOLD }

    enum Role { FIRE, BREACH, ASSAULT }

    /** How a breach is cut: from range with shells and rockets, or at the foot of the wall with charges. */
    enum BreachMeans { DISTANCE, CLOSE }

    /** A way in, on the map. */
    record Way(Variant variant, int gateIndex, int mouthX, int mouthY, int outX, int outY, int entryX, int entryY,
               List<Integer> covering) { }

    /**
     * A strike the plan wants asked for: the nest's cell, and the squads whose observers may direct it. The plan only wishes;
     * the commander asks the air serially after the plan is committed, and reports the answer back.
     */
    record StrikeWish(int nest, int cellX, int cellY, Set<Integer> operatorSquads) {
        StrikeWish {
            operatorSquads = Set.copyOf(operatorSquads);
        }
    }

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
            for (int[] nest : frame.plan().defenceNests()) nests.add(frame.cell(nest[0], nest[1]));
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
        /** The tick before which a nest whose strike was refused is not asked for again, and how many strikes it has been asked. */
        private final Map<Integer, Integer> strikeRefusedUntil = new HashMap<>();
        private final Map<Integer, Integer> strikesAsked = new HashMap<>();
        /** The tick from which a never-seen nest has been in clear view of an own squad with no contact on it. */
        private final Map<Integer, Integer> unseenSince = new HashMap<>();

        NestBelief(int nests) {
            seenAlive = new boolean[nests];
            dead = new boolean[nests];
        }

        /** Whether the air may be counted on for a nest at {@code tick}: not refused lately and not asked too often. */
        boolean strikeOpen(int nest, int tick) {
            return tick >= strikeRefusedUntil.getOrDefault(nest, 0) && strikesAsked.getOrDefault(nest, 0) < STRIKE_ATTEMPTS;
        }

        void strikeAsked(int nest) {
            strikesAsked.merge(nest, 1, Integer::sum);
        }

        void strikeRefused(int nest, int untilTick) {
            strikeRefusedUntil.put(nest, untilTick);
        }

        /**
         * A nest with a contact on it is seen alive. A nest once seen alive, with none now, and with an own squad in
         * sight of it, is believed dead for good. A nest never seen is believed live: the works are disclosed, and a gun
         * out of sight is not a gun absent.
         */
        void update(Works works, List<int[]> hostiles, List<int[]> own) {
            update(works, hostiles, own, null, 0);
        }

        /**
         * As above, with the sight a nest never seen alive can be judged by. A nest nobody has ever had a contact on, with an
         * own squad holding a clear line to it inside {@link FortAssault#NEST_SIGHT_CELLS} for {@link FortAssault#NEST_UNSEEN_TICKS} without
         * any contact on it, is believed dead: a gun that stood there would have been seen and shot at the squad, so a squad
         * that stood in view of the ground and was never answered has found it already destroyed (by the lane squads that
         * got to it before the plan's belief did). The same sight is the evidence either way, never the map's truth.
         * {@code topology == null} leaves the never-seen nests believed live, as before.
         */
        void update(Works works, List<int[]> hostiles, List<int[]> own, CommandTopology topology, int tick) {
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
                    unseenSince.remove(i);
                    continue;
                }
                if (!seenAlive[i]) {
                    if (topology == null) continue;
                    boolean inView = false;
                    for (int[] mine : own) {
                        if (Math.hypot(mine[0] - nest[0], mine[1] - nest[1]) <= NEST_SIGHT_CELLS
                                && Execution.sight(topology, mine[0], mine[1], nest[0], nest[1])) {
                            inView = true;
                            break;
                        }
                    }
                    if (!inView) {
                        unseenSince.remove(i);
                        continue;
                    }
                    int since = unseenSince.computeIfAbsent(i, k -> tick);
                    if (tick - since >= NEST_UNSEEN_TICKS) dead[i] = true;
                    continue;
                }
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

    /**
     * What a company can ask of the air, frozen for one pulse.
     *
     * @param air      the side's strikes on offer and the believed cost of a pass over each nest
     * @param operator whether the company brought a forward observer to direct them
     * @param tick     the tick of the pulse, for the nests whose strikes were refused lately
     */
    record Strikes(AirSupportFacts air, boolean operator, int tick) {
        static final Strikes NONE = new Strikes(AirSupportFacts.NONE, false, 0);
    }

    /**
     * The nests covering a way in that the company may count a strike for: believed live, not lately refused, over which a
     * pass is believed to cost no more than the director risks, up to the strikes on offer, least defended first. Empty without an
     * observer in the company, which is the only way to ask.
     */
    static List<Integer> strikeable(Works works, NestBelief belief, Way way, Strikes strikes) {
        List<Integer> out = new ArrayList<>();
        if (!strikes.operator() || !strikes.air().any()) return out;
        for (int i : way.covering()) {
            int[] nest = works.nests.get(i);
            if (belief.live(i) && belief.strikeOpen(i, strikes.tick()) && strikes.air().acceptable(nest[0], nest[1])) out.add(i);
        }
        out.sort(Comparator.<Integer>comparingDouble(i -> strikes.air().riskByCell().get(
                AirSupportFacts.key(works.nests.get(i)[0], works.nests.get(i)[1]))).thenComparingInt(i -> i));
        return out.size() <= strikes.air().calls() ? out : new ArrayList<>(out.subList(0, strikes.air().calls()));
    }

    /**
     * The believed defence in front of a company, and the way in it would take.
     *
     * @param means    how a breach would be cut, or null for a gate
     * @param staffing what the company's capabilities make of the plan's needs, or null when they were not judged
     */
    record Defence(Way way, int liveCovering, int liveOther, float strength, BreachMeans means, Staffing staffing,
                   List<Integer> strikeNests) {
        Defence {
            strikeNests = List.copyOf(strikeNests);
        }

        Defence(Way way, int liveCovering, int liveOther, float strength, BreachMeans means, Staffing staffing) {
            this(way, liveCovering, liveOther, strength, means, staffing, List.of());
        }

        Defence(Way way, int liveCovering, int liveOther, float strength) {
            this(way, liveCovering, liveOther, strength, null, null);
        }

        /** Whether the company can staff the plan, or true when that was not judged. */
        boolean staffed() {
            return staffing == null || staffing.staffed();
        }

        /** The way in, as a short phrase. */
        String variant() {
            return way.variant().name().toLowerCase(Locale.ROOT)
                    + (means == null ? "" : " (" + means.name().toLowerCase(Locale.ROOT) + ")");
        }
    }

    /**
     * What a plan asks of the company that takes this way in: a base of fire when nests cover it, a breach when it is a
     * cut wall, and bodies to go in.
     */
    static List<Need> needs(Way way, BreachMeans means, int liveCovering) {
        return needs(way, means, liveCovering, 0);
    }

    /** As above, with {@code struck} of the covering nests counted as answered from the air and so not asked of the ground. */
    static List<Need> needs(Way way, BreachMeans means, int liveCovering, int struck) {
        List<Need> needs = new ArrayList<>();
        if (liveCovering - struck > 0) {
            needs.add(new Need(AssaultRoles.Role.BASE_OF_FIRE,
                    EnumSet.of(AssaultCapability.DIRECT_STRUCTURE_FIRE, AssaultCapability.STANDOFF_STRUCTURE_FIRE),
                    STRUCTURE_FIRE_PER_NEST * (liveCovering - struck), 0f));
        }
        if (way.variant() == Variant.BREACH) {
            if (means == BreachMeans.CLOSE) {
                needs.add(new Need(AssaultRoles.Role.BREACH, EnumSet.of(AssaultCapability.CLOSE_BREACH),
                        CLOSE_BREACH_RATE, 0f));
            } else {
                needs.add(new Need(AssaultRoles.Role.BREACH, EnumSet.of(AssaultCapability.DISTANCE_BREACH),
                        BREACH_RATE, BREACH_RESERVE));
            }
        }
        needs.add(new Need(AssaultRoles.Role.ENTRY, EnumSet.of(AssaultCapability.ASSAULT), ENTRY_BODIES, 0f));
        return needs;
    }

    /**
     * The way in a company would take given what its squads can <em>do</em>: the least covered gate, nearest the company
     * on a tie, unless a breach is less covered than every gate and the company can staff it (from range first, then at
     * the foot of the wall). The answer says whether the company can staff the way it chose and, when it cannot, what is
     * missing. Null when the plan offers no way.
     */
    static Defence assess(Works works, NestBelief belief, int fromX, int fromY, Map<Integer, CapabilityProfile> squads) {
        return assess(works, belief, fromX, fromY, squads, Strikes.NONE);
    }

    /** As above, counting the strikes the company may call for the nests covering each way in. */
    static Defence assess(Works works, NestBelief belief, int fromX, int fromY, Map<Integer, CapabilityProfile> squads,
                          Strikes strikes) {
        Way gate = null;
        int gateCover = Integer.MAX_VALUE;
        double gateGap = Double.MAX_VALUE;
        Way breach = null;
        for (Way way : works.ways) {
            if (way.variant() == Variant.BREACH) {
                breach = way;
                continue;
            }
            int cover = belief.liveCovering(way);
            double gap = Math.hypot(way.mouthX() - fromX, way.mouthY() - fromY);
            if (cover < gateCover || (cover == gateCover && gap < gateGap)) {
                gate = way;
                gateCover = cover;
                gateGap = gap;
            }
        }
        Defence gateDefence = gate == null ? null : defenceOf(works, belief, gate, null, squads, strikes);
        if (breach != null && (gate == null || belief.liveCovering(breach) <= gateCover)) {
            // As covered as the best gate, the breach is still the way a company that brought kit for a wall takes: it
            // chooses where the assault comes in, instead of walking into the gatehouse the nests are laid on. A
            // company with nothing to open a wall cannot staff it and takes the gate.
            for (BreachMeans means : BreachMeans.values()) {
                Defence defence = defenceOf(works, belief, breach, means, squads, strikes);
                if (defence.staffed()) return defence;
            }
        }
        if (gateDefence != null) return gateDefence;
        return breach == null ? null : defenceOf(works, belief, breach, BreachMeans.DISTANCE, squads, strikes);
    }

    private static Defence defenceOf(Works works, NestBelief belief, Way way, BreachMeans means,
                                     Map<Integer, CapabilityProfile> squads, Strikes strikes) {
        int covering = belief.liveCovering(way);
        int other = Math.max(0, belief.liveCount() - covering);
        float strength = NEST_COVERING_STRENGTH * covering + NEST_OTHER_STRENGTH * other
                + (way.variant() == Variant.BREACH ? BREACH_STRENGTH : GATE_STRENGTH);
        List<Integer> struck = strikeable(works, belief, way, strikes);
        return new Defence(way, covering, other, strength, means,
                AssaultRoles.assign(needs(way, means, covering, struck.size()), squads), struck);
    }

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

    /** A smoke screen being stood for one nest: the points a cloud is wanted on, and when it was begun. */
    record SmokeJob(int nest, List<int[]> points, int startedTick, int allowedTicks) { }

    /**
     * What a company's close breachers went through while the plan held them, kept for evidence and never read by the plan:
     * when they began to be held and were let go, the enabling actions asked for and by what, and how the approach stood
     * at the moment of release.
     */
    static final class SatchelRecord {
        int heldSinceTick = -1;
        int releasedTick = -1;
        /** The last pulse on which the breachers were still held. */
        int lastHeldTick = -1;
        final Map<SatchelApproach.Enabler, Integer> asked = new EnumMap<>(SatchelApproach.Enabler.class);
        /** Nests still open at the moment of release: a release into one is a fault. */
        int openAtRelease = -1;
        final Map<SatchelApproach.Answer, Integer> answersAtRelease = new EnumMap<>(SatchelApproach.Answer.class);
        int approachChangedTick = -1;
        String approachChange = "";
        /** The nest each fire squad is placed against while it is believed live, as of the last pulse: where the company's own fire is meant to go. */
        final Map<Integer, int[]> baseOfFire = new TreeMap<>();
        /** Breach roles taken from squads that lost the kit behind them before they were let go. */
        int breachersLost;
        /** What became of the breach roles that lost their kit, and of the squads that took them over, as sentences. */
        final List<String> breachLog = new ArrayList<>();
        /** The readings of the last pulse the breachers were held, as a sentence, for the evidence report. */
        String lastReadings = "";
        /** Pulses on which an open nest had nothing the company could answer it with. */
        int unservablePulses;
        /** Pulses on which an open nest had an enabling action already in motion. */
        int pendingPulses;
        /** The last tick a further nest was answered while the breachers were held. */
        int lastProgressTick = -1;
        /** Nests that read DEAD for the first time while the breachers were held. */
        int nestsDiedWhileHeld;
        /** Screens the planner declined because of what they would cost the company, by reason, in pulses. */
        final Map<SatchelApproach.Refusal, Integer> refused = new EnumMap<>(SatchelApproach.Refusal.class);
        /** Pulses on which the company carried smoke but none that would last the crossing. */
        int smokeTooShortPulses;
        /** The last reason each nest had no smoke on offer, for the evidence report. */
        final Map<Integer, String> smokeNotes = new TreeMap<>();
        /** What happened to each screen the company ordered: when, for which nest, who threw it and how it ended. */
        final List<String> smokeLog = new ArrayList<>();

        int heldTicks() {
            return heldSinceTick < 0 ? 0 : (releasedTick >= 0 ? releasedTick : lastHeldTick) - heldSinceTick;
        }

        int asked(SatchelApproach.Enabler enabler) {
            return asked.getOrDefault(enabler, 0);
        }
    }

    /** One company's run through the plan. */
    static final class Execution {
        private final Works works;
        private final NestBelief belief;
        private Way way;
        private Step step = Step.POSITION;
        private int stepTick;
        private int commitTick;
        private final Map<Integer, Role> roles = new TreeMap<>();
        private final Map<Integer, int[]> firePositions = new TreeMap<>();
        /** The nest each fire squad was placed against, by index into the works. */
        private final Map<Integer, Integer> fireNests = new TreeMap<>();
        private final Map<Integer, int[]> breachPositions = new TreeMap<>();
        private BreachMeans means;
        private Staffing staffing;
        private int[] assembly;

        private final Map<Integer, Integer> breachGo = new TreeMap<>();
        /** The tick by which every let-go breacher should have reached its place, allowing for the walk. */
        private int breachWalkUntil = -1;
        /** The tick a breacher first stood at its place, from which the wall is given its time, or -1. */
        private int breachArrivedTick = -1;
        private int[] heldAspect;
        private int[] fallen;
        private int liveCovering;
        /** The covering nests the company counted a strike for, the ones it has asked for and until when each is on its way. */
        private final List<Integer> strikeNests = new ArrayList<>();
        private final Set<Integer> strikeAsked = new TreeSet<>();
        private final Map<Integer, Integer> strikeFlight = new TreeMap<>();
        private final List<StrikeWish> wishes = new ArrayList<>();
        /** Whether the side is shut out of the sky by anti-air it knows; set by the muster each pulse. */
        private SkyNeed skyNeed = SkyNeed.NONE;

        /** What the company can ask of the air, set by the muster each pulse. */
        private Strikes strikes = Strikes.NONE;
        /** Clouds of smoke standing on the field, as the side sees them, set by the muster each pulse. */
        private List<SatchelApproach.Cloud> clouds = List.of();
        /** The cells of the held breachers' route each live nest sees, by nest, this pulse. */
        private final Map<Integer, List<int[]>> exposedCells = new TreeMap<>();
        /** The last cell of each held breacher's route that no live nest sees: where it waits. */
        private final Map<Integer, int[]> holdEdge = new TreeMap<>();
        /** The places the held breachers are to reach, this pulse. */
        private List<int[]> heldPlaces = List.of();
        /** Ticks the held breachers need to cross the exposed stretch and set their charge, this pulse. */
        private int crossingTicks;
        /** Smoke screens being stood, by the nest they are for, and the screens each nest has been tried with. */
        private final Map<Integer, SmokeJob> smokeJobs = new TreeMap<>();
        private final Map<Integer, Integer> smokeTries = new TreeMap<>();
        /** Orders for squads that are throwing smoke this pulse, laid over the ones the step gave them. */
        private final Map<Integer, Order> smokeOrders = new TreeMap<>();
        private final Set<String> smokeAssigned = new TreeSet<>();
        /** A base-of-fire squad the plan put against a particular nest, over the spread it was first given. */
        private final Map<Integer, Integer> forcedFire = new TreeMap<>();
        /** The tick from which a friendly squad in position has been engaging each nest, by nest index. */
        private final Map<Integer, Integer> engagedSince = new TreeMap<>();
        /** The tick the close breachers began to be held, the tick a further nest was last answered, and the most answered yet. */
        private int satchelHeldSince = -1;
        private int satchelProgress = -1;
        private int satchelAnswered = -1;
        /** Whether this pulse's approach is survivable for the held close breachers. */
        private boolean satchelClear;
        private final SatchelRecord satchelRecord = new SatchelRecord();

        void strikes(Strikes strikes) {
            this.strikes = strikes;
        }

        /** The clouds of smoke standing this pulse, as the side sees them: their life is turned into the tick they thin out. */
        void clouds(List<SmokeCloudFact> standing, int tick) {
            this.clouds = SmokePartition.of(standing, tick, TICKS_PER_SECOND).clouds();
        }


        SatchelRecord satchelRecord() {
            return satchelRecord;
        }

        /** Whether this run has been flown at: once it has, it stays on the radar until the radar is not the side's to take. */
        private boolean flownAt;

        void skyNeed(SkyNeed skyNeed) {
            flownAt |= skyNeed.flownAt();
            this.skyNeed = new SkyNeed(skyNeed.shut(), flownAt);
        }

        /**
         * Whether the plan goes straight for the hostile bunker's radar: the company has been flown at and the bunker is a
         * disclosed aspect that is not ours. Set-up steps (position, suppress, entry, hold) are for a fort worked at leisure;
         * a radar spotting for aircraft that are bombing the company is not.
         */
        boolean radarFirst(Faction perspective, Objective objective) {
            return flownAt && FortTactics.hostileRadar(perspective, objective.aspects());
        }

        Execution(Works works, NestBelief belief, Way way, int tick) {
            this(works, belief, new Defence(way, 0, 0, 0f, way.variant() == Variant.BREACH ? BreachMeans.DISTANCE : null, null), tick);
        }

        /** The run of a company that has judged its way in: the roles it was staffed with are the ones it takes. */
        Execution(Works works, NestBelief belief, Defence defence, int tick) {
            this.works = works;
            this.belief = belief;
            this.way = defence.way();
            this.means = defence.means();
            this.staffing = defence.staffing();
            this.strikeNests.addAll(defence.strikeNests());
            this.stepTick = tick;
            this.commitTick = tick;
        }

        /** The strikes the plan wants asked for since it was last drained, each through a squad that carries an observer. */
        List<StrikeWish> takeWishes() {
            List<StrikeWish> out = new ArrayList<>(wishes);
            wishes.clear();
            return out;
        }

        /** What the air said to a request: on its way (the nest is taken to be answered for a time) or refused (asked again later). */
        void strikeAnswered(int nest, boolean calling, int tick) {
            if (calling) {
                strikeFlight.put(nest, tick + STRIKE_FLIGHT_TICKS);
            } else {
                strikeAsked.remove(nest);
                belief.strikeRefused(nest, tick + STRIKE_RETRY_TICKS);
            }
        }

        /** Whether a strike asked for on a nest still believed live is thought to be on its way. */
        boolean strikeInFlight(int tick) {
            for (Map.Entry<Integer, Integer> flight : strikeFlight.entrySet()) {
                if (flight.getValue() > tick && belief.live(flight.getKey())) return true;
            }
            return false;
        }

        /** Asks for the strikes the plan counted, while the company is still setting up its fire: once per nest, again if it did not work. */
        private void planStrikes(int tick, Set<Integer> members, Map<Integer, Body> byId) {
            if (strikeNests.isEmpty() || (step != Step.POSITION && step != Step.SUPPRESS)) return;
            Set<Integer> operators = new TreeSet<>();
            for (int id : members) {
                Body body = byId.get(id);
                if (body != null && body.capabilities().has(AssaultCapability.AIR_DIRECTION)) operators.add(id);
            }
            if (operators.isEmpty()) return;
            for (int nest : strikeNests) {
                if (!belief.live(nest)) continue;
                if (strikeAsked.contains(nest)) {
                    // Asked and the flight is over with the nest still standing: it may be asked again.
                    if (strikeFlight.getOrDefault(nest, Integer.MAX_VALUE) > tick) continue;
                    strikeAsked.remove(nest);
                    strikeFlight.remove(nest);
                }
                if (!belief.strikeOpen(nest, tick)) continue;
                belief.strikeAsked(nest);
                strikeAsked.add(nest);
                int[] cell = works.nests.get(nest);
                wishes.add(new StrikeWish(nest, cell[0], cell[1], operators));
            }
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
            return count(Role.FIRE);
        }

        int breachSquads() {
            return count(Role.BREACH);
        }

        private int count(Role role) {
            int n = 0;
            for (Role r : roles.values()) if (r == role) n++;
            return n;
        }

        /** The way in being taken, as a short phrase. */
        String variant() {
            return way.variant().name().toLowerCase(Locale.ROOT)
                    + (way.variant() == Variant.BREACH && means != null ? " (" + means.name().toLowerCase(Locale.ROOT) + ")" : "");
        }

        /** What the plan lacked of the company it was staffed with, as short sentences; empty when it was staffed. */
        List<String> shortfall() {
            return staffing == null ? List.of() : staffing.describeGaps();
        }

        String describe() {
            return step.name() + " via " + variant() + ", "
                    + liveCovering + " covering nests believed live, " + fireSquads() + " fire / "
                    + breachSquads() + " breach / " + (roles.size() - fireSquads() - breachSquads()) + " assault squads";
        }

        /**
         * One pulse. Returns the orders by squad, or null when the aspect cannot be reached, which the caller treats as
         * the company having nothing left to take.
         */
        Map<Integer, Order> run(int tick, Faction perspective, Objective objective, Set<Integer> members,
                                Map<Integer, Body> byId, CommandTopology topology, List<String> notes) {
            assignRoles(members, byId);
            if (revalidateBreachers(tick, members, byId, topology, notes)) {
                return run(tick, perspective, objective, members, byId, topology, notes);
            }
            liveCovering = belief.liveCovering(way);
            List<Integer> fire = new ArrayList<>();
            List<Integer> breach = new ArrayList<>();
            List<Integer> assault = new ArrayList<>();
            sortRoles(fire, breach, assault);
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
            for (int id : breach) {
                if (!breachPositions.containsKey(id)) {
                    int[] cell = breachPosition(byId.get(id), topology);
                    if (cell == null) {
                        roles.put(id, Role.ASSAULT);
                        notes.add("squad " + id + " has no position to open the wall from; it goes in with the assault");
                    } else {
                        breachPositions.put(id, cell);
                    }
                }
            }
            sortRoles(fire, breach, assault);
            satchelRecord.baseOfFire.clear();
            for (int id : fire) {
                Integer placed = fireNests.get(id);
                if (placed != null && belief.live(placed)) satchelRecord.baseOfFire.put(id, works.nests.get(placed));
            }
            if (satchelStep(tick, members, fire, breach, byId, topology, notes)) {
                return run(tick, perspective, objective, members, byId, topology, notes);
            }

            if (step != Step.OBJECTIVE && radarFirst(perspective, objective)) {
                // The wall will not be cut first: the breachers go in with the assault (a machine keeps its place as fire), or they
                // would hold the role the objective step gives no order to.
                convertBreachers(breach, byId);
                go(Step.OBJECTIVE, tick, notes, "radar first: the company is being flown at and the bunker's radar spots for it");
            }
            advance(tick, fire, breach, assault, byId, topology, notes);
            sortRoles(fire, breach, assault);
            planStrikes(tick, members, byId);

            Map<Integer, Order> orders = new TreeMap<>();
            switch (step) {
                case POSITION, SUPPRESS -> {
                    for (int id : fire) orders.put(id, fireOrder(id));
                    int[] wall = breachCell(topology);
                    for (int id : breach) {
                        if (breachMayGo(tick, id, fire.isEmpty(), byId)) {
                            orders.put(id, breachOrder(id, wall));
                        } else {
                            // A held sapper advances to the last cell of its route no live nest sees and waits there.
                            int[] edge = means == BreachMeans.CLOSE ? holdEdge.get(id) : null;
                            int holdX = edge == null ? assembly[0] : edge[0];
                            int holdY = edge == null ? assembly[1] : edge[1];
                            orders.put(id, new Order(ObjectiveAssignment.advanceTrack(id, holdX, holdY),
                                    AssignmentReason.ASSAULT_STAGE));
                        }
                    }
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
                            perspective, objective.aspects(), candidates, topology, skyNeed);
                    if (tasks.isEmpty()) return candidates.isEmpty() ? orders : null;
                    int[] aspect = {tasks.get(0).structure().cellX(), tasks.get(0).structure().cellY()};
                    boolean turned = heldAspect != null && (heldAspect[0] != aspect[0] || heldAspect[1] != aspect[1]);
                    if (turned && !stillHostile(perspective, objective, heldAspect)) {
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
                    if (heldAspect == null) notes.add("company takes aim at the aspect at (" + aspect[0] + "," + aspect[1] + "): "
                            + tasks.get(0).reason());
                    else if (turned) notes.add("company turns from the aspect at (" + heldAspect[0] + "," + heldAspect[1]
                            + ") to the one at (" + aspect[0] + "," + aspect[1] + "), which is still work: " + tasks.get(0).reason());
                    heldAspect = aspect;
                    for (DefenceStructureTactics.Task task : tasks) {
                        orders.put(task.squadId(), new Order(task.assignment(), AssignmentReason.ASSAULT_COMMIT));
                    }
                }
            }
            for (Map.Entry<Integer, Order> smoke : smokeOrders.entrySet()) {
                if (roles.get(smoke.getKey()) != Role.BREACH && members.contains(smoke.getKey())) orders.put(smoke.getKey(), smoke.getValue());
            }
            return orders;
        }

        /** Whether the aspect at a cell is still disclosed and not ours: work to do, not a fallen aspect to hold. */
        private static boolean stillHostile(Faction perspective, Objective objective, int[] cell) {
            for (DefenceStructureFact aspect : objective.aspects()) {
                if (aspect.cellX() == cell[0] && aspect.cellY() == cell[1] && perspective.hostileTo(aspect.holder())) return true;
            }
            return false;
        }

        private Order fireOrder(int id) {
            int[] cell = firePositions.get(id);
            ObjectiveAssignment order = ObjectiveAssignment.attackMove(id, cell[0], cell[1]);
            int[] nest = missionNest(id);
            // The squad's crew-served tubes are told which nest to fire on; a squad with none carries the order unused.
            if (nest != null) order = order.withFireMission(new FireMission(nest[0], nest[1], FIRE_MISSION_RADIUS));
            return new Order(order, AssignmentReason.ASSAULT_BASE_OF_FIRE);
        }

        /**
         * The nest a fire squad's tubes are told to fire on: the one it was placed against while that is believed live,
         * else the first nest covering the way in that is. Own-force knowledge of the fort's disclosed works; whether the
         * side has seen anything there is the tube's concern, not the plan's.
         */
        private int[] missionNest(int id) {
            Integer placed = fireNests.get(id);
            if (placed != null && belief.live(placed)) return works.nests.get(placed);
            for (int i : way.covering()) if (belief.live(i)) return works.nests.get(i);
            return null;
        }

        /**
         * A breacher's order: to its place, carrying the wall cell to open. With the wall already open (or no wall to
         * open) it is only its place.
         */
        private Order breachOrder(int id, int[] wall) {
            int[] cell = breachPositions.get(id);
            ObjectiveAssignment order = ObjectiveAssignment.attackMove(id, cell[0], cell[1]);
            if (wall != null) order = order.withBreach(wall[0], wall[1]);
            return new Order(order, AssignmentReason.ASSAULT_BREACH);
        }

        /**
         * Whether a breacher is let go to the wall now. A breacher is the first thing to reach the wall and the last thing
         * to leave it. A <b>close</b> breacher (a sapper with a charge) stands under every gun that can see its
         * approach for as long as it takes to set the charge, so it is let go only when the approach is survivable
         * ({@link SatchelApproach}): each nest believed live that reaches the route is dead, suppressed, screened or out of
         * sight. There is no window after which it goes anyway; a company that cannot make the approach survivable changes its
         * approach instead ({@link #changeApproach}). A distance breacher stands off, so it keeps the earlier rule: it waits
         * with the assault until the base of fire has had its suppression window, the covering nests are believed dead, or
         * there is no fire element to wait for, and a breacher whose place is out of every live nest's reach has nothing to
         * wait for. Once let go it stays let go.
         */
        private boolean breachMayGo(int tick, int id, boolean noFireElement, Map<Integer, Body> byId) {
            if (breachGo.containsKey(id)) return true;
            int[] place = breachPositions.get(id);
            boolean go;
            if (means == BreachMeans.CLOSE) {
                go = satchelClear;
            } else {
                go = liveCovering == 0 || noFireElement || (place != null && !inNestReach(place[0], place[1]))
                        || (step == Step.SUPPRESS && tick - stepTick >= suppressWindow(tick));
            }
            if (!go) return false;
            breachGo.put(id, tick);
            Body body = byId.get(id);
            double walk = body == null || place == null ? 0 : Math.hypot(body.anchorX() - place[0], body.anchorY() - place[1]);
            breachWalkUntil = Math.max(breachWalkUntil, tick + (int) Math.ceil(walk * BREACH_WALK_TICKS_PER_CELL) + BREACH_WALK_SLACK_TICKS);
            return true;
        }

        // ---- the satchel approach

        /** Nests already read dead when the hold began, so deaths while held can be told from those before; -1 before. */
        private int deadAtHold = -1;

        /**
         * One pulse of the decision whether the held close breachers may go: read each nest against the route they would
         * walk, plan the cheapest enabling action for each that is open, begin them, and, when the company has waited out
         * its patience with nothing further answered, change approach. Returns true when the approach changed (the pulse
         * is then planned again from the new one). Sets {@link #satchelClear} for {@link #breachMayGo}.
         */
        private boolean satchelStep(int tick, Set<Integer> members, List<Integer> fire, List<Integer> breach,
                                    Map<Integer, Body> byId, CommandTopology topology, List<String> notes) {
            satchelClear = false;
            smokeOrders.clear();
            if (means != BreachMeans.CLOSE || way.variant() != Variant.BREACH || assembly == null
                    || (step != Step.POSITION && step != Step.SUPPRESS)) return false;
            List<Integer> held = new ArrayList<>();
            for (int id : breach) if (!breachGo.containsKey(id) && breachPositions.containsKey(id)) held.add(id);
            if (held.isEmpty()) return false;
            List<int[]> route = new ArrayList<>();
            holdEdge.clear();
            List<int[]> places = new ArrayList<>();
            for (int id : held) places.add(breachPositions.get(id));
            heldPlaces = places;
            SatchelApproach.Sight sight = (x0, y0, x1, y1) -> sight(topology, x0, y0, x1, y1);
            for (int id : held) {
                int[] place = breachPositions.get(id);
                // The walked route from where the squad stands, not the straight line: a fort stands between a squad and its mouth,
                // and the way round it is the part that is under the guns. The squad advances along it while no live nest sees it
                // and holds at the last such cell; only the stretch beyond is the approach to be made survivable.
                Body body = byId.get(id);
                int fromX = body == null ? assembly[0] : body.anchorX();
                int fromY = body == null ? assembly[1] : body.anchorY();
                List<int[]> walked = walkedRoute(topology, fromX, fromY, place[0], place[1]);
                int first = firstExposed(walked, sight);
                holdEdge.put(id, first <= 0 ? new int[] {fromX, fromY} : walked.get(first - 1));
                route.addAll(first < 0 ? List.of() : walked.subList(first, walked.size()));
            }
            refreshEngagement(tick, fire, byId);
            int walkTicks = route.size() * SatchelApproach.ROUTE_STEP_CELLS * WALK_TICKS_PER_CELL + PLANT_DWELL_TICKS;
            crossingTicks = walkTicks;
            exposedCells.clear();
            List<SatchelApproach.Reading> readings = new ArrayList<>();
            Set<Integer> exposedNests = new TreeSet<>();
            int dead = 0;
            for (int i = 0; i < works.nests.size(); i++) {
                SatchelApproach.Reading reading = SatchelApproach.read(i, works.nests.get(i), belief.live(i), route,
                        NEST_REACH_CELLS, sight, nestSettled(i, tick), clouds, tick, walkTicks);
                if (belief.live(i)) exposedCells.put(i, SatchelApproach.exposed(works.nests.get(i), route, NEST_REACH_CELLS, sight));
                readings.add(reading);
                if (reading.answer() == SatchelApproach.Answer.DEAD) dead++;
                if (belief.live(i) && reading.answer() != SatchelApproach.Answer.HIDDEN) exposedNests.add(i);
            }
            if (satchelHeldSince < 0) {
                satchelHeldSince = tick;
                satchelProgress = tick;
                satchelRecord.heldSinceTick = tick;
                satchelRecord.lastProgressTick = tick;
                if (deadAtHold < 0) deadAtHold = dead;
            }
            satchelRecord.lastHeldTick = tick;
            satchelRecord.nestsDiedWhileHeld = Math.max(satchelRecord.nestsDiedWhileHeld, dead - deadAtHold);
            int answered = SatchelApproach.answered(readings);
            if (answered > satchelAnswered) {
                satchelAnswered = answered;
                satchelRecord.lastProgressTick = tick;
                satchelProgress = tick;
            }
            SatchelResources resources = new SatchelResources(tick, members, fire, byId, topology, exposedNests, readings);
            SatchelApproach.Plan plan = SatchelApproach.plan(readings, resources);
            satchelRecord.lastReadings = describe(readings, plan, fire, byId, tick);
            upkeepSmoke(tick, readings);
            resources.reweigh();
            if (plan.verdict() == SatchelApproach.Verdict.GO) {
                satchelClear = true;
                satchelRecord.releasedTick = tick;
                satchelRecord.openAtRelease = 0;
                satchelRecord.answersAtRelease.clear();
                for (SatchelApproach.Reading reading : readings) satchelRecord.answersAtRelease.merge(reading.answer(), 1, Integer::sum);
                notes.add("the approach to the wall is survivable at tick " + tick + " (" + satchelRecord.answersAtRelease
                        + "); the sappers are let go");
                return false;
            }
            for (SatchelApproach.Move move : plan.moves()) {
                if (resources.begin(move, notes)) satchelRecord.asked.merge(move.enabler(), 1, Integer::sum);
            }
            if (!plan.unservable().isEmpty()) satchelRecord.unservablePulses++;
            if (!plan.pending().isEmpty()) satchelRecord.pendingPulses++;
            resources.orderSmoke();
            if (SatchelApproach.patienceSpent(satchelProgress, tick)) return changeApproach(tick, byId, notes, plan);
            return false;
        }

        /** Index of the first cell of a route that some live nest reaches and sees, or -1 when none does. */
        private int firstExposed(List<int[]> walked, SatchelApproach.Sight sight) {
            for (int k = 0; k < walked.size(); k++) {
                for (int i = 0; i < works.nests.size(); i++) {
                    if (!belief.live(i)) continue;
                    int[] nest = works.nests.get(i);
                    int[] cell = walked.get(k);
                    if (Math.hypot(cell[0] - nest[0], cell[1] - nest[1]) <= NEST_REACH_CELLS && sight.clear(cell[0], cell[1], nest[0], nest[1])) return k;
                }
            }
            return -1;
        }

        private String describe(List<SatchelApproach.Reading> readings, SatchelApproach.Plan plan, List<Integer> fire,
                                Map<Integer, Body> byId, int tick) {
            StringBuilder out = new StringBuilder("tick " + tick + ": ");
            for (SatchelApproach.Reading reading : readings) {
                int[] cell = works.nests.get(reading.nest());
                out.append("n").append(reading.nest()).append("@").append(cell[0]).append(",").append(cell[1]).append("=")
                        .append(reading.answer() == null ? "OPEN" : reading.answer()).append(" ");
            }
            out.append("| smoke ").append(satchelRecord.smokeNotes).append(" ");
            out.append("| crossing ").append(crossingTicks).append(" ticks over ").append(exposedCells.values().stream().mapToInt(List::size).max().orElse(0)).append(" exposed cells ");
            out.append("| moves ").append(plan.moves()).append(" pending ").append(plan.pending()).append(" unservable ")
                    .append(plan.unservable()).append(" | fire ");
            for (int id : fire) {
                int[] position = firePositions.get(id);
                out.append("sq").append(id).append("->n").append(fireNests.get(id)).append("@")
                        .append(position == null ? "?" : position[0] + "," + position[1]);
                Body body = byId.get(id);
                out.append(body == null ? "(gone) " : "(at " + body.anchorX() + "," + body.anchorY() + " alive " + body.alive() + ") ");
            }
            return out.toString();
        }

        /** The sampled cells of the route the pathfinder finds between two cells, or the straight line when it finds none. */
        private static List<int[]> walkedRoute(CommandTopology topology, int fromX, int fromY, int toX, int toY) {
            int[] path = topology.route(fromX, fromY, toX, toY);
            return path.length == 0 ? SatchelApproach.route(fromX, fromY, toX, toY) : SatchelApproach.routeOf(path);
        }


        /**
         * Retires smoke jobs that have done their work or run out of time: a nest that now reads answered needs no screen, and a
         * job that has had its {@link #SMOKE_JOB_TICKS} without the nest reading blinded counts as a failed try.
         */
        private void upkeepSmoke(int tick, List<SatchelApproach.Reading> readings) {
            java.util.Iterator<SmokeJob> jobs = smokeJobs.values().iterator();
            while (jobs.hasNext()) {
                SmokeJob job = jobs.next();
                SatchelApproach.Reading reading = readings.get(job.nest());
                if (!reading.open()) {
                    satchelRecord.smokeLog.add("tick " + tick + ": screen for n" + job.nest() + " retired, the nest reads " + reading.answer());
                    jobs.remove();
                } else if (tick - job.startedTick() >= job.allowedTicks()) {
                    satchelRecord.smokeLog.add("tick " + tick + ": screen for n" + job.nest() + " timed out after " + job.allowedTicks() + " ticks with "
                            + standing(job, tick) + " of " + job.points().size() + " clouds standing");
                    smokeTries.merge(job.nest(), 1, Integer::sum);
                    jobs.remove();
                }
            }
        }

        /** How many of a screen's points have a cloud standing on them now. */
        private int standing(SmokeJob job, int tick) {
            int n = 0;
            for (int[] point : job.points()) {
                for (SatchelApproach.Cloud cloud : clouds) {
                    if (cloud.untilTick() > tick && Math.hypot(cloud.x() - (point[0] + 0.5), cloud.y() - (point[1] + 0.5)) <= SMOKE_STANDS_CELLS) {
                        n++;
                        break;
                    }
                }
            }
            return n;
        }

        /** Whether a squad in position has been engaging this nest for the settle time. */
        private boolean nestSettled(int nest, int tick) {
            Integer since = engagedSince.get(nest);
            return since != null && tick - since >= SatchelApproach.SUPPRESSION_SETTLE_TICKS;
        }

        /** Notes which nests a fire squad is standing in place against, and since when. */
        private void refreshEngagement(int tick, List<Integer> fire, Map<Integer, Body> byId) {
            Set<Integer> engaged = new TreeSet<>();
            for (int id : fire) {
                Integer nest = fireNests.get(id);
                if (nest == null || !belief.live(nest) || arrivedShare(List.of(id), i -> firePositions.get(i), byId) < 1f) continue;
                engaged.add(nest);
            }
            engagedSince.keySet().retainAll(engaged);
            for (int nest : engaged) engagedSince.putIfAbsent(nest, tick);
        }

        /**
         * The company has held its sappers as long as its slowest enabling action takes to show and nothing further has been
         * answered: it takes another approach. First the same wall from range, if the company can now staff that (a lance
         * that landed since the commit); else the least covered gate, with the sappers going in as infantry among the
         * assault. With neither, it keeps holding: the wall is the only way in and a sapper walked into live guns opens
         * nothing.
         */
        private boolean changeApproach(int tick, Map<Integer, Body> byId, List<String> notes, SatchelApproach.Plan plan) {
            int heldFor = satchelHeldSince < 0 ? 0 : tick - satchelHeldSince;
            Map<Integer, CapabilityProfile> profiles = new TreeMap<>();
            for (int id : roles.keySet()) if (byId.containsKey(id)) profiles.put(id, byId.get(id).capabilities());
            Way next = null;
            BreachMeans nextMeans = null;
            String why;
            if (AssaultRoles.assign(needs(way, BreachMeans.DISTANCE, belief.liveCovering(way)), profiles).staffed()) {
                next = way;
                nextMeans = BreachMeans.DISTANCE;
                why = "the same wall from range, a standoff breacher being in the company";
            } else {
                int bestCover = Integer.MAX_VALUE;
                for (Way candidate : works.ways) {
                    if (candidate.variant() == Variant.BREACH) continue;
                    int cover = belief.liveCovering(candidate);
                    if (next == null || cover < bestCover) {
                        next = candidate;
                        bestCover = cover;
                    }
                }
                why = next == null ? "" : "the gate at (" + next.mouthX() + "," + next.mouthY() + ") with " + bestCover
                        + " nests believed to cover it";
            }
            if (next == null) {
                notes.add("the sappers have held " + heldFor + " ticks with open nests " + (plan == null ? List.of() : plan.unservable())
                        + " and no other approach to take; they keep holding");
                satchelProgress = tick;
                return false;
            }
            notes.add("assault plan changes approach at tick " + tick + " after " + heldFor
                    + (plan == null ? " ticks, the breachers having lost their kit: " : " ticks holding the sappers with no further nest answered: ") + why);
            satchelRecord.approachChangedTick = tick;
            satchelRecord.approachChange = why;
            way = next;
            means = nextMeans;
            roles.clear();
            firePositions.clear();
            fireNests.clear();
            forcedFire.clear();
            engagedSince.clear();
            breachPositions.clear();
            breachGo.clear();
            breachArrivedTick = -1;
            breachWalkUntil = -1;
            assembly = null;
            commitTick = tick;
            satchelHeldSince = -1;
            satchelProgress = -1;
            satchelAnswered = -1;
            go(Step.POSITION, tick, notes, "the approach changed");
            return true;
        }

        /** What the company has to answer an open nest with, read and acted on for one pulse. */
        private final class SatchelResources implements SatchelApproach.Resources {
            private final int tick;
            private final Set<Integer> members;
            private final List<Integer> fire;
            private final Map<Integer, Body> byId;
            private final CommandTopology topology;
            private final Set<Integer> exposedNests;
            private final List<SatchelApproach.Reading> readings;
            private final Map<Integer, Integer> engagers = new TreeMap<>();
            private final Set<Integer> retasked = new TreeSet<>();

            SatchelResources(int tick, Set<Integer> members, List<Integer> fire, Map<Integer, Body> byId,
                             CommandTopology topology, Set<Integer> exposedNests, List<SatchelApproach.Reading> readings) {
                this.readings = readings;
                this.tick = tick;
                this.members = members;
                this.fire = fire;
                this.byId = byId;
                this.topology = topology;
                this.exposedNests = exposedNests;
            }

            @Override
            public boolean can(SatchelApproach.Enabler enabler, int nest) {
                return switch (enabler) {
                    case ENGAGE -> engager(nest) >= 0;
                    case STRIKE -> strikeOffered(nest);
                    case SMOKE -> smokeOffered(nest);
                };
            }

            @Override
            public boolean pending(SatchelApproach.Enabler enabler, int nest) {
                return switch (enabler) {
                    case ENGAGE -> {
                        boolean assigned = false;
                        for (int id : fire) assigned |= byId.containsKey(id) && Integer.valueOf(nest).equals(fireNests.get(id));
                        yield assigned;
                    }
                    case STRIKE -> strikeAsked.contains(nest) && strikeFlight.getOrDefault(nest, Integer.MAX_VALUE) > tick;
                    case SMOKE -> smokeJobs.containsKey(nest);
                };
            }

            private boolean strikeOffered(int nest) {
                if (!strikes.air().any() || !belief.strikeOpen(nest, tick)) return false;
                int[] cell = works.nests.get(nest);
                if (!strikes.air().acceptable(cell[0], cell[1])) return false;
                for (int id : members) {
                    Body body = byId.get(id);
                    if (body != null && body.capabilities().has(AssaultCapability.AIR_DIRECTION)) return true;
                }
                return false;
            }

            /**
             * The fire squad that could be put against this nest without leaving another exposed nest unengaged: one whose
             * nest is dead, off the route's sight, or already engaged by another squad; with a firing position against the
             * nest; the nearest to it. -1 when there is none.
             */
            private int engager(int nest) {
                Integer cached = engagers.get(nest);
                if (cached != null) return cached;
                int best = -1;
                double bestGap = Double.MAX_VALUE;
                int[] cell = works.nests.get(nest);
                for (int id : fire) {
                    Body body = byId.get(id);
                    if (body == null || retasked.contains(id) || !firePositions.containsKey(id)) continue;
                    Integer current = fireNests.get(id);
                    if (current != null && current == nest) continue;
                    boolean spare = current == null || !belief.live(current) || !exposedNests.contains(current)
                            || sharedWith(id, current);
                    if (!spare) continue;
                    double gap = Math.hypot(body.anchorX() - cell[0], body.anchorY() - cell[1]);
                    if (gap >= bestGap || firePositionAgainst(body, nest, topology) == null) continue;
                    best = id;
                    bestGap = gap;
                }
                engagers.put(nest, best);
                return best;
            }

            /** Whether a fire squad can be spared from the nest it is on: it is dead, off the route's sight, or doubled up. */
            private boolean spare(int id) {
                Integer current = fireNests.get(id);
                return current == null || !belief.live(current) || !exposedNests.contains(current) || sharedWith(id, current);
            }

            /**
             * The squads that could throw smoke now: any of the company's that is not a held breacher, carries grenades, and is the
             * assault element waiting at the staging cell or a fire squad that can be spared. Nearest to the staging cell first.
             */
            private List<Integer> grenadiers(int minLifeTicks) {
                List<Integer> out = new ArrayList<>();
                for (int id : members) {
                    Body body = byId.get(id);
                    Role role = roles.get(id);
                    if (body == null || role == null || role == Role.BREACH || !body.capabilities().has(AssaultCapability.SMOKE_SCREEN)) continue;
                    if (role == Role.FIRE && !spare(id)) continue;
                    if (body.capabilities().screenSeconds() * TICKS_PER_SECOND < minLifeTicks) continue;
                    out.add(id);
                }
                out.sort(Comparator.<Integer>comparingDouble(id -> Math.hypot(byId.get(id).anchorX() - assembly[0],
                        byId.get(id).anchorY() - assembly[1])).thenComparingInt(id -> id));
                return out;
            }

            private float grenades(List<Integer> squads) {
                float n = 0f;
                for (int id : squads) n += byId.get(id).capabilities().reserve(AssaultCapability.SMOKE_SCREEN);
                return n;
            }

            private List<int[]> screenFor(int nest, double radius) {
                List<int[]> cells = exposedCells.get(nest);
                if (cells == null || cells.isEmpty()) return List.of();
                return SatchelApproach.screenPoints(works.nests.get(nest), cells, radius);
            }

            /** The radius every one of these squads' clouds holds, at least: the smallest of theirs (the grenade's when a squad does not say). */
            private double radiusOf(List<Integer> throwers) {
                double radius = Double.MAX_VALUE;
                for (int id : throwers) {
                    float own = byId.get(id).capabilities().screenRadius();
                    radius = Math.min(radius, own > 0f ? own : CLOUD_RADIUS_CELLS);
                }
                return radius == Double.MAX_VALUE ? CLOUD_RADIUS_CELLS : radius;
            }

            private boolean smokeOffered(int nest) {
                if (smokeTries.getOrDefault(nest, 0) >= SMOKE_ATTEMPTS || smokeJobs.containsKey(nest)) {
                    return noSmoke(nest, smokeJobs.containsKey(nest) ? "a screen is already being stood" : "tried " + SMOKE_ATTEMPTS + " times");
                }
                // A cloud holds its radius only so long: the screen has to be one that stands the whole crossing, which the
                // hand grenade's twelve seconds seldom does.
                List<Integer> throwers = grenadiers(crossingTicks);
                if (throwers.isEmpty()) {
                    List<Integer> any = grenadiers(0);
                    if (!any.isEmpty()) satchelRecord.smokeTooShortPulses++;
                    return noSmoke(nest, any.isEmpty() ? "no squad free to throw" : "no cloud lasts " + crossingTicks + " ticks");
                }
                List<int[]> points = screenFor(nest, radiusOf(throwers));
                if (points.isEmpty() || points.size() > MAX_SCREEN_CLOUDS) return noSmoke(nest, points.size() + " clouds wanted");
                if (grenades(throwers) < points.size()) return noSmoke(nest, points.size() + " clouds wanted, " + grenades(throwers) + " throws");
                SatchelApproach.Refusal refusal = weighScreen(nest, points, throwers);
                if (refusal != null) {
                    satchelRecord.refused.merge(refusal, 1, Integer::sum);
                    return noSmoke(nest, refusal.name());
                }
                return true;
            }

            /**
             * Ticks a screen is given to stand: the base allowance, the walk of the nearest squad that can throw to the first point it
             * is wanted on, and a throw cycle for each further cloud (a squad throws one, walks, and throws the next).
             */
            private int smokeAllowance(int nest, List<int[]> points) {
                double nearest = Double.MAX_VALUE;
                for (int id : grenadiers(crossingTicks)) {
                    Body body = byId.get(id);
                    nearest = Math.min(nearest, Math.hypot(body.anchorX() - points.get(0)[0], body.anchorY() - points.get(0)[1]));
                }
                int walk = nearest == Double.MAX_VALUE ? 0 : (int) Math.round(nearest * WALK_TICKS_PER_CELL);
                return SMOKE_JOB_TICKS + walk + (points.size() - 1) * SMOKE_THROW_CYCLE_TICKS;
            }

            /** Notes why a nest has no smoke on offer, for the evidence report, and says so. */
            private boolean noSmoke(int nest, String why) {
                satchelRecord.smokeNotes.put(nest, why);
                return false;
            }

            /**
             * The other edge of a screen: whether laying it would cost the company a suppression it relies on or leave the sappers
             * alone beyond it. The clouds it would stand are as long-lived as the shortest of the throwers' cloud. The suppressions
             * relied on are every fire squad's placement against a nest believed live, answered yet or not (a squad on its way is
             * still the company's fire, and a cloud laid across its line is laid before it gets there), read from both where the plan
             * put it and where the squad stands now: a squad does not fire from the cell it was told to walk to.
             */
            private SatchelApproach.Refusal weighScreen(int nest, List<int[]> points, List<Integer> throwers) {
                int life = Integer.MAX_VALUE;
                for (int id : throwers) life = Math.min(life, Math.round(byId.get(id).capabilities().screenSeconds() * TICKS_PER_SECOND));
                return weighPoints(nest, points, life, (float) radiusOf(throwers));
            }

            private SatchelApproach.Refusal weighPoints(int nest, List<int[]> points, int life, float radius) {
                List<SatchelApproach.Cloud> proposed = new ArrayList<>();
                for (int[] point : points) proposed.add(new SatchelApproach.Cloud(point[0] + 0.5f, point[1] + 0.5f, radius, tick + life));
                List<SatchelApproach.Placement> placements = new ArrayList<>();
                List<int[]> support = new ArrayList<>();
                for (int id : fire) {
                    Body body = byId.get(id);
                    if (body == null) continue;
                    int[] actual = {body.anchorX(), body.anchorY()};
                    support.add(actual);
                    if (firePositions.get(id) != null) support.add(firePositions.get(id));
                    Integer placed = fireNests.get(id);
                    boolean live = placed != null && belief.live(placed);
                    placements.add(new SatchelApproach.Placement(live ? placed : -1, live ? works.nests.get(placed) : null,
                            firePositions.get(id), actual));
                }
                List<SatchelApproach.Reliance> reliances = SatchelApproach.reliances(placements);
                return SatchelApproach.weigh(nest, clouds, proposed, reliances, support, heldPlaces, NEST_REACH_CELLS,
                        (x0, y0, x1, y1) -> sight(topology, x0, y0, x1, y1), tick, crossingTicks);
            }

            /**
             * Re-weighs every screen still being stood against where the fire squads are now, and withdraws one that would now cut
             * a line the company relies on: a screen is ordered many pulses before its clouds are thrown, and the base of fire
             * moves in between. Clouds already standing stay; only the throws not yet made are cancelled.
             */
            void reweigh() {
                List<Integer> throwers = grenadiers(crossingTicks);
                if (throwers.isEmpty()) return;
                int life = Integer.MAX_VALUE;
                for (int id : throwers) life = Math.min(life, Math.round(byId.get(id).capabilities().screenSeconds() * TICKS_PER_SECOND));
                float radius = (float) radiusOf(throwers);
                Iterator<SmokeJob> jobs = smokeJobs.values().iterator();
                while (jobs.hasNext()) {
                    SmokeJob job = jobs.next();
                    List<int[]> remaining = new ArrayList<>();
                    for (int[] point : job.points()) if (!cloudStands(point)) remaining.add(point);
                    if (remaining.isEmpty()) continue;
                    SatchelApproach.Refusal refusal = weighPoints(job.nest(), remaining, life, radius);
                    if (refusal == null) continue;
                    satchelRecord.refused.merge(refusal, 1, Integer::sum);
                    satchelRecord.smokeLog.add("tick " + tick + ": screen for n" + job.nest() + " withdrawn with " + remaining.size() + " throws to make, " + refusal);
                    satchelRecord.smokeNotes.put(job.nest(), refusal.name());
                    smokeTries.merge(job.nest(), 1, Integer::sum);
                    jobs.remove();
                }
            }

            /**
             * Sends a grenadier to a cell within its throw of each point of a running screen not yet standing, each squad to one
             * point: the order carries the point to throw on and the cell to throw from.
             */
            void orderSmoke() {
                Set<Integer> busy = new TreeSet<>();
                for (SmokeJob job : smokeJobs.values()) {
                    int[] nest = works.nests.get(job.nest());
                    for (int[] point : job.points()) {
                        if (cloudStands(point)) continue;
                        int squad = -1;
                        for (int id : grenadiers(crossingTicks)) {
                            if (!busy.contains(id)) {
                                squad = id;
                                break;
                            }
                        }
                        if (squad < 0) break;
                        busy.add(squad);
                        double dx = point[0] - nest[0];
                        double dy = point[1] - nest[1];
                        double d = Math.max(1e-6, Math.hypot(dx, dy));
                        int[] from = walkableNear((int) Math.round(point[0] + dx / d * THROW_STANDOFF_CELLS),
                                (int) Math.round(point[1] + dy / d * THROW_STANDOFF_CELLS), byId.get(squad), topology);
                        if (from == null) continue;
                        if (smokeAssigned.add(squad + ":" + point[0] + "," + point[1])) {
                            satchelRecord.smokeLog.add("tick " + tick + ": squad " + squad + " sent to (" + from[0] + "," + from[1]
                                    + ") to throw on (" + point[0] + "," + point[1] + ")");
                        }
                        ObjectiveAssignment order = ObjectiveAssignment.attackMove(squad, from[0], from[1])
                                .withScreen(new ObjectiveAssignment.ScreenMission(point[0], point[1]));
                        smokeOrders.put(squad, new Order(order, AssignmentReason.ASSAULT_STAGE));
                    }
                }
            }

            private boolean cloudStands(int[] point) {
                for (SatchelApproach.Cloud cloud : clouds) {
                    if (cloud.untilTick() > tick && Math.hypot(cloud.x() - (point[0] + 0.5), cloud.y() - (point[1] + 0.5))
                            <= SMOKE_STANDS_CELLS) return true;
                }
                return false;
            }

            private boolean sharedWith(int id, int nest) {
                for (int other : fire) if (other != id && Integer.valueOf(nest).equals(fireNests.get(other))) return true;
                return false;
            }

            /** Begins a move; false when it could not be (the squad or the offer went). */
            boolean begin(SatchelApproach.Move move, List<String> notes) {
                int[] cell = works.nests.get(move.nest());
                switch (move.enabler()) {
                    case ENGAGE -> {
                        int squad = engager(move.nest());
                        if (squad < 0) return false;
                        retasked.add(squad);
                        forcedFire.put(squad, move.nest());
                        fireNests.put(squad, move.nest());
                        firePositions.put(squad, firePositionAgainst(byId.get(squad), move.nest(), topology));
                        engagedSince.remove(move.nest());
                        notes.add("the base of fire is told to engage the nest at (" + cell[0] + "," + cell[1] + ") with squad " + squad
                                + ": the sappers wait on it");
                        return true;
                    }
                    case STRIKE -> {
                        if (!strikeNests.contains(move.nest())) strikeNests.add(move.nest());
                        notes.add("a strike is wanted on the nest at (" + cell[0] + "," + cell[1] + ") before the sappers go");
                        return true;
                    }
                    case SMOKE -> {
                        List<int[]> points = screenFor(move.nest(), radiusOf(grenadiers(crossingTicks)));
                        if (points.isEmpty()) return false;
                        smokeJobs.put(move.nest(), new SmokeJob(move.nest(), points, tick, smokeAllowance(move.nest(), points)));
                        StringBuilder where = new StringBuilder();
                        for (int[] point : points) where.append("(").append(point[0]).append(",").append(point[1]).append(") ");
                        satchelRecord.smokeLog.add("tick " + tick + ": screen of " + points.size() + " clouds ordered for n" + move.nest() + " at " + where);
                        notes.add("a smoke screen of " + points.size() + " clouds is ordered for the nest at (" + cell[0] + "," + cell[1]
                                + ") before the sappers go");
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        }


        /** Ticks of suppression the assault gives its fire element: a strike on its way is waited for. */
        private int suppressWindow(int tick) {
            return SUPPRESS_MAX_TICKS + (strikeInFlight(tick) ? STRIKE_FLIGHT_TICKS : 0);
        }

        private void sortRoles(List<Integer> fire, List<Integer> breach, List<Integer> assault) {
            fire.clear();
            breach.clear();
            assault.clear();
            for (Map.Entry<Integer, Role> e : roles.entrySet()) {
                switch (e.getValue()) {
                    case FIRE -> fire.add(e.getKey());
                    case BREACH -> breach.add(e.getKey());
                    case ASSAULT -> assault.add(e.getKey());
                }
            }
        }

        /**
         * The wall cell the breach is to open now: the outermost cell of the cut, from the mouth inward, that is still a
         * wall. Null when the way in is not a breach, or when the cut is through. Read off the topology the commander
         * has, so a wall that falls is seen to fall.
         */
        int[] breachCell(CommandTopology topology) {
            if (way.variant() != Variant.BREACH) return null;
            for (int k = 1; k <= 8; k++) {
                int x = way.mouthX() - way.outX() * k;
                int y = way.mouthY() - way.outY() * k;
                if (!topology.inBounds(x, y)) return null;
                if (x == way.entryX() && y == way.entryY()) return null;
                if (!topology.isWalkable(x, y)) return new int[] {x, y};
            }
            return null;
        }

        /**
         * Once the way is made, a breacher who can enter a building goes in with the assault, and one who cannot (a machine)
         * keeps its place as a base of fire, following in only once no covering nest is left, as the fire element does.
         */
        private void convertBreachers(List<Integer> breach, Map<Integer, Body> byId) {
            for (int id : breach) {
                Body body = byId.get(id);
                if (body != null && body.capabilities().canAssault()) {
                    roles.put(id, Role.ASSAULT);
                } else {
                    roles.put(id, Role.FIRE);
                    int[] place = breachPositions.get(id);
                    if (place != null) firePositions.put(id, place);
                }
            }
        }

        /**
         * Where a breacher stands to open the wall. A distance breacher stands inside its weapon's reach from the cut,
         * outside the way in's face and in sight of it, and (where it can) out of the live nests' reach; a close breacher
         * stands at the mouth, against the wall. The one nearest the squad.
         */
        private int[] breachPosition(Body body, CommandTopology topology) {
            int[] wall = breachCell(topology);
            if (wall == null) return null;
            if (means == BreachMeans.CLOSE) {
                return walkableNear(way.mouthX(), way.mouthY(), body, topology);
            }
            float reach = body.capabilities().distanceBreachRange();
            int standoff = Math.max(6, Math.round(reach) - BREACH_STANDOFF_MARGIN);
            for (boolean clearOfNests : new boolean[] {true, false}) {
                for (boolean needSight : new boolean[] {true, false}) {
                    int[] best = null;
                    double bestGap = Double.MAX_VALUE;
                    for (int dy = -standoff; dy <= standoff; dy++) {
                        for (int dx = -standoff; dx <= standoff; dx++) {
                            double d = Math.hypot(dx, dy);
                            if (d < standoff - BREACH_BAND || d > standoff || dx * way.outX() + dy * way.outY() <= 0) continue;
                            int x = wall[0] + dx;
                            int y = wall[1] + dy;
                            if (!topology.inBounds(x, y) || !topology.isWalkable(x, y)
                                    || !topology.reachable(body.anchorX(), body.anchorY(), x, y)
                                    || (needSight && !sight(topology, x, y, wall[0], wall[1]))
                                    || (clearOfNests && inNestReach(x, y))) continue;
                            double gap = Math.hypot(x - body.anchorX(), y - body.anchorY());
                            if (gap < bestGap) {
                                bestGap = gap;
                                best = new int[] {x, y};
                            }
                        }
                    }
                    if (best != null) return best;
                }
            }
            return null;
        }

        private boolean inNestReach(int x, int y) {
            for (int i = 0; i < works.nests.size(); i++) {
                if (belief.live(i) && Math.hypot(works.nests.get(i)[0] - x, works.nests.get(i)[1] - y) <= NEST_REACH_CELLS) return true;
            }
            return false;
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

        private void advance(int tick, List<Integer> fire, List<Integer> breach, List<Integer> assault,
                             Map<Integer, Body> byId, CommandTopology topology, List<String> notes) {
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
                    // A strike on its way is waited for: the nest it was called on is not yet answered for, and the assault does
                    // not walk up under it.
                    int suppressMax = suppressWindow(tick);
                    boolean suppressed = liveCovering == 0 || fire.isEmpty() || elapsed >= suppressMax;
                    // The wall's own time runs from a breacher standing at it, not from the step's start: the breachers are held
                    // back until the covering nests have had their suppression and then walk to the wall, which a fort's far
                    // side makes a long way. The walk has its own bound.
                    if (breachArrivedTick < 0) {
                        for (int id : breach) {
                            if (breachGo.containsKey(id) && arrivedShare(List.of(id), i -> breachPositions.get(i), byId) > 0f) {
                                breachArrivedTick = tick;
                            }
                        }
                    }
                    boolean breachHeld = false;
                    for (int id : breach) breachHeld |= !breachGo.containsKey(id);
                    boolean walled = way.variant() == Variant.BREACH && !breach.isEmpty() && breachCell(topology) != null
                            && (breachHeld
                            || (breachArrivedTick < 0 ? tick < breachWalkUntil : tick - breachArrivedTick < BREACH_TIMEOUT_TICKS));
                    if (assembled && suppressed && !walled) {
                        convertBreachers(breach, byId);
                        go(Step.ENTRY, tick, notes, way.variant() == Variant.BREACH && !breach.isEmpty() && breachCell(topology) == null
                                ? "the wall is open"
                                : liveCovering == 0 ? "the covering nests are believed dead"
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
         * Whether a squad still carries what its breach role needs, read off the live profile of its living members rather than the
         * one it was staffed with: a close breacher needs a charge, a distance breacher a wall-damaging gun.
         */
        private boolean carriesBreachKit(Body body) {
            if (body == null) return false;
            return means == BreachMeans.CLOSE ? body.capabilities().has(AssaultCapability.CLOSE_BREACH)
                    : body.capabilities().has(AssaultCapability.DISTANCE_BREACH);
        }

        /**
         * Roles are sticky, but a breach role is only as good as the kit behind it. A breacher not yet let go that has lost its kit (the
         * carrier fell on the walk up, or while held) cannot open the wall, and ordering it to would send the squad to a wall it can do
         * nothing to. It is returned to the assault (or the fire, for a machine); another squad of the company that carries the kit takes
         * the role, and when there is none the company changes approach rather than hold or send an empty squad. A breacher already
         * let go is left alone: a charge it has set has spent its kit by design. Returns true when the approach changed and the pulse
         * must be planned again.
         */
        private boolean revalidateBreachers(int tick, Set<Integer> members, Map<Integer, Body> byId, CommandTopology topology,
                                            List<String> notes) {
            if (way.variant() != Variant.BREACH || means == null || (step != Step.POSITION && step != Step.SUPPRESS)) return false;
            boolean wallStands = breachCell(topology) != null;
            if (!wallStands) return false;
            boolean capable = false;
            boolean lost = false;
            for (Map.Entry<Integer, Role> role : new ArrayList<>(roles.entrySet())) {
                if (role.getValue() != Role.BREACH) continue;
                int id = role.getKey();
                if (breachGo.containsKey(id) || carriesBreachKit(byId.get(id))) {
                    capable = true;
                    continue;
                }
                Body body = byId.get(id);
                roles.put(id, body != null && body.machine() ? Role.FIRE : Role.ASSAULT);
                breachPositions.remove(id);
                lost = true;
                satchelRecord.breachersLost++;
                notes.add("squad " + id + " no longer carries what opens the wall; it is taken off the breach");
                satchelRecord.breachLog.add("tick " + tick + ": squad " + id + " lost its kit before it was let go");
            }
            if (capable || !lost) return false;
            for (int id : members) {
                Body body = byId.get(id);
                if (roles.get(id) != Role.ASSAULT || !carriesBreachKit(body)) continue;
                roles.put(id, Role.BREACH);
                notes.add("squad " + id + " carries the kit and takes over the breach");
                satchelRecord.breachLog.add("tick " + tick + ": squad " + id + " takes over the breach");
                return false;
            }
            return changeApproach(tick, byId, notes, null);
        }

        /**
         * Sticky roles. The squads whose kit answers what the plan needs (anti-structure fire for the nests covering the
         * way in, a breach for a cut wall) take those roles first, by what they carry ({@link AssaultRoles#assign}); the
         * remaining machine squads are the fire element too; of the remaining infantry, when there are enough, a third
         * without demolition is a base of fire as well (the assault keeps the satchels). A squad that joins later goes with
         * the assault.
         */
        private void assignRoles(Set<Integer> members, Map<Integer, Body> byId) {
            roles.keySet().retainAll(members);
            firePositions.keySet().retainAll(members);
            fireNests.keySet().retainAll(members);
            breachPositions.keySet().retainAll(members);
            boolean first = roles.isEmpty();
            if (first) {
                Map<Integer, CapabilityProfile> profiles = new TreeMap<>();
                for (int id : members) if (byId.containsKey(id)) profiles.put(id, byId.get(id).capabilities());
                staffing = AssaultRoles.assign(needs(way, means, belief.liveCovering(way)), profiles);
                for (Map.Entry<Integer, AssaultRoles.Role> e : staffing.roles().entrySet()) {
                    // The entry need is judged for staffing only: whoever is not given another role goes in.
                    if (e.getValue() == AssaultRoles.Role.BASE_OF_FIRE) roles.put(e.getKey(), Role.FIRE);
                    else if (e.getValue() == AssaultRoles.Role.BREACH) roles.put(e.getKey(), Role.BREACH);
                }
            }
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
            Integer forced = forcedFire.get(body.squadId());
            if (forced != null && !belief.live(forced)) {
                forcedFire.remove(body.squadId());
                forced = null;
            }
            List<Integer> targets = new ArrayList<>();
            if (forced != null) targets.add(forced);
            else for (int i : way.covering()) if (belief.live(i)) targets.add(i);
            if (targets.isEmpty()) return null;
            int nestIndex = targets.get(turn % targets.size());
            int[] cell = firePositionAgainst(body, nestIndex, topology);
            if (cell != null || forced == null) fireNests.put(body.squadId(), nestIndex);
            return cell;
        }

        /**
         * A firing position for a squad against one nest, with no effect on the plan: a reachable cell at the squad's
         * standoff from the nest on its outer side, clear of the approach lane, with sight of it where there is such a cell.
         */
        private int[] firePositionAgainst(Body body, int nestIndex, CommandTopology topology) {
            int[] nest = works.nests.get(nestIndex);
            // Fire from beyond the nests' reach: a machine does, and so does any squad whose anti-structure fire is arcing
            // (a served mortar tube reaches fifty cells, a nest twenty-four).
            boolean standoffFire = body.machine() || body.capabilities().standoffFirst();
            int standoff = standoffFire ? FIRE_STANDOFF_MACHINE : FIRE_STANDOFF_INFANTRY;
            // Stand inside the reach of what the squad carries: a gun that cannot reach the nest from its place is no base of fire.
            float reach = body.capabilities().structureFireRange();
            if (reach > 0f) standoff = Math.max(MIN_FIRE_STANDOFF, Math.min(standoff, Math.round(reach) - 2));
            int band = standoffFire ? FIRE_BAND_MACHINE : FIRE_BAND_INFANTRY;
            for (boolean needSight : new boolean[] {true, false}) {
                int[] best = null;
                double bestGap = Double.MAX_VALUE;
                double ox = nest[0] - works.centreX;
                double oy = nest[1] - works.centreY;
                for (int dy = -standoff; dy <= standoff; dy++) {
                    for (int dx = -standoff; dx <= standoff; dx++) {
                        double d = Math.hypot(dx, dy);
                        if (d < standoff - band || d > standoff || dx * ox + dy * oy <= 0) continue;
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
        static boolean sight(CommandTopology topology, int x0, int y0, int x1, int y1) {
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
