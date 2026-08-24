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

class DetonationsContactPayloadTest {

    private static final float EPS = 1e-4f;

    @Test
    void directContactUsesItsOwnArmorProfileOnceWhileNeighborTakesSplash() {
        BattleSimulation sim = openArena();
        long shooter = sim.spawn(new EntitySpec(
                "shooter", Faction.MARINE, UnitType.MARINE, 1, 1).health(100f));
        long direct = armoredTarget(sim, "direct", Faction.DEFENDER, 5, 5);
        long neighbor = armoredTarget(sim, "neighbor", Faction.DEFENDER, 6, 5);

        sim.detonateNow(new PendingDetonation(
                shooter,
                5.5f, 5.5f, 0f,
                2f, /*splashDamage*/ 40f, /*splashPenetration*/ 0f,
                /*wallDamage*/ 0, Faction.MARINE, /*aerialDelivery*/ false,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false,
                /*friendlyFireImmune*/ false,
                direct, /*directDamage*/ 40f, /*directPenetration*/ 20f,
                /*authoredAftermath*/ false));

        assertEquals(60f, sim.world().armor(direct), EPS,
                "the contact target takes one full-efficiency direct payload and no splash");
        assertEquals(96f, sim.world().armor(neighbor), EPS,
                "the neighbor takes the low-penetration area payload");
        assertEquals(100f, sim.world().hp(direct), EPS);
        assertEquals(100f, sim.world().hp(neighbor), EPS);
        assertEquals(1, sim.telemetry().roundsHit(shooter),
                "direct explosive contact must use the ordinary ballistic-impact seam");
    }

    @Test
    void friendlyDirectDamageIsAppliedExactlyAsCarried() {
        BattleSimulation sim = openArena();
        long friendly = sim.spawn(new EntitySpec(
                "friendly", Faction.MARINE, UnitType.MARINE, 5, 5).health(100f));

        sim.detonateNow(new PendingDetonation(
                CombatTelemetryService.NO_ATTACKER,
                5.5f, 5.5f, 0f,
                1f, /*splashDamage*/ 40f, /*splashPenetration*/ 0f,
                /*wallDamage*/ 0, Faction.MARINE, /*aerialDelivery*/ false,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false,
                /*friendlyFireImmune*/ false,
                friendly, /*already source-multiplied*/ 7.5f, /*directPenetration*/ 20f,
                /*authoredAftermath*/ false));

        assertEquals(92.5f, sim.world().hp(friendly), EPS,
                "the detonation must not multiply or add splash to the carried direct value");
    }

    @Test
    void legacyConstructorRetainsUniformAreaOnlyBehavior() {
        BattleSimulation sim = openArena();
        long first = armoredTarget(sim, "first", Faction.DEFENDER, 5, 5);
        long second = armoredTarget(sim, "second", Faction.DEFENDER, 6, 5);

        sim.detonateNow(new PendingDetonation(
                CombatTelemetryService.NO_ATTACKER,
                5.5f, 5.5f, 0f,
                2f, 20f, 20f,
                0, Faction.MARINE, false));

        assertEquals(80f, sim.world().armor(first), EPS);
        assertEquals(80f, sim.world().armor(second), EPS);
    }

    @Test
    void zeroDamageContactMetadataDoesNotExcludeAnActorFromSplash() {
        BattleSimulation sim = openArena();
        long direct = armoredTarget(sim, "direct", Faction.DEFENDER, 5, 5);
        long neighbor = armoredTarget(sim, "neighbor", Faction.DEFENDER, 6, 5);

        sim.detonateNow(new PendingDetonation(
                CombatTelemetryService.NO_ATTACKER,
                5.5f, 5.5f, 0f,
                2f, /*splashDamage*/ 40f, /*splashPenetration*/ 0f,
                /*wallDamage*/ 0, Faction.MARINE, /*aerialDelivery*/ false,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false,
                /*friendlyFireImmune*/ false,
                direct, /*directDamage*/ 0f, /*directPenetration*/ 0f,
                /*authoredAftermath*/ false));

        assertEquals(96f, sim.world().armor(direct), EPS,
                "area-only weapons still splash the actor at the impact point");
        assertEquals(96f, sim.world().armor(neighbor), EPS);
    }

    private static BattleSimulation openArena() {
        int size = 12;
        NavigationGrid grid = new NavigationGrid(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(size, size));
    }

    private static long armoredTarget(BattleSimulation sim, String id, Faction faction,
                                       int cellX, int cellY) {
        return sim.spawn(new EntitySpec(id, faction, UnitType.MARINE, cellX, cellY)
                .health(100f)
                .armor(100f, 20f));
    }
}
