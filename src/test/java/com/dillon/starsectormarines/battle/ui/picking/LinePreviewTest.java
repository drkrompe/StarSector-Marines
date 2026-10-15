package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay.LineMark;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay.PlaceMark;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rings drawn under a drag are where the marines are sent once it is released. */
class LinePreviewTest {

    private static final int W = 80;
    private static final int H = 80;
    private static final int TRENCH_Y = 40;

    private boolean previouslyEnabled;
    private BattleSimulation sim;
    private int squadId;
    private Selection selection;

    @BeforeEach
    void setUp() {
        previouslyEnabled = DigInSystem.isEnabled();
        DigInSystem.setEnabledForEvidence(true);
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 10; y <= 70; y++) {
            for (int x = 10; x <= 70; x++) grid.setWalkableFloor(x, y);
        }
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 8; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, 34 + i, 20).squad(squadId));
        }
        // The far side keeps the battle undecided, or the simulation never advances a tick.
        sim.setMissionCompletionEnabled(false);
        int enemy = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        sim.spawn(new EntitySpec("far", Faction.DEFENDER, UnitType.MILITIA, 70, 70).squad(enemy));
        selection = new Selection();
        selection.selectSquad(squadId);
    }

    @AfterEach
    void tearDown() {
        DigInSystem.setEnabledForEvidence(previouslyEnabled);
    }

    private void trench(int fromX, int toX) {
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = fromX; x <= toX; x++) sim.addDoodad(new Doodad(x, TRENCH_Y, def));
    }

    @Test
    void theRingsOnTwoTrenchesAreTheCellsTheOrderClaims() {
        trench(18, 29);
        trench(46, 57);
        sim.advance(1f / 30f);

        List<LineMark> marks = SelectionOrders.previewMarks(sim, selection, 16, TRENCH_Y, 59, TRENCH_Y);
        assertEquals(1, marks.size());
        List<PlaceMark> places = marks.get(0).places();
        assertEquals(8, places.size(), "a ring for every marine");
        Map<Long, int[]> promised = new HashMap<>();
        for (PlaceMark place : places) {
            assertTrue(place.position(), "two trenches have room for the whole squad");
            promised.put(place.member(), new int[]{place.x(), place.y()});
        }

        SelectionOrders.requestLine(sim, selection, 16, TRENCH_Y, 59, TRENCH_Y);
        for (int i = 0; i < 300 && sim.fightingPositionClaims().isEmpty(); i++) sim.advance(1f / 30f);

        for (Map.Entry<Long, int[]> entry : promised.entrySet()) {
            int[] claimed = sim.fightingPositionClaims().cellOf(entry.getKey());
            assertNotNull(claimed, "every marine with a ring is given a position");
            assertArrayEquals(entry.getValue(), claimed, "and it is the cell his ring was drawn on");
        }
    }

    @Test
    void withNothingToHoldEveryRingIsOpenGroundAlongTheLine() {
        sim.advance(1f / 30f);

        List<PlaceMark> places = SelectionOrders.previewMarks(sim, selection, 20, TRENCH_Y, 60, TRENCH_Y)
                .get(0).places();

        assertEquals(8, places.size());
        int lastX = Integer.MIN_VALUE;
        for (PlaceMark place : places) {
            assertFalse(place.position());
            assertTrue(Math.abs(place.y() - TRENCH_Y) <= 3, "on the line's band");
            assertTrue(place.x() > lastX, "spread along it in member order");
            lastX = place.x();
        }
    }
}
