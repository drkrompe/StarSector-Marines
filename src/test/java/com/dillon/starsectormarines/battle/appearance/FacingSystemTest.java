package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end + column-walk coverage for {@link FacingSystem}: membership
 * (every {@link UnitType#carriesSprite()} type carries {@code SPRITE} at
 * spawn), layered torso tracking, and the corpse handoff (the row drops out
 * of {@link BattleComponents#liveSprites}).
 *
 * <p>Most scenarios construct the system directly and call {@link
 * FacingSystem#tick()} by hand — the {@code DeadBodySystemTest} arena
 * pattern, avoiding whole-tick AI nondeterminism for facing/target setup done
 * via direct {@code World} writes. The dead-target and corpse scenarios
 * still need one real {@link BattleSimulation#advance} to drain the death
 * mailbox through {@code DeadBodySystem}.
 */
public class FacingSystemTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static FacingSystem systemFor(BattleSimulation sim) {
        return new FacingSystem(sim.getEntityWorld(), sim.getBattleComponents(), sim.getRoster());
    }

    private static int spriteIndex(BattleSimulation sim, long id) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getInt(id, c.SPRITE, BattleComponents.SPRITE_INDEX);
    }

    private static float layeredFacing(BattleSimulation sim, long id) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getFloat(id, c.LAYERED_ANIMATION,
                BattleComponents.LAYERED_FACING_DEGREES);
    }

    @Test
    public void carriesSpriteTruthTable() {
        for (UnitType t : UnitType.values()) {
            boolean expectPosed = t != UnitType.TURRET
                    && t != UnitType.DRONE_HUB_STRUCTURE
                    && t != UnitType.BASED_AIRCRAFT
                    && t != UnitType.RANGE_TARGET
                    && t != UnitType.DRONE
                    && t != UnitType.GROUND_VEHICLE;
            assertEquals(expectPosed, t.carriesSprite(), t.name());
        }
    }

    @Test
    public void liveSheetUnitsSpawnWithSprite() {
        BattleSimulation sim = openArena(40, 40);
        long marine = sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 5, 5));
        long civilian = sim.spawn(new EntitySpec("c0", Faction.CIVILIAN, UnitType.CIVILIAN, 10, 10));

        EntityWorld world = sim.getEntityWorld();
        BattleComponents c = sim.getBattleComponents();
        for (long u : new long[]{marine, civilian}) {
            assertTrue(world.has(u, c.SPRITE), sim.identity().name(u) + " carries SPRITE at spawn");
        }

        // Negative membership: a live non-sheet unit (whole-sprite turret)
        // carries no SPRITE — it gains one only at the corpse transmute.
        long turret = sim.spawn(MapTurret.create("t0", Faction.DEFENDER,
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 20, 20));
        assertFalse(world.has(turret, c.SPRITE), "live turret carries no SPRITE");
    }

    @Test
    public void layeredMarineTracksReflexAcquisitionWithBoundedTurns() {
        BattleSimulation sim = openArena(40, 40);
        long marine = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, 20, 20));
        long east = sim.spawn(new EntitySpec("east", Faction.DEFENDER,
                UnitType.MARINE, 25, 20));
        long west = sim.spawn(new EntitySpec("west", Faction.DEFENDER,
                UnitType.MARINE, 15, 20));
        BattleComponents c = sim.getBattleComponents();
        sim.getEntityWorld().setFloat(marine, c.LAYERED_ANIMATION,
                BattleComponents.LAYERED_FACING_DEGREES, 0f);
        sim.world().setTargetId(marine, west);
        sim.combat().setReflexTargetId(marine, east);

        FacingSystem system = systemFor(sim);
        system.tick();
        float first = layeredFacing(sim, marine);
        float expectedStep = FacingSystem.COMBATANT_TURN_RATE_DEGREES_PER_SECOND
                * BattleSimulation.TICK_DT;
        assertEquals(expectedStep,
                Math.abs(LayeredAppearance.wrapDegrees(first)), 1e-4f,
                "visual aim follows reflex acquisition and turns one bounded step");

        sim.combat().setReflexTargetId(marine, west);
        system.tick();
        float second = layeredFacing(sim, marine);
        assertTrue(Math.abs(LayeredAppearance.wrapDegrees(second - first))
                        <= expectedStep + 1e-4f,
                "opposite-side acquisition cannot snap the layered torso");
    }

    @Test
    public void corpseFreezesPoseAndLeavesLiveSprites() {
        BattleSimulation sim = openArena(40, 40);
        long marine = sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 5, 5));
        long keepalive = sim.spawn(new EntitySpec("d-keepalive", Faction.DEFENDER, UnitType.MARINE, 38, 38));
        long id = marine;
        EntityWorld world = sim.getEntityWorld();
        BattleComponents c = sim.getBattleComponents();

        sim.applyDamage(marine, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.getRoster().isLive(id));
        int pose = spriteIndex(sim, id);
        assertTrue(pose >= 0 && pose < 4, "a valid death-pose frame");

        boolean matchesLiveSprites = false;
        for (ArchetypeTable t : world.matched(c.liveSprites)) {
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (t.entityAt(r) == id) matchesLiveSprites = true;
            }
        }
        assertFalse(matchesLiveSprites, "a corpse carries no HEALTH, so liveSprites excludes it");
    }

}
