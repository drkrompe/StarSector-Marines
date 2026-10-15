package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A round that flies to a point is aimed where its target will be when it lands. The scatter is
 * drawn around that point exactly as it was around the target, so leading changes where the
 * pattern is centred and nothing about how wide it is.
 */
class TurretRoundLeadTest {

    private static final int ROW = 20;
    private static final long SEED = 4242L;
    private static final float WALK = 1.5f;

    @Test
    void aMortarBombIsLaidAheadOfAWalkingManByHisTravelDuringTheFlight() {
        Projectile still = fire(0f);
        Projectile walking = fire(WALK);

        assertEquals(still.toX, walking.toX, 1e-3f, "the same draws scatter both rounds alike");
        float flight = still.totalFlightTime;
        assertEquals(WALK * flight, walking.toY - still.toY, 0.05f,
                "the pattern moves with the man, by his walk over the flight");
    }

    private static Projectile fire(float walkNorth) {
        NavigationGrid grid = new NavigationGrid(60, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(60, 40), SEED);
        StructureDef mortar = TurretCatalogRegistry.requireStructure("structure.deployable-field-mortar");
        long tube = sim.spawn(MapTurret.create("tube", Faction.MARINE, mortar.id, 5, ROW));
        long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 40, ROW));
        EntityWorld world = sim.getEntityWorld();
        BattleComponents c = sim.getBattleComponents();
        world.setFloat(target, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_X, 0f);
        world.setFloat(target, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, walkNorth);

        sim.fireShotFrom(tube, sim.world().x(tube), sim.world().y(tube), Faction.MARINE, mortar, target,
                /*aerialShooter*/ false, /*hasLos*/ true);
        assertEquals(1, sim.getActiveProjectiles().size());
        return sim.getActiveProjectiles().get(0);
    }
}
