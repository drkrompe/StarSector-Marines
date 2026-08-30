package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
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

            assertEquals(0, instance.requireElement("battle-hud-overlay")
                    .background().getAlpha());
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

            UiElement root = instance.requireElement("battle-hud-overlay");
            float middleX = root.box().borderBox().width() * 0.5f;
            assertFalse(BattleHudOverlay.insideInteractiveSurface(
                    instance, middleX, 10f));
            assertFalse(BattleHudOverlay.insideInteractiveSurface(
                    instance, 10f, 10f));
            assertTrue(BattleHudOverlay.insideInteractiveSurface(
                    instance, root.box().borderBox().right() - 10f, 10f));

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
            assertTrue(instance.requireElement("battle-conquest-command")
                    .hasClass("conquest-command-panel-hidden"));
        }
    }

    @Test
    void mlxProjectsMarineConquestCommandAcrossThreeLanes() throws Exception {
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        BattleHudOverlayModel.Presentation presentation = model.updateProjected(
                1f, List.of(), conquestCommander());

        assertFalse(presentation.objectivesVisible());
        assertTrue(presentation.commandVisible());
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleHudOverlay.COMPONENT, model.props())) {
            document(instance, BattleHudOverlay.COMMAND_ONLY_HEIGHT);

            assertFalse(instance.requireElement("battle-conquest-command")
                    .hasClass("conquest-command-panel-hidden"));
            assertEquals("SHIFTING SUPPORT",
                    instance.requireElement("battle-conquest-command-phase").text());
            assertEquals("8 SQUADS  ·  1 RESERVE",
                    instance.requireElement("battle-conquest-command-force").text());
            assertEquals(3, instance.requireElement("battle-conquest-lanes").childCount());
            assertEquals("3 SQ · 31",
                    instance.requireElement("battle-conquest-lane-0-force").text());
            assertEquals("SECURE x2",
                    instance.requireElement("battle-conquest-lane-0-intent").text());
            assertEquals("2 CONTACTS",
                    instance.requireElement("battle-conquest-lane-0-status").text());
            assertEquals("ADVANCE x3",
                    instance.requireElement("battle-conquest-lane-1-intent").text());
            assertEquals("OBJECTIVE",
                    instance.requireElement("battle-conquest-lane-2-status").text());
            UiElement root = instance.requireElement("battle-hud-overlay");
            assertTrue(BattleHudOverlay.insideInteractiveSurface(
                    instance, 10f, 10f));
            assertFalse(BattleHudOverlay.insideInteractiveSurface(
                    instance, root.box().borderBox().width() * 0.5f, 10f));
            assertTrue(BattleHudOverlay.insideInteractiveSurface(
                    instance, root.box().borderBox().right() - 10f, 10f));
        }
    }

    private static UiDocument document(MarkupInstance instance) {
        return document(instance, BattleHudOverlay.OBJECTIVE_HEIGHT);
    }

    private static UiDocument document(MarkupInstance instance, float height) {
        BattleHudOverlay.wireLayout(instance);
        UiDocument document = new UiDocument(instance.root());
        for (var style : instance.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(BattleHudOverlay.RAIL_WIDTH * 2f + 240f, height);
        return document;
    }

    private static CommanderSnapshot<ConquestFrontSnapshot> conquestCommander() {
        List<ConquestFrontSnapshot.TrackState> tracks = List.of(
                track(0, 3, 31, 2, 100),
                track(1, 3, 30, 0, -1),
                track(2, 2, 18, 0, 102));
        List<ConquestFrontSnapshot.SquadDirective> directives = List.of(
                directive(1, 0, AssignmentKind.SECURE_COMPOUND, 100),
                directive(2, 0, AssignmentKind.SECURE_COMPOUND, 100),
                directive(3, 0, AssignmentKind.ATTACK_MOVE, -1),
                directive(4, 1, AssignmentKind.ADVANCE_TRACK, -1),
                directive(5, 1, AssignmentKind.ADVANCE_TRACK, -1),
                directive(6, 1, AssignmentKind.ADVANCE_TRACK, -1),
                directive(7, 2, AssignmentKind.SUPPORT, 102),
                directive(8, 2, AssignmentKind.SUPPORT, 102));
        ConquestFrontSnapshot front = new ConquestFrontSnapshot(
                90, 75, Faction.MARINE, TraversalAxis.SOUTH_TO_NORTH,
                ConquestFrontSnapshot.Phase.FRONT_ADJUST, 4, 120,
                CompoundService.CompoundState.DEFENDER_HELD,
                tracks, List.of(), directives);
        return new CommanderSnapshot<>(Faction.MARINE, "conquest-command",
                "FRONT_ADJUST", 90, 75, 8, 1,
                List.of("remaining compounds=4"), List.of(), front);
    }

    private static ConquestFrontSnapshot.TrackState track(
            int index, int squads, int members, int contacts, int targetZone) {
        return new ConquestFrontSnapshot.TrackState(index, index * 20,
                index * 20 + 19, squads, squads, members,
                0.42f, 0.55f, contacts > 0 ? 0.61f : -1f,
                contacts, 12f, contacts * 7f, targetZone);
    }

    private static ConquestFrontSnapshot.SquadDirective directive(
            int squadId, int track, AssignmentKind kind, int targetZone) {
        return new ConquestFrontSnapshot.SquadDirective(squadId, track, track,
                ConquestFrontSnapshot.AssignmentReason.TRACK_ADVANCE,
                kind, targetZone);
    }

    private static BattleHudOverlayModel.CaptureObjective objective(
            TacticalNode.Kind kind, CompoundService.CompoundState state,
            float progress) {
        return new BattleHudOverlayModel.CaptureObjective(kind, state, progress);
    }
}
