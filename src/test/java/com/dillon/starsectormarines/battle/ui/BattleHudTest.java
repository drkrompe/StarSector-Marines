package com.dillon.starsectormarines.battle.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleHudTest {
    @Test
    void directControlHidesStrategicPaintInputAndPointerBoundsButKeepsStateFresh() {
        BattleHud hud = new BattleHud(null);
        CountingPanel strategic = new CountingPanel(false, true);
        CountingPanel reticle = new CountingPanel(true, false);
        hud.addPanel(strategic);
        hud.addPanel(reticle);
        List<InputEventAPI> events = Collections.singletonList(null);
        hud.render(1f);
        hud.processInput(events);
        assertTrue(hud.blocksWorldPointer(20f, 20f));

        hud.setDirectControl(true);
        hud.setDirectControl(true);
        assertEquals(1, strategic.deactivations, "entry releases captures once");
        assertEquals(0, reticle.deactivations);
        hud.update(.1f);
        hud.render(1f);
        hud.processInput(events);
        assertEquals(1, strategic.paints);
        assertEquals(1, strategic.inputs);
        assertEquals(1, strategic.updates, "hidden panel snapshots still refresh");
        assertEquals(2, reticle.paints);
        assertEquals(2, reticle.inputs);
        assertFalse(hud.blocksWorldPointer(20f, 20f), "hidden panels cannot suppress world fire");

        hud.setDirectControl(false);
        hud.render(1f);
        hud.processInput(events);
        assertEquals(2, strategic.paints);
        assertEquals(2, strategic.inputs);
        assertTrue(hud.blocksWorldPointer(20f, 20f));
    }

    private static final class CountingPanel implements HudPanel {
        private final boolean manual;
        private final boolean opaque;
        int updates, paints, inputs, deactivations;
        CountingPanel(boolean manual, boolean opaque) { this.manual = manual; this.opaque = opaque; }
        @Override public void update(float dt) { updates++; }
        @Override public void render(float alpha) { paints++; }
        @Override public void handleInput(List<InputEventAPI> events) { inputs++; }
        @Override public void deactivateInput() { deactivations++; }
        @Override public boolean isVisible() { return true; }
        @Override public boolean visibleDuringDirectControl() { return manual; }
        @Override public boolean blocksWorldPointer(float x, float y) { return opaque; }
    }
}
