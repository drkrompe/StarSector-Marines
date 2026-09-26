package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugTogglesPanelTest {

    @Test
    void deactivatingInputReleasesDialBeforeItsHiddenMouseRelease() {
        PositionAPI position = (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(),
                new Class<?>[]{PositionAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f;
                    case "getWidth" -> 1744f;
                    case "getHeight" -> 938f;
                    default -> throw new AssertionError(method.getName());
                });
        BattleLayout layout = new BattleLayout(position, 100, 100);
        BattleUiContext context = (BattleUiContext) Proxy.newProxyInstance(BattleUiContext.class.getClassLoader(),
                new Class<?>[]{BattleUiContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLayout")) return layout;
                    throw new AssertionError(method.getName());
                });
        double[] value = {0};
        DebugTogglesPanel panel = new DebugTogglesPanel(context)
                .addDial("Test", () -> value[0], next -> value[0] = next, 0, 1);
        // No GL paint is required: the initial cached panel origin is (0, 0).
        panel.handleInput(List.of(pointer("down", 162, 12))); // Expand header.
        panel.handleInput(List.of(pointer("down", 162, 12))); // Begin dragging first dial.
        assertEquals(.5, value[0], 1e-9);

        panel.deactivateInput();
        panel.handleInput(List.of(pointer("move", 218, 12), pointer("up", 218, 12)));
        assertEquals(.5, value[0], 1e-9);
    }

    private static InputEventAPI pointer(String kind, int x, int y) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(),
                new Class<?>[]{InputEventAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> x;
                    case "getY" -> y;
                    case "isConsumed" -> consumed[0];
                    case "consume" -> { consumed[0] = true; yield null; }
                    case "isLMBDownEvent" -> kind.equals("down");
                    case "isLMBUpEvent" -> kind.equals("up");
                    case "isMouseMoveEvent" -> kind.equals("move");
                    default -> throw new AssertionError(method.getName());
                });
    }

    @Test
    void dialMapsTrackPositionToRangeAndClampsOutside() {
        assertEquals(0.0, DebugTogglesPanel.dialValueAt(90f, 100f, 200f, 0.0, 0.02), 1e-9);
        assertEquals(0.01, DebugTogglesPanel.dialValueAt(150f, 100f, 200f, 0.0, 0.02), 1e-9);
        assertEquals(0.02, DebugTogglesPanel.dialValueAt(220f, 100f, 200f, 0.0, 0.02), 1e-9);
    }

    @Test
    void poweredDialPreservesEndpointsAndExpandsLowRange() {
        assertEquals(0.0, DebugTogglesPanel.dialValueAt(100f, 100f, 200f, 0.0, 5.0, 3.0), 1e-9);
        assertEquals(0.625, DebugTogglesPanel.dialValueAt(150f, 100f, 200f, 0.0, 5.0, 3.0), 1e-9);
        assertEquals(5.0, DebugTogglesPanel.dialValueAt(200f, 100f, 200f, 0.0, 5.0, 3.0), 1e-9);
        assertEquals(0.5, DebugTogglesPanel.dialFractionForValue(0.625, 0.0, 5.0, 3.0), 1e-9);
    }
}
