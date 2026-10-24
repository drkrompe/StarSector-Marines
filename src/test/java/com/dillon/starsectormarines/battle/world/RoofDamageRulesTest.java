package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A roof keeps what hit it ({@link RoofDamage}): how a blow opens it, and how a second blow is kept. */
class RoofDamageRulesTest {

    private static final int WALL_HP = 100;

    /** A 5x3-floor room (x 2..6, y 2..4, building 1) walled in on a 9x7 map. */
    private static final class Room implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(9, 7);
        final CellTopology topology = new CellTopology(9, 7);
        final NavigationService navigation;
        final MapEditor editor;
        int caveIns;

        Room() {
            for (int y = 0; y < 7; y++) for (int x = 0; x < 9; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            for (int y = 1; y <= 5; y++) for (int x = 1; x <= 7; x++) {
                if (x == 1 || x == 7 || y == 1 || y == 5) {
                    grid.setWalkable(x, y, false);
                    grid.setWallHp(x, y, WALL_HP);
                    topology.setWall(x, y, true);
                } else {
                    topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
                    topology.setBuildingId(x, y, 1);
                }
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation);
            editor.setRoofCollapseSink((x, y) -> caveIns++);
        }

        @Override public void close() { navigation.close(); }
    }

    @Test
    void aSecondBlowKeepsTheWorseDamage() {
        assertEquals(RoofDamage.COLLAPSED, RoofDamage.HOLED.worse(RoofDamage.COLLAPSED));
        assertEquals(RoofDamage.BURNT, RoofDamage.BURNT.worse(RoofDamage.HOLED));
        assertEquals(RoofDamage.BURNT, RoofDamage.COLLAPSED.worse(RoofDamage.BURNT));
        assertFalse(RoofDamage.INTACT.isOpen());
        assertTrue(RoofDamage.HOLED.isOpen());
    }

    @Test
    void aHeavyBlastBringsItsCoreDownAndHolesItsEdgeWhileALightOneOnlyHoles() {
        float bomb = 3.6f;
        assertEquals(RoofDamage.COLLAPSED, RoofDamage.ofBlast(bomb, 0.5f));
        assertEquals(RoofDamage.HOLED, RoofDamage.ofBlast(bomb, 3f));
        float mortar = 2.8f;
        assertEquals(RoofDamage.HOLED, RoofDamage.ofBlast(mortar, 0f));
    }

    @Test
    void aHoledRoofThatThenBurnsIsBurntThroughAndCavesInOnce() {
        try (Room room = new Room()) {
            room.editor.destroyRoof(3, 3, RoofDamage.HOLED);
            assertTrue(room.topology.isRoofDestroyed(3, 3), "a hole opens the room to the sky");
            assertEquals(RoofDamage.HOLED, room.topology.getRoofDamage(3, 3));

            room.editor.destroyRoof(3, 3, RoofDamage.BURNT);
            assertEquals(RoofDamage.BURNT, room.topology.getRoofDamage(3, 3));
            room.editor.destroyRoof(3, 3, RoofDamage.HOLED);
            assertEquals(RoofDamage.BURNT, room.topology.getRoofDamage(3, 3), "a later shell does not unburn it");
            assertEquals(1, room.caveIns, "the cave-in debris falls once, when the roof first opens");
        }
    }

    @Test
    void aFallenWallBringsTheRoofBesideItDown() {
        try (Room room = new Room()) {
            room.editor.destroyRoof(3, 4, RoofDamage.HOLED);
            assertTrue(room.editor.damageWall(3, 5, WALL_HP * 10));
            assertEquals(RoofDamage.COLLAPSED, room.topology.getRoofDamage(3, 4), "worse than the hole it had");
            assertTrue(room.topology.isRoofDestroyed(3, 4));
            assertEquals(RoofDamage.INTACT, room.topology.getRoofDamage(3, 3), "only the roof the wall carried");
        }
    }

    @Test
    void theEaveOverAWallTakesDamageButHasNoShieldToLose() {
        try (Room room = new Room()) {
            room.editor.destroyRoof(3, 5, RoofDamage.BURNT);
            assertEquals(RoofDamage.BURNT, room.topology.getRoofDamage(3, 5));
            assertFalse(room.topology.isRoofDestroyed(3, 5), "a wall is not a roofed room");
            assertEquals(0, room.caveIns);
            // Open ground has no roof to damage.
            room.editor.destroyRoof(0, 0, RoofDamage.HOLED);
            assertEquals(RoofDamage.INTACT, room.topology.getRoofDamage(0, 0));
        }
    }

    @Test
    void aRebuiltRoofIsWholeAndARoofOpenedWithoutACauseReadsAsCollapsed() {
        try (Room room = new Room()) {
            room.editor.destroyRoof(3, 3, RoofDamage.BURNT);
            room.topology.setRoofDestroyed(3, 3, false);
            assertEquals(RoofDamage.INTACT, room.topology.getRoofDamage(3, 3));
            room.topology.setRoofDestroyed(5, 3, true);
            assertEquals(RoofDamage.COLLAPSED, room.topology.getRoofDamage(5, 3));
        }
    }
}
