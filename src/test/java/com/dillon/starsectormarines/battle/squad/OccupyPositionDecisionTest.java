package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which clicks become an occupy order, on a hand-built pair of trenches. */
class OccupyPositionDecisionTest {

    private static final int W = 40;
    private static final int H = 10;

    private static FightingPositionIndex twoTrenches() {
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        List<Doodad> props = new ArrayList<>();
        for (int x = 2; x <= 13; x++) props.add(new Doodad(x, 2, def));
        for (int x = 2; x <= 13; x++) props.add(new Doodad(x, 7, def));
        return FightingPositionIndex.build(W, H, props, (x, y) -> true);
    }

    @Test
    void aClickOnATrenchCellNamesThatRun() {
        FightingPositionIndex index = twoTrenches();
        assertEquals(2, index.runCount());
        assertEquals(index.runIdAt(5, 2), OccupyPositionDecision.runUnderClick(index, true, 5, 2));
        assertEquals(index.runIdAt(5, 7), OccupyPositionDecision.runUnderClick(index, true, 5, 7));
        assertTrue(OccupyPositionDecision.runUnderClick(index, true, 5, 2)
                != OccupyPositionDecision.runUnderClick(index, true, 5, 7));
    }

    @Test
    void aClickBesideTheTrenchIsAnOrdinaryMove() {
        FightingPositionIndex index = twoTrenches();
        assertEquals(OccupyPositionDecision.NONE, OccupyPositionDecision.runUnderClick(index, true, 5, 3));
        assertEquals(OccupyPositionDecision.NONE, OccupyPositionDecision.runUnderClick(index, true, 20, 5));
    }

    @Test
    void withFightingPositionsOffNothingIsOccupied() {
        assertEquals(OccupyPositionDecision.NONE,
                OccupyPositionDecision.runUnderClick(twoTrenches(), false, 5, 2));
    }

    @Test
    void aMapWithoutPositionsOrAnIndexIsNeverOccupied() {
        assertEquals(OccupyPositionDecision.NONE,
                OccupyPositionDecision.runUnderClick(FightingPositionIndex.empty(W, H), true, 5, 2));
        assertEquals(OccupyPositionDecision.NONE,
                OccupyPositionDecision.runUnderClick(null, true, 5, 2));
    }

    @Test
    void anOccupyOrderIsMarkedAndAnOrdinaryDefenceIsNot() {
        SquadMoveOrderService.ActiveDefendAreaOrder occupy =
                new SquadMoveOrderService.ActiveDefendAreaOrder(5, 2, 5, 2, 20, 1);
        SquadMoveOrderService.ActiveDefendAreaOrder plain =
                new SquadMoveOrderService.ActiveDefendAreaOrder(5, 2, 5, 2, 20);
        assertTrue(occupy.isOccupy());
        assertFalse(plain.isOccupy());
    }
}
