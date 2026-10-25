package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A hull fitted at its own presence carries its whole body at that size, and nothing else. */
class WarshipPresenceTest {

    @BeforeEach
    void install() {
        PilotShipFixture.install();
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    @Test
    void aHullFittedAtAThirdOfThePresenceIsAThirdTheSizeWithItsMountsAndReachAsBefore() throws Exception {
        WarshipLoadout loadout = new StockVariantLoadout("test", "test_Standard");
        Warship standard = Warship.fit(loadout, Faction.MARINE, 0f, 0f, 0f);
        float third = WarshipScale.HULL_PRESENCE / 3f;
        Warship small = Warship.fit(loadout, Faction.MARINE, 0f, 0f, 0f, third);

        assertEquals(1f, standard.presenceShare(), 1e-6f);
        assertEquals(third, small.presence(), 1e-6f);
        assertEquals(standard.lengthCells / 3f, small.lengthCells, 1e-4f);
        assertEquals(standard.widthCells / 3f, small.widthCells, 1e-4f);
        assertEquals(standard.hullCells(90f) / 3f, small.hullCells(90f), 1e-4f);
        for (int i = 0; i < standard.mounts().size(); i++) {
            Warship.Mount big = standard.mounts().get(i);
            Warship.Mount near = small.mounts().get(i);
            assertEquals(standard.mountY(big) / 3f, small.mountY(near), 1e-4f, "a mount sits where its slot does");
            assertEquals(big.weapon.rangePx(), near.weapon.rangePx(), "a gun reaches as far whatever the hull's size");
        }
        assertEquals(LandedScale.berthPresence() / third, LandedScale.berthFactor(small.presence()), 1e-4f);
        assertThrows(IllegalArgumentException.class, () -> Warship.fit(loadout, Faction.MARINE, 0f, 0f, 0f, 0f));
    }
}
