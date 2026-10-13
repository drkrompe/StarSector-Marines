package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.comms.BattleCommsFeed;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHudGeometryTest {
    @Test
    void commsMovesBelowSubtitleAndClaimsItsOpaqueNoticeWithoutClaimingWorld() {
        Context context = new Context();
        BattleCommsFeed feed = new BattleCommsFeed();
        BattleCommsPanel panel = new BattleCommsPanel(context, feed);
        assertEquals(context.subtitleBottom, panel.noticeBottom());
        assertFalse(panel.blocksWorldPointer(960, 900));
        feed.post(new BattleCommsFeed.Notice("NOTICE", "Hold the gate.", BattleCommsFeed.Tone.STATUS, 4f));
        var bounds = panel.noticeBounds();
        assertEquals(680f, bounds.x());
        assertEquals(560f, bounds.width());
        assertEquals(context.subtitleBottom - 12f, bounds.y() + bounds.height());
        for (String kind : List.of("isLMBDownEvent", "isLMBUpEvent", "isRMBDownEvent", "isRMBUpEvent", "isMouseScrollEvent")) {
            AtomicBoolean inside = new AtomicBoolean();
            AtomicBoolean outside = new AtomicBoolean();
            panel.handleInput(List.of(event(kind, 960, (int) bounds.y() + 10, inside), event(kind, 100, 500, outside)));
            assertTrue(inside.get(), kind);
            assertFalse(outside.get(), kind);
        }
        context.subtitleBottom -= 100;
        assertEquals(bounds.y() - 100f, panel.noticeBounds().y());
        feed.update(5f);
        assertFalse(panel.blocksWorldPointer(960, panel.noticeBottom() - 10));
        assertEquals(context.subtitleBottom, panel.noticeBottom());
    }

    @Test
    void expandedToolsUseCurrentBoundsBeforeRenderAndCollapseWhenSpaceRunsOut() {
        Context context = new Context();
        DebugTogglesPanel tools = new DebugTogglesPanel(context);
        for (int i = 0; i < 10; i++) tools.addAction("Action " + i, () -> {});
        float top = DebugHudLayout.top(context);
        AtomicBoolean clicked = new AtomicBoolean();
        tools.handleInput(List.of(event("isLMBDownEvent", 1000, (int) top - 8, clicked)));
        assertTrue(clicked.get());
        assertEquals(top - 244f, tools.occupiedBottom());
        assertEquals(tools.occupiedBottom(), DebugHudLayout.clusterBottom(context, null, tools));
        AtomicBoolean wheel = new AtomicBoolean();
        tools.handleInput(List.of(event("isMouseScrollEvent", 1000, (int) top - 50, wheel)));
        assertTrue(wheel.get());
        context.bottomTop = top - 100f;
        assertEquals(top - 24f, tools.occupiedBottom());
        assertFalse(tools.blocksWorldPointer(1000, top - 50));
        context.bottomTop = top - 10f;
        assertFalse(tools.isVisible());
        assertFalse(tools.blocksWorldPointer(1000, top - 8));
    }

    @Test
    void tallLeftSelectionDoesNotPreventCenterToolsFromExpanding() {
        Context context = new Context();
        context.bottomTop = 408f;
        context.spanBottom = 154f;
        DebugTogglesPanel tools = new DebugTogglesPanel(context);
        for (int i = 0; i < 20; i++) tools.addAction("Action " + i, () -> {});
        float top = DebugHudLayout.top(context);
        tools.handleInput(List.of(event("isLMBDownEvent", 1000, (int) top - 8, new AtomicBoolean())));
        assertEquals(top - 464f, tools.occupiedBottom());
        assertEquals(656f, context.requestedX);
        assertEquals(608f, context.requestedWidth);
    }
    @Test
    void shipDiagnosticsUseCurrentDeveloperBottomAndCenteredBoundedWidth() {
        Context context = new Context();
        var before = WarshipDebugPanel.bounds(context, 60f);
        assertEquals(640f, before.x());
        assertEquals(640f, before.width());
        assertEquals(context.developerBottom - 12f, before.y() + before.height());
        context.developerBottom -= 80;
        var after = WarshipDebugPanel.bounds(context, 60f);
        assertEquals(before.y() - 80f, after.y());
        assertFalse(after.contains(before.x() + 20, before.y() + 50));
    }

    @Test
    void hiddenDeveloperHudRemovesTheCenteredToolsAndLeavesTheSwitchToBringThemBack() {
        Context context = new Context();
        DebugTogglesPanel tools = new DebugTogglesPanel(context);
        DebugHudSwitchPanel hudSwitch = new DebugHudSwitchPanel(context);
        assertTrue(tools.isVisible());
        context.debugHud = false;
        assertFalse(tools.isVisible());
        assertFalse(tools.blocksWorldPointer(1000, DebugHudLayout.top(context) - 8));
        assertTrue(hudSwitch.isVisible());

        int inX = (int) (DebugHudSwitchPanel.x(context.layout) + 4f);
        int inY = (int) (DebugHudSwitchPanel.y(context.layout) + 4f);
        AtomicBoolean missed = new AtomicBoolean();
        hudSwitch.handleInput(List.of(event("isLMBDownEvent", inX - 200, inY, missed)));
        assertFalse(missed.get());
        assertFalse(context.debugHud);
        assertFalse(hudSwitch.blocksWorldPointer(inX - 200, inY));

        AtomicBoolean clicked = new AtomicBoolean();
        assertTrue(hudSwitch.blocksWorldPointer(inX, inY));
        hudSwitch.handleInput(List.of(event("isLMBDownEvent", inX, inY, clicked)));
        assertTrue(clicked.get());
        assertTrue(context.debugHud);
        assertTrue(tools.isVisible());

        hudSwitch.handleInput(List.of(event("isLMBDownEvent", inX, inY, new AtomicBoolean())));
        assertFalse(context.debugHud);
    }

    private static InputEventAPI event(String kind, int x, int y, AtomicBoolean consumed) {
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(), new Class<?>[]{InputEventAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "consume" -> { consumed.set(true); yield null; }
                    case "isConsumed" -> consumed.get();
                    case "getX" -> x;
                    case "getY" -> y;
                    default -> method.getReturnType() == boolean.class ? method.getName().equals(kind) : null;
                });
    }

    private static final class Context implements BattleUiContext {
        float subtitleBottom = 952f, stackBottom = 864f, developerBottom = 600f, bottomTop = 300f;
        float spanBottom = Float.NaN, requestedX, requestedWidth;
        boolean debugHud = true;
        final BattleLayout layout = new BattleLayout((PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(),
                new Class<?>[]{PositionAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> 1920f;
                    case "getHeight" -> 1080f;
                    case "getX", "getY" -> 0f;
                    default -> null;
                }), 100, 100);
        @Override public BattleSimulation getSim() { return null; }
        @Override public BattleFixture getBattleFixture() { return null; }
        @Override public BattleCamera getCamera() { return null; }
        @Override public BattleLayout getLayout() { return layout; }
        @Override public Selection getSelection() { return null; }
        @Override public HighlightOverlay getHighlights() { return null; }
        @Override public boolean debugHudVisible() { return debugHud; }
        @Override public void setDebugHudVisible(boolean visible) { debugHud = visible; }
        @Override public float topCenterContentBottom() { return subtitleBottom; }
        @Override public float topCenterStackBottom() { return stackBottom; }
        @Override public float developerHudBottom() { return developerBottom; }
        @Override public float bottomHudTop() { return bottomTop; }
        @Override public float bottomHudTop(float x, float width) {
            requestedX = x; requestedWidth = width;
            return Float.isNaN(spanBottom) ? bottomTop : spanBottom;
        }
    }
}
