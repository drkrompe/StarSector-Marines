package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A mission's word to one party's commander about one place (`garrisonables.md`, place directives): what to do there
 * ({@link Kind}), where ({@link PlaceTarget}), when to begin ({@link #when}) and when to stop ({@link #until}). Declared
 * once at mission setup; a party's commander holds its own in a {@link PlaceDirectives} and evaluates them every pulse.
 *
 * @param id        unique within the mission; names the directive in traces and evidence
 * @param party     whose commander it speaks to
 * @param posts     a lock's own cells to stand on, cycled by detachment; empty for the other kinds
 * @param radius    a lock's defend-area radius around its post
 * @param allowance how many of the party's squads a hold may add to its share for its own places
 * @param reason    the reason a commander publishes for an order it gives under this directive
 * @param key       the mission's own number for the place (a site index), echoed back; -1 when it has none
 * @param rearms    whether a released directive is dormant again from the next pulse
 */
public record PlaceDirective(String id, Faction party, Kind kind, PlaceTarget target, List<Post> posts, int radius,
                             int allowance, String reason, int key, PlaceTrigger when, PlaceTrigger until,
                             boolean rearms) {

    /** What a party's commander does with a directive's place. */
    public enum Kind {
        /** Consider holding it: its places are open to the party's reactive manning within the allowance. */
        HOLD,
        /** A locked position: the party takes it and holds it, and nothing reactive gives it back. */
        LOCK,
        /** Retake it: read by the commander's counter-attack, which decides how and with what. */
        COUNTER_ATTACK
    }

    /** A cell a lock's squads stand on. */
    public record Post(int x, int y) {}

    public PlaceDirective {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(until, "until");
        posts = List.copyOf(posts);
        if (radius < 0) throw new IllegalArgumentException("Negative radius " + radius);
        if (allowance < 0) throw new IllegalArgumentException("Negative allowance " + allowance);
        if (reason == null || reason.isBlank()) reason = "PLACE_" + kind.name();
    }

    /** Consider holding {@code target}; by default always, never released, an allowance of one squad. */
    public static Builder hold(String id, Faction party, PlaceTarget target) {
        return new Builder(id, party, Kind.HOLD, target).allowance(1);
    }

    /** A locked position at {@code target}; by default locked from the start, never unlocked. */
    public static Builder lock(String id, Faction party, PlaceTarget target) {
        return new Builder(id, party, Kind.LOCK, target).radius(5);
    }

    /** Retake {@code target}; by default when it is lost, released once it is held again, re-arming. */
    public static Builder counterAttack(String id, Faction party, PlaceTarget target) {
        return new Builder(id, party, Kind.COUNTER_ATTACK, target).when(PlaceTrigger.lost())
                .until(PlaceTrigger.held(PlaceHold.OWN)).rearms(true);
    }

    /** Authoring for a {@link PlaceDirective}. */
    public static final class Builder {
        private final String id;
        private final Faction party;
        private final Kind kind;
        private final PlaceTarget target;
        private final List<Post> posts = new ArrayList<>();
        private int radius;
        private int allowance;
        private String reason;
        private int key = -1;
        private PlaceTrigger when = PlaceTrigger.always();
        private PlaceTrigger until = PlaceTrigger.never();
        private boolean rearms;

        private Builder(String id, Faction party, Kind kind, PlaceTarget target) {
            this.id = id;
            this.party = party;
            this.kind = kind;
            this.target = target;
        }

        public Builder when(PlaceTrigger trigger) { when = trigger; return this; }

        public Builder until(PlaceTrigger trigger) { until = trigger; return this; }

        public Builder post(int x, int y) { posts.add(new Post(x, y)); return this; }

        public Builder radius(int cells) { radius = cells; return this; }

        public Builder allowance(int squads) { allowance = squads; return this; }

        public Builder reason(String text) { reason = text; return this; }

        public Builder key(int value) { key = value; return this; }

        public Builder rearms(boolean value) { rearms = value; return this; }

        public PlaceDirective build() {
            return new PlaceDirective(id, party, kind, target, posts, radius, allowance, reason, key, when, until,
                    rearms);
        }
    }
}
