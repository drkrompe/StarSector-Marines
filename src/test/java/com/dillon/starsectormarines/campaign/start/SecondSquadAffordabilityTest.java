package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.PreparationDepot.Purpose;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The story Relief has no squad cap because a new Second Wind company cannot pay for a second squad before it
 * (owner, 2026-10-20). The prepaid depot founds exactly one squad, Morgan's; any other comes out of the hold at the
 * full {@link SquadFoundingCost#STANDARD} bill: its marines bought into the hold, and its price for the stores.
 * This prices the most desperate liquidation there is, every crewman, all the fuel and both guns sold, with every
 * price at the extreme that favours the player, against that bill.
 *
 * <p>Base prices are vanilla's {@code commodities.csv} (marines 200, supplies 100, food 20, fuel 25, crew 50) and
 * the Vulcan's base value of 200 from {@code weapon_data.csv}. The swings are vanilla {@code settings.json}:
 * {@code economyExcessPriceMultMin} 0.85, {@code economyDeficitPriceMultMax} 1.25 and
 * {@code shipWeaponSellPriceMult} 0.5. The black market charges no tariff, so none is applied. The Cerberus's
 * minimum crew of 20 is the hull's own ({@code cerberus_d} skins {@code cerberus}).
 */
class SecondSquadAffordabilityTest {
    private static final Map<String, Integer> BASE_PRICE = Map.of(Commodities.MARINES, 200,
            Commodities.SUPPLIES, 100, Commodities.FOOD, 20, Commodities.FUEL, 25, Commodities.CREW, 50);
    private static final double CHEAPEST_BUY = 0.85, DEAREST_SALE = 1.25, WEAPON_SALE = 0.5;
    private static final int CERBERUS_MIN_CREW = 20, VULCANS = 2, VULCAN_VALUE = 200;

    @Test
    void sellingEverythingAboardCannotPayForASecondSquad() {
        int crew = CERBERUS_MIN_CREW + CerberusCompanyStart.SURPLUS_CREW;
        double raised = CerberusCompanyStart.STARTING_CREDITS
                + DEAREST_SALE * (CerberusCompanyStart.STARTING_FUEL * BASE_PRICE.get(Commodities.FUEL)
                        + crew * BASE_PRICE.get(Commodities.CREW))
                + WEAPON_SALE * VULCANS * VULCAN_VALUE;
        double bill = SquadFoundingCost.STANDARD.credits();
        for (var line : SquadFoundingCost.STANDARD.lines()) {
            bill += CHEAPEST_BUY * line.quantity() * BASE_PRICE.get(line.commodityId());
        }
        assertTrue(raised < bill, "a fire sale raises " + Math.round(raised) + " against a bill of " + Math.round(bill));
    }

    @Test
    void theDepotFoundsOneSquadAndNothingElseCanDrawIt() {
        PreparationDepot depot = new PreparationDepot("jangala", true);
        for (var line : SquadFoundingCost.STANDARD.lines()) {
            assertEquals(line.quantity(), depot.available(line.commodityId(), Purpose.SQUAD));
            assertEquals(line.quantity(), depot.draw(line.commodityId(), line.quantity() * 2, Purpose.SQUAD));
            assertEquals(0, depot.available(line.commodityId(), Purpose.SQUAD));
            assertEquals(0, depot.available(line.commodityId(), Purpose.GENERAL));
        }
        int price = SquadFoundingCost.STANDARD.credits();
        assertEquals(0, depot.credits(Purpose.GENERAL));
        assertEquals(price, depot.drawCredits(price * 2, Purpose.SQUAD), "the squad's credit and none of the boat's");
        assertEquals(0, depot.credits(Purpose.SQUAD));
    }
}
