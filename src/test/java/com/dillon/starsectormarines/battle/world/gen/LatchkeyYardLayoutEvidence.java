package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence, not a test: it generates the whole Latchkey map to ask its questions. Run with
 * {@code gradlew.bat generatedSpaceEvidence}.
 *
 * <p>The Yard's Owners rests on two geometric claims the generator could silently lose: the tooling
 * hall has exactly two ways in, so the counterattack's two groups really do arrive at different doors;
 * and each group's ingress leads to the door it is meant to threaten. A stray doorway or a gap in the
 * yard wall turns the choice between holding approaches and escorting the inspection back into one
 * open-floor fight.
 */
@Tag("generated-space-evidence")
class LatchkeyYardLayoutEvidence {
    private static final long[] SEEDS = {15303, 16203, 16204};

    @Test
    void toolingHallIsEnteredOnlyByItsFreightDoorsAndTheGallery() {
        for (long seed : SEEDS) {
            var map = LatchkeyYard.generate(seed, TargetProfile.NEUTRAL);
            var hall = LatchkeyYard.SITES.get(1);
            Set<String> doors = new HashSet<>();
            for (int y = hall.bottom(); y <= hall.top(); y++) for (int x = hall.left(); x <= hall.right(); x++) {
                boolean ring = x == hall.left() || x == hall.right() || y == hall.bottom() || y == hall.top();
                if (ring && map.grid.isDoorway(x, y)) doors.add(x + "," + y);
            }
            assertEquals(Set.of("92,86", "93,86", "94,86", "88,56"), doors, "seed " + seed);
        }
    }

    @Test
    void eachCounterattackIngressReachesItsOwnDoor() {
        var map = LatchkeyYard.generate(15303, TargetProfile.NEUTRAL);
        var hall = LatchkeyYard.SITES.get(1);
        var entrance = LatchkeyYard.SITES.get(2);
        var cinder = LatchkeyYard.RESERVE_INGRESS.get(0);
        var south = LatchkeyYard.RESERVE_INGRESS.get(1);
        int[] toHall = GridPathfinder.findPath(map.grid, cinder.x(), cinder.y(), hall.x(), hall.y());
        int[] toEntrance = GridPathfinder.findPath(map.grid, south.x(), south.y(), entrance.x(), entrance.y());
        assertFalse(Paths.isEmpty(toHall));
        assertFalse(Paths.isEmpty(toEntrance));
        assertTrue(passes(toHall, List.of(new int[]{92, 86}, new int[]{93, 86}, new int[]{94, 86})),
                "the Cinder Steps group enters the hall by its freight doors");
        assertTrue(passes(toHall, List.of(new int[]{LatchkeyYard.CINDER_GATE.x(), LatchkeyYard.CINDER_GATE.y() - 3},
                        new int[]{LatchkeyYard.CINDER_GATE.x(), LatchkeyYard.CINDER_GATE.y() + 3}), true),
                "and reaches the yard through the Cinder Steps gate");
        assertTrue(passes(toEntrance, List.of(new int[]{LatchkeyYard.VESTIBULE.x(), LatchkeyYard.VESTIBULE.y()})),
                "the south-road group comes in through the pressure vestibule");
    }

    @Test
    void calibrationMachineStandsInTheHall() {
        var map = LatchkeyYard.generate(15303, TargetProfile.NEUTRAL);
        assertTrue(map.doodads.stream().anyMatch(prop -> prop.cellX == LatchkeyYard.TOOL_X && prop.cellY == LatchkeyYard.TOOL_Y
                && LatchkeyYard.TOOL_ID.equals(prop.definitionId())));
        assertTrue(map.topology.hasBuildingKindHint(LatchkeyYard.TOOL_X, LatchkeyYard.TOOL_Y));
        assertFalse(map.grid.isWalkable(LatchkeyYard.TOOL_X, LatchkeyYard.TOOL_Y));
    }

    private static boolean passes(int[] path, List<int[]> cells) { return passes(path, cells, false); }

    /** Whether the path crosses one of {@code cells}, or with {@code band} the vertical span between two of them. */
    private static boolean passes(int[] path, List<int[]> cells, boolean band) {
        for (int i = 0; i < Paths.cellCount(path); i++) {
            int x = Paths.cellX(path, i), y = Paths.cellY(path, i);
            if (band) {
                if (x == cells.get(0)[0] && y >= cells.get(0)[1] && y <= cells.get(1)[1]) return true;
            } else for (int[] cell : cells) if (cell[0] == x && cell[1] == y) return true;
        }
        return false;
    }
}
