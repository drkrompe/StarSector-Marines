package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The preamble every scene used to write by hand, checked once. */
class SceneBuilderTest {

    private static final int W = 24;
    private static final int H = 12;

    @Test
    void openGroundIsWalkableEverywhere() {
        SceneWorld world = SceneBuilder.openGround(W, H).build();
        NavigationGrid grid = world.sim().getGrid();
        assertEquals(W, world.width());
        assertEquals(H, world.height());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertTrue(grid.isWalkable(x, y), "cell " + x + "," + y);
            }
        }
    }

    @Test
    void wallCutsAnInclusiveRectangle() {
        SceneWorld world = SceneBuilder.openGround(W, H).wall(4, 2, 6, 3).build();
        NavigationGrid grid = world.sim().getGrid();
        for (int y = 2; y <= 3; y++) {
            for (int x = 4; x <= 6; x++) assertFalse(grid.isWalkable(x, y), x + "," + y);
        }
        assertTrue(grid.isWalkable(3, 2));
        assertTrue(grid.isWalkable(7, 3));
        assertTrue(grid.isWalkable(4, 1));
        assertTrue(grid.isWalkable(6, 4));
    }

    @Test
    void wallWithDoorSealsTheColumnExceptTheDoorway() {
        int doorY = H / 2;
        SceneWorld world = SceneBuilder.openGround(W, H).wallWithDoor(10, doorY).build();
        NavigationGrid grid = world.sim().getGrid();
        for (int y = 0; y < H; y++) {
            assertEquals(y == doorY, grid.isWalkable(10, y), "column cell at y=" + y);
        }
        assertTrue(grid.hasTag(10, doorY, NavigationGrid.CellTag.DOORWAY));
        assertFalse(grid.hasTag(10, doorY - 1, NavigationGrid.CellTag.DOORWAY));
    }

    @Test
    void sealClosesTheWholeColumn() {
        SceneWorld world = SceneBuilder.openGround(W, H).seal(9).build();
        NavigationGrid grid = world.sim().getGrid();
        for (int y = 0; y < H; y++) assertFalse(grid.isWalkable(9, y), "y=" + y);
    }

    @Test
    void aDefaultSquadIsArmedAndSquadded() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .squad("alpha").at(6, 4).done()
                .build();

        long[] members = world.members("alpha");
        assertEquals(6, members.length);
        for (long member : members) {
            assertNotNull(world.sim().combat().primaryWeaponDef(member),
                    "an unarmed spawn is scenery");
        }
        Squad squad = world.squad("alpha");
        assertNotNull(squad);
        assertEquals(6, squad.aliveMembers);
        assertEquals(6, squad.originalSize);
        assertEquals(world.squadId("alpha"), squad.id);
    }

    @Test
    void unarmedSquadsCarryNoPrimary() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .squad("alpha").size(2).at(6, 4).unarmed().done()
                .build();
        for (long member : world.members("alpha")) {
            assertEquals(null, world.sim().combat().primaryWeaponDef(member));
        }
    }

    @Test
    void assignedLandsOnTheSquadsMission() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .squad("alpha").size(2).at(6, 4)
                .assigned(id -> ObjectiveAssignment.attackMove(id, 20, 6))
                .done()
                .build();

        Squad squad = world.squad("alpha");
        ObjectiveAssignment mission = squad.assignedObjective;
        assertNotNull(mission);
        assertEquals(squad.id, mission.squadId());
        assertEquals(20, mission.targetCellX());
        assertEquals(6, mission.targetCellY());
    }

    @Test
    void aMechLanceCarriesTheLoadoutThatRoutesItToItsOwnDispatcher() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .squad("lance").size(2).at(6, 4)
                .mech(MechVariant.BULWARK, MechRole.BALANCED)
                .done()
                .build();

        for (long member : world.members("lance")) {
            assertTrue(world.sim().world().hasMechLoadout(member),
                    "without the loadout a mech runs the infantry ladder");
        }
    }

    @Test
    void stationaryMembersDoNotMove() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .squad("post").size(2).at(6, 4).stationary().done()
                .build();
        for (long member : world.members("post")) {
            assertEquals(0f, world.sim().movement().moveSpeed(member));
        }
    }

    @Test
    void looseUnitsSpawnWhereTheyAreToldAndTakeTheirCustomiser() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .unit("far", Faction.DEFENDER, UnitType.MARINE, W - 2, H - 2,
                        spec -> spec.moveSpeed(0f))
                .build();

        long far = world.unit("far");
        assertEquals(W - 2, (int) world.sim().world().x(far));
        assertEquals(H - 2, (int) world.sim().world().y(far));
        assertEquals(0f, world.sim().movement().moveSpeed(far));
    }

    @Test
    void unknownKeysNameTheOnesThatExist() {
        SceneWorld world = SceneBuilder.openGround(W, H)
                .squad("alpha").size(2).at(6, 4).done()
                .unit("far", Faction.DEFENDER, UnitType.MARINE, W - 2, H - 2)
                .build();

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> world.squadId("bravo")).getMessage().contains("alpha"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> world.members("bravo")).getMessage().contains("alpha"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> world.unit("near")).getMessage().contains("far"));
    }

    @Test
    void buildingTwiceIsRefused() {
        SceneBuilder builder = SceneBuilder.openGround(W, H);
        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void aKeyIsClaimedOnce() {
        SceneBuilder builder = SceneBuilder.openGround(W, H);
        builder.squad("alpha").done();
        assertThrows(IllegalArgumentException.class, () -> builder.squad("alpha"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.unit("alpha", Faction.DEFENDER, UnitType.MARINE, 1, 1));
    }
}
