package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A {@link ScanCover} keeps covered bodies out of hostile scans until it is lifted, and nothing else. */
class UnitSpatialIndexCoverTest {

    @Test void coveredBodiesAreLeftOutOfHostileScansUntilTheCoverLifts() {
        UnitSpatialIndex index = new UnitSpatialIndex(32, 32);
        UnitRosterService roster = new UnitRosterService(index, null);
        long sentry = roster.spawn(new EntitySpec("sentry", Faction.DEFENDER, UnitType.MARINE_BLUE, 10, 10));
        long outside = roster.spawn(new EntitySpec("outside", Faction.MARINE, UnitType.MARINE_BLUE, 4, 10));
        long inside = roster.spawn(new EntitySpec("inside", Faction.MARINE, UnitType.MARINE_BLUE, 12, 10));
        index.cover((faction, x, y) -> faction == Faction.DEFENDER || x < 8f);
        index.rebuild(roster);

        LongBucket found = new LongBucket();
        index.gatherHostileCombatants(10.5f, 10.5f, 12f, Faction.DEFENDER, found);
        assertEquals(1, found.size);
        assertEquals(inside, found.ids[0]);
        index.gatherHostileCombatants(10.5f, 10.5f, 12f, Faction.MARINE, found);
        assertEquals(0, found.size, "the covered sentry is not offered to the company");
        assertTrue(index.withheld(sentry));
        assertTrue(index.withheld(outside));
        assertFalse(index.withheld(inside));
        LongBucket everyone = new LongBucket();
        index.gather(10.5f, 10.5f, 12f, everyone);
        assertEquals(3, everyone.size, "covered bodies are still there to see and hit");

        index.cover(null);
        index.rebuild(roster);
        index.gatherHostileCombatants(10.5f, 10.5f, 12f, Faction.DEFENDER, found);
        assertEquals(2, found.size);
        assertFalse(index.withheld(sentry));
    }
}
