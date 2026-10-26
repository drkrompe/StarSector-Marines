package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.turret.TurretRole;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * The company's boats and the berths they are standing in.
 *
 * <p><b>The boats are the company's; the berths are the ship's.</b> That split
 * is the whole model here. The boats move with the company to any ship and are
 * never left behind. The berth count is not an independent authority of
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

    /**
     * Boat parts in stores, by {@link BoatFitting} or {@link BoatGun} id: built
     * by the workshop, or taken off a boat. The company's, not a boat's, so a
     * spare outlives the boat it came off and moves with the company between
     * ships. A yard-standard part is never stored; every socket has its own
     * for nothing.
     */
    private Map<String, Integer> spares = new HashMap<>();

    /** The ship whose berths these boats are currently standing in. */
    private String shipId;
    private Set<String> recoverySettlements = new HashSet<>();

    /** One battle receipt, including losses, even when the same Results are reopened later. */
    public String settleRecovery(String receipt, Map<String, Float> damage,
                                 Collection<String> lost, float day) {
        if (receipt == null || !Float.isFinite(day)) return null;
        if (recoverySettlements == null) recoverySettlements = new HashSet<>();
        if (!recoverySettlements.add(receipt)) return null;
        lose(lost);
        String first = null;
        for (CampaignBoat boat : berths) {
            if (boat == null) continue;
            Float fraction = damage.get(boat.id());
            if (fraction == null || !Float.isFinite(fraction) || fraction <= 0f) continue;
            boat.damage(fraction, day);
            if (first == null) first = boat.id();
        }
        return first;
    }

    private boolean commissioningRequired;
    private String foundingWreckShipId;
    private int recoveredWrecks;

    /** Registers the original wrecks once. Existing occupied or previously opened berths
     * are treated as used when migrating a save; a lost boat must never respawn. */
    public void registerFoundingWrecks(String originalShipId) {
        if (originalShipId == null || foundingWreckShipId != null) return;
        foundingWreckShipId = originalShipId;
        if (shipId == null && !commissioningRequired) recoveredWrecks = 3;
        if (shipId != null) recoveredWrecks = originalShipId.equals(shipId)
                ? (1 << Math.min(2, berths.size())) - 1 : 3;
    }

    public boolean wreckInBerth(int berth) {
        return berth >= 0 && berth < Math.min(2, berths.size())
                && foundingWreckShipId != null && foundingWreckShipId.equals(shipId)
                && pattern == ShuttleType.HERMES && berths.get(berth) == null
                && (recoveredWrecks & (1 << berth)) == 0;
    }

    /** Only new companies opt out of the one-time legacy starting complement. */
    void requireCommissioning() { commissioningRequired = true; }

    /**
     * What her bays hold, from the last reconcile. Persisted because
     * fabrication has to know what to build into an empty berth and the hull
     * that decides it is not in the room's hand — a berth is filled from the
     * ship the company is on, not from whatever was standing there before.
     * Null on a deck that has never been reconciled.
     */
    private ShuttleType pattern;

    /** One entry per berth, in berth order. A null is a berth with nothing in it. */
    private List<CampaignBoat> berths = new ArrayList<>();

    /**
     * Monotonic and never reset, so a tail number is never reused. A company
     * that moves to a gig hull and back gets {@code Aeroshuttle 07}, not a
     * second {@code Aeroshuttle 01} that reads like the boat they lost.
     */
    private int nextTailNumber = 1;

    /**
     * Brings the deck into step with one hull.
     *
     * <p>The boats belong to the company and travel with it: a change of ship
     * never leaves one behind and never changes what the deck builds. The hull
     * only says how many berths there are. Staying aboard preserves berth
     * positions and vacancies; moving compacts the boats into the new hull's
     * berths. A hull with fewer berths than boats still keeps every boat, the
     * surplus ones stowed in berths past the hull's own: they fly like any other
     * and the bay does not draw them. Neither movement nor a read pays for a
     * replacement.
     *
     * <p>New companies commission every boat. Later vacancies always require
     * fabrication.
     *
     * @param shipId the hull the company is aboard, by fleet-member id
     * @param pattern what her bays hold ({@code ShipsBoats.carriedBy}); the
     *     deck takes it as the pattern it builds only the first time
     * @param berthCount how many she has ({@code ShipsBoats.aboard(ship).size()})
     */
    public void reconcile(String shipId, ShuttleType pattern, int berthCount) {
        if (pattern == null) return;
        int wanted = Math.max(0, berthCount);
        boolean sameShip = shipId != null && shipId.equals(this.shipId);

        List<CampaignBoat> kept = new ArrayList<>(wanted);
        if (sameShip) {
            kept.addAll(berths);
            while (kept.size() > wanted && kept.get(kept.size() - 1) == null) kept.remove(kept.size() - 1);
        } else {
            for (CampaignBoat boat : berths) {
                if (boat != null) kept.add(boat);
            }
            boolean legacyStart = this.shipId == null && !commissioningRequired;
            if (legacyStart && kept.isEmpty()) {
                for (int i = 0; i < wanted; i++) kept.add(build(pattern));
            }
        }
        while (kept.size() < wanted) kept.add(null);

        this.berths = kept;
        this.shipId = shipId;
        if (this.pattern == null) this.pattern = pattern;
    }

    /**
     * Strikes the named boats off their berths, which stand empty afterwards.
     *
     * <p>One way in for the only thing that can remove a boat the company still
     * owns: a shoot-down. An id nobody is standing under is ignored rather than
     * refused — a resolution naming a boat the player has already moved away
     * from should record nothing, not throw.
     *
     * @return how many berths this actually emptied
     */
    public int lose(Collection<String> boatIds) {
        if (boatIds == null || boatIds.isEmpty()) return 0;
        int struck = 0;
        for (int berth = 0; berth < berths.size(); berth++) {
            CampaignBoat boat = berths.get(berth);
            if (boat != null && boatIds.contains(boat.id())) {
                berths.set(berth, null);
                struck++;
            }
        }
        return struck;
    }

    /** The berths with nothing standing in them, in berth order. */
    public List<Integer> vacantBerths() {
        List<Integer> vacant = new ArrayList<>();
        for (int berth = 0; berth < berths.size(); berth++) {
            if (berths.get(berth) == null) vacant.add(berth);
        }
        return Collections.unmodifiableList(vacant);
    }

    /**
     * What this deck's berths hold, from the last reconcile, or null before
     * there has been one. What a vacant berth would be built back up as.
     */
    public ShuttleType pattern() {
        return pattern;
    }

    /**
     * Stands a new boat of the deck's own pattern in one empty berth.
     *
     * <p>Package-private for the reason {@link CampaignBoat#install} is: the
     * atomic authority is {@link BoatWorkshop}, and a caller that reached past
     * it would be a boat nobody paid for. Refuses a berth that does not exist,
     * one that is already held, and a deck that does not yet know what it
     * carries.
     *
     * @return the boat now standing there, or null when nothing was built
     */
    CampaignBoat fabricate(int berthIndex) {
        if (pattern == null || berthIndex < 0 || berthIndex >= berths.size()) return null;
        if (berths.get(berthIndex) != null) return null;
        CampaignBoat boat = build(pattern);
        if (wreckInBerth(berthIndex)) recoveredWrecks |= 1 << berthIndex;
        berths.set(berthIndex, boat);
        return boat;
    }

    /** How many of one part are in stores. */
    public int spares(String partId) {
        if (partId == null || spares == null) return 0;
        return spares.getOrDefault(partId, 0);
    }

    /**
     * Puts one part into stores. Package-private for the reason
     * {@link CampaignBoat#install} is: the workshop decides what is a spare.
     */
    void stow(String partId) {
        if (partId == null) return;
        if (spares == null) spares = new HashMap<>();
        spares.merge(partId, 1, Integer::sum);
    }

    /** Takes one part out of stores; false, and nothing taken, when there is none. */
    boolean take(String partId) {
        int held = spares(partId);
        if (held <= 0) return false;
        if (held == 1) spares.remove(partId);
        else spares.put(partId, held - 1);
        return true;
    }

    /** Every berth in order; an entry is null for a berth with nothing in it. */
    public List<CampaignBoat> boats() {
        return Collections.unmodifiableList(berths);
    }

    /**
     * Each berth's door guns, mount by mount as her frame carries them, or null for a berth
     * with nothing in it: what a ship's parked boats are drawn wearing.
     */
    public String[][] doorGuns() {
        String[][] guns = new String[berths.size()][];
        for (int berth = 0; berth < berths.size(); berth++) {
            CampaignBoat boat = berths.get(berth);
            if (boat != null) guns[berth] = boat.freezeForDeployment().kitFor(TurretRole.A2G);
        }
        return guns;
    }

    /** The boats that can actually be put in the air, in berth order. */
    public List<CampaignBoat> airworthy() { return airworthy(CampaignClock.dayFloat()); }

    public List<CampaignBoat> airworthy(float day) {
        List<CampaignBoat> flying = new ArrayList<>(berths.size());
        for (CampaignBoat boat : berths) {
            if (boat != null && boat.airworthy(day)) flying.add(boat);
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

    private CampaignBoat build(ShuttleType pattern) {
        int tail = nextTailNumber++;
        String number = String.format(Locale.ROOT, "%02d", tail);
        return new CampaignBoat("boat_" + number, tailName(pattern) + " " + String.format(Strings.locale(), "%02d", tail),
                pattern, BoatFitting.STANDARD_PLATING.id(), BoatFitting.STANDARD_DRIVE.id());
    }

    /** {@code AEROSHUTTLE} reads as {@code Aeroshuttle}, which is what a tail says. */
    private static String tailName(ShuttleType pattern) {
        String name = pattern.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Strings.locale());
    }

    private Object readResolve() {
        if (berths == null) berths = new ArrayList<>();
        if (spares == null) spares = new HashMap<>();
        nextTailNumber = Math.max(nextTailNumber, berths.size() + 1);
        return this;
    }
}
