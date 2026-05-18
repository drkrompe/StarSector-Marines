package com.dillon.starsectormarines.battle.ui;

import com.fs.starfarer.api.input.InputEventAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the battle screen's HUD panel list and drives update / render / input
 * for them. Z-order is list order: panels render front-to-back (so the last
 * panel added paints on top) and input goes back-to-front (topmost gets first
 * crack), so a panel that's visually on top also intercepts clicks first.
 *
 * <p>Panel visibility is self-determined via {@link HudPanel#isVisible()} —
 * see e.g. the squad overview / detail pair that swap on selection state.
 */
public final class BattleHud {

    private final BattleUiContext ctx;
    private final List<HudPanel> panels = new ArrayList<>();

    public BattleHud(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    public BattleUiContext context() {
        return ctx;
    }

    /** Adds a panel to the top of the z-stack (rendered last, input first). */
    public void addPanel(HudPanel panel) {
        panels.add(panel);
    }

    /**
     * Every panel ticks every frame regardless of visibility — panels that
     * cache a per-frame snapshot of sim state (see {@link com.dillon.starsectormarines.battle.ui.panel.SquadOverviewPanel})
     * derive their own visibility from that snapshot, so gating update() on
     * isVisible() would make them invisible-forever (the cache stays empty).
     */
    public void update(float dt) {
        for (HudPanel p : panels) {
            p.update(dt);
        }
    }

    public void render(float alphaMult) {
        for (HudPanel p : panels) {
            if (p.isVisible()) p.render(alphaMult);
        }
    }

    /**
     * Reverse-iterates so the topmost panel sees events first; each panel
     * is responsible for {@code event.consume()}-ing what it claims so later
     * handlers (and the camera in {@code BattleScreen.handleCameraInput})
     * skip already-claimed events.
     */
    public void processInput(List<InputEventAPI> events) {
        if (events == null || events.isEmpty()) return;
        for (int i = panels.size() - 1; i >= 0; i--) {
            HudPanel p = panels.get(i);
            if (!p.isVisible()) continue;
            p.handleInput(events);
        }
    }
}
