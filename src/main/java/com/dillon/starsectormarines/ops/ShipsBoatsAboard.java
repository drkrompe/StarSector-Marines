package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * The lift the company actually has: the boats aboard the ship they are on.
 *
 * <p><b>This replaces asking the fleet.</b> A detachment used to be assembled
 * from whichever transports the player owned and chose to commit, which made a
 * lift a thing you shopped for and a Valkyrie one of the items. It is not. The
 * company lives aboard one ship, that ship has bays, and what is in them is the
 * lift — so the question "what can we put on the ground" has exactly one answer
 * and it is a fact about where you are standing.
 *
 * <p>The campaign-side half of {@link ShipsBoats}: that one derives boats from a
 * hull and knows nothing about a sector, and this finds the hull. Split so the
 * derivation can be asked about any ship — a prize, a hull being considered —
 * rather than only about the one the company is on.
 *
 * <p><b>The hull still sizes the berths; the company owns what stands in
 * them.</b> This is where those two meet, so the deck is reconciled against the
 * ship here rather than trusted: a lift read off a deck that belongs to a ship
 * the company has already left would be boats that are not there.
 */
public final class ShipsBoatsAboard {

    private ShipsBoatsAboard() { }

    /**
     * Every boat the company can put in the air, or empty when they are aboard
     * nothing this can read.
     *
     * <p>Empty is a real answer rather than a failure: a company with no ship
     * has no lift, and a briefing should say so plainly instead of quietly
     * finding transports somewhere else.
     */
    public static List<FittedBoat> lift() {
        FleetMemberAPI aboard = CompanyShipDesignation.aboard();
        if (aboard == null) return List.of();

        BoatDeck deck = deck();
        // A headless caller with no campaign behind it still has a hull and a
        // berth count, and the honest answer for it is the boats the hull comes
        // with — the same set a fresh deck would reconcile to.
        if (deck == null) {
            CompanyShip ship = CompanyShipResolver.read(aboard);
            return standardFit(ShipsBoats.carriedBy(ship.role()),
                    ShipsBoats.aboard(ship).size());
        }

        reconcile(deck);
        List<FittedBoat> flying = new ArrayList<>(deck.berths());
        for (CampaignBoat boat : deck.airworthy()) flying.add(boat.freezeForDeployment());
        return List.copyOf(flying);
    }

    /**
     * Brings a deck into step with the ship the company is actually aboard.
     *
     * <p>The one place that knows how the reconcile is fed. The lift and the
     * Boat Deck screen both have to run it — a stale deck is never presented
     * and never flown — and neither of them has any business deciding for
     * itself which hull, which pattern and how many berths that means. Asked
     * twice in a row it is a no-op, which is what lets both callers simply run
     * it rather than working out whether the other one has.
     *
     * @return what the move this call discovered left behind, or
     *     {@link BoatDeck.LeftBehind#NONE} when the company is aboard nothing
     *     this can read
     */
    public static BoatDeck.LeftBehind reconcile(BoatDeck deck) {
        if (deck == null) return BoatDeck.LeftBehind.NONE;
        FleetMemberAPI aboard = CompanyShipDesignation.aboard();
        if (aboard == null) return BoatDeck.LeftBehind.NONE;
        CompanyShip ship = CompanyShipResolver.read(aboard);
        return deck.reconcile(aboard.getId(), ShipsBoats.carriedBy(ship.role()),
                ShipsBoats.aboard(ship).size());
    }

    /** The ship the lift comes off, for a screen that wants to name her. */
    public static String carrier() {
        FleetMemberAPI aboard = CompanyShipDesignation.aboard();
        return aboard == null ? null : aboard.getShipName();
    }

    private static BoatDeck deck() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script == null ? null : script.roster();
        return roster == null ? null : roster.boatDeck();
    }

    private static List<FittedBoat> standardFit(ShuttleType pattern, int berths) {
        List<FittedBoat> boats = new ArrayList<>(berths);
        for (int i = 0; i < berths; i++) boats.add(FittedBoat.standard(pattern));
        return List.copyOf(boats);
    }
}
