package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@link KitRetrieverBehavior}'s retrieval-path routing — the
 * FiringSystem epic sweep flips the per-unit dispatch's inline opportunistic
 * fire to a fire intent, and routes cooldown ticking through
 * {@link InfantryUnitPrep#tickCooldowns} (primary + secondary + reposition)
 * like the GOAP path does before {@code Action.execute}, in place of the old
 * primary-only inline decrement.
 */
public class KitRetrieverBehaviorTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void retrievalUpdateTicksAllThreeCooldownsAndWritesMovingIntentOnAnInRangeEnemy() {
        BattleSimulation sim = openArena(30, 10);
        Entity retriever = new Entity("r", Faction.MARINE, UnitType.MARINE, 5, 5);
        retriever.seedSecondaryWeapon = MarineSecondary.ROCKET_LAUNCHER;
        retriever.seedSecondaryAmmo = MarineSecondary.ROCKET_LAUNCHER.startingAmmo;
        sim.addUnit(retriever);
        sim.world().setAttackRange(retriever.entityId, 10f);
        sim.world().setCooldownTimer(retriever.entityId, 0.6f);
        sim.world().setSecondaryCooldownTimer(retriever.entityId, 0.6f);
        sim.world().setRepositionCooldown(retriever.entityId, 0.6f);

        Entity enemy = new Entity("e", Faction.DEFENDER, UnitType.MARINE, 8, 5);
        sim.addUnit(enemy);

        EquipmentDrop drop = new EquipmentDrop(20, 5, null);
        sim.task().setEquipmentDropTarget(retriever.entityId, drop);

        KitRetrieverBehavior.INSTANCE.update(retriever, sim);

        float dt = BattleSimulation.TICK_DT;
        assertEquals(0.6f - dt, sim.world().cooldownTimer(retriever.entityId), 1e-6f,
                "primary cooldown ticks by one TICK_DT via InfantryUnitPrep.tickCooldowns");
        assertEquals(0.6f - dt, sim.world().secondaryCooldownTimer(retriever.entityId), 1e-6f,
                "secondary cooldown now ticks too — the old inline decrement was primary-only");
        assertEquals(0.6f - dt, sim.world().repositionCooldown(retriever.entityId), 1e-6f,
                "reposition cooldown now ticks too");
        assertEquals(enemy.entityId, sim.combat().fireTargetId(retriever.entityId),
                "an in-range, visible enemy gets a MOVING fire intent written while pathing to the kit");
        assertEquals(FireStance.MOVING.ordinal(),
                sim.getRoster().entityWorld().getInt(retriever.entityId, sim.getRoster().components().COMBAT,
                        BattleComponents.COMBAT_FIRE_STANCE));
    }

    @Test
    public void demoteBranchNeverRunsTheRetrievalPathTickCooldowns() {
        // Placement guard: the demote branch (drop consumed/null) delegates to
        // CombatantBehavior, which runs its own GOAP prep (its own
        // tickCooldowns) — the retrieval-path tickCooldowns call must sit
        // AFTER the demote check, or a demote tick would double-tick.
        BattleSimulation sim = openArena(30, 10);
        Entity retriever = new Entity("r", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(retriever);
        sim.world().setCooldownTimer(retriever.entityId, 0.6f);

        EquipmentDrop drop = new EquipmentDrop(20, 5, null);
        drop.consumed = true;
        sim.task().setEquipmentDropTarget(retriever.entityId, drop);

        KitRetrieverBehavior.INSTANCE.update(retriever, sim);

        // No squad assigned — GoapInfantryBehavior.update no-ops entirely
        // (mirrors FiringSystemTest's squadless-defender pattern), so
        // cooldownTimer is untouched by this update() call, proving the
        // retrieval-path tickCooldowns call never ran on the demote branch.
        assertEquals(0.6f, sim.world().cooldownTimer(retriever.entityId), 1e-6f,
                "demote branch must not run the retrieval-path tickCooldowns call");
    }
}
