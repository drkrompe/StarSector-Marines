package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corpse-publication unit, without a map, simulation, renderer or campaign host. */
class LateDeathPublicationTest {
    @Test void secondBoundaryPublishesLateNamedDeathAndCascadeExactlyOnce() {
        EntityWorld world = new EntityWorld();
        BattleComponents components = new BattleComponents(world);
        long late = world.createEntity(components.IDENTITY, components.POSITION, components.HEALTH);
        long cascade = world.createEntity(components.IDENTITY, components.POSITION, components.HEALTH);
        world.setObject(late, components.IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID, "named-late");
        world.setObject(cascade, components.IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID, "named-cascade");
        DeathDispatcher dispatcher = new DeathDispatcher();
        DeadBodySystem corpses = new DeadBodySystem(world, components);
        List<Long> published = new ArrayList<>();
        dispatcher.subscribe(event -> {
            if (event.unitId() == late) dispatcher.publish(new DeathEvent(cascade, 0f, 0f, 1));
        });
        dispatcher.subscribe(corpses::onDeath);
        dispatcher.subscribe(event -> published.add(event.unitId()));
        dispatcher.drain(); // early DEMOLISH boundary, before the delayed impact
        dispatcher.publish(new DeathEvent(late, 0f, 0f, 2));
        assertFalse(world.has(late, components.CORPSE), "Publishing remains buffered");
        dispatcher.drain(); // final publication boundary before completion
        assertEquals(List.of(late, cascade), published);
        for (long id : List.of(late, cascade)) {
            assertTrue(world.has(id, components.CORPSE));
            assertFalse(world.has(id, components.HEALTH));
        }
        assertEquals("named-late", world.getObject(late, components.IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID));
        assertEquals("named-cascade", world.getObject(cascade, components.IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID));
        dispatcher.drain();
        assertEquals(List.of(late, cascade), published, "No duplicate publication on a later empty drain");
    }
}
