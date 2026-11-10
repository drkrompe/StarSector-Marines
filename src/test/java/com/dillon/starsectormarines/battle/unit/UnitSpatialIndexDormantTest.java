package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A dormant body (a ruins battery with its power cut) is left out of every side's hostile scans until it wakes. */
class UnitSpatialIndexDormantTest {

    @Test void aDormantHazardIsNobodysTargetUntilItWakes() {
        UnitSpatialIndex index = new UnitSpatialIndex(32, 32);
        UnitRosterService roster = new UnitRosterService(index, null);
        long gun = roster.spawn(new EntitySpec("gun", Faction.HAZARD, UnitType.MARINE_BLUE, 10, 10));
        roster.spawn(new EntitySpec("crew", Faction.DEFENDER, UnitType.MARINE_BLUE, 14, 10));
        index.setDormant(gun, true);
        index.rebuild(roster);

        LongBucket found = new LongBucket();
        index.gatherHostileCombatants(12.5f, 10.5f, 12f, Faction.DEFENDER, found);
        assertEquals(0, found.size, "the occupant passes over the dark gun");
        assertTrue(index.withheld(gun));

        index.setDormant(gun, false);
        index.rebuild(roster);
        index.gatherHostileCombatants(12.5f, 10.5f, 12f, Faction.DEFENDER, found);
        assertEquals(1, found.size, "awake, it is the occupant's enemy");
        assertEquals(gun, found.ids[0]);
        assertFalse(index.withheld(gun));
    }
}
