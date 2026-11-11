package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The autoplant's assembly bays ({@link AssemblyBays}): craft put together from their sections inside the assembly
 * hall, along its wall toward dispatch, noses toward dispatch's end. Planned on a bare hall floor; no map is generated.
 */
class AssemblyBaysTest {

    /** A hall floor as the works builds it, dispatch beyond its north-east corner. */
    private static final int X0 = 10, Y0 = 20, X1 = X0 + LightIndustryWorks.ASSEMBLY_WIDTH - 1,
            Y1 = Y0 + LightIndustryWorks.ASSEMBLY_DEPTH - 1;

    private static AssemblyBays.Plan plan(AssemblyBays.Model m) {
        return AssemblyBays.plan(X0, Y0, X1, Y1, X1 + 20, Y1 + 20, m, 4);
    }

    /** A craft is mated from its sections before it is fitted out; the last bay holds a finished craft. */
    @Test
    void craftAreMatedThenFittedThenFinished() {
        assertEquals(List.of(AssemblyBays.Stage.FITTING), AssemblyBays.stages(1));
        assertEquals(List.of(AssemblyBays.Stage.MATING, AssemblyBays.Stage.FITTING), AssemblyBays.stages(2));
        assertEquals(List.of(AssemblyBays.Stage.MATING, AssemblyBays.Stage.FITTING, AssemblyBays.Stage.FINISHED),
                AssemblyBays.stages(3));
    }

    /** Every model's bays lie inside the hall, in the band against the wall toward dispatch, and never overlap. */
    @Test
    void theBaysLieInsideTheHallAlongItsWallTowardDispatch() {
        for (AssemblyBays.Model m : AssemblyBays.MODELS) {
            AssemblyBays.Plan plan = plan(m);
            assertNotNull(plan, m.name() + " fits the hall");
            assertTrue(plan.pieces().size() >= 3, m.name() + ": more than one craft in the hall");
            assertEquals(Y1 - AssemblyBays.AISLE, plan.band()[3], "against the north wall, an aisle off it");
            for (AssemblyBays.Piece p : plan.pieces()) {
                assertTrue(p.x() >= X0 && p.x() + p.fx() - 1 <= X1 && p.y() >= plan.band()[1]
                        && p.y() + p.fy() - 1 <= plan.band()[3], p + " lies in the band");
                assertEquals(3, p.turns(), "noses east, toward dispatch's end");
                for (AssemblyBays.Piece q : plan.pieces()) {
                    if (p == q) continue;
                    assertFalse(p.x() < q.x() + q.fx() && q.x() < p.x() + p.fx(), p + " overlaps " + q);
                }
            }
        }
    }

    /** A mating craft's sections stand apart with the gap still open; the gantry is over the nose, not the gap. */
    @Test
    void aMatingCraftsSectionsStandApart() {
        AssemblyBays.Model m = AssemblyBays.MODELS.get(0);
        List<AssemblyBays.Piece> pieces = plan(m).pieces();
        AssemblyBays.Piece aft = pieces.get(0), nose = pieces.get(1);
        assertEquals(AssemblyBays.Stage.MATING, aft.stage());
        assertEquals(m.aftSection(), aft.art());
        assertEquals(m.noseSection(), nose.art());
        assertEquals(AssemblyBays.JOIN_GAP, nose.x() - (aft.x() + aft.fx()), "the gap between the sections");
        assertTrue(nose.gantry() >= nose.x() && nose.gantry() + 1 < nose.x() + nose.fx(), "the gantry is over the nose");
        AssemblyBays.Piece last = pieces.get(pieces.size() - 1);
        assertEquals(AssemblyBays.Stage.FINISHED, last.stage());
        assertEquals(-1, last.gantry(), "a finished craft has no gantry over it");
        assertTrue(last.x() > aft.x(), "the finished craft is nearest dispatch");
    }

    /** A hall too shallow for a craft and its aisles holds no bays. */
    @Test
    void aShallowHallHoldsNoBays() {
        assertNull(AssemblyBays.plan(0, 0, 63, 12, 100, 100, AssemblyBays.MODELS.get(1), 4));
    }

    /** A cell of a craft's footprint corner is open floor; its middle is hull. */
    @Test
    void onlyTheHullClosesItsCells() {
        for (AssemblyBays.Piece p : plan(AssemblyBays.MODELS.get(1)).pieces()) {
            assertFalse(AssemblyBays.underHull(p, p.x(), p.y()));
            assertTrue(AssemblyBays.underHull(p, p.x() + p.fx() / 2, p.y() + p.fy() / 2));
        }
    }
}
