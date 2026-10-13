package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.ops.BattleLayout;

/** Shared screen-space anchors for centered developer surfaces. */
public final class DebugHudLayout {
    private DebugHudLayout() {}
    public static float top(BattleUiContext ctx) {
        return ctx.topCenterStackBottom() - BattleLayout.CONTROLS_GAP;
    }
    public static float clusterBottom(BattleUiContext ctx, TickProfileDebugPanel profiler, DebugTogglesPanel tools) {
        float bottom = ctx.topCenterStackBottom();
        if (profiler != null && profiler.isVisible()) bottom = Math.min(bottom, profiler.occupiedBottom());
        if (tools != null && tools.isVisible()) bottom = Math.min(bottom, tools.occupiedBottom());
        return bottom;
    }
    static boolean fits(BattleUiContext ctx, float height) {
        BattleLayout layout = ctx.getLayout();
        float width = TickProfileDebugPanel.PANEL_W + TickProfileDebugPanel.DEBUG_TOOLS_GAP + DebugTogglesPanel.PANEL_W;
        return top(ctx) - height >= ctx.bottomHudTop(TickProfileDebugPanel.groupX(layout), width)
                + BattleLayout.CONTROLS_GAP;
    }
    public static float shipTop(BattleUiContext ctx) {
        return ctx.developerHudBottom() - BattleLayout.CONTROLS_GAP;
    }
}
