package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a mission frame says about itself without being told.
 *
 * <p>Asked of the registry and the pad list directly. The derivation is a pure
 * read over the simulation's public surface, so a generated battle would prove
 * nothing here that one registered compound and one berth does not.
 */
class BattleReviewAnnotationsTest {

    private static final int W = 60;
    private static final int H = 40;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode compound(TacticalNode.Kind kind) {
        // top < bottom, which is the row order a TacticalNode bbox arrives in.
        return new TacticalNode(kind, 20, 30, 16, 26, 24, 34,
                Faction.DEFENDER, 90, 4);
    }

    @Test
    void oneCompoundAndOneBerthReadAsAKeepAnLzAndTheWayBetweenThem() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(compound(TacticalNode.Kind.COMMAND_POST));
        sim.setMarineLandingPads(List.of(
                LandingPad.conquest(8, 8, LandingPad.Approach.SOUTH)));

        ReviewAnnotations marks = BattleReviewAnnotations.forBattle(sim);
        assertEquals(3, marks.size(), "expected one compound box, one LZ box, one arrow");

        ReviewAnnotation.Box keep = boxWithStyle(marks, ReviewStyle.KEEP);
        assertEquals(16, keep.left());
        assertEquals(24, keep.right());
        assertEquals(26, keep.bottom(), "the node's lower row is the box's bottom");
        assertEquals(34, keep.top());
        assertTrue(keep.text().startsWith("KEEP"), "read: " + keep.text());
        assertTrue(keep.text().contains("held"), "a box says who holds it: " + keep.text());

        ReviewAnnotation.Box lz = boxWithStyle(marks, ReviewStyle.LANDING);
        assertEquals("LZ", lz.text());
        assertEquals(6, lz.left());
        assertEquals(10, lz.right());
        assertEquals(6, lz.bottom());
        assertEquals(10, lz.top());

        ReviewAnnotation.Arrow approach = onlyArrow(marks);
        assertEquals("approach", approach.text());
        assertEquals(ReviewStyle.APPROACH, approach.style());
        assertEquals(8.5f, approach.fromX(), 0.001f);
        assertEquals(8.5f, approach.fromY(), 0.001f);
        assertEquals(20.5f, approach.toX(), 0.001f, "the arrow ends on the keep's anchor");
        assertEquals(30.5f, approach.toY(), 0.001f);
    }

    @Test
    void aCompoundThatIsNotTheCommandPostIsAnObjective() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(compound(TacticalNode.Kind.ARMORY));

        ReviewAnnotations marks = BattleReviewAnnotations.forBattle(sim);
        assertEquals(1, marks.size(), "no berths means no LZ and no approach");
        assertEquals(ReviewStyle.OBJECTIVE,
                boxWithStyle(marks, ReviewStyle.OBJECTIVE).style());
    }

    @Test
    void aBerthSeveralArrivalsShareIsDrawnOnce() {
        BattleSimulation sim = openSim();
        LandingPad shared = LandingPad.conquest(8, 8, LandingPad.Approach.SOUTH);
        sim.setMarineLandingPads(List.of(shared, shared,
                LandingPad.conquest(20, 8, LandingPad.Approach.SOUTH)));

        ReviewAnnotations marks = BattleReviewAnnotations.forBattle(sim);
        assertEquals(2, marks.marks().stream()
                .filter(mark -> mark.style() == ReviewStyle.LANDING).count());
    }

    @Test
    void thePairedBerthsOfOneArrivalAreaAreOneLandingGround() {
        BattleSimulation sim = openSim();
        // The authored Conquest shape: two five-cell berths, centres eight
        // apart, three cells of open ground between their footprints.
        sim.setMarineLandingPads(List.of(
                LandingPad.conquest(8, 8, LandingPad.Approach.SOUTH),
                LandingPad.conquest(8, 16, LandingPad.Approach.SOUTH)));

        ReviewAnnotations marks = BattleReviewAnnotations.forBattle(sim);
        assertEquals(1, marks.size(), "two berths of one area are one place: " + marks.marks());
        ReviewAnnotation.Box ground = boxWithStyle(marks, ReviewStyle.LANDING);
        assertEquals(6, ground.bottom());
        assertEquals(18, ground.top());
        assertEquals(6, ground.left());
        assertEquals(10, ground.right());
    }

    @Test
    void aBattleWithNothingRegisteredCarriesNoMarks() {
        assertTrue(BattleReviewAnnotations.forBattle(openSim()).isEmpty());
        assertTrue(BattleReviewAnnotations.forBattle(null).isEmpty());
    }

    private static ReviewAnnotation.Box boxWithStyle(ReviewAnnotations marks,
                                                     ReviewStyle style) {
        return marks.marks().stream()
                .filter(ReviewAnnotation.Box.class::isInstance)
                .map(ReviewAnnotation.Box.class::cast)
                .filter(box -> box.style() == style)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + style + " box in " + marks.marks()));
    }

    private static ReviewAnnotation.Arrow onlyArrow(ReviewAnnotations marks) {
        return marks.marks().stream()
                .filter(ReviewAnnotation.Arrow.class::isInstance)
                .map(ReviewAnnotation.Arrow.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no arrow in " + marks.marks()));
    }
}
