package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.campaign.CampaignMarks;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An optional, mission-owned job with no bearing on victory or defeat ({@code side-objective-toolkit.md}): who offers
 * it ({@link Hook}), how it is done ({@link SideTrigger}), when it is open ({@link Window}), what completion changes in
 * the battle ({@link Effect}) and what the campaign keeps ({@link Return}). It holds its live {@link State} too, moved on
 * by the battle's {@link SideObjectiveService}. It never enters the win check.
 */
public final class SideObjective {
    public enum State {
        /** Available, not started. */
        OFFERED,
        /** The trigger is in progress. */
        WORKING,
        /** Done: the effect has fired. */
        DONE,
        /** The window closed first; a job half done at that moment lapses too. Pays nothing. */
        LAPSED,
        /** The job was ruined, as when the escorted unit dies. */
        FAILED,
        /** A hidden job never found before its window closed: never shown, reported only by an authored missed line. */
        MISSED;

        public boolean isFinal() { return this == DONE || this == LAPSED || this == FAILED || this == MISSED; }

        /** The state the shared mission picture shows. */
        public MissionObjectiveSnapshot.State picture() {
            return this == DONE ? MissionObjectiveSnapshot.State.COMPLETE
                    : isFinal() ? MissionObjectiveSnapshot.State.FAILED : MissionObjectiveSnapshot.State.ACTIVE;
        }

        /** The state's part in a story key: {@code Offered}, {@code Working}, {@code Done}, {@code Lapsed}, {@code Failed}. */
        public String keyPart() { return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT); }
    }

    /**
     * Who offers the job and how it reads, all from story keys named {@code prefix + part}: {@code Name},
     * {@code Steps} (the briefing line), one phase per state ({@code Offered}, {@code Working}, {@code Done},
     * {@code Lapsed}, {@code Failed}, the last two falling back to the shared {@code sideObjectiveLapsed} and
     * {@code sideObjectiveFailed}), {@code Timer} for a timed job, {@code Target} and {@code Destination} for its beacons,
     * and {@code Debrief} plus the final state's part for the debrief line ({@code DebriefMissed} is optional: a hidden
     * job never found says nothing unless the mission authors it). {@code Reward}, if authored, is what the job earns
     * beyond credits, such as "Harrow will remember".
     *
     * @param offererKey story key naming the offerer, a contact or a faction
     * @param lines      the field line spoken when the job reaches a state: a narration catalog event, or absent
     */
    public record Hook(String prefix, String offererKey, Map<State, String> lines) {
        public Hook {
            if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("a hook needs a key prefix");
            lines = lines == null || lines.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(lines));
        }

        public String name() { return Strings.get(prefix + "Name"); }
        public String steps() { return Strings.get(prefix + "Steps"); }
        public String offerer() { return Strings.get(offererKey); }
        public String timer() { return Strings.get(prefix + "Timer"); }
        public String target() { return Strings.get(prefix + "Target"); }
        public String destination() { return Strings.get(prefix + "Destination"); }

        public String phase(State state) {
            String key = prefix + state.keyPart();
            String text = Strings.get(key);
            return !text.equals(key) || !state.isFinal() || state == State.DONE ? text : Strings.get("sideObjective" + state.keyPart());
        }

        public String debrief(State state) {
            String key = prefix + "Debrief" + state.keyPart(), text = Strings.get(key);
            return state == State.MISSED && text.equals(key) ? "" : text;
        }

        /** What the job earns beyond credits, or empty when the mission authors nothing. */
        public String reward() {
            String key = prefix + "Reward", text = Strings.get(key);
            return text.equals(key) ? "" : text;
        }
    }

    /**
     * How a hidden job is found: a company unit meets its offerer, reaches its place or sights its prop. Until then the
     * job is absent from the briefing, the checklist and the map, and its trigger is not judged.
     */
    @FunctionalInterface
    public interface Discovery {
        boolean found(SideField field);

        /** A company unit of this kind stands in the ground: the place reached, or the offerer met where they wait. */
        static Discovery reach(SideField.Who who, Ground ground) { return field -> field.anyIn(who, ground); }

        /** A company unit of this kind has a line of sight to the ground within {@code range} cells. */
        static Discovery sight(SideField.Who who, Ground ground, float range) { return field -> field.sees(who, ground, range); }

        /** Found by whichever comes first. */
        static Discovery anyOf(Discovery... ways) {
            List<Discovery> all = List.of(ways);
            return field -> {
                for (Discovery way : all) if (way.found(field)) return true;
                return false;
            };
        }
    }

    /** When a job is open. Once it closes, an unfinished job lapses. */
    public interface Window {
        /** @param elapsed seconds since the battle began */
        boolean open(float elapsed);

        /** Open for the whole battle. */
        static Window always() { return elapsed -> true; }

        /** Open while a phase of the mission lasts, such as the quiet before an alarm. */
        static Window phase(BooleanSupplier open) { return elapsed -> open.getAsBoolean(); }

        /** Open for the first {@code seconds} of the battle. */
        static Window clock(float seconds) { return elapsed -> elapsed < seconds; }

        /** Open while another job's state passes {@code test}. */
        static Window whileState(SideObjective other, Predicate<State> test) { return elapsed -> test.test(other.state()); }
    }

    /** What completion changes in the battle, through the existing seams only. Fired once, when the job is done. */
    @FunctionalInterface
    public interface Effect {
        Effect NONE = sim -> { };

        void apply(BattleSimulation sim);
    }

    /**
     * What the campaign keeps: a mark per final state that earns one, such as {@code corvus.harrow.coupling_delivered},
     * and the credits the job pays when done, both written at settlement from the outcome ({@link CampaignMarks}). A
     * job may pay either, both or neither; its effect in the battle is its own reward.
     */
    public record Return(Map<State, String> marks, int credits) {
        public static final Return NONE = new Return(Map.of(), 0);

        public Return {
            if (credits < 0) throw new IllegalArgumentException("a side objective pays no negative credits");
            marks = marks == null || marks.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(marks));
            for (var mark : marks.entrySet()) {
                if (!mark.getKey().isFinal()) throw new IllegalArgumentException("only a final state earns a mark");
                if (!CampaignMarks.valid(mark.getValue()))
                    throw new IllegalArgumentException("a mark is namespaced lower-case words: " + mark.getValue());
            }
        }

        /** A mark for the job done. */
        public static Return done(String mark) { return new Return(Map.of(State.DONE, mark), 0); }

        /** A mark and credits for the job done. */
        public static Return done(String mark, int credits) { return new Return(Map.of(State.DONE, mark), credits); }

        public String mark(State state) { return marks.getOrDefault(state, ""); }

        /** The credits a job in this state has earned: its pay when done, none otherwise. */
        public int credits(State state) { return state == State.DONE ? credits : 0; }
    }

    private final String key;
    private final Hook hook;
    private final SideTrigger trigger;
    private final Window window;
    private final Effect effect;
    private final Return ret;
    private final Supplier<List<Marker>> context;
    private State state = State.OFFERED;
    private Discovery discovery;
    private String foundLine;
    private boolean found;
    private float foundAt = -1f, lastElapsed;

    /**
     * @param context extra beacons the mission shows on this job's row, such as what its payoff will change; may be null
     */
    public SideObjective(String key, Hook hook, SideTrigger trigger, Window window, Effect effect, Return ret,
                         Supplier<List<Marker>> context) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("a side objective needs a key");
        if (hook == null || trigger == null || window == null) throw new IllegalArgumentException("side objective " + key + " needs a hook, a trigger and a window");
        this.key = key; this.hook = hook; this.trigger = trigger; this.window = window;
        this.effect = effect == null ? Effect.NONE : effect;
        this.ret = ret == null ? Return.NONE : ret;
        this.context = context == null ? List::of : context;
    }

    public String key() { return key; }
    public Hook hook() { return hook; }
    public SideTrigger trigger() { return trigger; }
    public Return ret() { return ret; }
    public State state() { return state; }
    public boolean done() { return state == State.DONE; }
    public List<Marker> context() { return context.get(); }

    /**
     * Hides the job until {@code discovery} finds it, at setup only. {@code line} is the field line spoken when it is
     * found (a narration catalog event), or null.
     */
    public SideObjective hidden(Discovery discovery, String line) {
        if (discovery == null) throw new IllegalArgumentException("a hidden job needs a way to be found");
        if (lastElapsed > 0f) throw new IllegalStateException("hide side objective " + key + " at setup");
        this.discovery = discovery;
        this.foundLine = line;
        return this;
    }

    /** Whether the job is on the checklist and map: offered openly, or hidden and since found. */
    public boolean known() { return discovery == null || found; }

    /** Whether the job is hidden and was found within the last {@link #NEW_SECONDS}: its row reads as new. */
    public boolean fresh() { return found && lastElapsed - foundAt < NEW_SECONDS; }

    /** How long a found job's row is marked new. */
    public static final float NEW_SECONDS = 12f;

    String foundLine() { return foundLine; }

    /**
     * One tick: a closed window lapses the job; otherwise the trigger is judged, and completion fires the effect once.
     *
     * @return whether the state changed
     */
    boolean step(SideField field, float dt, float elapsed, BattleSimulation sim) {
        lastElapsed = elapsed;
        if (state.isFinal()) return false;
        if (!window.open(elapsed)) return set(known() ? State.LAPSED : State.MISSED);
        if (!known()) {
            if (!discovery.found(field)) return false;
            found = true;
            foundAt = elapsed;
        }
        return switch (trigger.judge(field, dt)) {
            case DONE -> { set(State.DONE); effect.apply(sim); yield true; }
            case FAILED -> set(State.FAILED);
            case WORKING -> set(State.WORKING);
            case IDLE -> set(State.OFFERED);
        };
    }

    /** Completes the job outright, as if it had been done, while its window is open; the effect fires. */
    void grant(float elapsed, BattleSimulation sim) {
        if (state.isFinal() || !window.open(elapsed)) throw new IllegalStateException("side objective " + key + " is closed");
        if (!known()) { found = true; foundAt = -NEW_SECONDS; }
        state = State.DONE;
        effect.apply(sim);
    }

    /** The battle is over: an unfinished job lapses, and a hidden one never found is missed. */
    boolean end() { return !state.isFinal() && set(known() ? State.LAPSED : State.MISSED); }

    private boolean set(State next) {
        if (state == next) return false;
        state = next;
        return true;
    }
}
