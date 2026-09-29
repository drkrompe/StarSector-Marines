package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class BattleDirectControlOverlayTest {
    @Test
    void retainedButtonFitsItsViewportAndOnlyEligibleActionFires() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            AtomicInteger actions = new AtomicInteger();
            try (MarkupInstance markup = fixture(Path.of("mod"), false, enabled,
                    actions::incrementAndGet)) {
                UiDocument document = document(markup);
                var button = markup.requireElement("battle-direct-control-toggle");
                var box = button.box().borderBox();
                assertEquals(!enabled, button.disabled());
                assertTrue(box.x() >= 0 && box.right() <= 440f);
                assertTrue(box.y() >= 0 && box.bottom() <= BattleDirectControlOverlay.DOCUMENT_HEIGHT);
                assertTrue(markup.requireElement("battle-direct-control-hint")
                        .box().borderBox().bottom() <= BattleDirectControlOverlay.DOCUMENT_HEIGHT);
                assertEquals(0f, markup.requireElement("battle-direct-control-pause").box().borderBox().width(),
                        "the hidden pause control has no painted or pointer area");
                assertTrue(markup.requireElement("battle-direct-control-pause").disabled());
                document.pointerDown(box.x() + 20, box.y() + 12);
                document.pointerUp(box.x() + 20, box.y() + 12);
                assertEquals(enabled ? 1 : 0, actions.get());
            }
        }
    }

    @Test
    void controlPlateClearsBothSelectionPanelsAndOtherChromeAcrossHostScales() throws Exception {
        for (int[] size : List.of(new int[]{1744, 938}, new int[]{1920, 1080},
                new int[]{1366, 768}, new int[]{1280, 720})) {
            for (float uiScale : new float[]{1f, 1.25f, 1.5f}) {
                OverlayLayout layout = layout(size[0], size[1], uiScale);
                UiViewport plate = layout.control();
                String context = size[0] + "x" + size[1] + " UI " + uiScale;
                assertDisjoint(plate, layout.squad(), context + " infantry and hover");
                assertDisjoint(plate, layout.mech(), context + " mech/lance");
                assertDisjoint(plate, layout.powers(), context + " power tray");
                assertDisjoint(plate, layout.retreat(), context + " retreat confirmation");
                float commanderBottom = layout.host().screenY() + layout.host().height()
                        - 12f - BattleHudOverlay.COMMAND_ONLY_HEIGHT * plate.documentScale();
                assertTrue(plate.screenY() + plate.height() <= commanderBottom,
                        context + " commander clearance");
                assertTrue(plate.screenX() >= layout.host().screenX());
                assertTrue(plate.screenX() + plate.width()
                        <= layout.host().screenX() + layout.host().width());
                AtomicInteger actions = new AtomicInteger();
                try (MarkupInstance markup = fixture(Path.of("mod"), false, true,
                        true, actions::incrementAndGet)) {
                    UiDocument document = document(markup);
                    document.layout(plate.documentWidth(), plate.documentHeight());
                    var button = markup.requireElement("battle-direct-control-toggle").box().borderBox();
                    float screenX = plate.screenXFor(button.x() + button.width() / 2f);
                    float screenY = plate.screenTopFor(button.y() + button.height() / 2f);
                    document.pointerDown(plate.documentX(screenX), plate.documentY(screenY));
                    document.pointerUp(plate.documentX(screenX), plate.documentY(screenY));
                    assertEquals(1, actions.get(), context + " control remains clickable");
                    assertTrue(markup.requireElement("battle-direct-control-hint")
                            .box().borderBox().bottom() <= plate.documentHeight());
                }
            }
        }
    }

    static record OverlayLayout(UiViewport host, UiViewport control, UiViewport squad,
                                UiViewport mech, UiViewport powers, UiViewport retreat,
                                UiViewport hud, UiViewport activeControl) { }

    /** Uses the exact production panel bounds, including host offset and user-scale conversion. */
    static OverlayLayout layout(int physicalWidth, int physicalHeight, float uiScale) {
        return withHost(physicalWidth, physicalHeight, uiScale, position -> new OverlayLayout(
                MarineOpsUiViewport.from(position), BattleDirectControlOverlay.viewport(position),
                BattleSquadOverlay.viewport(position), BattleMechOverlay.viewport(position),
                BattlePowerOverlay.viewport(position, 5, true),
                BattleRetreatOverlay.viewport(position, BattleRetreatOverlayModel.Presentation.CONFIRM),
                BattleHudOverlay.viewport(position, new BattleHudOverlayModel.Presentation(false, true)),
                BattleDirectControlOverlay.viewport(position, true)));
    }

    private static <T> T withHost(int physicalWidth, int physicalHeight, float uiScale,
                                 Function<PositionAPI, T> check) {
        SettingsAPI previous = Global.getSettings();
        SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getScreenScaleMult")) return uiScale;
                    throw new AssertionError("Unexpected settings query: " + method.getName());
                });
        PositionAPI position = (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(),
                new Class<?>[]{PositionAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> 37f;
                    case "getY" -> 29f;
                    case "getWidth" -> physicalWidth / uiScale;
                    case "getHeight" -> physicalHeight / uiScale;
                    default -> throw new AssertionError("Unexpected position query: " + method.getName());
                });
        try {
            Global.setSettings(settings);
            return check.apply(position);
        } finally {
            Global.setSettings(previous);
        }
    }

    private static void assertDisjoint(UiViewport a, UiViewport b, String context) {
        assertTrue(a.screenX() + a.width() <= b.screenX() || b.screenX() + b.width() <= a.screenX()
                || a.screenY() + a.height() <= b.screenY() || b.screenY() + b.height() <= a.screenY(), context);
    }

    static MarkupInstance fixture(Path modRoot, boolean active, boolean enabled,
                                  Runnable action) throws Exception {
        return fixture(modRoot, active, enabled, false, action);
    }

    static MarkupInstance fixture(Path modRoot, boolean active, boolean enabled,
                                  boolean mech, Runnable action) throws Exception {
        return fixture(modRoot, active, enabled, mech, false, action);
    }

    static MarkupInstance fixture(Path modRoot, boolean active, boolean enabled,
                                  boolean mech, boolean vehicle, Runnable action) throws Exception {
        return fixture(modRoot, active, enabled, mech, vehicle, false, action, () -> {});
    }

    static MarkupInstance fixture(Path modRoot, boolean active, boolean enabled,
                                  boolean mech, boolean vehicle, boolean paused,
                                  Runnable action, Runnable pauseAction) throws Exception {
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(modRoot.resolve(path)),
                List.of(BattleDirectControlOverlay.COMPONENT_PATH));
        loader.reload();
        return loader.build(new Reactor(), BattleDirectControlOverlay.COMPONENT, Map.of(
                "label", active ? "Return to command [C / Esc]" : "Control selected unit [C]",
                "hint", active ? vehicle ? "heavy_apc-1  |  W/S drive | A/D steer | Mouse aim | LMB fire"
                        : mech ? "Bulwark 1  |  WASD pivot/walk | Mouse aim | LMB direct mounts"
                        : "Rhea Voss  |  WASD move | Mouse aim | Hold LMB fire"
                        : enabled ? "Direct control uses pause or 1x speed"
                        : "Select a ready Marine, combat Mech, or deployed APC",
                "disabled", !enabled, "toggle", action,
                "pauseLabel", paused ? "1x" : "Pause",
                "pauseClasses", active ? "direct-pause" : "direct-pause direct-pause-hidden",
                "pauseDisabled", !active,
                "togglePause", pauseAction));
    }

    @Test
    void activeHudStaysBottomCenteredInsideTheHostAcrossScales() throws Exception {
        for (int[] size : List.of(new int[]{1744, 938}, new int[]{1920, 1080},
                new int[]{1366, 768}, new int[]{1280, 720})) {
            for (float uiScale : new float[]{1f, 1.25f, 1.5f}) {
                OverlayLayout layout = layout(size[0], size[1], uiScale);
                UiViewport plate = layout.activeControl();
                assertEquals(layout.host().screenX() + layout.host().width() / 2f,
                        plate.screenX() + plate.width() / 2f, .001f);
                assertEquals(layout.host().screenY() + 12f, plate.screenY(), .001f);
                assertTrue(plate.screenX() >= layout.host().screenX());
                assertTrue(plate.screenX() + plate.width() <= layout.host().screenX() + layout.host().width());
                assertTrue(plate.screenY() + plate.height() <= layout.host().screenY() + layout.host().height());
                assertEquals(BattleDirectControlOverlay.ACTION_WIDTH, plate.documentWidth(), .001f);
                assertEquals(BattleDirectControlOverlay.ACTION_HEIGHT, plate.documentHeight(), .001f);
            }
        }
    }

    @Test
    void hudDocksAtTopOnlyWhenTheControlledBodyWouldOverlapItsBottomBounds() {
        for (int[] size : List.of(new int[]{1744, 938}, new int[]{1280, 720})) {
            for (float scale : new float[]{1f, 1.25f, 1.5f}) {
                withHost(size[0], size[1], scale, position -> {
                    UiViewport host = MarineOpsUiViewport.from(position);
                    UiViewport bottom = BattleDirectControlOverlay.viewport(position, true);
                    float x = bottom.screenX() + bottom.width() / 2f;
                    float y = bottom.screenY() + bottom.height() / 2f;
                    float radius = 24f * scale;
                    UiViewport docked = BattleDirectControlOverlay.actionViewport(position, x, y, radius);
                    assertEquals(host.screenY() + host.height() - 12f - bottom.height(),
                            docked.screenY(), .001f);
                    assertEquals(bottom.screenX(), docked.screenX(), .001f);
                    assertEquals(bottom.width(), docked.width(), .001f);
                    assertTrue(y + radius < docked.screenY(), "docking leaves the body visible");
                    assertEquals(bottom, BattleDirectControlOverlay.actionViewport(position,
                            x, bottom.screenY() + bottom.height() + radius + 9f, radius));
                    assertEquals(bottom, BattleDirectControlOverlay.actionViewport(position,
                            bottom.screenX() - radius - 9f, y, radius));
                    return null;
                });
            }
        }
    }

    static UiDocument document(MarkupInstance markup) {
        UiDocument document = new UiDocument(markup.root());
        for (var style : markup.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(BattleDirectControlOverlay.DOCUMENT_WIDTH, BattleDirectControlOverlay.DOCUMENT_HEIGHT);
        return document;
    }
}
