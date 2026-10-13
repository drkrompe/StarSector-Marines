package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.ops.battleview.DrawList;
import com.dillon.starsectormarines.ops.battleview.MissionMarkerRenderSystem;
import com.dillon.starsectormarines.ops.battleview.RenderContext;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.WorldLabel;
import com.dillon.starsectormarines.render2d.BattleCamera;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BattleManualObjectiveOverlayTest {
    static MissionObjectiveSnapshot preview() {
        return new MissionObjectiveSnapshot(List.of(
                new Item("capture", "Supply Depot", State.ACTIVE, "CONTESTED", "Clear > Hold > Secure",
                        "Hold the marked room until the capture completes.", "Capture: 4 / 10 seconds", .4f,
                        "Clear the defenders from the marked room.",
                        List.of(new Marker("depot", "Supply Depot", 24, 18, "CONTESTED", State.ACTIVE)), "depot"),
                new Item("records", "Recover Records", State.ACTIVE, "APPROACH", "Seize > Extract",
                        "Reach the records room and remain inside to recover them.", "Seizure: 0 / 6 seconds", 0,
                        "Waiting for a friendly combatant at the records.",
                        List.of(new Marker("records", "Records", 85, 24, "REACH SITE", State.ACTIVE)), "records")), List.of());
    }

    @Test void worldLocatorsAndOffscreenDirectionsDoNotDependOnStrategicSelectionOrHighlights() {
        var camera = new BattleCamera(120, 60);
        camera.setViewport(0, 0, 1920, 1080, 30);
        camera.centerOn(32, 18);
        var context = new RenderContext(null, camera, null, 1, 0, false, null, null);
        var commands = new DrawList();
        new MissionMarkerRenderSystem().collect(preview(), context, commands);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < commands.count(RenderLayer.MISSION_MARKERS); i++) {
            var command = commands.buffer(RenderLayer.MISSION_MARKERS)[i];
            if (command.customDraw() instanceof WorldLabel label) labels.add(label.text());
        }
        assertTrue(labels.contains("Supply Depot"));
        assertTrue(labels.contains("Records"));
        assertTrue(labels.stream().anyMatch(text -> text.startsWith("OFF SCREEN /")));
    }

    @Test void projectsInstructionsBlockersAndProgressWithoutStrategicControls() throws Exception {
        var reactor = new Reactor();
        var model = new BattleManualObjectiveModel(reactor);
        model.update(preview());
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleManualObjectiveOverlay.COMPONENT_PATH));
        try (var markup = loader.reloadAndBuild(reactor, BattleManualObjectiveOverlay.COMPONENT, model.props())) {
            var document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(BattleManualObjectiveOverlay.WIDTH, BattleManualObjectiveOverlay.HEIGHT);
            assertEquals("0 / 2", markup.requireElement("manual-objective-score").text());
            assertEquals(preview().objectives().get(0).blocker(), markup.requireElement("mission-capture-blocker").text());
            assertEquals(preview().objectives().get(1).instruction(), markup.requireElement("mission-records-instruction").text());
            var list = markup.requireElement("manual-objective-list").box().borderBox();
            assertTrue(list.bottom() <= BattleManualObjectiveOverlay.HEIGHT);
            assertTrue(list.right() <= BattleManualObjectiveOverlay.WIDTH);
            assertTrue(markup.requireElement("mission-capture-progress").box().borderBox().width() > 0);
            model.update(MissionObjectiveSnapshot.EMPTY);
            markup.flush();
            assertEquals("0 / 0", markup.requireElement("manual-objective-score").text());
        }
    }
}
