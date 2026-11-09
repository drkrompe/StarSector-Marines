package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuinsDefenceTest {

    @Test
    void anEmptySiteFieldsNoOne() {
        assertEquals(RuinsDefence.NONE, RuinsDefence.of(RuinsOccupant.NOBODY));
    }

    @Test
    void onlyTheScavengersFieldACrew() {
        for (RuinsOccupant occupant : RuinsOccupant.values()) {
            if (occupant == RuinsOccupant.SCAVENGERS) assertEquals(RuinsDefence.CREW, RuinsDefence.of(occupant));
            else assertNotEquals(RuinsDefence.CREW, RuinsDefence.of(occupant), occupant + " fields a crew");
        }
    }

    @Test
    void eachOccupiedSiteFieldsItsOwnSide() {
        assertEquals(RuinsDefence.SECURITY, RuinsDefence.of(RuinsOccupant.DOMAIN_SECURITY));
        assertEquals(RuinsDefence.SECURITY, RuinsDefence.of(RuinsOccupant.CONTINGENCY_SECURITY));
        assertEquals(RuinsDefence.SLEEPERS, RuinsDefence.of(RuinsOccupant.RAV));
    }
}
