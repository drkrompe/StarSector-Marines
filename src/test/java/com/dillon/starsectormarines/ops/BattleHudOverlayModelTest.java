package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.LiftQueue;
import com.dillon.starsectormarines.battle.air.LiftQueueReadout;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.ui.retained.UiWhiteSpace;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleHudOverlayModelTest {

    private static final float[] ROUTE = {-8f, 40f, -12f, 40f};
    /** The top-left commander readout's height before the lift queue replaced it. */
    private static final float COMMANDER_BOUND = 166f;

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
            assertFalse(track.displayed());
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
    void sideObjectivesSitInTheirOwnSectionUntalliedStruckWhenLapsedAndLocatable() throws Exception {
        Reactor reactor = new Reactor();
        var model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        var raid = new MissionObjectiveSnapshot.Item("raid", "Seize the ledger", MissionObjectiveSnapshot.State.ACTIVE,
                "APPROACH", "", "", "", -1f, "", List.of(), "");
        var marker = new MissionObjectiveSnapshot.Marker("side-feed-target", "Junction", 12, 34, "", MissionObjectiveSnapshot.State.ACTIVE);
        var done = new MissionObjectiveSnapshot.Item("side-feed", "Cutting the feed", MissionObjectiveSnapshot.State.COMPLETE,
                "Feed cut", "", "", "", -1f, "", List.of(marker), marker.id(), true);
        var lapsed = new MissionObjectiveSnapshot.Item("side-warden", "The warden's nod", MissionObjectiveSnapshot.State.FAILED,
                "Not done before the alarm", "", "", "", -1f, "", List.of(), "", true);
        var selected = new AtomicReference<MissionObjectiveSnapshot.Marker>();
        model.updateProjected(1, List.of());
        var state = model.updateMission(new MissionObjectiveSnapshot(List.of(raid, done, lapsed), List.of()), selected::set);
        assertEquals(2, state.optionalRows());
        assertTrue(BattleHudOverlay.documentHeight(state) > BattleHudOverlay.documentHeight(
                new BattleHudOverlayModel.Presentation(true, state.missionRows())));
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            var doc = document(instance, BattleHudOverlay.documentHeight(state));
            assertEquals("0 / 1", instance.requireElement("mission-score").text(), "a done side objective is not tallied");
            assertEquals(1, instance.requireElement("battle-mission-list").childCount());
            assertTrue(instance.requireElement("mission-optional-heading").displayed());
            assertTrue(instance.requireElement("mission-side-warden-strike").displayed(), "a lapsed job is struck");
            assertFalse(instance.requireElement("mission-side-feed-strike").displayed());
            var locate = instance.requireElement("mission-side-feed-locate").box().borderBox();
            float x = locate.x() + locate.width() / 2, y = locate.y() + locate.height() / 2;
            assertTrue(doc.pointerDown(x, y));
            assertTrue(doc.pointerUp(x, y));
            assertEquals(12, selected.get().x());
        }
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
            assertTrue(instance.requireElement("battle-lift-queue")
                    .hasClass("lift-queue-hidden"));
        }
    }

    @Test
    void liftQueueListsCraftThenWaitingJobsAndItsXCancelsOnlyAQueuedDrop() throws Exception {
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        LiftQueue.MachineDrop hound = new LiftQueue.MachineDrop(7L, "mech_support", 0,
                ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.HOUND)), 40f, 40f, ROUTE, null);
        LiftQueueReadout.Reading reading = new LiftQueueReadout.Reading(
                List.of(new LiftQueueReadout.Flight(1L, ShuttleType.AEROSHUTTLE,
                        LiftQueueReadout.Cargo.SQUAD, 6, null, 7.4f, false)),
                List.of(new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.SQUAD, 2, null, 19f),
                        new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.MACHINE, 1, hound, 62f)));
        AtomicLong cancelled = new AtomicLong();

        int rows = model.updateLiftQueue(reading, List.of(), cancelled::set, ignored -> { });

        assertEquals(3, rows);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleHudOverlay.COMPONENT, model.props())) {
            UiDocument document = document(instance, BattleHudOverlay.liftHeight(rows));
            assertEquals(3, instance.requireElement("battle-lift-queue-rows").childCount());
            assertFalse(instance.requireElement("battle-lift-air-1-cancel").displayed(),
                    "a craft in the air cannot be called back");
            assertFalse(instance.requireElement("battle-lift-squads-cancel").displayed());
            assertTrue(BattleHudOverlay.insideInteractiveSurface(instance, 10f, 10f));

            var x = instance.requireElement("battle-lift-drop-7-cancel").box().borderBox();
            float clickX = x.x() + x.width() / 2, clickY = x.y() + x.height() / 2;
            var panel = instance.requireElement("battle-lift-queue").box().borderBox();
            var last = instance.requireElement("battle-lift-drop-7").box().borderBox();
            assertEquals(BattleHudOverlay.liftHeight(rows), panel.height(), 0.01f);
            assertTrue(last.bottom() <= panel.bottom() && last.right() <= panel.right(),
                    "row " + last + " inside the panel " + panel);
            assertTrue(x.bottom() <= last.bottom() && x.right() <= last.right(),
                    "X " + x + " inside its row " + last);
            assertTrue(document.pointerDown(clickX, clickY));
            assertTrue(document.pointerUp(clickX, clickY));
            assertEquals(7L, cancelled.get());

            model.updateLiftQueue(LiftQueueReadout.Reading.EMPTY, List.of(), cancelled::set, ignored -> { });
            instance.flush();
            document.advance(0f);
            assertTrue(instance.requireElement("battle-lift-queue").hasClass("lift-queue-hidden"));
        }
    }

    @Test
    void aLongQueueKeepsItsRowsAndFoldsTheRestIntoACount() {
        BattleHudOverlayModel model = new BattleHudOverlayModel(new Reactor(),
                ignored -> { }, "Pause", "1x", "2x", "4x");
        List<LiftQueueReadout.Waiting> waiting = new ArrayList<>();
        for (int i = 0; i < BattleHudOverlayModel.MAX_LIFT_ROWS + 3; i++) {
            waiting.add(new LiftQueueReadout.Waiting(LiftQueueReadout.Cargo.MACHINE, 1,
                    new LiftQueue.MachineDrop(i + 1L, "mech_support", i,
                            ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.HOUND)), 40f, 40f, ROUTE, null),
                    10f * i));
        }

        List<BattleHudOverlayModel.LiftRow> rows = model.liftRows(
                new LiftQueueReadout.Reading(List.of(), waiting), List.of(), ignored -> { }, ignored -> { });

        assertEquals(BattleHudOverlayModel.MAX_LIFT_ROWS, rows.size());
        assertEquals("battle-lift-more", rows.get(rows.size() - 1).id());
        assertFalse(rows.get(rows.size() - 1).cancellable());
        assertTrue(BattleHudOverlay.liftHeight(BattleHudOverlayModel.MAX_LIFT_ROWS) <= COMMANDER_BOUND,
                "the full queue fits where the commander readout stood");
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

    private static BattleHudOverlayModel.CaptureObjective objective(
            TacticalNode.Kind kind, CompoundService.CompoundState state,
            float progress) {
        return new BattleHudOverlayModel.CaptureObjective(kind, state, progress);
    }
}
