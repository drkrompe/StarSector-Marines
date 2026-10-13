package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A commander reads whether its own squads can wreck a building off the kit they were issued, and keeps what it has
 * sighted. Both are facts about its own side, so neither needs more than a squad on a field.
 */
class CommandFrameDemolitionTest {

    private static BattleSimulation sim() {
        NavigationGrid grid = new NavigationGrid(24, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 24));
    }

    private static int squadOf(BattleSimulation sim, int x, String... specials) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long leader = 0L;
        for (int i = 0; i < specials.length; i++) {
            EntitySpec spec = new EntitySpec("m" + x + "-" + i, Faction.MARINE, UnitType.MARINE, x, 4 + i).squad(squadId);
            if (specials[i] != null) spec.specialEquipment(SpecialEquipmentRegistry.require(specials[i]), 0);
            long id = sim.spawn(spec);
            if (leader == 0L) leader = id;
        }
        sim.getSquad(squadId).leaderId = leader;
        sim.getSquad(squadId).aliveMembers = specials.length;
        return squadId;
    }

    @Test
    void aSquadWithOneSatchelKitAmongItsMembersIsReadAsAbleToWreck() {
        BattleSimulation sim = sim();
        int sappers = squadOf(sim, 3, null, SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, null);
        int riflemen = squadOf(sim, 9, null, null);
        int rockets = squadOf(sim, 15, SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);

        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE, CommandTopology.freeze(sim),
                new CommandAssignmentSnapshot(Map.of()));

        assertTrue(frame.squad(sappers).demolition());
        assertFalse(frame.squad(riflemen).demolition());
        assertFalse(frame.squad(rockets).demolition(), "a rocket tube is not a charge");
    }

    @Test
    void aSightingOfAStructureIsKept() {
        BattleSimulation sim = sim();
        assertFalse(sim.structureSighted(12, 7));
        sim.noteStructureSighted(12, 7);
        assertTrue(sim.structureSighted(12, 7), "the company looked away, and still knows");
        assertFalse(sim.structureSighted(7, 12), "and only that place");
    }
}
