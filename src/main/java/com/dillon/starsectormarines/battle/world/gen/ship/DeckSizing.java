package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * How big a ship's interior is, and how it grows.
 *
 * <p>Crew alone is a poor proxy for size. A vanilla Atlas carries 50–100 crew,
 * the same as a Hammerhead, but it is a vastly larger ship — the difference is
 * 2000 units of hold against 100. So interior area is driven by <b>crew and
 * cargo together</b>: complement wants habitation, command, and engineering
 * space, while hold wants volume. Both are walkable during a boarding action,
 * which is why both count.
 *
 * <p>Growth is not uniform. A deck lengthens faster than it widens, because a
 * short wide deck reads as a station and loses the axis the whole family is
 * built around. Past a playable envelope a deck stops growing altogether and the
 * ship gains <b>more decks</b> instead, which keeps any single battle map
 * legible and respects the standing law that one battle occupies one deck.
 *
 * <p>The constants are calibrated so a Valkyrie — the vanilla personnel
 * transport, 250 maximum complement — comes out as roughly one full deck.
 */
public final class DeckSizing {

    /** Walkable cells a crew berth implies once passages and working space are shared in. */
    private static final float AREA_PER_CREW = 11f;
    /** Walkable cells one unit of hold implies. Cargo is bulk, so it is cheap per unit. */
    private static final float AREA_PER_CARGO = 0.5f;

    /** Length-to-beam ratio a deck aims for. Below about four a deck stops reading as a ship. */
    private static final float LENGTH_TO_BEAM = 4.5f;
    /** Of the bounding rectangle, the share a tapered hull actually encloses. */
    private static final float HULL_FILL = 0.7f;

    private static final int MIN_FRAMES = 44;
    private static final int MAX_FRAMES = 150;
    private static final int MIN_HEIGHT = 16;
    private static final int MAX_HEIGHT = 34;

    private DeckSizing() {}

    /** One ship's interior budget: how many decks, and how big each one is. */
    public record DeckPlan(int deckCount, int frames, int height) {

        /** Bounding-rectangle area of a single deck, in cells. */
        public int deckArea() {
            return frames * height;
        }
    }

    /**
     * Size a ship's decks from its complement and hold.
     *
     * @param maxCrew maximum crew complement; a vanilla hull's {@code max crew}
     * @param cargo hold capacity; a vanilla hull's {@code cargo}
     */
    public static DeckPlan planFor(int maxCrew, int cargo) {
        float wanted = Math.max(1f, maxCrew) * AREA_PER_CREW + Math.max(0, cargo) * AREA_PER_CARGO;

        int frames = clamp(Math.round(lengthFor(wanted)), MIN_FRAMES, MAX_FRAMES);
        int height = clamp(Math.round(frames / LENGTH_TO_BEAM), MIN_HEIGHT, MAX_HEIGHT);

        float perDeck = frames * height * HULL_FILL;
        int decks = Math.max(1, (int) Math.ceil(wanted / perDeck));
        return new DeckPlan(decks, frames, height);
    }

    /**
     * Length that would hold the wanted area at the target ratio, before either
     * dimension is clamped. Clamping is what converts further growth into extra
     * decks rather than an unplayably long map.
     */
    private static float lengthFor(float wantedArea) {
        return (float) Math.sqrt(wantedArea * LENGTH_TO_BEAM / HULL_FILL);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
