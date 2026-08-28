package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.HullSilhouette;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A deck seen from above, as a plan rather than as a place.
 *
 * <p>What a ship <em>is</em>, in one picture. A list of facilities says a hull
 * has eighteen berthings and a bay; the plan says they are spread down a long
 * thin spine with the bay amidships, and that is the difference between reading
 * a specification and recognising a ship. It is the same view the deck evidence
 * is drawn in, which is deliberate — the player and the person tuning the
 * generator should be looking at the same thing.
 *
 * <p>Schematic on purpose. Nothing here loads a tile sheet or runs a
 * simulation: a candidate the player is only considering should not cost what
 * the ship they live on costs, and the shape and the arrangement are the whole
 * of what this view has to carry.
 */
public final class DeckPlanCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color HULL = new Color(0x16, 0x1E, 0x2A);
    private static final Color BULKHEAD = new Color(0x2B, 0x37, 0x48);
    private static final Color UNREMARKABLE = new Color(0x54, 0x5E, 0x6E);
    private static final Color NOTHING = new Color(0x3A, 0x44, 0x52);
    private static final Color CAPTION = new Color(0x9E, 0xAF, 0xC2);

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
            new Keyed(RoomPurpose.CONTROL_ROOM, new Color(0x8D, 0x6F, 0xC9), "Bridge"));

    private static final Map<RoomPurpose, Color> NOTABLE = notable();

    private static Map<RoomPurpose, Color> notable() {
        Map<RoomPurpose, Color> colors = new EnumMap<>(RoomPurpose.class);
        for (Keyed keyed : KEY) colors.put(keyed.purpose(), keyed.color());
        return colors;
    }

    /** What the legend calls everything the plan does not tint. */
    private static final String OTHER = "Other spaces";

    /** The body face; the legend is a caption rather than a heading. */
    private static final BitmapFont FONT = Fonts.INSIGNIA_15_AA;

    /** Swatch edge, the gap beside it, and the gap between entries. */
    private static final float SWATCH = 11f;
    private static final float SWATCH_GAP = 6f;
    private static final float ENTRY_GAP = 18f;

    private final Supplier<DeckGraph> deck;
    private final Supplier<HullSilhouette> outline;

    /**
     * @param deck the plan to draw, or a supplier returning null for a hull with
     *     no interior — an empty frame is the honest picture of a ship there is
     *     nothing aboard
     * @param outline the hull the rooms are laid out inside, or null for one
     *     whose form is unknown; without it the rooms still read, but as an
     *     arrangement rather than as a ship
     */
    public DeckPlanCanvas(Supplier<DeckGraph> deck, Supplier<HullSilhouette> outline) {
        if (deck == null) throw new IllegalArgumentException("a deck is required");
        this.deck = deck;
        this.outline = outline == null ? () -> null : outline;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        context.fillRect(0f, 0f, width, height, BACKGROUND);

        DeckGraph rooms = deck.get();
        if (rooms == null || rooms.compartments().isEmpty()) {
            context.strokeRect(1f, 1f, width - 2f, height - 2f, NOTHING, 1f);
            return;
        }

        int frames = 0;
        int depth = 0;
        for (DeckGraph.Compartment room : rooms.compartments()) {
            frames = Math.max(frames, room.right() + 1);
            depth = Math.max(depth, room.bottom() + 1);
        }
        if (frames <= 0 || depth <= 0) return;

        List<Keyed> present = presentIn(rooms);
        boolean anyOther = anyUntinted(rooms);
        float legendHeight = FONT.getLineHeight() + SWATCH_GAP * 2f;

        float inset = Math.min(width, height) * INSET;
        float usableWidth = width - inset * 2f;
        float usableHeight = height - inset * 2f - legendHeight;
        // One scale for both axes: a deck stretched to fill its frame would show
        // every hull as the same shape, which is the one thing the plan is for.
        float cell = Math.min(usableWidth / frames, usableHeight / depth);
        float left = (width - cell * frames) * 0.5f;
        float top = (height - legendHeight - cell * depth) * 0.5f;

        drawHull(context, outline.get(), left, top, cell, frames, depth);
        for (DeckGraph.Compartment room : rooms.compartments()) {
            float x = left + room.left() * cell;
            float y = top + room.top() * cell;
            float roomWidth = room.width() * cell;
            float roomDepth = room.depth() * cell;
            context.fillRect(x, y, roomWidth, roomDepth,
                    NOTABLE.getOrDefault(room.purpose(), UNREMARKABLE));
            // Bulkheads only where a room is large enough for the stroke to
            // read as a wall rather than swallow the room it encloses.
            if (roomWidth >= 4f && roomDepth >= 4f) {
                context.strokeRect(x, y, roomWidth, roomDepth, BULKHEAD, 1f);
            }
        }

        drawBow(context, left, top, cell, frames, depth);
        drawLegend(context, present, anyOther, inset,
                height - legendHeight + SWATCH_GAP, width - inset * 2f);
    }

    /**
     * Which way the ship is pointing.
     *
     * <p>A plan is close to unreadable without it. Fore and aft is most of what
     * a deck layout means — berthings forward, the bay amidships, engineering
     * aft — and a viewer who does not know which end is which is looking at a
     * pattern rather than a ship.
     *
     * <p>Set above the bow rather than beside it. A deck fills the width it is
     * given, so there is no margin outside the plan to write in, but a hull
     * narrows at both ends and the space over the fine end is empty.
     */
    private static void drawBow(CanvasContext context, float left, float top,
                                float cell, int frames, int depth) {
        context.text(FONT, "BOW", left + cell * 0.5f, top + 2f, CAPTION);
        String stern = "STERN";
        context.text(FONT, stern,
                left + cell * frames - FONT.measureWidth(stern) - cell * 0.5f,
                top + 2f, CAPTION);
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

    /**
     * The hull the rooms sit inside, frame by frame.
     *
     * <p>Drawn rather than implied. Compartments alone read as a scatter of
     * boxes, and it is the shape around them that turns the plan into a
     * particular ship — a fine bow, a full waist, the taper aft. Without an
     * outline the whole deck is filled instead, which says honestly that the
     * shape is not known rather than inventing one.
     */
    private static void drawHull(CanvasContext context, HullSilhouette hull,
                                 float left, float top, float cell,
                                 int frames, int depth) {
        if (hull == null) {
            context.fillRect(left, top, cell * frames, cell * depth, HULL);
            return;
        }
        float centre = top + cell * depth * 0.5f;
        float halfDepth = cell * depth * 0.5f;
        for (int frame = 0; frame < frames; frame++) {
            float along = frames <= 1 ? 0f : (float) frame / (frames - 1);
            float toPort = hull.portAt(along) * halfDepth;
            float toStarboard = hull.starboardAt(along) * halfDepth;
            if (toPort + toStarboard <= 0f) continue;
            context.fillRect(left + frame * cell, centre - toPort,
                    cell, toPort + toStarboard, HULL);
        }
    }
}
