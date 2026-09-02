package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one seam both defence missions reach the allied garrison through, asked
 * from the outside: it is inert unless the mission named a faction to field.
 */
class AlliedGarrisonPlaceholderTest {

    private static final List<ShuttleAssignment> MANIFEST = List.of(
            new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));

    /** Every mission but the two defences names nobody, and gets nobody. */
    @Test
    void aMissionThatNamesNoAllyFieldsNone() {
        try (BattleSimulation sim = placeholder(null)) {
            assertEquals(0, alliedUnits(sim));
            for (Squad squad : sim.getSquads()) {
                assertTrue(squad.faction != Faction.ALLY);
            }
        }
    }

    /** A defence names the market it is defending, and its troops turn out. */
    @Test
    void aDefenceFieldsTheProtectedMarketsOwnGarrison() {
        try (BattleSimulation sim = placeholder("hegemony")) {
            int expected = AlliedGarrisonSize.squads(market())
                    * AlliedGarrison.SQUAD_SIZE;
            assertTrue(expected > 0, "the fixture market must field a garrison");
            assertEquals(expected, alliedUnits(sim));
            for (Squad squad : sim.getSquads()) {
                if (squad.faction != Faction.ALLY) continue;
                assertEquals(CommandAuthority.GARRISON,
                        sim.getSquadCommandDirective(squad.id).authority());
                assertEquals(AlliedGarrison.ISSUER,
                        sim.getSquadCommandDirective(squad.id).issuer());
            }
        }
    }

    private static BattleSimulation placeholder(String alliedGarrisonFactionId) {
        return BattleSetup.createPlaceholder(7_311L, MANIFEST, false,
                OperationTier.FIRST_CONTRACT, RiskLevel.LOW, MissionType.ASSAULT,
                market(), FlybyRoster.EMPTY, FlybyRoster.EMPTY, null,
                alliedGarrisonFactionId);
    }

    /** A settled, defended market — the shape a defence is fought over. */
    private static TargetProfile market() {
        return new TargetProfile(5, 6, 2, 1, "hegemony",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD);
    }

    private static int alliedUnits(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.ALLY) count++;
        }
        return count;
    }
}
