package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitRenderHostProfileTest {

    @Test
    void embeddedHostCanSuppressBattleHpBarsWithoutForkingUnitRendering() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        CellTopology topology = new CellTopology(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation simulation = new BattleSimulation(grid, topology)) {
            simulation.spawn(new EntitySpec(
                    "marine", Faction.MARINE, UnitType.MARINE, 4, 4));
            simulation.getFogOfWar().tick(0, simulation.getRoster());
            BattleCamera camera = new BattleCamera(8, 8);
            camera.setViewport(0f, 0f, 400f, 400f, 50f);
            UnitRenderService service = new UnitRenderService(new BattleSprites());

            DrawList battle = collect(service, simulation, camera,
                    BattleRenderHostProfile.STANDALONE_BATTLE);
            DrawList embedded = collect(service, simulation, camera,
                    BattleRenderHostProfile.EMBEDDED_SCENE);

            assertTrue(battle.count(RenderLayer.UNITS)
                            > embedded.count(RenderLayer.UNITS),
                    "standalone battle decorators should add HP-bar commands");
        }
    }

    private static DrawList collect(UnitRenderService service,
                                    BattleSimulation simulation,
                                    BattleCamera camera,
                                    BattleRenderHostProfile profile) {
        RenderContext context = new RenderContext(simulation, camera, null,
                1f, 0f, false, new HighlightOverlay(), new Selection(),
                profile);
        DrawList result = new DrawList();
        service.collect(context, result);
        return result;
    }
}
