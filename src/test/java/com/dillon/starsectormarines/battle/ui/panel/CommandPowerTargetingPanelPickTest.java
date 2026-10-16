package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The targeting panel owns the armed Mech Support's pick: default, toggles, and disarm. */
class CommandPowerTargetingPanelPickTest {

    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void armingMechSupportStartsFromTheDefaultPickAndTogglesOnlyCallableEntries() {
        BattleSimulation sim = openSim();
        MechSupport mech = MechSupport.configured(List.of(
                        MechDeploymentSpec.standard(MechVariant.HOUND),
                        MechDeploymentSpec.standard(MechVariant.BULWARK)),
                List.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC)),
                CRADLED_BUFFALO, false);
        sim.setCommandPowers(List.of(mech, new ReconPing()));
        CommandPowerTargetingPanel panel = new CommandPowerTargetingPanel(context(sim));

        panel.toggle(MechSupport.ID);
        assertEquals(MechSupport.ID, panel.targetingPowerId());
        assertEquals(List.of(0, 2), panel.selection());
        assertEquals(3, panel.pickerRows().size());

        panel.togglePick(1);
        assertEquals(List.of(0, 2), panel.selection(), "the Bulwark has no heavy lift");
        panel.togglePick(2);
        assertEquals(List.of(0), panel.selection());
        assertFalse(panel.pickerRows().get(2).selected());
        panel.togglePick(2);
        assertEquals(List.of(0, 2), panel.selection());
        panel.togglePick(0);
        panel.togglePick(2);
        assertEquals(List.of(), panel.selection());
        assertFalse(mech.canTarget(15, 15, sim, panel.selection()),
                "an empty pick stays armed rather than spending a call");
        assertTrue(mech.canTarget(15, 15, sim, List.of(0)));

        panel.cancel();
        assertNull(panel.selection());
        assertTrue(panel.pickerRows().isEmpty());

        panel.toggle(ReconPing.ID);
        assertEquals(ReconPing.ID, panel.targetingPowerId());
        assertNull(panel.selection(), "a power without a pick carries none");
        assertTrue(panel.pickerRows().isEmpty());
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

    private static BattleUiContext context(BattleSimulation sim) {
        return new BattleUiContext() {
            @Override public BattleSimulation getSim() { return sim; }
            @Override public BattleFixture getBattleFixture() { return null; }
            @Override public BattleCamera getCamera() { return null; }
            @Override public BattleLayout getLayout() { return null; }
            @Override public Selection getSelection() { return null; }
            @Override public HighlightOverlay getHighlights() { return null; }
        };
    }
}
