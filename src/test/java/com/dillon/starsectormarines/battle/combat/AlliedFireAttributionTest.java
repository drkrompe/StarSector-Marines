package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who a round belongs to, once there is a friendly side that is not the
 * player's.
 *
 * <p>{@link DamageResolver} decides "friendly" for both the separate
 * friendly-fire counter and the kill credit, and it decided it by comparing
 * faction identities — which is right for two sides and gives an allied
 * militiaman shot by a marine to that marine as a scored kill. The distinction
 * matters beyond bookkeeping: {@code damageDealt} is what a soldier's career
 * record and the after-action readout are built from.
 */
class AlliedFireAttributionTest {

    private static final int W = 16;
    private static final int H = 16;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long spawn(BattleSimulation sim, String id, Faction faction, int x, int y) {
        return sim.spawn(new EntitySpec(id, faction, UnitType.MARINE, x, y));
    }

    @Test
    void aMarineRoundIntoAnAllyIsFriendlyFireAndNoKill() {
        try (BattleSimulation sim = openSim()) {
            long marine = spawn(sim, "m0", Faction.MARINE, 4, 4);
            long ally = spawn(sim, "a0", Faction.ALLY, 6, 6);
            CombatTelemetryService telemetry = sim.telemetry();

            sim.applyDamage(ally, marine, 5f, 1f, 1f);

            assertEquals(5f, telemetry.friendlyFireDamage(marine), 1e-4f,
                    "shooting the militia fighting beside you is friendly fire");
            assertEquals(0f, telemetry.damageDealt(marine), 1e-4f,
                    "and is not output the marine produced");

            sim.applyDamage(ally, marine, sim.world().hp(ally) + 10f, 1f, 1f);

            assertFalse(sim.world().isAlive(ally), "the round still kills");
            assertEquals(0, telemetry.kills(marine),
                    "but an ally is never a scored kill");
        }
    }

    /** Symmetric: the ally shooting the company is friendly fire too. */
    @Test
    void anAlliedRoundIntoAMarineIsFriendlyFire() {
        try (BattleSimulation sim = openSim()) {
            long ally = spawn(sim, "a0", Faction.ALLY, 4, 4);
            long marine = spawn(sim, "m0", Faction.MARINE, 6, 6);

            sim.applyDamage(marine, ally, 5f, 1f, 1f);

            assertEquals(5f, sim.telemetry().friendlyFireDamage(ally), 1e-4f);
            assertEquals(0f, sim.telemetry().damageDealt(ally), 1e-4f);
        }
    }

    @Test
    void anAlliedRoundIntoADefenderIsALegitimateHitAndAScoredKill() {
        try (BattleSimulation sim = openSim()) {
            long ally = spawn(sim, "a0", Faction.ALLY, 4, 4);
            long defender = spawn(sim, "d0", Faction.DEFENDER, 6, 6);
            CombatTelemetryService telemetry = sim.telemetry();

            sim.applyDamage(defender, ally, 5f, 1f, 1f);

            assertEquals(5f, telemetry.damageDealt(ally), 1e-4f);
            assertEquals(0f, telemetry.friendlyFireDamage(ally), 1e-4f);

            sim.applyDamage(defender, ally, sim.world().hp(defender) + 10f, 1f, 1f);

            assertEquals(1, telemetry.kills(ally));
        }
    }

    /**
     * The defender's own bookkeeping is untouched: an ally is an enemy to it,
     * so killing one is a kill and not friendly fire.
     */
    @Test
    void aDefenderRoundIntoAnAllyIsALegitimateHit() {
        try (BattleSimulation sim = openSim()) {
            long defender = spawn(sim, "d0", Faction.DEFENDER, 4, 4);
            long ally = spawn(sim, "a0", Faction.ALLY, 6, 6);

            sim.applyDamage(ally, defender, 5f, 1f, 1f);

            assertEquals(5f, sim.telemetry().damageDealt(defender), 1e-4f);
            assertEquals(0f, sim.telemetry().friendlyFireDamage(defender), 1e-4f);
        }
    }
}
