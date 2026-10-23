package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A screened advance ({@code allied-command.md}, Screened advance): two elements going to a rally
 * point a bound at a time, the <b>screen</b> in front and the <b>main</b> behind it. Either can be
 * infantry, machines or both: militia screening tanks through woods, or APCs screening marines across
 * open ground.
 *
 * <p>Each bound goes the same way. The screen steps off to the bound's line once its gate is open
 * while the main holds where the last bound left it; the screen arrives; the main closes up to a
 * standoff behind it; both halt to regroup; the bound is cleared. The main is never sent past a point
 * {@code standoff} cells behind the screen's centre, however much faster it is. When the screen reports
 * contact it halts where it stands, the main closes up behind it to give it fire, and after the contact
 * has been quiet for a while (or has held it long enough) the screen goes on to the same bound. A screen
 * worn below its floor either halts the whole advance or hands the lead to the main.
 *
 * <p>Mission-neutral and owned by the mission, which advances it from its own serial tick after its
 * {@link AlliedCommand}: an authored battle declares its elements, route, bounds, gates and trigger;
 * generic command can take the defaults. Hulls and mechs are driven through the allied command's
 * station orders and squads by attack-move under {@code GARRISON} authority, so every element fights on
 * its own doctrine wherever it is put.
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

    /** What happens when the screen is worn below its floor. */
    public enum Broken {
        /** Both elements halt where they stand and the advance ends there. */
        HOLD,
        /** What is left of the screen joins the main, which goes on as an unscreened advance. */
        MAIN_LEADS
    }

    public enum Phase { WAITING, GATED, SCREEN_BOUNDING, CONTACT, MAIN_CLOSING, REGROUP, RALLIED, HALTED }

    /** One side of the advance: its squads, hulls and mechs. Members may join after the advance has begun. */
    public static final class Element {
        final List<Integer> squads = new ArrayList<>();
        final List<Long> hulls = new ArrayList<>();
        final List<Long> mechs = new ArrayList<>();

        public Element squads(List<Integer> ids) { squads.addAll(ids); return this; }
        public Element squad(int id) { squads.add(id); return this; }
        public Element hulls(List<Long> ids) { hulls.addAll(ids); return this; }
        public Element hull(long id) { hulls.add(id); return this; }
        public Element mechs(List<Long> ids) { mechs.addAll(ids); return this; }
        public Element mech(long id) { mechs.add(id); return this; }

        int size() { return squads.size() + hulls.size() + mechs.size(); }
    }

    /** Default cells a bound is long, for an advance that names none. */
    public static final float DEFAULT_BOUND = 24f;
    /** Default cells the main stands behind the screen's centre. */
    public static final float DEFAULT_STANDOFF = 10f;
    /** Default cells between neighbours across the advance. */
    public static final float DEFAULT_SPACING = 6f;
    /** Default cells from a screen member within which a hostile is contact. */
    public static final float DEFAULT_CONTACT_RANGE = 16f;
    /** Cells from its slot a squad's centre, a hull or a mech counts as arrived. */
    public static final float ARRIVE_REACH = 6f;
    /** Share of an element's living squads and machines that must have arrived for the element to have. */
    public static final float ARRIVE_SHARE = 2f / 3f;
    /** Seconds an element is given to reach a bound before it is taken to have: a machine stuck on a fence does not hold everyone. */
    public static final float BOUND_PATIENCE_SECONDS = 90f;
    /** Seconds both elements halt at a cleared bound before the screen steps off again. */
    public static final float REGROUP_SECONDS = 4f;
    /** Seconds without contact before a halted screen goes on. */
    public static final float CONTACT_CLEAR_SECONDS = 6f;
    /** Seconds a contact may hold the screen before it goes on anyway, the main still behind it. */
    public static final float CONTACT_HOLD_SECONDS = 20f;
    /** Share of the screen's strength at step-off below which it is broken. */
    public static final float SCREEN_FLOOR = 1f / 3f;
    /** Cells a squad's slot must move before it is ordered again. */
    static final int RESTATION = 3;
    /** Ticks between the advance's decisions. */
    static final int DECIDE_TICKS = Math.round(1f / BattleSimulation.TICK_DT);

    private final String name, issuer;
    private final AlliedCommand allied;
    private final AdvanceRoute route;
    private final float[] bounds;
    private final Trigger[] gates;
    private final Trigger start;
    private final Faction side;
    private final float standoff, spacing, contactRange;
    private final Broken broken;
    private final Element screen, main;
    private final Consumer<String> beats;

    private Phase phase = Phase.WAITING;
    private int bound;
    private int phaseSince;
    private int quietSince;
    private int contacts;
    private int screenStrength;
    /** Distance along the route the main is posted at now; it never goes back. */
    private float mainPost = Float.NEGATIVE_INFINITY;
    /** Distance along the route the screen is posted at now. */
    private float screenPost;
    /** Where the screen halted on contact, along the route. */
    private float haltedAt;
    private final Map<Integer, int[]> squadOrders = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    private ScreenedAdvance(Builder b) {
        name = b.name;
        issuer = b.issuer;
        allied = b.allied;
        route = b.route;
        bounds = b.bounds != null ? b.bounds : route.bounds(b.boundEvery);
        gates = new Trigger[bounds.length];
        for (int i = 0; i < gates.length; i++) gates[i] = b.gates.getOrDefault(i, Trigger.now());
        start = b.start;
        side = b.side;
        standoff = b.standoff;
        spacing = b.spacing;
        contactRange = b.contactRange;
        broken = b.broken;
        screen = b.screen;
        main = b.main;
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
        float[] bounds;
        float boundEvery = DEFAULT_BOUND;
        final Map<Integer, Trigger> gates = new HashMap<>();
        Trigger start = Trigger.now();
        Faction side = Faction.ALLY;
        float standoff = DEFAULT_STANDOFF, spacing = DEFAULT_SPACING, contactRange = DEFAULT_CONTACT_RANGE;
        Broken broken = Broken.HOLD;
        Element screen = new Element(), main = new Element();
        Consumer<String> beats = beat -> { };

        Builder(String name, String issuer, AlliedCommand allied, AdvanceRoute route) {
            this.name = name;
            this.issuer = issuer;
            this.allied = allied;
            this.route = route;
        }

        public Builder screen(Element element) { screen = element; return this; }
        public Builder main(Element element) { main = element; return this; }
        /** Bounds every {@code cells} along the route. */
        public Builder boundEvery(float cells) { boundEvery = cells; return this; }
        /** Bounds at these distances along the route, increasing; the last is taken as the rally point. */
        public Builder boundsAt(float... distances) {
            for (int i = 1; i < distances.length; i++)
                if (distances[i] <= distances[i - 1]) throw new IllegalArgumentException("bounds must increase");
            bounds = distances.clone();
            bounds[bounds.length - 1] = route.length();
            return this;
        }
        /** The screen does not step off for bound {@code index} (from 0) until {@code gate} is ready. */
        public Builder gate(int index, Trigger gate) { gates.put(index, gate); return this; }
        public Builder startWhen(Trigger trigger) { start = trigger; return this; }
        /** The side the elements fight for; contact is what is hostile to it. */
        public Builder side(Faction faction) { side = faction; return this; }
        public Builder standoff(float cells) { standoff = cells; return this; }
        public Builder spacing(float cells) { spacing = cells; return this; }
        public Builder contactRange(float cells) { contactRange = cells; return this; }
        public Builder onScreenBroken(Broken policy) { broken = policy; return this; }
        /** Where the advance's beats go: {@code <name>.step_off}, {@code .bound_N}, {@code .contact_N}, {@code .rallied}, {@code .screen_broken}. */
        public Builder beats(Consumer<String> sink) { beats = sink; return this; }

        public ScreenedAdvance build() { return new ScreenedAdvance(this); }
    }

    public Phase phase() { return phase; }
    /** The bound the advance is on, from 0; equal to the bound count once rallied. */
    public int bound() { return bound; }
    public int boundCount() { return bounds.length; }
    /** Distance along the route of bound {@code index}, from 0. */
    public float boundAlong(int index) { return bounds[index]; }
    public AdvanceRoute route() { return route; }
    public Element screen() { return screen; }
    public Element main() { return main; }
    /** What the advance has done and when, one line each, for a report. */
    public List<String> log() { return log; }

    /** A wave arriving late joins the screen where it stands; the next decision posts it with the rest. */
    public void joinScreen(int squad) { screen.squads.add(squad); }
    public void joinMainHull(long hull) { main.hulls.add(hull); }
    public void joinMainMech(long mech) { main.mechs.add(mech); }

    /** One tick; decisions are taken once a second. */
    public void advance(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % DECIDE_TICKS != 0 || phase == Phase.RALLIED || phase == Phase.HALTED) return;
        prune(sim, screen);
        prune(sim, main);
        switch (phase) {
            case WAITING -> {
                if (!start.ready(sim)) return;
                screenStrength = strength(sim, screen);
                beat(sim, "step_off");
                enterBound(sim, 0);
            }
            case GATED -> {
                if (gates[bound].ready(sim)) sendScreen(sim, bounds[bound], Phase.SCREEN_BOUNDING);
            }
            case SCREEN_BOUNDING -> {
                if (brokenScreen(sim)) return;
                if (contact(sim)) {
                    contacts++;
                    haltedAt = centre(sim, screen);
                    beat(sim, "contact_" + contacts);
                    sendScreen(sim, Math.min(haltedAt, bounds[bound]), Phase.CONTACT);
                    quietSince = tick;
                    return;
                }
                if (arrived(sim, screen, screenPost) || waited(sim)) {
                    phase(sim, Phase.MAIN_CLOSING);
                }
            }
            case CONTACT -> {
                if (brokenScreen(sim)) return;
                if (contact(sim)) quietSince = tick;
                boolean quiet = tick - quietSince >= seconds(CONTACT_CLEAR_SECONDS);
                boolean heldLong = tick - phaseSince >= seconds(CONTACT_HOLD_SECONDS);
                if (quiet || heldLong) sendScreen(sim, bounds[bound], Phase.SCREEN_BOUNDING);
            }
            case MAIN_CLOSING -> {
                if (brokenScreen(sim)) return;
                if (arrived(sim, main, mainPost) || waited(sim)) phase(sim, Phase.REGROUP);
            }
            case REGROUP -> {
                if (tick - phaseSince < seconds(REGROUP_SECONDS)) break;
                beat(sim, "bound_" + (bound + 1));
                if (bound == bounds.length - 1) {
                    bound = bounds.length;
                    phase(sim, Phase.RALLIED);
                    beat(sim, "rallied");
                    return;
                }
                enterBound(sim, bound + 1);
            }
            default -> { }
        }
        postMain(sim);
    }

    private void enterBound(BattleSimulation sim, int index) {
        bound = index;
        if (gates[index].ready(sim)) sendScreen(sim, bounds[index], Phase.SCREEN_BOUNDING);
        else phase(sim, Phase.GATED);
    }

    private void sendScreen(BattleSimulation sim, float along, Phase next) {
        screenPost = along;
        post(sim, screen, along, "screen to bound " + (bound + 1));
        phase(sim, next);
    }

    /**
     * The main's post: the last bound the screen cleared while the screen is bounding, closed up behind the
     * screen once it has arrived or halted, and never nearer the screen's centre than the standoff.
     */
    private void postMain(BattleSimulation sim) {
        if (main.size() == 0) return;
        float planned = switch (phase) {
            case GATED, SCREEN_BOUNDING -> (bound == 0 ? Float.NEGATIVE_INFINITY : bounds[bound - 1] - standoff);
            case CONTACT -> haltedAt - standoff;
            default -> bounds[Math.min(bound, bounds.length - 1)] - standoff;
        };
        float post = Math.min(planned, centre(sim, screen) - standoff);
        if (post <= mainPost) return;
        mainPost = post;
        post(sim, main, Math.max(0f, post), "close up behind the screen");
    }

    /** Puts every squad and machine of an element on its slot in a line abreast {@code along} the route. */
    private void post(BattleSimulation sim, Element element, float along, String reason) {
        int count = element.size(), i = 0;
        for (int squad : element.squads) {
            int[] at = walkable(sim, route.slot(along, i++, count, spacing));
            int[] held = squadOrders.get(squad);
            if (held != null && Math.abs(held[0] - at[0]) < RESTATION && Math.abs(held[1] - at[1]) < RESTATION) continue;
            squadOrders.put(squad, at);
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, at[0], at[1]),
                    CommandAuthority.GARRISON, issuer, name + ": " + reason);
        }
        for (long hull : element.hulls) {
            int[] at = route.slot(along, i++, count, spacing);
            allied.station(sim, hull, at[0], at[1]);
        }
        for (long mech : element.mechs) {
            int[] at = route.slot(along, i++, count, spacing);
            allied.stationMech(sim, mech, at[0], at[1]);
        }
    }

    private boolean brokenScreen(BattleSimulation sim) {
        if (screenStrength == 0 || strength(sim, screen) * 1f >= screenStrength * SCREEN_FLOOR) return false;
        beat(sim, "screen_broken");
        if (broken == Broken.HOLD || main.size() == 0) {
            phase(sim, Phase.HALTED);
            return true;
        }
        // The main takes the lead with the screen's remnants: from here it is one element and nothing waits behind it.
        screen.squads.addAll(main.squads);
        screen.hulls.addAll(main.hulls);
        screen.mechs.addAll(main.mechs);
        main.squads.clear();
        main.hulls.clear();
        main.mechs.clear();
        screenStrength = strength(sim, screen);
        sendScreen(sim, bounds[bound], Phase.SCREEN_BOUNDING);
        return true;
    }

    /** Whether a hostile stands within contact range of any living screen member. */
    private boolean contact(BattleSimulation sim) {
        List<float[]> at = positions(sim, screen);
        if (at.isEmpty()) return false;
        float reach = contactRange * contactRange;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || !sim.world().isAlive(unit)) continue;
            if (!side.hostileTo(sim.identity().faction(unit))) continue;
            float ux = sim.world().x(unit), uy = sim.world().y(unit);
            for (float[] p : at) {
                float dx = p[0] - ux, dy = p[1] - uy;
                if (dx * dx + dy * dy <= reach) return true;
            }
        }
        return false;
    }

    /** Whether enough of an element's squads and machines stand at their slots {@code along} the route. */
    private boolean arrived(BattleSimulation sim, Element element, float along) {
        int count = element.size(), i = 0, live = 0, there = 0;
        if (count == 0) return true;
        Map<Integer, float[]> centres = squadCentres(sim, element);
        for (int squad : element.squads) {
            int[] slot = walkable(sim, route.slot(along, i++, count, spacing));
            float[] c = centres.get(squad);
            if (c == null) continue;
            live++;
            if (Math.hypot(c[0] - slot[0] - .5f, c[1] - slot[1] - .5f) <= ARRIVE_REACH) there++;
        }
        for (long hull : element.hulls) {
            int[] slot = route.slot(along, i++, count, spacing);
            if (!AlliedCommand.alive(sim.convoy(), hull)) continue;
            live++;
            if (sim.convoy().body(hull).distanceTo(slot[0] + .5f, slot[1] + .5f) <= ARRIVE_REACH) there++;
        }
        for (long mech : element.mechs) {
            int[] slot = route.slot(along, i++, count, spacing);
            if (!mechAlive(sim, mech)) continue;
            live++;
            if (Math.hypot(sim.world().x(mech) - slot[0] - .5f, sim.world().y(mech) - slot[1] - .5f) <= ARRIVE_REACH) there++;
        }
        return live == 0 || there >= Math.ceil(live * ARRIVE_SHARE);
    }

    private boolean waited(BattleSimulation sim) {
        return sim.getSimTickIndex() - phaseSince >= seconds(BOUND_PATIENCE_SECONDS);
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
