package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a gun run does to people standing in it.
 *
 * <p>The run puts rounds on the ground rather than resolving against a chosen
 * victim, so being caught is a question of how much of you is standing in the
 * beaten zone. That is the whole design and it only works if the two cases
 * actually come out different: massed in the open should be a disaster and
 * spread out should mostly be noise.
 */
class StrafeLethalityTest {

    private static final int W = 80;
    private static final int H = 60;

    private static BattleSimulation openField() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * Runs one strike over a platoon and answers how many were still standing.
     *
     * @param spacing cells between neighbours — 1 is shoulder to shoulder,
     *                larger is dispersed
     */
    private static int survivorsOfOneStrike(int spacing) {
        BattleSimulation sim = openField();
        int targetX = 40;
        int targetY = 30;
        int platoon = 12;
        for (int i = 0; i < platoon; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    targetX + (i % 4) * spacing, targetY + (i / 4) * spacing));
        }
        // A single defender well away from the action, so the battle does not
        // end the moment the strike starts working.
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 2, 2));

        long fighter = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                targetX + 0.5f, targetY + 0.5f, 5f, 5f, 5f, 5f, 0f);
        ShuttleMission mission = sim.world().mission(fighter);
        mission.strikeSortie = true;
        sim.world().kinematics(fighter).teleport(5f, 5f, 0f);
        mission.state = ShuttleState.INCOMING;

        for (int t = 0; t < 3000 && mission.state != ShuttleState.GONE
                && mission.state != ShuttleState.DEPARTING; t++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        int alive = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.MARINE) alive++;
        }
        return alive;
    }

    /**
     * Massed in the open is a disaster; dispersed is survivable.
     *
     * <p>Both numbers matter. A run that kills everybody however they are
     * standing makes dispersal pointless, and one that kills nobody makes the
     * airfield pointless.
     */
    @Test
    void beingMassedInTheOpenIsWhatMakesAGunRunDecisive() {
        int massedLeft = survivorsOfOneStrike(1);
        int spreadLeft = survivorsOfOneStrike(5);
        System.out.println("[strafe] of 12 marines, survivors — massed: " + massedLeft
                + ", dispersed: " + spreadLeft);

        // Held near what a strike actually does rather than at a number almost
        // anything clears. At eight, a pass that had quietly lost most of its
        // rounds — an aircraft re-dialled faster over the same beaten zone at
        // the same rate of fire — still passed, which is a test that reports a
        // decisive weapon while watching a weak one.
        assertTrue(massedLeft <= 5,
                "a strike over a bunched platoon left " + massedLeft + " of 12 standing");
        assertTrue(spreadLeft > massedLeft,
                "dispersing did not help: massed " + massedLeft + ", spread " + spreadLeft);
        assertTrue(spreadLeft >= 6,
                "dispersed infantry lost " + (12 - spreadLeft) + " of 12, so spreading out"
                        + " is no defence and a gun run is simply an area delete");
    }
}
