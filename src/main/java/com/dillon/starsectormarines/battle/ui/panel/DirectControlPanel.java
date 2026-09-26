package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

/** World-space identity bracket and point-aim cursor; the retained plate owns actions. */
public final class DirectControlPanel implements HudPanel {
    private static final Color CONTROLLED = new Color(0x6E, 0xD7, 0xFF);
    private final BattleUiContext context;

    public DirectControlPanel(BattleUiContext context) { this.context = context; }

    @Override public boolean isVisible() {
        return context.getSim() != null && context.getSim().directControl().active();
    }
    @Override public void update(float dt) {}
    @Override public void handleInput(List<InputEventAPI> events) {}

    @Override
    public void render(float alpha) {
        var sim = context.getSim();
        var camera = context.getCamera();
        if (sim == null || camera == null || !sim.directControl().active()) return;
        long id = sim.directControl().activeUnitId();
        float x = camera.cellToScreenX(sim.world().renderX(id));
        float y = camera.cellToScreenY(sim.world().renderY(id));
        float radius = Math.max(9f, camera.cellPxSize() * Math.max(0.7f, sim.physicalRadius(id) + 0.15f));
        HudDraw.prepBlend();
        HudDraw.borderRect(x - radius, y - radius, radius * 2f, radius * 2f,
                CONTROLLED, alpha);
        var aim = sim.directControl().intent();
        if (!Float.isFinite(aim.aimX()) || !Float.isFinite(aim.aimY())) return;
        float aimX = camera.cellToScreenX(aim.aimX());
        float aimY = camera.cellToScreenY(aim.aimY());
        if (!camera.containsScreen(aimX, aimY)) return;
        HudDraw.filledRect(aimX - 8f, aimY - 1f, 5f, 2f, CONTROLLED, alpha);
        HudDraw.filledRect(aimX + 3f, aimY - 1f, 5f, 2f, CONTROLLED, alpha);
        HudDraw.filledRect(aimX - 1f, aimY - 8f, 2f, 5f, CONTROLLED, alpha);
        HudDraw.filledRect(aimX - 1f, aimY + 3f, 2f, 5f, CONTROLLED, alpha);
    }
}
