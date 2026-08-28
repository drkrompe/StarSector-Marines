package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a candidate hull would give the company, and what it would cost them.
 *
 * <p>The invariant that matters most is that a preview and the ship it previews
 * are the same ship. A comparison generated any other way than the deck itself
 * is advertising a vessel the player will not get, and the player only finds out
 * after they have moved.
 */
class ShipInteriorTest {

    private static final long SEED = 0x5AFE_DECEL;

    private static final CompanyShip TRANSPORT = TestHulls.transport();
    private static final CompanyShip CAPITAL_TRANSPORT = new CompanyShip(
            HullClass.CAPITAL, HullRole.TROOP_TRANSPORT, 60, 400, 250, 0.34f);
    private static final CompanyShip WARSHIP = new CompanyShip(
            HullClass.CRUISER, HullRole.WARSHIP, 200, 300, 100, 0.30f);
    private static final CompanyShip FRIGATE = new CompanyShip(
            HullClass.FRIGATE, HullRole.TROOP_TRANSPORT, 10, 40, 20, 0.40f);
    /** Carried rather than entered: the one hull class with no deck of its own. */
    private static final CompanyShip FIGHTER = new CompanyShip(
            HullClass.FIGHTER, HullRole.WARSHIP, 1, 2, 0, 0.90f);

    @Test
    @DisplayName("a preview is of the deck the player would actually get")
    void whatIsPreviewedIsWhatTheShipHas() {
        for (CompanyShip hull : List.of(TRANSPORT, CAPITAL_TRANSPORT, WARSHIP, FRIGATE)) {
            ShipInterior preview = ShipInterior.of(hull, SEED);
            CompanyDeck real = new CompanyDeck(hull, SEED);
            for (RoomPurpose purpose : RoomPurpose.values()) {
                assertEquals(real.has(purpose), preview.has(purpose),
                        hull.role() + " " + hull.hullClass()
                                + " disagrees with its own deck about " + purpose);
            }
        }
    }

    @Test
    @DisplayName("a hull with no interior holds nothing rather than refusing to say")
    void anUninhabitableHullHoldsNothing() {
        ShipInterior none = ShipInterior.of(FIGHTER, SEED);

        assertTrue(none.purposes().isEmpty());
        assertFalse(none.has(RoomPurpose.BARRACKS));
        assertEquals(0, none.facility(RoomPurpose.BARRACKS).capacity());
    }

    @Test
    @DisplayName("berthing is counted in bunks across many compartments, not in floor")
    void berthingIsCountedInWhatItHolds() {
        ShipInterior transport = ShipInterior.of(TRANSPORT, SEED);
        ShipInterior.Facility berthing = transport.facility(RoomPurpose.BARRACKS);

        assertTrue(berthing.rooms() > 1,
                "a ship berths her people in many small compartments");
        assertEquals(0, berthing.capacity() % berthing.rooms(),
                "every berthing holds the same racks, so bunks divide by rooms");
        assertTrue(berthing.capacity() > berthing.rooms(),
                "a compartment holds more than one person");
    }

    @Test
    @DisplayName("moving nowhere changes nothing")
    void aShipComparedWithHerselfIsNoTrade() {
        ShipInterior here = ShipInterior.of(TRANSPORT, SEED);
        InteriorChange staying = new InteriorChange(here, here);

        assertTrue(staying.gained().isEmpty());
        assertTrue(staying.lost().isEmpty());
        assertFalse(staying.costsSomething());
        for (RoomPurpose purpose : staying.purposes()) {
            assertEquals(0, staying.capacityChange(purpose));
        }
    }

    @Test
    @DisplayName("nothing is both gained and lost")
    void gainsAndLossesDoNotOverlap() {
        InteriorChange move = new InteriorChange(
                ShipInterior.of(TRANSPORT, SEED),
                ShipInterior.of(WARSHIP, SEED));

        for (RoomPurpose gained : move.gained()) {
            assertFalse(move.lost().contains(gained), gained + " both gained and lost");
            assertFalse(move.kept().contains(gained), gained + " both gained and kept");
        }
        assertEquals(move.purposes().size(),
                move.gained().size() + move.lost().size() + move.kept().size(),
                "every place either ship owes is gained, lost, or kept");
    }

    @Test
    @DisplayName("a bigger hull is not automatically a better home")
    void aMoveCanCostSomething() {
        InteriorChange toWarship = new InteriorChange(
                ShipInterior.of(TRANSPORT, SEED),
                ShipInterior.of(WARSHIP, SEED));

        // A warship of the same class carries her own crew rather than a ground
        // force, so the company's own space is what she does not have.
        assertTrue(toWarship.capacityChange(RoomPurpose.BARRACKS) < 0,
                "a warship should berth fewer marines than a transport of her class");
    }

    @Test
    @DisplayName("the company ship the player is offered is the one they would live on")
    void aLargerTransportIsALargerHome() {
        InteriorChange promotion = new InteriorChange(
                ShipInterior.of(TRANSPORT, SEED),
                ShipInterior.of(CAPITAL_TRANSPORT, SEED));

        assertTrue(promotion.capacityChange(RoomPurpose.BARRACKS) > 0);
        assertFalse(promotion.costsSomething(),
                "a capital transport should not take anything away from a cruiser one; lost " + promotion.lost());
    }
}
