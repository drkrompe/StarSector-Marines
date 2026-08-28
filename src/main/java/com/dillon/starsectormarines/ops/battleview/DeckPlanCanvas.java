package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.HullSilhouette;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.Map;
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

    /** Deck margin as a fraction of the surface, so a plan never touches its frame. */
    private static final float INSET = 0.04f;

    /**
     * The rooms worth telling apart at a glance.
     *
     * <p>Not every purpose: a plan tinted twenty ways is a colour puzzle. These
     * are the places a company is chosen for, and everything else reads as
     * ship — which is honest, because to a marine most of a vessel is.
     */
    private static final Map<RoomPurpose, Color> NOTABLE = Map.of(
            RoomPurpose.BARRACKS, new Color(0x3F, 0x7F, 0xC4),
            RoomPurpose.CREW_QUARTERS, new Color(0x2F, 0x5A, 0x8C),
            RoomPurpose.VEHICLE_BAY, new Color(0xD8, 0x8B, 0x2F),
            RoomPurpose.ARMORY, new Color(0xC4, 0x4B, 0x4B),
            RoomPurpose.FIRING_RANGE, new Color(0x9C, 0x4D, 0x2F),
            RoomPurpose.HANGAR, new Color(0xD8, 0x5C, 0x9A),
            RoomPurpose.MESS_HALL, new Color(0xC9, 0xA2, 0x5E),
            RoomPurpose.STOCKROOM, new Color(0x9A, 0x7A, 0x45),
            RoomPurpose.PATIENT_WARD, new Color(0xE0, 0xE4, 0xEA),
            RoomPurpose.CONTROL_ROOM, new Color(0x8D, 0x6F, 0xC9));

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

        float inset = Math.min(width, height) * INSET;
        float usableWidth = width - inset * 2f;
        float usableHeight = height - inset * 2f;
        // One scale for both axes: a deck stretched to fill its frame would show
        // every hull as the same shape, which is the one thing the plan is for.
        float cell = Math.min(usableWidth / frames, usableHeight / depth);
        float left = (width - cell * frames) * 0.5f;
        float top = (height - cell * depth) * 0.5f;

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
