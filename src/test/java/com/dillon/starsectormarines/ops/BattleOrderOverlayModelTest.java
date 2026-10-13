package com.dillon.starsectormarines.ops;

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
    @Test void bothOrdersFitAndDispatchWithVisibleScopeAndPlacementFeedback() throws Exception {
        Reactor reactor = new Reactor();
        List<Mode> actions = new ArrayList<>();
        var model = new BattleOrderOverlayModel(reactor, actions::add);
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleOrderOverlay.COMPONENT_PATH));
        try (var markup = loader.reloadAndBuild(reactor, BattleOrderOverlay.COMPONENT, model.props())) {
            UiDocument document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            model.update(3, false, Mode.ATTACK_MOVE);
            markup.flush();
            document.layout(BattleOrderOverlay.WIDTH, BattleOrderOverlay.HEIGHT);
            assertEquals("3 SQUADS", markup.requireElement("battle-order-scope").text());
            assertEquals("ALL SELECTED", markup.requireElement("battle-order-defend-scope").text());
            assertTrue(markup.requireElement("battle-order-hint").text().contains("CANCEL"));
            for (String id : List.of("battle-order-attack", "battle-order-defend")) {
                var box = markup.requireElement(id).box().borderBox();
                assertTrue(box.x() >= 0 && box.right() <= BattleOrderOverlay.WIDTH);
                assertTrue(box.y() >= 0 && box.bottom() <= BattleOrderOverlay.HEIGHT);
                document.pointerDown(box.x() + 12, box.y() + 12);
                document.pointerUp(box.x() + 12, box.y() + 12);
            }
            assertEquals(List.of(Mode.ATTACK_MOVE, Mode.DEFEND_AREA), actions);
            model.update(1, true, Mode.NONE);
            markup.flush();
            assertEquals("SELECTED MECH", markup.requireElement("battle-order-attack-scope").text());
            assertEquals("WHOLE LANCE", markup.requireElement("battle-order-defend-scope").text());
            assertEquals("RMB: MOVE", markup.requireElement("battle-order-hint").text());
        }
    }
}
