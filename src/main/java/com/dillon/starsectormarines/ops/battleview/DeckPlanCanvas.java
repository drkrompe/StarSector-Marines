package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.HullSilhouette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A deck seen from above, as a plan rather than as a place.
 *
 * <p>What a ship <em>is</em>, in one picture. A list of facilities says a hull
 * has eighteen berthings and a bay; the plan says they are spread down a long
 * thin spine with the bay amidships, and that is the difference between reading
 * a specification and recognising a ship.
 *
 * <p><b>The passages are drawn, not left as background.</b> Circulation is
 * about a fifth of a deck's walkable area on every hull measured, and a plan
 * showing only compartments leaves the rooms apparently floating in structure —
 * which reads as a tighter pack than the ship really has, and hides the spine
 * that makes the arrangement mean anything.
 *
 * <p>Schematic on purpose. Nothing here loads a tile sheet or runs a
 * simulation: a candidate the player is only considering should not cost what
 * the ship they live on costs, and the shape, the arrangement and the
 * circulation are the whole of what this view has to carry.
 */
public final class DeckPlanCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color HULL = new Color(0x16, 0x1E, 0x2A);
    private static final Color PASSAGE = new Color(0x33, 0x40, 0x52);
    private static final Color BULKHEAD = new Color(0x2B, 0x37, 0x48);
    private static final Color UNREMARKABLE = new Color(0x54, 0x5E, 0x6E);
    private static final Color NOTHING = new Color(0x3A, 0x44, 0x52);
    private static final Color CAPTION = new Color(0x9E, 0xAF, 0xC2);
    private static final Color HIGHLIGHT = new Color(0xFF, 0xD4, 0x64);
    private static final Color READOUT_FILL = new Color(0x0B, 0x14, 0x1E, 0xEE);

    /** Deck margin as a fraction of the surface, so a plan never touches its frame. */
    private static final float INSET = 0.04f;

    /** How one kind of room is drawn and what it is called under the plan. */
    private record Keyed(RoomPurpose purpose, Color color, String label) { }

    /**
     * The rooms worth telling apart at a glance, in reading order.
     *
     * <p>Not every purpose: a plan tinted twenty ways is a colour puzzle. These
     * are the places a company is chosen for, and everything else reads as
     * ship — which is honest, because to a marine most of a vessel is.
     *
     * <p>Colour and name live together because they are one fact. A legend
     * whose swatches were defined apart from the fill they explain is a legend
     * that goes quietly wrong.
     */
    private static final List<Keyed> KEY = List.of(
            new Keyed(RoomPurpose.BARRACKS, new Color(0x3F, 0x7F, 0xC4), "Berthing"),
            new Keyed(RoomPurpose.CREW_QUARTERS, new Color(0x2F, 0x5A, 0x8C), "Crew"),
            new Keyed(RoomPurpose.VEHICLE_BAY, new Color(0xD8, 0x8B, 0x2F), "Mech bay"),
            new Keyed(RoomPurpose.HANGAR, new Color(0xD8, 0x5C, 0x9A), "Boat bay"),
            new Keyed(RoomPurpose.ARMORY, new Color(0xC4, 0x4B, 0x4B), "Armory"),
            new Keyed(RoomPurpose.FIRING_RANGE, new Color(0x9C, 0x4D, 0x2F), "Range"),
            new Keyed(RoomPurpose.MESS_HALL, new Color(0xC9, 0xA2, 0x5E), "Mess"),
            new Keyed(RoomPurpose.PATIENT_WARD, new Color(0xE0, 0xE4, 0xEA), "Sick bay"),
            new Keyed(RoomPurpose.STOCKROOM, new Color(0x9A, 0x7A, 0x45), "Hold"),
            new Keyed(RoomPurpose.BRIDGE, new Color(0x8D, 0x6F, 0xC9), "Bridge"));

    private static final Map<RoomPurpose, Color> NOTABLE = notable();

    private static Map<RoomPurpose, Color> notable() {
        Map<RoomPurpose, Color> colors = new EnumMap<>(RoomPurpose.class);
        for (Keyed keyed : KEY) colors.put(keyed.purpose(), keyed.color());
        return colors;
    }

    /** What the legend calls everything the plan does not tint. */
    private static final String OTHER = "Other spaces";
    private static final String PASSAGE_LABEL = "Passage";

    /** The body face; the legend is a caption rather than a heading. */
    private static final BitmapFont FONT = Fonts.INSIGNIA_15_AA;

    /** Swatch edge, the gap beside it, and the gap between entries. */
    private static final float SWATCH = 11f;
    private static final float SWATCH_GAP = 6f;
    private static final float ENTRY_GAP = 18f;
    private static final float READOUT_PAD = 7f;

    /** Where the deck sits on the surface, shared by drawing and hit-testing. */
    private record Frame(float left, float top, float cell, int frames, int depth) {
        int cellX(float canvasX) {
            return (int) Math.floor((canvasX - left) / cell);
        }

        int cellY(float canvasY) {
            return (int) Math.floor((canvasY - top) / cell);
        }

        boolean holds(int x, int y) {
            return x >= 0 && y >= 0 && x < frames && y < depth;
        }
    }

    private final Supplier<CompanyDeck> ship;
    /** Corridor runs in cell coordinates, cached per deck: {x, y, length}. */
    private List<int[]> passages = List.of();
    private MapResult tracedFrom;
    private float pointerX = Float.NaN;
    private float pointerY = Float.NaN;

    /**
     * @param ship the deck to draw, or a supplier returning null for a hull with
     *     no interior — an empty frame is the honest picture of a ship there is
     *     nothing aboard
     */
    public DeckPlanCanvas(Supplier<CompanyDeck> ship) {
        if (ship == null) throw new IllegalArgumentException("a ship is required");
        this.ship = ship;
    }

    /**
     * Point at the deck, in canvas coordinates, or {@code NaN} for nothing.
     *
     * <p>A legend can only say what a colour means in general. Twenty pale-blue
     * boxes are twenty berthings, and the question a player actually has is
     * about the one under the cursor.
     */
    public void pointAt(float canvasX, float canvasY) {
        pointerX = canvasX;
        pointerY = canvasY;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        context.fillRect(0f, 0f, width, height, BACKGROUND);

        CompanyDeck aboard = ship.get();
        DeckGraph rooms = aboard == null || !aboard.ship().habitable() ? null : aboard.rooms();
        if (rooms == null || rooms.compartments().isEmpty()) {
            context.strokeRect(1f, 1f, width - 2f, height - 2f, NOTHING, 1f);
            return;
        }

        MapResult map = aboard.map();
        List<Keyed> present = presentIn(rooms);
        boolean anyOther = anyUntinted(rooms);
        float legendHeight = FONT.getLineHeight() + SWATCH_GAP * 2f;
        Frame frame = frameFor(map, rooms, width, height, legendHeight);
        if (frame == null) return;

        drawHull(context, aboard.ship().outline(), frame);
        drawPassages(context, map, frame);
        DeckGraph.Compartment hovered = compartmentUnderPointer(rooms, frame);
        for (DeckGraph.Compartment room : rooms.compartments()) {
            drawRoom(context, frame, room, room == hovered);
        }

        drawEnds(context, frame);
        // Along the surface, not the deck. A beamy hull is centred in a narrow
        // column, and hanging the key off its left edge would cut the key short
        // on exactly the ships that have the most in them.
        float margin = Math.min(width, height) * INSET;
        drawLegend(context, present, anyOther, margin,
                height - legendHeight + SWATCH_GAP, width - margin * 2f);
        drawReadout(context, map, frame, hovered, width, height - legendHeight);
    }

    private static Frame frameFor(MapResult map, DeckGraph rooms,
                                  float width, float height, float legendHeight) {
        int frames = map.topology.getWidth();
        int depth = map.topology.getHeight();
        for (DeckGraph.Compartment room : rooms.compartments()) {
            frames = Math.max(frames, room.right() + 1);
            depth = Math.max(depth, room.bottom() + 1);
        }
        if (frames <= 0 || depth <= 0) return null;

        float inset = Math.min(width, height) * INSET;
        float usableWidth = width - inset * 2f;
        float usableHeight = height - inset * 2f - legendHeight;
        // One scale for both axes: a deck stretched to fill its frame would show
        // every hull as the same shape, which is the one thing the plan is for.
        float cell = Math.min(usableWidth / frames, usableHeight / depth);
        return new Frame((width - cell * frames) * 0.5f,
                (height - legendHeight - cell * depth) * 0.5f, cell, frames, depth);
    }

    private static void drawRoom(CanvasContext context, Frame frame,
                                 DeckGraph.Compartment room, boolean hovered) {
        float x = frame.left() + room.left() * frame.cell();
        float y = frame.top() + room.top() * frame.cell();
        float roomWidth = room.width() * frame.cell();
        float roomDepth = room.depth() * frame.cell();
        context.fillRect(x, y, roomWidth, roomDepth,
                NOTABLE.getOrDefault(room.purpose(), UNREMARKABLE));
        if (hovered) {
            context.strokeRect(x - 1f, y - 1f, roomWidth + 2f, roomDepth + 2f, HIGHLIGHT, 2f);
            return;
        }
        // Bulkheads only where a room is large enough for the stroke to read as
        // a wall rather than swallow the room it encloses.
        if (roomWidth >= 4f && roomDepth >= 4f) {
            context.strokeRect(x, y, roomWidth, roomDepth, BULKHEAD, 1f);
        }
    }

    /**
     * The hull the rooms sit inside, frame by frame.
     *
     * <p>Drawn rather than implied. Compartments alone read as a scatter of
     * boxes, and it is the shape around them that turns the plan into a
     * particular ship — a fine bow, a full waist, the taper aft. Without an
     * outline the whole deck is filled instead, which says honestly that the
     * shape is not known rather than inventing one.
     */
    private static void drawHull(CanvasContext context, HullSilhouette hull, Frame frame) {
        float deckWidth = frame.cell() * frame.frames();
        float deckDepth = frame.cell() * frame.depth();
        if (hull == null) {
            context.fillRect(frame.left(), frame.top(), deckWidth, deckDepth, HULL);
            return;
        }
        float centre = frame.top() + deckDepth * 0.5f;
        float halfDepth = deckDepth * 0.5f;
        for (int station = 0; station < frame.frames(); station++) {
            float along = frame.frames() <= 1 ? 0f : (float) station / (frame.frames() - 1);
            float toPort = hull.portAt(along) * halfDepth;
            float toStarboard = hull.starboardAt(along) * halfDepth;
            if (toPort + toStarboard <= 0f) continue;
            context.fillRect(frame.left() + station * frame.cell(), centre - toPort,
                    frame.cell(), toPort + toStarboard, HULL);
        }
    }

    /**
     * The spine and the passages branching off it.
     *
     * <p>Coalesced into runs along each frame and cached per deck. A capital's
     * circulation is six thousand cells, and issuing one rectangle apiece every
     * time the plan is drawn would be a schematic that costs more than the room
     * it illustrates.
     */
    private void drawPassages(CanvasContext context, MapResult map, Frame frame) {
        if (map != tracedFrom) {
            passages = trace(map.topology);
            tracedFrom = map;
        }
        for (int[] run : passages) {
            context.fillRect(frame.left() + run[0] * frame.cell(),
                    frame.top() + run[1] * frame.cell(),
                    run[2] * frame.cell(), frame.cell(), PASSAGE);
        }
    }

    private static List<int[]> trace(CellTopology topology) {
        List<int[]> runs = new ArrayList<>();
        for (int y = 0; y < topology.getHeight(); y++) {
            int start = -1;
            for (int x = 0; x <= topology.getWidth(); x++) {
                boolean passage = x < topology.getWidth()
                        && topology.getRoomPurpose(x, y) == RoomPurpose.CORRIDOR;
                if (passage && start < 0) start = x;
                if (!passage && start >= 0) {
                    runs.add(new int[] { start, y, x - start });
                    start = -1;
                }
            }
        }
        return List.copyOf(runs);
    }

    /**
     * Which way the ship is pointing.
     *
     * <p>A plan is close to unreadable without it. Fore and aft is most of what
     * a deck layout means — berthings forward, the bay amidships, engineering
     * aft — and a viewer who does not know which end is which is looking at a
     * pattern rather than a ship.
     *
     * <p>Set above the ends rather than beside them: a deck fills the width it
     * is given, so there is no margin outside the plan to write in, but a hull
     * narrows at both ends and the space over the fine end is empty.
     */
    private static void drawEnds(CanvasContext context, Frame frame) {
        context.text(FONT, "BOW", frame.left() + frame.cell() * 0.5f,
                frame.top() + 2f, CAPTION);
        String stern = "STERN";
        context.text(FONT, stern,
                frame.left() + frame.cell() * frame.frames()
                        - FONT.measureWidth(stern) - frame.cell() * 0.5f,
                frame.top() + 2f, CAPTION);
    }

    private DeckGraph.Compartment compartmentUnderPointer(DeckGraph rooms, Frame frame) {
        if (Float.isNaN(pointerX) || Float.isNaN(pointerY)) return null;
        int x = frame.cellX(pointerX);
        int y = frame.cellY(pointerY);
        if (!frame.holds(x, y)) return null;
        for (DeckGraph.Compartment room : rooms.compartments()) {
            if (room.contains(x, y)) return room;
        }
        return null;
    }

    /** What the cursor is over, said in words beside it. @see #pointAt */
    private void drawReadout(CanvasContext context, MapResult map, Frame frame,
                             DeckGraph.Compartment hovered, float width, float ceiling) {
        if (Float.isNaN(pointerX) || Float.isNaN(pointerY)) return;
        int x = frame.cellX(pointerX);
        int y = frame.cellY(pointerY);
        if (!frame.holds(x, y)) return;

        String text;
        if (hovered != null) {
            // Where in the ship, not just what: two squads berthed port and
            // starboard of the same spine live in different parts of a vessel.
            text = label(hovered.purpose()) + "  ·  "
                    + words(hovered.zone().name()) + " " + words(hovered.side().name());
        } else if (map.topology.getRoomPurpose(x, y) == RoomPurpose.CORRIDOR) {
            text = PASSAGE_LABEL;
        } else {
            return;
        }

        float boxWidth = FONT.measureWidth(text) + READOUT_PAD * 2f;
        float boxHeight = FONT.getLineHeight() + READOUT_PAD * 2f;
        // Kept inside the surface, and above the cursor rather than under it, so
        // the box never covers the room it is naming.
        float boxX = Math.max(0f, Math.min(width - boxWidth, pointerX + 12f));
        float boxY = Math.max(0f, Math.min(ceiling - boxHeight, pointerY - boxHeight - 8f));
        context.fillRect(boxX, boxY, boxWidth, boxHeight, READOUT_FILL);
        context.strokeRect(boxX, boxY, boxWidth, boxHeight, HIGHLIGHT, 1f);
        context.text(FONT, text, boxX + READOUT_PAD, boxY + READOUT_PAD, HIGHLIGHT);
    }

    /**
     * What the colours mean, for this ship only.
     *
     * <p>Listing every kind of room the generator knows would explain the
     * generator. Listing the ones aboard explains the ship, and the key shrinks
     * as the hull gets plainer — a freighter's runs to three entries, which is
     * itself the point being made.
     */
    private static void drawLegend(CanvasContext context, List<Keyed> present,
                                   boolean anyOther, float left, float top,
                                   float available) {
        float x = left;
        for (Keyed keyed : present) {
            if (!fits(x, left, available, keyed.label())) return;
            x += drawEntry(context, keyed.color(), keyed.label(), x, top);
        }
        if (fits(x, left, available, PASSAGE_LABEL)) {
            x += drawEntry(context, PASSAGE, PASSAGE_LABEL, x, top);
        }
        if (anyOther && fits(x, left, available, OTHER)) {
            drawEntry(context, UNREMARKABLE, OTHER, x, top);
        }
    }

    /** Whether one more entry still lands inside the strip; the first always does. */
    private static boolean fits(float x, float left, float available, String label) {
        return x <= left
                || x + SWATCH + SWATCH_GAP + FONT.measureWidth(label) <= left + available;
    }

    private static float drawEntry(CanvasContext context, Color color, String label,
                                   float x, float top) {
        float swatchTop = top + (FONT.getLineHeight() - SWATCH) * 0.5f;
        context.fillRect(x, swatchTop, SWATCH, SWATCH, color);
        context.strokeRect(x, swatchTop, SWATCH, SWATCH, BULKHEAD, 1f);
        context.text(FONT, label, x + SWATCH + SWATCH_GAP, top, CAPTION);
        return SWATCH + SWATCH_GAP + FONT.measureWidth(label) + ENTRY_GAP;
    }

    /** The tinted kinds this deck actually has, in the key's own order. */
    private static List<Keyed> presentIn(DeckGraph rooms) {
        Set<RoomPurpose> aboard = EnumSet.noneOf(RoomPurpose.class);
        for (DeckGraph.Compartment room : rooms.compartments()) aboard.add(room.purpose());
        List<Keyed> present = new ArrayList<>();
        for (Keyed keyed : KEY) {
            if (aboard.contains(keyed.purpose())) present.add(keyed);
        }
        return present;
    }

    private static boolean anyUntinted(DeckGraph rooms) {
        for (DeckGraph.Compartment room : rooms.compartments()) {
            if (!NOTABLE.containsKey(room.purpose())) return true;
        }
        return false;
    }

    private static String label(RoomPurpose purpose) {
        for (Keyed keyed : KEY) {
            if (keyed.purpose() == purpose) return keyed.label();
        }
        return words(purpose.name());
    }

    private static String words(String constant) {
        String spaced = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
