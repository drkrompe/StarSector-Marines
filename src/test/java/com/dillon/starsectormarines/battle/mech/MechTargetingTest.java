package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MechTargetingTest {

    @Test
    void replacesRearTargetWithEnemyInsideCurrentTraverse() {
        BattleSimulation sim = arena();
        long mech = mechFacingNorth(sim);
        long rearTarget = alien(sim, 20, 8);
        long flankTarget = alien(sim, 12, 20);
        sim.world().setTargetId(mech, rearTarget);

        assertEquals(flankTarget, MechTargeting.refreshTarget(mech, sim),
                "a visible target inside the gun arc should replace one in the rear blind wedge");
    }

    @Test
    void closeThreatInterruptsAValidDistantEngagement() {
        BattleSimulation sim = arena();
        long mech = mechFacingNorth(sim);
        long distantTarget = alien(sim, 20, 35);
        long closeThreat = alien(sim, 26, 20);
        sim.world().setTargetId(mech, distantTarget);

        assertEquals(closeThreat, MechTargeting.refreshTarget(mech, sim),
                "an enemy inside the self-defense radius should pull aggro from a distant target");
    }

    @Test
    void keepsAnExistingCloseTargetToAvoidThrashing() {
        BattleSimulation sim = arena();
        long mech = mechFacingNorth(sim);
        long current = alien(sim, 27, 20);
        alien(sim, 24, 20);
        sim.world().setTargetId(mech, current);

        assertEquals(current, MechTargeting.refreshTarget(mech, sim),
                "once fighting at close range, the mech should finish traversing instead of changing every tick");
    }

    @Test
    void visibleEnemyOutsideSelfDefenseIsNotAcquiredWithoutSquadBelief() {
        BattleSimulation sim = arena();
        SquadMech fixture = squadMechFacingNorth(sim);
        alien(sim, 20, 34);

        assertEquals(0L, MechTargeting.refreshTarget(fixture.mech(), sim),
                "doctrine targeting cannot manufacture a new far contact from global live state");
    }

    @Test
    void actionableBeliefCanBeAcquiredOnlyWhileCurrentlyShootable() {
        BattleSimulation sim = arena();
        SquadMech fixture = squadMechFacingNorth(sim);
        long believed = alien(sim, 20, 34);
        SquadBeliefTestAccess.observeDirect(fixture.squad(), believed,
                20, 34, sim.getSimTickIndex());

        assertEquals(believed,
                MechTargeting.refreshTarget(fixture.mech(), sim));

        for (int x = 0; x < sim.getGrid().getWidth(); x++) {
            sim.getGrid().setWalkable(x, 27, false);
            sim.getGrid().setWallHp(x, 27, 100);
            sim.getTopology().setWall(x, 27, true);
        }
        sim.world().setTargetId(fixture.mech(), believed);
        assertEquals(0L, MechTargeting.refreshTarget(fixture.mech(), sim),
                "remembering an identity must not reveal or pursue its hidden live position");
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(45, 45);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(45, 45));
    }

    private static long mechFacingNorth(BattleSimulation sim) {
        long mech = sim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 20, 20));
        BattleComponents components = sim.getBattleComponents();
        sim.getEntityWorld().setFloat(mech, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        return mech;
    }

    private static SquadMech squadMechFacingNorth(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = sim.spawn(MechVariant.HOUND.applyTo(new EntitySpec(
                "squad-mech", Faction.MARINE, UnitType.HEAVY_MECH,
                20, 20).squad(squadId)));
        sim.world().attachMechLoadout(mech,
                MechVariant.HOUND.createLoadout(MechRole.BALANCED));
        BattleComponents components = sim.getBattleComponents();
        sim.getEntityWorld().setFloat(mech, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = 20.5f;
        squad.centroidY = 20.5f;
        return new SquadMech(mech, squad);
    }

    private static long alien(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("alien-" + sim.liveUnitCount(),
                Faction.DEFENDER, UnitType.ALIEN, x, y));
    }

    private record SquadMech(long mech, Squad squad) {}
}
