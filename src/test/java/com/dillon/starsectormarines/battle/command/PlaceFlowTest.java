package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flow tier without an axis ({@link PlaceFlow}): one barracks (x 40..55, y 20..30) and a hand-built pressure area
 * to its south. Reach is free and every cell may be stood on. Each test is a plan or a handful.
 */
class PlaceFlowTest {

    private static final int W = 120, H = 60, BLOCK = 8;
    private static final TacticalNode BARRACKS = new TacticalNode(TacticalNode.Kind.BARRACKS, 47, 25, 40, 20, 55, 30,
            Faction.DEFENDER, 60, 0);

    private final GarrisonableRegistry places = GarrisonableRegistry.build(
            FightingPositionIndex.build(W, H, List.of(), (x, y) -> true), W, H, BLOCK, new int[0],
            List.of(new GarrisonableRegistry.Envelope(BARRACKS, new int[]{47, 20, 48, 20, 42, 20, 53, 20})));
    private final int barracks = places.envelopeOf(BARRACKS);
    private final PlaceFlow flow = new PlaceFlow();

    private static final List<PlaceFlow.Candidate> FOUR = List.of(new PlaceFlow.Candidate(1, 100, 50),
            new PlaceFlow.Candidate(2, 105, 50), new PlaceFlow.Candidate(3, 110, 50), new PlaceFlow.Candidate(4, 115, 50));

    private List<Integer> stage(List<GarrisonFlow.Area> areas, List<PlaceFlow.Candidate> free, boolean manned) {
        return flow.stage(areas, new int[]{barracks}, new int[]{barracks}, place -> manned, places,
                new int[areas.size()], free, free.size(), (sx, sy, x, y) -> true, (x, y) -> true, (x, y) -> true);
    }

    /** An attack {@code gap} cells south of the barracks' centre, heavy enough to ask for {@code weight / 40} squads. */
    private GarrisonFlow.Area attack(int gap, float weight) {
        return new GarrisonFlow.Area(places.centreX(barracks), places.centreY(barracks) + gap, weight, 2, 0);
    }

    @Test
    void anAreaFarFromItsPlacesGetsOneSquadAtThePlacesAndOneForwardInTheStreet() {
        List<Integer> sent = stage(List.of(attack(20, 50f)), FOUR, false);

        assertEquals(List.of(1, 2), sent, "the nearest two, within the per-plan bound");
        PlaceFlow.Post back = flow.post(1);
        PlaceFlow.Post forward = flow.post(2);
        assertFalse(back.forward, "the first waits at the places, to man them");
        assertEquals(places.centreY(barracks), back.order.targetCellY(), "at the barracks, twenty cells from the attack");
        assertTrue(forward.forward, "the second meets the attack");
        assertEquals(places.centreY(barracks) + 20 - GarrisonFlow.FORWARD_STANDOFF_CELLS, forward.order.targetCellY(),
                "a standoff out from the attack toward the places");
        assertEquals(PlaceFlow.FORWARD_RADIUS_CELLS, forward.order.targetRadiusCells());
        assertEquals(AssignmentKind.DEFEND_AREA, forward.order.kind());
        assertEquals(1, flow.stageOrders);
        assertEquals(1, flow.forwardOrders);
    }

    @Test
    void withForwardSwitchedOffEverySquadWaitsBack() {
        System.setProperty(PlaceFlow.FORWARD_PROPERTY, "false");
        try {
            stage(List.of(attack(20, 50f)), FOUR, false);
        } finally {
            System.clearProperty(PlaceFlow.FORWARD_PROPERTY);
        }
        assertFalse(flow.post(1).forward);
        assertFalse(flow.post(2).forward);
    }

    @Test
    void anAttackAlreadyAtThePlacesLeavesNoStreetToMeetItIn() {
        stage(List.of(attack(7, 50f)), FOUR, false);
        assertFalse(flow.post(1).forward);
        assertFalse(flow.post(2).forward, "nine cells or fewer: both wait back");
    }

    @Test
    void aLoneReserveStaysAndAFaintAreaStagesNobody() {
        assertTrue(stage(List.of(attack(20, 50f)), List.of(new PlaceFlow.Candidate(1, 100, 50)), false).isEmpty(),
                "half a pool of one is nobody");
        assertEquals(1, PlaceFlow.stagedCap(2), "a reserve of two may stand one nearer the pressure");
        assertTrue(stage(List.of(attack(20, 1.5f)), FOUR, false).isEmpty(), "below the start weight");
    }

    @Test
    void aSquadAlreadyNearTheStagingCellIsLocalSoNobodyMoves() {
        List<PlaceFlow.Candidate> near = List.of(new PlaceFlow.Candidate(1, 47, 26), new PlaceFlow.Candidate(2, 100, 50),
                new PlaceFlow.Candidate(3, 110, 50));
        // Weight 30 with the barracks manned asks for one squad, and one already stands there.
        assertTrue(stage(List.of(attack(20, 30f)), near, true).isEmpty());
    }

    @Test
    void aQuietAreaGivesItsSquadsBackAfterTheDwell() {
        stage(List.of(attack(20, 50f)), FOUR, false);
        for (int i = 1; i < PlaceFlow.COOL_PLANS; i++) {
            assertTrue(flow.cool(List.of()).isEmpty(), "still waiting at plan " + i);
        }
        assertEquals(List.of(1, 2), flow.cool(List.of()));
        assertTrue(flow.posts().isEmpty());
        assertEquals(2, flow.releases);
    }

    @Test
    void anAreaThatPressesAgainResetsTheDwell() {
        GarrisonFlow.Area area = attack(20, 50f);
        stage(List.of(area), FOUR, false);
        for (int i = 1; i < PlaceFlow.COOL_PLANS; i++) flow.cool(List.of());
        flow.cool(List.of(area));
        assertTrue(flow.cool(List.of()).isEmpty(), "pressed again: the dwell starts over");
        assertNotNull(flow.post(1));
    }
}
