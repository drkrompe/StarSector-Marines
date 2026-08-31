package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldPickerTest {

    @Test
    void smallPointerMovementRetainsClickSelectionForAnyFaction() {
        try (Fixture fixture = fixture()) {
            Unit defender = fixture.spawnSquad(Faction.DEFENDER, UnitType.MILITIA,
                    12, 12);
            int x = fixture.screenX(defender.entityId);
            int y = fixture.screenY(defender.entityId);

            fixture.picker.handleInput(List.of(
                    event(Kind.LEFT_DOWN, x, y),
                    event(Kind.MOVE, x + 2, y + 1),
                    event(Kind.LEFT_UP, x + 2, y + 1)));

            assertEquals(defender.squadId, fixture.selection.getSelectedSquadId());
            assertEquals(defender.entityId,
                    fixture.selection.getSelectedUnitEntityId());
        }
    }

    @Test
    void dragChoosesOnePlayerSquadNearestTheBoxCenter() {
        try (Fixture fixture = fixture()) {
            fixture.spawnSquad(Faction.MARINE, UnitType.MARINE, 7, 10);
            Unit right = fixture.spawnSquad(Faction.MARINE, UnitType.MARINE,
                    14, 10);
            fixture.spawnSquad(Faction.DEFENDER, UnitType.MILITIA, 12, 10);

            fixture.dragCells(5, 8, 20, 12);

            assertEquals(right.squadId, fixture.selection.getSelectedSquadId());
            assertEquals(right.entityId,
                    fixture.selection.getSelectedUnitEntityId());
        }
    }

    @Test
    void dragPinsTheExactPlayerMech() {
        try (Fixture fixture = fixture()) {
            Unit mech = fixture.spawnSquad(Faction.MARINE, UnitType.HEAVY_MECH,
                    24, 18);

            fixture.dragCells(22, 16, 27, 21);

            assertEquals(mech.squadId, fixture.selection.getSelectedSquadId());
            assertEquals(mech.entityId,
                    fixture.selection.getSelectedUnitEntityId());
        }
    }

    @Test
    void emptyDragClearsThePreviousSelection() {
        try (Fixture fixture = fixture()) {
            Unit marine = fixture.spawnSquad(Faction.MARINE, UnitType.MARINE,
                    5, 5);
            fixture.selection.selectUnit(marine.squadId, marine.entityId);

            fixture.dragCells(30, 30, 36, 36);

            assertEquals(Selection.NONE, fixture.selection.getSelectedSquadId());
            assertEquals(0L, fixture.selection.getSelectedUnitEntityId());
        }
    }

    private static InputEventAPI event(Kind kind, int x, int y) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(
                InputEventAPI.class.getClassLoader(),
                new Class<?>[]{InputEventAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isLMBDownEvent" -> kind == Kind.LEFT_DOWN;
                    case "isLMBUpEvent" -> kind == Kind.LEFT_UP;
                    case "isMouseMoveEvent" -> kind == Kind.MOVE;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "isConsumed" -> consumed[0];
                    case "consume" -> {
                        consumed[0] = true;
                        yield null;
                    }
                    case "toString" -> kind.name();
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private enum Kind {
        LEFT_DOWN,
        LEFT_UP,
        MOVE
    }

    private record Unit(int squadId, long entityId) {}

    private static Fixture fixture() {
        int size = 40;
        NavigationGrid grid = new NavigationGrid(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation simulation = new BattleSimulation(
                grid, new CellTopology(size, size));
        Selection selection = new Selection();
        BattleCamera camera = new BattleCamera(size, size);
        camera.setViewport(0f, 0f, 400f, 400f, 10f);
        return new Fixture(simulation, selection, camera);
    }

    private static final class Fixture implements BattleUiContext, AutoCloseable {
        private final BattleSimulation simulation;
        private final Selection selection;
        private final BattleCamera camera;
        private final WorldPicker picker;

        private Fixture(BattleSimulation simulation, Selection selection,
                        BattleCamera camera) {
            this.simulation = simulation;
            this.selection = selection;
            this.camera = camera;
            this.picker = new WorldPicker(this);
        }

        private Unit spawnSquad(Faction faction, UnitType type, int x, int y) {
            int squadId = simulation.mintSquad(faction, type);
            long entity = simulation.spawn(new EntitySpec(
                    faction.name() + "-" + squadId, faction, type, x, y)
                    .squad(squadId));
            return new Unit(squadId, entity);
        }

        private int screenX(long entity) {
            return Math.round(camera.cellToScreenX(
                    simulation.world().renderX(entity)));
        }

        private int screenY(long entity) {
            return Math.round(camera.cellToScreenY(
                    simulation.world().renderY(entity)));
        }

        private void dragCells(float minX, float minY, float maxX, float maxY) {
            int startX = Math.round(camera.cellToScreenX(minX));
            int startY = Math.round(camera.cellToScreenY(minY));
            int endX = Math.round(camera.cellToScreenX(maxX));
            int endY = Math.round(camera.cellToScreenY(maxY));
            picker.handleInput(List.of(
                    event(Kind.LEFT_DOWN, startX, startY),
                    event(Kind.MOVE, endX, endY),
                    event(Kind.LEFT_UP, endX, endY)));
        }

        @Override public BattleSimulation getSim() { return simulation; }
        @Override public BattleFixture getBattleFixture() { return null; }
        @Override public BattleCamera getCamera() { return camera; }
        @Override public BattleLayout getLayout() { return null; }
        @Override public Selection getSelection() { return selection; }
        @Override public HighlightOverlay getHighlights() { return null; }

        @Override public void close() { simulation.close(); }
    }
}
