package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.marine.MechCompartment.Placement;
import com.dillon.starsectormarines.marine.MechCompartment.Spot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A compartment part keeps its place: taking one out moves nothing else, and a drop goes in its bay. */
class CompartmentFitTest {

    private static final MechInternal VENT = MechInternal.AUXILIARY_VENT_ARRAY;
    private static final MechInternal LOADER = MechInternal.STANDARD_LOADER;

    /** Two 2x2 bays, as a Heavy hull's and the Bulwark's. */
    private static final List<SocketDef> DUAL = List.of(bay(SocketId.AUXILIARY), bay(SocketId.AUXILIARY_2));

    private static SocketDef bay(SocketId id) {
        return new SocketDef(id, SocketType.AUXILIARY, 2, 2, 0f, 0f, 0f, 1f, 1f, 1f, false);
    }

    @Test
    void takingAPartOutOfOneBayLeavesTheOtherBaysPartsWhereTheySit() {
        CompartmentFit fit = new CompartmentFit();
        fit.add(DUAL, LOADER, Spot.in(SocketId.AUXILIARY));
        fit.add(DUAL, VENT, Spot.in(SocketId.AUXILIARY));
        fit.add(DUAL, VENT, Spot.in(SocketId.AUXILIARY_2));
        Placement right = MechCompartment.in(fit.placements(DUAL), SocketId.AUXILIARY_2).get(0);
        Placement leftVent = MechCompartment.in(fit.placements(DUAL), SocketId.AUXILIARY).stream()
                .filter(p -> p.part() == VENT).findFirst().orElseThrow();

        assertTrue(fit.remove(DUAL, VENT, leftVent.spot()));

        List<Placement> after = fit.placements(DUAL);
        assertEquals(List.of(right), MechCompartment.in(after, SocketId.AUXILIARY_2),
                "the right bay's vent is not pulled into the room the left one left");
        assertEquals(List.of(LOADER), MechCompartment.in(after, SocketId.AUXILIARY).stream().map(Placement::part).toList());
    }

    @Test
    void aPartGoesInTheBayItIsDroppedOnAndElsewhereOnlyWhenThatBayIsFull() {
        CompartmentFit fit = new CompartmentFit();
        fit.add(DUAL, VENT, Spot.in(SocketId.AUXILIARY_2));
        assertEquals(SocketId.AUXILIARY_2, fit.placements(DUAL).get(0).grid(), "the left bay had room, but it went right");

        fit.add(DUAL, VENT, Spot.in(SocketId.AUXILIARY_2));
        fit.add(DUAL, VENT, Spot.in(SocketId.AUXILIARY_2));
        assertEquals(2, MechCompartment.in(fit.placements(DUAL), SocketId.AUXILIARY_2).size());
        assertEquals(1, MechCompartment.in(fit.placements(DUAL), SocketId.AUXILIARY).size(), "a full bay sends it on");
    }

    @Test
    void aCompartmentSavedWithoutPlacesKeepsTheArrangementItShowedOnceSettled() {
        CompartmentFit fit = CompartmentFit.of(List.of(LOADER.id, VENT.id, VENT.id));
        List<Placement> shown = fit.placements(DUAL);
        fit.settle(DUAL);
        assertEquals(shown, fit.placements(DUAL));
        Placement first = shown.get(0);
        assertTrue(fit.remove(DUAL, first.part(), first.spot()));
        assertEquals(shown.subList(1, 3), fit.placements(DUAL), "and from then on nothing moves when a part comes out");
    }
}
