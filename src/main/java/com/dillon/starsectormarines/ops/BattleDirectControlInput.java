package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Held manual controls at the host boundary. No simulation or selection authority. */
final class BattleDirectControlInput {
    private boolean active;
    private boolean north, south, west, east, firing;
    private boolean pointerKnown;
    private float pointerX, pointerY;
    private float previousSpeed;
    private boolean toggleHeld;

    /** Read host values before retained chrome consumes and invalidates them. */
    static List<Sample> capture(List<InputEventAPI> events) {
        if (events == null) return List.of();
        List<Sample> samples = new ArrayList<>(events.size());
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            boolean pointer = event.isMouseMoveEvent() || event.isLMBDownEvent()
                    || event.isLMBUpEvent() || event.isRMBDownEvent()
                    || event.isRMBUpEvent() || event.isMouseScrollEvent();
            samples.add(new Sample(event, event.isKeyDownEvent(), event.isKeyUpEvent(),
                    event.isKeyDownEvent() || event.isKeyUpEvent() ? event.getEventValue() : 0,
                    pointer, pointer ? event.getX() : 0f, pointer ? event.getY() : 0f,
                    event.isLMBDownEvent(), event.isLMBUpEvent(),
                    event.isRMBDownEvent() || event.isRMBUpEvent()));
        }
        return samples;
    }

    /** Chrome acts first; releases still clear our held state even when claimed there. */
    void process(List<Sample> samples, Runnable toggle, Runnable exit,
                 BiPredicate<Float, Float> chrome) {
        for (Sample sample : samples) {
            if (sample.pointer) {
                pointerKnown = true;
                pointerX = sample.x;
                pointerY = sample.y;
            }
            if (sample.keyUp) setMovement(sample.key, false);
            if (sample.keyUp && sample.key == Keyboard.KEY_C) toggleHeld = false;
            if (sample.primaryUp) firing = false;
            if (active && sample.keyDown && sample.key == Keyboard.KEY_ESCAPE) {
                exit.run();
                sample.event.consume();
                continue;
            }
            if (sample.event.isConsumed()) continue;
            if (sample.keyDown && sample.key == Keyboard.KEY_C) {
                if (!toggleHeld) toggle.run();
                toggleHeld = true;
                sample.event.consume();
                continue;
            }
            if (!active) continue;
            if ((sample.keyDown || sample.keyUp) && movementKey(sample.key)) {
                setMovement(sample.key, sample.keyDown);
                sample.event.consume();
            }
            // Arrow panning and world orders have no owner while following a Marine.
            if ((sample.keyDown || sample.keyUp) && arrowKey(sample.key)) {
                sample.event.consume();
            }
            if (sample.primaryDown) firing = !chrome.test(pointerX, pointerY);
            if (sample.primaryDown || sample.primaryUp || sample.secondary) {
                sample.event.consume();
            }
        }
    }

    ManualIntent intent(BattleCamera camera, boolean paused,
                        BiPredicate<Float, Float> chrome) {
        if (!active || camera == null || !pointerKnown) return ManualIntent.NEUTRAL;
        boolean blocked = blocked(camera, paused, chrome);
        float dx = blocked ? 0f : (east ? 1f : 0f) - (west ? 1f : 0f);
        float dy = blocked ? 0f : (north ? 1f : 0f) - (south ? 1f : 0f);
        return new ManualIntent(dx, dy, camera.screenToCellX(pointerX),
                camera.screenToCellY(pointerY), !blocked && firing);
    }

    float enter(float currentSpeed) {
        previousSpeed = currentSpeed;
        active = true;
        releaseHeld();
        return currentSpeed == 0f ? 0f : 1f;
    }

    float exit(float currentSpeed) {
        if (!active) return currentSpeed;
        active = false;
        releaseHeld();
        return previousSpeed;
    }

    /** An explicit time choice replaces the entry rate that would otherwise be restored. */
    float speedChanged(float requested) {
        float effective = requested == 0f ? 0f : 1f;
        previousSpeed = effective;
        releaseHeld();
        return effective;
    }

    boolean blocked(BattleCamera camera, boolean paused, BiPredicate<Float, Float> chrome) {
        return paused || camera == null || !pointerKnown
                || !camera.containsScreen(pointerX, pointerY) || chrome.test(pointerX, pointerY);
    }

    void releaseHeld() { north = south = west = east = firing = toggleHeld = false; }
    boolean active() { return active; }

    private void setMovement(int key, boolean down) {
        if (key == Keyboard.KEY_W) north = down;
        if (key == Keyboard.KEY_S) south = down;
        if (key == Keyboard.KEY_A) west = down;
        if (key == Keyboard.KEY_D) east = down;
    }

    private static boolean movementKey(int key) {
        return key == Keyboard.KEY_W || key == Keyboard.KEY_A
                || key == Keyboard.KEY_S || key == Keyboard.KEY_D;
    }

    private static boolean arrowKey(int key) {
        return key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN
                || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT;
    }

    record Sample(InputEventAPI event, boolean keyDown, boolean keyUp, int key,
                  boolean pointer, float x, float y, boolean primaryDown,
                  boolean primaryUp, boolean secondary) {}
}
