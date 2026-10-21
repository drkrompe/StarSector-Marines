package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A side's counter-attack on a place it holds and has lost or is losing (`conquest-command.md`, Defender command): the
 * answer to a lost place worth retaking, of any kind — a compound, an objective, a story site, a fort as one case among
 * many (`garrisonables.md`, places not forts). Retaking is done in proportion or not at all, never by feeding single
 * squads into the place, and in proportion to what the side has <em>observed</em> holds it.
 *
 * <p>One group at a time, for one place, through four stages:
 * <ul>
 *   <li><b>Reserve.</b> Once a place has turned, a share of the mobile pool waits at a staging cell behind the turned
 *       place most worth retaking, on the line from home (the keep, or the side's home site) and clear of believed
 *       contact ({@link AssaultMuster#rallyCell}). It is the counter-attack's core and counts as the reserve law's share,
 *       not in addition to it. Nothing is held back before a place is lost unless {@link #formOnAlarm} asks for it.</li>
 *   <li><b>Probe.</b> Once the place has turned, and before anything commits, the side establishes what holds it. If no
 *       own squad has watched the place lately, one member of the group closes to an observation post short of it and
 *       watches for a couple of pulses; any own squad already standing within observation range (a picket, a recon car,
 *       a drone flight) serves as well. The group is then sized from the <b>observed holders</b> with a
 *       {@link #MARGIN margin}, plus a weight for the side's own <b>remembered losses</b> on that ground, raised for each
 *       failed attempt ({@link #required}). If that cannot be raised the place is not attempted and the group stands
 *       down: its squads go back to the ordinary pool, the place's ground is the ordinary defence's again, and the
 *       place <b>waits</b>. It is observed afresh after a while ({@link #waitTicks}), or sooner when own squads watching
 *       it see its holders thin to what could be raised. Standing down is not a failed attempt: the holders may move
 *       on.</li>
 *   <li><b>Muster.</b> The group recruits the nearest free squads at useful strength until it holds what the place takes,
 *       never the last free squad, and gathers at the staging cell. The probe keeps its post and keeps watching.</li>
 *   <li><b>Launched.</b> When what stands at the staging cell (and the probe at its post) reaches what the place takes, or
 *       everything the side can raise has arrived above the {@link #COMMIT_SHARE commit share}, or the muster times out
 *       above it, every member is given the place's own {@link Assault} order on the same pulse. A group worn below
 *       {@link #BROKEN_FRACTION} of what it launched with, or one that has not won the place back within
 *       {@link #ASSAULT_TICKS}, has failed: what it saw at the place is written into the estimate, its survivors fall back
 *       to the staging cell, and after {@link #MAX_ATTEMPTS} failures the place is given up for good.</li>
 * </ul>
 *
 * <p>Belief only: how a place stands is an own-force fact about the side's own places, its holders are the hostile
 * contacts the side believes near it, and its losses are the side's own casualty memory there. Home outranks every
 * place: a believed mover near the keep dissolves anything not already launched. Pure planning over frozen inputs and
 * deterministic; which places exist, what they are worth and how a group is ordered at one are the caller's, and which
 * squads may be offered is the caller's decision. Every figure marked UNTUNED is a first pass.
 */
final class PlaceCounterattack {

    /** {@code -Dbattle.counterattack=false} is the same-build control: no reserve, no group, no probe. */
    static final String PROPERTY = "battle.counterattack";
    /** The fort counter-attack's old switch, still honoured when the new one is not given. */
    static final String LEGACY_PROPERTY = "battle.conquest.fortCounterattack";

    private static volatile boolean enabled = Boolean.parseBoolean(
            System.getProperty(PROPERTY, System.getProperty(LEGACY_PROPERTY, "true")));

    /** A place further than this from home is not worth leaving home for. UNTUNED. */
    static final int MAX_RETAKE_CELLS = 220;
    /** A believed hostile mover this close to home threatens it: no counter-attack forms or waits. UNTUNED. */
    static final int KEEP_GUARD_CELLS = 40;
    /** The least a place is believed to take, whoever is seen in it: two patrols. */
    static final int MIN_STRENGTH = 12;
    /**
     * Marines the group brings for each observed holder. An attack on a consolidated hold needs more than parity: the
     * fort trials with parity broke every push at a sixth to a third of its strength. UNTUNED.
     */
    static final float MARGIN = 2.0f;
    /** Marines added for each of the side's own remembered losses on the place's ground: the holders it did not see. UNTUNED. */
    static final float LOSS_WEIGHT = 0.5f;
    /** Each failed attempt raises what the place is believed to take by this fraction. */
    static final float FAILURE_GROWTH = AssaultMuster.FAILURE_GROWTH;
    /** The share of what the place takes the side must be able to raise, and have at the staging cell, to go. UNTUNED. */
    static final float COMMIT_SHARE = 0.75f;
    /** Ticks a muster waits for its recruits before it goes or stands down (2 min). UNTUNED. */
    static final int MUSTER_TICKS = 3600;
    /** A member within this many cells of the staging cell (or the probe of its post) has arrived. */
    static final int RALLY_RADIUS_CELLS = AssaultMuster.RALLY_RADIUS_CELLS;
    /** Radius of the area defence a member holds at the staging cell. */
    static final int STAGE_RADIUS_CELLS = 4;
    /** Radius of the area the probe holds at its observation post. */
    static final int PROBE_RADIUS_CELLS = 2;
    /**
     * An own squad this close to a place's centre is watching it, and the observation post stands this far out on the
     * line from staging: inside a rifleman's sight of the place's middle. UNTUNED.
     */
    static final int OBSERVE_CELLS = 16;
    /** Pulses of watching that make an observation. */
    static final int OBSERVE_PULSES = 2;
    /** An observation older than this is stale: the place is observed again before the next decision (1 min). UNTUNED. */
    static final int OBSERVATION_FRESH_TICKS = 1800;
    /**
     * Ticks a probe has to make its observation, beyond its walk to the post at a muster's pace, before what it has is
     * taken as the observation (1 min). UNTUNED.
     */
    static final int PROBE_TICKS = 1800;
    /** A launched group below this fraction of the strength it launched with has failed. UNTUNED. */
    static final float BROKEN_FRACTION = 0.4f;
    /** Ticks a launched group has to win the place back (2 min). UNTUNED. */
    static final int ASSAULT_TICKS = 3600;
    /** Ticks a place is left after a failed attempt before the next decision (80 s). UNTUNED. */
    static final int RETRY_TICKS = 2400;
    /**
     * The longest a place judged outmatched waits before it is observed again, in {@link #RETRY_TICKS}: each stand-down
     * waits one longer than the last, up to this many. UNTUNED.
     */
    static final int MAX_WAIT_RETRIES = 3;
    /** Failed attempts after which a place is given up for good. UNTUNED. */
    static final int MAX_ATTEMPTS = 2;
    /** The least mobile pool, in squads, that holds a reserve for a counter-attack at all. */
    static final int MIN_POOL_SQUADS = 2;
    /** Free squads a muster never recruits: the reserve law's floor. */
    static final int RESERVE_FLOOR = 1;
    /**
     * The counter-attack's reserve as a share of the mobile pool, above the reserve law's quarter: on a four-patrol
     * garrison a quarter is one patrol, which is no counter-attack.
     */
    static final float RESERVE_SHARE = 0.5f;

    /*
     * Evidence knobs (`-Dbattle.counterattack.<name>`, or the old `-Dbattle.conquest.fortCounterattack.<name>`), read
     * once, so a tuning variant is one command away from its control. The defaults are the constants above.
     */
    static volatile float reserveShare = knob("reserveShare", RESERVE_SHARE);
    static volatile int minStrength = (int) knob("minStrength", MIN_STRENGTH);
    static volatile float commitShare = knob("commitShare", COMMIT_SHARE);
    static volatile int minPoolSquads = (int) knob("minPoolSquads", MIN_POOL_SQUADS);
    static volatile float margin = knob("margin", MARGIN);
    static volatile float lossWeight = knob("lossWeight", LOSS_WEIGHT);
    /** Whether the group observes before it commits; off is the uninformed control (believed contacts at the staging cell). */
    static volatile boolean informed = knob("informed", 1f) > 0f;
    /**
     * Whether the reserve forms behind a held place once any hostile mover has been believed this battle, not only on the
     * place's alarm. Off by default: held from first contact, even the law's share at a staging cell took the patrol a
     * small garrison mans its compound with, and the law already keeps that share from the track response.
     */
    static volatile boolean formOnContact = knob("formOnContact", 0f) > 0f;
    /**
     * Whether a place judged outmatched waits (its group stands down to the ordinary pool, and the place is observed again
     * later) rather than counting as a failed attempt with the reserve held at its staging cell. Off is the first form.
     */
    static volatile boolean standDown = knob("standDown", 1f) > 0f;
    /**
     * Whether the reserve grows past the law's share as soon as a place has turned, before anything has decided the place
     * is worth attempting; off, the larger share is drawn by the muster itself, so a reserve waiting on a probe is the
     * law's share and no more.
     */
    static volatile boolean growBeforeMuster = knob("growBeforeMuster", 1f) > 0f;
    /**
     * Whether the ordinary defence may man the garrisonables of a turned place the counter-attack does not claim (one it
     * has stood down from, or given up); off, nothing on any turned place's ground is manned.
     */
    static volatile boolean manLostGround = knob("manLostGround", 0f) > 0f;
    /**
     * Whether a decided counter-attack may draw on quiet garrisons the caller offers ({@code garrisonSortie}); off is the
     * control in which only the mobile pool is the counter-attack's.
     */
    static volatile boolean garrisonSortie = knob("garrisonSortie", 1f) > 0f;
    /** Whether a reserve forms behind a held place whose own alarm stands, before anything is lost. */
    static volatile boolean formOnAlarm = knob("formOnAlarm", 0f) > 0f;
    /** Whether the reserve above the law's share may be lent to man its own place's pressed garrisonables while it holds. */
    static volatile boolean lendToBelt = knob("lendToBelt", 1f) > 0f;

    private static float knob(String name, float fallback) {
        String value = System.getProperty(PROPERTY + "." + name, System.getProperty(LEGACY_PROPERTY + "." + name));
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return 1f;
        if (value.equalsIgnoreCase("false")) return 0f;
        return Float.parseFloat(value);
    }

    enum Stage { RESERVE, PROBE, MUSTER, LAUNCHED }

    /** How a place stands: whole in the side's hands, being lost (or being won back), or lost. */
    enum Status { HELD, LOSING, LOST }

    /** How a group is ordered at a place: each member's order by squad id, empty when no part of it can be reached. */
    @FunctionalInterface
    interface Assault {
        Map<Integer, ObjectiveAssignment> orders(List<Body> members, CommandTopology topology);
    }

    /**
     * A place the side holds or held, as the caller reads it this pulse.
     *
     * @param key       stable identity and the label the notes use ("fort 3", "compound 12")
     * @param x         its centre
     * @param radius    its ground: hostiles within it hold the place, and it is the counter-attack's while turned
     * @param worth     what the place is worth to the side by itself (a mission objective, a fort's guns); home's
     *                  nearness is added by {@link #value}
     * @param alarmed   whether its own alarm stands (a fort's place alarm), which forms the reserve behind it
     * @param capturing whether the side's own retake of it is under way (a group in the room is not called off)
     */
    record Place(String key, int x, int y, int radius, Status status, float worth, boolean alarmed, boolean capturing,
                 Assault assault) {
        boolean turned() {
            return status != Status.HELD;
        }
    }

    /**
     * A squad the commander may put in the group or already has in it.
     *
     * @param inContact engaged now: never recruited, though a member in contact stays a member
     */
    record Body(int squadId, int x, int y, int alive, int original, boolean machine, boolean demolition,
                boolean inContact, ObjectiveAssignment incumbent) { }

    /** A believed hostile mover, never an emplacement, with its weight (a rifleman is one, a machine more). */
    record Hostile(int x, int y, float weight) {
        Hostile(int x, int y) {
            this(x, y, 1f);
        }
    }

    /** The side's own remembered losses on a place's ground (its casualty memory, `lossesAt`). */
    @FunctionalInterface
    interface Losses {
        Losses NONE = (x, y, radius) -> 0f;

        float near(int x, int y, int radius);
    }

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

    /**
     * What one commander's counter-attack is allowed: how much of the pool waits for it, the floor a place takes, and
     * whether the reserve waits for contact or forms at once (a finite counter-attack pool whose whole purpose it is).
     *
     * @param margin      marines brought for each observed holder; zero or less is the shared {@link #margin} knob
     * @param maxShare    the most of the pool, as a share of its squads, the group may ever hold (never fewer than one
     *                    squad); one or more is no bound beyond the pool itself
     * @param maxLaunches pushes the side may launch in a battle; once spent no group forms again
     * @param floorOnPool whether the reserve law's floor is kept only out of the free pool, never out of garrisons
     *                    offered to sortie: a side whose pool is spent may still sortie its last garrison
     * @param commitShare the share of what a place takes the side must raise to go; zero or less is the shared
     *                    {@link #commitShare} knob
     */
    record Doctrine(float reserveShare, int floor, boolean formOnContact, boolean formAtOnce, int maxRetakeCells,
                    int minPoolSquads, float margin, float maxShare, int maxLaunches, boolean floorOnPool,
                    float commitShare) {
        /** The Conquest defender's: the knobs above. */
        static Doctrine conquest() {
            return new Doctrine(PlaceCounterattack.reserveShare, minStrength, PlaceCounterattack.formOnContact, false,
                    MAX_RETAKE_CELLS, PlaceCounterattack.minPoolSquads, 0f, 1f, Integer.MAX_VALUE, false, 0f);
        }

        /** A story mission's finite counter-attack pool: all of it waits for the counter-attack from the start. */
        static Doctrine finitePool(int floor) {
            return new Doctrine(1f, floor, true, true, MAX_RETAKE_CELLS, 1, 0f, 1f, Integer.MAX_VALUE, false, 0f);
        }

        /**
         * A procedural mission defender's (`MissionCounterattack`): a bounded share of a small pool, nothing held back
         * before a place turns, and a budget of pushes a battle, so a capture is answered once or twice and not forever.
         */
        static Doctrine mission(float share, int floor, float margin, float commitShare, int maxLaunches) {
            return new Doctrine(share, floor, false, false, MAX_RETAKE_CELLS, 1, margin, share, maxLaunches, true,
                    commitShare);
        }

        /** The most squads the group may hold out of a pool of this many: no bound at a share of one or more. */
        int cap(int poolSquads) {
            return maxShare >= 1f ? Integer.MAX_VALUE : Math.max(1, Math.round(poolSquads * maxShare));
        }
    }

    /** What the side believes holds one turned place: what it last observed there, and what a push met. */
    private static final class Estimate {
        float holders;
        int observedTick = -1;
        int watchedPulses;
        /** How close an own squad must stand to be watching: further for a place whose near ground cannot be stood on. */
        int watchRange = OBSERVE_CELLS;
        /** The most a push met at the place: the next attempt believes at least this many hold it. */
        float repulsedBy;
        /** The most believed at the place during the current push. */
        float pushPeak;
        /** Consecutive pulses a waiting place has been watched holding no more than could now be raised against it. */
        int thinPulses;
    }

    private static final class Group {
        String key;
        Stage stage = Stage.RESERVE;
        final Set<Integer> members = new TreeSet<>();
        int stageX;
        int stageY;
        int musterUntil = -1;
        int required;
        float launchedStrength;
        int launchTick = -1;
        int probeId = -1;
        int probeX = -1;
        int probeY = -1;
        int probeUntil = -1;
        /** What the side could raise at the last look, and from how many free squads, for the trace. */
        float raisable;
        int recruitable;

        Group(String key) {
            this.key = key;
        }
    }

    private final Doctrine doctrine;
    private Group group;
    private final Map<String, Integer> failures = new TreeMap<>();
    private final Map<String, Integer> retryAt = new TreeMap<>();
    private final Set<String> abandoned = new TreeSet<>();
    private final Map<String, Estimate> estimates = new TreeMap<>();
    /** Places judged outmatched: the tick each is observed again, and how many times each has stood down. */
    private final Map<String, Integer> waitUntil = new TreeMap<>();
    private final Map<String, Integer> standDowns = new TreeMap<>();
    /** The tick of the last plan, for {@link #claimsGround}. */
    private int lastTick = Integer.MIN_VALUE;
    /** This pulse's sortie-able garrisons, offered by the caller. */
    private List<Body> sortieThisPulse = List.of();
    /** This pulse's lendable members ({@link #lendable()}). */
    private List<Integer> lendable = List.of();
    /** Whether any hostile mover has been believed this battle: the attack has begun, and a quiet pulse does not end it. */
    private boolean contactSeen;
    /** Pushes launched this battle, against the doctrine's budget. */
    private int launches;
    /** The most squads the group may hold this pulse ({@link Doctrine#cap}). */
    private int capThisPulse = Integer.MAX_VALUE;

    PlaceCounterattack() {
        this(Doctrine.conquest());
    }

    PlaceCounterattack(Doctrine doctrine) {
        this.doctrine = doctrine;
    }

    static boolean isEnabled() {
        return enabled;
    }

    /** Evidence seam: switches the counter-attack for the rest of this JVM. Restore the previous value in a {@code finally}. */
    static void setEnabledForEvidence(boolean on) {
        enabled = on;
    }

    /**
     * Reserve members above the reserve law's share, while the group waits behind a place it still holds: the place's own
     * garrisonables may borrow them to man a pressed post. A place being defended is the reserve's purpose too; the track
     * response and every other draw may not.
     */
    List<Integer> lendable() {
        return lendable;
    }

    /** The place the group stands for, or {@code null}. */
    String placeKey() {
        return group == null ? null : group.key;
    }

    /** A lent member has gone to man its place: it leaves the group. */
    void lend(int squadId) {
        if (group != null) group.members.remove(squadId);
        lendable = lendable.stream().filter(id -> id != squadId).toList();
    }

    /** Whether this squad belongs to the group, in any stage. */
    boolean claims(int squadId) {
        return group != null && group.members.contains(squadId);
    }

    /** Squads held back as the reserve this pulse (the group's members while it waits), for the reserve law's count. */
    int heldInReserve() {
        return group != null && (group.stage == Stage.RESERVE || group.stage == Stage.PROBE) ? group.members.size() : 0;
    }

    /** Failed attempts on a place so far, for tests and the trace. */
    int failures(String key) {
        return failures.getOrDefault(key, 0);
    }

    boolean abandoned(String key) {
        return abandoned.contains(key);
    }

    /** Whether a place judged outmatched is waiting to be observed again, as of the last plan. */
    boolean waiting(String key) {
        return waiting(lastTick, key);
    }

    private boolean waiting(int tick, String key) {
        Integer until = waitUntil.get(key);
        return until != null && tick < until;
    }

    /**
     * Whether a turned place's ground is the counter-attack's question, as of the last plan: a group with squads in it
     * stands for the place, which is neither given up nor waiting after a stand-down. Any other turned place (one it
     * has stepped back from, or has nobody for) is the ordinary defence's, so the track response, flow and its
     * garrisonables answer what is on it.
     */
    boolean claimsGround(String key) {
        return group != null && group.key.equals(key) && !group.members.isEmpty() && !abandoned.contains(key)
                && !waiting(key);
    }

    /** How long a place waits after its n-th stand-down. */
    static int waitTicks(int standDowns) {
        return RETRY_TICKS * Math.max(1, Math.min(MAX_WAIT_RETRIES, standDowns));
    }

    /** The group's stage, or {@code null} with no group. */
    Stage stage() {
        return group == null ? null : group.stage;
    }

    /** The squad probing for the group, or -1. */
    int probeId() {
        return group == null ? -1 : group.probeId;
    }

    /** What the side believes holds a place, in marine-equivalents, or 0 with no estimate. */
    float believedHolders(String key) {
        Estimate estimate = estimates.get(key);
        return estimate == null ? 0f : Math.max(estimate.holders, estimate.repulsedBy);
    }

    /** The believed hostile movers in a side's picture, weighted, never emplacements; none without a picture. */
    static List<Hostile> hostiles(CommanderInfluenceSnapshot influence) {
        List<Hostile> out = new ArrayList<>();
        if (influence == null) return out;
        for (CommanderContact contact : influence.contacts()) {
            if (contact.structure() || contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) {
                continue;
            }
            out.add(new Hostile(contact.cellX(), contact.cellY(), contact.strength()));
        }
        return out;
    }

    /** The side's own remembered losses within a radius of a cell, read off its casualty memory. */
    static Losses losses(CommanderInfluenceSnapshot influence) {
        if (influence == null) return Losses.NONE;
        return (x, y, radius) -> {
            int size = influence.blockSize();
            float sum = 0f;
            for (int by = Math.max(0, (y - radius) / size); by <= Math.min(influence.height() - 1, (y + radius) / size); by++) {
                for (int bx = Math.max(0, (x - radius) / size); bx <= Math.min(influence.width() - 1, (x + radius) / size); bx++) {
                    int cx = bx * size + size / 2;
                    int cy = by * size + size / 2;
                    if (Math.hypot(cx - x, cy - y) <= radius) sum += influence.lossesAt(bx, by);
                }
            }
            return sum;
        };
    }

    /** What a place is worth to the side: its own worth, and home's nearness (one at home, none at the reach bound). */
    static float value(float worth, double gapHome, int maxRetakeCells) {
        if (worth <= 0f) return 0f;
        return worth + (float) Math.max(0.0, 1.0 - gapHome / maxRetakeCells);
    }

    /**
     * What a place is believed to take, in marine-equivalents: the observed holders with the margin, plus the remembered
     * losses on its ground, never below the floor, raised for each failed attempt.
     */
    static int required(float holders, float losses, int failures, int floor) {
        return required(holders, losses, failures, floor, margin);
    }

    /** As {@link #required(float, float, int, int)}, at a doctrine's own margin. */
    static int required(float holders, float losses, int failures, int floor, float margin) {
        float base = Math.max(floor, margin * Math.max(0f, holders) + lossWeight * Math.max(0f, losses));
        return (int) Math.ceil(base * (1f + FAILURE_GROWTH * Math.max(0, failures)));
    }

    /** Whether what the side can raise is enough to go: at least the commit share of what the place takes. */
    static boolean worth(float raisable, int required) {
        return raisable > 0f && raisable >= commitShare * required;
    }

    /** As {@link #worth}, at the doctrine's own commit share. */
    private boolean goes(float raisable, int required) {
        float share = doctrine.commitShare() > 0f ? doctrine.commitShare() : commitShare;
        return raisable > 0f && raisable >= share * required;
    }

    /** The turned places within reach of home: ground that is the counter-attack's question and nobody else's. */
    static List<Place> turnedGround(List<Place> places, int[] home, int maxRetakeCells) {
        List<Place> out = new ArrayList<>();
        for (Place place : places) {
            if (!place.turned()) continue;
            if (home != null && Math.hypot(place.x() - home[0], place.y() - home[1]) > maxRetakeCells) continue;
            out.add(place);
        }
        return out;
    }

    /**
     * Whether a cell lies on a turned place's ground: within its radius, and nearer it than any place the side still
     * holds, so an attack on a held place beside a lost one is still that held place's to answer.
     */
    static boolean onTurnedGround(List<Place> turned, List<Place> places, int x, int y) {
        for (Place place : turned) {
            double gap = Math.hypot(x - place.x(), y - place.y());
            if (gap > place.radius()) continue;
            boolean nearerHeld = false;
            for (Place other : places) {
                if (!other.turned() && Math.hypot(x - other.x(), y - other.y()) < gap) {
                    nearerHeld = true;
                    break;
                }
            }
            if (!nearerHeld) return true;
        }
        return false;
    }

    /** What is believed at a place: the weight of the hostile movers on its ground. */
    static float believedAt(List<Hostile> hostiles, Place place) {
        float n = 0f;
        for (Hostile hostile : hostiles) {
            if (Math.hypot(hostile.x() - place.x(), hostile.y() - place.y()) <= place.radius()) n += hostile.weight();
        }
        return n;
    }

    /**
     * One pulse.
     *
     * @param places        the places the side holds or held this pulse
     * @param keep          home's cell (the keep, or the side's home site), or {@code null} (then staging runs from the pool)
     * @param hostiles      believed hostile movers, never emplacements
     * @param watchers      every own squad's cell this pulse: one near a place is watching it
     * @param losses        the side's remembered losses on a place's ground
     * @param members       the group's members still on strength, as the caller found them; a member not offered is dropped
     * @param free          squads the caller may give the group: free, under its command, not crews
     * @param reserveTarget the reserve law's share of the mobile pool, in squads
     * @param poolSquads    the mobile pool, in squads
     */
    Result plan(int tick, List<Place> places, int[] keep, List<Hostile> hostiles, List<int[]> watchers, Losses losses,
                List<Body> members, List<Body> free, int reserveTarget, int poolSquads, CommandTopology topology) {
        return plan(tick, places, keep, hostiles, watchers, losses, members, free, List.of(), reserveTarget, poolSquads,
                topology);
    }

    /**
     * As {@link #plan(int, List, int[], List, List, Losses, List, List, int, int, CommandTopology)}, with garrisons
     * that may sortie: squads the caller holds elsewhere (a quiet compound's garrison above what it keeps home) that a
     * decided counter-attack may draw on. They count toward what can be raised and a muster recruits them, but nothing
     * waits on them: no reserve is topped up from them and no probe is drawn from them. The caller hands a recruited
     * one over to the counter-attack.
     */
    Result plan(int tick, List<Place> places, int[] keep, List<Hostile> hostiles, List<int[]> watchers, Losses losses,
                List<Body> members, List<Body> free, List<Body> sortie, int reserveTarget, int poolSquads,
                CommandTopology topology) {
        lendable = List.of();
        sortieThisPulse = garrisonSortie ? sortie : List.of();
        lastTick = tick;
        capThisPulse = doctrine.cap(poolSquads);
        Map<Integer, Order> orders = new TreeMap<>();
        List<Integer> recruited = new ArrayList<>();
        List<Integer> released = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<Integer, Body> byId = new TreeMap<>();
        for (Body body : members) if (body.alive() > 0) byId.put(body.squadId(), body);
        Map<String, Place> byKey = new TreeMap<>();
        for (Place place : places) byKey.put(place.key(), place);
        // A place given up and since won back (by whoever was still in it) is a new question if it is lost again; what it
        // cost before is still remembered in its failures.
        for (Place place : places) {
            if (place.turned()) continue;
            abandoned.remove(place.key());
            estimates.remove(place.key());
            waitUntil.remove(place.key());
        }
        if (!hostiles.isEmpty()) contactSeen = true;
        int lawTarget = reserveTarget;
        // Before anything is lost the reserve is the law's share, waiting where it is most useful; the counter-attack's
        // larger share is drawn once a place has turned (or from the start for a pool that is all counter-attack).
        boolean anyTurned = false;
        for (Place place : places) {
            anyTurned |= place.turned() && !abandoned.contains(place.key()) && !waiting(tick, place.key());
        }
        boolean deciding = group != null && (group.stage == Stage.MUSTER || group.stage == Stage.LAUNCHED);
        if (doctrine.reserveShare() > 0f && (anyTurned && (growBeforeMuster || deciding) || doctrine.formAtOnce())) {
            reserveTarget = Math.max(reserveTarget, Math.round(poolSquads * doctrine.reserveShare()));
        }
        boolean keepThreatened = keep != null && anyWithin(hostiles, keep[0], keep[1], KEEP_GUARD_CELLS);
        // With no home, staging runs from the pool; a pool with nobody free stages from the garrisons that may sortie.
        int[] home = keep != null ? keep : centroid(members, free);
        if (home == null) home = centroid(sortieThisPulse, List.of());
        // A probe in a fight sees what it is fighting, from wherever it was stopped.
        String engaged = null;
        if (group != null && group.stage == Stage.PROBE && byId.containsKey(group.probeId)
                && byId.get(group.probeId).inContact()) engaged = group.key;
        observe(tick, places, hostiles, watchers, engaged);
        thinning(tick, places, hostiles, losses, byId, free, topology, notes);

        if (group != null) {
            group.members.retainAll(byId.keySet());
            review(tick, byKey, keepThreatened, byId, released, notes);
        }
        if (group != null && group.stage != Stage.LAUNCHED && launches >= doctrine.maxLaunches()) {
            dissolve(released, notes, "the side's pushes for this battle are spent");
        }
        boolean pooled = poolSquads >= doctrine.minPoolSquads() && reserveTarget > 0;
        if (group == null && launches < doctrine.maxLaunches() && !keepThreatened && home != null
                && (pooled || !sortieThisPulse.isEmpty())) {
            // A pool too small to hold a reserve still answers a lost place when garrisons may sortie to it; it forms
            // only for a place that has turned, since a garrison never waits behind one that still holds.
            Place best = choose(tick, places, home, keep, hostiles);
            if (best != null && (pooled || best.turned())) form(tick, best, home, hostiles, topology, notes);
        } else if (group != null && group.stage == Stage.RESERVE && !keepThreatened && home != null) {
            // A reserve waiting behind a place that has not turned stands for one that has, when one does.
            Place best = choose(tick, places, home, keep, hostiles);
            Place current = byKey.get(group.key);
            if (best != null && !best.key().equals(group.key) && best.turned() && (current == null || !current.turned())) {
                notes.add("counter-attack reserve moves from " + group.key + " to " + best.key() + ": it has turned");
                group.key = best.key();
                int[] cell = stageCell(home, new int[] {best.x(), best.y()}, hostiles, topology);
                group.stageX = cell[0];
                group.stageY = cell[1];
            }
        }
        if (group == null) return new Result(orders, recruited, released, notes);

        Place place = byKey.get(group.key);
        int[] centre = {place.x(), place.y()};
        if (group.stage != Stage.LAUNCHED) {
            if (home != null && !AssaultMuster.safe(group.stageX, group.stageY, cells(hostiles))) {
                int[] cell = stageCell(home, centre, hostiles, topology);
                group.stageX = cell[0];
                group.stageY = cell[1];
                notes.add("counter-attack for " + group.key + " staging moved to (" + cell[0] + "," + cell[1]
                        + "): a hostile is believed near the old one");
            }
            dropUnreachable(byId, topology, released);
        }
        List<Body> recruitable = recruitable(free, byId.keySet(), topology);
        if (group.stage == Stage.RESERVE) {
            topUp(Math.min(reserveTarget, capThisPulse), centre, recruitable, byId, recruited);
            // Before anything is lost the whole reserve is the law's share and all of it may man its own place; once a
            // place has turned, only what it holds above the law's share may.
            if (lendToBelt && !place.turned()) lendable = frontmost(anyTurned ? lawTarget : 0, centre, byId);
            recruitable.removeIf(body -> group.members.contains(body.squadId()));
            if (place.turned() && tick >= retryAt.getOrDefault(group.key, Integer.MIN_VALUE)) {
                if (informed && !observed(tick, group.key)) {
                    // Somebody of ours is watching it already (a picket, a recon car): wait for that, send nobody.
                    if (!watchedNow(tick, group.key)) {
                        startProbe(tick, place, byId, withSortie(recruitable, byId, topology), recruited, topology,
                                notes);
                    }
                } else {
                    decide(tick, place, hostiles, losses, byId, withSortie(recruitable, byId, topology), released,
                            notes);
                }
            }
        }
        if (group != null && group.stage == Stage.PROBE) {
            probe(tick, place, hostiles, losses, byId, withSortie(recruitable, byId, topology), released, notes);
        }
        if (group != null && group.stage == Stage.MUSTER) {
            group.required = Math.max(group.required, requiredFor(place, hostiles, losses));
            recruitable = withSortie(recruitable, byId, topology);
            float raisable = raisable(strength(group.members, byId), group.members.size(), recruitable);
            if (informed && standDown && !goes(raisable, group.required)) {
                // What the place takes has outgrown what can be raised while the group gathered: waiting at the staging
                // cell for the muster's bound would only hold the pool out of the fight.
                standDown(tick, place, group.required, raisable, released, notes);
            } else {
                muster(tick, recruitable, byId, recruited, released, notes);
            }
        }
        if (group == null) return new Result(orders, recruited, released, notes);
        if (group.stage == Stage.LAUNCHED) {
            launchOrders(tick, place, byId, topology, orders, released, notes);
        } else {
            for (int id : group.members) {
                if (id == group.probeId) {
                    orders.put(id, new Order(ObjectiveAssignment.defendArea(id, group.probeX, group.probeY,
                            PROBE_RADIUS_CELLS), group.stage == Stage.MUSTER ? Stage.MUSTER : Stage.PROBE));
                    continue;
                }
                orders.put(id, new Order(ObjectiveAssignment.defendArea(id, group.stageX, group.stageY,
                        STAGE_RADIUS_CELLS), group.stage == Stage.PROBE ? Stage.RESERVE : group.stage));
            }
        }
        if (group != null) notes.add(standing(byId, place, hostiles, losses));
        return new Result(orders, recruited, released, notes);
    }

    /**
     * A waiting place watched by own squads (whoever stands near it) for a couple of pulses holding no more than could now
     * be raised against it has thinned: it stops waiting, and what was seen is the estimate, a push's old peak included.
     */
    private void thinning(int tick, List<Place> places, List<Hostile> hostiles, Losses losses, Map<Integer, Body> byId,
                          List<Body> free, CommandTopology topology, List<String> notes) {
        if (waitUntil.isEmpty()) return;
        float raisable = -1f;
        for (Place place : places) {
            if (!place.turned() || !waiting(tick, place.key())) continue;
            Estimate estimate = estimates.get(place.key());
            if (estimate == null || estimate.observedTick != tick) continue;
            if (raisable < 0f) {
                List<Body> recruitable = withSortie(recruitable(free, byId.keySet(), topology), byId, topology);
                raisable = raisable(strength(byId.values()), byId.size(), recruitable);
            }
            float seen = believedAt(hostiles, place);
            int required = required(seen, losses.near(place.x(), place.y(), place.radius()), failures(place.key()),
                    doctrine.floor(), margin());
            estimate.thinPulses = goes(raisable, required) ? estimate.thinPulses + 1 : 0;
            if (estimate.thinPulses < OBSERVE_PULSES) continue;
            waitUntil.remove(place.key());
            estimate.holders = seen;
            estimate.repulsedBy = 0f;
            estimate.watchedPulses = OBSERVE_PULSES;
            estimate.thinPulses = 0;
            notes.add(String.format(Locale.ROOT, "counter-attack for %s: its holders are seen to have thinned (%.0f "
                    + "believed, %d needed, %.0f can be raised); it is attempted again", place.key(), seen, required,
                    raisable));
        }
    }

    /** Every turned place an own squad stands near: what is believed there now is what it observed. */
    private void observe(int tick, List<Place> places, List<Hostile> hostiles, List<int[]> watchers, String engaged) {
        for (Place place : places) {
            if (!place.turned()) continue;
            Estimate estimate = estimates.computeIfAbsent(place.key(), k -> new Estimate());
            boolean watched = place.key().equals(engaged);
            for (int[] watcher : watchers) {
                if (Math.hypot(watcher[0] - place.x(), watcher[1] - place.y()) <= estimate.watchRange) {
                    watched = true;
                    break;
                }
            }
            float seen = believedAt(hostiles, place);
            if (group != null && group.stage == Stage.LAUNCHED && group.key.equals(place.key())) {
                estimate.pushPeak = Math.max(estimate.pushPeak, seen);
            }
            if (!watched) continue;
            if (estimate.observedTick < 0 || tick - estimate.observedTick > OBSERVATION_FRESH_TICKS) {
                estimate.holders = seen;
                estimate.watchedPulses = 1;
            } else {
                estimate.holders = Math.max(estimate.holders, seen);
                estimate.watchedPulses++;
            }
            estimate.observedTick = tick;
        }
    }

    /** Whether the place has been watched long enough, lately enough, to size a group from. */
    private boolean observed(int tick, String key) {
        Estimate estimate = estimates.get(key);
        return estimate != null && estimate.observedTick >= 0 && tick - estimate.observedTick <= OBSERVATION_FRESH_TICKS
                && estimate.watchedPulses >= OBSERVE_PULSES;
    }

    /** Whether an own squad watched the place this pulse. */
    private boolean watchedNow(int tick, String key) {
        Estimate estimate = estimates.get(key);
        return estimate != null && estimate.observedTick == tick;
    }

    /** What the place takes now: the estimate when informed, else what is believed there this pulse. */
    private int requiredFor(Place place, List<Hostile> hostiles, Losses losses) {
        float holders = informed ? believedHolders(place.key()) : believedAt(hostiles, place);
        float lost = informed ? losses.near(place.x(), place.y(), place.radius()) : 0f;
        return required(holders, lost, failures(place.key()), doctrine.floor(), margin());
    }

    /**
     * How many of these squads the reserve law's floor keeps back: one, or with {@link Doctrine#floorOnPool} one only if
     * one of them is a free squad of the pool rather than a garrison offered to sortie.
     */
    private int floorIn(List<Body> recruitable) {
        if (!doctrine.floorOnPool() || sortieThisPulse.isEmpty()) return RESERVE_FLOOR;
        int pool = 0;
        for (Body body : recruitable) {
            boolean sortie = false;
            for (Body garrison : sortieThisPulse) {
                if (garrison.squadId() == body.squadId()) {
                    sortie = true;
                    break;
                }
            }
            if (!sortie && ++pool >= RESERVE_FLOOR) return RESERVE_FLOOR;
        }
        return pool;
    }

    /** The doctrine's margin, or the shared knob. */
    private float margin() {
        return doctrine.margin() > 0f ? doctrine.margin() : margin;
    }

    /**
     * What the side could raise: what the group holds, and the free squads it could still take within its cap, less the
     * reserve law's floor.
     */
    private float raisable(float inGroup, int groupSquads, List<Body> recruitable) {
        int n = Math.max(0, Math.min(recruitable.size() - floorIn(recruitable), capThisPulse - groupSquads));
        return inGroup + strength(recruitable.subList(0, n));
    }

    /** Sends one member to watch the place from an observation post short of it. */
    private void startProbe(int tick, Place place, Map<Integer, Body> byId, List<Body> recruitable,
                            List<Integer> recruited, CommandTopology topology, List<String> notes) {
        int[] post = observationPost(place, topology);
        Body probe = null;
        double best = Double.MAX_VALUE;
        for (int id : group.members) {
            Body body = byId.get(id);
            if (body == null || body.alive() < 1) continue;
            double gap = Math.hypot(body.x() - post[0], body.y() - post[1]);
            if (gap < best) {
                best = gap;
                probe = body;
            }
        }
        if (probe == null) {
            // A reserve with nobody in it (its share is the law's, and the pool is busy) still looks: the nearest free
            // squad goes, never the last.
            for (int i = 0; i < recruitable.size() - floorIn(recruitable); i++) {
                Body body = recruitable.get(i);
                double gap = Math.hypot(body.x() - post[0], body.y() - post[1]);
                if (gap < best) {
                    best = gap;
                    probe = body;
                }
            }
            if (probe == null) return;
            take(probe, byId, recruited);
            recruitable.remove(probe);
        }
        group.stage = Stage.PROBE;
        group.probeId = probe.squadId();
        group.probeX = post[0];
        group.probeY = post[1];
        group.probeUntil = tick + PROBE_TICKS + (int) Math.ceil(AssaultMuster.TICKS_PER_CELL * best);
        Estimate estimate = estimates.computeIfAbsent(place.key(), k -> new Estimate());
        estimate.watchRange = Math.max(OBSERVE_CELLS,
                (int) Math.ceil(Math.hypot(post[0] - place.x(), post[1] - place.y())) + PROBE_RADIUS_CELLS + 1);
        notes.add(String.format(Locale.ROOT, "counter-attack for %s probes: squad %d to observe it from (%d,%d), "
                + "by tick %d", place.key(), probe.squadId(), post[0], post[1], group.probeUntil));
    }

    /** The probe watches; once the place is observed (or the probe is lost, engaged or out of time) the group decides. */
    private void probe(int tick, Place place, List<Hostile> hostiles, Losses losses, Map<Integer, Body> byId,
                       List<Body> recruitable, List<Integer> released, List<String> notes) {
        Body probe = byId.get(group.probeId);
        String why = null;
        if (observed(tick, place.key())) {
            why = "observed";
        } else if (probe == null) {
            why = "the probe was lost";
        } else if (tick >= group.probeUntil) {
            why = "out of time at its post";
        }
        if (why == null) return;
        Estimate estimate = estimates.computeIfAbsent(place.key(), k -> new Estimate());
        if (!observed(tick, place.key())) {
            // What the probe saw (or the price it paid) is the observation: belief near the place, and its losses.
            estimate.holders = Math.max(estimate.observedTick >= 0
                    && tick - estimate.observedTick <= OBSERVATION_FRESH_TICKS ? estimate.holders : 0f,
                    believedAt(hostiles, place));
            estimate.observedTick = tick;
            estimate.watchedPulses = OBSERVE_PULSES;
        }
        if (probe == null) group.probeId = -1;
        group.stage = Stage.RESERVE;
        notes.add(String.format(Locale.ROOT, "counter-attack for %s observed it (%s): %.0f holders believed, %.0f "
                        + "remembered losses on its ground", place.key(), why, believedHolders(place.key()),
                losses.near(place.x(), place.y(), place.radius())));
        decide(tick, place, hostiles, losses, byId, recruitable, released, notes);
    }

    /** With the place observed: muster for it if what it takes can be raised, else leave it as a failed attempt. */
    private void decide(int tick, Place place, List<Hostile> hostiles, Losses losses, Map<Integer, Body> byId,
                        List<Body> recruitable, List<Integer> released, List<String> notes) {
        int required = requiredFor(place, hostiles, losses);
        float raisable = raisable(strength(group.members, byId), group.members.size(), recruitable);
        group.raisable = raisable;
        group.recruitable = recruitable.size();
        if (goes(raisable, required)) {
            group.stage = Stage.MUSTER;
            group.required = required;
            group.musterUntil = tick + MUSTER_TICKS;
            notes.add(String.format(Locale.ROOT, "counter-attack for %s musters: it is %s, it is believed to take %d "
                            + "(%.0f holders, %.0f losses) and %.0f can be raised; staging at (%d,%d), goes by tick %d",
                    place.key(), place.status().name().toLowerCase(Locale.ROOT), required,
                    informed ? believedHolders(place.key()) : believedAt(hostiles, place),
                    losses.near(place.x(), place.y(), place.radius()), raisable, group.stageX, group.stageY,
                    group.musterUntil));
        } else if (informed && standDown) {
            standDown(tick, place, required, raisable, released, notes);
        } else if (informed) {
            // Outmatched: the place is not attempted, which is an attempt spent. It is observed afresh before the next.
            group.probeId = -1;
            fail(tick, String.format(Locale.ROOT, "outmatched: believed to take %d, %.0f can be raised", required,
                    raisable), released, notes);
        }
    }

    /**
     * Outmatched: the place is not attempted, and the group is no use waiting for it. It stands down to the ordinary
     * pool, and the place is observed again later (or sooner, if it is seen to thin).
     */
    private void standDown(int tick, Place place, int required, float raisable, List<Integer> released,
                           List<String> notes) {
        int count = standDowns.merge(place.key(), 1, Integer::sum);
        int until = tick + waitTicks(count);
        waitUntil.put(place.key(), until);
        Estimate estimate = estimates.get(place.key());
        if (estimate != null) estimate.thinPulses = 0;
        notes.add(String.format(Locale.ROOT, "counter-attack for %s stands down (outmatched: believed to take %d, "
                + "%.0f can be raised); its squads go back to the pool, observed again from tick %d", place.key(),
                required, raisable, until));
        dissolve(released, notes, null);
    }

    /** Ends or advances the group already standing: success, failure, home, a place back in hand and quiet. */
    private void review(int tick, Map<String, Place> byKey, boolean keepThreatened, Map<Integer, Body> byId,
                        List<Integer> released, List<String> notes) {
        Place place = byKey.get(group.key);
        if (place == null) {
            dissolve(released, notes, "nothing of it is left to retake");
            return;
        }
        if (group.probeId >= 0 && !group.members.contains(group.probeId)) group.probeId = -1;
        if (group.stage == Stage.LAUNCHED) {
            float strength = strength(group.members, byId);
            if (!place.turned()) {
                notes.add(String.format(Locale.ROOT, "counter-attack for %s retook it at tick %d, %d ticks after "
                        + "launching, with %.0f of %.0f left", group.key, tick, tick - group.launchTick, strength,
                        group.launchedStrength));
                dissolve(released, notes, null);
            } else if (group.members.isEmpty()) {
                fail(tick, "nobody left of it", released, notes);
            } else if (place.capturing() && tick - group.launchTick < 2 * ASSAULT_TICKS) {
                // Somebody of ours is on the place taking it back: a group is not called off in the room, though a room
                // contested for twice the bound is not being won.
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
            dissolve(released, notes, "a hostile is believed near home");
        } else if (!place.turned() && (group.stage == Stage.MUSTER || group.stage == Stage.PROBE)) {
            group.stage = Stage.RESERVE;
            group.probeId = -1;
            notes.add("counter-attack for " + group.key + " stands back to reserve: it is held again");
        } else if (!place.turned() && !place.alarmed() && !(doctrine.formOnContact() && contactSeen)
                && !doctrine.formAtOnce()) {
            dissolve(released, notes, "it is held and quiet");
        }
    }

    /**
     * The place the reserve should stand for: a turned place most worth it, else a held place whose own alarm stands,
     * else (with {@link #formOnContact}, once the battle has reached the side) the held place most worth guarding.
     */
    private Place choose(int tick, List<Place> places, int[] home, int[] keep, List<Hostile> hostiles) {
        Place best = null;
        int bestTier = Integer.MAX_VALUE;
        float bestValue = -1f;
        double bestGap = Double.MAX_VALUE;
        for (Place place : places) {
            if (abandoned.contains(place.key()) || waiting(tick, place.key())) continue;
            double gap = Math.hypot(place.x() - home[0], place.y() - home[1]);
            if (keep != null && gap > doctrine.maxRetakeCells()) continue;
            float value = value(place.worth(), gap, doctrine.maxRetakeCells());
            if (value <= 0f) continue;
            int tier;
            if (place.turned()) {
                tier = 0;
            } else if (place.alarmed() && formOnAlarm) {
                tier = 1;
            } else if (doctrine.formAtOnce() || doctrine.formOnContact() && contactSeen) {
                tier = 2;
            } else {
                continue;
            }
            if (tier < bestTier || tier == bestTier && (value > bestValue || value == bestValue && gap < bestGap)) {
                best = place;
                bestTier = tier;
                bestValue = value;
                bestGap = gap;
            }
        }
        return best;
    }

    private void form(int tick, Place place, int[] home, List<Hostile> hostiles, CommandTopology topology,
                      List<String> notes) {
        group = new Group(place.key());
        int[] cell = stageCell(home, new int[] {place.x(), place.y()}, hostiles, topology);
        group.stageX = cell[0];
        group.stageY = cell[1];
        notes.add("counter-attack reserve for " + place.key() + " formed at (" + cell[0] + "," + cell[1] + "), tick "
                + tick);
    }

    private void muster(int tick, List<Body> recruitable, Map<Integer, Body> byId, List<Integer> recruited,
                        List<Integer> released, List<String> notes) {
        List<Body> nearest = new ArrayList<>(recruitable);
        nearest.removeIf(body -> group.members.contains(body.squadId()));
        nearest.sort(Comparator.<Body>comparingDouble(b -> Math.hypot(b.x() - group.stageX, b.y() - group.stageY))
                .thenComparingInt(Body::squadId));
        int room = Math.min(nearest.size() - floorIn(nearest), capThisPulse - group.members.size());
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
            int x = id == group.probeId ? group.probeX : group.stageX;
            int y = id == group.probeId ? group.probeY : group.stageY;
            if (Math.hypot(body.x() - x, body.y() - y) <= RALLY_RADIUS_CELLS) {
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
        } else if (allArrived && exhausted && goes(arrived, group.required)) {
            why = "everything it can raise is at the staging cell";
        } else if (tick >= group.musterUntil) {
            if (goes(arrived, group.required)) {
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
        launches++;
        group.launchedStrength = strength(group.members, byId);
        Estimate estimate = estimates.get(group.key);
        if (estimate != null) estimate.pushPeak = 0f;
        notes.add(String.format(Locale.ROOT, "counter-attack for %s launches (%s): %d squads, strength %.0f "
                        + "against %d believed needed, from (%d,%d)", group.key, why, group.members.size(),
                group.launchedStrength, group.required, group.stageX, group.stageY));
    }

    private void launchOrders(int tick, Place place, Map<Integer, Body> byId, CommandTopology topology,
                              Map<Integer, Order> orders, List<Integer> released, List<String> notes) {
        List<Body> bodies = new ArrayList<>();
        for (int id : group.members) bodies.add(byId.get(id));
        Map<Integer, ObjectiveAssignment> tasks = place.assault().orders(bodies, topology);
        if (tasks.isEmpty()) {
            fail(tick, "no part of it can be reached", released, notes);
            return;
        }
        for (Map.Entry<Integer, ObjectiveAssignment> task : tasks.entrySet()) {
            orders.put(task.getKey(), new Order(task.getValue(), Stage.LAUNCHED));
        }
        for (int id : new ArrayList<>(group.members)) {
            if (tasks.containsKey(id)) continue;
            group.members.remove(id);
            released.add(id);
        }
    }

    /** A failed attempt: counted, what it met remembered, the place left for a while, survivors back, or given up. */
    private void fail(int tick, String why, List<Integer> released, List<String> notes) {
        int count = failures.merge(group.key, 1, Integer::sum);
        retryAt.put(group.key, tick + RETRY_TICKS);
        Estimate estimate = estimates.get(group.key);
        if (group.stage == Stage.LAUNCHED && estimate != null) {
            float before = Math.max(estimate.holders, estimate.repulsedBy);
            estimate.repulsedBy = Math.max(estimate.repulsedBy, estimate.pushPeak);
            notes.add(String.format(Locale.ROOT, "counter-attack for %s estimate after the repulse: %.0f holders "
                    + "believed, was %.0f", group.key, Math.max(estimate.holders, estimate.repulsedBy), before));
        }
        group.probeId = -1;
        if (count >= MAX_ATTEMPTS) {
            abandoned.add(group.key);
            notes.add("counter-attack for " + group.key + " gives up (" + why + "); " + group.key
                    + " abandoned after " + count + " failed attempts");
            dissolve(released, notes, null);
            return;
        }
        group.stage = Stage.RESERVE;
        group.launchTick = -1;
        notes.add("counter-attack for " + group.key + " gives up (" + why + "); attempt " + count + " of "
                + MAX_ATTEMPTS + ", survivors fall back to (" + group.stageX + "," + group.stageY + "), next decision "
                + "from tick " + (tick + RETRY_TICKS));
    }

    private void dissolve(List<Integer> released, List<String> notes, String why) {
        released.addAll(group.members);
        if (why != null) notes.add("counter-attack for " + group.key + " dissolved: " + why);
        group = null;
    }

    /** Fills the reserve to its share with the free squads furthest from the place. */
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

    /** The members above the law's share, nearest the place first: the ones its garrisonables may borrow. */
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

    /**
     * The sortie-able garrisons of this pulse ahead of the free patrols: the reserve law's floor is taken from the
     * patrols at the tail, never from a garrison, so the last free patrol stays the floor it always was.
     */
    private List<Body> withSortie(List<Body> recruitable, Map<Integer, Body> byId, CommandTopology topology) {
        if (sortieThisPulse.isEmpty()) return recruitable;
        List<Body> out = new ArrayList<>(recruitable(sortieThisPulse, byId.keySet(), topology));
        for (Body body : recruitable) {
            if (out.stream().noneMatch(other -> other.squadId() == body.squadId())) out.add(body);
        }
        return out;
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
            if (id == group.probeId) group.probeId = -1;
        }
    }

    private boolean reaches(Body body, CommandTopology topology) {
        // With no group there is no staging cell yet: what could be raised is judged on strength alone.
        if (group == null) return true;
        if (body.x() == group.stageX && body.y() == group.stageY) return true;
        return topology.inBounds(body.x(), body.y()) && topology.reachable(body.x(), body.y(), group.stageX, group.stageY);
    }

    /**
     * Where a probe watches from: {@link #OBSERVE_CELLS} short of the place's centre on the line from the staging cell,
     * on walkable ground reachable from it, or the staging cell when none is.
     */
    private int[] observationPost(Place place, CommandTopology topology) {
        double dx = group.stageX - place.x();
        double dy = group.stageY - place.y();
        double gap = Math.hypot(dx, dy);
        if (gap <= OBSERVE_CELLS) return new int[] {group.stageX, group.stageY};
        // As close as a rifleman's sight of the middle; for a place whose near ground cannot be stood on (a fort's works),
        // out toward the edge of its ground.
        int outer = (int) Math.min(gap, Math.max(OBSERVE_CELLS, place.radius()));
        for (double out = OBSERVE_CELLS - 4; out <= outer; out += 1.0) {
            int x = (int) Math.round(place.x() + dx / gap * out);
            int y = (int) Math.round(place.y() + dy / gap * out);
            for (int ring = 0; ring <= 3; ring++) {
                for (int oy = -ring; oy <= ring; oy++) {
                    for (int ox = -ring; ox <= ring; ox++) {
                        if (Math.max(Math.abs(ox), Math.abs(oy)) != ring) continue;
                        int cx = x + ox;
                        int cy = y + oy;
                        if (Math.hypot(cx - place.x(), cy - place.y()) > Math.max(OBSERVE_CELLS, out + 1)) continue;
                        if (!topology.inBounds(cx, cy) || !topology.isWalkable(cx, cy)) continue;
                        if (topology.reachable(group.stageX, group.stageY, cx, cy)) return new int[] {cx, cy};
                    }
                }
            }
        }
        return new int[] {group.stageX, group.stageY};
    }

    private String standing(Map<Integer, Body> byId, Place place, List<Hostile> hostiles, Losses losses) {
        return String.format(Locale.ROOT, "counter-attack %s %s: %d squads, strength %.0f, believes it takes %d "
                        + "(%.0f holders believed, %.0f near it now), staging (%d,%d)%s, could raise %.0f from %d free%s",
                group.key, group.stage.name().toLowerCase(Locale.ROOT), group.members.size(),
                strength(group.members, byId),
                group.stage == Stage.RESERVE || group.stage == Stage.PROBE ? requiredFor(place, hostiles, losses)
                        : group.required,
                believedHolders(place.key()), believedAt(hostiles, place), group.stageX, group.stageY,
                group.probeId >= 0 ? ", probe " + group.probeId + " at (" + group.probeX + "," + group.probeY + ")" : "",
                group.raisable, group.recruitable,
                group.stage == Stage.LAUNCHED ? ", launched at tick " + group.launchTick : "");
    }

    /** The staging cell: {@link AssaultMuster#STAGING_STANDOFF_CELLS} short of the place on the line from home, clear of believed contact. */
    private static int[] stageCell(int[] home, int[] centre, List<Hostile> hostiles, CommandTopology topology) {
        AssaultMuster.Ground ground = (x, y) -> topology.inBounds(x, y) && topology.isWalkable(x, y)
                && (x == home[0] && y == home[1] || topology.reachable(home[0], home[1], x, y));
        return AssaultMuster.rallyCell(home[0], home[1], centre[0], centre[1], ground, cells(hostiles));
    }

    private static List<int[]> cells(List<Hostile> hostiles) {
        List<int[]> out = new ArrayList<>(hostiles.size());
        for (Hostile hostile : hostiles) out.add(new int[] {hostile.x(), hostile.y()});
        return out;
    }

    private static boolean anyWithin(List<Hostile> hostiles, int x, int y, int radius) {
        for (Hostile hostile : hostiles) if (Math.hypot(hostile.x() - x, hostile.y() - y) <= radius) return true;
        return false;
    }

    /** Where the side stands: the cell of the squad nearest the bodies' mean, or {@code null} with none. */
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
        if (n == 0) return null;
        // The squad standing nearest the mean, not the mean itself: a mean between buildings can fall in a wall, and a
        // staging cell is only ever chosen among cells reachable from home.
        float mx = (float) x / n;
        float my = (float) y / n;
        Body nearest = null;
        double best = Double.MAX_VALUE;
        for (List<Body> bodies : List.of(members, free)) {
            for (Body body : bodies) {
                double d = Math.hypot(body.x() - mx, body.y() - my);
                if (d < best) {
                    best = d;
                    nearest = body;
                }
            }
        }
        return new int[] {nearest.x(), nearest.y()};
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
