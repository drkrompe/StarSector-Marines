package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldPlacesTest {

    private static final PointOfInterest DEPOT =
            new PointOfInterest(PointOfInterest.Kind.DEPOT, 10, 20, 18, 27, 14, 28, 14, 24);
    private static final PointOfInterest BERTH =
            new PointOfInterest(PointOfInterest.Kind.LANDING_SITE, 40, 5, 60, 15, 50, 10);

    @Test
    void aBuildingBecomesAHeldPlaceOnItsOwnFootprint() {
        List<TacticalNode> places = HeldPlaces.fromBuildings(List.of(DEPOT), poi -> Faction.ALLY);
        assertEquals(1, places.size());
        TacticalNode place = places.get(0);
        assertEquals(TacticalNode.Kind.INNER_POSITION, place.kind);
        assertEquals(10, place.left);
        assertEquals(20, place.top);
        assertEquals(18, place.right);
        assertEquals(27, place.bottom);
        assertEquals(14, place.anchorX, "entered at the interior anchor, not the doorstep");
        assertEquals(24, place.anchorY);
        assertEquals(Faction.ALLY, place.defaultGuard);
    }

    @Test
    void aHeldPlaceAsksForNoGarrisonAndIsNoObjective() {
        TacticalNode place = HeldPlaces.fromBuildings(List.of(DEPOT), poi -> Faction.DEFENDER).get(0);
        assertEquals(0, place.garrisonSize, "manned in answer to threat or by order, never filled by default");
        assertFalse(CompoundService.isCompound(place.kind), "publishing a building adds no capture objective");
        assertTrue(HeldPlaces.isUnposted(place));
    }

    @Test
    void aLandingSiteIsOpenGroundNotABuilding() {
        assertTrue(HeldPlaces.fromBuildings(List.of(BERTH), poi -> Faction.ALLY).isEmpty());
    }
}
