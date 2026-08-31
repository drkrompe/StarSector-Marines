package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraControlsTest {

    @Test
    void stationaryRightGestureClicksWithoutPanning() {
        BattleCamera camera = camera();
        CameraControls controls = new CameraControls(true);
        List<float[]> clicks = new ArrayList<>();
        List<InputEventAPI> events = List.of(
                event(Kind.RIGHT_DOWN, 50, 50),
                event(Kind.MOVE, 52, 51),
                event(Kind.RIGHT_UP, 52, 51));

        controls.process(events, camera, CameraControls.PointerSpace.SCREEN,
                (x, y) -> clicks.add(new float[]{x, y}));

        assertEquals(1, clicks.size());
        assertEquals(52f, clicks.get(0)[0]);
        assertEquals(51f, clicks.get(0)[1]);
        assertEquals(50f, camera.panCellX());
        assertEquals(50f, camera.panCellY());
    }

    @Test
    void rightGestureBeyondThresholdPansWithoutClicking() {
        BattleCamera camera = camera();
        CameraControls controls = new CameraControls(true);
        List<float[]> clicks = new ArrayList<>();

        controls.process(List.of(
                        event(Kind.RIGHT_DOWN, 50, 50),
                        event(Kind.MOVE, 60, 50),
                        event(Kind.RIGHT_UP, 60, 50)),
                camera, CameraControls.PointerSpace.SCREEN,
                (x, y) -> clicks.add(new float[]{x, y}));

        assertEquals(0, clicks.size());
        assertEquals(47.5f, camera.panCellX(), 0.001f);
        assertEquals(50f, camera.panCellY(), 0.001f);
    }

    @Test
    void cameraOnlyViewsRetainImmediateRightDrag() {
        BattleCamera camera = camera();
        CameraControls controls = new CameraControls();

        controls.process(List.of(
                event(Kind.RIGHT_DOWN, 50, 50),
                event(Kind.MOVE, 52, 50)), camera);

        assertEquals(49.5f, camera.panCellX(), 0.001f);
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(100, 100);
        camera.setViewport(0f, 0f, 200f, 200f, 4f);
        return camera;
    }

    private static InputEventAPI event(Kind kind, int x, int y) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(
                InputEventAPI.class.getClassLoader(),
                new Class<?>[]{InputEventAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isRMBDownEvent" -> kind == Kind.RIGHT_DOWN;
                    case "isRMBUpEvent" -> kind == Kind.RIGHT_UP;
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
        RIGHT_DOWN,
        RIGHT_UP,
        MOVE
    }
}
