package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The company's boats and the berths they are standing in.
 *
 * <p><b>The boats are the company's; the berths are the ship's.</b> That split
 * is the whole model here. A hull's bays hold a number of a particular pattern
 * and neither of those is negotiable, so the berth count is never a field of
 * this class — it is asked of the hull ({@code ShipsBoats.aboard(ship).size()})
 * every time the deck is read. What persists is the boats: their ids, their
 * names, and what has been fitted to them, which is the part a player has paid
 * for and would be furious to lose to a transfer.
 *
 * <p>{@link #reconcile} is where the two meet, and it runs whenever the deck is
 * looked at against the ship the company is actually on — a screen attaching, a
 * briefing asking what the lift is. A deck that has drifted out of step with
 * the hull is never presented and never flown, which is why this is a read-time
 * reconcile rather than an event somebody has to remember to fire when the
 * player moves ship.
 *
 * <p>It is idempotent by construction rather than by a short-circuit: a second
 * call with the same hull keeps every boat, leaves none behind, and mints
 * nothing, because that is simply what the rule does when the deck already
 * matches.
 */
public final class BoatDeck implements Serializable {

    /** The ship whose berths these boats are currently standing in. */
    private String shipId;

    /** One entry per berth, in berth order. A null is a berth with nothing in it. */
    private List<CampaignBoat> berths = new ArrayList<>();

    /**
     * Monotonic and never reset, so a tail number is never reused. A company
     * that moves to a gig hull and back gets {@code Aeroshuttle 07}, not a
     * second {@code Aeroshuttle 01} that reads like the boat they lost.
     */
    private int nextTailNumber = 1;

    /**
     * What the last <em>move</em> cost, kept so the room can still say it after
     * the reconcile that discovered it has been run again. Reconciling against
     * the same hull is a no-op and deliberately does not clear this.
     */
    private LeftBehind leftBehind = LeftBehind.NONE;

    /**
     * Brings the deck into step with one hull, and reports what that cost.
     *
     * <p>The company's boats go into her berths in berth order up to her count;
     * boats beyond it, and boats of a pattern her bays do not hold, stay with
     * the hull they were aboard. Berths still vacant are filled with her own
     * boats at standard fit, because the hull comes with her boats and a berth
     * standing empty would be lift the player has to buy before they can leave.
     *
     * @param shipId the hull the company is aboard, by fleet-member id
     * @param pattern what her bays hold ({@code ShipsBoats.carriedBy})
     * @param berthCount how many she has ({@code ShipsBoats.aboard(ship).size()})
     * @return the boats this call left behind; empty when nothing changed
     */
    public LeftBehind reconcile(String shipId, ShuttleType pattern, int berthCount) {
        if (pattern == null) return LeftBehind.NONE;
        int wanted = Math.max(0, berthCount);
        boolean moved = !Objects.equals(this.shipId, shipId);

        List<CampaignBoat> kept = new ArrayList<>(wanted);
        List<String> left = new ArrayList<>();
        for (CampaignBoat boat : berths) {
            if (boat == null) continue;
            if (boat.pattern() == pattern && kept.size() < wanted) kept.add(boat);
            else left.add(boat.displayName());
        }
        while (kept.size() < wanted) kept.add(build(pattern));

        this.berths = kept;
        this.shipId = shipId;
        LeftBehind cost = left.isEmpty() ? LeftBehind.NONE : new LeftBehind(left);
        if (moved) leftBehind = cost;
        return cost;
    }

    /** Every berth in order; an entry is null for a berth with nothing in it. */
    public List<CampaignBoat> boats() {
        return Collections.unmodifiableList(berths);
    }

    /** The boats that can actually be put in the air, in berth order. */
    public List<CampaignBoat> airworthy() {
        List<CampaignBoat> flying = new ArrayList<>(berths.size());
        for (CampaignBoat boat : berths) {
            if (boat != null) flying.add(boat);
        }
        return Collections.unmodifiableList(flying);
    }

    public CampaignBoat boatById(String id) {
        if (id == null) return null;
        for (CampaignBoat boat : berths) {
            if (boat != null && id.equals(boat.id())) return boat;
        }
        return null;
    }

    /** How many berths the deck is currently laid out for. */
    public int berths() {
        return berths.size();
    }

    /** The ship these boats are aboard, or null before the first reconcile. */
    public String shipId() {
        return shipId;
    }

    /** What the company's last change of ship cost them. Never null. */
    public LeftBehind leftBehind() {
        return leftBehind;
    }

    /**
     * Boats a move stranded with the old hull.
     *
     * <p>Names rather than ids, because the only thing that can be done with
     * this is say it out loud — the boats themselves are gone with the ship.
     */
    public static final class LeftBehind implements Serializable {

        public static final LeftBehind NONE = new LeftBehind(List.of());

        // A plain class rather than a record because this rides in the save
        // graph, and nothing else xstream walks here is one.
        private final List<String> names;

        public LeftBehind(List<String> names) {
            this.names = List.copyOf(names);
        }

        public List<String> names() { return names; }

        public int count() { return names.size(); }

        public boolean any() { return !names.isEmpty(); }
    }

    private CampaignBoat build(ShuttleType pattern) {
        int tail = nextTailNumber++;
        String number = String.format(Locale.ROOT, "%02d", tail);
        return new CampaignBoat("boat_" + number, tailName(pattern) + " " + number,
                pattern, BoatFitting.STANDARD_PLATING.id(), BoatFitting.STANDARD_DRIVE.id());
    }

    /** {@code AEROSHUTTLE} reads as {@code Aeroshuttle}, which is what a tail says. */
    private static String tailName(ShuttleType pattern) {
        String name = pattern.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private Object readResolve() {
        if (berths == null) berths = new ArrayList<>();
        if (leftBehind == null) leftBehind = LeftBehind.NONE;
        nextTailNumber = Math.max(nextTailNumber, berths.size() + 1);
        return this;
    }
}
