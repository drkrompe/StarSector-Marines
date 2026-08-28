package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * What the company loses when its ship does not come home.
 *
 * <p>The rule is that the company keeps what it <b>is</b> and loses what it
 * <b>had</b>. Squads, fire-team templates, doctrines, arrangements, named
 * officers and the machines in the bay are the company itself and survive her;
 * the marines who were aboard, the spares on the bay's shelf and the stores in
 * her holds were aboard a ship that burned. What that leaves is an outfit that
 * still knows how it fights and has to buy back the means to do it.
 *
 * <p><b>There is no armory inventory to sink.</b> A company's equipment is a
 * set of designs it owns permanently rather than a rack it draws down: owning a
 * template card is what lets a squad be issued a weapon, and what an issue
 * actually consumes is fleet cargo. So the materiel cost of a sinking lands
 * where the counted things are — the spare mech components and the stores — and
 * restocking those is what makes the company workable again.
 *
 * <p><b>Holding the field decides who is picked up.</b> Losing the ship out of
 * a battle the player still won leaves boats in the water and time to use them;
 * losing her out of a rout does not. That is the one lever the player has over
 * the toll after the ship is already burning, and it is why the same loss reads
 * as a bad day or as a disaster.
 *
 * @see CompanyShipLossListener
 */
public final class ShipLossSettlement {

    /** Share who come through when the player still owned the field. */
    private static final float SURVIVAL_FIELD_HELD = 0.65f;

    /** Share who come through a rout, with nobody coming back for them. */
    private static final float SURVIVAL_ROUTED = 0.35f;

    /** What a company keeps in its own ship's holds. */
    private static final String[] STORES = {
            Commodities.MARINES, Commodities.HAND_WEAPONS,
            Commodities.SUPPLIES, Commodities.HEAVY_MACHINERY };

    private ShipLossSettlement() { }

    /**
     * The bill for one lost ship.
     *
     * @param marinesLost named marines who went down with her
     * @param marinesSurvived named marines who were picked up
     * @param sparesLost spare mech components destroyed on the bay shelf
     * @param storesLost units of cargo that went down in her holds
     */
    public record Toll(int marinesLost, int marinesSurvived,
                       int sparesLost, int storesLost) {

        public static final Toll NOTHING = new Toll(0, 0, 0, 0);

        /** Whether anything at all was lost worth telling the player about. */
        public boolean anything() {
            return marinesLost > 0 || sparesLost > 0 || storesLost > 0;
        }
    }

    /**
     * Settle a confirmed loss against the company and the fleet's holds.
     *
     * @param holds the fleet's cargo, or null when there is none to draw from
     * @param holdCapacity how much the lost ship could carry, which bounds what
     *     can plausibly have been aboard her
     * @param heldTheField whether the player still owned the field afterwards
     * @param seed fixes the roll, so reloading the save cannot buy a better one
     */
    public static Toll settle(MarineRoster roster, CargoAPI holds,
                              int holdCapacity, boolean heldTheField, long seed) {
        if (roster == null) return Toll.NOTHING;
        Random rolls = new Random(seed);
        float survival = heldTheField ? SURVIVAL_FIELD_HELD : SURVIVAL_ROUTED;

        // Squads away on a stationing contract are somewhere else in the sector
        // and were never aboard, which is the player's standing hedge against
        // losing everybody at once.
        List<MarineSoldier> aboard = MarineOpsContext.companyMarines(roster);
        Set<String> fallen = new LinkedHashSet<>();
        for (MarineSoldier marine : aboard) {
            if (rolls.nextFloat() >= survival) fallen.add(marine.id());
        }
        roster.applySoldierOutcome(Set.of(), fallen);
        int survivors = aboard.size() - fallen.size();

        int spares = roster.mechBay().loseSpares();
        int stores = sink(holds, holdCapacity);
        return new Toll(fallen.size(), survivors, spares, stores);
    }

    /**
     * Stores go down in proportion to what the fleet was carrying, bounded by
     * what the lost ship could hold. Nothing distinguishes one crate in a fleet
     * pool from another, so her share is her share of the total rather than a
     * manifest nobody keeps.
     */
    private static int sink(CargoAPI holds, int holdCapacity) {
        if (holds == null || holdCapacity <= 0) return 0;
        float[] stock = new float[STORES.length];
        float total = 0f;
        for (int index = 0; index < STORES.length; index++) {
            stock[index] = Math.max(0f, holds.getCommodityQuantity(STORES[index]));
            total += stock[index];
        }
        if (total <= 0f) return 0;

        float lost = Math.min(total, holdCapacity);
        int sunk = 0;
        for (int index = 0; index < STORES.length; index++) {
            int share = Math.round(stock[index] / total * lost);
            if (share <= 0) continue;
            holds.removeCommodity(STORES[index], share);
            sunk += share;
        }
        return sunk;
    }
}
