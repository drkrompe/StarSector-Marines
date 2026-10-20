package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Status;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BattleOrderOverlayModelTest {
    @Test void defendOrderFitsAndDispatchesWithVisibleScopeAndPlacementFeedback() throws Exception {
        Reactor reactor = new Reactor();
        List<Mode> actions = new ArrayList<>();
        var model = new BattleOrderOverlayModel(reactor, actions::add);
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleOrderOverlay.COMPONENT_PATH));
        try (var markup = loader.reloadAndBuild(reactor, BattleOrderOverlay.COMPONENT, model.props())) {
            UiDocument document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            model.update(3, false, Mode.DEFEND_AREA);
            markup.flush();
            document.layout(BattleOrderOverlay.WIDTH, BattleOrderOverlay.HEIGHT);
            assertEquals("3 SQUADS", markup.requireElement("battle-order-scope").text());
            assertEquals("ALL SELECTED", markup.requireElement("battle-order-defend-scope").text());
            assertTrue(markup.requireElement("battle-order-hint").text().contains("CANCEL"));
            for (String id : List.of("battle-order-defend")) {
                var box = markup.requireElement(id).box().borderBox();
                assertTrue(box.x() >= 0 && box.right() <= BattleOrderOverlay.WIDTH);
                assertTrue(box.y() >= 0 && box.bottom() <= BattleOrderOverlay.HEIGHT);
                document.pointerDown(box.x() + 12, box.y() + 12);
                document.pointerUp(box.x() + 12, box.y() + 12);
            }
            assertEquals(List.of(Mode.DEFEND_AREA), actions);
            model.update(1, true, Mode.NONE);
            markup.flush();
            assertEquals("MECH / LANCE", markup.requireElement("battle-order-scope").text());
            assertEquals("WHOLE LANCE", markup.requireElement("battle-order-defend-scope").text());
            assertEquals("RMB: MOVE", markup.requireElement("battle-order-hint").text());
        }
    }

    @Test void garrisonCardDispatchesItsToggleAndSaysWhatTheStanceIsDoing() throws Exception {
        Reactor reactor = new Reactor();
        int[] toggles = {0};
        var model = new BattleOrderOverlayModel(reactor, mode -> { }, () -> toggles[0]++);
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleOrderOverlay.COMPONENT_PATH));
        try (var markup = loader.reloadAndBuild(reactor, BattleOrderOverlay.COMPONENT, model.props())) {
            UiDocument document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            model.update(1, false, Mode.NONE, List.of(Status.MANNING_TRENCH));
            markup.flush();
            document.layout(BattleOrderOverlay.WIDTH, BattleOrderOverlay.HEIGHT);
            assertEquals("MANNING TRENCH", markup.requireElement("battle-order-garrison-detail").text());
            var box = markup.requireElement("battle-order-garrison").box().borderBox();
            assertTrue(box.x() >= 0 && box.right() <= BattleOrderOverlay.WIDTH);
            assertTrue(box.y() >= 0 && box.bottom() <= BattleOrderOverlay.HEIGHT);
            document.pointerDown(box.x() + 12, box.y() + 12);
            document.pointerUp(box.x() + 12, box.y() + 12);
            assertEquals(1, toggles[0]);
            model.update(1, true, Mode.NONE, List.of());
            markup.flush();
            assertEquals("INFANTRY ONLY", markup.requireElement("battle-order-garrison-detail").text());
        }
        assertEquals("OFF", BattleOrderOverlayModel.garrisonDetail(List.of(Status.OFF, Status.OFF)));
        assertEquals("1 OF 2 WATCHING", BattleOrderOverlayModel.garrisonDetail(List.of(Status.OFF, Status.RETURNING)));
    }
}
