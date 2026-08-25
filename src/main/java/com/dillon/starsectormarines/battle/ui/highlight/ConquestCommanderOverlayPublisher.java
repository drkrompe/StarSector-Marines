package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects one published Conquest command picture into world-cell debug marks.
 * The publisher deliberately accepts no battle view: lane fronts, hostile
 * belief, and action targets can only come from the selected perspective's
 * immutable post-commit snapshot.
 */
@DebugOnly
public final class ConquestCommanderOverlayPublisher {

    private static final Color MARINE_TRACK_A = new Color(0x38, 0x78, 0xA8, 0x38);
    private static final Color MARINE_TRACK_B = new Color(0x48, 0x98, 0xC8, 0x38);
    private static final Color DEFENDER_TRACK_A = new Color(0xB0, 0x68, 0x38, 0x38);
    private static final Color DEFENDER_TRACK_B = new Color(0xD0, 0x88, 0x48, 0x38);
    private static final Color FRIENDLY_BODY = new Color(0x40, 0xC8, 0xFF, 0xB8);
    private static final Color FRIENDLY_LEAD = new Color(0x70, 0xF0, 0x98, 0xE0);
    private static final Color KNOWN_HOSTILE_FRONT = new Color(0xFF, 0x58, 0xB0, 0xE0);
    private static final Color ACTION_TARGET = new Color(0xFF, 0xE0, 0x50, 0xFF);
    private static final Color SELECTED_TRACK = new Color(0xFF, 0xF0, 0x80, 0x70);
    private static final Color SELECTED_ACTION = new Color(0xFF, 0xFF, 0xFF, 0xFF);

    private ConquestCommanderOverlayPublisher() { }

    public static void publish(HighlightOverlay overlay,
                               CommanderSnapshot<?> commander,
                               int mapWidth, int mapHeight,
                               int selectedSquadId) {
        ConquestFrontSnapshot snapshot = commander != null
                && commander.detail() instanceof ConquestFrontSnapshot conquest
                ? conquest : null;
        if (snapshot == null || mapWidth <= 0 || mapHeight <= 0) {
            clear(overlay);
            return;
        }

        List<CellHighlight> tracks = new ArrayList<>();
        List<CellHighlight> fronts = new ArrayList<>();
        List<CellHighlight> actions = new ArrayList<>();
        List<CellHighlight> selected = new ArrayList<>();
        List<CellHighlight> selectedAction = new ArrayList<>();
        boolean vertical = snapshot.axis() == TraversalAxis.SOUTH_TO_NORTH;
        int forwardExtent = vertical ? mapHeight : mapWidth;

        for (ConquestFrontSnapshot.TrackState track : snapshot.tracks()) {
            Color trackColor = trackColor(snapshot.perspective(), track.index());
            tracks.add(trackRect(track, vertical, mapWidth, mapHeight, trackColor));
            addProgress(fronts, track, vertical, forwardExtent,
                    track.friendlyBodyProgress(), FRIENDLY_BODY);
            addProgress(fronts, track, vertical, forwardExtent,
                    track.friendlyLeadProgress(), FRIENDLY_LEAD);
            addProgress(fronts, track, vertical, forwardExtent,
                    track.knownHostileFrontProgress(), KNOWN_HOSTILE_FRONT);
        }

        for (ConquestFrontSnapshot.SquadDirective directive : snapshot.directives()) {
            if (directive.markerCellX() >= 0 && directive.markerCellY() >= 0) {
                actions.add(new CellHighlight(directive.markerCellX(),
                        directive.markerCellY(), ACTION_TARGET));
            }
            if (directive.squadId() == selectedSquadId) {
                ConquestFrontSnapshot.TrackState track = snapshot.track(
                        directive.effectiveTrack());
                if (track != null) {
                    selected.add(trackRect(track, vertical, mapWidth, mapHeight,
                            SELECTED_TRACK));
                }
                if (directive.markerCellX() >= 0
                        && directive.markerCellY() >= 0) {
                    selectedAction.add(new CellHighlight(
                            directive.markerCellX(), directive.markerCellY(),
                            SELECTED_ACTION));
                }
            }
        }

        overlay.put(HighlightOverlay.SRC_CONQUEST_TRACKS, tracks);
        overlay.put(HighlightOverlay.SRC_CONQUEST_FRONTS, fronts);
        overlay.put(HighlightOverlay.SRC_CONQUEST_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_CONQUEST_SELECTED_TRACK, selected);
        overlay.put(HighlightOverlay.SRC_CONQUEST_SELECTED_ACTION,
                selectedAction);
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_CONQUEST_TRACKS);
        overlay.clear(HighlightOverlay.SRC_CONQUEST_FRONTS);
        overlay.clear(HighlightOverlay.SRC_CONQUEST_ACTIONS);
        overlay.clear(HighlightOverlay.SRC_CONQUEST_SELECTED_TRACK);
        overlay.clear(HighlightOverlay.SRC_CONQUEST_SELECTED_ACTION);
    }

    private static CellHighlight trackRect(ConquestFrontSnapshot.TrackState track,
                                           boolean vertical,
                                           int mapWidth, int mapHeight,
                                           Color color) {
        int lateralSize = Math.max(1,
                track.lateralEnd() - track.lateralStart() + 1);
        return vertical
                ? new CellHighlight(track.lateralStart(), 0,
                        lateralSize, mapHeight, color)
                : new CellHighlight(0, track.lateralStart(),
                        mapWidth, lateralSize, color);
    }

    private static void addProgress(List<CellHighlight> out,
                                    ConquestFrontSnapshot.TrackState track,
                                    boolean vertical, int forwardExtent,
                                    float progress, Color color) {
        if (progress < 0f || forwardExtent <= 0) return;
        int forward = Math.round(Math.max(0f, Math.min(1f, progress))
                * (forwardExtent - 1));
        int lateralSize = Math.max(1,
                track.lateralEnd() - track.lateralStart() + 1);
        out.add(vertical
                ? new CellHighlight(track.lateralStart(), forward,
                        lateralSize, 1, color)
                : new CellHighlight(forward, track.lateralStart(),
                        1, lateralSize, color));
    }

    private static Color trackColor(Faction perspective, int track) {
        boolean alternate = (track & 1) != 0;
        if (perspective == Faction.DEFENDER) {
            return alternate ? DEFENDER_TRACK_B : DEFENDER_TRACK_A;
        }
        return alternate ? MARINE_TRACK_B : MARINE_TRACK_A;
    }
}
