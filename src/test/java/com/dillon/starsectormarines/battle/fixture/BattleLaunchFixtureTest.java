package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleLaunchFixtureTest {

    @Test
    void checkedInProductionShapedLaunchReplaysHeadlessly() throws Exception {
        BattleLaunchFixture fixture =
                BattleFixtureTestSupport.loadConquestLaunchFixture();
        ConquestBattleFixture construction = (ConquestBattleFixture)
                fixture.construction();

        assertEquals(8, construction.manifest().size());
        assertEquals(8, construction.manifest().stream()
                .filter(shuttle -> shuttle.type == ShuttleType.VALKYRIE
                        && shuttle.cycles == 4)
                .count());
        assertEquals(384, construction.manifest().stream()
                .mapToInt(shuttle -> shuttle.type.capacity * shuttle.cycles)
                .sum());

        try (BattleSimulation simulation = fixture.build()) {
            assertEquals("fixture-marine-001",
                    firstMission(simulation).cycleLoadouts[0][0].campaignSoldierId);
            assertEquals(List.of(ReconPing.ID), simulation.getCommandPowerService()
                    .getAvailablePowers().stream().map(power -> power.id).toList());
            assertEquals(20,
                    simulation.getCommandPowerService().getAvailableSupplies());
        }
    }

    @Test
    void rebuildsPersonnelAircraftPowersAndFiniteResourcesIndependently() {
        BattleLaunchFixture fixture = productionShapedFixture();

        try (BattleSimulation first = fixture.build();
             BattleSimulation second = fixture.build()) {
            MarineLoadout firstSeat = firstMission(first).cycleLoadouts[0][0];
            assertEquals("marine-17", firstSeat.campaignSoldierId);
            assertEquals(MarineWeapon.PULSE_RIFLE.id, firstSeat.primaryDef.id);
            assertEquals(EquipmentGrade.MILSPEC, firstSeat.equipmentGrade);
            assertEquals(new SoldierProfile(SoldierAptitude.GIFTED, 321),
                    firstSeat.soldierProfile);
            assertEquals(LayeredArmorFamily.CHARCOAL, firstSeat.armorFamily);
            assertEquals("squad-2", firstSeat.campaignSquad.squadId);
            assertEquals(0, firstSeat.campaignSquad.fireTeamIndex);

            List<FighterWing> wings = first.getFlybyRoster().wings;
            assertEquals(FighterProfile.BROADSWORD, wings.get(0).profile);
            assertEquals(Faction.MARINE, wings.get(0).side);
            assertEquals(FighterProfile.DAGGER, wings.get(wings.size() - 1).profile);

            List<CommandPower> firstPowers =
                    first.getCommandPowerService().getAvailablePowers();
            List<CommandPower> secondPowers =
                    second.getCommandPowerService().getAvailablePowers();
            assertEquals(List.of(ReconPing.ID, MechSupport.ID),
                    firstPowers.stream().map(power -> power.id).toList());
            assertEquals(37, first.getCommandPowerService().getAvailableSupplies());
            assertEquals(37, second.getCommandPowerService().getAvailableSupplies());
            assertNotSame(firstPowers.get(0), secondPowers.get(0));
            assertNotSame(firstPowers.get(1), secondPowers.get(1));
            assertEquals(((MechSupport) firstPowers.get(1)).deployments(),
                    ((MechSupport) secondPowers.get(1)).deployments());
        }
    }

    @Test
    void rejectsDuplicatePowerIdsAtTheFrozenBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> new BattleLaunchOverlay(0, List.of(), List.of(), List.of(),
                        List.of(
                                new CommandPowerCommitment(ReconPing.ID, List.of()),
                                new CommandPowerCommitment(ReconPing.ID, List.of())),
                        0));
    }

    private static BattleLaunchFixture productionShapedFixture() {
        ConquestBattleFixture construction = new ConquestBattleFixture(
                8_192L,
                List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, OperationTier.REINFORCED, RiskLevel.MEDIUM,
                TargetProfile.NEUTRAL, List.of(),
                List.of(new FighterWingCommitment(FighterProfile.TALON,
                        Faction.DEFENDER, 1, 9f, 20f)));
        BattleLaunchOverlay launch = new BattleLaunchOverlay(
                0,
                List.of(new MarineSeatCommitment(
                        "marine-17", MarineWeapon.PULSE_RIFLE.id,
                        EquipmentGrade.MILSPEC,
                        new SoldierProfile(SoldierAptitude.GIFTED, 321),
                        null, LayeredArmorFamily.CHARCOAL,
                        60f, 5f, 0.95f, 0.9f,
                        "squad-2", "Second Squad", true, 1, 0)),
                List.of(new FighterWingCommitment(FighterProfile.BROADSWORD,
                        Faction.MARINE, 2, 4f, 15f)),
                List.of(new FighterWingCommitment(FighterProfile.DAGGER,
                        Faction.DEFENDER, 1, 7f, 30f)),
                List.of(
                        new CommandPowerCommitment(ReconPing.ID, List.of()),
                        new CommandPowerCommitment(MechSupport.ID, List.of(
                                new MechDeploymentSpec(MechVariant.SIROCCO,
                                        MechRole.LR_SUPPORT,
                                        MissileReplenisherComponent.ACCELERATED_FEED)))),
                37);
        return new BattleLaunchFixture(construction, launch);
    }

    private static ShuttleMission firstMission(BattleSimulation simulation) {
        BattleComponents components = simulation.getBattleComponents();
        for (ArchetypeTable table : simulation.getEntityWorld()
                .matched(components.airCraft)) {
            Object[] missions = table.objects(components.SHUTTLE_MISSION,
                    BattleComponents.SHUTTLE_MISSION_STATE).array();
            for (int row = 0; row < table.rowCount(); row++) {
                ShuttleMission mission = (ShuttleMission) missions[row];
                if (mission != null && mission.cycleLoadouts != null) return mission;
            }
        }
        throw new AssertionError("No shuttle mission found");
    }
}
