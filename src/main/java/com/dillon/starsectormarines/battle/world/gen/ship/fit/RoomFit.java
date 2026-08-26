package com.dillon.starsectormarines.battle.world.gen.ship.fit;

/**
 * How well a compartment is fitted out — the axis the upgrade chain moves along.
 *
 * <p><b>A refit changes what the floor holds, not how much floor there is.</b>
 * The same berth compartment sleeps eight under single racks with room to walk
 * around them, and sixteen under triple racks with working room only. Nothing
 * about the ship changed; the fitting did. That is what makes an upgrade
 * something the player recognises in a room they already know, rather than a
 * number going up somewhere or a new compartment appearing out of nowhere.
 *
 * <p>It also gives the fill somewhere honest to put a bad layout. A hull taken
 * as a prize, or a company too poor to have refitted anything, should read as
 * badly used space — wide gaps, gear stacked where it landed, capacity wasted —
 * and that has to be a level the fill can produce deliberately rather than a
 * defect it falls into.
 *
 * <p>Capacity is the fixture count at the fitted level. There is no second
 * number recording it.
 */
public enum RoomFit {

    /**
     * Never properly fitted, or fitted for something else. Generous spacing that
     * buys nothing, and the compartment holds well under what it could.
     */
    MAKESHIFT(2, 4, 1),
    /** A working arrangement. What a hull comes with. */
    STANDARD(1, 3, 2),
    /**
     * Fitted by someone who needed the room. Tight pitch and aisles cut to the
     * width people actually pass in.
     */
    OPTIMISED(0, 2, 3);

    private final int gap;
    private final int aisleWidth;
    private final int ranks;

    RoomFit(int gap, int aisleWidth, int ranks) {
        this.gap = gap;
        this.aisleWidth = aisleWidth;
        this.ranks = ranks;
    }

    /**
     * How many rows of fixtures may be ranked inboard from each bulkhead before
     * the rest of the compartment is left as working floor. This is where most
     * of a refit's capacity comes from, and the reason a poorly fitted room
     * looks half empty rather than merely loose.
     */
    public int ranks() {
        return ranks;
    }

    /** Cells left between one fixture group and the next. Zero is shoulder to shoulder. */
    public int gap() {
        return gap;
    }

    /**
     * Cells of clear floor kept between fixture blocks. An optimised fit buys
     * most of its capacity here, which is also what makes it read as tight
     * rather than merely fuller.
     */
    public int aisleWidth() {
        return aisleWidth;
    }
}
