package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.objective.side.SideField.Who;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * How a side objective is done, judged once a tick while its window is open. The vocabulary is the Dust Ledger's
 * shapes ({@link Reach}, {@link Hold}, {@link Carry}, {@link Escort}); a mission may supply its own through the same
 * interface. A trigger keeps its own work (a clock, a carried prop) and is never shared between objectives.
 */
public interface SideTrigger {
    /** What the trigger saw this tick. */
    enum Reading { IDLE, WORKING, DONE, FAILED }

    Reading judge(SideField field, float dt);

    /** Where the thing the job is about stands now, for its beacon; null when there is none to mark. */
    Spot target(SideField field);

    /** Where it must go, for its beacon; null for a job done in place. */
    default Ground destination() { return null; }

    /** Whether the destination is marked before the work begins; an escort marks it once the unit is under escort. */
    default boolean destinationShownIdle() { return true; }

    /** Seconds of unbroken work so far, for a timed job; -1 when untimed. */
    default float worked() { return -1f; }

    /** Seconds of unbroken work the job needs; -1 when untimed. */
    default float required() { return -1f; }

    /** A beacon's cell and the outline it draws (empty for a point). */
    record Spot(int x, int y, List<Cell> outline) {
        public Spot { outline = List.copyOf(outline); }

        static Spot of(Ground ground) { return new Spot(ground.anchor().x(), ground.anchor().y(), ground.outline()); }
    }

    /**
     * A job that waits for the player to choose a verb. While a company unit of the right kind is within
     * {@code nearCells} of the job's place, the job is on offer: the HUD draws its verbs there, and pressing one
     * ({@link SideObjectiveService#choose}) engages the work, which is then judged by the inner trigger: the choice stands
     * while the company stays near, so it may press first and walk the squad in after. It lapses when the company
     * leaves, so a squad walking past does nothing.
     */
    final class Interact implements SideTrigger {
        /** How the offer reads on screen. */
        public enum Layout {
            /** A card: title, one line of what it does, then the verbs in a row. */
            CARD,
            /** One line per verb: a small button with what it does beside it. */
            LINES
        }

        private final Who who;
        private final Ground ground;
        private final float nearCells;
        private final SideTrigger work;
        private final List<String> verbs;
        private final Layout layout;
        private boolean engaged;

        public Interact(Who who, Ground ground, float nearCells, SideTrigger work, Layout layout, List<String> verbs) {
            if (verbs.isEmpty()) throw new IllegalArgumentException("an interaction needs at least one verb");
            if (!(nearCells > 0f)) throw new IllegalArgumentException("an interaction needs a positive reach");
            this.who = who; this.ground = ground; this.nearCells = nearCells; this.work = work;
            this.layout = layout; this.verbs = List.copyOf(verbs);
        }

        @Override public Reading judge(SideField field, float dt) {
            if (engaged && !near(field)) engaged = false;
            return engaged ? work.judge(field, dt) : Reading.IDLE;
        }

        /** Whether a company unit of the right kind is close enough to the job's place for it to be on offer. */
        public boolean near(SideField field) {
            return field.nearest(who, ground.anchor().x() + .5f, ground.anchor().y() + .5f, nearCells) != 0L;
        }

        /** Engages the work if {@code verb} is one of this job's and the company is near; true when it did. */
        boolean press(String verb, SideField field) {
            if (!verbs.contains(verb) || !near(field)) return false;
            engaged = true;
            return true;
        }

        public boolean engaged() { return engaged; }
        public List<String> verbs() { return verbs; }
        public Layout layout() { return layout; }
        public Ground ground() { return ground; }

        @Override public Spot target(SideField field) { return work.target(field); }
        @Override public Ground destination() { return work.destination(); }
        @Override public boolean destinationShownIdle() { return work.destinationShownIdle(); }
        @Override public float worked() { return work.worked(); }
        @Override public float required() { return work.required(); }
    }

    /** A company unit enters an area. */
    final class Reach implements SideTrigger {
        private final Who who;
        private final Ground ground;

        public Reach(Who who, Ground ground) { this.who = who; this.ground = ground; }

        @Override public Reading judge(SideField field, float dt) {
            return field.anyIn(who, ground) ? Reading.DONE : Reading.IDLE;
        }

        @Override public Spot target(SideField field) { return Spot.of(ground); }
    }

    /** A company unit stays in an area for a number of seconds; leaving restarts the clock. */
    final class Hold implements SideTrigger {
        private final Who who;
        private final Ground ground;
        private final float seconds;
        private float work;

        public Hold(Who who, Ground ground, float seconds) {
            if (!(seconds > 0f)) throw new IllegalArgumentException("a hold needs a positive duration");
            this.who = who; this.ground = ground; this.seconds = seconds;
        }

        @Override public Reading judge(SideField field, float dt) {
            work = field.anyIn(who, ground) ? work + dt : 0f;
            return work >= seconds ? Reading.DONE : work > 0f ? Reading.WORKING : Reading.IDLE;
        }

        @Override public Spot target(SideField field) { return Spot.of(ground); }
        @Override public float worked() { return work; }
        @Override public float required() { return seconds; }
    }

    /**
     * A company unit takes a prop from where it lies to an area. The prop is wherever its carrier stands, delivered the
     * moment that is inside the area, and dropped where the carrier last stood if it falls; the nearest company unit in
     * reach then picks it up again.
     */
    final class Carry implements SideTrigger {
        private final Who who;
        private final float reach;
        private final Ground destination;
        private int x, y;
        private long carrier;
        private boolean delivered;

        public Carry(Who who, Cell start, float reach, Ground destination) {
            this.who = who; this.reach = reach; this.destination = destination;
            x = start.x(); y = start.y();
        }

        @Override public Reading judge(SideField field, float dt) {
            if (delivered) return Reading.DONE;
            if (carrier != 0L) {
                if (!field.onField(carrier)) { carrier = 0L; return Reading.IDLE; }
                x = (int) Math.floor(field.x(carrier)); y = (int) Math.floor(field.y(carrier));
                if (destination.contains(x, y)) { delivered = true; carrier = 0L; return Reading.DONE; }
                return Reading.WORKING;
            }
            carrier = field.nearest(who, x + .5f, y + .5f, reach);
            return carrier != 0L ? Reading.WORKING : Reading.IDLE;
        }

        @Override public Spot target(SideField field) { return delivered ? null : new Spot(x, y, List.of()); }
        @Override public Ground destination() { return destination; }
        public long carrier() { return carrier; }
    }

    /**
     * A captive or ally reaches an area alive. It counts only while escorted (a captive held, an ally under the company's
     * wing); its death ruins the job.
     */
    final class Escort implements SideTrigger {
        private final LongSupplier unit;
        private final BooleanSupplier escorted;
        private final Ground destination;

        public Escort(LongSupplier unit, BooleanSupplier escorted, Ground destination) {
            this.unit = unit; this.escorted = escorted; this.destination = destination;
        }

        @Override public Reading judge(SideField field, float dt) {
            long id = unit.getAsLong();
            if (!field.onField(id)) return Reading.FAILED;
            if (!escorted.getAsBoolean()) return Reading.IDLE;
            return destination.contains((int) Math.floor(field.x(id)), (int) Math.floor(field.y(id)))
                    ? Reading.DONE : Reading.WORKING;
        }

        @Override public Spot target(SideField field) {
            long id = unit.getAsLong();
            return field.onField(id) ? new Spot((int) Math.floor(field.x(id)), (int) Math.floor(field.y(id)), List.of()) : null;
        }

        @Override public Ground destination() { return destination; }
        @Override public boolean destinationShownIdle() { return false; }
    }
}
