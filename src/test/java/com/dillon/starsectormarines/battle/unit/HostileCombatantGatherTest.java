package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one query nearly all target acquisition bottoms out in, asked with three
 * sides on the map.
 *
 * <p>It used to gather every combatant of a <em>different</em> faction, which
 * is the same answer as "hostile" for exactly as long as there are two sides
 * that fight. With an allied militia on the field the two sentences part
 * company, and this is where they are pinned apart: what a marine gathers,
 * what a defender gathers, and what the ally gathers are three different sets
 * over the same three bodies.
 */
class HostileCombatantGatherTest {

    private static final float RADIUS = 8f;

    private final UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
    private final UnitRosterService roster = new UnitRosterService(index, null);

    private final long marine = roster.spawn(new EntitySpec("marine",
            Faction.MARINE, UnitType.MARINE, 10, 10));
    private final long defender = roster.spawn(new EntitySpec("defender",
            Faction.DEFENDER, UnitType.MILITIA, 12, 10));
    private final long ally = roster.spawn(new EntitySpec("ally",
            Faction.ALLY, UnitType.MILITIA, 10, 12));

    @Test
    void aMarineGathersTheDefenderAndNotTheAlly() {
        LongBucket out = gatherFor(Faction.MARINE);

        assertTrue(contains(out, defender), "the defender is what a marine shoots at");
        assertFalse(contains(out, ally),
                "an allied militia is a different faction and is not a target");
        assertFalse(contains(out, marine), "nor is the querier's own side");
    }

    @Test
    void aDefenderGathersBothOfThem() {
        LongBucket out = gatherFor(Faction.DEFENDER);

        assertTrue(contains(out, marine));
        assertTrue(contains(out, ally),
                "the defender fights the ally too, which a same-faction test cannot say");
        assertFalse(contains(out, defender));
    }

    @Test
    void anAllyGathersTheDefenderOnly() {
        LongBucket out = gatherFor(Faction.ALLY);

        assertTrue(contains(out, defender));
        assertFalse(contains(out, marine),
                "the company it landed beside is not something it shoots at");
        assertFalse(contains(out, ally));
    }

    /** The count query is the same filter without a buffer, so it agrees. */
    @Test
    void theCountAgreesWithTheGather() {
        for (Faction faction : Faction.values()) {
            assertEquals(gatherFor(faction).size,
                    index.countHostileCombatants(10.5f, 10.5f, RADIUS, faction, 0L),
                    "count and gather disagree for " + faction);
        }
    }

    /** A neutral has no enemies, so it gathers nobody at all. */
    @Test
    void aCivilianGathersNobody() {
        assertEquals(0, gatherFor(Faction.CIVILIAN).size);
    }

    /**
     * The nearest-first walk shares the filter, and a caller that used one and
     * not the other would see two different battles.
     */
    @Test
    void theRingWalkVisitsTheSameSetAsTheGather() {
        for (Faction faction : Faction.values()) {
            LongBucket ringed = new LongBucket();
            index.forEachHostileCombatantByRing(10.5f, 10.5f, faction,
                    new UnitSpatialIndex.RingVisitor() {
                        @Override public void accept(long id, float x, float y) {
                            ringed.add(id);
                        }
                        @Override public boolean continueAfterRing(float nearest) {
                            return true;
                        }
                    });

            LongBucket gathered = gatherFor(faction);
            assertEquals(gathered.size, ringed.size,
                    "ring walk and radius gather disagree for " + faction);
            for (int i = 0; i < gathered.size; i++) {
                assertTrue(contains(ringed, gathered.ids[i]));
            }
        }
    }

    private LongBucket gatherFor(Faction faction) {
        LongBucket out = new LongBucket();
        index.gatherHostileCombatants(10.5f, 10.5f, RADIUS, faction, out);
        return out;
    }

    private static boolean contains(LongBucket bucket, long id) {
        for (int i = 0; i < bucket.size; i++) {
            if (bucket.ids[i] == id) return true;
        }
        return false;
    }
}
