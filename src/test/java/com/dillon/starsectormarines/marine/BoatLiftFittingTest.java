package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hold's lift: a ladder of its own up to Heavy, on any boat that does not already lift as much. */
class BoatLiftFittingTest {

    @Test
    void eachHoldTierLiftsOneHeavierClass() {
        assertEquals(List.of(LiftRating.NONE, LiftRating.LIGHT, LiftRating.MEDIUM, LiftRating.HEAVY),
                BoatFitting.catalog(BoatFittingSlot.LIFT).stream().map(BoatFitting::liftRating).toList());
    }

    @Test
    void aHoldGoesIntoAnyBoatThatDoesNotAlreadyLiftAsMuch() {
        assertTrue(BoatFitting.HEAVY_LIFT_FRAME.supports(ShuttleType.HERMES));
        assertTrue(BoatFitting.LIFT_CRADLE.supports(ShuttleType.AEROSHUTTLE));
        assertFalse(BoatFitting.HEAVY_LIFT_FRAME.supports(ShuttleType.VALKYRIE), "a Valkyrie lifts Heavy as built");
        assertTrue(BoatFitting.STANDARD_LIFT.supports(ShuttleType.VALKYRIE));
    }

    @Test
    void aHermesFittedWithAHeavyLiftFrameLiftsHeavyAndKeepsHerSeats() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        BoatWorkshop workshop = new BoatWorkshop(deck, BoatWorkshopTest.TestResources.stocked(500));
        CampaignBoat boat = deck.flightReady().get(0);

        assertEquals(BoatWorkshop.Status.PART_FABRICATED, workshop.fabricatePart("heavy_lift_frame").status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boat.id(), "heavy_lift_frame").status());

        FittedBoat frozen = boat.freezeForDeployment();
        assertEquals(LiftRating.HEAVY, frozen.liftRating());
        assertTrue(frozen.cradled());
        assertEquals(BoatFitting.STANDARD_CABIN, frozen.fitting(BoatFittingSlot.CABIN), "lift never costs a seat");
    }

    @Test
    void aCradleOnAFreighterSpendsItsBillWhenBuilt() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("freighter", ShuttleType.BUFFALO, 1);
        BoatWorkshopTest.TestResources hold = BoatWorkshopTest.TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();

        workshop.fabricatePart("lift_cradle");
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boatId, "lift_cradle").status());

        assertEquals(LiftRating.LIGHT, deck.boatById(boatId).freezeForDeployment().liftRating());
        assertEquals(85, hold.available(Commodities.SUPPLIES));
    }
}
