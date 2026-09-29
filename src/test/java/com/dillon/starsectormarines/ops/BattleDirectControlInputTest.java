package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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

    @Test
    void weaponKeysAreDebouncedAndAcceptedSelectionRequiresNewFirePressWithoutDroppingMovement() {
        BattleDirectControlInput input = active();
        process(input, List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_W),
                event(Kind.LEFT_DOWN, 70, 60, 0)));
        int[] calls = {0}, choice = {-1};
        var keys = List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_3),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_3));
        input.process(BattleDirectControlInput.capture(keys), () -> {}, () -> {}, selection -> {
            calls[0]++; choice[0] = selection; return true;
        }, NO_CHROME);
        assertEquals(1, calls[0]);
        assertEquals(2, choice[0]);
        assertTrue(keys.stream().allMatch(InputEventAPI::isConsumed));
        assertEquals(1f, input.intent(camera(), false, NO_CHROME).moveY());
        assertFalse(input.intent(camera(), false, NO_CHROME).firing());
        process(input, List.of(event(Kind.LEFT_DOWN, 70, 60, 0)));
        assertTrue(input.intent(camera(), false, NO_CHROME).firing());

        var release = event(Kind.UP, 0, 0, Keyboard.KEY_3);
        var captured = BattleDirectControlInput.capture(List.of(release));
        release.consume();
        input.process(captured, () -> {}, () -> {}, selection -> false, NO_CHROME);
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_3))),
                () -> {}, () -> {}, selection -> { calls[0]++; return false; }, NO_CHROME);
        assertEquals(2, calls[0], "chrome-consumed key release still re-arms the selector");
        assertTrue(input.intent(camera(), false, NO_CHROME).firing(), "rejected selection does not cancel fire");
    }

    @Test
    void weaponNumberMappingPreservesSlotsAndInactiveOrChromeConsumedKeysDoNotChoose() {
        BattleDirectControlInput input = active();
        int[] expectedKeys = {Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4};
        for (int i = 0; i < expectedKeys.length; i++) {
            int expected = i;
            input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, expectedKeys[i]))),
                    () -> {}, () -> {}, selection -> { assertEquals(expected, selection); return true; }, NO_CHROME);
        }
        input.exit(1f);
        var key = event(Kind.DOWN, 0, 0, Keyboard.KEY_1);
        input.process(BattleDirectControlInput.capture(List.of(key)), () -> {}, () -> {},
                selection -> { fail("Inactive selector"); return false; }, NO_CHROME);
        assertFalse(key.isConsumed());
        input.enter(1f);
        var captured = BattleDirectControlInput.capture(List.of(key));
        key.consume();
        input.process(captured, () -> {}, () -> {},
                selection -> { fail("Chrome claimed selector key"); return false; }, NO_CHROME);
    }

    @Test
    void validatedSelectionFallbackCannotRepublishHeldFireAndRetainsMovementUntilNewPress() {
        BattleDirectControlInput input = active();
        input.syncSelectedWeapon(2);
        process(input, List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_W),
                event(Kind.LEFT_DOWN, 70, 60, 0)));
        assertTrue(input.intent(camera(), false, NO_CHROME).firing());
        input.syncSelectedWeapon(2);
        assertTrue(input.intent(camera(), false, NO_CHROME).firing(), "unchanged equipment keeps the held trigger");
        // Simulation validated a missing selected mount and returned to ALL.
        input.syncSelectedWeapon(0);
        ManualIntent afterAdvance = input.intent(camera(), false, NO_CHROME);
        assertFalse(afterAdvance.firing(), "post-advance submission must not restore the stale trigger");
        assertEquals(1f, afterAdvance.moveY());
        input.syncSelectedWeapon(0);
        assertFalse(input.intent(camera(), false, NO_CHROME).firing());
        process(input, List.of(event(Kind.LEFT_DOWN, 70, 60, 0)));
        input.syncSelectedWeapon(0);
        assertTrue(input.intent(camera(), false, NO_CHROME).firing(), "a new press may use ALL");
    }

    @Test
    void explicitSelectionSyncDoesNotCancelAFreshMousePressLaterInTheEventBatch() {
        BattleDirectControlInput input = active();
        input.process(BattleDirectControlInput.capture(List.of(
                event(Kind.DOWN, 0, 0, Keyboard.KEY_3), event(Kind.LEFT_DOWN, 70, 60, 0))),
                () -> {}, () -> {}, selection -> { input.syncSelectedWeapon(selection); return true; }, NO_CHROME);
        input.syncSelectedWeapon(2);
        assertTrue(input.intent(camera(), false, NO_CHROME).firing());
        input.exit(1f);
        input.enter(1f);
        process(input, List.of(event(Kind.LEFT_DOWN, 70, 60, 0)));
        input.syncSelectedWeapon(0);
        assertTrue(input.intent(camera(), false, NO_CHROME).firing(), "entry initializes the new session to ALL");
    }

    @Test
    void abilityKeysMapToOneShotRequestsAndConsumedReleaseRearmsWithoutDroppingPrimaryControls() {
        BattleDirectControlInput input = active();
        process(input, List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_W),
                event(Kind.LEFT_DOWN, 70, 60, 0)));
        List<DirectControlAbility> requests = new ArrayList<>();
        Predicate<DirectControlAbility> accept = ability -> { requests.add(ability); return true; };
        var presses = List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_E),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_E), event(Kind.DOWN, 0, 0, Keyboard.KEY_G),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_G));
        input.process(BattleDirectControlInput.capture(presses), () -> {}, () -> {},
                selection -> false, accept, NO_CHROME);
        assertEquals(List.of(DirectControlAbility.SHIELD, DirectControlAbility.SMOKE), requests);
        assertTrue(presses.stream().allMatch(InputEventAPI::isConsumed));
        assertEquals(1f, input.intent(camera(), false, NO_CHROME).moveY());
        assertTrue(input.intent(camera(), false, NO_CHROME).firing());

        var release = event(Kind.UP, 0, 0, Keyboard.KEY_E);
        var captured = BattleDirectControlInput.capture(List.of(release));
        release.consume();
        input.process(captured, () -> {}, () -> {}, selection -> false, accept, NO_CHROME);
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_E))),
                () -> {}, () -> {}, selection -> false, accept, NO_CHROME);
        assertEquals(List.of(DirectControlAbility.SHIELD, DirectControlAbility.SMOKE,
                DirectControlAbility.SHIELD), requests);
    }

    @Test
    void rejectedAbilityPressNeverQueuesLaterAfterPauseChromeOrViewportUnblocks() {
        for (int blockedBy = 0; blockedBy < 4; blockedBy++) {
            BattleDirectControlInput input = active();
            BattleCamera camera = camera();
            boolean[] paused = {blockedBy == 0}, chrome = {blockedBy == 1};
            if (blockedBy != 3) process(input, List.of(event(Kind.MOVE,
                    blockedBy == 2 ? 250 : 70, 60, 0)));
            int[] attempts = {0}, accepted = {0};
            BiPredicate<Float, Float> chromeGate = (x, y) -> chrome[0];
            Predicate<DirectControlAbility> request = ability -> {
                attempts[0]++;
                if (input.blocked(camera, paused[0], chromeGate)) return false;
                accepted[0]++;
                return true;
            };
            input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                    () -> {}, () -> {}, selection -> false, request, chromeGate);
            assertEquals(0, accepted[0]);
            if (paused[0]) {
                input.speedChanged(0f);
                input.speedChanged(1f);
            }
            paused[0] = chrome[0] = false;
            process(input, List.of(event(Kind.MOVE, 70, 60, 0)));
            input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                    () -> {}, () -> {}, selection -> false, request, chromeGate);
            assertEquals(1, attempts[0], "unblocking cannot replay a held rejected press");
            assertEquals(0, accepted[0]);
            input.process(BattleDirectControlInput.capture(List.of(event(Kind.UP, 0, 0, Keyboard.KEY_G),
                            event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                    () -> {}, () -> {}, selection -> false, request, chromeGate);
            assertEquals(1, accepted[0], "a fresh press after release is eligible");
        }
    }

    @Test
    void inactiveAndConsumedAbilityPressCannotActivateOnRepeatOrSessionReentry() {
        BattleDirectControlInput input = new BattleDirectControlInput();
        int[] requests = {0};
        Predicate<DirectControlAbility> request = ability -> { requests[0]++; return true; };
        var inactivePress = event(Kind.DOWN, 0, 0, Keyboard.KEY_E);
        input.process(BattleDirectControlInput.capture(List.of(inactivePress)), () -> {}, () -> {},
                selection -> false, request, NO_CHROME);
        assertFalse(inactivePress.isConsumed());
        input.enter(1f);
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_E))),
                () -> {}, () -> {}, selection -> false, request, NO_CHROME);
        assertEquals(0, requests[0]);

        var chromePress = event(Kind.DOWN, 0, 0, Keyboard.KEY_G);
        var captured = BattleDirectControlInput.capture(List.of(chromePress));
        chromePress.consume();
        input.process(captured, () -> {}, () -> {}, selection -> false, request, NO_CHROME);
        input.exit(1f);
        input.enter(1f);
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                () -> {}, () -> {}, selection -> false, request, NO_CHROME);
        assertEquals(0, requests[0], "handback does not turn held chrome input into a new press");
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.UP, 0, 0, Keyboard.KEY_G),
                        event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                () -> {}, () -> {}, selection -> false, request, NO_CHROME);
        assertEquals(1, requests[0]);
    }

    @Test
    void observedPhysicalReleaseRearmsAfterDroppedKeyupWhileHeldObservationDoesNot() {
        BattleDirectControlInput input = active();
        List<DirectControlAbility> requests = new ArrayList<>();
        Predicate<DirectControlAbility> request = ability -> { requests.add(ability); return true; };
        var held = List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_E),
                event(Kind.DOWN, 0, 0, Keyboard.KEY_G));
        input.process(BattleDirectControlInput.capture(held), () -> {}, () -> {},
                selection -> false, request, NO_CHROME);
        input.reconcileAbilityKeyReleases(true, true);
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_E),
                        event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                () -> {}, () -> {}, selection -> false, request, NO_CHROME);
        assertEquals(List.of(DirectControlAbility.SHIELD, DirectControlAbility.SMOKE), requests);

        // Focus was lost and both keyups were dropped by the host.
        input.exit(1f);
        input.enter(1f);
        input.reconcileAbilityKeyReleases(false, false);
        assertEquals(2, requests.size(), "a release observation cannot activate equipment");
        input.process(BattleDirectControlInput.capture(List.of(event(Kind.DOWN, 0, 0, Keyboard.KEY_E),
                        event(Kind.DOWN, 0, 0, Keyboard.KEY_G))),
                () -> {}, () -> {}, selection -> false, request, NO_CHROME);
        assertEquals(List.of(DirectControlAbility.SHIELD, DirectControlAbility.SMOKE,
                DirectControlAbility.SHIELD, DirectControlAbility.SMOKE), requests);
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
