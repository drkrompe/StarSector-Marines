package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
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
                assertTrue(box.x() >= 0 && box.right() <= BattleDirectControlOverlay.DOCUMENT_WIDTH);
                assertTrue(box.y() >= 0 && box.bottom() <= BattleDirectControlOverlay.DOCUMENT_HEIGHT);
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

                }
            }
        }
    }

    @Test
    void orderBarClearsSupportAndSelectionChromeAcrossScales() {
        for (int[] size : List.of(new int[]{1920, 1080}, new int[]{1280, 720})) {
            for (float scale : new float[]{1f, 1.25f, 1.5f}) {
                withHost(size[0], size[1], scale, position -> {
                    for (boolean armed : List.of(false, true)) {
                        var powers = BattlePowerOverlay.viewport(position, 5, armed);
                        var orders = BattleOrderOverlay.viewport(position, powers.height());
                        assertDisjoint(orders, powers, "orders clear fleet support");
                        var squad = BattleSquadOverlay.viewport(position);
                        assertDisjoint(orders, new UiViewport(squad.screenX(), squad.screenY(),
                                BattleSquadOverlay.PANEL_WIDTH * squad.documentScale(), squad.height()),
                                "orders clear squad");
                        assertTrue(orders.screenX() + orders.width() <= position.getX() + position.getWidth());
                        assertDisjoint(orders, BattleMechOverlay.viewport(position), "orders clear mech");
                    }
                    return null;
                });
            }
        }
    }

    static record OverlayLayout(UiViewport host, UiViewport control, UiViewport squad,
                                UiViewport mech, UiViewport powers, UiViewport retreat,
                                UiViewport hud, UiViewport activeStatus, UiViewport activeWeapons,
                                UiViewport activeSections, UiViewport activeActions, UiViewport orders,
                                UiViewport manualObjectives) { }

    /**
     * The status panel's production bounds at a measured {@code documentHeight}: beside the body's doll,
     * or in the corner itself when there is none.
     */
    static UiViewport activeStatus(int physicalWidth, int physicalHeight, float uiScale, float documentHeight,
                                   boolean doll) {
        return withHost(physicalWidth, physicalHeight, uiScale,
                position -> BattleDirectControlOverlay.statusViewport(position, documentHeight, doll));
    }

    /** The weapon-groups panel's production bounds at a measured {@code documentHeight}. */
    static UiViewport activeWeapons(int physicalWidth, int physicalHeight, float uiScale, float documentHeight) {
        return withHost(physicalWidth, physicalHeight, uiScale,
                position -> BattleDirectControlOverlay.weaponsViewport(position, documentHeight));
    }

    /** A representative weapons panel: a three-mount mech's groups measure close to this. */
    static final float SAMPLE_WEAPONS_HEIGHT = 260f;

    /** Uses the exact production panel bounds, including host offset and user-scale conversion. */
    static OverlayLayout layout(int physicalWidth, int physicalHeight, float uiScale) {
        return layout(physicalWidth, physicalHeight, uiScale, true);
    }

    static OverlayLayout layout(int physicalWidth, int physicalHeight, float uiScale, boolean mech) {
        return withHost(physicalWidth, physicalHeight, uiScale, position -> new OverlayLayout(
                MarineOpsUiViewport.from(position), BattleDirectControlOverlay.entryViewport(position, mech),
                BattleSquadOverlay.viewport(position), BattleMechOverlay.viewport(position),
                BattlePowerOverlay.viewport(position, 5, true),
                BattleRetreatOverlay.viewport(position, 0f, 0f, BattleRetreatOverlayModel.Presentation.CONFIRM),
                BattleHudOverlay.viewport(position, new BattleHudOverlayModel.Presentation(false, true)),
                BattleDirectControlOverlay.statusViewport(position, BattleDamageDollPlate.DOCUMENT_HEIGHT, mech),
                BattleDirectControlOverlay.weaponsViewport(position, SAMPLE_WEAPONS_HEIGHT),
                BattleDirectControlOverlay.sectionsViewport(position),
                BattleDirectControlOverlay.actionsViewport(position),
                BattleOrderOverlay.viewport(position, BattlePowerOverlay.viewport(position, 5, true).height()),
                BattleManualObjectiveOverlay.viewport(position)));
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
                "label", "Take over [Tab]", "disabled", !enabled, "toggle", action));
    }

    @Test
    void manualObjectivesClearEquipmentAndSessionButtonsAcrossScales() {
        for (int[] size : List.of(new int[]{1920, 1080}, new int[]{1280, 720})) {
            for (float scale : new float[]{1f, 1.25f, 1.5f}) {
                var layout = layout(size[0], size[1], scale);
                var objectives = layout.manualObjectives();
                assertDisjoint(objectives, layout.activeStatus(), "objectives clear the status panel");
                assertDisjoint(objectives, layout.activeSections(), "objectives clear the section doll");
                assertDisjoint(objectives, layout.activeWeapons(), "objectives clear the weapon groups");
                assertDisjoint(objectives, layout.activeActions(), "objectives clear session buttons");
                assertTrue(objectives.screenY() >= layout.host().screenY());
                assertTrue(objectives.screenX() + objectives.width() <= layout.host().screenX() + layout.host().width());
            }
        }
    }

    @Test
    void activePanelsHoldTheBottomCornersAndLeaveTheCentreClear() throws Exception {
        for (int[] size : List.of(new int[]{1744, 938}, new int[]{1920, 1080},
                new int[]{1366, 768}, new int[]{1280, 720})) {
            for (float uiScale : new float[]{1f, 1.25f, 1.5f}) {
                OverlayLayout layout = layout(size[0], size[1], uiScale);
                String context = size[0] + "x" + size[1] + " UI " + uiScale;
                UiViewport host = layout.host();
                UiViewport status = layout.activeStatus();
                UiViewport sections = layout.activeSections();
                UiViewport weapons = layout.activeWeapons();
                UiViewport actions = layout.activeActions();
                float scale = status.documentScale();
                assertEquals(host.screenX() + 12f, sections.screenX(), .001f, context);
                assertEquals(host.screenY() + 12f, sections.screenY(), .001f, context);
                assertEquals(sections.screenX() + sections.width() + BattleDamageDollPlate.GAP * scale,
                        status.screenX(), .001f, context + " status stands beside the doll");
                assertEquals(host.screenY() + 12f, status.screenY(), .001f, context);
                assertEquals(BattleDirectControlOverlay.STATUS_WIDTH, status.documentWidth(), .001f, context);
                assertEquals(host.screenX() + host.width() - 12f, weapons.screenX() + weapons.width(), .001f, context);
                assertEquals(host.screenY() + 12f, weapons.screenY(), .001f, context);
                assertEquals(BattleDirectControlOverlay.WEAPONS_WIDTH, weapons.documentWidth(), .001f, context);
                assertDisjoint(status, weapons, context + " status and weapons");
                assertDisjoint(sections, weapons, context + " doll and weapons");
                for (UiViewport panel : List.of(status, sections, weapons)) {
                    assertDisjoint(panel, actions, context + " session buttons stay clear");
                    float centre = host.screenX() + host.width() / 2f;
                    assertTrue(panel.screenX() + panel.width() <= centre - 80f * scale
                            || panel.screenX() >= centre + 80f * scale, context + " the centre stays open");
                }
            }
        }
    }

    @Test
    void everyGroundBodyStandsBesideADoll() {
        for (Carrier carrier : List.of(Carrier.MARINE, Carrier.MECH, Carrier.VEHICLE)) {
            assertTrue(BattleDirectControlOverlay.hasDoll(carrier), carrier + " has a doll in the corner");
        }
        for (Carrier carrier : List.of(Carrier.NONE, Carrier.WARSHIP, Carrier.TENDER)) {
            assertFalse(BattleDirectControlOverlay.hasDoll(carrier), carrier + " keeps the corner for its status");
        }
        assertEquals(BattleDamageDollPlate.DOCUMENT_WIDTH, BattleMarineDollPlate.DOCUMENT_WIDTH,
                "the status stands in one place beside either doll");
        assertEquals(BattleDamageDollPlate.DOCUMENT_HEIGHT, BattleMarineDollPlate.DOCUMENT_HEIGHT);
    }

    @Test
    void onlyABodyInsideAPanelsBoundsMakesItStepBack() {
        UiViewport panel = new UiViewport(1548f, 12f, 360f, 260f, 1f);
        assertTrue(BattleDirectControlOverlay.overlaps(panel, 1700f, 100f, 20f), "under the panel");
        assertTrue(BattleDirectControlOverlay.overlaps(panel, 1540f, 100f, 20f), "its radius reaches the edge");
        assertFalse(BattleDirectControlOverlay.overlaps(panel, 960f, 40f, 20f), "bottom centre is clear");
        assertFalse(BattleDirectControlOverlay.overlaps(panel, 1700f, 300f, 20f), "above the panel");
        assertFalse(BattleDirectControlOverlay.overlaps(null, 1700f, 100f, 20f), "no panel, nothing to fade");
    }

    static UiDocument document(MarkupInstance markup) {
        UiDocument document = new UiDocument(markup.root());
        for (var style : markup.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(BattleDirectControlOverlay.DOCUMENT_WIDTH, BattleDirectControlOverlay.DOCUMENT_HEIGHT);
        return document;
    }
}
