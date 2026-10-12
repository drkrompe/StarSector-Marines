package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.fs.starfarer.api.ui.PositionAPI;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleCommsPanelTest {
    @Test void discoveryMarkerOutlivesItsNoticeAndClearsWhenTheFrameIsLost() {
        var grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        try (var sim = new BattleSimulation(grid, new CellTopology(40, 40))) {
            sim.installWaylineHound(20, 20);
            PositionAPI position = (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(),
                    new Class<?>[]{PositionAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getX", "getY" -> 0f;
                        case "getWidth" -> 1920f;
                        case "getHeight" -> 1080f;
                        default -> throw new AssertionError(method.getName());
                    });
            var layout = new BattleLayout(position, 40, 40);
            BattleUiContext context = (BattleUiContext) Proxy.newProxyInstance(BattleUiContext.class.getClassLoader(),
                    new Class<?>[]{BattleUiContext.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getLayout" -> layout;
                        case "getSim" -> sim;
                        default -> throw new AssertionError(method.getName());
                    });
            var panel = new BattleCommsPanel(context);
            assertFalse(panel.isVisible());
            sim.spawn(new EntitySpec("company", Faction.MARINE, UnitType.MARINE, 22, 20));
            sim.spawn(new EntitySpec("raider", Faction.DEFENDER, UnitType.MILITIA, 25, 20));
            sim.getWaylineHound().tick(sim);
            panel.update(0);
            panel.update(13);
            assertTrue(panel.isVisible(), "expired comms must not hide an unfinished objective");
            sim.takeOffTheField(sim.getWaylineHound().frameId());
            sim.getWaylineHound().tick(sim);
            panel.update(0);
            panel.update(13);
            assertFalse(panel.isVisible(), "destroyed equipment must not retain a recovery marker");
        }
    }
}
