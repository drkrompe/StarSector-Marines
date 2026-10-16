package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who flies a drop: the company's cradled boat when it can, the synthetic Valkyrie when it cannot. */
class CompanyLiftDropTest {

    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void theCompanyBoatFliesACallItCanLiftAndTheSyntheticCarrierTheRest() {
        List<LiftClass> hounds = List.of(LiftClass.LIGHT, LiftClass.LIGHT);
        List<LiftClass> mixed = List.of(LiftClass.LIGHT, LiftClass.HEAVY);

        assertSame(CRADLED_BUFFALO, AirDeliveryPower.chooseCarrier(
                hounds, CRADLED_BUFFALO, true, true, ShuttleType.VALKYRIE));
        assertSame(ShuttleType.VALKYRIE, AirDeliveryPower.chooseCarrier(
                mixed, CRADLED_BUFFALO, true, true, ShuttleType.VALKYRIE));
        assertSame(ShuttleType.VALKYRIE, AirDeliveryPower.chooseCarrier(
                hounds, CRADLED_BUFFALO, false, true, ShuttleType.VALKYRIE),
                "a boat already lost or in the air does not fly again");
        assertNull(AirDeliveryPower.chooseCarrier(
                mixed, CRADLED_BUFFALO, true, false, ShuttleType.VALKYRIE));
    }

    @Test
    void aCradledBuffaloFliesOneHoundPerSortie() {
        BattleSimulation sim = openSim();
        MechSupport power = MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND),
                MechDeploymentSpec.standard(MechVariant.HOUND)), CRADLED_BUFFALO, false);
        sim.setCommandPowers(List.of(power));

        assertTrue(power.canTarget(15, 15, sim));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        long boat = sim.getAirEntityIds()[0];
        assertEquals(CRADLED_BUFFALO, sim.world().airframe(boat),
                "the company's own boat is in the air, so its loss is the company's");
        ShuttleMission mission = sim.world().mission(boat);
        assertEquals(2, mission.totalCycles, "one trip per machine");
        assertEquals(1, mission.marinesRemaining);
    }

    @Test
    void aBulwarkIsRefusedWithoutHeavyLiftAndNoChargeIsSpent() {
        BattleSimulation sim = openSim();
        MechSupport power = MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.BULWARK)), CRADLED_BUFFALO, false);
        sim.setCommandPowers(List.of(power));

        assertFalse(power.canTarget(15, 15, sim));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0, sim.getAirEntityIds().length);
        assertEquals(1, sim.getCommandPowerService().getChargesRemaining(power.id));
    }

    @Test
    void withShipLiftBehindItABulwarkStillComesDownInTheValkyrie() {
        BattleSimulation sim = openSim();
        MechSupport power = MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.BULWARK)), CRADLED_BUFFALO, true);
        sim.setCommandPowers(List.of(power));

        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        assertSame(ShuttleType.VALKYRIE, sim.world().airframe(sim.getAirEntityIds()[0]));
    }

    @Test
    void armourSupportFliesTheHeavyApcInACradledBoat() {
        BattleSimulation sim = openSim();
        VehicleSupport power = VehicleSupport.configured(List.of(), CRADLED_BUFFALO, true);
        sim.setCommandPowers(List.of(power));

        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        assertEquals(CRADLED_BUFFALO, sim.world().airframe(sim.getAirEntityIds()[0]));
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
