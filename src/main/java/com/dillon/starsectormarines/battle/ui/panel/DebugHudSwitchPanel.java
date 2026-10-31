package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The one control that stays on screen when the developer HUD is hidden: a small
 * bottom-right button that shows or hides the tick profile, DEBUG tools, squad
 * plan readout and ship diagnostics together. The state itself belongs to the
 * {@link BattleUiContext}; this panel only flips it.
 *
 * <p>In a player build it is the CHEAT switch: given cheats, turning it on opens their buttons
 * in a row directly to its left, the first nearest, instead of the developer panels.
 */
@DebugOnly
public final class DebugHudSwitchPanel implements HudPanel {

    static final float W = 76f;
    static final float H = 22f;
    static final float CHEAT_GAP = 6f;
    static final float CHEAT_PAD = 12f;

    private static final Color BG = new Color(0x10, 0x14, 0x1E, 0xCC);
    private static final Color BG_HOVER = new Color(0x24, 0x30, 0x44, 0xE6);
    private static final Color BORDER_OFF = new Color(0x4A, 0x55, 0x60);
    private static final Color BORDER_ON = new Color(0x70, 0xD8, 0x90);
    private static final Color TEXT_OFF = new Color(0x8C, 0x98, 0xA4);
    private static final Color TEXT_ON = new Color(0xE6, 0xEC, 0xF2);

    private final BattleUiContext ctx;
    private final BitmapFont font = Fonts.ORBITRON_12_BOLD;
    /** The player build's cheats; empty in a dev build, whose cheats live in the developer panel. */
    private final List<Cheat> cheats;
    private boolean hover;
    private int hoverCheat = -1;

    /** One cheat button: its label, its action, and whether it is offered right now. */
    public record Cheat(String label, Runnable action, BooleanSupplier offered) { }

    public DebugHudSwitchPanel(BattleUiContext ctx) {
        this(ctx, List.of());
    }

    public DebugHudSwitchPanel(BattleUiContext ctx, List<Cheat> cheats) {
        this.ctx = ctx;
        this.cheats = List.copyOf(cheats);
    }

    static float x(BattleLayout layout) { return layout.gridX + layout.gridW - BattleLayout.PAD - W; }

    public static float y(BattleLayout layout) { return layout.gridY + BattleLayout.PAD; }

    private boolean inside(float px, float py) {
        BattleLayout layout = ctx.getLayout();
        if (layout == null) return false;
        float x = x(layout), y = y(layout);
        return px >= x && px < x + W && py >= y && py < y + H;
    }

    /** The cheats offered now, nearest the switch first; none while the switch is off. */
    private List<Cheat> shown() {
        if (cheats.isEmpty() || !ctx.debugHudVisible() || ctx.getLayout() == null) return List.of();
        List<Cheat> out = new ArrayList<>();
        for (Cheat cheat : cheats) if (cheat.offered().getAsBoolean()) out.add(cheat);
        return out;
    }

    private float cheatWidth(Cheat cheat) {
        return font.measureWidth(cheat.label()) + 2f * CHEAT_PAD;
    }

    /** Left edge of the {@code index}th shown cheat, counting leftward from the switch. */
    public float cheatX(BattleLayout layout, int index) {
        List<Cheat> shown = shown();
        float left = x(layout);
        for (int i = 0; i <= index; i++) left -= CHEAT_GAP + cheatWidth(shown.get(i));
        return left;
    }

    private int cheatAt(float px, float py) {
        BattleLayout layout = ctx.getLayout();
        if (layout == null || py < y(layout) || py >= y(layout) + H) return -1;
        List<Cheat> shown = shown();
        for (int i = 0; i < shown.size(); i++) {
            float left = cheatX(layout, i);
            if (px >= left && px < left + cheatWidth(shown.get(i))) return i;
        }
        return -1;
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
        font.drawString(DevConfig.DEV_MODE ? "DEBUG" : Strings.get("cheatsToggle"), x + 19f, y + H - 5f, on ? TEXT_ON : TEXT_OFF, alphaMult);
        List<Cheat> shown = shown();
        for (int i = 0; i < shown.size(); i++) {
            float cx = cheatX(layout, i), cw = cheatWidth(shown.get(i));
            HudDraw.filledRect(cx, y, cw, H, hoverCheat == i ? BG_HOVER : BG, alphaMult);
            HudDraw.borderRect(cx, y, cw, H, BORDER_ON, alphaMult);
            font.drawString(shown.get(i).label(), cx + CHEAT_PAD, y + H - 5f, TEXT_ON, alphaMult);
        }
    }

    @Override
    public boolean blocksWorldPointer(float x, float y) {
        return isVisible() && (inside(x, y) || cheatAt(x, y) >= 0);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || !isVisible()) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isMouseMoveEvent()) {
                hover = inside(e.getX(), e.getY());
                hoverCheat = cheatAt(e.getX(), e.getY());
                continue;
            }
            if (!(e.isLMBDownEvent() || e.isLMBUpEvent() || e.isRMBDownEvent()
                    || e.isRMBUpEvent() || e.isMouseScrollEvent())) continue;
            int cheat = cheatAt(e.getX(), e.getY());
            if (cheat >= 0) {
                if (e.isLMBDownEvent()) shown().get(cheat).action().run();
                e.consume();
                continue;
            }
            if (!inside(e.getX(), e.getY())) continue;
            if (e.isLMBDownEvent()) ctx.setDebugHudVisible(!ctx.debugHudVisible());
            e.consume();
        }
    }
}
