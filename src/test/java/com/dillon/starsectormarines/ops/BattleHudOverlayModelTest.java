package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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

class BattleHudOverlayModelTest {

    @Test
    void mlxProjectsSelectedSpeedAndCompressedCaptureState() throws Exception {
        Reactor reactor = new Reactor();
        AtomicReference<Float> selectedSpeed = new AtomicReference<>(1f);
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                selectedSpeed::set, "Pause", "1x", "2x", "4x");
        model.updateProjected(2f, List.of(
                objective(TacticalNode.Kind.COMMAND_POST,
                        CompoundService.CompoundState.MARINE_HELD, 0f),
                objective(TacticalNode.Kind.BARRACKS,
                        CompoundService.CompoundState.CONTESTED, 0.46f),
                objective(TacticalNode.Kind.BARRACKS,
                        CompoundService.CompoundState.DEFENDER_HELD, 0f),
                objective(TacticalNode.Kind.ARMORY,
                        CompoundService.CompoundState.MARINE_HELD, 0f),
                objective(TacticalNode.Kind.ARMORY,
                        CompoundService.CompoundState.DEFENDER_HELD, 0f)));

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleHudOverlay.COMPONENT, model.props())) {
            UiDocument document = document(instance);

            UiElement selectedDouble = instance.requireElement("battle-time-double");
            assertTrue(selectedDouble.hasClass("time-selected"));
            assertEquals(new Color(0xFF, 0xD4, 0x64), selectedDouble.borderColor());
            assertEquals(new Color(0xFF, 0xD4, 0x64), selectedDouble.textColor());
            assertFalse(instance.requireElement("battle-time-normal").hasClass("time-selected"));
            assertEquals("2 / 5", instance.requireElement("battle-objective-score").text());
            assertEquals("2 SECURE  ·  1 CONTESTED  ·  2 HOSTILE",
                    instance.requireElement("battle-objective-tally").text());
            assertEquals(5, instance.requireElement("battle-objective-chips").childCount());
            assertEquals("BARRACKS 1  ·  CONTESTED 46%",
                    instance.requireElement("battle-objective-focus-label").text());

            UiElement track = instance.requireElement("battle-objective-progress");
            UiElement fill = instance.requireElement("battle-objective-progress-fill");
            assertTrue(fill.box().borderBox().width() > 0f);
            assertTrue(fill.box().borderBox().width()
                    < track.box().borderBox().width());

            UiElement quad = instance.requireElement("battle-time-quad");
            float clickX = quad.box().borderBox().x()
                    + quad.box().borderBox().width() * 0.5f;
            float clickY = quad.box().borderBox().y()
                    + quad.box().borderBox().height() * 0.5f;
            assertTrue(document.pointerDown(clickX, clickY));
            assertTrue(document.pointerUp(clickX, clickY));
            assertEquals(4f, selectedSpeed.get());

            model.updateProjected(0f, List.of());
            instance.flush();
            document.advance(0f);
            assertTrue(instance.requireElement("battle-time-pause").hasClass("time-selected"));
            assertTrue(instance.requireElement("battle-objectives")
                    .hasClass("objective-panel-hidden"));
            assertEquals(0, instance.requireElement("battle-objective-chips").childCount());
        }
    }

    private static UiDocument document(MarkupInstance instance) {
        BattleHudOverlay.wireLayout(instance);
        UiDocument document = new UiDocument(instance.root());
        for (var style : instance.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(BattleHudOverlay.DOCUMENT_WIDTH,
                BattleHudOverlay.OBJECTIVE_HEIGHT);
        return document;
    }

    private static BattleHudOverlayModel.CaptureObjective objective(
            TacticalNode.Kind kind, CompoundService.CompoundState state,
            float progress) {
        return new BattleHudOverlayModel.CaptureObjective(kind, state, progress);
    }
}
