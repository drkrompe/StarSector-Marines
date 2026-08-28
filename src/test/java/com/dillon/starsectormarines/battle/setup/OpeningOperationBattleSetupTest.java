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
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpeningOperationBattleSetupTest {

    private static final List<ShuttleAssignment> MANIFEST = List.of(
            new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
            new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));

    @Test
    void reliefStartsWithLocalLineAndMilitiaOnlyRaiders() {
        BattleSimulation sim = BattleSetup.createOpeningOperation(
                8_101L, MANIFEST, 1, OpeningOperationKind.RELIEF,
                TargetProfile.NEUTRAL);

        assertEquals(8, count(sim, Faction.MARINE, UnitType.MILITIA));
        assertEquals(12, count(sim, Faction.DEFENDER, UnitType.MILITIA));
        assertEquals(12, combatants(sim, Faction.DEFENDER));
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
            if (squad.faction == Faction.MARINE) {
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
        assertEquals(OpeningOperationCommand.issuer(Faction.MARINE),
                employer.commandClaim.issuer());
        assertEquals(OpeningOperationCommand.issuer(Faction.MARINE),
                player.commandClaim.issuer());
        assertEquals(CommandAuthority.MISSION_COMMAND,
                employer.commandClaim.authority());
    }

    @Test
    void counterattackHasNoPredeployedLocalLine() {
        BattleSimulation sim = BattleSetup.createOpeningOperation(
                8_102L, MANIFEST, 1, OpeningOperationKind.COUNTERATTACK,
                TargetProfile.NEUTRAL);

        assertEquals(0, count(sim, Faction.MARINE, UnitType.MILITIA));
        assertEquals(12, count(sim, Faction.DEFENDER, UnitType.MILITIA));
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
