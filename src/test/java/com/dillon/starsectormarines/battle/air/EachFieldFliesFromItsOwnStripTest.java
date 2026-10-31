package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One airfield service holds every field on the map, each with its own owner, strip and berths (`air-nouns.md`,
 * Airbase), and each strip takes one aircraft at a time independently of the others.
 */
class EachFieldFliesFromItsOwnStripTest {

    private static final Runway WEST = new Runway(0f, 10f, 40f, 10f, 4f);
    private static final Runway EAST = new Runway(60f, 10f, 100f, 10f, 4f);

    /** A berth flies from its own field's strip and belongs to its own field's owner; the first field is untouched. */
    @Test
    void aBerthBelongsToItsOwnField() {
        AirfieldService service = new AirfieldService();
        AirfieldService.Berth garrison = service.addBerth(
                LandingPad.garrison(5, 5, LandingPad.Approach.SOUTH), ShuttleType.AEROSHUTTLE, 0f);
        AirfieldService.Field west = service.addField(Faction.ALLY, WEST, true);
        AirfieldService.Field east = service.addField(Faction.ALLY, EAST, true);
        AirfieldService.Berth w = service.addBerth(west, LandingPad.allied(20, 20, LandingPad.Approach.NORTH),
                ShuttleType.AEROSHUTTLE, 0f);
        AirfieldService.Berth e = service.addBerth(east, LandingPad.allied(80, 20, LandingPad.Approach.NORTH),
                ShuttleType.AEROSHUTTLE, 0f);

        assertSame(WEST, service.stripOf(w));
        assertSame(EAST, service.stripOf(e));
        assertNull(service.stripOf(garrison), "the garrison field has no strip until it is given one");
        assertEquals(Faction.ALLY, service.ownerOf(w));
        assertEquals(Faction.DEFENDER, service.ownerOf(garrison));
        assertNull(service.runway(), "a second field's strip is not the first field's");
        assertEquals(3, service.fields().size());
    }

    /** Two strips, two occupants: a departure on one does not hold the other short. */
    @Test
    void eachStripTakesItsOwnOneAircraft() {
        AirfieldService service = new AirfieldService();
        service.addField(Faction.ALLY, WEST, true);
        service.addField(Faction.ALLY, EAST, true);

        assertTrue(service.claimRunway(WEST, 1L));
        assertFalse(service.claimRunway(WEST, 2L), "one strip still takes one aircraft");
        assertTrue(service.claimRunway(EAST, 2L), "the other base's strip is not held by the first's traffic");
        assertEquals(1L, service.runwayOccupant(WEST));
        assertEquals(2L, service.runwayOccupant(EAST));

        service.releaseRunway(1L);
        assertEquals(0L, service.runwayOccupant(WEST));
        assertEquals(2L, service.runwayOccupant(EAST), "a release frees only the releasing aircraft's strip");
        service.takeRunway(WEST, 3L);
        assertEquals(3L, service.runwayOccupant(WEST));
    }
}
