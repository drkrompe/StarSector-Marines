package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleResourcesTest {
    private CompoundService armory() {
        var service = new CompoundService();
        service.register(new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5, 3, 3, 7, 7, Faction.DEFENDER, 10, 4));
        return service;
    }
    @Test void genericProfileStillProducesForTheCurrentOwner() {
        var resources = new BattleResources(); var compounds = armory();
        resources.tick(80, compounds);
        assertEquals(1f, resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT));
        compounds.getRecords().iterator().next().state = CompoundService.CompoundState.MARINE_HELD;
        resources.tick(80, compounds);
        assertEquals(1f, resources.getBalance(Faction.MARINE, ResourceType.REINFORCEMENT));
    }
    @Test void finiteProfileCannotEarnTicketsFromEitherTerritoryOwner() {
        var resources = new BattleResources(); var compounds = armory();
        resources.disableCompoundProduction(); resources.tick(80, compounds);
        compounds.getRecords().iterator().next().state = CompoundService.CompoundState.MARINE_HELD;
        resources.tick(80, compounds);
        assertEquals(0f, resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT));
        assertEquals(0f, resources.getBalance(Faction.MARINE, ResourceType.REINFORCEMENT));
        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT, 1f);
        assertTrue(resources.tryConsume(Faction.DEFENDER, ResourceType.REINFORCEMENT, 1f));
        assertFalse(resources.tryConsume(Faction.DEFENDER, ResourceType.REINFORCEMENT, 1f));
    }
}
