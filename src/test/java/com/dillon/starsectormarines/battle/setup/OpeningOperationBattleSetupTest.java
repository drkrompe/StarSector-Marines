package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningOperationBattleSetupTest {

    private static final List<ShuttleAssignment> MANIFEST = List.of(
            new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
            new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));

    @Test
    void reliefStartsWithLocalLineAndMilitiaOnlyRaiders() {
        BattleSimulation sim = BattleSetup.createOpeningOperation(
                8_101L, MANIFEST, 1, OpeningOperationKind.RELIEF,
                TargetProfile.NEUTRAL);

        // The local line is the client's own, so it is on the field as ALLY.
        // Eight bodies in two fireteams share the actual checkpoint gate.
        assertEquals(8, count(sim, Faction.ALLY, UnitType.MILITIA));
        assertEquals(0, count(sim, Faction.MARINE, UnitType.MILITIA),
                "the player's company does not field the employer's militia");
        assertEquals(12, count(sim, Faction.DEFENDER, UnitType.MILITIA));
        assertEquals(8, sim.pendingGroundArrivals(Faction.DEFENDER));
        assertEquals(12, combatants(sim, Faction.DEFENDER));
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.DEFENDER)
                assertEquals(WeaponRegistry.STARTER_PRIMARY_ID, sim.combat().primaryWeapon(unit).id);
        }
        assertEquals(0, count(sim, Faction.DEFENDER, UnitType.MARINE_RED));
        assertEquals(0, count(sim, Faction.DEFENDER, UnitType.HEAVY_MECH));
        assertEquals(0, count(sim, Faction.DEFENDER, UnitType.TURRET));
        assertInstanceOf(OpeningOperationCommand.class,
                sim.getCommander(Faction.MARINE));
        assertInstanceOf(OpeningOperationCommand.class,
                sim.getCommander(Faction.DEFENDER));

        int localPosts = 0;
        int raiderPool = 0;
        for (Squad squad : sim.getSquads()) {
            CommandDirective directive = sim.getSquadCommandDirective(squad.id);
            if (squad.faction == Faction.ALLY) {
                assertNotNull(directive);
                assertEquals(CommandAuthority.GARRISON, directive.authority());
                localPosts++;
            } else if (squad.faction == Faction.DEFENDER) {
                assertNotNull(directive);
                assertEquals(CommandAuthority.MISSION_COMMAND,
                        directive.authority());
                assertEquals(OpeningOperationCommand.issuer(Faction.DEFENDER),
                        directive.issuer());
                raiderPool++;
            }
        }
        assertEquals(2, localPosts);
        assertEquals(3, raiderPool);

        long[] shuttles = sim.getAirEntityIds();
        assertEquals(2, shuttles.length);
        ShuttleMission employer = sim.world().mission(shuttles[0]);
        ShuttleMission player = sim.world().mission(shuttles[1]);
        assertEquals(UnitType.MILITIA, employer.deboardUnitType);
        assertNull(player.deboardUnitType);
        assertEquals(Faction.ALLY, sim.world().airFaction(shuttles[0]));
        assertEquals(Faction.MARINE, sim.world().airFaction(shuttles[1]));
        // The employer still owns its troops, but they reinforce the same
        // checkpoint gate as the starting line instead of guarding their pad.
        assertNull(employer.commandClaim);
        assertNotNull(employer.garrisonNode);
        assertEquals(Faction.ALLY, employer.garrisonNode.defaultGuard);
        assertEquals(OpeningOperationCommand.issuer(Faction.MARINE),
                player.commandClaim.issuer());
        assertEquals(CommandAuthority.MISSION_COMMAND,
                player.commandClaim.authority());
        assertNull(player.garrisonNode);
        assertEquals(6f, employer.pendingDelay);
        assertEquals(7.5f, player.pendingDelay);
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.ALLY) assertSame(employer.garrisonNode, squad.assignedNode);
        }
        assertTrue(Math.hypot(employer.garrisonNode.anchorX - employer.lzX,
                employer.garrisonNode.anchorY - employer.lzY) > 3,
                "employer reinforcement destination must be the post, not its landing pad");
        sim.close();
    }

    @Test
    void counterattackHasNoPredeployedLocalLine() {
        BattleSimulation sim = BattleSetup.createOpeningOperation(
                8_102L, MANIFEST, 1, OpeningOperationKind.COUNTERATTACK,
                TargetProfile.NEUTRAL);

        assertEquals(0, count(sim, Faction.ALLY, UnitType.MILITIA));
        assertEquals(0, count(sim, Faction.MARINE, UnitType.MILITIA));
        assertEquals(12, count(sim, Faction.DEFENDER, UnitType.MILITIA));
        assertEquals(0f, sim.world().mission(sim.getAirEntityIds()[0]).pendingDelay);
        assertEquals(1.5f, sim.world().mission(sim.getAirEntityIds()[1]).pendingDelay);
        sim.close();
    }

    private static int count(BattleSimulation sim, Faction faction,
                             UnitType type) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == faction
                    && sim.identity().type(unit) == type) count++;
        }
        return count;
    }

    private static int combatants(BattleSimulation sim, Faction faction) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == faction
                    && sim.identity().type(unit).combatant) count++;
        }
        return count;
    }
}
