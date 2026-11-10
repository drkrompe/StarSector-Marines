package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.mech.MechRefit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A company vehicle lands with the Mech Lab's upgrades over it, as the company's mechs do. */
class ConvoyRefitTest {

    private static final MechRefit REFIT = new MechRefit(1.06f, 1.20f, 1.10f, 1.25f);

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(16, 16));
    }

    private static VehicleMission mission() {
        return new VehicleMission(new float[]{0.5f, 1.5f}, new float[]{0.5f, 0.5f},
                new float[]{1.5f, 0.5f}, new float[]{0.5f, 0.5f}, 0f, VehicleType.MEDIUM_TANK.capacity);
    }

    @Test void theUpgradesScaleArmorCoolingTraverseAndAim() {
        BattleSimulation sim = openSim();
        VehicleDeploymentSpec stock = new VehicleDeploymentSpec("v-1", "Anvil", VehicleType.MEDIUM_TANK);
        long plain = sim.convoy().spawn(stock, Faction.MARINE, mission());
        long upgraded = sim.convoy().spawn(stock.withRefit(REFIT), Faction.MARINE, mission());

        assertEquals(sim.world().maxArmor(plain) * 1.10f, sim.world().maxArmor(upgraded), 0.01f);
        GroundTurret plainGun = sim.convoy().turret(plain);
        GroundTurret upgradedGun = sim.convoy().turret(upgraded);
        assertEquals(plainGun.reservoir.dissipationPerSecond() * 1.20f, upgradedGun.reservoir.dissipationPerSecond(), 1e-4f);
        assertEquals(1f, plainGun.traverseMult);
        assertEquals(1.25f, upgradedGun.traverseMult);
        assertEquals(1f, sim.convoy().refitAccuracy(plain));
        assertEquals(1.06f, sim.convoy().refitAccuracy(upgraded));
    }
}
