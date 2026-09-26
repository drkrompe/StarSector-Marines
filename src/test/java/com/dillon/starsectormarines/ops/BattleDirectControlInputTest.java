package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;

class BattleDirectControlInputTest {
    private static final BiPredicate<Float, Float> NO_CHROME = (x, y) -> false;

    @Test
    void heldControlsUseCurrentCameraWorldCoordinatesAndOwnMovementKeys() {
        BattleDirectControlInput input = active();
        BattleCamera camera = camera();
        List<InputEventAPI> events = List.of(event(Kind.MOVE, 70, 60, 0),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_W),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_D), event(Kind.LEFT_DOWN, 70, 60, 0));
        process(input, events);
        ManualIntent first = input.intent(camera, false, NO_CHROME);
        assertEquals(1f, first.moveX());
        assertEquals(1f, first.moveY());
        assertTrue(first.firing());
        assertTrue(events.get(1).isConsumed());
        assertTrue(events.get(2).isConsumed());
        camera.centerOn(65f, 65f);
        ManualIntent next = input.intent(camera, false, NO_CHROME);
        assertEquals(camera.screenToCellX(70), next.aimX());
        assertEquals(camera.screenToCellY(60), next.aimY());
        assertNotEquals(first.aimX(), next.aimX());
    }

    @Test
    void releasesConsumedByChromeStillClearHeldControlsWithoutReadingConsumedHostValues() {
        BattleDirectControlInput input = active();
        process(input, List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_W),
                event(Kind.LEFT_DOWN, 70, 60, 0)));
        List<InputEventAPI> released = List.of(event(Kind.UP, 0, 0, Keyboard.KEY_W),
                event(Kind.LEFT_UP, 20, 20, 0));
        var captured = BattleDirectControlInput.capture(released);
        released.forEach(InputEventAPI::consume);
        input.process(captured, () -> {}, () -> {}, NO_CHROME);
        ManualIntent intent = input.intent(camera(), false, NO_CHROME);
        assertEquals(0f, intent.moveY());
        assertFalse(intent.firing());
    }

    @Test
    void chromeAndPauseNeutralizeMovementAndFireWhilePreservingWorldAim() {
        BattleDirectControlInput input = active();
        process(input, List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_A),
                event(Kind.LEFT_DOWN, 70, 60, 0)));
        assertNeutral(input.intent(camera(), false, (x, y) -> x > 50));
        assertNeutral(input.intent(camera(), true, NO_CHROME));
        assertTrue(input.blocked(camera(), true, NO_CHROME));
        assertTrue(Float.isFinite(input.intent(camera(), true, NO_CHROME).aimX()));
        assertTrue(input.intent(camera(), false, NO_CHROME).firing());
    }

    @Test
    void chromeClickCannotStartFiringWhenPointerReturnsToWorld() {
        BattleDirectControlInput input = active();
        InputEventAPI down = event(Kind.LEFT_DOWN, 70, 60, 0);
        var captured = BattleDirectControlInput.capture(List.of(down));
        down.consume();
        input.process(captured, () -> {}, () -> {}, NO_CHROME);
        process(input, List.of(event(Kind.MOVE, 40, 40, 0)));
        assertFalse(input.intent(camera(), false, NO_CHROME).firing());
    }

    @Test
    void exitRestoresPriorSpeedIncludingPauseAndClearsHeldState() {
        for (float speed : new float[]{0f, 1f, 2f, 4f}) {
            BattleDirectControlInput input = new BattleDirectControlInput();
            assertEquals(speed == 0f ? 0f : 1f, input.enter(speed));
            process(input, List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_W),
                    event(Kind.LEFT_DOWN, 70, 60, 0)));
            assertEquals(speed, input.exit(1f));
            input.enter(1f);
            assertNeutral(input.intent(camera(), false, NO_CHROME));
        }
    }

    @Test
    void escapeExitsEvenWhenChromeHasConsumedIt() {
        BattleDirectControlInput input = active();
        InputEventAPI escape = event(Kind.DOWN, 0, 0, Keyboard.KEY_ESCAPE);
        var captured = BattleDirectControlInput.capture(List.of(escape));
        escape.consume();
        input.process(captured, () -> {}, () -> input.exit(1f), NO_CHROME);
        assertFalse(input.active());
    }

    @Test
    void explicitTimeChoiceReplacesTheRateRestoredOnExit() {
        BattleDirectControlInput input = new BattleDirectControlInput();
        input.enter(4f);
        assertEquals(0f, input.speedChanged(0f));
        assertEquals(0f, input.exit(0f));
        input.enter(2f);
        assertEquals(1f, input.speedChanged(4f));
        assertEquals(1f, input.exit(1f));
    }

    @Test
    void toggleKeyRepeatCannotOscillateControlMode() {
        BattleDirectControlInput input = new BattleDirectControlInput();
        int[] toggles = {0};
        Runnable toggle = () -> { toggles[0]++; input.enter(1f); };
        input.process(BattleDirectControlInput.capture(List.of(
                event(Kind.DOWN, 0, 0, Keyboard.KEY_C),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_C))), toggle, () -> {}, NO_CHROME);
        assertEquals(1, toggles[0]);
        input.process(BattleDirectControlInput.capture(List.of(
                event(Kind.UP, 0, 0, Keyboard.KEY_C),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_C))), toggle, () -> {}, NO_CHROME);
        assertEquals(2, toggles[0]);
    }

    private static void assertNeutral(ManualIntent intent) {
        assertEquals(0f, intent.moveX());
        assertEquals(0f, intent.moveY());
        assertFalse(intent.firing());
    }

    private static BattleDirectControlInput active() {
        BattleDirectControlInput input = new BattleDirectControlInput();
        input.enter(1f);
        return input;
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(100, 100);
        camera.setViewport(0, 0, 200, 200, 4);
        return camera;
    }

    private static void process(BattleDirectControlInput input, List<InputEventAPI> events) {
        input.process(BattleDirectControlInput.capture(events), () -> {}, () -> {}, NO_CHROME);
    }

    private enum Kind { DOWN, UP, MOVE, LEFT_DOWN, LEFT_UP }

    private static InputEventAPI event(Kind kind, int x, int y, int key) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(),
                new Class<?>[]{InputEventAPI.class}, (proxy, method, args) -> {
                    if (consumed[0] && (method.getName().equals("getX")
                            || method.getName().equals("getY")
                            || method.getName().equals("getEventValue"))) {
                        throw new AssertionError("Host value read after consume");
                    }
                    return switch (method.getName()) {
                        case "isKeyDownEvent" -> kind == Kind.DOWN;
                        case "isKeyUpEvent" -> kind == Kind.UP;
                        case "isMouseMoveEvent" -> kind == Kind.MOVE;
                        case "isLMBDownEvent" -> kind == Kind.LEFT_DOWN;
                        case "isLMBUpEvent" -> kind == Kind.LEFT_UP;
                        case "getX" -> x;
                        case "getY" -> y;
                        case "getEventValue" -> key;
                        case "isConsumed" -> consumed[0];
                        case "consume" -> { consumed[0] = true; yield null; }
                        default -> method.getReturnType() == boolean.class ? false : null;
                    };
                });
    }
}
