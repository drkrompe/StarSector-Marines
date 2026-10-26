package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The third fitting slot: who can take a cradle, what it costs, and what old boats read as. */
class BoatLiftFittingTest {

    @Test
    void cradlesGoOnlyIntoBusClassHoldsThatDoNotAlreadyLiftMore() {
        for (ShuttleType pattern : ShuttleType.values()) {
            boolean bus = pattern.busClass() && pattern != ShuttleType.VALKYRIE;
            assertEquals(bus, BoatFitting.LIFT_CRADLE.supports(pattern), pattern.name());
            assertEquals(bus, BoatFitting.HEAVY_CRADLE.supports(pattern), pattern.name());
            assertTrue(BoatFitting.STANDARD_LIFT.supports(pattern), pattern.name());
        }
        assertTrue(ShuttleType.BUFFALO.busClass());
        assertFalse(ShuttleType.AEROSHUTTLE.busClass());
    }

    @Test
    void liftFittingsCarryRatingsAndBills() {
        assertEquals(LiftRating.NONE, BoatFitting.STANDARD_LIFT.liftRating());
        assertEquals(LiftRating.LIGHT, BoatFitting.LIFT_CRADLE.liftRating());
        assertEquals(LiftRating.MEDIUM, BoatFitting.HEAVY_CRADLE.liftRating());
        assertNotNull(BoatFitting.LIFT_CRADLE.bill());
        assertNotNull(BoatFitting.HEAVY_CRADLE.bill());
        assertSame(BoatFitting.STANDARD_LIFT, BoatFitting.standard(BoatFittingSlot.LIFT));
        assertEquals(3, BoatFitting.catalog(BoatFittingSlot.LIFT).size());
    }

    @Test
    void aBoatSavedBeforeTheLiftSlotReadsAsStandardLift() throws Exception {
        CampaignBoat boat = new CampaignBoat("b1", "Old Faithful", ShuttleType.BUFFALO,
                "reinforced_plating", "tuned_drive");
        Field liftId = CampaignBoat.class.getDeclaredField("liftId");
        liftId.setAccessible(true);
        liftId.set(boat, null); // what Java deserialisation leaves on an old save

        assertSame(BoatFitting.STANDARD_LIFT, boat.lift());
        FittedBoat frozen = boat.freezeForDeployment();
        assertSame(BoatFitting.STANDARD_LIFT, frozen.lift());
        assertEquals(LiftRating.NONE, frozen.liftRating());
        assertFalse(frozen.cradled());
    }

    @Test
    void aWorkshopFitsACradleAndTheFrozenBoatLiftsLight() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("freighter", ShuttleType.BUFFALO, 1);
        BoatWorkshopTest.TestResources hold = BoatWorkshopTest.TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();

        assertEquals(BoatWorkshop.Status.PART_FABRICATED, workshop.fabricatePart("lift_cradle").status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boatId, "lift_cradle").status());

        FittedBoat frozen = deck.boatById(boatId).freezeForDeployment();
        assertEquals(LiftRating.LIGHT, frozen.liftRating());
        assertTrue(frozen.cradled());
        assertEquals(85, hold.available(Commodities.SUPPLIES));
    }

    @Test
    void aSmallBoatRefusesACradleBeforeCharging() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("transport", ShuttleType.AEROSHUTTLE, 1);
        BoatWorkshopTest.TestResources hold = BoatWorkshopTest.TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();

        assertEquals(BoatWorkshop.Status.INCOMPATIBLE_FITTING,
                workshop.fit(boatId, "lift_cradle").status());
        assertEquals(100, hold.available(Commodities.SUPPLIES));
        assertEquals(0, deck.spares("lift_cradle"));
    }
}
