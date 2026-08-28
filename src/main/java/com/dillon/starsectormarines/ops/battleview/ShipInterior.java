package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one hull would give the company, read off a deck rather than a datasheet.
 *
 * <p>The question the player is actually asking when they weigh one ship
 * against another: not how large she is, but what would be aboard. Answered by
 * generating the candidate's deck the same way the company's own is generated,
 * because a preview produced any other way is advertising a ship the player
 * will not get.
 *
 * <p><b>A room the deck could not fit is a room the ship does not have.</b> A
 * hull whose class never programs a firing range and a hull that owed one and
 * had nowhere to put it are different stories inside the generator and the same
 * fact aboard: there is no such place. So both come back as absence, which is
 * what makes the comparison honest about a big hull badly proportioned.
 *
 * <p><b>Capacity is what the rooms hold, not how much floor they cover.</b> A
 * berthing berths nine because nine racks fit in it; two bays of different
 * sizes service different numbers of machines. Counting the arrangement rather
 * than the area is what lets a cramped hull fitted well out-berth a large one
 * fitted badly, which is the comparison worth making.
 *
 * <p>Presence and capacity are therefore read from different places, on
 * purpose. Whether there is such a room aboard is the deck's answer, so a
 * comparison never denies a place the player could walk into; how much it holds
 * is the program's, so the spares cage the packer dropped into a corner does not
 * read as cargo capacity.
 */
public record ShipInterior(CompanyShip ship, Map<RoomPurpose, Facility> facilities) {

    /**
     * One kind of place aboard, as much of it as the ship has.
     *
     * @param rooms how many separate compartments of this kind the deck holds;
     *     a ship berths her people in many small compartments rather than one
     *     dormitory, and the count is what says so
     * @param capacity what they hold between them, in whatever unit the purpose
     *     implies — bunks for berthing, serviced hulls for a bay, hold units for
     *     a stockroom. Zero for a place that earns its keep by function, like a
     *     bridge.
     * @param programmed whether the ship <em>owes</em> a room of this kind, as
     *     against having ended up with one. A spares cage the packer dropped
     *     into an awkward corner is a real room the player can walk into and is
     *     not a reason to choose a ship; two hulls whose leftovers fell
     *     differently have not gained or lost a facility between them.
     */
    public record Facility(int rooms, int capacity, boolean programmed) {

        static final Facility NONE = new Facility(0, 0, false);
    }

    public ShipInterior {
        facilities = Map.copyOf(facilities);
    }

    /**
     * Generate this hull's deck and read what is aboard her.
     *
     * <p>A hull with no playable interior comes back empty rather than
     * refusing: that a fighter holds nothing is the answer the comparison
     * wants, not an error.
     */
    public static ShipInterior of(CompanyShip ship, long seed) {
        if (ship == null) throw new IllegalArgumentException("a ship is required");
        if (!ship.habitable()) return new ShipInterior(ship, Map.of());

        DeckGraph deck = new CompanyDeck(ship, seed).rooms();

        // What is aboard comes from the deck, and what it holds from the
        // program. A deck carries more rooms than the program owed: the packer
        // fills the pockets the authored rooms did not want with lockers, cable
        // trunks and spares cages. Those are real places and are counted as
        // such, and they supply nothing, so a hull does not acquire hold space
        // by having awkward corners.
        Map<RoomPurpose, Integer> rooms = new EnumMap<>(RoomPurpose.class);
        for (DeckGraph.Compartment compartment : deck.compartments()) {
            rooms.merge(compartment.purpose(), 1, Integer::sum);
        }

        List<RoomRecipe> programmed = new ArrayList<>(ship.deckPlan().rooms());
        for (RoomRecipe missing : deck.unplaced()) programmed.remove(missing);
        Map<RoomPurpose, Integer> capacity = new EnumMap<>(RoomPurpose.class);
        for (RoomRecipe room : programmed) {
            capacity.merge(room.purpose(), room.provides(), Integer::sum);
        }

        Set<RoomPurpose> owed = new HashSet<>();
        for (RoomRecipe room : programmed) owed.add(room.purpose());

        Map<RoomPurpose, Facility> found = new EnumMap<>(RoomPurpose.class);
        for (Map.Entry<RoomPurpose, Integer> entry : rooms.entrySet()) {
            found.put(entry.getKey(), new Facility(entry.getValue(),
                    capacity.getOrDefault(entry.getKey(), 0),
                    owed.contains(entry.getKey())));
        }
        return new ShipInterior(ship, found);
    }

    /** Whether the ship has anywhere of this kind at all. */
    public boolean has(RoomPurpose purpose) {
        return facilities.containsKey(purpose);
    }

    /** What this ship's places of one kind hold between them. */
    public Facility facility(RoomPurpose purpose) {
        return facilities.getOrDefault(purpose, Facility.NONE);
    }

    /** Everything aboard, in the enum's own order so two ships read alike. */
    public List<RoomPurpose> purposes() {
        return sorted(facilities.keySet());
    }

    /**
     * The places the ship owes rather than merely has — what a player would
     * choose a hull for, and what a comparison is about.
     */
    public List<RoomPurpose> programmed() {
        List<RoomPurpose> owed = new ArrayList<>();
        for (Map.Entry<RoomPurpose, Facility> entry : facilities.entrySet()) {
            if (entry.getValue().programmed()) owed.add(entry.getKey());
        }
        return sorted(owed);
    }

    private static List<RoomPurpose> sorted(Collection<RoomPurpose> purposes) {
        List<RoomPurpose> order = new ArrayList<>(purposes);
        order.sort(Comparator.comparingInt(RoomPurpose::ordinal));
        return order;
    }
}
