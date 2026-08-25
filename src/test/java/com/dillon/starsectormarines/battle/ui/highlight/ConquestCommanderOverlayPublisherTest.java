package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestCommanderOverlayPublisherTest {

    @Test
    void southNorthPictureUsesOnlyPublishedTracksFrontsAndTargets() {
        ConquestFrontSnapshot.TrackState first = track(0, 0, 3,
                0.25f, 0.5f, 0.75f);
        ConquestFrontSnapshot.TrackState second = track(1, 4, 7,
                -1f, -1f, -1f);
        ConquestFrontSnapshot.SquadDirective directive =
                new ConquestFrontSnapshot.SquadDirective(14, 0, 1,
                        ConquestFrontSnapshot.AssignmentReason.TRACK_ADVANCE,
                        AssignmentKind.CLEAR_ZONE, 3, 8, 7);
        CommanderSnapshot<ConquestFrontSnapshot> commander = commander(
                Faction.MARINE, TraversalAxis.SOUTH_TO_NORTH,
                List.of(first, second), List.of(directive));
        HighlightOverlay overlay = new HighlightOverlay();

        ConquestCommanderOverlayPublisher.publish(overlay, commander,
                12, 9, 14);

        List<CellHighlight> tracks = overlay.source(
                HighlightOverlay.SRC_CONQUEST_TRACKS);
        assertEquals(2, tracks.size());
        assertRect(tracks.get(0), 0, 0, 4, 9);
        assertRect(tracks.get(1), 4, 0, 4, 9);

        List<CellHighlight> fronts = overlay.source(
                HighlightOverlay.SRC_CONQUEST_FRONTS);
        assertEquals(3, fronts.size());
        assertRect(fronts.get(0), 0, 2, 4, 1);
        assertRect(fronts.get(1), 0, 4, 4, 1);
        assertRect(fronts.get(2), 0, 6, 4, 1);
        assertRect(overlay.source(HighlightOverlay.SRC_CONQUEST_ACTIONS).get(0),
                8, 7, 1, 1);
        assertRect(overlay.source(
                HighlightOverlay.SRC_CONQUEST_SELECTED_TRACK).get(0),
                4, 0, 4, 9);
        assertRect(overlay.source(
                HighlightOverlay.SRC_CONQUEST_SELECTED_ACTION).get(0),
                8, 7, 1, 1);
    }

    @Test
    void westEastPictureRotatesTracksAndProgressBars() {
        CommanderSnapshot<ConquestFrontSnapshot> commander = commander(
                Faction.DEFENDER, TraversalAxis.WEST_TO_EAST,
                List.of(track(0, 2, 5, 0f, 0.5f, 1f)), List.of());
        HighlightOverlay overlay = new HighlightOverlay();

        ConquestCommanderOverlayPublisher.publish(overlay, commander,
                9, 12, -1);

        assertRect(overlay.source(HighlightOverlay.SRC_CONQUEST_TRACKS).get(0),
                0, 2, 9, 4);
        List<CellHighlight> fronts = overlay.source(
                HighlightOverlay.SRC_CONQUEST_FRONTS);
        assertRect(fronts.get(0), 0, 2, 1, 4);
        assertRect(fronts.get(1), 4, 2, 1, 4);
        assertRect(fronts.get(2), 8, 2, 1, 4);
        assertTrue(overlay.source(
                HighlightOverlay.SRC_CONQUEST_SELECTED_TRACK).isEmpty());
    }

    @Test
    void absentOrDisabledPictureClearsEveryConquestSource() {
        HighlightOverlay overlay = new HighlightOverlay();
        CommanderSnapshot<ConquestFrontSnapshot> commander = commander(
                Faction.MARINE, TraversalAxis.SOUTH_TO_NORTH,
                List.of(track(0, 0, 3, 0f, 0f, 0f)), List.of());
        ConquestCommanderOverlayPublisher.publish(overlay, commander,
                12, 9, -1);

        ConquestCommanderOverlayPublisher.publish(overlay, null,
                12, 9, -1);

        assertTrue(overlay.source(HighlightOverlay.SRC_CONQUEST_TRACKS).isEmpty());
        assertTrue(overlay.source(HighlightOverlay.SRC_CONQUEST_FRONTS).isEmpty());
        assertTrue(overlay.source(HighlightOverlay.SRC_CONQUEST_ACTIONS).isEmpty());
        assertTrue(overlay.source(
                HighlightOverlay.SRC_CONQUEST_SELECTED_ACTION).isEmpty());
    }

    private static ConquestFrontSnapshot.TrackState track(
            int index, int start, int end, float body, float lead,
            float hostile) {
        return new ConquestFrontSnapshot.TrackState(index, start, end,
                1, 1, 12, body, lead, hostile, hostile >= 0f ? 2 : 0,
                5f, 3f, 8);
    }

    private static CommanderSnapshot<ConquestFrontSnapshot> commander(
            Faction side, TraversalAxis axis,
            List<ConquestFrontSnapshot.TrackState> tracks,
            List<ConquestFrontSnapshot.SquadDirective> directives) {
        ConquestFrontSnapshot detail = new ConquestFrontSnapshot(75, 60,
                side, axis, ConquestFrontSnapshot.Phase.LANE_ADVANCE,
                3, 9, CompoundService.CompoundState.DEFENDER_HELD,
                tracks, directives);
        return new CommanderSnapshot<>(side, "conquest", "LANE_ADVANCE",
                75, 60, directives.size(), 0, List.of(), List.of(), detail);
    }

    private static void assertRect(CellHighlight cell, int x, int y,
                                   int width, int height) {
        assertEquals(x, cell.cellX);
        assertEquals(y, cell.cellY);
        assertEquals(width, cell.width);
        assertEquals(height, cell.height);
    }
}
