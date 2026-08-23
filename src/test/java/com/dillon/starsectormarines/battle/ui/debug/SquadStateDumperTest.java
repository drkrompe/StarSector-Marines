package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadStateDumperTest {

    @Test
    void contactPictureCarriesDecisionAgeRatioAndPrimaryEvidence() throws Exception {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        for (int i = 0; i < 4; i++) {
            long member = sim.spawn(new EntitySpec("Marine " + i,
                    Faction.MARINE, UnitType.MARINE, 10, 9 + i).squad(squadId));
            if (i == 0) squad.leaderId = member;
        }
        long enemy = sim.spawn(new EntitySpec("Raider", Faction.DEFENDER,
                UnitType.MARINE, 15, 10));

        sim.advance(BattleSimulation.TICK_DT);
        sim.world().setCellPos(enemy, 14, 10);
        sim.advance(BattleSimulation.TICK_DT);
        squad._contactDoctrineChangedThisTick = true;

        JSONObject picture = SquadStateDumper.buildSquadJson(squad, sim)
                .getJSONObject("contactPicture");
        JSONObject evidence = picture.getJSONObject("primaryEvidence");

        assertEquals(sim.simTickIndex, picture.getInt("tick"));
        assertEquals(0, picture.getInt("ageTicks"));
        assertEquals(0.25, picture.getDouble("hostileToFriendlyRatio"), 0.001);
        assertEquals(enemy, picture.getLong("primaryContactId"));
        assertEquals("Raider", picture.getString("primaryContactName"));
        assertTrue(picture.getBoolean("doctrineChangedThisTick"));
        assertEquals("DIRECT", evidence.getString("source"));
        assertEquals(sim.simTickIndex, evidence.getInt("lastSeenTick"));
        assertEquals(0, evidence.getInt("ageTicks"));
        assertTrue(evidence.getBoolean("observedThisTick"));
        assertTrue(evidence.getBoolean("freshMotionSample"));
        assertEquals(15, evidence.getInt("previousDirectCellX"));
        assertEquals(10, evidence.getInt("previousDirectCellY"));
        assertEquals(sim.simTickIndex - 1, evidence.getInt("previousDirectTick"));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(32, 24);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }
}
