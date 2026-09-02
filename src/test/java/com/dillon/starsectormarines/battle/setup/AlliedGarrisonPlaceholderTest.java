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

    /**
     * The mission's numbers doctrine reaches the ground through this seam and no
     * other, so it is asked here rather than only of the size function.
     */
    @Test
    void theMissionsNumbersDoctrineReachesTheGround() {
        try (BattleSimulation neutral = placeholder("hegemony", 1f);
             BattleSimulation doubled = placeholder("hegemony", 2f)) {
            assertEquals(AlliedGarrisonSize.squads(market(), 2f)
                            * AlliedGarrison.SQUAD_SIZE,
                    alliedUnits(doubled));
            assertTrue(alliedUnits(doubled) > alliedUnits(neutral),
                    "a doubled numbers doctrine must field more of the colony");
        }
    }

    private static BattleSimulation placeholder(String alliedGarrisonFactionId) {
        return BattleSetup.createPlaceholder(7_311L, MANIFEST, false,
                OperationTier.FIRST_CONTRACT, RiskLevel.LOW, MissionType.ASSAULT,
                market(), FlybyRoster.EMPTY, FlybyRoster.EMPTY, null,
                alliedGarrisonFactionId);
    }

    private static BattleSimulation placeholder(String alliedGarrisonFactionId,
                                                float strengthMultiplier) {
        return BattleSetup.createPlaceholder(7_311L, MANIFEST, false,
                OperationTier.FIRST_CONTRACT, RiskLevel.LOW, MissionType.ASSAULT,
                market(), FlybyRoster.EMPTY, FlybyRoster.EMPTY, null,
                alliedGarrisonFactionId, strengthMultiplier);
    }

    /**
     * A settled, defended market — the shape a defence is fought over. Its
     * ground-defence strength is vanilla's own for a size-5 colony at stability
     * 6 with no defence industry ({@code 200 * 0.7}), so this fixture fields the
     * three fireteams it has always fielded.
     */
    private static TargetProfile market() {
        return new TargetProfile(5, 6, 2, 1, "hegemony",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD, 140f, 0f);
    }

    private static int alliedUnits(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.ALLY) count++;
        }
        return count;
    }
}
