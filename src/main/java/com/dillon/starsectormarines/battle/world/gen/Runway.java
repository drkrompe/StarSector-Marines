package com.dillon.starsectormarines.battle.world.gen;

/**
 * One authored airstrip: the centreline an aircraft rolls along, its two
 * thresholds, and how wide the made surface is.
 *
 * <p>Published by the lot that laid it rather than recovered afterwards by
 * scanning for runway-coloured ground. The lot knows exactly where it put the
 * strip; a scan would have to infer it from paint, and paint is a presentation
 * decision that has already changed once — a re-export of the floor sheet made
 * a marked strip and the apron beside it the same colour without touching a
 * line of generation. Geometry a system depends on cannot live in the art.
 *
 * <p><b>The strip runs across the lot's frontage, not out of it.</b> It is laid
 * along the approach edge because it is the longest thing on the base and needs
 * to be unobstructed end to end, so an aircraft rolls along the lot's long axis
 * and climbs out afterwards. Which threshold it starts from is a decision for
 * whoever is flying, not a property of the strip: both ends are thresholds and
 * neither is privileged.
 *
 * <p>Coordinates are continuous cell space — the same space an
 * {@link com.dillon.starsectormarines.battle.air.AirBody} flies in — so a
 * steering system can consume the centreline without converting. Cell
 * <em>centres</em>, not corners.
 */
public final class Runway {

    /** One threshold, in continuous cell coordinates. */
    public final float startX;
    public final float startY;
    /** The other threshold. Nothing distinguishes it from the first. */
    public final float endX;
    public final float endY;
    /** Made width of the strip, across the roll direction. */
    public final float widthCells;

    public Runway(float startX, float startY, float endX, float endY, float widthCells) {
        if (widthCells <= 0f) {
            throw new IllegalArgumentException("a runway has width: " + widthCells);
        }
        if (startX == endX && startY == endY) {
            throw new IllegalArgumentException("a runway has two distinct thresholds");
        }
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.widthCells = widthCells;
    }

    /** Usable roll distance, threshold to threshold. */
    public float lengthCells() {
        return (float) Math.hypot(endX - startX, endY - startY);
    }

    /** Centre of the strip — where a taxiway is aiming when it joins. */
    public float centreX() { return (startX + endX) * 0.5f; }

    /** Centre of the strip. */
    public float centreY() { return (startY + endY) * 0.5f; }

    /**
     * Compass heading of a roll from {@code start} toward {@code end}, in the
     * same degrees an {@link com.dillon.starsectormarines.battle.air.AirBody}
     * carries as its facing.
     */
    public float headingDegrees() {
        return (float) Math.toDegrees(Math.atan2(endY - startY, endX - startX));
    }

    /**
     * The threshold to start a roll from when the aircraft is going to
     * {@code (towardX, towardY)} — the far one, so the roll runs toward where
     * it is headed and it leaves the strip already pointing the right way.
     *
     * @return {@code {x, y}} of the chosen threshold
     */
    public float[] departureThreshold(float towardX, float towardY) {
        double fromStart = Math.hypot(towardX - startX, towardY - startY);
        double fromEnd = Math.hypot(towardX - endX, towardY - endY);
        return fromStart >= fromEnd
                ? new float[]{startX, startY}
                : new float[]{endX, endY};
    }

    /**
     * The threshold to touch down on for a craft arriving from
     * {@code (fromX, fromY)} — the near one, so it lands into the end it
     * reaches first and rolls out along the strip rather than flying the length
     * of its own runway to land the wrong way down it.
     */
    public float[] touchdownThreshold(float fromX, float fromY) {
        return opposite(departureThreshold(fromX, fromY));
    }

    /** The opposite end from {@code threshold} — where a roll that started there ends up. */
    public float[] opposite(float[] threshold) {
        boolean atStart = threshold[0] == startX && threshold[1] == startY;
        return atStart ? new float[]{endX, endY} : new float[]{startX, startY};
    }
}
