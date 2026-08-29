package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlePowerOverlayModelTest {

    @Test
    void mlxProjectsCompactPowerStatesAndArmsAReadyCard() throws Exception {
        Reactor reactor = new Reactor();
        AtomicReference<String> toggled = new AtomicReference<>();
        BattlePowerOverlayModel model = new BattlePowerOverlayModel(reactor, toggled::set);
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
            assertEquals("SUP 4", instance.requireElement("battle-power-supplies").text());
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
            assertEquals(new Color(0xFF, 0xD4, 0x64), armed.borderColor());
            assertEquals("TARGET", status(instance, "recon"));
            assertFalse(instance.requireElement("battle-power-targeting")
                    .hasClass("power-targeting-hidden"));
            assertEquals("TARGETING · RECON PING",
                    instance.requireElement("battle-power-targeting-label").text());
            assertTrue(instance.requireElement("battle-power-deck").box().borderBox().bottom()
                    <= BattlePowerOverlay.TARGETING_HEIGHT);
        }
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
