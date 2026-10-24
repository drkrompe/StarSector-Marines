package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dillon.starsectormarines.ops.BattleDirectControlStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleDirectControlHudTest {
    @Test
    void ventingHoundShowsFluxAsALabelledNumberAndHoldsItsWeapons() throws Exception {
        var loadout = MechVariant.HOUND.createLoadout(null);
        MechFlux flux = loadout.flux;
        flux.commit(98f);
        var base = preview(Carrier.MECH, false);
        var snapshot = new Snapshot(base.entityId(), "Wayward", Carrier.MECH, base.durability(),
                mechWeapons(loadout.mounts(), flux), List.of(), MarineSprint.EMPTY,
                new MechFluxStatus(flux.value(), flux.venting()));
        try (Fixture fixture = fixture(Path.of("mod"), snapshot, 0, false, ignored -> {}, () -> {}, () -> {})) {
            assertEquals("FLUX", fixture.status.requireElement("direct-status-flux-label").text());
            assertEquals("98%", fixture.status.requireElement("direct-status-flux-value").text());
            assertEquals("VENTING - GUNS LOCKED", fixture.status.requireElement("direct-status-flux-state").text());
            assertTrue(fixture.status.requireElement("direct-status-flux-state").classes().contains("state-alarm"));
            assertEquals("FLUX", fixture.weapons.requireElement("direct-hud-weapon-ARMS-state").text());
            var fill = fixture.status.requireElement("direct-status-flux-fill").box().borderBox();
            var meter = fixture.status.requireElement("direct-status-flux-meter").box().contentBox();
            assertEquals(meter.width() * .98f, fill.width(), .01f);
            assertInside(fixture.status, fixture.statusHeight, BattleDirectControlOverlay.STATUS_WIDTH);
            assertInside(fixture.weapons, fixture.weaponsHeight, BattleDirectControlOverlay.WEAPONS_WIDTH);
        }
        flux.advance(20f);
        loadout.mounts()[0].cycleRemaining = .5f;
        assertFalse(mechWeapons(loadout.mounts(), flux).get(0).fluxBlocked());
        assertEquals("Cycler", mechWeapons(loadout.mounts(), flux).get(0).mechanism());
    }

    @Test
    void statusNamesTheBodyAndReadsHullAndArmorAsPercentages() throws Exception {
        try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.MECH, false), 0,
                false, ignored -> {}, () -> {}, () -> {})) {
            assertEquals("Bulwark Lead", fixture.status.requireElement("direct-status-name").text());
            assertEquals("MECH", fixture.status.requireElement("direct-status-carrier").text());
            assertEquals("HULL", fixture.status.requireElement("direct-status-hull-label").text());
            assertEquals("68%", fixture.status.requireElement("direct-status-hull-value").text());
            assertEquals("ARMOR", fixture.status.requireElement("direct-status-armor-label").text());
            assertEquals("40%", fixture.status.requireElement("direct-status-armor-value").text());
            var hull = fixture.status.requireElement("direct-status-hull-fill");
            assertEquals(.68f * hull.parent().box().contentBox().width(), hull.box().borderBox().width(), .01f);
            var hullMeter = fixture.status.requireElement("direct-status-hull-meter").box().borderBox();
            assertTrue(hullMeter.height() >= 8f, "a bar a player can read at a glance");
            assertTrue(fixture.status.requireElement("direct-status-armor").box().borderBox().bottom()
                    <= fixture.status.requireElement("direct-status-flux").box().borderBox().y(), "armor above flux");
            assertHidden(fixture.status.requireElement("direct-status-stamina"));
        }
        try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.MARINE, true), 0,
                false, ignored -> {}, () -> {}, () -> {})) {
            assertEquals("HEALTH", fixture.status.requireElement("direct-status-hull-label").text());
            assertEquals("0%", fixture.status.requireElement("direct-status-armor-value").text(),
                    "stripped armor reads empty rather than inferred from health");
            assertEquals(0f, fixture.status.requireElement("direct-status-armor-fill").box().borderBox().width());
            assertHidden(fixture.status.requireElement("direct-status-flux"));
            assertHidden(fixture.status.requireElement("direct-status-flux-state"));
        }
    }

    @Test
    void triggerHeadersNameThePickAndOnlyPickedRowsCarryTheWash() throws Exception {
        AtomicInteger key = new AtomicInteger(-1);
        List<String> asked = new ArrayList<>();
        BattleDirectControlHudModel.WeaponActions actions = new BattleDirectControlHudModel.WeaponActions() {
            @Override public void select(int selected) { key.set(selected); }
            @Override public void cycle(FireTrigger trigger) { asked.add("cycle " + trigger); }
            @Override public void pick(FireTrigger trigger, int member) { asked.add("pick " + trigger + " " + member); }
        };
        try (Fixture fixture = fixture(Path.of("mod"), bulwark(TriggerPicks.LINKED), 0, false,
                actions, () -> {}, () -> {})) {
            assertEquals("WEAPON GROUPS", fixture.weapons.requireElement("direct-weapons-title").text());
            assertEquals(List.of("direct-hud-weapon-LEFT_ARM", "direct-hud-weapon-RIGHT_ARM"),
                    rows(fixture.weapons, "direct-hud-group-lmb"));
            assertEquals(List.of("direct-hud-weapon-LEFT_SHOULDER", "direct-hud-weapon-RIGHT_SHOULDER"),
                    rows(fixture.weapons, "direct-hud-group-rmb"));
            assertEquals("LINKED", fixture.weapons.requireElement("direct-hud-group-lmb-name").text());
            assertEquals("[Q]", fixture.weapons.requireElement("direct-hud-group-lmb-hint").text());
            assertEquals("LINKED", fixture.weapons.requireElement("direct-hud-group-rmb-name").text());
            assertEquals("[E]", fixture.weapons.requireElement("direct-hud-group-rmb-hint").text());
            assertEquals("3", fixture.weapons.requireElement("direct-hud-weapon-RIGHT_ARM-key").text(),
                    "key 1 is LINKED, then each gun");
            for (String id : List.of("LEFT_ARM", "RIGHT_ARM", "LEFT_SHOULDER", "RIGHT_SHOULDER")) {
                assertTrue(fixture.weapons.requireElement("direct-hud-weapon-" + id).classes().contains("weapon-selected"),
                        id + " fires on LINKED");
            }
            click(fixture.weapons, fixture.weaponsDocument, "direct-hud-weapon-RIGHT_ARM");
            assertEquals(2, key.get(), "a gun's row is its number key");
            click(fixture.weapons, fixture.weaponsDocument, "direct-hud-group-lmb-header");
            click(fixture.weapons, fixture.weaponsDocument, "direct-hud-weapon-RIGHT_SHOULDER");
            assertEquals(List.of("cycle LEFT", "pick RIGHT " + (MechMountSlot.RIGHT_SHOULDER.ordinal() + 1)), asked);
        }
        var rightArm = new TriggerPicks(MechMountSlot.RIGHT_ARM.ordinal() + 1, MechFireGroups.LINKED);
        try (Fixture fixture = fixture(Path.of("mod"), bulwark(rightArm), 0, false, actions, () -> {}, () -> {})) {
            var picked = fixture.weapons.requireElement("direct-hud-weapon-RIGHT_ARM");
            var held = fixture.weapons.requireElement("direct-hud-weapon-LEFT_ARM");
            assertEquals("R arm " + MechWeaponComponent.CHAINGUN.displayName(),
                    fixture.weapons.requireElement("direct-hud-group-lmb-name").text(),
                    "two chainguns: the header says which arm");
            assertTrue(picked.classes().contains("weapon-selected"));
            assertFalse(held.classes().contains("weapon-selected"), "the unpicked gun holds fire");
            assertEquals(.45f, held.opacity(), 1e-4f);
        }
    }

    @Test
    void marineRightButtonItemsSitUnderRmbWithThePickAndStaminaJoinsTheStatus() throws Exception {
        for (int state = 0; state < 4; state++) {
            try (Fixture fixture = fixture(Path.of("mod"), abilityPreview(state), 0,
                    false, ignored -> fail("Right-button rows cannot select a left-button weapon"), () -> {}, () -> {})) {
                assertEquals(List.of("direct-hud-ability-SMOKE", "direct-hud-ability-SHIELD"),
                        rows(fixture.weapons, "direct-hud-group-rmb"));
                assertEquals("Smoke", fixture.weapons.requireElement("direct-hud-group-rmb-name").text(),
                        "the RMB header names the pick");
                assertEquals("C", fixture.weapons.requireElement("direct-hud-ability-SHIELD-key").text());
                assertEquals("G", fixture.weapons.requireElement("direct-hud-ability-SMOKE-key").text());
                assertTrue(fixture.weapons.requireElement("direct-hud-ability-SMOKE").classes()
                        .contains("weapon-selected"), "the pick carries the wash");
                for (String id : List.of("direct-hud-ability-SHIELD", "direct-hud-ability-SMOKE")) {
                    var row = fixture.weapons.requireElement(id);
                    assertFalse(row.disabled(), "a row picks itself when the list has more than one item");
                    assertEquals(1f, row.opacity(), "C and G still work, so the unpicked row does not dim");
                }
                assertEquals(new String[]{"READY", "UP", "6.0s", "BROKEN"}[state],
                        fixture.weapons.requireElement("direct-hud-ability-SHIELD-state").text());
                assertEquals(new String[]{"READY", "THROW", "EMPTY", "READY"}[state],
                        fixture.weapons.requireElement("direct-hud-ability-SMOKE-state").text());
                assertEquals(state == 2 ? "0 / 2" : "2 / 2",
                        fixture.weapons.requireElement("direct-hud-ability-SMOKE-ammo").text());
                assertEquals(new String[]{"100%", "RUN", "REST", "REST"}[state],
                        fixture.status.requireElement("direct-status-stamina-value").text());
                var stamina = fixture.status.requireElement("direct-status-stamina-fill");
                assertEquals(new float[]{1f, .61f, 0f, .35f}[state] * stamina.parent().box().contentBox().width(),
                        stamina.box().borderBox().width(), .01f);
                if (state == 1) {
                    var fill = fixture.weapons.requireElement("direct-hud-ability-SHIELD-fill");
                    assertEquals(fill.parent().box().contentBox().width() * .5f,
                            fill.box().borderBox().width(), .01f);
                }
                assertInside(fixture.weapons, fixture.weaponsHeight, BattleDirectControlOverlay.WEAPONS_WIDTH);
                assertInside(fixture.status, fixture.statusHeight, BattleDirectControlOverlay.STATUS_WIDTH);
            }
        }
        try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.MARINE, false), 0,
                false, ignored -> {}, () -> {}, () -> {})) {
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.weapons.requireElement("direct-hud-group-rmb"));
            assertEquals("100%", fixture.status.requireElement("direct-status-stamina-value").text(),
                    "unmodified Marines still have stamina");
            var primary = fixture.weapons.requireElement("direct-hud-weapon-PRIMARY");
            assertTrue(primary.disabled(), "the single primary is a readout, not a selector");
            assertEquals(1f, primary.opacity(), "read-only primary status must remain legible");
            assertEquals("", fixture.weapons.requireElement("direct-hud-weapon-PRIMARY-ammo").text(),
                    "an infantry primary has no ammunition counter to show");
        }
    }

    @Test
    void aPickedGrenadeNamesTheRmbHeaderAndCountsWhatIsLeft() throws Exception {
        var base = preview(Carrier.MARINE, false);
        var frag = new AbilityStatus(DirectControlAbility.GRENADE, "Shattercap", true,
                0f, .75f, 0f, 0f, 2, 3, 0f, 0f).withPicked(true);
        var shield = new AbilityStatus(DirectControlAbility.SHIELD, "Breacher shield", true,
                0f, 6f, 0f, 12f, -1, -1, 0f, 120f, false, 90f, 120f);
        List<String> picks = new ArrayList<>();
        var actions = new BattleDirectControlHudModel.WeaponActions() {
            @Override public void select(int key) { fail("no left-button pick"); }
            @Override public void cycle(FireTrigger trigger) { picks.add("cycle " + trigger); }
            @Override public void pick(FireTrigger trigger, int member) { picks.add(trigger + " " + member); }
        };
        try (Fixture fixture = fixture(Path.of("mod"), new Snapshot(base.entityId(), base.name(), base.carrier(),
                base.durability(), base.weapons(), List.of(frag, shield)), 0, false, actions, () -> {}, () -> {})) {
            assertEquals("Grenade", fixture.weapons.requireElement("direct-hud-group-rmb-name").text());
            assertEquals("Shattercap", fixture.weapons.requireElement("direct-hud-ability-GRENADE-behavior").text(),
                    "the model goes on the row's second line");
            assertEquals("[E]", fixture.weapons.requireElement("direct-hud-group-rmb-hint").text());
            assertEquals("", fixture.weapons.requireElement("direct-hud-ability-GRENADE-key").text(),
                    "a grenade has no hotkey of its own; E and the right button reach it");
            assertEquals("2 / 3", fixture.weapons.requireElement("direct-hud-ability-GRENADE-ammo").text());
            click(fixture.weapons, fixture.weaponsDocument, "direct-hud-ability-SHIELD");
            click(fixture.weapons, fixture.weaponsDocument, "direct-hud-group-rmb-header");
            assertEquals(List.of("RIGHT 1", "cycle RIGHT"), picks,
                    "a row click picks that item and the header cycles, as on a mech");
        }
    }

    @Test
    void liveUpdatesKeepRowElementsAndOnlyAStructuralChangeAsksForRemeasure() throws Exception {
        try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.MARINE, false), 0,
                false, ignored -> {}, () -> {}, () -> {})) {
            var primary = fixture.weapons.requireElement("direct-hud-weapon-PRIMARY");
            String structure = fixture.model.structureKey();
            fixture.model.update(preview(Carrier.MARINE, true), 0, false);
            fixture.weapons.flush();
            assertSame(primary, fixture.weapons.requireElement("direct-hud-weapon-PRIMARY"));
            assertEquals(structure, fixture.model.structureKey(), "damage changes values, not rows");
            fixture.model.update(abilityPreview(0), 0, false);
            assertNotEquals(structure, fixture.model.structureKey(), "new ability rows change the panel's height");
        }
    }

    @Test
    void sessionButtonsStillExitAndPause() throws Exception {
        AtomicInteger exits = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        try (Fixture fixture = fixture(Path.of("mod"), preview(Carrier.VEHICLE, false), 0,
                true, ignored -> fail("Read-only equipment cannot select"), exits::incrementAndGet, pauses::incrementAndGet)) {
            assertEquals("PAUSED", fixture.actions.requireElement("direct-actions-pause-label").text());
            assertEquals("SPACE", fixture.actions.requireElement("direct-actions-pause-key").text());
            assertTrue(fixture.actions.requireElement("direct-actions-pause").classes().contains("action-paused"),
                    "a paused battle shows it on the pause control");
            assertEquals("TAB", fixture.actions.requireElement("direct-actions-exit-key").text());
            click(fixture.actions, fixture.actionsDocument, "direct-actions-exit");
            click(fixture.actions, fixture.actionsDocument, "direct-actions-pause");
            assertEquals("WEAPONS", fixture.weapons.requireElement("direct-weapons-title").text());
            assertHidden(fixture.status.requireElement("direct-status-flux"));
            assertHidden(fixture.status.requireElement("direct-status-stamina"));
            click(fixture.weapons, fixture.weaponsDocument, "direct-hud-weapon-TURRET");
        }
        assertEquals(1, exits.get());
        assertEquals(1, pauses.get());
    }

    @Test
    void productionAttachBindsAllShippedComponentsWithoutABattleOrGlContext() {
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

    /** Builds both panels and the session buttons, each laid out at the height its content measures. */
    static Fixture fixture(Path modRoot, Snapshot snapshot, int selected, boolean paused,
                           BattleDirectControlHudModel.WeaponActions selection, Runnable exit, Runnable pause)
            throws Exception {
        Reactor reactor = new Reactor();
        var model = new BattleDirectControlHudModel(reactor, selection, exit, pause, ModStrings.fromDisk(modRoot));
        model.update(snapshot, selected, paused);
        var loader = new MarkupLoader(path -> Files.readString(modRoot.resolve(path)),
                List.of(BattleDirectControlOverlay.STATUS_COMPONENT_PATH,
                        BattleDirectControlOverlay.WEAPONS_COMPONENT_PATH,
                        BattleDirectControlOverlay.ACTIONS_COMPONENT_PATH));
        var status = loader.reloadAndBuild(reactor, BattleDirectControlOverlay.STATUS_COMPONENT, model.statusProps());
        var weapons = loader.build(reactor, BattleDirectControlOverlay.WEAPONS_COMPONENT, model.weaponsProps());
        var actions = loader.build(reactor, BattleDirectControlOverlay.ACTIONS_COMPONENT, model.actionsProps());
        var statusDocument = BattleDirectControlOverlay.document(status);
        var weaponsDocument = BattleDirectControlOverlay.document(weapons);
        var actionsDocument = BattleDirectControlOverlay.document(actions);
        float statusHeight = BattleDirectControlOverlay.naturalHeight(statusDocument, status,
                "direct-status-spacer", BattleDirectControlOverlay.STATUS_WIDTH);
        if (snapshot.carrier() == Carrier.MECH || snapshot.carrier() == Carrier.VEHICLE) statusHeight = Math.max(statusHeight, BattleDamageDollPlate.DOCUMENT_HEIGHT);
        float weaponsHeight = BattleDirectControlOverlay.naturalHeight(weaponsDocument, weapons,
                "direct-weapons-spacer", BattleDirectControlOverlay.WEAPONS_WIDTH);
        statusDocument.layout(BattleDirectControlOverlay.STATUS_WIDTH, statusHeight);
        weaponsDocument.layout(BattleDirectControlOverlay.WEAPONS_WIDTH, weaponsHeight);
        actionsDocument.layout(BattleDirectControlOverlay.ACTIONS_WIDTH, BattleDirectControlOverlay.ACTIONS_HEIGHT);
        return new Fixture(model, status, statusDocument, statusHeight, weapons, weaponsDocument, weaponsHeight,
                actions, actionsDocument);
    }

    private static void click(MarkupInstance markup, UiDocument document, String id) {
        var box = markup.requireElement(id).box().borderBox();
        assertTrue(box.width() > 0f && box.height() > 0f, id);
        document.pointerDown(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
        document.pointerUp(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
    }

    private static List<String> rows(MarkupInstance markup, String groupId) {
        return markup.requireElement(groupId + "-rows").children().stream().map(UiElement::id).toList();
    }

    private static void assertHidden(UiElement element) {
        var box = element.box().borderBox();
        assertEquals(0f, box.width() * box.height(), element.id());
    }

    /** Nothing visible spills past the panel's measured bounds. */
    private static void assertInside(MarkupInstance markup, float height, float width) {
        assertInsideElement(markup.root(), height, width);
    }

    private static void assertInsideElement(UiElement element, float height, float width) {
        var box = element.box().borderBox();
        if (box.width() > 0f && box.height() > 0f) {
            assertTrue(box.right() <= width + .01f, element.id() + " fits the width");
            assertTrue(box.bottom() <= height + .01f, element.id() + " fits the measured height");
        }
        for (UiElement child : element.children()) assertInsideElement(child, height, width);
    }

    static Snapshot preview(Carrier carrier, boolean exposed) {
        var durability = carrier == Carrier.MARINE ? new Durability(74, 100, exposed ? 0 : 35, 60, 12)
                : new Durability(610, 900, exposed ? 0 : 320, 800, 35);
        List<WeaponStatus> weapons = switch (carrier) {
            case MECH -> List.of(
                    weapon(1, "ARMS", "Dual Chaingun", FireKind.BURST, 12, true, .3f, 0,
                            new Ammo(-1, -1, AmmoUnit.TRIGGER_PACKS, true), ""),
                    weapon(2, "LEFT_SHOULDER", "SRM-4 Rack", FireKind.BURST, 4, true, 0f, 2,
                            new Ammo(12, 20, AmmoUnit.TRIGGER_PACKS, false), ""),
                    weapon(3, "RIGHT_SHOULDER", "LRM Rack", FireKind.INDIRECT, 8, false, 0f, 0,
                            new Ammo(0, 8, AmmoUnit.TRIGGER_PACKS, false), ""));
            case VEHICLE -> List.of(weapon(1, "TURRET", "Heavy Machine Gun", FireKind.BURST, 10,
                    true, 0f, 0, new Ammo(90, 120, AmmoUnit.ROUNDS, false), ""));
            default -> List.of(weapon(1, "PRIMARY", "Pulse Rifle", FireKind.BURST, 3,
                    true, .4f, 0, new Ammo(0, 0, AmmoUnit.UNTRACKED, false), ""));
        };
        return new Snapshot(101, carrier == Carrier.MARINE ? "Rhea Voss" : carrier == Carrier.MECH
                ? "Bulwark Lead" : "Heavy APC", carrier, durability, weapons);
    }

    /**
     * The mech preview with real button assignments: the chaingun on the left and the SRM on the
     * right by default, or (selection 2) the SRM pointed at by the left button as well.
     */
    @Test
    void anLrmRackInsideItsMinimumReadsTooCloseOnItsRowAndItsButtonsHeader() throws Exception {
        var base = triggered(0);
        var lrm = base.weapons().get(2);
        var close = new WeaponStatus(3, lrm.slotKey(), lrm.name(), new Behavior(FireKind.INDIRECT, 8, .1f, 1), true,
                0f, 1f, 0, .1f, new Ammo(4, 4, AmmoUnit.TRIGGER_PACKS, false), false, "", "R", true);
        var snapshot = new Snapshot(base.entityId(), base.name(), base.carrier(), base.durability(),
                List.of(base.weapons().get(0), base.weapons().get(1), close));
        try (Fixture fixture = fixture(Path.of("mod"), snapshot, 0, false, ignored -> {}, () -> {}, () -> {})) {
            assertEquals("TOO CLOSE", fixture.weapons.requireElement("direct-hud-weapon-RIGHT_SHOULDER-state").text());
            assertEquals("TOO CLOSE", fixture.weapons.requireElement("direct-hud-group-rmb-hint").text());
            assertTrue(fixture.weapons.requireElement("direct-hud-group-rmb-hint").classes().contains("group-hint-warn"));
            assertFalse(fixture.weapons.requireElement("direct-hud-group-lmb-hint").classes().contains("group-hint-warn"),
                    "the guns on the other button are not refusing anything");
        }
    }

    /** A Bulwark with a chaingun on each arm and an SRM-5 on each shoulder, on {@code picks}. */
    static Snapshot bulwark(TriggerPicks picks) {
        MechWeaponMount[] mounts = new MechWeaponMount[MechMountSlot.values().length];
        mounts[MechMountSlot.LEFT_ARM.ordinal()] = new MechWeaponMount(MechMountSlot.LEFT_ARM, MechWeaponComponent.CHAINGUN);
        mounts[MechMountSlot.RIGHT_ARM.ordinal()] = new MechWeaponMount(MechMountSlot.RIGHT_ARM, MechWeaponComponent.CHAINGUN);
        mounts[MechMountSlot.LEFT_SHOULDER.ordinal()] = new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_5);
        mounts[MechMountSlot.RIGHT_SHOULDER.ordinal()] = new MechWeaponMount(MechMountSlot.RIGHT_SHOULDER, MechWeaponComponent.SRM_5);
        mounts[MechMountSlot.RIGHT_SHOULDER.ordinal()].ammo -= 2;
        mounts[MechMountSlot.LEFT_ARM.ordinal()].cycleRemaining = .3f;
        var base = preview(Carrier.MECH, false);
        return new Snapshot(base.entityId(), "Bulwark Lead", Carrier.MECH, base.durability(),
                mechWeapons(mounts, new MechFlux(), picks));
    }

    static Snapshot triggered(int selected) {
        var base = preview(Carrier.MECH, false);
        var arms = base.weapons().get(0);
        var srm = base.weapons().get(1);
        var lrm = base.weapons().get(2);
        return new Snapshot(base.entityId(), base.name(), base.carrier(), base.durability(), List.of(
                weapon(1, arms.slotKey(), arms.name(), FireKind.BURST, 12, true, .3f, 0, arms.ammo(),
                        selected == 0 ? "L" : ""),
                weapon(2, srm.slotKey(), srm.name(), FireKind.BURST, 4, true, 0f, 2, srm.ammo(),
                        selected == 2 ? "LR" : "R"),
                weapon(3, lrm.slotKey(), lrm.name(), FireKind.INDIRECT, 8, false, 0f, 0, lrm.ammo(), "")));
    }

    static Snapshot abilityPreview(int state) {
        var base = preview(Carrier.MARINE, false);
        var shield = new AbilityStatus(DirectControlAbility.SHIELD, "Breacher shield", state == 0,
                state == 1 ? 3f : 0f, 6f, state == 0 ? 0f : 6f, 12f,
                -1, -1, state == 1 ? 60f : 0f, 120f, state == 3, 90f, 120f);
        var smoke = new AbilityStatus(DirectControlAbility.SMOKE, "Smoke grenade", state == 0 || state == 3,
                state == 1 ? .4f : 0f, .8f, 0f, 0f, state == 2 ? 0 : 2, 2,
                0f, 0f).withPicked(true);
        return new Snapshot(base.entityId(), base.name(), base.carrier(), base.durability(),
                base.weapons(), List.of(smoke, shield), switch (state) {
                    case 0 -> MarineSprint.EMPTY;
                    case 1 -> new MarineSprint.Status(.61f, true, false);
                    case 2 -> new MarineSprint.Status(0f, false, true);
                    default -> new MarineSprint.Status(.35f, false, true);
                });
    }

    private static WeaponStatus weapon(int number, String slot, String name, FireKind kind, int rounds,
                                       boolean direct, float cooldown, int burst, Ammo ammo, String trigger) {
        return new WeaponStatus(number, slot, name, new Behavior(kind, rounds, .1f, 1), direct,
                cooldown, 1f, burst, .1f, ammo, false, "", trigger);
    }

    record Fixture(BattleDirectControlHudModel model, MarkupInstance status, UiDocument statusDocument,
                   float statusHeight, MarkupInstance weapons, UiDocument weaponsDocument, float weaponsHeight,
                   MarkupInstance actions, UiDocument actionsDocument) implements AutoCloseable {
        @Override public void close() { actions.close(); weapons.close(); status.close(); }
    }
}
