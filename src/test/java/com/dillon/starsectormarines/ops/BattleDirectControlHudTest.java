package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ui.retained.UiDocument;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static com.dillon.starsectormarines.ops.BattleDirectControlStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleDirectControlHudTest {
    @Test
    void realBindingsShowDamageAndEquipmentAndKeepSelectionActionsInsideVisibleBounds() throws Exception {
        AtomicInteger selection = new AtomicInteger(-1);
        AtomicInteger exits = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        for (int[] size : List.of(new int[]{1744, 938}, new int[]{1280, 720}, new int[]{1366, 768})) {
            for (float scale : new float[]{1f, 1.25f, 1.5f}) {
                var viewport = BattleDirectControlOverlayTest.layout(size[0], size[1], scale).activeControl();
                try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.MECH, false), 2,
                        true, selection::set, exits::incrementAndGet, pauses::incrementAndGet)) {
                    fixture.document.layout(viewport.documentWidth(), viewport.documentHeight());
                    assertEquals("610 / 900", fixture.markup.requireElement("direct-hud-health").text());
                    assertEquals("1x", fixture.markup.requireElement("direct-hud-pause").text());
                    var disabled = fixture.markup.requireElement("direct-hud-weapon-RIGHT_SHOULDER");
                    assertTrue(disabled.disabled());
                    for (String id : List.of("direct-hud-all", "direct-hud-weapon-ARMS",
                            "direct-hud-weapon-LEFT_SHOULDER", "direct-hud-exit", "direct-hud-pause")) {
                        var box = fixture.markup.requireElement(id).box().borderBox();
                        assertTrue(box.width() > 0f && box.height() > 0f, id);
                        assertTrue(box.x() >= 0f && box.right() <= viewport.documentWidth(), id);
                        assertTrue(box.y() >= 0f && box.bottom() <= viewport.documentHeight(), id);
                        fixture.document.pointerDown(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
                        fixture.document.pointerUp(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
                    }
                    assertEquals(2, selection.get());
                    assertTrue(fixture.markup.requireElement("direct-hud-weapon-LEFT_SHOULDER")
                            .classes().contains("weapon-selected"));
                    var healthFill = fixture.markup.requireElement("direct-hud-health-fill").box().borderBox();
                    assertEquals(.68f * fixture.markup.requireElement("direct-hud-health-fill")
                            .parent().box().contentBox().width(), healthFill.width(), .01f);
                    assertTrue(fixture.markup.requireElement("direct-hud-controls").box().borderBox().width() > 0f);
                    var box = disabled.box().borderBox();
                    fixture.document.pointerDown(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
                    fixture.document.pointerUp(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
                    assertEquals(2, selection.get(), "an indirect weapon cannot select manual fire");
                }
            }
        }
        assertEquals(9, exits.get());
        assertEquals(9, pauses.get());
    }

    @Test
    void liveUpdatesRetainWeaponElementsAndDescribeExposedArmorWithoutInferringItFromHealth() throws Exception {
        try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.MARINE, false), 0,
                false, ignored -> {}, () -> {}, () -> {})) {
            var primary = fixture.markup.requireElement("direct-hud-weapon-PRIMARY");
            assertTrue(primary.disabled(), "the single primary is a readout, not a selector");
            assertEquals(1f, primary.opacity(), "read-only primary status must remain legible");
            fixture.model.update(preview(Carrier.MARINE, true), 0, false);
            fixture.markup.flush();
            fixture.document.layout(BattleDirectControlOverlay.ACTION_WIDTH, BattleDirectControlOverlay.ACTION_HEIGHT);
            assertSame(primary, fixture.markup.requireElement("direct-hud-weapon-PRIMARY"));
            assertTrue(fixture.markup.requireElement("direct-hud-armor").text().contains("EXPOSED"));
            assertEquals(0f, fixture.markup.requireElement("direct-hud-armor-fill").box().borderBox().width());
        }
    }

    @Test
    void productionAttachBindsBothShippedComponentsWithoutABattleOrGlContext() {
        SettingsAPI previous = Global.getSettings();
        var copy = ModStrings.fromDisk();
        SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "loadText" -> Files.readString(Path.of("mod").resolve((String) args[0]));
                    case "getString" -> copy.apply((String) args[1]);
                    case "getScreenScaleMult" -> 1f;
                    default -> throw new AssertionError("Unexpected settings query: " + method.getName());
                });
        PositionAPI position = (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(),
                new Class<?>[]{PositionAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f;
                    case "getWidth" -> 1744f;
                    case "getHeight" -> 938f;
                    default -> throw new AssertionError("Unexpected position query: " + method.getName());
                });
        try {
            Global.setSettings(settings);
            var overlay = new BattleDirectControlOverlay(new Selection(), () -> {}, () -> {}, ignored -> {});
            assertDoesNotThrow(() -> overlay.attach(position, null, 1f));
            overlay.detach();
            assertDoesNotThrow(() -> overlay.attach(position, null, 0f));
        } finally {
            Global.setSettings(previous);
        }
    }

    static Fixture fixture(Path modRoot, Snapshot snapshot, int selected, boolean paused,
                           IntConsumer selection, Runnable exit, Runnable pause) throws Exception {
        Reactor reactor = new Reactor();
        var model = new BattleDirectControlHudModel(reactor, selection, exit, pause, ModStrings.fromDisk(modRoot));
        model.update(snapshot, selected, paused);
        var loader = new MarkupLoader(path -> Files.readString(modRoot.resolve(path)),
                List.of(BattleDirectControlOverlay.ACTION_COMPONENT_PATH));
        var markup = loader.reloadAndBuild(reactor, BattleDirectControlOverlay.ACTION_COMPONENT, model.props());
        var document = BattleDirectControlOverlay.document(markup);
        document.layout(BattleDirectControlOverlay.ACTION_WIDTH, BattleDirectControlOverlay.ACTION_HEIGHT);
        return new Fixture(model, markup, document);
    }

    static Snapshot preview(Carrier carrier, boolean exposed) {
        var durability = carrier == Carrier.MARINE ? new Durability(74, 100, exposed ? 0 : 35, 60, 12)
                : new Durability(610, 900, exposed ? 0 : 320, 800, 35);
        List<WeaponStatus> weapons = switch (carrier) {
            case MECH -> List.of(
                    weapon(1, "ARMS", "Dual Chaingun", FireKind.BURST, 12, true, .3f, 0,
                            new Ammo(-1, -1, AmmoUnit.TRIGGER_PACKS, true)),
                    weapon(2, "LEFT_SHOULDER", "SRM-4 Rack", FireKind.BURST, 4, true, 0f, 2,
                            new Ammo(12, 20, AmmoUnit.TRIGGER_PACKS, false)),
                    weapon(3, "RIGHT_SHOULDER", "LRM Rack", FireKind.INDIRECT, 8, false, 0f, 0,
                            new Ammo(0, 8, AmmoUnit.TRIGGER_PACKS, false)));
            case VEHICLE -> List.of(weapon(1, "TURRET", "Heavy Machine Gun", FireKind.BURST, 10,
                    true, 0f, 0, new Ammo(90, 120, AmmoUnit.ROUNDS, false)));
            default -> List.of(weapon(1, "PRIMARY", "Pulse Rifle", FireKind.BURST, 3,
                    true, .4f, 0, new Ammo(0, 0, AmmoUnit.UNTRACKED, false)));
        };
        return new Snapshot(101, carrier == Carrier.MARINE ? "Rhea Voss" : carrier == Carrier.MECH
                ? "Bulwark Lead" : "Heavy APC", carrier, durability, weapons);
    }

    private static WeaponStatus weapon(int number, String slot, String name, FireKind kind, int rounds,
                                       boolean direct, float cooldown, int burst, Ammo ammo) {
        return new WeaponStatus(number, slot, name, new Behavior(kind, rounds, .1f, 1), direct,
                cooldown, 1f, burst, .1f, ammo);
    }

    record Fixture(BattleDirectControlHudModel model, MarkupInstance markup, UiDocument document)
            implements AutoCloseable {
        @Override public void close() { markup.close(); }
    }
}
