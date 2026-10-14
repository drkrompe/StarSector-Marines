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
import java.util.EnumSet;
import java.util.HashMap;
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
    /** Wall damage per second a close breach (cutters, charges) asks for: one charge's worth. UNTUNED. */
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

    private FortAssault() {}

    enum Step { POSITION, SUPPRESS, ENTRY, OBJECTIVE, HOLD }

    enum Role { FIRE, BREACH, ASSAULT }

    /** How a breach is cut: from range with shells and rockets, or at the foot of the wall with charges and cutters. */
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
        /** The nest each fire squad was placed against, by index into the works. */
        private final Map<Integer, Integer> fireNests = new TreeMap<>();
        private final Map<Integer, int[]> breachPositions = new TreeMap<>();
        private final BreachMeans means;
        private Staffing staffing;
        private int[] assembly;
        private int[] heldAspect;
        private int[] fallen;
        private int liveCovering;
        /** The covering nests the company counted a strike for, the ones it has asked for and until when each is on its way. */
        private final List<Integer> strikeNests = new ArrayList<>();
        private final Set<Integer> strikeAsked = new TreeSet<>();
        private final Map<Integer, Integer> strikeFlight = new TreeMap<>();
        private final List<StrikeWish> wishes = new ArrayList<>();
        /** Whether the side is shut out of the sky by anti-air it knows; set by the muster each pulse. */
        private boolean skyWanted;

        void skyWanted(boolean skyWanted) {
            this.skyWanted = skyWanted;
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

            advance(tick, fire, breach, assault, byId, topology, notes);
            sortRoles(fire, breach, assault);
            planStrikes(tick, members, byId);

            Map<Integer, Order> orders = new TreeMap<>();
            switch (step) {
                case POSITION, SUPPRESS -> {
                    for (int id : fire) orders.put(id, fireOrder(id));
                    int[] wall = breachCell(topology);
                    for (int id : breach) orders.put(id, breachOrder(id, wall));
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
                            perspective, objective.aspects(), candidates, topology, skyWanted);
                    if (tasks.isEmpty()) return candidates.isEmpty() ? orders : null;
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
                    int suppressMax = SUPPRESS_MAX_TICKS + (strikeInFlight(tick) ? STRIKE_FLIGHT_TICKS : 0);
                    boolean suppressed = liveCovering == 0 || fire.isEmpty() || elapsed >= suppressMax;
                    boolean walled = way.variant() == Variant.BREACH && !breach.isEmpty() && breachCell(topology) != null
                            && elapsed < BREACH_TIMEOUT_TICKS;
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
            List<Integer> targets = new ArrayList<>();
            for (int i : way.covering()) if (belief.live(i)) targets.add(i);
            if (targets.isEmpty()) return null;
            int nestIndex = targets.get(turn % targets.size());
            int[] nest = works.nests.get(nestIndex);
            fireNests.put(body.squadId(), nestIndex);
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
