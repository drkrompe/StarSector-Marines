package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BattleShipCommandOverlayModelTest {
    @Test
    void retainedCardsSelectShipsAndExposeDisabledAndArmedCommands() throws Exception {
        Reactor reactor = new Reactor();
        BattleShipCommandOverlayModel model = new BattleShipCommandOverlayModel(reactor);
        AtomicInteger selected = new AtomicInteger();
        AtomicInteger requested = new AtomicInteger();
        var state = new BattleShipCommandOverlayModel.State(List.of(
                new BattleShipCommandOverlayModel.ShipState("shepherd", "Shepherd", "graphics/ships/shepherd.png", "HOLD", .6f, true, selected::incrementAndGet)),
                "Shepherd", "RMB move", List.of(
                new BattleShipCommandOverlayModel.ActionState("build", "Vulcan", "graphics/icons/cargo/supplies.png", "Place a Vulcan turret", "TARGET", true, true, requested::incrementAndGet),
                new BattleShipCommandOverlayModel.ActionState("move", "Move", "", "Grounded hull", "GROUNDED", false, false, requested::incrementAndGet)));
        model.update(state);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleShipCommandOverlay.COMPONENT_PATH, BattleShipCommandOverlay.ACTION_COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance heroes = loader.build(reactor, "battle-ship-command-overlay", model.props());
             MarkupInstance actions = loader.build(reactor, "battle-ship-command-actions", model.props())) {
            UiDocument heroDoc = BattleDirectControlOverlay.document(heroes);
            UiDocument actionDoc = BattleDirectControlOverlay.document(actions);
            heroDoc.layout(BattleShipCommandOverlay.heroWidth(1), BattleShipCommandOverlay.HERO_HEIGHT);
            actionDoc.layout(BattleShipCommandOverlay.actionWidth(2), BattleShipCommandOverlay.ACTION_HEIGHT);
            assertTrue(heroes.requireElement("ship-heroes").box().contentBox().height() >= 68f);
            assertTrue(actions.requireElement("ship-actions").box().contentBox().height() >= 60f);
            click(heroDoc, heroes.requireElement("ship-hero-shepherd"));
            assertEquals(1, selected.get());
            assertTrue(heroes.requireElement("ship-hero-shepherd").hasClass("ship-selected"));
            assertEquals("graphics/ships/shepherd.png", heroes.requireElement("ship-hero-shepherd-image").imageSource());
            assertTrue(actions.requireElement("ship-action-move").disabled());
            assertTrue(actions.requireElement("ship-action-build").hasClass("ship-selected"));
            click(actionDoc, actions.requireElement("ship-action-build"));
            assertEquals(1, requested.get());
            UiElement disabled = actions.requireElement("ship-action-move");
            var disabledBox = disabled.box().borderBox();
            actionDoc.pointerDown(disabledBox.x() + disabledBox.width() / 2f,
                    disabledBox.y() + disabledBox.height() / 2f);
            actionDoc.pointerUp(disabledBox.x() + disabledBox.width() / 2f,
                    disabledBox.y() + disabledBox.height() / 2f);
            assertEquals(1, requested.get());
            assertTrue(heroes.requireElement("ship-hero-shepherd-fill").box().borderBox().bottom()
                    <= BattleShipCommandOverlay.HERO_HEIGHT);
            assertEquals(24f, actions.requireElement("ship-command-hint").box().contentBox().height());
            assertTrue(actions.requireElement("ship-command-hint").box().borderBox().bottom()
                    <= BattleShipCommandOverlay.ACTION_HEIGHT);
            model.hover("ship-action-move");
            actions.flush();
            assertEquals("Grounded hull", actions.requireElement("ship-command-hint").text());
            model.hover(null);
            actions.flush();
            assertEquals("RMB move", actions.requireElement("ship-command-hint").text());
        }
    }
    private static void click(UiDocument document, UiElement element) {
        var box = element.box().borderBox();
        float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
        assertTrue(document.pointerDown(x, y));
        assertTrue(document.pointerUp(x, y));
    }
}
