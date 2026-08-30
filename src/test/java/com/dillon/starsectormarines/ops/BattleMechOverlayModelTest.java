package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleMechOverlayModelTest {

    @Test
    void mlxProjectsFourDoctrinesAndRoutesOverrideAndResetRequests() throws Exception {
        Reactor reactor = new Reactor();
        AtomicInteger backs = new AtomicInteger();
        AtomicReference<Request> requested = new AtomicReference<>();
        BattleMechOverlayModel model = new BattleMechOverlayModel(
                reactor, backs::incrementAndGet,
                (mechId, role) -> requested.set(new Request(mechId, role)));
        List<MechRole> roles = BattleMechOverlayModel.selectableRoles();
        assertEquals(4, roles.size());
        assertEquals("BRAWLER", upper(roles.get(0)));
        assertEquals("TANK", upper(roles.get(1)));
        assertEquals("LONG RANGE SUPPORT", upper(roles.get(2)));
        assertEquals("BALANCED", upper(roles.get(3)));

        MechRole effective = roles.get(0);
        MechRole deployed = roles.get(1);
        assertTrue(model.updateProjected(new BattleMechOverlayModel.MechState(
                42L, "Atlas Three", "Bulwark", deployed, effective, roles)).visible());

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleMechOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleMechOverlay.COMPONENT, model.props())) {
            BattleMechOverlay.wireLayout(instance);
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(BattleMechOverlay.DOCUMENT_WIDTH,
                    BattleMechOverlay.DOCUMENT_HEIGHT);

            assertEquals("BULWARK", instance.requireElement("battle-mech-title").text());
            assertEquals("ATLAS THREE",
                    instance.requireElement("battle-mech-identity").text());
            assertEquals("DEPLOYED · " + upper(deployed),
                    instance.requireElement("battle-mech-deployed").text());
            assertEquals("EFFECTIVE · " + upper(effective),
                    instance.requireElement("battle-mech-effective").text());
            assertEquals("PLAYER OVERRIDE",
                    instance.requireElement("battle-mech-state").text());
            assertTrue(instance.requireElement("battle-mech-state")
                    .hasClass("doctrine-state-overridden"));
            assertEquals(4, instance.requireElement(
                    "battle-mech-doctrine-cards").childCount());
            assertEquals("LR SUPPORT", instance.requireElement(
                    BattleMechOverlayModel.cardId(roles.get(2)) + "-name").text());
            assertTrue(instance.requireElement(BattleMechOverlayModel.cardId(effective))
                    .hasClass("doctrine-card-active"));
            assertTrue(instance.requireElement(BattleMechOverlayModel.cardId(deployed))
                    .hasClass("doctrine-card-deployed"));
            assertTrue(instance.requireElement("battle-mech-panel").box().borderBox().right()
                    <= BattleMechOverlay.DOCUMENT_WIDTH);

            MechRole next = roles.get(2);
            click(document, instance.requireElement(BattleMechOverlayModel.cardId(next)));
            assertEquals(42L, requested.get().mechId());
            assertEquals(next, requested.get().role());

            click(document, instance.requireElement("battle-mech-default"));
            assertEquals(42L, requested.get().mechId());
            assertNull(requested.get().role());

            click(document, instance.requireElement("battle-mech-back"));
            assertEquals(1, backs.get());
        }
    }

    @Test
    void invalidProjectionStaysHiddenAndCannotRequest() {
        AtomicInteger requests = new AtomicInteger();
        BattleMechOverlayModel model = new BattleMechOverlayModel(
                new Reactor(), () -> { }, (mechId, role) -> requests.incrementAndGet());

        assertFalse(model.updateProjected(null).visible());
        assertFalse(model.updateProjected(new BattleMechOverlayModel.MechState(
                0L, "", "", null, null, List.of())).visible());
        assertEquals(0, requests.get());
    }

    private static void click(UiDocument document, UiElement element) {
        float x = element.box().borderBox().x() + element.box().borderBox().width() * 0.5f;
        float y = element.box().borderBox().y() + element.box().borderBox().height() * 0.5f;
        assertTrue(document.pointerDown(x, y));
        assertTrue(document.pointerUp(x, y));
    }

    private static String upper(MechRole role) {
        return role.displayName().toUpperCase(Locale.ROOT);
    }

    private record Request(long mechId, MechRole role) { }
}
