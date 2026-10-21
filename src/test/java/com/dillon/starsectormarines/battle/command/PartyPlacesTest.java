package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A party's places (`garrisonables.md`, parties, place directives and locked positions; {@link PartyPlaces}): what a
 * party's commander offers the shared planner, and what a lock orders. Two buildings, a barracks (40..55, 20..30) and
 * a far one (95..110, 40..50); no battle.
 */
class PartyPlacesTest {

    private static final int W = 120, H = 60, BLOCK = 8;
    private static final TacticalNode BARRACKS = building(40, 20, 55, 30);
    private static final TacticalNode FAR = building(95, 40, 110, 50);
    private static final PlaceTarget BARRACKS_YARD = PlaceTarget.region(47, 25, 9);

    private final GarrisonableRegistry places = GarrisonableRegistry.build(
            FightingPositionIndex.build(W, H, List.of(), (x, y) -> true), W, H, BLOCK, new int[0],
            List.of(new GarrisonableRegistry.Envelope(BARRACKS, new int[]{47, 20, 48, 20, 42, 20, 53, 20}),
                    new GarrisonableRegistry.Envelope(FAR, new int[]{100, 40, 104, 40})));
    private final int barracks = places.envelopeOf(BARRACKS);
    private final int far = places.envelopeOf(FAR);

    private static TacticalNode building(int left, int top, int right, int bottom) {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, (left + right) / 2, (top + bottom) / 2,
                left, top, right, bottom, Faction.ALLY, 60, 0);
    }

    private static CommandSquadState squad(int id, float x, float y) {
        return squad(id, x, y, 6);
    }

    private static CommandSquadState squad(int id, float x, float y, int alive) {
        return new CommandSquadState(id, Faction.ALLY, alive, x, y, (int) x, (int) y, 0, UnitRole.PATROL, false, false,
                false, null, null, null, null, null, 0, 0, 0, 0, 0, null, null, null, 0, new int[0], new int[0], new int[0],
                null, false, false, false, null, 6);
    }

    private static PartyPlaces party(PlaceDirective... directives) {
        return new PartyPlaces(Faction.ALLY, new PlaceDirectives(List.of(directives)));
    }

    @Test
    void aDutySquadGuardsOnlyThePlaceItWasPutOn() {
        PartyPlaces party = party();
        party.begin(PlaceSignals.at(0));
        party.offerDuty(squad(1, 47, 25), 47, 25);
        party.offerDuty(squad(2, 47, 34), 47, 34);
        MissionGarrisons.Offer inside = party.offer(1);
        MissionGarrisons.Offer beside = party.offer(2);
        assertTrue(MissionGarrisons.withinDuty(inside, barracks, places), "the building its post stands in");
        assertFalse(MissionGarrisons.withinDuty(beside, barracks, places), "four cells off it is not its place");
        assertFalse(MissionGarrisons.withinDuty(inside, far, places));
    }

    @Test
    void aHoldDirectiveOpensItsPlacesToDutySquadsNearAndToFreeSquads() {
        PlaceDirective hold = PlaceDirective.hold("barracks", Faction.ALLY, BARRACKS_YARD)
                .when(PlaceTrigger.flag("alarm")).allowance(2).build();
        PartyPlaces party = party(hold);
        party.begin(PlaceSignals.at(0));
        party.offerFree(squad(3, 80, 50));
        assertNull(party.offer(3), "a free squad is offered only for an active hold");
        party.begin(PlaceSignals.at(75).raise("alarm"));
        party.offerDuty(squad(2, 47, 34), 47, 34);
        party.offerDuty(squad(4, 104, 52), 104, 52);
        party.offerFree(squad(3, 80, 50));
        assertTrue(MissionGarrisons.withinDuty(party.offer(2), barracks, places), "the hold's place, near its post");
        assertFalse(MissionGarrisons.withinDuty(party.offer(4), barracks, places), "a hold far from the post is not its");
        MissionGarrisons.Offer free = party.offer(3);
        assertTrue(free.roving());
        assertTrue(MissionGarrisons.withinDuty(free, barracks, places));
        assertFalse(MissionGarrisons.withinDuty(free, far, places), "roving only within the hold's region");
        for (int id = 1; id <= 3; id++) party.own(squad(id, 0, 0));
        assertEquals(1 + 2, party.holderCap(), "the share of three, plus the hold's allowance");
    }

    @Test
    void aLockedSquadTakesItsLockAndIsNeverOffered() {
        PlaceDirective post = PlaceDirective.lock("road", Faction.ALLY, PlaceTarget.region(70, 10, 4))
                .post(70, 10).post(72, 12).radius(4).build();
        PartyPlaces party = party(post);
        party.begin(PlaceSignals.at(0));
        PartyPlaces.Locked first = party.lock(squad(1, 20, 20), 0, places);
        PartyPlaces.Locked second = party.lock(squad(2, 20, 20), 1, places);
        assertNotNull(first);
        assertEquals(AssignmentKind.DEFEND_AREA, first.order().kind());
        assertEquals(70, first.order().targetCellX());
        assertEquals(72, second.order().targetCellX(), "posts cycle by detachment");
        assertEquals(4, first.order().targetRadiusCells());
        party.offerDuty(squad(1, 20, 20), 20, 20);
        party.offerFree(squad(1, 20, 20));
        assertNull(party.offer(1), "nothing reactive is offered a locked squad");
        assertEquals(2, party.figures().get("lockedSquadPulses"));
    }

    @Test
    void aWornDetachmentIsLetGoUnlessTheLockHoldsToTheLast() {
        PlaceDirective held = PlaceDirective.lock("held", Faction.ALLY, PlaceTarget.region(70, 10, 4)).post(70, 10).build();
        PartyPlaces party = party(held);
        party.begin(PlaceSignals.at(0));
        assertNotNull(party.lock(squad(1, 20, 20, 4), 0, places), "four of six still hold");
        party.begin(PlaceSignals.at(75));
        assertNull(party.lock(squad(1, 20, 20, 3), 0, places), "half is let go");
        party.offerDuty(squad(1, 20, 20, 3), 20, 20);
        assertNotNull(party.offer(1), "a let-go squad is back on its duty");
        PartyPlaces last = party(PlaceDirective.lock("last", Faction.ALLY, PlaceTarget.region(70, 10, 4))
                .post(70, 10).toTheLast().build());
        last.begin(PlaceSignals.at(0));
        assertNotNull(last.lock(squad(1, 20, 20, 1), 0, places), "an authored hold to the end keeps even one");
    }

    @Test
    void aLockWithNoPostsOnABuildingMansIt() {
        PlaceDirective building = PlaceDirective.lock("barracks", Faction.ALLY, PlaceTarget.region(47, 25, 3))
                .when(PlaceTrigger.tickAtLeast(150)).until(PlaceTrigger.flag("unlock")).build();
        PartyPlaces party = party(building);
        party.begin(PlaceSignals.at(75));
        assertNull(party.lock(squad(1, 20, 20), 0, places), "not locked before its trigger");
        party.begin(PlaceSignals.at(150));
        PartyPlaces.Locked locked = party.lock(squad(1, 20, 20), 0, places);
        assertNotNull(locked);
        assertTrue(locked.order().isEnvelope());
        assertTrue(GarrisonableRegistry.sameBuilding(locked.order().targetNode(), BARRACKS));
        party.begin(PlaceSignals.at(225).raise("unlock"));
        assertNull(party.lock(squad(1, 20, 20), 0, places), "the mission unlocked it");
        party.offerDuty(squad(1, 47, 25), 47, 25);
        assertNotNull(party.offer(1), "back to its duty, and offered again");
    }
}
