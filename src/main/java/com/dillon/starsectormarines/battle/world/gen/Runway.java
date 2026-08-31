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
     * <p>Where the aircraft is coming from is not consulted, so this is the
     * rule for an arrival rather than a departure: see
     * {@link #departureThreshold(float, float, float, float)}.
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
     * The threshold to start a roll from for an aircraft standing at
     * {@code (fromX, fromY)} and going to {@code (towardX, towardY)} — the end
     * that costs the least turning.
     *
     * <p>Picking purely on where the sortie is going, the way
     * {@link #departureThreshold(float, float)} does, is right about the
     * departure and blind to everything before it. It sends an aircraft parked
     * beside one threshold down the length of its own field to the other one
     * and then asks it to turn most of the way round on arrival, which is a
     * half-circle of taxiing, a half-circle of turning, and a departure that
     * gains nothing the near end would not have given.
     *
     * <p>So both ends are scored on the turning the whole procedure costs: the
     * turn at the threshold, from the direction the aircraft arrives on to the
     * direction it will roll, plus the turn after it is airborne, from the roll
     * direction onto its course. The taxi carries a small per-cell charge as
     * well, so an end that is right there is not passed over for one across the
     * base on the strength of a few degrees.
     *
     * <p>The aircraft's parked heading is deliberately not part of this. It
     * turns out of its shelter toward whichever end it picks, so that turn is
     * paid either way; what causes the half-circles is the turn at the far end
     * of the taxi, and that is what the arrival direction measures.
     *
     * <p>Deliberately a turn count rather than a curvature-constrained path
     * length, though
     * {@link com.dillon.starsectormarines.battle.vehicle.ReedsShepp} would
     * answer "shortest drive from this pose to that one" outright. Neither of
     * its assumptions holds here: an aircraft on its brakes swings its nose
     * round standing still, so it is not confined to arcs of a minimum radius,
     * and the taxi is a routed path round the base's buildings rather than a
     * free-space curve — a planner's length would be measuring a journey the
     * craft is not going to make. What the two ends genuinely differ by is how
     * much turning each of them costs, so that is what is counted. Kept in one
     * scoring method so a later chain that does solve the whole ground path
     * analytically replaces it in one place.
     *
     * @return {@code {x, y}} of the chosen threshold
     */
    public float[] departureThreshold(float fromX, float fromY,
                                      float towardX, float towardY) {
        float[] atStart = {startX, startY};
        float[] atEnd = {endX, endY};
        return departureCost(atStart, fromX, fromY, towardX, towardY)
                <= departureCost(atEnd, fromX, fromY, towardX, towardY)
                ? atStart : atEnd;
    }

    /**
     * Degrees of turning charged for each cell an aircraft has to taxi to reach
     * a threshold.
     *
     * <p>The exchange rate between the two things being traded. At this rate
     * the whole length of a thirty-cell strip is worth about ninety degrees, so
     * a near end wins any ordinary argument and a far end still wins when the
     * near one would mean rolling out backwards.
     */
    private static final float TURN_DEG_PER_TAXI_CELL = 3f;

    /** Turning the whole departure costs from {@code threshold}, in degrees. */
    private float departureCost(float[] threshold, float fromX, float fromY,
                                float towardX, float towardY) {
        float[] far = opposite(threshold);
        float taxi = (float) Math.hypot(threshold[0] - fromX, threshold[1] - fromY);
        float arriveOn = bearing(threshold[0] - fromX, threshold[1] - fromY);
        float rollOn = bearing(far[0] - threshold[0], far[1] - threshold[1]);
        float courseOn = bearing(towardX - far[0], towardY - far[1]);
        return turn(arriveOn, rollOn) + turn(rollOn, courseOn)
                + taxi * TURN_DEG_PER_TAXI_CELL;
    }

    /** Compass bearing of a direction, degrees; zero for a zero-length one. */
    private static float bearing(float dx, float dy) {
        if (Math.abs(dx) < 1e-6f && Math.abs(dy) < 1e-6f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    /** Shortest turn between two bearings, degrees, always positive. */
    private static float turn(float fromDeg, float toDeg) {
        return Math.abs(((toDeg - fromDeg + 540f) % 360f) - 180f);
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

    /**
     * A point {@code leadCells} out beyond {@code threshold}, on the strip's
     * own axis — the final approach fix.
     *
     * <p>Where a landing aircraft is solved <em>to</em>, rather than a waypoint
     * it is railroaded through. An aircraft that flies straight at a threshold
     * arrives on whatever heading it happened to be on and then has to sort
     * itself out while standing on the runway; one whose path ends here, on the
     * runway axis, is already pointing down the strip and only has to hold that
     * for the last stretch. See {@code RunwayApproach}, which owns how far out
     * this sits and why.
     */
    public float[] approachPoint(float[] threshold, float leadCells) {
        float[] far = opposite(threshold);
        float dx = threshold[0] - far[0];
        float dy = threshold[1] - far[1];
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-6f) return new float[]{threshold[0], threshold[1]};
        return new float[]{
                threshold[0] + dx / length * leadCells,
                threshold[1] + dy / length * leadCells };
    }

    /** The opposite end from {@code threshold} — where a roll that started there ends up. */
    public float[] opposite(float[] threshold) {
        boolean atStart = threshold[0] == startX && threshold[1] == startY;
        return atStart ? new float[]{endX, endY} : new float[]{startX, startY};
    }
}
