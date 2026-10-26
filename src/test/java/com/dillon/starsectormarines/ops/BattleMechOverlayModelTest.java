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
import java.util.concurrent.atomic.AtomicLong;
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
        AtomicLong detached = new AtomicLong();
        BattleMechOverlayModel model = new BattleMechOverlayModel(
                reactor, backs::incrementAndGet,
                (mechId, role) -> requested.set(new Request(mechId, role)),
                detached::set);
        List<MechRole> roles = BattleMechOverlayModel.selectableRoles();
        assertEquals(4, roles.size());
        assertEquals("BRAWLER", upper(roles.get(0)));
        assertEquals("TANK", upper(roles.get(1)));
        assertEquals("LONG RANGE SUPPORT", upper(roles.get(2)));
        assertEquals("BALANCED", upper(roles.get(3)));

        MechRole effective = roles.get(0);
        MechRole deployed = roles.get(1);
        assertTrue(model.updateProjected(new BattleMechOverlayModel.MechState(
                7, 42L, "Atlas Three", "Bulwark", deployed, effective,
                null, roles)).visible());

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
            assertEquals("DOCTRINE OVERRIDE",
                    instance.requireElement("battle-mech-state").text());
            assertTrue(instance.requireElement("battle-mech-state")
                    .hasClass("doctrine-state-overridden"));
            assertEquals("ATTACHMENT", instance.requireElement(
                    "battle-mech-attachment-heading").text());
            assertEquals("SELECTED MECH", instance.requireElement(
                    "battle-mech-doctrine-scope").text());
            assertEquals("RESET DOCTRINE", instance.requireElement(
                    "battle-mech-default").text());
            assertTrue(instance.requireElement("battle-mech-attachment-state")
                    .hasClass("attachment-state-free"));
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
            assertTrue(instance.requireElement("battle-mech-panel").box().borderBox().bottom()
                    <= BattleMechOverlay.DOCUMENT_HEIGHT);
            assertTrue(instance.requireElement("battle-mech-attachment-row").box().borderBox().bottom()
                    < instance.requireElement("battle-mech-doctrine-heading-row").box().borderBox().y());

            assertTrue(model.updateProjected(new BattleMechOverlayModel.MechState(
                    7, 42L, "Atlas Three", "Bulwark", deployed, effective,
                    "Alpha", roles)).visible());
            instance.flush();
            document.advance(0f);
            assertTrue(instance.requireElement("battle-mech-attachment-state")
                    .hasClass("attachment-state-attached"));
            assertEquals("FOLLOWING · ALPHA", instance.requireElement(
                    "battle-mech-attachment-state").text());
            click(document, instance.requireElement("battle-mech-detach"));
            assertEquals(42L, detached.get());

            MechRole next = roles.get(2);
            click(document, instance.requireElement(BattleMechOverlayModel.cardId(next)));
            assertEquals(42L, requested.get().mechId());
            assertEquals(next, requested.get().role());

            click(document, instance.requireElement("battle-mech-default"));
            assertEquals(42L, requested.get().mechId());
            assertNull(requested.get().role());

            assertTrue(model.updateProjected(new BattleMechOverlayModel.MechState(
                    7, 42L, "Atlas Three", "Bulwark", deployed, deployed,
                    "Alpha", roles)).visible());
            instance.flush();
            document.advance(0f);
            assertEquals("DOCTRINE DEFAULT",
                    instance.requireElement("battle-mech-state").text());
            assertTrue(instance.requireElement("battle-mech-state")
                    .hasClass("doctrine-state-default"));

            click(document, instance.requireElement("battle-mech-back"));
            assertEquals(1, backs.get());
        }
    }

    @Test
    void invalidProjectionStaysHiddenAndCannotRequest() {
        AtomicInteger requests = new AtomicInteger();
        BattleMechOverlayModel model = new BattleMechOverlayModel(
                new Reactor(), () -> { }, (mechId, role) -> requests.incrementAndGet(),
                mechId -> requests.incrementAndGet());

        assertFalse(model.updateProjected(null).visible());
        assertFalse(model.updateProjected(new BattleMechOverlayModel.MechState(
                -1, 0L, "", "", null, null, null,
                List.of())).visible());
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
