package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a body keeps of its live layered appearance, without a map or simulation. */
class CorpseAppearanceTest {
    @Test void layeredBodyKeepsTheFacingAndOutfitItDiedWithThroughADuplicateDeath() {
        EntityWorld world = new EntityWorld();
        BattleComponents c = new BattleComponents(world);
        long marine = world.createEntity(c.IDENTITY, c.POSITION, c.HEALTH, c.LAYERED_ANIMATION);
        world.setFloat(marine, c.LAYERED_ANIMATION, BattleComponents.LAYERED_FACING_DEGREES, 137f);
        world.setInt(marine, c.LAYERED_ANIMATION, BattleComponents.LAYERED_BODY_FAMILY,
                LayeredArmorFamily.SHIP_MEDICAL.ordinal());
        int[] tick = {412};
        DeadBodySystem bodies = new DeadBodySystem(world, c, () -> tick[0]);

        bodies.onDeath(new DeathEvent(marine, 0f, 0f, 2));
        tick[0] = 500;
        bodies.onDeath(new DeathEvent(marine, 0f, 0f, 2));

        assertEquals(137f, world.getFloat(marine, c.CORPSE, BattleComponents.CORPSE_FACING_DEGREES));
        assertEquals(LayeredArmorFamily.SHIP_MEDICAL.ordinal(),
                world.getInt(marine, c.CORPSE, BattleComponents.CORPSE_BODY_FAMILY));
        assertEquals(412, world.getInt(marine, c.CORPSE, BattleComponents.CORPSE_DEATH_TICK),
                "the body keeps the tick it fell on");
    }

    @Test void sheetDrawnBodyHasNoOutfit() {
        EntityWorld world = new EntityWorld();
        BattleComponents c = new BattleComponents(world);
        long turret = world.createEntity(c.IDENTITY, c.POSITION, c.HEALTH);
        new DeadBodySystem(world, c).onDeath(new DeathEvent(turret, 0f, 0f, 0));
        assertEquals(-1, world.getInt(turret, c.CORPSE, BattleComponents.CORPSE_BODY_FAMILY));
    }
}
