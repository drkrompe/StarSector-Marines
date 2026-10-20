package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A side's counter-attack on a fort it built and has lost or is losing (`conquest-command.md`, Defender command): the
 * structure planner's answer to a lost cause. Retaking is done in proportion or not at all, never by feeding single
 * squads into the works.
 *
 * <p>One group at a time, for one fort, through three stages:
 * <ul>
 *   <li><b>Reserve.</b> From the first believed contact of the battle (or a fort's alarm), while a fort of the side is
 *       within reach of the keep, {@link #RESERVE_SHARE} of the mobile pool waits at a staging cell behind it, on
 *       the line from the keep and clear of believed contact
 *       ({@link AssaultMuster#rallyCell}), rather than being spent at the fort. It is the counter-attack's core.</li>
 *   <li><b>Muster.</b> Once the fort's most critical aspect is held by the enemy and the fort is {@link #worth} it, the
 *       group recruits the nearest free squads at useful strength until it holds what the fort is believed to take,
 *       never the last free squad, and gathers at the staging cell.</li>
 *   <li><b>Launched.</b> When what stands at the staging cell reaches what the fort is believed to take, or everything
 *       the side can raise has arrived above the commit share, or the muster times out above it, every member is given
 *       {@link DefenceStructureTactics#planMassed}'s order on the same pulse. A group worn below
 *       {@link #BROKEN_FRACTION} of what it launched with, or one that has not won the critical aspect back within
 *       {@link #ASSAULT_TICKS}, has failed: its survivors fall back to the staging cell as the reserve and the fort is
 *       left for {@link #RETRY_TICKS}. After {@link #MAX_ATTEMPTS} failures (a muster that stands down is one) the fort
 *       is given up for good.</li>
 * </ul>
 *
 * <p>Belief only: who holds a fort's aspects is an own-force fact about the side's own places, what the fort takes is
 * the hostile movers it believes near the fort, and the keep's safety is the movers it believes near the keep. The keep
 * outranks every fort: a believed mover near it dissolves anything not already launched. Pure planning over frozen
 * inputs and deterministic; which squads may be offered is the caller's decision. Every figure marked UNTUNED is a
 * first pass.
 */
final class FortCounterattack {

    /** {@code -Dbattle.conquest.fortCounterattack=false} is the same-build control: no reserve, no group, single retakes as before. */
    static final String PROPERTY = "battle.conquest.fortCounterattack";

    private static volatile boolean enabled = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** A fort further than this from the keep does not guard it and is not worth leaving the keep for. UNTUNED. */
    static final int MAX_RETAKE_CELLS = 220;
    /** A believed hostile mover this close to the keep threatens it: no counter-attack forms or waits. UNTUNED. */
    static final int KEEP_GUARD_CELLS = 40;
    /** Believed movers within this many cells of a fort's centre are the strength it is believed held by. */
    static final int CONTACT_RADIUS_CELLS = AssaultMuster.CONTACT_RADIUS_CELLS;
    /**
     * The least a fort is believed to take, whoever is seen in it: two patrols. Three left the trial ground's default
     * garrison (four patrols) unable to go at all.
     */
    static final int MIN_STRENGTH = 12;
    /** Marines the group brings for each believed hostile at the fort: a proportionate answer, not a crushing one. UNTUNED. */
    static final float STRENGTH_RATIO = 1.0f;
    /** Each failed attempt raises what the fort is believed to take by this fraction. */
    static final float FAILURE_GROWTH = AssaultMuster.FAILURE_GROWTH;
    /** The share of what the fort takes the side must be able to raise, and have at the staging cell, to go. UNTUNED. */
    static final float COMMIT_SHARE = 0.75f;
    /** Ticks a muster waits for its recruits before it goes or stands down (2 min). UNTUNED. */
    static final int MUSTER_TICKS = 3600;
    /** A member within this many cells of the staging cell has arrived. */
    static final int RALLY_RADIUS_CELLS = AssaultMuster.RALLY_RADIUS_CELLS;
    /** Radius of the area defence a member holds at the staging cell. */
    static final int STAGE_RADIUS_CELLS = 4;
    /** A launched group below this fraction of the strength it launched with has failed. UNTUNED. */
    static final float BROKEN_FRACTION = 0.4f;
    /** Ticks a launched group has to win the fort's critical aspect back (2 min). UNTUNED. */
    static final int ASSAULT_TICKS = 3600;
    /** Ticks a fort is left after a failed attempt before the next muster (80 s). UNTUNED. */
    static final int RETRY_TICKS = 2400;
    /** Failed attempts after which a fort is given up for good. UNTUNED. */
    static final int MAX_ATTEMPTS = 2;
    /** Free squads a muster never recruits: the reserve law's floor. */
    static final int RESERVE_FLOOR = 1;
    /** The least mobile pool, in squads, that holds a reserve for a counter-attack at all. */
    static final int MIN_POOL_SQUADS = 2;
    /**
     * The counter-attack's reserve as a share of the mobile pool, above the reserve law's quarter: on a four-patrol
     * garrison a quarter is one patrol, which is no counter-attack.
     */
    static final float RESERVE_SHARE = 0.5f;

    /*
     * Evidence knobs (`-Dbattle.conquest.fortCounterattack.<name>`), read once, so a tuning variant is one command away
     * from its control. The defaults are the constants above.
     */
    private static final String KNOB = PROPERTY + ".";
    /** The counter-attack's reserve, as a share of the mobile pool, when larger than the reserve law's. */
    static volatile float reserveShare = knob("reserveShare", RESERVE_SHARE);
    /** The least a fort is believed to take. */
    static volatile int minStrength = (int) knob("minStrength", MIN_STRENGTH);
    /** When positive, the floor is at most this share of the largest mobile pool the side has fielded. */
    static volatile float floorShare = knob("floorShare", 0f);
    /** The share of what the fort takes the side must raise to go. */
    static volatile float commitShare = knob("commitShare", COMMIT_SHARE);
    /** The least mobile pool, in squads, that holds a reserve at all. */
    static volatile int minPoolSquads = (int) knob("minPoolSquads", MIN_POOL_SQUADS);
    /**
     * Whether the reserve forms behind a held fort once any hostile mover has been believed this battle, not only on the
     * fort's alarm: by the alarm, the patrols that meet the landing are already spent.
     */
    static volatile boolean formOnContact = knob("formOnContact", 1f) > 0f;
    /** Whether the reserve above the law's share may be lent to man its own fort's pressed belt while the fort holds. */
    static volatile boolean lendToBelt = knob("lendToBelt", 1f) > 0f;

    /** This pulse's lendable members ({@link #lendable()}). */
    private List<Integer> lendable = List.of();

    private static float knob(String name, float fallback) {
        String value = System.getProperty(KNOB + name);
        return value == null ? fallback : Float.parseFloat(value);
    }

    /** Whether any hostile mover has been believed this battle: the attack has begun, and a quiet pulse does not end it. */
    private boolean contactSeen;

    /** The largest mobile pool strength seen, in marine-equivalents: what a garrison-scaled floor is measured against. */
    private float peakPool;

    enum Stage { RESERVE, MUSTER, LAUNCHED }

    /** How a fort the side built stands: whole in its hands, its most critical aspect lost, or every aspect lost. */
    enum Status { HELD, LOSING, LOST }

    /**
     * A squad the commander may put in the group or already has in it.
     *
     * @param inContact engaged now: never recruited, though a member in contact stays a member
     */
    record Body(int squadId, int x, int y, int alive, int original, boolean machine, boolean demolition,
                boolean inContact, ObjectiveAssignment incumbent) { }

    /** An order to a member, with the stage it belongs to. */
    record Order(ObjectiveAssignment assignment, Stage stage) { }

    /**
     * One pulse's plan.
     *
     * @param orders    by squad id, for every member after the pulse
     * @param recruited free squads taken into the group this pulse
     * @param released  members given back this pulse, to be offered to the rest of the plan
     * @param notes     what changed and why, plus the group's standing line, for the command trace
     */
    record Result(Map<Integer, Order> orders, List<Integer> recruited, List<Integer> released, List<String> notes) { }

    /** Whether a fort's place alarm stands ({@link PlaceAlarms#alarmed}). */
    @FunctionalInterface
    interface Alarms {
        boolean alarmed(int fortId);
    }

    private static final class Group {
        final int fortId;
        Stage stage = Stage.RESERVE;
        final Set<Integer> members = new TreeSet<>();
        int stageX;
        int stageY;
        int musterUntil = -1;
        int required;
        float launchedStrength;
        int launchTick = -1;
        /** What the side could raise at the last look, and from how many free squads, for the trace. */
        float raisable;
        int recruitable;

        Group(int fortId) {
            this.fortId = fortId;
        }
    }

    private Group group;
    private final Map<Integer, Integer> failures = new TreeMap<>();
    private final Map<Integer, Integer> retryAt = new TreeMap<>();
    private final Set<Integer> abandoned = new TreeSet<>();

    static boolean isEnabled() {
        return enabled;
    }

    /** Evidence seam: switches the counter-attack for the rest of this JVM. Restore the previous value in a {@code finally}. */
    static void setEnabledForEvidence(boolean on) {
        enabled = on;
    }

    /** Whether this squad belongs to the group, in any stage. */
    /**
     * Reserve members above the reserve law's share, while the group waits behind a fort it still holds: the fort's own
     * belt may borrow them to man a pressed place. A fort being defended is the reserve's purpose too; the track response
     * and every other draw may not.
     */
    List<Integer> lendable() {
        return lendable;
    }

    /** The fort the group stands for, or -1. */
    int fortId() {
        return group == null ? -1 : group.fortId;
    }

    /** A lent member has gone to man its fort's belt: it leaves the group. */
    void lend(int squadId) {
        if (group != null) group.members.remove(squadId);
        lendable = lendable.stream().filter(id -> id != squadId).toList();
    }

    boolean claims(int squadId) {
        return group != null && group.members.contains(squadId);
    }

    /** Squads held back as the reserve this pulse (the group's members while it waits), for the reserve law's count. */
    int heldInReserve() {
        return group != null && group.stage == Stage.RESERVE ? group.members.size() : 0;
    }

    /** Failed attempts on a fort so far, for tests and the trace. */
    int failures(int fortId) {
        return failures.getOrDefault(fortId, 0);
    }

    boolean abandoned(int fortId) {
        return abandoned.contains(fortId);
    }

    /** The group's stage, or {@code null} with no group. */
    Stage stage() {
        return group == null ? null : group.stage;
    }

    /** The forts {@code side} built, by fort id. */
    static Map<Integer, List<DefenceStructureFact>> ownForts(Faction side, List<DefenceStructureFact> structures) {
        Map<Integer, List<DefenceStructureFact>> forts = new TreeMap<>();
        for (DefenceStructureFact fact : structures) {
            if (fact.ofFort() && fact.originalHolder() == side) {
                forts.computeIfAbsent(fact.fortId(), id -> new ArrayList<>()).add(fact);
            }
        }
        return forts;
    }

    /** The aspect whose loss turns a fort: the bunker of a battery fort (the guns' command), the plant of a shield fort. */
    static DefenceStructureFact critical(List<DefenceStructureFact> fort) {
        if (fort.isEmpty()) return null;
        Kind kind = fort.get(0).family() == Family.SHIELD_FORT ? Kind.FORT_POWER : Kind.FIRE_CONTROL;
        for (DefenceStructureFact aspect : fort) if (aspect.kind() == kind) return aspect;
        return null;
    }

    static Status status(Faction side, List<DefenceStructureFact> fort) {
        boolean allLost = !fort.isEmpty();
        for (DefenceStructureFact aspect : fort) if (!side.hostileTo(aspect.holder())) allLost = false;
        if (allLost) return Status.LOST;
        DefenceStructureFact critical = critical(fort);
        return critical != null && side.hostileTo(critical.holder()) ? Status.LOSING : Status.HELD;
    }

    /**
     * The structure facts single-squad structure work may see: every fact, less the enemy-held aspects of the side's own
     * forts that are losing or lost. Those are the counter-attack's, whether or not it judges them worth it, so a fort is
     * never retaken piecemeal; standing to at the aspects still held is left alone.
     */
    static List<DefenceStructureFact> withheld(Faction side, List<DefenceStructureFact> structures) {
        Map<Integer, List<DefenceStructureFact>> forts = ownForts(side, structures);
        Set<Integer> turned = new TreeSet<>();
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : forts.entrySet()) {
            if (status(side, entry.getValue()) != Status.HELD) turned.add(entry.getKey());
        }
        if (turned.isEmpty()) return structures;
        List<DefenceStructureFact> out = new ArrayList<>(structures.size());
        for (DefenceStructureFact fact : structures) {
            if (fact.ofFort() && fact.originalHolder() == side && turned.contains(fact.fortId())
                    && side.hostileTo(fact.holder())) continue;
            out.add(fact);
        }
        return out;
    }

    /**
     * The side's own forts whose critical aspect is lost, by fort id, with their centres: ground that is the
     * counter-attack's question, which the track response, belt manning and staging leave alone.
     */
    static Map<Integer, int[]> turned(Faction side, List<DefenceStructureFact> structures) {
        Map<Integer, int[]> out = new TreeMap<>();
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : ownForts(side, structures).entrySet()) {
            if (status(side, entry.getValue()) != Status.HELD) out.put(entry.getKey(), centre(entry.getValue()));
        }
        return out;
    }

    /** Whether a cell lies on a turned fort's ground: within {@link #CONTACT_RADIUS_CELLS} of its centre. */
    static boolean onTurnedGround(Map<Integer, int[]> turned, int x, int y) {
        for (int[] centre : turned.values()) {
            if (Math.hypot(x - centre[0], y - centre[1]) <= CONTACT_RADIUS_CELLS) return true;
        }
        return false;
    }

    /** What a fort is believed to take, in marine-equivalents: a floor, or the believed hostiles in it, raised per failure. */
    static int required(int believedHostiles, int failures) {
        return required(believedHostiles, failures, minStrength);
    }

    static int required(int believedHostiles, int failures, int floor) {
        float base = Math.max(floor, STRENGTH_RATIO * Math.max(0, believedHostiles));
        return (int) Math.ceil(base * (1f + FAILURE_GROWTH * Math.max(0, failures)));
    }

    /** The floor this side's fort is believed to take: the constant, or a share of the largest pool it has fielded. */
    private int floor() {
        if (floorShare <= 0f) return minStrength;
        return Math.min(minStrength, Math.max(6, Math.round(floorShare * peakPool)));
    }

    /** Whether what the side can raise is enough to go: at least {@link #COMMIT_SHARE} of what the fort takes. */
    static boolean worth(float raisable, int required) {
        return raisable > 0f && raisable >= commitShare * required;
    }

    /**
     * One pulse.
     *
     * @param structures   the structure facts the side holds this pulse
     * @param keep         the keep's cell, or {@code null} when the map has none (then the staging line runs from the pool)
     * @param hostiles     believed hostile movers ({@code x, y}), never emplacements
     * @param members      the group's members still on strength, as the caller found them; a member not offered is dropped
     * @param free         squads the caller may give the group: free, under its command, not crews
     * @param reserveTarget the reserve law's share of the mobile pool, in squads
     * @param poolSquads   the mobile pool, in squads
     */
    Result plan(int tick, Faction side, List<DefenceStructureFact> structures, Alarms alarms, int[] keep,
                List<int[]> hostiles, List<Body> members, List<Body> free, int reserveTarget, int poolSquads,
                CommandTopology topology) {
        lendable = List.of();
        Map<Integer, Order> orders = new TreeMap<>();
        List<Integer> recruited = new ArrayList<>();
        List<Integer> released = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<Integer, Body> byId = new TreeMap<>();
        for (Body body : members) if (body.alive() > 0) byId.put(body.squadId(), body);
        Map<Integer, List<DefenceStructureFact>> forts = ownForts(side, structures);
        // A fort given up and since won back (by whoever was still in it) is a new question if it is lost again; what it
        // cost before is still remembered in its failures.
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : forts.entrySet()) {
            if (status(side, entry.getValue()) == Status.HELD) abandoned.remove(entry.getKey());
        }
        peakPool = Math.max(peakPool, strength(members) + strength(free));
        if (!hostiles.isEmpty()) contactSeen = true;
        int lawTarget = reserveTarget;
        if (reserveShare > 0f) reserveTarget = Math.max(reserveTarget, Math.round(poolSquads * reserveShare));
        boolean keepThreatened = keep != null && anyWithin(hostiles, keep[0], keep[1], KEEP_GUARD_CELLS);
        int[] home = keep != null ? keep : centroid(members, free);

        if (group != null) {
            group.members.retainAll(byId.keySet());
            review(tick, side, forts, alarms, keepThreatened, byId, released, notes, contactSeen);
        }
        if (group == null && !keepThreatened && home != null && poolSquads >= minPoolSquads && reserveTarget > 0) {
            form(tick, side, forts, alarms, home, keep, hostiles, topology, notes);
        }
        if (group == null) return new Result(orders, recruited, released, notes);

        List<DefenceStructureFact> fort = forts.get(group.fortId);
        int[] centre = centre(fort);
        if (group.stage != Stage.LAUNCHED) {
            if (home != null && !AssaultMuster.safe(group.stageX, group.stageY, hostiles)) {
                int[] cell = stageCell(home, centre, hostiles, topology);
                group.stageX = cell[0];
                group.stageY = cell[1];
                notes.add("counter-attack for fort " + group.fortId + " staging moved to (" + cell[0] + "," + cell[1]
                        + "): a hostile is believed near the old one");
            }
            dropUnreachable(byId, topology, released);
        }
        List<Body> recruitable = recruitable(free, byId.keySet(), topology);
        if (group.stage == Stage.RESERVE) {
            topUp(reserveTarget, centre, recruitable, byId, recruited);
            if (lendToBelt && status(side, fort) == Status.HELD) lendable = frontmost(lawTarget, centre, byId);
            recruitable.removeIf(body -> group.members.contains(body.squadId()));
            Status status = status(side, fort);
            if (status != Status.HELD && tick >= retryAt.getOrDefault(group.fortId, Integer.MIN_VALUE)) {
                int required = required(believedNear(hostiles, centre), failures(group.fortId), floor());
                float raisable = strength(group.members, byId) + strength(recruitable.subList(0,
                        Math.max(0, recruitable.size() - RESERVE_FLOOR)));
                group.raisable = raisable;
                group.recruitable = recruitable.size();
                if (worth(raisable, required)) {
                    group.stage = Stage.MUSTER;
                    group.required = required;
                    group.musterUntil = tick + MUSTER_TICKS;
                    notes.add(String.format(Locale.ROOT, "counter-attack for fort %d musters: the fort is %s, it is "
                                    + "believed to take %d and %.0f can be raised; staging at (%d,%d), goes by tick %d",
                            group.fortId, status.name().toLowerCase(Locale.ROOT), required, raisable, group.stageX,
                            group.stageY, group.musterUntil));
                }
            }
        }
        if (group.stage == Stage.MUSTER) {
            group.required = Math.max(group.required, required(believedNear(hostiles, centre), failures(group.fortId), floor()));
            muster(tick, recruitable, byId, recruited, released, notes);
        }
        if (group == null) return new Result(orders, recruited, released, notes);
        if (group.stage == Stage.LAUNCHED) {
            launchOrders(tick, side, fort, byId, topology, orders, released, notes);
        } else {
            for (int id : group.members) {
                orders.put(id, new Order(ObjectiveAssignment.defendArea(id, group.stageX, group.stageY,
                        STAGE_RADIUS_CELLS), group.stage));
            }
        }
        if (group != null) notes.add(standing(tick, byId, centre, hostiles));
        return new Result(orders, recruited, released, notes);
    }

    /** Ends or advances the group already standing: success, failure, the keep, a fort back in hand and quiet. */
    private void review(int tick, Faction side, Map<Integer, List<DefenceStructureFact>> forts, Alarms alarms,
                        boolean keepThreatened, Map<Integer, Body> byId, List<Integer> released, List<String> notes, boolean contact) {
        List<DefenceStructureFact> fort = forts.get(group.fortId);
        if (fort == null) {
            dissolve(released, notes, "nothing of it is left to retake");
            return;
        }
        Status status = status(side, fort);
        if (group.stage == Stage.LAUNCHED) {
            float strength = strength(group.members, byId);
            if (status == Status.HELD) {
                notes.add(String.format(Locale.ROOT, "counter-attack for fort %d retook it at tick %d, %d ticks after "
                        + "launching, with %.0f of %.0f left", group.fortId, tick, tick - group.launchTick, strength,
                        group.launchedStrength));
                dissolve(released, notes, null);
            } else if (group.members.isEmpty()) {
                fail(tick, "nobody left of it", released, notes);
            } else if (capturing(fort) && tick - group.launchTick < 2 * ASSAULT_TICKS) {
                // Somebody of ours is on the critical aspect's floor taking it back: a group is not called off in the room,
                // though a room contested for twice the bound is not being won.
                return;
            } else if (strength < BROKEN_FRACTION * group.launchedStrength) {
                fail(tick, String.format(Locale.ROOT, "broken at %.0f of %.0f launched", strength,
                        group.launchedStrength), released, notes);
            } else if (tick - group.launchTick >= ASSAULT_TICKS) {
                fail(tick, "not retaken within " + ASSAULT_TICKS + " ticks", released, notes);
            }
            return;
        }
        if (keepThreatened) {
            dissolve(released, notes, "a hostile is believed near the keep");
        } else if (status == Status.HELD && group.stage == Stage.MUSTER) {
            group.stage = Stage.RESERVE;
            notes.add("counter-attack for fort " + group.fortId + " stands back to reserve: the fort is held again");
        } else if (status == Status.HELD && !alarms.alarmed(group.fortId) && !(formOnContact && contact)) {
            dissolve(released, notes, "the fort is held and quiet");
        }
    }

    /**
     * Whether the fort's critical aspect, held by the enemy, is being taken back: its fact says its holder is pressed, which
     * on a fort the side has lost is the side's own capture under way.
     */
    private static boolean capturing(List<DefenceStructureFact> fort) {
        DefenceStructureFact critical = critical(fort);
        return critical != null && critical.threatened();
    }

    /** Forms the reserve for the fort most worth guarding: alarmed or turned, not given up, nearest the keep. */
    private void form(int tick, Faction side, Map<Integer, List<DefenceStructureFact>> forts, Alarms alarms, int[] home,
                      int[] keep, List<int[]> hostiles, CommandTopology topology, List<String> notes) {
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : forts.entrySet()) {
            int id = entry.getKey();
            if (abandoned.contains(id)) continue;
            if (status(side, entry.getValue()) == Status.HELD && !alarms.alarmed(id)
                    && !(formOnContact && contactSeen)) continue;
            int[] centre = centre(entry.getValue());
            double gap = Math.hypot(centre[0] - home[0], centre[1] - home[1]);
            if (keep != null && gap > MAX_RETAKE_CELLS) continue;
            if (gap < bestGap) {
                bestGap = gap;
                best = id;
            }
        }
        if (best < 0) return;
        group = new Group(best);
        int[] cell = stageCell(home, centre(forts.get(best)), hostiles, topology);
        group.stageX = cell[0];
        group.stageY = cell[1];
        notes.add("counter-attack reserve for fort " + best + " formed at (" + cell[0] + "," + cell[1] + "), tick " + tick);
    }

    private void muster(int tick, List<Body> recruitable, Map<Integer, Body> byId, List<Integer> recruited,
                        List<Integer> released, List<String> notes) {
        List<Body> nearest = new ArrayList<>(recruitable);
        nearest.removeIf(body -> group.members.contains(body.squadId()));
        nearest.sort(Comparator.<Body>comparingDouble(b -> Math.hypot(b.x() - group.stageX, b.y() - group.stageY))
                .thenComparingInt(Body::squadId));
        int room = nearest.size() - RESERVE_FLOOR;
        for (Body body : nearest) {
            if (room <= 0 || strength(group.members, byId) >= group.required) break;
            take(body, byId, recruited);
            room--;
        }
        // Nothing more can be raised: the floor is reached (or there was nobody free to begin with).
        boolean exhausted = room <= 0;
        float arrived = 0f;
        int arrivedSquads = 0;
        for (int id : group.members) {
            Body body = byId.get(id);
            if (Math.hypot(body.x() - group.stageX, body.y() - group.stageY) <= RALLY_RADIUS_CELLS) {
                arrived += weight(body);
                arrivedSquads++;
            }
        }
        boolean allArrived = arrivedSquads == group.members.size() && !group.members.isEmpty();
        String why = null;
        if (arrived >= group.required && allArrived) {
            // Ready only once the whole group stands at the staging cell: a straggler still walking up is waited for
            // (to the muster's bound), so the push goes in together rather than with its last squad behind it.
            why = "ready";
        } else if (allArrived && exhausted && worth(arrived, group.required)) {
            why = "everything it can raise is at the staging cell";
        } else if (tick >= group.musterUntil) {
            if (worth(arrived, group.required)) {
                why = "timed out";
            } else {
                fail(tick, String.format(Locale.ROOT, "stands down: %.0f at the staging cell of %d believed needed",
                        arrived, group.required), released, notes);
                return;
            }
        }
        if (why == null) return;
        group.stage = Stage.LAUNCHED;
        group.launchTick = tick;
        group.launchedStrength = strength(group.members, byId);
        notes.add(String.format(Locale.ROOT, "counter-attack for fort %d launches (%s): %d squads, strength %.0f "
                        + "against %d believed needed, from (%d,%d)", group.fortId, why, group.members.size(),
                group.launchedStrength, group.required, group.stageX, group.stageY));
    }

    private void launchOrders(int tick, Faction side, List<DefenceStructureFact> fort, Map<Integer, Body> byId,
                              CommandTopology topology, Map<Integer, Order> orders, List<Integer> released,
                              List<String> notes) {
        List<DefenceStructureTactics.Candidate> candidates = new ArrayList<>();
        for (int id : group.members) {
            Body body = byId.get(id);
            candidates.add(new DefenceStructureTactics.Candidate(id, body.x(), body.y(), body.incumbent(),
                    body.demolition(), body.machine()));
        }
        List<DefenceStructureTactics.Task> tasks = DefenceStructureTactics.planMassed(side, fort, candidates, topology);
        if (tasks.isEmpty()) {
            fail(tick, "no aspect of it can be reached", released, notes);
            return;
        }
        Set<Integer> tasked = new TreeSet<>();
        for (DefenceStructureTactics.Task task : tasks) {
            orders.put(task.squadId(), new Order(task.assignment(), Stage.LAUNCHED));
            tasked.add(task.squadId());
        }
        for (int id : new ArrayList<>(group.members)) {
            if (tasked.contains(id)) continue;
            group.members.remove(id);
            released.add(id);
        }
    }

    /** A failed attempt: counted, the fort left for a while, survivors back to the staging cell, or the fort given up. */
    private void fail(int tick, String why, List<Integer> released, List<String> notes) {
        int count = failures.merge(group.fortId, 1, Integer::sum);
        retryAt.put(group.fortId, tick + RETRY_TICKS);
        if (count >= MAX_ATTEMPTS) {
            abandoned.add(group.fortId);
            notes.add("counter-attack for fort " + group.fortId + " gives up (" + why + "); fort abandoned after "
                    + count + " failed attempts");
            dissolve(released, notes, null);
            return;
        }
        group.stage = Stage.RESERVE;
        group.launchTick = -1;
        notes.add("counter-attack for fort " + group.fortId + " gives up (" + why + "); attempt " + count + " of "
                + MAX_ATTEMPTS + ", survivors fall back to (" + group.stageX + "," + group.stageY + "), next muster from tick "
                + (tick + RETRY_TICKS));
    }

    private void dissolve(List<Integer> released, List<String> notes, String why) {
        released.addAll(group.members);
        if (why != null) notes.add("counter-attack for fort " + group.fortId + " dissolved: " + why);
        group = null;
    }

    /** Fills the reserve to the law's share with the free squads furthest from the fort. */
    private void topUp(int reserveTarget, int[] centre, List<Body> recruitable, Map<Integer, Body> byId,
                       List<Integer> recruited) {
        if (group.members.size() >= reserveTarget) return;
        List<Body> rearmost = new ArrayList<>(recruitable);
        rearmost.sort(Comparator.<Body>comparingDouble(b -> -Math.hypot(b.x() - centre[0], b.y() - centre[1]))
                .thenComparingInt(Body::squadId));
        for (Body body : rearmost) {
            if (group.members.size() >= reserveTarget) break;
            take(body, byId, recruited);
        }
    }

    /** The members above the law's share, nearest the fort first: the ones its belt may borrow. */
    private List<Integer> frontmost(int keep, int[] centre, Map<Integer, Body> byId) {
        if (group.members.size() <= keep) return List.of();
        List<Integer> order = new ArrayList<>(group.members);
        order.sort(Comparator.<Integer>comparingDouble(id -> Math.hypot(byId.get(id).x() - centre[0],
                byId.get(id).y() - centre[1])).thenComparingInt(id -> id));
        return List.copyOf(order.subList(0, group.members.size() - keep));
    }

    private void take(Body body, Map<Integer, Body> byId, List<Integer> recruited) {
        group.members.add(body.squadId());
        byId.put(body.squadId(), body);
        recruited.add(body.squadId());
    }

    /** Free squads that may join: out of contact, at useful strength (half what they started with, never fewer than two), and able to walk to the staging cell. */
    private List<Body> recruitable(List<Body> free, Set<Integer> members, CommandTopology topology) {
        List<Body> out = new ArrayList<>();
        for (Body body : free) {
            if (members.contains(body.squadId()) || body.inContact() || body.alive() < 2
                    || 2 * body.alive() < body.original()) continue;
            if (!reaches(body, topology)) continue;
            out.add(body);
        }
        return out;
    }

    private void dropUnreachable(Map<Integer, Body> byId, CommandTopology topology, List<Integer> released) {
        for (int id : new ArrayList<>(group.members)) {
            if (reaches(byId.get(id), topology)) continue;
            group.members.remove(id);
            released.add(id);
        }
    }

    private boolean reaches(Body body, CommandTopology topology) {
        if (body.x() == group.stageX && body.y() == group.stageY) return true;
        return topology.inBounds(body.x(), body.y()) && topology.reachable(body.x(), body.y(), group.stageX, group.stageY);
    }

    private String standing(int tick, Map<Integer, Body> byId, int[] centre, List<int[]> hostiles) {
        return String.format(Locale.ROOT, "counter-attack fort %d %s: %d squads, strength %.0f, believes it takes %d "
                        + "(%d hostiles believed near it), staging (%d,%d), could raise %.0f from %d free%s", group.fortId,
                group.stage.name().toLowerCase(Locale.ROOT), group.members.size(), strength(group.members, byId),
                group.stage == Stage.RESERVE ? required(believedNear(hostiles, centre), failures(group.fortId), floor()) : group.required,
                believedNear(hostiles, centre), group.stageX, group.stageY, group.raisable, group.recruitable,
                group.stage == Stage.LAUNCHED ? ", launched at tick " + group.launchTick : "");
    }

    /** The staging cell: {@link AssaultMuster#STAGING_STANDOFF_CELLS} short of the fort on the line from home, clear of believed contact. */
    private static int[] stageCell(int[] home, int[] centre, List<int[]> hostiles, CommandTopology topology) {
        AssaultMuster.Ground ground = (x, y) -> topology.inBounds(x, y) && topology.isWalkable(x, y)
                && (x == home[0] && y == home[1] || topology.reachable(home[0], home[1], x, y));
        return AssaultMuster.rallyCell(home[0], home[1], centre[0], centre[1], ground, hostiles);
    }

    static int[] centre(List<DefenceStructureFact> fort) {
        long x = 0;
        long y = 0;
        for (DefenceStructureFact aspect : fort) {
            x += aspect.cellX();
            y += aspect.cellY();
        }
        return new int[] {(int) Math.round((double) x / fort.size()), (int) Math.round((double) y / fort.size())};
    }

    static int believedNear(List<int[]> hostiles, int[] centre) {
        int n = 0;
        for (int[] hostile : hostiles) {
            if (Math.hypot(hostile[0] - centre[0], hostile[1] - centre[1]) <= CONTACT_RADIUS_CELLS) n++;
        }
        return n;
    }

    private static boolean anyWithin(List<int[]> hostiles, int x, int y, int radius) {
        for (int[] hostile : hostiles) if (Math.hypot(hostile[0] - x, hostile[1] - y) <= radius) return true;
        return false;
    }

    private static int[] centroid(List<Body> members, List<Body> free) {
        long x = 0;
        long y = 0;
        int n = 0;
        for (Body body : members) {
            x += body.x();
            y += body.y();
            n++;
        }
        for (Body body : free) {
            x += body.x();
            y += body.y();
            n++;
        }
        return n == 0 ? null : new int[] {(int) (x / n), (int) (y / n)};
    }

    private static float weight(Body body) {
        return AssaultMuster.weight(body.alive(), body.machine());
    }

    private static float strength(Iterable<Body> bodies) {
        float sum = 0f;
        for (Body body : bodies) sum += weight(body);
        return sum;
    }

    private static float strength(Set<Integer> ids, Map<Integer, Body> byId) {
        float sum = 0f;
        for (int id : ids) {
            Body body = byId.get(id);
            if (body != null) sum += weight(body);
        }
        return sum;
    }
}
