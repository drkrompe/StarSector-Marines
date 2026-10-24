package com.dillon.starsectormarines.battle.command.allied;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunStockTest {

    @Test
    void aDrainedPlaceIsDryUntilATruckRefillsIt() {
        GunStock stock = new GunStock("redoubt 2", 100).add(1L).add(2L);
        assertEquals(1f, stock.share());
        stock.drain();
        assertEquals(0f, stock.share());
        assertEquals(2, stock.dry());
        stock.refill(null);
        assertEquals(1f, stock.share());
        assertEquals(0, stock.dry());
        assertEquals(1, stock.refills());
    }

    @Test
    void aTrucksArrivalRefillsThroughItsRecipient() {
        GunStock stock = new GunStock("base", 50).add(7L);
        stock.drain();
        boolean[] standing = {true};
        SupplyDelivery.Recipient to = stock.recipient(new int[]{4, 5}, () -> standing[0]);
        assertEquals("base", to.name());
        assertEquals(4, to.where(null)[0]);
        to.arrived(null);
        assertEquals(1f, stock.share());
        standing[0] = false;
        assertEquals(null, to.where(null));
    }

    @Test
    void aShotFiredOutsideTheCooldownSpendsARoundUntilNoneAreLeft() {
        GunStock stock = new GunStock("battery", 2).add(3L);
        assertTrue(stock.spend(3L));
        assertTrue(stock.spend(3L));
        assertFalse(stock.spend(3L));
        assertTrue(stock.spend(9L), "a gun not stocked here fires freely");
    }
}
