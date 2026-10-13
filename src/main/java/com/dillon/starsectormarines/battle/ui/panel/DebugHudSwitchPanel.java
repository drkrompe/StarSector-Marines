package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

/**
 * The one control that stays on screen when the developer HUD is hidden: a small
 * bottom-right button that shows or hides the tick profile, DEBUG tools, squad
 * plan readout and ship diagnostics together. The state itself belongs to the
 * {@link BattleUiContext}; this panel only flips it.
 */
@DebugOnly
public final class DebugHudSwitchPanel implements HudPanel {

    static final float W = 76f;
    static final float H = 22f;

    private static final Color BG = new Color(0x10, 0x14, 0x1E, 0xCC);
    private static final Color BG_HOVER = new Color(0x24, 0x30, 0x44, 0xE6);
    private static final Color BORDER_OFF = new Color(0x4A, 0x55, 0x60);
    private static final Color BORDER_ON = new Color(0x70, 0xD8, 0x90);
    private static final Color TEXT_OFF = new Color(0x8C, 0x98, 0xA4);
    private static final Color TEXT_ON = new Color(0xE6, 0xEC, 0xF2);

    private final BattleUiContext ctx;
    private final BitmapFont font = Fonts.ORBITRON_12_BOLD;
    private boolean hover;

    public DebugHudSwitchPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    static float x(BattleLayout layout) { return layout.gridX + layout.gridW - BattleLayout.PAD - W; }

    static float y(BattleLayout layout) { return layout.gridY + BattleLayout.PAD; }

    private boolean inside(float px, float py) {
        BattleLayout layout = ctx.getLayout();
        if (layout == null) return false;
        float x = x(layout), y = y(layout);
        return px >= x && px < x + W && py >= y && py < y + H;
    }

    @Override
    public boolean isVisible() {
        return ctx.getLayout() != null;
    }

    @Override
    public void update(float dt) {}

    @Override
    public void render(float alphaMult) {
        BattleLayout layout = ctx.getLayout();
        if (layout == null) return;
        boolean on = ctx.debugHudVisible();
        float x = x(layout), y = y(layout);
        font.ensureLoaded();
        HudDraw.prepBlend();
        HudDraw.filledRect(x, y, W, H, hover ? BG_HOVER : BG, alphaMult);
        HudDraw.borderRect(x, y, W, H, on ? BORDER_ON : BORDER_OFF, alphaMult);
        HudDraw.filledRect(x + 7f, y + H * .5f - 3f, 6f, 6f, on ? BORDER_ON : BORDER_OFF, alphaMult);
        font.drawString("DEBUG", x + 19f, y + H - 5f, on ? TEXT_ON : TEXT_OFF, alphaMult);
    }

    @Override
    public boolean blocksWorldPointer(float x, float y) {
        return isVisible() && inside(x, y);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || !isVisible()) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isMouseMoveEvent()) {
                hover = inside(e.getX(), e.getY());
                continue;
            }
            if (!(e.isLMBDownEvent() || e.isLMBUpEvent() || e.isRMBDownEvent()
                    || e.isRMBUpEvent() || e.isMouseScrollEvent())) continue;
            if (!inside(e.getX(), e.getY())) continue;
            if (e.isLMBDownEvent()) ctx.setDebugHudVisible(!ctx.debugHudVisible());
            e.consume();
        }
    }
}
