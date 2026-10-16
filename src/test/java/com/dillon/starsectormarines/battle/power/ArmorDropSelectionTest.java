package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ArmorDropPayload;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calling armor is a choice from the frozen bay: subset, lift and cooldown. */
class ArmorDropSelectionTest {

    private static final FittedBoat LIGHT_CRADLE = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");
    private static final MechDeploymentSpec HOUND = new MechDeploymentSpec(MechVariant.HOUND,
            null, null, MechVariant.HOUND.arms, MechVariant.HOUND.leftShoulder,
            MechVariant.HOUND.rightShoulder, "Hammer");
    private static final MechDeploymentSpec BULWARK = MechDeploymentSpec.standard(MechVariant.BULWARK);
    private static final VehicleDeploymentSpec APC =
            new VehicleDeploymentSpec("v-1", "Mule", VehicleType.HEAVY_APC);
    private static final int HOUND_AT = 0;
    private static final int BULWARK_AT = 1;
    private static final int APC_AT = 2;

    @Test
    void theManifestIsFrozenAtLaunchMechsThenVehicles() {
        List<MechDeploymentSpec> mechs = new ArrayList<>(List.of(HOUND, BULWARK));
        MechSupport power = MechSupport.configured(mechs, List.of(APC), LIGHT_CRADLE, false);
        mechs.clear();

        BayManifest manifest = power.manifest();
        assertEquals(3, manifest.size());
        assertEquals("Hammer", manifest.get(HOUND_AT).callsign());
        assertTrue(manifest.get(APC_AT).isVehicle());
        assertEquals("Mule", manifest.get(APC_AT).callsign());
        assertEquals("Heavy APC", manifest.get(APC_AT).typeName());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.entries().add(manifest.get(0)));
    }

    @Test
    void onlyThePickedMachineIsDispatchedAndTheRestStayAboard() {
        BattleSimulation sim = openSim();
        MechSupport power = power();
        sim.setCommandPowers(List.of(power));

        sim.getCommandPowerService().requestActivation(power.id, 15, 15, List.of(HOUND_AT));
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertSame(ArmorDropPayload.INSTANCE, mission.payload);
        assertEquals(1, mission.armorDrop.length);
        assertSame(HOUND, mission.armorDrop[0].mech());
        assertEquals(LIGHT_CRADLE, sim.world().airframe(sim.getAirEntityIds()[0]));
        assertTrue(power.called(HOUND_AT));
        assertFalse(power.called(APC_AT));
        assertEquals(2, sim.getCommandPowerService().getChargesRemaining(power.id),
                "two machines still aboard");
    }

    @Test
    void aMachineNoCarrierLiftsIsRefusedWithItsReasonAndNothingIsSpent() {
        BattleSimulation sim = openSim();
        MechSupport power = power();
        sim.setCommandPowers(List.of(power));
        float points = sim.getCommandPowerService().getCommandPoints();

        assertSame(LiftBudget.Refusal.NO_LIFT,
                power.picker(sim, List.of()).get(BULWARK_AT).refusal());
        assertFalse(power.canTarget(15, 15, sim, List.of(BULWARK_AT)));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15, List.of(BULWARK_AT));
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0, sim.getAirEntityIds().length);
        assertFalse(power.called(BULWARK_AT));
        assertTrue(sim.getCommandPowerService().getCommandPoints() >= points);
        assertEquals(0f, sim.getCommandPowerService().getCooldownRemaining(power.id), 0f);
    }

    @Test
    void theNextCallWaitsOutTheCooldownAndACalledMachineCannotComeAgain() {
        BattleSimulation sim = openSim();
        MechSupport power = MechSupport.configured(List.of(HOUND), List.of(APC), null, true);
        sim.setCommandPowers(List.of(power));
        CommandPowerService service = sim.getCommandPowerService();

        service.requestActivation(power.id, 15, 15, List.of(0));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(service.getCooldownRemaining(power.id) > 0f);
        assertFalse(service.canActivate(power), "on cooldown");

        assertFalse(power.canTarget(15, 15, sim, List.of(0)), "the Hound has been called");
        assertTrue(power.picker(sim, null).get(0).called());
        assertEquals(List.of(1), power.defaultSelection(sim));
    }

    private static MechSupport power() {
        return MechSupport.configured(List.of(HOUND, BULWARK), List.of(APC), LIGHT_CRADLE, false);
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(30, 30));
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MILITIA, 25, 25));
        return sim;
    }
}
