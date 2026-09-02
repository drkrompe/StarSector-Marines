package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliedGarrisonTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 20;

    @Test
    void theMarketsTroopsStandUpAsAlliedGarrisonPostsOfTheirOwn() {
        try (BattleSimulation sim = openGround()) {
            GroundRosterProfile roster = GroundRosterRegistry.resolve("hegemony");

            AlliedGarrison.Installed installed = AlliedGarrison.install(
                    sim, map(), roster, 3, RiskLevel.HIGH, new Random(4_401L));

            assertEquals(3, installed.squadCount());
            for (int squadId : installed.squadIds()) {
                Squad squad = sim.getSquad(squadId);
                assertNotNull(squad);
                // The player's order path admits MARINE squads and nothing else
                // (see SquadMoveOrderSystem), so an ALLY squad is out of the pool
                // by the same relation that makes it friendly.
                assertEquals(Faction.ALLY, squad.faction);
                assertEquals(AlliedGarrison.SQUAD_SIZE,
                        sim.squadMemberCount(squadId));
                assertNotNull(squad.assignedNode);
                assertEquals(Faction.ALLY, squad.assignedNode.defaultGuard);

                CommandDirective directive = sim.getSquadCommandDirective(squadId);
                assertNotNull(directive);
                assertEquals(CommandAuthority.GARRISON, directive.authority());
                assertEquals(AlliedGarrison.ISSUER, directive.issuer());

                for (int i = 0; i < sim.squadMemberCount(squadId); i++) {
                    long member = sim.squadMemberAt(squadId, i);
                    assertEquals(Faction.ALLY, sim.identity().faction(member));
                    assertEquals(UnitRole.GARRISON, sim.role().role(member));
                    assertNotNull(sim.combat().primaryWeapon(member));
                }
            }
            assertEquals(3 * AlliedGarrison.SQUAD_SIZE, alliedUnits(sim));
        }
    }

    /**
     * The kit is the protected faction's, not a militia default: two rosters
     * asked the same way hand out different rifles. Rolled with a table-first
     * stream so the comparison is the doctrine rather than the dice.
     */
    @Test
    void membersCarryTheProtectedFactionsOwnPrimary() {
        WeaponDef hegemony = firstIssuedPrimary("hegemony");
        WeaponDef pirates = firstIssuedPrimary("pirates");

        assertSame(GroundRosterRegistry.resolve("hegemony")
                        .issue(GroundRosterProfile.ForceTier.BULK)
                        .pickPrimaryDef(new TableFirstRandom()),
                hegemony);
        assertNotEquals(hegemony.id, pirates.id);
    }

    @Test
    void aGarrisonOfNoSquadsInstallsNothing() {
        try (BattleSimulation sim = openGround()) {
            AlliedGarrison.Installed installed = AlliedGarrison.install(
                    sim, map(), GroundRosterRegistry.resolve("hegemony"), 0,
                    RiskLevel.HIGH, new Random(1L));

            assertEquals(0, installed.squadCount());
            assertEquals(0, alliedUnits(sim));
            assertTrue(sim.getSquads().isEmpty());
        }
    }

    private static WeaponDef firstIssuedPrimary(String factionId) {
        try (BattleSimulation sim = openGround()) {
            AlliedGarrison.Installed installed = AlliedGarrison.install(
                    sim, map(), GroundRosterRegistry.resolve(factionId), 1,
                    RiskLevel.HIGH, new TableFirstRandom());
            long member = sim.squadMemberAt(installed.squadIds().get(0), 0);
            return sim.combat().primaryWeapon(member);
        }
    }

    private static int alliedUnits(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.ALLY) count++;
        }
        return count;
    }

    private static BattleSimulation openGround() {
        return BattleSetup.buildMap(map(), Collections.emptyList(),
                Collections.emptyList(), 4_401L).sim();
    }

    /** Open ground with the company ashore in the west and the enemy in the east. */
    private static MapResult map() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return new MapResult(grid, topology, 2, HEIGHT / 2, WIDTH - 3, HEIGHT / 2,
                Collections.emptyList(), Collections.emptyList());
    }

    /** Always picks a weighted table's first entry, so a roll reads as its doctrine. */
    private static final class TableFirstRandom extends Random {
        @Override public int nextInt(int bound) { return 0; }
        @Override public int nextInt() { return 0; }
        @Override public float nextFloat() { return 0f; }
        @Override public double nextDouble() { return 0d; }
    }
}
