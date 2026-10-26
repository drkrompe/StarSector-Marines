package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A screened advance ({@code allied-command.md}, Screened advance): two elements moving to a rally point
 * as one formation, the <b>screen</b> a few cells in front of the <b>main</b> (or abreast of it on its
 * flanks), each squad and machine keeping its own slot the whole way. Either element can be infantry,
 * machines or both: militia screening tanks through woods, or APCs screening marines across open ground.
 *
 * <p>The formation has one <b>anchor</b>, a distance along the route; every slot is fixed relative to it.
 * The anchor moves only as fast as the slowest element keeps up, so a tank never leaves its infantry in the
 * open and a screen is never left out in front of armour that has fallen behind.
 *
 * <p>It is a <b>fighting advance</b>: when the formation is engaged its progress toward the rally point pauses,
 * but it does not freeze. It turns to face the threat, the main holding where it stands on the route and the
 * screen swinging round to stand between it and the threat, and every element fights from its slot. It
 * picks the advance back up once the contact has cleared (nothing hostile in range and nothing seen by any
 * squad of it, for a few seconds). A contact it cannot clear is capped: at the cap it pushes on
 * fighting for as long again before a contact can hold it once more, or hands the elements back to the mission. A screen worn below its floor either halts the
 * advance or hands the lead to the main.
 *
 * <p>A third element, the <b>support</b>, may trail the main by {@code supportBehind} cells: fire-support carriers,
 * mortar teams, whatever fights from behind the line by reaching over it. It moves up with the formation and fights
 * from its slot like the others, but it never holds the advance or its rally: it follows rather than being waited
 * for. A contact turns it with the rest, still behind the main.
 *
 * <p>Mission-neutral and owned by the mission, which advances it from its own serial tick before its
 * {@link AlliedCommand}: an authored battle declares its elements, route, trigger and, where something
 * else sets how far it may go (a walking barrage), a limit; generic command can take the defaults. Hulls
 * and mechs are driven through the allied command's station orders and squads by attack-move under
 * {@code GARRISON} authority, so every element fights on its own doctrine wherever it is put.
 */
public final class ScreenedAdvance {

    /** Whether the advance may go: on a tick, after a beat, when a barrage line lifts. */
    @FunctionalInterface
    public interface Trigger {
        boolean ready(BattleSimulation sim);

        static Trigger now() {
            return sim -> true;
        }

        static Trigger atTick(int tick) {
            return sim -> sim.getSimTickIndex() >= tick;
        }

        static Trigger atSeconds(float seconds) {
            return atTick(Math.round(seconds / BattleSimulation.TICK_DT));
        }
    }

    /** How far along the route the formation may go now: a barrage's hold, an objective not yet taken. */
    @FunctionalInterface
    public interface Limit {
        float along(BattleSimulation sim);

        Limit NONE = sim -> Float.POSITIVE_INFINITY;
    }

    /** Where the screen stands relative to the main. */
    public enum Screening {
        /** A line abreast {@code screenAhead} cells in front of the main's. */
        AHEAD,
        /** Split between the main's two flanks, abreast of it on one line. */
        FLANKS
    }

    /** What happens when the screen is worn below its floor. */
    public enum Broken {
        /** Both elements halt where they stand and the advance ends there. */
        HOLD,
        /** What is left of the screen joins the main, which goes on as an unscreened advance. */
        MAIN_LEADS
    }

    /** What a fighting advance does with a contact it has not cleared by the cap. */
    public enum Stalled {
        /**
         * Faces the route again and goes on, fighting as it moves, for as long as the contact held it before a contact
         * can hold it again.
         */
        PUSH_THROUGH,
        /** Suspends itself and raises {@code .stalled}: the mission decides, and resumes it when it wants. */
        HAND_BACK
    }

    public enum Phase { WAITING, ADVANCING, CONTACT, RALLIED, HALTED }

    /** One side of the advance: its squads, hulls and mechs. Members may join after the advance has begun. */
    public static final class Element {
        final List<Integer> squads = new ArrayList<>();
        final List<Long> hulls = new ArrayList<>();
        final List<Long> mechs = new ArrayList<>();

        public Element squads(List<Integer> ids) { squads.addAll(ids); return this; }
        public Element squad(int id) { squads.add(id); return this; }
        public Element hulls(List<Long> ids) { hulls.addAll(ids); return this; }
        public Element hull(long id) { hulls.add(id); return this; }
        /** Whether the hull or mech {@code id} is of this element. */
        public boolean has(long id) { return hulls.contains(id) || mechs.contains(id); }
        public Element mechs(List<Long> ids) { mechs.addAll(ids); return this; }
        public Element mech(long id) { mechs.add(id); return this; }

        int size() { return squads.size() + hulls.size() + mechs.size(); }
    }

    /** Default cells the screen's line stands in front of the main's. */
    public static final float DEFAULT_SCREEN_AHEAD = 6f;
    /** Default cells the support's line trails the main's. */
    public static final float DEFAULT_SUPPORT_BEHIND = 10f;
    /** Default cells between neighbours across the advance. */
    public static final float DEFAULT_SPACING = 6f;
    /** Default cells from a screen member within which a hostile is contact. */
    public static final float DEFAULT_CONTACT_RANGE = 16f;
    /**
     * Cells the anchor may run ahead of where its slowest element has got to: each slot is this far in front
     * of whoever is furthest behind, so the slowest walks on without stopping and the fastest waits for it.
     */
    public static final float KEEP_UP = 6f;
    /** Cells a slot moves at most per decision: the formation's top pace, about a walking marine's. */
    public static final float MAX_STEP = 1.5f;
    /** Cells from its slot a squad's centre, a hull or a mech counts as in place. */
    public static final float ARRIVE_REACH = 6f;
    /** Share of an element's living squads and machines that must be in place for the element to be. */
    public static final float ARRIVE_SHARE = 2f / 3f;
    /** Seconds the contact must have been clear before an engaged formation picks the advance back up. */
    public static final float CONTACT_CLEAR_SECONDS = 5f;
    /** Seconds a contact may hold the formation before the {@link Stalled} policy decides. */
    public static final float CONTACT_HOLD_SECONDS = 45f;
    /** Seconds since a squad of the formation last saw a hostile within which the formation is still engaged by it. */
    static final float SEEN_SECONDS = 1f;
    /** Share of the screen's strength at step-off below which it is broken. */
    public static final float SCREEN_FLOOR = 1f / 3f;
    /**
     * Seconds a member off its slot may stand without moving a cell before it is a <b>straggler</b>: out of its
     * element's body until it moves again, so a hull that cannot find its way or a squad pinned down cannot hold
     * the formation where it stands.
     */
    public static final float STRAGGLE_SECONDS = 10f;
    static final int STRAGGLE_TICKS = Math.round(STRAGGLE_SECONDS / BattleSimulation.TICK_DT);

    /**
     * Whether one member keeps up with the formation. A member is <b>on the line</b> once it has stood on its slot:
     * the members it stepped off with are, and one that joins later is not until it has walked up to its slot.
     * It keeps up while it is on the line and either on its slot or still moving; one that has stood
     * {@link #STRAGGLE_SECONDS} off its slot without moving a cell has fallen out until it moves again. Only
     * members that keep up are the body the anchor waits for: the anchor never runs further ahead of the body
     * than {@link #KEEP_UP}, and a member on its slot cannot go further forward, so one that cannot reach its own
     * slot would otherwise hold the formation for good.
     */
    static final class Pace {
        private boolean onLine;
        private float x = Float.NaN, y = Float.NaN;
        private int movedSince;
        /** Whether it stood in place at its last observation, and its slot's distance along the route then. */
        private boolean inPlace;
        private float slotAlong = Float.NaN;
        /** Where it last moved a cell from, in place or not, and when. */
        private float stillX = Float.NaN, stillY = Float.NaN;
        private int stillSince;

        Pace(boolean onLine, int tick) {
            this.onLine = onLine;
            movedSince = tick;
            stillSince = tick;
        }

        /** The member stands at {@code (x, y)} at {@code tick}, on its slot or not. */
        void observe(int tick, float x, float y, boolean inPlace) {
            this.inPlace = inPlace;
            if (Float.isNaN(stillX) || Math.hypot(x - stillX, y - stillY) >= 1f) {
                stillX = x;
                stillY = y;
                stillSince = tick;
            }
            if (inPlace) onLine = true;
            if (inPlace || Float.isNaN(this.x) || Math.hypot(x - this.x, y - this.y) >= 1f) {
                this.x = x;
                this.y = y;
                movedSince = tick;
            }
        }

        boolean keepsUp(int tick) {
            return onLine && tick - movedSince < STRAGGLE_TICKS;
        }

        /** Its slot stands {@code along} the route. */
        void slotAt(float along) {
            slotAlong = along;
        }

        /**
         * How far along the route it has got at {@code tick}, for the anchor's keep-up: its slot's distance once it has
         * stood in place without moving a cell for {@link #STRAGGLE_TICKS}, else {@code along}, where it stands. One
         * still walking up inside its reach holds the anchor as it always did.
         */
        float keepUpAlong(float along, int tick) {
            return inPlace && tick - stillSince >= STRAGGLE_TICKS && !Float.isNaN(slotAlong) ? slotAlong : along;
        }
    }

    /** Cells a squad's slot must move before it is ordered again. */
    static final int RESTATION = 3;
    /** Ticks between the advance's decisions. */
    static final int DECIDE_TICKS = Math.round(0.5f / BattleSimulation.TICK_DT);

    private final String name, issuer;
    private final AlliedCommand allied;
    private final AdvanceRoute route;
    private final Trigger start;
    private final Limit limit;
    private final Faction side;
    private final Screening screening;
    private final float screenAhead, supportBehind, spacing, frontage, contactRange, contactClear, contactHold;
    private final Broken broken;
    private final Stalled stalled;
    private final Element screen, main, support;
    private final Consumer<String> beats;

    private Phase phase = Phase.WAITING;
    private int phaseSince;
    private int quietSince;
    /**
     * The tick until which a pushed advance goes on through its contact. It went straight back into the contact it had
     * pushed, so a swarm that never let up held it where it was: the heart's lanes stood eighty cells short of the
     * mothers from the last satellite's fall to the cap (Broad Front frozen 5, 2026-10-25).
     */
    private int pushingUntil;
    private int contacts;
    private int screenStrength;
    /** Distance along the route of the main's line; the screen's slots are fixed relative to it. */
    private float anchor = Float.NaN;
    /** Which way the formation faces while engaged: toward the threat. Null while it faces along the route. */
    private float[] facing;
    private boolean suspended;
    private final Map<Integer, int[]> squadOrders = new HashMap<>();
    /** Each member's pace, keyed {@code kind:id}; a member with none stepped off with the formation. */
    private final Map<String, Pace> paces = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    private ScreenedAdvance(Builder b) {
        name = b.name;
        issuer = b.issuer;
        allied = b.allied;
        route = b.route;
        start = b.start;
        limit = b.limit;
        side = b.side;
        screening = b.screening;
        screenAhead = b.screening == Screening.FLANKS ? 0f : b.screenAhead;
        spacing = b.spacing;
        frontage = b.frontage;
        contactRange = b.contactRange;
        contactClear = b.contactClear;
        contactHold = b.contactHold;
        broken = b.broken;
        stalled = b.stalled;
        screen = b.screen;
        main = b.main;
        support = b.support;
        supportBehind = b.supportBehind;
        beats = b.beats;
    }

    public static Builder builder(String name, String issuer, AlliedCommand allied, AdvanceRoute route) {
        return new Builder(name, issuer, allied, route);
    }

    /** How a mission declares an advance. Everything but the elements has a default. */
    public static final class Builder {
        final String name, issuer;
        final AlliedCommand allied;
        final AdvanceRoute route;
        Trigger start = Trigger.now();
        Limit limit = Limit.NONE;
        Faction side = Faction.ALLY;
        Screening screening = Screening.AHEAD;
        float screenAhead = DEFAULT_SCREEN_AHEAD, spacing = DEFAULT_SPACING, frontage = Float.MAX_VALUE;
        float contactRange = DEFAULT_CONTACT_RANGE, contactClear = CONTACT_CLEAR_SECONDS, contactHold = CONTACT_HOLD_SECONDS;
        Broken broken = Broken.HOLD;
        Stalled stalled = Stalled.PUSH_THROUGH;
        Element screen = new Element(), main = new Element(), support = new Element();
        float supportBehind = DEFAULT_SUPPORT_BEHIND;
        Consumer<String> beats = beat -> { };

        Builder(String name, String issuer, AlliedCommand allied, AdvanceRoute route) {
            this.name = name;
            this.issuer = issuer;
            this.allied = allied;
            this.route = route;
        }

        public Builder screen(Element element) { screen = element; return this; }
        public Builder main(Element element) { main = element; return this; }
        /** The support, a line {@code behind} cells behind the main's that follows the formation and never holds it. */
        public Builder support(Element element, float behind) { support = element; supportBehind = behind; return this; }
        public Builder startWhen(Trigger trigger) { start = trigger; return this; }
        /** The formation's main line never goes further along the route than this says. */
        public Builder limitedBy(Limit cap) { limit = cap; return this; }
        /** The side the elements fight for; contact is what is hostile to it. */
        public Builder side(Faction faction) { side = faction; return this; }
        /** The screen a line {@code cells} in front of the main. */
        public Builder screenAhead(float cells) { screening = Screening.AHEAD; screenAhead = cells; return this; }
        /** The screen split between the main's flanks, abreast of it. */
        public Builder screenOnFlanks() { screening = Screening.FLANKS; return this; }
        public Builder spacing(float cells) { spacing = cells; return this; }
        /** The widest a line may be: a large formation closes up its spacing to fit a lane. */
        public Builder frontage(float cells) { frontage = cells; return this; }
        public Builder contactRange(float cells) { contactRange = cells; return this; }
        /**
         * Seconds the contact must have been clear before the formation goes on, and the most a contact may
         * hold it. A battle where contact never lets up (a swarm) wants a short cap, so the advance keeps its pace.
         */
        public Builder contactTimes(float clearSeconds, float holdSeconds) { contactClear = clearSeconds; contactHold = holdSeconds; return this; }
        public Builder onScreenBroken(Broken policy) { broken = policy; return this; }
        public Builder onStalled(Stalled policy) { stalled = policy; return this; }
        /**
         * Where the advance's beats go: {@code <name>.step_off}, {@code .contact_N}, {@code .cleared_N},
         * {@code .pushed_N}, {@code .stalled}, {@code .rallied}, {@code .screen_broken}.
         */
        public Builder beats(Consumer<String> sink) { beats = sink; return this; }

        public ScreenedAdvance build() { return new ScreenedAdvance(this); }
    }

    public Phase phase() { return phase; }
    public AdvanceRoute route() { return route; }
    public Element screen() { return screen; }
    public Element main() { return main; }
    public Element support() { return support; }
    /** Distance along the route of the main's line, or NaN before step-off. */
    public float anchor() { return anchor; }
    /** Cells along the route the screen's line stands in front of the main's. */
    public float screenAhead() { return screenAhead; }
    /** The way the formation faces while engaged, a unit vector toward the threat; null while it faces along the route. */
    public float[] facing() { return facing == null ? null : facing.clone(); }
    /** Where the anchor stops: the screen's line on the rally point, the main's just behind it. */
    public float rallyAnchor() { return Math.max(0f, route.length() - screenAhead); }
    /** Where the formation stands, for a probe: phase, anchor against its end and limit, each element's body and place. */
    public String describe(BattleSimulation sim) {
        float[] s = body(sim, screen), m = body(sim, main);
        return String.format("%s anchor %.1f of %.1f limit %.1f slowest %.1f screen %s%s main %s%s%s", phase, anchor,
                rallyAnchor(), limit.along(sim), slowest(sim),
                s == null ? "-" : String.format("%.1f@%.0f,%.0f", s[2], s[0], s[1]), inPlace(sim, screen) ? " in place" : "",
                m == null ? "-" : String.format("%.1f@%.0f,%.0f", m[2], m[0], m[1]), inPlace(sim, main) ? " in place" : "",
                suspended ? " suspended" : "") + members(sim);
    }

    /** Each machine's slot and where it stands, for {@link #describe}. */
    private String members(BattleSimulation sim) {
        StringBuilder out = new StringBuilder();
        for (Slot slot : slots()) {
            if (slot.kind == 0) continue;
            float[] p = where(sim, slot.kind, slot.id, Map.of());
            if (p == null) continue;
            int[] at = slot.cell();
            out.append(String.format(" | %s%d slot %d,%d at %.0f,%.0f%s", slot.kind == 1 ? "hull" : "mech", slot.id,
                    at[0], at[1], p[0], p[1], keepsUp(sim, slot.kind, slot.id) ? "" : " straggling"));
        }
        return out.toString();
    }

    /** What the advance has done and when, one line each, for a report. */
    public List<String> log() { return log; }

    /** A squad joins the main, or the screen when {@code toMain} is false; the next decision gives it its slot. */
    public void joinInfantry(int squad, boolean toMain) {
        (toMain ? main : screen).squads.add(squad);
        joinedLate(0, squad);
    }

    /** A hull or a mech joins the screen, or the main when {@code toScreen} is false. */
    public void joinMachine(long id, boolean mech, boolean toScreen) {
        Element element = toScreen ? screen : main;
        if (mech) element.mechs.add(id);
        else element.hulls.add(id);
        joinedLate(mech ? 2 : 1, id);
    }

    /** A squad, hull or mech joins the support. {@code kind} is 0 for a squad, 1 a hull, 2 a mech. */
    public void joinSupport(int kind, long id) {
        if (kind == 0) support.squads.add((int) id);
        else if (kind == 1) support.hulls.add(id);
        else support.mechs.add(id);
        joinedLate(kind, id);
    }

    /** A member joining after step-off is off the line until it has walked up to its slot. */
    private void joinedLate(int kind, long id) {
        if (phase != Phase.WAITING) paces.put(kind + ":" + id, new Pace(false, 0));
    }

    /** Every member's pace, read from where each stands against its slot. Once a decision, before the body is asked. */
    private void pace(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        Map<Integer, float[]> centres = squadCentres(sim, screen);
        centres.putAll(squadCentres(sim, main));
        centres.putAll(squadCentres(sim, support));
        for (Slot slot : slots()) {
            float[] p = where(sim, slot.kind, slot.id, centres);
            if (p == null) continue;
            int[] at = slot.cell();
            Pace pace = paces.computeIfAbsent(slot.kind + ":" + slot.id, k -> new Pace(true, tick));
            pace.observe(tick, p[0], p[1], Math.hypot(p[0] - at[0] - .5f, p[1] - at[1] - .5f) <= ARRIVE_REACH);
            pace.slotAt(route.along(at[0] + .5f, at[1] + .5f));
        }
    }

    /** Whether a member keeps up ({@link Pace}); one never paced stepped off with the formation and does. */
    private boolean keepsUp(BattleSimulation sim, int kind, long id) {
        Pace pace = paces.get(kind + ":" + id);
        return pace == null || pace.keepsUp(sim.getSimTickIndex());
    }

    /** Where a member stands: a squad's centre, a hull or a mech; null when it no longer lives. */
    private static float[] where(BattleSimulation sim, int kind, long id, Map<Integer, float[]> centres) {
        if (kind == 0) return centres.get((int) id);
        if (kind == 1) return AlliedCommand.alive(sim.convoy(), id) ? new float[]{sim.convoy().body(id).x, sim.convoy().body(id).y} : null;
        return mechAlive(sim, id) ? new float[]{sim.world().x(id), sim.world().y(id)} : null;
    }

    /** Whether the advance is still going: neither rallied nor halted. */
    public boolean active() {
        return phase != Phase.RALLIED && phase != Phase.HALTED;
    }

    public boolean suspended() { return suspended; }

    /**
     * Hands the elements back to the mission for a while: the advance gives no orders until {@link #resume}.
     * A battle with a side task on the way (a satellite to take) suspends the advance for it.
     */
    public void suspend() {
        suspended = true;
    }

    /** Takes the elements back: the formation re-forms where its slowest element now stands. */
    public void resume(BattleSimulation sim) {
        if (!suspended) return;
        suspended = false;
        squadOrders.clear();
        if (phase == Phase.WAITING) return;
        anchor = Math.min(slowest(sim), Math.max(0f, anchor));
        post(sim, "re-form");
    }

    /** One tick; decisions are taken twice a second. */
    public void advance(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (suspended || tick % DECIDE_TICKS != 0 || !active()) return;
        prune(sim, screen);
        prune(sim, main);
        prune(sim, support);
        if (phase != Phase.WAITING) pace(sim);
        switch (phase) {
            case WAITING -> {
                if (!start.ready(sim)) return;
                screenStrength = strength(sim, screen);
                // The formation forms on its slowest element: nobody steps back to it, nobody runs on.
                anchor = Math.max(0f, Math.min(slowest(sim), limit.along(sim)));
                beat(sim, "step_off");
                phase(sim, Phase.ADVANCING);
                post(sim, "form up");
            }
            case ADVANCING -> {
                if (brokenScreen(sim)) return;
                float[] threat = threat(sim);
                if (engages(threat != null, tick, pushingUntil)) {
                    contacts++;
                    beat(sim, "contact_" + contacts);
                    quietSince = tick;
                    phase(sim, Phase.CONTACT);
                    face(sim, threat);
                    return;
                }
                float end = rallyAnchor();
                float next = Math.min(Math.min(anchor + MAX_STEP, end), Math.min(slowest(sim) + KEEP_UP, limit.along(sim)));
                if (next > anchor) {
                    anchor = next;
                    post(sim, "advance in formation");
                }
                if (anchor >= end && inPlace(sim, screen) && inPlace(sim, main)) {
                    phase(sim, Phase.RALLIED);
                    beat(sim, "rallied");
                }
            }
            case CONTACT -> {
                if (brokenScreen(sim)) return;
                float[] threat = threat(sim);
                if (threat != null) quietSince = tick;
                // The formation keeps facing the threat as it moves round: a flank contact turns it.
                if (threat != null) face(sim, threat);
                if (tick - quietSince >= seconds(contactClear)) {
                    beat(sim, "cleared_" + contacts);
                    goOn(sim);
                } else if (tick - phaseSince >= seconds(contactHold)) {
                    if (stalled == Stalled.HAND_BACK) {
                        beat(sim, "stalled");
                        facing = null;
                        phase(sim, Phase.ADVANCING);
                        suspend();
                    } else {
                        beat(sim, "pushed_" + contacts);
                        pushingUntil = tick + seconds(contactHold);
                        goOn(sim);
                    }
                }
            }
            default -> { }
        }
    }

    /** Whether a threat in range holds an advancing formation now: not while it is pushing on through one. */
    static boolean engages(boolean threat, int tick, int pushingUntil) {
        return threat && tick >= pushingUntil;
    }

    /** Faces the route again and picks the advance back up. */
    private void goOn(BattleSimulation sim) {
        facing = null;
        phase(sim, Phase.ADVANCING);
        post(sim, "pick the advance back up");
    }

    /** Turns the formation to face {@code threat} about the anchor's point, re-posting only for a real turn. */
    private void face(BattleSimulation sim, float[] threat) {
        float[] at = route.pointAt(anchor);
        float dx = threat[0] - at[0], dy = threat[1] - at[1];
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) return;
        float[] next = {dx / length, dy / length};
        if (facing != null && facing[0] * next[0] + facing[1] * next[1] > FACE_AGAIN) return;
        facing = next;
        post(sim, "face the threat");
    }

    /** Cosine of the turn below which an engaged formation does not re-face: about twenty degrees. */
    static final float FACE_AGAIN = 0.94f;

    /**
     * How far the anchor has got for the element furthest behind: each element's body less its own slot's
     * offset in front of the anchor, the least of them. A straggler (a hull pulling out to withdraw, a squad
     * pinned down) is not the body, so it cannot hold the formation; it is left to catch up.
     */
    private float slowest(BattleSimulation sim) {
        float least = Float.POSITIVE_INFINITY;
        float[] s = body(sim, screen, true), m = body(sim, main, true);
        if (s != null) least = Math.min(least, s[2] - screenAhead);
        if (m != null) least = Math.min(least, m[2]);
        // Nothing keeps up (every member straggling, or still walking up to its slot): the formation holds.
        if (least == Float.POSITIVE_INFINITY) return Float.isNaN(anchor) ? 0f : anchor - KEEP_UP;
        return least;
    }

    /**
     * An element's <b>body</b>: the leading {@link #ARRIVE_SHARE} of its squads and machines that keep up
     * ({@link Pace}) along the route, each squad counted once at its centre. Returns {@code [x, y, along]} of their mean, or null when nothing
     * of the element lives and keeps up.
     */
    public float[] body(BattleSimulation sim, Element element) {
        return body(sim, element, false);
    }

    /**
     * As {@link #body(BattleSimulation, Element)}; {@code keepUp}, each member standing still in place is counted along
     * the route at its slot rather than where it stands ({@link Pace#keepUpAlong}). The anchor runs at most {@link #KEEP_UP} past the slowest, and a
     * member within {@link #ARRIVE_REACH} of its slot is in place: a hull halted five cells short of a slot six ahead
     * held the anchor where its slot stayed in reach, and was never moved on (Broad Front frozen 5, 2026-10-25: lane 1
     * stood from tick 21400 to the cap).
     */
    private float[] body(BattleSimulation sim, Element element, boolean keepUp) {
        List<float[]> at = new ArrayList<>();
        Map<Integer, float[]> centres = squadCentres(sim, element);
        for (long[] member : members(element)) {
            float[] p = where(sim, (int) member[0], member[1], centres);
            if (p == null || !keepsUp(sim, (int) member[0], member[1])) continue;
            float along = route.along(p[0], p[1]);
            Pace pace = keepUp ? paces.get(member[0] + ":" + member[1]) : null;
            at.add(new float[]{p[0], p[1], pace == null ? along : pace.keepUpAlong(along, sim.getSimTickIndex())});
        }
        if (at.isEmpty()) return null;
        List<float[]> ranked = at;
        ranked.sort((a, b) -> Float.compare(b[2], a[2]));
        int keep = (int) Math.ceil(ranked.size() * ARRIVE_SHARE);
        float x = 0f, y = 0f, along = 0f;
        for (int i = 0; i < keep; i++) {
            x += ranked.get(i)[0];
            y += ranked.get(i)[1];
            along += ranked.get(i)[2];
        }
        return new float[]{x / keep, y / keep, along / keep};
    }

    /** Every squad and machine on its slot about the anchor. */
    private void post(BattleSimulation sim, String reason) {
        for (Slot slot : slots()) {
            int[] at = slot.cell();
            switch (slot.kind) {
                case 0 -> {
                    int[] cell = walkable(sim, at);
                    int[] held = squadOrders.get((int) slot.id);
                    if (held != null && Math.abs(held[0] - cell[0]) < RESTATION && Math.abs(held[1] - cell[1]) < RESTATION) continue;
                    squadOrders.put((int) slot.id, cell);
                    sim.assignSquadCommand(ObjectiveAssignment.attackMove((int) slot.id, cell[0], cell[1]),
                            CommandAuthority.GARRISON, issuer, name + ": " + reason);
                }
                // A support hull faces the way the formation does, its launchers over the line.
                case 1 -> {
                    if (slot.role == SUPPORT) allied.station(sim, slot.id, at[0], at[1], slot.fx, slot.fy);
                    else allied.station(sim, slot.id, at[0], at[1]);
                }
                default -> allied.stationMech(sim, slot.id, at[0], at[1]);
            }
        }
    }

    /**
     * One squad's or machine's place in the formation: {@code index} of {@code count} in a line abreast through
     * {@code (x, y)}, across the facing {@code (fx, fy)}. {@code kind} is 0 for a squad, 1 a hull, 2 a mech.
     */
    private record Slot(int kind, long id, int role, float x, float y, float fx, float fy,
                        int index, int count, float spacing) {
        int[] cell() {
            float across = (index - (count - 1) / 2f) * spacing;
            return new int[]{(int) Math.floor(x - fy * across), (int) Math.floor(y + fx * across)};
        }
    }

    /**
     * The formation's slots. AHEAD: the main's line through the anchor's point and the screen's line in front
     * of it. FLANKS: one line through the anchor's point, the screen's members on its two ends and the main
     * between them. Facing along the route while advancing; toward the threat while engaged.
     */
    private List<Slot> slots() {
        List<Slot> out = new ArrayList<>();
        List<long[]> s = members(screen), m = members(main);
        float[] at = route.pointAt(anchor);
        float[] dir = facing != null ? facing : route.directionAt(anchor);
        if (screening == Screening.AHEAD) {
            float[] front = facing != null
                    ? new float[]{at[0] + dir[0] * screenAhead, at[1] + dir[1] * screenAhead}
                    : route.pointAt(anchor + screenAhead);
            float[] frontDir = facing != null ? dir : route.directionAt(anchor + screenAhead);
            line(out, s, SCREEN, front, frontDir, 0, s.size());
            line(out, m, MAIN, at, dir, 0, m.size());
        } else {
            int count = s.size() + m.size(), left = s.size() / 2;
            line(out, s.subList(0, left), SCREEN, at, dir, 0, count);
            line(out, m, MAIN, at, dir, left, count);
            line(out, s.subList(left, s.size()), SCREEN, at, dir, left + m.size(), count);
        }
        List<long[]> t = members(support);
        if (!t.isEmpty()) {
            float[] back = supportPoint(route, anchor, facing, supportBehind);
            float[] backDir = facing != null ? dir : route.directionAt(Math.max(0f, anchor - supportBehind));
            line(out, t, SUPPORT, back, backDir, 0, t.size());
        }
        return out;
    }

    static final int SCREEN = 0, MAIN = 1, SUPPORT = 2;

    /**
     * The support line's point: {@code behind} cells back along the route from the anchor while advancing (never
     * before its start), or straight back from the main's point, away from the threat, while engaged.
     */
    static float[] supportPoint(AdvanceRoute route, float anchor, float[] facing, float behind) {
        float[] at = route.pointAt(anchor);
        if (facing != null) return new float[]{at[0] - facing[0] * behind, at[1] - facing[1] * behind};
        return route.pointAt(Math.max(0f, anchor - behind));
    }

    private void line(List<Slot> out, List<long[]> members, int role, float[] at, float[] dir, int first, int count) {
        float gap = count > 1 ? Math.min(spacing, frontage / (count - 1)) : spacing;
        for (int i = 0; i < members.size(); i++) {
            long[] member = members.get(i);
            out.add(new Slot((int) member[0], member[1], role, at[0], at[1], dir[0], dir[1], first + i, count, gap));
        }
    }

    /** An element's members as {@code [kind, id]}: squads, then hulls, then mechs. */
    private static List<long[]> members(Element element) {
        List<long[]> out = new ArrayList<>();
        for (int squad : element.squads) out.add(new long[]{0, squad});
        for (long hull : element.hulls) out.add(new long[]{1, hull});
        for (long mech : element.mechs) out.add(new long[]{2, mech});
        return out;
    }

    private boolean brokenScreen(BattleSimulation sim) {
        if (screenStrength == 0 || strength(sim, screen) * 1f >= screenStrength * SCREEN_FLOOR) return false;
        beat(sim, "screen_broken");
        if (broken == Broken.HOLD || main.size() == 0) {
            phase(sim, Phase.HALTED);
            return true;
        }
        // The main takes the lead with the screen's remnants: from here it is one element and nothing screens it.
        screen.squads.addAll(main.squads);
        screen.hulls.addAll(main.hulls);
        screen.mechs.addAll(main.mechs);
        main.squads.clear();
        main.hulls.clear();
        main.mechs.clear();
        screenStrength = strength(sim, screen);
        squadOrders.clear();
        post(sim, "close up without a screen");
        return true;
    }

    /**
     * What the formation is engaged by, and where to face: the centre of every hostile within contact range of
     * any living member, screen or main, and of every hostile a squad or walker of it has seen in the last
     * second, however far off (a picket its guns are trading fire with at range). Null when there is none.
     */
    private float[] threat(BattleSimulation sim) {
        List<float[]> at = positions(sim, screen);
        at.addAll(positions(sim, main));
        if (at.isEmpty()) return null;
        float reach = contactRange * contactRange;
        float sx = 0f, sy = 0f;
        int n = 0;
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || !sim.world().isAlive(unit)) continue;
            if (!side.hostileTo(sim.identity().faction(unit))) continue;
            float ux = sim.world().x(unit), uy = sim.world().y(unit);
            for (float[] p : at) {
                float dx = p[0] - ux, dy = p[1] - uy;
                if (dx * dx + dy * dy <= reach) {
                    sx += ux;
                    sy += uy;
                    n++;
                    break;
                }
            }
        }
        int since = sim.getSimTickIndex() - seconds(SEEN_SECONDS);
        for (Squad squad : formationSquads(sim)) {
            for (BelievedContact seen : squad.believedContacts()) {
                if (seen.lastSeenTick() < since) continue;
                sx += seen.lastSeenCellX() + .5f;
                sy += seen.lastSeenCellY() + .5f;
                n++;
            }
        }
        return n == 0 ? null : new float[]{sx / n, sy / n};
    }

    /** The formation's squads: its infantry, and each walker's own squad. */
    private List<Squad> formationSquads(BattleSimulation sim) {
        List<Squad> out = new ArrayList<>();
        for (Element element : List.of(screen, main)) {
            for (int id : element.squads) {
                Squad squad = sim.getSquad(id);
                if (squad != null) out.add(squad);
            }
            for (long mech : element.mechs) {
                Squad squad = mechAlive(sim, mech) ? sim.squadOf(mech) : null;
                if (squad != null) out.add(squad);
            }
        }
        return out;
    }

    /** Whether enough of an element's squads and machines stand at their slots. */
    private boolean inPlace(BattleSimulation sim, Element element) {
        int live = 0, there = 0;
        Map<Integer, float[]> centres = squadCentres(sim, element);
        int role = element == screen ? SCREEN : element == main ? MAIN : SUPPORT;
        for (Slot slot : slots()) {
            if (slot.role != role) continue;
            float[] p = where(sim, slot.kind, slot.id, centres);
            // A straggler is left to catch up, here as in the body: it cannot keep the formation from rallying.
            if (p == null || !keepsUp(sim, slot.kind, slot.id)) continue;
            int[] at = slot.cell();
            live++;
            if (Math.hypot(p[0] - at[0] - .5f, p[1] - at[1] - .5f) <= ARRIVE_REACH) there++;
        }
        return live == 0 || there >= Math.ceil(live * ARRIVE_SHARE);
    }

    /** Distance along the route of an element's centre; the route's start when nothing of it lives. */
    public float centre(BattleSimulation sim, Element element) {
        List<float[]> at = positions(sim, element);
        if (at.isEmpty()) return 0f;
        float sum = 0f;
        for (float[] p : at) sum += route.along(p[0], p[1]);
        return sum / at.size();
    }

    /** Distance along the route of an element's most forward living member. */
    public float lead(BattleSimulation sim, Element element) {
        float best = Float.NEGATIVE_INFINITY;
        for (float[] p : positions(sim, element)) best = Math.max(best, route.along(p[0], p[1]));
        return best;
    }

    /** Every living member's position: each squad member, each hull, each mech. */
    public static List<float[]> positions(BattleSimulation sim, Element element) {
        List<float[]> out = new ArrayList<>();
        if (!element.squads.isEmpty()) {
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (!sim.world().hasPosition(unit) || !sim.world().isAlive(unit)) continue;
                Squad squad = sim.squadOf(unit);
                if (squad != null && element.squads.contains(squad.id)) out.add(new float[]{sim.world().x(unit), sim.world().y(unit)});
            }
        }
        for (long hull : element.hulls) {
            if (AlliedCommand.alive(sim.convoy(), hull)) out.add(new float[]{sim.convoy().body(hull).x, sim.convoy().body(hull).y});
        }
        for (long mech : element.mechs) {
            if (mechAlive(sim, mech)) out.add(new float[]{sim.world().x(mech), sim.world().y(mech)});
        }
        return out;
    }

    private static Map<Integer, float[]> squadCentres(BattleSimulation sim, Element element) {
        Map<Integer, float[]> sums = new HashMap<>();
        if (element.squads.isEmpty()) return sums;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || !sim.world().isAlive(unit)) continue;
            Squad squad = sim.squadOf(unit);
            if (squad == null || !element.squads.contains(squad.id)) continue;
            float[] s = sums.computeIfAbsent(squad.id, k -> new float[3]);
            s[0] += sim.world().x(unit);
            s[1] += sim.world().y(unit);
            s[2]++;
        }
        for (float[] s : sums.values()) {
            s[0] /= s[2];
            s[1] /= s[2];
        }
        return sums;
    }

    private static int strength(BattleSimulation sim, Element element) {
        return positions(sim, element).size();
    }

    private static void prune(BattleSimulation sim, Element element) {
        element.squads.removeIf(squad -> sim.squadMemberCount(squad) == 0);
        element.hulls.removeIf(hull -> !AlliedCommand.alive(sim.convoy(), hull));
        element.mechs.removeIf(mech -> !mechAlive(sim, mech));
    }

    private static boolean mechAlive(BattleSimulation sim, long mech) {
        return sim.resolveUnit(mech) != 0L && sim.world().isAlive(mech);
    }

    /** The slot, or the nearest walkable cell to it: a squad is never sent into a wall. */
    private static int[] walkable(BattleSimulation sim, int[] cell) {
        for (int r = 0; r <= 8; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cell[0] + dx, y = cell[1] + dy;
                    if (sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y)) return new int[]{x, y};
                }
        return cell;
    }

    private void phase(BattleSimulation sim, Phase next) {
        phase = next;
        phaseSince = sim.getSimTickIndex();
    }

    private void beat(BattleSimulation sim, String key) {
        log.add(sim.getSimTickIndex() + " " + key);
        beats.accept(name + "." + key);
    }

    private static int seconds(float s) {
        return Math.round(s / BattleSimulation.TICK_DT);
    }
}
