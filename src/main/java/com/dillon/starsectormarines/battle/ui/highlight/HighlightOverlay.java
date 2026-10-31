package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.squad.FrontageLine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared cell-highlight state for overlays on the battle map. Multiple "sources"
 * write into their own slot keyed by string id and are rendered together:
 * accepted defensive areas and explicit developer overlays. Unit selection is
 * rendered separately as circles beneath characters. Sources don't stomp each other —
 * clearing one source leaves the others intact.
 *
 * <p>Pure state holder: the GL was hoisted out to {@code HighlightRenderer}
 * ({@code ops.battleview}), which drains {@link #sourceLists()} into
 * {@code SOLID_RECT} + {@code LINE} commands. Keeping the draw out of here avoids
 * an {@code ops.battleview} package cycle and leaves this in the UI tier where
 * the publishers live.
 *
 * <p>Iteration order is insertion order ({@link LinkedHashMap}) — sources
 * pushed later paint on top. Callers that want explicit layering should push
 * in the order they want drawn back-to-front (selected unit > captain > action
 * cells is a sensible default).
 *
 * <p>Lifecycle: panels rebuild their source's list every frame in update()
 * and consume in render() — same per-frame snapshot pattern the rest of the
 * HUD uses. {@link #clear(String)} drops a single source; {@link #clearAll()}
 * resets everything (e.g. on battle teardown).
 */
public final class HighlightOverlay {

    /** Source ids the in-tree panels publish under. New consumers should add their own constants here. */
    public static final String SRC_ACTION_CELLS    = "action-cells";
    /** The staged-build ghosts under the yard power's, the ship order's and direct control's placement cursors. */
    public static final String SRC_BUILD_GHOST_POWER = "build-ghost-power";
    public static final String SRC_BUILD_GHOST_SHIP_ORDER = "build-ghost-ship-order";
    public static final String SRC_BUILD_GHOST_DIRECT = "build-ghost-direct";
    public static final String SRC_SELECTED_SQUAD  = "selected-squad";
    public static final String SRC_SELECTED_UNIT   = "selected-unit";
    public static final String SRC_MECH_MOVE_DESTINATION =
            "mech-move-destination";
    public static final String SRC_SQUAD_MOVE_DESTINATION =
            "squad-move-destination";
    public static final String SRC_VEHICLE_MOVE_DESTINATION =
            "vehicle-move-destination";
    /** Where an order was refused, or given up on — a different thing from where one is going. */
    public static final String SRC_VEHICLE_MOVE_REFUSED =
            "vehicle-move-refused";
    /** The selected squads' GARRISON watch: the ring round the anchor and the place being manned. */
    public static final String SRC_GARRISON_WATCH = "garrison-watch";
    /** Prepared ground and the selection's claimed cells; see {@code FightingPositionHighlightPublisher}. */
    public static final String SRC_FIGHTING_POSITIONS = "fighting-positions";
    /** The line a right drag in progress would give the selected squads. */
    public static final String SRC_SQUAD_LINE_PREVIEW = "squad-line-preview";
    /** The destination and heading a right drag in progress would give a selected vehicle or mech. */
    public static final String SRC_FACING_PREVIEW = "facing-preview";
    /** The heading the selected vehicle's accepted order will finish on. */
    public static final String SRC_VEHICLE_MOVE_FACING = "vehicle-move-facing";
    public static final String SRC_FIGHTING_POSITION_CLAIMS = "fighting-position-claims";
    public static final String SRC_CAPTAIN         = "captain";
    public static final String SRC_BELIEVED_CONTACTS = "believed-contacts";
    public static final String SRC_HEARD_NOISE      = "heard-noise";
    public static final String SRC_CONTACT_DOCTRINE = "contact-doctrine";
    public static final String SRC_MARINE_FRIENDLY_INFLUENCE = "marine-friendly-influence";
    public static final String SRC_MARINE_HOSTILE_INFLUENCE = "marine-hostile-influence";
    public static final String SRC_DEFENDER_FRIENDLY_INFLUENCE = "defender-friendly-influence";
    public static final String SRC_DEFENDER_HOSTILE_INFLUENCE = "defender-hostile-influence";
    public static final String SRC_CONQUEST_TRACKS = "conquest-tracks";
    public static final String SRC_CONQUEST_FRONTS = "conquest-fronts";
    public static final String SRC_CONQUEST_ACTIONS = "conquest-actions";
    public static final String SRC_CONQUEST_SELECTED_TRACK = "conquest-selected-track";
    public static final String SRC_CONQUEST_SELECTED_ACTION = "conquest-selected-action";
    public static final String SRC_ASSAULT_SECTORS = "assault-sectors";
    public static final String SRC_ASSAULT_ACTIONS = "assault-actions";
    public static final String SRC_ASSAULT_STRONGPOINTS = "assault-strongpoints";
    public static final String SRC_ASSAULT_SELECTED_SECTOR = "assault-selected-sector";
    public static final String SRC_ASSAULT_SELECTED_ACTION = "assault-selected-action";
    public static final String SRC_SABOTAGE_SITES = "sabotage-sites";
    public static final String SRC_SABOTAGE_SELECTED_SITE = "sabotage-selected-site";
    public static final String SRC_RAID_TARGET = "raid-target";
    public static final String SRC_RAID_EGRESS = "raid-egress";
    public static final String SRC_RAID_ACTIONS = "raid-actions";
    public static final String SRC_RAID_SELECTED_ACTION = "raid-selected-action";
    public static final String SRC_EXTRACTION_SOURCE = "extraction-source";
    public static final String SRC_EXTRACTION_PAYLOAD = "extraction-payload";
    public static final String SRC_EXTRACTION_GUIDE = "extraction-guide";
    public static final String SRC_EXTRACTION_EGRESS = "extraction-egress";
    public static final String SRC_EXTRACTION_ACTIONS = "extraction-actions";
    public static final String SRC_EXTRACTION_SELECTED_ACTION =
            "extraction-selected-action";
    public static final String SRC_RESCUE_SWARM_APPROACHES =
            "rescue-swarm-approaches";

    /** Suggested palette so unrelated sources don't visually collide. */
    public static final Color COLOR_ACTION_CELLS   = new Color(0x40, 0xE0, 0xFF, 0xFF);  // cyan
    public static final Color COLOR_SELECTED_UNIT  = new Color(0x80, 0xFF, 0x80, 0xFF);  // green
    public static final Color COLOR_SELECTED_PRIMARY = new Color(0xFF, 0xD4, 0x64, 0xFF); // gold
    public static final Color COLOR_MECH_MOVE_DESTINATION =
            new Color(0x50, 0xE8, 0xFF, 0xFF); // command cyan
    public static final Color COLOR_SQUAD_MOVE_DESTINATION =
            COLOR_MECH_MOVE_DESTINATION;
    public static final Color COLOR_VEHICLE_MOVE_DESTINATION =
            COLOR_MECH_MOVE_DESTINATION;
    /** Refusal reads as a warning rather than a command cue: it is the order not happening. */
    public static final Color COLOR_VEHICLE_MOVE_REFUSED =
            new Color(0xFF, 0x60, 0x50, 0xFF); // refusal red
    /** A watch is a standing permission, not an order, so its ring is fainter than an order's. */
    public static final Color COLOR_GARRISON_WATCH = new Color(0xFF, 0xD0, 0x40, 0x70);
    /** The place the stance is manning, drawn as a small bright pip in the watch's colour. */
    public static final Color COLOR_GARRISON_PLACE = new Color(0xFF, 0xD0, 0x40, 0xE0);
    /** Faint on purpose: a wash over every cell of every trench must not shout. */
    public static final Color COLOR_FIGHTING_POSITION = new Color(0x58, 0xE0, 0x78, 0x58);
    public static final Color COLOR_FIGHTING_POSITION_CLAIMED = new Color(0x50, 0xE8, 0xFF, 0xC0);
    public static final Color COLOR_CAPTAIN        = new Color(0xFF, 0xD0, 0x40, 0xFF);  // gold
    public static final Color COLOR_BELIEVED_CONTACT = new Color(0xFF, 0x50, 0xA0, 0xFF); // magenta
    public static final Color COLOR_AUDIO_CONTACT  = new Color(0xFF, 0xA0, 0x38, 0xFF); // amber
    public static final Color COLOR_HEARD_NOISE    = new Color(0xFF, 0xE0, 0x40, 0xFF); // yellow
    public static final Color COLOR_DOCTRINE_ADVANCE = new Color(0x40, 0xE0, 0xA0, 0xA0); // green-cyan
    public static final Color COLOR_DOCTRINE_HOLD = new Color(0xFF, 0xD0, 0x40, 0xA0); // gold
    public static final Color COLOR_DOCTRINE_DISENGAGE = new Color(0xFF, 0x60, 0x60, 0xA0); // red

    private final Map<String, List<CellHighlight>> sources = new LinkedHashMap<>();
    private final Map<String, List<LineMark>> lineSources = new LinkedHashMap<>();
    private final Map<String, List<FacingMark>> facingSources = new LinkedHashMap<>();
    private final Map<String, BuildPreview> buildPreviews = new LinkedHashMap<>();

    /**
     * A drawn frontage, drawn as a stroke with its front rather than as cells, and, while the drag
     * that draws it is still under way, the ring each marine will stand in.
     */
    public record LineMark(FrontageLine line, List<PlaceMark> places) {
        public LineMark {
            places = places == null ? List.of() : List.copyOf(places);
        }
    }

    /** Where one marine will stand ({@code x}, {@code y} a cell), and whether it is a position rather than open ground. */
    public record PlaceMark(long member, int x, int y, boolean position) { }

    /**
     * A destination with the heading to finish on: ({@code x}, {@code y}) in continuous cells,
     * {@code facingDegrees} in the sprite convention (0 = +Y, positive counterclockwise), drawn as
     * an arrow {@code lengthCells} long.
     */
    public record FacingMark(float x, float y, float facingDegrees, float lengthCells) { }

    /** Replaces the facing marks under {@code sourceId}; empty or null drops them. */
    public void putFacings(String sourceId, List<FacingMark> marks) {
        if (marks == null || marks.isEmpty()) {
            facingSources.remove(sourceId);
            return;
        }
        facingSources.put(sourceId, List.copyOf(marks));
    }

    /** Every facing mark, in insertion order. */
    public List<FacingMark> facingMarks() {
        List<FacingMark> marks = new ArrayList<>();
        for (List<FacingMark> list : facingSources.values()) marks.addAll(list);
        return marks;
    }

    /** Replaces the line marks under {@code sourceId}; empty or null drops them. */
    public void putLines(String sourceId, List<LineMark> marks) {
        if (marks == null || marks.isEmpty()) {
            lineSources.remove(sourceId);
            return;
        }
        lineSources.put(sourceId, List.copyOf(marks));
    }

    /** Every line mark, in insertion order. */
    public List<LineMark> lineMarks() {
        List<LineMark> marks = new ArrayList<>();
        for (List<LineMark> list : lineSources.values()) marks.addAll(list);
        return marks;
    }

    /**
     * Replaces the highlight list under {@code sourceId}. Pass an empty list
     * (or call {@link #clear(String)}) to drop the source's marks.
     */
    public void put(String sourceId, List<CellHighlight> cells) {
        if (cells == null || cells.isEmpty()) {
            sources.remove(sourceId);
            return;
        }
        sources.put(sourceId, new ArrayList<>(cells));
    }

    public void clear(String sourceId) {
        sources.remove(sourceId);
        lineSources.remove(sourceId);
        facingSources.remove(sourceId);
    }

    public void clearAll() {
        sources.clear();
        lineSources.clear();
        facingSources.clear();
        buildPreviews.clear();
    }

    /**
     * Sets the build ghost {@code sourceId} shows, or takes it away when {@code preview} is null. A placement cursor
     * sets its own every frame it renders; the HUD {@linkplain #clearBuildPreviews clears} them all before it
     * renders, so a cursor that stops rendering takes its ghost away.
     */
    public void setBuildPreview(String sourceId, BuildPreview preview) {
        if (preview == null) buildPreviews.remove(sourceId);
        else buildPreviews.put(sourceId, preview);
    }

    /** Takes every build ghost away. */
    public void clearBuildPreviews() {
        buildPreviews.clear();
    }

    /** Every build ghost a source is showing, for the doodad pass. */
    public Collection<BuildPreview> buildPreviews() {
        return Collections.unmodifiableCollection(buildPreviews.values());
    }

    public boolean hasSource(String sourceId) {
        return sources.containsKey(sourceId);
    }

    /** Immutable view of one source, primarily for pure publisher verification. */
    public List<CellHighlight> source(String sourceId) {
        List<CellHighlight> cells = sources.get(sourceId);
        return cells != null ? List.copyOf(cells) : List.of();
    }

    /** True iff at least one source has highlights to draw. Renderer can skip the GL state setup when false. */
    public boolean isEmpty() {
        for (List<CellHighlight> v : sources.values()) {
            if (!v.isEmpty()) return false;
        }
        return true;
    }

    /**
     * The per-source highlight lists in insertion (= paint) order — the read
     * side {@code HighlightRenderer} drains into draw commands. Live view over
     * the backing map; the renderer only iterates it within a frame.
     */
    public Collection<List<CellHighlight>> sourceLists() {
        return sources.values();
    }
}
