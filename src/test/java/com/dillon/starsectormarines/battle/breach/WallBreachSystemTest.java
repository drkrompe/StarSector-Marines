package com.dillon.starsectormarines.battle.breach;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An order that names a wall cell spends the squad's kit on that cell and on nothing else; without the order the same
 * gun leaves the wall alone.
 */
class WallBreachSystemTest {

    private static final int WALL_X = 22;
    private static final int WALL_HP = 300;

    @BeforeAll
    static void data() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static SceneWorld world(boolean ordered) {
        SceneBuilder builder = SceneBuilder.openGround(48, 40).missionCompletion(false)
                .wall(WALL_X, 17, WALL_X, 23);
        builder.squad("cannon").faction(Faction.MARINE).size(1).at(8, 20)
                .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT)
                .assigned(id -> {
                    ObjectiveAssignment move = ObjectiveAssignment.attackMove(id, 8, 20);
                    return ordered ? move.withBreach(WALL_X, 20) : move;
                }).done();
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 2, 2);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 46, 38);
        SceneWorld world = builder.build();
        NavigationGrid grid = world.sim().getGrid();
        for (int y = 17; y <= 23; y++) grid.setWallHp(WALL_X, y, WALL_HP);
        return world;
    }

    private static int standing(BattleSimulation sim) {
        int n = 0;
        for (int y = 17; y <= 23; y++) if (!sim.getGrid().isWalkable(WALL_X, y)) n++;
        return n;
    }

    @Test
    void aCannonOrderedAtAWallBringsItDown() {
        SceneWorld world = world(true);
        SceneRun.of(world).run(1500);
        BattleSimulation sim = world.sim();
        assertTrue(sim.getGrid().isWalkable(WALL_X, 20), "the ordered cell is open");
        assertTrue(sim.wallBreach().roundsAtWalls() > 0);
        assertTrue(standing(sim) >= 3, "the cut is the ordered cell and its neighbours, not the whole wall");
    }

    @Test
    void theSameCannonWithNoWallOrderLeavesItStanding() {
        SceneWorld world = world(false);
        SceneRun.of(world).run(1500);
        assertEquals(0, world.sim().wallBreach().roundsAtWalls());
        assertEquals(7, standing(world.sim()));
    }
}
