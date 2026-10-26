package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.LiftBudget;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlePowerOverlayModelTest {

    @Test
    void mlxProjectsCompactPowerStatesAndArmsAReadyCard() throws Exception {
        Reactor reactor = new Reactor();
        AtomicReference<String> toggled = new AtomicReference<>();
        BattlePowerOverlayModel model = new BattlePowerOverlayModel(
                reactor, toggled::set, new CommodityPresentation() {
                    @Override public String commodityIcon(String commodityId) {
                        return "graphics/icons/cargo/supplies.png";
                    }
                });
        List<BattlePowerOverlayModel.PowerState> powers = List.of(
                power("recon", "Recon Ping", 2f, 0, 0f, -1),
                power("mech", "Mech Support", 4f, 0, 0f, 2),
                power("resupply", "Resupply", 3f, 0, 9.2f, 2),
                power("barrage", "Orbital Barrage", 3f, 8, 0f, 1),
                power("drop", "Marine Drop", 2f, 0, 0f, 0));
        model.updateProjected(3f, 10f, 4, powers, null);

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattlePowerOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattlePowerOverlay.COMPONENT, model.props())) {
            BattlePowerOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(BattlePowerOverlay.documentWidth(5),
                    BattlePowerOverlay.DECK_HEIGHT);

            assertEquals("CP 3 / 10", instance.requireElement("battle-power-cp").text());
            assertEquals("SUP 4",
                    instance.requireElement("battle-power-supplies-label").text());
            assertEquals("graphics/icons/cargo/supplies.png",
                    instance.requireElement("battle-power-supplies-icon").imageSource());
            assertEquals(5, instance.requireElement("battle-power-cards").childCount());
            assertEquals("READY", status(instance, "recon"));
            assertEquals("LOW CP", status(instance, "mech"));
            assertEquals("10S", status(instance, "resupply"));
            assertEquals("LOW SUP", status(instance, "barrage"));
            assertEquals("SPENT", status(instance, "drop"));
            assertTrue(instance.requireElement("battle-power-mech").disabled());
            assertTrue(instance.requireElement("battle-power-cp-fill")
                    .box().borderBox().width() > 20f);
            assertTrue(instance.requireElement("battle-power-drop").box().borderBox().right()
                    <= BattlePowerOverlay.documentWidth(5));

            UiElement recon = instance.requireElement("battle-power-recon");
            float clickX = recon.box().borderBox().x()
                    + recon.box().borderBox().width() * 0.5f;
            float clickY = recon.box().borderBox().y()
                    + recon.box().borderBox().height() * 0.5f;
            assertTrue(document.pointerDown(clickX, clickY));
            assertTrue(document.pointerUp(clickX, clickY));
            assertEquals("recon", toggled.get());

            model.updateProjected(3f, 10f, 4, powers, "recon");
            instance.flush();
            document.layout(BattlePowerOverlay.documentWidth(5),
                    BattlePowerOverlay.TARGETING_HEIGHT);
            document.advance(0.2f);
            UiElement armed = instance.requireElement("battle-power-recon");
            assertTrue(armed.hasClass("power-armed"));
            assertEquals("TARGET", status(instance, "recon"));
            assertFalse(instance.requireElement("battle-power-targeting")
                    .hasClass("power-targeting-hidden"));
            assertEquals("TARGETING · RECON PING",
                    instance.requireElement("battle-power-targeting-label").text());
            assertTrue(instance.requireElement("battle-power-deck").box().borderBox().bottom()
                    <= BattlePowerOverlay.TARGETING_HEIGHT);
        }
    }

    @Test
    void armedMechSupportShowsTheBayPickerAndPicksOnlyCallableChips() throws Exception {
        MechSupport mech = cradledBay();
        Integer pick = mech.defaultPick(null);
        assertEquals(0, pick, "the picker opens on the first liftable machine");

        Reactor reactor = new Reactor();
        AtomicInteger toggledPick = new AtomicInteger(-1);
        BattlePowerOverlayModel model = new BattlePowerOverlayModel(reactor, ignored -> { },
                toggledPick::set, CommodityPresentation.NONE);
        List<BattlePowerOverlayModel.PowerState> powers = List.of(
                power(MechSupport.ID, "Mech Support", 4f, 0, 0f, 3),
                power("recon", "Recon Ping", 2f, 0, 0f, -1));
        BattlePowerOverlayModel.Presentation presentation = model.withPicker(
                model.updateProjected(4f, 10f, 4, powers, MechSupport.ID),
                mech.picker(null, pick));
        assertEquals(3, presentation.pickerChips());

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattlePowerOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattlePowerOverlay.COMPONENT, model.props())) {
            BattlePowerOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            float width = BattlePowerOverlay.documentWidth(presentation);
            float height = BattlePowerOverlay.documentHeight(presentation);
            document.layout(width, height);

            assertFalse(instance.requireElement("battle-power-picker")
                    .hasClass("power-picker-hidden"));
            assertEquals(3, instance.requireElement("battle-power-picker").childCount());
            assertEquals("HOUND", chip(instance, 0, "name"));
            assertEquals("ROOK", chip(instance, 0, "callsign"));
            assertEquals("MECH \u00b7 LIGHT", chip(instance, 0, "weight"));
            assertEquals("SELECTED", chip(instance, 0, "state"));
            assertEquals("BULWARK", chip(instance, 1, "name"));
            assertEquals("NO HEAVY LIFT", chip(instance, 1, "state"));
            assertTrue(instance.requireElement("battle-power-pick-1").disabled());
            assertEquals("MULE", chip(instance, 2, "callsign"));
            assertEquals("VEHICLE \u00b7 LIGHT", chip(instance, 2, "weight"));
            assertEquals("AVAILABLE", chip(instance, 2, "state"));
            assertEquals("TARGETING \u00b7 MECH SUPPORT \u00b7 HOUND",
                    instance.requireElement("battle-power-targeting-label").text());
            assertTrue(instance.requireElement("battle-power-pick-2").box().borderBox().right()
                    <= width);
            assertTrue(instance.requireElement("battle-power-deck").box().borderBox().bottom()
                    <= height);

            click(document, instance.requireElement("battle-power-pick-1"));
            assertEquals(-1, toggledPick.get(), "a refused chip does nothing");
            click(document, instance.requireElement("battle-power-pick-2"));
            assertEquals(2, toggledPick.get());

            // The host picks the APC instead; the Hound reads available again.
            model.withPicker(model.updateProjected(4f, 10f, 4, powers, MechSupport.ID),
                    mech.picker(null, 2));
            instance.flush();
            assertEquals("SELECTED", chip(instance, 2, "state"));
            assertEquals("AVAILABLE", chip(instance, 0, "state"));

            // Disarmed: the picker hides and the tray drops back to its deck height.
            BattlePowerOverlayModel.Presentation idle = model.withPicker(
                    model.updateProjected(4f, 10f, 4, powers, null), List.of());
            instance.flush();
            assertEquals(0, idle.pickerChips());
            assertTrue(instance.requireElement("battle-power-picker")
                    .hasClass("power-picker-hidden"));
            assertEquals(BattlePowerOverlay.DECK_HEIGHT, BattlePowerOverlay.documentHeight(idle));
        }
    }

    @Test
    void chipStatesNameQueuedPriorCallAndEachLiftRefusal() {
        MechSupport mech = cradledBay();
        BayManifest.Entry hound = mech.manifest().get(0);
        BayManifest.Entry bulwark = mech.manifest().get(1);
        assertEquals("QUEUED", BattlePowerOverlayModel.pickState(
                new ArmorDropPower.PickerRow(hound, ArmorDropPower.Status.QUEUED, false, null)));
        assertEquals("CALLED", BattlePowerOverlayModel.pickState(
                new ArmorDropPower.PickerRow(hound, ArmorDropPower.Status.CALLED, false, null)));
        assertEquals("NO HEAVY LIFT", BattlePowerOverlayModel.pickState(
                new ArmorDropPower.PickerRow(bulwark, ArmorDropPower.Status.ABOARD, false, LiftBudget.Refusal.NO_LIFT)));
        assertEquals("LIFT SPENT", BattlePowerOverlayModel.pickState(
                new ArmorDropPower.PickerRow(hound, ArmorDropPower.Status.ABOARD, false, LiftBudget.Refusal.LIFT_SPENT)));
        assertEquals("LIFT CRAFT LOST", BattlePowerOverlayModel.pickState(
                new ArmorDropPower.PickerRow(hound, ArmorDropPower.Status.ABOARD, false, LiftBudget.Refusal.NO_CRAFT)));
        assertEquals("AVAILABLE", BattlePowerOverlayModel.pickState(
                new ArmorDropPower.PickerRow(hound, ArmorDropPower.Status.ABOARD, false, null)));
    }

    /** A cradled Buffalo and no ship lift behind it: Light machines lift, Heavy is refused. */
    static MechSupport cradledBay() {
        FittedBoat buffalo = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");
        MechDeploymentSpec houndSpec = MechDeploymentSpec.standard(MechVariant.HOUND);
        MechDeploymentSpec rook = new MechDeploymentSpec(houndSpec.variant(), houndSpec.role(),
                houndSpec.missileReplenisher(), houndSpec.arms(), houndSpec.leftShoulder(),
                houndSpec.rightShoulder(), "Rook");
        return MechSupport.configured(
                List.of(rook, MechDeploymentSpec.standard(MechVariant.BULWARK)),
                List.of(new VehicleDeploymentSpec(null, "Mule", VehicleType.HEAVY_APC)),
                buffalo, false);
    }

    private static String chip(MarkupInstance instance, int index, String part) {
        return instance.requireElement("battle-power-pick-" + index + "-" + part).text();
    }

    private static void click(UiDocument document, UiElement element) {
        float x = element.box().borderBox().x() + element.box().borderBox().width() * 0.5f;
        float y = element.box().borderBox().y() + element.box().borderBox().height() * 0.5f;
        document.pointerDown(x, y);
        document.pointerUp(x, y);
    }

    private static String status(MarkupInstance instance, String id) {
        return instance.requireElement("battle-power-" + id + "-status").text();
    }

    private static BattlePowerOverlayModel.PowerState power(
            String id, String name, float cp, int supplies,
            float cooldown, int charges) {
        return new BattlePowerOverlayModel.PowerState(
                id, name, cp, supplies, cooldown, charges);
    }
}
