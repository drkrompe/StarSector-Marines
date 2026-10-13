package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand-built props on a 40x10 field: which cells are fighting positions, and which runs qualify. */
class FightingPositionIndexTest {

    private static final int W = 40;
    private static final int H = 10;

    private final List<Doodad> props = new ArrayList<>();
    private final boolean[] blocked = new boolean[W * H];

    private static Doodad prop(int x, int y, FightingPositionRole role) {
        DoodadDef def = new DoodadDef("doodad.test." + role, "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, role);
        return new Doodad(x, y, def);
    }

    private void line(int fromX, int toX, int y, FightingPositionRole role) {
        for (int x = fromX; x <= toX; x++) props.add(prop(x, y, role));
    }

    private FightingPositionIndex build() {
        return FightingPositionIndex.build(W, H, props, (x, y) -> !blocked[y * W + x]);
    }

    @Test
    void aStraightTrenchOfTwelveIsOneRun() {
        line(5, 16, 4, FightingPositionRole.TRENCH);
        FightingPositionIndex index = build();

        assertEquals(1, index.runCount());
        assertEquals(12, index.runSize(0));
        for (int x = 5; x <= 16; x++) {
            assertTrue(index.isFightingPositionCell(x, 4));
            assertEquals(0, index.runIdAt(x, 4));
        }
        assertFalse(index.isFightingPositionCell(4, 4));
        assertFalse(index.isFightingPositionCell(10, 5), "trench cells are the cells, not their neighbours");
    }

    @Test
    void aSandbagLineIsMannedFromBothSides() {
        line(5, 10, 4, FightingPositionRole.PARAPET);
        FightingPositionIndex index = build();

        assertEquals(1, index.runCount());
        assertEquals(20, index.runSize(0), "the line, a row either side, and the ground at each end of it");
        for (int x = 5; x <= 10; x++) {
            assertEquals(0, index.runIdAt(x, 3));
            assertEquals(0, index.runIdAt(x, 4));
            assertEquals(0, index.runIdAt(x, 5));
        }
        assertEquals(0, index.runIdAt(4, 4), "the cell off each end is a cardinal neighbour of the end bag");
        assertEquals(0, index.runIdAt(11, 4));
        assertEquals(-1, index.runIdAt(3, 4), "but no further");
        assertEquals(-1, index.runIdAt(4, 3), "and not diagonally");
    }

    @Test
    void aRunOfElevenIsCoverButNotAPosition() {
        line(5, 15, 4, FightingPositionRole.TRENCH);
        FightingPositionIndex index = build();

        assertEquals(0, index.runCount());
        assertFalse(index.isFightingPositionCell(8, 4));
        assertEquals(-1, index.runIdAt(8, 4));
    }

    @Test
    void destroyingTheParapetThatBridgesTwoHalvesDissolvesHalvesUnderTheFloor() {
        line(2, 9, 2, FightingPositionRole.TRENCH);          // 8 cells
        line(10, 10, 2, FightingPositionRole.PARAPET);       // bridge: (10,1) (10,2) (10,3)
        line(11, 20, 2, FightingPositionRole.TRENCH);        // 10 cells
        FightingPositionIndex joined = build();
        assertEquals(1, joined.runCount());
        assertEquals(8 + 3 + 10, joined.runSize(0));

        props.removeIf(d -> d.cellX == 10);
        FightingPositionIndex split = build();
        assertEquals(0, split.runCount(), "8 and 10 cells are both under the floor");
        assertFalse(split.isFightingPositionCell(5, 2));
    }

    @Test
    void aSplitKeepsTheHalvesLongEnoughToQualify() {
        line(2, 14, 2, FightingPositionRole.TRENCH);         // 13 cells
        line(15, 15, 2, FightingPositionRole.PARAPET);
        line(16, 28, 2, FightingPositionRole.TRENCH);        // 13 cells
        assertEquals(1, build().runCount());

        props.removeIf(d -> d.cellX == 15);
        FightingPositionIndex split = build();
        assertEquals(2, split.runCount());
        assertEquals(13, split.runSize(0));
        assertEquals(13, split.runSize(1));
        assertEquals(0, split.runIdAt(2, 2), "ids follow the row-major first cell");
        assertEquals(1, split.runIdAt(28, 2));
        assertEquals(-1, split.runIdAt(15, 2));
    }

    @Test
    void nonWalkableCellsAreNeverPositions() {
        line(5, 10, 4, FightingPositionRole.PARAPET);
        blocked[3 * W + 7] = true;
        blocked[5 * W + 8] = true;
        FightingPositionIndex index = build();

        assertEquals(20 - 2, index.runSize(0));
        assertFalse(index.isFightingPositionCell(7, 3));
        assertFalse(index.isFightingPositionCell(8, 5));
        assertTrue(index.isFightingPositionCell(7, 5));
    }

    @Test
    void aBlockedPropCellIsNotStoodOn() {
        line(5, 17, 4, FightingPositionRole.TRENCH);
        blocked[4 * W + 11] = true;
        FightingPositionIndex index = build();

        // 6 cells and 6 cells: the blocked cell splits the line below the floor.
        assertEquals(0, index.runCount());
    }

    @Test
    void untaggedDoodadsAreIgnored() {
        line(5, 20, 4, FightingPositionRole.NONE);
        FightingPositionIndex index = build();

        assertEquals(0, index.runCount());
        assertFalse(index.isFightingPositionCell(8, 4));
    }

    @Test
    void outOfBoundsQueriesAreSafe() {
        FightingPositionIndex index = build();
        assertEquals(-1, index.runIdAt(-1, 0));
        assertEquals(-1, index.runIdAt(W, 0));
        assertFalse(index.isFightingPositionCell(0, H));
    }

    @Test
    void runCellsEnumerateTheRun() {
        line(5, 16, 4, FightingPositionRole.TRENCH);
        FightingPositionIndex index = build();
        int sumX = 0;
        for (int i = 0; i < index.runSize(0); i++) {
            assertEquals(4, index.runCellY(0, i));
            sumX += index.runCellX(0, i);
        }
        assertEquals((5 + 16) * 12 / 2, sumX);
    }

    @Test
    void propsOutsideTheMapAreSkippedNotFatal() {
        props.add(prop(-3, 4, FightingPositionRole.PARAPET));
        props.add(prop(W + 2, 4, FightingPositionRole.TRENCH));
        assertEquals(0, build().runCount());
    }
}
