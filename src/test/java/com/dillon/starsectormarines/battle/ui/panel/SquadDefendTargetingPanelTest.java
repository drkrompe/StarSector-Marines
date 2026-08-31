package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SquadDefendTargetingPanelTest {

    @Test
    void armedSquadClaimsTheNextWorldClickAndQueuesItsArea() {
        Fixture fixture = fixture();
        SquadDefendTargetingPanel panel = new SquadDefendTargetingPanel(fixture);
        panel.toggle(fixture.squad.id);

        int screenX = Math.round(fixture.camera.cellToScreenX(30.5f));
        int screenY = Math.round(fixture.camera.cellToScreenY(20.5f));
        panel.handleInput(List.of(leftClick(screenX, screenY)));
        fixture.sim.getSquadMoveOrderSystem().tick(fixture.sim);

        ActiveDefendAreaOrder order = assertInstanceOf(ActiveDefendAreaOrder.class,
                fixture.sim.getSquadMoveOrderService().activeOrder(fixture.squad.id));
        assertEquals(30, order.destinationX());
        assertEquals(20, order.destinationY());
        assertEquals(Selection.NONE, panel.targetingSquadId());
    }

    private static InputEventAPI leftClick(int x, int y) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(
                InputEventAPI.class.getClassLoader(),
                new Class<?>[]{InputEventAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isLMBDownEvent" -> true;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "isConsumed" -> consumed[0];
                    case "consume" -> {
                        consumed[0] = true;
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static Fixture fixture() {
        int width = 64;
        int height = 48;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, height));
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 5, 20).squad(squadId).primaryWeapon(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)));
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        Selection selection = new Selection();
        selection.selectSquad(squadId);
        BattleCamera camera = new BattleCamera(width, height);
        camera.setViewport(0f, 0f, 640f, 480f, 10f);
        return new Fixture(sim, squad, selection, camera);
    }

    private record Fixture(BattleSimulation sim, Squad squad,
                           Selection selection, BattleCamera camera)
            implements BattleUiContext {
        @Override public BattleSimulation getSim() { return sim; }
        @Override public BattleFixture getBattleFixture() { return null; }
        @Override public BattleCamera getCamera() { return camera; }
        @Override public BattleLayout getLayout() { return null; }
        @Override public Selection getSelection() { return selection; }
        @Override public HighlightOverlay getHighlights() { return null; }
    }
}
