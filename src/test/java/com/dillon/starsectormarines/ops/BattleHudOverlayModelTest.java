package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.ui.retained.UiWhiteSpace;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
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
    void untimedSharedObjectiveCollapsesProgressTrackAndNeverRendersNegativeWidth() throws Exception {
        var reactor = new Reactor();
        var model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        var untimed = new MissionObjectiveSnapshot.Item("construction", "Road gun", MissionObjectiveSnapshot.State.ACTIVE,
                "CONSTRUCTION", "Build > Repair", "Place the turret", "Awaiting construction", -1f, "",
                List.of(), "");
        model.updateProjected(1, List.of());
        var presentation = model.updateMission(new MissionObjectiveSnapshot(List.of(untimed), List.of()), ignored -> { });
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            var doc = document(instance, BattleHudOverlay.documentHeight(presentation));
            var track = instance.requireElement("mission-construction-track");
            var fill = instance.requireElement("mission-construction-progress");
            assertEquals(0f, track.box().borderBox().height(), .01f);
            assertEquals(0f, track.opacity(), .01f);
            assertEquals(0f, fill.box().borderBox().width(), .01f);
            assertTrue(fill.box().borderBox().width() >= 0f);
            assertEquals("Awaiting construction", instance.requireElement("mission-construction-progressText").text());
            var timed = new MissionObjectiveSnapshot.Item(untimed.id(), untimed.name(), untimed.state(),
                    "REPAIR", untimed.steps(), "Cover the repair", "Repair / 50%", .5f, "", List.of(), "");
            model.updateMission(new MissionObjectiveSnapshot(List.of(timed), List.of()), ignored -> { });
            instance.flush();
            doc.advance(0);
            track = instance.requireElement("mission-construction-track");
            fill = instance.requireElement("mission-construction-progress");
            assertTrue(track.box().borderBox().height() > 0f);
            assertEquals(1f, track.opacity(), .01f);
            assertTrue(fill.box().borderBox().width() > 0f);
            assertTrue(fill.box().borderBox().width() < track.box().borderBox().width());
        }
    }

    @Test
    void sharedTerritoryPicturePreservesConquestCommandAndCollapsesDuplicateSquadPanel() throws Exception {
        var reactor = new Reactor();
        var model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1, List.of(), conquestCommander());
        var territory = new MissionObjectiveSnapshot.Item("territory-dispatch", "Dispatch", MissionObjectiveSnapshot.State.ACTIVE,
                "CAPTURING", "Clear > Hold", "Hold the interior", "Capture / 50%", .5f, "", List.of(), "");
        var presentation = model.updateMission(new MissionObjectiveSnapshot(List.of(territory), List.of()), ignored -> { });
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            var doc = document(instance, BattleHudOverlay.documentHeight(presentation));
            assertFalse(instance.requireElement("battle-conquest-command").hasClass("conquest-command-panel-hidden"));
            assertEquals("SHIFTING SUPPORT", instance.requireElement("battle-conquest-command-phase").text());
            assertEquals(3, instance.requireElement("battle-conquest-lanes").childCount());
            assertEquals(1, instance.requireElement("battle-mission-list").childCount());
            var squads = instance.requireElement("battle-mission-squads");
            assertTrue(squads.hasClass("mission-hidden"));
            assertEquals(0f, squads.box().borderBox().height(), .01f);
            assertEquals(0f, squads.box().borderBox().width(), .01f);
            // Territory keeps its existing commander even if shared activities arrive.
            model.updateMission(new MissionObjectiveSnapshot(List.of(territory), List.of(
                    new MissionObjectiveSnapshot.SquadActivity(1, "First", "Clear dispatch", "Moving"))), ignored -> { });
            instance.flush();
            doc.advance(0);
            assertTrue(instance.requireElement("battle-mission-squads").hasClass("mission-hidden"));
            assertFalse(instance.requireElement("battle-conquest-command").hasClass("conquest-command-panel-hidden"));
            assertEquals(3, instance.requireElement("battle-conquest-lanes").childCount());
        }
    }

    @Test
    void sharedObjectivesLocateTheirOwnBranchAndResetCleanlyForOtherMissions() throws Exception {
        Reactor reactor = new Reactor();
        var model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        var item = MissionObjectiveProjection.payload(new ColonyArchiveObjective(40, 50, 1),
                new CivilianEvacuationSystem.Progress(false, 0, false, false, 0, 0, 0, 0));
        var selected = new AtomicReference<MissionObjectiveSnapshot.Marker>();
        model.updateProjected(1, List.of());
        var state = model.updateMission(new MissionObjectiveSnapshot(List.of(item), List.of()), selected::set);
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            var doc = document(instance, BattleHudOverlay.documentHeight(state));
            assertEquals(0f, instance.requireElement("mission-COLONY-ARCHIVE-progress").box().borderBox().width(), .01f);
            assertEquals("sealed colony archive", instance.requireElement("mission-COLONY-ARCHIVE-name").text());
            assertEquals(UiWhiteSpace.NORMAL, instance.requireElement("mission-COLONY-ARCHIVE-instruction").whiteSpace());
            var locate = instance.requireElement("mission-COLONY-ARCHIVE-locate").box().borderBox();
            float x = locate.x() + locate.width() / 2, y = locate.y() + locate.height() / 2;
            assertTrue(doc.pointerDown(x, y));
            assertTrue(doc.pointerUp(x, y));
            assertEquals(40, selected.get().x());
            assertEquals(50, selected.get().y());
            assertFalse(BattleHudOverlay.insideInteractiveSurface(instance, 600, 100), "middle battlefield stays clickable");
            model.updateProjected(1, List.of(new BattleHudOverlayModel.CaptureObjective(
                    TacticalNode.Kind.COMMAND_POST, CompoundService.CompoundState.DEFENDER_HELD, 0)));
            instance.flush();
            doc.layout(1896, BattleHudOverlay.OBJECTIVE_HEIGHT);
            assertEquals(0, instance.requireElement("battle-mission-list").childCount());
            assertEquals(0, instance.requireElement("battle-mission-objectives").box().borderBox().height());
            assertFalse(instance.requireElement("battle-objectives").hasClass("objective-panel-hidden"));
        }
    }


    @Test
    void publishedGroupsReplaceLaneRowsWithNamesTasksAndActualLivingStrength() throws Exception {
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of(), groupedCommander(List.of(
                group("officer:a", "a", "Captain Vale", List.of(1, 2), "ASSAULT", true),
                group("officer:b", "b", "Captain Reed", List.of(3), "HOLD", false))));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            UiDocument document = document(instance, BattleHudOverlay.COMMAND_ONLY_HEIGHT);
            assertEquals("8 SQUADS  ·  2 GROUPS  ·  1 RESERVE",
                    instance.requireElement("battle-conquest-command-force").text());
            assertEquals(0, instance.requireElement("battle-conquest-lanes").childCount());
            assertTrue(instance.requireElement("battle-conquest-lanes").hasClass("conquest-lanes-hidden"));
            assertEquals(2, instance.requireElement("battle-conquest-groups").childCount());
            assertEquals("Captain Vale", instance.requireElement("battle-conquest-group-0-name").text());
            assertEquals("2 SQ · 11 LIVE", instance.requireElement("battle-conquest-group-0-force").text());
            assertEquals("ASSAULT · RALLY", instance.requireElement("battle-conquest-group-0-intent").text());
            assertEquals("OBJECTIVE AREA", instance.requireElement("battle-conquest-group-0-target").text());
            assertEquals("HOLD", instance.requireElement("battle-conquest-group-1-intent").text());
            model.updateProjected(1f, List.of(), conquestCommander());
            instance.flush();
            document.advance(0);
            assertEquals(3, instance.requireElement("battle-conquest-lanes").childCount());
            assertEquals(0, instance.requireElement("battle-conquest-groups").childCount());
            assertTrue(instance.requireElement("battle-conquest-groups").hasClass("conquest-groups-hidden"));
        }
    }

    @Test
    void manyNcoGroupsAggregateExplicitlyWithinThreeRows() throws Exception {
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of(), groupedCommander(List.of(
                group("nco:1", null, "First / acting NCO", List.of(4), "HOLD", false),
                group("officer:a", "a", "Captain Vale", List.of(1), "ASSAULT", false),
                group("nco:2", null, "Second / acting NCO", List.of(5), "ASSAULT", true),
                group("officer:b", "b", "Captain Reed", List.of(2), "MIXED", false),
                group("nco:3", null, "Third / acting NCO", List.of(6), "ASSAULT", false))));
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            document(instance, BattleHudOverlay.COMMAND_ONLY_HEIGHT);
            assertEquals(3, instance.requireElement("battle-conquest-groups").childCount());
            assertEquals("Captain Vale", instance.requireElement("battle-conquest-group-0-name").text());
            assertEquals("Captain Reed", instance.requireElement("battle-conquest-group-1-name").text());
            assertEquals("3 ACTING NCO GROUPS", instance.requireElement("battle-conquest-group-summary-name").text());
            assertEquals("3 SQ", instance.requireElement("battle-conquest-group-summary-force").text());
            assertEquals("1 HOLD · 2 ASSAULT", instance.requireElement("battle-conquest-group-summary-intent").text());
            assertEquals("1 RALLYING", instance.requireElement("battle-conquest-group-summary-target").text());
            UiElement last = instance.requireElement("battle-conquest-group-summary");
            assertTrue(last.box().borderBox().bottom()
                    <= instance.requireElement("battle-conquest-command").box().borderBox().bottom());
        }
    }

    private static ConquestFrontSnapshot.GroupState group(String id, String officer,
            String label, List<Integer> squadIds, String task, boolean rallying) {
        return new ConquestFrontSnapshot.GroupState(
                new CommandGroupIdentity(id, officer, label, officer != null), squadIds,
                task, AssignmentKind.CLEAR_ZONE, 100, -1, -1,
                "GROUP_TASK_PRESERVED", rallying);
    }

    private static CommanderSnapshot<ConquestFrontSnapshot> groupedCommander(
            List<ConquestFrontSnapshot.GroupState> groups) {
        CommanderSnapshot<ConquestFrontSnapshot> base = conquestCommander();
        ConquestFrontSnapshot front = base.detail();
        ConquestFrontSnapshot grouped = new ConquestFrontSnapshot(front.tick(),
                front.influenceTick(), front.perspective(), front.axis(), front.phase(),
                front.remainingCompounds(), front.keepZoneId(), front.keepState(), front.tracks(),
                List.of(new ConquestFrontSnapshot.SquadState(1, 6, 0, 0, 0, null, false),
                        new ConquestFrontSnapshot.SquadState(2, 5, 0, 0, 0, null, false),
                        new ConquestFrontSnapshot.SquadState(3, 4, 0, 0, 0, null, false)),
                front.directives(), groups);
        return new CommanderSnapshot<>(Faction.MARINE, "conquest-command", "FRONT_ADJUST",
                90, 75, 8, 1, List.of(), List.of(), grouped);
    }

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
        document.layout(1896, height); // 1920px screen with the production 12px side insets, 100% scale.
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
